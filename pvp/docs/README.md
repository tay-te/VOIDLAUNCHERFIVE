# `docs/`

Design and decision documents for `void-pvp`.

| Document | What it is |
|---|---|
| [`adding-a-mod.md`](adding-a-mod.md) | **The procedure.** Six files, in order, and what is enforced at each step. Start here to add a mod. |
| [`mod-roster.md`](mod-roster.md) | What Lunar and Badlion ship, what VOID has, what to build and in what order. §9 is the per-mod tax and what is left of it. |
| [`TESTING.md`](TESTING.md) | What CI proves and what needs a real machine. |

The governing contract lives outside this repo for now, in `VOIDLAUNCHERFIVE`:
`docs/PVP_ARCHITECTURE.md`. Section references throughout this monorepo (§4, §6.5, §7, §8,
…) point at it. Move it here once the split is finished.

Open questions that need their own document, from §16: cosmetics rendering and asset
pipeline; friends/party; server pings and server-bound default loadouts; the ⌘K palette;
code signing and notarization.
