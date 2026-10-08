# Walkthrough results (raw, written by WalkthroughTour)

### temperate cottage — PASS

`hearth:house/cottage` temperate, 8x10 facing south, tier 2, origin 0, -50, 200, 2 beds. Street start 4,-50,211.

- rooms reached 3/3; entrances 1/1; doors bad 0/1
- POIs walked to (model): bed 2/2, storage 1/1, seat 2/2, table 2/2, hearth 1/1, door 1/1
- folk pathfinder street -> bed/job/store: 3/3
- player driven (real movement & collision): 7/7 paths completed, 0 stuck
- stairs: 4 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 62 cells, 0 under 8, 0 at 0
  - room y+0 5 cells round 3,-50,208: reached, light min 8, 0 cells under 8; door
  - room y+0 19 cells round 4,-50,203: reached, light min 11, 0 cells under 8; hearth, storage, table, seat, seat
  - room y+4 21 cells round 4,-46,204: reached, light min 11, 0 cells under 8; bed, bed, table
- shots: temperate_cottage_main.jpg, temperate_cottage_bed.jpg, temperate_cottage_stair.jpg

### temperate farmhouse — WARN

`hearth:house/farmhouse` temperate, 16x15 facing south, tier 2, origin 112, -50, 200, 2 beds. Street start 120,-50,216.

- rooms reached 6/7; entrances 3/3; doors bad 0/1
- POIs walked to (model): bed 2/2, job_site 1/1, storage 2/2, seat 5/5, table 2/2, hearth 1/1, door 2/2, gate 1/1, viewpoint 1/1
- folk pathfinder street -> bed/job/store: 5/5
- player driven (real movement & collision): 12/12 paths completed, 0 stuck
- stairs: 4 flight steps, 0 unclimbable; jumps needed 2; tight head room 0
- light under roof: 141 cells, 16 under 8, 1 at 0
  - room y+0 14 cells round 116,-50,214: reached, light min 10, 0 cells under 8; gate
  - room y+0 34 cells round 119,-50,204: reached, light min 2, 6 cells under 8; door, job_site
  - room y+0 21 cells round 122,-50,209: reached, light min 10, 0 cells under 8; hearth, storage, table, seat, seat, seat, seat
  - room y+0 7 cells round 122,-50,215: reached, light min 10, 0 cells under 8; door, seat, viewpoint
  - room y+1 4 cells round 117,-49,204: NOT reached, light min -, 0 cells under 8
  - room y+1 15 cells round 127,-49,207: reached, light min 0, 9 cells under 8
  - room y+4 22 cells round 123,-46,210: reached, light min 11, 0 cells under 8; bed, bed, storage, table
- defects:
  - **LOW** room of 4 cells at y+1 round 117,-49,204 cannot be reached (nothing in it)
  - **MED** room at y+1 round 127,-49,207 is dim: 9 of 15 cells under light 8 (min 0)
  - **LOW** 2 full-block step(s) on the way to rooms, beds or work with no stair (a player must jump): 114,-50,210; 127,-49,208
  - **LOW** 1 reached cell(s) under a roof at block light 0 (monsters can spawn): 126,-49,200
- shots: temperate_farmhouse_main.jpg, temperate_farmhouse_bed.jpg, temperate_farmhouse_stair.jpg

### temperate townhouse — FAIL

`hearth:house/townhouse` temperate, 5x12 facing south, tier 2, origin 224, -50, 200, 2 beds. Street start 226,-50,213.

- rooms reached 4/5; entrances 1/1; doors bad 0/1
- POIs walked to (model): bed 2/2, storage 2/2, seat 1/1, table 2/2, hearth 1/1, door 1/1
- folk pathfinder street -> bed/job/store: 3/4
- player driven (real movement & collision): 10/10 paths completed, 0 stuck
- stairs: 8 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 61 cells, 0 under 8, 0 at 0
  - room y+0 16 cells round 227,-50,206: reached, light min 12, 0 cells under 8; hearth, storage
  - room y+0 4 cells round 227,-50,212: reached, light min 11, 0 cells under 8; door
  - room y+4 13 cells round 226,-46,206: reached, light min 11, 0 cells under 8; seat, table, storage
  - room y+8 13 cells round 226,-42,206: reached, light min 11, 0 cells under 8; bed, bed, table
  - room y+12 23 cells round 227,-38,206: NOT reached, light min -, 0 cells under 8
- defects:
  - **MED** room of 23 cells at y+12 round 227,-38,206 cannot be reached (nothing in it)
  - **HIGH** folk pathfinder finds no way from the street to the bed at 226, -42, 209 (gets as far as 227,-42,208)
- shots: temperate_townhouse_main.jpg, temperate_townhouse_bed.jpg, temperate_townhouse_stair.jpg

### temperate tenement — FAIL

`hearth:house/tenement` temperate, 11x14 facing south, tier 2, origin 336, -50, 200, 12 beds. Street start 341,-50,215.

- rooms reached 11/11; entrances 1/1; doors bad 2/10
- POIs walked to (model): bed 12/12, job_site 4/4, storage 8/8, table 3/3, hearth 1/1, door 1/1, viewpoint 1/3
- folk pathfinder street -> bed/job/store: 24/24
- player driven (real movement & collision): 40/40 paths completed, 0 stuck
- stairs: 12 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 272 cells, 10 under 8, 0 at 0
  - room y+0 5 cells round 339,-50,202: reached, light min 5, 5 cells under 8
  - room y+0 12 cells round 338,-50,208: reached, light min 11, 0 cells under 8; bed, hearth, job_site, storage, job_site
  - room y+0 14 cells round 341,-50,208: reached, light min 11, 0 cells under 8
  - room y+0 6 cells round 344,-50,214: reached, light min 9, 0 cells under 8; door
  - room y+0 5 cells round 345,-50,202: reached, light min 4, 5 cells under 8
  - room y+0 17 cells round 345,-50,208: reached, light min 11, 0 cells under 8; bed, job_site, storage, job_site, table
  - room y+4 18 cells round 338,-46,207: reached, light min 11, 0 cells under 8; bed, bed, storage
  - room y+4 29 cells round 344,-46,208: reached, light min 11, 0 cells under 8; bed, bed, bed, storage, table, storage
  - room y+8 17 cells round 338,-42,207: reached, light min 11, 0 cells under 8; bed, bed
  - room y+8 31 cells round 344,-42,208: reached, light min 10, 0 cells under 8; bed, bed, bed, storage, storage, table, storage
  - room y+12 74 cells round 342,-38,208: reached, light min 11, 0 cells under 8
- defects:
  - **HIGH** door at 341, -46, 202 leads into a wall or a drop on its north side (-1:spruce_slab / 0:polished_andesite_stairs / 1:air / 2:dark_oak_stairs)
  - **MED** door at 341, -42, 202 is reached on one side but cannot be passed
  - **LOW** room at y+0 round 339,-50,202 is dim: 5 of 5 cells under light 8 (min 5)
  - **LOW** room at y+0 round 345,-50,202 is dim: 5 of 5 cells under light 8 (min 4)
- shots: temperate_tenement_main.jpg, temperate_tenement_bed.jpg, temperate_tenement_stair.jpg

### temperate merchant_house — FAIL

`hearth:house/merchant_house` temperate, 8x12 facing south, tier 2, origin 448, -50, 200, 5 beds. Street start 452,-50,213.

- rooms reached 5/5; entrances 1/1; doors bad 0/1
- POIs walked to (model): bed 5/5, job_site 1/1, storage 4/4, counter 1/1, seat 3/3, table 3/3, hearth 1/1, door 1/1, strongbox 1/1
- folk pathfinder street -> bed/job/store: 4/11
- player driven (real movement & collision): 22/22 paths completed, 0 stuck
- stairs: 13 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 204 cells, 2 under 8, 0 at 0
  - room y+0 19 cells round 452,-50,212: reached, light min 6, 2 cells under 8; door
  - room y+0 36 cells round 452,-50,206: reached, light min 11, 0 cells under 8; job_site, counter, strongbox
  - room y+5 37 cells round 452,-45,207: reached, light min 11, 0 cells under 8; hearth, storage, table, seat, seat, seat
  - room y+9 37 cells round 452,-41,206: reached, light min 11, 0 cells under 8; bed, bed, bed, table
  - room y+13 35 cells round 452,-37,207: reached, light min 11, 0 cells under 8; bed, bed, storage, table, storage, storage
- defects:
  - **HIGH** folk pathfinder finds no way from the street to the bed at 449, -37, 205 (gets as far as 450,-41,204)
  - **HIGH** folk pathfinder finds no way from the street to the bed at 454, -41, 210 (gets as far as 454,-44,207)
  - **HIGH** folk pathfinder finds no way from the street to the bed at 453, -37, 210 (gets as far as 453,-40,207)
  - **HIGH** folk pathfinder finds no way from the street to the bed at 451, -41, 210 (gets as far as 451,-44,207)
  - **MED** folk pathfinder finds no way from the street to the storage at 452, -37, 209 (gets as far as 452,-41,206)
  - **MED** folk pathfinder finds no way from the street to the storage at 450, -37, 203 (gets as far as 450,-41,203)
  - **MED** folk pathfinder finds no way from the street to the storage at 449, -37, 209 (gets as far as 449,-44,207)
- shots: temperate_merchant_house_main.jpg, temperate_merchant_house_bed.jpg, temperate_merchant_house_stair.jpg

### temperate inn — WARN

`hearth:commerce/inn` temperate, 13x12 facing south, tier 2, origin 560, -50, 200, 4 beds. Street start 566,-50,213.

- rooms reached 6/7; entrances 1/1; doors bad 0/4
- POIs walked to (model): bed 4/4, job_site 1/1, storage 3/3, market_stall 1/1, counter 1/1, seat 4/4, table 4/4, hearth 1/1, door 1/1
- folk pathfinder street -> bed/job/store: 9/9
- player driven (real movement & collision): 16/16 paths completed, 0 stuck
- stairs: 4 flight steps, 0 unclimbable; jumps needed 1; tight head room 0
- light under roof: 100 cells, 0 under 8, 0 at 0
  - room y+0 19 cells round 565,-50,206: reached, light min 12, 0 cells under 8; job_site, counter, market_stall, hearth, table, seat, table, seat, seat, table, seat
  - room y+0 4 cells round 565,-50,211: reached, light min 12, 0 cells under 8; door
  - room y+0 25 cells round 571,-50,207: reached, light min 10, 0 cells under 8
  - room y+4 10 cells round 566,-46,205: reached, light min 11, 0 cells under 8; storage
  - room y+4 7 cells round 565,-46,208: reached, light min 11, 0 cells under 8; bed, bed, storage
  - room y+4 12 cells round 570,-46,208: reached, light min 10, 0 cells under 8; bed, bed, storage, table
  - room y+7 16 cells round 566,-43,206: NOT reached, light min -, 0 cells under 8
- defects:
  - **MED** room of 16 cells at y+7 round 566,-43,206 cannot be reached (nothing in it)
  - **LOW** 1 full-block step(s) on the way to rooms, beds or work with no stair (a player must jump): 561,-49,208
- shots: temperate_inn_main.jpg, temperate_inn_bed.jpg

### temperate tavern — PASS

`hearth:commerce/tavern` temperate, 10x13 facing south, tier 2, origin 672, -50, 200, 0 beds. Street start 677,-50,214.

- rooms reached 4/6; entrances 1/1; doors bad 0/1
- POIs walked to (model): job_site 2/2, storage 1/1, market_stall 1/1, counter 1/1, seat 7/7, table 1/1, hearth 1/1, door 1/1, gaming_table 1/1, stage 1/1
- folk pathfinder street -> bed/job/store: 4/4
- player driven (real movement & collision): 9/9 paths completed, 0 stuck
- stairs: 4 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 71 cells, 0 under 8, 0 at 0
  - room y-4 9 cells round 678,-54,204: reached, light min 12, 0 cells under 8; storage
  - room y+0 26 cells round 677,-50,207: reached, light min 11, 0 cells under 8; job_site, counter, market_stall, hearth, stage, gaming_table, seat, seat, seat, seat, seat, seat, table
  - room y+0 6 cells round 675,-50,212: reached, light min 11, 0 cells under 8; door
  - room y+1 6 cells round 680,-49,209: reached, light min 11, 0 cells under 8; stage, job_site, seat, seat, table
  - room y+4 5 cells round 675,-46,207: NOT reached, light min -, 0 cells under 8
  - room y+4 5 cells round 678,-46,207: NOT reached, light min -, 0 cells under 8
- defects:
  - **LOW** room of 5 cells at y+4 round 675,-46,207 cannot be reached (nothing in it)
  - **LOW** room of 5 cells at y+4 round 678,-46,207 cannot be reached (nothing in it)
- shots: temperate_tavern_main.jpg

### temperate smithy — PASS

`hearth:work/smithy` temperate, 10x10 facing south, tier 2, origin 784, -50, 200, 0 beds. Street start 789,-50,211.

- rooms reached 1/3; entrances 1/1; doors bad 0/0
- POIs walked to (model): job_site 4/4, storage 2/2, door 1/1
- folk pathfinder street -> bed/job/store: 6/6
- player driven (real movement & collision): 6/6 paths completed, 0 stuck
- stairs: 0 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 55 cells, 2 under 8, 0 at 0
  - room y+0 40 cells round 789,-50,207: reached, light min 8, 0 cells under 8; door, job_site, job_site, job_site, storage, storage, job_site
  - room y+3 7 cells round 788,-47,202: NOT reached, light min -, 0 cells under 8
  - room y+6 5 cells round 789,-44,205: NOT reached, light min -, 0 cells under 8
- defects:
  - **LOW** room of 7 cells at y+3 round 788,-47,202 cannot be reached (nothing in it)
  - **LOW** room of 5 cells at y+6 round 789,-44,205 cannot be reached (nothing in it)
- shots: temperate_smithy_main.jpg

### temperate bakery — WARN

`hearth:work/bakery` temperate, 9x10 facing south, tier 2, origin 896, -50, 200, 0 beds. Street start 900,-50,211.

- rooms reached 2/3; entrances 3/3; doors bad 0/2
- POIs walked to (model): job_site 3/3, storage 1/1, counter 1/1, seat 1/1, table 1/1, door 2/2
- folk pathfinder street -> bed/job/store: 5/5
- player driven (real movement & collision): 7/7 paths completed, 0 stuck
- stairs: 0 flight steps, 0 unclimbable; jumps needed 1; tight head room 0
- light under roof: 36 cells, 4 under 8, 0 at 0
  - room y+0 8 cells round 899,-50,205: reached, light min 11, 0 cells under 8; job_site, job_site, counter, storage, table
  - room y+1 6 cells round 898,-49,206: reached, light min 11, 0 cells under 8; job_site, job_site, counter, storage, table
  - room y+5 14 cells round 899,-45,205: NOT reached, light min -, 0 cells under 8
- defects:
  - **MED** room of 14 cells at y+5 round 899,-45,205 cannot be reached (nothing in it)
  - **LOW** 1 full-block step(s) on the way to rooms, beds or work with no stair (a player must jump): 898,-49,205
- shots: temperate_bakery_main.jpg

### temperate windmill — WARN

`hearth:work/windmill` temperate, 16x12 facing south, tier 2, origin 1008, -50, 200, 0 beds. Street start 1016,-50,213.

- rooms reached 4/10; entrances 2/2; doors bad 0/1
- POIs walked to (model): job_site 2/2, storage 1/1, door 1/1
- folk pathfinder street -> bed/job/store: 3/3
- player driven (real movement & collision): 7/7 paths completed, 0 stuck
- stairs: 0 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 54 cells, 16 under 8, 0 at 0
  - room y+0 23 cells round 1016,-50,209: reached, light min 7, 4 cells under 8; door, job_site, job_site, storage
  - room y+0 4 cells round 1012,-50,203: reached, light min 3, 4 cells under 8
  - room y+0 15 cells round 1016,-50,205: reached, light min 11, 0 cells under 8
  - room y+0 4 cells round 1019,-50,203: reached, light min 3, 4 cells under 8
  - room y+1 14 cells round 1016,-49,205: NOT reached, light min -, 0 cells under 8
  - room y+5 9 cells round 1016,-45,206: NOT reached, light min -, 0 cells under 8
  - room y+9 9 cells round 1016,-41,206: NOT reached, light min -, 0 cells under 8
  - room y+10 6 cells round 1012,-40,206: NOT reached, light min -, 0 cells under 8
  - room y+10 6 cells round 1019,-40,206: NOT reached, light min -, 0 cells under 8
  - room y+18 6 cells round 1015,-32,206: NOT reached, light min -, 0 cells under 8
- defects:
  - **LOW** room at y+0 round 1012,-50,203 is dim: 4 of 4 cells under light 8 (min 3)
  - **LOW** room at y+0 round 1019,-50,203 is dim: 4 of 4 cells under light 8 (min 3)
  - **MED** room of 14 cells at y+1 round 1016,-49,205 cannot be reached (nothing in it)
  - **MED** room of 9 cells at y+5 round 1016,-45,206 cannot be reached (nothing in it)
  - **MED** room of 9 cells at y+9 round 1016,-41,206 cannot be reached (nothing in it)
  - **LOW** room of 6 cells at y+10 round 1012,-40,206 cannot be reached (nothing in it)
  - **LOW** room of 6 cells at y+10 round 1019,-40,206 cannot be reached (nothing in it)
  - **LOW** room of 6 cells at y+18 round 1015,-32,206 cannot be reached (nothing in it)
- shots: temperate_windmill_main.jpg

### temperate chapel — WARN

`hearth:civic/chapel` temperate, 12x18 facing south, tier 2, origin 1120, -50, 200, 0 beds. Street start 1126,-50,219.

- rooms reached 6/8; entrances 1/1; doors bad 0/1
- POIs walked to (model): job_site 1/1, seat 2/2, altar 1/1, door 1/1
- folk pathfinder street -> bed/job/store: 1/1
- player driven (real movement & collision): 30/30 paths completed, 0 stuck
- stairs: 73 flight steps, 2 unclimbable; jumps needed 4; tight head room 0
- light under roof: 157 cells, 16 under 8, 0 at 0
  - room y+0 4 cells round 1122,-50,204: reached, light min 6, 4 cells under 8
  - room y+0 4 cells round 1130,-50,204: reached, light min 6, 4 cells under 8
  - room y+1 38 cells round 1125,-49,207: reached, light min 11, 0 cells under 8; seat, seat, altar, job_site
  - room y+1 5 cells round 1126,-49,201: reached, light min 7, 4 cells under 8
  - room y+1 11 cells round 1125,-49,213: reached, light min 11, 0 cells under 8
  - room y+7 5 cells round 1126,-43,210: NOT reached, light min -, 0 cells under 8
  - room y+8 5 cells round 1126,-42,217: NOT reached, light min -, 0 cells under 8
  - room y+12 36 cells round 1125,-38,213: reached, light min 9, 0 cells under 8
- defects:
  - **LOW** room at y+0 round 1122,-50,204 is dim: 4 of 4 cells under light 8 (min 6)
  - **LOW** room at y+0 round 1130,-50,204 is dim: 4 of 4 cells under light 8 (min 6)
  - **LOW** room at y+1 round 1126,-49,201 is dim: 4 of 5 cells under light 8 (min 7)
  - **LOW** room of 5 cells at y+7 round 1126,-43,210 cannot be reached (nothing in it)
  - **LOW** room of 5 cells at y+8 round 1126,-42,217 cannot be reached (nothing in it)
  - **MED** stair step at 1123, -41, 210 (going east) cannot be climbed: no head room over it (a ceiling within 1.8 of the tread)
  - **MED** stair step at 1127, -41, 210 (going west) cannot be climbed: no head room over it (a ceiling within 1.8 of the tread)
  - **MED** 4 full-block step(s) on the way to rooms, beds or work with no stair (a player must jump): 1124,-48,210; 1120,-49,203; 1130,-49,203; 1130,-49,200
- shots: temperate_chapel_main.jpg, temperate_chapel_stair.jpg

### temperate town_hall — WARN

`hearth:civic/town_hall` temperate, 19x13 facing south, tier 3, origin 1232, -50, 200, 0 beds. Street start 1241,-50,214.

- rooms reached 7/10; entrances 2/2; doors bad 0/1
- POIs walked to (model): storage 2/2, notice_board 1/1, meeting_point 1/1, seat 3/3, table 1/1, hearth 1/1, lectern 1/1, door 1/1
- folk pathfinder street -> bed/job/store: 1/2
- player driven (real movement & collision): 32/32 paths completed, 0 stuck
- stairs: 109 flight steps, 6 unclimbable; jumps needed 2; tight head room 0
- light under roof: 297 cells, 21 under 8, 0 at 0
  - room y+1 47 cells round 1241,-49,204: reached, light min 11, 0 cells under 8; lectern, storage
  - room y+1 27 cells round 1239,-49,209: reached, light min 10, 0 cells under 8; door, notice_board
  - room y+1 11 cells round 1242,-49,201: reached, light min 4, 8 cells under 8
  - room y+1 13 cells round 1247,-49,208: reached, light min 9, 0 cells under 8
  - room y+7 77 cells round 1240,-43,205: reached, light min 10, 0 cells under 8; table, seat, seat, seat, meeting_point, storage, hearth
  - room y+13 6 cells round 1247,-37,211: reached, light min 3, 6 cells under 8
  - room y+13 4 cells round 1247,-37,205: NOT reached, light min -, 0 cells under 8
  - room y+13 5 cells round 1251,-37,208: NOT reached, light min -, 0 cells under 8
  - room y+19 36 cells round 1247,-31,208: reached, light min 7, 2 cells under 8
  - room y+25 9 cells round 1248,-25,208: NOT reached, light min -, 0 cells under 8
- defects:
  - **LOW** room at y+1 round 1242,-49,201 is dim: 8 of 11 cells under light 8 (min 4)
  - **LOW** room at y+13 round 1247,-37,211 is dim: 6 of 6 cells under light 8 (min 3)
  - **LOW** room of 4 cells at y+13 round 1247,-37,205 cannot be reached (nothing in it)
  - **LOW** room of 5 cells at y+13 round 1251,-37,208 cannot be reached (nothing in it)
  - **MED** room of 9 cells at y+25 round 1248,-25,208 cannot be reached (nothing in it)
  - **MED** folk pathfinder finds no way from the street to the storage at 1235, -43, 202 (gets as far as 1234,-48,204)
  - **MED** stair step at 1244, -34, 206 (going north) cannot be climbed: no head room over it (a ceiling within 1.8 of the tread)
  - **MED** stair step at 1245, -34, 204 (going south) cannot be climbed: no head room over it (a ceiling within 1.8 of the tread)
  - **MED** stair step at 1246, -34, 204 (going south) cannot be climbed: no head room over it (a ceiling within 1.8 of the tread)
  - **MED** stair step at 1247, -34, 204 (going south) cannot be climbed: no head room over it (a ceiling within 1.8 of the tread)
  - **MED** stair step at 1248, -34, 204 (going south) cannot be climbed: no head room over it (a ceiling within 1.8 of the tread)
  - **MED** stair step at 1249, -34, 204 (going south) cannot be climbed: no head room over it (a ceiling within 1.8 of the tread)
  - **LOW** 2 full-block step(s) on the way to rooms, beds or work with no stair (a player must jump): 1248,-48,202; 1241,-49,200
- shots: temperate_town_hall_main.jpg, temperate_town_hall_stair.jpg

### temperate library — PASS

`hearth:civic/library` temperate, 11x15 facing south, tier 2, origin 1344, -50, 200, 0 beds. Street start 1349,-50,216.

- rooms reached 3/3; entrances 1/1; doors bad 0/1
- POIs walked to (model): job_site 2/2, storage 1/1, seat 8/8, table 4/4, lectern 2/2, door 1/1, viewpoint 1/1
- folk pathfinder street -> bed/job/store: 3/3
- player driven (real movement & collision): 7/7 paths completed, 0 stuck
- stairs: 4 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 96 cells, 4 under 8, 0 at 0
  - room y+1 39 cells round 1350,-49,206: reached, light min 10, 0 cells under 8; job_site, lectern, job_site, lectern, table, seat, seat, table, seat, seat, table, seat, seat, table, seat, seat
  - room y+1 6 cells round 1349,-49,213: reached, light min 10, 0 cells under 8; door
  - room y+5 17 cells round 1350,-45,203: reached, light min 9, 0 cells under 8; viewpoint, storage
- shots: temperate_library_main.jpg, temperate_library_stair.jpg

### temperate market_hall — FAIL

`hearth:commerce/market_hall` temperate, 13x14 facing south, tier 3, origin 1456, -50, 200, 0 beds. Street start 1462,-50,215.

- rooms reached 1/4; entrances 2/2; doors bad 2/2
- POIs walked to (model): job_site 4/4, storage 0/2, market_stall 4/4, counter 4/4, meeting_point 1/2, seat 0/4, table 0/1, hearth 0/1, door 2/2, viewpoint 0/1
- folk pathfinder street -> bed/job/store: 8/10
- player driven (real movement & collision): 9/9 paths completed, 0 stuck
- stairs: 0 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 101 cells, 12 under 8, 0 at 0
  - room y+0 80 cells round 1462,-50,209: reached, light min 7, 6 cells under 8; door, job_site, counter, market_stall, job_site, counter, market_stall, job_site, counter, market_stall, job_site, counter, market_stall, meeting_point, door
  - room y+0 14 cells round 1462,-50,203: NOT reached, light min -, 0 cells under 8; storage
  - room y+6 72 cells round 1462,-44,207: NOT reached, light min -, 0 cells under 8; table, seat, seat, seat, seat, meeting_point, hearth, storage
  - room y+7 5 cells round 1462,-43,205: NOT reached, light min -, 0 cells under 8; seat, seat
- defects:
  - **HIGH** door at 1462, -38, 212 leads into a wall or a drop on its south side (-1:air / 0:lantern / 1:iron_chain / 2:dark_oak_log)
  - **MED** door at 1465, -50, 204 is reached on one side but cannot be passed
  - **HIGH** room of 14 cells at y+0 round 1462,-50,203 cannot be reached (has storage)
  - **HIGH** room of 72 cells at y+6 round 1462,-44,207 cannot be reached (has table, seat, seat, seat, seat, meeting_point, hearth, storage)
  - **LOW** room of 5 cells at y+7 round 1462,-43,205 cannot be reached (has seat, seat)
  - **HIGH** storage at 1465, -50, 202 cannot be walked to by a player
  - **HIGH** folk pathfinder finds no way from the street to the storage at 1465, -50, 202 (gets as far as 1465,-50,200)
  - **HIGH** storage at 1465, -44, 211 cannot be walked to by a player
  - **HIGH** folk pathfinder finds no way from the street to the storage at 1465, -44, 211 (gets as far as 1465,-48,211)
- shots: temperate_market_hall_main.jpg

### temperate manor — FAIL

`hearth:house/manor` temperate, 18x21 facing south, tier 3, origin 1568, -50, 200, 6 beds. Street start 1577,-50,222.

- rooms reached 17/18; entrances 2/2; doors bad 0/10
- POIs walked to (model): bed 6/6, storage 8/8, seat 6/7, table 6/6, hearth 2/2, door 1/1, gate 1/1, viewpoint 1/2
- folk pathfinder street -> bed/job/store: 11/14
- player driven (real movement & collision): 33/33 paths completed, 0 stuck
- stairs: 4 flight steps, 0 unclimbable; jumps needed 2; tight head room 0
- light under roof: 280 cells, 7 under 8, 0 at 0
  - room y+0 5 cells round 1569,-50,216: reached, light min 7, 1 cells under 8
  - room y+0 5 cells round 1574,-50,216: reached, light min 11, 0 cells under 8
  - room y+0 5 cells round 1585,-50,204: reached, light min 7, 1 cells under 8
  - room y+0 5 cells round 1585,-50,216: reached, light min 7, 1 cells under 8
  - room y+1 11 cells round 1572,-49,208: reached, light min 12, 0 cells under 8; hearth, seat, table, storage
  - room y+1 4 cells round 1572,-49,213: reached, light min 13, 0 cells under 8; storage
  - room y+1 23 cells round 1576,-49,208: reached, light min 11, 0 cells under 8; table
  - room y+1 4 cells round 1577,-49,213: reached, light min 13, 0 cells under 8; door
  - room y+1 8 cells round 1582,-49,206: reached, light min 11, 0 cells under 8; hearth, seat, seat
  - room y+1 7 cells round 1582,-49,210: reached, light min 11, 0 cells under 8; hearth, table, seat, seat
  - room y+1 4 cells round 1583,-49,213: reached, light min 13, 0 cells under 8; seat, table
  - room y+4 4 cells round 1578,-46,204: NOT reached, light min -, 0 cells under 8
  - room y+5 11 cells round 1572,-45,208: reached, light min 11, 0 cells under 8; bed, bed
  - room y+5 8 cells round 1571,-45,214: reached, light min 10, 0 cells under 8; bed, storage
  - room y+5 13 cells round 1576,-45,208: reached, light min 11, 0 cells under 8; seat, table, storage
  - room y+5 20 cells round 1582,-45,208: reached, light min 10, 0 cells under 8; bed, bed, storage, table
  - room y+5 7 cells round 1583,-45,213: reached, light min 11, 0 cells under 8; bed
  - room y+9 74 cells round 1577,-41,208: reached, light min 11, 0 cells under 8; storage, storage, storage
- defects:
  - **LOW** room of 4 cells at y+4 round 1578,-46,204 cannot be reached (nothing in it)
  - **HIGH** folk pathfinder finds no way from the street to the bed at 1572, -45, 213 (gets as far as 1572,-48,214)
  - **MED** folk pathfinder finds no way from the street to the storage at 1581, -41, 206 (gets as far as 1582,-44,205)
  - **MED** folk pathfinder finds no way from the street to the storage at 1571, -45, 214 (gets as far as 1571,-48,214)
  - **LOW** 2 full-block step(s) on the way to rooms, beds or work with no stair (a player must jump): 1576,-49,214; 1580,-48,208
- shots: temperate_manor_main.jpg, temperate_manor_bed.jpg, temperate_manor_stair.jpg

### temperate keep — FAIL

`hearth:noble/keep` temperate, 24x22 facing south, tier 3, origin 1680, -50, 200, 3 beds. Street start 1692,-50,223.

- rooms reached 11/15; entrances 1/1; doors bad 0/2
- POIs walked to (model): bed 3/3, job_site 4/4, storage 5/5, meeting_point 2/2, seat 2/2, table 4/4, hearth 1/1, altar 1/1, lectern 1/1, door 1/1, guard_post 5/6, gate 1/1, viewpoint 1/1, hitching_post 1/1, throne 1/1, strongbox 1/1
- folk pathfinder street -> bed/job/store: 7/12
- player driven (real movement & collision): 25/25 paths completed, 0 stuck
- stairs: 11 flight steps, 1 unclimbable; jumps needed 6; tight head room 0
- light under roof: 572 cells, 84 under 8, 0 at 0
  - room y+0 38 cells round 1687,-50,205: reached, light min 11, 0 cells under 8; hearth, storage, table, seat
  - room y+0 18 cells round 1686,-50,211: reached, light min 7, 1 cells under 8; door, storage
  - room y+0 41 cells round 1692,-50,219: reached, light min 4, 5 cells under 8; gate
  - room y+0 27 cells round 1695,-50,212: reached, light min 7, 1 cells under 8; guard_post
  - room y+0 213 cells round 1697,-50,206: reached, light min 10, 0 cells under 8; job_site, storage, job_site, throne, seat, job_site, meeting_point, altar, table, lectern, job_site, storage, strongbox, table, guard_post, viewpoint
  - room y+5 15 cells round 1683,-45,219: NOT reached, light min -, 0 cells under 8; guard_post
  - room y+5 40 cells round 1687,-45,205: reached, light min 10, 0 cells under 8; bed, bed, bed, storage, table
  - room y+5 7 cells round 1692,-45,223: reached, light min 7, 2 cells under 8
  - room y+5 13 cells round 1692,-45,220: reached, light min 12, 0 cells under 8; guard_post
  - room y+5 10 cells round 1697,-45,201: NOT reached, light min -, 0 cells under 8
  - room y+5 14 cells round 1701,-45,219: reached, light min 10, 0 cells under 8; guard_post
  - room y+5 5 cells round 1701,-45,212: reached, light min 7, 1 cells under 8
  - room y+5 11 cells round 1703,-45,206: reached, light min 2, 10 cells under 8
  - room y+9 5 cells round 1692,-41,203: NOT reached, light min -, 0 cells under 8
  - room y+10 7 cells round 1691,-40,205: NOT reached, light min -, 0 cells under 8
- defects:
  - **HIGH** room of 15 cells at y+5 round 1683,-45,219 cannot be reached (has guard_post)
  - **MED** room of 10 cells at y+5 round 1697,-45,201 cannot be reached (nothing in it)
  - **LOW** room at y+5 round 1703,-45,206 is dim: 10 of 11 cells under light 8 (min 2)
  - **LOW** room of 5 cells at y+9 round 1692,-41,203 cannot be reached (nothing in it)
  - **LOW** room of 7 cells at y+10 round 1691,-40,205 cannot be reached (nothing in it)
  - **HIGH** folk pathfinder finds no way from the street to the bed at 1691, -45, 206 (gets as far as 1691,-49,205)
  - **HIGH** folk pathfinder finds no way from the street to the bed at 1691, -45, 203 (gets as far as 1691,-49,204)
  - **HIGH** folk pathfinder finds no way from the street to the job_site (steward) at 1697, -35, 208 (gets as far as 1699,-45,206)
  - **MED** folk pathfinder finds no way from the street to the storage at 1696, -35, 208 (gets as far as 1696,-45,205)
  - **MED** folk pathfinder finds no way from the street to the storage at 1690, -45, 204 (gets as far as 1691,-49,204)
  - **MED** stair step at 1698, -39, 220 (going east) cannot be climbed: blocked stepping onto it
  - **MED** 6 full-block step(s) on the way to rooms, beds or work with no stair (a player must jump): 1682,-49,202; 1685,-49,218; 1691,-44,221; 1701,-44,206; 1702,-44,215; 1702,-44,211
- shots: temperate_keep_main.jpg, temperate_keep_bed.jpg, temperate_keep_stair.jpg

### temperate mine — WARN

`hearth:extract/mine` temperate, 7x25 facing south, tier 2, origin 1792, -50, 200, 0 beds. Street start 1795,-50,226.

- rooms reached 1/1; entrances 1/1; doors bad 0/0
- POIs walked to (model): job_site 1/1, storage 2/2, work_face 6/6
- folk pathfinder street -> bed/job/store: 3/7
- player driven (real movement & collision): 8/8 paths completed, 0 stuck
- stairs: 0 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 45 cells, 0 under 8, 0 at 0
  - room y-2 45 cells round 1796,-52,209: reached, light min 11, 0 cells under 8; work_face, work_face, work_face, work_face, work_face, work_face
- defects:
  - **MED** folk pathfinder finds no way from the street to the work_face (miner) at 1797, -52, 215 (gets as far as 1796,-51,218)
  - **MED** folk pathfinder finds no way from the street to the work_face (miner) at 1795, -52, 212 (gets as far as 1796,-51,218)
  - **MED** folk pathfinder finds no way from the street to the work_face (miner) at 1793, -52, 209 (gets as far as 1794,-51,218)
  - **MED** folk pathfinder finds no way from the street to the work_face (miner) at 1795, -52, 206 (gets as far as 1795,-44,214)
- shots: temperate_mine_main.jpg

### mediterranean cottage — PASS

`hearth:house/cottage` mediterranean, 9x8 facing south, tier 2, origin 0, -50, 360, 2 beds. Street start 4,-50,369.

- rooms reached 2/2; entrances 1/1; doors bad 0/1
- POIs walked to (model): bed 2/2, storage 1/1, table 1/1, door 1/1
- folk pathfinder street -> bed/job/store: 3/3
- player driven (real movement & collision): 5/5 paths completed, 0 stuck
- stairs: 0 flight steps, 0 unclimbable; jumps needed 1; tight head room 0
- light under roof: 38 cells, 0 under 8, 0 at 0
  - room y+0 17 cells round 4,-50,363: reached, light min 10, 0 cells under 8; bed, bed, storage, table
  - room y+1 4 cells round 9,-49,364: reached, light min 9, 0 cells under 8
- defects:
  - **LOW** 1 full-block step(s) on the way to rooms, beds or work with no stair (a player must jump): 8,-49,363
- shots: mediterranean_cottage_main.jpg, mediterranean_cottage_bed.jpg

### mediterranean farmhouse — FAIL

`hearth:house/farmhouse` mediterranean, 16x13 facing south, tier 2, origin 112, -50, 360, 2 beds. Street start 120,-50,374.

- rooms reached 4/7; entrances 3/3; doors bad 0/1
- POIs walked to (model): bed 2/2, job_site 3/3, storage 1/1, seat 4/4, table 2/2, hearth 1/1, door 2/2, gate 1/1, work_face 2/2
- folk pathfinder street -> bed/job/store: 7/8
- player driven (real movement & collision): 12/12 paths completed, 0 stuck
- stairs: 4 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 116 cells, 0 under 8, 0 at 0
  - room y+0 20 cells round 116,-50,366: reached, light min 9, 0 cells under 8; door, work_face, work_face, job_site, job_site
  - room y+0 23 cells round 123,-50,365: reached, light min 11, 0 cells under 8; hearth, storage, table, seat, seat, seat, seat
  - room y+2 11 cells round 116,-48,361: NOT reached, light min -, 0 cells under 8
  - room y+2 8 cells round 113,-48,368: NOT reached, light min -, 0 cells under 8
  - room y+2 5 cells round 127,-48,361: NOT reached, light min -, 0 cells under 8
  - room y+4 27 cells round 123,-46,365: reached, light min 10, 0 cells under 8; bed, bed, table
  - room y+5 14 cells round 116,-45,365: reached, light min 11, 0 cells under 8; job_site
- defects:
  - **MED** room of 11 cells at y+2 round 116,-48,361 cannot be reached (nothing in it)
  - **MED** room of 8 cells at y+2 round 113,-48,368 cannot be reached (nothing in it)
  - **LOW** room of 5 cells at y+2 round 127,-48,361 cannot be reached (nothing in it)
  - **HIGH** folk pathfinder finds no way from the street to the job_site (herder) at 116, -45, 365 (gets as far as 117,-49,365)
- shots: mediterranean_farmhouse_main.jpg, mediterranean_farmhouse_bed.jpg, mediterranean_farmhouse_stair.jpg

### mediterranean townhouse — PASS

`hearth:house/townhouse` mediterranean, 5x9 facing south, tier 2, origin 224, -50, 360, 4 beds. Street start 226,-50,370.

- rooms reached 4/4; entrances 1/1; doors bad 0/1
- POIs walked to (model): bed 4/4, job_site 2/2, storage 2/2, table 2/2, door 1/1
- folk pathfinder street -> bed/job/store: 8/8
- player driven (real movement & collision): 14/14 paths completed, 0 stuck
- stairs: 9 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 59 cells, 0 under 8, 0 at 0
  - room y+0 5 cells round 227,-50,370: reached, light min 9, 0 cells under 8; door
  - room y+0 13 cells round 227,-50,364: reached, light min 11, 0 cells under 8; job_site, storage, job_site
  - room y+5 13 cells round 226,-45,365: reached, light min 10, 0 cells under 8; bed, bed, storage, table
  - room y+9 12 cells round 226,-41,364: reached, light min 10, 0 cells under 8; bed, bed, table
- shots: mediterranean_townhouse_main.jpg, mediterranean_townhouse_bed.jpg, mediterranean_townhouse_stair.jpg

### mediterranean tenement — FAIL

`hearth:house/tenement` mediterranean, 8x12 facing south, tier 2, origin 336, -50, 360, 12 beds. Street start 340,-50,373.

- rooms reached 7/9; entrances 1/1; doors bad 0/11
- POIs walked to (model): bed 12/12, storage 6/6, table 5/5, hearth 1/1, door 1/1, viewpoint 0/3
- folk pathfinder street -> bed/job/store: 13/18
- player driven (real movement & collision): 33/33 paths completed, 0 stuck
- stairs: 16 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 187 cells, 1 under 8, 0 at 0
  - room y+0 4 cells round 338,-50,365: reached, light min 12, 0 cells under 8; hearth
  - room y+0 28 cells round 341,-50,367: reached, light min 11, 0 cells under 8; bed, bed, bed, hearth, table, storage
  - room y+0 4 cells round 341,-50,372: reached, light min 11, 0 cells under 8; door
  - room y+4 25 cells round 341,-46,367: reached, light min 11, 0 cells under 8; bed, bed, table, storage
  - room y+5 4 cells round 340,-45,361: NOT reached, light min -, 0 cells under 8; viewpoint
  - room y+8 20 cells round 340,-42,367: reached, light min 11, 0 cells under 8; bed, bed, table, storage, table
  - room y+9 4 cells round 340,-41,361: NOT reached, light min -, 0 cells under 8; viewpoint
  - room y+12 24 cells round 341,-38,367: reached, light min 11, 0 cells under 8; bed, bed, bed, storage
  - room y+16 20 cells round 340,-34,367: reached, light min 11, 0 cells under 8; bed, bed, storage, table, storage
- defects:
  - **LOW** room of 4 cells at y+5 round 340,-45,361 cannot be reached (has viewpoint)
  - **LOW** room of 4 cells at y+9 round 340,-41,361 cannot be reached (has viewpoint)
  - **HIGH** folk pathfinder finds no way from the street to the bed at 338, -34, 367 (gets as far as 338,-34,365)
  - **HIGH** folk pathfinder finds no way from the street to the bed at 341, -38, 369 (gets as far as 340,-38,368)
  - **HIGH** folk pathfinder finds no way from the street to the bed at 342, -34, 367 (gets as far as 339,-34,365)
  - **MED** folk pathfinder finds no way from the street to the storage at 341, -34, 368 (gets as far as 341,-37,366)
  - **MED** folk pathfinder finds no way from the street to the storage at 341, -34, 363 (gets as far as 340,-34,365)
- shots: mediterranean_tenement_main.jpg, mediterranean_tenement_bed.jpg, mediterranean_tenement_stair.jpg

### mediterranean merchant_house — FAIL

`hearth:house/merchant_house` mediterranean, 7x13 facing south, tier 2, origin 448, -50, 360, 5 beds. Street start 451,-50,374.

- rooms reached 6/6; entrances 1/1; doors bad 0/1
- POIs walked to (model): bed 5/5, job_site 1/1, storage 7/7, market_stall 1/1, counter 1/1, seat 1/1, table 4/4, hearth 1/1, door 1/1, strongbox 1/1
- folk pathfinder street -> bed/job/store: 11/14
- player driven (real movement & collision): 26/26 paths completed, 0 stuck
- stairs: 13 flight steps, 0 unclimbable; jumps needed 2; tight head room 0
- light under roof: 167 cells, 1 under 8, 0 at 0
  - room y+0 27 cells round 452,-50,367: reached, light min 11, 0 cells under 8; counter, market_stall, job_site, strongbox, seat, table, storage, storage, storage
  - room y+1 5 cells round 450,-49,369: reached, light min 12, 0 cells under 8; counter, market_stall, job_site, storage
  - room y+1 4 cells round 454,-49,367: reached, light min 11, 0 cells under 8; storage, storage
  - room y+5 30 cells round 452,-45,367: reached, light min 11, 0 cells under 8; hearth, storage, table
  - room y+9 29 cells round 452,-41,367: reached, light min 11, 0 cells under 8; bed, bed, bed, bed, storage, table
  - room y+13 25 cells round 452,-37,367: reached, light min 11, 0 cells under 8; bed, table, storage, storage
- defects:
  - **HIGH** folk pathfinder finds no way from the street to the bed at 453, -41, 370 (gets as far as 452,-40,367)
  - **HIGH** folk pathfinder finds no way from the street to the bed at 453, -37, 369 (gets as far as 453,-40,367)
  - **MED** folk pathfinder finds no way from the street to the storage at 450, -37, 370 (gets as far as 449,-40,367)
  - **LOW** 2 full-block step(s) on the way to rooms, beds or work with no stair (a player must jump): 450,-49,368; 453,-49,368
- shots: mediterranean_merchant_house_main.jpg, mediterranean_merchant_house_bed.jpg, mediterranean_merchant_house_stair.jpg

### mediterranean inn — WARN

`hearth:commerce/inn` mediterranean, 13x15 facing south, tier 2, origin 560, -50, 360, 7 beds. Street start 566,-50,376.

- rooms reached 8/12; entrances 1/1; doors bad 0/6
- POIs walked to (model): bed 7/7, job_site 1/1, storage 3/3, market_stall 1/1, counter 1/1, seat 6/6, table 6/6, hearth 1/1, door 1/1, viewpoint 0/1
- folk pathfinder street -> bed/job/store: 12/12
- player driven (real movement & collision): 22/22 paths completed, 0 stuck
- stairs: 9 flight steps, 0 unclimbable; jumps needed 1; tight head room 0
- light under roof: 166 cells, 5 under 8, 0 at 0
  - room y+0 17 cells round 565,-50,363: NOT reached, light min -, 0 cells under 8
  - room y+0 21 cells round 565,-50,369: reached, light min 11, 0 cells under 8; job_site, counter, market_stall, seat, hearth, table, seat, table, seat, seat, seat, table, seat
  - room y+0 35 cells round 570,-50,368: reached, light min 5, 5 cells under 8
  - room y+5 10 cells round 566,-45,367: reached, light min 11, 0 cells under 8; storage
  - room y+5 4 cells round 562,-45,371: NOT reached, light min -, 0 cells under 8
  - room y+5 8 cells round 565,-45,370: reached, light min 12, 0 cells under 8; bed, table
  - room y+5 13 cells round 570,-45,370: reached, light min 11, 0 cells under 8; bed, bed, storage, storage
  - room y+6 13 cells round 567,-44,365: NOT reached, light min -, 0 cells under 8; viewpoint
  - room y+9 17 cells round 565,-41,368: reached, light min 10, 0 cells under 8
  - room y+9 9 cells round 565,-41,370: reached, light min 10, 0 cells under 8; bed, bed, table
  - room y+9 12 cells round 570,-41,370: reached, light min 11, 0 cells under 8; bed, bed, table
  - room y+12 15 cells round 567,-38,368: NOT reached, light min -, 0 cells under 8
- defects:
  - **MED** room of 17 cells at y+0 round 565,-50,363 cannot be reached (nothing in it)
  - **LOW** room of 4 cells at y+5 round 562,-45,371 cannot be reached (nothing in it)
  - **MED** room of 13 cells at y+6 round 567,-44,365 cannot be reached (has viewpoint)
  - **MED** room of 15 cells at y+12 round 567,-38,368 cannot be reached (nothing in it)
  - **LOW** 1 full-block step(s) on the way to rooms, beds or work with no stair (a player must jump): 561,-49,371
- shots: mediterranean_inn_main.jpg, mediterranean_inn_bed.jpg, mediterranean_inn_stair.jpg

### mediterranean tavern — FAIL

`hearth:commerce/tavern` mediterranean, 11x10 facing south, tier 2, origin 672, -50, 360, 0 beds. Street start 677,-50,371.

- rooms reached 3/3; entrances 1/1; doors bad 0/1
- POIs walked to (model): job_site 1/2, storage 1/1, market_stall 1/1, counter 1/1, seat 4/4, table 1/1, hearth 1/1, door 1/1, gaming_table 1/1, stage 1/1
- folk pathfinder street -> bed/job/store: 3/4
- player driven (real movement & collision): 7/7 paths completed, 0 stuck
- stairs: 4 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 75 cells, 0 under 8, 0 at 0
  - room y-4 10 cells round 678,-54,363: reached, light min 11, 0 cells under 8; storage
  - room y+0 5 cells round 675,-50,370: reached, light min 11, 0 cells under 8; door
  - room y+0 27 cells round 677,-50,366: reached, light min 12, 0 cells under 8; job_site, counter, market_stall, seat, hearth, stage, gaming_table, seat, seat, table
- defects:
  - **HIGH** job_site (entertainer) at 681, -49, 366 cannot be walked to by a player
  - **HIGH** folk pathfinder finds no way from the street to the job_site (entertainer) at 681, -49, 366 (gets as far as 680,-49,367)
- shots: mediterranean_tavern_main.jpg

### mediterranean smithy — PASS

`hearth:work/smithy` mediterranean, 11x12 facing south, tier 2, origin 784, -50, 360, 0 beds. Street start 789,-50,373.

- rooms reached 1/2; entrances 1/1; doors bad 0/0
- POIs walked to (model): job_site 5/5, storage 2/2, seat 1/1, door 1/1
- folk pathfinder street -> bed/job/store: 7/7
- player driven (real movement & collision): 8/8 paths completed, 0 stuck
- stairs: 0 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 45 cells, 1 under 8, 0 at 0
  - room y+0 33 cells round 790,-50,366: reached, light min 10, 0 cells under 8; door, job_site, job_site, job_site, storage, storage, job_site, job_site, seat
  - room y+5 4 cells round 789,-45,367: NOT reached, light min -, 0 cells under 8
- defects:
  - **LOW** room of 4 cells at y+5 round 789,-45,367 cannot be reached (nothing in it)
- shots: mediterranean_smithy_main.jpg

### mediterranean bakery — WARN

`hearth:work/bakery` mediterranean, 10x8 facing south, tier 2, origin 896, -50, 360, 0 beds. Street start 901,-50,369.

- rooms reached 2/3; entrances 3/3; doors bad 0/2
- POIs walked to (model): job_site 3/3, storage 1/1, counter 1/1, seat 1/1, table 1/1, door 2/2
- folk pathfinder street -> bed/job/store: 5/5
- player driven (real movement & collision): 7/7 paths completed, 0 stuck
- stairs: 0 flight steps, 0 unclimbable; jumps needed 2; tight head room 0
- light under roof: 28 cells, 3 under 8, 0 at 0
  - room y+0 8 cells round 902,-50,364: reached, light min 11, 0 cells under 8; job_site, job_site, counter, storage, table, seat
  - room y+1 4 cells round 904,-49,365: reached, light min 11, 0 cells under 8; job_site, counter, seat
  - room y+5 20 cells round 903,-45,364: NOT reached, light min -, 0 cells under 8
- defects:
  - **MED** room of 20 cells at y+5 round 903,-45,364 cannot be reached (nothing in it)
  - **LOW** 2 full-block step(s) on the way to rooms, beds or work with no stair (a player must jump): 901,-49,365; 902,-48,365
- shots: mediterranean_bakery_main.jpg

### mediterranean windmill — FAIL

`hearth:work/windmill` mediterranean, 16x15 facing south, tier 2, origin 1008, -50, 360, 0 beds. Street start 1016,-50,376.

- rooms reached 3/6; entrances 2/2; doors bad 0/1
- POIs walked to (model): job_site 2/2, storage 1/1, door 1/1
- folk pathfinder street -> bed/job/store: 3/3
- player driven (real movement & collision): 6/6 paths completed, 0 stuck
- stairs: 0 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 61 cells, 22 under 8, 0 at 0
  - room y+0 7 cells round 1012,-50,370: reached, light min 5, 3 cells under 8
  - room y+0 20 cells round 1016,-50,367: reached, light min 11, 0 cells under 8; job_site, job_site
  - room y+0 8 cells round 1020,-50,370: reached, light min 6, 4 cells under 8
  - room y+1 24 cells round 1016,-49,367: NOT reached, light min -, 0 cells under 8; job_site
  - room y+5 14 cells round 1016,-45,366: NOT reached, light min -, 0 cells under 8
  - room y+9 18 cells round 1016,-41,366: NOT reached, light min -, 0 cells under 8
- defects:
  - **LOW** room at y+0 round 1012,-50,370 is dim: 3 of 7 cells under light 8 (min 5)
  - **LOW** room at y+0 round 1020,-50,370 is dim: 4 of 8 cells under light 8 (min 6)
  - **HIGH** room of 24 cells at y+1 round 1016,-49,367 cannot be reached (has job_site)
  - **MED** room of 14 cells at y+5 round 1016,-45,366 cannot be reached (nothing in it)
  - **MED** room of 18 cells at y+9 round 1016,-41,366 cannot be reached (nothing in it)
- shots: mediterranean_windmill_main.jpg

### mediterranean chapel — WARN

`hearth:civic/chapel` mediterranean, 9x15 facing south, tier 2, origin 1120, -50, 360, 0 beds. Street start 1124,-50,376.

- rooms reached 3/4; entrances 1/1; doors bad 0/1
- POIs walked to (model): job_site 1/1, seat 4/4, altar 1/1, door 1/1
- folk pathfinder street -> bed/job/store: 1/1
- player driven (real movement & collision): 4/4 paths completed, 0 stuck
- stairs: 0 flight steps, 0 unclimbable; jumps needed 3; tight head room 0
- light under roof: 61 cells, 10 under 8, 0 at 0
  - room y+1 5 cells round 1125,-49,361: reached, light min 4, 5 cells under 8
  - room y+1 26 cells round 1125,-49,367: reached, light min 11, 0 cells under 8; seat, seat, seat, seat, altar, job_site
  - room y+2 4 cells round 1125,-48,363: reached, light min 11, 0 cells under 8; altar, job_site
  - room y+6 9 cells round 1125,-44,363: NOT reached, light min -, 0 cells under 8
- defects:
  - **LOW** room at y+1 round 1125,-49,361 is dim: 5 of 5 cells under light 8 (min 4)
  - **MED** room of 9 cells at y+6 round 1125,-44,363 cannot be reached (nothing in it)
  - **LOW** 3 full-block step(s) on the way to rooms, beds or work with no stair (a player must jump): 1124,-49,372; 1128,-49,360; 1125,-48,363
- shots: mediterranean_chapel_main.jpg

### mediterranean town_hall — WARN

`hearth:civic/town_hall` mediterranean, 16x14 facing south, tier 3, origin 1232, -50, 360, 0 beds. Street start 1240,-50,375.

- rooms reached 9/13; entrances 2/2; doors bad 0/2
- POIs walked to (model): storage 2/2, notice_board 1/1, meeting_point 1/1, seat 7/7, table 1/1, hearth 1/1, lectern 1/1, door 1/1, viewpoint 1/2
- folk pathfinder street -> bed/job/store: 1/2
- player driven (real movement & collision): 12/12 paths completed, 0 stuck
- stairs: 5 flight steps, 0 unclimbable; jumps needed 7; tight head room 0
- light under roof: 208 cells, 5 under 8, 0 at 0
  - room y+1 5 cells round 1233,-49,361: reached, light min 8, 0 cells under 8
  - room y+1 7 cells round 1233,-49,368: reached, light min 9, 0 cells under 8
  - room y+1 50 cells round 1240,-49,364: reached, light min 11, 0 cells under 8; lectern, storage, seat, seat
  - room y+1 23 cells round 1238,-49,370: reached, light min 9, 0 cells under 8; door, notice_board
  - room y+1 4 cells round 1242,-49,361: reached, light min 7, 2 cells under 8
  - room y+1 5 cells round 1247,-49,361: reached, light min 6, 3 cells under 8
  - room y+1 7 cells round 1248,-49,368: reached, light min 8, 0 cells under 8
  - room y+6 65 cells round 1239,-44,366: reached, light min 10, 0 cells under 8; table, seat, seat, seat, seat, seat, meeting_point, storage, hearth
  - room y+6 9 cells round 1245,-44,369: NOT reached, light min -, 0 cells under 8
  - room y+7 5 cells round 1240,-43,372: NOT reached, light min -, 0 cells under 8; viewpoint
  - room y+7 4 cells round 1240,-43,366: reached, light min 12, 0 cells under 8; table, seat, seat, seat, seat, seat
  - room y+12 13 cells round 1246,-38,370: NOT reached, light min -, 0 cells under 8
  - room y+20 21 cells round 1245,-30,369: NOT reached, light min -, 0 cells under 8; viewpoint
- defects:
  - **LOW** room at y+1 round 1242,-49,361 is dim: 2 of 4 cells under light 8 (min 7)
  - **LOW** room at y+1 round 1247,-49,361 is dim: 3 of 5 cells under light 8 (min 6)
  - **MED** room of 9 cells at y+6 round 1245,-44,369 cannot be reached (nothing in it)
  - **LOW** room of 5 cells at y+7 round 1240,-43,372 cannot be reached (has viewpoint)
  - **MED** room of 13 cells at y+12 round 1246,-38,370 cannot be reached (nothing in it)
  - **MED** room of 21 cells at y+20 round 1245,-30,369 cannot be reached (has viewpoint)
  - **MED** folk pathfinder finds no way from the street to the storage at 1235, -44, 362 (gets as far as 1235,-48,360)
  - **MED** 7 full-block step(s) on the way to rooms, beds or work with no stair (a player must jump): 1245,-48,362; 1232,-49,361; 1232,-49,367; 1241,-49,360; 1247,-49,361; 1247,-49,367
- shots: mediterranean_town_hall_main.jpg

### mediterranean library — WARN

`hearth:civic/library` mediterranean, 10x14 facing south, tier 2, origin 1344, -50, 360, 0 beds. Street start 1349,-50,375.

- rooms reached 2/3; entrances 1/1; doors bad 0/1
- POIs walked to (model): job_site 2/2, storage 1/1, seat 4/4, table 2/2, lectern 2/2, door 1/1, viewpoint 1/1
- folk pathfinder street -> bed/job/store: 3/3
- player driven (real movement & collision): 6/6 paths completed, 0 stuck
- stairs: 4 flight steps, 0 unclimbable; jumps needed 1; tight head room 0
- light under roof: 96 cells, 0 under 8, 0 at 0
  - room y+1 54 cells round 1349,-49,366: reached, light min 10, 0 cells under 8; job_site, lectern, job_site, lectern, table, seat, seat, table, seat, seat
  - room y+5 18 cells round 1349,-45,363: reached, light min 9, 0 cells under 8; viewpoint, storage
  - room y+11 16 cells round 1349,-39,367: NOT reached, light min -, 0 cells under 8
- defects:
  - **MED** room of 16 cells at y+11 round 1349,-39,367 cannot be reached (nothing in it)
  - **LOW** 1 full-block step(s) on the way to rooms, beds or work with no stair (a player must jump): 1348,-49,372
- shots: mediterranean_library_main.jpg, mediterranean_library_stair.jpg

### mediterranean market_hall — FAIL

`hearth:commerce/market_hall` mediterranean, 12x20 facing south, tier 3, origin 1456, -50, 360, 0 beds. Street start 1462,-50,381.

- rooms reached 1/6; entrances 2/2; doors bad 1/1
- POIs walked to (model): job_site 6/6, storage 0/2, market_stall 6/6, counter 6/6, meeting_point 1/2, seat 0/10, table 0/1, hearth 0/1, door 2/2, viewpoint 0/1
- folk pathfinder street -> bed/job/store: 12/14
- player driven (real movement & collision): 13/13 paths completed, 0 stuck
- stairs: 0 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 180 cells, 20 under 8, 0 at 0
  - room y+1 151 cells round 1462,-49,370: reached, light min 2, 20 cells under 8; door, job_site, counter, market_stall, job_site, counter, market_stall, job_site, counter, market_stall, job_site, counter, market_stall, job_site, counter, market_stall, job_site, counter, market_stall, meeting_point, door
  - room y+1 12 cells round 1462,-49,363: NOT reached, light min -, 0 cells under 8; storage
  - room y+6 91 cells round 1462,-44,370: NOT reached, light min -, 0 cells under 8; table, seat, seat, seat, seat, seat, seat, seat, seat, seat, seat, meeting_point, hearth, storage
  - room y+7 6 cells round 1462,-43,379: NOT reached, light min -, 0 cells under 8; viewpoint
  - room y+7 7 cells round 1461,-43,366: NOT reached, light min -, 0 cells under 8; seat, seat, seat, seat, seat, seat
  - room y+7 5 cells round 1462,-43,373: NOT reached, light min -, 0 cells under 8; table, seat, seat, seat, seat, seat, seat, meeting_point
- defects:
  - **MED** door at 1464, -49, 364 is reached on one side but cannot be passed
  - **HIGH** room of 12 cells at y+1 round 1462,-49,363 cannot be reached (has storage)
  - **HIGH** room of 91 cells at y+6 round 1462,-44,370 cannot be reached (has table, seat, seat, seat, seat, seat, seat, seat, seat, seat, seat, meeting_point, hearth, storage)
  - **LOW** room of 6 cells at y+7 round 1462,-43,379 cannot be reached (has viewpoint)
  - **LOW** room of 7 cells at y+7 round 1461,-43,366 cannot be reached (has seat, seat, seat, seat, seat, seat)
  - **HIGH** room of 5 cells at y+7 round 1462,-43,373 cannot be reached (has table, seat, seat, seat, seat, seat, seat, meeting_point)
  - **HIGH** storage at 1464, -49, 362 cannot be walked to by a player
  - **HIGH** folk pathfinder finds no way from the street to the storage at 1464, -49, 362 (gets as far as 1464,-49,360)
  - **HIGH** storage at 1464, -44, 376 cannot be walked to by a player
  - **HIGH** folk pathfinder finds no way from the street to the storage at 1464, -44, 376 (gets as far as 1464,-48,376)
- shots: mediterranean_market_hall_main.jpg

### mediterranean manor — WARN

`hearth:house/manor` mediterranean, 25x16 facing south, tier 3, origin 1568, -50, 360, 4 beds. Street start 1580,-50,377.

- rooms reached 13/15; entrances 2/2; doors bad 0/7
- POIs walked to (model): bed 4/4, storage 7/7, seat 5/5, table 7/7, hearth 1/1, lectern 1/1, door 1/1, gate 1/1, viewpoint 1/1
- folk pathfinder street -> bed/job/store: 10/11
- player driven (real movement & collision): 26/26 paths completed, 0 stuck
- stairs: 5 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 255 cells, 24 under 8, 0 at 0
  - room y+0 5 cells round 1570,-50,362: reached, light min 5, 5 cells under 8
  - room y+0 4 cells round 1570,-50,369: reached, light min 7, 1 cells under 8
  - room y+0 5 cells round 1591,-50,362: reached, light min 5, 4 cells under 8
  - room y+0 4 cells round 1591,-50,369: reached, light min 7, 1 cells under 8
  - room y+1 16 cells round 1574,-49,366: reached, light min 11, 0 cells under 8; hearth, lectern, seat, table, storage, table, seat
  - room y+1 24 cells round 1581,-49,366: reached, light min 11, 0 cells under 8; table
  - room y+1 6 cells round 1581,-49,370: reached, light min 11, 0 cells under 8; door
  - room y+1 14 cells round 1587,-49,365: reached, light min 11, 0 cells under 8; table, seat, seat, storage
  - room y+2 4 cells round 1582,-48,364: reached, light min 11, 0 cells under 8; table
  - room y+3 6 cells round 1574,-47,364: NOT reached, light min -, 0 cells under 8
  - room y+6 25 cells round 1574,-44,365: reached, light min 11, 0 cells under 8; bed, bed, table
  - room y+6 17 cells round 1581,-44,366: reached, light min 11, 0 cells under 8; seat, table, storage
  - room y+6 21 cells round 1588,-44,366: reached, light min 11, 0 cells under 8; bed, bed, storage, table
  - room y+7 4 cells round 1581,-43,370: NOT reached, light min -, 0 cells under 8; viewpoint
  - room y+10 53 cells round 1581,-40,366: reached, light min 10, 0 cells under 8; storage, storage, storage
- defects:
  - **LOW** room at y+0 round 1570,-50,362 is dim: 5 of 5 cells under light 8 (min 5)
  - **LOW** room at y+0 round 1591,-50,362 is dim: 4 of 5 cells under light 8 (min 5)
  - **LOW** room of 6 cells at y+3 round 1574,-47,364 cannot be reached (nothing in it)
  - **LOW** room of 4 cells at y+7 round 1581,-43,370 cannot be reached (has viewpoint)
  - **MED** folk pathfinder finds no way from the street to the storage at 1587, -40, 366 (gets as far as 1586,-43,366)
- shots: mediterranean_manor_main.jpg, mediterranean_manor_bed.jpg, mediterranean_manor_stair.jpg

### mediterranean keep — FAIL

`hearth:noble/keep` mediterranean, 18x20 facing south, tier 3, origin 1680, -50, 360, 0 beds. Street start 1689,-50,381.

- rooms reached 5/6; entrances 1/1; doors bad 0/1
- POIs walked to (model): job_site 4/4, storage 3/3, meeting_point 2/2, seat 1/1, table 4/4, altar 1/1, lectern 1/1, guard_post 4/5, gate 1/1, viewpoint 1/1, hitching_post 1/1, throne 1/1, strongbox 1/1
- folk pathfinder street -> bed/job/store: 5/7
- player driven (real movement & collision): 13/13 paths completed, 0 stuck
- stairs: 5 flight steps, 0 unclimbable; jumps needed 2; tight head room 0
- light under roof: 382 cells, 55 under 8, 0 at 0
  - room y+0 196 cells round 1686,-50,366: reached, light min 11, 0 cells under 8; job_site, storage, job_site, throne, seat, job_site, meeting_point, altar, table, lectern, job_site, storage, strongbox, table, table, table
  - room y+0 39 cells round 1688,-50,378: reached, light min 8, 0 cells under 8; gate, meeting_point
  - room y+0 13 cells round 1693,-50,372: reached, light min 9, 0 cells under 8; storage, meeting_point
  - room y+4 5 cells round 1691,-46,375: NOT reached, light min -, 0 cells under 8
  - room y+5 7 cells round 1689,-45,381: reached, light min 7, 2 cells under 8
  - room y+5 14 cells round 1688,-45,378: reached, light min 12, 0 cells under 8; guard_post
- defects:
  - **LOW** room of 5 cells at y+4 round 1691,-46,375 cannot be reached (nothing in it)
  - **HIGH** folk pathfinder finds no way from the street to the job_site (steward) at 1685, -35, 367 (gets as far as 1685,-45,367)
  - **MED** folk pathfinder finds no way from the street to the storage at 1684, -35, 367 (gets as far as 1684,-46,366)
  - **LOW** 2 full-block step(s) on the way to rooms, beds or work with no stair (a player must jump): 1694,-49,376; 1688,-44,379
- shots: mediterranean_keep_main.jpg

### mediterranean mine — WARN

`hearth:extract/mine` mediterranean, 10x28 facing south, tier 2, origin 1792, -50, 360, 0 beds. Street start 1797,-50,389.

- rooms reached 1/1; entrances 1/1; doors bad 0/0
- POIs walked to (model): job_site 1/1, storage 2/2, work_face 6/6
- folk pathfinder street -> bed/job/store: 3/7
- player driven (real movement & collision): 8/8 paths completed, 0 stuck
- stairs: 0 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 58 cells, 0 under 8, 0 at 0
  - room y-2 58 cells round 1797,-52,370: reached, light min 11, 0 cells under 8; work_face, work_face, work_face, work_face, work_face, work_face
- defects:
  - **MED** folk pathfinder finds no way from the street to the work_face (miner) at 1794, -52, 378 (gets as far as 1796,-51,381)
  - **MED** folk pathfinder finds no way from the street to the work_face (miner) at 1797, -52, 374 (gets as far as 1798,-51,381)
  - **MED** folk pathfinder finds no way from the street to the work_face (miner) at 1800, -52, 369 (gets as far as 1800,-44,377)
  - **MED** folk pathfinder finds no way from the street to the work_face (miner) at 1797, -52, 367 (gets as far as 1797,-43,376)
- shots: mediterranean_mine_main.jpg

### northern cottage — WARN

`hearth:house/cottage` northern, 11x11 facing south, tier 2, origin 0, -50, 520, 2 beds. Street start 5,-50,532.

- rooms reached 1/1; entrances 1/1; doors bad 0/1
- POIs walked to (model): bed 2/2, storage 1/1, seat 1/1, table 2/2, hearth 1/1, door 1/1
- folk pathfinder street -> bed/job/store: 3/3
- player driven (real movement & collision): 4/4 paths completed, 0 stuck
- stairs: 2 flight steps, 2 unclimbable; jumps needed 0; tight head room 0
- light under roof: 52 cells, 2 under 8, 0 at 0
  - room y+0 30 cells round 5,-50,524: reached, light min 10, 0 cells under 8; bed, bed, hearth, table, storage, table, seat
- defects:
  - **MED** stair step at 0, -47, 528 (going north) cannot be climbed: blocked stepping onto it
  - **MED** stair step at 10, -47, 528 (going north) cannot be climbed: blocked stepping onto it
- shots: northern_cottage_main.jpg, northern_cottage_bed.jpg, northern_cottage_stair.jpg

### northern farmhouse — FAIL

`hearth:house/farmhouse` northern, 17x14 facing south, tier 2, origin 112, -50, 520, 2 beds. Street start 120,-50,535.

- rooms reached 5/7; entrances 3/3; doors bad 0/1
- POIs walked to (model): bed 2/2, job_site 3/3, storage 3/3, seat 2/2, table 2/2, hearth 1/1, door 2/2, gate 1/1, work_face 2/2
- folk pathfinder street -> bed/job/store: 6/10
- player driven (real movement & collision): 16/16 paths completed, 0 stuck
- stairs: 4 flight steps, 0 unclimbable; jumps needed 1; tight head room 0
- light under roof: 139 cells, 0 under 8, 0 at 0
  - room y+0 33 cells round 117,-50,527: reached, light min 9, 0 cells under 8; door, door, work_face, work_face, job_site, storage
  - room y+0 25 cells round 124,-50,525: reached, light min 11, 0 cells under 8; hearth, storage, table, seat, seat
  - room y+1 19 cells round 115,-49,523: NOT reached, light min -, 0 cells under 8
  - room y+1 15 cells round 128,-49,524: reached, light min 8, 0 cells under 8
  - room y+4 28 cells round 124,-46,525: reached, light min 10, 0 cells under 8; bed, bed, storage, table
  - room y+5 14 cells round 117,-45,526: reached, light min 10, 0 cells under 8; job_site
  - room y+8 8 cells round 120,-42,525: NOT reached, light min -, 0 cells under 8
- defects:
  - **MED** room of 19 cells at y+1 round 115,-49,523 cannot be reached (nothing in it)
  - **MED** room of 8 cells at y+8 round 120,-42,525 cannot be reached (nothing in it)
  - **HIGH** folk pathfinder finds no way from the street to the bed at 121, -46, 525 (gets as far as 122,-49,525)
  - **HIGH** folk pathfinder finds no way from the street to the bed at 121, -46, 522 (gets as far as 123,-48,522)
  - **HIGH** folk pathfinder finds no way from the street to the job_site (herder) at 118, -45, 525 (gets as far as 118,-49,525)
  - **MED** folk pathfinder finds no way from the street to the storage at 122, -46, 524 (gets as far as 123,-48,522)
  - **LOW** 1 full-block step(s) on the way to rooms, beds or work with no stair (a player must jump): 128,-49,523
- shots: northern_farmhouse_main.jpg, northern_farmhouse_bed.jpg, northern_farmhouse_stair.jpg

### northern townhouse — PASS

`hearth:house/townhouse` northern, 5x13 facing south, tier 2, origin 224, -50, 520, 2 beds. Street start 226,-50,534.

- rooms reached 3/3; entrances 1/1; doors bad 0/1
- POIs walked to (model): bed 2/2, storage 2/2, seat 1/1, table 2/2, hearth 1/1, door 1/1
- folk pathfinder street -> bed/job/store: 4/4
- player driven (real movement & collision): 9/9 paths completed, 0 stuck
- stairs: 8 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 64 cells, 0 under 8, 0 at 0
  - room y+0 16 cells round 227,-50,527: reached, light min 11, 0 cells under 8; hearth, storage
  - room y+4 12 cells round 227,-46,527: reached, light min 11, 0 cells under 8; seat, table, storage
  - room y+8 15 cells round 227,-42,527: reached, light min 10, 0 cells under 8; bed, bed, table
- shots: northern_townhouse_main.jpg, northern_townhouse_bed.jpg, northern_townhouse_stair.jpg

### northern tenement — FAIL

`hearth:house/tenement` northern, 9x13 facing south, tier 2, origin 336, -50, 520, 11 beds. Street start 340,-50,534.

- rooms reached 5/7; entrances 1/1; doors bad 0/8
- POIs walked to (model): bed 11/11, storage 8/8, seat 4/4, table 6/6, hearth 1/1, door 1/1, viewpoint 0/2
- folk pathfinder street -> bed/job/store: 11/19
- player driven (real movement & collision): 30/30 paths completed, 0 stuck
- stairs: 12 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 224 cells, 4 under 8, 0 at 0
  - room y+0 4 cells round 338,-50,525: reached, light min 12, 0 cells under 8; hearth
  - room y+0 39 cells round 341,-50,528: reached, light min 11, 0 cells under 8; bed, bed, bed, hearth, storage, table, storage
  - room y+4 36 cells round 341,-46,528: reached, light min 10, 0 cells under 8; bed, bed, bed, storage, table, storage
  - room y+5 4 cells round 341,-45,521: NOT reached, light min -, 0 cells under 8; viewpoint
  - room y+8 34 cells round 341,-42,528: reached, light min 10, 0 cells under 8; bed, bed, storage, storage, table, storage, table, seat, seat
  - room y+9 5 cells round 341,-41,521: NOT reached, light min -, 0 cells under 8; viewpoint
  - room y+12 37 cells round 341,-38,528: reached, light min 10, 0 cells under 8; bed, bed, bed, table, storage, table, seat, seat
- defects:
  - **LOW** room of 4 cells at y+5 round 341,-45,521 cannot be reached (has viewpoint)
  - **LOW** room of 5 cells at y+9 round 341,-41,521 cannot be reached (has viewpoint)
  - **HIGH** folk pathfinder finds no way from the street to the bed at 343, -38, 525 (gets as far as 338,-42,525)
  - **HIGH** folk pathfinder finds no way from the street to the bed at 337, -42, 527 (gets as far as 337,-42,525)
  - **HIGH** folk pathfinder finds no way from the street to the bed at 343, -42, 529 (gets as far as 342,-46,527)
  - **HIGH** folk pathfinder finds no way from the street to the bed at 337, -38, 527 (gets as far as 337,-38,525)
  - **HIGH** folk pathfinder finds no way from the street to the bed at 342, -38, 530 (gets as far as 338,-42,525)
  - **MED** folk pathfinder finds no way from the street to the storage at 342, -42, 530 (gets as far as 342,-46,528)
  - **MED** folk pathfinder finds no way from the street to the storage at 338, -42, 528 (gets as far as 340,-42,526)
  - **MED** folk pathfinder finds no way from the street to the storage at 341, -38, 523 (gets as far as 339,-38,525)
- shots: northern_tenement_main.jpg, northern_tenement_bed.jpg, northern_tenement_stair.jpg

### northern merchant_house — FAIL

`hearth:house/merchant_house` northern, 9x12 facing south, tier 2, origin 448, -50, 520, 5 beds. Street start 452,-50,533.

- rooms reached 6/6; entrances 1/1; doors bad 0/1
- POIs walked to (model): bed 5/5, job_site 1/1, storage 9/9, market_stall 1/1, counter 1/1, seat 3/3, table 4/4, hearth 1/1, door 1/1, strongbox 1/1
- folk pathfinder street -> bed/job/store: 7/16
- player driven (real movement & collision): 28/28 paths completed, 0 stuck
- stairs: 12 flight steps, 0 unclimbable; jumps needed 1; tight head room 0
- light under roof: 206 cells, 4 under 8, 0 at 0
  - room y+0 36 cells round 452,-50,526: reached, light min 11, 0 cells under 8; counter, market_stall, job_site, strongbox, seat, table, storage, storage, storage
  - room y+0 5 cells round 452,-50,532: reached, light min 11, 0 cells under 8; door
  - room y+4 41 cells round 452,-46,526: reached, light min 11, 0 cells under 8; hearth, storage, table, seat, seat
  - room y+8 39 cells round 452,-42,526: reached, light min 11, 0 cells under 8; bed, bed, bed, storage, table
  - room y+12 37 cells round 452,-38,527: reached, light min 11, 0 cells under 8; bed, bed, storage, storage, table, storage, storage
  - room y+13 4 cells round 452,-37,523: reached, light min 11, 0 cells under 8; storage
- defects:
  - **HIGH** folk pathfinder finds no way from the street to the bed at 455, -38, 528 (gets as far as 454,-38,527)
  - **HIGH** folk pathfinder finds no way from the street to the bed at 450, -38, 529 (gets as far as 454,-42,524)
  - **HIGH** folk pathfinder finds no way from the street to the bed at 450, -42, 522 (gets as far as 453,-42,523)
  - **HIGH** folk pathfinder finds no way from the street to the bed at 449, -42, 528 (gets as far as 449,-46,528)
  - **MED** folk pathfinder finds no way from the street to the storage at 450, -42, 527 (gets as far as 452,-45,526)
  - **MED** folk pathfinder finds no way from the street to the storage at 454, -38, 529 (gets as far as 454,-42,529)
  - **MED** folk pathfinder finds no way from the street to the storage at 449, -38, 528 (gets as far as 454,-42,524)
  - **MED** folk pathfinder finds no way from the street to the storage at 450, -38, 524 (gets as far as 450,-41,523)
  - **MED** folk pathfinder finds no way from the street to the storage at 450, -38, 523 (gets as far as 450,-41,523)
  - **LOW** 1 full-block step(s) on the way to rooms, beds or work with no stair (a player must jump): 451,-37,522
- shots: northern_merchant_house_main.jpg, northern_merchant_house_bed.jpg, northern_merchant_house_stair.jpg

### northern inn — PASS

`hearth:commerce/inn` northern, 12x13 facing south, tier 2, origin 560, -50, 520, 3 beds. Street start 566,-50,534.

- rooms reached 8/8; entrances 1/1; doors bad 0/4
- POIs walked to (model): bed 3/3, job_site 1/1, storage 3/3, market_stall 1/1, counter 1/1, seat 3/3, table 4/4, hearth 1/1, door 1/1
- folk pathfinder street -> bed/job/store: 8/8
- player driven (real movement & collision): 17/17 paths completed, 0 stuck
- stairs: 5 flight steps, 0 unclimbable; jumps needed 3; tight head room 0
- light under roof: 139 cells, 8 under 8, 0 at 0
  - room y+0 20 cells round 564,-50,527: reached, light min 11, 0 cells under 8; job_site, counter, market_stall, seat, hearth, table, seat, seat, table
  - room y+0 4 cells round 565,-50,523: reached, light min 4, 4 cells under 8
  - room y+0 4 cells round 565,-50,532: reached, light min 13, 0 cells under 8; door
  - room y+0 31 cells round 570,-50,527: reached, light min 7, 1 cells under 8
  - room y+5 9 cells round 566,-45,525: reached, light min 11, 0 cells under 8; storage
  - room y+5 9 cells round 565,-45,528: reached, light min 11, 0 cells under 8; bed, storage, table
  - room y+5 12 cells round 569,-45,528: reached, light min 10, 0 cells under 8; bed, bed, storage, table
  - room y+8 17 cells round 566,-42,527: reached, light min 12, 0 cells under 8
- defects:
  - **LOW** room at y+0 round 565,-50,523 is dim: 4 of 4 cells under light 8 (min 4)
  - **LOW** 3 full-block step(s) on the way to rooms, beds or work with no stair (a player must jump): 561,-49,529; 563,-43,526; 563,-42,525
- shots: northern_inn_main.jpg, northern_inn_bed.jpg

### northern tavern — PASS

`hearth:commerce/tavern` northern, 12x10 facing south, tier 2, origin 672, -50, 520, 0 beds. Street start 678,-50,531.

- rooms reached 4/7; entrances 1/1; doors bad 0/1
- POIs walked to (model): job_site 1/1, storage 1/1, market_stall 1/1, counter 1/1, seat 8/8, table 1/1, hearth 1/1, door 1/1, gaming_table 1/1
- folk pathfinder street -> bed/job/store: 3/3
- player driven (real movement & collision): 8/8 paths completed, 0 stuck
- stairs: 4 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 80 cells, 1 under 8, 0 at 0
  - room y-4 11 cells round 677,-54,523: reached, light min 11, 0 cells under 8; storage
  - room y+0 32 cells round 678,-50,525: reached, light min 11, 0 cells under 8; job_site, counter, market_stall, seat, seat, hearth, gaming_table, seat, seat, seat, seat, seat, seat, table
  - room y+0 4 cells round 675,-50,530: reached, light min 7, 1 cells under 8
  - room y+0 5 cells round 681,-50,530: reached, light min 10, 0 cells under 8; door
  - room y+4 4 cells round 675,-46,525: NOT reached, light min -, 0 cells under 8
  - room y+4 4 cells round 678,-46,525: NOT reached, light min -, 0 cells under 8
  - room y+4 4 cells round 681,-46,525: NOT reached, light min -, 0 cells under 8
- defects:
  - **LOW** room of 4 cells at y+4 round 675,-46,525 cannot be reached (nothing in it)
  - **LOW** room of 4 cells at y+4 round 678,-46,525 cannot be reached (nothing in it)
  - **LOW** room of 4 cells at y+4 round 681,-46,525 cannot be reached (nothing in it)
- shots: northern_tavern_main.jpg

### northern smithy — PASS

`hearth:work/smithy` northern, 9x9 facing south, tier 2, origin 784, -50, 520, 0 beds. Street start 788,-50,530.

- rooms reached 1/1; entrances 1/1; doors bad 0/0
- POIs walked to (model): job_site 3/3, storage 2/2, seat 1/1, door 1/1
- folk pathfinder street -> bed/job/store: 5/5
- player driven (real movement & collision): 5/5 paths completed, 0 stuck
- stairs: 0 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 46 cells, 1 under 8, 0 at 0
  - room y+0 39 cells round 789,-50,526: reached, light min 8, 0 cells under 8; door, job_site, job_site, job_site, storage, storage, seat
- shots: northern_smithy_main.jpg

### northern bakery — FAIL

`hearth:work/bakery` northern, 8x8 facing south, tier 2, origin 896, -50, 520, 0 beds. Street start 900,-50,529.

- rooms reached 2/4; entrances 2/2; doors bad 0/1
- POIs walked to (model): job_site 2/2, storage 1/1, counter 1/1, table 1/1, door 1/1
- folk pathfinder street -> bed/job/store: 4/4
- player driven (real movement & collision): 6/6 paths completed, 0 stuck
- stairs: 0 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 23 cells, 2 under 8, 0 at 0
  - room y+0 11 cells round 900,-50,524: reached, light min 11, 0 cells under 8; job_site, job_site, counter, storage, table
  - room y+0 4 cells round 901,-50,528: reached, light min 12, 0 cells under 8; door
  - room y+1 4 cells round 900,-49,525: NOT reached, light min -, 0 cells under 8; job_site, job_site, table
  - room y+5 16 cells round 900,-45,524: NOT reached, light min -, 0 cells under 8
- defects:
  - **HIGH** room of 4 cells at y+1 round 900,-49,525 cannot be reached (has job_site, job_site, table)
  - **MED** room of 16 cells at y+5 round 900,-45,524 cannot be reached (nothing in it)
- shots: northern_bakery_main.jpg

### northern windmill — WARN

`hearth:work/windmill` northern, 13x12 facing south, tier 2, origin 1008, -50, 520, 0 beds. Street start 1014,-50,533.

- rooms reached 4/11; entrances 2/2; doors bad 0/1
- POIs walked to (model): job_site 2/2, storage 1/1, door 1/1
- folk pathfinder street -> bed/job/store: 3/3
- player driven (real movement & collision): 7/7 paths completed, 0 stuck
- stairs: 0 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 50 cells, 14 under 8, 0 at 0
  - room y+0 19 cells round 1015,-50,529: reached, light min 5, 4 cells under 8; door, job_site, job_site, storage
  - room y+0 4 cells round 1011,-50,523: reached, light min 6, 2 cells under 8
  - room y+0 15 cells round 1015,-50,525: reached, light min 11, 0 cells under 8
  - room y+0 4 cells round 1018,-50,523: reached, light min 1, 4 cells under 8
  - room y+1 14 cells round 1015,-49,525: NOT reached, light min -, 0 cells under 8
  - room y+5 9 cells round 1015,-45,526: NOT reached, light min -, 0 cells under 8
  - room y+7 7 cells round 1011,-43,526: NOT reached, light min -, 0 cells under 8
  - room y+7 7 cells round 1015,-43,529: NOT reached, light min -, 0 cells under 8
  - room y+7 7 cells round 1018,-43,526: NOT reached, light min -, 0 cells under 8
  - room y+9 9 cells round 1015,-41,526: NOT reached, light min -, 0 cells under 8
  - room y+18 6 cells round 1014,-32,526: NOT reached, light min -, 0 cells under 8
- defects:
  - **LOW** room at y+0 round 1011,-50,523 is dim: 2 of 4 cells under light 8 (min 6)
  - **LOW** room at y+0 round 1018,-50,523 is dim: 4 of 4 cells under light 8 (min 1)
  - **MED** room of 14 cells at y+1 round 1015,-49,525 cannot be reached (nothing in it)
  - **MED** room of 9 cells at y+5 round 1015,-45,526 cannot be reached (nothing in it)
  - **LOW** room of 7 cells at y+7 round 1011,-43,526 cannot be reached (nothing in it)
  - **LOW** room of 7 cells at y+7 round 1015,-43,529 cannot be reached (nothing in it)
  - **LOW** room of 7 cells at y+7 round 1018,-43,526 cannot be reached (nothing in it)
  - **MED** room of 9 cells at y+9 round 1015,-41,526 cannot be reached (nothing in it)
  - **LOW** room of 6 cells at y+18 round 1014,-32,526 cannot be reached (nothing in it)
- shots: northern_windmill_main.jpg

### northern chapel — PASS

`hearth:civic/chapel` northern, 11x18 facing south, tier 2, origin 1120, -50, 520, 0 beds. Street start 1125,-50,539.

- rooms reached 7/7; entrances 1/1; doors bad 0/1
- POIs walked to (model): job_site 1/1, seat 2/2, altar 1/1, door 1/1
- folk pathfinder street -> bed/job/store: 1/1
- player driven (real movement & collision): 8/8 paths completed, 0 stuck
- stairs: 0 flight steps, 0 unclimbable; jumps needed 1; tight head room 0
- light under roof: 95 cells, 24 under 8, 0 at 0
  - room y+0 5 cells round 1122,-50,523: reached, light min 6, 2 cells under 8
  - room y+0 6 cells round 1123,-50,536: reached, light min 10, 0 cells under 8
  - room y+0 5 cells round 1129,-50,523: reached, light min 4, 4 cells under 8
  - room y+0 6 cells round 1129,-50,536: reached, light min 6, 2 cells under 8
  - room y+1 32 cells round 1126,-49,531: reached, light min 2, 16 cells under 8; door
  - room y+1 26 cells round 1125,-49,529: reached, light min 11, 0 cells under 8; seat, seat, altar, job_site
  - room y+2 4 cells round 1126,-48,525: reached, light min 10, 0 cells under 8; altar, job_site
- defects:
  - **LOW** room at y+0 round 1122,-50,523 is dim: 2 of 5 cells under light 8 (min 6)
  - **LOW** room at y+0 round 1129,-50,523 is dim: 4 of 5 cells under light 8 (min 4)
  - **LOW** room at y+1 round 1126,-49,531 is dim: 16 of 32 cells under light 8 (min 2)
  - **LOW** 1 full-block step(s) on the way to rooms, beds or work with no stair (a player must jump): 1126,-48,525
- shots: northern_chapel_main.jpg

### northern town_hall — FAIL

`hearth:civic/town_hall` northern, 17x15 facing south, tier 3, origin 1232, -50, 520, 0 beds. Street start 1240,-50,536.

- rooms reached 9/11; entrances 2/2; doors bad 0/2
- POIs walked to (model): storage 2/2, notice_board 1/1, meeting_point 1/1, seat 11/11, table 1/1, hearth 1/1, lectern 1/1, door 1/1, viewpoint 1/3
- folk pathfinder street -> bed/job/store: 2/2
- player driven (real movement & collision): 12/12 paths completed, 0 stuck
- stairs: 5 flight steps, 0 unclimbable; jumps needed 6; tight head room 0
- light under roof: 263 cells, 4 under 8, 0 at 0
  - room y+1 4 cells round 1233,-49,521: reached, light min 7, 1 cells under 8
  - room y+1 5 cells round 1233,-49,530: reached, light min 9, 0 cells under 8
  - room y+1 81 cells round 1241,-49,526: reached, light min 11, 0 cells under 8; door, lectern, storage, seat, seat, seat, seat
  - room y+1 11 cells round 1236,-49,530: reached, light min 11, 0 cells under 8
  - room y+1 10 cells round 1245,-49,531: reached, light min 11, 0 cells under 8; notice_board
  - room y+1 5 cells round 1248,-49,521: reached, light min 6, 3 cells under 8
  - room y+1 5 cells round 1249,-49,530: reached, light min 9, 0 cells under 8
  - room y+6 85 cells round 1240,-44,526: reached, light min 11, 0 cells under 8; seat, seat, seat, seat, seat, seat, seat, storage, hearth
  - room y+6 6 cells round 1241,-44,530: NOT reached, light min -, 0 cells under 8; table, meeting_point
  - room y+7 4 cells round 1241,-43,526: reached, light min 12, 0 cells under 8; table, seat, seat, seat, seat, seat, seat, seat, meeting_point
  - room y+21 9 cells round 1241,-29,531: NOT reached, light min -, 0 cells under 8; viewpoint
- defects:
  - **LOW** room at y+1 round 1248,-49,521 is dim: 3 of 5 cells under light 8 (min 6)
  - **HIGH** room of 6 cells at y+6 round 1241,-44,530 cannot be reached (has table, meeting_point)
  - **MED** room of 9 cells at y+21 round 1241,-29,531 cannot be reached (has viewpoint)
  - **MED** 6 full-block step(s) on the way to rooms, beds or work with no stair (a player must jump): 1246,-48,522; 1232,-49,521; 1232,-49,529; 1248,-49,521; 1248,-49,529; 1241,-43,525
- shots: northern_town_hall_main.jpg

### northern library — PASS

`hearth:civic/library` northern, 9x14 facing south, tier 2, origin 1344, -50, 520, 0 beds. Street start 1348,-50,535.

- rooms reached 2/2; entrances 1/1; doors bad 0/1
- POIs walked to (model): job_site 2/2, storage 1/1, seat 8/8, table 4/4, lectern 2/2, door 1/1, viewpoint 1/1
- folk pathfinder street -> bed/job/store: 3/3
- player driven (real movement & collision): 6/6 paths completed, 0 stuck
- stairs: 4 flight steps, 0 unclimbable; jumps needed 1; tight head room 0
- light under roof: 83 cells, 0 under 8, 0 at 0
  - room y+1 39 cells round 1349,-49,526: reached, light min 11, 0 cells under 8; job_site, lectern, job_site, lectern, table, seat, seat, table, seat, seat, table, seat, seat, table, seat, seat
  - room y+5 17 cells round 1349,-45,523: reached, light min 10, 0 cells under 8; viewpoint, storage
- defects:
  - **LOW** 1 full-block step(s) on the way to rooms, beds or work with no stair (a player must jump): 1348,-49,532
- shots: northern_library_main.jpg, northern_library_stair.jpg

### northern market_hall — FAIL

`hearth:commerce/market_hall` northern, 14x21 facing south, tier 3, origin 1456, -50, 520, 0 beds. Street start 1463,-50,542.

- rooms reached 4/8; entrances 2/2; doors bad 1/1
- POIs walked to (model): job_site 8/8, storage 0/2, market_stall 8/8, counter 8/8, meeting_point 1/2, seat 0/12, table 0/1, hearth 0/1, door 2/2
- folk pathfinder street -> bed/job/store: 16/18
- player driven (real movement & collision): 20/20 paths completed, 0 stuck
- stairs: 0 flight steps, 0 unclimbable; jumps needed 1; tight head room 0
- light under roof: 211 cells, 17 under 8, 0 at 0
  - room y+0 6 cells round 1459,-50,540: reached, light min 10, 0 cells under 8; door
  - room y+0 7 cells round 1467,-50,540: reached, light min 8, 0 cells under 8; door
  - room y+1 152 cells round 1463,-49,532: reached, light min 7, 3 cells under 8; door, job_site, counter, market_stall, job_site, counter, market_stall, job_site, counter, market_stall, job_site, counter, market_stall, job_site, counter, market_stall, job_site, counter, market_stall, job_site, counter, market_stall, job_site, counter, market_stall, meeting_point, door
  - room y+1 10 cells round 1463,-49,521: reached, light min 2, 10 cells under 8
  - room y+1 16 cells round 1463,-49,523: NOT reached, light min -, 0 cells under 8; storage
  - room y+6 171 cells round 1463,-44,530: NOT reached, light min -, 0 cells under 8; table, seat, seat, seat, seat, seat, seat, seat, seat, seat, seat, seat, seat, meeting_point, hearth, storage
  - room y+7 8 cells round 1462,-43,525: NOT reached, light min -, 0 cells under 8; seat, seat, seat, seat, seat, seat
  - room y+7 6 cells round 1463,-43,533: NOT reached, light min -, 0 cells under 8; table, seat, seat, seat, seat, seat, seat, meeting_point
- defects:
  - **MED** door at 1466, -49, 524 is reached on one side but cannot be passed
  - **LOW** room at y+1 round 1463,-49,521 is dim: 10 of 10 cells under light 8 (min 2)
  - **HIGH** room of 16 cells at y+1 round 1463,-49,523 cannot be reached (has storage)
  - **HIGH** room of 171 cells at y+6 round 1463,-44,530 cannot be reached (has table, seat, seat, seat, seat, seat, seat, seat, seat, seat, seat, seat, seat, meeting_point, hearth, storage)
  - **MED** room of 8 cells at y+7 round 1462,-43,525 cannot be reached (has seat, seat, seat, seat, seat, seat)
  - **HIGH** room of 6 cells at y+7 round 1463,-43,533 cannot be reached (has table, seat, seat, seat, seat, seat, seat, meeting_point)
  - **HIGH** storage at 1466, -49, 522 cannot be walked to by a player
  - **HIGH** folk pathfinder finds no way from the street to the storage at 1466, -49, 522 (gets as far as 1469,-49,522)
  - **HIGH** storage at 1467, -44, 537 cannot be walked to by a player
  - **HIGH** folk pathfinder finds no way from the street to the storage at 1467, -44, 537 (gets as far as 1466,-48,536)
  - **LOW** 1 full-block step(s) on the way to rooms, beds or work with no stair (a player must jump): 1462,-49,520
- shots: northern_market_hall_main.jpg

### northern manor — PASS

`hearth:house/manor` northern, 16x17 facing south, tier 3, origin 1568, -50, 520, 3 beds. Street start 1576,-50,538.

- rooms reached 10/10; entrances 2/2; doors bad 0/6
- POIs walked to (model): bed 3/3, storage 4/4, seat 3/3, table 3/3, hearth 2/2, lectern 1/1, door 1/1, gate 1/1, viewpoint 2/2
- folk pathfinder street -> bed/job/store: 7/7
- player driven (real movement & collision): 19/19 paths completed, 0 stuck
- stairs: 4 flight steps, 0 unclimbable; jumps needed 0; tight head room 11
- light under roof: 195 cells, 16 under 8, 0 at 0
  - room y+0 21 cells round 1571,-50,526: reached, light min 5, 8 cells under 8; seat, viewpoint
  - room y+0 21 cells round 1582,-50,527: reached, light min 5, 8 cells under 8; seat, viewpoint
  - room y+1 5 cells round 1571,-49,526: reached, light min 11, 0 cells under 8; hearth, lectern, seat
  - room y+1 19 cells round 1575,-49,527: reached, light min 11, 0 cells under 8; table
  - room y+1 4 cells round 1576,-49,531: reached, light min 13, 0 cells under 8; door
  - room y+1 12 cells round 1580,-49,527: reached, light min 11, 0 cells under 8; hearth
  - room y+5 8 cells round 1571,-45,527: reached, light min 11, 0 cells under 8; bed
  - room y+5 14 cells round 1576,-45,527: reached, light min 11, 0 cells under 8; seat, table
  - room y+5 10 cells round 1581,-45,526: reached, light min 11, 0 cells under 8; bed, bed, storage, table
  - room y+9 52 cells round 1576,-41,526: reached, light min 11, 0 cells under 8; storage, storage, storage
- defects:
  - **LOW** room at y+0 round 1571,-50,526 is dim: 8 of 21 cells under light 8 (min 5)
  - **LOW** room at y+0 round 1582,-50,527 is dim: 8 of 21 cells under light 8 (min 5)
  - **LOW** head room under 1.9 at 11 cell(s) (head bumps): 1583,-50,528; 1583,-50,525; 1580,-50,530; 1580,-50,522; 1577,-50,530; 1577,-50,522
- shots: northern_manor_main.jpg, northern_manor_bed.jpg, northern_manor_stair.jpg

### northern keep — FAIL

`hearth:noble/keep` northern, 24x18 facing south, tier 3, origin 1680, -50, 520, 3 beds. Street start 1692,-50,539.

- rooms reached 8/13; entrances 1/1; doors bad 0/2
- POIs walked to (model): bed 3/3, job_site 4/4, storage 4/4, meeting_point 2/2, seat 4/4, table 4/4, hearth 1/1, altar 1/1, lectern 1/1, door 1/1, guard_post 3/6, gate 1/1, viewpoint 1/1, throne 1/1, strongbox 1/1
- folk pathfinder street -> bed/job/store: 5/11
- player driven (real movement & collision): 21/21 paths completed, 0 stuck
- stairs: 10 flight steps, 0 unclimbable; jumps needed 4; tight head room 0
- light under roof: 509 cells, 30 under 8, 0 at 0
  - room y+0 89 cells round 1692,-50,534: reached, light min 4, 9 cells under 8; guard_post, gate, meeting_point
  - room y+0 46 cells round 1689,-50,525: reached, light min 11, 0 cells under 8; hearth, storage, table, seat, seat, seat
  - room y+0 9 cells round 1689,-50,529: reached, light min 12, 0 cells under 8; door
  - room y+0 188 cells round 1699,-50,526: reached, light min 11, 0 cells under 8; job_site, storage, job_site, throne, seat, job_site, meeting_point, altar, table, lectern, job_site, storage, strongbox, table, guard_post, viewpoint
  - room y+0 6 cells round 1701,-50,539: reached, light min 7, 1 cells under 8
  - room y+5 17 cells round 1683,-45,535: NOT reached, light min -, 0 cells under 8; guard_post
  - room y+5 50 cells round 1688,-45,525: reached, light min 10, 0 cells under 8; bed, bed, bed, storage, table
  - room y+5 7 cells round 1692,-45,539: reached, light min 7, 2 cells under 8
  - room y+5 13 cells round 1692,-45,536: reached, light min 12, 0 cells under 8; guard_post
  - room y+5 4 cells round 1696,-45,535: NOT reached, light min -, 0 cells under 8; guard_post
  - room y+5 15 cells round 1701,-45,535: NOT reached, light min -, 0 cells under 8; guard_post
  - room y+9 4 cells round 1694,-41,522: NOT reached, light min -, 0 cells under 8
  - room y+11 35 cells round 1692,-39,536: NOT reached, light min -, 0 cells under 8
- defects:
  - **HIGH** room of 17 cells at y+5 round 1683,-45,535 cannot be reached (has guard_post)
  - **HIGH** room of 4 cells at y+5 round 1696,-45,535 cannot be reached (has guard_post)
  - **HIGH** room of 15 cells at y+5 round 1701,-45,535 cannot be reached (has guard_post)
  - **LOW** room of 4 cells at y+9 round 1694,-41,522 cannot be reached (nothing in it)
  - **MED** room of 35 cells at y+11 round 1692,-39,536 cannot be reached (nothing in it)
  - **HIGH** folk pathfinder finds no way from the street to the bed at 1682, -45, 524 (gets as far as 1682,-49,524)
  - **HIGH** folk pathfinder finds no way from the street to the bed at 1685, -45, 526 (gets as far as 1688,-48,526)
  - **HIGH** folk pathfinder finds no way from the street to the bed at 1693, -45, 524 (gets as far as 1695,-45,524)
  - **HIGH** folk pathfinder finds no way from the street to the job_site (steward) at 1698, -35, 527 (gets as far as 1698,-42,524)
  - **MED** folk pathfinder finds no way from the street to the storage at 1697, -35, 527 (gets as far as 1698,-42,524)
  - **MED** folk pathfinder finds no way from the street to the storage at 1683, -45, 523 (gets as far as 1684,-47,522)
  - **MED** 4 full-block step(s) on the way to rooms, beds or work with no stair (a player must jump): 1682,-49,522; 1687,-49,525; 1685,-49,534; 1691,-44,537
- shots: northern_keep_main.jpg, northern_keep_bed.jpg, northern_keep_stair.jpg

### northern mine — FAIL

`hearth:extract/mine` northern, 10x17 facing south, tier 2, origin 1792, -50, 520, 0 beds. Street start 1797,-50,538.

- rooms reached 0/1; entrances 1/1; doors bad 0/0
- POIs walked to (model): job_site 1/1, storage 2/2, work_face 0/4
- folk pathfinder street -> bed/job/store: 3/7
- player driven (real movement & collision): 5/5 paths completed, 0 stuck
- stairs: 4 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 0 cells, 0 under 8, 0 at 0
  - room y-3 29 cells round 1798,-53,525: NOT reached, light min -, 0 cells under 8; work_face, work_face, work_face, work_face
- defects:
  - **HIGH** room of 29 cells at y-3 round 1798,-53,525 cannot be reached (has work_face, work_face, work_face, work_face)
  - **HIGH** work_face (miner) at 1794, -53, 528 cannot be walked to by a player
  - **HIGH** folk pathfinder finds no way from the street to the work_face (miner) at 1794, -53, 528 (gets as far as 1796,-52,530)
  - **HIGH** work_face (miner) at 1797, -53, 525 cannot be walked to by a player
  - **HIGH** folk pathfinder finds no way from the street to the work_face (miner) at 1797, -53, 525 (gets as far as 1798,-52,530)
  - **HIGH** work_face (miner) at 1800, -53, 522 cannot be walked to by a player
  - **HIGH** folk pathfinder finds no way from the street to the work_face (miner) at 1800, -53, 522 (gets as far as 1798,-52,530)
  - **HIGH** work_face (miner) at 1797, -53, 521 cannot be walked to by a player
  - **HIGH** folk pathfinder finds no way from the street to the work_face (miner) at 1797, -53, 521 (gets as far as 1798,-52,530)

### desert cottage — PASS

`hearth:house/cottage` desert, 9x7 facing south, tier 2, origin 0, -50, 680, 2 beds. Street start 4,-50,688.

- rooms reached 1/2; entrances 1/1; doors bad 0/1
- POIs walked to (model): bed 2/2, storage 1/1, seat 0/1, table 1/1, door 1/1, viewpoint 0/1
- folk pathfinder street -> bed/job/store: 3/3
- player driven (real movement & collision): 4/4 paths completed, 0 stuck
- stairs: 0 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 24 cells, 0 under 8, 0 at 0
  - room y+0 18 cells round 5,-50,683: reached, light min 11, 0 cells under 8; bed, bed, storage, table
  - room y+6 4 cells round 8,-44,681: NOT reached, light min -, 0 cells under 8
- defects:
  - **LOW** room of 4 cells at y+6 round 8,-44,681 cannot be reached (nothing in it)
- shots: desert_cottage_main.jpg, desert_cottage_bed.jpg, desert_cottage_stair.jpg

### desert farmhouse — PASS

`hearth:house/farmhouse` desert, 14x13 facing south, tier 2, origin 112, -50, 680, 2 beds. Street start 119,-50,694.

- rooms reached 3/4; entrances 4/4; doors bad 0/3
- POIs walked to (model): bed 2/2, storage 2/2, seat 0/1, table 1/1, hearth 1/1, door 3/3, gate 1/1, viewpoint 0/1
- folk pathfinder street -> bed/job/store: 4/4
- player driven (real movement & collision): 8/8 paths completed, 0 stuck
- stairs: 4 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 91 cells, 5 under 8, 0 at 0
  - room y+0 22 cells round 119,-50,683: reached, light min 11, 0 cells under 8; hearth, storage
  - room y+0 14 cells round 119,-50,688: reached, light min 11, 0 cells under 8; door, door, door
  - room y+4 21 cells round 119,-46,684: reached, light min 11, 0 cells under 8; bed, bed, table
  - room y+10 4 cells round 124,-40,682: NOT reached, light min -, 0 cells under 8
- defects:
  - **LOW** room of 4 cells at y+10 round 124,-40,682 cannot be reached (nothing in it)
- shots: desert_farmhouse_main.jpg, desert_farmhouse_bed.jpg, desert_farmhouse_stair.jpg

### desert townhouse — FAIL

`hearth:house/townhouse` desert, 5x9 facing south, tier 2, origin 224, -50, 680, 3 beds. Street start 226,-50,690.

- rooms reached 6/7; entrances 1/1; doors bad 0/2
- POIs walked to (model): bed 3/3, job_site 2/2, storage 3/3, table 2/2, door 1/1
- folk pathfinder street -> bed/job/store: 8/8
- player driven (real movement & collision): 17/17 paths completed, 0 stuck
- stairs: 13 flight steps, 0 unclimbable; jumps needed 1; tight head room 0
- light under roof: 73 cells, 0 under 8, 0 at 0
  - room y+0 5 cells round 227,-50,690: reached, light min 10, 0 cells under 8; door
  - room y+0 13 cells round 226,-50,684: reached, light min 12, 0 cells under 8; job_site, storage, job_site
  - room y+1 4 cells round 228,-49,683: reached, light min 12, 0 cells under 8; job_site, storage, job_site
  - room y+5 4 cells round 226,-45,689: NOT reached, light min -, 0 cells under 8; bed
  - room y+5 13 cells round 227,-45,685: reached, light min 11, 0 cells under 8; bed, storage, table
  - room y+9 14 cells round 227,-41,685: reached, light min 10, 0 cells under 8; bed, bed, storage, table
  - room y+13 6 cells round 226,-37,683: reached, light min 10, 0 cells under 8
- defects:
  - **HIGH** room of 4 cells at y+5 round 226,-45,689 cannot be reached (has bed)
  - **LOW** 1 full-block step(s) on the way to rooms, beds or work with no stair (a player must jump): 227,-49,684
- shots: desert_townhouse_main.jpg, desert_townhouse_bed.jpg, desert_townhouse_stair.jpg

### desert tenement — FAIL

`hearth:house/tenement` desert, 8x13 facing south, tier 2, origin 336, -50, 680, 9 beds. Street start 340,-50,694.

- rooms reached 6/6; entrances 1/1; doors bad 1/13
- POIs walked to (model): bed 9/9, storage 7/7, table 6/6, door 1/1, viewpoint 2/4
- folk pathfinder street -> bed/job/store: 13/16
- player driven (real movement & collision): 30/30 paths completed, 0 stuck
- stairs: 16 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 195 cells, 2 under 8, 0 at 0
  - room y+0 4 cells round 338,-50,685: reached, light min 12, 0 cells under 8
  - room y+0 28 cells round 340,-50,688: reached, light min 11, 0 cells under 8; bed, bed, storage, storage, table, storage
  - room y+0 4 cells round 341,-50,693: reached, light min 11, 0 cells under 8; door
  - room y+4 33 cells round 341,-46,687: reached, light min 11, 0 cells under 8; bed, bed, bed, storage, table, storage
  - room y+8 23 cells round 340,-42,688: reached, light min 11, 0 cells under 8; bed, bed, table, storage, table
  - room y+12 29 cells round 340,-38,688: reached, light min 11, 0 cells under 8; bed, bed, table, storage
- defects:
  - **HIGH** door at 338, -38, 691 leads into a wall or a drop on its south side (-1:mud_brick_stairs / 0:jungle_stairs / 1:air / 2:spruce_log)
  - **HIGH** folk pathfinder finds no way from the street to the bed at 339, -38, 690 (gets as far as 339,-42,689)
  - **HIGH** folk pathfinder finds no way from the street to the bed at 342, -38, 689 (gets as far as 342,-41,687)
  - **MED** folk pathfinder finds no way from the street to the storage at 341, -38, 682 (gets as far as 341,-38,685)
- shots: desert_tenement_main.jpg, desert_tenement_bed.jpg, desert_tenement_stair.jpg

### desert merchant_house — FAIL

`hearth:house/merchant_house` desert, 8x12 facing south, tier 2, origin 448, -50, 680, 5 beds. Street start 452,-50,693.

- rooms reached 8/8; entrances 1/1; doors bad 0/1
- POIs walked to (model): bed 5/5, job_site 1/1, storage 10/10, market_stall 1/1, counter 1/1, seat 3/3, table 4/4, hearth 1/1, door 1/1, strongbox 1/1
- folk pathfinder street -> bed/job/store: 13/17
- player driven (real movement & collision): 31/31 paths completed, 0 stuck
- stairs: 13 flight steps, 0 unclimbable; jumps needed 4; tight head room 0
- light under roof: 183 cells, 0 under 8, 0 at 0
  - room y+0 29 cells round 452,-50,686: reached, light min 10, 0 cells under 8; counter, market_stall, job_site, strongbox, seat, table, storage, storage, storage
  - room y+0 4 cells round 453,-50,692: reached, light min 11, 0 cells under 8; door
  - room y+1 6 cells round 451,-49,689: reached, light min 10, 0 cells under 8; counter, market_stall, job_site, storage, storage
  - room y+1 4 cells round 453,-49,683: reached, light min 10, 0 cells under 8; strongbox, seat, table, storage
  - room y+1 4 cells round 455,-49,687: reached, light min 11, 0 cells under 8; storage
  - room y+5 32 cells round 452,-45,686: reached, light min 11, 0 cells under 8; hearth, storage, table, seat, seat
  - room y+9 31 cells round 452,-41,686: reached, light min 10, 0 cells under 8; bed, bed, bed, bed, storage, storage, table
  - room y+13 31 cells round 452,-37,687: reached, light min 10, 0 cells under 8; bed, storage, table, storage, storage, storage
- defects:
  - **HIGH** folk pathfinder finds no way from the street to the bed at 454, -41, 689 (gets as far as 454,-45,689)
  - **HIGH** folk pathfinder finds no way from the street to the bed at 450, -41, 689 (gets as far as 449,-41,688)
  - **MED** folk pathfinder finds no way from the street to the storage at 453, -41, 688 (gets as far as 452,-41,686)
  - **MED** folk pathfinder finds no way from the street to the storage at 452, -37, 688 (gets as far as 452,-41,686)
  - **MED** 4 full-block step(s) on the way to rooms, beds or work with no stair (a player must jump): 453,-40,689; 450,-49,688; 452,-49,682; 454,-49,688
- shots: desert_merchant_house_main.jpg, desert_merchant_house_bed.jpg, desert_merchant_house_stair.jpg

### desert inn — PASS

`hearth:commerce/inn` desert, 12x14 facing south, tier 2, origin 560, -50, 680, 4 beds. Street start 566,-50,695.

- rooms reached 5/7; entrances 1/1; doors bad 0/4
- POIs walked to (model): bed 4/4, job_site 1/1, storage 3/3, market_stall 1/1, counter 1/1, seat 3/4, table 3/3, hearth 1/1, door 1/1, viewpoint 0/2
- folk pathfinder street -> bed/job/store: 9/9
- player driven (real movement & collision): 15/15 paths completed, 0 stuck
- stairs: 5 flight steps, 0 unclimbable; jumps needed 2; tight head room 0
- light under roof: 107 cells, 4 under 8, 0 at 0
  - room y+0 20 cells round 564,-50,688: reached, light min 11, 0 cells under 8; job_site, counter, market_stall, seat, hearth, table, seat, seat, table
  - room y+0 4 cells round 565,-50,683: NOT reached, light min -, 0 cells under 8
  - room y+0 33 cells round 569,-50,687: reached, light min 6, 4 cells under 8
  - room y+5 9 cells round 566,-45,686: reached, light min 11, 0 cells under 8; storage
  - room y+5 6 cells round 564,-45,689: reached, light min 12, 0 cells under 8; bed, bed, storage
  - room y+5 12 cells round 569,-45,689: reached, light min 11, 0 cells under 8; bed, bed, storage, table
  - room y+9 6 cells round 563,-41,690: NOT reached, light min -, 0 cells under 8; seat, viewpoint
- defects:
  - **LOW** room of 4 cells at y+0 round 565,-50,683 cannot be reached (nothing in it)
  - **LOW** room of 6 cells at y+9 round 563,-41,690 cannot be reached (has seat, viewpoint)
  - **LOW** 2 full-block step(s) on the way to rooms, beds or work with no stair (a player must jump): 561,-49,690; 565,-44,689
- shots: desert_inn_main.jpg, desert_inn_bed.jpg

### desert tavern — PASS

`hearth:commerce/tavern` desert, 11x14 facing south, tier 2, origin 672, -50, 680, 0 beds. Street start 677,-50,695.

- rooms reached 5/6; entrances 1/1; doors bad 0/1
- POIs walked to (model): job_site 2/2, storage 1/1, market_stall 1/1, counter 1/1, seat 18/19, table 3/3, hearth 1/1, door 1/1, viewpoint 0/1, gaming_table 1/1, stage 1/1
- folk pathfinder street -> bed/job/store: 4/4
- player driven (real movement & collision): 10/10 paths completed, 0 stuck
- stairs: 4 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 85 cells, 10 under 8, 0 at 0
  - room y-4 10 cells round 677,-54,685: reached, light min 11, 0 cells under 8; storage
  - room y+0 27 cells round 678,-50,688: reached, light min 11, 0 cells under 8; job_site, counter, market_stall, seat, hearth, stage, seat, gaming_table, seat, seat, seat, seat, seat, seat, seat, seat, seat, seat, table
  - room y+0 8 cells round 679,-50,681: reached, light min 4, 7 cells under 8; table, seat, seat
  - room y+0 4 cells round 679,-50,693: reached, light min 12, 0 cells under 8; door, seat, seat
  - room y+1 5 cells round 674,-49,689: reached, light min 11, 0 cells under 8; stage, job_site, seat, seat
  - room y+5 6 cells round 675,-45,690: NOT reached, light min -, 0 cells under 8; seat, viewpoint
- defects:
  - **LOW** room at y+0 round 679,-50,681 is dim: 7 of 8 cells under light 8 (min 4)
  - **LOW** room of 6 cells at y+5 round 675,-45,690 cannot be reached (has seat, viewpoint)
- shots: desert_tavern_main.jpg

### desert smithy — WARN

`hearth:work/smithy` desert, 10x11 facing south, tier 2, origin 784, -50, 680, 0 beds. Street start 789,-50,692.

- rooms reached 1/3; entrances 1/1; doors bad 0/0
- POIs walked to (model): job_site 4/4, storage 2/2, door 1/1
- folk pathfinder street -> bed/job/store: 6/6
- player driven (real movement & collision): 7/7 paths completed, 0 stuck
- stairs: 0 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 36 cells, 0 under 8, 0 at 0
  - room y+0 30 cells round 789,-50,686: reached, light min 10, 0 cells under 8; door, job_site, job_site, job_site, storage, storage, job_site
  - room y+3 8 cells round 788,-47,683: NOT reached, light min -, 0 cells under 8
  - room y+6 4 cells round 787,-44,686: NOT reached, light min -, 0 cells under 8
- defects:
  - **MED** room of 8 cells at y+3 round 788,-47,683 cannot be reached (nothing in it)
  - **LOW** room of 4 cells at y+6 round 787,-44,686 cannot be reached (nothing in it)
- shots: desert_smithy_main.jpg

### desert bakery — FAIL

`hearth:work/bakery` desert, 10x9 facing south, tier 2, origin 896, -50, 680, 0 beds. Street start 901,-50,690.

- rooms reached 1/2; entrances 3/3; doors bad 1/3
- POIs walked to (model): job_site 3/3, storage 1/1, counter 1/1, seat 1/1, table 1/1, door 2/2
- folk pathfinder street -> bed/job/store: 5/5
- player driven (real movement & collision): 6/6 paths completed, 0 stuck
- stairs: 0 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 18 cells, 0 under 8, 0 at 0
  - room y+0 9 cells round 900,-50,684: reached, light min 11, 0 cells under 8; job_site, job_site, counter, storage, table
  - room y+1 4 cells round 898,-49,685: NOT reached, light min -, 0 cells under 8; job_site, counter, storage, table
- defects:
  - **HIGH** door at 898, -45, 683 leads into a wall or a drop on its south side (-1:mud_bricks / 0:air / 1:palm_log / 2:white_wool_slab)
  - **HIGH** room of 4 cells at y+1 round 898,-49,685 cannot be reached (has job_site, counter, storage, table)
- shots: desert_bakery_main.jpg

### desert windmill — FAIL

`hearth:work/windmill` desert, 15x13 facing south, tier 2, origin 1008, -50, 680, 0 beds. Street start 1015,-50,694.

- rooms reached 3/6; entrances 2/2; doors bad 0/1
- POIs walked to (model): job_site 2/2, storage 1/1, door 1/1
- folk pathfinder street -> bed/job/store: 3/3
- player driven (real movement & collision): 6/6 paths completed, 0 stuck
- stairs: 0 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 60 cells, 13 under 8, 0 at 0
  - room y+0 7 cells round 1012,-50,689: reached, light min 4, 5 cells under 8
  - room y+0 20 cells round 1016,-50,686: reached, light min 11, 0 cells under 8; job_site, job_site
  - room y+0 8 cells round 1020,-50,689: reached, light min 10, 0 cells under 8
  - room y+1 24 cells round 1016,-49,686: NOT reached, light min -, 0 cells under 8; job_site
  - room y+5 14 cells round 1016,-45,685: NOT reached, light min -, 0 cells under 8
  - room y+9 18 cells round 1016,-41,685: NOT reached, light min -, 0 cells under 8
- defects:
  - **LOW** room at y+0 round 1012,-50,689 is dim: 5 of 7 cells under light 8 (min 4)
  - **HIGH** room of 24 cells at y+1 round 1016,-49,686 cannot be reached (has job_site)
  - **MED** room of 14 cells at y+5 round 1016,-45,685 cannot be reached (nothing in it)
  - **MED** room of 18 cells at y+9 round 1016,-41,685 cannot be reached (nothing in it)
- shots: desert_windmill_main.jpg

### desert chapel — PASS

`hearth:civic/chapel` desert, 11x19 facing south, tier 2, origin 1120, -50, 680, 0 beds. Street start 1125,-50,700.

- rooms reached 1/1; entrances 2/2; doors bad 0/1
- POIs walked to (model): job_site 1/1, seat 10/10, altar 1/1, door 1/1
- folk pathfinder street -> bed/job/store: 1/1
- player driven (real movement & collision): 2/2 paths completed, 0 stuck
- stairs: 0 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 79 cells, 2 under 8, 0 at 0
  - room y+1 47 cells round 1125,-49,689: reached, light min 11, 0 cells under 8; seat, seat, seat, seat, seat, seat, seat, seat, seat, seat, altar, job_site
- shots: desert_chapel_main.jpg

### desert town_hall — WARN

`hearth:civic/town_hall` desert, 13x19 facing south, tier 3, origin 1232, -50, 680, 0 beds. Street start 1238,-50,700.

- rooms reached 4/8; entrances 2/2; doors bad 0/2
- POIs walked to (model): storage 2/2, notice_board 1/1, meeting_point 1/1, seat 12/13, table 1/1, lectern 1/1, door 1/1, viewpoint 1/3
- folk pathfinder street -> bed/job/store: 1/2
- player driven (real movement & collision): 7/7 paths completed, 0 stuck
- stairs: 5 flight steps, 0 unclimbable; jumps needed 2; tight head room 0
- light under roof: 172 cells, 0 under 8, 0 at 0
  - room y+1 51 cells round 1238,-49,688: reached, light min 11, 0 cells under 8; lectern, storage, seat, seat, seat, seat
  - room y+1 14 cells round 1237,-49,695: reached, light min 9, 0 cells under 8; door, notice_board
  - room y+6 37 cells round 1236,-44,690: reached, light min 11, 0 cells under 8; table, seat, seat, seat, seat, seat, meeting_point, storage
  - room y+6 18 cells round 1241,-44,688: reached, light min 11, 0 cells under 8; seat, seat, seat, seat
  - room y+6 9 cells round 1242,-44,694: NOT reached, light min -, 0 cells under 8
  - room y+12 6 cells round 1236,-38,694: NOT reached, light min -, 0 cells under 8; seat, viewpoint
  - room y+12 9 cells round 1242,-38,694: NOT reached, light min -, 0 cells under 8
  - room y+18 13 cells round 1242,-32,694: NOT reached, light min -, 0 cells under 8; viewpoint
- defects:
  - **MED** room of 9 cells at y+6 round 1242,-44,694 cannot be reached (nothing in it)
  - **LOW** room of 6 cells at y+12 round 1236,-38,694 cannot be reached (has seat, viewpoint)
  - **MED** room of 9 cells at y+12 round 1242,-38,694 cannot be reached (nothing in it)
  - **MED** room of 13 cells at y+18 round 1242,-32,694 cannot be reached (has viewpoint)
  - **MED** folk pathfinder finds no way from the street to the storage at 1235, -44, 685 (gets as far as 1238,-44,685)
  - **LOW** 2 full-block step(s) on the way to rooms, beds or work with no stair (a player must jump): 1242,-48,685; 1239,-44,686
- shots: desert_town_hall_main.jpg

### desert library — WARN

`hearth:civic/library` desert, 9x15 facing south, tier 2, origin 1344, -50, 680, 0 beds. Street start 1348,-50,696.

- rooms reached 2/3; entrances 1/1; doors bad 0/1
- POIs walked to (model): job_site 2/2, storage 1/1, seat 8/8, table 4/4, lectern 2/2, door 1/1, viewpoint 1/1
- folk pathfinder street -> bed/job/store: 3/3
- player driven (real movement & collision): 6/6 paths completed, 0 stuck
- stairs: 4 flight steps, 0 unclimbable; jumps needed 1; tight head room 0
- light under roof: 88 cells, 0 under 8, 0 at 0
  - room y+1 44 cells round 1349,-49,687: reached, light min 10, 0 cells under 8; job_site, lectern, job_site, lectern, table, seat, seat, table, seat, seat, table, seat, seat, table, seat, seat
  - room y+5 19 cells round 1349,-45,684: reached, light min 10, 0 cells under 8; viewpoint, storage
  - room y+10 9 cells round 1349,-40,687: NOT reached, light min -, 0 cells under 8
- defects:
  - **MED** room of 9 cells at y+10 round 1349,-40,687 cannot be reached (nothing in it)
  - **LOW** 1 full-block step(s) on the way to rooms, beds or work with no stair (a player must jump): 1348,-49,693
- shots: desert_library_main.jpg, desert_library_stair.jpg

### desert market_hall — FAIL

`hearth:commerce/market_hall` desert, 15x14 facing south, tier 3, origin 1456, -50, 680, 0 beds. Street start 1463,-50,695.

- rooms reached 1/3; entrances 2/2; doors bad 1/1
- POIs walked to (model): job_site 4/4, storage 0/2, market_stall 4/4, counter 4/4, meeting_point 1/2, seat 0/4, table 0/1, hearth 0/1, door 2/2, viewpoint 0/1
- folk pathfinder street -> bed/job/store: 8/10
- player driven (real movement & collision): 9/9 paths completed, 0 stuck
- stairs: 0 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 120 cells, 20 under 8, 0 at 0
  - room y+0 101 cells round 1464,-50,689: reached, light min 5, 13 cells under 8; door, job_site, counter, market_stall, job_site, counter, market_stall, job_site, counter, market_stall, job_site, counter, market_stall, meeting_point, door
  - room y+0 18 cells round 1463,-50,683: NOT reached, light min -, 0 cells under 8; storage
  - room y+5 92 cells round 1464,-45,687: NOT reached, light min -, 0 cells under 8; table, seat, seat, seat, seat, meeting_point, hearth, storage
- defects:
  - **MED** door at 1467, -50, 684 is reached on one side but cannot be passed
  - **HIGH** room of 18 cells at y+0 round 1463,-50,683 cannot be reached (has storage)
  - **HIGH** room of 92 cells at y+5 round 1464,-45,687 cannot be reached (has table, seat, seat, seat, seat, meeting_point, hearth, storage)
  - **HIGH** storage at 1467, -50, 682 cannot be walked to by a player
  - **HIGH** folk pathfinder finds no way from the street to the storage at 1467, -50, 682 (gets as far as 1467,-50,680)
  - **HIGH** storage at 1467, -45, 691 cannot be walked to by a player
  - **HIGH** folk pathfinder finds no way from the street to the storage at 1467, -45, 691 (gets as far as 1467,-49,691)
- shots: desert_market_hall_main.jpg

### desert manor — FAIL

`hearth:house/manor` desert, 24x24 facing south, tier 3, origin 1568, -50, 680, 8 beds. Street start 1580,-50,705.

- rooms reached 13/14; entrances 2/2; doors bad 0/11
- POIs walked to (model): bed 8/8, storage 7/7, meeting_point 1/1, seat 11/12, table 11/11, hearth 1/1, lectern 1/1, door 1/1, gate 1/1, viewpoint 1/2
- folk pathfinder street -> bed/job/store: 8/15
- player driven (real movement & collision): 30/30 paths completed, 0 stuck
- stairs: 5 flight steps, 0 unclimbable; jumps needed 2; tight head room 0
- light under roof: 295 cells, 6 under 8, 0 at 0
  - room y+1 22 cells round 1574,-49,689: reached, light min 11, 0 cells under 8; hearth, lectern, seat, table, storage, table, seat
  - room y+1 9 cells round 1573,-49,695: reached, light min 11, 0 cells under 8; storage, storage, storage
  - room y+1 36 cells round 1580,-49,689: reached, light min 11, 0 cells under 8; table
  - room y+1 6 cells round 1580,-49,694: reached, light min 11, 0 cells under 8; door
  - room y+1 19 cells round 1587,-49,689: reached, light min 11, 0 cells under 8; table, seat, seat, seat, seat, seat, seat, storage
  - room y+1 10 cells round 1587,-49,695: reached, light min 11, 0 cells under 8; seat, table, storage, table, seat
  - room y+2 4 cells round 1574,-48,686: NOT reached, light min -, 0 cells under 8; table, storage
  - room y+2 4 cells round 1575,-48,695: reached, light min 10, 0 cells under 8; storage, storage, storage
  - room y+2 5 cells round 1587,-48,689: reached, light min 13, 0 cells under 8; table, seat, seat, seat, seat, seat, seat
  - room y+6 30 cells round 1573,-44,689: reached, light min 11, 0 cells under 8; bed, bed, table
  - room y+6 15 cells round 1573,-44,695: reached, light min 10, 0 cells under 8; bed, bed, table
  - room y+6 29 cells round 1580,-44,689: reached, light min 11, 0 cells under 8; seat, table, storage
  - room y+6 30 cells round 1586,-44,689: reached, light min 11, 0 cells under 8; bed, bed, table
  - room y+6 14 cells round 1587,-44,696: reached, light min 10, 0 cells under 8; bed, bed, table
- defects:
  - **HIGH** room of 4 cells at y+2 round 1574,-48,686 cannot be reached (has table, storage)
  - **HIGH** folk pathfinder finds no way from the street to the bed at 1585, -44, 695 (gets as far as 1587,-47,693)
  - **HIGH** folk pathfinder finds no way from the street to the bed at 1574, -44, 697 (gets as far as 1577,-47,700)
  - **HIGH** folk pathfinder finds no way from the street to the bed at 1571, -44, 689 (gets as far as 1573,-48,689)
  - **HIGH** folk pathfinder finds no way from the street to the bed at 1588, -44, 697 (gets as far as 1590,-48,697)
  - **HIGH** folk pathfinder finds no way from the street to the bed at 1588, -44, 688 (gets as far as 1587,-48,688)
  - **HIGH** folk pathfinder finds no way from the street to the bed at 1572, -44, 685 (gets as far as 1574,-44,685)
  - **HIGH** folk pathfinder finds no way from the street to the bed at 1571, -44, 694 (gets as far as 1571,-48,694)
  - **LOW** 2 full-block step(s) on the way to rooms, beds or work with no stair (a player must jump): 1574,-48,694; 1585,-48,689
- shots: desert_manor_main.jpg, desert_manor_bed.jpg, desert_manor_stair.jpg

### desert keep — FAIL

`hearth:noble/keep` desert, 17x17 facing south, tier 3, origin 1680, -50, 680, 0 beds. Street start 1688,-50,698.

- rooms reached 4/4; entrances 1/1; doors bad 0/1
- POIs walked to (model): job_site 4/4, storage 2/2, meeting_point 1/1, seat 1/1, table 4/4, altar 1/1, lectern 1/1, guard_post 4/4, gate 1/1, viewpoint 1/1, throne 1/1, strongbox 1/1
- folk pathfinder street -> bed/job/store: 4/6
- player driven (real movement & collision): 11/11 paths completed, 0 stuck
- stairs: 5 flight steps, 0 unclimbable; jumps needed 2; tight head room 0
- light under roof: 334 cells, 40 under 8, 0 at 0
  - room y+0 194 cells round 1685,-50,686: reached, light min 11, 0 cells under 8; job_site, storage, job_site, throne, seat, job_site, meeting_point, altar, table, lectern, job_site, storage, strongbox, table, table, table
  - room y+0 36 cells round 1688,-50,694: reached, light min 8, 0 cells under 8; guard_post, gate
  - room y+1 4 cells round 1686,-49,682: reached, light min 12, 0 cells under 8; job_site, job_site
  - room y+5 13 cells round 1688,-45,695: reached, light min 12, 0 cells under 8; guard_post
- defects:
  - **HIGH** folk pathfinder finds no way from the street to the job_site (steward) at 1685, -35, 687 (gets as far as 1684,-44,685)
  - **MED** folk pathfinder finds no way from the street to the storage at 1684, -35, 687 (gets as far as 1684,-44,684)
  - **LOW** 2 full-block step(s) on the way to rooms, beds or work with no stair (a player must jump): 1685,-49,682; 1693,-49,693
- shots: desert_keep_main.jpg

### desert mine — WARN

`hearth:extract/mine` desert, 7x36 facing south, tier 2, origin 1792, -50, 680, 0 beds. Street start 1795,-50,717.

- rooms reached 1/1; entrances 1/1; doors bad 0/0
- POIs walked to (model): job_site 1/1, storage 2/2, work_face 10/10
- folk pathfinder street -> bed/job/store: 3/7
- player driven (real movement & collision): 8/8 paths completed, 0 stuck
- stairs: 0 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 74 cells, 0 under 8, 0 at 0
  - room y-2 74 cells round 1796,-52,694: reached, light min 11, 0 cells under 8; work_face, work_face, work_face, work_face, work_face, work_face, work_face, work_face, work_face, work_face
- defects:
  - **MED** folk pathfinder finds no way from the street to the work_face (miner) at 1797, -52, 706 (gets as far as 1796,-51,709)
  - **MED** folk pathfinder finds no way from the street to the work_face (miner) at 1795, -52, 703 (gets as far as 1794,-51,709)
  - **MED** folk pathfinder finds no way from the street to the work_face (miner) at 1793, -52, 700 (gets as far as 1794,-51,709)
  - **MED** folk pathfinder finds no way from the street to the work_face (miner) at 1795, -52, 697 (gets as far as 1795,-44,705)
- shots: desert_mine_main.jpg

### grown builders_yard #5 — WARN

`hearth:work/builders_yard` temperate, 15x14 facing west, tier 0, origin -1353, 76, -1354, 0 beds. Street start -1355,76,-1347.

- rooms reached 2/3; entrances 2/2; doors bad 0/1
- POIs walked to (model): job_site 5/5, storage 4/4, lectern 1/1, door 1/1, gate 1/1
- folk pathfinder street -> bed/job/store: 8/9
- player driven (real movement & collision): 11/11 paths completed, 0 stuck
- stairs: 0 flight steps, 0 unclimbable; jumps needed 1; tight head room 0
- light under roof: 67 cells, 0 under 8, 0 at 0
  - room y+0 46 cells round -1342,76,-1346: reached, light min 10, 0 cells under 8; storage, job_site, job_site, job_site, job_site, storage
  - room y+0 8 cells round -1342,76,-1352: reached, light min 11, 0 cells under 8; storage, lectern
  - room y+4 10 cells round -1342,80,-1352: NOT reached, light min -, 0 cells under 8
- defects:
  - **MED** room of 10 cells at y+4 round -1342,80,-1352 cannot be reached (nothing in it)
  - **MED** folk pathfinder finds no way from the street to the storage at -1341, 76, -1353 (gets as far as -1342,76,-1355)
  - **LOW** 1 full-block step(s) on the way to rooms, beds or work with no stair (a player must jump): -1353,77,-1350
- shots: grown_builders_yard_#5_main.jpg

### grown croft #9 — FAIL

`hearth:house/croft` temperate, 13x12 facing east, tier 0, origin -1369, 76, -1397, 3 beds. Street start -1355,76,-1391.

- rooms reached 4/5; entrances 2/2; doors bad 1/4
- POIs walked to (model): bed 3/3, seat 3/3, table 1/1, hearth 1/1, door 1/1
- folk pathfinder street -> bed/job/store: 3/3
- player driven (real movement & collision): 7/7 paths completed, 0 stuck
- stairs: 0 flight steps, 0 unclimbable; jumps needed 2; tight head room 0
- light under roof: 69 cells, 1 under 8, 0 at 0
  - room y+0 15 cells round -1366,76,-1389: reached, light min 11, 0 cells under 8
  - room y+0 18 cells round -1360,76,-1388: reached, light min 10, 0 cells under 8; bed, bed, bed, hearth, seat, table, seat, seat
  - room y+0 4 cells round -1358,76,-1393: reached, light min 7, 1 cells under 8
  - room y+1 7 cells round -1357,77,-1389: reached, light min 8, 0 cells under 8
  - room y+4 9 cells round -1365,80,-1389: NOT reached, light min -, 0 cells under 8
- defects:
  - **HIGH** doorway at -1364, 76, -1389 has no head room to pass (open door)
  - **MED** room of 9 cells at y+4 round -1365,80,-1389 cannot be reached (nothing in it)
  - **LOW** 2 full-block step(s) on the way to rooms, beds or work with no stair (a player must jump): -1366,76,-1392; -1357,77,-1391
- shots: grown_croft_#9_main.jpg, grown_croft_#9_bed.jpg, grown_croft_#9_stair.jpg

### grown storehouse #19 — WARN

`hearth:storage/storehouse` temperate, 7x7 facing west, tier 1, origin -1328, 76, -1338, 0 beds. Street start -1330,76,-1335.

- rooms reached 2/3; entrances 2/2; doors bad 0/2
- POIs walked to (model): storage 5/5, door 1/1
- folk pathfinder street -> bed/job/store: 5/5
- player driven (real movement & collision): 7/7 paths completed, 0 stuck
- stairs: 0 flight steps, 0 unclimbable; jumps needed 1; tight head room 0
- light under roof: 28 cells, 0 under 8, 0 at 0
  - room y+0 11 cells round -1324,76,-1335: reached, light min 11, 0 cells under 8; storage, storage, storage, storage, storage
  - room y+1 5 cells round -1323,77,-1335: reached, light min 10, 0 cells under 8; storage, storage, storage
  - room y+4 14 cells round -1324,80,-1335: NOT reached, light min -, 0 cells under 8
- defects:
  - **MED** room of 14 cells at y+4 round -1324,80,-1335 cannot be reached (nothing in it)
  - **LOW** 1 full-block step(s) on the way to rooms, beds or work with no stair (a player must jump): -1323,77,-1335
- shots: grown_storehouse_#19_main.jpg

### grown fishery #24 — WARN

`hearth:work/fishery` temperate, 7x7 facing east, tier 0, origin -1313, 74, -1374, 0 beds. Street start -1305,74,-1371.

- rooms reached 1/2; entrances 3/3; doors bad 0/2
- POIs walked to (model): job_site 2/2, storage 3/3, hearth 1/1, door 2/2
- folk pathfinder street -> bed/job/store: 5/5
- player driven (real movement & collision): 6/6 paths completed, 0 stuck
- stairs: 0 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 29 cells, 0 under 8, 0 at 0
  - room y+0 7 cells round -1309,74,-1370: reached, light min 11, 0 cells under 8; hearth, job_site, job_site, storage, storage, storage
  - room y+4 11 cells round -1309,78,-1371: NOT reached, light min -, 0 cells under 8
- defects:
  - **MED** room of 11 cells at y+4 round -1309,78,-1371 cannot be reached (nothing in it)
- shots: grown_fishery_#24_main.jpg

### grown carpentry #26 — PASS

`hearth:work/carpentry` temperate, 9x10 facing south, tier 0, origin -1322, 76, -1366, 0 beds. Street start -1318,76,-1355.

- rooms reached 4/5; entrances 3/3; doors bad 0/3
- POIs walked to (model): job_site 3/3, storage 5/5, door 2/2
- folk pathfinder street -> bed/job/store: 8/8
- player driven (real movement & collision): 12/12 paths completed, 0 stuck
- stairs: 0 flight steps, 0 unclimbable; jumps needed 2; tight head room 0
- light under roof: 57 cells, 0 under 8, 0 at 0
  - room y+0 13 cells round -1319,76,-1361: reached, light min 11, 0 cells under 8; job_site, job_site, job_site, storage, storage, storage, storage
  - room y+0 13 cells round -1316,76,-1358: reached, light min 9, 0 cells under 8; door, storage, door
  - room y+1 4 cells round -1321,77,-1360: reached, light min 11, 0 cells under 8; job_site, storage
  - room y+1 9 cells round -1315,77,-1363: reached, light min 11, 0 cells under 8; storage, door
  - room y+5 4 cells round -1319,81,-1362: NOT reached, light min -, 0 cells under 8
- defects:
  - **LOW** room of 4 cells at y+5 round -1319,81,-1362 cannot be reached (nothing in it)
  - **LOW** 2 full-block step(s) on the way to rooms, beds or work with no stair (a player must jump): -1321,77,-1361; -1316,77,-1361
- shots: grown_carpentry_#26_main.jpg

### grown market_stall #30 — FAIL

`hearth:commerce/market_stall` temperate, 4x4 facing north, tier 1, origin -1362, 76, -1316, 0 beds. Street start -1360,76,-1318.

- rooms reached 1/1; entrances 1/1; doors bad 0/0
- POIs walked to (model): job_site 1/1, market_stall 1/1, counter 1/1
- folk pathfinder street -> bed/job/store: 0/2
- player driven (real movement & collision): 3/3 paths completed, 0 stuck
- stairs: 0 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 6 cells, 0 under 8, 0 at 0
  - room y+0 5 cells round -1361,76,-1315: reached, light min 10, 0 cells under 8; market_stall, counter, job_site
- defects:
  - **MED** folk pathfinder finds no way from the street to the counter at -1360, 76, -1314 (gets as far as -1361,76,-1314)
  - **HIGH** folk pathfinder finds no way from the street to the job_site (merchant) at -1360, 76, -1314 (gets as far as -1361,76,-1314)
- shots: grown_market_stall_#30_main.jpg

### grown smithy #31 — PASS

`hearth:work/smithy` temperate, 9x10 facing north, tier 1, origin -1342, 75, -1298, 0 beds. Street start -1338,75,-1300.

- rooms reached 1/3; entrances 1/1; doors bad 0/0
- POIs walked to (model): job_site 4/4, storage 2/2, seat 1/1, door 1/1
- folk pathfinder street -> bed/job/store: 6/6
- player driven (real movement & collision): 6/6 paths completed, 0 stuck
- stairs: 0 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 55 cells, 3 under 8, 0 at 0
  - room y+0 46 cells round -1337,75,-1295: reached, light min 8, 0 cells under 8; door, job_site, job_site, job_site, storage, storage, job_site, seat
  - room y+5 5 cells round -1339,80,-1292: NOT reached, light min -, 0 cells under 8
  - room y+5 5 cells round -1336,80,-1292: NOT reached, light min -, 0 cells under 8
- defects:
  - **LOW** room of 5 cells at y+5 round -1339,80,-1292 cannot be reached (nothing in it)
  - **LOW** room of 5 cells at y+5 round -1336,80,-1292 cannot be reached (nothing in it)
- shots: grown_smithy_#31_main.jpg

### grown logging_camp #37 — PASS

`hearth:work/logging_camp` temperate, 15x15 facing east, tier 0, origin -1459, 76, -1243, 2 beds. Street start -1443,76,-1236.

- rooms reached 2/2; entrances 2/2; doors bad 0/1
- POIs walked to (model): bed 2/2, job_site 4/4, storage 1/1, table 1/1, door 1/1
- folk pathfinder street -> bed/job/store: 7/7
- player driven (real movement & collision): 9/9 paths completed, 0 stuck
- stairs: 0 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 38 cells, 1 under 8, 0 at 0
  - room y+0 18 cells round -1456,76,-1232: reached, light min 11, 0 cells under 8; bed, bed, table, storage
  - room y+0 6 cells round -1453,76,-1236: reached, light min 8, 0 cells under 8; door
- shots: grown_logging_camp_#37_main.jpg

### grown sawmill #38 — PASS

`hearth:work/sawmill` temperate, 10x11 facing west, tier 0, origin -1441, 76, -1239, 0 beds. Street start -1443,76,-1234.

- rooms reached 1/1; entrances 1/1; doors bad 0/0
- POIs walked to (model): job_site 4/4, storage 1/1
- folk pathfinder street -> bed/job/store: 5/5
- player driven (real movement & collision): 6/6 paths completed, 0 stuck
- stairs: 0 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 51 cells, 22 under 8, 0 at 0
  - room y+0 45 cells round -1434,76,-1233: reached, light min 3, 20 cells under 8; job_site, job_site, storage, job_site
- defects:
  - **LOW** room at y+0 round -1434,76,-1233 is dim: 20 of 45 cells under light 8 (min 3)
- shots: grown_sawmill_#38_main.jpg

### grown barn #40 — PASS

`hearth:work/barn` temperate, 11x13 facing north, tier 0, origin -1289, 75, -1298, 0 beds. Street start -1284,75,-1300.

- rooms reached 3/3; entrances 1/1; doors bad 0/0
- POIs walked to (model): job_site 2/2, door 1/1, work_face 4/4
- folk pathfinder street -> bed/job/store: 6/6
- player driven (real movement & collision): 9/9 paths completed, 0 stuck
- stairs: 0 flight steps, 0 unclimbable; jumps needed 1; tight head room 1
- light under roof: 114 cells, 15 under 8, 0 at 0
  - room y+0 57 cells round -1284,75,-1291: reached, light min 3, 10 cells under 8; door, work_face, work_face, work_face, work_face, job_site
  - room y+1 7 cells round -1279,76,-1292: reached, light min 7, 2 cells under 8
  - room y+5 24 cells round -1283,80,-1290: reached, light min 10, 0 cells under 8; job_site
- defects:
  - **LOW** 1 full-block step(s) on the way to rooms, beds or work with no stair (a player must jump): -1279,76,-1295
  - **LOW** head room under 1.9 at 1 cell(s) (head bumps): -1289,75,-1293
- shots: grown_barn_#40_main.jpg

### grown hunters_lodge #41 — PASS

`hearth:work/hunters_lodge` temperate, 8x8 facing south, tier 0, origin -1288, 75, -1309, 0 beds. Street start -1284,75,-1300.

- rooms reached 2/2; entrances 2/2; doors bad 0/1
- POIs walked to (model): job_site 2/2, storage 1/1, seat 1/1, hearth 1/1, door 1/1
- folk pathfinder street -> bed/job/store: 3/3
- player driven (real movement & collision): 5/5 paths completed, 0 stuck
- stairs: 0 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 41 cells, 0 under 8, 0 at 0
  - room y+0 16 cells round -1284,75,-1301: reached, light min 8, 0 cells under 8; door
  - room y+0 20 cells round -1284,75,-1306: reached, light min 11, 0 cells under 8; hearth, seat, job_site, job_site, storage
- shots: grown_hunters_lodge_#41_main.jpg

### grown shrine #42 — PASS

`hearth:civic/shrine` temperate, 3x3 facing east, tier 1, origin -1359, 74, -1262, 0 beds. Street start -1355,74,-1261.

- rooms reached 0/0; entrances 1/1; doors bad 0/0
- POIs walked to (model): altar 1/1, door 1/1
- folk pathfinder street -> bed/job/store: 0/0
- player driven (real movement & collision): 0/0 paths completed, 0 stuck
- stairs: 0 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 3 cells, 0 under 8, 0 at 0

### grown reeve_house #48 — FAIL

`hearth:house/reeve_house` temperate, 10x13 facing north, tier 1, origin -1413, 78, -1276, 4 beds. Street start -1408,78,-1278.

- rooms reached 6/6; entrances 1/1; doors bad 0/3
- POIs walked to (model): bed 4/4, job_site 1/1, storage 5/5, notice_board 1/1, seat 2/2, table 4/4, hearth 1/1, door 1/1, viewpoint 1/1, strongbox 1/1
- folk pathfinder street -> bed/job/store: 9/10
- player driven (real movement & collision): 20/20 paths completed, 0 stuck
- stairs: 8 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 133 cells, 11 under 8, 0 at 0
  - room y-1 4 cells round -1413,77,-1269: reached, light min 6, 3 cells under 8
  - room y+0 17 cells round -1408,78,-1268: reached, light min 10, 0 cells under 8; hearth, storage, table, seat
  - room y+0 4 cells round -1406,78,-1275: reached, light min 11, 0 cells under 8; door, notice_board
  - room y+0 4 cells round -1407,78,-1272: reached, light min 11, 0 cells under 8; seat, table, job_site, strongbox
  - room y+4 36 cells round -1408,82,-1269: reached, light min 11, 0 cells under 8; bed, bed, bed, table
  - room y+8 29 cells round -1408,86,-1269: reached, light min 10, 0 cells under 8; bed, storage, table, storage, storage, storage
- defects:
  - **LOW** room at y-1 round -1413,77,-1269 is dim: 3 of 4 cells under light 8 (min 6)
  - **HIGH** folk pathfinder finds no way from the street to the job_site (reeve) at -1409, 78, -1273 (gets as far as -1408,78,-1273)
- shots: grown_reeve_house_#48_main.jpg

### grown inn #50 — WARN

`hearth:commerce/inn` temperate, 11x11 facing south, tier 0, origin -1326, 78, -1386, 2 beds. Street start -1321,78,-1374.

- rooms reached 6/7; entrances 1/1; doors bad 0/4
- POIs walked to (model): bed 2/2, job_site 1/1, storage 1/1, market_stall 1/1, counter 1/1, seat 1/1, table 1/1, hearth 1/1, door 1/1
- folk pathfinder street -> bed/job/store: 5/5
- player driven (real movement & collision): 12/12 paths completed, 0 stuck
- stairs: 4 flight steps, 0 unclimbable; jumps needed 1; tight head room 0
- light under roof: 90 cells, 6 under 8, 0 at 0
  - room y+0 28 cells round -1324,78,-1380: reached, light min 8, 0 cells under 8
  - room y+0 4 cells round -1319,78,-1384: reached, light min 4, 4 cells under 8
  - room y+0 14 cells round -1318,78,-1380: reached, light min 11, 0 cells under 8; job_site, counter, market_stall, hearth, seat, table
  - room y+4 7 cells round -1324,82,-1379: reached, light min 11, 0 cells under 8; bed
  - room y+4 8 cells round -1320,82,-1382: reached, light min 11, 0 cells under 8; storage
  - room y+4 7 cells round -1319,82,-1379: reached, light min 11, 0 cells under 8; bed
  - room y+7 14 cells round -1320,85,-1380: NOT reached, light min -, 0 cells under 8
- defects:
  - **LOW** room at y+0 round -1319,78,-1384 is dim: 4 of 4 cells under light 8 (min 4)
  - **MED** room of 14 cells at y+7 round -1320,85,-1380 cannot be reached (nothing in it)
  - **LOW** 1 full-block step(s) on the way to rooms, beds or work with no stair (a player must jump): -1317,79,-1378
- shots: grown_inn_#50_main.jpg

### grown apiary #52 — PASS

`hearth:work/apiary` temperate, 9x10 facing south, tier 1, origin -1426, 78, -1286, 0 beds. Street start -1422,78,-1275.

- rooms reached 5/5; entrances 2/2; doors bad 0/1
- POIs walked to (model): job_site 4/4, storage 2/2, door 1/1
- folk pathfinder street -> bed/job/store: 6/6
- player driven (real movement & collision): 11/11 paths completed, 0 stuck
- stairs: 0 flight steps, 0 unclimbable; jumps needed 3; tight head room 0
- light under roof: 32 cells, 2 under 8, 0 at 0
  - room y+0 7 cells round -1422,78,-1281: reached, light min 10, 0 cells under 8; door, job_site
  - room y+0 4 cells round -1422,78,-1283: reached, light min 12, 0 cells under 8; storage, job_site, storage
  - room y+1 7 cells round -1425,79,-1283: reached, light min 7, 1 cells under 8
  - room y+1 4 cells round -1422,79,-1284: reached, light min 11, 0 cells under 8; storage, job_site, storage
  - room y+1 6 cells round -1418,79,-1283: reached, light min 7, 1 cells under 8
- defects:
  - **LOW** 3 full-block step(s) on the way to rooms, beds or work with no stair (a player must jump): -1426,79,-1283; -1423,79,-1284; -1418,79,-1283
- shots: grown_apiary_#52_main.jpg

### grown bakery #53 — FAIL

`hearth:work/bakery` temperate, 9x9 facing north, tier 0, origin -1306, 74, -1298, 0 beds. Street start -1302,74,-1300.

- rooms reached 1/2; entrances 3/3; doors bad 1/2
- POIs walked to (model): job_site 3/3, storage 1/1, seat 1/1, door 2/2
- folk pathfinder street -> bed/job/store: 4/4
- player driven (real movement & collision): 5/5 paths completed, 0 stuck
- stairs: 0 flight steps, 0 unclimbable; jumps needed 0; tight head room 0
- light under roof: 28 cells, 3 under 8, 0 at 0
  - room y+0 7 cells round -1300,74,-1293: reached, light min 11, 0 cells under 8; job_site, job_site, storage
  - room y+3 4 cells round -1299,77,-1292: NOT reached, light min -, 0 cells under 8
- defects:
  - **HIGH** door at -1303, 74, -1293 leads into a wall or a drop on its east side (-1:oak_planks / 0:smoker / 1:air / 2:spruce_shelf)
  - **LOW** room of 4 cells at y+3 round -1299,77,-1292 cannot be reached (nothing in it)
- shots: grown_bakery_#53_main.jpg

## Part B residents

- **dusk** (13101): 48 embodied, 48 with a home: 0 asleep in a bed of their home, 0 asleep elsewhere, 11 awake at home, 37 away from home
    - Cole Ashdown (builder) home #354 house/manor: at -1403, 77, -1297, EAT / going to sit
    - Osric Pond (builder) home #69 house/farmhouse: at -1393, 75, -1321, EAT / going to sit
    - Elspeth Ashdown (smith) home #215 house/townhouse: at -1332, 74, -1290, EAT / going to sit
    - Nell Cotton (smith) home #89 civic/guardhouse: at -1413, 76, -1345, EAT / going to sit
    - Barrow Farrow (builder) home #475 house/cottage: at -1345, 83, -1324, EAT / going to sit
    - Oswin Moss (steward) home #159 house/manor: at -1374, 78, -1297, EAT / going to sit
    - Davy Clayton (merchant) home #166 house/townhouse: at -1379, 76, -1311, EAT / going to sit
    - Posy Stanhope (merchant) home #212 house/townhouse: at -1349, 76, -1320, EAT / going to sit
    - Jeremiah Ridley (builder) home #184 house/townhouse: at -1358, 76, -1310, EAT / going to sit
    - Gareth Ridley (merchant) home #173 law/jail: at -1372, 81, -1308, EAT / going to sit
    - Oliver Cotton (builder) home #173 law/jail: at -1410, 73, -1326, behaviour hearth:social/tavern_games (priority 60) over EAT
    - Meg Penrose (merchant) home #209 house/townhouse: at -1341, 77, -1322, EAT / going to sit
    - Barnaby Swift (builder) home #184 house/townhouse: at -1352, 76, -1308, EAT / going to sit
    - Bess Chandler (forester) home #215 house/townhouse: at -1341, 78, -1325, EAT / going to sit
    - Meg Tidwell (scholar) home #271 civic/monastery: at -1368, 81, -1304, EAT / going to sit
    - Wystan Summerfield (builder) home #271 civic/monastery: at -1345, 83, -1324, EAT
    - Ned Summerfield (merchant) home #347 house/reeve_house: at -1337, 75, -1305, EAT
    - Hollis Hedgely (priest) home #397 house/reeve_house: at -1303, 74, -1323, EAT / going to sit
    - Alden Hale (carpenter) home #394 house/merchant_house: at -1375, 81, -1294, EAT / going to sit
    - Harold Ford (mason) home #285 noble/hunting_lodge: at -1346, 83, -1323, EAT / going to sit
    - Barnaby Ridley (glassmaker) home #514 house/tenement: at -1321, 76, -1356, EAT / eating at home (on the way)
    - Hugh Wickham (quarrier) home #312 house/farmhouse: at -1331, 76, -1379, EAT / going to sit
    - Silas Cobb (baker) home #420 civic/almshouse: at -1337, 77, -1331, EAT / going to sit
    - Osric Warrick (baker) home #420 civic/almshouse: at -1314, 75, -1332, EAT / going to sit
    - Theo Hedgely (none) home #420 civic/almshouse: at -1378, 76, -1264, HOME / going to sit
    - Oliver Larkin (none) home #694 civic/orphanage: at -1351, 75, -1301, HOME / going to sit

- **midnight** (18251): 48 embodied, 48 with a home: 29 asleep in a bed of their home, 0 asleep elsewhere, 5 awake at home, 14 away from home
    - Barrow Farrow (builder) home #475 house/cottage: at -1362, 77, -1310, SLEEP / going to bed
    - Oswin Moss (steward) home #159 house/manor: at -1346, 74, -1283, SLEEP / going to bed
    - Posy Stanhope (merchant) home #212 house/townhouse: at -1348, 84, -1312, SLEEP / going to bed
    - Meg Penrose (merchant) home #209 house/townhouse: at -1340, 83, -1320, SLEEP / going to bed
    - Bess Chandler (forester) home #215 house/townhouse: at -1356, 74, -1288, SLEEP / going to bed
    - Wystan Summerfield (builder) home #271 civic/monastery: at -1345, 83, -1324, SLEEP / going to bed
    - Harold Ford (mason) home #285 noble/hunting_lodge: at -1346, 83, -1323, SLEEP / going to bed
    - Nicholas Woodruff (kiln_worker) home #493 house/tenement: at -1332, 76, -1357, SLEEP / going to bed
    - Silas Cobb (baker) home #420 civic/almshouse: at -1382, 76, -1320, SLEEP / going to bed
    - Osric Warrick (baker) home #420 civic/almshouse: at -1341, 74, -1299, SLEEP / going to bed
    - Edwin Elwood (smith) home #355 house/scribe_house: at -1346, 83, -1323, SLEEP / going to bed
    - Desmond Underhill (guard) home #499 house/tenement: at -1366, 77, -1351, WORK / keeping watch (on the way)
    - Juliana Underhill (none) home #499 house/tenement: at -1348, 89, -1329, SLEEP / going to bed
    - Rose Pargeter (none) home #251 house/hermit_hut: at -1344, 83, -1324, SLEEP / going to bed

- **morning** (4101): 48 embodied, 28 with a work plot, 11 at it
    - grown builders_yard #5: morning: at work 1/2
    - grown smithy #31: morning: at work 1/2
    - Cole Ashdown (builder) not at work plot #5 work/builders_yard: at -1367, 76, -1323, WORK / at the builders' yard (on the way)
    - Bess Norwood (mason) not at work plot #71 work/masonry: at -1327, 75, -1307, WORK / dressing stone (on the way)
    - Wilhelmina Swift (merchant) not at work plot #164 commerce/market_hall: at -1334, 75, -1301, WORK / at the stall (on the way)
    - Nell Cotton (smith) not at work plot #31 work/smithy: at -1413, 76, -1347, WAKE / getting up: getting up (on the way)
    - Gideon Fairweather (brewer) not at work plot #117 work/brewery: at -1386, 80, -1314, WORK / brewing
    - Emma Honeywell (builder) not at work plot #220 work/builders_yard: at -1390, 80, -1312, WORK / at the builders' yard
    - Esme Hollins (baker) not at work plot #231 work/bakery: at -1414, 75, -1331, WORK / at the ovens (on the way)
    - Bess Chandler (forester) not at work plot #261 work/logging_camp: at -1376, 78, -1295, WORK / felling timber (on the way)
    - Davy Cooper (guard) not at work plot #167 noble/keep: at -1381, 77, -1266, WORK / keeping watch (on the way)
    - Alden Hale (carpenter) not at work plot #338 work/carpentry: at -1382, 90, -1282, WORK / at the bench
    - Wren Linden (kiln_worker) not at work plot #416 work/brick_kiln: at -1374, 75, -1330, WORK / firing bricks at the kiln
    - Philippa Thatcher (glassmaker) not at work plot #242 work/glassworks: at -1404, 74, -1331, WORK / blowing glass (on the way)
    - Marjorie Thatcher (glassmaker) not at work plot #242 work/glassworks: at -1327, 76, -1316, WORK / blowing glass (on the way)
    - Margery Fowler (smith) not at work plot #469 work/smithy: at -1403, 78, -1293, WORK / at the anvil (on the way)
    - Gideon Webb (guard) not at work plot #500 civic/guardhouse: at -1361, 76, -1372, SLEEP / asleep
    - Desmond Underhill (guard) not at work plot #500 civic/guardhouse: at -1390, 79, -1281, SLEEP / going to bed
    - Bartholomew Tidwell (priest) not at work plot #527 civic/convent: at -1410, 74, -1335, WORK / tending the altar

