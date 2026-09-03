# `mod/native/` — our JNI binding to Ultralight

C++17, CMake. **The `native` agent fills this in.** Nothing here yet.

Per PVP_ARCHITECTURE.md §6.2 and §13: both public Java bindings are dead
(`LabyMod/ultralight-java`, archived; `Janrupf/ultralight-java-reborn`, last touched
2023), and neither targets Ultralight 1.4. So we write and own this one.

Scope:

- Wrap Ultralight's **C API** over JNI — Java 8 means no Panama. Roughly 150 C functions,
  3–4k lines.
- Implement Ultralight's `GPUDriver` in OpenGL, against **GL 2.1 via LWJGL 2**, which is
  what Minecraft 1.8.9 gives us. The view renders straight into MC's GL context: no CPU
  readback, no full-frame `glTexSubImage2D`.
- Target Ultralight **1.4** (April 2025).
- Built in CI for `win-x64`, `mac-x64` and `mac-arm64`; the resulting libraries plus the
  Ultralight natives ship inside the mod JAR (~25 MB). Linux is out of scope.
- macOS note: 1.8.9 runs on an x64 JVM under Rosetta on Apple Silicon because LWJGL 2 has
  no official arm64 natives, so **mac-x64 is the one that must work**; arm64 is a bonus.
  macOS OpenGL is deprecated but present — verifying the GL path there is part of M1.

This directory is the **M1 gate** (§14). If the binding stalls, Ultralight cannot hold the
card design, or the Mac GL path fails, we learn it before any product code exists. The
fallback is the v1 overlay design, kept in git history.

Licence: the free Ultralight tier applies while both last-fiscal-year turnover and total
funding raised stay under $100k. A credit line from Ultralight's `NOTICES.txt` must appear
in the launcher's About/credits screen (§13).
