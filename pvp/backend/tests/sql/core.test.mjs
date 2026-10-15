// Players, catalog, entitlements, equipped sets, rate limits, and the RLS/grant lockdown.

import { before, describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { asAdmin, asAnon, asService, asUser, createPlayer, freshDb, one, q, rejects, seedCatalog, U } from './db.mjs';

let db;

before(async () => {
  db = await freshDb();
  await seedCatalog(db);
  await createPlayer(db, U.kestrel, 'Kestrel');
  await createPlayer(db, U.moss, 'Moss');
});

describe('free items', () => {
  it('a new player is granted every active free item, and only those', async () => {
    await asAdmin(db);
    const rows = await q(db, `select item, source from public.entitlements where player = $1 order by item`, [U.kestrel]);
    assert.deepEqual(rows, [
      { item: 'emote-wave', source: 'free' },
      { item: 'void-cape-origin', source: 'free' },
    ]);
  });

  it('a free item added later is owned at once, and backfill records it', async () => {
    await asAdmin(db);
    await db.exec(`insert into public.catalog (id, kind, name, price_cents, availability) values ('void-trail-dust', 'trail', 'Dust', 0, 'free')`);
    assert.equal(await one(db, `select public.owns($1, 'void-trail-dust')`, [U.kestrel]), true);
    assert.equal(await one(db, `select public.backfill_free_grants()`), 2);
    assert.equal(await one(db, `select public.backfill_free_grants()`), 0, 'backfill is idempotent');
  });

  it('grant-only items are never auto-granted', async () => {
    assert.equal(await one(db, `select public.owns($1, 'void-badge-founder')`, [U.kestrel]), false);
  });
});

describe('equipping', () => {
  it('equips an owned item in its slot and returns the contract shape', async () => {
    await asService(db);
    const set = await one(db, `select public.equip_slot($1, 'cape', 'void-cape-origin')`, [U.kestrel]);
    assert.deepEqual(set, {
      uuid: U.kestrel, cape: 'void-cape-origin', back: null, head: null, face: null,
      halo: null, trail: null, badge: null, emotes: [],
    });
  });

  it('refuses an item the player does not own (403) and one in the wrong slot (400)', async () => {
    await asService(db);
    await rejects(db.query(`select public.equip_slot($1, 'cape', 'void-cape-rift')`, [U.kestrel]), 'not_owned', 403);
    await rejects(db.query(`select public.equip_slot($1, 'back', 'void-cape-origin')`, [U.kestrel]), 'wrong_slot', 400);
    await rejects(db.query(`select public.equip_slot($1, 'aura', null)`, [U.kestrel]), 'bad_slot', 400);
    await rejects(db.query(`select public.equip_slot($1, 'cape', 'nope')`, [U.kestrel]), 'unknown_item', 404);
  });

  it('the trigger holds even for a direct write by the table owner', async () => {
    await asAdmin(db);
    await rejects(db.query(`update public.equipped set halo = 'void-halo-ring' where player = $1`, [U.kestrel]), 'not_owned', 403);
  });

  it('null takes an item off', async () => {
    await asService(db);
    const set = await one(db, `select public.equip_slot($1, 'cape', null)`, [U.kestrel]);
    assert.equal(set.cape, null);
  });

  it('the emote wheel: owned emotes only, no repeats, at most eight', async () => {
    await asService(db);
    const set = await one(db, `select public.set_emotes($1, array['emote-wave'])`, [U.kestrel]);
    assert.deepEqual(set.emotes, ['emote-wave']);
    await rejects(db.query(`select public.set_emotes($1, array['emote-gg'])`, [U.kestrel]), 'not_owned', 403);
    await rejects(db.query(`select public.set_emotes($1, array['emote-wave','emote-wave'])`, [U.kestrel]), 'duplicate_emote', 400);
    await rejects(db.query(`select public.set_emotes($1, array['void-cape-origin'])`, [U.kestrel]), 'wrong_slot', 400);
    await rejects(db.query(`select public.set_emotes($1, array['a','b','c','d','e','f','g','h','i'])`, [U.kestrel]), 'too_many', 400);
  });

  it('tints: only on a tintable equipped item, dropped when the item comes off', async () => {
    await asAdmin(db);
    await db.query(`select public.grant_item($1, 'void-cape-rift', 'test')`, [U.kestrel]);
    await asService(db);
    await rejects(db.query(`select public.set_tint($1, 'cape', '#9F8BFF')`, [U.kestrel]), 'not_tintable', 400);
    await one(db, `select public.equip_slot($1, 'cape', 'void-cape-rift')`, [U.kestrel]);
    let set = await one(db, `select public.set_tint($1, 'cape', '#9F8BFF')`, [U.kestrel]);
    assert.deepEqual(set.tints, { cape: '#9f8bff' });
    await rejects(db.query(`select public.set_tint($1, 'cape', 'purple')`, [U.kestrel]), 'bad_color', 400);
    set = await one(db, `select public.equip_slot($1, 'cape', 'void-cape-origin')`, [U.kestrel]);
    assert.equal(set.tints, undefined, 'tints omitted once empty');
  });

  it('revoking an entitlement takes the item off', async () => {
    await asService(db);
    await one(db, `select public.equip_slot($1, 'cape', 'void-cape-rift')`, [U.kestrel]);
    await asAdmin(db);
    await db.query(`select public.revoke_item($1, 'void-cape-rift', 'test', 'refund')`, [U.kestrel]);
    assert.equal(await one(db, `select cape from public.equipped where player = $1`, [U.kestrel]), null);
  });
});

describe('reading', () => {
  it('my_cosmetics answers for the signed-in player only', async () => {
    await asUser(db, U.kestrel);
    const mine = await one(db, `select public.my_cosmetics()`);
    assert.ok(mine.owned.includes('void-cape-origin'));
    assert.ok(mine.owned.includes('void-trail-dust'));
    assert.ok(!mine.owned.includes('void-cape-rift'), 'revoked is not owned');
    assert.equal(mine.equipped.uuid, U.kestrel);
    await asUser(db, '00000000-0000-0000-0000-000000000000');
    await rejects(db.query(`select public.my_cosmetics()`), 'signed_out', 401);
  });

  it('lookup_equipped returns VOID players only, with empty sets for players wearing nothing', async () => {
    await asUser(db, U.kestrel);
    const sets = await one(db, `select public.lookup_equipped($1::uuid[])`, [[U.moss, U.tavi]]);
    assert.equal(sets.length, 1);
    assert.equal(sets[0].uuid, U.moss);
    assert.equal(sets[0].cape, null);
  });

  it('players can read the catalog and equipped sets, not others’ entitlements', async () => {
    await asUser(db, U.moss);
    assert.ok((await one(db, `select count(*)::int from public.catalog`)) >= 8);
    assert.equal(await one(db, `select count(*)::int from public.entitlements where player = $1`, [U.kestrel]), 0);
    assert.ok((await one(db, `select count(*)::int from public.entitlements where player = $1`, [U.moss])) > 0);
    await asAnon(db);
    assert.ok((await one(db, `select count(*)::int from public.catalog`)) >= 8);
    assert.equal(await one(db, `select count(*)::int from public.players`), 0, 'anon sees no players');
  });
});

describe('lockdown', () => {
  it('no client role can write a table directly', async () => {
    await asUser(db, U.kestrel);
    await rejects(db.query(`insert into public.entitlements (player, item, source) values ($1, 'void-wings-ash', 'grant')`, [U.kestrel]), '42501');
    await rejects(db.query(`update public.equipped set cape = null where player = $1`, [U.kestrel]), '42501');
    await rejects(db.query(`insert into public.catalog (id, kind, name) values ('x', 'cape', 'x')`), '42501');
    await rejects(db.query(`select * from public.rate_limits`), '42501');
  });

  it('service-only functions are not executable by players', async () => {
    await asUser(db, U.kestrel);
    for (const call of [
      `select public.equip_slot('${U.kestrel}', 'cape', null)`,
      `select public.grant_item('${U.kestrel}', 'void-wings-ash', 'me')`,
      `select public.player_upsert('${U.kestrel}', 'Evil')`,
      `select public.friends_list('${U.kestrel}')`,
      `select public.stripe_checkout_completed('e', 't', 's', '${U.kestrel}', 'void-wings-ash', null, 0, 'usd', true)`,
      `select public.rate_limit_hit('k', 1, 60)`,
      `select public.backfill_free_grants()`,
    ]) {
      await rejects(db.query(call), '42501');
    }
  });

  it('rate_limit_hit counts per key and window', async () => {
    await asService(db);
    assert.equal(await one(db, `select public.rate_limit_hit('t', 2, 60)`), true);
    assert.equal(await one(db, `select public.rate_limit_hit('t', 2, 60)`), true);
    assert.equal(await one(db, `select public.rate_limit_hit('t', 2, 60)`), false);
    assert.equal(await one(db, `select public.rate_limit_hit('other', 2, 60)`), true);
  });
});
