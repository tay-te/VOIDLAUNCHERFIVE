import {
  useCallback,
  useEffect,
  useId,
  useRef,
  useState,
  type ButtonHTMLAttributes,
  type HTMLAttributes,
  type KeyboardEvent as ReactKeyboardEvent,
  type PointerEvent as ReactPointerEvent,
  type ReactNode,
} from 'react';

import { cx } from '../lib/cx.js';

/* -------------------------------------------------------------------------- */
/* Toggle                                                                     */
/* -------------------------------------------------------------------------- */

/**
 * The three switch sizes, and only three (`design/quiet-cell-system.md` §4).
 *
 * `sm` / `md` / `lg` are the contract's names. `s` / `m` / `l` are what this package
 * shipped before it and mean exactly the same three sizes; both are accepted so no
 * caller had to change, and both resolve to the same class.
 */
export type ToggleSize = 'sm' | 'md' | 'lg' | 's' | 'm' | 'l';

/** The pre-contract size letters, mapped onto the sizes they always meant. */
const TOGGLE_SIZE: Record<ToggleSize, 'sm' | 'md' | 'lg'> = {
  sm: 'sm',
  md: 'md',
  lg: 'lg',
  s: 'sm',
  m: 'md',
  l: 'lg',
};

/** Props for {@link Toggle}. */
export interface ToggleProps
  extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'onChange' | 'children'> {
  /** Whether the switch is on. Controlled. */
  checked: boolean;
  /** Called with the requested state. */
  onChange?: (next: boolean) => void;
  /**
   * - `sm` — 30 × 17, knob 11. A mod tile.
   * - `md` — 40 × 22, knob 16. A list row.
   * - `lg` — 46 × 26, knob 20. The properties panel.
   */
  size?: ToggleSize;
  /** Accessible name. Required whenever the switch has no visible label beside it. */
  label?: string;
}

/**
 * The pill switch.
 *
 * It is a `role="switch"` button, not a checkbox: a styled native checkbox is not
 * reliably restyleable in Ultralight, whereas a button plus a 2D translate is.
 *
 * §4 in full: semi-rounded at 35% of the height, knob inset 3px with a radius of 35%
 * of the knob — a rounded square, not a circle — and a grip of three 2px cells at 30%
 * on the knob centre, which is why the knob has children. ON is a
 * `rgba(237,238,239,.88)` track with a `--bg-shell` knob; OFF is `--bg-base` with a 1px
 * `rgba(255,255,255,.16)` rim and a `--text-muted` knob. Nothing here is the hue: a
 * switch reports a boolean, and colour is reserved for a live *value* (§1).
 */
export function Toggle({
  checked,
  onChange,
  size = 'sm',
  label,
  className,
  disabled,
  onClick,
  type = 'button',
  ...rest
}: ToggleProps): React.ReactElement {
  const resolved = TOGGLE_SIZE[size] ?? 'sm';
  return (
    <button
      type={type}
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      className={cx(
        'v-toggle',
        resolved !== 'sm' && `v-toggle--${resolved}`,
        checked && 'v-toggle--on',
        className,
      )}
      onClick={(event) => {
        onClick?.(event);
        if (!event.defaultPrevented) onChange?.(!checked);
      }}
      {...rest}
    >
      <span className="v-toggle__knob">
        <span className="v-toggle__grip" />
        <span className="v-toggle__grip" />
        <span className="v-toggle__grip" />
      </span>
    </button>
  );
}

/* -------------------------------------------------------------------------- */
/* Cell                                                                       */
/* -------------------------------------------------------------------------- */

/** What a cell is reporting (§3). */
export type CellState = 'empty' | 'filled' | 'live';

/** Props for {@link Cell}. */
export interface CellProps extends HTMLAttributes<HTMLSpanElement> {
  /** Edge length in pixels. A cell is always square. */
  size?: number;
  /**
   * - `empty` — off, `rgba(237,238,239,.06-.10)`.
   * - `filled` — on, `rgba(237,238,239,.36-.45)`.
   * - `live` — the current value, `var(--hue, var(--accent))` at .95.
   */
  state?: CellState;
}

/**
 * The atom (§3): a square whose corner radius is 30% of its size.
 *
 * Everything textural in the system is built from these — meter cells, the toggle
 * grip, the grid and dither textures, the mark in the launcher search. The radius is a
 * percentage rather than a length precisely so one class covers every size.
 */
export function Cell({
  size = 12,
  state = 'empty',
  className,
  style,
  ...rest
}: CellProps): React.ReactElement {
  return (
    <span
      className={cx(
        'v-cell',
        state === 'filled' && 'v-cell--on',
        state === 'live' && 'v-cell--live',
        className,
      )}
      style={{ width: size, height: size, ...style }}
      {...rest}
    />
  );
}

/* -------------------------------------------------------------------------- */
/* Meter                                                                      */
/* -------------------------------------------------------------------------- */

/** Props for {@link Meter}. */
export interface MeterProps extends Omit<HTMLAttributes<HTMLDivElement>, 'onChange'> {
  /** Current value, in `[min, max]`. Controlled. */
  value: number;
  /** Called on every step — a click, a drag across cells, an arrow key. */
  onChange?: (next: number) => void;
  /** Called once when a drag or a key press ends — the moment to write through. */
  onCommit?: (next: number) => void;
  /** Lower bound. Defaults to 0. */
  min?: number;
  /** Upper bound. Defaults to 1. */
  max?: number;
  /** How many discrete cells the track is drawn with. */
  cells?: number;
  /** The label to the left. */
  label?: ReactNode;
  /** The numeric readout, which sits to the right in the hue at .95. */
  readout?: ReactNode;
  /** Hide the label row entirely — for a row that supplies its own. */
  hideLabels?: boolean;
  /** Accessible name when `label` is not a string. */
  ariaLabel?: string;
  /** Disable interaction. */
  disabled?: boolean;
}

const clampTo = (value: number, min: number, max: number): number =>
  Math.min(max, Math.max(min, value));

/**
 * The meter — the quiet cell system's slider (§4).
 *
 * N discrete cells, 12px on a 17px step. Cells below the value are filled and
 * monochrome, because they are history; the cell *at* the value takes the hue at .95,
 * because it is the value; the readout to the right is the same fact in the same hue.
 *
 * §5.3, the bleed: while the pointer is down, the cell under it takes the hue at .95,
 * its neighbours at .40 and theirs at .16, and the whole thing travels with the
 * pointer. §5.4: on release it drains over 200ms, leaving the hue on the value that was
 * set. That is what `dragging` and the `--draining` class are for — the falloff needs
 * to know where the pointer is, which CSS alone cannot.
 *
 * Keyboard: ← / → and ↑ / ↓ step a cell, Home / End jump to the bounds — the standard
 * `role="slider"` contract, so it is usable without a pointer.
 */
export function Meter({
  value,
  onChange,
  onCommit,
  min = 0,
  max = 1,
  cells = 12,
  label,
  readout,
  hideLabels = false,
  ariaLabel,
  disabled = false,
  className,
  ...rest
}: MeterProps): React.ReactElement {
  const trackRef = useRef<HTMLDivElement>(null);
  const [bleed, setBleed] = useState<number | null>(null);
  const [draining, setDraining] = useState(false);
  const dragging = useRef(false);
  const count = Math.max(1, Math.round(cells));

  /** Which cell index a value lands on, and the value at the centre of an index. */
  const indexOf = (raw: number): number =>
    max === min ? 0 : Math.round(clampTo((raw - min) / (max - min), 0, 1) * (count - 1));
  const valueOfIndex = (index: number): number =>
    min + (clampTo(index, 0, count - 1) / (count - 1 || 1)) * (max - min);

  const current = indexOf(value);

  const indexAt = (clientX: number): number => {
    const track = trackRef.current;
    if (!track) return current;
    const rect = track.getBoundingClientRect();
    const ratio = rect.width === 0 ? 0 : clampTo((clientX - rect.left) / rect.width, 0, 1);
    return Math.round(ratio * (count - 1));
  };

  const onPointerDown = (event: ReactPointerEvent<HTMLDivElement>): void => {
    if (disabled) return;
    dragging.current = true;
    setDraining(false);
    event.currentTarget.setPointerCapture?.(event.pointerId);
    const index = indexAt(event.clientX);
    setBleed(index);
    onChange?.(valueOfIndex(index));
  };

  const onPointerMove = (event: ReactPointerEvent<HTMLDivElement>): void => {
    if (!dragging.current || disabled) return;
    const index = indexAt(event.clientX);
    setBleed(index);
    onChange?.(valueOfIndex(index));
  };

  const endDrag = (event: ReactPointerEvent<HTMLDivElement>): void => {
    if (!dragging.current) return;
    dragging.current = false;
    event.currentTarget.releasePointerCapture?.(event.pointerId);
    // §5.4 — the bleed drains, leaving the hue on the value that was set.
    setBleed(null);
    setDraining(true);
    onCommit?.(valueOfIndex(indexAt(event.clientX)));
  };

  const onKeyDown = (event: ReactKeyboardEvent<HTMLDivElement>): void => {
    if (disabled) return;
    let next: number | null = null;
    if (event.key === 'ArrowRight' || event.key === 'ArrowUp') next = current + 1;
    else if (event.key === 'ArrowLeft' || event.key === 'ArrowDown') next = current - 1;
    else if (event.key === 'Home') next = 0;
    else if (event.key === 'End') next = count - 1;
    if (next === null) return;
    event.preventDefault();
    const settled = valueOfIndex(next);
    onChange?.(settled);
    onCommit?.(settled);
  };

  return (
    <div
      className={cx(
        'v-meter',
        draining && 'v-meter--draining',
        disabled && 'v-meter--disabled',
        className,
      )}
      {...rest}
    >
      {hideLabels ? null : (
        <div className="v-meter__labels">
          <span className="v-meter__label">{label}</span>
          <span className="v-meter__value">{readout}</span>
        </div>
      )}
      <div
        ref={trackRef}
        role="slider"
        tabIndex={disabled ? -1 : 0}
        aria-valuenow={value}
        aria-valuemin={min}
        aria-valuemax={max}
        aria-valuetext={typeof readout === 'string' ? readout : undefined}
        aria-label={ariaLabel ?? (typeof label === 'string' ? label : undefined)}
        aria-disabled={disabled || undefined}
        className="v-meter__track"
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={endDrag}
        onPointerCancel={endDrag}
        onKeyDown={onKeyDown}
      >
        {Array.from({ length: count }, (_unused, index) => {
          const distance = bleed === null ? null : Math.abs(index - bleed);
          return (
            <span
              key={index}
              className={cx(
                'v-meter__cell',
                index < current && 'v-meter__cell--filled',
                index === current && 'v-meter__cell--current',
                distance === 1 && 'v-cell--bleed-1',
                distance === 2 && 'v-cell--bleed-2',
              )}
            />
          );
        })}
      </div>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Slider                                                                     */
/* -------------------------------------------------------------------------- */

/** Props for {@link Slider}. */
export interface SliderProps extends Omit<HTMLAttributes<HTMLDivElement>, 'onChange'> {
  /** Current value, in `[min, max]`. Controlled. */
  value: number;
  /** Called on every drag step and arrow-key press. */
  onChange?: (next: number) => void;
  /** Called once when a drag ends — the moment to write through `setModSetting`. */
  onCommit?: (next: number) => void;
  /** Lower bound. Defaults to 0. */
  min?: number;
  /** Upper bound. Defaults to 1. */
  max?: number;
  /** Step. Defaults to `(max - min) / 100`. */
  step?: number;
  /** The label to the left of the value readout. */
  label?: ReactNode;
  /**
   * The value readout. Pass a string to control the formatting exactly — the design
   * shows `1.0×`, `85%` and `8 px`, which no single formatter produces.
   */
  readout?: ReactNode;
  /**
   * - `compact` — the 200 × 14 track in the ModSettingsPanel.
   * - `wide` — the 180 × 16 track in a settings row, with its own 48px value column.
   */
  variant?: 'compact' | 'wide';
  /** Hide the label row entirely — for a settings row that supplies its own. */
  hideLabels?: boolean;
  /** Accessible name when `label` is not given. */
  ariaLabel?: string;
  /** Disable interaction. */
  disabled?: boolean;
}

const clamp = (value: number, min: number, max: number): number =>
  Math.min(max, Math.max(min, value));

/**
 * The design's slider: a rail, an accent fill and a rectangular thumb.
 *
 * Keyboard: ← / → step, ↑ / ↓ step, Home / End jump to the bounds — the standard
 * `role="slider"` contract, so it is usable without a pointer.
 */
export function Slider({
  value,
  onChange,
  onCommit,
  min = 0,
  max = 1,
  step,
  label,
  readout,
  variant = 'compact',
  hideLabels = false,
  ariaLabel,
  disabled = false,
  className,
  ...rest
}: SliderProps): React.ReactElement {
  const trackRef = useRef<HTMLDivElement>(null);
  const resolvedStep = step ?? (max - min) / 100;
  const fraction = max === min ? 0 : clamp((value - min) / (max - min), 0, 1);

  const quantise = useCallback(
    (raw: number): number => {
      const stepped = Math.round((raw - min) / resolvedStep) * resolvedStep + min;
      // Trim the float noise `0.1 + 0.2` style arithmetic leaves behind.
      const decimals = Math.min(6, Math.max(0, String(resolvedStep).split('.')[1]?.length ?? 0));
      return Number(clamp(stepped, min, max).toFixed(decimals));
    },
    [min, max, resolvedStep],
  );

  const valueAt = useCallback(
    (clientX: number): number => {
      const track = trackRef.current;
      if (!track) return value;
      const rect = track.getBoundingClientRect();
      const ratio = rect.width === 0 ? 0 : clamp((clientX - rect.left) / rect.width, 0, 1);
      return quantise(min + ratio * (max - min));
    },
    [max, min, quantise, value],
  );

  const dragging = useRef(false);

  const onPointerDown = (event: ReactPointerEvent<HTMLDivElement>): void => {
    if (disabled) return;
    dragging.current = true;
    event.currentTarget.setPointerCapture?.(event.pointerId);
    onChange?.(valueAt(event.clientX));
  };

  const onPointerMove = (event: ReactPointerEvent<HTMLDivElement>): void => {
    if (!dragging.current || disabled) return;
    onChange?.(valueAt(event.clientX));
  };

  const endDrag = (event: ReactPointerEvent<HTMLDivElement>): void => {
    if (!dragging.current) return;
    dragging.current = false;
    event.currentTarget.releasePointerCapture?.(event.pointerId);
    onCommit?.(valueAt(event.clientX));
  };

  const onKeyDown = (event: ReactKeyboardEvent<HTMLDivElement>): void => {
    if (disabled) return;
    const jump = event.shiftKey ? resolvedStep * 10 : resolvedStep;
    let next: number | null = null;
    if (event.key === 'ArrowRight' || event.key === 'ArrowUp') next = quantise(value + jump);
    else if (event.key === 'ArrowLeft' || event.key === 'ArrowDown') next = quantise(value - jump);
    else if (event.key === 'Home') next = min;
    else if (event.key === 'End') next = max;
    if (next === null) return;
    event.preventDefault();
    onChange?.(next);
    onCommit?.(next);
  };

  return (
    <div
      className={cx('v-slider', variant === 'wide' && 'v-slider--wide', disabled && 'v-slider--disabled', className)}
      {...rest}
    >
      {hideLabels ? null : (
        <div className="v-slider__labels">
          <span className="v-slider__label">{label}</span>
          <span className="v-slider__value">{readout}</span>
        </div>
      )}
      <div
        ref={trackRef}
        role="slider"
        tabIndex={disabled ? -1 : 0}
        aria-valuenow={value}
        aria-valuemin={min}
        aria-valuemax={max}
        aria-valuetext={typeof readout === 'string' ? readout : undefined}
        aria-label={ariaLabel ?? (typeof label === 'string' ? label : undefined)}
        aria-disabled={disabled || undefined}
        className="v-slider__track"
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={endDrag}
        onPointerCancel={endDrag}
        onKeyDown={onKeyDown}
      >
        <span className="v-slider__rail" />
        <span className="v-slider__fill" style={{ width: `${fraction * 100}%` }} />
        <span className="v-slider__thumb" style={{ left: `${fraction * 100}%` }} />
      </div>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* KeybindChip                                                                */
/* -------------------------------------------------------------------------- */

/** Props for {@link KeybindChip}. */
export interface KeybindChipProps
  extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'onChange' | 'children'> {
  /** The bound key, as an LWJGL 2 name — `R-Shift`, `C`, `NONE`. */
  value: string;
  /**
   * Start a capture. In game this is `void.openKeybindCapture(modId)`, which resolves
   * with the captured key or null when the player pressed Escape. The chip shows
   * `Press a key…` until the promise settles, then hands the result to `onChange`.
   *
   * Omit it and the chip is a read-only display.
   */
  onCapture?: () => Promise<string | null>;
  /** Called with the captured key. Not called when the capture was cancelled. */
  onChange?: (key: string) => void;
  /** Text shown while a capture is open. */
  capturingLabel?: string;
}

/**
 * The `R-Shift` chip beside a `Keybind` label.
 *
 * The capture flow is the one place on the bridge that is asynchronous, so this
 * component owns exactly that state: idle → capturing → idle, with the applied value
 * coming back from Java rather than from an optimistic write.
 */
export function KeybindChip({
  value,
  onCapture,
  onChange,
  capturingLabel = 'Press a key…',
  className,
  disabled,
  onClick,
  type = 'button',
  ...rest
}: KeybindChipProps): React.ReactElement {
  const [capturing, setCapturing] = useState(false);
  const alive = useRef(true);
  useEffect(
    () => () => {
      alive.current = false;
    },
    [],
  );

  const start = async (): Promise<void> => {
    if (!onCapture || capturing) return;
    setCapturing(true);
    try {
      const captured = await onCapture();
      if (captured !== null && captured !== undefined) onChange?.(captured);
    } finally {
      if (alive.current) setCapturing(false);
    }
  };

  return (
    <button
      type={type}
      disabled={disabled || !onCapture}
      aria-live="polite"
      className={cx('v-keybind', capturing && 'v-keybind--capturing', className)}
      onClick={(event) => {
        onClick?.(event);
        if (!event.defaultPrevented) void start();
      }}
      {...rest}
    >
      {capturing ? capturingLabel : value}
    </button>
  );
}

/* -------------------------------------------------------------------------- */
/* FilterTabs                                                                 */
/* -------------------------------------------------------------------------- */

/** One tab in a {@link FilterTabs} set. */
export interface FilterTab {
  /** Stable id handed back to `onChange`. */
  id: string;
  /** Visible label, e.g. `All`, `HUD`, `Requests`. */
  label: ReactNode;
  /** Optional count shown after the label in DM Mono. */
  count?: number;
  /** Tint the count green — `Requests · 2` reads as "needs your attention". */
  countTone?: 'default' | 'ok';
}

/** Props for {@link FilterTabs}. */
export interface FilterTabsProps extends Omit<HTMLAttributes<HTMLDivElement>, 'onChange'> {
  /** The tabs, left to right. */
  tabs: readonly FilterTab[];
  /** Id of the selected tab. Controlled. */
  value: string;
  /** Called with the id of the tab the user picked. */
  onChange?: (id: string) => void;
  /** Accessible name for the tablist. */
  label?: string;
}

/**
 * The pill tab row: `All / HUD / PvP / Visual / Utility` and its siblings.
 *
 * Implements the ARIA tablist keyboard contract — ← / → move, Home / End jump — so the
 * in-game overlay is navigable without a mouse.
 */
export function FilterTabs({
  tabs,
  value,
  onChange,
  label = 'Filter',
  className,
  ...rest
}: FilterTabsProps): React.ReactElement {
  const id = useId();
  const move = (delta: number): void => {
    const index = tabs.findIndex((tab) => tab.id === value);
    if (index < 0) return;
    const next = tabs[(index + delta + tabs.length) % tabs.length];
    if (next) onChange?.(next.id);
  };

  return (
    <div
      role="tablist"
      aria-label={label}
      className={cx('v-tabs', className)}
      onKeyDown={(event) => {
        if (event.key === 'ArrowRight') {
          event.preventDefault();
          move(1);
        } else if (event.key === 'ArrowLeft') {
          event.preventDefault();
          move(-1);
        } else if (event.key === 'Home') {
          event.preventDefault();
          if (tabs[0]) onChange?.(tabs[0].id);
        } else if (event.key === 'End') {
          event.preventDefault();
          const last = tabs[tabs.length - 1];
          if (last) onChange?.(last.id);
        }
      }}
      {...rest}
    >
      {tabs.map((tab) => {
        const selected = tab.id === value;
        return (
          <button
            key={tab.id}
            id={`${id}-${tab.id}`}
            type="button"
            role="tab"
            aria-selected={selected}
            tabIndex={selected ? 0 : -1}
            className={cx('v-tab', selected && 'v-tab--selected')}
            onClick={() => onChange?.(tab.id)}
          >
            {tab.label}
            {tab.count === undefined ? null : (
              <span
                className={cx('v-tab__count', tab.countTone === 'ok' && 'v-tab__count--ok')}
              >
                {tab.count}
              </span>
            )}
          </button>
        );
      })}
    </div>
  );
}
