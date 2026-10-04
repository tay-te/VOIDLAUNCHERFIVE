# law module (phase 3): requests to the coordinator

What the law module needs from outside its package, what it does meanwhile, and proposed signatures.

## 1. Wanted posters on the notice board (quest module)

**Why.** LIVING_WORLD.md §4: wanted status spreads to neighbouring towns "with wanted posters on their notice boards".
The board and its screen are the quest module's.

**Meanwhile.** `LawServiceImpl.posters(Settlement)` returns the posters (`Poster(player, name, bounty, worst, outlaw,
origin)`, worst first); `/hearth law wanted` prints them. Nothing is drawn on the board.

**Proposed.** Add to `LawService`:

```java
/** Every player wanted in a settlement, worst first, for notice boards and town criers. */
List<Wanted> wanted(long settlementId);

/** @param origin the settlement the crime was committed in when word came from elsewhere (0: here) */
record Wanted(UUID player, String name, long bounty, Offence worst, boolean outlaw, long origin) {}
```

and the quest module shows them as pinned papers on the board ("WANTED — Alex — for theft — 45 bits"), perhaps with a
bounty-hunter quest for outlaws later.

## 2. Merchants refuse stolen goods (economy module)

**Why.** §4: "That town's merchants will not buy them; a fence will". The market is the economy's.

**Meanwhile.** The law module's fence (a `FENCE` resident, or the innkeeper in one town in three of village size and
up) buys stolen goods through a `DialogueProvider` at 35 % of their worth, from a daily purse. The market still buys
everything.

**Proposed.** In the market's sell path: `LawService law = HearthServices.law(); Owner from = law == null ? null :
law.stolenFrom(stack); if (from != null && from.settlementId() == settlement.id()) refuse` (with a line such as "I know
where that came from"). Stolen marks fade after `stolen_fade_days` (7), which `stolenFrom` already accounts for.

## 3. Resident traits (npc module) — a data contract, not an api change

**Why.** Witness reactions, sharpness and speech checks use traits (brave, cautious, greedy, generous, pious, curious,
dreamy, lazy, shy).

**Meanwhile.** `law/folk/FolkTraits` reads `Resident.extra("npc").traits` as a list of trait names, without the npc
module's classes. If the npc module renames or moves them, please keep the names or tell the law module.

**Proposed (optional).** `Set<String> NpcService.traits(Resident)` or a `Resident.traits()` accessor in the api.

## 4. Jail cells and the pillory in blueprints (build module)

**Why.** Submitting to the guard means the guardhouse cell (`PoiType.CELL`); lesser crimes may take the pillory
(`PoiType.PILLORY`) in the square. Phase 2's `civic/guardhouse` has no `CELL` POI.

**Meanwhile.** With no cell, submitting settles the warrant with a public reprimand (a standing hit) instead; with no
pillory the option is not offered. `/hearth law jail|pillory <player> <minutes>` say so.

**Proposed.** `civic/guardhouse`, `law/jail` and `law/watch_house` report a `CELL` POI per cell (the block a prisoner
stands in, inside a lockable door: the law treats LAW-category doors as always locked, tier 4); `law/pillory` and
plazas at town tier report a `PILLORY` POI; strongboxes in banks, keeps and merchants' houses a `STRONGBOX` POI (locked
at tier 5).

## 5. Telling the npc module a folk is busy with the law (npc module)

**Why.** Guards confronting a player, witnesses running to the guard and pickpocketing all assume the folk stays put
or follows the behaviour. The `FolkBehaviour` contract covers that (priority 250 witness, 320 guard).

**Meanwhile.** Nothing extra is needed if the npc module honours behaviour priorities over routines and its own
reactions (fleeing when hurt). A pickpocketing session ends when the mark walks more than 3 blocks away.

**Proposed (optional).** `FolkHandle.busy()` / a way to freeze a folk for a moment (being robbed while asleep should not
wake them unless they notice), and `FolkHandle.wake()` so a caught pickpocket wakes a sleeper.

## 6. Vandalism against the repair module's damage (repair module)

**Why.** "The repair module tracks the damage; you judge the crime."

**Meanwhile.** The law module judges player block breaks itself (`PlayerBlockBreakEvents.AFTER`): a broken block that
is one of the plot's built blueprint steps (`Blueprint.forEachInChunk` over the built stages) is vandalism, its fine
`vandalism_per_block` per block, merged into one crime per spree. Excavation plots never count. It does not listen to
`SettlementEvents.DAMAGED`, so explosions and fire a player caused are not crimes yet.

**Proposed.** When the repair module fires `DAMAGED` with cause `PLAYER`, include the player (`@Nullable UUID player`)
so TNT and arson can be judged too.
