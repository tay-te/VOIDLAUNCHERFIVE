# The law — ownership, crime, justice and stealth

Kingdom Come: Deliverance is the reference (docs/LIVING_WORLD.md §4): everything in a settlement belongs to someone,
a crime counts only if someone sees it (or it is found out later), witnesses run to the guard, guards confront you,
and you pay, go to the cell or the pillory, talk, bribe, run or fight. Bounties follow you to the neighbouring towns;
an unpaid murder makes you an outlaw attacked on sight. Stealth is a playstyle: sneak in the dark, pick locks, pick
pockets, sell to a fence. Code: `dev.voidmc.hearth.law` (server), `dev.voidmc.hearth.client.law` (screens, HUD).

```
law/
  LawModule        init: config, components, items, attachments, packets, hooks, behaviours, dialogue, commands, ticker
  Law              config in force, the settlement data key (hearth:law/state), calendar, exemptions, item value
  LawConfig        config/hearth/law.json (mode full|gentle|off and every switch)
  LawServiceImpl   the api's LawService; posters()
  own/             OwnerRules (pure: plot -> owner), PlotIndex (block -> plot, cached per settlement), Ownership
                   (blocks, animals with a saved owner, names), LookHints (the red hand)
  crime/           CrimeDesk (commit -> witnesses -> pending report -> file), Witnesses, Presence (players in towns),
                   CrimeHooks (Fabric events), TheftWatch (containers), TrespassWatch, LawDays (the law's day),
                   LawStatusSender
  stealth/         Detection (pure sight/hearing model), Visibility (a player's stance), StealthTracker (the eye)
  justice/         Warrant, Offences (pure fines and sentences), SpeechCheck (pure odds), Justice (pay, submit,
                   persuade, bribe, resist, search), Confrontations, Jail (cell and pillory), PlayerLaw (attachment)
  folk/            GuardBehaviour, WitnessBehaviour (FolkBehaviours for the npc module), FolkTraits
  lock/            Locks (lock state from the plot and the hour), LockpickModel (pure, shared with the client),
                   LockpickSessions
  pocket/          Pockets (what a resident carries), PickpocketModel (pure), PickpocketSessions
  item/            StolenMark, OwnedMark, KeyData (components), LawItems (lockpick, key), StolenGoods
  talk/            LawDialogue (guards, witnesses, fences in conversation)
  net/             LawPayloads
  command/         LawCommands (/hearth law ...)
  dev/             LawDevHarness (-Dhearth.dev.law=true)
client/law/        LawClient, LawHud, LockpickScreen, PickpocketScreen, ConfrontScreen, ContainerOwnerOverlay, LawGui
client/mixin/law/  ContainerScreenAccessor
```

## 1. Ownership

`OwnerRules.owner(settlement, plot)`: a plot a player bought is theirs (`PLAYER`); homes (and keeps, underground
dwellings) belong to the head of the household — the eldest adult whose `home` is the plot (`HOUSEHOLD`); workshops,
farms, shops and extraction sites to their eldest worker (`WORKPLACE`), or the settlement if nobody works there;
halls, stores, walls, the jail and landmarks to the settlement (`SETTLEMENT`); roads, squares, gardens and other
infrastructure and landscape are public (no owner). Planned plots and abandoned ruins nobody lives in belong to nobody.
The archetype's category comes from the build module (`ArchetypeInfo`), or from its id (`house/` → housing...).

`PlotIndex` buckets each settlement's plots by chunk and answers "which plot is this block in" from the footprint, six
blocks under the floor to the plot's height; overlaps go to the most specific plot (private before public, then the
smaller). It rebuilds when the plots' number, states or owners change, at most every 30 s otherwise.

Animals belong to the owner of the pasture, barn, stable or yard they are first found in and keep that owner (the entity
attachment `hearth:law/owner`) when led away.

**Shown.** Looking at a container, door, bed, crop or penned animal in a settlement shows KCD's red hand and "Owned by
Edda Miller" beside the crosshair, with the lock's tier, whether it is locked, and whether you carry the key ("Yours" on
your own property). Opening someone's chest shows the owner above the panel, and the red-hand line on every item in it.

## 2. Crimes and witnesses

| Crime | Detected when | Noise |
|---|---|---|
| theft | items leave an owned container (`TheftWatch`), crops are broken or berries picked in an owned plot, a container is smashed (its contents), an owned animal is killed or put on a lead | 0.05–0.6 |
| poaching | killing a wild animal within the territory (`Settlement.territoryRadius`) of a town with a keep, castle, palace or hunting lodge (`NOBLE`): the lord's game | 0.4 |
| pickpocketing | caught in the minigame, seen by a bystander, or missed later | 0 |
| lockpicking | seen at the lock, or the picked lock is found later | 0.1–0.15 |
| trespass | inside a home at night, a shop or workshop after hours, or the jail; warned first, a crime if still there 6 s later and seen | 0 |
| vandalism | breaking a block of a built plot's blueprint (`Blueprint.forEachInChunk` over the built stages); a smashed locked door also leaves a broken lock | 0.5–1 |
| assault | a player hurts a folk (once per 10 s per victim; hitting a guard who is arresting you is resisting arrest) | 0.9 |
| murder | a player kills a folk (the npc module removes the resident) | 1 |
| jailbreak | walking out of the cell | 0.4 |
| robbery and anything else | another module calls `LawService.report(crime)` | — |

**Witnesses** (`Witnesses.find`): the embodied folk near the player (`NpcService.folkNear`), awake, whose eyes reach
the player (`FolkHandle.canSee`: range, field of view, line of sight), weighed by `Detection`:

- sight range = 24 blocks × light factor × stance × the folk's sharpness. Light: a pitch-dark spot leaves 30 % of the
  range (`light_weight` 1), full light all of it — so the lit interiors of §1 are dangerous rooms to rob. Stance:
  sneaking 55 % unarmoured, up to 85 % in plate; running 115 %; invisibility 15 %. Sharpness: guards ×1.25, curious
  ×1.15, cautious ×1.1, lazy ×0.9, dreamy ×0.8, children ×0.9 (traits from `Resident.extra("npc").traits`).
- hearing: footsteps and armour (running loud, sneaking near silent, chain and plate clank when moving) plus the deed's
  own noise, over `hearing_range` (12), halved through walls; loud deeds (≥ 0.8: a fight, a smashed door) heard
  clearly count as witnessed even unseen. Sleepers hear at 40 %.

A crime with a guard among its witnesses is filed at once. Otherwise up to three witnesses set off to report it
(`PendingReport`, saved with the settlement): `WitnessBehaviour` (priority 250) — the brave walk up and demand an
account first (you may hush or threaten them, odds shown), the cautious, shy and children run, the rest cry out and go —
to the nearest guard (`npc.folkNear`, `guard_radius` 64) or guard post or cell; the report is made on arrival, or when
`report_seconds` (90) pass if they never find one (also for witnesses who were folded away). A witness bribed,
threatened or killed on the way never arrives. Crimes by the same hand against the same victim within 10 s are one
crime (a looted chest). Nobody saw it → `Unsolved`: each day it may be discovered (murder and jailbreak always,
vandalism 90 %, theft 60 %, pickpocketing 50 %, a picked lock 40 %, trespass never) and casts suspicion (severity × 8,
up to 80) on any player seen in town that day.

When a report reaches the law (`CrimeDesk.file`): the fine is added to the player's warrant, a standing deed goes to
`StandingService.deed` (`law:theft`, weight −5 … −100; the settlement's raw reputation when the standing module is
absent), `SettlementEvents.CRIME_COMMITTED` fires once, and the player is told "you are wanted for theft".

## 3. Stolen goods

Taken items carry `hearth:stolen` (owner, owner's and town's names, the day, **the thief**): "Stolen from Edda Miller of
Ashbrook" in red. Coin is not marked. Marks fade after `stolen_fade_days` (7). Guards hold only the thief to stolen
goods, so nobody can be framed by planted loot. Put stolen goods back in the container they came from and the mark
comes off. A **fence** (a `FENCE` resident, or the innkeeper in one town in three of village size and up) buys them at
35 % of their worth from a daily purse (120 + 80 × tier bits).

## 4. Guards and justice

A **warrant** per player per settlement (`LawState`): bounty, offences, murder, outlaw, one try each at persuasion and
bribery per crime. `LawService.wanted/bounty/outlaw` read it.

`GuardBehaviour` (priority 320, GUARD and JAILER residents): a guard who sees a wanted player within 28 blocks walks up
and **confronts** them (`ConfrontScreen`: the guard's figure, the charge and bounty, choices with odds):

| Choice | Effect |
|---|---|
| Pay the fine | coin to the treasury (`EconomyService.charge`, `Settlement.deposit`); this town's stolen goods handed back |
| Come quietly | stolen goods confiscated (all, by default); the cell for `bounty / jail_bits_per_minute` minutes (2–30; murder 30) |
| Ask for the pillory | lesser crimes (bounty ≤ 60, nothing violent): 3 minutes in the pillory, jeered at, −8 standing |
| Talk my way out | odds from standing, the guard's opinion, charisma, the crime's severity: small matters let go, others halved; failing adds 10 % |
| Slip them coin | 60 % of the bounty; greedy guards take it, honest ones add 25 % and keep nothing |
| Run / Draw your weapon | resisting arrest (fine and −15 standing); a murderer who does is an outlaw; the guards give chase |

Guards chase and strike (`doHurtTarget`) until the player is down to `yield_health` (6), then take them in; a player who
gets 48 blocks away or out of sight for 12 s has escaped (the warrant stays). An **outlaw** is attacked on sight. At most
two guards go after one player. Suspected players (suspicion ≥ 40) are **stopped and searched** once a day: allow it
(stolen goods found → theft filed, the talk becomes an arrest; clean → suspicion −40), refuse (they insist at 80+, then
refusing is resisting), persuade, or bribe. The confrontation screen cannot be dismissed with Esc: running is an answer.
In conversation (any time) a guard takes your fine or your surrender, and a witness takes hush money or a threat.

**The cell** (`PoiType.CELL`): on a server the sentence is real time, counted while online; walking more than 3 blocks
from the cell is a jailbreak (the unserved part of the bounty is owed again, plus the jailbreak if seen; the jail's doors
are always locked at tier 4 and the guardhouse's at tier 3, so breaking out means picking them). In single player (one player, `sleep_through_jail`) the
sentence is slept through: an hour per minute, the clock jumps, the sim catches up. **The pillory** holds the player in
place. No cell: a public reprimand (standing) settles it. Sentences survive death and relog (`hearth:law/player`).

**Bounties follow you.** Each day (`LawDays`, on `DAY_PASSED`, also during catch-up), a warrant `spread_days` (2) old is
taken up by every settlement within `spread_radius` (1500) at `spread_fraction` (½) of the bounty; outlawry goes with
it. An unpaid murder makes an outlaw after `outlaw_after_days` (1). Suspicion fades 10 a day; picked locks are locked
again by their owners after half a day; forced locks are mended within days.

## 5. Stealth

**Sneaking.** While crouched in a settlement the HUD shows an eye above the hotbar: shut when nobody can see you,
opening and a bar filling as the folk round you could (the same `Detection` the witnesses use), flashing red when a
witness saw a crime.

**Locks** (`Locks`, no block state, nothing saved but exceptions): doors and chests and barrels in private plots.
Homes lock their doors from `night_start` (21:00) to `night_end` (06:00); workshops, shops, stores and civic buildings
from `shop_close` (19:00) to `shop_open` (07:00); the jail and the guardhouse always; inns, taverns, hostels, chapels, temples, shrines and
bathhouses never. Chests: 55 % of home chests, half of workshop chests, all chests in shops, banks (tier 5), keeps and
the jail. Tiers: hovel door 1, home 1–2, workshop 2, shop 2–3, keep 3–4, bank and jail 4, strongbox (`STRONGBOX` POI) 5.
A **key** (`hearth:key`, component `hearth:key`: settlement and plot) opens every lock of its plot; the head of the
household (or of a workshop nobody lives in) carries it, so it can be pickpocketed. Without the key: "Locked (Hard)" and
the chest's locked rattle; with a lockpick in hand, the minigame.

**Lockpicking** (`LockpickScreen`, `LockpickModel`): an iron lock face, a brass cylinder, a pick. Move the mouse to work
the pick round the top of the keyhole; hold left mouse, D or space to turn the cylinder. In the sweet spot it turns all
the way and the pin sets with a click; off it, it turns part way and binds — the pick shakes, reddens and strains, and
snaps if forced. Releasing drops set pins one by one. Tier 1–2 one pin, 3 two, 4–5 three; sweet spots 14° → 3°; picks
weaker on better locks; skill (practice, 0–20) widens the spot and strengthens the pick. Lockpicks: `hearth:lockpick`,
four from two iron nuggets and a stick. The server takes broken picks, watches for witnesses every second (seen: a
crime, the attempt ends), checks the success took at least `lockpick_min_seconds` per pin, then marks the lock picked
and opens the door or the chest.

**Pickpocketing** (`PickpocketScreen`, `PickpocketModel`): crouch behind a folk (out of their field of view) or beside a
sleeper and use them with an empty hand. Their pockets open: a purse by station (a hovel's few bits to a lord's
hundreds; paid through the economy module), the house key if they keep it, the small things of their trade, perhaps
food. Click to lift (heavier things take longer), click again to let go, Esc to slip away. The mark's awareness fills
all the while — 0.14/s awake, 0.05/s asleep, ×2.2 while lifting, more in bright light and for sharp folk or a thief in
armour, less with practice — and when full you are caught (the victim is the witness). The server runs the clock.
Pockets refill every `pocket_refresh_days` (3); what was lifted stays gone until then.

## 6. Servers

Creative and spectator players are exempt (`exempt_creative`), game masters optionally (`exempt_ops`, off so single
player worlds with cheats still have law). PvP inside a settlement's bounds is blocked unless `pvp_in_towns`. Players
cannot report or frame each other: crimes are only ever detected against the player who did them, player-on-player
harm is not a crime, and stolen goods count only against their thief. `protect_player_property` makes theft from
another player's deeded house a crime when folk see it.

## 7. Config: `config/hearth/law.json`

Every field is written out with its value; delete one to get its default back. `mode`: `full` (all of it), `gentle`
(no jail, pillory, outlaws or violent guards, no trespass, fines halved, bounties lapse after 7 days), `off` (no crimes;
ownership, locks and marks still show).

```json
{
  "mode": "full", "exempt_creative": true, "exempt_ops": false, "pvp_in_towns": false, "protect_player_property": true,
  "crimes": {"theft": true, "pickpocketing": true, "trespass": true, "vandalism": true, "assault": true, "murder": true,
             "animals": true, "crops": true, "lockpicking": true},
  "witnesses": {"sight_range": 24, "hearing_range": 12, "report_seconds": 90, "guard_radius": 64, "light_weight": 1.0,
                "bribes": true, "threats": true},
  "fines": {"theft": 20, "value_multiplier": 2.0, "pickpocketing": 30, "lockpicking": 25, "trespass": 15, "poaching": 30,
            "vandalism_per_block": 3, "assault": 60, "murder": 1500, "robbery": 300, "jailbreak": 200,
            "resisting_arrest": 80, "multiplier": 1.0},
  "justice": {"jail": true, "jail_bits_per_minute": 10, "jail_min_minutes": 2, "jail_max_minutes": 30,
              "sleep_through_jail": true, "pillory": true, "pillory_max_bounty": 60, "pillory_minutes": 3,
              "confiscate_all_stolen": true, "guards_attack": true, "outlaws": true, "outlaw_after_days": 1,
              "bounty_expiry_days": 0, "spread_days": 2, "spread_radius": 1500, "spread_fraction": 0.5,
              "suspicion_search_threshold": 40, "yield_health": 6.0},
  "stealth": {"lock_doors": true, "lock_containers": true, "night_start": 1260, "night_end": 360, "shop_close": 1140,
              "shop_open": 420, "stolen_fade_days": 7, "pickpocket_awake": 0.14, "pickpocket_asleep": 0.05,
              "pocket_refresh_days": 3, "lockpick_min_seconds": 0.6}
}
```

`/hearth law reload` reads it again.

## 8. Commands

```
/hearth law status [player]                 warrants, suspicion and sentence everywhere; thief's skills
/hearth law wanted                          the wanted posters of the town you stand in
/hearth law pardon <player> [everywhere]    (gm) clear warrant and suspicion here / everywhere
/hearth law bounty <player> <bits>          (gm) set what they owe here
/hearth law crime <offence> [value]         (gm) file a crime against yourself here (to try the guards)
/hearth law jail|pillory <player> <min>     (gm) sentence them here
/hearth law release <player>                (gm)
/hearth law lock                            (gm) owner, plot, lock tier and state of the block you look at
/hearth law key                             (gm) the key to the plot you stand in
/hearth law picks                           (gm) 16 lockpicks
/hearth law reload                          (gm) re-read config/hearth/law.json
```

## 9. Saved state and network

- `hearth:law/state` (settlement data): warrants, suspicion, the day each player was last seen, pending reports,
  unsolved crimes, lock overrides. Small: settled warrants are pruned.
- `hearth:law/player` (player attachment, kept through death): sentence, lockpicking and pickpocketing practice.
- `hearth:law/owner` (entity attachment on animals).
- Components `hearth:stolen`, `hearth:owned` (transient: only while an owned container is open), `hearth:key`.
- Packets `hearth:law/*`: `hint`, `status`, `stealth`, `container_owner`, `lockpick_start|end`, `pocket`,
  `confront` (down); `lockpick_event`, `pocket_action`, `confront_choice` (up). The server decides everything except
  the feel of the lockpicking minigame, whose result it checks.

## 10. Performance

- Nothing runs for settlements nobody is in. `Presence` (every 10 ticks) finds the players in towns; the eye (every 5
  ticks), ownership hints (every 4), trespass, owned-mark sweeps and report deadlines (every second) run only for them.
- Crimes are event-driven; witness queries ask the npc module for the folk near one player and raycast only those the
  light and distance leave a chance.
- Behaviours: `wants()` returns at once for anyone but guards and pending witnesses; guards look only at the players in
  their town who have warrants or suspicion.
- `DAY_PASSED` work is a few comparisons per warrant, report and unsolved crime of that settlement (spreading scans the
  settlement list once per warrant per spread).
- Measured (dedicated server, `found=3 generate=true law=true`, seed 20261003, this busy shared machine, 2026-10-03):
  ownership lookups 0.9–2.0 µs each over 20 000 random positions per town (index included); a day's law for a town
  with a warrant to spread 0.5 ms; Plumvale: 39 lockable blocks, 10 door halves and 12 chests with locks (tiers 1–2,
  14 locked at 06:18), owners 4 households, 2 workshops, 2 town; word of a 100-bit theft in Torheim reached Thornfield
  (933 blocks) as a 50-bit warrant.

## 11. Tests and the dev harness

`src/test/java/dev/voidmc/hearth/law/`: the detection model (light, sneaking, armour, hearing, sharp eyes), the
lockpick model (determinism, pins and tolerance by tier, opening in the sweet spot, snapping when forced, dropping
pins), the pickpocket clock and pockets, fines, sentences, the pillory rule, warrants, speech odds, skills, escapes,
ownership rules and the plot index on `SimpleSettlement`, lock tiers, crime merging, discovery, stolen-mark fading,
warrants spreading to neighbours, the config (round trip, partial files, gentle and off modes) and the saved state.

`-Dhearth.dev.law=true` on the dedicated server (with the sim's `found`/`generate`) logs `[hearth law]` lines: owners
per town, ownership lookup time, every door and chest with a lock in loaded chunks, cells and pillories, and a crime
filed, spread to neighbours by a day's passing, and pardoned.

Client tour: copy a world with live settlements into `run/saves/<name>` (e.g. the dev server's `run/world`), then
`JAVA_TOOL_OPTIONS="-Dhearth.dev.law.tour=true" ./gradlew runClient --args="--quickPlaySingleplayer <name>"` (set
`onboardAccessibility:false` in `run/options.txt`). It goes to the nearest town, finds a locked door or chest, shows the
real red-hand hint and kneels at it with lockpicks (the real lockpicking screen, then forced into strain), shows the
pickpocketing and confrontation screens, the sneaking eye, the wanted and jail lines and a container's owner with
made-up data (those need embodied folk), and writes `run/screenshots/hearth_law_*.png`.
