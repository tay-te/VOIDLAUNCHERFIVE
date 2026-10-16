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
    if (face === 'up') return { c: i % 4 === 0 ? '#6e5f72' : '#5a4c60' };
    if (face === 'down') return { c: '#16111d' };
    return { c: j === 0 ? '#5a4c60' : j === h - 1 ? '#16111d' : '#382d40' };
  };


  // Ramps (docs/cosmetics.md, the reference board): soot feathers that are plum in the shadow
  // and warm ash in the light, and an ember ramp for the burning tips — deep coal to a hot core.
  const SOOT = ['#120d18', '#1d1626', '#2a2134', '#3a2f44', '#54465a', '#76656f'];
  const EMBER = ['#3a1814', '#7a2e1e', '#c4552f', '#ff8a5c', '#ffb894', '#ffe9dc'];

  const coverts = (u, v, w, h, front) => {
    const row = v < h * 0.5 ? 0 : 1;
    const k = (u + (row ? 2 : 0)) % 4;
    const rowEnd = row === 0 ? Math.floor(h * 0.6) : h - 1;
    const tip = (k === 0 || k === 3) ? 1 : 0;
    if (v > rowEnd - tip) return null;
    // The coverts sit over the flight feathers and are a step lighter, so the layering reads.
    let c = row ? SOOT[3] : SOOT[4];
    if (k === 3) c = row ? SOOT[2] : SOOT[3];
    if (k === 0 && v < rowEnd - 1) c = row ? SOOT[4] : SOOT[5];
    if (v === rowEnd - tip) c = SOOT[1];
    if (v === 0) c = SOOT[5];
    return { c: shade(c, dark(front)) };
  };

  // Secondaries: hanging from the arm, longest at the wrist, the trailing edge warming to coal.
  const secondaries = (u, v, w, h, front) => {
    const fi = Math.floor(u / 4), k = u % 4;
    const len = Math.round(h * (0.55 + 0.4 * (u / w) + (fi % 2) * 0.05));
    const end = len - ((k === 0 || k === 3) ? 2 : 0);
    if (v > end) return null;
    const t = v / len;
    let c = t < 0.5 ? SOOT[2] : SOOT[1];
    if (k === 1) c = t < 0.5 ? SOOT[3] : SOOT[2];   // the shaft
    if (k === 3) c = SOOT[0];
    const tip = clamp01((v - (len - 5)) / 5) * (u / w);
    if (tip > 0.25) c = tip > 0.6 ? EMBER[1] : mix(c, EMBER[0], 0.7);
    return { c: shade(c, dark(front)) };
  };

  // Primary coverts on the hand.
  const pcoverts = coverts;

  // Primaries: long feathers fanning from the wrist; each burns from the tip back — coal, ember,
  // a hot core at the very end — flickering on the emissive strip.
  const primaries = (u, v, w, h, front, f) => {
    const slant = u - v * 0.32;
    if (slant < 0) return null;
    const fi = Math.floor(slant / 4.4), k = Math.floor(slant) % 4;
    const nf = Math.ceil(w / 4.4);
    const len = Math.round(h * (1 - Math.pow(fi / nf, 1.15) * 0.78) - (fi === 0 ? 2 : 0));
    const end = len - ((k === 0 || k === 3) ? 2 : 0);
    if (v > end) return null;
    const t = v / len;
    let c = t < 0.4 ? SOOT[2] : SOOT[1];
    if (k === 1) c = t < 0.4 ? SOOT[4] : SOOT[3];
    if (k === 3) c = SOOT[0];
    const burn = clamp01((v - (len - 10)) / 10);
    if (burn > 0) {
      const i = burn > 0.88 ? 5 : burn > 0.72 ? 4 : burn > 0.5 ? 3 : burn > 0.3 ? 2 : burn > 0.12 ? 1 : 0;
      c = k === 3 && i > 1 ? EMBER[i - 1] : EMBER[i];
    }
    let e = null;
    if (burn > 0.3) {
      const phase = hash(fi, 3, 41);
      const flick = 0.62 + 0.38 * Math.sin((f / F + phase) * Math.PI * 2) + (hash(fi, v, 500 + f) < 0.06 ? 0.4 : 0);
      e = [burn > 0.85 ? EMBER[5] : burn > 0.6 ? EMBER[4] : EMBER[3], Math.round(Math.min(255, smooth((burn - 0.3) / 0.7) * 235 * flick))];
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
  // Feathers have the soft grazing sheen of cloth.
  for (const c of [cov, sec, pcov, pri]) c.material = 'cloth';
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
  // Black glass: the membranes are see-through, and mirror-bright at a glancing angle.
  armMem.material = 'glass';
  handMem.material = 'glass';
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
