/**
 * `friends` — the friends list and requests. Every action answers with the whole updated list.
 *
 *   {"action":"list"}
 *   {"action":"request","name":"Moss"}           sending one back to someone who asked is accepting
 *   {"action":"accept"|"decline","uuid":"..."}   a request they sent you
 *   {"action":"cancel","uuid":"..."}             a request you sent
 *   {"action":"remove","uuid":"..."}             a friend
 *
 * The other player hears about request / accept / cancel / remove on their `user:<uuid>`
 * channel; a decline is deliberately silent.
 */

import { ApiError, guarded, json, normalizeUuid, readJson, requireMethod, str } from '../_shared/http.ts';
import { rateLimit, requireUser, LIMITS } from '../_shared/guard.ts';
import type { Deps } from '../_shared/deps.ts';

const BY_UUID: Record<string, string> = {
  accept: 'friend_accept',
  decline: 'friend_decline',
  cancel: 'friend_cancel',
  remove: 'friend_remove',
};

export function handler(deps: Deps) {
  const { db } = deps;
  return guarded(async (req) => {
    requireMethod(req, 'POST');
    const caller = await requireUser(req, db);
    await rateLimit(db, `friends:${caller.uuid}`, LIMITS.friends.max, LIMITS.friends.windowS);
    const body = await readJson(req, 2 * 1024);
    const action = str(body, 'action') ?? 'list';

    if (action === 'list') {
      return json(await db.rpc('friends_list', { p: caller.uuid }));
    }
    if (action === 'request') {
      const name = (str(body, 'name') ?? '').trim();
      if (!/^[A-Za-z0-9_]{3,16}$/.test(name)) {
        throw new ApiError(400, 'invalid_name', 'Usernames are 3–16 letters, numbers or underscores.');
      }
      return json(await db.rpc('friend_request', { p: caller.uuid, target_name: name }));
    }
    const fn = BY_UUID[action];
    if (fn) {
      const other = normalizeUuid(body.uuid);
      if (!other) throw new ApiError(400, 'bad_uuid', 'That is not a player id.');
      return json(await db.rpc(fn, { p: caller.uuid, other }));
    }
    throw new ApiError(400, 'bad_action', 'action must be list, request, accept, decline, cancel or remove.');
  });
}
