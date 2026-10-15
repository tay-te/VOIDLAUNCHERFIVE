/**
 * A fresh database per test file: PGlite + the Supabase shim + every migration, in order.
 *
 * Role switching mirrors what PostgREST does per request: `set role authenticated` plus the
 * JWT claims in `request.jwt.claims`, so `auth.uid()` and every RLS policy see exactly what
 * they would in production.
 */

import { readFileSync, readdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';
import { PGlite } from '@electric-sql/pglite';

const here = dirname(fileURLToPath(import.meta.url));
export const MIGRATIONS = join(here, '../../supabase/migrations');

export async function freshDb() {
  const db = new PGlite();
  await db.exec(readFileSync(join(here, 'supabase-shim.sql'), 'utf8'));
  for (const f of readdirSync(MIGRATIONS).filter((f) => f.endsWith('.sql')).sort()) {
    try {
      await db.exec(readFileSync(join(MIGRATIONS, f), 'utf8'));
    } catch (e) {
      throw new Error(`migration ${f} failed: ${e.message}`);
    }
  }
  return db;
}

/** Back to the superuser (the migration owner). */
export async function asAdmin(db) {
  await db.exec(`reset role; select set_config('request.jwt.claims', '', false); select set_config('realtime.topic', '', false);`);
}

export async function asService(db) {
  await asAdmin(db);
  await db.exec(`set role service_role; select set_config('request.jwt.claims', '{"role":"service_role"}', false);`);
}

export async function asUser(db, uuid, topic = '') {
  await asAdmin(db);
  const claims = JSON.stringify({ sub: uuid, role: 'authenticated' });
  await db.query(`select set_config('request.jwt.claims', $1, false), set_config('realtime.topic', $2, false)`, [claims, topic]);
  await db.exec('set role authenticated;');
}

export async function asAnon(db) {
  await asAdmin(db);
  await db.exec(`set role anon; select set_config('request.jwt.claims', '{"role":"anon"}', false);`);
}

/** Run as admin and return rows. */
export async function q(db, sql, params = []) {
  return (await db.query(sql, params)).rows;
}

/** One value from the first row. */
export async function one(db, sql, params = []) {
  const rows = (await db.query(sql, params)).rows;
  return rows.length ? Object.values(rows[0])[0] : undefined;
}

/**
 * Assert the statement fails. `code` matches our `raise_api` machine code (the error message)
 * or a SQLSTATE such as 42501 (permission denied).
 */
export async function rejects(promise, code, status) {
  try {
    await promise;
  } catch (e) {
    const got = `${e.code} ${e.message}`;
    assert.ok(e.message === code || e.code === code, `expected ${code}, got ${got}`);
    if (status) assert.equal(e.code, `PT${status}`, `expected PT${status}, got ${got}`);
    return e;
  }
  assert.fail(`expected failure ${code}, but it succeeded`);
}

export const U = {
  kestrel: '069a79f4-44e9-4726-a5be-fca90e38aaf5',
  moss: '853c80ef-3c37-49fd-aa49-938b674adae6',
  tavi: '61699b2e-d327-4a01-9f1e-0ea8c3f06bc6',
  rin: 'f84c6a79-0a4e-45e0-879b-cd49ebd4c4e2',
};

export async function createPlayer(db, uuid, name) {
  await asAdmin(db);
  await db.query(`insert into auth.users (id, email) values ($1, $2) on conflict do nothing`, [uuid, `${uuid.replace(/-/g, '')}@players.void.invalid`]);
  await db.query(`select public.player_upsert($1, $2)`, [uuid, name]);
}

/** A small catalog covering every availability and a tintable item. */
export async function seedCatalog(db) {
  await asAdmin(db);
  await db.exec(`
    insert into public.catalog (id, kind, name, price_cents, availability, tintable) values
      ('void-cape-origin', 'cape', 'Origin cape', 0, 'free', false),
      ('void-cape-rift', 'cape', 'Rift cape', 499, 'paid', true),
      ('void-wings-ash', 'back', 'Ash wings', 799, 'paid', false),
      ('void-halo-ring', 'halo', 'Ring halo', 299, 'paid', true),
      ('void-badge-founder', 'badge', 'Founder', 0, 'grant', false),
      ('emote-wave', 'emote', 'Wave', 0, 'free', false),
      ('emote-gg', 'emote', 'GG', 199, 'paid', false),
      ('emote-bow', 'emote', 'Bow', 199, 'paid', false);
  `);
}
