/**
 * The painter's brushes: a tiny RGBA canvas, a PNG encoder, the VOID palette, deterministic
 * noise, and a UV atlas for cuboid models whose faces are painted one rect at a time.
 *
 * Nothing here is clever. It exists so every texture in the launch set is *code* — reviewable,
 * diffable, re-paintable at another scale — rather than an opaque PNG someone has to redraw.
 */

import { deflateSync } from 'node:zlib';
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';

/* ------------------------------------------------------------------------------ palette */

/**
 * The launch palette. Subtle and expensive: void blacks, the #9F8BFF violet family, ember, ice,
 * jade, and warm/cool neutrals. No saturated rainbow anywhere; a hue is used at full strength
 * only where it emits light.
 */
export const P = {
  void0: '#060509', void1: '#0b0a10', void2: '#14121c', void3: '#1d1a28', void4: '#2a2638',
  slate0: '#24252a', slate1: '#2f3136', slate2: '#3a3c42', slate3: '#474a4f', slate4: '#5c5e63', slate5: '#7a7c82',
  bone: '#edeef0', mist: '#c9cbd1',
  vio0: '#1c1630', vio1: '#2a2250', vio2: '#4a3c99', vio3: '#6e5bd6', vio4: '#9f8bff', vio5: '#c9beff', vio6: '#ece7ff',
  emb0: '#3a1d17', emb1: '#7a3524', emb2: '#c8603f', emb3: '#ff7e57', emb4: '#ff9e7a', emb5: '#ffc2a8', emb6: '#ffe6d9',
  ice0: '#0e2630', ice1: '#164452', ice2: '#2f7f99', ice3: '#3fa9cc', ice4: '#7adfff', ice5: '#bff0ff', ice6: '#e8fbff',
  jade2: '#3d9e74', jade3: '#55c493', jade4: '#7ae0b0', jade5: '#b6f2d4',
  gold2: '#9a7a4a', gold3: '#c9a46a', gold4: '#e8c88f',
};

export function rgb(hex) {
  const n = parseInt(hex.slice(1), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

export function mix(a, b, t) {
  const A = typeof a === 'string' ? rgb(a) : a;
  const B = typeof b === 'string' ? rgb(b) : b;
  return [0, 1, 2].map((i) => Math.round(A[i] + (B[i] - A[i]) * t));
}

export function shade(c, k) {
  const C = typeof c === 'string' ? rgb(c) : c;
  return C.map((v) => Math.max(0, Math.min(255, Math.round(k >= 0 ? v + (255 - v) * k : v * (1 + k)))));
}

/* -------------------------------------------------------------------------------- ramps */

export function toHsl(c) {
  const [r, g, b] = (typeof c === 'string' ? rgb(c) : c).map((v) => v / 255);
  const max = Math.max(r, g, b), min = Math.min(r, g, b);
  const l = (max + min) / 2;
  if (max === min) return [0, 0, l];
  const d = max - min;
  const s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
  let h;
  if (max === r) h = (g - b) / d + (g < b ? 6 : 0);
  else if (max === g) h = (b - r) / d + 2;
  else h = (r - g) / d + 4;
  return [h * 60, s, l];
}

export function fromHsl(h, s, l) {
  h = ((h % 360) + 360) % 360 / 360;
  s = Math.max(0, Math.min(1, s));
  l = Math.max(0, Math.min(1, l));
  if (s === 0) return [l, l, l].map((v) => Math.round(v * 255));
  const q = l < 0.5 ? l * (1 + s) : l + s - l * s, p = 2 * l - q;
  const t = (x) => {
    x = x < 0 ? x + 1 : x > 1 ? x - 1 : x;
    if (x < 1 / 6) return p + (q - p) * 6 * x;
    if (x < 1 / 2) return q;
    if (x < 2 / 3) return p + (q - p) * (2 / 3 - x) * 6;
    return p;
  };
  return [t(h + 1 / 3), t(h), t(h - 1 / 3)].map((v) => Math.round(v * 255));
}

/**
 * A hue-shifted value ramp of `n` shades through `base` (which lands near the middle): the
 * shadows turn toward violet-blue and gain saturation, the lights turn toward warm and lose it,
 * the way painted light behaves (docs/cosmetics.md, "the reference board"). `lo`/`hi` are the
 * lightness of the darkest and lightest shade.
 */
export function ramp(base, n = 6, { lo = 0.06, hi = 0.9, cool = 250, warm = 45, shiftDark = 0.3, shiftLight = 0.2 } = {}) {
  const [h, s, l] = toHsl(base);
  const towards = (from, to, k) => {
    let d = ((to - from + 540) % 360) - 180;
    return from + d * k;
  };
  const out = [];
  for (let i = 0; i < n; i++) {
    const t = n === 1 ? 0.5 : i / (n - 1);
    const L = t < 0.5 ? lo + (l - lo) * (t / 0.5) : l + (hi - l) * ((t - 0.5) / 0.5);
    const dark = Math.max(0, 0.5 - t) * 2, light = Math.max(0, t - 0.5) * 2;
    const H = dark > 0 ? towards(h, cool, dark * shiftDark) : towards(h, warm, light * shiftLight);
    // HSL saturation reads hotter the darker a colour is: ease it off in the shadows.
    const S = Math.min(s, 0.85) * (1 - dark * 0.3) * (1 - light * 0.4);
    out.push(fromHsl(H, S, L));
  }
  return out;
}

/** Relative luminance 0..1 (Rec. 709), for checking a composition's value structure. */
export function lum(c) {
  const [r, g, b] = typeof c === 'string' ? rgb(c) : c;
  return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255;
}

/** Material key colours for `material.png` (docs/cosmetics.md, "Materials"). */
export const MAT = {
  matte: [0, 0, 0], cloth: [0, 0, 255], metal: [255, 255, 0], glass: [0, 255, 255], emissive: [255, 255, 255],
};

/* -------------------------------------------------------------------------------- noise */

/** Deterministic hash noise, so a re-paint is byte-identical. */
export function hash(x, y, seed = 0) {
  let h = (x * 374761393 + y * 668265263 + seed * 2147483647) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  h ^= h >>> 16;
  return (h >>> 0) / 4294967295;
}

/** 4x4 Bayer threshold in [0,1), for the occasional two-tone dither between bands. */
const BAYER = [0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5];
export const bayer = (x, y) => (BAYER[(y & 3) * 4 + (x & 3)] + 0.5) / 16;

/* ------------------------------------------------------------------------------- canvas */

export class Canvas {
  constructor(w, h) {
    this.w = w;
    this.h = h;
    this.px = new Uint8ClampedArray(w * h * 4);
  }

  set(x, y, c, a = 255) {
    x = Math.floor(x);
    y = Math.floor(y);
    if (x < 0 || y < 0 || x >= this.w || y >= this.h) return;
    const C = typeof c === 'string' ? rgb(c) : c;
    const i = (y * this.w + x) * 4;
    this.px[i] = C[0];
    this.px[i + 1] = C[1];
    this.px[i + 2] = C[2];
    this.px[i + 3] = a;
  }

  get(x, y) {
    const i = (y * this.w + x) * 4;
    return [this.px[i], this.px[i + 1], this.px[i + 2], this.px[i + 3]];
  }

  /** Source-over blend of a colour at alpha `a` (0..1). */
  blend(x, y, c, a) {
    if (x < 0 || y < 0 || x >= this.w || y >= this.h || a <= 0) return;
    const C = typeof c === 'string' ? rgb(c) : c;
    const [r, g, b, al] = this.get(x, y);
    const da = al / 255;
    const oa = a + da * (1 - a);
    if (oa <= 0) return;
    const out = [0, 1, 2].map((k) => Math.round((C[k] * a + [r, g, b][k] * da * (1 - a)) / oa));
    this.set(x, y, out, Math.round(oa * 255));
  }

  rect(x, y, w, h, c, a = 255) {
    for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) this.set(x + i, y + j, c, a);
  }

  /** Calls f(i, j) for every pixel of a rect, setting whatever colour it returns ([c, a] or c). */
  paint(x, y, w, h, f) {
    for (let j = 0; j < h; j++) {
      for (let i = 0; i < w; i++) {
        const r = f(i, j, w, h);
        if (r == null) continue;
        if (Array.isArray(r) && (typeof r[0] === 'string' || Array.isArray(r[0]))) this.set(x + i, y + j, r[0], r[1]);
        else this.set(x + i, y + j, r);
      }
    }
  }

  /** Copies another canvas in at (x, y). */
  draw(src, x, y) {
    for (let j = 0; j < src.h; j++) for (let i = 0; i < src.w; i++) {
      const [r, g, b, a] = src.get(i, j);
      if (a) this.set(x + i, y + j, [r, g, b], a);
    }
  }

  png() {
    return encodePng(this.w, this.h, this.px);
  }

  save(path) {
    mkdirSync(dirname(path), { recursive: true });
    writeFileSync(path, this.png());
  }
}

/* ---------------------------------------------------------------------------------- png */

const CRC = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c >>> 0;
  }
  return t;
})();

function crc32(buf) {
  let c = 0xffffffff;
  for (const b of buf) c = CRC[(c ^ b) & 255] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}

export function encodePng(w, h, rgba) {
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) {
    raw[y * (w * 4 + 1)] = 0;
    Buffer.from(rgba.buffer, rgba.byteOffset + y * w * 4, w * 4).copy(raw, y * (w * 4 + 1) + 1);
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0);
  ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 6; // RGBA
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

/* -------------------------------------------------------------------------------- atlas */

/**
 * A shelf packer for per-face UV. `cube(...)` allocates a rect for every face of a cuboid at
 * one texel per model pixel, so every 3D item shares the skin's texel density, and returns the
 * source-model cube plus the face rects so the painter can fill exactly those pixels.
 *
 * Face conventions (docs/cosmetics.md): n/s/e/w are the -z/+z/+x/-x sides, u/d the -y/+y
 * (top/bottom, model y points down). Side faces: v runs down (+y). n: u along +x; s: u along -x;
 * e: u along +z; w: u along -z. u/d faces: u along +x, v along -z.
 */
export class Atlas {
  /** @param d texels per model pixel: 1 is the skin's density, 2 is HD. */
  constructor(w, h, d = 1) {
    this.w = w;
    this.h = h;
    this.d = d;
    this.x = 0;
    this.y = 0;
    this.row = 0;
  }

  alloc(w, h) {
    w = Math.max(1, Math.ceil(w));
    h = Math.max(1, Math.ceil(h));
    if (this.x + w > this.w) {
      this.x = 0;
      this.y += this.row;
      this.row = 0;
    }
    if (this.y + h > this.h) throw new Error(`atlas ${this.w}x${this.h} full`);
    const r = [this.x, this.y, this.x + w, this.y + h];
    this.x += w;
    this.row = Math.max(this.row, h);
    return r;
  }

  /**
   * @param faces which faces to allocate (default all six); a face whose extent is zero is
   *              skipped automatically, which is what makes a flat plane cost two rects.
   */
  cube(from, to, faces = 'nsewud', extra = {}) {
    const [x0, y0, z0] = from;
    const [x1, y1, z1] = to;
    const W = x1 - x0, H = y1 - y0, D = z1 - z0;
    const dims = { n: [W, H], s: [W, H], e: [D, H], w: [D, H], u: [W, D], d: [W, D] };
    // Rects are keyed by the long face names (north, south, east, west, up, down) everywhere the
    // painter sees them, so a paint function's `face === 'up'` means what it says.
    const names = { n: 'north', s: 'south', e: 'east', w: 'west', u: 'up', d: 'down' };
    const rects = {};
    for (const f of faces) {
      const [a, b] = dims[f];
      if (a <= 0 || b <= 0) continue;
      rects[names[f]] = this.alloc(Math.max(1, Math.round(a * this.d)), Math.max(1, Math.round(b * this.d)));
    }
    const out = { from, to, faces: { ...rects }, ...extra };
    return { cube: out, rects };
  }
}

/** Fills each allocated face rect with f(face, i, j, w, h). */
export function paintFaces(canvas, rects, f) {
  for (const [face, r] of Object.entries(rects)) {
    canvas.paint(r[0], r[1], r[2] - r[0], r[3] - r[1], (i, j, w, h) => f(face, i, j, w, h));
  }
}

export const clamp01 = (v) => Math.max(0, Math.min(1, v));
export const smooth = (t) => t * t * (3 - 2 * t);
