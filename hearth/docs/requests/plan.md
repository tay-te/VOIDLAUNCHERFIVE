# Requests from the plan module

The planner works around all of these inside `dev.voidmc.hearth.plan`; none blocks it.

## For the api / ARCHITECTURE.md (coordinator)

1. **Party wall sides.** §6.1 names `party_left` / `party_right` but not whose left. The planner uses **the building's
   own left and right, looking out of its front door along `facing`** (left = `facing.getCounterClockWise()`). Please
   state this in §6.1 so the build module's attachable archetypes agree. The same convention applies to the planner's
   `corner_left` / `corner_right`.
2. **Infrastructure geometry.** Please copy PLANNING.md §7 (the dense-centreline rule, the corridor rule, odd widths,
   bridge abutments at bank level with the deck at `deck_y`, `boardwalk` bridges, terrace `retain_*` flags) into §6.1,
   so the infrastructure architect builds to exactly what the planner reserves. The planner also issues one-wide `path`
   roads to set-back doors and three-wide `path` farm tracks.
3. **Overlap allowances.** PLANNING.md §4 lists the overlaps the planner makes on purpose (building on a terrace of the
   same footprint, `stacked_on`, plaza furniture inside the plaza with the plaza's `keep`, gatehouse `over_road`,
   towers on walls, `replaces`, walkways overhead). Appliers and the sim's "player built over this plot" check should
   treat these as intended. Proposed: a sentence in §5.3.
4. **`PlotView.replaces()`** (optional): the planner writes `replaces` into the spec's params as well as the project's
   field, because `PlotView` does not carry it. A `PlotView#replaces()` would let it drop the param.

## For the sim module

5. **Several calls per free builder.** Roads come first: planning one need often queues a lane and then the houses
   along it, handed out one per `next` call. When `next` returns an `infra/*` project, please call again in the same
   step (the planner caps itself: queued roads are a handful at most).
6. **Rebuilds (`replaces`).** The planner counts a `DEMOLISHING` plot as gone and the new plot's beds as planned. It
   expects the old plot to stand until the new one's construction starts, then to be removed (or marked demolished).
   It drops a queued rebuild whose old plot is no longer `BUILT`/`ABANDONED`.
7. **Biome at the heart.** Signatures read `TerrainProbe.biome(center)`; the generator-backed probe should return the
   Expanse biome keys (`expanse:jade_karst`, ...) so Expanse lands get their signature settlements.
8. **Founding cost.** `found` surveys the site and routes the skeleton with A\*: 50–150 ms per settlement. Budgeted in
   §5.1, but it should run off the server thread during pre-history as planned.
9. **Dev layout switch.** For in-game dev worlds: `LayoutChooser.FORCED` (a thread-local) forces a layout; the sim's dev
   harness could set it from a system property such as `hearth.dev.plan.layout`.

## For the build module

10. **Catalogue habits the planner relies on** (all through `ArchetypeInfo`, none hard-coded): `upgradesTo` chains
    cottage → rowhouse/shophouse/townhouse → tenement (growing up); `STACKABLE` + `HILLSIDE` on cliff dwellings
    (pueblos, karst sills); `ATTACHABLE` on rowhouses, townhouses, tenements, shophouses (rows with party walls);
    `CENTRAL` on market stalls, wells, fountains, statues, lamp posts (square furniture); `OUTSKIRTS` on farms, barns,
    sawmills, quarries, windmills; `WATERFRONT` on fisheries, watermills; `ON_WATER` on stilt houses. Cathedral and
    temple are planned on the chapel's site and close (`replaces` the chapel), so their size can exceed the chapel's.
11. **Box blueprints for tests.** `plan/testing/CatalogueDouble` + `BoxBlueprint` implement `BlueprintService` with the
    §6.1 infos and plain boxes; the sim's tests can use them until the real catalogue lands.

## For the gametest entrypoint (coordinator)

12. A client tour of planned settlements (`gametest/plan/SettlementTour`, flying to a dev settlement and photographing
    it) needs an entry in the gametest `fabric.mod.json`. Not written yet: it needs the sim module to found settlements
    in a dev world; the offline previews cover the planner meanwhile.
