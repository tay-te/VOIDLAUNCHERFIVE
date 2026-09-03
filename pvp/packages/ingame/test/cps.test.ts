/**
 * CPS is derived in JS from the rising edges of `keys.lmb` / `keys.rmb` (§3).
 * These are the edge and window rules the counter depends on.
 */

import { describe, expect, it } from 'vitest';
import { cps, createClickRing, pushClick, risingEdges, trimRing } from '@/store/cps';

describe('risingEdges', () => {
  it('counts nothing on the first payload — there is no previous state to compare', () => {
    expect(risingEdges(null, { lmb: 1, rmb: 1 })).toEqual({ lmb: false, rmb: false });
  });

  it('fires on 0 → 1 only', () => {
    expect(risingEdges({ lmb: 0, rmb: 0 }, { lmb: 1, rmb: 0 })).toEqual({ lmb: true, rmb: false });
  });

  it('does not fire while the button is held', () => {
    expect(risingEdges({ lmb: 1, rmb: 0 }, { lmb: 1, rmb: 0 })).toEqual({ lmb: false, rmb: false });
  });

  it('does not fire on release', () => {
    expect(risingEdges({ lmb: 1, rmb: 1 }, { lmb: 0, rmb: 0 })).toEqual({ lmb: false, rmb: false });
  });

  it('tracks the two buttons independently', () => {
    expect(risingEdges({ lmb: 1, rmb: 0 }, { lmb: 1, rmb: 1 })).toEqual({ lmb: false, rmb: true });
  });
});

describe('cps', () => {
  it('is zero with no clicks', () => {
    expect(cps(createClickRing(), 1000)).toBe(0);
  });

  it('counts every click inside the window', () => {
    const ring = createClickRing();
    for (const at of [100, 300, 500, 700, 900]) pushClick(ring, at);
    expect(cps(ring, 1000, 1000)).toBe(5);
  });

  it('ages a click out at exactly one window — the window is half-open', () => {
    const ring = createClickRing();
    pushClick(ring, 1000);
    expect(cps(ring, 2000, 1000)).toBe(0);
    expect(cps(ring, 1999, 1000)).toBe(1);
  });

  it('counts without mutating, so the same ring reads the same twice', () => {
    const ring = createClickRing();
    for (const at of [0, 100, 200, 5000]) pushClick(ring, at);
    expect(cps(ring, 5200, 1000)).toBe(1);
    expect(cps(ring, 5200, 1000)).toBe(1);
    expect(ring.at).toHaveLength(4);
  });

  it('trimRing is what prunes aged samples', () => {
    const ring = createClickRing();
    for (const at of [0, 100, 200, 5000]) pushClick(ring, at);
    trimRing(ring, 5200, 1000);
    expect(ring.at).toEqual([5000]);
  });

  it('honours a non-default window_ms', () => {
    const ring = createClickRing();
    for (const at of [100, 600, 1100, 1600]) pushClick(ring, at);
    expect(cps(ring, 2000, 500)).toBe(1);
    expect(cps(ring, 2000, 5000)).toBe(4);
  });

  it('stays bounded under a click storm', () => {
    const ring = createClickRing();
    for (let i = 0; i < 5000; i += 1) pushClick(ring, i);
    expect(ring.at.length).toBeLessThanOrEqual(256);
  });
});
