# Requests from the landmark architect

The landmark family (`build/archetype/landmark/`, `docs/buildings/landmark.md`) needs nothing from the shared classes; it
edits none. These are notes for the plan module, the styles' owner and the integrator.

## For the plan module

1. **The culture's own landmark when there is no signature.** `Signature.NONE` lists bell tower, clock tower, cathedral,
   temple; `Landmarks.next` only falls back to `Growth.allowed(LANDMARK)` when none of those suits. So a karst town
   without the Jade Karst signature raises a temple and never its pagoda, a northern or tundra town without a signature
   never its great hall (the bell tower suits northern first), a maritime town never its lighthouse. Suggest: for
   `NONE`, rank the allowed landmarks by how specific their `cultures` are (fewest cultures first), or keep a small
   culture → landmark preference table beside the signatures.
2. **The cathedral almost never comes.** With `NONE` a temperate town takes the bell tower at town tier and the clock
   tower at city tier, and the two-landmark cap is reached before the cathedral, though the chapel's close is reserved
   for it from the village days (`Landmarks.chapel`). Suggest the city's second landmark prefer the cathedral or temple
   whenever a close is reserved.
3. **The close's size.** `Landmarks.chapel` reserves `max(chapel.maxFrontage, cathedral.minFrontage)` ×
   `max(chapel.maxDepth, cathedral.minDepth)`, so the cathedral nearly always gets its minimum, 17 × 29. It is designed to
   stand there (tested), but 21–25 × 35–41 lets it grow a nave of seven, a cloister (24 × 33 and up) and twin towers with
   room. Suggest reserving the mid size (21 × 35) where the land allows, falling back to the minimum.
4. **The lighthouse's parcel.** It is WATERFRONT with a keeper's cottage at the front and the tower on rocks at the back;
   its jetty is laid only where the plot itself holds water (it never builds outside its plot). Parcels with water in
   their back third give the best result; a dry headland still gets tower, rocks and cottage.
5. **Sizes and heights changed from `DefaultCatalogue`** (the build module's infos replace these at runtime; please align
   the defaults so dev tools and tests agree):
   - `lighthouse` 11–15 × 13–21, height 26–40 (was 7–9 × 7–9, 24–36: a tower alone; the brief asks for a cottage and a
     jetty);
   - `pagoda` 15–21 × 17–25, height 28–40 (was 9–13 × 9–13: the brief asks for a temple court, lanterns and a moon
     bridge);
   - `bell_tower` height 26–44 (was 24–40), `clock_tower` 28–44 (was 24–34), `cathedral` 26–44 (was 24–44),
     `temple` 16–30 (was 14–28), `great_hall` 14–22 (was 12–20); temple depth 17–25 (was 15–25).
6. **Cultures changed:** bell tower adds `northern` and `bayou` (forest-hall and stilt towns list it) and drops nothing;
   cathedral drops `northern` (no stave church yet: northern towns take the great hall and bell tower); temple adds
   `mediterranean` (a colonnaded temple) and keeps the eastern and warm cultures; lighthouse adds `northern` (fjord
   harbours); clock tower is for all cultures.
7. **Params the landmarks read** (all optional; defaults are seeded):
   - every landmark: `variant`;
   - `bell_tower`: `crown` (`spire`, `octagon`, `broach`, `pyramid`, `onion`, `dome`, `cupola`, `parapet`, `stave`,
     `pagoda`), `clock` (boolean);
   - `clock_tower`: `crown`;
   - `cathedral`: `towers` (`twin`, `crossing`, `campanile`), `cloister` (boolean); it reads `replaces` only as the
     planner's own record;
   - `temple`: `form` (`colonnade`, `dome`, `pyramid`, `hall`, `round`);
   - `pagoda`: `tiers` (5 or 7);
   - `lighthouse`: `bands` (boolean).
8. **Entrances.** Each landmark's first entrance is its main door's outside cell (bell, clock and lighthouse towers, the
   cathedral's west portal, the great hall's porch) or the street cell under its gateway (pagoda, temples with a court).
   Floors meet the street at the front's middle; the forecourts step to the street where the levels differ.

## For the styles' owner (no style was edited)

9. **Tropical and bayou have no stone role.** Their foundation, plinth, wall accent and paving are all wood. Landmarks
   that must be masonry (a lighthouse tower, a pyramid, a cathedral in previews) fall back to the first stony role
   (`chimney`: mud bricks) or to a fixed weathered stone-brick mix (`Stone.FALLBACK`). A stony `foundation` entry (mossy
   cobblestone, mud bricks) or a dedicated `masonry` role in those two styles would let them choose their own stone.
10. **Domes and onions in shingle-roofed styles** (alpine, temperate at tier 2, northern, tropical, karst bamboo roofs)
    are sheathed in patinated copper (`Stone.copper`), since a wooden onion reads as a pinecone; spires take copper on a
    share of towers whatever the roof. A `sheet` role (dome and spire covering) per style would make this data.
11. **The clock dial** is white and black concrete with gold marks and a rim of the roof trim, the same in every style.

## For the integrator

12. Shared classes edited: **none**. New files only: `build/archetype/landmark/*`, `build/kit/landmark/*`,
    `build/archetype/LandmarkArchetypes.java` (registrations), `lang/en_us/build_landmark.json`,
    `test/.../build/landmark/LandmarkTest.java`, `docs/buildings/landmark.md`, this file.
