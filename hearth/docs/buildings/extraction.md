# Extraction: quarries, mines, shafts, pits and the woodlot

The extraction architect's archetypes (LIVING_WORLD.md §7.3, §8.5): construction run backwards. Six archetypes,
registered in `build/archetype/ExtractionArchetypes.java`; classes in `build/archetype/extract/`, the kit in
`build/kit/extraction/`, names and sign texts in `assets/hearth/lang/en_us/build_extraction.json`. Tests beyond the
shared invariants: `src/test/java/dev/voidmc/hearth/build/extraction/ExtractionArchetypesTest.java` (every culture,
every synthetic terrain, plain and with the planner's params, both the middle and the top of the height range) and
`ExtractionDebugTest.java` (one plot in detail: problems, the surface, sections, walk maps, per-stage cuts, the
auto-lighter's fixtures; `HEARTH_EXTRACT_DEBUG=archetype,culture,terrain,seed,facing,height[,planned[,probeSeed]]`).

## Worked in campaigns

An excavation is worked for weeks: its blueprint is **built in campaigns** (`Draft.campaign(n)`, the same mechanism
the castle is raised with; BUILDING_GUIDE.md §"Campaigns", LIVING_WORLD.md §8.5). Campaign 0 is the opening works (the
yard, its shed, the rig, the shaft house, the ride); every later campaign is one bench, one segment of a drift, one
lift of a shaft, one spit of a pit, one coupe of the woodlot. The compiler emits the campaigns in order, each with its
own stages in `StageKind` order (cuts in `EARTHWORK`, props in `FRAME`, rails and lanterns in `EXTERIOR`;
`BlueprintImpl.campaign(stage)`, `campaignCount()`), and `BlueprintChecks.order` checks that order. Inside a campaign
the cuts carry order keys
(`Draft.order(key)`): a bench is cut outward from where the diggers come down, a drift slice by slice as the heading
advances, a shaft course by course. Every campaign after the opening cuts natural ground and registers `WORK_FACE` POIs
(where its gatherers stand); `BlueprintChecks.reach` checks that every work face can be walked to from the street.
Earth and gravel moved in `EARTHWORK` cost no goods, so no stage asks for more than a few hundred goods (the test's
ceiling is 3,000).

## The light rule (`LightRules.Family.EXTRACTION`)

Quarry pits, mines, shafts, clay and sand pits (the woodlot is a `FARM`): every walkable cell reached in the plot with
natural ground anywhere over the head (a gallery, a stall, a station) or on a ladder in a narrow way walled within two
blocks on all four sides (a shaft's ladder way, a trial pit) at **≥ 8**; rooms at ≥ 8; under a shelter (a roof at
most four blocks over the head: the portal's hood, a shed, the shaft house) never dark; the open pit, its benches, the
cutting and the spoil dark on purpose like a field; outside every entrance ≥ 8. Lit by design: lanterns under every
other cap in a drift and under the portal's cap, one in every stall and every station, one under a bearer every eight
blocks down a shaft, the culture's lamp at the gate (a lantern set on the bank's top where the street runs at the
foot of a bank). The auto-lighter is the safety net only.

## The catalogue

| id | frontage × depth | height | jobs | tier | flags | campaigns |
|---|---|---|---|---|---|---|
| `extract/quarry_pit` | 15–31 × 15–31 | 8–14 | QUARRIER | hamlet | OUTSKIRTS | 1 + one per bench (3 deep) |
| `extract/mine` | 7–11 × 16–48 | 6–12 | MINER | hamlet | OUTSKIRTS, HILLSIDE | 1 + one per ~6 rows of drift |
| `extract/shaft` | 7–11 × 7–11 | 10–16 | MINER | village | OUTSKIRTS | 1 + one per ~10 blocks of depth (2–9) |
| `extract/clay_pit` | 9–17 × 9–17 | 6–9 | CLAY_DIGGER | camp | OUTSKIRTS | 1 + one per spit |
| `extract/sand_pit` | 9–17 × 9–17 | 6–9 | CLAY_DIGGER | camp | OUTSKIRTS | 1 + one per spit |
| `extract/plantation` | 15–31 × 15–31 | 9–16 | FORESTER | village | OUTSKIRTS | 1 + one per coupe (2–5) |

All have no beds, no `maxPerSettlement` (the planner opens one working per site at a time) and suit every culture.
Every one has a store (the shed's chest, the ore heap, the log stack), a job site for its trade at the bench, and
`WORK_FACE` POIs for that trade in every later campaign.

## Archetypes

### `extract/quarry_pit` (QuarryPit.java)
A stone quarry cut bench by bench into the town's rock. A levelled pit head by the street: the quarrymen's open shed
with stonecutter, banker and grindstone, blanks and dressed blocks on pallets, a cart, the lamp and the sign; at the
rim the culture's rig (a treadwheel crane in Bohemia and the Alps, a gallows derrick in the north, shear legs in the
south and east), its chain let down a little further with every bench, a block of the rock swinging on it. The pit
goes down in lifts three deep, the working face steep on the uphill side and the other sides stepping in, short
flights of treads cut in the rock spiralling from bench to bench, a ladder up a wall now and then, blocks left half
cut free on the benches, grit and spalls on the floors. On rising ground the faces climb the hillside in steps of
their own; on land falling away the pit opens to it and a stair goes down from the rim. The spoil tip along one side
grows with every bench. **Params:** `depth` (default 9–15), `rock`, `site`, `kind`, `face`, `variant`.

### `extract/mine` (Mine.java)
An adit driven into an ore hill. A levelled yard by the street (the miners' lean-to, the ore heaped for the carts,
a cart, the lamp), and behind it the way in: on a hillside a headwall against the hill's face laid in courses (dressed
every other one, dry rubble between, a slab coping where it stands clear) with the timbered portal in it (a doubled set
under a hood of boards railed along its top, a lantern under its cap); on gentler ground a cutting that goes down a
block a step (a tread off the yard's floor at the first), its sides lined with dry-stone wing walls, until two courses
of land stand over the crown, and the portal always on a level row. A wall coping (or a fence) runs along the cutting's
rims and across its end wherever they stand three or more over its floor. The drift runs straight back, three wide,
its sides three high and its crown four, a timber set every three blocks (two posts let into the rock's line, a cap
across the crown, so the whole floor stays clear), a lantern under every other cap, rails down the middle (ascending
where the floor steps, treads at the sides) and a way either side of them: folk will not walk a rail they did not
start on, so the miners walk beside the track to every face. A stall is opened into alternate sides in every segment
where the ore was followed. Where the land
over it falls away the drift dips a step; it stops short of daylight and never runs under water. Where the land falls
away from the street (no hill to drive into) the cutting stops and the miners sink a trial pit at its end (a ladder
down, a lantern at its foot). The waste tip grows beside the yard with every segment. **Params:** `length` (rows from
the yard; default the plot's depth), `rock` (wing walls, treads, spoil), `speciality` (an ore id for the heap; else
iron, copper or coal by seed, deepslate ores in deepslate), `site`, `kind`, `face`.

### `extract/shaft` (Shaft.java)
A shaft sunk to ore under level ground. The shaft house: an open timber house on four corner posts over a stone
collar (dressed top course, three courses down), its roof in the culture's form where its eaves stay over the plot,
else a pent; under it the windlass, an axle on two trestles across the mouth with a crank on its end, its chain down
the shaft to the kibble over the bottom. The shaft is two by two, its ladder way up the left wall; every lift ends in
a station, a crosscut out of the back wall with its lantern and work face; bearers across the shaft every eight blocks
carry lanterns. The pad round the house is levelled to the yard (cut only as high as the plot reaches where a bank
rises behind it, run out to the street where the yard could not be levelled). Spoil heaps round the house grow lift
by lift. **Params:** `depth` (default 16–32, at most 96), `realm_below` (recorded), `rock`, `site`, `kind`, `face`.

### `extract/clay_pit`, `extract/sand_pit` (SoftPit.java)
A bank of clay or sand dug spit by spit (a lift one block deep, the planner's `depth` 3–4 of them, more where the
land falls away behind the yard), straight faces on three sides and the floor stepping down a spit a row toward the
working face; treads are boards of the culture's timber. A small yard with the diggers' lean-to and its bench, a cart
loaded with clay or sand, heaps on boards; for clay a trough of water where the clay is trodden and a loaf of it souring
under a slab, for sand a riddle on its post. On wider plots the stripped turf and topsoil make a low tip along one side.
**Params:** `depth`, `site`, `kind`, `face`, `rock` (recorded; red sand in the mesa or for a red `rock`).

### `extract/plantation` (Plantation.java)
The woodlot behind the logging camp, coppice-with-standards worked in rotation. A yard with the forester's shed,
logs stacked for the carts, a chopping block, a cart of logs, the lamp. Behind it a ride up the middle (a holloway
where the land rises faster than a step a row, embanked where it falls, stopping where it cannot go on) and the
coupes across the depth, each closed by a wood bank (a ditch a block deep, its earth thrown up on the coupe's side),
each at its age in the rotation: just cut (stumps of the timber tree, saplings set between them, cordwood stacked by
the ride, a standard or two left), regrowing (coppice stools bushing up a man's height, a standard here and there),
or grown (the culture's tree, tall). Crowns are kept off the ride. The timber is the culture's own tree unless that is a
garden or orchard tree no forester plants for wood: the karst's cherry gives way to fir, the olive to an oak coppice, the
wet tropics' palm to jungle hardwood (`Trees.timber`). **Params:** `coupes` (2–5), `site`, `kind`.

## The kit (`build/kit/extraction/`)

- `Rock`: the host rock from the `rock` param or the culture's land (stone, andesite, granite, diorite, limestone,
  sandstone, red sandstone, deepslate, tuff, blackstone, mud; clay and sand for the soft pits): natural, rough,
  dressed, treads, slab, spoil mix.
- `Benches`: a pit planned in levels and cut lift by lift (flights, ladders, stubs, debris, work faces);
  `debris` and `ladders` switch off for the soft pits.
- `Pithead`: the yard and its pieces (levelled yard, the way in (`path` for a pit, `way` for a mine or a shaft),
  rig, shed, stacks, cart, lamp, sign, fence, stair down; `reserve` and `keep` hold cells for the working's own works).
- `Rigs`: treadwheel crane, gallows derrick, shear legs, and the chain let down campaign by campaign.
- `Spoil`: a tip on its spine growing campaign by campaign, tapered so no column stands more than a block over a
  neighbour or the land beside it (a mound to the angle of repose on every side, never a loaf with a cliff).
- `Drift`: an adit planned row by row (level profile, portal, stalls) and driven segment by segment.

## Previews

```
./gradlew -I docs/buildings/extraction_preview.gradle extractionPreview -Parchetype=mine,shaft -Pstyle=temperate,northern,desert,karst -Pseeds=2 -Pterrain=hills
```

Renders the working in deep ground: both corners, a section through the middle by day and by night, and the working
after its opening, a third, two thirds and all of its campaigns (`build/previews/extraction/`).
