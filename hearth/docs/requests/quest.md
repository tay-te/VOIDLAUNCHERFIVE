# Requests from the quest module

What the quest module needs from the api or other modules, and how it works around each until then.

## 1. Sim: a "found a settlement here" hook (scouting quests)

**Done** (integration): handing in a scouting quest calls `HearthWorld.found(site, null, origin, reason)` (`QuestManager.foundScouted`); `SITE_SCOUTED` still fires afterwards. The scout objective also requires `whyNotFound(pos) == null`.

**What.** A way to ask the sim to found a new hamlet, as a daughter of an existing settlement, at a site a player
scouted.

**Why.** §5.1 says a scouting quest picks the site "and it is founded there". The quest side is done: a scouting quest
completes when the player stands, within the distance band, on gentle ground with water near and clear of every
settlement; the site is stored on the objective and, when the quest is handed in, the quest module fires
`dev.voidmc.hearth.quest.QuestEvents.SITE_SCOUTED (level, origin, site, player)`. Nothing founds the hamlet yet.

**Proposed signature** (api, `HearthWorld`):

```java
/**
 * Founds a settlement at {@code site} as settlers from {@code parent} (culture from the site's biome; the planner's
 * found() lays it out). Returns null if the site is no longer suitable (too close to another settlement, under water).
 */
@Nullable Settlement found(BlockPos site, @Nullable Settlement parent, String reason);
```

Until then the sim can subscribe to `QuestEvents.SITE_SCOUTED` directly (it is a plain Fabric event in the quest
package) — or the coordinator moves that event into `api/SettlementEvents` and the quest module fires it there.

## 2. NPC: an escort that is a resident, not a mule

**Open**: needs a companion api and npc work; the mule escort stays.

**What.** A way to have a resident follow a player (embodied as `hearth:folk`) and report arrival or death.

**Why.** §10 mentions escorting "a caravan to another settlement". Escorts today lead a vanilla pack animal (mule or
llama, chest on, named, leashed to the player, tagged with the quest), which works without the NPC module and fails the
quest if it dies or is lost. A folk companion would be better.

**Proposed signature** (api, new `CompanionService` or on `ResidentFactory`'s owner):

```java
/** Asks the npc module to embody a resident following the player; null if it cannot (too many folk, no space). */
@Nullable UUID follow(ServerPlayer player, Settlement settlement, Resident resident, BlockPos destination);
```

plus `SettlementEvents.COMPANION_ARRIVED / COMPANION_LOST`.

## 3. NPC: call the quest dialogue's `collect` only when a conversation opens

**Checked** (integration): `Conversations` calls providers only when a conversation opens and after an option is picked.

`QuestDialogue.collect` refreshes the settlement's board if a day has turned (cheap, once a day per settlement) and
reads the player's quests. Please call providers when a conversation opens or after a `select`, not every frame.

## 4. API: the board's position

**Done** (integration): offers and accepted quests point at the first `NOTICE_BOARD` POI (`QuestWorlds.turnInPos`), else the centre.

Quests hand in at "the giver's settlement" and the journal's waypoint points at `Settlement.center()`. If a settlement
exposed its notice board's position (a `Poi` of type `NOTICE_BOARD` from the built plaza, say through a
`Settlement.pois(PoiType)` lookup), the waypoint could point at the board itself.

## 5. Economy: coin names on the client

**Open** (optional): no client formatter yet.

Reward text is written by the server (`EconomyService.format(bits).getString()`) and stored on the quest, so the client
needs nothing. If the economy module adds a client-side formatter with coin icons, the journal and board can use it.

## 6. Gametest entrypoint (optional)

**Done differently** (integration): `IntegrationTour` takes a real supply quest from the board and hands it in; `QuestClientTour` stays as the sandbox pilot.

`QuestClientTour` (client, dev only, `-Dhearth.dev.quest.tour=true`) takes screenshots of the board, journal and HUD
in a quick-played world. A `fabric-client-gametest` entrypoint in the gametest `fabric.mod.json` would let it run under
`runClientGameTest` instead; propose `dev.voidmc.hearth.gametest.QuestTour` wrapping the same steps.
