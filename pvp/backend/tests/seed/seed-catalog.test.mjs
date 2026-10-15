// scripts/seed-catalog.mjs against a fixture, including running its SQL in PGlite.

import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { applyRemote, findItems, loadCatalog, toSql } from '../../scripts/seed-catalog.mjs';
import { asAdmin, createPlayer, freshDb, one, q, U } from '../sql/db.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const fixture = JSON.parse(readFileSync(join(here, '../fixtures/cosmetics.fixture.json'), 'utf8'));

describe('reading cosmetics.json', () => {
  it('finds the items in every accepted shape', () => {
    const items = [{ id: 'a', kind: 'cape' }];
    assert.equal(findItems(items), items);
    assert.equal(findItems({ items }), items);
    assert.equal(findItems({ cosmetics: items }), items);
    assert.equal(findItems({ catalog: items }), items);
    assert.equal(findItems({ examples: [{ items }] }), items);
    assert.equal(findItems({ examples: [items] }), items);
    assert.equal(findItems({ nope: 1 }), null);
  });

  it('normalises the fixture and derives availability', () => {
    const { items, problems } = loadCatalog(fixture);
    assert.deepEqual(problems, []);
    const by = Object.fromEntries(items.map((i) => [i.id, i]));
    assert.equal(by['void-cape-origin'].availability, 'free');
    assert.equal(by['void-cape-rift'].availability, 'paid');
    assert.equal(by['void-badge-founder'].availability, 'grant', 'exclusive: true means grant-only');
    assert.equal(by['void-trail-spark'].stripe_price_id, 'price_123');
    assert.equal(by['emote-wave'].tintable, false);
    assert.equal(by['void-wings-ash'].rarity, 'legendary');
  });

  it('reports every problem and accepts nothing when there is one', () => {
    const { problems } = loadCatalog({
      items: [
        { id: 'Bad_Id', kind: 'cape' },
        { id: 'dup', kind: 'cape' },
        { id: 'dup', kind: 'cape' },
        { id: 'aura-x', kind: 'aura' },
        { id: 'neg', kind: 'cape', price_cents: -1 },
        { id: 'paid-free', kind: 'cape', availability: 'paid', price_cents: 0 },
      ],
    });
    assert.equal(problems.length, 5, problems.join('\n'));
    assert.ok(problems.some((p) => p.includes('kebab-case')));
    assert.ok(problems.some((p) => p.includes('duplicate')));
    assert.ok(problems.some((p) => p.includes('kind must be')));
    assert.ok(problems.some((p) => p.includes('price_cents')));
    assert.ok(problems.some((p) => p.includes('paid item needs')));
  });
});

describe('seeding', () => {
  it('the generated SQL loads, re-runs cleanly, deactivates leavers and backfills free items', async () => {
    const db = await freshDb();
    await createPlayer(db, U.kestrel, 'Kestrel');
    const { items } = loadCatalog(fixture);

    await asAdmin(db);
    await db.exec(toSql(items));
    await db.exec(toSql(items)); // idempotent
    assert.equal(await one(db, `select count(*)::int from public.catalog`), 8);
    assert.deepEqual(
      (await q(db, `select item from public.entitlements where player = $1 order by item`, [U.kestrel])).map((r) => r.item),
      ['emote-wave', 'void-cape-origin'],
      'a player who signed up before the catalog existed is backfilled',
    );
    const preview = await one(db, `select preview from public.catalog where id = 'void-cape-rift'`);
    assert.deepEqual(preview, { front: 'rift-front.png', back: 'rift-back.png' });
    assert.equal(await one(db, `select preview from public.catalog where id = 'void-cape-origin'`), 'previews/void-cape-origin.png');
    assert.equal(await one(db, `select name from public.catalog where id = 'emote-gg'`), "GG — 'good game'");

    // An item leaves the file: it is deactivated, not deleted, and its owners keep it.
    await db.exec(toSql(items.filter((i) => i.id !== 'void-halo-ring')));
    assert.equal(await one(db, `select active from public.catalog where id = 'void-halo-ring'`), false);
  });

  it('applyRemote speaks PostgREST with the service key', async () => {
    const calls = [];
    const fakeFetch = async (url, init) => {
      calls.push({ url, method: init.method, headers: init.headers, body: init.body });
      return new Response(url.includes('backfill') ? '3' : '', { status: url.includes('backfill') ? 200 : 201 });
    };
    const { items } = loadCatalog(fixture);
    const granted = await applyRemote(items, { url: 'https://x.supabase.co/', serviceKey: 'legacy.jwt.key', fetchImpl: fakeFetch });
    assert.equal(granted, 3);
    assert.equal(calls[0].url, 'https://x.supabase.co/rest/v1/catalog?on_conflict=id');
    assert.equal(calls[0].headers.prefer, 'resolution=merge-duplicates,return=minimal');
    assert.equal(calls[0].headers.authorization, 'Bearer legacy.jwt.key');
    assert.equal(JSON.parse(calls[0].body).length, 8);
    assert.match(calls[1].url, /catalog\?id=not\.in\.\(.*"void-cape-rift".*\)&active=is\.true$/);
    assert.equal(calls[1].method, 'PATCH');
    assert.equal(calls[2].url, 'https://x.supabase.co/rest/v1/rpc/backfill_free_grants');

    calls.length = 0;
    await applyRemote(items, { url: 'https://x.supabase.co', serviceKey: 'sb_secret_abc', fetchImpl: fakeFetch, deactivate: false });
    assert.equal(calls.length, 2);
    assert.equal(calls[0].headers.authorization, undefined, 'new secret keys are not bearer tokens');
    assert.equal(calls[0].headers.apikey, 'sb_secret_abc');
  });
});
