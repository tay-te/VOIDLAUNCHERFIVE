/**
 * HUD placement maths. Pure — no React, no DOM, no bridge. Everything the HUD
 * layer and the HUD editor need to turn a `hud_item` into pixels and back.
 *
 * PVP_ARCHITECTURE.md §8.1: positions are stored as `anchor + dx/dy + scale`,
 * never as absolute pixels, so a layout survives GUI-scale, resolution and
 * fullscreen changes.
 *
 * Convention (the one the CSS below implements, and the one `setHud` round-trips):
 * the anchor names a point on the viewport *and* the matching point on the
 * widget box. `bottom-left` puts the widget's bottom-left corner at
 * `(0 + dx, viewportHeight + dy)`; `right` puts the middle of the widget's right
 * edge at `(viewportWidth + dx, viewportHeight / 2 + dy)`. That is why dx is
 * negative on right-hand anchors and dy negative on bottom anchors, exactly as
 * loadout.json describes.
 */

import type { CSSProperties } from 'react';
import type { HUDAnchor, HUDModId } from '@/bridge/protocol';

export type Axis = 'start' | 'center' | 'end';

/**
 * The factory HUD layout, matching the placements drawn on frame `244:1722`.
 *
 * **There is a second copy of this table, in Java** — `Loadout.DEFAULT_HUD`, which seeds a new
 * loadout. They must agree entry for entry, because `Reset layout` has to put every chip back
 * where the client started and a second table that disagreed would make reset a *move* rather
 * than an undo. `test/hud-defaults.test.ts` reads the Java file and fails naming any row that
 * has drifted, which is the only thing that keeps two hand-maintained tables in step.
 *
 * It lives here rather than in `HudEditorScreen` because two other things need it and neither
 * should have to import a screen for data: `HudLayer` uses it as the placement of last resort
 * for a mod that is on but has no `hud[]` entry (rendering-invariants §15 — an absence is the
 * one failure mode that survives being looked at), and the test above reads it.
 *
 * Mods that ship off (`coordinates`) are placed too: a placement is where a widget *would* go,
 * not whether it is drawn.
 */
export const DEFAULT_HUD: Record<HUDModId, { anchor: HUDAnchor; dx: number; dy: number }> = {
  fps: { anchor: 'top-left', dx: 23, dy: 23 },
  ping: { anchor: 'top-left', dx: 23, dy: 65 },
  coordinates: { anchor: 'top-left', dx: 23, dy: 103 },
  // Next row of the left column, after coordinates at 103. `loadout.json`'s own factory layout
  // says `top-left 20,58`, on an 18-20px rhythm; this table's rhythm is 38-42, so 58 here would
  // land the mark on top of the ping chip at 65 rather than under it. Same intent — third in the
  // top-left stack — expressed in the space the page actually lays out in.
  watermark: { anchor: 'top-left', dx: 23, dy: 141 },
  // Next row of the same column, on this table's 38 px rhythm. Under Coordinates on purpose:
  // it is the mod a player confuses with Coordinates, and stacking them makes the difference
  // — a position, versus a facing — visible at a glance rather than argued about.
  direction: { anchor: 'top-left', dx: 23, dy: 179 },
  potion_effects: { anchor: 'top-right', dx: -25, dy: 23 },
  armor_status: { anchor: 'top-right', dx: -25, dy: 299 },
  keystrokes: { anchor: 'bottom-left', dx: 31, dy: -109 },
  cps: { anchor: 'bottom-left', dx: 175, dy: -108 },
};

/** Decompose an anchor into its horizontal and vertical halves. */
export function anchorAxes(anchor: HUDAnchor): { x: Axis; y: Axis } {
  switch (anchor) {
    case 'top-left':
      return { x: 'start', y: 'start' };
    case 'top':
      return { x: 'center', y: 'start' };
    case 'top-right':
      return { x: 'end', y: 'start' };
    case 'left':
      return { x: 'start', y: 'center' };
    case 'center':
      return { x: 'center', y: 'center' };
    case 'right':
      return { x: 'end', y: 'center' };
    case 'bottom-left':
      return { x: 'start', y: 'end' };
    case 'bottom':
      return { x: 'center', y: 'end' };
    case 'bottom-right':
      return { x: 'end', y: 'end' };
  }
}

/** Recompose an anchor from its two axes. */
export function axesToAnchor(x: Axis, y: Axis): HUDAnchor {
  const rows: Record<Axis, Record<Axis, HUDAnchor>> = {
    start: { start: 'top-left', center: 'left', end: 'bottom-left' },
    center: { start: 'top', center: 'center', end: 'bottom' },
    end: { start: 'top-right', center: 'right', end: 'bottom-right' },
  };
  return rows[x][y];
}

/**
 * Inline style that places a widget at `anchor + dx/dy`. Only `position`, one or two edge
 * offsets, `transform` and `transform-origin` — 2D transforms only (ultralight-notes.md §4), no
 * `calc()` inside the transform, no 3D.
 *
 * **The scale is not here, and that is the whole of a bug fix.** It used to be, as
 * `translate(...) scale(s)` — and `transform: scale()` corrupts text in this engine. Measured on
 * the keystrokes pad at `1.3×`: the single-letter caps `W A S D` were clean and the two
 * three-letter ones came out as `LNB` and `RNB`, the glyphs drawn at the scaled size over
 * advances computed at the unscaled one, so every multi-glyph run collides with itself. At `2.8×`
 * every cap was unreadable. This is rendering-invariants §10 — "a label under `scale()` renders
 * at roughly the device scale" — which was written about `:active` press states and is in fact
 * true of any scaled text, permanently, not just for the frames of a press.
 *
 * The scale is {@link zoomStyle} on an inner box instead. `zoom` is a **layout** scale, so the
 * engine lays the text out at its final size and the metrics are the right ones: the same pad at
 * the same `1.3×` renders `LMB` and `RMB` correctly.
 *
 * Two boxes rather than one, and the split is load-bearing. The outer box keeps its unzoomed
 * coordinate system, so `dx`/`dy` still mean what `loadout.json` and `screenPosition()` say they
 * mean, and `getBoundingClientRect()` on it still reports the box the player sees — a zoom on
 * *this* element would scale its own `translate()` too, quietly redefining every offset Java has
 * stored.
 */
export function placementStyle(anchor: HUDAnchor, dx: number, dy: number): CSSProperties {
  const { x, y } = anchorAxes(anchor);
  const style: CSSProperties = { position: 'absolute' };
  const pre: string[] = [];

  if (x === 'start') style.left = 0;
  else if (x === 'end') style.right = 0;
  else {
    style.left = '50%';
    pre.push('translateX(-50%)');
  }

  if (y === 'start') style.top = 0;
  else if (y === 'end') style.bottom = 0;
  else {
    style.top = '50%';
    pre.push('translateY(-50%)');
  }

  const ox = x === 'start' ? '0%' : x === 'end' ? '100%' : '50%';
  const oy = y === 'start' ? '0%' : y === 'end' ? '100%' : '50%';

  style.transformOrigin = `${ox} ${oy}`;
  style.transform = `${pre.join(' ')} translate(${dx}px, ${dy}px)`.trim();
  return style;
}

/**
 * The widget's own size multiplier, for the box **inside** {@link placementStyle}'s.
 *
 * `zoom` rather than `transform: scale()`, because scaled text is corrupt in this engine — see
 * the note on `placementStyle`. Being a layout scale it also means the outer box measures the
 * widget at its drawn size, which is exactly what the HUD editor's geometry needs.
 */
export function zoomStyle(scale: number): CSSProperties {
  // `zoom` is not in React's CSSProperties in every version, and it is a real CSS property here.
  return { zoom: scale } as CSSProperties;
}

export interface Size {
  width: number;
  height: number;
}

/**
 * Top-left corner of the widget in viewport pixels — what the HUD editor's
 * selection readout prints as `x N · y N`.
 */
export function screenPosition(
  anchor: HUDAnchor,
  dx: number,
  dy: number,
  size: Size,
  viewport: Size,
): { x: number; y: number } {
  const { x, y } = anchorAxes(anchor);
  const anchorX = x === 'start' ? 0 : x === 'end' ? viewport.width : viewport.width / 2;
  const anchorY = y === 'start' ? 0 : y === 'end' ? viewport.height : viewport.height / 2;
  const offsetX = x === 'start' ? 0 : x === 'end' ? size.width : size.width / 2;
  const offsetY = y === 'start' ? 0 : y === 'end' ? size.height : size.height / 2;
  return { x: anchorX + dx - offsetX, y: anchorY + dy - offsetY };
}

/** Inverse of {@link screenPosition}: a top-left corner back to `dx`/`dy`. */
export function placementFromScreen(
  anchor: HUDAnchor,
  x: number,
  y: number,
  size: Size,
  viewport: Size,
): { dx: number; dy: number } {
  const axes = anchorAxes(anchor);
  const anchorX = axes.x === 'start' ? 0 : axes.x === 'end' ? viewport.width : viewport.width / 2;
  const anchorY = axes.y === 'start' ? 0 : axes.y === 'end' ? viewport.height : viewport.height / 2;
  const offsetX = axes.x === 'start' ? 0 : axes.x === 'end' ? size.width : size.width / 2;
  const offsetY = axes.y === 'start' ? 0 : axes.y === 'end' ? size.height : size.height / 2;
  return { dx: anchorX === 0 ? x + offsetX : x + offsetX - anchorX, dy: y + offsetY - anchorY };
}

/** The editor grid, in unscaled GUI pixels. */
export const GRID = 8;

/** Snap a value to the 8 px editor grid. */
export function snapTo(value: number, grid = GRID): number {
  return Math.round(value / grid) * grid + 0;
}

/** Keep a widget's top-left corner inside the viewport, allowing a small bleed. */
export function clampToViewport(
  x: number,
  y: number,
  size: Size,
  viewport: Size,
  bleed = 0,
): { x: number; y: number } {
  // `+ 0` normalises -0, which Object.is (and therefore React and Vitest)
  // treats as a different value from 0.
  return {
    x: Math.min(Math.max(x, -bleed), Math.max(-bleed, viewport.width - size.width + bleed)) + 0,
    y: Math.min(Math.max(y, -bleed), Math.max(-bleed, viewport.height - size.height + bleed)) + 0,
  };
}

/** `hud_item.scale` is bounded to [0.25, 4] by loadout.json. */
export function clampScale(scale: number): number {
  return Math.min(4, Math.max(0.25, scale));
}

/** `dx`/`dy` are bounded to [-4096, 4096] by loadout.json. */
export function clampOffset(value: number): number {
  return Math.min(4096, Math.max(-4096, value));
}

/**
 * Pick the anchor a widget should be stored against, given where it ended up.
 * Widgets that settle in the outer third of an axis pin to that edge; the middle
 * third pins to the centre. Keeps a bottom-left HUD bottom-left when the window
 * is resized, which is the whole point of §8.1.
 */
export function anchorForPosition(
  x: number,
  y: number,
  size: Size,
  viewport: Size,
): HUDAnchor {
  const cx = x + size.width / 2;
  const cy = y + size.height / 2;
  const axis = (centre: number, extent: number): Axis =>
    centre < extent / 3 ? 'start' : centre > (extent * 2) / 3 ? 'end' : 'center';
  return axesToAnchor(axis(cx, viewport.width), axis(cy, viewport.height));
}
