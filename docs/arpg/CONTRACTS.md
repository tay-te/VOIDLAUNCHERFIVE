# Contracts: who owns what, and how the mods talk

The contract between **Fray** (shared library), **Bestiary**, **Mettle**, **Arsenal**, **Alembic** and **Hearth**
(with Expanse, Atlas and Hub as partners). Read it before writing code in any of them. Numbers here are defaults;
the tunable values live in each owner's config or data.

Engine facts below were checked against the 26.3 jars in `~/.gradle/caches/fabric-loom/26.3` with `javap` and
against the decompiled sources in `.tools/mc-src` (2026-10-21). 26.3 is unobfuscated: names are Mojang's.

---

## 1. Modules and ownership

| Module | Mod id · package | Owns | Never owns |
|---|---|---|---|
| **Fray** (shared library) | `fray` · `dev.voidmc.fray` | damage pipeline; the ten damage kinds and resistance attributes; shared statuses and build-up; stamina, mana, toxicity and poise pools; threat; perception service; combat events; action framework (phases, cancel windows, input buffer, prediction); animation runtime (rigs, clips, layers, sync, server timelines, hit volumes); encounter service (boss bars, participants); combat HUD primitives; the family handshake | any creature, item, skill, spell or number tuning beyond neutral defaults |
| **Bestiary** | `bestiary` · `dev.voidmc.bestiary` | mob AI (perception use, tactics, group play), attack patterns and tells, elites and affixes, bosses and encounters, new creatures, reworked vanilla mobs, danger map, spawn rules, creature profiles (resistances, poise), the codex; **the player's combat actions** (light, heavy, charged, dodge, parry, block, lock-on) and base **movesets** for vanilla weapon classes; **the animation library** (clips) and the authoring tools (Blockbench bake, Motion Studio); maintains Fray | player numbers; items beyond trophies and drops |
| **Mettle** | `mettle` · `dev.voidmc.mettle` | every number that comes from the player: skills, perks, cards, talents, attributes; the Mythic retune; **Alchemy** and **Arcana** skills; stamina and mana pool scaling; action-speed, window and cost scaling; the **power ceilings**; the **reference curves** (OVERVIEW §5.1) everything is tuned against | items, creatures, stations, minigames |
| **Arsenal** | `arsenal` · `dev.voidmc.arsenal` | forged weapons (all families incl. bows, crossbows, throwables), spell foci (staffs, wands, tomes), parts and materials, attachments (incl. the coating slot), techniques, forges and stations, **runes** (the enchanting rework), item conversion of enchanted gear, the inscribing table (also inscribes spells into foci) | spell effects (Alembic), skill numbers (Mettle) |
| **Alembic** | `alembic` · `dev.voidmc.alembic` | reagents and their harvesting, brewing stations and the brewing craft, brews (draughts, tinctures, elixirs, oils, bombs, salves, inks), toxicity rules, absorbed vanilla potions and brewing recipes, **spells** (definitions, effects, schools, reactions), casting actions | foci and sockets (Arsenal), skill numbers (Mettle) |
| **Hearth** | `hearth` · `dev.voidmc.hearth` | who fights whom among folk; quests, bounties, deeds, standing, law, prices, trainers, shops | combat rules (Fray), numbers (Mettle) |
| **Expanse** | `expanse` | biomes, structures, ruins, the underground; lair anchors in its structures (request §18.2) | spawns of Bestiary creatures |
| **Atlas** | `atlas` | the map, places, discovery | creature data |
| **Hub** | `voidhub` | Ultralight page chrome and the bridge whitelist | game rules |

**Arsenal and Alembic are one family**: shared materials and reagents live under shared tags in the `void`
namespace (`#void:reagent/*`, `#void:material/*`), Alembic makes the oils, inks and quench brines Arsenal uses, and
Arsenal makes the foci and glassware-free tools Alembic casts through. Each still runs alone (§17).

**Why a separate library.** Arsenal techniques, Alembic casting and Bestiary's actions all need the same action
framework, animation runtime and damage pipeline; if those lived inside Bestiary, Arsenal or Alembic without
Bestiary would have nothing to stand on. Fray ships **inside each of the four jars** (Fabric jar-in-jar; the loader
keeps the newest copy), so any one mod alone carries it, and players never install it separately. The Bestiary lane
builds and maintains it (the user's split: Bestiary owns actions and animation); its api is a separate artifact
(`dev.voidmc:void-fray-api`) the others compile against. Name `Fray` is a working name (ROADMAP decision D1).

## 2. The numbers rule

"Mettle owns the numbers" (the Hearth rule) means: **every number that grows with the player is Mettle's.** Base
numbers of things (a steel blade's damage, a zombie's health, a brew's base duration) belong to the mod that owns the
thing, and are tuned against Mettle's reference curves.

| Number | Owner | Reaches the game through |
|---|---|---|
| Weapon base damage, reach, weight, poise factor, damage-kind split | Arsenal (vanilla weapons: data defaults in Fray) | item attributes, `fray:damage_split` component, `arsenal:build` |
| Action base timings (windup, active, recovery), base costs, base parry window and i-frames | Bestiary (moveset and action data) | `fray:action`, `fray:moveset` registries |
| Player scaling of the above (Fencer's faster recovery, +1 i-frame tick, cheaper dodges) | **Mettle** | `BalanceProvider` keys (§13.4) and attribute modifiers |
| Max stamina, stamina regen, max mana, mana regen, poise, spell power, cast speed, crit chance and damage, max toxicity | **Mettle** (Fray holds the neutral defaults) | Fray attributes (§7), modifiers through Mettle's `EffectsApplier` (fixed ids `mettle:...`) |
| Creature health, damage, poise, resistances, build-up thresholds, danger multipliers | Bestiary | `bestiary:creature` data, danger config |
| Brew base potency, duration, toxicity | Alembic | formula data |
| Brew scaling by skill (potency, duration, toxicity tolerance, yield) | **Mettle** (Alchemy) | `BalanceProvider` keys |
| Spell base damage, cost, cooldown, cast time | Alembic | spell data |
| Spell scaling (power, cost, cooldown reduction) | **Mettle** (Arcana) | attributes `fray:spell_power`, `fray:cast_speed`; keys |
| Rune base values | Arsenal (for absorbed vanilla enchantments: the enchantment JSON itself) | rune data, `minecraft:enchantment` |
| Rune capacity and instability scaling | **Mettle** (Scholarship) | keys |
| Whole-character ceilings | **Mettle** | `Ceilings` (§7.6) |
| Prices, rewards in coin, reputation | Hearth (Mettle's price factors as today) | Hearth api |

Without Mettle, Fray's `BalanceProvider.NEUTRAL` answers every key with its base value: everyone plays with 100
stamina, 40 mana, no scaling. Nothing breaks.

## 3. Fray: packaging and api shape

```
fray/                          (built from the bestiary repo folder, its own Gradle subproject)
  api/      dev.voidmc.fray.api.*         stable, additive only; separate artifact void-fray-api
  core/     dev.voidmc.fray.*             pipeline, resources, statuses, threat, encounters, network
  motion/   dev.voidmc.fray.motion.*      animation runtime (client and server halves)
  action/   dev.voidmc.fray.action.*      action framework, input, prediction
  client/   dev.voidmc.fray.client.*      HUD primitives, camera, lock-on view, first-person body
```

```java
// Service locator; never null after mod init. Consumers look up lazily, never in static initialisers.
public final class Fray {
	static DamageService damage();        static StatusService statuses();
	static ResourceService resources();   static ThreatService threat();
	static ActionService actions();       static MotionService motion();
	static PerceptionService perception(); static EncounterService encounters();
	static BalanceProvider balance();     // Mettle replaces NEUTRAL during init
	static void setBalance(BalanceProvider provider);
}
```

Rules (Hearth's api discipline): the api is **additive only**; new methods are `default`; javadoc dates every
addition; nothing is removed inside a major version. Feature mods request api changes in
`fray/docs/requests/<mod>.md`; the Bestiary lane merges them between milestones.

## 4. The damage pipeline

### 4.1 What 26.3 does today (verified)

`LivingEntity.hurtServer(ServerLevel, DamageSource, float)` checks invulnerability, fire resistance, then
`applyItemBlocking` (the `minecraft:blocks_attacks` component: shields block 100 % in a 90° arc after a 0.25 s
delay), then the **damage cooldown**: a hit within 10 ticks of the last only deals the amount above the last hit,
unless the damage type is tagged `#minecraft:bypasses_cooldown`. `actuallyHurt` applies armour
(`CombatRules.getDamageAfterAbsorb`, skipped for `#bypasses_armor`), then Resistance and enchantment protection
(`getDamageAfterMagicAbsorb`, skipped for `#bypasses_effects` / `#bypasses_enchantments`), then absorption hearts.
Fabric offers `ServerLivingEntityEvents.ALLOW_DAMAGE` and `AFTER_DAMAGE` but no event to change the amount, so the
pipeline below is a Fray mixin. Damage types are a data registry (`data/<ns>/damage_type/*.json`) with tags; items
can name their attack damage type (`minecraft:damage_type` component, new in 26.x).

### 4.2 Fray's pipeline

Every hit made by a Fray action (melee swing, projectile, spell, bomb) is a `FrayDamageSource` (a `DamageSource`
subclass) carrying a `HitContext`:

```java
public record DamageSpec(Map<DamageKind, Float> amounts, float poise, Map<StatusType, Float> buildUp) {}
public record HitContext(Entity attacker, @Nullable ItemStack weapon, Identifier action, DamageSpec spec,
	int flags /* LIGHT, HEAVY, CHARGED, CRIT, RIPOSTE, BACKSTAB, PLUNGE, WEAK_POINT, PROJECTILE, SPELL, AOE */,
	float charge, @Nullable Identifier hurtVolume /* a boss's weak point */) {}
```

Its damage type is `fray:combat`, tagged `#bypasses_armor`, `#bypasses_enchantments`, `#bypasses_shield` and
`#bypasses_cooldown`, because Fray does those steps itself; it is **not** tagged `#bypasses_effects`, so vanilla's
Resistance effect still applies. The stages, in order, on the server thread:

| # | Stage | Who hooks in | What happens |
|---|---|---|---|
| 1 | **Build** | the action (Bestiary, Arsenal, Alembic) | base spec from the weapon or spell: amounts per kind, poise damage, build-up |
| 2 | **Attacker** | Mettle (skills, cards), Arsenal (runes, edge, coating), Alembic (buffs) | modifiers in three pools per kind: `add` (flat), `increased` (summed %), `more` (each multiplies). Final = (base + add) × (1 + Σ increased) × Π more |
| 3 | **Situational** | Fray rules, Mettle cards | crit (×`fray:crit_damage`, 1.5 default), riposte ×2.0, backstab ×2.0, plunge ×1.5, charged ×1.6–2.4, weak point (per hurt volume, e.g. ×1.5) |
| 4 | **Guard** | Fray (block, parry), Mettle (windows) | parried: damage 0, attacker staggered; blocked: physical reduced by the guard, the rest becomes stamina damage; guard break if stamina runs out (COMBAT.md §7) |
| 5 | **Defence** | Fray, Mettle (perks), Arsenal (armour runes) | physical kinds through **vanilla armour and toughness** (the same formula); every kind through its resistance attribute: × (1 − resist), resist in −1.0…0.9 |
| 6 | **Statuses** | Fray | build-up added per status, scaled by the target's status resistance; a full meter procs the status (§6) |
| 7 | **Ceilings** | Mettle | outgoing multiplier and incoming reduction clamped per §7.6; PvP scale applied |
| 8 | **Hand-off** | vanilla | `hurtServer` with the final total: vanilla's Resistance effect, absorption, death, totems, stats, advancements, knockback (Fray sets its own strength first) all run as normal |
| 9 | **After** | everyone | `CombatEvents.DAMAGE_DEALT` with the full breakdown (§10) |

**Foreign damage** (lava, fall, vanilla skeletons' arrows, other mods' hits) keeps vanilla's path. A mixin at the
head of `actuallyHurt` runs only stage 5's resistance step for it: the damage type is looked up in a
**damage profile** (`data/<ns>/fray/damage_profile/*.json`, e.g. `minecraft:lava → fire 1.0`,
`minecraft:arrow → pierce 1.0`, `minecraft:wither → blight 1.0`, `minecraft:magic → arcane 1.0`,
`minecraft:sonic_boom → arcane 1.0, resist ignored`), so a frost ward helps against powder snow and a fire ward
against lava. Unknown types count as physical (`blunt`) only if tagged `#fray:physical`, else they pass untouched.

**The damage cooldown.** Fray hits bypass vanilla's 10-tick cooldown because one swing must be able to hit five
enemies and a dagger must land four hits in a second. Instead Fray keeps **hit-once per action instance** (a swing
hits each target at most once) and a **4-tick per-attacker floor** per target (no two Fray hits from the same
attacker on the same target within 4 ticks unless the action says `multi_hit`). Foreign damage keeps vanilla's
cooldown, so lava and cacti behave as always.

### 4.3 Melee without Fray actions

When Bestiary isn't loaded (no movesets), a player's vanilla attack still goes through the pipeline: Fray wraps
`Player.attack` damage into a `FrayDamageSource` with the weapon's split, so runes, resistances and statuses work
with vanilla swings. Attack strength (vanilla cooldown) keeps applying in that case only.

## 5. Damage kinds and resistances

Ten kinds, fixed in code (a built-in registry `fray:damage_kind`), because each has a resistance attribute and
attributes must exist at startup.

| Kind | Group | Armour applies | Typical sources | Paired status |
|---|---|---|---|---|
| `slash` | physical | yes | swords, axes, claws | Bleed |
| `pierce` | physical | yes (toughness counts double) | spears, daggers' thrusts, arrows, bolts, fangs | Bleed (half) |
| `blunt` | physical | yes (toughness counts half) | maces, hammers, staff strikes, shield bash, falls of rock | Daze, poise ×1.5 |
| `fire` | elemental | no | fire, lava, Kindling, Pyromancy, fire pots | Burn |
| `frost` | elemental | no | Rime, Cryomancy, powder snow, strays | Chill → Frozen |
| `shock` | elemental | no | Thunder, Stormcraft, lightning | Shock |
| `poison` | elemental | no | venom, Venom oil, spiders, asps | Poison |
| `arcane` | occult | no | Kinesis, raw magic, `minecraft:magic` | Hex |
| `radiant` | occult | no | Lumen, Sanctity, holy water | (Smite on undead) |
| `blight` | occult | no | wither, Hexcraft drains, grave things | Blight |

Weapons carry a split in the `fray:damage_split` item component (Arsenal writes it from parts; vanilla defaults by
tag: swords `slash 1.0`, axes `slash 0.7 blunt 0.3`, maces `blunt 1.0`, spears and tridents `pierce 1.0`, bows'
arrows `pierce 1.0`, fists `blunt 1.0`).

**Resistance attributes** `fray:resist/<kind>`: default 0, range −1.0 (double damage) to 0.9 (a 10 % floor),
client-synced so tooltips and the codex can show them. Players get them through Fray's
`FabricDefaultAttributeRegistry.MODIFY` (as Mettle registers `mettle:carry_capacity`); creatures get theirs from
Bestiary creature profiles as base values. Entities that lack the attribute (another mod's mob) read the family
default from their profile, or 0.

## 6. Statuses

### 6.1 The build-up model

Hits don't roll for statuses. They add **build-up** to a meter per status on the target; when the meter fills the
status procs and the meter empties. Meters drain 10 % of their size a second after 2 s without new build-up. This is
readable (the meter is drawn under the target's health bar), fair against bosses (they just have bigger meters) and
works the same for players and creatures.

`threshold = base 100 × (1 + status resistance)`; creature profiles set the resistance per status (undead: Poison
immune, Bleed ×2.0 threshold; bosses ×3–5).

### 6.2 The shared statuses

Where vanilla already has an effect, Fray **reuses it** (so other mods, milk, beacons and the effect HUD still work)
and only adds the build-up and the extra rule.

| Status | Built by | Proc | Lasts | Uses vanilla |
|---|---|---|---|---|
| **Bleed** | slash, pierce (half), Rend rune, serrated edges, wolves | burst of 8 % max health + 4 (bosses 3 % + 4) | instant | — |
| **Burn** | fire | sets on fire for 4 s at 1.5 a second × the source's tier; −20 % fire resist while burning | 4 s | fire ticks (`igniteForSeconds`, the fire overlay) |
| **Chill → Frozen** | frost | each third of the meter is a Chill stack (−10 % movement and attack speed each); full: **Frozen** 1.5 s (rooted, can't act, takes ×1.3 blunt) | Chill 6 s, Frozen 1.5 s | freeze ticks (`setTicksFrozen`, the frost overlay) |
| **Shock** | shock | 0.6 s stun; jumps 50 % of the proc's damage to the nearest wet or metal-armoured target within 4 blocks | 0.6 s | — |
| **Poison** | poison | Poison II for 6 s (bosses Poison I) | 6 s | `minecraft:poison` |
| **Hex** | arcane (Hexcraft) | takes +10 % damage from everything, healing received −25 % | 8 s | — |
| **Blight** | blight | Wither I for 6 s; natural regeneration stopped | 6 s | `minecraft:wither` |
| **Stagger** | poise damage (COMBAT.md §9) | interrupts, can't act; open to a riposte | 20 ticks common, 14 elite | — |
| **Daze** | blunt crits, shield bash, Shock | interrupts the current windup | 10 ticks | — |
| **Exposed** | Sunder rune, thermal shock | armour −30 % | 6 s | — |
| **Rooted** | nets, Pinning Shot, frost traps | can't move, can act | 1–3 s | — |
| **Silenced** | Hex school, certain affixes | can't cast spells | 3 s | — |
| **Wet** | rain, water, Tide brews and spells | shock and frost build-up ×1.5, fire build-up ×0.5; ends Burn | while wet + 5 s | — |
| **Oiled** | oil flasks, Tallow bombs | fire build-up ×2; a Burn proc while Oiled is a **Conflagration** (burst 6 + double duration) | 10 s | — |
| **Marked** | Hunter's Mark, Brand rune | takes +10 % from the marker and their allies | 6 s | `minecraft:glowing` for the marker only |

Vanilla Slowness, Weakness, Blindness, Darkness, Nausea and Levitation stay as they are and count as "control"
for PvP diminishing returns (COMBAT.md §14). Status **reactions** between kinds (wet + shock, frozen + blunt...) are
listed in MAGIC.md §14; Fray implements them so every source triggers them alike.

## 7. Resources

### 7.1 Attributes (all registered by Fray, client-synced unless noted)

| Attribute | Default | Range | Who adds modifiers |
|---|---|---|---|
| `fray:max_stamina` | 100 | 0–1,000 | Mettle (Athletics, Vitality, cards), Arsenal armour runes, Alembic brews |
| `fray:stamina_regen` (per second) | 25 | 0–200 | same |
| `fray:max_mana` | 40 | 0–2,000 | Mettle (Arcana, Wits), Arsenal foci, Alembic |
| `fray:mana_regen` (per second, in combat) | 2 | 0–100 | same |
| `fray:poise` | 20 (player); creature profile | 0–10,000 | armour weight (Mettle), runes, brews |
| `fray:spell_power` | 1.0 | 0–5 | Mettle (Arcana), foci, brews |
| `fray:cast_speed` | 1.0 | 0.25–3 | Mettle, foci |
| `fray:crit_chance` | 0 | 0–1 | Mettle cards, runes (ceiling 0.4) |
| `fray:crit_damage` | 1.5 | 1–5 | Mettle cards, runes |
| `fray:max_toxicity` | 100 | 0–1,000 | Mettle (Alchemy) |
| `fray:resist/<kind>` ×10 | 0 | −1–0.9 | armour runes, brews, creature profiles |
| `fray:status_resist/<status>` | 0 | −1–10 | creature profiles, Unbowed-type perks |

### 7.2 Stamina

Spent only by combat actions (COMBAT.md §8): attacks, dodges, guarding, parries, holding a drawn bow. Sprinting and
jumping cost nothing (hunger stays vanilla's job). Regen starts 12 ticks after the last spend; half rate while
guarding; reaching 0 makes you **Winded** for 30 ticks (no dodge, heavy, charged or parry; guard breaks on the next
blocked hit). Players only: creatures don't use stamina, their limits are cooldowns and recovery.

### 7.3 Mana

Spent by spells (MAGIC.md). Everyone has a small pool (40), so any build can use a wand a little; Arcana is what
makes a mage. Regen 2 a second in combat, 6 a second after 5 s out of combat (Mettle owns both numbers).

### 7.4 Toxicity

Alembic's brew limit (MAGIC.md §6). Fray holds the pool so Mettle and Hearth (a healer's tea) can read it.

### 7.5 Storage and sync

One Fray attachment per living entity in combat, `fray:combat` (stamina, mana, toxicity, poise left, status meters,
current action), **not persistent** except mana and toxicity on players (`fray:player` attachment, persistent, copy
on death: mana refilled, toxicity cleared). Players' own values sync `targetOnly`; what others need to see
(status meters, stagger, current action) goes in Fray's own payloads to trackers only while it changes, never per
tick (§15). Records are immutable, set only on change (Mettle's attachment rules hold: Fabric syncs inside
`setAttached` only when the value isn't `equals` the old one).

### 7.6 Ceilings (Mettle's)

Mettle registers clamps on the final numbers; Fray applies them at pipeline stage 7 and in the resource code.
Proposed for the Mythic retune (Mettle's call; today's card-only ceilings are in Mettle's CARDS.md §14):

| Ceiling | Value (all sources together) |
|---|---|
| Attacker multiplier (pipeline stage 2: skills, cards, talents, runes, brews), over the weapon's base | ×2.6 |
| Situational multiplier (stage 3: the largest of crit, riposte, backstab, plunge, charged, Execute; times weak point) | ×3.0 (×3.5 with a Mastery that says so) |
| Incoming damage after all reduction, before armour | never below 25 % of the raw hit (−75 %) |
| Movement speed | +45 % |
| Crit chance | 40 % |
| Cooldown reduction (techniques, spells, Arts) | 40 % |
| Stamina cost reduction | 35 % |
| Healing from damage dealt (leech) | 8 % of damage, at most 1 heart a second |
| Status build-up multiplier | ×2.0 |
| Max stamina / max mana | 220 / 320 |
| i-frames on a dodge | 10 ticks |
| Parry window (PvE) | 9 ticks (accessibility setting doubles separately, PvE only) |

## 8. Threat and aggro

`ThreatService` keeps a small table per hostile creature (at most 8 entries): threat from damage dealt (1 per point),
from healing or warding someone it is fighting (0.5 per point), from taunts (sets top + 20 %), from noise and sight
(perception adds a little to start a table). Bestiary's tactics read `top(mob)` and also weigh distance and attack
tokens (BESTIARY.md §5); vanilla mobs not reworked keep their own targeting.

Hooks that already exist and move onto it: Mettle's **Guardian** ("hostiles that attack allies turn on you"),
Hearth's guards and companions (their hits count), Hearth's `hostiles_target_folk` option.

```java
public interface ThreatService {
	void add(Mob mob, LivingEntity source, float amount, Identifier reason);
	void taunt(Mob mob, LivingEntity taunter, int ticks);
	@Nullable LivingEntity top(Mob mob);
	List<ThreatEntry> table(Mob mob);
	void clear(Mob mob);
}
```

## 9. Actions and motion (api surface)

An **action** is anything with phases: a swing, a dodge, a cast, a drink, a boss's slam. Actions are data
(`fray:action`): phases and their ticks, costs, cancel windows, hyper-armour, guard flags, the clip they play and the
server timeline (hit volumes, events). A **moveset** (`fray:moveset`) maps inputs to actions for a weapon family or a
creature: the combo graph.

```java
public interface ActionService {
	ActionResult start(LivingEntity entity, Identifier action, ActionContext ctx); // server; validates costs
	@Nullable RunningAction current(LivingEntity entity);
	boolean interrupt(LivingEntity entity, InterruptReason reason);              // stagger, flinch, death
	void registerHandler(Identifier action, ActionHandler handler);              // code for special moves
}
public interface MotionService {
	void play(Entity entity, Identifier clip, MotionLayer layer, float speed);   // server → trackers
	void stop(Entity entity, MotionLayer layer, int blendTicks);
}
```

Details: COMBAT.md §3 (the action model) and ANIMATION.md (clips, timelines, sync).

## 10. Combat events (`CombatEvents`, Fabric events, server thread)

| Event | Arguments | Cancellable | Typical listeners |
|---|---|---|---|
| `BEFORE_ACTION` | entity, action, ctx | yes | Mettle (Arts gating), Hearth (no casting in a chapel?) |
| `ACTION_STARTED` / `ACTION_ENDED` | entity, action, outcome (done, interrupted, cancelled) | no | Mettle XP, codex |
| `MODIFY_DAMAGE` | hit context, a mutable `DamageBuilder` with the stage | per stage | Mettle, Arsenal, Alembic |
| `DAMAGE_DEALT` | attacker, target, hit context, final breakdown per kind, killed? | no | Mettle XP (replaces the AFTER_DAMAGE hooks for Fray hits), Arsenal history, Hearth law (assault) |
| `GUARDED` | defender, attacker, kind (BLOCK, PARRY, GUARD_BREAK), amount | no | Mettle Defense XP and perks |
| `DODGED` | entity, action, perfect? | no | Mettle Athletics, cards (Slipstream-style) |
| `STAGGERED` / `POISE_BROKEN` | entity, by, ticks | no | Mettle (Stagger perk), Bestiary AI |
| `STATUS_BUILDUP` / `STATUS_PROC` | target, status, amount, source | no | HUD, Alembic reactions, Mettle |
| `RESOURCE_SPENT` | player, resource, amount, action | no | Mettle (perks that refund) |
| `WINDED` | player | no | Mettle (Second Wind-like perks) |
| `SPELL_CAST` | caster, spell, focus, mana | no (use `BEFORE_ACTION`) | Mettle Arcana XP, Hearth law |
| `KILLED` | killer, victim, hit context, tags (ELITE, BOSS, NAMED, LAIR) | no | Mettle feats and deeds, Hearth deeds, Atlas |
| `ENCOUNTER_STARTED` / `PHASE` / `ENDED` | encounter, participants with damage shares, outcome | no | Hearth deeds, Mettle Mythics ("first to fell"), Atlas place look |

Order guarantee: Fray fires `DAMAGE_DEALT` after vanilla's `AFTER_DAMAGE`, then `KILLED` after `AFTER_DEATH`.

## 11. Registries

| Registry | Kind | Owner | Data path | Synced |
|---|---|---|---|---|
| `fray:damage_kind` | built-in (10) | Fray | — | yes (static) |
| `fray:status` | built-in | Fray | tuning: `data/<ns>/fray/status/*.json` | yes |
| `fray:damage_profile` | reload data | Fray (+ any datapack) | `data/<ns>/fray/damage_profile/*.json` | no |
| `fray:action` | dynamic | Fray format; content from Bestiary, Arsenal, Alembic | `data/<ns>/fray/action/**.json` | yes |
| `fray:moveset` | dynamic | Bestiary (vanilla classes, creatures), Arsenal (its families) | `data/<ns>/fray/moveset/*.json` | yes |
| `fray:rig` | client assets + server data | Fray format; Bestiary content | `assets/<ns>/fray/rig/*.json` | — |
| `fray:clip` | client assets | Bestiary (library), any mod | `assets/<ns>/fray/clip/<rig>/*.json` | hashed (§16) |
| `fray:timeline` | reload data | baked with the clip | `data/<ns>/fray/timeline/<rig>/*.json` | hashed |
| `bestiary:creature` | dynamic | Bestiary | `data/<ns>/bestiary/creature/*.json` (family, resists, poise, build-up, moveset, danger scaling) | yes |
| `bestiary:affix` | dynamic | Bestiary | `data/<ns>/bestiary/affix/*.json` | yes |
| `bestiary:encounter` | dynamic | Bestiary | `data/<ns>/bestiary/encounter/*.json` | yes |
| `bestiary:spawn_rule` | reload data | Bestiary | `data/<ns>/bestiary/spawn/*.json` | no |
| `arsenal:material` · `part` · `family` · `attachment` · `technique` | dynamic | Arsenal | `data/<ns>/arsenal/...` | yes |
| `arsenal:rune` | dynamic | Arsenal; mirrors `minecraft:enchantment` | `data/<ns>/arsenal/rune/*.json` (new runes; absorbed ones generated) | yes |
| `alembic:reagent` · `formula` · `brew` | dynamic | Alembic | `data/<ns>/alembic/...` | yes |
| `alembic:spell` · `school` | dynamic | Alembic | `data/<ns>/alembic/...` | yes |
| `fray:reaction` | dynamic | Fray (mechanics), Alembic lane (the data) | `data/<ns>/fray/reaction/*.json` | yes |
| `mettle:skill` (+ `alchemy`, `arcana`) | dynamic | Mettle | existing | yes |

Dynamic registries use Fabric's `DynamicRegistries.registerSynced`, like Mettle's.

## 12. Item data components

| Component | Owner | On | Holds |
|---|---|---|---|
| `fray:damage_split` | Fray (format), Arsenal (writes) | weapons, ammo | kind → share, poise factor, build-up per hit |
| `fray:moveset` | Fray (format) | any item | overrides the moveset chosen by tag |
| `arsenal:build` | Arsenal | Arsenal weapons and foci | family, parts, quality, temper, attachments, edge, maker, history |
| `arsenal:sockets` | Arsenal | any item with runes (incl. converted vanilla gear) | sockets: rune (= an enchantment or an Arsenal rune), level, origin (`inscribed`, `settled`), capacity used |
| `fray:coating` | Fray (format), Alembic (content), Arsenal (which parts take one, extra charges) | blades, heads, arrows, any melee weapon | coating id (an `alembic:brew`), charges left, quality; lives in Fray so Alembic can coat a vanilla sword without Arsenal |
| `arsenal:focus` | Arsenal | foci | spell sockets (spell ids from `alembic:spell`), school affinity |
| `alembic:brew` | Alembic | `minecraft:potion`, `splash_potion`, `lingering_potion`, flasks, bombs | formula, quality, brewer, doses left, toxicity |
| `alembic:grade` | Alembic | reagents | harvest grade (Common, Fine, Pristine), freshness day |
| vanilla `minecraft:potion_contents` | vanilla | brews | the effects: Alembic brews **are** vanilla potion items, so everything that reads potions still works |
| vanilla `minecraft:potion_duration_scale` | vanilla | brews | Alembic quality writes it (verified 26.3 component) |
| vanilla `minecraft:enchantments` | vanilla | gear | stays the source of truth for absorbed runes (ENCHANTING.md §8) |
| vanilla `minecraft:mob_visibility` | vanilla | stealth gear | Arsenal's hooded cloaks may use it (verified 26.3 component) |

## 13. Each mod's api for the others

### 13.1 Bestiary (`dev.voidmc.bestiary.api`)

`Bestiary.creatures()` (profile of any entity type: family, resists, codex entry), `danger(level, pos)` (the danger
value 1.0–5.0 at a place), `elite(entity)` (affixes), `encounters()` (active boss fights near a position),
`Lairs` (lair positions and state for Atlas and Hearth), and `HumanoidFighter.attach(PathfinderMob, MovesetId)` so
Hearth folk can fight with Bestiary's humanoid movesets (§18.1).

### 13.2 Arsenal (`dev.voidmc.arsenal.api`)

`Arsenal.items()` (read a build: family, stats, sockets), `runes()` (known runes per player, `learn`, rune lookup by
enchantment), `forging()` (events: `PART_FORGED`, `WEAPON_ASSEMBLED`, `RUNE_INSCRIBED`), `focus(stack)` (spell
sockets for Alembic), `coatingSlots(stack)` (how many charges a part's coating attachment adds). Arsenal reports Smithing XP to Mettle
(Mettle's CARDS.md §10, already agreed).

### 13.3 Alembic (`dev.voidmc.alembic.api`)

`Alembic.brews()` (read a brew stack; `brewOf(potionContents)` maps any vanilla or modded potion to its brew),
`reagents()` (grade, virtues), `spells()` (known spells per player, `learn`, `cast` for scripted casts),
`toxicity(player)`. Events: `BREWED`, `DRUNK`, `SPELL_LEARNED`. Reports Alchemy and Arcana XP to Mettle.

### 13.4 Mettle (`BalanceProvider`, implemented in `mettle.compat.fray`)

```java
public interface BalanceProvider {
	/** The player's value for a key, given the base. NEUTRAL returns base. */
	double value(ServerPlayer player, BalanceKey key, double base, @Nullable Identifier context);
	BalanceProvider NEUTRAL = (p, k, b, c) -> b;
}
```

`BalanceKey` (an Identifier-backed key so new ones are additive): `action_speed` (context: action), `stamina_cost`
(context: action), `parry_window`, `iframe_ticks`, `guard_efficiency`, `poise_damage`, `status_buildup` (context:
status), `brew_potency`, `brew_duration`, `brew_yield`, `brew_toxicity`, `spell_cost` (context: spell),
`spell_cooldown`, `technique_cooldown` (Mettle's existing `Cooldowns.scale`), `rune_capacity`, `rune_instability`,
`perception` (context: observer type). Calls are cached per player and invalidated on Mettle's own change events
(`CARD_SLOTTED`, `PERK_LEARNED`, `RESPEC`...), so the provider costs a map lookup in hot paths.

Mettle also: implements `PerceptionService` modifiers for Stealth (BESTIARY.md §3), listens to `CombatEvents` for XP
(Fray hits replace its `AFTER_DAMAGE` hooks; `DAMAGE_DEALT` carries the weapon and action), registers the ceilings.

## 14. Server and client

| Runs on the server (authoritative) | Runs on the client (presentation and prediction) |
|---|---|
| every action's start, costs, phases, cancel windows | input reading, buffering, and **prediction** of the local player's action start |
| hit detection: swept arcs and hit volumes from server timelines | animation sampling, blending, first-person body, item and VFX attachment |
| damage pipeline, guard, parry, i-frames, statuses, poise | hit-stop, camera shake, sounds, particles, damage numbers |
| stamina, mana, toxicity (the client shows the synced value, predicts its own spends) | HUD (stamina, mana, lock-on reticle, boss bars, meters) |
| AI, perception, tactics, spawning | lock-on camera and target choice (the server validates the target id) |
| lag compensation (rewind for player-vs-player hits; defender's grace) | the local dodge's movement impulse (player movement is client-driven in Minecraft; the server checks it) |

The client's animation classes don't exist on a dedicated server (verified: `net.minecraft.client.animation` and
`ModelPart` are absent from the server jar), so the server never samples bones: it reads baked timelines
(ANIMATION.md §9).

## 15. Networking

Fabric 26.3 names: `PayloadTypeRegistry.clientboundPlay()` / `serverboundPlay()`, `ServerPlayNetworking`,
`PlayerLookup.tracking(entity)`, `EntityTrackingEvents.START_TRACKING` (verified).

| Payload | Dir | Size | When |
|---|---|---|---|
| `fray:action/request` (action, input tick, aim yaw/pitch, lock target, seq) | C→S | ~16 B | each player action |
| `fray:action/ack` (seq, accepted, server start tick) | S→C | ~8 B | reply |
| `fray:motion/play` (entity, clip index, layer, start tick, speed, flags) | S→C trackers | ~12 B | each action or clip change |
| `fray:motion/stop` | S→C trackers | ~6 B | interrupts |
| `fray:combat/hit` (attacker, target, kinds bitmask, flags, amount bucket) | S→C trackers | ~10 B | hit confirm, for hit-stop, sounds, numbers |
| `fray:combat/meters` (entity, status meters as bytes) | S→C trackers | ~4–14 B | when a meter changes, at most 4 a second per entity |
| `fray:combat/state` (entity, stagger, guard, current action) | S→C trackers | ~10 B | on change; sent on `START_TRACKING` for late joiners |
| `fray:resource/self` (stamina, mana, toxicity, winded) | S→C target | ~8 B | on change, at most 10 a second |
| `fray:lock/request` (target) | C→S | ~6 B | lock-on changes |
| `fray:encounter/*` (bar, phase, stagger meter) | S→C participants | small | Fray's own boss bars (vanilla `ServerBossEvent` is the fallback) |
| `bestiary:map/lairs` | S→C | small | every 40 ticks within 1,024 blocks (Hearth's settlements pattern), for Atlas |

Budget: a 30-creature fight costs a player about 4–6 KB/s down. One packet per action, never per tick.

## 16. Versioning and handshakes

- **Semver per mod.** Fray's api follows semver; feature mods declare `"depends": {"fray": ">=1.2.0 <2"}` and ship
  the Fray jar they were built with (jar-in-jar). The loader keeps the newest nested copy.
- **One family handshake.** In the configuration phase the server sends `fray:hello` = (Fray protocol, and for each
  loaded family mod: id, protocol, content hash). The content hash covers action, moveset and timeline data and the
  clip set's hash. The client answers with its own; any mismatch refuses the login with a plain message: "VOID
  Bestiary 0.3.0 required (you have 0.2.1)". Mettle's own `mettle:core/hello` stays as it is.
- **Protocol bumps** whenever server and client must match (a payload format, an action's phases). Content-only
  changes change the hash, not the protocol.
- **Client required.** A vanilla client or one missing a family mod the server runs is refused at the configuration
  phase, as Mettle does today. A client may carry family mods the server doesn't run; they stay idle.
- **Saves.** Every component and attachment has a codec with a version field; old data is migrated on load and never
  dropped (Mettle's rule). Converted vanilla gear keeps its vanilla components, so removing Arsenal leaves working
  enchanted items behind.

## 17. Supported combinations

| Installed | Status | What you get |
|---|---|---|
| **All four + Hearth, Expanse, Atlas, Hub** | **designed and balanced**; every gate runs on it | the full game |
| Bestiary (+ Fray) alone | supported, tested | the new combat on vanilla weapons, reworked and new creatures, elites, bosses; neutral numbers |
| Bestiary + Mettle | supported, balanced | the above with skills, stamina scaling and ceilings |
| Arsenal alone | supported, tested | forged weapons and foci, runes; vanilla-style swings through Fray's pipeline (§4.3); techniques; no dodge or parry |
| Alembic alone | supported, tested | reagents, the brewing craft, brews, oils on any weapon (`fray:coating`), bombs; **no spells** (spells need a focus) |
| Arsenal + Alembic | supported | the above plus spells through foci, with Fray's core clip set (cast, throw, drink) |
| Mettle alone | unchanged from today | |
| Any of them + Hearth | supported | Hearth's quests, guards and law adapt through data and its existing api slots |
| Without Hub | supported, not designed | native fallback screens (dev quality), as Mettle and Hearth today |
| With another combat or animation overhaul (Better Combat, Epic Fight, Player Animator-based mods) | **not supported** | Fray detects them at start and logs a warning; player animation layers switch off |
| With another enchanting or brewing overhaul | not supported | |
| With Iris + any shader pack, Sodium | supported, in the test matrix | |

## 18. Requests to the partners

### 18.1 Hearth (all additive; through its requests folder)

1. **Folk fight with Fray when present.** Hearth's guard fight task, bandits, caravan guards, champions and
   companions call `HumanoidFighter.attach(folk, moveset)` (Bestiary api, behind `isModLoaded`) instead of a plain
   `doHurtTarget` every 20 ticks; Hearth keeps every decision about whom to fight and when to yield. Without Fray
   nothing changes.
2. **Danger-weighted monster pressure.** Pressure counts each monster by Bestiary's danger weight (an elite 3, a
   lair creature 5) when Bestiary is loaded.
3. **Deed kinds** `defense:elite`, `defense:lair`, `bounty:beast_slain` in the deed table (data).
4. **Quest templates** naming Bestiary creatures: added by Bestiary in its own namespace as Hearth data (no Hearth
   code change).
5. **Trainers and teachers:** Alchemy (healer, apothecary, hermit), Arcana (scholar, priest, hermit, monk),
   Scholarship (scholar) via Mettle's existing `HearthRules.TRAINERS` map; runes and spells taught through
   `DialogueProvider` (Arsenal's and Alembic's compat, as Mettle's).
6. **Goods** for reagents, inks and brews in shops (`GoodsItems` data).
7. **Law:** spell and bomb damage counts as assault and arson exactly like blows (it already does if Hearth listens to
   damage by attacker; confirm for `FrayDamageSource`).

### 18.2 Expanse

1. A **lair anchor** marker (a jigsaw data marker read by Bestiary) in ruined_keep, gallows_hill, stone_circle
   crypts, hill_chapel, deepstone_ruins, cavern_halls structures and at each realm's central plateau, plus a
   `#expanse:lairs` structure tag.
2. A **remoteness** read in `ExpanseCountry.Here` is not needed: Bestiary computes danger from distance, depth,
   country and Hearth territory itself.

### 18.3 Atlas

Nothing new in its api: Bestiary's client plugin (`"atlas"` entrypoint) reports lairs as places (category DUNGEON)
and a danger `MapLayer` behind a server rule.

### 18.4 Hub

Bridge whitelists `ARSENAL_OPS`, `ALEMBIC_OPS`, `BESTIARY_OPS` beside `HEARTH_OPS` and `METTLE_OPS`; each mod exposes
`client.overlay.<Mod>State / <Mod>Actions / <Mod>Screens` reached by reflection only (Hearth's HUB_UI.md pattern).
Combat HUD elements are native (drawn every frame), registered with Fabric's `HudElementRegistry` and reserving their
rects with `AtlasHud.reserve`.

## 19. Working rules (parallel lanes)

- Branches and worktrees: `fray` and `bestiary` (one repo folder `bestiary/` with the `fray` subproject),
  `arsenal`, `alembic`, `mettle-mythic`; agents work in `.claude/worktrees/<lane>-<module>`; never switch the main
  checkout's branch or stash there.
- Nobody edits another mod's package. Api needs go in `<mod>/docs/requests/<asker>.md`.
- Fray api changes are additive; a breaking change needs the user's yes and a major version.
- One Minecraft instance at a time machine-wide (the shared game-test lock); the Motion Studio counts as one.
