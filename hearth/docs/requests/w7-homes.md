# Wave 7 requests from the homes lane (L2 / T5)

What the homes lane changed outside `archetype/house/**`, `kit/townhome/**`, `kit/homestead/**`, `kit/station/**`, and
what it needs from the lanes that own the rest.

## Small edits outside the lane (please keep them when you merge)

- `kit/light/Spaces.standable` (L1): a chain is not stood on. Lanterns hung on three links or more from a ridge made the
  walker find a floor on the chain (phantom rooms and "unreached levels" in the croft and farmhouse roofs). Settle's own
  `standable` matches.
- `gametest/audit/StructureAudit` (L0): `-Daudit.params=k=v;k=v` draws every case with those params (pass it through
  `JAVA_TOOL_OPTIONS`). Used to audit the town homes at the planner's village height: `storeys=2`, hard 0.

## To the gate lane (L0)

1. **Lattice and shutter windows as windows** (your item 5): every karst and tropical town home still shows two blind
   sides (`blind_sides` 2-3 in 12 cases of each of nine archetypes) because its windows are open trapdoor lattices.
2. **Courtyards are not roof holes.** `roof_hole_cells` counts a court's open patch of six or fewer cells with roof all
   round (the desert courtyard house s1, the karst one s0, where the upper gallery's spur splits the court into two
   light wells): 10-12 points of `play_score` each. A light well in a court house is the point of it; please count a
   hole only where it has no walkable floor under it, or not where the patch is the plot's own court (`d.body` covers it).
3. `cramped_rooms` splits one room into pieces where a cell is not `enclosed` (a cell under a loft's ladder hole, beside
   a hatch): the tropical and bayou shack's one room (13 walkable cells, one piece) reports two cramped rooms.

## To the styles lane (L7 / T6)

1. **Karst tier 0-1 roofs** are stripped oak with oak stairs and a bamboo trim: hay-yellow on every karst hamlet's roof
   (art review 2 item 29). The cliff dwelling's pent now takes the chimney's grey stone where the roof is timber, but the
   other karst homes at low tier still read yellow: a grey tile (tuff bricks, deepslate tiles) or rammed earth would do.
2. **The desert shack's walls are white concrete** (a flat, untextured white cube, `dominant_share` 0.91): sandstone,
   mud or a plaster with a darker plinth would read as a mud shack.
3. **Mesa terracotta has no stairs**, so a mesa parapet cannot be rounded over (the mesa cottage keeps its square top).

## To the planner / town lane

- Art review 2 item 9's other half (dressed flanks): a house drawn with `party_left`/`party_right` gets a blind party
  wall, as asked. When a row's end house stands with no neighbour yet, please leave its free side's party flag off (the
  flank then gets windows and its chimney); the homes lane cannot tell at build time. As the town lane asked, a
  `neighbour_left/right` param would let a flank keep shutters closed when a neighbour stands flush.
- Town homes now accept `storeys` 2 (townhouse, merchant house; tenement 3); the scribe house is two storeys and a garret
  standing alone, three in a row.

## Open in the homes lane

- `house/courtyard_house` play is 87.5 mean (target 90; worst 78.6, target 75 met): the roof holes above and its
  one-wide corner rooms (`cramped_rooms`).
- Desert and mesa detail for the small homes: hut 69, hovel 70, hermit hut 81, shack 63 (target 70): the plain white
  and terracotta cubes need the palettes above as much as more dressing.
- The in-game walk (`-Dhearth.tour=walk`) was not re-run this wave.
