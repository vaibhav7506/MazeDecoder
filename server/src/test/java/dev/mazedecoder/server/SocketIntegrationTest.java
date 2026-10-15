package dev.mazedecoder.server;

import dev.mazedecoder.core.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.Environment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SocketIntegrationTest {
    @Autowired Environment environment;
    @Autowired ObjectMapper json;
    @Autowired MazeSocketHandler handler;
    @Autowired MazeService service;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private final class Peer implements WebSocket.Listener, AutoCloseable {
        final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        final StringBuilder partial = new StringBuilder();
        final int abortAfterSteps;
        volatile int steps;
        WebSocket socket;
        Peer(int abortAfterSteps) { this.abortAfterSteps = abortAfterSteps; }
        @Override public void onOpen(WebSocket socket) { socket.request(1); }
        @Override public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                String frame = partial.toString();
                partial.setLength(0);
                frames.add(frame);
                if ("step".equals(json.readTree(frame).get("type").asText())) steps++;
                if (abortAfterSteps > 0 && steps >= abortAfterSteps) { socket.abort(); return null; }
            }
            socket.request(1);
            return null;
        }
        void send(String command) { socket.sendText(command, true).join(); }
        JsonNode next() throws Exception {
            String frame = frames.poll(10, TimeUnit.SECONDS);
            assertNotNull(frame, "Timed out waiting for WebSocket frame");
            return json.readTree(frame);
        }
        List<JsonNode> untilComplete() throws Exception {
            List<JsonNode> events = new ArrayList<>();
            while (true) {
                JsonNode event = next();
                assertNotEquals("error", event.get("type").asText(), event.toString());
                events.add(event);
                if (event.get("type").asText().equals("complete")) return events;
            }
        }
        @Override public void close() { socket.abort(); }
    }
    private Peer connect(int abortAfterSteps) {
        Peer peer = new Peer(abortAfterSteps);
        peer.socket = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5))
                .buildAsync(URI.create("ws://localhost:" + environment.getProperty("local.server.port") + "/ws/maze"), peer).join();
        return peer;
    }
    @Test void generateThenSolveStreamsEveryStepInOrderAndCompletion() throws Exception {
        try (Peer peer = connect(0)) {
            peer.send("{\"requestId\":\"gen-1\",\"operation\":\"generate\",\"algorithm\":\"PRIM\",\"width\":4,\"height\":3,\"seed\":42}");
            List<JsonNode> generated = peer.untilComplete();
            GenerationResult expected = MazeAlgorithms.generate(GenerationAlgorithm.PRIM, 4, 3, 42);
            assertEquals(13, generated.size());
            assertEquals("started", generated.get(0).get("type").asText());
            assertEquals("client", generated.get(0).get("data").get("pacing").asText());
            String mazeId = generated.get(0).get("mazeId").asText();
            for (int i = 0; i < expected.stepEvents().size(); i++) {
                JsonNode event = generated.get(i + 1);
                assertEquals("step", event.get("type").asText());
                assertEquals("gen-1", event.get("requestId").asText());
                assertEquals("PRIM", event.get("algorithm").asText());
                assertEquals(mazeId, event.get("mazeId").asText());
                assertEquals(i, event.get("sequence").asInt());
                assertEquals(json.valueToTree(expected.stepEvents().get(i)), event.get("data"));
            }
            assertEquals(json.valueToTree(ApiModels.GridView.from(expected.finalGrid())), generated.get(12).get("data"));
            peer.send("{\"requestId\":\"solve-1\",\"operation\":\"solve\",\"mazeId\":\"" + mazeId + "\",\"algorithm\":\"ASTAR\"}");
            List<JsonNode> solved = peer.untilComplete();
            assertSearchStream(solved, "solve-1", mazeId, List.of(SearchAlgorithm.ASTAR), expected.finalGrid());
        }
    }
    @Test void raceFramesAreRoundRobinTaggedAndMatchIndividualRuns() throws Exception {
        var generated = service.generate(new ApiModels.GenerateRequest(GenerationAlgorithm.PRIM, 12, 9, 91L));
        try (Peer peer = connect(0)) {
            peer.send("{\"requestId\":\"race-1\",\"operation\":\"race\",\"mazeId\":\"" + generated.mazeId() + "\",\"algorithms\":[\"BFS\",\"DFS\"]}");
            List<JsonNode> events = peer.untilComplete();
            assertSearchStream(events, "race-1", generated.mazeId(), List.of(SearchAlgorithm.BFS, SearchAlgorithm.DFS), generated.result().finalGrid());
            assertEquals("BFS", events.get(1).get("algorithm").asText());
            assertEquals("DFS", events.get(2).get("algorithm").asText());
        }
    }
    private void assertSearchStream(List<JsonNode> events, String requestId, String mazeId,
                                    List<SearchAlgorithm> algorithms, Grid grid) {
        assertEquals("started", events.get(0).get("type").asText());
        assertEquals("complete", events.get(events.size() - 1).get("type").asText());
        Map<SearchAlgorithm, SolveResult> expected = new LinkedHashMap<>();
        for (SearchAlgorithm algorithm : algorithms) expected.put(algorithm, MazeAlgorithms.solve(algorithm, grid));
        int cursor = 1;
        int max = expected.values().stream().mapToInt(result -> result.exploredCells().size()).max().orElseThrow();
        for (int sequence = 0; sequence < max; sequence++) {
            for (var entry : expected.entrySet()) {
                SolveResult result = entry.getValue();
                if (sequence < result.exploredCells().size()) {
                    JsonNode event = events.get(cursor++);
                    assertEquals("step", event.get("type").asText());
                    assertEquals(entry.getKey().name(), event.get("algorithm").asText());
                    assertEquals(sequence, event.get("sequence").asInt());
                    assertEquals(json.valueToTree(result.exploredCells().get(sequence)), event.get("data"));
                }
                if (sequence == result.exploredCells().size() - 1) {
                    JsonNode completed = events.get(cursor++);
                    assertEquals("algorithm_complete", completed.get("type").asText());
                    assertEquals(entry.getKey().name(), completed.get("algorithm").asText());
                    assertEquals(json.valueToTree(result.finalPath()), completed.get("data").get("finalPath"));
                    assertEquals(result.stats().cellsExplored(), completed.get("data").get("stats").get("cellsExplored").asInt());
                }
            }
        }
        assertEquals(events.size() - 1, cursor);
        for (JsonNode event : events) {
            assertEquals(requestId, event.get("requestId").asText());
            assertEquals(mazeId, event.get("mazeId").asText());
        }
    }
    @Test void invalidMessageAndMissingMazeReturnErrorsAndConnectionRemainsUsable() throws Exception {
        try (Peer peer = connect(0)) {
            peer.send("{");
            assertEquals("INVALID_REQUEST", peer.next().get("data").get("code").asText());
            peer.send("{\"requestId\":\"missing\",\"operation\":\"solve\",\"algorithm\":\"BFS\",\"mazeId\":\"missing\"}");
            JsonNode missing = peer.next();
            assertEquals("error", missing.get("type").asText());
            assertEquals("missing", missing.get("requestId").asText());
            assertEquals("MAZE_NOT_FOUND", missing.get("data").get("code").asText());
            peer.send("{\"requestId\":\"valid\",\"operation\":\"generate\",\"algorithm\":\"DFS\",\"width\":1,\"height\":1,\"seed\":0}");
            assertEquals(2, peer.untilComplete().size());
        }
    }
    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void abruptDisconnectAfterThreeStepsReleasesWorkAndNewConnectionWorks(CapturedOutput output) throws Exception {
        try (Peer peer = connect(3)) {
            peer.send("{\"requestId\":\"abort\",\"operation\":\"generate\",\"algorithm\":\"KRUSKAL\",\"width\":100,\"height\":100,\"seed\":42}");
            await(() -> peer.steps >= 3);
            assertEquals(3, peer.steps);
            await(() -> handler.connectionCount() == 0 && handler.activeWorkers() == 0 && handler.queuedRequests() == 0);
        }
        try (Peer peer = connect(0)) {
            peer.send("{\"requestId\":\"reconnected\",\"operation\":\"generate\",\"algorithm\":\"DFS\",\"width\":2,\"height\":2,\"seed\":0}");
            assertEquals(5, peer.untilComplete().size());
        }
        assertFalse(output.getAll().contains("ERROR"), output.getAll());
        assertFalse(output.getAll().contains("Exception"), output.getAll());
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        assertTrue(condition.getAsBoolean(), "Condition did not become true before timeout");
    }
}
