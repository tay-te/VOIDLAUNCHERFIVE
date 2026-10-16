# Requests from the access kit lane (wave 7, audit lane L1)

The audit (`docs/review/AUDIT.md` §5, §7) found that most blockers come from a handful of kit rules: doors placed
without checking both sides (S1), storeys nobody reaches (S2), furniture in the way (S3), edges with no guard (S4).
This lane fixes them once, in the kits, and adds two kit pieces the other lanes can call. Every API change is
additive: existing calls keep compiling and behave better.

## 1. What every blueprint now gets for free (the access pass)

`build/kit/refine/Access.finish(Draft)` runs in `Compiler.compile` on every draft, after the archetype has drawn and
before the sealed voids are filled and the light pass runs. It does only what is still wrong:

- **Door fronts** (`Doors.finish`): in both front cells of every door, at feet and head height, props are cleared.
  Props are pots, flower boxes, closed trapdoors, open shutters whose leaf lies against the door's wall, chests,
  barrels, counters, hay, bookshelves and the like: anything drawn in the `INTERIOR`, `EXTERIOR` or `OPENINGS` stage
  that a person cannot pass. Steps, beds, rails, ladders, panes and job blocks are not props.
- **Doors that lead nowhere** are bricked up: a door with a full block at feet and head on one side, or a fall of
  four or more on one side. Its lower half becomes a course of the wall it stands in, its upper half the same course
  or, over a drop, a pane. Its `DOOR` points of interest are removed. An entrance (a registered `entrance()` within one
  cell) is never bricked up. Doors whose front cell is outside the plot are left alone.
- **Chests under a solid block** become barrels (same facing, same goods), which open whatever stands on them.
- **Head room over flights** (`Stairs.finish`): over every tread of a run of two or more stairs (not a roof's
  courses), a lamp, chain, joist or shelf in the two rows of head room goes, and a floor board over the second row is
  cut where nothing stands on it. Where a flight turns with no landing (a stair one up beside or before its top tread,
  as in a corkscrew), a board in the third row over the top tread is cut too, so whoever steps round the corner fits.
- **Lofts nobody reaches** (`Lofts.finish`): every level of roofed, walled floor of four cells or more that the walk
  does not reach gets a ladder from the reached floor under it (against a wall, through a hatch, with a cell to step
  off onto; its foot never beside a door, bed, container, stair or workstation). A loft too thin to be a room (a rim
  of boards round an opened floor, a strip under the eaves), or one no ladder fits, is opened to the room under it if
  nothing in it is used: its boards come up where drawn air runs down to something the building drew (never the
  ground), and only where the walk reaches the room under it: a loft over a room nobody reaches (the courtyard house's
  wings while pots close its court) is left for the archetype. A lamp bracket that held by a board, and leaf litter
  or a carpet on a board left hanging, go with the boards. A furnished loft no ladder fits is left for its archetype.
  `-Dhearth.lofts.off=true` turns this step off; `-Dhearth.access.skip=doors,stairs,lofts,guards` leaves any steps out.
- **Guards** (`Guards.finish`): for buildings and grounds (not `defense/*`, not works, roads, workings or fields), every
  reached cell on a flat floor beside a drop of four or more gets a guard **on the edge cell itself**. The guard is the
  culture's rail; on a masonry roof deck it is the wall stone's wall block. It never goes on a roof's slope or ridge, on a
  cell a ladder, hatch, stair or door arrives at, on an entrance or a point of interest, or where the floor does not
  go on inward (a one-wide walk keeps its way). If any guard would leave a reached cell unreached, the pass keeps
  none. Opt out per plot with the param `guards=none`.

To measure what the pass does, run with `JAVA_TOOL_OPTIONS=-Dhearth.access.off=true`.

**What lanes should still do by hand.** The pass is a safety net. A door that has nothing to stand on is better
moved or given its balcony or step than bricked up. Hand-built parapets and balustrades look better than the pass's
rails, so build them in the archetype where the design calls for one. Cells the pass skips (one-wide walks,
galleries reached only along their length, edges beside stair heads) stay with the archetype's lane.

## 2. New kit pieces

### `build/kit/Doors`

| call | does |
|---|---|
| `Doors.ok(d, x, y, z, out)` | both sides of a door at (x, y, z) are standable as drawn so far: level floor, a step down, or a bottom stair/slab up, with two air over it |
| `Doors.ok(d, side, col, y, width)` | the same for a door planned at a wall column, ignoring props that `place` would clear |
| `Doors.standable(d, x, y, z)` | one side's test |
| `Doors.place(d, side, col, y, width, Openings.DoorOpts)` | `Openings.door` only if `ok`, after clearing the dressing in its fronts; returns whether it went in (nothing drawn otherwise) |
| `Doors.placeOrWindow(...)` | `place`, else a plain one-high window over the columns |
| `Doors.clearFronts(d, x, y, z, out)` | clears props from both front cells (call after furnishing near a door) |
| `Doors.finish(d)` | the compile pass (above); returns `Report(doors, cleared, bricked, lids)` |

Ask `ok` once the rooms on both sides are carved and their floors laid, for example after `shell()`.

### `build/kit/Guards`

| call | does |
|---|---|
| `Guards.rail(d)` | the culture's railing fence, else a wooden fence of its timber |
| `Guards.guardOn(d, floorState, floorStage)` | the guard the pass would use on that floor |
| `Guards.edge(d, x, y, z, toward)` | a rail in the cell beyond the standing cell (x, y, z) if that cell is open, allowed and falls 4+ |
| `Guards.deck(d, x0, z0, x1, z1, y, gaps...)` | rails round the open edges of a deck, gallery or balcony standing at y, except at the gap cells (x, z pairs) |
| `Guards.DROP` | 4, the audit's fall edge |

### Changed kit behaviour (same signatures)

- `Porches.balcony(...)` now returns `int`, the depth built (0, 1 or 2). It is **two deep** where the plot allows
  (the deck carried on beams and corbels). The rail stands on the outer row and returns to the wall at both ends, so the
  row before the wall, where the door is, stays clear. Window dressing on that row is removed. Where only one row fits
  (over the street), the rail is a row of open trapdoors with their leaves on the deck's outer edge. Callers put
  their door after the balcony as before (`TownHouses.galleryDoor` works unchanged).
- `Hall.terrace(...)`: on two or more storeys the way up is a ladder from the top storey through a hatch in the deck,
  with an open trapdoor lid, in the middle of the depth against a side wall. An outside stair is used where the side
  is clear all the way up. **With no way up there is no room on the deck** (W1). It still returns whether the terrace
  can be walked to.
- `Rooms.read` (and so `Rooms.furnish`) keeps the inside cell of every door in the walls round the room, or a step up
  or down, free and joined. Callers no longer need `keep()` for doors, though it does no harm.

- `Storeys.flight` and `Hall.flight` rail their stairwell on the floor they arrive at (`Stairs.well`), and
  `Floors.joists` leaves out a joist over a tread's head room.
- `Rooms.furnish`, in kitchens, living rooms, stores and workshops (after the trade's own station), keeps a square of
  three by three clear in rooms of nine cells or more, nearest a door.

- `Spaces.enclosed`: a lantern or a candle at head height no longer closes the view (a lamp on a guard's post beside
  an open strip leaves it a porch, not a room the light check then finds dark).

### New calls

| call | does |
|---|---|
| `Stairs.well(d, x, y, z, dir, rise, openSide)` | rails a stairwell on the floor it arrives at: along its open side and across its lower end, only where a lane stays open beside the rail |
| `Stairs.finish(d)` | the head-room pass above (the compiler runs it) |
| `Lofts.ladder(d, x, z, yFrom, yTo, toWall, roof)` | as before, but only where `Lofts.landing` finds a boarded cell with two air beside the hatch |
| `Lofts.landing(d, x, z, yTo, toWall, roof)` | that step-off cell, or null |
| `Lofts.finish(d)` | the loft pass above (the compiler runs it); returns `Report(ladders, opened)` |
| `Spaces.of(d, false)` | the walk from the street row only (as the invariant checks walk), for passes that must not count a cell reached only from inside |

## 3. Requests to other lanes

### To L0 (gate and checks)

- A balcony one deep over the street now has its rail as open trapdoors on the deck's outer edge. `Spaces.passable`
  treats an open trapdoor as passable, so `Measure`'s fall edge counts that edge. Please treat an open trapdoor whose
  leaf lies on the far edge of the standing cell as a guard in the fall-edge check.
- The hatch lid over a roof ladder (`Hall.terrace`, open trapdoor) is passable for the walker, so ladder exits work
  as before. Please keep it that way.

### To the archetype lanes: what the kit and the pass cannot reach

Measured with `structureAudit -Pmode=metrics -Pseeds=2` on this branch (all 134 archetypes, 10,356 cases). Coordinates
are the audit's (`x/y/z` in the plot's frame, y over the floor); reproduce with the case named.

**`bad_flights` left (93 cases outside the castle, from 246).** None is a flight the kit draws; most are roof courses
the walk climbs onto, so they go with the rule "nobody walks onto roofs" (sills, parapets):

- L4a: `law/courthouse` (36: temperate flat s0, 6/13/4), `civic/town_hall` (12: 6/13/3) and `civic/chapel` (12:
  1/6/6). The belfry ladder lets the walker out onto the roof's brick courses. A sill in the belfry openings (item 4)
  closes them.
- L5: `defense/wall` (14: temperate slope s1, 0/0/2, a flight up the wall walk with its top under the parapet's
  coping), `landmark/lighthouse` (tropical flat s1, 7/3/8, roof courses), `noble/palace` (karst flat s0, 20/0/29),
  `landmark/cathedral` (northern hills s1, 15/4/26).
- L6: `farm/terrace_field` (28: temperate slope s0, 9/-2/1, the terrace steps face down the slope), `infra/dock`
  (13: temperate slope s1, 11/-2/5), `deep/hall` (12: temperate flat s1, 11/0/24), `deep/dwelling` (wisteria flat s1,
  8/8/-1), `farm/field` (moorland slope s1, 7/-4/9), `farm/paddy` (tropical slope s1, 14/-3/5).
- L4b: `commerce/bank` (tropical flat s1, 2/3/10: the vault's piston and smooth stone stand over the top tread; move
  them off the stair's head), `landscape/statue` (steppe flat s0, 1/0/2: the plinth's steps).
- L3: `work/logging_camp` (karst flat s0, 6/3/12: roof courses reached).
- `deep/lift` (84 cases, every one) is fixed here: its corkscrew turns with no landing, and the pass now cuts the
  board over the top tread of each turn.

**Lofts the pass leaves (`unreached_furnished_cells`).** A furnished loft where no ladder fits (no wall cell with a
clear column down to a reached floor and a step-off cell) is left as drawn. The biggest: `work/carpentry` (42 cases,
temperate flat s0, 9/5/2), `work/watermill` (20, temperate flat s1, 4/5/8), `noble/keep` (18), `deep/hall` (24),
`commerce/tavern` (8), `work/brewery` (7), `storage/storehouse` (6). Give these their stair or ladder in the archetype,
or call `Lofts.ladder` with a cell you know is clear.

**Dead ladders** (`dead_ladders`, 207 cases from 255): `farm/orchard` (65, temperate flat s0, 2/1/9, the picking
ladder against the trunk: L6 item 3), `noble/keep` (36) and `noble/palace` (18, L5), `work/barn` (12, desert flat s0,
0/5/5, L3), `house/farmhouse` (4, mesa flat s0, 11/8/6, L2).

**Other findings in passing.**

- L4a: the library's gallery rail (temperate flat s0, the open trapdoors at 3..5/5/9) has its leaves on the wall
  side of the walk, not on the drop's side, so the gallery's edge counts as a fall edge. Turn the trapdoors round
  (or use `Guards.deck`).
- L2: a water cauldron in the town homes' yards cannot be reached in 13 cases each of rowhouse, scribe house,
  shophouse and merchant house, 20 in the tenement (`workstations_unusable`, e.g. rowhouse temperate slope s1,
  4/0/8).
- L3/L4b: barrels with no free face in racks and cellar corners: tavern (66 cases), warehouse (63), storehouse (15),
  bakery (12).
- L3: the bakery's oven top is a walkable enclosed pocket (bakery temperate flat s0, 4/3/5): an unreached "room" the
  audit counts; cap it or fill it.

### To the planner lane: `RealGrowthTest.aTownKeepsItsDiggersAtAFace` is a knife edge

After merging `hearth` (4b2c8ef1) the full build passes but for this test: seed 3 cuts on 21 days of 200 against the
threshold of 24 (0.12). With the access pass off it passes; leaving out only the loft step gives 38, only the guards
step 40, so no one step does it: any change to what the buildings cost moves the town's days about. Across seeds the
same test gives (pass on / off) seed 1: 41 / 59, seed 2: 36 / 37, seed 4: 9 / 23 (fails either way), seed 5: 37 / 43.
The gaps in the failing run are stretches of 40+ days with no working open at all (`........`), the planner not
opening the next one. Please make the test robust (several seeds, or a lower bound that a building change cannot
cross), or look at why no working is opened for weeks at a time.
