/**
 * The crosshair geometry, pinned against the Java it is a port of.
 *
 * `src/lib/crosshair.ts` exists because the crosshair is the one mod this page does not draw
 * in game — GL does, from `CrosshairGeometry.java` — and a settings page that cannot show you
 * the thing you are setting is not a settings page. The risk that creates is drift: two
 * implementations of one shape, never on screen together, quietly disagreeing.
 *
 * So the cases below are `ActuatorsTest.crosshairShapes` and `crosshairIsCentred`, transcribed.
 * If the Java changes and this does not, one of these fails; if this changes and the Java does
 * not, the same. Keeping the two suites saying the same sentences is what makes the port
 * maintainable rather than merely correct today.
 */

import { describe, expect, it } from 'vitest';

import {
  CROSSHAIR_STYLES,
  crosshairRects,
  dynamicSpread,
  isRing,
  keepsVanilla,
} from '../src/lib/crosshair.js';

describe('crosshair geometry', () => {
  it('a cross is four arms, a t-shape is three, a dot is one', () => {
    expect(crosshairRects('cross', 5, 1, 2, 0)).toHaveLength(4);
    expect(crosshairRects('t_shape', 5, 1, 2, 0)).toHaveLength(3);
    expect(crosshairRects('dot', 5, 1, 2, 0)).toHaveLength(1);
    expect(crosshairRects('none', 5, 1, 2, 0)).toHaveLength(0);
    expect(crosshairRects('default', 5, 1, 2, 0)).toHaveLength(0);
    // A circle is a ring, not rectangles.
    expect(crosshairRects('circle', 5, 1, 2, 0)).toHaveLength(0);
    expect(isRing('circle')).toBe(true);
    expect(keepsVanilla('default')).toBe(true);
    expect(keepsVanilla('cross')).toBe(false);
  });

  it('is symmetric about the exact centre', () => {
    const rects = crosshairRects('cross', 5, 1, 2, 0);
    const sumX = rects.reduce((total, r) => total + r.x + r.w / 2, 0);
    const sumY = rects.reduce((total, r) => total + r.y + r.h / 2, 0);
    expect(sumX).toBeCloseTo(0, 9);
    expect(sumY).toBeCloseTo(0, 9);
  });

  it('widens the gap only while dynamic and sprinting', () => {
    expect(dynamicSpread(false, false)).toBe(0);
    expect(dynamicSpread(true, false)).toBe(0);
    expect(dynamicSpread(false, true)).toBe(0);
    expect(dynamicSpread(true, true)).toBe(2);

    const rest = crosshairRects('cross', 5, 1, 2, 0);
    const sprinting = crosshairRects('cross', 5, 1, 2, 2);
    // The arms move out by the spread and keep their length.
    expect(sprinting[0]!.x).toBe(rest[0]!.x - 2);
    expect(sprinting[0]!.w).toBe(rest[0]!.w);
  });

  it('clamps thickness and size up, and the gap up, exactly as Java does', () => {
    expect(crosshairRects('dot', 5, 0, 2, 0)[0]!.w).toBe(1);
    expect(crosshairRects('cross', 0, 1, 2, 0)[0]!.w).toBe(1);
    // A negative gap is a zero gap, not a negative one.
    expect(crosshairRects('cross', 5, 1, -4, 0)).toEqual(crosshairRects('cross', 5, 1, 0, 0));
  });

  describe('center_dot', () => {
    it('adds one rectangle to every style that draws', () => {
      for (const style of CROSSHAIR_STYLES) {
        const without = crosshairRects(style, 5, 1, 2, 0, false).length;
        const withDot = crosshairRects(style, 5, 1, 2, 0, true).length;
        if (style === 'none' || style === 'default') {
          // Off is off, and `default` is the vanilla pass — there is no VOID draw to hang a
          // dot on, so neither gains one.
          expect(withDot, style).toBe(without);
        } else if (style === 'dot') {
          // Already a dot. Drawing it twice would double the outline pass over one rectangle.
          expect(withDot, style).toBe(without);
        } else {
          expect(withDot, style).toBe(without + 1);
        }
      }
    });

    it('is the one thing a ring draws', () => {
      expect(crosshairRects('circle', 5, 1, 2, 0, true)).toHaveLength(1);
      expect(crosshairRects('circle', 5, 1, 2, 0, true)[0]).toEqual({
        x: -0.5,
        y: -0.5,
        w: 1,
        h: 1,
      });
    });

    it('sits on the centre whatever the gap is', () => {
      for (const gap of [0, 2, 10]) {
        const [dot] = crosshairRects('cross', 5, 3, gap, 0, true);
        expect(dot, `gap ${gap}`).toEqual({ x: -1.5, y: -1.5, w: 3, h: 3 });
      }
    });
  });
});
