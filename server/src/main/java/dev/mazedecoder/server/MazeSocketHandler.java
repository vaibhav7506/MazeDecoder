package dev.mazedecoder.server;

import dev.mazedecoder.core.*;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static dev.mazedecoder.server.ApiModels.*;

/** Individual frames, client-paced playback. One outstanding request per connection. */
@Component
public class MazeSocketHandler extends TextWebSocketHandler {
    private static final Logger LOG = LoggerFactory.getLogger(MazeSocketHandler.class);
    private final MazeService service;
    private final ObjectMapper json;
    private final Map<String, Connection> connections = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16), runnable -> {
                Thread thread = new Thread(runnable, "maze-stream");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    private static final class Connection {
        final WebSocketSession session;
        final AtomicReference<Command> current = new AtomicReference<>();
        volatile FutureTask<Void> task;
        Connection(WebSocketSession session) { this.session = session; }
    }

    public MazeSocketHandler(MazeService service, ObjectMapper json) { this.service = service; this.json = json; }

    @Override public void afterConnectionEstablished(WebSocketSession session) {
        session.setTextMessageSizeLimit(4096);
        // Tomcat's blocking send deadline also bounds a slow/non-reading client.
        if (session instanceof org.springframework.web.socket.adapter.standard.StandardWebSocketSession standard) {
            standard.getNativeSession().getUserProperties().put("org.apache.tomcat.websocket.BLOCKING_SEND_TIMEOUT", 5000L);
        }
        connections.put(session.getId(), new Connection(session));
    }

    @Override protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Connection connection = connections.get(session.getId());
        if (connection == null) return;
        Command command;
        try {
            command = json.readValue(message.getPayload(), Command.class);
            if (command == null || command.requestId() == null || command.requestId().isBlank()
                    || command.requestId().length() > 80 || command.operation() == null)
                throw new IllegalArgumentException("requestId (1-80 characters) and operation are required");
        } catch (RuntimeException exception) {
            error(connection, null, "INVALID_REQUEST", "Malformed JSON or missing requestId/operation");
            return;
        }
        synchronized (connection) {
            if (!session.isOpen()) return;
            if (!connection.current.compareAndSet(null, command)) {
                error(connection, command, "BUSY", "Wait for the current request to complete");
                return;
            }
            FutureTask<Void> task = new FutureTask<>(() -> { execute(connection, command); return null; }) {
                @Override protected void done() { connection.current.compareAndSet(command, null); }
            };
            connection.task = task;
            try { workers.execute(task); }
            catch (RejectedExecutionException exception) {
                task.cancel(false);
                error(connection, command, "SERVER_BUSY", "Stream capacity reached; retry later");
            }
        }
    }

    private void execute(Connection connection, Command command) {
        try {
            checkOpen(connection);
            switch (command.operation()) {
                case "generate" -> generate(connection, command);
                case "solve", "race" -> search(connection, command);
                default -> throw new IllegalArgumentException("operation must be generate, solve, or race");
            }
        } catch (MazeStore.MissingMazeException exception) {
            terminalError(connection, command, "MAZE_NOT_FOUND", exception.getMessage());
        } catch (IllegalArgumentException exception) {
            terminalError(connection, command, "INVALID_REQUEST", exception.getMessage());
        } catch (CancellationException | IOException exception) {
            disconnect(connection);
        } catch (Exception exception) {
            // Unexpected failures are logged once. Normal disconnects never get a stack trace.
            LOG.error("Maze request failed: {}", command.requestId(), exception);
            terminalError(connection, command, "INTERNAL_ERROR", "Request failed");
        }
    }

    private void generate(Connection connection, Command command) throws IOException {
        GenerationAlgorithm algorithm = generationAlgorithm(command.algorithm());
        MazeService.Generated generated = service.generate(new GenerateRequest(algorithm, command.width(), command.height(), command.seed()));
        Grid grid = generated.result().finalGrid();
        String id = generated.mazeId();
        send(connection, event("started", command, id, algorithm.name(), null, Started.from(grid)));
        List<WallRemoved> steps = generated.result().stepEvents();
        for (int i = 0; i < steps.size(); i++)
            send(connection, event("step", command, id, algorithm.name(), i, steps.get(i)));
        finish(connection, command, event("complete", command, id, algorithm.name(), null, GridView.from(grid)));
    }

    private void search(Connection connection, Command command) throws IOException {
        List<SearchAlgorithm> algorithms = command.operation().equals("solve")
                ? List.of(searchAlgorithm(command.algorithm())) : MazeService.validateRace(command.algorithms());
        Grid grid = service.grid(command.mazeId());
        send(connection, event("started", command, command.mazeId(), null, null, Started.from(grid)));
        List<SolveResult> results = new ArrayList<>();
        for (SearchAlgorithm algorithm : algorithms) {
            checkOpen(connection);
            results.add(MazeAlgorithms.solve(algorithm, grid));
        }
        // Round-robin exploration; each sequence counter belongs to its algorithm alone.
        int maxSteps = results.stream().mapToInt(result -> result.exploredCells().size()).max().orElse(0);
        for (int sequence = 0; sequence < maxSteps; sequence++) {
            for (int i = 0; i < algorithms.size(); i++) {
                SolveResult result = results.get(i);
                String algorithm = algorithms.get(i).name();
                int count = result.exploredCells().size();
                if (sequence < count)
                    send(connection, event("step", command, command.mazeId(), algorithm, sequence, result.exploredCells().get(sequence)));
                if (sequence == count - 1)
                    send(connection, event("algorithm_complete", command, command.mazeId(), algorithm, null, Solution.from(result)));
            }
        }
        finish(connection, command, event("complete", command, command.mazeId(), null, null, null));
    }

    private static GenerationAlgorithm generationAlgorithm(String value) {
        if (value == null) throw new IllegalArgumentException("algorithm is required");
        try { return GenerationAlgorithm.valueOf(value); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Generation algorithm must be DFS, PRIM, or KRUSKAL"); }
    }
    private static SearchAlgorithm searchAlgorithm(String value) {
        if (value == null) throw new IllegalArgumentException("algorithm is required");
        try { return SearchAlgorithm.valueOf(value); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Search algorithm must be BFS, DFS, DIJKSTRA, or ASTAR"); }
    }
    private static StreamEvent event(String type, Command command, String id, String algorithm, Integer sequence, Object data) {
        return new StreamEvent(type, command.requestId(), command.operation(), id, algorithm, sequence, data);
    }
    private static void checkOpen(Connection connection) {
        if (Thread.currentThread().isInterrupted() || !connection.session.isOpen()) throw new CancellationException();
    }
    private void send(Connection connection, StreamEvent event) throws IOException {
        synchronized (connection.session) {
            checkOpen(connection);
            try { connection.session.sendMessage(new TextMessage(json.writeValueAsString(event))); }
            catch (IllegalStateException exception) { throw new IOException("Connection closed during send", exception); }
        }
    }
    private void error(Connection connection, Command command, String code, String message) {
        try {
            send(connection, new StreamEvent("error", command == null ? null : command.requestId(),
                    command == null ? null : command.operation(), command == null ? null : command.mazeId(),
                    null, null, new ApiError(code, message)));
        } catch (IOException | CancellationException exception) { disconnect(connection); }
    }
    private void finish(Connection connection, Command command, StreamEvent event) throws IOException {
        synchronized (connection.session) {
            // Allow the next command as soon as this terminal frame arrives at the client.
            // The session lock preserves wire order if its worker starts immediately.
            connection.current.compareAndSet(command, null);
            send(connection, event);
        }
    }
    private void terminalError(Connection connection, Command command, String code, String message) {
        try {
            finish(connection, command, event("error", command, command.mazeId(), null, null, new ApiError(code, message)));
        } catch (IOException | CancellationException exception) { disconnect(connection); }
    }
    private void disconnect(Connection connection) {
        connections.remove(connection.session.getId(), connection);
        synchronized (connection) {
            if (connection.task != null) {
                connection.task.cancel(true);
                workers.remove(connection.task);
            }
        }
        try { if (connection.session.isOpen()) connection.session.close(CloseStatus.GOING_AWAY); }
        catch (IOException ignored) { /* Peer is already gone. */ }
    }
    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Connection connection = connections.get(session.getId());
        if (connection != null) disconnect(connection);
    }
    @Override public void handleTransportError(WebSocketSession session, Throwable exception) {
        Connection connection = connections.get(session.getId());
        if (connection != null) disconnect(connection);
    }
    int activeWorkers() { return workers.getActiveCount(); }
    int queuedRequests() { return workers.getQueue().size(); }
    int connectionCount() { return connections.size(); }
    @PreDestroy public void shutdown() {
        for (Connection connection : connections.values()) disconnect(connection);
        workers.shutdownNow();
    }
}
