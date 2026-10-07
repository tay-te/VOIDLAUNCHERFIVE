# Hearth structure audit

Made by `./gradlew structureAudit` (hearth/src/gametest/java/dev/voidmc/hearth/gametest/audit/). 134 registered archetypes x 14 cultures x 3 terrains (flat, slope, and the archetype's own: river or coast for waterside works, cliff for workings and the deep, hills for the rest) x 2 seeds (1007 facing south, 2007 facing east), every blueprint compiled by the real build module offline, as `blueprintPreview` does.

## Files

- `metrics.csv`: one row per case (archetype, culture, terrain, seed). `suits=0` marks a culture that never builds the archetype (measured, but left out of every ranking). `error` is set when the blueprint failed to draw or compile.
- `archetypes.csv`: per archetype over its suited cases, sorted worst first: `play_score` (mean, 0-100), `play_min` (its worst case), `detail_score`, `overall` (mean of the two) and the means of the key metrics.
- `worst.md`: per metric the 15 worst archetypes (each by its worst case) with the case to reproduce.
- `sheets/<category>/<name>.webp`: every culture from the front-right corner, seed 1007, flat land (river, coast or cliff for waterside works, workings and the deep). `<name>_cut.webp` for buildings with rooms: per culture its storey cutaways by day and the main floor by night with its own lamps; red floor markers are room cells under light 8, orange a dark door, magenta dark ground. Grey labels: a culture that does not build the archetype.

Reproduce one case: `./gradlew blueprintPreview -Parchetype=<path> -Pstyle=<culture> -Pseeds=1 -Pterrain="<terrain>;seed=<seed>;facing=<facing>"` (add `night=true` for the night panels, `reach=0/4` for walk maps).

## How to read metrics.csv

The walk is the invariant checks' own (BlueprintChecks and the light check's Spaces): a mover one block wide and two high (a player's 0.6 x 1.8), stepping up a stair, slab or one full block with three clear above, dropping at most three, climbing ladders, passing doors and gates, starting from the street row and the registered entrances. A *room cell* is a walkable cell in the plot with a roof above and a wall before the roof ends in all four directions (Spaces.enclosed). The light check's walk does not stand on candles, lanterns, flower pots, cakes or skulls (Spaces.standable); the BlueprintChecks gate does, so a room reached only by clambering over pots passes the tests but shows here as unreached (desert courtyard house).

| column | meaning |
|---|---|
| check_problems, floating, unsupported, main_entrance_unreached, beds/jobs_unreached_check, check_text | BlueprintChecks.all as the tests run it (bounds, floating, support, reach, chunks, order, light), parsed |
| walk_cells, reached_cells, room_cells | walkable cells in the plot; of them reached from the street; of them room cells |
| unreached_room_cells, unreached_furnished_cells, unreached_pockets, unreached_levels | room cells no one can walk to; of them those in rooms with furniture, a POI or a door (the rest are sealed voids under roofs: wasted space that needs a hidden lamp); unreached pockets of 4+ cells; whole storeys (6+ room cells) with nothing reached |
| rooms, rooms_no3x3, cramped_rooms, empty_rooms, purposeless_rooms | reached rooms of 4+ cells (4-connected on one level, doors split them); rooms of 6+ cells with no 3x3 of clear floor; furnished rooms with < 6 free cells; rooms of 6+ cells with no furniture; rooms with <= 2 ways out, no POI and <= 1 piece of furniture |
| tight_headroom_share | share of room floor with exactly two blocks clear above (heads at the ceiling) |
| open_roofed_share | share of roofed reached floor that is not enclosed (porches and arcades, or rooms whose walls have gaps) |
| doors, blocked_doors, doors_facing_wall, doors_facing_drop, unreached_doors | door blocks; doors with a side nobody can stand on (level, a step down, or a stair up); sides against a wall; sides over a drop; doors nobody walks to |
| entrances_unreached | registered entrances with no reached cell within two below or one above |
| flights, bad_flights | walked flights (2+ stairs climbing one way); flights that end at a wall or ceiling or have a step without two clear above |
| ladders, dead_ladders | ladder columns; ladders with nowhere to step off at the top |
| beds_no_free_side | beds with no reached cell beside either half |
| workstations, workstations_unusable, chest_lids_blocked | job blocks, chests, barrels; those with no reached face (beside, a level up or down); chests under a solid block |
| light_min_room, light_mean_room, dark_room_cells, light_unmet | block light on reached room cells (the light check's flood); cells under 8; the light check's unmet needs |
| roof_hole_cells | walkable open-sky cells in patches of <= 6 with roof all round (rain gets in; smoke holes and light wells show here too) |
| fall_edge_cells | reached cells beside a drop of 4+ with no rail and no water below |
| lava, water_roofed | lava placed; water placed under a roof |
| relief_share | per facade (front, back, sides; the body's stages only: foundation, frame, walls, floors, roof, openings) the share of wall cells standing proud or recessed from the wall plane or made of shaped blocks (stairs, slabs, trapdoors, fences): beams, sills, frames, shutters, pilasters |
| flat_patch_max | largest 4-connected patch of one block on the wall plane, over all facades (cells) |
| facade_blocks, dominant_share | distinct blocks per facade; the commonest block's share |
| windows_per_side, blind_sides | glass groups and wall openings per facade 5+ wide; such facades without one |
| overhang_mean | median over a facade's columns of how far the roof above the wall stands out from the wall plane |
| roof_facings, roof_corners, roof_levels, roof_complexity | top surface: distinct stair facings; inner/outer stair corners (hips, valleys); distinct heights; facings + corners/2 (<= 4) + levels/3 (<= 3) |
| height, height_ratio | blocks above the floor; over the body's longer side |
| mirror_sym | share of body blocks whose left-right mirror is the same block (1 = perfectly symmetric) |
| repeat_cols | share of facade columns repeating another column exactly (bays copied) |
| furniture_per_cell, empty_room_ratio | furniture blocks round room cells (feet and head height) per room cell; share of rooms with none |
| culture_twin, twin_culture, culture_likes, shape_hash | flat seed-0 rows only: the Jaccard likeness of the blueprint's filled cells to the nearest suited culture's (as cultureSimilarity), which culture, how many suited cultures are >= 0.8 alike (cultureSimilarity's threshold), and a hash of the shape |
| where | the first location of each kind of playability finding, in the plot's frame: x across the front from the left as seen from the street, y above the floor, z back from the front row (`-Preach=<path>,<culture>,<seed>,<facing>,<y>` prints that case's walk map) |
| play_score, detail_score | reviewer weights (Scores.java): playability starts at 100 and loses points per finding; detail averages ten 0-1 sub-scores (relief, flat patch, palette, windows, overhang, roof, symmetry, repetition, furniture, culture likes) |

Caveats: infrastructure archetypes (roads, bridges, docks, walls) need the planner's polylines and draw a stub on a bare plot; deep archetypes are drawn on the synthetic surface here, not in a cavern; Expanse blocks render as their vanilla stand-ins; castles are being fixed in parallel on branch hearth-w6-fix, so their rows describe the hearth branch as it is.

## Top problems found automatically

Per metric: cases over the line (suited cultures), archetypes affected, and the five worst (archetype value (case)).

### Playability

- **check_problems**: 1 of 10356 cases, 1 archetypes. noble/castle 1 (temperate slope s1)
- **unreached_furnished_cells**: 460 of 10356 cases, 32 archetypes. noble/castle 203 (alpine flat s1); commerce/bank 54 (bayou flat s1); house/courtyard_house 50 (mediterranean flat s0); noble/keep 35 (northern flat s0); work/watermill 26 (temperate flat s1)
- **unreached_room_cells**: 4463 of 10356 cases, 94 archetypes. landmark/cathedral 467 (temperate slope s0); noble/castle 390 (alpine flat s1); deep/hall 304 (northern cliff s0); civic/monastery 263 (karst flat s1); civic/convent 128 (karst flat s0)
- **unreached_levels**: 2144 of 10356 cases, 58 archetypes. landmark/cathedral 21 (temperate flat s0); deep/hall 15 (northern cliff s0); civic/monastery 10 (temperate flat s0); noble/castle 8 (temperate slope s1); civic/convent 6 (maritime flat s0)
- **blocked_doors**: 1214 of 10356 cases, 37 archetypes. house/tenement 7 (maritime flat s0); house/courtyard_house 6 (karst slope s0); storage/warehouse 4 (desert flat s0); civic/university 2 (temperate flat s1); commerce/market_hall 2 (temperate flat s0)
- **unreached_doors**: 387 of 10356 cases, 34 archetypes. house/courtyard_house 8 (desert slope s1); commerce/caravanserai 3 (desert flat s0); house/tenement 3 (tropical flat s1); storage/warehouse 2 (tropical flat s0); house/townhouse 2 (tropical flat s0)
- **entrances_unreached**: 9 of 10356 cases, 2 archetypes. work/carpentry 1 (tropical flat s1); landmark/pagoda 1 (karst flat s0)
- **bad_flights**: 271 of 10356 cases, 19 archetypes. noble/castle 49 (mediterranean hills s1); law/courthouse 7 (temperate flat s0); landmark/cathedral 5 (northern hills s1); work/logging_camp 4 (karst flat s0); farm/terrace_field 2 (temperate slope s0)
- **dead_ladders**: 255 of 10356 cases, 6 archetypes. noble/castle 2 (temperate flat s0); noble/keep 1 (temperate flat s0); farm/orchard 1 (temperate flat s0); noble/palace 1 (desert flat s0); work/barn 1 (desert flat s0)
- **beds_no_free_side**: 25 of 10356 cases, 5 archetypes. house/courtyard_house 4 (desert flat s0); house/farmhouse 1 (tropical flat s0); noble/palace 1 (desert hills s0); civic/orphanage 1 (tropical slope s0); noble/castle 1 (moorland flat s0)
- **workstations_unusable**: 637 of 10356 cases, 21 archetypes. house/courtyard_house 26 (desert flat s0); noble/castle 6 (temperate slope s1); commerce/tavern 4 (temperate slope s1); house/tenement 4 (temperate slope s1); storage/storehouse 4 (alpine slope s1)
- **chest_lids_blocked**: 177 of 10356 cases, 3 archetypes. civic/library 1 (temperate flat s0); work/charcoal_kiln 1 (temperate flat s0); work/builders_yard 1 (temperate flat s1)
- **roof_hole_cells**: 458 of 10356 cases, 24 archetypes. noble/palace 12 (karst flat s0); noble/keep 9 (karst flat s0); noble/castle 9 (karst flat s1); landmark/temple 8 (steppe flat s0); work/brewery 4 (tropical flat s0)
- **open_roofed_share**: 3377 of 9325 cases, 96 archetypes. defense/wall 1.000 (temperate flat s0); work/smelter 1.000 (desert flat s0); work/sawmill 1.000 (temperate flat s0); extract/quarry_pit 1.000 (temperate flat s0); infra/walkway 1.000 (temperate flat s0)
- **rooms_no3x3**: 5092 of 10356 cases, 95 archetypes. noble/castle 27 (alpine hills s1); commerce/caravanserai 23 (mediterranean flat s0); noble/palace 19 (karst hills s0); civic/university 18 (tropical flat s0); civic/monastery 11 (karst slope s0)
- **cramped_rooms**: 1741 of 10356 cases, 71 archetypes. commerce/caravanserai 23 (desert flat s0); noble/castle 8 (karst flat s1); civic/university 8 (karst slope s0); commerce/market_hall 6 (tropical flat s0); civic/convent 5 (tropical flat s0)
- **purposeless_rooms**: 849 of 10356 cases, 35 archetypes. noble/castle 4 (wisteria hills s1); noble/palace 4 (desert flat s0); deep/hall 2 (mesa flat s0); landmark/cathedral 2 (alpine flat s0); deep/lift 1 (temperate flat s0)
- **tight_headroom_share**: 218 of 6862 cases, 25 archetypes. house/shack 1.000 (desert flat s1); work/charcoal_kiln 1.000 (desert flat s0); house/stilt_house 1.000 (tropical slope s1); civic/school 1.000 (tropical hills s0); commerce/tavern 1.000 (tropical hills s1)
- **fall_edge_cells**: 4034 of 10356 cases, 117 archetypes. noble/castle 476 (maritime hills s1); noble/palace 351 (mediterranean hills s0); noble/keep 138 (moorland slope s1); deep/hall 131 (mesa flat s1); landmark/pagoda 128 (karst slope s0)

### Detail and hand-built look

- **relief_share**: 1975 of 8259 cases, 73 archetypes. deep/lift 0.000 (temperate flat s0); civic/playhouse 0.000 (temperate flat s0); landscape/bell 0.000 (temperate flat s0); work/masonry 0.000 (desert flat s0); work/quarry 0.000 (temperate flat s0)
- **flat_patch_max**: 4743 of 10356 cases, 94 archetypes. civic/university 218 (desert flat s0); civic/monastery 209 (desert flat s0); noble/stables 208 (bayou hills s1); civic/convent 191 (desert flat s0); commerce/caravanserai 183 (mesa flat s1)
- **facade_blocks**: 345 of 8259 cases, 21 archetypes. work/quarry 1.000 (temperate flat s0); law/gallows 1.000 (temperate flat s0); landscape/statue 1.000 (desert flat s1); law/pillory 1.000 (temperate flat s1); work/windmill 1.000 (temperate flat s0)
- **dominant_share**: 807 of 8259 cases, 62 archetypes. work/quarry 1.000 (temperate flat s0); landscape/statue 1.000 (desert flat s1); law/pillory 1.000 (temperate flat s1); law/gallows 1.000 (temperate flat s0); noble/keep 1.000 (mesa flat s1)
- **windows_per_side**: 2762 of 7895 cases, 92 archetypes. defense/wall 0.000 (temperate flat s0); work/sawmill 0.000 (temperate flat s0); house/hut 0.000 (tropical flat s0); deep/glow_garden 0.000 (temperate flat s0); house/hovel 0.000 (temperate flat s0)
- **blind_sides**: 2440 of 10356 cases, 96 archetypes. deep/glow_garden 4 (temperate flat s0); house/hovel 4 (temperate flat s0); house/hermit_hut 4 (temperate flat s0); work/masonry 4 (temperate flat s0); deep/forge 4 (temperate flat s0)
- **overhang_mean**: 3412 of 8373 cases, 103 archetypes. defense/wall 0.000 (temperate flat s0); civic/playhouse 0.000 (temperate flat s0); law/pillory 0.000 (temperate flat s0); defense/gatehouse 0.000 (temperate flat s0); noble/hunting_lodge 0.000 (temperate flat s0)
- **roof_complexity**: 3794 of 10356 cases, 102 archetypes. extract/mine 0.000 (temperate flat s0); infra/dock 0.000 (temperate flat s1); landscape/lamp_post 0.000 (temperate flat s0); landscape/park 0.000 (temperate flat s0); landscape/plaza 0.000 (temperate flat s0)
- **mirror_sym**: 1186 of 9312 cases, 49 archetypes. defense/palisade 1.000 (temperate flat s0); commerce/market_stall 1.000 (temperate flat s0); extract/shaft 1.000 (temperate cliff s1); farm/field 1.000 (temperate flat s0); farm/pasture 1.000 (temperate flat s0)
- **repeat_cols**: 573 of 8088 cases, 25 archetypes. civic/cemetery 0.941 (tropical hills s1); landmark/temple 0.933 (mesa slope s0); civic/tilt_yard 0.929 (temperate flat s1); defense/palisade 0.926 (moorland flat s1); farm/terrace_field 0.900 (tropical hills s1)
- **empty_room_ratio**: 1097 of 6862 cases, 42 archetypes. extract/mine 1.000 (temperate flat s0); civic/bathhouse 1.000 (karst flat s0); civic/school 1.000 (tropical slope s0); house/stilt_house 1.000 (tropical slope s1); defense/watchtower 0.750 (desert flat s0)
- **furniture_per_cell**: 1248 of 6862 cases, 48 archetypes. civic/bathhouse 0.000 (karst flat s0); extract/shaft 0.000 (temperate flat s0); extract/mine 0.000 (temperate flat s0); landmark/clock_tower 0.000 (temperate flat s1); landmark/temple 0.000 (karst flat s0)
- **culture_twin**: 1322 of 1726 cases, 126 archetypes. extract/mine 1.000 (temperate flat s0); deep/lift 1.000 (temperate flat s0); infra/dock 1.000 (temperate flat s0); defense/wall 1.000 (temperate flat s0); civic/playhouse 1.000 (wisteria flat s0)
- **culture_likes**: 913 of 1726 cases, 91 archetypes. extract/mine 13 (temperate flat s0); deep/lift 13 (temperate flat s0); infra/dock 13 (temperate flat s0); defense/wall 13 (temperate flat s0); civic/playhouse 13 (temperate flat s0)
- **height_ratio**: 304 of 9312 cases, 10 archetypes. extract/plantation 6.000 (northern cliff s0); landmark/bell_tower 5.857 (temperate flat s1); landmark/clock_tower 5.714 (temperate flat s1); infra/walkway 4.333 (temperate flat s0); infra/dock 4.000 (temperate flat s0)

## Archetypes by audit group

Scores: play / detail / overall (means over suited cases). Sheets are relative to this file.

### G1 homes

| archetype | own terrain | play | detail | overall | sheets |
|---|---|---|---|---|---|
| house/cliff_dwelling | hills | 89.221 | 60.233 | 74.727 | [sheet](sheets/house/house_cliff_dwelling.webp) [cut](sheets/house/house_cliff_dwelling_cut.webp) |
| house/cottage | hills | 91.477 | 84.873 | 88.175 | [sheet](sheets/house/house_cottage.webp) [cut](sheets/house/house_cottage_cut.webp) |
| house/courtyard_house | hills | 46.221 | 63.412 | 54.817 | [sheet](sheets/house/house_courtyard_house.webp) [cut](sheets/house/house_courtyard_house_cut.webp) |
| house/croft | hills | 84.117 | 78.801 | 81.459 | [sheet](sheets/house/house_croft.webp) [cut](sheets/house/house_croft_cut.webp) |
| house/farmhouse | hills | 90.046 | 86.650 | 88.348 | [sheet](sheets/house/house_farmhouse.webp) [cut](sheets/house/house_farmhouse_cut.webp) |
| house/hermit_hut | hills | 99.964 | 68.814 | 84.389 | [sheet](sheets/house/house_hermit_hut.webp) [cut](sheets/house/house_hermit_hut_cut.webp) |
| house/hovel | hills | 100.000 | 71.995 | 85.998 | [sheet](sheets/house/house_hovel.webp) [cut](sheets/house/house_hovel_cut.webp) |
| house/hut | hills | 96.464 | 74.286 | 85.375 | [sheet](sheets/house/house_hut.webp) [cut](sheets/house/house_hut_cut.webp) |
| house/longhouse | hills | 96.343 | 79.921 | 88.132 | [sheet](sheets/house/house_longhouse.webp) [cut](sheets/house/house_longhouse_cut.webp) |
| house/manor | hills | 74.637 | 83.030 | 78.834 | [sheet](sheets/house/house_manor.webp) [cut](sheets/house/house_manor_cut.webp) |
| house/merchant_house | hills | 88.750 | 74.113 | 81.431 | [sheet](sheets/house/house_merchant_house.webp) [cut](sheets/house/house_merchant_house_cut.webp) |
| house/rectory | hills | 90.106 | 86.375 | 88.240 | [sheet](sheets/house/house_rectory.webp) [cut](sheets/house/house_rectory_cut.webp) |
| house/reeve_house | hills | 85.953 | 84.886 | 85.419 | [sheet](sheets/house/house_reeve_house.webp) [cut](sheets/house/house_reeve_house_cut.webp) |
| house/round_house | hills | 97.278 | 70.708 | 83.993 | [sheet](sheets/house/house_round_house.webp) [cut](sheets/house/house_round_house_cut.webp) |
| house/rowhouse | hills | 88.956 | 77.756 | 83.356 | [sheet](sheets/house/house_rowhouse.webp) [cut](sheets/house/house_rowhouse_cut.webp) |
| house/scribe_house | hills | 83.909 | 75.690 | 79.800 | [sheet](sheets/house/house_scribe_house.webp) [cut](sheets/house/house_scribe_house_cut.webp) |
| house/shack | hills | 97.310 | 60.954 | 79.132 | [sheet](sheets/house/house_shack.webp) [cut](sheets/house/house_shack_cut.webp) |
| house/shophouse | hills | 89.557 | 69.554 | 79.556 | [sheet](sheets/house/house_shophouse.webp) [cut](sheets/house/house_shophouse_cut.webp) |
| house/stilt_house | river | 92.056 | 83.949 | 88.002 | [sheet](sheets/house/house_stilt_house.webp) [cut](sheets/house/house_stilt_house_cut.webp) |
| house/tenement | hills | 58.807 | 76.771 | 67.789 | [sheet](sheets/house/house_tenement.webp) [cut](sheets/house/house_tenement_cut.webp) |
| house/tower_house | hills | 80.689 | 73.648 | 77.169 | [sheet](sheets/house/house_tower_house.webp) [cut](sheets/house/house_tower_house_cut.webp) |
| house/townhouse | hills | 86.240 | 74.899 | 80.570 | [sheet](sheets/house/house_townhouse.webp) [cut](sheets/house/house_townhouse_cut.webp) |

### G2 work-craft-industry-storage

| archetype | own terrain | play | detail | overall | sheets |
|---|---|---|---|---|---|
| storage/granary | hills | 96.821 | 83.796 | 90.308 | [sheet](sheets/storage/storage_granary.webp) [cut](sheets/storage/storage_granary_cut.webp) |
| storage/storehouse | hills | 87.394 | 76.908 | 82.151 | [sheet](sheets/storage/storage_storehouse.webp) [cut](sheets/storage/storage_storehouse_cut.webp) |
| storage/warehouse | hills | 63.383 | 75.326 | 69.355 | [sheet](sheets/storage/storage_warehouse.webp) [cut](sheets/storage/storage_warehouse_cut.webp) |
| work/alchemist_tower | hills | 74.345 | 72.278 | 73.312 | [sheet](sheets/work/work_alchemist_tower.webp) [cut](sheets/work/work_alchemist_tower_cut.webp) |
| work/apiary | hills | 98.405 | 78.249 | 88.327 | [sheet](sheets/work/work_apiary.webp) [cut](sheets/work/work_apiary_cut.webp) |
| work/bakery | hills | 83.648 | 81.258 | 82.453 | [sheet](sheets/work/work_bakery.webp) [cut](sheets/work/work_bakery_cut.webp) |
| work/barn | hills | 80.854 | 66.374 | 73.614 | [sheet](sheets/work/work_barn.webp) [cut](sheets/work/work_barn_cut.webp) |
| work/brewery | hills | 91.603 | 68.408 | 80.006 | [sheet](sheets/work/work_brewery.webp) [cut](sheets/work/work_brewery_cut.webp) |
| work/brick_kiln | hills | 79.350 | 73.891 | 76.620 | [sheet](sheets/work/work_brick_kiln.webp) [cut](sheets/work/work_brick_kiln_cut.webp) |
| work/builders_yard | hills | 83.530 | 77.856 | 80.693 | [sheet](sheets/work/work_builders_yard.webp) [cut](sheets/work/work_builders_yard_cut.webp) |
| work/butcher | hills | 91.851 | 76.614 | 84.233 | [sheet](sheets/work/work_butcher.webp) [cut](sheets/work/work_butcher_cut.webp) |
| work/carpentry | hills | 74.053 | 80.824 | 77.439 | [sheet](sheets/work/work_carpentry.webp) [cut](sheets/work/work_carpentry_cut.webp) |
| work/chandler | hills | 93.336 | 81.414 | 87.375 | [sheet](sheets/work/work_chandler.webp) [cut](sheets/work/work_chandler_cut.webp) |
| work/charcoal_kiln | hills | 93.976 |  | 93.976 | [sheet](sheets/work/work_charcoal_kiln.webp) [cut](sheets/work/work_charcoal_kiln_cut.webp) |
| work/dye_works | hills | 86.803 | 78.023 | 82.413 | [sheet](sheets/work/work_dye_works.webp) [cut](sheets/work/work_dye_works_cut.webp) |
| work/fishery | coast | 86.110 | 80.181 | 83.145 | [sheet](sheets/work/work_fishery.webp) [cut](sheets/work/work_fishery_cut.webp) |
| work/glassworks | hills | 91.372 | 69.453 | 80.412 | [sheet](sheets/work/work_glassworks.webp) [cut](sheets/work/work_glassworks_cut.webp) |
| work/hunters_lodge | hills | 96.425 | 73.670 | 85.048 | [sheet](sheets/work/work_hunters_lodge.webp) [cut](sheets/work/work_hunters_lodge_cut.webp) |
| work/logging_camp | hills | 90.131 | 77.333 | 83.732 | [sheet](sheets/work/work_logging_camp.webp) [cut](sheets/work/work_logging_camp_cut.webp) |
| work/masonry | hills | 93.717 | 65.898 | 79.807 | [sheet](sheets/work/work_masonry.webp) [cut](sheets/work/work_masonry_cut.webp) |
| work/paper_mill | river | 90.449 | 81.163 | 85.806 | [sheet](sheets/work/work_paper_mill.webp) [cut](sheets/work/work_paper_mill_cut.webp) |
| work/quarry | hills | 98.357 | 51.025 | 74.691 | [sheet](sheets/work/work_quarry.webp) |
| work/salt_works | hills | 96.327 | 74.905 | 85.616 | [sheet](sheets/work/work_salt_works.webp) [cut](sheets/work/work_salt_works_cut.webp) |
| work/sawmill | hills | 98.464 | 56.245 | 77.355 | [sheet](sheets/work/work_sawmill.webp) |
| work/shipyard | coast | 89.870 | 67.437 | 78.654 | [sheet](sheets/work/work_shipyard.webp) [cut](sheets/work/work_shipyard_cut.webp) |
| work/smelter | hills | 79.672 | 84.983 | 82.328 | [sheet](sheets/work/work_smelter.webp) |
| work/smithy | hills | 85.705 | 78.157 | 81.931 | [sheet](sheets/work/work_smithy.webp) [cut](sheets/work/work_smithy_cut.webp) |
| work/tailor | hills | 90.795 | 75.887 | 83.341 | [sheet](sheets/work/work_tailor.webp) [cut](sheets/work/work_tailor_cut.webp) |
| work/tannery | hills | 87.355 | 74.991 | 81.173 | [sheet](sheets/work/work_tannery.webp) [cut](sheets/work/work_tannery_cut.webp) |
| work/watermill | river | 84.820 | 80.206 | 82.513 | [sheet](sheets/work/work_watermill.webp) [cut](sheets/work/work_watermill_cut.webp) |
| work/windmill | hills | 78.041 | 62.518 | 70.279 | [sheet](sheets/work/work_windmill.webp) [cut](sheets/work/work_windmill_cut.webp) |

### G3 civic-learning-law

| archetype | own terrain | play | detail | overall | sheets |
|---|---|---|---|---|---|
| civic/almshouse | hills | 91.287 | 78.096 | 84.691 | [sheet](sheets/civic/civic_almshouse.webp) [cut](sheets/civic/civic_almshouse_cut.webp) |
| civic/bathhouse | hills | 95.674 | 71.965 | 83.820 | [sheet](sheets/civic/civic_bathhouse.webp) [cut](sheets/civic/civic_bathhouse_cut.webp) |
| civic/cemetery | hills | 91.572 | 59.124 | 75.348 | [sheet](sheets/civic/civic_cemetery.webp) [cut](sheets/civic/civic_cemetery_cut.webp) |
| civic/chapel | hills | 90.718 | 86.881 | 88.800 | [sheet](sheets/civic/civic_chapel.webp) [cut](sheets/civic/civic_chapel_cut.webp) |
| civic/convent | hills | 56.171 | 77.182 | 66.677 | [sheet](sheets/civic/civic_convent.webp) [cut](sheets/civic/civic_convent_cut.webp) |
| civic/guardhouse | hills | 95.179 | 74.723 | 84.951 | [sheet](sheets/civic/civic_guardhouse.webp) [cut](sheets/civic/civic_guardhouse_cut.webp) |
| civic/infirmary | hills | 84.375 | 75.762 | 80.068 | [sheet](sheets/civic/civic_infirmary.webp) [cut](sheets/civic/civic_infirmary_cut.webp) |
| civic/library | hills | 81.653 | 74.758 | 78.206 | [sheet](sheets/civic/civic_library.webp) [cut](sheets/civic/civic_library_cut.webp) |
| civic/monastery | hills | 52.482 | 76.859 | 64.671 | [sheet](sheets/civic/civic_monastery.webp) [cut](sheets/civic/civic_monastery_cut.webp) |
| civic/observatory | hills | 76.320 | 76.873 | 76.596 | [sheet](sheets/civic/civic_observatory.webp) [cut](sheets/civic/civic_observatory_cut.webp) |
| civic/orphanage | hills | 94.369 | 76.976 | 85.673 | [sheet](sheets/civic/civic_orphanage.webp) [cut](sheets/civic/civic_orphanage_cut.webp) |
| civic/playhouse | hills | 82.082 | 66.223 | 74.152 | [sheet](sheets/civic/civic_playhouse.webp) [cut](sheets/civic/civic_playhouse_cut.webp) |
| civic/school | hills | 94.585 | 76.460 | 85.522 | [sheet](sheets/civic/civic_school.webp) [cut](sheets/civic/civic_school_cut.webp) |
| civic/shrine | hills | 100.000 | 66.267 | 83.134 | [sheet](sheets/civic/civic_shrine.webp) |
| civic/tilt_yard | hills | 92.500 | 38.863 | 65.681 | [sheet](sheets/civic/civic_tilt_yard.webp) [cut](sheets/civic/civic_tilt_yard_cut.webp) |
| civic/town_hall | hills | 74.246 | 81.917 | 78.082 | [sheet](sheets/civic/civic_town_hall.webp) [cut](sheets/civic/civic_town_hall_cut.webp) |
| civic/university | hills | 74.110 | 65.831 | 69.971 | [sheet](sheets/civic/civic_university.webp) [cut](sheets/civic/civic_university_cut.webp) |
| law/courthouse | hills | 70.643 | 78.087 | 74.365 | [sheet](sheets/law/law_courthouse.webp) [cut](sheets/law/law_courthouse_cut.webp) |
| law/gallows | hills | 99.548 | 47.212 | 73.380 | [sheet](sheets/law/law_gallows.webp) |
| law/jail | hills | 92.991 | 70.136 | 81.564 | [sheet](sheets/law/law_jail.webp) [cut](sheets/law/law_jail_cut.webp) |
| law/pillory | hills | 99.310 | 48.882 | 74.096 | [sheet](sheets/law/law_pillory.webp) |
| law/toll_gate | hills | 90.931 | 64.933 | 77.932 | [sheet](sheets/law/law_toll_gate.webp) [cut](sheets/law/law_toll_gate_cut.webp) |
| law/watch_house | hills | 92.619 | 83.648 | 88.133 | [sheet](sheets/law/law_watch_house.webp) [cut](sheets/law/law_watch_house_cut.webp) |

### G4 commerce-social-landscape

| archetype | own terrain | play | detail | overall | sheets |
|---|---|---|---|---|---|
| commerce/bank | hills | 95.078 | 79.300 | 87.189 | [sheet](sheets/commerce/commerce_bank.webp) [cut](sheets/commerce/commerce_bank_cut.webp) |
| commerce/caravan_yard | hills | 80.695 | 75.509 | 78.102 | [sheet](sheets/commerce/commerce_caravan_yard.webp) [cut](sheets/commerce/commerce_caravan_yard_cut.webp) |
| commerce/caravanserai | hills | 55.892 | 59.456 | 57.674 | [sheet](sheets/commerce/commerce_caravanserai.webp) [cut](sheets/commerce/commerce_caravanserai_cut.webp) |
| commerce/counting_house | hills | 96.201 | 75.224 | 85.713 | [sheet](sheets/commerce/commerce_counting_house.webp) [cut](sheets/commerce/commerce_counting_house_cut.webp) |
| commerce/hostel | hills | 93.146 | 68.499 | 80.822 | [sheet](sheets/commerce/commerce_hostel.webp) [cut](sheets/commerce/commerce_hostel_cut.webp) |
| commerce/inn | hills | 80.649 | 81.054 | 80.851 | [sheet](sheets/commerce/commerce_inn.webp) [cut](sheets/commerce/commerce_inn_cut.webp) |
| commerce/market_hall | hills | 75.571 | 79.947 | 77.759 | [sheet](sheets/commerce/commerce_market_hall.webp) [cut](sheets/commerce/commerce_market_hall_cut.webp) |
| commerce/market_stall | hills | 99.500 |  | 99.500 | [sheet](sheets/commerce/commerce_market_stall.webp) |
| commerce/tavern | hills | 74.354 | 80.921 | 77.638 | [sheet](sheets/commerce/commerce_tavern.webp) [cut](sheets/commerce/commerce_tavern_cut.webp) |
| commerce/thieves_den | hills | 91.427 | 73.489 | 82.458 | [sheet](sheets/commerce/commerce_thieves_den.webp) [cut](sheets/commerce/commerce_thieves_den_cut.webp) |
| landscape/bell | hills | 100.000 | 56.083 | 78.041 | [sheet](sheets/landscape/landscape_bell.webp) |
| landscape/festival_ground | hills | 91.000 |  | 91.000 | [sheet](sheets/landscape/landscape_festival_ground.webp) |
| landscape/fountain | hills | 99.893 |  | 99.893 | [sheet](sheets/landscape/landscape_fountain.webp) |
| landscape/garden | hills | 99.357 | 42.860 | 71.109 | [sheet](sheets/landscape/landscape_garden.webp) |
| landscape/lamp_post | hills | 100.000 |  | 100.000 | [sheet](sheets/landscape/landscape_lamp_post.webp) |
| landscape/park | hills | 94.333 |  | 94.333 | [sheet](sheets/landscape/landscape_park.webp) |
| landscape/plaza | hills | 95.000 |  | 95.000 | [sheet](sheets/landscape/landscape_plaza.webp) |
| landscape/statue | hills | 98.857 | 42.488 | 70.672 | [sheet](sheets/landscape/landscape_statue.webp) |
| landscape/well | hills | 93.048 | 72.808 | 82.928 | [sheet](sheets/landscape/landscape_well.webp) |

### G5 landmark-noble-defense

| archetype | own terrain | play | detail | overall | sheets |
|---|---|---|---|---|---|
| defense/gatehouse | hills | 79.578 | 65.457 | 72.517 | [sheet](sheets/defense/defense_gatehouse.webp) [cut](sheets/defense/defense_gatehouse_cut.webp) |
| defense/palisade | hills | 100.000 | 35.367 | 67.683 | [sheet](sheets/defense/defense_palisade.webp) |
| defense/wall | hills | 85.048 | 60.472 | 72.760 | [sheet](sheets/defense/defense_wall.webp) |
| defense/watchtower | hills | 91.214 | 61.496 | 76.355 | [sheet](sheets/defense/defense_watchtower.webp) [cut](sheets/defense/defense_watchtower_cut.webp) |
| landmark/bell_tower | hills | 82.245 | 80.498 | 81.372 | [sheet](sheets/landmark/landmark_bell_tower.webp) [cut](sheets/landmark/landmark_bell_tower_cut.webp) |
| landmark/cathedral | hills | 76.933 | 74.496 | 75.715 | [sheet](sheets/landmark/landmark_cathedral.webp) [cut](sheets/landmark/landmark_cathedral_cut.webp) |
| landmark/clock_tower | hills | 83.222 | 80.100 | 81.661 | [sheet](sheets/landmark/landmark_clock_tower.webp) [cut](sheets/landmark/landmark_clock_tower_cut.webp) |
| landmark/great_hall | hills | 84.707 | 86.143 | 85.425 | [sheet](sheets/landmark/landmark_great_hall.webp) [cut](sheets/landmark/landmark_great_hall_cut.webp) |
| landmark/lighthouse | coast | 75.849 |  | 75.849 | [sheet](sheets/landmark/landmark_lighthouse.webp) [cut](sheets/landmark/landmark_lighthouse_cut.webp) |
| landmark/pagoda | hills | 75.105 | 50.543 | 62.824 | [sheet](sheets/landmark/landmark_pagoda.webp) [cut](sheets/landmark/landmark_pagoda_cut.webp) |
| landmark/temple | hills | 87.934 | 63.633 | 75.784 | [sheet](sheets/landmark/landmark_temple.webp) [cut](sheets/landmark/landmark_temple_cut.webp) |
| noble/castle (castle: fixed on hearth-w6-fix) | hills | 46.385 | 72.354 | 59.370 | [sheet](sheets/noble/noble_castle.webp) [cut](sheets/noble/noble_castle_cut.webp) |
| noble/hunting_lodge | hills | 79.510 | 65.672 | 72.591 | [sheet](sheets/noble/noble_hunting_lodge.webp) [cut](sheets/noble/noble_hunting_lodge_cut.webp) |
| noble/keep | hills | 60.685 | 69.478 | 65.081 | [sheet](sheets/noble/noble_keep.webp) [cut](sheets/noble/noble_keep_cut.webp) |
| noble/palace | hills | 52.434 | 74.690 | 63.562 | [sheet](sheets/noble/noble_palace.webp) [cut](sheets/noble/noble_palace_cut.webp) |
| noble/stables | hills | 87.422 | 72.030 | 79.726 | [sheet](sheets/noble/noble_stables.webp) [cut](sheets/noble/noble_stables_cut.webp) |

### G6 farm-infra-extract-deep

| archetype | own terrain | play | detail | overall | sheets |
|---|---|---|---|---|---|
| deep/dwelling | cliff | 82.463 | 70.868 | 76.666 | [sheet](sheets/deep/deep_dwelling.webp) [cut](sheets/deep/deep_dwelling_cut.webp) |
| deep/forge | cliff | 84.870 | 52.416 | 68.643 | [sheet](sheets/deep/deep_forge.webp) |
| deep/glow_garden | cliff | 91.194 | 63.941 | 77.568 | [sheet](sheets/deep/deep_glow_garden.webp) [cut](sheets/deep/deep_glow_garden_cut.webp) |
| deep/hall | cliff | 65.801 | 69.436 | 67.619 | [sheet](sheets/deep/deep_hall.webp) [cut](sheets/deep/deep_hall_cut.webp) |
| deep/lift | cliff | 67.552 | 67.579 | 67.566 | [sheet](sheets/deep/deep_lift.webp) [cut](sheets/deep/deep_lift_cut.webp) |
| deep/market | cliff | 90.719 | 79.431 | 85.075 | [sheet](sheets/deep/deep_market.webp) |
| extract/clay_pit | cliff | 94.667 |  | 94.667 | [sheet](sheets/extract/extract_clay_pit.webp) |
| extract/mine | cliff | 84.167 |  | 84.167 | [sheet](sheets/extract/extract_mine.webp) [cut](sheets/extract/extract_mine_cut.webp) |
| extract/plantation | cliff | 93.702 |  | 93.702 | [sheet](sheets/extract/extract_plantation.webp) |
| extract/quarry_pit | cliff | 90.381 |  | 90.381 | [sheet](sheets/extract/extract_quarry_pit.webp) |
| extract/sand_pit | cliff | 94.476 |  | 94.476 | [sheet](sheets/extract/extract_sand_pit.webp) |
| extract/shaft | cliff | 75.619 | 69.012 | 72.315 | [sheet](sheets/extract/extract_shaft.webp) [cut](sheets/extract/extract_shaft_cut.webp) |
| farm/field | hills | 97.976 | 57.627 | 77.802 | [sheet](sheets/farm/farm_field.webp) |
| farm/orchard | hills | 95.357 |  | 95.357 | [sheet](sheets/farm/farm_orchard.webp) |
| farm/paddy | hills | 97.167 | 49.583 | 73.375 | [sheet](sheets/farm/farm_paddy.webp) |
| farm/pasture | hills | 99.071 |  | 99.071 | [sheet](sheets/farm/farm_pasture.webp) |
| farm/terrace_field | hills | 94.655 | 41.751 | 68.203 | [sheet](sheets/farm/farm_terrace_field.webp) |
| infra/bridge | hills | 97.421 |  | 97.421 | [sheet](sheets/infra/infra_bridge.webp) |
| infra/dock | coast | 97.524 |  | 97.524 | [sheet](sheets/infra/infra_dock.webp) |
| infra/road | hills | 99.988 |  | 99.988 | [sheet](sheets/infra/infra_road.webp) |
| infra/stairs | hills | 98.929 |  | 98.929 | [sheet](sheets/infra/infra_stairs.webp) |
| infra/terrace | hills | 97.500 |  | 97.500 | [sheet](sheets/infra/infra_terrace.webp) |
| infra/walkway | hills | 98.000 | 47.815 | 72.908 | [sheet](sheets/infra/infra_walkway.webp) |

Written 2026-10-07.

## Checked by hand (spot checks of the automatic findings)

- `house/cottage` temperate slope seed 2007 east: a chest stands on the floor cell right inside the front door (local 6/0/3; `blocked_door@6/0/2`). Real: Rooms.furnish put storage in front of the door.
- `house/courtyard_house` desert flat seed 1007 south: the rooms round the court are walled off by potted plants; the BlueprintChecks walk climbs over the pots (it stands on flower pots), the light check's walk does not, so the tests pass while 26 workstations, 4 beds and 8 doors are unreachable on foot. Real for players and villagers alike.
- `house/tenement` temperate flat: the upper-floor back door (local 8/4/10) opens onto an upside-down stair at feet height. Real.
- `noble/castle` temperate slope seed 2007: the only BlueprintChecks failure in the whole catalogue (castles are being reworked on hearth-w6-fix).
- Calibrated away: one- and two-cell sealed pockets beside furniture are counted as sealed voids, not unreached rooms (the smithy's niche behind its forge).
