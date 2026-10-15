/**
 * `auth-minecraft` — prove a Minecraft identity, get a one-time Supabase login.
 *
 *   {"mode":"challenge"}                          → {"server_id","ts","nonce"}
 *   {"mode":"join","name","ts","nonce"}           → {"player","token_hash","verification_type"}
 *   {"mode":"token","access_token"}               → same
 *
 * The launcher redeems `token_hash` at `/auth/v1/verify` for a normal session whose user id is
 * the player's MC UUID. See pvp/docs/online-contract.md, "Auth", for why it is shaped this way.
 */

import { ApiError, clientIp, guarded, json, readJson, requireMethod, str } from '../_shared/http.ts';
import { rateLimit, LIMITS } from '../_shared/guard.ts';
import { hasJoined, makeChallenge, profileFromAccessToken, redeemChallenge, type McProfile } from '../_shared/mojang.ts';
import type { Deps } from '../_shared/deps.ts';

export const DEFAULT_EMAIL_DOMAIN = 'players.void.invalid';

export function playerEmail(uuid: string, domain = DEFAULT_EMAIL_DOMAIN): string {
  return `${uuid.replace(/-/g, '')}@${domain}`;
}

export function handler(deps: Deps) {
  const { db } = deps;
  return guarded(async (req) => {
    requireMethod(req, 'POST');
    const body = await readJson(req, 8 * 1024);
    const mode = str(body, 'mode') ?? (typeof body.access_token === 'string' ? 'token' : '');
    const nowS = Math.floor(deps.nowMs() / 1000);
    const secret = deps.env('AUTH_CHALLENGE_SECRET') ?? '';

    await rateLimit(db, `auth:ip:${clientIp(req)}`, LIMITS.authPerIp.max, LIMITS.authPerIp.windowS);

    if (mode === 'challenge') {
      return json(await makeChallenge(secret, nowS));
    }

    let profile: McProfile;
    if (mode === 'join') {
      const name = str(body, 'name') ?? '';
      if (!/^[A-Za-z0-9_]{1,16}$/.test(name)) {
        throw new ApiError(400, 'invalid_name', 'That is not a Minecraft name.');
      }
      await rateLimit(db, `auth:name:${name.toLowerCase()}`, LIMITS.authPerName.max, LIMITS.authPerName.windowS);
      const serverId = await redeemChallenge(secret, body.ts, body.nonce, nowS);
      // Single use: a challenge redeemed once cannot be redeemed again, even inside its TTL.
      const fresh = await db.rpc<boolean>('rate_limit_hit', { p_key: `auth:nonce:${body.nonce}`, p_max: 1, p_window_s: 600 });
      if (!fresh) throw new ApiError(401, 'challenge_used', 'Sign-in failed. Try again.');
      const joined = await hasJoined(name, serverId, deps.fetch);
      if (!joined) {
        throw new ApiError(401, 'not_joined', 'Minecraft did not confirm your sign-in. Try again.');
      }
      profile = joined;
    } else if (mode === 'token') {
      const token = str(body, 'access_token') ?? '';
      if (token.length < 16 || token.length > 4096) {
        throw new ApiError(400, 'bad_minecraft_token', 'Your Minecraft sign-in expired. Sign in again.');
      }
      profile = await profileFromAccessToken(token, deps.fetch);
    } else {
      throw new ApiError(400, 'bad_mode', 'mode must be challenge, join or token.');
    }

    const domain = deps.env('PLAYER_EMAIL_DOMAIN') || DEFAULT_EMAIL_DOMAIN;
    const user = await db.adminEnsureUser(profile.uuid, playerEmail(profile.uuid, domain), {
      provider: 'minecraft',
      providers: ['minecraft'],
    });
    if (user.id !== profile.uuid) {
      // GoTrue ignored the requested id. Every policy assumes auth.uid() = MC UUID, so stop here.
      console.error('auth user id mismatch', user.id, profile.uuid);
      throw new ApiError(500, 'misconfigured', 'Sign-in is not configured correctly on this server.');
    }
    await db.rpc('player_upsert', { p: profile.uuid, p_name: profile.name });
    const link = await db.adminGenerateLink(user.email || playerEmail(profile.uuid, domain));

    return json({ player: profile, token_hash: link.hashed_token, verification_type: link.verification_type });
  });
}
