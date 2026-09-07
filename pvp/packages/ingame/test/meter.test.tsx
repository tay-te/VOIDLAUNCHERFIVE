/**
 * The meter's motion, which is CSS but is *driven* from here.
 *
 * `overlay.css` animates three things — the hue marker's `translateX`, each cell's fill, and
 * the §5.3 bleed — and every one of them reads a custom property this component writes. A
 * transition cannot be asserted in jsdom, but the numbers it transitions between can, and
 * getting one of them wrong is silent: the marker sits at cell 0 for the life of the process
 * and nothing throws.
 *
 * The same goes for the gesture (§5.3–5.4). What a press and a drag *report* is assertable
 * here; what the report then looks like is not. So these tests hold the two things that can
 * silently break — the value a given pointer x produces, and how often it produces one — and
 * the CSS that draws the result is checked in game.
 */

import { useState } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render } from '@testing-library/react';

import { CellMeter, cellIndex, cellValue } from '@/menu/CellMeter';

afterEach(cleanup);

function meter(props: Partial<React.ComponentProps<typeof CellMeter>> = {}) {
  const onChange = vi.fn();
  const { container, unmount } = render(
    <CellMeter
      label="Opacity"
      value={0.5}
      min={0}
      max={1}
      step={0.1}
      cells={10}
      onChange={onChange}
      {...props}
    />,
  );
  const track = container.querySelector('.meter__track') as HTMLElement;
  const cells = [...container.querySelectorAll('.meter__cell')] as HTMLElement[];
  return { container, track, cells, onChange, unmount };
}

/* -------------------------------------------------------------------------------------- */
/* Geometry. jsdom lays nothing out, so every rect it reports is 0×0 at (0,0) — and the      */
/* gesture works out the cell under the pointer from exactly those rects. These are the      */
/* `md` numbers from `overlay.css`: a 12px cell on §4's 17px step.                           */
/* -------------------------------------------------------------------------------------- */

const CELL = 12;
const PITCH = 17;
const LEFT = 100;

/** The client x of the centre of cell `i`. */
const centre = (i: number): number => LEFT + i * PITCH + CELL / 2;

/** Gives the cells the layout jsdom will not. */
function layout(cells: HTMLElement[]): void {
  cells.forEach((cell, i) => {
    const left = LEFT + i * PITCH;
    cell.getBoundingClientRect = () =>
      ({
        x: left,
        y: 0,
        left,
        right: left + CELL,
        top: 0,
        bottom: CELL,
        width: CELL,
        height: CELL,
        toJSON: () => ({}),
      }) as DOMRect;
  });
}

/** Where the pointer is, so that a release carries a position the way a real one does. */
let pointerX = 0;

/** A press on the track at a client x, the way a mouse delivers one. */
function press(track: HTMLElement, clientX: number): void {
  pointerX = clientX;
  fireEvent.mouseDown(track, { clientX, button: 0 });
}

/** A move at the tick rate: one event, however far the pointer travelled. */
function drag(clientX: number): void {
  pointerX = clientX;
  fireEvent.mouseMove(window, { clientX });
}

function release(clientX: number = pointerX): void {
  pointerX = clientX;
  fireEvent.mouseUp(window, { clientX });
}

describe('CellMeter', () => {
  it('puts the live index on the track, which is what the marker translates by', () => {
    const { track } = meter({ value: 0.5 });
    // 0.5 of 0..1 over ten cells is the fifth step.
    expect(cellIndex(0.5, 10, 0, 1)).toBe(5);
    expect(track.style.getPropertyValue('--live')).toBe('5');
  });

  it('draws exactly one marker, over the cells rather than among them', () => {
    const { container, track } = meter();
    const markers = container.querySelectorAll('.meter__live');
    expect(markers).toHaveLength(1);
    // Last, so it paints over the cell it sits on.
    expect(track.lastElementChild).toBe(markers[0]);
    expect(markers[0]!.getAttribute('aria-hidden')).toBe('true');
  });

  it('fills every cell up to and including the live one', () => {
    const { cells } = meter({ value: 0.5 });
    const filled = cells.map((c) => c.classList.contains('meter__cell--on'));
    expect(filled).toEqual([true, true, true, true, true, true, false, false, false, false]);
  });

  it('staggers by distance from the live cell, capped so a long jump still lands fast', () => {
    const { cells } = meter({ value: 0 });
    // d is |i - live|, and stops counting past 5: at 14ms a cell that is 70ms of lead-in.
    expect(cells.map((c) => c.style.getPropertyValue('--d'))).toEqual([
      '0', '1', '2', '3', '4', '5', '5', '5', '5', '5',
    ]);
  });

  it('bleeds the hue at §5.3 strengths and nowhere else', () => {
    const { cells } = meter({ value: 0.5 });
    // .95 on the live value is the marker's own opacity; ±1 is .40, ±2 is .16, then nothing.
    expect(cells.map((c) => c.style.getPropertyValue('--bleed'))).toEqual([
      '0', '0', '0', '0.16', '0.4', '0', '0.4', '0.16', '0', '0',
    ]);
  });

  it('still reports the cell that was clicked', () => {
    const { cells, onChange } = meter({ value: 0.5 });
    fireEvent.click(cells[1]!);
    expect(onChange).toHaveBeenCalledWith(cellValue(1, 10, 0, 1, 0.1));
  });

  it('draws no marker chrome a read-only meter cannot use, but still marks its value', () => {
    const { container, track } = meter({ onChange: undefined });
    expect(container.querySelectorAll('button.meter__cell')).toHaveLength(0);
    expect(track.style.getPropertyValue('--live')).toBe('5');
  });

  /* ---- the gesture, §5.3–5.4 --------------------------------------------------------- */

  it('reports the cell under the pointer on the press, before anything is released', () => {
    const { track, cells, onChange } = meter();
    layout(cells);
    press(track, centre(8));
    expect(onChange).toHaveBeenCalledTimes(1);
    expect(onChange).toHaveBeenCalledWith(cellValue(8, 10, 0, 1, 0.1));
  });

  it('counts the gaps between the cells as part of the nearest cell', () => {
    const { track, cells, onChange } = meter();
    layout(cells);
    // Ten past the centre of cell 2 is inside the 5px gap, and seven short of cell 3.
    press(track, centre(2) + 10);
    expect(onChange).toHaveBeenCalledWith(cellValue(3, 10, 0, 1, 0.1));
  });

  it('follows the pointer for as long as the button is held, and stops when it is not', () => {
    const { track, cells, onChange } = meter();
    layout(cells);
    press(track, centre(2));
    drag(centre(5));
    drag(centre(9));
    release();
    drag(centre(0));
    expect(onChange.mock.calls.map(([v]) => v)).toEqual([
      cellValue(2, 10, 0, 1, 0.1),
      cellValue(5, 10, 0, 1, 0.1),
      cellValue(9, 10, 0, 1, 0.1),
    ]);
  });

  it('lands where the button came up, even if no move ever reported that far', () => {
    // The release carries a position of its own, and at the tick rate it is often the only
    // event that carries the end of a quick flick.
    const { track, cells, onChange } = meter();
    layout(cells);
    press(track, centre(1));
    release(centre(7));
    expect(onChange.mock.calls.map(([v]) => v)).toEqual([
      cellValue(1, 10, 0, 1, 0.1),
      cellValue(7, 10, 0, 1, 0.1),
    ]);
    // And the release really did end it.
    drag(centre(0));
    expect(onChange).toHaveBeenCalledTimes(2);
  });

  it('lands where a fast drag ended, not one cell along from where it started', () => {
    // Input reaches the page at the menu's tick rate, coalesced to the newest position, so a
    // quick drag really does arrive as one move seven cells wide. Interpolating is the whole
    // reason this control does not need an event per cell.
    const { track, cells, onChange } = meter();
    layout(cells);
    press(track, centre(1));
    drag(centre(8));
    expect(onChange).toHaveBeenCalledTimes(2);
    expect(onChange).toHaveBeenLastCalledWith(cellValue(8, 10, 0, 1, 0.1));
  });

  it('says nothing while the pointer moves inside the cell it already set', () => {
    // The hard constraint: no render may come out of pointer movement. A report is the only
    // thing that renders, so a cell's worth of travel has to produce exactly one.
    const { track, cells, onChange } = meter();
    layout(cells);
    press(track, centre(4));
    drag(centre(4) + 6);
    drag(centre(4) - 6);
    drag(centre(4));
    expect(onChange).toHaveBeenCalledTimes(1);
  });

  it('clamps a drag that runs off either end to the track, and so to min and max', () => {
    const { track, cells, onChange } = meter();
    layout(cells);
    press(track, centre(5));
    drag(centre(0) - 400);
    drag(centre(9) + 400);
    // Still past the end. The clamp is on the cell and not only on the value, so travelling
    // further off the track is travelling inside the same cell: it reports nothing.
    drag(centre(9) + 800);
    expect(onChange.mock.calls.map(([v]) => v)).toEqual([cellValue(5, 10, 0, 1, 0.1), 0, 1]);
  });

  it('can only produce a value a click could — a drag snaps to the same step', () => {
    // Scale's own range: 0.25 to 4 in steps of 0.05 over ten cells, where the raw
    // `min + i/9 * (max - min)` lands between steps at nearly every cell.
    const { track, cells, onChange } = meter({ value: 1, min: 0.25, max: 4, step: 0.05, cells: 10 });
    layout(cells);
    press(track, centre(0));
    for (let i = 1; i < 10; i += 1) drag(centre(i));
    const reported = onChange.mock.calls.map(([v]) => v as number);
    expect(reported).toEqual([...Array(10)].map((_, i) => cellValue(i, 10, 0.25, 4, 0.05)));
    for (const value of reported) {
      expect(value).toBeGreaterThanOrEqual(0.25);
      expect(value).toBeLessThanOrEqual(4);
      // On the step, to the rounding `cellValue` already does.
      expect(Math.abs((value - 0.25) / 0.05 - Math.round((value - 0.25) / 0.05))).toBeLessThan(1e-9);
    }
  });

  it('moves the marker and the bleed with the pointer, which is what §5.3 asks for', () => {
    // The numbers the CSS animates: `--live` is the marker's translate, and `--bleed` is the
    // falloff around it. Both are centred on the value, and during a drag the value is the
    // cell under the pointer — so both travel with it.
    function Controlled(): React.ReactElement {
      const [value, setValue] = useState(0);
      return (
        <CellMeter
          label="Opacity"
          value={value}
          min={0}
          max={1}
          step={0.1}
          cells={10}
          onChange={setValue}
        />
      );
    }
    const { container } = render(<Controlled />);
    const track = container.querySelector('.meter__track') as HTMLElement;
    const cells = [...container.querySelectorAll('.meter__cell')] as HTMLElement[];
    layout(cells);

    press(track, centre(3));
    expect(track.style.getPropertyValue('--live')).toBe('3');
    drag(centre(7));
    expect(track.style.getPropertyValue('--live')).toBe('7');
    expect(
      [...container.querySelectorAll('.meter__cell')].map((c) =>
        (c as HTMLElement).style.getPropertyValue('--bleed'),
      ),
    ).toEqual(['0', '0', '0', '0', '0', '0.16', '0.4', '0', '0.4', '0.16']);
    release();
  });

  it('reports a mouse click once, not once for the press and once for the click', () => {
    const { track, cells, onChange } = meter();
    layout(cells);
    press(track, centre(3));
    release();
    // A real click carries a click count; the keyboard's synthesised one carries 0.
    fireEvent.click(cells[3]!, { detail: 1 });
    expect(onChange).toHaveBeenCalledTimes(1);
    expect(onChange).toHaveBeenCalledWith(cellValue(3, 10, 0, 1, 0.1));
  });

  it('still answers the keyboard, whose click never had a press in front of it', () => {
    const { track, cells, onChange } = meter();
    layout(cells);
    press(track, centre(3));
    release();
    // Enter or Space on a focused cell: a click with no click count and no mouse behind it.
    fireEvent.click(cells[6]!);
    expect(onChange).toHaveBeenLastCalledWith(cellValue(6, 10, 0, 1, 0.1));
  });

  it('reports the cell a press landed on even with no layout to measure', () => {
    // The fallback for a track that has no geometry — which is jsdom, and would be a
    // renderer that had not laid the menu out yet. It can still name the cell it hit.
    const { cells, onChange } = meter();
    fireEvent.mouseDown(cells[6]!, { clientX: 0, button: 0 });
    expect(onChange).toHaveBeenCalledWith(cellValue(6, 10, 0, 1, 0.1));
  });

  it('leaves the secondary button alone', () => {
    const { track, cells, onChange } = meter();
    layout(cells);
    fireEvent.mouseDown(track, { clientX: centre(8), button: 2 });
    drag(centre(2));
    expect(onChange).not.toHaveBeenCalled();
  });

  it('does not answer a press at all when it is a read-only meter', () => {
    const { track, cells, onChange } = meter({ onChange: undefined });
    layout(cells);
    press(track, centre(8));
    drag(centre(2));
    expect(onChange).not.toHaveBeenCalled();
  });

  it('takes its listeners with it when the menu closes mid-drag', () => {
    // They live on `window` for the length of a press, and the move stream behind them does
    // not stop when the overlay does. Asserted on the removal and not only on the silence:
    // an unmounted track nulls its ref, so the handler would go quiet either way.
    const detach = vi.spyOn(window, 'removeEventListener');
    const { track, cells, onChange, unmount } = meter();
    layout(cells);
    press(track, centre(2));
    unmount();
    expect(detach.mock.calls.map(([type]) => type)).toEqual(
      expect.arrayContaining(['mousemove', 'mouseup']),
    );
    detach.mockRestore();
    drag(centre(9));
    expect(onChange).toHaveBeenCalledTimes(1);
  });
});
