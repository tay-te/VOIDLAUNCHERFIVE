# Landscape and defence: the spaces between and the walls around

The family of `build/archetype/landscape/` and `build/archetype/defense/`, registered in
`build/archetype/LandscapeArchetypes.java` (the well, a phase-1 reference, stays registered in `Archetypes`). Their
reusable parts are in `build/kit/landscape/`. Read `BUILDING_GUIDE.md` first; this page says what each piece looks
like, what it reads and registers, and how it meets the planner.

| id | file | size (f × d) | height | tier | flags |
|---|---|---|---|---|---|
| `hearth:landscape/plaza` | `landscape/Plaza.java` | 7–31 × 7–31 | 1–10 | camp | CENTRAL |
| `hearth:landscape/well` | `landscape/Well.java` | 5–9 × 5–9 | 6–9 | camp | CENTRAL |
| `hearth:landscape/fountain` | `landscape/Fountain.java` | 5–9 × 5–9 | 4–8 | village | CENTRAL |
| `hearth:landscape/statue` | `landscape/Statue.java` | 5–7 × 5–7 | 7–14 | town | CENTRAL |
| `hearth:landscape/bell` | `landscape/Bell.java` | 3–5 × 3–5 | 5–9 | camp, max 1 | CENTRAL |
| `hearth:landscape/lamp_post` | `landscape/LampPost.java` | 3–5 × 3–5 | 4–7 | village | CENTRAL |
| `hearth:landscape/garden` | `landscape/GardenPlot.java` | 7–15 × 7–15 | 1–9 | hamlet | |
| `hearth:landscape/park` | `landscape/Park.java` | 13–25 × 13–25 | 1–12 | town | |
| `hearth:defense/palisade` | `defense/Palisade.java` | linear (1 wide) | 5–8 | hamlet | OUTSKIRTS, upgrades to wall |
| `hearth:defense/wall` | `defense/TownWall.java` | linear (3 wide) | 8–14 | town | OUTSKIRTS |
| `hearth:defense/gatehouse` | `defense/Gatehouse.java` | 13–15 × 7–9 | 14–22 | town | VERTICAL; job GUARD |
| `hearth:defense/watchtower` | `defense/Watchtower.java` | 7–9 × 7–9 | 14–22 | village | VERTICAL, OUTSKIRTS; job GUARD |

## The pieces

**Plaza.** The whole plot levelled (water embanked) and paved in the culture's pattern (`Paving`): square rings or
circles round a medallion, a grid of bands with inlays at the crossings, a checker, rays, staggered setts, plain setts
with mended patches, boards on log sleepers in timber cultures; a Bohemian square in plain cobble setts of one stone,
the medallion its only pattern and no dotted border. A kerb of the band stone round the edge; a frame of it
round everything kept. What stands in the middle (the planner's well or bell, in `keep`) gets benches on its sides and
back looking at it, the notice board at a front corner and the meeting point before it; where nothing stands there
(junction squares, a plaza drawn alone) the plaza raises its own centrepiece: a tree in a raised planter ringed by
benches; a fire pit with log seats (northern, tundra, steppe; a HEARTH POI); a little tiled basin with a jet in a
cauldron (desert, mediterranean, mesa). The middle is the one thing in the open: lamps at the four corners, and on big
plazas that are not the market a pair of shade trees in planters at the back quarters (never a grove filling the
middle). On the heart plaza the two edge rows (where the doors round the square open) and the ring
two to five in from the edge (where the planner stands stalls, lamps, a fountain and a statue) are paved but left
clear. The notice board is the quest module's `hearth:notice_board`, resolved by id at draw time, or a lettered sign
on a fence post where that block is absent (`NoticeBoards`).

**Well.** A stone curb round a deep shaft on a paved square, and the culture's well head (`head`): a windlass with its
crank under a little roof (gable, hip, half-hip, curved karst roof); a well sweep in the north and on the steppe (a tall
post, a long pole with a stone counterweight); a wrought-iron arch with a lantern on its crown (mediterranean); a domed
kiosk on arched piers with a finial of its own stone (desert, mesa). The rope (a chain) hangs down the shaft and the
bucket stands full on the paving by the curb, where a hand can take it up. Benches, a trough, pots and corner lamps
round it.

**Fountain.** Built on top of the paving, never dug into it, so one set into a paved plaza keeps the plaza's floor as
its basin's bed: a basin whose rim is a ring of stone seats, a column carrying a tazza over the water (three wide even in
a five-wide basin, where lanterns stand on the rim's corner stones) and a stone finial over the jet (a second, wider bowl on
7+ plots); all stone, capitals and inlay in the paving's carved stone, never glazed tile or a block of water in the air
(temperate, wisteria, maritime, alpine, moorland; round on the
steppe and in the vale); a low octagon, its rim inlaid every other stone in a second stone, with a short stone spout and pots (desert, mediterranean,
mesa); a spring pool with a mossy (or log) edge, lilies, a bamboo spout into a stone bowl and a stone lantern (karst,
tropical, bayou); a stone trough with a timber pump and a bucket (northern, tundra).

**Statue.** A stepped plinth (a step all round, a base moulding, on plots 13+ high a die of two courses, a cornice
overhanging it) and a lettered sign whose lines are translatable components with English fallbacks (`Inscriptions`). On
it: a figure some eight blocks tall in the round, two deep and three across at the chest so it reads as a person from
any corner, its arms standing off the torso (legs apart or a robe to the ground with its hem sloping, sloping shoulders,
a neck, the head a block clear of the shoulders with a cap and its back rounded): the founder with a raised sword and a
cloak falling behind, a keeper holding up a lantern, an elder with a staff, a scholar with an open book; in polished
stone, white marble (mediterranean, wisteria), green bronze (temperate, maritime, alpine towns), carved cypress (bayou);
an obelisk on a flared foot with a recessed band and a pyramidion of its own stone (desert, mesa); a stele with a carved
panel (steppe); a rune stone two wide with a rounded shoulder, a band of knotwork and runes in relief (northern,
tundra); a seated sage in jade (karst); a tiki (tropical). A low monument stands on a step of slabs.

**Bell.** On a small plot a bell frame in the culture's manner, its rope a chain: two posts on stone feet under a beam
(braced on a five-wide plot) and a little shingled gable (temperate, alpine, moorland, maritime, bayou); an A-frame of
raking logs under a turf cap (northern, tundra); a masonry arch with a stepped crest (desert, mesa, steppe); red posts
under a curved roof turned up at its ends (karst, wisteria); a bell gable, a rendered wall with the bell in an arched
opening under a tiled pediment (mediterranean); bamboo posts under a thatched cap (tropical). On a plot five by five or
more a pavilion (`Pavilions`): a podium with steps, four posts (arched masonry piers in the
desert), the bell hung from a beam, a roof in the culture's form (curved in the karst, a stepped cupola in the desert),
lanterns under the eaves, benches.

**Lamp post.** A street light on a little paved island (`Lamps`): a timber crook on a stone foot (a double standard in
towns), a karst stone lantern, a fire basket on a pillar (north, steppe), a tiki torch, a lantern on a masonry pillar
(south), a bayou lantern on a chain, a lantern under a little roof (alpine, wisteria); flowers, pots or a clipped bush in
the island's corners, seats back to back against the post.

**Garden.** Terraced where the ground rises or falls behind the street (`Terraces`: retaining walls with a coping, a
parapet where the wall stands three or more, steps on the path). Cottage (fence or hedge, a path to an arbour with a bench, a cross path, borders, a fruit tree, a
beehive, a bird bath); kitchen (rows of crops with water channels, a scarecrow, compost, water butt, an olive or fruit
tree); riad (walled with a coping and an arched gate, tiled walks quartering it round a little fountain, palms, a dry
garden, stone benches); cactus garden under a ramada (mesa); zen (raked gravel, moss islands, weathered stones, a pool,
a blossom tree, a stone lantern, a viewing deck); tropical; rock (dry-stone walled, heather, boulders, a birch); bog
(pools, mud, a boardwalk, a willow hung with moss); winter garden (spruces, a fire bowl with log seats).

**Park.** Terraced like the garden. A gate on the street and openings half way down the sides, a broad walk up the middle
(steps where it climbs a terrace), a cross walk on each terrace, a loop walk on level ground, a pond with lilies and
reeds in one quarter, the culture's trees standing one or two to a lawn in the others, five apart, some lawns left open
with flowers (`Trees`: broadleaf, conifer with its leader of leaves over the last whorl, birch, palm, blossom, olive,
acacia, willow with hanging moss; crowns of several lumps, pruned so nothing floats), borders by the gate,
benches and lamps along the walk, and at city tier a bandstand (a pavilion) where the walks meet.

**Palisade.** One stake thick along the planner's line, stakes of the local tree a block into the ground, set in panels
of three to five whose tops step down a slope panel by panel (`TownWall.crest`), ragged heights,
many sharpened, a heavier post every eight or so, tall gate posts with lanterns where the stretch ends.

**Town wall.** Three thick along the line (`Corridor` reads the planner's polyline by the dense-centreline and corridor
rules of PLANNING.md §7). Footings; a darker plinth course; the field face crenellated, and on straight runs of stone
walls pilasters every six blocks with the face between them set back under a corbel table and a sloping talus at the
foot; a wall walk along the middle at `height − 2`, stepping with the ground a block at a time (raised, never lowered,
where the land drops); the coping (field parapet and merlons, town parapet) steps in runs of three to five, level along
each run at its highest walk step and broken at half bays, so a sloping wall's crest steps like a mason's and not
column by column into a saw-tooth (`TownWall.crest`; a run's walk never sits more than two below its coping); a low parapet on the town side with lanterns; flights of steps cut into the town face near the
ends (a niche at the foot), two on long stretches; on straight runs a blind arcade of round-headed niches along the
town face, a lantern hung in every other one; square piers closing the walk where the stretch ends, unless it runs
on (`join_*`, or into a `keep` rectangle). Bayou and tropical walls are timber ramparts (two rows of logs, earth
between, a boarded walk, a railing, sharpened tops).

**Gatehouse.** Over the road (local z runs from the field in front to the town behind): round-shouldered arches front
and back, a raised portcullis in the front arch, gate leaves folded back, a lantern in the vault, a corbelled
crenellated band over the front arch (one row out over the road) and the town's banner (`Towers.banner`: the culture's
cloth colours, the same device on every banner of the culture). The gallery over the passage is the wall walk's level,
with the portcullis windlass on it. The left tower holds a guard room and the switchback stair to the gallery and rises
two higher under a saddle roof (gables to the flanks; a curved roof in the karst; battlements in the south); the right
tower holds the store room with the guards' grindstone and chest and a stair on up to its battlemented platform. With
`over_road`, doorways in both flanks at the walk let the wall walk in.

**Watchtower.** Standing alone: a square shaft on a battered skirt with clasping buttresses at its corners, string
courses, a door with its lamp, slits climbing with the spiral stair (on 9-wide plots a guard room and floors, the
stair in a corner), the banner, a platform oversailing on a corbel ring, and a lookout under a pyramid roof on posts set
in a block from the parapet (a curved roof in the karst) or open battlements (desert, mesa, mediterranean, some
temperate towers). On the wall (`on_wall`): a square bastion astride the line, solid to the walk; the walk enters
through doorways in both flanks and climbs two steps into a guard chamber above the wall top, so the tower and a wall
stretch built through it agree block for block, whichever is raised first; a stair along the chamber's back leads to
the top.

## Params

| archetype | params |
|---|---|
| plaza | `heart` (bool), `junction` (bool), `keep` (int array of world rects) |
| well | `head` (`windlass`, `sweep`, `arch`, `kiosk`) |
| fountain | `shape` (`square`, `octagon`, `round`) |
| statue | `figure` (`founder`, `keeper`, `elder`, `scholar`) |
| lamp_post | `benches` (bool) |
| garden | `kind` (`cottage`, `kitchen`, `riad`, `cactus`, `zen`, `tropical`, `rock`, `bog`, `winter`) |
| park | `bandstand` (bool, default city tier), `pond` (bool) |
| palisade, wall | `points`, `width`, `height` (PLANNING.md §7); `keep`, `join_start`, `join_end`, `heart`; wall: `corbels` (bool) |
| gatehouse | `over_road`, `passage_width`, `wall_height`, `roof`, `street_y` |
| watchtower | `on_wall`, `wall_height`, `roof` (`open`, `pyramid`, `pagoda`) |

All default to seeded, culture-appropriate choices. Every archetype also takes the shared `variant`.

## Points of interest

`SEAT` on every bench (the stair block), `MEETING_POINT` before the middle of a plaza and at the well, bell, fountain,
statue and bandstand, `NOTICE_BOARD` in front of the plaza's board, `HEARTH` at fire pits, `GUARD_POST` on wall walks,
tower tops and the gatehouse, `VIEWPOINT` on towers, walks, arbours and pond benches, `GATE` in the gatehouse passage,
`JOB_SITE` GUARD at the grindstones, `STORAGE` at the chests, `DOOR` at doors. `LandscapeTest` walks to each of them
from the entrances in every culture on flat, slope and hills.

**Guards (`Edges`).** Every piece here (and every commerce building) ends with `Edges.guard(d)`: where a walked cell
stands beside a drop of three or more (a terrace over falling ground, a plinth on a slope, the back of a stall), a low
wall of the masonry on open ground or a rail of the railing on a deck guards it, never across a door, a step, a stair,
a ladder head or a POI and never cutting the way to one; on the goods of a stall at the plot's edge a jug instead.

## How the pieces meet the planner

- **One dressing for a town's defences.** Wall stretches, palisades, towers and gatehouses take their materials from
  the style's base dressing chosen by the culture, not by each plot's seed (`Manner.fort`, through the small shared
  addition `Draft.mat(Role, variant, seed)`), so a ring of eight stretches never comes out half whitewashed and half
  sandstone. Roofs of towers still follow each plot's variant.

- **Plaza furniture.** The well (and later stalls, lamps, fountain, statue) are separate plots inside the plaza; the
  plaza leaves the `keep` rectangles untouched (it removes its own drawing there) and keeps its tall pieces out of the
  ring where the planner adds furniture later. Furniture archetypes build on top of the paving, never below it.
- **Walls, towers, gates.** Stretches leave `keep` rectangles alone and their walks open into them; the on-wall tower is
  designed so a stretch built through it (no `keep`) still agrees with it below the chamber. Gatehouses centre their
  passage on the plot (odd frontages) for the road the planner runs through it. See `docs/requests/arch-landscape.md`
  for what the planner should pass.

## Previews

```
cd hearth
JAVA_HOME=C:/Users/bitto/VOID/.tools/jdk-25 ./gradlew blueprintPreview -Parchetype=landscape/plaza -Pstyle=all -Pseeds=2 "-Pterrain=hills;frontage=11;depth=11"
./gradlew blueprintPreview -Parchetype=defense/wall,palisade -Pstyle=all -Pseeds=2 -Pterrain=hills
./gradlew blueprintPreview -Parchetype=watchtower -Pstyle=all -Pseeds=2 "-Pterrain=flat;on_wall=true;frontage=7;depth=7"
./gradlew blueprintPreview -Parchetype=gatehouse -Pstyle=all -Pseeds=2 "-Pterrain=slope;over_road=true"
./gradlew blueprintPreview -Parchetype=landscape/park -Pstyle=all -Pseeds=2 "-Pterrain=slope;tier=4"
./gradlew settlementPreview -Pculture=temperate/walled -Pdays=400 -Pterrain=hills -Pseed=2
```

Walls drawn alone (no `points`) run down the plot from back to front, sometimes with a bend. An on-wall tower drawn
alone reports its entrance as unreachable from the street: it is entered from the wall walk.
