-- VOID PvP online backend — core: players, catalog, entitlements, equipped sets, rate limits.
--
-- The contract is pvp/docs/online-contract.md. Three rules shape everything below:
--
-- 1. A player IS their Minecraft UUID. `auth-minecraft` creates the Supabase auth user with
--    `id = <dashed MC UUID>`, so `auth.uid()` is the player and there is no mapping table.
-- 2. No client role writes any table. Reads are governed by RLS; every write goes through a
--    `security definer` function that either checks `auth.uid()` itself or is granted to
--    `service_role` only (the edge functions, which verify the caller first).
-- 3. The database enforces the cosmetics invariants itself — an item in the wrong slot, an
--    item the player does not own, nine emotes, a tint on an untintable item — with a trigger,
--    so no code path (function, dashboard edit, future endpoint) can store an invalid set.
--
-- Errors raised for callers use SQLSTATE `PT<http status>`, which PostgREST turns into that
-- HTTP status, with MESSAGE = a machine code (`not_owned`) and DETAIL = a sentence a player can
-- read. The edge functions pass both through as `{error, message}`.

-- ---------------------------------------------------------------------------------------------
-- players

create table public.players (
  uuid          uuid primary key references auth.users (id) on delete cascade,
  name          text not null check (name ~ '^[A-Za-z0-9_]{1,16}$'),
  created_at    timestamptz not null default now(),
  last_seen_at  timestamptz not null default now()
);

comment on table public.players is
  'One row per Minecraft account that has signed in to VOID. uuid = auth.users.id = the dashed MC UUID. '
  'name is the Minecraft name as of the last sign-in; names are not unique over time (a name can be '
  'released and taken), so lookups by name pick the most recently seen holder.';

create index players_name_lower_idx on public.players (lower(name), last_seen_at desc);

-- ---------------------------------------------------------------------------------------------
-- catalog — seeded from pvp/schema/cosmetics.json by scripts/seed-catalog.mjs

create table public.catalog (
  id               text primary key
                     check (id ~ '^[a-z0-9]+(-[a-z0-9]+)*$' and length(id) <= 64),
  kind             text not null
                     check (kind in ('cape', 'back', 'head', 'face', 'halo', 'trail', 'badge', 'emote')),
  name             text not null check (length(name) between 1 and 80),
  rarity           text,
  price_cents      integer not null default 0 check (price_cents >= 0),
  -- free: every account owns it. paid: sold through Stripe Checkout. grant: only ever granted
  -- (founder badges, event rewards) — never sold, never auto-granted.
  availability     text not null default 'paid' check (availability in ('free', 'paid', 'grant')),
  tintable         boolean not null default false,
  preview          jsonb,
  -- false: no longer sold or auto-granted. Owners keep what they have.
  active           boolean not null default true,
  -- optional: a Stripe Price to charge instead of ad-hoc price_data.
  stripe_price_id  text,
  updated_at       timestamptz not null default now(),
  constraint catalog_paid_has_price check (availability <> 'paid' or price_cents > 0)
);

comment on table public.catalog is
  'Cosmetic catalog metadata. Single source is pvp/schema/cosmetics.json; assets are bundled in the mod.';

-- ---------------------------------------------------------------------------------------------
-- entitlements — who owns what

create table public.entitlements (
  player                 uuid not null references public.players (uuid) on delete cascade,
  item                   text not null references public.catalog (id) on update cascade,
  source                 text not null check (source in ('stripe', 'grant', 'free')),
  stripe_session_id      text unique,
  stripe_payment_intent  text,
  granted_at             timestamptz not null default now(),
  revoked_at             timestamptz,
  primary key (player, item),
  constraint entitlements_stripe_ids check (source <> 'stripe' or stripe_session_id is not null)
);

create index entitlements_payment_intent_idx on public.entitlements (stripe_payment_intent)
  where stripe_payment_intent is not null;

comment on column public.entitlements.revoked_at is
  'Set on refund or admin revoke. A revoked row is kept for the audit trail and is not ownership.';

-- Does `p` own item `i`? Live entitlement, or an active free item (so a free item added to the
-- catalog is owned by everyone at once, before any backfill runs).
create function public.owns(p uuid, i text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
           select 1 from public.entitlements e
            where e.player = p and e.item = i and e.revoked_at is null)
      or exists (
           select 1 from public.catalog c
            where c.id = i and c.availability = 'free' and c.active);
$$;

-- New players get every active free item, recorded, so their entitlement list is complete.
create function public.grant_free_items()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.entitlements (player, item, source)
  select new.uuid, c.id, 'free'
    from public.catalog c
   where c.availability = 'free' and c.active
  on conflict (player, item) do nothing;
  return new;
end;
$$;

create trigger players_grant_free_items
  after insert on public.players
  for each row execute function public.grant_free_items();

-- Backfill after the catalog gains a free item (scripts/seed-catalog.mjs calls it).
create function public.backfill_free_grants()
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  n integer;
begin
  insert into public.entitlements (player, item, source)
  select p.uuid, c.id, 'free'
    from public.players p
   cross join public.catalog c
   where c.availability = 'free' and c.active
  on conflict (player, item) do nothing;
  get diagnostics n = row_count;
  return n;
end;
$$;

-- ---------------------------------------------------------------------------------------------
-- equipped — one row per player; a missing row means "wearing nothing"

create table public.equipped (
  player      uuid primary key references public.players (uuid) on delete cascade,
  cape        text references public.catalog (id) on update cascade,
  back        text references public.catalog (id) on update cascade,
  head        text references public.catalog (id) on update cascade,
  face        text references public.catalog (id) on update cascade,
  halo        text references public.catalog (id) on update cascade,
  trail       text references public.catalog (id) on update cascade,
  badge       text references public.catalog (id) on update cascade,
  emotes      text[] not null default '{}',
  tints       jsonb not null default '{}'::jsonb,
  updated_at  timestamptz not null default now(),
  constraint equipped_emotes_max check (cardinality(emotes) <= 8),
  constraint equipped_tints_object check (jsonb_typeof(tints) = 'object')
);

-- The seven slots, in contract order. Kept as a function so SQL and tests share one list.
create function public.cosmetic_slots()
returns text[]
language sql
immutable
as $$ select array['cape', 'back', 'head', 'face', 'halo', 'trail', 'badge'] $$;

create function public.raise_api(status integer, code text, detail text)
returns void
language plpgsql
as $$
begin
  raise exception using errcode = 'PT' || status::text, message = code, detail = detail;
end;
$$;

-- The invariant guard. Runs as the table owner on every insert/update, whoever wrote it.
create function public.equipped_validate()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  slot      text;
  item      text;
  item_kind text;
  tint_key  text;
  tint_val  jsonb;
  clean     jsonb := '{}'::jsonb;
begin
  foreach slot in array public.cosmetic_slots() loop
    item := case slot
      when 'cape' then new.cape  when 'back' then new.back  when 'head' then new.head
      when 'face' then new.face  when 'halo' then new.halo  when 'trail' then new.trail
      when 'badge' then new.badge end;
    if item is not null then
      select c.kind into item_kind from public.catalog c where c.id = item;
      if item_kind is distinct from slot then
        perform public.raise_api(400, 'wrong_slot',
          format('%s is not a %s item.', item, slot));
      end if;
      if not public.owns(new.player, item) then
        perform public.raise_api(403, 'not_owned', format('You don''t own %s.', item));
      end if;
    end if;
  end loop;

  if (select count(*) from unnest(new.emotes)) <> (select count(distinct e) from unnest(new.emotes) e) then
    perform public.raise_api(400, 'duplicate_emote', 'An emote can only be on the wheel once.');
  end if;
  foreach item in array new.emotes loop
    select c.kind into item_kind from public.catalog c where c.id = item;
    if item_kind is distinct from 'emote' then
      perform public.raise_api(400, 'wrong_slot', format('%s is not an emote.', item));
    end if;
    if not public.owns(new.player, item) then
      perform public.raise_api(403, 'not_owned', format('You don''t own %s.', item));
    end if;
  end loop;

  -- Tints: keep only well-formed ones for a slot whose current item is tintable. Taking an
  -- item off drops its tint silently; `set_tint` refuses a bad one loudly before it gets here.
  for tint_key, tint_val in select * from jsonb_each(new.tints) loop
    if tint_key = any (public.cosmetic_slots())
       and jsonb_typeof(tint_val) = 'string'
       and (tint_val #>> '{}') ~ '^#[0-9a-fA-F]{6}$' then
      item := case tint_key
        when 'cape' then new.cape  when 'back' then new.back  when 'head' then new.head
        when 'face' then new.face  when 'halo' then new.halo  when 'trail' then new.trail
        when 'badge' then new.badge end;
      if item is not null and exists (select 1 from public.catalog c where c.id = item and c.tintable) then
        clean := clean || jsonb_build_object(tint_key, lower(tint_val #>> '{}'));
      end if;
    end if;
  end loop;
  new.tints := clean;
  new.updated_at := now();
  return new;
end;
$$;

create trigger equipped_validate
  before insert or update on public.equipped
  for each row execute function public.equipped_validate();

-- A refund or revoke takes the item off.
create function public.entitlements_unequip_revoked()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if new.revoked_at is not null and old.revoked_at is null and not public.owns(new.player, new.item) then
    update public.equipped e set
      cape  = nullif(e.cape,  new.item), back  = nullif(e.back,  new.item),
      head  = nullif(e.head,  new.item), face  = nullif(e.face,  new.item),
      halo  = nullif(e.halo,  new.item), trail = nullif(e.trail, new.item),
      badge = nullif(e.badge, new.item),
      emotes = array_remove(e.emotes, new.item)
     where e.player = new.player;
  end if;
  return new;
end;
$$;

create trigger entitlements_unequip_revoked
  after update of revoked_at on public.entitlements
  for each row execute function public.entitlements_unequip_revoked();

-- The contract's EquippedSet for one player: every slot present (null when empty), emotes,
-- and tints only when there are some. A player with no row wears nothing.
create function public.equipped_json(p uuid)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select jsonb_build_object(
           'uuid', x.player,
           'cape', e.cape, 'back', e.back, 'head', e.head, 'face', e.face,
           'halo', e.halo, 'trail', e.trail, 'badge', e.badge,
           'emotes', coalesce(to_jsonb(e.emotes), '[]'::jsonb))
         || case when e.tints is null or e.tints = '{}'::jsonb then '{}'::jsonb
                 else jsonb_build_object('tints', e.tints) end
    from (select p as player) x
    left join public.equipped e on e.player = x.player;
$$;

-- ---------------------------------------------------------------------------------------------
-- rate limits — fixed windows, for the edge functions and the RPCs players call directly

create table public.rate_limits (
  key           text not null,
  window_start  timestamptz not null,
  hits          integer not null default 0,
  primary key (key, window_start)
);

-- True while `p_key` has made at most `p_max` calls in the current `p_window_s` window.
create function public.rate_limit_hit(p_key text, p_max integer, p_window_s integer)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  w timestamptz := to_timestamp(floor(extract(epoch from now()) / p_window_s) * p_window_s);
  n integer;
begin
  insert into public.rate_limits as r (key, window_start, hits)
  values (p_key, w, 1)
  on conflict on constraint rate_limits_pkey do update set hits = r.hits + 1
  returning r.hits into n;
  -- Opportunistic sweep, about one call in a hundred: windows older than a day are dead.
  if random() < 0.01 then
    delete from public.rate_limits r2 where r2.window_start < now() - interval '1 day';
  end if;
  return n <= p_max;
end;
$$;

-- ---------------------------------------------------------------------------------------------
-- RPCs

-- Signed-in player's owned items and equipped set. Called directly via PostgREST.
create function public.my_cosmetics()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  me uuid := auth.uid();
begin
  if me is null or not exists (select 1 from public.players where uuid = me) then
    perform public.raise_api(401, 'signed_out', 'Sign in to VOID to see your cosmetics.');
  end if;
  return jsonb_build_object(
    'owned', coalesce((
      select jsonb_agg(i order by i) from (
        select e.item as i from public.entitlements e
         where e.player = me and e.revoked_at is null
        union
        select c.id from public.catalog c where c.availability = 'free' and c.active
      ) owned), '[]'::jsonb),
    'equipped', public.equipped_json(me));
end;
$$;

-- Batch EquippedSets for the players among `uuids` who are VOID players. Read-only, public data.
create function public.lookup_equipped(uuids uuid[])
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  if cardinality(uuids) > 200 then
    perform public.raise_api(400, 'too_many', 'Ask for at most 200 players at a time.');
  end if;
  return coalesce((
    select jsonb_agg(public.equipped_json(p.uuid) order by p.uuid)
      from public.players p
     where p.uuid = any (uuids)), '[]'::jsonb);
end;
$$;

-- Equip `item` in `slot` (null takes it off) for `p`. service_role only: the `equip` edge
-- function verifies the caller and rate-limits first.
create function public.equip_slot(p uuid, slot text, item text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
begin
  if not (slot = any (public.cosmetic_slots())) then
    perform public.raise_api(400, 'bad_slot', format('%s is not a slot.', coalesce(slot, 'null')));
  end if;
  if item is not null and not exists (select 1 from public.catalog where id = item) then
    perform public.raise_api(404, 'unknown_item', 'That item doesn''t exist.');
  end if;
  insert into public.equipped (player) values (p) on conflict (player) do nothing;
  execute format('update public.equipped set %I = $1 where player = $2', slot) using item, p;
  return public.equipped_json(p);
end;
$$;

create function public.set_emotes(p uuid, ids text[])
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
begin
  if cardinality(ids) > 8 then
    perform public.raise_api(400, 'too_many', 'The wheel holds eight emotes.');
  end if;
  if exists (select 1 from unnest(ids) i where not exists (select 1 from public.catalog c where c.id = i)) then
    perform public.raise_api(404, 'unknown_item', 'One of those emotes doesn''t exist.');
  end if;
  insert into public.equipped (player) values (p) on conflict (player) do nothing;
  update public.equipped set emotes = coalesce(ids, '{}') where player = p;
  return public.equipped_json(p);
end;
$$;

create function public.set_tint(p uuid, slot text, color text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  item text;
begin
  if not (slot = any (public.cosmetic_slots())) then
    perform public.raise_api(400, 'bad_slot', format('%s is not a slot.', coalesce(slot, 'null')));
  end if;
  if color is not null and color !~ '^#[0-9a-fA-F]{6}$' then
    perform public.raise_api(400, 'bad_color', 'A tint is a colour like #9f8bff.');
  end if;
  insert into public.equipped (player) values (p) on conflict (player) do nothing;
  execute format('select %I from public.equipped where player = $1', slot) into item using p;
  if color is not null and (item is null or not exists (
        select 1 from public.catalog c where c.id = item and c.tintable)) then
    perform public.raise_api(400, 'not_tintable', 'That item can''t be recoloured.');
  end if;
  update public.equipped e
     set tints = case when color is null then e.tints - slot
                      else e.tints || jsonb_build_object(slot, lower(color)) end
   where e.player = p;
  return public.equipped_json(p);
end;
$$;

-- Record a sign-in: create the player (which grants the free items) or refresh their name.
-- service_role only — `auth-minecraft` calls it after Mojang vouched for the profile.
create function public.player_upsert(p uuid, p_name text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.players as pl (uuid, name) values (p, p_name)
  on conflict (uuid) do update set name = excluded.name, last_seen_at = now();
end;
$$;

-- ---------------------------------------------------------------------------------------------
-- RLS

alter table public.players      enable row level security;
alter table public.catalog      enable row level security;
alter table public.entitlements enable row level security;
alter table public.equipped     enable row level security;
alter table public.rate_limits  enable row level security;

create policy "players are visible to signed-in players" on public.players
  for select to authenticated using (true);

create policy "the catalog is public" on public.catalog
  for select to anon, authenticated using (true);

create policy "players see their own entitlements" on public.entitlements
  for select to authenticated using (player = auth.uid());

create policy "equipped sets are visible to signed-in players" on public.equipped
  for select to authenticated using (true);

-- rate_limits: no policy at all — nobody but security definer functions touches it.

-- Belt and braces under RLS: client roles cannot write, whatever a future policy says.
revoke insert, update, delete, truncate on public.players, public.catalog, public.entitlements,
  public.equipped, public.rate_limits from anon, authenticated;
revoke all on public.rate_limits from anon, authenticated;

-- Functions are executable by PUBLIC by default (and Supabase grants anon/authenticated on
-- top). Close everything, then open exactly what a client may call.
revoke execute on function
  public.owns(uuid, text), public.grant_free_items(), public.backfill_free_grants(),
  public.equipped_validate(), public.entitlements_unequip_revoked(), public.equipped_json(uuid),
  public.rate_limit_hit(text, integer, integer), public.my_cosmetics(),
  public.lookup_equipped(uuid[]), public.equip_slot(uuid, text, text),
  public.set_emotes(uuid, text[]), public.set_tint(uuid, text, text),
  public.raise_api(integer, text, text), public.cosmetic_slots(), public.player_upsert(uuid, text)
  from public, anon, authenticated;

grant execute on function public.my_cosmetics() to authenticated;
grant execute on function public.lookup_equipped(uuid[]) to authenticated, service_role;
grant execute on function
  public.owns(uuid, text), public.backfill_free_grants(), public.equipped_json(uuid),
  public.rate_limit_hit(text, integer, integer), public.equip_slot(uuid, text, text),
  public.set_emotes(uuid, text[]), public.set_tint(uuid, text, text), public.cosmetic_slots(),
  public.raise_api(integer, text, text), public.player_upsert(uuid, text)
  to service_role;
