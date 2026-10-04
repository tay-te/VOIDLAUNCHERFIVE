# Requests from the civic, landmark and trade polish (stage A2)

What the polished trade, civic, landmark, landscape and infrastructure buildings need from files they do not own. None
of it blocks: every blueprint builds and passes its checks without it. Most is for the plan module; one item is for the
style director.

## For the plan module

1. **Gatehouses are never placed (bug, still open).** `Walls.placeGate` accepts a footprint only where every cell is
   `Cell.isAccess || Cell.isBuildable || PLANNED_ROAD` (`plan/growth/Walls.java`, the loop before the spec is queued). A
   gatehouse straddles the wall line beside the road, so the cells either side of the road are `Cell.WALL` (marked by
   `markLine` at founding, or by the stretches just issued in a ring wall) and the check fails every time: walled towns
   have their main roads pass the wall through bare gaps. Accept `Cell.WALL` in the footprint (it is the overlap
   PLANNING.md §4 intends), and pass the wall's `height` as the gatehouse's `wall_height` and the gatehouse's footprint
   in the neighbouring stretches' `keep` (see `arch-landscape.md` items 2 and 5).

2. **Landmark slot competition: the cathedral (and temple) rarely come.** `Landmarks.next` walks the signature's list,
   then `Growth.allowed(LANDMARK)` in catalogue order, and a city takes at most two landmarks. Without a signature a
   temperate town raises the bell tower at town tier and the clock tower at city tier, and the cap is reached before the
   cathedral, although `Landmarks.chapel` has kept its close since the village days. Suggest, in `Landmarks.next`:
   - when a close is reserved (`ctx.state.reservation("close") != null`) and the tier is CITY, offer the cathedral or
     temple (whichever `suits` the culture) before any other landmark;
   - otherwise rank the allowed landmarks by how few cultures they serve (the culture's own first: pagoda in the karst,
     great hall in the north, lighthouse on the coast), as `arch-landmark.md` item 1 proposes.
   The northern culture now has a cathedral of its own (a great stave church, `StaveCathedral`): `cathedral` lists
   `NORTHERN` among its cultures, so a northern chapel's close can be raised to it as in the other western cultures.

3. **Big landmarks on steep ground.** The cathedral (17–25 × 29–41), the great hall (15–23 × 21–31), the temple and the
   pagoda (15–21 × 17–25) level their whole plot to a podium (`kit/landmark/Grounds.podium`): on steep land that is a
   cliff of retaining wall at the downhill edge and a deep cut uphill. The chapel's `Placement.RISE` and the landmarks'
   `RISE`/`SUMMIT` placements pick the high ground, which is often the steepest. Suggest a slope limit for parcels of
   landmarks wider than 15: the spread of ground heights over the footprint at most 4 (or the plot's `maxHeight / 8`),
   falling back to the flattest candidate in the district; for the cathedral's close, check the slope when the close is
   reserved, so the chapel is put where the cathedral can follow.

4. **Heights the infos now announce** (the planner reads them from `ArchetypeInfo`; please align `DefaultCatalogue` for
   the tests and dev tools that use it):
   - `landmark/cathedral` 26–**56** (was 44): the west towers carry their belfries a full stage over the nave's ridge
     and their needle spires above that; the nave keeps the height it had;
   - `commerce/inn` 11–**22** × depth 11–**17** (was 17 high, 15 deep): three storeys on broad plots, and a stable yard
     behind with a gabled stable. Inns stand best in the core, where commerce gets its full height
     (`Build.height`: zone 0 → max); in the inner ring (three quarters of the range, 19) they keep three storeys only on
     plots 13+ wide and 13+ deep; beyond that they build two;
   - `landscape/statue` 7–**14** (was 11): a figure some seven blocks tall on a pedestal with a die and cornice. Square
     furniture takes `info.maxHeight()` (`Square.furniture`), so nothing more is needed there.

5. **Windmills want rises and room behind.** The tower mill now stands forward in its plot with its fantail or tail pole
   behind; plots 13+ deep give the fantail its full wheel. VERTICAL already gives it its maximum height (26): the
   reefing stage and tail pole appear from 16 up.

## For the style director

6. **Accents the dry south's public buildings now read.** The desert and mesa civic buildings route their dressings
   through roles, so whatever the style gives them shows:
   - `accent`: a frieze along the wall head under every flat roof's parapet (alternate blocks, over the street front; all
     round from tier 3), and the domes of towers and roof domes when the accent material cuts stairs (glazed tile, a
     coloured stone);
   - `wall_accent`: the parapet's raised corners and the stepped crest over the front, the coping, quoins and courses;
   - `cloth`: awnings and banners; `beam`: the vigas and spouts; `shutter`: the latticed screens.
   Today all of these are sandstone in the desert (`cut_sandstone`, `chiseled_sandstone`) and terracotta in the mesa, so
   the buildings stay one colour; an accent with a different hue (a blue or green glazed tile with stairs, red sandstone
   in the desert, smooth sandstone or white terracotta in the mesa) brings them to life without further code.

## Shared files edited

None. Everything is in the trade, civic, landmark, landscape and infrastructure archetypes and their kits
(`kit/landmark/Shapes`, `kit/landmark/Crowns`, `kit/civic/Towers`, `kit/civic/Adobe`, `kit/trade/Mills`), plus the new
`archetype/landmark/StaveCathedral.java`.
