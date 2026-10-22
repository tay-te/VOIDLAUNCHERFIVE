# Animation contract: clip names, timeline events, defender timing

The agreement between the animation lane (branch `bestiary-anim`: Fray Motion runtime, rigs, tools, clips) and the
combat lane (branch `bestiary-combat`: damage pipeline, stamina, poise, statuses, threat, the player actions' server
logic, the HUD). Written first so both lanes can code against it; changes go through this file.

**Ownership.** Animation owns `dev.voidmc.fray.motion.**`, `dev.voidmc.fray.client` (runtime, studio, first person),
`bestiary/tools/**`, `src/main/motion/**`, `src/main/blockbench/**`, `src/main/rig/**` and the creature *rendering*
(`dev.voidmc.bestiary.anim.**`). Combat owns `dev.voidmc.fray.api.**`, `dev.voidmc.fray.combat.**` (or wherever it
puts them), the HUD, and what an action *does* (costs, damage, validation). Shared entrypoints (`Fray.java`,
`FrayClient.java`, `Bestiary.java`, `fabric.mod.json`) get one-line additions only.

Timing numbers below are COMBAT.md's; when the user signs off the stage 1 clip set, lengths are final. Until then a
clip may change its curves, never its id, phases or window ticks without an edit here.

---

## 1. Clip ids

All clips are `bestiary:<path>`. Rig `fray:humanoid` unless noted. `W/A/R` = windup / active / recovery ticks
(ACTIVE = the ticks hits land). "Window" columns are timeline windows (§2), inclusive tick ranges from the clip start.

### 1.1 Player (`fray:humanoid`; the Mannequin plays the same clips)

| Clip | Ticks | Phases W/A/R | Windows | Use |
|---|---|---|---|---|
| `player/dodge_forward` | 12 | – | `iframes` 1-7, `perfect` 1-3, `recovery` 8-11, `roll_attack` 9-11 | dodge with a forward or no-diagonal input |
| `player/dodge_left`, `player/dodge_right` | 12 | – | same as forward | dodge sideways (crouched side-dash) |
| `player/backstep` | 8 | – | `iframes` 1-4, `perfect` 1-2, `recovery` 5-7 | dodge with no direction |
| `player/sword/light_1`, `light_2` | 12 | 4/2/6 | `chain` 6-11, `dodge_cancel` 8-11 | sword string, cuts 1 and 2 |
| `player/sword/light_3` | 18 | 6/3/9 | `chain` 9-17, `dodge_cancel` 12-17 | finisher (×1.3) |
| `player/sword/heavy` | 27 | 12/3/12 | `hyper_armour` 0-14, `chain` 15-26, `dodge_cancel` 18-26, `feint` 0-9 | heavy on release after a 6+ tick hold |
| `player/sword/charge_hold` | 20, loop `hold` | – | – | held attack past 16 ticks; played until release (server stops it) |
| `player/sword/charge_release` | 24 | 6/3/15 | `hyper_armour` 0-8 | the charged strike (damage from the hold time, combat side) |
| `player/sword/plunge` | 16 | 4/3/9 | `landing` 4-6 | light while falling 1.5+ blocks (D16); start it so tick 4 is the ground contact |
| `player/parry` | 14 | – | `parry` 0-4, `whiff` 5-13 | weapon parry (shield parry: `player/shield/parry`, `parry` 0-5, later) |
| `player/block_raise` | 4, loop `hold` | – | `block` 3 | raises the guard; then `block_hold` |
| `player/block_hold` | 20, loop | – | `block` all | held guard (upper body only while walking) |
| `player/block_hit` | 8, additive | – | – | a hit taken on the guard (combat side plays it) |
| `player/hit_light`, `player/hit_heavy` | 8 / 14, additive | – | – | hit reactions, from the front (directional variants later) |
| `player/flinch` | 6 | – | – | poise broken: the action is interrupted (COMBAT.md §9) |

Prototype ids that stay as aliases until stage 1 ships: `player/heavy_swing` = `player/sword/heavy`,
`player/dodge_roll` = `player/dodge_forward`.

### 1.2 Zombie (`fray:humanoid`)

| Clip | Ticks | W/A/R | Tell | Windows | Notes |
|---|---|---|---|---|---|
| `zombie/idle`, `zombie/idle_b` | loops | – | – | – | client-side BASE layer; the variant is picked per entity |
| `zombie/walk`, `zombie/walk_b` | loops | – | – | – | client-side LOCOMOTION layer (arms and torso; vanilla legs underneath), synced to the walk cycle |
| `zombie/claw` | 26 | 10/3/13 | white from 3 | – | right claw |
| `zombie/claw_left` | 26 | 10/3/13 | white from 3 | – | left claw (mirror) |
| `zombie/double_claw` | 34 | 9/2, 7/2, 14 | white from 3 | – | W W string (D3): two ACTIVE windows, `hit_start`/`hit_end` per strike |
| `zombie/lunge_grab` | 36 | 14/4/18 | purple from 2 | `grab` 14-17 | the grab (D4); a caught player is held by the combat side |
| `zombie/stagger` | 20 | – | – | `punish` 2-16 | poise broken |
| `zombie/hit_light` | 8, additive | – | – | – | flinch without interrupting |

### 1.3 The Gallows Knight (rig `bestiary:gallows_knight`)

| Clip | Ticks | W/A/R | Tell | Notes |
|---|---|---|---|---|
| `gallows_knight/idle` | loop | – | – | BASE layer |
| `gallows_knight/walk` | loop | – | – | LOCOMOTION layer, synced to the walk cycle |
| `gallows_knight/chain_sweep` | 44 | 18/4/22 | orange from 4 | the gibbet-chain sweep (120°, BESTIARY.md §8.3); hit volumes `hook` and `chain`; hurt volume `chest` (weak point) while the chain is drawn back |
| `gallows_knight/stagger` | 30 | – | – | Break/stagger reaction; `punish` 4-26 |

Phase 2 moves (chain lash, the Hanging, cage tear-off) come after the user signs off the rig.

## 2. Timeline data the combat side reads

`MotionServer.timeline(Identifier)` returns the baked `Timeline` (common code, dedicated-server safe):

| Field / method | Meaning |
|---|---|
| `ticks` | clip length in ticks |
| `activeStart`, `activeEnd`, `isActive(k)`, `phase(k)` | the first ACTIVE window (`activeEnd` exclusive) |
| `strikes()` | every ACTIVE window as `[start, endExclusive]` pairs (multi-strike clips such as `double_claw`) |
| `window(name)` | a named window as `[from, to]` inclusive, or null: `iframes`, `perfect`, `parry`, `whiff`, `block`, `chain`, `dodge_cancel`, `hyper_armour`, `feint`, `grab`, `recovery`, `roll_attack` |
| `tellFrom`, `tellColour` | `white`, `orange`, `red`, `purple` (`TellColour.byId`) |
| `events` | `[tick, name, arg]` in tick order: `tell <colour>`, `hit_start <volume>`, `hit_end <volume>`, `window_open <name>`, `window_close <name>`, `sound_cue <id>`, `vulnerable <volume>`, plus any authored marker |
| `volumes`, `hurtVolumes` | capsules per tick (entity-local blocks, +Z forward) |

`MotionServer.running(entity)` gives the running action (`tick(now)` = ticks since its start, the timeline, and for
players `defenderStart`, below). `MotionServer.hurtVolumesNow(entity)` gives the weak points exposed right now (world
capsules), for the weak-point test. `MotionServer.play(entity, clip, speed)` / `stop(entity, blendTicks)` start and stop
clips; `MotionServer.EVENTS` is a test log. Events fire through `MotionServer.TIMELINE_EVENT` (server, per running
action and tick) so the combat side can react to `tell`, `hit_start`, `window_open` without polling.
`MotionServer.setActionGate((player, clip, running) -> boolean)` lets the combat side refuse a player's request
(stamina, Winded, the item in hand); a refusal acks "refused" and the client's predicted clip blends out in 3 ticks.

### 2.1 How combat reads it (merged)

| Combat | From the clip |
|---|---|
| `ActionTiming.of(kind, family, def, clip, timeline, speed)` | an action playing its own clip (sword light_1/2/3, heavy, charge_release, plunge, the dodges) takes `phases` from `strikes()`, and `chain`, `dodge_cancel`, `feint`, `hyper_armour`, `roll_attack`, `recovery` from `window(name)`; a borrowed clip (other families, Execute, guard counter, roll attack, Arts) keeps the moveset's phases and plays retimed so its strike lands on ACTIVE. Mettle's action speed scales the clip. The client predicts with the same function. |
| The arc sweep | runs on the ticks `ActionTiming.active(k)` (the clip's ACTIVE window); the player's own clip volumes stay ignored |
| `ActionGate` | set by combat: a raw `ActionRequest` (not a combat input) is refused while a combat action runs or the player can't act, and pays the dodge's / parry's / light's stamina (free in creative: the studio, tests) |
| Pose clips | guard: `player/parry` on the press, `block_raise` after the parry window, then `block_hold`; `player/sword/charge_hold` from tick 16 of a held attack; `flinch` on a flinch or stagger; additive `hit_light`/`hit_heavy`/`block_hit` and creatures' `<type>/hit_light` through `MotionServer.overlay`; a creature's `<type>/stagger` on Stagger |

## 3. Hits from timelines

A creature's ACTIVE-tick capsule that touches a living entity becomes a `MotionHit` (attacker, target, clip, volume,
tick, the defender's tick, the tell colour). Fray Motion never applies damage itself once a handler is registered:

```java
MotionHits.setHandler((MotionHit hit, DefenseWindow window) -> { /* the pipeline: damage, stagger, stamina */ });
```

`window` is what the defender's own action covered at the moment the hit landed **on the defender's screen**:
`NONE`, `IFRAMES`, `PERFECT` (first i-frame ticks: a Slip), `PARRY`, `BLOCK`. The combat side maps it to
`GuardOutcome` (orange and red rules, stamina, Winded) and applies damage. Without a handler Fray applies plain
`hurtServer` damage for `NONE` and nothing otherwise (the prototype's behaviour, for tests).

## 4. Defender-favoured timing (the user's decision, 2026-10-21)

A dodge or parry that was in time **on the defending player's screen** counts.

- The client stamps every action request with its own game time (`ActionRequest.clientTick`). The client's clock runs
  behind the server's by the downlink latency, and a creature's clip on that client is drawn at `clientTime - start`,
  so the stamp is directly "the creature's clip tick the defender was looking at when they pressed".
- Start player actions with `MotionServer.startPlayerAction(player, clip, clientTick)` (it returns the `Running` or
  null). It runs the clip on the server from receipt as before (other clients see it from then) and records
  `defenderStart` = the stamp, clamped by the rules below. Defensive windows (`iframes`, `perfect`, `parry`, `block`)
  are evaluated against `defenderStart`; nothing else is.
- A creature hit on a player is **held** (not applied) for up to the player's grace: `min(measured RTT + 1 tick,
  5 ticks)` (250 ms). A request arriving in that time whose window covers the hit's tick cancels or parries it; when
  the hold expires the hit resolves with whatever covered it (usually `NONE`). A hit that a window already covers
  resolves at once.

Anti-abuse limits (all server-side, `DefenderTiming`):

| Rule | Value |
|---|---|
| Local players | a measured round trip under 60 ms (`DefenderTiming.LOCAL_RTT_MS`: the probe reads up to a tick above the network) holds nothing and rewinds nothing |
| Rewind cap | 5 ticks (250 ms), server config `fray.defender_grace_ms` (0 turns it off; PvP uses `min(cap, 100 ms)`) |
| Claimed lag | `receipt - stamp` may not exceed the measured RTT + 1 tick; older stamps are clamped to that |
| Future stamps | clamped to the receipt tick |
| Monotonic | a stamp earlier than the player's previous action's stamp is clamped to it |
| One window, one hit set | a dodge or parry covers only hits whose tick falls in its own window |
| RTT measure | Fray's own probe (`fray:motion/clock`, once a second to every player): the median of the last 5 round trips, so one slow echo can't widen the window. The echo is answered on the client's main thread, so it reads about one tick (50 ms) above the network's round trip |
| Logged | each rewound outcome is counted per player (`DefenderTiming.stats`) for server owners |

**On the merged branch (`bestiary-stage1`) this is the only judge.** Combat's `PlayerCombat.window(at)` asks
`MotionServer.windowFor(player, at)`, i.e. `DefenderTiming.windowAt` over the clip the player is running, for every hit on
a player (timeline hits, Fray hits, vanilla hits); stamps are `DefenderTiming.accept`, the hold for Fray and vanilla hits
is `DefenderTiming.graceTicks`. Combat's own numbers widen windows per run through overrides
(`MotionServer.startAccepted(player, clip, stamp, speed, windows)`): Mettle's extra i-frames (`iframes` → `[1, 7 + n]`), the
press's parry window (`parry` → `[0, window - 1]`: Mettle's bonus, halved when spammed, none when Winded). Without the
clips in the jar combat falls back to its own `iFrom/iTo` and parry press.

Tests: `DefenderTimingTest` (JUnit, the clock maths at 0/150/250 ms RTT, cheats clamped) and the game test
`-Dfray.show.only=latency` (a zombie's claw against the local player with 0, 150 and 250 ms injected round-trip
latency: a dodge pressed in time on screen is never hit; a late one is).
