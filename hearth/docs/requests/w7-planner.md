# Requests from the planner and sim lane (wave 7)

What this lane changed is in `docs/PLANNING.md` §14.8 and `docs/SIM.md` §2, §16.3 and §17.2. It answers
requests/w7-tour.md §1 (calls past their limit), §3 (workings never reopen), §4 (camps that never grow) and §5 (patron
towns under "every need resting"), requests/w6-plan.md §1, §4 and §5, and requests/expanse.md's 2026-10-07 section.

## Small edits outside this lane (please keep them when you merge)

- (Everything under `sim/**`, `plan/resource/**` and `plan/growth/{Growth,Needs,Walls}` is this lane's own.)

- `api/SettlementPlanner.java`: `default boolean ranOutOfTime(Settlement)` (false). `HearthPlanner` answers it; the
  clock asks again at its next step after an answer that was nothing only for want of time.
- `api/TerrainProbe.java`: `SITE_NONE/HILLTOP/RIM` and `default int site(int x, int z)` (none). `sim/terrain/ExpanseProbe`
  reads Expanse's `Column.site`; `plan/society/Nobility` prefers the marked places for the castle hill.
- `plan/road/Router.java`: `Router.DEADLINE` (a per-thread `System.nanoTime()` limit), checked every 1 024 expansions;
  `plan/road/Roads.connect` sets it to the live call's deadline around its one route (approaches, cart tracks). A route
  given up so is not remembered anywhere: the need is out of time and is tried again. The other callers (the founder's
  arms, `Expand.continueArm`) never set it: `continueArm` closes an arm's end for good when its route fails, so it must
  not fail for want of time.
- `plan/growth/Expand.java`: `town()` checks the deadline before each lane walk and before `continueArm` (a walk not
  tried is not remembered as failed).
- `plan/model/PlanState.java`: three transient fields (`needsMemo`, `outOfTime`, `outOfTimeRuns`), never saved.
- `src/gametest/.../SettlementPreview.java`: `-Pterrain=earthat:x:z` founds exactly there (the tour's towns from their
  "founded" lines); `HEARTH_PREVIEW_DUMP=1` prints the founded settlement's occupancy round its heart, its roads and the
  ground along the eight headings. `gametest/plan/EarthProbe`: `maxY` 640 (Expanse's taller world) and `site()`.

## For the layout lane (`plan/layout/**`, `plan/terrain/SiteSurvey`, `plan/growth/Lots`)

1. **A camp on a small mesa top** (Safia, desert, founded at -3840, -1568 by the tour's grid): the survey puts the heart
   on a plateau about 25 blocks across with 40-block cliffs on every side but one, the founder's arms are 16 blocks each
   and the square, its well, the arms and a 13 × 13 town-hall lot fill the top. Nothing can grow there (no lane walks
   more than 4–9 blocks from the square in any of eight headings). `FirstLane` and the wall line's least radius
   (`Walls.MIN_RADIUS`, below) do not help it. Ask: when the heart's land within ~30 blocks is too small for a hamlet
   (say under 1 500 walkable columns within the slope tolerance), move the heart to the foot of the mesa or to the
   largest level ground within the founding radius, as `easierGround` does for a crag.
2. **The square's lots in a camp** (Hochdorf, alpine, 2560, -4128): the town-hall (17 × 17) and market-hall (15 × 18)
   lots are held against the square's east side from the camp's founding, and with the river on the west the camp's
   streets end at 10 souls ("a lane mouth at 49 would not walk"; every first lane but one, 13 blocks south, is blocked
   within a block). Ask: hold the square's lots from village tier (or release them while the settlement is a camp or a
   hamlet whose roads will not grow), so a camp's first houses can stand round its square.
3. **Lane mouths on a small square** start on the square's edge line, one block inside its rim: on a square of half 3
   that line runs over the well's kerb, so a lane begun there is blocked at its first column (`FirstLane` starts just
   outside the rim instead). `Expand.branches` off the square may have the same trouble.

## For the npc lane

4. **Diggers at faces**: a town now keeps a working open for its diggers (`Extraction.counted`: a quarry pit, mine,
   shaft, clay or sand pit dug out no longer counts while the town has folk in its trade or wants its goods; the next
   opens beside the old cut or at the next site). In the sim a trade with a face being cut gets one hand right after
   the builders, and `SettlementClock.workplaceFor` places a digger at the open excavation (PLANNED/BUILDING, `digs`)
   before a pit dug out; the pits dug out keep one crew a trade (`SimSettlement.recomputeDerived`). Whoever sends
   diggers to work should send them to the open excavation of their trade, wherever their `work` plot is.
   `sim.settlement.RealGrowthTest.aTownKeepsItsDiggersAtAFace` prints a day-by-day line (D cut, o open, . none).

## For the build lane (`build/archetype/extract/**`)

5. **A quarry pit with no cuts**: in the sim's harness (`RealGrowthTest`, `HEARTH_DIG_TERRAIN=cliff`, seed 3) the quarry
   pits staked out at the foot of the cliff (26 × 24, `depth` 21, `street_y` 55) draw with two stages and no cut steps
   (`PlotFacts.cuts` all 0: work [0, 87]), so each is "dug out" the day it opens and yields nothing. Planner-side the
   reopening is held to four dug out at a site, but such pits are scars with nothing taken out. Worth a look: a pit
   that cannot fit a bench should cut at least its floor, or say so (so the planner can pass the spot over).

## Open for the next wave (planner)

- By a river (`HEARTH_DIG_TERRAIN=river`) the next clay pit mostly finds "no plot fits at the site (14 places by the
  track)": the bank's best points lie within a few blocks of the track but no 15 × 15 plot fits there between the water
  and the old pit, and the planner's second point at each site does not help. Pits on narrow banks may want a smaller
  footprint or a plot reached across the end of the track, as adits are (`Parcels.atEnds`).
- The gaps between faces (D runs, then 20–40 days of none in the cliff town) are the planner's tries that find no room
  and the land's rests; the live tour should show whether towns keep a face open on most days now.

## For the tour lane

6. **Measure again live** (`LiveTown`): the out-of-time calls (the clock now asks again at its next step, up to four
   times, rather than half a day later; a need cut short twice rests about a day's calls and its third try has three
   times the call's time); the planner's past-the-deadline line in `PlanStats.lines()` (now in `/hearth sim stats`)
   shows which stage ran on. Offline (`PlanBenchTest`, `HEARTH_PLAN_BENCH_DEADLINE=10`, `HEARTH_PLAN_BENCH_PROFILE=past`
   samples only what runs past the deadline) the numbers are in PLANNING.md §14.8; the live server's load is what the
   offline bench cannot show.
7. The tour's scale towns are founded on a fixed 640-block grid, so some land where no settler would (Safia's mesa top).
   The atlas places real towns by the land; a tour town that never grows on such a site says more about the grid than
   about the planner.
