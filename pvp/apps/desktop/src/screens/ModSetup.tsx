/**
 * Mod setup — one mod, its placement and its properties.
 *
 * Reached from a card on the Mods grid (`stores/ui.openModSetup`); there is no router,
 * so this is a sub-route on the `mods` screen and `App` swaps it in. Back returns to
 * the grid without leaving the screen.
 *
 * The shape is `design/quiet-cell-system.md` §8, which is a rule rather than a layout:
 *
 *   Spatial properties — position and size — **never** appear as list rows. They live
 *   on the preview, as a draggable module with accent corner ticks and four corner
 *   anchor slots. `local/registry.propertiesFor` is what enforces that: it drops
 *   `scale` from the list, so the count that decides the list's structure is the count
 *   of things that are *not* on the preview.
 *
 *   What remains gets the simplest structure that fits it: 1 property → no list at all,
 *   2–4 → a flat list with no section labels, 5+ → two light groups. Never tabs.
 *
 * Edits are a draft. Everything else in the launcher writes straight through, but a
 * drag is hundreds of writes a second and the contract puts a Save here, so the page
 * keeps its own copy and commits it — which also gives "Reset to default" something
 * honest to reset.
 */

import { Icon, MOD_ICONS } from '@void/ui';
import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type CSSProperties,
  type PointerEvent as ReactPointerEvent,
} from 'react';

import { Chips, FlatButton, KeyChip, Meter, Swatches, Toggle } from '../local/controls';
import { captureKey, prettyKey } from '../local/keys';
import { PRESETS, matchingPreset, presetSettings, type PresetId } from '../local/presets';
import type { HUDAnchor, HUDItem, HUDModId, Loadout, ModId } from '../local/protocol';
import {
  MOD_REGISTRY,
  type SettingSpec,
  categoryOf,
  effectiveState,
  formatSetting,
  defaultsFor,
  hueOf,
  propertiesFor,
} from '../local/registry';
import { useLoadouts } from '../stores/loadouts';
import { useUi } from '../stores/ui';

/**
 * The GUI space HUD offsets are measured in.
 *
 * `schema/loadout.json` says "unscaled GUI pixels", which for 1.8.9 at the default GUI
 * scale is 854 × 480 — a 16:9 screen. The mockup is that rectangle, so a drag writes
 * the number the game will actually use rather than a number scaled to this window.
 */
const VIRTUAL = { w: 854, h: 480 };

/** The four corner anchors the preview offers as slots (§7 "four corner anchors"). */
const CORNERS: readonly { anchor: HUDAnchor; label: string }[] = [
  { anchor: 'top-left', label: 'Top left' },
  { anchor: 'top-right', label: 'Top right' },
  { anchor: 'bottom-left', label: 'Bottom left' },
  { anchor: 'bottom-right', label: 'Bottom right' },
];

/** How far from its corner a freshly slotted module sits, in GUI pixels. */
const SLOT_PADDING = 20;

/** The colour row, as ids the mod schema round-trips. */
const COLOURS = [
  { id: '#FFFFFFFF', color: '#FFFFFF', label: 'White' },
  { id: '#9F8BFFFF', color: '#9F8BFF', label: 'Violet' },
  { id: '#3DD68CFF', color: '#3DD68C', label: 'Green' },
  { id: '#D9A93AFF', color: '#D9A93A', label: 'Amber' },
  { id: '#C05B54FF', color: '#C05B54', label: 'Red' },
  { id: '#4D87CDFF', color: '#4D87CD', label: 'Blue' },
];

/**
 * What each HUD module shows in the mockup.
 *
 * A still, not a live readout: the launcher has no game to read from, and a fake live
 * number would be the one thing on the screen pretending to be state.
 */
const MODULE_SAMPLE: Partial<Record<ModId, string>> = {
  fps: '240 FPS',
  ping: '42 ms',
  cps: '12 CPS',
  coordinates: 'X 118  Y 71  Z −402',
  keystrokes: 'W · A S D',
  armor_status: '100%  92%  74%  88%',
  potion_effects: 'Speed II  1:20',
};

type Side = 'left' | 'center' | 'right';
type Tier = 'top' | 'middle' | 'bottom';

function sideOf(anchor: HUDAnchor): Side {
  if (anchor.includes('left')) return 'left';
  if (anchor.includes('right')) return 'right';
  return 'center';
}

function tierOf(anchor: HUDAnchor): Tier {
  if (anchor.startsWith('top')) return 'top';
  if (anchor.startsWith('bottom')) return 'bottom';
  return 'middle';
}

/** Where the module sits in the stage, as CSS percentages of the 854 × 480 space. */
function moduleStyle(item: HUDItem, scale: number): CSSProperties {
  const side = sideOf(item.anchor);
  const tier = tierOf(item.anchor);
  const x = `${(item.dx / VIRTUAL.w) * 100}%`;
  const y = `${(item.dy / VIRTUAL.h) * 100}%`;
  const style: CSSProperties = {};

  if (side === 'left') style.left = x;
  else if (side === 'right') style.right = `${(-item.dx / VIRTUAL.w) * 100}%`;
  else style.left = `calc(50% + ${x})`;

  if (tier === 'top') style.top = y;
  else if (tier === 'bottom') style.bottom = `${(-item.dy / VIRTUAL.h) * 100}%`;
  else style.top = `calc(50% + ${y})`;

  // Scale is a 2D transform, not a font size: the module is a picture of the HUD, and
  // the mod's `scale` setting is what the game will draw it at. The origin is the
  // module's own anchor corner so growing it never walks it off that edge.
  style.transformOrigin = `${tier === 'bottom' ? 'bottom' : tier === 'middle' ? 'center' : 'top'} ${
    side === 'right' ? 'right' : side === 'center' ? 'center' : 'left'
  }`;
  style.transform = `translate(${side === 'center' ? '-50%' : '0'}, ${
    tier === 'middle' ? '-50%' : '0'
  }) scale(${scale})`;
  return style;
}

function clampOffset(value: number): number {
  return Math.max(-4096, Math.min(4096, Math.round(value)));
}

/** The placement a HUD mod gets when the loadout has never placed it. */
function defaultItem(id: HUDModId): HUDItem {
  return { id, anchor: 'top-left', dx: SLOT_PADDING, dy: SLOT_PADDING };
}

/* -------------------------------------------------------------------------- */

function PropertyControl({
  spec,
  value,
  hue,
  onChange,
}: {
  spec: SettingSpec;
  value: unknown;
  hue: string;
  onChange: (next: unknown) => void;
}) {
  switch (spec.control) {
    case 'switch':
      return <Toggle size="md" checked={value === true} onChange={onChange} label={spec.label} />;
    case 'select':
      return (
        <Chips
          label={spec.label}
          value={String(value ?? spec.options?.[0] ?? '')}
          options={(spec.options ?? []).map((option) => ({
            id: option,
            label: option.replace(/_/g, ' '),
          }))}
          onChange={onChange}
        />
      );
    case 'keybind':
      return (
        <KeyChip
          label={spec.label}
          value={prettyKey(String(value ?? 'NONE'))}
          onCapture={captureKey}
          onChange={onChange}
        />
      );
    case 'color':
      return (
        <Swatches
          label={spec.label}
          swatches={COLOURS}
          value={String(value ?? '#FFFFFFFF').toUpperCase()}
          onChange={onChange}
        />
      );
    case 'slider':
      return (
        <Meter
          label={spec.label}
          readout={formatSetting(spec, value)}
          value={Number(value ?? spec.min ?? 0)}
          min={spec.min ?? 0}
          max={spec.max ?? 1}
          step={spec.step ?? 0.01}
          onChange={onChange}
        />
      );
    default:
      // `hue` is threaded through so a future control can tint its live value without
      // reaching for `--accent`; the four above inherit it from the page root.
      return <span className="props__todo" style={{ color: hue }} />;
  }
}

function Property({
  spec,
  value,
  hue,
  onChange,
}: {
  spec: SettingSpec;
  value: unknown;
  hue: string;
  onChange: (next: unknown) => void;
}) {
  // A meter carries its own label and readout, so it takes the whole row and its
  // parts are re-ordered onto the row by CSS (`.props .meter`), which is how the
  // frame draws it: label left, cells right-aligned, value last.
  if (spec.control === 'slider') {
    return (
      <div className="prop prop--meter">
        <PropertyControl spec={spec} value={value} hue={hue} onChange={onChange} />
      </div>
    );
  }
  return (
    <div className="prop">
      <span className="prop__label">{spec.label}</span>
      <PropertyControl spec={spec} value={value} hue={hue} onChange={onChange} />
    </div>
  );
}

/* -------------------------------------------------------------------------- */

function Setup({ id, loadout }: { id: ModId; loadout: Loadout }) {
  const setMod = useLoadouts((s) => s.setMod);
  const setHudItem = useLoadouts((s) => s.setHudItem);
  const switchTo = useLoadouts((s) => s.switchTo);
  const library = useLoadouts((s) => s.library);
  const close = useUi((s) => s.closeModSetup);

  const entry = MOD_REGISTRY[id];
  const isHud = entry.kind === 'hud';
  const hue = hueOf(id);
  const saved = effectiveState(loadout, id);
  const savedItem = isHud
    ? (loadout.hud.find((h) => h.id === id) ?? defaultItem(id as HUDModId))
    : null;

  const [draft, setDraft] = useState<Record<string, unknown>>({ ...saved });
  const [item, setItem] = useState<HUDItem | null>(savedItem);

  const stage = useRef<HTMLDivElement>(null);
  const module = useRef<HTMLDivElement>(null);
  const drag = useRef<{ x: number; y: number } | null>(null);
  const resize = useRef<{ x: number; scale: number } | null>(null);

  const properties = propertiesFor(id);
  const scale = typeof draft.scale === 'number' ? draft.scale : 1;
  const on = draft.on === true;
  /** The mod's own toggle key, printed in the meta line the way the frame does. */
  const keybindLabel =
    typeof draft.keybind === 'string' && draft.keybind && draft.keybind !== 'NONE'
      ? prettyKey(draft.keybind)
      : '';

  const dirty =
    JSON.stringify(draft) !== JSON.stringify(saved) ||
    JSON.stringify(item) !== JSON.stringify(savedItem);

  const set = (patch: Record<string, unknown>) => setDraft((prev) => ({ ...prev, ...patch }));

  /** Stage pixels per GUI pixel — the drag's only unit conversion. */
  const ratio = useCallback((): number => {
    const box = stage.current?.getBoundingClientRect();
    return box && box.width > 0 ? box.width / VIRTUAL.w : 1;
  }, []);

  /**
   * On release the module takes the nearest of the four corners, and its offsets are
   * re-measured against that corner so it does not move a pixel when the anchor
   * changes. That is what makes the corner slots and free dragging the same gesture.
   */
  const snapToNearestCorner = useCallback(() => {
    const stageBox = stage.current?.getBoundingClientRect();
    const modBox = module.current?.getBoundingClientRect();
    if (!stageBox || !modBox) return;
    const s = stageBox.width / VIRTUAL.w;
    const cx = modBox.left + modBox.width / 2;
    const cy = modBox.top + modBox.height / 2;
    const side: Side = cx < stageBox.left + stageBox.width / 2 ? 'left' : 'right';
    const tier: Tier = cy < stageBox.top + stageBox.height / 2 ? 'top' : 'bottom';
    const anchor = `${tier}-${side}` as HUDAnchor;
    const dx = side === 'left' ? (modBox.left - stageBox.left) / s : (modBox.right - stageBox.right) / s;
    const dy = tier === 'top' ? (modBox.top - stageBox.top) / s : (modBox.bottom - stageBox.bottom) / s;
    setItem((prev) =>
      prev ? { ...prev, anchor, dx: clampOffset(dx), dy: clampOffset(dy) } : prev,
    );
  }, []);

  // One window-level drag for both handles: the pointer has to keep adjusting after it
  // leaves the module, or a fast drag drops the module wherever it crossed the edge.
  useEffect(() => {
    const onMove = (event: PointerEvent): void => {
      const s = ratio();
      if (drag.current) {
        const dx = (event.clientX - drag.current.x) / s;
        const dy = (event.clientY - drag.current.y) / s;
        drag.current = { x: event.clientX, y: event.clientY };
        setItem((prev) =>
          prev ? { ...prev, dx: clampOffset(prev.dx + dx), dy: clampOffset(prev.dy + dy) } : prev,
        );
      } else if (resize.current) {
        const next = resize.current.scale + (event.clientX - resize.current.x) / 120;
        set({ scale: Number(Math.max(0.25, Math.min(4, Math.round(next / 0.05) * 0.05)).toFixed(2)) });
      }
    };
    const onUp = (): void => {
      if (drag.current) {
        drag.current = null;
        snapToNearestCorner();
      }
      resize.current = null;
    };
    window.addEventListener('pointermove', onMove);
    window.addEventListener('pointerup', onUp);
    return () => {
      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('pointerup', onUp);
    };
    // `set` is a stable closure over `setDraft`; the effect only needs the two refs.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ratio, snapToNearestCorner]);

  const startDrag = (event: ReactPointerEvent) => {
    event.preventDefault();
    drag.current = { x: event.clientX, y: event.clientY };
  };

  const startResize = (event: ReactPointerEvent) => {
    event.preventDefault();
    event.stopPropagation();
    resize.current = { x: event.clientX, scale };
  };

  const slot = (anchor: HUDAnchor) => {
    const side = sideOf(anchor);
    const tier = tierOf(anchor);
    setItem((prev) =>
      prev
        ? {
            ...prev,
            anchor,
            dx: side === 'right' ? -SLOT_PADDING : SLOT_PADDING,
            dy: tier === 'bottom' ? -SLOT_PADDING : SLOT_PADDING,
          }
        : prev,
    );
  };

  const save = async (): Promise<void> => {
    await setMod(id, draft);
    if (isHud && item) await setHudItem(id, item);
  };

  const reset = (): void => {
    setDraft(defaultsFor(id));
    if (isHud) setItem(defaultItem(id as HUDModId));
  };

  const preset = matchingPreset(id, draft);
  const behaviour = properties.filter(
    (spec) => spec.control === 'switch' || spec.control === 'keybind',
  );
  const appearance = properties.filter(
    (spec) => spec.control !== 'switch' && spec.control !== 'keybind',
  );

  const rows = (specs: readonly SettingSpec[]) =>
    specs.map((spec) => (
      <Property
        key={spec.key}
        spec={spec}
        value={draft[spec.key]}
        hue={hue}
        onChange={(next) => set({ [spec.key]: next })}
      />
    ));

  return (
    <div className="setup" style={{ '--hue': hue } as CSSProperties}>
      <div className="setup__preview">
        {/*
          The frame puts the whole identity of the mod in the left column, over the
          preview: the way back, the 30px title, and one label line that says what it
          is, whether it is on, and what key toggles it. The properties column opens on
          `PROPERTIES` and carries nothing but properties.
        */}
        <header className="setup__head">
          <button type="button" className="backlink eyebrow" onClick={close}>
            <Icon name="arrow-left" size={11} />
            Mods
          </button>
          <h1 className="screen-title">{entry.label}</h1>
          <p className="setup__meta eyebrow">
            {categoryOf(id)} · {on ? 'Enabled' : 'Disabled'}
            {keybindLabel ? ` · ${keybindLabel}` : ''}
          </p>
        </header>

        <div className="setup__mockwrap">
          <div className="gamescreen" ref={stage}>
            <span className="gamescreen__field" aria-hidden="true" />

            {/*
              The mock's own furniture, from `289:1411`: a horizon rule at 60% of the
              height, the game's crosshair, and a nine-slot hotbar along the bottom.
              None of it is interactive — it is what tells the eye that the rectangle
              being dragged into is a game screen and not an empty box, which is the
              only thing that makes a placement preview readable.
            */}
            <span className="gamescreen__horizon" aria-hidden="true" />
            <span className="gamescreen__cross" aria-hidden="true">
              <i />
              <i />
              <i />
              <i />
              <i />
              <i />
              <i />
              <i />
            </span>
            <span className="gamescreen__hotbar" aria-hidden="true">
              {Array.from({ length: 9 }, (_, i) => (
                <i key={i} />
              ))}
            </span>

            {isHud && item ? (
              <>
                {CORNERS.map(({ anchor, label }) => (
                  <button
                    key={anchor}
                    type="button"
                    className={`slot slot--${anchor}${item.anchor === anchor ? ' is-on' : ''}`}
                    aria-label={`Place at ${label.toLowerCase()}`}
                    title={label}
                    onClick={() => slot(anchor)}
                  >
                    <span className="slot__mark cell" aria-hidden="true" />
                  </button>
                ))}

                <div
                  ref={module}
                  className={`module${on ? ' is-on' : ''}`}
                  style={moduleStyle(item, scale)}
                  onPointerDown={startDrag}
                  role="application"
                  aria-label={`${entry.label} placement — drag to move`}
                >
                  <span className="module__body tnum">{MODULE_SAMPLE[id] ?? entry.label}</span>
                  <span className="module__tick module__tick--tl" aria-hidden="true" />
                  <span className="module__tick module__tick--tr" aria-hidden="true" />
                  <span className="module__tick module__tick--bl" aria-hidden="true" />
                  <span
                    className="module__tick module__tick--br is-handle"
                    onPointerDown={startResize}
                    role="presentation"
                  />
                </div>
              </>
            ) : (
              <div className="gamescreen__note">
                <Icon name={MOD_ICONS[id] ?? 'layers'} size={30} />
                <p>{entry.label} draws no placeable module — it changes the world, not the HUD.</p>
              </div>
            )}
          </div>
        </div>

        <p className="setup__caption eyebrow">
          {isHud
            ? 'Drag the module to place it · arrow keys nudge by one pixel'
            : 'This mod changes the world, not the HUD'}
          {isHud && item ? (
            <span className="setup__place tnum">
              {item.anchor.replace('-', ' ')} · {item.dx}, {item.dy} · {scale.toFixed(2)}×
            </span>
          ) : null}
        </p>

        <div className="setup__presets">
          <p className="eyebrow">Presets</p>
          <Chips
            label="Preset"
            value={preset ?? ''}
            options={PRESETS.map((p) => ({ id: p.id, label: p.label }))}
            onChange={(next) => setDraft(presetSettings(id, next as PresetId))}
          />
          {preset === null ? <span className="setup__custom">Custom</span> : null}
        </div>
      </div>

      <aside className="props" aria-label={`${entry.label} properties`}>
        <p className="eyebrow props__cap">Properties</p>

        <div className={`props__list${on ? '' : ' is-off'}`}>
          {properties.length === 1 ? (
            // §8: one property gets no list at all.
            <div className="props__single">{rows(properties)}</div>
          ) : properties.length <= 4 ? (
            rows(properties)
          ) : (
            <>
              <div className="props__group">
                <p className="eyebrow">Appearance</p>
                {rows(appearance)}
              </div>
              <div className="props__group">
                <p className="eyebrow">Behaviour</p>
                {rows(behaviour)}
              </div>
            </>
          )}
        </div>

        <hr className="divider" />

        <div className="props__actions">
          <FlatButton kind="ghost" onClick={reset}>
            Reset to default
          </FlatButton>
          <FlatButton kind="primary" disabled={!dirty} onClick={() => void save()}>
            Save
          </FlatButton>
        </div>

        <div className="props__enabled">
          <p className="eyebrow">Enabled in</p>
          <div className="chips">
            {library.map((entryLoadout) => {
              const current = entryLoadout.id === loadout.id;
              return (
                <button
                  key={entryLoadout.id}
                  type="button"
                  className={`chip${current && on ? ' is-on' : ''}`}
                  // Only the active loadout's mod state is loaded, so only its chip can
                  // read as on. Picking another loadout switches to it, which is what
                  // makes the row honest rather than a guess about the other's state.
                  title={
                    current
                      ? `${on ? 'On' : 'Off'} in this loadout`
                      : `Switch to ${entryLoadout.name}`
                  }
                  onClick={() => (current ? set({ on: !on }) : void switchTo(entryLoadout.id))}
                >
                  {entryLoadout.name}
                </button>
              );
            })}
          </div>
        </div>
      </aside>
    </div>
  );
}

export function ModSetupScreen() {
  const id = useUi((s) => s.modSetup);
  const active = useLoadouts((s) => s.active);
  if (!id || !active) return <div className="setup" />;
  // Keyed so switching mod or loadout reseeds the draft; there is no other way in.
  return <Setup key={`${active.id}:${id}`} id={id} loadout={active} />;
}
