/**
 * The six capes, painted at twice the vanilla resolution: the vanilla cape layout (outer face
 * seen from behind at (1,1) 10x16, lining at (12,1), one-pixel edges round the border) scaled by
 * K = 2, so each face is 20 x 32 texels and every frame is 128 x 64. Frames stack vertically.
 * Albedo, an emissive layer for anything that gives off light, a tint mask for the tintable ring
 * cape.
 *
 * The cloth draws one quad per vanilla texel, so a 2x texture puts four texels on each quad: the
 * cape still moves at the skin's pixel scale, and the art gets the room to be drawn rather than
 * suggested.
 */

import { Canvas, P, mix, shade, hash, bayer, rgb } from './lib.mjs';

const K = 2;
const FW = 10 * K, FH = 16 * K; // face size
const W = 64 * K, H = 32 * K;   // frame size

/**
 * Paints one frame at y offset `oy`: `outer(x, y)` and `lining(x, y)` return [colour, alpha?] or
 * null per face texel; `edge(y)` the side/top/bottom strips (null leaves them clear).
 */
function frame(canvas, oy, outer, lining, edge) {
  for (let y = 0; y < FH; y++) for (let x = 0; x < FW; x++) {
    const o = outer(x, y);
    if (o) canvas.set(K + x, oy + K + y, o[0] ?? o, o[1] ?? 255);
    const l = lining ? lining(x, y) : null;
    if (l) canvas.set(12 * K + x, oy + K + y, l[0] ?? l, l[1] ?? 255);
  }
  if (!edge) return;
  for (let y = 0; y < FH; y++) {
    const e = edge(y / K);
    for (let k = 0; k < K; k++) {
      canvas.set(k, oy + K + y, e);
      canvas.set(11 * K + k, oy + K + y, e);
      canvas.set(22 * K + k, oy + K + y, e);
    }
  }
  for (let x = 0; x < FW; x++) for (let k = 0; k < K; k++) {
    canvas.set(K + x, oy + k, edge(0));
    canvas.set(11 * K + x, oy + k, edge(15));
  }
}

/** The lining: a deep satin, darker toward the hem, with a slow diagonal sheen. */
function satin(base) {
  return (x, y) => {
    const d = (x + y * 0.6) % 14;
    const sheen = d < 1.5 ? 0.06 : d < 3 ? 0.03 : 0;
    return [shade(base, sheen - y * 0.006)];
  };
}

/** A crisp 2px-thick ring on a 16x16 grid: the VOID mark at this resolution. */
function ringMask(cx, cy, r0, r1) {
  return (x, y) => {
    const d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
    return d >= r0 && d <= r1;
  };
}

/** Angle round the ring's centre, 0..1, for the orbiting highlight. */
const angleOf = (x, y, cx, cy) => (Math.atan2(y + 0.5 - cy, x + 0.5 - cx) / (Math.PI * 2) + 1) % 1;

export function paintCapes(save) {
  /* ------------------------------------------------------------- origin: free, woven greys */
  {
    const c = new Canvas(W, H);
    const L = rgb(P.slate2), R = rgb(P.slate4);
    const ring = ringMask(15.5, 26.5, 1.6, 2.7);
    frame(c, 0, (x, y) => {
      const base = x < FW / 2 ? L : R;
      // A tight twill: a diagonal of lighter threads every third texel.
      const tw = (x + y) % 3 === 0 ? 0.045 : (x + y) % 3 === 1 ? -0.015 : 0;
      let col = shade(base, tw - y * 0.004);
      if (y >= FH - 3) col = shade(base, -0.3 + (y === FH - 3 ? 0.08 : 0));       // the hem band
      if (x === FW / 2 - 1 && y % 3 === 1) col = shade(L, 0.22);                   // seam stitches
      if (x === FW / 2) col = shade(R, -0.12);
      if (ring(x, y)) col = shade(R, 0.28);                                        // a small mark near the hem
      return [col];
    }, satin(P.slate1), () => shade(P.slate1, -0.15));
    save('origin-cape', 'texture.png', c);
    save('origin-cape', 'cape.json', { stiffness: 0.9, weight: 1 });
  }

  /* ------------------------------------------------------------- cell: a breathing violet grid */
  {
    const F = 12;
    const alb = new Canvas(W, H * F);
    const emi = new Canvas(W, H * F);
    // 3x3 cells on a 4px step: five across, eight down, with a 1px margin.
    const cellAt = (x, y) => {
      const cx = x % 4, cy = y % 4;
      if (cx === 0 || cy === 0 || x >= 20 || y >= 32) return null;
      return { col: Math.floor(x / 4), row: Math.floor(y / 4), cx: cx - 1, cy: cy - 1 };
    };
    for (let f = 0; f < F; f++) {
      const oy = f * H;
      const phase = f / F;
      const band = (row) => Math.max(0, Math.cos((row / 8 - phase) * Math.PI * 2)) ** 3;
      frame(alb, oy, (x, y) => {
        const c = cellAt(x, y);
        if (c) {
          const hi = 1 - c.row * 0.09;
          const lit = c.cx === 0 && c.cy === 0 ? 0.16 : c.cx === 2 && c.cy === 2 ? -0.1 : 0;
          const t = Math.min(1, Math.max(0, hi * 0.5 + band(c.row) * 0.35 + lit));
          return [mix(P.vio1, P.vio5, t)];
        }
        // The ground between cells: near-black violet, a hair lighter at the collar.
        return [shade(P.void2, ((x + y) % 2 ? 0.012 : 0) + (y < 3 ? 0.04 : 0) - y * 0.003)];
      }, satin(P.vio0), () => P.void1);
      frame(emi, oy, (x, y) => {
        const c = cellAt(x, y);
        if (!c) return null;
        const b = band(c.row);
        const a = Math.round(255 * (0.14 + b * 0.66) * (1 - c.row * 0.07));
        return [mix(P.vio4, P.vio6, b * 0.6 + (c.cx === 0 && c.cy === 0 ? 0.3 : 0)), a];
      }, null, null);
    }
    save('cell-cape', 'texture.png', alb);
    save('cell-cape', 'emissive.png', emi);
    save('cell-cape', 'cape.json', { frames: { count: F, fps: 6 }, stiffness: 0.92, weight: 1,
      glow: [{ at: [5, 4], color: P.vio4, size: 7, strength: 0.16 }] });
  }

  /* ------------------------------------------------------------- void: the ring, one cell in orbit */
  {
    const F = 24;
    const alb = new Canvas(W, H * F);
    const emi = new Canvas(W, H * F);
    const CX = 10, CY = 13;
    const ring = ringMask(CX, CY, 5.2, 7.2);
    const stars = [[3, 3], [16, 5], [5, 24], [15, 28], [2, 18], [18, 21]];
    for (let f = 0; f < F; f++) {
      const oy = f * H;
      const lead = f / F;
      const dist = (x, y) => {
        const d = Math.abs(angleOf(x, y, CX, CY) - lead);
        return Math.min(d, 1 - d);
      };
      frame(alb, oy, (x, y) => {
        if (ring(x, y)) {
          const d = dist(x, y);
          return [d < 0.035 ? P.bone : d < 0.09 ? mix(P.mist, P.bone, 0.6) : P.mist];
        }
        if (stars.some(([sx, sy]) => sx === x && sy === y)) return [shade(P.void0, 0.12)];
        // True black, with a near-invisible weave so it reads as cloth and not a hole.
        const n = hash(x, y, 7) < 0.14 ? 0.022 : 0;
        return [shade(P.void0, n + (y > FH - 4 ? -0.4 : 0))];
      }, satin(P.void2), () => P.void0);
      frame(emi, oy, (x, y) => {
        if (ring(x, y)) {
          const d = dist(x, y);
          if (d < 0.035) return [P.vio6, 255];
          if (d < 0.09) return [P.bone, 170];
          if (d < 0.16) return [P.bone, 95];
          return [P.bone, 48];
        }
        const s = stars.findIndex(([sx, sy]) => sx === x && sy === y);
        if (s >= 0) return [P.vio5, Math.round(30 + 50 * (0.5 + 0.5 * Math.cos((f / F + s / 6) * Math.PI * 2)))];
        return null;
      }, null, null);
    }
    save('void-cape', 'texture.png', alb);
    save('void-cape', 'emissive.png', emi);
    save('void-cape', 'cape.json', { frames: { count: F, fps: 6 }, stiffness: 0.9, weight: 1.05,
      glow: [{ at: [5, 6.5], color: P.vio5, size: 8, strength: 0.12 }] });
  }

  /* ------------------------------------------------------------- ring: on slate, tintable */
  {
    const alb = new Canvas(W, H);
    const tint = new Canvas(W, H);
    const ring = ringMask(10, 13, 5.2, 7.2);
    frame(alb, 0, (x, y) => (ring(x, y) ? [P.bone] : [shade(P.slate3, -y * 0.006)]), satin(P.slate1), () => P.slate1);
    // The tint mask: the slate field in light greys (the tint multiplies them); the ring stays white.
    frame(tint, 0, (x, y) => {
      if (ring(x, y)) return null;
      const tw = (x + y) % 3 === 0 ? 0.04 : (x - y + 64) % 6 === 0 ? -0.03 : 0;
      return [shade([206, 206, 206], tw - y * 0.008 - (y >= FH - 3 ? 0.28 : 0))];
    }, null, null);
    save('ring-cape', 'texture.png', alb);
    save('ring-cape', 'tint.png', tint);
    save('ring-cape', 'cape.json', { stiffness: 0.9, weight: 1 });
  }

  /* ------------------------------------------------------------- dusk: bands and early stars */
  {
    const F = 8;
    const alb = new Canvas(W, H * F);
    const emi = new Canvas(W, H * F);
    // Dusk to dark: mauve, violet, near-black, with a thin warm line where the sun went down.
    const STOPS = [[0, '#7a6a84'], [0.3, '#56496a'], [0.55, '#2e2740'], [1, '#14111c']];
    const at = (t) => {
      for (let i = 1; i < STOPS.length; i++) {
        if (t <= STOPS[i][0]) {
          const [a, ca] = STOPS[i - 1], [b, cb] = STOPS[i];
          return mix(ca, cb, (t - a) / (b - a));
        }
      }
      return rgb(STOPS[STOPS.length - 1][1]);
    };
    const stars = [[4, 19, 0], [14, 22, 3], [8, 27, 5], [17, 25, 6], [2, 29, 2], [11, 17, 4], [16, 30, 1]];
    for (let f = 0; f < F; f++) {
      const oy = f * H;
      frame(alb, oy, (x, y) => {
        // Quantised into eight steps with an ordered dither between them: a gradient drawn in
        // pixels, not a smooth wash.
        const t = y / (FH - 1);
        const steps = 8;
        const q = Math.floor(t * steps + bayer(x, y) * 0.999) / steps;
        let col = at(Math.min(1, q));
        if (y === 6 || y === 7) col = mix(col, P.emb3, y === 6 ? 0.22 : 0.1);       // the last light
        const star = stars.find(([sx, sy]) => sx === x && sy === y);
        if (star) {
          const tw = 0.5 + 0.5 * Math.cos(((f + star[2]) / F) * Math.PI * 2);
          col = mix(col, P.vio6, 0.3 + tw * 0.55);
        }
        return [col];
      }, satin('#191522'), () => '#151220');
      frame(emi, oy, (x, y) => {
        if (y === 6) return [P.emb4, 60];
        const star = stars.find(([sx, sy]) => sx === x && sy === y);
        if (!star) return null;
        const tw = 0.5 + 0.5 * Math.cos(((f + star[2]) / F) * Math.PI * 2);
        return [P.vio6, Math.round(50 + tw * 205)];
      }, null, null);
    }
    save('dusk-cape', 'texture.png', alb);
    save('dusk-cape', 'emissive.png', emi);
    save('dusk-cape', 'cape.json', { frames: { count: F, fps: 3 }, stiffness: 0.88, weight: 0.95 });
  }

  /* ------------------------------------------------------------- static: a dead channel */
  {
    const F = 12;
    const alb = new Canvas(W, H * F);
    const emi = new Canvas(W, H * F);
    for (let f = 0; f < F; f++) {
      const oy = f * H;
      // Two kinds of snow: fine single-texel grain that changes every frame, and 2x2 cells
      // that hold for a few frames, so the scatter crawls rather than strobes.
      const cell = (x, y) => {
        const cx = Math.floor(x / 2), cy = Math.floor(y / 2);
        const always = hash(cx, cy, 99) < 0.05;
        const slow = hash(cx, cy, 13 + Math.floor((f + hash(cx, cy, 5) * 4) / 3)) < 0.13;
        return always || slow;
      };
      const grain = (x, y) => hash(x, y, 1000 + f) < 0.12;
      const bar = Math.floor((f / F) * FH);
      frame(alb, oy, (x, y) => {
        if (cell(x, y)) return [mix(P.slate4, P.bone, 0.72)];
        const roll = Math.abs(y - bar) < 2 ? 0.07 : 0;
        return [shade(P.slate1, roll + (grain(x, y) ? 0.09 : 0))];
      }, satin(P.slate0), () => P.slate0);
      frame(emi, oy, (x, y) => (cell(x, y) ? [P.bone, 95] : grain(x, y) ? [P.mist, 22] : null), null, null);
    }
    save('static-cape', 'texture.png', alb);
    save('static-cape', 'emissive.png', emi);
    save('static-cape', 'cape.json', { frames: { count: F, fps: 10 }, stiffness: 0.9, weight: 1 });
  }
}
