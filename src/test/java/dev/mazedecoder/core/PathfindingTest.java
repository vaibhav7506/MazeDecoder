package dev.mazedecoder.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.*;

class PathfindingTest {
    @Test
    void explorationIncludesDeadEndsSeparatelyFromFinalPath() {
        Cell start = new Cell(0, 0);
        Cell deadEnd = new Cell(1, 0);
        Cell middle = new Cell(0, 1);
        Cell goal = new Cell(1, 1);
        Grid grid = Grid.builder(2, 2).carve(start, deadEnd).carve(start, middle).carve(middle, goal).build();
        for (SearchAlgorithm algorithm : SearchAlgorithm.values()) {
            SolveResult result = MazeAlgorithms.solve(algorithm, grid);
            assertEquals(List.of(start, deadEnd, middle, goal), result.exploredCells());
            assertEquals(List.of(start, middle, goal), result.finalPath());
            assertEquals(4, result.stats().cellsExplored());
            assertEquals(2, result.stats().pathLength());
        }
    }

    @ParameterizedTest
    @EnumSource(SearchAlgorithm.class)
    void knownCorridorRecordsExplorationPathAndStats(SearchAlgorithm algorithm) {
        Grid grid = Grid.builder(3, 2)
                .carve(new Cell(0, 0), new Cell(1, 0))
                .carve(new Cell(1, 0), new Cell(1, 1))
                .carve(new Cell(1, 1), new Cell(2, 1)).build();
        SolveResult result = MazeAlgorithms.solve(algorithm, grid);
        List<Cell> expected = List.of(new Cell(0, 0), new Cell(1, 0), new Cell(1, 1), new Cell(2, 1));
        assertEquals(expected, result.finalPath());
        assertEquals(expected, result.exploredCells());
        assertEquals(3, result.stats().pathLength());
        assertValidResult(grid, result);
    }

    @Test
    void dfsMayTakeLongerRouteWhileOtherSearchesFindKnownShortestPath() {
        // A perimeter cycle offers a two-edge left route and six-edge clockwise route.
        Grid.Builder builder = Grid.builder(3, 3).endpoints(new Cell(0, 0), new Cell(0, 2));
        List<Cell> perimeter = List.of(new Cell(0, 0), new Cell(1, 0), new Cell(2, 0), new Cell(2, 1),
                new Cell(2, 2), new Cell(1, 2), new Cell(0, 2), new Cell(0, 1), new Cell(0, 0));
        for (int i = 1; i < perimeter.size(); i++) builder.carve(perimeter.get(i - 1), perimeter.get(i));
        Grid grid = builder.build();
        for (SearchAlgorithm algorithm : SearchAlgorithm.values()) {
            SolveResult result = MazeAlgorithms.solve(algorithm, grid);
            assertValidResult(grid, result);
            assertEquals(algorithm == SearchAlgorithm.DFS ? 6 : 2, result.stats().pathLength());
        }
    }

    @ParameterizedTest
    @EnumSource(GenerationAlgorithm.class)
    void allSearchesAgreeOnPerfectMazesWithoutMutatingTheGrid(GenerationAlgorithm generator) {
        for (int seed = 0; seed < 12; seed++) {
            Grid grid = MazeAlgorithms.generate(generator, 20, 17, seed).finalGrid();
            Grid before = grid.withEndpoints(grid.start(), grid.end());
            SolveResult reference = MazeAlgorithms.solve(SearchAlgorithm.BFS, grid);
            for (SearchAlgorithm algorithm : SearchAlgorithm.values()) {
                SolveResult result = MazeAlgorithms.solve(algorithm, grid);
                assertValidResult(grid, result);
                assertEquals(reference.finalPath(), result.finalPath());
                assertEquals(before, grid);
                assertEquals(result.exploredCells(), MazeAlgorithms.solve(algorithm, grid).exploredCells());
            }
        }
    }

    @ParameterizedTest
    @EnumSource(SearchAlgorithm.class)
    void supportsHundredByHundredGrid(SearchAlgorithm algorithm) {
        Grid grid = MazeAlgorithms.generate(GenerationAlgorithm.DFS, 100, 100, 88).finalGrid();
        SolveResult result = MazeAlgorithms.solve(algorithm, grid);
        assertValidResult(grid, result);
        assertEquals(MazeAlgorithms.solve(SearchAlgorithm.BFS, grid).stats().pathLength(), result.stats().pathLength());
    }

    @Test
    void manhattanIsAdmissibleAndConsistentAgainstIndependentDistances() {
        // Exact distances are calculated independently, without the production solvers.
        // Open-grid equality makes even a slight heuristic overestimate fail this test.
        for (Grid grid : List.of(openGrid(7, 6), MazeAlgorithms.generate(GenerationAlgorithm.PRIM, 7, 6, 4).finalGrid())) {
            int[] exact = exactDistances(grid, grid.end());
            for (int i = 0; i < grid.size(); i++) {
                Cell cell = grid.cell(i);
                double heuristic = Pathfinder.heuristic(SearchAlgorithm.ASTAR, cell, grid.end(), 1);
                assertTrue(heuristic <= exact[i], "Overestimated distance at " + cell);
                assertTrue(heuristic >= 0);
                for (Cell neighbor : grid.openNeighbors(cell)) {
                    assertTrue(heuristic <= 1 + Pathfinder.heuristic(SearchAlgorithm.ASTAR, neighbor, grid.end(), 1));
                }
            }
            assertEquals(0, Pathfinder.heuristic(SearchAlgorithm.ASTAR, grid.end(), grid.end(), 1));
        }
    }

    @Test
    void astarAndDijkstraMatchIndependentShortestDistanceOnCyclicGrids() {
        // Perfect mazes alone cannot expose a solver choosing a longer alternative route.
        for (int seed = 0; seed < 100; seed++) {
            Grid.Builder builder = Grid.builder(9, 8);
            Random random = new Random(seed);
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 9; x++) {
                    Cell cell = new Cell(x, y);
                    if (x < 8 && random.nextDouble() < .7) builder.carve(cell, new Cell(x + 1, y));
                    if (y < 7 && random.nextDouble() < .7) builder.carve(cell, new Cell(x, y + 1));
                }
            }
            Grid grid = builder.build();
            int expected = exactDistances(grid, grid.start())[grid.index(grid.end())];
            for (SearchAlgorithm algorithm : List.of(SearchAlgorithm.BFS, SearchAlgorithm.DIJKSTRA, SearchAlgorithm.ASTAR)) {
                SolveResult result = MazeAlgorithms.solve(algorithm, grid);
                assertEquals(expected >= 0, result.reachedEnd());
                if (expected >= 0) {
                    assertEquals(expected, result.stats().pathLength(), "seed=" + seed + " algorithm=" + algorithm);
                    assertValidResult(grid, result);
                }
            }
        }
    }

    @Test
    void weightedSearchTakesCheaperLongerRouteAndScalesFractionalHeuristic() {
        Grid grid = openGrid(3, 2).withEndpoints(new Cell(0, 0), new Cell(2, 0));
        ToDoubleFunction<Cell> cost = cell -> cell.equals(new Cell(1, 0)) ? 10 : .25;
        for (SearchAlgorithm algorithm : List.of(SearchAlgorithm.DIJKSTRA, SearchAlgorithm.ASTAR)) {
            SolveResult result = MazeAlgorithms.solve(algorithm, grid, cost);
            assertEquals(4, result.stats().pathLength());
            assertEquals(1, result.stats().pathCost(), 1e-12);
            assertFalse(result.finalPath().contains(new Cell(1, 0)));
            assertEquals(.5, Pathfinder.heuristic(SearchAlgorithm.ASTAR, grid.start(), grid.end(), .25));
            SolveResult zero = MazeAlgorithms.solve(algorithm, grid, cell -> 0);
            assertTrue(zero.reachedEnd());
            assertEquals(0, zero.stats().pathCost());
        }
    }

    @ParameterizedTest
    @EnumSource(SearchAlgorithm.class)
    void unreachableAndSameEndpointAreUnambiguous(SearchAlgorithm algorithm) {
        Grid isolated = Grid.builder(2, 2).build();
        SolveResult unreachable = MazeAlgorithms.solve(algorithm, isolated);
        assertFalse(unreachable.reachedEnd());
        assertTrue(unreachable.finalPath().isEmpty());
        assertEquals(List.of(isolated.start()), unreachable.exploredCells());
        assertEquals(0, unreachable.stats().pathLength());
        assertEquals(0, unreachable.stats().pathCost());
        Grid single = Grid.builder(1, 1).build();
        SolveResult result = MazeAlgorithms.solve(algorithm, single);
        assertEquals(List.of(single.start()), result.finalPath());
        assertEquals(0, result.stats().pathLength());
        assertValidResult(single, result);
        Grid custom = isolated.withEndpoints(new Cell(1, 0), new Cell(1, 0));
        assertEquals(List.of(new Cell(1, 0)), MazeAlgorithms.solve(algorithm, custom).finalPath());
    }

    private static Grid openGrid(int width, int height) {
        Grid.Builder builder = Grid.builder(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (x + 1 < width) builder.carve(new Cell(x, y), new Cell(x + 1, y));
                if (y + 1 < height) builder.carve(new Cell(x, y), new Cell(x, y + 1));
            }
        }
        return builder.build();
    }

    private static int[] exactDistances(Grid grid, Cell source) {
        int[] distances = new int[grid.size()];
        Arrays.fill(distances, -1);
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        distances[grid.index(source)] = 0;
        queue.add(source);
        while (!queue.isEmpty()) {
            Cell current = queue.remove();
            for (Cell neighbor : grid.openNeighbors(current)) {
                if (distances[grid.index(neighbor)] < 0) {
                    distances[grid.index(neighbor)] = distances[grid.index(current)] + 1;
                    queue.add(neighbor);
                }
            }
        }
        return distances;
    }

    private static void assertValidResult(Grid grid, SolveResult result) {
        assertTrue(result.reachedEnd());
        assertEquals(grid.start(), result.finalPath().get(0));
        assertEquals(grid.end(), result.finalPath().get(result.finalPath().size() - 1));
        assertEquals(result.finalPath().size(), new HashSet<>(result.finalPath()).size());
        for (int i = 1; i < result.finalPath().size(); i++)
            assertTrue(grid.openNeighbors(result.finalPath().get(i - 1)).contains(result.finalPath().get(i)));
        assertEquals(result.exploredCells().size(), new HashSet<>(result.exploredCells()).size());
        assertTrue(result.exploredCells().containsAll(result.finalPath()));
        assertEquals(grid.start(), result.exploredCells().get(0));
        assertEquals(grid.end(), result.exploredCells().get(result.exploredCells().size() - 1));
        assertEquals(result.exploredCells().size(), result.stats().cellsExplored());
        assertEquals(result.finalPath().size() - 1, result.stats().pathLength());
        assertEquals(result.stats().pathLength(), result.stats().pathCost());
        assertTrue(result.stats().elapsedNanos() >= 0);
    }
}
