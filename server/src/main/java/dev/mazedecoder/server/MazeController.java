package dev.mazedecoder.server;

import dev.mazedecoder.core.SolveResult;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import static dev.mazedecoder.server.ApiModels.*;

@RestController
@RequestMapping("/api/maze")
public class MazeController {
    private final MazeService service;
    public MazeController(MazeService service) { this.service = service; }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public GenerateResponse generate(@RequestBody GenerateRequest request) {
        MazeService.Generated generated = service.generate(request);
        return new GenerateResponse(generated.mazeId(), GridView.from(generated.result().finalGrid()));
    }
    @PostMapping("/{mazeId}/solve")
    public SolveResult solve(@PathVariable("mazeId") String mazeId, @RequestBody SolveRequest request) {
        return service.solve(mazeId, request);
    }
    @PostMapping("/{mazeId}/race")
    public RaceResponse race(@PathVariable("mazeId") String mazeId, @RequestBody RaceRequest request) {
        return service.race(mazeId, request);
    }
}
