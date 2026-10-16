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
  const A = new Atlas(128, 64, 2);
  const alb = new Canvas(128, 64);
  const emi = new Canvas(128, 64);
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
    format: 'void-cosmetic/1', attach: 'head', texture_size: [128, 64], helmet_fit: true,
    bones: [{ name: 'crown', pivot: [0, -8, 0], rotation: [-4, 0, 3], cubes }],
    glow: [{ bone: 'crown', at: [0, -8.8, -5], color: P.emb4, size: 2.4, strength: 0.5 }],
  });
}

/* =============================================================================== beanie */

function beanie(save) {
  const A = new Atlas(128, 64, 2);
  const alb = new Canvas(128, 64);
  const tint = new Canvas(128, 64);
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
    format: 'void-cosmetic/1', attach: 'head', texture_size: [128, 64], helmet_fit: true,
    bones: [{ name: 'beanie', pivot: [0, -6, 0], rotation: [-2, 0, 0], cubes }],
  });
}

/* ============================================================================== top hat */

function topHat(save) {
  const A = new Atlas(128, 128, 2);
  const alb = new Canvas(128, 128);
  const tint = new Canvas(128, 128);
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
  add([-6, -8.7, -6], [6, -8.05, 6], (face, i, j, w, h) => {
    if (face === 'down') return [P.void0];
    if (face === 'up') {
      // A sheen ring round the crown's footprint, the brim darker where the crown sits.
      const r = Math.hypot(i - w / 2, j - h / 2) / (w / 2);
      return [shade('#16141c', r > 0.85 ? 0.08 : r < 0.66 ? -0.12 : 0.02)];
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
    format: 'void-cosmetic/1', attach: 'head', texture_size: [128, 128], helmet_fit: true,
    bones: [{ name: 'hat', pivot: [0, -8, 0], rotation: [-5, 0, -6], cubes }],
  });
}

/* ================================================================================ halos */

/** A chunky ring of overlapping cells: reads as a solid ring with a pixel-art edge. */
function ringOf(A, alb, emi, n, r, y, size, height, look) {
  const out = [];
  for (let k = 0; k < n; k++) {
    const a = (k / n) * Math.PI * 2;
    const x = Math.cos(a) * r, z = Math.sin(a) * r;
    out.push(cell(A, alb, emi, [x - size / 2, y - height / 2, z - size / 2], [x + size / 2, y + height / 2, z + size / 2], look));
  }
  return out;
}

function halos(save) {
  /* Seraph: a thick ring of warm gold light, an inner filament, motes drifting round it. */
  {
    const A = new Atlas(128, 64, 2);
    const alb = new Canvas(128, 64);
    const emi = new Canvas(128, 64);
    const Y = -12.8;
    const gold = cellPaint(P.gold4, P.emb5, 175);
    const inner = cellPaint(P.emb6, P.emb6, 230);
    const mote = cellPaint(P.emb5, P.emb6, 255);
    const ring = ringOf(A, alb, emi, 26, 5.1, Y, 1.55, 1.0, gold);
    const fil = ringOf(A, alb, emi, 18, 4.15, Y + 0.05, 0.7, 0.45, inner);
    const motes = [];
    for (let k = 0; k < 4; k++) {
      const a = (k / 4) * Math.PI * 2 + 0.4;
      const x = Math.cos(a) * 6.4, z = Math.sin(a) * 6.4, dy = [-0.9, 0.6, -0.3, 1.0][k];
      motes.push(cell(A, alb, emi, [x - 0.32, Y + dy - 0.32, z - 0.32], [x + 0.32, Y + dy + 0.32, z + 0.32], mote));
    }
    save('seraph-halo', 'texture.png', alb);
    save('seraph-halo', 'emissive.png', emi);
    save('seraph-halo', 'model.json', {
      format: 'void-cosmetic/1', attach: 'halo', texture_size: [128, 64], procedural: 'halo',
      bones: [
        { name: 'tilt', pivot: [0, Y, 0], rotation: [16, 0, 0] },
        { name: 'ring', parent: 'tilt', pivot: [0, Y, 0], cubes: [...ring, ...fil] },
        { name: 'motes', parent: 'tilt', pivot: [0, Y, 0], cubes: motes },
      ],
      glow: [
        ...[0, 1, 2, 3, 4, 5].map((q) => ({ bone: 'ring', at: [Math.cos(q * Math.PI / 3) * 5.1, Y, Math.sin(q * Math.PI / 3) * 5.1], color: P.gold4, size: 4.2, strength: 0.28 })),
        { bone: 'motes', at: [Math.cos(0.4) * 6.4, Y - 0.9, Math.sin(0.4) * 6.4], color: P.emb5, size: 2, strength: 0.5 },
      ],
      animations: {
        idle: {
          length: 24, loop: true, bones: {
            ring: { rotation: [[0, [0, 0, 0], 'linear'], [24, [0, 360, 0], 'linear']] },
            motes: {
              rotation: [[0, [0, 0, 0], 'linear'], [24, [0, -720, 0], 'linear']],
              position: [[0, [0, 0, 0], 'sine'], [6, [0, -0.8, 0], 'sine'], [12, [0, 0.3, 0], 'sine'], [18, [0, -0.5, 0], 'sine'], [24, [0, 0, 0], 'sine']],
            },
          },
        },
      },
    });
  }
  /* Void: a violet ring with a bright inner edge and three ice motes in orbit. */
  {
    const A = new Atlas(128, 64, 2);
    const alb = new Canvas(128, 64);
    const emi = new Canvas(128, 64);
    const Y = -12.6;
    const vio = cellPaint(P.vio3, P.vio4, 150);
    const edge = cellPaint(P.vio5, P.vio6, 220);
    const ice = cellPaint(P.ice5, P.ice6, 255);
    const ring = ringOf(A, alb, emi, 22, 4.7, Y, 1.25, 0.8, vio);
    const lip = ringOf(A, alb, emi, 22, 4.0, Y - 0.2, 0.5, 0.4, edge);
    const motes = [];
    for (let k = 0; k < 3; k++) {
      const a = (k / 3) * Math.PI * 2;
      const x = Math.cos(a) * 6.2, z = Math.sin(a) * 6.2, dy = [-0.7, 0.5, 0][k];
      motes.push(cell(A, alb, emi, [x - 0.42, Y + dy - 0.42, z - 0.42], [x + 0.42, Y + dy + 0.42, z + 0.42], ice));
    }
    save('void-halo', 'texture.png', alb);
    save('void-halo', 'emissive.png', emi);
    save('void-halo', 'model.json', {
      format: 'void-cosmetic/1', attach: 'halo', texture_size: [128, 64], procedural: 'halo',
      bones: [
        { name: 'tilt', pivot: [0, Y, 0], rotation: [14, 0, 0] },
        { name: 'ring', parent: 'tilt', pivot: [0, Y, 0], cubes: [...ring, ...lip] },
        { name: 'motes', parent: 'tilt', pivot: [0, Y, 0], cubes: motes },
      ],
      glow: [
        ...[0, 1, 2].map((k) => ({ bone: 'motes', at: [Math.cos(k * 2.094) * 6.2, Y + [-0.7, 0.5, 0][k], Math.sin(k * 2.094) * 6.2], color: P.ice5, size: 2.6, strength: 0.55 })),
        ...[0, 1, 2, 3].map((q) => ({ bone: 'ring', at: [Math.cos(q * Math.PI / 2) * 4.7, Y, Math.sin(q * Math.PI / 2) * 4.7], color: P.vio4, size: 4, strength: 0.22 })),
      ],
      animations: {
        idle: {
          length: 12, loop: true, bones: {
            motes: { rotation: [[0, [0, 0, 0], 'linear'], [12, [0, -360, 0], 'linear']] },
            ring: { rotation: [[0, [0, 0, 0], 'linear'], [12, [0, 90, 0], 'linear']] },
          },
        },
      },
    });
  }
}

/* ================================================================================ masks */

function masks(save) {
  /* Ice visor: a curved band of tinted glass in a slim frame, a refracted highlight that sweeps. */
  {
    const F = 12, W = 128, H = 64;
    const alb = new Canvas(W, H * F);
    const emi = new Canvas(W, H * F);
    const A = new Atlas(W, H, 2);
    const Y0 = -5.5, Y1 = -3.2, Z0 = -4.95, Z1 = -4.45;
    const glassPaint = (seg) => (face, i, j, w, h, f) => {
      const front = face === 'north' || (seg !== 0 && (face === 'east' || face === 'west'));
      if (!front) return { c: '#0a1a22' };
      const gx = (seg === 0 ? i / w : seg < 0 ? i / w - 0.5 : i / w + 1);  // across the whole band
      const t = j / (h - 1);
      // Deep teal glass, darker at the top, a pale reflected band low down.
      let c = mix(P.ice0, P.ice1, t);
      if (Math.abs(t - 0.72) < 0.1) c = mix(c, P.ice2, 0.5);
      // The refraction highlight: two parallel diagonal streaks travelling across.
      const pos = gx * 1.0 + t * 0.35 - ((f / F) * 2.4 - 0.7);
      const streak = Math.abs(pos) < 0.05 ? 1 : Math.abs(pos - 0.12) < 0.025 ? 0.6 : 0;
      if (streak) c = mix(c, P.ice6, 0.85 * streak);
      const e = streak ? [P.ice6, Math.round(220 * streak)] : (Math.abs(t - 0.45) < 0.08 ? [P.ice4, 70] : null);
      return { c, e };
    };
    const rim = (face, i, j, w, h) => ({ c: face === 'up' ? '#4a5560' : j === 0 ? '#3a4450' : '#1b2228' });
    const paint = (cube, rects, look) => {
      for (const [face, r] of Object.entries(rects)) {
        const w = r[2] - r[0], h = r[3] - r[1];
        for (let f = 0; f < F; f++) for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) {
          const res = look(face, i, j, w, h, f);
          if (!res) continue;
          alb.set(r[0] + i, f * H + r[1] + j, res.c);
          if (res.e) emi.set(r[0] + i, f * H + r[1] + j, res.e[0], res.e[1]);
        }
      }
      return cube;
    };
    const mk = (from, to, look) => { const { cube, rects } = A.cube(from, to); return paint(cube, rects, look); };
    // A wraparound band: the front glass spans the face and turns the corners onto the sides,
    // framed top and bottom by a slim rim.
    const front = [mk([-4.75, Y0, Z0], [4.75, Y1, Z1], glassPaint(0)),
      mk([-4.9, Y0 - 0.35, Z0 - 0.1], [4.9, Y0, Z1], rim), mk([-4.9, Y1, Z0 - 0.1], [4.9, Y1 + 0.3, Z1], rim)];
    const side = (sx) => [mk(sx > 0 ? [4.25, Y0, Z1] : [-4.75, Y0, Z1], sx > 0 ? [4.75, Y1, -1.2] : [-4.25, Y1, -1.2], glassPaint(sx)),
      mk(sx > 0 ? [4.25, Y0 - 0.35, Z1] : [-4.9, Y0 - 0.35, Z1], sx > 0 ? [4.9, Y0, -1.2] : [-4.25, Y0, -1.2], rim),
      mk(sx > 0 ? [4.25, Y1, Z1] : [-4.9, Y1, Z1], sx > 0 ? [4.9, Y1 + 0.3, -1.2] : [-4.25, Y1 + 0.3, -1.2], rim)];
    save('ice-visor', 'texture.png', alb);
    save('ice-visor', 'emissive.png', emi);
    save('ice-visor', 'model.json', {
      format: 'void-cosmetic/1', attach: 'head', texture_size: [W, H], frames: { count: F, fps: 9 }, helmet_fit: true,
      bones: [
        { name: 'visor', pivot: [0, -4.3, -4.7], cubes: [...front, ...side(1), ...side(-1)] },
      ],
      glow: [{ bone: 'visor', at: [0, -4.3, -5.1], color: P.ice4, size: 3.6, strength: 0.22 }],
    });
  }
  /* Rift mask: a faceplate in plates (brow, cheeks, chin) of dusky ceramic, eye slits lit from
   * within, and a seam of violet light splitting it. */
  {
    const F = 10, W = 128, H = 64;
    const alb = new Canvas(W, H * F);
    const emi = new Canvas(W, H * F);
    const A = new Atlas(W, H, 2);
    const ceramic = '#4a4258', light = '#6c6280', deep = '#2a2434';
    // The seam as a polyline in face pixels (x across -4.4..4.4, y down -7.6..-0.4).
    const seam = [[0.6, -7.6], [0.2, -6.3], [0.9, -5.2], [0.4, -3.9], [1.3, -2.6], [0.7, -1.3], [1.1, -0.4]];
    const seamDist = (x, y) => {
      let d = Infinity;
      for (let k = 1; k < seam.length; k++) {
        const [ax, ay] = seam[k - 1], [bx, by] = seam[k];
        const dx = bx - ax, dy = by - ay;
        const t = Math.max(0, Math.min(1, ((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy)));
        d = Math.min(d, Math.hypot(x - ax - dx * t, y - ay - dy * t));
      }
      return d;
    };
    const plate = (x0, y0, x1, y1, zf, slitTop, slitBottom) => {
      const { cube, rects } = A.cube([x0, y0, zf], [x1, y1, zf + 0.55]);
      for (const [face, r] of Object.entries(rects)) {
        const w = r[2] - r[0], h = r[3] - r[1];
        for (let f = 0; f < F; f++) {
          const pulse = 0.55 + 0.45 * Math.cos((f / F) * Math.PI * 2);
          for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) {
            const X = x0 + (i + 0.5) / w * (x1 - x0), Y = y0 + (j + 0.5) / h * (y1 - y0);
            let c, e = null;
            if (face === 'north') {
              const bevel = j === 0 || i === 0 ? 0.1 : (j === h - 1 || i === w - 1) ? -0.22 : 0;
              c = shade(mix(ceramic, light, 0.25 + 0.2 * (1 - j / h)), bevel + (hash(i, j, 41) < 0.12 ? 0.03 : 0));
              const sd = seamDist(X, Y);
              if (sd < 0.28) { c = mix(P.vio4, P.vio6, pulse); e = [P.vio5, Math.round(170 + 85 * pulse)]; }
              else if (sd < 0.6) { c = shade(c, -0.25); e = [P.vio3, Math.round(50 * pulse)]; }
            } else if ((face === 'down' && slitTop) || (face === 'up' && slitBottom)) {
              // The edges of the eye slits glow from inside.
              c = P.vio3;
              e = [P.vio4, Math.round(140 + 60 * pulse)];
            } else {
              c = face === 'up' ? light : deep;
            }
            alb.set(r[0] + i, f * H + r[1] + j, c);
            if (e) emi.set(r[0] + i, f * H + r[1] + j, e[0], e[1]);
          }
        }
      }
      return cube;
    };
    const Z = -4.95;
    const cubes = [
      plate(-4.5, -7.7, 4.5, -5.0, Z - 0.15, true, false),          // brow, standing proud
      plate(-4.4, -4.1, -0.5, -1.5, Z, false, true),                // cheeks below the slits
      plate(0.5, -4.1, 4.4, -1.5, Z, false, true),
      plate(-0.5, -5.0, 0.5, -1.5, Z + 0.05, false, false),         // the nose bridge between the eyes
      plate(-3.2, -1.5, 3.2, -0.3, Z + 0.1, false, false),          // chin
    ];
    save('rift-mask', 'texture.png', alb);
    save('rift-mask', 'emissive.png', emi);
    save('rift-mask', 'model.json', {
      format: 'void-cosmetic/1', attach: 'head', texture_size: [W, H], frames: { count: F, fps: 6 }, helmet_fit: true,
      bones: [{ name: 'mask', pivot: [0, -4, 0], cubes }],
      glow: [{ bone: 'mask', at: [0.6, -4.5, -5.3], color: P.vio4, size: 4.2, strength: 0.25 },
        { bone: 'mask', at: [-2.4, -4.5, -5.1], color: P.vio4, size: 2.2, strength: 0.3 },
        { bone: 'mask', at: [2.4, -4.5, -5.1], color: P.vio4, size: 2.2, strength: 0.3 }],
    });
  }
}

/* ================================================================================ orbit */

function orbit(save) {
  // A gyroscope of ice behind the shoulders: two rings of cells at an angle to each other turning
  // in opposite directions, a bright core, each ring with one lead cell. It frames the head from
  // behind and from the front and reads as one object from every angle.
  const A = new Atlas(128, 64, 2);
  const alb = new Canvas(128, 64);
  const emi = new Canvas(128, 64);
  const C = [0, -1.5, 5.6];
  const dim = cellPaint(P.ice3, P.ice4, 150);
  const lit = cellPaint(P.ice5, P.ice6, 240);
  const ringCells = (n, r, size) => {
    const out = [];
    for (let k = 0; k < n; k++) {
      const a = (k / n) * Math.PI * 2;
      const x = C[0] + Math.cos(a) * r, y = C[1] + Math.sin(a) * r;
      const s = k === 0 ? size * 1.5 : k % 2 ? size : size * 0.8;
      out.push(cell(A, alb, emi, [x - s / 2, y - s / 2, C[2] - 0.35], [x + s / 2, y + s / 2, C[2] + 0.35], k === 0 ? lit : dim));
    }
    return out;
  };
  const outer = ringCells(18, 7.2, 1.0);
  const inner = ringCells(12, 5.6, 0.9);
  const core = cell(A, alb, emi, [C[0] - 0.7, C[1] - 0.7, C[2] - 0.7], [C[0] + 0.7, C[1] + 0.7, C[2] + 0.7], cellPaint(P.ice6, P.ice6, 255));
  save('orbit-ring', 'texture.png', alb);
  save('orbit-ring', 'emissive.png', emi);
  save('orbit-ring', 'model.json', {
    format: 'void-cosmetic/1', attach: 'body', texture_size: [128, 64], procedural: 'float',
    bones: [
      { name: 'frame', pivot: C, rotation: [0, 0, 0], cubes: [core] },
      { name: 'outer', parent: 'frame', pivot: C, cubes: outer },
      { name: 'gimbal', parent: 'frame', pivot: C, rotation: [0, 58, 0] },
      { name: 'inner', parent: 'gimbal', pivot: C, cubes: inner },
    ],
    glow: [
      { bone: 'frame', at: C, color: P.ice5, size: 5.5, strength: 0.4 },
      { bone: 'outer', at: [C[0] + 7.2, C[1], C[2]], color: P.ice5, size: 3, strength: 0.5 },
      { bone: 'inner', at: [C[0] + 5.6, C[1], C[2]], color: P.ice5, size: 2.6, strength: 0.45 },
      { bone: 'outer', at: [C[0], C[1] + 7.2, C[2]], color: P.ice4, size: 3.2, strength: 0.2 },
      { bone: 'outer', at: [C[0], C[1] - 7.2, C[2]], color: P.ice4, size: 3.2, strength: 0.2 },
    ],
    animations: {
      idle: {
        length: 30, loop: true, bones: {
          outer: { rotation: [[0, [0, 0, 0], 'linear'], [30, [0, 0, 360], 'linear']] },
          inner: { rotation: [[0, [0, 0, 0], 'linear'], [30, [0, 0, -720], 'linear']] },
          gimbal: { rotation: [[0, [0, 0, 0], 'linear'], [30, [0, 360, 0], 'linear']] },
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
  orbit(save);
}
