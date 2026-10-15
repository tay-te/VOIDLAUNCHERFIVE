/**
 * Proving a Minecraft identity, two ways (pvp/docs/online-contract.md, "Auth").
 *
 * `join` (what the launcher uses): we hand out a stateless challenge, the launcher tells
 * Mojang's session server it "joined" a server with that id, and we ask Mojang whether that
 * name did. The player's access token never reaches us — the same handshake every online-mode
 * Minecraft server performs.
 *
 * `token` (the contract's original method, kept as a fallback): the launcher sends its
 * Minecraft access token and we read the profile it belongs to.
 */

import { ApiError, normalizeUuid } from './http.ts';
import type { Fetch } from './supabase.ts';

export interface McProfile {
  uuid: string; // dashed
  name: string;
}

export const MOJANG = {
  profile: 'https://api.minecraftservices.com/minecraft/profile',
  hasJoined: 'https://sessionserver.mojang.com/session/minecraft/hasJoined',
};

const NAME_RE = /^[A-Za-z0-9_]{1,16}$/;

function profileFrom(body: unknown): McProfile {
  const b = body as { id?: unknown; name?: unknown };
  const uuid = normalizeUuid(b?.id);
  if (!uuid || typeof b.name !== 'string' || !NAME_RE.test(b.name)) {
    throw new ApiError(502, 'mojang_unavailable', 'Minecraft returned a profile we could not read.');
  }
  return { uuid, name: b.name };
}

/** `mode: "token"` — whose Minecraft access token is this? */
export async function profileFromAccessToken(token: string, fetchImpl: Fetch, base = MOJANG.profile): Promise<McProfile> {
  const res = await fetchImpl(base, { headers: { authorization: `Bearer ${token}`, accept: 'application/json' } });
  if (res.status === 401) {
    throw new ApiError(401, 'bad_minecraft_token', 'Your Minecraft sign-in expired. Sign in again.');
  }
  if (res.status === 404) {
    throw new ApiError(403, 'no_minecraft', 'This Microsoft account does not own Minecraft: Java Edition.');
  }
  if (res.status === 429) {
    throw new ApiError(429, 'mojang_busy', 'Minecraft is busy. Try again in a minute.', { 'retry-after': '60' });
  }
  if (!res.ok) {
    throw new ApiError(502, 'mojang_unavailable', 'Minecraft sign-in is unavailable right now.');
  }
  return profileFrom(await res.json());
}

/** `mode: "join"` — did `name` just join with `serverId`? Null when it did not. */
export async function hasJoined(name: string, serverId: string, fetchImpl: Fetch, base = MOJANG.hasJoined): Promise<McProfile | null> {
  if (!NAME_RE.test(name)) {
    throw new ApiError(400, 'invalid_name', 'That is not a Minecraft name.');
  }
  const url = `${base}?username=${encodeURIComponent(name)}&serverId=${encodeURIComponent(serverId)}`;
  const res = await fetchImpl(url, { headers: { accept: 'application/json' } });
  if (res.status === 204) return null;
  if (res.status === 429) {
    throw new ApiError(429, 'mojang_busy', 'Minecraft is busy. Try again in a minute.', { 'retry-after': '60' });
  }
  if (!res.ok) {
    throw new ApiError(502, 'mojang_unavailable', 'Minecraft sign-in is unavailable right now.');
  }
  const text = await res.text();
  if (!text.trim()) return null;
  const profile = profileFrom(JSON.parse(text));
  // Mojang matches the name case-insensitively; the profile's own spelling is canonical.
  return profile.name.toLowerCase() === name.toLowerCase() ? profile : null;
}

// ---------------------------------------------------------------------------------------------
// the stateless challenge

/** How long a challenge stays redeemable. */
export const CHALLENGE_TTL_S = 120;

const enc = new TextEncoder();

async function hmacHex(secret: string, message: string): Promise<string> {
  const key = await crypto.subtle.importKey('raw', enc.encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  const sig = new Uint8Array(await crypto.subtle.sign('HMAC', key, enc.encode(message)));
  return [...sig].map((b) => b.toString(16).padStart(2, '0')).join('');
}

export function randomHex(bytes: number): string {
  const b = new Uint8Array(bytes);
  crypto.getRandomValues(b);
  return [...b].map((x) => x.toString(16).padStart(2, '0')).join('');
}

export interface Challenge {
  server_id: string;
  ts: number;
  nonce: string;
}

/**
 * `server_id = HMAC-SHA256(secret, "<ts>.<nonce>")[:40 hex]`. Nothing is stored: redeeming
 * recomputes it. A Minecraft server hash is a SHA-1 digest the client computes over a secret it
 * generates itself, so a hostile game server cannot steer a player's join onto one of these.
 */
export async function makeChallenge(secret: string, nowS: number, nonce = randomHex(16)): Promise<Challenge> {
  if (!secret || secret.length < 32) {
    throw new ApiError(500, 'misconfigured', 'Sign-in is not configured on this server.');
  }
  const server_id = (await hmacHex(secret, `${nowS}.${nonce}`)).slice(0, 40);
  return { server_id, ts: nowS, nonce };
}

export async function redeemChallenge(secret: string, ts: unknown, nonce: unknown, nowS: number): Promise<string> {
  if (typeof ts !== 'number' || !Number.isInteger(ts) || typeof nonce !== 'string' || !/^[0-9a-f]{32}$/.test(nonce)) {
    throw new ApiError(400, 'bad_challenge', 'Sign-in failed. Try again.');
  }
  if (ts > nowS + 5 || nowS - ts > CHALLENGE_TTL_S) {
    throw new ApiError(401, 'challenge_expired', 'Sign-in took too long. Try again.');
  }
  return (await makeChallenge(secret, ts, nonce)).server_id;
}
