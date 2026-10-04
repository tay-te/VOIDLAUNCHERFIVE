# VOID Hearth — the sim module

The sim module is the engine under every Hearth settlement: the records, the clock that grows them while nobody is
near, the atlas that gives the world a history from its seed, the world generation that writes that history, the chunk
applier that writes what was built since, persistence, embodiment, commands and the dev harness. It implements
ARCHITECTURE.md §4, §5, §11 and §12, and LIVING_WORLD.md §7 (towns thrive on the resources around them: territory,
survey, sites, chains, extraction, prosperity, caravans; §16 below). Code: `dev.voidmc.hearth.sim` (+ `client.sim`,
`mixin.sim`).

```
sim/
  SimModule, SimServer, SimConfig, LodFootprints
  settlement/  SimSettlement, SimPlot, SimStockpile, DataMap, SettlementClock, PlotFacts, Cursor, Names, PlainResidents,
               Production, Extraction, Prosperity, Spoilage, Stations
  model/       SimContext (what the model reaches outside itself), SimRules (the config as flat arrays)
  chains/      SimData (the resource economy's data), Recipe, BiomeTable
  land/        LandState, SimSite, Survey, Land, BiomeResources, BiomeLand, OreModel, Territory, Lands
  trade/       Caravans
  world/       SimWorld (HearthWorld + SimContext), SettlementStore, SimIndex, SpatialIndex, Hamlets, Woodcutter
  atlas/       Atlas, RegionPlan, SiteFinder, History, StructureAvoidance, PlotColumns
  terrain/     GeneratorProbe, ExpanseProbe, Probes, CultureMap, CultureVote, ExpanseUnderground, Undergrounds
  gen/         SettlementFeature (hearth:settlement), WorldgenGuard   + mixin/sim/FeaturePlacerMixin
  apply/       ChunkApplier, StepWriter, AppliedLedger, ChunkLedgers
  net/         ArrivalPayload
  command/     SimCommands, LandCommands, AtlasMap, Report
  dev/         DevHarness, DevCatalogue, PlaceholderBlueprints, PlaceholderBlueprint, PlaceholderPlanner, PlaceholderExcavations
client/sim/    SimClient, ArrivalBanner
```

---

## 1. A settlement is data

`SimSettlement` implements the api's `Settlement`. It holds plots (`SimPlot`, implementing `PlotView`), residents (the
api's `Resident`), a `Stockpile`, the treasury, reputation per player, embodied-resident flags, and a `DataMap` of other
modules' `HearthDataKey` data. Everything except the embodied flags is saved.

- **Plots** carry their spec, state, the single progress counter `(stage, step)`, the labour accrued in the current
  stage, whether the stage's goods were taken (`funded`), what it is stalled on, and, once built, its beds and job slots
  (so a loaded settlement needs no blueprints for them). A plot that rebuilds another (`replaces`) keeps the old spec
  after the old plot is gone (`replacedSpec`), so the old building's blocks still count as its own when writing.
- **Blueprint facts** (`PlotFacts`: stage count, steps, work and goods per stage, beds, job sites) are read once per plot
  from the BlueprintService and kept transiently. A plot whose archetype no longer exists is abandoned, not crashed on.
- **Module data**: `settlement.data(KEY)` decodes lazily on first use with the key's codec (registry ops in a running
  world); keys nobody registered are kept as raw NBT and written back unchanged.
- **Stockpile capacity** is per goods (phase 3): `storage.base` for each, plus what each standing building adds —
  `storage.by_archetype` (a granary holds food, a sawmill's yard logs and timber, a warehouse everything), else
  `per_storage` for other STORAGE buildings and `per_building` for the rest. A stage needing more than the capacity is
  clamped to it (carts bring the rest straight to site). Saves with fewer goods (phase 2 had ten) load with zeros.
- **Tiers** follow population (`SettlementTier.forPopulation`) with a 20 % margin before falling back.

## 2. The clock

`SettlementClock.advance(settlement, targetTick)` runs a settlement forward through the same model whether it is live
(`SimWorld` is its `SimContext`) or a history on a world generation thread (`History.Context`). Sim time is the overworld
clock (1000 ticks an hour, 24000 a day; sleeping moves it, so towns grow while you sleep) plus any fast-forward offset.

Time is cut into **slices that never cross a day boundary**. Each slice integrates goods and labour; each day boundary
runs the day's business. A step catches up at most `max_catch_up_days` (30); longer absences take several steps.

**Goods** (per slice; embodied residents are left out — their entities do the work): phase 3 moved this into data and
§16 — gatherers yield from their territory's sites, workshops follow the recipes of `hearth_sim/recipes`, tools wear,
folk eat bread or, without it, what the land gives. The config's `production.per_day` table is now only for trades the
data does not cover (a merchant's trade goods, a priest's offerings).

Food eaten: adult 2, child 1, elder 1.5 a day. Fractions carry over between slices. Coin per worker per day by job
(1 for most, merchant 8, minter 20, ...) — only while no economy module is registered: with one, its daily books
(taxes, passing trade, wages, upkeep on `DAY_PASSED`) are the treasury's income and the clock leaves its own coin out
(`SimContext.economyHandlesWages`), so a town is not paid twice. Histories fire no events and keep the clock's coin;
adoption trims an adopted town's treasury to 60 bits a head when the economy runs. **Trade** keeps a town from stalling forever on goods it cannot make: what the
stores cannot hold is sold to passing traders at `export_share` (0.1) of its price; a stalled stage buys what it lacks
(`import_per_day`, 96 units of each) at `import_markup` (1.5) × price, with coin first, then by bartering whatever
overflows the stores at half value. Deliberately slow and dear: a player bringing the goods is faster. Phase 3 adds
the necessities (a town down to a few days' tools buys a cartload, a starving one a few days' food, coin permitting)
and the trade between towns (caravans, §16.6).

**Construction**: labour = builders × `work_per_builder_hour` (33, what an embodied builder manages) ×
`builder_hours_per_day`/24 (16 h), spread evenly over up to ⌈builders / `builder_share`⌉ active plots (labour left
over by a plot that finishes or stalls flows to the others). Starting a stage takes its goods from the stockpile; if
short, the plot stalls and `CONSTRUCTION_STALLED` fires once per episode (goods most short of, and how many). **The oldest
stall has first claim**: while a plot waits for goods, a plot after it (in plot order, the order labour goes round) may
not start a stage that would take those goods below what the waiting plot needs; it waits too ("held back", quietly:
no event). So goods a player sells or supplies reach the build that asked for them, not smaller jobs. Steps
follow labour (`step = steps × labour / work`); when a stage's labour is done the next stage starts; when the last is
done the plot is `BUILT`, its beds and jobs count, a plot it replaced is removed, and `BUILDING_COMPLETED` fires.

**The day** (once per day crossed, also in catch-up): ageing (children grow up after 48 days, adults become elders
after 240 more and retire), elder deaths (1/60 a day, ×4 while starving), hunger (no food → `hunger_days`),
overcrowding (more people than beds + 4 tent places → `overcrowd_days`), emigration (one adult may leave a day after
3 days overcrowded or 4 hungry), births (1/30 per couple per day while beds are free), settlers (a group of 1–3 with a
chance of 0.6 a day while beds are free; they bring 10 days of food; an empty town can be resettled), trade imports,
homes (the homeless move into standing houses with free beds), jobs (builders first — base 2 + 1 per 10 residents +
yard job sites, but never more than 2 in 5 adults, 1 while food is short — then food trades when short, then every
workplace's trade; everyone else gathers), the tier (`TIER_CHANGED`), then `DAY_PASSED`.

**Growth**: after every slice, if builders are idle (fewer active plots than they can work; builders walking about as
entities count) the clock asks `planner.next(settlement, terrain, random)` — up to 4 proposals in a row, while at most
one plot is stalled — and adds each as `PLANNED`. A road (`infra/*`) does not count toward the 4: roads come first, the
planner often queues a lane and then the houses along it, so the clock asks again in the same step (up to 4 roads in a
row, without spending the per-tick budget). A null answer backs off half a day. Live, planner calls are budgeted
(`planner_calls_per_tick`, 1); histories and explicit fast-forwards are not. Their cost is measured
(`SettlementClock.PlannerTimings`, in `/hearth sim stats`).

**Rebuilds** (`replaces`): the old plot stands until the new one's first stage is funded; then it is `DEMOLISHING`
(its beds and job sites stop counting, `PLOT_ABANDONED` fires with "rebuilt as …"), the applier clears its blocks from
loaded chunks before the new ones go in (§6), and it is removed from the record when the rebuild is built. If the
rebuild is abandoned the old plot is `BUILT` again (and written back where it was cleared).

**Daughter hamlets** (§5.1): a town of at least `daughters.min_population` (30) with no free bed for
`daughters.crowded_days` (5) days in a row sends settlers out (at most `max_per_town` hamlets, `cooldown_days` apart).
`Hamlets` looks for the site off the hot path — a few random candidates a tick (0.2 ms) 600–2500 blocks out, scored for
gentle dry ground above the sea with water near, rejected by `whyNotFound` (which never computes a region on the server
thread; an unsurveyed region is planned off-thread and asked about again) — and the hamlet is laid out on a worker
thread (`SimWorld.foundDaughterAsync`), then registered on the server thread. Up to `settlers` (4) adults leave (the
homeless first), with a share of the parent's stores and coin; `RESIDENT_REMOVED` sees `extra("sim").left_for` on them,
so listeners can tell a departure from a death. Histories never found hamlets (they keep to their region).

**Scheduling** (`SimServer`): a priority queue keyed by due tick, so a tick only touches settlements that are due.
Embodied settlements come round every `embodied_step_interval` (20) ticks, the rest every `step_interval` (1200), a
settlement that is behind every tick until it has caught up. The per-tick budget is `budget_ms` (2 ms) while the
average tick time is under 60 % of `high_mspt` (40 ms), shrinking linearly to `min_budget_ms` (0.25 ms) at it. A
settlement far behind catches up only as many days as the remaining budget allows (each settlement's cost per sim day
is measured and smoothed). A step that throws (a listener, the planner, a blueprint) is logged and retried a minute
later; it never takes the server down.

**Events** — all fired on the server thread, also during catch-up of settlements nobody is near:

| event | when |
|---|---|
| `FOUNDED` | a live settlement is founded, or a historic one adopted (after its people exist) |
| `RESIDENT_ADDED/REMOVED` | births, settlers, deaths, emigration, settlers leaving for a hamlet (`extra("sim").left_for`), and modules' own add/remove (not adoption's re-peopling) |
| `PLOT_ABANDONED` | a plot is built over, its archetype is gone, or it comes down for its rebuild |
| `CONSTRUCTION_STALLED` | a stage cannot start for want of goods (once per stall) |
| `BUILDING_COMPLETED` | the last stage of a plot is done |
| `TIER_CHANGED` | at the day boundary the population crossed a tier |
| `DAY_PASSED` | every sim day of every settlement |
| `EMBODIMENT_CHANGED` | a player came within `radius` of its bounds / left beyond `radius + hysteresis` |
| `SITE_FOUND` | a survey found a site the settlement did not have (founding, tier growth, every `resurvey_days`) — not during histories or adoption |
| `SITE_DEPLETED` | a site's stock reached nothing (once; a renewable site regrown past half is re-armed) |
| `PROSPERITY_CHANGED` | the fortunes turned: rising past `rise_above`, declining under `decline_below` (with a short why) |
| `CARAVAN` | a caravan set out, arrived, was robbed or lost (§16.6) |
| `PLOT_ABANDONED` | also: a house left standing empty in a decline (`left empty: <why>`) |

## 3. The atlas and history (§5.1)

The world is cut into **regions of 1024 × 1024**. `Atlas.plan(rx, rz)` decides, from the world seed alone:

1. whether the region was settled (`density`, 40 %);
2. **where**: a cheap survey of a 24-block grid inside the window that keeps `site_margin` (300) from the region's
   edges, then a close look at the best 32 — dry, gentle ground above the sea, flat ground round about, water within
   56–112 blocks, not too high — and the best one clear of **structures**: every structure set holding a
   `#minecraft:village` structure (20 chunks; vanilla, Expanse and modded villages) and the configured extras (Expanse's
   eight landmarks 12 chunks, its seven buildings 7, pillager outposts 10), found by arithmetic on the seed
   (`RandomSpreadStructurePlacement`) and checked against the biome there;
3. its **culture**, voted by the biomes out to ~100 blocks (a town on a taiga's shore is northern, not "beach");
4. its **age**: `max_age_days` × u^`age_skew` (0–400 days, most young);
5. its **history**: founded `age` days before day 0 and run to day 0 through the real planner and BlueprintService with
   a random seeded from (seed, region), touching only the terrain probe (`History`). A history may only build inside
   its region, so centres are at least 600 apart and writing a chunk never needs a neighbour's plan.

Plans are computed by whichever thread first needs one (a world generation worker writing a chunk, the guard, the server
adopting) — never on the world generation pool by request of another thread — cached (`plan_cache`, 256) and evicted
least-recently-used; recomputing gives the same plan. Settlement ids: historic `1 << 62 | region`, live from 1.

Determinism depends on the inputs: changing `density`, the age or site settings, or the planner/blueprints mid-world
changes plans for regions not yet generated, and a region half-generated under the old plan would get a different
town in its new chunks. Adopted settlements are unaffected (they are records).

A plot that was `DEMOLISHING` at day 0 (its rebuild under way) is not written at all; the rebuild's finished stages
stand in its place.

**Queries** (thread-safe): `atlas.plotsInChunk(cx, cz)`; `atlas.isPlanned(x, z)` (plots and roads, road polylines as
strips, with `guard_margin` 3); `SimWorld.isPlanned` adds every live settlement's plots (built ones too: a live town grows
abstractly into land nobody has generated yet, and its streets must not come up full of trees). Each plot's columns are
cached in the spatial index, so a re-index merges masks rather than walking columns.

## 4. World generation writes history (§5.1)

`hearth:settlement` (configured + placed feature in `data/hearth/worldgen/`, added to every overworld biome at
`TOP_LAYER_MODIFICATION`) writes, for the chunk being decorated, the completed stages of every historic plot touching it
through `Blueprint.forEachInChunk` — strictly inside the chunk — and records them in the chunk's ledger.

**The guard** (`FeaturePlacerMixin` + `WorldgenGuard`): every top-level placed feature tells the guard its decoration
step (read once from the biomes after biome modifications); in the surface steps (lakes, local modifications, surface
structures, springs, vegetation) a feature whose origin is a planned column at or above the probe's ground (−4) is not
placed. Ores, caves and everything underground are untouched; so are top-layer features (snow, ice, ours).

## 5. Adoption (§5.1)

When a chunk within `adopt_margin` (96) of a historic settlement's bounds loads, the plan is fetched (off-thread if it
is not cached) and the settlement adopted: a deep copy of its history, re-peopled through `HearthServices.residents()`
(one new person per historic one, keeping age, trade, home and parents), registered, `FOUNDED` fired, and the time
since day 0 caught up as ordinary simulation. Adopted regions are remembered in the index.

## 6. The chunk applier (§5.3)

Runtime construction is written into chunks when they load, under `applier.budget_ms` (3) and `max_blocks` (6000) per
tick, with `UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE | UPDATE_SUPPRESS_DROPS` (no neighbour updates: shapes are resolved),
block entities loaded from the step's tag.

- **Ledger**: each chunk carries a persistent Fabric attachment `hearth:sim/applied` — per (settlement, plot) a cursor
  below which every step of that plot lying in this chunk is in the world. World generation sets it to the historic
  progress; the applier advances it; embodied builders advance it (below). Because it lives in the chunk, blocks and
  ledger are always saved together: after a crash the ledger still describes the chunk exactly.
- **What may be written**: whole stages while a settlement is abstract; **every step as it accrues while it is
  embodied**, so nothing pops in in front of a player (`SimSettlement.applicable`).
- **Edit safety** (`StepWriter`): a step replaces only air, replaceable blocks (plants, snow layers, fluids),
  `#hearth:buildable_over` (natural ground, ores near the surface, Expanse ground, plants), non-persistent leaves, logs
  of natural trees, and blocks this plot, the plot it rebuilds or another plot of the same settlement put there earlier
  (the planner's intended overlaps: a building on its terrace, furniture in the plaza, a gatehouse over its road) (rebuilt lazily from the ledger and
  the blueprint, only when a step meets something not plainly replaceable). Never a player's blocks, never block
  entities that are not its own. A plot meeting more than 16 refusals and a quarter of its steps is abandoned (the
  planner chooses other ground).
- **Demolition**: before writing a chunk, the applier clears what a `DEMOLISHING` plot (or the plot a started rebuild
  replaces) left there — every block above its floor still exactly as placed becomes air (containers drop their
  contents; foundations stay for the rebuild; a player's changes stay) — and sets its ledger cursor to zero.

## 7. Embodiment and the construction contract (§4)

Every 20 ticks each player's nearby settlements (a 256-block cell index of bounds grown by `radius + hysteresis`) are
checked: within `radius` (128) of the bounds → embodied; it stays embodied until beyond `radius + hysteresis` (160).
`EMBODIMENT_CHANGED` fires; an embodied settlement is stepped every second, and its partial stages become writable.
`/hearth sim embody <id>` holds a settlement embodied with nobody near (for testing).

**The contract with the npc module** (both modules follow it):

1. A plot has **one progress counter**, `(plot.stage(), plot.step())`.
2. An embodied builder entity works only plots that are `BUILDING` and not `waitingForGoods()`, and places exactly the
   step `(stage, step)` of its plot — `HearthServices.blueprints().blueprint(plot.spec(), world.terrain()).step(stage,
   step, sink)` — at the moment of placement, then calls `settlement.advanceConstruction(plotId, 1)`. The sim advances
   the counter, finishes stages and the plot, and marks those steps applied in every loaded chunk that was caught up. A
   stage is never started short of goods: if the stage's goods are not in store the plot waits (`waitingForGoods()`,
   the stall fires) and the step is not counted. While a settlement is embodied the clock starts the stages its
   entity builders will need (as many plots as there are pairs of them) and takes the next stage's goods the moment a
   stage is finished, so builders see at once whether to stay.
3. Residents flagged with `setResidentEmbodied(id, true)` are left out of abstract labour and production (they still eat
   and age); clear the flag when the entity goes back to being a record, and report the work they did as an entity with
   `creditLabour(id, job, ticks)` (9000 ticks of `WORK` = one worker-day at the abstract rates; builders' work arrives
   through `advanceConstruction` instead).
4. While an embodied builder has advanced a plot within the last 10 seconds (200 ticks), abstract labour leaves that plot
   alone and goes to the others. Abstract progress on other plots of an embodied settlement is written step by step by
   the applier as it accrues.

## 8. Persistence

| what | where | when |
|---|---|---|
| each settlement | `<world>/data/hearth/settlements/<id hex>.dat` (other dimensions: a subfolder per dimension), compressed NBT | dirty ones: all at once on a flushing save (shutdown, `/save-all flush`); after an autosave a 1 ms slice per tick |
| the index | `<world>/dimensions/minecraft/overworld/data/hearth/sim/index.dat` (`SavedDataType`, saved by the game) | with the level |
| ledgers | in each chunk (attachment `hearth:sim/applied`) | with the chunk |

Settlements are encoded on the server thread (a consistent snapshot) and written by a background thread to a temporary
file that is then moved over the old one. The settlement files are the truth (all are loaded at level load; a file that
will not read is renamed `.bad`); the index summarises them (ids, names, centres, bounds, the next live id, adopted
regions, WORLD-scope data keys). A crash loses at most what changed since the last save; settlement files and chunk
ledgers may disagree by one save either way, and both directions are safe (a ledger behind its record gets the
difference written; a ledger ahead means those steps are already in the world and are not written again).

## 9. Terrain probe and cultures

- `GeneratorProbe`: `ChunkGenerator.getBaseColumn` walks one column of noise (any noise world, Expanse included) and
  gives ground and water in one pass; temperature is the biome's, cooled above the snow line. Per-thread direct-mapped
  caches (8192 columns) and per-thread caching biome resolvers: safe on any thread.
- `ExpanseProbe` (only class-loaded with Expanse; `Probes` picks it): reads the Earth `TerrainModel` — rivers carry
  their own water level and bed, fjords and the sea fill below sea level, crater lakes count as water. About 3 µs per
  column against ~1 ms for the generic probe cold. The model's surface is before the generator's 3D noise and 4 × 8
  density interpolation: on seed 20261003, 92 % of columns are within a block of the generator's ground (48 % exact).
  `probe_correction: true` measures the generator on an 8-block grid and adds the difference back (94 %, 58 % exact) for
  several times the cost; it is off by default. **Blueprints should take footings two blocks below the probe's ground.**
- `CultureMap` (`HearthServices.culture`): every Expanse biome by id, vanilla biomes tags cannot separate by id, then
  vanilla and `c:` tags, then plain temperature and rain — any modded biome gets a culture. `CultureVote` chooses a
  site's culture from the biomes round it.

## 10. Configuration — `config/hearth/sim.json`

Written with the defaults on first run; every field optional. (Delete it to pick up new defaults after an update.)

| key | default | |
|---|---|---|
| `dimensions` | `["minecraft:overworld"]` | levels Hearth runs in |
| `probe_correction` | false | §9 |
| `embody.radius` / `hysteresis` / `banner_margin` | 128 / 32 / 8 | §7, arrival banner margin round the bounds |
| `clock.step_interval` / `embodied_step_interval` | 1200 / 20 ticks | §2 |
| `clock.max_catch_up_days` | 30 | |
| `clock.budget_ms` / `min_budget_ms` / `high_mspt` | 2 / 0.25 / 40 | |
| `clock.planner_calls_per_tick` | 1 | |
| `applier.budget_ms` / `max_blocks` | 3 / 6000 | §6 |
| `atlas.enabled` / `density` / `site_margin` | true / 0.4 / 300 | §3 |
| `atlas.max_age_days` / `age_skew` | 400 / 2.2 | |
| `atlas.village_clearance` / `structure_clearance` | 20 / {expanse landmarks 12, buildings 7, outposts 10} | chunks |
| `atlas.guard_margin` / `adopt_margin` / `plan_cache` | 3 / 96 / 256 | |
| `labour.*` | 33 per hour, 16 h a day, 2 base builders, +1 per 10, 2 per plot | §2 |
| `production.per_day` / `food_*` / `coin_per_day` | table above | |
| `production.prices` / `export_share` / `import_markup` / `import_per_day` | food 0.5 … tools 6 bits / 0.1 / 1.5 / 96 | trade |
| `population.*` | 48 / 240 days, 1/60, 1/30, 0.6, 4 tents, 3 / 4 days | §2 |
| `storage.base` / `per_storage` / `per_building` | 2000 / 2500 / 120 | |
| `founding.residents` / `goods` / `treasury` | 5 / food 240, wood 900, stone 700, … / 200 | a new camp |
| `daughters.enabled` / `min_population` / `crowded_days` / `cooldown_days` | true / 30 / 5 / 40 | §2 daughter hamlets |
| `daughters.min_distance` / `max_distance` / `settlers` / `max_per_town` | 600 / 2500 / 4 / 3 | |
| `storage.by_archetype` | granary edible 6000, warehouse all 4000, sawmill logs/timber, ... | per goods, §1 |
| `resources.land.territory` | camp 96, hamlet 160, village 256, town 384, city 512 | §16.1 |
| `resources.land.core_margin` / `survey_cells` / `survey_step` / `max_sites` / `resurvey_days` | 32 / 36 / 24 / 48 / 120 | |
| `resources.chains.tool_wear` / `no_tools_factor` / `no_site_factor` / `builder_share` | 0.02 / 0.6 / 1 / 0.15 | §16.2 |
| `resources.chains.haul_range` / `haul_per_hauler` | 384 / 4 | far sites yield less without haulers |
| `resources.spoilage.enabled` / `rate` / `cool` / `salted` | true / 1 / 0.1 / 0.4 | |
| `resources.prosperity.enabled` / `capacity_limits_growth` / `decline_below` / `rise_above` | true / true / 0.35 / 0.7 | §16.4 |
| `resources.prosperity.finish_tier` / `emigration` / `abandon_slack` / `smoothing` | true / 0.25 / 6 / 0.05 | |
| `resources.caravans.enabled` / `max_distance` / `interval_days` / `cargo` | true / 3200 / 4 / 640 | §16.6 |
| `resources.caravans.speed` / `risk` / `min_value` / `margin` | 1800 blocks a day / 0.03 per 1000 blocks / 60 / 0.5 | |
| `resources.stations.enabled` / `period_days` / `up_at` / `down_at` / `gentry_at` | true / 10 / 2.2 / 0.5 / 5 | §16.7 |
| `resources.extraction.abstract_only` / `hours_per_day` / `seam_factor` | false / 9 / 6 | §16.3 |
| `resources.extraction.fell_natural_woods` / `fell_budget_ms` / `fell_away_from_players` / `max_felling_debt` | true / 0.25 / 48 / 2000 | |

## 11. Commands (operator level 2)

```
/hearth settlement list                     every live settlement, nearest first
/hearth settlement info [id]                people, trades, stores, plots, what is being built and why, how far behind
/hearth settlement tp <id>
/hearth settlement found [culture]          a live camp here (refused on water or within 200 of another town)
/hearth settlement simulate <id> <days>     fast-forward (plans as much as it needs; chunks catch up on load)
/hearth settlement adopt                    adopt this region's history now
/hearth atlas [radius]                      PNG of regions, historic sites, live towns -> <world>/hearth_atlas/
/hearth plot info                           the plot you stand on: spec, state, progress, writable cursor
/hearth sim stats                           clock, atlas, probe, world generation, applier, adoption, save timings
/hearth sim embody <id>                     hold a settlement embodied with nobody near, or let it go
/hearth settlement sites [id]               the territory's sites: kind, material, where, what is left, who works it
/hearth settlement economy [id]             prosperity, capacity, trade, trades, stations, stores by raw/processed/finished
/hearth settlement survey [id]              survey the territory again (off the server thread)
/hearth caravans                            caravans on the road: cargo, value, where, when they arrive
/hearth underground                         the realm under you and those near (Expanse Earth worlds)
```

## 12. The dev harness

Off unless asked for. Run a dedicated server with the flags in `JAVA_TOOL_OPTIONS` (Loom's `runServer` does not pass
`-D` through; use `+` for spaces in `commands`), and in `run/server.properties` a free `server-port` and
`pause-when-empty-seconds=0` (an empty dedicated server otherwise pauses after a minute):

```
cd hearth
JAVA_TOOL_OPTIONS="-Dhearth.dev.found=6 -Dhearth.dev.radius=4 -Dhearth.dev.simulate=30 -Dhearth.dev.bench=true \
  -Dhearth.dev.generate=true -Dhearth.dev.probe=2000 -Dhearth.dev.atlas=4096 -Dhearth.dev.report=true \
  -Dhearth.dev.stop=true" JAVA_HOME=C:/Users/bitto/VOID/.tools/jdk-25 ./gradlew runServer
```

| flag | does |
|---|---|
| `found=N`, `radius=R` | adopt the N historic settlements nearest spawn (searching R regions out), founding live ones if too few |
| `simulate=DAYS` | fast-forward each |
| `bench=true` | time 200 ordinary hour-long clock steps per settlement |
| `generate=true` | generate their chunks, let the applier finish, verify every written position against the record, draw `town_<name>.png` (top-down, real blocks) |
| `embody=SECONDS` | hold each (and a freshly founded camp) embodied with chunks loaded and log the step-by-step progress |
| `scale=N` | found N more live settlements on a grid, age them 30–300 days, run a minute and log tick times |
| `probe=N` | compare the probe with the generator's own heights at N columns and time the probes |
| `atlas=RADIUS` | write the atlas PNG round spawn |
| `commands=a;b` | run commands as the server at the first settlement and log the replies |
| `report=true` | per-subsystem timings (as `/hearth sim stats`, plus planner `next` timings and founding cost), settlement figures, each town's growth (layout and signature, plots by kind, rebuilds, doors on finished roads), daughter hamlets, LOD footprint samples |
| `stop=true` | shut down afterwards |
| `fillCatalogue=true` | (with a build module registered) stand-ins for every catalogue archetype the build module lacks (§15) |

`-Dhearth.dev.plan.layout=<layout>` (plan module) forces every settlement founded in the run to that layout.

**Measured** (seed 20261003, Expanse Earth terrain, the real planner over the dev catalogue, this machine; integration
runs of 2026-10-03). The stand-in buildings are several times smaller than real ones, so towns grow several times
faster than they will with the build module's blueprints; the costs below are for those oversized towns.

| | |
|---|---|
| probe (Expanse model) | ~3 µs per column on average (7.9 M columns) |
| atlas | 59 regions, 135 ms avg per region, 539 ms avg per history (3.8 s for a 783-soul city) — atlas workers only |
| world generation | 0.77 ms per chunk holding history; the guard kept 640 k features off plots |
| written vs record | 93.5–99.4 % of written positions hold their block (the rest: intended overlaps, where the later plot wins) |
| clock, hour steps | 0.005–0.52 ms avg per step for towns of 86–852 souls (0.03 ms for a 177-soul town earlier); worst single step 25–95 ms in the first planner calls of an 800-soul city after a restart (cold terrain cache), under 2 ms otherwise |
| planner `next` (live, incl. fast-forwards) | 20 009 calls, 1.9 ms avg; 55 % under 1 ms, 89 % under 5 ms, 42 over 20 ms (max 202 ms, before the deadline also bounded enumeration) |
| founding | 108 ms avg to lay out a live camp on the server thread; daughter hamlets 0.9 ms on the server thread (laid out off-thread) |
| growth over 300 days | 5 → 769 souls (camp → city, 1466 plots), 70 → 850, 193 → 837; all finished buildings' doors on a finished road or square; 29–78 rebuilds per city, 2658 blocks cleared for them; land-locked towns stop (pop = beds) and send settlers out |
| daughter hamlets | 13 founded (15 searches, 2 found nowhere) 650–2350 blocks from their parents |
| 57 settlements, 10 965 souls | the sim's tick work 0.13 ms per tick over a minute; restart: 57 loaded, none re-adopted |

**Client tour** (`gametest/IntegrationTour`, registered in the gametest `fabric.mod.json`; run alone with
`JAVA_TOOL_OPTIONS="-Dhearth.tour=integration" ./gradlew runClientGameTest`, `-Dhearth.tour.days=N` grows the town N days
first, default 60): founds a camp on gentle ground near spawn (seed 20261003), grows it with the real sim and planner,
builds it into the world, then walks a player in and photographs the arrival banner, the embodied town by day and night,
a folk close up, builders on the plot with the most work left (progress counter and "a builder placed a step N ticks
ago" logged), a merchant's conversation and the market (stockpile and treasury before and after a sale), a resident's
quest offers, the notice board (the plaza's, or one placed by the well) and a supply quest for a real stalled build,
handed in, and the build resuming. Every check is printed as `[hearth tour]`; screenshots go to
`build/run/clientGameTest/screenshots/*hearth_int_*.png`.

## 13. LOD footprints

`LodFootprints.of(level)` (null where Hearth does not run): `version(regionX, regionZ)` changes whenever anything in a
region would draw differently; `sample(x, z, int[3])` gives a finished building's {wall rgb, roof rgb, eave << 16 |
ridge} at a column (from `Blueprint.silhouette()`). Live settlements' standing plots are tracked as they finish;
histories not yet adopted come from the atlas. Thread-safe and read-only for LOD's workers. Roads are not drawn.

## 14. Client

`ArrivalPayload` (`hearth:sim/arrival`) is sent when the settlement a player stands in (bounds + `banner_margin`)
changes; the banner shows the name and "a timber-framed village of 34 souls" (culture flavour and tier from the lang
fragment), fading in and out, at most once a minute per settlement. A debug screen entry `hearth:sim` shows the current
settlement (enable it in the debug options).

## 15. Placeholders and the dev catalogue

If no BlueprintService is registered at server start (only before the build module exists), `SimModule` installs
`DevCatalogue`: every archetype of ARCHITECTURE.md §6.1 (the planner's `CatalogueDouble` infos) as a walkable stand-in —
buildings of the plot's size with a door, windows, storeys joined by a ladder, beds for the archetype's bed count,
workstations and job sites for its trades, barrels for stores, a counter for shops; the placeholder yard, well and
wheat field; a paved plaza with lamp posts and the quest module's `hearth:notice_board` (a `NOTICE_BOARD` POI);
streets on exactly the corridor the planner reserved (PLANNING.md §7: cleared above, filled below, an earth surface,
lamps); and the planner's own boxes for bridges, terraces, walls, docks and gardens. With a build module registered
and `-Dhearth.dev.fillCatalogue=true`, the build module's archetypes are used wherever it has them and stand-ins fill
the rest, so a town can be grown end to end while the architects land archetypes one by one. In a normal run only the
registered service is used. `PlaceholderPlanner` is installed only if no planner is registered (the plan module always
registers one).

Phase 3 adds stand-ins for the excavations (`PlaceholderExcavations`: quarry, clay and sand pits a bench per stage, a
propped and lit adit, a shaft, a woodlot) and the chains' workshops (smelter, kilns, glassworks, tannery, butcher,
chandler, apiary, logging camp, paper mill, dye works, salt works, shipyard, caravan yard), so the resource loop can be
grown end to end before the architects land them.

## 16. Towns thrive on the resources around them (LIVING_WORLD.md §7)

What lies round a settlement decides what it builds with, how large it grows, what it trades and what becomes of it when
the land runs out. All of it is data on the record — sites and goods are numbers, so the cost stays where it was — and
runs the same in histories (on world generation workers) and live towns. A settlement's land is saved under the
settlement data key `hearth:sim/land` (`LandState`); phase 2 saves start without it and are surveyed when they load.

### 16.1 Territory and survey (§7.1)

- **Territory**: a radius by tier (`resources.land.territory`: camp 96 … city 512 blocks), never reaching into another
  settlement's core (its bounds plus `core_margin`): where two territories meet, each stops short of the other's
  built-up heart. A history's territory keeps to its region.
- **Survey** (`land/Survey`, pure, no chunk loads): a grid over the disc of the territory (at most `survey_cells` across,
  cells at least `survey_step` apart, so about a thousand columns at most), each column read from the terrain probe (the
  Expanse model's rough view on Earth worlds) and its biome's land (`BiomeResources`), classified into what it could be:
  - **woods** — tree cover and species from the biome's vegetation features (every tree feature through any nesting of
    random selectors, weighted by its expected count per chunk and height — the reading VOID LOD uses for its forest
    blanket), so a redwood valley gives `expanse:redwood_log` woods and a birch hillside birch;
  - **stone** — steep ground, bare-rock biomes (peaks, badlands, stony shores) and high ground, with the surface rock
    from the land table (sandstone, terracotta, Expanse limestone and opal sandstone, basalt and tuff on the volcanoes);
  - **ore** — hills and mountains with rock to dig, and wherever Expanse's caverns open the rock (a realm or a great
    tunnel under the column); the grade (ore and coal per block cut) estimated by `OreModel` from the overworld's ore
    bands by height and the biome's multipliers (gold in the badlands, iron in the mountains, copper under dripstone);
  - **clay, sand** — river and lake banks, shallows, beaches, dunes; **salt** — salt flats and hot dry shores;
  - **fertile** — level or gently sloping, temperate, watered land (by the biome's fertility); **pasture** — open
    grass; **fish** — water two blocks deep or more;
  - **rarities** — the land table's specialties (amber, prismite, volcanic ash, glowcaps, peat, crab, venison, mammoth
    wool, bamboo, cocoa, honeycomb, kelp...), thinned in clumps over their biomes; and under Expanse Earth worlds every
    realm under the territory gives workings in its walls and its own rarities (`expanse:realm_<character>` entries).
- Cells of a kind and material join into patches of at most `max_cells` (a vast forest becomes several woods), each a
  **site** with capacity (density × the kind's `per_cell`), regrowth (`regen`: forests, pasture, fish, clay banks; never
  stone or ore), the primary trade's yields scaled by density, its material, its grade. The richest and nearest are kept
  (`max_sites` per kind and in all). Deterministic: the same land gives the same sites.
- **When**: at founding (a history at once; the live world on the `hearth-survey` thread, merged on the server thread),
  when the tier grows the territory, every `resurvey_days`, on `/hearth settlement survey`. A site found again keeps its
  id, what was taken from it and its excavation; a new one fires `SITE_FOUND`.
- `Settlement.territoryRadius()` and `Settlement.sites()` (the api's `ResourceSite` views; until the api has a
  `material` field, `speciality` carries every site's material — see requests/p3-sim.md §1).

### 16.2 Goods and production chains (§7.4, §7.5)

All 29 `Goods` are stocked (arrays sized to the enum). Each step (`settlement/Production`), per **crew** — the working
adults of one trade at one kind of workplace, resolved once a day:

1. **Recipes** (`hearth_sim/recipes/*.json`, below): the scarcest output first; a recipe runs while its outputs are
   under `target` of the store room, as far as inputs allow, for at most `share` of the day. Logs → timber at the sawmill
   or carpenter, logs → charcoal, stone → dressed stone, clay + fuel → brick, sand + fuel → glass, ore + fuel → metal at
   the smelter (or the smithy's bloomery), metal + fuel → tools, grain → flour at the mills, flour → bread, meat → food
   (salt helps), wool → cloth, wax or tallow → candles, logs or rags → paper → books. Builders spend
   `builder_share` of their day hewing timber and dressing stone for their own work; odd-job labourers hand-hew and spin.
2. **Excavations** of the crew's trade (§16.3).
3. **Gathering** (`hearth_sim/gathering/<job>.json`) with what is left: the day shared over the kinds of site the trade
   works (an odd-job labourer gets every kind's yields at once), each kind over its best three sites by richness and
   nearness; yield = the trade's yields × the site's density × its richness (the square root of how full it is; a
   renewable site never gives under 0.35) × the haul (sites beyond half of `haul_range` give less, unless there are
   enough haulers); the site loses what was taken. No site of its kinds: the trade's `no_site` yields.
- **Tools** wear by the worker-day (`tool_wear` or the data's); short of tools, every trade but odd jobs manages
  `no_tools_factor` of a day. Passing traders sell a toolless town a cartload a day while its coin lasts.
- **Food**: folk eat prepared food; without it, what the land gives as it is (fish, meat, fruit, flour, grain at the
  model's `edible` values — bread from the mill and bakery is worth more than grain eaten as porridge). Hunger counts
  when nothing edible is left. A starving town with coin buys a few days' food.
- **Spoilage** (daily): each perishable good loses its share (`model.json` `perishable`) outside cool stores
  (granaries, cellars: the model's `cool` archetypes, up to their room), `cool` of it inside; meat and fish keep while
  there is salt (which it uses).
- **Construction bills** in whatever goods the blueprints report (timber, dressed stone, brick, glass, metal, candles...);
  a stalled stage imports what it lacks (§2).

### 16.3 Extraction is construction run backwards (§7.3, §8.5)

An excavation (`hearth:extract/*`: quarry pit, mine, shaft, clay pit, sand pit, plantation) is a plot like any other —
the single progress counter, the applier and its edit safety, abstract and embodied progress — with three differences:

- its **labour** comes from its own trade (`model.json` `extraction`: quarrier, miner, clay digger, forester) at the
  builders' rate per hour for `hours_per_day`, never from builders; it employs them from the day it is staked out (its
  job sites count while PLANNED/BUILDING); a stage waiting for its goods (props, rails, lanterns) sends them back to
  surface gathering meanwhile;
- each stage's **cuts** (`PlotFacts.cuts`: air steps below the probe's ground; a woodlot's at any height) are its
  **yield**, credited as the stage finishes, abstract or embodied alike: the extraction's goods per cut (stone, clay,
  sand, logs) plus ore and coal at the site's grade × `seam_factor` for mines; the site (the plot's `site` param, else
  the nearest of its kind) loses the cut rock. Never damage;
- `resources.extraction.abstract_only` writes nothing of an excavation into the world (the loop runs as numbers).

**Natural woods** without a woodlot: what abstract foresters take is owed to the world (`SimSite.fellingDebt`, capped)
and felled where the wood's chunks are loaded (`world/Woodcutter`, `fell_budget_ms` a tick, a few random columns of a
wood that owes): a natural tree (logs under decaying leaves, on dirt) is taken down whole, top to bottom, leaves and all,
and a sapling of its kind goes in at the stump. Never within `fell_away_from_players` of a player (embodied foresters
fell in view), never on planned or built ground, never by anything a player built (any block round the trunk that is
not natural ground, plants, trees or water), never with `abstract_only`. An embodied forester's credited work is not
owed again.

### 16.4 Carrying capacity, prosperity, boom and bust (§7.2)

- **Carrying capacity** (daily): what the food sites regrow a day, through the trade they are for, in food (a farm's
  grain and fruit eaten), plus food the town's trade earnings would buy, over what an adult eats (× 1.15). With
  `capacity_limits_growth`, settlers stop and births fall to a quarter past it; past it a crowded town sends settlers out.
- **Prosperity** (0–1, daily, smoothed by `smoothing`): 0.2 × how full the sites in use are + 0.15 × days of food (30 is
  full) + 0.1 × the share in work + 0.25 × trade per head (exports less imports) + 0.2 × coin per head + 0.1 × housing.
  A town that merely feeds itself sits near 0.55.
- **Turns** fire `PROSPERITY_CHANGED` with a why: above `rise_above` "trade is good", "the land round about is rich"...;
  under `decline_below` "the iron ore at 412, -96 is worked out", "food is short", "the trade has gone"... A boom draws
  settlers (×1.4) and raises the finish tier of new buildings by one (`finish_tier`; roads as planned); a decline keeps
  settlers away (×0.2), halves births, lowers the finish, and households leave (`emigration` × how deep; a household is
  everyone who shares a home). With more empty beds than `abandon_slack`, a house at the edge of town is left standing
  empty: `ABANDONED` (its blocks stay; the build module's decay look applies to it), listed in `LandState.deserted`,
  `PLOT_ABANDONED` "left empty: <why>". When folk need beds again, they move back in before anything new is built.

### 16.5 Daughter hamlets at the resources the parent lacks (§7.2)

A crowded town (no free bed, or past its capacity) sends settlers out as before (§2), but the search now values what the
parent lacks — farmland, woods, stone, ore, fishing, clay — read with a nine-column survey at each candidate (+6 a kind),
so a mountain town founds a farming hamlet on the plain and a plain town a mining hamlet by the ore ("settlers from
Ashbrook, gone for the ore it lacked"). Hamlets ship to their parent first (caravans favour kin ×1.5).

### 16.6 Caravans (§7.6)

`trade/Caravans` (world data `hearth:sim/caravans`): once a sim day each town may, every `interval_days`, send a caravan
of up to `cargo` units of its surplus (beyond a third of its store room, its food reserve and what its stalled builds
wait for) to the town within `max_distance` that wants it most — value to the buyer (its price: dearer the shorter it is,
up to three times), nearness, kin. Travel time from the distance and the road's roughness (water to ford, climbing:
sampled along the way) at `speed`; the risk (`risk` per 1000 blocks × roughness, less with guards) decides at departure
whether it will be lost. On arrival the buyer's stores fill and its treasury pays the seller (between the two prices,
`margin`); trade feeds both towns' prosperity. `CARAVAN` fires DEPARTED, ARRIVED, ROBBED or LOST. The npc module can
place a caravan on its road (`Caravans.positionAt`), the quest module escort it (`escort`: it arrives) or rob it
(`rob`: cargo gone, ROBBED) — see requests/p3-sim.md §3 for the api asked for. Arrivals are a queue checked by its head
each tick.

### 16.7 Stations by the purse (§8.1)

Each household (residents sharing a home) is looked at every `period_days`, staggered. Its means: the purse the economy
module keeps per resident (`Resident.extra("economy").purse`, bits) against what its station's households usually keep;
without one, what its trades earn × (0.5 + prosperity), against their usual (homeless: less). At `up_at` times the usual
it moves up a station (poor → peasant → artisan → merchant; a merchant household at `gentry_at` in a town or city →
gentry), under `down_at` down one, but only after two looks in a row agree — slow, as the design asks. When the
household's trades change (a labourer becomes a smith), it takes the station they bring at once. Callings stay
(clergy, scholars, officials, the lord). The planner reads `Resident.station` for class-aware housing.

### 16.8 Settlement reputation

`Settlement.reputation/addReputation` stay stored here, but while a `StandingService` is registered it is the authority:
reads return its standing, writes become `deed(... "hearth:reputation" ...)`. Calls from inside the standing service's
own `standing`/`deed` reach the raw numbers (no recursion); `SimSettlement.rawReputation/rawAddReputation` expose them.

### 16.9 The underground probe

`HearthWorld.underground()` on Expanse Earth worlds is `terrain/ExpanseUnderground` (class-loaded only with Expanse):
realms from the cavern model's realm cells (one per 640 blocks at most) — centre, radius, the floor of its plateau, the
vault over its centre, its character (`wilds`, `lush`, `dripstone`, `crystal`, `ember`, `mere`, `frozen`, `roots`,
`fossil`, `tidal`, `amber`, `brimstone`) — and for any column the floor nearest a height (a realm's floor or lake, a
great tunnel, a river tunnel's water, a passage or chamber) and the ceiling over it. `/hearth underground` shows it.

### 16.10 The data (`data/<ns>/hearth_sim/`)

Any data pack can add or override (same file id replaces ours); reloaded with `/reload`.

```
recipes/<name>.json    { "recipes": [ { "job": "smelter", "workplace": "hearth:work/smelter",
                          "inputs": {"ore": 6, "fuel": 3}, "outputs": {"metal": 4}, "labour": 1.0,
                          "tools": -1, "share": 1.0, "target": 0.9, "priority": 0 } ] }     (or one recipe per file)
                        labour: worker-days a batch; workplace: only workers there; no job: every worker there
gathering/<job>.json   { "job": "forester", "split": true, "tools": 0.04,
                          "sites": [ { "kind": "woods", "yields": {"logs": 120, "fuel": 4}, "share": 1, "wear": -1 } ],
                          "no_site": {"logs": 20} }
sites/<kind>.json      { "kind": "woods", "per_cell": 1800, "regen": 0.006, "primary": "forester",
                          "max_cells": 16, "max_sites": 8, "material": "minecraft:oak_log" }
land/<name>.json       { "biomes": { "expanse:jade_karst": { "rock": "expanse:limestone", "rocky": true, "fertility": 0.5,
                          "specialties": [ { "item": "minecraft:bamboo", "goods": "specialty", "job": "forester",
                          "yield": 30, "density": 0.5, "regen": 0.05 } ] } },
                          "tags": { "minecraft:is_badlands": { "ore": {"gold": 4}, ... } } }
                        also: sand, clay, wood, trees, pasture, salt; ore multipliers coal/iron/copper/gold
model.json             { "edible": {...}, "perishable": {...}, "cool": [archetypes], "extraction":
                          { "extract/mine": { "job": "miner", "kind": "ore", "yields": {"stone": 0.6}, "any_height": false } } }
```

`SimDataTest` checks that every input of every recipe is made or gathered somewhere.
