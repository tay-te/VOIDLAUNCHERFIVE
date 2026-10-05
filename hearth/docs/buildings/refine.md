# Refine: weak buildings, palettes, light by design

Stage B "refine" owns the phase 2 families' archetypes and kits, the style JSONs and `build/style/**`, and the speed of
`build/check` and `build/blueprint`. This page says what changed and the tools that came with it. Components live in
`build/kit/refine/`; debugging tests in `src/test/java/dev/voidmc/hearth/build/refine/`.

## Light by design (no invisible light)

The light pass's last resort is vanilla's invisible `light` block. Over the catalogue (71 archetypes x 14 cultures x 6
terrains x 2 seeds, 11,928 blueprints) it put up **about 9,300** of them; it now puts up **about 1,250** (the cathedral
alone went from 2,784 to about 125, the temple from 656 to 11, the warehouse from 630 to 2, the windmill from 526 to
18, the manor from 421 to 30, the cliff dwelling from 92 to none). Almost all were *pockets*: sealed voids under roofs, inside spires and turrets, over
knee walls and lofts nobody could reach, which the rule (rightly) keeps from going dark. The fixes, from the most
general:

| component | what it does | used by |
|---|---|---|
| `Voids.seal(d, box, core)` | fills the empty cells of a box that no way of air joins to the box's sides, top or (through an open floor) what is under it. Air passes empty cells and whatever folk walk, climb or look through (doors, hatches, gates, ladders, panes, bars, fences, carpets, lamps), never full blocks, stairs or slabs: a room with a door or a window is never a void, and a filled void was never reachable. | crowns (every spire, dome, onion, cap), the windmill's cap, the pagoda's top roof |
| `Voids.finish(d)` | the last stroke of a building: `seal` over the whole plot, a rubble core where the culture builds in stone, boards where it builds in timber, in the ROOF stage | every building archetype of the phase 2 families (after the final `Groundwork.clear`) |
| `Hall.sealedKnee` (in `Hall.roof`) | a knee attic (two rows or less under a pitched roof) that no stair reaches is opened to the room below (`openLoft`): a store open to its rafters instead of a dark loft | the bakery, tailor, warehouse, watermill, granary, storehouse, smithy, carpentry and every other `Hall` building |
| `Lofts.ladder(d, x, z, yFrom, yTo, toWall, roof)` | a ladder against a wall up through a hatch into a loft a stair cannot reach (checks the wall behind every rung and headroom under the roof) | the manor's garret, now a box room |
| `Lamps.byWalls(d, cx, cz, floorY, n)` | lanterns hung under the joists against the walls of a narrow round room, looking out along the axes from the middle | the windmill's floors |
| `Lighting.lamp` (existing) | a lantern stood where the light belongs: the miller's lamp in an open windmill cap, the library's glazed roof lantern (it glows over the roofs at night), the cliff dwellings' roof terraces | windmill, library, cliff dwelling |
| `Lighting.wheel` / `sconce` / `chandelier` | (existing kit) | the cathedral, the temple's dome hall |

Designed lighting where the pass was guessing:

- **Cathedral**: no flat plank ceilings any more. The nave, the transept and the choir rise open to their roofs, the
  aisles to their lean-tos; the crossing rises open into its tower (a lantern lit by the belfry's lights) or its drum.
  A wheel of four lanterns hangs on a long chain from the ridge in every bay of the nave and the choir and over the
  crossing (`Lighting.wheel`, hub at the aisle arcade's spring), a bracket lantern on every pier faces into the nave,
  and a lantern hangs from the lean-to in every bay of the aisles. Chains hang only from a solid block.
- **Temple** (desert dome): the prayer hall rises into the drum, lit by the drum's lights, under a vault at the drum's
  head with a wheel from its keystone; the dome over it is a sealed shell (a core).
- **Windmill**: a lantern under the joists on every floor; the bonnet's hollow boarded shut.
- **Manor**: the garret is reached by a ladder from the landing and furnished as a box room.

What is left (about 1,250 over the catalogue): the town hall's tower (149), pockets in the stave cathedral and the
Gothic cathedral's alpine roof walks (124), town wall and terrace ground cells where the street's lamps do not reach
(101, 52), the quarry's pit (67), bell tower stages (63), the crawl spaces under stilted tropical and bayou houses,
banks, schools and chapels (a room two high where no lamp may hang), and single pockets here and there.
`HiddenLightsTest` lists them spec by spec.

## A cheaper light pass

The cathedral's light pass (~165 ms cold, ~106 ms warm) is down to ~50 ms warm with every blueprint digest unchanged
(`BlueprintDigestTest`): probes remembered per emitter and reporting only the cells that need light, room questions
flooded only down to a room's light, per-cell loss/shape/edge arrays in the flood, the draft painted into the grid,
blocks classified once per kind for walking, one pass over the drawing in setup, the next round reusing the grid and the
light, fast iteration in `Draft.forEach` and the shape resolver. With the cathedral's voids gone the pass has far fewer
pockets to answer for. The rule is unchanged.

## Palettes

- **Bohemian red tile**: the temperate town's tier 3, burgher brick, Norman stone and the manorial manner roof in red
  clay tile (bricks with weathered granite patches); slate stays as a fashion. The wisteria culture's roofs likewise.
- **Thatch**: aged thatch (stripped oak with a ridge and moss) in place of the bright hay strip; hay-only thatches get
  straw stairs.
- **Northern**: silver logs with tarred dark oak and white-painted trim.
- **Bayou**: weathered cypress under shakes or rusted tin, no green copper roofs or red trim.
- **Wisteria (Woodland Glades)**: a stone town wall for its works; the scholar's house European (no flared eaves,
  lattice or zen garden); its pagoda is now a **garden tower**: the town's bell tower (broach, octagon or pyramid of
  tile) in a walled flower garden with benches and lamps (`Pagoda.gardenTower`).
- **Old forest**: keeps its honey boards whatever the territory's timber.
- **Gables**: a fashion's fabric (walls, gables, quoins, frames) survives its parent's tier overrides, so a brick
  town stays brick as it prospers (no white walls under ochre gables). Sub-style fashions may carry tiers.
- **Corner stone**: quoin toothing only on walls eight long (a stone cottage's front is no longer half quoins).
- **Bohemian village boards**: the upper storeys' boards are weathered spruce with some dark oak low down, not a box of
  near-black dark oak. The warehouse is never a village building (it is a burgher's: plaster or brick under red tile).

## Weak buildings

- **Warehouse**: a burgher's warehouse of plaster or brick under red tile in a temperate town, its stepped gable to the
  street with a loading door at every floor and the hoist beam out of the gable under the verge (it used to lie on the
  roof), every floor reached by the stair (the unreached attic is now open to the top store's rafters).
- **Tailor**: two storeys (a third only in a city's crowded rows), a steep gable to the street (no hipped lids), no knee
  wall to stretch the front; the **weavers' windows**: a band of lights along the workroom's front and back between the
  posts with a sill under it; the dyed cloth drying on a line high between poles over the yard wall.
- **Hunters' lodge**: in the timber cultures a **log lodge**: round logs course on course (`LogWalls.lay`), notched
  corners whose ends stand out on alternate courses (over head height), a steep gable turned to the clearing carried
  on over the porch; a little roof over the smoking rack.
- **Builders' yard**: every job site at a real workstation: the joiner's bench (crafting table) at the trestles, the
  rigger's bench at the crane's foot, a stone banker (stonecutter) beside the trial wall, as well as the shed's benches,
  saw and grindstone.

## Tools

- `HEARTH_HIDDEN=<archetype or 1> ./gradlew test --rerun --tests '*HiddenLightsTest*'`: every hidden light, spec by spec,
  as preview params, with why, where, what stands under it and over it (`HEARTH_HIDDEN_ALL=1` for every spec).
- `HEARTH_PROBE=archetype,culture,seed,frontage,depth[,k=v;...] HEARTH_PROBE_FIND=<block>`: the blocks a draft uses.
- `HEARTH_WARM_TIMING=<archetype>`: the warm cost of draw, light pass and compile.
- `HEARTH_DIGEST=1 HEARTH_DIGEST_OUT=<file>`: digests of every blueprint, to refactor machinery without changing output.
