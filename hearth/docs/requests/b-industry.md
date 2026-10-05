# Requests from the industry architect

What the heavy workshops (`docs/buildings/industry.md`) need from files they do not own. Nothing here blocks: each
item has a workaround inside the industry package.

## For the plan module

1. **`DefaultCatalogue` sizes are behind the registered infos** (only the fallback catalogue is affected):

   | id | DefaultCatalogue (w × d, h) | registered (w × d, h) | why |
   |---|---|---|---|
   | `work/logging_camp` | 11–17 × 11–17, 5–8 | 12–17 × 12–17, 7–11 | bunkhouse roof and chimney, saw-pit gantry |
   | `work/charcoal_kiln` | 7–11 × 7–11, 4–7 | 11–14 × 10–13, 6–9 | two clamps, cordwood, the hut |
   | `work/brick_kiln` | 9–13 × 9–13, 6–10 | 10–14 × 10–14, 9–15 | the chimney is the landmark |
   | `work/glassworks` | 9–13 × 11–15, 7–11 | 10–14 × 12–16, 10–16 | furnace stack through the ridge |
   | `work/smelter` | 9–13 × 9–13, 8–12 | 10–14 × 10–14, 12–20 | blast-furnace stack reads on the horizon |
   | `work/salt_works` | 11–17 × 11–17, 3–6 | 11–17 × 12–17, 6–10 | boiling house and graduation tower |
   | `work/shipyard` | h 8–14 | h 9–15 | hull plus crane |
   | `commerce/caravan_yard` | h 5–8 | h 7–11 | warehouse with a hoist |

2. **Steep plots.** `IndustryArchetypesTest` skips the clipping count where the plot's relief passes the terrace limit
   (`PlanConfig.terraceMaxHeight`) or the plot's height; please confirm the planner never hands out such a plot.

## For the build module (shared machinery)

1. **`Lighting.post` and props land on doorsteps.** Archetypes that draw yards piece by piece can put a lamp post, a heap
   or a hoist in a door's outside cell; the industry kit works round it with `Works.lampPost` and `Works.keepDoors`.
   A shared "reserve the doorstep" in `Draft` (entrance cells refused to later props) would serve every family.

## For the sim / npc

1. Logging camp bunks and the charcoal burner's bed are furnishing only (`beds` 0); if workers should sleep on site,
   the info can carry beds.
