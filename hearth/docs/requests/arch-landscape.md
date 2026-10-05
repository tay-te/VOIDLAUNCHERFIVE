# Requests from the landscape and defence architect

The archetypes in `build/archetype/landscape/` and `build/archetype/defense/` work with what the planner sends today
(every param below is optional and defaults sensibly). These requests make the pieces compose the way the planner
intends them to.

## For the plan module

1. **Gatehouses are never placed in walled layouts (bug).** `Walls.placeGate` accepts a footprint only where every
   cell is `isAccess || isBuildable || PLANNED_ROAD`. A gatehouse straddles the wall line beside the road, and in a
   walled layout those cells are `Cell.WALL` (marked by `markLine` at founding; in ring walls, by the wall stretches
   just issued), so the check always fails. Seen: `settlementPreview -Pculture=temperate/walled -Pdays=400
   -Pterrain=hills -Pseed=2` raises eight wall stretches and two towers but no gatehouse; the main streets pass the
   wall through bare gaps. Accept `Cell.WALL` in the gatehouse footprint (it is the intended overlap of PLANNING.md §4).
   **Done** (integration 3): `Walls.placeGate` accepts `Cell.WALL` in the footprint; gatehouses are planned before the stretches.

2. **Wall stretches: leave towers and gatehouses to themselves (`keep`).** A tower `on_wall` and a gatehouse
   `over_road` stand on the wall line, overlapping a stretch's corridor. Whichever is built first, the applier fills the
   other's air with its blocks, so a stretch built through a tower would put its parapets and merlons inside the
   tower's chamber. `defense/wall` and `defense/palisade` read an optional **`keep`** param (int array of world
   rectangles `x0,z0,x1,z1`, inclusive, like the plaza's) and build nothing there; the walk then runs into the tower or
   gatehouse through its flank doorways (tested: `LandscapeTest.wallLeavesATowerOnItToTheTowerAndTheWalkMeetsItsDoors`).
   Please pass the footprints of the towers and gatehouses on the line in each stretch's `keep` (and add them to
   stretches already issued when a tower is planned later, or plan towers and gates before the stretches).
   **Done** (integration 3): every stretch gets `keep` with the footprints of the towers and gatehouses on it (they are planned first).

3. **Wall stretches that continue (`join_start` / `join_end`).** A long ring is cut into stretches of at most
   `road_plot_length`. Each stretch closes its walk with a pier at both ends unless told it continues, so today a
   person walking the wall goes down and up the stairs at every cut. Set `join_end: true` on a stretch whose last point
   is followed directly by the next stretch's first point, and `join_start: true` on that next stretch. Ends next to a
   `keep` rectangle open by themselves.
   **Done** (integration 3): `join_start`/`join_end` on stretches that run straight on into each other.

4. **The town side of a wall (`heart`).** The wall puts its crenellated parapet on the field side and its stairs and
   lanterns on the town side. It takes the town to be on the right of the way from the first point to the last (true of
   both `Walls.plan` and `Walls.reserveLine`, which lay their rings clockwise seen from above). If that may change,
   pass `heart` (int array `{x, z}`) and the wall uses it instead.
   **Done** (integration 3): `heart` on every stretch.

5. **`wall_height` for towers and gatehouses.** A tower on the wall and a gatehouse put their flank doorways at the
   wall walk, `wall_height − 2` above their floor (default 8 → 6). Please pass the `height` the stretches were given
   as `wall_height` on the towers and gatehouses of that wall.
   **Done** (integration 3): `wall_height` on towers and gatehouses.

6. **The heart plaza's height.** `Square.pave` gives the plaza `maxHeight` 4, which leaves no room for trees in its
   planters or a lamp's arm. Please pass `info.maxHeight()` (10), as `Square.furniture` does for furniture.
   **Done** (integration 3): the heart plaza gets the plaza info's max height (10).

7. **Sizes the catalogue now reports** (the planner reads them from `ArchetypeInfo`, so nothing needs changing unless
   the defaults in `DefaultCatalogue` are used in tests): `lamp_post` 3–5 (a lamp on its own paved island with
   flowers; a 1 × 1 lamp builds almost nothing), `statue` 5–7 (`Square.furniture` picks `clamp(3, min, max)` = 5),
   `well` 5–9, `gatehouse` 13–15 × 7–9 (flanking towers four wide either side of a five-wide road;
   `clamp(width + 4, 13, 15)` gives 13), `watchtower` 7–9 (`placeTower` takes 7), `wall` and `palisade` are linear.
   **Done** (integration 3): the catalogue matches. The heart well now shrinks from 5 to 3 where a road passes close (stilt towns).

8. **Square furniture rarely lands (observation).** In the ring towns I grew (desert and karst, `-Pseed=3`, 400 days)
   two lamps were placed and no fountain, statue or park; in the walled and crossroads towns none at all. The heart
   plaza keeps the ring two to five blocks in from its edge and its two edge rows clear for the planner's stalls,
   lamps, fountain and statue, and puts its own benches, notice board and corner lamps outside those; it would look
   its best with them. Junction plazas (`junction`) are complete on their own (centrepiece, benches, lamps).
   Open: square furniture placement was not looked at.

## For the npc module

9. **Points of interest the family registers:** `SEAT` (the bench's stair block; folk sit facing the POI's facing),
   `MEETING_POINT` (in front of the well, bell, fountain, statue, the plaza's middle, a bandstand), `NOTICE_BOARD` (the
   cell in front of the board), `GUARD_POST` (wall walks, tower tops, the gatehouse passage and gallery),
   `VIEWPOINT` (tower tops, wall walks, garden arbours, pond benches), `HEARTH` (fire pits on northern plazas and in
   tundra gardens), `GATE` (the gatehouse passage), `JOB_SITE` GUARD (the grindstone in towers and gatehouses),
   `STORAGE`, `DOOR`. All are reachable from the entrances (`LandscapeTest.folkCanReachWhereTheyGather`).
   Noted.

## For the styles (not edited: noted only)

10. Nothing blocks. Two things a style pass could improve: the steppe's tree log (`expanse:baobab_log|acacia_log`)
    falls back to grey-barked acacia offline, so its palisades read as stone; the desert and mediterranean `fence`
    roles are stone with no fence form, so lamp arms and windlass posts fall back to the frame's wooden fence (fine,
    but a `wood:` entry for small timber would be truer).
   Noted.
