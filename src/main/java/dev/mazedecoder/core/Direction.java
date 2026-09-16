package dev.mazedecoder.core;

/** The declaration order also fixes search tie-breaking. */
public enum Direction {
    NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);

    private final int dx;
    private final int dy;

    Direction(int dx, int dy) { this.dx = dx; this.dy = dy; }
    public Cell from(Cell cell) { return new Cell(cell.x() + dx, cell.y() + dy); }
    public Direction opposite() { return values()[(ordinal() + 2) % 4]; }
    int mask() { return 1 << ordinal(); }
}
