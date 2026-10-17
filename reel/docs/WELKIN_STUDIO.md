# Welkin studio

A Minecraft that stays open, for fixing VOID Welkin. It loads Gloam, VOID LOD, VOID Expanse, Sodium and Iris, and
runs the Welkin pack straight from a worktree. A fix is checked as edit, reload, shoot: about 2 to 9 seconds a look,
where a game test took 6 to 10 minutes.

Everything goes through `reel/tools/wstudio.sh`. The examples below say `bash tools/wstudio.sh`; the script finds its
own folder, so from anywhere you can run it by its full path without `cd`:

```bash
WS=C:/Users/bitto/VOID/.claude/worktrees/welkin-studio/reel/tools/wstudio.sh
bash $WS start B C:/Users/bitto/VOID/.claude/worktrees/<yours> 90
```

## A fix, start to finish

Run every command in the foreground (give the Bash tool a 600000 ms timeout): each returns when its work is done.

1. Make your own worktree of `welkin` and edit there. Never edit the main welkin worktree.
2. `bash $WS start B <your worktree> 90`. It returns after 1.5 to 2.5 minutes with `studio B ready`.
3. Shoot the views your fix is about **before** you edit, with a name: `bash $WS B shoot plain 16:30 p1630_before`.
   Shoot it twice and keep the second if you have just moved there (see "Same frame twice" below). For a view with
   water (valley, rain0) let the water flow first (see "Water does not flow" under Known limits).
4. Edit `<your worktree>/welkin/shaders/...`, then `bash $WS B reload`.
   - Exit 0: the pack compiled. Exit 1: it printed `--- errors` (see "When a reload fails").
5. `bash $WS B shoot plain 16:30 p1630_after`, and compare the two frames (and the reference packs, with `wcompare.py`).
6. Repeat 4 and 5. One loop is about 5 seconds.
7. `bash $WS B quit` when you are done. Then commit in your worktree.

## Start

```bash
bash tools/wstudio.sh start                     # studio A, the main Welkin worktree, open 180 minutes
bash tools/wstudio.sh start B <worktree> 90     # studio B, your own worktree (its welkin/ holds shaders/), 90 minutes
```

- `start` waits for a free game slot, launches the game in the background and returns only when the world, the
  chunks and the far land around the first view are drawn. That takes about 1.5 to 2.5 minutes.
- The game keeps running after `start` returns. It holds the game slot until it quits.
- If both slots (`C:/Users/bitto/VOID/.tools/locks/game1|game2`) are taken, `start` prints `waiting for a game slot`
  with their owners every 20 s and goes on waiting.
- To run A and B together, start one, wait until it is ready, then start the other. Two `start`s at the same moment
  would run two Gradle builds of this worktree at once.
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
- `opt KEY=value ...` sets pack options and reloads (and checks for errors as `reload` does). `opt` with no
  arguments shows them; `opt --reset` goes back to the pack's defaults. The options live in the run folder
  (`build/run/studio<A|B>/shaderpacks/Welkin.txt`), never in your worktree.
- `pack <worktree>` points a running studio at another worktree and reloads. `pack` alone shows the current one.

### When a reload fails

- Iris reports a compile error only in the game's log, so the reply's `"error":""` stays empty. `reload` reads the
  log for you: it prints `--- errors` and the Iris lines and exits 1. For example:

  ```
  net.irisshaders.iris.gl.shader.ShaderCompileException: final: ... line 1353:11 missing ';' at 'outColor'
  ```

  - The first word (`final`) is the program: `shaders/world0/final.fsh`, which includes `program/final.glsl`.
  - The line number counts lines of the program after Iris has expanded every `#include`, so it is not a line of your
    file. Look for the quoted token near your edit.
- After a failed reload the game runs **without the pack** (plain vanilla look). The studio remembers this:
  - `shoot` refuses with `refused: the last reload failed` (set `WSTUDIO_FORCE=1` to shoot anyway);
  - `status` prints `BROKEN`.
- Fix the file and `reload` again. A reload that compiles clears the mark.

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
- **Same frame twice.** The first shot just after a long move can differ from the next shot of the same view by one
  8-bit step in a few thousand pixels (measured: 2128 pixels, at most 1 step). From the second shot at a spot on,
  frames repeat exactly, and a reverted edit gives back the very same frame. Take your before frame at the spot,
  not as the first shot after the move.
- **A frame's tone can differ a lot from the old bench frame of the same view at the same commit.** Most views land
  within 1 to 7 median steps of review round 2, but in the baseline of 2026-10-16 (pack a304d351):
  - plain 16:30 was 31 steps darker (93 vs 125): a frozen cloud's shadow lay over the foreground;
  - forest was 5 to 34 darker at every hour: the trees grew differently and a tree stands right in front of the camera;
  - countryside (-21) and mesa (-12): the far trees are LOD's, and differ from the bench's.

  So judge a fix by your before and after frames from the same running studio. Use the bench rounds and the
  reference packs for the target tone, not as a before frame.
- **Compare sheets:** `python -I <scratchpad>/studio_tools/wcompare.py <frame> --before review2` puts a frame beside the
  reference packs and an earlier round, with tone figures. `wsheet.py` makes sheets of several frames (pass the
  files, not the folder: `renders/welkin/A` also holds older frames).
  - The tools know the bench, foliage, vista and amber/griver views only. They skip rain0, far0 and storm0 (no
    reference pack shot them); compare those by eye with the review round's frames in
    `<scratchpad>/welkin_p2/review2/B/raw/n3/`.
  - amber and griver have no tone target: the GAP line says "no numeric target at this hour (night ...)" there.

## Quit and status

```bash
bash tools/wstudio.sh status                    # state, pack, front, slot, log (and BROKEN after a failed reload)
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
| `quit` | 6 to 11 s |
| `pack <worktree>` (relink and reload) | 4 s |
| a full bench view, six hours, first visit | 55 to 70 s (one 30 to 47 s shot, then 3 to 7 s each) |

The second session of 2026-10-16 (A and B running together) matched these: start 104 to 126 s, reload 1.9 to 3.6 s,
re-shoot 1.6 to 4.4 s, a loop 4.8 s, a first shot at a new place 30 to 58 s.

## Rules

- **Never compile the studio while one runs.** That means `gradlew` builds in this worktree's `reel/`; `start` runs
  Gradle itself. Quit every studio first. Starting B while A runs is fine, because nothing gets recompiled.
- **Quit when you are done.** A studio holds one of the machine's two game slots until it quits, or until its minutes
  run out (180 by default).
- **Use one studio at a time if you can.** Two games at once share the GPU and run slower.
- **Editing `wstudio.sh` while a studio runs is safe only for studios started after 2026-10-16.** The background
  holder is bash running this file; before the `esac; exit $?` line, bash read on from its old place in an edited file
  when the game ended, and ran whatever stood there.
- **`quit` leaves a Gradle daemon** (a `java.exe` running `gradle-daemon-main`). That is normal; it stops itself when
  idle. Never kill Java or run `gradlew --stop`: other sessions use them. Only a game of yours whose `quit` failed may
  be ended, a `java.exe` whose command line holds `devlaunchinjector` and `welkin-studio`.
- The junction `build/run/studio<A|B>/shaderpacks/Welkin` stays after `quit`, pointing at the last pack. It is only a
  link; the worktree's `git status` is not touched by it.
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
- **Water does not flow and rain does not fall while the world is held.** Flowing water and the rain level advance only
  on world ticks, and the studio freezes them from the moment it is ready. So in a fresh studio:
  - every waterfall and spring that was not yet flowing is missing. The valley at 2026-10-16 lost all its waterfalls,
    which moved its R/B from 1.30 to 1.65 at 16:30; rain0 lost the waterfalls too;
  - `weather rain` sets rain, but the rain streaks need about 100 ticks to fade in, so rain0 shows no rain.

  Before you shoot a view with water, let it flow once; it then stays put after `hold on`:

  ```bash
  bash $WS B shoot valley 12:00 warmup     # go there (the chunks load)
  bash $WS B hold off; sleep 40; bash $WS B hold on
  bash $WS B shoot valley 16:30            # waterfalls in place; it now repeats exactly again
  ```

  For rain, do the same with a rain view (`shoot rain0`, `hold off`, wait 10 s, shoot again while the world runs).
  The waterfall warm-up is checked; the rain one is untested.
- **The front is set once, at start.** A view the bench shot at another front (rain0, far0 and storm0 at 0.8)
  needs a studio started with `-Pgloam.test.front=0.8`.
- **Places need generating.** With one generation thread, the first shot at a new place takes 20 to 60 s.
- **The underground views** (amber, griver, ...) ignore the front; shoot them in any studio.
