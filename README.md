# MazeDecoder

A pathfinding and maze algorithm visualizer built in three separately committed phases.

**Current status: Phases 1–2 implemented — core library and Spring Boot API.**
Phase 3 (browser visualization and deployment) and the final documentation audit
are pending. There is no deployed demo yet.

## Build and test

Requires JDK 17 or newer. The Maven wrapper downloads Maven 3.9.16 on first use;
the first build also needs internet access for build plugins and JUnit.

```powershell
.\mvnw.cmd verify
```

On Linux/macOS:

```sh
./mvnw verify
```

The standalone library JAR is `core/target/maze-core-1.0.0-SNAPSHOT.jar`. It has
**zero runtime dependencies**. JUnit is test-only in that module. The runnable
server is `server/target/maze-server-1.0.0-SNAPSHOT.jar`. Test reports are in each
module's `target/surefire-reports/`. Recorded verification:
[Phase 1](docs/PHASE_1_REPORT.md), [Phase 2](docs/PHASE_2_REPORT.md).

```powershell
java -jar server/target/maze-server-1.0.0-SNAPSHOT.jar
# In another terminal:
java scripts/WebSocketSmoke.java
```

The server listens on port 8080 by default (`PORT` overrides it), with REST under
`/api/maze` and WebSocket at `/ws/maze`. There is no page at `/` yet.
See [the API and streaming contract](docs/STREAMING_CONTRACT.md) for request/response
examples, race tagging, client-paced playback, bounded storage, and configuration.

## Public API

```java
import dev.mazedecoder.core.*;

GenerationResult maze = MazeAlgorithms.generate(GenerationAlgorithm.PRIM, 20, 20, 42L);
SolveResult solution = MazeAlgorithms.solve(SearchAlgorithm.ASTAR, maze.finalGrid());

for (WallRemoved step : maze.stepEvents()) {
    // Animate removal of the shared wall between step.from() and step.to().
}
for (Cell cell : solution.exploredCells()) {
    // Animate explored cells in this exact order.
}
System.out.println(solution.finalPath());
System.out.println(solution.stats());
```

Generation supports randomized `DFS`, `PRIM`, and `KRUSKAL`. Solving supports
`BFS`, `DFS`, `DIJKSTRA`, and `ASTAR`. The same generation algorithm, dimensions,
and seed reproduce the same grid **and event order**. Algorithms use local run
state, and grids and returned event/path lists are immutable, so multiple searches
can safely use one grid. Search does not modify generation's visited flags.

## Grid and replay contract

- Coordinates are zero-based `(x, y)`, east and south positive. Each cell has
  north/east/south/west walls and a generation visited flag.
- All walls start closed. Carving removes both sides of a shared wall. Outer
  boundaries remain closed. Dimensions must be positive; 1×1, narrow corridors,
  rectangular mazes, and 100×100 grids are tested.
- Start defaults to `(0, 0)` and end to `(width - 1, height - 1)`. Use
  `grid.withEndpoints(start, end)` for an immutable copy with custom endpoints.
  Use `Grid.builder(width, height).carve(from, to).build()` for hand-built mazes.
- A `WallRemoved(from, to)` records one carve; its list index is its replay
  sequence. Start from a closed grid with the top-left cell visited, apply each
  carve, and mark both endpoints visited to reconstruct the full generation
  snapshot. A 1×1 maze has no carve events and its sole cell is visited.
- `exploredCells` records a cell when removed from the search worklist and
  explored/settled, including start and the reached goal. Each cell appears at
  most once. This is separate from `finalPath`, which includes both endpoints.
- `stats.pathLength` counts **edges**, not cells. `cellsExplored` counts replay
  exploration events. `elapsedNanos` measures one run using monotonic
  `System.nanoTime()`, including search setup and path reconstruction, excluding
  generation. It is an elapsed duration, not a timestamp or benchmark guarantee.
- An unreachable goal yields an empty path, `reachedEnd() == false`, and zero
  length/cost. Start equal to end yields a one-cell path and zero length/cost.
- BFS, Dijkstra, and A* find shortest paths on unweighted grids. DFS finds a
  valid path but need not find the shortest on cyclic grids. On perfect mazes
  all four necessarily return the same unique path.

## Correctness choices

DFS generation uses an explicit stack to avoid recursion overflow. Prim samples
unique frontier cells, then a visited neighbor. Kruskal shuffles each undirected
edge once and uses union-find with path compression and rank. Each generator
carves exactly `cellCount - 1` edges and produces a connected grid; together
these properties prove there is exactly one path between every pair of cells.

A* uses Manhattan distance because movement is orthogonal. Every move can reduce
Manhattan distance by at most one, and walls can only increase travel distance.
Tests check admissibility against independently computed exact distances,
consistency across edges, and shortest paths on cyclic grids. An inflated
heuristic fails the open-grid admissibility check.

Dijkstra and A* also accept a `ToDoubleFunction<Cell>` entry-cost callback as a
library extension point for future weighted cells. No terrain feature is built.
Costs are evaluated once per cell, exclude the start, and must be finite,
nonnegative, and at most `Double.MAX_VALUE / (2 * cellCount)` to keep priorities
finite. A* scales Manhattan distance by the minimum entry cost; zero costs
reduce it to Dijkstra. BFS/DFS reject this weighted overload. Priority ties use
insertion order; neighbor order is north, east, south, west.

## Phase boundaries

The original [build prompt](docs/BUILD_PROMPT.md) is retained as the scope contract.
Commit each phase separately. The [documentation audit prompt](docs/DOCUMENTATION_PROMPT.md)
is intentionally deferred until **all three phases are built and deployed**.
At that point re-read the actual code and Git history, produce root-level
`WORKFLOW.md` and `ARCHITECTURE_DECISIONS.md`, surface inconsistencies, and commit
those two files separately from implementation. Keep them current thereafter.
