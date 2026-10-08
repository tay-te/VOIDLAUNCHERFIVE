# G2 audit: work, craft, industry and storage (31 archetypes)

Auditor pass over `archetype/work/**` and `archetype/storage/**` on branch `hearth-audit` (c79df201 + 7c8a23b3).
Evidence: the contact sheets in `../sheets/work/` and `../sheets/storage/` (all 14 cultures, seed 1007, flat, or
river/coast for the mills, fishery and shipyard), the `_cut` cutaways and night panels, every row of `../metrics.csv`
for these archetypes (84 suited cases each), the sources, and one extra `blueprintPreview` run with walk maps
(temperate, seed 1007, south: warehouse, carpentry, bakery, alchemist tower). Crops that show a defect are in
`img/` (1.6 MB). No code was changed.

Severity: **blocker** = broken or unplayable; **major** = clearly not hand-built quality; **minor** = polish.
Grades are for the archetype across its 14 cultures, against the user's bar (hand-built by a skilled player, detailed,
very playable, real-world surface, temperate = KCD II Bohemia).

## Grade distribution

| grade | n | archetypes |
|---|---|---|
| A | 0 | |
| B | 6 | granary (B+), storehouse (B-), apiary (B-), logging_camp (B-), paper_mill (B-), smithy (B-) |
| C | 19 | dye_works, glassworks, salt_works, tailor, tannery, bakery, barn (C+); brewery, butcher, chandler, charcoal_kiln, hunters_lodge, smelter, watermill (C); carpentry, fishery, masonry, sawmill, shipyard (C-) |
| D | 6 | warehouse, builders_yard, quarry (D+); alchemist_tower, brick_kiln, windmill (D) |
| F | 0 | |

(B 6, C 19, D 6 = 31.)

Blockers found: **11** (several span many archetypes; see the list at the end).

## Defects shared by many archetypes (fix once, in the kit)

These recur below; each archetype entry points back here instead of repeating them.

- **S1. Desert/mesa roof-terrace room unreachable (blocker).** `Hall.terrace` (kit/trade/Hall.java:552) builds the
  outside stair only when `body.storeys() == 1`, but always builds the set-back room with a door onto the deck. On
  every 2+ storey flat-roofed building the deck has no way up, so the room's door is blocked and its furniture
  unreached. Seen in desert and mesa for bakery, butcher, chandler, smithy, tailor, hunters_lodge, fishery, storehouse,
  brewery (metrics: `unreached_furnished_room@x/4..8/z blocked_door@...`, 3 to 12 of 84 cases each). Fix: when
  `storeys() > 1`, carry the hall's own stair (or `Lofts.ladder` through a hatch) from the top storey to the deck; if
  neither fits, build no room (and no parapet gap) and return false. Add a test that every registered door has two
  standable sides.
- **S2. Sealed attic/loft over the top floor (major).** Unreached room cells with no furniture under pitched roofs in
  ~20 archetypes (bakery 84/84, smithy 84/84, glassworks 84/84, carpentry, chandler 63/84, fishery 75/84, watermill
  72/84, paper_mill 72/84, warehouse 72/84, tannery 66/84, brick_kiln 78/84, hunters_lodge 78/84). Wasted volume that
  the light pass must fill and that reads, in a two-storey exterior, as a floor nobody can use. `Hall.sealedKnee` only
  opens knees of two rows or less. Fix: in `Hall.roof`, open any unreached attic over the top floor to the room below
  (rafters showing) unless the archetype provides a stair/ladder; where the doc promises a loft (bakery's flour loft,
  carpentry's board loft, tannery's drying loft, fishery's net loft) give it `Lofts.ladder`.
- **S3. Steep "workshop" roofs read as stepped and doubled (major).** `Craft.roof` steepening (kit/craft/Craft.java:44-80)
  in northern, alpine, tundra, moorland, maritime, bayou: the slope is built of offset full-block and stair courses
  with a second verge row, so from the street the roof looks like two roofs stacked or a staircase (butcher, chandler,
  tannery, dye_works, fishery). Image: `img/G2_butcher_chandler_steep_roofs.webp`. Fix: build steep pitches as
  stair-on-full-block pairs per row with one consistent verge (or use the Bohemia `gables` path that tannery uses in
  temperate), and drop the extra ridge board row.
- **S4. Northern/tundra corner posts stand proud past the eaves (major).** In northern and tundra, the dark corner
  posts run full height as 1x1 pillars outside the wall plane and beyond the verge (storehouse, bakery, dye_works,
  paper_mill, carpentry, warehouse). They read as scaffolding, not framing. Image: `img/G2_storehouse_northern_posts.webp`.
  Fix (style/kit, not per archetype): stop the posts at the wall plate and tuck them into the corner (flush or
  half-proud with a stair/slab cap), as a log or stave builder would.
- **S5. One plan, re-skinned, in 9 to 13 cultures (major).** `culture_likes` 13 (every other culture >= 0.8 alike) for
  alchemist_tower, brick_kiln, logging_camp, quarry, shipyard; 12 for builders_yard, masonry; 9 to 11 for
  charcoal_kiln, paper_mill, sawmill. Only desert/mesa (and sometimes tropical/steppe/karst) get a different plan. Fix
  in the style bible's spirit: per culture group, a different footprint or a different type (see each entry).
- **S6. Unguarded upper edges (major).** Lofts open over the floor below and staging/galleries without rails: barn
  82/84 (hayloft well), shipyard 84/84 (staging at y=5), tailor 45/84 (first-floor gallery), smelter 72/84 (charging
  gallery), warehouse loading doors (see warehouse). Fix: fence or trapdoor rail along any walkable edge over a drop
  of 3+, leaving one gap where a ladder or hoist serves it.

## Per archetype

### storage/granary: B+
Raised on staddle stones in the timber/stone cultures (real, readable), beehive mud bins in desert/mesa, a round
thatched or stone bin on a platform in tropical/steppe. Best of the group. Sheet `sheets/storage/storage_granary.webp`.
- minor: temperate, alpine, moorland, maritime, bayou are one plan reskinned (twins 0.92-0.96). Vary roof form (half
  hip, catslide), footprint and stair side per seed. `Granary.java`.
- minor: desert/mesa beehive cluster: only one dome is entered, through a one-cell room (`rooms_no3x3` 9/84, cramped
  karst). Give the entered dome a 3x3 floor or connect two domes.
- minor: fall edge on slope (up to 19 cells, all cultures) round the raised floor: add a rail or step on the low side.

### storage/storehouse: B-
Solid small barn-like store with hoist; plain.
- blocker: alpine, desert, maritime, mesa (15/84): the upper store's door (`blocked_door@3/5/2`) and furnished loft
  unreached; workstation barrels unusable (up to 4). Desert/mesa is S1; the alpine/maritime loft has a hoist door and
  no stair. Fix: `Lofts.ladder` to the loft in `Storehouse.java`; keep the hoist door with a rail.
- major: temperate white plaster walls 58-cell flat patch (`flat_patch_max` mean 24); no framing, plinth or sills.
  Add a stone plinth and timber frame/boards as the culture's cottages do.
- major: S4 northern posts; temperate/northern ridge board protruding past the gable reads as a stray beam.
- minor: loft furnished with one chest and hay only.

### storage/warehouse: D+
The burgher's stepped-gable store reads well from the street in temperate, but it fails inside.
- major: **the ground floor is empty in every culture**: no barrel racks, no crates, only the switchback's first step
  and one chest at the back corner (walk map y=64 all reached floor; plan y=1 shows only `N` at 12,13 and the chest).
  The racks and crates draw on the upper floors only. Image: `img/G2_warehouse_empty_ground_floor.webp`. Likely cause:
  `Warehouse.interior` k=0 `Floorplan` refuses the cells (check `hall.floor(0).blockHoles(d)` over the ground storey,
  whose floor is the levelled ground). Fix that, and furnish the cart floor with racks, crates and a weigh beam.
- blocker: loading doors at every upper floor open straight onto a 4 to 12 block drop (`blocked_doors` 84/84, up to 4;
  `fall_edge` 47/84): a player or NPC opening one walks out into the air. Fix: half-height doors (trapdoor pair or
  fence gate) with a rail inside, or a hoist platform with a rail outside.
- major: flat facades: `flat_patch_max` mean 72, up to 151; `mirror_sym` 0.16 (the stacked bay is off-centre with the
  cart shed). Brick and plaster walls carry no pilasters, string courses, sills or anchors. Add a plinth, a string
  course per floor, framed loading bay, iron anchors.
- major: attic unreached 72/84 (S2).
- minor: tropical/karst use striped bamboo with dark posts that reads busy.

### work/alchemist_tower: D
A tall 7x8 stone tower with a crenellated top and a tiny cap roof, identical plan in all 14 cultures (S5, twins
0.97-1.0, including tropical/steppe/bayou mud-brick versions of a European tower).
- blocker: workstations unusable 84/84: the barrel and brewing stand of the still (`barrel@5/5/2`,
  `brewing_stand@5/6/2`) have no reachable face. Fix in `AlchemistTower.java` (still on a barrel, ~line 648): set the
  still against a wall with a free cell in front, or drop the barrel and stand the brewing stand on a bench.
- major: cramped: `rooms_no3x3` 84/84; the ground floor is mostly solid with a one-wide passage (walk map y=64), the
  floors above are a one-cell ring round a central core. Image: `img/G2_alchemist_cramped.webp`. Fix: 9x9 minimum
  footprint, stair along the wall instead of a central core, one room per floor with a 3x3 clear.
- major: silhouette: flat walls (`flat_patch_max` up to 108), the cap roof sits on the terrace like a hat; overhang 0.
  Give it a real roof (a candle-snuffer or pyramid that oversails), corbelled top, quoins.
- major: culture fit: desert/mesa get banded terracotta towers, but tropical, steppe and bayou get the same tower in
  mud brick. Per culture: a walled courtyard laboratory in the south, a timber herbalist's tower house in the north.

### work/apiary: B-
Fenced flower garden and a bee house; reads right (Bohemian včelín).
- minor: blind walls in many cultures (`blind_sides` mean 1.8); the bee house front is the only lit side.
- minor: honeycomb-block floor cells in the bee house corridor read as glowing orange tiles; use them only inside the
  hive wall. `Apiary.java:270`.
- minor: desert/mesa: a plain box with a hollow parapet top and two hives; add a reed shade and water jar.

### work/bakery: C+
Shop with bread oven(s) and an awning; recognisable but cramped.
- blocker: desert/mesa terrace room (S1).
- major: the interior is crowded by two domed ovens and the counter (`rooms_no3x3` 78/84, `tight_headroom` over half in
  42/84). Fix: one oven inside with the second in the side yard (as the doc says), counter along the front wall.
- major: temperate: an open square hole in the front roof slope (likely a dormer whose cheeks were not built). Sheet
  `sheets/work/work_bakery.webp`, temperate tile. Fix the dormer in `Bakery.java`/`Hall.roof` or remove it.
- major: flour loft unreached 75/84 (S2): the one-storey variant promises a loft in the roof with no way up.
- minor: S4 northern posts; mediterranean and steppe have plain flat walls.

### work/barn: C+
Big gable barn with a hayloft; good mass.
- major: hayloft well open with no rail (`fall_edge` 82/84, up to 20 cells): S6.
- blocker (desert, mesa, 12/84): dead ladder to the roof deck (`dead_ladders`). Fix in `Barn.java`: end the ladder at
  a deck cell or cut a hatch.
- major: plain white gable ends (alpine, tundra, moorland, maritime: `flat_patch_max` up to 89; relief 0.08). Board the
  gable or add frame posts, a hay door with hood, owl hole.
- minor: desert/mesa roof deck pergola reads as a lid; add a reed/brush shade.

### work/brewery: C
Malt kiln tower plus a long brew hall.
- blocker: temperate slope and 12 other cases: the side room reached only through a door with no standing cell
  (`unreached_furnished_room@1/0/2 blocked_door@2/0/0`), plus desert/mesa S1.
- major: the brew hall is a one-to-two wide aisle along a wall of vats (`rooms_no3x3` 78/84; `purposeless_rooms`
  47/84 = the sealed kiln chamber). Widen the hall, put the copper and mash tun in the middle with a walk round.
- major: the kiln cowl is a white block on a fence post that floats over the pyramid cap; use a louvred timber cowl
  (trapdoors/slabs) sitting on the cap.
- major: bayou: roof planes overlap and break; temperate/alpine: blank white walls (`relief_share` 0.05, lowest of
  the group).

### work/brick_kiln: D
Bottle chimney and stepped chamber with a lean-to; identical in all 14 cultures (S5: twins 1.0) down to the red brick.
- blocker: the kiln worker's furnace is inside the sealed chamber (`station_unusable:furnace@6/0/5` 84/84). Fix in
  `BrickKiln.java`: firemouths in the chamber wall facing a stoking pit, furnace face open to it.
- major: no drying shed with hacks of green brick, no pug mill or clay heap is visible from the street (the doc
  promises them); the chamber reads as a brick staircase. Image: `img/G2_brick_kiln_same_everywhere.webp`. Build the
  open drying shed on the free side and the pug mill sweep.
- major: desert/mesa should be mud brick with a brick throat (doc); tropical a clamp kiln under a thatched shade.
- minor: no rooms at all (cutaway empty): the stokers' pentice is a roof over a bench only.

### work/builders_yard: D+
- blocker: chest under a solid block (`chest_lid@2/0/12`, 39/84) and blocked doors (42/84, `blocked_door@2/4/7`),
  unreached furnished cells 18/84. Fix in `BuildersYard.java`: keep the cell above every chest empty; check door sides
  after the scaffold/trial wall is drawn.
- major: cluttered and "generated": the crane gantry stands through the shed roof, roof fragments of different
  heights meet it at odd angles, and the half-built trial wall reads as a ruin. Image:
  `img/G2_builders_yard_clutter.webp`. Fix: crane in the yard clear of roofs, one shed with one roof, the trial wall
  as a clean corner with scaffold poles.
- major: S5 (likes 12).

### work/butcher: C
Narrow row house with stall board and awning.
- blocker: desert/mesa terrace room (S1, 12/84).
- major: S3 jagged steep roof in northern/alpine/tundra/moorland/maritime/bayou.
- major: same shell as the chandler (ShopHouse): from the street the two differ only in the awning.
- minor: cramped shop (`cramped_rooms` karst/tropical; `rooms_no3x3` 39/84).

### work/carpentry: C-
- blocker: the board loft is furnished (slabs of boards, lantern) but has no ladder or stair (`unreached_furnished`
  57/84; walk map y=69 the loft ring is `+`), and its gable hoist door opens onto air. `Carpentry.java:201-219`. Fix:
  `Lofts.ladder` up through the open middle; rail the loft edge.
- blocker: one leaf of the front double door is blocked inside (`blocked_door@4/0/3`, 84/84): a bench under the window
  stands against it. Fix: keep the inside cell of both leaves (`fp.keep(door+1, in[1])`), as the warehouse does.
- major: S4 posts, flat white walls in temperate.
- minor: karst/tropical entrance unreached on one seed (`entrances_unreached` 6/84).

### work/chandler: C
- major: honeycomb blocks used as stacked wax stock on the sill, the stall board and inside: big orange cubes that
  shout over the facade and fill the shop. Images `img/G2_chandler_honeycomb.webp`, `img/G2_chandler_cut.webp`.
  `Chandler.java:124,200`: use candles, a few honeycomb items in barrels, and one wax block at most.
- major: S3 roofs; same shell as butcher.
- blocker: desert S1 (3/84).

### work/charcoal_kiln: C
Turfed clamp, billet stacks, burner's hut.
- blocker: chest under the hut's A-frame roof (`chest_lid@8/0/11`, 54/84). Move the store out from under the eave.
- major: billets take the culture's log: karst cherry (pink), bayou mangrove (red), moorland birch (white) stacks are
  loud. Use stripped spruce/oak/dark oak only (what burners actually cut).
- minor: desert/mesa hut is a 3x3 box with an open top; headroom 2 (`tight_headroom` 6/84). Give it a reed roof.
- minor: S5 (temperate plan in 9 cultures).

### work/dye_works: C+
Dye house with the cloth gallery and a rinsing channel.
- major: the cloth gallery is a flat wall of red, yellow and blue banners that reads as flags (primary colours,
  no undyed lengths between in most tiles). Use madder/woad/weld tones (`Hangs.DYED` with brown, light blue,
  yellow-green), alternate with undyed white, hang on a rail with gaps.
- major: loft unreached in 7 cultures (42/84) and cramped rooms 46/84. Add the stair the doc implies; clear a 3x3.
- minor: tenter frames do not show over the roofs at this size.

### work/fishery: C-
- major: from the street it is a generic cottage: nets, drying rack and jetty are behind the house (and on the audit's
  coast case the jetty and nets did not show at all). Put the net frames and the fish rack on the street side.
- major: net loft unreached 75/84 (S2).
- blocker: desert S1; karst/tropical unreached doors 15/84.
- minor: mediterranean is a tall thin tower on a small plot (odd proportion).

### work/glassworks: C+
- major: the same squat brick stack with an oversized cap in every culture; the lime glass cube in the yard reads as
  a slime block. Image: `img/G2_glassworks_stack.webp`. Taller tapering stack, sheet-glass racks as panes in a frame.
- major: sealed pocket at the back (`sealed_void@3/3/13`, 84/84) and loft unreached 27/84 (S2).
- minor: big empty floor round the furnace; add the annealing oven and benches the doc lists.

### work/hunters_lodge: C
- major: the front gable is open into the attic and reads as a hole in the roof (trophies visible through it) in
  temperate, wisteria, northern, alpine, tundra, bayou. Image: `img/G2_hunters_lodge_open_gable.webp`. Fix in
  `HuntersLodge.java`: close the gable (boards with a small window or the antlers mounted on it).
- major: the log lodge's notched corners do not read at sheet scale; moorland birch logs are noisy.
- blocker: desert/mesa S1.
- minor: attic void 78/84 (S2).

### work/logging_camp: B-
Log decks, saw pit, bunkhouse: reads as a working camp.
- major: S5 (likes 13). Karst decks in cherry (pink), steppe/mesa/mediterranean decks look grey. Use spruce/oak.
- minor: saw pit counted as a purposeless room (51/84); karst bad flight (3/84); fall edge on slope (up to 35 cells)
  round the saw pit: rail its lips.

### work/masonry: C-
Open-front mason's shed with blocks and a post crane.
- major: no windows, blind sides 3.8 of 4, relief 0.02: one stone side wall and posts. Add a back wall with openings,
  a banker shelter, the lodge's roof oversailing.
- major: S5 (likes 12). Cramped 30/84.

### work/paper_mill: B-
Louvred drying loft over the stamping room on the race; good silhouette.
- major: the drying loft is empty in most cultures (`empty_rooms` 25/84): add the rails of drying sheets the doc
  describes (paper/white carpet on fences or string).
- minor: wheel hidden from the street corner; attic void 72/84 (S2).

### work/quarry: D+
A rectangular stone pit, a log gantry and a small hut on a large plot.
- major: very sparse (half the plot plain grass), relief 0, same pit in every culture (likes 13). Add spoil heaps,
  stacked blocks, a stone sledge path, a crane with a jib, benches cut in the face.

### work/salt_works: C+
- major: the pans are a checkerboard of single water and white cells; the salt cones are white stair pyramids that read
  as steps. Use long rectangular pans (3x5) with kerbs, cones of white concrete powder/snow layers.
- minor: graduation tower is a short leaf wall; fall edge on slope.

### work/sawmill: C-
- major: an open shed on four heavy posts with a bench; no saw frame, no log deck under a crane, no water wheel. Same
  plan everywhere (likes 9). Relief 0.06, windows 0.
- minor: northern ridge board sticks out past the gable.

### work/shipyard: C-
- major: the hull on the stocks is a block of dark planks with no sheer or curved section; the sail loft is a 3x4
  tower. On the audit's coast case the slip does not reach water.
- major: staging unguarded (`fall_edge` 84/84, up to 50): S6.
- minor: S5 (likes 13).

### work/smelter: C
- blocker: the lava cauldron (tap) is unreachable 60/84 (`station_unusable:lava_cauldron@7/0/9`) and there is a roof
  hole over the furnace top (72/84). Fix in `Smelter.java`: face the tapping arch onto the casting floor.
- major: the forest/stone blast furnace is wrapped in a timber tower with a stone stack on top: it reads as a
  wooden house round a chimney, not a battered rubble furnace with quoins. Image: `img/G2_smelter_timber_tower.webp`.
  Build the furnace stack in stone, timber only for the charging bridge and bellows house.
- major: charging gallery and ramp unguarded (S6).
- note: desert/mesa/tropical/steppe clay shaft furnaces under a ramada are good.

### work/smithy: B-
Open forge with a big hearth chimney; reads as a smithy.
- blocker: desert/mesa S1.
- major: attic void 84/84 (S2).
- minor: the hearth takes a quarter of the floor; `rooms_no3x3` 72/84.

### work/tailor: C+
- major: striped market awnings on the upper floor serve no stall; one lone orange block on the facade. Put the awning
  over the ground-floor window board; drop the orange cell or make it a cloth line.
- major: first-floor gallery unguarded (`fall_edge@7/5/2`, 45/84): S6.
- blocker: desert/mesa S1.

### work/tannery: C+
- major: S3 roofs; loft unreached 54/84 although the stair exists in some cases (check `hall.flight` failure path).
- major: cramped beam house (39/84).
- minor: hides as white boards/banners on frames read well; yard vats fine.

### work/watermill: C
- blocker: the upper meal floor is furnished but unreached in 20/84 (temperate, alpine, bayou, maritime, moorland,
  northern: `unreached_furnished_room@4/5/8 blocked_door@6/5/1`): the flight fails on those sizes. Fix in
  `Watermill.java:100`: fall back to a ladder when `hall.flight` returns null.
- major: tall gable body (2-3 storeys high) with only one usable floor; huge blank gable walls (`flat_patch_max` up to
  100). Lower the body or use the floors; add frame, boards, window rows.
- minor: wheel on the far side from the street corner, invisible in every sheet view; consider turning it toward the
  approach.

### work/windmill: D
- blocker: the mill floors (stones, meal) are unreached in every case (`unreached_levels` 84/84, furnished 66/84):
  the spiral stair (`Stairs.spiral`, `Windmill.java:211`) climbs under floors four blocks apart and does not give a
  walkable exit. Image: `img/G2_windmill_cut.webp`. Fix: floors every five blocks or a wider hatch over the spiral's
  last step, then check with `reach=` that each floor is `r`.
- major: sails clip into the tower body and, on post mills (northern, alpine, tundra), reach the ground. Image:
  `img/G2_windmill_sails.webp`. Set the windshaft out from the cap so the sweep clears the tower by 1 and the ground by
  2; keep the four blades in one plane.
- major: rooms are a one-cell ring round the central post (`rooms_no3x3` 84/84).

## Group top 10 fixes, by impact

1. **Hall.terrace: no stair, no room (S1).** One kit fix unblocks desert/mesa in ~10 archetypes (bakery, butcher,
   chandler, smithy, tailor, hunters_lodge, fishery, storehouse, brewery). Carry the stair to the deck or skip the room.
2. **Windmill floors reachable and sails clear of the tower.** Every windmill in every culture is unplayable above
   the ground floor and visibly broken.
3. **Lofts get ladders; unreached attics open to the room below (S2).** Carpentry (furnished, 57/84), storehouse,
   bakery, dye_works, tannery, fishery, watermill fallback; and the general `Hall.roof` open-or-ladder rule.
4. **Warehouse: furnish the ground floor and make loading doors safe.** The town's store is empty where it matters,
   and its doors open onto a fall.
5. **Unusable job sites:** alchemist still, brick kiln furnace, smelter tap, builders' yard and charcoal kiln chests.
   NPCs cannot work them (84/84, 84/84, 60/84, 39/84, 54/84).
6. **Steep Craft roofs and northern posts (S3, S4).** Fix in `kit/craft/Craft.java` and the northern/tundra frame
   habit; improves butcher, chandler, tannery, dye_works, fishery, storehouse, bakery, carpentry, paper_mill at once.
7. **Per-culture plans for the S5 offenders.** Alchemist tower, brick kiln, quarry, shipyard, logging camp, masonry,
   sawmill, builders yard (likes 12-13): at least a southern, a northern-timber and a Bohemian variant each.
8. **Guard rails on lofts, staging and galleries (S6).** Barn, shipyard, tailor, smelter, warehouse.
9. **Facade depth for the big plain walls.** Warehouse (flat patch 151), watermill (100), alchemist (108), barn (89),
   brewery (relief 0.05), masonry and sawmill (no windows): plinths, string courses, frames, sills, boarded gables.
10. **Noise and props:** chandler honeycomb cubes, dye-works primary banners, glassworks lime cube, charcoal/log
    decks in pink/red/white woods, salt checkerboard and stair "cones", hunters' lodge open gable, bakery roof hole,
    brewery floating cowl.

## Blockers (11)

1. S1 terrace room/door, desert+mesa, ~10 archetypes.
2. windmill upper floors unreached, all cultures.
3. carpentry furnished loft unreached + front door leaf blocked.
4. warehouse loading doors onto a drop, all cultures.
5. alchemist_tower still (barrel/brewing stand) unusable, all cultures.
6. brick_kiln furnace inside sealed chamber, all cultures.
7. smelter lava cauldron tap unusable (60/84).
8. builders_yard chest lid blocked, blocked doors, unreached furnished room.
9. charcoal_kiln store chest under the hut roof (54/84).
10. watermill upper floor unreached when the flight fails (20/84); storehouse loft unreached (alpine, maritime).
11. barn dead ladder (desert, mesa) and brewery side room behind a blocked door.
