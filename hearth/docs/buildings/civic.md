# Civic and commercial buildings

The public buildings of a settlement: where people trade, drink and sleep, keep their money, pray, govern, read,
learn, bathe and keep watch. Nine archetypes, each built in the manner of every culture's tradition, registered by
`build/archetype/CivicArchetypes.java`.

| id | class | for | size (frontage × depth) | height | jobs, beds |
|---|---|---|---|---|---|
| `hearth:commerce/market_hall` | `archetype/commerce/MarketHall` | town+, CENTRAL | 11–17 × 13–21 | 12–28 | MERCHANT |
| `hearth:commerce/inn` | `archetype/commerce/Inn` | village+ | 11–15 × 11–17 | 11–22 | INNKEEPER, 4–8 beds |
| `hearth:commerce/bank` | `archetype/commerce/Bank` | village+, CENTRAL | 9–13 × 9–13 | 9–16 | MINTER |
| `hearth:civic/chapel` | `archetype/civic/Chapel` (+ `Chapel*`) | village+, VERTICAL | 9–13 × 13–19 | 12–36 | PRIEST |
| `hearth:civic/town_hall` | `archetype/civic/TownHall` | town+, CENTRAL, max 1 | 13–19 × 13–19 | 14–36 | — |
| `hearth:civic/library` | `archetype/civic/Library` | town+ | 9–13 × 11–15 | 12–20 | SCHOLAR |
| `hearth:civic/school` | `archetype/civic/School` | town+ | 11–15 × 11–15 | 9–15 | — |
| `hearth:civic/bathhouse` | `archetype/civic/Bathhouse` | city | 11–15 × 11–17 | 8–16 | — |
| `hearth:civic/guardhouse` | `archetype/civic/Guardhouse` | village+ | 7–11 × 7–11 | 8–18 | GUARD, 2 beds |

Heights are what each building needs to stand at its best (a chapel's spire, a town hall's tower). Civic buildings
get their maximum from the planner; commerce gets less away from the core, so every one of them also fits its lowest
height: a roof that cannot fit pitched becomes flat behind a parapet, towers and cupolas shorten or drop.

## What each one is

**Market hall.** In the timber traditions (temperate, wisteria) a Bohemian market house: a lofty arcade of pointed
arches rendered with the walls (the arch rings in dressed stone) open on three sides, the framed hall set flush on it
(no jetty, so the arches read from the square), a tall framed gable turned to the square with the loft door under a
hoist beam and its lantern, a balcony for proclamations in towns, the bell cupola on the ridge. Elsewhere: an arcaded
ground floor open on three sides, its piers and arches in the culture's manner (round
stone arches, pointed ones, braced timber posts, bracketed eastern posts), market counters under it with a merchant's
place behind each; a back bay closed in for the stores and the stair; the hall upstairs with tables and benches under
the roof; a cupola on the ridge with a bell and, in towns of standing, clock faces (front and back). Flat-roofed
traditions put little domes on drums over the roof instead (the souk). Dormers light the deepest pitched roofs.

**Inn.** A coaching inn, the broadest front in the street: its range runs the whole width of the plot, two storeys over
a lofty ground floor (three on plots 13+ wide and 13+ deep where the height allows), the ground floor in stone wherever
the culture builds in timber, the top storey jettied over the street in timber towns with a five-wide cross gable over
the tap-room door (dormers instead on unjettied roofs), a stack at each gable end. Through one side runs the carriage
arch (voussoirs, keystone, guard stones at its foot, the yard's gates folded back inside, a lantern under the boarded
vault) to the stable yard; a side door from the passage into the tap room. The tap room shows itself to the street:
tall windows, a bay, the door under the inn's painted sign hung from a wrought bracket (two fence posts out from the
wall, a stair brace, the hanging sign on its chains; a wall hanging sign where the forecourt is one row), the name
painted along the front on a board of wall signs (one of 16, translatable), a settle and casks with a lantern, a barrel
table with stools in the forecourt's outer row; within, the bar with its kegs racked behind, the floor furnished to the walls (`Rooms.Type.DINING`: booths of a
table between two benches out from the long walls, a long table down the middle where there is room, tables backed
against the bar or the stair with a bench before each, settles along the rest), the
hearth, lanterns hung from the joists over the tables and the bar. Upstairs, on every floor, a corridor along the back
with a runner and lanterns, guest rooms along the street (a second flight beside the first on three-storey inns); the
gallery looks over the yard, and across it stands a gabled stable (stalls behind half doors, hay and water, a hay door
over the middle, a lantern). Alpine and karst inns carry a balcony across the upper front, the mediterranean a vine
pergola, the dry south a flat roof with a shaded terrace, latticed screens, a striped awning and a palm in the yard;
raised traditions (bayou, tropical) lift it on stilts with a stair down to the street and no carriage arch. Params:
`arch_right`, `name`, `storeys` (2–3).

**Bank.** A rusticated stone storey whatever the culture (or a stone base under a karst hall), barred windows, a
portico of columns and a pediment where the plot allows (a karst gate roof, an adobe crest), and on smaller plots an
aedicule (two columns and a pediment framing the door); a low hipped roof behind a cornice whatever the houses do (a
crested parapet, the beam ends in a row, latticed screens and a little dome on a drum in the dry south); the counter
across the banking hall with the minter's workstation at its end; the strongroom behind an iron door with buttons
(chests of coin, a block of gold); the press room with a blast furnace, an anvil and the coin press (a die under a
piston); a lectern for the ledgers. Two storeys where deep enough, else one tall hall.

**Chapel.** One per tradition:
- *Bohemian parish church* (temperate, wisteria; `ChapelBohemian`): stone whatever the houses are. White render (the
  style's own walls) between dressed quoins over a stone plinth, buttresses rendered with the church and finished with
  a dressed set-off and weathering, tall lancets of coloured glass, a steep Gothic roof (two blocks per cell where the
  height allows, one otherwise: pitches between terrace the roof in lips), gables in the render (no king post), a
  narrower chancel ending in a three-sided apse with a buttress in every notch of its octagon and a light in every face
  (a square east end on the shortest plots), a cross on the apse's hip. A town's church raises a west tower half-engaged
  in the nave (its foot the narthex, an arch through into the nave, the font in the aisle beside it), or a tower beside
  the nave's front on short wide plots: five square, rendered between corner stones, a pointed portal under a little
  gable, slit lights, clocks to the street (and over the roof behind), a belfry of paired lights and the Bohemian crown
  (a corbelled gallery with merlons and corner pinnacles round a slender needle spire); the ringer's ladder inside. A
  village's church carries a slender shingled turret on the ridge over its west gable, its portal and a round window in
  the gable. Lanterns from tie beams, banners between the windows, the churchyard behind a wall with a lychgate.
- *Western parish church* (moorland, maritime, alpine, tundra): nave of pews either side of an
  aisle, a narrower chancel with the altar, lancets of stained glass, tie beams with lanterns, a bell in a cote, a
  turret on the ridge, a front tower with a spire and a clock, or a side tower (tier and seed decide); buttresses on
  stone ones; a churchyard of headstones and a dark tree.
- *Stave church* (northern): staves, a svalgang (covered gallery) round the nave, stacked steep shingle roofs, carved
  ridge crests, a porch gable, a belfry turret, a chancel with a little apse spire.
- *Whitewashed chapel* (mediterranean): white nave under a low gable, a bell wall (espadaña) over the front, a
  sanctuary under a blue dome on a drum, a campanile in towns, a terrace with cypresses.
- *Adobe mission* (desert, mesa): thick adobe walls and buttresses, a frontispiece with an arched recess and a rose
  window, a pierced bell wall, a domed side tower in towns, a walled campo santo.
- *Round chapel* (steppe): a round drum under a cone, a clerestory in towns, a porch with a bell-cote.
- *Shrine-temple* (karst): a limestone terrace up steps through a gate, columns and lattice, a double curved roof
  (pent skirt round a raised clerestory, lifted tips), stone lanterns, incense burners on the terrace, a bell in a
  pavilion or under the porch beam.
- *Raised chapel* (bayou, tropical): the western plan lifted on stilts with boards, a gallery and a belfry.

**Town hall.** In the timber traditions a Bohemian town hall: the whole hall rendered over a stone plinth, a rendered
arcade of pointed arches (their rings in dressed stone) along the square with the town's banners on its piers, a corner
tower (`kit/civic/Bohemian`) whose foot is the arcade's corner bay (arches to the square and into the loggia) rising to
clocks, a belfry of paired lights and the gallery and needle spire, the chamber's windows framed in dressed stone, an
oriel on stepped corbels over the arcade, a gable roof along the square with a crow-stepped show gable over the middle
(each step capped in dressed stone, a pinnacle on its peak). Elsewhere: a loggia of arches
inside the front, a stone ground floor (the clerk's lectern and the records, benches
for those waiting, a runner to the stair), the council chamber above with its long
table, chairs and the meeting point, the clerk's lectern, a fireplace; a balcony over the loggia hung with the
settlement's banners; a tower (centre over the front, or at a corner in stone, mediterranean and adobe towns) with a
belfry, clocks and a cap in the culture's form (spire, pyramid, onion, dome, two-tier pagoda, cone, battlements); a
flagpole and the notice board by the door. In the dry south the hall has a crested parapet over the front, a shaded
terrace on its roof and latticed screens before the chamber's windows.

**Library.** One tall storey with a gallery round three sides (two wide: shelves and a walk, a rail of trapdoors) and
its stair; shelves and chiseled shelves floor to gallery on every wall; reading tables with chairs; lecterns at the
back for the scholars; a rose window over the door, two tiers of windows (lancets or lattice); a portico where the
plot is deep; a glazed lantern on the ridge (a dome on a drum of lights in the desert).

**School.** A lofty classroom with tall windows, desks facing a blackboard and the teacher's lectern, a porched door
(a colonnade along the front in the south), a bell in a cote, turret or bell wall, a fenced yard with a tree, a bench and
a bell on a post.

**Bathhouse.** The Roman *thermae* (most traditions): a hall of columns round a sunken pool on a mosaic floor, a hot
pool in the back with steam rising through grates (campfires beneath), benches, a clerestory lantern, a portico with
basins of water either side fed from carved heads in the wall. The
*hammam* (desert, mesa): an iwan front, a hot room under a dome with star-lights on a drum, little domes at the
corners, the marble platform, basins, a cold pool. The *onsen* (karst): a rock pool steaming in a walled garden of
stone lanterns behind a gate, a lattice bath hall under a curved roof. The *sauna* (northern): a log bath house with
tiers of benches round the stove, a changing room, a plunge pool outside.

**Guardhouse.** A stone ground floor with slit windows (iron bars), the guard room with the grindstone, chests and
racks of arms built into the side walls (swords, crossbow, shield; helmets and a chestplate; bow, arrows, spear),
a table; the barracks upstairs with beds; battlements on a walkable roof with a hatch stair (masonry and adobe) or the
culture's pitched roof with the alarm bell in a turret on its ridge and a sentry box by the door; the settlement's banner
over the door; a training yard with a dummy and a target on wider plots.

## Points of interest

Every building registers DOOR at the entrance. Beyond it (tested in `build/civic/CivicArchetypesTest` for every
culture):

| id | POIs |
|---|---|
| market_hall | COUNTER + MARKET_STALL at each counter, MERCHANT JOB_SITEs behind them, MEETING_POINT, SEAT, TABLE, STORAGE |
| inn | COUNTER + MARKET_STALL at the bar, INNKEEPER JOB_SITE behind it, HEARTH, SEAT, TABLE, BED, STORAGE, VIEWPOINT (terraces) |
| bank | COUNTER + MARKET_STALL, MINTER JOB_SITE at the counter's end, LECTERN, STORAGE |
| chapel | SEAT (pews), ALTAR before the altar, PRIEST JOB_SITE at the lectern, VIEWPOINT (terraces) |
| town_hall | MEETING_POINT in the council chamber, SEAT, TABLE, LECTERN (clerk), NOTICE_BOARD, VIEWPOINT (balcony) |
| library | SCHOLAR JOB_SITE + LECTERN at each lectern, SEAT, TABLE, STORAGE, VIEWPOINT (gallery) |
| school | LECTERN, SEAT (desks), MEETING_POINT (yard), TABLE |
| bathhouse | SEAT (benches), MEETING_POINT, STORAGE |
| guardhouse | GUARD_POST by the door (and on the roof), GUARD JOB_SITE, BED, STORAGE, SEAT |

**The notice board.** The town hall puts `hearth:notice_board` by its door when that block is registered (resolved at
build time through the block registry) and an oak sign on a fence ("Notices", translatable) when it is not; either way
it registers the NOTICE_BOARD POI on the cell in front, facing the board.

## Params (all optional; defaults are seeded)

| param | archetypes | meaning |
|---|---|---|
| `variant` | all | the style variant |
| `banner` | all with banners or flags | the settlement's arms: `"field;colour:pattern;colour:pattern…"`, e.g. `"blue;yellow:cross;white:border"`; default: seeded from the culture's cloth colours (so a settlement's buildings agree only if the planner passes it, see requests) |
| `tower` | chapel (boolean), town_hall (`"centre"`, `"corner"`, `"none"`) | a bell tower rather than a cote (chapel: default in towns and cities); where the town hall's tower stands |
| `graveyard` | chapel | a churchyard on the plot (default true where it fits) |
| `cupola` | market_hall | the bell and clock cupola on the ridge (default true) |
| `roof` | market_hall | a `RoofForm` name |
| `portico` | bank | columns and pediment on the street (default from tier 3 on plots 9+ wide, 10+ deep) |
| `arch_right` | inn | the carriage arch on the right (default seeded) |
| `name` | inn | which of the 16 names the sign shows |
| `yard_right` | school | the yard on the right |

## Traditions

`kit/civic/Tradition` groups the cultures by how they build public buildings: TIMBER (temperate, wisteria), LOG
(northern, tundra), ALPINE, STONE (moorland, maritime), MEDITERRANEAN, ADOBE (desert, mesa), STEPPE, KARST, STILT
(bayou, tropical). It decides arch kinds, tower caps, flat roofs, raised floors, chapel and bathhouse types.

## The civic kit (`build/kit/civic`)

| class | what it builds |
|---|---|
| `Arches` | arcades: piers, pad stones, round, pointed, braced, bracketed, posted and flat heads |
| `Towers` | shafts, belfries, bells, caps (spire, pyramid, onion, dome, pagoda, cone, battlements, hip), lathed domes, clocks, bell-cotes, bell walls, lookouts, flagpoles, finials; spires, domes, onions and every lathe course are laid from the octagon ladder of `kit/landmark/Shapes` (clean octagons drawn in a cell at a time, not rasterised circles), so a three- or five-wide dome is round-shouldered and an onion swells past its drum |
| `Signs` | wall banners, the settlement's arms, hanging and post signs with translatable lines, the notice board |
| `Grounds` | gentle-street checks, raised and stilt bases, forecourts, stair flights with footings, cuts into slopes |
| `Halls` | floors, portals, iwans, double doors, lychgates, string courses, cornices, hanging lanterns, lamps, roof terraces |
| `Sacred` | pews, altars, lancets, rose windows, buttresses, churchyards, candles |
| `Storeys` | ground floors, walls and windows per storey, roofs that fit the plot's height (with dormers), stone storeys |
| `Trade` | counters with the job's workstation, wares, bars with kegs, tables with chairs |
| `Portico` | stylobate, columns, entablature, pediment (karst gate roof, adobe crest) |
| `Baths` | pools, rock pools, steam grates, mosaics, benches |
| `Eaves` | pent roofs skirting a raised core, lifted eave tips |
| `Bohemian` | the Bohemian towers of the timber traditions' churches and halls: five square, render between corner stones, stage courses, slits, clocks, a belfry of paired lights, the corbelled gallery with corner pinnacles round a needle spire; the pointed portal under a little gable |
| `Adobe` | the dry south's flat roofs (raised corners and crest in the `wall_accent`, a frieze of the `accent` along the wall head, beam ends, spouts), latticed screens, awnings, domes on drums in the accent where it cuts stairs (else lime-washed: white domes over walls of the earth's colour, `domeSheet`, also used by the towers', library's and hammam's domes), palms |
| `Fam` | block families recut from a role's material (stairs, slabs, walls of the same stone) |

## Previews

```
./gradlew blueprintPreview -Parchetype=hearth:civic/chapel -Pstyle=all -Pseeds=1 -Pterrain=slope
./gradlew blueprintPreview -Parchetype=hearth:commerce/inn -Pstyle=temperate -Pseeds=3 -Pterrain="flat;tier=3"
./gradlew settlementPreview -Pculture=temperate -Pdays=400 -Pterrain=hills -Pseed=2
```

Terrain params: `tier`, `frontage`, `depth`, `facing`, any archetype param (`tower=corner`, `banner=...`).

## Known limits

- Bells, banners, signs and the notice board are block-entity rendered: the offline previews do not draw them.
- The tower and cupola heights adapt to `maxHeight`; at the lowest heights a tower may be left off entirely.
- Bank vault chests are not STORAGE POIs (the strongroom is not for the public); the press room's furnace is not a
  second job site.
