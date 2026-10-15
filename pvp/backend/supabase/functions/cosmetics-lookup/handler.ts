/**
 * `cosmetics-lookup` — EquippedSets for a batch of players.
 *
 *   POST {"uuids":[...]}   or   GET ?uuids=a,b,c      (≤ 100, dashed or undashed)
 *   → {"sets":[EquippedSet...]}
 *
 * Only VOID players appear; a VOID player wearing nothing appears with every slot null — which
 * is how the mod knows to draw the "VOID player" badge. Answers carry
 * `Cache-Control: private, max-age=30` and a weak ETag over the sorted request, so a client
 * that repeats a batch can send `If-None-Match` and get a 304.
 */

import { ApiError, guarded, json, normalizeUuid, readJson, requireMethod } from '../_shared/http.ts';
import { rateLimit, requireUser, LIMITS } from '../_shared/guard.ts';
import type { Deps } from '../_shared/deps.ts';

export const MAX_BATCH = 100;

async function etagOf(body: string): Promise<string> {
  const digest = new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(body)));
  return `W/"${[...digest.slice(0, 12)].map((b) => b.toString(16).padStart(2, '0')).join('')}"`;
}

export function handler(deps: Deps) {
  const { db } = deps;
  return guarded(async (req) => {
    requireMethod(req, 'GET', 'POST');
    const caller = await requireUser(req, db);
    await rateLimit(db, `lookup:${caller.uuid}`, LIMITS.lookup.max, LIMITS.lookup.windowS);

    let raw: unknown[];
    if (req.method === 'GET') {
      raw = (new URL(req.url).searchParams.get('uuids') ?? '').split(',').filter(Boolean);
    } else {
      const body = await readJson(req, 16 * 1024);
      if (!Array.isArray(body.uuids)) throw new ApiError(400, 'bad_request', 'uuids must be an array.');
      raw = body.uuids;
    }
    const uuids = [...new Set(raw.map(normalizeUuid).filter((u): u is string => u !== null))].sort();
    if (uuids.length > MAX_BATCH) {
      throw new ApiError(400, 'too_many', `Ask for at most ${MAX_BATCH} players at a time.`);
    }
    if (uuids.length === 0) return json({ sets: [] }, 200, { 'cache-control': 'private, max-age=30' });

    const sets = await db.rpc<unknown[]>('lookup_equipped', { uuids });
    const text = JSON.stringify({ sets });
    const etag = await etagOf(text);
    const headers = { 'cache-control': 'private, max-age=30', etag, vary: 'authorization' };
    if (req.headers.get('if-none-match') === etag) {
      return new Response(null, { status: 304, headers });
    }
    return new Response(text, { status: 200, headers: { 'content-type': 'application/json; charset=utf-8', ...headers } });
  });
}
