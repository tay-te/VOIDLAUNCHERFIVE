/**
 * Cartridge keystrokes: 48px keycaps with a 3px lip, straight on the game (no module backing —
 * every cap is its own backing, which is what keeps the labels legible over any sky).
 *
 * Pressed is a light fill with dark ink, no lip, and 2px of travel by `translateY` — never
 * `scale()`, which garbles a run of glyphs in this engine (rendering-invariants §10/§10a). Only
 * the changed cap's class changes on a `keys` push; nothing else in the widget re-renders.
 */

import { memo, type CSSProperties, type ReactNode } from 'react';

import { cx } from '@/ui';

export interface CartKeysState {
  w?: boolean;
  a?: boolean;
  s?: boolean;
  d?: boolean;
  lmb?: boolean;
  rmb?: boolean;
  space?: boolean;
  shift?: boolean;
}

/**
 * `keystrokes.key_color`, resolved for Cartridge. The enum of names is the registry's
 * (`shell | raised | pill | sky | teal`); these are what each name paints over the game.
 * `shell` is the design's own cap (Figma B10). The light caps carry dark ink.
 *
 * Exported so the settings swatches can draw from the same table as the widget — a swatch that
 * showed one colour while the cap took another is how these settings once went dead.
 */
export const CART_KEY_COLORS: Readonly<Record<string, { bg: string; ink: string; label: string }>> = {
  shell: { bg: 'rgba(22, 24, 28, 0.78)', ink: '#c4c8cf', label: 'Shell' },
  raised: { bg: 'rgba(48, 53, 61, 0.86)', ink: '#eceef1', label: 'Raised' },
  pill: { bg: 'rgba(12, 13, 15, 0.92)', ink: '#9ba0a8', label: 'Pill' },
  sky: { bg: 'rgba(122, 223, 255, 0.82)', ink: '#0c0d0f', label: 'Ice' },
  teal: { bg: 'rgba(122, 224, 176, 0.82)', ink: '#0c0d0f', label: 'Mint' },
};

/** `keystrokes.pressed_color`, resolved the same way. `accent` is the design's light press. */
export const CART_PRESSED_COLORS: Readonly<Record<string, { bg: string; ink: string; label: string }>> = {
  accent: { bg: 'rgba(236, 238, 241, 0.95)', ink: '#0c0d0f', label: 'Light' },
  sky: { bg: '#7adfff', ink: '#0c0d0f', label: 'Ice' },
  warn: { bg: '#e8b86a', ink: '#0c0d0f', label: 'Amber' },
  fear: { bg: '#ff6b5e', ink: '#0c0d0f', label: 'Fear' },
  teal: { bg: '#7ae0b0', ink: '#0c0d0f', label: 'Mint' },
};

export interface CartKeysLook {
  keyColor?: string | null;
  pressedColor?: string | null;
  /** `corner_radius`, px. */
  radius?: number | null;
  /** Release time in ms (`animate` off → 0). */
  fadeMs: number;
}

/**
 * The inline custom properties that paint the pad in the loadout's chosen look. Unknown names
 * write nothing, so the stylesheet's defaults stand.
 */
export function cartKeysStyle({ keyColor, pressedColor, radius, fadeMs }: CartKeysLook): CSSProperties {
  const style: Record<string, string> = {};
  const key = keyColor ? CART_KEY_COLORS[keyColor] : undefined;
  const pressed = pressedColor ? CART_PRESSED_COLORS[pressedColor] : undefined;
  if (key) {
    style['--ch-key-bg'] = key.bg;
    style['--ch-key-ink'] = key.ink;
  }
  if (pressed) {
    style['--ch-key-dn'] = pressed.bg;
    style['--ch-key-dn-ink'] = pressed.ink;
  }
  if (typeof radius === 'number' && Number.isFinite(radius)) style['--ch-key-r'] = `${Math.max(0, radius)}px`;
  style['--ch-key-fade'] = `${Math.max(0, fadeMs)}ms`;
  return style as CSSProperties;
}

export interface CartKeystrokesProps {
  keys: CartKeysState;
  showMouse: boolean;
  showSpacebar: boolean;
  showSneak: boolean;
  /** CPS under the mouse labels — `show_cps`. */
  cps?: { left: number; right: number };
  style?: CSSProperties;
  className?: string;
}

function Key({
  label,
  down,
  modifier,
  sub,
}: {
  label: ReactNode;
  down: boolean | undefined;
  modifier?: string;
  sub?: ReactNode;
}) {
  return (
    <span className={cx('ch-key', modifier, down && 'ch-key--dn')}>
      {label}
      {sub === undefined ? null : <small className="ch-key__sub">{sub}</small>}
    </span>
  );
}

export const CartKeystrokes = memo(function CartKeystrokes({
  keys,
  showMouse,
  showSpacebar,
  showSneak,
  cps,
  style,
  className,
}: CartKeystrokesProps) {
  return (
    <div className={cx('ch-keys', className)} style={style}>
      <div className="ch-keys__row ch-keys__row--w">
        <Key label="W" down={keys.w} />
      </div>
      <div className="ch-keys__row">
        <Key label="A" down={keys.a} />
        <Key label="S" down={keys.s} />
        <Key label="D" down={keys.d} />
      </div>
      {showMouse ? (
        <div className="ch-keys__row">
          <Key label="LMB" down={keys.lmb} modifier="ch-key--wide" sub={cps ? cps.left : undefined} />
          <Key label="RMB" down={keys.rmb} modifier="ch-key--wide" sub={cps ? cps.right : undefined} />
        </div>
      ) : null}
      {/* One row for both feet: the bar keeps the full width alone and yields a cap's width to
          sneak beside it. Labels are words, not ␣ / ⇧ — Geist Mono has neither glyph. */}
      {showSpacebar || showSneak ? (
        <div className="ch-keys__row ch-keys__row--foot">
          {showSpacebar ? <Key label="" down={keys.space} modifier="ch-key--space" /> : null}
          {showSneak ? <Key label="SNK" down={keys.shift} modifier="ch-key--sneak" /> : null}
        </div>
      ) : null}
    </div>
  );
});
