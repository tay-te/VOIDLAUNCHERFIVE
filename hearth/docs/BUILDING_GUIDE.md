# VOID Hearth — building guide

How the build module turns a `PlotSpec` into a `Blueprint`, and how to write archetypes that look handmade. Read
ARCHITECTURE.md §6 first for the contract; this guide is the working manual for whoever writes or edits buildings.

Everything below lives in `hearth/src/main/java/dev/voidmc/hearth/build/`:

| package | what |
|---|---|
| `build` | `BuildModule` (wiring), `BlueprintServiceImpl` (LRU cache, error handling), `Archetype`, `ArchetypeRegistry` |
| `build.draft` | `Draft` (the canvas an archetype draws on), `Mat`/`Paint` (materials), `Dirs`, `States`, `BlockEntities`, `Noise` |
| `build.blueprint` | `Compiler`, `ShapeResolver`, `BlueprintImpl` (packed steps, chunk index), `MaterialBill`, `LocalFrame` |
| `build.kit` | the components: `Body`, `Facade`, `Walls`, `Openings`, `Roofs`, `Porches`, `Stairs`, `Floors`, `Chimneys`, `Furniture`, `Rooms`, `Garden`, `Side` |
| `build.terrain` | `Site` (the ground under the plot), `Groundwork` (clearing, footings, levelling, steps, approaches, stilts) |
| `build.style` | `Style`, `StyleLoader`, `StyleRegistry`, `Role`, `Material`, `Palette`, `Form`, `Habits` |
| `build.archetype` | the archetypes, registered in `Archetypes.registerAll` |
| `build.check` | `BlueprintChecks`: bounds, floating blocks, walkability, chunk index, stage order |
| `build.command` | `/hearth build` |

Styles are JSON in `src/main/resources/data/hearth/hearth_style/<culture>.json`. Previews and their renderer are in the
gametest source set (`gametest/BlueprintPreview.java`, `gametest/render/`). Tests are in
`src/test/java/dev/voidmc/hearth/build/`.

---

## 1. The local frame

Archetypes never see world coordinates. They draw in a frame tied to the street:

```
            back (z = depth-1)
        +-------------------+
        |                   |
  left  |      the plot     |  right
 (x=0)  |                   | (x = frontage-1)
        |                   |
        +-------------------+  z = 0   (the front row)
  ~~~~~~~~~~~~ street ~~~~~~~~~~~~~~~   z = -1
                 viewer
```

- **x** runs across the frontage, left to right **as seen from the street**. **z** runs from the front (0) to the back
  (`depth - 1`). **y** is relative to the floor level: `y = 0` is where a person stands on the ground floor, the floor
  block itself is at `y = -1`.
- Directions in local code are the labels in `Dirs`: `Dirs.FRONT` (toward the street, z − 1), `Dirs.BACK`, `Dirs.LEFT`,
  `Dirs.RIGHT`. Step with `Dirs.dx(dir)` / `Dirs.dz(dir)`, never `BlockPos.relative`. A stair "facing" `Dirs.BACK`
  climbs toward the back. `Dirs.right(d)` is d turned clockwise seen from above.
- The compiler maps the frame onto the world for whichever way the plot faces and rotates directional states (stairs,
  doors, logs' axes, beds, trapdoors, fence gates, wall torches...). Draw as if the street were in front of you.
- **Limits.** Placements outside `0 ≤ x < frontage`, `-1 ≤ z < depth`, `y < maxHeight()` are dropped and counted
  (`d.clipped()`). Row `z = -1` (over the street) is allowed only at `y ≥ 3`: eaves, awnings, signs, jetties. Nothing
  may stand on the street.
- `d.site().ground(x, z)` is the natural standing level at a cell, relative to the floor (so on a slope it is negative
  in front and positive behind); `d.site().water(x, z)` the water surface (first air above it) or `Site.NONE`. The site
  is sampled four blocks beyond the plot, so `ground(x, -1)` is the street.

## 2. The draft

`Draft d` is the canvas. Its inputs:

| call | gives |
|---|---|
| `d.frontage()`, `d.depth()`, `d.maxHeight()`, `d.tier()` | the plot |
| `d.site()` | the ground and water under and around the plot |
| `d.habits()` | the style's regional habits (variant applied) |
| `d.mat(Role.X)` | a material bound to this building (see §5) |
| `d.seed()`, `d.random()`, `d.chance(p)`, `d.pick(a, b, c)`, `d.between(lo, hi)` | deterministic randomness |
| `d.param("key", fallback)` | a spec param (int, boolean or String overloads); `d.hasParam` |
| `d.variant()` | the style variant this building wears (-1 the base dressing) |

**Determinism.** All randomness comes from the spec seed through `d.random()` (or `Noise.unit(d.seed(), x, y, z)` for
per-position choices that must not shift when the drawing order changes). Never use `Math.random`, wall-clock time,
hash sets with identity hashes, or world state. The same spec and terrain must give the same blueprint
(`BlueprintChecks.same`, tested).

**Drawing.** Every call takes local coordinates:

```java
d.set(x, y, z, state);                       // a block state (or a Paint, or a Block's default state)
d.set(x, y, z, state, blockEntityTag);       // with a block entity (see BlockEntities)
d.setIfAbsent(x, y, z, paint);               // only where nothing is drawn yet
d.setIfAir(x, y, z, paint);                  // only where nothing or air is drawn
d.air(x, y, z);  d.remove(x, y, z);          // draw air (clears what is there when built) / undraw
d.fill(x0, y0, z0, x1, y1, z1, paint);       d.fillIfAbsent(...);
d.hollowBox(x0, y0, z0, x1, y1, z1, walls, inside /* or null */);
d.outline(x0, z0, x1, z1, y, paint);         d.line(x0, y0, z0, x1, y1, z1, paint);
d.column(x, z, y0, y1, paint);               d.ellipse(cx, cz, rx, rz, y, filled, paint);
d.carve(x0, y0, z0, x1, y1, z1);             // air where nothing is drawn: a room's volume (stage WALLS)
d.clearBox(...);                              // air everywhere in the box, replacing drawings
Draft.ring(x0, z0, x1, z1)  Draft.ellipseCells(cx, cz, rx, rz, filled)  Draft.rect(x0, z0, x1, z1, visitor)
```

Reading back: `d.get(x, y, z)` (null where nothing is drawn), `d.cell(...)` (state, tag, stage),
`d.isAbsent`, `d.isEmpty` (nothing or air), `d.isSolid`.

Two-part blocks have helpers that place both halves: `d.door(x, y, z, doorBlock, facing, hinge)`,
`d.bed(x, y, z, bedBlock, facing)`, `d.tallPlant(x, y, z, plant)`.

**Records for the simulation.** `d.entrance(x, y, z)` registers a cell folk walk in from (the first one registered is
the main entrance; put it on the street side, usually `z = -1` or the cell outside the front door). `d.poi(type, x, y,
z, facing[, job])` registers a point of interest at the cell a person stands in (beds are registered automatically
from bed blocks). `d.body(x0, z0, x1, z1)` declares the main mass (used to tell which way a wall faces when mixing
weathered materials). `d.eave(y)` records the eave height for the silhouette.

**Stages.** Every placement belongs to a `StageKind`, the order a builder works in: `CLEAR` (trees and earth out of the
way), `EARTHWORK`, `FOUNDATION`, `FRAME`, `WALLS`, `FLOORS`, `ROOF`, `OPENINGS`, `INTERIOR`, `EXTERIOR`. Materials carry
their role's default stage; set one for a block of code with

```java
try (var s = d.stage(StageKind.INTERIOR)) { ... }     // or d.inStage(StageKind.INTERIOR, () -> ...)
```

Within a stage the compiler orders steps itself: supports first (bottom-up, a hanging lantern after its ceiling, a chain
top-down), structural before attached before resting before hanging, outward from the main entrance, then by position.
Clearing runs top-down; earthwork digs before it fills.

## 3. What the compiler does for you

Do not hand-set connection states; the `ShapeResolver` sets them from the finished drawing:

- stair corners (inner/outer) from neighbouring stairs;
- fence, wall, pane and iron-bar connections (wall posts where vanilla raises them);
- door halves and hinges, bed halves, double plants;
- waterlogging of outdoor stages (`CLEAR`, `EARTHWORK`, `FOUNDATION`, `EXTERIOR`) under natural water, unless the
  column was drained (`d.drain(x, z)`, what `Groundwork.level(..., fillWater)` does behind its dyke); a building's
  interior carved below the water line stays dry;
- snowy grass under snow.

Air drawn in stage `CLEAR` under natural water is dropped (clearing never drains a river). Block entities are 26.3
`CompoundTag`s; `BlockEntities` builds the common ones: `sign(lines, colour, glowing)`, `lectern(title, author,
pages)`, `banner(patterns)`, `pot(back, left, right, front)`, `container(items)`, `shelf(items)`, where items are
`List<Object[]>` of `{"minecraft:bread", 6}`.

The bill of goods per stage is computed from the steps (`MaterialBill`, documented mapping: wood blocks → WOOD, stone
and stone bricks → STONE, bricks/terracotta/mud → CLAY, glass → GLASS, iron/copper/lanterns/chains → METAL,
wool/carpet/beds/banners → CLOTH...); `work` counts non-air steps plus excavation.

## 4. The kit

Components take a `Draft` and local coordinates and draw complete, finished parts. They read the style through
`d.mat(...)` and `d.habits()`, so the same call gives a timber frame in the temperate style and adobe in the desert.

### Body and sides

`Body` describes storeys over a rectangle: `new Body(x0, z0, x1, z1, baseY, heights, partyLeft, partyRight)`, where
`heights[k]` is storey k's height in blocks (4 is an ordinary storey: floor + 3 of air; a 1 on top is an attic knee).
Shape it with `body.jetty(k, side)` (storey k and up oversail one block) and `body.setback(k, side, n)` (storey k and up
step back n). Ask `body.rect(k)`, `body.topRect()`, `body.floorY(k)` (standing level of storey k), `body.plateY()` (the
course the roof sits on), `body.side(k, Dirs.FRONT)`.

A `Side` is one wall walked as seen from outside, cell 0 at the left: `side.x(i)`, `side.z(i)`, `side.length()`,
`side.out()`, `side.column(i)` / `side.index(x)`, `side.outX(i, n)` / `side.outZ(i, n)` (n cells outside),
`side.middle()`, `side.isCorner(i)`. `Side.of(x0, z0, x1, z1)` gives the four walls.

### Facade, walls, openings

```java
Facade facade = Facade.plan(d, body, doorColumn, doorWidth, windowWidth, density);   // all storeys, once
HouseBuilder.shell(d, body, facade);        // floors, walls storey by storey (Walls.storey), rooms' air
HouseBuilder.openings(d, body, facade, true);
```

`Facade.plan` places posts (timber, bamboo, plank kinds), the door in its bay and windows symmetric by density, so
windows line up storey over storey. `Walls.storey(d, body, k, facade)` draws a storey in the style's wall kind
(`TIMBER_FRAME`, `LOG`, `STONE`, `PLASTER`, `ADOBE`, `BAMBOO`, `BRICK`, `PLANK`; `habits.walls.upper` may board upper
storeys differently), with plinth, girts, braces, quoins, notched log corners and wall depth where the kind has them.

`Openings.window(d, side, column, y, width, height, style[, opts])` draws a window with sill, lintel or arch, shutters
and flower box by habit; styles `DRESSED`, `PLAIN`, `ARCHED`, `LATTICE`, `SLIT`. `Openings.door(d, side, column, y,
width, DoorOpts.MAIN)` draws a door with its lamp and hood and registers the entrance and a DOOR POI;
`Openings.wallLantern`, `Openings.hood`, `Openings.box` are the small parts.

### Roofs

Roofs are **height fields**: `h(x, z)` in blocks above an eave line, rasterised to stairs where the slope is a whole
block per cell, slabs where it is half, full blocks stacked where it is steep (toward the downhill neighbour only), and
a ridge cap. That one engine makes gables, hips, half-hips, gambrels, mansards, sheds, saltboxes, cones, domes and
curved roofs, and merges them (cross gables and dormers are max-merged fields, rasterised once, so valleys come out
right).

```java
// whole roofs, the usual way: eaves and gable ends from the habits, gable walls filled, brackets under the eaves
Roofs.Built b = Roofs.build(d, wx0, wz0, wx1, wz1, plateY, RoofForm.HALF_HIP, Direction.Axis.X, Roofs.Opts.of(d), partyL, partyR);

// with additions
Roofs.Plan plan = Roofs.plan(d, wx0, wz0, wx1, wz1, plateY, form, axis, Roofs.Opts.of(d), partyL, partyR);
plan.crossGable(frontSide, column, width);    // a gable at right angles over the door, its own end wall
plan.dormer(frontSide, column, 1, sillY);      // a dormer window merged into the slope
Roofs.Roof roof = plan.draw(d).roof();

// raw fields for anything else
Roofs.Field f = Roofs.shed(x0, z0, x1, z1, Dirs.BACK, Roofs.Profile.straight(0.5));   // rises toward the back
Roofs.Roof r = Roofs.raster(d, f, eaveY, x0, z0, x1, z1, d.mat(Role.ROOF), d.mat(Role.ROOF_TRIM), Habits.Ridge.SLAB);
```

Fields: `gable`, `hip`, `halfHip`, `shed`, `saltbox`, `cone`, `dome`, `flared`; profiles `Profile.straight(pitch)`
and `Profile.broken(steep, shallow, knee)`; `field.max(other)` merges. A `Roof` answers `covers(x, z)`,
`surfaceY(x, z)`, `bottomY(x, z)` (what a wall fills up to) and `ridgeY`. `Roofs.flat(d, ...)` makes a deck with a
parapet, spouts and vigas; `Roofs.terrace(d, outer, inner, deckY)` the roof of a lower storey round a set-back upper
one. `Chimneys.stack(d, x, z, fromY, roof, minTop)` runs a chimney up through the roof with a corbel and a lit campfire
(smoke) on top. Thatched styles (`habits.roof.thatch`) get their roof and ridge from the `thatch` role automatically.

### Porches, stairs, floors

`Porches.porch(d, side, c0, c1, depth, y, clear, opening)` (posts never in front of the opening, railings, lean-to
roof, a bench), `Porches.balcony(d, side, c0, c1, floorY)`, `Porches.bay(d, side, c0, c1, y)`.

`Stairs.straight(d, x, y, z, dir, rise, width)` (with a string under every step), `Stairs.switchback(d, x, y, z, dir,
rise, across)` (two lanes, a landing), `Stairs.spiral(d, cx, cz, y0, rise, startAt)`, `Stairs.ladder(d, x, z, y0, y1,
facing)` (last resort). Cut the stairwell in the floor above yourself and keep its foot and head free in the rooms.

`Floors.boards(d, x0, z0, x1, z1, y)` lays boards (over anything not built, dug-out ground included);
`Floors.joists(...)` exposed beams under a ceiling, skipping stairwells.

### Rooms and furniture

`Rooms` furnishes a room so it stays walkable:

```java
Rooms.Room room = new Rooms.Room(ix0, iz0, ix1, iz1, floorY, airRows);
room.keep(doorX, iz0);                      // stays free and must stay reachable (inside a door, a stair foot)
room.keepArea(ax, az, bx, bz);              // stays free (a stair run)
room.block(x, z);                           // already taken (a chimney breast)
Rooms.furnish(d, room, Rooms.Type.BEDROOM, 2, JobType.NONE);   // BEDROOM, KITCHEN, LIVING, WORKSHOP, STORAGE, HALL
```

`furnish` reads what is already built: blocks on the floor are taken, holes (stairwells) cannot be stood on, cells
under a stair can hold furniture but are not walkways, ladders and carpets are walked through. Every placement is
tried against a connectivity check, so a bed never cuts the room in two and every bed side, chest front and workstation
front stays reachable. `room.claim(x, z)` reserves a cell for something you build yourself (a hearth).

`Furniture` has the pieces: `bed`, `table`, `longTable`, `chair`, `chest` (registers STORAGE in front),
`barrel`, `shelf`, `bookshelf`, `hangingLantern`, `wallTorch`, `rug`, `workstation(d, job, x, y, z, facing)` (the
job's block, JOB_SITE in front), `hearth` (registers HEARTH), `counter`.

### Gardens and yards

`Garden.garden(d, x0, z0, x1, z1, y)` lays the culture's garden kind (`COTTAGE`, `KITCHEN`, `XERIC`, `ZEN`,
`TROPICAL`, `ROCK`). The parts: `flower`, `pot`, `flowerBed`, `kitchenRows`, `path` (only over ground), `fence` (with a
gate), `tree`, `woodpile`, `barrels`, `beehive`, `waterButt`, `lampPost`, `snowBanks`. `Garden.line(x0, z0, x1, z1)`
gives the cells of a straight run.

## 5. Styles

One style per culture, `data/hearth/hearth_style/<culture>.json`, loaded from the classpath and reloaded from data
packs (`/reload`): a pack can override any culture's style or add new ones under its own namespace.

```json
{
  "culture": "temperate",
  "name": "Temperate: timber frame and plaster on a stone plinth",
  "roles": {
    "wall": [{"m": "minecraft:mushroom_stem", "w": 3}, {"m": "minecraft:smooth_quartz", "w": 2, "high": 1}],
    "foundation": [{"m": "minecraft:cobblestone", "w": 3}, {"m": "minecraft:mossy_cobblestone", "w": 1, "low": 2, "north": 1.5}],
    "frame": "wood:minecraft:dark_oak",
    "roof": [{"m": "wood:minecraft:spruce", "w": 4}, {"m": "wood:minecraft:dark_oak", "w": 1.5}],
    "garden": ["minecraft:poppy", "expanse:lavender|minecraft:allium"]
  },
  "tiers": {"0": {"roof": "wood:minecraft:oak"}, "3": {"roof": "minecraft:deepslate_tiles"}},
  "base_weight": 2,
  "variants": [
    {"name": "brick nogging", "w": 1, "roles": {"wall": ["minecraft:bricks"]}},
    {"name": "rubble stone", "w": 1, "roles": {"wall": ["minecraft:cobblestone"]}, "habits": {"walls": {"kind": "stone"}}}
  ],
  "habits": { ... }
}
```

**Roles.** `foundation`, `plinth`, `wall`, `wall_accent`, `frame`, `beam`, `floor`, `ceiling`, `roof`, `roof_trim`,
`gable` (boards of gable ends and plank walls), `window` (sills, frames), `glass`, `door`, `shutter`, `fence`,
`railing`, `path`, `paving`, `light`, `chimney`, `furniture`, `cloth`, `accent`, `garden`, `crops`, `hedge`, `tree_log`,
`tree_leaves`, `ground`, `fill`, `stilt`, `thatch`. Missing roles fall back to vanilla defaults (`StyleDefaults`).

**Materials.** A material string is `wood:<ns>:<name>` (a whole wood family: planks, log, wood, stripped, stairs,
slab, fence, gate, door, trapdoor, sign...), `dye:<colour>` (wool and its stairs/slabs, carpet, bed, banner, candle,
glass, terracotta), or a block id whose cuts follow vanilla naming (`minecraft:stone_bricks` → `stone_brick_stairs`,
`_slab`, `_wall`). `a|b|c` is a fallback chain: Expanse ids first, vanilla after, so styles work without Expanse.
Per-form overrides go on an entry: `{"m": "minecraft:hay_block", "stairs": "minecraft:bamboo_mosaic_stairs",
"slab": "minecraft:bamboo_mosaic_slab"}`.

**Mixing.** A role lists 2–4 weighted entries. Most roles mix per block by a patchy noise (so walls weather in
patches, not salt-and-pepper); biases shift weights by position: `low` (near the ground), `high`, `north` (north
faces: moss), `corner`. Roles that should be one thing per building (`roof`, `roof_trim`, `frame`, `beam`, `door`,
`shutter`, `window`, `fence`, `railing`, `furniture`, `chimney`, `stilt`, `light`, `floor`, `ceiling`, `tree_log`,
`tree_leaves`, `hedge`) choose one entry per building by seed instead, so a street of houses varies roof to roof while
each roof is whole.

**Tiers.** The base roles are tier 2 (an ordinary village house). `tiers` overrides roles for other tiers; a tier
inherits toward 2 (tier 4 looks at 4, 3, then the base; tier 0 at 0, 1, then the base).

**Variants.** The fashions within a culture: each building picks one by seed, weighted against `base_weight` (the
base dressing). A variant overrides roles and, optionally, habit keys (merged section by section, a key's value
replaced whole). A tier's own overrides win over a variant's. Force one with the param `variant` (an index).

**Habits** (all optional; defaults in `Habits.DEFAULT`):

| section | keys |
|---|---|
| `walls` | `kind` (wall kind), `upper` (kind of upper storeys), `storey_height`, `plinth` (courses), `jetty` (chance), `post_spacing`, `braces` (chance), `recessed`, `beam_ends` (chance) |
| `roof` | `forms` ({form: weight}: gable, cross_gable, hip, half_hip, gambrel, mansard, flat, dome, cone, pagoda, shed, saltbox), `pitch` (1 = 45°), `eaves`, `gable_overhang`, `ridge` (slab, trim, stairs, full, wall), `flare`, `brackets`, `dormers` (chance), `thatch`, `parapet`, `front_gable` (chance the ridge runs back from the street) |
| `windows` | `shutters`, `boxes`, `arched`, `bay` (chances), `lattice`, `tall` |
| `plan` | `round`, `courtyard`, `tower` (chances), `stilts` (never, water, always), `stilt_height`, `terraces` |
| `yard` | `garden` (kind), `fence` (fence, wall, hedge, none), `hedge`, `woodpile` (chances) |
| top level | `chimney`, `porch`, `balcony` (chances), `snow` |

Check a style with `./gradlew test` (`StylesTest` verifies every role of every tier and variant resolves to blocks and
can be cut in the forms the kit asks of it) and look at it with the preview.

## 6. Terrain

The spec's floor level (`y = 0`) is set by the planner so floors meet the street; the ground under the plot can be
anything. Reconcile, never float, never bury a door:

| helper | does |
|---|---|
| `Groundwork.footings(d, x0, z0, x1, z1, floorBlockY)` | foundation from the floor block down to the ground under a rectangle |
| `Groundwork.level(d, x0, z0, x1, z1, y[, fillWater])` | cut and fill to standing level y; a retaining wall where the cut is deeper than a block beyond the edge; with `fillWater`, water is embanked behind a stone edge, filled, and drained above the new level |
| `Groundwork.apron(d, ...)` | a gentle bank of earth round walls standing proud of the ground |
| `Groundwork.steps(d, x, y, z, dir, width)` | a flight down from a door to the ground with a landing and a turn when long |
| `Groundwork.approach(d, x, y, z)` | a walk from a door at (x, z), standing level y, to the street: routed through free cells to the front row, stepping at most a block per cell (stairs down or up, paving level, a footing under every step, a cutting where the ground rises), then along the front row until it meets the street's level |
| `Groundwork.stilts(d, ...)`, `needsStilts(...)` | posts to the bed or ground, cross-braced when tall |
| `Groundwork.clear(d, x0, z0, x1, z1, topY)` | air from the ground (or water) up, in stage CLEAR: trees and boulders out of the way. Call it last |
| `Groundwork.surface(d, x, z)` | the standing level as drawn so far (raised by fill, lowered by a cut) |

Patterns that work: footings plus an approach for a house on a gentle slope; stilts plus a porch and an approach over
water or very uneven ground (and by habit in tropical and bayou styles); the door column chosen where the street meets
the floor, with a row in front for steps, for tall narrow houses on steep streets (see `Townhouse`); a level yard with a
dyke on a river bank (`BuildersYard`). Put the entrance where the street can reach it: the checks walk from the street.

## 7. Writing an archetype

1. **Pick the id and the info.** Ids are `hearth:<category>/<name>` from the catalogue in ARCHITECTURE.md §6.1.
   `ArchetypeInfo(id, category, jobs, beds, minFrontage, maxFrontage, minDepth, maxDepth, minHeight, maxHeight,
   minTier, cultures, flags, upgradesTo, maxPerSettlement, weight)`. The planner chooses by it; be honest about sizes
   and heights.
2. **Write the class** implementing `Archetype` (`info()` and `draw(Draft d)`), with a Javadoc that says what it looks
   like and documents every param.
3. **Draw** in this order, roughly: entrance; ground (level, footings, stilts); the body (walls, floors); openings;
   porch/balcony; roof; chimney and hearth; stairs and rooms; garden and yard; `Groundwork.clear(...)` last.
4. **Register** it in `Archetypes.registerAll` and add its name to `assets/hearth/lang/en_us/build.json` as
   `hearth.archetype.<category>.<name>`.
5. **Look at it**: `./gradlew blueprintPreview -Parchetype=<category>/<name> -Pstyle=all -Pseeds=4 -Pterrain=slope`,
   read the sheet, fix, repeat. Then `./gradlew test`: the invariant tests pick up every registered archetype
   automatically (all cultures, all variants, six terrains, four facings).

### The worked example: `hearth:commerce/market_stall`

`archetype/commerce/MarketStall.java`, a merchant's stall: four posts, a striped cloth awning sloping over a counter,
stock behind. The whole `draw`:

```java
public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("commerce/market_stall"), ArchetypeCategory.COMMERCE,
	Set.of(JobType.MERCHANT), 0, 4, 7, 4, 6, 4, 7, SettlementTier.HAMLET, Set.of(), Set.of(ArchetypeInfo.SiteFlag.CENTRAL),
	List.of(), 0, 1.0);

@Override
public void draw(Draft d) {
	int w = d.frontage();
	int dp = d.depth();
	// customers come in from the street at the middle of the front
	d.entrance(w / 2, 0, -1);
	// the plot levelled to the floor (a river bank embanked), the stall paved
	Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
	int x0 = w >= 6 ? 1 : 0;
	int x1 = w >= 6 ? w - 2 : w - 1;
	int zc = 1;            // the counter row
	int zb = dp - 1;       // the back row
	Mat paving = d.mat(Role.PAVING);
	try (var s = d.stage(StageKind.EXTERIOR)) {
		for (int z = 0; z <= zb; z++) {
			for (int x = x0; x <= x1; x++) {
				d.set(x, -1, z, paving.full());
			}
		}
	}
	// four posts
	Mat frame = d.mat(Role.FRAME);
	try (var s = d.stage(StageKind.FRAME)) {
		for (int[] p : new int[][]{{x0, zc}, {x1, zc}, {x0, zb}, {x1, zb}}) {
			for (int y = 0; y < 3; y++) {
				d.set(p[0], y, p[1], frame.log(Direction.Axis.Y));
			}
		}
	}
	// the awning: a lean-to field of cloth rising toward the back, overhanging the front row
	Mat cloth = d.mat(Role.CLOTH);
	Roofs.Field awning = Roofs.shed(x0, 0, x1, zb, Dirs.BACK, Roofs.Profile.straight(0.5));
	Roofs.Roof roof = Roofs.raster(d, awning, 3, x0, 0, x1, zb, cloth, cloth, Habits.Ridge.SLAB);
	if (d.param("stripes", d.chance(0.75))) {
		stripe(d, x0, x1, 0, zb, roof);        // every other column restated in white wool of the same shape
	}
	// the posts carried up to meet it (nothing may float)
	try (var s = d.stage(StageKind.FRAME)) {
		for (int[] p : new int[][]{{x0, zc}, {x1, zc}, {x0, zb}, {x1, zb}}) {
			for (int y = 3; y < roof.bottomY(p[0], p[1]); y++) {
				d.set(p[0], y, p[1], frame.log(Direction.Axis.Y));
			}
		}
	}
	// the counter: wares on slab tops and barrels, a gap at one end to get behind it
	Mat wood = d.mat(Role.FURNITURE);
	int gap = d.chance(0.5) ? x0 + 1 : x1 - 1;
	try (var s = d.stage(StageKind.INTERIOR)) {
		for (int x = x0 + 1; x <= x1 - 1; x++) {
			if (x == gap) {
				continue;
			}
			if ((x - x0) % 3 == 2) {
				d.set(x, 0, zc, States.facing(Blocks.BARREL, Dirs.FRONT));
			} else {
				d.set(x, 0, zc, wood.slab(SlabType.TOP));
				wares(d, x, 1, zc);                 // a pot, a candle, a decorated pot, a melon, or nothing
			}
		}
	}
	int mid = (x0 + x1) / 2 == gap ? (x0 + x1) / 2 + (gap == x0 + 1 ? 1 : -1) : (x0 + x1) / 2;
	d.poi(PoiType.MARKET_STALL, mid, 0, zc - 1, Dirs.BACK);     // where a customer stands
	d.poi(PoiType.COUNTER, mid, 0, zc + 1, Dirs.FRONT);         // where the merchant stands
	// stock at the back: the merchant's barrel (the job site), a chest, crates
	if (zb - zc >= 3) {
		Furniture.workstation(d, JobType.MERCHANT, x0 + 1, 0, zb, Dirs.FRONT);
		Furniture.chest(d, x1 - 1, 0, zb, Dirs.FRONT, List.of(new Object[]{"minecraft:bread", 6}, new Object[]{"minecraft:apple", 4}));
		if (x1 - x0 >= 4) {
			Garden.barrels(d, x0 + 2, 0, zb, Dirs.RIGHT, Math.min(2, x1 - x0 - 3));
		}
	} else {
		Furniture.workstation(d, JobType.MERCHANT, gap == x0 + 1 ? x1 - 1 : x0 + 1, 0, zb, Dirs.FRONT);
	}
	// a lantern under the high side of the awning
	if (roof.covers(mid, zb - 1) && roof.bottomY(mid, zb - 1) >= 3) {
		Furniture.hangingLantern(d, mid, roof.bottomY(mid, zb - 1), zb - 1, 0);
	}
	Groundwork.clear(d, 0, 0, w - 1, dp - 1, roof.ridgeY + 2);
}
```

What it shows: the entrance on the street row; levelling with water handling; materials by role so every culture
dresses it differently (striped red awnings in the temperate style, blue and white in the Mediterranean, ochre on the
steppe); a roof field reused as an awning; posts carried up to whatever the roof turned out to be (read the `Roof`, do
not assume); furniture through the kit so POIs are registered; a param with a seeded default; clearing last.

The bigger archetypes are the references for everything else: `house/Hut` (round and square, stilts),
`house/HouseBuilder.cottage` (facade, cross gables and dormers, porch, bay, balcony, attic, lean-to outshot, garden),
`house/Townhouse` (jetties, shop front, switchback stair well, party walls, roof terrace), `work/BuildersYard` (yard
layout, shed, crane, stacks), `landscape/Well`.

### Params conventions

- Keys are `snake_case`; read them with `d.param(key, fallback)`, and make the fallback a seeded choice where variety
  is wanted (`d.param("shop", d.chance(0.5))`), so the planner can pin what it needs and leave the rest to the seed.
- Document every key in the class Javadoc: name, type, range, default.
- Shared keys: `storeys` (int), `party_left` / `party_right` (boolean, ATTACHABLE archetypes: no windows, no eaves on
  that side — **the building's own left and right looking out of its front door**, which is the mirror of the local
  frame's LEFT/RIGHT; always read them with `d.partyLocalLeft()` / `d.partyLocalRight()`, never `d.param("party_left")`), `roof` (a `RoofForm` name), `variant` (style variant index). Per archetype: `shop` and `terrace`
  (townhouse), `outshot` (cottage), `stripes` (market stall).
- Infrastructure archetypes take their geometry in params (ARCHITECTURE.md §6.1).

## 8. The quality bar

Every archetype, every culture, every seed:

- [ ] **Correct.** `./gradlew test` passes: inside the plot and height, nothing floats, every bed and job site
  reachable from a door, the main door reachable from the street, chunk index and stage order right, deterministic.
- [ ] **Grounded.** On slope, steep, hills, river and coast terrains: footings or stilts reach the ground, no buried
  doors, no stairs ending a block above the street, gardens and paths only on ground.
- [ ] **Silhouette first.** Readable from a distance: a roof that overhangs, a chimney, a gable or a tower, a step in
  the mass (jetty, outshot, set-back storey, porch). No plain boxes.
- [ ] **Depth.** Walls are not flat planes: posts, plinths, sills, lintels, shutters, beam ends, window boxes, eaves
  brackets. Something every three blocks.
- [ ] **Material discipline.** Two or three materials carry a building; the palette mixes in patches; accents are rare
  and deliberate. No noisy mixes, no colours outside the culture.
- [ ] **Lived in.** Interiors are furnished and walkable, with light (lanterns, a hearth), storage and a place to sit;
  outside there is a path to the door, plants, a woodpile, a barrel, a lamp.
- [ ] **Varied.** Four seeds of one style should look like four different houses by four builders of one tradition:
  roof form, size, door side, porch or none, variant palette, garden.
- [ ] **Culture.** Desert reads as desert, karst as karst. Use the habits; add to the style (a variant, a habit) rather
  than special-casing a culture in Java where you can.
- [ ] **Looked at.** Render it on flat, slope and steep in every culture, from both corners, with the storey cutaways,
  and in game with `/hearth build`.

## 9. Previews

```
cd hearth
JAVA_HOME=C:/Users/bitto/VOID/.tools/jdk-25 ./gradlew blueprintPreview -Parchetype=house/cottage -Pstyle=temperate,desert -Pseeds=4 -Pterrain=slope
```

- `archetype`: an id, a path (`house/cottage`), a name (`cottage`), a comma list, or `all`.
- `style`: culture names, comma separated, or `all`. `seeds`: how many seeds per style.
- `terrain`: `flat`, `slope`, `hills`, `river`, `coast`, `steep` (comma list or `all`), optionally followed by params
  after semicolons, passed to the spec: `-Pterrain="steep;frontage=9;depth=11;roof=hip;outshot=true"`.
  Preview-only keys: `seed=` (first seed), `facing=` (south, east, north, west; else the facing turns per seed),
  `frontage=`, `depth=`, `tier=`, `variant=`.
- Output in `build/previews/`: `sheet_<archetype>_<terrain>.png` (all styles × seeds, labelled with the variant) and
  per building `<archetype>/<culture>_s<n>_<terrain>.png` (views from two opposite corners, then a plan and a cutaway
  per storey) and `_front.png`.
- Every building is checked; problems print as `CHECK <archetype> <style> s<n> <terrain>: ...`.

Debugging a check failure: reproduce it with its seed and facing, then print text sections and walk maps:

```
-Pterrain="steep;seed=845751;facing=south;slices=true;slice=y0/y4/x3/z2/c3:0;reach=0/4"
```

`slice=` takes plans (`y4`: local height), sections (`x3`, `z2`) and column listings (`c3:0`: every drafted cell in local
column x=3, z=0 with its stage and state), joined by `/`; without `slice=`, `slices=true` prints a few sections.
`reach=0/4` prints walk maps at floor-relative heights: `r` reached from the entrances, `+` walkable but not reached, `#`
solid, `.` air, `B` bed, `P`/`p` job site or store (unreached/reached), `E` entrance. Glyphs in sections: `#` log,
`=` planks, `o` stone/brick, `|` glass, `D` door, `f` fence, `t` trapdoor, `NSEW` stairs (lower case: upside down),
`_`/`‾` bottom/top slab, `@` anything else, `.` drafted air, blank nothing drafted.

`./gradlew settlementPreview` (the plan module's) renders whole settlements with these buildings.

## 10. Tests

`./gradlew test` (JUnit 5, `src/test/java/dev/voidmc/hearth/build/`):

- `BlueprintInvariantsTest`: every archetype × six terrains × every culture × two seeds × turning facings; every style
  variant forced for every archetype; outshot cottages on rough ground; determinism; every archetype builds something
  with an entrance, housing has beds, workplaces their job sites.
- `MachineryTest`: the local frame in all four facings, shape resolution, rotation of directional states, build order,
  the materials bill, the service cache.
- `StylesTest`: every culture has its style; every role of every tier and variant resolves and cuts the forms the kit
  needs; material families and fallbacks; palette mixing.

A new archetype is covered by the invariant tests as soon as it is registered.

## 11. In game

`/hearth build <archetype> [style] [seed] [frontage] [depth] [storeys]` (permission level 2) generates a blueprint for
the land in front of you and places it at once, its street front facing you, two blocks ahead. `style` is a culture
name or `auto` (the culture of the biome there); seed, frontage and depth default to random choices in the archetype's
ranges. The chat repeats the full command (click to reuse it). `/hearth build undo` puts back what the last build
replaced. The live probe reads the loaded world: trees and plants are not ground, so a house set in a wood clears them.

## 12. Performance

Drafting and compiling a building takes milliseconds; blueprints are cached (LRU, 512, keyed by spec, probe
and style generation) and the service is thread-safe, so the sim may ask from worker threads. Keep `draw` free of world
access and allocation-heavy loops over large volumes; use the kit's helpers, which work on the plot only. Blueprints
store steps packed (an int position and an int palette index per step, block entities aside) with a per-chunk index, so a builder or the off-screen applier touches only the
chunks it is in.
