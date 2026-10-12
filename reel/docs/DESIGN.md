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
4. **Holds.** Meshing can always be waited for (passes with no ticks change nothing). Waiting on the server needs
   ticks, so it is waited for only when the world is frozen (frozen shots, stills). A frame waits at most 400 passes.
   Every frame filmed loading is listed in the report with what was missing.

## The report

`renders/<shot>/render.json` (or `stills.json`): job, rig, wall time, prefetch chunks and seconds, prepare time,
seconds per frame, holds, catch-ups, writer backlog waits, `framesFilmedLoading`, notes, failure. A render with an
empty `framesFilmedLoading` and no failure is clean.

## Control

`ControlServer` (127.0.0.1 only, on with `-Dreel.control=PORT` or the studio's `-Preel.studio=MINUTES`), wrapped by
`tools/reel.sh`: status, shots, check, play, render, stills, snap (one frame from anywhere, for scouting), where,
cmd (a server command), stop, quit. In-game: `/reel play|render|stills|stop|key|shots`.

## Known limits (phase 1)

- Single player only: the director drives the integrated server.
- Without Sodium, readiness reads vanilla's sections; with Sodium it asks Sodium (`isTerrainRenderComplete`). VOID
  LOD's far terrain has no readiness hook yet.
- Iris and shader packs are untested with the frame-size override. The main target resizes, and packs should
  follow it.
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
