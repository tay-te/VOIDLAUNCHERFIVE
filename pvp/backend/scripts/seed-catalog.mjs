#!/usr/bin/env node
/**
 * Seed the Supabase `catalog` table from `pvp/schema/cosmetics.json` — the single source of
 * cosmetic metadata (pvp/docs/online-contract.md, "Assets").
 *
 *   node scripts/seed-catalog.mjs --check                 validate the file, print a summary
 *   node scripts/seed-catalog.mjs --sql supabase/seed.sql  write SQL (local `supabase db reset`)
 *   node scripts/seed-catalog.mjs --apply                 upsert into the linked project over REST
 *        (needs SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY — or VOID_SECRET_KEY — in the env)
 *   --file <path>        read another file (the tests use a fixture)
 *   --no-deactivate      leave catalog rows that are no longer in the file active
 *
 * What it accepts. `cosmetics.json` is written by another workstream, so the reader is liberal
 * in shape and strict in content: the items may be the document itself (an array), or under
 * `items` / `cosmetics` / `catalog`, or — the house style of `schema/mods.json` — under
 * `examples[0]`. Each item needs `id` (kebab-case) and `kind` (a slot or `emote`; `slot` and
 * `type` are read as synonyms), and may carry `name`, `rarity`, `price_cents`, `tintable`,
 * `preview`, `active`, `stripe_price_id` and `availability`.
 *
 * `availability` is how an item is obtained: `free` (everyone owns it), `paid` (sold through
 * Stripe), `grant` (never sold, only granted — founder badges, event rewards). When absent it
 * is derived: an item flagged `grant_only`, `exclusive` or `purchasable: false` is `grant`;
 * otherwise `price_cents > 0` is `paid` and `0` is `free`.
 *
 * Items that disappear from the file are set `active = false` rather than deleted: owners keep
 * them, nobody new can buy them. Then free items are backfilled to every existing player.
 */

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
export const DEFAULT_FILE = resolve(here, '../../schema/cosmetics.json');

export const KINDS = ['cape', 'back', 'head', 'face', 'halo', 'trail', 'badge', 'emote'];
const ID_RE = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

/** Find the item array in any of the accepted document shapes. */
export function findItems(doc) {
  const candidates = [doc, doc?.examples?.[0]];
  for (const c of candidates) {
    if (Array.isArray(c)) return c;
    for (const key of ['items', 'cosmetics', 'catalog']) {
      if (Array.isArray(c?.[key])) return c[key];
    }
  }
  return null;
}

/**
 * Validate and normalise. Returns `{ items, problems }`; any problem means nothing is written.
 * @param {unknown} doc
 */
export function loadCatalog(doc) {
  const raw = findItems(doc);
  const problems = [];
  if (!raw) return { items: [], problems: ['no item array found (expected an array, items, cosmetics, catalog or examples[0])'] };

  const seen = new Set();
  const items = [];
  raw.forEach((it, i) => {
    const where = `item ${i}${it && typeof it.id === 'string' ? ` (${it.id})` : ''}`;
    if (!it || typeof it !== 'object') return problems.push(`${where}: not an object`);
    const id = it.id;
    const kind = it.kind ?? it.slot ?? it.type;
    if (typeof id !== 'string' || id.length > 64 || !ID_RE.test(id)) problems.push(`${where}: id must be kebab-case, ≤ 64 chars`);
    else if (seen.has(id)) problems.push(`${where}: duplicate id`);
    else seen.add(id);
    if (!KINDS.includes(kind)) problems.push(`${where}: kind must be one of ${KINDS.join(', ')}`);

    const price = it.price_cents ?? (typeof it.price === 'number' ? Math.round(it.price * 100) : 0);
    if (!Number.isInteger(price) || price < 0) problems.push(`${where}: price_cents must be a whole number ≥ 0`);

    let availability = it.availability;
    if (availability === undefined) {
      if (it.grant_only === true || it.exclusive === true || it.purchasable === false) availability = 'grant';
      else availability = price > 0 ? 'paid' : 'free';
    }
    if (!['free', 'paid', 'grant'].includes(availability)) problems.push(`${where}: availability must be free, paid or grant`);
    if (availability === 'paid' && !(price > 0)) problems.push(`${where}: a paid item needs price_cents > 0`);

    const name = typeof it.name === 'string' && it.name.trim() ? it.name.trim() : id;
    if (typeof name === 'string' && name.length > 80) problems.push(`${where}: name longer than 80 characters`);

    items.push({
      id,
      kind,
      name,
      rarity: typeof it.rarity === 'string' ? it.rarity : null,
      price_cents: Number.isInteger(price) ? price : 0,
      availability,
      tintable: it.tintable === true,
      preview: it.preview === undefined ? null : it.preview,
      active: it.active !== false,
      stripe_price_id: typeof it.stripe_price_id === 'string' && it.stripe_price_id ? it.stripe_price_id : null,
    });
  });
  return { items, problems };
}

const lit = (v) => {
  if (v === null || v === undefined) return 'null';
  if (typeof v === 'boolean') return v ? 'true' : 'false';
  if (typeof v === 'number') return String(v);
  if (typeof v === 'object') return `'${JSON.stringify(v).replace(/'/g, "''")}'::jsonb`;
  return `'${String(v).replace(/'/g, "''")}'`;
};

/** jsonb literal for any JSON value (a preview may be a path string or an object). */
const jsonbLit = (v) => (v === null || v === undefined ? 'null' : `'${JSON.stringify(v).replace(/'/g, "''")}'::jsonb`);

const COLUMNS = ['id', 'kind', 'name', 'rarity', 'price_cents', 'availability', 'tintable', 'preview', 'active', 'stripe_price_id'];

/** SQL that upserts the catalog, deactivates what left it, and backfills free grants. */
export function toSql(items, { deactivate = true } = {}) {
  const lines = [
    '-- Generated by pvp/backend/scripts/seed-catalog.mjs from pvp/schema/cosmetics.json. Do not edit.',
    'begin;',
  ];
  if (items.length > 0) {
    lines.push(`insert into public.catalog (${COLUMNS.join(', ')}) values`);
    lines.push(
      items
        .map((it) => `  (${COLUMNS.map((c) => (c === 'preview' ? jsonbLit(it.preview) : lit(it[c]))).join(', ')})`)
        .join(',\n'),
    );
    lines.push(
      `on conflict (id) do update set ${COLUMNS.filter((c) => c !== 'id').map((c) => `${c} = excluded.${c}`).join(', ')}, updated_at = now();`,
    );
  }
  if (deactivate) {
    const ids = items.map((it) => lit(it.id)).join(', ');
    lines.push(
      items.length > 0
        ? `update public.catalog set active = false, updated_at = now() where active and id not in (${ids});`
        : 'update public.catalog set active = false, updated_at = now() where active;',
    );
  }
  lines.push('select public.backfill_free_grants();', 'commit;', '');
  return lines.join('\n');
}

/** Upsert over PostgREST with the service key — for a remote project with no psql handy. */
export async function applyRemote(items, { url, serviceKey, fetchImpl = fetch, deactivate = true }) {
  const base = url.replace(/\/+$/, '');
  const auth = serviceKey.startsWith('sb_') ? { apikey: serviceKey } : { apikey: serviceKey, authorization: `Bearer ${serviceKey}` };
  const call = async (path, init) => {
    const res = await fetchImpl(`${base}${path}`, { ...init, headers: { ...auth, 'content-type': 'application/json', ...(init.headers ?? {}) } });
    if (!res.ok) throw new Error(`${init.method} ${path} → ${res.status} ${await res.text()}`);
    const text = await res.text();
    return text ? JSON.parse(text) : null;
  };
  if (items.length > 0) {
    await call('/rest/v1/catalog?on_conflict=id', {
      method: 'POST',
      headers: { prefer: 'resolution=merge-duplicates,return=minimal' },
      body: JSON.stringify(items.map((it) => ({ ...it, updated_at: new Date().toISOString() }))),
    });
  }
  if (deactivate) {
    const ids = items.map((it) => `"${it.id}"`).join(',');
    const filter = items.length > 0 ? `id=not.in.(${ids})&active=is.true` : 'active=is.true';
    await call(`/rest/v1/catalog?${filter}`, { method: 'PATCH', headers: { prefer: 'return=minimal' }, body: JSON.stringify({ active: false }) });
  }
  return call('/rest/v1/rpc/backfill_free_grants', { method: 'POST', body: '{}' });
}

function summary(items) {
  const by = {};
  for (const it of items) by[it.kind] = (by[it.kind] ?? 0) + 1;
  const avail = {};
  for (const it of items) avail[it.availability] = (avail[it.availability] ?? 0) + 1;
  return `${items.length} items — ${Object.entries(by).map(([k, v]) => `${k} ${v}`).join(', ')} — ${Object.entries(avail).map(([k, v]) => `${k} ${v}`).join(', ')}`;
}

async function main(argv) {
  const arg = (name) => {
    const i = argv.indexOf(name);
    return i >= 0 ? argv[i + 1] : undefined;
  };
  const file = resolve(arg('--file') ?? DEFAULT_FILE);
  let doc;
  try {
    doc = JSON.parse(readFileSync(file, 'utf8'));
  } catch (e) {
    console.error(`could not read ${file}: ${e.message}`);
    return 1;
  }
  const { items, problems } = loadCatalog(doc);
  if (problems.length > 0) {
    console.error(`${file} has ${problems.length} problem(s):`);
    for (const p of problems) console.error(`  - ${p}`);
    return 1;
  }
  console.log(`${file}: ${summary(items)}`);
  const deactivate = !argv.includes('--no-deactivate');

  const sqlOut = arg('--sql');
  if (sqlOut) {
    writeFileSync(resolve(sqlOut), toSql(items, { deactivate }));
    console.log(`wrote ${sqlOut}`);
  }
  if (argv.includes('--apply')) {
    const url = process.env.SUPABASE_URL;
    const serviceKey = process.env.VOID_SECRET_KEY || process.env.SUPABASE_SERVICE_ROLE_KEY;
    if (!url || !serviceKey) {
      console.error('--apply needs SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY (or VOID_SECRET_KEY)');
      return 1;
    }
    const granted = await applyRemote(items, { url, serviceKey, deactivate });
    console.log(`applied to ${url}; ${granted ?? 0} free grant(s) backfilled`);
  }
  return 0;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main(process.argv.slice(2)).then((code) => process.exit(code));
}
