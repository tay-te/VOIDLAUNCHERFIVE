# Requests from the town lane (wave 7: ART_REVIEW_2 T1-T4, planner side)

What the town lane (plan/layout, plan/road, plan/growth Housing, Lots, Parcels, Square, Expand, Grid, Stacks, Landmarks,
Siting, Beauty, Farms) did is in PLANNING.md §14.8. What it needs from other lanes:

## For the planner lane (`plan/growth` Growth, Upgrades, Needs, Program; `PlanContext`)

1. **Small edits made in your files, please keep them when you merge** (each a line or three):
   - `PlanContext.coreRadiusAt`: a farmsteads settlement of 20 souls and up keeps a crossroads town's core scale
     (3.4, not 5): the heart it nucleates is compact (`Grid.nucleated`).
   - `Growth.placeBuilding`: `!ctx.state.layout.expansive` became `!Grid.expansive(ctx)` (a nucleated farmsteads village
     makes room near its heart like any town).
   - `Growth.program`: `Lots.release(c, e.id())` became `if (Lots.build(c, e, info)) return true;`: the hall, the market
     hall and the court are built exactly on the lot kept for them since the founding (freed and searched for again, a
     hill town's hall went to whatever raised ground scored best a hundred blocks out).
   - `Growth.needs`: street lamps no longer count against the town's beauty cap, and a `lights` need (95) asks
     `Beauty.lights` for a lamp while `Beauty.lampsWanted` (town tier, 4 + pop/8 lamps).
2. **`Upgrades` and the street wall** (ART_REVIEW_2 item 3). A row continues only where the side of a row house is open;
   in a grown core the sides are mostly taken by detached neighbours' kept gaps (`SETBACK`). When `densify` rebuilds a
   batch along a street, please take the gap to a detached neighbour into the batch as well when that neighbour is a home
   the batch could rebuild, so batches meet; and set `party_left/right` on a rebuilt house whose new side touches an
   attachable house already standing (`Housing.terraced` says where rows belong now: the core, a dense layout's inner
   town, and a town's inner streets).
3. **Walkways** (ART_REVIEW_2 item 5, `Upgrades.walkway`). The floating decks are walkways whose house opposite was not
   (yet) rebuilt, or was rebuilt without the walkway door. Please issue the walkway only once both rebuilds stand (a
   later call that finds both built with matching `walkway_y`), and give it up if either is replaced or demolished.
4. **The farmsteads road budget**: `PlanContext.roadsMayGrow` still gives a farmsteads settlement the expansive budget
   (700 + 45 a head) after it has nucleated, so a nucleated village plans more lane stubs than a crossroads town of the
   same size (about 400 road plots in a city of 209 against 230-290). Consider the town budget for the roads within
   `Grid.reach` of the heart once `Grid.nucleated`, the expansive one for its tracks only.

## For the infra lane (L6 / T4: `build/archetype/infra/**`, `build/kit/infra/**`)

5. **Street lamps on roads**: road plots now carry `lamps` (int, blocks between lamps along the verge; left out when 0):
   16 on main streets, rings and the square within 1.5 core radii of the heart, 20 on a town's inner lanes and stair
   streets. Please stand a lamp (the culture's lamp kind, `Manner.lamp`) on alternate verges at that spacing, on a
   kerb or against a house wall where one stands, never in the corridor; at a junction one lamp for the crossing. Below
   town tier (`d.tier()` < 3) you may leave them out. By night the walled town's streets are dark between pools of
   door light (ART_REVIEW_2 item 19); the planner also stands lamp posts at junctions now, but a street needs its own.
6. **Road hierarchy materials** (item 16): the kinds already say the rank (`highway`, `street`, `lane`, `alley`,
   `path`). Main streets cobbled; lanes packed earth with worn edges and grass between wheel ruts; village roads
   (`path`, and lanes of a town under 60) dirt and coarse dirt; mediterranean streets warm grey stone or brick, not white.
   A main street's first fourteen blocks out of the square are now issued two wider (7 for a street of 5): pave them as
   the square's floor runs out into the street, not as a different road.
7. **Walkways stand on something** (item 5): posts to the ground at least every four blocks (`street_y` is the street
   below; the ground under each post is yours to read), or a bracket into the facades at both ends when the span is
   three or less.

## For the open-spaces lane (L4b / T9: `build/archetype/landscape/**`)

8. **The village green** (`landscape/plaza` param `green`, item 24). The heart plaza of a temperate, northern, alpine,
   moorland or wisteria village (not a walled one) now carries `green: true`: please lay it as a *náves*: grass, not
   paving, with a pond or the well, a lime tree (a broad round crown on a stout trunk), a bench or two under it, a path
   worn across; `keep` rectangles stand as they are. At town tier the planner replaces it (`replaces`) with the paved
   square, so the green only ever stands in a village.
9. **Lamp posts at corners**: the planner stands `landscape/lamp_post` (3 x 3, facing the street) on the corner where a
   lane meets its street and at the junctions near the heart; the island's flowers and benches suit a square better than
   a street corner, where a plain kerbed foot reads better (`benches` false by default when the plot is not on the
   square, `on_square` absent).

## For the homes lane (L2 / T5: `build/archetype/house/**`)

10. Rows now run on from an existing row house (`party_left`/`party_right` set toward it) and a town's inner streets are
    terraced too; the old house's own side wall toward the new one stays as it was built. A detached flank dressed with
    windows (item 9) will face its new neighbour's party wall one block away or flush; please keep those windows
    shuttered or blind when a neighbour stands flush (the build module cannot know; a param from the planner,
    `neighbour_left/right`, could tell it at build time if you want one).
