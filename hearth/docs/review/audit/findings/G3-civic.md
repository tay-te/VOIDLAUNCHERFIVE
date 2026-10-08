# G3 civic, learning, law: visual and playability audit

Scope: 23 archetypes (`archetype/civic/**`, `archetype/law/**`), 14 cultures each. Sources checked: every flat sheet and
cutaway/night sheet in `../sheets/civic/` and `../sheets/law/` (read at full resolution in crops), every metrics row
(`../metrics.csv`, 84 cases per archetype), the archetype sources and the kits they call, plus my own runs in the
hearth-audit worktree:

- walk maps (`blueprintPreview ... reach=`) for town_hall, playhouse, university, courthouse, observatory, library,
  convent, infirmary, chapel, bathhouse and monastery (temperate or northern, seed 1007 south);
- a column listing (`slice=c4:1/c4:2/c4:3`) at the town hall's upper door;
- slope renders of town_hall, monastery, university, chapel, courthouse and tilt_yard in temperate, karst and tropical
  (`G3-img/*.webp`).

Image paths are relative to this file. `sheet` = `../sheets/<cat>/<cat>_<name>.webp`, `cut` = `..._cut.webp`.

**How to read the automated numbers here.** Most `unreached_room_cells` and `unreached_levels` in this group are sealed
attics and roof voids, not rooms people need. The walk maps show the upper storeys of monastery, convent, university,
playhouse and library ARE reached. Monastery's "10 unreached storeys" are the cloister lean-to's top and the roof
spaces. So those scores make the big precincts look worse to play than they are, and hide how they actually look,
which is their real problem. Several `bad_flights` (courthouse, chapel) are the walk climbing roof stairs after it gets
out of a belfry onto the roof (see courthouse).

## Grades at a glance

| archetype | grade | blockers | major | minor |
|---|---|---|---|---|
| civic/chapel | B+ | 0 | 1 | 4 |
| law/watch_house | B+ | 0 | 1 | 1 |
| civic/shrine | B | 0 | 1 | 2 |
| civic/almshouse | B- | 0 | 2 | 2 |
| civic/cemetery | B- | 0 | 1 | 3 |
| civic/guardhouse | B- | 0 | 1 | 2 |
| civic/observatory | B- | 0 | 2 | 2 |
| civic/orphanage | B- | 1 | 1 | 2 |
| civic/school | B- | 0 | 1 | 2 |
| civic/infirmary | C+ | 0 | 2 | 2 |
| civic/library | C+ | 1 | 2 | 1 |
| law/jail | C+ | 0 | 2 | 1 |
| law/pillory | C+ | 0 | 2 | 0 |
| civic/bathhouse | C | 0 | 3 | 1 |
| civic/town_hall | C | 2 | 3 | 2 |
| law/courthouse | C | 0 | 4 | 2 |
| law/gallows | C | 0 | 2 | 1 |
| law/toll_gate | C | 0 | 2 | 2 |
| civic/playhouse | C- | 0 | 4 | 1 |
| civic/monastery | D+ | 0 | 5 | 2 |
| civic/university | D+ | 0 | 5 | 1 |
| civic/convent | D | 1 | 5 | 1 |
| civic/tilt_yard | D | 0 | 4 | 1 |

Distribution: A 0, B 9 (B+ 2, B 1, B- 6), C 10 (C+ 4, C 5, C- 1), D 4 (D+ 2, D 2), F 0. **Blockers: 5.**

## Problems in many archetypes (fix these in the kit or style, not per building)

- **X1 (major): mesa is desert, block for block.** `culture_twin` desert~mesa is 1.00 in all 23 archetypes; only the
  palette changes (redder). The style bible gives mesa its own manners (pueblo, cliff masonry, mission). Images: every
  sheet, bottom row: desert column 2, mesa column 7. At fault: `kit/civic/Tradition.java` maps both to `ADOBE`, and every
  `case ADOBE` (Adobe.java, CourtYard, Halls.roofTerrace) draws the same. Fix: add a `PUEBLO` tradition (or a mesa
  habit) with stepped terraces set back storey by storey, viga ends, ladders to roof hatches, rounded parapets, and a
  bell-wall (espadaña) for the religious buildings. Keep the crenellated cube for desert only.
- **X2 (major): the desert "adobe cube" is the same building in every small civic type.** Almshouse, infirmary,
  orphanage, school, guardhouse, jail, toll gate, bathhouse and chapel are all a crenellated cube with the same pink
  pilaster bands and the same row of turquoise tiles. Images: desert column of each sheet. At fault:
  `kit/civic/Adobe.java` (`roof`, `screens`) applied unchanged. Fix: vary by purpose. Courtyard houses with an
  `iwan`/arcade for the almshouse, infirmary and school. A domed qubba for the chapel and bathhouse. A plain walled
  compound with a gate tower for the guardhouse and jail. Keep the tile band for religious and civic-grand buildings only.
- **X3 (major): tropical walls are saturated yellow bamboo planks.** `hearth_style/tropical.json` roles.wall =
  `bamboo_planks` (weight 2), roof = `bamboo_mosaic`. In every tropical render the building reads lemon-yellow (town hall,
  university, library, almshouse, guardhouse, infirmary). The noisy dark-post pattern on yellow (almshouse, guardhouse,
  orphanage) looks generated, not woven. Fix: make walls palm or jungle planks, stripped bamboo, or lime-plastered coral
  stone. Keep bamboo for screens, rails and trim. Make the roof thatch (hay/dried kelp or the Expanse thatch) or palm
  rather than mosaic.
- **X4 (major): one plan reskinned.** Archetypes with `culture_likes` 10 to 13 (at least 0.8 alike): playhouse 13, tilt_yard 13, jail 13,
  toll_gate 12, gallows 11, pillory 11, university 10, cemetery 10, watch_house 10. Fix per archetype below.
- **X5 (minor): belfries let you out onto the roof.** Courthouse (y 12-13 ring reached all round the eaves, up to 91
  fall-edge cells, steppe slope), chapel temperate/wisteria (eave ring at y 6, 54 fall-edge cells) and town hall
  (fall_edge@6/13/14). This is also what the audit counts as `bad_flights`: the walk climbs roof stairs. At fault:
  `kit/civic/Bohemian.tower` and `Towers` belfry openings. Fix: put a sill (slab or wall) or a rail in each belfry
  opening, as real belfries have louvres.
- **X6 (minor): night lighting outdoors.** Cemetery and tilt yard paths are nearly dark at night (cut sheets, night
  column). Fix: a lamp at the lychgate and at the chapel door, and lanterns on the list posts and the stand.

---

## civic/town_hall: C

Images: `../sheets/civic/civic_town_hall.webp`, `..._cut.webp`, `G3-img/town_hall_slope.webp`.

1. **BLOCKER: the chamber's balcony doors open onto a fence.** 12 cultures (not temperate or wisteria; their Bohemian
   branch builds an oriel); 72/84 cases. Column listing, northern s0, door at local x4 z3 y6:
   `z=2: y5 spruce_slab(top) · y6 pale_oak_fence · y7-8 dark_oak_trapdoor(open) · y9 trapdoor · y10 roof stair`. The
   balcony is one block deep and its rail stands on that one block, right in front of the door, with the window's
   shutters hung in the door's bay. Nobody can step out, and the VIEWPOINT POI there is unusable. At fault:
   `TownHall.java:223-243` (doors at `cx±4`, `Porches.balcony(d, upper, …, fy)`) and `kit/Porches.balcony`. Fix: make the
   balcony 2 deep (floor at z0-1 and z0-2, rail on the outer row only), keep the door cell's front clear, and skip
   shutters on door bays. Add a test that every door has a standable cell on both sides.
2. **BLOCKER: the tower's room on the chamber floor is sealed but furnished.** Centre-tower cultures (northern, alpine,
   tundra, steppe, tropical, bayou), 36/84 cases, `unreached_furnished_room@9/6/4` (6 cells). In the walk map, northern
   y70, the tower base inside the chamber shows `+++` with no door. At fault: `TownHall.tower(...)` (line 584). Its
   walls are drawn over the chamber without an opening. Fix: give it an archway from the chamber (and a ladder on up to
   the belfry), or fill it solid if it is not meant to be a room.
3. **Major: reads as a church.** A west tower with a spire before a gabled nave (temperate, wisteria, moorland, maritime,
   tundra, northern). Real Bohemian town halls (Prague Old Town, Tábor) put the tower on a corner of a broad, arcaded,
   many-windowed block, with an oriel and a show gable. Images: sheet, top row. At fault: `TownHall.draw` tower=`centre`
   default by tradition. Fix: default to `corner` for stone and timber traditions. Make the body wider than deep with an
   arcade (loggia) the full width of the front and a council-chamber row of tall windows. Keep the centre spire for
   alpine and tropical only.
4. **Major: the ground floor is an empty stone hall with a bed.** All cultures (cut sheet, level 1: grey floor, one bed,
   a bench or two). The javadoc promises the clerk's desk, the records and benches. At fault: `TownHall.java:309-350`
   (`Rooms.furnish(entry, HALL)`). Fix: partition a clerk's office (desk, lectern, chests of records, a map on the wall)
   and a waiting hall (benches, notice board, a lamp every 4 blocks). Move the bed to a caretaker's room or drop it.
5. **Major: courthouse twin.** See law/courthouse #1.
6. Minor: on slopes the loggia steps and the plinth are fine (G3-img/town_hall_slope.webp), but the tropical version is
   all yellow (X3) and the karst tower is a yellow box under green eaves; it wants a drum tower (gulou) form.
7. Minor: tight headroom (exactly 2 clear) on 0.1-0.4 of the floor in all cultures, worst tropical hills (0.41).
   `Storeys` storey height for the chamber should be 4+ clear.

## law/courthouse: C

Images: `../sheets/law/law_courthouse.webp`, `..._cut.webp`, `G3-img/courthouse_slope.webp`.

1. **Major: the same building as the town hall** in the stone and timber cultures (temperate, wisteria, alpine,
   moorland, maritime, steppe). `CourtHall.towered` uses the Bohemian tower and the same gabled hall. Fix: make the
   court a lower, longer hall without a spire: an arcaded front (or a stair up to a raised court room), a lock-up in
   the undercroft, and the town's pillory or a whipping post at the door. Leave the tower to the town hall.
2. **Major: tropical and bayou are a plain box on stilts under one hip roof** (`CourtSingle`, LOG/STILT): no porch, no
   gallery, no hierarchy. Fix: a wrap-round gallery (veranda) on posts, a raised dais visible through big shutters,
   and steps centred on the front.
3. **Major: belfry gives onto the roof** (X5). 36/84 `bad_flights`, fall-edge up to 94 cells (alpine slope).
4. **Major: the ground floor is empty and cramped** (51/84 `cramped_rooms`; cut level 1: grey floor, a bed, a desk).
   Fix: holding cell with an iron door, clerk's table, benches for petitioners.
5. Minor: desert/mesa roof holes (3 cells, `roof_hole@6/0/7`). That is the court's open patio being counted, which is fine,
   but put a drain or basin there so it reads as meant.
6. Minor: karst Yamen is the best variant (pavilions, red columns, screen wall); keep it.

## civic/monastery: D+

Images: `../sheets/civic/civic_monastery.webp`, `..._cut.webp`, `G3-img/monastery_temperate_slope_roofs.webp`,
`G3-img/monastery_slope_temperate_karst_tropical.webp`.

1. **Major: the roofs are broken where the church meets the ranges.** In the temperate slope back view
   (monastery_temperate_slope_roofs.webp) the church roof sits as a separate block with a gap and a strip of bare wall
   between two parallel ridges. Its ends step raggedly, and a loose line of roof stairs sticks out past the far range.
   The front shows a hole over the gate gable through which the cloister walls are visible from the street, and white
   crenel stubs poke up out of the roof. The same appears on the flat sheet in temperate, wisteria, alpine and tundra,
   and in tropical thatch. At fault: `Precinct.roofs()` (the "one surface, hipped at outer corners, valleyed at inner"
   join) and `PrecinctChurch.draw` (church roof, `gateGable`). Fix: draw the ranges' roof as one heightmap and the church
   as a higher roof that dies into it with a proper valley. Fill every cell between the two eaves lines, then trim. Drop
   the crenel stubs on the gate gable.
2. **Major: one plan in all 14 cultures.** Every cut sheet shows the same cloister: church front-right with pews, the
   same rooms round a square garth. Only karst swaps the church for a pavilion. Fix: per tradition (style bible). A karst
   monastery is an axial sequence of halls and courts climbing the slope (gate hall, bell and drum towers, main hall,
   sutra library). Desert/mesa is a walled mission with an arcaded patio and a bell-wall. Northern/tundra is a timber
   stave church beside log ranges. Tropical is a raised compound with galleries.
3. **Major: flat walls.** `flat_patch_max` 209 (desert), mean 107. Long range walls have 1x1 windows scattered at
   random heights (temperate back, slope image). Fix: buttresses on the church every bay; a plinth and a string course
   on the ranges; windows in regular bays (cloister architecture is regular); a jettied or timber upper storey in the
   timber lands, as the javadoc says.
4. **Major: rooms are cramped and empty.** 84/84 `cramped_rooms`, up to 11 rooms without a 3x3 clear (karst slope),
   80/84 have empty rooms (porter's lodge, cellar, store). Fix: fewer, larger rooms per range (`Want` weights), and
   furnish STORE/CELLAR with barrels and sacks and the porter with a desk and bench.
5. **Major: the cloister door from the church is blocked** (`blocked_door@5/0/12` in 70/84 cases; in karst and tropical
   nobody reaches it, `unreached_door`). At fault: `PrecinctChurch.java:344-353`. The walk side is kept for only one cell
   and the arcade's low wall or lean-to post lands beside it. Fix: keep the full 3-wide threshold on the walk side and
   leave that arcade bay open.
6. Minor: roof holes over the walk (2 cells, 30/84, `roof_hole@13/0/11`). These are gaps in the lean-to at the inner
   corners. Close the corners of the lean-to.
7. Minor: temperate roof is dark-oak planks/stairs. For a Bohemian abbey use red tile (as the town houses) or shingle.

## civic/convent: D

Images: `../sheets/civic/civic_convent.webp`, `..._cut.webp`.

1. **Major: the monastery again, smaller.** Same `Precinct`, same church, same rooms and roofs (compare the two sheets
   culture by culture). Fix: give the convent a different programme and massing. Enclosed and inward: no street gate
   range, a high wall with a turn (rota) and a parlour grille, an orchard/garden court, a nuns' gallery at the church's
   west end, a smaller bell-cote.
2. **Major: the same roof faults as monastery #1** (temperate, wisteria, alpine: open gable over the walk seen from the
   street; ragged hip ends). Same fix.
3. **BLOCKER: a store barrel no one can use** in desert, steppe and mesa seed 2007 (all three terrains):
   `station_unusable:barrel@4/5/2` on the upper floor. Fix: `Precinct.furnish()` must check a reached face before
   placing STORE furniture.
4. **Major: cloister door blocked** (`blocked_door@5/0/7`, all cultures 42/84; unreached in karst/tropical). Same fix as
   monastery #5.
5. **Major: cramped and empty rooms** (84/84 cramped up to 5; 78/84 empty). Same fix as monastery #4.
6. **Major: flat walls** (`flat_patch_max` 191).
7. Minor: roof holes at the walk's inner corners (30/84).

## civic/university: D+

Images: `../sheets/civic/civic_university.webp`, `..._cut.webp`, `G3-img/university_slope_temperate_karst_tropical.webp`.

1. **Major: a plain box.** 218-block flat wall (worst in Hearth), relief 0.04, uniform windows the full 29-block side.
   No plinth, no string course, no buttresses or pilasters, no dormers. The roof is one even hip around the court.
   Slope image: the temperate and karst sides are blank white planes. Fix: in `Precinct.shell()` for the university, add
   a plinth course and a cornice; group windows in bays with pilasters; give the library range taller windows and a
   gable or higher roof; add dormers on the long roofs; let the gate tower project a block from the front.
2. **Major: the upper floor is mostly empty.** 84/84 `empty_rooms` (up to 5), `purposeless_room@12/5/3`. Cut level 5:
   long bare boards with a bed per chamber at the ends. Fix: divide the chambers into 4-5-wide studies (bed, desk,
   chest, shelf, lamp each). Make the back range's upper library a real hall (shelves in rows, lecterns, reading
   tables).
3. **Major: a ground-floor door with a blocked side** in the gate passage, 84/84 (`blocked_door@13/0/6` / `14/0/6`);
   unreached in karst and tropical. At fault: `University.gateTower` (the arched wall across the passage at z1 and the
   `p.keep` cells) against the porter's or parlour door. Fix: keep the cells either side of every door in the passage
   and move the lodge door off the arch pier.
4. **Major: one plan in 10 cultures** (temperate=wisteria=alpine=moorland=maritime 1.00). Fix: karst should be an
   academy of halls round courts (shuyuan); desert a madrasa with four iwans; northern a timber college.
5. **Major: tropical is all bamboo yellow** (X3); slope image, right.
6. Minor: rooms without a 3x3 clear (up to 18, tropical). These are the 2-wide walks counted as rooms. Widen the
   arcade walk to 3 where the plot allows.

## civic/playhouse: C-

Images: `../sheets/civic/civic_playhouse.webp`, `..._cut.webp`.

1. **Major: identical in 13 cultures** (`culture_likes` 13; desert and mesa are the same plan with a flat roof).
   Fix: give each tradition its own playhouse. Timber lands: the galleried yard (keep it). Mediterranean: an open-air
   cavea of stone tiers. Karst: an opera stage pavilion facing a courtyard and a two-storey viewing hall. Desert and
   mesa: an arcaded courtyard with a raised platform. Tropical and bayou: an open-sided pavilion.
2. **Major: facade depth 0** (relief 0.000 in temperate; the frame is painted onto the plane). Fix: posts and sills a
   block proud, the upper gallery jettied, plaster panels recessed, a projecting entrance porch with the house's
   board.
3. **Major: the tiring house's roof is a fragment.** At the back a separate gable floats above the ring roof, cut off at
   its ends (sheet: every culture, back left). At fault: `Playhouse.java` ring roof (`354-390`) vs the tiring-house
   gable. Fix: run the tiring house's gable through to the outer wall with a proper end gable and a valley into the
   ring roof.
4. **Major: upper gallery tier is barely there.** On a 13-high plot `tiers` is false, so the "two tiers" are one,
   and the walk map at y4-5 shows only a narrow reached strip along the side wings. Fix: allow `tiers` from height 11, and
   give the upper gallery benches.
5. Minor: the yard is bare packed earth. Add a few barrels, a hay bale, a vendor's stall.

## civic/tilt_yard: D

Images: `../sheets/civic/civic_tilt_yard.webp`, `..._cut.webp`, `G3-img/tilt_yard_slope.webp`.

1. **Major: identical in all 14 cultures** (`culture_likes` 13; detail score 38.9, lowest in the group). Desert, karst and
   tundra get the same white pavilions and plank stand. Fix: per culture. Steppe: a horse-games field with yurts.
   Karst: an archery and horse ground with a pavilion. Desert: a walled hippodrome ground. Bohemia: a list with a
   painted-cloth stand.
2. **Major: the pavilions are white wool cubes with a red stripe**, not tents. Fix: tents from wool and carpet with a
   pole, a pointed or ridged top (stairs and slabs), guy ropes (fences/chains) and pennants in the culture's cloth.
3. **Major: one stand on one side, no shelter or lord's box hierarchy.** Fix: a covered lord's box in the middle (roof,
   banners, chairs), open tiers either side, a second low stand opposite, a judges' table, a weapons rack, a
   well/trough for horses.
4. **Major: on slopes the arena's terrace drops 2-4 blocks with no rail** (up to 72 fall-edge cells, temperate slope
   s1). Slope image. Fix: fence the terrace edge or ramp it.
5. Minor: dark at night (X6).

## civic/bathhouse: C

Images: `../sheets/civic/civic_bathhouse.webp`, `..._cut.webp`.

1. **Major: one undivided room with no furniture.** Cut sheet: temperate and alpine are a pool room with basins and
   nothing else. Desert, karst, mesa, northern and tundra have empty rooms (30/84). Mesa is one room with a slab
   platform; northern is benches only. Fix: a changing room (benches, chests, hooks as item frames), a hot room (stove or
   furnace, cauldrons, steam as campfire under grating), a cold basin, a keeper's counter. The northern sauna wants a
   stove and tiered benches.
2. **Major: northern and tundra have a sealed roof storey** (17-20 sealed cells, `unreached_levels` 1-2). Fix: fill it,
   or put a drying loft there reached by a ladder.
3. **Major: exterior is a house.** Only the water troughs out front say "bath". Fix: a chimney or stove stack, a wood
   store, a vaulted or domed roof in Mediterranean/desert, steam vents.
4. Minor: karst version's yellow bamboo fence frame is noisy.

## civic/library: C+

Images: `../sheets/civic/civic_library.webp`, `..._cut.webp`.

1. **BLOCKER: the records chest can't open.** 84/84, `chest_lid@7/5/10`. `Library.java:271`:
   `Furniture.chest(d, ix1, gy, iz1, …)` sits in the gallery's back corner under the shelves the gallery carries above.
   Fix: put it on the gallery walk where the cell above is air (or use a barrel, which opens from the side).
2. **Major: a tower, not a hall.** 9 wide x 20 tall in every culture. It reads as a watch tower with a cupola. Fix: a
   wider-than-tall reading hall (11-15 x 9) with tall windows in bays and a lantern on the ridge; keep the tall version
   for a "tower library" variant only.
3. **Major: same box in 9 cultures** (temperate=alpine, northern=tundra, moorland=maritime 1.00).
4. Minor: temperate front has a red-brick hood and a broken pattern of stone frames across the gable (sheet top-left).
   Make the entrance a proper portal with a pediment in the wall's own stone.

## civic/observatory: B-

Images: `../sheets/civic/civic_observatory.webp`, `..._cut.webp`.

1. **Major: the roof door has a blocked side** in 10 cultures (60/84, `blocked_door@1/16/7`). In the walk map at y16
   (temperate) the outer ring is `+` (walkable, not reached). The door from the stair turret opens toward a merlon or
   ledge. At fault: `Observatory.java` top storey. Fix: open it onto the roof walk, or drop the door and end the stair in
   the room under the dome.
2. **Major: karst is a plain white two-step box** with a black slab roof and no instruments visible. Fix: the karst
   observatory is a brick platform with an armillary sphere, celestial globe and quadrant (Instruments kit) on top, and
   a stair up its side.
3. Minor: desert, mesa and steppe roof holes and cramped top room (`roof_hole@7/6/6`): the open oculus. Keep it but rail
   the edge.
4. Minor: tropical and bayou are a brick tower with a copper dome; fine, but the copper reads very new. Use weathered
   copper.

## civic/infirmary: C+

Images: `../sheets/civic/civic_infirmary.webp`, `..._cut.webp`.

1. **Major: reads as a cottage.** A one-storey gabled house with a physic garden. A town infirmary (Spitalkirche,
   hospital) is a long ward hall with the beds along the walls and an altar at one end. Fix: a long hall (frontage 13 as
   depth, beds both sides, a chapel apse or altar niche), a herb store, and a bell-cote.
2. **Major: blank side walls** (temperate and maritime right side; cut level 0 shows the ward is lit but has small windows).
3. Minor: attic is sealed (25 cells, `unreached_levels` 2). Fill it or make it a linen loft.
4. Minor: tropical hills tight headroom 0.70: the raised floor leaves the ward 2 blocks clear. Raise the plate by 1 on
   stilts.

## civic/almshouse: B-

Images: `../sheets/civic/civic_almshouse.webp`, `..._cut.webp`.

1. **Major: noisy wall pattern in tropical, bayou, desert and mesa.** Dark posts and dark patches scattered irregularly
   over yellow, white or adobe (sheet bottom row). It looks generated. At fault: the timber-frame pattern in
   `kit/Walls`/`Facade` for STILT, and `Adobe`. Fix: frame on a regular bay rhythm, posts at corners and openings only.
2. **Major: same L-plan in 6 cultures** (alpine=mediterranean 0.97, desert=mesa 1.00).
3. Minor: a lone plank bar sits above the ridge beside the chimney (temperate, wisteria, tundra, moorland, maritime;
   sheet). If it is a ridge vent or bell-cote it needs posts; otherwise remove it.
4. Minor: upstairs dormitory beds in a 4x2 grid with no aisle on one side (66/84 rooms without 3x3). Use two rows
   along the walls with a central aisle and a chest at each bed.

## civic/orphanage: B-

Images: `../sheets/civic/civic_orphanage.webp`, `..._cut.webp`.

1. **BLOCKER: a bed with no free side** in tropical slope and hills (`bed_blocked@2/8/6`). Fix: the dormitory rows must
   keep an aisle when the stilted plan shrinks; check `beds_no_free_side` in `Orphanage.java`'s bed placement.
2. **Major: a plain gabled box** with blank side walls (temperate right side is one white plane). Fix: a porch or a
   lean-to schoolroom, a bell over the door, windows on the sides.
3. Minor: dormitory beds butt end to end in a continuous strip (temperate level 5). Space them with chests.
4. Minor: tropical hills tight headroom 0.6-0.74.

## civic/school: B-

Images: `../sheets/civic/civic_school.webp`, `..._cut.webp`.

1. **Major: the schoolroom is sparse.** Cut level 0: teacher's desk and blackboard, very few pupil benches, a large
   bare floor. Fix: rows of benches with desks (stairs + trapdoors) facing the board, a stove, a shelf of slates, pegs.
2. Minor: tropical hills tight headroom 1.0 (the whole floor) and empty rooms on stilts. Verify the stilted storey
   height in `School.java`.
3. Minor: desert/mesa is the adobe cube (X2).

## civic/guardhouse: B-

Images: `../sheets/civic/civic_guardhouse.webp`, `..._cut.webp`.

1. **Major: mediterranean, moorland, maritime, desert and mesa are crenellated cubes** with one small door hood: no
   plinth, no machicolation, no gate. Fix: a projecting door with a bracketed hood or a small porch, an arrow slit
   pattern, a corbelled parapet.
2. Minor: temperate and wisteria gables have a jagged stepped verge (the roof stairs poke past the gable). Trim the
   verge with a slab or a barge board.
3. Minor: tropical yellow (X3). Interiors are good (armoury below, bunks above).

## civic/cemetery: B-

Images: `../sheets/civic/civic_cemetery.webp`, `..._cut.webp`.

1. **Major: one layout everywhere** (`culture_likes` 10). Same two trees in the same spots, graves in the same grid.
   Fix: vary by culture. Bayou/tropical raised tombs in rows (already there, good). Karst: hillside tombs with omega
   walls. Desert: whitewashed domed tombs. Northern: grave mounds and rune stones. Jitter the grid.
2. Minor: mortuary chapel is a 2x3 cell (cramped).
3. Minor: dark at night (X6).
4. Minor: on slope, terrace drops without a wall (up to 49 fall-edge cells).

## civic/chapel: B+

Images: `../sheets/civic/civic_chapel.webp`, `..._cut.webp`.

1. **Major: the belfry gives onto the nave roof** (temperate and wisteria; X5). 54 fall-edge cells; `bad_flight@1/6/6`.
2. Minor: mediterranean dome is bright yellow terracotta, which jars against the natural-colour rule. Use red tile or
   lime-washed stone.
3. Minor: moorland and maritime gables have ragged roof-stair verges (sheet, right half).
4. Minor: desert is a plain adobe box (X2); make it a domed qubba or a bell-wall chapel.
5. Minor: tight headroom 0.36 in tropical (stilt floor).
Strong per-culture variety (Bohemian, stave, rotunda, mission, karst hall), the best in the group.

## civic/shrine: B

Images: `../sheets/civic/civic_shrine.webp`.

1. **Major: the karst shrine is a lone red pi-gate with a teal slab blob beside it** (sheet right, karst). It reads as
   leftover parts. Fix: a tudi shrine: a small tiled hip roof on red posts over an altar table with incense.
2. Minor: northern and alpine roofs sit lopsided on a single post like a "7". Centre a small pitched roof over the post.
3. Minor: desert shrine is a bare brick cube.

## law/watch_house: B+

Images: `../sheets/law/law_watch_house.webp`, `..._cut.webp`.

1. **Major: one plan in 10 cultures** (all twins of temperate at 1.00). Fix: vary the loft (stone parapet in the dry
   lands, a pagoda-roofed drum room in karst, a raised stilt watch in bayou).
2. Minor: tight headroom 0.10-0.17. Good silhouette and furnished interiors otherwise.

## law/jail: C+

Images: `../sheets/law/law_jail.webp`, `..._cut.webp`.

1. **Major: one cube in 13 cultures** (`culture_likes` 13), a symmetric 10x11 box with a pyramid roof.
2. **Major: patchy side walls.** Big blocks of a second material sit in the middle of the side (temperate, alpine,
   maritime). Fix: put the second material at the base (plinth) or at the quoins, not as random patches.
3. Minor: a sealed void beside the cells (`sealed_void@8/1/8`). Make it the warden's strongroom.

## law/pillory: C+

Images: `../sheets/law/law_pillory.webp`.

1. **Major: the stocks are a dark block lump**, unreadable. Fix: a beam of slabs/trapdoors with two neck holes on
   posts, a bench before it.
2. **Major: same in 11 cultures.** Bohemian is a stone column (pranýř) with a chain and a statue on top; karst could be a
   cangue stand. Steppe and tropical want a post.

## law/gallows: C

Images: `../sheets/law/law_gallows.webp`.

1. **Major: same chunky dark-oak frame in 11 cultures** (karst only recoloured red). Posts and beam are 2 blocks thick,
   which is oversized for a 7x7 plot. Fix: 1-thick posts with braces (fence/stair), a trapdoor drop, a ladder. For
   Bohemia use the real form (šibenice): a round masonry platform with two or three stone pillars and beams.
2. **Major: desert and mesa platforms are a solid block** with no visible stair on the sheet side.
3. Minor: on slopes, 2 fall-edge cells at the platform edge.

## law/toll_gate: C

Images: `../sheets/law/law_toll_gate.webp`, `..._cut.webp`.

1. **Major: the side rooms by the road are empty and cramped** (84/84 `empty_rooms`, `cramped_room@1/0/2`,
   `purposeless_room@9/0/3`). Fix: one side the collector's office (desk, chest, scales, lamp), the other the guard's
   bench and a rack.
2. **Major: plain boxes** in moorland, maritime and steppe (one flat front with an arch).
3. Minor: the red-white barrier is a solid wool band across the arch that reads as an awning. Make it a pole (fence or
   wall) with stripes on a pivot post.
4. Minor: `culture_likes` 12.

---

## Top 10 fixes for G3, ranked by impact

1. **Clear the doors and reach blockers** (5 blockers plus 4 door majors). Town hall balcony: 2 deep, rail on the outer
   row, no shutters in door bays (`TownHall.java:223-243`, `kit/Porches.balcony`). Give the town hall's tower room on
   the chamber floor an arch (`TownHall.tower`). Move the library's chest out from under the shelves
   (`Library.java:271`). Check reach before placing the convent store barrel (`Precinct.furnish`) and orphanage beds.
   Keep a 3-wide threshold at the precinct church's cloister door (`PrecinctChurch.java:344`), the university gate
   passage (`University.gateTower`) and the observatory roof door. Add a BlueprintChecks rule: every door has a
   standable cell on both sides.
2. **Rebuild the precinct roofs and walls** (`Precinct.roofs`, `Precinct.shell`, `PrecinctChurch`). Use one continuous
   valleyed roof surface. Have the church roof die into it, with no gaps, holes, stubs or loose stairs. Add buttresses,
   plinth, string course and regular window bays. This lifts monastery, convent and university, the three largest civic
   buildings.
3. **Give mesa its own building tradition** (X1, all 23 archetypes): a PUEBLO tradition with stepped terraces, vigas,
   ladders and bell-walls. Stop reusing the desert adobe cube for every small civic type (X2, `kit/civic/Adobe`).
4. **Fix tropical materials** (X3, `hearth_style/tropical.json`): no bamboo planks as the main wall, a thatch or palm
   roof, and timber framing on a regular rhythm. One JSON change improves all 23 tropical buildings.
5. **Give monastery, convent and university per-culture plans and programmes.** The convent becomes enclosed with no
   gate range; the university gets its own massing and a real library hall. Karst, desert and northern get their own
   layouts (style bible).
6. **Rework the tilt yard**: real tents, a lord's box, a second stand, lamps, slope rails, and per-culture forms
   (`TiltYard.java`).
7. **Furnish the empty interiors** (`Rooms.furnish` kinds and the archetypes' room code): town hall and courthouse
   ground floors, university upper chambers, bathhouse rooms (changing, hot, cold), toll gate side rooms, school
   benches.
8. **Split courthouse from town hall** (`CourtHall.towered`): a low arcaded court with a lock-up and no spire. Move the
   town hall tower to a corner with a full arcade (`TownHall` tower default). Put sills or rails in all belfry openings
   so nobody walks out on the roofs (X5, `Bohemian.tower`, `Towers`).
9. **Playhouse**: facade relief, a whole tiring-house gable, two tiers from height 11, and per-culture theatre forms
   (`Playhouse.java`).
10. **Small law and landscape pieces**: readable stocks and gallows in real regional forms (Bohemian pranýř and
    masonry šibenice, karst cangue). Vary the jail, watch house and cemetery plans by culture. Put plinth/quoin
    material at the base and corners instead of random wall patches (`Jail`, `Gallows`, `Pillory`, `Cemetery`).
