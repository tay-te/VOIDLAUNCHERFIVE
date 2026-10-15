/**
 * Emotes, trails and badges — everything that is not a model.
 *
 * Emote keyframes are degrees (rotation) and model pixels (position), per player part, with an
 * ease per key that shapes the segment *arriving* at it. Model space: y down, -z is the way the
 * player faces, -x is their right. Arm rotations: x < 0 swings an arm forward/up (-90 is
 * pointing ahead, -180 straight up); z > 0 swings the right arm outward (the left arm takes
 * z < 0); y on a forward-pointing arm swings it across (the right arm inward is y < 0).
 * `upper` bends the whole upper body (head, torso, arms) about the waist; `root` moves and turns
 * the whole player, e.g. down onto the ground for sit.
 */

import { Canvas, P, mix, shade } from './lib.mjs';

/* ================================================================================ emotes */

const k = (t, v, e = 'sine') => [t, v, e];

const EMOTES = {
  'emote-wave': {
    length: 2.2, blend: 0.22,
    parts: {
      right_arm: { rotation: [k(0, [0, 0, 0]), k(0.32, [-12, 0, 152], 'back'), k(0.55, [-10, 0, 128]), k(0.78, [-12, 0, 156]), k(1.01, [-10, 0, 128]), k(1.24, [-12, 0, 156]), k(1.5, [-10, 0, 140]), k(2.2, [0, 0, 0], 'cubic')] },
      head: { rotation: [k(0, [0, 0, 0]), k(0.4, [-4, -10, 6]), k(1.6, [-4, -10, 6]), k(2.2, [0, 0, 0])] },
      left_arm: { rotation: [k(0, [0, 0, 0]), k(0.5, [4, 0, -6]), k(2.2, [0, 0, 0])] },
      body: { rotation: [k(0, [0, 0, 0]), k(0.5, [0, 4, 0]), k(1.8, [0, 4, 0]), k(2.2, [0, 0, 0])] },
    },
  },
  'emote-clap': {
    length: 2.6, blend: 0.2,
    parts: (() => {
      const r = [k(0, [0, 0, 0]), k(0.3, [-72, -14, 0], 'quad')];
      const l = [k(0, [0, 0, 0]), k(0.3, [-72, 14, 0], 'quad')];
      let t = 0.3;
      for (let i = 0; i < 3; i++) {
        r.push(k(t + 0.18, [-74, -34, 0], 'quad'), k(t + 0.42, [-70, -10, 0], 'cubic'));
        l.push(k(t + 0.18, [-74, 34, 0], 'quad'), k(t + 0.42, [-70, 10, 0], 'cubic'));
        t += 0.5;
      }
      r.push(k(2.6, [0, 0, 0], 'cubic'));
      l.push(k(2.6, [0, 0, 0], 'cubic'));
      return {
        right_arm: { rotation: r },
        left_arm: { rotation: l },
        head: { rotation: [k(0, [0, 0, 0]), k(0.4, [6, 0, 0]), k(2.1, [6, 0, 0]), k(2.6, [0, 0, 0])] },
      };
    })(),
  },
  'emote-bow': {
    length: 3.0, blend: 0.25,
    parts: {
      upper: { rotation: [k(0, [0, 0, 0]), k(0.7, [46, 0, 0], 'cubic'), k(1.9, [48, 0, 0]), k(2.7, [0, 0, 0], 'cubic')] },
      head: { rotation: [k(0, [0, 0, 0]), k(0.7, [18, 0, 0]), k(1.9, [20, 0, 0]), k(2.7, [0, 0, 0])] },
      right_arm: { rotation: [k(0, [0, 0, 0]), k(0.7, [-38, -42, 0]), k(1.9, [-38, -42, 0]), k(2.7, [0, 0, 0])] },
      left_arm: { rotation: [k(0, [0, 0, 0]), k(0.7, [26, 0, -8]), k(1.9, [26, 0, -8]), k(2.7, [0, 0, 0])] },
      right_leg: { rotation: [k(0, [0, 0, 0]), k(0.6, [-6, 0, 0]), k(2.0, [-6, 0, 0]), k(2.7, [0, 0, 0])] },
      left_leg: { rotation: [k(0, [0, 0, 0]), k(0.6, [8, 0, 0]), k(2.0, [8, 0, 0]), k(2.7, [0, 0, 0])] },
    },
  },
  'emote-salute': {
    length: 2.2, blend: 0.15,
    parts: {
      right_arm: { rotation: [k(0, [0, 0, 0]), k(0.22, [-132, -58, 12], 'back'), k(1.5, [-132, -58, 12]), k(1.7, [-128, -52, 14]), k(2.1, [0, 0, 0], 'cubic')] },
      head: { rotation: [k(0, [0, 0, 0]), k(0.2, [-3, 0, 0]), k(1.6, [-3, 0, 0]), k(2.1, [0, 0, 0])] },
      body: { rotation: [k(0, [0, 0, 0]), k(0.2, [0, -6, 0]), k(1.6, [0, -6, 0]), k(2.1, [0, 0, 0])] },
      root: { position: [k(0, [0, 0, 0]), k(0.2, [0, -0.4, 0]), k(1.6, [0, -0.4, 0]), k(2.1, [0, 0, 0])] },
    },
  },
  'emote-shrug': {
    length: 1.9, blend: 0.18,
    parts: {
      right_arm: { rotation: [k(0, [0, 0, 0]), k(0.35, [-34, 22, 38], 'back'), k(1.25, [-34, 22, 38]), k(1.9, [0, 0, 0], 'cubic')] },
      left_arm: { rotation: [k(0, [0, 0, 0]), k(0.35, [-34, -22, -38], 'back'), k(1.25, [-34, -22, -38]), k(1.9, [0, 0, 0], 'cubic')] },
      head: { rotation: [k(0, [0, 0, 0]), k(0.35, [-6, 0, 14], 'back'), k(1.25, [-6, 0, 14]), k(1.9, [0, 0, 0])] },
      root: { position: [k(0, [0, 0, 0]), k(0.3, [0, -0.8, 0], 'quad'), k(1.25, [0, -0.8, 0]), k(1.9, [0, 0, 0])] },
    },
  },
  'emote-sit': {
    length: 1.2, loop: true, loop_from: 0.8, blend: 0.2, camera: 'low',
    parts: {
      root: { position: [k(0, [0, 0, 0]), k(0.7, [0, 11.2, 0], 'cubic'), k(1.2, [0, 11.2, 0])] },
      right_leg: { rotation: [k(0, [0, 0, 0]), k(0.7, [-86, 14, 0], 'cubic'), k(1.2, [-86, 14, 0])] },
      left_leg: { rotation: [k(0, [0, 0, 0]), k(0.7, [-86, -14, 0], 'cubic'), k(1.2, [-86, -14, 0])] },
      right_arm: { rotation: [k(0, [0, 0, 0]), k(0.7, [-38, 4, 8]), k(1.2, [-38, 4, 8])] },
      left_arm: { rotation: [k(0, [0, 0, 0]), k(0.7, [-38, -4, -8]), k(1.2, [-38, -4, -8])] },
      upper: { rotation: [k(0, [0, 0, 0]), k(0.7, [-6, 0, 0]), k(1.2, [-6, 0, 0])] },
    },
  },
};

export function paintEmotes(save) {
  for (const [id, e] of Object.entries(EMOTES)) save(id, 'emote.json', e);
}

/* ================================================================================ trails */

/** A crisp cell: a filled square with one lit corner texel, on an 8x8 sprite. */
function cellSprite(color, size = 6) {
  const c = new Canvas(8, 8);
  const o = Math.floor((8 - size) / 2);
  c.paint(o, o, size, size, (i, j) => {
    const corner = (i === 0 || i === size - 1) && (j === 0 || j === size - 1);
    if (corner) return [shade(color, -0.1), 160];
    if (i <= 1 && j <= 1) return [shade(color, 0.35)];
    return [color];
  });
  return c;
}

/** A soft round mote: bright core, falloff. */
function moteSprite(core, rim) {
  const c = new Canvas(8, 8);
  c.paint(0, 0, 8, 8, (i, j) => {
    const d = Math.hypot(i - 3.5, j - 3.5);
    if (d > 3.6) return null;
    const t = d / 3.6;
    return [mix(core, rim, t), Math.round(255 * (1 - t * t))];
  });
  return c;
}

/** A six-armed pixel flake. */
function flakeSprite(color) {
  const c = new Canvas(8, 8);
  const pts = [[3, 0], [4, 0], [3, 1], [4, 1], [3, 6], [4, 6], [3, 7], [4, 7], [0, 3], [0, 4], [1, 3], [1, 4], [6, 3], [6, 4], [7, 3], [7, 4], [1, 1], [6, 1], [1, 6], [6, 6], [2, 2], [5, 2], [2, 5], [5, 5]];
  for (const [x, y] of pts) c.set(x, y, color, 200);
  c.rect(3, 3, 2, 2, P.ice6);
  return c;
}

export function paintTrails(save) {
  save('spark-trail', 'sprite.png', cellSprite(P.vio4));
  save('spark-trail', 'trail.json', { motion: 'spark', colors: [P.vio5, P.vio4, P.vio3], life: 0.9, rate: 16, size: 0.16, cap: 28, glow: 0.35 });
  save('ember-trail', 'sprite.png', moteSprite(P.emb6, P.emb3));
  save('ember-trail', 'trail.json', { motion: 'drift', colors: [P.emb5, P.emb4, P.emb3], life: 1.4, rate: 14, size: 0.13, cap: 32, glow: 0.5 });
  save('frost-trail', 'sprite.png', flakeSprite(P.ice5));
  save('frost-trail', 'trail.json', { motion: 'settle', colors: [P.ice6, P.ice5, P.ice4], life: 1.6, rate: 10, size: 0.15, cap: 30, glow: 0.25 });
  save('stair-trail', 'sprite.png', cellSprite(P.jade4, 6));
  save('stair-trail', 'trail.json', { motion: 'stair', colors: [P.jade5, P.jade4, P.jade3], life: 1.1, rate: 9, size: 0.14, cap: 24, glow: 0.2 });
}

/* ================================================================================ badges */

const RING = [[2, 0], [3, 0], [4, 0], [1, 1], [5, 1], [0, 2], [6, 2], [0, 3], [6, 3], [0, 4], [6, 4], [1, 5], [5, 5], [2, 6], [3, 6], [4, 6]];

export function paintBadges(save) {
  // 9x9 icons, drawn at the nametag's font scale: one texel is one font pixel.
  {
    const c = new Canvas(9, 9);
    for (const [x, y] of RING) c.set(x + 1, y + 1, P.bone);
    save('void-badge-player', 'icon.png', c);
    save('void-badge-player', 'badge.json', {});
  }
  {
    // Founder: a cut stone in ember, a violet cell at its heart, glinting.
    const F = 8;
    const c = new Canvas(9, 9 * F);
    const e = new Canvas(9, 9 * F);
    const D = ['....#....', '...###...', '..#####..', '.#######.', '#########', '.#######.', '..#####..', '...###...', '....#....'];
    for (let f = 0; f < F; f++) {
      for (let y = 0; y < 9; y++) for (let x = 0; x < 9; x++) {
        if (D[y][x] !== '#') continue;
        const upper = y < 4 || (y === 4 && x < 4);
        let col = upper ? (x + y < 7 ? P.emb5 : P.emb4) : (x - y > 0 ? P.emb2 : P.emb3);
        if (Math.abs(x - 4) <= 0 && Math.abs(y - 4) <= 1 || (y === 4 && Math.abs(x - 4) <= 1)) col = P.vio4;
        const glint = f === 0 && (x + y === 5) ? P.emb6 : f === 1 && x + y === 7 ? P.emb6 : null;
        c.set(x, f * 9 + y, glint ?? col);
        if (glint) e.set(x, f * 9 + y, P.emb6, 230);
        else if (col === P.vio4) e.set(x, f * 9 + y, P.vio5, 120);
      }
    }
    save('void-badge-founder', 'icon.png', c);
    save('void-badge-founder', 'emissive.png', e);
    save('void-badge-founder', 'badge.json', { frames: { count: F, fps: 6 }, glow: 0.35 });
  }
  {
    const c = new Canvas(9, 9);
    for (let y = 0; y < 3; y++) for (let x = 0; x < 3; x++) {
      const lit = (x + y) % 2 === 0;
      c.rect(x * 3, y * 3, 2, 2, lit ? P.vio4 : P.vio2);
      c.set(x * 3, y * 3, lit ? P.vio5 : P.vio3);
    }
    save('cell-badge', 'icon.png', c);
    save('cell-badge', 'badge.json', {});
  }
  {
    const c = new Canvas(9, 9);
    for (let i = 0; i < 3; i++) {
      c.rect(i * 3, 6 - i * 3, 2, 2, [P.ice3, P.ice4, P.ice5][i]);
      c.set(i * 3, 6 - i * 3, P.ice6);
    }
    save('frost-badge', 'icon.png', c);
    save('frost-badge', 'badge.json', {});
  }
}
