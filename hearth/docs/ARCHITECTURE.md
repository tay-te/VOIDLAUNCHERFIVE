# VOID Hearth — architecture and module contract

VOID Hearth makes the world lived in. Settlements are founded, grow and build while you play, whether or not you are
there to watch: folk with names, homes, trades and routines raise beautiful, hand-built-looking towns (sprawling
farmsteads or dense, tall hill towns), coin changes hands at markets, and the quests you are offered come from what a
town actually needs. It is a sibling of **VOID Expanse** (biomes, Earth terrain, structures) and **VOID LOD** (far
terrain), and it is built to complement both.

```
Minecraft 26.3 · Fabric Loader ≥ 0.19.5 · Fabric API 0.161.0+26.3 · Java 25 · optional: VOID Expanse, VOID LOD
```

This document is the contract between the modules. **Read it all before you write code.** Code against
`dev.voidmc.hearth.api` for anything another module owns; never edit another module's package.

---

## 1. Pillars

1. **Alive.** Folk keep a day: wake, eat, work, gather, go home, sleep. Builders really build, block by block, with
   scaffolding. Farmers farm. Couriers walk the roads between towns. Children play. Towns react to what happens.
2. **Growing while you are away.** A settlement nobody is near is still simulated — cheaply, as numbers — and what it
   built is in the world when you come back.
3. **Beautiful.** Every building looks like a skilled player built it by hand for that place: depth, silhouette,
   regional materials, furnished interiors, gardens. Layouts follow the land: switchbacks and terraces up slopes,
   stilts over water, bridges, towers, walkways. **Verticality matters**: dense towns grow up, not just out.
4. **Minecraft's own money.** Physical coins struck from Minecraft metals at settlement mints, spent at market stalls.
   No floating numbers on a HUD as the primary way to pay.
5. **Quests that mean something.** A town short of stone for its bridge asks you for stone. A courier needs an escort.
   A scholar wants a page from the Expanse landmark three valleys over. Finishing them visibly changes the town.
6. **Server-first and scalable.** All decisions on the server. Work per tick bounded by budgets, not by world size.
   A server with 50 players and 200 settlements must hold 20 TPS.

---

## 2. Working in this repo

- Build: from `hearth/`, `JAVA_HOME=C:/Users/bitto/VOID/.tools/jdk-25 ./gradlew build` (≈30 s warm). Keep it green.
- **Looking up the game's code** (26.3 is unobfuscated; names are Mojang's; do not trust memory of older versions):
  - Minecraft, decompiled: `C:/Users/bitto/VOID/.tools/mc-src/common/net/minecraft/...` (server + common) and
    `C:/Users/bitto/VOID/.tools/mc-src/client/net/minecraft/...` (client only). Grep these.
  - Fabric API, decompiled: `C:/Users/bitto/VOID/.tools/fabric-src/net/fabricmc/fabric/api/...`
  - Vanilla assets (blockstates, models, textures, loot tables): inside
    `C:/Users/bitto/.gradle/caches/fabric-loom/26.3/minecraft-client.jar` (unzip what you need into your scratch dir).
  - Working 26.3 idioms in this repo: `expanse/` (blocks, items, entities with JSON models and renderers, worldgen
    features, structures, biome modifications, commands, dev harness, client game tests) and `lod/` (client rendering,
    worker threads, terrain sampling from the generator, mixins into rendering, JUnit tests).
- Tabs, concise comments that say *why*, small classes — match `expanse/`.
- Prefer mixin `@Accessor`/`@Invoker` (in your module's mixin config) over access wideners/class tweakers.
- JUnit 5 tests live in `src/test/java` (plain JVM, no game bootstrap unless you call `SharedConstants.tryDetectVersion();
  Bootstrap.bootStrap();` yourself — that works for vanilla registries).
- Shared test doubles in `api/testing/` (main source set, so tests, previews and dev commands can all use them):
  `SyntheticTerrain` (flat, slope, steep, hills, cliff, river, coast, lake — the same ground for everyone's previews)
  and `SimpleSettlement` (an in-memory `Settlement` with plots, residents, stockpile, treasury, data). Use them instead
  of writing your own fakes; the real implementations come from the sim module.

---

## 3. Modules and ownership

| Module | Owner agent | Main package | Client package | Mixins | Lang fragment |
|---|---|---|---|---|---|
| api (contract) | coordinator | `dev.voidmc.hearth.api` | — | — | — |
| **sim** (core) | `hearth-engineer` "core" | `dev.voidmc.hearth.sim` (+ `sim.*` subpackages) | `client.sim` | `hearth-sim[.client].mixins.json` → `mixin.sim` / `client.mixin.sim` | `core.json` |
| **build** | `hearth-architect` "build" | `dev.voidmc.hearth.build` | — | — | `build.json` |
| **plan** | `hearth-architect` "planner" | `dev.voidmc.hearth.plan` | — | — | `plan.json` |
| **npc** | `hearth-engineer` "npc" | `dev.voidmc.hearth.npc` | `client.npc` | `hearth-npc[.client]` | `npc.json` |
| **economy** | `hearth-engineer` "economy" | `dev.voidmc.hearth.economy` | `client.economy` | `hearth-economy[.client]` | `economy.json` |
| **quest** | `hearth-engineer` "quest" | `dev.voidmc.hearth.quest` | `client.quest` | `hearth-quest[.client]` | `quest.json` |

| **standing** (phase 3) | — | `dev.voidmc.hearth.standing` | `client.standing` | `hearth-standing[.client]` | `standing.json` |
| **law** (phase 3) | — | `dev.voidmc.hearth.law` | `client.law` | `hearth-law[.client]` | `law.json` |
| **repair** (phase 3) | — | `dev.voidmc.hearth.repair` | `client.repair` | `hearth-repair[.client]` | `repair.json` |
| **social** (phase 3) | — | `dev.voidmc.hearth.social` | `client.social` | `hearth-social[.client]` | `social.json` |
| **map** (partner) | — | `dev.voidmc.hearth.map` | `client.map` (+ `src/atlas`: `client.map.atlas`) | — | `map.json` |

Phase 3 (the living world: light, repairs, reputation, law and stealth, the resource economy, more architecture) is
designed in **`docs/LIVING_WORLD.md`**, which extends this contract; its §8.5 lists the new archetype ids and params.

Rules that keep parallel work mergeable:

- Each module's `XxxModule.init()` (and `XxxClient.init()`) is already called from `Hearth` / `HearthClient` in the
  order shown there. Put your registration behind it. Do not edit `Hearth.java`, `HearthClient.java`, `HearthTab.java`,
  `build.gradle`, `fabric.mod.json` or anything in `api/`.
- Need something from another module that the api does not give you? Do **not** edit `api/`. Write what you need in
  `hearth/docs/requests/<your-module>.md` (what, why, proposed signature) and work around it inside your own package
  (an adapter, a default). The coordinator merges requests into the api between phases.
- Resources: your files under `assets/hearth/...` and `data/hearth/...` use your module as a prefix or subfolder where
  the format allows (`data/hearth/hearth_style/...` for build, `textures/item/coin_*.png` for economy, etc.). Lang keys
  go in **your** fragment `assets/hearth/lang/en_us/<module>.json` (merged at build; duplicate keys with different
  text fail the build). Keys: `hearth.<module>.*`, `item.hearth.*`, `block.hearth.*`, `entity.hearth.*`.
- Creative tab: `HearthTab.add(item)` from your init.
- Commands: register your own subtree under `/hearth <yourthing>` with `CommandRegistrationCallback` — Brigadier merges
  literal nodes, so every module can add children to `/hearth` independently.
- Settings: `HearthConfig.load("<module>", CODEC, DEFAULTS)` → `config/hearth/<module>.json`. Every field
  `optionalFieldOf(name, default)`.
- Saved state: per-settlement and per-world module state through `HearthDataKey.register(Hearth.id("<module>/<name>"),
  scope, codec, factory)`; per-player state through Fabric's data attachment API (`AttachmentRegistry`) under
  `hearth:<module>/<name>`.
- Networking: your own payload types (`CustomPacketPayload` + `PayloadTypeRegistry`), ids `hearth:<module>/<name>`.
- Dev switches: system properties `hearth.dev.<module>.*`; your own `/hearth` debug subcommands.

### Services (`api/HearthServices`)

```
blueprints  BlueprintService   build    required    blueprint(spec, terrain), archetypes, style(culture)
planner     SettlementPlanner  plan     required    found(ctx), next(settlement, terrain, random)
worlds      HearthWorld        sim      required    settlements of a level, terrain probe, day, world data, caravans, written()
culture     biome → Culture    sim      required
residents   ResidentFactory    sim default; npc replaces it (names, looks, personality)
economy     EconomyService     economy  optional    balance/charge/pay/format/price; purses, services, fines, bribes, funded plots, owners
quests      QuestService       quest    optional    NPC quest markers
npc         NpcService         npc      optional    folk lookups, perception, FolkBehaviours other modules add, escorts, traits, wake
standing    StandingService    standing optional    reputation layers, deeds, gossip, charisma, speech checks, renown
law         LawService         law      optional    ownership, stolen goods, wanted status and posters, crime reports, pardons
repairs     RepairService      repair   optional    damage against blueprints, repair claims
```

Plus `DialogueProvider.register(...)` (economy and quest add conversation options; npc owns the screen) and
`SettlementEvents` (sim fires; everyone may listen). Phase 3 adds the law's `CRIME_COMMITTED`, standing's `STANDING_CHANGED`,
the sim's `SITE_DEPLETED`/`SITE_FOUND`/`PROSPERITY_CHANGED`/`CARAVAN` (with the caravan id), the repair module's `DAMAGED`
(with the player), `FIRE_STARTED`, `FIRE_OUT`, `REPAIRED` and `REPAIR_STALLED`, and the npc module's `ESCORT_ENDED`.
The living world's integration points are listed in `docs/requests/p3-*.md`, each marked with how it was resolved.

---

## 4. The simulation: three levels of fidelity

The defining idea, and the reason this scales: **a settlement is data first.** Entities and block placement are views of
that data, made only where a player can see them, exactly as VOID LOD makes far terrain from the generator rather than
from chunks.

| Level | When | What runs | Cost |
|---|---|---|---|
| **Abstract** | no player within `embodyRadius` of the settlement's bounds | The sim clock integrates elapsed time: production and consumption by job, population (births, deaths, settlers), construction labour against plots, treasury, market drift, quest board refresh. Completed construction stages are recorded, not placed. | µs per settlement per step; round-robin under a per-tick time budget |
| **Applied** | a chunk holding recorded-but-unplaced construction is loaded (no player needed) | The chunk applier writes those blueprint steps into the chunk, a budgeted number per tick, no neighbour updates (shapes are pre-resolved). | bounded blocks/tick |
| **Embodied** | a player within `embodyRadius` (leave at `+32` hysteresis) | Residents near players exist as entities (`hearth:folk`) following their routine; builders place steps one at a time; farmers work fields; merchants stand at stalls. The settlement still runs its clock, at real time. | bounded entities per settlement and per server |

Default numbers (all in `config/hearth/sim.json`, `npc.json`):

- `embodyRadius` 128 blocks (from the settlement's bounds); entities spawn within 96 blocks of a player in loaded,
  entity-ticking chunks and are taken back into their record beyond 112.
- At most 48 embodied folk per settlement, 400 per server (nearest to players first).
- Abstract sim: every live settlement is stepped at least every 1200 ticks, catching up whatever time passed (capped at
  30 sim days per step; longer absences are caught up over several steps). Budget 2 ms per server tick total, shrinking
  when MSPT is high.
- Chunk applier: at most 3 ms and 6000 blocks per tick.
- Sim time follows the overworld's day time: a sim hour is 1000 ticks, a day 24000.

**Labour and building pace.** One work unit = one builder placing one ordinary block. An embodied builder places about
one step per 30 ticks (and walks, fetches, climbs scaffolding), which is ~33 units per sim hour; the abstract rate is
the same so the two agree. A 2,500-block house with two builders takes about two sim days (40 minutes of play). A town
visibly grows over a session.

**Folk are records.** A `Resident` (api) is the person. Entities are never saved with chunks (`hearth:folk` is not
persisted; the NPC module writes the entity's state back into the record when it goes away and on server stop).
Births, deaths and arrivals are decided by the sim (abstract) — the NPC module never creates people on its own, it asks
the sim (`Settlement.addResident`) so the record stays authoritative.

---

## 5. A settlement's life

### 5.1 Where settlements come from

- **History, from the seed.** The world is divided into **regions of 1024 × 1024 blocks**. For each region the sim
  module's *atlas* decides deterministically from the world seed whether a settlement was founded there, where (the
  best site by the terrain probe: water nearby, gentle ground, not on a structure — avoid every structure set tagged
  `#minecraft:village` and Expanse's landmarks via `ChunkGeneratorStructureState.hasStructureChunkInRange`), its
  culture (`HearthServices.culture(biome)`), and its **age** (0–400 sim days, skewed so most are hamlets and villages,
  a few are towns, rarely a city). Default density: about 40 % of regions, never two settlement centres within 600
  blocks.
- **Pre-history.** The atlas then runs the settlement's growth for its age — planner proposals, abstract construction,
  population — with a random seeded from (world seed, region), producing its plots and how far each got. This is
  deterministic and touches only the terrain probe, so it can run on a world generation worker and give the same town
  every time. Cache per region; budget ~100–300 ms per town, once.
- **World generation writes the history.** A feature `hearth:settlement` (step `TOP_LAYER_MODIFICATION`, every
  overworld biome) asks the atlas which plots touch the chunk being generated and writes their completed stages with
  `Blueprint.forEachInChunk` — inside that chunk only, so order does not matter. A worldgen guard keeps trees, boulders
  and other surface features out of planned plots and roads (the atlas answers "is this column planned?" cheaply).
- **Adoption.** The first time a chunk near a historic settlement loads, the sim *adopts* it: it creates the live
  record from the plan (residents via `ResidentFactory`, filling the beds and job sites; stockpile; treasury), fires
  `SettlementEvents.FOUNDED`, and from then on the record evolves. The time the world has existed is caught up as
  ordinary abstract simulation, so a settlement found late in a world has grown more.
- **New settlements while you play.** A settlement that outgrows its land sends settlers to found a hamlet at a good
  site 600–2500 blocks away along a road. These are live from birth and are built only by the runtime path (below).
  Quests can help (a scout quest picks the site).

### 5.2 Growth

When builders are free, the sim asks `planner.next(settlement, terrain, random)` for a `GrowthProject`: a new plot
(spec), or the rebuild of an existing plot (`replaces` = its id: a cottage becoming a townhouse — that is how a village
grows **up**), with a reason. The sim adds the plot as `PLANNED` and construction begins when goods allow.

Needs the planner weighs (it reads them from the settlement): housing (residents vs. beds), each job's workplaces,
storage, food production, the tier's civic buildings (chapel at village, market and inn at village, town hall and walls
at town, cathedral/landmark at city), roads to connect everything, and beauty (plazas, gardens, wells, lamps) once needs
are met.

### 5.3 Construction

A plot is built stage by stage (`StageKind` order). Starting a stage takes its goods from the stockpile
(`Blueprint.goods(stage)`); if short, the plot stalls and `CONSTRUCTION_STALLED` fires (quests turn that into a supply
request). Labour (`Blueprint.work(stage)`) then accrues from builders: abstractly as numbers, or embodied as real
placements (`Settlement.advanceConstruction`). When the last stage completes the plot is `BUILT`, its beds and job sites
count, and `BUILDING_COMPLETED` fires.

Where the result goes:

- **Abstract** completion is recorded per (plot, stage). The chunk applier writes recorded stages into chunks when they
  load, and remembers per chunk what it has applied (so it never re-applies over a player's later edits).
- **Embodied** builders place the steps themselves; those steps count as applied.
- **Intended overlaps.** The planner overlaps plots on purpose (PLANNING.md §4): a building on a terrace of the
  same footprint, dwellings `stacked_on` another, plaza furniture inside its plaza, a gatehouse `over_road`, towers on
  walls, walkways overhead, rebuilds (`replaces`). These are not "built over"; appliers write both.
- **Edits.** Runtime application never overwrites blocks a player placed or block entities: it replaces only air,
  plants, snow, natural terrain (`#hearth:buildable_over` block tag, sim module data) and blocks of earlier stages of
  the same plot. A plot whose footprint the player has built over is abandoned and re-planned elsewhere.

---

## 6. Blueprints, styles and archetypes (build module)

A blueprint is a **pure function** of `(PlotSpec, TerrainProbe)` → `Blueprint` (api). Determinism is what lets a town of
a hundred buildings be stored as a hundred specs and regenerated on any thread at any time. The build module caches
blueprints (bounded LRU, keyed by spec) because the applier and builders ask repeatedly.

- **Terrain.** Generators read the natural ground under and around the footprint from the probe and reconcile it with
  the spec's floor level: footings and foundations down to the ground (never floating), cut and fill, retaining walls,
  stilts and piles over water, a stair up to a door that would otherwise be high. Hillside archetypes use the slope
  (a lower storey opening downhill) instead of flattening it.
- **Shapes resolved.** Steps carry final block states: stair corners, fence/wall/pane connections, door halves, bed
  halves, double plants. Appliers place without neighbour updates.
- **Styles** are data (`data/hearth/hearth_style/<culture>.json`): materials per role (foundation, wall, frame, beam,
  floor, roof, roof trim, window, door, fence, path, light, accent, garden plants...) with 2–4 weighted blocks each and
  per-role fallbacks (an Expanse block id falls back to a vanilla block when Expanse is absent), plus regional habits
  (roof form and pitch, overhangs, jetties, stilts, flat roofs and parapets, domes, round plans, courtyards, colour).
  One style per culture at least; tier changes finish (rough timber at 0, dressed stone and glass at 4).
- **Archetypes** are generators (`hearth:<category>/<name>`) with an `ArchetypeInfo` the planner selects by. Each
  documents its `params` keys in its class Javadoc.
- **Materials bill.** Every step maps to goods (planks/logs → WOOD, stone/cobble/bricks of stone → STONE, bricks,
  terracotta, mud → CLAY, glass → GLASS, iron/copper/lanterns/chains → METAL, wool/carpet/beds/banners → CLOTH...,
  air → nothing); `work` counts non-air steps (heavier blocks may count more).
- **Previews.** `./gradlew blueprintPreview` and `./gradlew settlementPreview` (gametest source set, already wired in
  `build.gradle`: main classes `dev.voidmc.hearth.gametest.BlueprintPreview` / `SettlementPreview`) render isometric and
  plan PNGs with texture-average colours from the client jar, on synthetic terrains (`flat`, `slope`, `hills`, `cliff`,
  `river`, `coast`). Architects look at their output with the Read tool and iterate. A dedicated-server dev harness and
  a client game test give in-game screenshots for the final word.

### 6.1 The archetype catalogue (ids the planner and architects share)

The planner may only rely on archetypes in this list; architects implement them (and may add more, announcing them in
`ArchetypeInfo` so the planner discovers them). Sizes are frontage × depth ranges in blocks.

**Housing** (`hearth:house/...`)
| id | for | notes |
|---|---|---|
| `hut` | camp, hamlet | one room, 5–7 × 5–7; tents/lean-tos in some cultures |
| `cottage` | all | 1–1½ storeys, 7–11 × 7–11, garden |
| `farmhouse` | outskirts | house + yard/barn corner, 11–17 × 11–17 |
| `longhouse` | northern, tundra, moorland | long hall, 7–9 × 13–21 |
| `rowhouse` | village+ | 2 storeys, ATTACHABLE, 5–7 × 8–12 |
| `townhouse` | town+ | 3–4 storeys, ATTACHABLE, VERTICAL, jettied upper floors, 5–8 × 8–13 |
| `tenement` | city | 4–6 storeys, VERTICAL, ATTACHABLE, shared stair, 8–12 × 10–14 |
| `shophouse` | village+ | shop on the ground floor, home above (MERCHANT), ATTACHABLE |
| `manor` | town+ | grand house in grounds, 15–25 × 15–25 |
| `tower_house` | alpine, moorland, mediterranean, karst | narrow and tall, VERTICAL, 5–7 × 5–7 |
| `stilt_house` | bayou, tropical, maritime | ON_WATER |
| `cliff_dwelling` | mesa, karst, alpine | HILLSIDE, STACKABLE: terraced rooms stepping up a slope |
| `round_house` | steppe, tropical | round plan, cone roof |
| `courtyard_house` | desert, mediterranean, karst | rooms round an open court |

**Workplaces** (`hearth:work/...`): `builders_yard` (BUILDER — the first workplace any settlement builds), `smithy`
(SMITH), `carpentry` (CARPENTER), `masonry` (MASON), `bakery` (BAKER), `brewery` (BREWER), `tailor` (TAILOR),
`sawmill` (FORESTER, CARPENTER, OUTSKIRTS; a water wheel where water runs behind), `quarry` (QUARRIER, OUTSKIRTS), `fishery` (FISHER, WATERFRONT),
`windmill` (FARMER/BAKER, VERTICAL), `watermill` (FARMER/BAKER, WATERFRONT), `barn` (HERDER), `hunters_lodge` (HUNTER).

**Farms** (`hearth:farm/...`): `field` (FARMER), `orchard`, `pasture` (HERDER), `paddy` (karst, tropical),
`terrace_field` (HILLSIDE).

**Commerce** (`hearth:commerce/...`): `market_stall` (MERCHANT, CENTRAL), `market_hall` (MERCHANT, town+), `inn`
(INNKEEPER, beds for travellers), `bank` (MINTER: mint, treasury and deposits).

**Civic** (`hearth:civic/...`): `chapel` (PRIEST, village+), `town_hall` (town+, max 1), `library` (SCHOLAR),
`school`, `bathhouse`, `guardhouse` (GUARD).

**Storage** (`hearth:storage/...`): `storehouse`, `granary`, `warehouse` (town+).

**Defense** (`hearth:defense/...`): `palisade`, `wall`, `gatehouse`, `watchtower` (GUARD, VERTICAL).

**Party walls.** `party_left` / `party_right` (and the planner's `corner_left` / `corner_right`) are the building's
**own** left and right, looking out of its front door along `facing` (left = `facing.getCounterClockWise()`).

**Infrastructure** (`hearth:infra/...`) — long, thin plots; the footprint is the bounding box, the params carry the
geometry (world coordinates). The table below is the summary; the exact geometry the planner reserves (the dense
centreline rule, the corridor rule, odd widths, bridge abutments at bank level with the deck at `deck_y`, `boardwalk`
bridges, terrace `retain_*` flags, one-wide `path` roads to set-back doors, three-wide farm tracks) is specified in
**`docs/PLANNING.md` §7, which is binding** for infrastructure blueprints:
| id | params | builds |
|---|---|---|
| `road` | `points`: int array x,y,z,… (centreline at walking level), `width`, `kind` (`street`/`lane`/`alley`/`path`/`highway`) | graded surface with cut and fill, edges/curbs, steps where steep, lamps by tier |
| `stairs` | `points`, `width` | a stair street with landings, for slopes too steep to grade |
| `bridge` | `points` (two ends), `width`, `deck_y` | span chosen by length and culture: beam, arch, trestle, rope/suspension, covered |
| `terrace` | `level` (platform y); footprint is the platform | retaining walls on the downhill sides, steps up, a level deck for plots on top |
| `walkway` | `points`, `width`, `deck_y` | an elevated walk or covered bridge joining buildings above the street |
| `dock` | `points`, `width` | piers, moorings, boats |

**Landscape** (`hearth:landscape/...`): `plaza` (CENTRAL, with a notice board), `well` (CENTRAL), `fountain`, `garden`,
`park`, `lamp_post`, `statue`, `bell` (meeting point).

**Landmarks** (`hearth:landmark/...`, max 1 each, city or town): `bell_tower`, `great_hall`, `cathedral` or `temple`,
`lighthouse` (maritime), `pagoda` (karst), `clock_tower`.

Blocks owned by other modules that blueprints place: `hearth:notice_board` (quest module; fall back to an oak sign on
a fence post if absent). Resolve by id at runtime.

---

## 7. Layout (plan module)

The planner turns needs into plots that make a place: a heart, streets that follow the land, districts that differ.

- **Layout types** (chosen at founding from culture, terrain and chance; may densify as tier rises): scattered
  farmsteads (expansive, spread out along tracks), crossroads village, linear (along a river, coast or ridge), hillside
  terraces, dense walled town (tall, tight), canal/stilt town over water, cliff town (stacked up a cliff or karst
  tower), mountain hold (switchbacks). Both **expansive** (hundreds of blocks of farmland and hamlets) and **dense and
  tall** must come out of it.
- **Roads first.** A road graph grown from the heart: main street, lanes, alleys; graded within a slope limit, stairs
  beyond it, switchbacks on mountainsides, bridges over rivers. Roads are plots (`hearth:infra/*`), built like
  buildings.
- **Plots along roads**, facing them, with floor levels that meet the street; corner lots, plazas at junctions; civic
  buildings on prominent ground (the chapel on the rise, the inn on the main road, the market in the square).
- **Verticality**: terraces stepping up slopes with retaining walls; rowhouses and townhouses on tight frontages; upper
  storeys and stacked buildings (`STACKABLE` on terrace decks); towers where they read from afar; walkways joining tall
  buildings; a city's core rebuilt taller over time (`replaces`).
- **Districts** at town and city: market, crafts by the water, rich houses on the hill, farms outside the walls.
- **The planner keeps its own state** (road graph, districts, layout type, reserved land) under its own data key.

### 7.1 Signature settlements on Expanse land

Expanse renamed five biomes for a natural surface (2026-10): `wisteria_vale` → `woodland_glades`, `lumen_grove` →
`ancient_oakwood`, `opal_dunes` → `red_dunes`, `prismatic_peaks` → `dolomite_peaks`, `frostbloom_tundra` → `lichen_tundra`.
Hearth reads both ids everywhere (the sim's culture map and land table, the planner's signatures and materials); the new
ones are natural lands (woodland glades and ancient oakwood are temperate, with oak and dark oak), and nothing Hearth
places on the surface glows (lumen wood is read as birch; glowcaps, amber and prismite light only the deep towns).

Aim the planner's layouts and the architects' styles at these; they are what make Hearth feel made for Expanse:

| Land | Settlement |
|---|---|
| Jade Karst | **cliff town**: houses on limestone sills climbing stone stacks the town builds itself, bamboo walkways and bridges between them, a pagoda landmark |
| Redwood Giants | **forest hall town**: log halls among the giants, raised walkways on trestles, a great hall |
| Willow Bayou, Boreal Muskeg | **stilt town** on boardwalks over the water, docks and punts |
| Opal Dunes (now Red Dunes), Saguaro Flats | **oasis town**: courtyard houses, flat roofs stacked as terraces, domes, a market in the shade |
| Painted Canyons, badlands | **pueblo** stacked up the canyon wall, ladders and roof terraces |
| Olive Groves | **hill town**: dense, tall, stair streets, a bell tower at the top |
| Fjordlands, cool coasts | **harbour town**: quays, boathouses, net lofts, a lighthouse |
| Verdant Peaks, meadows | **alpine village**: chalets on terraces, switchback road, a chapel spire |
| Heather Moor | **moor village**: stone cottages, dry-stone walled fields fanning out |
| Amber Steppe | **ring town**: round houses in rings round a market ring |
| Wisteria Vale (Expanse now: Woodland Glades, a temperate town) | **garden town**: timber-and-plaster lanes under blossom, canals and arched bridges |
| Frostbloom Tundra (now Lichen Tundra) | **longhouse steading** round a fire hall |
| Palm and Coral Coast | **lagoon village** on stilts with jetties |

---

## 8. Hearthfolk (npc module)

- **Entity** `hearth:folk`: a humanoid (player-shaped) `PathfinderMob`, not a vanilla villager. Skins are composed on
  the client from the resident's appearance seed, culture and job (skin tone, hair, face, clothes, apron/armour/robe
  layers) into a dynamic texture — no skin assets to author, endless variety. Synced data: name, appearance, culture,
  job, age stage, quest marker.
- **Embodiment manager**: decides which residents are entities (see §4), spawns them where their routine says they are
  (in bed at night, at the workplace by day, on the road between), and folds them back into records.
- **Routine**: a schedule per resident from job, age and traits — sleep, wake, breakfast at the hearth, work, lunch,
  work, free time (the plaza, the inn, a viewpoint, visiting friends), home, sleep — with weather and danger overrides
  (shelter from rain and night monsters, ring the bell and hide in a raid). Cheap goals over vanilla navigation
  (doors, stairs, ladders); pathfinding throttled.
- **Jobs embodied**: builders fetch from the store, climb scaffolding they put up and take down, and place steps;
  farmers till, plant and harvest; foresters fell and replant outside town; merchants keep stalls; guards patrol and
  fight; smiths work the anvil; couriers and caravans travel between settlements (abstract when nobody is near, met on
  the road when someone is).
- **Personality and relationships**: traits, friends, families (spouses, children with parents' names and looks),
  moods; kept in `Resident.extra("npc")`.
- **Conversation**: right-click opens the conversation screen: a greeting that reflects time, weather, the player's
  reputation, the resident's traits and what is happening in town (a building just finished, stone short, a stranger
  on the road), rumours (directions to other settlements and to Expanse landmarks the atlas knows of), and the
  options from every `DialogueProvider`.

---

## 9. Coin (economy module)

- **Coins are items**, struck from Minecraft metals: the **copper bit** (1), the **iron mark** (10 bits), the **gold
  crown** (100 bits). Amounts in code are always bits (`long`). They stack, have weight in the hand, clink when
  picked up. A **coin pouch** (bundle-like) holds coin and is counted as carried. Change is made automatically.
- **Only mints make coin.** Players cannot craft coins; settlement mints strike them from the treasury's metal. Coin
  enters the world through trade, wages, quest rewards and as treasure (add coins to vanilla and Expanse chest loot —
  e.g. Expanse's lost chests — with Fabric's loot table events). Melting coin back gives less metal than it is worth.
- **Markets** are the merchant's stall: a trade screen of what the settlement sells (its surplus, as the culture's
  real items: local planks and logs for WOOD, bread and the local food for FOOD, ...) and buys (what it is short of,
  paying more the shorter it is). Selling to a town feeds its stockpile — **a player can make a town grow faster by
  selling it timber and stone.** Prices: a base value per item (data), times scarcity (stockpile vs. capacity and
  consumption), times the player's reputation, times local specialities; prices differ between towns, so carrying
  goods between them pays.
- **Treasury**: settlements earn from trade and taxes, pay wages and quest rewards, and only offer what they can pay.
- **Bank** (the mint's counter): deposit and withdraw; a deposit is honoured at any bank (world data per player).
- **Property**: buy a house or an empty plot in a town with a deed (`Settlement.setPlotOwner`); its folk do not use it.
- Emeralds stay vanilla's villager currency; a money changer at the bank exchanges emeralds and coin at a rate.

## 10. Quests (quest module)

- **From real needs** (events and settlement state): supply (a stalled build's missing goods, paid above market),
  bounty (monsters harrying the town at night, a named beast in a nearby cave), courier and escort (a parcel or a
  caravan to another settlement), exploration (find and chart an Expanse landmark or realm — the atlas and structure
  locate give the target; bring back proof), scouting (pick a site for a new hamlet — and it is founded there),
  gathering (Expanse's mammoth wool, venison, crab), personal (a resident's own request: a gift, a lost thing).
- Offered by residents in conversation (`DialogueProvider`) and on the **notice board** block in the plaza.
- **Journal** item and screen (active quests, objectives, rewards), HUD objective line, waypoint marker.
- **Rewards**: coin (from the treasury), reputation, items, and consequences in the world — the bridge you supplied gets
  built, the hamlet you scouted is founded.
- **Reputation** per settlement: prices, quest availability, greetings, and at high standing a house to buy.
- Quest templates are data (`data/hearth/hearth_quest/*.json`) with variables, so writers can add quests without code.

## 11. Partners

**Who builds what (decided with the user, 2026-10-04).** Hearth owns the villages, towns, castles and keeps and every
working building — mills, charcoal kilns, quarries, mines, smithies, worked farms — and the roads within and between
settlements. Expanse owns the land between settlements: terrain, foliage and understory, hedgerows, fishponds, wild
features, ruins and small wayside sights. The surface stays natural (no glowing or coloured fantasy above ground;
underground may stay fantastical), and the temperate culture carries the KCD II Bohemian village look Expanse's
landscapes are built for. Hearth keeps its planned land clear of Expanse's structures and Expanse keeps its sights small
and unpeopled (it asks Hearth through its `LandClaims` hook before placing anything with a footprint).

**Villages.** With Hearth installed, its living settlements replace the world's static villages: every structure set made
only of `#minecraft:village` structures (vanilla's, Expanse's seven regional villages, any mod's) is left out of the
world's possible structures (`world/VillageReplacement`, `mixin/world/ChunkGeneratorStructureStateMixin`;
`config/hearth/villages.json` → `"replace_villages": false` keeps them). Expanse's settlement-like landmarks (Frostwatch
Citadel, Palm Harbour, the Logging Camp...) stay: remote, unpeopled places to explore and quest to.

- **Expanse** (optional, `compileOnly` + `localRuntime`; guard with `FabricLoader.isModLoaded("expanse")` and keep
  Expanse classes behind a separate class that is only loaded then). Cultures cover every Expanse biome; styles use
  Expanse woods and stones (wisteria, redwood, palm, baobab planks; limestone, opal sandstone) with vanilla fallbacks;
  the terrain probe reads the Earth model directly for rivers, coasts and landforms; settlements avoid Expanse
  structures; quests send players to Expanse landmarks and realms; Expanse's villages are left as they are.
- **Atlas** (optional; VOID's map, not on main yet). Its build is included only when present: `settings.gradle` takes
  `-Patlas.dir=<an Atlas checkout's atlas/ folder>` or else `../atlas` if it exists, and only then does `build.gradle`
  add the `atlas` source set (`src/atlas`, `compileOnly 'dev.voidmc:void-atlas'`, shipped in the jar; `-Patlas.run=true`
  also loads Atlas in the dev runs). Without it the source set does not exist and the `"atlas"` entrypoint line is
  filtered out of `fabric.mod.json`. `client.map.atlas.HearthAtlas` (the `"atlas"` entrypoint, which Fabric only loads
  with Atlas) is the only class that names Atlas types. Atlas owns discovery: the map module (server) only sends
  `hearth:map/settlements` — the settlements within `sync_radius` (1024) blocks of the player, with the player's own
  standing, title and property in each — looked at every 40 ticks (staggered), sent only when the list changed and only
  to clients that registered the receiver (`client.map.MapClient` does so only when Atlas is loaded). The plugin's place
  provider `hearth:settlements` reports them to Atlas as places with `DiscoveryState.UNKNOWN` (kind `hearth:<tier>`,
  category `VILLAGE`), so Atlas sights them ("?"), discovers them within its reach (24 blocks of the settlement's
  radius), keeps that with its map of the world, and makes the toasts and journal lines; `AtlasPlaces.look` gives each a
  settlement tile (house for camps and hamlets, village, `TOWN` for towns and cities; `KEEP` is kept for castles and
  seats once they are places of their own), folk, tier, culture, the player's standing meter and title, and
  `AtlasPlaces.claim` claims its area (radius + 32) so Atlas's own village sighting there is left out. Settlements Atlas
  remembers but the server no longer sends (far away, after a restart) get a plain look from the place's name and kind.
  The tracked quest (`QuestClient.tracked`, from the synced quest attachment) is a `Marker.Style.QUEST` marker
  (`MarkerGlyph.QUEST`, the step's name and its progress, "12 / 24") on a "Quests" layer (labelled by lang key), and
  `AtlasMarkers.track` is called whenever it appears or becomes another quest's (compass, minimap rim, beam); Atlas lets
  it go once the step has no place. The old per-player attachment `hearth:map/known` is still registered so old saves
  load cleanly, and is dropped when the player joins. `config/hearth/map.json`: `enabled`, `sync_radius`,
  `interval_ticks`. What Hearth asked of Atlas (all done): `docs/requests/atlas.md`.
- **LOD** (optional, later phase): VOID LOD draws settlements at the horizon from `Blueprint.silhouette()` of built
  plots, through a small read-only API the sim module will expose (`sim/LodFootprints`): columns of (wall, roof)
  colour and height, thread-safe, versioned so the LOD rebuilds only tiles that changed.

## 12. Performance rules (every module)

- Nothing per tick proportional to all settlements, residents or plots. Round-robin with budgets; event-driven
  updates; dirty flags.
- No chunk loads for simulation. Read terrain from the probe; read live blocks only in loaded chunks.
- Entities only near players, capped; AI goals cheap; pathfinding throttled and staggered; nothing ticks for a folk
  nobody can see.
- Planning, blueprint generation and pre-history may run off the server thread (they touch only the probe and
  immutable inputs); anything that touches the world or a live settlement runs on the server thread.
- Persist per settlement (one file per settlement under `<world>/data/hearth/`, written only when dirty) so a world
  with hundreds of towns saves only what changed.
- Measure: the dev harness logs time per subsystem; keep the abstract step of a 150-person town under 0.2 ms.

## 13. Testing

- **Unit tests** for pure logic: planner on synthetic terrain, blueprint invariants (within footprint and height, no
  floating blocks, every door reachable from the street, every bed reachable from a door), sim integration
  (deterministic history for a seed), prices, quest generation.
- **Offline previews** (gametest source set): `blueprintPreview`, `settlementPreview` → PNGs in `build/previews/`.
- **Dev harness** on a dedicated server: `./gradlew runServer` with `-Dhearth.dev.*` (found/adopt settlements round
  spawn, simulate N days, generate their chunks, log timings and stats, write previews, stop).
- **Client game test** (`./gradlew runClientGameTest`): flies to settlements and photographs them by day and night.
  This machine has a display and GPU, so it runs windowed here.

## 14. Phases

Status (October 2026): phases 1–3 and architecture stages A and B are merged into `hearth` and the build is green (2,000+
tests). What remains is a short list of open items, below.

1. **Foundations, in parallel** (merged): sim core, build foundation (format, styles, previews, first archetypes),
   planner, npc, economy, quests — each in its own worktree against this api.
2. **Architecture** (merged). *Stage A*: residential, civic and commercial, infrastructure, landscape and landmark
   archetypes for every culture (`arch-*`), then the polish passes (`a2-*`: lighting, styles, homes, civic). *Stage B*:
   the buildings for the living world, ahead of the systems that use them (`b-*`): the age of a town (additions, decay,
   dressings), crafts, refining, industry, extraction (campaigns and parts), learning, nobility and stations, the law
   house, social life and the underground towns. About 140 building types in 14 cultures, with the light rule
   (interiors ≥ 8) held by tests.
3. **The living world** (merged; designed in `docs/LIVING_WORLD.md`): light, repairs, reputation (standing), law and
   stealth, the resource economy (territory and survey, excavations, gatherers, chains, caravans, purses and shops),
   social life, and integration with the in-game tour. Modules: standing, law, repair and social join the six of
   phase 1 (§3).

### What's left

From the open items in `docs/requests/*.md` (each file says who owns it); everything else in those files is done.

**Planner and sim**
- Pastures are not stocked with animals (`PoiType.PEN` exists for it) — `arch-homestead.md`.
- No re-issue (`replaces`) of timber bridges as stone arches at town tier, of road plots repaved at village and town
  tier, or lamp posts on lanes — `arch-infra.md`.
- The lighthouse parcel does not prefer water in its back third — `arch-landmark.md`.
- Square furniture placement (benches, notice board, corner lamps) has not been reviewed — `arch-landscape.md`.
- Parcels are not sized deeper on purpose for houses that will grow additions, and learning buildings are not forced
  wide — `b-age.md`, `b-learning.md`.
- Gossip approximates the roads between towns; `HearthWorld.roads(...)` is not exposed — `p3-standing.md`.
- Carts, a caravan master who rides along, and an escort that is a resident rather than a mule — `p3-npc.md`,
  `p3-quest.md`, `quest.md`.
- No first-snow event in the social calendar (winter dressings) — `b-age.md`.

**Build module**
- Shared kit fixes: `Openings.window` of width 2 or more lands off target on the back and left walls; no `headroom`
  argument for `StairCore`; `Facade.plan` cannot pin the door; `Facade` braces cannot be copied; `Roofs.fillUnder` and
  the bracket helpers are package-private — `arch-townhome.md`.
- No doorstep reservation in `Draft` (`Works.keepDoors` stands in); the stripped-log `roof_trim`/`gable` fallbacks only
  warn — `b-industry.md`, `b-noble.md`.
- `BlueprintService.goodsOf(BlockState)` is not implemented by the build module, so repair keeps its own goods table
  — `p3-repair.md`.
- No check that a town's buildings are not near-copies of each other — `b-age.md`.

**Other modules and partners**
- The arrival banner has no hook for the standing line (it draws just under it) — `p3-standing.md`.
- The social module does not yet report a bath as the `social:bathed` deed — `p3-standing.md`.
- No client formatter for coin names — `quest.md`.
- Expanse: `UndergroundProbe.waterY` is not answered by Expanse's implementation (deep towns keep to the floor the
  probe reports) — `p3-plan.md`. Hearth reads Expanse's lakes (`ExpanseProbe`) and registers with its `LandClaims`
  (`ExpanseClaims`), so Expanse's standalone structures keep out of planned settlements — `expanse.md`.
- VOID LOD does not yet draw settlements; the read-only `sim/LodFootprints` is ready for it (§11).
- `SyntheticTerrain`'s brim-full river leaves banks below the water line, so approach roads dip below it in the
  previews — `arch-infra.md`.
