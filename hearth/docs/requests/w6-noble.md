# Requests from the noble lane (wave 6)

What the nobility (`docs/buildings/noble.md`) needs from files it does not own. Nothing here blocks.

## For refine (style JSONs)

1. **Edit made, one line** in `hearth_style/temperate.json`: the `bohemian castle` sub-style's `grey stone` fashion
   now carries `"when": {"not": {"archetypes": ["noble/castle", "noble/keep"]}}`. It was the dark stone-brick walls
   under dark oak that made temperate seed 0's castle read grey and black (art review item 24); the castle and keep now
   wear the base (white render, red tile), `lime over rubble`, `ochre and tile`, `ochre render` or `shingled`. The
   manor and the great hall still may wear grey stone. Merge it into any wider rework of the file.
2. **Steppe contrast for the ordu**: the steppe castle (`Ordu`) builds its ring, towers and round houses of the style's
   `wall` under its `roof`; in the steppe style both come out brown, so the enclosure reads as one mass. A paler wall
   (packed mud mixed with a lighter render) or a straw/thatch roof distinct from the walls would let the cones read.
