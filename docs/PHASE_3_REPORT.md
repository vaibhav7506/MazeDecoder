# Phase 3 verification report

Verified on 2026-09-19.

## Public deployment

- Frontend: <https://mazedecoder.vercel.app>
- Backend: <https://mazedecoder-api.onrender.com>
- Health check: <https://mazedecoder-api.onrender.com/api/health>
- Source repository: <https://github.com/vaibhav7506/MazeDecoder>

The Vercel deployment is the static `frontend/` application. Its build embeds the
Render API origin in `config.js`; it connects with `wss://` to the backend's
`/ws/maze` endpoint. The Render service runs the Java backend from the root
Dockerfile and has `/api/health` configured as its health check.

## Local checks

```text
npm.cmd test
# 4 tests passed: replay wall carving, queued client-paced playback, stale-frame
# rejection and independent race sequences, plus sorted result rendering.

.\mvnw.cmd -B verify
# Maven reactor build succeeded: 29 core tests + 12 server tests = 41 passed.
```

## End-to-end browser verification

The public Vercel site was opened in a real browser and used against the public
Render backend. The complete initial generation and a two-algorithm race were
observed, including independent Canvas panels, exploration playback, final paths,
and the results table.

For the default 20×20 DFS maze using seed `4217`:

| Algorithm | Cells explored | Path length |
| --- | ---: | ---: |
| A* | 168 | 118 moves |
| Breadth-first | 194 | 118 moves |

The matching final path confirms both were applied to the same perfect maze.

Additional public-browser checks:

- 10×10 Prim maze, seed `7`, generated successfully.
- Pause/resume and speed controls worked while generation playback was active.
- A four-algorithm race completed: A*, DFS, BFS, and Dijkstra all produced the
  expected 18-move unique path; their exploration counts were presented in the
  sorted results table.
- A single-algorithm follow-up was attempted after this check; the browser
  operation exceeded the automation wait limit, so it is not claimed as a
  completed verification. The same single-solve stream is covered by the Phase 2
  WebSocket integration test.
- At a 390px viewport, the rendered document width was 375px and both canvases
  measured 310px, with no horizontal overflow.

## Deployed WebSocket smoke test

The JDK-only smoke client was run directly against:

```text
wss://mazedecoder-api.onrender.com/ws/maze
```

It generated a 2×2 DFS maze, solved it with BFS, ran a BFS/A* race, observed the
individual tagged frames, and ended with:

```text
SMOKE PASS: generation, solve, and tagged race completed over real WebSockets.
```

The full raw transcript is retained at
[evidence/phase-3-websocket-smoke.log](evidence/phase-3-websocket-smoke.log).

## Operational note

The Render backend uses the free plan. It can sleep after inactivity; the first
request after idle can take roughly a minute while it starts. The frontend visibly
shows a reconnect/wake state during that period and retries its connection.
