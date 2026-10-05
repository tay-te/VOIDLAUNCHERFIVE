# Requests from the age systems (b-age)

See `docs/buildings/age.md` for the params and the API.

## To the planner (`plan/`)

1. **Accretion.** Put `additions: 0` on a housing plot from its first build when its household may grow, and count it
   up (1, 2, ...) as the household grows or prospers (a new child, a trade that pays, a rise in station). Counting up
   only adds to the house; the planner never needs to rebuild it. A house first built with the param keeps a yard to
   grow into, so give such plots 3–4 rows more depth than the archetype's least depth (and, off party walls, 4 columns
   more frontage) where the street allows. Never add the param later to a house built without it (it would redraw the
   house smaller on its plot).
2. **Decay.** When the sim empties a plot (boom and bust, §7.2), set `decay` on it and raise it over time: about 0.25
   on the day it is left, 0.5 after a few years, 0.75 to 1 for a ruin. At 0.25 and above the plot has no beds, job
   sites or stores and is exempt from the light rule; keep nobody housed there. Lowering `decay` is a rebuild (the
   building comes back whole), not a repair in place.
3. A town-preview similarity check (§8.4) should count accretion: neighbouring houses of one archetype with different
   `additions` are not copies.

## To the social module

4. **Dressings.** On a market day, festival, wedding, funeral (mourning) or the first snow (winter, cold cultures
   only: check `Dressings.applies`), call `Dressings.steps(plot.spec(), blueprint, kind, day, terrain)` for the plots
   that dress (the market square's frontages, the couple's and families' houses, the bereaved house and its
   neighbours, the whole town for a festival), place the steps in order, and take them down after (each position back
   to air if it still holds the step's block). Steps never touch the building's own blocks or block a door.

## To the sim

5. A plot at `decay` ≥ 0.25 is a ruin to explore (and loot at the risk of the law): no residents, no work there.

## To refine (palettes)

6. Nothing blocking. Accretion builds in the plot's own roles (`WALL`, `GABLE`, `FRAME`, `ROOF`, `ROOF_TRIM`,
   `WALL_ACCENT` for annex copings, `FLOOR`, `GLASS`, `DOOR`, `BEAM`, `SHUTTER`, `RAILING`). A culture whose
   `WALL_ACCENT` has no slab gets its copings in `WALL` slabs.
