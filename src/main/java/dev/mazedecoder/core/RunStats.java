package dev.mazedecoder.core;

/** Path length counts edges; cost excludes the start cell. Duration uses System.nanoTime. */
public record RunStats(int cellsExplored, int pathLength, double pathCost, long elapsedNanos) { }
