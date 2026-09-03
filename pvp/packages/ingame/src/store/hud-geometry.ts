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
import type { HUDAnchor } from '@/bridge/protocol';

export type Axis = 'start' | 'center' | 'end';

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
 * Inline style that places a widget at `anchor + dx/dy` and scales it about the
 * anchor. Only `position`, one or two edge offsets, `transform` and
 * `transform-origin` — 2D transforms only (ultralight-notes.md §4), no `calc()`
 * inside the transform, no 3D.
 */
export function placementStyle(
  anchor: HUDAnchor,
  dx: number,
  dy: number,
  scale = 1,
): CSSProperties {
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
  style.transform = `${pre.join(' ')} translate(${dx}px, ${dy}px) scale(${scale})`.trim();
  return style;
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
  return Math.round(value / grid) * grid;
}

/** Keep a widget's top-left corner inside the viewport, allowing a small bleed. */
export function clampToViewport(
  x: number,
  y: number,
  size: Size,
  viewport: Size,
  bleed = 0,
): { x: number; y: number } {
  return {
    x: Math.min(Math.max(x, -bleed), Math.max(-bleed, viewport.width - size.width + bleed)),
    y: Math.min(Math.max(y, -bleed), Math.max(-bleed, viewport.height - size.height + bleed)),
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
