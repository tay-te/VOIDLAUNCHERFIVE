/**
 * The six capes of the VOID luxe line (docs/cosmetics.md, "The collection"), at the vanilla
 * 64 x 32: a 10 x 16 face, one texel per vanilla pixel, the skin's density.
 *
 * Each is a tailored garment drawn as a pixel map: a yoke across the shoulders, a clasp or brooch
 * holding the jewel, a contrasting lining, metal-thread trim, one motif, and a cut hem that is the
 * cape's silhouette (bias, centre point, scallop, swallowtail, rounded, straight and weighted).
 * Maps are rows of characters through a legend: `c` the colour, `m` the material (default the
 * cape's cloth), `g` an emissive [colour, alpha] or a function of the frame. `.` is a cut: no cloth.
 */

import { CW, CH, FAB, J, M, N, cape, saveCape, stoneLight } from './collection.mjs';

/**
 * Builds a cape from an outer map and a lining rule. `legend[ch]` is { c, m?, g?(f) }. The hem
 * cut is read off the map: wherever a column's texel is `.`, the lining, the side strip and the
 * bottom strip are cut there too, so the cloth has one silhouette from every side.
 */
function mapped(id, { rows, legend, frames = 1, lining, edge, top, meta, tint, glowAt, vary }) {
  if (rows.length !== CH || rows.some((r) => r.length !== CW)) throw new Error(`${id}: map must be ${CW} x ${CH}`);
  const at = (x, y) => rows[y][x];
  const cut = (x, y) => at(x, y) === '.';
  const L = (ch) => {
    const l = legend[ch];
    if (!l) throw new Error(`${id}: no legend for '${ch}'`);
    return l;
  };
  const c = cape({
    frames,
    outer: (x, y) => (cut(x, y) ? null : (vary ? vary(x, y, at(x, y)) : null) ?? L(at(x, y)).c),
    mat: (x, y) => (cut(x, y) ? null : L(at(x, y)).m ?? null),
    glow: (x, y, f) => {
      if (cut(x, y)) return null;
      const g = L(at(x, y)).g;
      const own = typeof g === 'function' ? g(f, x, y) : g ?? null;
      return own ?? (glowAt ? glowAt(x, y, f) : null);
    },
    lining: (x, y) => (cut(x, y) ? null : lining(x, y)),
    // Column 0's strip is the left side seen from behind, the last column's the right.
    edge: () => null,
    top: (x) => top(x),
    // The bottom strip runs under the last row: only where that row is cloth.
    bottom: (x) => (cut(x, CH - 1) ? null : edge(CH - 1, x)),
    tint: tint ? (x, y) => (cut(x, y) ? null : tint(x, y)) : null,
  });
  // Side strips: the column's own colour per row, cut with it.
  for (let f = 0; f < frames; f++) {
    for (let y = 0; y < CH; y++) {
      for (const [sx, col] of [[0, 0], [11, CW - 1]]) {
        if (cut(col, y)) continue;
        const e = edge(y, col);
        c.alb.set(sx, f * 32 + 1 + y, e);
      }
    }
  }
  saveCape(save_, id, c, meta);
}

let save_;

/** The stone's legend entries: sparkle, light, base, deep, glass, lit from within, twinkling. */
function stone(jr, frames, phase = 0) {
  return {
    sparkle: { c: jr[4], m: 'glass', g: (f) => [jr[4], stoneLight(f, frames, phase).spark] },
    light: { c: jr[3], m: 'glass', g: (f) => [jr[3], stoneLight(f, frames, phase).base] },
    base: { c: jr[2], m: 'glass', g: (f) => [jr[2], stoneLight(f, frames, phase).base] },
    deep: { c: jr[1], m: 'glass' },
  };
}

const metal = (r, k) => ({ c: r[k], m: 'metal' });

/* ============================================================================== Atelier */

/* Atelier (silver · amethyst): aubergine wool with an ivory V-yoke piped in polished silver, the
 * amethyst set as a brooch where the V closes, and a bias-cut hem — short on the left, long on the
 * right — finished in silver. A dove-grey silk lining, so the dark plum flashes light as it moves. */
function atelier() {
  const F = 8;
  const st = stone(J.amethyst, F);
  const I = N.ivory, T = FAB.aubergine, S = M.silver;
  saveIt('atelier-cape', {
    frames: F,
    rows: [
      'IIIIIIIIII',
      'iiiiiiiiii',
      'siiiiiiiis',
      'tsiiiiiist',
      'TtsiiiistT',
      'TTtsiiqtTT',
      'TTTsAaqtTT',
      'TTTqadqtTT',
      'TTTtqqtTTT',
      'TTTTttTTTT',
      'TTTTTTTTTT',
      'TTTTTTTTTT',
      'hhhTTTTTTT',
      '...hhhTTTT',
      '......hhhT',
      '.........h',
    ],
    legend: {
      I: { c: I[4] }, i: { c: I[3] }, j: { c: I[2] },
      s: metal(S, 3), q: metal(S, 1), h: metal(S, 2),
      t: { c: T[2] }, T: { c: T[3] },
      A: st.sparkle, a: st.base, d: st.deep,
    },
    lining: (x, y) => (y === 0 ? FAB.dove[4] : FAB.dove[3]),
    edge: (y, col) => (y <= 2 ? I[2] : y >= 12 ? S[1] : T[2]),
    top: () => I[3],
    meta: { stiffness: 0.95, weight: 1.3, frames: { count: F, fps: 5 } },
  });
}

function saveIt(id, spec) {
  mapped(id, spec);
}

/* =============================================================================== Regent */

/* Regent (gold · ember-garnet): charcoal wool framed in gold — a gold collar, a braided yoke
 * line, gold down both edges — the VOID ring as a gold crest with the garnet at its heart, cut to
 * a point and finished in gold. An ivory lining flashes when it moves. */
function regent() {
  const F = 8;
  const st = stone(J.garnet, F);
  const G = M.gold, C = N.charcoal;
  saveIt('regent-cape', {
    frames: F,
    rows: [
      'GGGGGGGGGG',
      'GKKKKKKKKo',
      'GKKKKKKKKo',
      'GgGgGgGgGo',
      'GccccccccO',
      'GCCGGGGCCo',
      'GCGccccoCo',
      'GCGcAacoCo',
      'GCGcadcoCo',
      'GCGccccoCo',
      'GCCooooCCo',
      'GCCCCCCCCo',
      'gCCCCCCCCg',
      '.gCCCCCCg.',
      '..ggCCgg..',
      '....gg....',
    ],
    legend: {
      G: metal(G, 3), g: metal(G, 2), o: metal(G, 1), O: metal(G, 1),
      K: { c: C[3] }, C: { c: C[2] }, c: { c: C[1] },
      A: st.sparkle, a: st.base, d: st.deep,
    },
    lining: (x, y) => (y === 0 ? N.ivory[4] : N.ivory[3]),
    edge: () => G[2],
    top: () => G[3],
    meta: { stiffness: 0.94, weight: 1.35, frames: { count: F, fps: 5 } },
  });
}

/* ============================================================================= Nocturne */

/* Nocturne (silver · ice-topaz): midnight-navy wool under a deeper yoke piped in silver,
 * Cassiopeia stitched in silver thread, and a glint of light that runs along the thread from star
 * to star to the one ice-topaz; silver scallops at the hem. An ivory lining. */
function nocturne() {
  const F = 12;
  // The glint's path along the thread, ending on the topaz.
  const PATH = [[1, 7], [2, 8], [3, 9], [4, 8], [5, 7], [6, 8], [7, 9], [8, 8], [8, 7]];
  const T = FAB.navy, S = M.silver, Z = J.topaz;
  saveIt('nocturne-cape', {
    frames: F,
    rows: [
      'NNNNNNNNNN',
      'YYYYYYYYYY',
      'YYYYYYYYYY',
      'sssssssssq',
      'ffffffffff',
      'FFFFFFFFFF',
      'FFFFFFFFZF',
      'FxFFFxFFtF',
      'FFtFtFtFtF',
      'FFFxFFFxFF',
      'FFFFFFFFFF',
      'FFFFFFyFFF',
      'FFyFFFFFFF',
      'FFFFFFFFFF',
      'hhhhhhhhhh',
      '.hh.hh.hh.',
    ],
    legend: {
      N: { c: T[3] }, Y: { c: T[1] }, F: { c: T[2] }, f: { c: T[1] },
      s: metal(S, 3), q: metal(S, 1), h: metal(S, 2),
      x: metal(S, 4), t: metal(S, 2), y: metal(S, 2),
      // The star: one texel of topaz, lit from within, flaring when the glint arrives.
      Z: { c: Z[3], m: 'glass', g: (f) => [Z[4], f === PATH.length ? 255 : f === PATH.length + 1 ? 215 : 165] },
    },
    glowAt: (x, y, f) => {
      const k = PATH.findIndex(([px, py]) => px === x && py === y);
      if (k >= 0 && k === f) return [S[4], 235];
      if (k >= 0 && k === f - 1) return [S[3], 90];
      return null;
    },
    lining: (x, y) => (y === 0 ? N.ivory[4] : N.ivory[3]),
    edge: (y) => (y >= 14 ? S[1] : T[1]),
    top: () => T[3],
    meta: { stiffness: 0.93, weight: 1.2, frames: { count: F, fps: 6 } },
  });
}

/* ============================================================================= Meridian */

/* Meridian (brass · jade): bottle-green wool cut in two tones, a lighter half and a deeper half
 * meeting at a brass seam, a cream yoke with a brass yoke line, the jade set at the seam's head,
 * cut to a swallowtail and hemmed in brass. A cream lining. */
function meridian() {
  const F = 8;
  const st = stone(J.jade, F);
  const G = FAB.bottle, B = N.bone, R = M.brass;
  saveIt('meridian-cape', {
    frames: F,
    rows: [
      'BBBBBBBBBB',
      'bbbbMMbbbb',
      'bbbMAambbb',
      'MMMMadmmmm',
      'iiiiMmtttt',
      'IIIIMmTTTT',
      'IIIIMmTTTT',
      'IIIIMmTTTT',
      'IIIIMmTTTT',
      'IIIIMmTTTT',
      'IIIIMmTTTT',
      'IIIIMmTTTT',
      'IIIIMmTTTT',
      'IIIIhhTTTT',
      'IIIh..hTTT',
      'hhh....hhh',
    ],
    legend: {
      B: { c: B[4] }, b: { c: B[3] },
      I: { c: G[3] }, i: { c: G[2] }, T: { c: G[2] }, t: { c: G[1] },
      M: metal(R, 3), m: metal(R, 2), h: metal(R, 2),
      A: st.sparkle, a: st.base, d: st.deep,
    },
    lining: (x, y) => (y === 0 ? B[3] : B[2]),
    edge: (y, col) => (y <= 2 ? B[2] : y === 3 ? R[2] : col === 0 ? G[2] : G[1]),
    top: () => B[3],
    meta: { stiffness: 0.92, weight: 1.15, frames: { count: F, fps: 5 } },
  });
}

/* ============================================================================== Bespoke */

/* Bespoke (silver · pearl): wool in the colour you choose from the house's cloths (the field is
 * tintable; oxblood by default, `default_tint` in the catalog is its base), an ivory yoke piped in
 * silver with a pearl at the clasp, a silver hem line, rounded corners. An ivory lining. */
function bespoke() {
  const F = 8;
  const st = stone(J.pearl, F);
  const I = N.ivory, T = FAB.oxblood, S = M.silver;
  const rows = [
    'IIIIIIIIII',
    'iiiiiiiiii',
    'iiiissiiii',
    'ssssPpqqqq',
    'ffffqqffff',
    'FFFFFFFFFF',
    'FFFFFFFFFF',
    'FFFFFFFFFF',
    'FFFFFFFFFF',
    'FFFFFFFFFF',
    'FFFFFFFFFF',
    'FFFFFFFFFF',
    'FFFFFFFFFF',
    'FFFFFFFFFF',
    'hhhhhhhhhh',
    '.FFFFFFFF.',
  ];
  saveIt('bespoke-cape', {
    frames: F,
    rows,
    legend: {
      I: { c: I[4] }, i: { c: I[3] },
      s: metal(S, 3), q: metal(S, 2), h: metal(S, 2),
      F: { c: T[2] }, f: { c: T[1] },
      P: st.sparkle, p: st.base,
    },
    // The tint mask: the field only. White lands the tint exactly — the default tint is the
    // field's base, so the tinted cape is the painted one — and the shadow row under the yoke
    // at about the painted ramp's ratio.
    tint: (x, y) => (rows[y][x] === 'F' ? [255, 255, 255] : rows[y][x] === 'f' ? [178, 178, 178] : null),
    lining: (x, y) => (y === 0 ? I[4] : I[3]),
    edge: (y) => (y <= 2 ? I[2] : y === 3 || y === 14 ? S[1] : T[2]),
    top: () => I[3],
    meta: { stiffness: 0.93, weight: 1.2, frames: { count: F, fps: 5 } },
  });
}

/* =============================================================================== Origin */

/* Origin (steel · pearl glint), every account's: camel herringbone wool, a brushed steel collar
 * band, a weighted hem of deeper camel under a steel line, and one silver thread down the right
 * with a glint of light running down it: the first thread. A cream lining. */
function origin() {
  const F = 16;
  const C = FAB.camel, E = M.steel, S = M.silver;
  const TH = 6; // the thread's column
  saveIt('origin-cape', {
    frames: F,
    rows: [
      'UUUUUUUUUU',
      'FFFFFFFFFF',
      'eeeeeeeeee',
      'ffffffefff',
      'FFFFFFlFFF',
      'FFFFFFlFFF',
      'FFFFFFlFFF',
      'FFFFFFlFFF',
      'FFFFFFlFFF',
      'FFFFFFlFFF',
      'FFFFFFlFFF',
      'FFFFFFlFFF',
      'FFFFFFlFFF',
      'FFFFFFlFFF',
      'EEEEEEEEEE',
      'WWWWWWWWWW',
    ],
    legend: {
      U: { c: C[3] },
      F: { c: C[2] }, f: { c: C[1] },
      e: metal(E, 3), E: metal(E, 2),
      l: metal(S, 3), W: { c: C[1] },
    },
    // Herringbone under 3% value: it reads as cloth up close and is gone at range.
    vary: (x, y, ch) => (ch === 'F' && (((x & 1) ? y + x : y - x + 32) % 4 === 0) ? '#a87a49' : null),
    glowAt: (x, y, f) => {
      if (x !== TH || y < 3 || y > 13) return null;
      const k = y - 3;
      if (k === f) return [J.pearl[4], 230];
      if (k === f - 1) return [J.pearl[3], 80];
      return null;
    },
    lining: (x, y) => (y === 0 ? N.ivory[4] : N.ivory[3]),
    edge: (y) => (y === 2 || y === 14 ? E[1] : y === 15 ? C[0] : C[1]),
    top: () => C[3],
    meta: { stiffness: 0.92, weight: 1.15, frames: { count: F, fps: 6 } },
  });
}

export function paintCapes(save) {
  save_ = save;
  origin();
  bespoke();
  meridian();
  atelier();
  nocturne();
  regent();
}
