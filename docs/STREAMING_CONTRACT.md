# Phase 2 API and WebSocket contract

Implementation: `server/src/main/java/dev/mazedecoder/server/`.
This is the contract for the future Phase 3 frontend; no frontend exists yet.

## REST

All requests use JSON and enum names are case-sensitive.

| Endpoint | Body | Success response |
| --- | --- | --- |
| `POST /api/maze/generate` | `{"algorithm":"PRIM","width":20,"height":20,"seed":42}` | 201: `{mazeId, finalGrid}` |
| `POST /api/maze/{mazeId}/solve` | `{"algorithm":"ASTAR"}` | 200: `{exploredCells, finalPath, stats}` |
| `POST /api/maze/{mazeId}/race` | `{"algorithms":["BFS","DFS","DIJKSTRA","ASTAR"]}` | 200: `{mazeId, results: {BFS: {exploredCells, finalPath, stats}, ...}}` |

`MazeController` calls `MazeService`, which delegates to the core API. A race
captures one immutable grid and uses it for all runs. It computes the algorithms
sequentially; this is a visualization comparison, not a parallel CPU benchmark.
Statistics measure the individual core run, excluding network/playback time.

Generation requires all four fields, with each dimension in 1–100. Generation
algorithms: `DFS`, `PRIM`, `KRUSKAL`. Search algorithms: `BFS`, `DFS`, `DIJKSTRA`,
`ASTAR`. A race requires 2–4 distinct algorithms. Missing fields, invalid enums,
fractional dimensions, and unknown JSON fields are rejected with 400.
Unknown/expired maze IDs return 404. Error bodies are `{code, message}`, using
`INVALID_REQUEST` or `MAZE_NOT_FOUND`.

`finalGrid` is `{width, height, start, end, cells}`. `cells` is a flat row-major
array (`index = y * width + x`), each entry being:

```json
{"x":0,"y":0,"walls":{"north":true,"east":false,"south":true,"west":true},"visited":true}
```

Wall `true` means closed. Coordinates, path-length semantics, and statistics are
described in the root README. Grid DTOs avoid adding JSON dependencies to core.
Java seeds are signed 64-bit integers; browser callers should use integers in
JavaScript's safe integer range if they serialize seeds as JSON numbers.

## WebSocket commands

Connect to `ws://localhost:8080/ws/maze` (use `wss://` with HTTPS in deployment).
Send plain JSON text frames, without STOMP or SockJS:

```json
{"requestId":"generate-1","operation":"generate","algorithm":"PRIM","width":20,"height":20,"seed":42}
{"requestId":"solve-1","operation":"solve","mazeId":"returned-id","algorithm":"ASTAR"}
{"requestId":"race-1","operation":"race","mazeId":"returned-id","algorithms":["BFS","ASTAR"]}
```

Use a fresh request ID per command, 1–80 characters. One request may be outstanding
per connection. Send the next command after `complete` or a terminal error.
A premature command receives `BUSY` for that command while the current request
continues. Malformed/unreadable commands receive `INVALID_REQUEST` with a null
request ID because a command could not be decoded. Incoming text is capped at 4 KiB.

Each outgoing frame is one JSON object with these fields (null values are explicit):

```json
{"type":"step","requestId":"race-1","operation":"race","mazeId":"returned-id","algorithm":"BFS","sequence":0,"data":{"x":0,"y":0}}
```

| Type | Algorithm / sequence | Data |
| --- | --- | --- |
| `started` | Generation algorithm, or null for solve/race; sequence null | `{width,height,start,end,pacing:"client"}` |
| `step` during generation | Generator name; zero-based carve index | `{from:{x,y},to:{x,y}}` |
| `step` during solve/race | Search name; zero-based exploration index **per algorithm** | `{x,y}` |
| `algorithm_complete` | Search name; sequence null | `{finalPath:[{x,y},...],stats:{cellsExplored,pathLength,pathCost,elapsedNanos}}` |
| `complete` during generation | Generator name; sequence null | Full `finalGrid` shape |
| `complete` during solve/race | Algorithm and sequence null | null |
| `error` | Algorithm and sequence null | `{code,message}` |

Generation sends `started`, N−1 individual `step` frames, and `complete`. A 1×1
maze sends only `started` and `complete`. Solve/race sends `started`, exploration
steps and one `algorithm_complete` per selected algorithm, then a single `complete`.
No completion frame repeats the explored-cell list.

For a race, `MazeSocketHandler.search()` sends exploration step 0 for each selected
algorithm in request order, then step 1 for each, and so on. Finished algorithms
drop out of this round-robin order. Their `algorithm_complete` frame immediately
follows their last exploration step. Route each algorithm's events to its own
panel using `(requestId, mazeId, algorithm)`; sequence numbers are local to that
algorithm, never global across the race.

## Playback choice and computation

**Playback is client-paced.** Core returns its recorded result synchronously.
Generation frames begin after generation finishes. Solve/race sends `started`,
computes each selected result, then sends the recorded events individually as
fast as the socket permits. This is streaming replay of recorded steps, not
incremental computation inside the core algorithm. No sleeps or playback timers
hold server workers while someone watches an animation.

The frontend must queue frames, render them at the chosen speed, and delay drawing
the final path or final grid until the preceding queued steps have played. A
received `complete` means delivery is done, not that the animation has finished.
Playback speeds do not affect the reported algorithm timings. Reset must clear
queues and ignore frames from superseded request IDs.

## Storage, disconnects, and limits

`MazeStore` retains only final immutable grids, not replay results. A synchronized
insertion-ordered map enforces a hard default maximum of 100 mazes, evicting the
oldest-created entry first. Entries expire 30 minutes after creation; reads do
not extend this. Expiry is checked on each get/put and swept every 60 seconds.
The active request retains its snapshot even if that maze is subsequently evicted.
Restarting the service loses all maze IDs. There is no database.

`MazeSocketHandler` uses four workers and a queue of 16 pending jobs. Overflow
returns `SERVER_BUSY`; the connection remains usable. A disconnected client's
job is cancelled and removed from the queue, its connection state is removed,
and sending stops. There are no per-client timer threads. Tomcat blocking sends
have a five-second timeout for stalled clients. Core computations do not expose
interrupt callbacks; an already-running computation may finish its bounded
100×100 work before the next cancellation check stops delivery. A generation
that already completed can remain in the bounded store until eviction.

There is no automatic stream resume on reconnect. A frontend should indicate
disconnection, discard incomplete playback, connect again, and issue a fresh
request ID. Solve/race may reuse a retained maze ID; on `MAZE_NOT_FOUND`, regenerate
using the original algorithm, dimensions, and seed. A generation interrupted
before delivery can be regenerated from those inputs.

## Configuration and local verification

`application.properties` provides defaults; environment variables can override them:

| Variable | Default |
| --- | --- |
| `PORT` | `8080` |
| `MAZE_STORE_MAX_COUNT` | `100` |
| `MAZE_STORE_TTL` | `PT30M` |
| `MAZE_STORE_CLEANUP_MS` | `60000` |
| `MAZE_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:3000` |

The origin allowlist applies to browser WebSocket handshakes and REST CORS. It
must be set to the deployed frontend's exact origin in Phase 3. Same-origin
WebSocket requests are also accepted by Spring; non-browser clients without an
Origin header can connect.

```powershell
.\mvnw.cmd verify
java -jar server/target/maze-server-1.0.0-SNAPSHOT.jar
# In another terminal, with the server running:
java scripts/WebSocketSmoke.java
```

The smoke client prints unmodified incoming JSON frames and verifies generation,
solve, and both race completions. It needs only JDK 17+, not npm/Python packages.
Spring Boot 4.1.1 supports Java 17+: see the official
[system requirements](https://docs.spring.io/spring-boot/system-requirements.html).
