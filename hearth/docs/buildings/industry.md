# Heavy workshops: the chains' industry

The industry architect's archetypes (LIVING_WORLD.md §7.5, §8.5): seven heavy workshops and the caravan yard,
registered in `build/archetype/IndustryArchetypes.java`. Classes live in `build/archetype/work/` and
`build/archetype/commerce/`, the shared kit in `build/kit/industry/`, names and sign texts in
`assets/hearth/lang/en_us/build_industry.json`. Tests beyond the shared invariants:
`src/test/java/dev/voidmc/hearth/build/industry/IndustryArchetypesTest.java`.

Each one reads from the street as what it makes: a smoking stack, a bottle kiln, a ship on the stocks, log decks by
the track, clamps breathing smoke, white salt pans, wagons under canvas. Every archetype reads `variant`, and `roof`
(a `RoofForm` name) where it has a choice of roof; the extraction params (`site`, `kind`, `rock`, `face`) are accepted
by the sited ones and ignored.

## The catalogue

| id | frontage × depth | height | jobs | tier | flags | max |
|---|---|---|---|---|---|---|
| `work/logging_camp` | 12–17 × 12–17 | 7–11 | FORESTER, HAULER | hamlet | OUTSKIRTS | |
| `work/charcoal_kiln` | 11–14 × 10–13 | 6–9 | CHARCOAL_BURNER | hamlet | OUTSKIRTS | |
| `work/brick_kiln` | 10–14 × 10–14 | 9–15 | KILN_WORKER | village | OUTSKIRTS | |
| `work/glassworks` | 10–14 × 12–16 | 10–16 | GLASSMAKER | village | | |
| `work/smelter` | 10–14 × 10–14 | 12–20 | SMELTER | village | OUTSKIRTS | |
| `work/salt_works` | 11–17 × 12–17 | 6–10 | SALTER | hamlet | OUTSKIRTS | |
| `work/shipyard` | 11–17 × 15–25 | 9–15 | BOATWRIGHT | village | WATERFRONT | |
| `commerce/caravan_yard` | 15–21 × 15–21 | 7–11 | TRADER, HAULER | town | | 1 |

Sizes are larger than `DefaultCatalogue`'s templates (see `docs/requests/b-industry.md`); the registered infos replace
those whenever the build module is present.

## The buildings

- **Logging camp.** An open landing of trodden earth: log decks piled pyramid-wise on their skids, butts to the track;
  a saw pit (trench boarded at its lips, a ladder down, a trunk on trestles, a gantry lowering the next log); the
  bunkhouse at the back, a notched log cabin in the timber and marsh cultures (the culture's own walls elsewhere) with
  bunks, a table and a smoking chimney; a smithy lean-to on its gable with anvil, grindstone and axe rack; cordwood,
  chopping block, stumps, a cart loaded with a trunk.
- **Charcoal kiln.** One clamp burning (a turfed earth mound with soot streaks, moss and green branches at its foot,
  smoke from the crown), a second going up (billets on end round a pole, the covering half on) or the last raked open
  into a black heap; cords of billets between stakes; the burner's A-frame bark hut (a round mud hut in the thatch
  cultures), barrels of charcoal by the track. Param `clamps` (1 or 2).
- **Brick kiln.** A vaulted brick chamber with firemouths under hoods and a banded, corbelled chimney (the bottle that
  reads from across the valley); a pentice for the stokers, an open drying shed with hacks of green brick, a pug mill
  with its sweep, clay heap and pallets of fired brick. Mud brick with a brick throat in the earthen cultures.
- **Glassworks.** A tall hall gable-on to the street, wide doorway; inside, the round brick furnace with glory holes on
  three sides and its flue rising through the ridge as a tall stack; the annealing oven, blowpipe racks, chests of
  glassware; in the yard racks of sheet glass, sand and cullet heaps, cordwood.
- **Smelter.** Forest and stone cultures: a battered rubble blast furnace with quoins, a glowing tapping arch, a
  charging gallery reached by a trestled ramp, a tall banded stack, a bellows house (water wheel where water lies behind),
  a casting shed with pigs cooling, ore, charcoal and slag heaps. Dry lands and tropics: clay shaft furnaces with
  goatskin bellows under a ramada or thatched shade. Params `furnace` (`tower`/`clay`), `wheel`.
- **Salt works.** Fields of stone-kerbed pans flush with the ground, flooded, drying and crusted; white salt cones; the
  boiling house (a sack store in the sun cultures); a graduation tower of brushwood on its frame in the timber cultures
  where the plot is large. Param `tower`.
- **Shipyard.** A slipway into the water behind, a hull on the stocks (keel blocks, stem toward the street, the fore part
  planked, the after part bare frames), shores and a staging; a crane over the slip head; timber racks and a keel-timber
  deck; the two-floor sail loft with chandlery below, sail cloth above, a hoist in its gable; steam box and pitch
  kettle. On dry ground the ways stop at the plot's end.
- **Caravan yard.** A roofed gateway with a hanging sign, a walled or fenced yard; a warehouse with cart doors, barrel
  racks and a hoist; pens with troughs, hay and a lit shelter (fenced on the plot's edge too, a gate from the yard into
  each run and a gate through each divider, the shelter a block higher where the land beyond rises beside it); the
  trader's booth (a lintel over its door where the culture's doors carry a lattice transom) with lectern and counter; wagons
  under canvas tilts or loaded open; a hitching rail with `HITCHING_POST`s.

## Points of interest

Job sites stand at the workstations (each furnace mouth, the saw pit's two sawyers, the decks, the slip's staging, the
counter). `Pois` (kit) gathers places while a yard is drawn and registers only those still standable at the end; a
works left with fewer than two job sites or without a store gets them round its first fallback cell (a barrel set
against something solid), so every works has two places at least, one per job, and a store. The caravan yard registers
`HITCHING_POST`s, `GATE`, `COUNTER` and `DOOR`.

Doorsteps: `Works.lampPost` sets a yard light on the nearest free cell that is not a door's outside cell, and
`Works.keepDoors` (called last) clears anything a later heap or prop put in front of a door, and the hillside where
the levelling left it.

## Light

Rooms are lit with `Lighting.room` (bunkhouse, hut, loft, booth, warehouse, the pens' shelter); yards carry the
culture's lamp posts. All eight pass the light rule in `BlueprintInvariantsTest` on every terrain.

## Previews

`docs/buildings/industry_preview.gradle` is an init script adding `industryShots` (close-ups and culture grids) without
touching the shared build:

    ./gradlew -I docs/buildings/industry_preview.gradle industryShots -Pids=work/smelter -Pstyle=temperate -Pseeds=2 -Pterrain=hills
    ./gradlew -I docs/buildings/industry_preview.gradle industryShots -Pids=work/charcoal_kiln -Pstyle=all -Pmode=grid -Pscale=12

Output goes to `build/previews/industry/`. The renderer is `gametest/industry/IndustryShots`.

## Known weak points

- Bunks in the logging camp and the burner's bed are furniture only (`beds` 0 in the info): folk do not live there.
- The shipyard on dry ground builds the hull on the yard without water; the planner places it WATERFRONT.
- Steep plots deeper than the planner's terrace limit leave the hillside above the plot's height standing at the back.
