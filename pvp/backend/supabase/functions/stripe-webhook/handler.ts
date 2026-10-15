/**
 * `stripe-webhook` — Stripe tells us what happened to a payment.
 *
 * 1. The signature is checked over the raw body with STRIPE_WEBHOOK_SECRET; anything else is a
 *    400 and touches nothing.
 * 2. The event is handed to one SQL function that records the event id and acts in the same
 *    transaction, so delivery is idempotent: a duplicate is a no-op, and a failure rolls back
 *    so Stripe's retry starts clean.
 *
 * Events handled (subscribe the endpoint to exactly these — README, Stripe setup):
 *   checkout.session.completed                grant when payment_status is paid
 *   checkout.session.async_payment_succeeded  grant
 *   checkout.session.expired                  mark the pending purchase expired
 *   charge.refunded                           full refund: revoke
 *   charge.dispute.created                    revoke
 *
 * A permanent failure (metadata that names no player) is answered 200 and logged rather than
 * 4xx/5xx, because Stripe retries non-2xx answers for three days and the answer will not change.
 * A transient one (database unreachable) is a 500, so Stripe does retry.
 */

import { ApiError, json, requireMethod, errorResponse, normalizeUuid } from '../_shared/http.ts';
import { verifyStripeSignature } from '../_shared/stripe.ts';
import type { Deps } from '../_shared/deps.ts';

interface StripeEvent {
  id: string;
  type: string;
  data: { object: Record<string, unknown> };
}

const s = (v: unknown): string | null => (typeof v === 'string' && v ? v : null);
const n = (v: unknown): number | null => (typeof v === 'number' && Number.isFinite(v) ? v : null);

/** The PaymentIntent id, whether Stripe sent it expanded or as an id. */
function paymentIntentOf(v: unknown): string | null {
  if (typeof v === 'string') return v;
  if (v && typeof v === 'object' && typeof (v as { id?: unknown }).id === 'string') return (v as { id: string }).id;
  return null;
}

export function handler(deps: Deps) {
  const { db } = deps;
  return async (req: Request): Promise<Response> => {
    try {
      requireMethod(req, 'POST');
      const secret = deps.env('STRIPE_WEBHOOK_SECRET') ?? '';
      const raw = await req.text();
      if (raw.length > 512 * 1024) throw new ApiError(413, 'too_large', 'Payload too large.');
      const ok = await verifyStripeSignature(raw, req.headers.get('stripe-signature'), secret, Math.floor(deps.nowMs() / 1000));
      if (!ok) throw new ApiError(400, 'bad_signature', 'Signature verification failed.');

      let event: StripeEvent;
      try {
        event = JSON.parse(raw) as StripeEvent;
      } catch {
        throw new ApiError(400, 'bad_json', 'Not JSON.');
      }
      if (!s(event?.id) || !s(event?.type) || !event.data?.object) {
        throw new ApiError(400, 'bad_event', 'Not a Stripe event.');
      }
      const obj = event.data.object;

      try {
        const result = await dispatch(deps, event, obj);
        return json({ received: true, result });
      } catch (e) {
        if (e instanceof ApiError && e.status >= 400 && e.status < 500) {
          console.error('stripe event rejected', event.id, event.type, e.code, e.message);
          return json({ received: true, result: 'rejected', error: e.code });
        }
        throw e;
      }
    } catch (e) {
      return errorResponse(e);
    }
  };

  async function dispatch(_deps: Deps, event: StripeEvent, obj: Record<string, unknown>): Promise<string> {
    switch (event.type) {
      case 'checkout.session.completed':
      case 'checkout.session.async_payment_succeeded': {
        const meta = (obj.metadata ?? {}) as Record<string, unknown>;
        const status = s(obj.payment_status);
        const paid = event.type === 'checkout.session.async_payment_succeeded' || status === 'paid' || status === 'no_payment_required';
        return db.rpc<string>('stripe_checkout_completed', {
          event_id: event.id,
          event_type: event.type,
          session_id: s(obj.id),
          p: normalizeUuid(meta.uuid) ?? normalizeUuid(obj.client_reference_id),
          item_id: s(meta.item),
          payment_intent: paymentIntentOf(obj.payment_intent),
          amount: n(obj.amount_total),
          currency: s(obj.currency),
          paid,
        });
      }
      case 'checkout.session.expired':
        return db.rpc<string>('stripe_checkout_expired', { event_id: event.id, event_type: event.type, session_id: s(obj.id) });
      case 'charge.refunded':
        // Partial refunds keep the item: one cosmetic cannot be part-refunded.
        if (obj.refunded !== true) {
          return db.rpc<string>('stripe_event_seen', { event_id: event.id, event_type: event.type });
        }
        return db.rpc<string>('stripe_refunded', { event_id: event.id, event_type: event.type, payment_intent: paymentIntentOf(obj.payment_intent) });
      case 'charge.dispute.created':
        return db.rpc<string>('stripe_refunded', { event_id: event.id, event_type: event.type, payment_intent: paymentIntentOf(obj.payment_intent) });
      default:
        return 'ignored';
    }
  }
}
