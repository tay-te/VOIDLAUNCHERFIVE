# Requests from the station architect (homes by station)

What the homes by station (`docs/buildings/station.md`) need from files they do not own.

## For the plan module

1. **Registered infos differ from `DefaultCatalogue`** (the registered ones replace it; the placeholders matter only
   for the sim's tests and dev tools). The homes needed more room than the placeholders give them to sleep their
   households and keep their rooms walkable:

   | archetype | beds (registered / catalogue) | size | height | jobs | flags |
   |---|---|---|---|---|---|
   | hovel | 2 / 2 | 7–9 × 6–8 / 4–6 × 4–6 | 8–9 / 3–5 | — | — |
   | shack | 2 / 2 | 5–7 × 5–8 / 4–6 × 5–7 | 8–11 / 3–5 | — | ATTACHABLE |
   | croft | 3 / 3 | 9–12 × 12–16 / 6–9 × 6–9 | 9–12 / 4–7 | — | — |
   | merchant_house | 5 / 5 | 7–11 × 11–15 / 7–11 × 10–15 | 20–28 / 10–16 | MERCHANT | ATTACHABLE, VERTICAL (catalogue: ATTACHABLE) |
   | reeve_house | 4 / 4 | 10–13 × 13–16 / 8–11 × 9–12 | 13–18 / 8–12 | REEVE (catalogue: none) | — |
   | rectory | 3 / 3 | 9–12 × 14–17 / 7–10 × 8–11 | 12–17 / 7–11 | — | — |
   | scribe_house | 3 / 3 | 6–8 × 9–12 / 5–8 × 8–11 | 18–24 / 9–14 | — | ATTACHABLE |
   | hermit_hut | 1 / 1 | 6–8 × 7–9 / 4–6 × 4–6 | 7–9 / 3–5 | — | OUTSKIRTS |

   The plot height is the whole building (roof included): a pitched roof over a hut needs about as much again as its
   walls. The reeve's house now offers a REEVE job site in its office; the merchant's house is VERTICAL so it gets the
   height its loft and hoist need.

## For the style owner (refine)

2. **A temperate rectory reads as a church.** With `station: clergy` (implied for `house/rectory`) the house falls into
   the Bohemian church palette (dark stone bricks, deepslate roof) in some seeds, which makes the priest's house
   gloomy. A clergy *home* would read better in the Bohemian village or town manner (lime plaster, shingle or tile);
   a `category religious` condition rather than the station would keep the church palette for churches.
3. **Scholar homes in the temperate culture** take the Bohemian village boards; a scribe's house on the market street
   might take the Bohemian town render (as merchant and official do), so the narrow house of books matches its row.

## For the build module owner (shared kit)

4. **`StairCore.keep` keeps no approach for a straight stair.** A straight flight is entered from the passage beside
   its first step; a hearth's mantel (a stair two above the floor in front of the hearth) or a bookshelf there shuts
   it, and the floor above becomes unreachable. The station homes keep the row in front of the well themselves
   (`StationHomes.keepCore`); keeping it in `StairCore.keep` would protect the town homes too.
5. **`TownHouses.doorColumn` puts the door at a plot's corner even where the street there stands well off the floor**
   (a steep street across the front): the main entrance then cannot be walked to from the street (seen with
   `house/townhouse` on the steep synthetic terrain, desert, 11 × 15, height 17). The station homes choose the column
   nearest the middle that meets the street (`StationHomes.townDoor`).

## Nothing edited outside this family

No shared machinery, style JSON, `Role`, Gradle file or other module was edited. The debug test helpers live in this
family's test package.
