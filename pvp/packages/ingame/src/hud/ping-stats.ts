/**
 * Rolling ping statistics — the Ping display's `show_average`, `show_jitter` and spike mark.
 *
 * Pure, like `store/cps.ts`. The store keeps the raw material — the last {@link PING_HISTORY}
 * distinct readings of `tick.ping` — and every figure here is computed from it per render, over
 * the mod's own `window`. Keeping the window out of the store is deliberate: it is a setting, and
 * a history pre-averaged over one window would be wrong the moment the slider moved.
 *
 * ## What a "reading" is
 *
 * `tick.ping` is `NetworkPlayerInfo.responseTime`, which the server refreshes every few seconds
 * and the sensor repeats on every tick in between. So a reading is a **change** of value, not a
 * tick: twenty identical figures a second are one measurement said twenty times, and averaging
 * them would weight whichever reading happened to last longest. A genuinely repeated latency
 * collapses into its predecessor, which costs one sample in a window of ten and is the price of
 * not having a sequence number on the wire. `-1` (unknown — singleplayer, or before the player
 * list arrives) is not a latency and never enters the history.
 */

/** Most readings the store keeps — the top of `ping.window`'s range. */
export const PING_HISTORY = 60;

/**
 * Append a reading if it is one: a real latency (≥ 0) that differs from the last one kept.
 * Mutates and returns whether it was taken, so the store knows whether to republish.
 */
export function pushPingReading(ring: number[], value: number, cap = PING_HISTORY): boolean {
  if (!Number.isFinite(value) || value < 0) return false;
  if (ring.length > 0 && ring[ring.length - 1] === value) return false;
  ring.push(value);
  if (ring.length > cap) ring.splice(0, ring.length - cap);
  return true;
}

/** What the chip draws beside the live figure. */
export interface PingStats {
  /** Mean of the last `window` readings, the latest included. */
  average: number;
  /** Mean absolute change between consecutive readings inside the window. 0 with one reading. */
  jitter: number;
  /** The latest reading sits more than `spikeMs` above the mean of the readings before it. */
  spike: boolean;
  /** How many readings the figures above were taken over. */
  count: number;
}

/** The fewest earlier readings a spike is judged against. One or two is a guess, not a baseline. */
export const SPIKE_BASELINE_MIN = 3;

/**
 * Average, jitter and spike over the last `window` readings. Null with no readings at all.
 *
 * The spike baseline **excludes** the latest reading: judged against an average it is part of,
 * a spike drags its own threshold up and a 200 ms jump in a window of three would hide itself.
 * The baseline is the `window` readings before it, so the slider shapes the spike test exactly
 * as it shapes the two figures the chip prints.
 */
export function pingStats(
  readings: readonly number[],
  window: number,
  spikeMs: number,
): PingStats | null {
  if (readings.length === 0) return null;
  const size = Math.max(1, Math.round(window));
  const recent = readings.slice(-size);
  const average = mean(recent);

  let jitter = 0;
  if (recent.length > 1) {
    let total = 0;
    for (let i = 1; i < recent.length; i += 1) total += Math.abs(recent[i]! - recent[i - 1]!);
    jitter = total / (recent.length - 1);
  }

  const latest = readings[readings.length - 1]!;
  const baseline = readings.slice(-(size + 1), -1);
  const spike =
    spikeMs > 0 && baseline.length >= SPIKE_BASELINE_MIN && latest > mean(baseline) + spikeMs;

  return { average, jitter, spike, count: recent.length };
}

function mean(values: readonly number[]): number {
  let total = 0;
  for (const value of values) total += value;
  return values.length > 0 ? total / values.length : 0;
}
