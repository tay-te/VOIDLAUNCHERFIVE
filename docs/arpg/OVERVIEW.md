# VOID as an action RPG — overview

The master design for turning the VOID modpack (Minecraft 26.3, Fabric, Java 25) into an action RPG: heroic
characters, and a world that fights back. Four mods build on it, plus one shared library they all carry.

```
Status: DESIGN (2026-10-21). Nothing built. Screens go to Figma first (ROADMAP.md §7).
Decided by the user, 2026-10-21: Mythic power (max builds 2x+ vanilla) with much harder, smarter enemies;
the five-mod split below; stamina in combat; our own animation system; vanilla enchanting and brewing replaced
as systems but absorbed as content.
```

| Doc | What it holds |
|---|---|
| **OVERVIEW.md** (this) | the vision, the builds, a session, the power and difficulty curves, how it lands in Hearth, Expanse and Atlas |
| [CONTRACTS.md](CONTRACTS.md) | who owns what, the shared library (Fray), the damage pipeline, statuses, resources, events, registries, handshakes, supported combinations |
| [COMBAT.md](COMBAT.md) | player actions, timings in ticks, stamina, guard and parry, lock-on, feel, PvP, accessibility, Mettle hooks |
| [ANIMATION.md](ANIMATION.md) | our own animation system: rigs, clips, tooling, blending, network sync, server hitboxes, budgets, Iris/Sodium |
| [BESTIARY.md](BESTIARY.md) | AI, perception, telegraphs, group tactics, danger, elites, bosses, the roster |
| [MAGIC.md](MAGIC.md) | Alembic: reagents, stations, brewing as a craft, potions absorbed; spells, mana, schools, foci |
| [ENCHANTING.md](ENCHANTING.md) | runes absorb every vanilla enchantment (mapping table), new runes, item conversion, compatibility |
| [ROADMAP.md](ROADMAP.md) | build order, milestones, tests and gates, risks, open decisions, every screen Figma needs |

---

## 1. The vision

Minecraft combat is click-and-wait: one swing, one cooldown, mobs that walk at you. VOID makes it a fight you
read and answer. A zombie raises its arm a beat before it strikes, and you can step aside, catch the blow on your
blade, or hit it first. A wolf pack circles and one feints while another goes for your legs. A barrow wight on the
heath carries a frost that slows you blow by blow until you burn it off. And your character can answer all of that:
a soldier who parries and ripostes, a ranger whose arrow finds the wight's skull at sixty paces, a hexer who lets
blight and fire do the work, a smith whose own forged blade carries a rune he learned from a ruin.

**Pillars.**

1. **Read and answer.** Every enemy attack has a tell you can see and hear. Every tell has an answer: dodge, guard,
   parry, interrupt, or be somewhere else. Deaths should feel earned, never random.
2. **Heroic, and it shows.** A built character is about twice as strong as the best vanilla player, and plays
   differently, not only harder: new actions, new ways to win. Mythic power, paid for by enemies that are much
   harder and smarter.
3. **Your build is a story.** Skills you trained (Mettle), a weapon you forged (Arsenal), brews and spells you
   learned (Alembic), runes you inscribed. No two characters fight the same way.
4. **A world, not an arena.** Danger has a place: the heath at night, the deep realms, the old keep on the hill.
   Towns are safe because guards fight for them (Hearth). The land tells you where the danger is (Expanse, Atlas).
5. **Minecraft still.** Blocks, building, the vanilla day. Every vanilla item, enchantment, potion, loot table,
   trade and datapack keeps working under the new rules. Surface stays natural (Expanse's rule); the deep may be
   strange.
6. **Server first.** Every number, hit and roll is decided on the server. It works on dedicated servers, in
   multiplayer at real pings, and with Iris, Sodium and Welkin.

## 2. The family at a glance

| Mod | Owns | The player sees |
|---|---|---|
| **Fray** (new shared library, built by the Bestiary lane) | the plumbing: damage pipeline, damage kinds and resistances, statuses, stamina, mana, poise, threat, combat events, the action framework and the animation runtime | nothing by itself; every other mod speaks through it |
| **Bestiary** (new) | mob AI, attack patterns with tells, elites and affixes, bosses, new enemies per biome and underground, the player's combat actions (light, heavy, charged, dodge, parry, block, lock-on) and the animation library and tools | harder, smarter, animated enemies; a fighting character |
| **Mettle** (exists) | skills and numbers; the Mythic retune; new skills **Alchemy** and **Arcana**; the stamina and mana numbers; the power ceilings | a character that grows: drafts, cards, decks, sets |
| **Arsenal** (designed) | forged weapons (swords to bows, crossbows, throwables), spell foci (staffs, wands, tomes), runes = the enchanting rework | a weapon you made, with runes you chose |
| **Alembic** (new) | reagents, stations, brewing as a craft, potions, oils, bombs, and spells (cast through Arsenal's foci) | brews you brewed, spells you learned |
| **Hearth** (exists) | towns, guards, quests, prices, standing: how the world reacts | bounties for named beasts, guards who fight beside you, scholars and apothecaries |
| **Expanse** (exists) | the land: biomes, ruins, the underground realms | where the enemies live, where the lairs are |
| **Atlas** (exists) | the map | lairs found, bosses felled, bounties marked |
| **Hub** (exists) | the in-game UI (Ultralight pages) | every station and book screen |

The rule from Mettle and Hearth carries over to every split: **Mettle owns the numbers** that come from the player
(skills, perks, cards, stamina and mana pools, ceilings); the other mods own their items, stations, creatures and
reactions. CONTRACTS.md §2 spells out exactly which number belongs to whom.

## 3. Build archetypes

A build is skills (Mettle) + a weapon and its runes (Arsenal) + brews and spells (Alembic). There are no classes:
Mettle's backgrounds are a start, not a cage. These are the shapes we design and balance for.

### 3.1 The four pillars

| | **Warrior** | **Ranger** | **Rogue** | **Mage** |
|---|---|---|---|---|
| Fantasy | "I stand in front and nothing gets past." | "It never reached me." | "It never saw me." | "The storm answers me." |
| Core skills | Blades or Heavy Weapons, Defense, Athletics | Marksmanship, Survival, Athletics | Stealth, Blades, Athletics, (Thievery) | Arcana, Alchemy, Wits-heavy |
| Weapons | longsword + shield, greatsword, war axe, mace | longbow, crossbow, javelins, hatchet as sidearm | daggers, sabre, throwing knives | staff, or wand + tome |
| Moment to moment | guard, parry, riposte; poise through blows; heavy and charged hits break enemy poise | keep distance, pick weak points (heads, glowing joints), kite, quick-step out of reach | open from stealth, backstab, dodge-heavy footwork, poisons and bleeds stacking | position, mana and cooldowns; status combos (wet + shock, oil + fire) |
| Signature moments | a perfect parry that staggers an ogre-sized brute; holding a gate for allies (Guardian) | a 60-block headshot on a wight; Arrowstorm into a pack | a triple-damage Assassin opener; Shadowstep behind a caster | a chain of lightning through a drenched pack; a ward that holds a boss's heavy |
| Brews | stamina tinctures, iron-skin, bandage salves | cat's-eye, coated arrows, swiftness | venom oil, smoke pots, shade draught | mana tinctures, clarity, wards |
| Weak to | ranged kiting, magic | being caught in melee, shields | heavy armour, groups | silence, rush-down, low mana |
| Mettle decks (keywords) | Warden, Iron, Brutal | Keen, Gale, Wild | Deep, Keen, Swift | Ember, Gale, Tide, Echo (Arcana adds its own, Mettle's call) |

### 3.2 Hybrids (designed for, not afterthoughts)

| Hybrid | Mix | How it plays |
|---|---|---|
| **Spellblade** | Blades + Arcana | sword in the main hand, wand in the off hand: light combos into a quick bolt; runes on the blade (Kindling, Thunder) feed status combos the wand finishes |
| **Lumen knight** (paladin) | Defense + Arcana (Lumen school) | shield and mace; wards on allies, radiant smites on the undead; the multiplayer anchor |
| **Battle alchemist** | Heavy Weapons + Alchemy | an axe and a belt of bombs; oils on the edge for the enemy's weakness; tinctures that turn a bad fight |
| **Hunter** | Marksmanship + Alchemy + Survival | the witcher of the family: reads a beast's weakness in the codex, coats arrows to match |
| **Hexer** | Arcana (Hex) + Stealth | curses from the dark, blight and poison ticking, a dagger for the finish |
| **Stormcaller skirmisher** | Arcana (Storm) + Athletics | fast feet, chain lightning, dodges that refund stamina |

Every hybrid must be viable at every stage (ROADMAP gate B2: each archetype and hybrid clears the stage boss in
the simulator and in a playtest).

## 4. How a session plays

A 90-minute evening for a mid-game player (around 15 hours in, skills 8–12, steel weapon, a few runes):

| Time | What happens | Systems |
|---|---|---|
| 0:00 | Wakes in a rented bed in a Hearth town. The notice board: a bounty on "the Barrow Wight of Kestrel Heath" (an elite), a caravan escort, an apothecary wants rime crystals. | Hearth quests (data templates naming Bestiary creatures) |
| 0:05 | Buys reagents at the apothecary, sells a spare steel blade. Brews two Frost-ward draughts at the inn's cauldron: a short timing game. | Alembic craft, Hearth market, Mettle Alchemy XP |
| 0:15 | Rides out along the king's road. Dusk. A bandit ambush (Hearth's bandits, fighting with Bestiary's humanoid moveset): pays, talks or fights. | Hearth roads, Fray actions on folk |
| 0:30 | The heath. A wolf pack circles; the alpha's howl buffs the pack (a tell). Parry the lunger, dodge the flanker, kill the alpha first. | Bestiary group tactics, Fray parry/dodge |
| 0:40 | The barrow (an Expanse stone circle with a crypt). The wight's frost builds up a meter on your bar; the draught slows it. Its slam is an orange tell: block, don't parry. Its scream is red: get out. Kill: the codex now shows "weak to radiant, fire". | Bestiary elite with affixes, statuses, codex |
| 0:55 | Loot: a rune tablet (Rime), wight dust (a reagent), a lore page. The Atlas marks the barrow "cleared". | Arsenal rune learning, Alembic reagent, Atlas place |
| 1:05 | A cave mouth nearby. Two levels down the danger rises; turns back when the lantern-light shows a Stalk Crawler den (it's a D4 place). Marks it on the Atlas. | Bestiary danger by depth |
| 1:15 | Back in town: claims the bounty (coin, standing, a deed for the "first to fell" Mythic chase), draws a Marksmanship card, inscribes Rime on the blade at the inscribing table (pays ink and lapis, no XP), forges a new limb for the bow. | Hearth, Mettle cards, Arsenal |
| 1:30 | Logs off with a plan for the crawler den. | |

The rhythm: **prepare in town, venture, fight, return changed.** Fights are short and sharp (a common enemy dies in
3–5 hits, a pack in 30–60 s, an elite in 15–30 s, a boss in 3–6 min); the in-between (travel, building, brewing,
forging, trading) stays as Minecraft as ever.

## 5. Power and difficulty

### 5.1 The power curve (the reference player)

Mettle owns these numbers (CONTRACTS.md §2); they are the targets Bestiary tunes enemies against. "Vanilla best" is
a netherite sword with Sharpness V (11 per hit, 1.6 hits a second: 17.6 damage a second), 20 HP, full netherite with
Protection IV.

| Stage | Hours | Skills | Gear | Light hit | Sustained melee DPS | Max HP | Damage taken (after armour, resist) | Stamina | Mana (mage) |
|---|---|---|---|---|---|---|---|---|---|
| **Early** | 0–5 | 0–6 | crude iron, leather | 6 | 8 | 20 | ×0.75 | 100 | 40 |
| **Mid** | 5–20 | 6–12 | steel, fine; 1–2 runes; chain/iron | 9 | 13 | 26 | ×0.60 | 120 | 90 |
| **Late** | 20–60 | 12–18 | diamond masterwork; 3 runes; elixirs | 14 | 22 | 32 | ×0.45 | 140 | 150 |
| **Endgame** | 60+ | 18–20 | netherite masterwork; full sockets; 6-sets | 20 | 32 | 38 | ×0.35 | 160 | 200 |
| **Apex** | long tail | 20 + Mythics | the above + Mythic cards, perfect brews | 26 | 40–45 | 40 | ×0.30 | 180 | 240 |

So the apex is **2.3–2.6× vanilla best** in damage output and about **2× in staying power** (40 HP and lower
damage taken), plus abilities vanilla doesn't have (parry, dodge, spells, statuses). The hard ceilings that keep it
there are Mettle's (CONTRACTS.md §7.6).

### 5.2 The difficulty curve (danger, world-anchored)

Enemies don't level with you. **Danger** belongs to places (BESTIARY.md §6): you choose when to go deeper, and you
can see where you are. Players feel stronger as they grow, and the world still has places that will kill them.

| Danger | Where (defaults) | Common enemy (zombie) HP · light hit | Elites | Matches stage |
|---|---|---|---|---|
| **D1** | within ~2 km of spawn; Hearth territory; daylight | 24 · 4 | none | Early |
| **D2** | 2–5 km; shallow caves; night near roads | 36 · 5 | 2 % of spawns, 1 affix | Early–Mid |
| **D3** | 5–10 km; remote country; mid caves; ruins | 53 · 8 | 4 %, 1–2 affixes | Mid–Late |
| **D4** | beyond 10 km; high wild country; the deep and realms; great mines below level 3 | 77 · 11 | 6 %, 2 affixes | Late–Endgame |
| **D5** | ember and brimstone realms, the deepest mines, boss lairs, the Nether's fortresses | 108 · 14 | 8 %, 3 affixes | Endgame–Apex |

Time-to-kill targets for a stage-matched player: a common in **3–5 light hits**, an elite in **15–30 s**, a boss in
**3–6 min** solo. Time-to-die: **7–11 common light hits**, **3–5 elite heavies**, **2–4 boss heavies** (and no
boss attack takes more than 60 % of a matched player's health except a clearly telegraphed "ultimate", at most 85 %).
These are the numbers the simulator checks (ROADMAP gate B1).

What makes enemies **smarter**, not only tougher, at higher danger: more moves unlocked per creature, faster recovery
after attacks, feints, flanking and attack tokens, ranged support, healers and shamans in packs, elites with
affixes that change how you must fight (BESTIARY.md).

### 5.3 A difficulty for every table

The game's Easy / Normal / Hard stay and scale enemy numbers (BESTIARY.md §6.3). Servers add **Mythic** (D+1
everywhere, elites twice as common). Accessibility options make parry and dodge windows wider in PvE without
touching anyone else's game (COMBAT.md §15).

## 6. With the rest of VOID

### 6.1 Hearth: towns, guards, quests

- **Towns stay safe because guards fight.** Guards, patrols, caravan guards, companions and bandits are Hearth's
  folk; with Fray present they fight with Bestiary's humanoid moveset (tells, guard, parry) and take damage through
  the same pipeline. Hearth decides who fights whom; Fray gives them the moves. Nothing changes for Hearth without
  Fray.
- **Quests name the new creatures.** Hearth's quest templates are data: bounties, slays, ambushes and commissions
  gain templates for Bestiary beasts, elites and lair bosses ("the Barrow Wight of Kestrel Heath"). Monster pressure
  at night counts danger-weighted monsters, so a town at the edge of D3 country asks for help more often.
- **Deeds and standing.** Felling an elite near a town, a lair boss, or breaking a raid are deeds
  (`defense:elite`, `defense:lair`), with fame. A boss "first to fell" can feed Mettle's deed Mythics.
- **People to learn from.** Scholars teach runes, hermits and priests teach spells, apothecaries sell reagents and
  teach formulas, smiths sell parts. All through Hearth's existing dialogue slot (300–399 training band) and markets.
- **Law still holds.** Spells and bombs used in town are assault like any blow; a fire spell that burns a barn is
  arson (Hearth's DAMAGED event already reports fire).

### 6.2 Expanse: biomes, structures, ruins

- **Every region has its own enemies** (BESTIARY.md §9): grey wolves and boars in the heartland, bears in the old
  forest, wights on the heath, hyenas and asps on the steppe, alligators in the bayou. All grounded, natural
  creatures or grim folk-horror on the surface; the strange and glowing stays underground, as Expanse's rule says.
- **Ruins are where elites and lairs live.** Ruined keeps, gallows hills, stone-circle crypts, hill chapels, deepstone
  ruins, abandoned mine camps and realm centres get lair anchors (an Expanse request: a marker in the structure that
  Bestiary reads). One keep in four holds a lair boss.
- **The underground is the dungeon.** Danger rises with depth; the twelve realms each have their own enemies and a
  realm boss at the central plateau; great mines are the classic dungeon crawl, level by level.
- **Loot that feeds the family.** Rune tablets and grimoires in hidden chests, reagents in the land (heather,
  edelweiss, salt crust, sulfur, glowcaps, amber), forge materials in the deep.

### 6.3 Atlas: the map

- Lairs and boss arenas are **places** (category DUNGEON) that follow Atlas's discovery rules (unknown, sighted,
  discovered) and gain a "felled" look once beaten. Bounty targets show through Hearth's quest markers.
- An optional **danger layer** (server rule, off by default) shades known land by danger level, so players can read
  where they are going. No enemy radar unless the server opts in, as Atlas already rules.

### 6.4 Hub and Welkin

- Every new station and book (workbench, inscribing table, cauldron, still, spellbook, codex) is a Hub page; the
  combat HUD (stamina, mana, lock-on, boss bars, build-up meters) is drawn natively each frame, designed in Figma
  first (ROADMAP §7).
- Welkin draws the world; our creatures and effects go through vanilla render types so the pack lights and shadows
  them. Welkin's entity mapping can give Bestiary creatures their own material ids (wet hide, bone, ember glow
  underground).

## 7. What stays vanilla (promises)

- Vanilla items, mobs, loot tables, villager trades, structures, datapacks and other mods keep working. Enchanted
  gear shows its enchantments as runes; potions are brews (ENCHANTING.md, MAGIC.md).
- Building, mining, farming, redstone, the day and night are untouched. Stamina is spent only by combat actions:
  sprinting and jumping stay free.
- A vanilla client can't join a server running these mods (they add items, entities and screens); the server
  refuses it at login with a clear message, as Mettle does today.
- Every number is a config or data value a server can change.
