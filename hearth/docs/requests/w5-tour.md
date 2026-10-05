# Requests from the live tour and performance pass (wave 5)

The tour (`gametest/LiveTown`, `-Dhearth.tour=live`) founds a temperate town near spawn on the Expanse Earth world
(seed 20261003), founds 54 more settlements round it (every other one with a patron topping up its stores and purse),
measures the sim with them all abstract, arrives at the largest temperate town, and photographs it. The numbers are in
`docs/SIM.md` §17. What it found that belongs to other modules is below; what it fixed itself is listed at the end.

## 1. Plan: towns stop at "pop = beds, everything built" with the planner answering nothing

**Seen**: Bradfield (temperate, gentle ground in broadleaf forest at 980, 1375) grows to 29 souls by day 120 and never
again: 63 plots all `BUILT`, 29 beds for 29 souls, 7 builders idle, stores full and treasury 400 000+ (patron), prosperity
0.81, carrying capacity unbounded. Asked once more, `planner.next` returns null (`founded ... planner.next: nothing` in the
log). Every patron town of the scale run stops the same way between 400 and 460 souls (`{BUILT=786}`, pop = beds): 27 of
them, all within 452 ± 15 souls. With pop = beds and builders idle the town should be planning housing.

**Asked**: find what the housing need gives up on (land? the deadline? a district full?) and either grow outward or
densify; log the reason a call returns null (a counter per reason in `/hearth sim stats` would do).

## 2. Plan: 69 carpentries in a city of 441

**Seen**: Thornley (temperate city, 441 souls, 786 plots) has `work/carpentry=69` while the program asks for
`1 + pop / 120` (4). Other workshops are sane (smithy 3, bakery 3, sawmill 9). `LiveTown` now logs the planner's reasons
for the most numerous workshop: `work/carpentry {work: the carpenters need a carpentry=70, work: a carpentry=1}`. So it is
the "a trade with nowhere to work" need (`Growth.workplace`), asked again and again for carpenters: the town keeps
assigning carpenters (the chains' timber recipe?) faster than a carpentry's one or two places absorb them. The jobs
step (sim) or the need (plan) should cap a trade's workers by its workplaces, or the need should stop at
`1 + pop / 120` like the program entry.

## 3. Plan: no settlement opens any working

**Seen**: none of 67 settlements (27 patron towns of 200–460 souls, 27 left alone, the rest adopted or hamlets) has an
`extract/*` plot or a `work/logging_camp`, although the program wants a logging camp from 8 souls, a quarry pit from
16 and a mine from 30 wherever `Extraction.available` finds a workable site, and the towns have 17–28 resource sites
each. So there is no mining town to photograph, no miner at a face, no deep town (a realm is reached through a mine),
and foresters fell only natural woods. The stand-in workings did appear in integration 3 (`fillCatalogue`), so it is
the real catalogue's case: perhaps `Extraction.place` finds no parcel that `fits` with the real archetypes' sizes, or the
patron's full stores mean the shortage needs never fire and the program entries alone never succeed.

**Asked**: a trace (`Growth.trace`) over one of these towns, and a test that a 60-soul town with an ore site in reach
plans a mine with the real catalogue.

## 4. Sim and economy: a town left alone does not grow on real blueprints

**Seen**: the 27 scale towns left to themselves average **7 souls** after 30–200 days (the patron towns: 205). Bradfield on
its own: 27 souls after 400 days, treasury 8. With real buildings costing thousands of goods across ten kinds
(timber, dressed stone, brick, glass, metal...), a small town's own production and its 96-a-day imports (bought with a
treasury the wages keep near empty, ECONOMY.md "wage_cap_share") cannot fund its stages; stalls follow. The stand-in
catalogue hid this (integration 3 noted "held back by an empty treasury" at 103 souls).

**Asked** (sim + economy): rebalance for real blueprints. Candidates: imports of construction goods paid partly by the
town's labour/exports rather than coin; a founding endowment scaled to what the first buildings cost; cheaper early
stages (camp and hamlet buildings in raw wood and stone only). A target to test against: a temperate town on good land
reaches a village (20) in ~60 days and a town (60) in ~200 on its own, as the stand-ins did.

## 5. Sim (worldgen guard): tree crowns over squares and streets

**Seen**: in broadleaf and old-growth woods (Bradfield) big crowns from trees rooted just outside plots spread over the
plaza and lanes (`hearth_live_market_square*`); the square reads as a clearing under a canopy. The guard keeps a
feature off only if its *origin* is on planned ground (`guard_margin` 3).

**Asked**: for vegetation features whose configured feature is a tree (or a selector holding trees), keep the origin a
crown's reach (6–8 blocks) from planned columns; or fell the overhang (non-persistent leaves above a plot's columns)
when the applier writes a plot's first stage, as the woodcutter already does for felling.

## 6. Plan: `planner.next` is the largest single cost left on the server thread

**Measured** (67 settlements, the largest 435–452 souls and 785–970 plots; live calls, one a tick at most): median 9–20
ms, p90 27–62 ms, max 34–62 ms per call; a town's step that makes one is its worst step (35–68 ms), against the clock's
2 ms a tick. A JFR profile of `HearthPlanner.next` on the server thread (1 984 samples, before this pass's two fixes):
`Growth.step` 83 %, `Parcels.find/best` 49 %, `Parcels.addPos/preScore` 30 %/25 %, `Ground.water` 19 % (the terrain probe
under it: Expanse's terrain model 10 %), `PlotSpec.params` copies 19 % (fixed: see below), `HearthPlanner.reconcile` 15 %,
`rebuild` 13 %, `Housing.plan` 21 %, `Additions.choose` 15 %, `StationLedger.neighbourhood` 5 % self. The 20 ms
`live_time_limit_ms` bounds the frontage search, not the rest.

**Asked**: keep a live call under ~5 ms in a city: memoise per town what does not change between calls (water distance
per column, the scored frontage positions of each road edge, the needs list between days), rebuild the occupancy grid
incrementally when a plot is removed, and lower the live deadline once that is done. Or plan off the server thread
against a snapshot of the settlement (plots, residents' homes and jobs, stockpile) and hand the result back, as
founding a daughter hamlet already does.

## 7. Building families (looks, from the photographs)

- **defense/wall** on sloping ground: the crest steps column by column into a saw-tooth (`hearth_live_night_town`,
  Thornley's north wall) instead of stepping by course or by bay; a skilled builder steps the coping in runs of 3–5.
- **civic/chapel** (Bradfield and Thornley): the spire is a thin brown needle on a narrow belfry; the gable end under it
  reads as a stepped stack of roof slabs rather than a gable. A Bohemian village church would have a squat square tower
  with an onion or a pyramidal cap (KCD II Rattay/Talmberg chapels).
- **moorland** (Brackenthwaite, `hearth_live_declining_wide`): from above, several of the hamlet's houses read as roofless
  gabled stone shells, though only #21 is weathered (decay 0.75, left empty in the decline); for every plot the record
  and the chunk's ledger agree (all stages written), so it is the drawing: the moorland cottage/longhouse roof should
  read as a roof (turf or heather thatch over the gables) so a lived-in house is told from a ruin at a glance.
- **noble/keep**, **landmark/cathedral**, **civic/town_hall**: read well (`hearth_live_keep`, `_cathedral`,
  `_town_hall`): red tile hip roofs, the cathedral's twin copper spires, the hall's clock tower.

## Fixed in this pass (no action needed; for the owners' information)

- **build** (`BlueprintServiceImpl`): `prepare(spec, terrain)` (new default method on `BlueprintService`) makes a blueprint
  on two background threads and answers whether it is ready; cache hits/misses and server-thread compiles are counted
  (`BlueprintServiceImpl.Stats`); `Compiler.Timings` times the light pass, shapes and campaigns.
- **sim**: the clock, the applier, `SimSettlement.pois` and excavation labour no longer make blueprints on the server
  thread when live (they wait a few ticks for `prepare`); the applier writes a stage at a time under its budget, places
  without `onPlace` (a carved pumpkin's golem check read an unloaded chunk and **crashed the server**; rails and water
  read and flowed into the next chunk) and marks block entities changed without the comparator update (which loaded or
  generated the neighbouring chunk in the tick); live planner calls are budgeted one a tick, roads included.
- **npc**: `PoiScanner` and `SettlementPlaces` scan only when every chunk the scan reads is loaded (a scan loaded chunks
  synchronously in the tick); builders' and diggers' placements skip `onPlace` and the comparator update; `Folk` and the
  embodiment manager are timed.
- **repair** (`Restorer`), **social** (`Dressings`): placements skip `onPlace` and the comparator update.
- **plan**: `cache_tiles` 4096 → 16384 (60+ towns thrashed the shared terrain tiles); hot paths read plot params without
  copying the whole tag (`PlotSpec.paramInt/hasParam/paramString/paramInts`; `params()` copies were 19 % of
  `planner.next`).
