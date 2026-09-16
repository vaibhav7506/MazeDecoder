# Pathfinding & Maze Algorithm Visualizer — 3-Phase Build Prompt

A Java (Spring Boot) backend implementing maze generation and pathfinding
algorithms, streaming step-by-step progress over WebSocket to a browser
frontend that renders it live. Deliberately scoped to be finishable in
2-3 weeks — do not expand scope mid-build. Hand each phase to Claude Code
as its own session; Phase 2 depends on Phase 1's algorithm interfaces,
Phase 3 depends on Phase 2's streaming contract.

---

## PHASE 1 — Core Algorithms (Java, no web layer yet)

**Goal:** Correct, fully tested implementations of maze generation and
pathfinding algorithms as a standalone Java library, with zero web/HTTP
dependencies. This phase proves the logic works before any UI exists.

### Prompt

```
Build a Java 17+ library (plain Maven/Gradle module, no Spring dependency
yet) implementing grid-based maze generation and pathfinding, with every
algorithm step recorded for later replay — not just a final result.

GRID MODEL
- Represent the maze as a 2D grid of cells, each with four walls (N/E/S/W)
  that can be open or closed, plus a visited flag used during generation.
- Support configurable grid dimensions (e.g., 10x10 up to at least 100x100)
  and a fixed start cell and end cell (default: top-left to bottom-right).

MAZE GENERATION ALGORITHMS (implement all three)
1. Randomized Depth-First Search (recursive backtracker): carve a passage
   by moving to a random unvisited neighbor, backtracking when stuck.
2. Prim's algorithm (randomized): grow the maze by repeatedly picking a
   random frontier cell and connecting it to the maze.
3. Kruskal's algorithm (randomized): treat each cell as a set, repeatedly
   union random adjacent cells using a union-find structure, until fully
   connected.
- Every generation algorithm must record its own sequence of steps (which
  wall was removed, in what order) as a list of events, not just produce
  a final grid — this recorded sequence is what later gets animated.
- Guarantee (and test) that every generated maze has exactly one path
  between any two cells (a perfect maze: no loops, no isolated regions).

PATHFINDING ALGORITHMS (implement all four)
1. Breadth-First Search (BFS)
2. Depth-First Search (DFS)
3. Dijkstra's algorithm (treat every edge as equal weight for a plain maze,
   but implement it generically enough to support weighted cells later)
4. A* search with Manhattan distance heuristic
- Every pathfinding run must record, in order: every cell visited/explored
  (not just the final path), and separately, the final reconstructed
  shortest path from start to end.
- Every run must also record simple statistics: total cells explored, final
  path length, and wall-clock time taken for that specific run.

CORRECTNESS TESTS
- Maze generation: verify perfectness (single path between any two cells,
  no cycles) across multiple random seeds and grid sizes.
- Pathfinding: on a small, hand-constructed maze with a known shortest path,
  verify all four algorithms return a path of the correct length (BFS/
  Dijkstra/A* must find the SHORTEST path; DFS is not required to, and your
  test should reflect that difference rather than wrongly asserting DFS
  finds the shortest path too).
- Verify Dijkstra and A* agree on path length on unweighted mazes (they
  must, by definition, when the heuristic is admissible) — and add a test
  that would fail if the A* heuristic were not admissible, to prove you
  actually chose a valid one.
- Determinism: given the same random seed, maze generation must produce
  the exact same maze every time — test this explicitly, since the whole
  "race" feature in later phases depends on running multiple algorithms
  against the identical maze.

DELIVERABLE
- A tested Java library exposing: generate(algorithm, width, height, seed)
  -> {finalGrid, stepEvents}, and solve(algorithm, grid) -> {exploredCells,
  finalPath, stats}
- All correctness tests passing, with real output (not placeholder) shown
  in a short report
- No HTTP, no WebSocket, no frontend code in this phase
```

---

## PHASE 2 — Spring Boot API & Real-Time Streaming

**Goal:** Wrap the Phase 1 library in a Spring Boot service that exposes it
over REST and streams step-by-step progress over WebSocket, plus a "race"
mode that runs multiple pathfinding algorithms against the same maze and
reports them side by side.

### Prompt

```
Build a Spring Boot application around the Phase 1 library. Do not
reimplement any algorithm logic here — this phase is purely the web/
streaming layer on top of what Phase 1 already proved correct.

REST ENDPOINTS
- POST /api/maze/generate — body: {algorithm, width, height, seed} — returns
  a mazeId and the final grid immediately (for cases where the caller just
  wants the result, not the animation).
- POST /api/maze/{mazeId}/solve — body: {algorithm} — runs one pathfinding
  algorithm against the specified maze and returns exploredCells, finalPath,
  and stats.
- POST /api/maze/{mazeId}/race — body: {algorithms: [...]} — runs ALL
  specified pathfinding algorithms against the SAME maze (same grid, same
  start/end) and returns each one's exploredCells, finalPath, and stats
  together, so the caller can compare them directly.

WEBSOCKET STREAMING (this is the core of this phase)
- When a client requests maze generation or solving via WebSocket instead
  of plain REST, stream each recorded step event as an individual message
  as soon as it's available — do not batch all steps into one message and
  send them at once, since the whole point is real-time playback on the
  frontend.
- Support a configurable playback speed: the server should be able to pace
  out the step events (e.g., one every N milliseconds) rather than firing
  them all instantly, since a maze with thousands of steps sent all at once
  is not watchable — OR delegate pacing to the frontend and send events as
  fast as possible; pick one approach and be consistent, and document which.
- For race mode over WebSocket, clearly tag each streamed event with which
  algorithm it belongs to, so a client can render multiple algorithms'
  progress simultaneously without mixing up whose step is whose.
- Handle a client disconnecting mid-stream cleanly (no server-side exception
  spam in the logs, no resource leak) — write a test that connects, receives
  a few events, then disconnects abruptly, and verify the server handles it.

STATE MANAGEMENT
- Store generated mazes in memory (a simple concurrent map keyed by mazeId
  is sufficient — do not add a database for this project, it's unnecessary
  complexity for what this needs to do).
- Decide and document a simple eviction policy (e.g., mazes older than N
  minutes or beyond a max count get dropped) so long-running demo instances
  don't leak memory indefinitely.

TESTS
- Integration tests hitting the real REST endpoints (not just unit-testing
  the underlying library again) confirming correct HTTP status codes and
  response shapes.
- A WebSocket integration test that actually connects a test client, sends
  a generate/solve request, and asserts that step events arrive in the
  correct order and that a final completion message is sent.
- A race-mode test with at least 2 algorithms confirming both produce
  correctly tagged, interleaved (or however you chose to send them) event
  streams without cross-contamination.

DELIVERABLE
- A running Spring Boot service exposing the REST endpoints and WebSocket
  streaming described above
- All integration/WebSocket tests passing with real output shown
- No frontend code yet — verify this phase using a simple WebSocket test
  client (a script, or even a browser dev console websocket connection) and
  show the actual raw messages received, proving the stream works before
  building any UI on top of it
```

---

## PHASE 3 — Frontend Visualization & Deployment

**Goal:** A browser frontend that renders the maze grid, animates generation
and solving live from the WebSocket stream, displays the race-mode
comparison, and is deployed publicly alongside the backend.

### Prompt

```
Build a frontend (plain HTML/CSS/JS with Canvas, or a lightweight framework
if you prefer — keep the dependency footprint small since this doesn't need
a heavy framework) that connects to the Phase 2 backend and visualizes it.

CORE VISUALIZATION
- Render the grid on a Canvas element, with walls drawn as lines between
  cells.
- On receiving a maze-generation step event from the WebSocket, visually
  "carve" that wall in near-real-time (respecting whatever pacing approach
  Phase 2 chose).
- On receiving a pathfinding step event, color each explored cell as it's
  reported (a common convention: a gradient from the start color to a
  "frontier" color as cells are explored), and once the final path arrives,
  draw it distinctly (e.g., a solid line or highlighted cells) over the
  explored-cell coloring.

RACE MODE UI
- Allow the user to select 2 or more pathfinding algorithms and trigger a
  race on the same maze.
- Render each algorithm's progress in its own panel (side-by-side grids)
  rather than overlaying them on one grid, so the visual comparison is
  unambiguous about which algorithm did what.
- After all algorithms finish, display a results table: algorithm name,
  cells explored, path length, time taken — sorted so the fastest/most
  efficient result is easy to spot at a glance.

CONTROLS
- Grid size selector, maze-generation algorithm selector, random seed input
  (with a "randomize" button) so the same maze can be reproduced or shared.
- Playback speed control if pacing is server-side; if pacing is client-side
  (per Phase 2's documented choice), implement the pacing logic here instead.
- A "reset" control that clears the current visualization without needing
  a full page reload.

RESPONSIVENESS AND POLISH
- The grid should scale reasonably across common viewport sizes — it does
  not need to be pixel-perfect on every device, but it should not be
  unusable on a laptop screen at a reasonable browser window size.
- Handle the WebSocket connection dropping (e.g., if the Render backend
  cold-starts or briefly disconnects) with a visible reconnect indicator
  rather than a silently frozen UI.

DEPLOYMENT
- Deploy the Spring Boot backend to Render (free tier), following the same
  process already used for the RunbookOS project.
- Deploy the frontend as a static site to Vercel (free tier).
- Update the frontend's WebSocket/API base URL to point at the deployed
  Render backend, not localhost.
- Test the full deployed flow yourself end to end (generate a maze, run a
  race) before considering this phase done — do not declare deployment
  complete without having actually watched it work in a real browser
  against the real deployed backend, the same standard applied to
  RunbookOS's verification.

DELIVERABLE
- A publicly reachable, working demo: a live URL where anyone can generate
  a maze, watch it build, and race multiple pathfinding algorithms against
  it with real-time visualization and a results comparison table
- A short README section noting the expected Render cold-start delay and
  recommending the visitor wait a few seconds on first load, same honest
  disclosure pattern used in your other deployed projects
```

---

## Note on scope

This is intentionally smaller than MatchEngine or the Options Risk Engine.
There is no concurrency-heavy systems work, no protocol parsing, no
numerical methods requiring careful correctness proofs. That is deliberate —
the goal this time is a finished, deployed, visually demonstrable project,
not another partially-built ambitious one. Do not add a fourth phase (3D
mazes, weighted terrain, more algorithms) until Phases 1-3 are fully done,
tested, and deployed.