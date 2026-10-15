// Checkout validation, webhook fulfilment and its idempotency, refunds, admin grants, and the
// auth-hardening trigger.

import { before, describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { asAdmin, asService, asUser, createPlayer, freshDb, one, q, rejects, seedCatalog, U } from './db.mjs';

let db;

before(async () => {
  db = await freshDb();
  await seedCatalog(db);
  await createPlayer(db, U.kestrel, 'Kestrel');
  await createPlayer(db, U.moss, 'Moss');
});

const complete = (eventId, sessionId, player, item, paid = true, pi = 'pi_1') =>
  db.query(
    `select public.stripe_checkout_completed($1, 'checkout.session.completed', $2, $3, $4, $5, 499, 'USD', $6) as r`,
    [eventId, sessionId, player, item, pi, paid],
  );

describe('checkout_begin', () => {
  it('describes a paid item for Stripe', async () => {
    await asService(db);
    const info = await one(db, `select public.checkout_begin($1, 'void-cape-rift')`, [U.kestrel]);
    assert.deepEqual(info, { item: 'void-cape-rift', name: 'Rift cape', kind: 'cape', price_cents: 499, stripe_price_id: null });
  });

  it('refuses free, grant-only, unknown, inactive and already-owned items', async () => {
    await asService(db);
    await rejects(db.query(`select public.checkout_begin($1, 'void-cape-origin')`, [U.kestrel]), 'already_owned', 409);
    await rejects(db.query(`select public.checkout_begin($1, 'void-badge-founder')`, [U.kestrel]), 'not_for_sale', 400);
    await rejects(db.query(`select public.checkout_begin($1, 'nope')`, [U.kestrel]), 'unknown_item', 404);
    await asAdmin(db);
    await db.exec(`update public.catalog set active = false where id = 'emote-bow'`);
    await asService(db);
    await rejects(db.query(`select public.checkout_begin($1, 'emote-bow')`, [U.kestrel]), 'not_for_sale', 400);
  });
});

describe('webhook fulfilment', () => {
  it('a paid session grants the item once, records the purchase, audits and notifies', async () => {
    await asService(db);
    await db.query(`select public.checkout_record($1, 'void-cape-rift', 'cs_1', 499, 'USD')`, [U.kestrel]);
    assert.equal((await complete('evt_1', 'cs_1', U.kestrel, 'void-cape-rift')).rows[0].r, 'granted');
    assert.equal((await complete('evt_1', 'cs_1', U.kestrel, 'void-cape-rift')).rows[0].r, 'duplicate', 'same event twice');

    await asAdmin(db);
    const ent = await q(db, `select source, stripe_session_id, stripe_payment_intent, revoked_at from public.entitlements where player = $1 and item = 'void-cape-rift'`, [U.kestrel]);
    assert.deepEqual(ent, [{ source: 'stripe', stripe_session_id: 'cs_1', stripe_payment_intent: 'pi_1', revoked_at: null }]);
    assert.equal(await one(db, `select status from public.purchases where stripe_session_id = 'cs_1'`), 'paid');
    assert.equal(await one(db, `select currency from public.purchases where stripe_session_id = 'cs_1'`), 'usd');
    assert.equal(await one(db, `select count(*)::int from public.audit_log where action = 'purchase'`), 1);
    const note = await q(db, `select event, payload from realtime.messages where topic = $1`, [`user:${U.kestrel}`]);
    assert.deepEqual(note, [{ event: 'entitlements', payload: { item: 'void-cape-rift', source: 'stripe' } }]);
  });

  it('a different event for the same session does not grant twice', async () => {
    await asService(db);
    assert.equal((await complete('evt_1b', 'cs_1', U.kestrel, 'void-cape-rift')).rows[0].r, 'granted');
    await asAdmin(db);
    assert.equal(await one(db, `select count(*)::int from public.entitlements where item = 'void-cape-rift'`), 1);
  });

  it('an unpaid completion (async payment) records pending and grants nothing', async () => {
    await asService(db);
    assert.equal((await complete('evt_2', 'cs_2', U.moss, 'void-wings-ash', false, 'pi_2')).rows[0].r, 'pending');
    assert.equal(await one(db, `select public.owns($1, 'void-wings-ash')`, [U.moss]), false);
    assert.equal(
      (await db.query(`select public.stripe_checkout_completed('evt_3', 'checkout.session.async_payment_succeeded', 'cs_2', $1, 'void-wings-ash', 'pi_2', 799, 'usd', true) as r`, [U.moss])).rows[0].r,
      'granted',
    );
    assert.equal(await one(db, `select public.owns($1, 'void-wings-ash')`, [U.moss]), true);
  });

  it('metadata that contradicts the recorded purchase is rejected and leaves no trace', async () => {
    await asService(db);
    await db.query(`select public.checkout_record($1, 'void-halo-ring', 'cs_3', 299, 'usd')`, [U.moss]);
    await rejects(complete('evt_4', 'cs_3', U.kestrel, 'void-halo-ring'), 'metadata_mismatch', 400);
    await asAdmin(db);
    assert.equal(await one(db, `select count(*)::int from public.stripe_events where id = 'evt_4'`), 0, 'rolled back, so a retry is processed');
    await asService(db);
    await rejects(complete('evt_5', 'cs_9', null, 'void-halo-ring'), 'bad_metadata', 400);
    await rejects(complete('evt_6', 'cs_9', U.kestrel, 'nope'), 'unknown_item', 400);
  });

  it('an expired session marks its pending purchase expired', async () => {
    await asService(db);
    assert.equal(await one(db, `select public.stripe_checkout_expired('evt_7', 'checkout.session.expired', 'cs_3')`), 'expired');
    assert.equal(await one(db, `select public.stripe_checkout_expired('evt_7', 'checkout.session.expired', 'cs_3')`), 'duplicate');
    await asAdmin(db);
    assert.equal(await one(db, `select status from public.purchases where stripe_session_id = 'cs_3'`), 'expired');
  });

  it('a refund revokes, unequips and audits; a re-purchase reinstates', async () => {
    await asService(db);
    await one(db, `select public.equip_slot($1, 'cape', 'void-cape-rift')`, [U.kestrel]);
    assert.equal(await one(db, `select public.stripe_refunded('evt_8', 'charge.refunded', 'pi_1')`), 'revoked');
    assert.equal(await one(db, `select public.stripe_refunded('evt_8', 'charge.refunded', 'pi_1')`), 'duplicate');
    assert.equal(await one(db, `select public.owns($1, 'void-cape-rift')`, [U.kestrel]), false);
    await asAdmin(db);
    assert.equal(await one(db, `select cape from public.equipped where player = $1`, [U.kestrel]), null);
    assert.equal(await one(db, `select status from public.purchases where stripe_session_id = 'cs_1'`), 'refunded');
    assert.equal(await one(db, `select count(*)::int from public.audit_log where action = 'refund'`), 1);

    await asService(db);
    assert.equal((await complete('evt_9', 'cs_10', U.kestrel, 'void-cape-rift', true, 'pi_10')).rows[0].r, 'granted');
    assert.equal(await one(db, `select public.owns($1, 'void-cape-rift')`, [U.kestrel]), true);
  });

  it('players see their own purchases and nothing of the webhook bookkeeping', async () => {
    await asUser(db, U.moss);
    const mine = await q(db, `select stripe_session_id from public.purchases order by stripe_session_id`);
    assert.deepEqual(mine.map((r) => r.stripe_session_id), ['cs_2', 'cs_3']);
    await rejects(db.query(`select * from public.stripe_events`), '42501');
    await rejects(db.query(`select * from public.audit_log`), '42501');
  });
});

describe('admin grants', () => {
  it('grant_item gives a grant-only item and audits it', async () => {
    await asService(db);
    await db.query(`select public.grant_item($1, 'void-badge-founder', 'admin:test', 'founder')`, [U.moss]);
    assert.equal(await one(db, `select public.owns($1, 'void-badge-founder')`, [U.moss]), true);
    const set = await one(db, `select public.equip_slot($1, 'badge', 'void-badge-founder')`, [U.moss]);
    assert.equal(set.badge, 'void-badge-founder');
  });
});

describe('auth hardening', () => {
  it('a password or email change on an auth user is discarded', async () => {
    await asAdmin(db);
    await db.query(`update auth.users set encrypted_password = 'x', email = 'me@example.com', email_change = 'me@example.com' where id = $1`, [U.kestrel]);
    const row = (await q(db, `select encrypted_password, email, email_change from auth.users where id = $1`, [U.kestrel]))[0];
    assert.equal(row.encrypted_password, null);
    assert.equal(row.email, `${U.kestrel.replace(/-/g, '')}@players.void.invalid`);
    assert.equal(row.email_change, '');
  });
});
