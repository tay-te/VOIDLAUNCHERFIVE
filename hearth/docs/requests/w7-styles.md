# Wave 7 requests from the styles lane (L7 / T6)

What the palettes cannot fix on their own, for the lanes that own the code.

## 1. Stables ridge lantern (L5, `noble/Stables`)

The tundra stables now wear the fire hall (deepslate tiles), not the sod longhouse, so the turf cap is gone there. But
`Stables.lantern` still caps the lantern in `Turret.cap` of the roof material: a sod or thatch roof anywhere would give
it a grass or hay cap again. Cap it in boards or shingle (`ROOF_TRIM`), never turf (audit G5 stables #3).

## 2. Plain adobe walls and `dominant_share` (L2, L4, L5)

Under review 1's rule (a second material under 10 %, tonal) a plain adobe or mud-brick facade is about 0.9 one block:
the steppe mud-brick halls (watch house, courthouse 0.97, bank 0.90, alchemist tower 0.91), the mesa gallows and
hunting lodge. The palette cannot lower that without mottling again. What lowers it is architecture: vigas and canales
at the wall head, window frames and sills, a plinth course, pilasters every three, a ramada. Those are archetype/kit
work (`kit/civic/Adobe`, `kit/homestead/Adobe`, the law and civic archetypes).

## 3. Lintels in `WALL_ACCENT` (L1 or whoever owns `kit/Openings`)

`Openings` heads every window and door of a stone, brick, plaster or adobe wall with one full `WALL_ACCENT` block
(and arched windows with accent stairs). That is why a contrasting accent mottles a facade: every window gets a patch.
The styles now keep accents tonal, but a lintel the width of the opening plus one each side, or the wall's own stair
upside down (art review 2 item 23), would read as a lintel and not a blotch in every culture.

## 4. Variants without `base_when` (L7 follow-up or the style loader's owner)

A fashion (`variants` entry) does not inherit the culture's `base_when`, so the steppe town hall (category grand, which
`base_when` excludes) can still draw **felt yurt** (the sheet labels it so) and is built in felt-yurt dressing over its
own tradition's plan. Either fashions should inherit `base_when`, or each needs its own `when`. Left as is in this wave
because changing the draw reshuffles every culture's dressings.

## 5. Glade region birch in working yards

`region/glade.json` sets `tree_log` to oak and birch; a culture's `overrides` win over the region, but only the
cultures whose own tree is cherry, mangrove, birch or acacia got a `workshop` override (karst, bayou, moorland, wisteria,
mesa, steppe). A temperate or alpine logging camp in a glade can still stack birch billets. Either a region-level
override for `workshop` or `Works.logs` choosing the frame wood for billets would close it.
