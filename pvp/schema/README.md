# `schema/` — the contracts

Four JSON Schema (draft-07) files. **`mods.json` and the derived half of `loadout.json` are
generated** — see "Adding a mod" below and `docs/adding-a-mod.md`. They are the **only** thing the six parallel owners in
[`../CONTRACTS.md`](../CONTRACTS.md) share. Nobody edits another owner's code; a
cross-directory need is expressed by reading a schema here.

| File | Defines | Written by | Read by |
|---|---|---|---|
| `mods.json` | **Generated** (`build.mjs`). The closed registry of every mod VOID ships (the 12 of §3, the VOID watermark, and the readouts added since): id, `kind`, `category` (§3), `hypixel_safe` (§11), the panel `label`, defaults, and a settings sub-schema per mod | `core` | everyone |
| `loadout.json` | The loadout model (§8): mod state + anchor-based HUD layout + stats | `core` | everyone |
| `protocol.json` | Every Rust ⇄ Java WS message (§7), a `oneOf` on `t` — 6 Java→Rust, 3 Rust→Java | `core` | `core`, `mod` |
| `bridge.json` | The `window.void` surface (§6.5): 9 events Java→JS, 8 calls JS→Java | `core` | `mod`, `ingame`, `ui` |

`mods.json` is the root: `loadout.json` `$ref`s its mod ids and settings, and both
`protocol.json` and `bridge.json` `$ref` `loadout.json`. `bridge.json` also `$ref`s
`protocol.json#/definitions/global_settings`, so the `settings` channel and the wire
message carry one type rather than two. **A mod is added in exactly one
place.** Refs are absolute (`https://schema.void.dev/pvp/<file>.json#/definitions/...`)
so any resolver works as long as all four documents are registered; the URL is an
identifier, not a location, and nothing fetches it.

## Adding a mod

**Write `mods/<id>.json`, add the id to `ORDER` in `build.mjs`, and run it.** A mod used to be
spelled out in nine mechanical places — seven in this file, two in `loadout.json` — and all
nine were silent when wrong: a mod missing from `hud_mod_id` is not a schema error, it is a mod
that cannot be placed on the HUD, and you find out in game.

```sh
cd schema
node build.mjs           # regenerate mods.json, patch loadout.json
node build.mjs --check   # the CI gate: is the committed output still its sources?
```

`mods.json` stays **generated and committed**, because it is the contract: `void-loadout`
`include_str!`s it, and `@void/protocol` and `ModRegistry.java` are both generated from it by
Node scripts that Gradle, Cargo and Vite cannot run themselves. So "generated" has to also mean
"checked" — hence the three `--check` gates.

`ModRegistry.java` was hand-transcribed until Wave 0 (`scripts/gen-java-registry.mjs`), and this
paragraph went on saying so for three roster passes afterwards. It cost real time: an agent
reading it concluded that two mods added to `schema/mods/` needed transcribing into Java by
hand, and went looking for a person to do it, when the answer was to run the generator. **A
stale sentence about how a file is maintained is as expensive as a stale table** — the file was
correct, and the documentation sent someone to edit it anyway.

A `kind: hud` mod must also carry a `default_placement` (`anchor` + `dx`/`dy`, and an optional
`note` saying why those numbers) — that is the factory HUD layout, and both the Java registry's
copy and `@void/protocol`'s are generated from it. `build.mjs` refuses a HUD mod without one and
a gameplay mod with one, and so does the emitted schema.

`mods/_shared.json` holds the properties every mod of a `kind` carries. A property added there
reaches every HUD mod at once, which is the point — `docs/mod-roster.md` §9's advice was
to settle the shared HUD property set once rather than retrofit it into twenty settings pages.
(It said "all eight" until this was written and there were sixteen; a count in prose is a
count somebody has to remember to edit, which is the argument `validate.mjs` already makes
about the count it stopped hard-coding.)

`mods/_base.json` holds the static half of the schema and the registry `version`, which is
bumped by hand: "did this change break a stored loadout" is not something a diff can answer.

## Validate

```sh
cd schema
npm i --no-save ajv
node validate.mjs        # compiles all four, checks every `examples` entry, plus cross-checks
```

`validate.mjs` also asserts the things JSON Schema cannot: that the registry contains
exactly the ids in the `mod_id` enum and no others (a count it derives rather than one
written here, which is why this sentence no longer names a number), that `hud_mod_id`/`gameplay_mod_id` agree with
each entry's `kind`, that every entry carries a `category` from the enum and that each
`<id>_entry` narrows it to the same value, that labels are unique, that every registry
`defaults` object satisfies its own mod's settings sub-schema, and that `init.loadouts`
and the `loadouts` bridge event carry the same thing. It belongs in CI.

## How each side consumes them

### Rust — `crates/void-bridge`, `crates/void-loadout`

Generate serde types at build time (`typify`/`schemars` from a `build.rs`), or hand-write
`#[derive(Serialize, Deserialize)]` structs and keep `validate.mjs` in CI as the guard.
Either way:

- `protocol.json` `#/definitions/java_to_rust` is what the WS server validates on the way
  in; `#/definitions/rust_to_java` is what it emits. The `t` field is the serde tag —
  `#[serde(tag = "t", rename_all = "snake_case")]`.
- **Unknown `t` and unknown fields are ignored, never an error** (§7). Do not use
  `#[serde(deny_unknown_fields)]` on protocol types; the schema sets
  `additionalProperties: true` for the same reason.
- `v` appears on `hello` and `init` only, `const 2`. A mismatch is fatal: the launcher
  refuses to launch and prompts for an update (§7).
- `loadout.json` is `void-loadout`'s on-disk format. Its `state_patch` paths
  (`mods.<mod>.<setting>`) are the diff unit.
- `global_patch` is the same idea for `settings.json`, carried by `globals`, and its keys
  are bare `global_settings` property names rather than dotted paths — globals are flat, so
  there is nothing to path into. Apply it with `GlobalSettings::apply_patch`, which merges
  through JSON so that `extra` survives: `global_settings` is `additionalProperties: true`,
  the mod's own class is five fixed fields, and a whole-object echo from the game would
  erase every global the mod does not model. That is the entire reason the message is a
  delta.

### Java — `mod/src/main/java/dev/voidpvp/client/{net,bridge}`

(`dev.void.*` is not a legal Java package — `void` is a keyword. See CONTRACTS.md.)

- `net/` speaks `protocol.json`. It sends `hello` with the token from `-Dvoid.token` and
  waits for `init` before applying anything; **the mod keeps no config files of its own**
  (§6.1), so `init` is the entire world of state it starts from.
- If Rust is unreachable, in-memory state persists for the session and is flushed on
  reconnect (§6.1) — the same `state` and `hud` messages, replayed.
- `bridge/` implements `bridge.json`. Java is authoritative for live state: a call
  returns the value actually applied, so there is no ack and no optimistic UI (§6.5).
  The one exception is `openKeybindCapture`, whose synchronous `returns: null` means
  *armed*: the captured key follows as a call-result envelope on the `__emit` channel.
  Null therefore arrives on both channels meaning different things — tell them apart by
  channel, never by value.
- Payloads in `bridge.json` are `additionalProperties: false` on purpose. The mod JAR
  embeds the UI bundle, so the two ship as one binary and the surface does not need to be
  forward-compatible — unlike `protocol.json`, which crosses a process boundary between
  independently updatable halves.

### TypeScript — `packages/protocol`, then `packages/ui` and `packages/ingame`

`packages/protocol` is generated, not written: run `json-schema-to-typescript` over all
four files and export the result. `ui` and `ingame` import types from `@void/protocol`
and never re-declare them.

The envelopes in `bridge.json` (`{e, payload}`, `{c, params}`, `{c, returns}`) do **not**
exist at runtime — the real bridge hands `payload` straight to the `void.on` handler and
`params` are positional arguments. The envelope exists so the surface is expressible as
one validatable schema, and because it is exactly the recording format the browser
`?debug` harness of §9 replays against a fake `window.void`.

## Conventions

- Every property carries a `description`. If you add one without, the review bounces it.
- `keybind` values are LWJGL 2 key names in upper case (`RSHIFT`, `C`, `MOUSE3`, `NONE`) —
  what MC 1.8.9's `Keyboard.getKeyName` produces. `openKeybindCapture` returns one.
- HUD placement is always `anchor` + `dx`/`dy` + `scale`, never absolute pixels (§8.1).
- Colours are `#RRGGBB` or `#RRGGBBAA`.
- Durations that cross a boundary are milliseconds, never ticks.
- Bump `protocol.json`'s `v` on any breaking change to a message; bump `mods.json`'s
  `version` when a mod is added, removed or reclassified. They are independent.
- Append every change to the changelog below, with the reason, not just the diff.

---

## Contract changes

Newest first. Each entry says what moved, why, and what had to change to follow it.

### 2026-09-09 (later) — `hex_color_rgb`, and narrowing is migration-shaped

`mods.json` registry `version` unchanged; `protocol.json` `v` unchanged.

**`hit_color.color` moves from `$ref: hex_color` to a new `$ref: hex_color_rgb`.** Its own
description already promised six digits and said the alpha byte belongs to `intensity` — which is
defined as a fraction of vanilla's own hurt-overlay alpha, and is the ceiling that classifies the
mod `safe` rather than `grey`. The promise was not enforced anywhere: `hex_color` accepts
`#RRGGBBAA` because `crosshair.color` genuinely ships it, and **draft-07 ignores keywords sitting
beside a `$ref`**, so writing a `pattern` next to one validates nothing while reading as though it
does. Two owners of one value is the shape `toggle_sneak` was split out of `toggle_sprint` to
remove, and it had a visible symptom: the mod page would have drawn a flash the game does not.

A separate definition rather than an inline pattern, because both generators resolve a string
type **by definition name** — `NEWTYPE_REFS` in `gen-rust-mods.mjs` and `descriptorOf` in
`gen-java-registry.mjs`. An inline pattern is a hard error in both, by design, and the error text
says to teach them. A named definition is the shape they can be taught.

It cost four edits and every one of them was refused by a gate first, which is the system working:

- `gen-java-registry.mjs` refused an unknown pattern, so `Type.COLOR_RGB` exists with its own
  clamp. It **refuses** an alpha byte rather than truncating it — a clamp that rewrote the value
  would hide the disagreement from the player and from the bridge, which reports what was stored.
- `ModRegistryTest`'s clamp-parity test then refused the mismatch between that clamp and the
  sub-schema, which is exactly what it was written for.
- `gen-rust-mods.mjs` refused a `$ref` with no newtype, so `HexColorRgb` is a real validating
  newtype in `keybind.rs` — and the generated file's `use` line is now derived from
  `NEWTYPE_REFS` instead of naming two types by hand, because the hand-written pair went stale the
  moment a third arrived.
- `LiveStateTest` asserted Java *masked* the byte; it now asserts the refusal, and that the refused
  write echoes the colour still in force. Null is reserved for a setting that does not exist —
  `bridge.json` says a call returns the value actually applied.

**Narrowing an accepted value set is the same shape of change as removing a setting**, and it is
worth saying so next to `REMOVED_SETTINGS`: `HexColorRgb` rejects an eight-digit value on
deserialization, so a stored loadout carrying one would fail `Store::load`, and `Store::list`
collects a `Result` — one such file would take the whole library down. This was safe **only**
because `hit_color` has never been released, exactly as `old_animations` was safe to withdraw. A
narrowing to a setting that has shipped needs a migration, and `REMOVED_SETTINGS` as it stands
cannot express one — it drops a key, it does not repair a value.

### 2026-09-09 (last) — `old_animations` comes back from the bytecode, as two mods and one fewer setting

`mods.json` registry `version` `9 → 10`; **`protocol.json` and `bridge.json` untouched.** Twenty-nine
mods: 16 HUD, 13 gameplay. Two `kind: gameplay` PvP mods, and between them they carry three of the
four settings the withdrawn draft had — because **one of those four named a difference that does not
exist**.

**The withdrawal, and what closed it.** The entry two below records `old_animations` declared and
pulled before its wave shipped: every one of its four settings was defined as "what 1.7 did", the 1.7
side could not be established from this repo, and a mod whose two `one_seven` defaults changed nothing
would have been the exact failure that removed `text_shadow` on the day it landed. That entry said what
it needed was **a 1.7.10 mapping, not more time**, and that is what it got — a 1.7.10 jar remapped with
Legacy Fabric yarn `1.7.10+build.603`, disassembled method-for-method against Loom's named 1.8.9 jar.
The result is the most useful thing in this entry and it is not a mod: **the draft was wrong about the
game, not merely unfinished.**

- **`swing` is deleted and must never come back.** `LivingEntity.swingHand`, `tickHandSwing`,
  `getHandSwingProgress` and the private `getMiningSpeedMultiplier` they read are byte-for-byte
  identical across the two versions. So is the first-person arc: 1.8.9's `applyEquipAndSwingOffset`
  opens `translate(0.56f, -0.52f, -0.71999997f)` and 1.7.10 opens the same translate spelled
  `0.7f*0.8f, -0.65f*0.8f, -0.9f*0.8f` — the same numbers with the `0.8f` constant-folded, the third
  folding to exactly `-0.71999997f` in float — followed by the same three rotations on the same axes.
  **There is no 1.7 swing arc.** What players remember under that name is 1.8's held-item *render
  pipeline* (fixed sprite quads → `BakedModel` + the JSON `firstperson` transforms), which is an L-cost
  renderer rewrite; the danger a `swing` checkbox creates is that it *promises* that rewrite in one
  word, so the mod file's `$comment` says so rather than leaving the absence to be re-litigated.
- **`always_swing` was factually wrong**, which §7 of the roster had already half-caught: both versions
  call `swingHand()` unconditionally at offset 12 of `doAttack`, before the hit result is read, so a
  miss swings the arm in 1.8 exactly as in 1.7. The real difference is 1.8.9's `attackCooldown = 10` on
  MISS (survival only) where 1.7.10's switch has no MISS entry and falls to `return`. That is one
  premise supporting **two** features, and they are not the same kind of thing — which is what split
  this wave into two mods.
- **`block_hit` is real and is exactly one float**, in two render paths. 1.8.9's `renderArmHoldingItem`
  passes `applyEquipAndSwingOffset(equip, 0.0F)` in every `UseAction` branch. 1.7.10 skipped the swing
  *translate* while using an item too — its three sines sit inside the same `else` — but the
  equip-and-swing block that follows is **outside** the branch and re-reads `getHandSwingProgress`
  live. So the fix is to pass the live progress, and explicitly **not** to add `translateSwingProgress`,
  which an earlier guess called for and which would overshoot 1.7 by the one thing 1.7 also skipped.
  `applySwordBlockTransformation`'s constants are identical in both versions.
- **`use_while_digging` was ready, and had an unrecorded mirror.** 1.8.9's `doUse` opens
  `if (interactionManager.isBreakingBlock()) return;` and 1.7.10 has no such guard and no
  `isBreakingBlock()` accessor at all. Reading the method next door found the other half: 1.8.9's
  `handleBlockBreaking` opens `if (attackCooldown > 0 || player.isUsingItem()) return;` where 1.7.10 has
  only the cooldown term. Same shape, same cost, and in a Bedwars rush arguably the more-felt half.

**Two mods, and the reason is `hypixel_safe` being per mod**

- **`old_animations`** (pvp, `reset`, ships off, **`safe`**) — `block_hit`
  (`vanilla`/`one_seven`, default `one_seven`) and `swing_during_delay` (boolean, default false).
  Animations only, and both claims are cheap to check: `block_hit` is an argument to a
  `GlStateManager.rotate`, and `swing_during_delay` sends nothing. No packet, no timing, no reach, no
  information — a stronger `safe` case than the withdrawn draft's, which had `use_while_digging` inside
  the same mod.
- **`old_input`** (pvp, `tap`, ships off, **`grey`**, every switch off) — `use_while_digging`,
  `dig_while_using`, `no_miss_delay`. §6.1 renders Hypixel's policy as an allowlist of three categories
  closing with "if it does not fit a category, assume it is disallowed", and none of these three is a
  performance improvement, an aesthetic change or a HUD readout: each changes what the client *does* on
  an input and therefore what the server receives. **"It ships off" is an argument about defaults, not
  about classification** — `hypixel_safe` is a per-mod claim and the badge is computed from it over the
  mods a loadout has enabled, which is exactly why shipping off costs nobody the badge and exactly why
  the label still has to be honest for the player who turns it on.

**Two decisions where the brief for this wave was followed with a change, both named here so a reader
can overturn them in one place.**

*The third-person half of the block revert is folded into `block_hit` rather than given its own
boolean.* 1.8 added `rightArm.posY = -0.5235988f` (a **yaw** of −30°; see the mapping trap below) to the
BLOCK case of `BiPedModel.setAngles`, which 1.7 did not have — it is how *other* players' blocking looks
on your screen, and nothing about how you appear to anyone else. It is the same revert in the other
render path, nobody wants 1.7 blocking in their hands and 1.8 blocking on the model in front of them,
and a boolean whose entire visible effect is a 30° arm rotation on an entity would need its own drawing
in `test/preview.test.tsx` or an exemption from it — the argument that kept `combo.hide_at_zero` and its
two neighbours out of Wave 3. The setting's description carries the split list if it is ever wanted.

*`always_swing` is renamed `swing_during_delay`, not merely redefined.* It ships as the cosmetic variant
— reproduce `LivingEntity.swingHand()`'s body through the public `handSwinging` / `handSwingTicks`
fields, **without** `ClientPlayerEntity.swingHand()`'s outbound `HandSwingC2SPacket`, so the arm agrees
with the mouse during the dead time and nothing reaches the server. The functional variant is
`old_input.no_miss_delay`. Keeping the old name would have carried the disproved premise ("1.8 does not
swing on a miss") into a Rust field, a Java descriptor and a settings page, on a setting that does not
make you always swing — you already always swing — while the honest name sat one mod over. The
description also states the thing that name would hide: **the click is still swallowed**, only the
animation returns. That makes it a behaviour that existed in *neither* version rather than a 1.7 revert,
and it is off by default because an arm that swings on a click the game ate can be read as a hit that
landed.

**Why two mods where `overlay` was kept whole, since a reader will notice the inconsistency.** Two
things separate them. §3.3 #1 explicitly instructed that grab-bag to "ship the six that matter as one
VOID mod and stop" — there was a roster line telling `overlay` not to split, and there is none here. And
`overlay`'s five switches are the same *kind* of thing (a settings-driven `return` in a render pass)
differing only in exposure, so splitting them would have cut a coherent mod along a line the player
cannot see; here the line is the subject of the mod. A bundle is only as safe as its worst switch, and a
player who wants their sword to keep swinging while they block should not lose HYPIXEL-READY for it.

**A mapping trap, recorded because it will otherwise cost an afternoon.** Legacy Fabric yarn names
`ModelPart`'s **rotation** fields `posX`/`posY`/`posZ` — `ModelPart.render` hands them to
`GlStateManager.rotate`, and the translation is the separate `pivotX`/`pivotY`/`pivotZ`. So
`rightArm.posY = -0.5235988f` is −π/6 of yaw, not a position. Read as a position it is unimplementable.

**Two scoping notes that a faster mixin would get wrong**, both in the setting descriptions rather than
here alone. `no_miss_delay` must be scoped to the **MISS branch only**: 1.7.10 still set the same
`attackCooldown = 10` on a null hit result and on a BLOCK hit resolving to `AIR`, so suppressing the
field outright goes past 1.7 rather than back to it. And `block_hit`'s `one_seven` reverts **BLOCK
only**, although 1.7's live swing applied to every `UseAction` — eating, drinking and bow-drawing
included — because those are cancelled by the click that would swing or do not occur inside an exchange.

**Glyphs — `reset` exists, `tap` does not.** `old_animations` keeps `reset`, already in `@void/ui`'s
`ICON_NAMES`. `tap` is declared undrawn, which is the order `docs/adding-a-mod.md` §3 describes:
`MOD_ICONS` is `satisfies Record<ModId, IconName>`, so a named-but-undrawn glyph is a **compile error in
the package that owns the drawing** rather than an empty box in game. It must be structurally distinct
from the existing `cursor-click` at 13px or it needs a different name, and the name is reconcilable here
in one line if the art wave proposes one.

**Consumers — nothing in this wave is generated yet, and three generators are outstanding.** They write
outside `schema/` and were deliberately not run: `scripts/gen-java-registry.mjs` (`ModRegistry.java`, 29
mods), `scripts/gen-rust-mods.mjs` (`mods/generated.rs` — two settings structs and one new enum,
`OldAnimationsBlockHit`; note this is *not* the `OldAnimationsSwing` the withdrawn draft would have
produced, because that setting no longer exists), and `pnpm --filter @void/protocol gen` followed by its
build. The one hand-written half that a generator cannot reach is in `crates/void-loadout`:
`grey_mods_are_exactly_fullbright_hitboxes_and_overlay` in `mods.rs` gains a fourth member and its name
has to move with it — `..._fullbright_hitboxes_overlay_and_old_input`. That is the mechanism working as
designed: a mod joining the class that gates the badge must touch a test with the class written into its
name. Nothing else in `void-loadout` needs a per-mod edit — `registry()` and `validate_settings()`
dispatch through generated tables, `defaults.rs` iterates `ModId::ALL` so both ship off in the curated
loadouts, and `Loadout::validate` bounds `hud` by `HudModId::ALL.len()`, unchanged at 16 because this
wave added no HUD mod. **No `REMOVED_SETTINGS` row is needed**: `old_animations` was never released, so
no loadout on disk carries `swing`, `always_swing` or `use_while_digging` — the same free removal the
withdrawal entry claimed, still true a wave later. Steps 2, 3 and 4 of `docs/adding-a-mod.md` — the two
widgets and `MOD_ORDER`, the `tap` glyph, and the actuators — are all still to write.

### 2026-09-09 (later still) — the camera, the flash, the vignette, and a setting that never did anything

`mods.json` registry `version` `8 → 9`; **`protocol.json` and `bridge.json` are both untouched**,
and that is a result rather than an oversight — see the note on the key path below. Twenty-seven
mods: 16 HUD, 11 gameplay. Three mods added, and one setting removed from a fourth.

**Three `kind: gameplay` PvP mods, and two of them are two roster rows each**

- **`freelook`** (pvp, `orbit`, ships off) — `keybind`, `mode` (`hold`/`toggle`), `perspective`
  (`third_back`/`third_front`/`free`) and `snap_back`. **It absorbs Snaplook**, which
  `docs/mod-roster.md` lists as its own mod at §3.2 #9. The roster's own verdict on #9 is "Build
  **after** freelook — it is the same camera machinery with a different input mode", costed `S*`
  where the asterisk is freelook having already paid. Snaplook is "hold a key for a third-person
  look, release to snap back", which is `mode: hold` + `snap_back: true` + `perspective:
  third_back` — and all three are the factory defaults, so VOID's freelook out of the box *is*
  Snaplook, on the key the player binds. A second registry row for a configuration of the first is
  `overlay`'s objection moved to a different corner of the roster: two mods over one input path is
  the grab-bag growing back. The concrete failure is sharper here than it was there, and this repo
  already found it once — `toggle_sneak` removed `toggle_sprint.sneak_too` because "two owners of
  one latch is a bug waiting for a player to find". Two mods over one camera is two keybinds
  racing for one piece of view state, and the player who binds both gets whichever actuator ran
  last. The mod file carries the split list in case the call is overturned: `mode` and `snap_back`
  move, `perspective` stays, and the camera detach cannot be split at all — which is the thing to
  notice before splitting rather than after.

  **The load-bearing sentence in that file is not about the bundle, it is about one enum value.**
  `perspective: free` means an **orbit** about the pivot vanilla's third-person camera already
  uses — the player's own head — and never a translation away from the body. A camera that can
  leave the body is a freecam; a freecam is scouting; scouting sees around walls and into rooms
  the player cannot reach, which is §6.1's "chest or player radar" wearing a nicer word and sits
  on the list §6.1 prefixes with "Never, under any framing". The distance from the pivot is
  vanilla's and is deliberately not a setting, because the moment it is one somebody raises it. If
  `free` ever stops meaning orbit the mod does not become `grey`; it does not ship.

  **`safe`.** §3.2 #4's researched verdict is "Camera-only, allowed, universally used", and §6.1's
  allowlist test is about information, which this adds none of: every camera position freelook can
  reach is one vanilla's own F5 reaches, and vanilla's front view already shows a player what is
  behind them while their body faces forward. What the mod adds is a continuous sweep in place of
  two fixed offsets, and a key that is not F5.

  **Its key does not go through the bridge, and the previous wave is why that is worth writing
  down.** `modaction` was added because `stopwatch`'s key is a *verb* for a widget the **page**
  owns. Freelook's camera is owned entirely by Java, so its key is polled the way `zoom.key`
  already is — `VoidClient` reads `isKeyDown` each frame and `ZoomController` eases off the level
  — and a round trip through the overlay would put a frame of latency inside a hold. That is the
  whole reason this wave adds no channel and no message.

- **`hit_color`** (pvp, `droplet`, ships off) — `color`, `own_hits_only`, `intensity`. §3.2 #5:
  "Entity-render tint; genuinely helps read whether a hit landed." It recolours
  `RenderLivingBase`'s entity hurt overlay and nothing else; it does not touch the first-person
  damage overlay, which `overlay` cut for removing a combat cue rather than an occluder, and which
  stays cut.

  **`safe`, and the *range* is what buys it rather than an argument.** Hue is aesthetic without
  needing a defence. `intensity` was where that stopped being true: a strength slider whose top
  end made a hit readable that vanilla left ambiguous is not "purely aesthetic" on §6.1's own
  account, and it would have been the second `grey` mod in two waves. So the number was defined
  instead of defended — `intensity` is a **fraction of vanilla's own hurt-overlay alpha**, 1 being
  exactly what the game already draws, with nothing above it. The mod cannot make a landed hit
  more visible than Minecraft made it; it can only make it a different colour, or less visible.
  That is `fov`'s move ("the range is exactly vanilla's own slider") applied to a different
  number, and it is the reason this entry is short where `overlay`'s was long.

  Two things are explicitly *not* what carries the classification, so that changing either does
  not move the mod by accident. `own_hits_only` (default on) is a legibility default: the tint on
  an entity somebody else hit is already on your screen at the same alpha for the same hurt ticks,
  so recolouring it reveals nothing and only makes a worse cue. And the mod's `$comment` names
  what *would* make it `grey`: an intensity range above vanilla's alpha, any duration or
  persistence setting — a tint that outlives `hurtTime` is a hit *marker*, a different mod and a
  `grey` one — or tinting an entity vanilla did not tint.

  **One conflict the schema cannot express and the file therefore states.** `#/definitions/
  hex_color` accepts `#RRGGBBAA` and `crosshair.color` ships the eight-digit form, so nothing
  stops a player storing an alpha byte in `color` — where it would be a second owner of the alpha
  `intensity` owns, and the failure is a player dragging `intensity` to its top and seeing nothing
  because their colour ends in `00`. The actuator drops the byte; the default is six digits;
  narrowing the `$ref` in place is not possible in draft-07, so the newtype in `void-loadout`'s
  `mods.rs` is where an enforcement could live if one is wanted.

  The default is `#2FB8A6` and not red, for two reasons that are both about the rest of the
  screen. A mod that turns on and changes nothing looks broken — the argument that withdrew
  `old_animations` last wave — and `#FF0000` would be exactly that mod. And red is the worst
  available colour on a 1.8 map: the nether, lava, red wool and red leather in Bedwars, and as of
  this same wave the low-health vignette `damage_tint` draws at the edge of the same frame. The
  two most decision-shaped cues on screen should not share a hue, and that trade is made once,
  here, rather than left to whichever settings page the player opens second.

- **`damage_tint`** (pvp, `pulse`, ships off) — `threshold` (half-hearts, 1–20, default 6),
  `strength` and `camera_shake` (`vanilla`/`reduced`/`off`). §3.2 #7's case is one sentence and it
  is the right one: "I did not notice I was at 3 hearts" is a real way to lose. **It absorbs Hurt
  cam control**, §3.2 #8. Unlike freelook/Snaplook these two share no render path — a
  health-driven vignette and `EntityRenderer#hurtCameraEffect` are different code — so the
  argument is the per-mod tax rather than the machinery. Split, `hurt_cam` is a registry row, a
  settings page, a thumbnail, a preview, a Rust settings struct, a Java descriptor block, an
  undrawn glyph and a `MOD_ORDER` slot, all to carry one three-valued enum; §9 costs that tax, and
  `build.mjs` removed nine of its twenty-five steps but not steps 2 and 4, which are the
  hand-written ones. The cost is `overlay`'s cost and the file writes it down: `hypixel_safe` is
  per **mod**, so if `camera_shake` is ever reclassified the vignette loses the badge with it.
  **That is the split condition and `camera_shake` is the split list.**

  **`safe`, and `camera_shake` is the half that needed checking rather than assuming.** It would
  be easy to read it as `overlay`'s trade: vanilla rolls the camera up to fourteen degrees when
  you are hit, and turning that off removes a cost the game imposed. But that is not the test
  `overlay` applied. Its two grey switches remove **occluders** — the fire overlay and the pumpkin
  blur are world pixels the game covered up, and suppressing them shows you world you could not
  see; "Fire overlay alone decides fights" decides them by hiding the other player. A camera roll
  hides nothing: the same frame, rotated, every pixel already yours. The precedent for that shape
  shipped two files ago and is `safe` — `fov.lock_sprint` suppresses a camera change the game
  imposes on you for sprinting and for *every knockback you take*, which is the same event and the
  same complaint. `camera_shake` is `lock_sprint`'s sibling, not `hide_fire`'s. The vignette is
  not near the line at all: your own health is already on your own HUD, and drawing it a second
  time in the periphery moves information rather than adding it.

  `vanilla` is the default and that is deliberate against the grain of what players think they
  want. The roll is a handicap, but it is oriented by `attackedAtYaw` — it is close to the only
  thing 1.8.9's client tells you about *where* a hit came from — so `reduced` exists to keep the
  direction and take the amplitude, and the description says so, because a player who deletes the
  cue from a settings page without knowing it was a cue has made a trade nobody explained.

  **No heartbeat audio, and §3.2 #7 does mention one.** Three reasons, in increasing order of
  weight. It would be the first sound VOID ships and this repo has no audio asset pipeline — §3.4
  #4 defers kill sounds for exactly that, which is the roster's own statement that audio is a
  project and not a setting. It would be a cue the game does not make, where everything else here
  re-presents something already on screen. And it could not be previewed: `test/preview.test.tsx`
  walks every setting and asserts the drawing changed, so it would land in `NOT_IN_THE_PREVIEW` on
  the day it shipped — the `text_shadow` failure moved into a different medium.

  It is `kind: gameplay` although it draws, for `crosshair`'s and `hitboxes`' reason: `kind`
  splits on whether the mod owns a draggable HUD item, and a full-viewport vignette has nowhere to
  be placed. The file also says where it should be drawn, because "vignette" and "full-screen
  effect" sound like the same cost and are two orders of magnitude apart: it belongs in the game's
  own overlay pass beside the crosshair, not as an element in the Ultralight page, where a
  full-viewport node whose alpha follows health would dirty the whole view every health tick —
  `design/rendering-invariants.md` §5 measures a forced full repaint at 22 ms against 0.5 ms. One
  alpha gradient, not the post-process §3.3 refuses on that same budget.

**All three are `safe`, and that is a claim rather than a copy.** `overlay` was classed `grey`
today by *not* copying its neighbours, so the three arguments above were made from §6.1's text
each time and each one names what would overturn it. The consequence for the Rust side is that
`grey_mods_are_exactly_fullbright_hitboxes_and_overlay` needs **no** edit; if any of the three
calls is overturned on review, that test's name and its `vec!` are the hand-written half that has
to move with it.

**Three glyphs that do not exist yet — `orbit`, `droplet`, `pulse`.** None is in `@void/ui`'s
`ICON_NAMES`, and they are declared anyway, which is the order `docs/adding-a-mod.md` §3
describes: `MOD_ICONS` is `satisfies Record<ModId, IconName>`, so a named-but-undrawn glyph is a
**compile error in the package that owns the drawing** rather than an empty box in game.
`@void/ui` typechecks red until a later wave draws them, and that is the mechanism working. The
last wave got to write the opposite note because the art wave beat it to `clock`; this one does
not.

**Removed: `toggle_sprint.mode` — because it was provably a no-op, not because it was deprecated**

Separate change, same version bump, and the reason is worth more than the diff. The Java wave read
it out of the bytecode: `ClientPlayerEntity.tickMovement` re-evaluates sprint as a *level* every
tick — both the idle-start test and the collision-resume test call `sprintKey.isPressed()` — and
`KeyBinding.setKeyPressed(code, true)` writes exactly the `pressed` field that `isPressed()` reads.
A latch setting that field every tick is bit-for-bit indistinguishable from a player holding the
key; sprint auto-resumes after a collision either way. So "restore vanilla hold-to-sprint" had no
implementation other than *write nothing*, and writing nothing is what `on: false` already does.
`mode: hold` was not merely redundant with the off switch, it was **worse** than it: the actuator
went quiet while the Mods panel went on reporting the mod enabled, which is a settings page lying
about the game. It was redundant a second way as well — `keybind` toggles the mod in game and its
own description already claimed the same use case, "a fight where holding the key is what the
hands expect".

**The prose was the actual defect, and this is the part to take away from the entry.** The
previous wave rewrote this description to say that on this mod `hold` "now means the mod is inert
until it is set back", and closed with "worth revisiting when a sprint indicator comes back". That
is a bug promoted to a specification. A confidently-worded description is exactly what stops the
next reader checking, which is the same species of silence `design/rendering-invariants.md`
catalogues in code; there was nothing to revisit, because there was no behaviour behind the
setting to restore. The correction was made in good faith and it made the file *more* wrong, which
is the argument for reading the bytecode rather than the neighbouring sentence.

**`toggle_sneak.mode` is not affected and stays**, and the difference is structural rather than a
judgement call: `toggle_sneak` latches its **own** bind rather than vanilla's sneak key, so its
`hold` is a real behaviour — sneak under a thumb that is not Shift. `toggle_sprint` latches
vanilla's sprint key and its `keybind` is the toggle-the-mod bind, so there is no second key for a
`hold` to move sprint onto.

**The migration is mandatory, not precautionary.** `mode` was in the shipped registry defaults from
the first release, so it is in essentially every loadout on disk; every `*Settings` struct in
`void-loadout` is `#[serde(deny_unknown_fields)]`, so without the migration each of those files
stops deserialising, `Store::load` returns `Error::Json`, and `Store::list` collects a `Result`
over the whole directory — one orphaned file takes the entire library listing with it.
`("toggle_sprint", "mode")` is added to `REMOVED_SETTINGS` in `crates/void-loadout/src/store.rs`
in the same change, the third entry after `show_status` and `sneak_too`; the stale key is dropped
on the way in and never written back, so the next save is the migration. The two tests bracketing
that constant — one proving a stale removed key still loads, one proving junk that was never ours
still fails loudly — are what keep the tolerance narrow, and both are parameterised on
`show_status`, so neither needed editing. `schema/loadout.json`'s third example carried
`toggle_sprint.mode` by hand and no longer does; `validate.mjs` would have failed on it, which is
the gate doing its job.

**Consumers**

Nothing in this wave is generated yet, and the list is short because the wave deliberately touched
no wire format. Still to run, all of them writing outside `schema/`: `scripts/gen-java-registry.mjs`
(`ModRegistry.java` — 27 mods, and `toggle_sprint`'s `mode` descriptor goes),
`scripts/gen-rust-mods.mjs` (`mods/generated.rs` — three settings structs and three enums added,
`FreelookMode`, `FreelookPerspective` and `DamageTintCameraShake`; `ToggleSprintMode` **removed**),
and `pnpm --filter @void/protocol gen`, which is *not* what `build` runs — `build` is only `tsc`,
so `src/generated/*` is stale at 24 mods until `gen` is run and the build after it is what proves
the result compiles. Hand-written halves in `void-loadout` that needed a per-mod edit: the
`REMOVED_SETTINGS` row above, and nothing else — the grey-set assertion is unchanged because all
three new mods are `safe`, `registry()` and `validate_settings()` dispatch through generated
tables, `defaults.rs` iterates `ModId::ALL` so all three ship off in the curated loadouts, and
`Loadout::validate` bounds `hud` by `HudModId::ALL.len()`, which is unchanged at 16 because this
wave added no HUD mod. Step 2 (`packages/ingame/src/mods/<id>.tsx` and `MOD_ORDER`), step 3 (the
three glyphs) and step 4 (the actuators) are all still to write, and one existing preview needs
rebuilding rather than extending: `menu/gameplay-previews.tsx` draws `toggle_sprint` as a
two-mode timeline off `settings.mode`, which is a type error the moment `@void/protocol` is
regenerated.


### 2026-09-09 (later) — a key can reach a mod, and the five that change how it feels

`mods.json` registry `version` `7 → 8`; `bridge.json` gains its tenth channel; **`protocol.json`
and its `v` are untouched, deliberately** — the reason is the whole first half of this entry.
Twenty-five mods: 16 HUD, 9 gameplay.

**`bridge.json` — the `modaction` event, and the input path it opens**

Until now a key could reach a *mod* in exactly one way. `VoidClient.toggleMod` polls four per-mod
`keybind` settings, edges each with `input/EdgeKey` against a code mirrored into
`LiveState.applyActuatorFields`, flips that mod's `on` and pushes `setting`. That is a table, not
four copies, and its own comment says why — but it covers every mod whose key is a *switch* and no
mod whose key is a *verb*. `docs/mod-roster.md` §7 is where the bill came due: Stopwatch was cut
from the last wave because `keys` carries W/A/S/D, the mouse, space and shift, and `hotkey_id` is
a closed set of two globals, so a `start_key` would have stored a key nothing acted on. That is
exactly the failure `text_shadow` was removed from `_shared.json` for, and it is the reason a
seven-mod wave shipped six.

So: `{"e": "modaction", "payload": {"mod": <mod_id>, "action": <verb>}}`, Java → JS. It is the
second row of the same table — same poll, same edge, same mirrored code — emitting a named
action instead of writing a setting. Three decisions inside it are worth more than the diff:

- **`mod` `$ref`s `mods.json#/definitions/mod_id`**, exactly as `setting_payload.id` does. The
  mod set is written down once, in the registry, and a restated list on a bridge payload is a
  list that goes stale the next time a mod is added.
- **`action` is a pattern, not a closed enum**, and that is argued rather than defaulted. The
  closed sets nearby are closed because a consumer must handle them exhaustively: both shims
  build their channel map from `event_name`, so an undeclared channel is dropped in total
  silence, and `void-core`'s `sync::pump` switches on `hotkey_id` to move the launcher's own
  pointer. Neither applies here. A `modaction` is dispatched by `mod` first, so an action nobody
  implements is a no-op inside one widget rather than a lost channel; the two halves that must
  agree ship in the same JAR (which is the same reason every payload on this surface stays
  `additionalProperties: false`); and the vocabulary has no registry to derive from the way
  `mod_id` does, so an enum would be a *fourth* place a per-mod input is written down, after the
  mod's settings prose, the Java table and the widget. Today it would be a closed set of two,
  invented for one mod. The `$comment` names the condition for revisiting: three or four mods
  sharing a vocabulary, at which point closing it buys real exhaustiveness.
- **It does not go to Rust, and that is the point rather than an omission.** `hotkey_id` stays
  `loadout.next` and `overlay`. Those two are things Java has **already done** — the active
  loadout advanced, the menu opened — and the launcher keeps its own copy of that state, so its
  tray and its next launch would disagree with the running game if it were not told. A
  `modaction` is the opposite shape: a **request**, unfulfilled when it is sent, fulfilled by the
  page inside the same binary. The launcher holds no state that depends on whether a stopwatch is
  running, and a stopwatch that survived a relaunch is not a feature anyone asked for. A third
  `hotkey_id` would have put a per-mod input vocabulary on a cross-process wire that has to stay
  compatible across launcher versions, in order to notify a process with nothing to do.

Consumers, the five-files-or-none list `bridge/connect.ts` names: `bridge.json`, `@void/protocol`'s
`VOID_EVENTS` / `VoidEventPayloadMap` (`ModactionPayload` is generated), `void-shim.js`'s `EVENTS`,
and `createFakeVoid`, which gains `emitModAction(id, action)` beside `applyModSetting` — no return
value, because nothing is stored. **The Java half is not written.** It is one more row in
`toggleMod`'s table that emits instead of setting, plus two mirrored key codes in
`applyActuatorFields`, and the page half is the widget that acts on `start_stop` and `reset`.

**Five mods**

- **`stopwatch`** (`hud`, utility, `clock`, ships off) — the mod the event exists for. With
  `watermark` it is one of two with no game field behind it: the elapsed time is the overlay's
  own, counted from `Date.now()` the way the combo chip already counts its own timeout, and no
  sensor, tick field or wire message carries it. Java owns the key, the page owns the clock, and
  they cannot disagree about elapsed time because only one of them counts it. `start_key` names
  the action `start_stop` and `reset_key` names `reset`, **in the settings prose**, because that
  is the one document the Java wave and the page wave both read. `-25,-99` is the third row of
  the bottom-right corner, 38 px above Server address and 76 above Memory, continuing the rhythm
  those two opened last wave — it is not a diagnostic like its neighbours, but it is read between
  fights rather than during them, which is the same reason for the same corner.
  Its icon is `clock`, and the glyph is **already drawn** — `packages/ui`'s `ICON_NAMES` and
  `packages/ingame/scripts/build-icons.py` both carry it, from `ec2d0a5`, whose own comment says
  it is deliberately a stopwatch rather than a wall clock. This entry was written expecting to
  name a glyph that did not exist and to leave `@void/ui` red until it did, which is the order
  `docs/adding-a-mod.md` §3 describes; the art wave got there first, so `MOD_ICONS`'
  `satisfies Record<ModId, IconName>` is satisfied on the day the id lands and `@void/ui`
  typechecks clean. Worth recording because the mechanism is only visible when it fires: a
  named-but-undrawn glyph is a compile error in the package that owns the drawing, not an empty
  box in game.
- **`fov`** (`gameplay`, pvp, `eye`, off) — `GameOptions.fov`, 30–110 (exactly vanilla's own
  slider, which is the whole §11 argument for `safe`: it moves a number the game already lets the
  player move, where `fullbright.gamma` runs to 15 against a vanilla ceiling of 1), plus
  `lock_sprint` and `lock_bow`. Lunar has 25 options here; §3.2 #2 says you need about four and
  this is three. **The mod file carries a hazard note and it is the most load-bearing thing in
  the entry**: `fov` is the same field family as Fullbright's `gamma`, and `GameOptions.save()`
  persists the live field into the player's own `options.txt` on almost any settings change and
  on quit. Left alone, the mod does not override the player's field of view — it *eats* it, the
  next launch starts at the override, `VoidClient` captures the override as the value to restore,
  and turning the mod off restores the override. That is measured, on gamma, in
  `mixin/GameOptionsMixin.java`'s own doc comment. The fix is that mixin, extended by one field
  in the same two injections. It is written next to the mod because the argument belongs where
  the mod is, not where the Java is.
- **`toggle_sneak`** (`gameplay`, pvp, `chevron-down`, off) — `mode` (`toggle`/`hold`) and its
  own `keybind`. §3.2 #3 says promote it, and promotion means the boolean it replaces goes:
  **`toggle_sprint.sneak_too` is removed.** That was checked rather than assumed, and the answer
  is that removing a setting is *fatal* on its own. Every `*Settings` struct in `void-loadout` is
  `#[serde(deny_unknown_fields)]`, so a stored loadout carrying the key stops deserialising and
  `Store::load` returns `Error::Json`; `Store::list` collects a `Result` over the whole
  directory, so **one orphaned file takes the entire library listing with it**, not just itself.
  `validate_settings` and `Loadout::validate` are not where this is decided — they run after the
  parse that has already failed. What makes it survivable is `REMOVED_SETTINGS` in
  `void-loadout/src/store.rs`, a mechanism this repo already built for exactly this and already
  used once (`toggle_sprint.show_status`): `read_loadout_json` drops a retired key on the way in
  and the next save writes the file without it, so the migration is a save the player was going
  to make anyway. `("toggle_sprint", "sneak_too")` is added there in the same change. The two
  store tests either side of it — one proving a stale removed key still loads, one proving junk
  that was never ours still fails loudly — are what keep that tolerance narrow. Java's
  `LiveState.toggleSprintSneakToo` and its reader in `VoidClient` are the loose end and are named
  in `gen-java-registry.mjs`'s `JAVA_NOTES`, because they *compile* and silently read `false`
  rather than failing — the quiet kind.
- **`overlay`** (`gameplay`, visual, `sparkle`, off) — `hide_fire`, `view_bobbing`
  (`vanilla`/`minimal`/`off`), `hide_own_armor`, `hide_stuck_arrows`, `hide_pumpkin`. §3.3 #1
  calls this the highest value-per-hour on the page and it is: five settings-driven suppressions
  in render passes that already exist, no sensor and no new number. It is five and not the six
  the roster lists, and the difference is named in the file rather than rounded — the damage
  overlay is not here, because suppressing the red flash removes a *combat cue* rather than an
  occluder, which is the opposite trade from the other four; it belongs with §3.2 #7's damage
  tint. **It is `hypixel_safe: grey`, and it is the only mod in this wave that is not `safe`.**
  Copying `safe` off its neighbours would have been wrong for two of the five switches.
  `hide_fire` removes an occluder the game puts on your camera as the *cost* of being on fire —
  §3.3 #1's own words are "Fire overlay alone decides fights" — and `hide_pumpkin` removes the
  blur that is the entire reason wearing a pumpkin is a trade. §6.1 renders Hypixel's policy as
  an allowlist of three categories closing with "if it does not fit a category, assume it is
  disallowed", and a change that decides fights is, on the roster's own account, not "purely
  aesthetic". That is the same reasoning §3.3 #7 already applies to the fog customiser, and the
  same posture this schema takes on `fullbright` while every competitor ships it as ordinary. The
  cost is written down beside the call: `hypixel_safe` is per **mod**, so a player who enables
  this only for `view_bobbing: minimal` loses the HYPIXEL-READY badge too. Splitting the bundle
  into a `safe` mod and a `grey` one was considered and rejected — §3.3 #1 says ship one mod and
  stop, two mods is the grab-bag growing back, and a badge honest about the most exposed switch
  in a bundle is the only kind worth showing. `void-loadout`'s exact-set assertion is renamed to
  `grey_mods_are_exactly_fullbright_hitboxes_and_overlay`; it stays an exact set by hand, because
  a mod joining the class that gates the badge should have to touch a test with a name in it.
- ~~**`old_animations`**~~ — **declared here and withdrawn before the wave shipped.** The entry
  above described `swing`, `block_hit`, `always_swing` and `use_while_digging`, the first two
  defaulting to `one_seven`, and argued that a mod which turned on and changed nothing would look
  broken. That argument is what removed it: the game code could not be written, so the mod would
  have been exactly that. Registry went 20 → 25 → **24**, and `registry_version` 8 covers the
  four that shipped. Nothing ever released it, so no stored loadout carries it and there is no
  `REMOVED_SETTINGS` row to add — the one time a mod can be taken out for free.

  The 1.8.9 side was established precisely, by disassembling the real methods out of Loom's named
  jar. The **1.7** side could not be: there is no 1.7.10 source or mapping in this repo or in the
  Gradle cache, and every one of the four settings is defined as "what 1.7 did". A reconstructed
  swing arc that nobody can runtime-test today is worse than an absent mod. `docs/mod-roster.md`
  §7 carries what was learned, and it is not nothing — one of the four settings is ready and
  provable, and one of the four descriptions above is factually wrong about 1.8.9.

**One correction while here.** `toggle_sprint.mode`'s description still ended "but keeps the
status readout", and the readout has been gone since `show_status` was removed. It now says what
is true — on that mod `hold` leaves nothing behind and the mod is inert until it is set back —
and contrasts it with `toggle_sneak.mode`, whose `hold` still does something, because it moves
sneak onto that mod's own bind rather than vanilla's key.

Consumers, all generated and all re-run: `ModRegistry.java` (25 mods, 193 setting descriptors),
`void-loadout`'s `mods/generated.rs` (25 mods, 20 settings enums — five new settings structs and
five new enums: `StopwatchFormat`, `ToggleSneakMode`, `OverlayViewBobbing`, `OldAnimationsSwing`,
`OldAnimationsBlockHit`), `@void/protocol`'s `src/generated/*` and its build. Hand-written halves
that did need a per-mod edit, both in `void-loadout` and both about a decision no generator can
make: the grey-set assertion in `mods.rs`, and the `REMOVED_SETTINGS` row in `store.rs`.
`registry()` and `validate_settings()` still dispatch through generated tables and needed nothing;
`defaults.rs` iterates `ModId::ALL`, so all five ship off in the three curated loadouts; and
`Loadout::validate` bounds `hud` by `HudModId::ALL.len()`, which follows to 16 on its own.
`scripts/verify-mods.mjs` picks up `stopwatch` in `baseline-all-huds` and `hud-all-off` for free,
because both are derived from `HUD_MOD_IDS`; per-setting steps for the five are still to write.

### 2026-09-09 — six readouts, and the first mod in the bottom-right corner

`mods.json` registry `version` `6 → 7`; `protocol.json` `v` unchanged, and `bridge.json`
unchanged — **no sensor was added by this change.** Every one of the six reads a `tick_payload`
field that already existed (`saturation`, `held_count`, `speed`, `memory`, `hits`) or an event
that already existed (`server.host`). That is the whole reason six mods land at once: the
expensive half of a HUD mod is the sensor, and this wave had none to write.

Six new `kind: hud` entries — `combo` (pvp), `saturation` (hud), `momentum` (hud), `memory`
(hud), `server_address` (utility), `item_counter` (pvp). Registry order is 15 HUD + 5 gameplay,
20 in total; `hud_layout.maxItems` follows `hud_mod_id` to 15, as it is derived to.

- **`combo` is derived in JS, like `cps`.** `bridge.json`'s `hits` sends two monotonic counters
  and refuses to send a combo, because a combo is a count with a *timeout policy* on it and the
  timeout is `combo.reset_ms` — a mod setting. A sensor that expired the count would put a UI
  policy on the wire and make the field unshareable between two readers who disagree about it.
  Counters rather than events for the matching reason: an event lost to a dropped tick leaves
  the combo wrong forever, a counter that jumps by two is still exactly right.
- **Three settings were specified and deliberately not shipped**: `combo.hide_at_zero`,
  `item_counter.hide_empty`, `server_address.hide_offline`. Each was a switch whose entire
  content is "the widget is not there". Hiding is *behaviour* here, not a preference — a combo
  chip reading `0` between fights, a counter on an empty hand and a host chip in singleplayer
  are noise, and `design/quiet-cell-system.md` §1 draws the customisation line at position,
  anchor and offset, scale, opacity, density, what is shown and format, none of which is
  absence. They are also untestable as settings: `packages/ingame/test/preview.test.tsx` walks
  every setting of every mod and asserts the drawing changed, and a switch with no drawing to
  move would need three new `NOT_IN_THE_PREVIEW` exemptions in one wave — from a list whose own
  doc comment calls it the one place that gate can quietly erode. The three widgets do it
  unconditionally instead, and each mod file carries a `$comment` saying so, because the next
  person to read a two-setting mod will want to add a third.
- **`server_address` shares `wifi` with `ping`, on purpose.** They are the same corner of the
  product — which server, and how far away it feels. `compass` is already shared by
  `coordinates` and `direction` for the same reason, so the precedent is one row up;
  `MOD_ICONS` is `Record<ModId, IconName>` and not a bijection. `ping.show_host` also stays:
  that is the inline form, this is the standalone chip, exactly the split `direction` makes
  against `coordinates.show_direction`.
- **`saturation.decimals` is `0..2`, not the `0..1` it would pick for itself.**
  `packages/protocol`'s `gen.mjs` keys `SETTING_BOUNDS` by property *name* — a name means one
  thing across this schema — and **throws** rather than hand one mod another's slider.
  `coordinates.decimals` and `momentum.decimals` are both `0..2`, so `0..1` was a hard build
  failure, caught by the generator rather than by a reviewer. Widening is the honest fix: the
  sensor sends saturation as a raw float, so a second place is real, just rarely worth the
  width. The default is still 1.
- **The bottom-right corner opens.** `memory` at `-25,-23` and `server_address` at `-25,-61`
  are the first factory placements there. Both are diagnostics — read when something is wrong,
  not while it is going wrong — so keeping them opposite the top-left reference stack stops the
  "is the client healthy" glance from crossing the "where am I" column. The insets match the
  corners already in use (25 px from the right as the top-right stack, 23 px from the bottom as
  the top-left stack is from the top), and the 38 px gap is this table's rhythm.
  `combo` (`23,217`) and `saturation`/`momentum` (`255`, `293`) extend the left column on that
  same rhythm; `item_counter` (`175,-146`) joins the *175* column of the bottom-left corner
  rather than the 31 one, because Keystrokes at `dx 31` is over a hundred canvas pixels tall and
  anything stacked above `dy -109` there lands on the caps. `combo`'s and `item_counter`'s
  `note` fields carry those arguments into every generated table.
  One correction while here: `docs/adding-a-mod.md`'s worked example annotates `combo` with
  "Under Coordinates, the mod players confuse it with." That argument belongs to `direction`,
  which has held `dy 179` since it shipped; `combo`'s real note is that it is *last* in a column
  the eye already sweeps.
- **All six are `hypixel_safe: safe`.** Every one reads state the client already has about its
  own player or its own process and draws it; nothing changes what is rendered of the world.
  `grey` stays exactly `fullbright` and `hitboxes`, which `void-loadout`'s
  `grey_mods_are_exactly_fullbright_and_hitboxes` asserts.
- Consumers, all generated and all re-run: `ModRegistry.java` (166 setting descriptors),
  `void-loadout`'s `mods/generated.rs` (20 mods, 15 settings enums — six new settings structs,
  four new enums: `SaturationStyle`, `MomentumUnit`, `MemoryStyle`, `ServerAddressStyle`),
  `@void/protocol`'s `src/generated/*`. Nothing hand-written needed a per-mod edit: `registry()`
  and `validate_settings()` dispatch through generated tables, `defaults.rs` builds its three
  curated loadouts by iterating `ModId::ALL` (so the six ship *off* there, which is the product
  decision that file already documents), and `Loadout::validate` bounds `hud` by
  `HudModId::ALL.len()`.
- `scripts/verify-mods.mjs` gained fifteen steps and lost its last hand-written mod list: the
  `hud-all-off` step named the eight original HUD mods and had never been extended, so it was
  already asserting less than it claimed when `direction` shipped. It is derived from
  `HUD_MOD_IDS` now, like `HUD` and `baseMods()` beside it. `combo.reset_ms` has no step, for
  the same reason `cps.window_ms` has none — two timeouts produce the same still frame.

### 2026-09-08 (later still) — `default_placement`, and the last hand-maintained table

`mods.json` registry `version` `5 → 6`; `protocol.json` `v` unchanged.

**`mod_entry` gains `default_placement`, on `kind: hud` mods only.** The factory HUD layout —
what a new loadout is seeded with and what the HUD editor's `Reset layout` restores — was the
one per-mod table left written out by hand, and it was written out *twice*: `DEFAULT_HUD` in
`packages/ingame/src/store/hud-geometry.ts` and `Loadout.DEFAULT_HUD` in Java. They were kept
level by a vitest that read the Java *source* and diffed it, which is precisely the shape
`docs/mod-roster.md` §9 names as the problem rather than the fix. Adding the fourteenth mod
meant editing both by hand, which is how it got flagged.

What the duplication risked is worse than a stale table: two tables that disagree make **`Reset
layout` a move rather than an undo** — the client starts in one layout and the button that
claims to restore it silently puts every widget somewhere else, for everyone.

- **The constraint is per-`kind`, and the schema says so.** `mod_entry` lists the property so
  `additionalProperties: false` permits it, and leaves it out of `required`; each `<id>_entry`
  then `required`s it on a HUD mod and forbids it on a gameplay one with
  `not: {required: [default_placement]}`. A gameplay mod draws nothing, so it has nowhere to
  be, and a HUD mod with no placement is a widget every consumer would have to guess at. Both
  are now schema errors rather than silent defaults, and `validate.mjs` walks all fourteen to
  prove the narrowings themselves were generated for the right kind.
- **The anchor set is not restated.** `build.mjs` copies
  `loadout.json#/definitions/anchor` into `hud_placement`; a second hand-written copy of nine
  strings would be the same duplication this field removes, and a `$ref` the other way would
  invert the documents' dependency — the registry is the root.
- **The reasoning moved with the numbers**, because it was the argument for them: the
  38-42 px rhythm and the design-canvas pixels are `hud_placement`'s own `description`, and the
  per-number arguments (the watermark's `dy 141` rather than `loadout.json`'s 58, which would
  collide with the ping chip at 65; direction taking the row under Coordinates) are the
  `$comment` on that mod's `<id>_entry`. Every generator reproduces them next to the row, so
  the argument is still readable where the number is.
- **Values unchanged.** Both pre-change tables were diffed against everything generated from
  the schema, row for row; nine placements, no differences. A silent change here moves every
  player's HUD on their next launch.
- Consumers: `ModRegistry`'s generated `place(...)` table plus a hand-written `Placement` and
  `defaultHud()`, which `Loadout.defaults` now seeds from (its own `DEFAULT_HUD` is gone);
  `void_loadout::HudPlacement` and `Registry::default_placement` — mandatory, since `ModEntry`
  is `deny_unknown_fields` and a field the schema has and Rust does not is a runtime parse
  failure of the whole registry; `@void/protocol`'s generated `DEFAULT_HUD_PLACEMENTS`, which
  `hud-geometry.ts` re-exports as `DEFAULT_HUD`.

`packages/ingame/test/hud-defaults.test.ts` survives with a different job: it no longer diffs
two hand tables against each other, it checks the schema against both generated tables — the
page's and the *committed bytes* of `ModRegistry.java`. Both generated files are committed
because neither Gradle nor Vite may run Node, and committed means they can be stale.

### 2026-09-08 (later) — `icon` on the entry, and Java/Rust stop being transcribed

`mods.json` registry `version` `4` throughout; `protocol.json` `v` unchanged.

**`mod_entry` gains a required `icon`.** The glyph the Mods list and quick palette draw was a
hand-maintained `MOD_ICONS` table in `@void/ui` — the last piece of mod *identity* no schema
knew about. It lived there because two applications need it and neither can import the other,
which is an argument for the contract rather than for a table per app. The game draws no icons,
so Rust and Java carry the field without using it; Rust had to, since `ModEntry` is
`deny_unknown_fields`.

**`text_shadow` removed from the HUD chrome block, same day it was added.**
`design/ultralight-notes.md` §3 rates `text-shadow` [risky] — the rasteriser drops it, or it
smears the glyph atlas — so the setting would have moved on the settings page and drawn nothing
in game. §3 also says HUD legibility is already solved structurally and the decision "must be
preserved rather than 'improved' later". `background` is that chip, exposed. The block is
`background` / `border` / `padding`, and `_shared.json`'s `$comment` carries the argument so
nobody re-adds it. Version was not bumped again: the block had not shipped.

**`ModRegistry.java` and `void-loadout`'s type surface are now generated**, by
`scripts/gen-java-registry.mjs` and `scripts/gen-rust-mods.mjs`, both `--check`-gated in CI
beside `build.mjs --check`. §9 of `docs/mod-roster.md` called the Java transcription "a test
compensating for hand-transcription"; that is now a generator with the test guarding staleness
instead. Both semantic diffs came back identical — the transcriptions were correct — so the
change buys the future, not a bug fix.

### 2026-09-08 — one file per mod, and the shared HUD chrome block

`mods.json` registry `version` `3 → 4`; `protocol.json` `v` unchanged at 2 — nothing on the
Rust ⇄ Java wire changed shape, only the settings each mod carries.

**`mods.json` and `loadout.json` are now generated from `schema/mods/`**

Not a contract change so much as a change to how the contract is written. A mod id appeared in
seven places in this file and two in `loadout.json`; every one was mechanical and every one
failed quietly. `build.mjs` derives all nine from `mods/<id>.json`, and `--check` is a CI gate
ahead of `validate.mjs`. The round-trip was verified structurally against the previous
`mods.json` before anything was added — enums, per-mod settings, entry narrowing and all
thirteen `defaults` identical. It caught one real drop (`keystrokes` ships `opacity` 0.85,
not 1), which is now a stated `shared_overrides` rather than an accident waiting to be noticed.

**The HUD chrome block — `background`, `border`, `text_shadow`, `padding`**

Every `kind: hud` mod, declared once in `mods/_shared.json#/hud`. Settled now rather than after
the ninth HUD widget, which is what `docs/mod-roster.md` §9 asks for: "decide whether VOID
matches that depth as a shared HUD-item property set before you write the ninth HUD widget, or
you will retrofit it into twenty settings pages later."

The answer is **not** Lunar's depth, and the difference is the whole design decision. Lunar
gives every HUD mod a background colour, a border colour and per-element text colours;
`design/quiet-cell-system.md` §1 names exactly that as the far side of its line — "a chip
background, border or label colour per mod" — because colour in this system encodes a value or
a state and is never a preference. So all four are *structural*: `background` is a step on a
token scale (`none` | `subtle` | `solid`), `border` and `text_shadow` are booleans, `padding`
is `density` under its own name, which §1 lists as legitimate customisation. The player chooses
whether a chip has a ground, never what colour that ground is.

`text_shadow` defaults **on**: it is what Minecraft itself does, and it is the one control that
makes white text survive a snow biome. Everything else defaults to the vanilla treatment, so
the block is a no-op for a player who liked the HUD as it was.

Consumers: Rust's eight HUD settings structs (mandatory — every one is `deny_unknown_fields`,
so the new keys would have failed registry parsing) plus `HudBackground` / `HudPadding`;
Java's `ModRegistry` transcription; `packages/ingame`'s `hud/chrome.ts`, applied by `HudSlot`
and by the mod page's `PreviewZoom` — the same box, in the same place `scale` and `opacity`
already apply, so the preview stays the same drawing rather than a similar one.

### 2026-09-07 — the watermark, and global settings reach the page

Two contract changes, landed together. `mods.json` registry `version` `2 → 3`;
`protocol.json` `v` is **unchanged at 2**, and deliberately so — see below.

**`mods.json` — a thirteenth mod, `watermark`**

- **`watermark`** (`kind: hud`, `category: visual`, `hypixel_safe: safe`, label
  `VOID watermark`). VOID drew its own mark over the game and had nowhere to say so, so
  the overlay was about to hard-code a thirteenth tile that the registry did not know
  about — exactly the re-declaration `mods.json` exists to stop. Its `source` is
  `drawn by the overlay; no game field`: it is the first mod with no sensor at all, which
  is why the column is prose rather than a 1.8.9 field.
- `watermark_settings` is modelled on `fps_settings` / `crosshair_settings`: `on`
  (default true), `scale` (0.25–4), `opacity` (0–1, default **0.9** rather than 1 — the
  mark sits behind the readouts the player is actually reading) and `style`
  (`full` | `mark` | `word`, default `full`).
- **No `color` key**, unlike every other drawn mod. `design/quiet-cell-system.md` §1 says
  accent marks the live value or the selected item and nothing else; a watermark is
  neither, so a colour control here would be a licence to break the system.
- The closed set is spelled out in six places and all six moved: `mods.required`,
  `mods.properties`, the `mod_id` enum, the `hud_mod_id` enum (**8** HUD mods now, not 7),
  `loadout.json`'s `mod_states.properties`, and the shipped registry in `examples[0]`.
  `hud_layout.maxItems` went `7 → 8` with it — the bound is one item per HUD mod, so it
  tracks `hud_mod_id` rather than being a number of its own.
- Default placement is `top-left` at `dx 20, dy 58`, under the existing fps (`dy 20`) and
  ping (`dy 38`) entries in `loadout.json`'s Sword PvP example, which is where every PvP
  client puts its mark. The Bedwars example is deliberately partial — it is what
  `omitted_mods_fall_back_to_the_registry` reads — and was left alone, as were the
  partial loadouts in `protocol.json` and `bridge.json`.
- Consumers: `void_loadout::ModId::Watermark` / `HudModId::Watermark` /
  `WatermarkSettings` / `WatermarkStyle`, and the `hud.len()` bound in
  `Loadout::validate`; `@void/protocol`'s `MOD_IDS` and `HUD_MOD_IDS`; the Java
  `ModRegistry` transcription.

**`bridge.json` — `settings`, `session`, and `setGlobal`**

`GlobalSettings` existed in `protocol.json`, in Rust and in Java's `LiveState`, and the
in-game page had **no way to read or write any of it**. The menu key and the UI scale are
in-game settings that only the in-game UI can sensibly change, and the one surface that
could change them did not have them. Three additions close that:

- **`settings` event** — the whole `global_settings` object, `$ref`'d from `protocol.json`
  so the channel and the wire message cannot drift into two shapes. Pushed on
  `pushWholeState()` — first paint, launcher `init`, and a reloaded document per
  `design/rendering-invariants.md` §9a — and again whenever Rust pushes new settings down.
  Explicitly **not** pushed as an echo of the page's own `setGlobal`, for the reason §6.5
  already gives for `setting`: the call returned the stored value, and a second push would
  fight the control the player is holding.
- **`setGlobal(key, value) -> value | null` call** — the exact mirror of `setModSetting`
  one level up. Synchronous, Java validates and clamps and returns *what it stored*, and
  the page binds to the return rather than to what it sent. `null` means nothing was
  stored: an unknown key, or a value that could not be made usable. `key` is a pattern
  string rather than an enum, matching `global_settings`' `additionalProperties: true` —
  a launcher may add a global without a protocol bump, and a key this Java does not know
  simply answers `null`.
- **`session` event** — *added, not invented*. Java was already pushing it and the page
  was already consuming it (`packages/ingame`'s store, via a cast); it was missing from
  this file, so `@void/protocol`'s `VoidBridge` did not know the channel and the reference
  shim's channel list did not carry it. Both shim channel lists are **closed** — `on`
  hands back a no-op subscription for an unknown name and `__emit` drops an envelope whose
  channel has no list — so an undeclared channel fails in total silence. That is how
  `session` was lost the first time, and the note is now in `event_name`'s own description.

No `v` bump: `bridge.json` does not cross a process boundary. The mod JAR embeds the UI
bundle, so the two ship as one binary and the surface is deliberately not
forward-compatible (see "How each side consumes them" above). Nothing on the Rust ⇄ Java
wire changed.

Consumers: `@void/protocol`'s `VOID_EVENTS`, `VOID_CALLS`, `VoidEventPayloadMap`,
`VoidBridge` (which also finally absorbed the optional `__keepsEscape`, and now exports
`SessionInfo`), `installVoidShim` and `createFakeVoid`; `void-shim.js`'s `EVENTS` and
`CALLS`; Java's bridge and `LiveState`.

### 2026-09-03 — the integration pass

Folded in the seams the six parallel owners reported. `mods.json` registry `version`
`1 → 2`, and `protocol.json` `v` `1 → 2`.

The `v` bump is for `init.loadouts` alone. `hotkey` is additive and a v1 launcher would
ignore it, as §7 requires — but a **v2 mod against a v1 launcher** would receive
`loadout_summary` objects where it expects whole loadouts, materialise every mod at its
factory default, and then silently apply the *wrong* loadout on a switch. Nothing would
error; the player would just find their settings gone. That is exactly the mixed-halves
case `v` exists to refuse, so it is refused.

**`mods.json`**

- **`category` per mod** (`hud | pvp | visual | utility`), plus a `category` definition and
  a `const` narrowing in each `<id>_entry`. The Mods panel filters across
  All / HUD / PvP / Visual / Utility (frame 244:538), which is a *product* split;
  `kind` is a *data-direction* split and the two genuinely disagree — Crosshair and
  Fullbright are `gameplay` but `visual`, Zoom is `gameplay` but `utility`. The mapping
  used to be hand-written in `packages/ingame/src/registry.ts`, read off the frame tile by
  tile; that override is deleted and the module derives everything from the registry.
  Consumers: `void_loadout::Category` + `ModEntry.category`; `ModRegistry.Category` and
  `ModRegistryTest.categoriesMatch`; `@void/protocol`'s `getModCategory`,
  `MOD_CATEGORIES`, `MOD_FILTER_TABS`, `getCategoryLabel`, `modsInCategory`.
- **Labels aligned with the frames.** `FPS → FPS display`, `CPS → CPS counter`,
  `Ping → Ping display`. The panel copy now lives in the registry, so the three overrides
  in the overlay's `registry.ts` are gone and `modLabel` is a straight lookup.
- **Three keystrokes settings the Mod settings frame (244:834) draws and the schema did
  not have**: `corner_radius` (integer 0–20 px, default **8**), `key_color`
  (`shell|raised|pill|sky|teal`, default **shell**) and `pressed_color`
  (`accent|sky|warn|fear|teal`, default **accent**) — defaults straight off
  `design/README.md`. They are token *names*, not hex, so the choice survives a theme
  change; `hex_color` would have frozen the launcher's palette into the in-game bundle.
  The overlay was already writing all three through `setModSetting`, where Java clamped
  them away as unknown keys.

**`protocol.json`**

- **`hotkey` (Java → Rust)**, `{"t":"hotkey","id":"loadout.next"|"overlay"}`. The mod had
  no way to tell the launcher that the player pressed L or opened the overlay, so the
  tray and the launcher's active-loadout pointer drifted away from the running game.
  It is a notification — Java has already acted — and it is dropped rather than queued
  when the link is down, because the state it produced travels in its own `state` message.
  `void-core`'s `sync::pump` follows `loadout.next` by advancing the stored active pointer.
- **`init.loadouts` carries whole loadouts, not `loadout_summary`.** This is the answer to
  "the mod cannot switch to a loadout it only has a summary of": the alternative was a
  `request_loadout` round trip, and whole loadouts are small enough (≈1 KB × ≤128, once per
  launch) that asking is not worth the state machine. `LiveState` lost its `pendingSwitch`
  field entirely — nothing is ever pending — and `void.switchLoadout` is now always
  immediate (§8.2). `loadout_summary` stays in `loadout.json` for the launcher's tray and
  `loadouts_list` IPC, and its description says so.

**`bridge.json`**

- **`loadouts` event** — the whole library, in full, in library order. Rust sent
  `init.loadouts` to Java and it stopped there; JS had no accessor, so the in-game
  Loadouts frame listed only the loadouts it had happened to watch go past. Pushed on
  `init` and on any library change, before `loadout`.
- **`setting` event** — one `{id, key, value}` Java changed *by itself*, such as the
  `keystrokes.keybind` overlay hotkey. The mod used to re-push the whole loadout for a
  single boolean. Explicitly **not** pushed for a change the page made through
  `setModSetting`: that call already returned the stored value, and a second push would
  fight the control the player is holding.
- **`openKeybindCapture` documented correctly.** The synchronous answer is
  `returns: null` and means *armed*; the captured key arrives later as a call-result
  envelope through `__emit`. The reference shim was reading the synchronous null as the
  resolution, which settled every capture instantly with no key — the schema now says
  null travels on both channels meaning two different things, and both shims tell them
  apart by channel. The mod's `keybindScript` emits the envelope
  (`window.void.__emit({"c":"openKeybindCapture","returns":…})`) instead of the private
  `__emitKeybind`, which survives as a shorthand.
