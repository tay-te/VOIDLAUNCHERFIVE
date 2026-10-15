/**
 * The Reach display's history — the last ten landed hits, keyed by the sensor's `n`.
 *
 * Pure, like `store/cps.ts`; the store owns one ring and republishes the derived figures.
 *
 * ## Why keyed by `n`
 *
 * `tick.reach` is `{ blocks, n }`, and `n` counts measured attacks for the whole session
 * (`bridge.json`). Two hits at the same distance are still two readings, so the value cannot be
 * the key; and the same reading delivered twice — a page reload re-pushing the last tick, or a
 * coalescer that repeats it — must not be averaged in twice. `n` answers both: a reading whose
 * `n` the ring already holds is the same hit, and a reading whose `n` went *backwards* is a new
 * session (the client reconnected and the counter restarted), so the old history is not this
 * session's and is dropped rather than averaged with it.
 */

/** One landed, measured hit. */
export interface ReachReading {
  /** Eye-to-hit-point distance in blocks. */
  blocks: number;
  /** The sensor's monotonic measured-attack counter. */
  n: number;
}

/** How many hits `mode: average` is taken over — the schema's "the mean of the last ten". */
export const REACH_HISTORY = 10;

/**
 * Take a reading into the ring if it is a new hit. Mutates; returns whether it was taken.
 */
export function pushReach(ring: ReachReading[], reading: ReachReading, cap = REACH_HISTORY): boolean {
  if (!Number.isFinite(reading.blocks) || !Number.isFinite(reading.n)) return false;
  if (ring.some((held) => held.n === reading.n)) return false;
  const last = ring[ring.length - 1];
  if (last !== undefined && reading.n < last.n) ring.length = 0;
  ring.push({ blocks: reading.blocks, n: reading.n });
  if (ring.length > cap) ring.splice(0, ring.length - cap);
  return true;
}

/** Mean distance over the ring, or null when it is empty. */
export function reachAverage(ring: readonly ReachReading[]): number | null {
  if (ring.length === 0) return null;
  let total = 0;
  for (const reading of ring) total += reading.blocks;
  return total / ring.length;
}
