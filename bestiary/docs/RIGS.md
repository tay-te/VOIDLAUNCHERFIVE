# Custom rigs (VOID's own creatures)

The user's decision (2026-10-21): VOID's own creatures (the Gallows Knight, the revenant soldier, future enemies) get
custom rigs **with extra joints** made from our own geometry; vanilla mobs and the player keep Minecraft's shape (vanilla
parts plus the virtual torso, `fray:humanoid`).

## The pipeline

```
 Blockbench project (modded entity, box UV)          motionBake                       game
 src/main/blockbench/rig/<ns>/<name>.bbmodel  ──►  assets/<ns>/fray/rig/<name>.json  ──►  RigModel (vanilla ModelParts,
   + <name>.fray.json (shadow, model scale)        assets/<ns>/textures/entity/<name>.png    vanilla render types)
                                                   (rig hash goes into every clip's entry     drawn by RigEntityRenderer
 src/main/motion/<ns>/<name>/*.motion.json  ──►    of the login clip-set hash)                (LivingEntityRenderer)
                                                   client clips + server timelines
                                                   (hit/hurt capsules per bone and tick)
```

- **Rig file** (`RigJson`): bones in parent-first order with `offset` (PartPose offset, pixels, relative to the parent),
  `rotation` (degrees) and cubes `[x, y, z, dx, dy, dz, u, v, inflate, mirror]`: vanilla's own model definition as data,
  number for number what Blockbench's Java export writes. Locators named `hv_<volume>_a@<radius>` / `hv_<volume>_b` in a
  group become capsules on that bone; clips name them in `hits` (hit volumes) or `hurts` (weak points, with a tick
  window).
- **Clips for a custom rig** name the rig (`"rig": "bestiary:gallows_knight"`) and its bones. There is no vanilla pose
  underneath: a bone is `lerp(rest, rest + clip, weight)` plus additive layers (`RigSolver`). Cube-rotation helper bones
  (`<cube>_r1`) can't be animated. `"ground": "plant"` keeps the lowest point on the floor every tick.
- **Locomotion**: `ClientMotion.registerBase(type, idle...)` and `registerLocomotion(type, walk...)` (variants picked per
  entity); walk loops with `sync_walk` play one cycle per that many units of vanilla's walk position, so feet match the
  ground speed.
- **Server**: the bake places every locator through the same solver the client draws with (`Posed`, `RigSolver.forward`
  matches `ModelPart.translateAndRotate`), one capsule per tick, scaled by the entity's `scale`. `MotionServer` sweeps
  them on ACTIVE ticks; `hurtVolumesNow(entity)` gives the exposed weak points.
- **Rendering**: `RigModel` is an `EntityModel` built from the rig at renderer creation (`LayerDefinition.create(...)
  .bakeRoot()`), posed in `setupAnim` from the render state's pose buffer, drawn with `entityCutout`. Sodium draws its
  cubes, Iris shades it and re-poses it in the shadow pass from the same render state (verified with Complementary and
  Welkin, `gallows_knight_*.gif` in `C:/Users/bitto/VOID/.tools/share/bestiary/stage1/`, outside the repository). The studio's reload rebuilds the model and the texture live.

## Blockbench: verified against real exports

Blockbench 5.1.2 is installed on this machine. `tools/blockbench/bb.mjs` drives it over the Chrome DevTools Protocol
(its own user-data folder; nothing of the user's settings is touched):

```bash
node tools/blockbench/bb.mjs roundtrip <in.bbmodel> <out dir>   # Blockbench's own save, Java entity export, animation export, viewport pictures
node tools/blockbench/bb.mjs eval "<js>"                        # anything else in Blockbench's API
```

The committed rig source `src/main/blockbench/rig/bestiary/gallows_knight.bbmodel` is **the file Blockbench itself
saved**, and `fray/src/test/resources/blockbench/gallows_knight.java` / `gallows_knight_animations.java` are what
Blockbench's "Export Java Entity" and "Export Animations" wrote for it. `BlockbenchExportTest` checks that `bbImport`
produces the same thing as Blockbench's exporter, every number in the same order:

| Checked | Result |
|---|---|
| Bones: name, order, parent | 30 of 30 (27 groups + 3 cube-rotation helpers `cage_cage_r1`, `r/l_upper_arm_pauldron_r1`) |
| Pivots and rest rotations | all equal (offset ±0.001 px, rotation ±0.0002 rad: the exporter's rounding) |
| Boxes: origin, size, UV, mirror, inflate | 48 of 48 |
| Keyframes (rotation, position; linear, catmull-rom, a step key; nested bones) | 4 channels, 795 samples equal (±0.001) |

The conversion it confirms (Blockbench → vanilla model space), from Blockbench 5.1.2's `modded_entity.js`:

| | Blockbench (Y up, feet at 0, absolute) | vanilla |
|---|---|---|
| bone offset | origin − parent origin | `(-x, -y, z)`, `+24` on Y for top-level bones (the project's "flip Y", on by default) |
| bone rotation | `(rx, ry, rz)` degrees | `(-rx, -ry, rz)` |
| box | `from`, `to` in the group | `addBox(origin.x - to.x, -from.y - size.y + origin.y, from.z - origin.z, size)` |
| rotated cube | own origin and rotation | a helper bone `<cube>_r1` at the cube's origin (cubes sharing a rotation share one) |
| rotation keyframe | `(x, y, z)` | `degreeVec(-x, -y, z)` |
| position keyframe | `(x, y, z)` | `posVec(-x, y, z)` |
| step key | holds until the next key | a STEP key on the next key (the export adds a key 1 ms before it) |
| bezier key | Blockbench's curve | sampled into linear keys every 1/40 s (Blockbench's own Java export writes it as linear) |
| pre/post data points | two values at one time | two keys 1 ms apart |

`docs/stage1/rig/` has Blockbench's own viewport pictures of the knight (front, side, three-quarter) and the knight in
game (`ingame_front.jpg`).

## Making a creature

1. Model it in Blockbench as a *Modded Entity* with box UV, one texture, group names in snake_case (`r_forearm`).
   Put hit and weak-point locators (`hv_hook_a@0.45`, `hv_hook_b`) inside the bone they ride. Or generate it, as
   `tools/knight/gallows_knight.py` does (model and texture together), then let Blockbench re-save it with
   `bb.mjs roundtrip`.
2. Save it as `src/main/blockbench/rig/<ns>/<name>.bbmodel` (+ `<name>.fray.json` for shadow and scale).
3. Write clips in `src/main/motion/<ns>/<name>/` with `"rig": "<ns>:<name>"` (or animate in Blockbench and
   `./gradlew bbImport -Pbb=<file> -PbbRig=<ns>:<name>`).
4. Register the entity and `EntityRendererRegistry.register(type, ctx -> new RigEntityRenderer<>(ctx, rigId))`, plus
   `ClientMotion.registerBase` / `registerLocomotion` for its loops.
5. Look at it in the studio (`subject` needs a line in `MotionStudio.spawn` for a new kind).

Budgets (ANIMATION.md §4.3, §10): up to 48 bones; the knight's 30 bones cost 1.5 µs per frame without shaders, 1.9 µs
with Iris (the shadow pass reuses the frame's sample).

## Player elbows (2026-10-22)

The player's arms (and every humanoid model's: armour, mannequins; zombies never bend theirs) have elbows. `fray:humanoid`
gains `right_forearm` / `left_forearm`: a forearm's rotation is relative to its arm, hinged at the vanilla arm box's
middle (`HumanoidRig.ELBOW_*`, model px), the hand 6 px below it. On the client `Elbows` cuts each arm part's faces at
that plane when the model is built (UVs interpolated, so any skin, slim or wide, sleeve layer, armour, trim and glint
maps exactly) into the vanilla part (upper half) plus a `fray_forearm` child, and the sleeve likewise. While an elbow
is straight the arm draws vanilla's own boxes (the halves are swapped in only while it bends), so models without a
clip, and other mods' poses, are vanilla. The upper half carries a 1.5 px skirt on its back and sides, 0.15 px inside
the surface, that fills the joint on a forward bend. Held items ride the forearm (`ItemInHandLayer`), vanilla's
first-person hand straightens it. The rig check (`MotionShowcase` part `rig`, `tools/review/rig_compare.py`) compares
rest poses pixel by pixel against a build without elbows; `-Dfray.noElbows=true` turns them off for measurements.
