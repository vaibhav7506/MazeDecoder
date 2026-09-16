package dev.mazedecoder.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.function.ToDoubleFunction;

final class Pathfinder {
    private Pathfinder() { }

    static SolveResult solve(SearchAlgorithm algorithm, Grid grid, ToDoubleFunction<Cell> entryCost) {
        long started = System.nanoTime();
        Objects.requireNonNull(algorithm);
        Objects.requireNonNull(grid);
        double[] costs = new double[grid.size()];
        double minimumCost = Double.POSITIVE_INFINITY;
        for (int i = 0; i < costs.length; i++) {
            double cost = entryCost.applyAsDouble(grid.cell(i));
            if (!Double.isFinite(cost) || cost < 0 || cost > Double.MAX_VALUE / (2.0 * grid.size()))
                throw new IllegalArgumentException("Entry costs must be finite, nonnegative, and allow distance plus heuristic without overflow");
            costs[i] = cost;
            minimumCost = Math.min(minimumCost, cost);
        }
        int[] parent = new int[grid.size()];
        Arrays.fill(parent, -1);
        List<Cell> explored = new ArrayList<>();
        boolean found = switch (algorithm) {
            case BFS, DFS -> traverse(algorithm, grid, parent, explored);
            case DIJKSTRA, ASTAR -> bestFirst(algorithm, grid, costs, minimumCost, parent, explored);
        };
        List<Cell> path = new ArrayList<>();
        if (found) {
            for (int index = grid.index(grid.end()); index != -1; index = parent[index]) path.add(grid.cell(index));
            Collections.reverse(path);
        }
        double pathCost = 0;
        for (int i = 1; i < path.size(); i++) pathCost += costs[grid.index(path.get(i))];
        return new SolveResult(explored, path, new RunStats(explored.size(), Math.max(0, path.size() - 1),
                pathCost, System.nanoTime() - started));
    }

    private static boolean traverse(SearchAlgorithm algorithm, Grid grid, int[] parent, List<Cell> explored) {
        boolean[] discovered = new boolean[grid.size()];
        ArrayDeque<Cell> pending = new ArrayDeque<>();
        pending.add(grid.start());
        discovered[grid.index(grid.start())] = true;
        while (!pending.isEmpty()) {
            Cell current = pending.removeFirst();
            explored.add(current);
            if (current.equals(grid.end())) return true;
            List<Cell> neighbors = new ArrayList<>(grid.openNeighbors(current));
            if (algorithm == SearchAlgorithm.DFS) Collections.reverse(neighbors);
            for (Cell next : neighbors) {
                int index = grid.index(next);
                if (!discovered[index]) {
                    discovered[index] = true;
                    parent[index] = grid.index(current);
                    if (algorithm == SearchAlgorithm.DFS) pending.addFirst(next);
                    else pending.addLast(next);
                }
            }
        }
        return false;
    }

    private record Candidate(int index, double distance, double priority, long sequence) { }

    private static boolean bestFirst(SearchAlgorithm algorithm, Grid grid, double[] costs, double minimumCost,
                                     int[] parent, List<Cell> explored) {
        double[] distance = new double[grid.size()];
        Arrays.fill(distance, Double.POSITIVE_INFINITY);
        boolean[] settled = new boolean[grid.size()];
        PriorityQueue<Candidate> pending = new PriorityQueue<>(Comparator.comparingDouble(Candidate::priority)
                .thenComparingLong(Candidate::sequence));
        int start = grid.index(grid.start());
        distance[start] = 0;
        long sequence = 0;
        pending.add(new Candidate(start, 0, heuristic(algorithm, grid.start(), grid.end(), minimumCost), sequence++));
        while (!pending.isEmpty()) {
            Candidate candidate = pending.remove();
            int index = candidate.index();
            if (settled[index] || candidate.distance() != distance[index]) continue;
            settled[index] = true;
            Cell current = grid.cell(index);
            explored.add(current);
            if (current.equals(grid.end())) return true;
            for (Cell next : grid.openNeighbors(current)) {
                int nextIndex = grid.index(next);
                double proposed = distance[index] + costs[nextIndex];
                if (!settled[nextIndex] && proposed < distance[nextIndex]) {
                    distance[nextIndex] = proposed;
                    parent[nextIndex] = index;
                    double estimate = heuristic(algorithm, next, grid.end(), minimumCost);
                    pending.add(new Candidate(nextIndex, proposed, proposed + estimate, sequence++));
                }
            }
        }
        return false;
    }

    static double heuristic(SearchAlgorithm algorithm, Cell from, Cell goal, double minimumCost) {
        if (algorithm != SearchAlgorithm.ASTAR) return 0;
        return ((long) Math.abs(from.x() - goal.x()) + Math.abs(from.y() - goal.y())) * minimumCost;
    }
}
