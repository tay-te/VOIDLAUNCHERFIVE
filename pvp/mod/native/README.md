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
platform, not three. Two ways out, both outside this directory's ownership:

1. **Per-OS mod JARs.** `void-core` already resolves and downloads the client JAR per launch
   (§12.3) and knows the target OS, so picking `void-client-<os>-<arch>.jar` costs nothing.
2. **Natives as a separate download**, verified by hash and cached like any other asset — the
   launcher's download pipeline already does SHA-1 verification and hash caching.

Recommended: (1). Raise it with `core` before M2.

### Gradle snippet for the mod build

Drop this in `mod/build.gradle`. It copies whichever staged trees exist into the JAR and fails
loudly if none do, rather than shipping a JAR that dies at `Ultralight.load()`.

```gradle
// ---- Ultralight natives, produced by mod/native/scripts/build.{sh,ps1} ------------------------
// Each `natives/<os>-<arch>/` tree is self-contained: the binding, Ultralight's runtime libraries,
// resources/ and the files.txt manifest NativeLoader reads. Nothing here is built by Gradle — the
// `native` agent owns that build and CI publishes the trees.

def nativeStages = [
    'windows-x64': file("$projectDir/native/build-win/natives/windows-x64"),
    'macos-x64'  : file("$projectDir/native/build-macx64/natives/macos-x64"),
    'macos-arm64': file("$projectDir/native/build-macarm64/natives/macos-arm64"),
]

// Local dev: a single host build lands in native/build/natives/<key>/.
file("$projectDir/native/build/natives").listFiles()?.each { d ->
    if (d.directory) nativeStages.putIfAbsent(d.name, d)
}

tasks.register('copyUltralightNatives', Copy) {
    description = 'Stages Ultralight + voidultralight natives into the JAR under natives/<os>-<arch>/'
    into layout.buildDirectory.dir('ultralight-natives')
    nativeStages.each { key, dir ->
        if (dir.directory) {
            from(dir) { into "natives/$key" }
        }
    }
    doFirst {
        def present = nativeStages.findAll { it.value.directory }.keySet()
        if (present.isEmpty()) {
            throw new GradleException(
                'No Ultralight natives staged. Run mod/native/scripts/build.sh (or build.ps1) first, '
                + 'or fetch the CI artifacts into mod/native/build-*/natives/.')
        }
        logger.lifecycle("Ultralight natives: ${present.join(', ')}")
    }
}

// The API classes. Prefer compiling them with the mod (one javac, one --release 8) so that a
// signature change is a compile error rather than a runtime NoSuchMethodError.
sourceSets.main.java.srcDir "$projectDir/native/java"

processResources.dependsOn tasks.named('copyUltralightNatives')
sourceSets.main.resources.srcDir layout.buildDirectory.dir('ultralight-natives')

// Java 8 target, per PVP_ARCHITECTURE: 1.8.9 runs on a Java 8 JVM.
tasks.withType(JavaCompile).configureEach {
    options.release = 8
}

// The natives are already compressed binaries; re-deflating them costs build time for nothing.
tasks.named('jar', Jar) {
    entryCompression = ZipEntryCompression.DEFLATED
    filesMatching(['natives/**/*.dll', 'natives/**/*.dylib', 'natives/**/*.so']) {
        // keep deflate: STORED would add ~150 MB. Listed here so the choice is visible.
    }
}
```

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

The driver is initialised lazily on the first `render()` of an accelerated view, because entry
points must come from a context that exists — there is none at mod init. If initialisation fails
it is logged once and accelerated views stop painting rather than crashing the game.

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

It hands back one bundled font (Inter) for every family. **Consequence, deliberate:** system fonts
are not reachable from the page — `font-family: Arial` gets Inter. The UI must declare what it wants
with `@font-face`, which is served by our `ULFileSystem` from the classpath. That is what we want
anyway: one design, identical on every machine.

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

### NOT verified — this is the M1 gate

- **The OpenGL driver has never executed.** There is no display in this environment, so not one GL
  call in `gpu_driver_gl.cpp` has run. It compiles; that is all that is claimed. The GLSL 1.20 port
  has never been through a driver's compiler.
- **macOS GL 2.1 specifically.** The whole reason the port exists.
- **State restoration against MC's renderer.** The save/restore list is derived from what the driver
  touches, not from watching MC break.
- **Windows and macOS builds.** Only `linux-x64` has been compiled. MSVC and Xcode toolchain notes
  above are written from the SDK's own requirements, not from a green build.
- **Paint cost (§10, ≤ 0.5 ms at 1080p).** Not measurable without a GPU.

The first thing to do with a real game is: launch, create one accelerated view, and check the log
for `gpu: GLSL 1.20 driver ready`. If the shaders fail to compile, the info log is printed verbatim.

---

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

**3. The JAR size problem above.** 77 MB of natives for three platforms against a §13 budget of 25.
Needs a decision from `core`, not a fix here.

**4. `1.4.0b` is a beta.** The dev bucket's `latest` moves. We pin `081c48b` with a SHA-256, so
nothing changes under us — but there is no stable 1.4 release to move to, and the C API has at least
one genuine hole in it (`ulPlatformSetFontLoader`), which is the kind of thing a beta has.

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
