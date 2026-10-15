// Every Cartridge sprite: 16 x 16 pixel art, 3-4 tones (light top-left, dark bottom-right),
// a dark coloured outline from G.ol(), Minecraft-adjacent palette.
//
// The sprites marked "port" are verbatim from the approved design's generator
// (figma-refresh/gen/sprites.js). Everything else is drawn here in the same style.
//
// ORDER MATTERS: `SPRITES` key order is the cell order of the generated sheet and is
// referenced by index from src/cart/sprite-map.ts. Append new sprites at the end only.

import { G } from './pixel.mjs';

// ------------------------------------------------------------------ colour helpers

const hex2 = (n) => Math.round(Math.max(0, Math.min(255, n))).toString(16).padStart(2, '0');
function rgb(c) {
  const h = c.replace('#', '');
  return [0, 2, 4].map((i) => parseInt(h.slice(i, i + 2), 16));
}
export function mix(a, b, t) {
  const A = rgb(a), B = rgb(b);
  return '#' + A.map((v, i) => hex2(v + (B[i] - v) * t)).join('');
}
const darken = (c, t) => mix(c, '#000000', t);
const lighten = (c, t) => mix(c, '#ffffff', t);
const luma = (c) => {
  const [r, g, b] = rgb(c);
  return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255;
};

// ------------------------------------------------------------------ palettes

const DIA = { a: '#d9fff8', b: '#4aedd9', c: '#21b3a3', k: '#0c3f3a' };
const GOLD = { a: '#fffbc2', b: '#f6d33f', c: '#c08a10', k: '#4d3304' };
const IRON = { a: '#ffffff', b: '#d8d8d8', c: '#9a9a9a', k: '#363636' };
const RED = { a: '#ffd6d0', b: '#ff6b5e', c: '#c2302a', k: '#4a0f0c' };
const WOOD = { a: '#d6ac6c', b: '#a67c45', c: '#6e4f27', k: '#2a1c0a' };
const STONE = { a: '#c4c4c4', b: '#8f8f8f', c: '#616161', k: '#232323' };
const LEATHER = { a: '#d9a26a', b: '#a8693e', c: '#73452a', k: '#2e1a0c' };
const CHAIN = { a: '#eceef2', b: '#acb0b8', c: '#6f737c', k: '#22242a' };
const PURPLE = { a: '#e2d2ff', b: '#9a6cf2', c: '#6038b8', k: '#22104a' };

// ------------------------------------------------------------------ shape helpers

/** Copy of a grid. */
function copy(g) {
  const o = new G(g.n);
  o.d = g.d.map((row) => row.slice());
  return o;
}
/** Mirror left-right. */
function flipX(g) {
  const o = new G(g.n);
  o.d = g.d.map((row) => row.slice().reverse());
  return o;
}
/** Paint every filled pixel of `top` over `base`. */
function over(base, top) {
  top.map((x, y, c) => {
    base.d[y][x] = c;
  });
  return base;
}
/** Clear every pixel whose colour is `c` (used to drop an outline before compositing). */
function strip(g, c) {
  for (let y = 0; y < g.n; y++) for (let x = 0; x < g.n; x++) if (g.d[y][x] === c) g.d[y][x] = null;
  return g;
}
/**
 * A tapered stroke from (x0,y0) to (x1,y1), radius r0 -> r1, shaded across its width:
 * the upper-left side gets P.a, the middle P.b, the lower-right P.c.
 * pick(t, off, r) may override the colour (t along the stroke 0..1, off across it).
 */
function stroke(g, x0, y0, x1, y1, r0, r1, P, pick) {
  const dx = x1 - x0, dy = y1 - y0, L2 = dx * dx + dy * dy, L = Math.sqrt(L2);
  const nx = -dy / L, ny = dx / L;
  for (let y = 0; y < g.n; y++) for (let x = 0; x < g.n; x++) {
    const px = x + 0.5, py = y + 0.5;
    const t = Math.max(0, Math.min(1, ((px - x0) * dx + (py - y0) * dy) / L2));
    const cx = x0 + t * dx, cy = y0 + t * dy;
    const r = r0 + (r1 - r0) * t;
    if (Math.hypot(px - cx, py - cy) > r) continue;
    const off = (px - cx) * nx + (py - cy) * ny;
    let col = off < -r * 0.3 ? P.a : off > r * 0.3 ? P.c : P.b;
    if (pick) col = pick(t, off, r) ?? col;
    g.p(x, y, col);
  }
  return g;
}
/** A 4-point sparkle centred on (x, y). */
function spark(g, x, y, core = '#ffffff', arm = '#fff3b0', big = false) {
  g.p(x, y, core).p(x - 1, y, arm).p(x + 1, y, arm).p(x, y - 1, arm).p(x, y + 1, arm);
  if (big) g.p(x - 2, y, arm).p(x + 2, y, arm).p(x, y - 2, arm).p(x, y + 2, arm);
  return g;
}
/** Number glyphs 3x5 for item counts, drawn white with a Minecraft drop shadow. */
const DIGITS = {
  0: '111101101101111', 1: '010110010010111', 2: '111001111100111', 3: '111001111001111',
  4: '101101111001001', 5: '111100111001111', 6: '111100111101111', 7: '111001001010010',
  8: '111101111101111', 9: '111101111001111',
};
function count(g, str, x, y) {
  const put = (ox, oy, col) => [...str].forEach((ch, k) => {
    const bits = DIGITS[ch];
    for (let j = 0; j < 5; j++) for (let i = 0; i < 3; i++) if (bits[j * 3 + i] === '1') g.p(ox + k * 4 + i, oy + j, col);
  });
  put(x + 1, y + 1, '#3f3f3f');
  put(x, y, '#ffffff');
  return g;
}

// ------------------------------------------------------------------ shared item shapes

/** port: the design's sword. outline=false leaves it un-outlined for compositing. */
function sword(P, outline = true) {
  const g = new G();
  for (let i = 0; i < 9; i++) { g.p(5 + i, 10 - i, P.b); g.p(6 + i, 10 - i, P.a); g.p(5 + i, 11 - i, P.c); }
  g.p(14, 1, P.a); g.p(13, 1, P.b); g.p(14, 2, P.b);
  g.l(2, 8, 6, 12, P.c); g.p(3, 8, P.b); g.p(6, 13, P.b);
  g.l(2, 13, 4, 11, '#7a4f25'); g.l(3, 13, 5, 11, '#5a3816'); g.p(1, 14, '#3a240e'); g.p(2, 14, '#7a4f25');
  return outline ? g.ol(P.k) : g;
}

/** port: the design's potion bottle. */
function potion(liq = '#f2434b', liqD = '#a8202a') {
  const g = new G();
  g.r(7, 1, 2, 1, '#9a6a3a');
  g.r(6, 2, 4, 1, '#cfe3f0').r(7, 3, 2, 2, '#cfe3f0');
  g.c(8, 10, 5.2, '#d8ecf8');
  g.c(8, 10, 4.4, liq, (x, y) => y >= 8);
  g.c(8, 10, 4.4, liqD, (x, y) => y >= 12 && x >= 9);
  g.p(5, 8, '#ffffff').p(5, 9, '#ffffff').p(6, 7, '#ffffff');
  return g.ol('#1f1a2a');
}

/** Pickaxe: wooden handle bottom-left to top-right, a quarter-arc head across the top-right. */
function pickaxe(P, outline = true) {
  const g = new G();
  stroke(g, 2.5, 13.5, 10.5, 5.5, 0.95, 0.95, { a: '#a8743e', b: '#7a4f25', c: '#5a3816' });
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const px = x + 0.5, py = y + 0.5;
    const d = Math.hypot(px - 1.5, py - 14.5);
    const ang = Math.atan2(px - 1.5, 14.5 - py); // 0 = up, pi/2 = right
    if (ang < 0.05 || ang > Math.PI / 2 - 0.05) continue;
    const mid = Math.abs(ang - Math.PI / 4) / (Math.PI / 4); // 0 at centre, 1 at the tips
    const half = 1.45 - mid * 0.75;
    if (Math.abs(d - 12.4) > half) continue;
    const col = d - 12.4 > half * 0.25 ? (ang < Math.PI / 4 ? P.a : P.b) : d - 12.4 < -half * 0.25 ? P.c : P.b;
    g.p(x, y, col);
  }
  return outline ? g.ol(P.k) : g;
}

/** Axe: handle bottom-left to top-right, a trapezoid blade standing off its upper-left side. */
function axe(P, outline = true) {
  const g = new G();
  stroke(g, 2.5, 13.5, 12.5, 3.5, 0.95, 0.95, { a: '#a8743e', b: '#7a4f25', c: '#5a3816' });
  const H = [9.6, 6.4], u = [Math.SQRT1_2, -Math.SQRT1_2], n = [-Math.SQRT1_2, -Math.SQRT1_2];
  const at = (s, t) => [H[0] + u[0] * s + n[0] * t, H[1] + u[1] * s + n[1] * t];
  const blade = new G();
  blade.poly([at(1.7, 0), at(3.4, 4.6), at(2.6, 5.4), at(-2.6, 5.4), at(-3.4, 4.6), at(-1.7, 0)], P.b);
  blade.map((x, y) => {
    const dx = x + 0.5 - H[0], dy = y + 0.5 - H[1];
    const t = dx * n[0] + dy * n[1], s2 = dx * u[0] + dy * u[1];
    if (t > 4.1) return P.a;
    if (t < 1.4) return P.c;
    return s2 < -1.2 ? P.c : s2 > 1.4 ? P.a : undefined;
  });
  over(g, blade);
  g.p(10, 5, '#7a4f25').p(11, 4, '#7a4f25');
  return outline ? g.ol(P.k) : g;
}

// ------------------------------------------------------------------ armour (port shapes)

function chainHoles(g, P) {
  // punch a regular hole pattern into the plates; the outline pass fills holes with P.k
  const holes = [];
  for (let y = 1; y < 15; y++) for (let x = 1; x < 15; x++) {
    if (!g.d[y][x] || (x + y) % 2 !== 0 || y % 2 !== 1) continue;
    const n4 = [[1, 0], [-1, 0], [0, 1], [0, -1]].every(([a, b]) => g.d[y + b][x + a]);
    if (n4 && g.d[y][x] !== P.a) holes.push([x, y]);
  }
  holes.forEach(([x, y]) => g.clear(x, y));
  return g;
}
function finishArmour(g, P, chain) {
  if (chain) chainHoles(g, P);
  return g.ol(P.k);
}
function helmet(P, chain) {
  const g = new G();
  g.r(2, 3, 12, 4, P.b).r(2, 7, 3, 4, P.b).r(11, 7, 3, 4, P.b);
  g.r(2, 3, 12, 1, P.a).r(2, 4, 2, 7, P.a).r(12, 4, 2, 7, P.c).r(4, 6, 8, 1, P.c);
  return finishArmour(g, P, chain);
}
function chestplate(P, chain) {
  const g = new G();
  g.r(1, 2, 5, 4, P.b).r(10, 2, 5, 4, P.b).r(4, 4, 8, 10, P.b);
  g.r(1, 2, 5, 1, P.a).r(10, 2, 5, 1, P.a).r(4, 5, 2, 8, P.a);
  g.r(10, 5, 2, 9, P.c).r(4, 12, 8, 2, P.c).l(8, 6, 8, 11, P.c);
  g.r(6, 4, 4, 1, P.c);
  g.clear(1, 5).clear(14, 5);
  return finishArmour(g, P, chain);
}
function leggings(P, chain) {
  const g = new G();
  g.r(3, 2, 10, 4, P.b).r(3, 6, 4, 8, P.b).r(9, 6, 4, 8, P.b);
  g.r(3, 2, 10, 1, P.a).r(3, 3, 1, 11, P.a).r(12, 3, 1, 11, P.c).r(6, 6, 1, 8, P.c).r(3, 4, 10, 1, P.c);
  return finishArmour(g, P, chain);
}
function boots(P, chain) {
  const g = new G();
  g.r(2, 6, 4, 6, P.b).r(10, 6, 4, 6, P.b).r(1, 10, 5, 3, P.b).r(10, 10, 5, 3, P.b);
  g.r(2, 6, 1, 7, P.a).r(10, 6, 1, 7, P.a).r(1, 12, 5, 1, P.c).r(10, 12, 5, 1, P.c);
  return finishArmour(g, P, chain);
}

// ------------------------------------------------------------------ faces (8x8 at 2x)

function face(rows, pal) {
  const g = new G();
  rows.forEach((row, j) => [...row].forEach((ch, i) => g.r(i * 2, j * 2, 2, 2, pal[ch])));
  return g;
}

// ------------------------------------------------------------------ sprites

const S = {};

// ---- A. mods: ported from the design

S.fps = () => { // port
  const g = new G();
  g.r(1, 2, 14, 10, '#4a4e57').r(1, 2, 14, 1, '#6a6f7a').r(2, 3, 12, 8, '#0d1219');
  const gr = [8, 7, 8, 9, 8, 5, 6, 4, 5, 4, 3, 4];
  gr.forEach((y, i) => { g.p(2 + i, y, '#8ef0c0'); g.p(2 + i, y + 1, '#2f6e57'); });
  g.r(2, 3, 12, 1, '#151c26');
  g.r(6, 12, 4, 1, '#3a3d44').r(4, 13, 8, 1, '#2a2c31');
  return g.ol('#08090b');
};
S.cps = () => { // port
  const g = new G();
  g.r(4, 3, 8, 11, '#e9eaee').r(11, 4, 1, 9, '#b9bcc4').r(4, 13, 8, 1, '#b9bcc4');
  g.clear(4, 3).clear(11, 3).clear(4, 13).clear(11, 13);
  g.r(4, 3, 4, 4, '#ffffff').clear(4, 3);
  g.l(8, 3, 8, 6, '#8a8e97').r(5, 7, 6, 1, '#c9ccd3');
  g.r(5, 4, 2, 2, '#ffd2c2');
  g.l(8, 2, 8, 0, '#6a6f7a');
  return g.ol('#16171b');
};
S.keystrokes = () => { // port
  const g = new G();
  g.r(1, 3, 14, 11, '#5e636d').r(1, 12, 14, 2, '#3e424a');
  g.r(2, 3, 12, 8, '#e4e6eb').r(2, 3, 12, 1, '#ffffff').r(13, 4, 1, 7, '#c3c7cf');
  g.rows(['x....x', 'x....x', 'x.xx.x', '.x..x.'], { x: '#2a2d33' }, 5, 5);
  g.clear(1, 3).clear(14, 3).clear(1, 13).clear(14, 13);
  return g.ol('#121317');
};
S.armor_status = () => chestplate(DIA); // port (design "armor")
S.potion_effects = () => potion(); // port (design "potion")
S.coordinates = () => { // port (design "coords")
  const g = new G();
  g.r(1, 1, 14, 14, '#c9a865').r(2, 2, 12, 12, '#efe0b0');
  g.r(3, 3, 4, 3, '#86b35e').r(4, 6, 3, 2, '#86b35e').r(9, 8, 4, 4, '#86b35e').r(10, 7, 2, 1, '#86b35e');
  g.r(7, 3, 6, 3, '#6a9ae0').r(3, 9, 5, 4, '#6a9ae0');
  g.l(9, 3, 11, 5, '#d23a2a').l(11, 3, 9, 5, '#d23a2a');
  g.p(2, 2, '#fff6d8').p(3, 2, '#fff6d8');
  return g.ol('#3a2a10');
};
S.combo = () => { // port
  const g = new G();
  g.rows([
    '.......a........',
    '......aa........',
    '......aba.......',
    '.....abba...a...',
    '....abbba..aa...',
    '....abcbba.aba..',
    '...abccbbaabba..',
    '...abcccbbabcba.',
    '..abccdccbbccba.',
    '..abcdddcbccba..',
    '..abccdddccbba..',
    '...abccdccbba...',
    '....abcccbba....',
    '.....aabbaa.....'], { a: '#e3402a', b: '#ff8a3d', c: '#ffc24a', d: '#fff3b0' }, 0, 1);
  return g.ol('#4a1206');
};
S.reach = () => { // port
  const g = new G();
  g.l(3, 12, 11, 4, '#9a6a3a'); g.l(4, 12, 12, 4, '#6e4a26');
  g.r(11, 2, 3, 1, '#e0e0e0').r(13, 2, 1, 3, '#e0e0e0').p(12, 3, '#b0b0b0').p(11, 3, '#9a9a9a').p(12, 4, '#9a9a9a');
  g.p(2, 11, '#ffffff').p(1, 12, '#ffffff').p(3, 14, '#d8d8d8').p(4, 13, '#ffffff').p(2, 13, '#d8d8d8').p(1, 14, '#bdbdbd').p(3, 13, '#bdbdbd');
  return g.ol('#1e1a16');
};
S.ping = () => { // port
  const g = new G();
  g.c(8, 8, 6, '#1d5a50').c(8, 8, 4.6, '#2c8a76').c(8.6, 8.6, 2.6, '#123d35').c(8.6, 8.6, 1.4, '#0a2622');
  g.p(5, 5, '#bff7e6').p(6, 5, '#7fe0c8').p(5, 6, '#7fe0c8').p(10, 11, '#3fae95');
  return g.ol('#06201b');
};
S.direction = () => { // port (design "compass")
  const g = new G();
  g.c(8, 8, 6.4, '#a9a9a9').ring(8, 8, 6.4, '#7a7a7a').c(8, 8, 4.8, '#dedede');
  g.l(8, 8, 11, 4, '#e8302a'); g.p(10, 5, '#ff7a70');
  g.l(8, 8, 5, 11, '#5a5a5a');
  g.p(8, 8, '#222');
  return g.ol('#262626');
};
S.toggle_sprint = () => { // port (design "sprint")
  const g = new G();
  g.r(6, 1, 5, 9, '#4aedd9').r(6, 9, 8, 4, '#4aedd9').r(6, 13, 9, 1, '#0f6b62');
  g.r(6, 1, 1, 12, '#c8fff4').r(10, 2, 1, 7, '#21b3a3').r(11, 10, 3, 1, '#21b3a3');
  g.r(6, 1, 5, 1, '#9ff7ea');
  g.clear(14, 9);
  g.l(1, 5, 3, 5, '#c9ccd3').l(2, 8, 4, 8, '#9a9da1').l(1, 11, 3, 11, '#c9ccd3');
  return g.ol('#0c3f3a');
};
S.hit_color = () => sword(RED); // port (design "hitcolor")
S.old_animations = () => { // port (design "animations")
  const g = sword(GOLD);
  [[1, 4], [2, 3], [3, 2], [4, 1]].forEach(([x, y]) => g.p(x, y, '#ffffff'));
  return g;
};
S.hitboxes = () => { // port
  const g = new G();
  const W = '#ffffff', D = '#7c8089';
  g.l(8, 1, 14, 4, W).l(14, 4, 8, 7, W).l(8, 7, 2, 4, W).l(2, 4, 8, 1, W);
  g.l(2, 4, 2, 11, W).l(14, 4, 14, 11, W).l(8, 7, 8, 14, W).l(2, 11, 8, 14, W).l(14, 11, 8, 14, W);
  g.l(8, 1, 8, 8, D);
  g.l(9, 3, 13, 1, '#ff5a5a');
  return g.ol('#0b0b0c');
};
S.nametags = () => { // port
  const g = new G();
  g.poly([[4, 4], [14, 4], [14, 12], [4, 12], [1.5, 8]], '#e9d6a0');
  g.r(4, 4, 10, 1, '#fff2cc').r(13, 5, 1, 7, '#c9ad6a').r(4, 11, 10, 1, '#c9ad6a');
  g.p(4, 8, '#3a2a10');
  g.r(7, 7, 5, 1, '#8a7040').r(7, 9, 4, 1, '#8a7040');
  g.l(4, 8, 1, 2, '#bdbdbd');
  return g.ol('#3a2a10');
};
S.crosshair = () => { // port
  const g = new G();
  g.ring(8, 8, 6, '#e9eaee');
  [[7, 1], [8, 1], [7, 14], [8, 14], [1, 7], [1, 8], [14, 7], [14, 8]].forEach(([x, y]) => g.clear(x, y));
  g.r(7, 3, 2, 3, '#ffffff').r(7, 10, 2, 3, '#ffffff').r(3, 7, 3, 2, '#ffffff').r(10, 7, 3, 2, '#ffffff');
  g.r(7, 7, 2, 2, '#ff6b5e');
  return g.ol('#0b0b0c');
};
S.fullbright = () => { // port
  const g = new G();
  g.r(7, 7, 2, 8, '#9a6a3a').r(8, 7, 1, 8, '#6e4a26');
  g.r(6, 3, 4, 4, '#ffb43a').r(7, 2, 2, 1, '#ffb43a').r(7, 4, 2, 2, '#fff3b0').p(7, 3, '#ffe27a');
  g.p(4, 2, '#fff3b0').p(11, 3, '#fff3b0').p(3, 6, '#ffe27a').p(12, 6, '#ffe27a').p(8, 0, '#fff3b0');
  return g.ol('#2a1606');
};
S.block_outline = () => { // port (design "outline")
  const g = new G();
  g.poly([[8, 1], [15, 4.5], [8, 8], [1, 4.5]], '#6cbc46');
  g.poly([[1, 4.5], [8, 8], [8, 15], [1, 11.5]], '#8f5e3a');
  g.poly([[8, 8], [15, 4.5], [15, 11.5], [8, 15]], '#6a4128');
  g.map((x, y, c) => (c === '#6cbc46' && (x * 7 + y * 3) % 5 === 0 ? '#5aa53a' : undefined));
  g.map((x, y, c) => (c !== '#6cbc46' && c !== '#5aa53a' && y <= 7 + Math.abs(x - 8) * 0.5 - 1 + 1.5 && y >= 5 ? (x < 8 ? '#5aa53a' : '#4a8a2e') : undefined));
  g.map((x, y, c) => (c === '#8f5e3a' && (x + y * 2) % 4 === 0 ? '#7a4e30' : undefined));
  g.ol('#f2f3f6');
  return g;
};
S.particles = () => { // port
  const g = new G();
  const star = (x, y, big) => {
    g.p(x, y, '#ffffff'); g.p(x - 1, y, '#ffe27a').p(x + 1, y, '#ffe27a').p(x, y - 1, '#ffe27a').p(x, y + 1, '#ffe27a');
    if (big) {
      g.p(x - 2, y, '#ffb43a').p(x + 2, y, '#ffb43a').p(x, y - 2, '#ffb43a').p(x, y + 2, '#ffb43a');
      g.p(x - 1, y - 1, '#fff3b0').p(x + 1, y + 1, '#fff3b0').p(x + 1, y - 1, '#fff3b0').p(x - 1, y + 1, '#fff3b0');
    }
  };
  star(5, 5, true); star(11, 9, true); star(4, 12, false); star(12, 3, false);
  return g;
};
S.time_weather = () => { // port (design "time": sun + moon)
  const g = new G();
  g.r(1, 1, 9, 9, '#ffd84a').r(2, 2, 7, 7, '#ffe98a').r(3, 3, 5, 5, '#fff7cc');
  g.r(7, 7, 8, 8, '#dfe4ff').r(8, 8, 2, 2, '#b4bbe0').r(11, 11, 3, 2, '#b4bbe0').r(12, 8, 2, 1, '#b4bbe0');
  g.r(7, 7, 8, 1, '#ffffff');
  return g.ol('#1a1a2a');
};
S.zoom = () => { // port (spyglass)
  const g = new G();
  for (let i = 0; i < 9; i++) { g.p(3 + i, 12 - i, '#d4834a'); g.p(4 + i, 12 - i, '#b0652e'); g.p(3 + i, 11 - i, '#f0a870'); g.p(4 + i, 13 - i, '#7e4518'); }
  g.r(10, 2, 4, 4, '#b0652e').r(11, 2, 3, 3, '#9fe6ff').p(11, 2, '#e8fbff').p(12, 3, '#5ab8e0');
  g.r(2, 12, 3, 3, '#7e4518').p(2, 12, '#b0652e');
  g.l(6, 8, 8, 10, '#ffd2a8');
  return g.ol('#2a1608');
};
S.freelook = () => { // port (eye)
  const g = new G();
  g.rows([
    '....wwwwww....',
    '..wwwwwwwwww..',
    '.wwwwiiiiwwww.',
    'wwwwiippiiwwww',
    'wwwwipphiiwwww',
    '.wwwwiiiiwwww.',
    '..wwwwwwwwww..',
    '....wwwwww....'], { w: '#f2f3f6', i: '#4a7ad0', p: '#10162a', h: '#ffffff' }, 1, 4);
  g.r(5, 4, 6, 1, '#d6d8de');
  return g.ol('#121317');
};
S.chat = () => { // port
  const g = new G();
  g.r(5, 1, 10, 7, '#8a8e97').clear(5, 1).clear(14, 1).p(12, 8, '#8a8e97');
  g.r(1, 5, 11, 7, '#f2f3f6').r(1, 11, 11, 1, '#c9ccd3').clear(1, 5).clear(11, 5).clear(1, 11).clear(11, 11);
  g.r(2, 12, 2, 1, '#c9ccd3').p(2, 13, '#c9ccd3');
  g.p(3, 8, '#25272d').p(6, 8, '#25272d').p(9, 8, '#25272d');
  return g.ol('#14151a');
};
S.server_address = () => { // port (design "server": a sign)
  const g = new G();
  g.r(1, 2, 14, 8, '#b98a4a').r(1, 2, 14, 1, '#d6a865').r(1, 9, 14, 1, '#8a6230');
  g.r(3, 4, 9, 1, '#3a2a10').r(3, 6, 7, 1, '#3a2a10').r(12, 4, 1, 1, '#3a2a10');
  g.r(7, 10, 2, 5, '#7a5228').r(8, 10, 1, 5, '#5a3816');
  return g.ol('#2a1c0a');
};

// ---- A. mods: new

S.clear_glass = () => {
  const g = new G();
  g.r(2, 2, 12, 12, '#bfe6f733'); // the pane itself is (almost) clear
  g.r(1, 1, 14, 1, '#eef8fc').r(1, 1, 1, 14, '#eef8fc');
  g.r(14, 1, 1, 14, '#86a9ba').r(1, 14, 14, 1, '#86a9ba');
  g.p(14, 1, '#c4dde8').p(1, 14, '#c4dde8');
  g.l(3, 8, 8, 3, '#ffffff').l(3, 9, 9, 3, '#ffffffaa');
  g.l(9, 12, 12, 9, '#e6f6fc');
  g.p(12, 4, '#ffffff');
  return g.ol('#1c2a33');
};
S.culling = () => {
  const g = new G();
  // a creeper behind a wall: only its eyes clear the top
  g.r(4, 1, 8, 7, '#5fbf4a');
  g.map((x, y) => ((x * 3 + y * 5) % 7 === 0 ? '#4aa03a' : (x * 5 + y) % 9 === 0 ? '#7ad65e' : undefined));
  g.r(4, 1, 8, 1, '#7ad65e').r(4, 2, 1, 6, '#7ad65e');
  g.r(5, 3, 2, 2, '#0f1a0d').r(9, 3, 2, 2, '#0f1a0d').r(7, 5, 2, 2, '#0f1a0d');
  // stone-brick wall
  g.r(1, 8, 14, 7, '#8e8e8e');
  g.r(1, 8, 14, 1, '#b4b4b4');
  g.r(1, 11, 14, 1, '#5c5c5c').r(1, 14, 14, 1, '#5c5c5c');
  g.r(7, 9, 1, 2, '#5c5c5c').r(4, 12, 1, 2, '#5c5c5c').r(11, 12, 1, 2, '#5c5c5c');
  g.p(1, 9, '#a6a6a6').p(8, 9, '#a6a6a6').p(1, 12, '#a6a6a6').p(5, 12, '#a6a6a6').p(12, 12, '#a6a6a6');
  return g.ol('#1a1d1a');
};
S.damage_tint = () => {
  const g = new G();
  // the screen with a low-health vignette, a heart at its centre
  g.r(1, 2, 14, 12, '#3a1212');
  g.r(1, 2, 14, 1, '#e8343a').r(1, 13, 14, 1, '#a3141c').r(1, 2, 1, 12, '#e8343a').r(14, 2, 1, 12, '#a3141c');
  g.r(2, 3, 12, 1, '#8a1c20').r(2, 12, 12, 1, '#6a1418').r(2, 3, 1, 10, '#8a1c20').r(13, 3, 1, 10, '#6a1418');
  g.p(1, 2, '#ff7a70').p(2, 3, '#c22a2e');
  g.clear(1, 2).clear(14, 2).clear(1, 13).clear(14, 13);
  g.rows([
    '.rr.rr.',
    'rwrrrrd',
    'rrrrrrd',
    '.rrrrd.',
    '..rrd..',
    '...d...'], { r: '#ff4a4a', w: '#ffc4c4', d: '#c2302a' }, 5, 5);
  return g.ol('#1a0606');
};
S.fog = () => {
  const g = new G();
  // a spruce sinking into banks of fog
  g.poly([[8, 0.6], [12.8, 8], [3.2, 8]], '#2f6b3a');
  g.poly([[8, 0.6], [8, 8], [3.2, 8]], '#3f8a4a');
  g.p(7, 3, '#5aa560').p(6, 5, '#5aa560').p(9, 6, '#24552e');
  const band = (x, y, w) => {
    g.r(x + 1, y, w - 2, 1, '#f4f7f9').r(x, y + 1, w, 1, '#c9d2da');
    g.p(x, y + 1, '#dfe5ea').p(x + w - 1, y + 1, '#aab6c0');
  };
  band(3, 7, 12);
  band(1, 10, 11);
  band(5, 13, 9);
  return g.ol('#1a2026');
};
S.fov = () => {
  const g = new G();
  // a wide viewing cone from an eye
  const ox = 4.5, oy = 8;
  g.c(ox, oy, 11.6, '#4aedd980', (x, y) => {
    const a = Math.atan2(y + 0.5 - oy, x + 0.5 - ox);
    return x >= 5 && Math.abs(a) <= 0.62;
  });
  const inside = (x, y) => g.d[y] && g.d[y][x];
  const edge = [];
  g.map((x, y) => {
    if (![[1, 0], [-1, 0], [0, 1], [0, -1]].some(([a, b]) => !inside(x + a, y + b))) return undefined;
    edge.push([x, y]);
    return undefined;
  });
  edge.forEach(([x, y]) => {
    const d = Math.hypot(x + 0.5 - ox, y + 0.5 - oy);
    g.p(x, y, d > 10.4 ? '#bff7ee' : y < oy ? '#9ff7ea' : '#21b3a3');
  });
  g.rows(['.www.', 'wwiww', '.www.'], { w: '#f2f3f6', i: '#10162a' }, 0, 7);
  g.p(1, 7, '#ffffff');
  return g.ol('#0c3f3a');
};
S.frame_limiter = () => {
  const g = new G();
  g.rows([
    '...wwwwwwwwww...',
    '...WWWWWWWWWW...',
    '...phggggggSp...',
    '...phsssssSgp...',
    '...p.hsssSg.p...',
    '...p..hsSg..p...',
    '...p...sS...p...',
    '...p...sg...p...',
    '...p..h.sg..p...',
    '...p.hg.sSg.p...',
    '...phgsssSSgp...',
    '...phssssSSSp...',
    '...wwwwwwwwww...',
    '...WWWWWWWWWW...',
  ], { w: '#b98a4a', W: '#7a5228', p: '#8a6230', g: '#cfe3f0', h: '#ffffff', s: '#f6d33f', S: '#c08a10' }, 0, 1);
  return g.ol('#2a1c0a');
};
S.gui_scale = () => {
  const g = new G();
  // an inventory slot holding a gem, with resize arrows at two corners
  g.r(4, 4, 8, 8, '#8b8b8b');
  g.r(4, 4, 8, 1, '#373737').r(4, 4, 1, 8, '#373737').r(4, 11, 8, 1, '#ffffff').r(11, 4, 1, 8, '#ffffff');
  g.rows(['.ab.', 'abbc', 'bbcc', '.cc.'], DIA, 6, 6);
  const A = '#ffffff', B = '#c9ccd3';
  g.r(1, 1, 4, 1, A).r(1, 1, 1, 4, A).p(2, 2, A).p(3, 3, B);
  g.r(11, 14, 4, 1, B).r(14, 11, 1, 4, B).p(13, 13, B).p(12, 12, A);
  return g.ol('#121317');
};
S.item_counter = () => {
  const g = new G();
  g.c(6.5, 7.5, 4.7, '#f6d33f').c(6.5, 7.5, 4.7, '#c08a10', (x, y) => x >= 8 && y >= 8);
  g.r(3, 5, 2, 2, '#fffbc2').p(3, 7, '#fff3a0');
  g.clear(6, 3);
  g.l(7, 3, 8, 1, '#6e4a26'); g.r(9, 1, 3, 2, '#5aa53a').p(9, 1, '#86d65e');
  g.ol('#4d3304');
  return count(g, '64', 7, 10);
};
S.memory = () => {
  const g = new G();
  g.r(1, 4, 14, 8, '#2f8a4a').r(1, 4, 14, 1, '#4ab36a').r(1, 4, 1, 8, '#4ab36a').r(14, 5, 1, 7, '#22683a');
  for (const x of [2, 6, 10]) {
    g.r(x + 0, 5, 3, 4, '#1d2026').r(x, 5, 3, 1, '#3a3f48');
  }
  g.r(2, 10, 12, 1, '#22683a');
  for (let x = 2; x <= 13; x++) if (x !== 8) g.r(x, 11, 1, 2, x % 2 ? '#f6d33f' : '#c08a10');
  g.clear(8, 11);
  g.clear(1, 12).clear(14, 12);
  g.r(1, 11, 1, 1, '#2f8a4a').r(14, 11, 1, 1, '#22683a');
  return g.ol('#0c2a16');
};
S.menu_blur = () => {
  const g = new G();
  // a blurred world behind a crisp frosted panel
  g.r(1, 1, 14, 14, '#86b4e0');
  g.r(1, 1, 14, 2, '#9cc4ea');
  g.c(4, 4, 2.6, '#f6e7a6');
  g.c(4, 4, 1.6, '#fff4c8');
  g.r(1, 9, 14, 1, '#80ae8a').r(1, 10, 14, 5, '#5f9c50').r(1, 13, 14, 2, '#4f8a42');
  g.clear(1, 1).clear(14, 1).clear(1, 14).clear(14, 14);
  g.r(6, 5, 8, 8, '#f4f8fcb8');
  g.r(6, 5, 8, 1, '#ffffff').r(6, 5, 1, 8, '#ffffffe0');
  g.r(8, 7, 4, 1, '#5e636d').r(8, 9, 3, 1, '#8a8e97').r(8, 11, 4, 1, '#8a8e97');
  return g.ol('#141a22');
};
S.momentum = () => {
  const g = new G();
  // a speedometer
  const cx = 8, cy = 10;
  g.c(cx, cy, 7, '#9a9da1', (x, y) => y <= 12);
  g.c(cx, cy, 7, '#c9ccd3', (x, y) => y <= 12 && y < 7 && x < 8);
  g.c(cx, cy, 5.8, '#1d2228', (x, y) => y <= 12);
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
    if (d > 5.8 || d < 4.6 || y > 10) continue;
    const a = Math.atan2(cx - (x + 0.5), cy - (y + 0.5)); // left = +pi/2
    g.p(x, y, a > 0.55 ? '#5fd35a' : a > -0.55 ? '#f6d33f' : '#ff5a4a');
  }
  g.l(8, 10, 11, 6, '#ffffff'); g.p(11, 6, '#ff6b5e');
  g.r(7, 10, 2, 2, '#c9ccd3').p(7, 10, '#ffffff');
  g.r(4, 12, 8, 1, '#6a6e75');
  return g.ol('#0b0b0c');
};
S.old_input = () => {
  // a pickaxe and a sword crossed: both hands answer at once
  const pick = flipX(pickaxe(IRON, false));
  const g = over(pick, sword(DIA, false));
  return g.ol('#141619');
};
S.overlay = () => {
  const g = new G();
  // a carved pumpkin
  g.r(1, 3, 14, 12, '#e38a1d');
  g.r(1, 3, 14, 1, '#f5a83a').r(1, 4, 1, 10, '#f5a83a').r(14, 4, 1, 11, '#b86410').r(1, 14, 14, 1, '#b86410');
  for (const x of [4, 8, 11]) g.r(x, 4, 1, 10, '#c97314');
  g.clear(1, 3).clear(14, 3).clear(1, 14).clear(14, 14);
  g.r(7, 1, 2, 2, '#5a7a2a').p(7, 1, '#7aa03a').p(9, 1, '#5a7a2a');
  const k = '#2a1404';
  g.rows([
    '...k....k...',
    '..kkk..kkk..',
    '............',
    '.k........k.',
    '.kkkkkkkkkk.',
    '..kk.kk.kk..',
  ], { k }, 2, 6);
  return g.ol('#3a1a04');
};
S.potion_counter = () => {
  const g = new G();
  const splash = (cx, cy, r, liq, liqD) => {
    g.r(Math.round(cx - 1), Math.round(cy - r - 2), 2, 1, '#9a6a3a');
    g.r(Math.round(cx - 1), Math.round(cy - r - 1), 2, 2, '#cfe3f0');
    g.c(cx, cy, r, '#d8ecf8');
    g.c(cx, cy, r - 0.8, liq, (x, y) => y >= cy - r * 0.35);
    g.c(cx, cy, r - 0.8, liqD, (x, y) => y >= cy + r * 0.25 && x >= cx);
    g.p(Math.round(cx - r + 1.2), Math.round(cy - 1), '#ffffff').p(Math.round(cx - r + 1.2), Math.round(cy), '#ffffff');
  };
  splash(5.5, 7, 3.9, '#c43a8c', '#7e1f5a');
  g.ol('#1f1a2a');
  splash(10.5, 10.5, 4.2, '#f2434b', '#a8202a');
  return g.ol('#1f1a2a');
};
S.saturation = () => {
  const g = new G();
  // a golden carrot, sparkling
  stroke(g, 2.6, 13.4, 10.4, 5.6, 0.7, 2.6, GOLD, (t, off, r) => {
    const band = Math.round(t * 9);
    if (band % 3 === 0 && off > -r * 0.3 && t > 0.15) return GOLD.c;
    return undefined;
  });
  g.rows(['....a.a', '...abaa', '..abba.', '.ab.a..', 'ab.....'], { a: '#86d65e', b: '#4aa03a' }, 9, 1);
  g.ol(GOLD.k);
  spark(g, 4, 4, '#ffffff', '#fff3b0');
  spark(g, 13, 11, '#ffffff', '#fff3b0');
  return g;
};
S.scoreboard = () => {
  const g = new G();
  g.r(2, 1, 12, 14, '#2c2f36').r(2, 1, 12, 3, '#1d1f24');
  g.r(4, 2, 3, 1, '#ffd84a').r(8, 2, 4, 1, '#ffd84a');
  const lines = [[5, 7], [7, 5], [9, 6], [11, 4], [13, 6]];
  lines.forEach(([y, w], i) => {
    g.r(3, y, w, 1, i === 2 ? '#7adfff' : '#e9eaee');
    g.p(12, y, '#ff5555');
  });
  return g.ol('#0b0b0c');
};
S.shiny_pots = () => {
  const g = potion('#f2434b', '#a8202a');
  g.map((x, y, c) => {
    if (c === '#1f1a2a') return undefined;
    const k = (x + y) % 7;
    if (k === 0) return mix(c, '#d49cff', 0.75);
    if (k === 1) return mix(c, '#b070ff', 0.4);
    return undefined;
  });
  spark(g, 2, 3, '#ffffff', '#c890ff');
  spark(g, 13, 5, '#ffffff', '#c890ff');
  g.p(13, 14, '#e6c8ff');
  return g;
};
S.stopwatch = () => {
  const g = new G();
  g.r(7, 1, 2, 2, '#c9ccd3').r(6, 1, 4, 1, '#e9eaee');
  g.l(12, 3, 13, 4, '#9a9da1');
  g.c(8, 9, 6, '#c9ccd3').c(8, 9, 6, '#8a8e97', (x, y) => x + y >= 18);
  g.c(8, 9, 4.6, '#f4f6f8').c(8, 9, 4.6, '#d6dae0', (x, y) => x + y >= 19);
  g.p(8, 5, '#5e636d').p(12, 9, '#5e636d').p(8, 13, '#5e636d').p(4, 9, '#5e636d');
  g.l(8, 9, 8, 6, '#25272d');
  g.l(8, 9, 10, 11, '#e8302a');
  g.p(8, 9, '#25272d');
  g.p(5, 5, '#ffffff').p(4, 6, '#ffffff');
  return g.ol('#1a1c20');
};
S.toggle_sneak = () => {
  const g = new G();
  // the sprint boot, crouched low, with a latch of down-chevrons
  g.r(7, 5, 5, 6, '#4aedd9').r(7, 10, 8, 3, '#4aedd9').r(7, 13, 8, 1, '#0f6b62');
  g.r(7, 5, 1, 8, '#c8fff4').r(11, 6, 1, 4, '#21b3a3').r(12, 11, 3, 1, '#21b3a3');
  g.r(7, 5, 5, 1, '#9ff7ea');
  g.clear(14, 10);
  g.rows(['aa..aa', '.aaaa.', '..aa..'], { a: '#e9eaee' }, 0, 2);
  g.rows(['aa..aa', '.aaaa.', '..aa..'], { a: '#9a9da1' }, 0, 7);
  return g.ol('#0c3f3a');
};
S.watermark = () => {
  const g = new G();
  g.r(1, 2, 14, 12, '#2a2d33').r(1, 2, 14, 1, '#40444d').r(1, 13, 14, 1, '#1d1f24').r(14, 3, 1, 10, '#232529');
  g.clear(1, 2).clear(14, 2).clear(1, 13).clear(14, 13);
  g.ring(8, 8, 5.1, '#edeeef');
  [[4, 4], [11, 4], [4, 11], [11, 11]].forEach(([x, y]) => g.p(x, y, '#2a2d33'));
  return g.ol('#0b0b0c');
};

// ---- B. UI / item extras

S.logo = () => { // port
  const g = new G();
  g.ring(8, 8, 6.6, '#edeeef');
  [[7, 1], [8, 1], [7, 14], [8, 14], [1, 7], [1, 8], [14, 7], [14, 8], [3, 3], [12, 12], [12, 3], [3, 12]].forEach(([x, y]) => g.clear(x, y));
  return g;
};
S.pin = () => { // port
  const g = new G();
  g.c(8, 5.5, 4.2, '#e9eaee').r(6, 3, 2, 2, '#ffffff').c(8, 5.5, 4.2, '#b9bcc4', (x, y) => x >= 9 && y >= 6);
  g.l(8, 9, 8, 14, '#9a9da1'); g.r(5, 9, 6, 1, '#c9ccd3');
  return g.ol('#121317');
};
S.sword = () => sword(DIA); // port
S.goldsword = () => sword(GOLD); // port
S.bow = () => { // port
  const g = new G();
  g.l(4, 1, 1, 4, '#9a6a3a'); g.l(5, 1, 11, 3, '#9a6a3a'); g.l(11, 3, 13, 5, '#9a6a3a'); g.l(13, 5, 14, 11, '#9a6a3a'); g.l(14, 11, 12, 14, '#9a6a3a');
  g.l(5, 2, 11, 4, '#6e4a26'); g.l(12, 5, 13, 11, '#6e4a26');
  g.l(2, 3, 12, 13, '#e9eaee');
  g.l(5, 11, 11, 5, '#bdbdbd'); g.p(11, 4, '#e0e0e0').p(12, 4, '#e0e0e0').p(12, 5, '#e0e0e0'); g.p(4, 12, '#ffffff').p(4, 11, '#ffffff').p(5, 12, '#ffffff');
  return g.ol('#1e1a16');
};
S.gapple = () => { // port
  const g = new G();
  g.c(8, 9.5, 5.6, '#f6d33f').c(8, 9.5, 5.6, '#c08a10', (x, y) => x >= 10 && y >= 9).r(5, 6, 2, 3, '#fffbc2').p(5, 9, '#fff3a0');
  g.clear(8, 4).clear(7, 4);
  g.l(8, 5, 9, 2, '#6e4a26'); g.r(10, 2, 3, 2, '#5aa53a').p(10, 2, '#86d65e');
  return g.ol('#4d3304');
};
S.wool = () => { // port
  const g = new G();
  g.poly([[8, 1], [15, 4.5], [8, 8], [1, 4.5]], '#5a7ae0');
  g.poly([[1, 4.5], [8, 8], [8, 15], [1, 11.5]], '#3e5ac4');
  g.poly([[8, 8], [15, 4.5], [15, 11.5], [8, 15]], '#2e44a0');
  g.map((x, y, c) => ((x * 5 + y * 7) % 6 === 0 ? (c === '#5a7ae0' ? '#7a96f0' : c === '#3e5ac4' ? '#4e6ad4' : '#3a52b4') : undefined));
  return g.ol('#121a3a');
};
S.rod = () => { // port
  const g = new G();
  g.l(2, 14, 12, 2, '#9a6a3a'); g.l(3, 14, 13, 2, '#6e4a26');
  g.l(13, 2, 13, 10, '#d8d8d8'); g.r(12, 10, 2, 2, '#c2302a').p(12, 10, '#ff6b5e');
  return g.ol('#1e1a16');
};
S.pearl = () => {
  const g = new G();
  g.c(8, 8, 5.8, '#145a4c');
  g.c(8, 8, 5.8, '#0c3e36', (x, y) => x + y >= 19);
  g.c(7.7, 7.7, 4.2, '#23876a');
  g.c(7.5, 7.5, 3.0, '#36b98a');
  g.c(7.4, 7.4, 1.9, '#6ff0b0');
  g.p(7, 7, '#d6ffe8');
  g.p(4, 5, '#bff7e0').p(5, 4, '#bff7e0').p(4, 6, '#5fd3a6');
  g.p(10, 11, '#1b7058').p(11, 9, '#1b7058').p(6, 11, '#1b7058');
  return g.ol('#041c1a');
};
S.steak = () => { // port
  const g = new G();
  g.c(9, 7, 5, '#b8562a').c(9, 7, 3.6, '#d9733a').r(8, 5, 3, 2, '#f09a5a');
  g.l(5, 10, 2, 13, '#f2eadb'); g.l(4, 10, 2, 12, '#f2eadb'); g.p(1, 13, '#ffffff').p(2, 14, '#ffffff');
  return g.ol('#3a1606');
};
S.steve = () => face(
  ['hhhhhhhh', 'hhhhhhhh', 'hsssssth', 'swbssbws', 'sssnnsss', 'ssmmmmss', 'smssssms', 'tsssssst'],
  { h: '#3a2a1e', s: '#c69c7a', t: '#b4896a', w: '#ffffff', b: '#4a5ac8', n: '#9a6a4e', m: '#7a4e36' },
);
S.alex = () => face(
  ['oooooooo', 'ooooooOo', 'ossssssO', 'swgssgws', 'ssssssss', 'sssnnsss', 'ssssssss', 'ossssssO'],
  { o: '#e07a2a', O: '#c0601a', s: '#f0c8a0', w: '#ffffff', g: '#3a8a4a', n: '#d0907a' },
);
S.zombie = () => face(
  ['gggGgggg', 'gGggggGg', 'gggggggg', 'gkkggkkg', 'gggddggg', 'ggGkkGgg', 'gggggggg', 'gGggggGg'],
  { g: '#5a9a4a', G: '#4a8a3a', k: '#1a2a1a', d: '#3a6a2e' },
);
S.tnt = () => {
  const g = new G();
  g.poly([[8, 1], [15, 4.5], [8, 8], [1, 4.5]], '#c9c9c9');
  g.poly([[1, 4.5], [8, 8], [8, 15], [1, 11.5]], '#db2c22');
  g.poly([[8, 8], [15, 4.5], [15, 11.5], [8, 15]], '#a81d16');
  g.map((x, y, c) => {
    if (c === '#c9c9c9') return undefined;
    const left = x < 8;
    const top = left ? 4.5 + (x + 0.5 - 1) * 0.5 : 8 - (x + 0.5 - 8) * 0.5;
    const rel = y + 0.5 - top;
    if (rel > 2.4 && rel < 4.6) {
      if (rel > 3.1 && rel < 4.0 && x !== 1 && x !== 7 && x !== 8 && x !== 14) return '#4a4a4a';
      return left ? '#f2f2f2' : '#cfcfcf';
    }
    if (x % 2 === 0) return left ? '#c0241b' : '#8f1712';
    return undefined;
  });
  g.map((x, y, c) => (c === '#c9c9c9' && (x + y) % 3 === 0 ? '#b4b4b4' : undefined));
  g.r(7, 4, 2, 1, '#3a3a3a').p(7, 3, '#e0e0e0').p(8, 3, '#a8a8a8');
  return g.ol('#2a0806');
};
S.bed = () => {
  const g = new G();
  g.rows([
    '.ppppPPPPPPPPPP.',
    '.qqqqrrrrrrrrrr.',
    '.qqqqrrrrrrrrrR.',
    '.qqqqRRRRRRRRRR.',
    '.wwwwwwwwwwwwww.',
    '.WWWWWWWWWWWWWW.',
    '.LL..........LL.',
  ], { p: '#ffffff', q: '#dfe2e8', P: '#ff6b5e', r: '#d23a3a', R: '#a32828', w: '#b98a4a', W: '#7a5228', L: '#5a3816' }, 0, 5);
  g.p(1, 6, '#ffffff').p(5, 6, '#ff8a7e');
  return g.ol('#2a0c08');
};
S.axe = () => axe({ a: '#f4f4f4', b: '#c4c4c4', c: '#868686', k: '#303030' });
S.pickaxe = () => pickaxe(DIA);
S.snowball = () => {
  const g = new G();
  g.c(8, 8.5, 5.4, '#eef4fa');
  g.c(8, 8.5, 5.4, '#c4d4e4', (x, y) => x + y >= 19);
  g.r(5, 5, 2, 2, '#ffffff').p(4, 7, '#ffffff');
  g.p(9, 6, '#d6e2ee').p(6, 10, '#d6e2ee').p(10, 9, '#d6e2ee').p(11, 11, '#a8bccf').p(8, 12, '#a8bccf');
  return g.ol('#33435a');
};
S.fire = () => {
  const g = new G();
  g.rows([
    '........a.......',
    '.......aa...a...',
    '...a...aba..aa..',
    '...aa.abba.aba..',
    '..aba.abcba.aba.',
    '..abaabccba.abba',
    '..abbabcdcbaabca',
    '.abcbbccdcbbccba',
    '.abccbcdddccdcba',
    '.abcddcdddcddcba',
    '..abcdddddddcba.',
  ], { a: '#d8402a', b: '#ff7a2a', c: '#ffb43a', d: '#fff3b0' }, 0, 1);
  g.rows([
    '.eLLLLLLLLLLLLe.',
    '..llllllllllll..',
  ], { L: '#7a4f25', l: '#4a2e14', e: '#c9a064' }, 0, 12);
  g.r(3, 14, 10, 1, '#3a2410');
  return g.ol('#2a0c04');
};
S.potion = () => potion();

// ---- C. armour pieces per material

const MATERIALS = { leather: LEATHER, chainmail: CHAIN, iron: IRON, gold: GOLD, diamond: DIA };
const SLOTS = { helmet, chestplate, leggings, boots };
for (const [slot, fn] of Object.entries(SLOTS))
  for (const [mat, P] of Object.entries(MATERIALS)) S[`armor_${slot}_${mat}`] = () => fn(P, mat === 'chainmail');

// ---- D. held weapons

S.sword_wood = () => sword(WOOD);
S.sword_stone = () => sword(STONE);
S.sword_iron = () => sword(IRON);
S.sword_gold = () => sword(GOLD);
S.sword_diamond = () => sword(DIA);

// ---- E. potion effect icons (1.8.9 ids, vanilla liquid colours)

const EFFECTS = [
  '#7CAFC6', '#5A6C81', '#D9C043', '#4A4217', '#932423', '#F82423', '#430A09', '#22FF4C',
  '#551D4A', '#CD5CAB', '#99453A', '#E49A3A', '#2E5299', '#7F8392', '#1F1F23', '#1F1FA1',
  '#587653', '#484D48', '#4E9331', '#352A27', '#F87D23', '#2552A5', '#F82423',
];
/** Colours the build must keep exact when it fits the palette (the effect liquids). */
export const PROTECTED_COLOURS = [];
EFFECTS.forEach((base, i) => {
  const b = base.toLowerCase();
  // very dark liquids are lifted a touch so they still read inside the glass
  const liq = luma(b) < 0.09 ? lighten(b, 0.18) : b;
  const liqD = luma(b) < 0.09 ? b : darken(b, 0.38);
  PROTECTED_COLOURS.push(liq, liqD);
  S[`fx${i + 1}`] = () => potion(liq, liqD);
});

// ---- F. cosmetic slot glyphs

S.cos_cape = () => {
  const g = new G();
  g.poly([[3.5, 2], [12.5, 2], [14, 14.5], [2, 14.5]], PURPLE.b);
  g.poly([[3.5, 2], [6, 2], [5, 14.5], [2, 14.5]], PURPLE.a);
  g.l(8, 4, 8, 14, PURPLE.c).l(11, 4, 12, 14, PURPLE.c).l(12, 4, 13, 14, PURPLE.c);
  g.r(3, 2, 10, 1, PURPLE.c);
  g.p(4, 2, GOLD.b).p(11, 2, GOLD.b).p(4, 3, GOLD.c).p(11, 3, GOLD.c);
  g.clear(4, 14).clear(9, 14);
  return g.ol(PURPLE.k);
};
S.cos_back = () => {
  const half = new G();
  half.rows([
    '.aaa....',
    'abbbaa..',
    'abbbbbaa',
    'abbbbbbb',
    'acbbbbbb',
    '.acbcbbb',
    '.acbcbcb',
    '..acbcbc',
    '..a.acbc',
    '....a.ac',
    '......a.',
  ], { a: '#ffffff', b: '#dfe5ee', c: '#9aa6b8' }, 0, 2);
  for (let y = 0; y < 16; y++) half.clear(7, y);
  return over(copy(half), flipX(half)).ol('#1e2633');
};

S.cos_head = () => {
  const g = new G();
  g.r(4, 2, 8, 9, '#3a3a46');
  g.r(4, 2, 8, 1, '#5a5a6a').r(4, 3, 2, 8, '#4a4a58').r(10, 3, 2, 8, '#26262e');
  g.r(4, 8, 8, 2, PURPLE.b).r(4, 8, 2, 2, PURPLE.a).r(10, 8, 2, 2, PURPLE.c);
  g.r(1, 11, 14, 2, '#3a3a46').r(1, 11, 14, 1, '#5a5a6a').r(13, 12, 2, 1, '#26262e');
  g.clear(1, 12).clear(14, 12);
  return g.ol('#0c0c10');
};
S.cos_face = () => {
  const g = new G();
  g.rows([
    '.aaa........aaa.',
    'abbbaa....aabbba',
    'abkkbbaaaabbkkbc',
    'abkkkbbbbbbkkkbc',
    '.abkkbbbbbbkkbc.',
    '..abbbc..abbbc..',
    '...ccc....ccc...',
  ], { a: GOLD.b, b: PURPLE.b, c: GOLD.c, k: '#000' }, 0, 5);
  g.map((x, y, c) => (c === '#000' ? null : undefined));
  g.map((x, y, c) => (c === PURPLE.b && y <= 6 ? PURPLE.a : c === PURPLE.b && y >= 9 ? PURPLE.c : undefined));
  return g.ol(GOLD.k);
};
S.cos_halo = () => {
  const g = new G();
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const e = ((x + 0.5 - 8) / 6.6) ** 2 + ((y + 0.5 - 7) / 3) ** 2;
    if (e > 1 || e < 0.42) continue;
    const front = y + 0.5 > 7;
    g.p(x, y, front ? (e > 0.75 ? '#f6d33f' : '#fffbc2') : e > 0.75 ? '#c08a10' : '#f6d33f');
  }
  g.ol('#4d3304');
  spark(g, 2, 12, '#ffffff', '#fff3b0');
  spark(g, 13, 12, '#ffffff', '#fff3b0');
  g.p(8, 13, '#fff3b0');
  return g;
};
S.cos_trail = () => {
  const g = new G();
  // a trail of sparkles, shrinking as it falls behind
  spark(g, 11, 4, '#ffffff', '#bff7ee', true);
  g.p(10, 3, '#4aedd9').p(12, 3, '#4aedd9').p(10, 5, '#4aedd9').p(12, 5, '#4aedd9');
  g.p(13, 4, '#4aedd9').p(9, 4, '#4aedd9').p(11, 2, '#4aedd9').p(11, 6, '#4aedd9');
  g.p(8, 7, '#4aedd9').p(9, 7, '#21b3a3').p(8, 8, '#21b3a3');
  spark(g, 5, 10, '#d9fff8', '#4aedd9');
  g.p(3, 12, '#21b3a3').p(2, 13, '#21b3a3').p(1, 14, '#177a70');
  spark(g, 13, 10, '#d9fff8', '#21b3a3');
  g.p(6, 4, '#4aedd9').p(9, 13, '#21b3a3');
  return g;
};
S.cos_badge = () => {
  const g = new G();
  g.poly([[2.5, 1.5], [13.5, 1.5], [13.5, 8.5], [8, 14.8], [2.5, 8.5]], DIA.b);
  g.poly([[2.5, 1.5], [8, 1.5], [8, 14.8], [2.5, 8.5]], '#7ff2e2');
  g.poly([[2.5, 1.5], [13.5, 1.5], [13.5, 2.5], [2.5, 2.5]], DIA.a);
  g.map((x, y, c) => (x >= 12 && c !== DIA.a ? DIA.c : undefined));
  g.rows(['w...w', 'w...w', '.w.w.', '..w..'], { w: '#ffffff' }, 6, 5);
  g.p(8, 9, '#d9fff8');
  return g.ol(DIA.k);
};
S.cos_emote = () => {
  const g = new G();
  const s = '#f0c8a0', d = '#c69c7a', h = '#ffe4c8';
  g.r(4, 3, 2, 6, s).r(7, 1, 2, 8, s).r(10, 2, 2, 7, s).r(13, 5, 2, 5, s);
  g.r(4, 8, 11, 5, s).r(5, 13, 9, 2, s);
  g.r(1, 7, 2, 3, s).r(2, 9, 2, 3, s);
  g.r(4, 3, 1, 6, h).r(7, 1, 1, 8, h).r(10, 2, 1, 7, h).r(13, 5, 1, 5, h).r(1, 7, 1, 2, h);
  g.r(5, 12, 9, 1, d).r(14, 9, 1, 4, d).r(13, 13, 1, 2, d);
  g.clear(4, 12).clear(14, 12);
  g.ol('#3a2414');
  g.p(1, 2, '#c9ccd3').p(2, 1, '#c9ccd3').p(1, 4, '#9a9da1').p(14, 1, '#c9ccd3').p(15, 2, '#9a9da1');
  return g;
};
S.lock = () => {
  const g = new G();
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const d = Math.hypot(x + 0.5 - 8, y + 0.5 - 6.5);
    if (y <= 7 && d <= 4.6 && d >= 2.6) g.p(x, y, x < 8 ? '#e9eaee' : '#9a9da1');
  }
  g.r(3, 7, 10, 8, GOLD.b).r(3, 7, 10, 1, GOLD.a).r(3, 8, 1, 7, GOLD.a).r(12, 8, 1, 7, GOLD.c).r(3, 14, 10, 1, GOLD.c);
  g.clear(3, 14).clear(12, 14);
  g.r(7, 9, 2, 2, '#4d3304').r(7, 11, 2, 2, '#6e4a10').p(8, 12, '#4d3304').p(7, 12, '#4d3304');
  return g.ol('#2a1c06');
};

// ---- G. appended after the cosmetics merge (registry 13): the `cosmetics` mod itself — a
// cape under a halo, the two things the mod draws on players.
S.cosmetics = () => {
  const g = new G();
  g.poly([[4.5, 6], [11.5, 6], [13, 15], [3, 15]], PURPLE.b);
  g.poly([[4.5, 6], [6.5, 6], [5.5, 15], [3, 15]], PURPLE.a);
  g.l(8, 7, 8, 14, PURPLE.c).l(10, 7, 11, 14, PURPLE.c);
  g.r(4, 6, 8, 1, PURPLE.c).p(5, 6, GOLD.b).p(10, 6, GOLD.b);
  g.clear(5, 15).clear(9, 15);
  g.ol(PURPLE.k);
  const halo = new G();
  for (let y = 0; y < 6; y++) for (let x = 0; x < 16; x++) {
    const e = ((x + 0.5 - 8) / 5.6) ** 2 + ((y + 0.5 - 2.6) / 2.3) ** 2;
    if (e > 1 || e < 0.34) continue;
    halo.p(x, y, y + 0.5 > 2.6 ? (e > 0.7 ? '#f6d33f' : '#fffbc2') : e > 0.7 ? '#c08a10' : '#f6d33f');
  }
  halo.ol('#4d3304');
  return over(g, halo);
};

// ---- H. the cosmetics catalog (pvp/schema/cosmetics.json). Sprite name = item id.
// Each is drawn AS WORN, front view, centred: the same cell is the store-card icon and the
// overlay laid on the paper doll (cape/wings behind the body, hat on the head, mask on the
// face, halo above, trail by the feet, badge beside a nameplate). Tintable items are drawn
// in neutral greys so a runtime multiply tint reads. Then the paper doll itself.

const VIO = { h: '#f4f0ff', a: '#e4ddff', b: '#9f8bff', c: '#6a55d6', d: '#3e2f8f', e: '#261c5c', k: '#120c2e' };
const CORAL = { h: '#fff0c8', y: '#ffc46b', a: '#ffd0b8', b: '#ff9e7a', c: '#e0623e', d: '#9c3a22', e: '#5c1f12', k: '#2e0e06' };
const ICE = { h: '#f2fcff', a: '#c4f0ff', b: '#7adfff', c: '#3aa6d6', d: '#1f6a96', e: '#13405e', k: '#081e30' };
const MINT = { a: '#d4f8e6', b: '#7ae0b0', c: '#3fae7e', d: '#22704f', k: '#0c3324' };
const NEU = { w: '#f4f5f6', a: '#dfe1e5', b: '#b8bbc1', c: '#8a8d94', d: '#5c5f66', e: '#3a3c42', f: '#26272c', k: '#141518' };

/** Tiny deterministic PRNG so noise patterns are identical every build. */
function rng(seed) {
  let s = seed >>> 0;
  return () => {
    s = (s + 0x6d2b79f5) >>> 0;
    let t = s;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/** A Minecraft-cape banner, 10 x 14 inside x 3..12, y 1..14; fill(i, j) colours each cell. */
function cape(fill, k) {
  const g = new G();
  for (let j = 0; j < 14; j++) for (let i = 0; i < 10; i++) {
    const c = fill(i, j);
    if (c) g.p(3 + i, 1 + j, c);
  }
  return g.ol(k);
}

/** The VOID ring as the logo draws it: a one-cell pixel circle (corners touch diagonally).
 *  size 6 / 8 are one pixel thick; 12 is the logo at double scale. Lit top-left, shaded bottom-right. */
const RINGS = {
  6: ['.####.', '#....#', '#....#', '#....#', '#....#', '.####.'],
  8: ['..####..', '.#....#.', '#......#', '#......#', '#......#', '#......#', '.#....#.', '..####..'],
  12: ['....####....', '....####....', '..##....##..', '..##....##..', '##........##', '##........##',
    '##........##', '##........##', '..##....##..', '..##....##..', '....####....', '....####....'],
};
function voidRing(g, ox, oy, size, light, shade) {
  RINGS[size].forEach((row, j) => [...row].forEach((ch, i) => {
    if (ch === '#') g.p(ox + i, oy + j, i + j >= size ? shade : light);
  }));
  return g;
}

// ---- capes

S['cell-cape'] = () => {
  const rowCol = [VIO.d, VIO.c, VIO.b, VIO.a];
  const rowHi = [VIO.c, VIO.b, VIO.a, VIO.h];
  const rowLo = [VIO.e, VIO.d, VIO.c, VIO.b];
  return cape((i, j) => {
    const ci = i % 3, cj = (j - 1) % 3;
    const r = Math.floor((j - 1) / 3);
    if (i >= 1 && i <= 8 && ci !== 0 && j >= 1 && j <= 11 && cj !== 2) {
      return ci === 1 && cj === 0 ? rowHi[r] : ci === 2 && cj === 1 ? rowLo[r] : rowCol[r];
    }
    if (j === 13) return i === 9 ? VIO.d : VIO.c;
    return j >= 9 ? '#30246e' : j >= 5 ? VIO.e : '#1c1448';
  }, VIO.k);
};

S['void-cape'] = () => {
  const g = cape((i, j) => (i === 9 ? '#0a0a0c' : i === 0 || j === 0 ? '#1c1c22' : '#101014'), '#4a4a56');
  voidRing(g, 4, 4, 8, NEU.w, NEU.b);
  g.r(10, 1, 2, 2, VIO.b).p(10, 1, VIO.a).p(11, 2, VIO.c);
  [5, 7, 8, 10].forEach((x) => g.p(x, 13, '#4a4a56'));
  return g;
};

S['ring-cape'] = () => {
  const g = cape((i, j) => (i === 0 ? '#a2a5aa' : i === 9 ? '#75787d' : j === 0 ? '#7f828a' : '#8f9299'), NEU.f);
  voidRing(g, 4, 3, 8, NEU.w, NEU.a);
  return g;
};

S['origin-cape'] = () => cape((i, j) => {
  const left = i < 5;
  let c = left ? NEU.b : NEU.c;
  if (j === 0) c = darken(c, 0.12);
  if (i === 0) c = NEU.a;
  if (i === 9) c = NEU.d;
  return c;
}, '#22242a');

S['dusk-cape'] = () => {
  const bands = ['#231c50', '#6a3d92', '#ef8458'];
  const band = (i, j) => {
    if (j <= 3) return 0;
    if (j === 4) return (i % 2 === 0) ? 1 : 0;
    if (j <= 8) return 1;
    if (j === 9) return (i % 2 === 1) ? 2 : 1;
    return 2;
  };
  const g = cape((i, j) => {
    const b = band(i, j);
    if (b === 2 && j === 13) return '#ffb27a';
    return bands[b];
  }, '#100a26');
  [[5, 2], [10, 4], [7, 5], [11, 1]].forEach(([x, y]) => g.p(x, y, '#fff3d0'));
  g.p(9, 7, '#c9b8ff');
  return g;
};

S['static-cape'] = () => {
  const rnd = rng(1337);
  const greys = [NEU.e, NEU.d, NEU.c, NEU.b, NEU.a, NEU.w];
  const g = cape((i, j) => {
    const v = rnd();
    const lift = j === 6 || j === 7 ? 0.25 : 0;
    if (v < 0.42 - lift) return NEU.f;
    return greys[Math.min(greys.length - 1, Math.floor(rnd() * greys.length + lift * 4))];
  }, '#0a0a0c');
  g.p(5, 4, VIO.b).p(10, 10, ICE.b).p(7, 12, CORAL.b);
  return g;
};

// ---- back

S['ember-wings'] = () => {
  // raised wings: long dark feathers hang from a warm leading edge and glow at the tips
  const half = new G();
  const top = [3, 2, 2, 3, 3, 4, 4, 5];
  const bot = [10, 12, 11, 12, 10, 10, 8, 7];
  for (let x = 0; x < 8; x++) for (let y = top[x]; y <= bot[x]; y++) {
    const k = y - top[x], n = bot[x] - y, t = k / (bot[x] - top[x]);
    const odd = x % 2 === 1;
    let c;
    if (n === 0) c = x >= 6 ? CORAL.c : CORAL.y;
    else if (k === 0) c = x <= 2 ? CORAL.b : CORAL.c;
    else if (x >= 6) c = t < 0.6 ? CORAL.e : CORAL.d;
    else if (t < 0.4) c = odd ? CORAL.e : CORAL.d;
    else if (n === 1) c = odd ? CORAL.b : CORAL.a;
    else c = odd ? CORAL.d : CORAL.c;
    half.p(x, y, c);
  }
  for (let y = 0; y < 16; y++) half.clear(7, y);
  return over(copy(half), flipX(half)).ol(CORAL.k);
};

S['void-wings'] = () => {
  // a bat wing: bright arm to the wrist, three finger veins, dark glass membrane scalloped between
  const half = new G();
  half.poly([[7.6, 4.2], [1, 0.4], [0, 1.4], [0, 11.6], [1.3, 8.6], [3.5, 13.6], [4.4, 9.4], [6.5, 12.6], [7.6, 8.8]], '#221a56');
  half.map((x, y) => (y >= 8 ? '#2a2068' : undefined));
  half.l(1, 2, 3, 13, VIO.c).l(2, 2, 6, 12, VIO.c).l(0, 3, 0, 10, VIO.d);
  half.l(7, 4, 1, 1, VIO.b).p(6, 4, VIO.a).p(5, 3, VIO.a);
  half.p(1, 1, VIO.h).p(0, 1, VIO.a).p(0, 11, VIO.b).p(3, 13, VIO.b).p(6, 12, VIO.b);
  for (let y = 0; y < 16; y++) half.clear(7, y);
  return over(copy(half), flipX(half)).ol(VIO.k);
};

S['orbit-ring'] = () => {
  const g = new G();
  g.ring(8, 8, 6.4, ICE.e);
  const n = 8;
  for (let k = 0; k < n; k++) {
    const a = -Math.PI / 2 + (k / n) * Math.PI * 2;
    const x = Math.round(8 + Math.cos(a) * 5.9 - 1), y = Math.round(8 + Math.sin(a) * 5.9 - 1);
    const lit = k / n; // brightest at the top, fading round the circle
    const P = lit < 0.3 ? [ICE.h, ICE.b, ICE.c] : lit < 0.65 ? [ICE.a, ICE.c, ICE.d] : [ICE.b, ICE.d, ICE.e];
    g.r(x, y, 2, 2, P[1]).p(x, y, P[0]).p(x + 1, y + 1, P[2]);
  }
  return g.ol(ICE.k);
};

// ---- head

S['monarch-crown'] = () => {
  const g = new G();
  g.rows([
    'b...b....b...b',
    'bb.bbb..bbb.bb',
    'bbbbbbbbbbbbbb',
    'aaaaaaaaaaaaaa',
    'bbbbbbbbbbbbbb',
    'cccccccccccccc',
  ], { a: GOLD.a, b: GOLD.b, c: GOLD.c }, 1, 6);
  g.map((x, y, c) => (x >= 12 && c === GOLD.b ? '#d9a822' : undefined));
  g.r(7, 9, 2, 2, CORAL.b).p(7, 9, CORAL.h).p(8, 10, CORAL.c);
  g.p(3, 10, CORAL.c).p(12, 10, CORAL.c);
  g.ol(GOLD.k);
  g.p(1, 6, GOLD.a).p(14, 6, GOLD.a);
  return g;
};

S['wool-beanie'] = () => {
  const g = new G();
  g.rows([
    '....bbbbbb....',
    '..baaaabbbbb..',
    '.baaabbbbbbbc.',
    '.babbbbbbbbbc.',
    'bbbbbbbbbbbbcc',
    'wwwwwwwwwwwwww',
    'ararararararar',
    'ararararararar',
  ], { a: NEU.a, b: NEU.b, c: NEU.c, w: NEU.w, r: NEU.c }, 1, 4);
  return g.ol(NEU.e);
};

S['dapper-top-hat'] = () => {
  const g = new G();
  g.rows([
    '..aaaaaaaa..',
    '..bccccccd..',
    '...bccccd...',
    '...bccccd...',
    '...bccccd...',
    '...bccccd...',
    '...wwwwwv...',
    '...wwwwwv...',
    '..bccccccd..',
    'bbcccccccddd',
  ], { a: '#5a5a6a', b: '#4a4a58', c: '#33333e', d: '#22222a', w: NEU.w, v: NEU.b }, 2, 2);
  return g.ol('#0a0a0e');
};

// ---- halo

S['seraph-halo'] = () => {
  const g = new G();
  g.rows([
    '....cccccc....',
    '..cc......cc..',
    '.b..........b.',
    'b............b',
    'y............y',
    '.yy........yy.',
    '...hhhhhhhh...',
    '.....yyyy.....',
  ], { c: CORAL.c, b: CORAL.b, y: CORAL.y, h: CORAL.h }, 1, 4);
  g.ol(CORAL.e);
  spark(g, 3, 3, '#ffffff', CORAL.h);
  spark(g, 13, 12, '#ffffff', CORAL.h);
  g.p(12, 2, CORAL.y);
  return g;
};

S['void-halo'] = () => {
  const g = new G();
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const e = ((x + 0.5 - 8) / 6.6) ** 2 + ((y + 0.5 - 8) / 3) ** 2;
    if (e > 1 || e < 0.5) continue;
    const front = y + 0.5 > 8;
    if (((x + (front ? 0 : 1)) % 3) === 0) continue;
    g.p(x, y, front ? VIO.b : VIO.c);
  }
  g.ol(VIO.k);
  g.r(1, 7, 2, 2, VIO.a).p(1, 7, VIO.h);
  g.r(12, 4, 2, 2, VIO.a).p(12, 4, VIO.h);
  return g;
};

// ---- face

S['ice-visor'] = () => {
  const g = new G();
  g.rows([
    '..dddddddddddd..',
    'ddhaaaaaabbbbbdd',
    'd.abbbbbbbbbbc.d',
    '..cccccccccccc..',
  ], { h: ICE.h, a: ICE.a, b: ICE.b, c: ICE.c, d: ICE.d }, 0, 6);
  g.p(5, 7, ICE.h).p(4, 8, ICE.h).p(6, 7, ICE.h);
  return g.ol(ICE.k);
};

S['rift-mask'] = () => {
  const g = new G();
  g.rows([
    '.aaaaaaaaaa.',
    'abbbbbbbbbbc',
    'abbbbbbbbbbc',
    'akkkbbbbkkkc',
    'abkkbbbbkkbc',
    'abbbbbbbbbbc',
    'abbbbbbbbbbc',
    '.bbbbbbbbbc.',
    '..cccccccc..',
  ], { a: '#4a4a58', b: '#30303a', c: '#1e1e26', k: '#0a0a10' }, 2, 3);
  // the seam: a jagged split down the middle, glowing violet
  [[8, 3], [8, 4], [7, 5], [7, 6], [8, 7], [8, 8], [9, 9], [8, 10], [8, 11]].forEach(([x, y]) => g.p(x, y, VIO.a));
  [[9, 4], [8, 6], [7, 7], [9, 8], [9, 10], [7, 4]].forEach(([x, y]) => g.p(x, y, VIO.d));
  g.p(4, 7, VIO.c).p(11, 7, VIO.c);
  return g.ol('#06060a');
};

// ---- emotes: a small light figure in the pose, no outline (as the emote wheel's figures)

const FIG = { w: '#f4f5f6', s: '#d3d6db', d: '#8a8d94', m: VIO.b, g: NEU.d, G: NEU.e };
/** Stamp a pose and centre its bounding box in the cell. */
function figure(rows) {
  const t = new G();
  t.rows(rows, FIG);
  let x0 = 16, x1 = -1, y0 = 16, y1 = -1;
  t.map((x, y) => { x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y); });
  const dx = Math.floor((16 - (x1 - x0 + 1)) / 2) - x0, dy = Math.floor((16 - (y1 - y0 + 1)) / 2) - y0;
  const g = new G();
  t.map((x, y, c) => { g.p(x + dx, y + dy, c); });
  return g;
}

S['emote-wave'] = () => figure([
  '.......wws.s.m..',
  '.......wws.s....',
  '.......wws.s.m..',
  '.....wwwwsss....',
  '.....w.wws......',
  '.....w.wws......',
  '.....w.wws......',
  '.......wws......',
  '.......wws......',
  '.......w.s......',
  '.......w.s......',
  '.......w.s......',
  '.......w.s......',
  '......ww.ss.....',
]);
S['emote-clap'] = () => figure([
  '...m...w...m....',
  '......w.s.......',
  '..m..w...s..m...',
  '....w.wws.s.....',
  '....w.wws.s.....',
  '....wwwwssss....',
  '......wws.......',
  '......wws.......',
  '......wws.......',
  '......wws.......',
  '......w.s.......',
  '......w.s.......',
  '......w.s.......',
  '......w.s.......',
  '.....ww.ss......',
]);
S['emote-bow'] = () => figure([
  '...wwwwwwww.....',
  '...wwwwwwwwww...',
  '...wwssssswww...',
  '...ws.....www...',
  '...ws.....ss....',
  '...ws...........',
  '...ws...........',
  '...ws...........',
  '...wsss.........',
]);
S['emote-salute'] = () => figure([
  '.......wwwwwm...',
  '.......wws.s....',
  '.......wws.s....',
  '.....wwwwsss....',
  '.....w.wws......',
  '.....w.wws......',
  '.....w.wws......',
  '.......wws......',
  '.......wws......',
  '.......w.s......',
  '.......w.s......',
  '.......w.s......',
  '.......w.s......',
  '......ww.ss.....',
]);
S['emote-shrug'] = () => figure([
  '...w.........s..',
  '...ww..wws..ss..',
  '....w..wws..s...',
  '....wwwwwssss...',
  '.......wws......',
  '.......wws......',
  '.......wws......',
  '.......wws......',
  '.......w.s......',
  '.......w.s......',
  '.......w.s......',
  '.......w.s......',
  '......ww.ss.....',
]);
S['emote-sit'] = () => figure([
  '...www.......',
  '...www.......',
  '...www.......',
  '...wwws......',
  '...www.s.....',
  '...www..s....',
  '...wwwwwwwwww',
  '...wwssssssss',
  '.GGGGGGGGGGGGG',
]);

// ---- trails: a short particle trail rising from the bottom-left, newest at the top-right

S['spark-trail'] = () => {
  const g = new G();
  g.r(11, 2, 3, 3, VIO.b).p(11, 2, VIO.h).p(12, 2, VIO.a).p(11, 3, VIO.a).p(13, 4, VIO.c);
  g.p(12, 0, VIO.a).p(12, 6, VIO.c).p(15, 3, VIO.c).p(9, 3, VIO.a);
  g.r(8, 7, 2, 2, VIO.c).p(8, 7, VIO.b);
  g.r(5, 10, 2, 2, VIO.d).p(5, 10, VIO.c);
  g.p(3, 13, VIO.d).p(1, 15, VIO.e);
  return g;
};
S['ember-trail'] = () => {
  const g = new G();
  g.r(10, 2, 2, 2, CORAL.y).p(10, 2, CORAL.h).p(11, 3, CORAL.b);
  g.p(13, 1, CORAL.b).p(13, 5, CORAL.c);
  g.r(7, 6, 2, 2, CORAL.b).p(7, 6, CORAL.y).p(8, 7, CORAL.c);
  g.p(10, 8, CORAL.c);
  g.r(4, 9, 2, 2, CORAL.c).p(4, 9, CORAL.b);
  g.p(6, 12, CORAL.d).p(2, 12, CORAL.d).p(3, 14, CORAL.e);
  return g;
};
S['frost-trail'] = () => {
  const g = new G();
  g.rows([
    'h..a..h',
    '.a.b.a.',
    '..bhb..',
    'abhwhba',
    '..bhb..',
    '.a.b.a.',
    'h..a..h',
  ], { w: '#ffffff', h: ICE.h, a: ICE.c, b: ICE.b }, 8, 1);
  g.rows(['..b..', '.b.b.', 'b...b', '.b.b.', '..b..'], { b: ICE.c }, 4, 8);
  g.p(6, 10, ICE.a);
  g.rows(['.d.', 'd.d', '.d.'], { d: ICE.d }, 1, 12);
  g.p(0, 15, ICE.e);
  return g;
};
S['stair-trail'] = () => {
  const g = new G();
  const step = (x, y, P) => g.r(x, y, 3, 3, P[1]).r(x, y, 3, 1, P[0]).r(x + 2, y + 1, 1, 2, P[2]);
  step(11, 1, [MINT.a, MINT.b, MINT.c]);
  step(8, 4, [MINT.b, MINT.c, MINT.d]);
  step(5, 7, [MINT.c, MINT.d, MINT.k]);
  g.r(2, 11, 2, 2, MINT.d);
  g.p(0, 14, MINT.d);
  return g;
};

// ---- badges: a 10-12 px mark, centred

S['void-badge-founder'] = () => {
  const g = new G();
  g.c(8, 8, 5.6, GOLD.b);
  g.c(8, 8, 5.6, GOLD.a, (x, y) => x + y < 12);
  g.c(8, 8, 5.6, GOLD.c, (x, y) => x + y > 19);
  g.c(8, 8, 4.4, CORAL.d);
  g.c(8, 8, 4.4, CORAL.e, (x, y) => x + y > 17);
  g.rows([
    '..h..',
    '.hhy.',
    'hhhyy',
    '.hyy.',
    '.h.y.',
  ], { h: CORAL.h, y: CORAL.y }, 6, 5);
  return g.ol(GOLD.k);
};
S['void-badge-player'] = () => {
  const g = new G();
  voidRing(g, 2, 2, 12, NEU.w, NEU.b);
  return g.ol('#1c1d22');
};
S['cell-badge'] = () => {
  const g = new G();
  for (let j = 0; j < 3; j++) for (let i = 0; i < 3; i++) {
    const x = 3 + i * 4, y = 3 + j * 4;
    const P = i === 1 && j === 1 ? [VIO.h, VIO.a, VIO.b] : [VIO.a, VIO.b, VIO.c];
    g.r(x, y, 3, 3, P[1]).p(x, y, P[0]).p(x + 2, y + 2, P[2]).p(x + 1, y + 2, P[2]).p(x + 2, y + 1, P[2]);
  }
  return g.ol(VIO.k);
};
S['frost-badge'] = () => {
  const g = new G();
  const dot = (cx, cy) => {
    g.c(cx, cy, 2.1, ICE.b);
    g.c(cx, cy, 2.1, ICE.c, (x, y) => x + 0.5 > cx && y + 0.5 > cy);
    g.p(Math.floor(cx) - 1, Math.floor(cy) - 1, ICE.h);
  };
  dot(8, 5);
  dot(5, 11);
  dot(11, 11);
  return g.ol(ICE.k);
};

// ---- the paper doll: Minecraft proportions at half scale (head 4x4, body 4x6, arms and legs
// 2x6), 8 wide x 16 tall, centred. No outline: it is drawn large as the wardrobe's centre.

const DOLL = {
  H: '#5a3c22', h: '#4a311b', i: '#6b4a2c', // hair
  S: '#c8956a', s: '#a87a54', t: '#dcaa7e', // skin
  E: '#3f4fa8', M: '#7a4a32', // eyes, mouth
  T: '#2ab3b0', u: '#1f8d8b', v: '#4cc8c4', // shirt
  B: '#3b3fa6', b: '#2c2f85', n: '#4a4fc0', // trousers
  G: '#7a7a80', g: '#5e5e64', // shoes
};
S.doll_front = () => {
  const g = new G();
  g.rows([
    'iHHh',
    'HttS',
    'EtSE',
    'tMMs',
  ], DOLL, 6, 0);
  g.rows([
    'vvvTTTuu',
    'vTvTTTuu',
    'tSvTTTsS',
    'tSvTTusS',
    'tSvTTusS',
    'tSvTTusS',
  ], DOLL, 4, 4);
  g.rows([
    'nBBb',
    'nBBb',
    'nBBb',
    'nBBb',
    'nBBb',
    'GGgg',
  ], DOLL, 6, 10);
  return g;
};
S.doll_back = () => {
  const g = new G();
  g.rows([
    'iHHh',
    'iHHh',
    'HHHh',
    'HHhh',
  ], DOLL, 6, 0);
  g.rows([
    'vvvTTTuu',
    'vTvTTTuu',
    'tSvTTusS',
    'tSvTTusS',
    'tSvTTusS',
    'tSvTTusS',
  ], DOLL, 4, 4);
  g.rows([
    'nBBb',
    'nBBb',
    'nBBb',
    'nBBb',
    'nBBb',
    'GGgg',
  ], DOLL, 6, 10);
  return g;
};

// Keep the sheet inside one 256-colour palette without loosening everyone's snap: any catalog
// colour within a few RGB steps of a colour the earlier sprites already use is snapped onto it.
{
  const names = Object.keys(S);
  const cut = names.indexOf('cosmetics') + 1;
  const base = new Map();
  // opaque '#rrggbb' colours only: translucent ones must never be snapped onto
  names.slice(0, cut).forEach((n) => S[n]().map((x, y, c) => { if (/^#[0-9a-f]{6}$/i.test(c)) base.set(c.toLowerCase(), rgb(c)); }));
  const near = (c) => {
    const v = rgb(c);
    let best = c, bd = 16;
    for (const [k, w] of base) {
      const d = Math.hypot(v[0] - w[0], v[1] - w[1], v[2] - w[2]);
      if (d < bd) { bd = d; best = k; }
    }
    return best;
  };
  names.slice(cut).forEach((n) => {
    const draw = S[n];
    S[n] = () => draw().map((x, y, c) => (/^#[0-9a-f]{6}$/i.test(c) ? near(c.toLowerCase()) : undefined));
  });
}

export const SPRITES = S;
