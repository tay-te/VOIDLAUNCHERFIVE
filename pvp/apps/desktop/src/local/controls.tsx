/**
 * The quiet-cell controls, as the launcher draws them.
 *
 * `design/quiet-cell-system.md` §3–§4 specify these to the pixel — a cell is a square
 * with a 30% radius, a toggle comes in exactly three sizes, a meter is N discrete cells
 * 12px wide on a 17px step with the *current* cell in the hue. `@void/ui` still ships
 * the previous system's `Toggle` / `Slider` (36 × 20, a continuous rail), so until that
 * package lands the new geometry the launcher draws its own. These are deliberately
 * small and styleless in JS: every value lives in `local/app.css`, and every accent
 * reads `var(--hue, var(--accent))` so a mod-scoped caller tints them by setting
 * `--hue` on an ancestor (§1, "Category hues").
 *
 * TODO(ui): fold these back into `@void/ui` once its controls are on the cell system;
 * nothing here is launcher-shaped, it is only launcher-*timed*.
 */

import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type ButtonHTMLAttributes,
  type ReactNode,
} from 'react';

/* -------------------------------------------------------------------------- */
/* Toggle                                                                     */
/* -------------------------------------------------------------------------- */

/** §4: three sizes only — `sm` a mod tile, `md` a list row, `lg` the properties panel. */
export type ToggleSize = 'sm' | 'md' | 'lg';

export interface ToggleProps
  extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'onChange' | 'children'> {
  checked: boolean;
  onChange?: (next: boolean) => void;
  size?: ToggleSize;
  /** Accessible name. Required whenever no visible label sits beside the switch. */
  label?: string;
}

export function Toggle({
  checked,
  onChange,
  size = 'md',
  label,
  className,
  onClick,
  ...rest
}: ToggleProps) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      className={`tg tg--${size}${checked ? ' is-on' : ''}${className ? ` ${className}` : ''}`}
      onClick={(event) => {
        onClick?.(event);
        if (!event.defaultPrevented) onChange?.(!checked);
      }}
      {...rest}
    >
      <span className="tg__knob">
        {/* The grip: three 2px cells at 30% on the knob centre (§4). */}
        <i />
        <i />
        <i />
      </span>
    </button>
  );
}

/* -------------------------------------------------------------------------- */
/* Meter                                                                      */
/* -------------------------------------------------------------------------- */

/**
 * How many cells a meter draws at most — 10, which is the number every meter in
 * `design/screens/launcher/Launcher-Setup.png` has: a 165px track (10 × 12 on a 17
 * step) right-aligned in the properties column. §4 fixes the cell geometry but not the
 * count, and a 16-cell track ran 100px past the frame's.
 */
const MAX_CELLS = 10;

export interface MeterProps {
  label: string;
  /** The formatted value, printed to the right of the label in the hue. */
  readout: string;
  value: number;
  min: number;
  max: number;
  step: number;
  onChange: (next: number) => void;
  disabled?: boolean;
}

/**
 * The slider, as N discrete cells.
 *
 * §4 fixes the geometry and §5.3 the behaviour: the cell under the pointer takes the
 * hue at .95, ±1 at .40, ±2 at .16, and the bleed travels with the pointer. The bleed
 * is a `data-bleed` distance on each cell rather than a per-cell colour animation, so
 * one pointer move is one attribute write and the CSS does the rest.
 */
export function Meter({ label, readout, value, min, max, step, onChange, disabled }: MeterProps) {
  const track = useRef<HTMLDivElement>(null);
  const [bleed, setBleed] = useState<number | null>(null);
  const [dragging, setDragging] = useState(false);

  const span = max - min;
  const steps = span > 0 && step > 0 ? Math.round(span / step) + 1 : 1;
  const count = Math.max(2, Math.min(steps, MAX_CELLS));
  const ratio = span > 0 ? (value - min) / span : 0;
  const current = Math.max(0, Math.min(count - 1, Math.round(ratio * (count - 1))));

  const commit = useCallback(
    (index: number) => {
      const clamped = Math.max(0, Math.min(count - 1, index));
      const raw = min + (clamped / (count - 1)) * span;
      const snapped = step > 0 ? Math.round((raw - min) / step) * step + min : raw;
      onChange(Number(Math.max(min, Math.min(max, snapped)).toFixed(4)));
    },
    [count, min, max, span, step, onChange],
  );

  const indexAt = useCallback(
    (clientX: number): number => {
      const el = track.current;
      if (!el) return current;
      const box = el.getBoundingClientRect();
      const x = clientX - box.left;
      return Math.round((x / Math.max(1, box.width)) * (count - 1));
    },
    [count, current],
  );

  // The drag is bound to the window, not the track: releasing outside the meter must
  // still drain the bleed (§5.4), and a pointer that leaves the track mid-drag keeps
  // adjusting rather than dropping the value where it happened to cross the edge.
  useEffect(() => {
    if (!dragging) return;
    const onMove = (event: PointerEvent): void => {
      const index = indexAt(event.clientX);
      setBleed(Math.max(0, Math.min(count - 1, index)));
      commit(index);
    };
    const onUp = (): void => {
      setDragging(false);
      setBleed(null);
    };
    window.addEventListener('pointermove', onMove);
    window.addEventListener('pointerup', onUp);
    return () => {
      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('pointerup', onUp);
    };
  }, [dragging, indexAt, commit, count]);

  const cells = [];
  for (let i = 0; i < count; i += 1) {
    const distance = bleed === null ? null : Math.abs(i - bleed);
    cells.push(
      <span
        key={i}
        className={`meter__cell cell${i < current ? ' is-filled' : ''}${
          i === current ? ' is-current' : ''
        }`}
        data-bleed={distance !== null && distance <= 2 ? distance : undefined}
      />,
    );
  }

  return (
    <div className={`meter${disabled ? ' is-disabled' : ''}`}>
      <div className="meter__head">
        <span className="meter__label">{label}</span>
        <span className="meter__value tnum">{readout}</span>
      </div>
      <div
        ref={track}
        className="meter__track"
        role="slider"
        tabIndex={disabled ? -1 : 0}
        aria-label={label}
        aria-valuemin={min}
        aria-valuemax={max}
        aria-valuenow={value}
        aria-valuetext={readout}
        onPointerDown={(event) => {
          if (disabled) return;
          event.preventDefault();
          setDragging(true);
          const index = indexAt(event.clientX);
          setBleed(Math.max(0, Math.min(count - 1, index)));
          commit(index);
        }}
        onPointerMove={(event) => {
          if (disabled || dragging) return;
          setBleed(Math.max(0, Math.min(count - 1, indexAt(event.clientX))));
        }}
        onPointerLeave={() => !dragging && setBleed(null)}
        onKeyDown={(event) => {
          if (disabled) return;
          if (event.key === 'ArrowLeft' || event.key === 'ArrowDown') {
            event.preventDefault();
            commit(current - 1);
          } else if (event.key === 'ArrowRight' || event.key === 'ArrowUp') {
            event.preventDefault();
            commit(current + 1);
          }
        }}
      >
        {cells}
      </div>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Chips                                                                      */
/* -------------------------------------------------------------------------- */

export interface ChipOption {
  id: string;
  label: string;
  /** Greyed out and unselectable; stays monochrome (§1, the accent rule). */
  disabled?: boolean;
}

export interface ChipsProps {
  options: readonly ChipOption[];
  value: string;
  onChange: (id: string) => void;
  /** Accessible name for the group. */
  label: string;
  className?: string;
}

/** The segmented row: enum settings, presets, the anchor picker. */
export function Chips({ options, value, onChange, label, className }: ChipsProps) {
  return (
    <div className={`chips${className ? ` ${className}` : ''}`} role="radiogroup" aria-label={label}>
      {options.map((option) => (
        <button
          key={option.id}
          type="button"
          role="radio"
          aria-checked={option.id === value}
          disabled={option.disabled}
          className={`chip${option.id === value ? ' is-on' : ''}`}
          onClick={() => onChange(option.id)}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Keybind                                                                    */
/* -------------------------------------------------------------------------- */

export interface KeyChipProps {
  /** The pretty name of the bound key. */
  value: string;
  label: string;
  /** Resolves with the next key the window sees — `local/keys.captureKey`. */
  onCapture: () => Promise<string | null>;
  onChange: (next: string) => void;
}

export function KeyChip({ value, label, onCapture, onChange }: KeyChipProps) {
  const [capturing, setCapturing] = useState(false);

  return (
    <button
      type="button"
      aria-label={label}
      className={`keychip${capturing ? ' is-capturing' : ''}`}
      onClick={() => {
        if (capturing) return;
        setCapturing(true);
        void onCapture().then((next) => {
          setCapturing(false);
          if (next !== null) onChange(next);
        });
      }}
    >
      {capturing ? 'Press a key…' : value}
    </button>
  );
}

/* -------------------------------------------------------------------------- */
/* Swatches                                                                   */
/* -------------------------------------------------------------------------- */

export interface Swatch {
  id: string;
  color: string;
  label: string;
}

export interface SwatchesProps {
  swatches: readonly Swatch[];
  value: string;
  onChange: (id: string) => void;
  label: string;
}

/**
 * The colour row.
 *
 * The swatches are the *values*, so they carry their own colour — the one place on the
 * screen where colour is not state. The selection ring is the hue, which is the state.
 */
export function Swatches({ swatches, value, onChange, label }: SwatchesProps) {
  return (
    <div className="swatches" role="radiogroup" aria-label={label}>
      {swatches.map((swatch) => (
        <button
          key={swatch.id}
          type="button"
          role="radio"
          aria-checked={swatch.id === value}
          aria-label={swatch.label}
          title={swatch.label}
          className={`swatch cell${swatch.id === value ? ' is-on' : ''}`}
          style={{ background: swatch.color }}
          onClick={() => onChange(swatch.id)}
        />
      ))}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Search field                                                               */
/* -------------------------------------------------------------------------- */

export interface SearchFieldProps {
  value: string;
  onChange: (next: string) => void;
  placeholder?: string;
  label: string;
}

export function SearchField({ value, onChange, placeholder, label }: SearchFieldProps) {
  return (
    <input
      type="search"
      className="searchfield"
      aria-label={label}
      placeholder={placeholder}
      value={value}
      onChange={(event) => onChange(event.target.value)}
    />
  );
}

/* -------------------------------------------------------------------------- */
/* Buttons                                                                    */
/* -------------------------------------------------------------------------- */

export type ButtonKind = 'primary' | 'secondary' | 'ghost';

export interface FlatButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  kind?: ButtonKind;
  children: ReactNode;
}

/** §4: primary is a `--text-primary` fill, secondary a hairline, ghost a label. */
export function FlatButton({ kind = 'secondary', className, children, ...rest }: FlatButtonProps) {
  return (
    <button type="button" className={`btn btn--${kind}${className ? ` ${className}` : ''}`} {...rest}>
      {children}
    </button>
  );
}
