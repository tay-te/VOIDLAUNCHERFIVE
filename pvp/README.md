# VOID PVP

A Lunar-style PVP client for **Minecraft 1.8.9**, in two halves that ship together. The
**desktop launcher** (Tauri + Rust) signs you in, downloads 1.8.9 and Legacy Fabric,
keeps your loadout library and spawns the game. The **client mod** (Legacy Fabric +
Ultralight) draws the HUD and the Right-Shift menus *inside Minecraft's own GL frame*,
from the same React components and design tokens the launcher uses — so the in-game UI
matches the Figma instead of approximating it, and there is no overlay window to lag,
flicker or vanish in exclusive fullscreen. Rust owns persistence, Java owns live state,
and a localhost WebSocket carries state between them — never frames. The full design
contract is [`../docs/PVP_ARCHITECTURE.md`](../docs/PVP_ARCHITECTURE.md); who owns which
directory is [`CONTRACTS.md`](CONTRACTS.md).

---

## Runtime topology

```
┌───────────────────────────────────────────────────────────┐
│ void-pvp desktop  (Tauri, one frameless window + tray)    │
│   Launcher UI (React)                                     │
│   Rust core: auth · download · JVM spawn · loadout store  │
│              WS server · updater                          │
└──────────────────────┬────────────────────────────────────┘
                       │ spawns the JVM with
                       │ -Dvoid.port=<port> -Dvoid.token=<token>
                       │ state over ws://127.0.0.1:<port>   (never frames)
                       ▼
┌───────────────────────────────────────────────────────────┐
│ Minecraft 1.8.9 + Legacy Fabric                           │
│ ┌───────────────────────────────────────────────────────┐ │
│ │ void-client mod                                       │ │
│ │   Mixin sensors  ──▶ window.void ──▶ Ultralight view  │ │
│ │   Mixin actuators ◀── window.void ◀── (React HUD/menu)│ │
│ │   Ultralight ──▶ GL texture ──▶ drawn in the HUD pass │ │
│ │   Netty WS client ◀──▶ Rust                           │ │
│ └───────────────────────────────────────────────────────┘ │
└───────────────────────────────────────────────────────────┘
```

Ultralight runs **inside the JVM**, so `window.void` calls are synchronous and return the
state actually applied: no acks, no optimistic UI. Telemetry a HUD widget draws never
crosses to Rust — only summaries do.

---

## Directory map

| Path | What it is |
|---|---|
| [`apps/desktop/`](apps/desktop/README.md) | The Tauri 2 launcher: one frameless window, tray, updater, thin `#[tauri::command]` wrappers over the crates |
| [`packages/ui/`](packages/ui/README.md) | `@void/ui` — the shared React components and design tokens, built twice (launcher webview, in-game Ultralight) |
| [`packages/ingame/`](packages/ingame/README.md) | `@void/ingame` — the in-game HUD layer + Right-Shift menus; builds into the mod's resources |
| [`packages/protocol/`](packages/protocol/README.md) | `@void/protocol` — TypeScript generated from `schema/`, plus the reference bridge shim and the fake `window.void` |
| [`crates/`](crates/README.md) | `void-core` (auth, manifests, download, Java, launch), `void-bridge` (the WS server), `void-loadout` (registry, loadouts, store) and the `void-pvp` CLI |
| [`mod/`](mod/README.md) | The `void-client` Legacy Fabric mod: Mixin sensors and actuators, the Ultralight host, `VoidMenuScreen`, the WS client. Java 8 bytecode |
| [`mod/native/`](mod/native/README.md) | Our JNI binding to Ultralight's C API plus an OpenGL 2.1 `GPUDriver`. C++17, CMake, one natives tree per platform |
| [`schema/`](schema/README.md) | The four JSON Schemas every owner reads: `mods.json`, `loadout.json`, `protocol.json`, `bridge.json` |
| [`design/`](design/README.md) | Figma exports, `tokens.css` / `tokens.json`, `ultralight-notes.md`. **Read-only reference**; nothing imports it at build time |
| [`docs/`](docs/README.md) | Design and decision documents, including [`docs/TESTING.md`](docs/TESTING.md) |
| `scripts/` | `verify-all.sh` / `verify-all.ps1` — every local check, one summary |

---

## Prerequisites

Everywhere: **Rust 1.85+**, **Node 22**, **pnpm 9**, and a **JDK 17 or newer** to run
Gradle (the mod compiles to Java 8 bytecode; no Java 8 JDK is needed to build it). The
game itself runs on Java 8, which the launcher finds or fetches for you.

| | Windows | macOS |
|---|---|---|
| Rust | `rustup` (MSVC toolchain) | `rustup` |
| Node + pnpm | Node 22, `corepack enable` | Node 22, `corepack enable` |
| JDK | 17+ (Temurin) for Gradle | 17+ (Temurin) for Gradle |
| C++ toolchain | **Visual Studio Build Tools 2022** with the *Desktop development with C++* workload, and **CMake 3.20+** | **Xcode Command Line Tools** (`xcode-select --install`) and **CMake 3.20+** |
| Webview | **WebView2** — present on Windows 11 and current Windows 10, else install the Evergreen Runtime | built in |

Linux is a development platform only, not a shipping target (§13). For the Tauri launcher
there: `libwebkit2gtk-4.1-dev libgtk-3-dev libayatana-appindicator3-dev librsvg2-dev
libssl-dev`.

Nothing needs the Ultralight SDK installed — CMake downloads the pinned revision, verifies
its SHA-256 and extracts it into `mod/native/sdk/` (its bundled libarchive reads `.7z`, so
no p7zip either).

---

## Quick start

```sh
git clone <this repo> && cd VOIDLAUNCHERFIVE/pvp
pnpm install
scripts/verify-all.sh          # Windows:  .\scripts\verify-all.ps1
```

`verify-all` runs every unit-test suite in the repo — schema validation, `cargo
build/test/clippy`, the Tauri crate's own `cargo check`, `pnpm -r typecheck/test/build`,
the Ultralight constraint guard, the 400 KB bundle budget, `./gradlew build` and the
binding's CPU-renderer test — and prints one pass/fail table. Nothing it does needs a
display, a GPU or the network beyond the package managers.

Then, in rough order of how much they prove:

```sh
# 1. Review the UI in a normal browser (no Rust, no game).
pnpm --filter @void/ui gallery                    # component gallery, both renderers
pnpm --filter @void/ingame dev                    # then open http://localhost:5183/?debug
pnpm --filter @void/desktop dev:web               # launcher screens with mock IPC

# 2. Launch vanilla 1.8.9 + Legacy Fabric from the CLI, with no account and no mod.
cargo build --release
./target/release/void-pvp prepare
./target/release/void-pvp launch --offline YourName

# 3. Build the binding, then the mod, then launch with it.
mod/native/scripts/build.sh                       # Windows: mod\native\scripts\build.ps1
cd mod && ./gradlew build && ./gradlew platformJars && cd ..
./target/release/void-pvp launch --offline YourName \
    --mod-jar mod/build/libs/void-client-0.1.0-<os>-<arch>.jar

# 4. The launcher itself.
pnpm --filter @void/desktop tauri dev
```

**[`docs/TESTING.md`](docs/TESTING.md) is the long form of that list** — every command for
both PowerShell and zsh, the exact log lines that mean success, and what each failure
looks like. Start there for a first run on real hardware.

---

## Reading order

`CONTRACTS.md` first (who owns what, and where the seams are), then the README of whatever
you are touching:

- [`schema/README.md`](schema/README.md) — the four contracts and the changelog of every
  change to them
- [`crates/README.md`](crates/README.md) — the Rust side and the `void-pvp` CLI
- [`apps/desktop/README.md`](apps/desktop/README.md) — the launcher
- [`packages/protocol/README.md`](packages/protocol/README.md) — generated types, the
  bridge shim, the fake bridge
- [`packages/ui/README.md`](packages/ui/README.md) — components, tokens, the two renderer
  modes
- [`packages/ingame/README.md`](packages/ingame/README.md) — the HUD and menu layers, the
  `?debug` harness
- [`mod/README.md`](mod/README.md) — the Legacy Fabric mod
- [`mod/native/README.md`](mod/native/README.md) — the Ultralight binding, the GL 2.1
  driver, and its known risks
- [`design/README.md`](design/README.md) — the 11 Figma frames, region by region

---

## Licences and credits

**Ultralight.** Its `LICENSE.txt` §4.4 requires this legend in the credit section of any
product that ships it. Copied from the SDK's `license/NOTICES.md`; the full text also
ships to disk as `natives/<os>-<arch>/resources/NOTICES.md`, and the short form is
available in code as `Ultralight.licenceNotice()` — use one of those, don't retype it:

> **Ultralight (c) 2024 Ultralight, Inc. All rights reserved. Ultralight is a trademark of
> Ultralight, Inc.**
>
> This software may include portions under the following licenses: WebKit (LGPL 2.1 and BSD),
> brotli (MIT), cURL (MIT-style), FreeType (BSD-style), Harfbuzz (MIT-style), mimalloc (MIT), ICU
> (ICU), libjpeg-turbo (BSD-style), libpng (zlib), libressl (BSD-style), libxml2 (MIT-style),
> libxslt (MIT), nghttp2 (MIT), skia (BSD), SQLite (public domain), zlib (zlib).
>
> All WebKit modifications are published open-source under LGPL 2.1 at
> <https://github.com/ultralight-ux/WebCore>.

Ultralight's free tier is $0 while last-fiscal-year turnover **and** total funding raised
are both under US$100k; crossing either starts a 30-day window to buy Pro
($3,000/yr per application) or distribution rights terminate (§13).

**Fonts.** The face Ultralight's bundled font loader serves is **Inter**, SIL Open Font
License 1.1, shipped with its `OFL.txt` alongside. The UI's own three families —
Bricolage Grotesque, Outfit, DM Mono — are also OFL 1.1 and ship with
`OFL-<family>.txt` in `@void/ui`.

The client itself is unlicensed and private (`publish = false`, every package `private`).

---

## Verified in CI vs. needs a real machine

CI is [`.github/workflows/pvp-ci.yml`](../.github/workflows/pvp-ci.yml). It has no display
and no GPU, which draws the line sharply.

| Area | Verified in CI | Needs a real machine |
|---|---|---|
| **Schemas** | All four compile; every `examples` entry validates; registry ↔ enum, category, label and `init.loadouts` cross-checks | — |
| **Rust core** | `cargo build/test/clippy -D warnings`; manifest rules, classpath and JVM-arg construction, the loadout store and diff, the WS handshake | Windows and macOS code paths (natives classifiers, `-XstartOnFirstThread`, JVM discovery); Microsoft sign-in (needs an Azure app); the OS keychain |
| **Launcher** | `cargo check` with and without Tauri features; the command layer's tests; `pnpm typecheck/test/build` | The window itself, tray, hide-to-tray, `⌘↵`, a real launch, `pnpm tauri build` bundling, the updater |
| **Shared UI** | Component tests; the renderer-token assertions | How it actually looks in Ultralight, and in a system webview |
| **In-game bundle** | Tests against the real fake bridge; the 22-rule Ultralight guard over source *and* emitted bundle; the 400 KB gzip budget | Rendering inside Ultralight; input forwarding coordinates; the ⌘K palette and HUD editor under a real cursor |
| **Mod** | Compiles against remapped 1.8.9, Java 8 bytecode; all five Mixins remap to real intermediary names; JUnit over the protocol codec, bridge, `LiveState`, diff, registry, sensors and actuators | Whether the Mixins *behave*; the WS handshake against the real server; anything touching GL |
| **Binding** | Builds for linux-x64, windows-x64, macos-x64 and macos-arm64; the CPU-renderer test (layout, `@font-face` off the classpath, `evaluateScript`, the `__void_native` round trip, `readPixels`, transparency, top-left origin) | **The whole OpenGL driver** — not one GL call has ever executed. GLSL 1.20 shader compilation, macOS GL 2.1, state restoration against MC's renderer, paint cost |
| **Packaging** | Per-OS `void-client-<version>-<os>-<arch>.jar` assembled from the staged natives trees | Installing one and running it; Gatekeeper on unsigned natives |

The first thing to do with a real game is **M1**: launch, create one accelerated view, and
look for `gpu: GLSL 1.20 driver ready` in the log. [`docs/TESTING.md`](docs/TESTING.md)
step 4 is that gate, written out.
