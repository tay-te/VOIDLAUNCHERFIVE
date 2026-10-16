/**
 * The 3D items of the VOID luxe line (docs/cosmetics.md, "The collection"): back items, hats,
 * halos and masks. Cuboid models in model pixels (y down) relative to their attachment — the body
 * (origin at the neck, back surface at z = +2, -x the wearer's right) or the head (origin at the
 * neck, head in x, z ±4 and y -8..0, face at z = -4) — painted one texel per model pixel, the
 * skin's density, from the collection's ramps only: neutrals, one metal family, one jewel.
 */

import { FAB, J, M, N, block, builder, metal, mirror, saveModel } from './collection.mjs';

/** Jewel strips twinkle on this many frames at 6 fps. */
const TW = 8;

/* ================================================================================== back */

/* Blade wings (Atelier: silver · amethyst). A polished silver spar from a silver hinge, ivory
 * lacquer blades hung from it like the slats of a fan, stepping lighter outward, the outermost
 * primary in silver, and an amethyst set in a silver boss at each wrist. About two blocks across;
 * they barely stir: a blade does not flutter. */
function bladeWings(save) {
  const I = N.ivory, B = N.bone, S = M.silver;
  const b = builder(64, 64, { frames: TW });
  const ROOT = [-1.2, 1.0, 3.0], ARM = 6, HAND = 8;
  const WRIST = [ROOT[0] - ARM, 1.0, 3.0];
  // A blade: flat lacquer, a lit leading row, the tip cut on the outer corner, a shaded inner edge.
  const blade = (r, outerLeft) => (face, i, j, w, h) => {
    const outer = outerLeft ? i === 0 : i === w - 1;
    if ((face === 'north' || face === 'south') && j === h - 1 && outer && w > 1) return null;
    if ((face === 'east' || face === 'west') && j === h - 1) return null;
    if (face === 'up') return r[3];
    if (face === 'down') return r[0];
    if (j === 0) return r[3];
    if (!outer && face !== 'east' && face !== 'west' && w > 1 && (outerLeft ? i === w - 1 : i === 0)) return r[1];
    return face === 'east' || face === 'west' ? r[1] : r[2];
  };
  const bone = [B[0], B[1], B[2], B[3]];
  const ivoryMid = [I[0], I[1], I[2], I[3]];
  const ivoryHi = [I[1], I[2], I[3], I[4]];
  const silver = [S[0], S[1], S[2], S[4]];

  const hinge = b.box([-2.1, 0.1, 2.4], [-0.9, 1.9, 3.6], metal(S), 'metal');
  const arm = b.box([WRIST[0], 0.4, 2.55], [ROOT[0] - 0.9, 1.5, 3.45], metal(S), 'metal');
  const hand = b.box([WRIST[0] - HAND, 0.5, 2.6], [WRIST[0], 1.4, 3.4], metal(S), 'metal');
  const cap = b.box([WRIST[0] - HAND - 0.9, 0.3, 2.5], [WRIST[0] - HAND, 1.6, 3.5], metal(S), 'metal');
  // The wrist boss and its stone, facing back (+z), where the eye lands from behind.
  const boss = b.box([WRIST[0] - 0.9, 0.0, 3.3], [WRIST[0] + 0.9, 1.9, 3.75], (face, i, j, w, h) =>
    (face === 'south' ? (j === 0 || i === 0 ? S[4] : S[2]) : S[1]), 'metal');
  const gem = b.gem([WRIST[0] - 0.5, 0.45, 3.75], [WRIST[0] + 0.5, 1.45, 4.25], J.amethyst);
  // Secondaries: three blades under the arm, innermost shortest, bone to ivory.
  const sec = [
    b.box([-3.4, 1.5, 2.8], [-1.9, 6.0, 3.25], blade(bone, false), 'matte'),
    b.box([-5.3, 1.5, 2.8], [-3.6, 7.0, 3.25], blade(ivoryMid, false), 'matte'),
    b.box([-7.2, 1.5, 2.8], [-5.5, 8.0, 3.25], blade(ivoryMid, false), 'matte'),
  ];
  // Primaries: four blades under the hand, longest outermost, the last in polished silver.
  const xs = [[-8.8, -7.3, 8], [-10.6, -9.0, 9], [-12.4, -10.8, 10], [-14.4, -12.6, 10.5]];
  const pri = xs.map(([x0, x1, len], k) => (k === 3
    ? b.box([x0, 1.3, 2.8], [x1, 1.3 + len, 3.25], blade(silver, true), 'metal')
    : b.box([x0, 1.3, 2.8], [x1, 1.3 + len, 3.25], blade(k === 2 ? ivoryHi : ivoryMid, true), 'matte')));
  const right = [
    { name: 'wing_r', parent: null, pivot: ROOT, rotation: [0, 0, 16], cubes: [hinge, arm] },
    { name: 'wing_r_sec', parent: 'wing_r', pivot: [ROOT[0], 1.4, 3.0], cubes: sec },
    { name: 'wing_r_tip', parent: 'wing_r', pivot: WRIST, rotation: [0, 0, 18], cubes: [hand, cap, boss, gem] },
    { name: 'wing_r_pri', parent: 'wing_r_tip', pivot: [WRIST[0], 1.3, 3.0], cubes: pri },
  ];
  saveModel(save, 'blade-wings', b, {
    attach: 'body', procedural: 'wings', motion: { rate: 0.7, amp: 0.5 },
    bones: [...right, ...mirror(right)],
  });
}

/* ================================================================================== head */

/* Atelier fedora (silver · amethyst). Dove-grey felt, worn low on the head: a crown taller in
 * its lines than its brim, pinched at the front into a teardrop with a centre crease, an aubergine
 * grosgrain band (the Atelier cloth) with a silver pin holding the amethyst, and a short brim with
 * rounded corners, snapped down in front and turned up behind. A fedora, never a stetson: the brim
 * stands about two pixels off the crown. */
function atelierFedora(save) {
  const D = FAB.dove, A = FAB.aubergine, S = M.silver;
  const b = builder(128, 64, { frames: TW });
  const Y0 = -6.9, BAND = -8.3, TOP = -11.3; // brim line, band top, crown shoulder
  const R = 4.65; // crown wall: clears the skin's hat layer (4.5)
  const felt = (face, i, j, w, h) => {
    if (face === 'up') return D[3];
    if (face === 'down') return D[0];
    if ((face === 'north' || face === 'south') && i === 1) return D[3]; // the felt's sheen
    return face === 'east' || face === 'west' ? D[1] : D[2];
  };
  const upper = (face, i, j, w, h) => {
    if (face === 'up') {
      // The crease: a shaded valley front to back, lit on its left wall.
      const mid = (w - 1) / 2;
      if (Math.abs(i - mid) < 0.6) return D[1];
      if (Math.abs(i - mid + 1) < 0.6) return D[4];
      return D[3];
    }
    if (face === 'down') return D[0];
    if ((face === 'north' || face === 'south') && i === 1) return D[3];
    return face === 'east' || face === 'west' ? D[1] : D[2];
  };
  // The pinch: the front of the upper crown, narrower, its two sides in shadow where the felt is
  // pressed in.
  const pinch = (face, i, j, w, h) => {
    if (face === 'north' && (i === 0 || i === w - 1)) return D[1];
    return upper(face, i, j, w, h);
  };
  // The brim's top catches the sun more than any other face: painted a step darker than the
  // crown's top so it reads as the same felt, not a pale disc.
  const brimTop = (face, i, j, w, h) => {
    if (face === 'up') return D[2];
    if (face === 'down') return D[0];
    return D[1];
  };
  const band = (face, i, j, w, h) => (face === 'up' ? A[3] : face === 'down' ? A[0] : j === 0 ? A[3] : A[2]);
  const MID = TOP - 1.1, CAP = MID - 0.45;
  const BR = [Y0, Y0 + 0.6]; // the brim hangs from under the band
  // A cuboid with its four vertical edges chamfered by `c` (two crossed boxes): in plan an
  // octagon, so the crown reads round from every side instead of as a box.
  const round = (r, c, y0, y1, look, z0 = -r, z1 = r) => [
    b.box([-r, y0, z0 + c], [r, y1, z1 - c], look, 'cloth'),
    b.box([-r + c, y0, z0], [r - c, y1, z1], look, 'cloth'),
  ];
  const crown = [
    // The walls: a course under the band, then one stepped in a little above it (the step hides
    // under the band's top edge, so no ledge of felt catches the light): the crown tapers.
    ...round(R, 0.7, BAND, Y0, felt),
    ...round(R - 0.3, 0.8, TOP, BAND, felt),
    // The upper crown in plan is a teardrop: full behind, pinched narrower at the front.
    ...round(3.75, 0.7, MID, TOP, upper, -1.0, 4.05),
    ...round(2.7, 0.5, MID, TOP, pinch, -3.55, -0.6),
    // Two low lobes either side of the crease, and the crease's floor between them.
    b.box([-2.6, CAP, -2.5], [-0.55, MID, 3.4], upper, 'cloth'),
    b.box([0.55, CAP, -2.5], [2.6, MID, 3.4], upper, 'cloth'),
    b.box([-0.55, CAP + 0.35, -2.0], [0.55, MID, 3.0], upper, 'cloth'),
    ...round(R + 0.1, 0.8, BAND, Y0, band),
    // The pin, on the band at the front, the wearer's left: polished silver, the stone in it.
    b.box([1.0, BAND + 0.05, -R - 0.35], [3.0, Y0 - 0.05, -R - 0.1], (face, i, j, w) =>
      (face === 'north' ? (i === 0 || j === 0 ? S[4] : S[2]) : S[1]), 'metal'),
    b.gem([1.5, BAND + 0.2, -R - 0.75], [2.5, Y0 - 0.2, -R - 0.35], J.amethyst),
  ];
  // The brim: about two pixels off the crown, its corners rounded in one step.
  const W = 6.6, C = W - 0.9;
  const sideR = [b.box([-W, BR[0], -R + 0.2], [-R, BR[1], R - 0.2], brimTop, 'cloth')];
  const sideL = [b.box([R, BR[0], -R + 0.2], [W, BR[1], R - 0.2], brimTop, 'cloth')];
  const front = [
    b.box([-R + 0.2, BR[0], -W], [R - 0.2, BR[1], -R], brimTop, 'cloth'),
    b.box([-C, BR[0], -C], [-R + 0.2, BR[1], -R], brimTop, 'cloth'),
    b.box([R - 0.2, BR[0], -C], [C, BR[1], -R], brimTop, 'cloth'),
  ];
  const back = [
    b.box([-R + 0.2, BR[0], R], [R - 0.2, BR[1], W], brimTop, 'cloth'),
    b.box([-C, BR[0], R], [-R + 0.2, BR[1], C], brimTop, 'cloth'),
    b.box([R - 0.2, BR[0], R], [C, BR[1], C], brimTop, 'cloth'),
  ];
  saveModel(save, 'atelier-fedora', b, {
    attach: 'head', helmet_fit: true,
    bones: [
      { name: 'hat', pivot: [0, -8, 0], rotation: [-4, 0, -5], cubes: crown },
      // Snapped down in front, up behind; the sides level.
      { name: 'brim', parent: 'hat', pivot: [0, Y0 + 0.3, 0], cubes: [...sideR, ...sideL] },
      { name: 'brim_front', parent: 'hat', pivot: [0, Y0 + 0.3, -R], rotation: [18, 0, 0], cubes: front },
      { name: 'brim_back', parent: 'hat', pivot: [0, Y0 + 0.3, R], rotation: [15, 0, 0], cubes: back },
    ],
  });
}

/* Halcyon ring (Nocturne: silver · ice-topaz). One perfect ring of polished silver that floats
 * behind the shoulders and frames the head from either side: a bold band (its cells overlap, so
 * it reads as one line at ten blocks, never a string of beads) with a raised boss at each quarter,
 * a fine steel line inside it, and the topaz in a silver setting at its crown. The ring turns once
 * in half a minute (the bosses travel round); the setting and the stone stay put. */
function halcyonRing(save) {
  const S = M.silver, E = M.steel;
  const b = builder(64, 64, { frames: TW });
  const C = [0, 0.4, 6.0], R = 6.6, N0 = 36;
  const ring = [];
  for (let k = 0; k < N0; k++) {
    const a = (k / N0) * Math.PI * 2;
    const x = C[0] + Math.cos(a) * R, y = C[1] + Math.sin(a) * R;
    const boss = k % 9 === 4;
    const s = boss ? 1.8 : 1.3, d = boss ? 1.2 : 0.85;
    ring.push(b.box([x - s / 2, y - s / 2, C[2] - d / 2], [x + s / 2, y + s / 2, C[2] + d / 2],
      boss ? metal([S[1], S[2], S[3], S[4], S[4]]) : metal(S), 'metal'));
  }
  // The fine inner line, steel: a second edge that makes the band read as a worked ring.
  const inner = [];
  // Cells overlap (chord under the cell's width), so the line is continuous, not beaded.
  const R2 = 5.3, N2 = 40;
  for (let k = 0; k < N2; k++) {
    const a = (k / N2) * Math.PI * 2;
    const x = C[0] + Math.cos(a) * R2, y = C[1] + Math.sin(a) * R2;
    inner.push(b.box([x - 0.45, y - 0.45, C[2] - 0.3], [x + 0.45, y + 0.45, C[2] + 0.3], metal(E), 'metal'));
  }
  // The setting at the crown of the ring (-y is up), the stone facing back and, smaller, forward.
  const top = C[1] - R;
  const setting = b.box([-1.4, top - 1.4, C[2] - 0.75], [1.4, top + 1.4, C[2] + 0.75], (face, i, j, w, h) =>
    (face === 'south' || face === 'north' ? (i === 0 || j === 0 ? S[4] : i === w - 1 || j === h - 1 ? S[1] : S[2]) : metal(S)(face, i, j, w, h)), 'metal');
  const gem = b.gem([-0.85, top - 0.85, C[2] + 0.75], [0.85, top + 0.85, C[2] + 1.45], J.topaz);
  const gemF = b.gem([-0.6, top - 0.6, C[2] - 1.25], [0.6, top + 0.6, C[2] - 0.75], J.topaz, 4);
  saveModel(save, 'halcyon-ring', b, {
    attach: 'body', procedural: 'float',
    bones: [
      { name: 'frame', pivot: C, cubes: [setting, gem, gemF, ...inner] },
      { name: 'ring', parent: 'frame', pivot: C, cubes: ring },
    ],
    animations: { idle: { length: 30, loop: true, bones: { ring: { rotation: [[0, [0, 0, 0], 'linear'], [30, [0, 0, 360], 'linear']] } } } },
  });
}

/* Vesper sheath (Regent: gold · ember-garnet). A sword in an ivory lacquer scabbard worn across
 * the back, the hilt over the right shoulder: gold locket, band and chape, a gold crossguard, a
 * grip wrapped in charcoal and bone, a garnet in the pommel; a bone leather baldric across the
 * chest. */
function vesperSheath(save) {
  const I = N.ivory, C = N.charcoal, B = N.bone, G = M.gold;
  const b = builder(64, 64, { frames: TW });
  const lacquer = (face, i, j, w, h) => {
    if (face === 'up') return I[4];
    if (face === 'down') return I[1];
    if (face === 'east' || face === 'west') return I[2];
    return i === 0 ? I[4] : i === w - 1 ? I[2] : I[3];
  };
  const wrap = (face, i, j) => ((i + j) % 3 === 0 ? B[3] : C[3]);
  // Authored at a quarter over life size along the blade, so the line reads at ten blocks.
  const K = 1.25, X = 1.15;
  const sz = (f, t) => [[f[0] * X, f[1] * K, f[2]], [t[0] * X, t[1] * K, t[2]]];
  const box = (f, t, look, m) => b.box(...sz(f, t), look, m);
  const scab = [
    box([-0.9, -4.2, 2.5], [0.9, 10.0, 3.4], lacquer, 'matte'),
    box([-1.05, -4.4, 2.4], [1.05, -2.8, 3.5], metal(G), 'metal'),
    box([-1.0, 3.0, 2.45], [1.0, 3.7, 3.45], metal(G), 'metal'),
    box([-0.95, 9.0, 2.45], [0.95, 10.4, 3.45], metal(G), 'metal'),
    box([-0.5, 10.4, 2.6], [0.5, 11.1, 3.3], metal(G), 'metal'),
  ];
  const hilt = [
    box([-2.5, -5.1, 2.5], [2.5, -4.4, 3.4], metal(G), 'metal'),
    box([-0.55, -8.8, 2.6], [0.55, -5.1, 3.3], wrap, 'cloth'),
    box([-0.85, -9.9, 2.5], [0.85, -8.8, 3.4], metal(G), 'metal'),
    b.gem(...sz([-0.5, -9.6, 3.4], [0.5, -9.0, 4.0]), J.garnet),
  ];
  const strap = (face, i, j, w, h) => (face === 'north' || face === 'south' ? (i === 0 ? B[2] : B[1]) : B[0]);
  saveModel(save, 'vesper-sheath', b, {
    attach: 'body',
    bones: [
      { name: 'sheath', pivot: [0, 5.5, 2.9], rotation: [0, 0, -36], cubes: [...scab, ...hilt] },
      { name: 'baldric', pivot: [0, 6.0, 0], rotation: [0, 0, -34], cubes: [
        b.box([-0.6, -7.6, -2.4], [0.6, 7.6, -2.1], strap, 'cloth'),
        b.box([-0.6, -7.6, 2.1], [0.6, 7.6, 2.4], strap, 'cloth'),
        b.box([-0.8, -0.6, -2.55], [0.8, 0.6, -2.35], metal(G), 'metal'),
      ] },
    ],
  });
}

/* Sovereign crown (Regent: gold · ember-garnet). Worked gold on an ermine band: a band with a lit
 * lip and an engraved dot every third texel, eight points — tall at the front, back and sides,
 * shorter at the corners — each tipped with a gold ball, a charcoal velvet cap inside, and one
 * garnet in a gold bezel at the front. */
function sovereignCrown(save) {
  const G = M.gold, I = N.ivory, C = N.charcoal;
  const b = builder(64, 64, { frames: TW });
  const Y1 = -7.0, Y0 = -9.2, R = 4.7;
  const band = (face, i, j, w, h) => {
    if (face === 'up') return G[4];
    if (face === 'down') return G[1];
    if (j === 0) return G[4];
    if (j === h - 1) return G[1];
    return i % 3 === 1 ? G[2] : G[3];
  };
  const ermine = (face, i, j, w, h) => {
    if (face === 'down') return I[1];
    if (face === 'up') return I[4];
    return (i % 4 === 1 && j === 0) || (i % 4 === 3 && j === h - 1) ? C[1] : j === 0 ? I[4] : I[3];
  };
  const cubes = [
    b.box([-R, Y0, -R], [R, Y1, -R + 0.6], band, 'metal'),
    b.box([-R, Y0, R - 0.6], [R, Y1, R], band, 'metal'),
    b.box([R - 0.6, Y0, -R + 0.6], [R, Y1, R - 0.6], band, 'metal'),
    b.box([-R, Y0, -R + 0.6], [-R + 0.6, Y1, R - 0.6], band, 'metal'),
    b.box([-R - 0.2, Y1, -R - 0.2], [R + 0.2, Y1 + 0.9, R + 0.2], ermine, 'cloth'),
    b.box([-4.0, Y0 - 1.1, -4.0], [4.0, Y0, 4.0], block([C[0], C[1], C[2], C[3], C[3]], { hi: false }), 'cloth'),
  ];
  const e = R - 0.3;
  // Each point tapers in two steps to a ball: a broad foot, a narrow spire.
  for (const [x, z, h] of [[0, -e, 3.8], [0, e, 3.2], [-e, 0, 3.2], [e, 0, 3.2], [-e, -e, 2.0], [e, -e, 2.0], [-e, e, 2.0], [e, e, 2.0]]) {
    const s = h > 3 ? 0.75 : 0.6, foot = h * 0.45;
    cubes.push(b.box([x - s, Y0 - foot, z - s], [x + s, Y0, z + s], metal(G), 'metal'));
    cubes.push(b.box([x - s * 0.7, Y0 - h, z - s * 0.7], [x + s * 0.7, Y0 - foot, z + s * 0.7], metal(G), 'metal'));
    cubes.push(b.box([x - 0.55, Y0 - h - 1.0, z - 0.55], [x + 0.55, Y0 - h, z + 0.55], (face) => (face === 'down' ? G[2] : G[4]), 'metal'));
  }
  cubes.push(b.box([-1.2, Y0 + 0.1, -R - 0.25], [1.2, Y1 - 0.1, -R], metal(G), 'metal'));
  cubes.push(b.gem([-0.75, Y0 + 0.35, -R - 0.7], [0.75, Y1 - 0.35, -R - 0.25], J.garnet));
  saveModel(save, 'sovereign-crown', b, {
    attach: 'head', helmet_fit: true,
    bones: [{ name: 'crown', pivot: [0, -8, 0], rotation: [-4, 0, 3], cubes }],
  });
}

/* Cloister hood (Meridian: brass · jade). Bottle-green wool, cut to drape rather than sit: a
 * domed crown that steps in toward the top, a soft beak over the brow, the opening rounded into an
 * arch and turned back to show the cream lining (the set's cream, as on the Meridian cape's yoke)
 * edged in brass, so the face is framed in light by day and the hood keeps a light value at
 * night; walls that flare like a bell as they fall, a roomy back that swells behind the head and
 * tapers to the nape, folds where the cloth falls free, and a short mantle that falls onto the
 * shoulders and closes at the throat with the jade in a brass clasp. Deeper inside, the wool is
 * in shadow (the shader's contact occlusion deepens it round the face). */
function cloisterHood(save) {
  const G = FAB.bottle, L = N.bone, R = M.brass;
  const b = builder(128, 128, { frames: TW });
  // The folds, in model pixels: down the side walls at these z, down the back at these x. Placed
  // in model space rather than per face, so a fold runs on from one step of a wall to the next.
  const FOLD_Z = [0.6, 3.6], FOLD_X = [-1.9, 1.4];
  /**
   * A wool cuboid: lit tops, dark undersides, walls at the base with a lit first row; `inside`
   * faces (toward the head) in the hood's shadow; with `folds`, a shadow texel down the wall
   * wherever a fold line crosses it, a lit texel on its upper side, starting a row down.
   * `rim`: a rolled edge, a step lighter; `cream`: the lining turned back round the opening.
   */
  const wool = (from, to, inside = [], { folds = false, rim = false, cream = false } = {}) => b.box(from, to, (face, i, j, w, h) => {
    if (cream) {
      if (face === 'up') return L[4];
      if (face === 'down' || inside.includes(face)) return L[1];
      return j === 0 ? L[4] : L[3];
    }
    if (inside.includes(face)) return j === 0 ? G[1] : G[0];
    if (face === 'up') return rim ? G[4] : G[3];
    if (face === 'down') return G[1];
    // Folds only where the cloth falls free, below the temples: smooth where it lies over the head.
    const y = from[1] + j + 0.5;
    if (folds && y > -4.6) {
      let at = null, lines = null;
      if (face === 'east') { at = from[2] + i + 0.5; lines = FOLD_Z; }
      if (face === 'west') { at = to[2] - i - 0.5; lines = FOLD_Z; }
      if (face === 'south') { at = to[0] - i - 0.5; lines = FOLD_X; }
      if (lines) {
        for (const l of lines) {
          if (Math.abs(at - l) < 0.5) return G[1];
          // The lit side: toward the light, the upper left as seen.
          const lit = face === 'west' || face === 'south' ? at - l : l - at;
          if (lit >= 0.5 && lit < 1.5 && y > -2.6) return G[3];
        }
      }
    }
    if (j === 0 && rim) return G[4];
    return rim ? G[3] : G[2];
  }, 'cloth');
  const fold = { folds: true };
  const rim = { rim: true };
  const cream = { cream: true };
  // The walls hang from the crown and swing out a little as they fall: a bell from the front.
  const wallR = [
    wool([-5.25, -7.7, -4.45], [-4.6, -3.6, 5.6], ['east'], fold),
    wool([-5.5, -3.6, -4.35], [-4.65, -0.3, 5.6], ['east', 'up'], fold),
  ];
  const right = [
    // The rim of the opening: a rolled edge, lit, standing just proud of the face.
    wool([-5.35, -7.75, -4.95], [-3.95, -0.3, -4.4], ['east'], cream),
    // The arch: the opening's top corners rounded in, two steps, over the brow's corners.
    wool([-3.95, -7.45, -4.9], [-2.6, -6.65, -4.4], ['down'], cream),
    wool([-3.95, -6.65, -4.85], [-3.25, -5.9, -4.4], ['down', 'east'], cream),
    // Brass along the opening's edge, following the arch.
    b.box([-4.25, -5.95, -5.05], [-3.9, -0.3, -4.9], metal(R), 'metal'),
    b.box([-3.3, -6.7, -5.0], [-2.55, -6.45, -4.85], metal(R), 'metal'),
    b.box([-3.95, -6.45, -5.0], [-3.2, -5.9, -4.85], metal(R), 'metal'),
  ];
  const centre = [
    // The crown, domed: three layers, each stepped in.
    wool([-4.75, -8.8, -4.45], [4.75, -7.7, 5.2], ['down']),
    wool([-3.9, -9.45, -3.8], [3.9, -8.8, 4.9], ['down']),
    wool([-2.6, -9.85, -2.7], [2.6, -9.45, 3.9], ['down']),
    // The back: the room behind the head, a curve in steps — rounded over from the crown, full
    // at the middle, tapering in to the nape — each step narrower toward the ends, so it is round
    // from behind as well as from the side.
    wool([-4.6, -7.7, 4.55], [4.6, -0.3, 5.3], ['north'], fold),
    wool([-3.4, -8.9, 4.9], [3.4, -8.4, 6.0], ['north', 'down']),
    wool([-3.9, -8.4, 5.2], [3.9, -7.2, 6.8], ['north']),
    wool([-4.1, -7.2, 5.2], [4.1, -5.0, 7.1], ['north'], fold),
    wool([-3.9, -5.0, 5.2], [3.9, -3.4, 6.7], ['north', 'up'], fold),
    wool([-3.6, -3.4, 5.2], [3.6, -1.8, 6.2], ['north', 'up'], fold),
    wool([-3.3, -1.8, 5.2], [3.3, -0.3, 5.7], ['north', 'up'], fold),
    // The brow, rolled, and the beak: a soft point over the face.
    wool([-4.6, -8.75, -5.0], [4.6, -7.45, -4.4], ['down'], cream),
    wool([-1.5, -9.0, -5.6], [1.5, -7.8, -5.0], ['down'], rim),
    wool([-0.65, -9.2, -6.05], [0.65, -8.15, -5.6], ['down'], rim),
    b.box([-2.6, -7.5, -5.0], [2.6, -7.3, -4.85], metal(R), 'metal'),
    // The mantle: under the chin, and behind the neck down onto the upper back.
    wool([-4.4, -0.45, -4.8], [4.4, 1.0, -2.6], ['up']),
    wool([-4.5, -0.3, 3.4], [4.5, 1.6, 5.5], ['up'], fold),
    wool([-3.6, 1.6, 2.4], [3.6, 3.0, 4.4], ['up'], fold),
    // The clasp at the throat and its jade.
    b.box([-1.0, -0.25, -4.95], [1.0, 1.3, -4.8], metal(R), 'metal'),
    b.gem([-0.5, 0.15, -5.3], [0.5, 0.95, -4.95], J.jade),
  ];
  // Where the mantle falls onto the shoulder, sloping away from the neck.
  const shoulderR = [wool([-7.3, -0.6, -3.4], [-4.3, 0.5, 4.8], ['down'])];
  const sides = [
    { name: 'side_r', parent: 'hood', pivot: [0, -4, 0], cubes: right },
    { name: 'wall_r', parent: 'hood', pivot: [-4.9, -7.7, 0], rotation: [0, 0, 6], cubes: wallR },
    { name: 'mantle_r', parent: 'hood', pivot: [-4.4, -0.2, 0.6], rotation: [0, 0, -15], cubes: shoulderR },
  ];
  saveModel(save, 'cloister-hood', b, {
    attach: 'head', helmet_fit: true,
    bones: [
      { name: 'hood', pivot: [0, -4, 0], cubes: centre },
      ...sides, ...mirror(sides),
    ],
  });
}
/* ================================================================================= halos */

/* Aureole (Regent: gold · pearl). A fine band of polished gold, a raised boss every sixth cell,
 * and a single pearl in a gold bezel at the front. */
function aureole(save) {
  const G = M.gold;
  const b = builder(64, 64, { frames: TW });
  const Y = -12.6, R = 4.5, NC = 26;
  const cells = [];
  for (let k = 0; k < NC; k++) {
    const a = (k / NC) * Math.PI * 2 + Math.PI / 2;
    const x = Math.cos(a) * R, z = Math.sin(a) * R;
    if (k === Math.round(NC / 2)) continue; // the bezel takes the front cell
    const boss = k % 6 === 3;
    const s = boss ? 1.05 : 0.85, h = boss ? 0.75 : 0.5;
    cells.push(b.box([x - s / 2, Y - h / 2, z - s / 2], [x + s / 2, Y + h / 2, z + s / 2], metal(G), 'metal'));
  }
  // Front is -z.
  cells.push(b.box([-0.8, Y - 0.6, -R - 0.6], [0.8, Y + 0.6, -R + 0.6], metal(G), 'metal'));
  cells.push(b.gem([-0.55, Y - 0.45, -R - 1.05], [0.55, Y + 0.45, -R - 0.6], J.pearl));
  saveModel(save, 'aureole-halo', b, {
    attach: 'halo', procedural: 'halo',
    bones: [
      { name: 'tilt', pivot: [0, Y, 0], rotation: [14, 0, 0] },
      { name: 'ring', parent: 'tilt', pivot: [0, Y, 0], cubes: cells },
    ],
    animations: { idle: { length: 6, loop: true, bones: { ring: { position: [[0, [0, 0, 0], 'sine'], [3, [0, -0.35, 0], 'sine'], [6, [0, 0, 0], 'sine']] } } } },
  });
}

/* Lumen (Atelier: silver · amethyst). Two fine silver bands crossed like an armillary — the
 * outer one still, the amethyst at its front, the inner one tilted the other way and turning
 * slowly inside it, so the halo is always gently changing shape. */
function lumen(save) {
  const S = M.silver;
  const b = builder(64, 64, { frames: TW });
  const Y = -12.8;
  const ringOf = (R, NC, s, h, skipFront) => {
    const out = [];
    for (let k = 0; k < NC; k++) {
      const a = (k / NC) * Math.PI * 2 + Math.PI / 2;
      if (skipFront && k === Math.round(NC / 2)) continue;
      const x = Math.cos(a) * R, z = Math.sin(a) * R;
      out.push(b.box([x - s / 2, Y - h / 2, z - s / 2], [x + s / 2, Y + h / 2, z + s / 2], metal(S), 'metal'));
    }
    return out;
  };
  const outer = ringOf(4.6, 26, 0.8, 0.45, true);
  outer.push(b.box([-0.75, Y - 0.55, -4.6 - 0.55], [0.75, Y + 0.55, -4.6 + 0.55], metal(S), 'metal'));
  outer.push(b.gem([-0.5, Y - 0.4, -4.6 - 1.0], [0.5, Y + 0.4, -4.6 - 0.55], J.amethyst));
  const inner = ringOf(3.7, 20, 0.6, 0.35, false);
  saveModel(save, 'lumen-halo', b, {
    attach: 'halo', procedural: 'halo',
    bones: [
      { name: 'tilt', pivot: [0, Y, 0], rotation: [14, 0, 0] },
      { name: 'outer', parent: 'tilt', pivot: [0, Y, 0], cubes: outer },
      { name: 'gimbal', parent: 'tilt', pivot: [0, Y, 0], rotation: [0, 0, 0] },
      { name: 'inner', parent: 'gimbal', pivot: [0, Y, 0], rotation: [-26, 0, 0], cubes: inner },
    ],
    animations: { idle: { length: 24, loop: true, bones: { gimbal: { rotation: [[0, [0, 0, 0], 'linear'], [24, [0, 360, 0], 'linear']] } } } },
  });
}

/* ================================================================================= masks */

/* Porcelain mask (Meridian: brass · jade). A full face of ivory porcelain in plates — brow,
 * cheeks, a proud nose bridge, a closed mouth, the chin — with the eyes cut out, a brass hairline
 * along the brow and one jade tear under the left eye. */
function porcelainMask(save) {
  const I = N.ivory, T = N.taupe, R = M.brass;
  const b = builder(64, 64, { frames: TW });
  const Z0 = -4.95, Z1 = -4.55;
  const porcelain = (tone = 0, mouth = false) => (face, i, j, w, h) => {
    if (face === 'up') return I[4];
    if (face === 'down') return I[1];
    if (face !== 'north') return I[2];
    if (mouth && j === h - 2 && i >= Math.floor(w / 2) - 1 && i <= Math.floor(w / 2) + 1) return T[3];
    if (j === 0 && tone > 0) return I[4];
    if (j === h - 1) return I[2];
    return tone > 0 ? I[4] : I[3];
  };
  const cubes = [
    b.box([-3.9, -7.4, Z0 - 0.1], [3.9, -4.2, Z1], porcelain(1), 'matte'),
    b.box([-3.8, -4.2, Z0], [-3.1, -2.9, Z1], porcelain(), 'matte'),
    b.box([3.1, -4.2, Z0], [3.8, -2.9, Z1], porcelain(), 'matte'),
    b.box([-0.9, -4.2, Z0 - 0.15], [0.9, -2.0, Z1], porcelain(1), 'matte'),
    b.box([-3.6, -2.9, Z0], [3.6, -0.9, Z1], porcelain(0, true), 'matte'),
    b.box([-2.4, -0.9, Z0 + 0.05], [2.4, -0.1, Z1], porcelain(), 'matte'),
    b.box([-3.95, -7.7, Z0 - 0.15], [3.95, -7.35, Z1], metal(R), 'metal'),
    b.gem([1.7, -2.75, Z0 - 0.35], [2.5, -1.95, Z0], J.jade),
  ];
  saveModel(save, 'porcelain-mask', b, {
    attach: 'head', helmet_fit: true,
    bones: [{ name: 'mask', pivot: [0, -4, 0], cubes }],
  });
}

/* Aegis visor (Nocturne: steel · ice-topaz). A band of brushed steel across the eyes that wraps
 * to the temples, a lit ridge along its top, rivets, and a slit of ice-topaz glass with a glint
 * that sweeps across it. */
function aegisVisor(save) {
  const E = M.steel, Z = J.topaz;
  const F = 10;
  const b = builder(64, 64, { frames: F });
  const cubes = [
    b.box([-4.75, -5.0, -5.0], [4.75, -2.6, -4.6], (face, i, j, w, h) => (face === 'north' ? (j === 0 ? E[3] : j === h - 1 ? E[1] : E[2]) : metal(E)(face, i, j, w, h)), 'metal'),
    b.box([4.6, -5.0, -4.6], [5.0, -2.6, -1.0], metal(E), 'metal'),
    b.box([-5.0, -5.0, -4.6], [-4.6, -2.6, -1.0], metal(E), 'metal'),
    b.box([-4.85, -5.35, -5.1], [4.85, -5.0, -4.5], (face) => (face === 'up' ? E[4] : E[3]), 'metal'),
    // Rivets at the slit's ends.
    b.box([-4.3, -3.9, -5.15], [-3.7, -3.3, -5.0], () => E[4], 'metal'),
    b.box([3.7, -3.9, -5.15], [4.3, -3.3, -5.0], () => E[4], 'metal'),
    // The slit: topaz glass, a glint sweeping across it.
    b.box([-3.5, -4.05, -5.12], [3.5, -3.25, -4.98], (face, i, j, w, h) => (face === 'north' ? (j === 0 ? Z[3] : Z[2]) : Z[1]), 'glass',
      (face, i, j, w, h, f) => {
        if (face !== 'north') return null;
        if (i === f) return [Z[4], 255];
        if (i === f - 1 || i === f + 1) return [Z[3], 190];
        return [Z[2], 110];
      }),
  ];
  saveModel(save, 'aegis-visor', b, {
    attach: 'head', helmet_fit: true, frames: { count: F, fps: 6 },
    bones: [{ name: 'visor', pivot: [0, -4, -4.7], cubes }],
  });
}

export function paintModels(save) {
  bladeWings(save);
  halcyonRing(save);
  vesperSheath(save);
  atelierFedora(save);
  sovereignCrown(save);
  cloisterHood(save);
  aureole(save);
  lumen(save);
  porcelainMask(save);
  aegisVisor(save);
}
