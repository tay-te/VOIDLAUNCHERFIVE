/**
 * `equip` — change what the caller wears.
 *
 *   {"slot":"cape","id":"void-cape-rift"}   put an item in a slot (null takes it off)
 *   {"emotes":["emote-wave", ...]}          replace the wheel (≤ 8, in order)
 *   {"tints":{"cape":"#9f8bff"}}            recolour tintable items (null clears)
 *   → {"equipped": EquippedSet}
 *
 * Shapes are checked here so a malformed request never reaches the database; ownership and
 * slot rules are checked by the database (`equipped_validate`), so they hold for every writer.
 */

import { ApiError, guarded, isItemId, isSlot, json, readJson, requireMethod } from '../_shared/http.ts';
import { rateLimit, requireUser, LIMITS } from '../_shared/guard.ts';
import type { Deps } from '../_shared/deps.ts';

export function handler(deps: Deps) {
  const { db } = deps;
  return guarded(async (req) => {
    requireMethod(req, 'POST');
    const caller = await requireUser(req, db);
    await rateLimit(db, `equip:${caller.uuid}`, LIMITS.equip.max, LIMITS.equip.windowS);
    const body = await readJson(req, 4 * 1024);
    const p = caller.uuid;

    if ('slot' in body) {
      const { slot, id } = body;
      if (!isSlot(slot)) throw new ApiError(400, 'bad_slot', 'That is not a slot.');
      if (id !== null && id !== undefined && !isItemId(id)) throw new ApiError(400, 'bad_item', 'That is not an item id.');
      const equipped = await db.rpc('equip_slot', { p, slot, item: id ?? null });
      return json({ equipped });
    }

    if ('emotes' in body) {
      const ids = body.emotes;
      if (!Array.isArray(ids) || ids.length > 8 || !ids.every(isItemId)) {
        throw new ApiError(400, 'bad_emotes', 'emotes must be up to eight emote ids.');
      }
      const equipped = await db.rpc('set_emotes', { p, ids });
      return json({ equipped });
    }

    if ('tints' in body) {
      const tints = body.tints;
      if (tints === null || typeof tints !== 'object' || Array.isArray(tints)) {
        throw new ApiError(400, 'bad_tints', 'tints must be an object of slot to colour.');
      }
      const entries = Object.entries(tints as Record<string, unknown>);
      if (entries.length === 0 || entries.length > 7) throw new ApiError(400, 'bad_tints', 'Name at least one slot.');
      let equipped: unknown = null;
      for (const [slot, color] of entries) {
        if (!isSlot(slot)) throw new ApiError(400, 'bad_slot', `${slot} is not a slot.`);
        if (color !== null && (typeof color !== 'string' || !/^#[0-9a-fA-F]{6}$/.test(color))) {
          throw new ApiError(400, 'bad_color', 'A tint is a colour like #9f8bff.');
        }
        equipped = await db.rpc('set_tint', { p, slot, color });
      }
      return json({ equipped });
    }

    throw new ApiError(400, 'bad_request', 'Send slot+id, emotes, or tints.');
  });
}
