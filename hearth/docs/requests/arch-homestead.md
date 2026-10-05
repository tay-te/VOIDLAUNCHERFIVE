# Requests from the homesteads and farms architect

None of these blocks the family; each is worked around inside the archetypes.

## For the plan module

1. **Height of farm buildings in the outer zones.** `Build.height` gives zone 2+ plots `min + 0.5 × (max − min)`, so a
   barn (11–16) gets 13–14 and a farmhouse (11–15) gets 13. Both choose their roof to fit, so a barn in the fields drops
   from a gambrel with a full hayloft to a lower loft or a plain gable. The skyline argument for the cap does not apply
   to buildings standing alone in the fields. Proposed: OUTSKIRTS-flagged archetypes get `t = max(t, 0.75)` (or their
   max height) wherever they are placed.
   **Done** (integration 3): OUTSKIRTS archetypes get `t >= 0.75` in `Build.height`.
2. **Storeys for farmhouses.** `Build.storeys` returns 1 outside zones 0–1, so every planner-placed farmhouse is told to
   be one storey; the steading and laithe houses of the timber and stone cultures are two-storey houses. Proposed: do
   not write `storeys` for OUTSKIRTS housing (the farmhouse picks 1 or 2 by culture and height), or write 2 when the
   archetype's max height is 11 or more.
   **Done** (integration 3): OUTSKIRTS housing is told 2 storeys when its max height is 11 or more.
3. **Cottages on slopes.** `house/cottage` is flagged HILLSIDE and builds a split-level house on an unterraced plot whose
   ground rises or falls three or more to the back (door from the street on one storey, a back or side door at the
   other level). It looks better than a terrace platform there; `terraced=true` keeps the flat cottage. No change
   needed, but the planner may now prefer leaving hillside cottage plots unterraced.
   Noted.
4. **Stilt houses (ON_WATER).** The house expects `origin.y` at the boardwalk/street level in front of it and lifts its
   floor at most two above that (piles to the bed of the water). The main entrance is the front (street row or the
   cell before the gallery); a DOCK POI marks the landing stage at the back when it stands over water.
   Noted.
5. **Catalogue fallbacks.** `DefaultCatalogue` still carries older numbers for these archetypes (farmhouse height 7–11
   and 4 beds, barn 7–11, longhouse 6 beds, round house 7–9 × 7–9). The registered infos are now: farmhouse 11–15, 2
   beds; barn 11–16; longhouse 3 beds; round house 8–9 × 8–9, 2 beds. Only matters where the fallback is used.
   **Done** (integration 3): the catalogue matches the registered infos.
6. **Pastures in the desert.** `Farms` gives desert settlements fields and orchards only. Pastures and barns now work
   there (walled pasture with a wooden gate, flat-roofed byre with rooftop hay); lift the restriction if wanted.
   **Done** (integration 3): desert towns plan pastures at the usual weight.

## For the sim module

7. **Stocking pastures.** Blueprints place no animals. A pasture's grazing is its plot inside the boundary ring; the
   HERDER job sites stand by the trough, the shelter, the gate and out in the grass. Farmhouse kraals (compound) and
   pens (raised) are small fenced rings with a gate; barn stalls are fenced bays off the aisle.
   Open (sim): animals are not stocked yet; `PoiType.PEN` now exists for it.

## For the api owner (optional)

8. **`PoiType.PEN`** (optional): a point marking where beasts are kept (a pasture's middle, a kraal, a pen, a stall),
   so the sim can spawn and return animals without inferring it from HERDER job sites. The family would register one
   per enclosure.
   **Done** (integration 3): `PoiType.PEN`.

## Edits made to shared classes (for the lead)

- `src/gametest/java/dev/voidmc/hearth/gametest/render/ModelLibrary.java` `tint()`: foliage tint only for the leaves
  vanilla tints (oak, jungle, acacia, dark oak, mangrove) and vines; other `*_leaves` (cherry, azalea, pale oak, ...)
  render untinted. The previews showed cherry blossom green.
- `src/main/java/dev/voidmc/hearth/build/archetype/house/HouseBuilder.java` `cottage()`: one line after `interior(...)`
  calling `FarmParts.ensureBeds(d, body, door, 2, through)`. A cottage whose roof space is too low for a bed got none
  (northern cottages had 0 beds in 17 of 18 test drawings); they now sleep downstairs. `cottage()` is only called by
  `house/cottage`.

## Phase 3 polish of the homes (stage A, homes architect)

9. **Catalogue sizes changed** (registered infos; `DefaultCatalogue` placeholders may want the same): stilt house
   9–12 × 10–13, height 10–14 (it now has a front porch, a side gallery and a boat landing behind); round house
   9–13 × 9–13, height 10–15 (odd rings seven to eleven across, a veranda round the tropical one). Bed counts unchanged
   (2 each, tested).
10. **Pens (for the sim).** Every enclosure registers a `WORK_FACE` POI with job `HERDER` where its beasts are kept,
    facing its gate: a pasture's grass (one in the middle, one per quarter on a pasture 17 × 17 or more), each barn stall,
    the compound's kraal, the raised farmstead's pen. Stock and return animals there; the herder's `JOB_SITE`s stay at
    the gate, trough and rack. (A dedicated `PoiType.PEN` would still be cleaner; the convention maps onto it one to one.)
11. **Households (for the planner and the sim).** Homes read `station` (a `Station` name) and `household`
    (`{job: <JobType>}`) and dress their living room for them (`kit/homestead/Household`); a merchant's home registers a
    `STRONGBOX`. Write `household.job` for the head of the household when a plot is assigned.
12. **For the style director (palettes):** the northern cottages now carry drip caps over their windows in `window` and
    horns in `beam`; a white `window` (birch) in the northern and tundra base dressing, and pale oak or birch beside the
    dark oak `beam`, would give the silvered-and-tarred contrast the brief asks for. The steppe rondavel paints its wall in
    `wall_accent` (or `plinth` where the accent is the wall's own colour): a contrasting accent (white or dark terracotta)
    in the "red ochre" variant would make its figures read. The mesa's `wall` has no stairs, so its parapets cannot be
    rounded over (a stairs override on its wall material would do it). Desert flat roofs show `wall_accent` copings,
    `shutter` lattices on wind towers and screens, `door` paint and an `accent` tile at thresholds.
13. **Bohemian temperate (for the style director and the planner).** The temperate cottages, farm houses and the
    laithe now build Bohemian (`kit/homestead/Bohemia`, homestead.md): steep gables at 2:1 over a two-course knee wall
    need height: a temperate cottage asks the planner for 14+ to get the full pitch (it falls back to 3:2, then the
    culture's pitch, under a lower plot). Palette wishes: a weathered grey-brown shingle (the temperate `roof` is spruce,
    it reads well) and, for the town tier, a red-brown tile to stand for Kuttenberg's clay roofs instead of deepslate
    tiles; the board gables use `roof_trim` (tarred dark oak) under shingle and `door` (spruce) under thatch.
14. **Expanse ids (natural surface rework).** Nothing in the homestead or town-home code reads Expanse biome ids. The
    desert palette (`hearth_style/desert.json`, not mine) uses `expanse:smooth_opal_sandstone`; if the `opal_dunes` →
    `red_dunes` rework renames the opal sandstone blocks, that fallback id wants updating (it falls back to vanilla, so
    nothing breaks). No glowing blocks are used on any surface home.
15. **Seen in game (for the style director).** The bayou base palette's weathered-copper roof reads green-turquoise in
    game and, with the mangrove-red trim and railings, makes the stilt house look like a pagoda rather than a bayou
    house; the "rust roof" variant (exposed and plain cut copper) or a grey tin stand-in, with willow or dark oak trim,
    would read as Louisiana. Expanse willow walls look right.
