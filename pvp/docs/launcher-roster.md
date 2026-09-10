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
| **Where you have played** | The server book — joins, playtime and last-played, written by the running game — **shipped 2026-09-10**, see §3 | `servers_*`, `void_loadout::ServerBook` |
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
| ~~**Auto-switch loadout per server**~~ | A disabled toggle on the Servers detail pane | **Cut 2026-09-10**, by the product owner: loadouts will be ordinary loadouts, not server-bound ones. The toggle and its "needs §16.3" note should come out | — |
| ~~**Server favourites**~~ | Works, and is Rust's now | ~~`localStorage`~~ `servers.json` — **done 2026-09-10** | — |

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

### 2026-09-10 — presence, or the half of Friends that needs no backend

**The launcher has always known where the player is. It was throwing it away.** The mod sends
`server` on connect and `session` every sixty seconds over the bridge; `sync::pump` logged the
first and used the second only for loadout stats. Now both land in `servers.json` — joins,
playtime and last-played, per host.

That matters beyond the Servers screen, because **this record is what a friends backend
publishes.** §4's chain is identity, then presence, then invites; presence was the step everyone
assumes needs a server and it did not. What is left for a backend is sending it somewhere.

**One list, not two.** A server is in the book because it was played on, or starred, or both. Two
lists — favourites and recents — would have to be reconciled every time a favourite was played,
and every bug in that reconciliation looks like a server appearing twice.

**The host had to be normalised to be an identity.** It arrives from two places that disagree
about spelling: a launcher text field, and `MinecraftClient.serverAddress`, which is whatever the
player typed into *the game*. `MC.Hypixel.net`, `mc.hypixel.net` and `mc.hypixel.net:25565` are
one server, and a record keyed on the raw string is three — each holding a third of the playtime,
which is the one number the file exists to get right. `canonical_host` lower-cases and drops an
explicit default port and does nothing else: it deliberately does not resolve DNS, because a
launcher that grouped by resolved address would merge every server behind one proxy.

**Menu time is not server time.** `session.server` is null in the main menu and in singleplayer,
and `server_for_report` returns `None` there rather than falling back to the last host. It is a
plain function with its own test for the reason the mod's sensors are: the pump cannot be
unit-tested and the rule can, and this rule is wrong in a way nobody notices — it would tell a
player they had hours on a server they left before dinner.

**A join and a minute of play are two facts.** They arrive as two messages and are recorded by
two methods. Merging them means either counting a join per telemetry report, or not counting one
until the first report arrives — and a player who alt-F4s in the queue has still joined.

It also retired the `localStorage` favourites list, which §2 had scored as a bug with a TODO on
it, and gave the Servers screen's **Recent** tab something to actually filter on: `joins > 0`. A
server you starred and never joined is a favourite, not a recent.

### 2026-09-10 — continue where you left off

The record's first use, and a bug fix underneath it.

**The Play screen was resolving its server by name-matching a slug.** It compared
`serverName(entry)` against the loadout's advisory `server` field — which agrees for `hypixel` by
coincidence, picks whichever of `na.` and `eu.minemen.club` sorts first for `minemen`, and falls
back to a hardcoded host otherwise. A launcher that keeps a record of where the player has been
does not need to guess: the record answers, and the slug stays as the fallback for a first run,
which is the only case it was ever right about.

**Continue is a second control, not a smarter Launch.** Making the primary button join the last
server would mean the app's main action quietly did something different depending on history —
press it expecting the title screen, arrive in somebody's Bedwars lobby. Two buttons say two
things, and the one that connects names where it is going.

It is **absent rather than disabled** when there is nowhere to resume. A disabled control promises
something is coming; on a fresh install there is nothing to come. The button appears the first
time a session finishes, which is the moment it starts meaning something.

**`resumeTarget` is derived, never stored.** A `last_server` field would be a second copy of what
the book already holds — written on every connect, and wrong the moment the server it points at is
forgotten. It keys on `joins > 0`, not `favourite`: starring is a bookmark, and offering to
continue somewhere the player has never been is guessing and calling it memory.

## 4. Friends, and the decision it actually needs

Both surfaces are placeholders. Making them real is not mostly UI work, and the dependency order
matters more than the estimate:

1. **Identity.** Accounts today are `microsoft | offline` — a *Mojang* identity. The offline path
   means identity is currently "whatever name you typed", which cannot back a friends list. There
   is no VOID account. This is step zero and it is not small.
2. ~~**Presence.**~~ **Shipped 2026-09-10** — see §3. The game → launcher hop was already built;
   the record it feeds now exists and is the shape a backend would publish. What is left here is
   launcher → backend, which is step 1's problem and not this one's.
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

1. **The failure surface.** Wrong Java, a 404 in the manifest, a mod jar that does not match
   the version. A PvP client is judged on whether it launches and what it says when it does not,
   and nothing on this page has been scored for that — including whether `LogDrawer` helps a
   player or only a developer. This is now the top row, because §16.3 was cut.
2. **Persisted ping history.** `pings` is twelve samples in memory, gone on restart. The book is
   the natural home for a rolling record, which turns "42 ms" into "usually 45, 120 right now" —
   `docs/mod-roster.md` §5's *connection quality* row, arriving through the launcher rather than
   the HUD, and the part of it neither competitor ships.
3. ~~**Server favourites out of `localStorage`.**~~ Done 2026-09-10.
4. **Friends**, once §4's decision is made.
