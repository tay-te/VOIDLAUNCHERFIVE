# VOID Hearth — the repair module

Damaged buildings are repaired by their builders (docs/LIVING_WORLD.md §2). The blueprint is the truth, so damage is the
difference between a built plot's blueprint and the world: the repair module notices it (from events, and from builders'
inspection rounds), keeps a repair queue per settlement, mends it — block by block by embodied builders, or in the record
by abstract labour and then on chunk load — follows fires as events with a bucket brigade, and reports vandalism to the
law. Code: `dev.voidmc.hearth.repair` (+ `mixin.repair`). Registers `RepairService` and three `FolkBehaviour`s.

```
repair/
  RepairModule        init: config, service, data key, behaviours, events, commands
  RepairConfig        config/hearth/repair.json
  RepairRuntime       per-server state: index, tracker, sprees, fires, abstract repair, chunk load times
  PlotLocator         position → (settlement, plot, stage, blueprint block): grid + LRU of plot sections
  DamageRules         what is damage, what a repair may overwrite, what is litter (pure)
  DamageTracker       hooks → damage records; cause context; DAMAGED batches
  RepairHooks         where the mixins call in
  Damage, RepairQueue the queue (settlement data hearth:repair/queue)
  RepairServiceImpl   claim / done / damaged
  Restorer            puts one block back with the applier's edit safety
  RepairBill          goods per block, worth in bits
  AbstractRepairs     labour while builders are records; the chunk applier for mended blocks
  Inspector           inspection rounds (budgeted blueprint-vs-world comparison)
  Litter              doorways and streets
  FireWatch           fire episodes: flames, helpers, brigade, water
  Vandalism           sprees, witnesses, law reports; standing for players who rebuild
  OwnerRepairs        deeded houses: quote and pay
  RepairDialogue      builders' conversation options
  RepairLines         "some fool smashed the chapel window"
  RepairEvents        FIRE_STARTED, FIRE_OUT, REPAIRED
  Scaffolds           takes down scaffolding left behind
  folk/               MendBehaviour (150), InspectBehaviour (110), BrigadeBehaviour (250), Column (scaffold), Reach
  command/            RepairCommands, SelfTest, DevFolk (stand-in folk for the self-test)
mixin/repair/         ServerPlayerGameMode, Level, ServerExplosion, FireBlock, BlockItem, BucketItem
```

---

## 1. Noticing damage

**Hooks** (26.3; each costs two field reads when no settlement is near):

| hook | catches | cause |
|---|---|---|
| `ServerPlayerGameMode.destroyBlock` (`@WrapMethod`) | blocks players break | `PLAYER` |
| `ServerExplosion.interactWithBlocks` (`@WrapMethod`) | creepers, TNT, beds, anything that explodes; who lit it (`getIndirectSourceEntity`) | `EXPLOSION` |
| `FireBlock.checkBurnOut` (`@WrapMethod`) | blocks fire burns away | `FIRE` |
| `Level.destroyBlock` (`@WrapMethod`) | withers, ravagers, `/setblock … destroy`, and every block popping off because what held it went | the surrounding cause, else `OTHER` |
| `FireBlock.tick` (head) | each flame's tick: feeds the fire watch | — |
| `BlockItem.placeBlock` (return) | a player putting a damaged block back; litter | — |
| `BucketItem.emptyContents` (return) | a player pouring water on a fire | — |

A **cause context** follows nested changes: the torch that drops when its wall is blown away is the explosion's, the door
that falls when its floor is broken is the player's. Changes the module makes itself (mending, clearing litter) run
inside a "self" context and are never damage.

**What counts** (`DamageRules`): the block that stood there was the blueprint's (by kind — an open door, a lit candle and
weathered copper are the same block) and the one there now is not. Blocks the blueprint expects to come and go are never
damage: air, fluids, plants and crops, leaves, farmland, snow, fire, cake, scaffolding, dirt paths, anything replaceable
(the invisible light block). A player's own deeded house is theirs to change. Excavations (`ArchetypeCategory.EXTRACTION`,
`extract/*`) are construction run backwards and never damaged.

**The position index** (`PlotLocator`) answers "which plot, which stage, which blueprint block is here": per settlement a
grid of chunk → plots whose footprint (± 2) touches it, rebuilt after `grid_ttl_ticks` or when a plot finishes or is given
up; per (plot, chunk, stages done) a section read with `Blueprint.forEachInChunk` one stage at a time, so the last write
wins as it does in the world. Sections live in an LRU bounded by total cells (`index_max_entries`). Only standing plots
count: `BUILT`, and the finished stages of `BUILDING` ones (a stage in progress is not tracked). Where plots overlap on
purpose, the later plot wins, as in the sim's applier.

**Batching.** Damage is queued at once; `SettlementEvents.DAMAGED` is sent once a second per (settlement, plot, cause):
an explosion is one event per plot, a fire one per plot per second however much it burns.

**Inspections** (`Inspector`, `InspectBehaviour`, priority 110): one round per settlement every `inspection.interval_ticks`
(2 minutes) by an embodied builder with nothing to mend, in working hours. The builder walks round a built plot (corner to
corner, then in) while `positions_per_tick` (20, i.e. 400 a second) of its cells are compared with the world. A hole (the
blueprint's block missing, nothing anyone placed in its way) is queued as `OTHER` damage; a block left in a doorway or on a
street is queued as litter. Found damage is committed when the round ends, unless a quarter or more of the plot's blocks
were missing — that is a building whose chunk the sim's applier has not written yet, not a ruin — and chunks loaded less
than a minute ago are skipped for the same reason. A round interrupted (to mend what it found) commits what it saw and
the plot is looked at again soon. Deeded houses are not inspected.

## 2. The repair queue and the service

Each settlement's `hearth:repair/queue` holds one entry per damaged position: plot, stage, step index (looked up when a
builder takes it), cause, day, offender, flags (`MENDED` in the record, `NEEDS_PAYMENT`/`PAID` for deeded houses,
`LITTER`, `REPORTED` to the law), the abstract labour clock, and every scaffold block up for repairs. At most
`queue.max_entries` (4096) per settlement. Claims and deferrals are not saved.

`RepairService` (registered in `HearthServices.repairs()`):

- `claim(settlement, builder, near)` reserves the open entry best for the builder — nearest, earlier stages first (walls
  before the lamps on them), doorways first — among those whose goods are in store and whose blueprint step is known. A
  builder holds one claim at a time; a claim lapses after `claim_ticks` (1200). An entry short of goods is deferred
  (`defer_ticks`) and the next is tried.
- `done(task, restored)`: put back (its goods taken from the stockpile, `RepairBill`) and gone, or returned to the queue.
- `damaged(settlement)`: blocks waiting.

**Repairs cost goods** like construction, in the phase 3 goods (planks WOOD, logs LOGS, stone bricks DRESSED_STONE, bricks
BRICK, glass GLASS, lanterns METAL + CANDLES, torches and candles CANDLES, beds CLOTH, bookshelves WOOD + BOOKS; earth and
plants are free). Litter costs nothing.

## 3. Mending

**Embodied builders** (`MendBehaviour`, priority 150 — work that interrupts the routine, so ahead of new construction):
an awake adult BUILDER of an embodied settlement, in working hours (`work_start`–`work_end`), with open damage in the
queue, claims the block nearest (within `radius`), says what they think of it, walks within arm's reach (4.6 blocks; a
spot to stand found round the block), puts up a scaffold column if the block is more than 3 above them (jump-and-place,
every block in the ledger, taken down top first), places exactly the blueprint's block (`Restorer`) with a swing, its sound
and particles, and takes the next block within `chain_radius` without stopping. A block they cannot reach after a fair try
is placed from close by (they stretch) or deferred; one that has beaten them three times is put in from as near as they
got (as construction does: a town is never left with a hole over one awkward block). Called away, they give the claim back
and their scaffolding comes down at once. The lines (`hearth.repair.say.*`) name the building and the part — "Some fool
smashed the chapel window.", "Blasted creepers. The bakery wall again.", "The fire took the inn roof. We'll have it back." —
at most once per `speak_cooldown_ticks`.

**Abstract repair** (`AbstractRepairs`): every `abstract.interval_ticks`, each settlement with damage accrues labour for the
sim time that passed (the overworld clock, so sleep and catch-up count; capped at `max_catch_up_days`): builders who are
records × `blocks_per_builder_hour` (16, half construction's 33 — finding damage takes time). Each unit mends one block in
the record — earlier stages first, its goods taken — and marks it `MENDED`. Builders who walk about as entities mend for
themselves and add nothing here. Mended blocks are written when their chunk is loaded — at once if it is, otherwise on
`CHUNK_LOAD` — under `apply_max_blocks` (256) and `apply_budget_ms` (1) per tick, through the `Restorer`. A block whose
support is still missing gets a second try after the rest of its chunk; failing that it goes back to the queue with its
goods returned.

**Edit safety** (`Restorer`, as the sim's `StepWriter`): a repair overwrites only air, fluids, plants, snow, fire, natural
ground (`#hearth:buildable_over`) and wild leaves — never a player's blocks, never a block entity that is not the plot's own
(an entry meeting one is dropped). Doors, tall plants and beds go back whole, the half on the ground first. Block-entity
data (sign text, banner patterns) comes from the blueprint step. Neighbours reconnect (fences, panes, walls) with
`updateNeighbourShapes`, drops suppressed. **Litter** — a block left where the blueprint has air, in the cells beside a door
or gate, the two cells over an entrance's outside block, or the walking space of a street (two cells over its surface) —
is broken with drops, so nothing a player owned is destroyed; block entities are never litter.

**Deeded houses** (`OwnerRepairs`): damage on a plot a player owns waits for the owner to pay (`owners.owner_pays`): goods
at the market's buying price × `markup` (1.25) plus `labour_fee` (2 bits) a block, into the treasury. `/hearth repair quote`,
`/hearth repair pay`, or "Mend my house (N blocks): price" in a builder's conversation.

**Players who rebuild**: a player who places the blueprint's block on a damaged position mends it (out of the queue, no
goods taken) and earns standing (`repair:rebuilt`, 1 + blocks/8, at most `rebuild_deed_max` per settlement per two minutes).

## 4. Fires

A fire that takes hold in a settlement's buildings becomes an **episode** (`FireWatch`): started by the first flame
ticking inside a plot (or on one) or the first block burned — never by a hearth fire on everlasting fuel
(`dimensionType().infiniburn()`) or a flame the blueprint itself puts there. It knows its flames (fed by each flame's tick,
pruned once a second against the world, at most 2048), the blocks lost, the players who fought it and the folk in the
brigade. `RepairEvents.FIRE_STARTED` fires at the start.

**The bucket brigade** (`BrigadeBehaviour`, priority 250, a reaction): awake adults within `fire.radius` (48) of it, up to
`max_folk` (12), raise the alarm ("Fire! Fire! Bring water!") and run between the nearest water source (found once per fire
in a bounded scan round it, `water_search` 24) and the nearest flame, dousing the flames within `douse_radius` of where each
bucket lands. With no water near they beat the flames out with shovels, slower. When the last flame is out they say so.

**After**: once no flame has been seen for `quiet_ticks`, the fire is out: players who fought it (put out flames by hand,
poured water near them, put blocks back meanwhile) earn standing (`repair:fire_brigade`, 2 + helps/2, at most
`brigade_deed_max` 15), `RepairEvents.FIRE_OUT` fires (with the blocks lost and the helpers, for quests and news), and the
builders rebuild from the queue the fire filled. Fires with nobody near (no embodied folk) burn as vanilla lets them and are
mended abstractly.

## 5. Vandalism and the law

A player's damage to a settlement's buildings (breaks, and TNT they lit) is gathered into **sprees**: one crime per player
per settlement per `law.window_ticks` (5 s), its worth summed (`RepairBill.worth`: the market's buying price for the block,
or a plain value per good without an economy). While the spree goes on, once a second, the NPC module is asked whether any
awake embodied folk within `witness_radius` can see the player. A witnessed spree is reported to `LawService.report` as
`Offence.VANDALISM` (victim: the owner the law module names for the position), and the law decides what it means. Damage
nobody saw is reported later, unwitnessed (a suspicion), when builders find it (claiming it, or abstract labour mending
it): `report_on_discovery`. Creative and spectator players' damage is mended but never reported (`exempt_creative`), and
other mods' fake players are never blamed. With no law module, witnessed vandalism costs standing directly
(`repair:vandalism`, `deed_per_block` −2 a block, at least −30).

## 6. Configuration — `config/hearth/repair.json`

Every field optional; written with the defaults on first run.

| key | default | |
|---|---|---|
| `tracking.enabled` / `player_breaks` / `explosions` / `fire` / `other_breaks` / `litter` | all true | what is noticed |
| `tracking.index_max_entries` / `grid_ttl_ticks` | 400000 / 600 | §1 position index |
| `queue.max_entries` / `claim_ticks` / `charge_goods` / `defer_ticks` | 4096 / 1200 / true / 400 | §2 |
| `abstract.enabled` / `blocks_per_builder_hour` / `interval_ticks` / `max_catch_up_days` | true / 16 / 200 / 30 | §3 |
| `abstract.apply_max_blocks` / `apply_budget_ms` | 256 / 1.0 | the applier for mended blocks |
| `builders.enabled` / `priority` / `work_start` / `work_end` / `radius` | true / 150 / 1000 / 12500 / 96 | §3 mending |
| `builders.step_ticks` / `scaffolding` / `chain_radius` / `speak_cooldown_ticks` | 25 / true / 24 / 1200 | |
| `inspection.enabled` / `priority` / `interval_ticks` / `positions_per_tick` / `max_ticks` | true / 110 / 2400 / 20 / 2400 | §1 inspections |
| `fire.enabled` / `brigade` / `priority` / `radius` / `max_folk` / `douse_radius` / `water_search` / `quiet_ticks` | true / true / 250 / 48 / 12 / 2 / 24 / 100 | §4 |
| `fire.brigade_deed_max` / `rebuild_deed_max` | 15 / 10 | standing for helpers |
| `law.report_vandalism` / `witness_radius` / `window_ticks` / `report_on_discovery` / `exempt_creative` / `deed_per_block` | true / 24 / 100 / true / true / −2 | §5 |
| `owners.owner_pays` / `markup` / `labour_fee` | true / 1.25 / 2 | deeded houses |

## 7. Commands

```
/hearth repair quote                 the bill for mending your property in the settlement here (everyone)
/hearth repair pay                   pay it; the builders mend it (everyone)
/hearth repair status                damage by cause, open / claimed / mended / held for owners / waiting, scaffolding,
                                     the fire if one burns, the behaviours (game masters)
/hearth repair list [n]              the damaged blocks nearest you
/hearth repair inspect               a whole inspection of the plot you stand in, now
/hearth repair mend [free]           mend everything open in the record now (goods taken unless free)
/hearth repair stats                 index, tracker, abstract repair, fires, law: counts and timings
/hearth repair selftest              the dev self-test (§8) in the settlement here
```

## 8. Seeing it work, and what it measured

**Self-test** — a dedicated server (game slot rules in the phase 3 brief; `run/server.properties` with a free port and
`pause-when-empty-seconds=0`):

```
cd hearth
JAVA_TOOL_OPTIONS="-Dhearth.dev.found=1 -Dhearth.dev.radius=3 -Dhearth.dev.simulate=20 -Dhearth.dev.generate=true \
  -Dhearth.dev.fillCatalogue=true -Dhearth.dev.commands=hearth+repair+selftest -Dhearth.dev.repair.stop=true" \
  JAVA_HOME=C:/Users/bitto/VOID/.tools/jdk-25 ./gradlew runServer
grep "\[hearth repair\]" logs/latest.log
```

On three real buildings of a real settlement (chunks forced, nobody near) it: benchmarks the index; takes a baseline of
every blueprint block that stands; breaks blocks with `destroyBlock`, takes some silently (no event), has a fake player break
some, put one back and leave cobblestone in a doorway, sets off an explosion against a wall, and sets timber alight (fire
spread allowed with nobody near for the test); waits for the fire and for the watch to call it out; inspects the buildings;
mends everything in the record and lets the applier write it; verifies every baseline block against the blueprint. Then,
with no NPC module in the worktree, stand-in folk (`DevFolk`: husks with their goals removed, driven through a
`FolkHandle` over vanilla navigation and a scheduler that follows the `NpcService` contract) run the real behaviours: a
builder mends blocks broken for them (the highest of a building first), three folk fight a fire, and the builder makes an
inspection round and mends what it finds. `-Dhearth.dev.repair.nofolk=true` skips that part.

**Measured** (seed 20261003, Expanse Earth terrain, the dev catalogue's stand-in buildings, this machine, 2026-10-03):

| | |
|---|---|
| index lookups over a whole town (20 000 random positions) | 1.6–2.4 µs cold (building sections), 0.5–0.7 µs warm |
| section builds | 97 sections, 12 600 cells, 5.5–11.8 ms in all |
| damage hooks inside settlements | 177–254 calls, 15–80 ms in all over the run, dominated by first lookups in a plot (its blueprint and sections built) |
| explosion against a wall | 25–29 building blocks lost, all queued (+ the torches and doors that fell with them), one DAMAGED per plot per second |
| `destroyBlock`, player breaks | 12/12 and 4/4 queued, with the player as offender; a block put back by hand leaves the queue |
| silent removal | 0 queued by events (as it must be); 6/6 found by inspection |
| fire left to burn 90 s | 37–45 blocks queued as burned; one episode, FIRE_STARTED and FIRE_OUT once each |
| inspection comparison | 1.1 µs a cell warm (0.02 ms a tick at a builder's 20 cells), 6–7 µs with cold sections |
| abstract repair and applier | 114–148 blocks mended and written, 0 refused, 7–23 ms in all |
| verify | 963 of 963 baseline blocks stand as the blueprint has them; every block the test broke is back; litter cleared |
| bucket brigade (stand-ins) | 3 flames set: out in 41 ticks with 4 folk |

## 9. Integration notes and limits

- **Behaviours** are registered with `NpcService.registerBehaviour` in `init()` if the NPC module has registered its service,
  otherwise at `SERVER_STARTING`. Without an NPC module every repair is abstract (records' labour mends in the record and
  the applier writes it), whether or not the settlement is embodied.
- **Construction labour.** Abstract repair labour is on top of construction's (docs/requests/p3-repair.md §1).
- **Partial stages.** Only finished stages of a plot under construction are tracked; a block of the stage being built that
  is broken is not mended (the builder's next pass over the plot does not revisit it either).
- **Fires at night** get no brigade while folk sleep (`FolkHandle.awake()`; requests §4).
- **Abandoned plots** are not mended: they decay (LIVING_WORLD.md §7.2), the build module's decay pass.
- Not saved: claims, deferrals, fire episodes (a fire still burning after a restart starts a new episode on its next flame),
  sprees (reported or dropped at shutdown), the index.
