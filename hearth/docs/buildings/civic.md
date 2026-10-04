# Civic and commercial buildings

The public buildings of a settlement: where people trade, drink and sleep, keep their money, pray, govern, read,
learn, bathe and keep watch. Nine archetypes, each built in the manner of every culture's tradition, registered by
`build/archetype/CivicArchetypes.java`.

| id | class | for | size (frontage × depth) | height | jobs, beds |
|---|---|---|---|---|---|
| `hearth:commerce/market_hall` | `archetype/commerce/MarketHall` | town+, CENTRAL | 11–17 × 13–21 | 12–24 | MERCHANT |
| `hearth:commerce/inn` | `archetype/commerce/Inn` | village+ | 11–15 × 11–15 | 11–17 | INNKEEPER, 4 beds |
| `hearth:commerce/bank` | `archetype/commerce/Bank` | village+, CENTRAL | 9–13 × 9–13 | 9–16 | MINTER |
| `hearth:civic/chapel` | `archetype/civic/Chapel` (+ `Chapel*`) | village+, VERTICAL | 9–13 × 13–19 | 12–30 | PRIEST |
| `hearth:civic/town_hall` | `archetype/civic/TownHall` | town+, CENTRAL, max 1 | 13–19 × 13–19 | 14–32 | — |
| `hearth:civic/library` | `archetype/civic/Library` | town+ | 9–13 × 11–15 | 12–20 | SCHOLAR |
| `hearth:civic/school` | `archetype/civic/School` | town+ | 11–15 × 11–15 | 9–15 | — |
| `hearth:civic/bathhouse` | `archetype/civic/Bathhouse` | city | 11–15 × 11–17 | 8–16 | — |
| `hearth:civic/guardhouse` | `archetype/civic/Guardhouse` | village+ | 7–11 × 7–11 | 8–18 | GUARD, 2 beds |

Heights are what each building needs to stand at its best (a chapel's spire, a town hall's tower). Civic buildings
get their maximum from the planner; commerce gets less away from the core, so every one of them also fits its lowest
height: a roof that cannot fit pitched becomes flat behind a parapet, towers and cupolas shorten or drop.

## What each one is

**Market hall.** An arcaded ground floor open on three sides, its piers and arches in the culture's manner (round
stone arches, pointed ones, braced timber posts, bracketed eastern posts), market counters under it with a merchant's
place behind each; a back bay closed in for the stores and the stair; the hall upstairs with tables and benches under
the roof; a cupola on the ridge with a bell and, in towns of standing, clock faces (front and back). Flat-roofed
traditions put little domes on drums over the roof instead (the souk). Dormers light the deepest pitched roofs.

**Inn.** A coaching inn: a stone carriage arch (voussoirs and keystone) through one side of the ground floor to the
stable yard behind, a side door from the passage into the tap room; the tap room with its bar (kegs racked behind the
innkeeper), tables and chairs, the hearth and its chimney; the stair up to a corridor along the back and guest rooms
along the street with beds and chests; a settle and casks of ale with a lantern by the door; the hanging sign over
the door with one of 16 names (translatable: "The Red Lion", "The Wheatsheaf"...); a bay window to the street;
balconies (alpine, karst, raised), a vine pergola (mediterranean), a roof terrace, latticed screens, a striped awning
and a palm in the yard (desert, mesa); behind, the stable with stalls and hay and a gallery along the yard. Raised
traditions (bayou, tropical) lift it on stilts with a stair down to the street and no carriage arch.

**Bank.** A rusticated stone storey whatever the culture (or a stone base under a karst hall), barred windows, a
portico of columns and a pediment where the plot allows (a karst gate roof, an adobe crest), and on smaller plots an
aedicule (two columns and a pediment framing the door); a low hipped roof behind a cornice whatever the houses do (a
crested parapet, the beam ends in a row, latticed screens and a little dome on a drum in the dry south); the counter
across the banking hall with the minter's workstation at its end; the strongroom behind an iron door with buttons
(chests of coin, a block of gold); the press room with a blast furnace, an anvil and the coin press (a die under a
piston); a lectern for the ledgers. Two storeys where deep enough, else one tall hall.

**Chapel.** One per tradition:
- *Western parish church* (temperate, wisteria, moorland, maritime, alpine, tundra): nave of pews either side of an
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

**Town hall.** A loggia of arches inside the front, a stone ground floor (the clerk's lectern and the records, benches
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
| `Towers` | shafts, belfries, bells, caps (spire, pyramid, onion, dome, pagoda, cone, battlements, hip), lathed domes, clocks, bell-cotes, bell walls, lookouts, flagpoles, finials |
| `Signs` | wall banners, the settlement's arms, hanging and post signs with translatable lines, the notice board |
| `Grounds` | gentle-street checks, raised and stilt bases, forecourts, stair flights with footings, cuts into slopes |
| `Halls` | floors, portals, iwans, double doors, lychgates, string courses, cornices, hanging lanterns, lamps, roof terraces |
| `Sacred` | pews, altars, lancets, rose windows, buttresses, churchyards, candles |
| `Storeys` | ground floors, walls and windows per storey, roofs that fit the plot's height (with dormers), stone storeys |
| `Trade` | counters with the job's workstation, wares, bars with kegs, tables with chairs |
| `Portico` | stylobate, columns, entablature, pediment (karst gate roof, adobe crest) |
| `Baths` | pools, rock pools, steam grates, mosaics, benches |
| `Eaves` | pent roofs skirting a raised core, lifted eave tips |
| `Adobe` | the dry south's flat roofs (raised corners, crests, beam ends, spouts), latticed screens, awnings, domes on drums, palms |
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
