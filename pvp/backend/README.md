# `backend/` — the VOID PvP online backend

Supabase (Postgres + Auth + Edge Functions + Realtime) for everything the launcher does online:
signing in with a Minecraft account, cosmetics ownership and equipped sets, the Store's Stripe
Checkout, friends, presence, and emotes. The contract — HTTP surface, Realtime topology, bridge
messages, launcher commands — is [`../docs/online-contract.md`](../docs/online-contract.md).

```
supabase/
  config.toml                      project settings (auth locked down, functions verify in code)
  migrations/                      schema, RLS, functions — applied in filename order
    20261015120000_core.sql        players, catalog, entitlements, equipped, rate limits
    20261015120100_social.sql      friends, presence, emotes, Realtime authorization
    20261015120200_commerce.sql    purchases, Stripe idempotency, audit log, grants
    20261015120300_auth_hardening.sql  no passwords or email changes on player accounts
  functions/                       Deno edge functions (no imports beyond web standards)
    _shared/                       http, Supabase REST client, Mojang, Stripe, guards
    auth-minecraft/  cosmetics-lookup/  equip/  checkout/  stripe-webhook/  friends/
  seed.sql                         generated catalog seed for local `supabase db reset`
scripts/seed-catalog.mjs           pvp/schema/cosmetics.json → catalog (SQL or REST upsert)
tests/                             node --test: SQL in PGlite, functions against a fake network
```

The Rust client is `crates/void-online`; the launcher wiring is in `apps/desktop/src-tauri`.

---

## Deploying

Nothing here has been deployed yet. Everything below is what has to happen once, in order.
Commands run from `pvp/backend/` unless they say otherwise.

### 0. Accounts and tools

- A **Supabase** account and a new project (dashboard → New project). Pick the region closest to
  most players. Note the project ref (`https://<ref>.supabase.co`) and the database password.
- A **Stripe** account. Everything works in test mode first; switch the keys to live at the end.
- The **Supabase CLI** ≥ 2.x: `scoop install supabase` (Windows), `brew install supabase/tap/supabase`
  (macOS), or `npx supabase@latest …` anywhere. Docker is only needed for local `supabase start`.
- `openssl` (Git Bash has it) for generating a secret.

### 1. Link and push the schema

```sh
supabase login
supabase link --project-ref <ref>          # asks for the database password
supabase db push                           # applies supabase/migrations/*
supabase config push                       # sends config.toml's [auth] settings to the project
```

If `config push` is not available in your CLI, set the same thing in the dashboard:
**Authentication → Sign In / Providers**: *Allow new users to sign up* **off**, *Allow anonymous
sign-ins* **off**, *Allow manual linking* **off**; Email provider: *Confirm email* off (no mail is
ever sent). The admin API the edge function uses is unaffected by "sign up off".

### 2. Realtime

**Project Settings → Realtime**: turn **off** *Allow public access* (so only private channels
exist — every VOID channel is private and authorized by the policies in `social.sql`).

### 3. Secrets for the edge functions

`SUPABASE_URL`, `SUPABASE_ANON_KEY` and `SUPABASE_SERVICE_ROLE_KEY` are injected automatically.
Set the rest:

```sh
supabase secrets set \
  AUTH_CHALLENGE_SECRET=$(openssl rand -hex 32) \
  STRIPE_SECRET_KEY=sk_test_... \
  STRIPE_WEBHOOK_SECRET=whsec_... \
  CHECKOUT_SUCCESS_URL='https://<your site>/store/thanks?session={CHECKOUT_SESSION_ID}' \
  CHECKOUT_CANCEL_URL='https://<your site>/store/cancelled'
```

| Secret | Required | What |
|---|---|---|
| `AUTH_CHALLENGE_SECRET` | yes | ≥ 32 chars. Keys the stateless sign-in challenge. Rotating it only invalidates challenges in flight (2 min). |
| `STRIPE_SECRET_KEY` | for the Store | `sk_test_…` / `sk_live_…`, or a restricted key with *Checkout Sessions: write*. |
| `STRIPE_WEBHOOK_SECRET` | for the Store | The endpoint's signing secret from step 5. |
| `CHECKOUT_SUCCESS_URL` / `CHECKOUT_CANCEL_URL` | for the Store | Where the browser lands after paying / backing out. Any page that says "you can close this tab and return to VOID". Edge functions cannot serve HTML on the `supabase.co` domain, so these belong on the marketing site (`apps/web`) — it needs those two pages. `{CHECKOUT_SESSION_ID}` is substituted by Stripe. |
| `STRIPE_CURRENCY` | no | Default `usd`. Catalog prices are in this currency's minor unit. |
| `PLAYER_EMAIL_DOMAIN` | no | Default `players.void.invalid`. The synthetic, undeliverable email domain for player accounts. |
| `VOID_PUBLISHABLE_KEY` / `VOID_SECRET_KEY` | no | Set these if the project has moved to the new `sb_publishable_…` / `sb_secret_…` API keys and the legacy keys are disabled. |

Without the three Stripe values `checkout` answers `503 payments_unavailable` ("The Store isn't
open yet. Nothing was charged."); everything else works.

### 4. Deploy the functions

```sh
supabase functions deploy          # all six; config.toml sets verify_jwt = false on each
```

(`verify_jwt = false` is deliberate: each function verifies its caller itself — see Security
notes. If you deploy one by name with an older CLI, add `--no-verify-jwt`.)

Smoke test — this should answer with a `server_id`:

```sh
curl -s -X POST https://<ref>.supabase.co/functions/v1/auth-minecraft \
  -H "apikey: <anon key>" -H "content-type: application/json" -d '{"mode":"challenge"}'
```

### 5. Stripe

1. **Developers → API keys**: copy the secret key into `STRIPE_SECRET_KEY` (step 3).
2. **Developers → Webhooks → Add endpoint**: URL
   `https://<ref>.supabase.co/functions/v1/stripe-webhook`, events:
   `checkout.session.completed`, `checkout.session.async_payment_succeeded`,
   `checkout.session.expired`, `charge.refunded`, `charge.dispute.created`.
   Copy the signing secret into `STRIPE_WEBHOOK_SECRET` and re-run `supabase secrets set`.
3. **Products and prices**: nothing to create. By default `checkout` charges the catalog's
   `price_cents` through `price_data`, so `cosmetics.json` is the price list and a price change is
   a re-seed. To use Stripe-managed prices instead (for tax codes, multi-currency, or reporting by
   product), create a Product + one-time Price per item and put its id in the item's
   `stripe_price_id` in `cosmetics.json`; that item then charges that Price.
4. **Settings → Checkout**: set the business name, icon and brand colour players will see; enable
   the payment methods you want (cards at minimum). Receipts: **Settings → Customer emails →
   Successful payments** on, so "receipt by email" (the Store's promise) is true.
5. Test: `stripe trigger checkout.session.completed` won't carry our metadata; instead buy an item
   from the launcher with card `4242 4242 4242 4242`. The item should appear within seconds.
6. Going live: repeat 1–2 with live-mode keys and endpoint.

### 6. Seed the catalog

Once `pvp/schema/cosmetics.json` exists (the cosmetics workstream writes it):

```sh
pnpm --filter @void/backend seed:check                     # validate the file
SUPABASE_URL=https://<ref>.supabase.co SUPABASE_SERVICE_ROLE_KEY=<service key> \
  pnpm --filter @void/backend seed:remote                  # upsert, deactivate leavers, backfill free items
pnpm --filter @void/backend seed:sql                       # refresh supabase/seed.sql for local resets
```

Re-run after every change to `cosmetics.json`. Items removed from the file are deactivated (no
longer sold or auto-granted), never deleted — owners keep them. How the script reads the file
(accepted shapes, `availability`) is documented at the top of `scripts/seed-catalog.mjs`.

Founder badges and other grant-only items: `availability: "grant"` (or `exclusive: true`) in
`cosmetics.json`, then grant them in the SQL editor:

```sql
select public.grant_item('<player uuid>', 'void-badge-founder', 'admin:<you>', 'founder');
select public.revoke_item('<player uuid>', 'void-badge-founder', 'admin:<you>', 'mistake');
```

Both are audited in `public.audit_log`.

### 7. Build the launcher against the project

The anon key is public by design (RLS is the boundary); it is compiled in:

```powershell
$env:VOID_SUPABASE_URL = 'https://<ref>.supabase.co'
$env:VOID_SUPABASE_ANON_KEY = '<anon or publishable key>'
pnpm tauri build      # or cargo build — void-online reads them with option_env!
```

A build without them runs in offline mode: the Store, friends and cosmetics show local state and
say online features are unavailable.

---

## Local development

```sh
supabase start                     # needs Docker; prints local URL + keys
supabase db reset                  # migrations + supabase/seed.sql
supabase functions serve --env-file supabase/.env.local   # AUTH_CHALLENGE_SECRET=..., STRIPE_*=...
stripe listen --forward-to http://127.0.0.1:54321/functions/v1/stripe-webhook
```

Point a dev launcher at it by setting both variables in its runtime environment — when both are
set they override whatever was compiled in: `VOID_SUPABASE_URL=http://127.0.0.1:54321
VOID_SUPABASE_ANON_KEY=<local anon key>`.

## Tests

```sh
pnpm --filter @void/backend test        # 98 tests, no Docker, ~5 s
pnpm --filter @void/backend typecheck
```

- **SQL** (`tests/sql/`): every migration runs in [PGlite](https://pglite.dev) (real Postgres 17
  compiled to WebAssembly) on top of `tests/sql/supabase-shim.sql`, a stand-in for the parts of
  Supabase the migrations touch (`anon`/`authenticated`/`service_role`, `auth.users`,
  `auth.uid()`, `realtime.messages`, `realtime.topic()`, `realtime.send()`). Tests switch roles
  and JWT claims the way PostgREST does, so RLS, grants, triggers and the security-definer
  functions are exercised for real: ownership and slot rules, free grants, friend flows,
  presence, emote broadcast stamping, Realtime channel authorization, webhook idempotency,
  refunds, the lockdown (no client role can write a table or call a service function).
- **Functions** (`tests/functions/`): each handler runs under `node --test` (Node strips the
  TypeScript) against a fake network standing in for GoTrue, PostgREST, Mojang and Stripe.
- **Seed** (`tests/seed/`): the catalog reader against `tests/fixtures/cosmetics.fixture.json`,
  and its SQL loaded into PGlite.

**Not covered without Docker/Supabase:** the real GoTrue (that `admin/users` honours an explicit
`id`, that `generate_link` + `verify` mint a session), the real PostgREST error mapping of
`PT4xx`, the real Realtime server's policy evaluation and `realtime.send`, and the Deno runtime
itself. `supabase start` + a launcher sign-in exercises all of them; do that once before launch.

---

## Security notes

**Identity.** A player's auth user id is their Minecraft UUID, created only by `auth-minecraft`
after Mojang vouches for the profile. The launcher proves identity with the session-server
handshake (`join` → `hasJoined`), so the Minecraft access token never leaves the player's machine
except to Mojang. The contract's original `token` mode (the function reads
`/minecraft/profile` with the player's token) is still accepted but unused by the launcher. The
challenge is stateless (HMAC of `ts.nonce`), expires in 120 s, and is single-use. Sign-ups,
anonymous sign-ins and manual linking are off; a trigger discards password and email changes on
`auth.users`, so a player account can never gain a second way in that outlives ownership of the
Minecraft account. Supabase sessions live only in launcher memory.

**Authorization.** RLS is on for every table. Client roles have **no** insert/update/delete on
any table (revoked explicitly, on top of having no write policies). Every write is a
`security definer` function with `search_path = ''`: the four a player calls directly
(`my_cosmetics`, `presence_heartbeat`, `presence_offline`, `emote`) act on `auth.uid()` only; the
rest are revoked from `public`/`anon`/`authenticated` and granted to `service_role`, and the edge
functions call them with the caller's UUID after verifying the caller with GoTrue
(`GET /auth/v1/user` — signature, expiry, bans and revoked sessions in one call; this is why
`verify_jwt` is off). The equipped-set invariants (slot, ownership, ≤ 8 emotes, tintable) are a
trigger, so they hold for every writer including the dashboard. The SQL tests assert the lockdown
role by role.

**Realtime.** All channels are private. A player may join `user:<uuid>` only as that player, and
`server:<hash>` only while their own presence row says they are on that server. Nobody can
broadcast: emotes are sent by the database after the `emote` function checked ownership and
stamped the sender, so a forged emote is impossible. Realtime presence metas are client-claimed and
are used only as a prefetch hint; friend presence comes from the `presence` table, and the "VOID
player" badge from `cosmetics-lookup`.

**Payments.** The webhook verifies `Stripe-Signature` (HMAC-SHA256 over the raw body, 5-minute
tolerance, constant-time compare, multiple `v1`s for secret rolling) before reading anything. The
grant is one SQL function that inserts the Stripe event id and the entitlement in one
transaction — duplicates are no-ops, partial failures roll back and Stripe retries cleanly;
`entitlements.stripe_session_id` is unique as a second guard; metadata that contradicts the
recorded purchase is refused. Prices come from the database, never the client. Checkout sessions
use an idempotency key per player/item/minute, so a double-click cannot create two. Refunds and
disputes revoke and unequip. Everything is in `audit_log`.

**Rate limits** (Postgres fixed windows, `public.rate_limit_hit`):

| What | Limit |
|---|---|
| `auth-minecraft` | 20/min per IP, 6/min per Minecraft name; each challenge once |
| `cosmetics-lookup` | 120/min per player (the launcher batches 100 UUIDs per call) |
| `equip` | 60/min per player |
| `checkout` | 10/min per player |
| `friends` | 60/min per player; ≤ 100 pending outgoing requests, ≤ 500 friends |
| `presence_heartbeat` | one real write per 5 s unless the server changed |
| `emote` | 1/s per player |
| GoTrue | `token_verifications` 30 / 5 min per IP (each launcher redeems its own login) |

**Data held.** Minecraft UUID and name, what a player owns and wears, friends, last-seen and
current server (visible to friends only), purchase records (Stripe ids and amounts — no card data
ever touches us). Deleting the auth user cascades every row.

**Known gaps.** No per-IP limit on the PostgREST RPCs players call directly beyond the per-player
guards above (put Supabase's network restrictions or a WAF in front if abused). Server presence
is self-reported — a player can claim to be on any server, which only lets them see and be seen
on that server's channel. Name lookups for friend requests pick the most recently seen holder of
a name that has changed hands.
