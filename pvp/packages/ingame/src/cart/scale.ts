/**
 * The menu's one scale, `--cu`.
 *
 * The Cartridge frames are drawn at 1920 × 1080 with every size a multiple of one unit (`u`),
 * and the 720p and 1440p frames (Figma 606:2, 607:2) are the same layout at u = 0.75 and
 * u = 1.333. So the menu is laid out once, at the design size, and the whole layer is `zoom`ed
 * by u — a layout scale, not `transform: scale()`, so the engine sets text at its final size
 * (rendering-invariants §10a) and a 13 px label at u = 0.75 is a real 9.75 px label, not a
 * resampled 13 px one.
 *
 * u follows the page's CSS viewport, which in game is already the window divided by the host's
 * dp/css scale; the floor of 0.75 is the 720p frame, below which the layout would stop fitting
 * rather than get smaller. `compact` is the 720p frame's other change: no bottom status strip,
 * because at 960 design px tall the panel needs that room.
 */

import { useEffect, useState } from 'react';

export const DESIGN_W = 1920;
export const DESIGN_H = 1080;
export const MIN_U = 0.75;
export const MAX_U = 2.5;

export interface CartScale {
  /** The zoom applied to the menu layer. */
  u: number;
  /** The layer's size in design px (the viewport divided by u). */
  width: number;
  height: number;
  /** Short window: the bottom strip yields its room to the panel. */
  compact: boolean;
}

export function solveScale(viewW: number, viewH: number): CartScale {
  const raw = Math.min(viewW / DESIGN_W, viewH / DESIGN_H);
  const u = Math.round(Math.min(MAX_U, Math.max(MIN_U, raw)) * 1000) / 1000;
  const width = Math.round(viewW / u);
  const height = Math.round(viewH / u);
  return { u, width, height, compact: height < 1000 };
}

function viewport(): { w: number; h: number } {
  if (typeof window === 'undefined') return { w: DESIGN_W, h: DESIGN_H };
  return { w: window.innerWidth || DESIGN_W, h: window.innerHeight || DESIGN_H };
}

/** The live scale; re-solved on resize only (no animation-frame loop — §9). */
export function useCartScale(): CartScale {
  const [scale, setScale] = useState(() => {
    const { w, h } = viewport();
    return solveScale(w, h);
  });
  useEffect(() => {
    const onResize = () => {
      const { w, h } = viewport();
      const next = solveScale(w, h);
      setScale((prev) =>
        prev.u === next.u && prev.width === next.width && prev.height === next.height ? prev : next,
      );
    };
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);
  return scale;
}

/** Style for a full-viewport layer laid out at design size and zoomed to fit. */
export function layerStyle(scale: CartScale): React.CSSProperties {
  return { zoom: scale.u, width: scale.width, height: scale.height } as React.CSSProperties;
}
