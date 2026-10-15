package dev.mazedecoder.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

final class MazeGenerator {
    private MazeGenerator() { }

    static GenerationResult generate(GenerationAlgorithm algorithm, int width, int height, long seed) {
        Objects.requireNonNull(algorithm);
        Grid shape = Grid.builder(width, height).build();
        Grid.Builder builder = Grid.builder(width, height).visit(shape.start());
        List<WallRemoved> events = new ArrayList<>(shape.size() - 1);
        Random random = new Random(seed);
        switch (algorithm) {
            case DFS -> depthFirst(shape, builder, events, random);
            case PRIM -> prim(shape, builder, events, random);
            case KRUSKAL -> kruskal(shape, builder, events, random);
        }
        return new GenerationResult(builder.build(), events);
    }

    private static void carve(Grid.Builder builder, List<WallRemoved> events, Cell from, Cell to) {
        builder.carve(from, to).visit(from).visit(to);
        events.add(new WallRemoved(from, to));
    }

    private static void depthFirst(Grid shape, Grid.Builder builder, List<WallRemoved> events, Random random) {
        // Explicit stack avoids Java stack overflow on long 100x100 corridors.
        ArrayDeque<Cell> stack = new ArrayDeque<>();
        stack.push(shape.start());
        while (!stack.isEmpty()) {
            Cell current = stack.peek();
            List<Cell> next = shape.neighbors(current).stream().filter(c -> !builder.visited(c)).toList();
            if (next.isEmpty()) stack.pop();
            else {
                Cell chosen = next.get(random.nextInt(next.size()));
                carve(builder, events, current, chosen);
                stack.push(chosen);
            }
        }
    }

    private static void prim(Grid shape, Grid.Builder builder, List<WallRemoved> events, Random random) {
        List<Cell> frontier = new ArrayList<>();
        boolean[] queued = new boolean[shape.size()];
        addFrontier(shape, builder, shape.start(), frontier, queued);
        while (!frontier.isEmpty()) {
            int pick = random.nextInt(frontier.size());
            Cell chosen = frontier.get(pick);
            frontier.set(pick, frontier.get(frontier.size() - 1));
            frontier.remove(frontier.size() - 1);
            List<Cell> connected = shape.neighbors(chosen).stream().filter(builder::visited).toList();
            carve(builder, events, connected.get(random.nextInt(connected.size())), chosen);
            addFrontier(shape, builder, chosen, frontier, queued);
        }
    }

    private static void addFrontier(Grid shape, Grid.Builder builder, Cell cell, List<Cell> frontier, boolean[] queued) {
        for (Cell neighbor : shape.neighbors(cell)) {
            int index = shape.index(neighbor);
            if (!builder.visited(neighbor) && !queued[index]) {
                queued[index] = true;
                frontier.add(neighbor);
            }
        }
    }

    private static void kruskal(Grid shape, Grid.Builder builder, List<WallRemoved> events, Random random) {
        List<WallRemoved> edges = new ArrayList<>();
        for (int i = 0; i < shape.size(); i++) {
            Cell from = shape.cell(i);
            for (Direction direction : List.of(Direction.EAST, Direction.SOUTH)) {
                Cell to = direction.from(from);
                if (shape.contains(to)) edges.add(new WallRemoved(from, to));
            }
        }
        Collections.shuffle(edges, random);
        UnionFind sets = new UnionFind(shape.size());
        for (WallRemoved edge : edges) {
            if (sets.union(shape.index(edge.from()), shape.index(edge.to()))) {
                carve(builder, events, edge.from(), edge.to());
                if (events.size() == shape.size() - 1) break;
            }
        }
    }

    private static final class UnionFind {
        private final int[] parent;
        private final byte[] rank;
        UnionFind(int size) {
            parent = new int[size];
            rank = new byte[size];
            for (int i = 0; i < size; i++) parent[i] = i;
        }
        int find(int value) {
            while (value != parent[value]) {
                parent[value] = parent[parent[value]];
                value = parent[value];
            }
            return value;
        }
        boolean union(int a, int b) {
            a = find(a);
            b = find(b);
            if (a == b) return false;
            if (rank[a] < rank[b]) parent[a] = b;
            else {
                parent[b] = a;
                if (rank[a] == rank[b]) rank[a]++;
            }
            return true;
        }
    }
}
