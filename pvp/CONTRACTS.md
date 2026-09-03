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

---

## Ultralight binding API

Owned by **native** (`mod/native/`), consumed by **mod**. Built against Ultralight **1.4.0b
(rev `081c48b`)**; see `mod/native/README.md` for the SDK provenance, the licence line that must
appear in the About screen, and what is and is not verified.

### ⚠️ One forced deviation: the package name

The agreed package was `dev.void.ultralight`. **That is not a legal Java package.** `void` is a
reserved word, and a keyword cannot be an identifier — neither `package dev.void.ultralight;` nor
`import dev.void.ultralight.View;` compiles:

```
error: <identifier> expected
package dev.void.ultralight;
            ^
```

The package is therefore **`dev.voidclient.ultralight`**, mirroring the mod's artifact name
(`rootProject.name = 'void-client'`). Nothing else about the API changed — every type, method,
signature and constant below is exactly as specified.

**This affects `mod/` too**: `dev.void.client.*` in PVP_ARCHITECTURE §6 has the same problem, and
**mod** hit it independently — it settled on `dev.voidpvp.client.*` and resolves the binding's
package at runtime, trying `-Dvoid.ultralight.package`, then the resource
`assets/void/native-package.txt`, then the candidates `dev.voidclient.ultralight`,
`dev.voidpvp.ultralight`, `dev.ultralight`. **`dev.voidclient.ultralight` is the first candidate, so
the two halves already agree** — nothing to change on either side. If **mod** wants to make it
explicit rather than rely on candidate order, write `dev.voidclient.ultralight` into
`assets/void/native-package.txt`.

### The API

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
transitions and `requestAnimationFrame` — without it the UI is static.

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
   `GL_ONE, GL_ONE_MINUS_SRC_ALPHA`, `v = 0` at the top, depth test off.
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
    `macos-arm64` — including the generated `files.txt`. `mod/native/README.md` has the exact file
    list and a Gradle snippet. `-Dvoid.ultralight.nativeDir=<dir>` skips extraction for dev runs.

### Open item for `core`

The native payload is **20.8 MB** (Windows) / **50.3 MB** (+ mac-x64) / **77.4 MB** (+ mac-arm64) of
JAR-deflated bytes, measured. PVP_ARCHITECTURE §13 budgets ~25 MB for all of it. Either the mod
ships per-OS JARs (recommended — `void-core` already resolves downloads per launch and knows the
target OS) or the natives become a separately downloaded, hash-verified artifact. Not a decision
`native` can make alone.
