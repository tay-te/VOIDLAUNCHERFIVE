/**
 * Emotes, trails and badges of the VOID luxe line — everything that is not a model.
 *
 * Emote keyframes are degrees (rotation) and model pixels (position), per player part, with an
 * ease per key that shapes the segment *arriving* at it. Model space: y down, -z is the way the
 * player faces, -x is their right. Arm rotations: x < 0 swings an arm forward/up (-90 is
 * pointing ahead, -180 straight up); z > 0 swings the right arm outward (the left arm takes
 * z < 0); y on a forward-pointing arm swings it across (the right arm inward is y < 0).
 * `upper` bends the whole upper body (head, torso, arms) about the waist; `root` moves and turns
 * the whole player, e.g. down onto the ground for sit.
 *
 * Luxe emotes are unhurried and confident: eased in and out (`cubic`, `sine`), a held pose in
 * the middle, the chin up, a `back` overshoot only where a flourish wants one, never a bounce.
 */

import { Canvas } from './lib.mjs';
import { J, M, N, stoneLight } from './collection.mjs';

/* ================================================================================ emotes */

const k = (t, v, e = 'sine') => [t, v, e];
const Z = [0, 0, 0];

const EMOTES = {
  // Greeting: the hand rises in one smooth arc, two easy waves from the elbow, the chin lifts and
  // the body opens a little toward the wave; it settles as smoothly as it rose.
  'emote-wave': {
    length: 2.6, blend: 0.25,
    parts: {
      right_arm: { rotation: [k(0, Z), k(0.45, [-14, 0, 146], 'cubic'), k(0.8, [-10, 0, 128]), k(1.15, [-14, 0, 150]), k(1.5, [-10, 0, 130]), k(1.85, [-12, 0, 142]), k(2.6, Z, 'cubic')] },
      left_arm: { rotation: [k(0, Z), k(0.6, [6, 0, -5]), k(2.0, [6, 0, -5]), k(2.6, Z)] },
      head: { rotation: [k(0, Z), k(0.5, [-7, -12, 5], 'cubic'), k(2.0, [-7, -12, 5]), k(2.6, Z, 'cubic')] },
      body: { rotation: [k(0, Z), k(0.5, [0, 6, 0], 'cubic'), k(2.0, [0, 6, 0]), k(2.6, Z, 'cubic')] },
    },
  },
  // Applause: three unhurried claps in front of the chest, the head lifted, a slight lean back.
  'emote-clap': {
    length: 2.8, blend: 0.22,
    parts: (() => {
      const r = [k(0, Z), k(0.35, [-68, -16, 0], 'cubic')];
      const l = [k(0, Z), k(0.35, [-68, 16, 0], 'cubic')];
      let t = 0.35;
      for (let i = 0; i < 3; i++) {
        r.push(k(t + 0.2, [-70, -34, 0], 'quad'), k(t + 0.55, [-66, -12, 0], 'cubic'));
        l.push(k(t + 0.2, [-70, 34, 0], 'quad'), k(t + 0.55, [-66, 12, 0], 'cubic'));
        t += 0.55;
      }
      r.push(k(2.8, Z, 'cubic'));
      l.push(k(2.8, Z, 'cubic'));
      return {
        right_arm: { rotation: r },
        left_arm: { rotation: l },
        head: { rotation: [k(0, Z), k(0.45, [-6, 0, 0], 'cubic'), k(2.3, [-6, 0, 0]), k(2.8, Z)] },
        upper: { rotation: [k(0, Z), k(0.45, [-4, 0, 0], 'cubic'), k(2.3, [-4, 0, 0]), k(2.8, Z)] },
      };
    })(),
  },
  // Reverence: a court bow. The right foot draws back, the right hand comes to the heart, the left
  // sweeps out low, the body folds from the waist and holds, then rises taller than it bowed.
  'emote-bow': {
    length: 3.3, blend: 0.28,
    parts: {
      upper: { rotation: [k(0, Z), k(0.8, [40, 0, 0], 'cubic'), k(2.0, [42, 0, 0]), k(2.9, [-3, 0, 0], 'cubic'), k(3.3, Z)] },
      head: { rotation: [k(0, Z), k(0.8, [14, 0, 0], 'cubic'), k(2.0, [16, 0, 0]), k(2.9, [-5, 0, 0], 'cubic'), k(3.3, Z)] },
      right_arm: { rotation: [k(0, Z), k(0.7, [-58, -46, 0], 'cubic'), k(2.0, [-58, -46, 0]), k(2.9, Z, 'cubic')] },
      left_arm: { rotation: [k(0, Z), k(0.8, [-24, 0, -38], 'cubic'), k(2.0, [-26, 0, -40]), k(2.9, Z, 'cubic')] },
      right_leg: { rotation: [k(0, Z), k(0.6, [14, 0, 0], 'cubic'), k(2.1, [14, 0, 0]), k(2.9, Z, 'cubic')] },
      left_leg: { rotation: [k(0, Z), k(0.6, [-6, 0, 0], 'cubic'), k(2.1, [-6, 0, 0]), k(2.9, Z, 'cubic')] },
    },
  },
  // Salute: up crisply to the brow, held still, a small nod, lowered with control.
  'emote-salute': {
    length: 2.4, blend: 0.16,
    parts: {
      right_arm: { rotation: [k(0, Z), k(0.28, [-134, -56, 12], 'cubic'), k(1.55, [-134, -56, 12]), k(1.75, [-130, -52, 13]), k(2.35, Z, 'cubic')] },
      head: { rotation: [k(0, Z), k(0.28, [-4, 0, 0], 'cubic'), k(1.45, [-4, 0, 0]), k(1.65, [4, 0, 0]), k(1.85, [-2, 0, 0]), k(2.35, Z)] },
      body: { rotation: [k(0, Z), k(0.28, [0, -5, 0], 'cubic'), k(1.75, [0, -5, 0]), k(2.35, Z)] },
      root: { position: [k(0, Z), k(0.28, [0, -0.4, 0], 'cubic'), k(1.75, [0, -0.4, 0]), k(2.35, Z)] },
    },
  },
  // Nonchalance: palms open at the hips, the shoulders lift and drop once, a tilt of the head.
  'emote-shrug': {
    length: 2.1, blend: 0.2,
    parts: {
      right_arm: { rotation: [k(0, Z), k(0.4, [-30, 24, 30], 'cubic'), k(1.35, [-30, 24, 30]), k(2.1, Z, 'cubic')] },
      left_arm: { rotation: [k(0, Z), k(0.4, [-30, -24, -30], 'cubic'), k(1.35, [-30, -24, -30]), k(2.1, Z, 'cubic')] },
      head: { rotation: [k(0, Z), k(0.45, [-4, 8, 12], 'cubic'), k(1.35, [-4, 8, 12]), k(2.1, Z)] },
      root: { position: [k(0, Z), k(0.35, [0, -0.8, 0], 'cubic'), k(0.8, [0, -0.5, 0]), k(1.35, [0, -0.5, 0]), k(2.1, Z)] },
    },
  },
  // Repose: down onto the ground at ease, one knee raised, leaning back on the hands.
  'emote-sit': {
    length: 1.3, loop: true, loop_from: 0.9, blend: 0.22, camera: 'low',
    parts: {
      root: { position: [k(0, Z), k(0.8, [0, 11.2, 0], 'cubic'), k(1.3, [0, 11.2, 0])] },
      right_leg: { rotation: [k(0, Z), k(0.8, [-118, 10, 0], 'cubic'), k(1.3, [-118, 10, 0])] },
      left_leg: { rotation: [k(0, Z), k(0.8, [-84, -16, 0], 'cubic'), k(1.3, [-84, -16, 0])] },
      right_arm: { rotation: [k(0, Z), k(0.8, [26, 0, 14], 'cubic'), k(1.3, [26, 0, 14])] },
      left_arm: { rotation: [k(0, Z), k(0.8, [26, 0, -14], 'cubic'), k(1.3, [26, 0, -14])] },
      upper: { rotation: [k(0, Z), k(0.8, [-10, 0, 0], 'cubic'), k(1.3, [-10, 0, 0])] },
      head: { rotation: [k(0, Z), k(0.8, [-6, 0, 0], 'cubic'), k(1.3, [-6, 0, 0])] },
    },
  },
};

export function paintEmotes(save) {
  for (const [id, e] of Object.entries(EMOTES)) save(id, 'emote.json', e);
}

/* ================================================================================ trails */

/*
 * Trail sprites are white and greys: the runtime multiplies each particle by one of the trail's
 * colours, so the sprite carries only the cut (light facets, mid, shadow) and the ramp the colour.
 * Small and sparse: two to five texels on an 8 x 8 sprite, drawn about a tenth of a block.
 */

const W1 = [255, 255, 255], W2 = [214, 214, 214], W3 = [164, 164, 164], W4 = [118, 118, 118];

function sprite(rows) {
  const c = new Canvas(8, 8);
  const pal = { 1: W1, 2: W2, 3: W3, 4: W4 };
  rows.forEach((r, y) => [...r].forEach((ch, x) => { if (pal[ch]) c.set(x, y, pal[ch]); }));
  return c;
}

export function paintTrails(save) {
  // Gilt sparks: a tiny four-point star of gold.
  save('gilt-trail', 'sprite.png', sprite([
    '........',
    '...2....',
    '...1....',
    '.2111...',
    '...13...',
    '...3....',
    '........',
    '........',
  ]));
  save('gilt-trail', 'trail.json', { motion: 'spark', colors: [M.gold[4], M.gold[3], M.gold[3]], life: 0.7, rate: 12, size: 0.11, cap: 22, glow: 0.3 });
  // Amethyst dust: a cut stone, light upper-left facets, a deep lower-right.
  save('amethyst-trail', 'sprite.png', sprite([
    '........',
    '...1....',
    '..112...',
    '.11223..',
    '..234...',
    '...4....',
    '........',
    '........',
  ]));
  save('amethyst-trail', 'trail.json', { motion: 'drift', colors: [J.amethyst[3], J.amethyst[2], J.amethyst[2]], life: 1.3, rate: 10, size: 0.1, cap: 24, glow: 0.4 });
  // Topaz frost: a long crystal facet that settles.
  save('topaz-trail', 'sprite.png', sprite([
    '........',
    '...1....',
    '...12...',
    '..112...',
    '..123...',
    '...23...',
    '...3....',
    '........',
  ]));
  save('topaz-trail', 'trail.json', { motion: 'settle', colors: [J.topaz[3], J.topaz[2], J.topaz[4]], life: 1.5, rate: 9, size: 0.11, cap: 24, glow: 0.3 });
  // Jade steps: a bevelled tile.
  save('jade-trail', 'sprite.png', sprite([
    '........',
    '........',
    '..1112..',
    '..1223..',
    '..1223..',
    '..2334..',
    '........',
    '........',
  ]));
  save('jade-trail', 'trail.json', { motion: 'stair', colors: [J.jade[3], J.jade[2], J.jade[2]], life: 1.1, rate: 8, size: 0.12, cap: 22, glow: 0.2 });
}

/* ================================================================================ badges */

/*
 * Enamel pins, 9 x 9, drawn at the nametag's font scale: a metal rim lit at the upper left and
 * shaded at the lower right, flat enamel, and one stone or glint that twinkles on a slow strip.
 * Maps through a legend; `.` is empty.
 */
function pin(id, rows, legend, frames = 1, fps = 6, glow = 0) {
  const c = new Canvas(9, 9 * frames);
  const e = new Canvas(9, 9 * frames);
  let lit = false;
  for (let f = 0; f < frames; f++) {
    rows.forEach((r, y) => [...r].forEach((ch, x) => {
      const l = legend[ch];
      if (!l) return;
      c.set(x, f * 9 + y, l.c);
      const g = typeof l.g === 'function' ? l.g(f) : l.g;
      if (g) {
        e.set(x, f * 9 + y, g[0], g[1]);
        lit = true;
      }
    }));
  }
  save_(id, 'icon.png', c);
  if (lit) save_(id, 'emissive.png', e);
  save_(id, 'badge.json', frames > 1 ? { frames: { count: frames, fps }, glow } : { glow });
}

let save_;

const gemLegend = (jr, F, phase = 0) => ({
  A: { c: jr[4], g: (f) => [jr[4], stoneLight(f, F, phase).spark] },
  a: { c: jr[3], g: (f) => [jr[2], stoneLight(f, F, phase).base] },
  b: { c: jr[2] },
  d: { c: jr[1] },
});

export function paintBadges(save) {
  save_ = save;
  const S = M.silver, G = M.gold, R = M.brass;
  // VOID player: the VOID ring as a silver pin, lit at the upper left.
  pin('void-badge-player', [
    '.........',
    '...hhh...',
    '..h...m..',
    '.h.....m.',
    '.h.....m.',
    '.m.....l.',
    '..m...l..',
    '...lll...',
    '.........',
  ], { h: { c: S[4] }, m: { c: S[3] }, l: { c: S[2] } });
  // Founder: a gold lozenge, charcoal enamel, the garnet set at its heart.
  {
    const F = 8;
    // A lozenge by distance from the centre: the rim at 4, charcoal enamel at 3, the stone
    // inside, lit from the upper left.
    const rows = [];
    for (let y = 0; y < 9; y++) {
      let r = '';
      for (let x = 0; x < 9; x++) {
        const d = Math.abs(x - 4) + Math.abs(y - 4), s2 = (x - 4) + (y - 4);
        if (d > 4) r += '.';
        else if (d === 4) r += s2 < 0 ? 'G' : s2 > 0 ? 'o' : 'g';
        else if (d === 3) r += 'e';
        else if (x === 3 && y === 3) r += 'A';
        else r += s2 < 0 ? 'a' : s2 > 0 ? 'd' : 'b';
      }
      rows.push(r);
    }
    pin('void-badge-founder', rows, { G: { c: G[3] }, o: { c: G[1] }, e: { c: N.charcoal[2] }, ...gemLegend(J.garnet, F), g: { c: G[2] } }, F, 6, 0.35);
  }
  // Atelier pin: amethyst enamel in a polished silver rim, one glint.
  {
    const F = 8;
    pin('atelier-badge', [
      '.........',
      '..hhhhm..',
      '.hAabbbm.',
      '.habbbbm.',
      '.hbbbbdm.',
      '.hbbbddl.',
      '.mbbdddl.',
      '..mllll..',
      '.........',
    ], { h: { c: S[4] }, m: { c: S[3] }, l: { c: S[2] }, ...gemLegend(J.amethyst, F) }, F, 6, 0.25);
  }
  // Laurel pin: a brass laurel round a jade.
  {
    const F = 8;
    pin('laurel-badge', [
      '.........',
      '.h.....h.',
      'hm..A..mh',
      'h...b...l',
      'mh..d..hl',
      '.mh...hl.',
      '..mh.hl..',
      '....m....',
      '.........',
    ], { h: { c: R[4] }, m: { c: R[3] }, l: { c: R[2] }, ...gemLegend(J.jade, F) }, F, 6, 0.25);
  }
}
