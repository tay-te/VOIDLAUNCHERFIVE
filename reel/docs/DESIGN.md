# VOID Reel: design

Reel films cinematic shots in Minecraft 26.3: camera moves written as files, rendered offline frame by frame. It is
built to be directed by Claude as much as by a person: every shot is text that can be diffed and reasoned about,
every render leaves a report, and the whole loop (scout, write, check, preview stills, render, review) runs from the
command line.

## Shape

```
shots/*.json ──ShotParser──▶ Shot (pure: maths, no game)        PathPreview / ShotReport: check without the game
                               │
                               ▼
        RenderJob (movie: every frame | stills: a few moments)
                               │
     Director (client thread) ─┼─ TimerMixin: fixed step in place of the wall clock
                               ├─ CameraMixin: position, angles, roll, fov, frame aspect
                               ├─ GameRendererMixin: frame size, capture after the world, no bob
                               ├─ MinecraftMixin: time of day each frame, never pause
                               ├─ IntegratedServerMixin: server sends 3 chunks beyond the view
                               ├─ IrisTimerMixin: shader packs animate by shot time, not the wall clock
                               ├─ Stage: options, HUD, spectator body, world clock, weather, freeze
                               ├─ Prefetch: server loads every chunk the camera will see
                               ├─ Readiness: is the view fully loaded and meshed?
                               └─ FrameSink: GPU readback ▶ motion-blur blend ▶ PNG writers
                                       ▼
                         renders/<shot>/frames/*.png, render.json ──Encoder──▶ <shot>.mp4 (ffmpeg)
```

`dev.voidmc.reel.math` and `dev.voidmc.reel.shot` never touch Minecraft classes, so shots are unit-tested and
plotted without starting the game.

## The frame loop

Each pass of the game loop, `DeltaTracker.Timer.advanceGameTime` asks the director how many ticks to run and at
what partial tick to draw. For a movie, frame f's sample s is at shot time t; the world should be `t * 20` ticks
in, so the director runs the ticks still owed (at most one a pass, catching up unfilmed if further behind) and
draws at the fractional remainder. Rendering takes as long as it takes; the world moves exactly 1/fps between frames.

The studio runs inside Fabric's client game test harness, which ticks the integrated server in lockstep with the
client (one server tick per client tick). So the whole world, not only the client, is stepped by the director:
mobs, water, redstone, and particles all move as they would in real time. In an ordinary game session the server
runs on its own clock, so running-world renders there are approximate (frozen shots and stills are exact anywhere).

The camera is placed at the tail of `Camera.alignWithEntity`, after vanilla lines it up with the player, so
everything downstream (frustum, projection, fog, the view matrix) uses the director's camera. Roll goes into the
third argument of `Camera.setRotation`'s `rotationYXZ`, which vanilla leaves 0. The player is a spectator snapped
to the camera every client tick, so the server loads chunks where the camera is.

Capture happens in `GameRenderer.render` just before `FogRenderer.endFrame`: the world and post effects are drawn,
the GUI is not. The main colour target is copied to a mapped buffer (`CommandEncoder.copyTextureToBuffer`); the
read lands a frame or two later through the fenced task queue, so capture never stalls the GPU. Samples of one
frame are summed and averaged (motion blur), and finished frames go to three PNG writer threads; the director
pauses if more than 24 frames are in flight.

Frame size is independent of the window: `GameRenderer.extractWindow` reports the job's size, which resizes the
main target and everything sized from it, and the camera's aspect ratio reads the same size. The window shows the
top-left crop while filming.

## Never filming half-loaded land

1. **Prefetch.** Before the first frame, the server loads (generating if need be) every chunk within the render
   distance (plus the send-ahead) of every camera position of the job, in batches between ticks.
2. **Send ahead.** While filming, the integrated server's view distance is the client's plus three, so chunks
   arrive before they come into view.
3. **Readiness.** A view is ready when no chunk inside the counted circle is still expected, the compile queue is
   empty, and every visible section inside the circle is meshed. The circle is two chunks short of the render
   distance, because the server sends chunks in a circle and vanilla only meshes a section once all eight
   neighbouring chunks are present, so the edge never finishes. Unmeshed sections lacking a neighbour count as
   waiting on the server.
4. **Far terrain.** VOID LOD builds a far tile bare, then again once its background tree placer has stood the
   world's trees on it; a camera that moves on first sees trees pop in. So a movie's preparation includes a warm-up:
   the camera visits points along its path (about every 24 blocks or 2 s) and waits at each, the world frozen, until
   `LodApi.complete()` holds; then it returns to the first frame. During the shot a frame may wait up to 600 passes
   for far tiles (no ticks needed: LOD builds off-thread), and the report counts frames filmed while they built.
5. **Holds.** Meshing can always be waited for (passes with no ticks change nothing). Waiting on the server needs
   ticks, so it is waited for only when the world is frozen (frozen shots, stills). A frame waits at most 400 passes.
   Every frame filmed loading is listed in the report with what was missing.

## Stable distance (measured 2026-10-12)

`tools/stability.py SHOT` aligns each frame to the last with a homography fitted to the far band and measures what
is left: popping, swaps, shimmer. It also reports the share of that variation on the 3-frame game-tick rhythm.
`tools/experiments/stability.sh` runs the comparison below on a studio.

| test (slow dolly at the summit, far band) | median | jitter rms | tick-rhythm share |
|---|---|---|---|
| original | 0.13 | 0.078 | 36% |
| + body follows camera every frame | 0.13 | 0.074 | 34% |
| + VOID LOD on the recording clock | 0.14 | 0.089 | 20% |
| + 6 jittered samples, pack TAA off | 0.30 | 0.081 | 21% |
| + 2x supersampling | 0.15 | 0.047 | 18% |
| LOD's recording API + hysteresis, tiles compensated for supersampling | 0.24 | 0.104 | 16% |
| the same, not compensated (LOD draws finer far tiles), pack TAA off | 0.16 | 0.049 | 15% |
| the same, pack TAA on | 0.14 | 0.047 | 23% |

The summit shot re-rendered with everything on, through the same encode as the film: jitter rms 0.545 to 0.386, and
the single-frame tile pops and ghosts are gone. Findings:

- VOID LOD times fades, rebuild and swap gates by the wall clock; offline, tick frames take longer, so far land
  changed on the tick rhythm. `LodClockMixin` gives it the recording's clock while filming.
- A LOD swap between two samples of one frame showed as a ghost. `LodFreezeMixin` keeps the drawn tile set fixed
  across a frame's samples.
- Shimmer is sub-pixel far detail; supersampling is what cures it, and mostly because VOID LOD then draws finer
  far tiles: asking it for the tiles of the final size (`lodCompensate`) doubled the jitter, so it is off. Jitter
  alone (without the pack's TAA) does not help; with supersampling the pack's TAA makes no difference, and films
  are made with it off (`reel.sh pack TAA_DEFINE=0`) as the deterministic choice.
- The body sync made no measurable difference and stays as harmless correctness.
- VOID LOD's branch `reel-lod` adds the hooks as API (`LodApi.setRecordingClock`, `setTileHold`,
  `setDetailScale`, set by `LodHooks` for each job) and 8% hysteresis on detail levels (no flips while a camera
  wobbles). With that API present, the mixins into LOD's internals stand aside; they remain for older LOD builds.
  `setDetailScale` makes a supersampled render choose the tiles it would at its final size.
- Still on LOD's side: no fade on swaps under a shader pack, and far structure stand-ins (the summit crosses) drawn
  as solid brown shapes once LOD has learned where they are; `-Dvoid.lod.structures=false` hides them.

One-frame flashes (`tools/flashes.py`: a frame unlike both neighbours while they agree) came from two LOD causes,
found with the per-sample LOD trace (`reel.sh set lodTrace=true`, renders/<shot>/lod-trace.csv):

- Over its GPU memory budget, LOD evicts tiles not drawn for two frames every half second, and wanted them back
  the next frame: a coarse, treeless parent stood in for one frame, every 11 frames. Finer far tiles (supersampled
  renders) need more memory: the studio's `config/void_lod.json` uses `"preset": "custom"` and `"budget": 2048`.
  A named preset there overrides the values written under it (budget and detail included).
- A camera crossing a detail threshold asks for new tiles in the very render that films it. Each frame now first
  takes an unfilmed look from its position (`probe`), so the far-terrain wait sees the request.

- Whatever else makes LOD fall back to coarser tiles for a moment: after a frame's first sample is rendered (the
  render where LOD chose its tiles), Reel asks `LodApi.complete()` and, if the far view was incomplete, renders the
  same instant again once it is (`verify`; counted as `redos` in the report). No ticks are owed, so nothing moves.

The village crane went from 17 flashes to 0 (2 redos, no measurable slowdown).

The tile hold turned each of these one-render transients into a whole frame (without it they were a sixth of a
frame's blend); with both causes fixed it stays, as it keeps tile swaps from ghosting.

Live switches: `reel.sh set syncBody= lodClock= lodFreeze= lodCompensate= lodTrace= probe= verify= jitter= supersample=`, `reel.sh pack NAME=value`.

## Motion between shots (2026-10-24)

The first film's shots were good alone and disjointed together. Every shot used even timing with one ease over the
whole move, so each one started and ended at rest, and every cut joined two stopped cameras (the report now says so:
"enters at 0.0 b/s ... leaves at 0.0 b/s" for all four). Paced moves (`math/Pace`, `math/Ramps`, docs/SHOTS.md) fix
the cause: keys set speeds in blocks a second, ramps are seconds long and may be 0 so a shot is cut into or out of
mid-motion, and holds stop the camera at a key. The timing has a closed form (a stretch lasts G/k + (in + out)/2,
with G its seconds at the keyed speeds), so a fixed duration scales the speeds instead of changing the shape.
Orbits take the same ramps. Even and keyed timing are unchanged, so old shots film as before.

## The report

`renders/<shot>/render.json` (or `stills.json`): job, rig, wall time, prefetch chunks and seconds, prepare time,
seconds per frame, holds, catch-ups, writer backlog waits, `framesFilmedLoading`, notes, failure. A render with an
empty `framesFilmedLoading` and no failure is clean.

## Control

`ControlServer` (127.0.0.1 only, on with `-Dreel.control=PORT` or the studio's `-Preel.studio=MINUTES`), wrapped by
`tools/reel.sh`: status, shots, check, play, render, stills, snap (one frame from anywhere, for scouting), where,
height (ground heights at points: how shots are placed without guessing), cmd (a server command, answered with its
output, e.g. `locate structure #minecraft:village`), stop, quit. `tools/cut.py` joins encoded shots into a film
with dissolves, fades and a title. In-game: `/reel play|render|stills|stop|key|shots`.

## Known limits (phase 1)

- Single player only: the director drives the integrated server.
- Without Sodium, readiness reads vanilla's sections; with Sodium it asks Sodium (`isTerrainRenderComplete`) and
  checks that every chunk in the counted circle has reached the client.
- Shader packs work (filmed with Complementary Reimagined under Iris 1.11.6 and VOID LOD drawing through the pack's
  DH programs); their frameTimeCounter is the shot's clock, so water and clouds move with the frames.
- Client-only effects tied to the wall clock (enchant glint, world border, `LocalTime` items) are not stepped.
- Without ffmpeg, movies stay as PNG sequences.

## Roadmap

- **Phase 2, the look.** Depth of field (focus distance and aperture keyed like fov), colour grading with LUTs,
  letterbox and aspect presets, exposure, a 16-bit or EXR master, supersampling.
- **Phase 2, the director's eye.** Scouting from Expanse and Atlas data: find vistas by terrain, water and place
  name; compute a camera that frames a subject; check line of sight and ground clearance along a path before
  filming.
- **Phase 2, cutting.** Edit lists: shots in order, cuts and dissolves, titles in VOID's flat style, music with beat
  markers to time cuts to, all assembled with ffmpeg.
- **Phase 3, actors and replay.** Scripted Hearth townsfolk as actors; packet recording of real play and replay
  through the same director, so a recorded moment can be re-shot from any angle.
- **Readiness for VOID LOD and Welkin.** A far-terrain-complete hook in LodApi; Welkin's own time uniforms stepped
  by the director.
