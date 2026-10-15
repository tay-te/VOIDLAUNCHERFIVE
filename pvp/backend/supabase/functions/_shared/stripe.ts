/**
 * The two Stripe operations the backend needs, over plain `fetch` and WebCrypto: create a
 * Checkout Session, and verify a webhook signature. No SDK — see supabase.ts for why.
 */

import { ApiError } from './http.ts';
import type { Fetch } from './supabase.ts';

export const STRIPE_API = 'https://api.stripe.com/v1';

/** Stripe's form encoding: nested objects and arrays as `a[b][0][c]=v`. */
export function formEncode(params: Record<string, unknown>): string {
  const out: string[] = [];
  const walk = (prefix: string, value: unknown): void => {
    if (value === undefined || value === null) return;
    if (Array.isArray(value)) {
      value.forEach((v, i) => walk(`${prefix}[${i}]`, v));
    } else if (typeof value === 'object') {
      for (const [k, v] of Object.entries(value as Record<string, unknown>)) walk(prefix ? `${prefix}[${k}]` : k, v);
    } else {
      out.push(`${encodeURIComponent(prefix)}=${encodeURIComponent(String(value))}`);
    }
  };
  walk('', params);
  return out.join('&');
}

export interface CheckoutItem {
  item: string;
  name: string;
  kind: string;
  price_cents: number;
  stripe_price_id: string | null;
}

export interface CheckoutUrls {
  success: string;
  cancel: string;
}

export interface CheckoutSession {
  id: string;
  url: string;
  amount_total: number | null;
  currency: string | null;
}

/**
 * One item, one session. `metadata` carries `{uuid, item}` on both the session and the
 * PaymentIntent (the latter so a refund event can be traced back), and `client_reference_id`
 * is the player too. A catalog row with `stripe_price_id` charges that Price; otherwise the
 * price comes from the catalog through `price_data`, so the database is the price list.
 */
export async function createCheckoutSession(
  secretKey: string,
  player: string,
  item: CheckoutItem,
  urls: CheckoutUrls,
  fetchImpl: Fetch,
  nowMs: number,
  currency = 'usd',
): Promise<CheckoutSession> {
  // Same player, same item, same minute: a double-click gets the same session back. Every
  // parameter must then be identical too (Stripe refuses a reused key with different ones),
  // so the expiry is derived from the minute, not from the second.
  const minute = Math.floor(nowMs / 60_000);
  const idem = `checkout:${player}:${item.item}:${minute}`;
  const lineItem = item.stripe_price_id
    ? { price: item.stripe_price_id, quantity: 1 }
    : {
        quantity: 1,
        price_data: {
          currency,
          unit_amount: item.price_cents,
          product_data: {
            name: item.name,
            description: `VOID cosmetic (${item.kind}). Cosmetic only — no gameplay advantage.`,
            metadata: { item: item.item },
          },
        },
      };
  const metadata = { uuid: player, item: item.item };
  const body = formEncode({
    mode: 'payment',
    success_url: urls.success,
    cancel_url: urls.cancel,
    client_reference_id: player,
    line_items: [lineItem],
    metadata,
    payment_intent_data: { metadata },
    // Just over Stripe's 30-minute minimum: an abandoned session expires and marks the
    // pending purchase expired.
    expires_at: (minute + 31) * 60,
  });
  const res = await fetchImpl(`${STRIPE_API}/checkout/sessions`, {
    method: 'POST',
    headers: {
      authorization: `Bearer ${secretKey}`,
      'content-type': 'application/x-www-form-urlencoded',
      'idempotency-key': idem,
      'stripe-version': '2024-06-20',
    },
    body,
  });
  const json = (await res.json().catch(() => ({}))) as Record<string, unknown>;
  if (!res.ok || typeof json.id !== 'string' || typeof json.url !== 'string') {
    console.error('stripe checkout failed', res.status, JSON.stringify(json).slice(0, 500));
    throw new ApiError(502, 'payments_unavailable', 'Checkout is unavailable right now. Nothing was charged.');
  }
  return {
    id: json.id,
    url: json.url,
    amount_total: typeof json.amount_total === 'number' ? json.amount_total : null,
    currency: typeof json.currency === 'string' ? json.currency : null,
  };
}

// ---------------------------------------------------------------------------------------------
// webhook signatures — https://docs.stripe.com/webhooks#verify-manually

const enc = new TextEncoder();

function timingSafeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

/**
 * Verify `Stripe-Signature: t=<ts>,v1=<hex>[,v1=<hex>...]` over the RAW body. Any matching v1
 * passes (Stripe sends several while a secret is being rolled). Rejects a timestamp more than
 * `toleranceS` from now, which is what stops a captured event being replayed later.
 */
export async function verifyStripeSignature(
  payload: string,
  header: string | null,
  secret: string,
  nowS: number,
  toleranceS = 300,
): Promise<boolean> {
  if (!header || !secret) return false;
  let ts: number | null = null;
  const sigs: string[] = [];
  for (const part of header.split(',')) {
    const [k, v] = part.split('=', 2).map((s) => s.trim());
    if (k === 't' && v && /^\d+$/.test(v)) ts = Number(v);
    else if (k === 'v1' && v) sigs.push(v.toLowerCase());
  }
  if (ts === null || sigs.length === 0) return false;
  if (Math.abs(nowS - ts) > toleranceS) return false;
  const key = await crypto.subtle.importKey('raw', enc.encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  const mac = new Uint8Array(await crypto.subtle.sign('HMAC', key, enc.encode(`${ts}.${payload}`)));
  const expected = [...mac].map((b) => b.toString(16).padStart(2, '0')).join('');
  return sigs.some((s) => timingSafeEqual(s, expected));
}

/** Test helper and documentation: the header Stripe would send for `payload`. */
export async function signStripePayload(payload: string, secret: string, ts: number): Promise<string> {
  const key = await crypto.subtle.importKey('raw', enc.encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  const mac = new Uint8Array(await crypto.subtle.sign('HMAC', key, enc.encode(`${ts}.${payload}`)));
  return `t=${ts},v1=${[...mac].map((b) => b.toString(16).padStart(2, '0')).join('')}`;
}
