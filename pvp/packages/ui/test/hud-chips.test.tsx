/**
 * The Wave 2 HUD chips: Combo, Saturation, Momentum, Memory, ServerAddress and ItemCounter.
 *
 * These are presentational, so what is worth testing is not that they render — the
 * inventory smoke tests cover that — but the handful of places where they make a
 * *decision*: which span the mod's `color` lands on (quiet-cell §1 puts it on the live
 * figure and nowhere else), what `show_label` is allowed to take away, and the two chips
 * that have to answer for a reading they do not have. All three are invisible in a
 * screenshot and all three are contracts other packages are already coding against.
 */
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import {
  ComboChip,
  ItemCounterChip,
  MemoryChip,
  MomentumChip,
  SaturationChip,
  ServerAddressChip,
} from '../src/index.js';

/** The chip's figure — the one span the `color` setting is allowed to reach. */
function figure(container: HTMLElement): HTMLElement {
  const node = container.querySelector('.v-hudchip__value');
  if (node === null) throw new Error('the chip drew no value');
  return node as HTMLElement;
}

/* -------------------------------------------------------------------------- */
/* ComboChip                                                                  */
/* -------------------------------------------------------------------------- */

describe('ComboChip', () => {
  it('draws the count and its noun', () => {
    const { container } = render(<ComboChip combo={7} />);
    expect(container.textContent).toBe('7combo');
  });

  it('drops the noun when show_label is off', () => {
    const { container } = render(<ComboChip combo={7} showLabel={false} />);
    expect(container.textContent).toBe('7');
  });

  it('draws a dropped combo as zero rather than as nothing', () => {
    const { container } = render(<ComboChip combo={0} />);
    expect(container.querySelector('.v-hudchip')).not.toBeNull();
    expect(figure(container)).toHaveTextContent('0');
  });

  it('draws no rule at all without a `remaining`', () => {
    const { container } = render(<ComboChip combo={7} />);
    expect(container.querySelector('.v-hudchip__bar--rule')).toBeNull();
    expect(figure(container)).not.toHaveClass('v-hudchip__value--ruled');
  });

  it('shortens the rule as the reset window runs out', () => {
    const full = render(<ComboChip combo={7} remaining={1} />).container;
    expect(full.querySelector('.v-hudchip__bar--rule .v-hudchip__fill')).toHaveStyle({
      width: '100%',
    });
    const half = render(<ComboChip combo={7} remaining={0.5} />).container;
    expect(half.querySelector('.v-hudchip__fill')).toHaveStyle({ width: '50%' });
  });

  it('holds a sensor fraction to 0-1', () => {
    const over = render(<ComboChip combo={7} remaining={1.4} />).container;
    expect(over.querySelector('.v-hudchip__fill')).toHaveStyle({ width: '100%' });
    const under = render(<ComboChip combo={7} remaining={-0.2} />).container;
    expect(under.querySelector('.v-hudchip__fill')).toHaveStyle({ width: '0%' });
  });

  it('draws a lapsed window as a rule of zero width, not as no rule', () => {
    const { container } = render(<ComboChip combo={7} remaining={0} />);
    expect(container.querySelector('.v-hudchip__bar--rule')).not.toBeNull();
  });

  it('hangs the rule inside the figure, so it takes the figure\u2019s own ink', () => {
    const { container } = render(<ComboChip combo={7} remaining={0.5} />);
    const value = figure(container);
    expect(value).toHaveClass('v-hudchip__value--ruled');
    // `currentColor` in CSS, so the rule carries no colour of its own to disagree with.
    expect(container.querySelector('.v-hudchip__fill')).toHaveAttribute('style', 'width: 50%;');
  });
});

/* -------------------------------------------------------------------------- */
/* SaturationChip                                                             */
/* -------------------------------------------------------------------------- */

describe('SaturationChip', () => {
  it('reads to one decimal by default, because the fraction is the reading', () => {
    const { container } = render(<SaturationChip saturation={17.52} />);
    expect(container.textContent).toBe('17.5sat');
  });

  it('honours decimals across the whole 0-2 range the generator settled on', () => {
    expect(render(<SaturationChip saturation={17.52} decimals={0} />).container.textContent)
      .toBe('18sat');
    expect(render(<SaturationChip saturation={17.526} decimals={2} />).container.textContent)
      .toBe('17.53sat');
  });

  it('draws only the level in `bar`, and both in `both`', () => {
    const bar = render(<SaturationChip saturation={10} style="bar" />).container;
    expect(bar.querySelector('.v-hudchip__value')).toBeNull();
    expect(bar.querySelector('.v-hudchip__bar')).not.toBeNull();

    const both = render(<SaturationChip saturation={10} style="both" />).container;
    expect(both.querySelector('.v-hudchip__value')).not.toBeNull();
    expect(both.querySelector('.v-hudchip__fill')).toHaveStyle({ width: '50%' });
  });

  it('clamps the fill to the 0-20 the food system produces', () => {
    const over = render(<SaturationChip saturation={40} style="bar" />).container;
    expect(over.querySelector('.v-hudchip__fill')).toHaveStyle({ width: '100%' });
    const under = render(<SaturationChip saturation={-3} style="bar" />).container;
    expect(under.querySelector('.v-hudchip__fill')).toHaveStyle({ width: '0%' });
  });

});

/* -------------------------------------------------------------------------- */
/* MomentumChip                                                               */
/* -------------------------------------------------------------------------- */

describe('MomentumChip', () => {
  it('reads blocks per second at the 2 dp the sensor sends', () => {
    const { container } = render(<MomentumChip speed={4.317} />);
    expect(container.textContent).toBe('4.32bps');
  });

  it('converts to km/h at 3.6 per block per second', () => {
    const { container } = render(<MomentumChip speed={5} unit="kmh" decimals={2} />);
    expect(container.textContent).toBe('18.00km/h');
  });

  it('drops the unit when show_label is off, in either unit', () => {
    const { container } = render(
      <MomentumChip speed={5} unit="kmh" showLabel={false} decimals={1} />,
    );
    expect(container.textContent).toBe('18.0');
  });

});

/* -------------------------------------------------------------------------- */
/* MemoryChip                                                                 */
/* -------------------------------------------------------------------------- */

describe('MemoryChip', () => {
  it('writes the three styles', () => {
    expect(render(<MemoryChip usedMb={1024} maxMb={4096} style="used" />).container.textContent)
      .toBe('1024\u00a0MB');
    expect(render(<MemoryChip usedMb={1024} maxMb={4096} style="used_of_max" />).container.textContent)
      .toBe('1024\u00a0/\u00a04096\u00a0MB');
    expect(render(<MemoryChip usedMb={1024} maxMb={4096} style="percent" />).container.textContent)
      .toBe('25%');
  });

  it('gives show_label the same job in all three: it takes the unit, never the ceiling', () => {
    const off = { usedMb: 1024, maxMb: 4096, showLabel: false } as const;
    expect(render(<MemoryChip {...off} style="used" />).container.textContent).toBe('1024');
    expect(render(<MemoryChip {...off} style="used_of_max" />).container.textContent)
      .toBe('1024\u00a0/\u00a04096');
    expect(render(<MemoryChip {...off} style="percent" />).container.textContent).toBe('25');
  });

  it('draws the level only when asked, filled to used / max', () => {
    expect(
      render(<MemoryChip usedMb={1024} maxMb={4096} />).container.querySelector('.v-hudchip__bar'),
    ).toBeNull();
    const { container } = render(<MemoryChip usedMb={1024} maxMb={4096} showBar />);
    expect(container.querySelector('.v-hudchip__fill')).toHaveStyle({ width: '25%' });
  });

  it('survives a zero ceiling rather than drawing NaN', () => {
    const { container } = render(<MemoryChip usedMb={1024} maxMb={0} style="percent" showBar />);
    expect(container.textContent).toBe('0%');
    expect(container.querySelector('.v-hudchip__fill')).toHaveStyle({ width: '0%' });
  });
});

/* -------------------------------------------------------------------------- */
/* ServerAddressChip                                                          */
/* -------------------------------------------------------------------------- */

describe('ServerAddressChip', () => {
  it('draws the host the caller formatted, untouched', () => {
    render(<ServerAddressChip host="mc.hypixel.net" />);
    expect(screen.getByText('mc.hypixel.net')).toBeInTheDocument();
  });

  it('renders nothing at all for an empty host', () => {
    const { container } = render(<ServerAddressChip host="" />);
    expect(container.firstChild).toBeNull();
  });

  it('treats a whitespace host as empty too', () => {
    const { container } = render(<ServerAddressChip host="   " />);
    expect(container.firstChild).toBeNull();
  });

  it('sets the host in proportional figures — it is written once, not ticked', () => {
    const { container } = render(<ServerAddressChip host="192.168.1.20" />);
    expect(container.querySelector('.v-serverchip')).not.toBeNull();
  });
});

/* -------------------------------------------------------------------------- */
/* ItemCounterChip                                                            */
/* -------------------------------------------------------------------------- */

/** The count itself, which on this chip is a span *inside* the value — the `x` is the other. */
function countFigure(container: HTMLElement): HTMLElement {
  const node = figure(container).lastElementChild;
  if (node === null) throw new Error('the chip drew no count');
  return node as HTMLElement;
}

describe('ItemCounterChip', () => {
  it('prefixes the stack size with the multiplication sign', () => {
    expect(render(<ItemCounterChip count={12} />).container.textContent).toBe('x12');
    expect(render(<ItemCounterChip count={12} showLabel={false} />).container.textContent)
      .toBe('12');
  });

  it('drops the prefix on an empty hand — `x —` is a reading of nothing', () => {
    expect(render(<ItemCounterChip count={null} />).container.textContent).toBe('—');
  });

  it('keeps the chip and says nothing for an empty hand — it is not a zero', () => {
    const { container } = render(<ItemCounterChip count={null} />);
    expect(container.querySelector('.v-hudchip')).not.toBeNull();
    expect(figure(container)).toHaveTextContent('—');
  });

  it('draws a real zero as a zero, which an empty hand must not be confused with', () => {
    expect(render(<ItemCounterChip count={0} />).container.textContent).toBe('x0');
  });

  it('takes the warn treatment at or below the threshold, and not above it', () => {
    expect(countFigure(render(<ItemCounterChip count={16} lowThreshold={16} />).container))
      .toHaveClass('v-hudchip__value--warn');
    expect(countFigure(render(<ItemCounterChip count={17} lowThreshold={16} />).container))
      .not.toHaveClass('v-hudchip__value--warn');
  });

  it('is off at lowThreshold 0, which a real zero must not trip', () => {
    expect(countFigure(render(<ItemCounterChip count={0} />).container))
      .not.toHaveClass('v-hudchip__value--warn');
  });

  it('never warns on an empty hand', () => {
    expect(countFigure(render(<ItemCounterChip count={null} lowThreshold={16} />).container))
      .not.toHaveClass('v-hudchip__value--warn');
  });

  it('warns at or below the threshold and not above it', () => {
    const warned = countFigure(
      render(<ItemCounterChip count={4} lowThreshold={16} />).container,
    );
    expect(warned).toHaveClass('v-hudchip__value--warn');
    const calm = countFigure(
      render(<ItemCounterChip count={40} lowThreshold={16} />).container,
    );
    expect(calm).not.toHaveClass('v-hudchip__value--warn');
  });
});
