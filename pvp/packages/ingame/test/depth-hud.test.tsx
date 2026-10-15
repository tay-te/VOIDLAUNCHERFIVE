/**
 * The depth wave's HUD half: the pure derivations (ping statistics, the reach ring), their
 * ingestion through the store's `applyTick` — `counts`, `reach`, the ping history, and
 * `hits.swings` feeding the left CPS ring under `cps.ignore_cancelled` — and the widgets that
 * read them.
 *
 * Driven against the real fake bridge, like `store.test.ts`, with no clock running: every tick
 * here is one the test wrote.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, render } from '@testing-library/react';

import { connectBridge } from '@/bridge/connect';
import { resetDerivedState, useVoidStore } from '@/store/store';
import type { KeysPayload, TickPayload } from '@/bridge/protocol';
import { PING_HISTORY, pingStats, pushPingReading } from '@/hud/ping-stats';
import { REACH_HISTORY, pushReach, reachAverage, type ReachReading } from '@/hud/reach-stats';
import { HudItemCounter } from '@/hud/item_counter';
import { HudPotionCounter } from '@/hud/potion_counter';
import { HudReach } from '@/hud/reach';
import { HudPing, HudPotionEffects, effectBlinks, effectHidden } from '@/hud/widgets';
import { FLAG_SETTINGS, hasFlag, modProperties, toggleFlag } from '@/menu/ModSettingsScreen';
import { modSettings } from '@/store/store';

let dispose: () => void;

function keys(patch: Partial<KeysPayload>): KeysPayload {
  return { w: 0, a: 0, s: 0, d: 0, lmb: 0, rmb: 0, space: 0, shift: 0, ...patch };
}

function tick(payload: TickPayload): void {
  act(() => useVoidStore.getState().applyTick(payload));
}

beforeEach(() => {
  resetDerivedState();
  ({ dispose } = connectBridge({ forceFake: true, runFakeClock: false }));
  useVoidStore.setState({
    keys: keys({}),
    ping: -1,
    cpsLeft: 0,
    cpsRight: 0,
    cpsPeak: 0,
    counts: null,
    reach: null,
    pingHistory: [],
    heldCount: null,
    fx: [],
    menuOpen: false,
    route: { name: 'mods' },
  });
});

afterEach(() => {
  cleanup();
  dispose();
  vi.useRealTimers();
});

/* -------------------------------------------------------------------------- */

describe('ping statistics', () => {
  it('takes only distinct, real readings', () => {
    const ring: number[] = [];
    expect(pushPingReading(ring, 42)).toBe(true);
    expect(pushPingReading(ring, 42)).toBe(false); // the same reading repeated by the tick
    expect(pushPingReading(ring, -1)).toBe(false); // unknown is not a latency
    expect(pushPingReading(ring, 50)).toBe(true);
    expect(ring).toEqual([42, 50]);
  });

  it('is bounded', () => {
    const ring: number[] = [];
    for (let i = 0; i < PING_HISTORY + 15; i += 1) pushPingReading(ring, i);
    expect(ring).toHaveLength(PING_HISTORY);
    expect(ring[0]).toBe(15);
  });

  it('averages and measures jitter over the last `window` readings', () => {
    const stats = pingStats([100, 40, 50, 40, 50], 4, 0)!;
    expect(stats.count).toBe(4);
    expect(stats.average).toBe(45);
    expect(stats.jitter).toBe(10); // |50-40| + |40-50| + |50-40|, over 3
    expect(pingStats([], 10, 80)).toBeNull();
    expect(pingStats([60], 10, 80)!.jitter).toBe(0);
  });

  it('flags a spike against the readings before it, not including it', () => {
    const steady = [50, 52, 48, 51];
    expect(pingStats([...steady, 160], 10, 80)!.spike).toBe(true);
    expect(pingStats([...steady, 120], 10, 80)!.spike).toBe(false);
    // 0 disables detection.
    expect(pingStats([...steady, 400], 10, 0)!.spike).toBe(false);
    // Too few earlier readings is a guess, not a baseline.
    expect(pingStats([50, 400], 10, 80)!.spike).toBe(false);
  });
});

describe('the reach ring', () => {
  it('keys readings by `n`, so a repeated reading is one hit', () => {
    const ring: ReachReading[] = [];
    expect(pushReach(ring, { blocks: 2.5, n: 1 })).toBe(true);
    expect(pushReach(ring, { blocks: 2.5, n: 1 })).toBe(false);
    // Two hits at the same distance are two readings.
    expect(pushReach(ring, { blocks: 2.5, n: 2 })).toBe(true);
    expect(ring).toHaveLength(2);
  });

  it('keeps the last ten and averages them', () => {
    const ring: ReachReading[] = [];
    for (let n = 1; n <= REACH_HISTORY + 5; n += 1) pushReach(ring, { blocks: n, n });
    expect(ring).toHaveLength(REACH_HISTORY);
    expect(reachAverage(ring)).toBe(10.5); // 6..15
    expect(reachAverage([])).toBeNull();
  });

  it('drops the old session when `n` goes backwards', () => {
    const ring: ReachReading[] = [];
    pushReach(ring, { blocks: 3, n: 40 });
    pushReach(ring, { blocks: 3, n: 41 });
    expect(pushReach(ring, { blocks: 2, n: 1 })).toBe(true);
    expect(ring).toEqual([{ blocks: 2, n: 1 }]);
  });
});

/* -------------------------------------------------------------------------- */

describe('applyTick — the depth-wave readings', () => {
  it('replaces `counts` only when present and different by value', () => {
    tick({ counts: { ender_pearl: 8, health_potion: 0 } });
    const first = useVoidStore.getState().counts;
    expect(first).toEqual({ ender_pearl: 8, health_potion: 0 });
    // Absent means unchanged.
    tick({ fps: 120 });
    expect(useVoidStore.getState().counts).toBe(first);
    // A fresh but identical object is not a change.
    tick({ counts: { ender_pearl: 8, health_potion: 0 } });
    expect(useVoidStore.getState().counts).toBe(first);
    tick({ counts: { ender_pearl: 7, health_potion: 0 } });
    expect(useVoidStore.getState().counts?.ender_pearl).toBe(7);
  });

  it('publishes reach as last and average, and a repeated `n` does not count twice', () => {
    tick({ reach: { blocks: 3, n: 5 } });
    tick({ reach: { blocks: 2, n: 6 } });
    tick({ reach: { blocks: 2, n: 6 } }); // a reload re-pushing the last reading
    const reach = useVoidStore.getState().reach!;
    expect(reach.last).toBe(2);
    expect(reach.average).toBe(2.5);
    expect(reach.n).toBe(6);
    expect(reach.at).toBeGreaterThan(0);
  });

  it('keeps distinct ping readings as a history', () => {
    for (const ping of [40, 40, 40, 55, 55, -1, 61]) tick({ ping });
    expect(useVoidStore.getState().pingHistory).toEqual([40, 55, 61]);
  });

  it('ingests behind the menu and publishes when it closes', () => {
    // The hold is Ultralight-only; stamp the renderer the way `setRenderer` does in game.
    document.documentElement.setAttribute('data-renderer', 'ultralight');
    try {
      useVoidStore.setState({ menuOpen: true, route: { name: 'mods' } });
      tick({ counts: { soup: 4 }, reach: { blocks: 2.8, n: 1 } });
      expect(useVoidStore.getState().counts).toBeNull();
      expect(useVoidStore.getState().reach).toBeNull();
      useVoidStore.setState({ menuOpen: false });
      // The next tick carries neither — they were sent once, while the menu was up.
      tick({ fps: 100 });
      expect(useVoidStore.getState().counts).toEqual({ soup: 4 });
      expect(useVoidStore.getState().reach?.last).toBe(2.8);
    } finally {
      document.documentElement.removeAttribute('data-renderer');
    }
  });
});

describe('cps.ignore_cancelled — the left ring from `hits.swings`', () => {
  function click(): void {
    const apply = useVoidStore.getState().applyKeys;
    act(() => apply(keys({ lmb: 1 })));
    act(() => apply(keys({ lmb: 0 })));
  }

  it('counts mouse edges when the setting is off', () => {
    tick({ hits: { dealt: 0, taken: 0, swings: 10 } });
    click();
    click();
    tick({ hits: { dealt: 0, taken: 0, swings: 10 } });
    expect(useVoidStore.getState().cpsLeft).toBe(2);
  });

  it('counts swings, not presses, when it is on', () => {
    useVoidStore.getState().setSetting('cps', 'ignore_cancelled', true);
    tick({ hits: { dealt: 0, taken: 0, swings: 10 } }); // baseline — counts nothing
    expect(useVoidStore.getState().cpsLeft).toBe(0);
    // Three presses, one of them swallowed by the miss delay: two swings.
    click();
    click();
    click();
    tick({ hits: { dealt: 0, taken: 0, swings: 12 } });
    expect(useVoidStore.getState().cpsLeft).toBe(2);
    // The right button is always the edge.
    const apply = useVoidStore.getState().applyKeys;
    act(() => apply(keys({ rmb: 1 })));
    expect(useVoidStore.getState().cpsRight).toBe(1);
  });

  it('falls back to edges until the sensor has sent a swing count', () => {
    useVoidStore.getState().setSetting('cps', 'ignore_cancelled', true);
    click();
    tick({ hits: { dealt: 0, taken: 0 } });
    expect(useVoidStore.getState().cpsLeft).toBe(1);
  });

  it('re-baselines when the counter goes backwards', () => {
    useVoidStore.getState().setSetting('cps', 'ignore_cancelled', true);
    tick({ hits: { dealt: 0, taken: 0, swings: 500 } });
    tick({ hits: { dealt: 0, taken: 0, swings: 3 } }); // new session
    expect(useVoidStore.getState().cpsLeft).toBe(0);
    tick({ hits: { dealt: 0, taken: 0, swings: 4 } });
    expect(useVoidStore.getState().cpsLeft).toBe(1);
  });
});

/* -------------------------------------------------------------------------- */

describe('the widgets', () => {
  it('Item counter reads `counts[track]`, and draws 0 as a reading', () => {
    useVoidStore.getState().setSetting('item_counter', 'track', 'ender_pearl');
    tick({ held_count: 12, counts: { ender_pearl: 0 } });
    const { container } = render(<HudItemCounter />);
    expect(container.textContent).toBe('x0');
  });

  it('Item counter keeps the held stack for `held_stack`, and draws nothing for an empty hand', () => {
    tick({ held_count: 12, counts: { held_total: 40 } });
    const { container, rerender } = render(<HudItemCounter />);
    expect(container.textContent).toBe('x12');
    act(() => useVoidStore.getState().setSetting('item_counter', 'track', 'held_total'));
    rerender(<HudItemCounter />);
    expect(container.textContent).toBe('x40');
    // `held_total` is absent with an empty hand.
    tick({ counts: { ender_pearl: 3 } });
    rerender(<HudItemCounter />);
    expect(container.textContent).toBe('');
  });

  it('Potion counter tracks pots, soup or both, and warns at the threshold', () => {
    tick({ counts: { health_potion: 2, soup: 9 } });
    const { container, rerender } = render(<HudPotionCounter />);
    expect(container.textContent).toBe('2pots');
    expect(container.querySelector('.v-hudchip__value--warn')?.textContent).toBe('2');
    act(() => useVoidStore.getState().setSetting('potion_counter', 'track', 'both'));
    act(() => useVoidStore.getState().setSetting('potion_counter', 'show_label', false));
    rerender(<HudPotionCounter />);
    // `both` keeps its units whatever `show_label` says.
    expect(container.textContent).toBe('2pots|9soup');
    expect(container.querySelectorAll('.v-hudchip__value--warn')).toHaveLength(1);
  });

  it('Reach prints the last hit or the average, to the chosen decimals', () => {
    tick({ reach: { blocks: 3, n: 1 } });
    tick({ reach: { blocks: 2.5, n: 2 } });
    const { container, rerender } = render(<HudReach />);
    expect(container.querySelector('.v-hudchip__value')?.textContent).toBe('2.50');
    act(() => useVoidStore.getState().setSetting('reach', 'mode', 'average'));
    act(() => useVoidStore.getState().setSetting('reach', 'decimals', 1));
    rerender(<HudReach />);
    expect(container.querySelector('.v-hudchip__value')?.textContent).toBe('2.8');
  });

  it('Reach hides itself `hide_after_ms` after the last hit', () => {
    vi.useFakeTimers();
    vi.setSystemTime(1_000_000);
    act(() => useVoidStore.getState().setSetting('reach', 'hide_after_ms', 2000));
    tick({ reach: { blocks: 3, n: 1 } });
    const { container } = render(<HudReach />);
    expect(container.querySelector('.v-hudchip__bar--rule')).not.toBeNull();
    act(() => {
      vi.advanceTimersByTime(2100);
    });
    expect(container.textContent).toBe('');
  });

  it('Ping draws the average, the jitter with its warn, and a spike mark', () => {
    act(() => {
      const set = useVoidStore.getState().setSetting;
      set('ping', 'show_average', true);
      set('ping', 'show_jitter', true);
      set('ping', 'jitter_warn_ms', 5);
    });
    for (const ping of [50, 60, 50, 60, 200]) tick({ ping });
    const { container } = render(<HudPing />);
    const asides = [...container.querySelectorAll('.v-hudchip__aside')].map((el) => el.textContent);
    expect(asides[0]).toContain('avg 84');
    expect(asides[1]).toContain('±43');
    expect(container.querySelector('.v-hudchip__aside--warn')).not.toBeNull();
    expect(container.querySelector('.v-hudchip__spike')).not.toBeNull();
    // In game the mark is the brief, self-fading form.
    expect(container.querySelector('.v-hudchip__spike--held')).toBeNull();
  });

  it('Potion effects hides masked effects and blinks on the odd seconds under the threshold', () => {
    useVoidStore.setState({
      fx: [
        { id: 1, amplifier: 0, duration_ms: 60_000 },
        { id: 16, amplifier: 0, duration_ms: 3_000 },
        { id: 10, amplifier: 0, duration_ms: 4_000 },
      ],
    });
    const { container, rerender } = render(<HudPotionEffects />);
    const rows = () => [...container.querySelectorAll('.v-potionlist__row')];
    expect(rows()).toHaveLength(3);
    // 3 s left is odd and under the factory 5: dim. 4 s is the bright beat.
    expect(rows()[1]!.classList.contains('v-potionlist__row--dim')).toBe(true);
    expect(rows()[2]!.classList.contains('v-potionlist__row--dim')).toBe(false);
    act(() => useVoidStore.getState().setSetting('potion_effects', 'hidden_effects', 2 ** 16));
    rerender(<HudPotionEffects />);
    expect(rows()).toHaveLength(2);
    expect(container.textContent).not.toContain('Night vision');
  });

  it('blink and mask helpers', () => {
    expect(effectBlinks(5_000, 5)).toBe(true);
    expect(effectBlinks(6_000, 5)).toBe(false);
    expect(effectBlinks(5_000, 0)).toBe(false);
    expect(effectBlinks(0, 5)).toBe(false);
    expect(effectHidden(0b10, 1)).toBe(true);
    expect(effectHidden(2 ** 23, 23)).toBe(true);
    expect(effectHidden(0, 23)).toBe(false);
  });
});

describe('the settings page', () => {
  it('draws `hidden_effects` as the effect chips, not a meter', () => {
    const settings = modSettings(useVoidStore.getState().loadout, 'potion_effects');
    const property = modProperties('potion_effects', settings).find((p) => p.key === 'hidden_effects')!;
    expect(property.kind).toBe('flags');
    const flags = FLAG_SETTINGS['potion_effects.hidden_effects']!;
    expect(flags).toHaveLength(23);
    expect(flags[0]).toEqual({ bit: 1, label: 'Speed' });
    expect(flags[15]).toEqual({ bit: 16, label: 'Night vision' });
  });

  it('toggles bits past 2^31 without sign trouble', () => {
    expect(toggleFlag(0, 23)).toBe(2 ** 23);
    expect(toggleFlag(2 ** 23 + 2, 23)).toBe(2);
    expect(hasFlag(2 ** 23 + 2, 1)).toBe(true);
    expect(hasFlag(2 ** 23 + 2, 2)).toBe(false);
  });
});
