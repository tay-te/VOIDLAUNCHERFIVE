# `crates/` — the Rust side of void-pvp

Three crates, owned by **core** (see [`../CONTRACTS.md`](../CONTRACTS.md)). They implement
the launcher half of `docs/PVP_ARCHITECTURE.md`: §7 (the protocol), §8 (loadouts), §11
(the HYPIXEL-READY rule) and §12 (auth and launch).

```
void-loadout  ──▶  void-bridge  ──▶  void-core  ──▶  bin/void-pvp
  schema types       WS server        launch path      the CLI
```

Nothing here depends on Tauri, and `void-core` **must never gain that dependency** (§4).
That is what buys the CLI below, and what lets the launch path be tested without a
webview.

---

## `void-loadout`

The Rust side of `schema/mods.json` and `schema/loadout.json`, plus the store.

| Module | What it is |
|---|---|
| `mods` | The closed registry of 12 mods: `ModId`, `Kind`, `HypixelSafe`, a typed settings struct per mod, and the factory defaults. `schema/mods.json` is compiled in with `include_str!` and its `examples[0]` *is* the shipped registry — there is no second copy to drift. |
| `loadout` | `Loadout`, `ModStates`, `HudItem`/`Anchor` (anchor + `dx`/`dy` + `scale`, never pixels, §8.1), `LoadoutStats`, and `hypixel_ready`. |
| `settings` | `GlobalSettings` — `protocol.json#/definitions/global_settings`. It lives here because it is persisted as well as sent; unknown keys survive a round trip via `#[serde(flatten)]`, which is the one place the schemas allow extra keys. |
| `keybind` | `Keybind` and `HexColor`: validating newtypes for the two string formats the schema pins with a regex. |
| `diff` | `diff` / `diff_split` → `Vec<Change>` split into gameplay (`mods.*`, the `state` message) and HUD (the `hud` message); `StatePatch` and `apply_patch` for the flat `mods.<id>.<key>` paths. |
| `store` | `~/.void-pvp/loadouts/*.json` + `active.json` + `settings.json`, every write atomic (temp file, fsync, rename). Seeds the three default loadouts on first run. |
| `defaults` | **Sword PvP** (sword, hypixel), **Bedwars** (bed, hypixel), **UHC** (shield, minemen) — the three cards on the Figma Loadouts frame. |

An omitted mod falls back to its registry defaults, which is what keeps an old loadout
valid when a mod is added. `diff` compares *effective* settings, so "explicitly set to the
default" and "omitted" are the same thing.

## `void-bridge`

The localhost WebSocket server the mod connects back to (§6.9). Binds `127.0.0.1:0` — an
OS-assigned port — and mints a fresh 32-byte hex session token per spawn; `void-core`
passes both to the JVM as `-Dvoid.port` and `-Dvoid.token`.

- `JavaToRust` / `RustToJava`: every message in `schema/protocol.json`, tagged on `t`.
  Unknown `t` values land in `JavaToRust::Unknown` and unknown fields are ignored, never
  an error (§7) — no type here uses `deny_unknown_fields`.
- Handshake: the first frame must be `hello` with a matching token and `v == 1`, or the
  socket is closed with a policy close frame and nothing is disclosed.
- Inbound messages fan out on a `tokio::sync::broadcast` bus (`subscribe()`); outbound go
  through `send()`.
- Reconnect-tolerant: a new `hello` takes over the link and the previous connection closes
  itself, so the mod's backoff loop needs no cooperation.

## `void-core`

| Module | What it does |
|---|---|
| `auth` | Microsoft device-code OAuth → Xbox Live → XSTS → Minecraft services → profile. The Azure client id is **configuration** (`VOID_MS_CLIENT_ID`, or `ms_client_id` in `config.json`), never compiled in. The refresh token goes in the OS keychain, falling back to a `0600` file with a warning. `Session::offline(name)` skips the whole chain. |
| `manifest` | Mojang `version_manifest_v2` → the 1.8.9 version JSON → libraries with OS rules and natives classifiers, the asset index and the client jar; Legacy Fabric loader + intermediary from `meta.legacyfabric.net/v2`, merged into one `LaunchProfile`. Pure — the network is in the `fetch_*` functions. |
| `download` | 16-way parallel fetches, SHA-1 verified, cached under `cache/objects/<ab>/<sha1>`, progress over an `mpsc` channel. |
| `java` | Finds a Java 8 in `JAVA_HOME`, on `PATH` and in the per-OS install directories; otherwise fetches Adoptium Temurin 8. **Apple Silicon gets the x64 build on purpose** — LWJGL 2 has no arm64 natives, so 1.8.9 runs under Rosetta and the JVM has to match (§13). |
| `launch` | Classpath, LWJGL 2 natives extraction, JVM args (`-Xmx`, `-Djava.library.path`, `-Dvoid.port`, `-Dvoid.token`, `-XstartOnFirstThread` on macOS), the mod JAR into `mods/`, spawn, stdout/stderr streamed line by line, exit code. The argument list is cached under `cache/args/<hash>.json`, keyed by profile hash. |
| `sync` | Answers `init` from the live store, and folds inbound `state` / `hud` / `session` back into it. Java is authoritative while the game runs; this is the "and tells Rust afterwards" half of §6.1. |
| `install` | `prepare`: resolve, download the profile's files, then every asset object. |

### Layout on disk

```
~/.void-pvp/                    ($VOID_PVP_HOME overrides the whole root)
  config.json  credentials.json  profile.json
  loadouts/  active.json  settings.json      ← void-loadout
  versions/1.8.9/{1.8.9.jar, *.profile.json}
  libraries/  assets/{indexes,objects}/  natives/<profile-id>/
  java/temurin8-<os>-<arch>/
  cache/objects/  cache/args/
  game/                          ← the game directory; mods/ lives here
```

---

## Running the CLI

Build once:

```sh
cd pvp
cargo build --release          # target/release/void-pvp[.exe]
```

### Windows

```powershell
# 1. Download 1.8.9 + Legacy Fabric + assets, and a Java 8 if you have none.
#    ~380 MB into %USERPROFILE%\.void-pvp
.\target\release\void-pvp.exe prepare

# 2. Launch offline — no Microsoft account needed.
.\target\release\void-pvp.exe launch --offline YourName

# with the client mod, once mod/ has built one:
.\target\release\void-pvp.exe launch --offline YourName `
    --loadout sword-pvp `
    --mod-jar .\mod\build\libs\void-client-0.1.0.jar
```

### macOS

```sh
./target/release/void-pvp prepare
./target/release/void-pvp launch --offline YourName

# Apple Silicon: nothing extra to do. `prepare` fetches the x64 Temurin 8 and macOS runs
# it under Rosetta, because LWJGL 2 ships no arm64 natives (§13). Install Rosetta once
# with `softwareupdate --install-rosetta` if you have never run an Intel binary.
```

### Everything else

```sh
void-pvp login                       # Microsoft device code; needs VOID_MS_CLIENT_ID
void-pvp whoami
void-pvp logout
void-pvp loadouts list
void-pvp loadouts show sword-pvp
void-pvp loadouts switch bedwars
void-pvp launch --loadout uhc        # signed-in launch
void-pvp launch --offline Dev --skip-prepare   # no network; uses the last `prepare`
void-pvp -v launch --offline Dev     # -v debug, -vv trace
```

`launch` starts the bridge **before** the JVM and prints the port it got:

```
Loadout: Sword PvP (10 mods on)  ·  HYPIXEL-READY
Playing as VoidTester (offline)
Bridge listening on ws://127.0.0.1:38277 (token 9c16c393…)
Minecraft started (pid Some(2899))
```

### Configuration

`~/.void-pvp/config.json`, all keys optional:

```json
{
  "ms_client_id": "00000000-0000-0000-0000-000000000000",
  "java_path": null,
  "max_memory_mb": 2048,
  "jvm_args": [],
  "mod_jar": null
}
```

`VOID_MS_CLIENT_ID` overrides `ms_client_id`; `VOID_PVP_HOME` overrides the root. **VOID
ships no Azure client id**: register an application with the device-code flow enabled and
put its id here. `--offline` needs none of this.

---

## Tests

```sh
cargo test --workspace                                  # everything below
cargo test -p void-core --test network -- --ignored     # the network-dependent ones
cargo clippy --workspace --all-targets -- -D warnings
```

Schema conformance is a test, not a convention: every `examples` entry in
`protocol.json`, `loadout.json` and `mods.json` is deserialized, re-serialized and
compared, and the `mod_id` / `hud_mod_id` / `gameplay_mod_id` enums are checked against
the registry — the Rust equivalent of `schema/validate.mjs`, which belongs in CI beside it.

### Verified here

- [x] `cargo build --workspace`, `cargo test --workspace`, `cargo clippy --workspace --all-targets -- -D warnings` — all green.
- [x] Round trip of every `examples` entry in all three schemas; registry ↔ enum cross-checks.
- [x] Loadout store: first-run seeding, atomic writes, switch, delete, L-cycle order, stats accumulation.
- [x] `diff` / `apply_patch`: path parsing, defaults materialisation, rejection of unknown keys and bad types, patch↔diff round trip.
- [x] `hypixel_ready` on all three default loadouts.
- [x] Bridge: handshake, `init`, `state` on the bus, outbound delivery, bad token, `v` mismatch, non-`hello` first frame, reconnect replacing the client, unknown `t` and junk frames not dropping the link.
- [x] Manifest: rules (allow/disallow/OS/arch), natives classifiers with `${arch}`, Maven paths, loader-first merge with `group:artifact` shadowing, natives-only libraries, per-platform library sets, profile-id sanitisation, asset-index expansion — against a vendored trimmed 1.8.9 JSON and a Legacy Fabric profile.
- [x] Launch: classpath order, `-XstartOnFirstThread` only on macOS, `-Dvoid.port`/`-Dvoid.token`, full placeholder substitution, argument-cache hit/miss, natives unzipped without `META-INF`, mod JAR installed, stdout+stderr streamed, exit code — spawned against a stand-in for `java`.
- [x] **A real `prepare` on Linux**: 722 files, 114.7 MB, 39 libraries, 2 natives jars, plus Adoptium Temurin 8 (`1.8.0_504`) fetched and detected.
- [x] **A real launch on Linux**: Minecraft 1.8.9 booted through Fabric Loader 0.19.3 and Mixin 0.8.7, loaded LWJGL `2.9.4+legacyfabric.17` from the extracted natives, set the offline user — and stopped at `Could not open X display connection`, which is the container, not the code.

### Not verified here

- [ ] **Windows and macOS.** This machine is Linux x64. The per-OS code paths (natives classifiers, `-XstartOnFirstThread`, JVM search directories, Adoptium archive format) are unit-tested with a forced `RuleContext`, but nothing has been *run* on either platform. macOS in particular: Rosetta, and whether Gatekeeper objects to the unsigned natives (§16.5).
- [ ] **Microsoft sign-in.** The whole `auth` chain is untested against live endpoints: no Azure application exists yet, so `login`, `refresh` and the XSTS error paths have never executed. The shapes come from the documented flow.
- [ ] **The OS keychain.** `keyring` compiles per platform (`windows-native`, `apple-native`, `linux-native`) but has not been exercised; only the file fallback is tested.
- [ ] **The bridge against the real mod.** `mod/net/` does not exist yet. The handshake is tested against a client written here, which is the same protocol but not the same implementation.
- [ ] **Actually playing.** No display in this container, so nothing past `Initializing game` has run: no world, no HUD, no `state` messages from a real session.
- [ ] **`os.version` rule clauses** are ignored rather than evaluated — 1.8.9 uses none, and adding a regex engine for a version we do not target was not worth it. Revisit before targeting anything but 1.8.9.
