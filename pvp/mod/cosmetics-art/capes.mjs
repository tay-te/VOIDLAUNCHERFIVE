/**
 * The six capes, painted at four times the vanilla density: the vanilla cape layout (outer face
 * seen from behind at (1,1) 10x16, lining at (12,1), one-pixel edges round the border) scaled by
 * K = 4, so each face is 40 x 64 texels and a frame is 256 x 128. Frames stack vertically.
 *
 * Painted against the reference board in docs/cosmetics.md. Each cape has:
 *
 *   - one composition, different from the other five (a vertical seam to an emblem; a diagonal
 *     cascade; a radial eclipse; a centred seal in a frame; horizontal bands of landscape; a
 *     framed screen), readable as a silhouette of values at ten blocks;
 *   - three value groups — a quiet dark field, a mid-value motif, and the brightest values only
 *     at the focal point — built from hue-shifted ramps (shadows cool, lights warm);
 *   - a trim in metal thread (material.png marks it metal, so it glints as the cloth turns);
 *   - an animated or emissive accent, slow and sparing.
 *
 * The cloth renderer and the material shader add fold shading, occlusion and sheen, so the art
 * paints form only where the motif has relief (bevels lit from the top left), never shadows of
 * the cloth itself.
 */

import { Canvas, P, mix, shade, hash, bayer, rgb, MAT } from './lib.mjs';

const K = 4;
const FW = 10 * K, FH = 16 * K;
const W = 64 * K, H = 32 * K;

/**
 * Paints one frame: outer face, lining and edge strips. Painters return a colour, [colour,
 * alpha], or null. On frame 0, `mat` (outer face) and `edgeMat` also fill the material mask.
 */
function frame(canvas, oy, { outer, lining, edge, mat, matCanvas, liningMat = null, edgeMat = null }) {
  const first = oy === 0 && matCanvas;
  for (let y = 0; y < FH; y++) for (let x = 0; x < FW; x++) {
    const o = outer ? outer(x, y) : null;
    if (o) put(canvas, K + x, oy + K + y, o);
    const l = lining ? lining(x, y) : null;
    if (l) put(canvas, 12 * K + x, oy + K + y, l);
    if (first) {
      const m = mat ? mat(x, y) : null;
      if (m) matCanvas.set(K + x, K + y, MAT[m]);
      if (liningMat) matCanvas.set(12 * K + x, K + y, MAT[liningMat]);
    }
  }
  if (!edge) return;
  const em = (x, y) => { if (first && edgeMat) matCanvas.set(x, y, MAT[edgeMat]); };
  for (let y = 0; y < FH; y++) {
    for (let k = 0; k < K; k++) {
      const e = edge(k, y);
      for (const x of [k, 11 * K + k, 22 * K + k]) {
        canvas.set(x, oy + K + y, e);
        em(x, K + y);
      }
    }
  }
  for (let x = 0; x < FW; x++) for (let k = 0; k < K; k++) {
    canvas.set(K + x, oy + k, edge(k, 0));
    canvas.set(11 * K + x, oy + k, edge(k, FH - 1));
    em(K + x, k);
    em(11 * K + x, k);
  }
}

function put(canvas, x, y, c) {
  if (Array.isArray(c) && (Array.isArray(c[0]) || typeof c[0] === 'string')) canvas.set(x, y, c[0], c[1] ?? 255);
  else canvas.set(x, y, c);
}

/** A fine twill: lighter threads on a 4-texel diagonal, a hair of noise. Kept under 3% value. */
const weave = (x, y, amp = 0.025) => ((x + y) % 4 === 0 ? amp : (x - y + 400) % 8 === 0 ? -amp * 0.6 : 0)
  + (hash(x, y, 77) < 0.06 ? amp * 0.4 : 0);

/** Quilted satin with a slow sheen and a diamond quilting line every 10 texels. */
function satin(base, line) {
  return (x, y) => {
    const d = (x + y) % 10 === 0 || (x - y + 400) % 10 === 0;
    if (d) return shade(line ?? base, -0.14);
    const sheen = Math.max(0, 1 - Math.abs(((x * 0.6 + y) % 28) - 14) / 6) * 0.06;
    return shade(base, sheen - y * 0.002);
  };
}

/** A woven maker's label under the collar of the lining: cream, with a small violet ring. */
function label(lining) {
  return (x, y) => {
    if (x >= 15 && x <= 24 && y >= 3 && y <= 8) {
      if (y === 3 || y === 8 || x === 15 || x === 24) return shade('#d9d4c7', -0.18);
      const d = Math.hypot(x - 19.5, y - 5.5);
      if (d > 1.2 && d < 2.4) return P.vio3;
      return (x + y) % 2 ? '#e6e1d4' : '#ddd8cb';
    }
    return lining(x, y);
  };
}

/** A lit trim: bright on the face side, dim toward the lining. */
const edgeOf = (light, dark) => (k) => (k === 0 ? shade(light, 0.1) : k === K - 1 ? dark : mix(light, dark, k / (K - 1)));

/** Cell centres on a circle (the VOID ring is sixteen cells). */
function ringCells(cx, cy, r, n = 16) {
  const out = [];
  for (let i = 0; i < n; i++) {
    const a = (i / n) * Math.PI * 2 - Math.PI / 2;
    out.push([Math.round(cx + Math.cos(a) * r), Math.round(cy + Math.sin(a) * r)]);
  }
  return out;
}

/** A bevelled square cell of size s centred at (cx, cy): null or the part of it (x, y) is on. */
function cellAt(x, y, cx, cy, s) {
  const x0 = cx - Math.floor(s / 2), y0 = cy - Math.floor(s / 2);
  const i = x - x0, j = y - y0;
  if (i < 0 || j < 0 || i >= s || j >= s) return null;
  if ((i === 0 || i === s - 1) && (j === 0 || j === s - 1) && s >= 4) return 'corner';
  if (i === 0 || j === 0) return 'lit';
  if (i === s - 1 || j === s - 1) return 'dark';
  if (i === 1 && j === 1) return 'spark';
  return 'body';
}

/** A cell's colour from its ramp [dark, mid, light, spark] by the part it is on. */
function cellShade(k, r) {
  return k === 'lit' ? r[2] : k === 'dark' ? r[0] : k === 'corner' ? mix(r[0], r[1], 0.5) : k === 'spark' ? r[3] : r[1];
}

/** Banded vertical gradient through stops, dithered between bands: pixel-art sky. */
function bands(stops, steps) {
  const at = (t) => {
    for (let i = 1; i < stops.length; i++) {
      if (t <= stops[i][0]) {
        const [a, ca] = stops[i - 1], [b, cb] = stops[i];
        return mix(ca, cb, (t - a) / (b - a));
      }
    }
    return rgb(stops[stops.length - 1][1]);
  };
  return (x, y, t) => at(Math.min(1, Math.floor(t * steps + bayer(x, y) * 0.999) / steps));
}

export function paintCapes(save) {
  /* ================================================== origin: the first thread (free) */
  {
    // Charcoal wool in a herringbone, framed in silver thread; a seam runs from the collar to a
    // small ring embroidered above the hem, where one stitch of light travels round. Quiet,
    // honest, and the one free cape still reads as made.
    const F = 8;
    const alb = new Canvas(W, H * F), emi = new Canvas(W, H * F), mat = new Canvas(W, H);
    const wool = ['#17181b', '#212226', '#2b2c31', '#383a40', '#4a4c54', '#63666f'];
    const silver = ['#4f535c', '#878c98', '#c2c7d1', '#eef1f6'];
    const CX = 20, CY = 45;
    const ring = ringCells(CX, CY, 6.2, 16);
    const onRing = (x, y) => ring.findIndex(([rx, ry]) => x >= rx - 1 && x <= rx && y >= ry - 1 && y <= ry);
    const region = (x, y) => {
      if (y < 5) return 'collar';
      if (y === 5) return 'collar-edge';
      if (y >= 54) return y === 54 ? 'hem-edge' : 'hem';
      if (x === 3 || x === 36) return 'pipe';
      if (x === 4 || x === 35) return 'pipe-shadow';
      return null;
    };
    for (let f = 0; f < F; f++) {
      const lead = (f * 2) % 16;
      frame(alb, f * H, {
        outer: (x, y) => {
          const r = region(x, y);
          if (r === 'collar') return shade(wool[1], weave(x, y, 0.03) + (y === 0 ? 0.06 : 0));
          if (r === 'collar-edge') return x % 3 === 2 ? wool[1] : silver[2];
          if (r === 'hem-edge') return silver[2];
          if (r === 'hem') {
            // A woven chevron band.
            const v = (Math.abs(((x + 400) % 8) - 4) + y) % 4 === 0;
            return v ? wool[2] : shade(wool[1], weave(x, y, 0.02));
          }
          if (r === 'pipe') return y % 2 ? silver[2] : silver[1];
          if (r === 'pipe-shadow') return wool[2];
          const ri = onRing(x, y);
          if (ri >= 0) {
            const [rx, ry] = ring[ri];
            return x === rx - 1 && y === ry - 1 ? silver[3] : silver[2];
          }
          // The embroidery's shadow, a texel down and right.
          if (onRing(x - 1, y - 1) >= 0) return wool[2];
          // The seam: a dark fold line with pale stitches either side, ending at the ring.
          if ((x === 19 || x === 20) && y < CY - 8) return x === 19 ? wool[2] : shade(wool[2], -0.1);
          if ((x === 18 || x === 21) && y % 4 === 1 && y < CY - 8) return silver[1];
          const band = Math.floor(y / 6) % 2;
          const hb = ((band ? x + y : x - y + 400) % 4 === 0) ? 0.05 : 0;
          return shade(wool[3], hb + weave(x, y, 0.015) - (x < 20 ? 0.03 : 0));
        },
        mat: (x, y) => {
          const r = region(x, y);
          if (r === 'collar-edge' && x % 3 !== 2) return 'metal';
          if (r === 'hem-edge' || r === 'pipe') return 'metal';
          if (onRing(x, y) >= 0) return 'metal';
          return null;
        },
        matCanvas: mat,
        edgeMat: 'metal',
        lining: label(satin(rgb('#24252b'))),
        edge: edgeOf(rgb(silver[1]), rgb(wool[0])),
      });
      // The needle of light: one stitch lit, the one before it fading.
      frame(emi, f * H, {
        outer: (x, y) => {
          const ri = onRing(x, y);
          if (ri < 0) return null;
          const d = (ri - lead + 16) % 16;
          return d === 0 ? [silver[3], 150] : d === 15 ? [silver[2], 60] : d === 14 ? [silver[2], 20] : null;
        },
      });
    }
    save('origin-cape', 'texture.png', alb);
    save('origin-cape', 'emissive.png', emi);
    save('origin-cape', 'material.png', mat);
    save('origin-cape', 'cape.json', { frames: { count: F, fps: 4 }, stiffness: 0.9, weight: 1.05 });
  }

  /* ================================================== cell: the grid, dissolving */
  {
    // A field of bevelled violet cells, dense under the collar, breaking up along a diagonal into
    // fragments that drift down off the hem. A wave of light runs down what is left.
    const F = 16;
    const alb = new Canvas(W, H * F), emi = new Canvas(W, H * F), mat = new Canvas(W, H);
    const fieldTop = '#211a3d', fieldBot = '#08070e';
    const cells = [], frags = [];
    for (let row = 0; row < 10; row++) for (let col = 0; col < 7; col++) {
      const cx = 5 + col * 5, cy = 9 + row * 5;
      // The dissolve front runs diagonally, from high on the right to low on the left: whole
      // rows under the collar, a ragged edge, and past it only fragments.
      const e = row + col * 0.8 + (hash(col, row, 11) - 0.5) * 2.4;
      if (e < 6.6) cells.push({ cx, cy, row, col });
      else if (e < 10.5 && hash(col, row, 12) < 0.75) frags.push({ cx, cy: cy + 1, row, col, phase: hash(col, row, 5), speed: 1 + Math.floor(hash(col, row, 6) * 2) });
    }
    const cellRamp = (row) => {
      const k = row / 9;
      return [mix('#2a1f5c', '#140f2a', k), mix('#7a64e0', '#3a2d78', k), mix('#b7a8ff', '#5b49b0', k), mix('#efeaff', '#8d7de0', k)];
    };
    const region = (x, y) => {
      if (y < 4) return 'collar';
      if (y === 4) return 'collar-edge';
      if (y >= 59) return y === 59 ? 'hem-edge' : 'hem';
      if (x === 1 || x === 38) return 'pipe';
      return null;
    };
    for (let f = 0; f < F; f++) {
      const phase = f / F;
      const wave = (row) => Math.max(0, Math.cos((row / 10 - phase) * Math.PI * 2)) ** 5;
      const look = (x, y) => {
        for (const c of cells) {
          const k = cellAt(x, y, c.cx, c.cy, 4);
          if (k) return { c, k };
        }
        return null;
      };
      const frag = (x, y) => {
        for (const g of frags) {
          const dy = Math.floor(((phase * g.speed + g.phase) % 1) * 14);
          const k = cellAt(x, y, g.cx, g.cy + dy, 2);
          if (k && g.cy + dy < 58) return { g, k, dy };
        }
        return null;
      };
      frame(alb, f * H, {
        outer: (x, y) => {
          const r = region(x, y);
          if (r === 'collar') return shade('#1a1530', weave(x, y, 0.03));
          if (r === 'collar-edge' || r === 'hem-edge' || r === 'pipe') return P.vio4;
          if (r === 'hem') return shade(fieldBot, weave(x, y, 0.03));
          const hit = look(x, y);
          if (hit) return cellShade(hit.k, cellRamp(hit.c.row));
          const fr = frag(x, y);
          if (fr) return mix(P.vio3, P.vio1, fr.dy / 14 + 0.2);
          const t = (y - 5) / 54;
          return shade(mix(fieldTop, fieldBot, Math.floor(t * 6 + bayer(x, y) * 0.999) / 6), weave(x, y, 0.02));
        },
        mat: (x, y) => {
          const r = region(x, y);
          return r === 'collar-edge' || r === 'hem-edge' || r === 'pipe' ? 'metal' : null;
        },
        matCanvas: mat,
        edgeMat: 'metal',
        lining: label(satin(rgb('#1a1430'), rgb('#2a2250'))),
        edge: edgeOf(rgb(P.vio4), rgb('#140f26')),
      });
      frame(emi, f * H, {
        outer: (x, y) => {
          const hit = look(x, y);
          if (hit) {
            const { c, k } = hit;
            const tw = hash(c.col, c.row, 300 + Math.floor((f + hash(c.col, c.row, 9) * F) / 4)) < 0.1 ? 0.5 : 0;
            const g = 0.06 + wave(c.row) * 0.7 + tw;
            const a = Math.min(1, g) * (k === 'dark' ? 0.35 : k === 'spark' ? 1 : 0.8) * (1 - c.row * 0.05);
            return a > 0.03 ? [k === 'spark' ? P.vio6 : mix(P.vio4, P.vio6, wave(c.row) * 0.6), Math.round(255 * a)] : null;
          }
          const fr = frag(x, y);
          if (fr) return [P.vio5, Math.round(90 * (1 - fr.dy / 14))];
          return null;
        },
      });
    }
    save('cell-cape', 'texture.png', alb);
    save('cell-cape', 'emissive.png', emi);
    save('cell-cape', 'material.png', mat);
    save('cell-cape', 'cape.json', { frames: { count: F, fps: 8 }, stiffness: 0.92, weight: 1,
      glow: [{ at: [5, 3.5], color: P.vio4, size: 7, strength: 0.2 }, { at: [5, 8], color: P.vio3, size: 6, strength: 0.12 }] });
  }

  /* ================================================== void: the eclipse */
  {
    // A black sun in a corona of sixteen pale cells, a violet aura breathing round it, stars in
    // the dark, a constellation stitched along the hem. One cell of the corona burns at a time
    // and the eclipse's rim brightens behind it as it travels.
    const F = 16;
    const alb = new Canvas(W, H * F), emi = new Canvas(W, H * F), mat = new Canvas(W, H);
    const CX = 20, CY = 27, R = 12.5;
    const ring = ringCells(CX, CY, R, 16);
    const bone = ['#5c5a6b', '#a9a8b8', '#dcdbe6', '#ffffff'];
    const stars = Array.from({ length: 26 }, (_, i) => [Math.floor(hash(i, 1, 5) * 36) + 2, Math.floor(hash(i, 2, 5) * 50) + 5, hash(i, 3, 5)])
      .filter(([x, y]) => Math.hypot(x - CX, y - CY) > R + 5);
    const constellation = [[6, 59], [11, 57], [16, 60], [23, 58], [29, 60], [34, 57]];
    const onLine = (x, y) => {
      for (let k = 1; k < constellation.length; k++) {
        const [ax, ay] = constellation[k - 1], [bx, by] = constellation[k];
        const steps = Math.max(Math.abs(bx - ax), Math.abs(by - ay));
        for (let s = 0; s <= steps; s++) {
          if (Math.round(ax + (bx - ax) * s / steps) === x && Math.round(ay + (by - ay) * s / steps) === y) return true;
        }
      }
      return false;
    };
    const region = (x, y) => {
      if (y < 3) return 'collar';
      if (y === 3) return 'collar-edge';
      if (y >= 55) return y === 55 ? 'hem-edge' : 'hem';
      if (x === 2 || x === 37) return 'pipe';
      return null;
    };
    for (let f = 0; f < F; f++) {
      const angLead = (f / 16) * Math.PI * 2 - Math.PI / 2;
      const lit = (i) => {
        const d = Math.min(Math.abs(i - f), 16 - Math.abs(i - f));
        return d === 0 ? 1 : d === 1 ? 0.4 : d === 2 ? 0.12 : 0;
      };
      const ringHit = (x, y) => {
        for (let i = 0; i < ring.length; i++) {
          const k = cellAt(x, y, ring[i][0], ring[i][1], 4);
          if (k) return { i, k };
        }
        return null;
      };
      const breathe = 0.85 + 0.15 * Math.cos((f / F) * Math.PI * 2);
      frame(alb, f * H, {
        outer: (x, y) => {
          const r = region(x, y);
          if (r === 'collar-edge' || r === 'hem-edge' || r === 'pipe') return bone[2];
          if (r === 'collar') return shade(P.void1, weave(x, y, 0.03));
          if (r === 'hem') {
            const isStar = constellation.some(([sx, sy]) => sx === x && sy === y);
            if (isStar) return bone[2];
            if (onLine(x, y)) return '#2e2a40';
            return shade(P.void1, weave(x, y, 0.025));
          }
          const h = ringHit(x, y);
          if (h) return cellShade(h.k, [bone[0], mix(bone[1], bone[2], lit(h.i)), bone[2], bone[3]]);
          const d = Math.hypot(x + 0.5 - CX, y + 0.5 - CY);
          if (d < 9.2) return d > 8.4 ? '#191427' : '#030205';
          // Rays between the cells, alternating long and short.
          const a = Math.atan2(y + 0.5 - CY, x + 0.5 - CX) + Math.PI / 2;
          const seg = ((a / (Math.PI * 2)) * 16 + 16.5) % 16;
          const off = Math.abs(seg - Math.round(seg));
          const long = Math.round(seg) % 2 === 0;
          if (off > 0.42 && d > R + 2.5 && d < R + (long ? 6 : 4)) return mix('#3a2f6e', P.void2, (d - R - 2.5) / 4);
          const aura = Math.max(0, 1 - Math.abs(d - R) / 8);
          let col = mix(P.void0, '#120e20', Math.max(0, 1 - d / 34));
          if (aura > 0 && bayer(x, y) < aura * 0.7 * breathe) col = mix(P.void1, '#2a2050', aura);
          const st = stars.find(([sx, sy]) => sx === x && sy === y);
          if (st) col = mix(P.void3, bone[1], st[2] * 0.5);
          return shade(col, weave(x, y, 0.015));
        },
        mat: (x, y) => {
          const r = region(x, y);
          if (r === 'collar-edge' || r === 'hem-edge' || r === 'pipe') return 'metal';
          return ringHit(x, y) ? 'metal' : null;
        },
        matCanvas: mat,
        edgeMat: 'metal',
        lining: label(satin(rgb('#0e0c16'), rgb('#1d1a28'))),
        edge: edgeOf(rgb(bone[1]), rgb(P.void1)),
      });
      frame(emi, f * H, {
        outer: (x, y) => {
          const r = region(x, y);
          if (r === 'hem') {
            const st = constellation.findIndex(([sx, sy]) => sx === x && sy === y);
            if (st >= 0) return [P.vio6, Math.round(60 + 120 * (0.5 + 0.5 * Math.cos((f / F + st / 6) * Math.PI * 2)))];
            return null;
          }
          if (r) return null;
          const h = ringHit(x, y);
          if (h) {
            const l = lit(h.i);
            return [l > 0.5 ? P.vio6 : bone[2], Math.round(40 + l * 215 * (h.k === 'dark' ? 0.5 : 1))];
          }
          const d = Math.hypot(x + 0.5 - CX, y + 0.5 - CY);
          if (d > 8.4 && d < 9.6) {
            const a = Math.atan2(y + 0.5 - CY, x + 0.5 - CX);
            const near = Math.max(0, Math.cos(a - angLead));
            return [P.vio5, Math.round(30 + 200 * near ** 6)];
          }
          const aura = Math.max(0, 1 - Math.abs(d - R) / 8);
          if (aura > 0.3 && bayer(x, y) < aura * 0.3 * breathe) return [P.vio3, Math.round(36 * aura)];
          const st = stars.find(([sx, sy]) => sx === x && sy === y);
          if (st) return [P.vio5, Math.round(30 + 110 * (0.5 + 0.5 * Math.cos((f / F + st[2]) * Math.PI * 2)))];
          return null;
        },
      });
    }
    save('void-cape', 'texture.png', alb);
    save('void-cape', 'emissive.png', emi);
    save('void-cape', 'material.png', mat);
    save('void-cape', 'cape.json', { frames: { count: F, fps: 6 }, stiffness: 0.9, weight: 1.05,
      glow: [{ at: [5, 6.75], color: P.vio5, size: 9, strength: 0.18 }] });
  }

  /* ================================================== ring: the seal (tintable) */
  {
    // A seal pressed into tinted cloth: an embossed silver ring, the sixteen cells inside it, a
    // diamond at the heart, in a double silver frame with corner studs. A glint passes over the
    // metal every few seconds. The tint colours the cloth, never the silver.
    const F = 12;
    const alb = new Canvas(W, H * F), tint = new Canvas(W, H * F), emi = new Canvas(W, H * F), mat = new Canvas(W, H);
    const CX = 20, CY = 28;
    const silver = ['#4a4d57', '#80848f', '#b9bdc7', '#e9ecf2'];
    const cells = ringCells(CX, CY, 9.5, 16);
    const seal = (x, y) => {
      const d = Math.hypot(x + 0.5 - CX, y + 0.5 - CY);
      if (d >= 12.6 && d <= 14.6) {
        // Embossed band: lit on its upper left, shadowed lower right.
        const a = Math.atan2(y + 0.5 - CY, x + 0.5 - CX);
        const facing = -Math.cos(a + Math.PI / 4);
        const edge = d < 13.2 ? -facing : d > 14.0 ? facing : 0;
        return edge > 0.35 ? 'lit' : edge < -0.35 ? 'dark' : 'body';
      }
      if (d >= 16.4 && d <= 17.2 && Math.floor((Math.atan2(y - CY, x - CX) + Math.PI) / (Math.PI / 24)) % 2 === 0) return 'body';
      for (const [cx, cy] of cells) {
        const k = cellAt(x, y, cx, cy, 3);
        if (k) return k === 'corner' ? 'body' : k;
      }
      const m = Math.abs(x + 0.5 - CX) + Math.abs(y + 0.5 - CY);
      if (m <= 3.2) return m <= 1.2 ? 'spark' : x + 0.5 < CX || y + 0.5 < CY ? 'lit' : 'dark';
      return null;
    };
    const frameAt = (x, y) => {
      const bx = Math.min(x, FW - 1 - x), by = Math.min(y, FH - 1 - y);
      const b = Math.min(bx, by);
      const corner = bx <= 4 && by <= 4 && bx >= 2 && by >= 2;
      if (corner) return bx === 2 || by === 2 ? 'lit' : 'body';
      if (b === 1 || b === 2) return b === 1 ? 'lit' : 'dark';
      if (b === 5) return 'thin';
      return null;
    };
    for (let f = 0; f < F; f++) {
      const sweep = (f / F) * 1.8 - 0.4;
      const glint = (x, y) => {
        const t = (x + y * 0.6) / 70 - sweep;
        return Math.max(0, 1 - Math.abs(t) / 0.05);
      };
      const metal = (x, y) => seal(x, y) || frameAt(x, y);
      frame(alb, f * H, {
        outer: (x, y) => {
          const k = metal(x, y);
          if (k) return k === 'thin' ? silver[1] : cellShade(k, silver);
          // Untinted reference colour (the tint mask carries the field when tinted).
          const d = Math.hypot(x - CX, y - CY) / 34;
          return shade(P.slate3, weave(x, y, 0.03) - d * 0.12);
        },
        mat: (x, y) => (metal(x, y) ? 'metal' : null),
        matCanvas: mat,
        edgeMat: 'metal',
        lining: label(satin(rgb(P.slate1))),
        edge: edgeOf(rgb(silver[2]), rgb(P.slate0)),
      });
      frame(tint, f * H, {
        outer: (x, y) => {
          if (metal(x, y)) return null;
          const d = Math.hypot(x - CX, y - CY) / 34;
          let v = 0.84 - d * 0.22 + weave(x, y, 0.04);
          // The seal is pressed in: a shadow just outside its lower right, a lift upper left.
          const s = seal(x - 1, y - 1) ? -0.2 : seal(x + 1, y + 1) ? 0.06 : 0;
          if (frameAt(x - 1, y - 1)) v -= 0.12;
          return shade([255, 255, 255], v + s - 1);
        },
      });
      frame(emi, f * H, {
        outer: (x, y) => {
          if (!metal(x, y)) return null;
          const g = glint(x, y);
          return g > 0.02 ? [silver[3], Math.round(150 * g)] : null;
        },
      });
    }
    save('ring-cape', 'texture.png', alb);
    save('ring-cape', 'tint.png', tint);
    save('ring-cape', 'emissive.png', emi);
    save('ring-cape', 'material.png', mat);
    save('ring-cape', 'cape.json', { frames: { count: F, fps: 6 }, stiffness: 0.9, weight: 1 });
  }

  /* ================================================== dusk: the last light */
  {
    // A landscape at the moment after sunset: banded violet sky warming to ember at the horizon,
    // the sun's glow under it, three ranges of hills (the far ridge catching the last light),
    // pines on the near one, a crescent moon, stars coming out and fireflies over the dark.
    const F = 12;
    const alb = new Canvas(W, H * F), emi = new Canvas(W, H * F), mat = new Canvas(W, H);
    const horizon = 38;
    const sky = bands([[0, '#15122b'], [0.3, '#2c2450'], [0.6, '#5a3f72'], [0.82, '#a35f78'], [0.94, '#e07a6c'], [1, '#ff9e7a']], 10);
    const far = (x) => horizon + 2 + Math.round(Math.sin(x * 0.19 + 1) * 2.2 + Math.sin(x * 0.5) * 1.1);
    const mid = (x) => horizon + 7 + Math.round(Math.sin(x * 0.13 + 3) * 2.6 + Math.sin(x * 0.37 + 1) * 1.2);
    const near = (x) => horizon + 13 + Math.round(Math.sin(x * 0.11 + 5) * 2.4 + Math.sin(x * 0.29 + 2) * 1.3);
    const pines = [[5, 6], [8, 4], [13, 5], [27, 7], [30, 5], [33, 6]];
    const pineAt = (x, y) => pines.some(([px, h]) => {
      const base = near(px);
      const top = base - h;
      return y >= top && y < base && Math.abs(x - px) <= Math.floor((y - top) / 2.2);
    });
    const stars = Array.from({ length: 18 }, (_, i) => [Math.floor(hash(i, 1, 21) * 36) + 2, Math.floor(hash(i, 2, 21) * 24) + 5, hash(i, 3, 21)]);
    const moon = (x, y) => {
      const d = Math.hypot(x - 9.5, y - 12.5), d2 = Math.hypot(x - 11.6, y - 11.3);
      return d <= 3.8 && d2 > 3.2;
    };
    const flies = [[7, 50, 0.1], [16, 53, 0.45], [24, 49, 0.7], [31, 52, 0.25], [36, 55, 0.85]];
    const region = (x, y) => {
      if (y < 3) return 'collar';
      if (y === 3) return 'collar-edge';
      if (y >= 59) return y === 59 ? 'hem-edge' : 'hem';
      if (x === 1 || x === 38) return 'pipe';
      return null;
    };
    const gold = ['#5d4426', '#9a7a4a', '#d0ad72', '#f2dcaa'];
    for (let f = 0; f < F; f++) {
      const tw = (s) => 0.5 + 0.5 * Math.cos((f / F + s[2]) * Math.PI * 2);
      const fly = (x, y) => flies.findIndex(([fx, fy, p]) => {
        const a = (f / F + p) * Math.PI * 2;
        return Math.round(fx + Math.sin(a) * 1.5) === x && Math.round(fy + Math.cos(a * 2) * 1.2) === y;
      });
      frame(alb, f * H, {
        outer: (x, y) => {
          const r = region(x, y);
          if (r === 'collar-edge' || r === 'hem-edge' || r === 'pipe') return gold[2];
          if (r === 'collar') return shade('#1d1830', weave(x, y, 0.03));
          if (r === 'hem') return shade('#0c0a13', weave(x, y, 0.03));
          if (y >= near(x) || pineAt(x, y)) return shade('#0e0b16', weave(x, y, 0.02));
          if (y >= mid(x)) return y === mid(x) ? '#3a2c4c' : mix('#251d36', '#1a1527', (y - mid(x)) / 8);
          if (y >= far(x)) {
            // The far ridge catches the last of the sun along its crest.
            if (y === far(x)) return '#c8705e';
            return mix('#4a3658', '#33284a', (y - far(x)) / 6);
          }
          if (moon(x, y)) return Math.hypot(x - 8.5, y - 13.5) < 2.4 ? '#fff1e6' : '#f1d6c8';
          const st = stars.find(([sx, sy]) => sx === x && sy === y);
          if (st && y < horizon - 10) return mix(sky(x, y, y / horizon), P.vio6, 0.3 + tw(st) * 0.5);
          let col = sky(x, y, y / horizon);
          // The sun's glow, just under the horizon right of centre.
          const g = Math.max(0, 1 - Math.hypot((x - 26) * 0.7, (y - horizon - 1) * 1.6) / 11);
          if (g > 0) col = mix(col, '#ffb48f', g * g * 0.8);
          return col;
        },
        mat: (x, y) => {
          const r = region(x, y);
          return r === 'collar-edge' || r === 'hem-edge' || r === 'pipe' ? 'metal' : null;
        },
        matCanvas: mat,
        edgeMat: 'metal',
        lining: label(satin(rgb('#1f1729'), rgb('#33263f'))),
        edge: edgeOf(rgb(gold[2]), rgb('#1b1626')),
      });
      frame(emi, f * H, {
        outer: (x, y) => {
          if (region(x, y)) return null;
          if (fly(x, y) >= 0 && y >= near(x) - 6) return [P.jade4, 230];
          if (y >= near(x) || pineAt(x, y) || y >= mid(x)) return null;
          if (y >= far(x)) return y === far(x) ? [P.emb3, 70] : null;
          if (moon(x, y)) return [P.emb6, 210];
          const st = stars.find(([sx, sy]) => sx === x && sy === y);
          if (st && y < horizon - 10) return [P.vio6, Math.round(30 + tw(st) * 210)];
          const g = Math.max(0, 1 - Math.hypot((x - 26) * 0.7, (y - horizon - 1) * 1.6) / 11);
          if (g > 0.2) return [P.emb4, Math.round(140 * g * g)];
          const dm = Math.hypot(x - 9.5, y - 12.5);
          if (dm < 7.5 && dm > 3.8 && bayer(x, y) < (1 - dm / 7.5) * 0.45) return [P.emb5, 28];
          return null;
        },
      });
    }
    save('dusk-cape', 'texture.png', alb);
    save('dusk-cape', 'emissive.png', emi);
    save('dusk-cape', 'material.png', mat);
    save('dusk-cape', 'cape.json', { frames: { count: F, fps: 4 }, stiffness: 0.88, weight: 0.95,
      glow: [{ at: [2.4, 3.1], color: P.emb5, size: 4, strength: 0.16 }, { at: [6.5, 9.6], color: P.emb4, size: 5, strength: 0.14 }] });
  }

  /* ================================================== static: a signal in the snow */
  {
    // A dead channel on an old set: a gunmetal bezel with a power light and two knobs, a rounded
    // screen of snow and scanlines with a rolling bar — and, for a moment every cycle, the VOID
    // ring surfacing through the static before the noise takes it back.
    const F = 16;
    const alb = new Canvas(W, H * F), emi = new Canvas(W, H * F), mat = new Canvas(W, H);
    const gun = ['#16181c', '#24272d', '#343840', '#4c515b', '#727885'];
    const SX0 = 4, SX1 = 35, SY0 = 6, SY1 = 50;
    const inScreen = (x, y) => {
      if (x < SX0 || x > SX1 || y < SY0 || y > SY1) return false;
      const cx = Math.min(x - SX0, SX1 - x), cy = Math.min(y - SY0, SY1 - y);
      return !(cx < 3 && cy < 3 && Math.hypot(3 - cx, 3 - cy) > 3.2);
    };
    const knob = (x, y) => [[29, 56], [34, 56]].some(([kx, ky]) => Math.hypot(x - kx, y - ky) <= 1.8);
    const led = (x, y) => x >= 6 && x <= 7 && y >= 56 && y <= 57;
    const ghost = ringCells(20, 28, 10, 16);
    for (let f = 0; f < F; f++) {
      const bar = Math.floor((f / F) * (SY1 - SY0 + 12)) + SY0 - 6;
      const glitchRow = Math.floor(hash(f, 7, 1) * (SY1 - SY0)) + SY0, glitchShift = Math.floor(hash(f, 8, 1) * 6) - 3;
      // The signal: fades in over frames 5-7, holds, gone by 11.
      const sig = f < 5 ? 0 : f < 8 ? (f - 4) / 3 : f < 10 ? 1 : f < 11 ? 0.4 : 0;
      const snow = (x, y) => {
        const gx = Math.abs(y - glitchRow) < 2 ? x + glitchShift : x;
        const cell = hash(Math.floor(gx / 2), Math.floor(y / 2), 13 + f) < 0.1 * (1 - sig * 0.6);
        const grain = hash(gx, y, 1000 + f) < 0.2 * (1 - sig * 0.5);
        return cell ? 2 : grain ? 1 : 0;
      };
      const ghostAt = (x, y) => ghost.some(([gx, gy]) => cellAt(x, y, gx, gy, 3));
      frame(alb, f * H, {
        outer: (x, y) => {
          if (inScreen(x, y)) {
            const s = snow(x, y);
            const scan = y % 2 === 0 ? -0.06 : 0;
            const roll = Math.abs(y - bar) < 3 ? 0.07 : 0;
            if (sig > 0 && ghostAt(x, y)) return shade(mix(P.slate3, P.vio5, sig * 0.8), scan);
            if (s === 2) return shade(mix(P.slate4, P.bone, 0.7), scan);
            if (s === 1) return shade(P.slate3, scan + roll);
            return shade(mix(P.slate1, '#1d2230', 0.4), scan + roll);
          }
          if (led(x, y)) return P.emb3;
          if (knob(x, y)) return Math.hypot(x - 28.6, y - 55.6) < 1 || Math.hypot(x - 33.6, y - 55.6) < 1 ? gun[4] : gun[3];
          // The bezel: lit along the top and left of its opening, dark along the bottom/right.
          const lip = inScreen(x + 1, y + 1) ? 'shadow' : inScreen(x - 1, y - 1) ? 'lit' : null;
          if (lip === 'shadow') return gun[0];
          if (lip === 'lit') return gun[3];
          if (y === 0 || x === 0 || x === FW - 1) return gun[3];
          if (y >= 53 && y <= 60 && x >= 12 && x <= 25 && (x + y) % 2 === 0) return gun[1]; // speaker grille
          return shade(gun[2], weave(x, y, 0.035));
        },
        mat: (x, y) => (inScreen(x, y) ? 'glass' : led(x, y) ? null : 'metal'),
        matCanvas: mat,
        edgeMat: 'metal',
        lining: label(satin(rgb(P.slate0))),
        edge: edgeOf(rgb(gun[4]), rgb(gun[0])),
      });
      frame(emi, f * H, {
        outer: (x, y) => {
          if (led(x, y)) return [P.emb4, 200 + (f % 4 === 0 ? 55 : 0)];
          if (!inScreen(x, y)) return null;
          if (sig > 0 && ghostAt(x, y)) return [P.vio5, Math.round(220 * sig)];
          const s = snow(x, y);
          const roll = Math.abs(y - bar) < 3 ? 40 : 0;
          return s === 2 ? [P.bone, 100 + roll] : s === 1 ? [P.mist, 24 + roll] : roll ? [P.mist, roll / 2] : null;
        },
      });
    }
    save('static-cape', 'texture.png', alb);
    save('static-cape', 'emissive.png', emi);
    save('static-cape', 'material.png', mat);
    save('static-cape', 'cape.json', { frames: { count: F, fps: 12 }, stiffness: 0.9, weight: 1 });
  }
}
