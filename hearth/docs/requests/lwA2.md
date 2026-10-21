# Lane A2 (the daily rhythm): what it added outside its package, and what it asks of others

From lane A2 (docs/LIVING_WORLD_2.md §2, §11.3), 2026-10-20.

## Screens

None needed. The rhythm speaks through plain conversation options ("Buy the house a round", "When is mass?") and barks;
nothing for Figma.

## Additions outside `social/rhythm`

| Where | What | Why |
|---|---|---|
| `api/NpcService.FolkHandle` | `pose(String, int)`, `sit(BlockPos, Direction)`, `standUp()` — default methods, additive | the congregation sits in the pews and stands for the chant, washers kneel, drinkers sit at the tables, children cheer: behaviours had no way to set a body pose |
| `npc/service/FolkHandleImpl` | implements them (the routine's own sitting: teleport onto the seat, `FolkPose.SIT`; a walk stands the folk up first) | as above. **A3 (crowds) and the batch-B lanes can use `pose` for cower/cheer/gawk** instead of adding their own |
| `social/life/Dressings` | `setUpLaundry`, `dressPlots`, `plotsNear`, the `rhythm:` prefix (kept to its own hour, not the agenda's) and **the stall callback `Dressings.listen(StallListener)`** | laundry lines and winter; **A4's stall wares** (`up` with the stalls set up, `down` with the packed positions taken down) |
| `social/calendar/TownCalendar` | `restWeekday`, `isRestDay`, `isWashDay`, `nextRestDay` (pure) | the week |
| `social/folk/Music` | `pitch` public, `play(..., instrument)` | the chant and the culture's tavern instrument |
| `npc/routine/ScheduleOverrides` | (not edited) `RhythmSchedule` registers as a source on `SERVER_STARTING`, after every module's init, so B2's named characters win where they overlap the rest day | |

## Requests

1. **Lane 0 / npc — `ScheduleOverrides.overlay` cost.** It walks all 1 440 minutes and looks each up in the base
   schedule (`Schedule.at`, a scan of the blocks): ~36 µs a resident a day measured (`RhythmTest.abstractCost`), paid
   once a resident a day when the day turns — ~1.7 ms in one tick for a town's 48 embodied folk on a rest day. A merge
   of the window list with the block list would be ~1 µs. Not a correctness issue; the rhythm only adds windows on rest
   days, for tonight's drinkers and in winter.
2. **A3 (crowds) — shutters.** §2.1 has the shutters open at the dawn bell and close at the curfew bell; §5 owns
   shutters. The curfew minute is the culture's (`RhythmData.of(culture).curfew()`, 21:00 by default, 20:00–22:30 by
   culture). If a public read is wanted in the api, a default `SettlementCalendar.curfew(Settlement)` would do it
   (additive; lane 0's file).
3. **B1 (town events)** — the funeral toll belongs to B1 (§2.3 "the funeral toll (§3)"); `RhythmDirector` rings the
   curfew and the rest-day peal through `Bells.ring/play` with its own stroke queue, so B1 can do the same.
4. **A1 (roads)** — `HearthServices.calendar()` is live: `marketDay`, `restDay` (no market goers or pedlars on it,
   if A1 wishes), `washDay`, `feast` (a festival's id or `first_snow`).
5. **Tour harness** — `runClientGameTest` runs fabric's `acceptGameTestEula` task as configured in `build.gradle`
   (`configureTests { eula = true }`): that is the project's existing client-tour setup, not something this lane added.
6. **B1 / B2 — a town with no priest.** The service is led by a monk, else an elder, else a grown-up of the town
   (`Service.officiant`, `Rhythm.officiant(s)` for tours). If B2's notables ever name a lay reader, that is the hook.

## Batch A polish (2026-10-21)

The open items above were taken by the batch A polish pass: what was done, and what it asks of others, is in
`lwApolish.md`.
