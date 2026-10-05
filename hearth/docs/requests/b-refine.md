# Requests from the refine stage (B)

What the refined buildings, palettes and light pass would like from files they do not own. Nothing blocks.

## For the build core (shared machinery)

1. **Seal voids for every family.** `Voids.finish(d)` (`build/kit/refine/Voids.java`) is called at the end of every phase 2
   archetype's draw. The phase 3 families (craft, deep, extraction, industry, law, learning, noble, social, station
   homes) could call it too, or the compiler could run it before the light pass for every archetype: it only fills
   cells that no way of air reaches, so it never cuts a way through a building, and it removes most invisible lights.
2. **Light pass and support.** When a building had a dome open to its hall, the light pass hung chains from the dome's
   underside that the support invariant then rejected (temple, desert). The temple now vaults the drum instead, but the
   pass's idea of what a chain may hang from and `BlueprintChecks`' support rule should agree (a sturdy bottom face).

## For the planner

3. **Warehouse district.** A warehouse in a temperate town now always wears the burgher's or town's dress (never the
   village's); placing it in the market or harbour district (`district` param) keeps the street front coherent.
4. **Wisteria's landmark.** The Woodland Glades' `GARDEN_TOWN` signature still asks for `landmark/pagoda`; for that culture
   the pagoda archetype now draws a garden tower (a bell tower in a walled flower garden), so a signature entry
   `BELL_TOWER` then `PAGODA` reads as two towers. Consider `GARDEN_TOWN` = `List.of(Archetypes.PAGODA, Archetypes.FOUNTAIN)`
   or `PARK`.

## Shared files edited (small, backward compatible)

- `build/kit/trade/Hall.java`: `roof()` opens an unreached knee attic to the room below (`sealedKnee`).
- `build/kit/landmark/Crowns.java`: `crown()` seals the hollow of every crown but a parapet.
- `build/kit/Walls.java`, `build/style/Style.java`, `build/style/StyleLoader.java`, `HouseBuilder.java` (earlier commit):
  quoins on long walls only; fashions' fabric surviving tier overrides.
- `build/kit/light/{Grid,LightField,Session,Spaces,AutoLighter}.java`, `build/draft/Draft.java`,
  `build/blueprint/ShapeResolver.java` (earlier commit): the cheaper light pass, same output.
