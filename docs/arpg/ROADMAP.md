# Roadmap: build order, gates, decisions, screens

The order the user proposed, made concrete: **1** combat foundation + animation system + a few reworked mobs, one
elite system and one boss; **2** Mettle's Mythic retune against them; **3** Arsenal wave 1 including bows, then runes;
**4** Alembic + Arcana and Alchemy; **5** world content. Each stage ends with a build the user plays.

---

## 1. Principles

- **Design, then Figma, then code.** New screens and HUD elements are designed in the VOID Figma file first (§7) and
  the in-game page 295:2 is kept in sync once built.
- **Mettle owns the numbers.** Every stage's balance is checked against Mettle's reference curves (OVERVIEW §5) by a
  simulator before the playtest.
- **Prove the risky things first.** The animation system and the feel are the biggest unknowns; stage 1 exists to
  answer them on a real dedicated server with real latency and real shaders.
- **Working rules** (from the other VOID lanes): Opus agents at **high** effort, never above; one worktree per lane;
  builds and tests run in the foreground and an agent is done only when everything is committed and verified; never
  `gradlew --stop` or kill another lane's Java; **one Minecraft instance at a time** machine-wide (the shared
  game-test lock; the Motion Studio counts as one); nothing pushed or merged to `main` without the user.

## 2. Stages at a glance

| Stage | Builds | Proves | Lanes | Size |
|---|---|---|---|---|
| **0 · Design** | these docs; Figma for the combat HUD and settings; the motion spike (M0) | the plan; that our animation idea works at all | design, one motion agent | S |
| **1 · Foundation** | Fray (pipeline, statuses, stamina, poise, threat, actions, motion M1–M5), Bestiary's player actions, AI framework, 5 creatures, elites, the Gallows Knight | the feel; server authority; animation end to end; AI; performance; shaders; multiplayer | fray-core, fray-motion, bestiary-actions, bestiary-ai, bestiary-boss | L |
| **2 · Mythic retune** | Mettle's balance provider, stamina and mana numbers, perk remap, ceilings, the fight simulator | that the numbers hold (2x+ vanilla, enemies that still threaten) | mettle-mythic (already starting) | M |
| **3 · Arsenal** | wave 1 parts (incl. bows, crossbows, throwables), wave 2 behaviour (techniques, attachments), wave 3 runes (absorption, conversion, new runes) | crafted weapons in Fray's combat; enchanting replaced without breaking anything | arsenal-parts, arsenal-behaviour, arsenal-runes | L |
| **4 · Alembic** | reagents, the brewing craft, absorbed potions, oils, bombs; foci (Arsenal) and spells; Mettle's Alchemy and Arcana | brewing as a craft; the mage path; reactions | alembic-brew, alembic-spells, arsenal-foci, mettle-arcana | L |
| **5 · World** | the roster by region and depth, all affixes, lair and realm bosses, Hearth quests, Atlas lairs | a world that fights back everywhere | bestiary-content ×2–3 lanes, hearth-requests, expanse-anchors | XL |
| **6 · Later** | Nether and End (Wither and Dragon reworked), raids, PvP polish, controller | | | |

Stage 2 is already starting in parallel (the user's note): it can write its design and simulator against stage 1's
data now, and locks its numbers after stage 1's feel gate.

## 3. Stage details

### Stage 0 · Design (now)

| Milestone | Contents | Done when |
|---|---|---|
| 0.1 | this master design | the user signs off or answers §6 |
| 0.2 | Figma: stamina bar, lock-on and target frame, boss bar, build-up meters, telegraph cues, the Combat settings page (§7, stage 1 rows) | the user picks a direction |
| 0.3 | motion spike M0 (ANIMATION.md §12) | a zombie and the player play a server-started clip on a dedicated server with two clients, with Sodium + Iris + Welkin; a GIF for the user |

### Stage 1 · Combat foundation

| Milestone | Contents | Tests |
|---|---|---|
| 1.1 Fray core | damage pipeline (all stages), ten kinds, resistance attributes, damage profiles for every vanilla type, the cooldown rule, statuses (Bleed, Burn, Chill/Frozen, Shock, Poison, Stagger, Daze, Exposed, Wet, Oiled) and the first reactions, stamina and poise, threat, events, the family handshake, `BalanceProvider.NEUTRAL` | JUnit for every stage's maths and ceilings; game tests: foreign damage resisted, vanilla cooldown kept for lava, hit-once per swing |
| 1.2 Fray Motion | M1 runtime, M2 tools and the Motion Studio, M3 first person, M4 performance and compatibility (ANIMATION.md §12) | sampling and blending JUnit; bake validation; late-join game test |
| 1.3 Player actions | movesets for sword, greatsword, axe, mace, spear, dagger, unarmed, shield, bow; dodge, parry, block, guard break, lock-on, specials (riposte, backstab, plunge, Execute); prediction and lag rules; the HUD from Figma; accessibility options | COMBAT.md §17: parry and dodge tables at 0/100/150/250 ms; first- and third-person strips |
| 1.4 Bestiary AI | the framework (perception, awareness, tactics, tokens, roles, morale), danger map, creature profiles; reworks: **zombie, skeleton, spider, creeper**; new: **revenant soldier**; elites with six affixes (Ironbound, Frenzied, Venomous, Regenerating, Shielded, Swift) and champion packs; codex v0 | BESTIARY.md §14 |
| 1.5 The boss | custom rigs (M5), the encounter framework, the **Gallows Knight** (both phases) at an Expanse lair anchor (fallback: a spawn command and a vanilla structure) | resets, personal loot, multiplayer scaling; a full fight on a dedicated server |

**Exit gates (all):** G-DS, G-MP, G-PERF-S, G-PERF-C, G-NET, G-SHADER, G-COMPAT, G-FEEL (§4).

### Stage 2 · Mettle's Mythic retune

| Milestone | Contents | Tests |
|---|---|---|
| 2.1 | Mettle implements `BalanceProvider` and Fray's attributes through its `EffectsApplier`; stamina and mana numbers; Athletics gains stamina; the perk remap (COMBAT.md §16.2); Arts on sneak + C; the ceilings (CONTRACTS.md §7.6); the reference curves written into Mettle's BALANCE.md | Mettle's tours still pass; ceilings enforced in every state |
| 2.2 | the **fight simulator**: Mettle's reference player per stage × Bestiary's creature profiles × danger 1–5 → time to kill and time to die; every archetype and hybrid | B1, B2 |
| 2.3 | playtest with the stage 1 content | G-FEEL again |

### Stage 3 · Arsenal

| Milestone | Contents | Tests |
|---|---|---|
| 3.1 Wave 1 · parts | `arsenal:build`, forge, anvil timing game, quench, steel and bloomery, workbench (assemble, salvage), composed item models; swords, axes, spears, **bows, crossbows, throwables** (hatchets, javelins, knives); `fray:damage_split` and moveset mapping; Hub workbench page; tooltips | every family hits through Fray; composed models cost ≤ vanilla's in the Hub perf gate |
| 3.2 Wave 2 · behaviour | techniques (C), attachments (incl. the coating attachment), grindstone edges, daggers and maces, fletching table | technique cooldowns read Mettle's Echo |
| 3.3 Wave 3 · runes | the inscribing table (the enchanting table's block), sockets, capacity, instability, libraries, rune learning, **all 43 vanilla enchantments absorbed**, conversion, the first new runes (Rend, Rime, Thunder, Venom, Kindling, Sunder, Evasion, Fortitude, Second Wind), Hearth scholars | ENCHANTING.md §9 (mapping, conversion, loot and trades, odds, removal) |

**Exit gates:** G-DS, G-COMPAT, G-SAVE, B1 (with forged gear), G-FEEL.

### Stage 4 · Alembic, Arcana and Alchemy

| Milestone | Contents | Tests |
|---|---|---|
| 4.1 Brewing | reagents and grades, mortar, the cauldron craft (Hub page + native gauge), bottling stand, still, toxicity, **absorbed potions and recipe import**, oils (`fray:coating`), bombs, salves, inks; Mettle's Alchemy deck | every vanilla potion drinks as its brew; a datapack brewing recipe becomes a formula; toxicity limits |
| 4.2 Foci | Arsenal's staff, wand and tome from parts; spell sockets; focus runes | foci in every hand combination |
| 4.3 Spells | mana, casting actions, Pyromancy and Lumen first (fire for everyone, radiant for stage 1's undead), then Cryomancy, Stormcraft, Hexcraft, Kinesis; all reactions; learning and attuning; Mettle's Arcana skill and deck | reactions game tests; the mage archetype clears the Gallows Knight (B2); area spells on 30 targets inside G-PERF-S |
| 4.4 Elixirs | the athanor, Quintessence, elixirs | |

**Exit gates:** G-DS, G-COMPAT, B1, B2, G-FEEL.

### Stage 5 · World content

| Wave | Contents |
|---|---|
| 5a | heartland, old forest, heath, steppe, swamp creatures; reworks (husk, drowned, stray, bogged, parched, cave spider, witch, pillager, vindicator, tamed wolves); the full affix list; lair bosses (Barrow King, Bloodmaw, Bog Mother); **Expanse lair anchors**; Hearth quest templates and deeds; Atlas lairs and the danger layer; the full codex |
| 5b | cold forests, tundra, deserts, jungle, coasts, mountains; the underground: shallow and mid caves, great mines and the Pit Foreman, the twelve realms and their bosses (in batches of three); enderman, evoker, ravager, slime and cube, phantom reworks |

Each batch: profiles and moves pass the data tests, the simulator, G-PERF-S, a spawn-density review in the studio
(vista shots like Expanse's), and a user playtest.

## 4. Gates

| Gate | What | Pass when |
|---|---|---|
| **G-DS** | dedicated server: two headless bot clients and one real client; fights, deaths, respawns, relogs mid-fight, a boss fight, a server restart with creatures mid-action | no errors, no stuck creatures, state survives |
| **G-MP** | latency: a dev latency injector at 0, 100, 150 and 250 ms; COMBAT.md §17's parry and dodge tables | every table row matches at ≤ 150 ms; 250 ms degrades gracefully |
| **G-PERF-S** | 200 engaged creatures around 4 bot players | Bestiary ≤ 6 ms of the tick; total ≤ 25 ms a tick |
| **G-PERF-C** | 150 animated creatures in view, on both 26.3 render backends (OpenGL and Vulkan, as the Hub's gates) | ≤ +1.0 ms a frame against the same scene in vanilla |
| **G-NET** | a 30-creature fight | ≤ 6 KB/s down per player |
| **G-SHADER** | screenshot matrix: vanilla · Sodium · Sodium + Iris + Welkin · + Complementary; first and third person; trails, statuses, the boss | everything draws, lit and shadowed; hands in the hand pass |
| **G-COMPAT** | with Mettle, Hearth, Expanse, Atlas, Hub and LOD loaded | every one of their own tours and gametests still passes |
| **G-SAVE** | an old world from before the family (enchanted gear, potions, villagers with trades) | loads; items work and convert; nothing lost |
| **G-DATA** | validation tests (moves meet tell minimums, clips bake, every enchantment has a rune, every potion has a brew) | all green |
| **B1** | the fight simulator | time to kill and time to die inside OVERVIEW §5.2's ranges at every stage and danger |
| **B2** | archetypes | warrior, ranger, rogue, mage and the six hybrids each clear the stage's boss in the simulator and in a playtest |
| **G-FEEL** | the user plays the stage build (a playtest bundle like Hearth's) and gets a GIF set | the user's yes |

## 5. Risks

| # | Risk | Mitigation |
|---|---|---|
| 1 | **Scope.** Four mods and 600+ clips | stage order delivers a playable slice early; each stage only builds what it proves; clip reuse across humanoids |
| 2 | **Feel takes longer than code** | the Motion Studio and short tuning loops (the Welkin lesson: lean agents, fast loops, the user sees results often, no long review workflows) |
| 3 | **Animation authoring throughput** | `.motion.json` for agents, mirroring, procedural layers; the user reviews GIFs, not frame lists |
| 4 | Player body without elbows looks stiff | judged at M0 by GIF, before anything is built on it |
| 5 | **Latency fairness** | defender's grace, long tells, compress-never-skip, G-MP |
| 6 | **Server cost** of AI and sweeps | budgets from day one, G-PERF-S on every stage |
| 7 | Shader packs and Sodium | vanilla render types only, G-SHADER every stage |
| 8 | **Mettle churn**: the retune changes cards players already hold | Mettle's migration rules (old saves never lose points); the retune ships with a migration |
| 9 | Partner requests (Hearth, Expanse, Welkin, Hub) wait on other lanes | every request has a fallback that works without it (CONTRACTS.md §18) |
| 10 | Minecraft's render code moves (26.x changed extract/submit recently) | all render hooks in one compat package with a test each |
| 11 | One game instance at a time is a bottleneck for many lanes | the studio for visual work; batch game tests; headless server tests don't need the GPU |
| 12 | Balance across four mods at once | Mettle's reference curves are the single target; the simulator runs in every lane's CI |

## 6. Decisions for the user

Each has a recommendation; the docs assume it until the user says otherwise.

| # | Decision | Recommended | Alternatives |
|---|---|---|---|
| D1 | The shared library | **Fray**, a library built inside the Bestiary repo by the Bestiary lane, shipped inside all four jars | fold it into Bestiary (then Arsenal and Alembic need Bestiary installed) |
| D2 | Default controls | left Alt dodge, C technique (sneak + C Arts), middle mouse lock-on, right mouse belongs to the off hand's shield or focus | dodge on double-tap only; technique on F (moves swap-hands) |
| D3 | Experience after enchanting | no experience costs anywhere (the anvil repairs with materials); experience feeds Renewal (Mending) and Alembic's Quintessence | keep vanilla's anvil experience costs |
| D4 | Enchanted books | **kept** after reading (they stay valuable, tradeable and fill libraries) | consumed when read |
| D5 | Health at up to 40 | a VOID health bar beside the stamina bar (Figma) | vanilla hearts in two rows |
| D6 | How difficulty scales | **world-anchored danger** by distance, depth and place, plus parties | scale enemies to the player's level; a mix |
| D7 | Limit on brews | **toxicity** | per-brew cooldowns |
| D8 | Mythic ceilings | CONTRACTS.md §7.6's numbers (for the Mettle retune to confirm with the user) | |
| D9 | Damage numbers | **off** by default, an option | on |
| D10 | Boss respawns | lair bosses return after 3 in-game days; realm bosses after 7; first kill is the deed | once per world; per player |
| D11 | The brewing stand | becomes the **bottling stand** (splash, lingering, flasks) | inert decoration; removed from recipes |
| D12 | Weapons as loot | none: bosses give **parts**, materials and tablets (Arsenal's forge-only rule) | rare finished heirloom weapons |
| D13 | Spells and blocks | spells never burn or break blocks unless a server opts in | fire spells light fires |
| D14 | Camera | first person stays the default; over-the-shoulder is an option | a third-person default |
| D15 | Stage 1's boss | the **Gallows Knight** (proves custom rigs and phases) | a beast boss (Bloodmaw) |
| D16 | Vanilla's jump critical | replaced by the plunging strike | kept alongside |
| D17 | Hostile wolves | a separate wild species (grey wolf); vanilla wolves stay tameable and neutral | vanilla wolves turn hostile at night |

## 7. Screens to design in Figma

Rules from the user (VOID UI): flat, rounded, no gradients or glass; quiet colour except on live values; gamified
screens (skills, cards) may use colour and glow. **HUD elements are drawn natively every frame** (Ultralight paints
cost 5–40 ms, Hearth's HUB_UI rule); **pages are Hub (Ultralight) pages** with native parts for anything that moves
fast (gauges, timing games). New frames go in exploration sections; built ones join page 295:2.

### 7.1 HUD (native)

| # | Element | Shows | Stage |
|---|---|---|---|
| H1 | **Stamina bar** | pool, a ghost of the next action's cost, the regen delay, Winded; fades when full out of combat | 1 |
| H2 | **Health** (decision D5) | up to 40 health, absorption, the status build-up meters under it | 1 |
| H3 | **Status row** | Fray statuses beside vanilla effects (icon, time, build-up) | 1 |
| H4 | **Lock-on reticle and target frame** | the mark on the target; its name, health, build-up meters, elite affix icons | 1 |
| H5 | **Nameplates over creatures** | health for hurt creatures and elites (option), affix icons | 1 |
| H6 | **Boss bar** | name, health with phase notches, the Break meter, status meters | 1 |
| H7 | **Encounter banners** | "The Gallows Knight" on start, "Felled" on victory | 1 |
| H8 | **Hit feedback** | hit marker, parry ring, Slip, guard break, damage numbers (option) | 1 |
| H9 | **Telegraph cues** (accessibility) | edge flash and shape-coded marks for red and purple attacks at you | 1 |
| H10 | **Arts and technique cooldowns** | on or beside the hotbar | 1 (Arts), 3 (techniques) |
| H11 | **Ammo and throwables** | quiver count (joins Mettle's), throwable counts | 3 |
| H12 | **Coating charges** | a small pip row on the held weapon | 4 |
| H13 | **Mana bar** | pool, cost ghost | 4 |
| H14 | **Spell selector** | the focus's sockets, selected spell, cooldowns | 4 |
| H15 | **Toxicity** | the pool, Queasy and Poisoned thresholds | 4 |

### 7.2 Hub pages

| # | Page | Shows | Stage |
|---|---|---|---|
| P1 | **Settings · Combat** | controls (and conflicts found), accessibility (§15 of COMBAT.md), camera, HUD layout | 1 |
| P2 | **Codex** | creatures by region and depth; a creature page by knowledge level (seen, fought, felled, studied): moves and colours, weaknesses | 1 (v0), 5 |
| P3 | **Character sheet additions** (Mettle) | stamina, mana, poise, resistances, crit, ceilings reached | 2 |
| P4 | **Alchemy and Arcana decks** (Mettle) | the two new skills' cards in their skill colours | 4 |
| P5 | **Workbench** (Arsenal) | the weapon, its parts, attachments, sockets, what each part gives; assemble and salvage | 3 |
| P6 | **Forge and anvil** | heat; the anvil's timing game (native strike marker) | 3 |
| P7 | **Quench, grindstone, fletching table** | temper choice, edges, arrows | 3 |
| P8 | **Inscribing table** | known runes, the item's sockets, capacity bar, the instability odds, the library's effect, unscribing | 3 |
| P9 | **Item tooltip** | parts, runes (settled and inscribed), coating, maker's mark, history | 3 |
| P10 | **Cauldron** | the formula's steps, heat gauge and hourglass (native), reagent tray, the quality forming | 4 |
| P11 | **Mortar, still, bottling stand, athanor** | small station pages | 4 |
| P12 | **Formula book** | known formulas, experiments and hints, reagents and their virtues | 4 |
| P13 | **Spellbook** | known spells by school; attuning to a focus (at the inscribing table) | 4 |
| P14 | **Hearth additions** (Hearth's pages) | elite and lair bounties on the board; apothecary goods; runes and spells taught in conversation | 5 |
| P15 | **Atlas additions** (Atlas section) | lair places and their felled look; the danger layer and legend | 5 |

## Decisions taken (user, 2026-10-21)

All of D1–D17 are accepted as proposed in the "Proposed" column above: Fray as a shared library inside all four jars; Left Alt dodge, C technique, middle mouse lock-on; no XP costs anywhere; enchanted books kept after reading; a VOID health bar; world-anchored danger; toxicity as the brew limit; damage numbers off by default; boss respawns (lair 3 days, realm 7, first kill is the deed); the bottling stand; bosses drop parts, not weapons; spells never burn or break blocks unless a server opts in; first person by default; the Gallows Knight as stage 1's boss; the plunging strike replaces the jump critical; a separate wild grey wolf.
D8 (Mythic ceilings): settled by Mettle's MYTHIC_POWER.md (branch mettle-mythic), whose seven decisions were also accepted: staying power from health (40 at the top) rather than damage reduction; a Mythic or a top set unlocks the next tier, both unlock the top; 2–5 slots per skill with 2/3/5 sets; every keyword's top set around skill 12–13; Rebirth = a boss heart + 1,000 bits, once per 30 in-game days; 2 loadouts (3 later), free to switch on waking; Wits gives spell power.
