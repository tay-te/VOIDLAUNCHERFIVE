# VOID Hearth — landmarks

The building each settlement is known for: one per settlement (two at city tier), raised from town tier, chosen by the
planner from the signature's preferences (`plan/model/Signature`, `plan/growth/Landmarks`). They are the silhouettes
VOID LOD shows on the horizon, so every one is tall or broad enough to read from far away and dense with detail close
up. Registered in `build/archetype/LandmarkArchetypes.java`; classes in `build/archetype/landmark/`; the shared pieces
in `build/kit/landmark/`.

## The archetypes

| id | class | size (front × depth) | height | tier | cultures | flags | jobs, beds |
|---|---|---|---|---|---|---|---|
| `hearth:landmark/bell_tower` | `BellTower` | 7–11 × 7–11 | 26–44 | town | temperate, wisteria, northern, alpine, moorland, maritime, mediterranean, bayou | VERTICAL | — |
| `hearth:landmark/clock_tower` | `ClockTower` | 7–11 × 7–11 | 28–44 | city | all | VERTICAL, CENTRAL | — |
| `hearth:landmark/cathedral` | `Cathedral` (+ `StaveCathedral`) | 17–25 × 29–41 | 26–56 | city | temperate, wisteria, maritime, alpine, moorland, mediterranean, northern | VERTICAL | PRIEST |
| `hearth:landmark/temple` | `Temple` | 15–21 × 17–25 | 16–30 | city | desert, mediterranean, mesa, steppe, karst, tropical, bayou | — | PRIEST |
| `hearth:landmark/pagoda` | `Pagoda` | 15–21 × 17–25 | 28–40 | town | karst, wisteria | VERTICAL | — |
| `hearth:landmark/great_hall` | `GreatHall` | 15–23 × 21–31 | 14–22 | town | northern, tundra, moorland, alpine | — | — |
| `hearth:landmark/lighthouse` | `Lighthouse` | 11–15 × 13–21 | 26–40 | town | maritime, tropical, mediterranean, northern | VERTICAL, WATERFRONT | 1 bed (the keeper) |

All have `maxPerSettlement` 1 and no `upgradesTo`. Every culture has at least one landmark of its own (tested).

### Bell tower
A square campanile on a chamfered plinth: a core of walls with clasping corner piers (the faces are recessed panels),
string courses each over its Lombard band of little arches, the door stage banded in the base stone, a deep blind arch
with a lancet in the long middle stage, the upper stage in dressed stone over the rendered shaft below (so the tower
lightens as it climbs and no two stages read alike), paired
lights or a clock higher up, the open belfry (a triple light or a big arch over a balustrade) with one to three bells on
a beam, a corbelled cornice. Panels in the style's light walling where it is masonry (white plaster between stone piers),
the belfry in dressed stone. Crowns by culture: needle, octagonal or broach spires behind a parapet with pinnacles
(maritime, moorland, wisteria), crenellated parapets with a flag (moorland), low tiled pyramids on brackets or
a brick campanile (mediterranean), onion domes (alpine), curved hips (karst). Timber cultures (northern, tundra, bayou,
tropical) raise a stone base under a pent skirt and, on a seven-wide core, step the framed shaft in a block behind it
(braces, lights) before the louvred belfry flares out two blocks on raking brackets under stacked shingle caps or a tin
pyramid, as a northern klockstapel does. Spires and domes take copper where the roof is shingle, and on
a share of the rest, except in temperate towns: the Bohemian town tower is lime render to its cornice (no clasping piers,
no rustication, its corner stones alone), a course over the door stage and under the belfry, lights up the shaft and a
clock on a share, a belfry of paired lights, and the gallery and cap of `kit/civic/Bohemian`: a timber balustrade on
corbels before a white attic, under a dark shingle or slate helm (or an onion on a share). The cathedral's spires in a
temperate town are dark shingle or slate too (`Bohemian.shingle`), copper only on a baroque onion. Spiral stair from the door to the belfry (VIEWPOINT); a forecourt with a bench, a lamp and pots
(MEETING_POINT, SEAT).
Params: `crown` (a `Crowns.Kind` name), `clock` (boolean).

### Clock tower
A shaft on a plinth (deep blind arches and lancets), a clock stage corbelled out a block wider with a dial laid in blocks
on each face (white face, black hands each at its own hour; on 9- and 11-wide stages a rim of the roof trim and gold
marks), pinnacles at its corners, the bell chamber over it (behind a walkable gallery on 11-wide plots), and a crown:
steep spires, open cupolas with a dome, domes, onions, curved hips. The spiral climbs past the clock room (grindstones for
the works, the keeper's lectern, a chest) to the bells. VIEWPOINT, LECTERN, MEETING_POINT.
Params: `crown`.

### Cathedral
Planned in bays: a west front on the street, nave and aisles, a transept, a choir and a round apse; aisles under lean-to
roofs below a clerestory; the nave, transept and apse roofed as one surface (valleys and hips resolve themselves).
- **Gothic** (temperate, maritime, moorland, wisteria; any style without a manner of its own): twin west towers with
  needle spires and belfries (70 %), or low towers and a crossing tower with a great spire (30 %, flanking turrets on the
  front); three portals, the main one deep with jamb shafts, a tympanum and a gabled hood, a gallery of little arches
  over it and a rose window; buttresses with pinnacles and flying buttresses over the aisle roofs; a louvred flèche
  with a needle on the crossing; ashlar walls; spires in copper on a share of them.
- **Romanesque** (mediterranean): low tiled roofs, a campanile at a front corner, an octagonal drum and dome over the
  crossing.
- **Baroque** (alpine): white walls with grey dressings, twin towers under copper onions, a dome over the crossing on
  half of them.
- **Stave** (northern): a great stave church in the manner of Heddal and Borgund (`StaveCathedral`). A tall core of
  staves (the midtrom) over columns and open arcades, aisles wrapping it under lean-to roofs (their ends boarded), the
  svalgang gallery on arched posts round the foot of everything, the roofs climbing in tiers in timber shingles (never
  slate); the west gable carved with a band and a round light; the roof turret rising from the ridge in two stages, a
  louvred belfry with its bell under a skirt and a stave cap (or a slender spire on narrow closes); a lower chancel with
  its own little louvred turret and needle, a round apse under a cone; a steep-gabled porch on carved posts; dragons
  rearing from every apex. Inside: pews down the nave, lanterns from the tie beams and in the aisles, the altar and the
  priest's lectern in the chancel, candles in the apse; a churchyard where the close leaves room.
The west towers carry their belfries a full stage over the nave's ridge; the nave's height is reckoned from a 44-block
sky whatever the plot allows, so the extra height of a 56-block plot goes to the towers and spires. Spires taper on a
concave profile (`Shapes.needle`): on a five-wide tower an octagon on the cornice with pinnacles in the corners it leaves,
on a broad one a parapet with corner pinnacles and the needle inside with lucarnes and a band of the dressing; the flèche
is an open lantern of posts with a sanctus bell under a slender needle.
On a close at least 24 wide and 33 deep a cloister opens off one aisle (walks under lean-to roofs round a garth with a
basin and flowers). Inside: a chequered nave, pews either side of the central aisle (SEAT), banners on the piers,
chandeliers, choir stalls, side altars, a font, the high altar with candles and gold (ALTAR), the priest's lectern by
the crossing (JOB_SITE PRIEST, LECTERN), MEETING_POINT; belfries in the towers (VIEWPOINT).
It is raised on the chapel's site (the planner's `replaces`) and stands at every size from 17 × 29 to 25 × 41 (tested).
Params: `towers` (`twin`, `crossing`, `campanile`), `cloister` (boolean).

### Temple
Five forms, by culture (param `form` overrides):
- **colonnade** (mediterranean; the fallback for cultures with no form of their own): a stepped base, a peristyle under a
  frieze of the style's accent tiles and a low pedimented roof with acroteria, a cella with a tall door behind two columns
  in antis, a statue between braziers, candles on altars in the forecourt.
- **dome** (desert): a walled court with arcades and a basin, a minaret with courses, slits, a corbelled balcony
  (VIEWPOINT) and a domed upper stage; the sanctuary under a great dome on a windowed drum (copper gone turquoise on 85 %,
  else the roof stone), corner turrets, a tall portal framing a deep arch, rugs and lanterns, a niche in the far wall.
- **pyramid** (mesa, tropical): banded terraces, a stair up the front between sloped balustrades (with a landing in the
  plot at its foot), a shrine on the summit (flat roof with vigas, or a thatched hip), braziers at the corners, stelae.
- **hall** (karst, bayou; wisteria in previews): a hall on a stone podium (or piles and a deck where the style's
  foundations are wood) behind a gateway and stone lanterns: posts and lattices, a veranda, a double eave (curved and
  lifted where the style flares), an altar with its figure, cushions, a bell.
- **round** (steppe): a sun sanctuary in the manner of the Sahel's adobe: a low ring wall banded with glazed tile at its
  head, a gilded sun over the gate, a round platform in two steps, a crenellated adobe drum ringed by buttresses that rise
  as pinnacles, scaffold beams (torons) bristling through the walls, a tall portal standing proud of the drum, and a
  tapering sun tower on its roof with beams through it and a gilded sun with tiled rays on the apex; within, a ring of
  posts, rugs round the fire under the tower (HEARTH).
Every form: ALTAR, PRIEST job site, SEAT, MEETING_POINT, DOOR.

### Pagoda
A temple court and its pagoda: a roofed gateway at the street, a paved way between stone lanterns and blossom trees, a
pond under a three-wide moon bridge where the court is deep enough, a podium with a balustrade and steps, then five tiers
(seven where the sky allows: param `tiers`) of posts and lattice lights, each ringed by a curved eave lifted at the
corners with lanterns hung under the tips and brackets under it; tiers step in behind little balustrades; the top roof
rises to a gilded boss and a spire of rings. Inside, a spiral stair from the shrine (figure, candles, cushions: ALTAR,
SEAT) past a scroll room (LECTERN) and a bell to the lookout (VIEWPOINT).

### Great hall
A long hall under a vast roof, gable to the street. Northern and tundra: log walls notched at the corners, raking braces
leaning on them, boarded gables boarded with vertical battens and a sun-wheel light, whose barge boards cross over the apex
and curl outward into carved dragon heads, smoke vents on the ridge, a porch on posts, banners either side of it; the
roof, vents and porch shingled in the culture's own timber (the gable boards), so the hall reads warm brown over its dark
logs rather than a black mass. Moorland: rubble walls with quoins and buttresses, crow-stepped gables. Alpine: two
storeys, a whitewashed stone hall with arched lights and quoins under a boarded upper storey with shuttered windows and
flower boxes, a broad low roof with eaves three deep, a balcony across the gable front, and a bell turret on the ridge
(louvres, its bell, a copper onion). Inside: two rows of posts under tie beams, the long hearth down the middle
(HEARTH), long tables with benches (TABLE, SEAT), the high seat on a dais under banners, mead barrels and a chest
(STORAGE), lanterns on chains from every tie beam, staggered either side of the hearth; woodpiles and a runestone outside.

### Lighthouse
A round tower on a mound of weathered rock: a flared plinth, the livery (the style's masonry walls, or white render, in
bands of the town's strongest cloth colour as concrete on 75 %, red before blue before the rest: param `bands`), a door
under a hood, little windows climbing it, a course at mid-height, the watch room's windows under a corbelled
gallery with an iron railing round a glazed lantern room (sea lanterns), a cap and a vane. The gallery deck is on a whole
turn of the spiral so the last step lands by the lantern room's door (VIEWPOINT). The keeper's cottage in front (bed,
hearth and chimney, the log on a lectern, chests). Where water reaches the plot, a plank jetty on posts with mooring posts
and a lamp at its end (DOCK).

## How they rise (stages)
Footings and podiums (FOUNDATION, EARTHWORK) — then the skeleton (FRAME): piers, buttresses, corner piers of towers,
timber posts and girts — then the walls between them, bottom up (WALLS) — floors, ceilings and stairs (FLOORS) — roofs,
spires, crowns, flyers and pinnacles (ROOF) — glass and doors (OPENINGS) — furnishings, bells (INTERIOR) — paving,
lamps, gardens (EXTERIOR). A half-built cathedral shows its piers and buttresses first, then walls rising between them,
then its roofs and spires. Goods per stage at the largest sizes stay under about 2 100 units (the cathedral's walls);
`LandmarkTest.stagesStayABuildersLoad` prints every bill.

## The landmark kit (`build/kit/landmark/`)
| class | what |
|---|---|
| `Stone` | a material with the cuts masonry needs; `masonry`, `ashlar`, `dressing` (crisp: main material only), `panel`, `footing` pick the style's best stone; `sheet` (roof stone, or copper over shingle towns), `copper`, `fixed`; a weathered stone-brick fallback for styles without stone |
| `Tower` | a square tower raised stage by stage: plinth with chamfer, shaft with clasping piers or alternating quoins (corner stones alone on a five-wide core, so its render reads), courses round any outline, Lombard bands, deep blind arches (`recess`), openings (slit, single, bifora, trifora, arch, louvre), floors, cornice and deck, spiral stair, bells |
| `Crowns` | spire, octagon, broach (with lucarnes and pinnacles), pyramid on brackets, onion (drum, ogee bulb, lantern), dome (drum, lantern), open cupola, crenellated parapet with a flag, stave caps, curved hip with lifted tips; `rows` estimates a crown's height for laying out stages |
| `Details` | string courses, corbel tables, corbels, round-headed arches with voussoirs and jambs, paired and triple lights, slits, pinnacles, finials and rods, bells, clock dials, stone lanterns, banners, candles, ring courses |
| `Shapes` | the octagon ladder (`oct`, `ladder`: square, octagons, the square a size smaller... down to the plus and the point) and forms stacked from it (`stack`: stairs where a row draws in, upside-down stairs under a swell): `needle` (a tapering spire held on a concave profile, a shaft of wall posts and a rod), `domeRows`, `onionRows`; spire fields (octagon, broach, pyramid, bell-cast), capped and outside fields, solids of revolution (`turned`), a walkable spiral stair in any stone, roof `support` (no floating apexes or ridges), `reclad` |
| `Grounds` | footings, podiums, arched doorways, forecourts (approach, apron, bench, lamp, pots), robust trees (every leaf joined to the trunk) |
| `Wooden` | timber belfry towers (stone base, pent skirt, framed stages, jettied louvred belfry) |
| `Eaves` | curved skirts with lifted corners and brackets, hipped tops with a spire of rings |

## Previews
```
cd hearth
JAVA_HOME=C:/Users/bitto/VOID/.tools/jdk-25 ./gradlew blueprintPreview -Parchetype=landmark/cathedral -Pstyle=temperate,maritime -Pseeds=3 "-Pterrain=flat;tier=4"
./gradlew blueprintPreview -Parchetype=landmark/temple -Pstyle=all -Pseeds=1 "-Pterrain=hills;tier=4"
./gradlew blueprintPreview -Parchetype=landmark/cathedral -Pstyle=temperate -Pseeds=2 "-Pterrain=slope;tier=4;towers=twin"
./gradlew blueprintPreview -Parchetype=landmark/lighthouse -Pstyle=maritime -Pseeds=2 "-Pterrain=river;tier=3;facing=west"
./gradlew settlementPreview -Pculture=temperate -Pdays=400 -Pterrain=hills -Pseed=2
```
Landmarks are tier 3–4 in game (the planner adds one to the settlement's finish); preview with `tier=3` or `tier=4` to
see the dressed materials (deepslate roofs in the temperate style, for instance).

## Tests
`src/test/java/dev/voidmc/hearth/build/landmark/LandmarkTest.java`: registration (one per settlement, town+, cultures
covered), the points folk use in every culture, bells and clock dials, the lighthouse's jetty and dock over water, the
cathedral at its smallest and largest closes in all three tower layouts, the skeleton before the walls, stage bills,
heights. The shared `BlueprintInvariantsTest` covers every landmark in every style, variant and terrain.
