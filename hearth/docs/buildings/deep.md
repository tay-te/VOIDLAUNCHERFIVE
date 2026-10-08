# Deep buildings: towns in the realms

The deep architect's archetypes: the six buildings of a town on a realm's floor (LIVING_WORLD.md §8.2, §8.5),
registered in `build/archetype/DeepArchetypes.java`. Classes live in `build/archetype/deep/`, the deep kit in
`build/kit/deep/`, names in `assets/hearth/lang/en_us/build_deep.json`. Tests beyond the shared invariants:
`src/test/java/dev/voidmc/hearth/build/deep/DeepArchetypesTest.java`. Previews: `docs/buildings/deep_preview.gradle`
(`deepPreview` for single buildings on a synthetic realm, `deepTown` for a street); classes
`gametest/deep/DeepPreview`, `DeepTown`, `DeepScene`.

A deep town is the mining town gone underground: when a settlement's mine breaks into a realm the planner
(`plan/deep/DeepTown`) sinks a lift from the street to the realm's floor, lays a little square at its foot with the
hall on it, runs streets along the foot of the realm's wall and out across the floor, and fills them with dwellings cut
into the wall, a forge, a market and glow gardens. The look is the Bohemian silver town (Kutná Hora, Jihlava, Telč)
carried below: arcades (*loubí*) under jettied upper storeys, steep halls with towers, copper gone green in the damp,
and — the one licence the surface never gets — the realm's own glow set in the stone and grown in the planters.

## How the deep town reads its realm

- **`origin.y` is the realm's floor** where the plot meets its street; `realm` (the `UndergroundProbe.Realm` id) marks a
  deep plot. The terrain probe should be the realm's floor as the planner sees it (`plan/deep/RealmTerrain`: where the
  realm's wall stands, the rock rises as ground to the vault). If a caller hands a deep plot the surface's probe instead
  (the ground along the plot's front six or more over its floor), `DeepSite.probeFor` swaps in a flat floor at the plot's
  level through `Archetype.terrain` (a small default method added to the shared interface), so the building stands on
  its floor instead of digging for the sky.
- **`ceiling_y`** (world y, the underside of the vault over the plot, the lowest over its footprint): nothing is built
  into it. Every deep building keeps one clear of it (`DeepSite.top()`); the hall's towers and the market's pavilions
  shorten, a dwelling loses its upper storey and becomes a one-room house under a pent. Absent: only the plot's height
  limits. **Requested from the planner** (`docs/requests/b-deep.md`).
- **`character`** (the realm's character as Expanse's underground probe names it: `wilds`, `lush`, `dripstone`,
  `crystal`, `ember`, `mere`, `frozen`, `roots`, `fossil`, `tidal`, `amber`, `brimstone`) picks the realm's glow
  (`Glow`): the lamp heads, the accent set in the stone, the soil and plants of the planters and beds. Absent: read
  from the `local` light the planner gives deep plots, else the glowcap wilds.
- **`local`**: the realm's rock and glow (`Materials.realm`), preferred where the deep region's habits allow.
- **The rock round a plot.** Dwellings stand with their backs cut into the rubble at the wall's foot or into the wall
  itself; the forecourt, the stair and the roof terrace are cut back out of the rock (`DeepDwelling.cut`), so a terrace
  is a ledge hewn into the wall, where a dwelling stacked on it stands.

**Light.** Every walkable room cell is at block light ≥ 8 and none at 0, and outside every entrance ≥ 8
(`BlueprintInvariantsTest`), counted on vanilla light only: Expanse's glowing blocks fall back to stand-ins outside
the game and glow differently, so they are the accent, never what the rule counts on (`Glow` javadoc). In the game a
realm has no sky light: the previews' lit renders seal the vault and show the town by its own light.

**Style.** The `deep` region (`hearth_style/region/deep.json`, owned by refine) dresses every culture's deep town in
deepslate, tuff and copper; what keeps a Bohemian deep town from a karst one is how the culture shapes that stone and
the timber it brings down (`DeepManner`): pointed or round arches, galleries, oriels, lattice screens, the hall's roof
(steep, gable, flared, hip, dome, flat), its own doors, shutters, floors, railings and furniture, and its own finish
for the upper storeys it raises on the realm's stone (`DeepManner.home`: the lime plaster and timber of Bohemia, the
sandstone render of the desert, bamboo boards in the karst). Copper roofs weather in patches by noise (`Rock.copper`).

## The catalogue

| id | frontage × depth | height | jobs | beds | tier | flags | max |
|---|---|---|---|---|---|---|---|
| `deep/hall` | 15–23 × 17–27 | 12–20 | REEVE | — | village | | 1 |
| `deep/dwelling` | 7–11 × 7–11 | 7–11 | — | 4 (4–6) | village | HILLSIDE, STACKABLE, ATTACHABLE | |
| `deep/market` | 11–17 × 11–17 | 8–12 | MERCHANT | — | village | CENTRAL | 1 |
| `deep/forge` | 9–13 × 9–13 | 8–14 | SMITH, SMELTER | — | village | | |
| `deep/glow_garden` | 11–19 × 11–19 | 6–9 | FARMER | — | village | | |
| `deep/lift` | 9–11 × 9–11 | 12–320 | HAULER | — | village | VERTICAL | 2 |

All are `UNDERGROUND`, any culture. Measured over 28 plots each (14 cultures × the realm's open floor and the foot of
its wall, the planner's params, the lift 64 deep): the largest single stage asks for 1,569 goods (the hall's walls),
the lift's shaft 1,436; whole buildings average 2,600 (hall), 2,300 (lift), 920 (market), 640 (dwelling), 600 (forge,
garden). Every building passes the shared invariants on the six surface terrains (built by hand, no realm) and on the
four realm grounds (`floor`, `rubble`, `wall`, `ledge`; the lift on the open floor the planner stands it on).

## Archetypes

### `deep/hall` (DeepHall.java and the `Hall*` plans)
The moot hall where the reeve sits, raised to the plan its culture builds a hall to (`DeepManner.hallForm`; art review
item 28 found one basilica in all fourteen cultures). Every plan keeps the vault one clear, stands on the realm's stone
and is furnished by the same moot (`kit/deep/Moot`: a runner from the doors to a dais, the reeve's high table, chair,
lectern and town chest, banners behind, long tables and benches, the town's records, lanterns from tie beams laid only
where both ends rest on something, the room lit to the rule).

| plan | cultures | what it is |
|---|---|---|
| basilica (`DeepHall`) | temperate, alpine, moorland, maritime | the aisled hall of the silver towns: a tall nave on tie beams, copper lean-to aisles, arcade and clerestory, buttresses, two front towers round a frontispiece with a rose window (on the broad halls each tower holds a spiral of slabs from a door on the aisle to a lookout, glazed slits up its faces, the spiral's foot beside the door so the way in stands clear; the dais keeps three rows: step, high table, the reeve's chair). The nave wears the culture's own covering; the towers' caps are the culture's: **dark shingle helms** (Bohemia), **copper onions** (the Alps), **squat crenellated towers** (the moors and coasts) |
| stave (`HallStave`) | northern | a core of staves on a stone sill in a gallery (*svalgang*) of posts with arched heads under a skirt roof; where the vault leaves room an upper core on masts rising out of a second skirt; a steep top roof with crossed boards at its ends, a ridge turret and spire where it fits; a long fire down the floor |
| longhouse (`HallStave`) | tundra | one long low hall, a stone footing under timber walls barely a man high, a great half-hipped roof sweeping down along the depth, posts carrying the purlins, a long fire |
| pavilion (`HallPavilion`) | karst, wisteria | a raised timber hall on a terrace: a dressed platform two or three high with a balustrade and a broad flight, a veranda of columns with brackets under a lower curved eave with lifted corners, lattice windows, triple doors, a flared hipped roof over the hall; a brazier inside |
| court (`HallCourt`) | desert | walls of the culture's render behind stepped merlons, a pishtaq portal higher than them with a pointed arch over the doors' recess, slim towers under little copper domes, an open court with a pool and arcades down its sides, the hall across the back under a copper dome on a drum |
| sahel (`HallCourt`) | steppe | the same court built as the Sahel's earthen halls: buttresses every third cell rising to pinnacles, timber torons through the faces, a buttressed tower round the door under a timber lintel, no dome |
| pueblo (`HallPueblo`) | mesa | a great house of earthen render stepped back in terraces (the moot below, a record room over it, a lookout over that where the vault allows), vigas through the walls, a rounded parapet round every terrace (the render capped with a slab, too high to step onto; open where the ladders arrive), ladders from terrace to terrace, a mission's bell wall over the door |
| loggia (`HallLoggia`) | mediterranean | the *palazzo comunale*: a whitewashed block with quoins and round-arched windows, an open loggia of round arches with a terrace over it, a low hip of tiles, a campanile at the front corner with an arched belfry and a pyramid cap |
| stilt (`HallStilt`) | tropical, bayou | a timber hall on a grid of posts three over the floor, a gallery two wide round its front and sides behind a railing, shuttered windows, a great hipped roof on deep eaves, a broad flight up the front, lanterns hung under the deck |

The culture's own materials come from `DeepManner.home` (walls, render, columns) and `DeepManner.rehome` (a roof the kit
rasterised in the deep region's tiles relaid in the culture's own covering); the realm's stone stays in plinths, the
foot course and terraces, and copper stays where it belongs (the basilica's aisles, the onions and the desert's domes).
Whatever the plan (wave 7, `HallParts.cut`/`kerb`): the hillside inside the plot is cut back to the floor round the hall,
so no bank runs up to its eaves (the stave hall's skirt and the longhouse's eaves come flush with the walls, the
longhouse a row forward, the pueblo drops its side vigas where a bank stands level with them); and wherever the apron,
the way round or the ground under a raised hall ends at a fall of three or more to the land, a kerb of the foundation's
wall stands at the edge. The street row is kerbed only where some of the street is there and no water lies before it,
and never between the street and the way in; a raised hall's flight moves along the front to where the street meets it.
The pavilion's balustrade posts are capped, the pueblo's ladders railed across their face from a man's height and its
bell wall's arch a niche over a sill (no door onto the drop).
**POIs:** DOOR (2–3 in the pueblo), MEETING_POINT, JOB_SITE REEVE + LECTERN, TABLE ×2–3, SEAT, THRONE, STRONGBOX,
STORAGE (record chests), HEARTH (a wall hearth, a long fire or a brazier), VIEWPOINT (the basilica's towers, the pueblo's
top terrace), NOTICE_BOARD (most). **Params:** `realm`, `ceiling_y`, `character`, `local`, `variant`.

### `deep/dwelling` (DeepDwelling.java)
A miner's house built the way the silver towns built theirs: a ground storey of rough-cut stone, and over it, on stone
piers and arches that make a covered arcade along the street (*loubí*), a jettied upper storey — timber-framed with the
culture's own infill in the timber traditions, dressed stone in the south's. A cornice and a parapet close a flat roof
terrace at the top of the plot's height. In front runs the house's stair, a flight of the realm's stone on its own
soffit against the upper front (the arcade, where there is one, behind it), rising to the upper door and on to the
terrace (a ladder on up the front where the frontage is too short). The street row before it carries its parapet: a
coping on a corbel beside every step three or more up, a newel past its head, the ladder railed its whole height on the
street side and a hood over the upper door beside it (wave 7: the flight's fall edges). On a ledge the flight starts on
the high side, and the forecourt's ends and street row carry a coping wherever the land beyond lies three or more
below (the way in left open); where the realm's wall stands on over the plot's top the terrace comes a course lower to
keep its head room. The desert's merlons and the mesa's and steppe's rounded parapets stand on a course of the render. An arcade only on plots nine or
more deep, so the rooms behind it keep their depth. An arched street door with a fanlight and a keystone of the realm's glow; framed windows; planters of the
realm's plants; glow lichen in the damp corners, glow berries hanging from the cornice; a chimney pot smoking on the
terrace. The top of the front is the culture's: an eave of its own covering sloping out over the street under a timber rail (the north, the Alps, the tundra, the tropics, the bayou, the Mediterranean), its corners lifted with lanterns under them (the karst, the glades), stepped merlons (the desert), vigas under a rounded parapet (the mesa), torons through it (the steppe), the stone parapet and cornice of the silver towns (Bohemia, the moors and coasts). Lower houses keep their piers to the cornice and wear a pent roof or a timber oriel; under a low vault the
house becomes one room. Inside: the hearth and table below, beds above (one to every ten cells of the bedroom's floor,
two to four: a chamber, not a dormitory), the household's tools on a shelf (picks for a miner). Where the ground rises behind, the back is cut into it and the rock round the forecourt and terrace is cut
back.
**POIs:** DOOR ×2 (street and upper door), BED ×2–4, HEARTH, TABLE, SEAT, STORAGE ×1–5.
**Params:** `stacked` (with `stacked_on`, `level`: the front rows stand on the terrace below; nothing is founded through
it), `party_left`, `party_right` (no windows, no parapet over a shared wall), `station`, `household` (`job`),
`realm`, `ceiling_y`, `character`, `local`, `variant`. The terrace's standing level is the plot's height minus one, so a
dwelling stacked on it (floor at `floorY + maxHeight`) stands one step up from it (two where the rock overhangs the
plot's top and the terrace came a course lower: the stacked plot's cut then opens the rock over it).

### `deep/market` (DeepMarket.java)
A paved court open to the street, closed on its other three sides by arcaded wings of stalls: dressed piers and arches,
a counter in every bay (a barrel of stock and a slab of the furniture's wood) with the merchant behind it, a cloth
awning out over the court, wares by stall (the deep's own glow berries, mushrooms, ore, iron and the realm's minerals,
and what comes down the lift: bread, wool, candles). The outer walls have pilasters behind the piers and a window high
in each bay. The wings are roofed in gables of the culture's own covering, pitched a block a block (their street ends walled up); at the back corners two pavilions rise over them under
copper pyramid roofs with a glow at the finial. In the court a lantern tree — a stone pillar with iron arms hung with
lanterns over a basin or benches — and festoons of lanterns on chains strung across the court. The merchants' doors go
through the first pier of each side wing, into the walk behind the counters.
**POIs:** COUNTER, MARKET_STALL, JOB_SITE MERCHANT and STORAGE ×7–10 (one of each per stall), DOOR ×2, MEETING_POINT,
SEAT ×4. **Params:** `realm`, `ceiling_y`, `character`, `local`, `variant`.

### `deep/forge` (DeepForge.java)
The deep town's forge on a vent of the realm's fire: a smithy hall of soot-dark rough stone open to the street under a
great timber beam on corbels. Set in the floor at a back corner a pool of lava rimmed with magma and fenced with iron
bars; against the back wall the smelting furnace — blast furnaces between stone cheeks under a hood, a lava crucible,
its broad stack through the roof; down the hall the anvils, the smithing table, a grindstone and the quench trough; ore
heaped in bins along the side wall with a rail line running in to them from the street; a chest, tools on a shelf; vents
of iron bars high in the side walls. The roof is the culture's (wave 7, from one deepslate bunker in fourteen towns): a
steep gable with a louvre on its ridge and its frame showing in the gables, the culture's infill between (Bohemia, the
north, the Alps, the moors and coasts), a flared hip on wide eaves (karst, glades, tropics, bayou), a flat deck behind a
capped parapet with beam ends through it (desert, mesa, steppe, Mediterranean); tie beams with lanterns.
**POIs:** DOOR, JOB_SITE SMITH ×2 (smithing table, the striker's place between the anvils), JOB_SITE SMELTER ×2 (the
furnace), STORAGE. **Params:** `realm`, `ceiling_y`, `character`, `local`, `variant`.

### `deep/glow_garden` (GlowGarden.java)
Where the deep grows its food under the vault: a low wall of the realm's stone, a gate to the street, four raised beds
of the realm's soil in dressed kerbs with a rill down each, planted in rows with the realm's glowcaps and mushrooms
(azalea and ferns in the lush realms, crimson roots in the ember realms); down the main walk a timber pergola dripping
with glow berries; at the crossing a giant mushroom over benches (a lamp post under a low vault); lamp posts of the
realm's glow at the corners; a potting shed in a back corner (on larger plots) with the composter, a chest and a lantern,
else the composter and chest at the head of the main walk.
**POIs:** GATE, DOOR (the shed), JOB_SITE FARMER (composter), STORAGE, SEAT ×2–4. **Params:** `realm`, `ceiling_y`,
`character`, `local`, `variant`.

### `deep/lift` (DeepLift.java)
The shaft that joins a surface town to its deep town — the link that makes the deep reachable. A seven-wide stone
shaft from the realm's floor to the street. Inside, a stair climbs round and round an open lightwell, a step a cell, a
rail post on the well's side of every step; down the well hangs the hoist's chain with a lantern every six blocks, from
the winding beam at the head to the cage at the foot. In the realm the shaft stands as a tower on a stepped plinth, slit
windows up its faces, string courses every eight blocks with lanterns on brackets, and meets the vault on a ring of
corbels; above the vault it is a lined shaft through the rock. At the head the winding house on the street — the
surface town's own walls and roof — with a timber headframe and its sheave through the roof (standing up to nine over
the eaves where the plot allows, braced under its crown and girt halfway up), the hatch railed on three sides beside the
windlass, a paved yard with lamps. The cage at the foot is two high, its roof low enough that no step beside it stands
four over the floor without its rail.
Built by hand (no `top_y`): a pit-head over a shaft sunk `depth` (default 24) to a landing chamber.
**POIs:** DOOR ×2 (the street and the deep square), JOB_SITE HAULER ×2 (the windlass at the head, the cage at the
foot), STORAGE, SEAT. **Params** (the planner's): `top_y`/`street_y` (the street at the head), `bottom_y` (= `origin.y`),
`deep_street_y` (the deep square at the lower door), `deep_facing` (world direction of the lower door; default the
plot's facing), `ceiling_y` (where the tower meets the rock; default sixteen under the street), `character`, `local`,
`realm`; without `top_y`: `depth`. The main entrance is the door on the level the caller's probe reads (the realm's
floor or the street).

## The kit (`build/kit/deep/`)

- `DeepSite`: the realm a plot stands in (`ceiling_y`, `character`, `top()`), the surface-probe fallback (`probeFor`).
- `DeepTerrain`: synthetic realm ground for tests and previews (`floor`, `rubble`, `wall`, `ledge`) and its vault
  (`ceilingY`, `ceilingOver`).
- `DeepManner`: what each culture keeps underground (arches, galleries, oriels, lattices, hall roof, the hall's plan
  `hallForm`) and its own finish (`home`, `rehome`).
- `Glow`: the realm's lamp, accent, mineral, soil and plants; glow-berry vines.
- `Moot`: the hall's furnishing inside whatever shell its culture raises (dais, high table, throne, lectern, chest,
  tables, records, the fire, tie beams and lanterns).
- `Rock`: the masonry vocabulary — dressing, piers with capitals, arched door heads with a glowing keystone, framed and
  arched windows, parapets, planters, lamp posts, benches, copper sheathing weathered by noise.

## What is weak (next passes)

- The dwellings now differ at the top of their fronts (eaves, lifted corners, merlons, vigas, torons; see the dwelling),
  but every one is still the same block under its terrace: the terrace must stay clear for a dwelling stacked on it and
  nothing may rise over the plot's height, so the silhouette can only change in front. A taller envelope (or the
  planner telling a dwelling nothing will be stacked on it) would allow gables and roofs.
- The halls fit their tallest parts to the vault: under a low vault the stave hall loses its turret and upper tier and
  the pavilion's roof flattens; the northern turret appears only on narrow plots or high vaults.
- The tropical and bayou halls share one plan (materials alone tell them apart).
- The glow garden's pergola and mushroom are small on the minimum plots; the market's court is narrow at 11 wide.
- The lift's tower is a plain shaft between its string courses: it could carry a gallery at the vault or buttresses.
