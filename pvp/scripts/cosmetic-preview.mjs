/**
 * Pixel-faithful previews of the bundled cosmetics, drawn from the same compiled assets the mod
 * loads, so the launcher Store and the in-game wardrobe can never drift from the game.
 *
 * A tiny software rasteriser: the compiled runtime model (`model.json` as gen-cosmetics writes it:
 * bones with pivot and rest rotation, cubes with per-face UV) is posed with the same bone
 * transform `ModelDraw` uses (translate to the pivot, rotate Z then Y then X, translate back),
 * turned by a view yaw and pitch, projected orthographically and z-buffered, every pixel sampling
 * its texel nearest-neighbour. The emissive layer is added on top, the way the shader adds it.
 *
 * Used by `scripts/gen-cosmetics.mjs` (the launcher's PNGs and the wardrobe's sprite grids) and,
 * standalone, to look at the art without a game:
 *
 *   node scripts/cosmetic-preview.mjs <out-dir> [id ...]   # one contact sheet per kind
 */

import { readFileSync, existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { inflateSync } from 'node:zlib';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

import { encodePng } from '../mod/cosmetics-art/lib.mjs';

/* ------------------------------------------------------------------------------ PNG decode */

/** Decodes an 8-bit RGBA or RGB, non-interlaced PNG into { w, h, px: Uint8ClampedArray RGBA }. */
export function decodePng(buf) {
  let p = 8;
  let w = 0, h = 0, type = 6, depth = 8;
  const idat = [];
  let plte = null, trns = null;
  while (p < buf.length) {
    const len = buf.readUInt32BE(p);
    const t = buf.toString('ascii', p + 4, p + 8);
    const d = buf.subarray(p + 8, p + 8 + len);
    if (t === 'IHDR') {
      w = d.readUInt32BE(0);
      h = d.readUInt32BE(4);
      depth = d[8];
      type = d[9];
      if (d[12] !== 0) throw new Error('interlaced PNG');
    } else if (t === 'PLTE') plte = d;
    else if (t === 'tRNS') trns = d;
    else if (t === 'IDAT') idat.push(d);
    p += 12 + len;
  }
  if (depth !== 8) throw new Error(`PNG bit depth ${depth}`);
  const bpp = type === 6 ? 4 : type === 2 ? 3 : type === 3 ? 1 : type === 4 ? 2 : 1;
  const raw = inflateSync(Buffer.concat(idat));
  const stride = w * bpp;
  const out = new Uint8Array(w * h * bpp);
  for (let y = 0; y < h; y++) {
    const f = raw[y * (stride + 1)];
    for (let x = 0; x < stride; x++) {
      const v = raw[y * (stride + 1) + 1 + x];
      const a = x >= bpp ? out[y * stride + x - bpp] : 0;
      const b = y > 0 ? out[(y - 1) * stride + x] : 0;
      const c = x >= bpp && y > 0 ? out[(y - 1) * stride + x - bpp] : 0;
      let r = v;
      if (f === 1) r = v + a;
      else if (f === 2) r = v + b;
      else if (f === 3) r = v + ((a + b) >> 1);
      else if (f === 4) {
        const pp = a + b - c, pa = Math.abs(pp - a), pb = Math.abs(pp - b), pc = Math.abs(pp - c);
        r = v + (pa <= pb && pa <= pc ? a : pb <= pc ? b : c);
      }
      out[y * stride + x] = r & 255;
    }
  }
  const px = new Uint8ClampedArray(w * h * 4);
  for (let i = 0; i < w * h; i++) {
    if (type === 6) for (let k = 0; k < 4; k++) px[i * 4 + k] = out[i * 4 + k];
    else if (type === 2) { px[i * 4] = out[i * 3]; px[i * 4 + 1] = out[i * 3 + 1]; px[i * 4 + 2] = out[i * 3 + 2]; px[i * 4 + 3] = 255; }
    else if (type === 3) { const k = out[i]; px[i * 4] = plte[k * 3]; px[i * 4 + 1] = plte[k * 3 + 1]; px[i * 4 + 2] = plte[k * 3 + 2]; px[i * 4 + 3] = trns && k < trns.length ? trns[k] : 255; }
    else if (type === 4) { px[i * 4] = px[i * 4 + 1] = px[i * 4 + 2] = out[i * 2]; px[i * 4 + 3] = out[i * 2 + 1]; }
    else { px[i * 4] = px[i * 4 + 1] = px[i * 4 + 2] = out[i]; px[i * 4 + 3] = 255; }
  }
  return { w, h, px };
}

/* ------------------------------------------------------------------------------ images */

export class Img {
  constructor(w, h) {
    this.w = w;
    this.h = h;
    this.px = new Float32Array(w * h * 4); // straight RGB 0..255, alpha 0..1
  }
  get(x, y) {
    const i = (y * this.w + x) * 4;
    return [this.px[i], this.px[i + 1], this.px[i + 2], this.px[i + 3]];
  }
  set(x, y, r, g, b, a = 1) {
    if (x < 0 || y < 0 || x >= this.w || y >= this.h) return;
    const i = (y * this.w + x) * 4;
    this.px[i] = r; this.px[i + 1] = g; this.px[i + 2] = b; this.px[i + 3] = a;
  }
  /** Source-over. */
  over(x, y, r, g, b, a) {
    if (x < 0 || y < 0 || x >= this.w || y >= this.h || a <= 0) return;
    const i = (y * this.w + x) * 4;
    const da = this.px[i + 3], oa = a + da * (1 - a);
    if (oa <= 0) return;
    this.px[i] = (r * a + this.px[i] * da * (1 - a)) / oa;
    this.px[i + 1] = (g * a + this.px[i + 1] * da * (1 - a)) / oa;
    this.px[i + 2] = (b * a + this.px[i + 2] * da * (1 - a)) / oa;
    this.px[i + 3] = oa;
  }
  png() {
    const out = new Uint8ClampedArray(this.w * this.h * 4);
    for (let i = 0; i < this.w * this.h; i++) {
      out[i * 4] = Math.round(this.px[i * 4]);
      out[i * 4 + 1] = Math.round(this.px[i * 4 + 1]);
      out[i * 4 + 2] = Math.round(this.px[i * 4 + 2]);
      out[i * 4 + 3] = Math.round(this.px[i * 4 + 3] * 255);
    }
    return encodePng(this.w, this.h, out);
  }
  /** Box-filters down by an integer factor (premultiplied, so edges stay clean). */
  down(k) {
    const o = new Img(Math.floor(this.w / k), Math.floor(this.h / k));
    for (let y = 0; y < o.h; y++) for (let x = 0; x < o.w; x++) {
      let r = 0, g = 0, b = 0, a = 0;
      for (let j = 0; j < k; j++) for (let i = 0; i < k; i++) {
        const [R, G, B, A] = this.get(x * k + i, y * k + j);
        r += R * A; g += G * A; b += B * A; a += A;
      }
      if (a > 0) o.set(x, y, r / a, g / a, b / a, a / (k * k));
    }
    return o;
  }
}

/* ------------------------------------------------------------------------------ math */

const mul = (a, b) => {
  const o = new Array(16).fill(0);
  for (let r = 0; r < 4; r++) for (let c = 0; c < 4; c++) for (let k = 0; k < 4; k++) o[r * 4 + c] += a[r * 4 + k] * b[k * 4 + c];
  return o;
};
const ident = () => [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1];
const tr = (x, y, z) => [1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z, 0, 0, 0, 1];
const rx = (a) => { const c = Math.cos(a), s = Math.sin(a); return [1, 0, 0, 0, 0, c, -s, 0, 0, s, c, 0, 0, 0, 0, 1]; };
const ry = (a) => { const c = Math.cos(a), s = Math.sin(a); return [c, 0, s, 0, 0, 1, 0, 0, -s, 0, c, 0, 0, 0, 0, 1]; };
const rz = (a) => { const c = Math.cos(a), s = Math.sin(a); return [c, -s, 0, 0, s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]; };
const ap = (m, p) => [
  m[0] * p[0] + m[1] * p[1] + m[2] * p[2] + m[3],
  m[4] * p[0] + m[5] * p[1] + m[6] * p[2] + m[7],
  m[8] * p[0] + m[9] * p[1] + m[10] * p[2] + m[11],
];

/** Bone world matrices, parent first: T(pivot) Rz Ry Rx T(-pivot), as ModelDraw does. */
function boneMatrices(model, extra = {}) {
  const out = [];
  model.bones.forEach((b, i) => {
    const [x, y, z] = b.pv;
    const add = extra[b.n] ?? [0, 0, 0];
    let m = mul(tr(x, y, z), mul(rz(b.r[2] + add[2]), mul(ry(b.r[1] + add[1]), mul(rx(b.r[0] + add[0]), tr(-x, -y, -z)))));
    if (b.p >= 0) m = mul(out[b.p], m);
    out[i] = m;
  });
  return out;
}

/**
 * The four corners of a cube face as [origin, u-end, v-end] in model space, matching the UV
 * conventions in docs/cosmetics.md (n: u +x; s: u -x; e: u +z; w: u -z; u/d: u +x, v -z; sides v
 * down).
 */
function faceFrame(f, k) {
  const [x0, y0, z0, x1, y1, z1] = f;
  switch (k) {
    case 'n': return [[x0, y0, z0], [x1, y0, z0], [x0, y1, z0], [0, 0, -1]];
    case 's': return [[x1, y0, z1], [x0, y0, z1], [x1, y1, z1], [0, 0, 1]];
    case 'e': return [[x1, y0, z0], [x1, y0, z1], [x1, y1, z0], [1, 0, 0]];
    case 'w': return [[x0, y0, z1], [x0, y0, z0], [x0, y1, z1], [-1, 0, 0]];
    case 'u': return [[x0, y0, z1], [x1, y0, z1], [x0, y0, z0], [0, -1, 0]];
    case 'd': return [[x0, y1, z1], [x1, y1, z1], [x0, y1, z0], [0, 1, 0]];
    default: return null;
  }
}

/* ------------------------------------------------------------------------------ render */

/**
 * Renders a compiled model.
 * @param model   the runtime model JSON
 * @param tex     decoded albedo ({w, h, px}), frame 0 is the top `model.tex[1]` rows
 * @param emi     decoded emissive layer or null
 * @param o.yaw   degrees, turning the model about its vertical axis (0: seen from the front)
 * @param o.pitch degrees, looking down onto it
 * @param o.scale screen pixels per model pixel
 * @param o.size  [w, h] of the image; the model is centred on its bounds unless o.anchor
 * @param o.anchor [mx, my]: the model point drawn at o.at ([sx, sy] screen)
 * @param o.shade  true: light the faces (top bright, sides by a key light); false: flat texels
 * @param o.emi    emissive gain (the jewel's light), 0..1
 * @param o.pose   extra rotation per bone name, radians [x, y, z] (spreads wings for a portrait)
 */
export function renderModel(model, tex, emi, o) {
  const [W, H] = o.size;
  const img = new Img(W, H);
  const zb = new Float32Array(W * H).fill(Infinity);
  const mats = boneMatrices(model, o.pose);
  const view = mul(rx((o.pitch ?? 0) * Math.PI / 180), ry((o.yaw ?? 0) * Math.PI / 180));
  const th = model.tex[1];
  const tw = model.tex[0];
  const sx = tex.w / tw; // texels per texture-size unit (1 unless a 2x sheet)
  // Light in view space (y down, -z toward the viewer): from the upper left, in front.
  const L = (() => { const v = [-0.45, -0.8, -0.5]; const n = Math.hypot(...v); return v.map((c) => c / n); })();

  const faces = [];
  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
  model.bones.forEach((b, bi) => {
    const m = mul(view, mats[bi]);
    for (const c of b.c ?? []) {
      for (const [k, rect] of Object.entries(c.uv)) {
        const fr = faceFrame(c.f, k);
        if (!fr) continue;
        const [O, U, V, n] = fr;
        const P0 = ap(m, O), PU = ap(m, U), PV = ap(m, V);
        const N = (() => { const z = ap(m, [0, 0, 0]); const q = ap(m, n); return [q[0] - z[0], q[1] - z[1], q[2] - z[2]]; })();
        faces.push({ P0, PU, PV, N, rect, mat: c.m ?? 0 });
        for (const P of [P0, PU, PV, [PU[0] + PV[0] - P0[0], PU[1] + PV[1] - P0[1]]]) {
          minX = Math.min(minX, P[0]); maxX = Math.max(maxX, P[0]);
          minY = Math.min(minY, P[1]); maxY = Math.max(maxY, P[1]);
        }
      }
    }
  });
  const s = o.scale;
  let ox, oy;
  if (o.anchor) {
    const a = ap(view, [o.anchor[0], o.anchor[1], o.anchor[2] ?? 0]);
    ox = o.at[0] - a[0] * s;
    oy = o.at[1] - a[1] * s;
  } else {
    ox = W / 2 - ((minX + maxX) / 2) * s;
    oy = H / 2 - ((minY + maxY) / 2) * s;
  }
  const sample = (img2, u, v) => {
    const x = Math.max(0, Math.min(img2.w - 1, Math.floor(u * sx)));
    const y = Math.max(0, Math.min(img2.h - 1, Math.floor(v * sx)));
    const i = (y * img2.w + x) * 4;
    return [img2.px[i], img2.px[i + 1], img2.px[i + 2], img2.px[i + 3]];
  };
  for (const f of faces) {
    const ax = (f.PU[0] - f.P0[0]) * s, ay = (f.PU[1] - f.P0[1]) * s;
    const bx = (f.PV[0] - f.P0[0]) * s, by = (f.PV[1] - f.P0[1]) * s;
    const det = ax * by - ay * bx;
    if (Math.abs(det) < 1e-6) continue;
    const x0 = f.P0[0] * s + ox, y0 = f.P0[1] * s + oy;
    const xs = [x0, x0 + ax, x0 + bx, x0 + ax + bx], ys = [y0, y0 + ay, y0 + by, y0 + ay + by];
    const X0 = Math.max(0, Math.floor(Math.min(...xs))), X1 = Math.min(W - 1, Math.ceil(Math.max(...xs)));
    const Y0 = Math.max(0, Math.floor(Math.min(...ys))), Y1 = Math.min(H - 1, Math.ceil(Math.max(...ys)));
    const [u0, v0, u1, v1] = f.rect;
    const uLo = Math.min(u0, u1), uHi = Math.max(u0, u1) - 1e-4, vLo = Math.min(v0, v1), vHi = Math.max(v0, v1) - 1e-4;
    // Lighting: Lambert from the key light, a floor of ambient, metal a touch brighter.
    const nl = Math.hypot(...f.N) || 1;
    const N = f.N.map((c) => c / nl);
    const lam = Math.max(0, -(N[0] * L[0] + N[1] * L[1] + N[2] * L[2])) ;
    const facing = Math.abs(N[2]);
    let light = o.shade ? 0.62 + 0.38 * Math.max(lam, Math.abs(N[0] * L[0] + N[1] * L[1] + N[2] * L[2]) * 0.55) : 1;
    if (o.shade && f.mat === 2) light = Math.min(1.12, light + 0.08 * facing);
    for (let py = Y0; py <= Y1; py++) for (let px = X0; px <= X1; px++) {
      const dx = px + 0.5 - x0, dy = py + 0.5 - y0;
      const sP = (dx * by - dy * bx) / det, tP = (ax * dy - ay * dx) / det;
      if (sP < 0 || sP > 1 || tP < 0 || tP > 1) continue;
      const z = f.P0[2] + sP * (f.PU[2] - f.P0[2]) + tP * (f.PV[2] - f.P0[2]);
      const zi = py * W + px;
      if (z >= zb[zi] - 1e-5) continue;
      const u = Math.max(uLo, Math.min(uHi, u0 + sP * (u1 - u0)));
      const v = Math.max(vLo, Math.min(vHi, v0 + tP * (v1 - v0)));
      const t = sample(tex, u, v);
      if (t[3] < 26) continue;
      zb[zi] = z;
      let r = t[0] * light, g = t[1] * light, b = t[2] * light;
      if (emi && o.emi) {
        const e = sample(emi, u, v);
        const k = (e[3] / 255) * o.emi;
        r += e[0] * k; g += e[1] * k; b += e[2] * k;
        if (o.glowOut && e[3] > 0) o.glowOut.push([px, py, e[0], e[1], e[2], e[3] / 255]);
      }
      img.set(px, py, Math.min(255, r), Math.min(255, g), Math.min(255, b), 1);
    }
  }
  return img;
}

/** A soft additive halo under the emissive texels (the bloom, for a still picture). */
export function addGlow(img, points, radius, gain) {
  if (!points.length) return img;
  // One light value per pixel first, so a big texel does not glow harder for covering more samples.
  const src = new Float32Array(img.w * img.h * 3);
  for (const [x, y, r, g, b, a] of points) {
    if (x < 0 || y < 0 || x >= img.w || y >= img.h) continue;
    const q = (y * img.w + x) * 3;
    src[q] = Math.max(src[q], r * a); src[q + 1] = Math.max(src[q + 1], g * a); src[q + 2] = Math.max(src[q + 2], b * a);
  }
  // Separable normalised Gaussian.
  const R = Math.ceil(radius * 2.5);
  const k = []; let ks = 0;
  for (let i = -R; i <= R; i++) { const v = Math.exp(-(i * i) / (2 * radius * radius)); k.push(v); ks += v; }
  const kn = k.map((v) => v / ks);
  const pass = (from, horiz) => {
    const to = new Float32Array(from.length);
    for (let y = 0; y < img.h; y++) for (let x = 0; x < img.w; x++) {
      let r = 0, g = 0, b = 0;
      for (let i = -R; i <= R; i++) {
        const X = horiz ? x + i : x, Y = horiz ? y : y + i;
        if (X < 0 || Y < 0 || X >= img.w || Y >= img.h) continue;
        const q = (Y * img.w + X) * 3, w = kn[i + R];
        r += from[q] * w; g += from[q + 1] * w; b += from[q + 2] * w;
      }
      const o = (y * img.w + x) * 3;
      to[o] = r; to[o + 1] = g; to[o + 2] = b;
    }
    return to;
  };
  const acc = pass(pass(src, true), false);
  const out = new Img(img.w, img.h);
  for (let y = 0; y < img.h; y++) for (let x = 0; x < img.w; x++) {
    const q = (y * img.w + x) * 3;
    const gr = acc[q] * gain, gg = acc[q + 1] * gain, gb = acc[q + 2] * gain;
    const ga = Math.min(1, Math.max(gr, gg, gb) / 255);
    if (ga > 0.004) out.set(x, y, Math.min(255, gr / ga), Math.min(255, gg / ga), Math.min(255, gb / ga), ga);
    const [r, g, b, a] = img.get(x, y);
    if (a > 0) out.over(x, y, Math.min(255, r + gr * 0.3), Math.min(255, g + gg * 0.3), Math.min(255, b + gb * 0.3), a);
  }
  return out;
}

/* ------------------------------------------------------------------------------ capes */

/** The cape's outer face (seen from behind) or lining (from the front), frame 0, 10 x 16 texels at any sheet scale. */
export function capeFace(tex, emi, which = 'outer', emiGain = 0.8) {
  const k = tex.w / 64;
  const out = new Img(10 * k, 16 * k);
  const x0 = (which === 'outer' ? 1 : 12) * k, y0 = k;
  for (let y = 0; y < 16 * k; y++) for (let x = 0; x < 10 * k; x++) {
    const i = ((y0 + y) * tex.w + x0 + x) * 4;
    const a = tex.px[i + 3];
    if (a < 26) continue;
    let r = tex.px[i], g = tex.px[i + 1], b = tex.px[i + 2];
    if (emi) {
      const e = emi.px[i + 3] / 255 * emiGain;
      r += emi.px[i] * e; g += emi.px[i + 1] * e; b += emi.px[i + 2] * e;
    }
    out.set(x, y, Math.min(255, r), Math.min(255, g), Math.min(255, b), 1);
  }
  return out;
}

/* ------------------------------------------------------------------------------ assets */

const HERE = dirname(fileURLToPath(import.meta.url));
export const ASSETS = join(HERE, '..', 'mod', 'src', 'main', 'resources', 'assets', 'void', 'cosmetics');

/** Loads an item's compiled bundle (model/cape/trail/badge JSON and decoded layers) from `dir`. */
export function loadBundle(dir, read = (p) => (existsSync(p) ? readFileSync(p) : null)) {
  const j = (f) => { const b = read(join(dir, f)); return b ? JSON.parse(b.toString('utf8')) : null; };
  const p = (f) => { const b = read(join(dir, f)); return b ? decodePng(b) : null; };
  return {
    model: j('model.json'), cape: j('cape.json'), trail: j('trail.json'), badge: j('badge.json'),
    texture: p('texture.png'), emissive: p('emissive.png'), sprite: p('sprite.png'), icon: p('icon.png'),
    iconEmi: p('emissive.png'),
  };
}

/** A portrait view per slot: what the Store shows. */
export const PORTRAIT = {
  back: { yaw: 196, pitch: 10, pose: { wing_r: [0, 0.25, 0], wing_l: [0, -0.25, 0] } },
  head: { yaw: 28, pitch: 18 },
  face: { yaw: 22, pitch: 8 },
  halo: { yaw: 20, pitch: 30 },
};

/** Renders the Store portrait of a model item: supersampled, lit, with the jewel's glow. */
export function portrait(b, kind, size = 360, fill = 0.78) {
  const v = PORTRAIT[kind] ?? PORTRAIT.head;
  const k = 4;
  // First pass to size it: render small, measure the bounds, then fit.
  const probe = renderModel(b.model, b.texture, null, { ...v, scale: 4, size: [600, 600], shade: false });
  let x0 = 600, y0 = 600, x1 = 0, y1 = 0;
  for (let y = 0; y < 600; y++) for (let x = 0; x < 600; x++) if (probe.px[(y * 600 + x) * 4 + 3] > 0) {
    x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y);
  }
  const ext = Math.max(x1 - x0, y1 - y0) / 4;
  const scale = (size * fill) / Math.max(1, ext);
  const glow = [];
  const big = renderModel(b.model, b.texture, b.emissive, { ...v, scale: scale * k, size: [size * k, size * k], shade: true, emi: 0.3, glowOut: glow });
  const small = big.down(k);
  const pts = glow.map(([x, y, r, g, bb, a]) => [Math.floor(x / k), Math.floor(y / k), r, g, bb, a]);
  return addGlow(small, pts, size / 40, 5);
}

/* ------------------------------------------------------------------------------ UI art */

/** Nearest-neighbour integer upscale. */
export function upscale(img, k) {
  const o = new Img(img.w * k, img.h * k);
  for (let y = 0; y < o.h; y++) for (let x = 0; x < o.w; x++) {
    const [r, g, b, a] = img.get(Math.floor(x / k), Math.floor(y / k));
    if (a > 0) o.set(x, y, r, g, b, a);
  }
  return o;
}

function bounds(img) {
  let x0 = img.w, y0 = img.h, x1 = -1, y1 = -1;
  for (let y = 0; y < img.h; y++) for (let x = 0; x < img.w; x++) if (img.px[(y * img.w + x) * 4 + 3] > 0) {
    x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y);
  }
  return x1 < 0 ? null : [x0, y0, x1, y1];
}

/** A trail's particle sprite tinted by colour `c` (the runtime multiplies it by a vertex colour). */
function tinted(sprite, c, k = 1) {
  const o = new Img(sprite.w * k, sprite.h * k);
  const R = (c >> 16) & 255, G = (c >> 8) & 255, B = c & 255;
  for (let y = 0; y < o.h; y++) for (let x = 0; x < o.w; x++) {
    const i = (Math.floor(y / k) * sprite.w + Math.floor(x / k)) * 4;
    if (sprite.px[i + 3] < 26) continue;
    o.set(x, y, (sprite.px[i] * R) / 255, (sprite.px[i + 1] * G) / 255, (sprite.px[i + 2] * B) / 255, 1);
  }
  return o;
}

function blit(dst, src, ox, oy) {
  for (let y = 0; y < src.h; y++) for (let x = 0; x < src.w; x++) {
    const [r, g, b, a] = src.get(x, y);
    if (a > 0) dst.over(ox + x, oy + y, r, g, b, a);
  }
}

/** A badge icon's first frame with its emissive layer laid in (lit texels take their light colour). */
function badgeFace(b) {
  const o = new Img(9, 9);
  for (let y = 0; y < 9; y++) for (let x = 0; x < 9; x++) {
    const i = (y * b.icon.w + x) * 4;
    if (b.icon.px[i + 3] < 26) continue;
    let r = b.icon.px[i], g = b.icon.px[i + 1], bl = b.icon.px[i + 2];
    if (b.iconEmi && b.iconEmi.px[i + 3] > 128) { r = b.iconEmi.px[i]; g = b.iconEmi.px[i + 1]; bl = b.iconEmi.px[i + 2]; }
    o.set(x, y, r, g, bl, 1);
  }
  return o;
}

/** The wardrobe view per slot: straight on (from behind for back items), flat texels. */
const SPRITE_VIEW = {
  back: { yaw: 180, pitch: 0, s: 1 },
  head: { yaw: 0, pitch: 12, s: 2 },
  face: { yaw: 0, pitch: 0, s: 2 },
  halo: { yaw: 0, pitch: 34, s: 2 },
};

/**
 * The in-game wardrobe's pixel sprite of an item: a 32 x 32 grid of its real texels (flat, as the
 * texture paints them, no lighting), cropped and centred, plus where it sits on the paper doll —
 * the doll is Minecraft's proportions at half a doll cell per model pixel, the neck at (8, 4) — as
 * `{ x: centre, y: top, size }` in doll cells (the whole 32-cell sprite's edge). Null for emotes.
 */
export function wardrobeSprite(item, b, N = 32) {
  let img, ax, ay, s;
  if (item.kind === 'cape') {
    s = 2;
    img = upscale(capeFace(b.texture, null, 'outer'), s);
    ax = img.w / 2; ay = 0; // the collar's centre sits at the neck
  } else if (item.kind === 'badge') {
    img = upscale(badgeFace(b), 3);
    s = 3; ax = img.w / 2; ay = 0;
  } else if (item.kind === 'trail') {
    const cols = b.trail.colors;
    img = new Img(N, N);
    blit(img, tinted(b.sprite, cols[0], 2), 1, 15);
    blit(img, tinted(b.sprite, cols[1 % cols.length], 2), 12, 6);
    blit(img, tinted(b.sprite, cols[2 % cols.length], 1), 23, 1);
    return { grid: img, doll: { x: 3.2, y: 10.5, size: 6 } };
  } else if (b.model) {
    const v = SPRITE_VIEW[item.kind];
    const W = 128;
    // The largest whole number of sprite pixels per model pixel that fits the grid (at most 3),
    // or, for an item wider than the grid at one (spread wings), a little under one.
    const probe = renderModel(b.model, b.texture, null, { yaw: v.yaw, pitch: v.pitch, scale: 1, size: [W, W], anchor: [0, 0, 0], at: [W / 2, W * 0.6], shade: false });
    const pb = bounds(probe);
    const ext = pb ? Math.max(pb[2] - pb[0] + 1, pb[3] - pb[1] + 1) : 1;
    const fit = N - 2;
    s = ext > fit ? fit / ext : Math.max(1, Math.min(3, Math.floor(fit / ext)));
    img = renderModel(b.model, b.texture, null, { yaw: v.yaw, pitch: v.pitch, scale: s, size: [W, W], anchor: [0, 0, 0], at: [W / 2, W * 0.6], shade: false });
    ax = W / 2; ay = W * 0.6;
  } else {
    return null;
  }
  const bb = bounds(img);
  const out = new Img(N, N);
  if (!bb) return { grid: out, doll: null };
  const w = bb[2] - bb[0] + 1, h = bb[3] - bb[1] + 1;
  const ox = Math.floor((N - w) / 2) - bb[0], oy = Math.floor((N - h) / 2) - bb[1];
  for (let y = bb[1]; y <= bb[3]; y++) for (let x = bb[0]; x <= bb[2]; x++) {
    const [r, g, bl, a] = img.get(x, y);
    if (a > 0) out.set(x + ox, y + oy, r, g, bl, 1);
  }
  const k = 0.5 / s; // doll cells per sprite pixel
  const doll = item.kind === 'badge' ? null : {
    x: +(8 + (N / 2 - (ax + ox)) * k).toFixed(3),
    y: +(4 - (ay + oy) * k).toFixed(3),
    size: +(N * k).toFixed(3),
  };
  return { grid: out, doll };
}

/**
 * The launcher Store's pictures of an item, as [file name, image] pairs: a lit, supersampled
 * three-quarter portrait for a model; the cape's face and lining at their native 10 x 16 (the
 * page scales them with nearest-neighbour); a trail's particles along an arc; a badge's 9 x 9.
 */
export function storeArt(item, b) {
  if (b.model) {
    const kind = item.kind === 'back' ? 'back' : item.kind === 'halo' ? 'halo' : item.kind === 'face' ? 'face' : 'head';
    return [[`${item.id}.png`, portrait(b, kind, 360)]];
  }
  if (b.cape) {
    return [[`${item.id}.png`, capeFace(b.texture, b.emissive, 'outer', 0.5)], [`${item.id}-lining.png`, capeFace(b.texture, null, 'lining')]];
  }
  if (b.trail) {
    const W = 360, H = 200;
    const img = new Img(W, H);
    const cols = b.trail.colors;
    const glow = [];
    // Newest at the top right, the oldest falling away to the lower left, smaller and fewer.
    const P = [[262, 50, 6], [210, 82, 5], [166, 108, 4], [126, 130, 4], [94, 146, 3], [66, 160, 2], [44, 170, 2]];
    P.forEach(([x, y, k], i) => {
      const t = tinted(b.sprite, cols[i % cols.length], k);
      blit(img, t, Math.round(x - t.w / 2), Math.round(y - t.h / 2));
      if (b.trail.glow > 0) {
        for (let yy = 0; yy < t.h; yy++) for (let xx = 0; xx < t.w; xx++) {
          const [r, g, bl, a] = t.get(xx, yy);
          if (a > 0) glow.push([Math.round(x - t.w / 2) + xx, Math.round(y - t.h / 2) + yy, r, g, bl, b.trail.glow * 0.6]);
        }
      }
    });
    return [[`${item.id}.png`, glow.length ? addGlow(img, glow, 7, 1.6) : img]];
  }
  if (b.icon) return [[`${item.id}.png`, badgeFace(b)]];
  return [];
}

/* ------------------------------------------------------------------------------ CLI */

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const out = process.argv[2];
  if (!out) {
    console.error('usage: node scripts/cosmetic-preview.mjs <out-dir> [id ...]');
    process.exit(2);
  }
  mkdirSync(out, { recursive: true });
  const index = JSON.parse(readFileSync(join(ASSETS, 'index.json'), 'utf8')).ids;
  const ids = process.argv.slice(3).length ? process.argv.slice(3) : index;
  for (const id of ids) {
    const b = loadBundle(join(ASSETS, id));
    if (b.model) {
      for (const kind of ['portrait']) {
        const k = b.model.attach === 'body' ? 'back' : b.model.attach === 'halo' ? 'halo' : 'head';
        writeFileSync(join(out, `${id}.png`), portrait(b, k, 360).png());
      }
      // Front and back at 8x, flat: the texel truth.
      for (const [name, yaw] of [['front', 0], ['back', 180]]) {
        const img = renderModel(b.model, b.texture, b.emissive, { yaw, pitch: 0, scale: 8, size: [320, 320], shade: true, emi: 0.8 });
        writeFileSync(join(out, `${id}-${name}.png`), img.png());
      }
    } else if (b.cape) {
      const k = 16;
      for (const which of ['outer', 'lining']) {
        const f = capeFace(b.texture, b.emissive, which);
        const big = new Img(f.w * k, f.h * k);
        for (let y = 0; y < big.h; y++) for (let x = 0; x < big.w; x++) { const [r, g, bb, a] = f.get(Math.floor(x / k), Math.floor(y / k)); if (a) big.set(x, y, r, g, bb, a); }
        writeFileSync(join(out, `${id}-${which}.png`), big.png());
      }
    }
  }
  console.log(`preview: ${ids.length} item(s) into ${out}`);
}
