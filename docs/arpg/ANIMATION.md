# Animation: our own system (Fray Motion)

The user's decision (2026-10-21): **our own animation system**, no GeckoLib or other libraries, for creatures and for
the player's first- and third-person actions. It must work on dedicated servers (server-authoritative timing and
hitboxes, client-side visuals), in multiplayer, and with Iris, Sodium and Welkin.

It lives in **Fray** (`dev.voidmc.fray.motion`), built and maintained by the Bestiary lane; the clip library and the
authoring tools are Bestiary's (CONTRACTS.md §1).

---

## 1. Goals and limits

1. **Gameplay lives on the server.** When a swing hits, where a claw is, when a boss's weak point is exposed: all
   decided by the server from baked timelines. Clients only draw.
2. **Built on Minecraft's own model parts.** We pose vanilla `ModelPart`s; we never draw cubes ourselves. That is
   what keeps Sodium's fast path, Iris's shader programs, armour, skins and held items working.
3. **One source of truth per clip.** The client's curves and the server's timeline are baked from the same file by
   the same code; a hash checked at login proves they match.
4. **Cheap.** Sampled once per frame per visible entity, a few microseconds each; nothing per tick on the server
   outside an action's active ticks.
5. **Authorable by artists and by agents.** Blockbench for people; a plain JSON source and a live studio for agents.

## 2. What 26.3 gives us (verified against the 26.3 jars, 2026-10-21)

| Fact | Where | What it means for us |
|---|---|---|
| Entity models are trees of `ModelPart` with public pose fields `x y z xRot yRot zRot xScale yScale zScale`, applied as a translate, then `Quaternionf.rotationZYX(zRot, yRot, xRot)`, then scale | `ModelPart.translateAndRotate` | clips store Euler angles; we write the same fields vanilla does |
| Parts are found by name (`createPartLookup`), and poses can be stored, reset and loaded (`storePose`, `resetPose`, `loadPose`, `getInitialPose`) | `ModelPart` | a rig is a list of bone names mapped onto parts |
| Vanilla has a keyframe system: `AnimationDefinition(lengthInSeconds, looping, bone → channels)`, channels target POSITION, ROTATION, SCALE; interpolations LINEAR and CATMULLROM; `Keyframe(timestamp, preTarget, postTarget, interpolation)` (pre/post gives steps); `KeyframeAnimation.apply(millis, weight)` **adds** offsets onto the current pose (`offsetPos`, `offsetRotation`, `offsetScale`) | `net.minecraft.client.animation` | our clip format is a superset; vanilla's additive semantics are ours; vanilla's own clips (warden, creaking, breeze...) can be imported |
| `AnimationState` (a start tick and a running flag) is common code | `world.entity.AnimationState` | the "started at tick T" idea exists on both sides; we use our own richer state |
| Models are posed from a **render state**: renderers fill it in `extractRenderState(entity, state, partialTick)`; `LivingEntityRenderer.submit` calls `model.setupAnim(state)` for its layers, and `ModelFeatureRenderer.prepareModel` calls `setupAnim(state)` again for each submitted model (the body, each armour piece) when drawing | `LivingEntityRenderer`, `feature.ModelFeatureRenderer` | a pose must be a **pure function of the render state**: sample once during extract, copy onto parts in `setupAnim` |
| Fabric adds `FabricRenderState.setData / getData(RenderStateDataKey)` to every render state | Fabric rendering v1 | our pose buffer rides in the render state without a mixin per state class |
| The player model: `PlayerModel extends HumanoidModel<AvatarRenderState>`; parts `head` (child `hat`), `body` (child `jacket`), `right_arm` / `left_arm` (children sleeves), `right_leg` / `left_leg` (children pants); **no elbows or knees; arms and head are siblings of the body, not children** | `PlayerModel.createMesh`, `HumanoidModel` | a virtual **torso** bone (§4.1); player clips can't bend limbs |
| Armour is drawn by `HumanoidArmorLayer` with HumanoidModels posed from the same render state | layers | posing in `setupAnim` reaches armour for free |
| Held items follow `translateToHand` on the parent model | `ItemInHandLayer` | item locators follow the arms |
| First person: `FirstPersonHandsAndItemsRenderer.submitHandsWithItems(...)` with a `FirstPersonHandsAndItemsRenderState`. **Iris mixes into this class** (`MixinItemInHandRenderer` targets it) and has its own hand pathway | client renderer; Iris 1.11.6+26.3 jar | our first-person body must be drawn inside that method so Iris keeps its hand pass |
| **Sodium** 0.9.2 for 26.3 replaces how `ModelPart` cubes are drawn (`ModelPartMixin`, its own `ModelCuboid` writer) but still reads the pose fields | Sodium jar | never draw cubes ourselves; set poses only |
| The client's animation and model classes **don't exist on the dedicated server** | server jar (`net.minecraft.client.animation`, `ModelPart` absent) | the server reads baked timelines, never bones |
| Multipart hitboxes exist only for the Ender Dragon (`EnderDragonPart`, `Level.dragonParts()` hard-coded) | `Level`, `ServerLevel` | bosses use Fray **hurt volumes**, not part entities |
| Items carry `attack_animation` / `interact_animation` (`SwingAnimation`: NONE, WHACK, STAB, duration); there is a `ClientboundSwingAnimationPacket` | data components, protocol | vanilla's swing stays the fallback when no Fray clip plays |
| The `Mannequin` entity (an `Avatar`, drawn with the player model) | `world.entity.decoration.Mannequin` | a test dummy for player clips in the studio |
| Geometry builders `LayerDefinition`, `MeshDefinition`, `PartDefinition`, `CubeListBuilder`; Fabric `ModelLayerRegistry` | `client.model.geom.builders`; Fabric | custom rigs for new creatures load from baked geometry |
| Vanilla mobs mostly use `GoalSelector`; piglins, hoglins, wardens, breezes, creakings, villagers and others use `Brain` | entity sources | relevant to BESTIARY.md; animation doesn't care |

## 3. Architecture

```
 AUTHORING                        BAKE (Gradle task motionBake)                 FILES
 Blockbench .bbmodel ───┐                                         ┌─► assets/<ns>/fray/rig/<rig>.json
  geometry, animations, ├──► Fray's own sampler evaluates every ──┼─► assets/<ns>/fray/clip/<rig>/<clip>.json   (client curves)
  instruction markers,  │    clip at every tick                   └─► data/<ns>/fray/timeline/<rig>/<clip>.json  (server)
  locators              │                                              + clipset hash for the login handshake
 .motion.json (agents) ─┘

 SERVER                                                     CLIENT
 ActionService.start ─► TimelineRunner (ticks) ─► events    Animator per entity
   │                     │  hit volumes swept on ACTIVE       layers: base → locomotion → action → additive → procedural
   │                     └─► damage pipeline                 sample at extractRenderState (partial tick)
   └─ fray:motion/play ──────────────────────────────────►   PoseBuffer in the render state (RenderStateDataKey)
      (clip, start tick, speed, layer)                        applied after setupAnim → ModelParts → Sodium/Iris draw
```

## 4. Rigs

A **rig** names bones and says which model part each one drives, plus virtual bones and locators.

### 4.1 The humanoid rig `fray:humanoid`

Used by players, zombies, husks, drowned, skeletons and their kin (strays, bogged, parched, wither skeletons),
piglins and brutes, zombified piglins, Hearth folk (its `FolkModel` is a `HumanoidModel`) and any modded humanoid
whose model extends `HumanoidModel`.

| Bone | Drives | Notes |
|---|---|---|
| `root` | the whole body, through the PoseStack | applied after `LivingEntityRenderer.setupRotations` (a small mixin); dodge rolls, falls, kneels, lunges |
| `torso` | virtual: rotates `body`, and carries `head`, both arms around the hip pivot (0, 12, 0) px | vanilla arms aren't children of the body, so Fray composes them: position = R × (p − hip) + hip |
| `head` | `head` (+ hat) | look-at is a procedural layer on top |
| `body` | `body` (+ jacket) | |
| `right_arm`, `left_arm` | the arms (+ sleeves) | |
| `right_leg`, `left_leg` | the legs (+ pants) | |
| `item_r`, `item_l` | an offset after `translateToHand` | weapon twirls, a dagger reversed, a shield angled |
| `camera` | first-person view kick (pitch, yaw, roll ≤ 3°) | only the local player in first person |
| locators `tip_r`, `tip_l` | weapon tip per family (sword 1.1 blocks from the hand, spear 2.0...) | **bake only**: become hit volumes |

Limits: no elbows or knees on vanilla bodies. Good player animation here means big readable arcs, torso twist,
root motion and item motion (what Minecraft combat mods that look good already do). Bending limbs would mean
replacing the player's geometry and breaking skins and other mods: not planned (§13, risk 1).

### 4.2 Vanilla non-humanoid rigs

Each vanilla model's part names become a rig file, e.g. `fray:spider` (`head`, `body0`, `body1`, `right_hind_leg`,
`right_middle_hind_leg`, `right_middle_front_leg`, `right_front_leg` and the left four: verified in `SpiderModel`),
`fray:wolf`, `fray:creeper`, `fray:quadruped`, `fray:illager`, `fray:slime`. Stage 1 needs humanoid, spider, wolf,
creeper (ROADMAP).

### 4.3 Custom rigs (new creatures and bosses)

Any hierarchy, real elbows, knees, jaws, tails and capes; up to 48 bones (budget §10). The bake writes the geometry as
a rig file; Fray's generic `RigModel` (an `EntityModel` built from it at resource load and registered with Fabric's
`ModelLayerRegistry`) draws it with vanilla render types. Textures are ordinary entity textures (the Expanse
generator style: models and textures made together so they can't drift).

## 5. Data formats

### 5.1 Client clip (`assets/<ns>/fray/clip/<rig>/<clip>.json`)

```json
{
  "format": 1,
  "rig": "fray:humanoid",
  "length": 1.30,
  "loop": "none",
  "blend_in": 3, "blend_out": 4,
  "mask": { "head": 0.4, "torso": 1, "body": 1, "right_arm": 1, "left_arm": 1, "right_leg": 0.3, "left_leg": 0.3 },
  "bones": {
    "right_arm": {
      "rotation": [[0.00, [-10, 0, 5], "catmullrom"], [0.55, [-160, 15, 0], "catmullrom"], [0.65, [40, 0, -10], "linear"]],
      "position": [[0.00, [0, 0, 0], "linear"]]
    },
    "torso": { "rotation": [[0.00, [0, 0, 0], "catmullrom"], [0.55, [0, -35, 0], "catmullrom"], [0.65, [10, 30, 0], "step"]] }
  },
  "fp": { "framing": "bestiary:sword_1h", "camera": { "rotation": [[0.60, [1.5, 0, 0], "linear"], [0.80, [0, 0, 0], "catmullrom"]] } },
  "cues": [[0.60, "trail_on", "tip_r"], [0.70, "trail_off", "tip_r"], [0.58, "sound", "bestiary:swing.heavy"]],
  "hash": "9c1e..."
}
```

- Angles in degrees in Blockbench's convention, positions in pixels (1/16 block) with Y up (converted as vanilla's
  `KeyframeAnimations.posVec` does), scale as a factor.
- Interpolations: `linear`, `catmullrom` (vanilla's two) and `step` (vanilla's pre/post targets).
- `cues` are client-only (sounds, trails, particles). Gameplay events are in the timeline.

### 5.2 Server timeline (`data/<ns>/fray/timeline/<rig>/<clip>.json`)

```json
{
  "format": 1,
  "clip": "bestiary:zombie/claw_heavy",
  "ticks": 26,
  "phases": { "windup": [0, 11], "active": [12, 14], "recovery": [15, 25] },
  "tell": { "colour": "orange", "from": 4 },
  "events": [[12, "hit_start", "claw_r"], [14, "hit_end", "claw_r"], [9, "sound_cue", "bestiary:zombie.growl"]],
  "hit_volumes": {
    "claw_r": [[12, [0.35, 1.30, 0.55], [0.55, 1.05, 1.35], 0.35],
               [13, [0.10, 1.10, 0.75], [0.05, 0.80, 1.55], 0.35],
               [14, [-0.25, 0.90, 0.70], [-0.45, 0.60, 1.30], 0.35]]
  },
  "hurt_volumes": {},
  "root_motion": [[12, 0.20], [13, 0.15]],
  "hyper_armour": [0, 14],
  "cancel": { "chain": [15, 25], "dodge": [17, 25] }
}
```

- Capsules (a → b, radius) in entity-local space (blocks, facing +Z), one entry per tick, baked by sampling the
  locator bones through the same code the client uses.
- **Phases come from the clip's markers** (`windup_end`, `active_end`), so gameplay and animation can't disagree.
  Action data (`fray:action`) adds costs, flags and damage, and refers to the timeline.
- `hurt_volumes` (bosses): where weak points are on every tick of the clip (a head, a glowing joint, an exposed
  heart during a roar).
- Scaled at runtime by the entity's `scale` attribute (baby zombies, giants, elites drawn 1.15× bigger).

### 5.3 Rig file (`assets/<ns>/fray/rig/<rig>.json`)

Bones (name, parent, pivot, part path or virtual), locators (name, parent bone, offset), default masks per layer,
and for custom rigs the geometry (cubes, UVs, texture size). Server copies of the locator and pivot data live in
`data/<ns>/fray/rig/` for the bake's checks; the server never needs geometry.

## 6. Tooling

| Tool | What it is |
|---|---|
| **Blockbench** (free, the standard Minecraft modelling tool) | geometry, animations in Animate mode. Gameplay markers are **instruction keyframes** on the animation's effects track (`windup_end`, `active_end`, `hit_start claw_r`, `hit_end claw_r`, `tell orange`). Hit and weapon-tip points are **locators** (`hv_claw_r@0.35` = a 0.35-block capsule end). A "Fray" Blockbench plugin (small JS, ours) adds a marker palette and a rig-name check |
| **`.motion.json`** | the same clip format plus markers and locators, written by hand or by agents; helpers: mirror left/right, pose presets (`guard_high`, `guard_low`, `idle_ready`), easing shortcuts |
| **`motionBake`** (Gradle task in `bestiary/fray-tools`) | reads `.bbmodel` and `.motion.json`, validates, writes rig, clip and timeline files, the clipset hash, and a report: phase ticks, reach of each hit volume, a PNG of each volume's path from above and the side |
| **Motion Studio** | the Welkin-studio pattern: a persistent dev client and server with a control port; `/motion play <entity> <clip>`, live reload when a file changes, `snap` (a strip of frames at chosen ticks from three cameras), `gif`, a debug overlay of hit and hurt volumes, phases and the tell colour, first and third person side by side, a Mannequin for player clips. Edit, reload, snap in seconds. It takes the machine-wide game-test lock like every Minecraft run |
| **Validation tests** | every clip bakes; every action names an existing timeline; phases present and ordered; no hit volume reaches beyond the move's declared reach + 0.5 block; client and server lengths equal; hash stable across two bakes |

**Who authors what.** The Bestiary lane owns the library and the tools. Arsenal and Alembic request clips for their
techniques and casts (`bestiary/docs/requests/arsenal.md`) or ship their own in their namespace through the same bake.
Agents author with `.motion.json` and judge by studio strips and GIFs; the user sees GIFs at milestones.

## 7. Client runtime

### 7.1 The animator

One `Animator` per animated entity on the client (created on the first play, dropped when the entity is removed).
Its **layers**, bottom to top:

| Layer | What | Weight rule |
|---|---|---|
| 0 · Vanilla | whatever the model's own `setupAnim` produced (walk cycle, look, vanilla swing) | always under everything |
| 1 · Locomotion | idle, walk, run, strafe, turn clips blended by the render state's walk speed (custom rigs; optional for vanilla ones) | crossfades by speed |
| 2 · Action | the action's clip; full body when standing, **upper body only** (by the clip's mask) when the entity moves, so legs keep walking | blend in / out per clip (2–6 ticks) |
| 3 · Additive | hit reactions (light, heavy, from the hit's direction), flinch, breathing, bow recoil | summed, each fades by itself |
| 4 · Procedural | head look-at toward the target (clamped), lean into turns, bow-arm pitch following the aim, torso twist toward a lock-on target | computed, no clips |

### 7.2 Blending

- **Override layers** (locomotion, action) mix toward their clip: for each masked bone,
  `pose = lerp(under, rest + clip, weight × mask[bone])`.
- **Additive layers** add `clip × weight`, which is exactly vanilla's `KeyframeAnimation.apply(millis, weight)`
  behaviour.
- Euler angles blend per component (as vanilla does); the bake warns when two keys differ by more than 150° on an
  axis so authors avoid flips.

### 7.3 Sampling and applying

1. **Sample** at the end of `LivingEntityRenderer.extractRenderState` (a mixin) for entities with an active animator:
   time = the client's estimate of server ticks + partial tick; keyframe cursors are cached per channel, so a sample
   is a few lerps, not a search.
2. Write a pooled **PoseBuffer** (`float[bones × 9]` plus the root transform) into the render state with a
   `RenderStateDataKey`.
3. **Apply** right after each `setupAnim` call: after the one in `LivingEntityRenderer.submit` (for layers that read
   part positions, like held items) and after the one in `ModelFeatureRenderer.prepareModel` (each submitted body
   and armour model), only when the state carries a PoseBuffer whose rig matches the model. A model's rig is looked up
   once per model class and cached.
4. **Root** is applied to the PoseStack after `setupRotations`.

Because the pose depends only on the render state, Iris's shadow pass and any repeated `setupAnim` call give the same
pose for free.

### 7.4 First person

When the local player in first person runs an action whose clip has an `fp` block, Fray draws the player's own posed
arms and held items from the camera, **inside `FirstPersonHandsAndItemsRenderer.submitHandsWithItems`** (a mixin), so
Iris treats them as the hand pass. One clip serves both views:

- a **framing** transform per weapon family (authored once: where the arms sit in view for a one-handed sword, a
  greatsword, a bow, a staff) places the third-person arms in front of the camera;
- optional per-clip `fp` curves override the framing where a clip needs it (a bow's anchor at the cheek);
- the `camera` channel adds small kicks on impacts and releases (≤ 3°, scaled by the screen-shake option);
- outside actions (eating, building, idle) vanilla's hands draw exactly as today.

### 7.5 Items, trails and effects

Held items follow `translateToHand` plus the `item_r` / `item_l` channels. **Weapon trails** sample the tip locator
each frame into an 8-point ring and draw a ribbon with a vanilla translucent emissive entity render type, through the
same submit collector entities use; Iris sees an ordinary translucent entity draw. Particles are vanilla particles.
No custom render pipelines (Iris wouldn't know them: no shadows, wrong lighting).

## 8. Network sync

| Case | How |
|---|---|
| A creature starts an action | the server sends `fray:motion/play` (entity, clip index, layer, **server start tick**, speed) to everyone tracking it |
| A client gets it late (latency) | it computes how far in it is; it **never skips a tell**: the part of the windup already gone is played at up to 1.5× speed so the whole tell shape is seen, then catches up |
| The local player acts | the client starts the clip at the press (prediction) and sends `fray:action/request`; the ack carries the server start tick; the client re-bases its clock smoothly (a snap with a 2-tick blend only if off by more than 3 ticks); a refusal blends back to idle in 3 ticks |
| Other players' actions | like creatures: the server broadcasts what it accepted |
| Late join or coming into range | `EntityTrackingEvents.START_TRACKING` sends the running action with its start tick |
| Interrupts (stagger, death) | `fray:motion/stop` plus the reaction clip |
| Locomotion, look, lean | never sent: derived on the client from the entity's movement and look |
| Clock | client server-tick estimate from the game time packets plus local ticks, smoothed |

One packet per action; a 30-creature fight is a few KB a second per player (CONTRACTS.md §15).

## 9. Server runtime

- **TimelineRunner** per running action: a tick counter, the timeline, the entity's facing at start (creatures keep
  their facing through an attack unless the move says `track`, with a turn-rate cap).
- **Events** fire on their tick: `hit_start` / `hit_end`, `tell` (Bestiary's AI and HUD cues), `spawn_projectile`,
  `root_motion`, `sound_cue` (the sound itself plays on clients from the clip; the server event is for AI hearing),
  `vulnerable` (opens a boss's weak point).
- **Hit volumes**: on each ACTIVE tick, the capsule from the timeline is placed in the world (entity position, start
  facing, scale) and swept from the previous tick's capsule with three sub-samples (20 ticks a second is coarse for a
  claw); entities in the swept bounds are tested, hit-once applied, then line of sight from the attacker's eye, then
  the damage pipeline.
- **Hurt volumes**: when anything hits a boss, the server checks the hit against the boss's current hurt volumes to
  mark a weak-point hit.
- **Root motion**: creatures are moved by the server (vanilla movement sync carries it; the client strips root
  translation from creature clips so it doesn't move twice). The local player's root motion is applied by the client
  (players move client-side in Minecraft) and checked by the server.
- **Determinism**: timelines are integer ticks with no randomness; choices happen in the AI before an action starts.
- **Dedicated server**: nothing here touches a client class. The timeline loader is in common code.

## 10. Performance budgets

| Where | Budget | How |
|---|---|---|
| Client sampling | ≤ 5 µs per entity per frame (12-bone humanoid, all layers) | cached keyframe cursors, no allocation, pooled buffers |
| Client total | ≤ 1.0 ms a frame with 150 animated creatures in view (the Hub perf machine, RTX 4060) on both 26.3 render backends (OpenGL and Vulkan, as the Hub's perf gates run) | the LOD tiers below; only visible entities are extracted at all |
| LOD tiers | under 24 blocks: everything; 24–48: action and locomotion, every other frame, no additive or procedural; 48–96: action layer only at 10 Hz; beyond 96: vanilla pose only | distance from the camera |
| Memory | clips shared; ≤ 512 bytes per animator; a pose buffer of 6–48 bones | |
| Server | ≤ 2 µs per running action per tick outside ACTIVE; ≤ 20 µs on an ACTIVE tick with sweeps; 200 creatures in combat ≤ 2 ms of the 50 ms tick for actions (AI is budgeted separately, BESTIARY.md §2) | bounds-first queries |
| Network | one packet per action, about 12 bytes | |

## 11. Compatibility

| With | Plan |
|---|---|
| **Sodium** | we only set pose fields; Sodium's `ModelPartMixin` draws the cubes as it always does |
| **Iris** + **Welkin** (and any pack) | creatures and trails use vanilla entity render types, so Iris's entity programs, shadows and `entity.properties` mapping apply; first-person arms are drawn inside the hand renderer Iris hooks; the shadow pass re-poses from the same render state |
| **Welkin** | its `entity.properties` can give Bestiary creatures their own ids (wet hide, bone, ember glow underground); a request to the Welkin lane when stage 5 content lands |
| Vanilla and modded player models | poses apply by part name to `HumanoidModel`s; a mod that swaps the player model for another class (Figura avatars, Entity Model Features replacements) makes the rig lookup fail: Fray skips the pose (logged once) and that player shows vanilla swings; gameplay is unchanged because the server decides everything |
| Slim (Alex) arms | same rig, same clips |
| Emote and animation mods on players (Player Animator family, Emotecraft) | Fray writes poses only while an action plays; outside actions they work as before; Better Combat and Epic Fight are not supported (CONTRACTS.md §17) |
| **Reel** (VOID's cinematic mod) and replay tools | all animation follows packets with server start ticks, so a replay of packets replays the fights; a note to the Reel lane |
| Dedicated server | §9; covered by the stage 1 gate |
| Vanilla clients | refused at login (CONTRACTS.md §16) |

## 12. Staged plan

| Milestone | Contents | Exit criteria |
|---|---|---|
| **M0 · Spike** | one hand-written clip on a zombie and on the player in third person, started by the server; the apply hooks; root transform | runs on a dedicated server with two clients; looks right with Sodium + Iris + Welkin; frame-cost probe numbers |
| **M1 · Runtime** | animator, layers, masks, blending, pose buffers; humanoid, spider, wolf, creeper rigs; motion play/stop with late join; server timelines, hit volumes, hurt volumes; unit tests for sampling and blending | game test: a zombie's claw hits a dummy only inside its baked volume; a late-joining client sees the running action |
| **M2 · Tools** | Blockbench plugin and bake, `.motion.json`, validation tests, Motion Studio, the stage 1 clip set (about 60 clips) | edit, reload, snap under 10 s; every stage 1 clip bakes and validates |
| **M3 · First person** | framing per family, camera channel, fp curves | first-person strips for every family; Iris hand pass correct with Welkin and Complementary |
| **M4 · Performance and compatibility** | LOD tiers, perf gates on both backends, the compatibility matrix, Reel check | `frayPerf` gate green: 150 animated creatures ≤ +1.0 ms a frame |
| **M5 · Custom rigs** | `RigModel` from baked geometry; the first new creature and the stage 1 boss with hurt volumes | the Gallows Knight fights through both phases on a dedicated server |
| **M6 · Polish** | trails, look-at and lean, the additive reaction library, procedural recoil | user review of GIFs |

## 13. Risks

| # | Risk | Mitigation |
|---|---|---|
| 1 | The player body has no elbows or knees, so actions can look stiff | torso bone, big arcs, root and item motion, camera kicks; judge by GIFs early (M0); bending limbs is out of scope |
| 2 | **Clip count**: about 160 player clips and 10–30 per creature, 600+ over the roadmap | reuse across humanoids (zombie, skeleton and folk share a library), mirroring, procedural layers, agent authoring with the studio, and the roadmap's stage order (only what a stage needs) |
| 3 | Latency makes remote tells late | tells of 8+ ticks for heavies, compress-never-skip (§8), the defender's grace (COMBAT.md §10.3) |
| 4 | Mixins on `LivingEntityRenderer`, `HumanoidModel` and the hand renderer collide with other mods | small mixins at method tails, tested with Entity Model Features, Figura and Emotecraft; failures fall back to vanilla visuals |
| 5 | Iris or a pack treats our draws differently (hand pass, shadows) | vanilla render types only; the compatibility matrix in every stage gate |
| 6 | Server cost of sweeps in big fights | sweeps only on ACTIVE ticks, bounds first, per-attacker caps |
| 7 | Client clips and server timelines drift | one bake, one sampler, the hash in the login handshake |
| 8 | Minecraft's render code keeps moving (extract and submit are recent) | every render hook in one small `fray.client.compat` package, with a game test per hook |
