/**
 * The meter — the quiet-cell system's slider (contract §4).
 *
 * N discrete cells on a fixed step. Filled cells and empty cells are monochrome;
 * **only the current cell is coloured**, and it takes the mod's own category hue
 * through `var(--hue, var(--accent))` rather than the global accent. The numeric
 * readout to its right takes the same hue. That is the whole accent rule (§1): if
 * something is coloured, it is the live value.
 *
 * Local to `packages/ingame` rather than taken from `@void/ui` for two reasons: the
 * shared `Slider` is a continuous track with a knob, which is a different control;
 * and the hue plumbing is the in-game overlay's own requirement.
 *
 * **Movement**, per §5. Three things move and none of them is a colour animation on
 * the thing that matters:
 *
 *  1. The hue is a single element — {@link https://developer.mozilla.org/CSS/transform
 *     `translateX`} over the track — not a class that hops between cells. It therefore
 *     *travels* to the value that was set instead of appearing at it, which is the whole
 *     difference between a slider and ten lights.
 *  2. The monochrome fill behind it crossfades per cell, staggered by each cell's
 *     distance from the live one, so a jump from cell 2 to cell 8 reads as a sweep
 *     rather than six cells changing at once. Capped at {@link STAGGER_SPAN} cells: the
 *     stagger is there to give a large change a direction, not to make it slow.
 *  3. The §5.3 bleed — the live cell's neighbours catching a fraction of the hue — is a
 *     static hue layer per cell whose *opacity* animates, which is what §5 asks for in
 *     game ("never a per-cell colour animation, and never a blur"). It is centred on the
 *     live cell, and since a press keeps the value on the cell under the pointer, during a
 *     drag that centre *is* the pointer: §5.3's "the bleed travels with the pointer",
 *     costing nothing per move. The strengths (.40 at ±1, .16 at ±2) come straight from
 *     §5.3, and CSS drains them in the 200ms of §5.4 without a timer or a re-render.
 *
 * **The gesture**, per §5.3 and §5.4, which both describe a press that is dragged and then
 * released. The value follows the cell under the pointer for as long as the button is held,
 * and the meter reports every cell it crosses. Three things make that work in game rather
 * than only in a browser:
 *
 *  - It is built on `mousedown` plus `mousemove` / `mouseup` on `window`, not on pointer
 *    events. Java hands the page `View::FireMouseEvent` and nothing else (`UiHost.mouseDown`
 *    → `fireMouseEvent`), so a mouse event is the one thing the renderer is certain to
 *    deliver; whether Ultralight's WebKit also synthesises `pointerdown` from it is not
 *    something this control should have to bet on. `ModSettingsScreen`'s preview drag handle
 *    is the same shape, for the same reason. The held button is *not* read off the move
 *    events: Java fires them with `kMouseButton_None`, so `MouseEvent.buttons` is 0 for the
 *    whole drag, and the gesture's own start/end is the only thing that says it is running.
 *  - The cell comes from the pointer's x against geometry measured once per press, not from
 *    the element the event landed on. Input reaches the page at the menu's tick rate and is
 *    coalesced to the newest position (`UiHost.drainWork`), so a quick drag arrives as a
 *    handful of moves several cells apart; interpolating is what makes it land where the
 *    pointer is rather than one cell along from where it started.
 *  - A move inside the cell that is already live reports nothing. Between two boundaries the
 *    whole gesture is a subtraction, a compare and a ref write.
 *
 * Nothing here re-renders on pointer movement. The component's output changes only when the
 * value does, so an idle open menu still paints nothing.
 */

import { useCallback, useEffect, useRef } from 'react';

import { cx } from '@/ui';

/**
 * How many cells of distance the fill stagger keeps counting.
 *
 * At 14ms a cell (`--d` in `overlay.css`) five is 70ms of lead-in on the far end of a
 * ten-cell track, which is enough to read as a direction and short enough that the last
 * cell has still finished inside 200ms. Without the cap a full-track jump would stagger
 * for 126ms before its last cell even started.
 */
const STAGGER_SPAN = 5;

/** The §5.3 bleed: the hue's strength on a cell that many steps from the live one. */
function bleed(distance: number): number {
  if (distance === 1) return 0.4;
  if (distance === 2) return 0.16;
  return 0;
}

/** Rounds away the float dust `min + i/(n-1) * (max-min)` leaves behind. */
function tidy(value: number): number {
  return Math.round(value * 1e6) / 1e6;
}

/** Which cell is lit for a value — the inverse of {@link cellValue}. */
export function cellIndex(value: number, cells: number, min: number, max: number): number {
  if (max <= min || cells < 2) return 0;
  const ratio = (value - min) / (max - min);
  return Math.min(cells - 1, Math.max(0, Math.round(ratio * (cells - 1))));
}

/** The value cell `index` sets, snapped to the setting's own step and clamped to it. */
export function cellValue(
  index: number,
  cells: number,
  min: number,
  max: number,
  step: number,
): number {
  if (cells < 2) return min;
  const raw = min + (index / (cells - 1)) * (max - min);
  const snapped = step > 0 ? Math.round((raw - min) / step) * step + min : raw;
  return tidy(Math.min(max, Math.max(min, snapped)));
}

/**
 * What a press measures once, so that the moves after it cost no layout.
 *
 * Measured rather than assumed: the step is `--meter-cell` + `--meter-gap`, which is 17px
 * at `md` and 11px at `sm`, and reading it off two cells means this file never has to hold
 * a copy of a number that lives in `overlay.css`.
 */
interface Press {
  /** Distance between two cell centres. Zero when there is no layout to measure. */
  pitch: number;
  /** Where the centre of cell 0 sits, in client coordinates. */
  originX: number;
  /** The cell last reported. A move that stays inside it says nothing. */
  index: number;
}

/** The pitch and origin of a track, from the first two cells it holds. */
function measure(track: HTMLElement): { pitch: number; originX: number } {
  const cells = track.querySelectorAll('.meter__cell');
  const first = cells[0]?.getBoundingClientRect();
  const second = cells[1]?.getBoundingClientRect();
  if (!first || !second) return { pitch: 0, originX: 0 };
  return { pitch: second.left - first.left, originX: first.left + first.width / 2 };
}

/** Props for {@link CellMeter}. */
export interface CellMeterProps {
  /** The current value, in the setting's own units. */
  value: number;
  min: number;
  max: number;
  /** The setting's own step; the meter never produces a value between two steps. */
  step: number;
  /** How many cells the track holds. */
  cells?: number;
  /**
   * How a value reads to the right of the track, e.g. `1.0×`. Omit for no readout.
   *
   * A formatter rather than the finished string, because the readout has to reserve the width
   * of the **widest** value this meter can ever show, not the width of the one it is showing —
   * see the sizers in the markup below. The component knows every value it can produce
   * ({@link cellValue} over the cells); it only lacks the words for them, and this supplies
   * those.
   */
  format?: (value: number) => string;
  /** Accessible name — the setting's label. */
  label: string;
  /** Omit to draw a read-only meter: a readout of the value, not a control. */
  onChange?: (next: number) => void;
  /** `md` is the properties panel's 12px cell; `sm` is the list row's 8px cell. */
  size?: 'sm' | 'md';
  className?: string;
}

/**
 * @example
 * ```tsx
 * <CellMeter label="Scale" value={1} min={0.25} max={4} step={0.05}
 *            format={(v) => formatSetting('scale', v)}
 *            onChange={(next) => setSetting(id, 'scale', next)} />
 * ```
 */
export function CellMeter({
  value,
  min,
  max,
  step,
  cells = 10,
  format,
  label,
  onChange,
  size = 'md',
  className,
}: CellMeterProps): React.ReactElement {
  const current = cellIndex(value, cells, min, max);

  /* ---- the gesture ------------------------------------------------------------------ */

  // What the window handlers need. They stay attached for the length of a press and must not
  // be swapped out when a prop changes underneath them, so they read the props from here
  // instead of closing over them.
  const latest = useRef({ cells, min, max, step, onChange });
  useEffect(() => {
    latest.current = { cells, min, max, step, onChange };
  });

  const trackRef = useRef<HTMLDivElement>(null);
  const pressRef = useRef<Press | null>(null);
  /** Undoes the window listeners of the press in flight, if there is one. */
  const releaseRef = useRef<(() => void) | null>(null);
  /** The cell last reported, so the click a press also produces is not a second report. */
  const reportedRef = useRef(-1);

  const report = useCallback((index: number) => {
    const { cells: n, min: lo, max: hi, step: unit, onChange: emit } = latest.current;
    if (!emit || index < 0) return;
    reportedRef.current = index;
    // The one place a value is produced, for the click and the drag alike: a drag can only
    // ever set a value a click could have set.
    emit(cellValue(index, n, lo, hi, unit));
  }, []);

  /** The cell under an x, from the press's geometry; `-1` when there is no cell there. */
  const cellUnder = useCallback((clientX: number, target: EventTarget | null): number => {
    const press = pressRef.current;
    const track = trackRef.current;
    if (!press || !track) return -1;
    if (press.pitch > 0) {
      const index = Math.round((clientX - press.originX) / press.pitch);
      return Math.min(latest.current.cells - 1, Math.max(0, index));
    }
    // Nothing to measure — an unlaid-out track, which in practice means jsdom. Fall back to
    // the cell the event landed on, so the control still answers where it can.
    const cell = target instanceof Element ? target.closest('.meter__cell') : null;
    return cell === null ? -1 : [...track.querySelectorAll('.meter__cell')].indexOf(cell);
  }, []);

  const onMove = useCallback(
    (event: MouseEvent) => {
      const press = pressRef.current;
      if (!press) return;
      const index = cellUnder(event.clientX, event.target);
      if (index < 0 || index === press.index) return;
      press.index = index;
      report(index);
    },
    [cellUnder, report],
  );

  const onUp = useCallback(
    (event: MouseEvent) => {
      // The release is a move as well. Java sends it with the position the button came up at,
      // and that position may never have arrived as a move of its own — the stream is coalesced
      // at the tick, so a quick flick can end between two of them. §5.4's "the value that was
      // set" is the cell the pointer let go over.
      onMove(event);
      releaseRef.current?.();
    },
    [onMove],
  );

  const onMouseDown = useCallback(
    (event: React.MouseEvent<HTMLDivElement>) => {
      const track = trackRef.current;
      if (!track || !latest.current.onChange) return;
      // Deliberately not `!== 0`. Java maps LWJGL's buttons onto Ultralight's own numbering
      // (`UiHost.mouseButton`) and this is the only place in the overlay that reads the
      // result back out, so a wrong mapping there must not be able to make the meter dead.
      // Turning away the secondary button is the whole of what this needs to do.
      if (event.button === 2) return;
      releaseRef.current?.();

      pressRef.current = { ...measure(track), index: -1 };
      const index = cellUnder(event.clientX, event.target);
      if (index < 0) {
        pressRef.current = null;
        return;
      }
      pressRef.current.index = index;

      // On `window`, not on the track: the drag has to keep working when the pointer wanders
      // off the 12px-tall row it started on, and it has to end on a release that lands
      // anywhere at all.
      window.addEventListener('mousemove', onMove);
      window.addEventListener('mouseup', onUp);
      releaseRef.current = () => {
        window.removeEventListener('mousemove', onMove);
        window.removeEventListener('mouseup', onUp);
        pressRef.current = null;
        releaseRef.current = null;
      };

      report(index);
    },
    [cellUnder, onMove, onUp, report],
  );

  // A menu closed mid-drag must not leave a listener behind on a 20Hz move stream.
  useEffect(() => () => releaseRef.current?.(), []);

  /* ---- the track -------------------------------------------------------------------- */

  const track: React.ReactElement[] = [];

  for (let i = 0; i < cells; i += 1) {
    // The live cell is filled like the rest of the run rather than coloured: the hue is the
    // travelling marker below, and while it is in flight the cell it is leaving has to look
    // like part of the track, not like a hole.
    const distance = Math.abs(i - current);
    const cellClass = cx('meter__cell', i <= current && 'meter__cell--on');
    const cellStyle = {
      ['--d']: Math.min(distance, STAGGER_SPAN),
      ['--bleed']: bleed(distance),
    } as React.CSSProperties;
    track.push(
      onChange ? (
        <button
          key={i}
          type="button"
          className={cellClass}
          style={cellStyle}
          aria-label={`${label} ${cellValue(i, cells, min, max, step)}`}
          onClick={(event) => {
            // A mouse click is mousedown → mouseup → click, and the press has already
            // reported this cell; the click is the same gesture arriving a second time.
            // `detail` is the click count, which is 0 exactly when the click was synthesised
            // by the keyboard — the only click left for this to answer. The second half is
            // the belt to that brace: if a press ever fails to report, its click still does.
            if (event.detail !== 0 && reportedRef.current === i) return;
            report(i);
          }}
        />
      ) : (
        <span key={i} className={cellClass} style={cellStyle} />
      ),
    );
  }

  return (
    <div className={cx('meter', `meter--${size}`, className)}>
      <div
        ref={trackRef}
        className="meter__track"
        role={onChange ? 'group' : 'meter'}
        aria-label={label}
        aria-valuenow={onChange ? undefined : value}
        aria-valuemin={onChange ? undefined : min}
        aria-valuemax={onChange ? undefined : max}
        style={{ ['--live']: current } as React.CSSProperties}
        // On the track rather than on the cells, so that the 5px gaps between them are part
        // of the control too — a press at the edge of a cell is a press on that cell.
        onMouseDown={onChange ? onMouseDown : undefined}
      >
        {track}
        {/* Last, so it paints over the cell it is sitting on, and `aria-hidden` because the
            value it marks is already on the track's own role. */}
        <span className="meter__live" aria-hidden="true" />
      </div>
      {format === undefined ? null : (
        <span className="meter__readout tnum">
          {/* The reserved width, and the whole of §4's "the track does not move".
              `.mprop` right-anchors the control cluster, so anything that widens the readout
              drags the track's left edge along with it — and the readout's width is not
              constant: `8%` -> `85%` -> `100%` is two characters then four, and Outfit's
              figures are proportional (`1` is 338 units against `0`'s 657), so even a
              same-length pair like `11%` and `88%` is not the same width. The user saw both,
              as "1's are smaller than 8's and so the slider kinda shifts around".

              One zero-height block per cell, each holding what that cell's value prints. A
              block contributes its whole width to this box's shrink-to-fit and `height: 0`
              with `overflow: hidden` contributes no height and no paint, so the box measures
              the widest string the meter can ever show and then never changes size, whatever
              the live value is and whether or not the engine honours `tnum`. Geometry, not
              typography: it is the only fix that survives both a proportional face and a
              change in the number of digits. */}
          {Array.from({ length: cells }, (_, i) => (
            <span key={i} className="meter__readout__sizer" aria-hidden="true">
              {format(cellValue(i, cells, min, max, step))}
            </span>
          ))}
          {format(value)}
        </span>
      )}
    </div>
  );
}
