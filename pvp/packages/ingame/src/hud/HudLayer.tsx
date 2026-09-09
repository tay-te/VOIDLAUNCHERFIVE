/**
 * The HUD layer. Always mounted, never takes input.
 *
 * One positioned box per enabled HUD mod that the loadout places, laid out by
 * its `hud[]` entry through `placementStyle()` — `anchor` picks the edge
 * offsets, `dx`/`dy` + `scale` become one 2D `transform` (§8.1, and
 * ultralight-notes.md §4, which rules out anything 3D). A widget draws only when
 * its mod is on *and* the loadout gives it a place — except in the editor, where an
 * enabled-but-unplaced mod falls back to its factory placement rather than vanishing.
 */

import { memo, type ComponentType, type HTMLAttributes, type ReactNode } from 'react';
import { hudItem, isModOn, useModSettings, useVoidStore } from '@/store/store';
import { DEFAULT_HUD, placementStyle, zoomStyle } from '@/store/hud-geometry';
import { HUD_MOD_IDS, type HUDAnchor, type HUDModId } from '@/bridge/protocol';
import { HudWatermark } from './watermark';
// The Wave 2 readouts, each in its own module — `watermark.tsx` set that precedent and
// `mods/art.tsx` states the principle: what one mod draws lives in that mod's own file.
import { HudCombo } from './combo';
import { HudHitTrade } from './hit_trade';
import { HudItemCounter } from './item_counter';
import { HudMemory } from './memory';
import { HudMomentum } from './momentum';
import { HudSaturation } from './saturation';
import { HudServerAddress } from './server_address';
import { HudStopwatch } from './stopwatch';
import {
  HudArmorStatus,
  HudCoordinates,
  HudCps,
  HudDirection,
  HudCrosshair,
  HudFps,
  HudKeystrokes,
  HudPing,
  HudPotionEffects,
  type HudWidgetProps,
} from './widgets';
import { isDebugBridge } from '@/bridge/connect';
import { hudChrome } from './chrome';

const WIDGETS: Record<HUDModId, ComponentType<HudWidgetProps>> = {
  fps: HudFps,
  ping: HudPing,
  coordinates: HudCoordinates,
  direction: HudDirection,
  potion_effects: HudPotionEffects,
  armor_status: HudArmorStatus,
  keystrokes: HudKeystrokes,
  cps: HudCps,
  combo: HudCombo,
  hit_trade: HudHitTrade,
  saturation: HudSaturation,
  momentum: HudMomentum,
  memory: HudMemory,
  server_address: HudServerAddress,
  item_counter: HudItemCounter,
  stopwatch: HudStopwatch,
  // The thirteenth mod, and it goes in this table like any other because that is the whole
  // argument for making the watermark a mod rather than a flag (`watermark.tsx`).
  watermark: HudWatermark,
};

/** A live placement, as the HUD editor supplies it mid-drag. */
export interface LivePlacement {
  anchor: HUDAnchor;
  dx: number;
  dy: number;
  scale: number;
}

export interface HudLayerProps {
  /** Chips drop to opacity 0.7 while an overlay panel is up. */
  dimmed?: boolean;
  /**
   * The HUD editor renders the same widgets with the denser chip treatment — and, because a
   * widget you cannot see is a widget you cannot place, it also draws every enabled HUD mod
   * the loadout has forgotten to place, at its factory position (rendering-invariants §15).
   */
  editor?: boolean;
  /**
   * Props for the positioned box itself — how the editor attaches its drag handlers.
   *
   * **On the `.hud-item` box, not on a wrapper inside it.** It used to be a `renderItem`
   * that wrapped the widget in a `display: contents` div, and that div is where the whole
   * editor came apart: `display: contents` is supported in this engine, so the wrapper has
   * no principal box and `getBoundingClientRect()` on it measured `0,0 0x0` — while the
   * `.hud-item` beside it measured `31,536 130x175`. Every drag therefore started from a
   * widget the editor believed was at the layer origin with no size, so grabbing a chip
   * teleported it. Handlers belong on the box that has the geometry.
   *
   * Called during render, so give it a stable identity (`useCallback`) or every widget
   * re-renders on every keystroke elsewhere in the page.
   */
  slotProps?: (id: HUDModId) => HTMLAttributes<HTMLDivElement>;
  /**
   * Live placement override, used by the HUD editor while a widget is being
   * dragged. The loadout is not touched until the drop, when `setHud` runs.
   */
  override?: Partial<Record<HUDModId, LivePlacement>>;
}

/**
 * One positioned widget. Memoised on the placement values, so a `keys` push
 * that repaints a keycap does not re-run the placement maths.
 */
const HudSlot = memo(function HudSlot({
  id,
  anchor,
  dx,
  dy,
  scale,
  opacity,
  chrome,
  slotProps,
  children,
}: {
  id: HUDModId;
  anchor: HUDAnchor;
  dx: number;
  dy: number;
  scale: number;
  opacity: number;
  /** The shared chrome block as classes — see `hud/chrome.ts`. */
  chrome: string;
  slotProps?: (id: HUDModId) => HTMLAttributes<HTMLDivElement>;
  children: ReactNode;
}) {
  return (
    <div
      className={`hud-item ${chrome}`}
      data-hud-id={id}
      style={{ ...placementStyle(anchor, dx, dy), opacity }}
      {...slotProps?.(id)}
    >
      {/* The size lives on its own box — `zoom`, not `transform: scale()`, which garbles text
          in this engine (`placementStyle`). Always present, even at 1×, so nothing about the
          widget's box model changes when it is scaled. */}
      <div className="hud-item__zoom" style={zoomStyle(scale)}>
        {children}
      </div>
    </div>
  );
});

/**
 * One widget's subscriptions, kept inside the widget.
 *
 * `HudLayer` used to select the whole loadout and derive every widget from it, which meant
 * toggling any mod re-rendered all of them. That is expensive here in a way it would not be in a
 * browser: the widgets sit in opposite corners, and Ultralight reports damage as a single bounding
 * rectangle rather than a region — so re-rendering the set drags that rectangle across the whole
 * 6.6 MP surface for a ~50 ms repaint, and unions with whatever the menu is doing.
 *
 * Each entry now subscribes to the three things it actually draws from. All three are stable
 * across an unrelated change: `on` is a boolean, `hud[]` keeps its reference because
 * `writeSetting` only rebuilds `mods`, and {@link useModSettings} watches one entry of it.
 */
const HudEntry = memo(function HudEntry({
  id,
  dimmed,
  editor,
  slotProps,
  live,
}: {
  id: HUDModId;
  dimmed?: boolean;
  editor?: boolean;
  slotProps?: (id: HUDModId) => HTMLAttributes<HTMLDivElement>;
  live?: LivePlacement;
}) {
  const on = useVoidStore((s) => isModOn(s.loadout, id));
  const item = useVoidStore((s) => hudItem(s.loadout, id));
  const settings = useModSettings(id);

  if (!on) return null;
  // On the HUD proper a mod that is on but unplaced draws nothing, and nothing says so — the
  // fourth row of rendering-invariants §15's table. In the **editor** that absence is fatal
  // rather than cosmetic: the one screen whose whole job is placing widgets would be the one
  // screen that cannot reach this one. So the editor falls back to the factory placement, which
  // is a position the player can then see, drag and thereby give the loadout a real entry.
  const place = item ?? (editor ? { id, ...DEFAULT_HUD[id], scale: 1 } : null);
  if (!place) return null;

  const Widget = WIDGETS[id];
  // `hud_item.scale` multiplies the mod's own `scale` setting (loadout.json).
  const scale = (live?.scale ?? place.scale ?? 1) * Number(settings.scale ?? 1);
  const opacity = Number(settings.opacity ?? 1) * (dimmed ? 0.7 : 1);
  return (
    <HudSlot
      id={id}
      anchor={live?.anchor ?? place.anchor}
      dx={live?.dx ?? place.dx}
      dy={live?.dy ?? place.dy}
      scale={scale}
      opacity={opacity}
      chrome={hudChrome(settings)}
      slotProps={slotProps}
    >
      <Widget variant={editor ? 'editor' : 'compact'} />
    </HudSlot>
  );
});

export function HudLayer({ dimmed, editor, slotProps, override }: HudLayerProps) {
  const crosshairOn = useVoidStore((s) => isModOn(s.loadout, 'crosshair'));

  return (
    <div className={['hud-layer', editor ? 'hud-layer--editor' : ''].filter(Boolean).join(' ')}>
      {HUD_MOD_IDS.map((id) => (
        <HudEntry
          key={id}
          id={id}
          dimmed={dimmed}
          editor={editor}
          slotProps={slotProps}
          live={override?.[id]}
        />
      ))}
      {/* The crosshair has no `hud[]` entry — it is always dead centre — so it is placed by a
          slot of its own rather than by `HudEntry`. Harness only: in game GL draws it at the
          exact pixel centre, and drawing a second one in HTML would double it. */}
      {isDebugBridge() && crosshairOn && (
        <div className="hud-crosshair-slot">
          <HudCrosshair />
        </div>
      )}
    </div>
  );
}
