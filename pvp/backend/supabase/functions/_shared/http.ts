/**
 * Responses, errors and request parsing shared by every function.
 *
 * Runtime-neutral on purpose: only web-standard `Request`/`Response`/`crypto`, so the same
 * code runs in Supabase's Deno edge runtime and under `node --test` (Node strips the types).
 * That is also why there are no TypeScript-only constructs here (enums, parameter
 * properties, namespaces) — Node's type stripping does not support them.
 *
 * Error shape, everywhere: `{"error":"<machine code>","message":"<sentence a player can read>"}`
 * with a matching HTTP status. `429` carries `Retry-After` in seconds.
 */

export class ApiError extends Error {
  status: number;
  code: string;
  headers: Record<string, string>;

  constructor(status: number, code: string, message: string, headers: Record<string, string> = {}) {
    super(message);
    this.status = status;
    this.code = code;
    this.headers = headers;
  }
}

const SECURITY_HEADERS: Record<string, string> = {
  'content-type': 'application/json; charset=utf-8',
  'x-content-type-options': 'nosniff',
};

export function json(body: unknown, status = 200, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...SECURITY_HEADERS, 'cache-control': 'no-store', ...headers },
  });
}

export function errorResponse(e: unknown): Response {
  if (e instanceof ApiError) {
    return json({ error: e.code, message: e.message }, e.status, e.headers);
  }
  // Never leak internals: log the detail, answer with a sentence.
  console.error('unhandled error', e);
  return json({ error: 'internal', message: 'Something went wrong on our side. Try again in a moment.' }, 500);
}

/** Wrap a handler so a thrown ApiError (or anything else) becomes the error shape. */
export function guarded(fn: (req: Request) => Promise<Response>): (req: Request) => Promise<Response> {
  return async (req) => {
    try {
      return await fn(req);
    } catch (e) {
      return errorResponse(e);
    }
  };
}

export function requireMethod(req: Request, ...methods: string[]): void {
  if (!methods.includes(req.method)) {
    throw new ApiError(405, 'method_not_allowed', `Use ${methods.join(' or ')}.`, { allow: methods.join(', ') });
  }
}

/** Parse a JSON object body of at most `maxBytes`. */
export async function readJson(req: Request, maxBytes = 16 * 1024): Promise<Record<string, unknown>> {
  const text = await req.text();
  if (text.length > maxBytes) {
    throw new ApiError(413, 'too_large', 'That request is too large.');
  }
  if (text.trim() === '') return {};
  let value: unknown;
  try {
    value = JSON.parse(text);
  } catch {
    throw new ApiError(400, 'bad_json', 'The request body is not valid JSON.');
  }
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    throw new ApiError(400, 'bad_json', 'The request body must be a JSON object.');
  }
  return value as Record<string, unknown>;
}

/** The caller's IP as the edge gateway reports it, for per-IP rate limits. */
export function clientIp(req: Request): string {
  const fwd = req.headers.get('x-forwarded-for');
  if (fwd) return fwd.split(',')[0].trim();
  return req.headers.get('cf-connecting-ip') ?? req.headers.get('x-real-ip') ?? 'unknown';
}

export function bearer(req: Request): string | null {
  const h = req.headers.get('authorization') ?? '';
  const m = /^Bearer\s+(.+)$/i.exec(h);
  return m ? m[1].trim() : null;
}

export function str(body: Record<string, unknown>, key: string): string | undefined {
  const v = body[key];
  return typeof v === 'string' ? v : undefined;
}

export const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
export const UNDASHED_RE = /^[0-9a-fA-F]{32}$/;

/** Dashed lowercase UUID from either form, or null. */
export function normalizeUuid(s: unknown): string | null {
  if (typeof s !== 'string') return null;
  const t = s.trim().toLowerCase();
  if (UUID_RE.test(t)) return t;
  if (UNDASHED_RE.test(t)) {
    return `${t.slice(0, 8)}-${t.slice(8, 12)}-${t.slice(12, 16)}-${t.slice(16, 20)}-${t.slice(20)}`;
  }
  return null;
}

export const ITEM_ID_RE = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
export const SLOTS = ['cape', 'back', 'head', 'face', 'halo', 'trail', 'badge'] as const;
export type Slot = (typeof SLOTS)[number];
export const isSlot = (s: unknown): s is Slot => typeof s === 'string' && (SLOTS as readonly string[]).includes(s);
export const isItemId = (s: unknown): s is string => typeof s === 'string' && s.length <= 64 && ITEM_ID_RE.test(s);
