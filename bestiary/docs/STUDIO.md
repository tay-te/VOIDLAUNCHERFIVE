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
| `cam <preset>` or `cam x y z tx ty tz` | `front back right left 3q 3q_left top low close wide fp`; clips that travel (dodges) are framed on the middle of their path |
| `shot [name]` | a screenshot (`build/studio/shots`) |
| `strip [t1,t2,...] [cam=..]` | labelled frozen frames in a grid with tick, phase, windows and tell (`build/studio/strips`) |
| `gif [cam=..] [from=..] [to=..] [step=..] [width=..] [name=..]` | one frozen frame per step, real-time GIF (`build/studio/gif`; the PNG frames stay in `shots/gif_<name>_<tick>.png`) |
| `onion <t1,t2,...>` / `onion off` | translucent ghosts of the subject at other ticks (vanilla's friendly-invisible look, so it works with shader packs) |
| `split <tick> [cam]` | first person (you) beside third person (a mannequin twin), the same clip and tick (`build/studio/split`) |
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
re-keys the root so feet never sink or float, `"floor"` only lifts what sinks) and `fp_framing` (how much of the screen
the first-person arms cover, limit 45 %). `report` shows them for the current clip.

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
