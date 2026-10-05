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

## Integration (hearth-integrate-4)

- §1 done in part: `Build.building` gives `additions: 0` to every new house with a yard to grow into (3 rows past its least depth or 4 columns past its least frontage; not on a terrace, on water, or a hermit's hut), and `plan/growth/Additions` counts it up one at a time (a household that fills its beds first, then about one addition per four folk past a dozen, up to 1 + the tier). The sim builds an addition over the standing, lived-in house (`SimSettlement.inPlace`, costing only the steps that change). Open: parcels are not yet sized 3–4 rows deeper on purpose; houses whose parcel happens to have the room grow.
- §2 done: the sim weathers a house a decline leaves empty (`SimSettlement.weather`): `decay` 0.25 the day it is left, +0.25 every `decay_step_days` (24) to 1; the applier rewrites it in place; folk coming back rebuild it whole.
- §3 open: there is no town-preview similarity check in the code to teach.
- §4 done: `BlueprintService.dressing` (api) serves `Dressings.steps`; the social module dresses the fronts round the square on market days, the town's fronts for a festival, the couple's and their families' houses for a wedding, the bereaved house and its neighbours for a funeral (recorded and taken down with its own dressings). Winter is open: the social calendar has no first-snow event.
- §5 done: weathered plots are `ABANDONED` (nobody housed, no work).
- §6 nothing to do.
- Fixed while merging: the decay pass no longer lets plants, carpets, pots or snow hold blocks up (a shrine's cap stood on a peony that weathering took), leaves hold only leaves, and no rubble falls in a house still lived in (below 0.25); awnings go up from a board the wall holds.
