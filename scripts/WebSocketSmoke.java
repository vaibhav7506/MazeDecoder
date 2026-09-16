import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** JDK-only raw WebSocket client: java scripts/WebSocketSmoke.java [ws://host/ws/maze] */
class WebSocketSmoke {
    public static void main(String[] args) throws Exception {
        URI uri = URI.create(args.length == 0 ? "ws://localhost:8080/ws/maze" : args[0]);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        List<String> generation = exchange(client, uri,
                "{\"requestId\":\"smoke-generate\",\"operation\":\"generate\",\"algorithm\":\"DFS\",\"width\":2,\"height\":2,\"seed\":42}");
        var idMatch = Pattern.compile("\"mazeId\":\"([^\"]+)\"").matcher(generation.get(0));
        if (!idMatch.find()) throw new AssertionError("Missing mazeId");
        String id = idMatch.group(1);
        if (generation.stream().filter(frame -> frame.contains("\"type\":\"step\"")).count() != 3)
            throw new AssertionError("Expected three individual wall-removal frames");
        exchange(client, uri, "{\"requestId\":\"smoke-solve\",\"operation\":\"solve\",\"mazeId\":\"" + id + "\",\"algorithm\":\"BFS\"}");
        List<String> race = exchange(client, uri,
                "{\"requestId\":\"smoke-race\",\"operation\":\"race\",\"mazeId\":\"" + id + "\",\"algorithms\":[\"BFS\",\"ASTAR\"]}");
        for (String algorithm : List.of("BFS", "ASTAR")) {
            if (race.stream().noneMatch(frame -> frame.contains("\"type\":\"algorithm_complete\"")
                    && frame.contains("\"algorithm\":\"" + algorithm + "\"")))
                throw new AssertionError("Missing completion for " + algorithm);
        }
        System.out.println("SMOKE PASS: generation, solve, and tagged race completed over real WebSockets.");
    }

    private static List<String> exchange(HttpClient client, URI uri, String command) throws Exception {
        BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        WebSocket socket = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10))
                .buildAsync(uri, new WebSocket.Listener() {
                    private final StringBuilder text = new StringBuilder();
                    @Override public void onOpen(WebSocket ws) { ws.request(1); }
                    @Override public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                        text.append(data);
                        if (last) { frames.add(text.toString()); text.setLength(0); }
                        ws.request(1);
                        return null;
                    }
                    @Override public void onError(WebSocket ws, Throwable error) { frames.add("CLIENT_ERROR: " + error); }
                }).get(15, TimeUnit.SECONDS);
        try {
            System.out.println(">> " + command);
            socket.sendText(command, true).join();
            List<String> received = new ArrayList<>();
            while (true) {
                String frame = frames.poll(15, TimeUnit.SECONDS);
                if (frame == null) throw new AssertionError("Timed out waiting for a frame");
                System.out.println("<< " + frame);
                if (frame.startsWith("CLIENT_ERROR") || frame.contains("\"type\":\"error\"")) throw new AssertionError(frame);
                received.add(frame);
                if (frame.contains("\"type\":\"complete\"")) return received;
            }
        } finally { socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join(); }
    }
}
