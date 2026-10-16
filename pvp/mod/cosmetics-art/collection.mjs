/**
 * VOID luxe — the collection's palette and the shared brushes every launch item is painted with
 * (docs/cosmetics.md, "The collection: VOID luxe"). Nothing in the launch set is painted outside
 * these ramps: warm light neutrals, four metals, and six jewels, each a short hand-picked ramp,
 * dark to light, shadows cool and lights warm.
 */

import { Atlas, Canvas } from './lib.mjs';

/* ================================================================================ palette */

/** Neutrals: cloth, felt, porcelain, lacquer. */
export const N = {
  ivory: ['#6f6a72', '#a39d9c', '#cfc6b8', '#e8dfcd', '#f8f2e4'],
  bone: ['#5f5652', '#8f8172', '#bba98c', '#d9c9a8', '#efe4c8'],
  taupe: ['#2c2a33', '#45414a', '#625c60', '#827a77', '#a59c94'],
  charcoal: ['#121117', '#1c1a22', '#28252e', '#36323b'],
  ink: '#0d0c12',
};

/** Metals: trim, clasps, bands, settings, spars. */
export const M = {
  gold: ['#3d2410', '#7a4f1c', '#c08a35', '#eec064', '#fff1bf'],
  brass: ['#3a2c1c', '#6e5630', '#a8894f', '#d4b878', '#f2e4b4'],
  silver: ['#2e3140', '#5f6577', '#9da3b1', '#d3d7df', '#fbfcff'],
  steel: ['#2a2d33', '#4a4f57', '#717780', '#9aa0a8', '#c0c5cb'],
};

/** The jewels: deep, shadow, base, light, sparkle. */
export const J = {
  amethyst: ['#251a5c', '#4f3db3', '#9f8bff', '#d2c8ff', '#f7f4ff'],
  garnet: ['#4a1220', '#a8343a', '#ff9e7a', '#ffcdb4', '#fff4ec'],
  topaz: ['#0f3550', '#2582b5', '#7adfff', '#c2f2ff', '#f2fdff'],
  jade: ['#0c3a30', '#258a66', '#7ae0b0', '#c0f4da', '#f0fff7'],
  pearl: ['#7d7890', '#b9b4c8', '#e9e5f0', '#f9f7fc', '#ffffff'],
  onyx: ['#08080c', '#14141b', '#24242f', '#454556', '#b8b8d0'],
};

/**
 * The twinkle: how bright a jewel's light is on frame f of an n-frame strip (0..1). One bright
 * frame, its neighbours half way, the rest at rest: light catching a facet as the stone turns,
 * never a flash. `phase` staggers stones on one item.
 */
export function twinkle(f, n, phase = 0) {
  const k = ((f - phase) % n + n) % n;
  return k === 0 ? 1 : k === 1 || k === n - 1 ? 0.5 : 0;
}

/** Emissive alphas for a stone's base and sparkle texels on frame f. */
export function stoneLight(f, n, phase = 0) {
  const t = twinkle(f, n, phase);
  return { base: Math.round(160 + 60 * t), spark: Math.round(225 + 30 * t) };
}

/* ================================================================================ shading */

/**
 * Flat block shading from a five-step ramp (dark → light): a lit top, sides at the base with a
 * lit top row and a shaded bottom row, a dark underside, a highlight texel at the upper left of
 * the front and back faces. Single texels and flat fills only.
 */
export const block = (r, { hi = true, rows = true } = {}) => (face, i, j, w, h) => {
  if (face === 'up') return r[3];
  if (face === 'down') return r[0];
  if (hi && (face === 'south' || face === 'north') && i === 0 && j === 0 && w > 1) return r[4];
  if (rows && j === 0 && h > 2) return r[3];
  if (rows && j === h - 1 && h > 2) return r[1];
  return face === 'east' || face === 'west' ? r[1] : r[2];
};

/** Metal: like block, but the lit row is the hot end, as a polished edge catches the light. */
export const metal = (r) => (face, i, j, w, h) => {
  if (face === 'up') return r[4];
  if (face === 'down') return r[1];
  if (j === 0) return r[3];
  if (j === h - 1 && h > 1) return r[1];
  if ((face === 'north' || face === 'south') && i === 0 && w > 2) return r[3];
  return face === 'east' || face === 'west' ? r[1] : r[2];
};

/* ================================================================================ builder */

const flipU = (r) => [r[2], r[1], r[0], r[3]];

export function mirrorCube(c) {
  const f = c.faces;
  const faces = {};
  if (f.north) faces.north = flipU(f.north);
  if (f.south) faces.south = flipU(f.south);
  if (f.east) faces.west = flipU(f.east);
  if (f.west) faces.east = flipU(f.west);
  if (f.up) faces.up = flipU(f.up);
  if (f.down) faces.down = flipU(f.down);
  return { ...c, from: [-c.to[0], c.from[1], c.from[2]], to: [-c.from[0], c.to[1], c.to[2]], faces };
}

/** Mirrors right-side bones (`*_r*`) into left ones across x = 0. */
export function mirror(bones) {
  const rename = (n) => n.replace('_r', '_l');
  return bones.map((b) => ({
    ...b,
    name: rename(b.name),
    parent: b.parent ? rename(b.parent) : null,
    pivot: [-b.pivot[0], b.pivot[1], b.pivot[2]],
    rotation: b.rotation ? [b.rotation[0], -b.rotation[1], -b.rotation[2]] : [0, 0, 0],
    cubes: (b.cubes ?? []).map(mirrorCube),
  }));
}

/**
 * A model under construction: an atlas at `d` texels per model pixel (1 = the skin's density)
 * and the albedo and emissive canvases, `frames` tall (an animated strip, every layer the same).
 *
 * `box(from, to, look, material, glow, faces)` allocates a cuboid and paints each face, every
 * frame: `look(face, i, j, w, h, f)` → colour | [colour, alpha] | null, `glow(face, i, j, w, h,
 * f)` → [colour, alpha] | null on the emissive layer.
 */
export function builder(w, h, { frames = 1, d = 1 } = {}) {
  const A = new Atlas(w, h, d);
  const alb = new Canvas(w, h * frames);
  const emi = new Canvas(w, h * frames);
  let lit = false;
  const api = {
    alb, emi, w, h, frames,
    hasGlow: () => lit,
    box(from, to, look, material, glow, faces = 'nsewud') {
      const { cube, rects } = A.cube(from, to, faces);
      for (const [face, r] of Object.entries(rects)) {
        const fw = r[2] - r[0], fh = r[3] - r[1];
        for (let f = 0; f < frames; f++) {
          for (let j = 0; j < fh; j++) for (let i = 0; i < fw; i++) {
            const c = look(face, i, j, fw, fh, f);
            const y = f * h + r[1] + j;
            if (c != null) {
              if (Array.isArray(c) && typeof c[0] === 'string') alb.set(r[0] + i, y, c[0], c[1]);
              else alb.set(r[0] + i, y, c);
            }
            const g = glow ? glow(face, i, j, fw, fh, f) : null;
            if (g) {
              emi.set(r[0] + i, y, g[0], g[1]);
              lit = true;
            }
          }
        }
      }
      if (material) cube.material = material;
      return cube;
    },
    /**
     * A cut stone of jewel ramp `jr` as a glass cube: deep underneath and to the lower right,
     * base in the middle, light at the upper left with one sparkle texel; lit from within on the
     * emissive layer, twinkling over the strip.
     */
    gem(from, to, jr, phase = 0) {
      // Saturated: the base hue carries the stone, one sparkle texel is the only near-white.
      return api.box(from, to, (face, i, j, fw, fh) => {
        if (face === 'down') return jr[0];
        if (fw * fh === 1) return face === 'up' ? jr[3] : jr[2];
        if (face === 'up') return i === 0 && j === 0 ? jr[4] : jr[3];
        if (i === 0 && j === 0) return jr[4];
        if (i === fw - 1 && j === fh - 1) return jr[1];
        return jr[2];
      }, 'glass', (face, i, j, fw, fh, f) => {
        if (face === 'down') return null;
        const L = stoneLight(f, frames, phase);
        if (fw * fh === 1) return [jr[2], L.spark];
        if (i === 0 && j === 0) return [jr[4], L.spark];
        if (i === fw - 1 && j === fh - 1) return null;
        return [jr[2], L.base];
      });
    },
  };
  return api;
}

/* ================================================================================ capes */

export const CW = 10, CH = 16;

/**
 * A cape at the vanilla 64 x 32 layout, `frames` tall. Painters take (x, y, f) with x to the
 * right as seen from behind and y down the face, and return a colour, [colour, alpha] or null
 * (transparent: a cut). `outer`, `lining` (x as seen from behind too: it is flipped onto the
 * sheet), `edge(y, f)` for the one-texel sides, `top(x)`, `bottom(x)`; `mat(x, y)` a material per
 * outer texel; `glow(x, y, f)` → [colour, alpha] on the emissive layer; `tint(x, y)` → grey or
 * null for the tint mask.
 */
export function cape({ frames = 1, outer, lining, edge, top, bottom, mat, glow, tint, liningMat, edgeMat }) {
  const alb = new Canvas(64, 32 * frames);
  const m = new Canvas(64, 32);
  const emi = new Canvas(64, 32 * frames);
  const tin = new Canvas(64, 32 * frames);
  let lit = false, tinted = false, masked = false;
  const put = (cv, x, y, c) => {
    if (c == null) return;
    if (Array.isArray(c) && typeof c[0] === 'string') cv.set(x, y, c[0], c[1]);
    else cv.set(x, y, c);
  };
  const mk = (x, y, k) => {
    if (!k) return;
    m.set(x, y, MATKEY[k]);
    masked = true;
  };
  for (let f = 0; f < frames; f++) {
    const oy = f * 32;
    for (let y = 0; y < CH; y++) {
      for (let x = 0; x < CW; x++) {
        const o = outer(x, y, f);
        put(alb, 1 + x, oy + 1 + y, o);
        const l = lining(x, y, f);
        // The lining is seen from the front: its u runs the other way.
        put(alb, 12 + (CW - 1 - x), oy + 1 + y, l);
        if (f === 0 && o != null) mk(1 + x, 1 + y, mat ? mat(x, y) : null);
        if (f === 0 && l != null && liningMat) mk(12 + (CW - 1 - x), 1 + y, liningMat);
        const g = glow ? glow(x, y, f) : null;
        if (g) {
          emi.set(1 + x, oy + 1 + y, g[0], g[1]);
          lit = true;
        }
        const t = tint ? tint(x, y) : null;
        if (t != null) {
          tin.set(1 + x, oy + 1 + y, t);
          tinted = true;
        }
      }
      const e = edge(y, f);
      for (const x of [0, 11, 22]) {
        put(alb, x, oy + 1 + y, e);
        if (f === 0 && e != null && edgeMat) mk(x, 1 + y, edgeMat);
      }
    }
    for (let x = 0; x < CW; x++) {
      put(alb, 1 + x, oy, top(x));
      put(alb, 11 + x, oy, bottom(x));
      if (f === 0 && edgeMat) {
        if (top(x) != null) mk(1 + x, 0, edgeMat);
        if (bottom(x) != null) mk(11 + x, 0, edgeMat);
      }
    }
  }
  return { alb, mat: masked ? m : null, emi: lit ? emi : null, tint: tinted ? tin : null };
}

const MATKEY = { matte: [0, 0, 0], cloth: [0, 0, 255], metal: [255, 255, 0], glass: [0, 255, 255], emissive: [255, 255, 255] };

/** Saves a painted cape's layers. */
export function saveCape(save, id, c, meta) {
  save(id, 'texture.png', c.alb);
  if (c.mat) save(id, 'material.png', c.mat);
  if (c.emi) save(id, 'emissive.png', c.emi);
  if (c.tint) save(id, 'tint.png', c.tint);
  save(id, 'cape.json', meta);
}

/** Saves a built model's layers and source JSON. */
export function saveModel(save, id, b, model) {
  save(id, 'texture.png', b.alb);
  if (b.hasGlow()) save(id, 'emissive.png', b.emi);
  const out = { format: 'void-cosmetic/1', texture_size: [b.w, b.h], ...model };
  if (b.frames > 1 && !out.frames) out.frames = { count: b.frames, fps: 6 };
  save(id, 'model.json', out);
}
