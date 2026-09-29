// State illustrations (240 × 160) and category cube glyphs (24 × 24) for VOID.
// Same isometric grid, light direction and three-shade faces as the coming-soon scenes.
import { writeFileSync, mkdirSync } from 'node:fs';

const C = {
  base: '#131315', card: '#191A1C', ink: '#EDEEEF',
  V: '#9F8BFF', O: '#FF9E7A', Cy: '#7ADFFF', G: '#7AE0B0', ok: '#3DD68C',
};
const hex = (h) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16));
const toHex = (a) => '#' + a.map((v) => Math.round(Math.max(0, Math.min(255, v))).toString(16).padStart(2, '0')).join('');
const mix = (a, b, t) => toHex(hex(a).map((v, i) => v + (hex(b)[i] - v) * t));
const isoF = (ox, oy, S) => (x, y, z) => [ox + (x - y) * S * 0.8660254, oy + (x + y) * S * 0.5 - z * S];
const f2 = (v) => +v.toFixed(2);
const pts = (a) => a.map((p) => p.map(f2).join(',')).join(' ');
const poly = (a, fill, extra = '') => `<polygon points="${pts(a)}" fill="${fill}" ${extra}/>`;
const shades = (hue) => ({ top: hue, left: mix(hue, C.base, 0.38), right: mix(hue, C.base, 0.58) });
const slab = { top: '#2A2C30', left: '#1F2124', right: '#18191C' };
const metal = { top: '#2C2E33', left: '#222428', right: '#1A1B1E' };

function box(iso, x, y, z, w, d, h, col, edge = 'rgba(255,255,255,0.1)') {
  const top = [iso(x, y, z + h), iso(x + w, y, z + h), iso(x + w, y + d, z + h), iso(x, y + d, z + h)];
  const left = [iso(x, y + d, z), iso(x + w, y + d, z), iso(x + w, y + d, z + h), iso(x, y + d, z + h)];
  const right = [iso(x + w, y, z), iso(x + w, y + d, z), iso(x + w, y + d, z + h), iso(x + w, y, z + h)];
  const a = iso(x + w, y + d, z), b = iso(x + w, y + d, z + h);
  return poly(left, col.left) + poly(right, col.right) + poly(top, col.top) +
    `<polyline points="${pts([iso(x, y + d, z + h), b, iso(x + w, y, z + h)])}" fill="none" stroke="${edge}"/>` +
    `<line x1="${f2(a[0])}" y1="${f2(a[1])}" x2="${f2(b[0])}" y2="${f2(b[1])}" stroke="${edge}"/>`;
}
function faceCells(iso, x, y, z, w, d, h, face, n, m, at, inset = 0.08) {
  let s = '';
  for (let i = 0; i < n; i++) for (let j = 0; j < m; j++) {
    const c = at(i, j); if (!c) continue;
    const a0 = (i + inset) / n, a1 = (i + 1 - inset) / n, b0 = (j + inset) / m, b1 = (j + 1 - inset) / m;
    const q = [[a0, b0], [a1, b0], [a1, b1], [a0, b1]].map(([a, b]) =>
      face === 'top' ? iso(x + a * w, y + b * d, z + h) : face === 'left' ? iso(x + a * w, y + d, z + h - b * h) : iso(x + w, y + d - a * d, z + h - b * h));
    s += poly(q, c);
  }
  return s;
}
let gid = 0, gpre = 'x';
const glow = (cx, cy, r, c, p = 0.3) => { const id = `${gpre}-g${gid++}`; return `<defs><radialGradient id="${id}"><stop offset="0" stop-color="${c}" stop-opacity="${p}"/><stop offset="0.55" stop-color="${c}" stop-opacity="${p * 0.3}"/><stop offset="1" stop-color="${c}" stop-opacity="0"/></radialGradient></defs><ellipse cx="${f2(cx)}" cy="${f2(cy)}" rx="${r}" ry="${r * 0.8}" fill="url(#${id})"/>`; };
const shadow = (cx, cy, rx, ry, o = 0.5) => { const id = `${gpre}-s${gid++}`; return `<defs><radialGradient id="${id}"><stop offset="0" stop-color="#000" stop-opacity="${o}"/><stop offset="1" stop-color="#000" stop-opacity="0"/></radialGradient></defs><ellipse cx="${f2(cx)}" cy="${f2(cy)}" rx="${rx}" ry="${ry}" fill="url(#${id})"/>`; };
const spark = (cx, cy, r, c, o = 1) => { const k = r * 0.22; return `<path d="M${cx} ${cy - r}C${cx + k} ${cy - k} ${cx + k} ${cy - k} ${cx + r} ${cy}C${cx + k} ${cy + k} ${cx + k} ${cy + k} ${cx} ${cy + r}C${cx - k} ${cy + k} ${cx - k} ${cy + k} ${cx - r} ${cy}C${cx - k} ${cy - k} ${cx - k} ${cy - k} ${cx} ${cy - r}Z" fill="${c}" opacity="${o}"/>`; };
function dots(W, H, cx, cy, rx, ry, step = 12, peak = 0.14) {
  let s = '';
  for (let X = step / 2; X < W; X += step) for (let Y = step / 2; Y < H; Y += step) {
    const d = Math.hypot((X - cx) / rx, (Y - cy) / ry); if (d >= 1) continue;
    const o = peak * Math.pow(1 - d, 1.6); if (o < 0.012) continue;
    s += `<rect x="${X - 1}" y="${Y - 1}" width="2" height="2" rx="0.6" fill="${C.ink}" opacity="${o.toFixed(3)}"/>`;
  }
  return s + '<!--scene-->';
}
const svg = (W, H, body) => `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}" fill="none">${body}</svg>`;
// a pixel head: coloured cube with a face on its front (left) face
function head(iso, x, y, z, sz, hue) {
  return box(iso, x, y, z, sz, sz, sz, shades(hue), 'rgba(255,255,255,0.2)') +
    faceCells(iso, x, y, z, sz, sz, sz, 'left', 4, 4, (i, j) => (j === 1 && (i === 0 || i === 3) ? '#0E0F10' : j === 3 && (i === 1 || i === 2) ? mix(hue, C.base, 0.6) : null), 0.1);
}
const dotted = (d, c, o = 0.5) => `<path d="${d}" stroke="${c}" stroke-opacity="${o}" stroke-width="1.3" stroke-dasharray="1.5 5" stroke-linecap="round"/>`;
const W = 240, H = 160;

const states = {
  // two players, not yet linked — a "+" waiting between them
  'friends-empty': () => {
    const iso = isoF(120, 104, 15);
    let s = dots(W, H, 120, 84, 130, 84);
    s += shadow(...iso(1.5, 1.5, -0.4), 92, 26, 0.5) + box(iso, -1.6, -1.6, -0.45, 6.2, 6.2, 0.45, slab);
    const a = iso(-0.1, 2.9, 0.75), b = iso(3.3, -0.3, 0.75);
    s += dotted(`M${f2(a[0])} ${f2(a[1])} Q120 ${f2(Math.min(a[1], b[1]) - 26)} ${f2(b[0])} ${f2(b[1])}`, C.ink, 0.28);
    s += head(iso, 2.6, -1.0, 0, 1.4, C.Cy) + head(iso, -1.0, 2.2, 0, 1.4, C.V);
    s += glow(120, 50, 26, C.V, 0.4) + `<circle cx="120" cy="50" r="11" fill="${C.card}" stroke="rgba(255,255,255,0.14)"/><path d="M120 45.5v9M115.5 50h9" stroke="${C.ink}" stroke-width="2" stroke-linecap="round"/>`;
    s += spark(40, 36, 5, C.ink, 0.6) + spark(204, 118, 4, C.V, 0.8);
    return svg(W, H, s);
  },
  // you and VOID, the link between you broken
  offline: () => {
    const iso = isoF(120, 100, 15);
    let s = dots(W, H, 120, 84, 130, 84);
    s += shadow(...iso(1.5, 1.5, -0.4), 96, 26, 0.5) + box(iso, -1.8, -1.8, -0.45, 6.6, 6.6, 0.45, slab);
    s += box(iso, 2.8, -1.2, 0, 1.6, 1.6, 1.6, metal);
    faceCells; // VOID ring on the block's top
    const ring = [[1, 0], [2, 0], [0, 1], [3, 1], [0, 2], [3, 2], [1, 3], [2, 3]];
    s += faceCells(iso, 2.8, -1.2, 0, 1.6, 1.6, 1.6, 'top', 4, 4, (i, j) => (ring.some(([a, b]) => a === i && b === j) ? C.ink : null), 0.14);
    s += head(iso, -1.2, 2.6, 0, 1.4, C.V);
    const a = iso(-0.2, 3.3, 0.9), b = iso(3.1, 0.2, 1.0);
    const mx = (a[0] + b[0]) / 2, my = (a[1] + b[1]) / 2 - 22;
    s += dotted(`M${f2(a[0])} ${f2(a[1])} Q${f2(a[0] + 18)} ${f2(my)} ${f2(mx - 12)} ${f2(my + 4)}`, C.ink, 0.35);
    s += dotted(`M${f2(mx + 12)} ${f2(my + 4)} Q${f2(b[0] - 18)} ${f2(my)} ${f2(b[0])} ${f2(b[1])}`, C.ink, 0.35);
    s += glow(mx, my + 4, 22, C.O, 0.45) + `<path d="M${f2(mx - 4)} ${f2(my)}l8 8M${f2(mx + 4)} ${f2(my)}l-8 8" stroke="${C.O}" stroke-width="2.2" stroke-linecap="round"/>`;
    s += spark(36, 40, 4, C.ink, 0.5) + spark(206, 34, 5, C.ink, 0.6);
    return svg(W, H, s);
  },
  // one blade, no light, no signal
  'server-unreachable': () => {
    const iso = isoF(112, 96, 15);
    let s = dots(W, H, 120, 84, 130, 84);
    s += shadow(...iso(2, 1.3, -0.4), 100, 26, 0.5) + box(iso, -1.2, -1.4, -0.45, 6.4, 5.4, 0.45, slab);
    const bw = 4.2, bd = 2.8, bh = 1.3;
    s += box(iso, 0, 0, 0.1, bw, bd, bh, metal);
    const onFace = (u, v) => iso(u, bd, 0.1 + bh - v);
    const [lx, ly] = onFace(0.4, bh / 2);
    s += glow(lx, ly, 18, C.O, 0.5) + `<circle cx="${f2(lx)}" cy="${f2(ly)}" r="3.2" fill="${C.O}"/>`;
    for (let i = 0; i < 5; i++) { const a = onFace(0.9 + i * 0.3, 0.36), c = onFace(0.9 + i * 0.3, bh - 0.36); s += `<line x1="${f2(a[0])}" y1="${f2(a[1])}" x2="${f2(c[0])}" y2="${f2(c[1])}" stroke="rgba(255,255,255,0.08)" stroke-width="2" stroke-linecap="round"/>`; }
    // signal bars, all empty but one
    for (let k = 0; k < 4; k++) { const h = 6 + k * 5; s += `<rect x="${176 + k * 9}" y="${58 - h}" width="5" height="${h}" rx="2" fill="${k === 0 ? C.O : C.ink}" opacity="${k === 0 ? 1 : 0.1}"/>`; }
    s += spark(36, 38, 4, C.ink, 0.5);
    return svg(W, H, s);
  },
  // an empty pedestal, a sword waiting over it
  'no-sessions': () => {
    const iso = isoF(120, 110, 15);
    let s = dots(W, H, 120, 84, 130, 84);
    s += shadow(...iso(1, 1, -0.4), 70, 20, 0.5) + box(iso, -0.8, -0.8, -0.45, 3.6, 3.6, 0.45, slab);
    s += box(iso, 0, 0, 0, 2, 2, 1.1, metal);
    const [cx, cy] = iso(1, 1, 1.1);
    s += glow(cx, cy - 38, 44, C.O, 0.35) + shadow(cx, cy, 14, 5, 0.5);
    const sword = ['.......WW', '......WSW', '.....WSW.', '....WSW..', '.G.WSW...', '..GSW....', '..hG.....', '.h..G....', 'p........'];
    const px = 5.2, sx = cx - (9 * px) / 2, sy = cy - 80;
    const pal = { W: '#F4F5F6', S: '#B9BCC2', G: C.O, h: mix(C.O, C.base, 0.45), p: C.O };
    sword.forEach((row, j) => [...row].forEach((c, i) => { if (c !== '.') s += `<rect x="${f2(sx + i * px)}" y="${f2(sy + j * px)}" width="${px - 0.6}" height="${px - 0.6}" rx="1.1" fill="${pal[c]}"/>`; }));
    s += spark(cx + 30, sy + 2, 5, C.ink, 0.8) + spark(cx - 34, sy + 26, 3.5, C.O, 0.8) + spark(40, 120, 4, C.ink, 0.4);
    return svg(W, H, s);
  },
  // the block cracked, a few cells fallen out
  crashed: () => {
    const iso = isoF(120, 96, 15);
    let s = dots(W, H, 120, 84, 130, 84);
    s += shadow(...iso(1.5, 1.5, -0.4), 92, 26, 0.5) + box(iso, -1.4, -1.4, -0.45, 5.8, 5.8, 0.45, slab);
    s += glow(...iso(1.5, 1.5, 1.6), 70, C.O, 0.22);
    s += box(iso, 0, 0, 0, 3, 3, 3, { top: '#2F3136', left: '#25272B', right: '#1C1D20' }, 'rgba(255,255,255,0.12)');
    const holes = new Set(['2,1', '3,1', '3,2']);
    s += faceCells(iso, 0, 0, 0, 3, 3, 3, 'left', 6, 6, (i, j) => (holes.has(`${i},${j}`) ? '#0E0F10' : null), 0.04);
    const crack = [iso(1.4, 3, 3), iso(1.2, 3, 2.4), iso(1.7, 3, 1.9), iso(1.5, 3, 1.2)];
    s += `<polyline points="${pts(crack)}" stroke="${C.O}" stroke-width="1.8" stroke-linejoin="round" fill="none"/>`;
    // fallen cells
    [[1.9, 4.2, 0, 0.5], [0.5, 4.6, 0, 0.4], [3.6, 3.8, 0, 0.35]].forEach(([x, y, z, sz]) => (s += box(iso, x, y, z, sz, sz, sz, { top: '#34363B', left: '#26282C', right: '#1E1F22' })));
    s += spark(196, 40, 4, C.O, 0.8) + spark(40, 44, 4, C.ink, 0.5);
    return svg(W, H, s);
  },
  // a lens of cells over an empty grid
  'no-results': () => {
    let s = dots(W, H, 120, 84, 130, 84);
    for (let i = 0; i < 6; i++) for (let j = 0; j < 4; j++) s += `<rect x="${60 + i * 20}" y="${44 + j * 20}" width="14" height="14" rx="4" fill="${C.ink}" opacity="0.05"/>`;
    s += glow(110, 72, 44, C.V, 0.35);
    s += `<circle cx="110" cy="72" r="26" fill="${C.card}" fill-opacity="0.7" stroke="${C.ink}" stroke-opacity="0.8" stroke-width="5"/>`;
    s += `<path d="M129 91 l20 20" stroke="${C.ink}" stroke-opacity="0.8" stroke-width="8" stroke-linecap="round"/>`;
    s += `<rect x="103" y="65" width="14" height="14" rx="4" fill="${C.V}" opacity="0.5"/><path d="M108 70l4 4M112 70l-4 4" stroke="${C.base}" stroke-width="1.6" stroke-linecap="round"/>`;
    s += spark(170, 44, 5, C.ink, 0.6) + spark(52, 120, 4, C.V, 0.7);
    return svg(W, H, s);
  },
};

// category cubes — 24 × 24 glyphs
const cube = (hue) => {
  const iso = isoF(12, 12.5, 6.2);
  return svg(24, 24, box(iso, -1, -1, -1, 2, 2, 2, shades(hue), 'rgba(255,255,255,0.22)') +
    faceCells(iso, -1, -1, -1, 2, 2, 2, 'top', 2, 2, (i, j) => ((i + j) % 2 ? mix(hue, '#ffffff', 0.25) : null), 0.16));
};
const cubes = { hud: cube(C.V), pvp: cube(C.O), visual: cube(C.Cy), utility: cube(C.G) };

const dir = new URL('./out/', import.meta.url);
mkdirSync(dir, { recursive: true });
const all = {};
const framed = new Set(['friends-empty', 'offline', 'server-unreachable', 'no-sessions', 'crashed']);
for (const [k, fn] of Object.entries(states)) {
  gid = 0; gpre = k;
  let v = fn();
  v = framed.has(k) ? v.replace('<!--scene-->', '<g transform="translate(120 74) scale(0.8) translate(-120 -80)">').replace('</svg>', '</g></svg>') : v.replace('<!--scene-->', '');
  all[`state-${k}`] = v;
}
for (const [k, v] of Object.entries(cubes)) all[`cube-${k}`] = v;
for (const [k, v] of Object.entries(all)) writeFileSync(new URL(`${k}.svg`, dir), v);
const tiles = Object.entries(all).filter(([k]) => k.startsWith('state'));
writeFileSync(new URL('./states.html', import.meta.url), `<!doctype html><body style="margin:0;background:#0B0B0C;font:13px system-ui;color:#9A9DA1;padding:20px">
<div style="display:grid;grid-template-columns:repeat(3,260px);gap:16px">${tiles.map(([k, v]) => `<div style="background:#191A1C;border-radius:16px;padding:10px">${v}<div style="padding:4px 6px">${k}</div></div>`).join('')}</div>
<div style="display:flex;gap:18px;margin-top:20px;align-items:center;background:#191A1C;border-radius:16px;padding:14px;width:fit-content">${Object.entries(cubes).map(([k, v]) => `<span style="display:flex;gap:6px;align-items:center;color:#EDEEEF">${v}${k}</span>`).join('')}
<span style="display:flex;gap:6px;align-items:center;color:#EDEEEF">${cubes.hud.replace('width="24" height="24"', 'width="48" height="48"')}48px</span></div></body>`);
console.log(Object.keys(all).join(' '));
