/**
 * The 3D items: back (wings, orbit), head (crown, beanie, top hat), halo, face. Each is a
 * cuboid model in model-space pixels (y down) relative to its attachment point — the body
 * pivot (top of the torso, back surface at z = +2) or the head pivot (the neck; the head spans
 * x,z in [-4, 4] and y in [-8, 0], the face at z = -4) — with per-face UV allocated one texel
 * per pixel, so every item shares the skin's texel density.
 */

import { Atlas, Canvas, P, mix, shade, hash, paintFaces, clamp01, smooth } from './lib.mjs';

/* ------------------------------------------------------------------------------ helpers */

const flipU = (r) => [r[2], r[1], r[0], r[3]];

/** Mirrors a cube across x = 0, keeping its texture readable (u flipped, e/w swapped). */
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

function mirrorBone(b, rename) {
  return {
    ...b,
    name: rename(b.name),
    parent: b.parent ? rename(b.parent) : null,
    pivot: [-b.pivot[0], b.pivot[1], b.pivot[2]],
    rotation: b.rotation ? [b.rotation[0], -b.rotation[1], -b.rotation[2]] : undefined,
    cubes: (b.cubes ?? []).map(mirrorCube),
  };
}

/**
 * A lit cell: lighter top, base sides, darker underside, one bright texel on the top-left of
 * each face — the same little bevel the launcher's pixel cards give their cells.
 */
function cellPaint(base, emissive, emiAlpha = 255) {
  return {
    alb: (face, i, j) => {
      const k = face === 'up' ? 0.22 : face === 'down' ? -0.35 : face === 'north' || face === 'south' ? 0.04 : -0.08;
      return [shade(base, k + (i === 0 && j === 0 ? 0.12 : 0))];
    },
    emi: emissive ? (face) => [emissive, face === 'down' ? Math.round(emiAlpha * 0.6) : emiAlpha] : null,
  };
}

function cell(atlas, alb, emi, from, to, look) {
  const { cube, rects } = atlas.cube(from, to);
  paintFaces(alb, rects, look.alb);
  if (look.emi && emi) paintFaces(emi, rects, look.emi);
  return cube;
}

/* ================================================================================ crown */

function crown(save) {
  // Aged dark gold: a band two pixels tall, lit along its top edge, with a row of short cells
  // standing on it (taller at the corners and the front) and one warm stone set in the front.
  const A = new Atlas(64, 32);
  const alb = new Canvas(64, 32);
  const emi = new Canvas(64, 32);
  const goldD = '#5a4526', goldM = '#8a6a3c', goldL = P.gold3, goldH = P.gold4;
  const metal = (face, i, j, w, h) => {
    if (face === 'up') return [goldL];
    if (face === 'down') return [shade(goldD, -0.3)];
    if (j === 0) return [i % 3 === 1 ? goldH : goldL];
    if (j === h - 1) return [shade(goldD, -0.15)];
    return [(i + j) % 4 === 0 ? goldM : shade(goldM, -0.12)];
  };
  const cubes = [];
  const add = (from, to, f = metal) => {
    const { cube, rects } = A.cube(from, to);
    paintFaces(alb, rects, f);
    cubes.push(cube);
  };
  const Y0 = -9.7, Y1 = -8.0, R = 4.65, T = 0.55;
  add([-R, Y0, -R], [R, Y1, -R + T]);
  add([-R, Y0, R - T], [R, Y1, R]);
  add([R - T, Y0, -R + T], [R, Y1, R - T]);
  add([-R, Y0, -R + T], [-R + T, Y1, R - T]);
  // Standing cells: 1.1 wide, on a regular step round the band.
  const cell = (x, z, h) => add([x - 0.55, Y0 - h, z - 0.55], [x + 0.55, Y0, z + 0.55], (face, i, j, w, hh) => {
    if (face === 'up') return [goldH];
    if (face === 'down') return [goldD];
    return [j === 0 ? goldL : j === hh - 1 ? goldD : goldM];
  });
  const edge = R - T / 2;
  for (const [x, z, h] of [
    [-edge, -edge, 1.6], [edge, -edge, 1.6], [-edge, edge, 1.6], [edge, edge, 1.6],
    [-2.3, -edge, 1.0], [2.3, -edge, 1.0], [0, -edge, 2.1],
    [-2.3, edge, 1.0], [2.3, edge, 1.0], [0, edge, 1.0],
    [-edge, 0, 1.0], [edge, 0, 1.0],
  ]) cell(x, z, h);
  // The stone: a warm faceted cell on the front of the band, below the tall front cell.
  {
    const { cube, rects } = A.cube([-0.75, -9.45, -R - 0.4], [0.75, -8.15, -R + 0.05]);
    paintFaces(alb, rects, (face, i, j) => [face === 'north' ? (i === 0 && j === 0 ? P.emb6 : j === 0 ? P.emb5 : P.emb3) : P.emb2]);
    paintFaces(emi, rects, (face, i, j) => [face === 'north' && i === 0 && j === 0 ? P.emb6 : P.emb4, face === 'north' ? 220 : 120]);
    cubes.push(cube);
  }
  save('monarch-crown', 'texture.png', alb);
  save('monarch-crown', 'emissive.png', emi);
  save('monarch-crown', 'model.json', {
    format: 'void-cosmetic/1', attach: 'head', texture_size: [64, 32], helmet_fit: true,
    bones: [{ name: 'crown', pivot: [0, -8, 0], rotation: [-4, 0, 3], cubes }],
    glow: [{ bone: 'crown', at: [0, -8.8, -5], color: P.emb4, size: 2.4, strength: 0.5 }],
  });
}

/* =============================================================================== beanie */

function beanie(save) {
  const A = new Atlas(64, 32);
  const alb = new Canvas(64, 32);
  const tint = new Canvas(64, 32);
  const knitGrey = (face, i, j, w, h, kind) => {
    let v = 190;
    if (kind === 'cuff') v = i % 2 === 0 ? 205 : 160; // ribbing
    else if (face === 'up') v = 196 + ((i + j) % 2 ? -10 : 6);
    else v = (j % 2 === 0 ? ((i % 2) ? 200 : 178) : ((i % 2) ? 178 : 200)); // a V-stitch
    if (face === 'down') v = 120;
    if (kind === 'dome' && face !== 'up' && j === 0) v += 12;
    return [[v, v, v]];
  };
  const def = [0x6e, 0x5b, 0xd6];
  const tinted = (g) => g.map((v, k) => Math.round((v / 255) * def[k]));
  const cubes = [];
  const part = (from, to, kind) => {
    const { cube, rects } = A.cube(from, to);
    paintFaces(tint, rects, (f, i, j, w, h) => knitGrey(f, i, j, w, h, kind));
    paintFaces(alb, rects, (f, i, j, w, h) => [tinted(knitGrey(f, i, j, w, h, kind)[0])]);
    cubes.push(cube);
  };
  part([-4.55, -9.25, -4.55], [4.55, -6.2, 4.55], 'dome');
  part([-4.85, -6.5, -4.85], [4.85, -4.65, 4.85], 'cuff');
  part([-1.1, -10.2, -1.1], [1.1, -9.25, 1.1], 'knot');
  save('wool-beanie', 'texture.png', alb);
  save('wool-beanie', 'tint.png', tint);
  save('wool-beanie', 'model.json', {
    format: 'void-cosmetic/1', attach: 'head', texture_size: [64, 32], helmet_fit: true,
    bones: [{ name: 'beanie', pivot: [0, -6, 0], rotation: [-2, 0, 0], cubes }],
  });
}

/* ============================================================================== top hat */

function topHat(save) {
  const A = new Atlas(64, 64);
  const alb = new Canvas(64, 64);
  const tint = new Canvas(64, 64);
  const felt = (face, i, j, w) => {
    if (face === 'up') return [shade('#1b1822', (i + j) % 3 === 0 ? 0.05 : 0)];
    if (face === 'down') return [P.void1];
    // A soft vertical sheen down one side of each face, as felt catches the light.
    const sheen = Math.exp(-(((i / w) - 0.28) ** 2) / 0.02) * 0.12;
    return [shade('#17151d', sheen + (j % 4 === 0 ? 0.015 : 0))];
  };
  const cubes = [];
  const add = (from, to, f, t) => {
    const { cube, rects } = A.cube(from, to);
    paintFaces(alb, rects, f);
    if (t) paintFaces(tint, rects, t);
    cubes.push(cube);
  };
  add([-6, -8.7, -6], [6, -8.05, 6], (face, i, j) => {
    if (face === 'down') return [P.void0];
    if (face === 'up') {
      const r = Math.hypot(i - 6, j - 6);
      return [shade('#16141c', r > 5 ? 0.06 : r < 4 ? -0.1 : 0)];
    }
    return [shade('#1d1a24', 0.04)];
  });
  add([-3.8, -15.6, -3.8], [3.8, -10.4, 3.8], felt);
  const bandGrey = (face, i, j, w, h) => {
    const v = j === 0 ? 225 : j === h - 1 ? 150 : 200 - (i % 4 === 0 ? 10 : 0);
    return [[v, v, v]];
  };
  add([-3.95, -10.4, -3.95], [3.95, -8.7, 3.95], (f, i, j, w, h) => {
    const g = bandGrey(f, i, j, w, h)[0][0] / 255;
    return [[Math.round(0x9f * g), Math.round(0x8b * g), Math.round(0xff * g)]];
  }, bandGrey);
  save('dapper-top-hat', 'texture.png', alb);
  save('dapper-top-hat', 'tint.png', tint);
  save('dapper-top-hat', 'model.json', {
    format: 'void-cosmetic/1', attach: 'head', texture_size: [64, 64], helmet_fit: true,
    bones: [{ name: 'hat', pivot: [0, -8, 0], rotation: [-5, 0, -6], cubes }],
  });
}

/* ================================================================================ halos */

function halos(save) {
  /* Seraph: sixteen warm cells in the VOID ring, floating and breathing. */
  {
    const A = new Atlas(64, 32);
    const alb = new Canvas(64, 32);
    const emi = new Canvas(64, 32);
    const look = cellPaint(P.gold4, P.emb5, 160);
    const cubes = [];
    const N = 16, R = 4.7, Y = -12.6;
    for (let k = 0; k < N; k++) {
      const a = (k / N) * Math.PI * 2;
      const x = Math.cos(a) * R, z = Math.sin(a) * R;
      const s = 0.62;
      cubes.push(cell(A, alb, emi, [x - s, Y - 0.4, z - s], [x + s, Y + 0.4, z + s], look));
    }
    save('seraph-halo', 'texture.png', alb);
    save('seraph-halo', 'emissive.png', emi);
    save('seraph-halo', 'model.json', {
      format: 'void-cosmetic/1', attach: 'halo', texture_size: [64, 32], procedural: 'halo',
      // The tilt is its own parent bone so the idle spin turns the ring about its own axis
      // rather than wobbling the tilt round the head.
      bones: [{ name: 'tilt', pivot: [0, Y, 0], rotation: [16, 0, 0] }, { name: 'ring', parent: 'tilt', pivot: [0, Y, 0], cubes }],
      glow: [0, 1, 2, 3].map((q) => ({ bone: 'ring', at: [Math.cos(q * Math.PI / 2 + 0.4) * R, Y, Math.sin(q * Math.PI / 2 + 0.4) * R], color: P.gold4, size: 4.5, strength: 0.22 })),
      animations: { idle: { length: 18, loop: true, bones: { ring: { rotation: [[0, [0, 0, 0], 'linear'], [18, [0, 360, 0], 'linear']] } } } },
    });
  }
  /* Void: a thin violet ring with two motes in orbit. */
  {
    const A = new Atlas(64, 32);
    const alb = new Canvas(64, 32);
    const emi = new Canvas(64, 32);
    const ring = [];
    const N = 28, R = 4.4, Y = -12.4;
    const look = cellPaint(P.vio4, P.vio5, 150);
    for (let k = 0; k < N; k++) {
      const a = (k / N) * Math.PI * 2;
      const x = Math.cos(a) * R, z = Math.sin(a) * R;
      ring.push(cell(A, alb, emi, [x - 0.4, Y - 0.25, z - 0.4], [x + 0.4, Y + 0.25, z + 0.4], look));
    }
    const motes = [];
    const ml = cellPaint(P.ice5, P.ice6, 255);
    for (const [a, dy] of [[0, -0.6], [Math.PI, 0.5]]) {
      const x = Math.cos(a) * 5.7, z = Math.sin(a) * 5.7;
      motes.push(cell(A, alb, emi, [x - 0.45, Y + dy - 0.45, z - 0.45], [x + 0.45, Y + dy + 0.45, z + 0.45], ml));
    }
    save('void-halo', 'texture.png', alb);
    save('void-halo', 'emissive.png', emi);
    save('void-halo', 'model.json', {
      format: 'void-cosmetic/1', attach: 'halo', texture_size: [64, 32], procedural: 'halo',
      bones: [
        { name: 'tilt', pivot: [0, Y, 0], rotation: [14, 0, 0] },
        { name: 'ring', parent: 'tilt', pivot: [0, Y, 0], cubes: ring },
        { name: 'motes', parent: 'tilt', pivot: [0, Y, 0], cubes: motes },
      ],
      glow: [
        { bone: 'motes', at: [5.7, Y - 0.6, 0], color: P.ice5, size: 2.4, strength: 0.5 },
        { bone: 'motes', at: [-5.7, Y + 0.5, 0], color: P.ice5, size: 2.4, strength: 0.5 },
        { bone: 'ring', at: [0, Y, -R], color: P.vio4, size: 4, strength: 0.14 },
        { bone: 'ring', at: [0, Y, R], color: P.vio4, size: 4, strength: 0.14 },
      ],
      animations: { idle: { length: 6, loop: true, bones: { motes: { rotation: [[0, [0, 0, 0], 'linear'], [6, [0, -360, 0], 'linear']] }, ring: { rotation: [[0, [0, 0, 0], 'linear'], [6, [0, 60, 0], 'linear']] } } } },
    });
  }
}

/* ================================================================================ masks */

function masks(save) {
  /* Ice visor: dark glass with a cold line of light and a glint that crosses it. */
  {
    const F = 12;
    const W = 64, H = 32;
    const alb = new Canvas(W, H * F);
    const emi = new Canvas(W, H * F);
    const A = new Atlas(W, H);
    const parts = [
      A.cube([-4.75, -5.25, -4.8], [4.75, -3.45, -4.25]),
      A.cube([4.25, -5.25, -4.25], [4.75, -3.45, 0.75]),
      A.cube([-4.75, -5.25, -4.25], [-4.25, -3.45, 0.75]),
    ];
    for (let f = 0; f < F; f++) {
      const glint = (f / F) * 22 - 6;
      for (const [pi, { rects }] of parts.entries()) {
        for (const [face, r] of Object.entries(rects)) {
          const w = r[2] - r[0], h = r[3] - r[1];
          for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) {
            const x = r[0] + i, y = f * H + r[1] + j;
            const front = face === 'north' || (pi > 0 && (face === 'east' || face === 'west'));
            const base = front ? (j === 0 ? P.ice1 : j === h - 1 ? '#0a1a22' : P.ice0) : '#0a1a22';
            alb.set(x, y, base);
            if (!front) continue;
            // Global x across the band, so the glint travels over the corner onto the side.
            const gx = pi === 0 ? i + 4 : pi === 1 ? 13 + i : 4 - i;
            const line = j === 1 ? 150 : 0;
            const g = Math.max(0, 1 - Math.abs(gx - glint) / 1.6);
            const a = Math.min(255, line + Math.round(g * 255));
            if (a > 0) emi.set(x, y, g > 0.3 ? P.ice6 : P.ice4, a);
            if (j === 1) alb.set(x, y, P.ice3);
          }
        }
      }
    }
    save('ice-visor', 'texture.png', alb);
    save('ice-visor', 'emissive.png', emi);
    save('ice-visor', 'model.json', {
      format: 'void-cosmetic/1', attach: 'head', texture_size: [W, H], frames: { count: F, fps: 9 }, helmet_fit: true,
      bones: [{ name: 'visor', pivot: [0, -4, 0], cubes: parts.map((p) => p.cube) }],
      glow: [{ bone: 'visor', at: [0, -4.35, -4.9], color: P.ice4, size: 3.4, strength: 0.18 }],
    });
  }
  /* Rift mask: a dark faceplate, eyes cut through, split by a violet seam that pulses. */
  {
    const F = 10;
    const W = 64, H = 32;
    const alb = new Canvas(W, H * F);
    const emi = new Canvas(W, H * F);
    const A = new Atlas(W, H);
    const plate = A.cube([-4.45, -7.4, -4.75], [4.45, -0.7, -4.2]);
    // The seam: a jagged diagonal from the brow down to the jaw, as texel coords on the 9x7 face.
    const seam = new Set(['5,0', '5,1', '4,2', '4,3', '5,4', '4,5', '3,6']);
    const eye = (i, j) => (j === 3 || j === 4) && ((i >= 1 && i <= 3) || (i >= 5 && i <= 7));
    for (let f = 0; f < F; f++) {
      const pulse = 0.55 + 0.45 * Math.cos((f / F) * Math.PI * 2);
      for (const [face, r] of Object.entries(plate.rects)) {
        const w = r[2] - r[0], h = r[3] - r[1];
        for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) {
          const x = r[0] + i, y = f * H + r[1] + j;
          if (face !== 'north') { alb.set(x, y, P.void2); continue; }
          if (eye(i, j)) { alb.set(x, y, [0, 0, 0], 0); continue; }
          const bevel = j === 0 ? 0.12 : (i === 0 || i === w - 1) ? 0.05 : j === h - 1 ? -0.2 : 0;
          const grain = hash(i, j, 41) < 0.2 ? 0.03 : 0;
          if (seam.has(`${i},${j}`)) {
            alb.set(x, y, mix(P.vio3, P.vio5, pulse));
            emi.set(x, y, P.vio5, Math.round(150 + 105 * pulse));
          } else {
            alb.set(x, y, shade(P.void3, bevel + grain));
            // A faint halo of the seam's light on the plate beside it.
            const nearSeam = [...seam].some((s) => { const [a, b] = s.split(',').map(Number); return Math.abs(a - i) + Math.abs(b - j) === 1; });
            if (nearSeam) emi.set(x, y, P.vio4, Math.round(40 * pulse));
          }
        }
      }
    }
    save('rift-mask', 'texture.png', alb);
    save('rift-mask', 'emissive.png', emi);
    save('rift-mask', 'model.json', {
      format: 'void-cosmetic/1', attach: 'head', texture_size: [W, H], frames: { count: F, fps: 6 }, helmet_fit: true,
      bones: [{ name: 'mask', pivot: [0, -4, 0], cubes: [plate.cube] }],
      glow: [{ bone: 'mask', at: [0.4, -4, -4.95], color: P.vio4, size: 3.6, strength: 0.2 }],
    });
  }
}

/* ================================================================================ wings */

/*
 * A wing is one silhouette, 19 x 17 texels (u outward from the root, v down), split at u = 8 into
 * two alpha-cut panels: the arm (inner, hinged at the shoulder blade) and the hand (outer, hinged
 * at the wrist on the leading edge), so the rig can fold the hand back. Both lie in the x-y plane
 * behind the back; the right wing extends to model -x. The leading edge rises from the shoulder
 * blade to a tip five pixels above the shoulders, and the feathers hang from it — longest just
 * past the wrist, shortening toward the tip — which is the silhouette that reads as a wing at a
 * glance, from behind and in profile.
 */
const WU = 19, WV = 17, SPLIT = 8;
const WY0 = -5;         // model y of texel row 0
const WZ = 2.6, WT = 0.5;
const lead = (u) => 6 - 6 * Math.pow(u / (WU - 1), 1.25); // top edge, in rows
const leadAt = (u) => Math.round(lead(u));

function wing(save, id, look) {
  const W = 64, H = 32;
  const alb = new Canvas(W, H);
  const emi = new Canvas(W, H);
  const A = new Atlas(W, H);
  const inner = A.cube([-1 - SPLIT, WY0, WZ], [-1, WY0 + WV, WZ + WT], ['n', 's']);
  const outer = A.cube([-1 - WU, WY0, WZ], [-1 - SPLIT, WY0 + WV, WZ + WT], ['n', 's']);
  const paintPanel = (rects, u0) => {
    for (const [face, r] of Object.entries(rects)) {
      const w = r[2] - r[0], h = r[3] - r[1];
      for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) {
        // u runs outward from the root on both faces (the north face's texture u runs along +x).
        const u = u0 + (face === 'north' ? w - 1 - i : i);
        const res = look(u, j, face === 'north');
        if (!res) continue;
        alb.set(r[0] + i, r[1] + j, res.c, res.a ?? 255);
        if (res.e) emi.set(r[0] + i, r[1] + j, res.e[0], res.e[1]);
      }
    }
  };
  paintPanel(inner.rects, 0);
  paintPanel(outer.rects, SPLIT);
  const wristY = WY0 + lead(SPLIT) + 0.5;
  const right = [
    { name: 'wing_r', parent: null, pivot: [-1, WY0 + lead(0) + 0.5, WZ + WT / 2], rotation: [0, 0, 0], cubes: [inner.cube] },
    { name: 'wing_r_tip', parent: 'wing_r', pivot: [-1 - SPLIT, wristY, WZ + WT / 2], rotation: [0, 0, 0], cubes: [outer.cube] },
  ];
  const rename = (n) => n.replace('_r', '_l');
  const left = right.map((b) => mirrorBone(b, rename));
  save(id, 'texture.png', alb);
  save(id, 'emissive.png', emi);
  return [...right, ...left];
}

/** Bottom edge of feather column u: where the feather hanging from that column ends. */
function featherEnd(u) {
  const fi = Math.floor(u / 2);
  let end;
  if (u < SPLIT) end = 12 + (fi % 2);                         // secondaries
  else end = 16 - Math.max(0, u - 10) * 1.45;                 // primaries, shortening outward
  if (u % 2 === 1) end -= 0.6;                                // a rounded tip per feather
  return Math.round(end);
}

function wings(save) {
  /* Ember: long dark feathers, warm at the tips. */
  const ember = (u, v, front) => {
    const top = leadAt(u), end = featherEnd(u);
    if (v < top || v > end) return null;
    const dark = front ? -0.08 : 0;
    if (v === top) return { c: shade(mix(P.void4, '#54496b', 0.6), dark) };
    // Coverts: the first three rows under the edge, small staggered feathers.
    if (v <= top + 3) {
      const k = (u + v) % 2;
      return { c: shade(mix(P.void3, P.void4, 0.8), (k ? -0.07 : 0.05) + dark) };
    }
    // Flight feathers: a darker shaft on each two-texel feather; tips go warm.
    const len = end - (top + 3);
    const t = (v - (top + 3)) / Math.max(1, len);
    const tipT = clamp01((v - (end - (u >= SPLIT ? 3.5 : 2))) / (u >= SPLIT ? 3.5 : 2));
    let c = mix('#2b2538', P.void3, t * 0.6);
    if (u % 2 === 0) c = shade(c, -0.13);
    const warm = u >= SPLIT ? 0.95 : 0.45;
    c = mix(c, tipT > 0.66 ? P.emb4 : P.emb2, smooth(tipT) * warm);
    const e = u >= SPLIT - 2 && tipT > 0.45
      ? [tipT > 0.85 ? P.emb5 : P.emb3, Math.round(smooth((tipT - 0.45) / 0.55) * (u >= SPLIT ? 200 : 90))]
      : null;
    return { c: shade(c, dark), e };
  };
  const emberBones = wing(save, 'ember-wings', ember);
  save('ember-wings', 'model.json', {
    format: 'void-cosmetic/1', attach: 'body', texture_size: [64, 32], procedural: 'wings',
    bones: emberBones,
    glow: [
      { bone: 'wing_r_tip', at: [-13, WY0 + 14.5, WZ + WT / 2], color: P.emb4, size: 3.6, strength: 0.2 },
      { bone: 'wing_l_tip', at: [13, WY0 + 14.5, WZ + WT / 2], color: P.emb4, size: 3.6, strength: 0.2 },
    ],
  });

  /* Void: black glass membranes stretched between finger bones that carry violet light. */
  // A bat's hand, drawn as geometry on the 19 x 17 grid: the arm runs along the leading edge
  // from the root to the wrist, three fingers fan from the wrist to the trailing edge, and the
  // membrane between them sags into scallops.
  const ROOT = [0, 6], WRIST = [8, 4];
  const TIPS = [[18, 0], [17.5, 8.5], [13, 13.5], [6.5, 15.5]];
  const BODY = [0, 11.5];
  const sag = (a, b, k) => {
    const mx = (a[0] + b[0]) / 2, my = (a[1] + b[1]) / 2;
    const dx = WRIST[0] - mx, dy = WRIST[1] - my, l = Math.hypot(dx, dy);
    return [mx + dx / l * k, my + dy / l * k];
  };
  const outline = [ROOT, WRIST, TIPS[0], sag(TIPS[0], TIPS[1], 1.6), TIPS[1], sag(TIPS[1], TIPS[2], 1.8),
    TIPS[2], sag(TIPS[2], TIPS[3], 1.9), TIPS[3], sag(TIPS[3], BODY, 1.7), BODY];
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
  const voidLook = (u, v, front) => {
    const x = u + 0.5, y = v + 0.5;
    const arm = Math.min(segDist(x, y, ROOT, WRIST), segDist(x, y, WRIST, TIPS[0]));
    let finger = Infinity;
    for (const t of TIPS.slice(1)) finger = Math.min(finger, segDist(x, y, WRIST, t));
    const inMembrane = inside(x, y);
    if (!inMembrane && arm > 0.75 && finger > 0.6) return null;
    const dark = front ? -0.05 : 0.04;
    // The arm: a dark bone with a thin lit edge on top.
    if (arm < 0.75) return { c: shade('#2c2540', dark + (y < WRIST[1] + 0.5 - (x - 8) * 0.4 ? 0.06 : 0)), e: [P.vio2, 70] };
    // Fingers: violet light in a dark sheath, dimming toward the tips.
    if (finger < 0.45) {
      const along = Math.hypot(x - WRIST[0], y - WRIST[1]) / 12;
      return { c: mix(P.vio3, P.vio4, 0.5), e: [P.vio4, Math.round(210 - along * 110)] };
    }
    if (finger < 0.9) return { c: shade(P.void3, dark) };
    // Membrane: near-black glass with a violet cast, a sheen under the arm, a lit rim.
    let rim = false;
    for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) if (!inside(x + dx, y + dy)) rim = true;
    const glass = mix(P.void1, P.vio0, 0.45 + 0.4 * (1 - y / WV));
    const sheen = Math.max(0, 1 - arm / 3.5) * 0.09;
    const n = hash(u, v, 3) < 0.08 ? 0.035 : 0;
    return { c: shade(glass, (rim ? 0.17 : 0) + sheen + n + (front ? -0.04 : 0)), e: rim ? [P.vio3, 80] : null };
  };
  const voidBones = wing(save, 'void-wings', voidLook);
  save('void-wings', 'model.json', {
    format: 'void-cosmetic/1', attach: 'body', texture_size: [64, 32], procedural: 'wings',
    bones: voidBones,
    glow: [
      { bone: 'wing_r_tip', at: [-14, WY0 + 7, WZ + WT / 2], color: P.vio4, size: 4.5, strength: 0.13 },
      { bone: 'wing_l_tip', at: [14, WY0 + 7, WZ + WT / 2], color: P.vio4, size: 4.5, strength: 0.13 },
    ],
  });
}

/* ================================================================================ orbit */

function orbit(save) {
  // A ring of sixteen ice cells standing behind the shoulders, framing the head from behind and
  // from the front, one of them brighter (the lead) and turning slowly, with three small cells
  // counter-turning at its heart. Spaced, so it reads as one object rather than a cloud.
  const A = new Atlas(64, 32);
  const alb = new Canvas(64, 32);
  const emi = new Canvas(64, 32);
  const C = [0, -1.5, 5.2];
  const ring = [];
  const heart = [];
  const dim = cellPaint(P.ice3, P.ice4, 120);
  const lit = cellPaint(P.ice5, P.ice6, 230);
  const small = cellPaint(P.ice4, P.ice5, 150);
  for (let k = 0; k < 16; k++) {
    const a = (k / 16) * Math.PI * 2;
    const x = C[0] + Math.cos(a) * 7, y = C[1] + Math.sin(a) * 7;
    const sz = k === 0 ? 0.7 : k % 3 === 0 ? 0.55 : 0.42;
    ring.push(cell(A, alb, emi, [x - sz, y - sz, C[2] - 0.3], [x + sz, y + sz, C[2] + 0.3], k === 0 ? lit : dim));
  }
  for (let k = 0; k < 3; k++) {
    const a = (k / 3) * Math.PI * 2;
    const x = C[0] + Math.cos(a) * 1.4, y = C[1] + Math.sin(a) * 1.4;
    heart.push(cell(A, alb, emi, [x - 0.35, y - 0.35, C[2] - 0.25], [x + 0.35, y + 0.35, C[2] + 0.25], small));
  }
  save('orbit-ring', 'texture.png', alb);
  save('orbit-ring', 'emissive.png', emi);
  save('orbit-ring', 'model.json', {
    format: 'void-cosmetic/1', attach: 'body', texture_size: [64, 32], procedural: 'float',
    bones: [
      { name: 'frame', pivot: C, rotation: [0, 0, 0] },
      { name: 'ring', parent: 'frame', pivot: C, cubes: ring },
      { name: 'heart', parent: 'frame', pivot: C, cubes: heart },
    ],
    glow: [
      { bone: 'ring', at: [C[0] + 7, C[1], C[2]], color: P.ice5, size: 3, strength: 0.45 },
      { bone: 'heart', at: C, color: P.ice4, size: 3.6, strength: 0.22 },
    ],
    animations: {
      idle: {
        length: 24, loop: true, bones: {
          ring: { rotation: [[0, [0, 0, 0], 'linear'], [24, [0, 0, 360], 'linear']] },
          heart: { rotation: [[0, [0, 0, 0], 'linear'], [24, [0, 0, -720], 'linear']] },
        },
      },
    },
  });
}

export function paintModels(save) {
  crown(save);
  beanie(save);
  topHat(save);
  halos(save);
  masks(save);
  wings(save);
  orbit(save);
}
