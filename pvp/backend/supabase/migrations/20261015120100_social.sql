-- VOID PvP online backend — social: friends, presence, emotes, and Realtime authorization.
--
-- Realtime topology (pvp/docs/online-contract.md, "Realtime topology"):
--
--   server:<32 hex>  one per Minecraft server (sha256 of the normalised address). Presence =
--                    which VOID players are on it; broadcast `emote` is sent ONLY by this
--                    database (the `emote` RPC, after checking ownership), so the sender's
--                    UUID is stamped by us and cannot be spoofed.
--   user:<uuid>      one per player, private to them. The database broadcasts `entitlements`
--                    (a purchase landed) and `friends` (request / accepted / removed / presence).
--
-- All channels are private: `realtime.messages` RLS below decides who may join, listen, send.
-- Friend presence (online, which server, last seen) is the `presence` table — written only by
-- the player through `presence_heartbeat` — not Realtime presence, whose metas are
-- client-claimed and which cannot say "last seen 4 h ago".

-- ---------------------------------------------------------------------------------------------
-- server addresses

-- One spelling per server: trimmed, lowercased, trailing dot dropped, default port dropped.
-- `void_online::normalize_server` in Rust is the same function and is tested against the same
-- cases. Null for an empty address; an address with characters no server has raises.
create function public.normalize_server(addr text)
returns text
language plpgsql
immutable
as $$
declare
  a text := lower(btrim(coalesce(addr, '')));
begin
  if a = '' then
    return null;
  end if;
  a := regexp_replace(a, '\.+(:[0-9]+)?$', '\1');
  a := regexp_replace(a, ':25565$', '');
  if length(a) > 261
     or a !~ '^([a-z0-9_-]+(\.[a-z0-9_-]+)*|\[[0-9a-f:.]+\])(:[0-9]{1,5})?$' then
    raise exception using errcode = 'PT400', message = 'bad_server',
      detail = 'That doesn''t look like a server address.';
  end if;
  return a;
end;
$$;

create function public.server_topic(normalized text)
returns text
language sql
immutable
as $$
  select case when normalized is null then null
              else 'server:' || left(encode(sha256(convert_to(normalized, 'UTF8')), 'hex'), 32) end;
$$;

-- ---------------------------------------------------------------------------------------------
-- presence

create table public.presence (
  player         uuid primary key references public.players (uuid) on delete cascade,
  server         text,
  server_topic   text,
  -- non-null while the launcher is open; null after presence_offline
  online_at      timestamptz,
  -- last heartbeat (or going offline): "last seen"
  updated_at     timestamptz not null default now(),
  last_emote_at  timestamptz
);

create index presence_server_topic_idx on public.presence (server_topic) where server_topic is not null;

-- How long a heartbeat keeps a player online. The launcher heartbeats every 30 s.
create function public.presence_ttl()
returns interval
language sql
immutable
as $$ select interval '90 seconds' $$;

create function public.is_online(pr public.presence)
returns boolean
language sql
stable
as $$ select pr.online_at is not null and pr.updated_at > now() - public.presence_ttl() $$;

-- Are `a` and `b` friends?
create table public.friendships (
  a           uuid not null references public.players (uuid) on delete cascade,
  b           uuid not null references public.players (uuid) on delete cascade,
  created_at  timestamptz not null default now(),
  primary key (a, b),
  constraint friendships_ordered check (a < b)
);

create index friendships_b_idx on public.friendships (b);

create function public.are_friends(x uuid, y uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (select 1 from public.friendships f
                  where f.a = least(x, y) and f.b = greatest(x, y));
$$;

create table public.friend_requests (
  from_player  uuid not null references public.players (uuid) on delete cascade,
  to_player    uuid not null references public.players (uuid) on delete cascade,
  created_at   timestamptz not null default now(),
  primary key (from_player, to_player),
  constraint friend_requests_not_self check (from_player <> to_player)
);

create index friend_requests_to_idx on public.friend_requests (to_player);

-- Tell `target` something happened, on their private channel. A no-op where Realtime is absent.
create function public.notify_user(target uuid, event text, payload jsonb)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform realtime.send(payload, event, 'user:' || target::text, true);
exception when others then
  -- Realtime is best effort; a failed notification must never fail the write that caused it.
  raise warning 'notify_user(%, %) failed: %', target, event, sqlerrm;
end;
$$;

create function public.notify_friends_presence(p uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  f uuid;
begin
  for f in
    select case when fr.a = p then fr.b else fr.a end
      from public.friendships fr where fr.a = p or fr.b = p
  loop
    perform public.notify_user(f, 'friends', jsonb_build_object('kind', 'presence', 'uuid', p));
  end loop;
end;
$$;

-- The launcher calls this every 30 s with the server it is on (null on the title screen or
-- with the game closed). Returns the server's Realtime topic to join. Rate-limited to one real
-- write per 5 s unless the server changed; friends are told only when something they can see
-- changed (came online, changed server).
create function public.presence_heartbeat(server text default null)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
#variable_conflict use_column
declare
  me      uuid := auth.uid();
  norm    text := public.normalize_server(presence_heartbeat.server);
  topic   text := public.server_topic(norm);
  prev    public.presence;
  changed boolean;
begin
  if me is null or not exists (select 1 from public.players where uuid = me) then
    perform public.raise_api(401, 'signed_out', 'Sign in to VOID first.');
  end if;

  select * into prev from public.presence where player = me;
  changed := prev.player is null
          or not public.is_online(prev)
          or prev.server is distinct from norm;

  if not changed and prev.updated_at > now() - interval '5 seconds' then
    return jsonb_build_object('server', norm, 'topic', topic);
  end if;

  insert into public.presence as pr (player, server, server_topic, online_at, updated_at)
  values (me, norm, topic, now(), now())
  on conflict (player) do update set
    server = excluded.server,
    server_topic = excluded.server_topic,
    online_at = coalesce(case when public.is_online(pr) then pr.online_at end, now()),
    updated_at = now();

  update public.players set last_seen_at = now() where uuid = me;

  if changed then
    perform public.notify_friends_presence(me);
  end if;
  return jsonb_build_object('server', norm, 'topic', topic);
end;
$$;

create function public.presence_offline()
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  me uuid := auth.uid();
begin
  if me is null then
    return;
  end if;
  update public.presence
     set online_at = null, server = null, server_topic = null, updated_at = now()
   where player = me;
  if found then
    perform public.notify_friends_presence(me);
  end if;
end;
$$;

-- ---------------------------------------------------------------------------------------------
-- emotes

-- Play emote `id` on the server the caller is on: checks it is an emote, the caller owns it and
-- is on a server, at most one per second, then broadcasts `{uuid,id,at}` on the server topic.
create function public.emote(id text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  me  uuid := auth.uid();
  pr  public.presence;
  at_ms  bigint := (extract(epoch from clock_timestamp()) * 1000)::bigint;
begin
  if me is null then
    perform public.raise_api(401, 'signed_out', 'Sign in to VOID first.');
  end if;
  if not exists (select 1 from public.catalog c where c.id = emote.id and c.kind = 'emote') then
    perform public.raise_api(404, 'unknown_item', 'That emote doesn''t exist.');
  end if;
  if not public.owns(me, emote.id) then
    perform public.raise_api(403, 'not_owned', 'You don''t own that emote.');
  end if;
  select * into pr from public.presence where player = me for update;
  if pr.player is null or not public.is_online(pr) or pr.server_topic is null then
    perform public.raise_api(409, 'no_server', 'Join a server to emote to other players.');
  end if;
  if pr.last_emote_at is not null and pr.last_emote_at > clock_timestamp() - interval '1 second' then
    perform public.raise_api(429, 'too_fast', 'One emote a second.');
  end if;
  update public.presence set last_emote_at = clock_timestamp() where player = me;
  perform realtime.send(
    jsonb_build_object('uuid', me, 'id', emote.id, 'at', at_ms),
    'emote', pr.server_topic, true);
  return jsonb_build_object('at', at_ms);
end;
$$;

-- ---------------------------------------------------------------------------------------------
-- friends — service_role only; the `friends` edge function verifies the caller and passes `p`

create function public.friends_list(p uuid)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select jsonb_build_object(
    'friends', coalesce((
      select jsonb_agg(jsonb_build_object(
               'uuid', pl.uuid,
               'name', pl.name,
               'presence',
                 jsonb_build_object('state', case when public.is_online(pr) then 'online' else 'offline' end)
                 || case when public.is_online(pr) and pr.server is not null
                         then jsonb_build_object('server', pr.server) else '{}'::jsonb end
                 || case when coalesce(pr.updated_at, pl.last_seen_at) is not null
                         then jsonb_build_object('last_seen',
                                (extract(epoch from coalesce(pr.updated_at, pl.last_seen_at)) * 1000)::bigint)
                         else '{}'::jsonb end)
             order by public.is_online(pr) desc nulls last, lower(pl.name))
        from public.friendships f
        join public.players pl on pl.uuid = case when f.a = p then f.b else f.a end
        left join public.presence pr on pr.player = pl.uuid
       where f.a = p or f.b = p), '[]'::jsonb),
    'incoming', coalesce((
      select jsonb_agg(jsonb_build_object('uuid', pl.uuid, 'name', pl.name,
               'at', (extract(epoch from r.created_at) * 1000)::bigint) order by r.created_at desc)
        from public.friend_requests r join public.players pl on pl.uuid = r.from_player
       where r.to_player = p), '[]'::jsonb),
    'outgoing', coalesce((
      select jsonb_agg(jsonb_build_object('uuid', pl.uuid, 'name', pl.name,
               'at', (extract(epoch from r.created_at) * 1000)::bigint) order by r.created_at desc)
        from public.friend_requests r join public.players pl on pl.uuid = r.to_player
       where r.from_player = p), '[]'::jsonb));
$$;

-- Make `x` and `y` friends and clear any requests between them.
create function public.befriend(x uuid, y uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  delete from public.friend_requests
   where (from_player = x and to_player = y) or (from_player = y and to_player = x);
  insert into public.friendships (a, b) values (least(x, y), greatest(x, y))
  on conflict do nothing;
end;
$$;

create function public.friend_request(p uuid, target_name text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  t   uuid;
  tn  text;
begin
  if target_name is null or btrim(target_name) !~ '^[A-Za-z0-9_]{3,16}$' then
    perform public.raise_api(400, 'invalid_name', 'Usernames are 3–16 letters, numbers or underscores.');
  end if;
  select pl.uuid, pl.name into t, tn
    from public.players pl
   where lower(pl.name) = lower(btrim(target_name))
   order by pl.last_seen_at desc
   limit 1;
  if t is null then
    perform public.raise_api(404, 'unknown_player',
      format('Nobody called %s has signed in to VOID yet.', btrim(target_name)));
  end if;
  if t = p then
    perform public.raise_api(400, 'self', 'That''s you.');
  end if;
  if public.are_friends(p, t) then
    perform public.raise_api(409, 'already_friends', format('You''re already friends with %s.', tn));
  end if;
  if exists (select 1 from public.friend_requests where from_player = p and to_player = t) then
    perform public.raise_api(409, 'already_sent', format('You already sent %s a request.', tn));
  end if;

  -- They asked first: sending one back is accepting theirs.
  if exists (select 1 from public.friend_requests where from_player = t and to_player = p) then
    perform public.befriend(p, t);
    perform public.notify_user(t, 'friends', jsonb_build_object('kind', 'accepted', 'uuid', p));
    return public.friends_list(p);
  end if;

  if (select count(*) from public.friend_requests where from_player = p) >= 100 then
    perform public.raise_api(429, 'limit', 'You have 100 requests waiting. Cancel some first.');
  end if;
  if (select count(*) from public.friendships where a = p or b = p) >= 500 then
    perform public.raise_api(429, 'limit', 'You''ve reached 500 friends.');
  end if;

  insert into public.friend_requests (from_player, to_player) values (p, t);
  perform public.notify_user(t, 'friends', jsonb_build_object('kind', 'request', 'uuid', p));
  return public.friends_list(p);
end;
$$;

create function public.friend_accept(p uuid, other uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
begin
  if not exists (select 1 from public.friend_requests where from_player = other and to_player = p) then
    perform public.raise_api(404, 'no_request', 'That request is gone.');
  end if;
  if (select count(*) from public.friendships where a = p or b = p) >= 500 then
    perform public.raise_api(429, 'limit', 'You''ve reached 500 friends.');
  end if;
  perform public.befriend(p, other);
  perform public.notify_user(other, 'friends', jsonb_build_object('kind', 'accepted', 'uuid', p));
  return public.friends_list(p);
end;
$$;

create function public.friend_decline(p uuid, other uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
begin
  -- Silent to the sender on purpose: a declined request looks like an unanswered one.
  delete from public.friend_requests where from_player = other and to_player = p;
  return public.friends_list(p);
end;
$$;

create function public.friend_cancel(p uuid, other uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
begin
  delete from public.friend_requests where from_player = p and to_player = other;
  if found then
    perform public.notify_user(other, 'friends', jsonb_build_object('kind', 'removed', 'uuid', p));
  end if;
  return public.friends_list(p);
end;
$$;

create function public.friend_remove(p uuid, other uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
begin
  delete from public.friendships where a = least(p, other) and b = greatest(p, other);
  if found then
    perform public.notify_user(other, 'friends', jsonb_build_object('kind', 'removed', 'uuid', p));
  end if;
  return public.friends_list(p);
end;
$$;

-- ---------------------------------------------------------------------------------------------
-- RLS

alter table public.presence        enable row level security;
alter table public.friendships     enable row level security;
alter table public.friend_requests enable row level security;

create policy "players see their own and their friends' presence" on public.presence
  for select to authenticated
  using (player = auth.uid() or public.are_friends(player, auth.uid()));

create policy "players see their own friendships" on public.friendships
  for select to authenticated using (a = auth.uid() or b = auth.uid());

create policy "players see requests they sent or received" on public.friend_requests
  for select to authenticated using (from_player = auth.uid() or to_player = auth.uid());

revoke insert, update, delete, truncate on public.presence, public.friendships,
  public.friend_requests from anon, authenticated;

-- Realtime authorization. `realtime.topic()` is the channel being joined; policies are checked
-- on join, so a player must heartbeat a server before joining its topic.
create policy "void: listen on the server you are on" on realtime.messages
  for select to authenticated
  using (
    realtime.messages.extension in ('broadcast', 'presence')
    and realtime.topic() like 'server:%'
    and exists (select 1 from public.presence pr
                 where pr.player = auth.uid() and pr.server_topic = realtime.topic())
  );

create policy "void: track presence on the server you are on" on realtime.messages
  for insert to authenticated
  with check (
    realtime.messages.extension = 'presence'
    and realtime.topic() like 'server:%'
    and exists (select 1 from public.presence pr
                 where pr.player = auth.uid() and pr.server_topic = realtime.topic())
  );

create policy "void: listen on your own user topic" on realtime.messages
  for select to authenticated
  using (
    realtime.messages.extension = 'broadcast'
    and realtime.topic() = 'user:' || auth.uid()::text
  );

-- No insert policy for broadcast anywhere: emotes and notifications come from the database.

-- ---------------------------------------------------------------------------------------------
-- grants

revoke execute on function
  public.normalize_server(text), public.server_topic(text), public.presence_ttl(),
  public.is_online(public.presence), public.are_friends(uuid, uuid),
  public.notify_user(uuid, text, jsonb), public.notify_friends_presence(uuid),
  public.presence_heartbeat(text), public.presence_offline(), public.emote(text),
  public.friends_list(uuid), public.befriend(uuid, uuid), public.friend_request(uuid, text),
  public.friend_accept(uuid, uuid), public.friend_decline(uuid, uuid),
  public.friend_cancel(uuid, uuid), public.friend_remove(uuid, uuid)
  from public, anon, authenticated;

-- Used inside RLS policies evaluated as `authenticated`.
grant execute on function public.are_friends(uuid, uuid), public.is_online(public.presence),
  public.presence_ttl(), public.normalize_server(text), public.server_topic(text)
  to authenticated, service_role;

grant execute on function public.presence_heartbeat(text), public.presence_offline(),
  public.emote(text) to authenticated;

grant execute on function
  public.friends_list(uuid), public.friend_request(uuid, text), public.friend_accept(uuid, uuid),
  public.friend_decline(uuid, uuid), public.friend_cancel(uuid, uuid), public.friend_remove(uuid, uuid),
  public.notify_user(uuid, text, jsonb)
  to service_role;
