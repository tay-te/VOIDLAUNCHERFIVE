# Craft workshops

The craft architect's archetypes (LIVING_WORLD.md §7.5, §8.5): six workshops of the production chains and the
merchant's counting house, registered in `build/archetype/CraftArchetypes.java`. Classes live in
`build/archetype/work/` (`Tannery`, `Butcher`, `Chandler`, `DyeWorks`, `PaperMill`, `Apiary`) and
`build/archetype/commerce/CountingHouse.java`; the craft kit in `build/kit/craft/`; names and sign texts in
`assets/hearth/lang/en_us/build_craft.json`. Tests: `src/test/java/dev/voidmc/hearth/build/craft/CraftArchetypesTest.java`
(the shared invariants per archetype × terrain × culture × facing, every style variant, every size the planner may
ask, job sites and stores, the counting house's strongbox and counter, goods per stage) and `CraftDebugTest` (one spec
in detail: walk maps, plans, columns, local POIs; set `HEARTH_CRAFT_DEBUG`).

Every archetype reads `variant` and `roof` (a `RoofForm` name); the row houses (butcher, chandler, counting house) also
read `party_left`, `party_right` and `storeys`. All keep the light rule (lit by `kit/light/Lighting`), the reach rule
and the surface rule (nothing glowing or pastel outdoors; the dyers' colours are banners of cloth and wool, the
liquors tinted water over a concrete or terracotta bed, never exposed coloured blocks).

## The catalogue

| id | frontage × depth | height | jobs | tier | flags | max |
|---|---|---|---|---|---|---|
| `work/tannery` | 9–13 × 11–15 | 11–16 | TANNER | village | OUTSKIRTS | |
| `work/butcher` | 6–9 × 8–11 | 9–14 | BUTCHER | village | ATTACHABLE | |
| `work/chandler` | 5–8 × 7–10 | 9–14 | CHANDLER | village | ATTACHABLE | |
| `work/dye_works` | 9–13 × 9–13 | 10–15 | DYER | town | | |
| `work/paper_mill` | 9–12 × 10–13 | 10–15 | PAPERMAKER | town | WATERFRONT | 1 |
| `work/apiary` | 7–11 × 7–11 | 9–12 | BEEKEEPER | hamlet | OUTSKIRTS | |
| `commerce/counting_house` | 7–10 × 9–13 | 9–14 | MERCHANT, TRADER | town | ATTACHABLE | 1 |

## The buildings

- **Tannery**: the yard of kerbed pits (lime, tan, bark ooze, water) behind a fence hung with hides, the beam house
  with the drying loft over it, open between its posts, under a great steep roof with a ridge louvre; Fez manner where
  roofs are flat (round sunk vats, hides on the terrace).
- **Butcher**: a narrow row house, the shop to the street (meat on the stall board, hooks under the jetty), the
  smokehouse and block behind, the household above.
- **Chandler**: the row house whose window glows: lit candles on the sill and the stall board, lanterns either side,
  the tallow kettle under its hood, the dipping rack, a rendering lean-to in the yard.
- **Dye works**: the dye house down one side of the plot, gable to the street: coppers by a back-wall chimney and a
  woad vat below, a wide bay to the yard; above, the loft open to the yard and hung with cloth in every colour, a
  gallery of cloth across the street gable. The yard: a rinsing channel, tall tenter frames of bright cloth that show
  over the roofs, vats of dye (round tubs, or sunk in the paving in the south), a bed of dye plants.
- **Paper mill**: on the race beside the water, the wheel turning a camshaft along the wall that drops a row of
  hammers into their troughs; the vat of white pulp, the finishing table (cartography table), the paper chest and the
  rag barrel; above, the drying loft whose long walls are ladders of louvres, rails of drying sheets inside, a ridge
  vent. Flat-roofed cultures dry paper on a frame on the terrace.
- **Apiary**: a fenced flower garden (clumps of cornflower, daisy, poppy, lilac, peony...) with hives on posts and a
  water butt, and across the back the bee house (the Bohemian *včelín*, the southern bee wall): hives set in its front
  wall at standing height, the beekeeper's room behind (honey chest, bench). The gate post carries the lamp.
- **Counting house**: a sober row house on the square: glazed window with ledgers and a candle on the sill, lamps
  either side of the door, the sign; the counting room with the counter and gold scales (COUNTER, TRADER), the
  merchant's lectern under shelves of books (MERCHANT), the strongbox chest of coin at the back (STRONGBOX); the
  office and the stores of goods on account above.

## Kit (`build/kit/craft/`)

`Craft` (steep-roof chooser that fits the plot and keeps steep gables flush, the cross gable on a broad front,
`yardPlan` for laying out a yard walkably: blocks occupied cells, cells over water and cells without headroom),
`Relief` (the second wall layer by construction), `Vats` (pits, tubs, sunk round vats, kettles), `Hangs` (hides,
cloth, paper on rails and frames), `Lofts` (open galleries, louvres, ridge vents), `ShopHouse` (the narrow row-house
shell), `Wares` (shelves of goods, candles, stall boards, hooks, scales).

## Previews

`./gradlew -I docs/buildings/craft_preview.gradle craftPreview -Parchetype=<id> -Pstyle=<cultures|all> -Pseeds=2
-Pterrain=<terrain>` writes close, street, back and far views to `build/previews/craft/<id>/`
(`dev.voidmc.hearth.gametest.craft.CraftPreview`).

## Weak spots

- The dye works on the narrowest plots (9–10 wide) leaves a three-wide yard: the channel and one tenter frame fit,
  the vats rarely do.
- The apiary's garden is small on 7–8 deep plots (the bee house takes the back four rows); its height floor (9) is
  above the planner's fallback catalogue (3–6) so the bee house never squeezes under its eaves.
- The paper mill's wheel is hidden behind high river water in the isometric preview; it reads from the race side in game.
- In-game tours were not run this session; all checks are blueprint-level.
