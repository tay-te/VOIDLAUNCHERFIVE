/**
 * The settings bench — a mod's settings as a wide side panel beside the shelves (Figma 597:2,
 * keybind capture with a conflict 598:2).
 *
 *   head      [sprite] Keystrokes  ● HUD · KEY K                [switch] [Esc]
 *   preview   the real widget on a little Minecraft scene, Day / Night / Nether / Water
 *   columns   SHOW / BEHAVIOUR (switches, behaviour meters)  ·  KEYBIND + LOOK (meters, options,
 *             swatches)
 *   foot      Move in HUD layout  H      Reset      Pin
 *
 * The property model is `modProperties` (ModSettingsScreen.tsx) — the same keys, kinds and
 * groups the v1 page drew, so every setting a mod has is here and nothing is invented. The
 * preview is the mod's own `ModArt.Preview`: for an HTML widget the identical component the HUD
 * places, for a world mod the diagram fed by the game's numbers.
 *
 * The scene behind the preview is flat fills (no image, no blur): it exists so a player can
 * check a colour against daylight, night, the Nether and water before a fight — the four places
 * the fight HUD frames were checked for legibility.
 */

import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react';

import { HUD_MOD_IDS, MOD_REGISTRY, type HUDModId, type ModId } from '@/bridge/protocol';
import { SETTING_ENUMS, SETTING_RANGES, modLabel } from '@/registry';
import { MAX_QUICK_PINS, isModOn, quickPins, useModSettings, useVoidStore, type SettingValue } from '@/store/store';
import { FLAG_SETTINGS, hasFlag, modProperties, toggleFlag, type Property } from '@/menu/ModSettingsScreen';
import { CellMeter } from '@/menu/CellMeter';
import { formatSetting, keybindLabel, settingLabel } from '@/menu/settings-format';
import { keybindOverview } from '@/menu/keybind-overview';
import { modArt } from '@/mods';
import { hudChrome } from '@/hud/chrome';
import { cx } from '@/ui';
import { CART_KEY_COLORS, CART_PRESSED_COLORS } from '@/hud/cart/Keys';
import { Sprite, hasSprite } from './Sprite';
import { CATEGORY_LABEL, Kc, Led, Switch, catClass, categoryOf } from './prims';
import { conflictFor, type KeyConflict } from './keys';
import { modKeySetting } from './Cartridge';

export type Biome = 'day' | 'night' | 'nether' | 'water';
const BIOMES: readonly { id: Biome; label: string }[] = [
  { id: 'day', label: 'Day' },
  { id: 'night', label: 'Night' },
  { id: 'nether', label: 'Nether' },
  { id: 'water', label: 'Water' },
];

const HEX_SWATCHES: readonly { id: string; color: string; label: string }[] = [
  { id: '#FFFFFF', color: '#eceef1', label: 'White' },
  { id: '#9F8BFF', color: '#9f8bff', label: 'Violet' },
  { id: '#FF9E7A', color: '#ff9e7a', label: 'Coral' },
  { id: '#7ADFFF', color: '#7adfff', label: 'Ice' },
  { id: '#7AE0B0', color: '#7ae0b0', label: 'Mint' },
];

/** `#RRGGBB(AA)` → `#RRGGBB`, upper case. */
function hexId(value: SettingValue): string {
  return typeof value === 'string' ? value.slice(0, 7).toUpperCase() : '';
}

/** The size key a mod has, if any — `scale` for HUD widgets, `size` for the crosshair. */
function sizeKeyOf(settings: Record<string, SettingValue>): 'scale' | 'size' | null {
  if ('scale' in settings) return 'scale';
  if ('size' in settings) return 'size';
  return null;
}

/* -------------------------------------------------------------------------- */
/* Preview                                                                    */
/* -------------------------------------------------------------------------- */

/** How big the bench draws a widget: the mod's page zoom, tempered to the bench's 240px stage. */
function benchZoom(id: ModId): number {
  const page = modArt(id)?.previewZoom;
  if (page === undefined) return 0.62; // world diagrams are authored at page size
  return Math.max(1, Math.min(2.2, page * 0.62));
}

export function BenchPreview({ id, biome, onBiome }: { id: ModId; biome: Biome; onBiome: (b: Biome) => void }) {
  const settings = useModSettings(id);
  const Live = modArt(id)?.Preview ?? null;
  const isWidget =
    id === 'crosshair' || modArt(id)?.previewZoom !== undefined || (HUD_MOD_IDS as readonly string[]).includes(id);
  const scale = benchZoom(id) * Number(settings.scale ?? 1);
  const opacity = Number(settings.opacity ?? 1);
  return (
    <div className={cx('c-prev', `c-prev--${biome}`)} data-biome={biome}>
      <div className="c-prev__scene" aria-hidden="true">
        <i className="c-prev__sky" />
        <i className="c-prev__orb" />
        <i className="c-prev__hill c-prev__hill--a" />
        <i className="c-prev__hill c-prev__hill--b" />
        <i className="c-prev__ground" />
        {biome === 'night' ? (
          <>
            <i className="c-prev__star" style={{ left: 64, top: 30 }} />
            <i className="c-prev__star" style={{ left: 180, top: 62 }} />
            <i className="c-prev__star" style={{ left: 300, top: 22 }} />
            <i className="c-prev__star" style={{ left: 410, top: 70 }} />
          </>
        ) : null}
        {biome === 'nether' ? <i className="c-prev__lava" /> : null}
        {biome === 'water' ? (
          <>
            <i className="c-prev__bubble" style={{ left: 96, top: 120 }} />
            <i className="c-prev__bubble" style={{ left: 420, top: 90 }} />
            <i className="c-prev__bubble" style={{ left: 450, top: 140 }} />
          </>
        ) : null}
      </div>
      <div className="c-prev__stage">
        {Live ? (
          <div
            className={cx('c-prev__zoom', hudChrome(settings), !isWidget && 'c-prev__zoom--diagram')}
            style={{ zoom: scale, opacity } as React.CSSProperties}
          >
            <Live variant="editor" sample />
          </div>
        ) : null}
      </div>
      <span className="c-prev__live">
        <Led on className="c-led--live" />
        <span className="c-mono">{isWidget ? 'Live · the real widget' : 'Live · drawn from the game’s numbers'}</span>
      </span>
      <div className="c-prev__biomes" role="radiogroup" aria-label="Preview backdrop">
        {BIOMES.map((b) => (
          <button
            key={b.id}
            type="button"
            role="radio"
            aria-checked={biome === b.id}
            className={cx('c-prev__biome', biome === b.id && 'is-on')}
            onClick={() => onBiome(b.id)}
          >
            {b.label}
          </button>
        ))}
      </div>
      <div className="c-prev__ruler" aria-hidden="true">
        {Array.from({ length: 41 }, (_, i) => (
          <i key={i} className={i % 5 === 0 ? 'is-major' : undefined} />
        ))}
      </div>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Keybind capture                                                            */
/* -------------------------------------------------------------------------- */

type CaptureState =
  | { phase: 'idle' }
  | { phase: 'listening' }
  | { phase: 'conflict'; key: string; conflict: KeyConflict };

export interface KeyCaptureProps {
  /** Shown above the block. */
  label: string;
  /** The stored LWJGL key name (`NONE` when unbound). */
  value: string;
  /** The binding's id in the keybind overview, so it does not clash with itself. */
  selfId: string;
  /** Arms Java's capture; resolves with the key, or null when cancelled. */
  capture: () => Promise<string | null>;
  /** Stores the key. */
  write: (key: string) => void;
  /** Offers `Unbind` when the binding may be empty. */
  allowNone?: boolean;
  /** Test / screenshot hook: start in a given state. */
  initial?: CaptureState;
  /** The keybinds panel's narrow form: one wide Rebind and an Unbind, no caption row. */
  compact?: boolean;
}

export function KeyCapture({ label, value, selfId, capture, write, allowNone = true, initial, compact }: KeyCaptureProps) {
  const loadout = useVoidStore((s) => s.loadout);
  const globals = useVoidStore((s) => s.globals);
  const [state, setState] = useState<CaptureState>(initial ?? { phase: 'idle' });
  const alive = useRef(true);
  useEffect(
    () => () => {
      alive.current = false;
    },
    [],
  );

  const start = () => {
    setState({ phase: 'listening' });
    void capture()
      .then((key) => {
        if (!alive.current) return;
        if (!key) {
          setState({ phase: 'idle' });
          return;
        }
        const conflict = conflictFor(key, keybindOverview(useVoidStore.getState().loadout, useVoidStore.getState().globals), selfId);
        if (conflict) setState({ phase: 'conflict', key, conflict });
        else {
          write(key);
          setState({ phase: 'idle' });
        }
      })
      .catch(() => {
        if (alive.current) setState({ phase: 'idle' });
      });
  };

  // Recompute once so a stale conflict (the other binding moved) is not offered.
  void loadout;
  void globals;

  const current = keybindLabel(value);
  return (
    <>
      {label ? <span className="c-mono c-col__cap">{label}</span> : null}
      {state.phase === 'listening' ? (
        <div className="c-blk c-cap c-cap--listen" role="status">
          <div className="c-cap__keys">
            <Kc>{current}</Kc>
            <span className="c-cap__arrow">→</span>
            <Kc tone="listen" large>
              …
            </Kc>
          </div>
          <span className="c-label">Listening · press a key</span>
          <span className="c-cap__hint">Esc cancels</span>
        </div>
      ) : state.phase === 'conflict' ? (
        <>
          <div className="c-blk c-cap c-cap--listen">
            <div className="c-cap__keys">
              <Kc>{current}</Kc>
              <span className="c-cap__arrow">→</span>
              <Kc tone="dn" large>
                {keybindLabel(state.key)}
              </Kc>
            </div>
            <span className="c-label">Listening · pressed {keybindLabel(state.key)}</span>
            <span className="c-cap__hint">Hold a modifier to combine · Esc cancels</span>
          </div>
          <div className="c-blk c-warnblk" role="alert">
            <div className="c-warnblk__head">
              <span className="c-warnblk__led" />
              <span className="c-label">
                {state.conflict.kind === 'vanilla'
                  ? `${keybindLabel(state.key)} ${state.conflict.does} (vanilla default)`
                  : `${keybindLabel(state.key)} is already ${state.conflict.label}`}
              </span>
            </div>
            <p className="c-warnblk__line">
              {state.conflict.kind === 'vanilla'
                ? `Binding it here means both happen on every press. Use ${keybindLabel(state.key)} anyway?`
                : `Both fire on one press. Use ${keybindLabel(state.key)} for this as well?`}
            </p>
            <div className="c-warnblk__actions">
              <button
                type="button"
                className="c-btn c-btn--p c-btn--sm"
                onClick={() => {
                  write(state.key);
                  setState({ phase: 'idle' });
                }}
              >
                Use {keybindLabel(state.key)}
              </button>
              <button type="button" className="c-btn c-btn--g c-btn--sm" onClick={start}>
                Pick another
              </button>
            </div>
          </div>
        </>
      ) : compact ? (
        <div className="c-cap__row c-cap__row--compact">
          <button type="button" className="c-btn c-btn--p c-cap__wide" onClick={start}>
            Rebind
          </button>
          {allowNone && value && value !== 'NONE' ? (
            <button type="button" className="c-btn c-btn--g" onClick={() => write('NONE')}>
              Unbind
            </button>
          ) : null}
        </div>
      ) : (
        <div className="c-blk c-cap">
          <div className="c-cap__row">
            <Kc large tone={value && value !== 'NONE' ? 'idle' : 'idle'}>
              {current}
            </Kc>
            <span className="c-cap__what">{value && value !== 'NONE' ? 'Bound' : 'Not bound'}</span>
            <span className="c-cap__spacer" />
            {allowNone && value && value !== 'NONE' ? (
              <button type="button" className="c-btn c-btn--g c-btn--sm" onClick={() => write('NONE')}>
                Unbind
              </button>
            ) : null}
            <button type="button" className="c-btn c-btn--p c-btn--sm" onClick={start}>
              Rebind
            </button>
          </div>
        </div>
      )}
    </>
  );
}

/* -------------------------------------------------------------------------- */
/* Controls                                                                   */
/* -------------------------------------------------------------------------- */

function MeterBlock({ label, value, min, max, step, format, onChange }: {
  label: string;
  value: number;
  min: number;
  max: number;
  step: number;
  format: (v: number) => string;
  onChange: (v: number) => void;
}) {
  return (
    <div className="c-blk c-meterblk">
      <div className="c-blk__top">
        <span className="c-label">{label}</span>
        <span className="c-num">{format(value)}</span>
      </div>
      <CellMeter className="c-meter" label={label} value={value} min={min} max={max} step={step} cells={16} onChange={onChange} />
    </div>
  );
}

function Swatches({ label, swatches, value, onChange }: {
  label: string;
  swatches: readonly { id: string; color: string; label: string }[];
  value: string;
  onChange: (id: string) => void;
}) {
  return (
    <div className="c-blk c-swblk">
      <span className="c-label">{label}</span>
      <span className="c-swatches" role="radiogroup" aria-label={label}>
        {swatches.map((s) => (
          <button
            key={s.id}
            type="button"
            role="radio"
            aria-checked={value === s.id}
            aria-label={s.label}
            title={s.label}
            className={cx('c-swatch', value === s.id && 'is-on')}
            style={{ background: s.color }}
            onClick={() => onChange(s.id)}
          />
        ))}
      </span>
    </div>
  );
}

function optionLabel(option: string): string {
  const spaced = option.replace(/_/g, ' ');
  return spaced.charAt(0).toUpperCase() + spaced.slice(1);
}

function Control({ id, property, settings, write }: {
  id: ModId;
  property: Property;
  settings: Record<string, SettingValue>;
  write: (key: string, value: SettingValue) => void;
}): ReactNode {
  const value = settings[property.key] ?? null;
  switch (property.kind) {
    case 'boolean':
      return (
        <div className={cx('c-row2', value !== true && 'is-off')}>
          <span className="c-row2__label" title={property.sub}>
            {property.label}
          </span>
          <Switch checked={value === true} label={property.label} onChange={(next) => write(property.key, next)} />
        </div>
      );
    case 'number': {
      const range = SETTING_RANGES[property.key]!;
      return (
        <MeterBlock
          label={property.label}
          value={Number(value ?? range.min)}
          min={range.min}
          max={range.max}
          step={range.step}
          format={(v) => formatSetting(property.key, v)}
          onChange={(v) => write(property.key, v)}
        />
      );
    }
    case 'swatch': {
      // The keycap's own resolution (hud/cart/Keys.tsx), so a swatch shows the colour the key takes.
      const table = property.key === 'key_color' ? CART_KEY_COLORS : CART_PRESSED_COLORS;
      return (
        <Swatches
          label={property.label}
          swatches={Object.entries(table).map(([sid, entry]) => ({ id: sid, color: entry.bg, label: entry.label }))}
          value={String(value ?? '')}
          onChange={(next) => write(property.key, next)}
        />
      );
    }
    case 'hex': {
      const current = hexId(value);
      const list = HEX_SWATCHES.some((s) => s.id === current) || !current
        ? HEX_SWATCHES
        : [...HEX_SWATCHES, { id: current, color: current, label: `Current ${current}` }];
      return <Swatches label={property.label} swatches={list} value={current} onChange={(next) => write(property.key, next)} />;
    }
    case 'flags': {
      const mask = Number(value ?? 0);
      const flags = FLAG_SETTINGS[`${id}.${property.key}`] ?? [];
      return (
        <div className="c-blk c-flagblk">
          <span className="c-label">{property.label}</span>
          <div className="c-opts c-opts--wrap" role="group" aria-label={property.label}>
            {flags.map((flag) => {
              const lit = hasFlag(mask, flag.bit);
              return (
                <button
                  key={flag.bit}
                  type="button"
                  role="checkbox"
                  aria-checked={lit}
                  className={cx('c-opt', lit && 'is-on')}
                  onClick={() => write(property.key, toggleFlag(mask, flag.bit))}
                >
                  {flag.label}
                </button>
              );
            })}
          </div>
        </div>
      );
    }
    case 'keybind':
      return null; // drawn by the keybind block
    default: {
      const options = SETTING_ENUMS[`${id}.${property.key}`] ?? [];
      return (
        <div className="c-blk c-optblk">
          <span className="c-label">{property.label}</span>
          {options.length === 0 ? (
            <span className="c-num">{String(value ?? '—')}</span>
          ) : (
            <div className={cx('c-opts', options.length > 4 && 'c-opts--wrap')} role="radiogroup" aria-label={property.label}>
              {options.map((option) => (
                <button
                  key={option}
                  type="button"
                  role="radio"
                  aria-checked={value === option}
                  className={cx('c-opt', value === option && 'is-on')}
                  onClick={() => write(property.key, option)}
                >
                  {optionLabel(option)}
                </button>
              ))}
            </div>
          )}
        </div>
      );
    }
  }
}

/* -------------------------------------------------------------------------- */
/* The bench                                                                  */
/* -------------------------------------------------------------------------- */

export interface BenchProps {
  id: ModId;
  /** Screenshot / test hook: open the first keybind in a given capture state. */
  captureInitial?: CaptureState;
}

export function Bench({ id, captureInitial }: BenchProps) {
  const settings = useModSettings(id);
  const on = useVoidStore((s) => isModOn(s.loadout, id));
  const pinned = useVoidStore((s) => quickPins(s.loadout).includes(id));
  const pinsFull = useVoidStore((s) => quickPins(s.loadout).length >= MAX_QUICK_PINS);
  const toggleMod = useVoidStore((s) => s.toggleMod);
  const setSetting = useVoidStore((s) => s.setSetting);
  const resetMod = useVoidStore((s) => s.resetMod);
  const toggleQuickPin = useVoidStore((s) => s.toggleQuickPin);
  const closeMod = useVoidStore((s) => s.closeMod);
  const setRoute = useVoidStore((s) => s.setRoute);
  const setEditorTarget = useVoidStore((s) => s.setEditorTarget);
  const captureKeybind = useVoidStore((s) => s.captureKeybind);
  const [biome, setBiome] = useState<Biome>('day');

  const write = (key: string, value: SettingValue) => setSetting(id, key, value);
  const properties = useMemo(() => modProperties(id, settings), [id, settings]);
  const keybinds = properties.filter((p) => p.kind === 'keybind');
  const flags = properties.filter((p) => p.kind === 'flags');
  const rest = properties.filter((p) => p.kind !== 'keybind' && p.kind !== 'flags');
  const left = rest.filter((p) => p.group === 'behaviour');
  const right = rest.filter((p) => p.group === 'appearance');
  const sizeKey = sizeKeyOf(settings);
  const sizeRange = sizeKey ? SETTING_RANGES[sizeKey] : undefined;
  const isHud = (HUD_MOD_IDS as readonly string[]).includes(id);
  const category = categoryOf(id);
  const keySetting = modKeySetting(id);
  const keyValue = keySetting ? settings[keySetting] : null;
  const leftCaption = left.length > 0 && left.every((p) => p.kind === 'boolean' && /^(show|hide)_/.test(p.key)) ? 'Show' : 'Behaviour';

  // Balance: with nothing on the right, half the left column moves over.
  let leftCol = left;
  let rightCol = right;
  if (rightCol.length === 0 && keybinds.length === 0 && !sizeKey && leftCol.length > 4) {
    const half = Math.ceil(leftCol.length / 2);
    rightCol = leftCol.slice(half);
    leftCol = leftCol.slice(0, half);
  }

  const empty = properties.length === 0 && !sizeKey;
  const hasRight = keybinds.length > 0 || Boolean(sizeKey) || rightCol.length > 0;
  // One column with nothing in it reads as broken; it says what the mod does instead.
  const about = (
    <>
      <span className="c-mono c-col__cap">About</span>
      <div className="c-blk c-about">
        <p>{MOD_REGISTRY[id]?.description}</p>
      </div>
    </>
  );

  return (
    <aside className={cx('c-bench', catClass(category))} aria-label={`${modLabel(id)} settings`}>
      <div className="c-bench__head">
        <span className="c-bench__bed">{hasSprite(id) ? <Sprite name={id} size={32} /> : null}</span>
        <span className="c-bench__titles">
          <span className="c-title">{modLabel(id)}</span>
          <span className="c-bench__meta">
            <Led on />
            <span className="c-mono">
              {CATEGORY_LABEL[category]} · {keyValue && keyValue !== 'NONE' ? `key ${keybindLabel(keyValue)}` : 'no key'}
              {MOD_REGISTRY[id]?.hypixel_safe === 'grey' ? ' · grey on Hypixel' : ''}
            </span>
          </span>
        </span>
        <Switch checked={on} label={`${modLabel(id)} — ${on ? 'on' : 'off'}`} onChange={(next) => toggleMod(id, next)} />
        <button type="button" className="c-btn c-btn--g c-btn--sm c-bench__close" onClick={closeMod} aria-label="Close settings">
          <Kc>Esc</Kc>
        </button>
      </div>

      <BenchPreview id={id} biome={biome} onBiome={setBiome} />

      <div className="c-bench__body">
        {empty ? (
          <p className="c-bench__none">{MOD_REGISTRY[id]?.description ?? ''} Nothing to set — it is on or it is off.</p>
        ) : (
          <div className="c-bench__cols">
            <div className="c-col">
              {leftCol.length === 0 ? about : null}
              {leftCol.length > 0 ? <span className="c-mono c-col__cap">{leftCaption}</span> : null}
              {leftCol.map((p) => (
                <Control key={p.key} id={id} property={p} settings={settings} write={write} />
              ))}
            </div>
            <div className="c-col">
              {keybinds.map((p) => (
                <KeyCapture
                  key={p.key}
                  label={keybinds.length > 1 ? settingLabel(p.key) : 'Keybind'}
                  value={String(settings[p.key] ?? 'NONE')}
                  selfId={`${id}.${p.key}`}
                  capture={() => captureKeybind(id) as Promise<string | null>}
                  write={(key) => write(p.key, key)}
                  initial={p === keybinds[0] ? captureInitial : undefined}
                />
              ))}
              {sizeKey || rightCol.length > 0 ? <span className="c-mono c-col__cap">Look</span> : null}
              {sizeKey && sizeRange ? (
                <MeterBlock
                  label={settingLabel(sizeKey)}
                  value={Number(settings[sizeKey] ?? sizeRange.min)}
                  min={sizeRange.min}
                  max={sizeRange.max}
                  step={sizeRange.step}
                  format={(v) => formatSetting(sizeKey, v)}
                  onChange={(v) => write(sizeKey, v)}
                />
              ) : null}
              {rightCol.map((p) => (
                <Control key={p.key} id={id} property={p} settings={settings} write={write} />
              ))}
              {!hasRight ? about : null}
            </div>
          </div>
        )}
        {flags.map((p) => (
          <Control key={p.key} id={id} property={p} settings={settings} write={write} />
        ))}
      </div>

      <div className="c-bench__foot">
        {isHud ? (
          <button
            type="button"
            className="c-btn c-btn--s c-bench__move"
            onClick={() => {
              setEditorTarget(id as HUDModId);
              setRoute({ name: 'hud-editor' });
            }}
          >
            Move in HUD layout <Kc>H</Kc>
          </button>
        ) : (
          <span className="c-bench__move c-bench__move--none c-mono">Applies the moment you change it</span>
        )}
        <button type="button" className="c-btn c-btn--g" onClick={() => resetMod(id)}>
          Reset
        </button>
        <button
          type="button"
          className="c-btn c-btn--g"
          aria-pressed={pinned}
          disabled={!pinned && pinsFull}
          title={!pinned && pinsFull ? `The Pinned shelf holds ${MAX_QUICK_PINS} — unpin one first` : undefined}
          onClick={() => toggleQuickPin(id)}
        >
          {pinned ? 'Unpin' : 'Pin'} <Kc>P</Kc>
        </button>
      </div>
    </aside>
  );
}

export type { CaptureState };
