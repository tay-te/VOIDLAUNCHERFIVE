// cosmetics-lookup, equip, friends, checkout: auth, shape checks, rate limits, error mapping.

import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { handler as lookup } from '../../supabase/functions/cosmetics-lookup/handler.ts';
import { handler as equip } from '../../supabase/functions/equip/handler.ts';
import { handler as friends } from '../../supabase/functions/friends/handler.ts';
import { handler as checkout } from '../../supabase/functions/checkout/handler.ts';
import { deps, FakeNet, KESTREL, MOSS, pgError, read, req, signedIn, SUPABASE_URL } from './fake.ts';

const emptySet = (uuid: string) => ({ uuid, cape: null, back: null, head: null, face: null, halo: null, trail: null, badge: null, emotes: [] });

describe('every player function', () => {
  for (const [name, h, method, body] of [
    ['cosmetics-lookup', lookup, 'POST', { uuids: [] }],
    ['equip', equip, 'POST', { slot: 'cape', id: null }],
    ['friends', friends, 'POST', { action: 'list' }],
    ['checkout', checkout, 'POST', { item: 'void-cape-rift' }],
  ] as const) {
    it(`${name}: no token, or a bad one, is 401 and reaches no database function`, async () => {
      const net = signedIn(new FakeNet());
      const none = await read(await h(deps(net))(req(method, name, body, { authorization: '' })));
      assert.equal(none.status, 401);
      assert.equal(none.body.error, 'signed_out');
      const bad = await read(await h(deps(net))(req(method, name, body, { authorization: 'Bearer forged' })));
      assert.equal(bad.status, 401);
      assert.equal(net.callsTo(`${SUPABASE_URL}/rest/v1/rpc/`).length, 0);
    });

    it(`${name}: over the limit is 429 with Retry-After`, async () => {
      const net = signedIn(new FakeNet());
      net.rpc('rate_limit_hit', () => false);
      const res = await read(await h(deps(net))(req(method, name, body)));
      assert.equal(res.status, 429);
      assert.ok(Number(res.headers.get('retry-after')) > 0);
    });
  }
});

describe('cosmetics-lookup', () => {
  it('normalises, dedupes and sorts the batch, and answers with cache headers', async () => {
    const net = signedIn(new FakeNet());
    net.rpc('lookup_equipped', (args) => {
      assert.deepEqual(args.uuids, [KESTREL, MOSS]);
      return [emptySet(KESTREL)];
    });
    const res = await read(await lookup(deps(net))(req('POST', 'cosmetics-lookup', { uuids: [MOSS, KESTREL.replace(/-/g, '').toUpperCase(), KESTREL, 'junk', 42] })));
    assert.equal(res.status, 200);
    assert.deepEqual(res.body, { sets: [emptySet(KESTREL)] });
    assert.equal(res.headers.get('cache-control'), 'private, max-age=30');
    assert.match(res.headers.get('etag') ?? '', /^W\/"[0-9a-f]{24}"$/);
  });

  it('GET with ?uuids works, and If-None-Match gets a 304', async () => {
    const net = signedIn(new FakeNet());
    net.rpc('lookup_equipped', () => [emptySet(MOSS)]);
    const first = await lookup(deps(net))(req('GET', `cosmetics-lookup?uuids=${MOSS}`));
    const etag = first.headers.get('etag')!;
    const again = await lookup(deps(net))(req('GET', `cosmetics-lookup?uuids=${MOSS}`, undefined, { 'if-none-match': etag }));
    assert.equal(again.status, 304);
  });

  it('refuses a batch over 100', async () => {
    const net = signedIn(new FakeNet());
    const uuids = Array.from({ length: 101 }, (_, i) => `00000000-0000-0000-0000-${String(i).padStart(12, '0')}`);
    const res = await read(await lookup(deps(net))(req('POST', 'cosmetics-lookup', { uuids })));
    assert.equal(res.body.error, 'too_many');
  });
});

describe('equip', () => {
  it('passes slot+id to equip_slot for the caller, never for a uuid in the body', async () => {
    const net = signedIn(new FakeNet());
    net.rpc('equip_slot', (args) => {
      assert.deepEqual(args, { p: KESTREL, slot: 'cape', item: 'void-cape-rift' });
      return { ...emptySet(KESTREL), cape: 'void-cape-rift' };
    });
    const res = await read(await equip(deps(net))(req('POST', 'equip', { slot: 'cape', id: 'void-cape-rift', p: MOSS, uuid: MOSS })));
    assert.equal(res.status, 200);
    assert.equal(res.body.equipped.cape, 'void-cape-rift');
  });

  it('maps the database refusing to the error shape', async () => {
    const net = signedIn(new FakeNet());
    net.rpc('equip_slot', () => pgError(403, 'not_owned', 'You don’t own void-cape-rift.'));
    const res = await read(await equip(deps(net))(req('POST', 'equip', { slot: 'cape', id: 'void-cape-rift' })));
    assert.equal(res.status, 403);
    assert.deepEqual(res.body, { error: 'not_owned', message: 'You don’t own void-cape-rift.' });
  });

  it('a database error that is not ours is a generic 502, not a leak', async () => {
    const net = signedIn(new FakeNet());
    net.rpc('equip_slot', () => new Response(JSON.stringify({ code: '23503', message: 'insert or update on table violates foreign key constraint' }), { status: 409 }));
    const res = await read(await equip(deps(net))(req('POST', 'equip', { slot: 'cape', id: 'x' })));
    assert.equal(res.status, 502);
    assert.ok(!JSON.stringify(res.body).includes('foreign key'));
  });

  it('emotes and tints', async () => {
    const net = signedIn(new FakeNet());
    net.rpc('set_emotes', (args) => ({ ...emptySet(KESTREL), emotes: args.ids }));
    const tints: unknown[] = [];
    net.rpc('set_tint', (args) => {
      tints.push(args);
      return emptySet(KESTREL);
    });
    assert.deepEqual((await read(await equip(deps(net))(req('POST', 'equip', { emotes: ['emote-wave'] })))).body.equipped.emotes, ['emote-wave']);
    await equip(deps(net))(req('POST', 'equip', { tints: { cape: '#9f8bff', halo: null } }));
    assert.deepEqual(tints, [{ p: KESTREL, slot: 'cape', color: '#9f8bff' }, { p: KESTREL, slot: 'halo', color: null }]);
  });

  it('rejects malformed requests before the database', async () => {
    const net = signedIn(new FakeNet());
    for (const [body, code] of [
      [{ slot: 'aura', id: 'x' }, 'bad_slot'],
      [{ slot: 'cape', id: 'Not An Id' }, 'bad_item'],
      [{ emotes: ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i'] }, 'bad_emotes'],
      [{ emotes: 'emote-wave' }, 'bad_emotes'],
      [{ tints: { cape: 'red' } }, 'bad_color'],
      [{ tints: {} }, 'bad_tints'],
      [{}, 'bad_request'],
    ] as const) {
      const res = await read(await equip(deps(net))(req('POST', 'equip', body)));
      assert.equal(res.body.error, code, JSON.stringify(body));
    }
    assert.equal(net.callsTo(`${SUPABASE_URL}/rest/v1/rpc/equip_slot`).length, 0);
  });
});

describe('friends', () => {
  const list = { friends: [], incoming: [], outgoing: [] };

  it('routes each action to its function with the caller as p', async () => {
    const net = signedIn(new FakeNet());
    const seen: Array<[string, unknown]> = [];
    for (const fn of ['friends_list', 'friend_request', 'friend_accept', 'friend_decline', 'friend_cancel', 'friend_remove']) {
      net.rpc(fn, (args) => {
        seen.push([fn, args]);
        return list;
      });
    }
    const h = friends(deps(net));
    await h(req('POST', 'friends', { action: 'list' }));
    await h(req('POST', 'friends', { action: 'request', name: ' Moss ' }));
    for (const action of ['accept', 'decline', 'cancel', 'remove']) {
      const res = await read(await h(req('POST', 'friends', { action, uuid: MOSS.replace(/-/g, '') })));
      assert.deepEqual(res.body, list);
    }
    assert.deepEqual(seen, [
      ['friends_list', { p: KESTREL }],
      ['friend_request', { p: KESTREL, target_name: 'Moss' }],
      ['friend_accept', { p: KESTREL, other: MOSS }],
      ['friend_decline', { p: KESTREL, other: MOSS }],
      ['friend_cancel', { p: KESTREL, other: MOSS }],
      ['friend_remove', { p: KESTREL, other: MOSS }],
    ]);
  });

  it('validates names, uuids and actions', async () => {
    const net = signedIn(new FakeNet());
    const h = friends(deps(net));
    assert.equal((await read(await h(req('POST', 'friends', { action: 'request', name: 'x!' })))).body.error, 'invalid_name');
    assert.equal((await read(await h(req('POST', 'friends', { action: 'accept', uuid: 'nope' })))).body.error, 'bad_uuid');
    assert.equal((await read(await h(req('POST', 'friends', { action: 'party' })))).body.error, 'bad_action');
  });

  it('passes the database’s sentence through', async () => {
    const net = signedIn(new FakeNet());
    net.rpc('friend_request', () => pgError(404, 'unknown_player', 'Nobody called Ghost has signed in to VOID yet.'));
    const res = await read(await friends(deps(net))(req('POST', 'friends', { action: 'request', name: 'Ghost' })));
    assert.equal(res.status, 404);
    assert.equal(res.body.message, 'Nobody called Ghost has signed in to VOID yet.');
  });
});

describe('checkout', () => {
  const env = {
    STRIPE_SECRET_KEY: 'sk_test_123',
    CHECKOUT_SUCCESS_URL: 'https://void.example/store/thanks?session={CHECKOUT_SESSION_ID}',
    CHECKOUT_CANCEL_URL: 'https://void.example/store/cancelled',
  };

  function shop(net: FakeNet): FakeNet {
    net.rpc('checkout_begin', () => ({ item: 'void-cape-rift', name: 'Rift cape', kind: 'cape', price_cents: 499, stripe_price_id: null }));
    net.rpc('checkout_record', () => null);
    net.json('POST', 'https://api.stripe.com/v1/checkout/sessions', { id: 'cs_test_1', url: 'https://checkout.stripe.com/c/pay/cs_test_1', amount_total: 499, currency: 'usd' });
    return net;
  }

  it('creates a session with the item, price and player in metadata, and records it', async () => {
    const net = shop(signedIn(new FakeNet()));
    const res = await read(await checkout(deps(net, env, 1_760_000_012_345))(req('POST', 'checkout', { item: 'void-cape-rift' })));
    assert.equal(res.status, 200);
    assert.deepEqual(res.body, { url: 'https://checkout.stripe.com/c/pay/cs_test_1', session_id: 'cs_test_1' });

    const stripe = net.callsTo('https://api.stripe.com')[0];
    assert.equal(stripe.headers.authorization, 'Bearer sk_test_123');
    assert.equal(stripe.headers['idempotency-key'], `checkout:${KESTREL}:void-cape-rift:29333333`);
    const form = new URLSearchParams(stripe.body);
    assert.equal(form.get('mode'), 'payment');
    assert.equal(form.get('metadata[uuid]'), KESTREL);
    assert.equal(form.get('metadata[item]'), 'void-cape-rift');
    assert.equal(form.get('payment_intent_data[metadata][uuid]'), KESTREL);
    assert.equal(form.get('client_reference_id'), KESTREL);
    assert.equal(form.get('line_items[0][price_data][unit_amount]'), '499');
    assert.equal(form.get('line_items[0][price_data][currency]'), 'usd');
    assert.equal(form.get('line_items[0][price_data][product_data][name]'), 'Rift cape');
    assert.equal(form.get('success_url'), env.CHECKOUT_SUCCESS_URL);
    assert.equal(form.get('expires_at'), String((29333333 + 31) * 60));

    const rec = JSON.parse(net.callsTo(`${SUPABASE_URL}/rest/v1/rpc/checkout_record`)[0].body);
    assert.deepEqual(rec, { p: KESTREL, item_id: 'void-cape-rift', session_id: 'cs_test_1', amount: 499, currency: 'usd' });
  });

  it('charges a configured Stripe Price when the catalog names one', async () => {
    const net = shop(signedIn(new FakeNet()));
    net.rpc('checkout_begin', () => ({ item: 'void-trail-spark', name: 'Spark', kind: 'trail', price_cents: 399, stripe_price_id: 'price_123' }));
    await checkout(deps(net, env))(req('POST', 'checkout', { item: 'void-trail-spark' }));
    const form = new URLSearchParams(net.callsTo('https://api.stripe.com')[0].body);
    assert.equal(form.get('line_items[0][price]'), 'price_123');
    assert.equal(form.get('line_items[0][price_data][unit_amount]'), null);
  });

  it('already owned never reaches Stripe', async () => {
    const net = shop(signedIn(new FakeNet()));
    net.rpc('checkout_begin', () => pgError(409, 'already_owned', 'You already own Rift cape.'));
    const res = await read(await checkout(deps(net, env))(req('POST', 'checkout', { item: 'void-cape-rift' })));
    assert.equal(res.status, 409);
    assert.equal(net.callsTo('https://api.stripe.com').length, 0);
  });

  it('a Stripe failure says nothing was charged; missing config is 503', async () => {
    const net = shop(signedIn(new FakeNet()));
    net.json('POST', 'https://api.stripe.com/', { error: { message: 'Invalid API Key' } }, 401);
    const res = await read(await checkout(deps(net, env))(req('POST', 'checkout', { item: 'void-cape-rift' })));
    assert.equal(res.status, 502);
    assert.match(res.body.message, /Nothing was charged/);
    assert.equal(net.callsTo(`${SUPABASE_URL}/rest/v1/rpc/checkout_record`).length, 0);

    const unconfigured = await read(await checkout(deps(shop(signedIn(new FakeNet()))))(req('POST', 'checkout', { item: 'void-cape-rift' })));
    assert.equal(unconfigured.status, 503);
  });
});
