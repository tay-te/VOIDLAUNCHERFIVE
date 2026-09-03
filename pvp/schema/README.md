# `schema/` — the contracts

Four JSON Schema (draft-07) files. They are the **only** thing the six parallel owners in
[`../CONTRACTS.md`](../CONTRACTS.md) share. Nobody edits another owner's code; a
cross-directory need is expressed by reading a schema here.

| File | Defines | Written by | Read by |
|---|---|---|---|
| `mods.json` | The closed registry of the 12 mods (§3): id, `kind`, `hypixel_safe` (§11), defaults, and a settings sub-schema per mod | `core` | everyone |
| `loadout.json` | The loadout model (§8): mod state + anchor-based HUD layout + stats | `core` | everyone |
| `protocol.json` | Every Rust ⇄ Java WS message (§7), a `oneOf` on `t` | `core` | `core`, `mod` |
| `bridge.json` | The `window.void` surface (§6.5): 5 events Java→JS, 6 calls JS→Java | `core` | `mod`, `ingame`, `ui` |

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
each entry's `kind`, and that every registry `defaults` object satisfies its own mod's
settings sub-schema. It belongs in CI.

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

### Java — `mod/src/main/java/dev/void/client/{net,bridge}`

- `net/` speaks `protocol.json`. It sends `hello` with the token from `-Dvoid.token` and
  waits for `init` before applying anything; **the mod keeps no config files of its own**
  (§6.1), so `init` is the entire world of state it starts from.
- If Rust is unreachable, in-memory state persists for the session and is flushed on
  reconnect (§6.1) — the same `state` and `hud` messages, replayed.
- `bridge/` implements `bridge.json`. Java is authoritative for live state: a call
  returns the value actually applied, so there is no ack and no optimistic UI (§6.5).
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
