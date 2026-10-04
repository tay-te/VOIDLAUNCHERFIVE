# Homesteads and farms

The family of buildings a settlement lives off: the fields and the beasts' ground, the barn, the farmstead, and the
dwellings of the cultures that do not build cottages (the northern long hall, the round house, the stilt house). It also
owns the two smallest houses, `house/hut` and `house/cottage`.

Everything is registered in `build/archetype/HomesteadArchetypes.java`; names are in
`resources/assets/hearth/lang/en_us/build_homestead.json`.

## The archetypes

| id | file | category, jobs | frontage × depth | height | beds | tier | cultures | flags | weight |
|---|---|---|---|---|---|---|---|---|---|
| `hearth:farm/field` | `archetype/farm/Field.java` | FARM, FARMER | 9–24 × 9–24 | 6–10 | – | camp | all | OUTSKIRTS | 1.0 |
| `hearth:farm/orchard` | `archetype/farm/Orchard.java` | FARM, FARMER | 11–21 × 11–21 | 8–12 | – | hamlet | all | OUTSKIRTS | 0.6 |
| `hearth:farm/pasture` | `archetype/farm/Pasture.java` | FARM, HERDER | 13–25 × 13–25 | 6–10 | – | hamlet | all | OUTSKIRTS | 0.8 |
| `hearth:farm/paddy` | `archetype/farm/Paddy.java` | FARM, FARMER | 9–19 × 9–19 | 6–12 | – | camp | karst, tropical | OUTSKIRTS, HILLSIDE | 1.5 |
| `hearth:farm/terrace_field` | `archetype/farm/TerraceField.java` | FARM, FARMER | 9–21 × 7–13 | 7–14 | – | hamlet | all | HILLSIDE, OUTSKIRTS | 1.0 |
| `hearth:work/barn` | `archetype/work/Barn.java` | WORKPLACE, HERDER | 9–13 × 11–15 | 11–16 | – | hamlet | all | OUTSKIRTS | 1.0 |
| `hearth:house/farmhouse` | `archetype/house/Farmhouse.java` | HOUSING | 11–17 × 11–17 | 11–15 | 2 | hamlet | all | OUTSKIRTS | 1.0 |
| `hearth:house/longhouse` | `archetype/house/Longhouse.java` | HOUSING | 7–9 × 13–21 | 9–13 | 3 | camp | northern, tundra, moorland | – | 1.4 |
| `hearth:house/round_house` | `archetype/house/RoundHouse.java` | HOUSING | 9–13 × 9–13 | 10–15 | 2 | camp | steppe, tropical | – | 1.5 |
| `hearth:house/stilt_house` | `archetype/house/StiltHouse.java` | HOUSING | 9–12 × 10–13 | 10–14 | 2 | camp | bayou, tropical, maritime | ON_WATER | 1.5 |
| `hearth:house/cottage` | `archetype/house/Cottage.java` | HOUSING | 7–11 × 7–11 | 6–16 | 2 | camp | all | HILLSIDE | 1.0 |
| `hearth:house/hut` | `archetype/house/Hut.java` | HOUSING | 5–7 × 5–7 | 5–10 | 1 | camp | all | – | 1.0 |

`house/cottage` upgrades to `house/rowhouse`, `house/shophouse` and `house/townhouse`; `house/hut` to `house/cottage`.
Bed counts are what the blueprints really give at their usual sizes (the tests hold every home to at least one bed
everywhere and to most of the promised count on average).

## What each one builds

**`farm/field`.** Farmland in level beds: on a slope the field is stepped into beds along the contour (Terraces kit),
held by grass banks or, where the culture walls its fields, dry-stone. Every furrow is within four blocks of water:
channels down the wide beds, sumps across the narrow ones; a channel cell that would spill over a bed's edge is left
out, and the odd row too far from water lies fallow rather than dry. Strips of the culture's crops are sown together at
one stage each; one pumpkin or melon patch at most (melons where it is warm), sugar cane on channel banks in the heat.
A headland path from the gate with notched steps through the banks, a field shed with the compost bin, scarecrows, a
rick or a compost heap, the culture's boundary with a gate, palms or hedge trees at the corners.
POIs: FARMER job sites (compost bin, headland stands), STORAGE (shed chest), GATE.
Params: `shed` (bool), `boundary` (`drystone|hedge|rails|bund|none`), `terraced` (bool, from the planner).

**`farm/orchard`.** The culture's fruit trees in a quincunx of mixed ages, ground left as it lies (dips filled so it can
be walked): apples (oak with blossom) in the vale, cherry or wisteria blossom in the karst and wisteria country, leaning
olives round the Mediterranean, palms in the desert, cocoa in the jungle; berry rows between the trees where it is
cold. Crowns are lifted clear of the ground under them. A mown path up the middle, beehives on posts, a ladder against a
grown tree and baskets, a press shed with the compost bin.
POIs: FARMER job sites (compost bin, stands by the trees), STORAGE. Params: `shed`, `spacing` (4–7), `boundary`.

**`farm/pasture`.** Stock-proof boundary (a culture whose boundary is a bank or nothing gets rails), wooden gate even in
walled cultures, a trampled patch round a trough and the herder's feed cauldron, an open-fronted shelter with hay and a
hay rack, a pond in the lowest hollow of a big one, long flowery grass away from the gate, a shade tree, a second gate
on a big one. No animals: the sim or the folk bring them.
POIs: HERDER job sites (cauldron, hay rack, gate, out in the grass), STORAGE, and **pens** (below). Params: `shelter`,
`pond`, `boundary`.

**`farm/paddy`** (karst, tropical). Flooded terraces down the hill, each a level sheet of water over mud with rows of
seagrass rice (tall seagrass sunk a block so its head stays under the surface: it holds water in both halves and would
spill over the bunds if it stood out), held by stone faces under raised earth bunds, a stepped path at the side, a
thatched shelter with the compost bin, a scarecrow, a shrine lantern on the top terrace. On level ground, one or two
flooded fields inside bunds. POIs: FARMER job sites. Params: `shelter`, `terraced`.

**`farm/terrace_field`** (any culture on a hillside). Narrow level terraces behind dry-stone retaining walls with flat
copings, a stepped path through every wall, a tool hut on the lowest terrace. The crop follows the people: vines on
posts and olives (olive cultures), tea bushes in close rows (blossom cultures), hay meadow with beehives and drying racks
(cold cultures), watered crop beds elsewhere. POIs: FARMER job sites, STORAGE. Params: `crop`
(`vines|tea|meadow|beds`), `hut`.

**`work/barn`.** Gable to the yard with wide cart doors hung open, a loft door and hoist beam with a bale on its chain;
inside a threshing aisle between fenced stalls with mangers and water, a hayloft over the stalls reached by a stair along
the back wall (or a ladder), lanterns on the tie beams. Boards between posts on a plinth in timber country, logs in log
country, masonry elsewhere; gambrel roof in timber country (55%), the culture's roof otherwise, a cupola with a lightning
rod on a big one. Flat-roofed cultures get a byre with hay stacked on the roof under a ramada of poles and slats, beam
ends through the side walls and a ladder up the outside. The roof and loft are chosen to fit the plot's height. Yard in
front with trough, muck heap, rick and lamp.
POIs: HERDER job sites (feed cauldron, loft, trough), STORAGE, DOOR. Params: `roof` (RoofForm name), `loft` (1–3).

**`house/farmhouse`.** A farmstead whose layout is the culture's:
- *steading* (timber and brick country, plots 15+ deep): the house on the track, a barn range across the back with
  cart doors to the yard, a kitchen garden, a yard with well, dung heap and hen house;
- *laithe* (stone and log country, and shallower steadings): house and barn in one range under one roof along the back,
  the barn end with tall cart doors, a walled or fenced yard in front;
- *court* (flat-roofed cultures and the courtyard habit): ranges round a paved court behind a wall and gateway, the
  house at the back with its roof terrace, a stable and a store either side, an oven, a well and a tree;
- *tun* (log cultures): log house, byre and a granary on staddle stones round a yard;
- *compound* (round cultures): round house, round granary, a fence-ring kraal (or the herder's place by the granary on
  a narrow plot), cooking fire;
- *raised* (stilt cultures): the house on posts with a gallery, a granary on posts, a cooking fire in the yard, a pen.
The yard is levelled to the track, the doors and gate kept clear. POIs: BED, DOOR, HEARTH, STORAGE, TABLE, SEAT, GATE,
FARMER and HERDER job sites in the yard. Params: `layout` (`steading|laithe|court|tun|compound|raised`), `roof`,
`storeys` (1–2).

**`house/longhouse`** (northern, tundra, moorland). Gable to the street behind a gabled entry porch, long walls bowed
out amidships under a level ridge, shingle, turf (grass and moss over a wooden eave board) or thatch, crossing carved
barge-board horns over the gables; peeled logs notched at the corners in log country, rubble with hipped ends (a
blackhouse) in the moorland. Inside: aisle posts and tie beams, a kerbed fire pit down the middle under a louvre, box
beds between the posts, the high seat and table at the far end, chests and banners, stores by the door; woodpile and
snow banks outside. POIs: BED, HEARTH (both sides of the fire), SEAT, TABLE, STORAGE, DOOR. Params: `roof`
(`shingle|turf|thatch|hip`), `bow` (bool).

**`house/round_house`** (steppe, tropical). Truly round: always an odd ring (a whole-cell centre, the round circles:
seven, nine or eleven across as the plot allows), so door, fire and the cone's point line up. *Steppe* (a rondavel): a
mud-plastered wall on a dark plinth, five courses high on the bigger rings, painted between plinth and head band with
bold figures (triangles, a stepped zigzag, lozenges) in whichever of the accent and the plinth stands out from the wall,
on an even ground of the wall's main colour; a steep cone of thatch (where it can be laid in courses, or bales) or the
culture's roofing, overhanging a block all round, with a **smoke hole** at its point under a little cap over the central
fire; a stone bench round the foot of the wall, pots by the door. *Tropical*: a **veranda** two cells deep all round under
one bell-cast cone that sweeps shallow over the deck on slender posts and steep over the house, raised on posts over water
or wet ground (on a plinth elsewhere); a hammock, pots, lanterns under the eave. A ring too small for a veranda stands on
posts with a landing at its door; a small ring sleeps its household first and cooks on its veranda. Inside: the fire,
then the beds with their heads to the wall (placed first, so the house sleeps what it promises), chests, pots, a lantern.
POIs: BED, HEARTH, DOOR, STORAGE, VIEWPOINT (veranda), SEAT (hammock). Params: `roof` (`thatch|tile`), `stilts`,
`veranda` (bool), `station`, `household`.

**`house/stilt_house`** (bayou, tropical, maritime; ON_WATER). A little house over the water, its life on its galleries.
The floor at the boardwalk's level or a step or two up on piles; a **front porch** two deep under the roof (rocking chairs
and a side table, pots on the rail heads by the steps, a lantern under the beam, slender turned posts between log corner
posts); a **side gallery** two wide down one side on a wide plot (a hammock slung between two posts, nets on the rail by
the sea) or a walk under the eave on a narrow one; a **back porch** with a back door, traps and a barrel, from which a step
(a ladder where the drop is deeper) goes down to the **boat landing**: boards at the water with a mooring post and its
lantern, crab traps or lobster pots, and a **punt** (a hull of stairs and slabs) moored alongside. *Bayou*: board walls
(corner posts, sill and plate beams, clean boards between), tall shuttered windows, a steep tin roof with its gable to the
boardwalk, bell-cast over the galleries so the eaves sweep low, barge boards down the rakes, a stovepipe, Spanish moss
from the eaves. *Tropical*: palm and bamboo under a deep hipped bell-cast roof of bamboo or courses of thatch, lattice
windows, the smoke left to find its way out. *Maritime*: a fisher's house of boards or whitewash under a straight slate
gable, nets over the rail. On dry ground the same house stands on its posts with **lattice skirting** between them and a
yard behind (a woodpile) instead of the landing. Inside: kitchen and living by the stove, the bedroom behind a partition
on a deep plot (beds placed first), a boarded ceiling with a lantern. POIs: BED, DOOR, HEARTH (stove), STORAGE, TABLE,
SEAT, VIEWPOINT, DOCK (the landing over water). Params: `roof` (`gable|hip|half_hip`), `side_walk` (bool), `station`,
`household`.

**`house/cottage`** (refined). The shared cottage (`HouseBuilder.cottage`) with: log walls peeled (stripped logs, corner
posts left in bark) and a stone sill course in log cultures without a plinth, so northern cottages read warm rather than
dark; in log cultures on grass, often (45%) a **turf roof**: a low gable of sods with flowers in them, barge boards at the
rakes and an eave board, the house one storey under it; **crossed horns** over the gables of log houses and **drip caps**
over the windows of log and board houses (the window joinery, white where the culture paints its trim); in flat-roofed
cultures the **dry-land dressing** (`HouseBuilder.dryDress`): rounded-over parapets in the pueblos, corner pinnacles
elsewhere, a **wind tower** with latticed vents rising from a back corner of the roof (desert), a latticed screen boxed out
over the window above the door on a two-storey house, the door in a painted surround with a tile at its sill, jars, a
palm-frond **ramada** before the door, a rug and seats on the roof terrace. And a **split-level** variant on hillside
plots (rise or fall of 3+ to the back, not terraced): where the land falls, the street door opens into the main floor
and the lower storey has a back door onto a terrace at the lower ground; where it rises, the street door opens into the
lower storey cut into the bank and the upper floor has a side door out at grade. A cottage whose loft is too low for a
bed sleeps downstairs (beds placed on purpose, `Beds`). Params: `storeys`, `garden`, `outshot`, `split` (bool, default
true), `terraced`, `turf` (bool), `station`, `household`.

**`house/hut`** (refined). Round cultures: a round hut on an odd ring of the Round kit (painted in the steppe, a smoke hole
over its fire, the bed placed first). Flat-roofed cultures: an adobe cell with its parapet rounded over, a palm-frond shade
before the door where the plot allows, a smoker oven against the side (the household's HEARTH before its mouth), jars and a
tile at the sill. Elsewhere a small gabled hut with peeled logs and a stone sill in log country. One bed, and a chest or a
table where they fit.

## Pens: where beasts are kept

Every enclosure marks where its beasts stand with a `WORK_FACE` POI for the `HERDER` (`Spots.pen`), facing its gate: the
middle of a pasture's grass (and each quarter of a big one), each barn stall, a farmhouse kraal (compound) and pen
(raised). The sim stocks and returns animals there; the herder works from the `JOB_SITE`s at the gate, the trough and the
hay rack. The ground round a pen's mark is kept open (grass, bedding), so the herd has room.

## Households (stations)

Homes read `station` (a `Station` name) and `household` (`{job: <JobType>}`) and are dressed for who lives there
(`kit/homestead/Household`, docs/LIVING_WORLD.md §8.1): a smith's anvil in the corner by the fire and tongs (a chain)
hung beside the hearth; a fisher's creel, barrel and a net high in a corner; a farmer's hay and harvest; a scholar's,
priest's or healer's bookshelves (a brewing stand for the healer); a merchant's carpets and a strongbox (`STRONGBOX` POI)
in the bedroom; a tailor's loom; a mason's stonecutter. The station's finish on the materials (rougher for the poor,
cleaner and finer for merchants and gentry) comes from the style (`StationFinish`), so the same cottage reads differently
for different households. Every piece is claimed through the room, so doors, beds and stores stay reachable. Wired into
every home of the family and of the town homes (stilt, round, cottage, farmhouse, hut, row, town, shop, tower, cliff and
courtyard houses).

## Signature looks by culture

- **Moorland**: stone laithe steadings and blackhouse longhouses inside dry-stone walls, fields of dry-stone banks.
- **Tundra / northern**: longhouses with turf or shingle roofs and crossing horns round a fire; tun farmsteads of log
  buildings and a granary on staddles; peeled-log cottages under flowered turf or shingle, with horns and drip caps.
- **Steppe**: painted rondavels under steep cones with smoke holes; compounds (round house, round granary, kraal).
- **Bayou / tropical / maritime**: stilt houses over the water with porches, side galleries, hammocks, boat landings and
  punts; raised farmsteads; tropical round houses under one sweeping cone over a veranda.
- **Karst**: paddies stepping down the hill with bunds, shrine lanterns and thatched shelters; tea terraces; court
  farmsteads.
- **Mediterranean / alpine**: dry-stone terraces of vines and olives, or hay meadows with beehives; alpine barns with a
  boarded loft over a stone byre.
- **Desert / mesa**: houses with wind towers, rounded or crested parapets, latticed screens, palm-frond shades and roof
  terraces; court farmsteads behind walls with an arcade before the house and a painted gate; flat-roofed byres with a
  palm shade before their doors and rooftop hay; adobe huts.

## The kit (`build/kit/homestead/`)

- `Terraces` – bands along the fall line anchored at the street, cut and fill, retaining walls (bank, dry-stone, bund),
  notched steps, water-aware ground.
- `Crops` – watered crop beds (channels, sumps, spill-safe, fallow where dry), crops, cane, scarecrows, ricks, compost.
- `Bounds` – drystone, hedge, rails and bund boundaries with gates and piers; hedgerow trees.
- `Sheds` – lean-to and small sheds, pads, granaries on staddles, hen coops (`coopCells` for placement), stores.
- `Trees` – apple, blossom, olive, palm, cocoa and local fruit trees, berries, ladders, shade trees.
- `Yard` – trough, well, chopping block, dung heap, bench.
- `Spots` – standable cells, POI placement (never on the plot ring), `claimed` cells.
- `Land` – raise-only mending, surface paths, yard levelling without water dykes.
- `Barns` – barn halls (boards, logs, masonry), loft, cart doors, stalls and loft stair, side windows, cupola.
- `Round` – rings, ring walls with posts or **paintings** (`Pattern`), cone roofs with a **smoke hole**, skirts, ring
  rooms, central fire, pots.
- `Turf` – turf roofs with an eave board, moss and tufts or snow.
- `Logs` – peeled wall logs, stone sill course.
- `Verandas` – decks on piles, posts, rails, rocking chairs, side tables, hammocks, lattice skirts, lanterns, rail pots.
- `Waterside` – punts moored on the water, mooring posts with lanterns, crab traps and lobster pots, nets on the rail.
- `Beds` – beds placed first and on purpose (heads to walls, a way in kept beside each, claimed through the room).
- `Household` – the household's station and trade read from the spec, and the things that say so.
- `RoofDress` – barge boards (a roof row or column restated in the trim).
- `Carving` – crossed horns over gables, drip caps over openings.
- `Adobe` – parapets (rounded or crested), wind towers, palm-frond ramadas, tiled thresholds, jars, roof-terrace life.

## Previews

```
cd hearth
JAVA_HOME=.../jdk-25 ./gradlew blueprintPreview -Parchetype=hearth:farm/field -Pstyle=all -Pseeds=2 -Pterrain=slope
./gradlew blueprintPreview -Parchetype=hearth:farm/terrace_field -Pstyle=mediterranean,alpine,karst,temperate -Pseeds=2 -Pterrain=slope
./gradlew blueprintPreview -Parchetype=hearth:farm/paddy -Pstyle=karst,tropical -Pseeds=3 -Pterrain=hills
./gradlew blueprintPreview -Parchetype=hearth:work/barn -Pstyle=all -Pseeds=2 -Pterrain=flat
./gradlew blueprintPreview -Parchetype=hearth:house/farmhouse -Pstyle=all -Pseeds=2 -Pterrain=hills
./gradlew blueprintPreview -Parchetype=hearth:house/longhouse -Pstyle=northern,tundra,moorland -Pseeds=3 -Pterrain=flat
./gradlew blueprintPreview -Parchetype=hearth:house/round_house -Pstyle=steppe,tropical -Pseeds=3 -Pterrain=hills
./gradlew blueprintPreview -Parchetype=hearth:house/stilt_house -Pstyle=bayou,tropical,maritime -Pseeds=2 -Pterrain=lake
./gradlew blueprintPreview -Parchetype=hearth:house/cottage -Pstyle=all -Pseeds=2 -Pterrain=steep
./gradlew settlementPreview -Pculture=temperate/farmsteads -Pdays=200 -Pterrain=hills -Pseed=2
./gradlew settlementPreview -Pculture=bayou -Pdays=200 -Pterrain=coast -Pseed=2
```

Images land in `hearth/build/previews/` (`sheet_<id>_<terrain>.png`, `settlement_<culture>_<terrain>_s<seed>_d<day>_iso.png`).
Debug options go in the terrain string, e.g. `-Pterrain="slope;seed=1007;facing=east;layout=compound;reach=0"`
(any other `key=value` is passed to the archetype as a param).

## Tests

`src/test/java/dev/voidmc/hearth/build/homestead/HomesteadTest.java`: farmland watered within four blocks; paddy and
field water contained; pastures gated and watered; barn lofts with a way up and hay; stilt houses clear of the water on
piles; longhouses with a central fire and HEARTH POI; split-level cottages opening on two levels; every home has a bed
(and most of its promised count); cottage upgrades; and the shared invariants for every forced value of `layout`,
`crop`, `roof` (longhouse), `loft`, `stilts`, `side_walk` in every culture.

## Known limits

- Farm plots are drawn without animals or crops growing over time; the pasture expects the sim to stock it (pens marked).
- A seven-ring (the smallest round house) still steps like an octagon; nine and eleven read round.
- Laithe and steading barns read best on deep plots (15+); at 13 deep the barn end of a laithe is easy to miss.
- The court farmstead's arcade wants a court five deep; on a shallower one it is left out.
- Northern timber stays brown in the base palette (spruce and dark oak): the silvered-and-tarred contrast and white trim
  come with the style's variants (pale oak, birch); the drip caps and horns use the roles that carry them.
- The mesa's terracotta has no stairs, so its parapets cannot be rounded over; pueblo walls stay square-topped.
- Offline renders show vanilla stand-ins for Expanse woods (willow, palm, baobab, redwood).
