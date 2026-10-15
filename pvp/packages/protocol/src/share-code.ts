/**
 * Loadout share codes — the TypeScript reader and writer, for the browser harness's fake
 * bridge and for anything in the launcher that wants to inspect a code without a round trip.
 *
 * ```text
 * VOID1.<base64url, no padding, of raw DEFLATE(json)>.<crc32(json) as 8 lowercase hex>
 * ```
 *
 * The real codecs are `void_loadout::share` (Rust, the launcher) and `ShareCode` (Java, the
 * in-game Loadouts screen); `schema/share-code.fixtures.json` holds codes from each, and
 * `test/share-code.test.ts` decodes every one of them here too. The payload is the loadout's
 * name, icon, server, mc, registry version, HUD layout, quick pins and only the settings that
 * differ from the registry defaults.
 *
 * The writer emits DEFLATE *stored* blocks — valid DEFLATE that every inflater reads, so a code
 * made in the harness imports in the game and the launcher — because a compressor is not worth
 * its bytes in a bundle whose real writer is Java. The reader is a complete inflater (stored,
 * fixed and dynamic Huffman blocks), so it reads codes Rust and Java made.
 *
 * Decoding is strict about structure and lenient about content, exactly as the other two are:
 * see `bridge.json#/definitions/call_importLoadout`.
 */

import type { HUDItem, ImportResult, Loadout, ModId } from './generated/schema.js';
import { KEYBIND_SETTINGS, SETTING_BOUNDS, SETTING_OPTIONS } from './generated/constraints.js';
import { MOD_IDS, MOD_REGISTRY_VERSION, getModDefaults, isHudMod, isModId } from './mods.js';

/** The format version this module writes, and the newest it reads. */
export const SHARE_CODE_VERSION = 1;

/** Longest code accepted. */
export const MAX_SHARE_CODE_LENGTH = 16 * 1024;

const MAX_JSON_BYTES = 256 * 1024;

/** A decoded code: the loadout without its id, and how much was dropped. */
export type DecodedShareCode =
  | { ok: true; loadout: Omit<Loadout, 'id' | 'stats'>; dropped: number }
  | { ok: false; error: Extract<ImportResult, { ok: false }>['error'] };

/* -------------------------------------------------------------------------- */
/* Encode                                                                     */
/* -------------------------------------------------------------------------- */

/** The JSON a code carries, before compression. */
export function shareCodePayload(loadout: Loadout): Record<string, unknown> {
  const mods: Record<string, Record<string, unknown>> = {};
  const given = loadout.mods as Record<string, Record<string, unknown> | undefined>;
  for (const id of MOD_IDS) {
    const defaults = getModDefaults(id) as unknown as Record<string, unknown>;
    const state = given[id];
    if (!state) continue;
    const changed: Record<string, unknown> = {};
    for (const [key, value] of Object.entries(state)) {
      if (defaults[key] !== value) changed[key] = value;
    }
    if (Object.keys(changed).length > 0) mods[id] = changed;
  }
  const out: Record<string, unknown> = {
    name: loadout.name,
    icon: loadout.icon,
    mc: loadout.mc,
    reg: MOD_REGISTRY_VERSION,
    mods,
    hud: loadout.hud,
  };
  if (loadout.server) out.server = loadout.server;
  if (loadout.quick) out.quick = loadout.quick;
  return out;
}

/** The share code of `loadout`. */
export function encodeShareCode(loadout: Loadout): string {
  const json = utf8(JSON.stringify(shareCodePayload(loadout)));
  return `VOID${SHARE_CODE_VERSION}.${base64url(deflateStored(json))}.${hex8(crc32(json))}`;
}

/* -------------------------------------------------------------------------- */
/* Decode                                                                     */
/* -------------------------------------------------------------------------- */

/** Decodes a pasted code. Never throws. */
export function decodeShareCode(code: string): DecodedShareCode {
  const c = (code ?? '').trim();
  if (c === '') return { ok: false, error: 'empty' };
  if (c.length > MAX_SHARE_CODE_LENGTH) return { ok: false, error: 'format' };
  const first = c.indexOf('.');
  const last = c.lastIndexOf('.');
  if (first <= 0 || last <= first) return { ok: false, error: 'format' };
  const head = c.slice(0, first);
  const version = /^VOID([0-9]+)$/.exec(head);
  if (!version || Number(version[1]) === 0) return { ok: false, error: 'format' };
  if (Number(version[1]) > SHARE_CODE_VERSION) return { ok: false, error: 'version' };
  const check = c.slice(last + 1);
  if (!/^[0-9a-fA-F]{8}$/.test(check)) return { ok: false, error: 'format' };
  const compressed = unbase64url(c.slice(first + 1, last));
  if (!compressed) return { ok: false, error: 'format' };
  const json = inflateRaw(compressed);
  if (!json) return { ok: false, error: 'format' };
  if (crc32(json) !== parseInt(check, 16)) return { ok: false, error: 'checksum' };
  let parsed: unknown;
  try {
    parsed = JSON.parse(fromUtf8(json));
  } catch {
    return { ok: false, error: 'format' };
  }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    return { ok: false, error: 'invalid' };
  }
  return read(parsed as Record<string, unknown>);
}

const HEX_COLOR = /^#[0-9A-Fa-f]{6}(?:[0-9A-Fa-f]{2})?$/;
const KEYBIND =
  /^(?:NONE|[A-Z0-9]|F[1-9]|F1[0-2]|NUMPAD[0-9]|MOUSE[0-7]|SPACE|TAB|ESCAPE|RETURN|BACK|DELETE|INSERT|HOME|END|PRIOR|NEXT|UP|DOWN|LEFT|RIGHT|LSHIFT|RSHIFT|LCONTROL|RCONTROL|LMENU|RMENU|CAPITAL|LBRACKET|RBRACKET|SEMICOLON|APOSTROPHE|COMMA|PERIOD|SLASH|BACKSLASH|MINUS|EQUALS|GRAVE)$/;
const ANCHORS = new Set([
  'top-left', 'top', 'top-right', 'left', 'center', 'right', 'bottom-left', 'bottom', 'bottom-right',
]);

/** Whether `value` is one `id.key` accepts as-is (never clamped — see the module docs). */
function settingAccepts(id: ModId, key: string, value: unknown): boolean {
  const defaults = getModDefaults(id) as unknown as Record<string, unknown>;
  if (!(key in defaults)) return false;
  const fallback = defaults[key];
  if (typeof value !== typeof fallback) return false;
  if (typeof value === 'number') {
    const bounds = SETTING_BOUNDS[key];
    if (!Number.isFinite(value)) return false;
    if (bounds && (value < bounds.min || value > bounds.max)) return false;
    if (Number.isInteger(fallback) && !Number.isInteger(value)) return false;
    return true;
  }
  if (typeof value === 'string') {
    const options = SETTING_OPTIONS[`${id}.${key}`];
    if (options) return options.includes(value);
    if (KEYBIND_SETTINGS.includes(`${id}.${key}`)) return KEYBIND.test(value);
    if (key.endsWith('color')) return HEX_COLOR.test(value);
    return true;
  }
  return typeof value === 'boolean';
}

function read(input: Record<string, unknown>): DecodedShareCode {
  let dropped = 0;
  const rawName = typeof input.name === 'string' ? input.name.trim() : '';
  const name = [...rawName].slice(0, 48).join('').trim();
  if (name === '') return { ok: false, error: 'invalid' };
  const icon =
    typeof input.icon === 'string' && input.icon.length >= 1 && input.icon.length <= 32
      ? input.icon
      : 'sword';
  const server =
    typeof input.server === 'string' && input.server.length <= 32 ? input.server : null;
  const mc =
    typeof input.mc === 'string' && /^[0-9]{1,4}\.[0-9]{1,4}(?:\.[0-9]{1,4})?$/.test(input.mc)
      ? input.mc
      : '1.8.9';

  const mods: Record<string, Record<string, unknown>> = {};
  if (input.mods && typeof input.mods === 'object' && !Array.isArray(input.mods)) {
    for (const [id, settings] of Object.entries(input.mods as Record<string, unknown>)) {
      if (!isModId(id) || !settings || typeof settings !== 'object' || Array.isArray(settings)) {
        dropped += 1;
        continue;
      }
      const accepted: Record<string, unknown> = {};
      for (const [key, value] of Object.entries(settings as Record<string, unknown>)) {
        if (settingAccepts(id, key, value)) accepted[key] = value;
        else dropped += 1;
      }
      if (Object.keys(accepted).length > 0) {
        const defaults = getModDefaults(id) as unknown as Record<string, unknown>;
        mods[id] = { on: defaults.on, ...accepted };
      }
    }
  }

  const hud: HUDItem[] = [];
  if (Array.isArray(input.hud)) {
    for (const raw of input.hud) {
      const item = raw as Partial<HUDItem> | null;
      const ok =
        item &&
        typeof item === 'object' &&
        typeof item.id === 'string' &&
        isHudMod(item.id) &&
        typeof item.anchor === 'string' &&
        ANCHORS.has(item.anchor) &&
        typeof item.dx === 'number' &&
        typeof item.dy === 'number' &&
        Math.abs(item.dx) <= 4096 &&
        Math.abs(item.dy) <= 4096 &&
        (item.scale === undefined || (typeof item.scale === 'number' && item.scale >= 0.25 && item.scale <= 4)) &&
        !hud.some((h) => h.id === item.id);
      if (ok) hud.push({ ...(item as HUDItem) });
      else dropped += 1;
    }
  }

  let quick: ModId[] | undefined;
  if (Array.isArray(input.quick)) {
    quick = [];
    for (const id of input.quick) {
      if (typeof id === 'string' && isModId(id) && !quick.includes(id) && quick.length < 8) quick.push(id);
      else dropped += 1;
    }
  }

  const loadout: Omit<Loadout, 'id' | 'stats'> = {
    name,
    icon,
    server,
    mc,
    mods: mods as Loadout['mods'],
    hud,
    ...(quick ? { quick } : {}),
  };
  return { ok: true, loadout, dropped };
}

/* -------------------------------------------------------------------------- */
/* Bytes                                                                      */
/* -------------------------------------------------------------------------- */

function utf8(text: string): Uint8Array {
  return new TextEncoder().encode(text);
}

function fromUtf8(bytes: Uint8Array): string {
  return new TextDecoder().decode(bytes);
}

let CRC_TABLE: Uint32Array | null = null;

/** CRC-32 (IEEE), as `java.util.zip.CRC32` and `crc32fast` compute it. */
export function crc32(bytes: Uint8Array): number {
  if (!CRC_TABLE) {
    CRC_TABLE = new Uint32Array(256);
    for (let n = 0; n < 256; n++) {
      let c = n;
      for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
      CRC_TABLE[n] = c >>> 0;
    }
  }
  let crc = 0xffffffff;
  for (let i = 0; i < bytes.length; i++) crc = CRC_TABLE[(crc ^ bytes[i]!) & 0xff]! ^ (crc >>> 8);
  return (crc ^ 0xffffffff) >>> 0;
}

function hex8(n: number): string {
  return n.toString(16).padStart(8, '0');
}

const B64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_';

function base64url(bytes: Uint8Array): string {
  let out = '';
  for (let i = 0; i < bytes.length; i += 3) {
    const a = bytes[i]!;
    const b = bytes[i + 1];
    const c = bytes[i + 2];
    out += B64[a >> 2]! + B64[((a & 3) << 4) | ((b ?? 0) >> 4)]!;
    if (b !== undefined) out += B64[((b & 15) << 2) | ((c ?? 0) >> 6)]!;
    if (c !== undefined) out += B64[c & 63]!;
  }
  return out;
}

function unbase64url(text: string): Uint8Array | null {
  if (!/^[A-Za-z0-9_-]*$/.test(text) || text.length % 4 === 1) return null;
  const out = new Uint8Array(Math.floor((text.length * 3) / 4));
  let bits = 0;
  let value = 0;
  let o = 0;
  for (const ch of text) {
    value = (value << 6) | B64.indexOf(ch);
    bits += 6;
    if (bits >= 8) {
      bits -= 8;
      out[o++] = (value >> bits) & 0xff;
    }
  }
  return out.subarray(0, o);
}

/** Raw DEFLATE made of stored blocks only: valid for every inflater, no compression. */
function deflateStored(data: Uint8Array): Uint8Array {
  const blocks = Math.max(1, Math.ceil(data.length / 65535));
  const out = new Uint8Array(data.length + blocks * 5);
  let o = 0;
  for (let b = 0; b < blocks; b++) {
    const start = b * 65535;
    const len = Math.min(65535, data.length - start);
    out[o++] = b === blocks - 1 ? 1 : 0;
    out[o++] = len & 0xff;
    out[o++] = len >> 8;
    out[o++] = ~len & 0xff;
    out[o++] = (~len >> 8) & 0xff;
    out.set(data.subarray(start, start + len), o);
    o += len;
  }
  return out;
}

/* -- inflate (RFC 1951), after tinf ----------------------------------------- */

interface Tree {
  counts: Uint16Array;
  symbols: Uint16Array;
}

const LENGTH_BASE = [3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258];
const LENGTH_EXTRA = [0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0];
const DIST_BASE = [1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577];
const DIST_EXTRA = [0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13];
const CLEN_ORDER = [16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15];

function buildTree(lengths: ArrayLike<number>, offset: number, count: number): Tree {
  const counts = new Uint16Array(16);
  const symbols = new Uint16Array(count);
  for (let i = 0; i < count; i++) {
    const len = lengths[offset + i]!;
    counts[len] = counts[len]! + 1;
  }
  counts[0] = 0;
  const offs = new Uint16Array(16);
  for (let i = 1; i < 16; i++) offs[i] = offs[i - 1]! + counts[i - 1]!;
  for (let i = 0; i < count; i++) {
    const len = lengths[offset + i]!;
    if (len) symbols[offs[len]!++] = i;
  }
  return { counts, symbols };
}

class BitReader {
  private pos = 0;
  private bit = 0;
  private bitCount = 0;
  constructor(private readonly src: Uint8Array) {}
  bits(n: number): number {
    while (this.bitCount < n) {
      if (this.pos >= this.src.length) throw new Error('truncated');
      this.bit |= this.src[this.pos++]! << this.bitCount;
      this.bitCount += 8;
    }
    const v = this.bit & ((1 << n) - 1);
    this.bit >>>= n;
    this.bitCount -= n;
    return v;
  }
  symbol(tree: Tree): number {
    let sum = 0;
    let cur = 0;
    let len = 0;
    do {
      cur = 2 * cur + this.bits(1);
      len++;
      if (len > 15) throw new Error('bad code');
      sum += tree.counts[len]!;
      cur -= tree.counts[len]!;
    } while (cur >= 0);
    return tree.symbols[sum + cur]!;
  }
  align(): void {
    this.bit = 0;
    this.bitCount = 0;
  }
  bytes(n: number): Uint8Array {
    if (this.pos + n > this.src.length) throw new Error('truncated');
    const out = this.src.subarray(this.pos, this.pos + n);
    this.pos += n;
    return out;
  }
}

let FIXED: { lit: Tree; dist: Tree } | null = null;

function fixedTrees(): { lit: Tree; dist: Tree } {
  if (!FIXED) {
    const l = new Uint8Array(288);
    l.fill(8, 0, 144);
    l.fill(9, 144, 256);
    l.fill(7, 256, 280);
    l.fill(8, 280, 288);
    const d = new Uint8Array(30).fill(5);
    FIXED = { lit: buildTree(l, 0, 288), dist: buildTree(d, 0, 30) };
  }
  return FIXED;
}

/** Inflates a raw DEFLATE stream, or null for anything malformed or too large. */
export function inflateRaw(data: Uint8Array): Uint8Array | null {
  const out: number[] = [];
  const r = new BitReader(data);
  try {
    let final = 0;
    do {
      final = r.bits(1);
      const type = r.bits(2);
      if (type === 0) {
        r.align();
        const head = r.bytes(4);
        const len = head[0]! | (head[1]! << 8);
        const nlen = head[2]! | (head[3]! << 8);
        if ((len ^ 0xffff) !== nlen) return null;
        for (const b of r.bytes(len)) out.push(b);
      } else if (type === 1 || type === 2) {
        let lit: Tree;
        let dist: Tree;
        if (type === 1) {
          ({ lit, dist } = fixedTrees());
        } else {
          const hlit = r.bits(5) + 257;
          const hdist = r.bits(5) + 1;
          const hclen = r.bits(4) + 4;
          const clens = new Uint8Array(19);
          for (let i = 0; i < hclen; i++) clens[CLEN_ORDER[i]!] = r.bits(3);
          const clTree = buildTree(clens, 0, 19);
          const lens = new Uint8Array(hlit + hdist);
          for (let n = 0; n < hlit + hdist; ) {
            const sym = r.symbol(clTree);
            if (sym < 16) lens[n++] = sym;
            else {
              let prev = 0;
              let rep = 0;
              if (sym === 16) {
                if (n === 0) return null;
                prev = lens[n - 1]!;
                rep = 3 + r.bits(2);
              } else if (sym === 17) rep = 3 + r.bits(3);
              else rep = 11 + r.bits(7);
              if (n + rep > hlit + hdist) return null;
              while (rep--) lens[n++] = prev;
            }
          }
          lit = buildTree(lens, 0, hlit);
          dist = buildTree(lens, hlit, hdist);
        }
        for (;;) {
          const sym = r.symbol(lit);
          if (sym === 256) break;
          if (sym < 256) out.push(sym);
          else {
            const li = sym - 257;
            if (li >= LENGTH_BASE.length) return null;
            const length = LENGTH_BASE[li]! + r.bits(LENGTH_EXTRA[li]!);
            const di = r.symbol(dist);
            if (di >= DIST_BASE.length) return null;
            const distance = DIST_BASE[di]! + r.bits(DIST_EXTRA[di]!);
            if (distance > out.length) return null;
            for (let i = 0; i < length; i++) out.push(out[out.length - distance]!);
          }
          if (out.length > MAX_JSON_BYTES) return null;
        }
      } else {
        return null;
      }
      if (out.length > MAX_JSON_BYTES) return null;
    } while (!final);
  } catch {
    return null;
  }
  return Uint8Array.from(out);
}
