/**
 * HUD placement maths: anchor → transform, screen ↔ placement round-trip, and
 * the editor's snap / clamp. §8.1 — positions are anchor + offset + scale, never
 * absolute pixels, and that is only true if the conversion is exact both ways.
 */

import { describe, expect, it } from 'vitest';
import {
  GRID,
  anchorAxes,
  anchorForPosition,
  axesToAnchor,
  clampOffset,
  clampScale,
  clampToViewport,
  placementFromScreen,
  placementStyle,
  screenPosition,
  snapTo,
} from '@/store/hud-geometry';
import type { HUDAnchor } from '@/bridge/protocol';

const VIEWPORT = { width: 1300, height: 820 };
const ANCHORS: HUDAnchor[] = [
  'top-left',
  'top',
  'top-right',
  'left',
  'center',
  'right',
  'bottom-left',
  'bottom',
  'bottom-right',
];

describe('anchor decomposition', () => {
  it('round-trips every anchor through its two axes', () => {
    for (const anchor of ANCHORS) {
      const { x, y } = anchorAxes(anchor);
      expect(axesToAnchor(x, y)).toBe(anchor);
    }
  });
});

describe('placementStyle', () => {
  it('pins a top-left item to the top-left edges and scales from that corner', () => {
    const style = placementStyle('top-left', 23, 23, 1);
    expect(style.left).toBe(0);
    expect(style.top).toBe(0);
    expect(style.transformOrigin).toBe('0% 0%');
    expect(style.transform).toBe('translate(23px, 23px) scale(1)');
  });

  it('pins a bottom-left item to the bottom edge, where dy is negative', () => {
    const style = placementStyle('bottom-left', 31, -109, 1);
    expect(style.left).toBe(0);
    expect(style.bottom).toBe(0);
    expect(style.transformOrigin).toBe('0% 100%');
    expect(style.transform).toBe('translate(31px, -109px) scale(1)');
  });

  it('centres a top-anchored item with a percentage pre-translate', () => {
    const style = placementStyle('top', 0, 20, 1);
    expect(style.left).toBe('50%');
    expect(style.transform).toBe('translateX(-50%) translate(0px, 20px) scale(1)');
  });

  it('centres on both axes for the `center` anchor', () => {
    const style = placementStyle('center', 0, 0, 1.5);
    expect(style.transform).toBe(
      'translateX(-50%) translateY(-50%) translate(0px, 0px) scale(1.5)',
    );
    expect(style.transformOrigin).toBe('50% 50%');
  });

  it('never emits a 3D transform or a calc() — ultralight-notes.md §4', () => {
    for (const anchor of ANCHORS) {
      const transform = String(placementStyle(anchor, 10, -10, 2).transform);
      expect(transform).not.toMatch(/3d|translateZ|perspective|calc\(/i);
    }
  });
});

describe('screenPosition ↔ placementFromScreen', () => {
  const size = { width: 130, height: 130 };

  it('places a bottom-left widget by its bottom-left corner', () => {
    // The HUD-layout frame draws Keystrokes at (31, 581) in a 1300 × 820 frame.
    expect(screenPosition('bottom-left', 31, -109, size, VIEWPORT)).toEqual({ x: 31, y: 581 });
  });

  it('places a top-right widget by its right edge', () => {
    const at = screenPosition('top-right', -25, 23, { width: 150, height: 60 }, VIEWPORT);
    expect(at).toEqual({ x: 1125, y: 23 });
  });

  it('round-trips through every anchor', () => {
    for (const anchor of ANCHORS) {
      const at = screenPosition(anchor, -40, 60, size, VIEWPORT);
      const back = placementFromScreen(anchor, at.x, at.y, size, VIEWPORT);
      expect(back).toEqual({ dx: -40, dy: 60 });
    }
  });

  it('survives a resolution change: the corner offset is preserved', () => {
    const big = { width: 2560, height: 1440 };
    const at = screenPosition('bottom-right', -24, -24, size, big);
    expect(at).toEqual({ x: big.width - 24 - size.width, y: big.height - 24 - size.height });
  });
});

describe('snapTo', () => {
  it('quantises to the 8 px editor grid', () => {
    expect(GRID).toBe(8);
    expect(snapTo(0)).toBe(0);
    expect(snapTo(3)).toBe(0);
    expect(snapTo(5)).toBe(8);
    expect(snapTo(12)).toBe(16);
    expect(snapTo(-3)).toBe(0);
    expect(snapTo(-5)).toBe(-8);
  });

  it('accepts a different grid', () => {
    expect(snapTo(10, 4)).toBe(12);
  });
});

describe('clampToViewport', () => {
  const size = { width: 200, height: 100 };

  it('keeps a widget fully on screen', () => {
    expect(clampToViewport(-50, -50, size, VIEWPORT)).toEqual({ x: 0, y: 0 });
    expect(clampToViewport(9999, 9999, size, VIEWPORT)).toEqual({ x: 1100, y: 720 });
  });

  it('leaves an in-bounds position alone', () => {
    expect(clampToViewport(400, 300, size, VIEWPORT)).toEqual({ x: 400, y: 300 });
  });

  it('allows a bleed when asked', () => {
    expect(clampToViewport(-50, 0, size, VIEWPORT, 20)).toEqual({ x: -20, y: 0 });
  });
});

describe('clampScale / clampOffset', () => {
  it('bounds scale to the loadout.json range', () => {
    expect(clampScale(0.1)).toBe(0.25);
    expect(clampScale(9)).toBe(4);
    expect(clampScale(1.25)).toBe(1.25);
  });

  it('bounds offsets to ±4096', () => {
    expect(clampOffset(-99999)).toBe(-4096);
    expect(clampOffset(99999)).toBe(4096);
    expect(clampOffset(32)).toBe(32);
  });
});

describe('anchorForPosition', () => {
  const size = { width: 100, height: 40 };

  it('pins to the corner a widget was dropped nearest', () => {
    expect(anchorForPosition(10, 10, size, VIEWPORT)).toBe('top-left');
    expect(anchorForPosition(1180, 760, size, VIEWPORT)).toBe('bottom-right');
    expect(anchorForPosition(10, 760, size, VIEWPORT)).toBe('bottom-left');
  });

  it('pins to an edge centre when only one axis is central', () => {
    expect(anchorForPosition(600, 10, size, VIEWPORT)).toBe('top');
    expect(anchorForPosition(10, 400, size, VIEWPORT)).toBe('left');
  });

  it('pins to `center` in the middle third of both axes', () => {
    expect(anchorForPosition(600, 400, size, VIEWPORT)).toBe('center');
  });
});
