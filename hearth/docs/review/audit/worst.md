# Worst offenders per metric

Suited cultures only (an archetype is never built in a culture it does not suit). One line per archetype: its worst case.
Case = culture / terrain / seed index (seed 1007 + 1000 x index; index 0 faces south, 1 east). 15 worst archetypes per metric.

## check_problems (playability)

invariant check failures (BlueprintChecks.all: bounds, floating, reach, order, light). Bad when > 0.0: 1 of 10356 cases, 1 archetypes.

| archetype | value | case | bad cases | note |
|---|---|---|---|---|
| noble/castle | 1 | temperate / slope / s1 | 1 | 0 beds and 3 job sites/stores cannot be walked to from a door, e.g. STORAGE at BlockPos{x=12, y=71, z=16} (Block{minecraft:air} on Block{minecraft:stone_bricks}... |

## floating (playability)

floating blocks (not joined to the ground). Bad when > 0.0: 0 of 10356 cases, 0 archetypes.

(none)

## unsupported (playability)

blocks lacking support (plants, carpets, lanterns). Bad when > 0.0: 0 of 10356 cases, 0 archetypes.

(none)

## main_entrance_unreached (playability)

main entrance not walkable from the street. Bad when > 0.0: 0 of 10356 cases, 0 archetypes.

(none)

## unreached_furnished_cells (playability)

cells of furnished rooms (furniture, a POI or a door) a player cannot reach from the street. Bad when > 0.0: 460 of 10356 cases, 32 archetypes.

| archetype | value | case | bad cases | note |
|---|---|---|---|---|
| noble/castle | 203 | alpine / flat / s1 | 65 | sealed_void@2/6/25 unreached_furnished_room@5/7/43 cramped_room@34/22/22 blocked_door@12/2/37 dead_ladder@24/10/11 fall_edge@38/13/24 |
| commerce/bank | 54 | bayou / flat / s1 | 3 | unreached_furnished_room@4/8/10 bad_flight@10/3/10 station_unusable:anvil@10/8/5 station_unusable:blast_furnace@10/8/4 station_unusable:lectern@2/8/4 |
| house/courtyard_house | 50 | mediterranean / flat / s0 | 7 | sealed_void@5/0/6 unreached_furnished_room@2/4/11 station_unusable:chest@3/0/11 station_unusable:barrel@11/0/10 unreached_door@2/0/8 bed_blocked@1/0/6 station_u... |
| noble/keep | 35 | northern / flat / s0 | 15 | unreached_furnished_room@9/11/3 sealed_void@18/5/1 cramped_room@6/0/4 dead_ladder@12/9/3 roof_hole@3/0/6 fall_edge@14/5/21 |
| work/watermill | 26 | temperate / flat / s1 | 20 | unreached_furnished_room@4/5/8 sealed_void@6/8/7 blocked_door@6/5/1 unreached_door@6/5/1 |
| storage/storehouse | 16 | alpine / flat / s1 | 15 | unreached_furnished_room@1/5/3 sealed_void@3/7/5 station_unusable:barrel@3/4/0 blocked_door@3/5/2 unreached_door@3/5/2 |
| work/builders_yard | 10 | temperate / flat / s0 | 18 | unreached_furnished_room@1/4/12 sealed_void@4/0/14 |
| noble/hunting_lodge | 10 | mediterranean / slope / s0 | 2 | unreached_furnished_room@8/7/15 sealed_void@6/9/11 cramped_room@4/-1/3 fall_edge@15/10/3 |
| work/tailor | 9 | mesa / flat / s0 | 6 | unreached_furnished_room@1/8/5 blocked_door@2/8/4 unreached_door@2/8/4 |
| work/carpentry | 8 | desert / flat / s1 | 57 | unreached_furnished_room@4/5/2 sealed_void@1/2/5 blocked_door@3/0/3 unreached_door@4/5/4 |
| work/windmill | 6 | temperate / flat / s0 | 66 | unreached_furnished_room@5/4/8 sealed_void@7/4/4 |
| civic/town_hall | 6 | northern / flat / s0 | 36 | sealed_void@11/13/6 unreached_furnished_room@9/6/4 blocked_door@4/6/3 |
| house/croft | 6 | temperate / flat / s0 | 21 | unreached_furnished_room@3/4/8 sealed_void@2/3/6 |
| work/brewery | 6 | desert / flat / s1 | 13 | unreached_furnished_room@3/5/8 blocked_door@2/5/7 unreached_door@2/5/7 |
| work/butcher | 6 | desert / flat / s0 | 12 | unreached_furnished_room@5/4/6 blocked_door@4/4/5 unreached_door@4/4/5 |

## unreached_room_cells (playability)

room cells (enclosed, walkable) a player cannot reach, sealed voids under roofs included. Bad when > 0.0: 4463 of 10356 cases, 94 archetypes.

| archetype | value | case | bad cases | note |
|---|---|---|---|---|
| landmark/cathedral | 467 | temperate / slope / s0 | 42 | sealed_void@12/29/17 purposeless_room@19/0/6 fall_edge@1/0/29 |
| noble/castle | 390 | alpine / flat / s1 | 84 | sealed_void@2/6/25 unreached_furnished_room@5/7/43 cramped_room@34/22/22 blocked_door@12/2/37 dead_ladder@24/10/11 fall_edge@38/13/24 |
| deep/hall | 304 | northern / cliff / s0 | 75 | sealed_void@14/8/9 fall_edge@15/0/0 |
| civic/monastery | 263 | karst / flat / s1 | 84 | sealed_void@22/9/11 cramped_room@3/0/7 blocked_door@14/0/6 unreached_door@14/0/6 fall_edge@14/6/0 |
| civic/convent | 128 | karst / flat / s0 | 84 | sealed_void@22/11/12 cramped_room@9/0/12 blocked_door@5/0/7 unreached_door@5/0/7 fall_edge@15/4/1 |
| noble/palace | 110 | mediterranean / slope / s0 | 18 | sealed_void@19/6/3 dead_ladder@25/10/7 fall_edge@33/5/19 |
| landmark/great_hall | 95 | moorland / flat / s1 | 24 | sealed_void@10/6/10 |
| storage/warehouse | 78 | northern / flat / s1 | 72 | sealed_void@10/12/9 station_unusable:barrel@7/3/1 blocked_door@8/5/3 |
| house/courtyard_house | 69 | mediterranean / slope / s0 | 9 | sealed_void@5/0/6 unreached_furnished_room@2/4/11 station_unusable:chest@3/0/11 station_unusable:barrel@11/0/10 unreached_door@2/0/8 bed_blocked@1/0/6 station_u... |
| commerce/bank | 55 | bayou / slope / s1 | 34 | unreached_furnished_room@9/7/6 sealed_void@10/9/6 bad_flight@10/2/10 station_unusable:anvil@10/7/5 station_unusable:blast_furnace@10/7/4 station_unusable:lecter... |
| civic/town_hall | 50 | wisteria / flat / s0 | 84 | sealed_void@2/13/8 bad_flight@6/13/3 fall_edge@6/13/14 |
| landmark/bell_tower | 49 | bayou / flat / s0 | 21 | cramped_room@7/27/2 sealed_void@5/34/6 fall_edge@8/29/3 |
| house/townhouse | 47 | alpine / flat / s0 | 12 | sealed_void@5/13/6 blocked_door@1/5/1 |
| work/watermill | 46 | steppe / flat / s1 | 72 | sealed_void@3/9/5 |
| noble/keep | 45 | northern / hills / s0 | 56 | unreached_furnished_room@9/10/0 cramped_room@16/-1/17 sealed_void@20/4/1 dead_ladder@12/8/3 roof_hole@4/-1/6 fall_edge@3/5/18 |

## unreached_levels (playability)

whole storeys with rooms that no stair or ladder reaches. Bad when > 0.0: 2144 of 10356 cases, 58 archetypes.

| archetype | value | case | bad cases | note |
|---|---|---|---|---|
| landmark/cathedral | 21 | temperate / flat / s0 | 35 | sealed_void@12/29/17 |
| deep/hall | 15 | northern / cliff / s0 | 70 | sealed_void@14/8/9 fall_edge@15/0/0 |
| civic/monastery | 10 | temperate / flat / s0 | 84 | sealed_void@24/9/9 purposeless_room@1/0/7 cramped_room@11/0/7 blocked_door@5/0/12 roof_hole@13/0/11 |
| noble/castle | 8 | temperate / slope / s1 | 17 | sealed_void@5/12/36 unreached_furnished_room@19/5/9 cramped_room@11/6/35 station_unusable:barrel@9/-1/12 station_unusable:chest@9/-1/11 dead_ladder@21/9/9 bad_f... |
| civic/convent | 6 | maritime / flat / s0 | 81 | sealed_void@22/9/11 cramped_room@24/1/13 blocked_door@5/0/7 |
| work/watermill | 4 | moorland / river / s0 | 72 | unreached_furnished_room@8/5/3 sealed_void@6/7/9 blocked_door@5/5/1 unreached_door@5/5/1 |
| civic/town_hall | 3 | northern / flat / s0 | 76 | sealed_void@11/13/6 unreached_furnished_room@9/6/4 blocked_door@4/6/3 |
| commerce/market_hall | 3 | northern / flat / s0 | 72 | cramped_room@4/1/13 sealed_void@5/11/12 blocked_door@9/1/15 |
| storage/warehouse | 3 | temperate / flat / s0 | 72 | sealed_void@12/9/14 station_unusable:barrel@7/3/1 blocked_door@7/5/3 fall_edge@10/6/14 |
| noble/hunting_lodge | 3 | temperate / flat / s0 | 57 | sealed_void@2/11/11 cramped_room@1/0/3 fall_edge@15/10/3 |
| law/courthouse | 3 | tropical / flat / s0 | 18 | sealed_void@8/9/4 fall_edge@3/4/14 |
| civic/playhouse | 2 | alpine / flat / s0 | 84 | sealed_void@5/10/16 cramped_room@3/0/12 fall_edge@2/5/3 |
| work/windmill | 2 | temperate / flat / s0 | 84 | unreached_furnished_room@5/4/8 sealed_void@7/4/4 |
| work/fishery | 2 | maritime / flat / s1 | 75 | cramped_room@3/0/3 sealed_void@1/8/4 |
| noble/stables | 2 | temperate / hills / s1 | 60 | sealed_void@14/9/17 fall_edge@2/6/13 |

## blocked_doors (playability)

doors with a wall or a drop on one side. Bad when > 0.0: 1214 of 10356 cases, 37 archetypes.

| archetype | value | case | bad cases | note |
|---|---|---|---|---|
| house/tenement | 7 | maritime / flat / s0 | 84 | purposeless_room@5/12/2 sealed_void@3/17/7 blocked_door@8/4/10 |
| house/courtyard_house | 6 | karst / slope / s0 | 6 | cramped_room@9/0/6 unreached_furnished_room@2/3/2 sealed_void@6/3/2 unreached_door@2/0/8 blocked_door@6/0/8 station_unusable:smoker@1/0/3 station_unusable:furna... |
| storage/warehouse | 4 | desert / flat / s0 | 84 | station_unusable:barrel@7/3/1 blocked_door@7/5/3 |
| civic/university | 2 | temperate / flat / s1 | 84 | purposeless_room@11/5/3 sealed_void@16/6/16 blocked_door@14/0/6 |
| commerce/market_hall | 2 | temperate / flat / s0 | 84 | sealed_void@6/12/8 blocked_door@9/1/15 unreached_door@6/13/2 |
| work/carpentry | 2 | temperate / flat / s1 | 84 | sealed_void@4/6/5 unreached_furnished_room@5/5/2 blocked_door@3/0/3 unreached_door@5/5/1 |
| civic/town_hall | 2 | northern / flat / s0 | 72 | sealed_void@11/13/6 unreached_furnished_room@9/6/4 blocked_door@4/6/3 |
| civic/observatory | 2 | temperate / flat / s0 | 60 | sealed_void@8/2/8 blocked_door@1/16/7 |
| civic/convent | 2 | temperate / flat / s0 | 42 | sealed_void@22/9/13 cramped_room@24/1/10 blocked_door@5/0/7 |
| house/townhouse | 2 | northern / flat / s0 | 36 | blocked_door@1/5/1 |
| house/croft | 2 | northern / slope / s1 | 20 | sealed_void@4/3/11 unreached_door@3/0/10 blocked_door@2/0/7 |
| house/manor | 1 | temperate / flat / s0 | 84 | cramped_room@14/1/9 blocked_door@9/5/7 |
| civic/monastery | 1 | temperate / flat / s0 | 70 | sealed_void@24/9/9 purposeless_room@1/0/7 cramped_room@11/0/7 blocked_door@5/0/12 roof_hole@13/0/11 |
| house/reeve_house | 1 | temperate / flat / s0 | 42 | cramped_room@3/0/3 blocked_door@4/4/2 |
| work/builders_yard | 1 | temperate / flat / s1 | 42 | sealed_void@5/0/12 chest_lid@2/0/12 blocked_door@2/4/7 |

## unreached_doors (playability)

doors nobody can walk to. Bad when > 0.0: 387 of 10356 cases, 34 archetypes.

| archetype | value | case | bad cases | note |
|---|---|---|---|---|
| house/courtyard_house | 8 | desert / slope / s1 | 12 | unreached_door@9/0/7 |
| commerce/caravanserai | 3 | desert / flat / s0 | 30 | cramped_room@24/4/22 unreached_furnished_room@12/4/3 sealed_void@10/0/28 unreached_door@13/0/1 blocked_door@13/4/5 fall_edge@20/4/22 |
| house/tenement | 3 | tropical / flat / s1 | 12 | purposeless_room@1/12/6 cramped_room@2/8/3 blocked_door@8/4/10 unreached_door@8/4/10 fall_edge@6/10/11 |
| storage/warehouse | 2 | tropical / flat / s0 | 12 | sealed_void@7/12/11 blocked_door@8/5/3 unreached_door@8/5/3 fall_edge@8/6/15 |
| house/townhouse | 2 | tropical / flat / s0 | 9 | blocked_door@1/5/2 unreached_door@1/5/2 fall_edge@5/6/11 |
| civic/convent | 2 | tropical / flat / s0 | 6 | cramped_room@9/0/12 sealed_void@19/7/14 blocked_door@5/0/7 unreached_door@5/0/7 fall_edge@15/4/1 |
| noble/castle | 2 | mediterranean / flat / s0 | 6 | sealed_void@24/8/4 unreached_furnished_room@5/0/41 purposeless_room@43/8/4 bad_flight@37/0/45 unreached_door@6/0/40 blocked_door@6/0/9 dead_ladder@26/11/8 roof_... |
| work/carpentry | 1 | temperate / flat / s0 | 57 | sealed_void@9/5/6 unreached_furnished_room@9/5/2 blocked_door@4/0/3 unreached_door@5/5/1 |
| house/croft | 1 | temperate / slope / s0 | 20 | unreached_furnished_room@3/4/8 sealed_void@2/3/6 unreached_door@3/0/7 |
| work/watermill | 1 | temperate / flat / s1 | 20 | unreached_furnished_room@4/5/8 sealed_void@6/8/7 blocked_door@6/5/1 unreached_door@6/5/1 |
| work/fishery | 1 | tropical / flat / s0 | 15 | sealed_void@9/4/2 cramped_room@6/0/4 unreached_door@8/0/7 |
| storage/storehouse | 1 | alpine / flat / s1 | 15 | unreached_furnished_room@1/5/3 sealed_void@3/7/5 station_unusable:barrel@3/4/0 blocked_door@3/5/2 unreached_door@3/5/2 |
| work/chandler | 1 | tropical / flat / s0 | 15 | cramped_room@2/0/4 unreached_door@1/0/6 |
| work/brewery | 1 | temperate / slope / s0 | 13 | unreached_furnished_room@1/0/2 blocked_door@2/0/0 unreached_door@2/0/0 |
| commerce/market_hall | 1 | temperate / flat / s0 | 12 | sealed_void@6/12/8 blocked_door@9/1/15 unreached_door@6/13/2 |

## entrances_unreached (playability)

registered entrances not reached. Bad when > 0.0: 9 of 10356 cases, 2 archetypes.

| archetype | value | case | bad cases | note |
|---|---|---|---|---|
| work/carpentry | 1 | tropical / flat / s1 | 6 | sealed_void@4/6/5 blocked_door@3/0/3 entrance_unreached@2/0/3 |
| landmark/pagoda | 1 | karst / flat / s0 | 3 | sealed_void@7/3/14 blocked_door@8/2/9 entrance_unreached@8/2/8 fall_edge@13/6/8 |

## bad_flights (playability)

stair flights that end at a wall/ceiling or lack headroom. Bad when > 0.0: 271 of 10356 cases, 19 archetypes.

| archetype | value | case | bad cases | note |
|---|---|---|---|---|
| noble/castle | 49 | mediterranean / hills / s1 | 22 | purposeless_room@38/9/4 sealed_void@10/6/33 unreached_furnished_room@19/9/6 cramped_room@38/9/40 bad_flight@31/0/45 blocked_door@6/1/9 dead_ladder@23/12/8 roof_... |
| law/courthouse | 7 | temperate / flat / s0 | 36 | sealed_void@6/12/7 cramped_room@8/7/6 bad_flight@6/13/4 fall_edge@17/13/11 |
| landmark/cathedral | 5 | northern / hills / s1 | 1 | sealed_void@12/7/19 bad_flight@15/4/26 fall_edge@3/4/27 |
| work/logging_camp | 4 | karst / flat / s0 | 3 | purposeless_room@6/-2/3 bad_flight@6/3/12 fall_edge@4/4/11 |
| farm/terrace_field | 2 | temperate / slope / s0 | 28 | bad_flight@9/-2/1 |
| infra/dock | 2 | temperate / slope / s1 | 13 | bad_flight@11/-2/5 |
| civic/chapel | 2 | temperate / flat / s0 | 12 | sealed_void@5/7/6 bad_flight@1/6/6 fall_edge@10/6/7 |
| landscape/statue | 2 | steppe / flat / s0 | 6 | bad_flight@1/0/2 |
| deep/dwelling | 2 | wisteria / flat / s1 | 6 | bad_flight@8/8/-1 fall_edge@0/11/0 |
| farm/field | 2 | moorland / slope / s1 | 5 | bad_flight@7/-4/9 |
| noble/palace | 2 | karst / flat / s0 | 3 | sealed_void@20/6/4 bad_flight@20/0/29 dead_ladder@25/10/7 roof_hole@39/0/32 fall_edge@33/5/19 |
| deep/lift | 1 | temperate / flat / s0 | 84 | sealed_void@5/-5/4 purposeless_room@5/-20/3 cramped_room@3/-24/2 bad_flight@7/-7/2 fall_edge@5/0/5 |
| defense/wall | 1 | temperate / slope / s1 | 14 | bad_flight@0/0/2 fall_edge@2/7/4 |
| civic/town_hall | 1 | temperate / flat / s0 | 12 | sealed_void@2/13/8 bad_flight@6/13/3 fall_edge@6/13/14 |
| deep/hall | 1 | temperate / flat / s1 | 12 | sealed_void@10/10/9 unreached_furnished_room@2/0/3 cramped_room@6/1/25 purposeless_room@19/0/4 bad_flight@11/0/24 blocked_door@4/0/5 |

## dead_ladders (playability)

ladders with no floor to step off at the top. Bad when > 0.0: 255 of 10356 cases, 6 archetypes.

| archetype | value | case | bad cases | note |
|---|---|---|---|---|
| noble/castle | 2 | temperate / flat / s0 | 72 | sealed_void@22/10/21 cramped_room@42/6/20 unreached_furnished_room@19/6/6 dead_ladder@24/10/9 bad_flight@4/14/37 fall_edge@37/6/6 |
| noble/keep | 1 | temperate / flat / s0 | 84 | sealed_void@3/5/1 cramped_room@16/0/17 dead_ladder@12/9/3 roof_hole@7/0/11 fall_edge@16/5/0 |
| farm/orchard | 1 | temperate / flat / s0 | 65 | dead_ladder@2/1/9 |
| noble/palace | 1 | desert / flat / s0 | 18 | sealed_void@19/6/3 purposeless_room@41/0/6 dead_ladder@25/10/7 fall_edge@33/5/19 |
| work/barn | 1 | desert / flat / s0 | 12 | sealed_void@2/5/9 dead_ladder@0/5/5 fall_edge@0/4/6 |
| house/farmhouse | 1 | mesa / flat / s0 | 4 | sealed_void@2/0/10 dead_ladder@11/8/6 roof_hole@2/5/10 fall_edge@11/6/6 |

## beds_no_free_side (playability)

beds with no reachable free side. Bad when > 0.0: 25 of 10356 cases, 5 archetypes.

| archetype | value | case | bad cases | note |
|---|---|---|---|---|
| house/courtyard_house | 4 | desert / flat / s0 | 6 | unreached_furnished_room@9/0/6 sealed_void@9/3/11 station_unusable:chest@3/0/11 station_unusable:barrel@11/0/10 unreached_door@2/0/8 bed_blocked@1/0/6 station_u... |
| house/farmhouse | 1 | tropical / flat / s0 | 12 | cramped_room@9/2/9 bed_blocked@6/2/10 |
| noble/palace | 1 | desert / hills / s0 | 3 | purposeless_room@4/-1/44 sealed_void@21/5/7 bed_blocked@35/4/21 dead_ladder@25/9/7 fall_edge@33/5/4 |
| civic/orphanage | 1 | tropical / slope / s0 | 2 | cramped_room@11/4/8 bed_blocked@2/8/6 fall_edge@0/9/9 |
| noble/castle | 1 | moorland / flat / s0 | 2 | sealed_void@22/5/8 dead_ladder@25/8/8 bed_blocked@43/15/37 fall_edge@1/5/17 |

## workstations_unusable (playability)

workstations, chests and barrels with no reachable face. Bad when > 0.0: 637 of 10356 cases, 21 archetypes.

| archetype | value | case | bad cases | note |
|---|---|---|---|---|
| house/courtyard_house | 26 | desert / flat / s0 | 7 | unreached_furnished_room@9/0/6 sealed_void@9/3/11 station_unusable:chest@3/0/11 station_unusable:barrel@11/0/10 unreached_door@2/0/8 bed_blocked@1/0/6 station_u... |
| noble/castle | 6 | temperate / slope / s1 | 1 | sealed_void@5/12/36 unreached_furnished_room@19/5/9 cramped_room@11/6/35 station_unusable:barrel@9/-1/12 station_unusable:chest@9/-1/11 dead_ladder@21/9/9 bad_f... |
| commerce/tavern | 4 | temperate / slope / s1 | 66 | sealed_void@8/4/5 cramped_room@10/1/2 station_unusable:barrel@10/-4/8 |
| house/tenement | 4 | temperate / slope / s1 | 20 | purposeless_room@7/12/2 sealed_void@4/17/5 station_unusable:barrel@10/0/11 station_unusable:composter@9/0/11 station_unusable:water_cauldron@0/0/11 blocked_door... |
| storage/storehouse | 4 | alpine / slope / s1 | 15 | unreached_furnished_room@5/5/6 sealed_void@3/7/5 station_unusable:barrel@6/0/6 blocked_door@3/5/2 unreached_door@3/5/2 |
| work/alchemist_tower | 3 | mediterranean / flat / s0 | 84 | station_unusable:barrel@5/5/2 station_unusable:brewing_stand@5/6/2 blocked_door@4/14/6 |
| commerce/caravan_yard | 3 | northern / slope / s1 | 45 | sealed_void@7/5/13 station_unusable:water_cauldron@17/0/13 |
| commerce/bank | 3 | bayou / flat / s1 | 3 | unreached_furnished_room@4/8/10 bad_flight@10/3/10 station_unusable:anvil@10/8/5 station_unusable:blast_furnace@10/8/4 station_unusable:lectern@2/8/4 |
| work/brick_kiln | 2 | temperate / flat / s0 | 84 | sealed_void@1/3/8 station_unusable:furnace@6/0/5 |
| house/merchant_house | 2 | karst / flat / s0 | 15 | cramped_room@2/0/3 blocked_door@1/5/1 unreached_door@1/5/1 station_unusable:barrel@1/13/2 fall_edge@1/10/0 |
| landscape/well | 1 | temperate / flat / s0 | 84 | sealed_void@3/2/4 station_unusable:cauldron@3/1/3 |
| storage/warehouse | 1 | temperate / flat / s0 | 63 | sealed_void@12/9/14 station_unusable:barrel@7/3/1 blocked_door@7/5/3 fall_edge@10/6/14 |
| work/smelter | 1 | temperate / flat / s0 | 60 | sealed_void@10/4/10 station_unusable:lava_cauldron@7/0/9 roof_hole@8/9/10 fall_edge@3/4/11 |
| work/bakery | 1 | temperate / flat / s1 | 24 | sealed_void@1/5/4 station_unusable:barrel@6/0/4 |
| house/shack | 1 | temperate / slope / s1 | 14 | station_unusable:water_cauldron@1/0/7 |

## chest_lids_blocked (playability)

chests under a solid block (cannot open). Bad when > 0.0: 177 of 10356 cases, 3 archetypes.

| archetype | value | case | bad cases | note |
|---|---|---|---|---|
| civic/library | 1 | temperate / flat / s0 | 84 | sealed_void@5/8/7 chest_lid@7/5/10 fall_edge@5/6/9 |
| work/charcoal_kiln | 1 | temperate / flat / s0 | 54 | chest_lid@9/0/11 |
| work/builders_yard | 1 | temperate / flat / s1 | 39 | sealed_void@5/0/12 chest_lid@2/0/12 blocked_door@2/4/7 |

## dark_room_cells (playability)

room cells under light 8. Bad when > 0.0: 0 of 10356 cases, 0 archetypes.

(none)

## light_min_room (playability)

darkest room cell (block light). Bad when < 8.0: 0 of 6862 cases, 0 archetypes.

(none)

## roof_hole_cells (playability)

open-sky cells inside the roof (rain gets in). Bad when > 0.0: 458 of 10356 cases, 24 archetypes.

| archetype | value | case | bad cases | note |
|---|---|---|---|---|
| noble/palace | 12 | karst / flat / s0 | 6 | sealed_void@20/6/4 bad_flight@20/0/29 dead_ladder@25/10/7 roof_hole@39/0/32 fall_edge@33/5/19 |
| noble/keep | 9 | karst / flat / s0 | 45 | sealed_void@18/5/2 purposeless_room@16/19/18 cramped_room@8/5/16 dead_ladder@12/9/3 roof_hole@3/0/6 fall_edge@2/5/6 |
| noble/castle | 9 | karst / flat / s1 | 30 | cramped_room@6/5/24 sealed_void@5/6/36 purposeless_room@27/2/35 unreached_furnished_room@21/5/7 dead_ladder@24/8/8 roof_hole@9/0/8 fall_edge@36/4/18 |
| landmark/temple | 8 | steppe / flat / s0 | 3 | cramped_room@10/2/14 roof_hole@15/2/13 |
| work/brewery | 4 | tropical / flat / s0 | 6 | cramped_room@4/0/5 roof_hole@11/0/2 |
| deep/glow_garden | 3 | temperate / flat / s0 | 84 | roof_hole@9/0/3 |
| civic/convent | 3 | temperate / flat / s1 | 30 | sealed_void@23/9/3 purposeless_room@3/0/4 cramped_room@24/1/12 roof_hole@10/0/8 |
| law/courthouse | 3 | desert / flat / s0 | 12 | roof_hole@6/0/7 |
| work/smelter | 2 | desert / flat / s1 | 72 | roof_hole@6/0/5 |
| civic/monastery | 2 | temperate / flat / s0 | 30 | sealed_void@24/9/9 purposeless_room@1/0/7 cramped_room@11/0/7 blocked_door@5/0/12 roof_hole@13/0/11 |
| civic/observatory | 2 | desert / flat / s0 | 24 | cramped_room@5/5/10 roof_hole@7/6/6 fall_edge@5/5/4 |
| extract/quarry_pit | 2 | northern / slope / s0 | 18 | roof_hole@4/-9/10 fall_edge@11/1/8 |
| work/glassworks | 2 | desert / flat / s0 | 15 | cramped_room@4/0/5 sealed_void@3/3/13 roof_hole@10/0/8 |
| house/farmhouse | 2 | desert / flat / s0 | 12 | roof_hole@2/5/10 fall_edge@6/7/12 |
| commerce/hostel | 2 | desert / flat / s0 | 6 | roof_hole@3/0/9 fall_edge@9/6/6 |

## open_roofed_share (playability)

share of roofed floor that is not enclosed (porches, or walls with gaps). Bad when > 0.6: 3377 of 9325 cases, 96 archetypes.

| archetype | value | case | bad cases | note |
|---|---|---|---|---|
| defense/wall | 1.000 | temperate / flat / s0 | 84 | fall_edge@2/8/8 |
| work/smelter | 1.000 | desert / flat / s0 | 84 | roof_hole@6/0/6 |
| work/sawmill | 1.000 | temperate / flat / s0 | 84 |  |
| extract/quarry_pit | 1.000 | temperate / flat / s0 | 84 | fall_edge@8/-5/23 |
| infra/walkway | 1.000 | temperate / flat / s0 | 84 | fall_edge@5/8/2 |
| landscape/bell | 1.000 | temperate / flat / s0 | 84 |  |
| law/pillory | 1.000 | temperate / flat / s0 | 84 |  |
| law/gallows | 1.000 | temperate / flat / s0 | 84 |  |
| landscape/festival_ground | 1.000 | temperate / flat / s0 | 84 |  |
| commerce/market_stall | 1.000 | temperate / flat / s0 | 84 |  |
| farm/field | 1.000 | temperate / flat / s0 | 84 |  |
| farm/orchard | 1.000 | temperate / flat / s0 | 84 | dead_ladder@2/1/9 |
| work/quarry | 1.000 | temperate / flat / s0 | 84 |  |
| landscape/well | 1.000 | temperate / flat / s0 | 78 | sealed_void@3/2/4 station_unusable:cauldron@3/1/3 |
| civic/shrine | 1.000 | temperate / flat / s0 | 72 |  |

## rooms_no3x3 (playability)

rooms of 6+ cells with no 3x3 clear floor. Bad when > 0.0: 5092 of 10356 cases, 95 archetypes.

| archetype | value | case | bad cases | note |
|---|---|---|---|---|
| noble/castle | 27 | alpine / hills / s1 | 84 | cramped_room@34/2/15 sealed_void@38/12/45 unreached_furnished_room@35/5/46 purposeless_room@3/5/42 blocked_door@12/5/37 dead_ladder@24/12/11 fall_edge@6/13/20 |
| commerce/caravanserai | 23 | mediterranean / flat / s0 | 30 | cramped_room@18/0/4 unreached_furnished_room@12/4/3 sealed_void@10/0/28 unreached_door@13/0/1 blocked_door@13/4/5 fall_edge@16/4/6 |
| noble/palace | 19 | karst / hills / s0 | 18 | sealed_void@13/9/39 cramped_room@36/4/19 bad_flight@20/-1/29 bed_blocked@35/4/21 dead_ladder@25/9/7 roof_hole@5/-1/35 fall_edge@5/4/30 |
| civic/university | 18 | tropical / flat / s0 | 84 | purposeless_room@12/5/3 cramped_room@5/0/5 blocked_door@13/0/6 fall_edge@14/4/17 |
| civic/monastery | 11 | karst / slope / s0 | 84 | sealed_void@22/12/8 cramped_room@6/0/9 purposeless_room@27/0/19 blocked_door@5/0/12 unreached_door@5/0/12 fall_edge@10/4/12 |
| noble/hunting_lodge | 11 | karst / slope / s0 | 16 | sealed_void@8/7/15 cramped_room@4/4/17 fall_edge@2/3/0 |
| house/tenement | 9 | desert / flat / s1 | 84 | blocked_door@8/4/10 fall_edge@6/16/0 |
| noble/stables | 9 | karst / slope / s1 | 18 | cramped_room@17/4/14 sealed_void@14/7/17 fall_edge@0/1/8 |
| commerce/inn | 8 | temperate / flat / s1 | 84 | cramped_room@1/5/6 sealed_void@11/14/6 fall_edge@9/12/6 |
| noble/keep | 8 | moorland / flat / s0 | 84 | sealed_void@11/11/19 dead_ladder@12/9/3 roof_hole@16/24/16 fall_edge@14/5/21 |
| civic/convent | 7 | tropical / flat / s1 | 84 | sealed_void@23/9/3 cramped_room@4/0/15 roof_hole@10/0/8 fall_edge@2/6/0 |
| commerce/market_hall | 6 | tropical / slope / s0 | 84 | cramped_room@3/0/5 purposeless_room@12/0/17 sealed_void@7/10/8 blocked_door@9/0/15 fall_edge@0/6/13 |
| law/courthouse | 6 | tropical / slope / s0 | 47 | sealed_void@13/10/4 fall_edge@11/5/14 |
| civic/infirmary | 5 | tropical / hills / s0 | 84 | sealed_void@8/7/10 fall_edge@1/4/3 |
| commerce/tavern | 5 | bayou / hills / s1 | 82 | sealed_void@5/9/7 roof_hole@7/4/1 fall_edge@12/6/2 |

## cramped_rooms (playability)

furnished rooms with fewer than 6 free cells. Bad when > 0.0: 1741 of 10356 cases, 71 archetypes.

| archetype | value | case | bad cases | note |
|---|---|---|---|---|
| commerce/caravanserai | 23 | desert / flat / s0 | 30 | cramped_room@24/4/22 unreached_furnished_room@12/4/3 sealed_void@10/0/28 unreached_door@13/0/1 blocked_door@13/4/5 fall_edge@20/4/22 |
| noble/castle | 8 | karst / flat / s1 | 63 | cramped_room@6/5/24 sealed_void@5/6/36 purposeless_room@27/2/35 unreached_furnished_room@21/5/7 dead_ladder@24/8/8 roof_hole@9/0/8 fall_edge@36/4/18 |
| civic/university | 8 | karst / slope / s0 | 15 | purposeless_room@12/5/3 cramped_room@17/0/10 blocked_door@13/0/6 fall_edge@14/4/17 |
| commerce/market_hall | 6 | tropical / flat / s0 | 17 | cramped_room@8/0/7 sealed_void@7/10/8 blocked_door@9/0/15 fall_edge@0/6/13 |
| civic/convent | 5 | tropical / flat / s0 | 84 | cramped_room@9/0/12 sealed_void@19/7/14 blocked_door@5/0/7 unreached_door@5/0/7 fall_edge@15/4/1 |
| civic/monastery | 5 | tropical / flat / s1 | 84 | sealed_void@22/9/11 cramped_room@3/0/7 blocked_door@14/0/6 unreached_door@14/0/6 fall_edge@14/6/0 |
| noble/hunting_lodge | 5 | karst / hills / s0 | 71 | sealed_void@12/10/12 cramped_room@15/-1/10 fall_edge@0/5/12 |
| noble/stables | 5 | karst / hills / s0 | 5 | cramped_room@13/4/13 purposeless_room@15/7/14 sealed_void@17/4/13 fall_edge@0/1/6 |
| deep/hall | 4 | desert / flat / s0 | 18 | cramped_room@9/1/18 |
| house/courtyard_house | 4 | karst / slope / s0 | 7 | cramped_room@9/0/6 unreached_furnished_room@2/3/2 sealed_void@6/3/2 unreached_door@2/0/8 blocked_door@6/0/8 station_unusable:smoker@1/0/3 station_unusable:furna... |
| landmark/bell_tower | 4 | northern / flat / s0 | 6 | cramped_room@7/22/6 fall_edge@3/24/0 |
| commerce/counting_house | 4 | tropical / flat / s0 | 3 | cramped_room@3/4/6 fall_edge@6/5/8 |
| commerce/inn | 3 | mediterranean / hills / s1 | 70 | cramped_room@1/5/5 sealed_void@10/5/5 fall_edge@9/12/5 |
| noble/keep | 3 | northern / flat / s0 | 55 | unreached_furnished_room@9/11/3 sealed_void@18/5/1 cramped_room@6/0/4 dead_ladder@12/9/3 roof_hole@3/0/6 fall_edge@14/5/21 |
| work/tannery | 3 | temperate / flat / s1 | 39 | cramped_room@9/4/7 sealed_void@3/8/7 |

## purposeless_rooms (playability)

dead-end rooms with no POI and at most one piece of furniture. Bad when > 0.0: 849 of 10356 cases, 35 archetypes.

| archetype | value | case | bad cases | note |
|---|---|---|---|---|
| noble/castle | 4 | wisteria / hills / s1 | 40 | cramped_room@34/2/15 sealed_void@38/12/45 unreached_furnished_room@35/5/46 purposeless_room@1/5/22 blocked_door@12/5/37 dead_ladder@24/12/11 fall_edge@6/13/20 |
| noble/palace | 4 | desert / flat / s0 | 6 | sealed_void@19/6/3 purposeless_room@41/0/6 dead_ladder@25/10/7 fall_edge@33/5/19 |
| deep/hall | 2 | mesa / flat / s0 | 30 | purposeless_room@10/6/17 sealed_void@3/3/6 fall_edge@22/5/9 |
| landmark/cathedral | 2 | alpine / flat / s0 | 16 | sealed_void@11/5/2 purposeless_room@16/0/2 fall_edge@19/19/14 |
| deep/lift | 1 | temperate / flat / s0 | 84 | sealed_void@5/-5/4 purposeless_room@5/-20/3 cramped_room@3/-24/2 bad_flight@7/-7/2 fall_edge@5/0/5 |
| civic/university | 1 | temperate / flat / s0 | 84 | purposeless_room@12/5/3 sealed_void@17/6/22 blocked_door@13/0/6 |
| law/toll_gate | 1 | temperate / flat / s0 | 84 | purposeless_room@9/0/3 cramped_room@1/0/2 |
| house/tenement | 1 | temperate / flat / s0 | 66 | purposeless_room@5/12/2 sealed_void@4/17/5 blocked_door@8/4/10 |
| landmark/clock_tower | 1 | temperate / flat / s1 | 57 | purposeless_room@5/0/2 sealed_void@5/28/3 fall_edge@3/23/6 |
| work/logging_camp | 1 | temperate / flat / s0 | 51 | purposeless_room@6/-2/3 |
| work/brewery | 1 | temperate / flat / s0 | 47 | sealed_void@1/7/1 purposeless_room@1/0/2 |
| defense/gatehouse | 1 | temperate / flat / s0 | 42 | purposeless_room@12/6/7 sealed_void@2/6/6 fall_edge@9/14/4 |
| landmark/bell_tower | 1 | temperate / flat / s1 | 39 | sealed_void@4/28/2 purposeless_room@5/0/2 fall_edge@4/22/0 |
| civic/convent | 1 | temperate / flat / s1 | 37 | sealed_void@23/9/3 purposeless_room@3/0/4 cramped_room@24/1/12 roof_hole@10/0/8 |
| civic/monastery | 1 | temperate / flat / s0 | 37 | sealed_void@24/9/9 purposeless_room@1/0/7 cramped_room@11/0/7 blocked_door@5/0/12 roof_hole@13/0/11 |

## tight_headroom_share (playability)

share of room floor with only 2 blocks of headroom. Bad when > 0.35: 218 of 6862 cases, 25 archetypes.

| archetype | value | case | bad cases | note |
|---|---|---|---|---|
| house/shack | 1.000 | desert / flat / s1 | 6 | sealed_void@1/4/5 |
| work/charcoal_kiln | 1.000 | desert / flat / s0 | 6 | cramped_room@11/0/10 |
| house/stilt_house | 1.000 | tropical / slope / s1 | 4 | fall_edge@5/0/10 |
| civic/school | 1.000 | tropical / hills / s0 | 3 | fall_edge@0/3/7 |
| commerce/tavern | 1.000 | tropical / hills / s1 | 2 | sealed_void@5/9/8 fall_edge@0/5/4 |
| civic/library | 0.897 | tropical / hills / s1 | 4 | sealed_void@4/13/5 cramped_room@3/4/10 chest_lid@7/8/13 fall_edge@0/5/12 |
| law/courthouse | 0.777 | tropical / hills / s0 | 7 | sealed_void@5/9/9 fall_edge@1/4/8 |
| civic/orphanage | 0.741 | tropical / hills / s0 | 2 | fall_edge@9/4/11 |
| civic/infirmary | 0.700 | tropical / hills / s0 | 2 | sealed_void@8/7/10 fall_edge@1/4/3 |
| work/bakery | 0.667 | tropical / flat / s0 | 42 | sealed_void@6/5/4 |
| house/round_house | 0.625 | tropical / slope / s1 | 1 |  |
| house/cottage | 0.619 | tropical / flat / s0 | 27 | fall_edge@1/4/9 |
| house/shophouse | 0.538 | karst / flat / s1 | 3 | sealed_void@2/6/1 fall_edge@5/6/8 |
| house/croft | 0.522 | tropical / slope / s1 | 2 | sealed_void@2/5/7 cramped_room@1/5/10 fall_edge@7/1/15 |
| landmark/bell_tower | 0.500 | temperate / flat / s1 | 39 | sealed_void@4/28/2 purposeless_room@5/0/2 fall_edge@4/22/0 |

## fall_edge_cells (playability)

walked cells beside an unguarded drop of 4+. Bad when > 0.0: 4034 of 10356 cases, 117 archetypes.

| archetype | value | case | bad cases | note |
|---|---|---|---|---|
| noble/castle | 476 | maritime / hills / s1 | 71 | cramped_room@34/2/15 purposeless_room@35/5/46 sealed_void@4/8/16 blocked_door@12/5/37 dead_ladder@24/12/11 bad_flight@12/12/29 roof_hole@35/2/18 fall_edge@29/22... |
| noble/palace | 351 | mediterranean / hills / s0 | 18 | sealed_void@28/7/34 bed_blocked@35/4/21 dead_ladder@25/9/7 fall_edge@11/9/15 |
| noble/keep | 138 | moorland / slope / s1 | 84 | sealed_void@15/10/18 dead_ladder@10/8/3 roof_hole@12/23/15 fall_edge@15/4/8 |
| deep/hall | 131 | mesa / flat / s1 | 32 | purposeless_room@4/6/14 sealed_void@3/3/6 fall_edge@21/7/26 |
| landmark/pagoda | 128 | karst / slope / s0 | 12 | sealed_void@7/3/14 blocked_door@8/2/9 entrance_unreached@8/2/8 fall_edge@13/6/8 |
| landmark/cathedral | 105 | alpine / flat / s1 | 19 | sealed_void@8/11/16 purposeless_room@3/0/3 fall_edge@16/21/26 |
| extract/plantation | 102 | bayou / cliff / s0 | 45 | fall_edge@13/-3/4 |
| extract/mine | 100 | temperate / cliff / s0 | 84 | fall_edge@8/-9/14 |
| law/courthouse | 94 | alpine / slope / s1 | 64 | sealed_void@3/15/8 cramped_room@3/6/5 bad_flight@9/12/3 fall_edge@13/13/3 |
| civic/town_hall | 89 | wisteria / slope / s0 | 33 | sealed_void@11/14/7 bad_flight@6/12/3 fall_edge@0/1/3 |
| house/farmhouse | 86 | mesa / slope / s1 | 25 | cramped_room@2/0/5 dead_ladder@2/8/10 roof_hole@2/5/14 fall_edge@6/10/15 |
| extract/quarry_pit | 78 | wisteria / cliff / s0 | 84 | fall_edge@5/2/29 |
| infra/terrace | 75 | temperate / slope / s1 | 14 | fall_edge@32/0/19 |
| landmark/temple | 74 | desert / slope / s1 | 29 | sealed_void@9/7/9 purposeless_room@3/0/1 fall_edge@17/4/3 |
| landmark/lighthouse | 73 | tropical / coast / s1 | 24 | purposeless_room@6/0/12 sealed_void@4/3/6 bad_flight@7/3/8 fall_edge@0/4/1 |

## lava (playability)

lava placed. Bad when > 0.0: 0 of 10356 cases, 0 archetypes.

(none)

## water_roofed (playability)

water placed under a roof. Bad when > 0.0: 0 of 10356 cases, 0 archetypes.

(none)

## relief_share (detail)

facade relief (share of wall area with depth or shaped blocks). Bad when < 0.06: 1975 of 8259 cases, 73 archetypes.

| archetype | value | case | bad cases | note |
|---|---|---|---|---|
| deep/lift | 0.000 | temperate / flat / s0 | 84 |  |
| civic/playhouse | 0.000 | temperate / flat / s0 | 84 |  |
| landscape/bell | 0.000 | temperate / flat / s0 | 84 |  |
| work/masonry | 0.000 | desert / flat / s0 | 84 |  |
| work/quarry | 0.000 | temperate / flat / s0 | 84 |  |
| work/windmill | 0.000 | temperate / flat / s0 | 69 |  |
| law/gallows | 0.000 | temperate / flat / s0 | 66 |  |
| civic/cemetery | 0.000 | temperate / flat / s0 | 62 |  |
| landscape/statue | 0.000 | temperate / flat / s0 | 60 |  |
| infra/walkway | 0.000 | temperate / flat / s0 | 54 |  |
| landmark/clock_tower | 0.000 | karst / flat / s0 | 42 |  |
| law/pillory | 0.000 | temperate / flat / s1 | 42 |  |
| farm/terrace_field | 0.000 | temperate / hills / s1 | 28 |  |
| work/sawmill | 0.000 | desert / flat / s0 | 27 |  |
| landmark/bell_tower | 0.000 | bayou / flat / s0 | 15 |  |

## flat_patch_max (detail)

largest flat patch of one block on a facade (cells). Bad when > 24.0: 4743 of 10356 cases, 94 archetypes.

| archetype | value | case | bad cases | note |
|---|---|---|---|---|
| civic/university | 218 | desert / flat / s0 | 81 |  |
| civic/monastery | 209 | desert / flat / s0 | 84 |  |
| noble/stables | 208 | bayou / hills / s1 | 84 |  |
| civic/convent | 191 | desert / flat / s0 | 84 |  |
| commerce/caravanserai | 183 | mesa / flat / s1 | 30 |  |
| civic/playhouse | 181 | mesa / hills / s0 | 81 |  |
| defense/wall | 169 | tropical / hills / s1 | 52 |  |
| house/manor | 167 | mesa / slope / s1 | 61 |  |
| noble/keep | 153 | karst / slope / s1 | 84 |  |
| noble/hunting_lodge | 151 | desert / flat / s0 | 84 |  |
| storage/warehouse | 151 | northern / flat / s1 | 75 |  |
| noble/castle | 141 | wisteria / hills / s0 | 46 |  |
| landmark/great_hall | 140 | moorland / slope / s1 | 21 |  |
| deep/hall | 138 | mediterranean / flat / s1 | 84 |  |
| defense/palisade | 135 | moorland / hills / s0 | 72 |  |

## facade_blocks (detail)

distinct blocks per facade. Bad when < 2.0: 345 of 8259 cases, 21 archetypes.

| archetype | value | case | bad cases | note |
|---|---|---|---|---|
| work/quarry | 1.000 | temperate / flat / s0 | 84 |  |
| law/gallows | 1.000 | temperate / flat / s0 | 48 |  |
| landscape/statue | 1.000 | desert / flat / s1 | 48 |  |
| law/pillory | 1.000 | temperate / flat / s1 | 48 |  |
| work/windmill | 1.000 | temperate / flat / s0 | 39 |  |
| noble/keep | 1.000 | mesa / flat / s1 | 11 |  |
| farm/terrace_field | 1.000 | northern / slope / s0 | 10 |  |
| noble/castle | 1.000 | bayou / flat / s0 | 8 |  |
| storage/granary | 1.000 | tropical / flat / s0 | 6 |  |
| house/round_house | 1.000 | tropical / flat / s0 | 6 |  |
| house/farmhouse | 1.000 | steppe / flat / s0 | 3 |  |
| civic/cemetery | 1.000 | tropical / hills / s1 | 2 |  |
| work/sawmill | 1.500 | desert / flat / s0 | 12 |  |
| house/shack | 1.500 | mesa / flat / s0 | 3 |  |
| civic/shrine | 1.500 | desert / flat / s0 | 3 |  |

## dominant_share (detail)

share of the commonest block on a facade. Bad when > 0.8: 807 of 8259 cases, 62 archetypes.

| archetype | value | case | bad cases | note |
|---|---|---|---|---|
| work/quarry | 1.000 | temperate / flat / s0 | 84 |  |
| landscape/statue | 1.000 | desert / flat / s1 | 48 |  |
| law/pillory | 1.000 | temperate / flat / s1 | 48 |  |
| law/gallows | 1.000 | temperate / flat / s0 | 48 |  |
| noble/keep | 1.000 | mesa / flat / s1 | 46 |  |
| work/windmill | 1.000 | temperate / flat / s0 | 39 |  |
| noble/castle | 1.000 | bayou / flat / s0 | 25 |  |
| farm/terrace_field | 1.000 | northern / slope / s0 | 14 |  |
| storage/granary | 1.000 | tropical / flat / s0 | 7 |  |
| house/round_house | 1.000 | tropical / flat / s0 | 6 |  |
| civic/cemetery | 1.000 | tropical / hills / s1 | 4 |  |
| house/farmhouse | 1.000 | steppe / flat / s0 | 4 |  |
| law/courthouse | 0.977 | steppe / slope / s1 | 3 |  |
| house/shack | 0.974 | mesa / flat / s0 | 12 |  |
| noble/palace | 0.972 | karst / slope / s1 | 8 |  |

## windows_per_side (detail)

windows per facade (sides 5+ wide). Bad when < 1.0: 2762 of 7895 cases, 92 archetypes.

| archetype | value | case | bad cases | note |
|---|---|---|---|---|
| defense/wall | 0.000 | temperate / flat / s0 | 84 |  |
| work/sawmill | 0.000 | temperate / flat / s0 | 84 |  |
| house/hut | 0.000 | tropical / flat / s0 | 84 |  |
| deep/glow_garden | 0.000 | temperate / flat / s0 | 84 |  |
| house/hovel | 0.000 | temperate / flat / s0 | 84 |  |
| house/hermit_hut | 0.000 | temperate / flat / s0 | 84 |  |
| work/masonry | 0.000 | temperate / flat / s0 | 84 |  |
| deep/forge | 0.000 | temperate / flat / s0 | 84 |  |
| defense/palisade | 0.000 | temperate / flat / s0 | 84 |  |
| house/shack | 0.000 | temperate / flat / s0 | 84 |  |
| work/shipyard | 0.000 | temperate / flat / s0 | 84 |  |
| civic/tilt_yard | 0.000 | temperate / flat / s0 | 84 |  |
| work/windmill | 0.000 | temperate / flat / s0 | 81 |  |
| work/apiary | 0.000 | tropical / flat / s0 | 66 |  |
| work/smelter | 0.000 | temperate / flat / s0 | 60 |  |

## blind_sides (detail)

facades 5+ wide with no window. Bad when > 1.0: 2440 of 10356 cases, 96 archetypes.

| archetype | value | case | bad cases | note |
|---|---|---|---|---|
| deep/glow_garden | 4 | temperate / flat / s0 | 84 |  |
| house/hovel | 4 | temperate / flat / s0 | 84 |  |
| house/hermit_hut | 4 | temperate / flat / s0 | 84 |  |
| work/masonry | 4 | temperate / flat / s0 | 84 |  |
| deep/forge | 4 | temperate / flat / s0 | 84 |  |
| house/shack | 4 | temperate / flat / s0 | 84 |  |
| landscape/statue | 4 | temperate / flat / s0 | 60 |  |
| noble/keep | 4 | northern / flat / s1 | 57 |  |
| deep/hall | 4 | wisteria / flat / s0 | 54 |  |
| civic/cemetery | 4 | temperate / flat / s1 | 53 |  |
| work/apiary | 4 | tropical / flat / s0 | 42 |  |
| law/pillory | 4 | temperate / flat / s0 | 42 |  |
| landscape/well | 4 | moorland / flat / s0 | 36 |  |
| work/salt_works | 4 | wisteria / flat / s0 | 33 |  |
| work/tannery | 4 | tropical / flat / s0 | 30 |  |

## overhang_mean (detail)

roof overhang beyond the wall (blocks, median per side). Bad when < 0.25: 3412 of 8373 cases, 103 archetypes.

| archetype | value | case | bad cases | note |
|---|---|---|---|---|
| defense/wall | 0.000 | temperate / flat / s0 | 84 |  |
| civic/playhouse | 0.000 | temperate / flat / s0 | 84 |  |
| law/pillory | 0.000 | temperate / flat / s0 | 84 |  |
| defense/gatehouse | 0.000 | temperate / flat / s0 | 84 |  |
| noble/hunting_lodge | 0.000 | temperate / flat / s0 | 84 |  |
| defense/palisade | 0.000 | temperate / flat / s0 | 84 |  |
| civic/tilt_yard | 0.000 | temperate / flat / s0 | 84 |  |
| deep/glow_garden | 0.000 | temperate / flat / s0 | 84 |  |
| infra/walkway | 0.000 | temperate / flat / s0 | 84 |  |
| work/builders_yard | 0.000 | temperate / flat / s0 | 84 |  |
| deep/forge | 0.000 | temperate / flat / s0 | 84 |  |
| work/alchemist_tower | 0.000 | temperate / flat / s0 | 78 |  |
| civic/observatory | 0.000 | temperate / flat / s0 | 72 |  |
| deep/dwelling | 0.000 | temperate / flat / s0 | 72 |  |
| work/shipyard | 0.000 | temperate / flat / s1 | 72 |  |

## roof_complexity (detail)

roof form complexity (slope facings + hips/valleys + levels). Bad when < 3.0: 3794 of 10356 cases, 102 archetypes.

| archetype | value | case | bad cases | note |
|---|---|---|---|---|
| extract/mine | 0.000 | temperate / flat / s0 | 84 |  |
| infra/dock | 0.000 | temperate / flat / s1 | 84 |  |
| landscape/lamp_post | 0.000 | temperate / flat / s0 | 84 |  |
| landscape/park | 0.000 | temperate / flat / s0 | 84 |  |
| landscape/plaza | 0.000 | temperate / flat / s0 | 84 |  |
| extract/plantation | 0.000 | temperate / flat / s0 | 84 |  |
| farm/pasture | 0.000 | temperate / slope / s1 | 84 |  |
| infra/terrace | 0.000 | temperate / flat / s0 | 84 |  |
| farm/field | 0.000 | temperate / slope / s1 | 84 |  |
| farm/terrace_field | 0.000 | temperate / slope / s1 | 84 |  |
| landscape/garden | 0.000 | temperate / flat / s0 | 84 |  |
| extract/sand_pit | 0.000 | temperate / flat / s0 | 84 |  |
| landscape/fountain | 0.000 | temperate / flat / s0 | 84 |  |
| extract/clay_pit | 0.000 | temperate / flat / s0 | 84 |  |
| infra/road | 0.000 | temperate / flat / s0 | 84 |  |

## mirror_sym (detail)

left-right mirror likeness of the body. Bad when > 0.95: 1186 of 9312 cases, 49 archetypes.

| archetype | value | case | bad cases | note |
|---|---|---|---|---|
| defense/palisade | 1.000 | temperate / flat / s0 | 84 |  |
| commerce/market_stall | 1.000 | temperate / flat / s0 | 84 |  |
| extract/shaft | 1.000 | temperate / cliff / s1 | 82 |  |
| farm/field | 1.000 | temperate / flat / s0 | 81 |  |
| farm/pasture | 1.000 | temperate / flat / s0 | 75 |  |
| farm/orchard | 1.000 | temperate / flat / s0 | 70 |  |
| infra/walkway | 1.000 | temperate / flat / s0 | 67 |  |
| landscape/bell | 1.000 | northern / flat / s1 | 51 |  |
| civic/shrine | 1.000 | wisteria / flat / s0 | 45 |  |
| infra/dock | 1.000 | temperate / flat / s0 | 44 |  |
| farm/terrace_field | 1.000 | temperate / flat / s0 | 42 |  |
| house/hut | 1.000 | northern / flat / s0 | 41 |  |
| landscape/statue | 1.000 | northern / flat / s1 | 30 |  |
| infra/stairs | 1.000 | northern / hills / s0 | 19 |  |
| house/shack | 1.000 | bayou / flat / s0 | 18 |  |

## repeat_cols (detail)

facade columns repeating another exactly. Bad when > 0.75: 573 of 8088 cases, 25 archetypes.

| archetype | value | case | bad cases | note |
|---|---|---|---|---|
| civic/cemetery | 0.941 | tropical / hills / s1 | 42 |  |
| landmark/temple | 0.933 | mesa / slope / s0 | 14 |  |
| civic/tilt_yard | 0.929 | temperate / flat / s1 | 84 |  |
| defense/palisade | 0.926 | moorland / flat / s1 | 48 |  |
| farm/terrace_field | 0.900 | tropical / hills / s1 | 17 |  |
| infra/walkway | 0.889 | tropical / flat / s1 | 27 |  |
| storage/granary | 0.882 | tropical / flat / s0 | 10 |  |
| civic/bathhouse | 0.874 | karst / slope / s1 | 6 |  |
| deep/hall | 0.869 | tropical / flat / s1 | 24 |  |
| noble/castle | 0.864 | bayou / flat / s1 | 22 |  |
| work/quarry | 0.850 | moorland / flat / s0 | 81 |  |
| law/pillory | 0.821 | temperate / flat / s1 | 42 |  |
| civic/observatory | 0.818 | temperate / flat / s0 | 6 |  |
| law/gallows | 0.817 | temperate / flat / s1 | 48 |  |
| landmark/pagoda | 0.815 | karst / slope / s1 | 6 |  |

## empty_room_ratio (detail)

share of rooms (6+ cells) with no furniture. Bad when > 0.0: 1097 of 6862 cases, 42 archetypes.

| archetype | value | case | bad cases | note |
|---|---|---|---|---|
| extract/mine | 1.000 | temperate / flat / s0 | 56 |  |
| civic/bathhouse | 1.000 | karst / flat / s0 | 30 |  |
| civic/school | 1.000 | tropical / slope / s0 | 4 |  |
| house/stilt_house | 1.000 | tropical / slope / s1 | 3 |  |
| defense/watchtower | 0.750 | desert / flat / s0 | 84 |  |
| law/courthouse | 0.667 | tropical / flat / s1 | 35 |  |
| landmark/temple | 0.667 | mediterranean / flat / s0 | 9 |  |
| civic/chapel | 0.667 | tropical / slope / s1 | 5 |  |
| civic/infirmary | 0.667 | bayou / hills / s0 | 5 |  |
| commerce/inn | 0.500 | karst / flat / s0 | 64 |  |
| deep/glow_garden | 0.500 | temperate / cliff / s1 | 14 |  |
| storage/warehouse | 0.500 | tropical / flat / s0 | 8 |  |
| commerce/bank | 0.500 | northern / slope / s0 | 7 |  |
| house/farmhouse | 0.500 | karst / flat / s0 | 5 |  |
| landmark/cathedral | 0.500 | temperate / slope / s0 | 5 |  |

## furniture_per_cell (detail)

furniture blocks per room floor cell. Bad when < 0.25: 1248 of 6862 cases, 48 archetypes.

| archetype | value | case | bad cases | note |
|---|---|---|---|---|
| civic/bathhouse | 0.000 | karst / flat / s0 | 84 |  |
| extract/shaft | 0.000 | temperate / flat / s0 | 84 |  |
| extract/mine | 0.000 | temperate / flat / s0 | 56 |  |
| landmark/clock_tower | 0.000 | temperate / flat / s1 | 48 |  |
| landmark/temple | 0.000 | karst / flat / s0 | 15 |  |
| work/logging_camp | 0.000 | tropical / flat / s0 | 8 |  |
| commerce/caravan_yard | 0.000 | tropical / flat / s0 | 4 |  |
| civic/school | 0.000 | tropical / slope / s0 | 3 |  |
| commerce/counting_house | 0.000 | karst / flat / s0 | 3 |  |
| house/stilt_house | 0.000 | tropical / slope / s1 | 1 |  |
| house/round_house | 0.000 | tropical / slope / s1 | 1 |  |
| commerce/tavern | 0.000 | tropical / hills / s1 | 1 |  |
| landmark/pagoda | 0.037 | wisteria / flat / s1 | 6 |  |
| work/salt_works | 0.045 | tropical / slope / s0 | 33 |  |
| landmark/bell_tower | 0.050 | temperate / flat / s0 | 3 |  |

## culture_twin (detail)

plan likeness to the nearest other culture (flat, seed 0). Bad when > 0.9: 1322 of 1726 cases, 126 archetypes.

| archetype | value | case | bad cases | note |
|---|---|---|---|---|
| extract/mine | 1.000 | temperate / flat / s0 | 14 |  |
| deep/lift | 1.000 | temperate / flat / s0 | 14 |  |
| infra/dock | 1.000 | temperate / flat / s0 | 14 |  |
| defense/wall | 1.000 | temperate / flat / s0 | 14 |  |
| civic/playhouse | 1.000 | wisteria / flat / s0 | 14 |  |
| deep/market | 1.000 | temperate / flat / s0 | 14 |  |
| law/watch_house | 1.000 | temperate / flat / s0 | 14 |  |
| law/pillory | 1.000 | temperate / flat / s0 | 14 |  |
| extract/shaft | 1.000 | temperate / flat / s0 | 14 |  |
| defense/gatehouse | 1.000 | temperate / flat / s0 | 14 |  |
| defense/palisade | 1.000 | temperate / flat / s0 | 14 |  |
| commerce/market_stall | 1.000 | temperate / flat / s0 | 14 |  |
| infra/terrace | 1.000 | temperate / flat / s0 | 14 |  |
| farm/field | 1.000 | temperate / flat / s0 | 14 |  |
| work/quarry | 1.000 | moorland / flat / s0 | 14 |  |

## culture_likes (detail)

other cultures whose plan is >= 0.8 alike (flat, seed 0; cultureSimilarity's threshold). Bad when > 5.0: 913 of 1726 cases, 91 archetypes.

| archetype | value | case | bad cases | note |
|---|---|---|---|---|
| extract/mine | 13 | temperate / flat / s0 | 14 |  |
| deep/lift | 13 | temperate / flat / s0 | 14 |  |
| infra/dock | 13 | temperate / flat / s0 | 14 |  |
| defense/wall | 13 | temperate / flat / s0 | 14 |  |
| civic/playhouse | 13 | temperate / flat / s0 | 14 |  |
| noble/stables | 13 | temperate / flat / s0 | 14 |  |
| deep/glow_garden | 13 | temperate / flat / s0 | 14 |  |
| deep/dwelling | 13 | temperate / flat / s0 | 14 |  |
| deep/market | 13 | temperate / flat / s0 | 14 |  |
| landscape/lamp_post | 13 | wisteria / flat / s0 | 14 |  |
| work/builders_yard | 13 | temperate / flat / s0 | 14 |  |
| landscape/bell | 13 | temperate / flat / s0 | 14 |  |
| work/paper_mill | 13 | temperate / flat / s0 | 14 |  |
| work/alchemist_tower | 13 | temperate / flat / s0 | 14 |  |
| work/masonry | 13 | temperate / flat / s0 | 14 |  |

## height_ratio (detail)

height over the longer side. Bad when > 2.5: 304 of 9312 cases, 10 archetypes.

| archetype | value | case | bad cases | note |
|---|---|---|---|---|
| extract/plantation | 6.000 | northern / cliff / s0 | 10 |  |
| landmark/bell_tower | 5.857 | temperate / flat / s1 | 48 |  |
| landmark/clock_tower | 5.714 | temperate / flat / s1 | 84 |  |
| infra/walkway | 4.333 | temperate / flat / s0 | 42 |  |
| infra/dock | 4.000 | temperate / flat / s0 | 30 |  |
| landmark/pagoda | 3.545 | wisteria / flat / s0 | 6 |  |
| civic/shrine | 3.333 | temperate / flat / s0 | 30 |  |
| house/tower_house | 3.000 | alpine / flat / s0 | 12 |  |
| work/windmill | 2.667 | moorland / flat / s0 | 36 |  |
| work/alchemist_tower | 2.625 | desert / flat / s0 | 6 |  |
