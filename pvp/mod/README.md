# `mod/` — the `void-client` Minecraft mod

The in-game half of VOID PVP: Legacy Fabric on **Minecraft 1.8.9**, Java 8 bytecode, with
the HUD and the Right-Shift menus rendered *inside Minecraft's own GL frame* by Ultralight
(PVP_ARCHITECTURE.md §6). Sensors read the game and push it to the UI over `window.void`;
actuators read a `LiveState` field every frame; a Netty WebSocket carries state — never
frames — to and from the Rust launcher.

```
src/main/java/dev/voidpvp/client/
├── VoidClient.java     entrypoint; owns the wiring and the frame/tick pump
├── state/              LiveState, Loadout, the mod registry, the hot-swap diff
├── bridge/             window.void — schema/bridge.json
├── net/                Netty WS client — schema/protocol.json
├── ui/                 the Ultralight host: view lifecycle, GL paint, input
├── screen/             VoidMenuScreen + the GL backdrop blur
├── mixin/              sensors and actuators, one class per injection point
├── sensor/             the plain, testable half of the sensors
├── actuator/           the plain, testable half of the actuators
├── render/             crosshair geometry, crosshair drawing, the textured quad
└── input/              LWJGL key names, edge-triggered hotkeys
native/                 NOT OURS — the JNI binding (see CONTRACTS.md)
src/main/resources/
├── fabric.mod.json     mod id `void`, client entrypoint
├── void.mixins.json    five mixins, JAVA_8
└── assets/void/
    ├── shaders/        the two-pass Gaussian for the menu backdrop (GLSL 1.20)
    ├── shim/           void-shim.js — the source of truth for the bridge shim
    └── ui/             packages/ingame's build output (gitignored, not ours)
```

## Build

Needs **JDK 17 or newer** to *run* Gradle (the mod itself is compiled to Java 8 bytecode
with `options.release = 8`; no Java 8 JDK is required). The Gradle wrapper is committed —
use it, not a system Gradle.

### macOS / Linux

```sh
cd pvp/mod
./gradlew build
```

### Windows

```bat
cd pvp\mod
gradlew.bat build
```

The JAR lands in `build/libs/void-client-<version>.jar`. First build downloads Minecraft
1.8.9, the Legacy Fabric yarn mappings and Loom's caches (~150 MB, a couple of minutes);
after that a clean build is seconds.

Useful targets:

| Command | Does |
|---|---|
| `./gradlew build` | compile, test, remap, JAR |
| `./gradlew test` | the JUnit suite only (no Minecraft needed) |
| `./gradlew clean build` | from scratch |
| `./gradlew vscode` / `eclipse` / `idea` | IDE run configs from Loom |

Versions live in `gradle.properties`: Minecraft `1.8.9`, yarn `1.8.9+build.604`,
fabric-loader `0.16.14`. The Loom plugin is Legacy Fabric's `legacy-looming 1.13.2`, which
wraps `fabric-loom 1.13` — **the newest Loom that still runs on Gradle 8**. Loom 1.14 and
later require Gradle 9; if you bump `legacy-looming` past 1.13.x you must bump the wrapper
too.

`legacy-fabric-api` is deliberately **not** a dependency. Everything is done with Mixin.

## Running it

The launcher builds the command line; there is nothing to install by hand.

1. `void-core` downloads `void-client-<version>.jar` and drops it in the instance's
   `mods/` directory alongside the Legacy Fabric loader.
2. It binds `ws://127.0.0.1:<port>`, mints a per-spawn session token, and spawns the JVM
   with **`-Dvoid.port=<port> -Dvoid.token=<token>`**.
3. The mod reads both properties on `onInitializeClient`, connects, sends `hello` with the
   token, and waits for `init` — which is the entire world of state it starts from. There
   are no config files on the Java side (§6.1).

Without those properties the mod logs one warning and runs anyway, on the registry
defaults. The game is never held hostage to the launcher.

To try it in a dev instance, Loom's `runClient` launches Minecraft with the mod loaded:

```sh
./gradlew runClient
```

That runs it *without* the launcher, on the registry defaults. To point a dev instance at
a running `void-bridge`, pass the two properties as JVM arguments of the run — add them to
the run configuration your IDE generated (`./gradlew vscode` / `eclipse` / `idea`), or
declare them in `build.gradle`:

```gradle
loom {
    runs {
        client {
            vmArg '-Dvoid.port=8787'
            vmArg '-Dvoid.token=0123456789abcdef0123456789abcdef'
        }
    }
}
```

## What is verified here, and what is not

Minecraft cannot run in this environment, so the line is sharp.

**Verified by `./gradlew build`:**

- The whole mod compiles against real remapped 1.8.9 (legacy yarn `1.8.9+build.604`) and
  emits Java 8 bytecode (class file major version 52, checked in the JAR).
- All five Mixins are remapped into intermediary by Loom — `InGameHud.render` →
  `method_9420`, `GameOptions.perspective` → `field_949`, `MinecraftClient.tick` →
  `method_2954`, `onResolutionChanged` → `method_2923`, `connect` → `method_2930`,
  `Minecraft.debugFPS` → `field_3787`. A name that did not exist would have failed the
  build, so the *names* are right even though the *behaviour* is not exercised.
- 68 JUnit tests over the protocol codec, the bridge, `LiveState`, the loadout diff, the
  mod registry, the sensors and the actuators. Every `examples` entry in
  `schema/protocol.json`, `schema/bridge.json`, `schema/loadout.json` and
  `schema/mods.json` is replayed against the code that has to speak it — the tests read
  the schema files themselves, so a contract change that this mod has not caught up with
  fails the build.
- The JAR carries `fabric.mod.json` (id `void`, client entrypoint
  `dev.voidpvp.client.VoidClient`), `void.mixins.json`, the blur shaders, the bridge shim
  at `assets/void/ui/void-shim.js`, and — when `native/java` is present — the Ultralight
  binding classes.

**Not verified here — needs a real game on Windows or macOS:**

- Anything that touches GL: the Ultralight paint, the ¼-res FBO copy, the two-pass blur,
  the crosshair. The blur degrades to a flat tint if the FBO or the shaders fail, so a
  wrong assumption costs fidelity, not a crash.
- Whether the Mixins land where they should behave. Two are worth a first look:
  - `InGameHudMixin.void$hideVanillaCrosshair` pins `ordinal = 0` on the read of
    `GameOptions.perspective` inside `InGameHud.render`, on the belief that 1.8.9 reads it
    exactly once there — in the crosshair guard. If the crosshair ever doubles up, that
    ordinal is why.
  - Toggle sprint latches the sprint `KeyBinding` at the **tail of the client tick**
    rather than in `onLivingUpdate`, because legacy yarn does not name
    `ClientPlayerEntity.tickMovement` (it is only named on `LivingEntity`, and a Mixin on
    every living entity is not worth the per-tick cost). The flag persists across ticks,
    so the only visible difference is one tick of latency on the toggle itself.
- The WS handshake against the real `void-bridge` server.
- Input forwarding coordinates: mouse positions go to the view in device-independent
  pixels (`Mouse.getX() / deviceScale`), and the view is sized `framebuffer / deviceScale`
  with `deviceScale = MC GUI scale x ui_scale`. Right in theory, unmeasured in practice.

## The one contract we could not honour

PVP_ARCHITECTURE.md §4 and CONTRACTS.md put this mod in **`dev.void.client`** and the
Ultralight binding in **`dev.void.ultralight`**. Neither package can exist: `void` is a
Java keyword, so it is not a legal identifier and `package dev.void.client;` does not
compile — nor does an `import` of it.

Both owners hit this and both renamed the one illegal segment:

| Contract | Actual |
|---|---|
| `dev.void.client.*` | **`dev.voidpvp.client.*`** (this mod) |
| `dev.void.ultralight.*` | **`dev.voidclient.ultralight.*`** (`native/`) |

Everything else is unchanged — the mod id is still `void`, the JS object is still
`window.void` (a reserved word *is* legal as a property name in ES5 and later), the
resource path is still `assets/void/`, and the JAR is still `void-client`.

Because the two halves renamed independently, `dev.voidpvp.client.ui.NativeUltralight`
binds to the binding **by reflection** rather than by import. It resolves the package at
runtime from, in order: `-Dvoid.ultralight.package`, the build-generated
`assets/void/native-package.txt`, then a short candidate list. That also buys the thing
the brief asked for directly: the JAR builds and runs with `native/` absent entirely, and
falls back to `NullWebView` — log once, HUD disabled, game untouched.

`build.gradle` adds `native/java` as a source directory of `main`, reads the package out
of whichever `Ultralight.java` it finds there, and writes it to
`assets/void/native-package.txt`. If that directory ever declares a package containing the
segment `void`, the build skips those sources with a warning rather than failing.

## The bridge shim

`assets/void/shim/void-shim.js` is the source of truth; `processResources` also publishes
it to `assets/void/ui/void-shim.js`, which is where the in-game bundle loads it from. The
two-step exists because `assets/void/ui/` is `packages/ingame`'s build output and is
gitignored — a file committed there would be deleted by the next UI build.

The bundle loads the shim first and then only ever talks to `window.void`. Java pushes a
frame's events as one `window.void.__emit([...])` call and answers
`window.__void_native('{"c":…,"params":[…]}')` synchronously with `{"c":…,"returns":…}`.
`openKeybindCapture` is the one asynchronous call: the synchronous answer only arms the
capture, and Java resolves the Promise later with `window.void.__emitKeybind(key)`.

## Conventions

- **Java 8, no exceptions.** No `var`, no `List.of`, no diamond-free streams; Gson is
  **2.2.4** and Netty **4.0.23.Final**, pinned to exactly what 1.8.9 has on its classpath,
  so nothing compiles against an API the game lacks.
- **Mixins stay thin.** They cannot be unit-tested here, so each one forwards to a plain
  class that can. Every non-trivial decision — edge detection, tick coalescing, zoom
  easing, the sprint latch, crosshair geometry, clamping — lives outside `mixin/`.
- **GL work saves and restores state.** Minecraft's `GlStateManager` caches GL state;
  everything in `render/` and `screen/` brackets its work with
  `glPushAttrib`/`glPopAttrib` so that cache stays honest.
- **`schema/` is the contract, not a suggestion.** The mod re-implements `mods.json`'s
  registry in `state/ModRegistry` because it cannot read the schema at runtime, and
  `ModRegistryTest` diffs the two on every build.
