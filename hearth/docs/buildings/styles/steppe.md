# Steppe: round houses and earthen halls

The savanna and the Sahel: round houses of ochre plaster and dark timber under cone roofs, market rings, earthen halls with vigas for the important buildings, herders' huts; palisades of debarked stakes.

![every dressing of the steppe culture, on its cottage](img/steppe_all.webp)

## Manners

| dressing | worn by | walls | roof | built as |
|---|---|---|---|---|
| **round house** (common manner, w 2) | not (station gentry / lord or category religious / grand) | yellow terracotta, sulfur bricks|yellow terracotta, smooth sandstone | acacia, mud bricks | plaster |
| · red ochre (w 1) | as the common manner | orange terracotta, terracotta | · | plaster |
| · lime white (w 1) | as the common manner | white terracotta, smooth sandstone | · | plaster |
| · thatched (w 1) | as the common manner | · | hay block | plaster |
| **sahel earthen hall** (sub-style, w 7) | category religious / grand / public or station gentry / lord / official / merchant | packed mud, mud bricks, terracotta | packed mud, mud bricks | adobe |
| **herder's hut** (sub-style, w 3) | station poor or category farmstead | packed mud, brown terracotta, coarse dirt |  | plaster |

A dash (·) keeps the material of the manner above. Weights compete among the dressings whose conditions hold (README §2).

### Round house

Yellow ochre plaster (yellow terracotta with sulfur-brick patches, smooth sandstone under the eaves), baobab (acacia) timber, cone and hip roofs. Fashions: **red ochre**, **lime white**, **thatched**.

![steppe round house: cottage, town house, chapel and a street](img/steppe_base_round_house.webp)

### Sahel earthen hall

For public and grand buildings, the gentry and merchants: packed mud and mud brick, battered walls, vigas, flat roofs.

![steppe sahel earthen hall: cottage, town house, chapel and a street](img/steppe_v03_sahel_earthen_hall.webp)

### Herder's hut

For the poor and farmsteads: mud and brown terracotta.

![steppe herder's hut: cottage, town house, chapel and a street](img/steppe_v04_herder_s_hut.webp)

## Finish by station and tier

Tier 0 is packed mud under hay; tier 3 has terracotta trim and glazed-tile accents.

## Local materials

Local timber into frames and stakes.

## Still weak

Every defence work's palisade is debarked acacia stakes (an override): the grey acacia bark no longer shows.

---

Renders: `./gradlew -I docs/buildings/styles/styles_preview.gradle styleSheets -Pmode=dress -Pstyle=steppe` (every dressing), `-Pmode=stations`, `-Pmode=street`, `-Pmode=set`. Data: `src/main/resources/data/hearth/hearth_style/steppe.json`.
