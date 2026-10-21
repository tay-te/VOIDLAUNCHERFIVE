# Fray Motion: the stage 0 animation prototype

Our own animation system (no GeckoLib or similar), proven end to end on Minecraft 26.3 / Fabric / Java 25 before stage 1
builds on it. Design: `docs/arpg/ANIMATION.md` (branch `design-arpg`); this is its milestone M0 plus parts of M1-M3.
Everything below was measured by automated client game tests on this machine (RTX-class GPU, shared with other
sessions' game tests) on 2026-10-21.

| | |
|---|---|
| Code | `bestiary/` (mod `bestiary`) with `bestiary/fray/` (mod `fray`, the shared library, jar-in-jar inside Bestiary's jar: ROADMAP D1) |
| Run it | `bash tools/gametest.sh [-Psodium \| -Piris] -Dfray.show.tag=<tag> [-Dfray.show.only=zombie,blend,bb,authority,player,fp,gif,perf,dedicated,shadow] [-Dfray.perf.mobs=150]` (takes a game slot); `bash tools/duo.sh <tag> <lag ms>` (two clients, takes both slots) |
| Unit tests | `./gradlew :fray:test` (sampling, blending, torso, bake, Blockbench import) |
| Shader runs | `-Piris` needs a pack in `build/run/clientGameTest/shaderpacks` and `config/iris.properties` there (used: Complementary Reimagined r5.9.3 from the LOD runs; Welkin as `git archive welkin welkin/shaders`) |
| Bake | `./gradlew motionBake` (runs before `processResources`); report in `build/motion-report.txt` |
| Pictures | `docs/prototype/` (contact sheets and GIFs, built with `tools/sheet.py` and `tools/gif.py`) |

## 1. Verdict

| # | Proof asked for | Result |
|---|---|---|
| 1 | Clip format and loader; Blockbench converter | **Proven.** `.motion.json` → client clip + server timeline by one bake; a `.bbmodel` converter (`./gradlew bbImport`) whose output bakes and plays in game |
| 2 | Posing vanilla parts after setupAnim: zombie attack in 3 parts, idle, blending, 20+ mobs | **Proven.** 1.1-1.6 µs per animated zombie per frame (sampling + both applies), 0.17-0.20 ms per frame for 150 zombies; frame-time A/B at 24 zombies: +0.03 ms (Sodium), +0.014 ms (Welkin, GPU-bound, shadow pass included) |
| 3 | Player: dodge roll and heavy swing with the virtual torso; first-person arms through the hand renderer; armour posed | **Proven, with caveats on looks** (§4.3, §6): torso composition, root bone, armour, held items and first-person arms all work; first-person framing and the roll need art time |
| 4 | Server-side timeline drives the hit; clip hash at login | **Proven.** The hit lands once, in ticks 11-13 only, only inside the baked capsule, on an integrated and a dedicated server; a client whose claw differs by one keyframe is refused with a plain message |
| 5 | Iris + shader pack (incl. shadow pass) and Sodium alone | **Proven** with Iris 1.11.6 + Complementary Reimagined r5.9.3, Iris + **VOID Welkin** (branch `welkin` @ dd5dd5f9) and Sodium 0.9.2 alone: the shadow pass re-poses every model (counted) and shadows follow the pose; first-person arms are drawn once, in Iris's hand pass |
| 6 | Two clients in sync; 150 ms latency | **Proven** on a dedicated server with two client JVMs: same clip time within 0.6 ticks; with 150 ms injected on one client it trails by exactly the lag and still plays the whole tell; late join works (§5) |

## 2. How it works (as built)

```
 src/main/motion/<ns>/<path>.motion.json ──motionBake──► assets/<ns>/fray/clip/<path>.json      (client curves, canonical bytes)
   (or tools: bbImport from .bbmodel)                    data/<ns>/fray/timeline/<path>.json     (ticks, phases, events, hit capsules,
                                                                                                   SHA-256 of the client clip's bytes)
 SERVER  MotionServer.play(entity, clip) ─► Running (start tick, facing) ─► END_LEVEL_TICK: events, ACTIVE-tick capsule sweeps ─► hurtServer
         └─► fray:motion/play (entity, clip index, start tick, speed, layer) to trackers; START_TRACKING re-sends the running action
 CLIENT  Animator per entity: BASE layer (idle loop, client-side) + ACTION layer; time = level game time + partial tick
         LivingEntityRenderer.extractRenderState TAIL ─► sample into a PoseBuffer ─► render state data (Fabric RenderStateDataKey)
         after setupAnim in LivingEntityRenderer.submit (layers, held items) and in ModelFeatureRenderer.prepareModel (every drawn
         body and armour model, every pass) ─► HumanoidSolver writes the ModelPart pose fields
         root bone ─► PoseStack, right after vanilla's translate(0, -1.501, 0)
         first person ─► FirstPersonHandsAndItemsRenderer.submitHandsWithItems HEAD: Fray's posed arms + held item, vanilla's cancelled
```

- **One sampler, one solver.** `Channel`, `ClipSampler` and `HumanoidSolver` are common code. The client runs them every
  frame onto `ModelPart`s; the bake runs them every tick to place the locators that become hit capsules. The capsule the
  server sweeps is where the client draws the claw (at full blend weight, which the bake checks holds through ACTIVE).
- **Vanilla's conventions.** Clip numbers are what vanilla's `KeyframeAnimations.degreeVec / posVec / scaleVec` take;
  interpolation follows vanilla's `KeyframeAnimation` (the target key's interpolation, Catmull-Rom with clamped
  neighbours) plus `step`. Override layers: `lerp(under, rest + clip, weight × mask)`, folded into one buffer per entity.
- **Virtual torso.** Vanilla's arms and head aren't children of the body, so the torso rotation is composed onto body,
  head and both arms about the hips (`p' = R(p - hip) + hip`, `rot' = R·rot`). Unit-tested; visible in every swing.
- **Clips come from the mods' jars, not the resource manager.** A resource pack replacing a clip would change one side's
  timing and break the login hash; the client hashes exactly the bytes it parsed.
- **Login check.** Configuration phase: the server sends its Fray protocol and clip-set hash (SHA-256 over
  `id + clip hash` lines); the client answers with its own; any difference refuses the login.
- **26.3 facts verified for this** (javap and decompiled sources): `LivingEntityRenderer.extractRenderState(T,S,float)`,
  `submit(S,PoseStack,SubmitNodeCollector,CameraRenderState)` calling `isBodyVisible` right after the model translate and
  `EntityModel.setupAnim` before the layers; `ModelFeatureRenderer.prepareModel` calling `Model.setupAnim(state)` before
  every `renderToBuffer`; render states are created fresh per extraction (`EntityRenderer.createRenderState`);
  `GameRenderer.renderItemInHand` → `submitHandsWithItems` with its own submit storage and a hand render pass; the local
  player's hand arm is drawn as `submitModelPart` (a `Model.Simple`, pose read at draw time); `LevelExtractor` draws the
  local player only when it is the camera entity; Iris 1.11.6 re-extracts entities for its shadow pass
  (`ShadowRenderer.extractVisibleEntities` → `extractEntity`) and draws hands through vanilla's `submitHandsWithItems`
  twice (solid, then translucent, skipping the other pass's hands in `iris$skipTranslucentHands`); Sodium 0.9.2 has no
  mixin on `ModelFeatureRenderer` or the living-entity renderer
  (its `ModelPartMixin` / `CubeMixin` redraw the cubes from the pose fields we set).

## 3. The clips

| Clip | Ticks | Phases (bake) | Notes |
|---|---|---|---|
| `bestiary:zombie/idle` | 48, loop | none | BASE layer of every zombie, client-side, fades out as it walks; legs masked off |
| `bestiary:zombie/claw` | 26 | windup 0-10 (tell orange from 3), **active 11-13**, recovery 14-25 | torso twist, root lunge; `claw_r` capsule r 0.35, reach 1.33 blocks |
| `bestiary:zombie/taunt_slam` | 30 | windup 0-17, active 18-20, recovery 21-29 | **converted from a Blockbench project** (`src/main/blockbench/zombie_taunt.bbmodel`): instruction keyframes became the phases, locators became two capsules |
| `bestiary:player/heavy_swing` | 26 | active 11-13 | torso ±35°, item follows the hand; `fp` block with framing offset and first-person override curves for the right arm; `blade_r` capsule reach 1.63 |
| `bestiary:player/dodge_roll` | 12 | i-frames 1-7 | root rotation 360° about the hips, full tuck, client root motion 3.5 blocks (measured 3.58) |

## 4. What the tests showed

### 4.1 Zombie: claw, idle, blending (`docs/prototype/1_zombie_claw_strip.jpg`, `2_blend_and_blockbench.jpg`, `iris_claw_and_swing.gif`)

- The server's `motion/play` reaches the client and the claw plays over the idle; a bare zombie and one in iron armour with
  a sword pose identically (armour models are posed by the same hook; the sword follows `translateToHand`).
- **Blending**: a deterministic trace through the client's own `Animator` at 60 fps (idle → claw → idle → claw
  interrupted by the slam): the largest frame-to-frame change of the right arm at any blend edge is 18.9°, the same as
  the claw's own fastest frame. No pops. Live screenshots of blend-in, blend-out and a claw → slam crossfade.

### 4.2 Server authority (report lines `authority [...]`)

The same four trials on the integrated server and on Fabric's in-process **dedicated** server (`isDedicatedServer() ==
true`), a NoAI pig as the dummy:

| Dummy | Result (both servers) |
|---|---|
| 1.2 blocks in front | hit **once, at tick 13** (ACTIVE is 11-13), 4 damage (10 → 6 health) |
| 2.4 blocks in front | never hit |
| 1.2 blocks behind | never hit |
| 1.0 block to the side | never hit |

Server cost (warmed up, 150 zombies clawing in turn): **0.8 µs per running action per tick outside ACTIVE, 1.6-4.2 µs per
ACTIVE-tick sweep** (budget: 2 µs and 20 µs). The first sweeps after start cost ~200 µs (JIT warm-up).

### 4.3 The player (`3_player_swing_third_person.jpg`, `4_dodge_roll.jpg`, `5_first_person.jpg`, `iris_roll.gif`, `sodium_fp_swing.gif`)

- **Third person**: the local player in iron armour, heavy swing frozen through its phases in the F5 front and back views
  (vanilla draws the local player only as the camera entity, so arbitrary angles use a **Mannequin**, which is drawn with
  the same `PlayerModel` and plays the same clips, started by the server). Armour, helmet and sword follow the torso.
- **Dodge roll**: root bone about the hips plus a full tuck; the client applies the clip's root motion (3.58 blocks).
- **Prediction**: the local player's clip starts at the key press; the server's ack comes 2 ticks later on a local
  server (logged; re-basing is stage 1).
- **First person** (verified path: `GameRenderer.renderItemInHand` → `FirstPersonHandsAndItemsRenderer.submitHandsWithItems`):
  Fray cancels vanilla's hands during an action whose clip has an `fp` block and submits the player's own posed arms
  (an arm-rooted `Model` posed at draw time) and held item in the same hand pass. One clip serves both views; the clip's
  `fp.bones` curves replace the overhead windup in first person so the arm doesn't fill the screen. Exactly one Fray draw
  per frame with and without Iris.

### 4.4 Performance (report lines `perf ...`)

Direct probes (nanoTime around sampling at extract and each apply after setupAnim), uncapped frame rate:

| Setup | Per zombie per frame | 24 zombies | 150 zombies |
|---|---|---|---|
| vanilla renderer | 1.16-1.59 µs (sample 0.33-0.49 + 2 applies × 0.41-0.55) | 28-38 µs | 171-198 µs |
| Sodium | 1.21-1.36 µs | 29-33 µs | |
| Iris + Complementary | 2.6-5.7 µs (sampled twice, applied 4×: main and shadow pass) | 63-137 µs | (≈0.4-0.8 ms est.) |
| Iris + Welkin | 2.3 µs (same doubling) | 55 µs | |

Budget (ANIMATION.md §10): ≤ 5 µs per entity, ≤ 1.0 ms for 150 in view: met without shaders; with a shader pack the
shadow pass doubles the work. The cleanest A/B is the GPU-bound Welkin run (170 fps, 5.86 ms frames, steady): **+0.014 ms
per frame for 24 animated zombies including the shadow pass**. Other frame-time A/B (Fray on/off, 2 rounds of 8 s each): +0.03 ms at 24 zombies (Sodium,
0.58 → 0.62 ms median frame); at 150 the A/B swung ±0.6 ms between rounds because another session's game test was
running in the other slot, so only the probe numbers are reliable. No allocation in sampling; a few small objects per
frame for first person.

### 4.5 Shaders (`6_iris_complementary.jpg`, `8_iris_welkin.jpg`)

Iris 1.11.6 + Sodium 0.9.2 + Complementary Reimagined r5.9.3: posed zombies, the armoured player and the mannequin are lit
by the pack; every model is re-posed in the shadow pass (counted: e.g. 83,760 shadow-pass applies in an 8 s round with 24
zombies), and the shadows on the ground follow the pose (from above: a zombie frozen with both arms up beside one at
rest, then the slam vs the claw). First-person arms drawn once per frame inside Iris's hand pass (Fray follows Iris's
solid/translucent hand rule). The same with the user's own pack, VOID Welkin (the committed `welkin` branch, without
Gloam's bridge): posed zombie, armoured player, mannequin roll and first person all draw, lit and shadowed.

### 4.6 Login hash (`7_perf_hash_dedicated.jpg`)

A client whose claw differs by one keyframe (`-172` → `-150`): client set `93444e70` vs server `5ac2aebf`; refused at the
configuration phase with: *"Your VOID animations don't match this server's: clip set 5ac2aebf (5 clips) here, 93444e70
(5 clips) on your side. Install the same VOID Bestiary version as the server."* With the shipped clips the same client
joins.

## 5. Two clients

`bash tools/duo.sh sync 150` (both game slots): client A hosts Fabric's in-process dedicated server on port 25612 and
joins it; client B ("Bob") is a second client JVM. Marker files step them through the script; both trace, every client
tick, the wall clock and how far into the clip they are, and both take a screenshot at the same wall-clock moment
(within 30 ms; `docs/prototype/9_two_clients.jpg`). Round 2 holds every packet B receives for 150 ms (a handler before
`packet_handler` in B's netty pipeline: one-way, downlink only).

| Check | Result |
|---|---|
| B joins with a claw that differs by one keyframe | refused at login with the clip-set message (screenshot), then joins with the shipped clips |
| Late join | B arrived while the zombie was mid-claw: `START_TRACKING` sent the running claw with its real start tick (B got start 525 at its tick 530, five ticks in) |
| Server-started claw, no lag | both clients start at clip tick 0; at the same moments their clip times differ by 0.57 ticks (28 ms, spread ±0.6) |
| Same, B at +150 ms | B shows it 141 ms after A; at the same moments A is 2.85 ticks (143 ms) ahead: exactly the lag. B still starts at clip tick 0, so the tell is never cut: the clip's clock is B's late server clock, which keeps it in step with the zombie's movement as B sees it |
| B's predicted heavy swing, as A sees it | A and B within 1.1 ticks of each other, with and without lag (B's clock is late by the lag and the server starts the swing when B's request arrives: the two cancel) |
| Prediction vs server | B's ack: the server's start is 1 tick (no lag) and 4 ticks (150 ms) after B's predicted start, so at 150 ms the server's ACTIVE window trails B's screen by ~200 ms: the case COMBAT.md §10.3's declared-target rule exists for |
| Hits | the dedicated server's claw hit the dummy at tick 13 in both rounds |

Both clients ran on one machine and the uplink wasn't delayed; G-MP's 100/150/250 ms tables are stage 1 work.

## 6. What failed, what is risky

1. **Looks without elbows and knees** (ANIMATION.md risk 1). The claw and the swing read, but stiffly; the roll needs a
   hard tuck to stay near the ground and still clips for a frame at 180°. This needs the user's eye on the GIFs before
   60 clips get written.
2. **First person is real art work.** The third-person arms seen from the eye are huge and pass through the camera on an
   overhead windup; per-clip `fp` curves fixed the heavy swing, but every family needs framing (M3). Item lighting in the
   hand pass looks dark for the iron sword (vanilla's first-person sword looks the same at this time of day).
3. **The local player can't be filmed from outside** (vanilla only draws it as the camera entity): the studio needs the
   Mannequin, which works with the same rig and clips.
4. **Hit volumes assume full weight.** The bake places capsules on the rest pose plus the clip; on the client, bones the
   clip doesn't animate show vanilla's pose underneath. Rule for the bake: a clip's locator bones must be fully animated
   (rotation and position) through ACTIVE, and blend-in must end before ACTIVE (already a bake warning).
5. **Shader packs double the cost** (the shadow pass re-extracts every entity). Sampling should be cached per entity per
   frame and the LOD tiers applied to the shadow pass.
6. **Blockbench axis convention unverified.** The converter follows Blockbench's own "Modded Entity" export (rotation
   `(-x, -y, z)`, position `(-x, y, z)`); no Blockbench install here, so the sample project is hand-written. One real
   export must round-trip in stage 1 before artists rely on it.
7. **Euler blending.** Like vanilla, angles blend per component; a 360° roll needs a `step` key back to 0 so the
   blend-out doesn't unwind. The bake should warn on jumps > 150° (not built).
8. **Not built in the prototype**: additive layer, upper-body mask while moving, `item_r`/`item_l` and `camera` bones,
   client cues (sounds), LOD tiers, prediction re-basing and compress-never-skip, Motion Studio live reload, custom rigs.
   Allies: a creature never hits its own kind (placeholder for Bestiary's AI rules).
9. **Frame-time A/B on a shared machine is noise**; per-entity probes are the reliable gate (keep them in `frayPerf`).

## 7. Recommendations for stage 1

1. **Keep the architecture.** Pose from the render state, applied after both `setupAnim` call sites, root after
   `setupRotations`, first person inside `submitHandsWithItems`: all verified with vanilla, Sodium and Iris, all in one
   package (`fray.client.mixin`: five small injections plus a frame-time probe) as ANIMATION.md §13 risk 8 wants.
2. **Build M1 on this code**: split `fray-api`; add the additive layer, masks while moving, item bones, cues; cache the
   sample per entity per frame (Iris's shadow pass); move the per-model rig binding from a `WeakHashMap` to a per-class
   cache; LOD tiers.
3. **Build the Motion Studio next, before the clip set.** The pieces exist: frozen-frame strips (`Animator.frozenElapsed`),
   the armour-stand camera, the Mannequin for player clips, `tools/gif.py` and `tools/sheet.py`. Authoring speed is now
   the bottleneck, not the engine.
4. **Bake validation** from §6.4 and §6.7, plus reach limits per move.
5. **Latency**: implement re-basing on the ack and compress-never-skip for late tells; run the duo at 100/150/250 ms as
   part of G-MP.
6. **Blockbench**: verify one real export, then write the small Fray plugin (marker palette, rig check).
7. **Ask the user for a GIF review now** (`docs/prototype/*.gif`), especially the roll and first person.
