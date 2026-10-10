# VOID Mettle — architecture and module contract

VOID Mettle is who you become. You pick a **background** once (Soldier, Ranger, Smith…), which only decides where you
start. From there you get better at what you actually do: mine and Mining rises, fight with a sword and Blades rises.
Every skill level deals a **draft** from that skill's perk deck (three cards, keep one; [DRAFT.md](DRAFT.md)), and perks change how you play (break a whole ore
vein, fell a tree in one cut, raise a shield instantly), not +2 % somewhere. Your **character level** comes from all of
it and buys **attribute** points (Strength, Agility, Vitality, Wits, Presence) that open the deeper tiers of the trees.

And **everything weighs.** Vanilla's 36 slots stay, but what you can carry is set by weight: Strength, perks, a
**backpack**, a **belt** and a **quiver** worn in three new slots on the character, potions, pack animals. Carry too
much and you slow down, then stop sprinting, then barely move.

It is a sibling of **VOID Expanse** (biomes, terrain, structures), **VOID LOD** (far terrain) and **VOID Hearth**
(settlements, folk, coin, quests), built to complement all three and to stand alone.

```
Minecraft 26.3 · Fabric Loader ≥ 0.19.5 · Fabric API 0.161.0+26.3 · Java 25 · optional: VOID Hearth, VOID Expanse
```

This document is the contract between the modules. **Read it all before you write code.** Code against
`dev.voidmc.mettle.api` for anything another module owns; never edit another module's package.

Design detail lives in the sibling docs: [CHARACTER.md](CHARACTER.md), [BURDEN.md](BURDEN.md), [UI.md](UI.md),
[BALANCE.md](BALANCE.md), [INTEGRATION.md](INTEGRATION.md).

---

## 1. Pillars

1. **Fun and easy first.** The player never has to read a wiki. Concretely, every module holds itself to these:
   - **Earn by playing.** XP comes from what you already do. No grind menus, no XP-for-clicking screens.
   - **Perks you feel.** A perk changes behaviour or a number you notice in the first minute. No perk below ~15 %
     effect; no "+1 % crit" filler. If a perk needs a paragraph to explain, cut it.
   - **Plain words, real numbers.** Tooltips say "Stone mines 20 % faster", not "+0.2 block_break_speed".
   - **Always know why.** If you are slow, the HUD says Burdened and the gear panel names the heaviest stack. If a
     perk is locked, it says exactly what it needs ("Needs Mining 6 and Strength 8").
   - **Feedback that feels good.** A soft chime and a chip on every level-up; a badge on the K hint while points are
     unspent; no spam (XP chips merge and fade).
   - **Nothing to configure.** Good defaults on a fresh server; every number tunable for those who want to.
   - **Mistakes are cheap early.** Free respec until character level 5; after that a respec costs something real but
     never a restart.
2. **Learn by doing (Kingdom Come 2), choose by attribute (Cyberpunk).** Skills rise from use. Attributes come from
   character level and gate perk tiers, so two players who both mine a lot can still be different miners.
3. **Weight is honest and readable.** Everything weighs, worn armour included, and the weight is always shown. Weight
   is the inventory limit; the vanilla slots stay so every other mod and every player's habits keep working.
4. **Backgrounds are a start, not a cage.** Any background can learn any skill.
5. **Server-first.** Every decision, number and roll lives on the server. Clients render what the server syncs.
   Cheap per player; nothing scales with world size. 50 players at 20 TPS.
6. **Plays well with the family, needs none of it.** With Hearth: trainers, books, prices, speech checks, coin weight.
   With Expanse: discovery, lore, herbs, wildlife. Alone: all of it still works on vanilla content.

---

## 2. Working in this repo

- **Branch and worktrees.** Work lives on branch `mettle` (made from `hearth`, so Hearth's api is present to compile
  against). Integration worktree `.claude/worktrees/mettle-main`; module agents get one worktree each,
  `.claude/worktrees/mettle-<module>` on branches `mettle-<module>`, merged into `mettle` by the coordinator. Never
  switch the main checkout `C:\Users\bitto\VOID`'s branch, never stash there.
- Build: from `mettle/`, `JAVA_HOME=C:/Users/bitto/VOID/.tools/jdk-25 ./gradlew build`. Keep it green.
- **Looking up the game's code** (26.3 is unobfuscated; names are Mojang's; do not trust memory of older versions):
  - Minecraft, decompiled: `C:/Users/bitto/VOID/.tools/mc-src/common/net/minecraft/...` and
    `.../mc-src/client/net/minecraft/...`. Fabric API: `C:/Users/bitto/VOID/.tools/fabric-src/net/fabricmc/fabric/...`
  - Vanilla assets: inside `C:/Users/bitto/.gradle/caches/fabric-loom/26.3/minecraft-client.jar`.
  - Working idioms in this repo: `hearth/` (player attachments, payloads, screens, HUD elements, key mappings, lang
    merge, config, data keys), `expanse/` (items, entities, renderers, client game tests), `lod/` (JUnit, composite
    build with an optional partner).
- **26.3 renames that bite**: `Identifier` (not ResourceLocation); `GuiGraphicsExtractor` and `extractRenderState` /
  `extractBackground` / `extractLabels` (not GuiGraphics / render); `Minecraft.getInstance().gui.setScreen(..)`;
  entity type constants in `EntityTypes`; bundle and container contents are `ItemStackTemplate`s (both it and
  `ItemStack` implement `ItemInstance`); Fabric `KeyMappingHelper` (not KeyBindingHelper) and `HudElementRegistry`
  (no HudRenderCallback). There is no rounded-rectangle draw call: rounded panels are nine-slice GUI sprites.
- Tabs, concise comments that say *why*, small classes — match `hearth/` and `expanse/`.
- Prefer mixin `@Accessor`/`@Invoker` (in your module's mixin config) over access wideners.
- JUnit 5 in `src/test/java` for pure logic. Client game tests in `src/gametest` for screens and the full loop.

---

## 3. Modules and ownership

| Module | Main package | Client package | Mixins | Lang fragment | Design doc |
|---|---|---|---|---|---|
| api (contract) | `dev.voidmc.mettle.api` | — | — | — | this file |
| **core** | `dev.voidmc.mettle.core` | `client.core` | `mettle-core[.client]` | `core.json` | this file §5–§8 |
| **character** | `dev.voidmc.mettle.character` | `client.character` | `mettle-character[.client]` | `character.json` | CHARACTER.md |
| **burden** | `dev.voidmc.mettle.burden` | `client.burden` | `mettle-burden[.client]` | `burden.json` | BURDEN.md §1–§4 |
| **gear** | `dev.voidmc.mettle.gear` | `client.gear` | `mettle-gear[.client]` | `gear.json` | BURDEN.md §5–§8 |
| **ui** | — | `client.ui` (+ `client.ui.kit`) | `mettle-ui.client` | `ui.json` | UI.md |
| **compat** | `dev.voidmc.mettle.compat.{hearth,expanse}` | `client.compat.*` | `mettle-compat[.client]` | `compat.json` | INTEGRATION.md |

What each owns:

- **core**: the player's character record (attachment), the three data registries (backgrounds, skills, perks), the
  effects applier (§6), the XP ledger and curves, config loading, the `/mettle` command root, payload registration
  helpers, the `mettle:carry_capacity` attribute registration.
- **character**: XP sources and anti-farm rules, perk effects (data effect types and code behaviours), backgrounds
  (picker flow on the server, kits, signature perks), character levels, respec, skill checks.
- **burden**: item weights (rules, recipe derivation, data overrides, nested contents), load and capacity, load bands
  and their effects, the weight table sync, tooltip weight line.
- **gear**: the gear items, the three equipment slots and the pocket slots in the inventory menu, all menu patches
  (shift-click, creative, server slot checks, click-outside), death and keepInventory, quiver ammo, belt quick use,
  the worn-gear render layer, the gear panel screen code.
- **ui**: the VOID panel kit (nine-slice panel, card, cell, meter, chip, button, tooltip), the background picker,
  the character screen (attributes, skills, perk trees), HUD chips, key mappings, sounds for level-ups.
- **compat**: everything that touches Hearth or Expanse types, behind `FabricLoader.isModLoaded` guards.

Rules that keep parallel work mergeable (same as Hearth):

- Each module's `XxxModule.init()` / `XxxClient.init()` is called from `Mettle` / `MettleClient` in a fixed order.
  Do not edit `Mettle.java`, `MettleClient.java`, `build.gradle`, `fabric.mod.json` or anything in `api/`.
- Need something another module or the api does not give you? Write it in `mettle/docs/requests/<module>.md` (what,
  why, proposed signature) and work around it inside your package. The coordinator merges requests between phases.
- Resources: `assets/mettle/...` and `data/mettle/...` under your module's folder where the format allows. Lang keys in
  **your** fragment `assets/mettle/lang/en_us/<module>.json` (merged at build; duplicates with different text fail).
  Keys: `mettle.<module>.*`, `item.mettle.*`, `attribute.name.mettle.*`, `key.mettle.*`.
- Commands: your own subtree under `/mettle <thing>` (Brigadier merges literals).
- Settings: `MettleConfig.load("<module>", CODEC, DEFAULTS)` → `config/mettle/<module>.json`; every field
  `optionalFieldOf(name, default)`.
- Networking: your own payloads, ids `mettle:<module>/<name>`. Server receivers run on the server thread and
  **validate everything** (the client only ever asks).
- Dev switches: system properties `mettle.dev.<module>.*`.

---

## 4. The api (`dev.voidmc.mettle.api`)

Small on purpose. Other VOID mods (Hearth's standing and law modules, a future mod) read Mettle only through this.

```java
// Services, set by the owning module during init; never null after server start.
public final class Mettle {
	static CharacterService characters();   // character
	static BurdenService burden();          // burden
	static GearService gear();              // gear
}

public enum CharacterAttribute { STRENGTH, AGILITY, VITALITY, WITS, PRESENCE }

public interface CharacterService {
	CharacterView view(ServerPlayer player);                  // immutable snapshot
	int level(ServerPlayer player, ResourceKey<Skill> skill);
	int attribute(ServerPlayer player, CharacterAttribute attribute);
	boolean hasPerk(ServerPlayer player, ResourceKey<Perk> perk);
	void award(ServerPlayer player, ResourceKey<Skill> skill, float xp, XpSource source); // goes through anti-farm
	Check check(ServerPlayer player, ResourceKey<Skill> skill, int difficulty);           // odds + roll, see CHARACTER §7
}

public interface BurdenService {
	float weight(ItemInstance stack);                          // kg, whole stack, nested contents included
	float weight(ServerPlayer player, ItemInstance stack);     // same, after the player's perks (Rock Hauler…)
	float load(ServerPlayer player);
	float capacity(ServerPlayer player);                       // = mettle:carry_capacity attribute value
	LoadBand band(ServerPlayer player);                        // LIGHT, BURDENED, OVERBURDENED, PINNED
	void registerModifier(WeightModifier modifier);            // per-player item weight changes (perks, compat)
}

public interface GearService {
	ItemStack worn(Player player, GearSlot slot);              // BACK, BELT, QUIVER
	boolean isGear(ItemStack stack);
}

public record XpSource(Identifier kind, @Nullable Identifier detail) {}   // e.g. mettle:mine / minecraft:iron_ore
public record Check(double odds, boolean passed, int margin) {}
```

Data records (`Background`, `Skill`, `Perk`) and their codecs live in api so compat code and other mods can read
definitions. `CharacterView` exposes background, attribute values (base + spent + background), per-skill XP and level,
learned perks, unspent attribute and perk points, character level and XP.

**Events** (`MettleEvents`, server thread):

| Event | Args | Use |
|---|---|---|
| `MODIFY_XP` | `(player, skill, xp) → double` | scale or cancel XP (compat, server rules); 0 cancels |
| `XP_GAINED` | `(player, skill, xp)` | chips, stats |
| `SKILL_LEVEL_UP` | `(player, skill, from, to)` | sounds, Hearth deeds, advancements |
| `CHARACTER_LEVEL_UP` | `(player, from, to)` | |
| `PERK_LEARNED` / `RESPEC` | `(player, perk, rank)` / `(player, attributes)` | earned perks fire `PERK_LEARNED` too, rank 1 |
| `BACKGROUND_CHOSEN` | `(player, background)` | kits, Hearth greeting lines |
| `LOAD_BAND_CHANGED` | `(player, from, to)` | HUD, compat |
| `DISCOVERED` | `(player, kind, id, pos)` | first visit to a biome, structure or landmark (Hearth, Expanse compat) |

**Attribute**: `mettle:carry_capacity` (base 65, range 0–10 000, syncable, POSITIVE), added to the player through
`FabricDefaultAttributeRegistry.MODIFY` on both sides. Anything may add modifiers to it (gear does, perks do, a
future mod's cart could).

---

## 5. Player state

| Attachment | Holds | Persistent | Copy on death | Sync |
|---|---|---|---|---|
| `mettle:core/character` | background, spent attribute points, XP per skill, learned perks, drafts (queued per skill, dealt hands, rerolls, seed, recent skills) | yes | yes | `targetOnly` |
| `mettle:character/cards` | the owner's cards (a cache of the world ledger `data/mettle/cards.dat`, rebuilt on join), free swaps, pins, and what the deck makes active (perks, dormant cards, trait totals, sets) (CARDS.md §2.1) | yes | yes | `targetOnly` |
| `mettle:character/escrow` | items a player has put up in a trade (back on join after a crash) | yes | yes | no |
| `mettle:character/mythics` | the Mythics a player holds (public, for name cards) | no (rebuilt from the ledger) | — | `all()` |
| `mettle:character/alive_since` | the in-game day the player last came back from death (Hundredth Dawn) | yes | yes | no |
| `mettle:character/journal` | books read, places discovered, trainer sessions, anti-farm memory | yes | yes | no |
| `mettle:burden/load` | `(load, capacity, band, heaviest item id + kg)` | no (derived) | no | `targetOnly` |
| `mettle:gear/worn` | the three worn gear stacks (pockets live inside them) | yes | only with keepInventory | `all()` (others draw it) |
| chunk `mettle:character/placed` | positions of XP-bearing blocks a player placed (ores, logs, crops seeds) | yes | — | no |

Rules (from how Fabric actually behaves in 26.3):

- Values are **immutable records**. Sync happens only inside `setAttached` and only when the new value is not
  `equals` the old, so always build a new record and set it; never mutate in place.
- The character record stays small (it syncs to its owner on every change): no lists that grow with play. Things
  that grow (discoveries, books) go in the unsynced journal.
- `copyOnDeath` transfer happens in Fabric's own `AFTER_RESPAWN` listener; when you need old state during respawn,
  read it from the old player.

---

## 6. Effects: one applier, fixed ids

Everything Mettle does to vanilla numbers goes through **attribute modifiers with fixed ids**, rebuilt by one
idempotent function per player:

```
EffectsApplier.apply(ServerPlayer)   // core; asks each module for its modifiers, removes every mettle:* modifier
                                     // whose id is no longer wanted, adds or updates the rest
```

- Ids: `mettle:attr/<attribute>/<vanilla attribute>`, `mettle:perk/<perk path>`, `mettle:burden/band/<attribute>`,
  `mettle:gear/<slot>`. Never random ids.
- Called on `ServerPlayerEvents.JOIN`, `COPY_FROM`, `AFTER_RESPAWN`, and whenever attributes, perks, background, worn
  gear or load band change. Conditional perks ("while holding a sword", "below y 0") re-evaluate on
  `ServerEntityEvents.EQUIPMENT_CHANGE`, hotbar selection change, and a 10-tick check for positional conditions.
- **Respawn drops every modifier** in 26.3 (`restoreFrom` copies base values only), hence the respawn call. After it,
  top health up to the new maximum.
- **Never touch a base value.** `Player` resets movement speed's base from abilities on load; the base of everything
  belongs to vanilla. Modifiers only.
- Max-health modifiers are **permanent** (saved with the player) so health is not clamped to 20 at load; everything
  else is transient and rebuilt.
- Update a modifier only when its amount changes (`addOrUpdateTransientModifier` resyncs whenever the object differs);
  never rebuild per tick.
- Behaviour perks (Motherlode, Timber, Backstab…) are code in the character module, registered by id and looked up
  through the learned-perk set; they never add modifiers of their own outside the applier.

Vanilla attributes Mettle uses (all player-relevant and client-synced unless noted): `movement_speed`,
`sneaking_speed`, `jump_strength`, `attack_damage` (not synced; server-side only is fine), `attack_speed`,
`block_break_speed`, `mining_efficiency`, `submerged_mining_speed`, `sweeping_damage_ratio`, `max_health`, `armor`,
`armor_toughness`, `knockback_resistance`, `safe_fall_distance`, `fall_damage_multiplier`,
`water_movement_efficiency`, `oxygen_bonus`, `step_height`, `luck`. Prefer one of these over a mixin whenever it
expresses the effect.

---

## 7. Data

| What | Where | Loaded | Synced |
|---|---|---|---|
| Backgrounds | `data/<ns>/mettle/background/*.json` | dynamic registry `mettle:background` | yes (`DynamicRegistries.registerSynced`) |
| Skills | `data/<ns>/mettle/skill/*.json` | dynamic registry `mettle:skill` | yes |
| Perks | `data/<ns>/mettle/perk/<skill>/*.json` | dynamic registry `mettle:perk` | yes |
| XP rules | `data/<ns>/mettle/xp/*.json` | reload listener (`/reload`) | no (server only) |
| Item weights | `data/<ns>/mettle/weight/*.json` | reload listener | yes, payload `mettle:burden/weights` on join and after reload |
| Card traits | `data/<ns>/mettle/trait/<skill>/*.json` | dynamic registry `mettle:trait` | yes |
| Card keywords and sets | `data/<ns>/mettle/keyword/*.json` | dynamic registry `mettle:keyword` | yes |
| Mythics | `data/<ns>/mettle/mythic/*.json` | dynamic registry `mettle:mythic` | yes |
| Card skills (prefix, native keywords, pool) | `data/<ns>/mettle/card_skill/<skill>.json` | dynamic registry `mettle:card_skill` | yes |
| Kits | the `kit` list in each background JSON | with the background registry | yes |

Dynamic registries load with the world (a restart applies changes to backgrounds, skills and perks). XP and weight
tables reload live. Every format is documented with its codec in the owning module and has a JUnit test that loads
the shipped files.

---

## 8. Networking

All client→server payloads are requests the server validates against the character record:

| Payload | Direction | Owner |
|---|---|---|
| `mettle:character/choose_background` `(background, favoured[] for Wanderer)` | C→S | character |
| `mettle:character/spend_attribute` `(attribute)` | C→S | character |
| `mettle:character/learn_perk` `(perk)` | C→S | character (answered "perks come from drafts now") |
| `mettle:character/pick_card` `(skill, index)` · `mettle:character/reroll` `(skill)` | C→S | character (DRAFT.md) |
| `mettle:character/keep_card` `(skill, index, slot)` · `mettle:character/card_action` `(action, cards[], index, keywords[])` | C→S | character (CARDS.md) |
| `mettle:character/trade_action` · `mettle:character/updraft` | C→S | character (CARDS.md §8, §6.3) |
| `mettle:character/card_sitting` · `trade_view` · `mythic_reveal` · `hall_of_firsts` | S→C | character |
| `mettle:character/dash` | C→S | character (Seven-League Stride) |
| `mettle:character/respec` `(kind)` | C→S | character |
| `mettle:gear/belt_use` `(pocket)` | C→S | gear |
| `mettle:core/open_picker` | S→C | core (first join, after a background reset) |
| `mettle:character/xp_chip` `(skill, xp)` batched each second | S→C | character |
| `mettle:burden/weights` `(item → kg table)` | S→C | burden |

The client mod is required: Fabric's attachment sync and the extra menu slots need it. A vanilla client is refused at
the configuration phase with a clear message.

---

## 9. Phases and who builds what

| # | Scope | Modules | Done when |
|---|---|---|---|
| 0 | This contract, design docs, Figma frames, balance sheet | coordinator | user sign-off |
| 1 | Skeleton, api, character attachment and sync, registries, effects applier, `/mettle` commands, JUnit for curves. **UI spike:** one VOID panel drawn both ways (UI.md §1, text size and font) | core, ui | `/mettle xp mining 500` levels Mining, a perk changes an attribute, all of it survives death, relog and a dedicated server; the user has picked the in-game font from the spike screenshots |
| 2 | Weights (rules, recipes, overrides, nesting), capacity, load bands, tooltip, HUD chip | burden, ui | a test proves every registered item has a weight; bands apply and clear; creative is exempt |
| 3 | Gear: items, three slots, pockets, menu patches, quiver and belt behaviour, render layer, gear panel | gear, ui | client game test: wear all three, fill them, die, respawn, keepInventory on and off |
| 4 | Wave 1 skills (8) and their perk trees, anti-farm, level-up feedback, character levels, character screen | character, ui | a fresh survival day feels like progress (playtest) |
| 5 | Backgrounds: picker, kits, signature perks, respec | character, ui | client game test screenshots of the picker and kits |
| 6 | Hearth: trainers, books, prices, speech checks, quest XP, coin weight (after the Hearth requests land). **Built 2026-10-07** (`compat.hearth`, INTEGRATION.md §1.2): the player-skills slot, prices, quest and trade XP, trainers and city masters' Teachings; books and Mettle's own speech-check options still to come | compat | works with Hearth present and absent |
| 7 | Expanse discovery and lore, wave 2 skills | compat, character | |
| 8 | Thievery (Hearth's law module provides the minigames), balance pass, 50-player load test | all | 20 TPS with 50 players |

**Order change (user, 2026-10-04): backgrounds and their in-game GUI came first.** Built: the background and skill
registries (data in `data/mettle/mettle/{background,skill}`), the character attachment, attribute effects (Strength,
Agility, Vitality modifiers and `mettle:carry_capacity`), the picker (opens on first join), the K screen, `/mettle info`
and `/mettle background reset|set`, and the client tour (`./gradlew runClientGameTest`, screenshots in
`build/run/clientGameTest/screenshots`). Signature perks were built 2026-10-05 (all eight; Coin Sense's Hearth half waits for the Hearth price hook) and are
checked in the client tour. **UI target changed to an Ultralight overlay** (OVERLAY.md); the Java screens are a dev
fallback. Not yet: gear items in kits
(the Ranger's quiver, Rogue's satchel, Smith's belt, Merchant's coin pouch and the bedroll come with phases 3 and 6;
kits use vanilla stand-ins until then).

Agents: Opus for every module, one worktree each, effort high; the ui module also starts from the Figma frames on the
page "Mettle · Character & Gear (explore)" in the VOID Figma file (UI is designed there first).

---

## 10. Performance rules

- Nothing per tick per player beyond a few field reads. Load is recomputed **on change** (inventory change trigger,
  equipment change, pocket change) with a 40-tick fallback; modifiers update only when a band or value changes.
- XP awards are cheap map lookups; anti-farm memory is bounded per player (ring buffers), and the placed-block ledger is
  a per-chunk bitset only for XP-bearing blocks.
- Discovery checks (which structure am I in?) run every 40 ticks per player and only where structure starts exist.
- No allocation in render paths; HUD chips and the gear panel read synced records.
- Budget: < 0.05 ms per player per tick on average, measured by the dev harness.

## 11. Testing

- **Unit (JUnit)**: XP and level curves, perk prerequisites and costs, weight derivation (every vanilla item gets a
  weight; iron block = 9 × ingot; nested bundles and shulkers; no cycles hang), load bands, check odds.
- **Game tests**: attribute modifiers survive death, respawn, relog and dimension change; gear slots keep contents;
  death drops and keepInventory; creative inventory does not eat gear.
- **Client game tests** (Expanse's `BiomeTour` pattern): screenshots of the picker, character screen, perk tree,
  inventory with gear, HUD chips, belt radial, saved to `build/run/clientGameTest/screenshots`.
- **Dev harness**: `-Dmettle.dev.*` switches to grant XP, set weights, spawn kits, and log per-subsystem timings.
