# Requests from the planner lane (wave 6)

The planner's half of requests/w5-tour.md §1–3 and ART_REVIEW.md item 1 is in PLANNING.md §14.7. What it needs from
other lanes:

## For the sim lane (`sim/`)

1. **`/hearth sim stats` could print the planner's reasons.** `dev.voidmc.hearth.plan.PlanStats.lines()` gives a
   header (`planner.next: N live calls, M planned something, K nothing`) and up to sixteen `count  reason` lines. One
   line in `sim/command/Report.lines` (`out.addAll(PlanStats.lines())`, after the `planner.next` timings) would put them
   beside the timings; `/hearth plan stats` prints them meanwhile. `PlanStats.reset()` clears them (the tours'
   `resetCounters` may want it).
2. **Carpenters** (w5-tour §2): the cause was on the planner's side. The sim gives a trade only as many workers as
   standing buildings have job sites, and townhouses and tenements furnish a workshop with a CARPENTER job site, so a
   city with many townhouses has many carpenters, all placed (`Resident.work` set). The planner counted every
   carpenter against its own guess of the slots in carpentries and sawmills and asked for a carpentry each time. It
   now counts only workers whose `work` plot is missing or not standing, and caps workshops at the program's count.
   Nothing needed from the sim, but **`Resident.work` must stay set** for every worker with a place (the planner reads
   it); a worker with a job and `NO_PLOT` reads as "nowhere to work".
3. **Done here, please review** (`SimWorld.survey`/`simulate`, 13 lines): a fast-forward (`simulate`) holds the server
   thread, so the off-thread survey could not merge before it ended and the live tour's towns grew their whole spells
   with no sites — the reason no settlement opened a working (w5-tour §3) and Bradfield's capacity read unbounded. A
   fast-forward now surveys at once (and makes the founders' survey if it is still out), and a late off-thread survey
   no longer overwrites a wider one made meanwhile. Move or rework it as you like.
4. **Live calls run out of time** in towns of 200+ (median 20–41 ms against `live_time_limit_ms` 20). A need cut short
   now rests a call or two without counting as a failure, so the workings and workshops below housing are reached; but
   the clock's half-day wait after a null answer (`noGrowthUntil`) multiplies it. If a null answer's reason ends in
   "out of time" (`PlanStats.last(id)`), the clock could ask again on the next tick instead of half a day later.

## For the homes lane (`build/archetype/house`)

5. The planner now tells tall homes `storeys` 2 in a village, the outskirts and the inner town of a town; 3 in a town's
   core; up to 4 in a city's core; at most 3 in steppe, tropical, bayou, northern and tundra towns. `Townhouse`,
   `Tenement`, `MerchantHouse` and `TowerHouse` clamp the param to 3–5 / 4–6 / 3–4 / 3–5 today, so a 2 is read as 3 (and
   the tenement's 4 as its least). Please let the param go down to 2 (the tenement to 3) so a village or outskirts
   townhouse is two storeys under its roof. The plot's height cap leaves 12 blocks over the top storey for the roof.
