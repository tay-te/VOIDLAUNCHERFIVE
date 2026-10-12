# VOID Reel

Cinematic shots for Minecraft 26.3 (Fabric). Camera moves are written as files, checked without the game, and
filmed frame by frame. That means any frame size, real motion blur, a world stepped exactly with the frames, and
no pop-in.

- Shot format: [docs/SHOTS.md](docs/SHOTS.md)
- How it works and what comes next: [docs/DESIGN.md](docs/DESIGN.md)

## Quick start

```bash
# check a shot without the game: numbers, warnings and a plot in build/reel/<shot>-path.png
./gradlew pathPreview -Pshot=example

# film shots in the studio (a fresh world; holds one of the machine's game slots)
bash tools/studio.sh -Preel.shots=smoke,smoke-run
bash tools/studio.sh -Preel.shots=example -Preel.mode=stills -Pexpanse   # stills + contact sheet, Earth terrain

# or keep the studio open and direct it live
bash tools/studio.sh -Preel.studio=60 &
bash tools/reel.sh stills example count=8
bash tools/reel.sh snap x=0 y=140 z=0 yaw=180 pitch=25 fov=60 name=north
bash tools/reel.sh render example
bash tools/reel.sh quit

# tile any render into a contact sheet
./gradlew contactSheet -Pshot=example
```

Output goes to `renders/<shot>/`: `frames/` and `<shot>.mp4` for movies, `stills/` and `stills.png` for stills,
and `render.json` / `stills.json` reports. Video encoding needs ffmpeg (`-Dreel.ffmpeg=...`,
`.tools/ffmpeg/bin`, or the PATH).

In a normal game: `/reel play <shot>` previews in real time, `/reel render <shot>`, `/reel stills <shot> [n]`,
`/reel stop`, and `/reel key` logs your position as a key in `shots/_keys.jsonl`.

## Layout

- `src/main/java/dev/voidmc/reel/math`, `shot`: splines, timing, rigs, the shot format and its report (no game classes)
- `src/main/java/dev/voidmc/reel/client`: the director, stage, prefetch, readiness, capture, encoder, control port
- `src/main/java/dev/voidmc/reel/mixin`: the hooks into 26.3's camera, timer, renderer and integrated server
- `src/main/java/dev/voidmc/reel/tool`: path plots and contact sheets
- `src/gametest`: the studio
- `shots/`: shot files; `renders/` (git-ignored): output
