import type { ButtonHTMLAttributes, HTMLAttributes, ReactNode } from 'react';

import { Icon, MOD_ICONS, type IconName } from './Icon.js';
import { Toggle, type ToggleSize } from './controls.js';
import { IconWell, Tag } from './primitives.js';
import { cx } from '../lib/cx.js';

/* -------------------------------------------------------------------------- */
/* ModGrid + ModTile                                                          */
/* -------------------------------------------------------------------------- */

/** The 3 × 4 tile grid, 620px wide with a 10px gutter. */
export function ModGrid({ className, ...rest }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cx('v-modgrid', className)} {...rest} />;
}

/** Props for {@link ModTile}. */
export interface ModTileProps
  extends Omit<HTMLAttributes<HTMLDivElement>, 'onChange' | 'onSelect' | 'onToggle'> {
  /** Display name, e.g. `FPS display`. */
  name: ReactNode;
  /** The uppercase category tag: `HUD`, `PVP`, `VISUAL`, `UTILITY`. */
  category: ReactNode;
  /** Whether the mod is enabled. Drives the switch and the icon-well tint. */
  on: boolean;
  /** Called when the tile's switch is flipped. */
  onToggle?: (next: boolean) => void;
  /** Whether this is the tile whose settings the pane is showing. */
  selected?: boolean;
  /** Called when the tile body (not the switch) is clicked. */
  onSelect?: () => void;
  /** The 16px glyph in the icon well. */
  icon?: IconName;
  /**
   * Accessible name for the select action, when `name` is not a plain string.
   * Defaults to `name`.
   */
  selectLabel?: string;
  /** Make the tile a drag source — dropping it on the HUD places that widget. */
  draggable?: boolean;
  /** Disable both the select action and the switch. */
  disabled?: boolean;
}

/**
 * One 200 × 96 mod tile.
 *
 * On/off is carried by the switch and the icon-well tint; **selection is carried by the
 * border alone**, with no fill change, exactly as the frames draw it.
 *
 * The tile holds two independent controls — select the mod, and turn it on — so it is a
 * container with a stretched select button behind its contents and the switch layered
 * above. Making the whole tile one `<button>` would nest a button inside a button, which
 * is invalid HTML and leaves the switch unreachable by keyboard.
 *
 * @example
 * ```tsx
 * <ModTile name="Keystrokes" category="HUD" icon="keyboard" on selected
 *          onToggle={(next) => void.setGameplay?.(…)} onSelect={() => select('keystrokes')} />
 * ```
 */
export function ModTile({
  name,
  category,
  on,
  onToggle,
  selected = false,
  onSelect,
  icon = 'box',
  selectLabel,
  disabled = false,
  className,
  ...rest
}: ModTileProps): React.ReactElement {
  const label = selectLabel ?? (typeof name === 'string' ? name : 'Mod');
  return (
    <div className={cx('v-modtile', selected && 'v-modtile--selected', className)} {...rest}>
      <button
        type="button"
        aria-pressed={selected}
        aria-label={label}
        disabled={disabled}
        className="v-modtile__select"
        onClick={onSelect}
      />
      <IconWell icon={icon} size={34} on={on} />
      <span className="v-modtile__text">
        <span className="v-modtile__name">{name}</span>
        <span className="v-modtile__row">
          <Toggle
            checked={on}
            onChange={onToggle}
            disabled={disabled}
            size="s"
            label={`${label} enabled`}
          />
          <Tag>{category}</Tag>
        </span>
      </span>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* KeystrokesPreview                                                          */
/* -------------------------------------------------------------------------- */

/** Which keys the preview and the widget can draw. */
export interface KeyState {
  w?: boolean;
  a?: boolean;
  s?: boolean;
  d?: boolean;
  lmb?: boolean;
  rmb?: boolean;
  space?: boolean;
  shift?: boolean;
}

/** Props for {@link KeystrokesPreview}. */
export interface KeystrokesPreviewProps extends HTMLAttributes<HTMLDivElement> {
  /** Which keys are pressed. Anything absent is unpressed. */
  keys?: KeyState;
}

/** Absolute positions of the six keycaps, straight off `252:189`. */
const PREVIEW_KEYS = [
  { id: 'w', label: 'W', left: 108, top: 13, wide: false },
  { id: 'a', label: 'A', left: 76, top: 45, wide: false },
  { id: 's', label: 'S', left: 108, top: 45, wide: false },
  { id: 'd', label: 'D', left: 140, top: 45, wide: false },
  { id: 'lmb', label: 'LMB', left: 76, top: 77, wide: true },
  { id: 'rmb', label: 'RMB', left: 124, top: 77, wide: true },
] as const;

/**
 * The 200 × 128 preview strip inside the ModSettingsPanel — a still of the keystrokes
 * widget at a smaller keycap size.
 */
export function KeystrokesPreview({
  keys = { w: true, d: true, lmb: true },
  className,
  ...rest
}: KeystrokesPreviewProps): React.ReactElement {
  return (
    <div className={cx('v-kspreview', className)} {...rest}>
      {PREVIEW_KEYS.map((key) => (
        <span
          key={key.id}
          className={cx(
            'v-kspreview__key',
            key.wide && 'v-kspreview__key--wide',
            keys[key.id] && 'v-kspreview__key--pressed',
          )}
          style={{ left: key.left, top: key.top }}
        >
          {key.label}
        </span>
      ))}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* EditPositionButton                                                         */
/* -------------------------------------------------------------------------- */

/** Props for {@link EditPositionButton}. */
export interface EditPositionButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  /** Override the label. */
  label?: ReactNode;
}

/** The full-width accent CTA that jumps from a mod's settings to the HUD editor. */
export function EditPositionButton({
  label = 'Edit position',
  className,
  type = 'button',
  ...rest
}: EditPositionButtonProps): React.ReactElement {
  return (
    <button type={type} className={cx('v-editposition', className)} {...rest}>
      <Icon name="move" size={13} />
      {label}
    </button>
  );
}

/* -------------------------------------------------------------------------- */
/* ModSettingsPanel                                                           */
/* -------------------------------------------------------------------------- */

/** Props for {@link ModSettingsPanel}. */
export interface ModSettingsPanelProps
  extends Omit<HTMLAttributes<HTMLDivElement>, 'title' | 'onToggle'> {
  /** The mod's display name — the pane heading. */
  title: ReactNode;
  /** Whether the mod is enabled; drives the M switch in the header. */
  on?: boolean;
  /** Called when the header switch is flipped. */
  onToggle?: (next: boolean) => void;
  /** Hide the header switch (for a mod that cannot be turned off from here). */
  hideToggle?: boolean;
  /** The rows: preview, sliders, the keybind row, the CTA. */
  children?: ReactNode;
}

/**
 * The 278px settings pane that sits to the right of the mod grid.
 *
 * A shell, not a form: the sliders, keybind row and CTA are passed as children so the
 * consumer owns which settings a given mod exposes — the registry in `@void/protocol`
 * is what says that, not this package.
 *
 * @example
 * ```tsx
 * <ModSettingsPanel title="Keystrokes" on={on} onToggle={setOn}>
 *   <KeystrokesPreview keys={keys} />
 *   <Slider label="Scale" value={scale} readout="1.0×" min={0.25} max={4} onChange={setScale} />
 *   <ModSettingsRow label="Keybind">
 *     <KeybindChip value="R-Shift" onCapture={capture} onChange={setKey} />
 *   </ModSettingsRow>
 *   <span className="v-spacer" />
 *   <EditPositionButton onClick={openEditor} />
 * </ModSettingsPanel>
 * ```
 */
export function ModSettingsPanel({
  title,
  on = true,
  onToggle,
  hideToggle = false,
  className,
  children,
  ...rest
}: ModSettingsPanelProps): React.ReactElement {
  return (
    <div className={cx('v-modsettings', className)} {...rest}>
      <div className="v-modsettings__header">
        <span className="v-modsettings__title">{title}</span>
        {hideToggle ? null : (
          <Toggle
            checked={on}
            onChange={onToggle}
            size="m"
            label={typeof title === 'string' ? `${title} enabled` : 'Enabled'}
          />
        )}
      </div>
      {children}
    </div>
  );
}

/** Props for {@link ModSettingsRow}. */
export interface ModSettingsRowProps extends HTMLAttributes<HTMLDivElement> {
  /** The label on the left. */
  label: ReactNode;
}

/** A label / control row inside a {@link ModSettingsPanel}, e.g. `Keybind  R-Shift`. */
export function ModSettingsRow({
  label,
  className,
  children,
  ...rest
}: ModSettingsRowProps): React.ReactElement {
  return (
    <div className={cx('v-modsettings__row', className)} {...rest}>
      <span className="v-modsettings__label">{label}</span>
      {children}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* SettingsGroup — the 448 × 290 card on the Mod settings frame                */
/* -------------------------------------------------------------------------- */

/** Props for {@link SettingsGroup}. */
export interface SettingsGroupProps extends HTMLAttributes<HTMLDivElement> {
  /** The uppercase caption: `APPEARANCE`, `BEHAVIOUR`. */
  caption: ReactNode;
}

/** A group of settings rows separated by 1px seams. */
export function SettingsGroup({
  caption,
  className,
  children,
  ...rest
}: SettingsGroupProps): React.ReactElement {
  return (
    <div className={cx('v-group', className)} {...rest}>
      <div className="v-group__cap">
        <span className="v-caption">{caption}</span>
      </div>
      {children}
    </div>
  );
}

/** Props for {@link SettingsRow}. */
export interface SettingsRowProps extends Omit<HTMLAttributes<HTMLDivElement>, 'title'> {
  /** Row title, 13px medium. */
  title: ReactNode;
  /** Optional second line, 11px muted. */
  sub?: ReactNode;
  /** A right-aligned value in the 48px column — pair it with a wide Slider. */
  value?: ReactNode;
  /** Draw the 1px seam above this row. */
  seam?: boolean;
}

/** One 48px row inside a {@link SettingsGroup}. */
export function SettingsRow({
  title,
  sub,
  value,
  seam = false,
  className,
  children,
  ...rest
}: SettingsRowProps): React.ReactElement {
  return (
    <>
      {seam ? <span className="v-seam" /> : null}
      <div className={cx('v-group__row', className)} {...rest}>
        <span className="v-group__labels">
          <span className="v-group__title">{title}</span>
          {sub ? <span className="v-group__sub">{sub}</span> : null}
        </span>
        {children}
        {value === undefined ? null : <span className="v-group__value">{value}</span>}
      </div>
    </>
  );
}

/* -------------------------------------------------------------------------- */
/* Swatches and position chips                                                */
/* -------------------------------------------------------------------------- */

/** One colour swatch offered by a `Key colour` / `Pressed colour` row. */
export interface Swatch {
  /** Stable id handed back to `onChange`. */
  id: string;
  /** The colour to paint, as any CSS colour — usually `var(--token)`. */
  color: string;
  /** Accessible name. */
  label?: string;
}

/** Props for {@link Swatches}. */
export interface SwatchesProps extends Omit<HTMLAttributes<HTMLDivElement>, 'onChange'> {
  /** The swatches, left to right. */
  swatches: readonly Swatch[];
  /** Id of the selected swatch. */
  value: string;
  /** Called with the picked swatch's id. */
  onChange?: (id: string) => void;
}

/** The 22 × 22 colour swatch row. Selection is a 2px `--text-primary` ring. */
export function Swatches({
  swatches,
  value,
  onChange,
  className,
  ...rest
}: SwatchesProps): React.ReactElement {
  return (
    <div role="radiogroup" className={cx('v-swatches', className)} {...rest}>
      {swatches.map((swatch) => (
        <button
          key={swatch.id}
          type="button"
          role="radio"
          aria-checked={swatch.id === value}
          aria-label={swatch.label ?? swatch.id}
          className={cx('v-swatch', swatch.id === value && 'v-swatch--selected')}
          style={{ background: swatch.color }}
          onClick={() => onChange?.(swatch.id)}
        />
      ))}
    </div>
  );
}

/** Props for {@link PositionChips}. */
export interface PositionChipsProps extends Omit<HTMLAttributes<HTMLDivElement>, 'onChange'> {
  /** The options, e.g. `Top left`, `Top right`, `Bottom left`, `Bottom right`. */
  options: readonly { id: string; label: ReactNode }[];
  /** Id of the selected option. */
  value: string;
  /** Called with the picked option's id. */
  onChange?: (id: string) => void;
}

/** The small raised chips a settings row uses for a short enum. */
export function PositionChips({
  options,
  value,
  onChange,
  className,
  ...rest
}: PositionChipsProps): React.ReactElement {
  return (
    <div role="radiogroup" className={cx('v-chips', className)} {...rest}>
      {options.map((option) => (
        <button
          key={option.id}
          type="button"
          role="radio"
          aria-checked={option.id === value}
          className={cx('v-chip', option.id === value && 'v-chip--selected')}
          onClick={() => onChange?.(option.id)}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Convenience                                                                */
/* -------------------------------------------------------------------------- */

/** The icon each of the 12 mods uses. Re-exported so callers need one import. */
export { MOD_ICONS };

/** The switch sizes, re-exported for consumers building their own rows. */
export type { ToggleSize };
