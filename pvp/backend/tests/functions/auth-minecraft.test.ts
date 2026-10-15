import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { handler } from '../../supabase/functions/auth-minecraft/handler.ts';
import { makeChallenge } from '../../supabase/functions/_shared/mojang.ts';
import { deps, FakeNet, KESTREL, read, req, SERVICE, SUPABASE_URL } from './fake.ts';

const SECRET = 'a-challenge-secret-that-is-long-enough-0123456789';
const NOW = 1_760_000_000_000;
const NOW_S = NOW / 1000;
const UNDASHED = KESTREL.replace(/-/g, '');

/** GoTrue admin + PostgREST answers for a successful sign-in. */
function backend(net: FakeNet, opts: { existing?: boolean; returnedId?: string } = {}): FakeNet {
  net.rpc('rate_limit_hit', () => true);
  net.rpc('player_upsert', () => null);
  net.on('GET', `${SUPABASE_URL}/auth/v1/admin/users/`, () =>
    opts.existing
      ? new Response(JSON.stringify({ id: KESTREL, email: `${UNDASHED}@players.void.invalid` }), { status: 200 })
      : new Response(JSON.stringify({ msg: 'User not found' }), { status: 404 }),
  );
  net.on('POST', `${SUPABASE_URL}/auth/v1/admin/users`, (call) => {
    const body = JSON.parse(call.body);
    return new Response(JSON.stringify({ id: opts.returnedId ?? body.id, email: body.email }), { status: 200 });
  });
  net.json('POST', `${SUPABASE_URL}/auth/v1/admin/generate_link`, {
    id: KESTREL,
    action_link: 'https://x',
    email_otp: '123456',
    hashed_token: 'hashed-token-abc',
    verification_type: 'magiclink',
  });
  return net;
}

describe('auth-minecraft: challenge', () => {
  it('hands out a 40-hex server id bound to ts and nonce', async () => {
    const net = backend(new FakeNet());
    const res = await read(await handler(deps(net, { AUTH_CHALLENGE_SECRET: SECRET }, NOW))(req('POST', 'auth-minecraft', { mode: 'challenge' })));
    assert.equal(res.status, 200);
    assert.match(res.body.server_id, /^[0-9a-f]{40}$/);
    assert.equal(res.body.ts, NOW_S);
    assert.match(res.body.nonce, /^[0-9a-f]{32}$/);
    assert.equal(res.body.server_id, (await makeChallenge(SECRET, NOW_S, res.body.nonce)).server_id);
  });

  it('refuses to run without a real secret', async () => {
    const net = backend(new FakeNet());
    const res = await read(await handler(deps(net, { AUTH_CHALLENGE_SECRET: 'short' }, NOW))(req('POST', 'auth-minecraft', { mode: 'challenge' })));
    assert.equal(res.status, 500);
    assert.equal(res.body.error, 'misconfigured');
  });
});

describe('auth-minecraft: join', () => {
  it('asks Mojang hasJoined, creates the user with id = MC UUID, and returns a token hash', async () => {
    const ch = await makeChallenge(SECRET, NOW_S);
    const net = backend(new FakeNet());
    net.on('GET', 'https://sessionserver.mojang.com/session/minecraft/hasJoined', (call) => {
      const u = new URL(call.url);
      assert.equal(u.searchParams.get('username'), 'kestrel', 'sent as typed; Mojang matches case-insensitively');
      assert.equal(u.searchParams.get('serverId'), ch.server_id);
      return new Response(JSON.stringify({ id: UNDASHED, name: 'Kestrel', properties: [] }), { status: 200 });
    });
    const res = await read(await handler(deps(net, { AUTH_CHALLENGE_SECRET: SECRET }, NOW + 5_000))(
      req('POST', 'auth-minecraft', { mode: 'join', name: 'kestrel', ts: ch.ts, nonce: ch.nonce }),
    ));
    assert.equal(res.status, 200, JSON.stringify(res.body));
    assert.deepEqual(res.body, { player: { uuid: KESTREL, name: 'Kestrel' }, token_hash: 'hashed-token-abc', verification_type: 'magiclink' });

    const created = JSON.parse(net.callsTo(`${SUPABASE_URL}/auth/v1/admin/users`).find((c) => c.method === 'POST')!.body);
    assert.equal(created.id, KESTREL);
    assert.equal(created.email, `${UNDASHED}@players.void.invalid`);
    assert.equal(created.email_confirm, true);
    const upsert = JSON.parse(net.callsTo(`${SUPABASE_URL}/rest/v1/rpc/player_upsert`)[0].body);
    assert.deepEqual(upsert, { p: KESTREL, p_name: 'Kestrel' });
    // Service calls carry the service key; no Mojang token ever reached us.
    assert.equal(net.callsTo(`${SUPABASE_URL}/auth/v1/admin/generate_link`)[0].headers.authorization, `Bearer ${SERVICE}`);
    assert.ok(net.calls.every((c) => !c.url.includes('minecraftservices')));
  });

  it('a name Mojang has not seen join is refused', async () => {
    const ch = await makeChallenge(SECRET, NOW_S);
    const net = backend(new FakeNet());
    net.on('GET', 'https://sessionserver.mojang.com/', () => new Response(null, { status: 204 }));
    const res = await read(await handler(deps(net, { AUTH_CHALLENGE_SECRET: SECRET }, NOW))(
      req('POST', 'auth-minecraft', { mode: 'join', name: 'Kestrel', ts: ch.ts, nonce: ch.nonce }),
    ));
    assert.equal(res.status, 401);
    assert.equal(res.body.error, 'not_joined');
  });

  it('an expired, forged or reused challenge is refused before Mojang is asked', async () => {
    const ch = await makeChallenge(SECRET, NOW_S - 600);
    const net = backend(new FakeNet());
    const late = await read(await handler(deps(net, { AUTH_CHALLENGE_SECRET: SECRET }, NOW))(
      req('POST', 'auth-minecraft', { mode: 'join', name: 'Kestrel', ts: ch.ts, nonce: ch.nonce }),
    ));
    assert.equal(late.body.error, 'challenge_expired');

    const forged = await read(await handler(deps(net, { AUTH_CHALLENGE_SECRET: SECRET }, NOW))(
      req('POST', 'auth-minecraft', { mode: 'join', name: 'Kestrel', ts: NOW_S, nonce: 'zz' }),
    ));
    assert.equal(forged.body.error, 'bad_challenge');

    const fresh = await makeChallenge(SECRET, NOW_S);
    net.rpc('rate_limit_hit', (args) => !String(args.p_key).startsWith('auth:nonce:'));
    const reused = await read(await handler(deps(net, { AUTH_CHALLENGE_SECRET: SECRET }, NOW))(
      req('POST', 'auth-minecraft', { mode: 'join', name: 'Kestrel', ts: fresh.ts, nonce: fresh.nonce }),
    ));
    assert.equal(reused.body.error, 'challenge_used');
    assert.equal(net.callsTo('https://sessionserver.mojang.com').length, 0);
  });
});

describe('auth-minecraft: token', () => {
  it('reads the profile the access token belongs to', async () => {
    const net = backend(new FakeNet(), { existing: true });
    net.on('GET', 'https://api.minecraftservices.com/minecraft/profile', (call) => {
      assert.equal(call.headers.authorization, 'Bearer mc-access-token-0123456789');
      return new Response(JSON.stringify({ id: UNDASHED, name: 'Kestrel' }), { status: 200 });
    });
    const res = await read(await handler(deps(net, {}, NOW))(req('POST', 'auth-minecraft', { mode: 'token', access_token: 'mc-access-token-0123456789' })));
    assert.equal(res.status, 200);
    assert.equal(res.body.player.uuid, KESTREL);
    assert.equal(net.callsTo(`${SUPABASE_URL}/auth/v1/admin/users`).filter((c) => c.method === 'POST').length, 0, 'existing user reused');
  });

  it('maps Mojang failures to sentences', async () => {
    for (const [status, code] of [[401, 'bad_minecraft_token'], [404, 'no_minecraft'], [500, 'mojang_unavailable']] as const) {
      const net = backend(new FakeNet());
      net.on('GET', 'https://api.minecraftservices.com/', () => new Response('{}', { status }));
      const res = await read(await handler(deps(net, {}, NOW))(req('POST', 'auth-minecraft', { access_token: 'mc-access-token-0123456789' })));
      assert.equal(res.body.error, code);
      assert.equal(typeof res.body.message, 'string');
    }
  });
});

describe('auth-minecraft: guards', () => {
  it('stops when GoTrue ignores the requested id', async () => {
    const net = backend(new FakeNet(), { returnedId: '11111111-1111-1111-1111-111111111111' });
    net.json('GET', 'https://api.minecraftservices.com/', { id: UNDASHED, name: 'Kestrel' });
    const res = await read(await handler(deps(net, {}, NOW))(req('POST', 'auth-minecraft', { mode: 'token', access_token: 'mc-access-token-0123456789' })));
    assert.equal(res.status, 500);
    assert.equal(res.body.error, 'misconfigured');
    assert.equal(net.callsTo(`${SUPABASE_URL}/auth/v1/admin/generate_link`).length, 0);
  });

  it('rate-limits per IP with Retry-After', async () => {
    const net = backend(new FakeNet());
    net.rpc('rate_limit_hit', (args) => !String(args.p_key).startsWith('auth:ip:'));
    const res = await read(await handler(deps(net, { AUTH_CHALLENGE_SECRET: SECRET }, NOW))(
      req('POST', 'auth-minecraft', { mode: 'challenge' }, { 'x-forwarded-for': '203.0.113.9, 10.0.0.1' }),
    ));
    assert.equal(res.status, 429);
    assert.equal(res.headers.get('retry-after'), '60');
    assert.equal(JSON.parse(net.callsTo(`${SUPABASE_URL}/rest/v1/rpc/rate_limit_hit`)[0].body).p_key, 'auth:ip:203.0.113.9');
  });

  it('rejects non-POST and junk bodies', async () => {
    const net = backend(new FakeNet());
    assert.equal((await handler(deps(net))(req('GET', 'auth-minecraft'))).status, 405);
    assert.equal((await read(await handler(deps(net))(req('POST', 'auth-minecraft', '{nope')))).body.error, 'bad_json');
    assert.equal((await read(await handler(deps(net))(req('POST', 'auth-minecraft', { mode: 'x' })))).body.error, 'bad_mode');
  });
});
