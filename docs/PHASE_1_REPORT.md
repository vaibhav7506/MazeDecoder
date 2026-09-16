# Phase 1 verification report

Verified on 2026-09-17 using Oracle Java 21.0.1 on Windows. Source and bytecode
target Java 17 (`maven.compiler.release=17`); this run did not execute under a
Java 17 JVM. Maven 3.9.16 was launched through the checked-in wrapper.

Command:

```powershell
.\mvnw.cmd -B verify
```

Actual output from the final verification run (excerpt):

```text
[INFO] Running dev.mazedecoder.core.GridTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.209 s -- in dev.mazedecoder.core.GridTest
[INFO] Running dev.mazedecoder.core.MazeGenerationTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.930 s -- in dev.mazedecoder.core.MazeGenerationTest
[INFO] Running dev.mazedecoder.core.PathfindingTest
[INFO] Tests run: 20, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.451 s -- in dev.mazedecoder.core.PathfindingTest
[INFO] Tests run: 29, Failures: 0, Errors: 0, Skipped: 0
[INFO] --- jar:3.5.1:jar (default-jar) @ maze-core ---
[INFO] Building jar: C:\Users\vs250\Documents\ChatGPT\MazeDecoder\target\maze-core-1.0.0-SNAPSHOT.jar
[INFO] BUILD SUCCESS
[INFO] Total time:  9.291 s
[INFO] Finished at: 2026-09-17T01:44:43+05:30
```

Raw local evidence: `target/phase-1-verify.log` and JUnit XML/text reports under
`target/surefire-reports/`. Generated build artifacts are ignored by Git.

## What the tests establish

| Requirement | Actual checks |
| --- | --- |
| Perfect generation | All 3 generators × 7 dimensions × 6 seeds = 126 cases. Connectivity, V−1 edges, per-event cycle detection, symmetric walls, closed boundaries, all cells visited. Includes 1×1, narrow grids, rectangular grids, and 100×100. |
| Replay | Every carve event is reapplied to an initially closed grid; the complete snapshot, including visited flags, must equal the returned grid. |
| Determinism | Each generator is repeated for 21 seeds; both grids and ordered event lists must match exactly. |
| Known path | All 4 searches return the exact hand-built corridor path and exploration sequence. |
| DFS distinction | A hand-built cycle has routes of 2 and 6 edges. BFS/Dijkstra/A* return 2; DFS returns 6. |
| A* admissibility | Production heuristic compared with independent exact distances on an open grid and a perfect maze, plus edge consistency and zero at the goal. On the open grid Manhattan equals exact distance, so inflation above it fails the bound. |
| Shortest paths with alternatives | BFS/Dijkstra/A* agree with an independent distance computation on 100 randomly carved cyclic/disconnected grids. Perfect mazes alone would not test route choice. |
| Identical maze across runs | All 4 solvers on 36 generated mazes; identical unique final path, no grid mutation, repeatable exploration order. |
| Scale | Each solver handles a 100×100 DFS-generated maze; iterative generation and traversal avoid recursion depth limits. |
| Exploration contract | A dead-end branch appears in exploration but not the final path; exact event order and statistics checked. |
| Cost extension | Dijkstra and A* choose a cheaper four-edge route over an expensive two-edge route; fractional and zero costs supported. Invalid costs and weighted BFS/DFS rejected. |
| Edge cases | Unreachable goal, start=end, custom endpoints, invalid dimensions and carves, immutable results. |

The admissibility test is a direct inequality check against independent exact
distances; no mutation-testing tool was run. Test durations are environment
dependent and are not performance claims.

## Scope and handoff

This deliverable is only the standalone library, its tests, Maven wrapper, and
usage documentation. There are no HTTP, WebSocket, Spring, or frontend dependencies.
Phase 2 should wrap `MazeAlgorithms.generate()` and `MazeAlgorithms.solve()`;
it must establish its own streaming protocol, pacing, and eviction policy.
The final documentation audit remains deferred as directed by the user.
