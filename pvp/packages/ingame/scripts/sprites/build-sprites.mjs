#!/usr/bin/env node
// Builds the Cartridge pixel-art sprite sheet.
//
//   pnpm --filter @void/ingame sprites      (or: node scripts/sprites/build-sprites.mjs)
//
// Every sprite in ./defs.mjs is a 16 x 16 grid (or 32 x 32: the generated cosmetics). Each art pixel is drawn as a block
// (nearest neighbour) into one 128 x 128 cell; cells are laid out 12 per row on a transparent
// sheet. Outputs:
//   src/cart/sprites.css    `.cart { --c-sheet: url(data:image/png;base64,...) }`
//   src/cart/sprite-map.ts  SPRITE_CELL, SHEET_COLS, SHEET_ROWS, SPRITE_INDEX, SpriteName
//   a review contact sheet (4x, names underneath, on #141416 and #0d0f12) when
//   SPRITES_REVIEW=<path.png> is set, plus <path>.txt listing the names in grid order.
//   Optional: SPRITES_FILTER=<regex>, SPRITES_REVIEW_SCALE=<n>, SPRITES_REVIEW_COLS=<n>,
//   SPRITES_REVIEW_ONE=1 (one background only).
//
// ORDER IS STABLE: a sprite's cell index is its position in the `SPRITES` export of defs.mjs.
// Code (and cached sheets) depend on those indices, so append new sprites at the END of that
// list and never reorder or remove entries in the middle (retire one by redrawing it).
//
// No dependencies: the PNG encoder below is a few lines over node:zlib.

import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { deflateSync, gzipSync } from 'node:zlib';

import { PROTECTED_COLOURS, SPRITES } from './defs.mjs';

const PROTECTED = new Set(PROTECTED_COLOURS.map((c) => c.toLowerCase().replace(/^#([0-9a-f]{6})$/, '#$1ff')));

const here = dirname(fileURLToPath(import.meta.url));
const pkg = resolve(here, '../..');

const CELL = 128;
// Transparent gutter around every cell. The GL driver samples every texture LINEAR, so a cell
// drawn at a fractional scale reads a texel or two of its neighbour; 8 px of nothing between
// cells is what keeps a sprite's edge from picking up the next sprite's outline.
const GUTTER = 8;
const STRIDE = CELL + 2 * GUTTER;
const SCALE = CELL / 16;
const COLS = 12;

// ---------------------------------------------------------------- PNG encoder

const CRC = new Uint32Array(256).map((_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
});
function crc32(buf) {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) c = CRC[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}
function chunk(type, data) {
  const out = Buffer.alloc(12 + data.length);
  out.writeUInt32BE(data.length, 0);
  out.write(type, 4, 'ascii');
  data.copy(out, 8);
  out.writeUInt32BE(crc32(out.subarray(4, 8 + data.length)), 8 + data.length);
  return out;
}

/**
 * Encode RGBA pixels. Uses an indexed palette (+tRNS) when the image has <= 256 colours,
 * truecolour+alpha otherwise. Each scanline picks None / Sub / Up by the smallest sum of
 * absolute filtered bytes; deflate level 9.
 */
export function encodePng(w, h, rgba) {
  const colours = new Map();
  for (let i = 0; i < w * h; i++) {
    const key = rgba.readUInt32BE(i * 4);
    if (!colours.has(key)) {
      colours.set(key, colours.size);
      if (colours.size > 256) break;
    }
  }
  const indexed = colours.size <= 256;
  const bpp = indexed ? 1 : 4;
  const stride = w * bpp;
  const raw = Buffer.alloc(w * h * bpp);
  if (indexed) {
    for (let i = 0; i < w * h; i++) raw[i] = colours.get(rgba.readUInt32BE(i * 4));
  } else rgba.copy(raw);

  const out = Buffer.alloc((stride + 1) * h);
  const cand = [Buffer.alloc(stride), Buffer.alloc(stride), Buffer.alloc(stride)];
  for (let y = 0; y < h; y++) {
    const row = raw.subarray(y * stride, (y + 1) * stride);
    const up = y > 0 ? raw.subarray((y - 1) * stride, y * stride) : null;
    let best = 0;
    let bestSum = Infinity;
    for (let f = 0; f < 3; f++) {
      const c = cand[f];
      let sum = 0;
      for (let i = 0; i < stride; i++) {
        let v = row[i];
        if (f === 1) v = (v - (i >= bpp ? row[i - bpp] : 0)) & 0xff;
        else if (f === 2) v = (v - (up ? up[i] : 0)) & 0xff;
        c[i] = v;
        sum += v < 128 ? v : 256 - v;
      }
      if (sum < bestSum) {
        bestSum = sum;
        best = f;
      }
    }
    out[y * (stride + 1)] = best;
    cand[best].copy(out, y * (stride + 1) + 1);
  }

  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0);
  ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8;
  ihdr[9] = indexed ? 3 : 6;
  const parts = [Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr)];
  if (indexed) {
    const plte = Buffer.alloc(colours.size * 3);
    const trns = Buffer.alloc(colours.size);
    for (const [key, i] of colours) {
      plte[i * 3] = key >>> 24;
      plte[i * 3 + 1] = (key >>> 16) & 0xff;
      plte[i * 3 + 2] = (key >>> 8) & 0xff;
      trns[i] = key & 0xff;
    }
    parts.push(chunk('PLTE', plte), chunk('tRNS', trns));
  }
  parts.push(chunk('IDAT', deflateSync(out, { level: 9, memLevel: 9 })), chunk('IEND', Buffer.alloc(0)));
  return Buffer.concat(parts);
}

// ---------------------------------------------------------------- canvas

function hexToU32(hex) {
  const h = hex.replace('#', '');
  const full = h.length === 3 ? [...h].map((c) => c + c).join('') : h;
  const rgb = parseInt(full.slice(0, 6), 16);
  const a = full.length >= 8 ? parseInt(full.slice(6, 8), 16) : 255;
  return ((rgb << 8) | a) >>> 0;
}

class Canvas {
  constructor(w, h, bg) {
    this.w = w;
    this.h = h;
    this.px = Buffer.alloc(w * h * 4);
    if (bg) this.fill(0, 0, w, h, bg);
  }
  /** Fill with source-over compositing (a translucent colour on an empty pixel stays translucent). */
  fill(x, y, w, h, hex) {
    const v = hexToU32(hex);
    const sa = (v & 0xff) / 255;
    const sr = v >>> 24, sg = (v >>> 16) & 0xff, sb = (v >>> 8) & 0xff;
    for (let j = Math.max(0, y); j < Math.min(this.h, y + h); j++)
      for (let i = Math.max(0, x); i < Math.min(this.w, x + w); i++) {
        const o = (j * this.w + i) * 4;
        const da = this.px[o + 3] / 255;
        if (sa >= 1 || da === 0) {
          this.px.writeUInt32BE(v, o);
          continue;
        }
        const a = sa + da * (1 - sa);
        const blend = (s, d) => Math.round((s * sa + d * da * (1 - sa)) / a);
        this.px[o] = blend(sr, this.px[o]);
        this.px[o + 1] = blend(sg, this.px[o + 1]);
        this.px[o + 2] = blend(sb, this.px[o + 2]);
        this.px[o + 3] = Math.round(a * 255);
      }
  }
  sprite(g, x, y, s) {
    for (let j = 0; j < g.n; j++)
      for (let i = 0; i < g.n; i++) {
        const c = g.d[j][i];
        if (c) this.fill(x + i * s, y + j * s, s, s, c);
      }
  }
  png() {
    return encodePng(this.w, this.h, this.px);
  }
}

// ---------------------------------------------------------------- tiny 3x5 font (review only)

const FONT = {
  A: '010101111101101', B: '110101110101110', C: '011100100100011', D: '110101101101110',
  E: '111100110100111', F: '111100110100100', G: '011100101101011', H: '101101111101101',
  I: '111010010010111', J: '001001001101010', K: '101101110101101', L: '100100100100111',
  M: '101111111101101', N: '110101101101101', O: '010101101101010', P: '110101110100100',
  Q: '010101101110011', R: '110101110101101', S: '011100010001110', T: '111010010010010',
  U: '101101101101111', V: '101101101101010', W: '101101111111101', X: '101101010101101',
  Y: '101101010010010', Z: '111001010100111', 0: '111101101101111', 1: '010110010010111',
  2: '110001010100111', 3: '110001010001110', 4: '101101111001001', 5: '111100110001110',
  6: '011100111101111', 7: '111001010010010', 8: '111101111101111', 9: '111101111001110',
  _: '000000000000111', '-': '000000111000000',
};
function text(cv, str, x, y, colour, s = 1) {
  [...str.toUpperCase()].forEach((ch, k) => {
    const bits = FONT[ch];
    if (!bits) return;
    for (let j = 0; j < 5; j++)
      for (let i = 0; i < 3; i++) if (bits[j * 3 + i] === '1') cv.fill(x + (k * 4 + i) * s, y + j * s, s, s, colour);
  });
}

// ---------------------------------------------------------------- build

const names = Object.keys(SPRITES);
const grids = names.map((n) => {
  const g = SPRITES[n]();
  if (!g || (g.n !== 16 && g.n !== 32)) throw new Error(`sprite ${n} is not a 16x16 or 32x32 grid`);
  return g;
});
const ROWS = Math.ceil(names.length / COLS);

// ---------------------------------------------------------------- palette fit
// An indexed PNG is about half the size of an RGBA one, but holds at most 256 colours
// (the sheet's transparent background takes one). When the art uses more, near-identical
// shades are snapped together: colours are ranked (PROTECTED first, then by pixel count) and
// each one joins the nearest kept colour of the same alpha within the smallest RGB distance
// that fits. The review sheet shows the snapped result, so what you review is what ships.

const norm = (c) => {
  let h = c.toLowerCase().replace('#', '');
  if (h.length === 3) h = [...h].map((q) => q + q).join('');
  return '#' + (h.length === 6 ? h + 'ff' : h);
};
const rgbaOf = (c) => [1, 3, 5, 7].map((i) => parseInt(c.slice(i, i + 2), 16));
const counts = new Map();
grids.forEach((g) => g.map((x, y, c) => {
  const k = norm(c);
  counts.set(k, (counts.get(k) || 0) + 1);
}));
const ranked = [...counts.keys()].sort((a, b) => {
  const pa = PROTECTED.has(a) ? 1 : 0, pb = PROTECTED.has(b) ? 1 : 0;
  return pb - pa || counts.get(b) - counts.get(a);
});
let snapDist = 0;
let remap = new Map(ranked.map((c) => [c, c]));
if (ranked.length > 255) {
  for (snapDist = 1; ; snapDist++) {
    const keep = [];
    const m = new Map();
    for (const c of ranked) {
      const v = rgbaOf(c);
      let best = null, bd = Infinity;
      for (const k of keep) {
        if (k.v[3] !== v[3]) continue;
        const d = Math.hypot(k.v[0] - v[0], k.v[1] - v[1], k.v[2] - v[2]);
        if (d < bd) { bd = d; best = k; }
      }
      if (best && bd <= snapDist) m.set(c, best.c);
      else { keep.push({ c, v }); m.set(c, c); }
    }
    if (keep.length <= 255) { remap = m; break; }
  }
  grids.forEach((g) => g.map((x, y, c) => remap.get(norm(c))));
}
const paletteSize = new Set(remap.values()).size;

const sheet = new Canvas(COLS * STRIDE, ROWS * STRIDE);
grids.forEach((g, i) => sheet.sprite(g, (i % COLS) * STRIDE + GUTTER, Math.floor(i / COLS) * STRIDE + GUTTER, CELL / g.n));
const png = sheet.png();
const b64 = png.toString('base64');

const css = `/* GENERATED by scripts/sprites/build-sprites.mjs from scripts/sprites/defs.mjs — do not edit.
 * Regenerate with \`pnpm --filter @void/ingame sprites\` (node scripts/sprites/build-sprites.mjs).
 * ${names.length} sprites, ${COLS} x ${ROWS} cells of ${CELL} px; cell order is in sprite-map.ts. */
.cart {
  --c-sheet: url(data:image/png;base64,${b64});
}
`;
const ident = (n) => (/^[A-Za-z_$][A-Za-z0-9_$]*$/.test(n) ? n : JSON.stringify(n));
const ts = `// GENERATED by scripts/sprites/build-sprites.mjs from scripts/sprites/defs.mjs — do not edit by hand.
// Regenerate with \`pnpm --filter @void/ingame sprites\`. Indices are stable: new sprites are appended.

export const SPRITE_CELL = ${CELL};
/** Cell pitch in the sheet: the cell plus a transparent gutter on every side. */
export const SPRITE_STRIDE = ${STRIDE};
export const SPRITE_GUTTER = ${GUTTER};
export const SHEET_COLS = ${COLS};
export const SHEET_ROWS = ${ROWS};

export const SPRITE_INDEX = {
${names.map((n, i) => `  ${ident(n)}: ${i},`).join('\n')}
} as const satisfies Record<string, number>;

export type SpriteName = keyof typeof SPRITE_INDEX;
`;
writeFileSync(resolve(pkg, 'src/cart/sprites.css'), css);
writeFileSync(resolve(pkg, 'src/cart/sprite-map.ts'), ts);

// review contact sheet
const review = process.env.SPRITES_REVIEW;
if (review) {
  const filter = process.env.SPRITES_FILTER ? new RegExp(process.env.SPRITES_FILTER) : null;
  const rs = Number(process.env.SPRITES_REVIEW_SCALE || 4);
  const rcols = Number(process.env.SPRITES_REVIEW_COLS || 12);
  const pick = names.map((n, i) => [n, grids[i]]).filter(([n]) => !filter || filter.test(n));
  const cw = Math.max(16 * rs + 16, 112);
  const ch = 16 * rs + 22;
  const rrows = Math.ceil(pick.length / rcols);
  const bgs = process.env.SPRITES_REVIEW_ONE ? ['#141416'] : ['#141416', '#0d0f12'];
  const cv = new Canvas(rcols * cw, rrows * ch * bgs.length, '#000000');
  bgs.forEach((bg, b) => {
    const oy = b * rrows * ch;
    cv.fill(0, oy, rcols * cw, rrows * ch, bg);
    pick.forEach(([n, g], i) => {
      const x = (i % rcols) * cw;
      const y = oy + Math.floor(i / rcols) * ch;
      cv.sprite(g, x + ((cw - 16 * rs) >> 1), y + 4, (rs * 16) / g.n);
      const label = n.length * 4 > cw ? n.slice(0, Math.floor(cw / 4)) : n;
      text(cv, label, x + ((cw - label.length * 4) >> 1), y + 16 * rs + 9, '#9aa0aa');
    });
  });
  mkdirSync(dirname(review), { recursive: true });
  writeFileSync(review, cv.png());
  writeFileSync(review.replace(/\.png$/, '') + '.txt', pick.map(([n], i) => `${i}\t${n}`).join('\n') + '\n');
}

const gz = gzipSync(Buffer.from(b64), { level: 9 });
console.log(
  `sprites: ${names.length} (${COLS}x${ROWS}), ${counts.size} colours -> ${paletteSize} (snap ${snapDist}), ` +
    `png ${png.length} B, base64 ${b64.length} B, gzipped base64 ${gz.length} B`,
);
