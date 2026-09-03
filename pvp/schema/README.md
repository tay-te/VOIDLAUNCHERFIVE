# `schema/` — the contracts

Four JSON Schema (draft-07) files. They are the **only** thing the six parallel owners in
[`../CONTRACTS.md`](../CONTRACTS.md) share. Nobody edits another owner's code; a
cross-directory need is expressed by reading a schema here.

| File | Defines | Written by | Read by |
|---|---|---|---|
| `mods.json` | The closed registry of the 12 mods (§3): id, `kind`, `category` (§3), `hypixel_safe` (§11), the panel `label`, defaults, and a settings sub-schema per mod | `core` | everyone |
| `loadout.json` | The loadout model (§8): mod state + anchor-based HUD layout + stats | `core` | everyone |
| `protocol.json` | Every Rust ⇄ Java WS message (§7), a `oneOf` on `t` — 6 Java→Rust, 3 Rust→Java | `core` | `core`, `mod` |
| `bridge.json` | The `window.void` surface (§6.5): 7 events Java→JS, 6 calls JS→Java | `core` | `mod`, `ingame`, `ui` |

`mods.json` is the root: `loadout.json` `$ref`s its mod ids and settings, and both
`protocol.json` and `bridge.json` `$ref` `loadout.json`. **A mod is added in exactly one
place.** Refs are absolute (`https://schema.void.dev/pvp/<file>.json#/definitions/...`)
so any resolver works as long as all four documents are registered; the URL is an
identifier, not a location, and nothing fetches it.

## Validate

```sh
cd schema
npm i --no-save ajv
node validate.mjs        # compiles all four, checks every `examples` entry, plus cross-checks
```

`validate.mjs` also asserts the things JSON Schema cannot: that the registry contains
exactly the 12 ids in the `mod_id` enum, that `hud_mod_id`/`gameplay_mod_id` agree with
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
- `v` appears on `hello` and `init` only, `const 1`. A mismatch is fatal: the launcher
  refuses to launch and prompts for an update (§7).
- `loadout.json` is `void-loadout`'s on-disk format. Its `state_patch` paths
  (`mods.<mod>.<setting>`) are the diff unit.

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
