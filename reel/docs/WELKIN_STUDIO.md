# Welkin studio

A Minecraft that stays open, for fixing VOID Welkin. It loads Gloam, VOID LOD, VOID Expanse, Sodium and Iris, and
runs the Welkin pack straight from a worktree. A fix is checked as edit, reload, shoot: about 2 to 9 seconds a look,
where a game test took 6 to 10 minutes.

Everything goes through `reel/tools/wstudio.sh`, run from `C:/Users/bitto/VOID/.claude/worktrees/welkin-studio/reel`.

## Start

```bash
bash tools/wstudio.sh start                     # studio A, the main Welkin worktree, open 180 minutes
bash tools/wstudio.sh start B <worktree> 90     # studio B, your own worktree (its welkin/ holds shaders/), 90 minutes
```

- `start` waits for a free game slot, launches the game in the background and returns only when the world, the
  chunks and the far land around the first view are drawn. That takes about 1.5 to 2.5 minutes.
- The game keeps running after `start` returns. It holds the game slot until it quits.
- A uses control port 7351 and B uses 7352. Give `A` or `B` as the first word of any command, or set `WSTUDIO=B`.
  The default is A.
- Each start makes a fresh world. Its settings match the bench, so frames line up with the old game-test frames:
  - seed 8675309, Earth terrain, render distance 12, 1600x900, FOV 70, HUD hidden, clouds fancy, Iris shadow
    distance 32;
  - the day and the weather stopped, `gloam.test.front=0.38`, world generation on one thread
    (`gloam.test.bgThreads=1`, so the trees grow the same), VOID LOD on its default preset with autoTune off.
- Extra `-P` settings after the minutes pass through to the game: any `gloam.*`, `void.lod.*` or `welkin.*`
  (for example `-Pwelkin.distance=24` or `-Pgloam.test.front=0.8`).

## Iterate

```bash
# edit welkin/shaders/... in the studio's worktree, then:
bash tools/wstudio.sh reload                    # Iris re-reads the pack; prints compile errors and exits 1 on one
bash tools/wstudio.sh shoot plain 16:30         # prints the frame's path
```

- The pack in the run folder is a junction to `<worktree>/welkin`. Iris reads your edited files on each reload, so
  there is nothing to copy or zip.
- After a compile error the game runs without the pack. Fix the file and reload again.
- `opt KEY=value ...` sets pack options and reloads. `opt` with no arguments shows them; `opt --reset` goes back to
  the pack's defaults.
- `pack <worktree>` points a running studio at another worktree and reloads. `pack` alone shows the current one.

## Shoot

```bash
bash tools/wstudio.sh shoot <view> [hour] [name]
bash tools/wstudio.sh shoot at "tp @a X Y Z YAW PITCH" <absolute tick> [name]   # any eye; Y is the feet
bash tools/wstudio.sh views                     # the views and their hours
```

- **Views** come from the views catalogue: the scratchpad's `studio_tools/views.json`, or set `WSTUDIO_VIEWS`.
  - Bench views: plain, forest, village, valley, shore, torch.
  - The repeatable foliage spots: grove (under the canopy) and crowns (over it).
  - Vistas: steppe, countryside, mesa.
  - Also underground views and extras.
  - Without the catalogue, a built-in table in `tools/wviews.py` covers the bench, foliage and vista views.
- **Hours:**
  - 05:30, 08:00, 12:00, 16:30, 18:30, night (the full moon), or any HH:MM on bench day 6;
  - a vista's own hour (left out, it is used by default);
  - `tNNNNNN` for an absolute tick.
- **Frames:** `reel/renders/welkin/<A|B>/<name>.png`. The default name is `<view>_<hour>`, so the frame is overwritten
  on each shot. Give a name to keep before and after frames.
- **Each shot:**
  1. Sets the time and the weather, and runs the view's `tp` exactly as the bench did.
  2. Settles: 1 s, or 3 s after a move.
  3. Waits for the chunks to load, Gloam's seasonal recolour, and VOID LOD's far tiles.
  4. Takes one frame.

  It prints the time taken and the eye.
- **Field of view:** the bench's FOV 70 seen by a flying spectator is a 77-degree lens; the studio shoots with the same.
- **The world holds still between shots** (`tick freeze`). Clouds, wind and sway stay put, so a frame before an edit
  and one after it differ only by the edit. An unchanged pack gives a pixel-identical frame. `hold off` lets the world
  run, as on the bench.
- **Compare sheets:** `python -I <scratchpad>/studio_tools/wcompare.py <frame> --before review2` puts a frame beside the
  reference packs and an earlier round, with tone figures. `wsheet.py` makes sheets of several frames.

## Quit and status

```bash
bash tools/wstudio.sh status                    # state, pack, slot, log
bash tools/wstudio.sh log 60                    # the game's log: build/wstudio-<A|B>.log
bash tools/wstudio.sh quit                      # closes the game; frees the slot and deletes the world
```

## Measured (2026-10-16, RTX 4060)

| step | time |
|---|---|
| `start` until ready | 95 to 135 s |
| `reload` | 1.4 to 6 s (Iris recompiling) |
| `shoot` of the same view again | 1.5 to 3.5 s |
| edit, reload and shoot, one loop | 4.4 to 8.8 s |
| `shoot` at a new place that is generated fresh | 23 to 49 s |
| `quit` | 6 s |

## Rules

- **Never compile the studio while one runs.** That means `gradlew` builds in this worktree's `reel/`; `start` runs
  Gradle itself. Quit every studio first. Starting B while A runs is fine, because nothing gets recompiled.
- **Quit when you are done.** A studio holds one of the machine's two game slots until it quits, or until its minutes
  run out (180 by default).
- **Use one studio at a time if you can.** Two games at once share the GPU and run slower.
- **Never `rm -rf` a run folder (`build/run/studioA|B`).** Its `shaderpacks/Welkin` is a junction into a worktree, and
  `rm` may follow it into your shaders. `wstudio.sh` removes the junction itself with `cmd /c rmdir`.
- The mods are jars in `build/set/` (`SOURCE.txt` says which gloam commit). To refresh them:
  1. Build gloam, LOD and Expanse in the gloam worktree.
  2. Copy the three jars into `build/set/`.
  3. Restart the studio.

## Known limits

- **The far land depends on where the studio has been.** VOID LOD writes the chunks you visit into the far view.
  Before you visit a place, its far tiles are LOD's guess, and its tree placer can put trees where none grow. For
  example, the steppe vista's dune showed trees until the dune was visited.
  - The old bench frames carry the same history effect. Compare studio frames with studio frames from the same
    session; use the old bench frames only for tone.
  - For the fullest far view, shoot nearby views first.
- **Two studios do not match pixel for pixel.** Each world stops at its own game time, so the clouds, the sway and
  any LOD-drawn tree differ between A and B, and between runs. Within one running studio, frames repeat exactly.
- **Clouds and wind are not the bench's.** The bench let game time run; the studio freezes it.
  - The pack's frame time is fixed at 100 s during a shot. Animated water and the like are steady, but not as the
    bench caught them.
- **The front is set once, at start.** A view the bench shot at another front (rain0, far0 and storm0 at 0.8)
  needs a studio started with `-Pgloam.test.front=0.8`.
- **Places need generating.** With one generation thread, the first shot at a new place takes 20 to 50 s.
