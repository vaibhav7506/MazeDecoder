package dev.mazedecoder.core;

import java.util.Objects;
import java.util.function.ToDoubleFunction;

/** Stateless public API; separate runs share no mutable algorithm state. */
public final class MazeAlgorithms {
    private MazeAlgorithms() { }

    public static GenerationResult generate(GenerationAlgorithm algorithm, int width, int height, long seed) {
        return MazeGenerator.generate(algorithm, width, height, seed);
    }

    public static SolveResult solve(SearchAlgorithm algorithm, Grid grid) {
        return Pathfinder.solve(algorithm, grid, cell -> 1.0);
    }

    /**
     * Extension point for weighted cells: cost to enter a cell, excluding the start.
     * Evaluated once per cell. A* scales Manhattan distance by the minimum entry cost,
     * preserving admissibility even with fractional or zero costs.
     */
    public static SolveResult solve(SearchAlgorithm algorithm, Grid grid, ToDoubleFunction<Cell> entryCost) {
        if (algorithm != SearchAlgorithm.DIJKSTRA && algorithm != SearchAlgorithm.ASTAR)
            throw new IllegalArgumentException("Weighted search requires DIJKSTRA or ASTAR");
        return Pathfinder.solve(algorithm, grid, Objects.requireNonNull(entryCost));
    }
}
