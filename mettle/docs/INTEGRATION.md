# VOID Mettle — working with Hearth, Expanse, LOD and pvp

Owned by the **compat** module. Every partner is optional: compiled against its api (for Hearth, a copy of it in
`src/hearthApi`, §1.2), at runtime only in the dev runs,
`"suggests"` in `fabric.mod.json`, and every partner type confined to `compat.<partner>` behind
`FabricLoader.getInstance().isModLoaded(...)`. This is the same pattern as `lod/.../ExpanseSource`.

---

## 1. Hearth

**The split, agreed with the user on 2026-10-03:** Mettle supplies the numbers (skills, perks, attributes, checks).
Hearth owns how the world reacts (prices, standing, opinion, law, the lockpicking and pickpocketing minigames,
witnesses, crime). Neither builds the other's half.

### 1.1 What Mettle does with Hearth

| Feature | How | Needs from Hearth |
|---|---|---|
| **Trainers** | Folk offer "Train <skill> · N bits" in conversation, by job. One session a sim day per trainer: grants XP worth about 40 % of the next level, up to the trainer's cap. Costs `EconomyService.charge`. | Request 5 (order band) |
| **Trainer cap by settlement tier** | camp 4 · hamlet 6 · village 10 · town 15 · city 20. A city's master is worth the trip. | — (reads the settlement's tier) |
| **Skill books** | Hearth scholars sell, and libraries and Expanse hidden chests hold, books stamped `mettle:skill_book {skill, xp, tier}`. Reading takes 5 s of holding use; once per book per player (journal). Tier 2+ books need Scholarship 5/10. | — (loot table events; merchant stock request optional) |
| **Quest XP** | On quest completion: bounty → Blades/Heavy/Marksmanship (whichever the player used most on it) 120 · courier, escort → Athletics/Riding 100 · supply → Mining or Survival by goods 80 · exploration, scouting → Survival 150 · gathering → Survival 60 · personal → Speech 80. Plus character XP. | Request 3 |
| **Speech and prices** | Speech perks and Presence move buy/sell prices; Merchant's Coin Sense adds 5 %. Speech XP per crown of business. | Requests 1 and 6 |
| **Speech checks** | Persuade / Intimidate / Flatter options with odds shown ("Persuade · 65 %"), resolved by `CharacterService.check`. | Request 2 |
| **Charisma and menace** | Hearth's `StandingService.charisma` / `menace` read Presence, Speech level, perks and background through the player-skills service. | Request 4 |
| **Thievery and Stealth vs folk** | Hearth's law module runs the minigames and witnesses and reads Thievery and Stealth through the player-skills service. It reports successes so Mettle grants XP. | Request 4, plus Hearth phase 3 |
| **Coin weight** | 0.01 kg per coin, loose or in a pouch (512 coins = 5.1 kg); Merchant halves it; the bank is weightless. This is the reason to change bits up into crowns and to bank. | — (reads `hearth:pouch_contents`) |
| **Carrying relief** | Hearth tanners and tailors sell Frame Packs and Hunter's Quivers. Later: hired pack mules (stables) and inn rooms as stashes. | Hearth phase 3 |
| **Backgrounds in conversation** | Optional greeting lines by background ("A soldier? The captain's always hiring"). | Hearth dialogue data condition `background` (later) |
| **Deck rebuilds** (cards v2) | A trainer also offers "Rebuild your Mining deck · fee": unslot cards needing up to the trainer's cap, with a Draught of Forgetting; the fee goes to the trainer; a city master scars half as often (CARDS.md §7.3). | — (training band, `charge`) |
| **Coin in trades** (cards v2) | The face-to-face trade screen carries coin beside cards and items (CARDS.md §8.1). | Request 8 (or `charge` + `pay` meanwhile) |
| **Card broker** (cards v2) | Players list cards for coin at a broker in towns and cities; Hearth runs the board, fees, escrow and payouts (CARDS.md §8.2). | Request 7 |
| **Mythics in the world** (cards v2) | The Open Hand goes to the first player Honoured in a Hearth city; folk may greet legends; The Open Hand shares the market edge (CARDS.md §9). | Requests 9 and 10 |

### 1.2 Built (2026-10-07)

Code: `dev.voidmc.mettle.compat.hearth`, entered only from `compat.CompatModule` when `FabricLoader.isModLoaded("hearth")`.
Every number is in `HearthRules` (pure, JUnit `HearthRulesTest`) and on the balance sheet (BALANCE.md §9).

**Compiling without Hearth.** `src/hearthApi/java` is a copy of the Hearth api types Mettle calls, from
`git show hearth:hearth/src/main/java/dev/voidmc/hearth/api/<X>.java`: PlayerSkills, PriceModifier, DialogueProvider,
QuestEvents, QuestType, MarketEvents, SettlementTier, JobType, AgeStage verbatim; HearthServices, EconomyService,
HearthWorld, Settlement and Resident trimmed to the members Mettle uses, with Hearth's signatures (bodies throw). It is
its own source set, on the compile classpath of main, test and gametest only, and the jar task excludes
`dev/voidmc/hearth/**` as well. When Hearth's api changes, copy the files again; `HearthRulesTest` checks Mettle's
tables against the copied enums and odds rule.

| Piece | Class | What it does |
|---|---|---|
| **Player skills** | `MettlePlayerSkills`, set with `HearthServices.setPlayerSkills` at init | `level` (Mettle skill by id; a bare `speech` means `mettle:speech`; unknown → 0), `attribute` (incl. background; unknown → 3), `hasPerk` (held perks and the background's signature perk, so `mettle:coin_sense` answers), `check` (odds by CHARACTER.md §7, the same rule as `PlayerSkills.odds`; rolled with the player's random; no XP, Hearth awards it), `award` → `Progress.award` (favoured, Wits and `MODIFY_XP` apply). Speech, Thievery and Stealth are registered (unreleased) skills, so their XP banks for when the trees ship; `Progress.award` has no released check. |
| **Prices** | `MettlePrices`, added with `EconomyService.addPriceModifier` on the first `SERVER_STARTING` (Hearth's economy registers in Hearth's init, which may run after Mettle's) | Presence and Speech move the price a little each way, Coin Sense adds 5 %: buy × (1 − edge), sell × (1 + edge). Hearth clamps the product and keeps its spread. |
| **Quest XP** | `HearthXp`, on `api.QuestEvents.COMPLETED` | Skill XP by quest type and template (table below); character XP comes with it (character XP is all skill XP). `FAILED` and `ABANDONED` give nothing. "Whichever weapon the player used most on it" = the combat skill (Blades, Heavy Weapons, Marksmanship) that earned the most XP since the player's last fighting quest ended; with none, their best of the three. |
| **Business** | `HearthXp`, on `api.MarketEvents.TRADED` | Speech XP per crown that changes hands, a fifth of it past 20 crowns a sim day (in memory, per player). |
| **Trainers** | `Trainers` (a `DialogueProvider`) | Lessons and a city master's Teaching, below. |

**Quest XP** (`HearthRules.questXp`; base XP, before favoured and Wits):

| Hearth quest type | Skill | XP |
|---|---|---|
| bounty, caravan, law | the combat skill used most | 120 |
| courier, escort | Athletics (Riding when its tree ships) | 100 |
| supply, contract | by the template's goods: Mining (stone, ore, clay, sand, salt, glass, fuel, kilns, smelter), Smithing (tools, metal, smithy, guild orders), else Survival | 80 |
| exploration, scouting | Survival | 150 |
| gathering | Survival | 60 |
| personal | Speech | 80 |
| disaster | wolves → combat; mine rescue, surveys, blast repairs → Mining; else Survival | 100 |
| official | guard and caravan work → combat; builder work → by goods; else (reeve, priest, lord) Speech | 80 |
| crime | Stealth | 100 |

**Trainers.** Folk offer lessons by job, in Hearth's training band (300–399; a resident's first skill at 300, the next at
310, a Teaching one above its lesson). Children never teach. Nothing is offered when Hearth's economy module is absent.

| Job | Trains | City master's Teaching |
|---|---|---|
| miner | Mining | Stonesense |
| quarrier | Mining | — |
| smith | Smithing | Folded Steel |
| guard | Blades, Defense | Master Strike, Roll with the Blow (TREES.md's guard captain) |
| forester | Heavy Weapons | Overhead Blow |
| hunter | Marksmanship, Survival | Steppe Draw |
| courier, hauler | Athletics | Courier's Stride (courier) |
| healer | Survival | Poultice |
| merchant, trader | Speech | — (no tree yet) |
| fence | Thievery, Stealth | — (no trees yet) |

- **A lesson**: "Train Blades · 40 bits", hint "+N XP" (after the player's multipliers). Worth 40 % of the XP from the
  player's level to the next (so a lesson never makes a level on its own), through `Progress.award`. Once a sim day
  per trainer (`HearthWorld.day()`; the overworld day without one), remembered in the player attachment
  `mettle:compat/trainer_sessions` (persistent, unsynced, kept on death, holds only today's lessons). Paid with
  `EconomyService.charge(player, settlement, trainer, fee)` into the trainer's till or purse.
- **Cap by tier**: camp 4 · hamlet 6 · village 10 · town 15 · city 20. Past it the option stays, locked: "Beyond them",
  tooltip "Your Smithing is past what Hans can teach. Folk here teach up to level 4; a bigger town's master could take
  you further." At skill 20 the option goes away.
- **Locked** with a reason: "Tomorrow" (taught today), "Needs 40 bits" (tooltip: the fee and what the player carries).
- **A city master's Teaching**: in a city, the job TREES.md names for a skill (miner, guard, forester, hunter, courier,
  healer, smith) also offers "Learn Stonesense · 10 crowns" (1,000 bits) once the player has the skill at 6 ("Needs
  Mining 6" until then); the tooltip is the perk's own sentence. Granted with `Perks.grant` (the same Teaching the tome
  gives; a held one is not offered). The Teaching perk is found by kind in the perk registry, so a skill gains a master
  as soon as its data has a Teaching.
- Everything is checked again when picked (level, cap, day, coin); a failed payment answers "Come back when you have
  the coin." and a lesson answers with one of three trainer lines (`DialogueProvider.reply`).

**Running with Hearth.** `./gradlew syncHearth` builds Hearth from a `git archive` snapshot of branch `hearth` (Hearth
and Expanse, under `build/hearth-snapshot`; no Hearth worktree is touched) and stages `build/hearth/void-hearth.jar`;
`-Phearth` then puts it on the dev runs' classpath (`localRuntime`), and the client tour adds its Hearth journey
(`gametest/HearthJourney`).

**Not built yet** (rows of §1.1): skill books, Mettle's own speech-check options (Hearth's speech checks already read
Speech and Presence through the slot), carrying relief, background greeting lines. Coin weight was built earlier in the
burden module (`Weights`, `WeightPerks`). Wave-2 skills no Hearth system reads yet (Alchemy, Scholarship, Husbandry,
Riding) get trainers when their trees ship.

### 1.3 Requests to Hearth

All six done by Hearth on 2026-10-07 (`hearth/docs/requests/mettle.md`, Hearth ARCHITECTURE.md §11 "Partners"):

1. **Player price hook**: a `PriceModifier` in the api, applied in `PriceModel.buy/sell` (via `MarketPricer.quote`) with the `ServerPlayer`. Today only reputation enters, and `EconomyServiceImpl.price` passes no player.
2. **Dialogue option state**:
   - an optional `locked`, `hint` (e.g. "Persuade · 65 %") and `tooltip` on `DialogueProvider.Option`, carried on the wire
   - `select` able to return an NPC reply `Component`
   
   Today the wire option is `(id, label)` only, and a non-npc provider can't set the reply.
3. **Quest events in the api**: move `QuestEvents.COMPLETED` (and add `FAILED` / `ABANDONED`) into `dev.voidmc.hearth.api`, with the quest type and the settlement's tier.
4. **A player-skills service slot**:
   - `HearthServices.setPlayerSkills(PlayerSkills)`, with `level(player, skill)`, `attribute(player, name)`, `hasPerk(player, id)` and `check(player, skill, difficulty)`, using string or Identifier keys so Hearth needs no Mettle types
   - Hearth's standing and law modules read it, and it falls back to neutral values when absent
   - Mettle registers the implementation
5. **Dialogue order band 300–399 reserved for training** (0–99 small talk, 100–199 trade, 200–299 quests today).
6. **A market trade event**: `(player, settlement, item, count, bits, playerBuys)`, fired by `Market.trade`, for Speech XP and stats.

### 1.4 Requests for cards v2

Written 2026-10-10 in [hearth/docs/requests/mettle-cards.md](../../hearth/docs/requests/mettle-cards.md) (a new file, so
it never collides with Hearth's answers in `mettle.md`): 7 virtual goods and the card broker, 8 a player-to-player coin
transfer, 9 a standing event for "Honoured", 10 Mythic titles readable by dialogue, 11 (FYI) the trainer's rebuild
option in the training band. Mettle's side calls them through `compat.hearth`, like the first six.

## 2. Expanse

| Feature | How |
|---|---|
| **Discovery XP** | Every 40 ticks, for each player standing in a structure piece of an `expanse:*` structure (`structureManager().getStructureWithPieceAt`), the first visit per structure instance grants Survival XP (40, or 150 for landmarks), recorded in the journal. Expanse has no landmark tag today → **request to Expanse:** add `#expanse:landmarks` listing the 8 landmark structures. Until then compat keeps the list. |
| **Lore books** | Lore books and journals from Expanse's structure loot count as Scholarship books on first read (detect by their loot origin component, or by title list). |
| **Wildlife** | Elk and deer flee unless you sneak (Expanse behaviour). Survival's Quiet Approach widens that; hunting them is Survival XP; Ranger's Field Dressing adds venison and leather. |
| **Herbs** | Lavender, fireweed, edelweiss, reindeer lichen, heather and others become Alchemy ingredients in wave 2 (Alchemy's tree). Forager covers them in wave 1. |
| **Hidden chests** | A "Keen Eye" cue (wave 2, Scholarship or Survival): a soft sound near a hidden mechanism. **No glow or particles on the surface**, which keeps Expanse's natural look. |
| **Spawner mobs in structures** | Fall under anti-farm (×0.1), so crypts are for loot, not grinding. |
| **Teaching tomes** | Built (`character.Tomes`). Each skill's Teaching has a tome in one landmark's hidden chest, 30 % per chest, added by `LootTableEvents.MODIFY` with no compile-time dependency. Without Expanse, a vanilla chest holds it at 8 %. Reading takes 5 s of holding use. The tome is kept, and each reader learns it once. See the table below. |
| **Climate** | Not used. Expanse has no climate system, and Mettle doesn't add cold or heat survival. |

Where the tomes lie (`Tomes.SOURCES`; TREES.md names the landmarks, mapped to the structures Expanse ships):

| Teaching | Tome | Expanse chest | Vanilla stand-in |
|---|---|---|---|
| Stonesense | *The Delver's Almanac* | `deepstone_ruins_hidden` | `ancient_city` |
| Master Strike | *The Verses of the Long Edge* | `wisteria_manor_hidden` | `woodland_mansion` |
| Overhead Blow | *The Feller's Rule* | `logging_camp_hidden` | `pillager_outpost` |
| Steppe Draw | *The Steppe Bow* | `caravanserai_hidden` | `desert_pyramid` |
| Roll with the Blow | *The Citadel Drill* | `frost_outpost_hidden` (TREES' Frostwatch Citadel isn't built) | `igloo_chest` |
| Courier's Stride | *Letters of a King's Runner* | `palm_harbour_hidden` | `shipwreck_map` |
| Poultice | *The Monks' Herbal* | `cloud_monastery_hidden` | `jungle_temple` |
| Folded Steel | *A Treatise on Folded Steel* | `karst_pagoda_hidden` | `bastion_other` |

The other half is built: a city's master of the trade TREES.md names teaches it for 10 crowns (§1.2, Trainers).

Cards v2: Expanse needs nothing new. The Far Horizon (a world-first Mythic) counts blocks from spawn, not landmarks,
so it works without Expanse; Seen It All and Lay of the Land are unchanged.

## 3. LOD

Nothing to integrate. Mettle renders worn gear on players, adds GUI screens and HUD chips, and touches no world
rendering.

## 4. pvp

`pvp/` is a separate 1.8.9 client and never meets this 26.3 mod. On 26.3 servers that want fair PvP, `pvp_perks`
(`full` / `reduced` / `off`) and `pvp_xp_scale` decide how perks and XP apply between players.

## 5. Arsenal

VOID Arsenal (`arsenal/docs/DESIGN.md`, design only) makes weapons from forged parts. **Smithing becomes its forging
skill**, on the Hearth split: Mettle owns the numbers (strike window, steel and Masterwork gates, trait and perk
values), Arsenal owns the items and stations and reports forging XP through `CharacterService.award`. Every Smithing
trait has a vanilla and an Arsenal meaning, Smithing perks gain forging meanings, The First Hammer Mythic marks parts in
gold, and Arsenal's technique cooldowns read the Echo set's scale. All in CARDS.md §10, with the `CardService` api
Arsenal reads. Built in `compat.arsenal` behind `isModLoaded("arsenal")` when Arsenal's wave 1 lands.

## 6. Launcher

The VOID launcher installs mods only from Modrinth, so VOID's own mods (Expanse, LOD, Hearth, Mettle) reach players
as hand-copied jars today. A first-party install path is outside this mod but needed before release.
