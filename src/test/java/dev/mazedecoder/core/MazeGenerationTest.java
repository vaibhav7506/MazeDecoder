package dev.mazedecoder.core;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MazeGenerationTest {
    @ParameterizedTest
    @EnumSource(GenerationAlgorithm.class)
    void perfectAcrossSizesAndSeedsAndExactlyReplayable(GenerationAlgorithm algorithm) {
        int[][] sizes = {{1, 1}, {1, 31}, {37, 1}, {2, 2}, {7, 11}, {10, 10}, {100, 100}};
        long[] seeds = {0, 1, 42, -1, Long.MIN_VALUE, Long.MAX_VALUE};
        for (int[] size : sizes) {
            for (long seed : seeds) {
                GenerationResult result = MazeAlgorithms.generate(algorithm, size[0], size[1], seed);
                Grid grid = result.finalGrid();
                String context = algorithm + " " + size[0] + "x" + size[1] + " seed=" + seed;
                assertEquals(grid.size() - 1, result.stepEvents().size(), context);
                Grid.Builder replay = Grid.builder(grid.width(), grid.height()).visit(grid.start());
                // Independently track components; a carve within a component would create a cycle.
                int[] components = new int[grid.size()];
                for (int i = 0; i < components.length; i++) components[i] = i;
                for (WallRemoved event : result.stepEvents()) {
                    int a = root(components, grid.index(event.from()));
                    int b = root(components, grid.index(event.to()));
                    assertNotEquals(a, b, "Cycle or duplicate carve: " + context);
                    components[a] = b;
                    replay.carve(event.from(), event.to()).visit(event.from()).visit(event.to());
                }
                assertEquals(grid, replay.build(), context);
                int openSides = 0;
                for (int i = 0; i < grid.size(); i++) {
                    Cell cell = grid.cell(i);
                    assertTrue(grid.visited(cell), context);
                    for (Direction direction : Direction.values()) {
                        Cell next = direction.from(cell);
                        if (!grid.contains(next)) assertTrue(grid.hasWall(cell, direction), context);
                        else assertEquals(grid.hasWall(cell, direction), grid.hasWall(next, direction.opposite()), context);
                    }
                    openSides += grid.openNeighbors(cell).size();
                }
                assertEquals(2 * (grid.size() - 1), openSides, context);
                // Connected + V-1 undirected edges proves exactly one path between every pair.
                Set<Cell> reached = new HashSet<>();
                ArrayDeque<Cell> queue = new ArrayDeque<>();
                reached.add(grid.start());
                queue.add(grid.start());
                while (!queue.isEmpty()) {
                    for (Cell next : grid.openNeighbors(queue.remove())) {
                        if (reached.add(next)) queue.add(next);
                    }
                }
                assertEquals(grid.size(), reached.size(), context);
            }
        }
    }

    private static int root(int[] parent, int index) {
        while (parent[index] != index) {
            parent[index] = parent[parent[index]];
            index = parent[index];
        }
        return index;
    }

    @ParameterizedTest
    @EnumSource(GenerationAlgorithm.class)
    void sameSeedProducesIdenticalGridAndEventOrder(GenerationAlgorithm algorithm) {
        for (long seed = -10; seed <= 10; seed++) {
            assertEquals(MazeAlgorithms.generate(algorithm, 17, 23, seed),
                    MazeAlgorithms.generate(algorithm, 17, 23, seed));
        }
    }
}
