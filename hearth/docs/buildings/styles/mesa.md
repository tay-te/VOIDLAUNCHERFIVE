# Mesa: pueblos in earth and terracotta

The American Southwest: terraced adobe pueblos in earth and terracotta with lime-white bands, dark vigas, turquoise doors, ladders and roof terraces; cliff masonry of red sandstone; white missions.

![every dressing of the mesa culture, on its cottage](img/mesa_all.webp)

## Manners

| dressing | worn by | walls | roof | built as |
|---|---|---|---|---|
| **pueblo** (common manner, w 2) | not (category religious) | terracotta, packed mud, light gray terracotta | mud bricks, packed mud | adobe |
| · white (w 1) | as the common manner | white terracotta | · | adobe |
| · rust (w 1) | as the common manner | orange terracotta, red terracotta | · | adobe |
| · red sandstone (w 1) | as the common manner | smooth red sandstone, red sandstone | · | adobe |
| **cliff masonry** (sub-style, w 4) | station poor / peasant or landform steep or category farmstead / storage | red sandstone, cut red sandstone, terracotta |  | stone |
| **mission** (sub-style, w 8) | category religious / grand or station gentry / lord / official | white terracotta, calcite, terracotta | bricks, mud bricks | adobe |

A dash (·) keeps the material of the manner above. Weights compete among the dressings whose conditions hold (README §2).

### Pueblo

Terracotta and packed mud with light-grey banding, flat roofs, spruce vigas. Fashions: **white**, **rust**, **red sandstone**.

![mesa pueblo: cottage, town house, chapel and a street](img/mesa_base_pueblo.webp)

### Cliff masonry

For the poor, steep ground, farmsteads and stores: red sandstone masonry.

![mesa cliff masonry: cottage, town house, chapel and a street](img/mesa_v03_cliff_masonry.webp)

### Mission

For churches, grand buildings, the gentry and officials: white terracotta and calcite render under tile.

![mesa mission: cottage, town house, chapel and a street](img/mesa_v04_mission.webp)

## Finish by station and tier

Tier 0 is packed mud with mud-brick bands; tier 3 paves in smooth sandstone.

## Local materials

Local stone into masonry.

---

Renders: `./gradlew -I docs/buildings/styles/styles_preview.gradle styleSheets -Pmode=dress -Pstyle=mesa` (every dressing), `-Pmode=stations`, `-Pmode=street`, `-Pmode=set`. Data: `src/main/resources/data/hearth/hearth_style/mesa.json`.
