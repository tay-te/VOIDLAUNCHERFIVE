# Enchanting: runes absorb every enchantment

Arsenal's runes **are** the enchanting rework (Arsenal DESIGN.md §6). The user's decision (2026-10-21): vanilla
enchanting is **replaced as a system** (no enchanting-table slot machine, no experience-level costs), and every
vanilla enchantment is **absorbed as content**: it becomes a rune under the new rules, so loot tables, villager
trades, datapacks, other mods and existing items keep working and convert. Plus many new runes built around the new
enemies and statuses.

Arsenal owns runes, sockets, the inscribing table and item conversion. Mettle owns the numbers that grow with the
player (Scholarship: capacity and instability). Alembic makes the inks.

---

## 1. What changes

| | Vanilla | VOID |
|---|---|---|
| How you get an effect | roll three random offers at the table, pay levels and lapis | **learn** a rune once, then **inscribe** it deliberately into a socket |
| Cost | experience levels + lapis | **ink** (Alembic) + lapis + a material the rune names; **no experience** |
| Limit | one enchantment of each kind, any number per item | **sockets** (how many runes) and **arcane capacity** (how strong in total) |
| Gamble | hidden, every time | yours to choose: inscribe past capacity for a chance of Resonance (and a risk of Flaw or Curse) |
| Bookshelves | count (15 for the best rolls) | count **and** what they hold (libraries) |
| Books | apply at the anvil | **teach** the rune at the inscribing table |
| Anvil combining | books and items merged for levels | gone; the anvil repairs and renames |
| Grindstone | removes enchantments for experience | sharpens edges (Arsenal); removing runes moves to the inscribing table |

## 2. The rune model

### 2.1 A rune is an enchantment

Every rune is an entry in Minecraft's data-driven **enchantment registry** (`data/<ns>/enchantment/*.json`,
verified 26.3: effects, `supported_items`, `exclusive_set`, `max_level`, `anvil_cost`...): the 43 vanilla ones, and
Arsenal's new ones in its own namespace. So the vanilla `minecraft:enchantments` item component **stays the source of
truth** for what an item does, every mod that reads enchantments sees runes, and vanilla's enchantment effect
machinery runs them. Arsenal adds only the **layout**: the `arsenal:sockets` component (which socket holds which
rune, at what level, inscribed or settled, capacity used).

New runes that need Fray behaviour (status build-up, poise, i-frames) use **custom enchantment effect types**
registered by Arsenal (`fray:status_buildup`, `fray:poise_damage`, `fray:stamina_regen`...), the way vanilla's own
effects are written. Arsenal keeps its runes out of `#minecraft:in_enchanting_table` and decides per rune whether it
may appear on loot (`#minecraft:on_random_loot`) or in trades (`#minecraft:tradeable`).

Vanilla rune numbers are retuned in data through Fabric's `EnchantmentEvents.MODIFY` (verified), never by editing
items. Fray's pipeline calls vanilla's `EnchantmentHelper.modifyDamage`, `modifyKnockback`,
`modifyArmorEffectiveness` and `doPostAttackEffects` (verified 26.3) at the right stages, so every enchantment's own
effects still run inside Fray hits.

### 2.2 Sockets and capacity

| Material | Sockets | Capacity |
|---|---|---|
| Wood, stone, leather, bone | 1 | 3 |
| Iron, chain | 2 | 5 |
| Copper (conductive) | 2 | 6 |
| Steel (Arsenal) | 2 | 6 |
| Diamond | 2 | 7 |
| Gold (arcane) | 3 | 9 |
| Netherite | 3 | 9 |

Quality (Arsenal): Crude −1 capacity · Fine +1 · **Masterwork +2 and one more socket**. Parts can add (a gold
pommel +1). Foci have rune sockets separate from their spell sockets. Mettle's Scholarship adds capacity through the
`rune_capacity` key.

Each rune costs capacity per level: **minor** 1 a level (narrow utility), **standard** 2 a level (core combat),
**major** 3 a level (economy and rare power), or a **flat** cost for one-level runes. The mapping table (§4) gives
every rune's class.

### 2.3 Inscribing

At the inscribing table: choose a known rune and a level (up to its max, and up to what the table's library allows,
§3.2), put the item in, pay **ink × level**, **lapis × level** and the rune's **material** (Edge: flint; Sanctity:
gold nugget and bone meal; Rime: a rime crystal...). No randomness while within capacity.

**Past capacity** (Arsenal's gamble): each point over adds 15 % instability (at most 90 %). If it triggers: **Flaw**
60 % (the rune works a level lower), **Curse** 30 % (a curse takes a socket too), **Resonance** 10 % (a level
higher than paid for). Mettle's Scholarship lowers the chance and raises Resonance's share (`rune_instability`). The
odds are shown before you confirm.

**Unscribing** removes one rune: half the ink back, the item keeps the socket, you still know the rune.

### 2.4 Learning runes

Runes are known per player (an Arsenal attachment, kept through death):

| Source | Notes |
|---|---|
| **Reading an enchanted book** at the inscribing table | teaches every rune on it (whether the book is used up or kept is ROADMAP decision D4) |
| **Salvaging enchanted gear** at the inscribing table | the gear is consumed, its runes are learned (Arsenal's design) |
| **Rune tablets** | Expanse hidden chests and ruins; Bestiary elites (5 %) and bosses (always) |
| **Hearth scholars** | teach for coin in conversation (the 300–399 training band) |

## 3. The table and its library

### 3.1 The inscribing table is the enchanting table

The same block, `minecraft:enchanting_table`, with a new screen (a Hub page). Its recipe is unchanged and every table
already in a world or a structure becomes an inscribing table. No new block to learn.

### 3.2 Libraries

| Library | Effect |
|---|---|
| Bookshelves within vanilla's ring (count, up to 15) | the highest level you can inscribe: under 5 shelves level II, 5–9 level III, 10–14 level IV, 15 any level |
| Chiseled bookshelves holding enchanted books of a family (weapon, armour, tool, ranged, focus) | that family's ink cost −10 % per book (at most −30 %) |
| Chiseled bookshelves holding **ruin tomes** (Expanse lore books) | +5 % Resonance share each (at most +15 %) |

## 4. Every vanilla enchantment as a rune

43 enchantments in 26.3 (verified). Values marked "vanilla" are unchanged; others are the proposal for Arsenal's
data, scaled by Mettle. The capacity class: m = minor (1 a level), s = standard (2), M = major (3), or a flat number.

### 4.1 Armour

| Enchantment | Max | Rune | On | Cost | Under the new rules |
|---|---|---|---|---|---|
| protection | IV | **Ward** | any armour | s | +2 % resistance to every damage kind per level per piece (a full set of IV: +32 %) |
| fire_protection | IV | **Ember-ward** | any armour | m | fire resistance +6 % per level per piece; Burn build-up on you −10 % per level |
| blast_protection | IV | **Bulwark** | any armour | m | explosions −8 % per level per piece (vanilla); blunt resistance +3 % per level per piece |
| projectile_protection | IV | **Deflection** | any armour | m | projectile damage −8 % per level per piece |
| feather_falling | IV | **Feather** | boots | m | vanilla |
| respiration | III | **Breath** | helmet | m | vanilla |
| aqua_affinity | I | **Diver** | helmet | 1 | vanilla |
| thorns | III | **Thorn** | any armour | s | reflects 5 % of melee damage taken per level as pierce (ceiling 15 %); no durability cost |
| depth_strider | III | **Current** | boots | m | vanilla |
| frost_walker | II | **Rime-walk** | boots | s | vanilla, and Chill build-up on you −15 % per level |
| soul_speed | III | **Soulstep** | boots | m | vanilla |
| swift_sneak | III | **Hush** | leggings | m | vanilla sneak speed; and your steps are 20 % quieter per level (Fray perception) |
| binding_curse | I | **Curse of Binding** | any | 0 | vanilla |
| vanishing_curse | I | **Curse of Vanishing** | any | 0 | vanilla |

### 4.2 Melee

| Enchantment | Max | Rune | On | Cost | Under the new rules |
|---|---|---|---|---|---|
| sharpness | V | **Edge** | blades, axes | s | +1 damage per level to the weapon's physical share (vanilla gives +1, then +0.5 a level) |
| smite | V | **Sanctity** | melee | s | 10 % per level of the weapon's damage becomes radiant (at most 50 %); +10 % per level against undead and occult; radiant procs Sanctify |
| bane_of_arthropods | V | **Bane** | melee | s | +10 % per level against arthropods; Poison build-up 10 per level per hit |
| knockback | II | **Force** | melee | m | knockback +0.3 a level; poise damage +15 % a level |
| fire_aspect | II | **Brand** | melee | s | Burn build-up 20 per level per hit |
| looting | III | **Plunder** | melee | M | vanilla looting; Bestiary materials +1 per level, one time in four |
| sweeping_edge | III | **Arc** | swords | m | a swing's other targets in its arc take +15 % per level |
| breach | IV | **Breach** | maces | s | vanilla (ignores 15 % armour a level) |
| density | V | **Weight** | maces | m | vanilla smash bonus; poise damage +10 % a level |
| wind_burst | III | **Gale** | maces | s | vanilla |
| lunge | III | **Lunge** | spears | m | the spear's lunge reaches +1 block a level, and costs stamina instead of hunger |

The vanilla exclusive sets stay (one damage rune of Edge, Sanctity, Bane, Breach, Weight per weapon).

### 4.3 Tools and fishing

| Enchantment | Max | Rune | Cost | Under the new rules |
|---|---|---|---|---|
| efficiency | V | **Delving** | m | vanilla |
| silk_touch | I | **Silk** | 3 | vanilla |
| unbreaking | III | **Endurance** | m | vanilla |
| fortune | III | **Fortune** | M | vanilla |
| mending | I | **Renewal** | 5 | vanilla: repairs from experience orbs (experience's lasting use, §8) |
| luck_of_the_sea | III | **Angler's Luck** | m | vanilla |
| lure | III | **Lure** | m | vanilla |

### 4.4 Ranged and thrown

| Enchantment | Max | Rune | On | Cost | Under the new rules |
|---|---|---|---|---|---|
| power | V | **Draw** | bows | s | +12 % arrow damage and poise per level |
| punch | II | **Recoil** | bows | m | vanilla knockback; arrow poise +20 % a level |
| flame | I | **Fireshot** | bows | 2 | arrows burn (vanilla) and add Burn build-up 30 |
| infinity | I | **Endless** | bows | 5 | vanilla; still exclusive with Renewal |
| multishot | I | **Volley** | crossbows | 4 | vanilla |
| quick_charge | III | **Quickload** | crossbows | m | vanilla (−5 ticks a level) |
| piercing | IV | **Pierce** | crossbows | s | vanilla |
| loyalty | III | **Tether** | tridents and **every Arsenal throwable** | s | thrown weapons return (vanilla for tridents; Arsenal's planned Tether rune is this one, widened) |
| impaling | V | **Harpoon** | tridents, spears | s | +10 % per level against aquatic and Wet targets |
| riptide | III | **Riptide** | tridents | 4 | vanilla |
| channeling | I | **Stormcall** | tridents | 4 | vanilla lightning in storms; Shock build-up 25 on every hit |

## 5. New runes

Built around Bestiary's creatures and Fray's statuses. Max level, capacity class, where they're learned.

### 5.1 Weapons

| Rune | Max | Cost | Does | Learned from |
|---|---|---|---|---|
| **Rend** | III | s | Bleed build-up +15 per level per hit | wolf and bear elites' tablets; heartland ruins |
| **Kindling** | III | s | criticals add triple Burn build-up (Arsenal's planned rune) | Ember Deeps camps, cinder hounds |
| **Rime** | III | s | Chill build-up 15 per level per hit | Rime Hollows, frostwights |
| **Thunder** | III | s | Shock build-up 15 per level per hit | Stormcaller elites, peaks' summit crosses |
| **Venom** | III | s | Poison build-up 15 per level per hit | asps, spiders, the Broodmother |
| **Sunder** | II | s | a hit Exposes the target (armour −30 %, 6 s) one time in ten per level | the Gallows Knight |
| **Hexbane** | II | M | +12 % per level against elites and bosses; breaks wards twice as fast | elite tablets (D4+) |
| **Wardbreaker** | III | m | damage to wards ×1.5 per level | Warded elites |
| **Giantsbane** | III | s | poise and Break damage +20 % per level against large creatures and bosses | lair bosses |
| **Riposte** | II | s | ripostes +15 % per level and give back 10 stamina | Hearth city guard captains |
| **Echo** | I | 4 | your technique repeats at half power (Arsenal's planned rune) | ruin tomes |
| **Vigil** | II | s | +10 % per level at night and in darkness underground | hill chapels |
| **Siphon** | III | s | 1 mana per hit per level (spellblades) | Kinesis grimoires' margins |
| **Leech** | II | M | heals 2 % of damage dealt per level (the leech ceiling) | Vampiric elites |
| **Brand-mark** | I | 2 | your hits Mark the target: allies deal +10 % to it | Hearth hunters |

### 5.2 Armour

| Rune | Max | Cost | Does | Learned from |
|---|---|---|---|---|
| **Evasion** | II | M | dodges get +1 i-frame tick per level (ceiling 10 ticks) | rogue-favoured ruins, Shadowstep feats |
| **Fortitude** | III | s | poise +8 per level | revenant soldiers |
| **Second Wind** | III | s | stamina regen +10 % per level | Hearth trainers |
| **Lightfoot** | II | s | dodges cost 10 % less stamina per level | steppe caravanserai |
| **Stillness** | I | 3 | parry window +1 tick (PvE ceiling 9) | the Old Bones |
| **Frostward**, **Stormward**, **Venomward**, **Hexward**, **Blightward** | IV | m | that kind's resistance +6 % per level per piece (the Ember-ward pattern) | the matching realms and creatures |
| **Resolve** | III | s | every status builds on you 8 % slower per level | Hearth priests |
| **Grounding** | II | m | shock resistance +10 % a level; lightning and Arc never jump to you | Stormcaller elites |
| **Shroud** | II | s | creatures see you 10 % closer per level (Fray perception) | echo shades |

### 5.3 Ranged

| Rune | Max | Cost | Does |
|---|---|---|---|
| **Barbed** | II | s | arrows add Bleed build-up 20 per level |
| **Heavy Draw** | II | m | arrow poise +25 % a level; draw +2 ticks a level |
| **Stormshot** | II | s | arrows add Shock build-up 20 per level |
| **Pinning** | II | s | full-draw arrows root for 0.5 s per level |

### 5.4 Foci

| Rune | Max | Cost | Does |
|---|---|---|---|
| **Amplify** | III | s | spell power +8 % a level |
| **Conduit** | III | s | spells cost 8 % less mana a level |
| **Quickening** | III | s | cast speed +8 % a level |
| **Wellspring** | II | s | mana regen +15 % a level |
| **Affinity** (Ember, Rime, Storm, Hex, Lumen, Kinesis) | II | s | that school +12 % a level |
| **Stillmind** | I | 3 | flinches don't break your casts |

### 5.5 Curses

Instability can put one of these into a socket (Arsenal's design names Thirst and Clumsy).

| Curse | Does |
|---|---|
| **Thirst** | if the weapon hasn't struck in 60 s, its next hit costs you 2 health |
| **Clumsy** | one heavy in ten staggers you instead |
| **Brittle** | durability wears twice as fast |
| **Loud** | your attacks are twice as loud (creatures hear you from further) |
| **Leaden** | the item weighs half again as much (Mettle's burden) |
| **Wild Magic** (foci) | one cast in ten is a random spell of the same school |

Curses cost no capacity and come off only by unscribing (and a curse takes double ink to remove).

## 6. Arsenal's runes and Fray's statuses

The build-up runes (Rend, Kindling, Rime, Thunder, Venom, Brand, Bane, Stormcall, Barbed, Stormshot) feed Fray's
meters (CONTRACTS.md §6), so they meet Alembic's oils and spells in the same **reactions** (MAGIC.md §14): a Rime
blade on a Wet target flash-freezes it, a Thunder blade electrocutes in the rain, a Kindling crit on an Oiled bandit
is a Conflagration. Against bosses the meters are bigger, so build-up runes stay useful without trivialising them.

## 7. Converting what already exists

### 7.1 Items

An item with `minecraft:enchantments` and no `arsenal:sockets` is **converted lazily** by
`RuneConversion.ensure(stack)` on the server, the first time it is seen in a player's inventory, a container a player
opens, a trade result, loot generation, or a mob's dropped equipment:

1. Sockets = the material's base sockets (§2.2), or the number of enchantments if larger.
2. Each enchantment goes into a socket as a rune at **its current level, unchanged**, marked **settled**.
3. Settled runes use capacity, but an item over capacity because of settled runes is **stable**: no instability, ever.
   It simply has no room for more until something is unscribed.
4. `minecraft:enchantments` is never removed or changed. Conversion only adds `arsenal:sockets`.

It is idempotent (running twice changes nothing) and the client's tooltip shows the same socket view for unconverted
stacks by computing it, without changing them. So a Sharpness V, Unbreaking III, Mending, Looting III sword from an
old world stays exactly as strong, shows four settled runes, and asks a choice only when its owner wants to change it.

### 7.2 Books

Enchanted books (`minecraft:stored_enchantments`) stay books. Reading one at the inscribing table teaches its runes
(§2.4). They still trade, still drop and still stack in libraries.

### 7.3 Removing Arsenal

Items keep `minecraft:enchantments`, so vanilla enchantments keep working as vanilla. Arsenal's new runes are Arsenal's
enchantments: without Arsenal their entries are missing from the registry and Minecraft drops them from the item on
load, as it does for any removed mod's enchantment. Sockets data is ignored.

## 8. Compatibility

| Thing | What happens |
|---|---|
| **Loot functions** `enchant_randomly`, `enchant_with_levels`, `set_enchantments`, `enchanted_count_increase` | unchanged: they still put enchantments on items; conversion adds sockets later. Loot rolling is not the player-facing slot machine, so it stays |
| **Villager trades** (data-driven in 26.3: `villager_trade`, `trade_set`, `#minecraft:tradeable`, `#double_trade_price`) | unchanged: librarians' enchanted books are rune books; smiths' enchanted gear converts. Arsenal adds trades for inks and tablets through the same data |
| **Mob equipment** (`enchantment_provider`: spawn equipment, raids) | unchanged; dropped gear converts |
| **Datapack enchantments** | they are runes: family from their `supported_items`, capacity cost = their `anvil_cost` clamped to 1–4 a level, learnable from their books |
| **Other mods' enchantments** | the same, and their custom effects keep working because the enchantment component stays the source |
| **Mods that hook the enchanting table** (Fabric `EnchantmentEvents.ALLOW_ENCHANTING`) | the slot machine is gone, so those hooks don't fire for players; listed as a known limit |
| `/enchant`, `/give` with enchantments | work; may exceed capacity (operators' business); converted as settled |
| **Anvil** | renaming and repairing stay; combining books or enchanted items is refused with a pointer to the inscribing table; experience costs (ROADMAP decision D3) |
| **Grindstone** | becomes Arsenal's edge sharpener; disenchanting moves to unscribing |
| **Experience** | orbs still drop and the bar still fills: Renewal (Mending) repairs with it, Alembic's still bottles it into **Quintessence** (elixirs, glyph ink), and the anvil if decision D3 keeps it |
| **Tooltips** | runes show in their sockets in plain words; vanilla's enchantment lines are hidden in the tooltip only (no item change) |
| **Glint** | enchanted items glint as ever |

## 9. Tests

| Test | Checks |
|---|---|
| Mapping (JUnit) | every enchantment in the registry at load has a rune entry (vanilla 43, datapack and mod ones generated); costs and classes in range |
| Conversion (game test) | an old-world sword with Sharpness V, Unbreaking III, Mending, Looting III converts to four settled runes, deals the same damage through Fray as before, and converting twice changes nothing |
| Loot and trades (game test) | a librarian's book and a chest's enchanted armour arrive working and convert on first sight |
| Inscribing (game test) | within capacity nothing random happens; past capacity the outcome rates over 10,000 seeded tries match the shown odds |
| Removal (manual) | a world with Arsenal removed loads, vanilla enchantments still work |
