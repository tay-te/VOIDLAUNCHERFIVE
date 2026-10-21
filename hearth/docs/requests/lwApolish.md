# Batch A polish (lane lwApolish): what it fixed, and what it asks of others

From the batch A polish pass (LIVING_WORLD_2.md §1, §2, §5, §6), 2026-10-21, branch `hearth-lwApolish`. It took the open
items of `lwA1.md`–`lwA4.md` in the batch A packages (`sim/roads`, `npc/travel`, `social/rhythm`, `npc/crowd`,
`npc/work`, and the files they had as shared touch points).

## Screens

None. Everything is in the world (wares on market stalls, a causeway at a ford, folk keeping to the ground) or in the
existing pages.

## Fixed

| Item | Was | Now |
|---|---|---|
| A1 water out by a road | a ford through a perched lake was laid at the lake's bed, its water left standing next to the dug approach: the lake ran down the road | `build/kit/infra/Roadbed`: a column in water is a causeway to the water's top, and a column beside water this plot does not lay (outside the corridor, or the next plot's) never walks below it. `RoadBankTest.aFordThroughAPerchedPondLetsNoWaterOut` overlays the blueprint on the land and finds no water block beside or above an opening (it failed before) |
| A1 party spawn tick | a party of six made in one tick (~6 ms) | `TravelParty`, `BanditParty`, `RoadCrew`: the first body when the party comes on the road, one more a tick (the books, the away flags and the caps count the rest as coming) |
| A2 tavern tables | seats filled table by table, a drinker whose seat was taken stood | `TavernNight.seatOrder` goes round the tables (two drinkers, two tables); a taken seat sends the drinker on to the next free one; the drinker's place in the night found by id |
| A2 night watch | each guard keeps the night one time in three by seed: a town of one or two guards often had none | `RhythmSchedule.appointedWatch`: where the rule gives the night to nobody, one guard of the town (fixed for the day) keeps it; `RhythmPolishTest` checks 300 towns of 1–3 guards at four night hours |
| A2 `ScheduleOverrides.overlay` | a walk of the day's 1 440 minutes, 26 µs | a merge of the windows' ends with the blocks' starts, 0.4 µs; `ScheduleOverlayTest` checks it against the old walk on 4 000 random days |
| A2 director | 0.17–0.27 ms a pass | the day's facts once a day (rest, wash, winter, keys), winter's ledger scan once, chimneys found once, the wash-place scan held to 40 µs a pass, the day's washers' homes found once; dressings set without re-planning paths (below) |
| A3 blade | 6 of 8 backed off | a scatter has up to four ways out (straight away, then fanning 40°, 80°, 120° to either side), each standable, 4+ blocks further and open along the line; no headway in 10 ticks: the next way. Guards are not counted in the tour (they stand and come over, §5.1) |
| A3 shutters | fixed 20:30–06:30 | the town's curfew and dawn bells: `SettlementCalendar.curfew/dawn` (additive defaults, 21:00/06:00; the rhythm's calendar answers from the culture's data and the bells) |
| A4 market stalls | the stall callback had landed, nothing used it | `StallWares` listens (`Dressings.listen`): each market-day stall shows its keeper's own shop stock (the stall keeper behaviour's assignment), else the town's stores of market goods, on the counter (no stand-in ware block when wares are laid); taken down with the stall |
| A4 counter shops without keepers | an adopted historic town's bakery, inn and stalls had nobody working there: shut, never restocked | `economy/shop/Shops.standIns`: a shop building nobody works at is kept by an adult of its trade who keeps no shop building (a baker, an innkeeper), a general shop by a merchant first; still shut when nobody is free (the existing "nobody at work: shut" test holds). `ShopStandInTest` |
| A1 cart on roofs | `CartEntity.groundY` scanned down from two above the cart | it starts at a step up and wants two blocks clear over the block it settles on; ground under the cart wins over anything hanging above. `CartGroundTest` |
| A4 clamps 16–25 ms | the first block set of a pass was dear | the cause: `ServerLevel.sendBlockUpdated` (any set with `UPDATE_CLIENTS`) asks every navigating mob whether its path passes near and recomputes those paths at once (a path recomputes once in 20 ticks, so the first set paid for all). Dressings (clamps, timber stacks, laundry, winter, stalls, camps, shutters, watch lights) now go through `npc/work/field/QuietBlocks.set`: the clients hear by the chunk's change list, the path-type cache forgets the block, and a walker who meets it finds his way round |

## Asked of others

- **npc (`npc/work/BuilderWork`, `ExcavationWork`, `ForesterWork`, `Staging`, `ScaffoldLedger`, `FarmerWork`)**: every block
  these set with `UPDATE_CLIENTS` re-plans every nearby walker's path at once (above). For work that changes the ways
  folk walk (a wall going up, a tree coming down) that may be wanted; where it is not (scaffolds, crops, hives),
  `QuietBlocks.set` would save the first set of each pass its milliseconds. Not changed here: outside this pass's files.
- **economy owner**: `Shops.standIns` is new behaviour in your file (asked by lane A4: "let `Shops.keeper` fall back").
  The economy tests pass; a stand-in keeps the shop but does not work at it (the counter's bark is only for a worker).
- **sim (`SettlementClock.assignJobs`)**: the deeper fix for the keeperless shops is a historic town's trades given their
  shop buildings as workplaces when it is adopted.
- **B lanes**: `SettlementCalendar.curfew/dawn` are there for events that keep the town's hours (a trial before curfew,
  the watch at a fire).
