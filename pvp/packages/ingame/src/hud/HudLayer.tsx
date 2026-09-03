/**
 * The HUD layer. Always mounted, never takes input.
 *
 * One `<div>` per enabled HUD mod that the loadout places, positioned by its
 * `hud[]` entry through `placementStyle()` — `anchor` picks the edge offsets,
 * `dx`/`dy` + `scale` become one 2D `transform` (§8.1, ultralight-notes.md §4).
 * A widget draws only when its mod is on *and* the loadout gives it a place.
 */

import { memo, type ComponentType, type ReactNode } from 'react';
import { hudItem, isModOn, modSettings, useVoidStore } from '@/store/store';
import { placementStyle } from '@/store/hud-geometry';
import { HUD_MOD_IDS } from '@/registry';
import type { HUDModId } from '@/bridge/protocol';
import {
  ArmorList,
  CoordsChip,
  CpsChip,
  DebugCrosshair,
  FpsChip,
  KeystrokesWidget,
  PingChip,
  PotionList,
} from './widgets';
import { isDebugBridge } from '@/bridge/connect';

const WIDGETS: Record<HUDModId, ComponentType> = {
  fps: FpsChip,
  ping: PingChip,
  coordinates: CoordsChip,
  potion_effects: PotionList,
  armor_status: ArmorList,
  keystrokes: KeystrokesWidget,
  cps: CpsChip,
};

export interface HudLayerProps {
  /** Chips drop to opacity 0.7 while an overlay panel is up. */
  dimmed?: boolean;
  /** The HUD editor renders the same widgets with the denser chip treatment. */
  editor?: boolean;
  /** The editor supplies its own wrapper (selection box, drag handlers). */
  renderItem?: (id: HUDModId, node: ReactNode) => ReactNode;
  /**
   * Live placement override, used by the HUD editor while a widget is being
   * dragged. The loadout is not touched until the drop, when `setHud` runs.
   */
  override?: Partial<
    Record<HUDModId, { anchor: Parameters<typeof placementStyle>[0]; dx: number; dy: number; scale: number }>
  >;
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
  anchor: Parameters<typeof placementStyle>[0];
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

export function HudLayer({ dimmed, editor, renderItem, override }: HudLayerProps) {
  const loadout = useVoidStore((s) => s.loadout);

  return (
    <div
      className={[
        'hud-layer',
        dimmed ? 'hud-layer--dimmed' : '',
        editor ? 'hud-layer--editor' : '',
      ]
        .filter(Boolean)
        .join(' ')}
    >
      {HUD_MOD_IDS.map((id) => {
        if (!isModOn(loadout, id)) return null;
        const item = hudItem(loadout, id);
        if (!item) return null;
        const settings = modSettings(loadout, id);
        const Widget = WIDGETS[id];
        const live = override?.[id];
        // `hud_item.scale` multiplies the mod's own `scale` setting (loadout.json).
        const scale = (live?.scale ?? item.scale ?? 1) * Number(settings.scale ?? 1);
        const opacity = Number(settings.opacity ?? 1);
        const node = <Widget />;
        return (
          <HudSlot
            key={id}
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
      })}
      {isDebugBridge() && isModOn(loadout, 'crosshair') && <DebugCrosshair />}
    </div>
  );
}
