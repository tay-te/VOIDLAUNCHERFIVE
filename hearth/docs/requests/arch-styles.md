# Requests from the styles architect

What the style model (`build/style/**`, `docs/buildings/styles/`) needs from files it does not own.

## For the sim (culture map)

1. **Expanse's renamed surface biomes are missing from `sim/terrain/CultureMap`.** Expanse renamed five biomes when they
   became real-world landscapes: `wisteria_vale` → `woodland_glades`, `lumen_grove` → `ancient_oakwood`, `opal_dunes` →
   `red_dunes`, `prismatic_peaks` → `dolomite_peaks`, `frostbloom_tundra` → `lichen_tundra`. `CultureMap` names only the
   old ids, so a settlement founded in a world of the new Expanse falls through to tags and climate (the Ancient Oakwood,
   a dark forest by its tags, would come out northern). Please map both ids: `woodland_glades` → WISTERIA,
   `ancient_oakwood` → TEMPERATE, `red_dunes` → DESERT, `dolomite_peaks` → ALPINE, `lichen_tundra` → TUNDRA.
   (`When.RENAMED_BIOMES` in the style package holds the same table, for styles.)
2. **`plan/layout/LayoutChooser`** matches `"wisteria_vale"` by name; it should accept `"woodland_glades"` too.

## For the planner

3. **`station`** (a `Station` name) on house plots: the planner knows who lives where. It moves the finish (rough and
   patched for the poor, dressed for merchants, carved stone and copper for lords) and chooses the manner (a merchant's
   house in a temperate town is a Bohemian town house). Without it the station is implied from the archetype and the
   `district` param, which only chooses the manner.
4. **`local`** (a compound: `wood`, `stone`, `clay`, `thatch`, or role keys, to block ids) from the territory's
   resource sites (LIVING_WORLD.md §7.2): a town by a birch wood and a limestone quarry builds birch log walls and
   limestone footings.
5. **`town_seed`** on every plot of a settlement (the settlement's id is enough): walls, towers and gates wear one
   dressing drawn from it, so every tower of a town has the same roof. Without it the dressing is one per culture.
6. **`biome`** (optional): the probe reads the biome at each plot's centre for regional styles; a settlement-level
   biome given by the planner would keep a town's regional dress whole where its edge crosses into another biome.

Nothing here blocks the style work: every param is optional and every default draws a sensible building.

## Edits outside the style files

- `build/archetype/farm/Paddy.java`: the scarecrow is no longer placed on the shelter's pad, where its arms barred the
  way to the farmer's workstation (seed 31526 facing west on a slope failed the invariants once temperate had 28
  dressings). A two-line guard; nothing else changed.
- `build/terrain/Site.java` (earlier): records the biome at the plot's centre for regional styles.
