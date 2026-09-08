/**
 * The properties model, and the body of the mod page — contract §8.
 *
 * This file owns "which settings does this mod expose, and what shape do they take"; the page
 * around it (head, enablement, Reset / Done) is `ModPage.tsx`.
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
 *
 * **§8 only started meaning anything when the properties got the page.** In the inspector all
 * three structures came out as the same narrow column — the panel was whatever was left after
 * the grid contracted, so "the preview takes the room" was a 208px strip and "two light groups"
 * was two columns barely wide enough for a meter. At full width they are three genuinely
 * different shapes, and the arrangement is stated here as a row and read by the CSS off
 * `data-structure`:
 *
 *     sentence   the preview alone, as large as the page allows, and one line under it
 *     flat       preview left, one column of rows right
 *     grouped    preview left, two captioned columns right
 *
 * The captions are new and they are the width's doing. `289:3024` runs the two groups as bare
 * columns because at that width the column *is* the grouping; standing beside a preview at
 * page width it is not obvious that two columns are two groups rather than one list flowed
 * into two, so they are named — which is what the narrow panels of `289:5523` already do.
 */

import { useRef } from 'react';

import {
  KEYCAP_COLORS,
  KEYCAP_PRESSED_COLORS,
  KeybindChip,
  PositionChips,
  Swatches,
  Toggle,
} from '@/ui';
import {
  HUD_MOD_IDS,
  MOD_REGISTRY,
  type HUDAnchor,
  type HUDModId,
  type ModId,
} from '@/bridge/protocol';
import { SETTING_ENUMS, SETTING_RANGES, hueStyle } from '@/registry';
import { hudItem, useModSettings, useVoidStore, type SettingValue } from '@/store/store';
import {
  HudArmorStatus,
  HudCoordinates,
  HudCps,
  HudCrosshair,
  HudFps,
  HudKeystrokes,
  HudPing,
  HudPotionEffects,
  type HudWidgetProps,
} from '@/hud/widgets';
import { HudWatermark } from '@/hud/watermark';
import {
  FullbrightPreview,
  HitboxPreview,
  SprintPreview,
  ZoomPreview,
} from './gameplay-previews';
import { CellMeter } from './CellMeter';
import { TilePreview } from './TilePreview';
import { SETTING_SUBTITLES, formatSetting, keybindLabel, settingLabel } from './settings-format';

/* -------------------------------------------------------------------------- */
/* Swatch sets                                                                */
/* -------------------------------------------------------------------------- */

/**
 * `keystrokes.key_color` / `keystrokes.pressed_color` are enums of swatch *names*
 * in mods.json, not hex — a name survives a theme change, a hex would freeze the
 * palette into the bundle. They are deliberately absent from `SETTING_ENUMS`,
 * which drives the generic chip row.
 *
 * **Derived, not transcribed.** These two tables used to be written out here, and that is
 * exactly why the two settings did nothing for as long as they did: the panel resolved the
 * names and the widget never saw the resolution, so a swatch could be selected, stored and
 * echoed back with nothing on the other end. `@void/ui` now owns the one resolution
 * ({@link KEYCAP_COLORS}) and the keycaps paint from it, so a swatch showing a colour the
 * key does not take is no longer expressible.
 *
 * Insertion order is the enum order in `mods.json`, which is the order the row reads in.
 */
const swatches = (table: Readonly<Record<string, { color: string; label: string }>>) =>
  Object.entries(table).map(([id, entry]) => ({ id, ...entry }));

const KEY_COLOURS = swatches(KEYCAP_COLORS);
const PRESSED_COLOURS = swatches(KEYCAP_PRESSED_COLORS);

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
  'warn_below',
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
  'show_eye_line',
  'sneak_too',
  'center_dot',
  'outline',
  'dynamic',
  'smooth',
  'cinematic',
];

/** Non-boolean keys that still describe behaviour rather than appearance. */
const BEHAVIOUR_KEYS = new Set([
  'keybind',
  'key',
  'mode',
  'window_ms',
  'good_ms',
  'bad_ms',
  // A threshold on a live value, like the two above it: it is about when the mod tells you
  // something, not about what it looks like.
  'warn_below',
]);

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
    default: {
      const options = SETTING_ENUMS[`${id}.${property.key}`] ?? [];
      // **An empty table used to render an empty row**: a label on the left and literally
      // nothing on the right, because `PositionChips` over `[]` draws no chips and throws no
      // error. It shipped that way on the watermark's `style` for as long as the mod existed,
      // and jsdom renders it without complaint, so no test saw it and only the game could.
      //
      // Two things now stop it. `test/registry.test.tsx` asserts every enum property in the
      // registry has a table, which fails `pnpm check` — that is the real gate, because it
      // catches the next mod as well as this one. And this branch degrades to the stored value
      // rather than to nothing, so a miss that somehow got past the gate is *visible* in game
      // instead of being an absence nobody can see. See `design/rendering-invariants.md` §14.
      if (options.length === 0) {
        return <span className="mprop__raw">{String(value ?? '—')}</span>;
      }
      return (
        <PositionChips
          options={options.map((option) => ({
            id: option,
            label: option.charAt(0).toUpperCase() + option.slice(1).replace(/_/g, ' '),
          }))}
          value={String(value ?? '')}
          onChange={(next) => write(property.key, next)}
        />
      );
    }
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

/* -------------------------------------------------------------------------- */
/* Live widget previews                                                       */
/* -------------------------------------------------------------------------- */

/**
 * The mods whose preview is the **real widget**, drawn from the loadout's own settings.
 *
 * This is the whole point of the page, and it is a different thing from what `TilePreview`
 * does. A tile preview is a *picture* of what the mod draws — cell art, hand-shaped, correct
 * about the mod's silhouette and about nothing else. A settings page needs the mod itself: you
 * are here to change `orientation` and see the pieces turn, to drag `thickness` and watch the
 * arms fatten. An approximation cannot do that, and an approximation that *tries* is worse than
 * one that does not, because it will drift from the widget and nobody will see the drift until
 * they compare them in game.
 *
 * So these entries are the same components `HudLayer` places. Not a copy of them, not a
 * preview-shaped variant — the identical component, reading the identical settings through
 * the identical hook, so a change to how a mod draws reaches this page for free and the two
 * cannot disagree.
 *
 * The table is partial on purpose: the mods not in it still draw `TilePreview`, which is
 * honest cell art, and each one moves here as its widget grows the settings to justify it.
 *
 * `sample` is documented on {@link HudWidgetProps} — it lets a widget stand in for live data
 * it has none of rather than rendering nothing, which on this page is the difference between a
 * preview and an empty box.
 */
const LIVE_WIDGETS: Record<ModId, React.ComponentType<HudWidgetProps>> = {
  // The nine that draw onto the page. These are the components `HudLayer` places, verbatim.
  fps: HudFps,
  keystrokes: HudKeystrokes,
  cps: HudCps,
  ping: HudPing,
  coordinates: HudCoordinates,
  armor_status: HudArmorStatus,
  potion_effects: HudPotionEffects,
  watermark: HudWatermark,
  crosshair: HudCrosshair,
  // The four that draw into the **world**, where there is no HTML to reuse and the entry is a
  // diagram instead. `gameplay-previews.tsx` opens with why that is a different promise and
  // what it still guarantees — the numbers are the game's own, even though the drawing is not.
  toggle_sprint: SprintPreview,
  fullbright: FullbrightPreview,
  hitboxes: HitboxPreview,
  zoom: ZoomPreview,
};

/**
 * Which mods claim a live preview — the domain `test/preview.test.tsx` walks.
 *
 * Exported so the gate reads the table rather than a copy of it: a mod added above has to
 * either prove every one of its settings moves the drawing or write down why one cannot, and
 * a list the test kept for itself could not do that.
 *
 * **All thirteen, now.** `TilePreview` is still the grid's art and still the fallback for a mod
 * added to `mods.json` before its preview is written; it is no longer what any mod page draws.
 */
export const LIVE_WIDGET_IDS: readonly ModId[] = Object.keys(LIVE_WIDGETS) as ModId[];

/**
 * How much bigger than life each live preview draws, before the mod's own `scale`.
 *
 * A HUD widget is sized for a 1458-px view seen at arm's length over a match; the preview
 * frame is ~300 px tall and the player is reading it, not glancing at it. One number for all
 * of them would be wrong for all of them — a keycap pad and a five-row armour strip do not
 * start from the same size — so each says what it needs and why.
 *
 * Absent means 1: the crosshair carries its own multiplier (`unit`), because every length in
 * it is arithmetic rather than layout, and multiplying the arithmetic keeps the 1px outline a
 * 1px outline.
 */
const PREVIEW_ZOOM: Partial<Record<ModId, number>> = {
  // The chips. A HUD chip is ~24px tall and reads at arm's length over a match; these pages
  // give the preview 600px of height and a person's full attention, and at 2.2 the chip still
  // read as a label on a form rather than as the object under discussion. All four are the same
  // number on purpose — they are the same object, and a set of chips at four different
  // magnifications would say they were not.
  fps: 3,
  cps: 3,
  ping: 3,
  coordinates: 3,
  // The list widgets carry more text per row, so they reach the frame's width sooner.
  potion_effects: 1.8,
  // The mark is already drawn at 3px cells to sit under the fps chip without out-ranking it
  // (`watermark.tsx`); it needs the most enlargement of anything here to be looked *at*.
  watermark: 3.4,
  // Four rows of 40px caps is 190 tall at 1x. 1.4 puts it at ~265 in a ~300 frame, with the
  // mouse row still clear of the corner slots.
  keystrokes: 1.5,
  // Set by the **horizontal** strip, which is the wide one: five cells whose natural width is
  // ~322 in a frame measured at 492. 1.6 put it at 518, and the widget did what a flex row does
  // when it is 26px too wide — it squeezed the cells until `1159 / 1561` wrapped onto a second
  // line and pushed the last bar out of the row. Measured in the harness, not guessed. 1.3
  // leaves ~25px of slack, which is the margin a slightly narrower panel needs.
  armor_status: 1.3,
};

/**
 * The zoom box.
 *
 * `zoom`, never `transform: scale()` — `design/rendering-invariants.md` §10a: a scaled run of
 * glyphs is drawn at the scaled size over advances computed at the unscaled one, so `LMB` came
 * out as `LNB` on the HUD for as long as `placementStyle` used a transform. `zoom` is a layout
 * scale, so the engine lays the text out at its final size. This preview draws `LMB`, `Helm`
 * and `Chest` at up to 6.4x; a transform here would garble every one of them.
 *
 * It carries `opacity` for the same reason `HudSlot` does: on the HUD, `scale` and `opacity`
 * are applied by the slot **around** the widget, not by the widget, so applying them here — in
 * the same place, in the same order — is what makes the preview the same drawing rather than a
 * similar one. It is also the answer to the two settings every HUD mod has and no preview
 * showed: before this, dragging `scale` moved a number in the caption and nothing else.
 */
function PreviewZoom({
  id,
  children,
}: {
  id: ModId;
  children: React.ReactNode;
}): React.ReactElement {
  const settings = useModSettings(id);
  const base = PREVIEW_ZOOM[id] ?? 1;
  const scale = base * Number(settings.scale ?? 1);
  const opacity = Number(settings.opacity ?? 1);
  return (
    <div className="preview__zoom" style={{ zoom: scale, opacity } as React.CSSProperties}>
      {children}
    </div>
  );
}

/** Props for {@link ModPreview}. */
export interface ModPreviewProps {
  id: ModId;
}

/**
 * The live preview, and every spatial property with it.
 *
 * Placement is four corner slots; size is the drag handle at the bottom right. The
 * "game still" behind is a flat fill, never a live blurred capture — Ultralight has
 * no backdrop-filter and the host's GL pass supplies the softness (§1 of the notes).
 *
 * **It has no size of its own any more.** It used to be 128px tall, or 208 for the one-property
 * case, because the inspector was a fixed column and every pixel the preview took was a pixel
 * the property rows did not get. On the page it is `flex: 1` — it takes whatever height the
 * properties leave, which for Fullbright's single property is most of the panel. That is §8's
 * "the preview takes the room", written as layout instead of as a magic number.
 *
 * **What it draws is the tile's art, at page size.** Only Keystrokes ever had a real picture
 * here; everything else got its description sentence centred in an empty box, which at 128px
 * was merely thin and at page size would be absurd. `TilePreview` is the vocabulary the grid
 * already uses — "a small picture of what the mod draws" — and it is the same component at a
 * larger cell size, so a mod that gains art in the grid gains it here for free and the two can
 * never disagree. The description moved to the page head, where it is a sentence rather than a
 * placeholder.
 */
export function ModPreview({ id }: ModPreviewProps): React.ReactElement {
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
  const LiveWidget = LIVE_WIDGETS[id];

  const meta = [
    placement ? anchorLabel(placement.anchor) : '—',
    sizeKey && sizeRange
      ? formatSetting(sizeKey, Number(settings[sizeKey] ?? sizeRange.min))
      : '—',
    'opacity' in settings ? formatSetting('opacity', Number(settings.opacity ?? 1)) : '—',
  ];
  const hasMeta = meta.some((part) => part !== '—');

  return (
    <div className="preview">
      <div className="preview__frame">
        {/* The stage carries the mod id once a live widget is on it, because one widget needs a
            ground: a crosshair's `outline` is black, and black on the frame's near-black is a
            setting whose switch does nothing you can see. Chips bring their own background. */}
        <div className={`preview__stage${LiveWidget ? ` preview__stage--${id}` : ''}`}>
          {/* The real widget where one exists (`LIVE_WIDGETS`), the grid's cell art otherwise.
              The split is temporary in one direction only: mods move into the table, never out
              of it. */}
          {LiveWidget ? (
            <PreviewZoom id={id}>
              <LiveWidget variant="editor" sample />
            </PreviewZoom>
          ) : (
            <TilePreview id={id} scale={2.6} />
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
        {/* Placement, size and opacity, and only the ones the mod has. A gameplay mod with no
            placement, no size and no opacity used to print `—   ·   —   ·   —`, which is three
            marks saying nothing three times; the absence is the information (§1), so the line
            simply is not there. Mods with some of the three still show a dash for the ones they
            lack, because there the dash contrasts with a value beside it. */}
        {hasMeta ? <span className="preview__meta tnum">{meta.join('   ·   ')}</span> : null}
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

/** The caption over a group, once there are enough properties to need two. */
const GROUP_CAPTION: Record<Property['group'], string> = {
  appearance: 'Appearance',
  behaviour: 'Behaviour',
};

/**
 * The properties of one mod, in whichever of §8's structures its count asks for.
 *
 * The mod's category hue is set here, on the root, so every accent underneath —
 * the live meter cell, the readout, the corner slot, the drag handle — reads
 * `var(--hue, var(--accent))` and a Visual mod's live value comes out ice. (The page sets it
 * too, on its own root; stating it here as well keeps the properties correct wherever they are
 * mounted, and a variable declared twice with the same value costs nothing.)
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
        <ModPreview id={id} />
        <div className="mprops__only">
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
      </div>
    );
  }

  if (structure === 'flat') {
    return (
      <div className="mprops" style={hueStyle(id)} data-structure="flat">
        <ModPreview id={id} />
        {/* No `.mprops__group` wrapper and no caption: §8's flat list is one list, and a group
            of one is the section label this structure exists to avoid. */}
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
  // one side is worse than no split, so it falls back to one column, and that column is then
  // not a group and is not captioned.
  const split: Array<[Property['group'] | null, Property[]]> =
    appearance.length > 0 && behaviour.length > 0
      ? [
          ['appearance', appearance],
          ['behaviour', behaviour],
        ]
      : [[null, properties]];

  return (
    <div className="mprops" style={hueStyle(id)} data-structure="grouped">
      <ModPreview id={id} />
      {split.map(([group, items]) => (
        <div className="mprops__group" key={group ?? 'all'}>
          {group ? <div className="mprops__cap">{GROUP_CAPTION[group]}</div> : null}
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
