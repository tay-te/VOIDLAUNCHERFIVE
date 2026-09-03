# Testing VOID PVP on a real machine

Nothing in CI has a display or a GPU, so a large and load-bearing part of this client has
never run: the OpenGL driver, the Mixins' actual behaviour, the launcher window, and a
real Minecraft launch. This page is the script for the first evening on real hardware.

**It is ordered so that each step proves one layer and the earliest steps are the ones
most likely to work.** Do them in order. If step 3 fails there is no point attempting step
4, and knowing *which* step broke is most of the diagnosis.

Every step gives the commands twice — **Windows PowerShell** and **macOS zsh** — plus what
success looks like (exact log lines), what failure looks like, and what to do about it.
Paths are relative to `pvp/` unless stated otherwise.

| Step | Proves | Time |
|---|---|---|
| [1. Clone + toolchain](#1-clone--toolchain-check) | Everything compiles and every unit test passes on your machine | 10 min |
| [2. Browser-only UI review](#2-browser-only-ui-review) | The design, in a real renderer, with no game | 15 min |
| [3. CLI launch, no account, no mod](#3-cli-launch-without-an-account) | Auth-free launch: manifests, downloads, Java, JVM spawn | 20 min |
| [4. The binding, the mod, the M1 gate](#4-the-native-binding-the-mod-and-the-m1-gate) | **The whole reason the architecture is shaped this way** | 45 min |
| [5. The Tauri launcher](#5-the-tauri-launcher) | The product as a player meets it | 20 min |
| [6. Microsoft sign-in (optional)](#6-optional-microsoft-sign-in) | The one path that needs an Azure app | 15 min |

---

## 1. Clone + toolchain check

Install the prerequisites from [`../README.md`](../README.md#prerequisites) first —
Rust, Node 22, pnpm, a JDK 17+, CMake, and the platform C++ toolchain (VS Build Tools 2022
with the C++ workload / Xcode Command Line Tools).

**PowerShell**

```powershell
git clone <this repo> VOIDLAUNCHERFIVE
cd VOIDLAUNCHERFIVE\pvp
corepack enable
pnpm install
.\scripts\verify-all.ps1
```

**zsh**

```sh
git clone <this repo> VOIDLAUNCHERFIVE
cd VOIDLAUNCHERFIVE/pvp
corepack enable
pnpm install
scripts/verify-all.sh
```

`verify-all` runs every unit-test suite in the repo in one pass: the four JSON schemas and
their cross-checks, `cargo build` / `test` / `clippy -D warnings` over the three crates,
`cargo check` + `cargo test` for the Tauri crate, `pnpm -r typecheck` / `test` / `build`,
the Ultralight constraint guard, the 400 KB in-game bundle budget, `./gradlew build` (which
includes the mod's JUnit suite), and — if you have already configured `mod/native` — the
binding's CPU-renderer test. Add `--keep-going` / `-KeepGoing` to see every failure instead
of stopping at the first, and `--with-native` / `-WithNative` to also CMake-build the
binding (that downloads the Ultralight SDK the first time).

**Success looks like** a summary table where every row says `PASS`:

```
══ summary
PASS  schema examples + cross-checks                     0s
PASS  cargo build --workspace                            1s
…
PASS  gradlew build (compile + JUnit suite)           2s
PASS  ctest cpu_render                                   2s

All checks passed.
```

**Failure, and what to do**

| Symptom | Cause | Fix |
|---|---|---|
| `schema examples + cross-checks skipped: ajv not resolvable` | `schema/` is not a workspace package | `cd schema; npm i --no-save ajv` — or ignore it; CI covers this |
| `linker 'link.exe' not found` | No MSVC toolchain | Install VS Build Tools 2022 with *Desktop development with C++* |
| Gradle: `Unsupported class file major version` / `requires Java 17` | Gradle is running on a JDK older than 17 | Point `JAVA_HOME` at a 17+ JDK. The mod still compiles to Java 8 bytecode |
| First `./gradlew build` takes minutes | Loom is downloading 1.8.9, yarn `1.8.9+build.604` and its caches (~150 MB) | Expected once. Later builds are seconds |
| `ctest cpu_render skipped` | `mod/native` has never been configured | Fine here — step 4 builds it |

If everything passes, the machine is set up correctly and nothing below is a toolchain
problem.

---

## 2. Browser-only UI review

Three surfaces run in an ordinary browser with no Rust, no JVM and no game. This is the
fastest way to see the design, and the only place with devtools.

> All three dev servers want a port near 5183 and `@void/desktop`'s uses `--strictPort`.
> Run **one at a time**, or the second will fail to bind or silently take another port.

### 2a. The component gallery — `@void/ui`

**PowerShell / zsh** (identical)

```sh
pnpm --filter @void/ui gallery      # http://localhost:5177
```

Every component in the design inventory, in every state, with a **renderer toggle** in the
header. Flip it between `webview` and `ultralight`: the second is the token layer the
in-game bundle gets — no `backdrop-filter`, no noise grain, solid selection borders. Both
must look right; the ultralight column is the one that matters, because that is what
players see over the game.

Live specimens (keystrokes, FPS chip, ping chip) animate off `createFakeVoid({ seed: 42 })`
— the same fake bridge the in-game harness uses.

**Success:** the page renders on the dark `ground` background, the toggle visibly changes
panel opacity and the grain, and nothing is unstyled.
**Failure:** unstyled text means `tokens.css` did not load — check the terminal for a Vite
resolve error and re-run `pnpm --filter @void/ui build`.

### 2b. The in-game bundle — `@void/ingame`

```sh
pnpm --filter @void/ingame dev      # then open http://localhost:5183/?debug
```

`?debug` forces the fake bridge and opens the menu. The harness renders at the authored
**1300 × 820** frame size with the matching Figma export behind the UI, so it can be
compared 1:1 with `design/screens/*.png`.

Work through, in this order:

| Do this | Should happen |
|---|---|
| Look for the **DEBUG** badge | It marks the fake bridge. It never appears in game |
| Press **Right Shift** | The menu opens and closes. (In game this is Java's `KeyBinding`; here the fake bridge plays Java) |
| Press **⌘K** / **Ctrl-K** | The quick palette opens over whatever screen is up; typing `fullb` ranks Fullbright first |
| Arrow keys, then **Enter** | Walks the 3-column mod grid and toggles the highlighted mod |
| Double-click a tile | Opens that mod's settings screen |
| In a mod's settings, click **Edit position** | Enters the HUD editor: drag, snap to grid, scale, and the selection frame reads out `anchor + dx/dy + scale` |
| **Esc** | Leaves a focused text field first, then the HUD editor, then the menu |
| Close the menu, then hold **W/A/S/D**, click, press **space** | The keystrokes widget lights up and the CPS counter moves — real input becomes `keys` events |
| Press **L** with the menu closed | Cycles loadouts |
| `?debug&screen=Overlay-Loadouts.png` | Puts a different Figma export behind the UI |
| `?debug&glblur=off` | Drops `data-glblur`, so panels paint at the denser no-GL-blur alpha |

**Failure:** a blank page with a console error about `window.void` means the fake bridge
did not install — confirm the URL still carries `?debug`. A screen that renders but does
not respond to Right Shift usually means the keyboard is attached to a focused input.

### 2c. The launcher screens — `apps/desktop`

```sh
pnpm --filter @void/desktop dev:web    # http://127.0.0.1:5183
```

`@tauri-apps/api` is aliased to a mock, so every screen, every launch phase and every error
state renders with fixture data and no Rust at all. A banner on the canvas says you are in
the preview.

**Success:** Play, Mods, Cosmetics, Servers, Friends and Settings all render; the launch
button walks through its progress states.
**Failure:** if it renders but IPC calls throw, the alias did not apply — make sure
`TAURI_ENV_PLATFORM` is *not* set in that shell.

---

## 3. CLI launch without an account

This is the first step that touches the real game. It proves manifests, parallel SHA-1
verified downloads, the Java 8 runtime, JVM argument construction and the spawn — with
**no Microsoft account and no mod**. Expect ~380 MB into `%USERPROFILE%\.void-pvp` /
`~/.void-pvp`.

**PowerShell**

```powershell
cd VOIDLAUNCHERFIVE\pvp
cargo build --release
.\target\release\void-pvp.exe prepare
.\target\release\void-pvp.exe launch --offline YourName
```

**zsh**

```sh
cd VOIDLAUNCHERFIVE/pvp
cargo build --release
./target/release/void-pvp prepare
./target/release/void-pvp launch --offline YourName
```

On **Apple Silicon** there is nothing extra to configure: `prepare` fetches the **x64**
Temurin 8 on purpose, because LWJGL 2 ships no arm64 natives and 1.8.9 runs under Rosetta
(§13). If you have never run an Intel binary on that Mac, install Rosetta once:
`softwareupdate --install-rosetta`.

**Success looks like** — `prepare`:

```
Installing into /Users/you/.void-pvp
Fetching 722 files (114.7 MB)
Ready: <legacy-fabric-profile-id> for macos-x64 (39 libraries, 2 natives jars, main class net.fabricmc.loader.impl.launch.knot.KnotClient)
Java 1.8.0_504 at /Users/you/.void-pvp/java/temurin8-macos-x64/…
```

and `launch`:

```
Loadout: Sword PvP (10 mods on)  ·  HYPIXEL-READY
Playing as YourName (offline)
Bridge listening on ws://127.0.0.1:38277 (token 9c16c393…)
Minecraft started (pid Some(2899))
```

then Minecraft's own log, ending at the main menu. **Vanilla 1.8.9 with Legacy Fabric
loaded and no VOID UI at all is the correct result here** — the mod is not installed yet.
Look for Fabric's own banner in the log (`Loading Minecraft 1.8.9 with Fabric Loader
<version>`) to confirm the loader is in — `void-core` resolves the newest stable Legacy
Fabric loader, so the version there is whatever `meta.legacyfabric.net` currently offers and
need not match `mod/gradle.properties`.

Useful extras:

```sh
void-pvp loadouts list
void-pvp loadouts switch bedwars
void-pvp launch --offline Dev --skip-prepare    # no network; uses the last prepare
void-pvp -v launch --offline Dev                # -v debug, -vv trace
```

**Failure, and what to do**

| Symptom | Meaning | Do this |
|---|---|---|
| `Could not open X display connection` | Headless machine. This is exactly where CI stops | Run it on a desktop session |
| Hangs or fails during `Fetching … files` | Mojang/Adoptium reachability, or a proxy | Re-run `prepare`; it is idempotent and skips verified files |
| `no Java 8 found` after `prepare --no-java` | You skipped the runtime fetch | Re-run `prepare` without `--no-java`, or set `java_path` in `~/.void-pvp/config.json` |
| Bad CPU type / "cannot execute binary" on macOS | No Rosetta on Apple Silicon | `softwareupdate --install-rosetta` |
| Crash with an LWJGL/natives error | Natives extraction | Delete `~/.void-pvp/natives/` and re-run |
| Game opens but the window is tiny/black on macOS | Known 1.8.9 + Rosetta behaviour | Resize once; report it if it persists |

Report the exact `Ready:` and `Bridge listening on` lines — they carry the profile id, the
platform key and proof the WS server bound.

---

## 4. The native binding, the mod, and the M1 gate

**This is the step the whole architecture is betting on.** The OpenGL `GPUDriver` in
`mod/native/src/gpu_driver_gl.cpp` has never executed a single GL call anywhere: it
compiles, and that is all that has ever been claimed. Everything below is new information.

### 4a. Build the binding

**PowerShell**

```powershell
cd VOIDLAUNCHERFIVE\pvp\mod\native
.\scripts\build.ps1
```

**zsh**

```sh
cd VOIDLAUNCHERFIVE/pvp/mod/native
scripts/build.sh                 # host arch
scripts/build.sh --arch x86_64   # Apple Silicon: ALSO build this one — see below
```

CMake downloads the pinned Ultralight SDK revision `081c48b` (1.4.0b), verifies its
SHA-256 and extracts it into `mod/native/sdk/`. Nothing else needs installing.

> **On Apple Silicon, build `x86_64` too, and prefer it.** The game runs on an x64 JVM
> under Rosetta, so the JVM can only load the **macos-x64** natives. `macos-arm64` is a
> bonus tree, not the one that runs. Ultralight ships separate per-arch SDKs and no fat
> binaries, so this is two builds, into `build-x86_64/` and `build-arm64/`.

**Success:** `natives staged in: …/build/natives/<os>-<arch>` followed by a file list
containing `voidultralight.{dll,dylib}`, `WebCore`, `Ultralight`, `UltralightCore`,
`resources/` and `files.txt`.

Then run the CPU-renderer smoke test — 21 checks against the real engine, no display
needed:

```sh
ctest --test-dir build --output-on-failure     # macOS/Linux; add -C Release on Windows
```

**Success:** `PASSED`, and `test/expected/out.png` is a transparent-background 800 × 480
view holding a card with `border-radius`, a gradient, a `box-shadow`, a webfont and a
completed CSS transition. Open it and look at it.
**Failure:** any check failing here means the engine or the classpath filesystem is wrong
on your platform — report the whole output, it is short.

### 4b. Build the in-game bundle, then the mod

The JAR carries the UI as a resource, so **build the web bundle first** or the view will
load nothing in game.

**PowerShell**

```powershell
cd ..\..                       # back to pvp\
pnpm --filter @void/ingame build
cd mod
.\gradlew.bat build
.\gradlew.bat platformJars
```

**zsh**

```sh
cd ../..                       # back to pvp/
pnpm --filter @void/ingame build
cd mod
./gradlew build
./gradlew platformJars
```

`build` produces the base `build/libs/void-client-0.1.0.jar` — 324 KB, **no natives**.
`platformJars` repackages it once per staged natives tree into
`void-client-0.1.0-<os>-<arch>.jar`.

**Success:** `void-client: per-OS JARs for windows-x64` (or `macos-x64, macos-arm64`).
**Failure:** `void-client: no Ultralight natives staged, so no per-OS JARs.` means step 4a
did not stage where Gradle looks. It reads `native/build-win/natives/windows-x64`,
`native/build-macx64/natives/macos-x64`, `native/build-macarm64/natives/macos-arm64`, and
**any** directory under `native/build/natives/`. A macOS cross-configure lands in
`native/build-x86_64/`, so either copy that tree to `native/build-macx64/` or move it under
`native/build/natives/macos-x64/`.

### 4c. Launch with the mod — the M1 gate

**PowerShell**

```powershell
cd ..                          # pvp\
.\target\release\void-pvp.exe launch --offline YourName `
    --loadout sword-pvp `
    --mod-jar .\mod\build\libs\void-client-0.1.0-windows-x64.jar
```

**zsh**

```sh
cd ..                          # pvp/
./target/release/void-pvp launch --offline YourName \
    --loadout sword-pvp \
    --mod-jar ./mod/build/libs/void-client-0.1.0-macos-x64.jar
```

**What to look for in the log, in this order.** These four lines, together, are the gate:

```
[voidultralight/info] gpu: GLSL 1.20 driver ready (vao=1, texture_rg=1)
[void] Ultralight 1.4.0 (WebKit 615.1.18.100.1) ready
[void] in-game UI started at 1920x1080 (scale 2.0)
[void] void-client 0.1.0 ready
```

Plus, because the CLI started a bridge before the JVM:

```
[void] launcher link up on 127.0.0.1:38277
```

**Then, in game:**

| Do this | Should happen |
|---|---|
| Enter a world (singleplayer is fine) | HUD widgets appear where the loadout's `hud[]` places them: FPS, keystrokes, CPS, ping, coords, armour, potions |
| Press **Right Shift** | The Mods panel opens over a **blurred** game, mouse released, game still running (`doesGuiPauseGame()` is false) |
| Click a mod tile's switch | It toggles instantly and *stays* where you put it — the bridge is synchronous and returns the applied state |
| Press **Right Shift** again | The menu closes and the HUD is back |
| Press **L** with no menu open | Loadouts cycle; the HUD re-lays out in under a frame |
| Toggle **Fullbright**, **Hitboxes**, **Zoom** (hold C) | Each takes effect immediately |
| Resize the window, change GUI scale | The view resizes; HUD items keep their anchors, not pixel positions |
| Quit the game normally | It exits cleanly. Do **not** add a "graceful shutdown" that joins the render thread — see risk 4 below |

### Known-risk symptoms, straight from `mod/native/README.md`

Look for these specifically. They are the four things most likely to go wrong, and each has
a distinctive signature.

**1. The GLSL 1.20 port fails to compile or link.** The driver prints the driver's own info
log verbatim:

```
[voidultralight/error] shader fill failed to compile:
<the GLSL compiler's message>
[voidultralight/error] program fill failed to link:
```

and, if your GPU is at GL 2.0's floor for varyings:

```
[voidultralight/error] gpu: GL_MAX_VARYING_FLOATS is 32, the fill shader needs 36 — expect a link failure
```

The `fill` program needs 9 `vec4` of varyings. Every GPU that can run 1.8.9 reports 64+ in
practice; a machine that does not is exactly what we need to hear about. Apple's GLSL
compiler is also stricter than Mesa's about constant-index-expressions. **Report the entire
compiler message** — it names the line.

Related: `[voidultralight/error] gpu: OpenGL entry points unavailable — is a context current
on this thread?` means the driver initialised off the render thread.

**2. State leakage into Minecraft's renderer.** The symptom is *not* a crash: it is
**corrupted world rendering after the first HUD paint** — untextured blocks, wrong
blending, a missing sky, the hotbar drawn in the wrong colours. `save_gl_state` /
`restore_gl_state` is thorough, but it is a list and lists have holes. **Suspect this first
for any "the game looks wrong since the mod" report.** Take a screenshot of the corruption
and say what you had just done (opened the menu? toggled a mod? resized?).

**3. The font loader.** `ulPlatformSetFontLoader` is declared but not exported by 1.4.0b,
so the binding installs a C++ `FontLoader` through the `Platform` singleton instead. If it
is missing, `ulCreateView` fails with *"tried to create a View but FontLoader was NULL"*
and you will see `[void] Ultralight failed to link, in-game UI disabled: …`. Second-order
symptom: the loader serves **one bundled face (Inter) for every family**, so text that
looks like Inter where the design wanted something else means a missing `@font-face`, not a
loader bug.

**4. WebCore aborts when a thread that touched it terminates.** Minecraft quits through
`System.exit()`, which sidesteps this entirely. If the process aborts *on quit* with a
stack through `WebCore::ThreadGlobalData::~ThreadGlobalData` / `WTFCrashWithInfo`, capture
it — it means something is letting the render thread die on its own.

**Other lines worth knowing**

| Line | Means |
|---|---|
| `[void] Ultralight failed to link, in-game UI disabled: …` | `NullWebView` took over: this JAR has no natives for your platform, or the wrong per-OS JAR. The game keeps running with no VOID UI. Check the JAR classifier |
| `[void] -Dvoid.port / -Dvoid.token not set; running without the launcher link` | The mod is running on registry defaults with no launcher. Expected under `./gradlew runClient` |
| `[void] no framebuffer objects available; menu backdrop will be a flat tint` | No FBO support — the blur degrades to a flat tint. Fidelity loss, not a crash |
| `[void] no GLSL available; menu backdrop will be a flat tint` | Same, for the shaders |
| `[void] menu backdrop blur failed, falling back to a flat tint: …` | The blur pass threw at runtime |
| `[void] launcher link closed; reconnecting` | The WS dropped; backoff is working |
| `[void] protocol mismatch: launcher vN, mod vM` | The two halves were not shipped together |

### What to report back from step 4

Whether or not it worked: the four gate lines (or the first one missing), the full text of
any `[voidultralight/error]`, a screenshot of the HUD in a world, a screenshot of the
Right-Shift menu over the blurred game, and — if the world looks wrong — a screenshot of
that with what you had just done.

---

## 5. The Tauri launcher

**PowerShell / zsh** (identical)

```sh
cd VOIDLAUNCHERFIVE/pvp
pnpm --filter @void/desktop tauri dev
```

The first run compiles the Rust half and takes a few minutes; after that it is seconds.
On Windows this is the step that needs **WebView2**; on macOS, the Xcode Command Line
Tools.

Work through:

1. **The window.** Frameless, custom drag region, correct at your DPI. Nothing in CI has
   ever rendered it.
2. **Play offline.** The gear → *Account* → *Play offline*, take a name, then **Launch**
   (or `⌘↵` / `Ctrl+↵`). Progress streams into the button as a bar with a step label and a
   MB/s readout. It should reach the same game step 3 reached — this time with the mod, if
   a per-OS JAR is configured.
3. **Hide to tray.** After a successful spawn the window hides (configurable in Settings).
   The tray menu should offer *Show launcher · Switch loadout ▸ · Quit*.
4. **Switch loadout from the tray while the game runs.** The running game should hot-swap —
   the HUD re-lays out — rather than only affecting the next launch. This is the `loadout`
   message going Rust → Java.
5. **Press L in game**, then look at the tray and the launcher's active loadout. They
   should follow the game (that is the `hotkey` notification), not contradict it.
6. **Close the game.** The window comes back with played time and average fps.
7. **Settings.** Java path, RAM, hotkeys, theme, data folder → *Open* reveals
   `~/.void-pvp`.

**Failure, and what to do**

| Symptom | Meaning |
|---|---|
| Blank white window on Windows | WebView2 runtime missing — install the Evergreen Runtime |
| `pnpm tauri dev` fails at `cargo build` on Linux | Missing `libwebkit2gtk-4.1-dev` and friends (Linux is dev-only) |
| Tray icon missing on macOS | Report it — tray behaviour has never been exercised |
| The updater tries to reach `updates.void.invalid` | Expected. `.invalid` is reserved by RFC 2606 and can never resolve; a dev build cannot be talked into installing anything |
| `pnpm tauri build` complains about icons | `src-tauri/icons/` holds PNGs only. Run `pnpm tauri icon <source.png>` on a real machine |

---

## 6. Optional: Microsoft sign-in

Five steps, and only needed if you want to play on online-mode servers. VOID ships **no**
Azure client id on purpose — an application registration is per-publisher and would be a
credential in the repository.

1. In the [Azure portal](https://portal.azure.com) → **Microsoft Entra ID** → **App
   registrations** → **New registration**. Any name; account type *Personal Microsoft
   accounts* (or multi-tenant + personal).
2. Under **Authentication** → **Add a platform** → **Mobile and desktop applications**, and
   set **Allow public client flows** to **Yes** — the launcher uses the *device code* flow,
   which needs it.
3. Copy the **Application (client) ID** from the overview page.
4. Export it as `VOID_MS_CLIENT_ID` (PowerShell: `$env:VOID_MS_CLIENT_ID="<id>"`; zsh:
   `export VOID_MS_CLIENT_ID=<id>`), or put it in `~/.void-pvp/config.json` as
   `"ms_client_id"`.
5. Run `void-pvp login` — it prints a short code and a URL, waits while you finish in the
   browser, then `void-pvp whoami` should name your account. The refresh token goes to the
   OS keychain (falling back to a `0600` file with a warning).

The whole chain — Microsoft → Xbox Live → XSTS → Minecraft services → profile — has never
executed against live endpoints, so **any** error here is new information, especially an
XSTS error code (2148916233 = no Xbox account; 2148916238 = child account).

---

## What to report

Copy this template. Fill in what you got to; "did not get this far" is a valid answer and
still tells us where the wall is.

```
## Environment
OS + version:            (e.g. Windows 11 23H2 / macOS 14.5)
CPU + arch:              (e.g. M2 Pro arm64 / Ryzen 7 5800X x64)
GPU + driver version:    (e.g. RTX 3070, 552.22 / Apple M2 integrated)
Display scale / DPI:     (e.g. 150% / Retina 2x)
Java used by the game:   (from `void-pvp prepare` output)
rustc / node / pnpm / JDK for Gradle:

## Step results
1. verify-all:              PASS / FAIL  (paste the summary table)
2. Browser UI review:       PASS / FAIL  (gallery, ?debug harness, dev:web)
3. CLI launch, no mod:      PASS / FAIL
4. Binding + mod (M1 gate): PASS / FAIL
5. Tauri launcher:          PASS / FAIL
6. Microsoft sign-in:       PASS / FAIL / not attempted

## The M1 gate, specifically
gpu: GLSL 1.20 driver ready line present?      yes / no  (paste it)
Ultralight … ready line present?               yes / no  (paste it)
HUD widgets visible in a world?                yes / no
Right Shift opens the menu over a blurred game? yes / no  (blurred, or flat tint?)
L cycles loadouts?                             yes / no
World rendering correct after the first HUD paint? yes / no

## Logs
Paste every line containing `[void]` or `[voidultralight/`, plus 20 lines of
context around any error or crash. Full stack traces, not summaries.

## Screenshots
- The HUD in a world
- The Right-Shift menu over the game
- Anything that looks wrong, with what you had just done

## Anything else
Frame rate with the mod on vs. off, if you measured it. Anything that felt slow,
flickered, or came back wrong after alt-tab or a resolution change.
```

Send it with the exact JAR you launched (`void-client-0.1.0-<os>-<arch>.jar`) and the
commit you built from.
