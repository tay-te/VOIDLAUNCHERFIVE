# Arsenal — design

Arsenal is what you fight with, and how you make it. A weapon is no longer one item from a crafting grid: it is
**parts** you forged yourself (a blade, a guard, a grip, a pommel), **attachments** that change what it can do (a
poisoned edge, a hooked guard that disarms, a split arrowhead), and **runes** inscribed into its sockets (the
enchantment table, rebuilt). You heat metal at a **forge**, shape it on an **anvil**, quench it, grind it, and put
it together at a **workbench**. Two swords of the same metal can play nothing alike.

It is a sibling of **VOID Mettle** (skills, perks, weight), **VOID Hearth** (settlements, folk, coin, quests),
**VOID Expanse** (the land) and **VOID Hub** (the in-game UI), built to complement them and to stand alone.

```
Minecraft 26.3 · Fabric · Java 25 · optional: VOID Mettle, VOID Hearth, VOID Expanse, VOID Hub
```

Status: **DESIGN** (2026-10-10). Nothing built. Screens go to Figma before code (page 295:2 once built).

---

## 1. Pillars

1. **Your weapon is yours.** Every part is a choice you can see and feel in the first swing: reach, speed, weight,
   what the right click does. No "+2 % damage" parts; a part either changes a number you notice (≥ 15 %) or changes
   behaviour.
2. **Making is play.** Forging is short, physical and readable: heat it, strike it on the beat, quench it. Doing it
   well makes a better part; doing it badly still makes a part.
3. **Randomness you choose.** The fun gamble is yours: overload a weapon with runes and it may crack, curse, or
   come out brilliant. Nothing is random behind your back (no lapis slot machine).
6. **Crafted, and unique to its maker.** (The user, 2026-10-10: weapons should feel crafted and unique to players.)
   Arsenal weapons come from the forge and the workbench, never the crafting grid. Every forged part carries its
   **maker's mark** and small natural variation (a few % in its numbers, its exact look), so no two are identical.
   A weapon you assemble gets a **name** (generated, or yours to give at the workbench) and keeps a short
   **history**: who made it, from what, its kills of note ("felled the Warden of Kestrel Ford"), its runes' story.
   Old weapons become heirlooms; a weapon from another player feels like theirs.
4. **Readable at a glance.** The workbench shows the weapon, its sockets and what each part does, in plain words.
   The tooltip lists the parts. You always know why a weapon is good.
5. **Vanilla stays playable.** Vanilla weapons already in the world (loot, old saves, mobs' drops) still work: each is
   a weapon with fixed, plain parts you can take apart and rebuild. Their crafting-grid recipes are removed: new
   weapons are forged (§1.6). Old enchanted gear keeps its enchantments as runes.

## 2. The loop

```
 ore / salvage ──► FORGE (heat) ──► ANVIL (shape a part) ──► QUENCH (temper) ──► GRINDSTONE (edge)
                                                                                     │
           runes known ◄── libraries, ruins, scholars, salvage                       ▼
                │                                                       WORKBENCH (assemble, attach)
                └──────────────────► INSCRIBING TABLE (runes into sockets) ◄────────┘
```

A player's first hour: a small forge (cobblestone, a furnace, an iron ingot) and the anvil make a Crude iron blade;
the workbench puts it on a plain grip. Or: salvage a vanilla iron sword at the workbench → its blade, guard and grip come out → swap the
grip for a long oak haft → now it is a slower, longer-reaching blade. Later: a steel bloom from the forge, a
masterwork blade off the anvil, an oil-quenched edge, a hooked guard, and a rune of Kindling in its one socket.

## 3. Stations ("forges, and other things")

Each station is a block (some with an optional bigger build). All of them open a VOID Hub screen.

| Station | What it does | Notes |
|---|---|---|
| **Forge** | Heats metal: ingots, salvage, blooms. Holds a **temperature** that fuel and **bellows** raise and that falls over time. | A small forge is one block. A **great forge** (forge + chimney + bellows + anvil within 3 blocks) heats faster and reaches steel and netherite heats. |
| **Bloomery** | Iron + charcoal → **steel bloom** over time (smelts while you play). | Steel is the first new material: tougher than iron, between iron and diamond. |
| **Anvil** (reworked) | Shapes a hot ingot into a **part**. A short timing game: strike when the marker is in the glow. Hits set the part's **quality**. | Vanilla anvil repair/rename stays as the anvil's second tab. |
| **Quench trough** | Water, oil or brine, filled by bucket. Quenching a hot part gives it a **temper** (hard, tough, springy). | Water: hard (more damage, less durability). Oil: tough (more durability). Brine: springy (faster). |
| **Grindstone** (reworked) | Puts an **edge** on a blade: a bonus that wears off with use; whetstones do the same in the field. | Vanilla disenchanting moves to the inscribing table. |
| **Workbench** | Assembles and takes apart weapons, fits **attachments**. Free and instant: building should never be a chore. | Salvaging a vanilla weapon gives its parts back. |
| **Fletching table** (reworked) | Arrowheads, shafts and fletchings → arrows with behaviour (barbed, whistling, split, heavy). | Gives the fletching table a use at last. |
| **Inscribing table** (was the enchanting table) | Inscribes **runes** you know into an item's sockets; removes them (some lost). | See §6. Bookshelves become **libraries**. |

## 4. Weapons are parts

### 4.1 Families and parts

| Family | Parts | Technique (from the head part) |
|---|---|---|
| Sword | blade · guard · grip · pommel | longsword *riposte*, sabre *flurry*, greatsword *cleave* |
| Axe | head · haft · grip | *hook shield*, *chop* (break a raised shield), *throw* (hatchet) |
| Mace / hammer | head · haft · grip | *ground slam*, *crush* (ignores armour), mace's vanilla smash kept |
| Spear | head · shaft · butt | *lunge*, *brace* (stops a charge), *throw* |
| Dagger | blade · grip | *backstab*, *quick step* |
| Bow | limbs · string · grip · rest | *draw speed* vs *power*, *volley* |
| Crossbow | stock · prod · string · sight | *aimed shot*, *repeater* |

A **technique** is what right-click (or the attack key held) does with that weapon: one per weapon, set by the head
or blade. Vanilla's own (shield block, trident throw, mace smash) stay where they are.

### 4.2 Stats a part changes

Damage · speed · reach · weight (feeds Mettle's load) · durability · **sockets** · **arcane capacity**.
The tooltip shows the totals and which part gave what ("Reach +0.5 · from Long haft").

### 4.3 Materials

| Material | Trait |
|---|---|
| Copper | Light, **conductive**: +1 arcane capacity. Soft (low durability). |
| Iron | Plain and honest: the baseline. |
| Steel (new) | Tough: +40 % durability over iron, a little heavier. |
| Gold | Soft and heavy, **arcane**: +2 capacity. Piglins respect it. |
| Diamond | Hard: damage and durability. |
| Netherite | Everything, and it floats. Great forge only. |
| Wood / bone / leather | Hafts, grips, shafts; bone grips are light, leather wraps add grip (faster). |

### 4.4 Quality

Each forged part has a **quality**: Crude · Fair · Fine · Masterwork. It comes from the anvil strikes, the forge's
heat and (with VOID Mettle) your **Smithing** skill, which widens the strike window and unlocks Masterwork.
Quality scales the part's numbers (Crude −15 %, Masterwork +20 %) and Masterwork parts get one extra socket.

## 5. Attachments

Attachments sit on a part and **change behaviour**. Each part has 0–2 attachment points.

| Attachment | On | Does |
|---|---|---|
| Coating (poison, fire oil, frost resin) | blade, head, arrowhead | Applies on hit for N hits, then wears off; re-coat at the workbench or by hand. |
| Hooked guard | guard | Your technique gains *disarm* (the target drops its held item, short cooldown). |
| Counterweight | pommel, butt | Heavy attacks knock back; slower. |
| Serrated edge | blade | Bleed on hit (damage over time); grinding removes it. |
| Lantern hook | haft | Holds a lantern: light without an offhand. |
| Scope | crossbow sight | Zoom while aiming; aimed shots crit. |
| Split rest | bow rest | Volley: three arrows, less damage each. |
| Quiver clip | grip | Arrows from your Mettle quiver load faster. |

## 6. Runes: the enchanting rework

The enchanting table is a slot machine you cannot read. Arsenal makes it a deliberate choice with a gamble you opt
into.

- **Runes are learned, not rolled.** You learn a rune by reading an enchanted book, salvaging enchanted gear at the
  inscribing table (the gear is consumed, the rune is learned), finding rune tablets in ruins (VOID Expanse), or
  being taught by a scholar (VOID Hearth). Your known runes are a list on the inscribing table.
- **Inscribing is a choice.** Pick a known rune and a level, put the item in, pay **XP + lapis + a material** the rune
  names. It goes into a free **socket**. No randomness.
- **Arcane capacity.** Every item has a capacity (material + parts + quality). Each rune level costs capacity.
  Inscribing **past capacity** is allowed: each point over adds a chance of **instability** when you finish:
  - *Flaw* (most common): the rune works at one level lower.
  - *Curse*: a curse rune takes the socket too (Binding, Vanishing, or new ones: Thirst, Clumsy).
  - *Resonance* (rare): the rune comes out one level **higher** than paid for. The reason to gamble.
- **Libraries.** Bookshelves around the table still matter, but by **what** they hold, not how many: a shelf of
  combat books makes combat runes cheaper; a shelf of tomes from ruins raises the table's max level.
- **Vanilla enchantments become runes** one to one (Sharpness → Rune of Edge, Protection → Rune of Ward...). An
  enchanted item from before Arsenal shows its enchantments as runes already in sockets. Books stay books.
- **New runes** come in later waves (Kindling: ignite on crit; Tether: thrown weapons return; Echo: technique
  repeats at half power).

## 7. With the other VOID mods (all optional)

| Mod | What Arsenal gets | Ownership |
|---|---|---|
| **VOID Mettle** | Smithing widens strike windows, unlocks Masterwork and steel; Blades / Heavy Weapons / Marksmanship scale techniques; part weight counts in the load. | Mettle owns the **numbers** (skill scaling); Arsenal owns the items and stations. Same rule as Hearth/Mettle. |
| **VOID Hearth** | Smiths sell parts and take commissions; scholars teach runes; quest rewards can be named parts or rune tablets. | Hearth owns NPCs and quests; Arsenal exposes an api (parts, runes) Hearth calls. |
| **VOID Expanse** | Rune tablets and lost parts in ruins; ores where they make sense. | Expanse owns the structures; Arsenal owns loot tables it adds to them. |
| **VOID Hub** | Every station screen is a Hub page (workbench, forge, inscribing table); the hover tooltip lists parts and runes. | Hub owns the chrome; Arsenal sends state like Mettle does. |

## 7b. Maker's mark and history

- **Mark:** the forging player's name and a mark (an icon they pick once, shown on the workbench and the tooltip).
- **Variation:** each forged part rolls ±4 % on its numbers within its quality, and a small cosmetic variation
  (blade notch, wrap colour, pommel shape) drawn by the composed model.
- **Name:** generated from material, shape and maker ("Ashford's Long Steel"), renamable at the workbench.
- **History:** up to five lines kept on the weapon: forged by, notable kills (named or boss mobs, players), runes
  inscribed, repairs at a smith. Shown in the tooltip's last section.

## 8. Technical sketch

- **One data component** on the stack: `arsenal:build` = family, parts (material, shape, quality, temper,
  attachments), runes per socket, edge and coating charges. Everything else (damage, speed, reach, weight,
  capacity) is computed from it and cached. Vanilla weapons without the component read as their fixed default
  build.
- **Item rendering**: a custom item model type that composes the part models from the component (one model per part
  shape and material, tinted), so any combination draws without a model per combination.
- **Server-authoritative**: stations validate everything; the client only asks (as the Hub already does).
- **Techniques** are server-side actions triggered by use-item / held attack, with a cooldown shown on the hotbar.
- **Enchantments**: vanilla's enchantment registry stays the source of truth for vanilla runes (a rune is an
  enchantment + socket), so other mods' enchantments still work and show up as runes.

## 9. Waves

| Wave | Contents |
|---|---|
| **1 · Parts** | `arsenal:build`, swords + axes + spears from parts, workbench (assemble, salvage), forge + anvil timing game, quench, steel + bloomery, tooltip + Hub workbench page, composed item models. |
| **2 · Behaviour** | Techniques, attachments, grindstone edges, daggers + maces, bows + crossbows from parts, fletching table. |
| **3 · Runes** | Inscribing table, known runes, capacity and instability, libraries, vanilla enchantment conversion, new runes. |
| **4 · Together** | Mettle scaling, Hearth smiths and scholars, Expanse loot, balance pass. |

## 10. Open questions for the user

1. ~~Name~~: **Arsenal** (decided 2026-10-10).
2. ~~Vanilla crafting~~: forge-only for new weapons, so they feel crafted and unique (decided 2026-10-10).
3. **Forge game**: the anvil timing game (recommended), or no minigame (quality from heat and skill only)?
4. **Great forge**: multiblock bonus build (recommended), or single blocks only?
