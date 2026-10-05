# VOID Hearth — quests

Quests in Hearth come from what a settlement needs. A cottage stalls for want of stone, so the town asks for stone,
pays above market for it, and the stone you bring goes into its stores so the builders take up their tools again.
Monsters press on a town at night, so it posts a bounty. It has neighbours, so it needs parcels carried and pack mules
led. Its scholars want rubbings from the landmarks of the Expanse; its folk want gifts, lost keepsakes found, letters
taken to friends. All of it is server-side, per player, and cheap: nothing scans the world.

Phase 3 (docs/LIVING_WORLD.md §4–§7) ties quests to the living world: workshops short of ore, wax or logs post standing
contracts; fires, mine collapses, robbed caravans, spent quarries and declining towns become happenings that call for
help; the reeve, the guard captain, the priest, the master builder and the lord give short lines of work that follow the
town's real state; the law refuses work to the wanted, posts their names, and lets them clear them; every completion,
failure and abandonment is a deed for the standing service, and better work waits for those with a better title. §13
describes it.

Code: `src/main/java/dev/voidmc/hearth/quest/` (server), `src/client/java/dev/voidmc/hearth/client/quest/` (client).
Data: `src/main/resources/data/hearth/hearth_quest/` (templates).

---

## 1. The model

| Thing | Where it lives | What it is |
|---|---|---|
| **Template** | `data/<ns>/hearth_quest/**/*.json`, loaded by `QuestTemplates` (a reload listener) | A kind of quest as data: type, when it is offered, objectives with variables, text with placeholders, rewards |
| **Offer** | the settlement's `SettlementQuests` (data key `hearth:quest/board`, `SETTLEMENT` scope) | A `QuestInstance` bound to this settlement now: a giver, real items, real places, filled-in text |
| **Need** | `SettlementQuests.needs` (at most 8) | A stalled build's goods, or (with a `source`) a workshop's input it is short of |
| **Happening** | `SettlementQuests.happenings` (at most 12) | Something that happened to the town that quests answer: a fire, a collapse, a caravan robbed, a site spent, decline |
| **Player quest** | the player's `PlayerQuests` (attachment `hearth:quest/player`: persistent, copied on death, synced to the owner) | Their copy of an offer, with progress, escrowed coin and deadline |
| **History** | in `PlayerQuests` | The last 40 finished quests, and completion counts per template (for prerequisites) |

A quest's **status**: `OFFERED` → `ACTIVE` → `READY` (everything done, waiting to be handed in) → `COMPLETED`; or
`FAILED` (deadline passed, escorted animal lost) or `ABANDONED` (the player gave up).

**Objectives** (`ObjectiveKind`), each tracked on the server:

| Kind | Done when | Driven by |
|---|---|---|
| `deliver` | the items are handed over (partial deliveries count) | board or conversation hand-in |
| `kill` | N mobs of a kind are killed, optionally near the town and at night | `ServerLivingEntityEvents.AFTER_DEATH` |
| `slay` | a named beast, raised at its lair when the player comes near, is killed | tracker + death event |
| `reach` | the player reaches a place (may hand them an item there: a keepsake) | tracker |
| `explore` | the player steps inside a located structure's bounds (hands them a proof: a rubbing, a page) | tracker |
| `biome` | the player stands in one of a set of biomes (Expanse's realms, landforms) | tracker |
| `courier` | the sealed parcel is handed over in the destination town (to the recipient or at its board) | hand-in |
| `escort` | the pack animal arrives alive in the destination town | tracker + death event |
| `scout` | the player stands on a good hamlet site in the distance band, where the sim could found one (`HearthWorld.whyNotFound` is null) | tracker |
| `talk` | the player passes on a message to a particular resident | conversation |
| `ambush` | every one of a band (bandits, wolves, a robbery's pack train) raised at a place — or partway along the road to an escort's destination — is down | tracker raises it; death event counts |
| `apprehend` | a named culprit raised at a place (at night if asked) is beaten into surrender (taken alive), or killed where the warrant says dead or alive | tracker; damage event (yield); death event |
| `rescue` | the player stays at the place (trapped miners) with a pickaxe or shovel in hand for `hold` seconds | tracker |
| `extinguish` | every fire in the burning plot's box is out while the player is there | tracker (section palettes skip fireless sections) |
| `prospect` | the player stands, in the distance band and outside every town, among enough of a resource (a rock face, ore in a cave wall, a clay bank, timber) | tracker (a small cube of loaded blocks) |
| `pay` | coin handed to the treasury (a donation): through the economy, or emeralds without it | board or conversation hand-in |
| `hunt` | an outlaw player is killed by the hunter (only where `player_bounties` is on) | death event |

Quests may be **sequential** (find the keepsake, *then* bring it back) and have a **deadline** in days. They turn in to
the giver (in conversation) or at their settlement's notice board, or `auto` on the spot.

---

## 2. Template format

```json
{
	"type": "supply",
	"weight": 10,
	"when": {
		"trigger": "stalled",
		"goods": ["stone"],
		"cultures": {"karst": 3.0, "mesa": 1.0, "*": 0},
		"min_tier": "camp", "max_tier": "city",
		"min_reputation": -1000,
		"prerequisites": ["hearth:supply/timber"],
		"requires_mods": ["expanse"]
	},
	"giver": {"jobs": ["builder", "mason"], "council_only": false},
	"objectives": [
		{"kind": "deliver", "item": "$goods_item", "count": "$missing", "label": "Bring {count} × {item}"}
	],
	"text": {
		"title": ["Limestone for the sills", "Stone for the terraces of {settlement}"],
		"summary": "...", "offer": "...", "progress": "...", "complete": "..."
	},
	"reward": {"coin": [8, 18], "market_premium": 1.5, "items": [{"item": "minecraft:bread", "count": [2, 4]}], "reputation": 4},
	"deadline_days": 0,
	"cooldown_days": 0,
	"repeatable": true,
	"turn_in": "giver",
	"sequential": false
}
```

- **`type`**: `supply`, `bounty`, `courier`, `escort`, `exploration`, `scouting`, `gathering`, `personal`, and since phase 3
  `contract`, `caravan`, `law`, `disaster`, `official`, `crime`. Escort, scouting, personal and crime offers (and any with
  a slay, escort, scout, ambush, apprehend, rescue, prospect or hunt objective) go to the first player who takes them; the
  rest can be taken by every player once.
- **`when.trigger`**: `stalled` (only for a construction stalled for goods the template covers; the default for
  `supply`), `shortage` (only for a workshop short of an input; the default for `contract`), `event` (only after a
  happening named in `when.events`; the default for `caravan` and `disaster`), `pressure` (only while monsters press on
  the town; the default for `bounty`), `none` (always eligible).
- **`when.events`**: for `event` templates, the happenings answered: `fire`, `collapse`, `blast`, `depleted`, `declining`,
  `caravan_departed`, `caravan_robbed`, `caravan_lost`.
- **`when.requires`**: the town's state (`TownState`), each optionally negated with `!`: `stalled`, `shortage`,
  `depleted`, `crime`, `damaged`, `declining`, `embodied`, `walled` (village or larger), `job:<job>`, `site:<kind>`,
  `plot:<archetype path prefix>`, `event:<kind>`. The officials' lines use it to follow the town.
- **`when.min_title`**: the standing title a player needs (`known`, `friend`, `hero`...; a Burgher counts as a Friend).
- **`when.wanted`**: `refuse` (the default: not for players the law wants here), `allow` (a fence's work), `only` (clearing
  your name).
- **`when.cultures`**: weight multipliers. A list means weight 1 for those; unlisted cultures get the `"*"` weight
  (0 if absent). Leave it out to offer the template everywhere.
- **`giver.jobs`**: residents of these trades give it; otherwise any adult resident some of the time, otherwise the
  town's council ("the village council"). `council_only` keeps it with the council (a scouting commission).
- **Text variants**: each text field is a string or a list; one index is picked per quest and used across fields of the
  same length, so a title and its summary match.
- **Amounts** (`count`, `distance`, reward counts): a number, `[min, max]`, or a variable: `"$missing"` (items needed to
  cover a stall), `"$pressure"`, `"$population"`, optionally scaled: `"$missing*0.5"`.
- **Rewards**: `coin` in bits (the economy's smallest coin), scaled by settlement tier (camp 0.6 … city 1.7) and the
  config multiplier; `market_premium` adds that multiple of the market price of what `deliver` objectives bring
  (`supply` and `contract` default to the config's 1.5). All coin is capped by what the treasury can promise. `deed`
  names the deed reported on completion (default `hearth:quest/<type>`); `alive_bonus` (apprehend) multiplies the coin on
  offer, which is divided by it again if the culprit is brought in dead.

### Objective fields

| Field | For | Meaning |
|---|---|---|
| `item` / `items` | deliver | item ids or `#tags`; one is picked. `$goods_item` = the stalled goods as a concrete item in the town's materials; `$gift` = what the giver would like |
| `entity` / `entities` | kill, slay, escort | entity ids (`#tags` for kill), or `monster` for any hostile mob |
| `count` | deliver, kill | how many |
| `radius` | kill, reach, explore, slay | kill: the area round the town (default: the town's size + 32); slay: how near the lair the beast rises |
| `distance` | slay, reach, scout | `[min, max]` blocks from the town centre for the place or the hamlet site |
| `structures` | explore | structure ids; the nearest located one not already on the board is used |
| `biomes` | biome | any of these biome ids |
| `names` | slay, escort, reach | name choices for the beast, the mule, the place |
| `night` | kill | only kills after dark count |
| `health` | slay | health multiplier for the beast |
| `grant` | reach, explore, biome | `{"item", "name", "lore", "required"}`: an item handed over on arrival; `required` means it must be brought back |
| `recipient` | courier | a particular resident of the destination receives it (a letter to a friend) |
| `clearance` | scout | blocks the site must keep from every settlement |
| `target_name`, `label` | any | display text overrides |
| `place` | slay, reach, ambush, apprehend, rescue, extinguish | `ring` (a distance band; the default), `center`, `event` (the happening's place), `route` (partway along the road to the other town), `poi:<type>`, `site:<kind>`, `plot:<archetype prefix>` (a plot's work face or middle). Objectives of one quest naming the same place share it |
| `along` | ambush, reach (`route`) | how far along the road, 0–1 |
| `hold` | rescue | seconds of digging |
| `resource` | prospect | the `ResourceSite.Kind` to find; empty for the spent site's kind |
| `crime` | ambush, escort | killing these creatures is this offence (`robbery`), reported to the law |
| `caravan`, `cargo` | escort (`cargo` also ambush) | pack animals in the train (1–6), and whether their chests carry a load of the town's goods |
| `lethal` | apprehend | dead or alive |

### Placeholders

`{settlement}`, `{giver}`, `{giver_job}`, `{target}`, `{count}`, `{item}`, `{place}` (what is being built),
`{goods}`, `{direction}` ("north-east"), `{distance}` ("about 600 blocks"), `{mob}`, `{name}`, `{days}`,
`{recipient}`, `{culture}`, `{tier}`, and since phase 3 `{other}` (a happening's other town), `{cargo}` (what a caravan
carried), `{site}` ("quarry", "ore vein"), `{victims}` (trapped miners), `{workshop}` (a contract's workshop). They are
filled once, on the server, when the quest is made.

### Validation

Every file is parsed and validated on reload. A broken one is logged with the field at fault
(`hearth:supply/stone: objectives[0].count: expected a number, [min, max] or "$variable"`) and left out; the rest load.
Unknown items or creatures, placeholders nobody fills, a `$goods_item` outside a stalled template, a beast without a lair
distance: all rejected. A template whose `requires_mods` are absent is quietly disabled.

### The starting set (55 templates in phase 2; 129 with phase 3, see §13.8)

| Type | Templates |
|---|---|
| supply (13) | timber, northern timber, stone, karst limestone, clay, adobe, glass, metal, cloth, provisions, fuel, tools, finery |
| bounty (8) | night raiders, skeleton archers, spiders; named beasts: the Hollow (zombie), the Frostwight (stray), the Dune Walker (husk), the Bog Lurker (bogged/drowned); the outlaw (vindicator/pillager, standing 10) |
| courier (3) | parcel, medicine (3-day deadline), trade samples to a named buyer |
| escort (2) | pack mule, the caravan's last llama (steppe, desert, mesa, alpine) |
| exploration (13) | Expanse landmarks: Karst Temple, Cloud Monastery, Temple of the Pink Sun, Frostwatch Citadel, Palm Harbour, logging camp, Wisteria Manor, Caravanserai; old places (stone circle, ruined watchtower, woodland lodge, lumen shrine, nomad camp, stilt hamlet, lighthouse); vanilla ruins (pyramids, witch hut, igloo, trail ruins, outpost); the deep places (trial chambers, ancient city; standing 15); the realms under the world; strange country (volcano, tepui, salt flat, painted canyon) |
| scouting (1) | a site for a new hamlet (village and up, standing 10) |
| gathering (11) | mammoth wool, venison, crab, amber, prismite, frostblooms, edelweiss and heather, honey, curiosities, fish, hides |
| personal (4) | a gift to the giver's taste, a lost keepsake, a letter to a friend in another town, a message to pass on |

---

## 3. Where quests come from (generators)

`QuestGenerator` makes a settlement's offers. It runs when a board is looked at, a resident is spoken to, a marker is
asked for, or a construction stalls — never per tick — and reads only the settlement's record, its quest data and what
the game already knows. Same settlement, same day, same board for everyone. A refresh (at most every `refresh_days`):

1. Untaken offers past their lifetime come down; untaken supply offers whose stall has cleared come down.
2. Monster pressure halves for each day passed.
3. **Supply** from `CONSTRUCTION_STALLED`: each stall (plot, goods, units missing) becomes a supply quest for concrete
   items in the town's own materials (`GoodsItems`: a northern town's wood is spruce, redwood or pine logs; a desert
   town's acacia or palm; a karst town's stone is limestone), enough to cover the shortfall (one log = four units of
   wood), paid above market. Handing them in **adds the goods to the stockpile** and clears the need, so the build
   resumes. Posted the moment a stall fires, not only at refresh.
4. **Bounty** from night monster pressure: each player standing in a town at night adds the monsters within 48 blocks
   (one box query per player per ten seconds) to the town's pressure; at the threshold the town posts a bounty.
5. **Fill**: the remaining slots get a weighted mix of everyday templates (weight × culture; types already on the board
   are damped so it shows a mix), skipping those on cooldown. Binding can fail — no other town to carry a parcel to,
   no landmark located yet, no resident for a personal request — and then another template is tried.

**Landmarks** (`StructureLocator`): an exploration template names structure ids. If the settlement has no answer for
one yet, the generator queues a search and the quest is not made this time. The search runs on a worker thread: the
structure set's placement maths gives candidate chunks, a biome check against the biome source thins them (no chunks
touched), and only the nearest few are confirmed by asking the chunk system for their chunk at `STRUCTURE_STARTS`
asynchronously. The result (found, with its bounding box; or not within `explore_search_radius`) is saved with the
settlement, so each structure is searched once per town (a miss is retried after 30 days), and the board tops up when a
search finds something. Expanse's underground halls are placed by the realms rather than a structure set, so the realms
are reached by biome instead.

---

## 4. The notice board

`hearth:notice_board`: a framed spruce board on dark-oak posts under a little gable roof, papers pinned to it
(`papers` 0–4 follows the number of offers up, so an empty board looks empty). Craft: wooden slabs over planks–paper–
planks over two sticks. The build module places it in plazas by id.

Right-click opens the board of the settlement it stands in (`QuestWorlds.at`): each offer as a pinned paper (title,
who asks, reward; a brass pin and a red line if the player cannot take it yet: standing too low, journal full). Click a
paper to read it — the giver's own words, the objectives, reward, deadline — and take it. **Hand in** appears when the
player carries something for this town: goods for its quests, a parcel from elsewhere, a finished quest.

---

## 5. Conversation

`QuestDialogue` is a `DialogueProvider` (order 200–299) for the NPC module's conversation screen:

- 200 "I've brought what you asked for." — hand-ins to this resident (goods, a finished quest, a parcel for them)
- 205 "I have a message from …" — a `talk` objective for this resident
- 210/211 "I'll do it. (reward)" / "Not now" — after the player asked about an offer
- 220+ "Need a hand? (title)" — this resident's own offers; picking one has them explain it in chat
- 250+ "About (title)…" — how a quest from them is going

`QuestMarkers` is the `QuestService` the NPC module asks for the marker over each resident: `TURN_IN` beats `OFFER`
beats `ACTIVE`. It reads the player's few quests and the town's few offers.

---

## 6. The journal, HUD and waypoint

- **Journal** (`hearth:journal`, book + leather, or given with the first quest; or press **J**): a leather book open on
  two pages — quests under way on the left (ready ones in green, the tracked one marked), the chosen quest on the right:
  giver and town, what they said, each objective with progress (and how many of the items you carry), reward, time
  left; **Track** and **Abandon** (asks to confirm). The second tab lists finished quests.
- **HUD tracker** (top left): the tracked quest's title and open objectives, beside a small **compass** whose needle
  points to where the quest wants you — the place, the landmark, the destination town, or the giver's town to hand in
  (its notice board, the first `NOTICE_BOARD` point of interest of its built plots, when it has one; set when the offer
  is posted and again when it is taken) — with the distance under it.
- **Toasts** for a quest taken, ready to hand in, completed, failed.

The client only reads the synced `PlayerQuests` and sends intents (track, abandon, accept, hand in); the server checks
everything (reach to the board, the settlement, standing, journal size).

---

## 7. Rewards and reputation

- **Coin** comes from the settlement's treasury. An offer never promises more than `treasury_share` of the treasury
  (less what other untaken offers promise). Accepting moves the coin into **escrow** on the player's quest; completing
  pays it (through `EconomyService.pay` when the economy module is present, as emeralds at `bits_per_emerald` when not);
  failing or abandoning returns it to the treasury.
- **Items** from the template; quest items (parcels, keepsakes, proofs) are ordinary vanilla items marked with
  `custom_data.hearth_quest`, never mistaken for goods.
- **Reputation** (`Settlement.addReputation`): + the template's (or `reputation_complete`) on completion,
  `reputation_fail` on failure, `reputation_abandon` on abandoning. Templates gate on `min_reputation`; the board shows
  standing as Distrusted / Stranger / Known / Friend / Hero.
- **Consequences**: supply goods reach the stockpile and the build resumes (the sim gives the oldest stalled plot first
  claim on what comes in); a scouted site is founded as a hamlet by settlers from the giver's town
  (`HearthWorld.found(site, null, origin, reason)`; the player is told its name, or why it could not be founded), then
  `QuestEvents.SITE_SCOUTED` fires; `QuestEvents.COMPLETED` for anyone who reacts.

---

## 8. Servers and performance

- Server-authoritative, per player. The tracker looks at each online player once every `tracker_interval` ticks
  (staggered), checking only their own quests (at most `max_active`): positions against targets, deadlines, a beast's
  lair, an escort's arrival. Kills arrive as events. A forced board refresh of a 16-folk town with all 129 templates takes about 0.4 ms (measured on the dev server).
- Settlement data is small and bounded (8 needs, 12 happenings, 24 located targets); player history is capped at 40.
- Nothing loads chunks except the landmark confirmation (asynchronous, a few chunks per search, once per structure per
  town) and a beast raised in the chunk the player is standing near.

---

## 9. Config (`config/hearth/quest.json`)

```json
{
	"board": {"offers_per_board": 5, "offer_lifetime_days": 3, "refresh_days": 1, "max_active": 6, "max_supply_offers": 2,
		"supply_max_items": 96, "pressure_threshold": 12, "explore_search_radius": 3000, "courier_max_distance": 3000,
		"scout_clearance": 500, "tracker_interval": 20},
	"rewards": {"coin_multiplier": 1.0, "market_premium": 1.5, "treasury_share": 0.4, "bits_per_emerald": 20,
		"reputation_complete": 4, "reputation_fail": -6, "reputation_abandon": -2, "give_journal": true},
	"living": {"contract_offers": 2, "contract_stock": 16, "event_offers": 3, "happening_days": 4, "urgent_broadcast": true,
		"collapse_chance": 0.02, "rescue_failure_kills": false, "fire_min_blocks": 3, "refuse_wanted": true,
		"player_bounties": false, "report_crimes": true, "title_gates": true, "deeds": true}
}
```

`living` (phase 3): contracts on a board at once; units of each input a workshop wants per worker (below half of it, it
posts a contract); event offers at once; days an unanswered happening keeps making offers; whether a fire or collapse is
called out to the players in town; the chance a worked mine collapses each day while a player is near (0 off); whether
trapped miners die when a rescue fails (folk die permanently; off by default); burned blocks before a brigade is called;
whether the wanted are refused work; whether outlaw players become bounty quests (PvP servers); whether crimes in quests
are reported to the law; whether `min_title` gates apply; whether outcomes go to the standing service as deeds.

---

## 10. Commands (permission 2)

```
/hearth quest list [player]                 a player's active quests and history
/hearth quest board [refresh]               the offers, needs and pressure of the settlement you stand in
/hearth quest give <template> [player]      make a quest from a template here and give it
/hearth quest complete [player]             complete the tracked quest
/hearth quest abandon [player]              abandon the tracked quest
/hearth quest templates                     the loaded templates by type
/hearth quest sandbox [culture] [population]  an in-memory town round you: residents of every trade, coin, a cottage
                                            stalled for stone and timber, a notice board, and a neighbouring hamlet
/hearth quest stall <goods> <units>         the settlement here stalls for goods (a supply quest follows)
/hearth quest pressure <amount>             add night monster pressure here (a bounty follows at the threshold)
/hearth quest standing <amount>             set your standing here
/hearth quest talk "<resident>" [option]    converse through the dialogue providers in chat (stand-in for the npc screen)
/hearth quest happen <kind>                 something happens here (fire, collapse, blast, depleted, declining,
                                            caravan_departed/robbed/lost): the happening is recorded and answered
/hearth quest crime <amount>                add recent crime here (thief-taking and the guard captain's line follow)
/hearth quest contracts                     the workshops here short of inputs
/hearth quest give <template>               (also binds contract and event templates, inventing a need or happening)
```

And for every player (permission 0): `/hearth heed <offer>` — the link in a town's call for help — takes the offer if the
player is in or near that town.

The sandbox lives in memory until the server stops and works with or without the sim module.

---

## 11. Integration points

- **Sim** fires `SettlementEvents.CONSTRUCTION_STALLED` (→ supply), `BUILDING_COMPLETED` (→ the plot's needs and untaken
  supply offers are dropped), `RESIDENT_REMOVED` (→ their untaken offers come down); persists the `hearth:quest/board`
  data key; gives `HearthWorld.settlementAt/nearest/settlements/day`, `found`/`whyNotFound` (scouting) and
  `Settlement.pois(NOTICE_BOARD)` (waypoints).
- **Npc** opens conversations and calls `QuestDialogue`; asks `HearthServices.quests().marker(...)`; writes
  `extra.npc.traits` (list of strings), which personal gifts read.
- **Economy** prices goods (`EconomyService.price`), formats and pays coin.
- **Build** places `hearth:notice_board` in plazas.
- **Expanse** (optional): landmarks and realms for exploration, mammoth wool, venison, crab, amber, prismite,
  frostbloom, edelweiss for gathering, its woods and stones for supply; templates naming Expanse things declare
  `"requires_mods": ["expanse"]`.

---

## 12. Testing

- `LivingWorldTest` (phase 3): every good has items in every culture (and the vanilla ones exist), a supply and a contract
  template; workshops short of inputs post contracts and deliveries satisfy their need; a fire calls the brigade to the
  burning plot's box; a collapse asks for props then a rescue at the mine's work face naming the trapped; a departing
  caravan gets an escort with a cargo train and highwaymen partway along the road (and the road maths); a robbed
  caravan's robbers are on the road; a spent quarry asks for a new one of its kind; a declining town asks for a donation
  or new trade; happenings merge, resolve and are bounded; the town's state; the master builder's line follows stalls and
  its later steps want prerequisites and a title; every official has a chain of three or more; every happening has a
  template; titles and their gates; clearing your name and the fence; an outlaw warrant pays more alive; thief-taking
  waits at the market at night; prospecting rules; the validator's new checks; saving and loading (and a phase 2 save).

- `./gradlew test`: template parsing and validation (every shipped template), generation from synthetic needs (stalled
  stone → a supply quest for concrete stone in the town's materials, covering the shortfall), bounties from pressure,
  couriers needing a destination, exploration queuing a search then binding, treasury caps, determinism, expiry and
  cooldowns; objective progress (kills by kind, place and time; arrivals; sequential steps; deadlines; scouting band),
  emerald fallback and escrow, save round trips.
- Dedicated-server self-test of the whole loop as a fake player (76 checks, all passing on 2026-10-03; 36 from phase 2: supply into the stockpile, courier,
  bounty with real kills, keepsake, escort, abandon, landmark search, refresh timing; and the living world's
  40 in `LivingSelfTest`: a contract into the stores, a real fire put out by the brigade, props and a dig with a pickaxe for a
  collapse, a bandit camp raised and beaten, a market thief yielding and the purse brought back, a donation into the
  treasury, a new quarry prospected in real rock, a caravan train on the lead and a robbery reported to the law, the wanted
  refused work and offered a way to clear their name):
  `JAVA_TOOL_OPTIONS="-Dhearth.dev.quest.selftest=true -Dhearth.dev.quest.stop=true" ./gradlew runServer`
- Client tour with screenshots (`run/screenshots/hearth_quest_*.png`), into an existing world:
  `JAVA_TOOL_OPTIONS="-Dhearth.dev.quest.tour=true" ./gradlew runClient --args="--quickPlaySingleplayer <world>"`
- Assets: `python tools/quest/gen_quest_assets.py` regenerates the board's models, the paper and journal textures, the
  blockstate, loot table and recipes.

---

## 13. The living world (phase 3)

Code: `quest/service/LivingQuests.java` (events → happenings, outcomes, calls for help), `Standings.java` and
`LawBridge.java` (the standing and law services, with fallbacks), `Fires.java`; `quest/gen/Chains.java` (workshop inputs),
`TownState.java`, `Prospecting.java`; the new objective kinds in `QuestTracker` and `QuestSpawns`. Data:
`hearth_quest/{contract,caravan,law,crime,disaster,official}/`, and supply templates for every good.

### 13.1 Supply and contracts for the chains

Every `Goods` — raw, processed and finished — has supply templates for stalled builds and concrete items in every
culture's own materials (`GoodsItems`: a karst town's dressed stone is limestone bricks, a tundra town's meat venison, a
desert town's specialty prismite). Wheat stands for flour (the miller grinds it as it comes in). Salt exists only as
Expanse's salt block; without Expanse a salt quest is not made rather than asking for an item that does not exist.

**Standing contracts**: on each board refresh `Chains.shortages` reads the residents' trades and the stockpile — a
smelter wants ore and fuel, a chandler wax, a sawyer logs, a scriptorium paper, leather and candles (the table in
`Chains`) — and every workshop whose stores hold less than half of `contract_stock` per worker for an input becomes a
need with a `source` (the job). Contract templates (trigger `shortage`) bind it like a stall: concrete items in the town's
materials, paid above market, delivered straight into the stockpile, the need reduced by what was brought. They are
repeatable and posted again whenever the bins run low. The guild's large order wants a Friend of the town.

### 13.2 Happenings

`LivingQuests` turns events into happenings on the town's board, each answered at once by an event template (one offer
per happening; at most `event_offers` at a time; forgotten after `happening_days`):

| Event | Happening | Answered by |
|---|---|---|
| `DAMAGED` (fire, embodied towns, ≥ `fire_min_blocks` in a day) | `fire` at the plot | the bucket brigade (`extinguish`; everyone may join; completes on the spot) |
| `DAMAGED` (explosion) on `extract/mine`, `extract/shaft`, `deep/lift` | `collapse`, naming up to three miners | props, then a `rescue` at the mine's work face |
| `DAMAGED` (explosion) elsewhere | `blast` | materials to mend the plot |
| `DAY_PASSED`, a player near, a worked mine, `collapse_chance` | `collapse` | as above |
| `SITE_DEPLETED` | `depleted`, with the site's kind | `prospect` for a new site of that kind |
| `SITE_FOUND` | (resolves `depleted` of that kind) | |
| `PROSPERITY_CHANGED` (falling / rising) | `declining` (and the town's decline flag) / resolved | a donation (`pay`), letters for new trade (`courier`) |
| `CARAVAN` departed (from an embodied town) | `caravan_departed`, cargo and destination | an escort with a cargo train and highwaymen on the road |
| `CARAVAN` arrived | (resolves `caravan_departed`) | |
| `CARAVAN` robbed / lost (both towns) | `caravan_robbed` / `caravan_lost` at the midpoint | the robbers' camp, the stolen cargo, the lost caravan's ledger |
| `CRIME_COMMITTED` | recent crime (+1 theft … +4 murder, halving daily) | the `crime` state (thief-taking, the guard captain) |

Fires and collapses are urgent: the players in or near the town hear a bell and a call with an **[I'll help]** link
(`/hearth heed <offer>`). A completed quest resolves its happening (its untaken offers come down); recovered cargo goes
back into the stores; a prospected site fires `QuestEvents.SITE_PROSPECTED` and is reported to the sim (`Settlement.reportSite`), which surveys round it and adds the site (`SITE_FOUND`). A failed rescue kills the trapped
miners only with `rescue_failure_kills`.

### 13.3 Caravans

The escort spawns a train (`caravan`: 3) of pack animals, each on the lead of the one before, their chests loaded with the
cargo; the leader must arrive (a follower lost costs its share of the reward). Highwaymen (`ambush`, place `route`) spring
once the player is past half way along the road and within 160 blocks of it, a little ahead; if the caravan arrives and
they never sprang, the road was safe. Killing a caravan's animals — your own escort's or a target's — is robbery, reported
with `LawService.report` (witnessed if an embodied folk near can see it, or the robbed train's drover survives).
A fence's work (`crime/rob_caravan`) sends you to rob a pack train: the loot is what its chests carried, and what the
animals drop is marked stolen from their town (`LawService.markStolen`), so its merchants refuse it.

**The sim's caravans**: an escort answering a departing caravan carries that caravan's id (in the happening's plot field).
Taking it calls `HearthWorld.escortCaravan` (the caravan arrives whatever the road held, and the npc module puts no
second party on the road: the quest's own train is the caravan); failing it loses the caravan (`caravanLost`).

### 13.4 The law

- **Wanted players are refused work** (`refuse_wanted`), except templates marked `wanted: allow` (a fence) and `only`
  (clearing your name: a night on the walls or labour for the town's works; completing one fires
  `QuestEvents.NAME_CLEARED` and calls `LawService.pardon`, which clears the warrant in that town).
- **Wanted posters**: the board shows, beside the offers, everyone the law wants in the town (`LawService.wanted`, online
  or not), worst first, with the offence ("for theft") and what they owe (WANTED, or OUTLAW).
- **Bounty hunting**: outlaws wanted dead or alive (`apprehend`, `lethal`: alive pays the full price, dead the price
  without the alive bonus); bandit camps; and, where `player_bounties` is on, bounties on outlaw players (`hunt`).
- **Thief-taking**: a thief working the market at night (`apprehend` at a `MARKET_STALL`, alive only): beaten down to a
  quarter of their health, the culprit yields instead of dying, drops the stolen purse into your pack, and the watch takes
  them away. More likely where crime has been seen lately.
- Officials offer them: guards, the jailer, the judge, the reeve.

### 13.5 Officials' lines

Short chains (`prerequisites` + `min_title`), given by the official (the first trade listed, never a passer-by; the
council where there is none), and tied to the town's state through `when.requires`:

| Official | Steps |
|---|---|
| Reeve | tithe rolls to a neighbour → a market charter (Known) → the relief fund (Friend, `declining`) or new ground for a spent quarry (Friend, `depleted`) |
| Guard captain | the night patrol → the market thief (Known, `crime`) or the road cleared (Known, `!crime`) → the outlaw chief, dead or alive (Friend) |
| Priest | candles for the altar → alms (Known, `declining`) or a pilgrimage (Known, `!declining`) → the lost relic (Friend) |
| Master builder | stone for the works → timber for scaffolds (Known) → glass and iron (Friend), all while builds are `stalled`; repairs while buildings are `damaged` |
| Lord / steward (town+) | game for the table → the lord's road (Known) → the tax caravan (Friend) |

### 13.6 Standing

Every completion, failure and abandonment is reported as a `StandingService.Deed` (kind from the template's `deed`, or
`hearth:quest/completed|failed|abandoned`; weight the reward's standing or the config's penalties; the giver as the
resident; witnessed). A rescue reports a deed per miner freed. Without the standing service (or with `deeds` off) the
settlement's standing moves directly, as in phase 2. Locks and the board read `StandingService.standing/title` when it is
there; otherwise the title is read from the standing (Outlaw ≤ −60, Distrusted ≤ −15, Stranger, Known ≥ 10, Friend ≥ 35,
Hero ≥ 75, Burgher for a property owner who is known).

### 13.7 Performance

All of it is event-driven: happenings are a bounded list per town; contracts and the town's state are computed once per
board refresh from the record (a pass over the residents, plots and sites); the tracker's new checks run only for the
quests a player holds, once per `tracker_interval` — the fire count skips chunk sections whose palette holds no fire, the
prospect reads a cube of at most 21 × 21 × 15 loaded blocks, ambushes and culprits are raised only when the player is
there. Nothing loads a chunk.

### 13.8 The phase 3 templates (74 new, 129 in all)

| Folder | Templates |
|---|---|
| supply (+19, 32) | logs, raw clay, ore, dressed stone, sand, grain, flour, meat, fish, wool, leather, wax, candles, paper, books, dye, salt (Expanse), produce, specialty; clay and adobe now ask for brick |
| contract (11) | smelter, sawmill, kilns, mill, butcher, looms, chandlery, scriptorium, smithy, a standing order for anything, the guild's large order (Friend) |
| caravan (5) | escort a llama train (steppe, desert, mesa, alpine, tundra) or a mule train (elsewhere), the robbers' camp, the stolen cargo, the lost caravan |
| crime (1) | rob a pack train for a fence |
| law (7) | thief-taking (and where crime is rife), an outlaw warrant dead or alive (Known), a bandit camp, clear your name by patrol or labour, a bounty on an outlaw player |
| disaster (12) | the bucket brigade, the mine rescue, blast repairs, rebuilding, wolves at the pasture, a new site for a spent one, surveys for stone, ore and clay, a declining town's donation, new trade, alms |
| official (19) | the reeve (4), the guard captain (4), the priest (4), the master builder (4), the lord (3) |
