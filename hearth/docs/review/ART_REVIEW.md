# Hearth art review (the LIVING_WORLD.md §8.6 gate), October 2026

An art director's pass over the whole catalogue (134 registered archetypes × 14 cultures, two seeds, flat and hills)
and over whole towns in eleven cultures, against the style bible (`docs/buildings/styles/`) and the user's three rules:
**the surface looks natural and real-world**, **temperate towns read as KCD II Bohemia**, **buildings look hand-built by
a skilled player**. Branch `hearth` at `3fdfb2d8`. No code was changed for this review.

**The verdict in one paragraph.** The single buildings are mostly good, often very good: the trade yards, the
workshops, the apiary, the quarry, the stables, the cathedral, the northern stave church, the pagoda, the
Mediterranean hill town and the decay pass would pass a lead artist's look today. What breaks the look of a real
world is not one bad building but **five habits that repeat across the whole catalogue**: (1) homes in towns grow into
five- to eight-storey towers, so every town of any size has a high-rise skyline; (2) render and stone walls are mottled
with contrasting blocks so whole streets read as camouflage or damage; (3) log and timber walls put a protruding
block on every other course and a white block at every window, so the northern and Bohemian timber houses read as
polka dots; (4) saturated dye colours (light blue, cyan, lime, magenta, purple) on awnings, stalls, booms, pavilions
and dye racks turn every market into a carnival; (5) civic crowns (onions, domes, cupolas) are piled from full cubes
and read as blobs or whipped cream. Fix those five and the catalogue moves from "good Minecraft build" to "place". The
temperate culture has one more problem of its own: its prominent buildings (chapel, town hall, manor, bell tower,
castle) too often wear the English and Hanseatic manners, so the market town reads North German brick with green
copper spires rather than Bohemia.

Contents: [1 Method](#1-method) · [2 Whole towns](#2-whole-towns) · [3 Family grades](#3-families) ·
[4 Culture grades](#4-cultures) · [5 Top 30](#5-the-top-30-grouped-for-architects) · [6 Contact sheets](#6-contact-sheets)

---

## 1. Method

| what | how | images |
|---|---|---|
| Contact sheets, every archetype | `BlueprintPreview all all 2 flat,hills` (sheets only), re-tiled 7 cultures to a row | `img/sheets/<archetype>_flat.webp` (134), seed 0 pairs `img/pair_*.webp` |
| Detail panels | `BlueprintPreview` panels at 16 and 36 px/block | `img/reeve_temperate_front.webp`, `img/rectory_northern_front.webp` |
| Night and interiors | `BlueprintPreview ... "flat;night=true"` for 22 key archetypes × 6 cultures (storey cuts lit by their own light) | `img/night_interiors.webp`, `img/night_inn_temperate.webp` |
| Whole towns, textured | a review-only renderer (MiniSim + the real build module + the textured `IsoRenderer`, overview and 60-block close-ups, by day and night); `settlementPreview` only draws map colours | `img/town_*.webp` |
| A declining town, decay stages | `AgeTown temperate 300 hills 2` (decline) and `AgeSheets decay` | `img/town_temperate_decline.webp`, `img/decay_stages.webp` |
| Deep town | `DeepTown alpine 2 wilds` by day and by its own light | `img/deep_town*.webp` |
| Style sets | `StyleSheets set temperate` (the buildings as the planner dresses them) | `img/temperate_set.webp` |

Not covered: in-game screenshots with Expanse blocks and real lighting (the offline renderer shows Expanse blocks as
their vanilla stand-ins), and the infrastructure archetypes (roads, bridges, docks need the planner's polylines; on a
bare plot they draw a stub, see `img/pair_infra_bridge_flat__infra_dock_flat.webp`). Towns came from the synthetic
terrains; the steep "alpine mining town" render clips the mountain (the town is a ribbon on the contour), so read it
for the buildings, not the land. Culture-gated archetypes (the courtyard house, palace, pagoda, caravanserai...) are
judged only in the cultures that can build them.

---

## 2. Whole towns

### Temperate / Bohemia

**Village (farmsteads, day 120, 43 folk).** `img/town_temperate_village.webp`, `img/town_temperate_village_close.webp`.
The best temperate image Hearth makes: scattered farmsteads under brown shingle and aged thatch, white render at the
foot, gardens, fences, a windmill. It reads as a Bohemian hamlet at 100 blocks. Problems: the houses' roofs swallow
their walls (a one-block band of white under a huge brown roof: at 30 blocks the village is brown, and KCD's villages
are *white* walls under dark roofs); three near-identical dark plank boxes stand in a row (the shack/croft lane);
the windmill's sails are thin white blocks that read as paper. Grade **B**.

**Market town (crossroads, day 220, city by count).** `img/town_temperate_market_town.webp`,
`img/town_temperate_market_town_close.webp`. The heart is a forest of five- and six-storey white towers (merchant
houses, town houses, scribe houses) standing among one-storey cottages, so the skyline is Manhattan, not Kutná Hora.
Red brick burgher blocks and green copper needle spires dominate; the market is a rainbow of candy awnings; birch logs
(white bark, black flecks) appear as posts and columns in the square. Nothing here reads KCD II except the odd
thatched cottage at the edge. Grade **C**.

**Ring town on a river (day 160) and city with castle (walled, day 700).** `img/town_temperate_ring_town.webp`,
`img/town_temperate_city_castle.webp`, `img/town_temperate_city_close.webp`, `img/town_temperate_city_close2.webp`. The
castle at its best is the most Bohemian thing in the catalogue: white rendered towers under red tile cones (Rattay).
The parish church with a dark shingle spire is right. But the town round it again climbs into brick and render towers
of six to eight storeys (one flat-roofed tenement block reads as a modern apartment building), the market hall wears a
crenellated brick tower, and the town walls' merlons read as ruins. Night (`img/town_temperate_city_close_night.webp`):
the windows glow and the interiors are lit, but the streets between are black: few lamps outside the square.
Grade **C+**.

**Declining town.** `img/town_temperate_decline.webp`, `img/decay_stages.webp`, `img/town_temperate_city_hills_close.webp`.
Decay itself is excellent: roofs open from the ridge, render falls in patches, moss and vines take the walls, the last
stage is a believable footing-and-chimney ruin (grade **A-**). The declining quarter reads in the town. Two notes: the
decayed tall town houses (five storeys of empty shell) look like bombed blocks rather than a dying market town, a
consequence of the heights (top-30 #1); and the 0.15 and 0.3 stages are hard to tell from 0 at town distance (a sagging
gate, weeds in the yard and a shutter hanging would sell "empty" earlier).

### Other cultures

| town | images | read | grade |
|---|---|---|---|
| **Mediterranean hill town** (hillside, slope, day 400) | `town_mediterranean_hill*.webp` | Red tile, white / cream / rose render, stair streets: reads Italian at once. But every wall is mottled (pink, white and grey blocks in patches), and five-storey blocks stand in rows; awnings blue and light-blue stripes. | B |
| **Karst** (cliff, day 400) | `town_karst*.webp` | Green-glazed hips with lifted eaves, yellow bamboo, white scholar courtyards under grey tile, cherry trees: the right ingredients. Spoiled by six-storey dark timber blocks with grid windows (offices), large red-brick blocks with teal roofs, and a town that sits at the foot of its cliff and never climbs it. | C+ |
| **Desert** (walled, oasis, day 400) | `town_desert*.webp` | Rich and varied, the mosque with its teal dome and minaret is fine. Against it: stepped white domes on half the roofs (whipped cream), saturated checkered awnings (yellow, red, blue, orange) everywhere, red and orange mottling on the walls, merlons on every house, and a terracotta Big Ben. | B- |
| **Northern** (crossroads, day 400) | `town_northern*.webp` | Silvered logs, Falu-red farms, turf and shingle roofs: a convincing timber town at 100 blocks. Close up the log-end polka dots and white trim blocks are everywhere; five- and six-storey timber blocks with birch-bark corners; red-white-blue striped awnings. | B- |
| **Alpine mining town** (mountain, steep, day 400) | `town_alpine_mining*.webp` | White stone ground storeys under dark boarded upper storeys and grey slab roofs, onion churches: right. Six-storey wooden tower houses and boxy cubic copper onions hurt it. | B- |
| **Bayou stilt town** (stilt, lake, day 400) | `town_bayou*.webp` | Board-and-batten, tin and shingle on the water: good. Inland it turns into four- to six-storey brick and timber blocks (a bayou city of towers); green and yellow striped awnings. | B- |
| **Deep town** (alpine, wilds realm) | `deep_town*.webp` | Dwellings cut into the realm wall and the square read well; the hall is the same green-copper basilica in every culture, the lift a plain blackstone shaft. Fantasy is allowed here; monotony is the issue. By its own light the town is mostly dark between pools of light. | B- |
| **Maritime, tundra, steppe, mesa, tropical, wisteria, moorland** | `towns_other_cultures.webp`, `town_maritime_close.webp`, `town_tundra_close.webp` | The same pattern everywhere: good low buildings, then a high-rise core (grey six-storey blocks on the maritime coast, thatched five-storey towers in the tropics, orange towers on the steppe); mesa, like karst, ignores its cliff. Moorland grew only a 36-folk village in 300 days (a planner matter). | C+ to B- |

**Similarity.** Neighbours rarely look copied: seed, dressing and accretion vary them well. The copies are across
cultures, not along streets: the castle, palace, gatehouse, smelter, brick kiln, glassworks, tilt yard, deep hall and
deep dwelling are one plan with materials swapped (see §3).

---

## 3. Families

Grades: **A** ready · **B** good, small fixes · **C** needs rework. Per-culture notes name the archetype, culture and
seed (s0 / s1 on the sheets).

### Homes by station (`station.md`) — **B-**
`img/sheets/house_{hovel,shack,croft,merchant_house,reeve_house,rectory,scribe_house,hermit_hut}_flat.webp`

- **merchant_house — C.** 7–11 wide and 20–28 high, VERTICAL, so the planner always asks for the full 20–28
  (`plan/growth/Build.height`): five or six storeys in every culture, even in a village. A Bohemian burgher's house is
  three storeys under a steep roof with dormers. Blank side walls on a detached plot read as a box.
- **scribe_house — C.** 6–8 wide and 18–24 high: a book-lined tower. karst s0 is a white box under a black cap.
- **shack — C+.** 5–7 wide, two or three storeys: taller than wide, a little tower, the same in every culture.
- **reeve_house — B-.** temperate s0 wears the log-cottage dress: the facade is a brown field of protruding log ends
  and stripped posts with no white render (see `img/reeve_temperate_front.webp`). An official's house should be the
  town manner.
- **rectory — B.** northern s0 (`img/rectory_northern_front.webp`): log ends on every other course plus a white slab
  sill and head under every window, so the facade is a polka-dot pattern.
- **croft — B+.** Long croft with garden reads; temperate's roof drops to one block of wall. Mediterranean and bayou
  get golden hay thatch that is out of character.
- **hovel — B.** Good mended-poor story; the northern half of the cultures share one lean-to silhouette with only a
  colour swap.
- **hermit_hut — B-.** desert/mesa are hollow boxes; bayou's roof looks lifted off its walls.

### Town homes (`townhome.md`) — **C+**
`img/sheets/house_{rowhouse,townhouse,tenement,shophouse,tower_house,cliff_dwelling,courtyard_house,manor}_flat.webp`,
`img/pair_civic_chapel_hills__house_rowhouse_hills.webp`

- **tenement — C.** Seven or eight storeys with a flat roof and a little roof hut in temperate, northern, alpine,
  tundra and moorland s0: a 20th-century apartment block. In pitched-roof cultures a tenement must carry a steep roof
  (dormer rows) and stop at four or five storeys.
- **townhouse — B-.** Proportions are right at three storeys; at the planner's VERTICAL maximum it reaches five. The
  grey stone-brick string courses on white render are heavy (temperate); northern/tundra log town houses are towers.
- **rowhouse — B.** The right size. Walls mottled in most cultures (northern, tundra: log ends again).
- **shophouse — B-.** Contrasting patches: temperate s0 brick with white blotches, mediterranean s0 grey with white,
  alpine, moorland and maritime alike. Reads damaged.
- **tower_house — B-** (alpine, moorland, mediterranean, karst only). Mediterranean (San Gimignano) is right. The
  alpine one is a six-storey wooden tower; a Tyrolean tower house is stone with a hipped cap. Moorland's crenel stubs
  read ruined.
- **manor — B+.** Wings, gardens, forecourt: good. temperate s0 is English black-and-white under deepslate: the
  Bohemian manor is white render under red tile (s1 gets it right). Steppe and mesa manors are crenellated boxes.
- **courtyard_house — A-** in its cultures (desert, mediterranean, karst).
- **cliff_dwelling — B** (judged only on cliffs in town renders).

### Homesteads and farms (`homestead.md`) — **B+**
`img/sheets/house_{cottage,hut,farmhouse,longhouse,round_house,stilt_house}_flat.webp`, `img/sheets/work_barn_flat.webp`,
`img/pair_farm_orchard_flat__farm_pasture_flat.webp`, `img/pair_house_round_house_flat__house_stilt_house_flat.webp`

- **cottage — B.** Temperate is right in form; the brown roof is heavy over too little white wall. northern/tundra:
  crossed gable boards ("horns") three blocks long read as antlers, white blocks scattered on log walls; tundra's
  snow-patched roof reads as a ruin. **desert cottage s0 is a crenellated tower with holes: a fort, not a house.**
- **hut — C+.** desert/mesa are plain cubes with a hollow top; mediterranean s1 a pink box; steppe saturated orange.
- **farmhouse — A-.** Desert is a fort again; steppe tiny.
- **barn — B.** Temperate draws a white plastered gambrel with timber; the bible's Bohemian **plank barn** (vertical
  spruce boards on fieldstone) does not show on the barn sheet.
- **longhouse — B.** Good mass; the dragon-head horns are oversized.
- **round_house — A-**, **stilt_house — B**, **fields, orchards, pastures — A-** (orchards use one tree in all cultures).

### Trade (`trade.md`) — **B+**
`img/sheets/work_*`, `img/sheets/storage_*`, `img/pair_work_carpentry_flat__work_masonry_flat.webp` and neighbours

- **A-**: carpentry, masonry, sawmill, quarry (work), builders' yard, storehouse, caravan yard (open-sided workshops that
  read as their trades at once).
- **smithy — B+**: desert is a white box with a blue wool awning, mesa a crenellated tower.
- **bakery, tailor — B**: rainbow awnings (purple, blue, cyan) on the tailor; northern/tundra polka dots; desert/mesa
  crenellated.
- **warehouse — B-**: temperate s0 stepped gable is right but the brick quoins zig-zag in a checkerboard; s1 a plain
  brick box; northern/tundra huge blank walls salted with white blocks.
- **fishery — B**: mediterranean is a three-storey tower for a fish shed.
- **hunters_lodge — B-**: the tower's quoins alternate light and dark in every culture (the bible's own §8 rule
  broken); moorland white birch walls with a purple awning, maritime cyan.
- **brewery — B**, **granary — B** (desert/mesa silos are lumpy white blobs), **windmill — B** (white-block sails),
  **watermill — B**.

### Industry (`industry.md`) — **B**
`img/pair_work_logging_camp_flat__work_charcoal_kiln_flat.webp`, `..._smelter_..._brick_kiln...`, `..._glassworks_..._salt_works...`, `..._shipyard_..._caravan_yard...`

Logging camp B+ (moorland stacks white birch bark), charcoal kiln B, smelter B (one brown tower with an outside stair
and a grey chimney, identical in seven cultures), brick kiln B (the same salmon bottle kiln in every culture),
glassworks B (one shell), salt works B- (blue and white squares read as a tiled floor), shipyard B- (judged on dry
ground only), caravan yard A-.

### Craft (`craft.md`) — **B-**
`img/pair_work_tannery_flat__work_butcher_flat.webp`, `..._chandler_..._dye_works...`, `..._paper_mill_..._apiary...`, `..._counting_house_..._courthouse...`

Apiary A-. Tannery B (three storeys on 9×11; white hides on the frames read as white carpet squares). Butcher B
(candy awning; tropical and bayou four-storey). **Chandler B-** (three or four storeys on 7×9; bright orange honeycomb
out front). **Dye works B-**: racks of saturated wool (red, blue, magenta, green, lime) on every culture: the brightest
object in any town; keep it to madder, woad, weld and undyed. **Paper mill B-**: northern/tundra polka dots at their
worst. **Counting house B-**: temperate s0 a pink brick wedge roof; steppe and mediterranean mottled.

### Civic and commerce (`civic.md`) — **B-**
`img/sheets/civic_{chapel,town_hall,library,bathhouse,school,guardhouse}_flat.webp`, `img/sheets/commerce_{inn,bank,market_hall,market_stall}_flat.webp`

- **chapel — B.** temperate s1 (white, dark spire) is the Bohemian church; s0 grey stone with a black needle is fine.
  The *set* render shows the temperate chapel in Norman stone with a red tile spire (`img/temperate_set.webp`).
  desert/mesa chapels are roofless boxes (the mission reads as a ruin); mediterranean (hills) carries a saturated blue
  dome. alpine onion reads.
- **town_hall — C+.** temperate s0 and s1 are Hanseatic brick with a crenellated tower and corner spirelets: not a
  Bohemian *radnice* (white render, arcade, a tower with a gallery and a helm or onion). alpine and moorland crowns are
  copper cube clusters that read as a tree. desert/mesa: coloured wool carpets on the roof.
- **market_hall — B.** The open arcade is right; temperate s0 quoin checkerboard; alpine copper cube onion; desert/mesa
  white wool domes.
- **inn — B.** Good; cold cultures mottled; desert/mesa roof terraces carpeted in red, blue and yellow.
- **library — B-**, **bathhouse — B**, **school — B**, **bank — B-** (temperate s0 brick with grey patches; bayou s1 an
  open-topped box).
- **guardhouse — B-.** moorland, maritime, desert, mesa and mediterranean are open crenellated boxes that read
  roofless; tundra s0 crown reads as antlers.
- **market_stall — C.** A tall closed booth under a candy-striped awning in purple, lime, cyan or light blue
  (`img/pair_work_barn_flat__commerce_market_stall_flat.webp`); ten of them on a square make a fairground. A market
  stall is a low trestle under a linen or plank canopy.

### Landmarks (`landmark.md`) — **B**
`img/pair_landmark_cathedral_flat__landmark_bell_tower_flat.webp`, `..._clock_tower_..._great_hall...`, `..._pagoda_..._temple...`, `..._lighthouse_..._stables...`

Cathedral A- (grey gothic with a dark spire is right for a Bohemian cathedral; northern stave A). Pagoda A-. Temple B
(steppe stupa reads as a blob). **Bell tower B-**: temperate is grey stone with a teal copper needle and pinnacles; a
Bohemian bell tower is white render with a dark shingle helm or onion. **Clock tower C+**: a Big Ben in every culture
including desert, karst, steppe and mesa, with 3×3 gold clock faces. Great hall B (oversized horns). Lighthouse B
(bands in purple, cyan or light blue; real day-marks are red/white or black/white; it lights its lamp with sea
lanterns, acceptable as a lighthouse lamp but worth a glance under the natural-surface rule).

### Clergy and learning (`learning.md`) — **B-**
`img/pair_civic_monastery_flat__civic_convent_flat.webp`, `img/pair_civic_university_flat__civic_observatory_flat.webp`, `img/pair_work_alchemist_tower_flat__civic_shrine_flat.webp`

Monastery and convent B (good mass; one quad plan; white balcony slabs repeat like a motel). University B.
**Observatory C**: oxidised-copper cube heaps on crenellated towers read as moss or trees, the same in nine cultures;
an observatory dome is a smooth hemisphere with a slit. **Alchemist tower C+**: top-heavy, the upper band salted with
white blocks. **Shrine B-**: temperate is a black cube on legs under a big double cross; a Bohemian wayside shrine
(*boží muka*) is a whitewashed pillar with a little gabled niche.

### Nobility (`noble.md`) — **B-**
`img/sheets/noble_{keep,castle,palace,hunting_lodge,stables}_flat.webp`, `img/town_temperate_city_castle.webp`

**Castle B-**: temperate s1 (white render, red cones) is Rattay; s0 dark grey with ten black witch-hat cones is a
fairy-tale castle. One plan in every culture (alpine, moorland and tundra near identical; karst gets a European plan
with black hips). Keep B (quoin checkerboards; karst fine). Palace B- in its cultures (desert crenels everywhere, dome
stepped). Hunting lodge C+ (checkerboard quoins on the tower in every culture). Stables B+ (tundra's moss roof is a
green box).

### Law (`lawhouse.md`) — **B**
`img/pair_law_jail_flat__law_gallows_flat.webp`, `..._pillory_..._watch_house...`, `..._toll_gate_...`, `..._counting_house_..._courthouse...`

Courthouse B (alpine copper cube onion, desert/mesa white wool domes). Jail B- (a cube; moorland and mediterranean
crenellated open tops). Gallows, pillory A-. Watch house B+. **Toll gate B-**: striped booms in red/white are right,
cyan, purple, lime and light blue are not.

### Social life (`sociallife.md`) — **B-**
`img/pair_civic_infirmary_flat__civic_almshouse_flat.webp`, `..._orphanage_..._hostel...`, `..._school_..._playhouse...`, `..._tavern_..._thieves_den...`, `..._tilt_yard_..._cemetery...`, `..._festival_ground_..._caravanserai...`

Tavern B+, playhouse B, cemetery B (tropical/bayou quartz graves too bright). Almshouse, orphanage, hostel, infirmary
B- (one shell, as the family page admits; desert/mesa crenellated boxes). **Tilt yard C+**: identical in every culture,
candy-striped wool drums for pavilions. **Festival ground C+**: rainbow wool awnings. Caravanserai B- (a plain brown
ring with a small portal). Thieves' den B (a cottage, as it should be).

### Landscape and defence (`landscape.md`) — **B**
`img/pair_defense_gatehouse_flat__defense_wall_flat.webp`, `img/pair_law_toll_gate_flat__defense_watchtower_flat.webp`, `img/pair_landscape_*`

Plaza, park, garden, well, bell, lamp post B+ to A-. Watchtower B+. Gatehouse B (mottled stone; tropical and steppe
camouflage). Town wall B- (merlons stepped irregularly so a sound wall reads ruined). Palisade B (wisteria and moorland
use birch logs with their white bark; karst purple). **Fountain B-**: the spout is a saturated blue block; desert and
mesa basins are tiled in bright cyan. Statue B (desert/mesa obelisks banded in cyan).

### Extraction (`extraction.md`) — **A-**
`img/pair_extract_mine_flat__extract_quarry_pit_flat.webp`. Quarry pit, clay and sand pits read as workings with
cranes and spoil. The mine on the surface is only its adit (fine).

### Deep (`deep.md`) — **B-**
`img/pair_deep_hall_flat__deep_dwelling_flat.webp`, `img/deep_town*.webp`. Underground fantasy is allowed; the
problem is sameness: the hall is the same green-copper basilica with teal turrets in all 14 cultures; the dwelling is
one shell with the infill swapped. The lift is a plain shaft.

### Age: decay, dressings, accretion (`age.md`) — **A-**
`img/decay_stages.webp`, `img/town_temperate_decline.webp`. See §2.

### Interiors and night — **B**
`img/night_interiors.webp` (cottage, merchant house, library, inn; temperate), `img/night_inn_temperate.webp`. Every
interior checked meets the light rule and is furnished. Weaknesses: the inn's ground floor is half bare cobble; the
library has unlit corners between the shelves; cottages are cramped by thick walls and stair. From outside at night the
windows read only faintly and the streets are dark.

---

## 4. Cultures

Grades across the catalogue as each culture builds it (only archetypes the culture can build). *Town* is the town
render of §2.

| culture | homes | work | civic / social / law | landmarks / noble | town | the main problems |
|---|---|---|---|---|---|---|
| **temperate** | B- | B+ | C+ | B- | C+ | tall town homes; English and Hanseatic manners on the prominent buildings; heavy brown roofs over thin white walls; teal copper spires; quoins |
| wisteria | B | B+ | B | B | B | pink terracotta roofs everywhere (monotone); birch-bark palisades |
| northern | B- | B- | B | A- (stave) | B- | log-end and white-trim polka dots; horns; timber towers |
| alpine | B | B | C+ | B- | B- | copper cube onions; wooden tower houses |
| tundra | C+ | B- | B- | B- | C+ | snow-patch roofs read ruined; moss-block roofs; polka dots |
| moorland | B | B | B- | B- | (village only) | crenellated open tops; purple accents; birch walls |
| maritime | B | B | B | B | C+ | grey six-storey blocks on the harbour; cyan/blue awnings |
| tropical | B | B | B- | B | C+ | thatched high-rises; cyan accents |
| desert | C+ | B- | B- | B+ (mosque, palace) | B- | merlons on every house; whipped-cream domes; carpets on roofs; carnival awnings |
| mediterranean | B | B | B | B+ | B | mottled render; five-storey rows; saturated blue dome on the chapel |
| steppe | C+ | B- | C+ | C+ | C | one orange terracotta colour on everything; towers; crenellated manor |
| karst | B- | B | B | A- (pagoda) | C+ | timber office blocks; cliff unused; European castle |
| bayou | B | B | B- | B- | B- | inland brick towers; open-topped boxes |
| mesa | C+ | B- | C+ | B | B- | crenellated boxes; roofless missions; cliff unused |

**Natural-surface rule.** No glowing or fantasy blocks were found above ground (the light `Manner` keeps glowstone and
shroomlight hidden; the deep styles alone use amber and copper). What breaks *natural* on the surface is colour, not
material: light-blue, cyan, lime, magenta and purple dyes (awnings, booms, pavilions, dye racks, fountain spouts,
statue bands) and the turquoise of oxidised copper used in quantity on temperate spires. Those are the items to fix
under this rule.

---

## 5. The top 30, grouped for architects

Ranked by how much each would improve Hearth in a real world. Each lane owns its files, so lanes can run in
parallel without conflicts; inside a lane, do the items in rank order.

| lane | owns | items |
|---|---|---|
| **L1 Heights** | `plan/growth/Build.java`, the `ArchetypeInfo` of `MerchantHouse`, `ScribeHouse`, `Tenement`, `Townhouse`, `Shack`, `TowerHouse`, `Chandler`, `Tannery` (height ranges only) | 1, 7, 22 |
| **L2 Walls kit** | `build/kit/Walls.java`, `build/kit/refine/LogWalls.java`, `build/kit/homestead/Carving.java` | 3, 12, 18 |
| **L3 Palettes** | `resources/data/hearth/hearth_style/*.json`, `build/style/**` (incl. `LocalMaterials`, `Palette`) | 2, 4, 6, 15, 19, 26 |
| **L4 Crowns** | `build/kit/civic/Towers.java`, `build/kit/landmark/Crowns.java`, `build/kit/landmark/Shapes.java`, `build/kit/civic/Adobe.java` (dome), `archetype/civic/Observatory.java`, `archetype/landmark/ClockTower.java` | 9, 10, 16, 17 |
| **L5 Bohemian civic** | `archetype/civic/TownHall.java`, `archetype/civic/Chapel*.java`, `archetype/civic/Shrine.java`, `archetype/landmark/BellTower.java`, `build/kit/civic/Bohemian.java` | 11, 13, 20 |
| **L6 Markets and fairs** | `archetype/commerce/MarketStall.java`, `build/kit/sociallife/Fair.java`, `archetype/landscape/FestivalGround.java`, `archetype/civic/TiltYard.java`, `archetype/work/DyeWorks.java`, `archetype/landmark/Lighthouse.java`, `archetype/law/TollGate.java`, `archetype/landscape/Fountain.java` | 5, 27 |
| **L7 Dry lands** | `archetype/house/HouseBuilder.java` (`dryDress`), `build/kit/homestead/Adobe.java`, `archetype/house/{Hut,Hovel,HermitHut}.java`, the roof-terrace dressing (`kit/Furniture`/`kit/townhome/Life` rugs) | 8, 14, 21 |
| **L8 Noble** | `archetype/noble/*`, `build/kit/noble/*` | 24 |
| **L9 Social and learning shells** | `archetype/civic/{Almshouse,Orphanage,Infirmary}.java`, `archetype/commerce/Hostel.java`, `archetype/work/AlchemistTower.java`, `build/kit/sociallife/Shell.java` | 25 |
| **L10 Deep** | `archetype/deep/*`, `build/kit/deep/*` | 28 |
| **L11 Planner sites** | `plan/layout/*` (cliff layouts), `plan/growth/Parcels.java` | 29 |
| **L12 Interiors and tooling** | `build/kit/Rooms.java`, `build/kit/Furniture.java`, `src/gametest/.../plan` | 23, 30 |

1. **[L1] Cap town-home heights by culture and tier.** `Build.height` returns `maxHeight` for every VERTICAL
   archetype in every zone, so the merchant house (20–28), town house (14–24) and tenement (18–28) always build at the
   top of their range: five to eight storeys in villages and towns of every culture (`img/town_temperate_market_town.webp`,
   `img/towns_other_cultures.webp`). Give VERTICAL homes the zone taper the others get, cap storeys by settlement tier
   (village 2 + roof, town 3 + roof, city 4 + roof) and by culture (steppe, tropical, bayou, northern, tundra never above
   3), and lower the info ranges (merchant house 13–20, scribe house 12–18, tenement 16–22).
2. **[L3] Stop the camouflage walls.** Render, stone and brick palettes mix strongly contrasting blocks in small
   patches (mediterranean pink/white/grey, alpine and maritime quartz/grey, steppe sandstone/orange, temperate brick
   with white): whole streets read damaged (`img/town_mediterranean_hill_close.webp`, `img/sheets/house_shophouse_flat.webp`).
   Keep the second wall material under ~10 %, tonal (calcite in quartz, not grey in white), in patches of 4+ blocks,
   and put weathering at the foot and under the eaves only.
3. **[L2] Log ends and trim without polka dots.** Log walls push a log end out on every other course at every corner
   *and* every opening post, and `Carving` adds a white full-block sill and head at every window, so northern, tundra,
   alpine and temperate log buildings are dotted all over (`img/rectory_northern_front.webp`, `img/reeve_temperate_front.webp`,
   `img/pair_work_paper_mill_flat__work_apiary_flat.webp`). Log ends only at the four corners (overlapping one block,
   both courses), flush stripped posts at openings, window trim as trapdoors or slabs in the frame's colour, white
   paint only on the better station's houses and never as full blocks.
4. **[L3] Natural dyes only.** The `cloth` role and per-building dyes use light blue, cyan, lime, magenta and purple.
   Restrict surface cloth to madder red, woad blue (`blue`, not `light_blue`), weld yellow, undyed white, light grey and
   brown; one dyed colour per building; stripes only in mediterranean and desert markets.
5. **[L6] Rebuild the market stall** as a low open trestle (2 high) under a canopy of undyed linen or boards on poles,
   with baskets, crates and sacks, and no closed booth. Ten on a square must read as a market, not a fairground
   (`img/town_desert_close.webp`, `img/pair_work_barn_flat__commerce_market_stall_flat.webp`).
6. **[L3] Make Bohemia win in temperate's prominent buildings.** The set render dresses the temperate chapel in Norman
   stone with a red tile spire, the manor in black-and-white Tudor, the town hall in burgher brick
   (`img/temperate_set.webp`, `img/sheets/civic_town_hall_flat.webp`, `img/sheets/house_manor_flat.webp`). Raise the
   Bohemian church, town and castle weights so they take at least 85 % of religious, civic and noble buildings; keep
   burgher brick to warehouses and the odd merchant; English cottage and manorial to a rare variant.
7. **[L1] Pitched roofs on tall homes.** Tenements (and any home over three storeys) in pitched-roof cultures build
   flat roofs with a roof hut. Give them a steep roof with one or two dormer rows (`img/sheets/house_tenement_flat.webp`).
8. **[L7] No merlons on houses.** Desert and mesa cottages, huts, farmhouses, smithies, bakeries and inns get stepped
   merlons from `dryDress`, so a desert town is a town of little forts (`img/sheets/house_cottage_flat.webp`). Homes and
   workshops: plain parapets with a coping, rounded shoulders, a roof hatch and a wind tower; merlons only for the fort,
   noble and law categories.
9. **[L4] Onions and cupolas with a profile.** Onions are stacked full blocks of oxidised copper (`Towers.onionRadii`,
   `Crowns.onion`) and read as a tree or a heap at 30 blocks (alpine town hall, market hall, courthouse, observatory).
   Build them from stairs and slabs with a neck, a waist and a lantern; on small towers prefer a helm.
10. **[L4] Domes that are not whipped cream.** `Adobe.dome` / `Shapes.domeRows` lay white domes outline by outline in
    full blocks (desert, mesa: market hall, library, bathhouse, courthouse, granary), which read as cream or snow. Use
    stair-smoothed profiles on a proper drum, at most one dome per building, and a plaster tone, not pure white.
11. **[L5] A Bohemian town hall.** White render, an arcaded ground floor on the square, a tower with a gallery and a
    dark helm or onion, red tile; no crenellations or corner spirelets.
12. **[L2] Tonal quoins.** Strongly contrasting quoins alternate course by course into checkerboards on the hunting
    lodge (every culture), warehouse, keep, market hall and small stone houses. The bible's own rule (README §8): tonal
    accents, and no quoins below 9 wide or tier 2.
13. **[L5] Temperate spires in dark shingle, not teal copper.** The bell tower, cathedral and market spires in temperate
    towns are teal oxidised copper needles with pinnacles; at town scale they turn the skyline turquoise. Bohemian
    spires and helms are dark shingle or slate; copper only on the odd baroque onion.
14. **[L7] Rooftop carpets.** Desert and mesa roof terraces are laid with red, blue, yellow and cyan carpets in a
    checker (inn, town hall, dye works, tannery). At most one muted rug and some jars.
15. **[L3] Whiter temperate villages.** Village cottages show one block of white wall under a huge brown roof; at 30
    blocks the village is brown. Raise eaves to show a full storey of render, keep dark boarding to the gable, and use
    the bible's lime render (calcite, mushroom stem) as the dominant wall.
16. **[L4] The clock tower in each culture's manner.** One Big Ben everywhere with 3×3 gold faces. Bohemian: the
    town hall tower; Italian: campanile; desert and steppe: no clock tower (use a minaret or gate tower); karst: a drum
    tower. Clock faces 1×1 or an item frame.
17. **[L4] Observatory domes.** A smooth lead or copper hemisphere with a slit on a round drum, not a heap of copper
    cubes on crenels.
18. **[L2] Horns at scale.** `Carving.horns` runs the barge boards two or three blocks past the ridge, reading as
    antlers on cottages, longhouses, great halls and tundra crowns. One block, and only on halls and stave buildings.
19. **[L3] No birch bark on the surface's posts and palisades.** Local birch turns up as bark-on logs (white with black
    flecks) in posts, columns and palisades (temperate market, maritime, wisteria and moorland palisades,
    `img/town_temperate_market_town_close.webp`). Strip it like the log walls, or keep the culture's dark posts.
20. **[L5] A Bohemian wayside shrine.** A whitewashed or stone pillar with a little gabled niche and a cross, not a
    black cube on legs.
21. **[L7] Small dry homes.** The desert and mesa hut, hovel and hermit hut are hollow cubes. Give them a lean-to of
    poles and reeds, a courtyard wall, a doorway shade.
22. **[L1] Narrow towers on small plots.** Shack (5–7 wide, three storeys), chandler (7×9, four storeys), tannery and
    butcher (tropical, bayou) stand taller than they are wide. Height ≤ 1.6 × frontage for detached buildings.
23. **[L12] Interiors that fill.** Inn ground floors are half bare cobble; the library has unlit corners between its
    shelves; cottages lose half their floor to walls and stair. Fill halls with tables and benches to the walls, light
    the library's bays, and thin cottage partitions (`img/night_interiors.webp`).
24. **[L8] Castles per culture and per Bohemia.** One castle plan in every culture; temperate s0 is dark grey with ten
    black witch-hat cones. Bohemian castles: fewer, broader towers in white render under red tile hips or low cones;
    karst a walled compound with gate towers and tiered halls; northern a timber-and-stone borg.
25. **[L9] Tell the charitable houses apart.** Almshouse, orphanage, hostel and infirmary share one shell; give each a
    sign of its own (a bell-cote, an arcade, an oriel, a herb garden) as the family page already asks.
26. **[L3] Steppe and tundra palettes.** The steppe builds everything in one orange terracotta; tundra roofs are moss
    and snow patches that read ruined. Steppe: felt, undyed wool, mud brick and timber; tundra: turf with stone and a
    dark board ridge.
27. **[L6] Fair, tilt yard, dye works, toll gate, lighthouse, fountain colours.** Pavilions in undyed canvas with one
    colour of the house that holds the tilt; dye racks in madder, woad and weld; toll booms red/white or black/white;
    lighthouse bands red/white or black/white; fountains in stone, no blue spout block.
28. **[L10] A deep hall per culture.** The green-copper basilica is in all 14 cultures; karst a raised timber hall on a
    terrace, northern a stave hall, desert a domed court (the family page already names this). Vary the dwellings'
    silhouettes, not only their infill.
29. **[L11] Towns that use their cliffs.** Karst and mesa cliff layouts put the town at the foot of the cliff and leave
    the face bare. Stack cliff dwellings and stair streets up the face, as the layout's name promises.
30. **[L12] A textured town preview in the build.** `settlementPreview` draws map colours; this review needed a
    textured whole-town renderer (MiniSim + the build module + the textured `IsoRenderer`, overview and close-ups by day
    and night). Add it as a gametest preview task so every architect can see their building in a town, and add the
    similarity check across cultures (one plan reused in many cultures shows up as copies in §3).

---

## 6. Contact sheets

All 134 archetypes, flat terrain, 14 cultures × 2 seeds, re-tiled seven cultures to a row (temperate, wisteria,
northern, alpine, tundra, moorland, maritime / tropical, desert, mediterranean, steppe, karst, bayou, mesa; seed 0 then
seed 1): `img/sheets/<category>_<name>_flat.webp`. Seed-0 pairs used in §3: `img/pair_*.webp` (two archetypes per
image, `hills` where named). Regenerate with
`./gradlew blueprintPreview -Parchetype=all -Pstyle=all -Pseeds=2 -Pterrain=flat` (about 3,750 blueprints a terrain, about 5 minutes).
