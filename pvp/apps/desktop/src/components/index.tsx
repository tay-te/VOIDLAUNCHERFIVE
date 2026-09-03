/**
 * The launcher's local port of the shared component set.
 *
 * TODO(integrate): every component in this file is one `@void/ui` owns — Switch,
 * Slider, FilterTabs, Panel, Kbd, StatusDot, IconWell, Button, SearchField, ModTile,
 * ModSettingsPane. `packages/ui` has no built `dist` yet (its `src/` is empty), so
 * importing it would break `pnpm typecheck` here.
 *
 * When it ships, this file becomes a re-export and the CSS classes below move with it:
 *
 * ```ts
 * export { Switch, Slider, FilterTabs, Panel, Kbd, ... } from '@void/ui';
 * ```
 *
 * Geometry, radii, shadows and type sizes come straight from
 * `design/README.md` "Component inventory" and are named after the tokens they use, so
 * the port is a mechanical move rather than a redesign.
 */

import type { ComponentType, ReactNode } from 'react';
import { useEffect, useRef, useState } from 'react';

import { CheckIcon, ChevronDown, SearchIcon } from './icons';

// ------------------------------------------------------------------ shared atoms

export function Kbd({ children, tone = 'nav' }: { children: ReactNode; tone?: 'nav' | 'accent' | 'palette' }) {
  return <kbd className={`kbd kbd--${tone}`}>{children}</kbd>;
}

export function StatusDot({
  tone = 'ok',
  size = 7,
}: {
  tone?: 'ok' | 'warn' | 'bad' | 'muted' | 'accent';
  size?: number;
}) {
  return <span className={`dot dot--${tone}`} style={{ width: size, height: size }} />;
}

export function Divider({ height = 36 }: { height?: number }) {
  return <span className="divider" style={{ height }} aria-hidden="true" />;
}

export function IconWell({
  icon: Icon,
  active = false,
  size = 34,
  glyph = 16,
}: {
  icon: ComponentType<{ size?: number }>;
  active?: boolean;
  size?: number;
  glyph?: number;
}) {
  const radius = size >= 44 ? 13 : size >= 34 ? 10 : size >= 30 ? 8 : 7;
  return (
    <span
      className={`icon-well${active ? ' is-active' : ''}`}
      style={{ width: size, height: size, borderRadius: radius }}
    >
      <Icon size={glyph} />
    </span>
  );
}

/**
 * The category tag on a ModTile, and the `ACTIVE` / `NEW` / `LEADER` badges.
 * One component because they are one shape at different tints.
 */
export function Tag({
  children,
  tone = 'muted',
}: {
  children: ReactNode;
  tone?: 'muted' | 'accent' | 'ok' | 'warn';
}) {
  return <span className={`tag tag--${tone}`}>{children}</span>;
}

export function Eyebrow({
  tone = 'ok',
  children,
}: {
  tone?: 'ok' | 'warn';
  children: ReactNode;
}) {
  return (
    <span className="eyebrow">
      <StatusDot tone={tone} size={7} />
      <span className="eyebrow__text">{children}</span>
    </span>
  );
}

/**
 * A Minecraft-style avatar. Deliberately not an `<img>` to a skin service by default:
 * the launcher must render with no network. When a `src` is given (a real skin head
 * URL from the account) it is used, otherwise a deterministic blocky placeholder keyed
 * off the name — the same name always gets the same face.
 */
export function Avatar({ name, src, size = 32 }: { name: string; src?: string | null; size?: number }) {
  const radius = size >= 44 ? 13 : size >= 36 ? 11 : size >= 32 ? 10 : 8;
  if (src) {
    return (
      <img className="avatar" src={src} alt="" width={size} height={size} style={{ borderRadius: radius }} />
    );
  }
  const hue = [...name].reduce((acc, c) => (acc * 31 + c.charCodeAt(0)) % 360, 7);
  return (
    <span
      className="avatar avatar--placeholder"
      style={{
        width: size,
        height: size,
        borderRadius: radius,
        background: `linear-gradient(150deg, hsl(${hue} 38% 42%), hsl(${(hue + 40) % 360} 32% 26%))`,
        fontSize: Math.round(size * 0.42),
      }}
      aria-hidden="true"
    >
      {name.slice(0, 1).toUpperCase()}
    </span>
  );
}

// ---------------------------------------------------------------------- controls

export function Button({
  variant = 'raised',
  icon: Icon,
  children,
  full = false,
  ...rest
}: {
  variant?: 'accent' | 'raised' | 'ghost' | 'text' | 'chip' | 'chip-accent';
  icon?: ComponentType<{ size?: number }>;
  children?: ReactNode;
  full?: boolean;
} & React.ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      type="button"
      {...rest}
      className={`btn btn--${variant}${full ? ' btn--full' : ''}${rest.className ? ` ${rest.className}` : ''}`}
    >
      {Icon ? <Icon size={13} /> : null}
      {children}
    </button>
  );
}

export function IconButton({
  icon: Icon,
  size = 34,
  glyph = 14,
  label,
  ...rest
}: {
  icon: ComponentType<{ size?: number }>;
  size?: number;
  glyph?: number;
  label: string;
} & React.ButtonHTMLAttributes<HTMLButtonElement>) {
  const radius = size >= 44 ? 13 : size >= 34 ? 9 : 8;
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      {...rest}
      className={`icon-btn${rest.className ? ` ${rest.className}` : ''}`}
      style={{ width: size, height: size, borderRadius: radius, ...rest.style }}
    >
      <Icon size={glyph} />
    </button>
  );
}

/** Three sizes, per the inventory: S on tiles, M on pane headers, L on settings rows. */
export function Switch({
  checked,
  onChange,
  size = 'm',
  label,
  disabled = false,
}: {
  checked: boolean;
  onChange: (next: boolean) => void;
  size?: 's' | 'm' | 'l';
  label: string;
  disabled?: boolean;
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={`switch switch--${size}${checked ? ' is-on' : ''}`}
    >
      <span className="switch__knob" />
    </button>
  );
}

export function Slider({
  value,
  min,
  max,
  step = 0.01,
  onChange,
  label,
  display,
  wide = false,
}: {
  value: number;
  min: number;
  max: number;
  step?: number;
  onChange: (next: number) => void;
  label: string;
  display: string;
  wide?: boolean;
}) {
  const pct = max === min ? 0 : ((value - min) / (max - min)) * 100;
  return (
    <div className={`slider${wide ? ' slider--wide' : ''}`}>
      <div className="slider__head">
        <span className="slider__label">{label}</span>
        <span className="slider__value">{display}</span>
      </div>
      <div className="slider__track">
        <span className="slider__fill" style={{ width: `${pct}%` }} />
        <input
          type="range"
          aria-label={label}
          value={value}
          min={min}
          max={max}
          step={step}
          onChange={(e) => onChange(Number(e.target.value))}
        />
        <span className="slider__thumb" style={{ left: `${pct}%` }} />
      </div>
    </div>
  );
}

/** The `position chip` row of the settings group, reused for every enum setting. */
export function Segmented<T extends string>({
  value,
  options,
  onChange,
  label,
}: {
  value: T;
  options: readonly T[];
  onChange: (next: T) => void;
  label: string;
}) {
  return (
    <div className="segmented" role="radiogroup" aria-label={label}>
      {options.map((option) => (
        <button
          key={option}
          type="button"
          role="radio"
          aria-checked={option === value}
          className={`segmented__chip${option === value ? ' is-selected' : ''}`}
          onClick={() => onChange(option)}
        >
          {option.replace(/_/g, ' ')}
        </button>
      ))}
    </div>
  );
}

/**
 * A keybind chip that captures the next key press.
 *
 * TODO(integrate): in game this is `void.openKeybindCapture(modId)`, which returns the
 * LWJGL 2 key name the game actually saw (`bridge.json`). Here there is no game, so we
 * map the browser's `KeyboardEvent.code` onto the same `keybind` pattern the schema
 * accepts. The mapping lives in `keyName` below and is the piece to delete once the
 * bridge can answer.
 */
export function KeybindChip({
  value,
  onChange,
  label,
}: {
  value: string;
  onChange: (next: string) => void;
  label: string;
}) {
  const [capturing, setCapturing] = useState(false);
  const ref = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!capturing) return;
    const onKey = (e: KeyboardEvent) => {
      e.preventDefault();
      e.stopPropagation();
      setCapturing(false);
      onChange(e.key === 'Escape' ? 'NONE' : keyName(e));
    };
    window.addEventListener('keydown', onKey, { capture: true });
    return () => window.removeEventListener('keydown', onKey, { capture: true });
  }, [capturing, onChange]);

  return (
    <button
      ref={ref}
      type="button"
      aria-label={label}
      className={`keybind${capturing ? ' is-capturing' : ''}`}
      onClick={() => setCapturing((c) => !c)}
    >
      {capturing ? 'Press a key…' : prettyKey(value)}
    </button>
  );
}

/** Browser `KeyboardEvent` → the LWJGL 2 names `mods.json#/definitions/keybind` allows. */
export function keyName(e: KeyboardEvent): string {
  const code = e.code;
  if (/^Key[A-Z]$/.test(code)) return code.slice(3);
  if (/^Digit[0-9]$/.test(code)) return code.slice(5);
  if (/^F([1-9]|1[0-2])$/.test(code)) return code;
  if (/^Numpad[0-9]$/.test(code)) return code.replace('Numpad', 'NUMPAD');
  const map: Record<string, string> = {
    Space: 'SPACE',
    Tab: 'TAB',
    Escape: 'ESCAPE',
    Enter: 'RETURN',
    Backspace: 'BACK',
    Delete: 'DELETE',
    Insert: 'INSERT',
    Home: 'HOME',
    End: 'END',
    PageUp: 'PRIOR',
    PageDown: 'NEXT',
    ArrowUp: 'UP',
    ArrowDown: 'DOWN',
    ArrowLeft: 'LEFT',
    ArrowRight: 'RIGHT',
    ShiftLeft: 'LSHIFT',
    ShiftRight: 'RSHIFT',
    ControlLeft: 'LCONTROL',
    ControlRight: 'RCONTROL',
    AltLeft: 'LMENU',
    AltRight: 'RMENU',
    CapsLock: 'CAPITAL',
    BracketLeft: 'LBRACKET',
    BracketRight: 'RBRACKET',
    Semicolon: 'SEMICOLON',
    Quote: 'APOSTROPHE',
    Comma: 'COMMA',
    Period: 'PERIOD',
    Slash: 'SLASH',
    Backslash: 'BACKSLASH',
    Minus: 'MINUS',
    Equal: 'EQUALS',
    Backquote: 'GRAVE',
  };
  return map[code] ?? 'NONE';
}

/** `RSHIFT` → `R-Shift`, the way the Figma prints it. */
export function prettyKey(value: string): string {
  if (!value || value === 'NONE') return 'None';
  const named: Record<string, string> = {
    RSHIFT: 'R-Shift',
    LSHIFT: 'L-Shift',
    RCONTROL: 'R-Ctrl',
    LCONTROL: 'L-Ctrl',
    LMENU: 'L-Alt',
    RMENU: 'R-Alt',
    RETURN: 'Enter',
    PRIOR: 'Page Up',
    NEXT: 'Page Down',
    CAPITAL: 'Caps',
    GRAVE: '`',
  };
  if (named[value]) return named[value];
  if (value.startsWith('MOUSE')) return `Mouse ${value.slice(5)}`;
  if (value.length === 1) return value;
  return value.charAt(0) + value.slice(1).toLowerCase();
}

export function ColorSwatches({
  value,
  onChange,
  label,
}: {
  value: string;
  onChange: (next: string) => void;
  label: string;
}) {
  // The swatch row of the mod-settings frame.
  const colors = ['#FFFFFFFF', '#9F8BFFFF', '#3DD68CFF', '#D9A93AFF', '#C05B54FF', '#4D87CDFF'];
  const normalised = value.length === 7 ? `${value}FF` : value.toUpperCase();
  return (
    <div className="swatches" role="radiogroup" aria-label={label}>
      {colors.map((c) => (
        <button
          key={c}
          type="button"
          role="radio"
          aria-checked={c === normalised}
          aria-label={c}
          className={`swatch${c === normalised ? ' is-selected' : ''}`}
          style={{ background: c.slice(0, 7) }}
          onClick={() => onChange(c)}
        />
      ))}
    </div>
  );
}

export function SearchField({
  value,
  onChange,
  placeholder,
  width,
  onSubmit,
}: {
  value: string;
  onChange: (next: string) => void;
  placeholder: string;
  width?: number;
  onSubmit?: (value: string) => void;
}) {
  return (
    <div className="search" style={width ? { width } : undefined}>
      <SearchIcon size={13} />
      <input
        value={value}
        placeholder={placeholder}
        aria-label={placeholder}
        onChange={(e) => onChange(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter' && onSubmit) onSubmit(value);
        }}
      />
    </div>
  );
}

// ------------------------------------------------------------------------ layout

export function FilterTabs<T extends string>({
  tabs,
  value,
  onChange,
  counts,
}: {
  tabs: readonly T[];
  value: T;
  onChange: (next: T) => void;
  counts?: Partial<Record<T, { value: number; tone?: 'ok' }>>;
}) {
  return (
    <div className="tabs" role="tablist">
      {tabs.map((tab) => {
        const count = counts?.[tab];
        return (
          <button
            key={tab}
            type="button"
            role="tab"
            aria-selected={tab === value}
            className={`tab${tab === value ? ' is-selected' : ''}`}
            onClick={() => onChange(tab)}
          >
            {tab}
            {count ? (
              <span className={`tab__count${count.tone === 'ok' ? ' tab__count--ok' : ''}`}>
                {count.value}
              </span>
            ) : null}
          </button>
        );
      })}
    </div>
  );
}

/** The 960 × 596 panel every screen but Play lives inside. */
export function Panel({
  title,
  controls,
  footer,
  children,
}: {
  title: string;
  controls?: ReactNode;
  footer?: ReactNode;
  children: ReactNode;
}) {
  return (
    <section className="panel">
      <header className="panel__head">
        <h1 className="panel__title">{title}</h1>
        {controls}
      </header>
      <div className="panel__body">{children}</div>
      {footer ? <footer className="panel__footer">{footer}</footer> : null}
    </section>
  );
}

/** The 308 px right-hand pane of Servers / Friends, and the 278 px Mods variant. */
export function Pane({
  narrow = false,
  children,
}: {
  narrow?: boolean;
  children: ReactNode;
}) {
  return <aside className={`pane${narrow ? ' pane--narrow' : ''}`}>{children}</aside>;
}

export function Caption({ children, count }: { children: ReactNode; count?: ReactNode }) {
  return (
    <div className="caption">
      <span>{children}</span>
      {count !== undefined ? <span className="caption__count">· {count}</span> : null}
    </div>
  );
}

export function StatTile({ value, unit }: { value: ReactNode; unit: string }) {
  return (
    <div className="stat-tile">
      <span className="stat-tile__value">{value}</span>
      <span className="stat-tile__unit">{unit}</span>
    </div>
  );
}

/**
 * Twelve plain `div` bars — the pattern `ultralight-notes.md` §5 mandates instead of a
 * canvas, and the launcher uses it too so the two renderers stay identical.
 */
export function Sparkline({ values, warnAbove = 100 }: { values: number[]; warnAbove?: number }) {
  const padded = [...values];
  while (padded.length < 12) padded.unshift(0);
  const window = padded.slice(-12);
  const max = Math.max(32, ...window);
  return (
    <div className="sparkline" aria-hidden="true">
      {window.map((v, i) => (
        <span
          key={i}
          className={`sparkline__bar${v > warnAbove ? ' is-warn' : ''}${
            i === window.length - 1 ? ' is-current' : ''
          }`}
          style={{ height: v === 0 ? 4 : Math.max(6, Math.round((v / max) * 32)) }}
        />
      ))}
    </div>
  );
}

export function Row({
  title,
  sub,
  children,
}: {
  title: ReactNode;
  sub?: ReactNode;
  children?: ReactNode;
}) {
  return (
    <div className="settings-row">
      <div className="settings-row__label">
        <span className="settings-row__title">{title}</span>
        {sub ? <span className="settings-row__sub">{sub}</span> : null}
      </div>
      <div className="settings-row__control">{children}</div>
    </div>
  );
}

export function Group({ caption, children }: { caption: string; children: ReactNode }) {
  return (
    <section className="group">
      <div className="group__cap">{caption}</div>
      {children}
    </section>
  );
}

/** A dropdown built from a button + list, since the design's pills are menus. */
export function Menu({
  label,
  eyebrow,
  value,
  icon: Icon,
  items,
  onSelect,
  disabled = false,
}: {
  label: string;
  eyebrow: string;
  value: string;
  icon?: ComponentType<{ size?: number }>;
  items: { id: string; label: string; disabled?: boolean; hint?: string }[];
  onSelect: (id: string) => void;
  disabled?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    window.addEventListener('mousedown', onDown);
    window.addEventListener('keydown', onKey);
    return () => {
      window.removeEventListener('mousedown', onDown);
      window.removeEventListener('keydown', onKey);
    };
  }, [open]);

  return (
    <div className="menu" ref={ref}>
      <button
        type="button"
        className={`pill${open ? ' is-open' : ''}`}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-label={label}
        disabled={disabled}
        onClick={() => setOpen((o) => !o)}
      >
        {Icon ? <Icon size={14} /> : null}
        <span className="pill__text">
          <span className="pill__eyebrow">{eyebrow}</span>
          <span className="pill__value">{value}</span>
        </span>
        <ChevronDown size={14} />
      </button>
      {open ? (
        <ul className="menu__list" role="listbox">
          {items.map((item) => (
            <li key={item.id}>
              <button
                type="button"
                role="option"
                aria-selected={item.label === value}
                disabled={item.disabled}
                className={`menu__item${item.label === value ? ' is-current' : ''}`}
                onClick={() => {
                  onSelect(item.id);
                  setOpen(false);
                }}
              >
                <span>{item.label}</span>
                {item.hint ? <span className="menu__hint">{item.hint}</span> : null}
                {item.label === value ? <CheckIcon size={13} /> : null}
              </button>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}
