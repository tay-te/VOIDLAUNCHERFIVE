# Lane 0 of the living world, part 2: what landed, and what the lanes need

From lane 0 (LIVING_WORLD_2.md §11.2), 2026-10-20. Lanes add to their own `docs/requests/lw-<lane>.md` when they need
another lane's file; this one lists the shared pieces and the screens to design.

## Screens to design in Figma first (§10)

None is built in code. Until the user picks a design, lanes ship through conversation options (§0.4 order bands) and
the existing Hub pages (conversation, journal, market, reputation).

| # | Screen | Kind | Lane |
|---|---|---|---|
| F1 | Conversation with a stranger (pedlar, pilgrim, bandit, tollkeeper): portrait without name and trade, hooded variant, a demand with coin icons (pay / talk / threaten / fight) | variant of the conversation page | A1 |
| F2 | Household: houses, servants and wages, larder, companions and their orders | new section of the Quests page (or a Hub segment) | B4 |
| F3 | Companion gear: worn armour, held items, a small pack | new | B4 |
| F4 | People: named characters met, where their story stands, relations touched | new section of the journal | B2 |
| F5 | Retinue ranks per lord and their rights; burgher status per town | change to the reputation page | B4 |
| F6 | Contest line (archery scores, melee bouts), restyled like the quest HUD | new HUD | B1 |
| F7 | Event line under the arrival banner ("A wedding at the chapel") | change | B1 |
| F8 | Roads and posts on Atlas (roads layer, post tiles) | Atlas's own UI (a request to Atlas) | A1, B3 |

New page state goes through `client/overlay/HearthStateSections.register(key, section)` (`people` for B2, `household`
for B4); actions through `HearthActions`, re-validated on the server.

## API additions (§11.4, additive only)

- `JobType`: BANDIT, PILGRIM, MESSENGER, CARTER, PEDLAR, TOLLKEEPER, SERVANT, HERMIT, MONK, BAILIFF, with
  `Station.of`, names in `lang/en_us/npc.json` and `about.job` lines in `hearth_dialogue/lw_jobs.json`.
- New types: `RoadNetwork` (+ `HearthWorld.roads()`, `NONE`), `TravelService`, `SettlementCalendar` (never null),
  `CrowdService`, `SceneService`, `LivesService`, `GoodsItems` (never null, `VANILLA`), `SettlementKind`.
- `HearthServices`: `travel`, `calendar`, `crowds`, `scenes`, `lives`, `goodsItems` (get and set).
- Defaults: `Settlement.kind/postType/setResidentAway/isResidentAway`, `SettlementPlanner.exits`,
  `NpcService.pin/unpin/pinned`, `EconomyService.chargeDeposit/shopStock`, `QuestService.offerPersonal`.
- `SettlementEvents`: TRAVELLER, TOWN_EVENT, LIFE_EVENT, COMPANION.
- Untouched: `PlayerSkills`, `PriceModifier`, `DialogueProvider`, `EconomyService.charge/price`, `QuestEvents`,
  `QuestType`, `MarketEvents`.

## Hooks (internal)

| Hook | Consulted by | Used by |
|---|---|---|
| `sim/world/Claims.register` | `SimWorld.isPlanned/isPlannedNow` (so world gen's guard and `ExpanseClaims`) | A1 roads, A4 Great Mine, B3 posts |
| `sim/settlement/ResidentClaims.register` | `SettlementClock.assignJobs/assignHomes` | B2 apprentices and masters, B4 servants and companions |
| Away: `SimSettlement.setResidentAway` (saved under `hearth:sim/away`) | the clock's workforce, deaths, emigration, decline, hamlet settlers | A1, A4, B4 |
| `npc/routine/ScheduleOverrides.register` | `FolkBrain` and `RoutinePlacement` (every day of every resident) | B2 named characters |
| `quest/gen/OfferSources.register` | `QuestGenerator.refresh`, after needs, events and bounty, before the fill | A1 road happenings, B4 commissions |
| `client/overlay/HearthStateSections.register` | `HearthState.build/revision` | B2 People, B4 Household |
| `sim/LoadCaps` | (pure) caps on the clock's `high_mspt` curve: full to 60 %, none new above, shed above it | every lane's caps |
| `sim/LivingWorldStats.timer(name)` | `/hearth sim stats` (one line per lane that ran) | every lane's tick cost |

## Entry points and settings

Each lane's init is called once from its module's init (lanes never edit those). Settings are in their own files under
`config/hearth/`, with the design's defaults and §13's decisions:

| Lane | Entry points | Config |
|---|---|---|
| A1 | `sim/roads/Roads`, `npc/travel/Travel`, `client/npc/travel/TravelClient` | `roads.json` (`Roads.config()`): roads, traffic, parties, bandits (`kill_chance` 0.05, `may_kill` true) |
| A2 | `social/rhythm/Rhythm` | `rhythm.json`: rhythm, lights (8 a town) |
| A3 | `npc/crowd/Crowds` | `crowds.json`: crowds (12 reacting a player), bark cooldowns (§0.2) |
| A4 | `npc/work/field/FieldWork` | `fieldwork.json`: sites, flocks (8/24/96), boats (6/24), displays (6/60/1 500) |
| B1 | `social/events/TownEvents` | `events.json`: `live_fires` auto (on in single player, off on servers), `raid_deaths` true, `executions` staged |
| B2 | `sim/lives/Lives`, `social/lives/LivesFolk` | `lives.json` |
| B3 | `sim/wild/Wild`, `plan/wild/WildPlanner` | `wild.json`: `per_region` 3, `step_interval` 6 000 |
| B4 | `npc/companion/Companions`, `economy/household/Household`, `social/burgher/Burghers`, `quest/commission/Commissions` | `place.json` (`Companions.config()`): 2 companions a player, 64 a server, 3 servants a house, knighthood true |

Lang fragments, empty, one per lane: `lw_roads`, `lw_rhythm`, `lw_crowd`, `lw_work`, `lw_events`, `lw_lives`, `lw_wild`,
`lw_place` (`assets/hearth/lang/en_us/`). Data folders are made by the lane that first writes into them.

Not declared centrally: the cart entity (`hearth:cart`) and A1's payloads are registered from `Travel.init` and
`TravelClient.init` (A1 owns both packages and the only user, A4's handcarts, comes after A1 merges); every other lane's
payloads, items and data keys go in its own entry point the same way.

## Cost

Lane 0 adds no tick work. With no source registered, every hook is one array-length check; `isResidentAway` is one map
lookup that answers false without creating the settlement's away data.
