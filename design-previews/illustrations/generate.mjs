// Coming-soon illustrations for VOID — isometric cell scenes, emitted as SVG.
import { writeFileSync } from 'node:fs';

const W = 640, H = 360;
const C = {
  base: '#131315', card: '#191A1C', raised: '#212225', ink: '#EDEEEF',
  V: '#9F8BFF', O: '#FF9E7A', Cy: '#7ADFFF', G: '#7AE0B0', ok: '#3DD68C',
};

// ---------- colour helpers
const hex = (h) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16));
const toHex = (a) => '#' + a.map((v) => Math.round(Math.max(0, Math.min(255, v))).toString(16).padStart(2, '0')).join('');
const mix = (a, b, t) => toHex(hex(a).map((v, i) => v + (hex(b)[i] - v) * t));

// ---------- seeded rng
function rng(seed) { let s = seed; return () => ((s = (s * 16807) % 2147483647) / 2147483647); }

// ---------- iso projection
function isoFactory(ox, oy, S) {
  return (x, y, z) => [ox + (x - y) * S * 0.8660254, oy + (x + y) * S * 0.5 - z * S];
}
const pts = (arr) => arr.map((p) => p.map((v) => v.toFixed(2)).join(',')).join(' ');
const poly = (arr, fill, extra = '') => `<polygon points="${pts(arr)}" fill="${fill}" ${extra}/>`;

// a box with its three visible faces; faces: {top,left,right} colours; returns svg string
function box(iso, x, y, z, w, d, h, col, opts = {}) {
  const top = [iso(x, y, z + h), iso(x + w, y, z + h), iso(x + w, y + d, z + h), iso(x, y + d, z + h)];
  const left = [iso(x, y + d, z), iso(x + w, y + d, z), iso(x + w, y + d, z + h), iso(x, y + d, z + h)];
  const right = [iso(x + w, y, z), iso(x + w, y + d, z), iso(x + w, y + d, z + h), iso(x + w, y, z + h)];
  const edge = opts.edge ?? 'rgba(255,255,255,0.07)';
  let s = poly(left, col.left) + poly(right, col.right) + poly(top, col.top);
  // light edges: the two top-front edges and the vertical front edge
  s += `<polyline points="${pts([iso(x, y + d, z + h), iso(x + w, y + d, z + h), iso(x + w, y, z + h)])}" fill="none" stroke="${edge}" stroke-width="1"/>`;
  s += `<line x1="${iso(x + w, y + d, z)[0]}" y1="${iso(x + w, y + d, z)[1]}" x2="${iso(x + w, y + d, z + h)[0]}" y2="${iso(x + w, y + d, z + h)[1]}" stroke="${edge}" stroke-width="1"/>`;
  return s;
}
const shades = (hue, base = C.base) => ({ top: hue, left: mix(hue, base, 0.38), right: mix(hue, base, 0.58) });
const neutral = { top: '#2A2C30', left: '#1F2124', right: '#18191C' };

// pixel cells laid onto one face of a box. face: 'top' | 'left' | 'right'
function faceCells(iso, x, y, z, w, d, h, face, n, m, colourAt, inset = 0.08) {
  let s = '';
  for (let i = 0; i < n; i++) for (let j = 0; j < m; j++) {
    const c = colourAt(i, j); if (!c) continue;
    const a0 = i / n + inset / n, a1 = (i + 1) / n - inset / n;
    const b0 = j / m + inset / m, b1 = (j + 1) / m - inset / m;
    let q;
    if (face === 'top') q = [[a0, b0], [a1, b0], [a1, b1], [a0, b1]].map(([a, b]) => iso(x + a * w, y + b * d, z + h));
    if (face === 'left') q = [[a0, b0], [a1, b0], [a1, b1], [a0, b1]].map(([a, b]) => iso(x + a * w, y + d, z + h - b * h));
    if (face === 'right') q = [[a0, b0], [a1, b0], [a1, b1], [a0, b1]].map(([a, b]) => iso(x + w, y + d - a * d, z + h - b * h));
    s += poly(q, c);
  }
  return s;
}

// ---------- ambient layers
function dotField(cx, cy, rx, ry, step = 16, peak = 0.16) {
  let s = '<g>';
  for (let X = step / 2; X < W; X += step) for (let Y = step / 2; Y < H; Y += step) {
    const d = Math.hypot((X - cx) / rx, (Y - cy) / ry);
    if (d >= 1) continue;
    const o = peak * Math.pow(1 - d, 1.6);
    if (o < 0.012) continue;
    s += `<rect x="${X - 1.25}" y="${Y - 1.25}" width="2.5" height="2.5" rx="0.8" fill="${C.ink}" opacity="${o.toFixed(3)}"/>`;
  }
  return s + '</g>';
}
let gid = 0;
function glow(cx, cy, r, colour, peak = 0.28) {
  const id = `glow${gid++}`;
  return `<defs><radialGradient id="${id}"><stop offset="0" stop-color="${colour}" stop-opacity="${peak}"/><stop offset="0.55" stop-color="${colour}" stop-opacity="${peak * 0.3}"/><stop offset="1" stop-color="${colour}" stop-opacity="0"/></radialGradient></defs><ellipse cx="${cx}" cy="${cy}" rx="${r}" ry="${r * 0.8}" fill="url(#${id})"/>`;
}
function groundShadow(cx, cy, rx, ry, o = 0.5) {
  const id = `sh${gid++}`;
  return `<defs><radialGradient id="${id}"><stop offset="0" stop-color="#000" stop-opacity="${o}"/><stop offset="1" stop-color="#000" stop-opacity="0"/></radialGradient></defs><ellipse cx="${cx}" cy="${cy}" rx="${rx}" ry="${ry}" fill="url(#${id})"/>`;
}
// four-point sparkle
function spark(cx, cy, r, colour, o = 1) {
  const k = r * 0.22;
  return `<path d="M${cx} ${cy - r} C${cx + k} ${cy - k} ${cx + k} ${cy - k} ${cx + r} ${cy} C${cx + k} ${cy + k} ${cx + k} ${cy + k} ${cx} ${cy + r} C${cx - k} ${cy + k} ${cx - k} ${cy + k} ${cx - r} ${cy} C${cx - k} ${cy - k} ${cx - k} ${cy - k} ${cx} ${cy - r}Z" fill="${colour}" opacity="${o}"/>`;
}
const svg = (body) => `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}" fill="none">${body}</svg>`;

// ======================================================= MODPACKS
function modpacks() {
  gid = 0;
  const S = 24, iso = isoFactory(320, 190, S);
  const r = rng(11);
  let s = dotField(320, 180, 330, 190);
  const [gx, gy] = iso(2, 2, 2.2);
  s += glow(gx, gy, 190, C.G, 0.22);
  // platform
  s += groundShadow(...iso(2, 2, -0.8), 190, 70, 0.55);
  s += box(iso, -1.6, -1.6, -0.7, 7.2, 7.2, 0.7, neutral);
  s += faceCells(iso, -1.6, -1.6, -0.7, 7.2, 7.2, 0.7, 'top', 12, 12, (i, j) => ((i + j) % 2 ? null : 'rgba(255,255,255,0.025)'), 0.1);
  // orbit ring (back half first)
  const rc = iso(2, 2, 2.1), R = 5.4, rx = R * 1.2247 * S, ry = R * 0.7071 * S;
  const ring = (half) => `<path d="M${rc[0] + (half ? rx : -rx)} ${rc[1]} A${rx} ${ry} 0 0 1 ${rc[0] + (half ? -rx : rx)} ${rc[1]}" stroke="${C.ink}" stroke-opacity="0.16" stroke-width="1.2" stroke-dasharray="2 7" stroke-linecap="round"/>`;
  s += ring(false);
  // mini mod cubes on the ring — back ones before the block
  const minis = [
    { t: 2.95, hue: C.Cy }, { t: -1.385, hue: C.V }, { t: -0.185, hue: C.O }, { t: 1.756, hue: C.G },
  ].map((m) => ({ ...m, x: 2 + R * Math.cos(m.t), y: 2 + R * Math.sin(m.t) }));
  const mini = (m) => {
    const z = 2.1, sz = 1.25, bob = 0;
    let q = '';
    const [cx, cy] = iso(m.x, m.y, z + sz / 2);
    q += glow(cx, cy, 46, m.hue, 0.35);
    q += box(iso, m.x - sz / 2, m.y - sz / 2, z + bob, sz, sz, sz, shades(m.hue), { edge: 'rgba(255,255,255,0.18)' });
    q += faceCells(iso, m.x - sz / 2, m.y - sz / 2, z + bob, sz, sz, sz, 'top', 2, 2, (i, j) => ((i + j) % 2 ? mix(m.hue, '#ffffff', 0.25) : null), 0.14);
    return q;
  };
  const back = minis.filter((m) => m.x + m.y < 4), front = minis.filter((m) => m.x + m.y >= 4);
  back.forEach((m) => (s += mini(m)));

  // the block: grass on top, ore-flecked stone on the sides
  const topPal = [C.G, mix(C.G, '#ffffff', 0.18), mix(C.G, C.base, 0.18), mix(C.G, C.base, 0.32)];
  s += box(iso, 0, 0, 0, 4, 4, 4, { top: mix(C.G, C.base, 0.25), left: '#26282B', right: '#1C1D20' }, { edge: 'rgba(255,255,255,0.12)' });
  s += faceCells(iso, 0, 0, 0, 4, 4, 4, 'top', 8, 8, () => topPal[Math.floor(r() * topPal.length)], 0.06);
  const ore = new Map([['1,4', C.V], ['2,4', C.V], ['5,5', C.Cy], ['6,6', C.Cy], ['3,6', C.O]]);
  const sidePal = (dark) => (dark ? ['#1C1D20', '#202125', '#18191C'] : ['#26282B', '#2B2D31', '#222427']);
  const side = (face, dark) => faceCells(iso, 0, 0, 0, 4, 4, 4, face, 8, 8, (i, j) => {
    if (j === 0) return mix(C.G, C.base, dark ? 0.5 : 0.3);
    if (j === 1 && r() < 0.55) return mix(C.G, C.base, dark ? 0.62 : 0.45);
    const o = ore.get(`${i},${j}`); if (o && face === 'left') return o;
    if (o && face === 'right') return mix(o, C.base, 0.35);
    const p = sidePal(dark); return p[Math.floor(r() * p.length)];
  }, 0.06);
  s += side('left', false) + side('right', true);
  front.forEach((m) => (s += mini(m)));
  s += ring(true);

  // verified badge
  const [bx0, by0] = iso(4, 4, 4); const bx = bx0 - 18, by = by0 + 4;
  s += `<circle cx="${bx + 18}" cy="${by - 6}" r="17" fill="${C.card}" stroke="rgba(255,255,255,0.1)"/><circle cx="${bx + 18}" cy="${by - 6}" r="12" fill="${C.ok}"/><path d="M${bx + 12.5} ${by - 6} l4 4 l7.5 -8" stroke="${C.base}" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"/>`;
  // sparkles
  s += spark(108, 78, 9, C.ink, 0.85) + spark(126, 104, 4, C.ink, 0.5) + spark(540, 238, 7, C.G, 0.9) + spark(520, 64, 5, C.ink, 0.6);
  return svg(s);
}

// ======================================================= SERVERS
function servers() {
  gid = 0;
  const S = 23, iso = isoFactory(330, 205, S);
  let s = dotField(320, 180, 330, 190);
  s += glow(...iso(2.5, 1.75, 3.4), 200, C.ok, 0.16);
  s += groundShadow(...iso(2.4, 1.9, -0.8), 200, 72, 0.55);
  s += box(iso, -1.8, -1.8, -0.7, 8.6, 7.2, 0.7, neutral);
  s += faceCells(iso, -1.8, -1.8, -0.7, 8.6, 7.2, 0.7, 'top', 14, 12, (i, j) => ((i + j) % 2 ? null : 'rgba(255,255,255,0.025)'), 0.1);

  // three blades, bottom to top
  const bw = 5, bd = 3.5, bh = 1.15, gap = 0.28;
  const blades = [{ led: C.V, sleep: true }, { led: C.ok }, { led: C.ok }];
  blades.forEach((b, k) => {
    const z = k * (bh + gap);
    s += box(iso, 0, 0, z, bw, bd, bh, { top: '#2C2E33', left: '#222428', right: '#1A1B1E' }, { edge: 'rgba(255,255,255,0.1)' });
    // front (left face): LED, vents, drive bays
    const onFace = (u, v) => iso(u, bd, z + bh - v);
    const [lx, ly] = onFace(0.45, bh / 2);
    if (!b.sleep) s += glow(lx, ly, 22, b.led, 0.55);
    s += `<circle cx="${lx}" cy="${ly}" r="3.6" fill="${b.led}" ${b.sleep ? 'opacity="0.9"' : ''}/>`;
    for (let i = 0; i < 6; i++) {
      const u0 = 1.05 + i * 0.32, a = onFace(u0, 0.34), c = onFace(u0, bh - 0.34);
      s += `<line x1="${a[0]}" y1="${a[1]}" x2="${c[0]}" y2="${c[1]}" stroke="rgba(255,255,255,0.09)" stroke-width="2" stroke-linecap="round"/>`;
    }
    for (let i = 0; i < 3; i++) {
      const u0 = 3.25 + i * 0.52;
      const q = [onFace(u0, 0.28), onFace(u0 + 0.38, 0.28), onFace(u0 + 0.38, bh - 0.28), onFace(u0, bh - 0.28)];
      s += poly(q, '#141517', 'stroke="rgba(255,255,255,0.06)"');
      const act = (k + i) % 3 === 0 && !b.sleep;
      const d0 = onFace(u0 + 0.12, 0.45);
      s += `<circle cx="${d0[0]}" cy="${d0[1]}" r="1.4" fill="${act ? C.ok : 'rgba(255,255,255,0.18)'}"/>`;
    }
  });

  // players — pixel heads, linked to the rack
  const heads = [{ x: -4.2, y: 4.2, z: 2.6, hue: C.V, blade: 2 }, { x: -3.2, y: 6.6, z: 0.9, hue: C.O, blade: 1 }, { x: 3.4, y: 5.4, z: 0.0, hue: C.Cy, blade: 0 }];
  const top = iso(2.5, 1.75, 3 * (bh + gap));
  heads.forEach((h, n) => {
    const sz = 1.1;
    const [hx, hy] = iso(h.x + sz / 2, h.y + sz / 2, h.z + sz);
    const [tx, ty] = iso(0.45, bd, h.blade * (bh + gap) + bh / 2);
    const [fx, fy] = iso(h.x + sz, h.y + sz / 2, h.z + sz / 2);
    const mx = (fx + tx) / 2, my = Math.min(fy, ty) - 26;
    const asleep = h.blade === 0;
    s += `<path d="M${fx} ${fy} Q${mx} ${my} ${tx - 6} ${ty}" stroke="${asleep ? C.V : h.hue}" stroke-opacity="${asleep ? 0.55 : 0.5}" stroke-width="1.4" stroke-dasharray="1.5 6" stroke-linecap="round"/>`;
  });
  heads.forEach((h) => {
    const sz = 1.1;
    s += groundShadow(...iso(h.x + sz / 2, h.y + sz / 2, -0.4), 22, 8, 0.4);
    s += box(iso, h.x, h.y, h.z, sz, sz, sz, shades(h.hue), { edge: 'rgba(255,255,255,0.2)' });
    // face on the front (left) face: two eyes and a mouth
    s += faceCells(iso, h.x, h.y, h.z, sz, sz, sz, 'left', 4, 4, (i, j) => ((j === 1 && (i === 0 || i === 3)) ? '#0E0F10' : (j === 3 && (i === 1 || i === 2)) ? mix(h.hue, C.base, 0.6) : null), 0.1);
  });

  // sleeping moon
  const mx = 520, my = 70;
  s += glow(mx, my, 60, C.V, 0.22);
  s += `<defs><mask id="moon"><rect width="${W}" height="${H}" fill="#fff"/><circle cx="${mx + 11}" cy="${my - 8}" r="21" fill="#000"/></mask></defs><circle cx="${mx}" cy="${my}" r="22" fill="${C.ink}" mask="url(#moon)"/>`;
  const z = (x, y, k, o) => `<path d="M${x} ${y} h${k} l-${k} ${k} h${k}" stroke="${C.ink}" stroke-opacity="${o}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>`;
  s += z(554, 44, 9, 0.8) + z(572, 28, 6, 0.55);
  s += spark(112, 92, 7, C.ink, 0.7) + spark(566, 232, 6, C.ok, 0.85) + spark(452, 38, 4, C.ink, 0.5);
  return svg(s);
}

// ======================================================= QUESTS
function quests() {
  gid = 0;
  const S = 21, iso = isoFactory(300, 262, S);
  let s = dotField(320, 180, 330, 190);
  const steps = 5, sw = 2.2, sd = 2.0;
  const stepAt = (i) => ({ x: 0, y: -i * sd + 2.4, h: 0.55 + i * 0.72 });
  const last = stepAt(steps - 1);
  const [tx, ty] = iso(last.x + sw / 2, last.y + sd / 2, last.h);
  s += glow(tx, ty - 60, 170, C.O, 0.24);
  s += groundShadow(...iso(1.1, -1.6, -0.7), 210, 62, 0.55);
  s += box(iso, -1.3, -6.9, -0.7, 4.8, 12.3, 0.7, neutral);
  s += faceCells(iso, -1.3, -6.9, -0.7, 4.8, 12.3, 0.7, 'top', 8, 20, (i, j) => ((i + j) % 2 ? null : 'rgba(255,255,255,0.025)'), 0.1);

  // path line between step centres
  let path = '';
  for (let i = 0; i < steps; i++) { const st = stepAt(i); const p = iso(st.x + sw / 2, st.y + sd / 2, st.h); path += (i ? ' L' : 'M') + p.join(' '); }

  const hues = [C.V, C.V, C.Cy, C.Cy, C.O];
  // draw back-to-front: higher steps are further back (smaller y) — draw them first
  for (let i = steps - 1; i >= 0; i--) {
    const st = stepAt(i);
    s += box(iso, st.x, st.y, 0, sw, sd, st.h, { top: '#2C2E33', left: '#222428', right: '#1A1B1E' }, { edge: 'rgba(255,255,255,0.1)' });
    const done = i < 3;
    // milestone tile inset on top
    const m = 0.55;
    s += box(iso, st.x + m, st.y + m, st.h, sw - 2 * m, sd - 2 * m, 0.12, done || i === steps - 1 ? shades(hues[i]) : { top: '#34363B', left: '#26282C', right: '#1E1F22' }, { edge: 'rgba(255,255,255,0.14)' });
    if (done) { const [cx, cy] = iso(st.x + sw / 2, st.y + sd / 2, st.h + 0.12); s += glow(cx, cy, 30, hues[i], 0.45); }
  }
  s += `<path d="${path}" stroke="${C.ink}" stroke-opacity="0.18" stroke-width="1.4" stroke-dasharray="1.5 6" stroke-linecap="round"/>`;

  // trophy sprite, standing on the last step
  const T = [
    '..OOOOOOOO..',
    'HOOWOOOOOOOH',
    'H.OWOOOOOO.H',
    'H.OWOOOOOO.H',
    '.H.OOOOOO.H.',
    '...OOOOOO...',
    '....OOOO....',
    '.....OO.....',
    '.....OO.....',
    '....dddd....',
    '...DDDDDD...',
    '...DDDDDD...',
  ];
  const px = 5.6, sx = tx - (T[0].length * px) / 2, sy = ty - T.length * px - 6;
  const pal = { O: C.O, W: '#FFE3D6', H: mix(C.O, C.base, 0.25), d: '#3A3C41', D: '#2C2E33' };
  s += groundShadow(tx, ty - 2, 34, 9, 0.45);
  T.forEach((row, j) => [...row].forEach((c, i) => { if (c !== '.') s += `<rect x="${(sx + i * px).toFixed(1)}" y="${(sy + j * px).toFixed(1)}" width="${px - 0.6}" height="${px - 0.6}" rx="1.2" fill="${pal[c]}"/>`; }));
  s += spark(tx + 46, sy + 4, 8, C.ink, 0.9) + spark(tx - 50, sy + 22, 5, C.O, 0.8) + spark(tx + 28, sy - 16, 4, C.ink, 0.6);
  const gem = (cx, cy, r, hue) => `<g>${glow(cx, cy, r * 3, hue, 0.35)}<path d="M${cx} ${cy - r} L${cx + r * 0.8} ${cy} L${cx} ${cy + r * 1.1} L${cx - r * 0.8} ${cy}Z" fill="${mix(hue, C.base, 0.3)}"/><path d="M${cx} ${cy - r} L${cx + r * 0.8} ${cy} L${cx} ${cy + r * 0.25} L${cx - r * 0.8} ${cy}Z" fill="${hue}"/><path d="M${cx} ${cy - r} L${cx - r * 0.8} ${cy} L${cx} ${cy + r * 0.25}Z" fill="#fff" opacity="0.25"/></g>`;
  s += gem(150, 112, 16, C.V) + gem(104, 176, 10, C.Cy) + gem(190, 64, 8, C.O);
  s += spark(210, 128, 5, C.ink, 0.6) + spark(78, 118, 4, C.ink, 0.45) + spark(540, 250, 6, C.O, 0.7);
  return svg(s);
}

const out = { modpacks: modpacks(), servers: servers(), quests: quests() };
for (const [k, v] of Object.entries(out)) writeFileSync(new URL(`./${k}.svg`, import.meta.url), v);
writeFileSync(new URL('./preview.html', import.meta.url), `<!doctype html><body style="margin:0;background:#0B0B0C;display:grid;gap:24px;padding:24px;grid-template-columns:repeat(3,640px)">${Object.values(out).map((v) => `<div style="background:#131315;border-radius:18px">${v}</div>`).join('')}</body>`);
console.log('ok', Object.fromEntries(Object.entries(out).map(([k, v]) => [k, v.length])));
