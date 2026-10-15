/**
 * One 16 × 16 pixel-art sprite, drawn from the generated PNG sheet.
 *
 * PNG, not SVG: design/ultralight-notes.md §7 rates inline SVG [risky] in this engine and asks
 * for a sprite sheet. The sheet (`cart/sprites.css`, `--c-sheet`) is produced by
 * `scripts/sprites/build-sprites.mjs` at 8× with nearest-neighbour scaling, and
 * `sprite-map.ts` (generated alongside) says which cell each name is in.
 *
 * `size` is the drawn edge in CSS px. Sizes that are a multiple of 16 land every art pixel on
 * whole CSS pixels; any size works.
 */

import { memo, type CSSProperties } from 'react';

import { SHEET_COLS, SHEET_ROWS, SPRITE_INDEX, type SpriteName } from './sprite-map';

export type { SpriteName };

export interface SpriteProps {
  name: SpriteName;
  /** Drawn edge in CSS px. */
  size: number;
  /** Opacity, for the off and blocked cartridge states. */
  dim?: number;
  className?: string;
  style?: CSSProperties;
}

/** Whether a sprite of this name exists (mods, loadout emblems and effects are looked up). */
export function hasSprite(name: string): name is SpriteName {
  return Object.prototype.hasOwnProperty.call(SPRITE_INDEX, name);
}

export function spriteStyle(name: SpriteName, size: number): CSSProperties {
  const index = SPRITE_INDEX[name] ?? 0;
  const col = index % SHEET_COLS;
  const row = Math.floor(index / SHEET_COLS);
  return {
    width: size,
    height: size,
    backgroundSize: `${SHEET_COLS * size}px ${SHEET_ROWS * size}px`,
    backgroundPosition: `${-col * size}px ${-row * size}px`,
  };
}

export const Sprite = memo(function Sprite({ name, size, dim, className, style }: SpriteProps) {
  return (
    <span
      className={className ? `c-sprite ${className}` : 'c-sprite'}
      data-sprite={name}
      aria-hidden="true"
      style={{ ...spriteStyle(name, size), ...(dim != null ? { opacity: dim } : null), ...style }}
    />
  );
});
