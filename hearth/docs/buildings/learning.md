# Clergy and learning

The houses of the orders and of the learned (LIVING_WORLD.md §8.1, §8.5): where clergy and scholars live and work, and
the little holy places on the roads. Six archetypes, each built in every culture's manner, registered by
`build/archetype/LearningArchetypes.java`.

| id | class | for | size (frontage × depth) | height | jobs, beds |
|---|---|---|---|---|---|
| `hearth:civic/monastery` | `archetype/civic/Monastery` (+ `Precinct`, `PrecinctChurch`) | town+, OUTSKIRTS, max 1 | 21–31 × 21–31 | 12–22 | PRIEST, SCHOLAR, BREWER; 10 beds |
| `hearth:civic/convent` | `archetype/civic/Convent` (+ `Precinct`, `PrecinctChurch`) | town+, OUTSKIRTS, max 1 | 17–27 × 17–27 | 10–18 | PRIEST, HEALER; 8 beds |
| `hearth:civic/shrine` | `archetype/civic/Shrine` | hamlet+ | 3–5 × 3–5 | 6–10 | — |
| `hearth:civic/university` | `archetype/civic/University` (+ `Precinct`) | city, max 1 | 23–33 × 23–33 | 14–24 | SCHOLAR; 8 beds |
| `hearth:civic/observatory` | `archetype/civic/Observatory` | city, VERTICAL, max 1 | 11–13 × 11–13 | 16–26 | SCHOLAR |
| `hearth:work/alchemist_tower` | `archetype/work/AlchemistTower` | town+, VERTICAL, max 1 | 7–9 × 7–9 | 14–22 | SCHOLAR; 1 bed |

Components: `build/kit/learning/` — `Rite` (how a culture raises its religious houses: walls of the ranges, the arcade,
the roofs, the church's east end, windows and bell), `Sacra` (crosses, candles, lanterns, cypresses, offerings),
`Studies` (lecterns, desks, shelves of books and bottles, brewing stands, crucibles), `Instruments` (telescope,
armillary sphere, globe, sundial, quadrant).

## The quadrangle (`Precinct`)

The monastery, the convent and the university share one plan, the way the orders and the colleges built: **ranges of
two storeys round a court** (one storey where the plot is under 16 high), a church on the remaining side (a fourth range
at the university), the court ringed by a **covered walk on arcades** under a lean-to, and in the middle a **garth** of
grass with a cross of paths, beds of herbs and flowers in its quarters, a well with a little roof (a tree in one
quarter when it is large).

- **Layout.** The church (or the near range) on the left or right (`side`), its west front on the street; the gate
  range along the street with a **gate passage** lined up on the court's middle (arches through both walls, a gable
  over it on the street face carrying a bell-cote when the church has no room for its own bell); the side range away
  from the church; the back range against the church's chancel. A walled **herb garden** (rows of herbs, beehives at
  the back wall, a water butt, a gate from the street) runs beside the side range where the plot is wide. On a plot too
  shallow for a gate range (small convents) a wall with an arched gateway closes the court instead.
- **Ranges.** Walls in the rite's manner (`Rite.ranges`): stone below and the culture's own above in the timber lands
  and the Alps (so a Bohemian abbey is rendered stone under a framed upper storey), rendered or logged or of adobe
  elsewhere. Plain lights with sills and lintels (arched on a masonry ground storey where the culture arches, lattices
  where it screens), never boxes or shutters. **One roof over all the ranges**: a hip over each merged into one surface,
  so the outer corners are hipped and the inner ones valleys (curved hips in the karst, one deck behind a parapet with
  beam ends and corner pinnacles or stakes in the dry lands). Chimneys rise from the kitchen, the brewhouse and the
  infirmary.
- **Walk.** Arcades on the pier lines in the rite's arch (pointed, round, braced, bracketed or posted), a low wall in
  the bays, the middle bay of each side open to the garth; the walk paved; a lean-to rising from the arcade to the
  ranges' walls, its eave over the garth's edge (a flat deck in the dry lands). The walk is one or two wide by the
  court's size, and drops on the church's side when the court is narrow.
- **Rooms.** Each range is divided along its length into its archetype's rooms by weight, a partition with a door
  between each, a door from the walk into every room that has the court before it, doors from the gate passage into
  the rooms either side. The upper floor is reached by a straight flight along the outer wall of a ground room (its
  open side toward the court, where the rail goes); where no room is long enough, a ladder up through a hatch. Doors
  upstairs through the side ranges' end walls run the upper floors round the court. Every room is furnished for what
  it is behind a reach plan (`Rooms.Room.claim`), so nothing ever closes a door off, then lit to the rule
  (`Lighting.room`).

| room | furnished with |
|---|---|
| gate | the passage itself: paved, arched both ends, GATE and DOOR |
| porter's lodge | table and chair, chest, barrel |
| cellar, store | barrels (some stacked), a chest of grain and apples (STORAGE) |
| brewhouse | the BREWER's brewing stand, cauldrons, barrels, a chest of grain, sugar and bottles |
| refectory | a long table down the hall with benches either side (SEAT, TABLE), the reader's pulpit (LECTERN) |
| kitchen | `Rooms` kitchen (hearth, counters, stores) under the chimney |
| chapter house | stone benches round the walls facing in, the head's chair, the house's banner over it |
| scriptorium | desks with stools under the windows, the masters' lecterns (SCHOLAR), shelves, a chest of vellum and ink |
| dormitory | beds side by side, heads to the outer wall (two rows where the range is wide), a chest |
| infirmary | sickbeds (SICKBED), the healer's bench with a brewing stand (HEALER), a cauldron, a chest of herbs and linen |
| workroom | looms, desks, chests of wool and thread |
| parlour | `Rooms` living room |
| lecture hall | the master's lectern (SCHOLAR), benches in rows facing it with a gangway, shelves |
| library | shelves on every windowless wall, lecterns (SCHOLAR), carrels under the windows, reading tables, a chest |
| scholars' chambers | bed, desk and shelf by turns along the windows, a chest |

## The church (`PrecinctChurch`)

On the near side of the cloister, in the culture's rite (`Rite`): walls rendered between dressed quoins (temperate,
wisteria: the Bohemian church), whitewashed (alpine, mediterranean), of rubble and dressed stone (moorland, maritime),
logged (northern, tundra), of adobe (desert, mesa, steppe), boarded (bayou); a polygonal apse with lights of coloured
glass in its faces and buttresses at its angles (temperate, wisteria, alpine, mediterranean) or a square end; lancets of
coloured glass (pointed) or round-headed lights; buttresses on the open flank where the plot leaves a row; high lights
on the cloister side over the walk's roof; the chancel arch; a rose in the west gable. **Its height is chosen** among the
shapes the plot allows, scoring how far it rises over the ranges, a steep Gothic roof in the timber lands and a turret
on the ridge: with 20+ blocks a Bohemian abbey church gets a pitch-2 roof towering over its cloister, its bell then in a
cote on the gate's gable. A west tower (ChapelWestern's) where the rite raises one and the plot is tall and deep (not at
a convent), a bell wall over the front in the dry lands, a ridge turret or a bell-cote elsewhere; the karst's church is
a hall of the Buddha under a curved hip with its bell in a pavilion in the garth.

Inside: the people's pews (kneeling mats in the karst) either side of the runner, the brethren's choir stalls facing
each other before the chancel, the altar with candles and the priest's lectern (PRIEST, ALTAR), the font by the door,
banners between the windows, the door from the choir to the walk.

## What each one is

**Monastery.** The abbey: gate range (brewhouse, gate, porter's lodge, cellar; guest chambers and a store over), side
range (refectory, kitchen; the granary over), east range (chapter house, scriptorium; the dormitory over); the church;
the cloister; the herb garden on wide plots. On a low plot the ranges are single-storey and the dormitory takes the
east range's ground floor beside the scriptorium.

**Convent.** The same plan smaller and turned to the sick: gate range (parlour, gate, portress's lodge; workroom over),
side range (refectory, kitchen; store over), east range (infirmary, chapter; dormitory over), the physic garden; the
chapel never raises a tower. On a shallow plot a wall with a gateway closes the court and the parlour moves to the side
range; on a low one the infirmary and the dormitory share the east range.

**University.** A closed quadrangle: front range (the rector's parlour, the gate under a **tower** with a clock toward
the street, a belfry and a cap in the culture's manner, the porter's lodge; scholars' chambers either side of the tower
room upstairs), lecture halls in the side ranges (chambers over), the library hall filling the back range on both
floors; arcades on all four sides of the court, a sundial at the crossing of its paths, benches in the back walk.

**Shrine.** The wayside shrine in each culture's form: the Bohemian *boží muka* pillar or a niche chapel; the alpine
*Wegkreuz* or *Bildstock*; the roofed worship cross of the north; a Celtic high cross or a holy well; the *edicola*; the
*qubba* or the *capilla* with its bell wall; the Sahel stupa; the karst's gate and shrine house with stone lanterns; the
tropical spirit house. ALTAR before it, a bench where there is room, offerings of flowers and candles.

**Observatory.** On the rise: the astronomers' tower (square with dressed corners or eight-sided, a newel stair through
the library and the instrument rooms to the observing room: a round drum (on a square tower the drum stands on its
head, the corners left to the walk) under a smooth hemisphere of one sheet, lead (polished andesite) in the north, the
west and Bohemia, patinated copper in the mountains, on the coasts and in the tropics, laid to a sphere's section row by
row (`Shapes.domeRows`) with its slit to the sky and the telescope aimed through it; a gallery round it behind a
balustrade (piers only at its angles), armillary spheres, a sundial in the forecourt), or in the dry lands, the steppe and the karst the terrace observatory (a stepped
platform with the library and the instrument store inside, a terrace of instruments on top reached by the great
gnomon's stair, or by a stair to a pavilion in the karst). Params: `form` (`tower`, `terrace`).

**Alchemist's tower.** Seven square in the culture's best stone: the workshop with the athanor, the laboratory of
brewing stands, bottles and the alchemist's lectern, his study under the roof, the roof platform with a still and drying
herbs behind a parapet, the stair house capped in the culture's manner. Params: `stair_right`.

## Params

- `side` (monastery, convent): `left` or `right`, where the church stands (default by seed).
- `form` (shrine, observatory), `stair_right` (alchemist's tower), `variant` (all).

## Previews

    ./gradlew blueprintPreview -Parchetype=hearth:civic/monastery -Pstyle=all -Pseeds=1 -Pterrain=hills
    ./gradlew blueprintPreview -Parchetype=hearth:civic/convent -Pstyle=all -Pseeds=1 -Pterrain=slope
    ./gradlew blueprintPreview -Parchetype=hearth:civic/university -Pstyle=all -Pseeds=1 -Pterrain=flat

## Tests

`build/learning/LearningArchetypesTest`: the points each building promises in every culture and tier (job sites for
every job, ALTAR, LECTERN, BED, SICKBED, STORAGE, SEAT, GATE, VIEWPOINT, DOOR as each needs), the shared invariants
(bounds, floating, reach, chunks, order, the light rule) over the family at every extreme size on six terrains, at the
lowest height announced, and for every variant of every culture's style.
