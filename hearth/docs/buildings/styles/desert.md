# Desert: sandstone and adobe

North Africa and the Middle East: sandstone and adobe houses under flat roof terraces with parapets, dark timber beams through the wall heads, blue doors, tile and awnings; earthen adobe for the poor and the workshops; dressed sandstone for merchants and public buildings; white palaces with domes.

![every dressing of the desert culture, on its cottage](img/desert_all.webp)

## Manners

| dressing | worn by | walls | roof | built as |
|---|---|---|---|---|
| **town house** (common manner, w 2) | not (station gentry / lord) | smooth sandstone, sandstone at the foot | smooth sandstone, sandstone | adobe |
| · whitewashed (w 1) | as the common manner | white concrete, smooth quartz | · | adobe |
| · mud brick (w 1) | as the common manner | mud bricks, packed mud | packed mud, mud bricks | adobe |
| · red sandstone (w 0.8) | as the common manner | smooth red sandstone, red sandstone | smooth red sandstone | adobe |
| **earthen adobe** (sub-style, w 5) | station poor / peasant or category farmstead / workshop | packed mud, mud bricks | packed mud, mud bricks | adobe |
| **dressed sandstone** (sub-style, w 8) | station merchant / official / clergy / scholar or category public / trade, tier 2+ | cut sandstone, smooth sandstone |  | stone |
| **palace** (sub-style, w 8) | station gentry / lord or category grand / noble | smooth quartz, white concrete, calcite | smooth quartz, smooth sandstone | plaster |

A dash (·) keeps the material of the manner above. Weights compete among the dressings whose conditions hold (README §2).

### Town house

Smooth sandstone with sandstone at the foot, dressed in cut sandstone (lintels, arch heads and copings tonal with the wall), terracotta roof trim, flat roofs and parapets, spruce vigas. Fashions: **whitewashed** (with blue painted doors and shutters), **mud brick** (packed-mud dressings), **red sandstone** (cut red sandstone dressings).

![desert town house: cottage, town house, chapel and a street](img/desert_base_town_house.webp)

### Earthen adobe

For the poor, farmsteads and workshops: packed mud with a little mud brick, mud-brick dressings, battered walls, flat mud roofs.

![desert earthen adobe: cottage, town house, chapel and a street](img/desert_v03_earthen_adobe.webp)

### Dressed sandstone

For merchants, officials, the clergy, scholars and public and trade buildings: cut sandstone with smooth sandstone dressings and a smooth red sandstone plinth (the terracotta lintels read as brown blotches).

![desert dressed sandstone: cottage, town house, chapel and a street](img/desert_v04_dressed_sandstone.webp)

### Palace

For the gentry and grand buildings: white render (quartz, concrete, calcite), domes in turquoise tile.

![desert palace: cottage, town house, chapel and a street](img/desert_v05_palace.webp)

**Walls (art review 2 item 6, audit L7).** Each dressing's wall is one material with at most a tonal second (under 10 %, in broad patches) and weathering only at the foot. Its `wall_accent` is one material, tonal with the wall but not the wall's own block: lintels, arch heads, quoins and copings are cut of it, so a contrasting accent dotted every window with a blotch (white on terracotta, terracotta and brown on white). No mixed accents.

## Finish by station and tier

Tier 0 is all packed mud and mud brick; tier 3 dresses plinths and paving in cut sandstone.

## Local materials

Local stone graded into plinths and dressings.

## Still weak

Turquoise tile domes use prismarine bricks: a real-world glaze colour, not a glowing block.

---

Renders: `./gradlew -I docs/buildings/styles/styles_preview.gradle styleSheets -Pmode=dress -Pstyle=desert` (every dressing), `-Pmode=stations`, `-Pmode=street`, `-Pmode=set`. Data: `src/main/resources/data/hearth/hearth_style/desert.json`.
