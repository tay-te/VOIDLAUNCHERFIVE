# Requests from the trade architect

What the trade buildings (`docs/buildings/trade.md`) need from files they do not own, and what the planner should
know. Nothing here blocks: each item has a workaround inside the trade package.

## For the plan module

1. **`DefaultCatalogue` is behind the registered infos.** The planner uses the build module's `ArchetypeInfo`s when
   the build module is present, so this only matters for the fallback catalogue (tests and runs without the build
   module), but the two disagree:

   | id | DefaultCatalogue (w × d, h) | registered (w × d, h) | why |
   |---|---|---|---|
   | `work/builders_yard` | 9–13 × 9–13, 5–8 | 11–17 × 11–17, 9–14 | gate, shed, two-storey office, crane |
   | `work/smithy` | 7–11 × 7–11, 6–10 | 8–12 × 8–12, 8–13 | hall plus a smoking stack over the ridge |
   | `work/carpentry`, `work/masonry`, `work/bakery` | h 6–10 | h 8–13 | loft or second storey under a pitched roof |
   | `work/brewery` | 8–12 × 9–13, 7–11 | 9–13 × 9–13, 11–16 | the oast kiln is a landmark and needs height |
   | `work/tailor` | 6–9 × 7–10, 7–11 | 6–9 × 7–11, 8–14 | two or three storeys |
   | `work/sawmill` | FORESTER, 6–10 | FORESTER + CARPENTER, 7–11 | the saw bench is joiner's work |
   | `work/quarry` | h 4–8 | h 6–12 | gantry and shed over the pit |
   | `work/fishery` | 7–11 × 7–11, 5–9 | 7–11 × 7–12, 7–14 | the maritime net loft is three floors |
   | `work/windmill` | 7–9 × 7–9, 14–20 | 13–17 × 11–15, 16–26 | sails need the frontage (span 11–15) |
   | `work/watermill` | 8–11 × 9–12, 8–12 | 9–12 × 10–13, 9–14 | race and wheel beside the house |
   | `work/hunters_lodge` | h 5–8 | h 6–10 | steep roof |
   | `storage/storehouse` | h 6–9 | h 8–13 | raised store on posts |
   | `storage/granary` | h 7–11 | h 8–12 | staddle stones under a steep roof |
   | `storage/warehouse` | h 9–13 | h 10–18 | three or four floors |

   Proposed: copy the registered values into `DefaultCatalogue` (or derive the defaults from the build module's list
   at test time).

2. **Windmill plots.** The windmill needs a frontage of 13+ for its sails (they turn across the front of the plot).
   It is VERTICAL and OUTSKIRTS; a site on a rise or ridge suits it best. If the planner ever gives it a plot narrower
   than 13, the blueprint still builds, with shorter sails.

3. **WATERFRONT means water behind.** The fishery and watermill expect the water at the back of the plot (the
   planner's current rule: water behind, at most 45 % wet). The fishery's jetty, DOCK and jetty job site appear only
   when water lies within the plot behind the house; on a dry plot it is a fish house with nets. The watermill's race
   runs in on the side where the water lies; on a dry plot the race is still built and filled with water.

4. **Height at the edges.** The planner gives zones beyond the inner ring half the height range. The trade buildings
   budget their storeys down to fit (warehouse floors, tailor/bakery storeys, the raised store's posts), so low plots
   are safe, but the landmarks look their part only near the top of the range: the windmill (max height ideally 20+),
   the brewery's oast (13+), the warehouse (14+). The windmill is VERTICAL and so already gets its max; giving the
   brewery and the warehouse at least three quarters of their range wherever they go would keep the oast and the
   hoist gable from being budgeted away.

5. **Params the trade blueprints read** (all optional): `roof` (a `RoofForm` name, everywhere a choice exists),
   `variant`; `party_left`/`party_right` (bakery, tailor); `storeys` (bakery 1–2, tailor 1–3, fishery 1–3, warehouse
   1–4); `raised` (storehouse; ignored on plots under 9 deep); `kiln` (brewery); `kind` (windmill `tower`/`post`,
   granary `staddle`/`bins`/`domes`); `sails` (windmill `cross`/`saltire`/`star`).

6. **Builders' yard `max` is 3** (as in the catalogue): a city may want a second yard near its growing edge.

## For the lead (shared files)

7. **`ARCHITECTURE.md` §6.1** says `sawmill` is "WATERFRONT if water-powered". It is OUTSKIRTS and builds its water
   wheel only when water happens to run behind the plot; a WATERFRONT variant would need a second archetype id or a
   planner-side choice of plot. Proposed wording: "`sawmill` (FORESTER, CARPENTER, OUTSKIRTS; a water wheel where water
   runs behind)".
