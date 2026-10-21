# VOID Hearth — town life and the player's life in town (the social module)

The social module makes a settlement keep a calendar and an order, and gives the player a life there
(docs/LIVING_WORLD.md §5–§6): weekly market days, bells at dawn, noon and dusk, festivals, weddings, funerals and births
on the notice board; the reeve, the guard captain, the priest, the master builder and, in towns, the lord and steward;
inns, bathhouses and stables; work for wages; and dice in the tavern. Code: `dev.voidmc.hearth.social`
(+ `client.social`), config `config/hearth/social.json`, lang `assets/hearth/lang/en_us/social.json`.

```
social/
  SocialModule      init: config, ledger key, items, dice menu and payloads, town life, dialogue, behaviours, events, commands
  SocialConfig      config/hearth/social.json (times as "HH:MM")
  SocialLedger      per-settlement record (HearthDataKey hearth:social/ledger)
  FolkFacts         reads traits and family from extra("npc") / extra("sim"); records marriages
  Hooks             guarded calls into economy, standing, law, npc
  SocialItems       hearth:loaded_dice
  calendar/         SocialTime (town ticks), TownCalendar (market days, feast days), Festival, TownEvent, Agenda (today, who comes)
  life/             TownLife (hub), FamilyLife, Matchmaker, Notices, Venues, Bells, Dressings
  office/           Office, Succession (pure), Officials (appoint and keep)
  folk/             GatherBehaviour, StallKeeperBehaviour, CrierBehaviour, DiceSeatBehaviour, TavernGamesBehaviour, Music
  service/          Inn, Bathhouse, Stables, WageTasks + WageTask, VisitingWares, ServiceSites, PlayerLife (attachment)
  dice/             Farkle (rules), DiceAi, DicePersonality, DiceGame (state machine), DiceMenu, DiceView, DicePayloads, DiceTables
  talk/             SocialDialogue (DialogueProvider)
  command/          SocialCommands (/hearth social ...)
  dev/              SocialDevHarness (-Dhearth.dev.social=SECONDS)
client/social/      SocialClient, DiceScreen
```

## 1. Town life

### Time

Town life reads the sim calendar: a day runs from sunrise (06:00) to the next sunrise, a sim hour is 1000 ticks. An
instant is a **town tick**, `day × 24000 + ticks since sunrise` (`SocialTime`), so events compare directly with the clock
even when a settlement's calendar has been fast-forwarded. Night festivals end past midnight inside the same sim day.

### The calendar (`TownCalendar`, pure)

- **Market day** once in `market.week_days` (7). Each settlement keeps its own weekday (a hash of its id), so towns
  along a road hold market on different days and a trader can go round them. From `market.min_tier` (hamlet).
- **Feast days** in a festival year of `festivals.year_days` (56): **midsummer** (day 14), the **harvest feast** (42),
  the **culture's own festival** (28), and **founders' day** (the settlement's founding day of the year). Culture
  festivals: May Day (temperate), Blossom (wisteria), Midwinter (northern), the Cattle Drive (alpine), the Long Night
  (tundra), Beacons (moorland), the Blessing of the Boats (maritime), Carnival (tropical), the Night of Stars (desert),
  the Vintage (mediterranean), the Summer Games (steppe), Lanterns (karst), the Crawfish Boil (bayou), the Sunfeast
  (mesa). Each names what folk carry and how the square is dressed; Midwinter, the Long Night, Beacons, Stars and
  Lanterns are night festivals (two hours later, past midnight). Festivals run 17:00–23:00 by default.

### Today (`Agenda`)

The day's events: the market (07:00–17:00), a festival, a **wedding** (11:00, an hour) and its **feast** (three hours
after), a **funeral** (10:00, an hour), and the **lord's court** (10:00–13:00, on days without market or festival).
`Agenda.attends(event, resident, now)` decides who comes, fixed per person and event so nobody flickers in and out:
kin and principals always; market crowds a `crowd_share` per hour (working adults only in their 12:00 dinner hour,
children, elders and the idle any hour); festivals a `share` (85 %); wedding guests a `guest_share` (40 %, the same
guests at ceremony and feast); mourners a `mourner_share` (25 %, no children); petitioners at court 6 % an hour.
`forResident` picks one by precedence: funeral, wedding, court (principals), feast, festival, market.

### Where (`Venues`)

From blueprint POIs: market — the square (a `MEETING_POINT` in a `landscape/plaza`, else the meeting point nearest the
heart) and `MARKET_STALL`s; festival — the `STAGE` nearest the square, else the square; wedding — the `ALTAR` nearest the
heart, guests at the `SEAT`s (pews) of that building; feast — the `TABLE`s of the inn, tavern or festival ground, else
the square; funeral — a built `civic/cemetery`, else the altar; court — the `THRONE`, else the lord's seat. Without any
of these, the settlement's centre. Folk spread round their spot on a ring onto standable ground (`standFor`).

### Families (`FamilyLife`, `Matchmaker`, data only — runs abstractly too)

- **Weddings.** Each day a settlement has a `wedding_chance` (8 %) of a match: among up to eight unmarried grown
  residents (a seeded draw), the two nearest in age who are not close kin (parents, children, siblings, spouses) and not
  already promised; opposite sexes, or (with `same_sex`, a `same_sex_share` of matches) two men or two women. The banns
  go up and the wedding is held `banns_days` (3) later; on the day, if both are still there and free, they are married
  (the npc module's `spouse` link on both, `extra("social").married_day`), the second moves into the first's home, and
  the feast eats `wedding_food` (12) from the stockpile. A death or departure calls the wedding off (a notice says so).
- **Deaths.** `RESIDENT_REMOVED` without the sim's `left_for` mark: an elder died of age; a folk killed (an entity
  `hearth:folk` died that tick) was killed, or murdered if a player killed them or the law reports a `MURDER`; an adult
  removed otherwise left town (no funeral). Deaths get a notice and a **funeral** `funeral_delay_days` (1) later with
  their kin (found from the living, whose links still name the dead).
- **Births.** `RESIDENT_ADDED` of a child born today with parents in town: a notice.

### Notices (`Notices`)

Births, banns, weddings called off, deaths, funerals, departures, appointments, and feast days three days ahead. Kept in
the ledger (`life.max_notices`, 12; the oldest past ones go first), never posted twice (a caught-up day is safe), told to
players in the settlement when posted (`announce_in_chat`; not for days caught up long after), read out in the square by
the **crier** (the reeve, else the priest, else the eldest adult) when the town is embodied, and listed in conversation
("Anything on in town?") and by `/hearth social notices`. An argument written `@key` is translated. The notice board
itself is the quest module's (docs/requests/p3-social.md §2).

### Bells (`Bells`, embodied only)

At `bells.times` (06:00 work begins, 12:00, 17:00 work ends) the bell tower, clock tower, cathedral, temple, pagoda,
monastery, convent, chapel or a `landscape/bell` rings `strokes` (3, 4, 3) a stroke every 1.5 s, heard over
`16 × volume` blocks (80); big towers ring deeper. Only the sound: striking a real bell block would send vanilla villagers
into hiding. A jump of more than half an hour (sleeping, `/time set`) rings nothing.

### Market days and festivals, seen (`Dressings`, embodied only)

- **Stalls.** When market opens in an embodied settlement, `stalls` (2) plus `stalls_per_tier` per tier above the
  minimum (at most `max_stalls`, 6) are set up on free ground in rings round the square: three wide and two deep, posts
  of the culture's wood, a counter of stripped log with wares on it (a melon, a cake, pumpkins, flowers, a lantern),
  a canopy striped in the culture's colours, room behind the counter for the keeper and in front for customers.
- **Festival dressings.** Poles with the town's colours and a lantern (`POLES`), hay bales with jack o'lanterns (`HAY`),
  a bonfire at the heart with poles round it (`BONFIRE`), lantern posts (`LANTERNS`), and two trestle tables.
- **Safety.** Only air is replaced; never within two blocks of the heart of the square or of a door; never where anyone
  stands; every block is written to the ledger before it is placed, placed without neighbour updates, and taken down
  only if it is still that block — when the event ends, when the town stops being embodied, when the server stops, or
  (if the chunks were unloaded) the next time the town is embodied. Placement reads blocks only in loaded chunks.
- **Abstractly**, a market day pays `stall_fee` (2) per stall into the treasury and a feast day eats
  `feast_food_per_head` (0.5) food a head from the stockpile, once per day (`SocialLedger.feastDay`).
- Attending a festival, wedding, feast or funeral (within `attend_radius`, 24) is a small kindness: one standing deed
  `social:attended_<kind>` (+1) per player per event.

### Folk behaviours (registered with `NpcService` when it is present)

| behaviour | priority | who | does |
|---|---|---|---|
| `DiceSeatBehaviour` | 150 | a folk playing dice with a player | stays, faces the player, throws on their turn (holds a bone) |
| `CrierBehaviour` | 95 | the crier, 07:00–20:00, an uncried notice of today or yesterday | walks to the notice board with a paper, reads it out ("Hear ye, hear ye!") |
| `StallKeeperBehaviour` | 90 | on market day, one keeper per stall: merchants and traders first, then bakers, butchers, fishers, farmers, tailors, smiths... in a fixed order | stands behind the counter, holds up their ware, calls it out to players passing |
| `TavernGamesBehaviour` | 60 | 18:30–23:00, two adults per `GAMING_TABLE`, drawn afresh each night (no guards, priests, lords, innkeepers; never someone due at a gathering) | stands at the table, throws dice in turn (bone sound), groans and crows when a player is near — someone to challenge |
| `GatherBehaviour` | 80 | whoever `Agenda` sends to an event | walks there (slowly to a funeral), spreads round the venue facing what matters (the couple at the altar, kin in front of a funeral with candles, the lord at the throne), cheers and talks now and then; one **musician** per festival or feast (an entertainer, else a cheerful soul) plays tunes on the culture's instrument with a drum on the beat (note particles) |

All of them only run for embodied settlements; `wants()` is a few map lookups (the day's agenda is cached per
settlement and day). Behaviours are registered in `init()` if the npc module registered its service first, otherwise
on `SERVER_STARTING`.

## 2. Officials (`Officials`, `Succession`, `Office`)

| office | when | from | gives |
|---|---|---|---|
| reeve | village+ | the best suited adult or elder (merchants and artisans, industrious and sociable, years) | job `REEVE`, seat: town hall, courthouse or reeve's house |
| guard captain | hamlet+ | the senior guard (brave, industrious) | title only |
| parish priest | hamlet+ | the senior priest (pious) | title, station clergy |
| master builder | hamlet+ | the senior builder (industrious) | title only |
| lord | town+ | the last lord's eldest grown child (`dynasties`), else the highest station (gentry, merchants...) | job `LORD`, station lord, seat: palace, castle, keep, manor or town hall |
| steward | town+ | scholars, merchants, gentry; industrious | job `STEWARD`, station gentry, same seats |
| judge | a built `law/courthouse` (`judges`) | scholars, officials; elders favoured | job `JUDGE` |
| jailer | a built `law/jail` | guards; brave | job `JAILER`, seat: jail, guardhouse or watch house |

Run on every `DAY_PASSED` (after the sim has handed out jobs, so posts get their job and seat back), on `TIER_CHANGED`,
when a court, keep or town hall is finished, and at once when a holder dies or leaves. Incumbents keep office while they
can; one office each. Holders carry `extra("social").office` = the office id; other modules find them by job or by that
field. Appointments are posted. (The sim should keep posts through its daily assignment: docs/requests/p3-social.md §6.)

**The lord's court** is held 10:00–13:00 at the throne (or the lord's seat) on ordinary days: the lord and steward
stand at the throne, petitioners come and go.

## 3. Player services (conversation; paid through `EconomyService`, coin into the treasury)

- **Inn** (an innkeeper, or anyone working at an inn, tavern, hostel or caravanserai):
  - *A bed for the night* (`inn.bed`, 6): a free bed of the inn (not one its own family sleeps in, not another guest's)
    becomes the player's respawn point until checkout at `inn.checkout` (09:00) the next morning; then their previous
    respawn point is given back (if they have not set another). Kept in the persistent attachment `hearth:social/player`.
  - *A room with a chest, by the day* (`inn.room`, 12): a guest chest near a bed (not the inn's store by the counter);
    locked to every other player (opening and breaking; creative players excepted) while rented and for
    `room_grace_days` (2) after, then the lock lapses. "Another day in my room" extends it.
  - *A hot meal* (`inn.meal`, 3): hunger filled, a little regeneration.
  - *News from the road*: what neighbouring live towns have on this week (feast days, market days), with direction and
    distance.
- **Bathhouse** (a bath keeper, or anyone working at `civic/bathhouse`): *a hot bath* (`bath.price`, 4) washes off
  poison, hunger and nausea, fire and frost, warms (regeneration), records `PlayerLife.lastBath`, and reports the deed
  `social:bath` for the standing module's cleanliness. Splash and steam at the tub (`BATH` POI) if one is near.
- **Stables** (a stablemaster, or anyone working at `noble/stables`, a caravan yard or caravanserai; with
  `inns_hire`, innkeepers whose inn has a `HITCHING_POST`): *hire* a mount (`hire_per_day` 6 + `deposit` 40) — saddled,
  tamed to the player, by the hitching post or beside them, tagged `hearth.hired`; *return* it to any stable within
  `return_radius` for the deposit less overdue days (paid by that town); a dead or lost mount forfeits the deposit.
  *Buy* one (`buy`, 160): a better animal. Desert and mesa towns keep camels, alpine towns mules, the rest horses.
- **Work for wages** (07:00–18:00; one job at a time, `per_day` 3 per town; against `time_limit_ticks`, 6 minutes;
  progress on the HUD):
  - *Harvest* (farmers, farm workers): bring in `harvest_crops` (16) ripe crops from the town's farm plots — each is
    counted and re-sown as you go — then hand the produce to a farmer: `harvest_wage` (1) a crop, the crops into the
    stockpile (wheat as grain, the rest as produce).
  - *Haul* (quarriers, miners, masons, quarry and mine workers): a load of `haul_load` (32) of the town's building stone
    (sandstone, red sandstone, Expanse limestone or cobblestone) marked as the town's; deliver it to a builder for
    `haul_wage` (6), the stone into the stockpile. Run out of time with it and the town suspects theft (a standing deed
    and an unwitnessed theft reported to the law).
- **Visiting trader** (market day): stallholders and merchants sell the wares of a trader from the nearest town of
  another culture (or far off): three wares a day from `data/<ns>/hearth_social/wares/<culture>.json`, `ware_stock` (4)
  lots each, at `visiting_markup` (1.3) over the town's own price; a fifth of the coin stays in town, the rest goes to
  the trader's town if it is live. Wares are real-world goods (no glowing or fantasy blocks: those belong underground);
  items of a mod that is not installed, or that it renamed, are skipped. The module reads no Expanse biome ids.

## 4. Officials' business (conversation)

Reeve: *How fares the town?* (tier, population, buildings going up and waiting on goods, your standing), *Who holds the
offices here?*, *Is there paid work about?* (who to ask). Guard captain: *Any trouble about?* (your bounty if wanted).
Priest: *Alms for the poor* (5 bits; +1 standing once given), *Any weddings or burials coming?*. Master builder: *What
are you building?*. Lord: *I would pledge my service* (once; +3 standing), *Tell me of your lands*. Steward: *A gift for
the town's coffers* (1 crown; +5 standing), *How full are the coffers?*. Judge and jailer: *How stands the law with me?*.

## 5. Tavern dice (`dice/`, `client/social/DiceScreen`)

**Rules** (`Farkle`): six dice. A single 1 is 100, a single 5 is 50; three of a kind is the face × 100 (three 1s 1000),
each die beyond three doubles it; runs 1–5 = 500, 2–6 = 750, 1–6 = 1500. Roll; set aside at least one scoring die (every
die you pick must score); roll the rest or bank. A roll with nothing scoring loses the turn's points. All six set aside:
roll all six again. First to `dice.target` (2000) wins. The best score of every handful is tabled at class load.

**Who plays**: a folk at a `GAMING_TABLE` within 8 blocks, an innkeeper or entertainer, or anyone in a tavern or inn
(anywhere with `dice.anywhere`); grown, not a priest, not distrusting the player (title outlaw or distrusted, or their
opinion below −20), not busy with another player, and not cleaned out today (lost `daily_loss_limit` × their largest
stake). **Stakes**: the config's list (5–100 bits), up to the folk's largest stake — by station (poor 5 … lord 250) ×
temper, never more than a quarter of the town's treasury (the folk's purse stands in for now) — and the player's purse;
"for honour" without an economy or when nothing fits. Both stakes go on the table when the game starts; the winner takes
the pot; leaving mid-game forfeits it.

**Folk play** (`DiceAi`) by the exact odds: for every valid set they could keep, banking now against
`(1 − P(bust with n dice)) × (points + average roll of n dice) × boldness`, bolder when the player is about to win,
calmer when far ahead, with a little noise. **Tempers** (`DicePersonality`, from npc traits): careful (cautious, shy),
shrewd (curious, industrious), bold (brave, cheerful), reckless (greedy, dreamy), sour (grumpy) — each with its own
boldness, eye for cheats, stakes and words for every moment of the game.

**Loaded dice** (`hearth:loaded_dice`, two bones and an iron nugget): carried to the table, they can be put in play;
each of your rolls tips a bad face once (`loaded_bias`, 0.45). Every loaded roll risks being noticed:
`detect_base` (0.03) + the folk's perceptiveness + 1.5 % per loaded roll so far. Caught: the game ends, the folk keeps
the pot, the dice are taken, the folk will not play you again today, a standing deed `social:dice_cheat`
(`cheat_standing`, −15), and (with `cheat_report`) a witnessed theft of the stake reported to the law.

**The screen**: a dark oak frame round green baize; your bone dice (the one-pip red) tumble across it when thrown, scoring
dice glow under the mouse, picked dice lift edged in gold, dice set aside wait in the tray; brass plaques for both scores
with the race to the target; the folk's words and what happened on a parchment strip; buttons for roll on, bank, pass
the dice, loaded dice and play again; a "?" card with the scoring rules. The server rolls everything
(`DicePayloads.Action` up, `DiceView` down); the folk's turn plays out a step every 22 ticks so you can follow it.

## 6. Commands

```
/hearth social events | notices | offices        what the settlement you stand in has on, its notices, its officials
/hearth social status                            embodied settlements, bell strokes queued, dressing blocks   (op)
/hearth social force market|court|wedding        hold one now (a wedding marries a match at once)              (op)
/hearth social force festival [name]             hold a festival now, with its dressings and musician          (op)
/hearth social bell                              ring the bell now                                             (op)
/hearth social day                               run the day's social business now (matches, offices, notices) (op)
/hearth social dice [n]                          sit down to dice with the n-th adult resident                 (op)
```

**Dev harness** (dedicated server, with the sim's harness founding towns; see SIM.md §12):

```
JAVA_TOOL_OPTIONS="-Dhearth.dev.found=2 -Dhearth.dev.radius=3 -Dhearth.dev.simulate=60 -Dhearth.dev.social=90" ./gradlew runServer
```

Once a live settlement exists, the most populous is held embodied (`/hearth sim embody`), its day's social business
run (offices, matches, notices: logged), a market and the harvest feast held and the bell rung; every 20 s the stalls
and dressings are counted block by block in the world; after the given seconds it is let go and the harness logs how
many dressing blocks remain (it should be none). Lines are prefixed `[hearth-social]`.

## 7. Config: `config/hearth/social.json`

Every field optional (defaults above); times `"HH:MM"`. Sections: `life` (enabled, pass_ticks, announce_in_chat,
attend_standing, attend_radius, max_notices), `bells` (enabled, times, strokes, volume), `market` (enabled, week_days,
min_tier, open, close, stalls, stalls_per_tier, max_stalls, crowd_share, visiting_wares, visiting_markup, ware_stock,
stall_fee), `festivals` (enabled, year_days, midsummer_day, culture_day, harvest_day, founders_day, min_tier, start, end,
share, feast_food_per_head, decorations, music), `family` (weddings, wedding_chance, banns_days, wedding_time, same_sex,
same_sex_share, wedding_food, funerals, funeral_delay_days, funeral_time, births, guest_share, mourner_share), `offices`
(enabled, reeve_tier, lord_tier, judges, court_start, court_end, dynasties), `inn` (enabled, bed, checkout, room,
room_grace_days, meal, meal_regen_seconds), `bath` (enabled, price, regen_seconds), `stables` (enabled, hire_per_day,
deposit, buy, return_radius, inns_hire), `wages` (enabled, harvest_crops, harvest_wage, haul_load, haul_wage,
time_limit_ticks, per_day), `dice` (enabled, target, stakes, anywhere, daily_loss_limit, loaded_bias, detect_base,
cheat_report, cheat_standing).

## 8. Saved state

- Per settlement, `hearth:social/ledger` (`SocialLedger`): notices, weddings and funerals to come, offices and the last
  lord, dressings standing, inn rooms and beds let, dice tallies for today, visiting wares sold, the last day whose
  feasts were settled.
- Per resident, `extra("social")`: `office`, `married_day`, `pledged` (on a lord: players who pledged service).
- Per player, attachment `hearth:social/player` (`PlayerLife`, persistent, kept on death): the inn bed (with the
  respawn point it replaced), the hired mount, the last bath.
- Wage jobs and dice games live only in memory (minutes long; a disconnect ends them).

## 9. Performance

- Data side: one pass over residents per settlement per sim day (matchmaking, offices), O(1) on births and deaths;
  nothing per tick for settlements nobody is near.
- Embodied side: every `pass_ticks` (20) for each embodied settlement — tracked by `EMBODIMENT_CHANGED`, never by
  scanning all settlements — a clock check for the bells, dressings set up once per event (a few hundred block reads in
  loaded chunks) and taken down once, and an attendance check over the level's players.
- The day's agenda and venues are cached per settlement and day; behaviours' `wants()` are cheap lookups.
- Bell strokes are a small queue; wage jobs and inn checkouts touch only players who have them.

## 10. Tests

`src/test/java/dev/voidmc/hearth/social/`: `FarkleTest` (scores, busts, scoring masks), `DiceGameTest` (exact bust
odds, the AI only ever keeps valid sets and banks a winning turn, careful folk bank smaller turns than reckless ones,
games end with the winner at the target, hot dice, invalid picks refused, loaded dice roll more 1s and 5s and are caught),
`TownCalendarTest` (one market day a week per town, different weekdays, feast days, night festivals inside their day,
town ticks, the agenda and who attends), `FamilyAndOfficesTest` (matches of an age and never kin, banns then wedding
with the feast's food, funerals with kin, births, offices appointed, posts restored after the sim's job pass, the lord's
heir, small places with few offices), `SocialDataTest` (ledger and config codecs, notices trimmed and translated, bells
only at their hours).

## 11. The daily rhythm (`rhythm/`, docs/LIVING_WORLD_2.md §2, lane A2)

A town keeps a day and a week you can see. Everything is computed from the calendar and the data; nothing new is
stored but a guard for the rest day's offering and the watch lights standing.

**The week.** `TownCalendar.isRestDay/isWashDay`: one rest day a week on the culture's `rest_weekday` (kept off the
market day and the day before it, so the wash day that follows never falls on market day either), towns along a road on
different days. `RhythmCalendar` publishes it as `HearthServices.calendar()` (`restDay`, `washDay`, `marketDay`,
`feast` — a festival's id or `first_snow` — and `events`, the day's agenda keys). `/hearth rhythm day rest|wash|plain`
holds a day in the town here for today (`calendar` gives it back).

**Schedules** (`RhythmSchedule`, a `ScheduleOverrides` source registered as the server starts, after the other modules'
so a named character's own plans win): on the rest day every trade but `rest_day_work` (innkeeper, guards, jailer,
priest, monk, entertainer, stablemaster) has 08:00–17:00 off (walks, visits, the square, prayer for the pious; dinner at
home) — the bakers' bread and the herders' milking are done by eight; tonight's drinkers stay at the inn until closing
and the walk home; in a cold culture's winter, folk keep in by the fire 17:30–20:30. The sim's books are unchanged
(daily rates are already a week's average).

**Folk behaviours** (`NpcService`; all embodied only, `wants()` a few cached lookups):

| behaviour | priority | who, when | does |
|---|---|---|---|
| `ServiceBehaviour` | 85 | rest day: priest (or monk), elders, the pious, the lord, three adults in five (fixed a week), children with their parents | walk to the chapel/temple/church as the bell peals (20 min), sit in the pews (`SEAT`s nearest the altar: the lord and elders in front), stand at the two chants; the priest at the `ALTAR` says a sermon line every 20 s and leads the chant (flute notes); 15 min at the door after, the priest greeting regular players. No church: an open-air blessing at the square |
| `WatchRounds` | 70 | guards and jailers on night duty, the culture's watch hours (21:00–06:00) | round from the gate to the square and past the doors, a torch and **real light** (`WatchLights`), the hours called, a challenge to players abroad or sneaking (2 min a player), "Bell's gone" after curfew |
| `TavernNight.Hands` | 55 | the innkeeper and the town's entertainer, tavern hours | the musician plays the night's tunes (the culture's or the data's instrument); the innkeeper goes round the tables, rings the hand bell at closing |
| `WashBehaviour` | 55 | wash day 08:00–12:00: one adult from a quarter of households (three at least) | to the wash place (river, lake, well, fountain by the culture's liking; `WashPlaces` scans loaded water within 48 blocks of the bounds once per embodiment), kneels (pose tend) with white wool, splashes, gossip |
| `TavernNight` | 45 | tonight's drinkers (`RhythmSchedule.drinkers`: adults drawn each night, the sociable twice as often, as many as the taverns' seats, ≤ 16) | sit at the tables with mugs, drink, toast, cheer when a tune ends, two arm-wrestle one night in three; at closing they weave home |
| `PlayBehaviour` | 25 | children by daylight | the town's game of the hour (hide-and-seek — one counts at the square, the rest hide at plot corners and are found —, hoop and stick, stick fights, snowballs in winter), or their own (skipping stones, chasing chickens, watching the smith); a third left to the routine's tag |

**Town-wide** (`RhythmDirector`, every `pass_ticks` per embodied town): the curfew bell (21:00, five strokes), the
rest-day peal, parents calling children in at supper by name, laundry lines in the washers' yards on wash day (posts,
string, white and grey linen; `Dressings.setUpLaundry`, up till 17:00), winter on the ledges in the cold cultures
(`winter` dressing), chimney smoke at the meal times from homes whose family is in (campfire smoke over the `HEARTH`
column, ≤ 24 a town every 40 ticks) and from the bakery before dawn, players at the service (`social:attended_mass` +1,
once a week a town; the priest counts regulars in his `extra("social").regulars`). Abstract, every settlement once a
day: the rest day's offering, a bit a head of the grown folk who go, to the treasury (`hearth:social/rhythm` guards it).

**Conversation** (`RhythmDialogue`): the innkeeper, in tavern hours, "Buy the house a round" (`round` bits a head of
the folk near, two heads at least; once a night; `social:bought_a_round` +2; the tavern's drinkers raise their mugs to
you); the priest "When is mass?" (today, tomorrow, in N days).

**Data**: `data/<ns>/hearth_social/rhythm/<culture>.json` over `default.json` (`RhythmData`: rest weekday; the service's
name, start, length, peal, share and chant; wash places and hours; curfew; tavern hours and instrument; watch hours;
meal times; rest-day trades; first snow and winter length), barks in `hearth_dialogue/barks/rhythm.json`
(`RhythmBarks`: the dialogue templates' format; one a folk a minute, one a player a town every 4 s, a line not again in
a town for 10 min; scripted lines skip the folk's minute), deeds in `hearth_standing/deeds/rhythm.json`, words in
`lang/en_us/lw_rhythm.json`. Config `config/hearth/rhythm.json` (`RhythmConfig`): rhythm, lights (8 a town, level 14,
8 changes a second), service, tavern, wash, smoke.

**Safety.** Laundry, winter dressings and watch lights go only into air, are written down before they are placed and
are taken down when their hour is over, when the town folds, when the server stops, and (unloaded chunks) on the next
embodiment; the day's agenda pass leaves the `rhythm:` dressings to their own hour. Watch lights are `minecraft:light`
blocks at the watchman's head, moved as he walks, ≤ 8 a town (fewer as the server slows, `LoadCaps`); laundry and winter
dressings are not put up while the server is slow.

**Cost.** Measured (`RhythmTest.abstractCost`, `/hearth rhythm status`, `/hearth sim stats` lines `rhythm` and
`rhythm folk`): see docs/LIVING_WORLD_2.md §2.4 and docs/requests/lwA2.md.

**A stall callback** for lane A4: `Dressings.listen(StallListener)` — `up(level, s, market, stalls)` when a market's
stalls are set up, `down(level, eventKey, positions)` with the packed positions of every dressing block taken down.

**Tests**: `TownCalendarTest` (rest and wash day per culture for every town, never on market day; the calendar service;
winter only in the cold cultures), `ScheduleTest` (no work on the rest day but the listed trades', unchanged otherwise;
the night watch's hours; drinkers until closing), `RhythmDataTest` (every culture has a file or the default, every file
reads, every bark category the code says has lines for every culture, placeholders known, no repeats inside the
cooldown), `RhythmTest` (the congregation, the parts of the service, washers, drinkers, the offering once a rest day,
held days, the abstract cost). Tour: `JAVA_TOOL_OPTIONS="-Dhearth.tour=rhythm" ./gradlew runClientGameTest` (`gametest/RhythmTour`).
