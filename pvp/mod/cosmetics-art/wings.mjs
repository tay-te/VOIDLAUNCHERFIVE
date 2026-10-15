/**
 * The two pairs of wings, built as layered geometry at twice the skin's texel density.
 *
 * Ember wings are a bird's wing: on the arm (hinged at the shoulder blade) a solid leading-edge
 * bone, a row of coverts on top and the secondaries beneath; on the hand (hinged at the wrist) the
 * same again with primary coverts over long primaries. Every feather layer is its own bone, so the
 * rig staggers the flap through them for follow-through, and the layers sit a fraction apart in
 * depth so the wing has thickness from every angle. The primaries' tips are ember: lit, and on an
 * animated emissive strip they flicker like coals.
 *
 * Void wings are a bat's hand: a leading-edge arm, three finger struts fanning from the wrist as
 * solid bones with a glowing core, and black glass membrane stretched between them with veins of
 * light and a lit trailing rim.
 *
 * Right wing = model -x; the left is mirrored. Units: model pixels, y down, origin at the neck,
 * the back surface at z = +2.
 */

import { Atlas, Canvas, P, mix, shade, hash, clamp01, smooth } from './lib.mjs';

const D = 2;                    // texels per pixel
const ROOT = [-1.2, 1.0, 3.0];  // the shoulder blade
const ARM = 11;                 // arm length, root to wrist
const HAND = 15;                // wrist to tip
const WRIST = [ROOT[0] - ARM, 1.0, 3.0];

const flipU = (r) => [r[2], r[1], r[0], r[3]];

function mirrorCube(c) {
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

function mirror(bones) {
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
 * A layer: a flat plane from x0 (outer) to x1 (inner) at depth z, painted by
 * `look(u, v, w, h, front, f)` in texels with u running outward from the plane's inner end.
 */
function plane(A, canvases, x0, x1, y0, y1, z, look, frames, H) {
  const { cube, rects } = A.cube([x0, y0, z], [x1, y1, z], ['n', 's']);
  for (const [face, r] of Object.entries(rects)) {
    const w = r[2] - r[0], h = r[3] - r[1];
    for (let f = 0; f < frames; f++) {
      for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) {
        // The north face's texture u runs along +x, i.e. inward for a right wing.
        const u = face === 'north' ? w - 1 - i : i;
        const res = look(u, j, w, h, face === 'north', f);
        if (!res) continue;
        canvases.alb.set(r[0] + i, f * H + r[1] + j, res.c, res.a ?? 255);
        if (res.e) canvases.emi.set(r[0] + i, f * H + r[1] + j, res.e[0], res.e[1]);
      }
    }
  }
  return cube;
}

/** A solid bone (a 3D cuboid), painted by `look(face, i, j, w, h, f)`. */
function solid(A, canvases, from, to, look, frames, H) {
  const { cube, rects } = A.cube(from, to);
  for (const [face, r] of Object.entries(rects)) {
    const w = r[2] - r[0], h = r[3] - r[1];
    for (let f = 0; f < frames; f++) {
      for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) {
        const res = look(face, i, j, w, h, f);
        if (!res) continue;
        canvases.alb.set(r[0] + i, f * H + r[1] + j, res.c);
        if (res.e) canvases.emi.set(r[0] + i, f * H + r[1] + j, res.e[0], res.e[1]);
      }
    }
  }
  return cube;
}

/* ===================================================================================== ember */

function emberWings(save) {
  const F = 8, W = 128, H = 128;
  const alb = new Canvas(W, H * F);
  const emi = new Canvas(W, H * F);
  const cv = { alb, emi };
  const A = new Atlas(W, H, D);
  const dark = (front) => (front ? -0.08 : 0);

  const boneLook = (face, i, j, w, h) => {
    if (face === 'up') return { c: shade('#5a4f6e', i % 4 === 0 ? 0.08 : 0) };
    if (face === 'down') return { c: P.void2 };
    return { c: j === 0 ? '#54496b' : j === h - 1 ? P.void2 : '#3b3349' };
  };

  // Coverts: two staggered rows of short rounded feathers.
  const covert = (len) => (u, v, w, h, front) => {
    const row = v < h * 0.55 ? 0 : 1;
    const off = row ? 2 : 0;
    const k = (u + off) % 4;
    const end = row === 0 ? h * 0.62 : h - 1;
    const tip = (k === 0 || k === 3) ? 1 : 0;
    if (v > end - tip) return null;
    const edge = k === 3 ? -0.14 : k === 0 ? 0.07 : 0;
    const c = mix(P.void3, '#3e3550', 0.55 + row * 0.15);
    return { c: shade(c, edge + dark(front) + (v === 0 ? 0.08 : 0)) };
  };
  void covert;

  const coverts = (u, v, w, h, front) => {
    const row = v < h * 0.5 ? 0 : 1;
    const k = (u + (row ? 2 : 0)) % 4;
    const rowEnd = row === 0 ? Math.floor(h * 0.6) : h - 1;
    if (v > rowEnd - ((k === 0 || k === 3) ? 1 : 0)) return null;
    // Lighter than the flight feathers beneath, so the layering reads from behind.
    const c = mix('#3a3149', '#5a4e70', row ? 0.35 : 0.7);
    const rim = v === rowEnd - ((k === 0 || k === 3) ? 1 : 0) ? -0.18 : 0;
    return { c: shade(c, (k === 3 ? -0.15 : k === 0 ? 0.08 : 0) + rim + dark(front)) };
  };

  // Secondaries: two-pixel feathers hanging from the arm, a pale shaft, warm only at the edge.
  const secondaries = (u, v, w, h, front) => {
    const fi = Math.floor(u / 4), k = u % 4;
    // Shorter at the body, longest at the wrist: the trailing edge sweeps down and out.
    const len = Math.round(h * (0.55 + 0.4 * (u / w) + (fi % 2) * 0.05));
    const end = len - ((k === 0 || k === 3) ? 2 : 0);
    if (v > end) return null;
    const t = v / len;
    let c = mix('#231e2f', '#2f2840', t);
    if (k === 1) c = shade(c, 0.1);
    if (k === 3) c = shade(c, -0.12);
    const tip = clamp01((v - (len - 6)) / 6);
    c = mix(c, P.emb1, smooth(tip) * 0.45 * (u / w));
    return { c: shade(c, dark(front)) };
  };

  // Primary coverts on the hand.
  const pcoverts = coverts;

  // Primaries: long feathers fanning from the wrist, the longest just past it; ember tips that
  // flicker on the emissive strip.
  const primaries = (u, v, w, h, front, f) => {
    const slant = u - v * 0.32;           // feathers lean outward toward their tips
    if (slant < 0) return null;
    const fi = Math.floor(slant / 4.4), k = Math.floor(slant) % 4;
    const nf = Math.ceil(w / 4.4);
    const len = Math.round(h * (1 - Math.pow(fi / nf, 1.15) * 0.78) - (fi === 0 ? 2 : 0));
    const end = len - ((k === 0 || k === 3) ? 2 : 0);
    if (v > end) return null;
    const t = v / len;
    let c = mix('#211c2c', '#2e2740', t);
    if (k === 1) c = shade(c, 0.1);
    if (k === 3) c = shade(c, -0.14);
    const tipT = clamp01((v - (len - 9)) / 9);
    c = mix(c, tipT > 0.7 ? P.emb4 : P.emb2, smooth(tipT) * 0.92);
    let e = null;
    if (tipT > 0.35) {
      const phase = hash(fi, 3, 41);
      const flick = 0.62 + 0.38 * Math.sin((f / F + phase) * Math.PI * 2) + (hash(fi, v, 500 + f) < 0.06 ? 0.4 : 0);
      e = [tipT > 0.85 ? P.emb6 : tipT > 0.6 ? P.emb5 : P.emb3, Math.round(Math.min(255, smooth((tipT - 0.35) / 0.65) * 230 * flick))];
    }
    return { c: shade(c, dark(front)), e };
  };

  const Z = { bone: [2.6, 3.7], cov: 3.55, feat: 3.05 };
  const arm = solid(A, cv, [WRIST[0], 0.3, Z.bone[0]], [ROOT[0], 1.7, Z.bone[1]], boneLook, F, H);
  const hand = solid(A, cv, [WRIST[0] - HAND, 0.4, Z.bone[0] + 0.1], [WRIST[0], 1.6, Z.bone[1] - 0.1], boneLook, F, H);
  const cov = plane(A, cv, WRIST[0], ROOT[0], 1.2, 7.2, Z.cov, coverts, F, H);
  const sec = plane(A, cv, WRIST[0], ROOT[0], 1.0, 14, Z.feat, secondaries, F, H);
  const pcov = plane(A, cv, WRIST[0] - HAND, WRIST[0], 1.2, 6.2, Z.cov, pcoverts, F, H);
  const pri = plane(A, cv, WRIST[0] - HAND, WRIST[0], 1.0, 21, Z.feat, primaries, F, H);
  const right = [
    { name: 'wing_r', parent: null, pivot: ROOT, rotation: [0, 0, 8], cubes: [arm] },
    { name: 'wing_r_cov', parent: 'wing_r', pivot: [ROOT[0], 1.2, Z.cov], cubes: [cov] },
    { name: 'wing_r_sec', parent: 'wing_r', pivot: [ROOT[0], 1.0, Z.feat], cubes: [sec] },
    // The hand rises from the wrist: the leading edge climbs to a tip above the shoulders.
    { name: 'wing_r_tip', parent: 'wing_r', pivot: WRIST, rotation: [0, 0, 26], cubes: [hand] },
    { name: 'wing_r_pcov', parent: 'wing_r_tip', pivot: [WRIST[0], 1.2, Z.cov], cubes: [pcov] },
    { name: 'wing_r_pri', parent: 'wing_r_tip', pivot: [WRIST[0], 1.0, Z.feat], cubes: [pri] },
  ];
  save('ember-wings', 'texture.png', alb);
  save('ember-wings', 'emissive.png', emi);
  save('ember-wings', 'model.json', {
    format: 'void-cosmetic/1', attach: 'body', texture_size: [W, H], frames: { count: F, fps: 7 }, procedural: 'wings',
    bones: [...right, ...mirror(right)],
    glow: [
      { bone: 'wing_r_pri', at: [WRIST[0] - 5, 17, Z.feat], color: P.emb4, size: 4.5, strength: 0.22 },
      { bone: 'wing_r_pri', at: [WRIST[0] - 11, 11, Z.feat], color: P.emb4, size: 3.5, strength: 0.18 },
      { bone: 'wing_l_pri', at: [-(WRIST[0] - 5), 17, Z.feat], color: P.emb4, size: 4.5, strength: 0.22 },
      { bone: 'wing_l_pri', at: [-(WRIST[0] - 11), 11, Z.feat], color: P.emb4, size: 3.5, strength: 0.18 },
    ],
  });
}

/* ====================================================================================== void */

function voidWings(save) {
  const W = 128, H = 128, F = 1;
  const alb = new Canvas(W, H);
  const emi = new Canvas(W, H);
  const cv = { alb, emi };
  const A = new Atlas(W, H, D);
  // Finger tips relative to the wrist, in pixels (x outward, y down).
  const TIPS = [[HAND, -2], [HAND - 1, 10], [HAND - 6.5, 17.5]];
  const BODY = 12;   // where the arm membrane meets the body, below the root

  // Membrane silhouette in whole-wing pixel coordinates: U outward from the root (0..ARM+HAND),
  // V down from y = 0. The trailing edge sags between finger tips.
  const pts = [[0, 1], [ARM, 1], [ARM + TIPS[0][0], 1 + TIPS[0][1]]];
  const sag = (a, b, k) => {
    const mx = (a[0] + b[0]) / 2, my = (a[1] + b[1]) / 2;
    const dx = ARM - mx, dy = 1 - my, l = Math.hypot(dx, dy);
    return [mx + dx / l * k, my + dy / l * k];
  };
  const tipsW = TIPS.map(([x, y]) => [ARM + x, 1 + y]);
  const outline = [pts[0], pts[1], tipsW[0], sag(tipsW[0], tipsW[1], 2.2), tipsW[1], sag(tipsW[1], tipsW[2], 2.4),
    tipsW[2], sag(tipsW[2], [0, BODY], 3), [0, BODY]];
  const inside = (x, y) => {
    let c = false;
    for (let i = 0, j = outline.length - 1; i < outline.length; j = i++) {
      const [xi, yi] = outline[i], [xj, yj] = outline[j];
      if ((yi > y) !== (yj > y) && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) c = !c;
    }
    return c;
  };
  const segDist = (x, y, a, b) => {
    const dx = b[0] - a[0], dy = b[1] - a[1];
    const t = Math.max(0, Math.min(1, ((x - a[0]) * dx + (y - a[1]) * dy) / (dx * dx + dy * dy)));
    return Math.hypot(x - a[0] - dx * t, y - a[1] - dy * t);
  };
  const wrist = [ARM, 1];
  const membrane = (U0, Y0) => (u, v, w, h, front) => {
    const x = U0 + (u + 0.5) / D, y = Y0 + (v + 0.5) / D;
    if (!inside(x, y)) return null;
    // Veins: from the wrist toward the midpoints between fingers, and from the root down.
    let vein = Infinity;
    const mids = [sag(tipsW[0], tipsW[1], 2.2), sag(tipsW[1], tipsW[2], 2.4), sag(tipsW[2], [0, BODY], 3)];
    for (const m of mids) vein = Math.min(vein, segDist(x, y, wrist, m));
    vein = Math.min(vein, segDist(x, y, [2, 2], [ARM * 0.6, BODY - 1]));
    let rim = false;
    for (const [dx, dy] of [[0.5, 0], [-0.5, 0], [0, 0.5], [0, -0.5]]) if (!inside(x + dx, y + dy)) rim = true;
    const glass = mix(P.void1, P.vio0, 0.45 + 0.45 * (1 - y / 18));
    const sheen = Math.max(0, 1 - (y - 1) / 3) * 0.1;
    if (vein < 0.32) return { c: mix(P.vio2, P.vio3, 0.6), e: [P.vio4, 140] };
    if (rim) return { c: shade(glass, 0.2), e: [P.vio3, 110] };
    const n = hash(u, v, 3) < 0.06 ? 0.04 : 0;
    return { c: shade(glass, sheen + n + (front ? -0.05 : 0)) };
  };
  const strutLook = (face, i, j, w, h) => {
    // A dark bone with a violet core of light along its length on every side.
    const core = (face === 'north' || face === 'south') && j === 0;
    if (core) return { c: mix(P.vio2, P.vio3, 0.6), e: [P.vio4, 120] };
    return { c: shade('#2a2338', (j === 0 ? 0.06 : 0) - (face === 'down' ? 0.15 : 0)) };
  };
  const Z = 3.0;
  const armMem = plane(A, cv, WRIST[0], ROOT[0], 1, BODY + 1, Z, membrane(0, 1), F, H);
  const handMem = plane(A, cv, WRIST[0] - HAND - 0.5, WRIST[0], -1.5, 19, Z, membrane(ARM, -1.5), F, H);
  const armBone = solid(A, cv, [WRIST[0], 0.4, 2.55], [ROOT[0], 1.6, 3.45], strutLook, F, H);
  const right = [
    { name: 'wing_r', parent: null, pivot: ROOT, rotation: [0, 0, 8], cubes: [armBone, armMem] },
    { name: 'wing_r_tip', parent: 'wing_r', pivot: WRIST, rotation: [0, 0, 14], cubes: [handMem] },
  ];
  TIPS.forEach(([tx, ty], k) => {
    const L = Math.hypot(tx, ty);
    const theta = Math.atan2(-ty, tx) * 180 / Math.PI;   // (-1, 0) rotated to point at the tip
    const cube = solid(A, cv, [WRIST[0] - L, WRIST[1] - 0.4, 2.7], [WRIST[0], WRIST[1] + 0.4, 3.3], strutLook, F, H);
    right.push({ name: `wing_r_f${k + 1}`, parent: 'wing_r_tip', pivot: WRIST, rotation: [0, 0, theta], cubes: [cube] });
  });
  save('void-wings', 'texture.png', alb);
  save('void-wings', 'emissive.png', emi);
  save('void-wings', 'model.json', {
    format: 'void-cosmetic/1', attach: 'body', texture_size: [W, H], procedural: 'wings',
    bones: [...right, ...mirror(right)],
    glow: [
      { bone: 'wing_r_tip', at: [WRIST[0] - 6, 6, Z], color: P.vio4, size: 6, strength: 0.16 },
      { bone: 'wing_l_tip', at: [-(WRIST[0] - 6), 6, Z], color: P.vio4, size: 6, strength: 0.16 },
      { bone: 'wing_r', at: [WRIST[0], 1, Z], color: P.vio5, size: 2.6, strength: 0.4 },
      { bone: 'wing_l', at: [-WRIST[0], 1, Z], color: P.vio5, size: 2.6, strength: 0.4 },
    ],
  });
}

export function paintWings(save) {
  emberWings(save);
  voidWings(save);
}
