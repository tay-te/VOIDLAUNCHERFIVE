# Requests from the wave 6 homes lane (art review 1, 7, 22)

## To the plan lane (art review 1, the planner half)

The town homes' height ranges are now:

| archetype | height | storeys |
|---|---|---|
| `house/townhouse` | 13–20 | three and a roof storey (a fourth now and then) |
| `house/tenement` | 16–22 | three or four and a steep dormered roof storey (five only under a flat roof) |
| `house/merchant_house` | 17–20 | three (or four) and the warehouse loft |
| `house/scribe_house` | 15–18 | three low storeys and a garret |

The review asked for merchant house 13–20 and scribe house 12–18. Below 17 and 15 they no longer sleep their
household (five and three) in every culture and size, even with the loft on knee walls and beds in the shop, and
`StationHomesTest` holds every home to its beds at its minimum height. So please:

- When the tier cap (village 2 + roof) gives a plot less than an archetype's `minHeight`, do not build that archetype
  there (prefer a rowhouse or shophouse in a village); never pass a `maxHeight` below `minHeight`.
- A merchant house in a village is fine at 17 (two tall storeys and a loft in the low-roofed cultures, three where
  the pitch is steep): it is a market-street house, the one tall house a village has.

## Proportions (art review 22)

`kit/townhome/Proportions` (owned here) gives any lane the free-standing rule: ground to ridge at most 1.6 x frontage
(`Proportions.tallest(w)`, `Proportions.detached(d)`, `Proportions.storeys(...)`). The shack, chandler, butcher and the
tropical and bayou tannery use it; other small detached buildings may too.
