# VOID Mettle — working with Hearth, Expanse, LOD and pvp

Owned by the **compat** module. Every partner is optional: `compileOnly` + `localRuntime` through `includeBuild`,
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

### 1.2 Requests to Hearth

Filed as `hearth/docs/requests/mettle.md` for the Hearth coordinator.

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

## 2. Expanse

| Feature | How |
|---|---|
| **Discovery XP** | Every 40 ticks, for each player standing in a structure piece of an `expanse:*` structure (`structureManager().getStructureWithPieceAt`), the first visit per structure instance grants Survival XP (40, or 150 for landmarks), recorded in the journal. Expanse has no landmark tag today → **request to Expanse:** add `#expanse:landmarks` listing the 8 landmark structures. Until then compat keeps the list. |
| **Lore books** | Lore books and journals from Expanse's structure loot count as Scholarship books on first read (detect by their loot origin component, or by title list). |
| **Wildlife** | Elk and deer flee unless you sneak (Expanse behaviour). Survival's Quiet Approach widens that; hunting them is Survival XP; Ranger's Field Dressing adds venison and leather. |
| **Herbs** | Lavender, fireweed, edelweiss, reindeer lichen, heather and others become Alchemy ingredients in wave 2 (Alchemy's tree). Forager covers them in wave 1. |
| **Hidden chests** | A "Keen Eye" cue (wave 2, Scholarship or Survival): a soft sound near a hidden mechanism. **No glow or particles on the surface**, which keeps Expanse's natural look. |
| **Spawner mobs in structures** | Fall under anti-farm (×0.1), so crypts are for loot, not grinding. |
| **Climate** | Not used. Expanse has no climate system, and Mettle doesn't add cold or heat survival. |

## 3. LOD

Nothing to integrate. Mettle renders worn gear on players, adds GUI screens and HUD chips, and touches no world
rendering.

## 4. pvp

`pvp/` is a separate 1.8.9 client and never meets this 26.3 mod. On 26.3 servers that want fair PvP, `pvp_perks`
(`full` / `reduced` / `off`) and `pvp_xp_scale` decide how perks and XP apply between players.

## 5. Launcher

The VOID launcher installs mods only from Modrinth, so VOID's own mods (Expanse, LOD, Hearth, Mettle) reach players
as hand-copied jars today. A first-party install path is outside this mod but needed before release.
