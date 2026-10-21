# Lane B3 (small places in the wild): screens, requests to others, deviations

From lane B3 of the living world, part 2 (LIVING_WORLD_2.md §7), 2026-10-21, branch `hearth-lwB3`.

## Screens (Figma first, HUB_UI.md)

No new Hearth screen. Everything a player does at a post goes through the existing conversation page (order band
150–179) and the journal:

| # | Screen | Until it is designed |
|---|---|---|
| F8 | Posts on Atlas (post tiles) | Posts are reported to Atlas as places of kind `hearth:post/<type>` (the map payload's `tier` field carries `post/<type>`; the wire format is unchanged). Atlas draws them with the house glyph and a subtitle from `hearth.tier.post/<type>` ("PASS HOSPICE"). **Asked of Atlas:** a glyph per post kind (a bell for the hospice and monastery, a gate for the toll, a tent for the nomads and hunters, an anchor for the harbour, a mug for the inn and the caravan stop, a sheep for the farmstead, a hood for the hermit). |
| — | The toll demand with coin icons (F1's variant) | The toll keeper's options are ordinary conversation options with the `Detail` kinds the Hub already reads: pay (`service`, cost in bits), talk your way through (`persuade`, odds), go round (`leave`). When F1's demand card is designed, the toll can use it as the bandits do. |

## Requests

- **A1 (roads and travellers)** — your open request: a journey's night stop should be the inn or post nearest its
  position. `sim/wild/Wild.posts(world)` gives the level's `PostWorld`; its `region(rx, rz)` lists the posts sited in a
  region (`PostSite`: type, centre, the road it stands by) and `PostBook.settlement(site.key())` the settlement once
  founded. `Traffic.place` / `TravelParty.rest` can look for an `INN`, `CARAVAN_STOP` or `HOSPICE` within a few hundred
  blocks of the stop. Caravans should pay the toll at a toll gate they pass (today a post's toll is only players'; the
  post's books take nothing from caravans yet). Road posts are not nodes of the network: they stand on its historic
  roads, so none is needed.
- **B1 (town events: domains)** — toll gates on a domain border (§3) are not sited: the siting only knows bridges. A
  border query (`domain(x, z)` from the event book) would let `PostSites.roadCandidates` put a gate where a road
  leaves a lord's land. Tolls go into the post's own treasury; B1's domains could forward them to the lord.
- **B2 (lives)** — a hermit's thread (§4): the hermit has `JobType.HERMIT` at the hermitage (`PostRules.JOBS`); a thread
  role keyed on `job:hermit` and the post type `hermitage` is ready for one.
- **Expanse** — the pass test is Expanse's own rule run on Hearth's probe (`PostSites.pass`, the same numbers as
  `SitedJigsawStructure.siteScore` "pass"); the hospice sits on the best saddle round each grid point and prefers one a
  road runs over. A site query from Expanse (the pass refuge's would-be spots) would make "every pass the refuge would
  have taken" exact; until then a region gets up to two hospices on its best saddles 400 apart. Post squares are
  planned ground through `Claims` (so `ExpanseClaims`): Expanse's sights, hedgerows and trees keep off them.
- **A4 / B4 (embodiment)** — posts embody as any settlement (48 a town); a post's programme holds at most 12 folk, so
  `wild.max_folk_per_post` is met by the programmes themselves and is not enforced in `EmbodimentSelector`.
- **Build module owner** — one line in `build/archetype/Archetypes.registerAll` registers the lane's archetypes
  (`build/archetype/wild/WildArchetypes`: `civic/hospice`, `wild/yurt`); `civic/hospice` lives in the civic package
  because it is drawn with the package-private `Precinct`.

## Touches outside the lane's own packages (all small, additive)

- `sim/world/SimWorld`: `allocateId()` and `adoptRecord(SimSettlement)` (adopt a record worked out off the server
  thread without a region); `trade(...)` skips posts (they send no caravans).
- `sim/settlement/SimSettlement`: `kind()`/`postType()` from `hearth:plan/wild`, `completePlot(id)` (a plot built whole
  in the books, the world told as it goes) and `demolishPlot(id)` (the nomads striking camp).
- `sim/SimServer`: posts are stepped every `wild.step_interval` (6 000 ticks) unless embodied.
- `sim/world/Hamlets`: posts found no daughters. `plan/HearthPlanner`: posts go to `plan/wild/PostPlanner`.
- `map/MapModule`, `map/MapPayload` (doc only): posts' place kind.

## Deviations from the design

- **Founding instead of world-generation writing.** A1 built roads as their own region plans rather than as atlas
  overlays, so posts have their own overlay (`sim/wild/PostWorld`, cached per region like the roads') and are written
  by the runtime applier as their chunks load, in regions generated before or after (the same path for both): when a
  chunk within 96 blocks of a post's square loads, the post's history (`PostHistory`: its programme laid out and built
  whole, two days of the books) is worked out on the `hearth-wild` worker and the world adopts it. Its square is planned
  ground from the start (Claims), so trees and Expanse's sights never stand where it will be. No crew walks out from the
  nearest town: the post stood before the world began.
- **Inns and caravan stops at the middle of long roads.** The network joins places within 2 400 blocks, so "a day's
  travel from the nearest inn or town" is the middle of a road 1 500 blocks long or more: a caravan stop on a king's
  road of 1 800 and more, an inn otherwise (the same spot: one of them).
- **The kennel and the hospice's dog** are not built (no kennel in the `Precinct` kit yet); the hospice's stable is its
  garden. The hunters' drying racks are not dressed (their huts and lodge are).
- **The nomads' move** happens in the books at once (the old ground's plots demolished, the new one's pitched whole);
  the herd does not walk with them (the camp's pen moves, its flock is A4's).
- **Toll evasion** is reported as `TRESPASS` worth the toll (no new `Offence` value); the toll is reckoned paid for the
  day after one crossing, so one crossing is one offence.

## Measured (2026-10-21)

- A post's abstract step (6 000 ticks, `PostGrowthTest.aPostsStepIsCheap`, an inn of four): p50 40 µs, avg 74 µs —
  over the design's 10 µs (the sim day's business once in four steps is most of it); 600 posts ≈ 0.007 ms a tick.
- Siting a region on synthetic land: under a millisecond to a few ms (the tests); on the dev seed see the tour's line.
