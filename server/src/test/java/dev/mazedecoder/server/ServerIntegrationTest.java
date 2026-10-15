package dev.mazedecoder.server;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ServerIntegrationTest {
    @Autowired Environment environment;
    @Autowired ObjectMapper json;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private HttpResponse<String> post(String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + environment.getProperty("local.server.port") + path))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode body(HttpResponse<String> response, int expectedStatus) {
        assertEquals(expectedStatus, response.statusCode(), response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("application/json"));
        return json.readTree(response.body());
    }
    @Test void generationReturnsGridAndSeedDeterminism() throws Exception {
        for (String algorithm : List.of("DFS", "PRIM", "KRUSKAL")) {
            String request = "{\"algorithm\":\"" + algorithm + "\",\"width\":4,\"height\":3,\"seed\":42}";
            JsonNode first = body(post("/api/maze/generate", request), 201);
            JsonNode second = body(post("/api/maze/generate", request), 201);
            assertNotEquals(first.get("mazeId").asText(), second.get("mazeId").asText());
            JsonNode grid = first.get("finalGrid");
            assertEquals(grid, second.get("finalGrid"));
            assertEquals(4, grid.get("width").asInt());
            assertEquals(3, grid.get("height").asInt());
            assertEquals(12, grid.get("cells").size());
            assertEquals(0, grid.get("start").get("x").asInt());
            assertEquals(3, grid.get("end").get("x").asInt());
            assertEquals(2, grid.get("end").get("y").asInt());
            for (JsonNode cell : grid.get("cells")) {
                assertTrue(cell.get("visited").asBoolean());
                for (String direction : List.of("north", "east", "south", "west"))
                    assertTrue(cell.get("walls").get(direction).isBoolean());
            }
        }
    }
    @Test void solveAndRaceUseIdenticalStoredMazeAndReturnFullResults() throws Exception {
        JsonNode generated = body(post("/api/maze/generate", "{\"algorithm\":\"PRIM\",\"width\":8,\"height\":7,\"seed\":123}"), 201);
        String id = generated.get("mazeId").asText();
        JsonNode race = body(post("/api/maze/" + id + "/race", "{\"algorithms\":[\"BFS\",\"DFS\",\"DIJKSTRA\",\"ASTAR\"]}"), 200);
        assertEquals(id, race.get("mazeId").asText());
        assertEquals(4, race.get("results").size());
        for (String algorithm : List.of("BFS", "DFS", "DIJKSTRA", "ASTAR")) {
            JsonNode solved = body(post("/api/maze/" + id + "/solve", "{\"algorithm\":\"" + algorithm + "\"}"), 200);
            JsonNode raced = race.get("results").get(algorithm);
            assertEquals(solved.get("exploredCells"), raced.get("exploredCells"));
            assertEquals(solved.get("finalPath"), raced.get("finalPath"));
            assertEquals(solved.get("finalPath").size() - 1, solved.get("stats").get("pathLength").asInt());
            assertEquals(solved.get("exploredCells").size(), solved.get("stats").get("cellsExplored").asInt());
            assertTrue(solved.get("stats").get("elapsedNanos").asLong() >= 0);
            assertEquals(race.get("results").get("BFS").get("finalPath"), solved.get("finalPath"));
        }
    }
    @Test void invalidRequestsAndMissingMazesHavePredictableStatusAndErrorShape() throws Exception {
        for (String request : List.of("{}", "null", "{", "{\"algorithm\":\"WRONG\",\"width\":3,\"height\":3,\"seed\":0}",
                "{\"algorithm\":\"DFS\",\"width\":101,\"height\":3,\"seed\":0}",
                "{\"algorithm\":\"DFS\",\"width\":0,\"height\":3,\"seed\":0}",
                "{\"algorithm\":\"DFS\",\"width\":3.5,\"height\":3,\"seed\":0}",
                "{\"algorithm\":\"DFS\",\"width\":3,\"height\":3}")) {
            assertEquals("INVALID_REQUEST", body(post("/api/maze/generate", request), 400).get("code").asText());
        }
        assertEquals("MAZE_NOT_FOUND", body(post("/api/maze/missing/solve", "{\"algorithm\":\"BFS\"}"), 404).get("code").asText());
        assertEquals("MAZE_NOT_FOUND", body(post("/api/maze/missing/race", "{\"algorithms\":[\"BFS\",\"DFS\"]}"), 404).get("code").asText());
        for (String request : List.of("{}", "{\"algorithms\":[\"BFS\"]}", "{\"algorithms\":[\"BFS\",\"BFS\"]}", "{\"algorithms\":[\"BFS\",null]}"))
            body(post("/api/maze/missing/race", request), 400);
    }
    @Test void configuredCorsOriginAcceptedAndUnknownOriginRejected() throws Exception {
        String url = "http://localhost:" + environment.getProperty("local.server.port") + "/api/maze/generate";
        for (String origin : List.of("http://localhost:5173", "https://untrusted.example")) {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(url)).method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                    .header("Origin", origin).header("Access-Control-Request-Method", "POST")
                    .header("Access-Control-Request-Headers", "content-type").build(), HttpResponse.BodyHandlers.ofString());
            if (origin.contains("localhost")) {
                assertEquals(200, response.statusCode());
                assertEquals(origin, response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
            } else assertEquals(403, response.statusCode());
        }
    }
}
