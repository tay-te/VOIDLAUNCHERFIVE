import type { ButtonHTMLAttributes, HTMLAttributes, ReactNode } from 'react';

import { Icon, type IconName } from './Icon.js';
import { Divider } from './primitives.js';
import { cx } from '../lib/cx.js';

/* -------------------------------------------------------------------------- */
/* EditorToolbar                                                              */
/* -------------------------------------------------------------------------- */

/** How a toolbar tool reads. */
export type ToolKind = 'mode' | 'default' | 'active' | 'primary';

/** Props for {@link Tool}. */
export interface ToolProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  /**
   * - `mode` — the label on the left (`HUD layout`); not interactive.
   * - `default` — `Grid`, `Reset`.
   * - `active` — `Snap` when snapping is on: accent tint, accent border, check icon.
   * - `primary` — `Done`: a filled accent button.
   */
  kind?: ToolKind;
  /** The 13px leading glyph. `active` tools get a check unless this is set. */
  icon?: IconName;
  /** Drop the leading icon entirely, as `Done` does. */
  hideIcon?: boolean;
}

/** One 32px tool inside {@link EditorToolbar}. */
export function Tool({
  kind = 'default',
  icon,
  hideIcon = false,
  className,
  children,
  type = 'button',
  ...rest
}: ToolProps): React.ReactElement {
  const glyph = icon ?? (kind === 'active' ? 'check' : undefined);
  const content = (
    <>
      {hideIcon || !glyph ? null : <Icon name={glyph} size={13} />}
      {children}
    </>
  );
  if (kind === 'mode') {
    return (
      <span className={cx('v-tool', 'v-tool--mode', className)}>{content}</span>
    );
  }
  return (
    <button
      type={type}
      aria-pressed={kind === 'active' ? true : undefined}
      className={cx('v-tool', kind !== 'default' && `v-tool--${kind}`, className)}
      {...rest}
    >
      {content}
    </button>
  );
}

/** Props for {@link EditorToolbar}. */
export interface EditorToolbarProps extends HTMLAttributes<HTMLDivElement> {
  /** The mode label on the left. Defaults to `HUD layout`. */
  mode?: ReactNode;
  /** Whether snapping is on. */
  snap?: boolean;
  /** Toggle snapping. */
  onSnapChange?: (next: boolean) => void;
  /** Whether the grid overlay is showing. */
  grid?: boolean;
  /** Toggle the grid overlay. */
  onGridChange?: (next: boolean) => void;
  /** Reset the layout to the loadout's defaults. */
  onReset?: () => void;
  /** Leave the editor — the `Done` button and the Esc key both do this. */
  onDone?: () => void;
  /** Replace the tool row entirely. */
  children?: ReactNode;
}

/**
 * The HUD editor's floating toolbar: `HUD layout | Snap · Grid · Reset · Done`.
 *
 * `Snap` is on by default, which the frames show as the accent-tinted state with a
 * check icon.
 */
export function EditorToolbar({
  mode = 'HUD layout',
  snap = true,
  onSnapChange,
  grid = false,
  onGridChange,
  onReset,
  onDone,
  className,
  children,
  ...rest
}: EditorToolbarProps): React.ReactElement {
  return (
    <div className={cx('v-toolbar', className)} {...rest}>
      <Tool kind="mode" icon="move">
        {mode}
      </Tool>
      <Divider toolbar />
      {children ?? (
        <>
          <Tool kind={snap ? 'active' : 'default'} onClick={() => onSnapChange?.(!snap)}>
            Snap
          </Tool>
          <Tool
            kind={grid ? 'active' : 'default'}
            icon="layers"
            onClick={() => onGridChange?.(!grid)}
          >
            Grid
          </Tool>
          <Tool icon="reset" onClick={onReset}>
            Reset
          </Tool>
          <Tool kind="primary" hideIcon onClick={onDone}>
            Done
          </Tool>
        </>
      )}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* SelectionFrame                                                             */
/* -------------------------------------------------------------------------- */

/** Which corner a resize grip sits on. */
export type SelectionHandle = 'nw' | 'ne' | 'sw' | 'se';

/** Props for {@link SelectionFrame}. */
export interface SelectionFrameProps extends HTMLAttributes<HTMLDivElement> {
  /** The selected widget's name, shown in the accent pill above the box. */
  name: ReactNode;
  /**
   * The live position readout beside the name — the design prints
   * `x 32  ·  y 580  ·  1.0×`.
   */
  readout?: ReactNode;
  /** Hide the four corner grips. */
  hideHandles?: boolean;
  /** Hide the pill above the box. */
  hideLabel?: boolean;
  /** Called when a grip is dragged. `⌥`+drag scales; the editor owns that logic. */
  onHandlePointerDown?: (handle: SelectionHandle, event: React.PointerEvent) => void;
}

/**
 * The dashed box around the widget being edited, with four corner grips and the live
 * position readout above it.
 *
 * Position it yourself — it is `position: absolute`, so give it `left/top/width/height`
 * through `style` and place it inside a `position: relative` parent, sized to the widget
 * plus the design's 8px bleed.
 *
 * The border style comes from `--selection-border-style`: dashed in the launcher, solid
 * in the overlay, because dash phase on rounded corners is inconsistent in Ultralight
 * (§7). The `--accent-tint-faint` fill distinguishes the selection either way.
 */
export function SelectionFrame({
  name,
  readout,
  hideHandles = false,
  hideLabel = false,
  onHandlePointerDown,
  className,
  children,
  ...rest
}: SelectionFrameProps): React.ReactElement {
  const handles: SelectionHandle[] = ['nw', 'ne', 'sw', 'se'];
  return (
    <div className={cx('v-selection', className)} {...rest}>
      {hideLabel ? null : (
        <span className="v-selection__label">
          <span className="v-selection__name">{name}</span>
          {readout === undefined ? null : (
            <span className="v-selection__readout">{readout}</span>
          )}
        </span>
      )}
      {hideHandles
        ? null
        : handles.map((handle) => (
            <span
              key={handle}
              className={cx('v-selection__handle', `v-selection__handle--${handle}`)}
              onPointerDown={(event) => onHandlePointerDown?.(handle, event)}
            />
          ))}
      {children}
    </div>
  );
}

/**
 * Format the selection readout the way the frames do: `x 32  ·  y 580  ·  1.0×`.
 * `dx` and `dy` come straight off the `hud_item`; scale defaults to 1.
 */
export function formatSelectionReadout(dx: number, dy: number, scale = 1): string {
  return `x ${Math.round(dx)}  ·  y ${Math.round(dy)}  ·  ${scale.toFixed(1)}×`;
}

/* -------------------------------------------------------------------------- */
/* HintBar                                                                    */
/* -------------------------------------------------------------------------- */

/** Props for {@link HintBar}. */
export interface HintBarProps extends HTMLAttributes<HTMLDivElement> {
  /**
   * The hints, joined with a `·` separator. Pass strings for the plain footer look, or
   * nodes when a hint needs a Kbd chip.
   */
  hints?: readonly ReactNode[];
}

/**
 * The raised mono chip at the bottom of the HUD editor —
 * `Drag to move   ·   ⌥ drag to scale   ·   Esc to exit`.
 *
 * Pass `children` instead of `hints` to lay the content out yourself.
 */
export function HintBar({
  hints,
  className,
  children,
  ...rest
}: HintBarProps): React.ReactElement {
  return (
    <div className={cx('v-hintbar', className)} {...rest}>
      {children ??
        hints?.map((hint, index) => (
          <span key={index} style={{ display: 'contents' }}>
            {index > 0 ? <span className="v-hintbar__sep">·</span> : null}
            <span>{hint}</span>
          </span>
        ))}
    </div>
  );
}
