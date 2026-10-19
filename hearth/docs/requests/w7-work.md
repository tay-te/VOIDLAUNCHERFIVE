# Requests from the work and storage lane (wave 7, L3)

What the work and storage archetypes still need from files other lanes own. Each item names the file, the defect as
`structureAudit` shows it, and what would close it. Updated after merging `hearth` with the access kit (Doors, Guards,
Lofts.finish, the terrace hatch).

## Done here in other lanes' files (small, listed for the owners)

- **`kit/trade/Hall.terrace`** (L1): with no stair or hatch the deck gets no awning or pots either (they made an
  unreached furnished "room" on the chandler's and tannery's desert decks); the hatch's lid is laid closed on the deck
  beside the hatch, not stood open against the parapet (its top edge was a step onto the coping: 13-19 fall-edge cells
  per desert dye works, paper mill, tailor and watermill); the awning's back pole never stands against the deck room's
  wall (it closed the way from the hatch to the room's door in the desert tailor).
- **`kit/trade/Hall.crown`** (L1): where the plot is too low for `Adobe.parapet` (it wants three rows over the deck),
  the wall is carried up one course round the deck, so a deck the hatch reaches is never an open edge.

## L0 (gate and checks)

1. **Fall edges through an open trapdoor's leaf.** `Measure`'s fall-edge check ignores the leaf of an open trapdoor on
   the edge crossed (`Spaces.leaf` already stops the walk there). Measured on this branch with the leaf counted as a
   guard, the work and storage fall edges dropped from 297 cells (128 cases) to 32 (24 cases) before this lane glazed the lattices (shipyard 10,
   tailor 12, warehouse 8 to 0). The commerce lane asked the same (w7-access §3).
2. **Standing on the top edge of open shutters.** The granary's (temperate, alpine, tundra slope) remaining fall edges
   at 10/4/6 are the walker standing on the shutters outside the upper windows. A shutter's top edge is no floor.
3. **Standing on a campfire.** The charcoal kiln's (northern, tundra, steppe) are the walker standing on a brazier
   (a campfire on a wall) the light pass set on the burning clamp's flank (see L1 item 3).

## L1 (access kit, light)

1. **`Lofts.ladder` and `kit/craft/Hatch`.** This lane's `Hatch.ladder` (a ladder against a wall through a hatch, with a
   step-off cell; it boards a knee loft over again first) is used by carpentry, storehouse, watermill, windmill (post
   mill), dye works, builders' yard, shipyard, the alchemist's tower and now the warehouse attic. `kit/refine/Lofts.ladder`
   does the same for the pass; one of them can go.
2. **`Guards`.** `Guards.finish` leaves works alone, so `kit/craft/Rails` stays the work lanes' guard (`Rails.reached`
   walks the finished draft and rails every reached edge, and now glazes the lower row of a window lattice someone
   steps up into over a fall). If `Guards` takes works on later, fold `Rails` into it.
3. **The light pass lights a burning clamp's flank with a brazier** (northern and tundra charcoal kiln s1, 1/4/3): a
   campfire on a cobblestone wall on the mound, which folk then stand on. A burning clamp is lit by its own vent.
4. **`kit/Openings` lattice windows** (tropical, karst): still steppable from the floor. This lane glazes them where a
   fall lies beyond (`Rails.reached`), the commerce lane where they lead onto a ledge (`Edges.guard`); a pane behind the
   lattice's lower row in `Openings` itself would make both unnecessary.
5. **Northern and tundra corner posts.** `kit/Walls` runs a log wall's corner ends a block past the wall to the plate;
   they also stand across a narrow strip's mouth beside a log wall (the glassworks now keeps its side strip three wide
   beside log walls for this).

## L2 (homes) / kit/homestead

6. **`kit/homestead/Barns`.** The barn's hayloft is railed from `Barn.java` (`Rails.guard` after `Barns.interior`); if
   `Barns.interior` rails its own well, drop that line.

## L7 (styles)

7. **Billets.** `Works.billet` keeps cherry, mangrove, birch, pale oak, bamboo and nether stems out of cords, decks and
   stumps (spruce or oak stand in). A timber-role log for working yards in the styles would make it unnecessary.

## Left in this lane

- `work/logging_camp` tropical slope s0 (1 case): a bad flight at 15/3/7 (roof courses reached).
- Fall edges on slopes in 6 archetypes, 1-2 cells a case: storehouse tropical and bayou (the front stair's landing,
  2/3/0, kept open as the only way in), glassworks alpine and maritime (7/4/2, by the street row), watermill river
  (8/2/0, the front row over the race), brewery and salt works bayou, quarry wisteria (the face, 5/-2/12).
- `unreached_levels` counts unfurnished attics (bakery, smithy, tannery, dye works, paper mill, chandler, brick kiln,
  glassworks, granary): no furnished storey is unreached.
