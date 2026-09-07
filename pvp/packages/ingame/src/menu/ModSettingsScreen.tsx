/**
 * The properties model, and the body of the properties panel — contract §8.
 *
 * There is no longer a Mod-settings *screen*. The overlay interrupts a live match,
 * so every step back costs time: selecting a mod opens the inspector beside the
 * items and nothing ever navigates away. What used to be a full screen is now the
 * content of that inspector, and this file is where it lives because this is where
 * the "which settings does this mod expose" knowledge already was.
 *
 * §8 in full:
 *
 *   Spatial properties (position, size, placement) ALWAYS live on the preview as
 *   drag handles or corner slots, never as list rows. Whatever remains gets the
 *   simplest structure that fits:
 *
 *     1 property    no list at all — the preview takes the room
 *     2–4           flat list, no section labels
 *     5+            two light groups
 *     tabs          never
 *
 * The count is derived, not declared, so a mod that gains or loses a setting in
 * `mods.json` changes structure on its own and no one has to remember to move it.
 */

import { useRef } from 'react';

import { KeybindChip, PositionChips, Swatches, Toggle } from '@/ui';
import {
  HUD_MOD_IDS,
  MOD_REGISTRY,
  type HUDAnchor,
  type HUDModId,
  type ModId,
} from '@/bridge/protocol';
import { SETTING_ENUMS, SETTING_RANGES, hueStyle } from '@/registry';
import { hudItem, useModSettings, useVoidStore, type SettingValue } from '@/store/store';
import { HudKeystrokes } from '@/hud/widgets';
import { CellMeter } from './CellMeter';
import { SETTING_SUBTITLES, formatSetting, keybindLabel, settingLabel } from './settings-format';

/* -------------------------------------------------------------------------- */
/* Swatch sets                                                                */
/* -------------------------------------------------------------------------- */

/**
 * `keystrokes.key_color` / `keystrokes.pressed_color` are enums of swatch *names*
 * in mods.json, not hex — a name survives a theme change, a hex would freeze the
 * palette into the bundle. They are deliberately absent from `SETTING_ENUMS`,
 * which drives the generic chip row.
 */
const KEY_COLOURS = [
  { id: 'shell', color: 'var(--bg-shell)', label: 'Shell' },
  { id: 'raised', color: 'var(--surface-raised)', label: 'Raised' },
  { id: 'pill', color: 'var(--card-bg)', label: 'Pill' },
  { id: 'sky', color: 'var(--hue-visual)', label: 'Ice' },
  { id: 'teal', color: 'var(--hue-utility)', label: 'Mint' },
];

const PRESSED_COLOURS = [
  { id: 'accent', color: 'var(--hue, var(--accent))', label: 'Hue' },
  { id: 'sky', color: 'var(--hue-visual)', label: 'Ice' },
  { id: 'warn', color: 'var(--hue-pvp)', label: 'Coral' },
  { id: 'fear', color: 'var(--hue-pvp)', label: 'Fear' },
  { id: 'teal', color: 'var(--hue-utility)', label: 'Mint' },
];

/** `fps.color`, `hitboxes.color`, `crosshair.color` are `hex_color` — #RRGGBB(AA). */
const HEX_COLOURS = [
  { id: '#FFFFFF', color: '#FFFFFF', label: 'White' },
  { id: '#9F8BFF', color: 'var(--hue-hud)', label: 'Violet' },
  { id: '#7ADFFF', color: 'var(--hue-visual)', label: 'Ice' },
  { id: '#7AE0B0', color: 'var(--hue-utility)', label: 'Mint' },
  { id: '#FF9E7A', color: 'var(--hue-pvp)', label: 'Coral' },
];

/** mods.json accepts `#RRGGBB` and `#RRGGBBAA`, upper or lower case. */
function hexId(value: SettingValue): string {
  return typeof value === 'string' ? value.slice(0, 7).toUpperCase() : '';
}

/* -------------------------------------------------------------------------- */
/* The property model                                                         */
/* -------------------------------------------------------------------------- */

/**
 * Settings that never appear in the list, whatever the count.
 *
 * `scale` and `size` are how big the thing is, and a mod's placement is where it
 * is — both are answered by looking at the preview, so §8 puts them on it. The
 * HUD anchor is not a settings key at all; it lives in `loadout.hud[]` and is
 * written through `commitHud`, which is why it is not in this set.
 */
export const SPATIAL_KEYS: ReadonlySet<string> = new Set(['scale', 'size']);

/** How a property is drawn. */
export type PropertyKind = 'number' | 'boolean' | 'enum' | 'keybind' | 'swatch' | 'hex';

/** One row of the properties panel. */
export interface Property {
  key: string;
  kind: PropertyKind;
  label: string;
  sub?: string;
  /** Which light group it falls in, once there are enough to need two. */
  group: 'appearance' | 'behaviour';
}

/**
 * Reading order. Layout, not data: `Object.keys` follows whatever order the bridge
 * happened to send, which puts `Show space bar` above `Show CPS`.
 */
const ORDER = [
  'opacity',
  'corner_radius',
  'gamma',
  'thickness',
  'gap',
  'line_width',
  'fov_divisor',
  'decimals',
  'style',
  'layout',
  'orientation',
  'color',
  'key_color',
  'pressed_color',
  'keybind',
  'key',
  'mode',
  'window_ms',
  'good_ms',
  'bad_ms',
  'show_mouse',
  'show_cps',
  'show_spacebar',
  'show_sneak',
  'show_label',
  'show_duration',
  'show_amplifier',
  'hide_ambient',
  'show_durability',
  'show_held_item',
  'show_direction',
  'show_status',
  'show_eye_line',
  'sneak_too',
  'outline',
  'dynamic',
  'smooth',
  'cinematic',
];

/** Non-boolean keys that still describe behaviour rather than appearance. */
const BEHAVIOUR_KEYS = new Set(['keybind', 'key', 'mode', 'window_ms', 'good_ms', 'bad_ms']);

function kindOf(id: ModId, key: string, value: SettingValue): PropertyKind {
  if (key === 'keybind' || key === 'key') return 'keybind';
  if (key === 'key_color' || key === 'pressed_color') return 'swatch';
  if (key === 'color') return 'hex';
  if (typeof value === 'boolean') return 'boolean';
  if (SETTING_ENUMS[`${id}.${key}`]) return 'enum';
  if (SETTING_RANGES[key]) return 'number';
  return 'enum';
}

/**
 * The mod's properties, spatial ones removed and in reading order.
 *
 * `on` is not a property: it is enablement, and it lives on the panel header as the
 * one switch that is always in the same place whatever mod is selected.
 */
export function modProperties(
  id: ModId,
  settings: Record<string, SettingValue>,
): Property[] {
  const keys = Object.keys(settings).filter(
    (key) => key !== 'on' && !SPATIAL_KEYS.has(key),
  );
  keys.sort((a, b) => {
    const ia = ORDER.indexOf(a);
    const ib = ORDER.indexOf(b);
    return (ia < 0 ? ORDER.length : ia) - (ib < 0 ? ORDER.length : ib);
  });
  return keys.map((key) => {
    const kind = kindOf(id, key, settings[key] ?? null);
    return {
      key,
      kind,
      label: settingLabel(key),
      sub: SETTING_SUBTITLES[key],
      group: kind === 'boolean' || BEHAVIOUR_KEYS.has(key) ? 'behaviour' : 'appearance',
    };
  });
}

/** Which of §8's three structures a property count asks for. Never tabs. */
export type PropertyStructure = 'sentence' | 'flat' | 'grouped';

/** §8's table, as a function. */
export function propertyStructure(count: number): PropertyStructure {
  if (count <= 1) return 'sentence';
  if (count <= 4) return 'flat';
  return 'grouped';
}

/* -------------------------------------------------------------------------- */
/* Controls                                                                   */
/* -------------------------------------------------------------------------- */

function PropertyControl({
  id,
  property,
  settings,
  write,
}: {
  id: ModId;
  property: Property;
  settings: Record<string, SettingValue>;
  write: (key: string, value: SettingValue) => void;
}): React.ReactElement {
  const captureKeybind = useVoidStore((s) => s.captureKeybind);
  const value = settings[property.key] ?? null;

  switch (property.kind) {
    case 'boolean':
      return (
        <Toggle
          checked={value === true}
          size="l"
          label={property.label}
          onChange={(next) => write(property.key, next)}
        />
      );
    case 'keybind':
      return (
        <KeybindChip
          value={keybindLabel(value)}
          onCapture={() => captureKeybind(id)}
          // bridge.json: the capture call does not store the key — the UI does.
          onChange={(key) => write(property.key, key)}
        />
      );
    case 'swatch':
      return (
        <Swatches
          swatches={property.key === 'key_color' ? KEY_COLOURS : PRESSED_COLOURS}
          value={String(value ?? '')}
          onChange={(next) => write(property.key, next)}
        />
      );
    case 'hex':
      return (
        <Swatches
          swatches={HEX_COLOURS}
          value={hexId(value)}
          onChange={(next) => write(property.key, next)}
        />
      );
    case 'number': {
      const range = SETTING_RANGES[property.key]!;
      const numeric = Number(value ?? range.min);
      return (
        <CellMeter
          label={property.label}
          value={numeric}
          min={range.min}
          max={range.max}
          step={range.step}
          format={(next) => formatSetting(property.key, next)}
          onChange={(next) => write(property.key, next)}
        />
      );
    }
    default:
      return (
        <PositionChips
          options={(SETTING_ENUMS[`${id}.${property.key}`] ?? []).map((option) => ({
            id: option,
            label: option.charAt(0).toUpperCase() + option.slice(1).replace(/_/g, ' '),
          }))}
          value={String(value ?? '')}
          onChange={(next) => write(property.key, next)}
        />
      );
  }
}

function PropertyRow(props: {
  id: ModId;
  property: Property;
  settings: Record<string, SettingValue>;
  write: (key: string, value: SettingValue) => void;
}): React.ReactElement {
  return (
    <div className="mprop">
      <span className="mprop__labels">
        <span className="mprop__label">{props.property.label}</span>
        {props.property.sub ? <span className="mprop__sub">{props.property.sub}</span> : null}
      </span>
      <span className="mprop__control">
        <PropertyControl {...props} />
      </span>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* The preview, and the spatial properties that live on it                    */
/* -------------------------------------------------------------------------- */

/** The four corner slots, in the order the preview draws them. */
const CORNERS: Array<{ id: HUDAnchor; label: string }> = [
  { id: 'top-left', label: 'Top left' },
  { id: 'top-right', label: 'Top right' },
  { id: 'bottom-left', label: 'Bottom left' },
  { id: 'bottom-right', label: 'Bottom right' },
];

/** `top-left` → `Top left`. */
export function anchorLabel(anchor: HUDAnchor): string {
  const words = anchor.split('-');
  const first = words[0]!;
  return [first.charAt(0).toUpperCase() + first.slice(1), ...words.slice(1)].join(' ');
}

/**
 * `top-left` → `TL`, the form the list layout uses.
 *
 * The frames print the two-letter tag in both places a placement appears in a
 * table — the list's `POSITION` column and the properties panel's corner chips —
 * and the column is only wide enough for that: at this frame's proportions the
 * column is 61px, where `Bottom left` wraps onto a second line inside a row whose
 * height is fixed. The long form stays on the preview caption, which has the room
 * and is the one place the word is being read rather than scanned.
 */
export function anchorShortLabel(anchor: HUDAnchor): string {
  const [first, second] = anchor.split('-');
  return `${first?.charAt(0) ?? ''}${second?.charAt(0) ?? ''}`.toUpperCase();
}

/**
 * The size property, as a drag handle on the preview's corner (§8).
 *
 * Drag is plain pointer bookkeeping on `mousemove`, not an animation frame loop —
 * §9 bans `requestAnimationFrame` outright and the 20 Hz `tick` is the only clock.
 * A write only goes out when the *snapped* value changes, so a slow drag across one
 * step costs one bridge call and one repaint rather than one per pixel.
 */
function SizeHandle({
  label,
  value,
  min,
  max,
  step,
  onChange,
}: {
  label: string;
  value: number;
  min: number;
  max: number;
  step: number;
  onChange: (next: number) => void;
}): React.ReactElement {
  const latest = useRef(value);

  const snap = (raw: number): number => {
    const stepped = step > 0 ? Math.round((raw - min) / step) * step + min : raw;
    return Math.round(Math.min(max, Math.max(min, stepped)) * 1e6) / 1e6;
  };

  const onMouseDown = (event: React.MouseEvent) => {
    event.preventDefault();
    const startX = event.clientX;
    const startY = event.clientY;
    const startValue = value;
    latest.current = value;
    // 200px of diagonal travel walks the whole range — the same feel whatever the units.
    const perPixel = (max - min) / 200;

    const move = (moved: MouseEvent) => {
      const delta = (moved.clientX - startX + (moved.clientY - startY)) / 2;
      const next = snap(startValue + delta * perPixel);
      if (next === latest.current) return;
      latest.current = next;
      onChange(next);
    };
    const up = () => {
      window.removeEventListener('mousemove', move);
      window.removeEventListener('mouseup', up);
    };
    window.addEventListener('mousemove', move);
    window.addEventListener('mouseup', up);
  };

  const nudge = (direction: number) => {
    const next = snap(value + direction * (step || (max - min) / 20));
    if (next !== value) onChange(next);
  };

  return (
    <button
      type="button"
      className="preview__handle"
      aria-label={label}
      onMouseDown={onMouseDown}
      onKeyDown={(event) => {
        if (event.key === 'ArrowRight' || event.key === 'ArrowUp') {
          event.preventDefault();
          nudge(1);
        } else if (event.key === 'ArrowLeft' || event.key === 'ArrowDown') {
          event.preventDefault();
          nudge(-1);
        }
      }}
    >
      <span className="preview__handle-grip" />
    </button>
  );
}

/** Props for {@link ModPreview}. */
export interface ModPreviewProps {
  id: ModId;
  /** The 1-property case gives the preview the room the list would have taken. */
  large?: boolean;
}

/**
 * The live preview, and every spatial property with it.
 *
 * Placement is four corner slots; size is the drag handle at the bottom right. The
 * "game still" behind is a flat fill, never a live blurred capture — Ultralight has
 * no backdrop-filter and the host's GL pass supplies the softness (§1 of the notes).
 */
export function ModPreview({ id, large = false }: ModPreviewProps): React.ReactElement {
  const settings = useModSettings(id);
  const commitHud = useVoidStore((s) => s.commitHud);
  const setSetting = useVoidStore((s) => s.setSetting);
  const placement = useVoidStore((s) => hudItem(s.loadout, id as HUDModId));
  const isHud = (HUD_MOD_IDS as readonly string[]).includes(id);

  // One handle, whichever size key the mod has. `scale` is the HUD widgets' own
  // multiplier; `size` is the crosshair's pixel size. Both are "how big", so both
  // are answered by dragging the preview rather than by reading a row.
  const sizeKey = 'scale' in settings ? 'scale' : 'size' in settings ? 'size' : null;
  const sizeRange = sizeKey ? SETTING_RANGES[sizeKey] : undefined;

  return (
    <div className={`preview${large ? ' preview--large' : ''}`}>
      <div className="preview__frame">
        <div className="preview__stage">
          {id === 'keystrokes' ? (
            <HudKeystrokes className="v-keystrokes--preview" />
          ) : (
            <span className="preview__blurb">{MOD_REGISTRY[id].description}</span>
          )}
        </div>

        {/* §8: placement never appears as a list row. Frame `289:5523` draws it as
            four labelled slots in the corners they stand for, the live one filled —
            the answer is about a corner, so the control is the corner. */}
        {isHud && placement
          ? CORNERS.map((corner) => (
              <button
                key={corner.id}
                type="button"
                className={`preview__slot preview__slot--${corner.id}${
                  placement.anchor === corner.id ? ' preview__slot--live' : ''
                }`}
                aria-label={corner.label}
                aria-pressed={placement.anchor === corner.id}
                onClick={() =>
                  commitHud(
                    id as HUDModId,
                    corner.id,
                    corner.id.endsWith('right') ? -25 : 25,
                    corner.id.startsWith('bottom') ? -25 : 25,
                    placement.scale ?? 1,
                  )
                }
              >
                {anchorShortLabel(corner.id)}
              </button>
            ))
          : null}

        {sizeKey && sizeRange ? (
          <SizeHandle
            label={settingLabel(sizeKey)}
            value={Number(settings[sizeKey] ?? sizeRange.min)}
            min={sizeRange.min}
            max={sizeRange.max}
            step={sizeRange.step}
            onChange={(next) => setSetting(id, sizeKey, next)}
          />
        ) : null}
      </div>

      {/* The caption sits *under* the frame in every in-game frame, and says what
          the two things on the preview do rather than naming the preview twice. */}
      <div className="preview__bar">
        <span className="preview__caption">
          {isHud && placement
            ? 'Click a slot to place   ·   drag a corner to scale'
            : sizeKey
              ? 'Drag the handle to resize'
              : 'Live preview'}
        </span>
        <span className="preview__meta tnum">
          {[
            placement ? anchorLabel(placement.anchor) : '—',
            sizeKey && sizeRange
              ? formatSetting(sizeKey, Number(settings[sizeKey] ?? sizeRange.min))
              : '—',
            'opacity' in settings ? formatSetting('opacity', Number(settings.opacity ?? 1)) : '—',
          ].join('   ·   ')}
        </span>
      </div>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* The properties body                                                        */
/* -------------------------------------------------------------------------- */

/**
 * A single property, said as a sentence rather than drawn as a row.
 *
 * One row with a label on the left and a control on the right is a list of one,
 * and §8 says a list of one is not a list.
 */
function propertySentence(property: Property, value: SettingValue): [string, string, string] {
  if (property.kind === 'boolean') {
    return [`${property.label} is `, value === true ? 'on' : 'off', '.'];
  }
  return [`${property.label} sits at `, formatSetting(property.key, value), '.'];
}

/** Props for {@link ModProperties}. */
export interface ModPropertiesProps {
  id: ModId;
}

/**
 * The properties of one mod, in whichever of §8's structures its count asks for.
 *
 * The mod's category hue is set here, on the root, so every accent underneath —
 * the live meter cell, the readout, the corner slot, the drag handle — reads
 * `var(--hue, var(--accent))` and a Visual mod's live value comes out ice.
 */
export function ModProperties({ id }: ModPropertiesProps): React.ReactElement {
  const settings = useModSettings(id);
  const setSetting = useVoidStore((s) => s.setSetting);
  const write = (key: string, value: SettingValue) => setSetting(id, key, value);

  const properties = modProperties(id, settings);
  const structure = propertyStructure(properties.length);

  if (structure === 'sentence') {
    const only = properties[0];
    return (
      <div className="mprops mprops--sentence" style={hueStyle(id)} data-structure="sentence">
        <ModPreview id={id} large />
        {only ? (
          <>
            <p className="mprops__sentence">
              {(() => {
                const [head, live, tail] = propertySentence(only, settings[only.key] ?? null);
                return (
                  <>
                    {head}
                    <span className="mprops__live tnum">{live}</span>
                    {tail}
                  </>
                );
              })()}
            </p>
            <PropertyControl id={id} property={only} settings={settings} write={write} />
          </>
        ) : (
          <p className="mprops__sentence">Nothing to set — this mod is on or it is off.</p>
        )}
      </div>
    );
  }

  if (structure === 'flat') {
    return (
      <div className="mprops" style={hueStyle(id)} data-structure="flat">
        <ModPreview id={id} />
        <div className="mprops__list">
          {properties.map((property) => (
            <PropertyRow
              key={property.key}
              id={id}
              property={property}
              settings={settings}
              write={write}
            />
          ))}
        </div>
      </div>
    );
  }

  const appearance = properties.filter((p) => p.group === 'appearance');
  const behaviour = properties.filter((p) => p.group === 'behaviour');
  // Two groups, always — never three, never tabs (§8). A split that lands empty on
  // one side is worse than no split, so it falls back to one column.
  const split =
    appearance.length > 0 && behaviour.length > 0 ? [appearance, behaviour] : [properties];

  // No captions. Frame `289:3024` runs the two groups as two columns of rows with a
  // dotted cell rule under each row and no section labels: at this width the column
  // *is* the grouping, and a caption over it would be naming what the eye already
  // sees. (The narrow panels of `289:5523` do caption their groups — the list
  // layout, which is that width, gets the same stacked treatment in CSS.)
  return (
    <div className="mprops" style={hueStyle(id)} data-structure="grouped">
      <ModPreview id={id} />
      {split.map((items, column) => (
        <div className="mprops__group" key={column}>
          <div className="mprops__list">
            {items.map((property) => (
              <PropertyRow
                key={property.key}
                id={id}
                property={property}
                settings={settings}
                write={write}
              />
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}
