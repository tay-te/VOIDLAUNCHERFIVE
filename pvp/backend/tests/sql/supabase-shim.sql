-- The slice of a Supabase database the migrations depend on, for running them in PGlite
-- (Postgres compiled to WebAssembly) with no Docker and no Supabase CLI.
--
-- It mirrors the real objects' names and behaviour closely enough for the migrations and the
-- RLS policies to run unchanged: the three API roles, `auth.users` and `auth.uid()` reading the
-- JWT claims PostgREST sets per request, and `realtime.messages` / `realtime.topic()` /
-- `realtime.send()`. It is NOT a Supabase emulator — GoTrue, PostgREST and Realtime themselves
-- are faked at the HTTP level in the function tests, and nothing here proves the real services
-- behave as these stand-ins do. `supabase start` + `supabase test db` is the full-fidelity run.

create role anon nologin noinherit;
create role authenticated nologin noinherit;
create role service_role nologin noinherit bypassrls;

grant usage on schema public to anon, authenticated, service_role;
alter default privileges in schema public grant all on tables to anon, authenticated, service_role;
alter default privileges in schema public grant all on sequences to anon, authenticated, service_role;
alter default privileges in schema public grant execute on functions to anon, authenticated, service_role;

create schema auth;
grant usage on schema auth to anon, authenticated, service_role;

create table auth.users (
  id                  uuid primary key,
  email               varchar(255),
  encrypted_password  varchar(255),
  email_change        varchar(255) default '',
  raw_app_meta_data   jsonb default '{}'::jsonb,
  created_at          timestamptz default now(),
  updated_at          timestamptz default now()
);

create function auth.uid() returns uuid language sql stable as $$
  select nullif(coalesce(
    current_setting('request.jwt.claim.sub', true),
    (nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub')), '')::uuid
$$;

create function auth.role() returns text language sql stable as $$
  select coalesce(
    nullif(current_setting('request.jwt.claim.role', true), ''),
    (nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'role'))
$$;

grant execute on function auth.uid(), auth.role() to anon, authenticated, service_role;

create schema realtime;
grant usage on schema realtime to anon, authenticated, service_role;

create table realtime.messages (
  id           bigint generated always as identity primary key,
  topic        text not null,
  extension    text not null,
  event        text,
  payload      jsonb,
  private      boolean default true,
  inserted_at  timestamptz not null default now()
);
alter table realtime.messages enable row level security;
grant select, insert on realtime.messages to authenticated;

-- In Supabase this is set by Realtime while it evaluates policies for a channel join.
create function realtime.topic() returns text language sql stable as $$
  select nullif(current_setting('realtime.topic', true), '')
$$;
grant execute on function realtime.topic() to anon, authenticated, service_role;

-- In Supabase this inserts into realtime.messages and Realtime fans it out. Same here, minus
-- the fan-out: the tests read the table.
create function realtime.send(payload jsonb, event text, topic text, private boolean default true)
returns void language plpgsql security definer as $$
begin
  insert into realtime.messages (topic, extension, event, payload, private)
  values (topic, 'broadcast', event, payload, private);
end;
$$;
