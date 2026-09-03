# CONTRACTS — who owns what, and what the seams actually are

Six agents built `void-pvp` in parallel. This page is the coordination protocol **and the
record of where the seams landed**. It describes the tree as it is, not as it was planned:
every "we could not honour this" in earlier drafts has been folded back into
`schema/*.json` and `docs/PVP_ARCHITECTURE.md`, and what remains here is the contract.

The design contract it implements is `docs/PVP_ARCHITECTURE.md` in `VOIDLAUNCHERFIVE`;
section references below (§3, §4, §6.5, §7, §8, §12, §13) point there.

## The one rule

**Each owner writes only inside its own directories. Cross-directory needs are expressed
by reading `schema/*.json` — never by editing another owner's files.**

If you need something another owner has not built yet, do not reach into their directory,
do not stub their file "temporarily", and do not vendor a copy. Read the schema, code
against it, and if the schema does not say what you need, that is the bug: raise it, get
`schema/` changed, and everyone recompiles against the new contract. Every change listed
under "Contract changes" in `schema/README.md` arrived exactly that way.

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
propose it, don't just land it, and append it to the changelog in `schema/README.md`.

`design/` pre-dates this scaffold: Figma screen exports plus `tokens.css` / `tokens.json`.
It is **reference material, read-only for everyone**. **ui** ports the tokens into
`packages/ui`; nobody edits `design/` and nothing imports from it at build time.

Root files not listed above (`package.json`, `pnpm-workspace.yaml`, `.gitignore`, this
file) are shared. Touch them only to register your own package or ignore your own build
output.

---

## Java package names

`dev.void.client` and `dev.void.ultralight`, as earlier drafts of §4 and of this file
specified them, **cannot be compiled**: `void` is a Java keyword, so it is not a legal
identifier and neither `package dev.void.client;` nor an `import` of it parses. Both
owners renamed the one illegal segment, and both names are now settled and written into
§4:

| Was specified | Actual |
|---|---|
| `dev.void.client.*` | **`dev.voidpvp.client.*`** — the mod |
| `dev.void.ultralight.*` | **`dev.voidclient.ultralight.*`** — the binding, in `mod/native/java` |

Nothing else moved. The Fabric mod id is still `void`, the JS object is still
`window.void` (a reserved word *is* a legal property name in ES5 and later), the resource
root is still `assets/void/`, and the JAR is still `void-client`.

**The mod imports the binding directly.** `mod/build.gradle` adds `native/java` as a
source directory of `main`, so `dev.voidpvp.client.ui.UltralightWebView` has plain
`import dev.voidclient.ultralight.*;` statements and a signature change in the binding is
a compile error here. The runtime package lookup this used to do by reflection — a
`-Dvoid.ultralight.package` property, an `assets/void/native-package.txt` resource, a
candidate list — is gone along with `NativeUltralight` and the build step that wrote the
marker file: with both names settled it bought nothing but a stringly-typed API.

**The `NullWebView` fallback is unchanged and is still the point.** It triggers on
`LinkageError` at runtime, which covers both real cases: this platform's natives are not
in the JAR (`Ultralight.load()` throws `UnsatisfiedLinkError`), or the JAR was built with
no `native/java` at all (`NoClassDefFoundError` on the first touch of
`UltralightWebView`). `WebViews.create` catches both, logs once, and the game runs with
the in-game UI disabled. Every reference to the binding lives in that one class so the
error stays containable.

---

## The seams

### `window.void` — Java ⇄ JS, in-process

The bridge object is named exactly **`window.void`**. Defined in `schema/bridge.json`,
§6.5. Implemented by **mod** (`mod/src/main/java/dev/voidpvp/client/bridge/`) and consumed
by **ingame**.

- Java → JS is push, on **seven** channels:
  `void.on('keys'|'tick'|'server'|'loadout'|'loadouts'|'setting'|'menu', handler)`.
  - `loadouts` carries the whole library, in full, from `init.loadouts`. Without it JS
    would only know the loadouts it happened to watch go past, and the Loadouts frame
    lists all of them.
  - `setting` carries one `{id, key, value}` Java changed **by itself** — an in-game
    hotkey, or a launcher echo. It is *not* pushed for a change the page made through
    `setModSetting`, which already returned the stored value; re-pushing that would fight
    the control the player is holding.
- JS → Java is a call, still exactly six: `setGameplay`, `setHud`, `setModSetting`,
  `switchLoadout`, `closeMenu`, `openKeybindCapture`.
- Ultralight runs **inside the JVM**, so calls are synchronous and return the state
  actually applied. No ack, no request id, no optimistic UI.
- **`openKeybindCapture` is the one asynchronous call, and the one easy thing to get
  wrong.** The hop is still synchronous and still answers — with `returns: null`, meaning
  *armed*. The captured key arrives later on the push channel as a **call-result
  envelope**, `__emit({c:'openKeybindCapture', returns:'V'})`, or `returns: null` again
  when the player cancelled with Escape. Null therefore travels on both channels meaning
  two different things: tell them apart **by channel, never by value**. A shim that reads
  the synchronous null as the resolution settles every capture instantly with no key.
- **ingame** must also run against a fake `window.void` in a normal browser (the `?debug`
  harness, §9) — there are no devtools in game. `createFakeVoid()` in `@void/protocol` is
  that fake, and it plays Java: it owns the library, clamps what it is given, and pushes
  `loadouts` before `loadout` on `emitInitialState()`, in the order Java does.

### The bridge shim — one file, three copies of the semantics

```
mod/src/main/resources/assets/void/shim/void-shim.js   the shipped shim, committed (mod)
  → processResources copies it to  build/resources/main/assets/void/ui/void-shim.js
packages/protocol/src/void-bridge.ts  installVoidShim()  the reference implementation (ui)
packages/protocol/src/fake-void.ts    createFakeVoid()   the browser stand-in (ui)
```

`void-shim.js` is the source of truth for what ships. `installVoidShim()` is the
specification: if the two ever disagree, `void-bridge.ts` settles it, and both are tested
against the same `bridge.json` examples.

**The two builds do not fight, and here is why.** `packages/ingame`'s Vite build writes to
`mod/src/main/resources/assets/void/ui/` with `emptyOutDir: true` — it owns that directory
and wipes it. The shim is **not** in it: it is committed one level up in
`assets/void/shim/`, and Gradle's `processResources` copies it into
`build/resources/main/assets/void/ui/`, which is Gradle's own output tree. Neither build
writes where the other reads, so the order they run in does not matter.

`packages/ingame/index.html` does **not** contain the shim tag — Vite would try to resolve
a file that does not exist in that package and fail the build. The `injectVoidShim()`
plugin in `vite.config.ts` inserts `<script src="./void-shim.js"></script>` as the first
element of `<head>` in the **built** `index.html` only. In the browser harness there is no
Java and `createFakeVoid()` installs `window.void` itself, so the tag would only be a 404.

At runtime, in the JAR: `void-shim.js` runs first and builds `window.void` on top of
`window.__void_native`; then the bundle runs, and `connectBridge()` **uses the
`window.void` that is already there** (it checks `__isVoidBridge`) rather than replacing
it, so the object Java pushes into is the object the page listens on. `installVoidShim()`
is the fallback for a host that installed `__void_native` but no shim.

### `-Dvoid.port` / `-Dvoid.token` — Rust ⇄ Java, over localhost WS

**core** binds `ws://127.0.0.1:<port>`, mints a per-spawn session token, and passes both to
the JVM as the system properties **`-Dvoid.port`** and **`-Dvoid.token`**. **mod** reads
them in `net/`, connects, and sends `hello` carrying the token; the server closes the
socket if it does not match.

Messages are defined in `schema/protocol.json`, §7 — **six** Java→Rust, three Rust→Java.
The link carries **state, never frames**. `v` is the protocol version, `1`, present on
`hello` and `init` only; a mismatch means the two halves were not shipped together, and
the launcher refuses to launch. Unknown `t` values and unknown fields are ignored by both
sides.

Two things about it are worth stating here because both were seams:

- **`init.loadouts` carries whole loadouts, not summaries.** A loadout is about a kilobyte
  and a library is capped at 128, so the whole library is a few hundred KB sent once per
  launch. In exchange `void.switchLoadout` and the L-key cycle apply any loadout in under
  a frame with no round trip (§8.2), and the `loadouts` bridge event has something to
  forward. There is deliberately **no** `request_loadout` message; `LiveState` has no
  "pending switch" state either, because nothing is ever pending.
- **`hotkey` is a notification, never a request.** `{"t":"hotkey","id":"loadout.next"|
  "overlay"}` says the player pressed a global hotkey and Java has *already* acted on it.
  `void-core`'s sync loop follows `loadout.next` by advancing its own active pointer, so
  the tray and the next launch agree with the running game. It is dropped rather than
  queued when the link is down: the state the key press produced travels in its own
  `state` message, which *is* queued, so replaying the keystroke would double-count.

### `packages/ingame` → `mod/src/main/resources/assets/void/ui/`

**ingame** builds its static bundle into
**`mod/src/main/resources/assets/void/ui/`**. This is the single exception to the writes-
only-in-your-own-directory rule, and it is a *build output*, not source: the directory's
contents are gitignored, and **mod** must not hand-edit anything in it.

**mod** loads the bundle from that classpath path (`assets/void/ui/index.html`) into the
Ultralight view. Budget: ≤ 400 KB gzipped (§10); currently 196 KB.

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

---

## Deciding where something goes

- Does it change how the game *plays*? It belongs in the **loadout** (`schema/loadout.json`),
  not in global settings. Account, Java path, RAM, hotkeys and theme are global (§8.3).
- Is it drawn? Then it is HTML, owned by **ingame** — the only GL exception is the
  crosshair, which must sit at the exact pixel centre (§3).
- Is it a number the HUD shows? It goes Mixin → `window.void` in-process, and **never**
  over the WS. Only summaries cross to Rust.
- Is it persisted? Java holds live state and tells Rust afterwards; Rust is the store of
  record between sessions (§6.1).
- Is it a property of a *mod* — its label, its filter tab, its clamp range, its factory
  default? Then it is a row in `schema/mods.json`, and no consumer re-declares it. That
  file now carries `category` (`hud | pvp | visual | utility`, the Mods-panel tabs of frame
  244:538) alongside `kind`, and the frames' own copy as `label` (`FPS display`, `CPS
  counter`, `Ping display`). The overrides `packages/ingame/src/registry.ts` used to hold
  are gone; what is left there is the tile **grid order**, which is layout, not a property
  of a mod.

---

## Ultralight binding API

Owned by **native** (`mod/native/`), consumed by **mod**. Built against Ultralight **1.4.0b
(rev `081c48b`)**; see `mod/native/README.md` for the SDK provenance, the licence line that
must appear in the About screen, and what is and is not verified. Package:
**`dev.voidclient.ultralight`** (see "Java package names" above).

```java
package dev.voidclient.ultralight;

public final class Ultralight {
  public static void load() throws UnsatisfiedLinkError;
  public static Renderer createRenderer(String resourcePathPrefix); // e.g. "assets/void/ui/"
  public static String version();          // Ultralight's version, e.g. "1.4.0"
  public static String webKitVersion();    // e.g. "615.1.18.100.1"
  public static String licenceNotice();    // the credit line §13 requires in About/credits
  public static String nativeDirectory();  // where the natives were extracted; null before load()
}

public final class Renderer implements AutoCloseable {
  public void update();                                          // once per game tick
  public void refreshDisplay();                                  // once per frame, before render()
  public void render();                                          // paints dirty views
  public View createView(int w, int h, boolean transparent);     // GPU, via our GL driver
  public View createViewCpu(int w, int h, boolean transparent);  // CPU surface — tests only
  public void purgeMemory();
  public void close();
}

public final class View implements AutoCloseable {
  public void loadUrl(String url);      // "file:///index.html" -> classpath under the prefix
  public void loadHtml(String html);
  public void resize(int w, int h);
  public void setDeviceScale(double s); // MC GUI scale x window DPI

  public int  glTextureId();            // valid after render(); RGBA, premultiplied, top-left origin
  public int  textureWidth();           // backing texture may be larger than the view
  public int  textureHeight();
  public float uvScaleX();              // sample [0,uvScaleX] x [0,uvScaleY]; usually 1.0
  public float uvScaleY();
  public boolean isDirty();

  public void fireMouseEvent(int type, int x, int y, int button); // 0 move 1 down 2 up / 0 none 1 L 2 M 3 R
  public void fireKeyEvent(int type, int virtualKey, int modifiers, String text);
                                        // 0 keydown 1 keyup 2 char; mods 1 alt 2 ctrl 4 meta 8 shift
  public void fireScrollEvent(int dx, int dy);

  public String evaluateScript(String js);   // "" if undefined; exceptions -> "" + logged
  public void setMessageHandler(java.util.function.Function<String,String> handler);
  public java.util.function.Function<String,String> messageHandler();

  public void setFocus(boolean f);
  public boolean hasInputFocus();       // true -> forward Escape to the page instead of closing
  public boolean isLoading();
  public byte[] readPixels();           // BGRA premultiplied, tight w*4 stride — CPU views only
  public int width();
  public int height();
  public boolean isAccelerated();
  public void close();
}
```

Constants on `View`: `MOUSE_MOVED/DOWN/UP` = 0/1/2, `KEY_DOWN/UP/CHAR` = 0/1/2,
`MOD_ALT/CTRL/META/SHIFT` = 1/2/4/8.

### Additions beyond the agreed surface

All additive — nothing specified was changed or removed. `version()`, `createViewCpu` and
`readPixels` were requested; the rest exist because the mod needs them and guessing later is worse:

`Ultralight.webKitVersion/licenceNotice/nativeDirectory`, `Renderer.refreshDisplay/purgeMemory`,
`View.loadHtml/textureWidth/textureHeight/uvScaleX/uvScaleY/messageHandler/hasInputFocus/isLoading/width/height/isAccelerated`.

`uvScaleX/Y` matter: Ultralight's render target may be larger than the view, and drawing the whole
texture would show garbage at the edges. `refreshDisplay()` is what advances CSS animations,
transitions and `requestAnimationFrame` — without it the UI is static, which is why `UiHost.frame()`
calls it every frame between `update()` and `render()`.

### Rules the mod must follow

1. **One thread.** Everything on the thread that called `createRenderer` — the render thread.
   There is no dispatch queue by design (§6.1: key press to pixel in the same frame).
2. **One renderer per process.** A second `createRenderer` returns the first one.
3. **Per frame:** `refreshDisplay()` then `render()`. **Per tick:** `update()`. Skip `render()` when
   `isDirty()` is false — that is the first lever if the §10 paint budget is missed.
4. **`loadUrl` takes three slashes**: `file:///index.html`. It resolves to the classpath resource
   `<resourcePathPrefix> + index.html` via a `ULFileSystem` backed by
   `ClassLoader.getResourceAsStream`; nothing is written to disk. Paths containing `..` are refused.
   A miss falls back to the extracted natives directory, which is how `resources/cacert.pem`,
   `resources/icudt67l.dat` and the bundled font are found.
5. **`fireKeyEvent` takes Windows virtual-key codes** (Ultralight's `GK_*`), on every platform. The
   mod maps LWJGL 2 key codes to them. A typed character needs **two** events: `KEY_DOWN` with the
   virtual key, then `KEY_CHAR` with the text — a key-down alone inserts nothing.
6. **`setMessageHandler` runs synchronously on the render thread** and its return value is the
   JavaScript return value. It is re-installed automatically on every navigation. Returning null
   yields `null` in JS; throwing is logged and surfaces as a JS exception, never escaping into Java.
   This is the primitive `window.void` (§6.5) is built on, not a replacement for it.
7. **The texture is premultiplied, top-left origin.** Blend with
   `GL_ONE, GL_ONE_MINUS_SRC_ALPHA`, `v = 0` at the top, depth test off. §6.2 said "straight alpha"
   in an earlier draft and has been corrected: straight-alpha blending double-darkens every
   antialiased edge in the UI.
8. **No system fonts.** The font loader serves one bundled face (Inter) for every family, so the UI
   must declare its typefaces with `@font-face`; those are served from the classpath. `font-family:
   Arial` silently gets Inter. This keeps the design identical on every machine — **ui** should
   treat it as a constraint on the token set, alongside the CSS restrictions already in §9.
9. **`readPixels()` is test-only.** Full-frame readback in game is exactly what §6.2 forbids.
10. **Never let the render thread terminate as a thread while Ultralight is live.** WebCore's
    thread-local destructor aborts the process (a 1.4.0b bug, reproduced and documented in
    `mod/native/README.md`). Quitting through `System.exit()` — which is what `Minecraft.shutdown()`
    already does — sidesteps it completely. Do not add a "graceful shutdown" that joins and exits
    the render thread instead.
11. **`natives/<os>-<arch>/` must be on the classpath** — `windows-x64`, `macos-x64`,
    `macos-arm64` — including the generated `files.txt`. That is what the per-OS JARs below are
    for. `-Dvoid.ultralight.nativeDir=<dir>` skips extraction for dev runs.

### Natives size — settled: per-OS JARs

The native payload is **20.8 MB** (Windows) / **50.3 MB** (+ mac-x64) / **77.4 MB** (+ mac-arm64) of
JAR-deflated bytes, measured. §13 budgeted ~25 MB for all of it and has been corrected.

**The mod ships one JAR per platform.** `mod/build.gradle`'s `platformJars` task repackages the
already-remapped JAR once per staged `mod/native/build*/natives/<os>-<arch>/` tree into
`void-client-<version>-<os>-<arch>.jar`. It is a repackage, not a second Loom remap: the classes are
byte-identical across platforms and only the natives differ, so remapping again would cost minutes to
produce the same bytes. The base `void-client-<version>.jar` carries **no** natives (324 KB), which
is what keeps `./gradlew build` and the test loop fast; `./gradlew platformJars` is the CI step, and
it says so plainly when no natives are staged instead of shipping a JAR that dies at
`Ultralight.load()`.

`void-core` selects one at prepare time: `install::ModPlatform`, derived from the OS the **JVM** will
run as — on Apple Silicon the game runs x64 under Rosetta, so an arm64 Mac takes the `macos-x64` JAR
(§13). `void-pvp prepare --platform <os>-<arch>` overrides it for cross-preparing another machine.
