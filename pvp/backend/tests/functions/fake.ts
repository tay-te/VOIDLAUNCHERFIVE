/**
 * A fake outside world for the function tests: one `fetch` that routes by method + URL prefix
 * to canned handlers and records every call, plus a ready-made Deps.
 *
 * The routes stand in for GoTrue, PostgREST, Mojang and Stripe at the HTTP level. They answer
 * the way the real services are documented to; the SQL behind PostgREST is tested for real in
 * tests/sql against the migrations.
 */

import { makeDeps, type Deps } from '../../supabase/functions/_shared/deps.ts';

export interface Call {
  method: string;
  url: string;
  headers: Record<string, string>;
  body: string;
}

export type Route = (call: Call) => Response | Promise<Response>;

export class FakeNet {
  routes: Array<[string, string, Route]> = [];
  calls: Call[] = [];

  on(method: string, urlPrefix: string, route: Route): this {
    this.routes.unshift([method, urlPrefix, route]);
    return this;
  }

  /** Reply with JSON. */
  json(method: string, urlPrefix: string, body: unknown, status = 200): this {
    return this.on(method, urlPrefix, () => new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } }));
  }

  /** Answer `POST /rest/v1/rpc/<fn>` by function name. */
  rpc(fn: string, answer: (args: Record<string, unknown>, call: Call) => unknown): this {
    return this.on('POST', `${SUPABASE_URL}/rest/v1/rpc/${fn}`, async (call) => {
      const out = await answer(JSON.parse(call.body || '{}'), call);
      if (out instanceof Response) return out;
      return new Response(JSON.stringify(out ?? null), { status: 200, headers: { 'content-type': 'application/json' } });
    });
  }

  callsTo(urlPrefix: string): Call[] {
    return this.calls.filter((c) => c.url.startsWith(urlPrefix));
  }

  fetch = async (input: string | URL | Request, init: RequestInit = {}): Promise<Response> => {
    const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
    const method = (init.method ?? 'GET').toUpperCase();
    const headers: Record<string, string> = {};
    new Headers(init.headers ?? {}).forEach((v, k) => (headers[k] = v));
    const call: Call = { method, url, headers, body: typeof init.body === 'string' ? init.body : '' };
    this.calls.push(call);
    for (const [m, prefix, route] of this.routes) {
      if (m === method && url.startsWith(prefix)) return route(call);
    }
    return new Response(JSON.stringify({ message: `no fake route for ${method} ${url}` }), { status: 599 });
  };
}

export const SUPABASE_URL = 'https://test.supabase.co';
export const ANON = 'anon-key';
export const SERVICE = 'service-key';
export const KESTREL = '069a79f4-44e9-4726-a5be-fca90e38aaf5';
export const MOSS = '853c80ef-3c37-49fd-aa49-938b674adae6';

/** PostgREST's answer to one of our `raise_api` errors. */
export function pgError(status: number, code: string, details: string): Response {
  return new Response(JSON.stringify({ code: `PT${status}`, message: code, details, hint: null }), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

export function deps(net: FakeNet, env: Record<string, string> = {}, nowMs = 1_760_000_000_000): Deps {
  const all: Record<string, string> = {
    SUPABASE_URL,
    SUPABASE_ANON_KEY: ANON,
    SUPABASE_SERVICE_ROLE_KEY: SERVICE,
    ...env,
  };
  return makeDeps((k) => all[k], net.fetch, () => nowMs);
}

/** A signed-in player: GoTrue answers /auth/v1/user for `token`, and rate limits pass. */
export function signedIn(net: FakeNet, token = 'player-token', uuid = KESTREL): FakeNet {
  net.on('GET', `${SUPABASE_URL}/auth/v1/user`, (call) =>
    call.headers.authorization === `Bearer ${token}`
      ? new Response(JSON.stringify({ id: uuid, email: 'x@players.void.invalid' }), { status: 200 })
      : new Response(JSON.stringify({ msg: 'invalid JWT' }), { status: 401 }),
  );
  net.rpc('rate_limit_hit', () => true);
  return net;
}

export function req(method: string, path: string, body?: unknown, headers: Record<string, string> = {}): Request {
  return new Request(`${SUPABASE_URL}/functions/v1/${path}`, {
    method,
    headers: { 'content-type': 'application/json', authorization: 'Bearer player-token', apikey: ANON, ...headers },
    body: body === undefined ? undefined : typeof body === 'string' ? body : JSON.stringify(body),
  });
}

export async function read(res: Response): Promise<{ status: number; body: any; headers: Headers }> {
  const text = await res.text();
  return { status: res.status, body: text ? JSON.parse(text) : null, headers: res.headers };
}
