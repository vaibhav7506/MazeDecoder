# Architecture decisions

Status labels distinguish facts verified in source and tests from design intent
inferred from the implementation.

| Decision | Status | Evidence and consequence |
| --- | --- | --- |
| Keep algorithms in a dependency-free Java core module. | Verified | `core/pom.xml` has no runtime dependencies; `MazeAlgorithms` is used by both REST and WebSocket paths. This makes deterministic generation and solving reusable outside Spring Boot. |
| Represent maze replay as immutable value events. | Verified | `GenerationResult`, `WallRemoved`, and `SolveResult` expose replayable output; tests cover deterministic output. Clients can animate without mutating server state. |
| Use WebSockets for live animation. | Verified | `MazeSocketHandler` streams `started`, `step`, terminal, and error frames described in `docs/STREAMING_CONTRACT.md`. A single bidirectional connection carries commands and replay events. |
| Let the client pace animation. | Verified | The handler sends ordered events without timing delays, while `PlaybackQueue` and `tick()` control visual rate. This prevents a slow UI setting from delaying server computation. |
| Retain generated grids in a bounded server store. | Verified | `MazeStore` supplies `mazeId` lookup and eviction. Solve requests avoid resending a grid while finite retention limits memory. |
| Support multi-algorithm races using tagged, round-robin frames. | Verified | `MazeSocketHandler.search()` emits each algorithm's sequence independently and the frontend maps a frame to its panel by algorithm. The result is comparable playback on the same maze. |
| Use Canvas for the visual grid. | Verified | `frontend/app.js` draws walls, explored cells, paths, and markers in `draw()`. Canvas scales to dense grids without creating one DOM node per cell. |
| Deploy a static browser client separately from the Spring service. | Verified | `frontend/vercel.json` configures Vercel output; `Dockerfile` and `render.yaml` package the Spring service. `MAZE_API_URL` binds the browser build to its API. |
| Prefer deterministic seeds as a reproducibility feature. | Inferred | The UI exposes a seed and core tests verify identical event order for equal inputs. It supports debugging, sharing, and replaying a maze after store expiry. |

## Open questions

No source-required architectural decision is unresolved. Production observability,
authentication, and persistent maze storage are intentionally outside the current
visualizer scope; the public deployment is designed for anonymous demonstration
traffic rather than long-lived user data.
