/**
 * `StopwatchChip` and its formatter.
 *
 * Three things here are decisions rather than rendering, and all three are invisible in a
 * screenshot of a single frame — which is exactly why they are asserted:
 *
 * 1. **Running and stopped do not draw the same.** If they ever do, the mod cannot tell the
 *    player what state it is in, and no test that only checks the digits would notice.
 * 2. **`mmss` past an hour carries its minutes** rather than rolling over. A rollover is a
 *    correct-looking reading of the wrong duration, so the only place it can be caught is a
 *    test that names the hour.
 * 3. **The figure does not jitter.** The reading sits in `.v-hudchip__value` so that
 *    `01-base.css`'s `tnum` reaches it, and the formatter pads the fields whose width is
 *    known. Both are one edit away from being lost and neither shows up in a still frame.
 */
import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { StopwatchChip, formatElapsed } from '../src/index.js';

const SECOND = 1000;
const MINUTE = 60 * SECOND;
const HOUR = 60 * MINUTE;

/** The chip's figure — the span `tnum` and the mod's ink both key off. */
function figure(container: HTMLElement): HTMLElement {
  const node = container.querySelector('.v-hudchip__value');
  if (node === null) throw new Error('the chip drew no value');
  return node as HTMLElement;
}

/** The state marker — present in both states, so its tone is the whole signal. */
function dot(container: HTMLElement): HTMLElement {
  const node = container.querySelector('.v-dot');
  if (node === null) throw new Error('the chip drew no status dot');
  return node as HTMLElement;
}

/* -------------------------------------------------------------------------- */
/* formatElapsed                                                              */
/* -------------------------------------------------------------------------- */

describe('formatElapsed', () => {
  it('pads every field after the leading one, so a tick cannot change the width', () => {
    expect(formatElapsed(0)).toBe('0:00');
    expect(formatElapsed(9 * SECOND)).toBe('0:09');
    expect(formatElapsed(5 * MINUTE + 7 * SECOND)).toBe('5:07');
    expect(formatElapsed(12 * MINUTE + 7 * SECOND)).toBe('12:07');
  });

  it('leaves `auto`\u2019s leading minutes unpadded, like `formatPotionTime` beside it', () => {
    // The narrowest honest reading, which is what `auto` is for; `mmss` is the format that
    // buys a fixed width instead.
    expect(formatElapsed(4 * MINUTE + 7 * SECOND, 'auto')).toBe('4:07');
    expect(formatElapsed(4 * MINUTE + 7 * SECOND, 'mmss')).toBe('04:07');
  });

  it('truncates rather than rounds — an unfinished second has not elapsed', () => {
    expect(formatElapsed(999)).toBe('0:00');
    expect(formatElapsed(1999)).toBe('0:01');
  });

  it('holds a negative reading at zero', () => {
    expect(formatElapsed(-1)).toBe('0:00');
    expect(formatElapsed(-60 * SECOND)).toBe('0:00');
  });

  it('draws hundredths only when asked, and truncates those too', () => {
    expect(formatElapsed(1234, 'auto', true)).toBe('0:01.23');
    expect(formatElapsed(1239, 'auto', true)).toBe('0:01.23');
    expect(formatElapsed(1234)).toBe('0:01');
    expect(formatElapsed(90, 'auto', true)).toBe('0:00.09');
  });

  it('grows the hour under `auto` only once there is one', () => {
    expect(formatElapsed(59 * MINUTE + 59 * SECOND, 'auto')).toBe('59:59');
    expect(formatElapsed(HOUR, 'auto')).toBe('1:00:00');
    expect(formatElapsed(HOUR + 2 * MINUTE + 3 * SECOND, 'auto')).toBe('1:02:03');
  });

  it('always draws the hour under `hmmss`, including a zero one', () => {
    expect(formatElapsed(0, 'hmmss')).toBe('0:00:00');
    expect(formatElapsed(HOUR + 2 * MINUTE + 3 * SECOND, 'hmmss')).toBe('1:02:03');
  });

  it('carries the minutes past 59 under `mmss` rather than rolling over', () => {
    // The point of the whole format: `02:03` here would be a reading of a different
    // duration, and `59:59` would be a stopwatch that stopped being one.
    expect(formatElapsed(HOUR + 2 * MINUTE + 3 * SECOND, 'mmss')).toBe('62:03');
    expect(formatElapsed(2 * HOUR, 'mmss')).toBe('120:00');
    expect(formatElapsed(10 * HOUR + 30 * SECOND, 'mmss')).toBe('600:30');
  });

  it('leaves the hour unpadded, because hours have no known width', () => {
    expect(formatElapsed(9 * HOUR, 'hmmss')).toBe('9:00:00');
    expect(formatElapsed(12 * HOUR, 'hmmss')).toBe('12:00:00');
  });
});

/* -------------------------------------------------------------------------- */
/* StopwatchChip                                                              */
/* -------------------------------------------------------------------------- */

describe('StopwatchChip', () => {
  it('draws the elapsed reading', () => {
    const { container } = render(<StopwatchChip elapsedMs={4 * MINUTE + 31 * SECOND} />);
    expect(figure(container)).toHaveTextContent('4:31');
  });

  it('tells running and stopped apart', () => {
    const live = render(<StopwatchChip elapsedMs={1000} running />).container;
    const held = render(<StopwatchChip elapsedMs={1000} running={false} />).container;
    expect(dot(live)).toHaveClass('v-dot--ok');
    expect(dot(held)).not.toHaveClass('v-dot--ok');
    // The states must actually differ — the whole reason the dot is here.
    expect(dot(live).className).not.toBe(dot(held).className);
  });

  it('claims no state it was not given', () => {
    const { container } = render(<StopwatchChip elapsedMs={1000} />);
    expect(dot(container)).not.toHaveClass('v-dot--ok');
  });

  it('keeps the dot in both states, so pausing cannot reflow the chip', () => {
    const live = render(<StopwatchChip elapsedMs={1000} running />).container;
    const held = render(<StopwatchChip elapsedMs={1000} />).container;
    expect(live.querySelectorAll('.v-dot')).toHaveLength(1);
    expect(held.querySelectorAll('.v-dot')).toHaveLength(1);
  });

  it('puts the reading in the span `tnum` reaches', () => {
    // `.v-hudchip__value` is what `01-base.css` gives tabular figures to. A timer whose
    // digits are proportional is a timer whose width changes every tick.
    const { container } = render(<StopwatchChip elapsedMs={1000} showMillis />);
    expect(figure(container).textContent).toBe('0:01.00');
  });

  it('takes no wrapper class that could turn tabular figures back off', () => {
    // `.v-serverchip` exists to opt *out* of `tnum`; this chip must not acquire one of those
    // by accident. Its root is the plain chip.
    const { container } = render(<StopwatchChip elapsedMs={1000} />);
    expect(container.querySelector('.v-hudchip')?.className).toBe('v-hudchip');
  });

  it('carries the chip variants and the caller’s own class and attributes', () => {
    const { container } = render(
      <StopwatchChip elapsedMs={0} variant="editor" dimmed className="mine" data-testid="sw" />,
    );
    const chip = container.querySelector('.v-hudchip') as HTMLElement;
    expect(chip).toHaveClass('v-hudchip--editor');
    expect(chip).toHaveClass('v-hudchip--dimmed');
    expect(chip).toHaveClass('mine');
    expect(chip).toHaveAttribute('data-testid', 'sw');
  });

  it('passes `format` and `showMillis` through to the reading', () => {
    const mmss = render(<StopwatchChip elapsedMs={HOUR + 2 * MINUTE + 3 * SECOND} format="mmss" />);
    expect(figure(mmss.container)).toHaveTextContent('62:03');
    const hmmss = render(<StopwatchChip elapsedMs={0} format="hmmss" showMillis />);
    expect(figure(hmmss.container).textContent).toBe('0:00:00.00');
  });
});
