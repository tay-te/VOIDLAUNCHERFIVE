import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { handler } from '../../supabase/functions/stripe-webhook/handler.ts';
import { signStripePayload, verifyStripeSignature, formEncode } from '../../supabase/functions/_shared/stripe.ts';
import { deps, FakeNet, KESTREL, pgError, read, SUPABASE_URL } from './fake.ts';

const SECRET = 'whsec_test_secret';
const NOW = 1_760_000_000_000;
const NOW_S = NOW / 1000;

async function deliver(net: FakeNet, event: unknown, opts: { secret?: string; ts?: number; tamper?: boolean } = {}) {
  const raw = JSON.stringify(event);
  const sig = await signStripePayload(raw, opts.secret ?? SECRET, opts.ts ?? NOW_S);
  const body = opts.tamper ? raw.replace('"paid"', '"unpaid"') : raw;
  const req = new Request(`${SUPABASE_URL}/functions/v1/stripe-webhook`, { method: 'POST', headers: { 'stripe-signature': sig }, body });
  return read(await handler(deps(net, { STRIPE_WEBHOOK_SECRET: SECRET }, NOW))(req));
}

const completed = (overrides: Record<string, unknown> = {}) => ({
  id: 'evt_1',
  type: 'checkout.session.completed',
  data: {
    object: {
      id: 'cs_1',
      object: 'checkout.session',
      payment_status: 'paid',
      amount_total: 499,
      currency: 'usd',
      client_reference_id: KESTREL,
      payment_intent: 'pi_1',
      metadata: { uuid: KESTREL, item: 'void-cape-rift' },
      ...overrides,
    },
  },
});

describe('signature', () => {
  it('accepts Stripe’s scheme, any of several v1s, and nothing else', async () => {
    const raw = '{"id":"evt"}';
    const good = await signStripePayload(raw, SECRET, NOW_S);
    assert.equal(await verifyStripeSignature(raw, good, SECRET, NOW_S), true);
    const v1 = good.split('v1=')[1];
    assert.equal(await verifyStripeSignature(raw, `t=${NOW_S},v1=${'0'.repeat(64)},v1=${v1}`, SECRET, NOW_S), true, 'secret rolling');
    assert.equal(await verifyStripeSignature(raw + ' ', good, SECRET, NOW_S), false, 'body changed');
    assert.equal(await verifyStripeSignature(raw, good, 'whsec_other', NOW_S), false, 'other secret');
    assert.equal(await verifyStripeSignature(raw, good, SECRET, NOW_S + 301), false, 'replayed later');
    assert.equal(await verifyStripeSignature(raw, `v1=${v1}`, SECRET, NOW_S), false, 'no timestamp');
    assert.equal(await verifyStripeSignature(raw, null, SECRET, NOW_S), false);
    assert.equal(await verifyStripeSignature(raw, good, '', NOW_S), false, 'unconfigured secret never verifies');
  });

  it('a bad signature is a 400 that touches nothing', async () => {
    const net = new FakeNet();
    for (const opts of [{ secret: 'whsec_forged' }, { tamper: true }, { ts: NOW_S - 3600 }]) {
      const res = await deliver(net, completed(), opts);
      assert.equal(res.status, 400);
      assert.equal(res.body.error, 'bad_signature');
    }
    assert.equal(net.calls.length, 0);
  });
});

describe('events', () => {
  it('checkout.session.completed (paid) grants through stripe_checkout_completed', async () => {
    const net = new FakeNet();
    net.rpc('stripe_checkout_completed', (args) => {
      assert.deepEqual(args, {
        event_id: 'evt_1', event_type: 'checkout.session.completed', session_id: 'cs_1', p: KESTREL,
        item_id: 'void-cape-rift', payment_intent: 'pi_1', amount: 499, currency: 'usd', paid: true,
      });
      return 'granted';
    });
    const res = await deliver(net, completed());
    assert.deepEqual(res.body, { received: true, result: 'granted' });
  });

  it('an unpaid completion and an expanded payment_intent are handled', async () => {
    const net = new FakeNet();
    let seen: Record<string, unknown> = {};
    net.rpc('stripe_checkout_completed', (args) => {
      seen = args;
      return 'pending';
    });
    await deliver(net, completed({ payment_status: 'unpaid', payment_intent: { id: 'pi_x', object: 'payment_intent' } }));
    assert.equal(seen.paid, false);
    assert.equal(seen.payment_intent, 'pi_x');
  });

  it('expired, refunded (full only) and disputed', async () => {
    const net = new FakeNet();
    const calls: string[] = [];
    for (const fn of ['stripe_checkout_expired', 'stripe_refunded', 'stripe_event_seen']) {
      net.rpc(fn, (args) => {
        calls.push(`${fn}:${args.payment_intent ?? args.session_id ?? ''}`);
        return 'ok';
      });
    }
    await deliver(net, { id: 'evt_2', type: 'checkout.session.expired', data: { object: { id: 'cs_2' } } });
    await deliver(net, { id: 'evt_3', type: 'charge.refunded', data: { object: { id: 'ch_1', refunded: true, payment_intent: 'pi_1' } } });
    await deliver(net, { id: 'evt_4', type: 'charge.refunded', data: { object: { id: 'ch_2', refunded: false, payment_intent: 'pi_2' } } });
    await deliver(net, { id: 'evt_5', type: 'charge.dispute.created', data: { object: { id: 'dp_1', payment_intent: 'pi_3' } } });
    assert.deepEqual(calls, ['stripe_checkout_expired:cs_2', 'stripe_refunded:pi_1', 'stripe_event_seen:', 'stripe_refunded:pi_3']);
  });

  it('other event types are acknowledged without touching the database', async () => {
    const net = new FakeNet();
    const res = await deliver(net, { id: 'evt_6', type: 'customer.created', data: { object: {} } });
    assert.deepEqual(res.body, { received: true, result: 'ignored' });
    assert.equal(net.calls.length, 0);
  });

  it('a permanent rejection is acknowledged (no 3-day retry loop); a transient failure is not', async () => {
    const net = new FakeNet();
    net.rpc('stripe_checkout_completed', () => pgError(400, 'bad_metadata', 'Checkout Session has no uuid/item metadata.'));
    const rejected = await deliver(net, completed({ metadata: {} }));
    assert.equal(rejected.status, 200);
    assert.equal(rejected.body.result, 'rejected');

    const down = new FakeNet();
    down.rpc('stripe_checkout_completed', () => new Response('upstream connect error', { status: 503 }));
    const failed = await deliver(down, completed());
    assert.ok(failed.status >= 500, 'Stripe should retry');
  });
});

describe('form encoding', () => {
  it('nests objects and arrays the way Stripe expects', () => {
    assert.equal(
      decodeURIComponent(formEncode({ a: 1, b: { c: 'x y', d: [{ e: true }] }, skip: null })),
      'a=1&b[c]=x y&b[d][0][e]=true',
    );
  });
});
