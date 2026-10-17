# VOID Reel

Cinematic shots for Minecraft 26.3 (Fabric). Camera moves are written as files, checked without the game, and
filmed frame by frame. That means any frame size, real motion blur, a world stepped exactly with the frames, and
no pop-in.

- Shot format: [docs/SHOTS.md](docs/SHOTS.md)
- How it works and what comes next: [docs/DESIGN.md](docs/DESIGN.md)
- The VOID Welkin studio (a studio kept open for shader pack fixes: edit, reload, shoot): [docs/WELKIN_STUDIO.md](docs/WELKIN_STUDIO.md)

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

# with VOID LOD, Expanse and a shader pack (packs in build/run/clientGameTest/shaderpacks, chosen in config/iris.properties)
bash tools/studio.sh -Preel.studio=120 -Preel.distance=24 -Piris "-Pmods=build/set/void-lod-0.1.0.jar;build/set/void-expanse-0.1.0.jar"

# scouting: ground heights, any command with its output
bash tools/reel.sh height "1440,-3536;1000,1650"
bash tools/reel.sh cmd "locate structure #minecraft:village"

# cut shots into a film
python tools/cut.py morning m1-range-dawn m2-village m3-gorge m4-summit --title "Morning in the Expanse"
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
