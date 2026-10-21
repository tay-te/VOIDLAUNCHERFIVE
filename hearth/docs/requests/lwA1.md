# Lane A1 (roads and travellers): requests to other lanes, screens, deviations

From lane A1 of the living world, part 2 (LIVING_WORLD_2.md §1), 2026-10-20.

## Screens (Figma first, HUB_UI.md)

| # | Screen | Until it is designed |
|---|---|---|
| F1 | Conversation with a stranger: portrait without name or trade, the hooded variant, the bandits' demand with coin icons (pay / talk / threaten / fight) | Strangers use the existing conversation page through `Conversations.openStranger` (`npc/travel/StrangerTalk`): their description in the name line ("a pedlar from Ashbrook", "a hooded man"), their trade in the title, where they come from ("Bandit of the road"), and the options with the `Detail` packet's kinds, odds and costs (the Hub's odds chips and coin costs already read them). |
| F8 | Roads on Atlas (a roads layer) | Nothing drawn yet: see `atlas-roads.md`. |

No other new screen. Paying toward a road is a conversation option at the reeve or the steward (order 160–175).

## Requests

- **A4 or B4 (`npc/embody/EmbodimentSelector`, `EmbodimentManager`)**: skip residents who are away
  (`Settlement.isResidentAway`) when choosing who to embody at home. A resident on the road is shown by the road's party
  (`npc/travel/TravelParty`, a body of their own with their name and face); until the selector skips the away, a party
  leaves out anyone already embodied at home, so nobody is seen twice, but a resident can still be embodied at home
  while their party is folded.
- **A3 (barks, §0.2)**: the roads' barks are lang keys `hearth.roads.bark.<kind>.0`–`11` (kinds `pedlar`, `pilgrim`,
  `messenger`, `carter`, `patrol`, `caravan`, `traveller`, `crew`) in `lw_roads.json`, said by `Parties.bark` with its own
  cooldowns (20 s a player). A3's `npc/crowd/Barks` (now merged) takes a `FolkHandle` and English lines from data; road
  folk are controller folk without a handle, and the road lines are translated. Wanted from A3: a `Barks.say` overload
  for a plain `Folk` with a translatable line, sharing the per-player-per-town limiter; then `Parties.bark` calls it.
- **A2 (calendar)**: traffic reads `HearthServices.calendar()` for market days and feasts; while it is `NONE`, a fallback
  (each town's market on its own day of the week, a feast every 28 days) keeps market goers, pedlars and pilgrims going.
- **B3 (posts)**: travellers camp by the road at night; once posts exist, a journey's night stop should be the inn or post
  nearest its position. `Traffic.place` and `TravelParty.rest` are where to look; the network's nodes should include posts
  (`RoadWorld.edgesNear` reads atlas sites only today).
- **Quest module**: a caravan robbed by a band on the roads now has a place: `LivingQuests.setRobbedAt` gives the band's
  camp, used as the happening's position; a new template `hearth_quest/road/robbers_camp.json` raises the robbers there.
  A quest objective kind that targets an embodied band (`npc/travel/BanditParty`) instead of spawning vanilla raiders
  would make the camp quest and the band one thing.
- **Expanse**: road strips are planned ground through `Claims` (and so `ExpanseClaims`): hedgerows, sights and trees keep
  off them. A pass/saddle site query would let the router pull routes over ranges (§1.2); today it finds saddles by cost.
- **LOD**: road plots are ordinary `infra/road` and `infra/bridge` specs; VOID LOD's footprints do not draw them yet. Their
  regions are `RoadWorld.region(rx, rz)` (`ways`, `byChunk`), deterministic from the seed like the atlas's plans.

## Deviations from the design

- The network rule is a relative neighbourhood graph (empty lune) rather than three nearest plus a Gabriel test: it has
  no crossings and no needless triangles and keeps every place's nearest neighbour, and it can be decided from the sites
  near an edge, which keeps world generation local. Degree is capped at 4 (both ends must keep the road); a place with
  nobody within `max_length` joins its nearest place within half as far again.
- Road ends: a historic road ends at an age-scaled radius from its town's heart (`RoadWorld.endRadius`), never reading
  the town's history; a road plot that would cross a settlement's planned ground is trimmed (the history of the regions
  next to it is read only for columns within 560 blocks of a site). Once the town is live, a short live **spur** joins
  the road's end to the nearest open end of the town's own roads (`SettlementPlanner.exits`, now in `HearthPlanner`).
- Signposts stand on the third road plot from each end (out past the town's own streets), naming the far town and its
  distance in leagues (a league is a thousand blocks).
- A refused plot (a player's work across it) is recorded and a **detour** is laid round the obstacle (the columns of
  somebody's work in every loaded chunk the plot crosses, kept 6 blocks beyond the road's edge), from the road a little
  before to a little after, built whole at once (the refused plot was due). No detour of a detour (that plot is a gap),
  none at a road's very end. A detour and its road count as one road where they overlap.
- The road bed (`build/kit/infra/Roadbed.earthwork`) keeps a bank up to the water's top where a cut runs beside standing
  water (town roads too), so a perched pond is not let out down a road.
- Travellers who are residents are shown with their own name, face and trade by the party (controller folk); talking to
  them uses the stranger conversation with their name.
- The cart texture is 128 x 128 (three kinds share the layout scheme), not 64 x 64.

## Measured (2026-10-21)

- Routes: synthetic hills p50 12 ms, p90 19 ms; Expanse Earth (seed 20261003, 25 regions round spawn) p50 35 ms, p90
  103–122 ms — over the 60 ms p90 target, but historic routes run on world-generation workers and live ones on the
  `hearth-roads` thread, two a second at most; never on the server thread.
- In game (roads tour): traffic books 0.003 ms a tick avg (1.1 ms worst), parties 0.05 ms avg (5.8 ms worst, the tick a
  party of six spawns), road writing 1.7 ms worst a tick (budget 1 ms a slice, one chunk pass may run over), roads'
  scheduling 0.006 ms avg.
- `TrafficBenchTest` (journey cap full, 50 players on one road): 0.006 ms a tick avg, the players' lookups 0.18 ms p50.
- Three routes on the dev seed find no way (sea between the places): those places stay unjoined.

## Open

- **Water let out by a new road at a perched lake** (roads tour, hamlet "Roadsend" on a lake shore above the land): water
  runs down the hamlet's road and spreads below. The bank kept by a cut beside water outside the corridor
  (`Roadbed.waterBeside`, `RoadBankTest`) did not stop it, so the likely way out is a ford inside the corridor (wet
  columns filled only to the walking level next to dug columns), or the hamlet's own works. To look at next: the route's
  types at that end and whether fords on water above the walking level next to a cut need a lip or a causeway.
- A party spawning costs up to ~6 ms in its tick (six entities at once); spreading the members over a few ticks would
  keep the parties under 0.6 ms in every tick.

## Batch A polish (2026-10-21)

The open items above were taken by the batch A polish pass: what was done, and what it asks of others, is in
`lwApolish.md`.
