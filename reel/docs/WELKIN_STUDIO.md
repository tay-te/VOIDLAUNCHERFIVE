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
   The shot settles the world at the view first (water flows, rain sets in; see "The settle"), so the frame is ready to
   compare. To compare several hours of one view, see "Before and after over several hours".
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
  2. Settles the world if the shot moved or changed the clock or the weather (see "The settle").
  3. Waits for Gloam's seasonal recolour, the chunks, and VOID LOD's far tiles.
  4. After a settle, films one warm-up frame and throws it away.
  5. Takes one frame.

  It prints the time taken, split into its parts, and the eye. The second line gives the requested tick and the clock
  the frame was taken at (`NOT THE TICK ASKED FOR` if they ever differ), the game time, the rain at the camera and
  Gloam's wet ground.
- **The settle.** The world is held (frozen) between shots. A shot that moves to another `tp` than the last shot's, or
  changes the clock or the weather, first runs the world for a budget of ticks, as fast as the game can (`tick sprint`,
  with the client running ten ticks a frame: the game test harness ticks the server in step with the client):
  - after a move: waits until the chunks round the eye are in, then runs **600 ticks** (30 s of game time). Water flows
    and settles at the new spot, and the waterfalls fall;
  - after a change of clock or weather only: **120 ticks**, enough for the rain level to ramp from 0 to 1 (vanilla
    raises it 0.01 a tick while the world runs, whatever the `advance_weather` rule says);
  - then it freezes the world again, winds the game time back to the time the studio holds at (so clouds, wind and
    Gloam's fronts stand where they stood), and sets the clock to the shot's tick again. The clock the frame is taken at
    is always the tick asked for;
  - the client then gets 20 ticks (40 after a move) to take it in, and Gloam's wet ground is brought to the rain: it
    soaks up to it in a minute of client ticks and dries after it over most of a day, so the client runs those ticks
    fast while the world stays frozen;
  - a shot that changes nothing runs no ticks.

  Change the budgets with `WSTUDIO_TICKS=N bash $WS shoot ...` (this shot), or at start with
  `-Pwelkin.settle.moved=600 -Pwelkin.settle.changed=120 -Pwelkin.settle.client=20`. `WSTUDIO_WARMUP=0|1` turns the
  warm-up frame off or on for a shot. With `hold off` there is no settle (the world runs anyway): a shot waits 1 s, or
  3 s after a move.
- **Field of view:** the bench's FOV 70 seen by a flying spectator is a 77-degree lens; the studio shoots with the same.
- **The world holds still between shots** (`tick freeze`). Clouds, wind and sway stay put, so a frame before an edit
  and one after it differ only by the edit. An unchanged pack gives a pixel-identical frame. `hold off` lets the world
  run, as on the bench; `hold on` holds it again at the game time it has reached.
- **Same frame twice.** Shooting a view, then shooting it again, gives the same frame pixel for pixel, also when the
  first shot was the one that moved there (checked on 2026-10-16 for valley 12:00 and 16:30, plain 16:30, forest,
  village, shore, grove, torch night and rain0, and across a reload). The first frame after a move or a change used to
  be one 8-bit step off in a few to a few thousand pixels (2128 after a long move); the warm-up frame takes that.
  Two exceptions, both from Gloam and both in a few hundred pixels at most:
  - **rain0:** about 1 shot in 3 is one step off in some 650 pixels (0.05%). Gloam's wet ground, once it has reached the
    rain, dries one step below it and climbs back on every client tick (`Atmosphere.tick` dries with `Math.max(0, ...)`
    rather than down to the rain), and the client ticks while the world is held, so a frame catches either value;
  - **far0 and storm0** (front 0.8): 6 to 65 pixels (up to 8 steps) at specks where Gloam's motes are, which keep
    spawning on client ticks while the world is held.
- **Before and after over several hours.** Every settle runs world ticks, and with them what the studio cannot wind
  back: the water's animated texture, particles, mobs. So a view shot at 12:00, then 16:30, then 12:00 again is not the
  same frame twice (the water moved on: some 98000 pixels). Back-to-back shots of one view and hour are. To sweep hours
  before and after an edit, settle the spot once, then shoot the other hours without ticks:

  ```bash
  bash $WS B shoot valley 12:00 v1200_before                    # moves there and settles
  WSTUDIO_TICKS=0 bash $WS B shoot valley 16:30 v1630_before     # clock only, nothing runs
  # edit, reload
  WSTUDIO_TICKS=0 bash $WS B shoot valley 12:00 v1200_after
  WSTUDIO_TICKS=0 bash $WS B shoot valley 16:30 v1630_after
  ```

  Do not use `WSTUDIO_TICKS=0` for a change of weather: the rain needs its 100 ticks.
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
| `shoot` of the same view again | 2.6 to 8 s (mostly under 5 s) |
| edit, reload and shoot, one loop | 4.4 to 8.8 s |
| `shoot` at a new place that is generated fresh | 22 to 58 s |
| `shoot` back at a place visited before | 14 to 25 s (4 to 5 s without the settle) |
| `shoot` with a new hour or weather at the same place | 6 to 13 s (about 3.5 s before the settle: a 1 s wait and the frame) |
| `quit` | 6 to 11 s |
| `pack <worktree>` (relink and reload) | 4 s |

What the settle adds (second session of 2026-10-16):

- after a move: the 600 ticks take 3.8 to 7.3 s (95 to 160 ticks a second); the 40 client ticks 2 s; the warm-up frame
  2.4 to 8 s where the place is loaded. The wait for the chunks (0.5 to 24 s) and much of a warm-up at a fresh place
  (up to 26 s) is loading the snap would have waited for anyway. At a place visited before it comes to about 10 s
  (forest: 14.2 s settled, 4.1 s without);
- after a clock or weather change: the 120 ticks take 1.1 to 1.3 s, the client ticks 1 s, the warm-up 2.4 to 8 s;
- rain: soaking the ground takes some 430 to 500 client ticks on top of the settle's own, about 1 to 3 s. Drying it after
  rain is long: the first clear view after rain0 took 9600 client ticks, about 60 s, once.

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
  any LOD-drawn tree differ between A and B, and between runs. Within one running studio, a view shot back to back
  repeats exactly (see "Same frame twice").
- **Clouds and wind are not the bench's.** The bench let game time run; the studio freezes it.
  - The pack's frame time is fixed at 100 s during a shot. Animated water and the like are steady, but not as the
    bench caught them.
- **The settle runs the world where the eye is.** Fluids flow only in chunks that are loaded and ticking (the
  simulation distance, 12 chunks), so a waterfall farther out than that flows only once a shot comes near it. 600
  ticks (30 s) is enough for the valley's falls; a taller fall may need more (`WSTUDIO_TICKS=1200`). Mobs walk and
  crops grow during a settle as they would in 30 s of play.
- **A studio that has rained stays wet until its ground dries.** After rain, the first shot without it dries Gloam's wet
  ground out (about a minute, once); every shot after that is dry. Shoot the dry views first, or in their own studio, if
  that minute matters.
- **The front is set once, at start.** A view the bench shot at another front (rain0, far0 and storm0 at 0.8)
  needs a studio started with `-Pgloam.test.front=0.8`.
- **Places need generating.** With one generation thread, the first shot at a new place takes 20 to 60 s.
- **The underground views** (amber, griver, ...) ignore the front; shoot them in any studio.
