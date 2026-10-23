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
| G-NET (dedicated server, 30 creatures fighting the player, 20 s) | Bestiary + Fray 0.29 KB/s (encoded payloads); total 9.63 KB/s down on the wire against 10.16 KB/s for the same scene with vanilla AI. **Met** under the revised gate (below; 2026-10-22, stage 1.5 run). First run, under the old gate: 7.4-9.4 KB/s total, **not met** (6) |

**G-NET gate revised (2026-10-22, the user's call):** "≤ 6 KB/s of Bestiary + Fray traffic, and the total no more than the
vanilla-AI baseline for the same scene". Why: the 6 KB/s total in ROADMAP §4 can't be met by any 30-creature fight; almost
all of the wire is vanilla's entity tracking (motion, position and head-turn packets: about 5000 motion packets in 20 s
for 30 creatures), and the same scene run by vanilla AI (30 husks, strays, cave spiders, zombie villagers) costs more
(10-13 KB/s) than Bestiary's (7.4-9.6). So the gate now measures what Bestiary and Fray add, and holds the whole fight to
"no worse than vanilla". `AiShowcase.dsNet` reports both numbers: Bestiary + Fray's own payloads counted where the server
encodes them (custom payloads in the `fray` and `bestiary` namespaces, before compression: an upper bound on their wire
cost), and the total bytes the client receives, beside the vanilla-AI baseline; the gate is asserted. Margin note: the
baseline varies run to run (10.2-13 KB/s seen) and the Bestiary scene 7.4-9.6, so the total check has a 5-25 % margin.

Bots are mannequins registered with `BestiaryApi.addBot` (creatures treat them as players), teleported in small circles
every tick so the crowd keeps repathing; the integrated client shares the machine, so tick times are on the high side.

Pictures (outside the repository): `C:/Users/bitto/VOID/.tools/share/bestiary/stage1/ai/` (tell → attack GIFs per
creature at half speed, one frame a tick; the elite pack's nameplates; the token ring; the dedicated-server crowd).

## 7. Fray additions (api 0.2.0, additive)

`PerceptionService` (noise; Fray reports hits and parries), `CreatureGuard` and `CombatEvents.CREATURE_GUARD` (stage 4
for creatures), `CombatEvents.ALLOW_STATUS`, `MotionServer.setAllyTest` (creatures' capsules pass through allies),
vanilla-bound rigs (`Rig.vanilla`, `*.rig.json`, `VanillaBind`), `FrayHud.AFFIX_COLOURS`; Fray Motion's test logs are capped.

**api 0.3.0 (stage 1.5, additive):** `EncounterService.start(..., boolean announce)` (resume a fight without the banner),
`HitFlags.GUARD_CRUSH` (a creature clip's `guard_crush` marker: blocking it costs ×2 stamina), weak points for the player's
sweep (`Sweep.weakPoint`: the swing's line within a hurt capsule's radius + 0.6 sets `WEAK_POINT`, ×1.5 damage and poise;
`DamageMath.segmentDistance`), `RigModel.HIDDEN` (bones hidden per frame from the render state). The HUD's floating
nameplates are laid out before drawing and never overlap each other or the boss bar / target frame (elites first, then
the nearer; `FrayHud.platesHidden` counts the ones left out).

## 8. The boss: the Gallows Knight (stage 1.5)

Branch `bestiary-boss`. Design: BESTIARY.md §8 (branch `design-arpg`), ROADMAP §3 milestone 1.5.

### 8.1 The encounter framework (`dev.voidmc.bestiary.boss`)

| | |
|---|---|
| Arena | `Arena` (saved with the boss): a cylinder (radius, a band below and above), the home spot and facing, an optional mound. The boss's perception only takes targets inside it (`Brain.leash`); it drops one that leaves or dies, walks home when out, and is put back home when 6+ blocks out, fallen below it or in a wall |
| Start | a fighter (a survival or adventure player, or a test bot) inside the arena whom it sees, who is within 6 blocks, or who hits it from inside |
| Reset | nobody in the arena for 60 s (`BossEncounter.resetTicks`; everyone left or died): full health, home, cooldowns and threat cleared, Fray's bar ended as RESET, `Boss.onReset` (the knight's cage back on its back, the rolling cage gone) |
| No cheese | Fray hits from anyone outside the arena are zeroed in the attacker stage; anything else from outside, and damage with no attacker (lava, falls, walls, cacti), is refused (`ALLOW_DAMAGE`); `/kill` still works |
| Multiplayer | toughness ×(1 + 0.6 per extra participant in the arena, the most seen this fight) × game difficulty's health factor: every hit's final amount is divided by it (`BossDamageMixin` on `hurtServer`). Vanilla caps max health at 1024, so the extra health is toughness; the bar's share is the same |
| Personal loot | each participant with 10 % of the damage or 30 s in the arena rolls `bestiary:boss/gallows_knight` for themselves: into the inventory of a player online, else dropped where the boss fell, pickable by them only (`ItemEntity.setTarget`), kept until picked up |
| Music | `BossMusic.CUE` (`start`, `phase_2`, `victory`, `reset`) for Gloam; a vanilla placeholder (a bell) until a listener handles it |
| Persistence | `BossData` (attachment): arena, fight on, phase, cage, participants (damage, ticks present), when the arena emptied, party size, Break meter, lair. A fight resumes after a relog, an unload or a restart (Fray's bar comes back without a banner) |
| Watchdog | a boss that wants to move and covers under a block in 5 s gets a fresh path and a hop; after 10 s it is put back home (`BossEncounter.unsticks`) |
| Lairs | `Lairs` (level saved data). Expanse has no lair anchors yet (CONTRACTS §18.2), so `/bestiary summon gallows_knight yard` builds the gallows yard (`GallowsYard`: trodden earth inside a broken fieldstone wall, a south gate, the mound and gallows, three broken pillars) and registers a lair; a felled lair boss returns after 3 in-game days (`Lairs.respawnDays`, 0 = once) when nobody is in its arena. `/bestiary summon gallows_knight` alone: an arena of 14 around you. A knight spawned any other way takes an arena of 14 where it stands |

Commands: `/bestiary summon gallows_knight [yard]`, `/bestiary boss info|reset <targets>`, `/bestiary boss phase <targets> <share>`.

### 8.2 The knight

Profile `data/bestiary/bestiary/creature/gallows_knight.json` (rank boss, tokens ignored, 900 health at D3, poise 60 into
the Break meter, undead), behaviour `knight` (`KnightMoves`), phase script `KnightScript`, entity `GallowsKnight`.

| Phase | Moves (tell, ticks from tell to hit) |
|---|---|
| 1 (100-55 %) | hook cut (white 8; a parry staggers it 26 ticks), two-cut string (white 8, white), slam (orange 14; the hook bites the ground and it hauls it free, its chest open: punish 28 ticks), shoulder charge (red 16, three blocks), chain sweep (orange 14, 120°), kick (white 7; a guard crusher, ×2 block stamina) |
| transition | tears the gibbet cage off its back (`tear_cage`, no hit, no tell): the cage falls and rolls away and stays as a hazard (`GibbetCage`: every 12-16 s it rattles, an orange tell of 20 ticks, and rolls at the nearest fighter 3+ blocks away: 7 blunt, guardable and dodgeable) |
| 2 (55-0 %) | three-cut string with one held beat before an overhead chop (white 7), chain lash (purple 16: it bites for 6 and drags you in, then follows with a cut), the Hanging (red ultimate, 24-tick tell: the mound ringed in red dust; on the drop everyone on it who didn't dodge takes 26), plus the hook cut, slam, charge, sweep and kick, with gaps ×0.65 and combos (cut → kick → slam, lash → cut) |

Rules: at most one red in any three moves (`BossMath.redAllowed`); no move but the Hanging above 18; the chain lash is also
its answer to a target it can't reach for 3 s (up a pillar, across a gap), in either phase. Damage numbers are the clips'
(light 10, heavy 18, ultimate 26, kick 8, lash 6), scaled only by game difficulty. Weak point: the chest (`hurts` during
the slam's, the lash's and the Hanging's haul): ×1.5 damage and poise and +4 % Break. The Break (Fray: poise, parries
+8 %, weak points) kneels it for its 60 Broken ticks (`gallows_knight/broken`; the HUD's "Broken · n s to punish").

Clips are basic, from key poses (`tools/knight/knight_clips.py` writes them; the user asked for basic animation): hook_cut,
two_cut, three_cut, slam, shoulder_charge, kick, chain_lash, the_hanging, tear_cage, broken; chain_sweep, stagger, idle and
walk are stage 1A's. The cage has its own little rig (`tools/knight/gibbet_cage.py`: the knight's cage cubes and paint),
rolling on its walk cycle; the knight's renderer hides the cage bone once it is off (`RigModel.HIDDEN`). Lore and the
codex entry are in the profile; the codex records what each player saw (`Codex.moveSeen`) and felled (each participant
who earned loot). Loot (placeholders until Arsenal): Gallows Chain ×2-4, a Sunder rune tablet (1 in 2), the Knight's
Crossguard (1 in 4), a lore page.

### 8.3 Results (2026-10-22)

Unit tests: `BossMathTest` (arena, reset, party toughness, loot eligibility, the red rule over 5000 random moves, the
watchdog), `CreatureDataTest` (every knight move meets the boss tell minimums, ultimates 20, phases of 4-8 moves, the
colours as designed, the slam's punish window, the kick's guard crush, the Hanging's 24-tick tell), Fray's
`GuardMathTest` (guard crush ×2) and `DamageMathTest` (segment distance): 100 pass.

Game tests: `bash tools/gametest.sh -Dfray.show.only=none -Dbestiary.boss=all` (report `fray-report-boss.txt`), all pass:
each of the nine moves lands on a mannequin after its tell (white 7-13, orange 14-16, red 19, purple 20, the Hanging 27
ticks); the charge covers 4 blocks; the lash pulls from 7 to 1.6 blocks; at 55 % the transition plays, the cage is off on
the client and lies in the arena, Fray's bar moves to phase 2, the transition hits no one, phase 2 draws on its own moves;
a full Break kneels it, 50 damage lands in the window with no answer, then it fights on; it resets after the fighter
leaves or dies (full health, home, bar ended) and nothing from outside or lava hurts it; a heavy takes 24.4 solo, 15.3
with two (×1.60) and 11.1 with three (×2.20); the two who earned it get their own drops, the third doesn't; the cage
rattles 20 ticks and runs over the player. Dedicated server (in process; the real player in survival with Resistance V;
a mannequin fighting at about 13 damage a second): a relog mid-fight keeps everything and the bar returns; a server
restart in phase 2 keeps health, phase, the torn-off cage, the hazard and the participants (a Break in progress is not
kept); the fight ends with the knight felled (94-112 s of fighting), never out of its arena, never unstuck, the player's
loot in their inventory. The motion showcase, `-Dfray.combat=all` and `-Dbestiary.ai=all` still pass (one full run,
16 min).

| | |
|---|---|
| Server cost | the encounter framework 13-16 µs a boss tick; the knight's AI 10-21 µs a tick |
| Network | the bar is Fray's (on change, at most 4 a second); the cage is one tracked entity |

Pictures (outside the repository): `C:/Users/bitto/VOID/.tools/share/bestiary/stage1/boss/` (each move from tell to hit,
the transition, the Break and punish, at half speed; the yard with the boss bar; the overview; the elite nameplates).

Open: Expanse lair anchors (the yard is the fallback); Arsenal's real parts for the loot; Gloam's music for the cues; a
real two-client fight (the tests use mannequin bots as the second player); the knight has no sword (the design's
"longsword cut" is a hook cut: the rig the user liked has the chain and hook only).
