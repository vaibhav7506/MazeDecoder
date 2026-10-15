package dev.mazedecoder.core;

import java.util.List;
import java.util.Objects;

public record GenerationResult(Grid finalGrid, List<WallRemoved> stepEvents) {
    public GenerationResult {
        Objects.requireNonNull(finalGrid);
        stepEvents = List.copyOf(stepEvents);
    }
}
