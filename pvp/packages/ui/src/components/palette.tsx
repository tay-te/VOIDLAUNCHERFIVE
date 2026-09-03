import type { HTMLAttributes, InputHTMLAttributes, ReactNode } from 'react';

import { Icon, type IconName } from './Icon.js';
import { Kbd } from './primitives.js';
import { cx } from '../lib/cx.js';

/**
 * The quick palette — the ⌘K command surface over the game (`244:1900`).
 *
 * `↑↓` moves the selection, `↵` runs the highlighted action, `⌘↵` opens that action's
 * settings instead of running it, `esc` closes. The selected result previews the state
 * change inline (`currently off  →  on`), and the footer's right-hand side always shows
 * the active loadout.
 *
 * Selection and key handling belong to the consumer: the palette does not know what its
 * results mean, and the in-game menu already owns the key routing.
 */

/* -------------------------------------------------------------------------- */
/* Palette                                                                    */
/* -------------------------------------------------------------------------- */

/** The 640px palette shell. */
export function Palette({ className, ...rest }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cx('v-palette', className)} role="dialog" aria-modal="true" {...rest} />;
}

/** Props for {@link PaletteInput}. */
export interface PaletteInputProps
  extends Omit<InputHTMLAttributes<HTMLInputElement>, 'onChange' | 'value'> {
  /** The query text. */
  value?: string;
  /** Called with the new query. */
  onChange?: (value: string) => void;
  /** Draw the blinking accent caret after the query. */
  showCaret?: boolean;
  /** The trailing chip. Defaults to `esc`. */
  hint?: ReactNode;
}

/** The 58px query row: search glyph, query, accent caret, `esc` chip. */
export function PaletteInput({
  value,
  onChange,
  showCaret = true,
  hint = <Kbd flavour="palette">esc</Kbd>,
  placeholder = 'Search actions',
  className,
  ...rest
}: PaletteInputProps): React.ReactElement {
  return (
    <div className={cx('v-palette__input', className)}>
      <span className="v-palette__search" aria-hidden="true">
        <Icon name="search" size={18} />
      </span>
      <span className="v-palette__query">
        <input
          type="text"
          value={value}
          placeholder={placeholder}
          // A palette rendered for display only — a still in the gallery, a preview —
          // passes a query with no handler. That is read-only, not a mistake.
          readOnly={value !== undefined && !onChange}
          onChange={onChange ? (event) => onChange(event.target.value) : undefined}
          {...rest}
        />
        {/* An invisible copy of the query, in the same face and size, that gives the
            caret below it the exact width of the typed text — the frame sets the
            caret against the last glyph (`fullb|`), not at the end of the field. */}
        <span className="v-palette__ghost" aria-hidden="true">
          {value}
        </span>
        {showCaret ? <span className="v-palette__caret" aria-hidden="true" /> : null}
      </span>
      {hint}
    </div>
  );
}

/** The 1px seam between the palette's sections. */
export function PaletteSeam({ className, ...rest }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cx('v-palette__seam', className)} {...rest} />;
}

/** Props for {@link PaletteSection}. */
export interface PaletteSectionProps extends HTMLAttributes<HTMLDivElement> {
  /** The uppercase caption, e.g. `ACTIONS`, `ALSO`. */
  caption: ReactNode;
}

/** A captioned group of results. */
export function PaletteSection({
  caption,
  className,
  children,
  ...rest
}: PaletteSectionProps): React.ReactElement {
  return (
    <div className={className} {...rest}>
      <div className="v-palette__caption">
        <span className="v-caption">{caption}</span>
      </div>
      <div className="v-palette__list" role="listbox">
        {children}
      </div>
    </div>
  );
}

/** Props for {@link PaletteResult}. */
export interface PaletteResultProps
  extends Omit<HTMLAttributes<HTMLDivElement>, 'onSelect' | 'title'> {
  /** The action's name, e.g. `Toggle Fullbright`. */
  title: ReactNode;
  /**
   * The line under it. On the selected row the design previews the state change
   * inline — `Visual  ·  currently off  →  on` — in the accent ink.
   */
  sub?: ReactNode;
  /** The 16px glyph in the 30px icon well. */
  icon?: IconName;
  /** Zero to two trailing keyboard chips, e.g. `↵` or `⌘` `↵`. */
  keys?: readonly ReactNode[];
  /** Whether this is the highlighted row. */
  selected?: boolean;
  /** Called when the row is chosen. */
  onSelect?: () => void;
}

/** One 48px result row. */
export function PaletteResult({
  title,
  sub,
  icon = 'sparkle',
  keys = [],
  selected = false,
  onSelect,
  className,
  ...rest
}: PaletteResultProps): React.ReactElement {
  return (
    <div
      role="option"
      aria-selected={selected}
      tabIndex={selected ? 0 : -1}
      className={cx('v-palette__row', selected && 'v-palette__row--selected', className)}
      onClick={onSelect}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          onSelect?.();
        }
      }}
      {...rest}
    >
      <span
        className={cx('v-icon-well', 'v-icon-well--30', selected && 'v-icon-well--on')}
        aria-hidden="true"
      >
        <Icon name={icon} size={16} />
      </span>
      <span className="v-palette__text">
        <span className="v-palette__title">{title}</span>
        {sub ? <span className="v-palette__sub">{sub}</span> : null}
      </span>
      {keys.length > 0 ? (
        <span className="v-palette__keys">
          {keys.map((key, index) => (
            <Kbd key={index} flavour="palette">
              {key}
            </Kbd>
          ))}
        </span>
      ) : null}
    </div>
  );
}

/** One `kbd + word` pair in the palette footer. */
export interface PaletteHint {
  /** The key, e.g. `↑↓`. */
  keys: ReactNode;
  /** What it does, e.g. `move`. */
  word: ReactNode;
}

/** Props for {@link PaletteFooter}. */
export interface PaletteFooterProps extends HTMLAttributes<HTMLDivElement> {
  /** The key hints, left to right. */
  hints?: readonly PaletteHint[];
  /** The active loadout's name — the footer always shows it. */
  loadout?: ReactNode;
  /** The loadout's icon. */
  loadoutIcon?: IconName;
}

/** The footer: key hints, a spacer, then the active loadout. */
export function PaletteFooter({
  hints = [],
  loadout,
  loadoutIcon = 'sword',
  className,
  ...rest
}: PaletteFooterProps): React.ReactElement {
  return (
    <div className={cx('v-palette__footer', className)} {...rest}>
      {hints.map((hint, index) => (
        <span key={index} className="v-palette__hint">
          <Kbd flavour="palette">{hint.keys}</Kbd>
          <span className="v-palette__hint-word">{hint.word}</span>
        </span>
      ))}
      <span className="v-spacer" />
      {loadout === undefined ? null : (
        <span className="v-palette__loadout">
          <Icon name={loadoutIcon} size={14} />
          {loadout}
        </span>
      )}
    </div>
  );
}
