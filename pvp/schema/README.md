# `schema/` — the contracts

Four JSON Schema (draft-07) files. **`mods.json` and the derived half of `loadout.json` are
generated** — see "Adding a mod" below and `docs/adding-a-mod.md`. They are the **only** thing the six parallel owners in
[`../CONTRACTS.md`](../CONTRACTS.md) share. Nobody edits another owner's code; a
cross-directory need is expressed by reading a schema here.

| File | Defines | Written by | Read by |
|---|---|---|---|
| `mods.json` | **Generated** (`build.mjs`). The closed registry of the 13 mods (§3, plus the VOID watermark): id, `kind`, `category` (§3), `hypixel_safe` (§11), the panel `label`, defaults, and a settings sub-schema per mod | `core` | everyone |
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
`include_str!`s it, `@void/protocol` generates from it and `ModRegistry.java` transcribes it,
and none of those can run a Node script. So "generated" has to also mean "checked".

`mods/_shared.json` holds the properties every mod of a `kind` carries. A property added there
reaches all eight HUD mods at once, which is the point — `docs/mod-roster.md` §9's advice was
to settle the shared HUD property set once rather than retrofit it into twenty settings pages.

`mods/_base.json` holds the static half of the schema and the registry `version`, which is
bumped by hand: "did this change break a stored loadout" is not something a diff can answer.

## Validate

```sh
cd schema
npm i --no-save ajv
node validate.mjs        # compiles all four, checks every `examples` entry, plus cross-checks
```

`validate.mjs` also asserts the things JSON Schema cannot: that the registry contains
exactly the 13 ids in the `mod_id` enum, that `hud_mod_id`/`gameplay_mod_id` agree with
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
