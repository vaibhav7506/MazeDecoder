package dev.mazedecoder.core;

import java.util.List;
import java.util.Objects;

public record SolveResult(List<Cell> exploredCells, List<Cell> finalPath, RunStats stats) {
    public SolveResult {
        exploredCells = List.copyOf(exploredCells);
        finalPath = List.copyOf(finalPath);
        Objects.requireNonNull(stats);
    }
    public boolean reachedEnd() { return !finalPath.isEmpty(); }
}
