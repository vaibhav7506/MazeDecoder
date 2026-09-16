package dev.mazedecoder.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Immutable maze snapshot. Only Builder can change walls or generation flags. */
public final class Grid {
    private final int width;
    private final int height;
    private final int[] walls;
    private final boolean[] visited;
    private final Cell start;
    private final Cell end;

    private Grid(Builder builder) {
        width = builder.width;
        height = builder.height;
        walls = builder.walls.clone();
        visited = builder.visited.clone();
        start = builder.start;
        end = builder.end;
    }

    public static Builder builder(int width, int height) { return new Builder(width, height); }
    public int width() { return width; }
    public int height() { return height; }
    public int size() { return walls.length; }
    public Cell start() { return start; }
    public Cell end() { return end; }
    public boolean contains(Cell cell) {
        return cell != null && cell.x() >= 0 && cell.x() < width && cell.y() >= 0 && cell.y() < height;
    }
    public boolean hasWall(Cell cell, Direction direction) {
        return (walls[index(cell)] & Objects.requireNonNull(direction).mask()) != 0;
    }
    public boolean visited(Cell cell) { return visited[index(cell)]; }
    public List<Cell> neighbors(Cell cell) {
        index(cell);
        List<Cell> result = new ArrayList<>(4);
        for (Direction direction : Direction.values()) {
            Cell neighbor = direction.from(cell);
            if (contains(neighbor)) result.add(neighbor);
        }
        return List.copyOf(result);
    }
    public List<Cell> openNeighbors(Cell cell) {
        index(cell);
        List<Cell> result = new ArrayList<>(4);
        for (Direction direction : Direction.values()) {
            if (!hasWall(cell, direction)) result.add(direction.from(cell));
        }
        return List.copyOf(result);
    }
    public Grid withEndpoints(Cell start, Cell end) {
        Builder builder = new Builder(width, height).endpoints(start, end);
        System.arraycopy(walls, 0, builder.walls, 0, size());
        System.arraycopy(visited, 0, builder.visited, 0, size());
        return builder.build();
    }
    int index(Cell cell) {
        if (!contains(cell)) throw new IllegalArgumentException("Cell outside grid: " + cell);
        return cell.y() * width + cell.x();
    }
    Cell cell(int index) { return new Cell(index % width, index / width); }

    @Override public boolean equals(Object other) {
        return other instanceof Grid grid && width == grid.width && height == grid.height
                && start.equals(grid.start) && end.equals(grid.end)
                && Arrays.equals(walls, grid.walls) && Arrays.equals(visited, grid.visited);
    }
    @Override public int hashCode() {
        return Objects.hash(width, height, start, end, Arrays.hashCode(walls), Arrays.hashCode(visited));
    }

    public static final class Builder {
        private final int width;
        private final int height;
        private final int[] walls;
        private final boolean[] visited;
        private Cell start = new Cell(0, 0);
        private Cell end;

        private Builder(int width, int height) {
            if (width <= 0 || height <= 0 || (long) width * height > Integer.MAX_VALUE)
                throw new IllegalArgumentException("Positive dimensions with an int-sized cell count are required");
            this.width = width;
            this.height = height;
            walls = new int[width * height];
            Arrays.fill(walls, 15);
            visited = new boolean[walls.length];
            end = new Cell(width - 1, height - 1);
        }
        private int index(Cell cell) {
            if (cell == null || cell.x() < 0 || cell.x() >= width || cell.y() < 0 || cell.y() >= height)
                throw new IllegalArgumentException("Cell outside grid: " + cell);
            return cell.y() * width + cell.x();
        }
        public Builder endpoints(Cell start, Cell end) {
            index(start);
            index(end);
            this.start = start;
            this.end = end;
            return this;
        }
        public Builder visit(Cell cell) { visited[index(cell)] = true; return this; }
        public boolean visited(Cell cell) { return visited[index(cell)]; }
        /** Removes both sides of a shared wall; outside and diagonal cells are rejected. */
        public Builder carve(Cell from, Cell to) {
            int a = index(from);
            int b = index(to);
            for (Direction direction : Direction.values()) {
                if (direction.from(from).equals(to)) {
                    walls[a] &= ~direction.mask();
                    walls[b] &= ~direction.opposite().mask();
                    return this;
                }
            }
            throw new IllegalArgumentException("Carving requires orthogonally adjacent cells");
        }
        public Grid build() { return new Grid(this); }
    }
}
