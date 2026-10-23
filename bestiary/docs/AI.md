# Bestiary AI (stage 1.4)

Branch `bestiary-ai`. Design: `docs/arpg/BESTIARY.md` §2-§7, §14 on branch `design-arpg`. Builds on Fray (damage,
statuses, poise, threat, telegraphs, profiles) and Fray Motion (clips, timelines, `MotionHits`); nothing here deals
damage or judges a dodge itself.

## 1. How a creature, its moves and its clips connect

```
 data/<ns>/bestiary/creature/<name>.json          (datapack; reloads with /reload)
   entity: minecraft:zombie                       which entity type it takes over (exact type, never subclasses)
   family, poise, resist, perception, roles, morale, tactics weights, codex name
   moves: [ { id, clip, behaviour, range, cooldown, weight, damage, min_danger, tokens, tags, considerations } ]
                     │
                     │ clip = bestiary:zombie/claw
                     ▼
 src/main/motion/bestiary/zombie/claw.motion.json  (Fray Motion source; baked by motionBake)
   markers: tell <colour> · windup_end · active_end · any named event (release, leap)
   locators + hits: the claw's capsule on the right arm; damage
                     │ baked
                     ▼
 client clip (the pose everyone sees)  +  server timeline (strikes, tell tick and colour, events, hit capsules)
```

- A **move** is data in the creature profile; its **clip** owns the timing: the tell colour, the tell tick, the ACTIVE
  window and the recovery come from the baked timeline, so the tell on screen and the hit on the server are one object.
  The move adds what the clip can't know: when to choose it (range, considerations, cooldown, danger), how much of the
  creature's attack damage it deals (`damage` multiplier) and what code runs on its events (`behaviour`).
- **Melee** hits are the clip's capsules: Fray Motion sweeps them on ACTIVE ticks and hands each touch to
  `MotionHits` → Fray's pipeline (defender-favoured timing, parry, block, dodge, Slip). Bestiary scales the damage in
  `CombatEvents.MODIFY_DAMAGE` (danger × difficulty × elite × leader × the move's multiplier).
- **Ranged and special** moves fire on a timeline event (`release`, `leap`, the strike tick): arrows are vanilla
  arrows (the attacker's live tell gives them their flags), a pounce is an impulse with the clip's fang capsule
  sweeping in flight, a web spit is a timed line check.
- The data test (`CreatureDataTest`) loads every profile and every baked timeline and checks BESTIARY §4.2: each move's
  clip exists, has a tell, `activeStart - tellFrom` meets the colour's minimum for the creature's rank at its fastest
  playback (Frenzied included), strings are short enough, and heavy, red and purple moves leave a punish window of 8+
  ticks.

## 2. The loop

```
 PERCEPTION   every 4 ticks (staggered by entity id)   Perception.sense: sight cone, light, sneaking, invisibility,
                                                       close sense, noise (FrayApi.perception()), memory 30 s
 AWARENESS    UNAWARE ─► SUSPICIOUS ─► SEARCHING ─► ENGAGED ─► FLEEING          Brain.awareness
 TACTICS      every 5 ticks while ENGAGED              Tactics.choose: utility scores of every move and position
 EXECUTOR     every tick                               Executor: plays the move's clip (with the tell to the target),
                                                       or a positioning (close, circle, flank, retreat, guard, kite)
 NAVIGATION   vanilla PathNavigation, repath at most every 10 ticks
```

| Piece | Class | Notes |
|---|---|---|
| Profile | `ai.profile.CreatureProfile`, `MoveDef`, `Consideration` | codecs; `CreatureProfiles` is the reload listener |
| Takeover | `ai.VanillaTakeover` | on `ENTITY_LOAD`: removes the type's melee, ranged, leap and swell goals and the player target goal; adds `BestiaryCombatGoal` at the removed goal's priority and `BestiaryTargetGoal` |
| Brain | `ai.Brain` | per-creature state (attachment, not saved): awareness, memory, cooldowns, the running option, token, role, squad |
| Senses | `ai.Perception`, `ai.math.SenseMath` | pure maths in `SenseMath` (JUnit) |
| Choosing | `ai.Tactics`, `ai.math.Utility`, `ai.math.Curves` | score = weight × Π curves × (1 ± 0.15); best, or one of the top two within 10 % |
| Tokens | `ai.Tokens`, `ai.math.TokenPool` | per target: melee 2/3/4 by danger (±1 by difficulty), ranged 2; elites cost 2; bosses ignore |
| Roles, squads, morale | `ai.Squads`, `ai.math.Morale` | bruiser, flanker, skirmisher, ranged, support, leader; leaders buff +15 % within 12 blocks |
| Moves | `ai.Executor`, `ai.behaviour.*` | `MoveBehaviour` per `behaviour` id: `melee`, `arrow`, `volley`, `pounce`, `web`, `fuse`, `grab` |
| Danger | `danger.Danger`, `danger.DangerScaling` | §6.1 map (config `config/bestiary.json`), §6.2/§6.3 multipliers, saved on the creature |
| Elites | `elite.Elites`, `elite.Affix` | six affixes, champion packs, names; shown through Fray's `ProfileService` |
| Codex | `codex.Codex` | per player (saved): seen, fought, felled, the moves learned |

## 3. Api for the boss lane and the others

`dev.voidmc.bestiary.api.BestiaryApi`:

| Call | For |
|---|---|
| `profile(EntityType)` / `profile(LivingEntity)` | a creature's profile (family, resists, moves, codex name) |
| `danger(ServerLevel, BlockPos)`, `info(LivingEntity)` | the danger at a place; a creature's danger, elite flag, affixes |
| `registerBehaviour(Identifier, MoveBehaviour)` | code for a special move (a boss's chain lash, a phase change); the move names it as `behaviour` |
| `brain(Mob)` | the creature's AI state; `brain.force(moveId)` makes it use a move next (bosses' phase scripts, tests) |
| `attach(PathfinderMob, Identifier profile)` | give any mob (a Hearth guard, a boss) the Bestiary loop with a profile (`HumanoidFighter.attach` of CONTRACTS §13.1) |
| `addStealth(StealthModifier)`, `addDanger(DangerModifier)` | Mettle's stealth perks, Hearth's territory and roads |
| `codex(ServerPlayer)` | what the player knows (codex v0, data only) |

A boss adds a phase script on top: it keeps `BestiaryCombatGoal` (utility layer, moves from its profile), registers
its special moves as behaviours, and calls `brain.force` at phase changes. `ignoresTokens` in the profile (rank boss)
skips tokens.

## 4. Vanilla rigs for non-humanoid mobs

Spiders and creepers keep Minecraft's shape. Their rigs (`src/main/blockbench/rig/bestiary/spider.rig.json`,
`creeper.rig.json`, `"vanilla": true`) are the vanilla models' own parts as data plus a virtual `root`; the bake places
hit volumes with them and the client adds the clip on top of vanilla's own pose (`fray.client.VanillaBind`), so the
walk swing stays underneath and nothing but the parts' angles changes.
