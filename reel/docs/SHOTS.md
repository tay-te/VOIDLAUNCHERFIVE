# Shot files

A shot is one JSON file in `reel/shots/` (files starting with `_` are ignored). It says where the camera goes,
what the world is doing meanwhile, and how frames are made. Shots are read afresh every time they are filmed, so
edit, check, film, repeat.

Errors name the field (`camera.keys[2].pos: expected 3 numbers in a list`), and unknown fields are errors, so a
typo fails instead of silently doing nothing.

## Top level

| field      | default        | meaning |
|------------|----------------|---------|
| `name`     | the file name  | output folder name under `renders/` |
| `fps`      | 60             | frames per second of shot time (1-240) |
| `duration` | required*      | seconds; *keyed paths take it from the last key's `t` |
| `size`     | `[1920, 1080]` | frame size in pixels, independent of the window |
| `world`    | unchanged      | see below |
| `camera`   | required       | see below |
| `shake`    | none           | handheld drift, see below |
| `render`   | see below      | motion blur and preroll |
| `notes`    | -              | free text, ignored |

## camera

Two rigs. `"rig"` may be omitted: a camera with `orbit` is an orbit, otherwise a path.

### path (dolly, crane, flyover, locked-off)

```json
"camera": {
  "keys": [ { "pos": [x, y, z], ... }, ... ],
  "timing": "even",
  "ease": "inOutSine",
  "look": [x, y, z],
  "face": "path",
  "fov": 70,
  "lens": 35
}
```

The camera follows a centripetal Catmull-Rom curve through every key's `pos` (the camera's eye, in world
coordinates). Through each key it passes exactly; between them it bends no more than it must and never loops.

**Timing.** `even` (the default unless every key has `t`): the camera covers equal distance in equal time, shaped by
`ease`, however unevenly the keys are spaced. `keyed`: each key has `t` (seconds, increasing) and the camera passes
it then; between keys the timing is a monotone cubic, so it never backs up or overshoots.

**Where it looks**, first match wins:
1. keys with `look: [x, y, z]`: the camera keeps that point centred; the point is carried from key to key, so it
   can track a moving subject;
2. `camera.look`: one fixed point for the whole shot;
3. `"face": "path"`: along the direction of travel (keys may still set `pitch`);
4. keys with `yaw` / `pitch` (degrees; yaw 0 faces south +z, 90 west, 180 north, -90 east; pitch positive looks down).
   Yaw always turns the short way between keys.

Any key may also set `roll` (degrees, a Dutch tilt), and `fov` (vertical degrees) or `lens` (focal length in mm on
a full-frame sensor: 24 wide, 35 natural, 50 normal, 85 portrait, 200 long). Values are carried along the path by
key with a monotone cubic, so they hold exactly at their key and never overshoot. A key with none of these just
shapes the path.

A path with one key is a locked-off camera. Several keys at one spot make a locked-off camera that pans or zooms
through their angles over the shot.

### orbit (reveals, turns around a landmark)

```json
"camera": {
  "orbit": { "center": [x, y, z], "radius": 60, "height": 20, "aim": 5, "from": 0, "sweep": 90 },
  "ease": "inOutSine",
  "fov": 50
}
```

The camera circles `center`, always aimed at `center + aim` (up). `from` is the direction the camera faces at the
start, as a yaw; `sweep` is how far it turns (positive is clockwise seen from above). `radius`, `height` (above the
centre) and `aim` may each be a number or `[start, end]`, eased along with the turn, so an orbit can close in, rise
or tilt as it goes.

### ease

`linear`, `inSine`, `outSine`, `inOutSine` (default), `inOutCubic`, `inOutQuint`, `smoother`. In-out eases start and
stop gently. Use `linear` for a move that is cut into mid-motion or loops.

## world

```json
"world": { "time": 23200, "timeRate": 12, "weather": "clear", "tick": "run" }
```

- `time`: day time at the first frame, in ticks (0 sunrise, 6000 noon, 12000 sunset, 18000 midnight; 23000-24000
  is the dawn glow). Omit to leave the clock alone.
- `timeRate`: how fast the day moves during the shot (1 normal, 0 held, 60 is a time-lapse). The sky is set
  exactly for every frame, sub-tick, so time-lapses are smooth.
- `weather`: `clear`, `rain` or `thunder`.
- `tick`: `run` (default) films the world moving, mobs, water, leaves and all, stepped exactly with the frames;
  `freeze` stops it so only the camera and the light move. Frozen shots can wait out loading at every frame, so
  they never film half-loaded land; running shots prefetch the whole path first.

## shake

```json
"shake": { "amount": 0.3, "speed": 0.25, "seed": 1 }
```

Seeded drift laid over the move: `amount` is the largest yaw wander in degrees (pitch gets 70%, roll 40%), `speed`
about how many wanders a second. 0.2-0.4 at 0.2-0.3 reads as a camera operator holding still; over 1.5 reads as
running.

## render

```json
"render": { "samples": 8, "shutter": 180, "preroll": 2 }
```

- `samples`: renders blended into each frame for motion blur (1 is none; 8 is smooth; up to 64).
- `shutter`: shutter angle in degrees. 180 exposes each frame for half its time, the film look; lower is crisper,
  360 smears.
- `preroll`: seconds the world runs before the first frame (running shots), so water, leaves and mobs are mid-motion
  when the shot opens.

## Checking a shot

`./gradlew pathPreview -Pshot=NAME` (or `tools/reel.sh check NAME` against a running studio) prints the numbers and
warnings and draws `build/reel/NAME-path.png`: the plan seen from above with view cones every half second, the
elevation, and speed and turn rate over time. Warnings:

- a pan over a third of a frame width a second judders at 60 fps without motion blur (slow it, or `samples: 8`);
- over one frame width a second is a whip pan;
- a lurch is a sudden change of speed (space keys evenly or use even timing).

## Keys from the game

Fly somewhere in-game and run `/reel key` to log a key for where you stand and look (`/reel key 200` adds the point
200 blocks ahead as a `look`). Keys are appended to `shots/_keys.jsonl`, ready to paste.
