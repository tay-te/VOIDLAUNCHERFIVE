/**
 * Everything a handler needs from the outside world, injected so tests can fake it.
 *
 * Each function's `index.ts` builds this from `Deno.env` and hands it to `handler(deps)`;
 * the tests build it from a map and a fake `fetch`. Handlers never read the environment or
 * the clock themselves.
 */

import { Supabase, type Fetch } from './supabase.ts';

export type EnvGet = (name: string) => string | undefined;

export interface Deps {
  db: Supabase;
  fetch: Fetch;
  env: EnvGet;
  nowMs: () => number;
}

function required(get: EnvGet, ...names: string[]): string {
  for (const n of names) {
    const v = get(n);
    if (v && v.trim()) return v.trim();
  }
  throw new Error(`missing environment variable: ${names.join(' or ')}`);
}

/**
 * `SUPABASE_URL`, `SUPABASE_ANON_KEY` and `SUPABASE_SERVICE_ROLE_KEY` are injected into every
 * edge function by Supabase. Projects that have moved to the new API keys can set
 * `VOID_PUBLISHABLE_KEY` / `VOID_SECRET_KEY` as secrets to use those instead.
 */
export function makeDeps(get: EnvGet, fetchImpl: Fetch, nowMs: () => number = Date.now): Deps {
  const db = new Supabase(
    {
      url: required(get, 'SUPABASE_URL').replace(/\/+$/, ''),
      anonKey: required(get, 'VOID_PUBLISHABLE_KEY', 'SUPABASE_ANON_KEY'),
      serviceKey: required(get, 'VOID_SECRET_KEY', 'SUPABASE_SERVICE_ROLE_KEY'),
    },
    fetchImpl,
  );
  return { db, fetch: fetchImpl, env: get, nowMs };
}
