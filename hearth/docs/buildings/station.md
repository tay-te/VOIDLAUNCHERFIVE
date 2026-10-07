# Homes by station

Who a household is shows in its home (LIVING_WORLD.md §8.1): the poor in a mended hovel or a back-lane shack, the
peasant in a long croft with the beasts under the same roof, the merchant in a tall house on the market street with
his counting room and warehouse loft, the reeve by the square, the priest by the chapel, the scribe in a narrow house
of books, the hermit alone past the fields. Eight archetypes, each in every culture's manner, registered by
`build/archetype/StationHomeArchetypes.java`. The planner asks for them by station (`plan/society/Stations`) and by
zone (`plan/growth/Housing`); the registered infos below replace the placeholders in `DefaultCatalogue`.

| id | class | tier, flags | size (frontage × depth) | height | beds, jobs | upgrades to |
|---|---|---|---|---|---|---|
| `hearth:house/hovel` | `house/Hovel` | camp+, weight 0.8 | 7–9 × 6–8 | 8–9 | 2 | cottage |
| `hearth:house/shack` | `house/Shack` | camp+, ATTACHABLE, weight 0.8 | 5–7 × 5–8 | 8–11 | 2 | rowhouse |
| `hearth:house/croft` | `house/Croft` | camp+ | 9–12 × 12–16 | 9–12 | 3 | cottage |
| `hearth:house/merchant_house` | `house/MerchantHouse` | village+, ATTACHABLE, VERTICAL | 7–11 × 11–15 | 17–20 | 5, MERCHANT | — |
| `hearth:house/reeve_house` | `house/ReeveHouse` | village+, max 2 | 10–13 × 13–16 | 13–18 | 4, REEVE | — |
| `hearth:house/rectory` | `house/Rectory` | village+, max 2 | 9–12 × 14–17 | 12–17 | 3 | — |
| `hearth:house/scribe_house` | `house/ScribeHouse` | town+, ATTACHABLE | 6–8 × 9–12 | 15–18 | 3 | — |
| `hearth:house/hermit_hut` | `house/HermitHut` | camp+, OUTSKIRTS, max 1, weight 0.5 | 6–8 × 7–9 | 7–9 | 1 | — |

Every home reads `station` and `household` (shared, `kit/homestead/Household`): the trade's signature piece and its
dressing, the station's finish (a merchant's or official's carpets, candles and strongbox; a cleric's or scholar's
books). The style picks the sub-style by station (`StyleContext.implied` gives each id its station when the planner
does not): the Bohemian town manner for the merchant and the reeve, the village manner for the croft and the poor.

## What each one is

**Hovel.** One room under a big roof come down low, walls on a course of fieldstone with the daub mended in patches
(packed mud, a board over a hole, stones at the foot); a lean-to run on as a catslide over the firewood; a fire pit on
its hearthstone against the back wall with a bed either side; a doorstep with a stool, a pot of flowers and the water
barrel; ferns at the foot. Poor but cared for. Flat lands build an adobe cell with a shade of poles over the cooking
fire, round-house lands a cone of thatch, raised lands a hut on piles, the north a turf roof.

**Shack.** The town's poor in the back lanes: a narrow board-and-batten shack in a row, mended with odd planks, a
sleeping loft oversailing the lane on brackets, a patched lean-to roof, a rubble flue; crates and a barrel at the door,
washing out of the loft. One storey with both beds by the fire out of the core, and wherever it stands alone on a
plot too narrow for the loft to stay within 1.6 times its front (`kit/townhome/Proportions`, art review 22).

**Croft.** The peasant's long low house with the family and the beasts under one roof. In Bohemia its boarded gable
stands to the lane over a little pent, the living room's two windows in it; the door into the hall from the yard side,
the hearth and its chimney there; through a partition the byre of fieldstone with its wide double door, stalls with
mangers and the trough, the hayloft over it with a hay door in the gable. The yard runs beside the house from a gate in
the wattle fence: the kailyard of greens and roots, the dung heap by the byre door, the woodpile, a trough, the hens'
house at the back. The moors and the north lay it along the lane (the blackhouse, the long house); the dry lands build
it of mud under a flat roof with a walled pen; raised lands lift it on piles. Three beds (the old one sleeps in the byre
by the beasts' warmth where the living room is short).

**Merchant's house.** Tall on the market street: three or four storeys and a warehouse loft under a steep roof with its
gable to the street, the hoist beam and chain at the apex over the loading door. In the Bohemian market town (and the
hill towns) the ground storey opens on the street as an arcade (the `podloubí`): round-headed arches on stone piers,
the walk under the house paved and beamed, the shop's door and window of small panes a row or two back. Elsewhere a
glazed shop front beside a dressed door under an awning. The trade's sign hangs over the street. Each culture's way:
crow-stepped brick gables in the burgher towns, jetties and an oriel in timber towns, balconies on rendered fronts, a
mashrabiya in the dry lands, pent roofs and a latticed gallery in the karst. Inside: the counting room with the counter
across it (scales on it; COUNTER and the merchant's barrel behind it, MARKET_STALL before), the strongbox in the back
corner by the ledger desk; the hall upstairs with the hearth and the best of everything; the chambers; the loft
stacked with barrels, bales, sacks and hay (servants' beds up there when there is only one storey of chambers; a ladder
where the stair cannot reach it). Where the plot's height leaves only two storeys, the loft stands up on knee walls two
high so the household sleeps under the roof, and the apprentices bed down in the shop when the house still runs short;
an arcade keeps its tall ground storey (a storey goes first). Its range is 17–20: below that a household of five no
longer fits in every culture (art review 1 asked for 13–20; see `docs/requests/w6-homes.md`).

**Reeve's house.** By the square: a detached house of two storeys and a garret standing back behind a paved forecourt,
its broad front to the square (or, in Bohemia, the rendered and boarded gable of the `rychta`), the door in the middle
under a hood, often a balcony over it. The notice board stands in the forecourt where the square reads it, a bench by
the wall for those who wait. Inside, the office at the front (the reeve's desk facing the door with JOB_SITE REEVE, the
rolls on shelves, the strongbox of dues and records) behind a partition from the kitchen, the hearth and the stair;
the chambers above.

**Rectory.** By the chapel: the same kind of house behind a front garden of flowers with a wayside shrine at the gate;
behind it the priest's garden of herbs and roots, a fruit tree, a log hive by the back fence and a bench in the sun.
Inside, the study (the lectern with the breviary open where the light falls, the desk with three candles, books up the
walls, the chest of the plate and the tithe) and the kitchen behind; the priest's and the housekeeper's chambers above.

**Scribe's house.** A narrow house in a row by the market: three low storeys and a garret on six to eight blocks, its
gable to the street; a bay of small panes beside the door with the desk inside it where the light is best, a sign of
the trade over the door. The writing room: the desk with candles, the lectern with a charter open on it, shelves of
books, the chest of deeds; the kitchen above; the chamber; the apprentice's bed in the garret among the paper (the
whole household in the garret, on knee walls two high, where the plot leaves room for only two storeys). Its range is
15–18: lower, a six-wide house no longer sleeps three (art review 1 asked for 12–18).

**Hermit's hut.** Out past the fields: a tiny cell under a low mossy roof, one shuttered window, a bed, a fire and a
lectern with the book always open; before it a wayside shrine of the culture's kind where passers-by stop (ALTAR), a
fenced garden of herbs with a hive, a bench, a woodpile, a rain barrel, ferns grown up all round.

## Kit (`build/kit/station/`)

- `Mending`: walls and roofs patched by hand in clusters of one material (mud, boards, rubble), clear of corners and
  frames, inside the culture's own materials.
- `Fire`: the fire pit on a hearthstone inside (HEARTH at the keeper's side), the cooking fire in a yard.
- `Roofwork`: thick thatch laid over a roof, barge boards on the verges, brackets under the eaves.
- `Verdure`: grass, ferns, bushes and flowers grown up round a building's foot.
- `Shrines`: the wayside shrine by culture (a stone pillar with its lamp in Bohemia, a carved post, a stone lantern,
  a mud niche of candles, a cairn), ALTAR where folk stop.
- `Study`: the lectern with its book open (LECTERN), the writing desk with candles and a stool (TABLE, SEAT), shelves
  of books, stacks.
- `Coffers`: the strongbox (STRONGBOX) with what a merchant, an official or a priest keeps in it, the shop counter with
  its scales (COUNTER, MARKET_STALL), the merchant's own place, goods stacked in a store.

Shared helpers in `archetype/house/StationHomes` (roofs fitted under the plot, low walls, earth floors, hut roofs with
catslides, the fire and the beds of one room, the town door column that meets a steep street, the straight town stair
kept clear at its foot) and `SeatHouses` (the reeve's house and the rectory). The merchant's and the scribe's houses use
the town houses' pipeline (`TownHouses`).

## Checks

`src/test/java/dev/voidmc/hearth/build/station/StationHomesTest`: every home in every culture on flat, slope, hills,
river, coast and steep ground, at the low and high end of its height range, with and without the planner's station;
every dressing forced; the corners of the size box; rows of four with blind party walls for the attachable ones; what
each household needs (the fire of the poor and the croft, the merchant's job site, counter and strongbox, the reeve's
job site, notice board and strongbox, the lectern of the priest and the scribe, the hermit's shrine); bills within a
stage's load. `StationDebugTest` builds one spec from `HEARTH_STATION_DEBUG` and prints its problems, POIs, floating
blocks and (with `HEARTH_STATION_PLAN=0,4,8`) top-down plans of the listed levels (`HEARTH_STATION_REACH=1` adds the
walk map). `HEARTH_STATION_ONLY=merchant_house,rectory` narrows the test run.

## Previews

```
./gradlew -I docs/buildings/station_preview.gradle stationPreview -Parchetype=croft,merchant_house -Pstyle=temperate -Pseeds=3 -Pterrain="flat;cutsMax=1"
./gradlew -I docs/buildings/station_preview.gradle stationPreview -Parchetype=reeve_house -Pstyle=all -Pseeds=1 -Pterrain=slope -Pscale=10 -Pcuts=false
```

Contact sheets go to `build/previews/station/station_<id>_<terrain>.png`, single views to
`build/previews/station/<id>/`. Terrain params after semicolons go to the spec (`frontage`, `depth`, `height`, `station`,
any archetype param) and to the sheet (`cutsMax`, `cutStep`).
