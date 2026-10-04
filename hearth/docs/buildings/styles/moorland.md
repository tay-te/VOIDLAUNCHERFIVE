# Moorland: rubble cottages under slate

Scotland, Wales and the Pennines: low rubble-stone cottages under slate, coped gables, dry-stone walls, heather; harled (white-rendered) laird's houses; blackhouses of rubble and thatch for the poor; stone kirks.

![every dressing of the moorland culture, on its cottage](img/moorland_all.webp)

## Manners

| dressing | worn by | walls | roof | built as |
|---|---|---|---|---|
| **rubble cottage** (common manner, w 2) | not (station gentry / lord or category religious) | cobblestone, stone, andesite | deepslate tiles, cobbled deepslate | stone |
| · whitewashed (w 1) | as the common manner | smooth quartz, calcite | · | plaster |
| · thatched (w 1) | as the common manner | · | hay block | stone |
| **harled laird's house** (sub-style, w 8) | station merchant / official / gentry / lord or archetype civic/town_hall / house/manor / noble/* or category fort | calcite, smooth quartz, diorite | deepslate tiles, cobbled deepslate | stone |
| **blackhouse** (sub-style, w 4) | station poor / peasant or category farmstead | cobblestone, mossy cobblestone, andesite |  | stone |
| **kirk** (sub-style, w 6) | category religious | stone bricks, stone, mossy stone bricks | deepslate tiles, cracked deepslate tiles | stone |

A dash (·) keeps the material of the manner above. Weights compete among the dressings whose conditions hold (README §2).

### Rubble cottage

Cobblestone, stone, andesite and tuff with moss low and on the north, slate roofs, gables coped in stone. Fashions: **whitewashed** and **thatched** (aged thatch, mossy).

![moorland rubble cottage: cottage, town house, chapel and a street](img/moorland_base_rubble_cottage.webp)

### Harled laird's house

Merchants, officials, the gentry, the town hall and the defence works: white harling (calcite, quartz, diorite) on stone, slate.

![moorland harled laird's house: cottage, town house, chapel and a street](img/moorland_v02_harled_laird_s_house.webp)

### Blackhouse

For the poor and farmsteads: rubble walls under thick hipped thatch, green with moss.

![moorland blackhouse: cottage, town house, chapel and a street](img/moorland_v03_blackhouse.webp)

### Kirk

Coursed stone, slate, arched windows.

![moorland kirk: cottage, town house, chapel and a street](img/moorland_v04_kirk.webp)

## Finish by station and tier

Tier 0 roofs in rough cobbled deepslate (old stone slates) and builds window surrounds in cobble; tier 3 walls are coursed stone.

## Local materials

Local stone goes into everything that is stone, graded as the culture grades it.

---

Renders: `./gradlew -I docs/buildings/styles/styles_preview.gradle styleSheets -Pmode=dress -Pstyle=moorland` (every dressing), `-Pmode=stations`, `-Pmode=street`, `-Pmode=set`. Data: `src/main/resources/data/hearth/hearth_style/moorland.json`.
