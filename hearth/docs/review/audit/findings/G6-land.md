# G6 audit: farms, infrastructure, extraction, deep

Auditor: G6. Worktree `hearth-audit` @ 7c8a23b3 (nothing committed, no code changed). Paths below are relative to
`hearth/src/main/java/dev/voidmc/hearth/build/`. Images are in `findings/g6img/` (47 files, 4 MB).

## How this was looked at

- The audit sheets and cutaways for all 23 archetypes, plus `metrics.csv` per case.
- The sheets can't be trusted for half of this group. Infrastructure draws a stub on a bare plot, the dock's demo course on `coast` never reaches water, and deep buildings and extraction on the synthetic `cliff` turn into 20-block pillars or see-through pits. So I rendered each family the way it is actually built:
  - `infraShots`: bridge in 8 cultures, dock in 6, walkway in 5, stairs, terrace and road in 4 each.
  - `infraCloseups` in real planner towns: maritime coast (docks), mediterranean steep (stair streets), alpine/mountain steep (terraces), temperate river (roads).
  - `deepPreview` on a realm floor: dwelling, forge, market and glow garden in 4 cultures, the hall in 7, the lift in 2. Also `deepTown` (temperate).
  - `extractionPreview` on hills with sections and campaigns, plus `blueprintPreview` of every extraction archetype on hills.
  - `blueprintPreview` of terrace field, field and orchard on slope and hills.
- Walk maps (`reach=`/`slice=`) for: hall, shaft, lift, orchard, mine, field, dock, terrace field, forge, dwelling.

## Grades

| archetype | grade | one line |
|---|---|---|
| infra/bridge | B+ | the best of the group: arch, covered, trestle and moon bridges read as real builds |
| infra/road | B | good kerbs, setts and lamps; mediterranean quartz kerbs zigzag; karst checker noise |
| infra/stairs | B | in real towns: cheeks, landings and retaining walls work; the demo shot is useless |
| farm/field | B- | a believable worked field, but one plan everywhere and blob trees |
| farm/pasture | B- | shelter, trough and shade tree; plain but right; same plan |
| infra/dock | B- | the stone quay is good; the timber pier is cluttered with full-log posts |
| infra/terrace | B- | a fine retaining wall; the fill's sides are left as raw dirt cliffs |
| extract/quarry_pit | B- | benches, spoil, rig and shed; Bohemia gets the wrong rig |
| extract/clay_pit, sand_pit | B- | compact and legible; the spoil is a box-shaped loaf |
| deep/glow_garden | B- | a good layout; same plan in all 14 cultures; the shed is a box |
| deep/hall | C+ | good per-culture plans, but the basilica has a sealed furnished room and its towers can't be reached |
| deep/market | C+ | arcades work; the wing roofs are flat plank strips; the court is 3 wide |
| farm/orchard | C+ | the crowns merge into one green mass; the picking ladder leads into leaves |
| farm/terrace_field | C+ | real terraces on slope, but no hut, chest or composter on its own terrain |
| extract/plantation | C+ | rotation reads; karst coppices cherry; blob crowns |
| extract/shaft | C+ | basic shaft house; the spoil is a cobble wall |
| deep/dwelling | C | the same block in 14 cultures; 6 beds wall to wall; stair without a rail |
| deep/lift | C | a plain one-material tower; the winding house and headframe look like a top hat |
| extract/mine | C | the drift is good; the surface is a cobble lump and a crafting table |
| infra/walkway | C (unverified) | only the 4-post stub can be rendered; never seen in a town |
| farm/paddy | C- | flat water maze with no bunds; no shed or composter in 8 of 12 cases |
| deep/forge | D+ | an identical deepslate box in 14 cultures, walls and roof in one block |

Distribution: A 0, B 11 (B+ 1, B 2, B- 8, counting clay and sand pits separately), C 11, D 1 (23 archetypes).
**Blockers: 1** (the deep hall basilica in 4 cultures). Majors: 20. Castles are not in this group.

---

## Farms

### farm/field: B-
- **major: one plan reskinned.** Every culture has the same 12x14 layout: the shed in the back right corner, a tree in the front left, the scarecrow in the middle, channels in the same columns. Only the boundary (rails, wall, hedge) changes. `culture_likes` is 8.
  - Cultures: all. Image: `g6img/farm_field_cultures.webp`.
  - Fault: `archetype/farm/Field.java` (layout constants).
  - Fix: pick the furrow direction, the shed corner and the channel spacing by seed and culture habit. Use long narrow strips (Bohemian *lány*) where the plot is long, and square plots with a raised bund in the south.
- **major: trees are blobs.** The shade or fruit tree is a 5x5x4 leaf block. In karst the cherry crown has a hole and reads as a pink ring. In tundra the trunk is hidden.
  - Cultures: all, worst in karst and tundra. Image: `g6img/farm_field_cultures.webp`, `g6img/farm_pasture.webp`.
  - Fault: `kit/homestead/Trees.round` and `Trees.shade`.
  - Fix: irregular crowns (offset lobes, a 1-2 block lean, branches as logs on their side, leaves only where a branch ends). Never leave an empty core in the top layer.
- **minor: bad flights on slope.** The headland steps through the banks hit the bank lip in 5/84 cases (moorland slope s1 `bad_flight@7/-4/9`).
  - Fault: `Field.java` path steps / `kit/homestead/Terraces`.
  - Fix: end every flight on a cleared landing two high.
- **minor: tiny shed.** The field shed is a 3x3 lean-to whose roof is two plank rows. It reads as a kiosk.
  - Fault: `kit/homestead/Sheds.lean`.
  - Fix: on fields 14+ deep, use `Sheds.small` (gabled) with a door, or add a hay rick beside the lean-to.

### farm/orchard: C+
- **major: crowns merge into one mass.** At spacing 5 with radius-2 crowns, all the canopies touch, so the rows can't be read and the orchard looks like a hedge block. In karst the cherry crowns form one pink lump. Real orchards show clear grass alleys and trunks in a grid.
  - Cultures: all. Image: `g6img/farm_orchard_cultures.webp`.
  - Fault: `archetype/farm/Orchard.java:77` (spacing), `kit/homestead/Trees.round`.
  - Fix: spacing 6-7 for size >= 1, or radius 1.5 crowns (drop the r=2 ring on alternate trees). Raise crowns so the trunk shows two blocks clear. Mix sizes 0/1/2 by row age.
- **minor: dead ladder.** The picking ladder is 2 high against a trunk and its top runs into leaves, with nowhere to step off (65/84 cases, `dead_ladder@2/1/9`).
  - Fault: `Trees.ladder` / `Orchard.java:139`.
  - Fix: lay the ladder flat on the grass (two ladder blocks on a slab), or place it only where the crown is two above the ladder top.
- **minor: same plan everywhere.** All 14 cultures share the layout, with the press shed in the same corner.
  - Fault: `Orchard.java`.
  - Fix: vary the shed corner, the row direction and the boundary type by seed and culture.

### farm/paddy: C-
- **major: not a real paddy.** A flat sunken water basin with a maze of grass strips. There are no raised earth bunds to walk on and nothing at the field's edge. A real paddy is levelled cells held by walkable mud bunds a block above the water, stepping down the hill.
  - Cultures: karst, tropical (the only ones that build it). Image: `g6img/farm_paddy.webp`.
  - Fault: `archetype/farm/Paddy.java`.
  - Fix: cells 4-6 wide, each bounded by a 1-wide bund of mud or packed mud at water+1. On hills, terrace the cells with a 1-block drop and a spillway notch.
- **major: the shed and composter are missing in most cases.** Only 4 of the 12 suited cases have a workstation. The other 8 have no compost bin and no chest, so the field has no store.
  - Cultures: karst, tropical; flat, slope and hills. Image: none (metrics).
  - Fault: `Paddy.java:199` (`Sheds.lean` returns without a fallback).
  - Fix: when the shed doesn't fit, put the composter and chest at the head of the bund path (the way GlowGarden does).

### farm/pasture: B-
- **minor: one plan.** The same layout (shed at the back right, tree at the front left, trough in front) in every culture. Image: `g6img/farm_pasture.webp`.
  - Fault: `archetype/farm/Pasture.java`.
  - Fix: vary by seed; add a gate path worn into the grass and a water source that fits the culture (a stream ford or a well sweep).
- **minor: tree shapes.** The karst cherry is a square with a hole, and the desert acacia is a lone blob on bare sand. Fault: `Trees.shade`. Fix: as for the field.

### farm/terrace_field: C+
- **major: no hut, chest or composter on its own terrain.** On slope (28/28 cases) and hills (14/28) the tool hut is skipped silently, because the lowest band doesn't fit a 4x4. Those cases have no composter and no chest, though the info says the shed's chest is the store.
  - Cultures: all. Image: `g6img/farm_terrace_field_slope.webp` (temperate and karst slope: no hut).
  - Fault: `archetype/farm/TerraceField.java:250-266` (`hut()` returns).
  - Fix: try every band and both ends. Failing that, cut a 2x3 niche into the highest retaining wall with the composter and chest under a slab roof.
- **major: the hut reads as a booth.** A 4x3 lean-to, 4 tall, open to the back (away from the gate). From the street it is a closed tall slab, like a privy. The javadoc says "a little gabled shed" but the code draws `Sheds.lean`.
  - Cultures: all where it appears. Image: `g6img/farm_terrace_field_flat.webp`, `g6img/farm_terrace_field_hut.webp`.
  - Fault: `TerraceField.java:280`.
  - Fix: use `Sheds.small` with a gable and a door facing the path, or open the lean-to toward the path.
- **minor: bad flights.** In 28/84 cases (`bad_flight@16/-3/4`, temperate slope s1) the steps between terraces end against the next retaining wall's coping.
  - Fault: `kit/homestead/Terraces` steps.
  - Fix: notch the coping above the top tread.
- **minor: plain on flat land.** It is a sunken strip, with half the northern plot left as weeds. Acceptable, since the planner should put it on slopes.

## Infrastructure

(The audit sheets show only the bare-plot stub. Judged from `infraShots` and real planner towns.)

### infra/bridge: B+
- **minor: covered bridges hump.** The covered bridge climbs five steps at each end into the house (`g6img/infra_bridge_covered_zoom.webp`, `infra_bridges_1.webp`). Real covered bridges are level, with the house at road height on raised abutments. A humped approach also gives carts and NPCs a staircase.
  - Cultures: temperate, alpine, northern, karst.
  - Fault: `kit/infra/Spans` (the covered make's deck profile).
  - Fix: build the covered make's abutments up to `deck_y` and ramp the approach road (Roadbed) instead of stair-stepping the deck.
- **minor: plain covered sides.** The temperate and alpine covered sides are dark board walls with three windows; the plaster panel over the board rail promised in `infra.md` isn't visible. Fault: `Spans` (covered). Fix: lime plaster infill above a 1-high board rail, with corner bracing.
- **minor: steppe slope void.** A sealed void and fall edge at an abutment (steppe slope s1, `fall_edge@14/-7/33`). Fix: fill or rail it.

### infra/dock: B-
- **major: timber pier cluttered.** Full-block log mooring posts stand inside the 3-wide deck, about 8 of them on a short pier, along with barrels and lanterns on posts. Walking it is a slalom, and from above it looks like scattered stumps.
  - Cultures: timber-pier cultures (maritime village shown). Image: `g6img/infra_dock_timber_posts.webp`, `closeup_maritime_coast_s3_d300_dock16.webp`.
  - Fault: `archetype/infra/Dock.java:139-143` and `:209-227`.
  - Fix: piles outboard of the deck edge (a stringer row outside the walkway), posts as fences or walls, and barrels grouped at the landing stage only.
- **major (verify): may not reach the shore.** The same pier seems to leave a water gap at its landward end. No steps down from the cobbled quay are visible.
  - Fault: `Dock.java` steps from `points[0]`.
  - Fix: check the first course column is on land at road level and that the flight lands on the deck. Add a BlueprintChecks rule: the DOCK point is reachable from the street.
- **major (test gap): never measured over water.** The `Demo` course on `coast` runs over land, so the audit, the sheets and the tests never see the dock over water. Every audited dock is a plank strip sunk into grass. Image: `g6img/infra_dock_demo_on_land.webp`.
  - Fault: `kit/infra/Demo`.
  - Fix: on `coast` and `river`, run the demo from the land column out over the water.
- **minor: bad flights and missing stores.** The steps down from the road end at a wall in 13/84 cases (`bad_flight@11/-2/5`). 24/84 cases have no store or workstation; the stone quay has no STORAGE.
  - Fault: `Dock.java`.
  - Fix: add a bollard-side chest or a net store to the quay.

### infra/road: B
- **minor: mediterranean kerbs zigzag.** Smooth-quartz kerbs step as full white blocks at every grade change, making a jagged stark zigzag down a hill. Image: `g6img/infra_road_med_quartz_kerbs.webp`.
  - Fault: `kit/infra/Dress` (kerb) and `Roadbed` (risers).
  - Fix: on risers, use stair or slab kerb pieces matching the carriageway step. Weather quartz kerbs with a calcite or diorite mix.
- **minor: karst lanes are noisy.** The karst lane alternates white and grey slabs in a fine checker (`sheets/infra/infra_road.webp`). Fault: `LineMat` mix scale. Fix: larger patches, mostly one stone.
- **minor: downhill fill shows grass and dirt.** On hills the road sits a block above the grass beside it. In the country the fill is only faced over two blocks, so one-block fills read as a causeway with a raw edge. Fault: `Roadbed` earthwork. Fix: grade the verge with a slab slope one block out.

### infra/stairs: B
- Real town (`closeup_mediterranean_steep_s2_d250_stairs2.webp`): white treads, cheeks, landings and parapets read well.
- **minor: the demo shot is useless.** The `infraShots` steep demo runs a short flight across the slope (41x20). Fault: `kit/infra/Demo`. Fix: run the demo up the fall line.

### infra/terrace: B-
- **major: raw dirt cliffs at the sides.** The fill's sides and the back cut are left as vertical faces of raw dirt, 3-5 high. Only the street face is walled. Image: `g6img/infra_terrace_zoom.webp`, `infra_terraces.webp`.
  - Cultures: all.
  - Fault: `archetype/infra/Terrace.java` (the `retain_*` default rule).
  - Fix: face any side whose fill or cut stands 2+ above the neighbour's ground, or batter it as a turf slope (one block out per block up).
- **minor: monolith on narrow plots.** On 5-wide plots the front wall is a lone pilastered slab, which looks like a monument. Fix: below 7 wide, drop the blind arch and use a plain coped wall.

### infra/walkway: C (unverified)
- **major (test gap): never rendered for real.** Without `points` it is a box on four posts (`g6img/infra_walkway_stub.webp`), and it has never appeared in an offline town (see `infra.md` known limits). The art can't be judged.
  - Fault: `kit/infra/Demo` / `archetype/infra/Walkway.java`.
  - Fix: give `infraShots` a two-facade scene (two dummy walls 9 apart), so the stone flying-arch, timber gallery and pavilion forms can be reviewed.
- **minor: no access on the stub.** The stub's top has fall edges and no access (84/84, max 2). Harmless if the planner always passes points.

## Extraction

### extract/mine: C
- **major: the surface is a cobble lump.** On hills the visible mine is a 10x31 plot with a lump of cobblestone (the headwall and spoil), a crafting table, a barrel and a lamp. The timbered portal under its board hood isn't readable from the street. There is no miners' lean-to roof, ore heap or cart.
  - Cultures: all (`culture_likes` 13). Image: `g6img/extract_mine_yard.webp`, `extract_mine_hills_section.webp`.
  - Fault: `kit/extraction/Pithead` (yard), `kit/extraction/Drift` (portal), `kit/extraction/Spoil`.
  - Fix: make the portal the yard's focus: a 3-wide timber set with a pent hood two blocks proud of the headwall, a lantern and a sign. Build the headwall of dressed or dry-stone courses with a coping, not a cobble mass. Add a lean-to with a roof, an ore bin of the speciality ore, and a minecart on the rail at the portal.
- **minor: empty stalls.** The stalls along the drift are empty in 56/84 cases. Image: `extract_mine_hills_section.webp` (section).
  - Fault: `Drift`.
  - Fix: a pick on a frame, an ore pile, a barrel and a lantern in alternate stalls.
- **major: unguarded cutting.** The cutting's sides drop 4+ with no rail or wall (84/84 cases, up to 100 cells, `fall_edge@7/0/11`).
  - Fault: `Drift` cutting.
  - Fix: carry the dry-stone wing walls one course above ground as a parapet, or add a fence.

### extract/shaft: C+
- **major: the spoil heap is a box.** The heap next to the shaft house is a rectangular cobblestone wall-block, not a mound. Image: `g6img/extract_shaft_house.webp`.
  - Fault: `kit/extraction/Spoil` at small `top` (rounding makes a flat-topped 2-high box).
  - Fix: for `top <= 3`, use slabs and stairs on the flanks, mix gravel, tuff and coarse dirt, and keep one-block crest variation.
- **minor: sealed void in the roof.** A 28-cell sealed space inside the shaft house roof (`sealed_void@5/5/6`), and a cramped bottom station (`cramped_room@3/-11/7`).
  - Fault: `archetype/extract/Shaft.java`.
  - Fix: open the house ceiling, so the roof is visible from inside and the space isn't a void; widen the station to 3x3.
- **minor: plain house.** Four posts and a small roof over a collar, the same plan in 8+ cultures. Fix: a weatherboarded shaft house on one side with a door, and a culture roof (`Roofs` habit).

### extract/quarry_pit: B-
- **minor: Bohemia gets a derrick.** The temperate (Bohemian) quarry gets a gallows derrick, not the treadwheel crane in the doc: `Pithead.rig` falls back when the treadwheel footprint doesn't fit. Image: `g6img/extract_quarry_hills.webp`.
  - Fault: `kit/extraction/Pithead.java:212-250`.
  - Fix: reserve the treadwheel footprint before laying the shed and stacks on plots 17+ wide.
- **minor: cliff renders unreadable.** On the cliff sheet the pit renders see-through with loose blocks (`g6img/extract_quarry_cliff_sheet.webp`). It looks right on hills. This is the synthetic cliff, not the build.
- **minor: unguarded benches.** Bench edges have up to 78 fall-edge cells. That is right for a quarry, but the rim fence should be continuous along the street side.

### extract/clay_pit, extract/sand_pit: B-
- **minor: the spoil is a loaf.** The stripped topsoil tip is a hard-edged coarse-dirt loaf. Image: `g6img/extract_clay_pit.webp`. Fault: `Spoil`. Fix: as for the shaft.
- **minor: unguarded faces.** Faces drop up to 53 cells with no fence on the yard side. Fix: fence the yard edge of the face.

### extract/plantation: C+
- **major: karst coppices cherry.** The karst woodlot is cherry blossom blobs. It reads as a park, not a timber woodlot (karst forestry is fir, pine and bamboo).
  - Image: `g6img/extract_shaft_clay_plantation.webp`.
  - Fault: `archetype/extract/Plantation.java` (uses the garden tree).
  - Fix: choose the timber tree from `Role.TREE_LOG` habits (karst: spruce or bamboo coppice; tropical: jungle), never the ornamental garden tree.
- **minor: blob crowns.** Grown-coupe crowns are leaf blobs. Fix: as for `Trees`.

## Deep (judged on the realm floor with `deepPreview`/`deepTown`; the cliff sheet pillars are a preview artefact)

### deep/hall: C+
- **BLOCKER: sealed furnished room, blocked door.** In the basilica (temperate, alpine, moorland, maritime) a furnished corner chamber is unreachable (`unreached_furnished_room@2/0/2`, 4 cells), and a door at `4/0/5` has a side no one can stand on. 24/84 cases. The walk map (temperate flat s0) shows the chamber at the back left behind the dais as `+` cells walled off.
  - Image: `g6img/deep_hall_cultures.webp`. Walk map: `-Preach=deep/hall,temperate,1007,south,0`.
  - Fault: `archetype/deep/DeepHall.java` (the basilica's side rooms) and the `kit/deep/Moot` placement next to the door.
  - Fix: put the chamber's door on the aisle and keep its approach clear of Moot furniture. Add a BlueprintChecks case for furnished rooms unreached by the light walk.
- **major: towers can't be reached.** The basilica's twin towers carry the VIEWPOINT, but their upper floors are unreached (`unreached_levels` 3). The stave hall has up to 139 sealed cells and 9 unreached levels.
  - Fault: `DeepHall.java` towers, `HallStave.java` upper core.
  - Fix: a ladder or newel stair in one tower to its top, and open the stave core's upper storey (or fill it solid).
- **major: pueblo terraces unguarded.** The mesa pueblo's stepped terraces have 118-126 fall-edge cells. Real pueblo roofs have low parapets.
  - Fault: `HallPueblo.java`.
  - Fix: a one-high parapet (rounded render) round each terrace, with gaps only at the ladders.
- **minor: blank mediterranean back.** The loggia's back is a 20-block blank render wall. Fault: `HallLoggia.java`. Fix: windows, quoins and a string course on all faces.

### deep/dwelling: C
- **major: same block in all cultures.** Rough-stone ground storey, plastered upper storey, a diagonal stair across the front and a flat terrace. Only the top trim changes. `culture_likes` 13. The back and sides are big flat panels of plaster and deepslate. Image: `g6img/deep_dwelling_cultures.webp`, `deep_dwelling_zoom.webp`.
  - Fault: `archetype/deep/DeepDwelling.java`.
  - Fix: ask the planner for the "nothing stacked here" flag (the doc's own "what is weak"), then allow gabled or culture roofs. Add the *loubí* arcade's depth (piers 1 proud) and side windows on non-party walls.
- **major: dormitory upper floor.** The upper room is 6 beds wall to wall in a 7x11 plot, with no 3x3 of clear floor in any room (84/84). Image: `g6img/deep_dwelling_beds.webp`.
  - Fault: `DeepDwelling.java:962` (`beds = 4 * min(1.8, area/49)`).
  - Fix: cap beds at the free floor divided by 10, or partition the upper floor into two chambers. Keep a 3x3 walk space.
- **major: stair without a rail.** The front flight on its own soffit has no balustrade, a 4+ drop to the street (fall edge 84/84, `fall_edge@6/7/0`).
  - Fault: `DeepDwelling.java` stair.
  - Fix: a rail of the culture's railing (`DeepManner`) on the open side.

### deep/forge: D+
- **major: one material, one plan.** Walls, gable and roof are all deepslate brick and tile. There are no windows on three sides, and the back and sides are blank. It is identical in all 14 cultures (`culture_likes` 13; `DeepManner` used once in the file). It reads as a bunker. Image: `g6img/deep_forge.webp`.
  - Fault: `archetype/deep/DeepForge.java`.
  - Fix:
    - Roof in the culture's covering via `DeepManner.rehome`.
    - A timber frame or tie beams showing on the gables.
    - Vents or louvres and high windows on the sides.
    - A smoke hood that differs by culture.
    - A lean-to coal store on one side.
- **minor: sealed roof void.** 38 cells (`sealed_void@8/-14/3`). Fix: open the trusses to the hall, which also lets the forge glow light the roof.

### deep/market: C+
- **major: roofs are flat strips.** The wings' pent roofs use `Profile.straight(0.5)` over 2-3 wide wings, so they rise one block. They read as loose flat rows of dark planks, not roofs. Image: `g6img/deep_market_roofs.webp`.
  - Fault: `archetype/deep/DeepMarket.java:250-259`.
  - Fix: profile 1.0 with an eave overhang and a ridge or coping on the arcade side, or a full gable across each wing.
- **minor: narrow court.** At 11 frontage the court is about 3 wide and the awnings close it over. Fix: raise the minimum frontage to 13 or drop the awnings under 13.
- **minor: same plan.** All cultures share it (`culture_likes` 13). Fix: per-culture arch and awning forms via `DeepManner` (pointed, round, lattice).

### deep/glow_garden: B-
- **minor: plan and shed.** The same plan everywhere. The potting shed is a plain box (a flat yellow bamboo-block cube in karst). Image: `g6img/deep_glow_garden.webp`.
  - Fault: `archetype/deep/GlowGarden.java`.
  - Fix: a culture shed (`Sheds.small` with the deep home finish), and vary the bed layout: a quincunx, two long beds, or a sunken bed.

### deep/lift: C
- **major: winding house is a top hat.** A 5x5 plaster box under a hipped hat, with a squat log box on top for the headframe. It doesn't read as a headframe with a sheave, and it is the same in all cultures except the roof colour. Image: `g6img/deep_lift_head.webp`, `deep_lift.webp`.
  - Fault: `archetype/deep/DeepLift.java:508-680` (`headHouse`, `headframe`).
  - Fix: an A-frame or four-leg headframe at least 6 above the eaves, with braces and a visible wheel (trapdoor or fence rim), and a winding house of the town's own archetype walls (a cottage-sized house).
- **major: plain tower.** A plain one-material shaft between string courses (the doc admits this). Image: `deep_lift.webp`.
  - Fix: buttresses at the corners, a gallery ring at the vault, copper-capped lanterns.
- **minor: bad flight, cramped chamber.** A bad flight in all 84 cases (`bad_flight@7/-7/2`; the walk still reaches the bottom, so verify in game for head bumps where the stair meets the ceiling ring). The foot chamber is cramped by its crates (`cramped_room@5/-24/3`), and there is a purposeless room.
  - Fix: clear the step under the ring; move the stores to the wall.

---

## Top 10 fixes for G6, ranked by impact

1. **Open the deep hall basilica's sealed chamber and blocked door, and give its towers a way up.** This is the group's only blocker, in 4 cultures. `archetype/deep/DeepHall.java`, `kit/deep/Moot`.
2. **Give every working farm its store and compost bin.** `TerraceField.hut` and `Paddy`'s shed fail silently: 42/84 and 8/12 cases have no chest or composter. Fall back to a niche or path-head composter and chest. `farm/TerraceField.java:250`, `farm/Paddy.java:199`.
3. **Rail the falls on paths people walk.** The deep dwelling's front stair (84/84), the pueblo hall's terraces, and the mine cutting's sides. `DeepDwelling.java`, `HallPueblo.java`, `kit/extraction/Drift`.
4. **Stop reskinning the deep town.** Forge, dwelling, market, lift head and glow garden draw one plan in 13-14 cultures. Route their roofs, frames, arches and sheds through `DeepManner` (`rehome`, `home`); start with the forge (D+). `archetype/deep/*`.
5. **Rewrite `Trees.round` and `Trees.shade` crowns.** Irregular lobes, branches and space between crowns. This fixes the orchard mass, blob field and pasture trees, the karst cherry ring and the plantation crowns at once. `kit/homestead/Trees.java`; also orchard spacing at `Orchard.java:77` and plantation timber species.
6. **Deep market roofs.** Use a real pitch (profile 1.0, an eave) instead of flat plank strips, and set the minimum court width to 13. `DeepMarket.java:250-259`.
7. **Deep dwelling beds.** Cap beds by floor area or split the upper floor, so the rooms aren't wall-to-wall beds. `DeepDwelling.java:962`.
8. **Extraction surfaces.** Make the mine's timber portal and hood the yard's focus, with a lean-to, ore bin and cart. Make spoil a sloped mound at small heights. Build the Bohemian treadwheel crane. `kit/extraction/Pithead`, `Drift`, `Spoil`, `Pithead.rig`.
9. **Infrastructure demos and tests that reach the real case.** Run the dock demo out over water, the stair demo up the fall line, and give the walkway a two-facade scene. Then fix the timber pier's in-deck log posts and confirm the pier meets the quay. `kit/infra/Demo`, `archetype/infra/Dock.java`.
10. **Earthwork finish.** Terrace fill sides and cuts get walls or turf batters; covered bridges get level decks on raised abutments; mediterranean kerbs step with slabs, not full quartz blocks. `infra/Terrace.java`, `kit/infra/Spans`, `kit/infra/Dress` and `Roadbed`.
