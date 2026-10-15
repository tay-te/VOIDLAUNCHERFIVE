# VOID PvP online contract

The shared launch contract for the online half of the VOID PvP client — cosmetics, emotes,
friends, presence and the Store — and the record of how the backend that implements it was
built. Part 1 is the contract every workstream agreed on, copied from the launch brief. Part 2
is the backend as built: the HTTP surface, the database, the Realtime topology, the bridge
messages and the launcher's commands. Where Part 2 refines Part 1 it says so and why.

Backend: Supabase (Postgres + Auth + Edge Functions + Realtime), in `pvp/backend/`. Payments:
Stripe Checkout, opened in the system browser; a webhook grants ownership. Deploy steps are in
`pvp/backend/README.md`.

---

## Part 1 — the contract (all workstreams)

### Slots and items

Slots (one item each): `cape`, `back` (wings and other back items), `head` (hats), `face`
(masks), `halo` (floats above head; may coexist with a hat), `trail` (particles while moving),
`badge` (icon beside nametag). Emotes are not a slot: a player has up to 8 `emotes` on their
wheel.

Item id: kebab-case, globally unique (e.g. `void-cape-rift`). Kinds = slot names + `emote`.

Equipped set (per player), JSON:

```json
{ "uuid": "dashed-mc-uuid", "cape": "id|null", "back": null, "head": null, "face": null, "halo": null,
  "trail": null, "badge": "void-badge-founder", "emotes": ["emote-wave", "..."], "tints": { "cape": "#9f8bff" } }
```

`tints` is optional per-slot recolour for items flagged `tintable`.

### Assets

Every launch cosmetic is BUNDLED in the mod jar under `assets/void/cosmetics/<id>/` (model
JSON, textures, animation), so rendering never waits on the network. The backend only says who
owns/wears what. Catalog metadata (id, kind, name, rarity, price_cents, tintable, preview) lives
in `pvp/schema/cosmetics.json` (single source), generated into TS (launcher/ingame), Java (mod)
and seeded into Supabase.

### Data flow — the mod never talks HTTP; Rust owns network and auth

- Launcher (Rust `void-online` crate) authenticates to Supabase by proving the player's
  Minecraft identity (see Part 2, *Auth*, for the as-built method) and receives a session
  keyed by MC UUID. Offline accounts get no online features.
- Launcher → mod over the existing localhost bridge WS, inbound message
  `{"t":"cosmetics","self":<EquippedSet>,"players":[<EquippedSet>...]}` (players = other VOID
  users' sets, delta).
- Mod → launcher: `{"t":"cosmetics_query","uuids":[...]}` with UUIDs of players it currently
  sees (batched, debounced, cached); launcher batch-fetches from Supabase and replies with
  `cosmetics` messages.
- Emotes: mod → launcher `{"t":"emote","id":"emote-wave"}` when the local player emotes;
  launcher broadcasts on a Supabase Realtime channel keyed by the server address; remote emotes
  come back as `{"t":"emote","uuid":"...","id":"...","at":<ms>}`. Presence on the same channel
  tells which VOID players share the server (friends + "VOID player" nametag badge).
- Equip from the in-game wardrobe: Ultralight UI → `window.void.equipCosmetic(slot, id|null)`
  bridge call → Java applies locally at once and forwards `{"t":"equip","slot":"cape","id":"..."}`
  to the launcher, which persists to Supabase. Launcher Store equips the same way.
- Purchases: launcher calls edge function `checkout` → Stripe Checkout URL opened in the system
  browser; Stripe webhook grants the entitlement; launcher refreshes entitlements (poll +
  realtime).

Schemas: `pvp/schema/protocol.json` (WS messages — the five above are in it),
`pvp/schema/bridge.json` (window.void calls/events, the cosmetics workstream's).

---

## Part 2 — as built

### Identity

A player is their Minecraft UUID. The Supabase auth user's `id` **is** the dashed MC UUID
(the admin API accepts an explicit id), so `auth.uid()` in every policy and function is the
player's UUID with no mapping table. `public.players.uuid` references `auth.users.id`.

### Auth (`auth-minecraft`)

**Refinement of Part 1.** Part 1 had the launcher send the Minecraft access token to the edge
function, which would check it against `api.minecraftservices.com/minecraft/profile`. That
works, and the function still accepts it (`mode: "token"`), but it hands a full-power Minecraft
credential (it can change the player's skin and name for ~24 h) to our server. The launcher
therefore uses the method vanilla servers and other PvP clients use, which proves the identity
without the token ever leaving the player's machine except to Mojang:

1. `POST auth-minecraft {"mode":"challenge"}` →
   `{"server_id":"<40 hex>","ts":<unix s>,"nonce":"<32 hex>"}`. `server_id` is
   `HMAC-SHA256(AUTH_CHALLENGE_SECRET, "<ts>.<nonce>")` truncated to 40 hex: stateless, nothing
   is stored.
2. The launcher calls Mojang itself:
   `POST https://sessionserver.mojang.com/session/minecraft/join`
   `{"accessToken":"<mc token>","selectedProfile":"<undashed uuid>","serverId":"<server_id>"}`
   → `204`.
3. `POST auth-minecraft {"mode":"join","name":"<mc name>","ts":..,"nonce":".."}`. The function
   recomputes `server_id`, rejects a `ts` older than 120 s, and asks Mojang
   `GET https://sessionserver.mojang.com/session/minecraft/hasJoined?username=<name>&serverId=<server_id>`
   → `200 {"id":"<undashed>","name":".."}` proves it.

`mode: "token"` (`{"mode":"token","access_token":".."}`) verifies with
`GET https://api.minecraftservices.com/minecraft/profile` instead (`200` → `{id,name}`, `401`
bad token, `404` no Minecraft licence). Kept for the contract and as an operational fallback;
the launcher does not use it unless built with `AuthMode::Token`.

Either way the function then upserts `players`, ensures an auth user with `id = uuid` and a
synthetic, undeliverable email `<undashed-uuid>@players.void.invalid`, and mints a one-time
login with `POST /auth/v1/admin/generate_link {"type":"magiclink","email":..}`. It answers:

```json
{ "player": { "uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5", "name": "Notch" },
  "token_hash": "<hashed_token>", "verification_type": "magiclink" }
```

The launcher redeems it: `POST {SUPABASE_URL}/auth/v1/verify {"type":"magiclink","token_hash":".."}`
(header `apikey: <anon key>`) → a normal Supabase session
`{"access_token","refresh_token","expires_in","expires_at","token_type","user"}`. It refreshes
with `POST /auth/v1/token?grant_type=refresh_token {"refresh_token":".."}` a minute before
`expires_at`, and re-runs the whole exchange if a refresh fails. Nothing is persisted: the
Supabase session lives in launcher memory only.

Why this shape (and not a self-signed JWT): Supabase projects now sign sessions with
asymmetric keys the project never exposes, so minting our own JWT would mean importing and
custodying a signing key. A real auth user gets real refresh tokens, revocation (ban the user),
Realtime authorization and `auth.uid()` in RLS for free. The token hash is redeemed by the
client rather than the function so GoTrue's per-IP verification rate limit applies per player,
not to the one IP all edge functions share.

### HTTP surface

Base `{SUPABASE_URL}/functions/v1/<name>`. Every request carries `apikey: <anon key>`;
authenticated ones also `Authorization: Bearer <access_token>`. JSON in and out. Errors are
`{"error":"<code>","message":"<sentence a player can read>"}` with a matching status; `429`
carries `Retry-After` (seconds).

| Function | Auth | Request | Response |
|---|---|---|---|
| `auth-minecraft` | none | see *Auth* | see *Auth* |
| `cosmetics-lookup` | user | `POST {"uuids":[..≤100]}` or `GET ?uuids=a,b,c` | `{"sets":[EquippedSet..]}` — only VOID players appear; a VOID player wearing nothing appears with all nulls. `Cache-Control: private, max-age=30`, `ETag`, honours `If-None-Match` (304). |
| `equip` | user | `{"slot":"cape","id":"x"\|null}` or `{"emotes":[..≤8]}` or `{"tints":{"cape":"#rrggbb"\|null}}` | `{"equipped":EquippedSet}`. `403 not_owned`, `400 wrong_slot`, `404 unknown_item`, `400 not_tintable`. |
| `checkout` | user | `{"item":"void-cape-rift"}` | `{"url":"https://checkout.stripe.com/..","session_id":"cs_.."}`. `409 already_owned`, `404 unknown_item`, `400 not_for_sale` (free or inactive). |
| `stripe-webhook` | Stripe signature | Stripe event | `200 {"received":true}` |
| `friends` | user | `{"action":"list"}`, `{"action":"request","name":".."}`, `{"action":"accept"\|"decline"\|"cancel"\|"remove","uuid":".."}` | `FriendsList` (below), always the whole updated list. `404 unknown_player`, `409 already_friends`, `409 already_sent`, `400 self`, `400 invalid_name`, `429 limit`. |

PostgREST RPCs, `POST {SUPABASE_URL}/rest/v1/rpc/<fn>` with the user's bearer token (RLS and
`auth.uid()` apply). Errors are PostgREST's `{"code","message","details","hint"}`: the SQL
raises `errcode 'PT<status>'` (PostgREST turns that into the HTTP status), `message` is the
same machine code the functions use (`not_owned`, ...) and `details` the player-readable
sentence. Clients map that to the functions' `{error, message}`. PostgREST's own errors keep
their codes — notably an expired token is `401 PGRST301`, which the client answers with one
token refresh and a retry.

| RPC | Body | Returns |
|---|---|---|
| `my_cosmetics` | `{}` | `{"owned":["id",..],"equipped":EquippedSet}` — owned = live entitlements ∪ every active free catalog item |
| `presence_heartbeat` | `{"server":"mc.hypixel.net"\|"host:port"\|null}` | `{"topic":"server:<32 hex>"\|null,"server":"<normalised>"\|null}` |
| `presence_offline` | `{}` | `null` |
| `emote` | `{"id":"emote-wave"}` | `{"at":<ms>}` — the DB broadcasts it to the player's current server topic. `403 not_owned`, `409 no_server`, `429 too_fast` (1 per second). |

`FriendsList`:

```json
{ "friends":  [ { "uuid": "..", "name": "Moss",
                  "presence": { "state": "online", "server": "mc.hypixel.net", "last_seen": 1760527200000 } } ],
  "incoming": [ { "uuid": "..", "name": "marrow", "at": 1760527200000 } ],
  "outgoing": [ { "uuid": "..", "name": "tavi",   "at": 1760527200000 } ] }
```

`presence.state` is `online` while the friend's launcher has heartbeated in the last 90 s;
`server` is present only while online and on a server; `last_seen` (Unix ms) is always present
once they have ever been seen. A launcher that dies without signing out goes stale after 90 s
without a push; friends see it on their next 60 s poll.

Server addresses are normalised one way everywhere (SQL `public.normalize_server`, Rust
`void_online::normalize_server`): trimmed, lowercased, trailing dot dropped, `:25565` dropped.
`mc.hypixel.net` and `hypixel.net` are different servers to us.

### Database (`pvp/backend/supabase/migrations/`)

| Table | What | Client access (RLS) |
|---|---|---|
| `players` | `uuid` (pk = `auth.users.id`), `name`, `created_at`, `last_seen_at` | read: any authenticated user (names are public in Minecraft) |
| `catalog` | `id`, `kind` (slot or `emote`), `name`, `rarity`, `price_cents`, `tintable`, `preview`, `active`, `stripe_price_id` | read: everyone |
| `entitlements` | `(player, item)` pk, `source` (`stripe`/`grant`/`free`), `stripe_session_id` (unique), `stripe_payment_intent`, `granted_at`, `revoked_at` | read own |
| `equipped` | one row per player: the seven slot columns (FK to `catalog`), `emotes text[]`, `tints jsonb`, `updated_at`; a trigger rejects anything not owned, in the wrong slot, >8 emotes, or a tint on a non-tintable item | read: any authenticated user; write: functions only |
| `friend_requests` | `(from_player, to_player)` | read where you are either end |
| `friendships` | `(a, b)` with `a < b` | read where you are either end |
| `presence` | `player` pk, `server`, `server_topic`, `online_at`, `updated_at`, `last_emote_at` | read own and friends' |
| `purchases` | one row per Checkout Session: player, item, status (`pending`/`paid`/`expired`/`refunded`), amount, currency | read own |
| `stripe_events` | processed Stripe event ids (webhook idempotency) | none |
| `audit_log` | grants, revocations, refunds, admin actions | none |
| `rate_limits` | fixed-window counters | none |

Free items: a trigger grants every active free catalog item to a new player (`source: free`),
`seed-catalog` backfills them when the catalog gains one, and `owns()` treats any active free
item as owned regardless, so a free item can never be missing.

Every write goes through a `security definer` function that checks `auth.uid()` (or is granted
to `service_role` only). No client role has insert/update/delete on any table.

### Realtime topology

One websocket per launcher: `wss://<ref>.supabase.co/realtime/v1/websocket?apikey=<anon>&vsn=1.0.0`.
All channels are **private** (`config.private = true`), so `realtime.messages` RLS decides who
may join, listen and send.

| Topic | Who may join | Carries |
|---|---|---|
| `server:<32 hex>` — sha256 of the normalised address, from `presence_heartbeat` | a player whose own `presence.server_topic` is that topic (heartbeat first, then join) | **broadcast** `emote` `{uuid,id,at}`, sent only by the database after the `emote` RPC checked ownership — clients cannot broadcast here, so the sender is never spoofable. **presence** keyed by the player's UUID, meta `{uuid,name}`: who of VOID is on this server right now. |
| `user:<uuid>` | that player only | **broadcast** from the database: `entitlements` `{item,source}` (a purchase landed), `friends` `{kind:"request"\|"accepted"\|"removed"\|"presence",uuid}` |

Presence metas are client-claimed (Realtime does not let RLS read them), so they are treated as
hints: the launcher uses them only to prefetch cosmetics, and the "VOID player" badge comes from
`cosmetics-lookup` (a UUID is a VOID player iff it has a `players` row). Friend presence comes
from the `presence` table, which only the player can write.

Wire details (Phoenix v1 JSON): join `{"topic":"realtime:<topic>","event":"phx_join","payload":{"config":{"broadcast":{"ack":false,"self":false},"presence":{"key":"<uuid>","enabled":true},"postgres_changes":[],"private":true},"access_token":"<jwt>"},"ref":"<n>","join_ref":"<n>"}`
(`presence.enabled` is `true` on server topics, where presence is tracked, and `false` on the
user topic);
heartbeat `{"topic":"phoenix","event":"heartbeat","payload":{},"ref":"<n>"}` every 25 s;
presence track `{"event":"presence","payload":{"type":"presence","event":"track","payload":{..}}}`;
token refresh `{"event":"access_token","payload":{"access_token":".."}}` on every joined topic;
incoming `phx_reply`, `presence_state`, `presence_diff`, `broadcast`
(`payload: {"type":"broadcast","event":"emote","payload":{..}}`), `phx_error`, `phx_close`,
`system`.

### Bridge (launcher ⇄ mod)

`schema/protocol.json`: up `cosmetics_query {uuids}`, `emote {id}`, `equip {slot,id|null}`;
down `cosmetics {self,players}`, `emote {uuid,id,at}`. The launcher:

- on `hello`, sends `cosmetics` with `self` (or `null` offline) and every set it has cached for
  players on the current server;
- on `server {host,connected}`, heartbeats the new server, joins its channel, and on leaving
  leaves it;
- on `cosmetics_query`, answers at once with whatever is cached, then asks `void-online`'s
  batcher (150 ms debounce, 100 per request, LRU of 2048 sets with a 5 min TTL, "not a VOID
  player" cached too) and forwards new or changed sets as `cosmetics` deltas;
- on `emote`, calls the `emote` RPC; remote emotes from the server channel go down as `emote`;
- on `equip`, calls `equip`; on refusal it sends `cosmetics` with the stored `self`, which
  reverts the mod's optimistic change.

### Launcher commands (`apps/desktop/src-tauri/src/ipc.rs`)

| Command | Args | Returns |
|---|---|---|
| `online_status` | — | `OnlineStatus` `{state:"disabled"\|"signed_out"\|"connecting"\|"online"\|"error", uuid?, name?, message?}` |
| `cosmetics_state` | — | `{online:boolean, owned:string[], equipped:EquippedSet\|null}` |
| `cosmetics_equip` | `{slot, id\|null}` | `EquippedSet` |
| `cosmetics_set_emotes` | `{ids}` | `EquippedSet` |
| `store_checkout` | `{item}` | `{url, session_id}` — Rust opens `url` in the system browser |
| `friends_list` | — | `FriendsList` |
| `friends_request` | `{name}` | `FriendsList` |
| `friends_accept` / `friends_decline` / `friends_cancel` / `friends_remove` | `{uuid}` | `FriendsList` |

Events: `online:status` (`OnlineStatus`), `online:cosmetics` (`cosmetics_state`'s shape),
`online:friends` (`FriendsList`).

### Configuration

`VOID_SUPABASE_URL` and `VOID_SUPABASE_ANON_KEY` are read **at build time** (`option_env!`) by
`void-online`; the same names in the runtime environment override them (dev only — the anon key
is public by design). With either absent the crate is in *disabled* mode: every call returns
`Error::Disabled`, the launcher shows its local state, and nothing touches the network.
