# CONTRACTS — who owns what

Six agents build `void-pvp` in parallel. This page is the whole coordination protocol.
The design contract it implements is `docs/PVP_ARCHITECTURE.md` in `VOIDLAUNCHERFIVE`;
section references below (§4, §6.5, §7, §8) point there.

## The one rule

**Each owner writes only inside its own directories. Cross-directory needs are expressed
by reading `schema/*.json` — never by editing another owner's files.**

If you need something another owner has not built yet, do not reach into their directory,
do not stub their file "temporarily", and do not vendor a copy. Read the schema, code
against it, and if the schema does not say what you need, that is the bug: raise it, get
`schema/` changed, and everyone recompiles against the new contract.

## Ownership map

| Owner | Writes exclusively to | Builds |
|---|---|---|
| **core** | `crates/void-core/`, `crates/void-bridge/`, `crates/void-loadout/`, `schema/`, root `Cargo.toml` | Auth → download → JVM spawn; the WS server; the loadout store, diff and persistence |
| **desktop** | `apps/desktop/` (incl. `src-tauri/`) | The Tauri launcher: one frameless window, tray, updater, thin `#[tauri::command]` wrappers over `void-core` |
| **ui** | `packages/ui/`, `packages/protocol/` | Shared React components and design tokens; the generated TS types |
| **ingame** | `packages/ingame/` | The in-game React entry: HUD + Right-Shift menus + HUD editor |
| **mod** | `mod/` **except `mod/native/`** | The Legacy Fabric mod: Mixin sensors and actuators, the Ultralight host, the `window.void` bridge, the WS client, `VoidMenuScreen` |
| **native** | `mod/native/` | The JNI binding to Ultralight's C API and the OpenGL `GPUDriver`; C++17, CMake, built in CI for win-x64 / mac-x64 / mac-arm64 |

`schema/` is written by **core** and read by everyone. A change there is a contract change:
propose it, don't just land it.

`design/` pre-dates this scaffold: Figma screen exports plus `tokens.css` / `tokens.json`.
It is **reference material, read-only for everyone**. **ui** ports the tokens into
`packages/ui`; nobody edits `design/` and nothing imports from it at build time.

Root files not listed above (`package.json`, `pnpm-workspace.yaml`, `.gitignore`, this
file) are shared. Touch them only to register your own package or ignore your own build
output.

## The seams

### `window.void` — Java ⇄ JS, in-process

The bridge object is named exactly **`window.void`**. Defined in `schema/bridge.json`, §6.5.
It is implemented by **mod** (`mod/src/main/java/dev/void/client/bridge/`) and consumed by
**ingame**.

- Java → JS is push: `void.on('keys'|'tick'|'server'|'loadout'|'menu', handler)`.
- JS → Java is a call: `setGameplay`, `setHud`, `setModSetting`, `switchLoadout`,
  `closeMenu`, `openKeybindCapture`.
- Ultralight runs **inside the JVM**, so calls are synchronous and return the state
  actually applied. No ack, no request id, no optimistic UI. `openKeybindCapture` is the
  one exception and returns a Promise.
- **ingame** must also run against a fake `window.void` in a normal browser (the `?debug`
  harness, §9) — there are no devtools in game.

### `-Dvoid.port` / `-Dvoid.token` — Rust ⇄ Java, over localhost WS

**core** binds `ws://127.0.0.1:<port>`, mints a per-spawn session token, and passes both to
the JVM as the system properties **`-Dvoid.port`** and **`-Dvoid.token`**. **mod** reads
them in `net/`, connects, and sends `hello` carrying the token; the server closes the
socket if it does not match.

Messages are defined in `schema/protocol.json`, §7. The link carries **state, never
frames**. `v` is the protocol version, `1`, present on `hello` and `init` only; a mismatch
means the two halves were not shipped together, and the launcher refuses to launch.
Unknown `t` values and unknown fields are ignored by both sides.

### `packages/ingame` → `mod/src/main/resources/assets/void/ui/`

**ingame** builds its static bundle into
**`mod/src/main/resources/assets/void/ui/`**. This is the single exception to the writes-
only-in-your-own-directory rule, and it is a *build output*, not source: the directory's
contents are gitignored, and **mod** must not hand-edit anything in it.

**mod** loads the bundle from that classpath path (`assets/void/ui/index.html`) into the
Ultralight view. Budget: ≤ 400 KB gzipped (§10).

### `packages/ui` → both bundles

One React codebase, two bundles (§9). **ui** owns the components and design tokens;
**desktop** bundles them for the system webview, **ingame** bundles them for Ultralight.
Ultralight is WebKit-derived and older: no `backdrop-filter`, no `text-shadow`, no 3D
transforms, no WebGL. **ui** must not ship a token that depends on any of them — the
in-game renderer is the constraint, not the launcher.

### `mod/native` → `mod`

**native** produces a loadable library plus a small Java-facing API surface. **mod**
consumes it and never builds it. This is the M1 gate (§14): if it stalls, everything
downstream of the Ultralight view stalls with it, and we want to know that early.

## Deciding where something goes

- Does it change how the game *plays*? It belongs in the **loadout** (`schema/loadout.json`),
  not in global settings. Account, Java path, RAM, hotkeys and theme are global (§8.3).
- Is it drawn? Then it is HTML, owned by **ingame** — the only GL exception is the
  crosshair, which must sit at the exact pixel centre (§3).
- Is it a number the HUD shows? It goes Mixin → `window.void` in-process, and **never**
  over the WS. Only summaries cross to Rust.
- Is it persisted? Java holds live state and tells Rust afterwards; Rust is the store of
  record between sessions (§6.1).
