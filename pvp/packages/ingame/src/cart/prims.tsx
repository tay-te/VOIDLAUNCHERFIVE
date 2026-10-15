/**
 * Cartridge primitives: keycap, LED, stepped meter, slide switch and the two line glyphs.
 *
 * Every mark is a box or a border — no inline SVG (ultralight-notes §7 rates SVG strokes
 * [risky]); the sliders and search glyphs are a few divs each.
 */

import { memo, type CSSProperties, type ReactNode } from 'react';

import type { ModId } from '@/bridge/protocol';
import { MOD_REGISTRY } from '@/bridge/protocol';
import { cx } from '@/ui';

export type CartCategory = 'hud' | 'pvp' | 'visual' | 'utility';

export const CATEGORIES: readonly CartCategory[] = ['hud', 'pvp', 'visual', 'utility'];

export const CATEGORY_LABEL: Record<CartCategory, string> = {
  hud: 'HUD',
  pvp: 'PvP',
  visual: 'Visual',
  utility: 'Utility',
};

export function categoryOf(id: ModId): CartCategory {
  return (MOD_REGISTRY[id]?.category ?? 'hud') as CartCategory;
}

/** The class that sets `--c-hue` for a category (cart/tokens.css). */
export function catClass(category: CartCategory): string {
  return `c-cat-${category}`;
}

/* -------------------------------------------------------------------------- */

export interface KcProps {
  children: ReactNode;
  tone?: 'idle' | 'lt' | 'dn' | 'listen' | 'warn';
  large?: boolean;
  className?: string;
  title?: string;
}

/** A keycap: Mono 600 with a 2px lip; pressed loses the lip and travels a pixel. */
export const Kc = memo(function Kc({ children, tone = 'idle', large, className, title }: KcProps) {
  return (
    <span
      className={cx('c-kc', tone !== 'idle' && `c-kc--${tone}`, large && 'c-kc--lg', className)}
      title={title}
    >
      {children}
    </span>
  );
});

/** A 7px LED. Lit takes the surrounding category hue (`--c-hue`). */
export const Led = memo(function Led({
  on,
  className,
  style,
}: {
  on: boolean;
  className?: string;
  style?: CSSProperties;
}) {
  return <span className={cx('c-led', on && 'c-led--on', className)} style={style} aria-hidden="true" />;
});

export interface SegProps {
  /** Total segments. */
  n: number;
  /** Lit segments, from the left. */
  on: number;
  w?: number;
  h?: number;
  gap?: number;
  /** Lit colour; default ink. */
  color?: string;
  /** Unlit colour; default the surface's off step. */
  off?: string;
  className?: string;
}

/** A stepped meter: plain divs, cheaper than anything eased in this engine. */
export const Seg = memo(function Seg({ n, on, w = 7, h = 14, gap = 2, color, off, className }: SegProps) {
  const lit = Math.max(0, Math.min(n, Math.round(on)));
  const cells: ReactNode[] = [];
  for (let i = 0; i < n; i += 1) {
    cells.push(
      <i
        key={i}
        className={i < lit ? 'on' : undefined}
        style={{
          width: w,
          height: h,
          marginRight: i === n - 1 ? 0 : gap,
          ...(i < lit && color ? { background: color } : null),
          ...(i >= lit && off ? { background: off } : null),
        }}
      />,
    );
  }
  return (
    <span className={cx('c-seg', className)} aria-hidden="true">
      {cells}
    </span>
  );
});

export interface SwitchProps {
  checked: boolean;
  onChange?: (next: boolean) => void;
  label: string;
  disabled?: boolean;
  className?: string;
}

/** The slide switch: the knob jumps in three pixel steps (cart/tokens.css). */
export function Switch({ checked, onChange, label, disabled, className }: SwitchProps) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      className={cx('c-sw', checked && 'is-on', className)}
      onClick={(event) => {
        event.stopPropagation();
        if (!disabled) onChange?.(!checked);
      }}
    >
      <b />
    </button>
  );
}

/** The cartridge's settings glyph: three rails with offset knobs. */
export function SlidersGlyph() {
  return (
    <span className="c-glyph-sliders" aria-hidden="true">
      <i />
      <i />
      <i />
      <b />
      <b />
      <b />
    </span>
  );
}

/** The search glyph: a rounded square and a handle. */
export function SearchGlyph({ lit }: { lit?: boolean }) {
  return (
    <span className={cx('c-glyph-search', lit && 'is-lit')} aria-hidden="true">
      <i />
      <b />
    </span>
  );
}
