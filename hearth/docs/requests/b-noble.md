# Requests from the noble architect

What the nobility (`docs/buildings/noble.md`) needs from files it does not own. Nothing here blocks.

## For refine (style JSONs)

1. **A `castle` sub-style for temperate** (and a `palace` one for the south): the castle and keep read their walling,
   dressing and roof from the culture's style, which for temperate gives a pale dressed stone with dark roofs. KCD II's
   castles are rendered in ochre and white lime over rubble with red-brown tile or dark shingle, the towers' cones in
   tile. A sub-style keyed on category `NOBLE` (station `lord`) with `wall` = a warm render (e.g. smooth sandstone /
   white terracotta mixed by noise), `wall_accent` = rubble, `roof` = red tile (brick/granite stairs) or dark shingle
   would make them read Bohemian at once.
2. **`roof_trim` stripped log / `gable` stripped log** fall back to full blocks in several styles (warned in the test
   logs); harmless, but slab-capped ridges would look finer.

## For the shared machinery (edits made, all backward compatible)

- `Draft.phase(n)` / `phaseOf` / `maxPhase` / `toLastPhase`, `Compiler` grouping steps by campaign then stage kind,
  `BlueprintImpl.phase(stage)`, `BlueprintChecks.order` allowing kinds to restart with each campaign (commit 144df15c).
  Archetypes that never call `phase` compile exactly as before.

## For the planner

- `noble/palace` is limited to desert, mediterranean and karst (`cultures`), so `Nobility.future` picks it there and the
  castle elsewhere; both share the size 37–47 × 39–49, so the keep's close is the same either way.
- `noble/hunting_lodge` carries `OUTSKIRTS`; it is placed by `Nobility.lodge` near the woods anyway.
- The castle raises nine campaigns (≈ 9 × 8 stages): it is a long build, as it should be; the planner may want to
  queue it with a lower priority than the town's needs.
