# Stage 1A: the animation foundation

Branch `bestiary-anim` (from `bestiary`). Builds on the stage 0 prototype (`PROTOTYPE.md`). Measured on this machine
on 2026-10-21 with client game tests and the Motion Studio. The clip set here is the **review set** for the user's
sign-off before the full ~60 clips; nothing in it is final.

## What was built

| Area | What | Where |
|---|---|---|
| Contract with the combat lane | clip ids, timeline windows and events, the `MotionHits` handler, `ActionGate`, defender-favoured timing | `docs/ANIM_EVENTS.md` (committed first) |
| Motion Studio | persistent client with a control port and live reload (re-bake in memory in ~100-150 ms), freeze and scrub, strips, GIF export, onion skin, camera presets, mannequin/zombie/knight/player subjects, first/third-person split, hit-volume overlay, walk driver, Iris pack switching, perf | `docs/STUDIO.md`, `tools/studio.sh`, `src/gametest/.../studio` |
| Custom rigs | rig files (vanilla's model definition as data), `RigSolver`, `RigModel` / `RigEntityRenderer` (vanilla parts and render types: Sodium and Iris shadow pass work), per-bone hit and hurt volumes in server timelines, rig hash in the login check | `docs/RIGS.md` |
| Blockbench | `bbImport` rewritten to match Blockbench 5.1.2's own exporter (geometry, helpers, step and bezier keys, locators); **verified against real exports made by the installed Blockbench** (30 bones, 48 boxes, 795 keyframe samples equal) | `tools/blockbench/bb.mjs`, `BlockbenchExportTest` |
| The Gallows Knight | 27 bones with elbows, knees, pelvis/spine/chest, neck, a three-link chain and hook, two-piece cape, skirts, gibbet cage; generated model + painted texture, re-saved by Blockbench; idle, walk (synced), telegraphed chain sweep (orange, hook and chain volumes, chest weak point while it pulls the chain back), stagger; placeholder AI | `tools/knight/gallows_knight.py`, `src/main/blockbench/rig/bestiary/` |
| Runtime | LOCO layer (walk loops synced to the walk cycle), ADD layer (additive reactions), move masks (upper body while walking), `item_r`/`item_l` bones (held items turn about the grip), first-person camera channels (dips, kicks), first person draws the main arm only and ignores the virtual torso, per-frame pose cache (Iris's shadow pass no longer samples twice) | `fray/src/client/...` |
| Bake checks | `ground` (lowest point of the body; `"ground": "plant"` re-keys the root so feet never sink or float), `fp_framing` (first-person screen coverage, limit 45 %), Euler jumps; `mirror_of` and `same_as` sources | `bake/Checks.java`, `bake/MotionBake.java` |
| Defender-favoured timing | hold-and-rewind on the server, bounded (RTT + 1 tick, cap 250 ms; PvP 100 ms), anti-abuse clamps, RTT probe (median of 5) | `DefenderTiming`, `MotionServer` |

## The review set

The user signed the set off on 2026-10-21. The GIFs (35 MB) now live outside the repository, in
`C:/Users/bitto/VOID/.tools/share/bestiary/stage1/` (they stay in this branch's history up to the sign-off); the contact sheets and stills stay in
`docs/stage1/`. Every GIF shows **Complementary Reimagined r5.9.3 and VOID Welkin side by side**, one frame per tick (real time), labelled
with the tick. `*_fp.gif` show first person beside third person. `sheet_player.jpg`, `sheet_zombie.jpg`,
`sheet_knight.jpg` are contact sheets (six frames per clip).

| Group | Clips |
|---|---|
| Dodges (the roll redone) | `player_dodge_left` (a crouched side-dash with a shoulder dip, planted on the ground), `player_dodge_right` (mirrored), `player_dodge_forward` (a low dash, no somersault), `player_backstep` (a hop back with the guard up); first person: camera dip and roll, vanilla hands |
| Sword | `player_sword_light_1/2/3` (the three-cut string), `player_sword_heavy` (anticipation, hold, diagonal cut, follow-through, lunge), `player_sword_charge_hold` / `charge_release`, `player_sword_plunge` (D16); all with first person |
| Guard and reactions | `player_parry`, `player_block_raise` / `block_hold` / `block_hit` (additive), `player_hit_light` / `hit_heavy` (additive), `player_flinch` |
| Zombie | `zombie_idle`, `zombie_idle_b`, `zombie_walk`, `zombie_walk_b` (variants picked per zombie), `zombie_claw` (re-polished, white tell), `zombie_claw_left`, `zombie_double_claw`, `zombie_lunge_grab` (purple), `zombie_stagger`, `zombie_hit_light` |
| Gallows Knight | `gallows_knight_idle`, `gallows_knight_walk`, `gallows_knight_chain_sweep`, `gallows_knight_stagger` |
| Studio | `studio_onion.jpg` (onion skin of the sweep), `studio_split.jpg` (first/third person), `studio_overlay.jpg` (hit volumes) |

Ground: every dodge, attack and the knight's clips stay on the floor (bake check `ground`, < 0.5 px). First person:
the sword clips cover at most 21 % of the screen (heavy, light_3), nothing reaches the camera.

## Performance (client probes, per mob per frame; 24 mobs unless noted)

| Setup | Zombie (vanilla model, 11 bones) | Gallows Knight (custom rig, 30 bones) | 150 zombies, Fray per frame | A/B frame time, 150 zombies |
|---|---|---|---|---|
| vanilla renderer | 1.24 µs (sample 0.42 + 2 applies × 0.41) | 1.47 µs (sample 1.25 + apply 0.22) | 0.18 ms | +0.25 ms |
| Sodium | 1.28 µs | 1.46 µs | | |
| Iris + Complementary | 2.30 µs (shadow pass: cached sample, extra applies) | 1.88 µs | 0.31 ms | +0.46 ms |
| Iris + VOID Welkin | 2.31 µs | 1.95 µs | 0.33 ms | +0.03 ms (GPU-bound) |

Budget (ANIMATION.md §10): ≤ 5 µs per entity, ≤ 1.0 ms for 150: met everywhere. The per-frame cache cut the shader-pack
cost from 2.8 to 2.3 µs per zombie. Server: 0.55-1.8 µs per running action per tick outside sweeps, 0.7-3.7 µs per
ACTIVE-tick sweep (budgets 2 and 20 µs; one 20 µs reading was JIT warm-up). Frame-time A/B at 24 mobs: +0.04 ms
(zombies) and +0.07 ms (knights) on the vanilla renderer, within noise with shader packs.

## Defender-favoured timing

Unit table (`DefenderTimingTest`): presses 1-7 ticks before the hit on the defender's screen dodge it (1-3 are
perfect), at 0, 150 and 250 ms alike. Game test (`-Dfray.show.only=latency`, a zombie's claw against the local player,
a two-way latency injector on the client's own connection, root motion off so only timing can save the player):

| Round trip | In time (pressed at claw tick 9 on screen) | Late (tick 13) | Control: in time, no grace |
|---|---|---|---|
| 0 ms | dodged (perfect) | hit | – |
| 150 ms | dodged (perfect, resolved by the late request) | hit | hit |
| 250 ms | dodged (perfect, resolved by the late request) | hit | hit |

The probe reads about 50 ms above the network round trip (the echo waits for the client tick), so at 250 ms the
cap (5 ticks) is what bounds the rewind; a press 2+ ticks early still counts. Stats per player (`DefenderTiming.stats`)
count rewinds, clamps and saves.

## Known issues and next steps

1. Fixed on `bestiary-stage1`: first person has its own rig and curves per move, starting and ending on vanilla's held
   sword, checked against third person by the bake (`fp_match`, STUDIO.md "First person"); sword clips interpolate
   rotations on quaternions and have no wrist roll.
2. Player hit volumes ride the arm, not `item_r` (the combat lane sweeps arcs for players, so this only affects
   creature clips that use item bones; none do yet).
3. `zombie/double_claw` sweeps both claws in both strikes (hit-once per strike); per-strike volume lists would be tidier.
4. Root motion for creatures stays visual (the server moves them); the dodge travel is the local player's.
5. The knight's AI is a placeholder (approach, sweep, 3 s cooldown) until the Bestiary AI lane's boss framework.
6. Done after sign-off: the review GIFs moved to `C:/Users/bitto/VOID/.tools/share/bestiary/stage1/` (see above).
