/**
 * Cartridge HUD parts: the module, its label tab, the mono numeral, the stepped meter, the LED.
 *
 * Every HUD widget in the in-game bundle is built from these (Figma section B, "Cartridge",
 * frames B10-B13). The look, in one paragraph: a module is a 66% near-black backing at radius 9
 * with a 1px white-7% edge; a small Geist Mono label tab sits top-left; the reading is a big
 * Geist Mono numeral bottom-left with a small grey unit; meters are stepped segments, never a
 * smooth bar. Category hue appears only on live values and meters.
 *
 * Presentational only. The adapters in `hud/*.tsx` own every subscription and every setting;
 * these take plain props. `packages/ui`'s chips are *not* restyled — the desktop launcher shares
 * them and must keep its look — so these are the in-game equivalents, scoped by `.cart`
 * (cart/tokens.css) and styled in `cart/hud.css` under the `ch-` prefix.
 *
 * Ultralight (rendering-invariants §10-§11): flex and absolute only, no text-shadow, no
 * gradient, no `transform: scale()` on text. Geist Mono is monospaced, so a reading keeps its
 * width while its digits change — which `tabular-nums` cannot promise here, because it is inert.
 */

import { memo, type CSSProperties, type ReactNode } from 'react';

import { cx } from '@/ui';

/* -------------------------------------------------------------------------- */
/* Module                                                                     */
/* -------------------------------------------------------------------------- */

export interface HudModuleProps {
  /** The label tab, e.g. `FPS`. Omitted (the mod's `show_label` off) draws no tab. */
  label?: ReactNode;
  /** Right-hand side of the tab row: an LED, a host, a peak, a window meter. */
  head?: ReactNode;
  className?: string;
  style?: CSSProperties;
  /** Marks the module for tests and the editor. */
  'data-ch'?: string;
  children?: ReactNode;
}

/** The module: backing, edge, tab row, then whatever the widget draws. */
export const HudModule = memo(function HudModule({
  label,
  head,
  className,
  style,
  children,
  ...rest
}: HudModuleProps) {
  const hasHead = (label !== undefined && label !== null && label !== '') || head;
  return (
    <div className={cx('ch-mod', !hasHead && 'ch-mod--headless', className)} style={style} data-ch={rest['data-ch']}>
      {hasHead ? (
        <div className="ch-head">
          {label ? <span className="ch-tab">{label}</span> : <span />}
          {head ? <span className="ch-head__r">{head}</span> : null}
        </div>
      ) : null}
      {children}
    </div>
  );
});

/* -------------------------------------------------------------------------- */
/* Meter                                                                      */
/* -------------------------------------------------------------------------- */

export interface MeterProps {
  /** Total segments. */
  n: number;
  /** Lit segments from the left; clamped to 0..n and rounded. */
  on: number;
  /** Segment width / height / gap in px. */
  w: number;
  h: number;
  gap?: number;
  /** Lit colour (any CSS colour, `var(--…)` included). Default: the HUD ink. */
  color?: string;
  className?: string;
}

/**
 * A stepped meter over the game: plain `<i>` cells, unlit at white-10%.
 *
 * The lit colour goes on each cell as an inline `background` rather than through a custom
 * property, so it is visible in serialised markup — the preview gate (`test/preview.test.tsx`)
 * compares markup, and a colour carried by a variable it cannot see would read as inert.
 */
export const Meter = memo(function Meter({ n, on, w, h, gap = 2, color, className }: MeterProps) {
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
        }}
      />,
    );
  }
  return (
    <span className={cx('c-seg', 'ch-seg', className)} aria-hidden="true">
      {cells}
    </span>
  );
});

/** Lit segments for a 0..1 fraction: any remainder lights a segment, so "almost gone" still shows. */
export function litFor(fraction: number, n: number): number {
  if (!(fraction > 0)) return 0;
  return Math.min(n, Math.ceil(Math.min(1, fraction) * n - 1e-9));
}

/* -------------------------------------------------------------------------- */
/* LED                                                                        */
/* -------------------------------------------------------------------------- */

export type LedTone = 'off' | 'ok' | 'warn' | 'bad' | 'hue';

/** A 7px status LED. `ok` is the utility mint, `hue` the HUD violet. */
export const HudLed = memo(function HudLed({ tone }: { tone: LedTone }) {
  return <span className={cx('c-led', 'ch-led', tone !== 'off' && `ch-led--${tone}`)} aria-hidden="true" />;
});

/* -------------------------------------------------------------------------- */
/* Window meter                                                               */
/* -------------------------------------------------------------------------- */

/**
 * Four small segments in the tab row: how much of a timeout is left (`combo.reset_ms`,
 * `reach.hide_after_ms`). Four because the widgets repaint only at four band edges — the meter
 * is drawn at exactly the resolution it is updated at, so it never sits on a stale cell.
 */
export function WindowMeter({ remaining, color }: { remaining: number; color?: string }) {
  return <Meter n={4} on={litFor(remaining, 4)} w={6} h={3} gap={2} color={color} className="ch-window" />;
}

/* -------------------------------------------------------------------------- */
/* Shared bits                                                                */
/* -------------------------------------------------------------------------- */

/** Ink for a `color` setting: `#RRGGBB` / `#RRGGBBAA` straight through, anything else none. */
export function inkStyle(color: string | undefined): CSSProperties | undefined {
  return color ? { color } : undefined;
}
