/**
 * The HUD layer. Always mounted, never takes input.
 *
 * One positioned box per enabled HUD mod that the loadout places, laid out by
 * its `hud[]` entry through `placementStyle()` — `anchor` picks the edge
 * offsets, `dx`/`dy` + `scale` become one 2D `transform` (§8.1, and
 * ultralight-notes.md §4, which rules out anything 3D). A widget draws only when
 * its mod is on *and* the loadout gives it a place.
 */

import { memo, type ComponentType, type ReactNode } from 'react';
import { hudItem, isModOn, useModSettings, useVoidStore } from '@/store/store';
import { placementStyle } from '@/store/hud-geometry';
import { HUD_MOD_IDS, type HUDAnchor, type HUDModId } from '@/bridge/protocol';
import {
  DebugCrosshair,
  HudArmorStatus,
  HudCoordinates,
  HudCps,
  HudFps,
  HudKeystrokes,
  HudPing,
  HudPotionEffects,
  type HudWidgetProps,
} from './widgets';
import { isDebugBridge } from '@/bridge/connect';

const WIDGETS: Record<HUDModId, ComponentType<HudWidgetProps>> = {
  fps: HudFps,
  ping: HudPing,
  coordinates: HudCoordinates,
  potion_effects: HudPotionEffects,
  armor_status: HudArmorStatus,
  keystrokes: HudKeystrokes,
  cps: HudCps,
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
  /** The HUD editor renders the same widgets with the denser chip treatment. */
  editor?: boolean;
  /** The editor wraps each widget to attach its drag handlers. */
  renderItem?: (id: HUDModId, node: ReactNode) => ReactNode;
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
  children,
}: {
  id: HUDModId;
  anchor: HUDAnchor;
  dx: number;
  dy: number;
  scale: number;
  opacity: number;
  children: ReactNode;
}) {
  return (
    <div
      className="hud-item"
      data-hud-id={id}
      style={{ ...placementStyle(anchor, dx, dy, scale), opacity }}
    >
      {children}
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
  renderItem,
  live,
}: {
  id: HUDModId;
  dimmed?: boolean;
  editor?: boolean;
  renderItem?: (id: HUDModId, node: ReactNode) => ReactNode;
  live?: LivePlacement;
}) {
  const on = useVoidStore((s) => isModOn(s.loadout, id));
  const item = useVoidStore((s) => hudItem(s.loadout, id));
  const settings = useModSettings(id);

  if (!on || !item) return null;

  const Widget = WIDGETS[id];
  // `hud_item.scale` multiplies the mod's own `scale` setting (loadout.json).
  const scale = (live?.scale ?? item.scale ?? 1) * Number(settings.scale ?? 1);
  const opacity = Number(settings.opacity ?? 1) * (dimmed ? 0.7 : 1);
  const node = <Widget variant={editor ? 'editor' : 'compact'} />;
  return (
    <HudSlot
      id={id}
      anchor={live?.anchor ?? item.anchor}
      dx={live?.dx ?? item.dx}
      dy={live?.dy ?? item.dy}
      scale={scale}
      opacity={opacity}
    >
      {renderItem ? renderItem(id, node) : node}
    </HudSlot>
  );
});

export function HudLayer({ dimmed, editor, renderItem, override }: HudLayerProps) {
  const crosshairOn = useVoidStore((s) => isModOn(s.loadout, 'crosshair'));

  return (
    <div className={['hud-layer', editor ? 'hud-layer--editor' : ''].filter(Boolean).join(' ')}>
      {HUD_MOD_IDS.map((id) => (
        <HudEntry
          key={id}
          id={id}
          dimmed={dimmed}
          editor={editor}
          renderItem={renderItem}
          live={override?.[id]}
        />
      ))}
      {isDebugBridge() && crosshairOn && (
        <div className="hud-crosshair-slot">
          <DebugCrosshair />
        </div>
      )}
    </div>
  );
}
