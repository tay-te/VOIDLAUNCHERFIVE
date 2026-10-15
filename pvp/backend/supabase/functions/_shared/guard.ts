/**
 * Who is calling, and are they calling too often.
 *
 * `requireUser` asks GoTrue rather than decoding the JWT locally: one round trip buys
 * signature, expiry, ban and session-revocation checks under either key system, and the
 * functions that need it are not on a per-frame path (the launcher batches and caches).
 */

import { ApiError, bearer, UUID_RE } from './http.ts';
import type { Supabase } from './supabase.ts';

export interface Caller {
  /** The player's dashed Minecraft UUID — also their auth user id. */
  uuid: string;
}

export async function requireUser(req: Request, db: Supabase): Promise<Caller> {
  const token = bearer(req);
  if (!token) {
    throw new ApiError(401, 'signed_out', 'Sign in with a Microsoft account to use online features.');
  }
  const user = await db.getUser(token);
  if (!user || typeof user.id !== 'string' || !UUID_RE.test(user.id)) {
    throw new ApiError(401, 'signed_out', 'Your session expired. Sign in again.');
  }
  return { uuid: user.id };
}

/**
 * Fixed-window limit backed by `public.rate_limit_hit`. `key` names the bucket
 * (`equip:<uuid>`, `auth:ip:<ip>`); throws 429 with Retry-After once it is exceeded.
 */
export async function rateLimit(db: Supabase, key: string, max: number, windowS: number): Promise<void> {
  const ok = await db.rpc<boolean>('rate_limit_hit', { p_key: key, p_max: max, p_window_s: windowS });
  if (!ok) {
    throw new ApiError(429, 'rate_limited', 'Slow down a little and try again.', {
      'retry-after': String(windowS),
    });
  }
}

/** Limits per function, per caller. One table so the README can quote it. */
export const LIMITS = {
  authPerIp: { max: 20, windowS: 60 },
  authPerName: { max: 6, windowS: 60 },
  lookup: { max: 120, windowS: 60 },
  equip: { max: 60, windowS: 60 },
  checkout: { max: 10, windowS: 60 },
  friends: { max: 60, windowS: 60 },
} as const;
