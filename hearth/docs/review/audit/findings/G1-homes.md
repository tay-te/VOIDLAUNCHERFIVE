# G1 homes: visual and playability audit

Scope: the 22 `house/*` archetypes in `hearth/src/main/java/dev/voidmc/hearth/build/archetype/house/` (worktree
`hearth-audit`, commit 7c8a23b3). For each one I looked at its contact sheet (14 cultures, flat or river, seed 1007), its
cutaways and night panels, its 84 metrics rows, and its source. I checked the worst automated findings by eye. Two I
confirmed with walk maps (`-Preach`), and one I rendered on `steep`. Evidence I added is in `findings/G1-img/`. Sheet
paths below are relative to `audit/`.

Severity: **blocker** = broken or unplayable (a room, bed, workstation, door or POI nobody can use); **major** = clearly
not hand-built quality, or a real playability cost (drops, cramped, unreachable storeys); **minor** = polish.

Things that are fine across the whole group: no floating or unsupported blocks, no build errors, no `check_problems`
anywhere. There is no water under a roof and no lava. Light is good in every culture: zero dark room cells and every
night panel is lit. Material discipline holds in most dressings. One rule for reading the numbers: tropical and karst
show `windows_per_side = 0` and `blind_sides = 4` because their lattice and shutter openings are not glass. That is a
quirk of the metric, not a defect, and is not counted below.

## Grade distribution

| grade | archetypes |
|---|---|
| A | none |
| B+ | cottage, farmhouse, longhouse, stilt_house |
| B | rectory, round_house |
| B- | reeve_house, rowhouse, tower_house, hut |
| C+ | manor, merchant_house, scribe_house, shophouse, townhouse, cliff_dwelling, hovel |
| C | croft, hermit_hut, shack |
| D | tenement |
| F | courtyard_house |

14 blocker defects in 11 archetypes. Several of them share one root cause: doors placed onto cells nobody can stand on
(top fix 1).

---

## house/courtyard_house: F

Desert, karst and Mediterranean only. The idea is right: rooms round an open court and a blind street wall. As built,
the court is too small and is filled with furniture, so the house does not work.

- **blocker: the court is filled, so the wings can only be reached by climbing over flower pots.** Desert and
  Mediterranean, seed 0, all three terrains. Per case: 41-50 unreached furnished cells, a whole storey unreached, 8
  unreached doors, 4 beds with no free side, and 26 workstations (furnace, smoker, chest, barrel) unusable. The cutaway
  shows "no rooms" for both cultures because nothing inside is reached. Image: `sheets/house/house_courtyard_house_cut.webp`,
  `findings/G1-img/courtyard_desert_med.webp`. File: `CourtyardHouse.court()` (lines 521-567). On a 3-5 wide court it puts
  the fountain (cauldron on a wall) in the middle, pots on every other edge cell (`(x+z)%2==0`) and chairs every third
  side cell. The only check is `nextToDoor`, which looks at the cell beside a door, not at the path between doors.
  `courtStair` (634) also climbs one court wall. **Fix:** before dressing the court, compute paths from the vestibule's
  inner door to each wing door (`inner,cz0-1`; `mx,cz1+1`; `cx0-1,mz`; `cx1+1,rightDoor`) and `keep` them, the way
  `Rooms.Room.keep` does. Put pots and chairs only off those paths. Under 5x5, use a wall fountain (a cauldron or basin
  against the back wall) instead of a free-standing one. Make the court at least 5x5 by taking wing depth from the
  13x13 plot.
- **blocker: karst inner doors are blocked or unreached in every case.** Six blocked doors per case (`blocked_door@6/0/8`,
  `unreached_door@6/4/8`) and 4 cramped rooms. In the karst the halls stand apart and the sill and spirit-wall blocks
  (`court()` 539-548) sit against the door fronts. File: `courtOpenings` and the eastern branch of `court()`. **Fix:**
  run the same door-front check. Put the spirit wall one cell further in, or drop it when `cd < 5`.
- **major: the Mediterranean court reads as a roof with a hole, not a court.** The cloister roof's eaves are pulled "over
  the court by one" (`roof...`, line 460), and the stair-ring tiles leave a 1-2 cell opening. The front door is a bare
  slit in a flat wall (relief 0.00, flat patch 34). Image: `courtyard_desert_med.webp`. **Fix:** stop the eaves at the
  court edge and let the portico carry the overhang. Give the street front a door surround and a plinth, as
  `Fronts.doorCase` does for town houses.
- **major: the desert version is a plain box.** Relief 0.03, three blind sides, no overhang. The court is a small square
  hole in a flat deck. **Fix:** add vigas, canales and a rounded parapet (`homestead/Adobe`), a raised wind tower that
  reads from the street, and a gate with a timber lintel and studded doors.

## house/tenement: D

All 14 cultures. A strong concept: banded storeys, balconies, back galleries and a shared stair. Every case has broken
doors.

- **blocker: balcony and gallery doors open onto posts, rails or nothing.** 3-7 blocked doors in 84/84 cases, unreached
  doors in karst and tropical. The walk map at level 4 (`findings/G1-img/walk_tenement_temperate_l4.txt`) shows the front
  door at x=6 with solid on its outside, while the balcony cells beside it are walkable but unreached. The back-wall door
  at x=9 opens onto air: no gallery deck behind it. File: `Tenement.java` 296-308 ("doors onto whatever was built in
  front of the windows") only checks that the cell below is non-empty and not solid. `backGalleries` 347-352 places a
  door at the first back window column it finds, whether or not the gallery deck reaches it. **Fix:** see top fix 1.
  Place a door only if its outside cell has a standable deck under it and two air cells above it, with no post, rail,
  box or pot. Clear that column's window dressing. If the check fails, leave the window.
- **blocker: up to 4 workstations unusable.** 2/6 cases in 12 cultures. Likely the shop and workshop stations behind the
  shop windows (`shopWindows`) against the hall partition. **Fix:** `keep` the front cell of each station when
  furnishing the twin flats (`twinFlats`, ~line 440).
- **major: the top storey is an empty roof storey.** A purposeless room at `5/12/2` in every pitched-roof culture
  (temperate night panel, level 12: a bare floor with lanterns). The javadoc promises attic flats. **Fix:** furnish the
  attic flats (`Rooms.Type.BEDROOM` x2 plus a hearth) with the dormers as their windows, or drop the roof storey's floor.
- **major: the steep-roof dormer rows come out broken** in northern, tundra, moorland, steppe and bayou: the roof is
  patched with stair fragments and holes at the gable. Image: `findings/G1-img/tenement_northern_alpine_roof.webp`.
  File: the tenement's two-row dormer plan through `TownHouses.pitched` / `Roofs.Plan.dormer`. **Fix:** allow a second
  dormer row only when the slope above the first is 3 or more blocks deep. Never let dormer cheeks meet a party gable.
- **major: drops from flat roofs.** 43-54 fall-edge cells in desert, Mediterranean, mesa, karst and tropical (roof garden
  and stair-house deck). **Fix:** a parapet or rail on every walkable deck edge (top fix 6).
- **minor:** 7-9 rooms per case with no 3x3 of clear floor. Flats are corridors. Twin flats need 5+ inner depth.

## house/croft: C

All 14 cultures. A good longhouse-and-byre plan with a garden, but one storey cannot be reached and the plan is
copied between cultures.

- **blocker: the hayloft ladder lands where nobody can step off.** Seed 0, all terrains, in temperate, wisteria, alpine,
  Mediterranean, steppe and karst. With seed 1 east the same happens in Mediterranean. 6 unreached furnished cells and
  the loft storey unreached. The walk map (`findings/G1-img/walk_croft_temperate_loft.txt`) shows the ladder's hole at the
  eaves corner, only diagonal to the walkable loft cells. File: `Croft.loft()` 455-461 cuts the hole at
  `(p2+1, wid-2)`, beside the side wall where the roof leaves less than 2 blocks of headroom. **Fix:** put the hole under
  the ridge (`lv = (wid-1)/2`) and keep one loft cell orthogonally beside the ladder top clear, with 2 blocks of air.
- **major: blocked door at `2/0/4`.** 5/6 cases in maritime, moorland, northern and tundra. This is the hall/byre
  partition door against the hearth or chimney breast. **Fix:** run the door-front check (top fix 1) in the partition
  placement.
- **major: one plan reskinned in 5+ cultures** (`culture_twin` 0.98-1.00: alpine, Mediterranean, temperate, steppe,
  wisteria; northern and tundra 1.00; desert and mesa 1.00). A Mediterranean or steppe croft should not be a Bohemian
  longhouse. Image: `sheets/house/house_croft.webp`. **Fix:** per-culture variants. Mediterranean: a masseria-style
  single storey with an external stair to a loggia. Steppe: a compound of round houses (reuse `RoundHouse` parts).
- **major: desert and mesa crofts are white boxes.** A parapet cube with warped-wood shutters and a reed shade. Relief
  0.11, flat patch 49-52, a roof hole. Image: `findings/G1-img/croft_desert_box.webp`. **Fix:** top fix 5.
- **minor:** fall edges 12-19 on bayou, tropical, alpine and karst (stilts and steps without rail). Headroom 0.35-0.52 in
  bayou and tropical.

## house/hermit_hut: C

- **major: almost no floor inside.** 1-2 free room cells: the bed, table and stores fill a roughly 3x3 interior. The
  cutaway shows the bed filling the room. Image: `sheets/house/house_hermit_hut_cut.webp`. **Fix:** grow the body to the
  7x9 plot's width (5x5 inside) or drop the table for a shelf and stool. Keep a 2x3 walkway.
- **major: windowless in all 14 cultures** (`blind_sides` 4). A hut in the woods would have one shuttered window by the
  bed and one by the hearth. File: `HermitHut.java` has no window pass. **Fix:** `Openings.window(..., PLAIN)` with
  shutters on each long side.
- **major: one plan in 9 cultures** (`culture_likes` 9, twins 0.98-1.00). **Fix:** vary it per culture. Northern: a
  turf-walled hut. Alpine: a stone-footed log hut with a woodshed lean-to. Karst: a bamboo pavilion with a deep pent roof.
- **major: tropical and bayou roofs are noisy.** Plank stairs patched with hay or brick, plus an odd stair ramp up the
  side. **Fix:** one thatch material (`habits.roof.thatch`) on the whole roof and the stair under a lean-to. The desert
  and mesa versions are white cubes (top fix 5). Image: `findings/G1-img/hermit_tropical_bayou_desert.webp`.

## house/shack: C

- **major: two beds and one free floor cell.** A 5x6 shack with a roughly 3x2 interior. The cutaway shows bed, stove and
  chest wall to wall. **Fix:** one bed (a poor household may share), or a 6x7 body.
- **major: windowless in all 14 cultures, and one plan in 9 cultures.** Relief 0.01-0.05. **Fix:** a lean-to woodpile,
  a patched door, one shuttered hole, and boards laid in two directions so walls are not one plane.
- **major: the desert and mesa shacks are a blank cube with a door** (detail score 47-51, the lowest in the group).
  Headroom 1.0 (heads at the ceiling) in 3/6 cases. Image: `sheets/house/house_shack.webp`. **Fix:** top fix 5, plus a
  4-high storey.
- **major: the yard water cauldron is unusable** in 1/6 cases in every culture (`station_unusable:water_cauldron@5/0/7`).
  **Fix:** keep its front cell free of the fence or garden.

## house/hovel: C+

- **major: a 3x3 interior with two beds.** 1-3 free cells. Image: `sheets/house/house_hovel_cut.webp`. A hovel is meant
  to be poor, but a player still needs to turn round in it. **Fix:** one bed against the wall and a straw pallet
  (carpet) as the second sleeper, or a 6-wide body.
- **minor:** windowless (plausible for a hovel, but give it a smoke hole or a single shutter). One plan in 7 cultures.
  Desert and mesa are cubes (top fix 5). Steppe is good (relief 0.42).

## house/hut: B-

- **major: cramped.** One room of 4-5 cells, `cramped_room` in 6/6 cases in 11 cultures. The steep render shows bed and
  table filling the interior. The cutaway sheet says "no rooms" in every culture. **Fix:** as for the hovel: one bed, a
  stool, and a 2x2 clear floor.
- **major: desert and mesa huts are a white cube with a reed shade** (relief 0.12, flat patch 18). Image:
  `sheets/house/house_hut.webp`. **Fix:** top fix 5.
- Otherwise the roofs read well per culture: the Bohemian shingle hip, the alpine deep eaves, the karst flared corners.

## house/cliff_dwelling: C+

Alpine, karst and mesa.

- **major: exposed blind walls.** The archetype leaves walls blind "where the ground stands over them". On flat land,
  and on `steep` where the rock behind is lower than the house, the back and sides are bare blind walls: the mesa
  version is a pink box standing two storeys proud of the slope. Detail score 56-63, flat patch 45-70. Image:
  `sheets/house/house_cliff_dwelling.webp`. **Fix:** a wall that the ground does not cover should get windows, vigas
  and a plinth like a front. Or the planner should only offer `HILLSIDE` sites whose rise is at least the house height.
- **major: drops of 24-33 cells** from the roof deck and terrace (karst, mesa) where the parapet is open toward a stair
  street (`open_left/right`). **Fix:** close the parapet unless a neighbour terrace or landing really adjoins.
- **minor:** karst windows read as blind. Alpine has a sealed void (7 cells) and an empty room.

## house/manor: C+

All 14 cultures. A compact double pile with a portico balcony, cross gable, dormers and a walled garden. Respectable,
but small for a manor, copied in 4-8 cultures, and the balcony door does not work.

- **blocker: the portico balcony door is blocked** in 84/84 cases (`blocked_door@9/5/7` or `9/6/7`). The balcony carries
  the VIEWPOINT POI. The zoom shows a potted shrub (the upper window's flower box) right in front of the door. File:
  `Manor.portico()` calls `TownHouses.galleryDoor` (TownHouses.java:888). That only checks the deck below and never
  clears the window dressing already drawn in that column. Image: `sheets/house/house_manor.webp`. **Fix:** top fix 1.
- **major: drops of 16-60 cells** (karst 60, 6/6) and cramped rooms (`14/1/9`, 2 per case). Headroom 0.39-0.42 in
  alpine and Mediterranean. **Fix:** rails on the side terraces and wing roofs. Put the 2 cramped side rooms together as
  one study.
- **major: one plan in 4-8 cultures** (twins 0.90-1.00: maritime and moorland 0.998). For a gentry house, the wings,
  roof form and garden should change per culture: a desert palace court, an alpine patrician house with an oriel,
  karst Hui halls.
- **minor:** the dormer on the cross gable is a lump. The attic (level 9/10) is a bare floor with a few items.

## house/merchant_house: C+

All 14 cultures. A tall merchant's house, 8x11 and 4 storeys, with a busy front.

- **blocker (karst):** the upper door at `1/5/1` is blocked and unreached in 6/6 cases, with 54 fall-edge cells and a
  barrel unusable at `1/13/2`. Other cultures have the same blocked door in 3/6 cases (alpine, maritime, moorland,
  northern, steppe, temperate, tropical, tundra, Mediterranean). File: the balcony or gallery door through
  `TownHouses.galleryDoor` / `Fronts.plainDoor`. **Fix:** top fix 1. Rail the karst gallery.
- **major: huge flat side walls.** Flat patches of 99-125 (steppe 125, moorland 115, Mediterranean 110, northern 106). In
  the sheet, northern's side is one 4-storey grey panel between two posts. These are party walls, but on a bare plot or
  at a row's end they are seen. Image: `findings/G1-img/merchant_northern_desert_steppe.webp`. **Fix:** top fix 8.
- **major: one plan in 10 cultures** (`culture_likes` 10). The steppe version, a 4-storey gabled mud-brick block, is not
  steppe. Its style is round houses and Sahel earthen halls, which would be flat-roofed with torons and a roof terrace.
  Desert and mesa walls are noisy (packed-mud blotches in mud brick).
- **major:** desert and mesa have 38 fall-edge cells (roof terraces). The yard water cauldron is unusable in 1/6.
  Relief 0.06-0.10 everywhere except tropical.

## house/scribe_house: C+

- **major: blocked upper door `4/4/1`** in 3/6 to 6/6 cases in 10 cultures (balcony or oriel door). **Fix:** top fix 1.
- **major: sealed attic of 30 cells**, a whole storey unreached (east seeds, temperate, alpine, tropical). It is dark,
  so mobs can spawn up there. **Fix:** top fix 9 (a garret study reached by the stair core, `MerchantHouse.loftLadder`
  as SeatHouses does).
- **major: one plan in 12 cultures** (`culture_likes` 12): a 6x10 tower of 4-5 storeys everywhere, including steppe and
  desert. The karst white walls and dark tiles are a fair Huizhou read, but without horse-head gables. **Fix:**
  per-culture forms (a karst scholar's study round a light well, steppe and desert flat-roofed).
- **minor:** tropical and karst headroom 0.26-0.27, cramped rooms, drops of 18-26. Desert and mesa drops of 34-42.

## house/shophouse: C+

- **major: flat fronts.** Relief 0.01-0.07 in 12 cultures (temperate 0.02, wisteria 0.01). Shop windows and an awning
  only; no plinth, string course, sill or frame depth on the body. Image: `sheets/house/house_shophouse.webp`. **Fix:**
  give `Facade.plan`/`Walls.storey` the town-house dress (`TownHouses` corner strips, a cornice at each floor), a jettied
  or set-back upper storey, and a shop counter that projects onto the street.
- **major: unreached attic of 26 cells** (bayou, east seeds). Desert, mesa and karst have 25-34 fall-edge cells on the
  roof terrace. Alpine and northern have blocked upper doors `3/5/0`. **Fix:** top fixes 1, 6 and 9.
- **minor:** one plan in about 10 cultures. Side party walls up to 76.

## house/townhouse: C+

- **blocker (tropical, karst): unreached and blocked doors** (`1/5/1`, `1/5/2`, 3/6-6/6). Tropical also has 38
  fall-edge cells and headroom 0.47. The other cultures (alpine, bayou, maritime, Mediterranean, northern) have 1-2
  blocked upper doors. **Fix:** top fix 1. Tropical and karst need 4-high storeys.
- **major: alpine sealed attic of 34-47 cells**, the storey at y=13 unreached in all 6 cases. **Fix:** top fix 9.
- **major: flat side walls of 112-115** (northern, moorland) and one plan in about 5 cultures. **Fix:** top fix 8.
- **minor: the ground floor is a big hall with little in it.** In the temperate and northern level-0 cutaways it is a
  large bare room (furniture per cell 0.29-0.47). Make it a shop or workshop with a counter, or a kitchen and a parlour.

## house/rowhouse: B-

- **blocker (karst): unreached door `1/4/1`** in 6/6 cases, with 12 fall-edge cells. Tropical has the same in 3/6.
  Alpine, desert and mesa have a blocked upper door in 6/6. **Fix:** top fix 1.
- **major: one plan in 4-11 cultures** (Mediterranean 11). **Fix:** per-culture variants.
- **major: sealed attic** (temperate, east seeds, 14 cells). Headroom 0.31 in alpine and temperate.
- Good: varied roofs, chimneys on the party walls, readable fronts.

## house/reeve_house: B-

`ReeveHouse` and `Rectory` are thin wrappers on `SeatHouses`.

- **blocker (karst, tropical): unreached door `4/4/2`** in 3/6 cases, with 16-36 fall-edge cells. A blocked `4/4/2`
  door in 3/6-6/6 cases in 10 cultures. This is the first-floor landing door off `StairCore`. **Fix:** top fix 1
  (`SeatHouses` partitions).
- **major: it looks almost identical to the rectory.** Same body, roof and windows (compare the two sheets). Only the
  notice board and bench differ. **Fix:** give the reeve an office front (a wide door with a hood, a tithe-barn
  outshot, a fenced pound or a weigh beam). Give the rectory a porch and a walled herb garden toward the church.
- **minor:** one cramped room `3/0/3` in every case (the office in front of the partition).

## house/rectory: B

- **major: hole in the wisteria roof.** A 2-cell opening near the hip apex shows sky and a lit interior. There is no
  chimney on that version, so it is probably the stack's cut or a dormer cut without its cheeks. `roof_hole_cells`
  misses it because it is not walkable. Image: `findings/G1-img/rectory_wisteria_roof_hole.webp`. File: `SeatHouses`
  193-200 (`Chimneys.stack` skipped by chance) or `TownHouses.pitched` dormers. **Fix:** re-raster the roof over a hole
  whose stack was skipped, and add a roof-hole check for non-walkable cells.
- **minor: northern white sills.** Quartz or birch slabs under every window, scattered over the grey wall, read as
  noise. Use the frame wood for the sills.
- **minor:** a cramped room `3/0/4` in every culture, drops of 14-28 (bayou, karst, tropical). It shares its plan with
  the reeve house (see above).

## house/tower_house: B-

Alpine, karst, Mediterranean, moorland. The moorland version is a convincing Scottish tower house.

- **blocker (karst): unreached door `1/4/1`** in 6/6 cases, with headroom 0.48 and 2 cramped rooms. **Fix:** top fix 1,
  and 4-high storeys.
- **major: no rail on the external entrance stair.** 8-18 fall-edge cells in all four cultures. **Fix:** a parapet wall
  or rail on the open side of the forestair.
- **minor:** alpine has a sealed top storey of 15 cells (east seeds). The alpine body is a single slab of dark boards
  (flat patch 49); give it a stone ground storey and a gallery.

## house/round_house: B

Steppe and tropical. A good cone roof and finial, and it reads well.

- **minor:** the steppe interior is crowded: beds side by side and stores round the wall, no 3x3 of floor. Tropical on a
  slope has a sealed void of 8 under the floor, an empty room and headroom 0.63 (1/6). The steppe `detail_score` of 0
  comes from the facade metric not handling round plans, not from the building.

## house/cottage: B+

All 14 cultures. The best-varied home: a Bohemian steep boarded gable on plaster and fieldstone, a turf-roofed
northern house, a tropical stilt cottage, a karst hipped pavilion, mesa and desert stepped adobe.

- **blocker (Mediterranean): an unreached furnished pocket** of 3 cells at `6/0/6` (seed 0, all terrains). A nook
  behind a partition with no door. File: `Cottage.java` room split. **Fix:** put a door in the partition, or merge the
  nook into the room.
- **major: drops of 39-44 cells** on desert and mesa roof terraces, with a 1-cell roof hole (5/6). **Fix:** top fix 6.
- **major: low headroom.** Tight-headroom share 0.42-0.62 in tropical, maritime, steppe, moorland, wisteria and bayou.
  **Fix:** 4-high storeys (3 air) in `HouseBuilder` for those habits.
- **minor:** one blocked door in 1/6 cases in 7 cultures. The temperate roof is very tall for the body (fine for
  Bohemia, but pull the eave down a course).

## house/farmhouse: B+

All 14 cultures, and properly varied: Bohemian, alpine with deep eaves, a Mediterranean masseria with a walled yard, a
desert court farm, a karst pavilion.

- **blocker (bayou, tropical): a bed with no free side** at `6/2/10`, in 6/6 cases (raised floor). File:
  `Farmhouse.java` 874-875, `FarmParts.ensureBeds` placed after the stores fill the one room. **Fix:** place beds first
  with `keep` on one long side, or run `ensureBeds` with the connectivity check `Rooms.furnish` uses.
- **blocker (mesa): dead ladder** at `11/8/6` in 4/6 cases (the roof ladder ends under a parapet or slab). **Fix:** cut
  the parapet where the ladder arrives, as `courtStair` does in the courtyard house.
- **major: drops of 82-86 cells** on desert and mesa roof decks, the largest in the group, plus 1-2 roof holes. File: the
  court farm's terrace (`Farmhouse.java` 397-470). `Adobe.parapet` is applied to the side ranges but not to the house
  deck's back and side edges. **Fix:** top fix 6.
- **minor:** the steppe farmhouse is small for a 14x13 farm (a stepped mud roof and a hay stack). Sealed voids of 7-12
  in the attics of 6 cultures.

## house/longhouse: B+

Moorland, northern, tundra. A long hall with two rows of posts, wall benches, box beds and a central hearth. The
interior is good and the turf roofs are good.

- **minor:** the long side walls are fairly plain (flat patch 34-37 in northern and moorland, 2 blind sides). Add a
  porch or a byre door, wall posts at every bay and a woodpile against the wall. There is a 5-cell sealed void in the
  roof.

## house/stilt_house: B+

Bayou, maritime, tropical. A raised room inside a wraparound gallery over a store, with good roofs.

- **major (tropical): heads at the ceiling.** Tight-headroom share 1.0 in 4/6 cases. **Fix:** a 4-high storey.
- **minor:** gallery edges have gaps (9-10 fall-edge cells). A barrel is unusable in maritime (`9/2/2`, 3/6). Tropical
  and maritime shutters are warped wood (teal). This is acceptable as painted joinery, but the style bible bans cyan
  cloth, so check that the art director agrees.

---

## Cross-cutting notes

- **Desert and mesa small homes share one weak plan:** the hut, hovel, hermit hut, shack, croft and cliff dwelling all
  render as a plain cube with a parapet and a reed shade (detail 47-69, overhang 0, flat patches 18-52). The adobe kit
  can do far better, as the cottage and farmhouse show.
- **Copied plans:** `culture_likes` of 9-12 for the hermit hut, shack, merchant house, scribe house and rowhouse, and
  4-10 for the manor, tenement, shophouse and croft.
- **Steppe town homes are off-culture:** the merchant house, scribe house, townhouse and tenement are 4-5 storey gabled
  mud-brick blocks. The style bible's steppe is round houses and flat-roofed Sahel earthen halls.
- **Castles** are being fixed on `hearth-w6-fix` and are not in this group.

## Top 10 fixes for G1, ranked by impact

1. **Only place a door where both sides can be stood on.** In `Fronts.plainDoor`, `TownHouses.galleryDoor`
   (TownHouses.java:888), `Tenement` 296-308 and 347-352, `Partitions.door` (used by `CourtyardHouse`, `Croft`,
   `SeatHouses`), and the stair-core landing doors: require a standable floor and 2 air on both sides, with no post,
   rail, box or pot. Clear the window dressing already drawn in that column. Leave a window if the check fails.
   Clears the blocked and unreached doors in tenement, manor, townhouse, rowhouse, scribe house, merchant house,
   shophouse, reeve house, tower house, croft and courtyard house (karst): 8 of the 14 blockers, plus about 10 major
   door defects.
2. **Rebuild the courtyard house's court.** `keep` paths from the gate to each wing door before pots, chairs and the
   fountain. No free-standing fountain under 5x5. A court of at least 5x5. Fixes the only F: unreached wings, 4 beds and
   26 workstations.
3. **Move the croft's loft ladder** under the ridge with a clear landing cell (`Croft.loft` 455-461). Fixes an
   unreachable furnished storey in 6 cultures.
4. **Give every walkable roof edge a parapet or rail.** `Roofs.flat`, `HouseBuilder.chimneyFlat`, `Adobe.parapet` on the
   house deck, the tower house forestair, stilt galleries and cliff-dwelling terraces. Removes 30-86 drop cells per case
   in desert, mesa, karst and tropical (farmhouse, tenement, cottage, merchant house, townhouse, scribe house, manor,
   shophouse, cliff dwelling).
5. **Give the small homes real floor.** Hovel, hermit hut, shack and hut: grow the body to the plot or cut the second
   bed. A clear walkway of at least 2x3. One shuttered window per long wall.
6. **Desert and mesa small-home vocabulary.** For the hut, hovel, hermit hut, shack, croft and cliff dwelling: vigas,
   canales, rounded parapets, buttressed corners, a ramada or portal on posts, a banco, niche windows with grilles and
   two stepped volumes, instead of the cube.
7. **Per-culture plans for the copied archetypes** (merchant house, scribe house, rowhouse, shophouse, tenement, hermit
   hut, shack, manor, croft). Steppe, desert and mesa town homes should be flat-roofed Sahel, Yemeni or pueblo forms
   with torons, setbacks and roof terraces. Karst should use Hui horse-head gables or courtyard studies. Bayou gets
   galleries, tropical verandas.
8. **Dress exposed party walls and long blank sides** (merchant house up to 125 cells, townhouse 115, tenement 111,
   shophouse 76, longhouse and cliff-dwelling backs). When no neighbour is attached, add a plinth, string courses,
   corner posts or quoins, a chimney breast and one small window per storey. Raise the shophouse's facade relief from
   about 0.03.
9. **Use or fill the sealed attics.** The empty roof storey in the tenement, and attics of 15-47 cells in the townhouse
   (alpine), scribe house, shophouse (bayou), tower house and rowhouse: floor and furnish them with a stair or ladder as
   garrets, or fill or light them so mobs cannot spawn.
10. **Raise storeys to 4 high (3 air)** where heads touch the ceiling: tropical and karst towers, townhouses, rowhouses
    and scribe houses; tropical, maritime and steppe cottages; the tropical stilt house. In the same pass, fix the beds
    with no free side in the bayou and tropical farmhouse, the mesa farmhouse's dead ladder, and the hole in the
    wisteria rectory's roof.
