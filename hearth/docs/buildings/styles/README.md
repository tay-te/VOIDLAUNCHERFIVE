# VOID Hearth — the style bible

How Hearth's buildings are dressed: what each culture builds with, which of its manners a building wears, and why. The
art director and the architects work from these pages; the renders under `img/` are made with the preview tools
(below) and are the reference for what each dressing should look like. LIVING_WORLD.md §8.2 is the brief; BUILDING_GUIDE.md
§5 describes the JSON. One page per culture, one per regional style.

**Two rules over everything** (the user's, October 2026):

- **The surface is natural.** Every surface style looks like a real-world building tradition in real materials: no
  pastel, coloured or glowing fantasy content (no lumenwood, wisteria wood, prismite, glowcaps, sea lanterns, opal
  sandstone) above ground. Fantasy belongs underground only, in the `deep` region. `StyleModelTest.theSurfaceIsNatural`
  holds every culture and surface region to it.
- **Colour is natural too.** Cloth (awnings, beds, banners, carpets) is dyed in what a medieval dyer had: madder red,
  woad blue (`blue`, never `light_blue`), weld yellow, undyed white, light grey and brown; never light blue, cyan,
  lime, magenta or purple (`StyleModelTest.surfaceClothIsNaturallyDyed`). A house wears one cloth: `cloth` is chosen
  once per building like the roof, so a house's awning, beds and banners are one dye or undyed. Only the
  Mediterranean and desert stripe their awnings with white (`"stripes": true` in their habits; `Habits.stripes`).
- **Temperate is Bohemia.** Expanse's visual target is *Kingdom Come: Deliverance II*'s Bohemia, so the temperate
  culture's main look is the Bohemian countryside: lime-plastered cottages with boarded gables on fieldstone, log
  cottages, shingle and thatch roofs, plank barns, white stone churches with tall towers, market towns of rendered
  houses under red tile, castles and manors of rendered stone. See [temperate.md](temperate.md).

| culture | manners (sub-styles) | page |
|---|---|---|
| temperate | **Bohemian village · Bohemian town · Bohemian church · Bohemian castle** (the main look) · English cottage · burgher brick · Norman stone · manorial | [temperate.md](temperate.md) |
| wisteria (Woodland Glades) | garden house · scholar's pavilion · canal merchant house | [wisteria.md](wisteria.md) |
| northern | log house · stave and carved timber · turf house · redwood hall | [northern.md](northern.md) |
| alpine | chalet · patrician stone house · baroque church · hayloft barn | [alpine.md](alpine.md) |
| tundra | longhouse · sod longhouse · fire hall | [tundra.md](tundra.md) |
| moorland | rubble cottage · harled laird's house · blackhouse · kirk | [moorland.md](moorland.md) |
| maritime | harbour house · net loft · granite merchant house | [maritime.md](maritime.md) |
| tropical | stilt house · coral-stone colonial · jungle shrine | [tropical.md](tropical.md) |
| desert | town house · earthen adobe · dressed sandstone · palace | [desert.md](desert.md) |
| mediterranean | hill-town house · masseria farmhouse · palazzo · villa | [mediterranean.md](mediterranean.md) |
| steppe | round house · sahel earthen hall · herder's hut | [steppe.md](steppe.md) |
| karst | bamboo house · scholar's courtyard · temple | [karst.md](karst.md) |
| bayou | stilt house · creole gallery house · fishing camp | [bayou.md](bayou.md) |
| mesa | pueblo · cliff masonry · mission | [mesa.md](mesa.md) |
| regions | old forest · glade · cloud forest · volcanic · fjord · tepui · deep | [regions.md](regions.md) |

---

## 1. How a building gets its dress

A plot names a culture's style (`hearth:temperate`). What it actually wears is decided in `Draft`'s constructor, in this
order, all deterministic from the spec and the ground:

1. **Who and where** (`StyleContext`): the archetype (its category and *purposes*: `home`, `workshop`, `religious`,
   `noble`, `fort`, `public`, `trade`, `farmstead`, `grand`, `underground`), the plot tier, the household's **station**
   (the `station` param; or the one the archetype or the `district` param implies: a manor is the gentry's, a chapel
   the clergy's, a house in the market district a merchant's), the **biome** at the plot's centre (the probe; the
   `biome` param stands in for it), the **landforms** read from the ground (`flat`, `slope`, `steep`, `water`, `coast`,
   `river`, `lowland`, `highland`, `mountain`, `cold`, `underground`, plus any the `landform` param adds) and the
   `region` param.
2. **The dressing**: one of the culture's dressings is drawn by weight among those whose `when` holds. Dressings are the
   base (the culture's common manner), its **fashions** (variants of the common manner, chosen by seed), and its
   **sub-styles** (whole other manners: burgher brick, the palazzo, the scholar's courtyard), each with its own
   fashions. Forced with `variant`; defence works wear the town's dressing (see 6).
3. **The region**: a regional style (`hearth_style/region/*.json`) is worn over the dressing where its `when` holds (a
   biome, a landform, the `region` param; `region=none` refuses it). It names only what it changes; the culture's
   geometry and everything it leaves out stand.
4. **The culture's overrides** (`overrides` in its JSON): roles it dresses one way for a purpose or a place in every
   dressing and under every region (the steppe's palisades are debarked stakes, the moorland's and the glades' dark
   stakes rather than birch).
5. **Local materials** (the `local` param, §7.2) are blended in, then the **station's finish** applies (an explicit
   `station` only).

`d.style()` is the dressed view: every component that reads the style directly (gardens, roads, fortifications) sees
the same dress as `d.mat(...)`. `d.style().root()` is the culture's style undressed. `d.dressing()` names the dress
("burgher brick · limewashed / lumen") for labels and logs.

## 2. Conditions (`when`)

```json
"when": {"stations": ["merchant"], "tiers": {"min": 2}}                       all keys must hold
"when": [{"stations": ["gentry", "lord"]}, {"archetypes": ["house/manor"]}]   one of the objects must hold
"when": {"categories": ["home"], "not": {"stations": ["poor"]}}                a nested condition that must fail
```

Keys: `stations`, `categories` (categories and purposes), `archetypes` (ids or paths, `*` wildcards), `tiers` (a list,
a number or `{"min", "max"}`), `biomes` (ids with wildcards), `landforms`, `districts`, `regions`, `cultures`, `not`.
Expanse's renamed surface biomes match by either id (`When.RENAMED_BIOMES`): `wisteria_vale` = `woodland_glades`,
`lumen_grove` = `ancient_oakwood`, `opal_dunes` = `red_dunes`, `prismatic_peaks` = `dolomite_peaks`, `frostbloom_tundra` =
`lichen_tundra`, so a style naming either works in worlds of either generation.
`base_when` is the condition of the common manner; a culture's fashions share it. A dressing with no condition may be
chosen anywhere, so JSON written before conditions existed draws exactly as it always did (tested).

**Weights do the work.** A matching sub-style competes with the common manner by weight: temperate's Bohemian village
(`w` 20) dresses about seven cottages in eight against the English cottage manner's 2.9; Bohemian town (`w` 30) takes
about 85 % of merchants' houses with the odd burgher brick (5) among them, as real towns mix. To exclude a manner from
a purpose entirely, give the base a `base_when` (temperate's cottage manner never dresses a lord's or a merchant's
house, a chapel, a civic building or a town wall).

**A sub-style keeps its weight.** A sub-style's fashions may carry their own `when` (the Bohemian village's plank barn is
for farmsteads only): where a fashion's condition fails, its share goes to the sub-style's other dressings, so the
sub-style competes with its whole weight wherever any of its dressings holds (`Style.weights`). Fashions of the common
manner keep their own weights, as before.

## 3. Stations: who lives there

`StationFinish` — applied only when the planner gives `station`, because the planner's tier already carries a
district's wealth:

| station | finish | weathering | other |
|---|---|---|---|
| poor | −2 tiers | ×1.9 | roof mended in patches (`Dressing.patchFor`), all local material |
| peasant | −1 | ×1.35 | all local material |
| artisan | 0 | ×1 | |
| merchant | +1 | ×0.6 | 60 % local |
| clergy, scholar | +1 | ×0.75 | |
| official | +1 | ×0.8 | |
| gentry | +2 | ×0.45 | 45 % local |
| lord | +2 | ×0.3 | copper lanterns; 35 % local (lords import) |

*Finish* moves the tier materials are drawn at (`d.finish()`); the building's size still reads `d.tier()`. *Weathering*
scales the entries of a palette that carry a ground or north bias (moss, cracks) and, inversely, those with an eaves bias
(clean dressed stone). A style may override any of it under `stations` (`{"lord": {"finish": 2, "roles": {...}}}`).
Every culture now has tier 0 (rough) and tier 3 (finer) dressings so the finish shows in every culture. Archetypes can
read `d.station()` to furnish and dress for the household (banners for a lord, books for a scholar).

## 4. Local materials

`"local": {"wood": "minecraft:birch", "stone": "expanse:limestone"}` (kind keys `wood`/`timber`, `stone`, `clay`/`brick`,
`thatch`), or by role (`"wall": "minecraft:spruce_planks"`). `LocalMaterials` blends rather than paints:

- A kind key changes only roles of that kind that a builder takes from the land: timber for log and board walls, frames,
  floors, fences, stilts, furniture; stone for footings, plinths, rubble walls, quoins, chimneys, paving, carved
  accents. Roofs, doors, shutters, window joinery, railings, glass, light and cloth stay the culture's (bought in). A
  role key may name any role.
- Stone keeps the culture's grading (`MaterialFamilies`): rubble footings become the local stone's rubble, dressed
  plinths its dressed blocks, chiselled accents its carved block. Moss and cracks the local stone lacks stay as they were.
  Basalt and calcite borrow polished deepslate's and polished diorite's cuts.
- Some of the culture's material stays in the mix (the station's local share); per-building roles take the local wood on
  most houses, not all.
- Log walls are built of the local wood hewn of its bark (a birch wood gives pale stripped birch logs, not white
  bark). A light timber (birch, pale oak, aspen...) never becomes frames and beams, which keep the culture's darker wood
  so a house keeps its lines (a frame against light infill, the corners of a log house); it goes into walls, floors and
  boards.
- A culture refuses what its habits say (`"habits": {"local": {"roles": {"wall": false}}}`): the Mediterranean renders
  its walls white whatever stone lies under them.

## 5. Regions

Regional styles are worn over whatever culture builds there (the sim maps the Ancient Oakwood to temperate, the Woodland
Glades to wisteria, Cloud Forest and the Tepuis to tropical, Volcanic Highlands to moorland, Fjordlands to maritime),
with a priority when several match (the deep wins over the land above it). A region is a style file with a `region`
block (`when`, `priority`, `aliases`: the old names the `region` param still accepts, `lumen` for the old forest); it may
have conditional variants (the old forest's great hall for temples and the gentry, volcanic's chalet variant for alpine
towns). All but the deep are natural. See [regions.md](regions.md).

## 6. A town's works wear one dress

Walls, towers, gates and palisades (the `fort` purpose: the defence category) are **pinned** to the town: they wear the
dressing the settlement draws once (from `town_seed`; without one, the culture's most likely dress for its works:
Bohemian rubble walls under shingles for a temperate town) and choose their per-building materials by
that seed too, so every tower of a town has the same roof. Any plot is pinned with `pin=town` (`pin=none` unpins a
defence plot); an archetype may call `d.pinToTown()` first thing in `draw`; `d.townMat(role)` gives a role in the town's
dress for pieces that must match (a gatehouse's banner poles). `town_variant` forces the town's dressing.

## 7. Making the renders

```
cd hearth
./gradlew -I docs/buildings/styles/styles_preview.gradle styleSheets -Pmode=dress -Pstyle=temperate      # every dressing
./gradlew -I docs/buildings/styles/styles_preview.gradle styleSheets -Pmode=stations -Pstyle=all         # by station
./gradlew -I docs/buildings/styles/styles_preview.gradle styleSheets -Pmode=regions -Pstyle=all          # regions
./gradlew -I docs/buildings/styles/styles_preview.gradle styleSheets -Pmode=street -Pstyle=temperate     # streets by district
./gradlew -I docs/buildings/styles/styles_preview.gradle styleSheets -Pmode=local -Pstyle=northern "-Pparams=wood=minecraft:birch,stone=minecraft:tuff"
./gradlew -I docs/buildings/styles/styles_preview.gradle styleSheets -Pmode=set -Pstyle=temperate -Pparams=px=14     # a settlement's buildings, nothing forced
```

`-Pparams=px=16` renders larger (pixels per block; default 9); `seed=` and `tier=` vary the sheets. The pictures in `img/`
are the `dress` sheets of each culture's manners (cottage, town house, chapel and a street, the dressing forced on all of
them so its materials show; an archetype's own manner may differ in play) and every culture's `all` overview, converted
to WebP. To refresh them: render with the commands above, then convert (`python3` with Pillow:
`Image.open(png).save(webp, quality=82)`).

Output in `build/previews/styles/`. The offline renderer has no Expanse blocks: lumenwood, redwood, limestone, amber,
glowcaps and the other Expanse materials render as their vanilla stand-ins (the second alternative of each chain), so
the region pages carry in-game screenshots too. Every blueprint drawn is checked; problems print as `CHECK ...`.

## 8. Rules for whoever adds a style

- Two or three materials carry a building; a fourth is an accent. Mix by patches, never salt and pepper.
- A wall is one material (art review item 2): its second material takes under 10 % of the wall, is tonal (calcite in
  quartz, mud brick in packed mud; never grey in white or white in grey), and comes in patches of four or more
  blocks (wall, gable, plinth, footing and masonry roles lay their noise broad, `Role.patchiness`). Weathering (moss,
  cracks, damp) goes at the foot (`low`, a small weight with a strong bias: `{"w": 0.09, "low": 12}` on a main of
  3) and cleaner render under the eaves (`high`); nothing leans `north` on a wall, since moss over a whole face reads
  as damage. `StyleModelTest.wallsAreNotCamouflage` holds every surface dressing and tier to it (the main material at
  least 85 % of a masonry, render or brick wall away from the foot and the eaves).
- Every dressing needs contrast at 30 blocks: a light wall wants a dark frame or roof, a dark wall light trim.
- Quoins (the `wall_accent` of stone and brick kinds) alternate course by course at the corners: a strongly contrasting
  accent reads as a checkerboard on small houses. Use a tonal accent (mud brick on brick, calcite or polished diorite on
  render) or a light one on rubble, and one material, not a mix (a mixed accent checkers twice over). The kits enforce
  it (`Walls.quoined`, `Walls.tonal`): no quoins on a building under 9 wide or dressed below tier 2, and an accent more
  than 45 apart in lightness (by map colour) from the walling stays a plain corner strip, untoothed.
- The `wall_accent` heads every lintel, arch, door head and coping on stone, brick, render and adobe walls, so it shows
  as a block over every window: one material, tonal with the wall and not the wall's own block (polished granite on
  terracotta, cut sandstone on smooth sandstone, mud brick on packed mud, smooth sandstone on white terracotta). A
  contrasting or mixed accent mottles the facade (art review 2 items 6 and 23, the dry cultures' blotches).
- No bamboo planks as a wall or gable (audit L7: lemon-yellow towns from afar); bamboo is for screens, doors, fences,
  railings, furniture and trim. Give a culture two or three wall and roof families at town scale (fashions, and
  per-building roof entries such as the tropical dark, oak-brown and straw thatch), so a town is not one colour from 100
  blocks (art review 2 item 15).
- Working yards fell timber (audit L7): a culture whose garden tree is cherry, mangrove, birch or acacia gives the
  `workshop` category spruce, oak or dark oak `tree_log` and `tree_leaves` by an `overrides` entry, so billets, log decks
  and woodlots are not pink, red, white or grey-barked.
- Noble archetypes wear the gentry's dressing: a rustic sub-style that answers `farmstead` (sod, rubble, cliff masonry,
  herders' huts) says `"not": {"categories": ["noble"]}` in that clause, so the lord's stables are not a sod hut.
- No birch bark on the surface: a light local timber's posts and stakes are hewn (`LocalMaterials.hewn`), and a
  culture whose garden tree is birch gives its palisades their own stakes (an `overrides` entry for `fort`).
- Wall roles of terracotta, wool or concrete have no stairs or slabs: give the entry per-form overrides
  (`"stairs": "minecraft:granite_stairs"`) in a stone of the same colour, or copings come out as full blocks.
- Name vanilla ids exactly (the tests resolve every one) and put Expanse ids first in a chain with a vanilla stand-in of
  the same colour after.
- A sub-style that changes the wall kind changes what every archetype builds; render it on the cottage, the town house,
  the chapel and a street before shipping (`styleSheets -Pmode=dress`). `BlueprintInvariantsTest` forces every dressing on
  every archetype; `StyleModelTest` dresses buildings in every region, station and local material.
