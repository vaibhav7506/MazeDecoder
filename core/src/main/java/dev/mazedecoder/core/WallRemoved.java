package dev.mazedecoder.core;

/** One shared wall removal. List position is the zero-based replay sequence. */
public record WallRemoved(Cell from, Cell to) { }
