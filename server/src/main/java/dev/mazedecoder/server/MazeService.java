package dev.mazedecoder.server;

import dev.mazedecoder.core.*;
import org.springframework.stereotype.Service;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import static dev.mazedecoder.server.ApiModels.*;

@Service
public class MazeService {
    public record Generated(String mazeId, GenerationResult result) { }
    private final MazeStore store;
    public MazeService(MazeStore store) { this.store = store; }

    public Generated generate(GenerateRequest request) {
        if (request == null || request.algorithm() == null || request.width() == null || request.height() == null
                || request.seed() == null) throw new IllegalArgumentException("algorithm, width, height, and seed are required");
        if (request.width() < 1 || request.width() > 100 || request.height() < 1 || request.height() > 100)
            throw new IllegalArgumentException("width and height must each be between 1 and 100");
        GenerationResult result = MazeAlgorithms.generate(request.algorithm(), request.width(), request.height(), request.seed());
        return new Generated(store.put(result.finalGrid()), result);
    }
    public Grid grid(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("mazeId is required");
        return store.get(id);
    }
    public SolveResult solve(String id, SolveRequest request) {
        if (request == null || request.algorithm() == null) throw new IllegalArgumentException("algorithm is required");
        return MazeAlgorithms.solve(request.algorithm(), grid(id));
    }
    static List<SearchAlgorithm> validateRace(List<SearchAlgorithm> algorithms) {
        if (algorithms == null || algorithms.size() < 2 || algorithms.size() > 4 || algorithms.contains(null)
                || new HashSet<>(algorithms).size() != algorithms.size())
            throw new IllegalArgumentException("Select 2 to 4 distinct algorithms");
        return List.copyOf(algorithms);
    }
    public RaceResponse race(String id, RaceRequest request) {
        List<SearchAlgorithm> algorithms = validateRace(request == null ? null : request.algorithms());
        Grid grid = grid(id); // One immutable snapshot shared by every run, even if the store later evicts it.
        var results = new LinkedHashMap<SearchAlgorithm, SolveResult>();
        for (SearchAlgorithm algorithm : algorithms) results.put(algorithm, MazeAlgorithms.solve(algorithm, grid));
        return new RaceResponse(id, results);
    }
}
