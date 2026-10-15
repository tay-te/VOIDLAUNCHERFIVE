/**
 * The six capes, painted at four times the vanilla density: the vanilla cape layout (outer face
 * seen from behind at (1,1) 10x16, lining at (12,1), one-pixel edges round the border) scaled by
 * K = 4, so each face is 40 x 64 texels and a frame is 256 x 128. Frames stack vertically.
 *
 * Every cape is composed, not patterned: a woven ground, a trimmed edge (piping inset from the
 * sides and hem, a collar band), a lining of quilted satin, edge strips painted as a lit trim, and
 * one idea per cape drawn with bevels and light. Anything that gives off light is on the emissive
 * layer, animated where it fits. The cloth renderer adds fold shading on top, so the art does not
 * paint shadows of its own beyond the bevels.
 */

import { Canvas, P, mix, shade, hash, bayer, rgb } from './lib.mjs';

const K = 4;
const FW = 10 * K, FH = 16 * K;
const W = 64 * K, H = 32 * K;

/** Paints one frame: outer face, lining and edge strips. Any of the painters may return null. */
function frame(canvas, oy, outer, lining, edge) {
  for (let y = 0; y < FH; y++) for (let x = 0; x < FW; x++) {
    const o = outer ? outer(x, y) : null;
    if (o) canvas.set(K + x, oy + K + y, o[0] ?? o, o[1] ?? 255);
    const l = lining ? lining(x, y) : null;
    if (l) canvas.set(12 * K + x, oy + K + y, l[0] ?? l, l[1] ?? 255);
  }
  if (!edge) return;
  for (let y = 0; y < FH; y++) {
    for (let k = 0; k < K; k++) {
      const e = edge(k, y);
      canvas.set(k, oy + K + y, e);
      canvas.set(11 * K + k, oy + K + y, e);
      canvas.set(22 * K + k, oy + K + y, e);
    }
  }
  for (let x = 0; x < FW; x++) for (let k = 0; k < K; k++) {
    canvas.set(K + x, oy + k, edge(k, 0));
    canvas.set(11 * K + x, oy + k, edge(k, FH - 1));
  }
}

/** A fine twill: lighter threads on a 4-texel diagonal, a hair of noise. */
const weave = (x, y, amp = 0.03) => ((x + y) % 4 === 0 ? amp : (x - y + 400) % 8 === 0 ? -amp * 0.6 : 0)
  + (hash(x, y, 77) < 0.08 ? amp * 0.5 : 0);

/** Quilted satin: a slow sheen and a diamond quilting line every 10 texels. */
function satin(base, line) {
  return (x, y) => {
    const d = (x + y) % 10 === 0 || (x - y + 400) % 10 === 0;
    if (d) return [shade(line ?? base, -0.12)];
    const sheen = Math.max(0, 1 - Math.abs(((x * 0.6 + y) % 28) - 14) / 6) * 0.07;
    return [shade(base, sheen - y * 0.002)];
  };
}

/** The trim layout on the outer face: 'pipe' 1px inset line, 'collar' band, 'hem' band, or null. */
function trim(x, y, { collar = 4, hem = 5, inset = 2 } = {}) {
  if (y < collar) return y === collar - 1 ? 'collar-edge' : 'collar';
  if (y >= FH - hem) return y === FH - hem ? 'hem-edge' : 'hem';
  if (x === inset || x === FW - 1 - inset) return 'pipe';
  return null;
}

/** A lit edge strip: bright at the face side, dimmer toward the lining. */
const edgeOf = (light, dark) => (k) => (k === 0 ? shade(light, 0.08) : k === K - 1 ? dark : mix(light, dark, k / (K - 1)));

/** The 16-cell VOID ring as cell centres on a circle. */
function ringCells(cx, cy, r, n = 16) {
  const out = [];
  for (let i = 0; i < n; i++) {
    const a = (i / n) * Math.PI * 2 - Math.PI / 2;
    out.push([Math.round(cx + Math.cos(a) * r), Math.round(cy + Math.sin(a) * r)]);
  }
  return out;
}

/** A bevelled square cell of size s at (cx, cy) centre: returns null or [shadeKey]. */
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

export function paintCapes(save) {
  /* ================================================================= origin: free, woven greys */
  {
    const c = new Canvas(W, H);
    const L = rgb('#3c3e45'), R = rgb('#5b5e66');
    const ring = ringCells(30, 50, 4.5, 16);
    frame(c, 0, (x, y) => {
      const t = trim(x, y, { collar: 4, hem: 6 });
      const base = x < FW / 2 ? L : R;
      // Herringbone: the twill reverses every 6 texels down.
      const band = Math.floor(y / 6) % 2;
      const hb = ((band ? x + y : x - y + 400) % 4 === 0) ? 0.05 : 0;
      let col = shade(base, hb + (hash(x, y, 3) < 0.06 ? 0.02 : 0) - y * 0.0015);
      if (t === 'collar' || t === 'hem') col = shade(base, -0.32 + hb * 0.5);
      if (t === 'collar-edge' || t === 'hem-edge') col = shade(P.mist, -0.25);
      if (t === 'pipe') col = shade(base, -0.18);
      // The centre seam: a dark fold line with pale stitches either side.
      if (x === FW / 2 - 1 || x === FW / 2) col = shade(L, -0.25);
      if ((x === FW / 2 - 2 || x === FW / 2 + 1) && y % 4 === 1 && !t) col = shade(P.mist, -0.2);
      // A small embroidered ring near the hem, in pale thread.
      if (ring.some(([rx, ry]) => Math.abs(rx - x) <= 0 && Math.abs(ry - y) <= 0)) col = shade(P.mist, -0.05);
      if (t === 'hem' && (x + 1) % 5 === 0 && y === FH - 3) col = shade(P.mist, -0.3);
      return [col];
    }, satin(rgb('#24252b')), edgeOf(rgb('#8a8d95'), rgb('#2a2b30')));
    save('origin-cape', 'texture.png', c);
    save('origin-cape', 'cape.json', { stiffness: 0.9, weight: 1 });
  }

  /* ================================================================= cell: a dissolving cascade */
  {
    const F = 16;
    const alb = new Canvas(W, H * F);
    const emi = new Canvas(W, H * F);
    // Cells on a 5-texel pitch (4px cells, 1px gutters): eight across, twelve down. The field is
    // dense and bright under the collar and dissolves toward the hem along a diagonal sweep;
    // where it dissolves, cells break into 2px fragments.
    const cells = [];
    for (let row = 0; row < 12; row++) for (let col = 0; col < 7; col++) {
      const cx = 3 + col * 5 + 2, cy = 6 + row * 5 + 2;
      if (cy > FH - 7) continue;
      const sweep = row / 11 + (col - 3) * 0.045;
      const keep = hash(col, row, 11);
      if (keep > 1.15 - sweep * 1.1) {
        if (keep < 1.35 - sweep && sweep > 0.4) cells.push({ cx, cy, s: 2, row, col, frag: true });
        continue;
      }
      cells.push({ cx, cy, s: 4, row, col, frag: false });
    }
    for (let f = 0; f < F; f++) {
      const oy = f * H;
      const phase = f / F;
      const wave = (row) => Math.max(0, Math.cos((row / 12 - phase) * Math.PI * 2)) ** 4;
      const twinkle = (c) => (hash(c.col, c.row, 300 + Math.floor((f + hash(c.col, c.row, 9) * F) / 3)) < 0.12 ? 1 : 0);
      const look = (x, y) => {
        for (const c of cells) {
          const k = cellAt(x, y, c.cx, c.cy, c.s);
          if (k) return { c, k };
        }
        return null;
      };
      frame(alb, oy, (x, y) => {
        const t = trim(x, y, { collar: 4, hem: 4 });
        if (t === 'collar') return [shade(P.vio0, 0.05)];
        if (t === 'collar-edge' || t === 'hem-edge') return [P.vio3];
        if (t === 'hem') return [shade(P.vio0, -0.1)];
        const hit = look(x, y);
        if (hit) {
          const { c, k } = hit;
          const hi = 1 - c.row * 0.055;
          const base = mix(P.vio2, P.vio4, Math.min(1, hi * 0.75 + wave(c.row) * 0.25));
          const col = k === 'lit' ? shade(base, 0.22) : k === 'dark' ? shade(base, -0.32) : k === 'corner' ? shade(base, -0.15)
            : k === 'spark' ? P.vio6 : base;
          return [col];
        }
        if (t === 'pipe') return [shade(P.vio1, 0.05)];
        return [shade('#120f1c', weave(x, y, 0.025) + (y < 10 ? 0.03 : 0))];
      }, satin(rgb('#1a1430'), rgb('#2a2250')), edgeOf(rgb(P.vio4), rgb('#1a1430')));
      frame(emi, oy, (x, y) => {
        const t = trim(x, y, { collar: 4, hem: 4 });
        if (t === 'collar-edge' || t === 'hem-edge') return [P.vio4, 70];
        const hit = look(x, y);
        if (!hit) return null;
        const { c, k } = hit;
        const glow = 0.12 + wave(c.row) * 0.6 + twinkle(c) * 0.35;
        const a = Math.round(255 * Math.min(1, glow) * (k === 'dark' ? 0.4 : k === 'spark' ? 1 : 0.85) * (1 - c.row * 0.04));
        return [k === 'spark' ? P.vio6 : mix(P.vio4, P.vio6, wave(c.row) * 0.5), a];
      }, null, null);
    }
    save('cell-cape', 'texture.png', alb);
    save('cell-cape', 'emissive.png', emi);
    save('cell-cape', 'cape.json', { frames: { count: F, fps: 8 }, stiffness: 0.92, weight: 1,
      glow: [{ at: [5, 3.5], color: P.vio4, size: 7, strength: 0.2 }, { at: [5, 8], color: P.vio3, size: 6, strength: 0.12 }] });
  }

  /* ================================================================= void: the mark, an orbit, an abyss */
  {
    const F = 16;
    const alb = new Canvas(W, H * F);
    const emi = new Canvas(W, H * F);
    const CX = 20, CY = 27, R = 12.5;
    const ring = ringCells(CX, CY, R, 16);
    const stars = Array.from({ length: 22 }, (_, i) => [Math.floor(hash(i, 1, 5) * 36) + 2, Math.floor(hash(i, 2, 5) * 52) + 5, hash(i, 3, 5)]);
    for (let f = 0; f < F; f++) {
      const oy = f * H;
      const ringHit = (x, y) => {
        for (let i = 0; i < ring.length; i++) {
          const k = cellAt(x, y, ring[i][0], ring[i][1], 4);
          if (k) return { i, k };
        }
        return null;
      };
      const lit = (i) => {
        const d = Math.min(Math.abs(i - f), 16 - Math.abs(i - f));
        return d === 0 ? 1 : d === 1 ? 0.45 : d === 2 ? 0.18 : 0;
      };
      const star = (x, y) => stars.find(([sx, sy]) => sx === x && sy === y);
      frame(alb, oy, (x, y) => {
        const t = trim(x, y, { collar: 3, hem: 4 });
        if (t === 'collar-edge' || t === 'hem-edge') return [shade(P.mist, -0.15)];
        if (t === 'collar' || t === 'hem') return [shade(P.void1, 0.04)];
        const h = ringHit(x, y);
        if (h) {
          const base = mix(P.mist, P.bone, 0.4 + lit(h.i) * 0.6);
          return [h.k === 'lit' ? shade(base, 0.1) : h.k === 'dark' ? shade(base, -0.35) : h.k === 'corner' ? shade(base, -0.2) : base];
        }
        const d = Math.hypot(x + 0.5 - CX, y + 0.5 - CY);
        // The aura: a dithered violet annulus round the ring, the abyss inside it.
        const aura = Math.max(0, 1 - Math.abs(d - R) / 6);
        const inside = d < R - 3;
        let col = shade(P.void0, weave(x, y, 0.02));
        if (aura > 0 && bayer(x, y) < aura * 0.6) col = mix(P.void1, P.vio0, 0.6 + aura * 0.4);
        if (inside) col = shade(P.void0, -0.2);
        const s = star(x, y);
        if (s && !inside) col = shade(P.void2, 0.25);
        if (t === 'pipe') col = shade(P.slate2, -0.15);
        return [col];
      }, satin(rgb('#0e0c14'), rgb('#1d1a28')), edgeOf(rgb(P.mist), rgb(P.void1)));
      frame(emi, oy, (x, y) => {
        const t = trim(x, y, { collar: 3, hem: 4 });
        if (t === 'hem-edge') return [P.mist, 50];
        const h = ringHit(x, y);
        if (h) {
          const l = lit(h.i);
          return [l > 0.5 ? P.vio6 : P.bone, Math.round(55 + l * 200 * (h.k === 'dark' ? 0.5 : 1))];
        }
        const d = Math.hypot(x + 0.5 - CX, y + 0.5 - CY);
        const aura = Math.max(0, 1 - Math.abs(d - R) / 6);
        if (aura > 0.3 && bayer(x, y) < aura * 0.35) return [P.vio3, Math.round(40 * aura)];
        const s = star(x, y);
        if (s) return [P.vio5, Math.round(40 + 90 * (0.5 + 0.5 * Math.cos((f / F + s[2]) * Math.PI * 2)))];
        return null;
      }, null, null);
    }
    save('void-cape', 'texture.png', alb);
    save('void-cape', 'emissive.png', emi);
    save('void-cape', 'cape.json', { frames: { count: F, fps: 6 }, stiffness: 0.9, weight: 1.05,
      glow: [{ at: [5, 6.75], color: P.vio5, size: 9, strength: 0.18 }] });
  }

  /* ================================================================= ring: embossed on slate, tintable */
  {
    const alb = new Canvas(W, H);
    const tint = new Canvas(W, H);
    const ring = ringCells(20, 27, 12.5, 16);
    const hit = (x, y) => {
      for (const [cx, cy] of ring) {
        const k = cellAt(x, y, cx, cy, 4);
        if (k) return k;
      }
      return null;
    };
    frame(alb, 0, (x, y) => {
      const k = hit(x, y);
      if (k) return [k === 'lit' ? P.bone : k === 'dark' ? P.slate4 : k === 'corner' ? P.mist : shade(P.mist, 0.1)];
      const t = trim(x, y, { collar: 4, hem: 5 });
      if (t === 'collar-edge' || t === 'hem-edge') return [P.mist];
      return [shade(P.slate3, weave(x, y) - y * 0.003)];
    }, satin(rgb(P.slate1)), edgeOf(rgb(P.mist), rgb(P.slate1)));
    // The tint mask: the field in light greys with a soft centre light; ring and trim left white.
    frame(tint, 0, (x, y) => {
      if (hit(x, y)) return null;
      const t = trim(x, y, { collar: 4, hem: 5 });
      if (t === 'collar-edge' || t === 'hem-edge') return null;
      const d = Math.hypot(x - 20, y - 27) / 34;
      let v = 0.86 - d * 0.18 + weave(x, y, 0.04);
      if (t === 'collar' || t === 'hem') v -= 0.3;
      if (t === 'pipe') v -= 0.2;
      // A shadow under each ring cell, so the ring stands proud of the cloth.
      if (ring.some(([cx, cy]) => x - cx >= 1 && x - cx <= 3 && y - cy >= 1 && y - cy <= 3)) v -= 0.18;
      return [shade([255, 255, 255], v - 1)];
    }, null, null);
    save('ring-cape', 'texture.png', alb);
    save('ring-cape', 'tint.png', tint);
    save('ring-cape', 'cape.json', { stiffness: 0.9, weight: 1 });
  }

  /* ================================================================= dusk: a landscape at the last light */
  {
    const F = 12;
    const alb = new Canvas(W, H * F);
    const emi = new Canvas(W, H * F);
    const STOPS = [[0, '#b29cb8'], [0.22, '#8a7aa8'], [0.45, '#544a7c'], [0.7, '#2a2546'], [1, '#14111f']];
    const sky = (t) => {
      for (let i = 1; i < STOPS.length; i++) {
        if (t <= STOPS[i][0]) {
          const [a, ca] = STOPS[i - 1], [b, cb] = STOPS[i];
          return mix(ca, cb, (t - a) / (b - a));
        }
      }
      return rgb(STOPS[STOPS.length - 1][1]);
    };
    const horizon = 40;
    const back = (x) => horizon + 2 + Math.round(Math.sin(x * 0.21 + 1) * 2.5 + Math.sin(x * 0.53) * 1.2);
    const front = (x) => horizon + 9 + Math.round(Math.sin(x * 0.15 + 3) * 3 + Math.sin(x * 0.41 + 1) * 1.4);
    const pines = new Set([6, 7, 13, 25, 26, 27, 34]);
    const stars = Array.from({ length: 16 }, (_, i) => [Math.floor(hash(i, 1, 21) * 36) + 2, Math.floor(hash(i, 2, 21) * 26) + 6, hash(i, 3, 21)]);
    const moon = (x, y) => {
      const d = Math.hypot(x - 10.5, y - 13.5), d2 = Math.hypot(x - 12.5, y - 12.2);
      return d <= 3.6 && d2 > 3.1;
    };
    for (let f = 0; f < F; f++) {
      const oy = f * H;
      const tw = (s) => 0.5 + 0.5 * Math.cos((f / F + s[2]) * Math.PI * 2);
      frame(alb, oy, (x, y) => {
        const t = trim(x, y, { collar: 3, hem: 3, inset: -1 });
        if (t === 'collar-edge' || t === 'hem-edge') return [P.gold2];
        if (t === 'collar') return [shade('#3a3050', 0.05)];
        if (y >= front(x) || (pines.has(x) && y >= front(x) - 4 + (x % 2))) return [shade('#0d0b14', weave(x, y, 0.02))];
        if (y >= back(x)) return [mix('#241e36', '#1a1628', (y - back(x)) / 10)];
        if (moon(x, y)) return [P.emb6];
        const st = stars.find(([sx, sy]) => sx === x && sy === y);
        if (st && y < horizon - 6) return [mix(sky(y / horizon), P.vio6, 0.3 + tw(st) * 0.6)];
        // The sky in eight dithered steps; a warm line where the sun went down.
        const q = Math.floor((y / horizon) * 10 + bayer(x, y) * 0.999) / 10;
        let col = sky(Math.min(1, q * 0.75));
        if (y >= horizon - 2 && y <= horizon + 1) col = mix(col, P.emb3, y === horizon ? 0.38 : 0.18);
        return [col];
      }, satin(rgb('#1b1626'), rgb('#2c2440')), edgeOf(rgb(P.gold3), rgb('#1b1626')));
      frame(emi, oy, (x, y) => {
        if (moon(x, y)) return [P.emb6, 200];
        if (y >= back(x)) return null;
        if (y === horizon) return [P.emb4, 90];
        if (y === horizon - 1) return [P.emb3, 40];
        const st = stars.find(([sx, sy]) => sx === x && sy === y);
        if (st && y < horizon - 6) return [P.vio6, Math.round(40 + tw(st) * 215)];
        // A faint glow round the moon.
        const d = Math.hypot(x - 10.5, y - 13.5);
        if (d < 7 && d > 3.6 && bayer(x, y) < (1 - d / 7) * 0.5) return [P.emb5, 30];
        return null;
      }, null, null);
    }
    save('dusk-cape', 'texture.png', alb);
    save('dusk-cape', 'emissive.png', emi);
    save('dusk-cape', 'cape.json', { frames: { count: F, fps: 4 }, stiffness: 0.88, weight: 0.95,
      glow: [{ at: [2.6, 3.4], color: P.emb5, size: 4, strength: 0.16 }] });
  }

  /* ================================================================= static: a dead channel on a screen */
  {
    const F = 12;
    const alb = new Canvas(W, H * F);
    const emi = new Canvas(W, H * F);
    for (let f = 0; f < F; f++) {
      const oy = f * H;
      const bar = Math.floor((f / F) * FH);
      const glitchRow = Math.floor(hash(f, 7, 1) * FH), glitchShift = Math.floor(hash(f, 8, 1) * 6) - 3;
      const snow = (x, y) => {
        const gx = Math.abs(y - glitchRow) < 2 ? x + glitchShift : x;
        const cell = hash(Math.floor(gx / 2), Math.floor(y / 2), 13 + Math.floor((f + hash(Math.floor(gx / 2), Math.floor(y / 2), 5) * 4) / 3)) < 0.1;
        const grain = hash(gx, y, 1000 + f) < 0.18;
        return cell ? 2 : grain ? 1 : 0;
      };
      frame(alb, oy, (x, y) => {
        // A bezel: a dark rounded frame two texels in, the screen inside it.
        const bez = x < 3 || x >= FW - 3 || y < 4 || y >= FH - 4;
        const corner = (x < 5 || x >= FW - 5) && (y < 6 || y >= FH - 6) && Math.hypot(Math.min(x - 4, FW - 5 - x), Math.min(y - 5, FH - 6 - y)) > 1.5
          && (x < 5 && y < 6 || x >= FW - 5 && y < 6 || x < 5 && y >= FH - 6 || x >= FW - 5 && y >= FH - 6);
        if (bez || corner) return [shade(P.slate0, weave(x, y, 0.04) + (y === 3 || x === 2 ? 0.08 : 0))];
        const s = snow(x, y);
        const scan = y % 2 === 0 ? -0.05 : 0;
        const roll = Math.abs(y - bar) < 3 ? 0.08 : 0;
        if (s === 2) return [shade(mix(P.slate4, P.bone, 0.75), scan)];
        if (s === 1) return [shade(P.slate3, scan + roll)];
        return [shade(P.slate1, scan + roll)];
      }, satin(rgb(P.slate0)), edgeOf(rgb(P.slate4), rgb(P.slate0)));
      frame(emi, oy, (x, y) => {
        if (x < 3 || x >= FW - 3 || y < 4 || y >= FH - 4) return null;
        const s = snow(x, y);
        const roll = Math.abs(y - bar) < 3 ? 40 : 0;
        return s === 2 ? [P.bone, 110 + roll] : s === 1 ? [P.mist, 28 + roll] : roll ? [P.mist, roll / 2] : null;
      }, null, null);
    }
    save('static-cape', 'texture.png', alb);
    save('static-cape', 'emissive.png', emi);
    save('static-cape', 'cape.json', { frames: { count: F, fps: 12 }, stiffness: 0.9, weight: 1 });
  }
}
