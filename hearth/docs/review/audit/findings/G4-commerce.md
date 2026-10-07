# G4: commerce, social life, landscape (visual and playability audit)

Scope: `archetype/commerce/**` (10) and `archetype/landscape/**` (9), 14 cultures each (caravanserai: 5 cultures).
Sources: contact sheets and cutaways in `../sheets/commerce`, `../sheets/landscape`, `metrics.csv` (all 6 cases per
culture), the archetype files and kits, the in-game walk of the temperate inn (`../walk/temperate_inn_*.webp`).
Evidence crops added in `G4-img/` (1.1 MB). Paths below: `src` = `hearth/src/main/java/dev/voidmc/hearth/build/`.

Severity: **blocker** = broken or unplayable, **major** = clearly not hand-built quality, **minor** = polish.
Case notation: `s0` = seed 1007 facing south, `s1` = seed 2007 facing east; x/y/z = audit `where` coordinates.

## Grades

| archetype | grade | blockers | one line |
|---|---|---|---|
| commerce/bank | C | 1 | portico pediment punches through the roof; bayou s1 press room unreachable; reads as a cottage |
| commerce/caravan_yard | C+ | 0 | same plan everywhere, clumsy shed roofs, sealed warehouse loft |
| commerce/caravanserai | D | 1 | blank 180-cell walls, a portal that is not monumental, gate room unreachable, 23 cramped cells |
| commerce/counting_house | C | 1 | a whole sealed storey on 7x13 plots, desert roof room blocked, ugly stacked gables |
| commerce/hostel | B | 0 | solid, plausible, plain by design; unrailed stairwell |
| commerce/inn | B- | 1 | good silhouette and ground floor; tropical/bayou room unreached, cramped guest rooms, unrailed balcony |
| commerce/market_hall | C | 1 | best silhouette in the group, but a counter blocks the only door to the stair in all 14 cultures |
| commerce/market_stall | C | 0 | the same stall in all 14 cultures, only the wood changes |
| commerce/tavern | B- | 0 | good tap room and cellar; tropical tap room bare, unreachable corner casks |
| commerce/thieves_den | B- | 0 | right idea (ordinary front, secret cellar); cellar sparse |
| landscape/bell | C | 0 | a 3-cell frame, identical in 13 cultures |
| landscape/festival_ground | C | 0 | 35x22 green with most of it empty; two booths, a maypole that reads as a tree |
| landscape/fountain | B+ | 0 | genuinely culture-specific and well made |
| landscape/garden | B- | 0 | good per-culture kinds; tundra conifers with bare trunk tips, tropical reads as wild scrub |
| landscape/lamp_post | B+ | 0 | varied, tidy, right scale |
| landscape/park | C+ | 0 | over-planted to a solid canopy; conifer trunk tips; same layout everywhere |
| landscape/plaza | C+ | 0 | noisy paving in temperate, five trees fill the centre, little to gather round |
| landscape/statue | D | 0 | the human figures are flat 1-deep slabs that do not read as people |
| landscape/well | B | 0 | good variety; gem-like finial on the desert kiosk, lopsided roofs on some |

Distribution: A 0, B+ 2, B 2, B- 4, C+ 3, C 6, D 2. Blockers: 5 (market_hall, bank, counting_house, caravanserai, inn).

---

## commerce/bank: C

Small (12x10 preview, one storey at depth 10) hipped box with rusticated stone; "the most solid building on the square"
reads as a cottage in most cultures.

1. **blocker**: bayou, s1 on all three terrains: the flight to the press room ends at a wall/ceiling
   (`bad_flight@10/3/10`), so the whole upper storey is unreachable: 54 furnished cells, anvil, blast furnace and lectern
   unusable (`station_unusable:anvil@10/8/5`). Tropical s1 has the same bad flight (upper reached another way).
   File: `src archetype/commerce/Bank.java` (stair at `sx = vaultLeft ? ix1 : ix0`, `Storeys.flight(d, body, 0, sx, iz1, FRONT)`
   on a stilt base with `zF = 3`). Fix: on raised plots check the run fits (`iz1 - iz0 + 1 >= h0`) and cut the stairwell
   in the press-room floor along the run; otherwise turn the flight (`Dirs.LEFT/RIGHT`) or use `Stairs.switchback`.
2. **major**: the door aedicule's pediment collides with the eaves: temperate's roof is chewed away over the door,
   wisteria/northern/tundra/moorland/maritime/mediterranean show three loose stone slabs lying on the roof slope.
   Image `G4-img/bank_aedicule_through_roof.webp`. File: `Bank.java` (`Portico.build(d, t, cx-2, cx+2, z0-1, z0-1, base,
   min(4, h0-1), ...)`) with the roof eave at `plate`. Fix: cap the aedicule's top at `plate - 1` (pediment under the
   eave line), or bring it forward one row so its pediment stands clear of the eave and returns into a small cross gable
   (`Roofs.plan(...).crossGable(FRONT, cx, 5)`).
3. **major**: desert/mesa: the "little dome" is a stepped ziggurat of light-grey blocks with a gold block on top,
   clashing with mud brick. Image `G4-img/desert_stepped_domes.webp`. File: `src kit/civic/Adobe.java` `dome(...)`.
   Fix: see top fix 6.
4. **major** (all temperate-family): one storey on the 9-11 deep plots the bank mostly gets
   (`twoStoreys = dp - zF >= 9`). Fix: allow two storeys from `dp - zF >= 7` with a 3-high upper floor (a counting
   loft), or raise `h0` to 6 with a mezzanine; add a string course and quoins at both storeys (the javadoc promises them).
5. **minor**: desert front is a patchwork of pink plaster loss, protruding plank shutters and orange carpet steps:
   noisy. Fix: reduce the plaster-loss patch weight for civic buildings in `desert.json`/`mesa.json` variants.

## commerce/caravan_yard: C+

Central warehouse with open front, two open sheds, a fenced pen with troughs and hay. 6.6 cultures alike on average.

1. **major**: the warehouse loft (26 cells at y=5, all cultures but desert/mesa) is a sealed void although the front
   carries a hoist; nobody can get up. `sealed_void@9/5/13`. File `src archetype/commerce/CaravanYard.java`. Fix: a
   ladder or straight flight inside the warehouse to a boarded loft with sacks/hay (`Floors.boards` + `Rooms.furnish
   STORAGE`), or drop the loft floor so the warehouse is open to the rafters.
2. **major**: the side sheds' roofs are flat stair bands over heavy 2x posts; the left shed's roof in temperate is a
   broken L of stairs. Image `G4-img/caravan_yard_shed_roofs.webp`. Fix: build the sheds with `Roofs.shed(...)` lean-tos
   falling away from the yard (1 block overhang, brackets), posts as fences/logs not 2-wide.
3. **major**: the same layout in every culture (sheds both sides, pen front right). Fix: by seed mirror the plan, swap
   the pen and the booth, in the dry south use a walled yard with an arcade, in the north a log fence and turf roofs.
4. **minor**: the water cauldron in the pen is boxed in (`station_unusable:water_cauldron@14/0/3`). Fix: leave one
   free side toward the pen gate.
5. **minor**: the javadoc's wagons (tilts, loads) do not read on the 16x18 preview; add one wagon (cart body of
   trapdoors on wheels of dark logs, white wool tilt) in the yard.

## commerce/caravanserai: D

Five cultures (desert, mediterranean, steppe, karst, mesa). Image `G4-img/caravanserai_walls_portal.webp`.

1. **blocker**: the room over the gate is furnished but unreachable and its door is blocked
   (`unreached_furnished_room@12/4/3`, `blocked_door@13/4/5`) in all five cultures, 3/6 cases; the gate's two door leaves
   are never walked to (`unreached_door@13/0/1`, 6/6). File `Caravanserai.java` (gate block, lines ~195-215; the
   gallery ring). Fix: open the gate room onto the gallery (a door in its back wall at the gallery level, clear cell in
   front); hang the gate as a proper pair of doors at `cx-1, cx` (or fold the leaves against the passage walls as
   trapdoors) instead of door-air-door.
2. **major**: outer walls are 160-180 cells of one block with slit windows (relief 0.02-0.05), corner towers stand one
   block proud, the "great portal" is a 5-wide stone frame barely taller than the wall. Real caravanserais are blank
   walled but carry a monumental pishtaq (portal 2-3x wall height with a deep arched iwan), round or polygonal corner
   towers and half-round buttresses along the walls. Fix in `portal(...)`: 7 wide, rising to `plate + 5`, a recessed
   pointed arch 2 deep with muqarnas-ish stepped stairs, inlay band; towers: 3x3 rounded (stair corners), 3 above the
   parapet; a buttress every 6 cells; a string course of the accent at the gallery floor.
3. **major**: 23 cramped cells (each a 3x3 with bed, chest, rug: < 6 free cells) and 23 rooms without a 3x3; corner
   cells are unlit sealed voids (black squares in the night cutaway). Fix: merge pairs of cells into 3x6 rooms with two
   beds; make the four corner tower rooms reachable (door from the gallery corner) and furnish them as the keeper's
   room, kitchen store, etc.
4. **major**: identical plan in all five cultures (culture_likes 4). Karst (yellow walls, green ring roof) and steppe
   (dark brick, plank roof) are reskins. Fix: steppe as a lower mud-brick han with a flat roof and a felt-awning
   gallery; karst as a courtyard inn with a raised tiled gatehouse; mediterranean with a loggia above the gate.
5. **minor**: gallery edge 9 unrailed drop cells (`fall_edge@20/4/22`) where the stair arrives. Fix: rail the opened
   parapet cell's sides.

## commerce/counting_house: C

Image `G4-img/counting_house_roofs.webp` (northern, steppe, temperate).

1. **blocker**: desert s0 (all terrains): the roof room in the wind tower is furnished but its door is blocked and
   never reached (`blocked_door@4/8/7`, `unreached_furnished_room@5/8/8`). File `CountingHouse.java` (flat-roof
   tower). Fix: keep the roof-access stair's landing clear of the tower door, or move the door to the deck side the stair
   arrives on.
2. **major**: temperate, moorland, maritime on the 7x13 plot (s1): the whole upper storey (45 cells) is a sealed void:
   a two-storey facade with nothing upstairs and no stair; the javadoc's office and stores are missing. Fix: find why
   the flight is skipped on 13-deep plots (storeys default vs `Storeys.flight` fit) and either add the stair or draw a
   one-storey house with a loft.
3. **major**: steep gable roofs (northern, tundra, steppe, bayou) rasterise as stacked full blocks with gaps and moss
   between tiers; the front gable is not filled cleanly. It reads as stacked planks, not a roof. Fix: cap the pitch at
   1.0 for 7-wide bodies, or have `Roofs.build` fill the gable wall and use stairs on every course.
4. **major**: big flat side walls (flat patch 34-91, mediterranean/steppe/desert worst) with one pane window per
   bay. Fix: `Facade.plan` density 0.85+ on the long sides, sills/lintels (`Openings.window ... DRESSED`), a plinth and a
   string course; party walls only where `party_left/right`.
5. **minor**: karst: no furniture per room cell (0.0) and no windows (blind_sides 4). Fix: lattice windows on the
   front, desk and shelves per the javadoc.

## commerce/hostel: B

Long two-storey range, refectory below, dormitory above, stable lean-to behind. Plain is right for the type.

1. **minor**: the stairwell on the upper floor has no guard (`fall_edge@9/6/6`, 4 cells, all cultures). Fix: in
   `Storeys.flight` (or the archetype) put a fence/trapdoor rail round the stairwell's open sides.
2. **minor**: karst: tight headroom 29% of the floor, 32 unrailed fall cells (balcony). Fix: add 1 to the karst upper
   storey height and rail the balcony (`Porches.balcony` with railing).
3. **minor**: desert/mesa twins (likeness 1.0), lean-to roof of loose slabs at the side reads unfinished. Fix: a
   proper ramada (poles + slats) or a flat-roofed outbuilding.
4. **minor**: facades flat (relief 0.05-0.10): add shutters/flower boxes upstairs by habit.

## commerce/inn: B-

Good silhouette (cross gable, two stacks, carriage arch, jetty), lively forecourt; ground floor tap room works; the
in-game walk passed 16/16 paths.

1. **blocker** (small): tropical and bayou, 3/6 cases: a furnished room is unreached (`unreached_furnished_room@10/7/9`,
   3 cells) and a storey is unreached in 1 case. File `Inn.java` (raised branch). Fix: keep the upper flight's landing and
   the door of that room joined (`room.keep` at the door), check the stair on stilts reaches `body.floorY(1)`.
2. **major**: guest rooms are crammed: bed, chest, wardrobe, table and chair in about 7 cells
   (`../walk/temperate_inn_bed.webp`; cramped/no-3x3 rooms in every culture). Fix: fewer pieces per guest room (bed,
   chest, one chair) via `Rooms.furnish(..., BEDROOM, 1, ...)` with a free 3x3; or two rooms instead of three on 12 wide.
3. **major**: karst balcony and some upper floors unrailed: karst 55-64 fall cells on s1, others 12 (`fall_edge@9/12/6`).
   Fix: railing on the balcony (`Porches.balcony` railing on) and a guard round the stairwell.
4. **minor**: an empty 16-cell attic room at y+7 nobody can reach (walk "NOT reached"); add a hatch ladder or fill it
   with joists/storage so it is clearly roof space.
5. **minor**: desert and mesa are the same building (likeness 1.0); a bed/table set on the roof terrace with no shade
   frame on mesa. Fix: mesa variant with a ramada on the roof, different bay rhythm.
6. **minor**: in game the carriage passage door renders teal (copper-like) in the temperate brick variant
   (`../walk/temperate_inn_main.webp`): check which door block the partition gets.

## commerce/market_hall: C

The best silhouette in the group (arcaded ground floor, hall above, bell cupola, stepped gable in temperate), and the
upstairs guild hall is well furnished. But:

1. **blocker**: in all 14 cultures, 84/84 cases, a merchant's counter stands directly in front of the only door from the
   market into the back bay (`blocked_door@9/1/15`): the stair and stores are reached only by climbing over the counter.
   Image `G4-img/market_hall_backbay_door_blocked.webp`. File `src archetype/commerce/MarketHall.java`: `doorX = x1 - 2`
   equals the right counter row `cxRow = x1 - 1 - lane` and the last counter run ends at `zb - 1`. Fix: put the door
   on the aisle (`doorX = cx`, there is a pier only at mid-columns), or shorten the counter run touching `zb` by one
   (`b = Math.min(b, zb - 2)`) when `cxRow == doorX`.
2. **major**: desert/mesa: the "roof of little domes" is a single grey stepped ziggurat with a gold block. Image
   `G4-img/desert_stepped_domes.webp`. File `MarketHall.domes(...)` / `Adobe.dome`. Fix: top fix 6; two or three
   domes as promised.
3. **minor**: the hoist door high in the temperate gable opens on a sealed roof void (`unreached_door@6/13/2`). Fine
   from outside; make it a door into a boarded loft reached by a ladder from the hall, or a shuttered opening
   (trapdoors) so it is not a dead door.
4. **minor**: slope cases: 37 unrailed fall cells along the raised plinth's downhill edge (`fall_edge@12/1/19`).
   Fix: a low wall/rail on plinth edges over 2 high.
5. **minor**: karst/tropical: cramped stalls (5-6 rooms without a 3x3). Fix: wider aisle (`lane = 2`) on 13+ wide plots.

## commerce/market_stall: C

Image `G4-img/market_stall_identical.webp`.

1. **major**: the same stall in all 14 cultures (culture_likes 13): plank canopy, cake, a white block, one basket;
   only the wood colour changes. The worked example promises linen canopies and striped canvas in the south. File
   `MarketStall.java` canopy helper. Fix: canopy in the culture's cloth colours (`Signs.clothColours`) as carpets on
   fences/slabs, thatch in tropical, curved board roof in karst, striped wool in desert/mesa/mediterranean; vary trade
   goods by seed (the preview seed always shows the bakery).
2. **minor**: the free-standing white block in front of the trestle reads as a stray block; make it a sack (wool on a
   slab) behind the trestle or a crate (barrel).

## commerce/tavern: B-

Low lofty tap room open to the rafters, cellar of casks, bar, dice table, stage; good variety of roofs.

1. **major**: tropical (and bayou): the raised tap room is nearly bare: 10 job/furniture blocks vs 27 elsewhere, no
   windows counted (blind 4), veranda edges unrailed (9-18 fall cells), and on hills s1 every floor cell has head-at-
   ceiling headroom (tight 1.0). Image `G4-img/tavern_tropical_bare_taproom.webp`. File `Tavern.java` raised branch.
   Fix: furnish the raised room as `DINING` like the others, rail the veranda, add one block of storey height on stilts.
2. **minor**: cellar corner casks have no free face (`station_unusable:barrel@9/-4/10`, all non-raised cultures):
   corner cells get barrels on both walls. File `src kit/sociallife/Cellar.java` `stock(...)`. Fix: skip the cell where
   two walls meet (or put a crate/hay there).
3. **minor**: `unreached_levels=1` at y=3 with a "furnished room" (`unreached_furnished_room@8/3/10`): the tie beams
   with hanging lanterns read as a floor to the audit. Likely a false positive; confirm the beams are not 2+ wide
   (if they are, they read as a loft to players too).
4. **minor**: desert/mesa twins (likeness 1.0).

## commerce/thieves_den: B-

1. **minor**: the secret cellar is sparse: a counter, a dice table, two chests on a large mossy floor. The javadoc
   promises crates and chests of loot. Fix: `Rooms.furnish(..., STORAGE, ...)` along the back wall, barrels and crates,
   a cloth hanging, a candle on the table.
2. **minor**: the upstairs is one room, so cramped/no-3x3 in every culture; acceptable for a hovel.
3. **minor**: steppe roof light grey and bayou pinkish: check those variants are intended (they stand out from the
   culture's other houses).

## landscape/bell: C

Image `G4-img/bell_identical.webp`.

1. **major**: identical in 13 cultures (likeness 13): two posts, a beam and a three-cell roof; the bell is barely
   visible. On the 5x4 preview plot the pavilion branch (`w >= 5 && dp >= 5`) never runs. File `Bell.java`. Fix: use the
   pavilion from 5x4 (`dp >= 4`), give the small frame culture roofs (thatch cap, curved karst roof, sandstone arch in the
   desert, a log A-frame in the north), stone feet stepped, a bench.

## landscape/festival_ground: C

Image `G4-img/festival_ground_sparse.webp`.

1. **major**: on 35x22 most of the green is empty; everything sits in the left two thirds; two booths (javadoc: booths
   down either side), one trestle group, a short random fence. The maypole has a full tree canopy on top, so it reads as a
   tall tree, not a maypole. File `FestivalGround.java`. Fix: scale the layout to the plot (booth rows along both long
   sides every 4 cells, trestles in front of the stage, the arch at the gate), maypole crown as a small wreath of
   leaves + coloured wool/banner ribbons on the pole.
2. **minor**: same layout in every culture; mirror by seed and vary booth count.

## landscape/fountain: B+

1. **minor**: still water at the jets in the preview (javadoc: it runs on first update); fine in game.
2. **minor**: the fountain uses only the middle of its 5x8 plot; add the corner lamps/benches on 5x8 too.

## landscape/garden: B-

Image `G4-img/conifer_trunk_tips_and_tropical_garden.webp`.

1. **major**: tundra (and northern/alpine/maritime wherever conifers are used): the spruces show a bare log 1-2 blocks
   above the foliage with a single leaf on top. Not natural. File `src kit/landscape/Trees.java` `conifer(...)`: the log
   runs to `topY - 1` while the radius is 0 for the top two rows. Fix: end the log at `topY - 2` and lay leaves at
   radius 0 on the top two rows (override the log), with a radius-1 cross at `topY - 2`.
2. **major**: tropical garden is random scrub: two trees, bushes, no path, edge, pool or bench (javadoc: ferns, palms, a
   lily pool, bamboo). Fix: a stepping-stone path from the street, a small lily pool, bamboo clumps at the back edge,
   a bench.
3. **minor**: the garden fills about half its 13x10 plot; extend the fence/hedge to a 1-cell margin.

## landscape/lamp_post: B+

1. **minor**: tropical tiki torch and desert pillar are thin for a 4x3 island; fine.

## landscape/park: C+

1. **major**: over-planted: 10-14 trees whose canopies merge into one mass over the walks; conifer cultures look like a
   plantation, with the bare-trunk tips (Trees.conifer, see garden 1). Same layout everywhere (pond front centre).
   File `Park.java` (tree clumps). Fix: at most one tree per ~25 cells, clumps of 2-3 with open lawn between, keep
   walks clear of canopy below y+4; mirror the pond by seed.
2. **minor**: desert palms are drawn as broad blobs; use `Trees.Kind.PALM` with a curved trunk.

## landscape/plaza: C+

Image `G4-img/plaza_noisy_paving.webp`.

1. **major**: temperate paving is a busy white/grey checker-band pattern; Bohemian squares are cobble setts with a
   calm pattern. File `src kit/.../Paving.java` pattern choice per culture. Fix: temperate pattern `SETTS` with one band
   colour, medallion only at the centre.
2. **major**: the stand-alone centrepiece is five trees in planters in a quincunx: they fill the centre, so the square
   has nothing open to gather in. Fix: one tree or a market cross/well at the middle, benches facing it, the ring clear.
3. **minor**: steppe trees have grey trunks reading as stone pillars; check the tree kind.

## landscape/statue: D

Image `G4-img/statue_figures.webp`.

1. **major**: the human figures are 3 wide by 1 deep by 7 high slabs of stone brick (`Statue.figure`: torso rows all
   at `cz`), so from any corner they read as a chimney or a ruin, not a person. Affects temperate, wisteria, alpine,
   moorland, maritime, mediterranean, bayou. File `src archetype/landscape/Statue.java` `figure(...)`. Fix: give the body
   two rows of depth (torso 3x2, robe 3x2 with a stair hem), a distinct head (a full block with a slab cap, one block
   above the shoulders), arms as walls/stairs standing off the torso, the plinth one step taller so the figure is
   above eye height; use bronze-green (oxidized copper) in north/coast as the javadoc says.
2. **minor**: desert/mesa obelisk: 1x1 shaft with two teal accent bands (`Paving.inlay` = glazed accent) that read as
   gems; use a recessed band of the dressed stone and a pyramidion of stairs.
3. **minor**: northern/tundra rune stone is a 1x1 pillar on a big plinth; make it 2 wide, 4 high, with a carved face
   (chiseled block, trapdoor relief).

## landscape/well: B

1. **minor**: desert/mesa kiosk is crowned with the glazed accent block (`Paving.inlay`), which reads as a gem. File
   `Well.java` `kiosk(...)`. Fix: a small dome finial of the wall material or a lantern.
2. **minor**: moorland/maritime/bayou well roofs sit off-centre and look like a sliding shed; centre the ridge on the
   windlass.
3. **minor**: the hanging bucket cauldron counts as an unusable station in all cases (decorative; a false positive
   unless it should be fillable).

---

## Top 10 fixes for G4, ranked by impact

1. **MarketHall back-bay door** (blocker, 14 cultures, 84/84): move `doorX` to the aisle (`cx`) or shorten the counter
   run in front of it. One-line fix in `MarketHall.java`.
2. **Trees.conifer trunk tips** (major, every conifer culture, garden/park/plaza/festival and any yard using
   `Trees`): end the log two below the top and finish the tip with leaves. One kit fix improves many plots.
3. **Statue figures** (major, 7 cultures): give `Statue.figure` depth (2-deep torso/robe, a real head and arms) so it
   reads as a person; teal bands off the obelisk.
4. **Caravanserai** (blocker + major): reach the gate room, real gate leaves, monumental pishtaq portal, taller round
   corner towers, buttresses, merge cells into usable rooms, open the dark corner rooms; per-culture plans.
5. **Bank**: keep the aedicule/portico pediment under the eave (no roof clipping), fix the raised-plot flight (bayou s1
   press room), allow two storeys on 10-deep plots.
6. **Adobe.dome** (bank, market hall; well kiosk and obelisk finials): rounded profile with stairs, wall/plaster
   material, small finial instead of grey ziggurat plus gold block plus gem-coloured inlay.
7. **CountingHouse**: add the missing stair on 7x13 plots (45-cell sealed storey in 3 cultures), unblock the desert
   roof-room door, cap steep pitches on 7-wide bodies so gables fill cleanly.
8. **Guards on drops**: rail stairwells and balconies (inn 12 / karst 55-64 cells, hostel 4, tavern tropical veranda
   9-18, market hall plinth 37, caravanserai gallery 9); best done once in `Storeys.flight` and `Porches.balcony`.
9. **Per-culture variety for the small set pieces**: market stall canopies in culture cloth and forms, bell frame
   culture roofs and the pavilion from 5x4, caravan yard plan mirroring and southern/northern variants.
10. **Open-space layouts**: fill the festival ground to its plot (booth rows, arch, a real maypole), thin the park's
    planting with lawns between clumps, calm the temperate plaza paving and clear its centre.

Also worth doing (smaller): uncrowd inn guest rooms; furnish the tropical tavern; reach or remove the caravan yard
loft; skip cellar corner barrels in `Cellar.stock`; stock the thieves' cellar; a real tropical garden.
