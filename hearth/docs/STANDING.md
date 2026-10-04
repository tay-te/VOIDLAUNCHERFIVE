# VOID Hearth — standing (reputation)

The standing module answers *what do these people think of me?* in the three layers of docs/LIVING_WORLD.md §3 —
each resident's **personal opinion**, the player's **standing in each town** with its title, and **regional fame** that
travels between towns as gossip — plus how the player **comes across right now** (charisma and menace), **speech
checks** any module can call, and optional **companies** of players who share standing. It implements
`StandingService` (api) and is registered in `HearthServices.standing()`.

```
dev.voidmc.hearth.standing
  StandingModule      init: config, data keys, attachment, packets, service, events, commands, dev harness
  StandingEngine      the service: layers, deeds, gossip, fame, titles, drift, hearsay (plain logic over a host)
  StandingHost        what the engine needs from the game; GameHost is the real one, tests use a fake
  PlayerTracker       the one regular job: each player once a second (arrival, washing, title), gossip delivery
  Deeds               deed kinds the module understands (hearth:seed, hearth:adjust) and the ones its table describes
  StandingConfig      config/hearth/standing.json
  StandingDevHarness  -Dhearth.dev.standing=true self-check against the real sim
  logic/              Titles, Curves (soft cap, fading, drift), GossipRoutes (news over the road network)
  model/              TownLedger (per settlement), RegionData (per world), Memories (per resident), Company,
                      Companies, FolkFacts (reads the npc module's traits/mood/family), StandingKeys
  rules/              DeedRule, StandingRules, StandingData (data-pack loader)
  appearance/         Look (charisma and menace as pure functions), PlayerState (attachment), Appearances (probe)
  speech/             SpeechKind, SpeechOdds (pure), SpeechCheck (the utility other modules call), StandingDialogue
  net/                StandingPayloads, StandingNetwork
  command/            StandingCommands (/hearth standing, /hearth company)
client.standing       StandingClient, StandingHud (arrival line), ScreenLine (conversation and trade screens),
                      ReputationScreen (the page), ClientStanding (state), StandingClientTour (dev screenshots)
data/hearth/hearth_standing/{deeds,gossip,attire}/*.json
```

## 1. The three layers

| Layer | Range | Stored | Moved by |
|---|---|---|---|
| **Personal** — a resident's opinion of a player | −100…100 | `Resident.extra("standing").mem`: up to 12 memories `{player, kind, weight, day}` per resident | deeds that touch them (victim, family, witness), speech checks, hearsay |
| **Settlement** — standing per player per town, and its title | −100…100 | the settlement's `hearth:standing/town` data (`TownLedger`) | deeds there, gossip from elsewhere, company shares, daily drift |
| **Regional** — fame around a place | 0…100 (signed renown inside) | the world's `hearth:standing/region` data (`RegionData`): places of fame per player | great deeds and great crimes |

**Personal opinion** is the sum of what is left of a player's memories (each fades with a half-life of 20 days; strong
ones, |weight| ≥ 40, four times slower) plus a quarter of the town's standing: folk see you partly as their town does.
Memories are bounded per resident (the faintest is forgotten first) and the same kind of deed by the same player within
a day is one memory (the stronger), so a crime reported once by the victim and again when confirmed is not counted
twice. They live in the resident's record, so they are saved with the person and die with them.

**Settlement standing** is the authority. `TownLedger` keeps it fractional (small shares and slow drift add up) and
rounds half away from zero when read. Changes away from 0 are softened (`town.soft_cap` 0.5: at ±100 a deed moves it
half as much), so a hero is made by many deeds. Bad names heal by 0.25 a day (`negative_drift_per_day`); good names keep
(`positive_drift_per_day` 0). Every change fires `SettlementEvents.STANDING_CHANGED` (from, to titles; they can be equal
when only the number moved) and is mirrored into the sim's own `Settlement.reputation` number for modules that still
read it (§9).

**Titles** (`logic/Titles`, thresholds in config): Outlaw ≤ −60 (or whenever `LawService.outlaw` says so), Distrusted ≤
−20, Stranger, Known ≥ 10, Friend ≥ 40, **Burgher** for anyone owning a plot in the town (read from
`Settlement.plots()` owners) at standing ≥ 0, Hero ≥ 80. A title that changes without a deed (a house bought, the law's
verdict) is noticed by the tracker while the player is in town and announced then.

**Regional fame** is a handful of *places of fame* per player (`fame.sources` 32; nearby deeds merge). Fame at a point
is the sum of each place's magnitude × (1 − d/2000)² × fading (half-life 60 days), capped at 100. It is signed inside
(renown vs infamy; `StandingEngine.renown`), unsigned through the api (infamy is fame too).

## 2. Deeds

Everything that moves standing is a deed reported through `StandingService.deed(Deed)`: `player`, `settlementId` (0 for
none), `kind` (namespaced), signed `weight` (+1 a small kindness, +25 saving the town, −30 a theft, −100 a murder),
`resident` (the victim, the quest giver), `pos`, `witnessed`. The **deed table** (data,
`data/<ns>/hearth_standing/deeds/*.json`) maps the kind to factors of the weight:

```json
{"deeds": {"law:theft": {"settlement": 1.0, "victim": 1.5, "witness": 0.6, "family": 0.5, "gossip": 0.6, "fame": 0.4,
                         "violent": false, "news": true, "action": ""}}}
```

| field | effect |
|---|---|
| `settlement` | moves the town's standing |
| `victim` | memory with the resident named in the deed |
| `family` | memory with their spouse, children and parents (× `opinion.family_share`) |
| `witness` | memory with each embodied folk who saw it (`NpcService.folkNear` + `awake` + `canSee`, within 24 blocks, at most 12) |
| `gossip` | how much travels to other towns (§3); 0 never |
| `fame` | how much makes the player known around `pos` (× `fame.scale`; only if ≥ `fame.threshold`) |
| `violent` | adds |weight|/2 to the player's name for violence (menace; fades with a 30-day half-life) |
| `news` | folk in the town talk about it (kept in the ledger's news, 12 per town) |
| `action` | `wash`: the player is clean and freshly bathed (the bathhouse reports `social:bathed`) |

Kinds not in the table use `<namespace>:default`, then `default`. The shipped table covers `quest:*`, `economy:*`,
every `law:<offence>` plus `law:fine_paid`, `law:jail_served`, `law:pillory`, `repair:*`, `social:*`, `npc:gift`,
`npc:insult`, `npc:rescue`, `defense:*`, `standing:amends`.

**Unwitnessed deeds** touch only the people directly involved: a bad deed nobody saw moves the victim (and family) but
not the town, makes no news, no gossip and no fame (`town.unwitnessed_bad` 0); reported again when discovered, it
counts for the town then. A good deed "not witnessed" (a quest handed in) counts in full (`town.unwitnessed_good` 1).

Two kinds are the standing module's own: **`hearth:seed`** sets a player's standing outright, once (the sim hands its old
numbers over with it), and **`hearth:adjust`** moves the town's standing by exactly the weight with nothing else — what
`Settlement.addReputation` becomes when the sim delegates it here.

## 3. Gossip

A deed whose `weight × gossip` reaches 20 (`gossip.threshold`), and that folk know of, goes out as news to the towns
around (`logic/GossipRoutes`): the known sites within 6000 blocks (`HearthWorld.knownSites`, live or not yet settled)
are linked to their 4 nearest neighbours within 3000 blocks — the shape of the roads between towns — and news takes the
shortest path, roads counted 1.25 × the crow's distance. It arrives after `distance / 1500` days (at least 1), with
`(1 − distance/6000) × 0.85^(towns passed)` of its strength, and moves the town's standing by half of that
(`standing_factor`). The town records it as news (folk tell you "Word from Ashbrook: they say Alex killed someone in
Ashbrook"), and the player, if online, is told ("Word of your deeds has reached Coldbrook (from Ashbrook)").

The queue is world data ordered by arrival day (≤ 2048 items; the faintest is dropped when full) and is checked every
100 ticks — one comparison when nothing is due. News for a town that is not settled yet waits up to 60 days and is
delivered when the sim adopts it (`FOUNDED`).

**Hearsay** — gossip between folk inside a town: three times a day per town someone passes the strongest thing they
saw or suffered at a player's hands in the last week on to a friend (npc `friends`, else a neighbour), who remembers
30 % of it as `hearsay:<kind>`. Hearsay is not passed on again, and a listener hears each story once.

## 4. Charisma and menace

`Look` (pure) from `Appearances.look(player)`:

- **cleanliness**: 1 just washed → 0 after 3 days (`appearance.dirty_after_days`); a swim (under water or swimming)
  washes; standing in water by a town's `BATH` POI, or a `social:bathed` deed, is a proper bath (+0.10 for a day);
- **blood**: hurt in a fight or striking a blow (≥ 2 damage) shows for 5 minutes or until washed off;
- **attire**: dyed leather in the local culture's colours (data `hearth_standing/attire`) is local dress, other colours
  foreign, plain leather a peasant's clothes; armour beyond leather (armour points > 7);
- **weapon drawn**: swords, axes, spears, maces, tridents, bows, crossbows in the main hand;
- **violence**: the player's name for violence (§2).

Charisma (−1…1): clean +0.25 … filthy −0.35, bathed +0.10, local dress +0.07 a piece, foreign −0.03, armour up to
−0.25, weapon −0.20, blood −0.35. Menace (0…1): armour up to 0.40, weapon 0.30, blood 0.15, violence up to 0.25.
Body state is a persistent player attachment (`hearth:standing/player`, copied on death).

## 5. Speech checks

`speech.SpeechCheck` is the utility for every module (law: guards and witnesses; economy: haggling; quests):

```java
SpeechOdds.Odds odds = SpeechCheck.odds(player, town, resident, SpeechKind.PERSUADE, 0.2, 0);
out.add(new Option(id, SpeechCheck.label(odds, Component.literal("Surely you can let it pass?")), 300));
SpeechCheck.Outcome o = SpeechCheck.attempt(player, town, resident, SpeechKind.BRIBE, 0.3, bits); // never takes coin
long ask = SpeechCheck.expectedBribe(resident, 0.3);
```

Odds (`SpeechOdds`, pure, every factor kept for display) come from standing, opinion, charisma, menace, mood, the
resident's traits (`Resident.extra("npc").traits`), station and job, the difficulty of the ask, the coin offered over
what they expect (bribes), and tries already made today; clamped to 5–95 %. The greedy take bribes (+25 %), the pious
refuse them; the brave shrug off threats (−25 %), the cautious and shy give in; the grumpy are hard to flatter; guards
and officials are hard to frighten; a lord expects 500 bits where a labourer takes 10. An attempt leaves a memory
(`SpeechKind`: a successful threat −4, a failed one −8, a refused bribe −5, successful flattery +6…).

**In conversation** (`StandingDialogue`, order 40–49): *Say something kind* (flatter, once a day per person), *What do
folk here say about me?* (their verdict on your title, a piece of news about you, and their own memory of you — someone
who dislikes you refuses until you persuade, intimidate or bribe them), *Any news from the other towns?*, and with
someone who thinks ill of you *I'd like to make amends* (persuade) or *Let me make it up to you* (bribe; needs the
economy module). Each check shows its odds in the label; the reply is spoken by the folk (or written in chat), and the
outcome shows on the screen's standing line.

## 6. Companies

`/hearth company form <name>`, `invite <player>`, `join <name>`, `leave`, `info [name]`, `list`. A player belongs to at
most one company (8 members, invitation needed — config). When a member's standing in a town changes through a deed,
every other member's moves by 25 % of it (`companies.share`; not passed on again). The founder leads; when they leave,
the oldest member does; the last to leave disbands it. `companies.enabled` false turns the whole thing off.

## 7. Client

- **Arrival**: walking into a town sends `standing/here`; under the sim's banner a line in the title's colour, "a Friend
  of Ashbrook · standing +42", and how known the player is around here, fading with the banner (once a minute per
  town).
- **Screens**: on the conversation, market and bank screens, a small line along the bottom: the town, title and standing;
  in conversation also "Edda Miller thinks well of you (+18)" and the last speech check's outcome. Those screens belong to
  other modules: the line is drawn after them through Fabric's screen events, the screens recognised by class name.
- **Reputation page**: the K key (`key.hearth.standing`) or `/hearth standing`: fame here, charisma and menace with what
  folk notice, the company, and every town the player has dealings with (title, a bar from outlaw to hero, tier, culture,
  distance), nearest first.

## 8. Commands

```
/hearth standing                                   the reputation page (chat lines for clients without the mod)
/hearth standing show <player>                     (GM) the page as chat lines
/hearth standing set <player> <value> [settlement] (GM) set standing (default: the settlement the player is in)
/hearth standing deed <player> <kind> <weight> [witnessed]  (GM) report a deed where the player stands
/hearth standing opinions <player>                 (GM) the town's residents' opinions of the player, their looks
/hearth standing gossip                            (GM) news on the road and held
/hearth company form|invite|join|leave|info|list   companies
```

## 9. Working with the other modules

- **Reporting**: every module reports deeds rather than moving numbers (`Deeds` lists the kinds the table knows).
- **The sim's reputation numbers**: until the sim delegates `Settlement.reputation/addReputation` to this service
  (docs/requests/p3-standing.md 1–2), a player's first standing in a town is seeded from the sim's number and every
  change is mirrored back into it (`town.mirror_to_settlement`). Once it delegates, the seeding read comes back here and
  is answered without recursion, and the mirror's echo (an `hearth:adjust` deed during the mirror) is ignored.
- **Reads**: `standing`, `title`, `opinion`, `fame`, `charisma`, `menace`, `company` through the api. `charisma` and
  `menace` read the player's inventory and body state; cheap.
- **npc fields read** (by name, from `Resident.extra("npc")`): `traits`, `mood`, `spouse`, `children`, `parents`,
  `friends`.

## 10. Config (`config/hearth/standing.json`)

All fields optional; the file is written empty and every default applies until a field is set. Groups: `titles`
(thresholds), `town` (soft cap, drift, unwitnessed shares, seeding and mirroring, news, the arrival line, tracker
interval), `opinion` (memories, half-lives, town influence, witnesses, family, hearsay), `gossip` (enabled, threshold,
reach, speed, roads, fading, queue, held days, `notify`), `fame`, `appearance` (enabled, dirt, blood, baths, violence),
`speech` (the conversation options on/off, odds limits, bribe multiplier, consequences), `companies` (enabled, share,
size, invite only). See `StandingConfig` for each field.

## 11. Cost

Nothing runs per tick in proportion to the world. Deeds, gossip and hearsay are event-driven; the tracker looks at each
player once per `track_interval` (20) ticks, staggered (a spatial lookup, a title check, a water check); gossip delivery
is one comparison every 100 ticks unless news is due; drift is a pass over a town's ledger once a sim day, and announces
only entries whose rounded number changes.

Measured on the dev server (4 Expanse Earth towns, real atlas, this machine): **62–76 µs per deed** including gossip
routing over the atlas, 94 deliveries in 1.6–1.9 ms, a day passing in every town 0.8–0.9 ms (2000 deeds of 40 players
in their ledgers and memories). Unit test: 500 deeds with gossip over 41 towns well under a second.

## 12. Seeing it work

- Unit tests: `./gradlew test --tests 'dev.voidmc.hearth.standing.*'` (engine with in-memory towns: seeding and
  delegation, crimes and memories, unwitnessed deeds, gossip timing and fading, held news, companies, fame, drift,
  titles, hearsay; odds and looks; data and saved-state round trips).
- Dev server self-check, logged as `[hearth-standing]` (towns in reach, every deed's effect, news on the road and its
  arrival, fame, hearsay, odds for real residents, a bench; a second run first logs what the first saved):

  ```
  JAVA_TOOL_OPTIONS="-Dhearth.dev.found=4 -Dhearth.dev.radius=3 -Dhearth.dev.fillCatalogue=true \
    -Dhearth.dev.standing=true -Dhearth.dev.standing.stop=true" ./gradlew runServer
  ```

- Client screenshots (`run/screenshots/hearth_standing_*.png`: arrival line, page, conversation with odds, after
  flattery), into a world with live towns (e.g. the dev server's world copied to `run/saves/`):

  ```
  JAVA_TOOL_OPTIONS="-Dhearth.dev.standing.tour=true" ./gradlew runClient --args="--quickPlaySingleplayer <world>"
  ```
