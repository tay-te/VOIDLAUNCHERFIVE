/**
 * The style test (pvp/design/cosmetics-style-test/): the same three items — a cape, a back item,
 * a hat — in two candidate directions, to be compared in game against the v3 set (void-cape,
 * void-wings, dapper-top-hat). Catalog items flagged `test`, ids `style-c-*` / `style-d-*`: they
 * ride in the jar for the showcase and never reach the Store.
 *
 * Both directions obey Minecraft's pixel scale, which the v3 set does not:
 *   - one texel per model pixel, the skin's density (capes at the vanilla 64 x 32, models through
 *     an Atlas at d = 1), so an item reads as part of the same world as the skin it sits on;
 *   - no smooth gradients: every surface is flat colour from a short hand-picked ramp, shaded by
 *     single texels (a lit top-left edge, a shadow on the lower right) and by the material shader;
 *   - ramps are hue-shifted (shadows cool, lights warm), never darkened with black;
 *   - wings sized to the body: about 1.6-2.2 blocks across when spread, not 3+.
 *
 * C · VOID luxe — restraint. Ivory, graphite and ink, brushed steel used once per item, and ONE
 * precise accent: a single violet glass jewel (VOID's #9F8BFF) set like a stone, the only light.
 * Clean, deliberate geometry; heavy cloth that hangs.
 *
 * D · Collectible toy — charm. One curated four-hue set (coral, butter, teal, cream) with
 * hue-shifted pixel shading, chunky exaggerated proportions with real volume, silhouettes that
 * read at a glance (a hero cape with a scalloped hem, puffy stub wings, a propeller cap), and a
 * playful motion personality: a light cape, quick fluttering wings, a propeller that spins up.
 */

import { Atlas, Canvas, MAT } from './lib.mjs';

/* ================================================================================ palettes */

/** C · luxe. Each ramp is dark → light; shadows lean cool, lights lean warm. */
const C = {
  ivory: ['#8f8c88', '#b3aea6', '#d4cec2', '#e8e2d6', '#f6f1e7'],
  graphite: ['#121318', '#1b1d22', '#25272d', '#30323a', '#3f4048'],
  ink: '#0f1015',
  steel: ['#4f545d', '#7d838d', '#adb3bc', '#dde1e7'],
  // The one accent: VOID violet, as a cut stone.
  jewel: ['#3b2f80', '#6e5bd6', '#9f8bff', '#e4ddff'],
};

/** D · toy. The capsule's four hues, each a five-step hue-shifted ramp. */
const D = {
  coral: ['#5e2448', '#a83a57', '#e85f5c', '#ff8d6c', '#ffbb98'],
  butter: ['#8f4f2a', '#d7882b', '#ffc246', '#ffde7c', '#fff3c2'],
  teal: ['#1d3556', '#1f6672', '#279e95', '#56cdb0', '#a6ecc9'],
  cream: ['#9c7c86', '#dcbba6', '#f6e6cf', '#fff4e3', '#fffcf5'],
};

/* =================================================================================== capes */

const FW = 10, FH = 16;

/**
 * A cape at the vanilla density (64 x 32, one frame): `outer(x, y)` and `lining(x, y)` paint the
 * two faces (x right as seen from behind, y down), `edge(y)` the one-texel sides, `top`/`bottom`
 * the collar and hem strips; each returns a colour, [colour, alpha] or null (transparent).
 * `mat(x, y)` names a material per outer texel (null = the cape's own).
 */
function cape({ outer, lining, edge, top, bottom, mat }) {
  const alb = new Canvas(64, 32);
  const m = new Canvas(64, 32);
  const put = (x, y, c) => {
    if (c == null) return;
    if (Array.isArray(c) && typeof c[0] === 'string') alb.set(x, y, c[0], c[1]);
    else alb.set(x, y, c);
  };
  for (let y = 0; y < FH; y++) {
    for (let x = 0; x < FW; x++) {
      put(1 + x, 1 + y, outer(x, y));
      // The lining is seen from the front: its u runs the other way.
      put(12 + x, 1 + y, lining(FW - 1 - x, y));
      const mk = mat ? mat(x, y) : null;
      if (mk) m.set(1 + x, 1 + y, MAT[mk]);
    }
    put(0, 1 + y, edge(y));
    put(11, 1 + y, edge(y));
    put(22, 1 + y, edge(y));
  }
  for (let x = 0; x < FW; x++) {
    put(1 + x, 0, top(x));
    put(11 + x, 0, bottom(x));
  }
  return { alb, mat: m };
}

/* C · luxe cape: "Atelier". Graphite wool, an ivory yoke, a brushed-steel bar where they meet with
 * the violet stone set off-centre in it like a clasp, one quiet ivory pinstripe down the right
 * third, an ivory lining that shows when it moves. Heavy: it hangs. */
function luxeCape(save) {
  const G = C.graphite, I = C.ivory, S = C.steel, J = C.jewel;
  const YOKE = 3, BAR = 3, STONE = 6, STRIPE = 7;
  const { alb, mat } = cape({
    outer: (x, y) => {
      if (y < YOKE) return y === 0 ? I[3] : I[2];
      if (y === BAR) {
        if (x === STONE) return J[2];
        // Brushed: a lit texel either side of the stone, the rest the mid steel.
        return x === STONE - 1 || x === STONE + 1 ? S[3] : S[2];
      }
      if (y === BAR + 1) return x === STONE ? G[0] : G[1]; // the bar's shadow, the stone's deeper
      if (x === STRIPE) return y === FH - 1 ? G[3] : I[0];
      if (y === FH - 1) return G[1]; // a weighted hem
      return G[2];
    },
    mat: (x, y) => (y === BAR ? (x === STONE ? 'glass' : 'metal') : null),
    lining: (x, y) => (y === 0 ? I[2] : y === FH - 1 ? I[1] : I[3]),
    edge: (y) => (y < YOKE ? I[1] : y === BAR ? S[1] : G[1]),
    top: () => I[2],
    bottom: () => G[0],
  });
  save('style-c-cape', 'texture.png', alb);
  save('style-c-cape', 'material.png', mat);
  // The stone's glint is the only light: small, faint, and mostly a night thing.
  const emi = new Canvas(64, 32);
  emi.set(1 + STONE, 1 + BAR, J[2], 200);
  save('style-c-cape', 'emissive.png', emi);
  save('style-c-cape', 'cape.json', { stiffness: 0.96, weight: 1.3 });
}

/* D · toy cape: "Hero". A coral hero cape with a chunky butter lightning bolt on a cream die-cut
 * raised print, a cream zigzag into a teal band and a scalloped hem cut out of the cloth, a teal
 * lining. Light: it flies out behind you. */
function toyCape(save) {
  const R = D.coral, B = D.butter, T = D.teal, K = D.cream;
  // The bolt, 6 wide x 9 tall, at (2, 2): a slanted head, the crossbar, a tail to a point.
  const BOLT = [
    '..####',
    '.####.',
    '.###..',
    '#####.',
    '.#####',
    '..###.',
    '..##..',
    '.##...',
    '.#....',
  ];
  const BX = 2, BY = 2;
  const bolt = (x, y) => {
    const i = x - BX, j = y - BY;
    return j >= 0 && j < BOLT.length && i >= 0 && i < 6 && BOLT[j][i] === '#';
  };
  // Scalloped hem: teeth two texels wide; symmetric, so lining and edges agree with the face.
  const GAP = new Set([0, 3, 6, 9]);
  const cut = (x, y) => y === FH - 1 && GAP.has(x);
  const ZIG = 12; // the zigzag's first row
  const { alb } = cape({
    outer: (x, y) => {
      if (cut(x, y)) return null;
      if (bolt(x, y)) return !bolt(x, y - 1) ? B[3] : B[2]; // lit where the shape faces up
      // A one-texel shade where the bolt's right edge meets the cloth, as a raised print casts.
      if (bolt(x - 1, y) && !bolt(x, y)) return R[1];
      if (y === ZIG) return x % 4 === 1 || x % 4 === 2 ? K[3] : R[2];
      if (y === ZIG + 1) return x % 4 === 0 || x % 4 === 3 ? K[3] : T[3];
      if (y > ZIG + 1) return y === FH - 1 ? T[1] : T[2];
      if (y === 0) return R[3];
      return R[2];
    },
    lining: (x, y) => (cut(x, y) ? null : y === 0 ? T[3] : y === FH - 1 ? T[1] : T[2]),
    edge: (y) => (y === FH - 1 ? null : y > ZIG ? T[1] : R[1]),
    top: () => R[3],
    // The hem's own thickness strip only under the teeth.
    bottom: (x) => (GAP.has(x) ? null : T[1]),
  });
  save('style-d-cape', 'texture.png', alb);
  save('style-d-cape', 'cape.json', { stiffness: 0.82, weight: 0.78 });
}

/* =================================================================================== models */

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
 * A model under construction: an atlas at the skin's density and the canvases it paints into.
 * `box(from, to, look, material)` allocates a cuboid and paints each face with
 * `look(face, i, j, w, h)` → colour | [colour, alpha] | null, optionally an emissive via
 * `glow(face, i, j, w, h)` → [colour, alpha] | null.
 */
function builder(w, h) {
  const A = new Atlas(w, h, 1);
  const alb = new Canvas(w, h);
  const emi = new Canvas(w, h);
  let lit = false;
  return {
    alb, emi,
    hasGlow: () => lit,
    box(from, to, look, material, glow, faces = 'nsewud') {
      const { cube, rects } = A.cube(from, to, faces);
      for (const [face, r] of Object.entries(rects)) {
        const fw = r[2] - r[0], fh = r[3] - r[1];
        for (let j = 0; j < fh; j++) for (let i = 0; i < fw; i++) {
          const c = look(face, i, j, fw, fh);
          if (c != null) {
            if (Array.isArray(c) && typeof c[0] === 'string') alb.set(r[0] + i, r[1] + j, c[0], c[1]);
            else alb.set(r[0] + i, r[1] + j, c);
          }
          const g = glow ? glow(face, i, j, fw, fh) : null;
          if (g) {
            emi.set(r[0] + i, r[1] + j, g[0], g[1]);
            lit = true;
          }
        }
      }
      if (material) cube.material = material;
      return cube;
    },
  };
}

/**
 * Flat block shading from a five-step ramp: a lit top, sides in the base with a lit top row and a
 * shaded bottom row, a dark underside, and one highlight texel on the upper-left of the back face.
 * Single texels and flat fills only.
 */
const block = (r, { hi = true } = {}) => (face, i, j, w, h) => {
  if (face === 'up') return r[3];
  if (face === 'down') return r[0];
  if (hi && face === 'south' && i === 0 && j === 0) return r[4];
  if (j === 0 && h > 2) return r[3];
  if (j === h - 1 && h > 2) return r[1];
  return face === 'east' || face === 'west' ? r[1] : r[2];
};

/* C · luxe wings: "Blade". A clean ivory spar from a brushed-steel hinge, graphite blades hung
 * from it like the slats of a fan, stepping darker inward, the outermost primary in steel; the
 * violet stone is set near the steel blade's tip. They barely stir: a blade does not flutter. */
function luxeWings(save) {
  const I = C.ivory, G = C.graphite, S = C.steel, J = C.jewel;
  const W = 64, H = 64;
  const b = builder(W, H);
  const ROOT = [-1.2, 1.0, 3.0], ARM = 6, HAND = 8;
  const WRIST = [ROOT[0] - ARM, 1.0, 3.0];
  const spar = (face, i, j, w, h) => (face === 'up' ? I[4] : face === 'down' ? I[1] : j === 0 ? I[3] : I[2]);
  const steel = (face, i, j) => (face === 'up' ? S[3] : face === 'down' ? S[0] : j === 0 ? S[3] : S[2]);
  // A blade: flat graphite of one value, a lit leading texel row, the tip cut on the outer corner.
  const blade = (tone, outerLeft) => (face, i, j, w, h) => {
    const outer = outerLeft ? i === 0 : i === w - 1;
    if ((face === 'north' || face === 'south') && j === h - 1 && (outerLeft ? i === 0 : i === w - 1) && w > 1) return null;
    if ((face === 'east' || face === 'west') && j === h - 1) return null;
    if (face === 'up') return tone[3];
    if (face === 'down') return tone[0];
    if (j === 0) return tone[3];
    if (outer && face !== 'east' && face !== 'west') return tone[1];
    return tone[2];
  };
  const tones = [
    [G[0], G[1], G[2], G[3]],
    [G[0], G[1], G[3], G[4]],
    [G[1], G[2], G[4], I[0]],
  ];
  const steelTone = [S[0], S[1], S[2], S[3]];

  const hinge = b.box([-2.0, 0.2, 2.5], [-0.9, 1.8, 3.5], steel, 'metal');
  const arm = b.box([WRIST[0], 0.4, 2.6], [ROOT[0] - 0.9, 1.4, 3.4], spar, 'matte');
  const hand = b.box([WRIST[0] - HAND, 0.5, 2.65], [WRIST[0], 1.3, 3.35], spar, 'matte');
  const cap = b.box([WRIST[0] - HAND - 0.8, 0.35, 2.55], [WRIST[0] - HAND, 1.45, 3.45], steel, 'metal');
  // Secondaries: three blades under the arm, innermost shortest and darkest.
  const sec = [
    b.box([-3.4, 1.4, 2.8], [-1.9, 5.9, 3.3], blade(tones[0], false), 'cloth'),
    b.box([-5.3, 1.4, 2.8], [-3.6, 6.9, 3.3], blade(tones[1], false), 'cloth'),
    b.box([-7.2, 1.4, 2.8], [-5.5, 7.9, 3.3], blade(tones[2], false), 'cloth'),
  ];
  // Primaries: four blades under the hand, longest outermost, the last in steel.
  const xs = [[-8.8, -7.3, 8], [-10.6, -9.0, 9], [-12.4, -10.8, 10], [-14.4, -12.6, 10]];
  // The outermost primary is steel, and the stone is set near its tip: at night, two violet
  // points at the wingtips are the only light.
  const stoneAt = (face, i, j, w, h) => face === 'south' && i === 0 && j === h - 2;
  const steelBlade = blade(steelTone, true);
  const pri = xs.map(([x0, x1, len], k) => (k === 3
    ? b.box([x0, 1.3, 2.8], [x1, 1.3 + len, 3.3], (...a) => (stoneAt(...a) ? J[2] : steelBlade(...a)), 'metal',
      (...a) => (stoneAt(...a) ? [J[2], 200] : null))
    : b.box([x0, 1.3, 2.8], [x1, 1.3 + len, 3.3], blade(tones[Math.min(2, k)], true), 'cloth')));
  const right = [
    { name: 'wing_r', parent: null, pivot: ROOT, rotation: [0, 0, 16], cubes: [hinge, arm] },
    { name: 'wing_r_sec', parent: 'wing_r', pivot: [ROOT[0], 1.4, 3.0], cubes: sec },
    { name: 'wing_r_tip', parent: 'wing_r', pivot: WRIST, rotation: [0, 0, 18], cubes: [hand, cap] },
    { name: 'wing_r_pri', parent: 'wing_r_tip', pivot: [WRIST[0], 1.3, 3.0], cubes: pri },
  ];
  save('style-c-wings', 'texture.png', b.alb);
  save('style-c-wings', 'emissive.png', b.emi);
  save('style-c-wings', 'model.json', {
    format: 'void-cosmetic/1', attach: 'body', texture_size: [W, H], procedural: 'wings',
    motion: { rate: 0.7, amp: 0.45 },
    bones: [...right, ...mirror(right)],
  });
}

/**
 * Extrudes a pixel-art sprite into voxels the way Minecraft extrudes an item: every row's runs of
 * opaque texels become one cuboid a texel tall, so the silhouette is exactly the sprite's and the
 * sides read as solid. `rows` are strings, u (column) running outward from `x0` along -x (a right
 * wing), v down from `y0`; `colour(ch, u, v, face)` paints each texel; `depth(v)` gives a row's
 * [z0, z1]; `split(v)` names the bucket (bone) a row's cubes go to.
 */
function extrude(b, rows, x0, y0, depth, colour, split, material) {
  const out = {};
  rows.forEach((row, v) => {
    let u = 0;
    while (u < row.length) {
      if (row[u] === '.') { u++; continue; }
      let e = u;
      while (e + 1 < row.length && row[e + 1] !== '.') e++;
      const [z0, z1] = depth(v);
      const u0 = u, u1 = e;
      const cube = b.box([x0 - u1 - 1, y0 + v, z0], [x0 - u0, y0 + v + 1, z1], (face, i) => {
        // South (+z, seen from behind): u along -x, so texel 0 is the run's inner end.
        const su = face === 'south' ? u0 + i : face === 'east' ? u0 : face === 'west' ? u1 : u1 - i;
        return colour(row[su], su, v, face);
      }, material);
      (out[split(v)] ??= []).push(cube);
      u = e + 1;
    }
  });
  return out;
}

/* D · toy wings: "Puff". Plush angel wings drawn as a pixel-art sprite and extruded like a
 * Minecraft item: a chunky butter body, fat feathers stepping longer outward with gaps between
 * them, each dipped teal at the tip, raised in a happy V. Quick and fluttery. */
function toyWings(save) {
  const T = D.teal, K = D.cream;
  const W = 64, H = 64;
  const b = builder(W, H);
  const ROOT = [-1.0, 1.6, 3.3];
  // The arm from the shoulder blade (6 texels), the hand from the wrist (9). K body, k its
  // shaded lower row; feathers three texels wide, alternating F and f (a step deeper) so they
  // read apart without gaps, each ending in a rounded teal tip (T, t) that scallops the edge.
  const ARM = [
    '.KKKK.',
    'KKKKKK',
    'KKKKKK',
    'kkkkkk',
    'FFFfff',
    'FFFfff',
    'TTTfff',
    '.T.ttt',
    '....t.',
  ];
  const HAND = [
    'KKKKKK...',
    'KKKKKKKK.',
    'KKKKKKKKK',
    'kkkkkkkkk',
    'FFFfffFFF',
    'FFFfffFFF',
    'FFFfffFFF',
    'TTTfffFFF',
    '.T.tttFFF',
    '....t.TTT',
    '......TTT',
    '.......T.',
  ];
  const WRIST = [ROOT[0] - ARM[0].length, ROOT[1], ROOT[2]];
  const Y0 = ROOT[1] - 2;
  const BODY = 4; // rows above this are the body, the rest feathers
  const depth = (v) => (v < BODY ? [2.3, 4.3] : [2.8, 4.0]);
  // Butter plush, cream where the light catches it, the shadows warming to orange (hue-shifted).
  const Y = D.butter;
  const colour = (ch, u, v, face) => {
    const side = face === 'east' || face === 'west';
    if (ch === 'K') return face === 'up' ? K[3] : face === 'down' ? Y[1] : side ? Y[2] : v === 0 ? K[3] : Y[3];
    if (ch === 'k') return face === 'down' ? Y[1] : side ? Y[1] : Y[2];
    if (ch === 'F') return face === 'up' ? Y[4] : face === 'down' ? Y[1] : side ? Y[1] : u % 3 === 0 ? K[3] : Y[3];
    if (ch === 'f') return face === 'up' ? Y[3] : face === 'down' ? Y[1] : side ? Y[1] : u % 3 === 0 ? Y[3] : Y[2];
    // Teal tips, lit on the feather's first column; the deeper feathers' tips a step deeper.
    if (face === 'down' || side) return T[1];
    if (ch === 't') return u % 3 === 0 ? T[2] : T[1];
    return u % 3 === 0 ? T[3] : T[2];
  };
  const arm = extrude(b, ARM, ROOT[0], Y0, depth, colour, (v) => (v < BODY ? 'body' : 'feathers'), 'matte');
  const hand = extrude(b, HAND, WRIST[0], Y0, depth, colour, (v) => (v < BODY ? 'body' : 'feathers'), 'matte');
  const right = [
    { name: 'wing_r', parent: null, pivot: ROOT, rotation: [0, 0, 22], cubes: arm.body },
    { name: 'wing_r_sec', parent: 'wing_r', pivot: [ROOT[0], Y0 + BODY, 3.3], cubes: arm.feathers },
    { name: 'wing_r_tip', parent: 'wing_r', pivot: WRIST, rotation: [0, 0, 18], cubes: hand.body },
    { name: 'wing_r_pri', parent: 'wing_r_tip', pivot: [WRIST[0], Y0 + BODY, 3.3], cubes: hand.feathers },
  ];
  save('style-d-wings', 'texture.png', b.alb);
  save('style-d-wings', 'model.json', {
    format: 'void-cosmetic/1', attach: 'body', texture_size: [W, H], procedural: 'wings',
    motion: { rate: 1.7, amp: 2.2 },
    bones: [...right, ...mirror(right)],
  });
}

/* C · luxe hat: "Atelier fedora". Ivory felt, a pinched crown, an ink grosgrain band, and on the
 * band a brushed-steel pin holding the violet stone. */
function luxeHat(save) {
  const I = C.ivory, S = C.steel, J = C.jewel;
  const W = 64, H = 64;
  const b = builder(W, H);
  const felt = (face, i, j, w, h) => {
    if (face === 'up') {
      // The pinch: a shaded crease front to back down the middle of the crown.
      const mid = Math.floor(w / 2);
      if (i === mid - 1) return I[1];
      if (i === mid) return I[2];
      return I[3];
    }
    if (face === 'down') return I[0];
    if (j === 0) return I[3];
    return face === 'east' || face === 'west' ? I[1] : I[2];
  };
  const brim = (face, i, j, w, h) => {
    if (face === 'up') {
      // A shade ring where the crown sits, the outer edge lit.
      const e = Math.min(i, j, w - 1 - i, h - 1 - j);
      return e === 0 ? I[4] : I[3];
    }
    if (face === 'down') return I[1];
    return I[2];
  };
  const band = (face, i, j, w, h) => (face === 'up' ? C.graphite[3] : face === 'down' ? C.ink : j === 0 ? C.graphite[2] : C.graphite[1]);
  const cubes = [
    b.box([-5.6, -8.6, -5.6], [5.6, -8.0, 5.6], brim, 'cloth'),
    b.box([-4.1, -11.6, -4.1], [4.1, -8.6, 4.1], felt, 'cloth'),
    b.box([-3.5, -12.4, -3.3], [3.5, -11.6, 3.3], felt, 'cloth'),
    b.box([-4.25, -9.6, -4.25], [4.25, -8.6, 4.25], band, 'cloth'),
    // The pin, on the band at the front left: brushed steel, the stone set in its inner end.
    b.box([-3.4, -9.6, -4.45], [-1.4, -8.6, -4.25], (face, i) => (face === 'north' && i === 1 ? J[2] : face === 'north' ? S[1] : S[0]), 'metal',
      (face, i) => (face === 'north' && i === 1 ? [J[2], 200] : null)),
  ];
  save('style-c-hat', 'texture.png', b.alb);
  save('style-c-hat', 'emissive.png', b.emi);
  save('style-c-hat', 'model.json', {
    format: 'void-cosmetic/1', attach: 'head', texture_size: [W, H], helmet_fit: true,
    bones: [{ name: 'hat', pivot: [0, -8, 0], rotation: [-3, 0, -4], cubes }],
  });
}

/* D · toy hat: "Propeller cap". A four-colour panelled cap with a teal visor and a butter stem;
 * the propeller spins up in bursts and coasts, forever. */
function toyHat(save) {
  const R = D.coral, B = D.butter, T = D.teal, K = D.cream;
  const W = 64, H = 64;
  const b = builder(W, H);
  // Side panels: each face its own hue, a seam texel of the next hue's shadow at the corner.
  const panel = { north: R, east: B, south: T, west: K };
  const cap = (face, i, j, w, h) => {
    if (face === 'up') {
      // Four triangles meeting at the button, in the side panels' hues.
      const cx = (w - 1) / 2, cy = (h - 1) / 2, dx = i - cx, dy = j - cy;
      // u/d faces: u along +x, v along -z (v down = toward north? the up face's v runs to -z).
      const r = Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? B : K) : (dy > 0 ? R : T);
      return Math.abs(Math.abs(dx) - Math.abs(dy)) < 0.6 ? r[2] : r[3];
    }
    if (face === 'down') return T[0];
    const r = panel[face];
    if (j === 0) return r[3];
    if (j === h - 1) return r[1];
    if (i === w - 1) return r[1];
    return i === 0 ? r[3] : r[2];
  };
  const visor = (face, i, j, w, h) => (face === 'up' ? T[3] : face === 'down' ? T[0] : j === 0 ? T[3] : T[1]);
  const stem = block(B, { hi: false });
  const hub = block(R, { hi: false });
  const blade = (r) => (face, i, j, w) => (face === 'up' ? (i === 0 || i === w - 1 ? r[4] : r[3]) : face === 'down' ? r[1] : r[2]);
  const capCubes = [
    b.box([-4.3, -11.2, -4.3], [4.3, -7.8, 4.3], cap, 'matte'),
    b.box([-3.6, -12.0, -3.6], [3.6, -11.2, 3.6], cap, 'matte'),
    b.box([-3.8, -8.4, -7.6], [3.8, -7.8, -4.3], visor, 'matte'),
    b.box([-0.5, -13.4, -0.5], [0.5, -12.0, 0.5], stem, 'matte'),
  ];
  const propCubes = [
    b.box([-0.9, -14.2, -0.9], [0.9, -13.2, 0.9], hub, 'matte'),
    b.box([-5.2, -13.9, -0.8], [-0.9, -13.4, 0.8], blade(B), 'matte'),
    b.box([0.9, -13.9, -0.8], [5.2, -13.4, 0.8], blade(T), 'matte'),
  ];
  save('style-d-hat', 'texture.png', b.alb);
  save('style-d-hat', 'model.json', {
    format: 'void-cosmetic/1', attach: 'head', texture_size: [W, H], helmet_fit: true,
    bones: [
      { name: 'cap', pivot: [0, -8, 0], rotation: [-6, 0, 5], cubes: capCubes },
      { name: 'prop', parent: 'cap', pivot: [0, -13.4, 0], cubes: propCubes },
    ],
    // Spins up in a burst (two turns with an ease), then coasts a slow half turn: 720 degrees a
    // loop, so it wraps seamlessly.
    animations: {
      idle: {
        length: 2.6, loop: true,
        bones: { prop: { rotation: [[0, [0, 0, 0], 'linear'], [1.1, [0, 540, 0], 'cubic'], [2.6, [0, 720, 0], 'linear']] } },
      },
    },
  });
}

export function paintStyleTest(save) {
  luxeCape(save);
  toyCape(save);
  luxeWings(save);
  toyWings(save);
  luxeHat(save);
  toyHat(save);
}
