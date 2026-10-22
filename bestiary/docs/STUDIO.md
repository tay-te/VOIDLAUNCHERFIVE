# The Motion Studio

A live in-game preview for iterating clips in seconds (ANIMATION.md §6). It is a persistent dev client (the Bestiary
client game test with `-Dfray.studio=true`) with a control port on `127.0.0.1:25700` and a file watcher: save a
`.motion.json` (or a rig's `.bbmodel`) and the studio re-bakes everything **in memory** (about 100-150 ms), swaps the
clips and timelines on both sides, and replays what you were looking at, frozen at the same tick. Pictures land in
`build/studio/`.

## Start, talk to it, stop

```bash
bash tools/studio.sh start            # takes a game slot (C:/Users/bitto/VOID/.tools/locks/game1|game2), returns when ready
bash tools/studio.sh start -Piris     # with Sodium and Iris (shader packs: build/run/clientGameTest/shaderpacks)
bash tools/studio.sh cmd "subject knight; play bestiary:gallows_knight/chain_sweep; strip 0,10,18,20,30 cam=front"
bash tools/studio.sh repl < tools/review/stage1_fp.studio   # one command per line
bash tools/studio.sh stop             # quits; the slot is released when the game exits
```

Commands chain with `;`. Every command answers with what it did (file paths for pictures). `help` lists them.

| Command | Does |
|---|---|
| `subject mannequin\|zombie\|armoured_zombie\|knight\|player` | spawns the subject facing south at the stage centre (the mannequin is the player model: player clips from any angle; `player` is you, for first person) |
| `play <ns:clip> [speed]` | starts the clip on the server (timeline, hit volumes, broadcast) or, for `player`, predicts it locally like a real action |
| `freeze <tick>` / `live` | holds the action at a tick (fractions allowed) / lets it run |
| `cam <preset>` or `cam x y z tx ty tz` | `front back right left 3q 3q_left top low close wide ots fp`; clips that travel (dodges) are framed on the middle of their path |
| `shot [name]` | a screenshot (`build/studio/shots`) |
| `strip [t1,t2,...] [cam=..]` | labelled frozen frames in a grid with tick, phase, windows and tell (`build/studio/strips`) |
| `gif [cam=..] [from=..] [to=..] [step=..] [width=..] [name=..]` | one frozen frame per step, real-time GIF (`build/studio/gif`; the PNG frames stay in `shots/gif_<name>_<tick>.png`) |
| `onion <t1,t2,...>` / `onion off` | translucent ghosts of the subject at other ticks (vanilla's friendly-invisible look, so it works with shader packs) |
| `split <tick> [cam[,cam]]` | first person (you) beside third person (a mannequin twin), the same clip and tick (`build/studio/split`); `ots` is over the twin's right shoulder. The bake's `fp_match` check compares the two |
| `overlay on\|off` | hit volumes (red while ACTIVE), hurt volumes (yellow), and the clip, tick, phase and tell above the subject (Gizmos; the text is skipped under shader packs, which draw it as a haze) |
| `travel on\|off` | shows root motion: the subject is moved along the clip's travel (dodges); the local player is teleported |
| `walk <speed>` / `walk 0` | drives the subject's walk cycle (vanilla legs swing, the walk loop plays synced); `gif` then films a cycle |
| `hud on\|off` | hotbar and crosshair in first-person shots (off by default; hands always show) |
| `pack complementary\|welkin\|off` | switches Iris's shader pack (needs `-Piris`) |
| `reload` / `report` | re-bakes now (also automatic on every file change) / the last bake report for the current clip |
| `perf [n]` | n copies of the subject playing the clip: Fray's per-entity cost from its probes |
| `run <command>` / `wait <ticks>` / `status` / `quit` | a server command / waits / state / stops the studio |

## The loop

1. `subject mannequin; play bestiary:player/dodge_left; strip cam=front` and look at the strip.
2. Edit `src/main/motion/bestiary/player/dodge_left.motion.json`, save. The studio log says
   `reloaded 35 clips, 1 rigs in 110 ms` and the subject replays frozen where it was.
3. `strip cam=front` again, or `gif cam=front`. Repeat.

The bake's checks run on every reload and in the build: `ground` (the lowest point of the body: `"ground": "plant"`
re-keys the root so feet never sink or float, `"floor"` only lifts what sinks), `fp_framing` and `fp_match` (below).
`report` shows them for the current clip.

## First person

First person has its own rig and its own curves (`FirstPersonRig`, 2026-10-22), as action games do: a hand and its held
item anchored to the camera, and the forearm (the elbow cut's lower half, closed by a cuff) coming in from the bottom
right. Never the upper arm or the shoulder. The rest pose is vanilla's first-person held item (`applyItemArmTransform`
and the item model's `firstperson_righthand` display), so a clip starts and ends exactly on vanilla's picture; the
crossfade (the clip's blend in and out, or one string cut into the next) blends the rig's hands with slerp.

A clip's `fp` block carries `bones.item_r` (and `item_l`, the off hand, drawn with `"arms": "both"`): a rigid offset of
the hand and its item from vanilla's held sword, rotation in degrees about the camera's axes (ZYX, turned about the
grip, always interpolated on quaternions) and position in pixels (x right, y up, z back). The sword never turns in the
hand; the hand travels. `framing` (`fray:default` = vanilla's hands, for camera-only clips such as dodges), `hide_arms`
and the `camera` kick channels are as before; `offset`, `pitch` and `swing` are gone (the parser rejects them), and so are
the client's `fpSwingScale`, the per-axis blend toward a ready framing and the ±1.5-tick averaging.

Third-person sword clips say `"slerp": true`: every rotation channel is interpolated on quaternions (slerp for linear
keys, a cubic on the sphere for catmullrom keys; `Channel.sampleQuat`), and `item_r` has no wrist roll (y = 0).

The bake's checks for first person:

| Check | What |
|---|---|
| `fp_framing` | forearm and blade at most 15 % of the screen (the forearm box cut at the near plane, its outline clipped to the screen); the crosshair covered only on ACTIVE ± 1; also reports how many ticks the forearm's cuff is on screen |
| `fp_match` | first and third person do the same move (`FirstPersonMatch`): the sword in each view relative to its own eye (first person: the camera; third person: the animated head), in view angles (the azimuthal projection about the view axis, continuous behind the head). Per tick: the tip's direction of travel (where both move 10°+ a tick) and the blade's angle on screen, each within 25°; the wind-up and follow-through sides within 45°; the strike's direction across the screen within 25° and the same way; and the edge leads (the blade's travel within 35° of its plane) on the ACTIVE ticks in both views. Compared over the move itself, from first person's first authored pose to its last before the return to rest: leaving and regaining rest, each view starts from and returns to its own rest, and the two rests differ |

`-Dfray.fp.plot=<dir>` on `motionBake` writes a picture per clip with both views' blades over the clip. The hand reach
check (right hand at most 0.65 blocks from the chest outside the strike) still warns. While iterating,
`bash tools/studio.sh start -Dfray.bake.lenient=true` keeps failing checks from stopping the studio; they are reported in
`report`. `split <tick> ots,right` films first person beside the mannequin twin from over its right shoulder (`ots`)
and from its right side.

## Review sets

`tools/review/stage1.studio` films every stage 1A clip (replace `PACK` with a pack tag), `stage1_fp.studio` the
first-person versions; `python tools/review/compose.py build/studio/shots <out>` makes the side-by-side GIFs and the
contact sheets. Review GIFs don't go in the repository: write them to `C:/Users/bitto/VOID/.tools/share/bestiary/stage1/` (or a folder beside it) and
copy only the contact sheets and stills into `docs/`.

```bash
bash tools/studio.sh cmd "pack complementary"; sed 's/PACK/comp/g' tools/review/stage1.studio | bash tools/studio.sh repl
bash tools/studio.sh cmd "pack welkin";        sed 's/PACK/welk/g' tools/review/stage1.studio | bash tools/studio.sh repl
bash tools/studio.sh repl < tools/review/stage1_fp.studio
python tools/review/compose.py build/studio/shots C:/Users/bitto/VOID/.tools/share/bestiary/stage1/
```

## Notes

- The studio is a game run: it holds a game slot until `stop`. Don't leave it running at the end of a session.
- Code changes (Java) need a restart; clip and rig changes don't.
- The studio and the showcase share `build/run/clientGameTest`; run them one at a time.
