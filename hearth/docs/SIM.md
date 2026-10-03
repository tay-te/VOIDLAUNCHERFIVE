# VOID Hearth — the sim module

The sim module is the engine under every Hearth settlement: the records, the clock that grows them while nobody is
near, the atlas that gives the world a history from its seed, the world generation that writes that history, the chunk
applier that writes what was built since, persistence, embodiment, commands and the dev harness. It implements
ARCHITECTURE.md §4, §5, §11 and §12. Code: `dev.voidmc.hearth.sim` (+ `client.sim`, `mixin.sim`).

```
sim/
  SimModule, SimServer, SimConfig, LodFootprints
  settlement/  SimSettlement, SimPlot, SimStockpile, DataMap, SettlementClock, PlotFacts, Cursor, Names, PlainResidents
  model/       SimContext (what the model reaches outside itself), SimRules (the config as flat arrays)
  world/       SimWorld (HearthWorld + SimContext), SettlementStore, SimIndex, SpatialIndex, Hamlets
  atlas/       Atlas, RegionPlan, SiteFinder, History, StructureAvoidance, PlotColumns
  terrain/     GeneratorProbe, ExpanseProbe, Probes, CultureMap, CultureVote
  gen/         SettlementFeature (hearth:settlement), WorldgenGuard   + mixin/sim/FeaturePlacerMixin
  apply/       ChunkApplier, StepWriter, AppliedLedger, ChunkLedgers
  net/         ArrivalPayload
  command/     SimCommands, AtlasMap, Report
  dev/         DevHarness, DevCatalogue, PlaceholderBlueprints, PlaceholderBlueprint, PlaceholderPlanner
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
- **Stockpile capacity** = `storage.base` + `per_storage` × standing STORAGE buildings + `per_building` × others. A stage
  needing more than the capacity is clamped to it (carts bring the rest straight to site).
- **Tiers** follow population (`SettlementTier.forPopulation`) with a 20 % margin before falling back.

## 2. The clock

`SettlementClock.advance(settlement, targetTick)` runs a settlement forward through the same model whether it is live
(`SimWorld` is its `SimContext`) or a history on a world generation thread (`History.Context`). Sim time is the overworld
clock (1000 ticks an hour, 24000 a day; sleeping moves it, so towns grow while you sleep) plus any fast-forward offset.

Time is cut into **slices that never cross a day boundary**. Each slice integrates goods and labour; each day boundary
runs the day's business. A step catches up at most `max_catch_up_days` (30); longer absences take several steps.

**Goods** (per slice, by job; embodied residents are left out — their entities do the work):

| | per worker per day (defaults) |
|---|---|
| none (gatherers) | food 2.5, wood 12, stone 10, clay 4, fuel 1 |
| builder | wood 6, stone 6 (and their labour) |
| farmer / fisher / hunter / herder | food 20 / 14 / 10 / 8 (+cloth 3 / 14) |
| forester / quarrier | wood 200, fuel 12 / stone 240, clay 24, metal 6 |
| carpenter, mason, smith, brewer, ... | convert inputs (negative) to outputs; output shrinks with short inputs |

Food eaten: adult 2, child 1, elder 1.5 a day. Fractions carry over between slices. Coin per worker per day by job
(1 for most, merchant 8, minter 20, ...) — only while no economy module is registered: with one, its daily books
(taxes, passing trade, wages, upkeep on `DAY_PASSED`) are the treasury's income and the clock leaves its own coin out
(`SimContext.economyHandlesWages`), so a town is not paid twice. Histories fire no events and keep the clock's coin;
adoption trims an adopted town's treasury to 60 bits a head when the economy runs. **Trade** keeps a town from stalling forever on goods it cannot make: what the
stores cannot hold is sold to passing traders at `export_share` (0.1) of its price; a stalled stage buys what it lacks
(`import_per_day`, 96 units of each) at `import_markup` (1.5) × price, with coin first, then by bartering whatever
overflows the stores at half value. Deliberately slow and dear: a player bringing the goods is faster.

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
