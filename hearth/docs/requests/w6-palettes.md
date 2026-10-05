# Requests from the palettes lane (wave 6)

The palettes lane (art review items 2, 4, 6, 15, 19, 26 and the tour's moorland roof) changed the style data and
`build/style/**`. What it found that belongs to other lanes is below; what it did outside its own files is listed at the
end.

## 1. Markets lane: stripes and dyes the style no longer gives

- `Habits.stripes()` is new (`"stripes": true` only in the mediterranean and desert habits). `MarketStall` still stripes
  by its own `stripes` param (`d.chance(0.75)`) and `Fair` stripes every pavilion: please default both to
  `d.habits().stripes()` (a param may still force it), so a temperate or northern market is one cloth per stall.
- `cloth` is now chosen once per building (`Role.CLOTH` is per-building) and every surface style's cloth is madder red,
  woad `blue`, weld yellow, undyed white, light grey or brown (`StyleModelTest.surfaceClothIsNaturallyDyed`). Kits that
  name dyes themselves are not covered: `DyeWorks` racks, `TollGate` booms, `Lighthouse` bands, `TiltYard`/`Fair`
  pavilions, `Fountain` spout (review items 5, 27). Read the house colour from `d.mat(Role.CLOTH)` or keep to the same
  six colours.

## 2. Homes lane: a full storey of render under the village roof (review item 15)

The village palette is now white lime render to the eaves (mushroom stem, calcite at the foot, quartz under the eaves),
boarding only in the gable (`bohemian village` habits `walls.upper` = `plaster`). The cottage still shows one block of
wall under the roof where its eaves drop below the wall plate: please raise the eaves of the temperate cottage, croft and
farmhouse so a full storey of white wall shows (three blocks over the plinth), the roof starting at the wall plate.

## 3. Walls lane: quoins

Render dressings now give tonal quoins and string courses (`wall_accent` calcite or polished diorite on render, one
material, no mix), so the checkerboards on white walls are gone from the data. Stone and brick dressings still alternate
stone-brick quoins course by course on small houses (`Walls` STONE/BRICK: `corner || nextToCorner && odd course`): review
item 12 asks for no quoins below 9 wide or tier 2.

## 4. Bohemian civic lane: spires

Temperate's `sheet` role (spires, helms, turret caps) is now slate (deepslate tiles) with weathered copper on about one
building in nine, not oxidised copper (review item 13). If a crown asks for `Role.SHEET` it is dark now; crowns that
name copper themselves are yours.

## 5. Expanse / region

Volcanic Highlands keep the moorland's heather thatch on cottages (the region does not set `roof.thatch`); its slate roof
role shows on the laird's houses and kirks. If volcanic should always be slate-roofed, add `"roof": {"thatch": false}`
to `region/volcanic.json`'s habits (it would also unthatch any temperate cottage there).

## Done outside this lane's files (tiny, for the owners' information)

- `build/kit/trade/Shop.java`, `build/kit/trade/Pentice.java`, `build/kit/townhome/Fronts.java`: the white awning stripe
  is now also gated by `d.habits().stripes()` (one condition each).
