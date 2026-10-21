# VOID Hearth — a world alive with people (living world, part 2)

Status: **design** (2026-10-20), not built. The user's goal, in their words: *"we want to make the world feel alive with
people"*. They approved all eight items below. This document says, for each, what the player sees and does, how it runs
abstractly and embodied, what it costs, how it is saved, how it behaves in multiplayer, which existing systems it uses
and what is new, its content, and the checks a test or tour verifies. §10 lists the screens for Figma; §11 is the build plan: eight lanes in two
batches.

*Kingdom Come: Deliverance II* stays the reference: a Bohemian countryside where the roads carry people, a town has a
day and a week, and the folk you meet have their own business and remember yours. Hearth's rules stand unchanged
(ARCHITECTURE.md §4, §12): **everything is data first, abstract by default, embodied only near players, inside strict
per-tick budgets, and the abstract and embodied versions produce the same results.** Nothing here may make a server of
50 players and 200 settlements drop below 20 TPS.

Read with: ARCHITECTURE.md (the contract), LIVING_WORLD.md (phase 3), SIM.md, FOLK.md, SOCIAL.md, LAW.md, STANDING.md,
ECONOMY.md, QUESTS.md, HUB_UI.md, requests/expanse.md.

---

## 0. What already exists, and the shared pieces this design adds

### 0.1 Built on (do not rebuild these)

| Exists | Where | Used here by |
|---|---|---|
| Sim caravans: surplus between towns, `CARAVAN` events, `CaravanView.positionAt`, escort/rob/lose | `sim/trade/Caravans`, `HearthWorld` | §1 (along real roads, with carts), §3 (tax, raids) |
| Caravan parties on the road (controller folk, pack animals, noSave) | `npc/caravan/*`, `Folk.Controller`, `EntityNoSaveMixin` | §1 generalises them into every kind of traveller |
| Embodiment manager, caps (48 a town, 400 a server), spawn 96 / fold 112 | `npc/embody/*` | every section |
| Behaviours other modules teach folk, priority bands, `FolkHandle.say/moveTo/lookAt` | `NpcService`, `BehaviourRunner` | §2, §3, §4, §5, §8 |
| Escorts (`follow`, `embodyNear`, pinned until the escort ends) | `npc/service/Escorts` | §1 (residents on the road), §8 (companions) |
| Calendar: market day per town, festivals, weddings, funerals, lord's court; who attends; venues | `social/calendar`, `social/life` | §2, §3, §4 |
| Bells (dawn, noon, dusk), market stalls and festival dressings (ledgered blocks), musician, tavern dice | `social/life`, `social/folk`, `social/dice` | §2, §3, §6 |
| Families: weddings, births, deaths, funerals, ageing (children grow up after 48 days) | sim clock, `social/life/FamilyLife`, `Matchmaker` | §4 |
| Officials (reeve, captain, priest, master builder, lord, steward, judge, jailer) | `social/office` | §3, §4, §8 |
| Law: witnesses, guards, warrants, jail, pillory, outlaws, stolen marks, locks, robbery reports | `law/*` | §1 (bandits), §3 (trials), §5 (outlaws shunned), §8 |
| Standing: personal memories, town standing and titles (Burgher, Hero, Outlaw), gossip along approximate roads, charisma and menace, speech checks | `standing/*` | §1 (messengers carry gossip), §4, §5, §8 |
| Economy: purses, shops with stock and till, property and deeds, businesses, public works, bank deposits, services (inn, bath, stables) | `economy/*`, `social/service` | §6 (stall wares), §7 (toll, inns), §8 |
| Quests: happenings from events, escorts, ambushes, robbers' camps, officials' lines (incl. the lord's road and tax caravan) | `quest/*` | §1, §3, §8 |
| Repairs and fire: damage against blueprints, bucket brigade, rebuilding | `repair/*` | §3 (fires) |
| Gatherers: foresters fell real trees, miners and quarriers cut excavations, herders shear and milk, fishers fish, haulers carry | `npc/work/*` | §6 |
| Roads inside towns, `highway` arms beyond 1.9 core radii, bridges, wayside shrines kit | `plan/road`, `build/archetype/infra`, `build/kit/station/Shrines` | §1 |
| Archetypes for small places: hostel, inn, tavern, caravanserai, caravan yard, toll gate, watch house, hermit hut, shrine, monastery, hunters' lodge, fishery, dock, farmhouse, barn, pasture, round house | `build/archetype/*` | §7 |
| Expanse: `LandClaims`, `ExpanseSights.greatMines`, `ExpanseUnderground.sampler(level).water/at`, sites (knoll, summit, pass, wood_edge, border), pass refuge stood down for Hearth | `requests/expanse.md` | §1, §6, §7 |

Open items from ARCHITECTURE.md §14 this design closes: pastures not stocked (§6), carts (§1, §6), a caravan master who
rides along (§1), an escort that is a resident (§8), `HearthWorld.roads(...)` for gossip (§1), the first-snow event
(§3), `UndergroundProbe.waterY` from Expanse (§6), the Great Mine reopened (§6), the pass hospice (§7).

### 0.2 Shared pieces (new, used by several sections)

- **Wayfarers.** People outside a settlement's embodiment radius. Every one is a **journey record** (abstract: kind,
  route, depart and arrive ticks, stops, party seed, the residents in it if any) and, near a player, a **party** of folk
  (controller folk for strangers; real residents, embodied away from home, for patrols, pilgrims, road crews, market
  goers and the tax collector). Where a journey is at time *t* is a pure function of the record, so a party always
  appears exactly where the books say. `npc/caravan` becomes one kind of this (§1).
- **Away.** A resident on the road, with a companion, or at a remote work site is `away` (new `Settlement.setResidentAway`):
  left out of their town's abstract labour and production (they still eat and age), back when the journey ends. Same
  contract as `setResidentEmbodied`.
- **Barks.** One-line remarks a folk says aloud (`FolkHandle.say` to players within 16), chosen from data
  (`data/<ns>/hearth_dialogue/barks/*.json`, the dialogue template conditions plus `stimulus`, `role`, `kind`), with
  cooldowns per folk (60 s), per player per town (one every 4 s) and per line (10 min), so towns talk without spam.
- **Scenes.** A staged gathering: a venue (from `Venues`), roles (principals, officiant, crowd), a timeline of beats
  (walk, stand, pose, bark, sound, dressing), an abstract outcome rule and the interventions a player can make. The
  social module's `GatherBehaviour` is the seed; §3's scene runner generalises it and §2, §4 and §8 stage their own
  through it.
- **Remote work sites.** Places in the territory beyond the settlement's embodiment radius where residents work by day
  (a pasture 300 blocks out, a charcoal clamp in the woods, the Great Mine's pithead, a fishing lake): embodied when a
  player is within 96 of the site, whether or not the town is (§6).
- **Posts.** Small fixed-programme settlements in the wild (§7): ordinary settlement records with `kind() == POST`,
  sited by the atlas, built by the planner's `plan/wild`, stepped rarely, never growing past their programme.

### 0.3 Priority bands for the new behaviours (NpcService, FOLK.md §13)

| Band | New behaviours |
|---|---|
| 20–49 (leisure, routine) | courting walks 30, personal schedules of named characters 35, children's games 25, porch gossip 22, tavern nights 45 |
| 50–99 | wash day 55, mass and services 85, night watch rounds 70 (guards on night duty), stall wares 90 (existing stall keeper) |
| 100–199 | remote work (drove, clamp, commute) 110, road crew 120, servant duties 130, apprentice at the master's side 105 |
| 200–239 | crowd reactions: hail a hero 205, guard remark 208, gawk at a fight 215, shun an outlaw 220, scatter from a blade 225, scene roles 230 |
| 240–299 | companion 280 (as escort), bandit ambush 290 (controller folk), brawl 260, raid defence (guards' fight 299 exists) |

### 0.4 Dialogue order bands (DialogueProvider is unchanged; these are just the numbers lanes use)

0–49 existing small talk and standing; **50–79 named characters and their threads**; **80–99 rhythm and town events**
("When is mass?", "Who is being married?"); 100–199 trade and services, with **150–179 travel, tolls, household and
companions**; 200–299 quests, with **260–279 the lord's commissions**; 300–399 Mettle training; goodbye 10 000.

---

## 1. Roads that are travelled

Merchants with carts between towns, pilgrims, messengers, patrols, and bandits on the lonely stretches.

> **Built (lane A1, 2026-10-21)** in `sim/roads/` (books) and `npc/travel/` (bodies). `RoadWorld` lays the network per
> 1024-block region from the atlas's sites alone (a relative neighbourhood graph, ≤ 4 roads a place), `RoadRouter` finds
> each way (two-pass A*, carts' grade, bridges, fords, switchbacks; an end shut in on the coarse lattice is searched from
> the other end, then finer), `RoadSegments` cuts it into `infra/road` / `infra/bridge` plots. Historic roads are written
> by world generation and, into land generated before them, by `RoadApplier` under the applier's budget with edit
> safety: a plot across somebody's work is refused and `LiveRoads.detour` lays a detour round the whole obstacle (built
> at once, saved in the road book); a cut beside standing water keeps its bank. `LiveRoads` begins and builds live roads
> with the towns' builders (`labour_share`), stone and players' coin ("pay toward the road" at the reeve or steward).
> `Traffic` keeps journeys (place a pure function of the record; caravans keep the sim's times; residents away),
> `Bandits`/`Band` the bands (danger from loneliness and cover, patrols lower it; robbery odds against guards; deaths
> rare, `bandits.kill_chance` 0.05). `Parties` embodies parties, carts (`hearth:cart`, registered by `Carts`), camps,
> bands with the stranger demand and road crews within 96 of a player, folding beyond 112, caps by `LoadCaps`.
> `/hearth roads stats|near|journeys|send|band|build`; tour `-Dhearth.tour=roads`; harness `RoadHarnessTest`
> (`-Dhearth.dev.roads=N`); perf gate `TrafficBenchTest`. Measured: traffic books 0.003 ms a tick avg, parties 0.05 ms
> avg (5.8 ms worst tick, a party spawning), road writing ≤ 1.8 ms a tick; bench with full roads and 50 players 0.006
> ms a tick avg, players' pass 0.18 ms. Requests in `docs/requests/lwA1.md`, Atlas in `docs/requests/atlas-roads.md`.

### 1.1 What the player sees and does

- **A road network.** Every settlement (and post, §7) is joined to its neighbours by a road Hearth lays and builds: a
  **king's road** (5 wide, metalled with gravel and packed earth, verges, milestones, a wayside cross or shrine every
  400–600 blocks, a signpost at every junction naming the towns each way and how far) between towns and cities, and a
  **cart track** (3 wide, earth and coarse dirt with grass between the ruts) to hamlets, posts and workings. Bridges at
  rivers (`infra/bridge`), fords where the river is shallow, switchbacks on mountain flanks, the pass on Expanse's
  saddles. Roads are dark at night on purpose (LIGHT rule: lanes and highways carry no lamps).
- **Roads being built.** A road not yet finished ends in a **road crew** at its head: builders of the nearer town
  digging the bed, setting stones, a cart of gravel, a foreman. A player can pay toward a road ("I'd like to pay toward
  the road to Coldbrook") at the reeve or the steward, and watch it reach the next town sooner.
- **Traffic.** On any road a player meets, by day: **merchants' wagons** (a covered four-wheel wagon with an ox or two
  horses, the caravan master riding beside it, two guards), **carters** (a two-wheel cart and a mule between a hamlet and
  its parent town), **pedlars** with a handcart going from market to market, **pilgrims** on foot with staffs bound for a
  monastery or a cathedral, **messengers** on horseback carrying news, **patrols** of two or three guards walking out to
  the edge of their town's land and back, **market goers** with baskets and a cart of produce on market days, settlers
  leaving for a new hamlet. At dusk travellers stop at a wayside inn (§7) or make camp by the road (a campfire, a
  bedroll, the animals hobbled).
- **Talking to travellers.** Right-click: a conversation with a stranger (no name, a description: "a pedlar from
  Ashbrook"): where they are bound, what they carry, news from where they came from, the state of the road ahead
  ("bandits past the ford, they say"); a pedlar opens their stock; a messenger tells you what news they carry (and a
  thief can pickpocket the letter).
- **Bandits on the lonely stretches.** Where a road runs far from any town and nobody patrols it, a **band** (3–7) holds
  a camp off the road in cover and lies in wait at dusk and by night. When a player passes their spot they step out:
  *"Your purse, or your life."* Pay (a share of the coin carried), talk your way past (speech check), frighten them off
  (menace: plate armour and a drawn blade scare small bands), fight, or run. Their camp (a tent, a fire, a chest of loot
  marked stolen) can be found and cleared. They rob caravans too: the player may come on a robbery under way and turn
  it, or find the wrecked cart afterwards.
- **What it changes.** Roads carry the sim's trade (caravans follow them; distance by road sets travel time), gossip
  (news reaches towns along the roads, now really carried by messengers), bounties (spread by road distance), and the
  danger the player feels at night.

### 1.2 Abstract

- **The network** (`sim/roads/RoadNetwork`, implementing the new api `RoadNetwork`):
  - **Nodes**: every atlas site (historic, from `Atlas.plan` steps 1–2 only — site and age, never the history, so a road
    never needs a neighbour region's history), every live settlement, every post (§7).
  - **Edges**: each node to its three nearest neighbours within 2 400 blocks, keeping an edge only if no third node lies
    inside the circle on it as a diameter (a Gabriel graph: no needless triangles), plus each node's nearest node if it
    has none. Class: **king's road** when both ends are towns or older than 200 days at day 0, else **track**. A track
    is re-issued as a king's road when both ends reach town tier (the plan module's `replaces` idea, for roads).
  - **Route** (`sim/roads/RoadRouter`, pure, off the server thread): A* on a 4-block lattice over the terrain probe in an
    ellipse 1.35 × the straight distance, at most 200 000 states; cost = length + climb (graded to 1 in 6 for carts,
    switchbacks beyond) + water (a bridge 30 a block up to 24 wide, a ford where the bed is within a block, lakes and
    sea refused) + a pull toward Expanse pass and rim sites over ranges + a push away from other settlements' cores and
    structures (`StructureAvoidance`); the town ends join the town's nearest `highway` arm end
    (`SettlementPlanner.exits`, new) or, failing that, its territory edge. Smoothed (Chaikin), graded with
    `plan/road/Profile`, then cut into **segments of at most 48 blocks**, each an ordinary `hearth:infra/road`
    (`kind: highway|track`, `country: true`) or `hearth:infra/bridge` spec. Deterministic from the seed and the probe.
  - **Day 0.** An edge whose two ends were both founded 30 days or more before day 0 was built in history: world
    generation writes its segments (the `hearth:roads` feature, same step as `hearth:settlement`). Other edges are
    **planned** and built live.
  - **Cost.** Historic routes are computed by whichever world generation worker first needs one (as atlas plans), cached
    LRU (`roads.route_cache` 512); live routes on a `hearth-roads` thread, at most two a second.
- **Building** (live edges): an edge keeps two cursors, built from each end. On `DAY_PASSED` each end town gives
  `roads.labour_share` (0.1) of its idle builder labour (`Settlement.takeBuilderLabour`) and the goods (gravel or stone,
  a unit a block for king's roads; tracks cost labour only) to its half; the lord's seat pays for king's roads in its
  domain from its treasury; players' gifts buy goods and labour at the town's prices. A segment whose labour is done is
  `BUILT`; the chunk applier writes it when its chunks load, under its own budget slice.
- **Traffic** (`sim/roads/Traffic`, world data): journeys of each kind (§1.7) generated on the sending town's
  `DAY_PASSED` from its state (surplus, kin, market days via the new `SettlementCalendar`, feasts, priests, guards,
  news to carry). Each journey: route (edge ids), depart tick, pace (walking 0.9 blocks a second, carts 0.8, riders 2.4),
  stops (dusk to dawn at the inn or post nearest its position, else a camp), arrive tick. Arrivals are a queue checked by
  its head. At most `traffic.max_journeys` (512) world-wide; when full, the least important kinds are not sent.
- **Sim caravans** keep their books in `sim/trade/Caravans`: the route and travel time now come from the network (road
  length and the edge's roughness instead of the straight line), they ride in wagons, and the departure-time **risk**
  becomes the bandit danger of the edges they cross (below), still rolled at departure so the sim stays the authority.
- **Bandits** (`sim/roads/Bandits`, world data): each edge has a **danger** 0–1: its loneliness (blocks beyond 500 from
  any settlement or post), cover (woods, gorges from the survey's land classes), minus patrols (each patrol pass lowers it
  for 3 days), minus bands broken by players, plus decline (households leaving a declining town may take to the roads:
  `PROSPERITY_CHANGED` falling adds a band nearby with a chance). A **band** (at most one per three dangerous edges, 32
  world-wide): id, home edge, camp position (40–120 blocks off the road in cover), strength 3–7, morale, loot (goods and
  coin). Each day a band may waylay one journey on its edge: odds by strength against the party's guards; outcomes
  robbed (caravan `ROBBED`, cargo to the band's loot), driven off, or — rarely (`bandits.kill_chance` 0.05) — a traveller
  killed (a resident dies: permadeath). A band that loses its strength breaks; a band grown strong may raid a weak town
  (§3).
- **Gossip** goes by road: standing's `GossipRoutes` reads `RoadNetwork.roadDistance` instead of its four-nearest-
  neighbour approximation, and every gossip item and notice sent to another town is also a **messenger** journey whose
  arrival is the delivery time standing already computes (the standing queue stays the authority; the messenger is what
  it looks like). A courier quest's parcel rides with one too.

### 1.3 Embodied

- **Parties** (`npc/travel/Parties`): every 40 ticks, for each player, the road segments within 160 blocks (a 256-block
  cell index of segments) give the journeys on them; a journey whose position is within the spawn radius (96) of a
  player in entity-ticking chunks is put on the road (one party every two seconds server-wide), folded beyond 112.
  Strangers are controller folk (as caravan parties today); residents are embodied away from home through
  `NpcService.embodyNear` and flagged away. A party walks its route's points (waypoints 8–16 blocks apart; vanilla
  pathfinding between them on the graded road), keeps formation (guard in front, wagon, the rest, guard behind), stops
  at its stop for the night (the inn's tables and beds, or a camp: a campfire block and bedrolls placed as a ledgered
  dressing and taken down when it folds or leaves), and turns to face a player who comes near. Stuck where nobody sees,
  it is moved along.
- **Carts** (`npc/travel/cart`, entity `hearth:cart`, noSave): a handcart (pushed), a two-wheel cart (mule or ox), a
  four-wheel covered wagon (two horses or oxen). A trailer: it follows its draught animal at hitch distance, yaw
  interpolated, pitched by the ground under its axles, wheels turning with distance travelled; its load drawn from its
  cargo (logs, sacks, barrels, crates, stone). Model and renderer in code (cuboids), one 64×64 texture per kind,
  authored as pixel art. Draught animals carry the existing `hearth.caravan` tag (never saved, left alone by herders).
  Carts do not carry players (later, maybe).
- **Road crews**: when a player is within 96 of an edge's cursor, two to four builders of the nearer town (away) work
  the next segment's steps exactly as builders do (the construction contract: one counter per segment, steps in order,
  `advanceConstruction`), with a gravel cart and a foreman's bark.
- **Bandits**: a band's ambush spot is its edge's loneliest point near its camp. With a player within 48 of it at dusk
  or night, the band steps out (the leader in front, the rest fanning out); the leader opens the stranger conversation
  with the demand. Pay: `EconomyService.charge` of a share of coin carried (25 % by default, at most 2 crowns, more for a
  rich-looking player), the band lets you by and remembers you paid. Persuade, intimidate (menace and armour weigh
  heavily), bribe low: speech checks (`StandingService.attempt`). Fight: bandits are folk with swords, bows and leather;
  they flee below a third of the band. Run: they chase 40 blocks. Killing bandits is no crime (`defense:bandit`, +3 a
  head in the nearest town, more for the leader; fame), their drops are their loot (stolen-marked from whoever they robbed).
  The **camp** is embodied when a player is within 96: tent (wool and fences), campfire, loot chest (the band's loot as
  items, marked stolen), sleeping bandits at noon. A caravan robbery due to happen while a player is near plays out live;
  if the player wins it, the caravan arrives (`escortCaravan`), as escorts do today.
- **Patrols** (residents, priority 120 as road work): guards from a town of three guards or more walk each of its roads
  to the territory edge and back at dawn and in the afternoon; they greet travellers, question strangers at night, and
  fight bandits they meet.

### 1.4 Cost

| | Budget |
|---|---|
| Route search | off-thread; ≤ 60 ms p90 per edge (≈ 1 500 blocks), ≤ 2 live edges a second; historic on world generation workers, cached |
| Road building (abstract) | ≤ 5 µs per edge per sim day on `DAY_PASSED`; nothing per tick |
| Road writing | inside the chunk applier's 3 ms / 6 000 blocks a tick (roads take at most a third of it) |
| Traffic records | ≤ 0.05 ms a tick on average: the arrivals head, plus every 40 ticks per player a cell lookup (≤ 20 µs a player); ≤ 512 journeys × ~96 bytes |
| Parties | ≤ 16 parties, ≤ 120 traveller entities server-wide (folk, animals, carts), ≤ 0.6 ms a tick in all (folk think every 4–12 ticks by distance, as residents) |
| Bandits | ≤ 32 bands × ~200 bytes; one roll per band per sim day |
| Per town | ≤ 20 µs per sim day to send its journeys |
| Per traveller | 0 per tick abstract; ~5 µs a tick embodied |

Caps shrink with MSPT like the clock's budget (`high_mspt`): above 60 % of it, no new parties; above it, the farthest
fold.

### 1.5 Persistence and multiplayer

- World data `hearth:sim/roads` (edges, classes, state, cursors, funds; live routes stored delta-encoded; historic routes
  recomputed from the seed, with a fingerprint of the road config and probe so a changed config keeps the stored routes of
  edges already begun), `hearth:sim/traffic` (journeys, bands, camps' ledgers). Road segments use the chunk ledger
  (`hearth:sim/applied`) under a pseudo-settlement id per edge (`1 << 61 | edge`). Parties and carts are never saved.
  A resident on a journey carries `extra("travel").away` (journey id, until).
- Multiplayer: one world, one caravan in one place; a party is embodied once if any player is near; a bandit band
  confronts the first player within 8 of the leader, the rest may join in; payments and deeds are per player; anyone may
  fund a road.

### 1.6 Built on / new

Uses sim caravans, npc caravan parties (generalised into `npc/travel`), `Folk.Controller`, escorts and `embodyNear`,
law (robbery, assault and murder of travellers, stolen marks, `LawService.report`), standing (deeds, gossip routes,
speech checks, menace), quests (escort, ambush, robbers' camp, the lord's road — now about a real edge), economy (charge,
pedlars' stock is the social module's visiting wares), build (`infra/road`, `infra/bridge`, `Shrines` for wayside
crosses), plan (`highway` arms), Expanse (`LandClaims` answers over road strips, so hedgerows and sights keep off them;
pass and rim sites pull routes). New: the network and router, traffic and bands, travel parties and carts, stranger
conversations, country road dress (ruts, verges, milestones, signposts with sign text, wayside crosses, troughs,
boundary stones at domain borders), and a request to Atlas to draw roads (`docs/requests/atlas-roads.md`).

### 1.7 Content

| Kind | Party | Sent when | Abstract effect |
|---|---|---|---|
| Merchant caravan | wagon, master, 2 guards | the sim's caravans (existing rule) | cargo between towns |
| Carter | cart, carter (resident) | daily between a hamlet and its parent, kin first | the hamlet's shipments (existing kin preference) |
| Pedlar | handcart, pedlar (stranger) | wanders a circuit of 3–5 towns timed to their market days | sells the visiting wares at each market |
| Pilgrims | 2–6 residents, staffs | feast days; to a monastery, cathedral or shrine within 3 000 | away 2–8 days; alms to the destination's treasury |
| Messenger | rider (courier resident, else stranger) | every gossip item and notice sent between towns; courier quests | the standing queue's delivery |
| Patrol | 2–3 guards (residents) | dawn and afternoon, towns with ≥ 3 guards | lowers the danger of its edges |
| Market goers | 2–8 residents, a cart of produce | a neighbour within 900 holds market | coin and goods at the market (existing visiting trader books) |
| Settlers | the departing settlers, carts | a daughter hamlet is founded (existing) | none new (the sim moves them) |
| Road crew | 2–4 builders, gravel cart | an edge under construction near a player | the edge's labour (abstract either way) |
| Bandits | 3–7 strangers | lonely, unpatrolled edges | waylays journeys; raids (§3) |
| Tax collector, wedding guests, troupe, dignitary | — | §3, §8 dispatch them | — |

Barks (samples; each kind 12–20 lines in `barks/road.json`, per culture where it matters): pedlar *"Needles, ribbons,
salt from the coast — cheaper than in town!"*; pilgrim *"Pray for us at the shrine, if you pass it before we do."*;
messenger *"Out of the way — word for the reeve of Coldbrook!"*; carter *"Easy, girl... the ford's high today."*; patrol
*"Road's quiet as far as the old oak. Keep your eyes open after dark."*; caravan guard *"Keep your distance from the
wagon, friend."*; bandit leader *"Nice boots. Nicer purse, I'd wager."*, *"Lay it on the stone and walk on."*, fleeing
*"Not worth it! Scatter!"*; traveller at camp *"Sit, warm yourself. Roads are no place to be alone at night."*

### 1.8 Acceptance

- Unit: `RoadRouterTest` (synthetic river → a bridge or ford; cliff → switchbacks or a detour; every step graded ≤ 1 in 6
  for carts; same seed, same route; keeps out of other towns' cores and off structures); `RoadNetworkTest` (every node
  reached, no crossing edges, at most four edges a node, classes by age); `TrafficTest` (position monotone along the
  route, stops at night, a caravan's party position equals `CaravanView.positionAt`, away flags set and cleared);
  `BanditsTest` (danger falls with patrols and rises with loneliness; robbery odds against guards; a broken band frees
  its edge).
- Dev harness `-Dhearth.dev.roads=<regions>`: builds the network over N×N regions, logs edges, ms per route (p50/p90),
  segments written and refused, and one live edge built over 10 sim days.
- Tour (`HEARTH_LW_TOUR=roads`): a king's road between two towns from 60 blocks up; a signpost naming both; a covered
  wagon with guards; pilgrims at a wayside cross; a road crew at the head of an unfinished road; a bandit ambush at dusk;
  a camp by the road at night.
- Perf gate: 50 bot positions spread along roads (harness) → traffic and parties ≤ 0.6 ms a tick p99, no more than the
  entity caps.

---

## 2. A visible daily rhythm

Bells and Sunday mass, market-day crowds, tavern nights with music and dice, children playing, washing at the river,
the night watch with torches.

**Built** (lane A2, branch `hearth-lwA2`): `social/rhythm/*`, documented in SOCIAL.md §11; what it added outside its
package and asks of others is in `requests/lwA2.md`.

### 2.1 What the player sees and does

A temperate town's day (other cultures shift it: siesta and late suppers in the south, early nights in the north; the
culture data says which):

| Hour | What happens |
|---|---|
| 04:00 | the bakers at the ovens, smoke from the bakery chimney |
| 06:00 | dawn bell; shutters open (§5); the night watch goes home; chimney smoke for breakfast |
| 07:00 | work; the market opens on market day; children to school where there is one, else chores and play |
| 08:00–12:00 | **wash day** (the day after the rest day): folk at the river bank, the well or the fountain with bundles, laundry lines in the yards |
| 12:00 | noon bell; dinner (chimney smoke); workers eat outside the workshop |
| 15:00 | children's games in the square and the lanes |
| 17:00 | work-end bell; the tavern fills; neighbours talk at doorsteps and the well |
| 19:00 | supper smoke; parents call the children in at 20:00 |
| 21:00 | **curfew bell**; shutters close; doors lock (the law's locks already do); the **night watch** sets out with torches |
| 21:00–23:30 | **tavern night**: a musician, songs, toasts, dice (existing), arm-wrestling at a table, a quarrel now and then; closing at 23:30, the innkeeper's hand bell, the last drinkers weaving home |
| 22:00–05:00 | the watch calls the hours (*"Eleven o'clock, and all's well!"*), challenges anyone abroad (*"Who goes there?"*) |

The week: **market day** (exists: stalls and crowds; now also visitors from the neighbouring towns, §1), the **rest day**
(no work but the innkeeper's, the guards', the herders' milking and the bakers' early bread; **mass** at 09:00 — the bell
peals for two minutes before; the priest at the altar, the congregation in the pews, a sermon in barks, a chant; then
folk linger at the church door; the afternoon is for walks, games on the green, courting couples (§4)), and **wash day**.
The year: festivals (exist), and now **first snow** in the cold cultures (closes ARCHITECTURE.md §14's open item: winter
dressings go up, folk stay in by the fire, children snowball).

The player can attend mass (+1 standing the first time a week, `social:attended_mass`; the priest greets regulars), buy
the tavern a round (the innkeeper; a few bits a head; `social:bought_a_round` +2, folk raise their mugs to you), sit in
on the music, play dice (exists), and be challenged by the watch at night.

Every culture's version is data (`hearth_social/rhythm/<culture>.json`): the rest day's name and service ("mass",
"the blessing", "evening prayers" — neutral wording, the service is the town's gathering at its chapel or temple), wash
places preferred (river, well, fountain, lake), the watch's calls, bell times, the tavern's hours and instrument.

### 2.2 Abstract

Rhythm is how the day looks; the sim's books are unchanged. The sim's daily rates are already a week's average, so the
rest day costs no production (this keeps `RealGrowthTest`'s growth targets and the economy's tuning intact). The calendar
gains rest and wash days (`TownCalendar`, pure) and is published through the new api `SettlementCalendar`
(`HearthServices.calendar()`), which §1 reads to send market goers and pedlars and §3, §4, §8 read for their own days.
Mass offerings (a bit a head attending) go to the treasury once per rest day (`SocialLedger.feastDay`-style guard).

### 2.3 Embodied

- **Schedule**: `npc/routine/Schedule` takes the day's calendar: the rest day turns work blocks into leisure for every
  trade but the listed ones; the night watch's hours become 21:00–06:00 for a third of guards (exists) with rounds.
- **Mass** (`social/rhythm/ServiceBehaviour`, 85): attendees by `Agenda` (60 % of adults, children with their parents,
  elders always, the pious always, the lord in front): walk to the chapel or temple (`ALTAR`, `SEAT`s), sit, stand at
  the chant, the priest at the altar (pose pray/talk) with sermon barks every 20 s; the chant is a note-block sequence
  per culture; afterwards porch gossip (22) for 15 minutes.
- **Wash day** (`WashBehaviour`, 55): one adult in four households, 08:00–12:00, to the nearest wash place: water within
  48 blocks of the town's bounds with standable bank, found by a one-time scan of loaded blocks when the town is
  embodied (cached), else a well or fountain POI; kneels at the edge (pose tend, a white wool bundle in hand, splash
  sounds and particles). **Laundry lines**: a dressing in yards (fence posts with white and undyed banners on string),
  ledgered and taken down at 17:00.
- **Children** (`PlayBehaviour`, 25, children only, by daylight): tag (exists), hide-and-seek (one counts at a wall, the
  others hide round plot corners), hoop and stick (a stick in hand, running loops), skipping stones at water, chasing
  the chickens of a pen, play fights with sticks, watching the smith; called home at 20:00 by a parent's bark.
- **Tavern night** (`TavernNight`, 45, 18:30–23:30 at inns and taverns): the musician (exists for festivals) plays
  nightly; drinkers at the `TABLE`s with mugs (a honey bottle held), toast barks and cheers when the music ends, two at a
  table arm-wrestling (pose), dice (exists), the innkeeper going round; at closing the last drinkers walk home with a
  sway (small lateral offsets on their path).
- **Night watch** (`WatchRounds`, 70): a watchman's round from the gate to the square and along the lanes, a torch held,
  and **real light where he walks**: an invisible `minecraft:light` block (level 12) at his head, moved as he moves —
  placed only in air, removed behind him, ledgered so a crash cleans them on the next embodiment; one per watchman,
  at most 8 a town. Hourly calls; a challenge bark to players crouching or abroad at night; a watchman who sees a crime
  is a guard (the law's).
- **Bells** (`Bells`, extended): the rest-day peal, the curfew bell, the fire and raid alarm (exists through npc), the
  funeral toll (§3).
- **Chimney smoke**: at meal times, the homes whose family is in (embodied) send campfire smoke from the top of the column
  above their `HEARTH` POI (found once per plot from the heightmap): at most 24 chimneys a town every 40 ticks.

### 2.4 Cost

Per embodied town: the rhythm director every 20 ticks (a clock check, ≤ 20 µs); behaviours' `wants()` are cached lookups
(the day's agenda); watch lights ≤ 8 block changes a second; smoke ≤ 24 particle packets every 2 s; wash-place scan once
per embodiment (≤ 4 000 loaded block reads, spread over 20 ticks). Abstract: nothing per tick; ≤ 2 µs per town per day.

**Measured** (lane A2, `RhythmTour` on a 145-soul northern town, an overloaded dev client; `/hearth sim stats`): the
director 0.17–0.27 ms a pass every 20 ticks, of which the steady part (clocks, bells, attendance, light sweep) is
~0.06 ms (≈ 3 µs a tick a town) — over the 20 µs aim; the rest is bounded one-offs: smoke 0.1 ms every 40 ticks at meal
times, the wash-place scan 0.15 ms a pass for ~40 passes per embodiment, laundry 0.7–1.1 ms a pass for ~37 passes on a
wash-day morning (one yard a pass, max ~5 ms). Folk behaviours 3–9 µs a step. Abstract (`RhythmTest.abstractCost`): the
offering 0.5 µs a town a day; a rest day's schedule windows ~36 µs a resident a day (`ScheduleOverrides.overlay`, see
requests/lwA2.md). After a town is let go: 0 watch lights, 0 rhythm dressings.

### 2.5 Persistence and multiplayer

Calendar is computed, not stored. Dressings (laundry, first-snow) in the existing dressings ledger; watch lights in
`hearth:social/lights` (settlement key; positions only, cleared on load). Per player: `extra("social")` of the priest
counts regulars (a small set of player ids). Everyone sees and hears the same day; a round bought pays for everyone in
the tavern.

### 2.6 Built on / new

Uses the calendar, agenda, venues, bells, dressings, musician, dice, schedules, law locks (curfew), standing deeds. New:
rest and wash days, the calendar api, mass, wash day, children's games, tavern nights, the watch with moving light,
chimney smoke, first snow, rhythm data per culture.

### 2.7 Content

Barks (`barks/rhythm.json`): priest's sermon (20+ lines, gentle and culture-neutral: *"Love your neighbour — even the one
whose geese are in your cabbages."*), watch calls by hour and culture, tavern toasts (*"To the harvest!"*, *"To {lord}'s
health — may he pay for the next one!"*), parents calling children (*"{child}! Supper!"*), washers' gossip (*"...and
she said the miller's boy..."*), curfew (*"Bell's gone. Off home with you."*).

### 2.8 Acceptance

- Unit: `TownCalendarTest` (rest day and wash day per culture, every town; market day never on the rest day), `ScheduleTest`
  (no work on the rest day for every trade but the listed; the watch's hours), `RhythmDataTest` (every culture has a file
  or the default; every bark category referenced exists).
- Tour (`HEARTH_LW_TOUR=rhythm`, a held-embodied village, `/time set` per shot): mass (≥ 60 % of the chapel's `SEAT`s
  filled, the priest at the altar); wash day at the river (≥ 3 washers, laundry lines up); children's games at 15:00;
  tavern night (musician playing, ≥ 2 tables in use); the night watch (block light ≥ 10 at the watchman's feet in a dark
  lane); chimney smoke at dusk.
- Harness: after a held town is let go, zero watch lights and zero rhythm dressings remain in the world.

---

## 3. Town events you stumble into

Weddings, funerals, trials and pillory crowds, fairs and tournaments, fires and rebuilding, the lord's tax collector,
bandit raids.

> **Built (lane B1, 2026-10-21)** in `social/events/` (documented in SOCIAL.md §12), with `law/justice/Trials` and
> `repair/fire/FireLeash`. Abstract: `EventDirector` plans each town's day on `DAY_PASSED` from its `TownFacts`
> (`FactsReader`: people, places, calendar, the domain's seat by road — `Domains` — and the boldest band in reach —
> `RaidRules` over lane A1's bands) through the pure `EventScheduler` (≤ 1 major, ≤ 3 minor, an outcome seed each; rules
> in `hearth_social/events/*.json`, one a kind); an event is decided by `Outcomes` (pure) and applied by `EventEffects`.
> Embodied: `Scenes` stages it when its time comes — `SceneCast` roles, `SceneRoleBehaviour` (230) orders per beat of
> the data timeline, the crowds' `spectacle`, dressings through the ledger (`EventDressings`, prefix `events:`), and a
> `SceneDirector` per kind (fire, raid, fair and archery, joust and melee, funeral bier, pillory and gallows, tax pig,
> beasts, wedding dance) — and resolves it by the same rule with the players' `Interventions`. Tax: the bailiff's
> `TAX_COLLECTOR` journey (A1) is dispatched every 14 days and the tax day held on his arrival; takings reach the seat
> when the journey ends, lost if waylaid. Live fires (auto: single player) on the leash; the books' fires leave a
> `BurntShell` the repair builders mend. Conversation options 82–99 (`EventDialogue`); the F6/F7 lines go to the action
> bar and chat until designed. `/hearth events status|book|force <kind>|stage <scene>|shell` (also `/hearth social
> force <kind>`); tour `-Dhearth.tour=events` / `HEARTH_LW_TOUR=events` (`gametest/EventsTour`). Tests `EventRulesTest`,
> `SceneTimelineTest`, `EventAgreementTest`, `FireLeashTest`, `EventsBenchTest`. Deviations and requests (quest kinds,
> the build module's `burnt` flavour, a waylaid journey's player) in `docs/requests/lwB1.md`. Measured: pure planning
> 0.7 µs a town-day; in game a town's day 27 µs warm (over the 20 µs aim); scene passes 0.31 ms avg; worst scene start
> 21 ms (a raid's first raiders); the leash 0.14 ms a pass. The tour's casts are limited by the 48 folk a town embodied:
> the pillory drew 1–3 folk within 10 in 12 s, not the 8 asked (see SOCIAL.md §12).

### 3.1 What the player sees and does

| Event | Seen | The player can |
|---|---|---|
| **Wedding** (exists as data and a gathering) | the banns on the board; on the day a procession from the bride's or groom's home to the chapel (music in front, garlands on the doors), the ceremony, a feast at the inn or festival ground with dancing in pairs and a musician | attend (+1, exists), give a gift (coin or an item: memory with both), dance (stand in the ring at the feast), be invited if a Friend of either family |
| **Funeral** (exists) | the bell tolls; four kin carry the bier from the house to the cemetery, mourners in black, the priest's words, a fresh grave (mound and candles, kept 7 days), black cloth on the house door for 7 days | attend, pay for the burial of the poor (+3), confess to the widow if it was you (§4) |
| **Trial** | on the lord's court days at the courthouse: the judge on the high seat, the accused (a resident caught stealing, brawling, poaching, in debt; a caught bandit), witnesses, a crowd; the verdict | testify (a speech check for or against, using what you saw: witnessed deeds), pay the fine for the poor accused, demand your own trial instead of a guard's sentence (law) |
| **Pillory crowd** | the convicted in the pillory for two hours: a crowd jeering, children throwing rotten vegetables (item particles) | throw, or (crime) free them, or pay their fine; when it is you, folk jeer at you (exists) |
| **Execution** (rare: murder) | dawn at the gallows; a drum, the bell, the crowd; the scene ends as the condemned is led up (the resident is removed off-screen; a notice "hanged at dawn") | attend; plead for mercy (a hard speech check to the lord, if present) |
| **Fair** (town and city, once a festival year, two days) | three times the stalls, pedlars and caravans from far towns (sent by §1), a juggler and a fire-eater on the stage, an **archery contest** | enter the contest (a fee), buy rare wares |
| **Tournament** (towns with a lord and a tilt yard; once a festival year and at the lord's wedding) | the gentry joust at the tilt yard (two mounted folk charging along the barrier; outcomes rolled; a herald's barks), a **melee for all comers** | enter the melee (duels to yield: a champion folk in good gear, stops at a quarter health, nobody dies), win a purse, a horse and the lord's favour (§8) |
| **Fire and rebuilding** | a house or barn burns; the bell, the bucket brigade (exists); a burnt shell for days; the family lodged with kin; then the builders rebuild it (exists as repairs) — or, abstractly, you come back to a blackened plot and then to a new house | fight the fire (exists), give to the rebuilding (works), take in the family? (no) |
| **The lord's tax collector** | every 14 days a bailiff with two guards and a cart rides from the lord's seat to each town of the domain; the reeve hands over the coin at the town hall while folk grumble; a poor household that cannot pay has a pig led away | pay a poor family's tax (+5, a strong memory), rob the collector on the road (a crime, but the poor love you: a split deed), escort the takings back (the lord's line) |
| **Bandit raid** | at night a band rides in from the road: the alarm bell (exists), folk hide (exists), guards fight, bandits carry sacks out of the storehouse and torch a barn | defend the town (`defense:raid_repelled` +15–25, fame), track the raiders to their camp after (existing robbers' camp quests) |
| **Topping-out** | when a large building's roof is done, the master builder sets a green bough on the ridge and the crew and neighbours drink to it | drink with them, give the crew a coin (+1) |

Smaller ones, for variety (data): a christening after a birth, the first snow (§2), a cart overturned at the town gate,
a runaway pig chased by children, a travelling troupe playing on the stage, a preacher in the square, a horse fair,
a lost child searched for at dusk (a quest), slaughter day in autumn, the lord's decree read by the crier, a quarrel at
the well (§4).

### 3.2 Abstract

- **Event book** (`social/events/EventBook`, settlement key `hearth:social/events`): on `DAY_PASSED` the town's rules
  (`hearth_social/events/*.json`: chance per day by tier, conditions on the town's state — prosperity, crime, timber
  and thatch share, a lord, the danger of its roads from `RoadNetwork`, the calendar) schedule the day's events: at most
  one major and three minor, each with a time, principals, venue and an **outcome seed**.
- **Outcome rule** per kind, pure: what happens if nobody intervenes (a trial's verdict from the offence and the judge;
  a raid's losses from the band against the town's defence; a fire's plot and how much burns; a contest's winner).
  Abstract towns apply it at the event's time; embodied towns enact it (below) and apply the same outcome unless a
  player intervened, which rolls it again with the intervention's effect. So abstract and embodied agree.
- Effects through existing calls only: treasury (`withdraw/deposit`), stockpile, plots (a fire weathers the plot to a
  new decay flavour `burnt` and the repair/rebuild pipeline restores it, as abandoned houses are rebuilt today),
  residents (permadeath only where the rule says: an execution, a raid's rare death, `events.raid_deaths`), standing
  news, notices, happenings for quests (`fire`, `raided`, `taxed`, `trial`), and `SettlementEvents.TOWN_EVENT` (new).
- **Domains and tax** (`social/events/Domains`, pure): a town's lord holds the settlements whose road distance to the
  seat is under 2 000 blocks and smaller than to any other seat. Every 14 days a bailiff's journey (§1, `TAX_COLLECTOR`)
  goes round; on arrival `tax_share` (10 %) of the treasury above a floor (60 bits a head) is taken and carried back
  (robbable on the way: the existing "tax caravan" line becomes a real journey).
- **Raids**: a band (§1) whose strength exceeds a town's defence (guards × 2, walls +4, a keep +6) within 1 200 road
  blocks of its edge may raid by night: up to 10 % of the stores and a share of the treasury, a barn burnt (abstract
  fire), rarely a death; the band grows. Walls and guards make raids rare; hamlets on lonely roads suffer most.
- **Trials of residents**: petty crimes among residents arise abstractly from the town's state (poverty, feuds from §4,
  crime pressure): one or two a fortnight in a poor town, none in a contented one; tried on court days.

### 3.3 Embodied

- **Scene runner** (`social/events/Scenes`): runs a scene in an embodied town: roles are assigned residents (by
  `Agenda.attends` as today), each given a `SceneRole` behaviour (230) that walks, stands, poses and barks on the
  timeline; the crowd comes through `CrowdService.spectacle` (§5) where it is a spectacle (pillory, execution, joust).
  Dressings (garlands, mourning cloth, the fresh grave, fair stalls, tournament pavilions and banners, the burnt house's
  smoke) through the existing ledger. One major scene a town at a time.
- **Live fire** (`repair/fire/FireLeash`, config `events.live_fires`, default on in single player, off on servers): a
  scripted fire is lit on the roof of one timber building; the leash watches the plot's box + 2 blocks (section palettes
  first, as the extinguish objective does) and snuffs any flame outside it every 10 ticks, so it never spreads into the
  next house or a player's build; the brigade (exists) and the player put it out; what burned is real damage and is
  repaired by the builders (exists). With live fires off, an embodied town's fire is shown as smoke and flame particles
  on the roof and resolved like an abstract one.
- **Archery contest**: vanilla target blocks set up as a dressing at 20, 30 and 40 blocks on the festival ground; ten
  arrows; each hit scores by the target's own redstone strength (accuracy); three folk competitors' scores rolled by
  their trade and traits; a contest HUD line (F6) shows the table. **Melee**: a ring of fences; a duel against a champion
  (gear by the town's tier); both yield at a quarter health (the law's `yield_health` idea), so nobody dies; three rounds.
- **Raids**: the band's party comes in from its road at 02:00, makes for the storehouse and a barn; townsfolk alarm and
  hide (exists); guards fight (exists); bandits carry sacks to their horses; one sets a barn alight (live fire rules).
  The player's part changes the outcome.
- **Trials**: at the courthouse's high seat (`THRONE` of `law/courthouse`, else the town hall): the judge, the accused
  standing before, two witnesses, the crowd on the `SEAT`s; the player's testimony is a conversation option on the judge
  while the trial runs ("I saw what happened."), a speech check whose odds weigh what the player really witnessed.

### 3.4 Cost

Abstract: ≤ 20 µs per town per sim day for the rules (cached state), O(1) per event at its time. Embodied: the scene
runner ≤ 0.05 ms a tick per embodied town with a scene (beats every 20 ticks); a scene's crowd ≤ 24 folk; dressings as
today; the fire leash ≤ 0.1 ms a tick while a fire burns (palette checks over ≤ 4 sections).

### 3.5 Persistence and multiplayer

`hearth:social/events` (≤ 16 scheduled, ≤ 16 recent outcomes; scene progress is not saved: an event in progress on a
restart resolves abstractly). Bands and the bailiff's journey live in §1's traffic data. Everyone sees the same scenes;
contests take up to four player entrants a round; rewards and deeds are per player; the first player to intervene sets
the intervention, others can add to it (more defenders).

### 3.6 Built on / new

Uses FamilyLife (weddings, funerals), Agenda and Venues, Dressings, Bells, Notices, officials (judge, lord, reeve,
bailiff from the steward's men), law (pillory, gallows, jail, offences), repair (damage, brigade, rebuilding), economy
(treasury, works for rebuilding), quests (happenings), standing (deeds, news). New: the event book and rules, scene
runner, domains and tax journeys, raids, trials of residents and testimony, fairs, archery and melee contests, jousts,
the fire leash, the `burnt` decay flavour (build module), topping-out, and the smaller events.

### 3.7 Content

Event data files (one per kind) with chance, conditions, roles, timeline, outcome table and barks. Barks
(`barks/events.json`): wedding (*"May your hearth never go cold!"*), funeral (*"He mended my roof for nothing, the year
my man died."*), trial crowd (*"Thief!"*, *"Let him go, he's got six mouths to feed!"*), tax day (*"Ten in a hundred, and
the roof still leaks."*), fair crier (*"Come see the fire-eater from the southern sands!"*), herald at the joust, raid
(*"They're at the granary!"*), topping-out (*"A bough on the ridge — she'll stand a hundred years!"*).

### 3.8 Acceptance

- Unit: `EventRulesTest` (deterministic schedules; frequency per 100 town-days by tier within bands — weddings 6–10 in a
  village, fires 0.3–0.8, trials only on court days and only with a defendant, tax every 14 days only in domain towns,
  raids only where a band's strength exceeds the defence); `SceneTimelineTest` (pure timelines end, every role has a
  place); `EventAgreementTest` (the same event with no intervention gives the same outcome abstract and embodied);
  `FireLeashTest` (a fire in a test plot never leaves its box + 2 in 2 000 ticks).
- Tour (`HEARTH_LW_TOUR=events`, `/hearth social force <kind>`): a wedding procession at the chapel door; a funeral at
  the cemetery; the pillory with ≥ 8 onlookers; a fair with ≥ 8 stalls and the archery targets; a joust; a burnt house,
  then the same plot rebuilt after a fast-forward; the bailiff's cart at the town hall; a night raid with guards fighting.

---

## 4. People with lives

Families with children who grow up, apprentices who become masters, friendships, feuds and romances, a few named
characters per town with personal schedules and a story thread (KCD-style), who talk about you and remember.

### 4.1 What the player sees and does

- **Growing up.** Children grow up (exists); now an older child (28 days on) is **apprenticed**: they follow a master
  of a trade (the parent's, if the parent masters a workshop; else a master with room) to work, stand at the bench,
  fetch and carry, and on growing up become a **journeyman** of that trade. A journeyman with 60 days at the trade
  makes a **masterpiece** (a small celebration at the workshop: the guild drinks to them) and becomes a **master**; a
  master in a town short of that trade's workshops gets one (the planner builds their house-and-workshop). Folk say
  so: *"Young Vojta's made master — he'll have his own forge by spring."*
- **Friendships** form between workmates, neighbours and those who spend their evenings together, and fade without
  contact; friends sit together at the tavern, walk together on the rest day, greet each other by name.
- **Romances.** Unmarried adults become sweethearts (data rules on age and the existing `same_sex` setting): they walk
  together on rest-day afternoons, sit at a viewpoint, one brings the other flowers; then the banns (the existing
  weddings now prefer sweethearts). A jilted rival may start a feud.
- **Feuds** between households: over love, a theft blamed, a debt unpaid (purses), a boundary between plots, a death in a
  raid one fled from, a brawl. Feuding folk avoid each other, trade insults within earshot, brawl at the tavern (§5's
  brawl), take it to the court (§3's trial); they make peace with time, a wedding between the houses, or a player's
  mediation (a speech check, or a small quest).
- **Grief**: a widow or widower in black for 7 days, at the grave on the rest day; orphans go to kin or the orphanage.
- **Named characters.** Each town keeps a few: a hamlet 1–2, a village 3, a town 5, a city 6, beside its officials.
  Each has a **role** (the drunk smith, the widow at the well, the young lovers of feuding houses, the ambitious
  apprentice, the old soldier, the gossip, the miser merchant, the healer, the runaway, the poacher, the miller in debt,
  the pious pilgrim, the exile come home), a **personal schedule** (the drunk smith at the tavern every night, the widow
  at the cemetery on the rest day, the old soldier on the bench by the gate at noon telling the same war story), and a
  **story thread** that moves on whether or not the player is there (the smith sobers up — or loses his smithy to his
  apprentice) and that the player can turn (pay his debts, take him to the priest, or let him fall). The **People** page
  of the journal (F4) lists those you have met and where their story stands.
- **They talk about you and remember.** Every folk can speak of what you did in their town and to them, from the
  standing module's memories and town news: *"You're the one who put out the fire at the chandler's. Drinks are on me."*;
  *"Last I saw you, you were running from the guards."*; named characters remember their thread with you by name and
  reward or resent it later. Two folk at leisure near the player sometimes talk **about** the player within earshot.

### 4.2 Abstract

`sim/lives` (pure model, run on `DAY_PASSED`, also in catch-up; histories leave it out):

- Per resident `extra("lives")`: up to 8 **relations** (other resident, kind: friend, sweetheart, rival, feud, mentor,
  apprentice; strength −100…100; last contact day), apprenticeship (master, trade, since), masterhood (trade, day),
  grief (until).
- Daily: relation strength from shared work plot, home plot neighbours, the day's leisure picks (a seeded draw; ≤ 3
  updates a resident a day), decay without contact; sweethearts from friends of the right age and rules; feuds from
  events (`CRIME_COMMITTED` with a resident victim and a suspect of another household, `RESIDENT_REMOVED` in a raid,
  purse debts from the economy's books, neighbouring plots in a crowded town) and from the thread rules; reconciliation
  odds by time and traits.
- **Apprentices and masters**: the sim's job assignment honours them through the new `ResidentClaims` hook (lane 0): an
  apprentice grown up takes the trade first; a master raises their trade's workplace demand, which the planner's needs
  read (`plan/growth/Needs`).
- **Named characters** (`Notables`): chosen when a town is adopted or reaches a tier (eligibility from the role data:
  job, age, traits, station), kept until they die or leave; a new one is drawn after. Officials stay officials.
- **Threads** (`Threads`): each a small state machine from data (`hearth_lives/threads/*.json`): beats with conditions
  (days since the last beat, a calendar or town event, a relation, a player's choice, a quest template completed, the
  player's standing), effects (relation and mood changes, a trade or station change, a wedding through `Matchmaker`, a
  departure, a resident death only where a beat says so, a gift, a memory, a personal quest through
  `QuestService.offerPersonal`, a deed), and branches. Time-only beats advance abstractly, so a town you left has moved on.
- `LivesService` (new api, `HearthServices.lives()`): notables, relations, life events for other modules (§3's trials
  ask for feuds; §8's commissions ask for feuds to settle; §5's crowds ask who is whose kin).

### 4.3 Embodied

- Behaviours (`social/lives/*`): `ApprenticeBehaviour` (105, at the master's side: stand beside, hold the trade's tool,
  carry what the master carries); `CourtingBehaviour` (30, rest-day afternoons: the two walk together, sit at a
  `VIEWPOINT`, a poppy given); `FeudBehaviour` (in passing: turn away, insult bark, sometimes a brawl through
  `CrowdService.brawl` at the tavern); `GriefBehaviour` (at the grave, in black — station dress darkened by the
  composer's existing finish rules for a mourning flag); `NotableSchedule` (35: the role's personal windows, through
  the `ScheduleOverrides` hook so the routine places them there when they are spawned).
- **Talk** (`npc/talk`): new dialogue categories `about.player.<deed>` (from the player's memories with this folk, the
  town's news about the player, and their title and fame), `overheard.player` (two folk), `thread.<id>.<beat>` (the
  notable's lines and options at 50–79), and `remember.last` ("Back again? Last time you…"); greetings prefer lines that
  name a remembered deed.

### 4.4 Cost

≤ 30 µs per 150-person town per sim day; relations bounded (8 a resident, ≤ 200 bytes); threads ≤ 6 a town, ≤ 64 bytes
of state each; behaviours as any (cached `wants`). Nothing per tick abstractly.

### 4.5 Persistence and multiplayer

`extra("lives")` per resident (dies with them; a feud passes to the household's kin), settlement key `hearth:lives/town`
(notables, threads, courtships, feuds between households). Threads belong to the town: each beat records the player who
caused it, if any; other players hear of it ("Did you hear what the stranger did for old Hanuš?"); personal lines come
from each player's own memories. A beat needing a choice is open to the first player who takes it.

### 4.6 Built on / new

Uses ageing and families (sim, npc data), Matchmaker and FamilyLife, standing memories, hearsay, news and fame, purses
(debts), the law's crimes, quests (personal), officials. New: relations, apprenticeships and masters, sweethearts and
feuds, grief, notables and threads, personal schedules, the about-the-player dialogue.

### 4.7 Content

At least 13 roles and threads (the list in §4.1), each 4–8 beats with two or three endings; barks per role (12–20);
dialogue categories above with 150+ lines across cultures. Example thread, *the drunk smith*: (1) he drinks every night
and his work suffers (folk complain); (2) the steward presses him for a debt; (3) choices — pay his debt (he swears off
the drink for a while), take him to the priest (a quest: candles and a vow), or nothing; (4a) sober, he takes the
apprentice back and makes you a blade at cost; (4b) the forge goes to his apprentice (who remembers you were kind to
him or not), he sits by the gate with the old soldier.

### 4.8 Acceptance

- Unit: `LivesTest` (an apprentice becomes a journeyman of their trade ≥ 90 % of the time when places exist; masters
  within 60–120 days; relations bounded; feuds resolve or persist; deterministic for a seed); `ThreadDataTest` (every
  thread file valid, every beat reachable, every branch ends, every quest template and dialogue key it names exists);
  `MatchmakerTest` (sweethearts married first).
- 200-day sim run (harness): per village at least one apprentice made master, one feud, one sweethearts' wedding,
  notables' threads that have moved on.
- Tour (`HEARTH_LW_TOUR=lives`): the drunk smith at the tavern at 22:00; a courting couple at a viewpoint on the rest
  day; an overheard line about the player after a deed (`/hearth standing deed`); the People page after meeting three.

---

## 5. Crowds that react

Gather at a fight, scatter from a drawn sword, guards comment on your armour, greet a hero, shun an outlaw, shutters close
at night.

> **Built (lane A3, 2026-10-20)** in `npc/crowd/`: `Stimuli` (pure) → `CrowdDirector` (every 10 ticks per player in an
> embodied town; `CrowdSelector` picks who reacts) → `Order`s picked up by six `CrowdBehaviour`s (hail 205, remark 208,
> gawk 215, mutter 218, shun 220, scatter 225) and `BrawlBehaviour` (260). `Barks` is the shared bark engine of §0.2
> (`hearth_dialogue/barks/*.json`, cooldowns in `crowds.json`). `Shutters` scans built plots' blueprints off-thread
> (`ShutterScan`) and toggles only `open`, 20:30–06:30 and while an outlaw, an alarm or a thunderstorm is about. Brawlers
> stop at half health; the nearest guard parts them and fines a bare-fisted player 5 bits. `/hearth crowds
> stats|sense|shutters|brawl`; tour `-Dhearth.tour=crowds`. Requests in `docs/requests/lwA3.md` (the law's assault watch
> must ask `Offences.brawling`; filth and dress remarks need a standing api method).

### 5.1 What the player sees and does

| The player | Folk near (≤ 16 blocks) |
|---|---|
| draws a weapon in town | step back 4–8 blocks, mothers pull children close, *"Put that away, there are children here!"*; a guard comes over if it stays drawn |
| fights (a mob, a bandit, a folk) | a ring gathers at 4–6 blocks (at most 12), shouts and cheers, the cautious keep back; guards break up a brawl |
| wears heavy armour | guards remark (*"Plate on a market day? Expecting a war?"*), merchants eye it; menace goes up (exists) |
| is a Hero of the town | folk stop and turn, wave, two or three cheer *"It's {player}!"*, children follow for half a minute |
| is a Friend / Known | greeted by name (exists), small talk barks |
| is Distrusted | muttering, folk cross the lane |
| is an Outlaw | folk run indoors, doors shut behind them, shutters close on their houses, *"Murderer! Fetch the watch!"*; guards attack on sight (exists) |
| is bloodied, filthy, in foreign dress | remarks from the standing module's look (*"You're bleeding on my step."*, *"The bathhouse is that way."*, *"Where's that cloth from?"*) |
| is abroad at night, or sneaking | *"Who's that creeping about?"*; the watch challenges (§2) |
| stands over a body | screams, a crowd, a guard |

**Shutters close at night**: every window shutter (trapdoors on a building's outer wall beside its glass) closes at the
curfew bell and opens at dawn; they also close when an outlaw or a raid is about, and in storms.

**Brawls**: folk-on-folk fist fights (feuds, tavern nights): both stop at half health, a crowd gathers, a guard breaks it
up. A player who joins unarmed is brawling (a small fine), not assault.

### 5.2 Abstract

None: reactions only happen where folk and players are. Shutters are block states, toggled only in loaded chunks of
embodied towns; an abstract town keeps whatever state its chunks last had (the next embodiment sets them for the hour).

### 5.3 Embodied

- `npc/crowd/Stimuli` (pure): from a player's state — main hand item, armour points, recent combat (last hurt by or at
  in 5 s), title (cached from standing), outlaw (`LawService`), charisma and menace, sneaking, time, nearby body — the
  set of stimuli with salience.
- `CrowdDirector`: every 10 ticks per player in an embodied town: `NpcService.folkNear(16)` (≤ 24 folk), give each at
  most one reaction (cooldown per folk per stimulus 60 s; bark caps from §0.2), the most salient player first when
  several are near. Reactions are behaviours (§0.3): scatter, gawk, hail, shun, remark, and the scene roles of §3's
  spectacles (`CrowdService.spectacle`).
- `Shutters`: a shutter scan per plot, once when it is first embodied (trapdoors on the outer faces of the blueprint's
  walls next to panes or glass), cached; toggled 32 a tick at most per town at their hours and stimuli (the `open`
  property only). The repair module's damage rules ignore the `open` state of doors, trapdoors and fence gates (one
  change in `repair/DamageRules`), so a closed shutter is never "repaired".
- `Brawl` (`CrowdService.brawl`): two folk fight with fists (swings, knockback, damage stops at half health), a gawking
  ring, a guard arrives and parts them; the law's `brawling` offence (fine 5) for a player who joins unarmed.

### 5.4 Cost

≤ 20 µs per player per 10-tick pass (one spatial query, arithmetic); ≤ 12 reacting folk per player; shutters ≤ 32
block-state changes a tick per town, at the hours only; scan once per plot per embodiment (cached in memory).

### 5.5 Persistence and multiplayer

Nothing saved but shutter scans' cache in memory. Stimuli are per player; a crowd reacts to the most salient; a group
of heroes is cheered together; an outlaw in a group of honest players makes the folk shun the group's outlaw only.

### 5.6 Built on / new

Uses standing (title, charisma, menace, look), law (outlaws, wanted, guards, offences), npc perception and behaviours,
greeting by name (exists), the repair module's damage rules. New: stimuli, director, the reactions, shutters, brawls,
the crowd api.

### 5.7 Content

`barks/crowd.json`: 15–25 lines per stimulus, per role (guards, merchants, mothers, children, elders), per culture where
it matters, and by standing title.

### 5.8 Acceptance

- Unit: `StimuliTest` (pure classification), `CrowdSelectorTest` (caps, cooldowns, salience order), `ShutterScanTest`
  (on built blueprints: shutters found, doors not).
- Tour (`HEARTH_LW_TOUR=crowds`): a drawn sword in the market — folk within 6 are 4+ blocks further away within 3 s; a
  fight with a ring of ≥ 6 onlookers; a Hero (`/hearth standing set`) greeted by ≥ 3 waving; an Outlaw — doors shut
  count logged; shutters ≥ 80 % closed at 21:30 and open at 07:00; a guard's armour remark in the log.
- Harness: a repaired-blocks count of zero after a night of shutters.

---

## 6. Work you can see

Woodcutters felling, charcoal burners at kilns, shepherds moving flocks over Expanse pastures, miners walking to the
Great Mine, boats at docks, stalls stocked with the town's real goods.

**Status (lane A4, 2026-10-21): built**, but for the one step that waits on another lane (market-day stalls' wares need
A2's stall callback; `requests/lwA4.md`). The handcarts landed on A1's `hearth:cart` (A4 merged `hearth-lwA1`, so A1
merges first): `npc/work/field/Handcarts` (haulers draw a handcart on hauls of `carts.min_haul` 16 blocks and more, 4 a
town, 32 a server, through `LoadCaps`, swept when the hauler is gone, parked by the door and whenever the hauler is under
a roof), the logging camp's logs riding to the sawmill on its bed. Measured (2026-10-21): the remote sites' pass 5.8 µs a
player at 200 towns × 12 sites × 50 players (`RemoteSitesCostTest`; budget 10); in the work tour on a 246-soul city with
16 flock beasts out and 3 handcarts, field work 0.03-0.05 ms a tick on average (flocks 0.02, clamps 0.01, boats 0.002,
handcarts 0.001); worst passes: flocks 5 ms (spawning a flock), clamps 16-25 ms when a timber stack changes height (the
first block set of the pass, `requests/lwA4.md`). Where it lives:
`npc/work/field/*` — `RemoteSites` and `FieldSites` (sites from plots and territory, cached a sim hour), `Commute` and
`FieldPlacement` (the record on the track or the meadow), `RemoteEmbodiment` (sites embodied on their own, from the
embodiment manager every `sites.pass_ticks`), `Flocks` (sizes, kinds, `hearth:npc/flocks` losses), `Herds` (the beasts
and the dog), `DroveWork` (the herder's way out and home), `ClampCycle` and `Clamps` (the burn, the smoke column, the
clearing's ledgered dressing `hearth:npc/clamps`, the logging camp's timber stack), `Boats` (moorings, the rowing fisher),
`Shafts` (the Great Mine's ladder climbed by hand), `WorkBarks` (`barks/work.json`); `sim/land/GreatMines` +
`sim/terrain/ExpanseGreatMines` (the survey's one call, the claim on the shaft's square); `plan/resource/GreatMinePlan`
and `build/archetype/extract/GreatPithead`; `social/life/StallWares`; `EconomyServiceImpl.shopStock`; the api's
`GoodsItems` served by `quest/gen/GoodsItems.SERVICE`; `ExpanseUnderground.waterY`. Departures from the text below: the
drove is the herder's routine work (`HerderWork` asks `DroveWork` first) rather than a priority-110 behaviour, because a
folk held by a behaviour does not count labour and the abstract and embodied flocks must yield the same wool; remote
sites are found per player by a town prefilter (towns whose territory comes within reach) rather than a cell index; a
Great Mine's new galleries are short (from the old station rooms to the edge of the claimed square), one a level, up to
six. Tour: `-Dhearth.tour=work` or `HEARTH_LW_TOUR=work` (`gametest/work/WorkTour`).

### 6.1 What the player sees and does

- **Woodcutters** fell real trees and replant (exists). New: logs go to the sawmill by **cart** on the track (§1's
  cart), and logging camps in the woods have a saw-pit and a stack of seasoned timber that grows and shrinks with the
  town's logs in store.
- **Charcoal burners** at the **clamp in the woods**: the burn cycle is visible — the stack of logs being built, covered
  with earth and turf, smoking for two days (a column of smoke you see from the road), then opened and raked into
  charcoal sacks; the burner sleeps in a hut beside it and tends it at night (KCD's charcoal burners). Talk to them for
  rumours of the woods.
- **Shepherds and flocks**: pastures are **stocked** (closing the open item): sheep, cows or goats by culture in the pens;
  at 07:00 the herder and a dog drive the flock out to a **pasture site of the territory** (Expanse's open meadows, moor,
  alpine grass) up to 300 blocks away, graze them there, and drive them back at 18:00. In alpine towns the herd goes up
  to the high meadows for the summer festival weeks (the Cattle Drive festival becomes a real drove).
- **Miners walking to the Great Mine**: a town whose territory reaches an Expanse **Great Mine** reopens it (Expanse
  stands its pithead down when Hearth claims the square): a working **pithead** beside the old shaft (headframe, winch
  house, miners' huts, ore heaps), a track to town; at 06:00 the miners walk up the track, climb down the shaft's ladder
  and work new **galleries** from the old levels; at 18:00 they walk home (or sleep in the huts through the week and go
  home on the rest day). A player who goes down the shaft finds them at the faces 100–300 blocks below.
- **Boats at docks**: moored boats at every dock; fishers row out at dawn, fish on the water, row back at noon and
  dusk; deep towns on Expanse's buried sea and underground rivers get landings and boats too (their docks on real
  water, `UndergroundProbe.waterY` answered from Expanse's sampler, closing that open item).
- **Stalls stocked with the town's real goods**: every market stall, market hall counter, bakery and shop counter shows
  what that shop actually holds: the culture's own items for its goods (rye loaves in the north, flatbread in the
  desert, the town's own fish, its own timber species, its cloth in its colours), most-stocked first; a shop running out
  shows empty boards; restocking shows the shelves filling.
- **Haulers with handcarts** on the town's streets once carts exist (§1).

### 6.2 Abstract

Nothing new is produced: the sim's chains already make the logs, charcoal, wool, fish and ore. What is new is data the
views read:
- **Flocks**: a herd size per pasture plot, derived (herders × `herd_per_herder` 8, capped by the pasture's area and its
  `PEN` POIs), less losses (animals of the flock killed or stolen by players, recorded and healed at one a day).
- **Remote work sites** (`npc/work/field/RemoteSites`): per settlement, its pastures in use, charcoal clamps, logging
  camps, the Great Mine pithead and galleries, fishing waters; a resident whose work is at one is placed there by their
  routine in working hours.
- **The Great Mine** (`sim/land/GreatMines`, through an Expanse-only class `sim/terrain/ExpanseGreatMines` calling
  `ExpanseSights.greatMines`): when a town's territory reaches a pithead, an ORE site "the Great Mine" (rich, deep) is
  added at its next survey; the square ±12 of the shaft is claimed through the `Claims` registry (so Expanse's own
  pithead stands down); the planner (`plan/resource`) builds a working pithead (new archetype `extract/great_pithead`)
  and opens galleries as excavations (`extract/mine` from a level's station room, `origin.y` at the level). Historic
  towns: the claim is answered for any atlas site whose largest future territory reaches a Great Mine, so Expanse never
  writes the abandoned pithead there.
- **Charcoal clamp stage**: a pure function of the charcoal recipe's activity in the town's books (day of the cycle).
- **Shop stock**: `EconomyService.shopStock(settlement, plot)` (new, additive) reads the shop book; item choice through the
  new `GoodsItems` api (the quest module's culture-aware item table, now shared).

### 6.3 Embodied

- **Remote sites embodied**: every 40 ticks, each player's nearby remote sites (≤ 12 a town in a cell index) are checked;
  a site within 96 of a player has its workers spawned there (`EmbodimentManager` learns remote sites; the town itself
  may stay abstract; the residents are flagged embodied as today and their labour credited when they fold).
  `RoutinePlacement` places commuters on the track between home and site at the start and end of the day.
- **Drove** (`DroveWork`, 110): the herder and a tagged dog (a tamed wolf, noSave) lead the flock (tagged noSave
  animals spawned at the pens, at most 8 a pasture, 24 a town, 96 a server, only within 64 of a player) to the pasture
  site and back, staying behind the flock, collecting strays (`HerderWork`'s existing round-up).
- **Clamp** (`ClampWork`): the burner at the clamp; the clamp is a ledgered dressing whose blocks change with the stage
  (logs → earth and turf mound with campfire smoke from the top → charcoal sacks and blackened earth); smoke particles
  while burning.
- **Miners** (`ExcavationWork`, extended): the walk up the track, the ladder down the 5×5 shaft (folk climb ladders),
  the gallery faces (the construction contract, unchanged), a winchman at the pithead (pose), ore carried up in loads.
- **Boats** (`BoatWork`): vanilla boats (noSave tag `hearth.boat`) moored at `DOCK` POIs (1–3 a dock, ≤ 6 a town, ≤ 24
  a server); a fisher rows out — the boat moved by the work along a loop of water waypoints 20–60 blocks out (the folk
  rides; the work sets the boat's motion), casts (`FisherWork`), rows back.
- **Stall wares** (`social/life/StallWares`): item display entities (vanilla `item_display`, noSave tag `hearth.display`)
  lying on counters and hung from stall canopies: up to 6 a stall, 60 a town, chosen from that shop's top stocked goods;
  updated when the stock changes by a quarter or once a sim hour; removed when the town folds. Market-day stalls get
  theirs when the social module's `Dressings` sets a stall up (a callback lane 2 adds).

### 6.4 Cost

Remote sites: ≤ 10 µs per player per 40 ticks. Flock animals: vanilla AI, ≤ 1 ms a tick in all at the cap of 96
(measured; thinned first when MSPT is high). Boats ≤ 24, moved by arithmetic. Displays do not tick (client render only;
≤ 1 500 server-wide). Clamp dressings change at most a few times a sim day. Great Mine: one Expanse query per survey.

### 6.5 Persistence and multiplayer

Flock losses: settlement key `hearth:npc/flocks` (a count per pasture). Everything embodied is noSave and rebuilt from
the books. The Great Mine site and its plots are ordinary land state and plots. Shared by all players; an animal of a
flock is owned (the law's animal ownership already applies).

### 6.6 Built on / new

Uses foresters, haulers, excavations and the construction contract, herders, fishers, CraftWork (charcoal burner),
territory and survey, the planner's workings and cart tracks, shops and shop stock, the social module's stalls, the
quest module's `GoodsItems`, Expanse's Great Mines and underground water. New: remote work sites, stocked pastures and
droves, the clamp cycle, the Great Mine reopened (pithead archetype, galleries, claims), boats, stall wares, and carts for
haulers (§1's entity).

### 6.7 Content

Barks (`barks/work.json`): herders (*"Hup, hup! Come on, you daft beasts."*), burners (*"Don't let her flare — two days
she must smoulder."*), miners on the track (*"Another day under the mountain."*), fishers, stallholders calling the
real wares (*"Fresh trout from the {river}!"* — names from the town's stock). Data: `herd_per_herder`, species by culture,
pasture distance, clamp cycle days, boats per dock.

### 6.8 Acceptance

- Unit: `FlockSizeTest`, `RemoteSitesTest` (sites from plots and the territory, placement on the track at shift change),
  `GreatMineSiteTest` (a fake Expanse source adds the ORE site and the claim; histories claim it too), `StallWaresTest`
  (shown goods ⊂ the shop's stock; empty when out of stock).
- Tour (`HEARTH_LW_TOUR=work`): a flock and herder on a meadow 200 blocks from town at 10:00, the pen full at 20:00; a
  smoking clamp and its burner; miners on the track at 06:30 and one at a gallery face 150 below; a fishing boat on the
  water; a bakery counter whose loaves match its stock; a hauler's handcart.
- Harness: four embodied towns, flock and boat entities never above the caps, flock ms a tick logged ≤ 1.

---

## 7. Small places in the wild

The pass hospice (Expanse stood its refuge down for it), wayside inns, manned toll gates, hermits, hunters' camps, and
the planned caravan stop, mountain monastery, nomad camp, fishing harbour and shepherd's farmstead.

### 7.1 What the player sees and does

| Post | Where | Built (archetypes) | Folk | What the player gets |
|---|---|---|---|---|
| **Pass hospice** | an Expanse pass (saddle) on a road over the mountains — every pass Expanse's refuge would have taken | `civic/hospice` (new: a walled stone refuge with a chapel, dormitory, kennel, a fog bell), stable | 2–4 monks, a brother keeping the door, a dog | a bed and a meal (inn services), the healer, shelter in storms (the bell rings in fog and blizzards to guide travellers); quests: travellers lost in the snow |
| **Wayside inn** | on roads about a day's travel (1 200–1 800 road blocks) from the nearest inn or town, on level ground by water | `commerce/inn` (`wayside` param: a yard, a sign, a trough), stables, well, garden | an innkeeper's family (3–6), a stablehand | inn services, tavern nights (§2), travellers overnight (§1), rumours of the road, sometimes a fence |
| **Toll gate** | where a road crosses a big river bridge, or a domain border (§3) | `law/toll_gate` astride the road, `law/watch_house` | a tollkeeper, 1–2 guards | stops you: pay the toll (2–10 bits), talk your way through, or go round (toll evasion, a small offence, if seen); caravans pay abstractly; tolls to the lord |
| **Hermit** | remote (≥ 600 from roads and towns): deep wood, a crag, a cave mouth | `house/hermit_hut`, `civic/shrine` | a hermit | herbs that heal, far rumours (landmarks, Great Mines, realms), a thread of their own (§4) |
| **Hunters' camp** | an Expanse wood edge 300–900 from a town | `work/hunters_lodge`, tents (`house/hut` tent form), drying racks (dressing) | 2–3 hunters | game and furs for sale, a hunting party to join, wolves and beasts bounties |
| **Caravan stop** | a junction of king's roads far (≥ 900) from towns | `commerce/caravanserai` in dry cultures, else `commerce/caravan_yard`; well | a keeper (trader), a stablemaster | stabling, a market of the caravans passing, news from far |
| **Mountain monastery** | an Expanse summit or knoll high above its land (≥ 150) | `civic/monastery`, terrace fields, a bell | 6–12 monks (an abbot) | the library (scholar quests), brewing, pilgrims' goal (§1), a rest-day service on the heights |
| **Nomad camp** | steppe, dunes, savanna | 3–6 `house/round_house` (`portable` param: felt yurts), pens | 6–12 herders of the nomad culture | horses and wool to trade; it **moves** between two grounds every 14 days (the camp packs up and walks with its herd) |
| **Fishing harbour** | a coast or big lake shore ≥ 600 from towns | `infra/dock`, `work/fishery`, 2–4 cottages, a boathouse | 3–6 fishers | fish, boats out (§6), a passage across the water (later) |
| **Shepherd's farmstead** | upland pasture, moor, alpine meadow | `house/farmhouse`, `work/barn`, two `farm/pasture`, pens | a herder family (3–6) | wool, cheese, a flock on the hill (§6), a bed in the hay |

### 7.2 Abstract

- **Siting** (`sim/wild/PostSites`, an atlas **site overlay**, deterministic from the seed with its own random stream so
  no town's history changes): per region up to `wild.per_region` (3) posts, ≥ 300 from any town's bounds and ≥ 200 from
  each other, clear of structures and of the region's plan: the pass, summit, knoll and wood-edge tests on the probe use
  Expanse's own definitions (a pass rises 12+ along one axis at ±48 and falls 8+ across; a knoll stands 12+ over its ring at
  radius 56), or Expanse's answer if it exposes one (`docs/requests/expanse.md`, asked); road-placed posts (inns, toll
  gates, caravan stops) from §1's network; culture-placed ones (nomads) from the culture map.
- **History and adoption** like towns: a post has an age; an old one is written at world generation (through the site
  overlay lane 1 builds) and adopted when its chunks load; regions already generated get posts **founded live** (as
  daughter hamlets are) and built by the runtime path, a crew of the nearest town walking out to help.
- **A post is a settlement** with `kind() == POST` and a `postType`: its planner (`plan/wild/PostPlanner`) lays out the
  type's fixed programme (each type its own compact layout: the hospice a walled court on the saddle; the inn by the
  road with its yard toward it; the toll gate astride the road; the monastery on its summit with terraces below), then
  `next()` returns nothing but rebuilds. The sim steps a post every `wild.step_interval` (6 000 ticks), never founds
  daughters from it, does not send caravans from it (it hosts them), keeps its residents to its beds (settlers refill
  empty beds), and lets its shops and services run as any town's.

### 7.3 Embodied

As any settlement: the same embodiment, routines, work and services (inn, healer, stables, shop). New behaviours:
the tollkeeper's stop (`TollBehaviour`, 150: steps into the road when a player passes the gate's trigger zone and opens
the conversation; guards bar the way when refused), the hospice's fog bell (`Bells` in storms), the hermit's day, the
nomads packing and pitching (the camp's plots go `DEMOLISHING`, the herd walks with them, the new ground's plots are
quick to build: felt and poles).

### 7.4 Cost

A post's abstract step ≤ 10 µs every 6 000 ticks: 600 posts ≈ 0.001 ms a tick. Siting: arithmetic and probe reads per
region, once, cached with the region plan. Embodied: ≤ 12 folk a post, within the town caps.

### 7.5 Persistence and multiplayer

Settlement files like towns, with the post type in settlement data `hearth:plan/wild`. Shared by all. Tolls per player
per crossing (a paid player passes freely for the rest of the sim day).

### 7.6 Built on / new

Uses the atlas and adoption, the sim's settlements, the planner, the archetypes listed, inn and stable services, law's
toll gate, quests, the map module (posts reported to Atlas as places, kind `hearth:post/<type>`, with their own tiles),
Expanse sites and `LandClaims`. New: the site overlay's posts, `kind()`/`postType()`, the post planner and layouts, the
`civic/hospice` archetype and the `wayside`/`portable` variants (architects), the toll stop, the nomads' move.

### 7.7 Content

Each type: names (`hearth_names/posts.json`: "The Saddle Hospice", "The Crooked Pine", "Tollbridge", "St. Prokop's on
the Crag"), barks, dialogue for its keepers, two or three quest templates (`hearth_quest/wild/*`).

### 7.8 Acceptance

- Unit: `PostSitingTest` (deterministic, spacing, per-region cap, the pass test on a synthetic saddle, never inside a
  town's territory core), `PostPlannerTest` (each type's programme on synthetic terrain: inside its footprint, doors
  reachable, the toll gate astride a road, the hospice on the saddle), `PostGrowthTest` (a post never grows past its
  programme in 300 days and refills its beds), existing `BlueprintInvariantsTest` over the new archetypes (light rule).
- Tour (`HEARTH_LW_TOUR=wild`, `/hearth wild tp <type>`): one shot of each of the ten types by day; the hospice in a
  snowstorm with its bell; the toll stop conversation.

---

## 8. The player's place

Hire companions, own a house with servants, rise to burgher, take a lord's commissions.

### 8.1 What the player sees and does

- **Companions**: at an inn or tavern, ask about hands for hire: a few residents (adults, not office holders, brave or
  greedy, guards and hunters first, and some named characters through their threads) will come along for a hiring fee
  (5 days' wage) and a daily wage (twice their station's), paid at dawn from the coin you carry. Orders in conversation:
  follow, wait here, keep your distance, go home, fight what I fight, hold back. They follow, fight monsters and your
  target, wear the gear you give them ("Let me see your gear", F3), sleep when you sleep, remark on the road, places and
  your deeds, and like or dislike what you do (theft from their own town costs loyalty; the greedy do not mind elsewhere).
  Unpaid for two days, they go home. They can die, for good; their family mourns and holds it against you if you led
  them to it. At most `companions.per_player` (2).
- **A house with servants**: in a house you own (property exists), hire up to three servants (by the house's beds) —
  poor adults willing to keep house. They live there, sweep and tidy, keep the larder chest stocked with the town's food
  (bought at the town's price from your bank account), tend the garden, stable your horse, lock up at night, greet you
  when you come home, and tell you what happened while you were away (notices and what folk say about you). Wages go out
  of your bank account daily; unpaid for three days they leave. The **Household** page (F2) shows your houses, servants,
  wages, larder and companions.
- **Rise to burgher**: today any owner of property is called a Burgher. Now a property owner is a **householder**; to be
  a **burgher** you swear the burgher's oath before the reeve (standing ≥ 40, property held 30 days, the burgher's fee of a
  crown a tier): a short ceremony at the town hall on a market day. Rights: market dues waived (a price factor through
  `PriceModifier`, within the api's bounds), guards do not remark on your arms, a seat at the high table at feasts, a pew
  at mass, and a voice in the **council**: once a festival month, after the rest-day service, the burghers choose which of
  the planner's next three public works the town builds first (it is funded first, as players' gifts are).
- **The lord's commissions**: at a town with a lord, a player of Known standing can serve: *Petitioner* → *Retainer* (the
  existing pledge of service, and one commission) → *Sworn man* (three, Friend) → *Household knight* (six, standing ≥ 70,
  and a tournament won or a raid repelled; dubbed at the throne before the court). Each rank brings rights: lodging at
  the keep, a horse from the lord's stables, the lord's colours (local dress for the standing module's look), toll-free
  passage on the lord's roads, and at knight a house or plot in the seat by deed. Commissions come from the real world:
  clear the band on the lord's most dangerous road (§1), escort the tax takings home (§3), bring a defaulting reeve to
  court (§3), settle a feud between two houses (§4), carry a letter to the neighbouring lord (§1), take the poacher (law),
  inspect a toll gate and bring its takings (§7), defend a village of the domain against a raid (§3), ride in the lord's
  colours at a tournament (§3), find ground for a new village (the existing scout quest, founded in the lord's name),
  survey the Great Mine (§6).

### 8.2 Abstract

- Companions are residents of their home town flagged `away` (`extra("companion")`: player, since, wage day, orders,
  loyalty); when the player logs off they fold into the record and wait; at login they are embodied beside the player
  again (`embodyNear`). Wages are charged on the player's `DAY_PASSED` of the home town while online (offline days are not
  charged and not worked). Death removes the resident (permadeath) and the funeral follows (§3).
- Servants take job `SERVANT` with home and work at the player's plot; the sim's daily assignment keeps them there through
  the `ResidentClaims` hook (the sim otherwise leaves player-owned plots empty). Larder: daily, up to the larder size of
  the town's prepared food from the stockpile into the house's storage chest (only when its chunk is loaded; else
  credited and placed on load), charged with the new `EconomyService.chargeDeposit`. Wages likewise.
- Burghers: per town, per player (`hearth:social/burghers`), read by standing's titles (Burgher now means sworn; owners
  are Householders), the economy (price factor), the crowds (§5) and the council (`economy/works`).
- Commissions (`quest/commission`): ranks per player per lord (attachment `hearth:quest/retinue`; when a lord dies the
  heir keeps your service one rank lower unless you pledge again); commission templates (`hearth_quest/commission/*`,
  quest type `OFFICIAL`, giver the lord or steward) bound by an offer source to the domain's real state through the
  services (`RoadNetwork.danger`, `TravelService`, `LivesService` feuds, the event book's tax days and raids); rewards
  from the lord's treasury.

### 8.3 Embodied

`CompanionBehaviour` (280, persistent while hired; the escort's follow logic): follows 2–4 blocks behind, catches up out
of sight beyond 24, fights hostiles and the player's target, holds back when told, waits at portals (folk never cross
dimensions: they wait, then go home after a day), sleeps near the player at night, eats from their pack. Worn gear is
drawn on the folk (an armour and held-item layer on the folk renderer). Servants: `ServantWork` (130: ChoresWork-like
rounds of the house, the larder, the garden, the hitching post, the door at night; the greeting bark). The oath and the
dubbing are small scenes through §3's runner when it is there (else a conversation beat).

### 8.4 Cost

Companions ≤ 2 a player, ≤ 64 a server, ~5 µs a tick each (pinned folk). Servants are residents within the town caps.
Commissions are quests (event-driven). Daily books per player: ≤ 10 µs.

### 8.5 Persistence and multiplayer

Player attachments (persistent, copied on death): `hearth:npc/companions`, `hearth:economy/household`,
`hearth:quest/retinue`; settlement key `hearth:social/burghers`; resident `extra("companion")`, `extra("servant")`.
Companions and servants serve one player; another player cannot hire them; servants serve whoever holds the house's
deed. Commissions are per player; several players may serve one lord; rank is per domain.

### 8.6 Built on / new

Uses escorts, `embodyNear`, behaviours, economy (property, deeds, bank deposits, works funding, price modifiers),
standing (titles, deeds, look), law (companions as folk who witness), social (lord, pledge, reeve, steward), quests
(templates, journal, offer sources), and §1, §3, §4, §7 for commission targets. New: companions, servants and the
household, burgher oath and council, retinue ranks and commissions, the gear layer, `chargeDeposit`.

### 8.7 Content

Companion barks by trait and culture (60+), servant greetings and reports, the oath text per culture, 12 commission
templates with 2–3 variants each, rank titles per culture (data).

### 8.8 Acceptance

- Unit: `CompanionTest` (wage cycle, leaves when unpaid, folds at logout and returns at login, never crosses dimensions,
  death removes the resident and starts the funeral), `HouseholdTest` (servants keep home and work through 30 days of the
  sim's assignment; larder filled and charged; unpaid servants leave), `BurgherTest` (requirements; the price factor stays
  inside 0.5–1.5 with every modifier; the council's choice is funded first), `CommissionTest` (each template binds to real
  state in a test world or is skipped; ranks advance; an heir inherits service one rank lower).
- Tour (`HEARTH_LW_TOUR=place`): a companion fighting beside the player; a servant greeting at the door; the oath at the
  town hall; the dubbing at the throne.

---

## 9. Budgets, caps and saved state at a glance

### 9.1 Server cost (target: 50 players, 200 towns, 600 posts, 20 TPS)

| System | Per tick (server) | Per town | Per traveller / entity |
|---|---|---|---|
| Roads (routes off-thread; writes in the applier) | ≤ 0.02 ms (scheduling) | ≤ 5 µs per edge per sim day | — |
| Traffic and bands | ≤ 0.05 ms avg, ≤ 0.3 ms worst pass | ≤ 20 µs per sim day | 0 abstract; ~5 µs embodied |
| Travel parties | ≤ 0.6 ms | — | ~5 µs |
| Rhythm | — | ≤ 20 µs per 20 ticks embodied; ≤ 2 µs per sim day | — |
| Events | — | ≤ 20 µs per sim day; ≤ 0.05 ms a tick with a scene | — |
| Lives | — | ≤ 30 µs per 150 people per sim day | ≤ 200 B per resident |
| Crowds | ≤ 20 µs per player per 10 ticks | — | ≤ 12 reacting folk per player |
| Visible work | flocks ≤ 1 ms at cap | ≤ 10 µs per player per 40 ticks for remote sites | — |
| Posts | ≤ 0.001 ms (600 posts) | ≤ 10 µs per step (every 6 000 ticks) | — |
| Companions | ≤ 0.3 ms at 64 | — | ~5 µs |
| **All new work** | **≤ 3 ms p50, ≤ 6 ms p99** | | |

### 9.2 Entity caps (all config, all shrinking with MSPT)

| | Cap |
|---|---|
| Residents embodied (existing) | 48 a town, 400 a server |
| Travellers (folk, draught animals, carts) | 16 parties, 120 entities |
| Flock animals and dogs | 8 a pasture, 24 a town, 96 a server |
| Boats | 6 a town, 24 a server |
| Companions | 2 a player, 64 a server |
| Item displays (do not tick) | 6 a stall, 60 a town, 1 500 a server |
| Watch lights (blocks) | 8 a town |

### 9.3 Saved state

| Key | Scope | Section |
|---|---|---|
| `hearth:sim/roads`, `hearth:sim/traffic` | world | §1 |
| chunk ledger entries under road pseudo-settlement ids | chunk | §1 |
| `extra("travel")` | resident | §1 |
| `hearth:social/lights` | settlement | §2 |
| `hearth:social/events` | settlement | §3 |
| `extra("lives")`, `hearth:lives/town` | resident, settlement | §4 |
| `hearth:npc/flocks` | settlement | §6 |
| `hearth:plan/wild` | settlement (posts) | §7 |
| `hearth:npc/companions`, `hearth:economy/household`, `hearth:quest/retinue` | player | §8 |
| `hearth:social/burghers`, `extra("companion")`, `extra("servant")` | settlement, resident | §8 |

Everything embodied (parties, carts, flocks, boats, displays, dressings, lights) is rebuilt from the books and never
saved with chunks.

---

## 10. Screens: Figma first (HUB_UI.md)

Everything above prefers the existing Hub pages: the **conversation** (portrait card A, cinematic bar B), the **journal**
(Quests page), the **market**, the **reputation** page, and dialogue options with odds chips. Design these in Figma (file
`3c0Wgc4ZKFSpm98fWJoARS`, page 295:2, next to section 651:37945) before code; the user picks; page 295:2 is then kept in
sync with what ships:

| # | Screen | Kind | Lane |
|---|---|---|---|
| F1 | Conversation with a stranger (pedlar, pilgrim, bandit, tollkeeper): portrait without name and trade, a hooded variant, a demand with coin icons (pay / talk / threaten / fight) | variant of the conversation page | A1 |
| F2 | **Household**: houses, servants and wages, larder, companions with their orders | **new** section of the Quests page (or a Hub segment) | B4 |
| F3 | **Companion gear**: their worn armour and held items, a small pack | **new** (until designed, gear changes only through "take this" / "give that back" options) | B4 |
| F4 | **People**: the named characters met, a line on where their story stands, relations you touched | **new** section of the journal | B2 |
| F5 | Retinue ranks per lord and their rights; burgher status per town | change to the reputation page | B4 |
| F6 | Contest line (archery scores, melee bouts), restyled like the quest HUD (Hearth-native) | **new** HUD | B1 |
| F7 | An event line under the arrival banner ("A wedding at the chapel"), with the open banner hook | change | B1 |
| F8 | Roads and posts on Atlas (a roads layer, post tiles) | Atlas's own UI (a request to Atlas) | A1, B3 |

Each new page state goes through `HearthState` sections (a registry lane 0 adds), actions through `HearthActions`, all
re-validated on the server.

---

## 11. Build plan

### 11.1 Batches

Eight lanes in two batches of four, after a short **lane 0**. The suggested split (A: 1, 2, 3, 6; B: 4, 5, 7, 8) has one
problem: **town events (3) are built from the other items** — the tax collector and bandit raids are §1's travellers and
bands, trials and the pillory need §5's crowds, fairs need §1's pedlars and caravans, weddings and funerals of named
characters and trials of feuds need §4. Built in batch A, events would code against half-made services. **Crowds (5)**,
on the other hand, depend on nothing new (standing, law and the npc module already give it everything) and batch B wants
it finished. So the better split is:

- **Batch A — the people in the world**: lanes A1 roads and travellers (item 1), A2 daily rhythm (2), A3 crowds (5), A4
  visible work (6). Independent of each other except two late, small steps (A4's stall wares use A2's stall callback;
  A4's handcarts use A1's cart entity).
- **Batch B — what they make together**: lanes B1 town events (3), B2 lives (4), B3 wild places (7), B4 the player's
  place (8). Each composes batch A's services; their dependencies on each other are soft (each guards on a service being
  present).

### 11.2 Lane 0 (coordinator, before batch A; medium)

So lanes never touch the same file, lane 0 lands in `hearth` first:

1. **API additions** (11.4), additive only.
2. **Skeleton init classes** for every new subpackage, each called once from its module's init (`NpcModule`, `SimModule`,
   `SocialModule`, `QuestModule`, `EconomyModule`, `PlanModule`, `RepairModule`, and the client inits), empty bodies:
   `sim/roads/Roads`, `npc/travel/Travel`, `client/npc/travel/TravelClient`, `social/rhythm/Rhythm`, `npc/crowd/Crowds`,
   `npc/work/field/FieldWork`, `social/events/TownEvents`, `sim/lives/Lives`, `social/lives/LivesFolk`,
   `sim/wild/Wild`, `plan/wild/WildPlanner`, `npc/companion/Companions`, `economy/household/Household`,
   `social/burgher/Burghers`, `quest/commission/Commissions`. Lanes never edit the module init files.
3. **Hooks** other lanes register with (internal, small):
   - `sim/world/Claims` — extra "is this column planned?" sources consulted by `SimWorld.isPlanned/isPlannedNow` (and so by
     `ExpanseClaims`): A1 roads, A4 the Great Mine square, B3 posts.
   - `sim/settlement/ResidentClaims` — pre-assigned jobs and homes, and residents kept out of the daily assignment,
     consulted in `SettlementClock.assignJobs/assignHomes`: B2 apprentices and masters, B4 servants and companions.
   - **Away** implemented in `SimSettlement` and the clock (`setResidentAway`), used by A1, A4, B4.
   - `npc/routine/ScheduleOverrides` — per-resident windows the schedule takes before its own: B2's named characters
     (A2 owns `Schedule` and keeps the call in place).
   - `quest/gen/OfferSources` — extra offer sources for the generator: A1 road happenings, B4 commissions.
   - `client/overlay/HearthState` section registry: B2 People, B4 Household.
4. **New jobs** (`JobType`), their `Station.of` mapping, and `about.job` lines in `data/hearth/hearth_dialogue/lw_jobs.json`
   (the test that every trade has lines stays green).
5. **Empty lang fragments** per lane (`lw_roads.json`, `lw_rhythm.json`, `lw_crowd.json`, `lw_work.json`, `lw_events.json`,
   `lw_lives.json`, `lw_wild.json`, `lw_place.json`) and empty data folders, so lanes add keys without colliding.

### 11.3 Lanes

| Lane | Item | Batch | Owns (new) | Shared touch points it owns in its batch | Depends on | Effort |
|---|---|---|---|---|---|---|
| **A1 Roads and travellers** | 1 | A | `sim/roads/*` (network, router, traffic, bands), `npc/travel/*` (parties, kinds, bandit and stranger behaviour, carts), `client/npc/travel/*` (cart model and renderer), `npc/look/WayfarerDress`, `plan/road/Exits`, `data/hearth/worldgen/*roads*`, `hearth_sim/roads/`, `hearth_dialogue/barks/road.json`, `hearth_quest/road/`, `lw_roads.json`, `docs/requests/atlas-roads.md` | `sim/atlas/Atlas` (site and strip **overlays**, which B3 then uses without editing Atlas), `sim/gen/WorldgenGuard`, `sim/gen/SettlementFeature`, `sim/apply/ChunkApplier` (road layer), `sim/world/SimWorld` (`roads()`), `sim/trade/Caravans`, `npc/caravan/*` (folded into travel), `npc/look/SkinComposer` (one call into WayfarerDress, dress for every new job), `npc/talk/Conversations` + `TalkPayloads` (stranger conversations), `build/archetype/infra/Road` + `build/kit/infra/{Dress,Roadbed}` (country dress, signposts, milestones), `plan/HearthPlanner.exits`, `standing/logic/GossipRoutes`, `quest/service/LivingQuests` (road happenings) | lane 0 | **high** (country road dress by an xhigh architect) |
| **A2 Daily rhythm** | 2 | A | `social/rhythm/*` (service, wash day, play, tavern night, watch rounds and lights, chimneys, calendar service), `hearth_social/rhythm/`, `barks/rhythm.json`, `lw_rhythm.json` | `npc/routine/{Schedule,Leisure,FolkBrain,Plans,Tasks}`, `social/calendar/*`, `social/life/{Bells,Dressings}` (+ the stall callback A4 uses), `social/folk/{Music,GatherBehaviour,TavernGamesBehaviour}` | lane 0 | medium |
| **A3 Crowds** | 5 | A | `npc/crowd/*` (stimuli, director, reactions, shutters, brawl, `CrowdService` impl), `barks/crowd.json`, `lw_crowd.json` | `repair/DamageRules` (ignore `open`), `law/justice/Offences` (+ brawling) | lane 0 | medium |
| **A4 Visible work** | 6 | A | `npc/work/field/*` (remote sites, drove, clamp, boats), `sim/land/GreatMines`, `sim/terrain/ExpanseGreatMines`, `social/life/StallWares` (new file), `build/archetype/extract/GreatPithead`, `barks/work.json`, `lw_work.json` | `npc/work/{HerderWork,FisherWork,HaulerWork,CraftWork,ExcavationWork}`, `npc/embody/{EmbodimentManager,EmbodimentSelector}` (remote sites), `npc/routine/RoutinePlacement` (commuters), `sim/land/Survey` (one call), `sim/terrain/ExpanseUnderground` (`waterY`), `plan/resource/*`, `plan/growth/Program` (pithead, pens), `economy` shop stock (`EconomyServiceImpl.shopStock`), `quest/gen/GoodsItems` (now the api's `GoodsItems`), `mixin/npc/EntityNoSaveMixin` (flock, boat, display tags) | lane 0; A2 (stall callback), A1 (cart) for the last steps | **high** (pithead by an xhigh architect) |
| **B1 Town events** | 3 | B | `social/events/*` (event book, rules, scene runner, domains and tax, raids, fairs and contests, tournaments, topping-out), `law/justice/Trials`, `repair/fire/FireLeash`, `hearth_social/events/`, `hearth_quest/event/`, `barks/events.json`, `lw_events.json`, `SceneService` impl | `social/life/{FamilyLife,Venues,Dressings,Bells,Notices}`, `social/calendar/TownCalendar` (fair and tournament days), `law/justice/{Justice,Jail}`, `repair/FireWatch`, `quest/service/{LivingQuests,QuestServiceImpl}` (`offerPersonal`), the build module's decay pass (`burnt` flavour, xhigh architect), the arrival banner hook (F7) | lane 0, A1, A2, A3 | **high** |
| **B2 Lives** | 4 | B | `sim/lives/*` (relations, apprentices, masters, notables, threads, `LivesService` impl), `social/lives/*` (behaviours), `hearth_lives/{roles,threads}/`, `lw_lives.json`, `client` People section | `social/life/Matchmaker`, `npc/talk/{Greetings,DialogueTemplates,TownNews,NpcDialogue}` (about-the-player categories), `plan/growth/Needs` (masters' workshops) | lane 0, A2 (overrides in place), A3 (brawl); B1's `offerPersonal` (soft: dialogue-only beats until then) | **high** |
| **B3 Wild places** | 7 | B | `sim/wild/*` (post sites on the atlas overlay, post rules), `plan/wild/*` (post planner and layouts), `build/archetype/wild/*` (`civic/hospice`; `wayside` and `portable` variants), `hearth_quest/wild/`, `hearth_names/posts.json`, `lw_wild.json` | `plan/HearthPlanner` (dispatch posts), `sim/settlement/SimSettlement` (`kind`, `postType`), `sim/SimServer` (post step interval), `sim/world/Hamlets` (no daughters from posts), `map/*` and `client/map/*` (post places and tiles) | lane 0, A1 (network, overlays); A2, A4 (soft) | **high** (archetypes by xhigh architects) |
| **B4 The player's place** | 8 | B | `npc/companion/*`, `economy/household/*`, `social/burgher/*`, `quest/commission/*`, `hearth_quest/commission/`, `lw_place.json`, client Household section | `npc/embody/EmbodimentManager` (companion pins), `npc/service/{NpcServiceImpl,Escorts}` (`pin`), `client/npc/FolkRenderer` (gear layer), `standing/logic/Titles` (Householder and sworn Burgher), `economy/{EconomyServiceImpl,works/Projects}` (`chargeDeposit`, council choice) | lane 0, A1, B1 (scenes, soft), B2 (feuds, soft), B3 (toll gates, soft) | **high** |

Rule for everything else: a file not listed belongs to its module's owner and no lane edits it; data, lang and test files
are new files per lane; a lane that finds it needs another lane's file writes `docs/requests/lw-<lane>.md` and works
around it, as phases 1–3 did.

### 11.4 API additions (lane 0; additive only)

Never changed: `PlayerSkills`, `PriceModifier`, `DialogueProvider`, `EconomyService.charge/price`, `QuestEvents`,
`QuestType`, `MarketEvents` (Mettle copies them). No new `QuestType` values: commissions are `OFFICIAL`, thread quests
`PERSONAL`, road quests `CARAVAN` and `LAW`.

```java
// JobType: + BANDIT, PILGRIM, MESSENGER, CARTER, PEDLAR (worn by strangers), TOLLKEEPER, SERVANT, HERMIT, MONK, BAILIFF
// Station.of: BANDIT/PEDLAR/CARTER/PILGRIM poor or peasant, MONK/HERMIT clergy, TOLLKEEPER/BAILIFF official, SERVANT poor

// HearthWorld
default RoadNetwork roads() { return RoadNetwork.NONE; }

// RoadNetwork (new)
public interface RoadNetwork {
	List<RoadView> near(ResourceKey<Level> dimension, BlockPos at, int radius);
	List<RoadView> from(long settlementId);
	@Nullable RoadView between(long a, long b);
	double roadDistance(long a, long b);             // -1 when not joined
	float danger(long roadId, double along);         // 0..1
	RoadNetwork NONE = ...;
	enum RoadClass { KINGS_ROAD, TRACK }
	record RoadView(long id, long from, long to, RoadClass kind, int[] points, float built, double length) {
		BlockPos at(double along) { ... }
	}
}

// TravelService (new; HearthServices.travel() / setTravel)
public interface TravelService {
	long dispatch(TravelKind kind, Settlement from, @Nullable Settlement to, @Nullable BlockPos destination,
		List<UUID> residents, long departTick, CompoundTag details);           // 0 if refused (caps)
	@Nullable TravellerView traveller(long id);
	List<TravellerView> near(ServerLevel level, BlockPos at, int radius);
	boolean waylay(long id, @Nullable UUID by);
	void recall(long id);
	enum TravelKind { CARAVAN, CARTER, PEDLAR, PILGRIMS, MESSENGER, PATROL, MARKET_GOERS, SETTLERS, ROAD_CREW,
		BANDITS, TAX_COLLECTOR, WEDDING_GUESTS, TROUPE, DIGNITARY }
	record TravellerView(long id, TravelKind kind, long from, long to, long departs, long arrives, List<UUID> residents,
		boolean embodied) { /* positionAt(tick) via the service */ }
}

// SettlementCalendar (new; HearthServices.calendar(), never null: NONE)
public interface SettlementCalendar {
	boolean restDay(Settlement s, long day);
	boolean washDay(Settlement s, long day);
	boolean marketDay(Settlement s, long day);
	@Nullable String feast(Settlement s, long day);
	List<String> events(Settlement s, long day);     // ids of the day's town events (§3)
	SettlementCalendar NONE = ...;
}

// CrowdService (new; HearthServices.crowds())
public interface CrowdService {
	boolean brawl(Settlement s, UUID a, UUID b, String why);
	void spectacle(Settlement s, BlockPos at, String kind, int seconds, int maxOnlookers);
	boolean shuttered(Settlement s);
}

// SceneService (new; HearthServices.scenes()) — B1 implements; B4's oath and dubbing use it
public interface SceneService {
	boolean stage(Settlement s, Identifier scene, List<UUID> principals, @Nullable ServerPlayer player, CompoundTag args);
}

// LivesService (new; HearthServices.lives())
public interface LivesService {
	List<Notable> notables(Settlement s);
	@Nullable Relation relation(Settlement s, UUID a, UUID b);
	List<Feud> feuds(Settlement s);
	void lifeEvent(Settlement s, UUID resident, String kind, @Nullable UUID player);
	record Notable(UUID resident, String role, String thread, String beat) {}
	record Relation(String kind, int strength) {}
	record Feud(UUID headA, UUID headB, String cause, long since) {}
}

// GoodsItems (new; HearthServices.goodsItems(), never null: a vanilla default)
public interface GoodsItems { ItemStack sample(Goods goods, Culture culture, long seed); }

// Settlement
default SettlementKind kind() { return SettlementKind.TOWN; }   // enum SettlementKind { TOWN, POST } (new)
default String postType() { return ""; }
default void setResidentAway(UUID id, long untilTick, String why) {}
default boolean isResidentAway(UUID id) { return false; }

// SettlementPlanner
default List<BlockPos> exits(Settlement s) { return List.of(); }  // where roads out of town end (arm ends, gates)

// NpcService
default boolean pin(UUID resident, UUID holder) { return false; }  // companions: never folded while pinned
default void unpin(UUID resident) {}
default List<UUID> pinned(UUID holder) { return List.of(); }

// EconomyService (charge and price untouched)
default boolean chargeDeposit(UUID player, long bits, String why) { return false; }
default Map<Goods, Integer> shopStock(Settlement s, int plotId) { return Map.of(); }

// QuestService
default boolean offerPersonal(ServerPlayer player, Settlement s, Resident giver, Identifier template, CompoundTag vars) {
	return false;
}

// SettlementEvents: + TRAVELLER (world, kind, id, from, to, phase DEPARTED|ARRIVED|WAYLAID|LOST)
//                   + TOWN_EVENT (world, settlement, kind, phase SCHEDULED|BEGUN|ENDED, outcome)
//                   + LIFE_EVENT (world, settlement, resident, kind, player?)
//                   + COMPANION (world, settlement, resident, player, phase HIRED|DISMISSED|LEFT|DIED)
```

### 11.5 Order, merging and tests

1. Lane 0 into `hearth`; build green.
2. Batch A in four worktrees off `hearth` (`hearth-lw-roads`, `-rhythm`, `-crowds`, `-work`); merge order A2 → A3 → A1 →
   A4 (A4's last steps need A2's callback and A1's cart).
3. Batch B off the merged `hearth` (`hearth-lw-events`, `-lives`, `-wild`, `-place`); merge order B1 → B3 → B2 → B4.
4. Every lane keeps the whole suite green (2 000+ tests), including `RealGrowthTest`'s growth targets, `BlueprintInvariantsTest`
   (light rule) for new archetypes, `SimDataTest`, the lang duplicate check and the `about.job` test; adds its unit tests
   and its tour id; runs its perf gate in the dev harness; runs game tests one at a time under the machine-wide game-test
   lock.
5. Integration tour after each batch (`HEARTH_LW_TOUR=all`): the shots of §1–§8 on one Expanse Earth world (seed
   20261003), by day and by night, with `/hearth sim stats` timings in the log.

---

## 12. Top risks

1. **Roads in worlds that already exist.** New edges change ungenerated regions (expected, like any atlas change) and are
   written live across generated land: edit safety stops them at player builds (a segment refused is rerouted round the
   obstacle), natural trees in the corridor must come down whole (the woodcutter's way, budgeted), and a road must never
   need a neighbour region's *history* — only its site and age, which are cheap and seed-only. Expanse's terrain
   softening (still to come) will move routes in regions not yet generated.
2. **Server cost of more bodies.** Travellers, flocks, boats and companions add up to ~300 entities beyond today's 400:
   hard caps, MSPT-scaled, nearest-first, perf gates in each lane's harness before merging.
3. **Abstract and embodied must agree.** Bandit robberies, raids, trials, contests and journeys decide their outcome in
   the books first; embodiment only enacts it unless a player intervenes. Any lane that lets an entity decide breaks
   this; `EventAgreementTest` and `TrafficTest` guard it.
4. **Permadeath piling up.** Raids, executions, bandits on the road and companions' deaths could empty small hamlets:
   low default chances, named characters die only through their thread or a player, config switches per server
   (`bandits.kill_chance`, `events.raid_deaths`, `events.executions`).
5. **Fire and griefing.** A live fire must never reach a player's build: the leash, and live fires off on servers by
   default.
6. **Eight lanes, shared files.** Lane 0's skeletons and hooks, the ownership table and the merge order are what keep it
   mergeable; a lane that edits outside its list is sent back.
7. **Writing volume and repetition.** Hundreds of barks, 13 threads, event and commission texts: data-driven with
   cooldowns, data tests for every reference, and a writing pass per culture; a town that repeats itself feels less
   alive than a quiet one.
8. **Figma gating.** F2–F4 (Household, gear, People) are new screens; until the user picks, those lanes ship through
   conversation options and the journal's existing lists.
9. **Expanse coordination.** Pass hospices must sit exactly where Expanse's refuge stood down (ask Expanse to expose its
   site query; fall back to the same rule on the probe), the Great Mine claim must be answered in historic regions before
   Expanse writes its pithead, and `LandClaims` stays a cheap lookup with road strips in it.
10. **Balance.** Tax, tolls, tithes, bandit losses and pilgrims' alms move coin and goods; re-run the economy's two-year
    test and the growth targets after B1.

## 13. Questions for the user (defaults in brackets)

- Executions: shown up to the gallows steps and then a cut, or never staged at all? [staged up to the steps]
- Live fires in multiplayer by default? [off on servers, on in single player]
- May bandits kill travellers on the road (permadeath)? [yes, rarely]
- Companions per player? [2]
- The top commission rank a household knighthood? [yes]
