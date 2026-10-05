# Requests from the town homes architect

What the town homes (`docs/buildings/townhome.md`) need from files they do not own.

## For the build module owner (shared kit)

1. **`Openings.window` with a width of 2 or more lands off target on BACK and LEFT walls.** A `Side`'s cell index
   runs right-to-left as seen from outside on those walls (opposite to the coordinate), so a window of width 2 asked
   for at column x covers x and x−1 there instead of x and x+1, and a window planned against a corner can end in the
   corner post. `HouseBuilder.openings` has the same mistake. The town homes work round it (`TownHouses.firstCell`
   picks the first cell by the side's direction); a fix in `Openings` (or a `Side.span(at, width)` helper) would let
   everyone drop their workarounds.
   Open (build module): shared kit fixes.
2. **`Stairs.straight` clears three blocks over every step.** Two are enough to walk (feet and head) and a third only
   where the next step's climb needs it; the third cuts through a low roof or a floor laid before the stair. The town
   homes check the roof before building (`StairCore.clears`) and stop the stair a storey short of an attic it would
   cut. A `headroom` argument (default 3) would let a stair climb under a slope.
   Open (build module).
3. **`Facade.plan` moves the door** to the bay nearest the column asked for. Builders that must have the door where
   the street meets the floor read it back (`HouseBuilder.doorColumn`) or replan without it and add it
   (`TownHouses.planWithDoor`). An option to pin the door would save that.
   Open (build module).
4. **`Facade` braces cannot be copied.** A filtered copy of a plan (`TownHouses.filtered`) keeps openings and posts but
   loses the braces, because there is no public way to add one. A public `brace(...)` adder, or `Facade.copy(filter)`,
   would keep timber fronts braced after filtering.
   Open (build module).
5. **`Roofs.fillUnder` and the bracket helpers are package-private**; town homes that draw roofs over party walls
   reimplement small parts of them. Making them public would remove that duplication.
   Open (build module).

Nothing in the shared build machinery was edited by this family.

## For the plan module

6. **Registered infos differ from `DefaultCatalogue`** (the registered ones replace it when the build module is
   loaded; the placeholders matter only for the sim's own tests and dev tools):
   **Done** (integration 3): the catalogue's beds, heights and sizes match the registered infos.

   | archetype | beds (registered / catalogue) | height | size | upgrades |
   |---|---|---|---|---|
   | rowhouse | 2 / 4 | 9–14 / 8–12 | same | townhouse, tenement / townhouse |
   | townhouse | 4 / 6 | 14–24 / 12–18 | same | same |
   | tenement | 8 / 12 | 18–28 / 16–26 | same | same |
   | shophouse | 2 / 3 | 9–15 / 8–14 | same | townhouse, tenement / townhouse |
   | manor | 6 / 8 | same | same | same |
   | tower_house | 2 / 3 | 14–22 / 12–20 | 6–7 × 7–8 / 5–7 × 5–7 | same |
   | cliff_dwelling | 2 / 3 | 5–6 / 5–8 | same | same |
   | courtyard_house | 4 / 5 | 7–12 / 6–10 | same | same |

   Registered `beds` is what the house sleeps at its largest (tested); a narrow or low plot sleeps fewer (at least one,
   tested at every catalogue size). Settlement capacity already comes from built blueprints (`PlotFacts`), so the
   numbers only steer how many houses `Housing` plans per deficit.
7. **Cliff dwellings: plot height 5–6.** The stacking contract holds as `Stacks` has it (the upper floor at
   `floorY + maxHeight` of the plot below, set back half the depth): the roof deck's walking level is exactly that.
   A taller plot only makes a taller single room; keep the base plot at 5–6.
   Noted.
8. **`storeys` is a suggestion.** Attached houses move a storey up or down now and then by seed so a row steps its
   eaves, and every house drops storeys (then flattens its roof, then goes flat) to fit the plot's height. A planner
   that wants an even row should pass `storeys` and a height that fits it (about `storeys × 4 + 1` plus 4–6 for a
   pitched roof).
   Noted.
9. **Params the family reads** beyond the shared ones: `shop`, `terrace`, `walkway_y` (townhouse); `garden`,
   `outshot` (rowhouse); `arcade`, `trade` (shophouse); `shops`, `walkway_y` (tenement); `stacked` (cliff dwelling).
   `cut_into_slope` and `level` are not needed: the cliff dwelling reads the ground itself. `street_y` is not read: the
   houses grade their own front strip and steps from the natural ground in front, so `origin.y` at the street's
   walking level gives the best fronts.
   Noted.
10. **Upgrading a shophouse keeps its trade only as a tenement with `shops`.** A shophouse upgraded to a townhouse
    loses its MERCHANT job site (a townhouse's `shop` is a CARPENTER workroom). When the merchant matters, upgrade it
    to a tenement with `shops=true`, or leave it.
   Noted.
11. **Party walls on corners.** Corner plots read best with `corner_left`/`corner_right` set: the house then gets
    windows on that side, a hipped end where the culture hips, a corner oriel (townhouse) or a shop front round the
    corner (shophouse).
   Noted.

## For the npc / economy modules

12. **Points promised** (tested for every culture in `build/townhome/TownHomeTest`): shophouse COUNTER (merchant's
    side), MARKET_STALL (customers' side), MERCHANT JOB_SITE; manor LECTERN, GATE, HEARTH and 6 beds at its largest;
    courtyard house MEETING_POINT; tower house VIEWPOINT or GUARD_POST on its top; tenement ≥ 6 beds; every home a bed.
    Roof gardens and loggias carry VIEWPOINTs.
   Noted.

## For the lead

13. The family adds `build/archetype/TownHomeArchetypes.java`, classes in `build/archetype/house/` (Rowhouse,
    Shophouse, Tenement, TowerHouse, CliffDwelling, CourtyardHouse, Manor, the shared `TownHouses`, and the refined
    `Townhouse`), a kit in `build/kit/townhome/`, a lang fragment `lang/en_us/build_townhome.json`, a street preview
    (`gametest/townhome/StreetPreview`, run through the init script `docs/buildings/townhome_street.gradle` so the
    shared Gradle files stay untouched) and tests in `test/.../build/townhome/`. If the street preview is wanted
    permanently, register `streetPreview` in `hearth/build.gradle` the way the init script does.
   Noted.
