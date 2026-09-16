package dev.mazedecoder.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GridTest {
    @Test
    void validatesDimensionsEndpointsAndCarves() {
        assertThrows(IllegalArgumentException.class, () -> Grid.builder(0, 10));
        assertThrows(IllegalArgumentException.class, () -> Grid.builder(10, -1));
        assertThrows(IllegalArgumentException.class, () -> Grid.builder(Integer.MAX_VALUE, 2));
        Grid.Builder builder = Grid.builder(2, 2);
        assertThrows(IllegalArgumentException.class, () -> builder.endpoints(new Cell(-1, 0), new Cell(1, 1)));
        assertThrows(IllegalArgumentException.class, () -> builder.carve(new Cell(0, 0), new Cell(1, 1)));
        assertThrows(IllegalArgumentException.class, () -> builder.carve(new Cell(0, 0), new Cell(0, -1)));
        assertThrows(IllegalArgumentException.class, () -> builder.carve(new Cell(0, 0), new Cell(0, 0)));
        assertThrows(IllegalArgumentException.class, () -> builder.build().openNeighbors(new Cell(3, 0)));
    }

    @Test
    void snapshotsAndResultsCannotBeMutated() {
        Cell start = new Cell(0, 0);
        Grid.Builder builder = Grid.builder(2, 1);
        Grid before = builder.build();
        builder.carve(start, new Cell(1, 0)).visit(start);
        Grid after = builder.build();
        assertTrue(before.hasWall(start, Direction.EAST));
        assertFalse(before.visited(start));
        assertFalse(after.hasWall(start, Direction.EAST));
        assertFalse(after.hasWall(new Cell(1, 0), Direction.WEST));
        assertNotEquals(before, after);
        assertEquals(before.hashCode(), Grid.builder(2, 1).build().hashCode());
        GenerationResult generation = MazeAlgorithms.generate(GenerationAlgorithm.DFS, 2, 2, 0);
        assertThrows(UnsupportedOperationException.class, () -> generation.stepEvents().clear());
        SolveResult solve = MazeAlgorithms.solve(SearchAlgorithm.BFS, generation.finalGrid());
        assertThrows(UnsupportedOperationException.class, () -> solve.exploredCells().clear());
        assertThrows(UnsupportedOperationException.class, () -> solve.finalPath().clear());
        assertThrows(UnsupportedOperationException.class, () -> after.openNeighbors(start).clear());
    }

    @Test
    void rejectsInvalidWeightedSearchInputs() {
        Grid grid = Grid.builder(2, 2).build();
        for (SearchAlgorithm algorithm : List.of(SearchAlgorithm.DIJKSTRA, SearchAlgorithm.ASTAR)) {
            for (double invalid : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY, Double.MAX_VALUE})
                assertThrows(IllegalArgumentException.class, () -> MazeAlgorithms.solve(algorithm, grid, cell -> invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> MazeAlgorithms.solve(SearchAlgorithm.BFS, grid, cell -> 2));
        assertThrows(IllegalArgumentException.class, () -> MazeAlgorithms.solve(SearchAlgorithm.DFS, grid, cell -> 2));
        assertThrows(NullPointerException.class, () -> MazeAlgorithms.solve(null, grid));
        assertThrows(NullPointerException.class, () -> MazeAlgorithms.solve(SearchAlgorithm.BFS, null));
    }
}
