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

(Further items of this lane, such as landings, stairwell guards, lofts, furnishing and lighting, are listed below as
they land.)

## 3. Requests to other lanes

### To L0 (gate and checks)

- A balcony one deep over the street now has its rail as open trapdoors on the deck's outer edge. `Spaces.passable`
  treats an open trapdoor as passable, so `Measure`'s fall edge counts that edge. Please treat an open trapdoor whose
  leaf lies on the far edge of the standing cell as a guard in the fall-edge check.
- The hatch lid over a roof ladder (`Hall.terrace`, open trapdoor) is passable for the walker, so ladder exits work
  as before. Please keep it that way.
