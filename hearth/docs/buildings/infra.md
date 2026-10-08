# Infrastructure: roads, stair streets, bridges, terraces, walkways, docks

The bones of every settlement: `hearth:infra/*`, built exactly over what the planner reserves (PLANNING.md §7 is
binding). Code: `build/archetype/infra/` (the six archetypes, registered in `archetype/InfrastructureArchetypes`) and
`build/kit/infra/` (the kit they share). Tests: `src/test/java/dev/voidmc/hearth/build/infra/`.

## The contract, and how the kit keeps it

| piece | what it does |
|---|---|
| `Course` | The planner's polyline rules reproduced in world coordinates: the dense centreline (`r(v) = floor(v + ½)`) and the corridor (distance < `(w + 0.5) / 2`). Every corridor column is described once: its nearest dense step (whose walking level it takes, as the planner's maps do), its signed offset (+ right of travel), its chainage, whether it is in an end cap, and its `ring` (0 on the side edge, 1 next to it...). `CourseTest` checks it against `plan.road.Polyline` on 400 random polylines. |
| `Lay` | Draws by world position and world direction on a `Draft` (positions through `LocalFrame`, directions to local labels). Infrastructure params are world coordinates, and rounding is not symmetric under the local frame's reflection, so geometry is never computed locally. |
| `LineMat` | A style role mixed in **world** coordinates with a seed that belongs to the culture, always in the base dressing: a long road is several plots issued months apart, and its paving runs on across the joints without a seam, its lamps are of one make, and no plot comes out in another variant's kerbs. `weathered(x)` fixes the moss bias (walked surfaces stay clean), `top(n)` keeps the n heaviest materials (one stone for every step of a flight). |
| `Dress` | How a road is surfaced per culture and kind: body, wear patches, edge (kerb, laid logs, grass verge), the stone its steps are cut from, a sunken carriageway or kennel, lamps. Kerbs always contrast with the course they edge. |
| `Roadbed` | Lays a road along a `Course`: levels, risers (a lone rise is a half step of slabs, a run of rises a flight of stairs), cut and fill (fill faced in stone where it shows), the surface, details (lamps, drains, verges, milestones, snow), headroom. |
| `Spans` | A bridge's deck profile (ends on the banks, rising as slabs or stairs, humped over arches, sagging on ropes) and every make's structure. |
| `Lamps` | Street lamps one column wide, four high at most: a timber crook with a hung lantern, a stone pillar, a tōrō. |
| `Demo` | Default geometry when `points` is missing (tests, previews, `/hearth build`): a course across the plot from the back to the front, graded as the planner grades. |

Every infrastructure blueprint places steps **only on its corridor's columns** (the walkway: not in its end caps,
which reach into the two facades), checked by `LinearWorksTest` over real-planner-shaped specs in every culture.

### Composing with neighbours (junctions, doors)

The sim's writer lets a later plot replace only natural ground, air, plants and its own blocks: where two road plots
overlap, the first one built keeps its paving. The roads are drawn so that this composes:

- **Edges are flush.** Kerbs, laid logs and verges are in the surface course, so a lane crossing a street's kerb finds a
  threshold, not a wall.
- **Nothing raised stands on a side edge** (the edge column is every fronting house's doorstep, PLANNING.md §4): lamps,
  milestones and drains stand on the *kerb line* (ring 1) of roads five or more wide; lanes, alleys and paths carry
  nothing above their surface. Bridges (no frontage over water) carry parapets; docks (not access roads) carry posts and
  racks; stair streets carry walls and parapets only where the land beside stands or falls three or more.
- **End zones.** Within two steps of either end (where a lane runs into the road it joins, and where consecutive plots
  of one road meet) a road keeps its plain body: no kerbs or sunken carriageway; lamps keep three steps away.
- **Houses meet the graded road.** A building's `Site` now reads the street in front at the planner's `street_y` (the
  road's walking level at its door), not at the natural ground the road was cut or filled from, so steps and paths from
  the door land on the road (shared edit, see the report).

## The archetypes

### `hearth:infra/road` — `Road`
Params: `points` (world centreline at walking level), `width` (odd), `kind` (`street`, `lane`, `alley`, `path`,
`highway`). Info: 1–56 × 1–56, height 1–24, camp+.

- **street** (5): setts (the foundation's cobble where it is cobbled, else the paving) with dressed kerbs of a
  contrasting stone (the plinth's grey stone in the Mediterranean, not white quartz that reads as painted lines down a
  hillside; the accent in the steppe; laid logs in the north); on level stretches the
  carriageway lies half a block below the kerbs; worn patches in a village; lamps on the kerb line alternating sides,
  every 20 blocks on a street laid at a camp's founding, 14 in a village or town, 10 in a city; iron grates over sumps in the gutters of a town's stone streets. Timber cultures
  (bayou, tropical): a plank boardwalk with log edges.
- **lane** (3): a cart track in a hamlet (two worn ruts, grass between), packed earth with a little rubble trodden in
  in a village, paved and kerbed in a city (review 2 item 16: the hierarchy reads from afar); rubble in the stone towns
  (mediterranean, moorland, maritime, desert, mesa); laid-log edges in the north ("mud and wood"), bamboo edges in the
  karst; planks on the bayou.
- **alley** (3): rubble setts with a kennel down the middle.
- **path**: one wide to a door; three wide a farm track (wheel ruts, a grass crown with tufts).
- **highway** (5): a metalled country road (packed path with cobbles) between grass verges with tufts and flowers,
  milestones every 48 blocks, lamps near a city.

Earthwork: fill that shows beside the road is faced in the foundation stone (any bank in a town, over two blocks in the
country); columns in water are built up in masonry (a quay where a road runs along a river).

### `hearth:infra/stairs` — `StairStreet`
Params: `points` (|Δy| ≤ 1 per step), `width`. Treads in the paving (or cobble below tier 2; planks in the timber
cultures), cheeks of dressed stone up the edges, landings where the profile is level with an inlaid accent stone in the
middle; retaining walls (coped, with potted flowers and the odd lantern) where the hill stands three or more over the
treads; a parapet over a drop of three or more; lamps on the landings of wide flights.
A climb up a cliff town's face (the planner's `CliffFace`) passes `cap` (its retaining walls rise at most that far
over the treads, since the courses of dwellings cut in beside it are its walls higher up) and `gaps` (x,y,z side columns
left open from y to y + 2: landings onto each course's terrace).

### `hearth:infra/bridge` — `Bridge`
Params: `points` (first and last on the banks at the approach roads' levels, the rest at `deck_y`), `width`,
`deck_y`, `water_y`, `kind` (`span` | `boardwalk`), `road_kind`, `make` (force: `arch`, `beam`, `trestle`, `covered`,
`rope`, `clapper`, `piers`). The deck's ends sit exactly on the banks' levels; it rises from them on its own ends (slabs,
or stairs where slabs cannot reach) and may hump above `deck_y` over arches. A VIEWPOINT mid-deck.

| make | where | what |
|---|---|---|
| arch | stone cultures; karst and wisteria (a moon bridge over 9 or less) | the deck first (humped), then segmental arches fitted under it (inset from the banks until they rise a quarter of their span; the abutments run out into the water to meet them), one arch to 13, several on two-wide piers beyond; voussoirs of a contrasting stone round each arch and under it (smoothed with upside-down stairs), impost bands on the piers, coped parapets with newels over the piers, lanterns at the ends. |
| piers | stone cultures on a slant or a bend (an arch would come out jagged) | stone piers the width of the deck under a timber deck, dressed newels. |
| beam / trestle | timber cultures, tier 0, the north | bents of posts to the bed every 3 (4) with caps and knee braces, girts across tall bents, stringers, a plank deck, a railing with posts at the bents; trapdoor rails on a three-wide bridge (the whole width stays walkable). |
| covered | alpine, northern, temperate (tier ≤ 2), wisteria, karst; straight and five wide | a sill beam, posts every three, walls in the culture's manner (plaster over a board rail in a timber frame, logs laid along in the north), an arched window band with potted flowers, a gable roof with eaves in the trim, gable boards over both portals, lanterns hung from the ridge, stone abutments. |
| rope | over a gorge (deck nine or more over the water or ground) | log towers with a lintel and a lantern at each bank, a slab deck on cross beams sagging a block or two, hand ropes from the tower tops down to rail height. |
| clapper | moorland, maritime, steppe; short and low | rough piers every three carrying flat slabs. |
| boardwalk | `kind=boardwalk` | planks a block over the water on piles and stringers, a cross board of darker timber every fourth step, open at the edges (stilt houses front it). |

### `hearth:infra/terrace` — `Terrace`
Params: `level`, `retain_north/south/east/west` (world sides; default: the planner's rule from the land),
`depth`. The whole footprint cut and filled to the deck and topped with the culture's ground (natural blocks, which the
building replaces freely with its own floor and yard). Retaining walls: footings, a weathered face, pilasters every four
and at the corners, a plinth course, a dressed coping, weep holes; walls of five or more get blind arches between the
pilasters with ivy hanging in them and here and there a stone bench in the niche (a SEAT). The street side keeps its top
three courses in earth so the building's own steps can be cut in. A side the planner does not retain is still faced
wherever its fill stands two or more over the land beside it (no raw earth cliffs at the platform's ends). Nothing is
drawn at or above the deck. Buildings on a
terrace (`terraced: true`) see their footprint's ground at their floor (shared edit) and stand on the deck.

### `hearth:infra/walkway` — `Walkway`
Params: `points` (two ends just outside the two facades, at deck level), `width` (3), `deck_y`, `street_y`. It rests
on the facades and on posts to the ground at both ends and every fourth step along both edges (stone piers under the
stone passage, the frame's posts under timber, piles under the planks), so it never hangs in the air on its own when a
house it meets is not built (art review 2 item 5); nothing else stands in the street (`LinearWorksTest`).
Stone cultures: an enclosed passage on a flying arch with small windows and a coped flat roof. Timber: a gallery on
beams with knee braces against the facades, the culture's infill, a glazed band, a gable roof. Karst and wisteria: an
open roofed pavilion walk. Bayou and tropical: an open plank walk with rope rails. Lanterns under it and inside. A
VIEWPOINT mid-span.

### `hearth:infra/dock` — `Dock`
Params: `points` (land column by the road at road level, out over the water), `width` (3). Info flags: WATERFRONT.
Steps down from the road to a deck a block over the water ending in a landing stage flush with it (a DOCK point).
Timber pier: a pile's head left standing through the deck as a fence post every sixth step, the sides in turn (the walk
down the middle stays clear), lanterns on the end posts, a fish rack hung with nets,
barrels and a chest of gear (STORAGE), a jib crane over the landing stage in a town. Stone quay (maritime,
mediterranean, desert, mesa towns): masonry from the bed, dressed coping, stone bollards, steps into the water, a street
lamp. Without `points` (tests, previews) the course runs from the plot's dry side out over its wet one, so the demo on
`coast` and `river` is measured over water.

## Stages and goods

CLEAR (headroom, trees) and EARTHWORK (cut and fill) first; FOUNDATION (faced banks, quays, piers, abutments, retaining
walls); FRAME (bents, beams, towers); WALLS (arch rings and spandrels, covered sides); FLOORS (the surface course, decks,
treads); ROOF; EXTERIOR (lamps, parapets, railings, plants, details). A 48-block street is about 240 stone in its
FLOORS stage.

## Previews

```
cd hearth
# one archetype, the planner's default geometry for a plot (frontage, depth, facing, tier and params after semicolons)
JAVA_HOME=C:/Users/bitto/VOID/.tools/jdk-25 ./gradlew blueprintPreview -Parchetype=infra/bridge -Pstyle=all -Pseeds=1 "-Pterrain=river;frontage=5;depth=32;facing=east;width=5"
# larger shots with low side elevations (where arches and retaining walls show)
./gradlew -I tools/infra/preview.gradle infraShots -Parchetype=infra/bridge -Pstyle=temperate,karst "-Pterrain=river;depth=32;facing=east;width=5;make=arch" -Pscale=24
# a town grown by the real planner, textured close-ups round its infrastructure (focus: heart, road, bridge, terrace,
# stairs, dock, walkway, all, or one plot as bridge#8; -Psolo=true draws the infrastructure alone)
./gradlew -I tools/infra/preview.gradle infraCloseups -Pculture=temperate -Pterrain=river -Pseed=5 -Pdays=300 -Pfocus=bridge -Pwindow=36 -Pscale=24
./gradlew -I tools/infra/preview.gradle infraCloseups -Pculture=alpine/mountain -Pterrain=steep -Pseed=2 -Pdays=250 -Pfocus=terrace
./gradlew -I tools/infra/preview.gradle infraCloseups -Pculture=temperate "-Pterrain=earth:0:0" -Pseed=8675309 -Pdays=250 -Pfocus=all
```

`infraCloseups` applies the plots in id order with the sim writer's first-built-wins rule, so junctions look as they
will in game.

## Known limits

- Walkways never appeared in the offline towns (they need an Expanse signature or a dense city core); they are checked
  with planner-shaped specs in `LinearWorksTest` and seen standing alone in `infraShots`.
- Diagonal stair streets and roads have stepped (staircase) edges, as anything diagonal in Minecraft.
- A bridge issued at a camp's founding stays timber; a stone rebuild at town tier needs the planner (see requests).
