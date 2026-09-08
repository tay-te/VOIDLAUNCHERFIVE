/**
 * The VOID watermark — the mark, drawn over the game.
 *
 * ## Why it is a mod and not a flag
 *
 * Every PvP client puts its mark on the screen and players expect to find it, move it and turn
 * it off. All three of those already exist here, for HUD mods: `on`, `scale` and `opacity` are
 * settings the registry knows how to clamp and Java knows how to persist, and `loadout.hud[]` is
 * a placement the player drags in the HUD editor. A bespoke boolean would have had to reinvent
 * the toggle, would not have been draggable, would not have survived a loadout switch, and would
 * have been the one thing on the HUD that the HUD editor could not see.
 *
 * So `watermark` is the thirteenth row of `schema/mods.json`: `kind: hud`, `category: visual`,
 * and it appears in the grid, in the list, in the palette and in the layout editor by having
 * done nothing special anywhere.
 *
 * ## The treatment
 *
 * It is the same mark the menu's bar draws (`cell-art.tsx`, `VoidMark`) — the ring is sixteen
 * §3 cells and the word is Outfit 500 tracked at .12em — because there is one VOID mark and
 * drawing a second one for the HUD is how two marks start to disagree.
 *
 * Three things are its own, though, and each is deliberate:
 *
 *   · **No plate.** Every other HUD widget sits on a chip: a `--card-bg` fill with a border.
 *     A watermark on a plate is a badge, and a badge in the corner of a match is another
 *     readout competing with the ones that carry numbers. Ink only.
 *   · **Smaller than the bar's.** 3px cells against the bar's 4, and a 13px word against 15.
 *     The bar's mark is a heading; this one sits under the fps and ping chips in the same
 *     corner and must not out-rank them.
 *   · **Monochrome, always.** §1: colour marks a live value or a selected item. A mark is
 *     neither, so it never takes the category hue — not even in the mods grid, where its tile
 *     preview draws the same white cells.
 *
 * `opacity` and `scale` are **not** read here. `HudLayer` already multiplies the mod's `scale`
 * setting into the placement transform and its `opacity` into the slot's opacity, for every HUD
 * widget; doing it again in the widget would apply both twice.
 */

import { memo } from 'react';

import { cx } from '@/ui';
import { CellArt } from '@/menu/cell-art';
import { modSettings, useVoidStore } from '@/store/store';

/** The mod id, in one place. It is a real registry id; the string is here to be imported. */
export const WATERMARK_ID = 'watermark';

/** The VOID ring: a 7 × 7 bitmap, the same one the bar's mark uses. */
const RING = ['..###..', '.#...#.', '#.....#', '#.....#', '#.....#', '.#...#.', '..###..'];

/** What `watermark.style` can be, per `mods.json`. */
export type WatermarkStyle = 'full' | 'mark' | 'word';

/** Narrow an unknown setting value to a style, so a host with a stale registry still draws. */
export function watermarkStyle(value: unknown): WatermarkStyle {
  return value === 'mark' || value === 'word' ? value : 'full';
}

export interface WatermarkProps {
  /** Override the loadout's style. The Settings page's preview does not; the HUD never does. */
  style?: WatermarkStyle;
  /** Cell edge of the ring, in px. 3 on the HUD; the bar's mark is 4. */
  cell?: number;
  className?: string;
}

/**
 * The mark itself, with no placement and no opacity of its own.
 *
 * Exported apart from {@link HudWatermark} because the Settings page draws one as a preview
 * beside the switch that turns it on — the same component at the same settings, so what the
 * card shows is what will be over the game.
 */
export function Watermark({ style, cell = 3, className }: WatermarkProps): React.ReactElement {
  const stored = useVoidStore((s) => modSettings(s.loadout, WATERMARK_ID as never).style);
  const shape = style ?? watermarkStyle(stored);
  return (
    <span className={cx('wmark', `wmark--${shape}`, className)} aria-hidden="true">
      {shape !== 'word' && <CellArt rows={RING} size={cell} className="wmark__ring" />}
      {shape !== 'mark' && <span className="wmark__word">VOID</span>}
    </span>
  );
}

/**
 * The HUD entry.
 *
 * Takes `HudWidgetProps` like every other widget so it can go in `HudLayer`'s one table, and
 * ignores `variant`: the editor's denser chip treatment is for widgets that have a chip, and
 * this one deliberately does not. What the editor gives it instead is its own drag handle and
 * outline, which are drawn by the editor around whatever the widget renders.
 */
export const HudWatermark = memo(function HudWatermark(): React.ReactElement {
  return <Watermark />;
});
