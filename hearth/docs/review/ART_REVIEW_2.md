# Hearth art review 2 (the LIVING_WORLD.md §8.6 gate after wave 6), October 2026

The second art director's pass, this time judging **whole towns** rather than single buildings: 14 cultures grown by
the planner at village, town and city scale (day 70, 120, 150-160, 250, 300), on hills and on the land each culture
lives on (river, coast, lake, cliff, mountain, slope, flat), rendered in textures by the new `townPreview` by day and
by night; contact sheets for the families the first review graded lowest; and the `cultureSimilarity` check over the
whole catalogue. Same three rules as before: **natural real-world surface**, **temperate towns read as KCD II
Bohemia**, **buildings look hand-built by a skilled player**; plus the user's whole-town asks: interiors well lit,
verticality through dense streets, civic towers and hill sites. Branch `hearth` at `6acb5a67` (all wave 6 lanes
merged). No game code was changed for this review.

**The verdict in one paragraph.** Wave 6 fixed the buildings. The high-rise skyline is gone (town homes now stand three
or four storeys under steep roofs), the camouflage render, the log-end polka dots, the candy awnings, the whipped-cream
domes and the Big Ben in every culture are gone or nearly so, and the temperate town hall, church, shrine and bell tower
now wear the Bohemian manner (white render, timber gallery, dark helm). Side by side with the first review's renders
(§2) every culture improved, most by half a grade or more. What now stops a Hearth town from reading as a *place* is
no longer any single building but **the town as a whole**: (1) the farmsteads layout grows into a city of 500 plots that
is still a scatter of detached houses in a meadow, with no street wall, no square and town houses standing alone on
the grass; (2) the civic towers stand where they were sited, often in an empty field at the edge, so the skyline has no
centre and the hill tops stay bare; (3) the town ends abruptly on bare grass, with a handful of fields and no orchard,
strip or hedge belt to lead into the countryside; (4) a few things float (walkway decks, fair-ground trees and fences
on slopes) and construction sites read as roofless ruins; (5) the dry cultures (desert, mesa, steppe) kept the problems
the temperate culture lost: mottled walls, merlons on homes, roofless boxes. The walled temperate town
(`img2/city_temperate_walled_d300.webp`) and the alpine mountain town (`img2/hab_alpine_mountain.webp`) show what Hearth
can already do when the layout is right: a dense, white-and-red heart inside a wall, terraces climbing a slope.

Contents: [1 Method](#1-method) · [2 Before and after](#2-what-improved-before-and-after) ·
[3 Whole towns](#3-whole-towns) · [4 Families](#4-families) · [5 Cultures](#5-cultures) ·
[6 Similarity](#6-the-similarity-check) · [7 Wave 6 requests](#7-the-remaining-wave-6-requests) ·
[8 Top 30](#8-the-top-30-grouped-for-architects)

---

## 1. Method

| what | how | images (`docs/review/img2/`) |
|---|---|---|
| Cities, all 14 cultures, hills | `townPreview -Pculture=all -Pdays=250 -Pterrain=hills -Pseed=1 -Pnight=true -Pcloseups=4` (209 folk, city tier, 430-540 plots) | `city_<culture>_hills_d250.webp`, `close_<culture>_heart.webp`, `city_temperate_hills_d250_night.webp`, `close_temperate_heart_night.webp` |
| Towns, all 14 cultures | `... -Pdays=120 -Pseed=2 -Pcloseups=2` (73 folk, town tier) | `towns_d120_a.webp`, `towns_d120_b.webp` |
| Villages, all 14 cultures | `... -Pdays=70 -Pseed=5 -Pcloseups=2` (24 folk, village tier) | `villages_d70_a.webp`, `villages_d70_b.webp` (hearts), `close_temperate_village_d70.webp` |
| Temperate set pieces | walled (`temperate/walled`, day 300, s4, day and night), river (ring layout, day 300), farmsteads town (day 150), Expanse Earth terrain (`-Pterrain=earth`, day 160) | `city_temperate_walled_d300[_night].webp`, `close_temperate_walled_heart[_night].webp`, `city_temperate_river_d300.webp`, `town_temperate_farmsteads_d150.webp`, `town_temperate_earth_d160.webp` |
| Habitats | maritime and tropical on coast, bayou on lake (stilt), karst and mesa on cliff, alpine on steep (mountain), mediterranean on slope (hillside), desert and steppe walled on flat, northern and wisteria on river, tundra and moorland on flat; day 250-300 | `hab_*.webp`, `close_cliffs.webp`, `close_maritime_coast_heart.webp`, `close_bayou_lake_heart.webp` |
| Contact sheets | `blueprintPreview` flat, 14 cultures × 2 seeds, for 61 archetypes: town homes, station homes, craft, civic and commerce, bell and clock towers, learning, noble, social life, deep; re-tiled 7 cultures to a row as in review 1 | `sheets/<category>_<name>_flat.webp` |
| Before / after | review 1's images beside the same culture, layout or sheet today | `ba_*.webp` |
| Similarity | `cultureSimilarity -Parchetype=all -Pseeds=2 -Pthreshold=0.8` | `docs/review/similarity_2.txt` |
| Floating pieces | a throw-away probe (MiniSim towns, 14 cultures, day 300, hills): every blueprint's solid cells flood-filled, any piece that touches neither ground nor the rest of the building listed; then the same over the composed town heart. The probe was not committed (§8 item 30 asks for it as a check) | quoted in §3 |

Not covered: castles (being reworked in parallel; judged only as they render in towns), law and trade sheets (not
re-run; nothing in wave 6 touched them beyond palettes), in-game lighting and Expanse blocks (the offline renderer
draws Expanse blocks as vanilla stand-ins), and **vegetation and the countryside between towns**: the synthetic
terrains and the Earth probe carry no trees, so a town's edge is judged only for what Hearth itself builds there
(fields, orchards, gardens). Towns on the same day grow to the same head count in every culture (the MiniSim's growth
curve), so the cultures differ by layout, plot count and buildings, not by size.

---

## 2. What improved (before and after)

| | before (review 1) | after (wave 6) | image |
|---|---|---|---|
| **Temperate skyline** | a forest of five- to eight-storey white and brick towers round the market; green copper needle spires | three- and four-storey white render houses under red tile and dark shingle; dark helms; no copper | `ba_temperate_town.webp` |
| **Desert heart** | candy-striped awnings on every stall, whipped-cream domes, a terracotta Big Ben, merlons everywhere | linen and single-colour canopies, plaster hemispheres on drums, a minaret for the clock tower; some merlons left on homes | `ba_desert_close.webp` |
| **Northern heart** | log ends on every other course, white window blocks, red-white-blue awnings, six-storey timber towers | log ends at corners only, board trim, turf roofs; still dark and tall-ish, with blank side walls | `ba_northern_close.webp` |
| **Mediterranean hill town** | every wall mottled pink/white/grey; light-blue striped awnings; five-storey rows | calm cream, white and rose render, tonal; three- and four-storey houses; stripes only on market awnings | `ba_mediterranean_close.webp` |
| **Tenement** | seven or eight storeys with a flat roof and roof hut (an apartment block) | four storeys under a steep roof with a Bohemian stepped gable; flat roofs only in dry cultures | `ba_tenement.webp` |
| **Town hall** | Hanseatic brick with crenellated tower and corner spirelets; copper cube clusters in the alpine | temperate: white render, arcade, a tower with a timber gallery and a dark helm (a *radnice*); every culture its own tower cap | `ba_town_hall.webp` |
| **Market stall** | a tall closed booth under purple, lime or cyan stripes | a low trestle under plank boards or undyed linen, with crates and baskets | `ba_market_stall.webp` |
| **Clock tower** | one Big Ben with 3×3 gold dials in every culture | a Bohemian town tower, campanile, minaret, drum tower; one-block dials | `ba_clock_tower.webp` |
| **Cottage** | crossed horns three blocks long, snow-patched tundra roofs, a desert fort | horns gone from cottages, real turf roofs in the north; the desert cottage is still a stepped tower | `ba_cottage.webp` |
| **Observatory** | oxidised copper cube heaps on crenels | smooth grey stone in temperate, but still a stepped heap, still copper in four cultures | `ba_observatory.webp` |
| **Night** | windows glow, streets black | interiors fully lit and furnished in every storey; streets still dark between pools of door light | `ba_town_night.webp` |

The natural-surface rule now holds almost everywhere: no light blue, cyan, lime, magenta or purple dyes appear on the
surface in any town render. What is left of it: a saturated **blue dome** on the mediterranean chapel (s1) and a
**yellow** one (s0), the **turquoise copper** on northern and moorland clock towers, wisteria and moorland bell towers
and four cultures' observatories, and the dye works' primary-colour racks (§8 items 11, 20, 21).

---

## 3. Whole towns

### Temperate / Bohemia

**Walled city (day 300, 538 plots).** `img2/city_temperate_walled_d300.webp`, `img2/close_temperate_walled_heart.webp`,
`img2/city_temperate_walled_d300_night.webp`, `img2/close_temperate_walled_heart_night.webp`. The best town Hearth
makes, and the first that reads as a Bohemian town: inside the ring wall a dense block of white rendered houses under
red tile, three and four storeys, timber-framed upper floors, a dark-helmed tower, a church with a shingle spire;
outside, timber farmsteads under brown shingle and thatch climbing the hill, and the white twin-towered cathedral on the
rise. Verticality comes from density and the hill, not from towers of flats: exactly what was asked. Problems: the
wall's merlons still read broken at 100 blocks; the space between the inner ring and the outer farms is grass, not
gardens and lanes; the night overview is a dark field with halos round each door. Grade **B+**.

**City on hills, farmsteads layout (day 250, 536 plots).** `img2/city_temperate_hills_d250.webp`,
`img2/close_temperate_heart.webp`, `img2/close_temperate_bell_tower.webp`. Every building is better than in review 1
and the town is worse: the farmsteads layout never nucleates, so a city of 209 souls is 500 detached plots strewn over
the hills with grass between them. Four-storey town houses stand alone in the meadow, showing blank flanks meant for a
neighbour; there is no street wall, no square, no market place; the heart close-up is a gravel crossing with a few
houses round it. The bell tower stands alone in a field a hundred blocks from the heart, with a brick kiln beside it
(`close_temperate_bell_tower.webp`). From 100 blocks it reads as a suburb, not a town. Grade **C+**. Seed 6 at day 150
(`town_temperate_farmsteads_d150.webp`) is the same shape smaller; a town on the Expanse Earth terrain
(`town_temperate_earth_d160.webp`, linear layout) is better because the river and the valley pull it into a line, but
it also leaves the hill tops bare and its landmarks at the edges.

**Ring town on a river (day 300).** `img2/city_temperate_river_d300.webp`, `img2/close_temperate_river_heart.webp`.
Watermills, a bridge, quays and a market on the bank read well; the cathedral was sited on the river's far edge at the
corner of the town, not over the market. Several houses in the heart are roofless shells: they are construction sites
(the preview draws a building plot as far as its stage, and nothing marks a site as being built), and they read as a
burnt-out quarter (§8 item 8). Grade **B-**.

**Town (day 120) and village (day 70).** `img2/towns_d120_a.webp`, `img2/villages_d70_a.webp`,
`img2/close_temperate_village_d70.webp`. The village is a timber hamlet: plank and log cottages and grey stone, a gravel
crossing with a well. Honest for a young hamlet, but not yet the whitewashed KCD village (Skalitz, Ledetchko): white
render under shingle and thatch arrives only with tier, and the cottage still shows a short wall under a big roof
(§8 item 22). There is no village green (*náves*) with a pond, lime tree or chapel at the heart (§8 item 24). A dark
slab bar with lanterns floats over the village's corner (a walkway deck, §8 item 5).

### Whole-town problems

**Street life.** The squares now hold low linen-roofed stalls, benches, wells, a pillory, a fountain: they read as
markets, not fairs. Between buildings, though, the streets are bare: carts, barrels, firewood, washing lines and
people-sized clutter appear in yards and workshops but not along the streets, and the dispersed layouts have no street
to put them in. Signs hang on shops (good).

**Density.** Three layouts give a town a dense heart: **walled** (temperate s4, desert, steppe, mesa), **mountain**
(alpine) and **cliff** (karst, mesa). The **farmsteads** layout, chosen for temperate, northern, alpine and moorland on
hills, stays a scatter whatever the head count; **crossroads** and **linear** (wisteria, maritime, tropical,
mediterranean on hills, karst, bayou) give streets but leave wide gaps between plots. None of the layouts builds
continuous terraces: rowhouses, townhouses and shophouses stand apart with a block or two of grass between them, so
the blank party walls they are designed with face the street (§8 items 1, 3, 9).

**Skyline.** No more high-rise. The skyline's problem now is *where* its towers stand: the planner's landmark siting
puts the bell tower, clock tower or cathedral at the edge of the built area or alone on open ground (temperate hills,
river and Earth towns; northern; moorland), and the hill tops of hilly sites stay empty. A KCD town's skyline is a
church tower and a town tower over the square, and a castle or church on the hill (§8 item 2).

**Colour at 30 blocks.** Good in temperate (white, red tile, dark shingle, brown timber), mediterranean (cream,
white, rose, red tile), alpine (white stone foot, dark boards, grey slab), bayou (grey boards, brick, black hip roofs),
moorland (grey stone, brown thatch). Mottled patches remain in **mesa** (pink, white and brown terracotta in blotches
on the town hall, chapel, townhouse, almshouse), **desert** (town hall, merchant house) and **steppe** (town hall,
townhouse brick). **Colour at 100 blocks:** northern is one mud brown (dark logs, brown shingle, little Falu red or
turf showing); wisteria one terracotta pink with black timber; tropical one straw yellow; steppe brown with **pink**
walls (the ring wall and towers read as pink plastic); tundra black on white. Temperate, mediterranean, karst and
alpine read as varied but coherent (§8 items 6, 15).

**How villages read from afar.** At day 70 every culture's village reads as a hamlet of its land
(`villages_d70_a/b.webp`): timber in the north, thatch in the tropics, mud brick and flat roofs in the dry lands, grey
stone and thatch on the moor. They are small and loose; none has a centre that reads from afar (a church tower, a
green, a pond). Floating debris above the temperate, moorland and karst hearts is the most visible fault at this scale.

**Vegetation.** Inside towns, green comes only from gardens, hedges round plots, the odd fruit tree and window boxes.
There are no street trees, no lime tree on the square, no orchards behind the houses, no churchyard trees. Cherry
trees in karst and palms in the desert are the exception. (Expanse trees were not in the renders; see §1.)

**Roads.** Village roads are thin dirt and gravel strips that read as dotted lines from 100 blocks; town streets are
gravel and cobble, but rarely wider at the square than in the lanes. Mediterranean streets are a bright white (they
glare like painted lines on the green slope), karst and desert streets pale. The alpine mountain town's gravel streets
on stone retaining walls are the best roads in the catalogue. A tropical coast town crosses its land with long rows
of stilt walkway posts that read as fences or pylons (`hab_tropical_coast.webp`, right side).

**Transitions to the countryside.** Towns stop dead on bare grass. A city of 209 souls has eight to twelve fields, a
pasture or two and a vineyard: nowhere near the belt of strip fields, orchards, gardens and paddocks a KCD town sits in.
The walled temperate town comes closest because its outer farmsteads make a ring. Expanse owns the hedgerows and land
between towns (memory note: Hearth owns villages and worked buildings), so this is Hearth's half: fields along the
roads out of town, orchards and gardens behind the houses, a paddock by every farmstead (§8 item 4).

**Floating pieces** (the probe, day 300, hills, 14 cultures; confirmed in the renders):

| what | where | seen |
|---|---|---|
| `infra/walkway` deck, 52 blocks of log and slab, 9 blocks over the ground | wisteria plot 577; the slab bars with lanterns over the temperate (d70) and moorland villages and the karst cliff (also `requests/w6-cliffs.md`) | `close_temperate_village_d70.webp`, `close_cliffs.webp` |
| `landscape/festival_ground` trees, benches and stairs 3-4 blocks over a slope | alpine plot 132 (8 pieces) | probe |
| `civic/tilt_yard` fence posts, lantern, campfire over a slope | mediterranean, bayou, mesa | probe |
| a light block over an inn's door | tropical (invisible in game; harmless) | probe |

**Construction sites.** A plot being built shows its finished stages and nothing else: roofless walls with empty
window holes. On a big town a dozen such shells stand at once and read as ruins or a fire (`close_temperate_river_heart.webp`,
the hollow white and tan cubes in `town_temperate_earth_d160.webp`). The sim stalls stages on goods for days
(SIM.md §4), so in game they stand as long. Plausible, not measured in game: a site wants scaffolding, a stack of its
materials and a hurdle fence (the lawhouse kit already has `Scaffold`).

**Night.** The interiors pass the light rule and are furnished to the walls on every storey of the walled town by
night; windows glow. Between houses the streets are dark except for pools at doors, and the overview by night shows
halos of lit grass round each house rather than lit streets. **Every chimney carries a lit campfire on its top**
(`kit/Chimneys`, line 45): from the street and by night every roof wears a flame, which reads as chimneys on fire;
recessing the campfire one block into the stack would keep the smoke and hide the flame (to check in game, §8 item 18).

### Other cultures

| town | images | read | grade (review 1) |
|---|---|---|---|
| **Alpine** mountain (steep, day 250) | `hab_alpine_mountain.webp`, `close_alpine_heart.webp` | Terraced streets on stone retaining walls climbing the slope, white stone ground storeys under dark boarded upper floors and grey slab roofs: the best layout in the catalogue. A turquoise copper cap and a green dome still show on the skyline; on hills the farmsteads layout scatters it like temperate. | **B+** (B-) |
| **Mediterranean** hillside (slope, day 300) | `hab_mediterranean_hillside.webp`, `ba_mediterranean_close.webp` | Reads Italian at once: cream, white and rose render, red tile, three- and four-storey houses, a campanile, gardens; mottling gone. White streets glare; the chapel's dome is saturated blue or yellow; streets are wide and the houses loose for a hill town. | **B+** (B) |
| **Karst** cliff (day 300) | `hab_karst_cliff.webp`, `close_cliffs.webp`, `close_karst_heart.webp` | Now uses its cliff: dwellings and stairs stacked up the face, a pagoda over the town, white scholar houses under black tile and green glaze, cherry trees. The cliff dwellings' roofs are hay-yellow squares that read as bales on the face; the town on the plain is a busy patchwork (green, yellow, black, white, blue pools). | **B** (C+) |
| **Mesa** cliff (day 300) | `hab_mesa_cliff.webp`, `close_cliffs.webp`, `close_mesa_heart.webp` | Pueblo terraces stacked up the face with ladders: right. Walls still mottled pink, white and brown; grey stone domes; a lone sandstone pillar on the crest (`requests/w6-cliffs.md`). | **B** (B-) |
| **Desert** walled (flat, day 300) and hills | `hab_desert_walled.webp`, `close_desert_heart.webp`, `ba_desert_close.webp` | The carnival is over: plaster domes on drums, linen canopies, palms, courtyards. Merlons remain on townhouses, merchant houses and shophouses; the town hall is mottled; an orange tower-house block stands out. | **B** (B-) |
| **Bayou** lake (stilt, day 250) and hills | `hab_bayou_lake.webp`, `close_bayou_lake_heart.webp`, `close_bayou_heart.webp` | Stilt houses over the water under weathered board roofs, a church with two dark-capped towers: good. Inland on hills, four-storey timber and white boxes with blank flanks. | **B** (B-) |
| **Maritime** coast (day 300) | `hab_maritime_coast.webp`, `close_maritime_coast_heart.webp` | Piers along the shore, a red and white lighthouse (right now), a grey cathedral. The town is still grey on grey: stone, slate and grey render blocks; little colour of boats, nets, red boards. | **B-** (C+) |
| **Tropical** coast (day 300) | `hab_tropical_coast.webp`, `close_tropical_heart.webp` | Lower and thatched; good piers. One straw yellow from end to end, and long lines of walkway posts cross the land. | **B-** (C+) |
| **Wisteria** hills and river (day 250) | `city_wisteria_hills_d250.webp`, `hab_wisteria_river.webp`, `close_wisteria_heart.webp` | A half-timbered river town with a cathedral and a pagoda: lively close up, but one terracotta pink from afar, with black-and-white four-storey frames and teal copper on the bell tower. | **B-** (B) |
| **Northern** hills and river (day 250) | `city_northern_hills_d250.webp`, `hab_northern_river.webp`, `ba_northern_close.webp` | Polka dots gone; turf and stave roofs good. Farmsteads layout scatter; at 100 blocks one dark brown; tall town homes with blank pale flanks. | **B-** (B-) |
| **Moorland** hills and flat (day 250-300) | `city_moorland_hills_d250.webp`, `hab_moorland_flat.webp`, `close_moorland_heart.webp` | Now a town at last (209 folk): grey stone and brown thatch, white harled houses, kirk and tower: reads Scottish. Crenellated stone towers stand open-topped; a stair walkway floats. | **B** (village only) |
| **Steppe** walled (flat, day 300) and hills | `hab_steppe_walled.webp`, `close_steppe_heart.webp` | Brown mud brick, yellow thatch, felt-grey roofs, timber-framed houses: the single-orange problem is gone. The ring wall and towers are pink; four-storey mud towers; mottled brick. | **C+** (C) |
| **Tundra** ring (flat, day 300) and hills | `hab_tundra_ring.webp`, `close_tundra_heart.webp` | Dark timber on snow; tall vertical-plank houses with blank flanks, a grey slab tower, dispersed. The snow-patch roofs are gone. | **C+** (C+) |

---

## 4. Families

Grades: **A** ready · **B** good, small fixes · **C** needs rework. Arrows give review 1's grade.

### Town homes (`townhome.md`): C+ → **B-**
`img2/sheets/house_{tenement,townhouse,rowhouse,shophouse,merchant_house,tower_house,manor}_flat.webp`

- **tenement C → B.** Four storeys under a steep roof (temperate a stepped gable, wisteria half-timbered), flat roofs
  only in desert, mediterranean and mesa. Northern s0 and tundra show a tall dark box with a pale blank flank.
- **townhouse B- → B.** Three or four storeys; temperate white render with a stepped gable. Desert both seeds and mesa
  carry merlon crests; tundra and northern dark boxes with blank flanks.
- **rowhouse B.** Right size; blank flanks are correct in a row but every town places them detached.
- **shophouse B- → B-.** Desert s1 an orange crenellated block, mesa mottled; temperate s0 good.
- **manor B+.** Temperate s0 now white render under red tile (Bohemian); steppe and mesa still crenellated boxes;
  tundra black.
- **tower_house B-**, **courtyard_house A-** (unchanged).

### Homes by station (`station.md`): B- → **B**
`img2/sheets/house_{merchant_house,scribe_house,shack,reeve_house,rectory,hovel,hermit_hut}_flat.webp`

- **merchant_house C → B.** Three or four storeys with a loft; temperate white render, red tile, stepped gable. Desert
  s1 an orange crenellated tower; northern/tundra blank flanks.
- **scribe_house C → C+.** Still a six- to seven-wide, four-storey tower in every culture.
- **shack C+ → B.** Two storeys; desert and mesa are a grey or orange hollow cube.
- **reeve_house B- → B**, **rectory B → B+** (polka dots gone), **hovel B**, **hermit_hut B-** (desert/mesa hollow boxes).

### Homesteads (`homestead.md`): **B+**
`img2/sheets/house_cottage_flat.webp`. Cottage B: temperate s0 still a big brown roof over a short white wall; northern
and tundra turf roofs are good; the desert and mesa cottages are still stepped towers with holes.

### Craft (`craft.md`): B- → **B**
`img2/sheets/work_{tannery,butcher,chandler,paper_mill,dye_works}_flat.webp`, `img2/detail_dye_works_temperate.webp`.
Tannery, butcher, chandler, paper mill **B**: lower, no polka dots, single-colour awnings. **Dye works B-**: the racks
are madder, woad and weld now, but as full wool blocks in a grid of red, blue and yellow they read as a flag, the
brightest thing in any town.

### Civic and commerce (`civic.md`): B- → **B**
`img2/sheets/civic_{town_hall,chapel,library,bathhouse,school,guardhouse}_flat.webp`, `img2/sheets/commerce_{market_hall,inn,bank,market_stall,tavern,hostel}_flat.webp`

- **town_hall C+ → B.** Temperate **A-** (a *radnice*). Northern, tundra, moorland, maritime and alpine halls are a
  nave with a west tower and spire: from afar they are churches. Desert and mesa halls are mottled and open-topped.
- **chapel B → B+.** Temperate A- (white tower, dark onion or tent roof). Mediterranean s0 yellow dome, s1 saturated
  blue dome; desert and mesa missions still roofless; steppe a brown blob.
- **market_stall C → B+** (one plan in all cultures, but the right one). **market_hall B**, **inn B+**, **bank B**,
  **school B**, **library B**, **bathhouse B** (desert and mesa domes still lumpy grey heaps).
- **guardhouse B- → C+.** Moorland, maritime, desert, mediterranean and mesa are open crenellated boxes; now that the
  rest of the catalogue is calm they stand out as ruins.

### Landmarks (`landmark.md`): **B**
`img2/sheets/landmark_{bell_tower,clock_tower}_flat.webp`. Bell tower B- → **B** (temperate A-; wisteria and moorland
still teal copper with pinnacles). Clock tower C+ → **B** (five manners; northern and moorland grey Gothic tower with a
green copper spire and pinnacles, tundra the same in black: still Big Ben's family). Cathedral A-.

### Clergy and learning (`learning.md`): B- → **B-**
`img2/sheets/civic_{observatory,monastery,university,shrine}_flat.webp`, `img2/sheets/work_alchemist_tower_flat.webp`.
**Shrine B- → B+** (temperate a white pillar with a little roof and cross: a *boží muka*). Monastery, university B.
**Observatory C → C+**: stone instead of copper in temperate, but a stepped heap with no drum and no slit, and
turquoise copper in alpine, maritime, tropical and bayou. **Alchemist tower C+**: top-heavy, one plan (0.95).

### Nobility (`noble.md`): B- → **B**
`img2/sheets/noble_{castle,keep,hunting_lodge,palace,stables}_flat.webp`. Castles now have plans per culture (borg,
compound, ordu, qasr, rocca, Bohemian); temperate s0 white render and red tile. Wisteria (grey with ten red cones) and
alpine (white with black cones) are still fairy-tale. Being reworked in parallel; not graded further. Keep B, hunting
lodge B (quoins tonal now).

### Social life (`sociallife.md`): B- → **B-**
`img2/sheets/civic_{almshouse,infirmary,orphanage,tilt_yard,playhouse,cemetery}_flat.webp`, `img2/sheets/landscape_festival_ground_flat.webp`.
Tavern B+. Almshouse, infirmary, hostel B- (desert and mesa open boxes). **Tilt yard C+ → B-**: white and red
pavilions now, but one plan everywhere and its fences float on slopes. **Festival ground C+ → B-**: linen awnings,
but trees and benches float on slopes.

### Deep (`deep.md`): B- → **B**
`img2/sheets/deep_{hall,dwelling,market,forge,lift}_flat.webp`. The hall has plans of its own in ten cultures (stave
hall, tiered pavilion, pueblo, loggia, stilt hall); temperate, alpine, moorland and maritime keep the green-copper
basilica. Dwelling, market, forge and lift are one plan each (0.99-1.00).

### Interiors and night: **B**
`img2/close_temperate_walled_heart_night.webp`, `img2/close_temperate_heart_night.webp`. Interiors lit and furnished
storey by storey; streets dark; flames on every chimney.

---

## 5. Cultures

*Homes* and the other columns grade the catalogue as each culture builds it; *town* is §3's town render.

| culture | homes | work | civic / social | landmarks / noble | town | the main problems now |
|---|---|---|---|---|---|---|
| **temperate** | B | B+ | B+ | B+ | B (walled B+, farmsteads C+) | farmsteads scatter; landmarks at the edge; timber villages; heavy cottage roof |
| wisteria | B- | B+ | B | B- | B- | one terracotta pink; tall half-timber frames; teal bell tower |
| northern | B- | B | B- | A- (stave) | B- | one mud brown; blank flanks; town halls read as churches; scatter |
| alpine | B | B | B | B | B+ (mountain) | turquoise caps and observatory; scatter on hills |
| tundra | C+ | B- | B- | B- | C+ | tall dark boxes; blank flanks; Gothic black clock tower |
| moorland | B | B | C+ | B- | B | roofless crenellated guardhouse and towers; teal spires |
| maritime | B- | B | B- | B | B- | grey on grey; open guardhouse |
| tropical | B | B | B | B | B- | one straw yellow; walkway post lines |
| desert | C+ | B- | B- | B+ | B | merlons on town homes; mottled halls; open boxes; lumpy library and bath domes |
| mediterranean | B+ | B | B | B+ | B+ | white streets; blue and yellow chapel domes; open guardhouse |
| steppe | C+ | B- | C+ | C+ | C+ | pink walls; mottled brick; crenellated manor; four-storey mud towers |
| karst | B | B | B | A- (pagoda) | B | hay-yellow roofs on the cliff; busy colour on the plain |
| bayou | B | B | B | B | B | inland timber boxes with blank flanks |
| mesa | C+ | B- | C+ | B | B | mottled terracotta; crenellated manor; roofless mission and hall; open boxes |

---

## 6. The similarity check

`cultureSimilarity` over the whole catalogue (two seeds, threshold 0.8): **85 of 121 archetypes flagged** (one plan
reused across half the cultures or more); over what the day-250 cities actually built, 64 of 97
(`docs/review/similarity_2.txt`). The flag is honest about plans but not about looks: a palette change that turns a
plan into a different building (the market hall, the inn) still counts as one plan, and many flagged archetypes are
yards and posts nobody will compare across cultures. Which ones **matter visually**, ranked by how often a town shows
them and how far they read:

| rank | archetypes (likeness across cultures) | why it matters | lane |
|---|---|---|---|
| 1 | **town homes**: merchant house 0.85, scribe house 0.84, rowhouse 0.84, tenement 0.80, shophouse 0.77, townhouse 0.71, reeve house 0.77 | the street fabric: dozens per town, and the reason a northern, tundra, bayou and karst street are the same tall box in different wood | T5 |
| 2 | **town wall 0.96, gatehouse 0.96, watchtower 0.82** | the silhouette of every walled town (temperate, desert, steppe, mesa) from 100 blocks | T10 |
| 3 | **alchemist tower 0.95, university 0.87** | tall or big, in every town of size | T8, T7 |
| 4 | **inn 0.66, tavern 0.52, hostel 0.68, bank 0.67** | on the square; but palettes and roofs already vary them a lot (low priority) | T8 |
| 5 | **tilt yard 0.95, playhouse 0.92, stables 0.90, hunting lodge 0.87** | large footprints that read from afar | T9, noble lane |
| 6 | **brick kiln 0.94, smelter 0.65, glassworks 0.84** | the salmon bottle kiln is unmistakable and stands in every town | T10 |
| 7 | **jail 0.87, watch house 0.83, pillory 0.85, gallows 0.85, toll gate 0.84** | small; the jail and watch house sit on squares | T10 |
| low | market stall 1.00, palisade 1.00, bell 1.00, lamp post 0.88, well 0.79, cemetery 0.80, quarry, sawmill, masonry, logging camp, charcoal kiln, builders' yard, caravan yard, quarry pit, mine | yards and street furniture: one plan is acceptable | - |
| low | deep forge, market, lift, dwelling, glow garden (0.99-1.00) | underground, fantasy allowed; vary the lift and dwelling silhouettes when the deep lane next works there | - |

Not flagged now (plans per culture): castle 0.27, chapel 0.38, deep hall 0.38, shrine 0.38, courthouse 0.39, temple 0.41,
clock tower 0.43, cottage 0.46, town hall 0.53, bell tower 0.54. Several of those vary little *between seeds* of one
culture (town hall 0.98, observatory 0.98, market hall 0.97, shrine 1.00, hovel 0.97): every temperate town hall is the
same building, acceptable while a town has one.

---

## 7. The remaining wave 6 requests

The visual requests still open in `docs/requests/w6-*.md`, checked against the renders, and where they went:

| request | seen in the renders | top 30 |
|---|---|---|
| w6-plan §6: let tall homes take `storeys` 2 (tenement 3) | yes: village and outskirts town houses are three and four storeys | 9 |
| w6-homes: no archetype where the tier cap is under its `minHeight`; catalogue heights for townhouse and tenement | the merchant house is still the village's tall house (fine, as asked) | 9 |
| w6-palettes §1: market stall and fair stripes from `habits().stripes()`; dye kits off the six natural colours | stalls fine; dye works and tilt yard keep their own colours | 21, 28 |
| w6-palettes §2: raise temperate cottage, croft, farmhouse eaves | yes: temperate cottage s0 | 22 |
| w6-palettes §3, w6-walls §3: quoins in defence, lawhouse, landscape towers, furnace | gatehouse and watchtower quoins still alternate | 17 |
| w6-walls §4: white shutters and lattice on dark log walls | small white squares on tundra and northern paper mill and rowhouse | 15 |
| w6-walls §5: full-block masonry lintels on render | not checked at town scale (the request's own finding stands) | 23 |
| w6-walls §1-2: great hall dragon heads, tundra guardhouse crown | yes: crossed dragon boards three or four blocks long (`close_northern_great_hall.webp`) | 12 |
| w6-bohemia §1: burgher brick off the town hall; red tile church naves from tier 1; dark temperate `sheet` | the sheet is slate now (done); town hall brick share not seen on the two seeds; church naves dark shingle | 23 |
| w6-bohemia §2: `Towers.spire`, `Crowns.Mats.coppered` copper in temperate | no teal in temperate; teal remains in wisteria, northern, moorland | 11 |
| w6-bohemia §3: market hall cupola in Bohemian shingle | temperate market hall cupola fine | - |
| w6-crowns §2: `MarketHall.domes` once, centred | desert and mesa market hall domes | 10 |
| w6-crowns §3: library and hammam domes from `Adobe.dome` | yes: lumpy grey heaps on desert and mesa library and bathhouse | 10 |
| w6-crowns §4: `BellTower` with `clock=true` still lays Big Ben dials | wisteria and moorland bell towers | 11 |
| w6-crowns §1, §5: no second minaret in desert/steppe towns with a temple; minaret has no bell | yes: the desert walled city's minaret clock tower stands beside the temple's minaret (`close_desert_clock_tower.webp`) | 11 |
| w6-dry §1: raised corners on desert and mesa inn and commerce roofs | yes: desert and mesa inn, bank | 7 |
| w6-dry §2: smithy and `civic/Adobe.awning` in one colour | not seen striped in the renders | - |
| w6-cliffs: floating slab bar over karst cliff; lone sandstone pillar on mesa crest | yes, both; the bar is a walkway deck (the probe) | 5 |
| w6-noble §2: steppe ordu wall and roof contrast | the ordu is one brown mass (castle lane, in progress) | - |
| w6-interiors: inn carriage passage bare | not visible at town scale | - |
| w6-sim §2: camp and hamlet buildings from local rough goods | villages read timber and stone (good); nothing else visual | - |

---

## 8. The top 30, grouped for architects

Ranked by how much each would make a Hearth town read as a real place. Each lane owns its files, so lanes run in
parallel without conflicts; inside a lane, do the items in rank order. Castles and keeps are with the castle lane and
left out here.

| lane | owns | items |
|---|---|---|
| **T1 Town form** | `plan/layout/**`, `plan/growth/{Housing,Lots,Parcels,Square,Expand,Grid,Stacks}.java` | 1, 3, 24 |
| **T2 Skyline and street furniture** | `plan/growth/{Landmarks,Siting,Beauty}.java` | 2, 19 |
| **T3 Edges and fields** | `plan/growth/Farms.java`, `build/archetype/farm/**` | 4 |
| **T4 Roads and walkways** | `plan/road/**`, `build/archetype/infra/**`, `build/kit/infra/**` | 5, 16 |
| **T5 Homes** | `build/archetype/house/**`, `build/kit/townhome/**`, `build/kit/homestead/**` | 7, 9, 22, 25, 29 |
| **T6 Palettes** | `resources/data/hearth/hearth_style/*.json`, `resources/data/hearth/**/region/*.json`, `build/style/**` | 6, 15, 23 |
| **T7 Crowns and domes** | `build/kit/civic/{Towers,Adobe}.java`, `build/kit/landmark/**`, `build/archetype/landmark/{BellTower,ClockTower,GreatHall}.java`, `build/archetype/civic/{Observatory,Library,Bathhouse,University}.java`, `build/archetype/commerce/MarketHall.java` | 10, 11, 12 |
| **T8 Civic and social shells** | `build/archetype/civic/{Guardhouse,TownHall,Chapel*,Almshouse,Orphanage,Infirmary,School}.java`, `build/archetype/commerce/{Inn,Hostel,Tavern,Bank}.java`, `build/archetype/work/AlchemistTower.java`, `build/kit/civic/{Bohemian,Halls,Storeys,Sacred,Portico,Arches}.java`, `build/kit/sociallife/Shell.java` | 13, 14, 20, 26 |
| **T9 Fairs, markets, yards** | `build/archetype/commerce/MarketStall.java`, `build/kit/sociallife/Fair.java`, `build/archetype/landscape/FestivalGround.java`, `build/archetype/civic/TiltYard.java`, `build/archetype/work/DyeWorks.java` | 21, 27, 28 |
| **T10 Walls, law and kilns per culture** | `build/archetype/defense/**`, `build/kit/landscape/Towers.java`, `build/archetype/law/**`, `build/kit/lawhouse/**` (not `Scaffold`), `build/archetype/work/BrickKiln.java`, `build/kit/industry/Furnace.java` | 17, 30b |
| **T11 Sites and smoke** | `build/kit/Chimneys.java`, `build/blueprint/**` (stage assembly), `build/kit/lawhouse/Scaffold.java`, `build/kit/light/**` | 8, 18 |
| **T12 Checks and preview** | `build/check/**`, `src/gametest/java/dev/voidmc/hearth/gametest/render/**` | 30 |

1. **[T1] Farmstead towns that become towns.** The farmsteads layout (chosen for temperate, northern, alpine and
   moorland on hills) grows to 500 plots and a city's head count as a scatter of detached houses on grass, with four-
   storey town houses alone in the meadow and no square (`city_temperate_hills_d250.webp`, `city_northern_hills_d250.webp`,
   `town_temperate_farmsteads_d150.webp`). A Bohemian village is a *náves* (a green with farmsteads round it) and grows
   into a town round a *náměstí*. At town tier, a farmsteads settlement should found a market street or square at its
   heart and put town homes there in rows; farmsteads stay round the edge. Compare `city_temperate_walled_d300.webp`.
2. **[T2] Towers over the square and on the summit.** Landmarks and churches are sited at the edge or alone on open
   ground: the temperate bell tower 100 blocks from the heart in a field beside a brick kiln (`close_temperate_bell_tower.webp`),
   the river town's cathedral in the corner, the Earth town's towers at its ends; hill tops stay bare. Site the church
   and town tower on or beside the square (within ~40 blocks of the heart), the second landmark (castle, cathedral,
   pagoda, bell tower) on the highest buildable ground in sight of the heart, and keep industry (kilns, tanneries) off
   the landmark's close.
3. **[T1] Street walls.** Rowhouses, townhouses, shophouses and tenements are placed one or two blocks apart, so their
   blank party walls face the street and a street is a row of separate towers (`close_bayou_heart.webp`,
   `close_tundra_heart.webp`, `close_northern_heart.webp`). In a town's core, lot them as continuous terraces sharing
   walls along the street frontage, with a gate or passage now and then; detached only outside the core.
4. **[T3] A countryside belt.** A city of 209 has eight to twelve fields and stops dead on bare grass. Grow strip fields
   along the roads out of town, orchards and kitchen gardens behind the houses, a fenced paddock by every farmstead,
   a vineyard or hop garden on a slope; enough that from 100 blocks the town sits in worked land (Expanse owns the
   hedgerows and woods beyond).
5. **[T4] Walkways that stand on something.** `infra/walkway` lays decks of log and slab up to nine blocks above ground
   with no support: the dark slab bars with lanterns over the temperate and moorland villages and the karst cliff
   (`close_temperate_village_d70.webp`, `close_cliffs.webp`, `requests/w6-cliffs.md`), and the long lines of posts across
   the tropical coast town. A walkway needs a post to the ground at least every four blocks, or should not be laid.
   Also check the mesa crest's lone sandstone pillar (same request).
6. **[T6] The dry cultures' mottled walls.** Mesa (town hall, chapel, townhouse, almshouse: pink, white and brown
   terracotta in blotches), desert (town hall, merchant house) and steppe (town hall, townhouse brick) still mix
   contrasting blocks in small patches (`sheets/civic_town_hall_flat.webp`, `close_mesa_heart.webp`). Apply review 1's
   rule (second material under ~10 %, tonal, patches of 4+, weathering at foot and eaves) to the mesa, desert and
   steppe palettes as it was applied to temperate and mediterranean.
7. **[T5] Merlons and hollow cubes on dry homes.** Desert townhouse (both seeds), merchant house s1, shophouse s1
   (orange crenellated blocks), the desert and mesa cottages (stepped towers with holes), steppe and mesa manors
   (crenellated boxes), the desert and mesa shack, hut and hermit hut (hollow grey or orange cubes), and the inn and
   commerce roofs' raised corners (`requests/w6-dry.md` §1; `sheets/house_townhouse_flat.webp`, `sheets/house_shack_flat.webp`).
   Plain parapets with coping and rounded shoulders, a roof terrace with a shade, a lean-to of poles and reeds.
8. **[T11] Construction sites, not ruins.** A building plot shows its finished stages as roofless shells with empty
   window holes; a big town has a dozen at once (`close_temperate_river_heart.webp`, the hollow cubes in
   `town_temperate_earth_d160.webp`). Give a site scaffolding round its rising walls (`kit/lawhouse/Scaffold`), a
   hurdle fence, its next stage's materials stacked by the door, and a temporary board roof over a finished storey
   while the roof stage waits.
9. **[T5] Two-storey town homes and dressed flanks.** The planner asks for `storeys` 2 in villages, outskirts and inner
   towns (`requests/w6-plan.md` §6), but `Townhouse`, `Tenement`, `MerchantHouse` and `TowerHouse` clamp to 3-4 at least,
   so the edges of every town are three- and four-storey boxes. Let the param go to 2 (tenement 3) and, when a town home
   stands detached (no neighbour within two blocks), give its side walls windows and a chimney instead of a blank party
   wall (northern, tundra, bayou: `sheets/house_tenement_flat.webp`).
10. **[T7] Domes and the observatory.** The observatory is still a stepped heap with no drum and no slit, in turquoise
    copper in alpine, maritime, tropical and bayou (`ba_observatory.webp`); the desert and mesa library and bathhouse
    domes are lumpy grey heaps (`requests/w6-crowns.md` §3), and `MarketHall.domes` should build one centred dome (§2).
    A smooth hemisphere on a round drum, a slit and a lead or dark sheet; one dome per building.
11. **[T7] The last teal spires and Big Ben dials.** Northern and moorland clock towers are grey Gothic towers with
    green copper spires and pinnacles, tundra the same in black; wisteria and moorland bell towers are teal needles,
    and `BellTower` with `clock=true` lays the old dials (`requests/w6-crowns.md` §4; `sheets/landmark_bell_tower_flat.webp`,
    `sheets/landmark_clock_tower_flat.webp`). Dark shingle or slate helms; a clock in a one-block dial; no pinnacles on
    timber cultures' towers. And no second minaret in a desert or steppe town that has a temple (`w6-crowns.md` §1, §5).
12. **[T7] Great hall dragon heads** still run four blocks past the apex (`requests/w6-walls.md` §1): one block of neck,
    the head over it. The tundra guardhouse crown the same (§2; the crown is T7's).
13. **[T8] No roofless crenellated boxes.** The guardhouse in moorland, maritime, desert, mediterranean and mesa, the
    desert and mesa mission chapels and town halls, and the desert and mesa almshouse family are open-topped boxes that
    read as ruins now that everything round them is roofed (`sheets/civic_guardhouse_flat.webp`, `sheets/civic_chapel_flat.webp`).
    A roof, or a parapet with a coping over a visible roof deck with a hatch and a shade.
14. **[T8] Town halls that do not look like churches.** The northern, tundra, moorland, maritime and alpine town halls
    are a nave with a west tower and a spire, the chapel's silhouette (`sheets/civic_town_hall_flat.webp`). A hall
    turns its long side or a gable with a council-room oriel to the square, has an arcade or a stair to a first-floor
    door, and a tower with a gallery, clock and helm, not a spire.
15. **[T6] Colour from 100 blocks.** Northern is one mud brown (Falu red and turf barely show at town scale), wisteria
    one terracotta pink with black timber, tropical one straw yellow, steppe brown with pink ring walls, maritime grey
    on grey (`city_northern_hills_d250.webp`, `hab_wisteria_river.webp`, `hab_steppe_walled.webp`). Give each culture
    two or three roof and wall families at town scale (northern: Falu red boards, tar-black logs, turf; maritime: red
    and ochre boards, white harl; steppe: undyed felt, ochre mud, timber), and a paler mud for the steppe walls.
    The northern and tundra white shutters on dark logs (`requests/w6-walls.md` §4) go with it.
16. **[T4] A road hierarchy.** Village roads are thin strips that read as dotted lines from 100 blocks; mediterranean
    streets glare white, karst and desert ones pale; streets do not widen at squares. Main streets in a town cobbled
    and two to three blocks wider at the square, lanes in packed earth with worn edges and grass between wheel ruts,
    village roads dirt and coarse dirt; mediterranean streets in warm grey stone or brick, not white.
17. **[T10] Town walls and gatehouses per culture.** One plan in all 14 cultures (0.96); the merlons still read broken
    from afar; the steppe wall is pink; gatehouse and watchtower quoins still alternate (`requests/w6-walls.md` §3,
    `requests/w6-palettes.md` §3). Bohemian: rendered wall with a covered timber walk and tower gates with helms;
    desert and mesa: plain adobe with round towers; steppe: earth bank and palisade; northern: timber with stone gate.
18. **[T11] No flames on the chimneys.** `kit/Chimneys` puts a lit campfire on top of every chimney, so every roof wears
    a flame by day and a beacon by night (the yellow dot on each chimney in every close-up). Set the campfire one block
    down inside the stack with the stack's material round it, so the smoke rises and the flame is hidden (check smoke
    height in game; a hay bale under it raises the smoke).
19. **[T2] Lamps along streets.** By night the walled town's streets are dark between pools at the doors
    (`close_temperate_walled_heart_night.webp`, `city_temperate_walled_d300_night.webp`). A lamp post or wall lantern at
    every street junction, along the market street, at gates and on the square, as `Beauty` already does for squares.
20. **[T8] Mediterranean chapel domes.** s0's dome is saturated yellow, s1's saturated blue (the last strong dye on the
    surface). Red tile or lead-grey, with the lantern in stone.
21. **[T9] Dye works without a flag.** The racks are madder, woad and weld, but as a grid of full red, blue and yellow
    wool blocks they read as a flag (`detail_dye_works_temperate.webp`). Hang lengths of cloth (banners or carpets on
    rails) in two colours per works, muted by undyed and brown, with vats and drying poles carrying the scene.
22. **[T5] The whitewashed Bohemian cottage.** The temperate cottage s0 still shows a short wall under a big brown roof
    (`sheets/house_cottage_flat.webp`, `requests/w6-palettes.md` §2): raise the eaves to show a full storey of white
    render. At tiers 0-1 the temperate village is plank and log; a KCD village is white lime render under shingle and
    thatch even when poor, so lime render (over wattle) should be the village cottage's wall from tier 1.
23. **[T6] Temperate manners, the rest.** Drop `civic/town_hall` from burgher brick; red tile from tier 1 on about one
    church nave in three (`requests/w6-bohemia.md` §1); full-block brick lintels on render (`requests/w6-walls.md` §5)
    are a palette choice of `wall_accent` heads: use the wall's own stair or a tonal accent.
24. **[T1] A village green.** Villages (day 70) have a gravel crossing with a well at their heart. Give the temperate
    village a *náves*: a green with a pond or well, a lime tree, a wayside shrine or small chapel, the farmsteads round
    it gable-end on.
25. **[T5] The scribe house** is still a six- to seven-wide, four-storey tower in every culture
    (`sheets/house_scribe_house_flat.webp`); build it as a two- or three-storey house in a row, or let it share a
    terrace (item 3).
26. **[T8] The alchemist tower**: top-heavy, white-salted upper band, one plan (0.95). A squat stone tower with a
    lab wing and a chimney, varied by culture.
27. **[T9] Fair grounds on slopes.** Festival ground trees, benches and stairs and tilt yard fences, lanterns and
    campfires float three or four blocks over sloping plots (the probe: alpine, mediterranean, bayou, mesa; canopies and
    fences hang over the corner of the desert clock tower close-up, `close_desert_clock_tower.webp`). Set each
    piece on the ground under it, or terrace the plot.
28. **[T9] Market stalls per culture** (1.00 likeness) and stripes from the habit (`requests/w6-palettes.md` §1): the
    stall is right but identical in 14 cultures. A souk booth with hangings in the desert, a lacquered pavilion stall in
    karst, a plank booth with a shingle hood in the north.
29. **[T5] Karst cliff dwellings' roofs.** The stacked dwellings on the karst cliff roof in hay-yellow that reads as
    hay bales on the face (`close_cliffs.webp`); grey tile or the green glaze in small amounts, bamboo only as walls.
30. **[T12] A floating-piece check and a truer preview.** Add the probe used here to `BlueprintChecks` (every solid
    piece of a blueprint must touch the ground or the rest of the building) so items 5 and 27 cannot come back; in
    `townPreview`, draw a building plot's site dressing as the game would (item 8), give the steppe and mesa ground a
    noise pattern instead of the diagonal hash that stripes the land, and add Expanse vegetation to the town scene so
    the countryside transition can be judged.
    **30b [T10]** The brick kiln (0.94), jail (0.87) and watch house (0.83) per culture: a clamp kiln in the north, a
    beehive kiln in the dry lands; a lock-up in the town hall's ground floor where the culture has no jail of its own.

---

## 9. Image index

All in `docs/review/img2/` (WebP, about 20 MB): `ba_*` before and after (11); `city_<culture>_hills_d250` (14) and
temperate variants; `hab_*` habitats (13); `close_*` heart and landmark close-ups (27); `villages_d70_{a,b}`,
`towns_d120_{a,b}`; `detail_*` crops; `sheets/*` 61 contact sheets (7 cultures to a row; seed 0 then seed 1). Regenerate
with the commands in §1 (`townPreview` takes about 25 s a culture with night; `blueprintPreview` about 1 minute for 20
archetypes × 14 cultures × 2 seeds).
