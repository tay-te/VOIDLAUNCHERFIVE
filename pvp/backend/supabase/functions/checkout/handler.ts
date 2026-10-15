/**
 * `checkout` — a Stripe Checkout Session for one item.
 *
 *   {"item":"void-cape-rift"} → {"url":"https://checkout.stripe.com/...","session_id":"cs_..."}
 *
 * The launcher opens `url` in the system browser. Nothing is granted here: the entitlement
 * arrives through `stripe-webhook` once Stripe says the payment succeeded, and the launcher
 * learns of it on its `user:<uuid>` channel (and by polling, as a fallback).
 */

import { ApiError, guarded, isItemId, json, readJson, requireMethod } from '../_shared/http.ts';
import { rateLimit, requireUser, LIMITS } from '../_shared/guard.ts';
import { createCheckoutSession, type CheckoutItem } from '../_shared/stripe.ts';
import type { Deps } from '../_shared/deps.ts';

export function handler(deps: Deps) {
  const { db } = deps;
  return guarded(async (req) => {
    requireMethod(req, 'POST');
    const caller = await requireUser(req, db);
    await rateLimit(db, `checkout:${caller.uuid}`, LIMITS.checkout.max, LIMITS.checkout.windowS);
    const body = await readJson(req, 2 * 1024);
    const item = body.item;
    if (!isItemId(item)) throw new ApiError(400, 'bad_item', 'That is not an item id.');

    const secret = deps.env('STRIPE_SECRET_KEY');
    const success = deps.env('CHECKOUT_SUCCESS_URL');
    const cancel = deps.env('CHECKOUT_CANCEL_URL');
    if (!secret || !success || !cancel) {
      console.error('checkout is not configured: set STRIPE_SECRET_KEY, CHECKOUT_SUCCESS_URL, CHECKOUT_CANCEL_URL');
      throw new ApiError(503, 'payments_unavailable', 'The Store isn’t open yet. Nothing was charged.');
    }
    const currency = (deps.env('STRIPE_CURRENCY') || 'usd').toLowerCase();

    const info = await db.rpc<CheckoutItem>('checkout_begin', { p: caller.uuid, item_id: item });
    const session = await createCheckoutSession(secret, caller.uuid, info, { success, cancel }, deps.fetch, deps.nowMs(), currency);
    await db.rpc('checkout_record', {
      p: caller.uuid,
      item_id: item,
      session_id: session.id,
      amount: session.amount_total ?? info.price_cents,
      currency: session.currency ?? currency,
    });
    return json({ url: session.url, session_id: session.id });
  });
}
