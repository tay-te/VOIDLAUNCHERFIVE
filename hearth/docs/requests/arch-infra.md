# Requests from the infrastructure architect

What the infrastructure blueprints need from code they do not own. The blueprints already work without any of it.

## For the plan module

1. **Junction levels.** A lane walked out from a road starts on the parent's centreline, but `Profile.grade` grades it
   from the smoothed ground, so its first steps (inside the parent's corridor) can sit a block above or below the
   parent's surface there. With the sim's first-built-wins writer the parent keeps its paving, so the mismatch shows as
   a one-block step at the mouth, or a pothole where the parent is a dirt lane. Please pin a lane's walking level to the
   parent's (`RoadGraph.Node.y` of the junction) for its first `parent.width / 2 + 2` steps, and the same where a lane
   runs on into the road it joins.
   **Done** (integration 3): a lane's first four columns from a junction take the junction's level where they are within a block of it (`Roads.pinToJunction`).
2. **Bridge width.** A bridge carries a parapet on each edge; at `width` 3 that leaves one walkable column (the
   blueprint uses half-height kerbs or trapdoor rails there instead, which leave the width walkable but read less as a
   bridge). Issuing bridges at the road's width + 2 (or at least 5 for streets) would let parapets stand outside the
   walking width.
   **Done** (integration 3): spans are issued at the road's width + 2, at least 5 for streets and highways; boardwalks keep the road's width.
3. **Docks.** `Docks.plan` starts a pier up to three columns past the road's edge (`start - 1` with `start` up to
   `width / 2 + 4`), leaving unpaved ground between road and pier; please start the pier on the road's edge column. It can
   also start beside a bridge run (a bridge column is access); the bridge's parapet then cuts it off. Skip bridge runs.
   **Done** (integration 3): piers start on the road's edge column, and never beside a bridge run.
4. **Stone bridges for grown towns.** A main road's bridge is issued at founding (tier 0) and the blueprint builds it in
   timber; a `replaces` rebuild at town tier would get the culture's stone arch.
   Open: no `replaces` re-issue of bridges at town tier yet.
5. **Repaving.** Road plots take the settlement's tier when issued, so the skeleton streets laid at a camp's founding
   keep a camp's dressing (worn cobbles, no sunken carriageway, sparse lamps) in a city. A `replaces` re-issue of built
   road plots at village and town tier would repave them (the blueprint dresses by tier).
   Open: no repaving re-issue yet.
6. **Lamps on lanes.** Roads put lamps only on the kerb line of five-wide roads (a lamp on a side edge would block a
   door). If lit lanes are wanted, `landscape/lamp_post` plots on edge columns the planner knows are free would do it.
   Open: no lamp posts on lanes yet.
7. **Brim-full synthetic rivers.** On `SyntheticTerrain` river, banks often stand below the water's surface (water_y 65,
   banks 61–64), so approach roads run below the water beside them; in game the water would flow onto them. Either the
   synthetic river or the approach grading (never below `water_y` within a few blocks of the water) should change.
   Open: `SyntheticTerrain`'s brim-full rivers are unchanged.
8. **Walkway doors.** The walkway meets the two facades at `walkway_y`; the tall houses must put a door there on their
   front (other architects' archetypes). The walkway's corridor caps reach a block into each facade; it never draws
   there.
   Noted.

## For the style owners (no edits made)

9. **A `roads` habit section** in the style JSON (per kind: which roles make the body, edge kind, step stone, sunken
   carriageway or kennel, lamp make) would let data packs restyle roads; `kit/infra/Dress` holds the mapping in Java
   for now, keyed by culture.
   Noted (style owners).
10. **Desert and mesa streets** read faintly (sandstone on sand, terracotta on red sand): a contrasting kerb or accent
   (red sandstone in the desert, smooth sandstone in the mesa) in `wall_accent` or `plinth` would help.
   Noted (style owners).

## Shared classes edited (for the lead)

11. `build/terrain/Site.java` (constructor from a spec), two additions, both inert without their params:
    - `terraced: true` → the footprint's ground reads at the floor level (water none): the terrace plot below has made a
      level deck there, so buildings stop founding and digging through it.
    - `street_y` → the rows in front of the plot read at the street's walking level: steps and paths from the door land
      on the graded road instead of on the natural ground the road was cut or filled from.
   Noted.
