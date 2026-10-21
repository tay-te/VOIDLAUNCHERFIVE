# Pass P1 (crowds and the daily rhythm): performance and gameplay misses

From pass P1 of the living world, part 2 (LIVING_WORLD_2.md §2, §5, §9.1), 2026-10-21, branch `hearth-lwP1`. Packages
touched: `npc/crowd`, `social/rhythm`, their tests and tours (`CrowdsTour`, `RhythmTour`). No API change, no new screen.

## What changed

| Area | Miss | Change |
|---|---|---|
| Crowds cost | ~110–160 µs a player a pass in the tours (budget 20) | The player's title and menace are kept 15 s (STANDING_CHANGED forgets them at once); outlawry, a lookup, is read every pass. A *glance* before a look: hand item, armour, last blows, crouch, night and health unchanged, nobody new reacting last time, within 2 s of the last look: nothing is read (a player with nothing about them may walk on). The selector's "who is in the middle of what" is built for the folk looked at, not from every order on the books. Per-player maps pruned every 200 ticks instead of every pass. |
| Rhythm cost | 0.13–0.16 ms a pass | Chimney smoke only for chimneys a player is within 32 blocks of (the particle packet's own reach; the rest cost and showed nothing); the wash-place scan takes 15 µs a pass (was 60). |
| Blade | 9/9 backed off in one run, 1 of 3 in another | A scatter's ways out reach 7–9 blocks and must end 5+ blocks further from the blade. Each is path-checked as it is taken: the path's end must get far enough (a path that stops short at a fence still does if it is far enough), else the next way at once; a stall (no headway in two looks) moves on too; with the ways spent, vanilla `LandRandomPos.getPosAway`; five path searches in all. A folk no longer stops on "arrived" until it stands 4.5+ blocks further off. |
| Tavern | drinkers sat at seats near no table; one table used | Seats more than 2.5 blocks from every table (settles by the walls, a stool at the bar) come after every table's seats; when the tables' seats are taken a drinker stands at a table (a floor cell beside it, round the tables in turn) rather than on a far settle. |
| Market wares | not shown in game | Shown: the rhythm tour forces a market, the stalls go up and `StallWares` lays the keepers' wares (see below). |

## Measured

Client tours (`-Dhearth.tour=crowds,rhythm`), the same town (Sigskog, ~120–145 souls, 48 embodied). The machine was
shared with other agents' builds and games, so means carry stalls; medians are the steadier figure. "Warm" = after the
director's bench has run its loops (the JIT compiled as on a server with many players), then timed in real ticks.

| | Before | After |
|---|---|---|
| Crowds, a whole tour (cold JIT included), µs a player a pass | 113.6 mean | 143.9 / 502 mean (two loaded runs), median 42–53 |
| Crowds, 40 s warm stretch in the busiest street (20 s with a blade) | 36.8 mean | 33.2–34.8 mean, **median 4.7** (p90 81; 57 of 80 passes a glance) |
| Crowds, bench (hot loop): a whole pass a player | 2.1 | 1.6 |
| Rhythm director, a town's pass, warm stretch | 27.6 mean | **18.6 mean, median 10.6** |
| Rhythm, bench at supper (smoke on) | — | 1.9–10.5 (with chimneys near the player) |
| Blade: folk within 6 that were 4+ blocks off within 3 s | 4/4, 0/0 (and 1 of 3, 9 of 9 earlier) | 26/26 (5 spots); 21/30, **30/30 within 5 s** in a 22-folk crowd |
| Tavern night | 1 drinker, 1 table (no room to embody more); earlier tours 0 tables | 10 drinkers, 4 tables in use (5 at tables in one inn); 6 drinkers, 2 tables |
| Market day | not shown | 4 stalls up, 3–4 showing their wares, 42–60 ware displays; `hearth_rhythm_market` shot |

## Requests

| To | What | Why |
|---|---|---|
| **build (tavern / inn archetypes)** | Every `TABLE` wants `SEAT`s within 2.5 blocks (benches or stools at the trestle, chairs at the barrel table), and settles along the walls want a table before them or no `SEAT` mark. In the tours' town: `commerce/tavern` with 13 seats, 4 by a table; another with 12 seats, 4 by a table; a `commerce/inn` with 2 tables and 1 seat, at neither. | Drinkers now stand at a table when its seats are taken; seated at tables is the look §2.8 wants. |
| **standing (`StandingEngine.title`)** | `owns(s, player)` walks every plot of the town on each call (4 µs hot, far more cold in a city). A per-town owner index (or the title cached per player per town until a deed or a plot sale) would help every caller. | The crowd now reads it once in 15 s; others (dialogue, law) still pay it. |
| **npc (`FolkBrain`)** | An additive "poll soon" (e.g. `FolkHandle.urgent()`) that brings the next behaviour poll forward for one folk. | A crowd reaction waits up to 10 ticks for the director and up to 20 for the brain's poll; a blade is 1.5 s old before the first step at worst. |
