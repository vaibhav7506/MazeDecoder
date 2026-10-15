package dev.mazedecoder.server;

import dev.mazedecoder.core.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Wire DTOs live here so the core library needs no JSON or Spring annotations. */
public final class ApiModels {
    private ApiModels() { }
    public record GenerateRequest(GenerationAlgorithm algorithm, Integer width, Integer height, Long seed) { }
    public record SolveRequest(SearchAlgorithm algorithm) { }
    public record RaceRequest(List<SearchAlgorithm> algorithms) { }
    public record Walls(boolean north, boolean east, boolean south, boolean west) { }
    public record CellView(int x, int y, Walls walls, boolean visited) { }
    public record GridView(int width, int height, Cell start, Cell end, List<CellView> cells) {
        static GridView from(Grid grid) {
            List<CellView> cells = new ArrayList<>(grid.size());
            for (int y = 0; y < grid.height(); y++) {
                for (int x = 0; x < grid.width(); x++) {
                    Cell cell = new Cell(x, y);
                    cells.add(new CellView(x, y, new Walls(grid.hasWall(cell, Direction.NORTH),
                            grid.hasWall(cell, Direction.EAST), grid.hasWall(cell, Direction.SOUTH),
                            grid.hasWall(cell, Direction.WEST)), grid.visited(cell)));
                }
            }
            return new GridView(grid.width(), grid.height(), grid.start(), grid.end(), List.copyOf(cells));
        }
    }
    public record GenerateResponse(String mazeId, GridView finalGrid) { }
    public record RaceResponse(String mazeId, Map<SearchAlgorithm, SolveResult> results) { }
    public record ApiError(String code, String message) { }
    public record Command(String requestId, String operation, String algorithm, Integer width, Integer height,
                          Long seed, String mazeId, List<SearchAlgorithm> algorithms) { }
    public record StreamEvent(String type, String requestId, String operation, String mazeId,
                              String algorithm, Integer sequence, Object data) { }
    public record Started(int width, int height, Cell start, Cell end, String pacing) {
        static Started from(Grid grid) {
            return new Started(grid.width(), grid.height(), grid.start(), grid.end(), "client");
        }
    }
    public record Solution(List<Cell> finalPath, RunStats stats) {
        static Solution from(SolveResult result) { return new Solution(result.finalPath(), result.stats()); }
    }
}
