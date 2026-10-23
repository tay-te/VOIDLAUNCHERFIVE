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

## 5. The stage 1.4 roster

| Creature | Moves (tell) | Behaviour |
|---|---|---|
| Zombie (gravewalker) | claw, left claw (white); double claw (white, D3); lunge-grab (purple, D4: Rooted and bitten until you dodge free) | bruiser or flanker; closes only with a token, else waits on the ring; never calls others (undead) |
| Skeleton (bone archer) | quick shot (white), aimed shot (orange, a glint 3 ticks before the release), backstep shot, bone club when cornered (white), volley of three (orange, D3) | keeps 7-14 blocks, strafes, never shoots through an ally; vanilla's sun rules stay |
| Spider (crawler) | bite (white), pounce (orange: rears up, then leaps), web spit (purple: Rooted 1.5 s unless dodged), ambush drop from walls | skirmisher or flanker (hit and run); neutral in daylight as vanilla; flees alone under 20 % |
| Creeper | the fuse (red, 30 ticks: vanilla's swell, hiss and blast) | a Stagger (a heavy hit) puts the fuse out; rushes at D3+; still fears cats |
| Revenant soldier (new) | cut, second cut (white), overhead (orange, punish 14), shield bash (white), riposte | shield up between attacks (vanilla's block pose); blocks hits from the front, parries lights (then ripostes); heavies wear its guard down, charged hits and axes break it; flank it |

Elites: health ×3, damage ×1.4, poise ×1.5 (Fray's elite rank), drawn 1.15× bigger, a name from their kind and the land
("the Gravewalker of Kestrel Heath"), affix chips on Fray's nameplate and target frame. Ironbound (+6 armour, poise +60 %,
no Stagger until three blunt hits break the plate), Frenzied (attacks 25 % sooner, 45 % once enraged at half health;
red glint at the eyes), Venomous (poison build-up on hits, a cloud when hit hard), Regenerating (2 % a second after 3 s
unhurt; Burn or Blight stop it), Shielded (the revenant's guard), Swift (+30 % speed, sidesteps a heavy it sees coming).
Champion packs from D3: 3-5 commons around the elite, each sharing one of its affixes, +15 % damage near their leader.

Codex v0 (`codex.Codex`, saved on the player, kept through death): seen, fought, felled, the moves learned (each tell
aimed at the player), studied; knowledge seen → fought → felled 10 → studied. `/bestiary codex` prints it. No screen yet.

NoAI mobs (props, showcase dummies) are left exactly as vanilla has them.

## 6. Results (2026-10-22)

Game tests: `bash tools/gametest.sh -Dfray.show.only=none -Dbestiary.ai=all` (report `fray-report-ai.txt`), 40 checks
pass: each creature fights a mannequin player and every hit follows its tell by at least the colour's minimum; a pack of
seven zombies never has more than the token count attacking or in ACTIVE; a heavy during the fuse defuses a creeper; the
revenant blocks, parries and breaks; the six affixes; a champion pack; the elite on the client's HUD; the codex; a husk
keeps vanilla's AI; a creature saved before Bestiary loads (legacy, never elite); an elite survives a save and load; a
dedicated-server pass (every creature fights the real player, the client receives the tells). Unit tests: `AiMathTest`
(curves, scoring, tokens never exceed N, morale, senses, danger), `CreatureDataTest` (§14 data test). The motion showcase
and `-Dfray.combat=all` still pass.

| Gate | Result |
|---|---|
| G-PERF-S (dedicated server, 200 engaged creatures around 4 moving mannequin bots, 30 s) | Bestiary AI 1.2-1.4 ms a tick (perception 0.09, tactics 0.12, executor with its path requests 1.0-1.15), Fray 0.03-0.08 ms; whole tick 4.2-5.1 ms mean, p95 5.0-6.0 ms. **Met** (≤ 6 ms, ≤ 25 ms) |
| G-NET (dedicated server, 30 creatures fighting the player, 20 s, bytes on the wire) | 7.4-9.4 KB/s down. **Not met** (6). Fray and Bestiary's own payloads are 0.04-0.09 KB/s; the rest is vanilla's entity tracking (motion, position, head-turn packets). The same scene with 30 vanilla-AI variants (husks, strays, cave spiders, zombie villagers) costs 11-13 KB/s |

Bots are mannequins registered with `BestiaryApi.addBot` (creatures treat them as players), teleported in small circles
every tick so the crowd keeps repathing; the integrated client shares the machine, so tick times are on the high side.

Pictures (outside the repository): `C:/Users/bitto/VOID/.tools/share/bestiary/stage1/ai/` (tell → attack GIFs per
creature at half speed, one frame a tick; the elite pack's nameplates; the token ring; the dedicated-server crowd).

## 7. Fray additions (api 0.2.0, additive)

`PerceptionService` (noise; Fray reports hits and parries), `CreatureGuard` and `CombatEvents.CREATURE_GUARD` (stage 4
for creatures), `CombatEvents.ALLOW_STATUS`, `MotionServer.setAllyTest` (creatures' capsules pass through allies),
vanilla-bound rigs (`Rig.vanilla`, `*.rig.json`, `VanillaBind`), `FrayHud.AFFIX_COLOURS`; Fray Motion's test logs are capped.
