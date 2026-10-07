# G5 audit: landmarks, nobility, defence

Auditor: G5 (landmark/**, noble/**, defense/**). Worktree `hearth-audit` @ 7c8a23b3. Sources: the contact sheets and cutaways in
`../sheets/{landmark,noble,defense}/`, `../metrics.csv` (suited cases only), the archetype classes under
`hearth/src/main/java/dev/voidmc/hearth/build/archetype/{landmark,noble,defense}/` and the kits `build/kit/{landmark,noble}/`.
I re-ran three walk maps (`structureAudit -Pmode=metrics -Preach=...`) to confirm findings: northern keep level 11,
karst pagoda level 2, desert palace level 5. Crops I added are in `img/G5_*.webp` (about 0.5 MB in all).

**Castles are being reworked on `hearth-w6-fix`.** The castle is graded as it stands on `hearth`; most of its defects are
probably already being fixed there. One fix, though, is in a shared kit (`kit/noble/Gate.java`), so the keep and palace
need it as well (fix 1).

Severity: **blocker** = broken or unplayable; **major** = clearly below hand-built quality; **minor** = polish.
"Likes" = the number of suited cultures at least 0.8 alike to this one (cultureSimilarity).

## Grades

| archetype | grade | play / detail (audit means) | one line |
|---|---|---|---|
| landmark/cathedral | B+ | 77 / 74 | Strong silhouettes in all four manners. One gothic plan covers four cultures; the northern chancel can't be reached; the alpine gallery has no rail |
| landmark/great_hall | B | 85 / 86 | Good interiors. The northern roof is pale white, the dragon heads read as antlers, the alpine balcony is fake |
| landmark/bell_tower | B | 82 / 80 | Solid campaniles. Alpine has the wrong crown and repeated stages; the base rooms are empty |
| landmark/lighthouse | B | 76 / – | A good tower, but the same in all four cultures; the keeper's cottage is a cramped box |
| landmark/clock_tower | B- | 83 / 80 | Four plans reskinned across 13 cultures; one-block dials read as holes; the karst podium is a blank box |
| defense/wall | B- | 85 / 60 | A sound stretch; one stub in every culture; the timber walls (tropical, bayou) are flat |
| defense/watchtower | B- | 91 / 61 | Neat, but one tower in 10 cultures and its middle storeys are empty |
| landmark/pagoda | C+ | 75 / 51 | The karst pagoda is good but its front door is blocked for the walk; the wisteria "pagoda" is just the bell tower again |
| landmark/temple | C+ | 88 / 64 | Mediterranean and desert are good. The bayou and mesa forms don't fit their cultures; the karst and bayou halls are empty |
| noble/stables | C+ | 87 / 72 | One plan in 13 cultures; 150 to 200-cell blind walls; tundra in sod and moss with a grass-block lantern |
| defense/palisade | C+ | 100 / 35 | Works, but flat panels of logs; the "points" are fence posts |
| noble/keep | C | 61 / 69 | Dead ladder in 84/84; northern and tundra gate-tower room can't be reached; heads touch the ceiling; one plan in every culture |
| noble/palace | C | 52 / 75 | Arcade roofs are walkable with no rail (140 to 350 fall cells); dead ladder; the desert palace is crenellated boxes |
| noble/hunting_lodge | C | 80 / 66 | One plan in 11 cultures; the timber band reads as a black outline; no eaves; empty chambers |
| defense/gatehouse | C | 80 / 65 | One lopsided plan in 13 cultures; tall plain boxes with no eaves; patchy palette in karst and mediterranean |
| noble/castle (hearth-w6-fix) | D | 46 / 72 | Silhouettes differ well by culture, but reach is broken: 203 unreachable furnished cells, 49 broken flights, the only test failure in the catalogue |

Distribution: A 0 · B+ 1 · B 3 · B- 3 · C+ 4 · C 4 · D 1 (16 archetypes).
Blockers: 11 (7 outside the castle, 4 in the castle).

---

## landmark/cathedral — B+

Suited: temperate, wisteria, northern, alpine, moorland, maritime, mediterranean. Sheet `sheets/landmark/landmark_cathedral.webp`,
cut `landmark_cathedral_cut.webp`. Exterior quality is high: buttresses, pinnacles, rose window, crossing tower, aisles under
lean-tos. Northern (stave), alpine (baroque) and mediterranean (romanesque) are real, distinct manners. Inside: pews,
chequered nave, chandeliers, altar. Light min 9 to 10, no dark cells.

| # | sev | defect | cultures | image | at fault | fix |
|---|---|---|---|---|---|---|
| 1 | blocker | The stave cathedral's chancel/apse is furnished but can't be reached (`unreached_furnished_room@11/0/27`, 5 cells). Candles and lectern sit behind a step or rail the walk can't pass | northern (flat s0, slope s0, hills s0) | cut sheet, northern level 0 (apse at the back) | `StaveCathedral.java` (chancel step / altar rail) | Open a 1-wide gap in the chancel rail or turn the step into a stair up; check with `-Preach=landmark/cathedral,northern,1007,south,0` |
| 2 | major | The alpine (and mediterranean) gallery around the dome/towers at y19 is reachable with no rail: 89 to 105 fall cells | alpine, mediterranean | metrics `fall_edge@19/19/14` | `Cathedral.java` baroque/romanesque tower decks, `kit/landmark/Tower` deck | Add a balustrade on every walkable deck edge that `Tower.deck` exposes (fence or wall on the outer ring) |
| 3 | major | One gothic plan and massing covers four cultures; only the stone changes. Moorland should be a plainer kirk-like minster, maritime a granite one | temperate, wisteria, moorland, maritime (likes 3) | sheet row 1 | `Cathedral.java` manner choice | Give moorland and maritime their own gothic variant (crossing tower only, no flyers, lower roof, harled walls for moorland) |
| 4 | minor | Large sealed roof voids, 400+ cells (lit by the light pass, but wasted) | all gothic | `sealed_void@12/29/17` | `Cathedral.java` nave roof | `Voids.seal` the nave attic like the pagoda's top roof |
| 5 | minor | Bad flight in one case (step without headroom) | northern hills s1 | `bad_flight@15/4/26` | `StaveCathedral.java` | Clear the block over the 4th tread |

## landmark/great_hall — B

Suited: northern, alpine, tundra, moorland. Sheet `landmark_great_hall.webp`, crop `img/G5_great_hall_suited.webp`, cut
`landmark_great_hall_cut.webp`. Interiors are among the best in the group: posts, long hearth, tables, dais, lanterns
(furniture 0.60 per cell). The moorland crow-stepped rubble hall is excellent.

| # | sev | defect | cultures | image | at fault | fix |
|---|---|---|---|---|---|---|
| 1 | major | The roof and gable boards are pale, almost white (stripped light wood), so the hall reads as snow-covered, against the doc's "warm brown over dark logs" | northern | `img/G5_great_hall_suited.webp` (left) | `GreatHall.java` `boards` = GABLE role; northern style GABLE/roof role | Shingle the roof in spruce/dark oak (the doc's intent); keep the pale timber for trim only |
| 2 | major | The alpine "two storeys" is one 7-high hall with a balcony and shuttered windows at y5 that nothing reaches (28 unreached cells, 2 levels) | alpine | `sealed_void@4/7/5`; crop middle | `GreatHall.java:81-258` (`wallH = 7`, `Porches.balcony`) | Add a timber gallery inside at y4 to y5 along the front with a stair, and a door onto the balcony; or give up the false storey |
| 3 | major | The dragon heads are two long crossing stair sticks two blocks beyond the ridge; at distance they read as antlers or broken barge boards | northern, tundra (and the not-suited reskins) | crop | `GreatHall.java:384-411` (`dragon`) | Shorten to one block past the apex, curl it back with one upside-down stair, and add an eye or jaw slab |
| 4 | minor | Tundra roof is noisy stripes of two timbers | tundra | crop | tundra roof palette | Blend in broad patches (<10 % second tone) |
| 5 | minor | `rooms_no3x3` 24/24: the side aisles between posts and walls are 2 wide | all | cut | `GreatHall.java` post rows | Set posts 3 in from the wall where the hall is 15+ wide |

## landmark/bell_tower — B

Suited: temperate, wisteria, northern, alpine, moorland, maritime, mediterranean, bayou. Sheet `landmark_bell_tower.webp`,
cut `landmark_bell_tower_cut.webp`. The Bohemian render tower (temperate) and the timber klockstapel (northern, bayou) read well.

| # | sev | defect | cultures | image | at fault | fix |
|---|---|---|---|---|---|---|
| 1 | major | The alpine tower has a flat slab pyramid, not the onion the doc promises, over a shaft of four identical open-lancet stages (repeat 0.39). It reads as scaffolding | alpine | sheet, alpine panel | `BellTower.java` crown choice; `Crowns.onion` | Force `Crowns.Kind.ONION` (bulbSkin copper/shingle) for alpine, and close the lower stages (blind arch, single light) so only the belfry is open |
| 2 | major | Belfry and gallery openings have no rail: 36 to 49 fall cells at y16 to y33 | temperate, northern, bayou, alpine, maritime, mediterranean | `fall_edge@8/16/3`, `@7/33/5` | `kit/landmark/Tower` openings at deck level, `Wooden` belfry | Sill every belfry opening at 1 block (slab or balustrade) so a walker can't step out |
| 3 | minor | The base room is the spiral and nothing else (purposeless room in 6 cultures) | all | cut level 0 | `BellTower.java` | Add a bell-rope (chain), a bench and a chest for the ringer |
| 4 | minor | Stage repetition in wisteria/maritime/mediterranean shafts (repeat 0.30 to 0.50) | those | sheet | `Tower` stage layout | Vary the middle stage (bifora vs blind arch) per seed |
| 5 | minor | Unreached cells under the helm or in the belfry loft, 39 to 49 | temperate, bayou | `sealed_void@3/23/3` | crown | `Voids.seal` under the crown |

## landmark/lighthouse — B

Suited: maritime, tropical, mediterranean, northern. Sheet `landmark_lighthouse.webp`, cut `landmark_lighthouse_cut.webp`,
crop `img/G5_lighthouse_cottage.webp`.

| # | sev | defect | cultures | image | at fault | fix |
|---|---|---|---|---|---|---|
| 1 | major | The keeper's cottage is about 3x3 inside, filled by bed, chest and table; `rooms_no3x3` 24/24 | all | `img/G5_lighthouse_cottage.webp` | `Lighthouse.java` cottage | Make it 5x6 inside: hearth wall, bed in a corner, table with the log on its lectern, a free 3x3 |
| 2 | major | The tower is identical in all four cultures (likes 3 to 4); only the band colour changes | all | sheet | `Lighthouse.java` | Per culture: a granite unbanded tower for northern/maritime on a share; a white square coral-stone tower for tropical; a short square tower on a keeper's house for mediterranean |
| 3 | major | Bad flight (broken step) on slope and coast | tropical s1 (3 cases) | `bad_flight@7/3/8` | `Lighthouse.java` spiral foot / mound | Start the spiral from the levelled plinth floor, not from the mound |
| 4 | minor | The tower base is a purposeless room, the spiral only | all | `purposeless_room@10/0/7` | | Put an oil store (barrels) and a coil of rope in the base |
| 5 | minor | No jetty or water shows on the coast sheet | all | sheet | terrain/jetty condition | Check the jetty is drawn whenever coast water lies within 2 of the plot |

## landmark/clock_tower — B-

Sheet `landmark_clock_tower.webp`, cut `landmark_clock_tower_cut.webp`, crops `img/G5_clock_tower_temperate.webp`,
`img/G5_clock_tower_karst.webp`.

| # | sev | defect | cultures | image | at fault | fix |
|---|---|---|---|---|---|---|
| 1 | major | Each dial is a single dark block on a 9-wide shaft; at any distance it reads as a hole, not a clock | temperate, wisteria, alpine, mediterranean, civic set | `img/G5_clock_tower_temperate.webp` | `kit/landmark/Details` dial, `ClockTower.java` | On shafts 9+ wide use a 3x3 dial: a gilded or quartz centre ring, stone voussoir frame, two hands (end rod/lever or trapdoors) |
| 2 | major | Four plans reskinned: temperate=wisteria=alpine, northern=tundra=moorland, tropical=bayou, desert=steppe (likes 2 each, same hash) | 13 | sheet | `ClockTower.java` manner table | Give moorland a harled tolbooth steeple, tundra a timber belfry tower, bayou a white board cupola tower |
| 3 | major | The karst drum tower's podium is a blank grey box (relief 0, flat patch 52) under a pavilion of bare posts | karst | `img/G5_clock_tower_karst.webp` | `ClockTower.java` drum manner | Batter the podium, add a stone string course and parapet, a proper arched gate with voussoirs, and lattice screens between the pavilion posts |
| 4 | major | The steppe minaret is the desert minaret in brown: not Sahelian | steppe | sheet row 2 | `ClockTower.java` minaret | Use the Sahel tower: buttressed tapering adobe with toron beams, like the steppe temple's sun tower |
| 5 | minor | Empty base rooms (purposeless in 9 cultures); unrailed gallery decks (21 to 54 fall cells) | most | cut | `ClockTower.java` | Bench and lectern in the base; balustrade on decks |

## defense/wall — B-

Sheet `defense_wall.webp` (a 13-long stub; the planner draws the real polylines).

| # | sev | defect | cultures | image | at fault | fix |
|---|---|---|---|---|---|---|
| 1 | major | The timber walls are flat palisaded slabs (flat patch 99, relief from fences only) | tropical, bayou | sheet row 2 | `TownWall.java` timber branch | Earth-filled crib: posts every 3, horizontal logs, a fighting platform on brackets |
| 2 | minor | One stub in every culture (likes 13); the arrow slits are horizontal strips | all | sheet | `TownWall.java` | Vary the merlon rhythm, coping and plinth per culture; use vertical 1x2 slits |
| 3 | minor | Bad flight at the stub's start on slope s1 (14 cases) | all | `bad_flight@0/0/2` | `TownWall.java` walk stairs at `join_start` | Start the walk stair one cell in from the end |
| 4 | minor | The wall walk has no town-side rail (fall 36) | all | | | Normal for town walls; a low rear parapet on the stretches beside streets |

## defense/watchtower — B-

Sheet `defense_watchtower.webp`, cut `defense_watchtower_cut.webp`.

| # | sev | defect | cultures | image | at fault | fix |
|---|---|---|---|---|---|---|
| 1 | major | Storeys 2 and 3 hold the stair and nothing else (`empty_rooms` 84/84) | all | cut levels 4, 8 | `Watchtower.java` | Storey 2: guard's bunk, chest, arms rack; storey 3: a brazier and a table |
| 2 | major | One tower in 10 cultures (likes 10) | 10 | sheet | `Watchtower.java` | Round towers for maritime/wisteria/steppe (as their castles); a timber hoarding top for northern/tundra |
| 3 | minor | Open battlement top (fall 29) | desert, mediterranean, mesa | `fall_edge@7/18/4` | crenel deck | Merlons 2 high or a slab sill between them |

## landmark/pagoda — C+

Suited: karst, wisteria. Sheet `landmark_pagoda.webp`, cut `landmark_pagoda_cut.webp`, crop `img/G5_pagoda_wisteria_karst.webp`
(the wisteria garden tower).

| # | sev | defect | cultures | image | at fault | fix |
|---|---|---|---|---|---|---|
| 1 | blocker | Karst: the cell before the shrine's main door can't be stood on by the light/NPC walk (`entrance_unreached@8/2/8`, `blocked_door@8/2/9`) in all six cases, flat included. The walk map shows `.` (air, not standable) in front of the door between two reached cells. BlueprintChecks pass only because their walker stands on candles/lanterns/pots | karst | `pagoda` walk map (re-run, level 2) | `Pagoda.java:184-186` door versus podium steps and court objects | Keep `xc, y0, doorZ-1` clear down to a solid tread: land the podium steps flush at the door; move any lantern/candle/pot off the axis |
| 2 | major | Tier galleries are walkable with no balustrade: 101 to 128 fall cells | karst | `fall_edge@13/6/8` | `Pagoda.java` tier loop, `Eaves` | Run a 1-high balustrade (fence on slab) round every stepped tier, as the doc says |
| 3 | major | Wisteria's "pagoda" is `new BellTower().draw(d)` in a hedge: a second copy of wisteria's bell tower, with a mottled teal spire | wisteria | `img/G5_pagoda_wisteria_karst.webp` | `Pagoda.java:67-70, 403` `gardenTower` | Drop WISTERIA from the pagoda's cultures (it already has bell tower, clock tower, cathedral); if it needs a second landmark, give it its own (market cross with a guildhall) |
| 4 | minor | The tiers hardly taper, and their walls are bright yellow bamboo that reads as gold | karst | sheet | `Pagoda.java` tier rects | Step each tier in by 1 per side; dark timber posts with lattice infill, bamboo only as accent |
| 5 | minor | The gate is a Japanese torii; karst is South China/Vietnam | karst | sheet | `Pagoda.java` court gate | A paifang: two or four posts, a tiled hip on top, a lintel board |

## landmark/temple — C+

Sheet `landmark_temple.webp`, crop `img/G5_temple_row2.webp`, cut `landmark_temple_cut.webp`. Mediterranean (colonnade) is
clean and convincing; desert (dome court and minaret) is good.

| # | sev | defect | cultures | image | at fault | fix |
|---|---|---|---|---|---|---|
| 1 | major | Bayou's temple is the karst hall with a black roof on piles: a Chinese hall in Louisiana | bayou | crop | `Temple.java` form table (`hall` for bayou) | Bayou form: a white board creole church on piers with a gallery, steep tin roof and a little belfry |
| 2 | major | Mesa's temple is a Mesoamerican stepped pyramid; the culture is the American Southwest (pueblo, mission) | mesa | crop | `Temple.java` (`pyramid` for mesa) | A mission church (adobe, buttressed, bell gable, walled campo santo) or a terraced pueblo kiva court |
| 3 | major | The karst and bayou halls are empty inside (furniture 0.00): a vast flat yellow floor, one altar | karst, bayou | cut | `Temple.java` hall interior | Fill it: an altar table with incense burners, a figure on a dais, cushions in rows, drum and bell on stands, pillars with banners, lanterns |
| 4 | major | The steppe sun temple reads as a brown lump; the ring wall's white glazed tile reads blue-white | steppe | crop | `Temple.java:1023-1046` round form | Fewer, taller buttress-pinnacles at a steady rhythm; toron beams in one row; band in mud brick, not glazed tile |
| 5 | minor | The desert dome is stepped and patchy; the red bands are busy | desert | crop | `Temple.java:366` dome | Smoother `Shapes.domeRows`; one band per storey |
| 6 | minor | Steppe slope: 8-cell roof hole (sun tower's open top over the hearth); tropical/mesa summit shrine has no room | steppe, tropical, mesa | `roof_hole@15/2/13` | | Expected smoke hole; give the summit shrine walls and a door |

## noble/stables — C+

Sheet `noble_stables.webp`, crop `img/G5_stables_tundra.webp`, cut `noble_stables_cut.webp`.

| # | sev | defect | cultures | image | at fault | fix |
|---|---|---|---|---|---|---|
| 1 | major | One plan in all 13 cultures (likes 13): a range, a shed, a low yard wall | all | sheet | `Stables.java` | Per-culture layouts: courtyard caravan stable (desert/mesa/steppe), log stable with a loft door (northern/tundra), stilted or open-sided shed (tropical/bayou), karst stable court |
| 2 | major | Blind back and side walls of 145 to 208 cells | all (bayou 196, mesa 175) | metrics `flat_patch_max` | `Stables.java` (`r.blind(BACK)`) | Buttresses or posts every 3, small high vents or shuttered loft hatches, a loft door with a hoist beam on the gable |
| 3 | major | Tundra wears the poor man's sod dressing: mossy walls and a turf roof, so the noble stables read as a ruin. The ridge lantern's cap is a grass block | tundra | `img/G5_stables_tundra.webp` | dressing selection for category NOBLE (no gentry station); `Stables.lantern` takes `Turret.cap` in roof material | Noble archetypes should imply the lord/gentry station (longhouse dressing); cap the lantern in boards or shingle, never turf |
| 4 | minor | The ridge lantern reads as a solid box with a dark "door" in every culture | all | sheet | `Stables.java:264-285` | Make it 2 high with open louvres on all four sides, and a stair-pyramid cap with a vane |
| 5 | minor | Mediterranean hills: 4 unreached furnished cells (the master's chamber or loft) | mediterranean hills s1 | `unreached_furnished_room@14/9/17` | `Stables.java` loft stair on terrain | Re-level the loft stair foot after terracing |

## defense/palisade — C+

Sheet `defense_palisade.webp`, crop `img/G5_palisade_steppe_karst.webp`.

| # | sev | defect | cultures | image | at fault | fix |
|---|---|---|---|---|---|---|
| 1 | major | Flat panels of whole logs; the "sharpened points" are thin fence posts and read as sticks | all | sheet | `Palisade.java` (`point` = fence) | Points from the log's own material (stripped log end + upside-down stair or wall top), a walk on posts behind it on longer runs |
| 2 | minor | Karst cherry stakes read purple-pink; steppe acacia orange with grey bark reads striped | karst, steppe | crop | style `TREE_LOG` fort override | Bamboo or dark timber stakes in karst; debarked acacia without the grey bark in steppe |

## noble/keep — C

Sheet `noble_keep.webp`, cut `noble_keep_cut.webp`, crops `img/G5_keep_karst.webp`, `img/G5_keep_cut_temperate.webp`.

| # | sev | defect | cultures | image | at fault | fix |
|---|---|---|---|---|---|---|
| 1 | blocker | The gate tower's upper room (and the walk corners beside it) at y11 is furnished but can't be reached: 35 furnished cells, all 12 cases. Re-run walk map: the whole front block at y11 is `+` (walkable, unreached) | northern, tundra (hoarded gate tower) | walk map `noble/keep,northern,1007,south,11` | `kit/noble/Gate.java:67-75` (ladder from hall to head), timber hoarding head | See fix 1: a ladder or stair that lands on the head deck, and a door from the curtain walk into the hoarding |
| 2 | blocker | 3 furnished cells unreached on terrain | bayou slope s0, hills s0 | `unreached_furnished_room@3/5/3` | keep range on terrain | Re-level the range's first-floor stair foot |
| 3 | major | Dead ladder 84/84: the gate tower ladder stops a block short under roofed caps, with nowhere to step off | all | `dead_ladder@12/9/3` | `kit/noble/Gate.java:71` (`y < top + (CRENEL ? 1 : 0)`) | Run the ladder to `top` for every cap and cut a hatch (trapdoor) in the head floor; check with the reach walk |
| 4 | major | Heads at the ceiling on 34 % of the floor (2 clear) in the range/hall storeys | temperate, northern, tundra (17 % in alpine, wisteria, steppe) | `tight_headroom_share` | `kit/noble/Range`/`Chambers` storey heights for the keep | Storeys at least 4 (3 clear); the hall 5 |
| 5 | major | The bailey is a cramped crack: no 3x3 in 84/84, cramped rooms 55/84; the well and stable shed jammed in | all | `img/G5_keep_cut_temperate.webp` (level 0) | `Keep.java` layout | Keep a 5x5 open court at least; push the range to the back wall; the shed against the curtain |
| 6 | major | One plan in every culture (likes 4 to 8); karst keep is a European turreted keep with a big blank white tower face | all; karst worst | `img/G5_keep_karst.webp` | `Keep.java`, `Manner` | Karst: a walled compound with the lord's tower under stacked green/grey eaves (as `Compound`); northern: borg-like ring with a timber hall; desert/mesa: a qasr-like square with drum towers |
| 7 | minor | 9-cell roof holes on tower decks | moorland, maritime, desert, mediterranean, mesa, karst | `roof_hole@14/24/16` | `kit/noble/Tower` head | Close the cap over the deck or open it fully |

## noble/palace — C

Suited: desert, mediterranean, karst. Sheet `noble_palace.webp`, cut `noble_palace_cut.webp`, crops
`img/G5_palace_desert_med.webp`, `img/G5_palace_desert_l5.webp`.

| # | sev | defect | cultures | image | at fault | fix |
|---|---|---|---|---|---|---|
| 1 | blocker | Bed with no free side, and a broken flight, on hills | karst hills s0 (and karst slope bad flight) | `bed_blocked@35/4/21`, `bad_flight@20/-1/29` | `Palace.java` ranges on terraced ground | Re-run furnish after terracing; stair foot to the court level |
| 2 | major | Arcade roofs round the court are walkable from the upper storey and have no parapet: a 5-block drop into the court; 140 to 350 fall cells. Confirmed on the desert y5 walk map (reached `r` strips along the court edge) | desert, mediterranean, karst | `img/G5_palace_desert_l5.webp`, `fall_edge@33/5/19` | `Palace.java:318-355` `arcade` (roof slab at `y+h`, nothing above) | Add a balustrade (fence/wall/slab rail) at `y+h+1` on the arcade's outer edge, or make the arcade roof a lean-to below the upper windows |
| 3 | major | Dead ladder 18/18 (shared gate tower) | all | `dead_ladder@25/10/7` | `kit/noble/Gate.java` | Fix 1 |
| 4 | major | The desert palace reads as crenellated sandstone boxes: merlons on everything, a tiny stepped dome, a flat white field (flat patch 112) | desert | `img/G5_palace_desert_med.webp` | `Palace.java`, `Qasr` pieces | A true audience-hall dome (smooth `domeRows`, drum with windows), horseshoe arcades on both storeys, tile bands, timber mashrabiya bays; merlons only on the outer curtain |
| 5 | minor | Unreached room cells 34 to 110; empty rooms 2 to 4; `rooms_no3x3` up to 19 | all | | ranges | Seal voids; furnish the empty range rooms (stores, guards) |

## noble/hunting_lodge — C

Sheet `noble_hunting_lodge.webp`, crops `img/G5_hunting_lodge_temperate.webp`, `img/G5_hunting_lodge_karst.webp`, cut
`noble_hunting_lodge_cut.webp`.

| # | sev | defect | cultures | image | at fault | fix |
|---|---|---|---|---|---|---|
| 1 | blocker | Furnished upper chamber unreached on terrain (10 cells) | mediterranean slope s0, hills s0 | `unreached_furnished_room@8/7/15` | `HuntingLodge.java` tower door at `r.floorY(1)` on terrain | Re-check the tower's door cells after levelling |
| 2 | major | One plan in 11 cultures (likes 11) | all but desert/mesa | sheet | `HuntingLodge.java` | Per manner: northern/tundra log lodge with a deep porch and antlers, alpine chalet lodge, karst pavilion with a lookout, desert/mesa hunting pavilion (already distinct) |
| 3 | major | The "half-timbered" upper storey is a black rectangle outline round a glass strip; no posts or braces; overhang 0 (no eaves) | temperate, wisteria, northern, alpine, tundra, moorland, maritime | `img/G5_hunting_lodge_temperate.webp` | `HuntingLodge.halfTimber`, `Range.roof` eaves | Posts every 2 to 3 blocks, braces at corners, sill beam proud by 1, jetty the upper storey 1 out, eaves 1+ with brackets |
| 4 | major | The karst lodge is a plain white box with dark punched windows | karst | `img/G5_hunting_lodge_karst.webp` | manner | Timber posts and lattice, curved hip, veranda |
| 5 | major | The upper chambers are bare plank rooms with a bed (empty rooms 55/84) | most | cut level 5 | `HuntingLodge.rooms` | Trophies (heads/banners), chests, a table with a map, rugs, a wardrobe |
| 6 | minor | The tower barely clears the ridge (known weakness); a cramped tower base (71/84) | all | sheet | `HuntingLodge.java:94` | Lower the hall roof pitch or raise the head 3 over the ridge |

## defense/gatehouse — C

Sheet `defense_gatehouse.webp`, crop `img/G5_gatehouse_temperate.webp`, cut `defense_gatehouse_cut.webp`.

| # | sev | defect | cultures | image | at fault | fix |
|---|---|---|---|---|---|---|
| 1 | major | Lopsided by design: the left tower is 2 higher under a gable as wide as the tower, the right has crenels. From the field it reads as half a roof, not a gate | all | `img/G5_gatehouse_temperate.webp` | `Gatehouse.java:56-60, 205-222` | Mirror the towers (both roofed or both crenellated), roof the gate hall over the passage between them, or make it a single gate tower over the road |
| 2 | major | One plan in 13 cultures (likes 13); tall plain boxes with only quoin stripes; overhang 0; flat patches 35 to 93 | all | sheet | `Gatehouse.java`, `Towers` | Machicolation or hoarding on corbels, a battered plinth, an arched passage with voussoirs, drawbridge or bar beam; culture tops (round drum towers in the south, timber hoarding in the north) |
| 3 | major | Patchy palette: mossy and grey patches on light stone read as camouflage (dominant 0.79 in karst) | karst, mediterranean | sheet row 2 | `Look.body()` / style walling for forts | Weathering only at the foot and in under 10 % broad patches (art review item 2) |
| 4 | minor | Guard rooms are 3-wide with the stair taking most of the floor; one empty room; the left tower loft unreached (4 cells) | all | cut | `Gatehouse.java` | A bunk and bench in the guard room; seal or stair the loft |

## noble/castle — D (as on `hearth`; reworked on `hearth-w6-fix`)

Sheet `noble_castle.webp`, cut `noble_castle_cut.webp`. Exterior silhouettes are distinct and characterful per culture
(Bohemian, borg, rocca, qasr, compound, bastion, tower house, wards).

| # | sev | defect | cultures | image | at fault | fix |
|---|---|---|---|---|---|---|
| 1 | blocker | Up to 203 furnished cells unreached (65/84 cases); whole storeys unreached (up to 8) | 13 of 14 | metrics | ward/palace stairs | On w6-fix |
| 2 | blocker | The only BlueprintChecks failure in the catalogue | temperate slope s1 | check_text | `BohemianCastle` | On w6-fix |
| 3 | blocker | 49 bad flights (mediterranean hills); bad flights in temperate, maritime | several | `bad_flight@37/0/45` | `Rocca`, `BohemianCastle` | On w6-fix |
| 4 | blocker | Blocked doors at 13/2/37 | wisteria, alpine, maritime, mediterranean | metrics | `Castle` (WARDS) inner gate | On w6-fix |
| 5 | major | Dead ladders 72/84 | all but desert, mesa | metrics | `kit/noble/Gate.java` (shared) | Fix 1 (check w6-fix has it) |
| 6 | major | Fall edges up to 476; roof holes; cramped and empty rooms | most | metrics | walks, ranges | Parapets on every walk edge (as fix 2) |

---

## Group top 10 fixes, ranked by impact

1. **`kit/noble/Gate.java` gate tower: a ladder that lands, a head you can reach.** Run the ladder up to the head deck for
   every cap (not only CRENEL), cut a trapdoor hatch in the head floor, and give the timber-hoarded head (northern,
   tundra) a door from the curtain walk. This clears dead ladders in keep 84/84, palace 18/18 and castle 72/84, and the
   northern/tundra keep blocker (35 furnished cells). Confirm with `-Preach=noble/keep,northern,1007,south,11`. Tell
   hearth-w6-fix, since the castle shares the kit.
2. **Rails on every walkable edge in noble and landmark work.** `Palace.arcade` needs a balustrade at `y+h+1` (140 to 350
   fall cells in all three palaces). Do the same on the pagoda's tier galleries (101 to 128), belfry and clock-gallery
   openings (`kit/landmark/Tower`), and the alpine cathedral gallery (89 to 105).
3. **Clear the small reach blockers:** the karst pagoda's door approach (all 6 karst cases); the stave cathedral's chancel
   rail (northern); hunting lodge mediterranean slope/hills chamber door; palace karst hills bed and stair; keep bayou
   range stair; stables mediterranean hills loft. Each is a few lines; re-run `structureAudit -Parchetype=<path>` and
   expect `unreached_furnished_cells = 0`, `entrances_unreached = 0`.
4. **Per-culture plans for the reskinned noble and defence works.** Stables (likes 13), gatehouse (13), hunting lodge
   (11), watchtower (10) and keep (4 to 8) are one plan in the culture's stone. Follow the castle's `Layout` approach:
   2 to 4 layouts each (courtyard stable for desert/mesa/steppe, log stable for the north, karst compound keep, round
   southern towers).
5. **Culture misfits to replace:** the wisteria "pagoda" (the bell tower again; drop WISTERIA from `Pagoda.INFO`), the
   bayou temple (a creole church, not the karst hall), the mesa temple (mission or kiva court, not a Mesoamerican
   pyramid), the steppe minaret (Sahel tower), the karst keep and lodge (curved hips, timber and lattice, not white boxes).
6. **Clock tower dials and the karst drum podium.** 3x3 framed dials with hands on 9+ wide shafts (they now read as
   holes in every town-hall and civic tower); give the karst podium a batter, course, parapet and a real arch.
7. **Hunting lodge rebuild of the timber storey:** posts every 2 to 3, braces, jetty, eaves with brackets (overhang now
   0), the tower 3 over the ridge, furnished chambers. Lifts 11 cultures at once.
8. **Keep interiors:** storeys 4 high (34 % of the floor now has heads at the ceiling), a real 5x5 bailey (no 3x3 in
   84/84), fewer cramped rooms.
9. **Great hall dress:** shingle the northern roof warm brown (now pale white), cut the dragon heads to a carved
   one-block curl, and make the alpine balcony reachable (an inner gallery and stair) or remove the fake storey.
10. **Stables and gatehouse facades:** noble archetypes take the gentry dressing (tundra stables are now sod and moss
    with a grass-block lantern); break the 150 to 208-cell blind walls with buttresses, vents and loft doors; mirror
    the gatehouse towers and roof the gate hall; calm the patchy karst and mediterranean fort palette.
