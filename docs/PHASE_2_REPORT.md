# Phase 2 verification report

Verified on 2026-09-17 using Java 21.0.1, Java 17 compilation target,
Maven 3.9.16, and Spring Boot 4.1.1 on Windows.

## Delivered

- Maven reactor with an unchanged standalone algorithm implementation in `core/`
  and a Spring Boot application in `server/`. Core retains zero runtime dependencies.
- Real generation, solve, and race REST endpoints with explicit grid DTOs,
  validation, 201/200 success responses, and structured 400/404 errors.
- Plain WebSocket endpoint sending each recorded step in its own text frame.
  Client-paced playback; race exploration steps alternate in request order,
  with independent algorithm sequence numbers and completion frames.
- Bounded in-memory store: 100 grids maximum, FIFO eviction, 30-minute lifetime,
  expiry on access plus a 60-second cleanup sweep.
- Bounded stream workers, queued-job cancellation, quiet disconnect cleanup,
  origin configuration, and a JDK-only raw WebSocket verification client.

See [STREAMING_CONTRACT.md](STREAMING_CONTRACT.md) for the exact wire format and
the Phase 3 client responsibilities. There is no frontend or deployment yet.

## Actual test output

Command: `.\mvnw.cmd -B verify` from the repository root. Excerpt from
`target/phase-2-verify.log`:

```text
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.383 s -- in dev.mazedecoder.core.GridTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.014 s -- in dev.mazedecoder.core.MazeGenerationTest
[INFO] Tests run: 20, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.453 s -- in dev.mazedecoder.core.PathfindingTest
[INFO] Tests run: 29, Failures: 0, Errors: 0, Skipped: 0
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.284 s -- in dev.mazedecoder.server.MazeStoreTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 8.216 s -- in dev.mazedecoder.server.ServerIntegrationTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.398 s -- in dev.mazedecoder.server.SocketIntegrationTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
[INFO] MazeDecoder ........................................ SUCCESS [  0.007 s]
[INFO] maze-core .......................................... SUCCESS [  6.353 s]
[INFO] maze-server ........................................ SUCCESS [ 17.841 s]
[INFO] BUILD SUCCESS
[INFO] Total time:  25.079 s
[INFO] Finished at: 2026-09-17T01:58:25+05:30
```

Total: **41 tests passed**, zero failures/errors/skips. XML/text results live in
`core/target/surefire-reports/` and `server/target/surefire-reports/`.

`ServerIntegrationTest` starts real Tomcat on a random port and uses Java's HTTP
client, not mocked controllers. It checks all generator response shapes and
determinism, all four solvers, four-algorithm races against the same stored maze,
validation/errors, and allowed/denied CORS origins.

`SocketIntegrationTest` connects real Java WebSocket clients. It verifies every
generation carve against the core result, the final grid, same-connection solve,
every tagged round-robin race exploration step and final path, and recoverable
errors. Its abrupt-disconnect test aborts after exactly three generation steps
on a 100×100 maze, waits for zero connections/active workers/queued jobs, asserts
no error/exception logs during that test, and verifies a new connection succeeds.

`MazeStoreTest` checks the count cap, oldest-created eviction despite reads,
expiry at the exact TTL boundary without read extension, idle cleanup, and
invalid store configuration.

## Packaged-service smoke test and raw frames

The executable server JAR was started separately from the test harness. Port
8080 was occupied by another application, so it was started with `--server.port=0`.
Tomcat selected port **1439**; actual Java PID was **2032**. These are local-run
details, not fixed configuration. The server was left running for local use.

```powershell
java -jar server/target/maze-server-1.0.0-SNAPSHOT.jar --server.port=0
java scripts/WebSocketSmoke.java ws://localhost:1439/ws/maze
```

The smoke client exited with code 0 and printed:

```text
SMOKE PASS: generation, solve, and tagged race completed over real WebSockets.
```

Full unmodified command/frame transcript:
[evidence/phase-2-websocket-smoke.log](evidence/phase-2-websocket-smoke.log).
Two consecutive actual race frames demonstrate independent algorithm tags:

```json
{"type":"step","requestId":"smoke-race","operation":"race","mazeId":"2e99bcfc-124c-499f-b116-dfd969cf077d","algorithm":"BFS","sequence":0,"data":{"x":0,"y":0}}
{"type":"step","requestId":"smoke-race","operation":"race","mazeId":"2e99bcfc-124c-499f-b116-dfd969cf077d","algorithm":"ASTAR","sequence":0,"data":{"x":0,"y":0}}
```

## Limits of this verification

This proves local HTTP and WebSocket behavior, not public deployment or browser
animation. Recordings are generated synchronously by Phase 1 before being sent;
the stream does not expose live computation callbacks. Timings are per core run,
not playback or transport benchmarks. The tests ran on Java 21 with release 17
compilation, not a Java 17 runtime. Spring's test stack printed its standard
Mockito dynamic-agent warning; it did not cause test failures.

Phase 3 still needs the frontend, queued playback, reconnect indicator, public
Render/Vercel deployment, and real deployed-browser verification. The supplied
final documentation audit remains deferred until those requirements are met.
