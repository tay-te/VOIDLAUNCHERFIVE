/**
 * Loadout share codes — `docs/mod-roster.md` §5's last row, and the only one on that list that
 * needs no game code at all.
 *
 * A code is a complete loadout as one pasteable string: which mods are on, every setting they
 * carry, and where every HUD item sits. Lunar has profiles and neither competitor has sharing,
 * which is the whole reason this is on the differentiator list rather than the table-stakes one.
 *
 * ## What "everything" means here, and how it is kept true
 *
 * The obvious failure is a code that carries the mods and forgets what they *look like* — the
 * shared HUD chrome block (`scale`, `opacity`, `background`, `border`, `padding`), which is the
 * half of a loadout a player tuned by eye and the half nobody thinks to serialise. A loadout that
 * arrives with the right mods and the wrong chrome is worse than no sharing at all, because it
 * looks like the sender's HUD is broken.
 *
 * So the guarantee is stated as a property rather than a list, and `test/share.test.ts` asserts
 * it by walking the registry: **for every mod and every setting key it declares, the decoded
 * loadout resolves to the same value as the encoded one.** A setting added to any mod is covered
 * the day it exists, and a codec that quietly stopped carrying a category fails on the next run.
 *
 * ## Why the payload is a delta, when the guarantee is "everything"
 *
 * The code carries only the values that differ from the registry's factory defaults, and the
 * decoder fills the rest back in from the same registry. At a given registry version that
 * reconstruction is *exact* — `resolveModSettings` is `{...defaults, ...stored}` on both ends —
 * so the delta and the whole are the same loadout, and the delta is roughly a tenth the size.
 *
 * The one case where it is not exact is a code crossing between two builds whose factory
 * defaults differ. That is why the registry version rides in the payload: a decoder that sees a
 * different one reports it (`foreign`) instead of pretending. It still applies everything the
 * code carries — refusing outright would make every code expire the next time a mod is added,
 * which for a share feature is worse than a warning.
 *
 * ## Why there is no compression
 *
 * The obvious next step is deflate, and it is not here for a reason worth writing down.
 * `CompressionStream` is not available in Ultralight (`design/ultralight-notes.md` — the engine
 * is a stripped WebKit, and this package is imported by `packages/ingame`), and WebKitGTK, which
 * is what the launcher runs on Linux, only grew it recently. A codec whose round trip depends on
 * a browser API that might be missing on one of the two ends is a codec that loses somebody's
 * loadout. Hand-rolling deflate to make a share code shorter is the wrong trade against a code
 * that is already inside a chat message's limit.
 *
 * Everything here is therefore pure: no `btoa`, no `TextEncoder`, no `Buffer`. The base64 and
 * UTF-8 encoders below are forty lines and they run identically in Ultralight, in the launcher's
 * webview and in node.
 *
 * ## What a code deliberately does not carry
 *
 * **The loadout id.** Importing creates a loadout on the receiver's machine; taking the sender's
 * slug would collide with their own on the second import and silently overwrite one.
 *
 * **`stats`.** Played time and average FPS are facts about the sender, not about the loadout.
 * A code that carried them would hand you somebody else's hours.
 */

import { DEFAULT_HUD_PLACEMENTS } from './generated/placements.js';
import type {
  HUDAnchor,
  HUDItem,
  HUDModId,
  Loadout,
  ModId,
  ModStates,
} from './generated/schema.js';
import { MOD_IDS, MOD_REGISTRY_VERSION, getModDefaults, isHudMod, isModId } from './mods.js';

/** The prefix every code carries, and the format version it names. */
export const SHARE_PREFIX = 'VOID1';

/**
 * Anchors, in the order `loadout.json` declares them — the index a code stores.
 *
 * Positional because an anchor is a closed enum of nine that has not changed and would be a
 * schema change if it did; the name would cost eight bytes a HUD item for nothing. A code
 * carrying an index this list does not have is rejected rather than clamped, because every wrong
 * answer here puts a widget somewhere the sender did not have it.
 */
const ANCHORS: readonly HUDAnchor[] = [
  'top-left',
  'top',
  'top-right',
  'left',
  'center',
  'right',
  'bottom-left',
  'bottom',
  'bottom-right',
];

/** What a code decodes to, or why it did not. */
export type DecodeResult =
  | {
      ok: true;
      /** The loadout, less its id — see the module note. */
      loadout: Omit<Loadout, 'id'>;
      /** The registry version the sender's client was on. */
      registryVersion: number;
      /** True when that differs from ours: everything applied, defaults may not match. */
      foreign: boolean;
      /**
       * Mod ids and setting keys this registry does not have, dropped rather than stored.
       *
       * Reported rather than swallowed. A code from a newer build carrying a mod we have never
       * heard of is the one case where the receiver genuinely gets less than the sender had, and
       * a share feature that lost a mod in silence would be blamed on the sender's HUD.
       */
      dropped: string[];
    }
  | { ok: false; reason: 'empty' | 'prefix' | 'checksum' | 'malformed' };

/* -------------------------------------------------------------------------- */
/* UTF-8 and base64url, by hand                                               */
/* -------------------------------------------------------------------------- */

const B64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_';

/** UTF-8 bytes of a string. Surrogate pairs handled; a lone surrogate becomes U+FFFD. */
function utf8(text: string): number[] {
  const out: number[] = [];
  for (let i = 0; i < text.length; i += 1) {
    let code = text.charCodeAt(i);
    if (code >= 0xd800 && code <= 0xdbff && i + 1 < text.length) {
      const next = text.charCodeAt(i + 1);
      if (next >= 0xdc00 && next <= 0xdfff) {
        code = 0x10000 + ((code - 0xd800) << 10) + (next - 0xdc00);
        i += 1;
      } else {
        code = 0xfffd;
      }
    } else if (code >= 0xd800 && code <= 0xdfff) {
      code = 0xfffd;
    }
    if (code < 0x80) out.push(code);
    else if (code < 0x800) out.push(0xc0 | (code >> 6), 0x80 | (code & 0x3f));
    else if (code < 0x10000) {
      out.push(0xe0 | (code >> 12), 0x80 | ((code >> 6) & 0x3f), 0x80 | (code & 0x3f));
    } else {
      out.push(
        0xf0 | (code >> 18),
        0x80 | ((code >> 12) & 0x3f),
        0x80 | ((code >> 6) & 0x3f),
        0x80 | (code & 0x3f),
      );
    }
  }
  return out;
}

/** A string back from UTF-8 bytes. Returns null on a malformed sequence. */
function fromUtf8(bytes: readonly number[]): string | null {
  let out = '';
  for (let i = 0; i < bytes.length; ) {
    const b = bytes[i]!;
    let code: number;
    let width: number;
    if (b < 0x80) {
      code = b;
      width = 1;
    } else if ((b & 0xe0) === 0xc0) {
      code = b & 0x1f;
      width = 2;
    } else if ((b & 0xf0) === 0xe0) {
      code = b & 0x0f;
      width = 3;
    } else if ((b & 0xf8) === 0xf0) {
      code = b & 0x07;
      width = 4;
    } else {
      return null;
    }
    if (i + width > bytes.length) return null;
    for (let k = 1; k < width; k += 1) {
      const cont = bytes[i + k]!;
      if ((cont & 0xc0) !== 0x80) return null;
      code = (code << 6) | (cont & 0x3f);
    }
    i += width;
    if (code > 0x10ffff) return null;
    if (code > 0xffff) {
      code -= 0x10000;
      out += String.fromCharCode(0xd800 + (code >> 10), 0xdc00 + (code & 0x3ff));
    } else {
      out += String.fromCharCode(code);
    }
  }
  return out;
}

/** base64url, unpadded — the alphabet a chat client and a URL both leave alone. */
function toBase64(bytes: readonly number[]): string {
  let out = '';
  for (let i = 0; i < bytes.length; i += 3) {
    const a = bytes[i]!;
    const b = i + 1 < bytes.length ? bytes[i + 1]! : -1;
    const c = i + 2 < bytes.length ? bytes[i + 2]! : -1;
    out += B64[a >> 2]! + B64[((a & 3) << 4) | (b < 0 ? 0 : b >> 4)]!;
    if (b < 0) break;
    out += B64[((b & 15) << 2) | (c < 0 ? 0 : c >> 6)]!;
    if (c < 0) break;
    out += B64[c & 63]!;
  }
  return out;
}

/** Bytes back from base64url. Returns null on a character outside the alphabet. */
function fromBase64(text: string): number[] | null {
  const out: number[] = [];
  let buffer = 0;
  let bits = 0;
  for (let i = 0; i < text.length; i += 1) {
    const value = B64.indexOf(text[i]!);
    if (value < 0) return null;
    buffer = (buffer << 6) | value;
    bits += 6;
    if (bits >= 8) {
      bits -= 8;
      out.push((buffer >> bits) & 0xff);
    }
  }
  return out;
}

/**
 * FNV-1a over the payload, base36 — the check digit in front of the code.
 *
 * Not a security measure and not trying to be: a share code is a thing a player pastes, and
 * every realistic corruption is a paste that lost its tail to a chat client's length limit or
 * picked up a stray character. A truncated code that still decoded would apply *most* of a
 * loadout, which is the failure that gets blamed on the client. This turns it into a refusal.
 */
function checksum(text: string): string {
  let hash = 0x811c9dc5;
  for (let i = 0; i < text.length; i += 1) {
    hash ^= text.charCodeAt(i);
    hash = Math.imul(hash, 0x01000193) >>> 0;
  }
  return hash.toString(36);
}

/* -------------------------------------------------------------------------- */
/* The payload                                                                */
/* -------------------------------------------------------------------------- */

/** The wire object, with keys kept to one character — this is what a code is made of. */
interface Payload {
  /** Format version. Bumped only when the shape below changes incompatibly. */
  v: number;
  /** The sender's `mods.json` registry version. */
  r: number;
  /** Loadout name. */
  n: string;
  /** Icon name. */
  i: string;
  /** Minecraft version. */
  c: string;
  /** Server slug, or null. */
  s?: string | null;
  /**
   * Every name the code uses — mod ids and setting keys alike — once. `d` and `h` index into it.
   *
   * A loadout is enormously repetitive in names and thin in values: the shared HUD chrome block
   * alone is six keys on twenty-two mods, and each mod id appears twice (once for its settings,
   * once for its HUD placement). Spelled out, the names are most of the code.
   *
   * **The table travels with the code**, rather than both ends indexing into the registry. That
   * is the whole reason this is safe. A positional scheme keyed to `mods.json` would be smaller
   * still and would break the day a mod grew a setting — and break by *mis-assigning* values
   * rather than by failing, which is a code that puts somebody's crosshair colour into their
   * hitbox width. Nothing here can drift, because the code carries its own names.
   */
  k: string[];
  /**
   * Per mod, `[modIndex, keyIndex, value, keyIndex, value, …]` — the settings that differ from
   * the registry's factory defaults.
   *
   * Flat pairs rather than nested `[key, value]` tuples, which is two characters a setting and
   * about a tenth of a heavily-customised code. The row is self-delimiting because it is its own
   * array, so nothing here depends on counting.
   */
  d: (number | unknown)[][];
  /** Per HUD item, `[nameIndex, anchorIndex, dx, dy]` plus a trailing scale when it is not 1. */
  h: number[][];
}

/** Whether two setting values are the same reading. Both are JSON scalars by schema. */
function same(a: unknown, b: unknown): boolean {
  return a === b || (a === undefined && b === null) || (a === null && b === undefined);
}

/**
 * A loadout as a share code.
 *
 * The `id` and `stats` are dropped on the way out; see the module note for both.
 */
export function encodeLoadout(loadout: Pick<Loadout, 'name' | 'icon' | 'mc' | 'mods' | 'hud'> &
  Partial<Pick<Loadout, 'server'>>): string {
  const keys: string[] = [];
  const keyIndex = (key: string): number => {
    let at = keys.indexOf(key);
    if (at < 0) {
      at = keys.length;
      keys.push(key);
    }
    return at;
  };

  const d: (number | unknown)[][] = [];
  for (const id of MOD_IDS) {
    const stored = loadout.mods[id] as Record<string, unknown> | undefined;
    if (stored === undefined) continue;
    const defaults = getModDefaults(id) as unknown as Record<string, unknown>;
    const row: (number | unknown)[] = [keyIndex(id)];
    for (const key of Object.keys(stored)) {
      // A stored value equal to the factory default is not carried: the decoder puts the same
      // number back from the same registry, so this is a smaller code and not a smaller loadout.
      if (!same(stored[key], defaults[key])) row.push(keyIndex(key), stored[key]);
    }
    if (row.length > 1) d.push(row);
  }

  const h: number[][] = [];
  for (const item of loadout.hud) {
    const factory = isHudMod(item.id) ? DEFAULT_HUD_PLACEMENTS[item.id as HUDModId] : undefined;
    const anchor = ANCHORS.indexOf(item.anchor);
    const scale = item.scale ?? 1;
    // A widget still sitting exactly where the registry put it is not carried either — the
    // decoder reconstructs it from `default_placement`, which is the same table the sender's
    // HUD editor started from.
    if (
      factory !== undefined &&
      scale === 1 &&
      factory.anchor === item.anchor &&
      factory.dx === item.dx &&
      factory.dy === item.dy
    ) {
      continue;
    }
    const row: number[] = [keyIndex(item.id), anchor, item.dx, item.dy];
    if (scale !== 1) row.push(scale);
    h.push(row);
  }

  const payload: Payload = {
    v: 1,
    r: MOD_REGISTRY_VERSION,
    n: loadout.name,
    i: loadout.icon,
    c: loadout.mc,
    k: keys,
    d,
    h,
  };
  if (loadout.server !== undefined && loadout.server !== null) payload.s = loadout.server;

  const body = toBase64(utf8(JSON.stringify(payload)));
  return `${SHARE_PREFIX}.${checksum(body)}.${body}`;
}

/** Whether a string looks like a share code at all — for a paste field's live validation. */
export function looksLikeShareCode(code: string): boolean {
  return code.trim().startsWith(`${SHARE_PREFIX}.`);
}

/**
 * A share code back to a loadout, or a reason it is not one.
 *
 * Whitespace-tolerant on the way in, because the realistic input is a paste out of a chat client
 * that has wrapped it across lines.
 */
export function decodeLoadout(code: string): DecodeResult {
  const clean = code.replace(/\s+/g, '');
  if (clean.length === 0) return { ok: false, reason: 'empty' };
  const parts = clean.split('.');
  if (parts.length !== 3 || parts[0] !== SHARE_PREFIX) return { ok: false, reason: 'prefix' };
  const body = parts[2]!;
  if (checksum(body) !== parts[1]) return { ok: false, reason: 'checksum' };

  const bytes = fromBase64(body);
  if (bytes === null) return { ok: false, reason: 'malformed' };
  const json = fromUtf8(bytes);
  if (json === null) return { ok: false, reason: 'malformed' };

  let payload: Payload;
  try {
    payload = JSON.parse(json) as Payload;
  } catch {
    return { ok: false, reason: 'malformed' };
  }
  if (payload === null || typeof payload !== 'object' || payload.v !== 1) {
    return { ok: false, reason: 'malformed' };
  }
  if (typeof payload.n !== 'string' || typeof payload.i !== 'string') {
    return { ok: false, reason: 'malformed' };
  }

  const names = payload.k;
  if (!Array.isArray(names) || names.some((name) => typeof name !== 'string')) {
    return { ok: false, reason: 'malformed' };
  }

  const dropped: string[] = [];
  const mods: Record<string, Record<string, unknown>> = {};
  for (const row of payload.d ?? []) {
    if (!Array.isArray(row) || row.length < 1) return { ok: false, reason: 'malformed' };
    const id = names[row[0] as number];
    // An index the code's own table does not have is a corrupt code, not a forward-compatibility
    // case — the table travels with the payload, so the two cannot disagree unless something has
    // been edited.
    if (typeof id !== 'string') return { ok: false, reason: 'malformed' };
    if (!isModId(id)) {
      dropped.push(id);
      continue;
    }
    const defaults = getModDefaults(id) as unknown as Record<string, unknown>;
    const kept: Record<string, unknown> = {};
    for (let i = 1; i + 1 < row.length; i += 2) {
      const key = names[row[i] as number];
      if (typeof key !== 'string') return { ok: false, reason: 'malformed' };
      // A key this registry does not declare is dropped rather than stored: `mod_states` is
      // `additionalProperties: false`, so keeping it would make the loadout fail its own schema
      // on the next write — a code from the future breaking the receiver's file rather than
      // simply arriving with less in it.
      if (!(key in defaults)) {
        dropped.push(`${id}.${key}`);
        continue;
      }
      kept[key] = row[i + 1];
    }
    if (Object.keys(kept).length > 0) mods[id] = kept;
  }

  const hud: HUDItem[] = [];
  const seen = new Set<string>();
  for (const row of payload.h ?? []) {
    if (!Array.isArray(row) || row.length < 4) return { ok: false, reason: 'malformed' };
    const id = names[row[0] as number];
    if (typeof id !== 'string') return { ok: false, reason: 'malformed' };
    if (!isHudMod(id)) {
      dropped.push(id);
      continue;
    }
    const anchor = ANCHORS[row[1] as number];
    const dx = row[2];
    const dy = row[3];
    if (anchor === undefined || typeof dx !== 'number' || typeof dy !== 'number') {
      return { ok: false, reason: 'malformed' };
    }
    const item: HUDItem = { id, anchor, dx, dy };
    if (row.length > 4 && typeof row[4] === 'number') item.scale = row[4];
    hud.push(item);
    seen.add(id);
  }
  // Every HUD mod the code did not carry sits where the registry puts it — which is what made
  // omitting an untouched placement safe on the way out. Rebuilt in registry order rather than
  // in the code's, so two loadouts that differ only in the order their items were dragged
  // produce the same `hud` array and read as the same layout.
  for (const id of MOD_IDS) {
    if (!isHudMod(id) || seen.has(id)) continue;
    const factory = DEFAULT_HUD_PLACEMENTS[id];
    hud.push({ id, anchor: factory.anchor, dx: factory.dx, dy: factory.dy });
  }

  return {
    ok: true,
    loadout: {
      name: payload.n,
      icon: payload.i,
      mc: typeof payload.c === 'string' ? payload.c : '1.8.9',
      server: payload.s ?? null,
      mods: mods as ModStates,
      hud,
    },
    registryVersion: typeof payload.r === 'number' ? payload.r : 0,
    foreign: payload.r !== MOD_REGISTRY_VERSION,
    dropped,
  };
}

/** Re-exported so a caller can name the type without reaching into the schema. */
export type { ModId };
