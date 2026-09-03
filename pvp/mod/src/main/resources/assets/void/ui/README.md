# Build output — do not hand-edit

`packages/ingame` builds the in-game HUD + menu bundle into this directory. The Ultralight
host in `dev.void.client.ui` loads it from this classpath path
(`assets/void/ui/index.html`), so the mod JAR ships the UI inside it.

Everything here except this file is generated and gitignored. Budget: **≤ 400 KB gzipped**
(PVP_ARCHITECTURE.md §10), checked in CI.
