# Requests from the craft architect

What the craft workshops (`docs/buildings/craft.md`) need from files they do not own. Nothing here blocks.

## For the plan module

1. **`DefaultCatalogue` heights are below the registered infos** (only the fallback catalogue is affected; the
   registered infos win when the build module is present):

   | id | DefaultCatalogue h | registered h | why |
   |---|---|---|---|
   | `work/tannery` | 6–9 | 11–16 | beam house, drying loft and a steep roof with a ridge louvre |
   | `work/butcher`, `work/chandler` | 7–10 | 9–14 | two or three storeys in the row |
   | `work/dye_works` | 6–9 | 10–15 | dye house with its cloth loft; tenter frames six high |
   | `work/paper_mill` | 8–12 | 10–15 | stamping room under a louvred drying loft |
   | `work/apiary` | 3–6 | 9–12 | the bee house's eaves must clear the walk along its hive wall |
   | `work/paper_mill` max per settlement | 0 | 1 | one mill on the race is enough |

2. **Counting house jobs.** Registered with MERCHANT and TRADER (both have job sites), `maxPerSettlement` 1, as the
   catalogue has it. STRONGBOX sits at the back of the counting room, COUNTER at the trader's place.

## For the economy / NPC modules

1. The counting house's strongbox is an ordinary chest (gold ingots, nuggets, emeralds, paper) under a STRONGBOX POI;
   if theft or the law module wants it locked, that is behaviour, not a block.
