/**
 * Clicks per second, derived in JS from the rising edges of `keys.lmb` /
 * `keys.rmb` (§3: "CPS counter — derived from clicks in JS").
 *
 * Pure and allocation-light: the ring is a plain number array of timestamps,
 * trimmed from the front. `keys` is edge-triggered, so a rising edge is exactly
 * "the previous payload had 0 and this one has 1" — no polling and no rAF.
 */

/** A bounded ring of click timestamps. 40 clicks/s for 5 s is the practical max. */
const MAX_SAMPLES = 256;

export interface ClickRing {
  /** Timestamps in ms, oldest first. */
  at: number[];
}

export function createClickRing(): ClickRing {
  return { at: [] };
}

/**
 * Record a click. Only call this on a rising edge.
 * Mutates and returns the ring so the store can keep one instance per button.
 */
export function pushClick(ring: ClickRing, now: number): ClickRing {
  ring.at.push(now);
  if (ring.at.length > MAX_SAMPLES) ring.at.splice(0, ring.at.length - MAX_SAMPLES);
  return ring;
}

/**
 * Clicks inside the trailing `windowMs`. Pure — it counts, it does not prune, so
 * the same ring can be read with two different windows (the left and right
 * counters share a `window_ms` today, but nothing here assumes that).
 *
 * The window is half-open: a click at exactly `now - windowMs` has aged out.
 */
export function cps(ring: ClickRing, now: number, windowMs = 1000): number {
  const cutoff = now - windowMs;
  let count = 0;
  for (let i = ring.at.length - 1; i >= 0; i -= 1) {
    if (ring.at[i]! <= cutoff) break; // timestamps are ordered, so we are done
    count += 1;
  }
  return count;
}

/**
 * Drop samples that can no longer count, so the ring does not grow without
 * bound in a long session. Called from the 20 Hz tick, never from a hot path.
 */
export function trimRing(ring: ClickRing, now: number, windowMs = 1000): ClickRing {
  const cutoff = now - windowMs;
  let drop = 0;
  while (drop < ring.at.length && ring.at[drop]! <= cutoff) drop += 1;
  if (drop > 0) ring.at.splice(0, drop);
  return ring;
}

/**
 * Rising edges between two `keys` payloads, for the two mouse buttons.
 * `previous` is null on the first push, where nothing counts as an edge.
 */
export function risingEdges(
  previous: { lmb: 0 | 1; rmb: 0 | 1 } | null,
  next: { lmb: 0 | 1; rmb: 0 | 1 },
): { lmb: boolean; rmb: boolean } {
  if (!previous) return { lmb: false, rmb: false };
  return {
    lmb: previous.lmb === 0 && next.lmb === 1,
    rmb: previous.rmb === 0 && next.rmb === 1,
  };
}
