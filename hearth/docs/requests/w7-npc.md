# Requests from the npc lane (wave 7: builders on a real building site)

What the npc lane changed is in `docs/FOLK.md` §6 (builders) and `docs/ARCHITECTURE.md` §5.3. One tiny change outside
the lane: `SimSettlement.advanceConstruction(plot, 0)` now only notes that embodied builders are at work on the plot
(`lastEmbodiedAdvance`), so the clock's abstract labour keeps off a plot while its crew puts scaffolding up or places
steps ahead of the counter (before, a crew whose counter stood still for ten seconds had abstract labour, and the
applier, finishing their house round them). The api's `Settlement.advanceConstruction` Javadoc says so.

## 1. Sim and plan: no excavation stays open, so no digger is ever at a face

**Seen** (w7 tour, requests/w7-tour.md §3): one excavation open (`BUILDING`) in 71 settlements; Bradfield's two quarry
pits, four shafts, clay pit and sand pit all `BUILT`; `BuilderTour`'s dig town (Oakham, 240 days) `{BUILT=558}`. Its
quarriers and miners fall back to their old work by the pit (`ExcavationWork` → `FieldWork`), because
`Extraction.workable` (npc) wants an excavation `BUILDING`. The npc side works when one is open: `ExcavationWork` picks
the open working whose kind fits, walks to its `WORK_FACE` by the cut, cuts and sets steps through the plot's counter
and carries loads to its store (the w6 tour photographed it at Herringport's pit; this wave's `BuilderTour` looks for a
working day by day instead of every ten days, see §3).

**Why** (read from the code, not changed here):

1. **A dug-out working still counts as a standing workplace.** `plan/growth/Needs` counts every plot of an archetype but
   `DEMOLISHING` (`count(ctx, spec, built)`), and a resident whose `work` plot is `BUILT` is "placed". When a pit's last
   bench is cut it becomes `BUILT` and stays a workplace: its quarriers are placed, its job slots count, so `job/quarrier`
   and `job/miner` never ask for another working. (While a pit is being cut it is *not* `BUILT`, so its workers count as
   unplaced: the incentive runs the wrong way round for excavations.)
2. **The quota counts dug-out workings.** `Growth.workplace` skips an archetype when `counts.count(id) >= wanted(ctx,
   info)` (`1 + pop / 40`, or the program's count), and the count includes `BUILT` (worked-out) pits. So even when the
   need fires, a town that has had its quota of pits plans no more. `plan/resource/Extraction.active` already treats a
   dug-out working as leaving its site free ("a dug-out working leaves its site free for the next"), but the two counts
   above never let the planner get that far.
3. **A working's life is fixed by its blueprint.** A quarry pit or mine is cut in campaigns (`Blueprint.campaign`), and
   once the last one is cut the plot is `BUILT` for good. Nothing reopens it or plans its next level.

**Asked**:

- In `Needs`, treat a dug-out excavation (`BUILT`, not woods, not the salt works: the same test as
  `Extraction.active`) as no workplace: its workers unplaced, its slots not counted; and in `Growth.workplace` count
  only unfinished workings against `wanted`. The next working then goes to the same site (`Extraction.site` allows it)
  or another.
- Better still, while the town draws on the working's goods (its stock under target, or the trade's workers idle),
  plan the working's next campaign on the same plot (a deeper bench, the next gallery, a wider pit): a rebuild of the
  same archetype with a param that starts the blueprint at its next campaign (`replaces` as upgrades do), so the pit
  grows down and out instead of a new hole beside it. That needs the build lane's extraction blueprints to take the
  param.
- A test: a 300-soul town with an ore site keeps at least one excavation `BUILDING` over 200 days (requests/w7-tour.md
  §3), and `BuilderTour`'s dig town finds one open within 60 days.
