/**
 * The interactive components: Toggle, Slider, FilterTabs and the KeybindChip capture
 * flow. These are the four places where `@void/ui` owns behaviour rather than just
 * paint, so they are the four places a regression would be invisible in the gallery.
 */
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';

import { FilterTabs, KeybindChip, Slider, Toggle } from '../src/index.js';

/* -------------------------------------------------------------------------- */
/* Toggle                                                                     */
/* -------------------------------------------------------------------------- */

describe('Toggle', () => {
  it('exposes itself as a switch with its checked state', () => {
    render(<Toggle checked label="Keystrokes enabled" />);
    const toggle = screen.getByRole('switch', { name: 'Keystrokes enabled' });
    expect(toggle).toHaveAttribute('aria-checked', 'true');
  });

  it('asks for the opposite state when clicked', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<Toggle checked={false} onChange={onChange} label="Fullbright" />);
    await user.click(screen.getByRole('switch'));
    expect(onChange).toHaveBeenCalledWith(true);
  });

  it('is controlled — it does not flip itself', async () => {
    const user = userEvent.setup();
    render(<Toggle checked={false} label="Fullbright" />);
    const toggle = screen.getByRole('switch');
    await user.click(toggle);
    expect(toggle).toHaveAttribute('aria-checked', 'false');
  });

  it('toggles from the keyboard, as a button does', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<Toggle checked onChange={onChange} label="Show CPS" />);
    await user.tab();
    await user.keyboard('{Enter}');
    expect(onChange).toHaveBeenCalledWith(false);
  });

  it('does not fire when disabled', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<Toggle checked disabled onChange={onChange} label="Locked" />);
    await user.click(screen.getByRole('switch'));
    expect(onChange).not.toHaveBeenCalled();
  });

  it('carries the size modifier for each of the three switch sizes', () => {
    const { container } = render(
      <>
        <Toggle checked size="s" label="s" />
        <Toggle checked size="m" label="m" />
        <Toggle checked size="l" label="l" />
      </>,
    );
    const classes = [...container.querySelectorAll('.v-toggle')].map((el) => el.className);
    expect(classes[0]).not.toMatch(/v-toggle--[ml]/);
    expect(classes[1]).toMatch(/v-toggle--m/);
    expect(classes[2]).toMatch(/v-toggle--l/);
  });
});

/* -------------------------------------------------------------------------- */
/* Slider                                                                     */
/* -------------------------------------------------------------------------- */

describe('Slider', () => {
  it('reports its value through the slider role', () => {
    render(<Slider value={0.85} min={0} max={1} label="Opacity" readout="85%" />);
    const slider = screen.getByRole('slider', { name: 'Opacity' });
    expect(slider).toHaveAttribute('aria-valuenow', '0.85');
    expect(slider).toHaveAttribute('aria-valuemin', '0');
    expect(slider).toHaveAttribute('aria-valuemax', '1');
    expect(slider).toHaveAttribute('aria-valuetext', '85%');
  });

  it('shows the value readout beside the label', () => {
    render(<Slider value={1} min={0.25} max={4} label="Scale" readout="1.0×" />);
    expect(screen.getByText('Scale')).toBeInTheDocument();
    expect(screen.getByText('1.0×')).toBeInTheDocument();
  });

  it('steps up and down with the arrow keys', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(
      <Slider value={0.5} min={0} max={1} step={0.1} onChange={onChange} ariaLabel="Opacity" />,
    );
    const slider = screen.getByRole('slider');
    slider.focus();
    await user.keyboard('{ArrowRight}');
    expect(onChange).toHaveBeenLastCalledWith(0.6);
    await user.keyboard('{ArrowLeft}');
    expect(onChange).toHaveBeenLastCalledWith(0.4);
  });

  it('jumps to the bounds with Home and End', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(
      <Slider value={0.5} min={0.25} max={4} step={0.25} onChange={onChange} ariaLabel="Scale" />,
    );
    const slider = screen.getByRole('slider');
    slider.focus();
    await user.keyboard('{Home}');
    expect(onChange).toHaveBeenLastCalledWith(0.25);
    await user.keyboard('{End}');
    expect(onChange).toHaveBeenLastCalledWith(4);
  });

  it('commits once on a keyboard step, so the bridge write is not spammed', async () => {
    const user = userEvent.setup();
    const onCommit = vi.fn();
    render(
      <Slider value={0.5} min={0} max={1} step={0.1} onCommit={onCommit} ariaLabel="Opacity" />,
    );
    screen.getByRole('slider').focus();
    await user.keyboard('{ArrowRight}');
    expect(onCommit).toHaveBeenCalledTimes(1);
  });

  it('clamps at the bounds rather than running past them', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<Slider value={1} min={0} max={1} step={0.1} onChange={onChange} ariaLabel="Opacity" />);
    screen.getByRole('slider').focus();
    await user.keyboard('{ArrowRight}');
    expect(onChange).toHaveBeenLastCalledWith(1);
  });

  it('paints the fill and the thumb at the value fraction', () => {
    const { container } = render(<Slider value={0.25} min={0} max={1} ariaLabel="Opacity" />);
    expect(container.querySelector<HTMLElement>('.v-slider__fill')?.style.width).toBe('25%');
    expect(container.querySelector<HTMLElement>('.v-slider__thumb')?.style.left).toBe('25%');
  });

  it('is inert and unfocusable when disabled', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<Slider value={1} min={0} max={4} disabled onChange={onChange} ariaLabel="Scale" />);
    const slider = screen.getByRole('slider');
    expect(slider).toHaveAttribute('tabindex', '-1');
    slider.focus();
    await user.keyboard('{ArrowRight}');
    expect(onChange).not.toHaveBeenCalled();
  });

  it('drives a controlled value end to end', async () => {
    const user = userEvent.setup();
    function Harness() {
      const [value, setValue] = useState(0.5);
      return (
        <Slider
          value={value}
          onChange={setValue}
          min={0}
          max={1}
          step={0.05}
          label="Opacity"
          readout={`${Math.round(value * 100)}%`}
        />
      );
    }
    render(<Harness />);
    screen.getByRole('slider').focus();
    await user.keyboard('{ArrowRight}{ArrowRight}');
    expect(screen.getByText('60%')).toBeInTheDocument();
  });
});

/* -------------------------------------------------------------------------- */
/* FilterTabs                                                                 */
/* -------------------------------------------------------------------------- */

const MOD_TABS = [
  { id: 'all', label: 'All' },
  { id: 'hud', label: 'HUD' },
  { id: 'pvp', label: 'PvP' },
  { id: 'visual', label: 'Visual' },
  { id: 'utility', label: 'Utility' },
] as const;

describe('FilterTabs', () => {
  it('renders a tablist with exactly one selected tab', () => {
    render(<FilterTabs tabs={MOD_TABS} value="all" />);
    const tabs = within(screen.getByRole('tablist')).getAllByRole('tab');
    expect(tabs).toHaveLength(5);
    expect(tabs.filter((tab) => tab.getAttribute('aria-selected') === 'true')).toHaveLength(1);
    expect(screen.getByRole('tab', { selected: true })).toHaveTextContent('All');
  });

  it('reports the tab that was clicked', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<FilterTabs tabs={MOD_TABS} value="all" onChange={onChange} />);
    await user.click(screen.getByRole('tab', { name: 'Visual' }));
    expect(onChange).toHaveBeenCalledWith('visual');
  });

  it('moves the selection with the arrow keys and wraps around', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<FilterTabs tabs={MOD_TABS} value="all" onChange={onChange} />);
    screen.getByRole('tab', { selected: true }).focus();
    await user.keyboard('{ArrowRight}');
    expect(onChange).toHaveBeenLastCalledWith('hud');
    await user.keyboard('{ArrowLeft}');
    expect(onChange).toHaveBeenLastCalledWith('utility'); // wraps from the first tab
  });

  it('jumps to the ends with Home and End', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<FilterTabs tabs={MOD_TABS} value="pvp" onChange={onChange} />);
    screen.getByRole('tab', { selected: true }).focus();
    await user.keyboard('{End}');
    expect(onChange).toHaveBeenLastCalledWith('utility');
    await user.keyboard('{Home}');
    expect(onChange).toHaveBeenLastCalledWith('all');
  });

  it('keeps only the selected tab in the tab order', () => {
    render(<FilterTabs tabs={MOD_TABS} value="pvp" />);
    const focusable = screen
      .getAllByRole('tab')
      .filter((tab) => tab.getAttribute('tabindex') === '0');
    expect(focusable).toHaveLength(1);
    expect(focusable[0]).toHaveTextContent('PvP');
  });

  it('renders counts, and tints the Requests count green', () => {
    const { container } = render(
      <FilterTabs
        value="online"
        tabs={[
          { id: 'online', label: 'Online', count: 3 },
          { id: 'all', label: 'All', count: 8 },
          { id: 'requests', label: 'Requests', count: 2, countTone: 'ok' },
        ]}
      />,
    );
    expect(screen.getByRole('tab', { name: /Requests/ })).toHaveTextContent('2');
    expect(container.querySelectorAll('.v-tab__count--ok')).toHaveLength(1);
  });
});

/* -------------------------------------------------------------------------- */
/* KeybindChip                                                                */
/* -------------------------------------------------------------------------- */

describe('KeybindChip — the capture flow', () => {
  it('shows the bound key when idle', () => {
    render(<KeybindChip value="R-Shift" onCapture={() => Promise.resolve(null)} />);
    expect(screen.getByRole('button')).toHaveTextContent('R-Shift');
  });

  it('is disabled when there is nothing to capture with', () => {
    render(<KeybindChip value="R-Shift" />);
    expect(screen.getByRole('button')).toBeDisabled();
  });

  it('shows the prompt while the capture is open, then the applied key', async () => {
    const user = userEvent.setup();
    let resolveCapture: (key: string | null) => void = () => {};
    const onCapture = vi.fn(
      () =>
        new Promise<string | null>((resolve) => {
          resolveCapture = resolve;
        }),
    );
    const onChange = vi.fn();

    function Harness() {
      const [value, setValue] = useState('C');
      return (
        <KeybindChip
          value={value}
          onCapture={onCapture}
          onChange={(key) => {
            onChange(key);
            setValue(key);
          }}
        />
      );
    }
    render(<Harness />);

    await user.click(screen.getByRole('button'));
    expect(onCapture).toHaveBeenCalledOnce();
    expect(screen.getByRole('button')).toHaveTextContent('Press a key…');

    // Java hands back the key it actually captured; the chip binds to that, not to a
    // value it guessed.
    resolveCapture('V');
    expect(await screen.findByText('V')).toBeInTheDocument();
    expect(onChange).toHaveBeenCalledWith('V');
  });

  it('keeps the old binding when the player cancels with Escape', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(
      <KeybindChip value="R-Shift" onCapture={() => Promise.resolve(null)} onChange={onChange} />,
    );
    await user.click(screen.getByRole('button'));
    expect(await screen.findByText('R-Shift')).toBeInTheDocument();
    expect(onChange).not.toHaveBeenCalled();
  });

  it('does not start a second capture while one is open', async () => {
    const user = userEvent.setup();
    const onCapture = vi.fn(() => new Promise<string | null>(() => {}));
    render(<KeybindChip value="C" onCapture={onCapture} />);
    const chip = screen.getByRole('button');
    await user.click(chip);
    await user.click(chip);
    expect(onCapture).toHaveBeenCalledOnce();
  });

  it('carries the capturing modifier class while open', async () => {
    const user = userEvent.setup();
    const { container } = render(
      <KeybindChip value="C" onCapture={() => new Promise<string | null>(() => {})} />,
    );
    await user.click(screen.getByRole('button'));
    expect(container.querySelector('.v-keybind--capturing')).not.toBeNull();
  });
});
