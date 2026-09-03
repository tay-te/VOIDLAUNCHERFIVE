# `mod/` — the `void-client` Minecraft mod

Legacy Fabric, Minecraft 1.8.9, Java 8 target. **The `mod` agent fills this in.**

Scaffolded here so far: `settings.gradle` (`rootProject.name = 'void-client'`) and the
package skeleton under `src/main/java/dev/void/client/`. Everything else — `build.gradle`,
the Loom setup, `fabric.mod.json`, the mixin config — is the mod agent's to write.

Per PVP_ARCHITECTURE.md §6, the packages are:

| Package | Holds |
|---|---|
| `mixin/` | Sensors (§6.6) and actuators (§6.7), one class per feature |
| `ui/` | The Ultralight host: view lifecycle, GL upload, input forwarding (§6.2) |
| `bridge/` | The `window.void` object — see `schema/bridge.json` (§6.5) |
| `net/` | Netty WS client to the Rust launcher — see `schema/protocol.json` (§6.9) |
| `screen/` | `VoidMenuScreen`: mouse release + GL backdrop blur (§6.4) |

Two things arrive from outside this directory and must not be duplicated inside it:

- **`mod/native/`** is the `native` agent's. It is the JNI binding to Ultralight's C API
  and the OpenGL `GPUDriver`. This agent consumes the loaded library; it does not build it.
- **`src/main/resources/assets/void/ui/`** is the build output of `packages/ingame`. The
  Ultralight host loads the in-game bundle from that classpath path. Do not hand-edit
  anything under it and do not commit generated bundles; the Gradle build copies them in.

Connection parameters come from the launcher as JVM properties: `-Dvoid.port` and
`-Dvoid.token`. There are **no config files on the Java side** (§6.1) — state arrives in
the `init` message and is mirrored back on change.
