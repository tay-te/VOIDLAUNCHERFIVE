# Requests from the extraction architect

What the extraction family (`docs/buildings/extraction.md`) needs from files it does not own, and what the planner and
the sim should know. Nothing here blocks: each item has a workaround inside the extraction package.

## For the plan module

1. **`DefaultCatalogue` heights are behind the registered infos.** Sizes match the planner's tables; heights do not
   (the registered infos win whenever the build module is present):

   | id | DefaultCatalogue h | registered h | why |
   |---|---|---|---|
   | `extract/quarry_pit` | 4–8 | 8–14 | the treadwheel crane's mast and jib over the rim |
   | `extract/mine` | 6–10 | 6–12 | the portal's hood and the headwall above a yard cut into the hill |
   | `extract/shaft` | 8–14 | 10–16 | the shaft house's roof over the windlass |
   | `extract/clay_pit`, `extract/sand_pit` | 1–3 | 6–9 | the diggers' lean-to |
   | `extract/plantation` | 1–8 | 9–16 | grown trees in the oldest coupe |

2. **Mines want rising land.** `Extraction.place` already asks for 5 blocks of rise from an adit's mouth to the back of
   its plot; keep it. On land falling away from the street the mine's cutting stops at once and the blueprint is a
   yard with a trial pit (it still builds and passes its checks, but it is not a mine).

3. **`length` is rows from the yard.** The mine drives at most `length` rows behind its yard (and never past the
   plot's back row less one), so `length = depth - 5` gives a drift to the back of the plot.

4. **Soft pits by water.** Clay and sand pits never cut within two blocks of water (the bank is kept) and dig 3–4 spits
   below the land; `depth` larger than 5 is clamped.

## For the sim / NPC modules

5. **Work faces belong to parts.** Every part after the opening registers its own `WORK_FACE` POIs, but POIs carry no
   part, so a finished bench's faces are still listed while a later bench is being cut. Until a POI can name its part
   (a small backward-compatible addition to `Draft.LocalPoi`/`Poi`, which I have not made), send gatherers to the work
   face nearest the most recent cut, or to the faces of the deepest part built.

6. **Yield.** Cuts are air steps in `EARTHWORK` below the natural ground; the waste tip, treads, timber and rails are
   the only things placed. The woodlot's cuts are its wood banks (a ditch a block deep); its yield is the trees it plants
   in later rotations, which the blueprint cannot show: credit FORESTER yield per coupe instead.

7. **Shafts into realms.** A shaft with `realm_below` is sunk to `depth` and stops there; it does not build stairs or
   a lift into the realm (that is `deep/lift`). The station at every lift is where a crosscut into the realm could be
   driven later.
