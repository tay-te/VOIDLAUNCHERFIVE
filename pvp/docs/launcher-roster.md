# The launcher, scored

`docs/mod-roster.md` exists because a list of mods that is not scored against the code turns
into a wish list, and this document exists because the launcher had no such list at all.

**The failure it is here to prevent has already happened three times, on the mod side.** Menu
blur, shiny pots and input latency each looked like cheap wins for exactly as long as nobody
opened the file: one was finished and switched off on purpose, one was already vanilla behaviour,
and one was a developer instrument for a different subsystem wearing the right name. Every one of
them was scored from a *name* rather than from the code. The launcher is a bigger surface than
the mod registry and, until this file, none of it was scored at all.

So the rule here is the roster's rule: **a row is a claim about code, and the evidence is a file
that runs, never a file that exists.**

---

## 1. What is real

Working, wired to `crates/void-core` through `apps/desktop/src-tauri`, and exercised by
`stores.test.ts` against the same mock backend `pnpm dev:web` runs.

| Area | State | Where |
|---|---|---|
| **Sign in** | Microsoft device-code flow and an offline path | `auth_login` / `auth_offline` / `auth_current` |
| **Install** | Manifest, libraries, assets, client jar, Legacy Fabric, Java 8, the mod jar | `prepare`, with progress events |
| **Launch** | JVM spawn, argument cache, natives, log capture, kill | `launch`, `game_kill`, `game_log_tail` |
| **Join a server** | `--server` / `--port` at spawn — **shipped 2026-09-10**, see §3 | `launch { server }` |
| **Loadouts** | List, create, update, delete, switch, and the live `bridge:state` echo from a running game | `loadouts_*` |
| **Loadout sharing** | Export and import by code, everything included | `@void/protocol`'s `share.ts` |
| **Settings** | Java path, RAM, JVM args, theme, UI scale, keybinds, update channel | `settings_get` / `settings_set` |
| **Server ping** | A real SLP handshake, not an ICMP guess | `server_ping` |
| **Updater, tray, window** | All three | `updater_check`, `tray.rs`, `window.rs` |

That is a working launcher. **The gap is not features.**

## 2. What is a placeholder, and how much of one

Each of these renders the Figma frame faithfully and calls nothing. That is a deliberate,
recorded choice in every case — but it is the exact shape that fooled the mod roster three times,
so each row says what is actually behind it.

| Screen | What exists | What is behind it | Cost to make real |
|---|---|---|---|
| **Friends** | `screens/Friends.tsx`, every action disabled | `local/friends.ts` — **five hardcoded names**. No store, no command, no backend | L, and gated: see §4 |
| **Party (in game)** | `menu/PartyScreen.tsx` | Nothing. `bridge.json` carries no party, presence or queue | L, same gate |
| **Cosmetics** | `screens/Cosmetics.tsx` | Nothing. §16.1 leaves the render Mixin and the asset pipeline to its own doc | L |
| **Auto-switch loadout per server** | A disabled toggle on the Servers detail pane | Nothing. `loadout.json` has a `server` slug and no per-server record to key it on | **S–M, and the best row on this page** |
| **Server favourites** | Works | `localStorage`, with a `TODO(integrate)` | S |

## 3. Shipped

### 2026-09-10 — join a server directly

The Servers screen's Join button launched the game and left the player on the title screen to
find the server themselves. It now connects.

**It needs no mod code, and reading the game is what established that.**
`net.minecraft.client.main.Main` parses `server` and `port` alongside `username`, `uuid` and
`accessToken`; `MinecraftClient.init()` then branches at offsets 960-994 —
`if (serverAddress != null) setScreen(new ConnectScreen(new TitleScreen(), this, serverAddress,
serverPort))` — so the client connects on start and backing out lands on the title screen. Two
launch arguments, nothing else.

**The host is validated, because it leaves the process.** `JoinTarget::new` refuses a host that
begins with `-`, contains whitespace, or carries anything outside `[A-Za-z0-9.\-_:]`. This is not
tidying: the value is appended to a JVM argument list that the client's own parser reads, so a
host beginning with a dash is not a bad hostname, it is a second flag. The realistic version is
not an attack but a paste — a line copied out of a forum post.

**The arguments are appended after substitution, never into the cached template.**
`cache/args/<hash>.json` is keyed by the profile, the natives directory, the memory setting and
the extra JVM arguments — and by nothing about which server was clicked. A `--server` inside the
template would be written once and replayed on every later launch. `launch.rs` already carries a
long note about a cache that went on spawning arguments an earlier build had chosen; this would
have been the same bug with a worse symptom, because joining the wrong server is silent.

**Why it is on this page at all**: "join my friend's game" is this plus knowing where the friend
is. The second half needs a backend; the first half did not, and it is the half every social
feature eventually calls.

## 4. Friends, and the decision it actually needs

Both surfaces are placeholders. Making them real is not mostly UI work, and the dependency order
matters more than the estimate:

1. **Identity.** Accounts today are `microsoft | offline` — a *Mojang* identity. The offline path
   means identity is currently "whatever name you typed", which cannot back a friends list. There
   is no VOID account. This is step zero and it is not small.
2. **Presence.** Half of this already exists and is easy to miss: the mod reports `msg_server` and
   `msg_session` to the launcher over the WebSocket, so the game → launcher hop is built and
   tested. What is missing is launcher → backend.
3. **Invites, and "join my game".** The launch half shipped in §3, so this reduces to publishing
   a friend's current host and offering a button. It is the most-wanted feature and, once
   presence exists, nearly free.
4. **Chat.** Skip it. Everyone is already in Discord, and it is the most expensive piece.

**The cost is not the client.** This is the first thing in the product that needs a server *we
operate*: accounts, a database, rate limits, abuse handling, uptime, and privacy — presence is
location data about a real person. `docs/mod-roster.md` §6.2 puts a hosted backend in the same
bucket as Replay and Minimap, and that judgement was about scope, not difficulty.

**Split by surface, once it exists.** In game, read plus one-click actions — Ultralight takes no
keyboard input while the HUD is up and `bridge.json` has no clipboard call, which is the same
constraint that put loadout sharing in the launcher. Adding a friend by name belongs in the
launcher.

## 5. What to do next, in order

1. **Per-server default loadout** (§16.3). The one launcher row that is a product idea rather
   than plumbing: pick Hypixel, get your Hypixel HUD. Small, and it is the natural pair to the
   join button that just shipped.
2. **The failure surface.** Wrong Java, a 404 in the manifest, a mod jar that does not match the
   version. A PvP client is judged on whether it launches and what it says when it does not, and
   nothing on this page has been scored for that yet — including whether `LogDrawer` helps a
   player or only a developer.
3. **Server favourites out of `localStorage`.** A desktop app storing its own data in the
   webview's storage is a bug with a TODO on it.
4. **Friends**, once §4's decision is made.
