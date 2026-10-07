# VOID Hearth — in-game playability walkthrough

Run 2026-10-07. Tour code: `hearth/src/gametest/java/dev/voidmc/hearth/gametest/walk/` (`WalkthroughTour`, `WalkModel`, `Audit`), committed on branch `hearth-audit-game` (commit 3f769836). Run it with `JAVA_TOOL_OPTIONS="-Dhearth.tour=walk -Dhearth.walk.out=<dir>" ./gradlew runClientGameTest`. Raw output for every building: `audit/walk/walk_results.md` (rooms, walks, defects with coordinates), `audit/walk/walk_summary.tsv`, and 147 JPEG screenshots in `audit/walk/` (12 MB).

## What was walked and how

- **Part A, built:** 17 building types × 4 cultures (temperate, mediterranean, northern, desert), 68 buildings. They were built from the real blueprint service onto raised 10-block pads in a superflat world. The mine got a hill behind it to dig into. All buildings face south, from the archetype's own size range; tier 2, or tier 3 for the manor, keep, town hall and market hall. Types: cottage, farmhouse, townhouse, tenement, merchant house, inn, tavern, smithy, bakery, windmill, chapel, town hall, library, market hall, manor, keep, mine.
- **Part B, grown:** the real sim grew a temperate town, Bradfield, on the Earth world (seed 20261003) for 120 days, to 269 people, city tier and 661 plots, and the applier wrote it into the world. 16 of its built plots were walked (one of each kind first). Its embodied residents were checked at dusk, at midnight and in the morning.
- **Per building:**
  1. Every door, gate and ladder hatch a hand can open is opened.
  2. A model with the player's size (0.6 × 1.8, 0.6 step, 1.25 jump, climbs ladders, drops up to 3.5) searches from the street 2 blocks in front of the plot. It reads the blocks' real collision shapes and finds the cheapest path (Dijkstra; jumps cost more, so a path jumps only where it has to). From this come rooms (roofed cells joined at one level), doors, stair flights, head room and block light.
  3. **The folk's own pathfinder** (`FolkNavigation`, a `hearth:folk` entity created at the street) is asked for a path to every bed, job site, store, counter and work face.
  4. **The real client player** (`LocalPlayer.move`: the game's collision, 0.6 step-up, stairs; jumps and ladder climbs where the model needs them) is driven along the model's path to every room, bed, job site, store and stair top. The player is put back afterwards.
  5. First-person JPEGs with the HUD hidden: the main room, a bedroom and the stair.
- **Calibration rig, passed:** four oak stairs onto a platform, a ladder with a top exit, a full-block step (needs a jump), a dirt path before a slab (0.5625 rise) and a carpet beside a slab.
- **One quirk found in calibration:** walking at a tenth of a block per move with gravity, the player wedges on the slab-to-carpet edge. A single move of one block clears it. The walker therefore retries any move that fails in tenths as one move, and a stuck point is only recorded when both fail.
- **Castles were not in scope here.** The keep was walked as it stands (castles are being fixed on `hearth-w6-fix`).

## Results

| | Part A (68) | Part B (16) | Total |
|---|---|---|---|
| PASS | 19 | 8 | 27 |
| WARN | 20 | 4 | 24 |
| FAIL | 29 | 4 | 33 |

- **Player walks:** 1059 of 1059 driven paths completed, with **0 stuck points**. Wherever the model finds a way, the real player can walk it: stairs, slabs, carpets, doorways and ladders all pass with real collision.
- **Interior light:** 10,040 reached cells under a roof. 634 (6%) are below light 8 and **1** is at light 0 (temperate farmhouse loft corner 126,-49,200).
- **Stairs:** 11 of 484 flight steps cannot be climbed. 80 full-block steps on routes need a jump.

**What makes a building FAIL:**
- a bed, job site or store the player cannot walk to;
- a bed or job site the folk pathfinder cannot reach;
- a room with a bed, job site or store in it that cannot be reached;
- a door that opens into a wall or a drop.

### Pass/fail by building

| type | temperate | mediterranean | northern | desert |
|---|---|---|---|---|
| cottage | PASS | PASS | WARN (2 stair steps under a beam) | PASS |
| farmhouse | WARN (dim loft, 1 dark cell) | FAIL (herder's job site in the loft: folk can't reach) | FAIL (folk can't reach either loft bed or the herder's site) | PASS |
| townhouse | FAIL (folk can't reach the attic bed) | PASS | PASS | FAIL (4-cell bed nook unreachable) |
| tenement | FAIL (door opens onto a stair under a low step) | FAIL (3 top-floor beds: folk) | FAIL (5 top-floor beds: folk) | FAIL (door onto a stair; 2 beds: folk) |
| merchant house | FAIL (4 upper beds: folk) | FAIL (2 beds: folk) | FAIL (4 beds: folk) | FAIL (2 beds: folk) |
| inn | WARN (empty attic room unreachable) | WARN (4 attic rooms unreachable) | PASS | PASS |
| tavern | PASS | FAIL (entertainer's stage spot unreachable) | PASS | PASS |
| smithy | PASS | PASS | PASS | WARN |
| bakery | WARN | WARN | FAIL (oven nook with 2 job sites sealed) | FAIL (sealed oven nook; upper door onto a drop) |
| windmill | WARN (6 of 10 tower rooms unreachable) | FAIL (milling floor with job site unreachable) | WARN (7 of 11 unreachable) | FAIL (milling floor unreachable) |
| chapel | WARN (gallery stair: no head room) | WARN | PASS | PASS |
| town hall | WARN (upper stair: no head room, 6 steps) | WARN (7 jumps) | FAIL (6-cell room with meeting point unreachable) | WARN |
| library | PASS | WARN | PASS | WARN |
| market hall | **FAIL** | **FAIL** | **FAIL** | **FAIL** |
| manor | FAIL (1 bed: folk) | WARN | PASS | FAIL (7 of 8 beds: folk; sealed 4-cell store) |
| keep (castles WIP) | FAIL | FAIL | FAIL | FAIL |
| mine | WARN (folk can't reach 4 work faces) | WARN (same) | **FAIL** (whole gallery unreachable) | WARN (folk) |

**Part B (grown Bradfield):**
- **PASS:** carpentry, smithy, logging camp, sawmill, barn, hunters' lodge, shrine, apiary.
- **WARN:** builders' yard, storehouse, fishery, inn.
- **FAIL:**
  - croft: doorway at -1364,76,-1389 has no head room;
  - market stall: folk can't reach the merchant's spot;
  - reeve house: folk can't reach the reeve's job site at -1409,78,-1273;
  - bakery: door at -1303,74,-1293 is blocked by a smoker.

### Residents (Bradfield, 48 embodied near the player)

- **Dusk (13100):** 0 asleep, 11 home, 37 out. They were mostly still at supper ("EAT / going to sit"), which is expected.
- **Midnight (18250):** **29 of 48 asleep in a bed of their own home**, 0 in another bed, 5 awake at home, **14 away from home**.
  - The 14 are all "SLEEP / going to bed". **Five of them, from five different homes (cottage, monastery, hunting lodge, scribe house, hermit hut), stand within two blocks of -1345,83,-1324.** That is a shared stuck point on the way to bed, 7 blocks above the street (a terrace or stair street?).
  - Two townhouse residents (#209, #212) are stuck at y 83–84 near their houses.
  - Look at -1345,83,-1324 next.
- **Morning (4100, 60 s after the jump to 3500):** 11 of 28 workers at their work plot. Most of the rest are "on the way", so the time allowed was too short to judge. Two say "brewing" or "at the bench" while outside their plot's reach box. That may be the reach box being tight, not a defect.

## Defects, worst first

| # | sev | building / culture | where | what | likely cause |
|---|---|---|---|---|---|
| 1 | HIGH | **market hall, all 4 cultures** | upper floor y+5/6 (72–171 cells) and a ground room by the back wall (12–18 cells) | The guild room upstairs (hearth, table, 4–12 seats, meeting point, store) has **no way up**: no stair or ladder found by the model, the folk or the player. The ground-floor store room has no door. The temperate hall also has an upper door at 1462,-38,212 that opens onto air beside a hanging lantern. | The archetype draws the upper hall and store room but no stair, and no doorway into the store; the door to nowhere is likely a gallery door with no balcony. |
| 2 | HIGH | **merchant house (4/4), tenement (3/4), manor (temperate, desert), townhouse (temperate), farmhouse (med, northern), keep (all)** | upper-storey and attic beds, the herder's loft, the steward's top-floor office | **The folk pathfinder cannot reach beds that the player reaches** (59 HIGH folk failures in all). It stops on the floor below, beside the stair foot. Desert manor: 7 of 8 beds. Northern tenement: 5 top-floor beds. The midnight count matches: 14 of 48 residents not abed, several high up near their houses. | The upper flights turn without a landing, or are steep or ladder-and-hatch climbs. Example: the temperate merchant house (seed 39, 8×12) climbs from floor 9 to 13 by two north steps, then two south steps one block over. The player steps or jumps across; `FolkNavigation`/`ClimbNodeEvaluator` finds no node. Confirm with a live folk on one of these (coordinates in walk_results.md). |
| 3 | HIGH | **mine, northern** | gallery y-3 round 1798,-53,525 | The whole drift (29 cells, 4 work faces) cannot be reached from the pit head. In the other cultures the player reaches the faces but the folk pathfinder fails on 4 of 7 work-face targets. | The northern drift portal or entry step is cut at the wrong height for this hill. The folk failures are likely the 1-block steps down into the drift. |
| 4 | HIGH | **windmill, mediterranean and desert** (WARN in the other two) | milling floor y+1, 24 cells, with the miller's job site | The milling floor cannot be reached. Temperate and northern towers also have 6–7 of 10–11 levels unreachable (no POIs in them). | The tower's internal ladder or stair doesn't reach the first floor in those styles. The empty upper levels are sealed voids that should be filled or opened. |
| 5 | HIGH | **bakery, northern and desert** | 4-cell room at y+1 round 900,-49,525 / 898,-49,685 | The oven nook with 2–3 job sites, the counter and the store is sealed. The desert bakery also has an upper door at 898,-45,683 that opens onto a drop. | The nook is raised one block with no step, or is walled off by the oven run. The upper door has no stair or balcony behind it. |
| 6 | HIGH | **tenement, temperate and desert** | doors 341,-46,202 and 338,-38,691 | The door opens straight onto a flight of stairs (polished andesite / mud brick stairs), with a stair overhead within 2 blocks: no head room at the threshold. | A stair well laid across the doorway on an upper landing. |
| 7 | HIGH | **keep (castles WIP), all cultures** | wall-walk turret rooms y+5 (guard posts), steward's job site at y+15 | 1–3 guard-post rooms per keep cannot be reached. The steward's room cannot be reached by the folk. The temperate keep has an unclimbable step at 1698,-39,220. | The turrets are not joined to the wall walk, and the top stair is a ladder or turn without a landing. Being fixed on hearth-w6-fix. |
| 8 | HIGH | grown croft #9 / grown bakery / mediterranean tavern | -1364,76,-1389 / -1303,74,-1293 / 681,-49,366 | Croft: a doorway without head room. Bakery: the door is blocked by a smoker. Tavern: the entertainer's stage spot is enclosed. | Furniture placed in front of a door; a lintel too low over a raised threshold; a stage fenced off. |
| 9 | HIGH | desert townhouse / desert manor / northern town hall | 226,-45,689 (bed nook) / 1574,-48,686 (store) / 1241,-44,530 (meeting point) | Small 4–6-cell rooms with a bed, store or meeting point that can't be entered. | Alcoves raised a block, or behind furniture, with no step. |
| 10 | MED | temperate town hall, temperate chapel | stair 1244–1249,-34,204 (6 steps); chapel gallery 1123/1127,-41,210 | The stair tread sits under a ceiling less than 1.8 above it, so the upper flight can't be climbed. The room above is still reached another way. | The stair passes under the floor or roof beams above without a cut-out (the "no headroom" kind of defect). |
| 11 | MED | northern cottage | stair steps 0/10,-47,528 | Steps cannot be stepped onto from their foot (blocked approach). | A beam or post at head height in front of the flight. |
| 12 | MED | town halls (med 7, northern 6), temperate keep 6, chapel 4 | e.g. 1232,-49,361; 1247,-49,367 | Full-block steps with no stair on the way into rooms; the player must jump. 80 in total across all buildings, mostly at front thresholds and dais edges. | Raised floors or daises without a step block. |
| 13 | MED | inn (temperate 1, mediterranean 4 rooms), tavern, town hall, library, smithy | attic levels y+7/8 | Empty unreachable rooms with head room (attics and lofts with no ladder), 43 MED and 82 LOW in all. Harmless for play, but they read as "doors to nowhere" when seen through a window, and they cost hidden lights. | `Voids.finish` leaves them unsealed; `Lofts.ladder` was not called. |
| 14 | LOW | windmills (ground rooms 3–6), chapels (side aisles 4–7), keep upper rooms (min 2), northern chapel nave (16 of 32 under 8, min 2), temperate farmhouse loft (1 cell at 0) | various | Dim corners under light 8. Only one reached cell in all 84 buildings is at 0, so there are no real mob-spawn holes. | The auto-lighter skips small side rooms and lofts reached only by ladder. |

## What is good

- Every path the model found, the real player walked: 1059 of 1059. No stairs that the player can't climb when they have head room, and no doorways that pinch.
- Cottages, townhouses (mediterranean, northern), inns (northern, desert), taverns, smithies, libraries, northern chapel and manor, and most grown workshops passed outright: every room, bed and job site reachable, and well lit (rooms at 10–12).
- Lighting is strong overall: 94% of interior cells are at 8 or more.

## Best screenshots

- `audit/walk/temperate_merchant_house_bed.webp`: attic bedroom under the rafters, candles and chests.
- `audit/walk/temperate_library_stair.webp`: library stair between the bookcases, chandeliers.
- `audit/walk/grown_croft_#9_main.webp`: the grown croft's lived-in main room (hearth, table, shelves).
- `audit/walk/desert_manor_main.webp`: desert manor hall.
- Defect views: `temperate_market_hall_main.webp` (the ground hall; there is no way up), `temperate_keep_stair.webp`, `grown_sleeper_night.webp`.

## Limits of this run

- The folk path check uses a `hearth:folk` entity that is not added to the world, with its normal follow range (48). A failure means the pathfinder returned no full path. It does not prove that a live folk could never get there, but the midnight count of 14 of 48 not abed backs it up.
- Rooms are roofed cells joined at one level. A sealed attic void counts as an "unreachable room" (LOW/MED), not a defect in play.
- The morning work check allowed only 60 s after the time jump, so it doesn't support any conclusion yet.
- Part A uses flat pads and faces south only. Slopes are covered only by the grown Bradfield plots.
