/**
 * LOCAL FALLBACKS for `@void/ui`.
 *
 * `packages/ui` owns the shared component set for both bundles. It is being
 * written in parallel; until it exports these, the overlay uses the versions
 * below — same names, same props as the shared ones are expected to take, all
 * built to the geometry in `pvp/design/README.md` → "Component inventory".
 *
 * Every one of these is listed in `README.md` under "Local fallbacks" so the
 * shared package can absorb them.
 */

import {
  type CSSProperties,
  type PointerEvent as ReactPointerEvent,
  type ReactNode,
  useCallback,
  useRef,
} from 'react';
import { Icon, type IconName } from '@/icons/Icon';

/* -------------------------------------------------------------------- Switch */

export interface SwitchProps {
  on: boolean;
  /** S on a ModTile, M on a pane header, L on a settings row. */
  size?: 's' | 'm' | 'l';
  onChange?: (next: boolean) => void;
  label?: string;
  className?: string;
}

export function Switch({ on, size = 'm', onChange, label, className }: SwitchProps) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={on}
      aria-label={label}
      className={[
        'vd-switch',
        `vd-switch--${size}`,
        on ? 'vd-switch--on' : 'vd-switch--off',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
      onClick={(e) => {
        e.stopPropagation();
        onChange?.(!on);
      }}
    >
      <span className="vd-switch__knob" />
    </button>
  );
}

/* -------------------------------------------------------------------- Slider */

export interface SliderProps {
  value: number;
  min: number;
  max: number;
  step?: number;
  /** `compact` is the 200 × 14 pane slider, `wide` the 180 × 16 settings one. */
  variant?: 'compact' | 'wide';
  onChange: (next: number) => void;
  /** Fired once when the drag ends, for callers that want to commit on drop. */
  onCommit?: (next: number) => void;
  label?: string;
}

export function Slider({
  value,
  min,
  max,
  step = 0.01,
  variant = 'compact',
  onChange,
  onCommit,
  label,
}: SliderProps) {
  const ref = useRef<HTMLDivElement | null>(null);
  const latest = useRef(value);
  latest.current = value;

  const width = variant === 'compact' ? 200 : 180;
  const thumbW = 10;
  const ratio = max === min ? 0 : (value - min) / (max - min);
  const clamped = Math.min(1, Math.max(0, ratio));

  const valueAt = useCallback(
    (clientX: number) => {
      const box = ref.current?.getBoundingClientRect();
      if (!box) return latest.current;
      const t = Math.min(1, Math.max(0, (clientX - box.left) / box.width));
      const raw = min + t * (max - min);
      const snapped = Math.round(raw / step) * step;
      // Kill float dust from the step division (0.30000000000000004).
      const decimals = (String(step).split('.')[1] ?? '').length;
      return Number(Math.min(max, Math.max(min, snapped)).toFixed(decimals));
    },
    [max, min, step],
  );

  const onPointerDown = (e: ReactPointerEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.stopPropagation();
    (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
    onChange(valueAt(e.clientX));
  };

  const onPointerMove = (e: ReactPointerEvent<HTMLDivElement>) => {
    if (!(e.currentTarget as HTMLElement).hasPointerCapture(e.pointerId)) return;
    onChange(valueAt(e.clientX));
  };

  const onPointerUp = (e: ReactPointerEvent<HTMLDivElement>) => {
    if (!(e.currentTarget as HTMLElement).hasPointerCapture(e.pointerId)) return;
    (e.currentTarget as HTMLElement).releasePointerCapture(e.pointerId);
    onCommit?.(valueAt(e.clientX));
  };

  return (
    <div
      ref={ref}
      role="slider"
      aria-label={label}
      aria-valuemin={min}
      aria-valuemax={max}
      aria-valuenow={value}
      className={`vd-slider vd-slider--${variant}`}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      onPointerCancel={onPointerUp}
    >
      <span className="vd-slider__rail" />
      <span className="vd-slider__fill" style={{ width: `${clamped * 100}%` }} />
      <span
        className="vd-slider__thumb"
        style={{ left: `${clamped * (width - thumbW)}px` }}
      />
    </div>
  );
}

/* ---------------------------------------------------------------- FilterTabs */

export interface TabSpec {
  id: string;
  label: string;
  count?: number;
  /** `Requests` renders its count in --ok-ink. */
  countTone?: 'default' | 'ok';
}

export interface FilterTabsProps {
  tabs: readonly TabSpec[];
  value: string;
  onChange: (id: string) => void;
  className?: string;
}

export function FilterTabs({ tabs, value, onChange, className }: FilterTabsProps) {
  return (
    <div className={['vd-tabs', className].filter(Boolean).join(' ')} role="tablist">
      {tabs.map((tab) => (
        <button
          key={tab.id}
          type="button"
          role="tab"
          aria-selected={tab.id === value}
          className={`vd-tab${tab.id === value ? ' vd-tab--selected' : ''}`}
          onClick={() => onChange(tab.id)}
        >
          <span>{tab.label}</span>
          {tab.count !== undefined && (
            <span
              className={`vd-tab__count${tab.countTone === 'ok' ? ' vd-tab__count--ok' : ''}`}
            >
              {tab.count}
            </span>
          )}
        </button>
      ))}
    </div>
  );
}

/* ----------------------------------------------------------------- KbdChip */

export interface KbdChipProps {
  children: ReactNode;
  variant?: 'palette' | 'keybind' | 'nav';
  className?: string;
}

export function KbdChip({ children, variant = 'palette', className }: KbdChipProps) {
  return (
    <span className={['vd-kbd', `vd-kbd--${variant}`, className].filter(Boolean).join(' ')}>
      {children}
    </span>
  );
}

/* ------------------------------------------------------------------ Button */

export interface ButtonProps {
  variant?: 'accent' | 'accent-sm' | 'raised' | 'ghost' | 'chip';
  icon?: IconName;
  iconSize?: number;
  full?: boolean;
  disabled?: boolean;
  onClick?: () => void;
  children?: ReactNode;
  className?: string;
  style?: CSSProperties;
  title?: string;
}

export function Button({
  variant = 'raised',
  icon,
  iconSize = 14,
  full,
  disabled,
  onClick,
  children,
  className,
  style,
  title,
}: ButtonProps) {
  return (
    <button
      type="button"
      title={title}
      disabled={disabled}
      style={style}
      className={['vd-btn', `vd-btn--${variant}`, full ? 'vd-btn--full' : '', className]
        .filter(Boolean)
        .join(' ')}
      onClick={onClick}
    >
      {icon && <Icon name={icon} size={iconSize} />}
      {children}
    </button>
  );
}

/* --------------------------------------------------------------- CloseButton */

export function CloseButton({ onClick }: { onClick: () => void }) {
  return (
    <button type="button" className="vd-close" aria-label="Close" onClick={onClick}>
      <Icon name="x" size={14} />
    </button>
  );
}

/* ----------------------------------------------------------------- IconWell */

export interface IconWellProps {
  icon: IconName;
  /** 24 / 30 / 34 / 44 in the design, with radii 7 / 8 / 10 / 13. */
  size: 24 | 30 | 34 | 44;
  on?: boolean;
  glyph?: number;
  className?: string;
}

const WELL_RADIUS: Record<number, string> = {
  24: 'var(--radius-swatch)',
  30: 'var(--radius-chip)',
  34: 'var(--radius-field)',
  44: 'var(--radius-avatar)',
};

const WELL_GLYPH: Record<number, number> = { 24: 12, 30: 16, 34: 16, 44: 22 };

export function IconWell({ icon, size, on, glyph, className }: IconWellProps) {
  return (
    <span
      className={['vd-well', on ? 'vd-well--on' : '', className].filter(Boolean).join(' ')}
      style={{ width: size, height: size, borderRadius: WELL_RADIUS[size] }}
    >
      <Icon name={icon} size={glyph ?? WELL_GLYPH[size]} />
    </span>
  );
}

/* ------------------------------------------------------------ GroupCaption */

export function GroupCaption({ label, count }: { label: string; count?: string }) {
  return (
    <div className="vd-caption">
      <span>{label}</span>
      {count !== undefined && <span className="vd-caption__count">· {count}</span>}
    </div>
  );
}

/* ----------------------------------------------------------------- Badge */

export function Badge({
  children,
  tone = 'accent',
}: {
  children: ReactNode;
  tone?: 'accent' | 'ok';
}) {
  return <span className={`vd-badge vd-badge--${tone}`}>{children}</span>;
}

/* ------------------------------------------------------------- SearchField */

export interface SearchFieldProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  width?: number;
  autoFocus?: boolean;
  onKeyDown?: (e: React.KeyboardEvent<HTMLInputElement>) => void;
  inputRef?: React.Ref<HTMLInputElement>;
}

export function SearchField({
  value,
  onChange,
  placeholder = 'Search',
  width,
  autoFocus,
  onKeyDown,
  inputRef,
}: SearchFieldProps) {
  return (
    <div className="vd-search" style={width ? { width } : undefined}>
      <Icon name="search" size={13} />
      <input
        ref={inputRef}
        value={value}
        placeholder={placeholder}
        autoFocus={autoFocus}
        spellCheck={false}
        onChange={(e) => onChange(e.target.value)}
        onKeyDown={onKeyDown}
      />
    </div>
  );
}

/* ------------------------------------------------------------- StatusDot */

export function StatusDot({ size = 7, color }: { size?: number; color: string }) {
  return (
    <span
      style={{
        width: size,
        height: size,
        borderRadius: 999,
        background: color,
        flex: '0 0 auto',
        display: 'block',
      }}
    />
  );
}
