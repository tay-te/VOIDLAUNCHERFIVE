# `mod/native/` — the Ultralight binding

C++17 + JNI wrapper around **Ultralight 1.4**'s C API, plus a `GPUDriver` written against the
OpenGL 2.1 that Minecraft 1.8.9 gives us. Owned by the `native` agent (`pvp/CONTRACTS.md`); the mod
consumes the built library and never builds it.

Both public Java bindings are dead — `LabyMod/ultralight-java` (1.3 beta, archived Jun 2024) and
`Janrupf/ultralight-java-reborn` (0.0.2-SNAPSHOT, Jul 2023) — and neither targets 1.4, so this one
is ours (PVP_ARCHITECTURE §6.2, §13).

---

## Version actually used

| | |
|---|---|
| SDK archive | `ultralight-sdk-081c48b-<platform>.7z` |
| `VERSION.txt` | **`1.4.0b.081c48b`** (2024-10-26) |
| `ULTRALIGHT_VERSION` (Defines.h) | **`1.4.0`** |
| `ulVersionString()` at runtime | `1.4.0` |
| WebKit | `615.1.18.100.1` |
| Host | `https://ultralight-sdk-dev.sfo2.cdn.digitaloceanspaces.com/` |

### Read this before "fixing" the download URL

The brief (and Ultralight's older docs) point at the **release** bucket:

```
https://ultralight-sdk.sfo2.cdn.digitaloceanspaces.com/ultralight-sdk-latest-<plat>.7z
```

That bucket's newest object is `208d653`, dated **2023-07-24**, and it is **Ultralight 1.3.0** —
its `LOG.txt` tops out at the 1.3.0 release notes and `Defines.h` says `1.3.0`. It also has **no
`mac-arm64` build at all** (`ultralight-sdk-latest-mac-arm64.7z` returns 404; the whole bucket
listing contains zero `arm` keys). Both facts were confirmed by enumerating the bucket, not
inferred.

The 1.4 line — and every `arm64` build — lives in the **dev** bucket, which is what
`cmake/UltralightSDK.cmake` uses. We pin the exact revision rather than `latest` and verify SHA-256,
so a silent upstream bump cannot change what we shipped:

| platform | SHA-256 |
|---|---|
| `win-x64` | `3899e728293bb12bbd5d828bf2a2d622647c35c438fd81160baf01ea6c5a8cc4` |
| `mac-x64` | `1ee67a0b8484ca2dec8d43a2710e87ef94256f06383514869491336aee170001` |
| `mac-arm64` | `d9b459fcab7116df6d24b355a2c521657f2c058ca9a1ec18cec38e4bb424807f` |
| `linux-x64` | `41a2b5034112d764acef1ecf63e19c0fafff3616c3ac3347a0473f120a535ec8` |

`1.4.0b` is a beta tag. There is no non-beta 1.4 build on either bucket as of this writing.

---

## Licence notice — required in the About/credits screen

Ultralight's `LICENSE.txt` §4.4 ("Marking") requires the legend to appear in the credit section of
any product that ships it. This is the line, copied from the SDK's `license/NOTICES.md`:

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

The full text ships to disk as `natives/<os>-<arch>/resources/NOTICES.md` and the short form is
available in code as `Ultralight.licenceNotice()` — use one of them, don't retype it.

The bundled font is **Inter**, SIL Open Font License 1.1
(`natives/<os>-<arch>/resources/fonts/OFL.txt`). The OFL requires the copyright and licence notice
to accompany the font, which shipping `OFL.txt` alongside it satisfies.

Free-tier terms are unchanged from §13: $0 while last-fiscal-year turnover **and** total funding
raised are both under US$100k.

---

## Building

The Ultralight SDK is fetched and cached by CMake into `mod/native/sdk/` (gitignored). Nothing to
install for that — CMake's bundled libarchive reads `.7z`, so **no p7zip is needed**.

### Linux / macOS

```bash
scripts/build.sh                  # release, host arch
scripts/build.sh --debug
scripts/build.sh --clean
scripts/build.sh --arch arm64     # macOS only: cross-configure for Apple Silicon
scripts/build.sh --arch x86_64    # macOS only: Intel / the arch the game actually runs under
```

Ultralight ships **separate** `mac-x64` and `mac-arm64` SDKs and no fat binaries, so a universal
build is impossible: run once per arch and ship both trees. The build refuses a multi-arch
`CMAKE_OSX_ARCHITECTURES` rather than producing something broken.

**Xcode / macOS notes**

- Xcode 12+ (any version with a macOS 10.14 SDK) works. `CMAKE_OSX_DEPLOYMENT_TARGET=10.14` is set
  by `build.sh`; that is Ultralight 1.4's floor.
- Our dylib gets `INSTALL_RPATH=@loader_path` and links with `-undefined dynamic_lookup`, because
  the GL entry points are resolved from the live context at runtime and must not be link-time
  dependencies. Ultralight's dylibs carry `@rpath/…` install names, which `@loader_path` resolves
  since everything lands in one directory.
- `-Xcode` generator: `cmake -S . -B build-xcode -G Xcode -DCMAKE_OSX_ARCHITECTURES=x86_64`.
- Do **not** codesign the extracted temp copies; the JVM loads them by path and Gatekeeper does not
  apply to `System.load` of a file the app itself wrote. If the mod is ever notarised, the natives
  must be signed **inside** the app bundle, not after extraction.

### Windows

```powershell
scripts\build.ps1
scripts\build.ps1 -Config Debug -Clean
```

**MSVC notes**

- Visual Studio 2019 or newer, C++ workload. `-A x64` is pinned by the script: the SDK and LWJGL 2
  are both x64 and there is no 32-bit path.
- `MSVC_RUNTIME_LIBRARY=MultiThreadedDLL` (`/MD`) is set to match the CRT the SDK was built with.
  Building `/MT` links a second CRT into the process and Ultralight's allocations start crossing
  heaps.
- Debug builds still use `/MD` (not `/MDd`) for the same reason — the SDK has no debug CRT variant.
- No `.def` file and no `__declspec(dllexport)` sprinkling: every JNI entry point is already
  `JNIEXPORT`, and `CXX_VISIBILITY_PRESET hidden` keeps the rest internal.

### What a build produces

```
build/
  voidultralight.{so,dylib,dll}
  voidultralight-api.jar          # dev.voidclient.ultralight, Java 8 bytecode (major 52)
  java-classes/                   # the same classes, unpacked
  natives/<os>-<arch>/            # the exact payload the mod JAR ships (below)
```

Run the tests with `test/run-test.sh` (Linux/macOS) or `ctest --test-dir build`.

---

## Native payload layout

`NativeLoader` expects `natives/<os>-<arch>/` on the classpath, with `<os>-<arch>` one of
`windows-x64`, `macos-x64`, `macos-arm64` (plus `linux-x64`, built here for tests, not shipped).

### Exact file list

| | `windows-x64` | `macos-x64` / `macos-arm64` |
|---|---|---|
| binding | `voidultralight.dll` | `voidultralight.dylib` |
| Ultralight | `UltralightCore.dll` (2.3 MB) | `libUltralightCore.dylib` (3.4 / 2.9 MB) |
| | `WebCore.dll` (44.9 MB) | `libWebCore.dylib` (80.0 / 76.4 MB) |
| | `Ultralight.dll` (0.6 MB) | `libUltralight.dylib` (0.9 / 0.8 MB) |
| resources | `resources/cacert.pem` (0.21 MB) | same |
| | `resources/icudt67l.dat` (6.16 MB) | same |
| | `resources/NOTICES.md` | same |
| | `resources/fonts/Inter-Variable.ttf` (0.86 MB) | same |
| | `resources/fonts/OFL.txt` | same |
| manifest | `files.txt`, `version.txt` | same |

`AppCore` is **not** shipped and not linked — see "Why no AppCore" below.

`files.txt` is generated by the build (`cmake/WriteNativesManifest.cmake`): one
`<relative path>\t<size in bytes>` per line. A JAR has no directory listing, so this manifest is how
`NativeLoader` knows what to unpack; the sizes let it skip files it already extracted.
`version.txt` names the temp directory and includes a hash of the JNI library, so a rebuilt binding
never runs against a previously extracted copy of itself.

### Extraction and load order

`Ultralight.load()` copies everything in the manifest to
`${java.io.tmpdir}/voidultralight-<key>-<version>-<hash>/`, skipping files already there at the
right size, then `System.load()`s in this order:

```
UltralightCore  ->  WebCore  ->  Ultralight  ->  voidultralight
```

Order is load-bearing. On Windows it is the only thing that makes dependent DLLs resolve without
touching `PATH` (the loader matches already-loaded modules by name). On macOS it is what lets dyld
satisfy Ultralight's `@rpath/…` install names. Set `-Dvoid.ultralight.nativeDir=<dir>` to skip
extraction and load straight out of a build tree.

### JAR size — the §13 estimate is wrong

PVP_ARCHITECTURE §13 budgets "~25 MB" for the natives. Measured, deflated as a JAR would store them:

| shipped platforms | JAR bytes added |
|---|---|
| `windows-x64` only | **20.8 MB** |
| `windows-x64` + `macos-x64` | **50.3 MB** |
| `windows-x64` + `macos-x64` + `macos-arm64` | **77.4 MB** |

`WebCore` is 45–80 MB uncompressed per platform and is most of it. 25 MB is achievable for *one*
platform, not three.

**Settled: per-OS mod JARs** (option 1). §13 has been corrected and `mod/build.gradle` implements
it — `./gradlew platformJars` repackages the already-remapped JAR once per staged
`natives/<os>-<arch>/` tree into `void-client-<version>-<os>-<arch>.jar`, and the base JAR carries no
natives at all (324 KB). It is a repackage, not a second Loom remap: the classes are byte-identical
across platforms and only these trees differ. `void-core` picks one at prepare time
(`install::ModPlatform`, §12.3), which costs nothing because it already resolves downloads per
launch and knows the target OS.

Option 2 — natives as a separate hash-verified download — was rejected: it adds a second artifact, a
second version to keep in step with the mod, and a second way for a half-installed machine to fail,
to save nothing the per-OS split does not already save.

### The Gradle side, as landed

`mod/build.gradle` carries this; it is reproduced here because this file is where the payload it
stages is described. It differs from the original sketch in two ways, both deliberate: the natives
never enter the **base** JAR (so `./gradlew build` and the test loop stay fast and small), and the
API classes are compiled unconditionally from `native/java` rather than behind a package-name probe,
because both package names are settled and the mod now imports the binding directly.

```gradle
// The API classes compile with the mod: one javac, one --release 8, so a signature change
// in the binding is a compile error in dev.voidpvp.client.ui.UltralightWebView.
sourceSets.main.java.srcDir file('native/java')

// Each `natives/<os>-<arch>/` tree is self-contained: the binding, Ultralight's runtime
// libraries, resources/ and the files.txt manifest NativeLoader reads. Nothing here is
// built by Gradle — the `native` agent owns that build and CI publishes the trees.
def nativeStages = [
    'windows-x64': file("$projectDir/native/build-win/natives/windows-x64"),
    'macos-x64'  : file("$projectDir/native/build-macx64/natives/macos-x64"),
    'macos-arm64': file("$projectDir/native/build-macarm64/natives/macos-arm64"),
]
// Local dev: a single host build lands in native/build/natives/<key>/.
file("$projectDir/native/build/natives").listFiles()?.each { d ->
    if (d.directory) nativeStages.putIfAbsent(d.name, d)
}

// One JAR per staged platform, built from the remapped JAR rather than remapped again:
// the classes are byte-identical across platforms and only the natives differ.
def platformJars = nativeStages.findAll { it.value.directory }.collect { key, dir ->
    tasks.register("platformJar${key.split('-').collect { it.capitalize() }.join()}", Jar) {
        dependsOn tasks.named('remapJar')
        archiveBaseName = project.archives_base_name
        archiveVersion = project.version
        archiveClassifier = key                      // void-client-<version>-<os>-<arch>.jar
        from { zipTree(tasks.named('remapJar').get().archiveFile) }
        from(dir) { into "natives/$key" }
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}

tasks.register('platformJars') { dependsOn platformJars /* + a lifecycle log */ }
```

Deflate, not STORED: the natives are already-compressed binaries, but `STORED` would add ~150 MB per
JAR for no gain. Gradle's `Jar` deflates by default, so nothing has to say so.

---

## The OpenGL driver, and the GL 2.1 constraint

`src/gpu_driver_gl.cpp` implements `ULGPUDriver`: texture create/update/destroy, render buffers,
geometry, command-list capture and the draw pass. It renders into Minecraft's context — no context
is created, bound or owned here.

**The constraint.** Minecraft 1.8.9 uses LWJGL 2. On macOS that means a legacy **OpenGL 2.1**
context; on Windows a compatibility context that is usually newer but must not be assumed to be.
Ultralight's own reference shaders (`sdk/<plat>/shaders/glsl/*.h`) are `#version 150`, which needs
a GL 3.2 core context. They will not compile for us.

**How the driver handles it:**

| Problem | What we do |
|---|---|
| Shaders are GLSL 1.50 | Ported to **`#version 120`** in `src/shaders_glsl120.h`, keeping the reference logic intact (including Inigo Quilez's MIT `sdEllipse`, notice preserved) |
| No `uint`, no `switch` | `int` throughout; `bool(uint(x + 0.5))` becomes a float compare; `switch` becomes if/else chains |
| No `in`/`out` at global scope | `attribute`/`varying`; the fragment shaders keep a global `out_Color` (the originals *read it back*, and `gl_FragColor` is not reliably readable) and copy it out at the end of `main` |
| Only constant-index-expressions may index a uniform array | `Clip[i]`, `Vector[i]`, `Scalar4[..][i]` and `ex_Data2[i]` all become explicit if/else fan-outs |
| No `layout(location=)` | `glBindAttribLocation` before linking, locations 0–10 |
| `texture()` | `texture2D()` |
| Varying that is read but never written | `ex_ScreenCoord` dropped from both programs — Apple's compiler rejects it |
| FBOs | `ARB_framebuffer_object` preferred, **`EXT_framebuffer_object` fallback** (what Apple's 2.1 profile advertises) |
| VAOs | Feature-detected (`ARB_` or `APPLE_vertex_array_object`); without one, the vertex format is rebound per draw, which a 2.1 driver is fine with |
| A8 glyph masks | `GL_R8`/`GL_RED` when `ARB_texture_rg` is present, else **`GL_LUMINANCE8`** so the shader's `.r` read still sees coverage. `GL_ALPHA` would put it in `.a` and render nothing |
| Loading entry points | `wglGetProcAddress` + `opengl32.dll` exports / `dlsym` on the OpenGL framework / `glXGetProcAddress`. **No GL library is linked and no loader that creates a context is used** |
| MC's state must survive | Everything touched is saved and restored: program, active texture + unit 0–2 bindings and `GL_TEXTURE_2D` enables, array/element buffer, FBO, VAO, viewport, scissor box, blend enable + separate func + equation, depth/cull/stencil/alpha-test enables, colour and depth masks, generic attrib array enables 0–10, and the fixed-function client array enables MC's immediate-mode renderer relies on |
| Premultiplied output | `glBlendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)` |

**Texture orientation.** `View.glTextureId()` promises RGBA, premultiplied, **top-left origin**.
That comes from passing `flip_y = true` to `ulApplyProjection`. This was measured, not guessed —
`test/projection_probe.cpp` prints the matrix both ways:

```
flip_y=false  ul(0,0) -> ndc(-1.00, +1.00)   ul(0,50) -> ndc(-1.00, -1.00)
flip_y=true   ul(0,0) -> ndc(-1.00, -1.00)   ul(0,50) -> ndc(-1.00, +1.00)
```

With `flip_y = true` the page top lands at NDC −1, which is texture row 0, which is `v = 0`. Draw
the quad with `v = 0` at the top and it is right way up.

### Coming up, and failing to

Entry points must come from a context that exists, and there is none at mod init, so the driver
cannot be built when it is registered. It is built on demand instead — and *who* asks matters.

`Renderer.probeAccelerated()` (`rendererProbeAccelerated`) builds it deliberately, on the UI
thread, with that thread's context current, **before any view has been created**, and answers
whether it worked. That is the call the mod makes, and it is what lets a machine this driver cannot
run on get a CPU view instead of an accelerated one. Until it existed the only way to find out was
to create an accelerated view and render it, and a failure there was terminal: a process-wide flag
latched on, every subsequent accelerated render returned without painting, and the player got an
empty overlay with no visible error — on hardware where the CPU surface would have worked fine.
`rendererRender` still builds the driver if nobody probed, for callers of the binding that are not
the mod, and the failure is no longer silent there either.

`Renderer.acceleratedDriverFailed()` (`gpuDriverFailed`) reports the same thing afterwards. It is a
plain read of a process-wide flag, safe from any thread, and the mod polls it once a frame so a
driver that dies *after* the probe is rebuilt on the CPU surface rather than latching. It is sticky
and one-way: a driver that could not link GLSL 1.20 on the first frame will not link it on the
next, and retrying would mean recompiling two programs 60 times a second on exactly the machine
that cannot compile them.

Every failure is logged at error level on **stderr, natively**, which is deliberate: Loom's
generated log4j config is broken on 1.8.9 and silences every Java logger in the process, and
Minecraft replaces `System.err` with a log4j stream during bootstrap. `fprintf(stderr)` from here is
the one channel no logging configuration can turn off. The mod's Java-side notice writes to
`FileDescriptor.err` for the same reason, so both halves of the story land in the same place.

### Making the driver fail on purpose: `VOID_UI_GPU_FAIL`

The fallback above guards a failure that happens on hardware nobody here has. This driver has
executed on exactly one machine — macOS, Apple's OpenGL 2.1 profile — and never on Windows or
Linux. A fallback that has never run is a fallback nobody knows works, so the failure is
reproducible on demand. An **environment variable**, not a `-D` property: the value has to be
legible to the native library, which cannot see the JVM's properties.

| `VOID_UI_GPU_FAIL=` | Fails at | Stands in for |
|---|---|---|
| `init` | before a single GL entry point is resolved | no usable context, missing entry points, a driver that refuses outright. **The only mode that works headless**, which is why `ctest -R accelerated_fallback` uses it |
| `compile` | `glCompileShader`, for real — invalid GLSL is appended to every fragment shader | "this GPU will not accept our GLSL 1.20". The driver's own error path runs with the driver's own message |
| `link` | `GL_LINK_STATUS`, for real — `glLinkProgram` is never called | a GPU that compiles both stages and refuses to link them. Distinct from `compile`, and the failure `GL_MAX_VARYING_FLOATS` warns about |
| `late` or `late:n` | after the driver has already painted `n` command lists (default 24) | a driver that dies mid-session. The only way to reach the mod's in-place rebuild of a live view onto the CPU surface |
| *(mod only)* `probe` | in Java, without asking the driver | the mod's own fallback wiring, on a build whose natives are whatever they are. Read by `WebViews.forcedProbeFailure()`, not here |

A value that is none of these is reported as an error and ignored, rather than silently doing
nothing — a typo here means the test everyone thinks is running is not.

`compile`, `link` and `late` need a real GL context and therefore a real game window. `init` and
`probe` do not, which is the whole reason `init` exists.

### Why no AppCore

AppCore ships stock platform handlers, and it is tempting. It is not linked because on Linux it
pulls **GTK3** and on Windows **D3D11 + D3DCompiler + DirectWrite** (verified from the import
tables), and on every platform it owns window and run-loop creation — which is exactly what
Minecraft already provides. We are a guest in someone else's OpenGL process.

### The one place we reach past the C API

`ulPlatformSetFontLoader` is **declared** in `CAPI_FontLoader.h` but **not exported** by Ultralight
1.4.0b on any platform (checked against `libUltralight.so`, both mac dylibs and `Ultralight.dll`).
Linking it makes the library unloadable. A font loader is nevertheless mandatory — without one
every `ulCreateView` fails with *"tried to create a View but FontLoader was NULL"*.

So `src/font_loader.cpp` implements `ultralight::FontLoader` against the C++ headers and installs it
through `ultralight::Platform::instance()`, which **is** exported from `UltralightCore` on all three
platforms. That is what AppCore does internally. The C++ ABI surface this costs is three symbols
(`Platform::instance`, `FontFile::Create`, `String`'s constructor), resolved from the same SDK whose
headers we compile. If a future SDK exports the C entry point, delete that file.

It resolves every family the page names. Ultralight never fetches an `@font-face` itself — the
declaration only says *which family* to ask for, and the ask arrives here — so the `kFaces` table in
that file is what decides the typeface on screen. It carries the design's own faces, published from
`src/main/resources/assets/void/fonts/` by `build.gradle`: **Outfit at 300, 400 and 500, and nothing
else** (`design/quiet-cell-system.md` §2). Anything not in the table falls back to Inter.

**Consequence, deliberate:** system fonts are not reachable from the page — `font-family: Arial`
gets Inter. That is what we want anyway: one design, identical on every machine. **The trap:** a
family missing from `kFaces` does not error, it silently comes out as Inter, so each served face is
logged once (`font_load: 'outfit' weight 400 -> …`) and a missing line is the symptom.

---

## What is verified, and what is not

### Verified here (Linux, CPU renderer, no display)

`test/run-test.sh` builds the library and runs `CpuRenderTest` — a plain `main`, no framework — on
the classpath shape the mod JAR will actually have, so the run also covers `files.txt`, the temp-dir
extraction and `System.load` ordering. 21 checks, all passing:

```
ultralight      = 1.4.0
webkit          = 615.1.18.100.1
  ok   page finished loading
  ok   flexbox laid the card out at 520px
  ok   evaluateScript returns "" for undefined
  ok   evaluateScript returns "" when the script throws
  ok   evaluateScript returns values
  ok   @font-face 'VoidTest' resolved through the ULFileSystem (document.fonts.check)
  ok   the title text has non-zero measured width
  ok   the CSS transition ran from 120px to 380px
  ok   window.__void_native round-tripped JS -> Java -> JS
  ok   window.__void_native is a function on the page
  ok   mouse / key / scroll events dispatched without a crash
  ok   readPixels() returned 800x480 BGRA
  ok   isDirty() is false immediately after readPixels()
  ok   isDirty() is true after a DOM change repainted the surface
  ok   the card centre is opaque (alpha 254 at 400,240)
  ok   outside the card is fully transparent (alpha 0)
  ok   border-radius clipped the card corner (alpha 13)
  ok   the card area is filled            (99.8% coverage)
  ok   the gradient and text produced a rich colour histogram   (2282 distinct)
  ok   white text was rasterised inside the card
  ok   the box-shadow falls below the card (top-left origin, shadow rendered)
PASSED
```

`test/expected/out.png` is what Ultralight actually drew — check it in, look at it. It is a
transparent-background 800×480 view holding a 520×300 card with `border-radius`, a 3-stop
`linear-gradient`, a `box-shadow`, a 1px border, flexbox rows, a webfont and a completed CSS
transition. **The fidelity question in §9 is answered: Ultralight 1.4 holds the card design.**

So the following are proven on the real engine: the classpath `ULFileSystem` (HTML *and*
`@font-face` served out of a JAR-shaped classpath), the bundled font loader, `evaluateScript`
semantics including the "" contracts, the synchronous `window.__void_native` round trip, mouse/key/
scroll dispatch, `isDirty()` in both directions, CPU readback with stride repacking, transparency,
and top-left origin.

`ctest` runs a second headless test, `accelerated_fallback` (`AcceleratedFallbackTest`), under
`VOID_UI_GPU_FAIL=init`. It is the evidence for the CPU fallback:

```
  ok   the driver is not marked failed before anything has asked it
[voidultralight/error] gpu: VOID_UI_GPU_FAIL=init — the GL driver will fail on purpose
[voidultralight/error] gpu: accelerated rendering is unavailable — VOID_UI_GPU_FAIL=init (forced failure; no GL was touched)
[voidultralight/error] gpu: falling back to the CPU surface. The interface will still work; it will cost considerably more frame time. …
  ok   probeAccelerated() reported the accelerated renderer as unusable
  ok   the failure is visible afterwards through acceleratedDriverFailed()
  ok   a second probe answers false without retrying (the failure is sticky)
  ok   the fallback view is a CPU surface
  ok   the page loaded into the fallback view
  ok   the page laid out in the fallback view
  ok   readPixels() returned 800x480 BGRA from the fallback view
  ok   the fallback view actually rendered content (centre alpha 254)
  ok   and left the rest of the overlay transparent
  ok   the fallback filled the card area (155681 of 156000 pixels)
```

The last four are the point: a machine whose driver will not run gets **pixels**, not a blank
overlay. It also pins one ordering that would be easy to break — inside `rendererRender` the
"no accelerated views exist" branch is taken *before* the driver-failed guard, so a dead GPU driver
must not stop a CPU view from rendering.

### The GL driver, headless (macOS)

`gpu_driver_gl.cpp` opens by calling itself "the half of this binding that cannot be tested off a
real game". That was true while the only GL context in reach belonged to Minecraft. On macOS it is
not: **CGL hands out an offscreen context in the same GL 2.1 / GLSL 1.20 profile LWJGL 2 gives the
game**, with no window, no display and no drawable — enough to compile and link. `ctest` therefore
also runs `test/gpu_driver_probe.cpp` three times:

| test | env | asserts |
|---|---|---|
| `gpu_driver_builds` | — | `probe()` true, `failed()` false, a second probe does not recompile |
| `gpu_driver_fails_compile` | `VOID_UI_GPU_FAIL=compile` | `probe()` false, `failed()` true, sticky |
| `gpu_driver_fails_link` | `VOID_UI_GPU_FAIL=link` | same, through the link path |

The first is the one that could not be run at all before: **every build now proves the GLSL 1.20
port survives a real compiler**, rather than that being a claim resting on one manual in-game run.
The failing two print the real thing:

```
[voidultralight/error] shader fill (fragment) failed to compile: ERROR: 0:491: 'this' : Reserved word.
[voidultralight/error] gpu: accelerated rendering is unavailable — the 'fill' GLSL 1.20 program would not build on this GPU
[voidultralight/error] gpu: falling back to the CPU surface. …
```

Limits, plainly. The context is *this* machine's driver, so a pass says nothing about anyone
else's — that is what the fallback is for, not a gap this closes. It cannot reach
`VOID_UI_GPU_FAIL=late`, which needs a live view and a real paint. And it is macOS-only: GLX
pbuffers on Linux and a hidden WGL window on Windows would both work the same way, and that file is
where the coverage goes the day somebody has the hardware.

So the only thing left needing a game window is `late` — and, of course, whether any of this is true
on a GPU other than an M1 Max.

### Verified in game (macOS 26.5, Apple M1 Max, Minecraft 1.8.9)

The driver now runs, and is now the default (`VOID_UI_RENDERER=cpu` opts out). On a real client it
logs:

```
gl: 2.1 Metal - 90.5 | Apple M1 Max
gl: fbo=1 vao=1 texture_rg=1 npot=1
gpu: GLSL 1.20 driver ready (vao=1, texture_rg=1)
render target: view 3456x1926, texture 3456x1926, uv 1.0000 x 1.0000
```

So the following are answered, on the context the port was written for:

- **The GLSL 1.20 port compiles and links on Apple's compiler.** Both programs, first try. Risk 1
  below (`GL_MAX_VARYING_FLOATS` under 36) did not fire.
- **`ARB_framebuffer_object`, VAOs and `ARB_texture_rg` are all present** on Apple's 2.1 profile
  here, so none of the fallbacks were exercised — they remain untested.
- **Texture orientation is right.** `flip_y = true` puts the page the right way up with the blit's
  `flipV = false`; nothing is upside down.
- **State restoration holds.** The world renders normally across thousands of paints with the menu
  open and closed. Note this is now weaker evidence than it looks: with the UI thread on its own
  context, `save_gl_state`/`restore_gl_state` no longer protect Minecraft from anything (see
  "The UI thread's context" below).
- **Paint cost.** 0.3-0.9 ms mean and 6-8 ms worst per paint of a full-screen 3336x1870 view, on
  the UI thread, against 0.5-1.0 ms mean and 39-42 ms worst for the CPU surface at the same size.
  In game that shows up as the worst frame: 17-28 ms against 40-47.
- **No tearing, with the double buffer.** 130 captures of a live menu, ~30 s, diffed pair by pair
  over a region the page does not animate: zero differing pairs. Without it — publishing
  Ultralight's render target directly — the same probe showed a differing pair every few captures
  and the user saw continuous flicker.

Still not verified: **Windows and Linux GL** (only macOS arm64 has run), and the whole
`EXT_framebuffer_object` / `GL_LUMINANCE8` / no-VAO fallback set, which this GPU does not need.

That is why the driver being the default is only defensible with the probe and the CPU fallback in
front of it. Nothing here says the driver *works* on Windows or Linux. What it now says is that a
machine where it does not gets a slower interface instead of no interface, and says so in the log.

---

## The UI thread's context

The accelerated path runs entirely on the mod's `void-ui` thread — `ulRender` *and* the GL work,
because our `ULGPUDriver` is called from inside `ulRender` on whatever thread called it. That
thread therefore has to own a GL context, and it owns one in Minecraft's share group:
`dev.voidpvp.client.ui.UiGlContext` builds it with LWJGL 2's `SharedDrawable` on the render thread
and makes it current on the UI thread before the first view exists. Three consequences:

- **Textures cross; containers do not.** The view's render target is a shared object, so the game
  thread binds the same texture the UI thread painted. FBOs and VAOs are per-context, and every one
  the driver creates is created and used on the UI thread, so nothing needs them to be shared.
- **`gpu::flush()` after every paint is load-bearing.** GL only promises a shared object is current
  in a second context once the producing context has been flushed. Without it the overlay can sit
  on a stale frame indefinitely.
- **The texture has no lock, deliberately.** A mutex around it would be the game thread waiting on
  a UI-thread paint, which is the one thing the split forbids; what it would prevent is not
  corruption but a blit that samples a half-drawn frame, which looks like a frame boundary. This is
  the opposite call from the CPU surface, whose bitmap *is* under `surface_lock()` — there a torn
  read also drops damage, and the region stops updating until something else dirties it.
- **`save_gl_state`/`restore_gl_state` now guard our own context, not Minecraft's.** They are kept
  because they cost a few `glGet`s off the game thread and are the only thing that would keep the
  driver correct if the paint ever moved back to the render thread.
- **What the presentation copy is gated on has been wrong twice, in opposite directions.** It skips
  frames that did not change the view's target, because a full-screen copy fifty times a second to
  republish identical pixels is exactly the cost the double buffer should not add.

  The first version counted replayed command lists. Ultralight emits *no* command list for a page
  with nothing left to draw, so the counter froze precisely when the overlay had to be cleared: a
  picture of the menu the player had just closed stayed welded to the screen. It now counts frames
  that changed the target — which is what "a frame to present" means — and that part is right.

  The second version also had `draw_command_list` clear the target itself whenever the command list
  came back empty. That is a guess and it is wrong: an empty list means "nothing was submitted this
  frame", which is the ordinary case for a page whose content did not change and whose target
  legitimately still holds the last good frame. In game the menu appeared for a fraction of a
  second and vanished, the first frame it had nothing new to draw. **The driver cannot tell the two
  cases apart and must not try.**

  The host can, and says so: `View.clearTarget()` clears the target once and counts it as a frame,
  and `UiHost.requestRender()` opens a window in which the host may spend it. The mod calls that at
  the two moments the page's content is known to be about to go away — the menu closing
  (`onMenuClosed`) and any loadout change (`emitLoadout`, which is where the last HUD widget being
  switched off arrives).

  **A window, and only ever spent on a frame the page itself is repainting.** It used to be a
  one-shot armed for the next render, on the reasoning that "the clear lands in the same frame as
  the repaint that follows, so a page that still has content just redraws onto a blank target".
  That reasoning is wrong, and it is worth being precise about why, because it looks right.
  `ulViewSetNeedsPaint(true)` is a *request*: Ultralight honours it only if something in the page
  actually changed, and otherwise submits no command list at all — which `draw_command_list`
  rightly refuses to interpret. So on a frame where the page has nothing new, the clear lands and
  the repaint does not, and the blanked target is what `viewTextureId` copies out and publishes.
  Measured in game with a per-frame probe on the close path: the clear armed as `menu:false` was
  emitted fired 6 ms later against a page React had not committed to yet (`isDirty()` false), the
  whole overlay — menu, HUD and all — went blank one frame after the keypress, and ~50 ms later the
  exit fade finally painted and put the menu back at 40% and then 12% opacity for two frames before
  it left. The player saw the menu vanish and then flash.

  `UiHost` now spends the clear only on a frame where `isDirty()` is true, which is exactly a frame
  whose render *will* repaint: with content, the whole view is redrawn over the blank target (and
  `draw_command_list` would have cleared it anyway, so the clear costs nothing); with its content
  gone, the render draws nothing and the blank target is the right answer. A window rather than a
  one shot because the frame that matters is ~130 ms after the close, not the next one.

  Both halves are visible in the `view WxH: N presents/s of M asks/s` line the profiler prints: an
  idle page reads 0 presents, and a page that has just gone blank reads exactly one.

### The whole-view repaint is not pure cost — it was measured

`UiHost.frameOnUiThread` calls `setNeedsPaint(true)` before every accelerated render, and
`draw_command_list` clears the view's target before replaying a list. Those are one decision: the
clear is only safe *because* the whole view is about to be redrawn. It is tempting to read the
clear as making the force redundant on this path — the driver already blanks the target, so why
also ask for a full repaint? — and to expect the force to be the reason a click is slow.

Both were measured in game, GPU renderer, the same 40-second self-test driving the same controls,
with the force and the clear switched off together:

| | forced repaint | incremental |
|---|---|---|
| per-click UI paint, mean | 0.6 ms | 0.3 ms |
| per-click UI paint, median / p90 | 0.1 / 0.2 ms | 0.0 / 0.0 ms |
| click to pixel, mean | 10.7 ms | 10.9 ms |
| menu UI paint, mean / peak | 1.52 / 78.4 ms | 1.71 / 77.7 ms |

So it buys 0.3 ms of mean paint on the thread that is not the game's, and nothing at all on the
number a player feels. Against that: **it is visibly broken.** Thirty seconds after the menu was
closed, a screenshot showed the properties pane's text, switches and buttons still welded over the
game with the panel behind them gone — the damage rectangle drawn onto a target the driver had
just blanked. The forced repaint stays.

The measurement is cheap to repeat if a future Ultralight changes the damage model: make
`WebView.needsFullRepaintEachFrame()` answer false and delete the clear at the top of
`draw_command_list`, together. Do not change one without the other.

## Known risks

**1. The GLSL 1.20 port fails to link on Apple's compiler.** The `fill` program needs 9 `vec4` of
varyings (36 floats). GL 2.0's floor for `GL_MAX_VARYING_FLOATS` is 32. Every GPU that can run
1.8.9 reports 64 or more in practice, but if one does not, the program will not link. The driver
checks the limit at init and logs a specific error rather than leaving you with a blank overlay.
Second-order: Apple's GLSL compiler is stricter than Mesa's about constant-index-expressions, which
is why every dynamic uniform index was fanned out by hand.

**2. State leakage into Minecraft's renderer.** The save/restore in `save_gl_state`/`restore_gl_state`
is thorough but it is a list, and lists have holes. The likely symptom is not a crash but corrupted
world rendering after the first HUD paint — untextured blocks, wrong blending, a missing sky.
Suspect this first for any "the game looks wrong since the mod" report.

**3. ~~The JAR size problem above.~~ Settled — no longer a risk.** 77 MB of natives for three
platforms against a §13 budget of 25 was a real problem; the decision was taken and landed. **One
JAR per platform**: `mod/build.gradle`'s `platformJars` repackages the remapped JAR once per staged
`natives/<os>-<arch>/` tree, the base JAR carries no natives at all (324 KB), and `void-core` picks
the right one at prepare time (`install::ModPlatform`). §13, `pvp/CONTRACTS.md` and `mod/README.md`
all say the same thing, and the measured numbers above are what it is sized against. Kept here as
the record of what moved.

**4. `1.4.0b` is a beta.** The dev bucket's `latest` moves. We pin `081c48b` with a SHA-256, so
nothing changes under us — but there is no stable 1.4 release to move to, and the C API has at least
one genuine hole in it (`ulPlatformSetFontLoader`), which is the kind of thing a beta has.

**4b. ~~A repeating-gradient background stops after the first tile.~~ No longer reachable, but the
defect is real.** Found by comparing the two renderers frame for frame in game: the overlay's
dotted cell rules (`.orule`, a `repeating-linear-gradient` on a 4px-tall element) drew across the
full width on the CPU surface and exactly two cells at the left edge here, in every frame of every
run — stable, not a damage artefact; and the 2D grid on `.preview__frame` tiled but read several
times too bright. It is somewhere in `fillPatternImage` or the pattern uniforms, not in tiling as
such. **Not the sampler wrap mode** — `GL_REPEAT` was tried (this GPU reports `npot=1`, so it was
legal) and changed nothing, which is consistent with the shader tiling with `fract()` rather than
with the sampler.

`packages/ingame` has since removed every `repeating-linear-gradient` — they violated the quiet-cell
contract's "no gradient, anywhere" as well — and the two renderers are now pixel-comparable across
the whole menu, `.orule` included. So nothing in the shipped page hits this. It is left written down
because the driver is still wrong, and the next page that reaches for a tiled background will find
out the hard way.

**5. Ultralight's own renderer threads call our `ULFileSystem`.** `Config.num_renderer_threads`
defaults to auto (3 on a 4-core box, per the log above). Those are not Java threads, so the file
system attaches them to the JVM as daemons on first use. That is handled, but it does mean
`ClassLoader.getResourceAsStream` gets called off the render thread — if the mod ever installs a
class loader that is not thread-safe, this is where it will bite.

**6. Single renderer per process.** Ultralight supports exactly one; `Ultralight.createRenderer`
returns the existing instance on a second call rather than pretending otherwise. Everything must be
driven from the thread that created it.

**7. WebCore aborts if the thread that used it terminates.** Found here, reproduced under gdb. When
a thread that has touched WebCore exits as a *pthread*, glibc runs the thread-local destructors and
`WebCore::ThreadGlobalData::~ThreadGlobalData()` tears down the font cache; `~Font` then calls
`FontCache::forCurrentThread()`, which re-enters `WebCore::threadGlobalData()` **from inside its own
destructor** and aborts in `WTFCrashWithInfo`:

```
#5  WTFCrashWithInfo
#6  WebCore::MainThreadSharedTimer::setFiredFunction
#8  WebCore::ThreadGlobalData::ThreadGlobalData()      <-- re-entered
#9  WebCore::threadGlobalData()
#10 WebCore::FontCache::forCurrentThread()
#12 WebCore::Font::~Font()
#19 WebCore::FontCache::~FontCache()
#20 WebCore::ThreadGlobalData::~ThreadGlobalData()     <-- from __nptl_deallocate_tsd
```

It is independent of us: it fires whether the renderer is closed, purged, or leaked. **`System.exit()`
avoids it entirely** — the process leaves from inside the thread, so the TSD destructors never run.
Minecraft quits exactly that way (`Minecraft.shutdown()` -> `System.exit(0)`), so the game is not
exposed; a headless tool that returns from `main` is, which is why the test harness ends with an
explicit `System.exit`. Verified against 1.4.0b on Linux; assume it holds on macOS and Windows and
do not add a "clean shutdown" path that lets the render thread die on its own.

---

## Layout

```
CMakeLists.txt                   library, Java compile, staging, ctest
cmake/UltralightSDK.cmake        pinned download + SHA-256 + extract, keyed by platform
cmake/WriteNativesManifest.cmake generates files.txt / version.txt
scripts/build.sh                 POSIX build (--arch for macOS cross-configure)
scripts/build.ps1                Windows / MSVC build
assets/fonts/                    Inter (OFL) — the bundled fallback face

src/common.{h,cpp}               globals, JNIEnv attach, classpath + disk reads
src/platform.cpp                 ULLogger + ULFileSystem (classpath, then natives dir)
src/font_loader.cpp              ultralight::FontLoader via the C++ Platform singleton
src/jni_api.cpp                  every JNI entry point
src/js_bridge.cpp                window.__void_native, evaluateScript
src/view_state.h                 per-View native state
src/gl_loader.{h,cpp}            runtime GL entry-point resolution, no linked loader
src/gpu_driver_gl.{h,cpp}        ULGPUDriver on GL 2.1
src/shaders_glsl120.h            Ultralight's fill / fill_path shaders ported to GLSL 1.20

java/dev/voidclient/ultralight/  the Java API (see pvp/CONTRACTS.md)
test/java/…/CpuRenderTest.java   the CPU-renderer smoke test
test/resources/assets/void/ui/   the page it renders
test/expected/out.png            what Ultralight actually drew
test/projection_probe.cpp        measures ulApplyProjection's flip_y
test/run-test.sh                 build + run
```
