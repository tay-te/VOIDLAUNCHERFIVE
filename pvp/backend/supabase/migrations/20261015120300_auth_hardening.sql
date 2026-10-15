-- VOID PvP online backend — auth hardening.
--
-- Every auth user is created by `auth-minecraft` with `id = <MC UUID>` and a synthetic,
-- undeliverable email (`<undashed uuid>@players.void.invalid`). The only way in is proving the
-- Minecraft account to that function. Two GoTrue self-service paths would open a second way in
-- that no longer needs the Minecraft account — which matters once an account changes hands:
--
--   * `updateUser({ password })` then email+password sign-in, forever;
--   * `updateUser({ email })` to an address the player controls, then magic links to it.
--
-- Signups are off in config.toml, but those two act on an existing user. So the database
-- refuses them: a password or email change on an auth user is silently discarded. The admin
-- API (service role) is subject to the same rule, which is fine — we never set either.

create function public.auth_users_freeze_credentials()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  new.encrypted_password := old.encrypted_password;
  new.email := old.email;
  new.email_change := old.email_change;
  return new;
end;
$$;

create trigger void_freeze_credentials
  before update of encrypted_password, email, email_change on auth.users
  for each row execute function public.auth_users_freeze_credentials();

revoke execute on function public.auth_users_freeze_credentials() from public, anon, authenticated;
