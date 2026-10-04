# VOID Hearth — the settlement planner

The plan module (`dev.voidmc.hearth.plan`) decides where everything in a settlement goes: the square, the streets,
every plot, and what is built next. It implements `SettlementPlanner` (ARCHITECTURE.md §5–7) and is registered in
`PlanModule.init()`. This document is the planner's half of the contract with the build module (the params every plot
carries, above all the infrastructure geometry) and with the sim (state, determinism, cost). Phase 3, the living world
(§14), adds housing by station and the quarters it makes, the lord's seat on the castle hill, workings on the
territory's resource sites with the workshops of the chains by their inputs, local materials, carrying capacity, the law,
social and civic buildings, and deep towns in the realms.

```
found(ctx)                       next(settlement, terrain, random)
  survey the site                  bring the occupancy grid up to date with the plots (each level on its own)
  choose layout + signature        tier risen? lay out districts (village tier with stations), hold the castle hill
  hold the square, the well        queue empty? plan the most urgent need that fits
  plan the skeleton roads           (roads first, then what stands on them)
  keep lots for hall, market, court hand out the next queued project (the deep town's when the surface has none)
  plan the side lanes ahead
  plan the first needs
```

## 1. Site analysis (`terrain/SiteSurvey`)

Run once at founding from a few thousand probe samples (not every column):

- **Heart**: the settlement's centre, moved ashore if it is in water, off a cliff face or crag to the nearest
  even ground (within 48 blocks), and to the cliff's foot for cliff towns.
- **Lie of the land**: a plane fitted to the ground within 40 blocks → mean gradient, the contour and uphill directions.
- **Relief** within 64 blocks; **cliff**: the biggest drop over 8 blocks within 48 (≥ 12 is a cliff), with its
  downhill direction; **buildable** share of land within 96 (dry, slope < 0.4).
- **Water**: within 128 blocks, how much, the nearest water column, and its kind — *coast* (mostly at sea level),
  *river* (elongated: principal-axis ratio > 3.5) or *lake* — with the direction along the shore or river.
- **Prominent ground**: the *rise* (most prominent dry, gentle spot 14–72 blocks out: the chapel's hill), the
  *summit* within 176 (the landmark's crag), a *headland* (dry land with water on ≥ 5 of 8 sides: the lighthouse).

## 2. Layout types and signatures (`layout/LayoutChooser`, `model/LayoutType`, `model/Signature`)

Weights: the culture's preference × what the land allows, then drawn by chance (same culture on the same land still
varies). Expansive and dense-and-tall both come out of it.

| Layout | Character | Land it needs |
|---|---|---|
| `farmsteads` | **expansive**: a hamlet green, 3–5 tracks running 280–420 blocks out, farmsteads (farmhouse + fields) ≥ 50 blocks apart along them | gentle, open |
| `crossroads` | a cross of main streets leaving a square at right angles, side lanes planned ahead into blocks | easy ground |
| `linear` | one long main street along a river or coast (or the contour), lanes down to the water, docks | water or a ridge |
| `hillside` | contour streets stepping up a slope every ~7 blocks of height, joined by stair streets straight up the fall line; terraces | a slope |
| `walled` | **dense and tall**: a wall line kept from the founding (radius 34–44), streets built through to the gates, rows of attached houses inside, rebuilt taller; farms and suburbs outside | not too steep |
| `stilt` | boardwalks over the water, stilt houses along them, the heart on the shore | water near |
| `cliff` | the town at a cliff's foot, dwellings cut into the face and stacked up it (pueblo, karst sills) | a cliff |
| `mountain` | a switchback road climbing toward the summit in 7-block legs, contour streets and stair streets, terraces | steep or high relief |
| `ring` | spokes from a market ring and three planned ring roads (r = square + 15, + 24, + 24) | flat |

Each layout fixes: core scale (core radius = scale · √population, ≥ 18), square half-size, main street width and kind,
lane spacing and length, side gaps per density band, vertical bias, whether terraces are used (`LayoutType`).

**Signatures** (ARCHITECTURE.md §7.1) come from the Expanse biome at the heart (`expanse:jade_karst` → karst cliff
town, `willow_bayou`/`boreal_muskeg` → stilt town, `opal_dunes`/`saguaro_flats` → oasis, `painted_canyons` → pueblo,
`olive_groves` → hill town, `fjordlands` → harbour, `verdant_peaks`/`maple_highlands`/`cloud_forest` → alpine,
`heather_moor` → moor, `amber_steppe` → ring town, `wisteria_vale` → garden town, `frostbloom_tundra` → steading,
`palm_coast`/`coral_coast` → lagoon, `redwood_giants` → forest hall), else from culture and land. A signature boosts its
layout and sets habits: landmark preferences, walkways between tall houses, stacked dwellings, field fans, docks.

## 3. Roads first (`road/*`, `growth/Expand`, `growth/Grid`)

- **Skeleton at founding** (`layout/Founder`): main streets leave the middle of a side of the square at right angles
  and run straight while the land allows (up to 36 blocks), then are routed by **A\*** (`road/Router`) over the
  heightfield to a target 100–300 blocks out: 8 directions, heading in the search state so turns cost, a pull toward
  the goal so roads run true, step cost rising with grade (stairs much dearer, impassable above the stair grade),
  water costing a bridge, and nothing within the road's half width of a plot. Grades are measured on a smoothed
  surface (whole-block heights make every step look like 0 or 1). Only the first stretch is built; the rest is
  reserved (`Cell.PLANNED_ROAD`) and built outward as the town grows along it.
- **Grading** (`road/Profile`): the ground along the centreline is smoothed; runs climbing faster than `road_grade`
  become **stairs** (grade ≤ `stair_grade`) where the road's role allows; the profile is clamped to its grades forward
  and backward and rounded; water runs up to `bridge_max_length` become **bridges** (level deck ≥ 2 above the water,
  never below either bank) or **boardwalks** (deck just on the water); the road is cut where it would need more than
  `max_cut` of earthwork or meets water it cannot bridge.
- **Streets planned ahead** (`growth/Grid`): when a stretch of street is built, its side lanes are walked out and
  reserved at the layout's spacing (along stair streets: about every 7 blocks of height), so houses leave their mouths
  open and the town grows in blocks. Lanes square off straight streets, follow the contour on slopes, climb as stair
  streets, stop at obstacles, stop if another road runs alongside within 10 blocks, and **join** a road or planned lane
  they meet, closing blocks. Beyond the planned core only the mouths are kept (16-block stubs). Lanes are built (issued)
  only when their frontage is needed, nearest the heart first.
- **Growth moves** when no frontage is left where a building wants to go: build a planned lane; walk a new lane at a
  good spacing; lengthen a dead-end lane (within 1.5× the planned core); extend a main road; run a farm track off a
  road beyond the houses; route an **approach** (A\* to the nearest road, stairs allowed) up to the rise or summit; a
  lane down to the water; a new boardwalk; for farmland, run a fully built main road that ends near the edge of town
  on by 64 blocks (never in expansive layouts, which reach out along their tracks). Road growth is capped at about
  `300 + 20 × (population + max(6, population / 5))` blocks so a settlement that cannot place what it wants does not
  pave the world. The cap runs a little ahead of the people living there: newcomers only come while beds are free and
  beds need frontage, so a cap on today's head count froze towns whose roads had no frontage left (a 15-soul harbour
  hamlet stood still for 400 days in the first integration run).
- **Switchbacks** (mountain holds) are laid out, not searched: legs of 40 blocks along the contour, each offset uphill
  by 7 blocks of rise over the slope, turning back at each end; graded without stairs like any road and cut where a
  leg leaves land it can grade. (An 8-direction grid cannot hold a 0.2 grade across a 0.7 slope.)
- **Bridges** go up whole or not at all; a road plot never paves over a column something stands on (it ends there).
- **Walls** (`growth/Walls`): a walled town keeps its wall line from the founding (as `Cell.WALL`, main roads passing
  through as gates); a palisade on it at 28 folk, rebuilt in stone (one `replaces` project per palisade segment) at 60,
  gatehouses over the main roads, towers about every 40 blocks. Other towns of walled cultures ring the built-up town at
  town tier (pushed out past anything standing on the line).

## 4. Plots along roads (`growth/Parcels`, `growth/Build`, `growth/Housing`, `growth/Square`, `growth/Lots`)

The frontage search: every 2 blocks along each built road and the square's edge, on each side, the first column past
the corridor is a place a plot could front. Places are scored cheaply for what the building wants (`Placement`: near
the heart, on the square, on a main street, raised for civic, on the rise, the summit, by the water, over it, just
past the edge of town, far out for farmsteads, high quiet ground for manors, the crafts ring), its district, and a
stable hash for variety; then examined best first within `search_budget`:

- a rectangle of the archetype's size (narrow and deep in the core, broad at the edge, from the archetype's ranges)
  facing the road, its front row on the corridor; set back by up to `door_path_max` along a slanting street (with a
  1-wide `path` road plot to the door);
- all its columns free, no water (or water behind for WATERFRONT, ≥ half water for ON_WATER), side gaps of its density
  band kept clear of anything standing (roads alongside make a **corner lot**);
- a floor level that meets the street at the door (raised up to 3 with steps where the plot stands on a bank); ground
  too uneven for the archetype gets a **terrace** plot under it (or, for HILLSIDE archetypes in cliff towns, the back is
  **cut into the slope**);
- the best of the first eight that fit wins, slid along the street to sit exactly its band's gap from its neighbour.

**Rows**: attachable houses in the core (and inner town of dense layouts) are laid out as rows of 2–5 along the street,
each floor meeting the street at its own door (a row steps down a hill), with party walls between neighbours.
The **square** is held open from the founding (a green with the well), paved at village tier (its `keep` lists what
stands in it), and furnished — market stalls round it, lamps at the corners, fountain and statue — two blocks in from
its edge, off the mouths of streets. Its frontage is kept for hall, chapel, inn, bank and shops. **Lots** for the town
hall, market hall and courthouse are reserved on the square (or a main street at its corner, within 34 blocks of it) at
founding, before the side lanes are planned, smaller if the square is small, looked for again while the town is below
town tier if none was free, and released when built.
The **chapel** goes on the rise with a close kept round it for the cathedral or temple a city raises in its place.

**Overlaps the planner allows** (everything else is a bug, see the tests): a building on a terrace (same footprint); a
stacked dwelling on the roof of the one below (`stacked_on`); plaza furniture inside the plaza; a gatehouse over its
road (`over_road`); towers on the wall; a rebuild over the plot it `replaces`; a walkway over a street (it takes no
ground); a door path's end touching its building's entrance.

## 5. Needs → projects (`growth/Growth`, `Needs`, `Program`, `Farms`, `Landmarks`, `Docks`, `Upgrades`, `Stacks`, `Beauty`)

Ranked each call with ±10 % chance in the order; the first that can be placed is planned; one that cannot rests for a
few calls (`cooldowns`, saved). While more than `4 + pop/6` plots are unbuilt only housing and food are planned.

| Need | Priority | How |
|---|---|---|
| builders' yard (first workplace) | 1000 | crafts ring |
| housing: residents + margin > beds + planned beds (capped by the carrying capacity) | 600 + 12/bed | the station shortest of homes (§14.1), its quarter and placement first, then the house for its band (§4) and station, farmsteads in expansive layouts, rebuilds in dense towns |
| housing: families in a home not of their station (within a fifth of slack, never past the capacity) | 240 + 4/bed | as above |
| food: farms feed < population | 420+ | field / orchard / pasture / paddy / terrace field by culture and land, just past the edge of town (outside the walls), round farmsteads |
| shortage: a plot waits for goods (`PlotView.waitingForGoods`) and the stores run low of a good the chains make (stone, wood, logs, dressed stone, brick, glass, metal, fuel, candles) | 380 | the workplace of the chain's last step, else of what feeds it (a quarry pit on a stone site, else the quarry; a kiln, then a clay pit…), at most two job sites per 40 souls; planned even while busy |
| a job's workers > its workplaces | 300+ | workplace providing that job (catalogue) |
| the program (`Program`): storehouse, fishery, carpentry, smithy, stalls, sawmill, barn, quarry, chapel, inn, bakery, bank, mills, granary, masonry, guardhouse, brewery, tailor, town hall, market hall, library, school, warehouse, manors, bathhouse; the workings and the chains' workshops (§14.3); the law and social buildings (§14.4) | 40–330 | each with a count by population and a placement, a siting by its inputs, or its own planner (workings at their sites, square furniture) |
| a lot on the square for the hall or the court, none found yet (below town tier) | 335 | `Lots` |
| the lord's keep (town), castle or palace (city), stables, gardens, hunting lodge | 90–265 | §14.2 |
| watch houses at the gates, toll gates on the roads in | 120–150 | §14.4 |
| the deep town: found it, grow it | 190 / 210 | §14.5 |
| plaza | 280 | at village tier |
| landmark (town ≥ 80 folk, a second at city) | 230 | signature's choice; summit/rise, headland, square; cathedral/temple replaces the chapel |
| docks | 150 | piers 8–16 long off roads at the water, 14 apart |
| walls | 310 | §3 |
| densify | 110–290 × vertical bias | §6 |
| stacked dwellings (signature) | 120 | §6 |
| beauty, once housed and fed | 60 | lamps, fountain, statue on the square; little squares where lanes meet (town and up, `junction` plazas); gardens; parks in a city |

Archetypes are taken only from what `HearthServices.blueprints().archetypes()` lists (`catalogue/Catalogue`): a
missing one falls back to the nearest allowed one of the same category (sharing a job first, then similar size), or the
need is skipped. The phase 3 buildings (LIVING_WORLD §8.5) are asked for exactly or not at all. Without a build module
the §6.1 and §8.5 defaults (`catalogue/DefaultCatalogue`) stand in.

## 6. Verticality

- **Density gradient**: bands by distance (core < 0.55 core radius, inner < 1, outskirts < 1.7, fringe; inside a wall
  line: core < half its radius, inner to the wall). Frontage narrows, gaps close and height caps rise toward the core
  (`Build.height`: full height in the core and for towers, ¾ inner, ½ beyond), so the skyline peaks at the heart.
- **Growing up** (`Upgrades`): built houses in the core (town: inner band; city: outskirts; walled: inside the wall)
  are rebuilt as the tallest archetype they `upgradesTo` that the tier allows — cottages → rowhouses/shophouses →
  townhouses → tenements — in batches of up to four neighbours along a street, the gaps between them taken into the new
  frontages and party walls set, deepened at the back into open ground when the new archetype needs it. Each is a
  project with `replaces` (and params `replaces`).
- **Walkways**: where a rebuilt tall house faces another house across a street of 3–8 blocks (towns with the habit:
  forest halls, hill towns, garden towns, karst towns, any city's dense core), the house opposite is rebuilt tall too
  and a `walkway` joins their fronts 8 blocks above the higher floor (both get `walkway_y`).
- **Terraces and terrace streets** on slopes (§3, §4); **towers** prefer raised ground (VERTICAL archetypes score
  height); **stacked dwellings** (`Stacks`, signature habit): a STACKABLE dwelling on the roof of a built one, set back
  half its depth, its back on (or cut into) the slope behind, up to three levels.

## 7. Infrastructure params (the contract with the build module)

All coordinates are world block coordinates. **Walking level** = the y a person stands at (the surface block is at
y − 1). `origin.y` of a linear plot is its lowest walking level; its footprint is the corridor's bounding box; its
`facing` is the cardinal direction nearest the way from its first point to its last.

**Polyline rules** (`road/Polyline`), shared by every `points` param:

- Between consecutive points `a`, `b` with `n = max(|dx|, |dz|)`, step `i` of `n` is the column
  `(a.x + r(dx·i/n), a.z + r(dz·i/n))` at walking level `a.y + r(dy·i/n)`, with `r(v) = floor(v + ½)`.
  Consecutive points are at most 8 blocks apart. (The planner's own path is exactly this.)
- A column belongs to a corridor of width `w` (odd: 1, 3, 5, 7) when the distance from its centre to the polyline
  through the points' column centres is less than `(w + 0.5) / 2`.

| Archetype | Params |
|---|---|
| `hearth:infra/road` | `points` (int array x,y,z,…), `width`, `kind`: `highway` (5, main roads beyond town), `street` (5, main streets), `lane` (3), `alley` (3, dense cores), `path` (1 door paths; 3 farm tracks). Graded: \|Δy\| ≤ 1 per step and ≤ `road_grade` over 8 blocks (+1 of rounding). |
| `hearth:infra/stairs` | `points`, `width`: a stair street; \|Δy\| ≤ 1 per step (≤ `stair_grade`), landings where it flattens. Issued only if the build module registers it; otherwise the run is a `road` (which builds steps where steep). |
| `hearth:infra/bridge` | `points` abutment to abutment (first and last on the banks at the approach roads' level, the rest at `deck_y`), `width`, `deck_y` (walking level on the deck), `water_y` (the water's surface, first non-water y), `kind`: `span` (deck ≥ water + 2, never lower than the banks; rise from the banks on the deck's ends) or `boardwalk` (deck = water + 1, many segments over shallow water, may bend), `road_kind`. |
| `hearth:infra/terrace` | footprint = the platform; `level` (walking level on top), `retain_north/south/east/west` (sides standing above the land: retaining walls), `depth` (level − lowest ground). `facing` = toward the street (steps go there). The building on it has the same footprint and `terraced: true`. |
| `hearth:infra/walkway` | `points` (two ends, just outside the two facades, y = deck), `width` (3), `deck_y`, `street_y` (the street below). Takes no ground. |
| `hearth:infra/dock` | `points` (from the last land column by its road, out over the water; y = road level then water level), `width` (3). |
| `hearth:defense/wall`, `palisade` | `points` (ground walking level along the line), `width` (3 stone, 1 palisade), `height` (8 / 5). |

## 8. Building params

Every building plot (not infrastructure) may carry:

| Param | Meaning |
|---|---|
| `street_y` | walking level of the street at the door (the floor is `origin.y`; if higher, steps up to the door) |
| `zone` | `core`, `inner`, `outer`, `fringe`: density band |
| `district` | `market`, `civic`, `crafts`, `rich`, `residential`, `farms`, `harbour` (town tier and up) |
| `gap`, `back_gap` | side and back gaps kept open beside it (part of the plan, not of the plot) |
| `party_left`, `party_right` | ATTACHABLE archetypes: a neighbour shares that wall. **Left/right are the building's own, looking out of its front door along `facing`** (left = `facing.getCounterClockWise()`) |
| `row` | number of houses in its row |
| `corner_left`, `corner_right` | a street runs along that side too (a second frontage) |
| `storeys` | suggested storeys (housing) |
| `terraced` | stands on a terrace plot of the same footprint |
| `cut_into_slope` | the ground rises past it at the back: cut it in (cliff dwellings) |
| `stacked`, `stacked_on`, `level` | stands on the roof of plot `stacked_on` (no foundations down through it); `level` 1–2 |
| `replaces` | id of the plot this rebuilds (also the project's `replaces`) |
| `walkway_y` | a walkway meets its front at this walking level: a door there |
| `on_square`, `heart` | stands in the main square (the well and furniture); `heart` also marks the main plaza |
| `junction` (plaza) | a little square on the corner where lanes meet |
| `keep` (plaza) | int array of rectangles x0,z0,x1,z1 (inclusive) inside the plaza that stand as they are |
| `over_road`, `passage_width` (gatehouse) | the road runs through it along `facing` |
| `on_wall` (tower) | stands on the wall line |
| `station` (homes) | the `Station` the home is for (§14.1); `lord` on the keep, castle or palace |
| `household` (homes) | `job` (the family's trade), and `head` (a resident id) and `name` when a family is waiting for it |
| `local` (every plot) | role → material from the territory's woods and rock (§14.3), the realm's for deep plots |
| `site`, `kind`, `rock`, `face`, `depth`, `length`, `speciality`, `realm_below` (workings) | §14.3 and LIVING_WORLD §8.5 |
| `realm` (deep plots, their roads included) | the realm id; `origin.y` is the realm floor (§14.5) |
| `top_y`, `bottom_y`, `deep_facing`, `deep_street_y`, `character` (`deep/lift`) | the lift's head on the street, its foot on the realm floor, its lower door |

`district` values: `market`, `civic`, `crafts`, `rich`, `residential`, `farms`, `harbour`, and with stations `poor` and
`castle`. Clay and sand pits may take in up to a third of their footprint of water at the bank.

## 9. State (`model/PlanState`, key `hearth:plan/state`, settlement scope)

One compound: version, layout, signature, site survey, heart and square, the road graph (nodes as an int array;
edges with role, kind, width, polyline points, runs `(start, type, deck)`, built count, limit, junction indices,
parent edge), districts (seed + weight), reservations (square, lots, the chapel's close, the castle hill, the castle's
close), the project queue (specs, `replaces`, reasons), the specs handed out last call, last tier, wall stage and
radius, counters, need cooldowns, the castle hill, and the deep town's own plan state (`deep`, nested, with its realm
id).
Int arrays are read back from NBT or JSON alike. A city's plan is a few kilobytes.

The **occupancy grid** (16×16 byte tiles, `grid/Occupancy`) is not saved: it is rebuilt from the plots, the planned
roads, the wall line and the reservations on first use, and whenever a plot the planner knew disappeared or a spec it
handed out was not taken; plots added since are marked incrementally. It can never disagree with the settlement.

## 10. Determinism, threads, cost

- Deterministic for a given settlement state and random: choices come from the random (in a fixed order) or from
  stable position hashes; collections are walked in id order; trigonometry is `StrictMath`.
- Thread-safe: the planner instance is stateless; per-call state is in `PlanContext`; the terrain cache
  (`terrain/Ground`) is shared per probe, bounded (`cache_tiles`), filled lazily per column; racing writers write
  identical values.
- Cost (JUnit `PlanPerformanceTest`, towns grown past 150 folk, 300–450 plots): `next` median < 0.1 ms, p90 < 2 ms,
  max ~3 ms. While a town grows, a call that plans a lane and the streets ahead of it, or routes an approach, takes
  2–10 ms (the worst seen in the previews: ~18 ms); approach searches are bounded (one per call, 90 000 states), so an
  unreachable crag costs a few milliseconds, and a need that only grew a road rests for a few calls. Founding, with its
  A\* skeleton, takes 30–150 ms (budgeted at 100–300 ms per town in §5.1). In the sim (integration, Expanse Earth terrain,
  dev catalogue): 2830 live calls averaged 1.6 ms (93 % under 5 ms, max 15 ms) for towns up to ~600 souls; live founding
  138 ms on average. Cities past ~1000 plots are slower (see the integration report): a call after a plot disappears
  rebuilds the occupancy grid from every plot.
- Phase 3 (measured 2026-10-03 on this shared machine): `PlanPerformanceTest` (towns past 200 folk, 430–490 plots, every
  phase 3 building in the catalogue) median 0.2–0.3 ms, p90 0.4–1.2 ms, max 2.4–3.6 ms run alone (under load from other
  builds p90 rose to ~5 ms). The fixed cost of a call that plans nothing is the per-call ledger, territory index and
  needs list (~0.2–0.5 ms for a city); a need that keeps finding no room rests longer each time (up to eight times its
  first rest, saved in the plan state), so a city with no place for its observatory does not search for one every few
  calls. Cart tracks and the castle hill's road are routed once per call (160 000 states, a 2- or 4-block grid by
  distance). In the sim (dedicated server, Expanse Earth terrain seed 20261003, the real planner over the dev catalogue,
  phase 2 sim without a survey): 976 and 1065 live calls averaged 2.4 and 2.2 ms (85–87 % under 5 ms, max 21 ms at the
  live time limit), history calls 0.5–1.9 ms. A deep town's founding scans the realm on a 10-block grid within 200
  blocks of the mine (a few thousand underground probes) once, then rests if it found no spot.
- New per-call state (`PlanContext.territory()`, `ledger`) and per-settlement caches (`PlanState.homeFacts`, `levels`)
  are derived from the settlement's plots and specs and never change a plan; the deep town's plan state is a full plan
  state of its own, so its determinism is the surface's.

## 11. Previews

```
./gradlew settlementPreview -Pculture=temperate -Pdays=30,120,400 -Pterrain=hills -Pseed=1
./gradlew settlementPreview -Pculture=temperate/walled,mediterranean/hillside -Pdays=150,400 -Pterrain=hills
./gradlew settlementPreview -Pculture=suite                        # 14 cultures × terrains at 30, 120, 400 days
./gradlew settlementPreview -Pculture=alpine -Pterrain=earth:1200:-800 -Pseed=8675309   # Expanse Earth terrain
HEARTH_PREVIEW_BOXES=1 ./gradlew settlementPreview -Pculture=temperate/crossroads -Pdays=120,260 -Pterrain=hills -Pseed=51  # the social map, the keep
HEARTH_PREVIEW_BOXES=1 ./gradlew settlementPreview -Pculture=alpine/mountain -Pdays=120,220 -Pterrain=steep+realm -Pseed=6  # a mine town and its deep town
HEARTH_PREVIEW_BOXES=1 ./gradlew settlementPreview -Pculture=alpine -Pdays=240 -Pterrain=earthdeep:1200:-800 -Pseed=8675309  # a deep town in an Expanse realm
HEARTH_PREVIEW_BOXES=1 ./gradlew settlementPreview -Pculture=desert/walled -Pdays=260 -Pterrain=flat -Pseed=2   # an oasis city with its palace
```

`culture[/layout]` forces a layout; terrains are the synthetic kinds (`steep+realm`: with a realm under it, for a deep
town), `earth[:x:z]`, or `earthdeep[:x:z]` (a town on Earth terrain over the realm nearest (x, z), read from Expanse's
cavern model). `HEARTH_PREVIEW_BOXES=1` draws the planner's boxes only (homes coloured by station, the social map).
Phase 3 previews add `_town.png` (the town close up), `_deep.png` and `_deep_iso.png` (the deep town on its realm's
floor), resource sites outlined on the plan (what is quarried, mined, dug and felled named; the land that feeds the town
faint) and a line of figures per age: folk, homes and beds by station, families in a home of their station, sites by
kind, capacity. Writes
`build/previews/settlement_<culture>_<terrain>_s<seed>_d<day>_plan.png` (hill-shaded plan: roads by kind, stairs
striped, bridges in timber, plots by category, pale while unbuilt, the square and kept lots hatched, planned streets
dotted, labels on the buildings that make the place) and `_iso.png` (isometric overview of the town, turned so it is
seen from downhill), plus `_log.txt` (every project and why). Buildings are boxes of footprint and height with the
planner's test double (`plan/testing/CatalogueDouble`); with a real build module registered, every blueprint block is
drawn in its map colour. A mini-sim (`plan/testing/MiniSim`) grows the town: logistic population toward ~220, builders
at ~330 work units a day, newcomers while beds are free, jobs from built workplaces.

## 12. Tuning (`config/hearth/plan.json`)

| Field | Default | |
|---|---|---|
| `road_grade` | 0.3 | steepest graded road (rise per block); steeper runs become stairs |
| `stair_grade` | 1.0 | steepest stair street |
| `max_cut` | 4 | deepest cut or fill a road makes before it turns or stops |
| `bridge_max_length` | 40 | longest bridge span |
| `slope_tolerance` | 3 | ground relief a building takes without a terrace |
| `hillside_tolerance` | 7 | the same for HILLSIDE archetypes |
| `terrace_max_height` | 7 | tallest retaining wall |
| `door_path_max` | 3 | longest garden path to a set-back door |
| `road_plot_length` | 48 | longest road plot (long roads are several plots) |
| `density_scale` | 1.0 | scales every layout's core radius (< 1 tighter towns) |
| `vertical_bias` | 1.0 | scales the preference for tall buildings and rebuilding up |
| `sprawl_bias` | 1.0 | scales how far farmsteads and tracks reach |
| `housing_margin` | 0.1 | spare beds planned ahead of the population (at least 2) |
| `search_budget` | 1600 | frontage places examined per placement |
| `cache_tiles` | 4096 | terrain tiles (256 columns) cached per world |
| `stations` | true | housing by station and the quarters (§14.1); off: homes by density alone, as in phase 2 |
| `extraction` | true | workings on the territory's sites (§14.3) |
| `haul_max_distance` | 320 | farthest a site may lie and still be worked |
| `carrying_capacity` | true | plan beds only for what the territory feeds |
| `trade_margin` | 1.25 | how far past what its land feeds trade lets a town grow |
| `local_materials` | true | the `local` param on every plot |
| `law_buildings`, `gallows`, `toll_gates` | true | the law's buildings; a server can drop the gallows or the toll gates |
| `social_buildings` | true | taverns, playhouses, infirmaries, almshouses, monasteries, the thieves' den… |
| `nobility` | true | the castle hill, the keep, the castle or palace, stables, gardens, the lodge |
| `deep_towns`, `deep_share` | true, 0.25 | deep towns, and the share of the town they house |
| `prevailing_wind` | −1 | degrees clockwise from north the wind blows from; negative: one per world from its seed |
| `live_time_limit_ms` | 20 | a call on the server thread stops searching frontage after this long and lets the need rest (a cold terrain cache in a big town cost 250 ms once); histories, on their own threads, are never cut short, so they stay deterministic |

Debug: `/hearth plan info` near a settlement; `-Dhearth.dev.plan.trace=true` logs every need tried, its result and
time; `-Dhearth.dev.plan.layout=<layout>` forces every settlement founded in the run to that layout (any thread: live
founding and histories alike; `PlanModule.devLayout`).

## 13. Tests (`src/test/java/dev/voidmc/hearth/plan`)

`PlanInvariantsTest` grows every layout on the synthetic terrains (160 days) and checks: no overlaps beyond §4's
allowances, no building on a road, every door on a road (or path, plaza, square green), road grades, buildings level or
terraced (or cut in), sizes within archetype ranges, nothing in water that cannot be. `PlanDeterminismTest`: same seed →
same plots, on another thread too; state round trip through NBT and JSON. `PlanPerformanceTest`: §10.
`RoadGeometryTest`: polyline reproduction, corridor widths, stairs on steep ground, bridges over rivers.
`CatalogueFallbackTest`: only registered archetypes, with fallbacks; the §8.5 buildings never stood in for.
`StationHousingTest`: homes name their station and household, families live in homes of their station, the poor live
farther out than the merchants, the keep stands on the castle hill, and with stations off homes are as before.
`ResourcePlanTest`: workings on sites of their kind with their params, out of town, cart tracks out to them, workshops by
their inputs, local materials on (nearly) every plot, the capacity estimate, nothing dug with extraction off.
`DeepTownTest`: a mining town over a realm founds a deep town (one lift, deep plots with `realm` on the realm floor in the
realm's materials, the hall, dwellings), each level sound on its own, the same town above and below for the same seed,
the deep plan surviving the codec. `PlanInvariantsTest` also grows a mining town over a realm (each level checked on its
own, the deep one over its realm floor) and a walled desert town with its palace.

## 14. The living world (LIVING_WORLD.md §7.2, §8)

Phase 3 makes the plan a place shaped by the people in it and the land that feeds it. Every switch below is in
`plan.json` (§12); with all of them off the planner plans as in phase 2.

### 14.1 Housing by station and the social geography (`society/*`, `growth/Housing`)

Each household has a `Station` (`Resident.station`, kept by the npc module): poor, peasant, artisan, merchant, clergy,
scholar, official, gentry, lord. The **station ledger** (`society/StationLedger`, once per call) counts the beds each
station needs (its adults, with the children and elders spread over the stations as the adults are), the beds of the
homes built, under way and queued for it (a home's `station` param, else what its archetype is built for:
`Stations.inferred`), which families live in a home not of their station, and the commonest trade of each station.

Housing is then two needs in one: **beds short** (population + margin over beds, as before, with the territory's
carrying capacity as a ceiling, §14.3) at 600+, and **homes of the wrong station** (the stations' shortfalls summed,
within a fifth of slack over the town's beds and never past the capacity) at 240+. The planner draws the station to
build for by its shortfall and plans its home where that station lives:

| Station | Homes asked for (best first, falling back) | Where (placement, quarter) |
|---|---|---|
| poor | `house/hovel`, `shack`, `hut`, rooms in a `tenement` (a third of a city's poor) | `HOVELS`: the edge of town, downwind, low ground, near the tannery and the fields — inside along the wall in a walled town; the poor quarter |
| peasant | `croft`, `cottage`, `farmhouse` (peasants only), the culture's own house | `CROFTS`: the edge by the fields (inside the wall's foot in a walled town); farmsteads in expansive layouts |
| artisan | `shophouse` (the house over the workshop), `rowhouse`, `cottage` | `MAIN`: the high street, the crafts quarter by the water |
| merchant | `merchant_house`, `townhouse`, `courtyard_house`, `shophouse` | `CENTRAL`: by the market and the square |
| clergy | `rectory` | by the chapel (or temple, convent, monastery) |
| scholar | `scribe_house`, `townhouse` | by the library, university or school |
| official | `reeve_house`, `townhouse` | by the town hall, the court, the guardhouse, the keep |
| gentry | `manor`, `courtyard_house`, `tower_house` | `RICH`: high ground on the hill, the rich quarter |
| lord | the seat (§14.2) | the castle hill |

The house itself is still chosen by the density band (rows in the core, the culture's detached house in the inner
town, farmhouses at the edge, stilts over the water, cliff dwellings on steep ground), weighted by how well each suits
the station (`Stations.affinity`): a station's own home most, plain houses some, another station's home hardly at all.
A station's homes are smaller or larger within their archetype's range (`sizeBias`), rougher or finer (`finish` −1 for
the poor to +2 for the lord) and lower or taller (`storeys`).

**Quarters.** With stations on, the districts are drawn at village tier (not town), for the city the town is becoming
(the core radius at 150 folk), adding the **poor quarter** (downwind at the edge, on the low side) and the **castle
hill** (§14.2). A home's frontage score gains strongly in its own quarter and loses in the hill, the castle hill and the
poor quarter if it is not theirs (the market for the poor), and the **neighbourhood** draws like to like: +5 for each
home of the same station within 22 blocks, −4 for each two ranks away (no hovel beside a manor), capped. Rebuilds
(`Upgrades`) keep a short station's families in place (a house is rebuilt for another station only where its own has
room to spare), are rebuilt for the station that suits the new house and is shortest of homes, and carry `station` and
`household`.

Every home carries `station` and, when the ledger knows one, `household`: `job` (the family's trade, or the station's
commonest), and, when a family is waiting for a home of its station, `head` (its resident id) and `name`. The sim is
asked to move families into homes of their station (requests/p3-plan.md).

### 14.2 The lord and his seat (`society/Nobility`)

At village tier (with the districts) the **castle hill** is chosen once and kept (`PlanState.castleHill`): the most
commanding ground within reach of the future city (its core at 150 folk × 2.2, out of today's streets): height over the
heart, plus 1.6 × its prominence over the land 28 blocks round, less its own unevenness, dry and still four-fifths open
over a castle's width. It is held (`castle_hill` reservation, open ground only) until the town is a town; then the
**keep** (`noble/keep`, 265) goes up on it with a **close** kept round it big enough for the castle (`castle`
reservation), its frontage reached by a cart road up the hill (stairs only if it must). At city tier (170 folk) the
**castle** (`noble/castle`), or in the cultures that build them the **palace** (`noble/palace`), replaces the keep over
the keep and its close. Beside the seat: **stables** and the lord's **gardens**; out at the edge of a wood, his
**hunting lodge**. The seat carries `station: lord`.

### 14.3 The land that feeds the town (`resource/*`)

**Territory** (`resource/Territory`, once per call) reads `Settlement.sites()` (the sim's survey): sites in id order,
the workable ones of a kind nearest first within reach (`haul_max_distance`, and the territory's radius), the
**carrying capacity** (the food the FERTILE, PASTURE and FISH sites regrow a day over 2 a head, × `trade_margin`, + 6;
0 = unbounded when nothing is known), the **prevailing wind** (from the config or one per world from its seed, mostly
westerly) and how far downwind a column lies, and the **local materials**.

**Workings** (`resource/Extraction`): a quarry pit on the town's rock (16 folk), the logging camp at the wood's edge
nearest the town (8), a mine into an ore hill — a shaft (`extract/shaft`) where the ore lies under flat ground (30), a
clay pit on the bank (28), a sand pit (55), the salt works on the pans (18), woodlots (`extract/plantation`) behind the
camp (70). Each stands on the part of its site out of town (beyond 1.9 × the core the town will have at 150 folk, or
past its houses), fronting a road; if no road comes near the site, a **cart track** (`HAUL` role: no stairs unless the
hill demands them, frontage only for what belongs out there, and a budget of its own, 360 + 6 a head, outside the
town's road budget) runs out to it first. A mine wants a hillside: its footprint runs uphill from the portal (the
ground rises at least five blocks to its back). A working names its `site`, `kind`, `rock`, `face`, and `depth` or
`length`; a built (dug-out) working frees its site for the next. The job route (`job/miner` …) and the shortage route
(builders waiting on stone) go through the same code; with no survey the phase 2 quarry and sawmill stand in.

**The chains' workshops** stand by their inputs (the `SITED` placement with a `Siting`: an anchor and weights for the
water, the wind, the edge, height and main streets): the sawmill by the logging camp and the water, the charcoal kiln
by the camp (and the smelter), the smelter by the mine, downwind, the brick kiln by the clay pit, the glassworks by the
sand pit or the kiln, the mason's yard by the quarry, the tannery and the dye works at the downwind edge by water; a
workshop of the outskirts never stands within the town's future edge. Butchers, chandlers and the counting house keep
to the market. Shortages now follow the chains: builders waiting on dressed stone plan a mason's yard, on brick a kiln
then a clay pit, on glass a glassworks, on metal a smelter then a mine, on candles a chandler then an apiary.

**Local materials** (`resource/Materials`): every plot gets `local` (role → material in the style syntax) from the
nearest wood and rock the town works: the species of its woods (birch on a birch hillside, redwood in the giants'
valley, wisteria in the vale…) for `frame`, `beam`, `floor` (and `wall` in the timber cultures), its rock (limestone in
the karst, opal sandstone in the dunes, sandstone, red sandstone, tuff, deepslate below y 0, andesite or granite in the
mountains) for `foundation`, `plinth`, `paving` (and `wall` in moorland, desert and mesa). With no survey, the land at
the heart decides.

### 14.4 Law, social life and the ways in (`growth/Program`, `growth/Gates`, `growth/Lots`)

| Building | From | Where |
|---|---|---|
| `law/pillory` | 24 folk | in the square, as its furniture |
| `law/courthouse` | 75 | on the square: a **lot** kept for it at founding beside the hall's and the market hall's (looked for again while the town is small if none was free) |
| `law/jail` | 66 | by the court (or the hall, the guardhouse) |
| `law/gallows` | 95 (`gallows` switch) | in the square |
| `law/watch_house` | village, 30 | at every gate: beside a walled town's gatehouses inside the wall, or where each main road meets the edge |
| `law/toll_gate` | town, 80 (`toll_gates`) | across the two longest main roads, past the town's future edge, over the road like a gatehouse |
| `commerce/tavern` | 30, one per 80 | the high street and the market |
| `commerce/hostel`, `commerce/caravanserai` (its cultures), `commerce/caravan_yard` | 40 / 70 / 75 | `GATEWAY`: on a main road where it meets the edge of town |
| `civic/cemetery` | 25 | by the chapel, raised, toward the edge |
| `landscape/festival_ground` | 40 | at the edge by the fields |
| `civic/shrine` | 12, one per 70 | on the roads in |
| `noble/stables` | 50 | by the inn |
| `civic/infirmary`, `civic/orphanage` | 70 / 120 | by the convent or the chapel |
| `civic/almshouse` | 80 | by the chapel, in the poor quarter (houses the poor) |
| `civic/monastery`, `civic/convent` | 90 / 130 | `QUIET`: raised ground apart beyond the edge (outside the walls), reached by a lane of its own |
| `civic/playhouse` | 100 | the high street by the market |
| `commerce/thieves_den` | 100 | `HIDDEN`: a lane or alley of the poor quarter, never a main street |
| `civic/tilt_yard` | 110 | by the lord's seat, toward the edge |
| `work/alchemist_tower` | 120 | by the library or university |
| `civic/university`, `civic/observatory` | 160 / 170 | the civic quarter / the rise |
| `house/hermit_hut` | 60 | `QUIET`, alone |

The §8.5 buildings are asked for exactly as registered: a missing one is skipped, never stood in for by another of its
category (a monastery never falls back to a library). Homes by station fall back to the houses that exist.

### 14.5 Deep towns (`deep/*`)

When a settlement at village tier or more has a mine or shaft at work over a realm (`UndergroundProbe.realm` under it),
or a working mine and a realm under its territory, the planner founds a **deep town** (`deep/found`, 190): a spot on the
realm floor about twenty blocks out from its wall, level, near the mine; the **lift** (`deep/lift`) on the surface over
it, fronting a road (a cart track out to it first if none); the deep **square** at the lift's lower door; streets along
the wall's foot either way and one out across the floor; the **deep hall** by the square. Then the deep town grows
(`deep/grow`, 210) to its share of the town (`deep_share`, a quarter, 6–120 folk): dwellings (`deep/dwelling`, rows by
the square, the rest toward the wall on the rising rubble, cut into it), forges, glow gardens for half its food (the rest
comes down the lift), a market at 14 folk.

The deep town is planned with the surface's own machinery, level by level: `PlanState.deep` is a whole plan state of
its own (roads, reservations, queue, issued specs, occupancy), its ground is the realm floor (`RealmTerrain`: the
underground probe's floor; outside the realm the rock stands to the vault), and both levels see the settlement through a
`LevelView` that shows only their plots (the lift on both). Its layout is a cliff town's (tight, terraces, plots cut into
slopes). Its projects are handed out when the surface has none waiting. Every deep plot carries `realm`, stands at the
realm floor (`origin.y`), and its `local` is the realm's (`Materials.realm`: its rock, and its glow — glowcaps,
amber, prismite — for `light`).

The planner reaches the underground through the terrain probe (`PlanModule.underground`); see requests/p3-plan.md.

