# Requests from the work and storage lane (wave 7, L3)

What the work and storage archetypes still need from files other lanes own. Each item names the file, the defect as
`structureAudit` shows it, and what would close it.

## L1 (access kit)

1. **`kit/trade/Hall.terrace` (W1).** Still the largest blocker left in this lane: on every two-storey flat-roofed
   desert or mesa building the set-back roof room's door opens onto a deck no stair reaches. Seen after this lane's
   work in bakery, brewery, butcher, carpentry, chandler, fishery, hunters_lodge, smithy, tailor and storehouse
   (`blocked_door` and `unreached_furnished_room` at y 4 to 8, 3 to 12 cases of 84 each). Carry the stair (or a hatch
   ladder) to the deck, or build no room.
2. **Lofts: `Lofts.ladder`.** This lane added `kit/craft/Hatch` (a ladder against a wall through a hatch, with a step-off
   cell, the knee loft `Hall.roof` opened boarded over again first, and `Hatch.hoistDoor`, a half loading door: a fence
   at the sill, the shutter open over it). Used by carpentry, storehouse, watermill, windmill (post mill), dye works,
   builders' yard, shipyard and the alchemist's tower. When `Lofts.ladder` lands, fold `Hatch.ladder` into it or keep
   one and delete the other.
3. **Edges: `Guards`.** This lane added `kit/craft/Rails`: `Rails.guard` (rail every standable cell's edge over a drop
   of 4+ in a box at a level), `Rails.well`/`Rails.flight` (round a stairwell's opening on the floor above, and on the
   chests beside it) and `Rails.yard` (the yard's levels, then a walk from the entrances over the finished draft that
   rails every reached edge). If `kit/Guards` covers the same, the two should become one.
4. **`kit/Openings` lattice and trapdoor windows (tropical, karst).** A closed bottom trapdoor in a window opening is
   passable to the walker and to a player: people step out of upper windows. Most of this lane's remaining
   `fall_edge_cells` are tropical and karst upper floors at the front wall (tailor, dye works, storehouse,
   warehouse, alchemist's tower, shipyard's loft). A sill block under the lattice or a two-high lattice would close it.
5. **`kit/Rooms.furnish`.** Barrels set where their face is a pocket nobody reaches (bakery 24/84,
   `station_unusable:barrel@6/0/4`, temperate, northern, alpine 2007 east).
6. **Northern and tundra corner posts.** `kit/Walls` runs a log wall's corner ends a block past the wall to the plate;
   under a gable whose verge is flush they stand out beyond the roof. This lane keeps the verge on steep craft roofs
   over log walls (`Craft.roof`); `Hall.roof`'s own flush gables (storehouse, bakery, carpentry, warehouse) need the same.

## L2 (homes) / kit/homestead

7. **`kit/homestead/Barns`.** The barn's hayloft is now railed from `Barn.java` (`Rails.guard` after `Barns.interior`);
   if `Barns.interior` rails its own well, drop that line.

## L7 (styles)

8. **Billets.** `Works.billet` keeps cherry, mangrove, birch, pale oak, bamboo and nether stems out of cords, decks and
   stumps (spruce or oak stand in). A timber-role log for working yards in the styles would make it unnecessary.
