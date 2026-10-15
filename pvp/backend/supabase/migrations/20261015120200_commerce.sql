-- VOID PvP online backend — commerce: purchases, Stripe webhook idempotency, audit log,
-- grants and revocations.
--
-- The money path: `checkout` (edge function) → `checkout_begin` validates the item and that the
-- player does not own it → Stripe Checkout Session created with metadata {uuid, item} →
-- `checkout_record` stores it as a pending purchase → the player pays in the browser → Stripe
-- calls `stripe-webhook` → signature verified → `stripe_checkout_completed` grants the item.
--
-- Idempotency is in the database, not in the function: the Stripe event id is inserted into
-- `stripe_events` in the same transaction as the grant, so a retried or duplicated event (Stripe
-- delivers at least once) is a no-op, and a failure halfway rolls the whole thing back and
-- Stripe's retry gets a clean second attempt. `entitlements.stripe_session_id` is unique on top.

create table public.purchases (
  id                     uuid primary key default gen_random_uuid(),
  player                 uuid not null references public.players (uuid) on delete cascade,
  item                   text not null references public.catalog (id) on update cascade,
  stripe_session_id      text not null unique,
  stripe_payment_intent  text,
  status                 text not null default 'pending'
                           check (status in ('pending', 'paid', 'expired', 'refunded', 'failed')),
  amount_cents           integer check (amount_cents >= 0),
  currency               text,
  created_at             timestamptz not null default now(),
  updated_at             timestamptz not null default now()
);

create index purchases_player_idx on public.purchases (player, created_at desc);
create index purchases_payment_intent_idx on public.purchases (stripe_payment_intent)
  where stripe_payment_intent is not null;

create table public.stripe_events (
  id           text primary key,
  type         text not null,
  received_at  timestamptz not null default now()
);

create table public.audit_log (
  id      bigint generated always as identity primary key,
  at      timestamptz not null default now(),
  actor   text not null,               -- 'stripe', 'admin:<who>', 'system'
  player  uuid,
  action  text not null,               -- 'grant', 'revoke', 'purchase', 'refund', ...
  detail  jsonb not null default '{}'::jsonb
);

create index audit_log_player_idx on public.audit_log (player, at desc);

-- Tell the player their entitlements changed, so the launcher refreshes without waiting for
-- its poll. Fires for every new or reinstated purchase or grant; not for free items, which
-- arrive in bulk (signup, backfill) and are owned by everyone anyway.
create function public.entitlements_notify()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if new.source <> 'free' and (tg_op = 'INSERT' or (old.revoked_at is not null and new.revoked_at is null)) then
    perform public.notify_user(new.player, 'entitlements',
      jsonb_build_object('item', new.item, 'source', new.source));
  end if;
  return new;
end;
$$;

create trigger entitlements_notify
  after insert or update of revoked_at on public.entitlements
  for each row execute function public.entitlements_notify();

-- ---------------------------------------------------------------------------------------------
-- checkout

-- Validate a purchase before a Checkout Session exists. Returns what Stripe needs.
create function public.checkout_begin(p uuid, item_id text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  c public.catalog;
begin
  select * into c from public.catalog where id = item_id;
  if c.id is null then
    perform public.raise_api(404, 'unknown_item', 'That item doesn''t exist.');
  end if;
  if public.owns(p, item_id) then
    perform public.raise_api(409, 'already_owned', format('You already own %s.', c.name));
  end if;
  if c.availability <> 'paid' or not c.active or c.price_cents <= 0 then
    perform public.raise_api(400, 'not_for_sale', format('%s isn''t for sale.', c.name));
  end if;
  return jsonb_build_object('item', c.id, 'name', c.name, 'kind', c.kind,
                            'price_cents', c.price_cents, 'stripe_price_id', c.stripe_price_id);
end;
$$;

create function public.checkout_record(p uuid, item_id text, session_id text,
                                       amount integer, currency text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.purchases (player, item, stripe_session_id, amount_cents, currency)
  values (p, item_id, session_id, amount, lower(currency))
  on conflict (stripe_session_id) do nothing;
end;
$$;

-- ---------------------------------------------------------------------------------------------
-- webhook fulfilment

-- `checkout.session.completed` / `checkout.session.async_payment_succeeded`. Returns
-- 'granted', 'pending' (completed but not yet paid — async methods), or 'duplicate'.
create function public.stripe_checkout_completed(
  event_id text, event_type text, session_id text, p uuid, item_id text,
  payment_intent text, amount integer, currency text, paid boolean)
returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
  rec public.purchases;
begin
  insert into public.stripe_events (id, type) values (event_id, event_type)
  on conflict (id) do nothing;
  if not found then
    return 'duplicate';
  end if;

  if p is null or item_id is null then
    perform public.raise_api(400, 'bad_metadata', 'Checkout Session has no uuid/item metadata.');
  end if;
  if not exists (select 1 from public.players where uuid = p) then
    perform public.raise_api(400, 'unknown_player', format('No player %s.', p));
  end if;
  if not exists (select 1 from public.catalog where id = item_id) then
    perform public.raise_api(400, 'unknown_item', format('No item %s.', item_id));
  end if;

  -- The pending row `checkout` wrote, if any, must agree with the metadata.
  select * into rec from public.purchases where stripe_session_id = session_id;
  if rec.id is not null and (rec.player <> p or rec.item <> item_id) then
    perform public.raise_api(400, 'metadata_mismatch',
      format('Session %s was recorded for %s/%s.', session_id, rec.player, rec.item));
  end if;

  insert into public.purchases as pu
    (player, item, stripe_session_id, stripe_payment_intent, status, amount_cents, currency)
  values (p, item_id, session_id, payment_intent,
          case when paid then 'paid' else 'pending' end, amount, lower(currency))
  on conflict (stripe_session_id) do update set
    stripe_payment_intent = coalesce(excluded.stripe_payment_intent, pu.stripe_payment_intent),
    status = case when paid then 'paid' else pu.status end,
    amount_cents = coalesce(excluded.amount_cents, pu.amount_cents),
    currency = coalesce(excluded.currency, pu.currency),
    updated_at = now();

  if not paid then
    return 'pending';
  end if;

  insert into public.entitlements as e
    (player, item, source, stripe_session_id, stripe_payment_intent)
  values (p, item_id, 'stripe', session_id, payment_intent)
  on conflict (player, item) do update set
    source = 'stripe',
    stripe_session_id = excluded.stripe_session_id,
    stripe_payment_intent = excluded.stripe_payment_intent,
    granted_at = now(),
    revoked_at = null
  -- A player who already owns it live (paid twice in two tabs) keeps the first grant; the
  -- second purchase is recorded as paid and is for support to refund.
  where e.revoked_at is not null;

  insert into public.audit_log (actor, player, action, detail)
  values ('stripe', p, 'purchase', jsonb_build_object(
    'item', item_id, 'session', session_id, 'payment_intent', payment_intent,
    'amount_cents', amount, 'currency', lower(currency), 'event', event_id));
  return 'granted';
end;
$$;

create function public.stripe_checkout_expired(event_id text, event_type text, session_id text)
returns text
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.stripe_events (id, type) values (event_id, event_type)
  on conflict (id) do nothing;
  if not found then
    return 'duplicate';
  end if;
  update public.purchases set status = 'expired', updated_at = now()
   where stripe_session_id = session_id and status = 'pending';
  return 'expired';
end;
$$;

-- `charge.refunded` (full refunds) and `charge.dispute.created`: take the item back.
create function public.stripe_refunded(event_id text, event_type text, payment_intent text)
returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
  r record;
begin
  insert into public.stripe_events (id, type) values (event_id, event_type)
  on conflict (id) do nothing;
  if not found then
    return 'duplicate';
  end if;
  if payment_intent is null then
    return 'ignored';
  end if;
  update public.purchases set status = 'refunded', updated_at = now()
   where stripe_payment_intent = payment_intent;
  for r in
    update public.entitlements set revoked_at = now()
     where stripe_payment_intent = payment_intent and revoked_at is null
    returning player, item
  loop
    insert into public.audit_log (actor, player, action, detail)
    values ('stripe', r.player, 'refund', jsonb_build_object(
      'item', r.item, 'payment_intent', payment_intent, 'event', event_id, 'type', event_type));
  end loop;
  return 'revoked';
end;
$$;

-- Any other event type: record it so a retry is a no-op, and do nothing else.
create function public.stripe_event_seen(event_id text, event_type text)
returns text
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.stripe_events (id, type) values (event_id, event_type)
  on conflict (id) do nothing;
  return case when found then 'ignored' else 'duplicate' end;
end;
$$;

-- ---------------------------------------------------------------------------------------------
-- admin grants (founder badges, event rewards, support). Run from the SQL editor or with the
-- service key: select public.grant_item('<uuid>', 'void-badge-founder', 'admin:tayte', 'founder');

create function public.grant_item(p uuid, item_id text, actor text, reason text default null)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  if not exists (select 1 from public.catalog where id = item_id) then
    perform public.raise_api(404, 'unknown_item', format('No item %s.', item_id));
  end if;
  insert into public.entitlements as e (player, item, source)
  values (p, item_id, 'grant')
  on conflict (player, item) do update set revoked_at = null, granted_at = now()
  where e.revoked_at is not null;
  insert into public.audit_log (actor, player, action, detail)
  values (actor, p, 'grant', jsonb_build_object('item', item_id, 'reason', reason));
end;
$$;

create function public.revoke_item(p uuid, item_id text, actor text, reason text default null)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  update public.entitlements set revoked_at = now()
   where player = p and item = item_id and revoked_at is null;
  insert into public.audit_log (actor, player, action, detail)
  values (actor, p, 'revoke', jsonb_build_object('item', item_id, 'reason', reason));
end;
$$;

-- ---------------------------------------------------------------------------------------------
-- RLS and grants

alter table public.purchases     enable row level security;
alter table public.stripe_events enable row level security;
alter table public.audit_log     enable row level security;

create policy "players see their own purchases" on public.purchases
  for select to authenticated using (player = auth.uid());

revoke insert, update, delete, truncate on public.purchases from anon, authenticated;
revoke all on public.stripe_events, public.audit_log from anon, authenticated;

revoke execute on function
  public.entitlements_notify(), public.checkout_begin(uuid, text),
  public.checkout_record(uuid, text, text, integer, text),
  public.stripe_checkout_completed(text, text, text, uuid, text, text, integer, text, boolean),
  public.stripe_checkout_expired(text, text, text), public.stripe_refunded(text, text, text),
  public.stripe_event_seen(text, text), public.grant_item(uuid, text, text, text),
  public.revoke_item(uuid, text, text, text)
  from public, anon, authenticated;

grant execute on function
  public.checkout_begin(uuid, text), public.checkout_record(uuid, text, text, integer, text),
  public.stripe_checkout_completed(text, text, text, uuid, text, text, integer, text, boolean),
  public.stripe_checkout_expired(text, text, text), public.stripe_refunded(text, text, text),
  public.stripe_event_seen(text, text), public.grant_item(uuid, text, text, text),
  public.revoke_item(uuid, text, text, text)
  to service_role;
