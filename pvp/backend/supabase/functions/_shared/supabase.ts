/**
 * A deliberately tiny Supabase client: PostgREST RPC, GoTrue's user and admin endpoints.
 *
 * Why not `@supabase/supabase-js`: these functions need five endpoints, and a fetch-only
 * client has no import to resolve at deploy time, runs unchanged under `node --test` with a
 * fake `fetch`, and makes every request the functions send visible in one file.
 *
 * Keys. Supabase has two key systems in the field: the legacy JWT `anon`/`service_role` keys
 * and the newer `sb_publishable_…`/`sb_secret_…` keys. A legacy key goes in both `apikey` and
 * `Authorization: Bearer`; a new secret key goes in `apikey` only (the gateway rejects it as a
 * bearer token and mints the service-role JWT itself). `serviceHeaders` handles both.
 */

import { ApiError } from './http.ts';

export interface SupabaseEnv {
  url: string;
  anonKey: string;
  serviceKey: string;
}

export type Fetch = (input: string | URL | Request, init?: RequestInit) => Promise<Response>;

export interface AuthUser {
  id: string;
  email?: string;
  banned_until?: string | null;
}

export interface GeneratedLink {
  hashed_token: string;
  verification_type: string;
}

/** Map a PostgREST error (`{code,message,details,hint}`) to ApiError. */
export async function postgrestError(res: Response): Promise<ApiError> {
  let body: Record<string, unknown> = {};
  try {
    body = (await res.json()) as Record<string, unknown>;
  } catch {
    /* empty or not JSON */
  }
  const code = typeof body.code === 'string' ? body.code : '';
  // Our own raises: SQLSTATE PT<status>, message = machine code, details = sentence.
  if (/^PT\d{3}$/.test(code)) {
    return new ApiError(
      Number(code.slice(2)),
      typeof body.message === 'string' ? body.message : 'error',
      typeof body.details === 'string' ? body.details : 'Something went wrong.',
    );
  }
  console.error('postgrest error', res.status, body);
  return new ApiError(502, 'database', 'Something went wrong on our side. Try again in a moment.');
}

export class Supabase {
  readonly env: SupabaseEnv;
  readonly fetch: Fetch;

  constructor(env: SupabaseEnv, fetchImpl: Fetch) {
    this.env = env;
    this.fetch = fetchImpl;
  }

  serviceHeaders(): Record<string, string> {
    const k = this.env.serviceKey;
    return k.startsWith('sb_') ? { apikey: k } : { apikey: k, authorization: `Bearer ${k}` };
  }

  private userHeaders(token: string): Record<string, string> {
    return { apikey: this.env.anonKey, authorization: `Bearer ${token}` };
  }

  /** Call a Postgres function as the service role. */
  async rpc<T>(fn: string, args: Record<string, unknown> = {}): Promise<T> {
    return this.rpcWith<T>(fn, args, this.serviceHeaders());
  }

  /** Call a Postgres function as the player whose access token this is (RLS applies). */
  async rpcAsUser<T>(fn: string, args: Record<string, unknown>, token: string): Promise<T> {
    return this.rpcWith<T>(fn, args, this.userHeaders(token));
  }

  private async rpcWith<T>(fn: string, args: Record<string, unknown>, headers: Record<string, string>): Promise<T> {
    const res = await this.fetch(`${this.env.url}/rest/v1/rpc/${fn}`, {
      method: 'POST',
      headers: { ...headers, 'content-type': 'application/json', accept: 'application/json' },
      body: JSON.stringify(args),
    });
    if (!res.ok) throw await postgrestError(res);
    const text = await res.text();
    return (text ? JSON.parse(text) : null) as T;
  }

  /**
   * The user an access token belongs to — GoTrue validates signature, expiry and revocation.
   * Null for a bad or expired token.
   */
  async getUser(token: string): Promise<AuthUser | null> {
    const res = await this.fetch(`${this.env.url}/auth/v1/user`, { headers: this.userHeaders(token) });
    if (res.status === 401 || res.status === 403) return null;
    if (!res.ok) {
      console.error('auth/v1/user failed', res.status);
      throw new ApiError(502, 'auth_unavailable', 'Sign-in is unavailable right now. Try again in a moment.');
    }
    return (await res.json()) as AuthUser;
  }

  /** Admin: fetch a user by id, or null. */
  async adminGetUser(id: string): Promise<AuthUser | null> {
    const res = await this.fetch(`${this.env.url}/auth/v1/admin/users/${id}`, { headers: this.serviceHeaders() });
    if (res.status === 404) return null;
    if (!res.ok) throw await authError(res, 'admin get user');
    return (await res.json()) as AuthUser;
  }

  /**
   * Admin: create the auth user for a player, with the given id. Idempotent: an existing user
   * with that id is returned as is.
   */
  async adminEnsureUser(id: string, email: string, metadata: Record<string, unknown>): Promise<AuthUser> {
    const existing = await this.adminGetUser(id);
    if (existing) return existing;
    const res = await this.fetch(`${this.env.url}/auth/v1/admin/users`, {
      method: 'POST',
      headers: { ...this.serviceHeaders(), 'content-type': 'application/json' },
      body: JSON.stringify({ id, email, email_confirm: true, app_metadata: metadata }),
    });
    if (res.ok) return (await res.json()) as AuthUser;
    // Lost a race with a concurrent sign-in for the same player: the user exists now.
    if (res.status === 422 || res.status === 409) {
      const raced = await this.adminGetUser(id);
      if (raced) return raced;
    }
    throw await authError(res, 'admin create user');
  }

  /** Admin: a one-time magic-link token for `email`, never mailed. */
  async adminGenerateLink(email: string): Promise<GeneratedLink> {
    const res = await this.fetch(`${this.env.url}/auth/v1/admin/generate_link`, {
      method: 'POST',
      headers: { ...this.serviceHeaders(), 'content-type': 'application/json' },
      body: JSON.stringify({ type: 'magiclink', email }),
    });
    if (!res.ok) throw await authError(res, 'admin generate link');
    const body = (await res.json()) as Record<string, unknown>;
    // GoTrue answers with the user fields plus the link fields at top level; supabase-js
    // reshapes it into `properties`. Accept both.
    const props = (body.properties as Record<string, unknown> | undefined) ?? body;
    const hashed = props.hashed_token;
    if (typeof hashed !== 'string' || !hashed) {
      throw new ApiError(502, 'auth_unavailable', 'Sign-in is unavailable right now. Try again in a moment.');
    }
    return {
      hashed_token: hashed,
      verification_type: typeof props.verification_type === 'string' ? props.verification_type : 'magiclink',
    };
  }
}

async function authError(res: Response, what: string): Promise<ApiError> {
  let detail = '';
  try {
    detail = await res.text();
  } catch {
    /* nothing */
  }
  console.error(`${what} failed`, res.status, detail.slice(0, 500));
  return new ApiError(502, 'auth_unavailable', 'Sign-in is unavailable right now. Try again in a moment.');
}
