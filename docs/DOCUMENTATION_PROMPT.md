# Maze Visualizer — Documentation Prompt

Same purpose as the RunbookOS documentation prompt: so you can understand
this project later without re-reading every file yourself, and so the agent
shows its work instead of just asserting things are done. Hand this to
Claude Code after all 3 phases are built and deployed — not before, since
there's nothing real to document yet.

---

## Prompt

```
Do not write any new features or make deployment changes in this session.
Your only job is to read the existing Maze Visualizer codebase closely —
actually re-open and re-read the files now, not from memory of building
them earlier — and produce documentation the human can independently verify.

======================================================================
FILE 1 — WORKFLOW.md (how the system actually behaves, end to end)
======================================================================
Read, in order: the Phase 1 algorithm library, the Phase 2 Spring Boot
controllers/WebSocket handlers, and the Phase 3 frontend's WebSocket client
code. Then document, as numbered steps or a Mermaid sequence diagram, each
of these real flows:
- Maze generation: from the frontend's "generate" button click, through the
  REST/WebSocket call, into which Phase 1 algorithm method runs, how step
  events get recorded, how they're streamed back, and how the frontend
  paints them
- Pathfinding solve: the same trace for a single-algorithm solve
- Race mode: the same trace for multiple algorithms running together —
  specifically show how events from different algorithms are tagged and
  kept separate so the human can verify they don't get mixed up
- Disconnect/reconnect handling: what actually happens, in the real code,
  when a WebSocket connection drops mid-stream

Cite the actual file and method/function name at every step (e.g.,
"MazeController.generate() in src/.../MazeController.java calls..."), so
the human can open that exact file and check you didn't invent a step.

======================================================================
FILE 2 — ARCHITECTURE_DECISIONS.md (why it was built this way)
======================================================================
For each of the following, and anything else major you find, state the
reasoning and label it clearly as one of:
(a) VERIFIED — an actual comment, commit message, or doc states the reason;
    quote or closely paraphrase it and cite the source
(b) INFERRED — no stated reason exists; you are inferring a plausible one
    based on common practice. Say so explicitly.
(c) UNKNOWN — you cannot even confidently infer why; say so rather than
    guessing something that sounds authoritative

Cover at minimum:
- Why these specific maze-generation algorithms (DFS/Prim's/Kruskal's) and
  not others
- Why WebSocket for streaming instead of Server-Sent Events or long polling
- The pacing decision: is playback speed controlled server-side or
  client-side, and why that choice was made
- Why an in-memory map for maze state instead of a database, and what the
  actual eviction policy is (find the real code, don't assume "some
  reasonable policy" was implemented if it wasn't)
- Why A* uses Manhattan distance specifically as its heuristic, and whether
  the code actually verifies admissibility anywhere or just assumes it
- How race mode keeps multiple algorithms' events from being cross-
  contaminated on the wire — the actual tagging/framing mechanism used

Check git log (`git log --all --oneline`, then `git log -p` on specific
files where a one-line message isn't enough) for stated intent at the time
code was written, not just the current file state.

======================================================================
FILE 3 — Inconsistencies section (required, inside WORKFLOW.md)
======================================================================
Add a clearly separated "Inconsistencies Found" section listing:
- Any README/doc claim that the actual code does not implement, or
  implements differently
- Any algorithm whose test coverage doesn't actually test what Phase 1's
  build prompt required (e.g., if DFS's test wrongly asserts it finds the
  shortest path, or if the admissibility test for A* isn't actually present)
- Dead code, unused config, or TODOs suggesting an incomplete change
- Any case where the frontend assumes a message format the backend doesn't
  actually send (a common source of "it works until it doesn't" bugs in
  WebSocket code)

Do not smooth these over to make the summary read cleanly — surfacing them
is the actual point of this exercise.

======================================================================
OUTPUT RULES
======================================================================
- Both files go in the repo root, as their own commit, separate from any
  other code changes.
- These are living documents — if the code changes later, update these
  files in the same session as the change, not as an afterthought.
- Do not proceed to any other task as part of this session.
```