/**
 * Overlay — HUD layout · frame `244:1722`.
 *
 * Direct manipulation over the live game. Drag moves a widget, Alt- or
 * Shift-drag scales it, `Snap` quantises to the 8 px grid, the selection reads
 * out `x · y · scale` live, and the placement is written back with
 * `void.setHud` **on drop** — one call per gesture, not per frame (§9, and the
 * `hud` protocol message which Java mirrors to Rust on drop).
 *
 * `setHud` returns the item Java actually stored after its own snapping and
 * clamping, and that is what the widget settles on.
 */

import { useCallback, useLayoutEffect, useRef, useState } from 'react';
import { hudItem, useVoidStore } from '@/store/store';
import {
  GRID,
  anchorForPosition,
  clampScale,
  clampToViewport,
  placementFromScreen,
  screenPosition,
  snapTo,
} from '@/store/hud-geometry';
import { HUD_MOD_IDS, MOD_REGISTRY } from '@/registry';
import type { HUDModId } from '@/bridge/protocol';
import { HudLayer } from '@/hud/HudLayer';
import { Icon } from '@/icons/Icon';

export const EDITOR_HINT = 'Drag to move   ·   ⌥ drag to scale   ·   Esc to exit';

/** Factory layout, matching the placements drawn on the frame. */
export const DEFAULT_HUD: Record<HUDModId, { anchor: Parameters<typeof screenPosition>[0]; dx: number; dy: number }> = {
  fps: { anchor: 'top-left', dx: 23, dy: 23 },
  ping: { anchor: 'top-left', dx: 23, dy: 65 },
  coordinates: { anchor: 'top-left', dx: 23, dy: 103 },
  potion_effects: { anchor: 'top-right', dx: -25, dy: 23 },
  armor_status: { anchor: 'top-right', dx: -25, dy: 299 },
  keystrokes: { anchor: 'bottom-left', dx: 31, dy: -109 },
  cps: { anchor: 'bottom-left', dx: 175, dy: -108 },
};

interface DragState {
  id: HUDModId;
  mode: 'move' | 'scale';
  pointerId: number;
  startClientX: number;
  startClientY: number;
  /** Widget top-left in layer coordinates at grab time. */
  startX: number;
  startY: number;
  size: { width: number; height: number };
  startScale: number;
}

export function HudEditorScreen() {
  const loadout = useVoidStore((s) => s.loadout);
  const target = useVoidStore((s) => s.editorTarget);
  const setTarget = useVoidStore((s) => s.setEditorTarget);
  const snap = useVoidStore((s) => s.editorSnap);
  const grid = useVoidStore((s) => s.editorGrid);
  const toggleSnap = useVoidStore((s) => s.toggleEditorSnap);
  const toggleGrid = useVoidStore((s) => s.toggleEditorGrid);
  const commitHud = useVoidStore((s) => s.commitHud);
  const setRoute = useVoidStore((s) => s.setRoute);

  const rootRef = useRef<HTMLDivElement | null>(null);
  const dragRef = useRef<DragState | null>(null);
  const [draft, setDraft] = useState<{
    id: HUDModId;
    anchor: DragState extends never ? never : Parameters<typeof screenPosition>[0];
    dx: number;
    dy: number;
    scale: number;
    x: number;
    y: number;
  } | null>(null);
  /** Measured box of the selected widget, in layer coordinates. */
  const [box, setBox] = useState<{ x: number; y: number; width: number; height: number } | null>(null);

  const measure = useCallback((id: HUDModId | null) => {
    const root = rootRef.current;
    if (!root || !id) return setBox(null);
    const el = root.querySelector<HTMLElement>(`[data-hud-id="${id}"]`);
    if (!el) return setBox(null);
    const layer = root.getBoundingClientRect();
    const rect = el.getBoundingClientRect();
    setBox({
      x: rect.left - layer.left,
      y: rect.top - layer.top,
      width: rect.width,
      height: rect.height,
    });
  }, []);

  useLayoutEffect(() => {
    measure(target);
  }, [measure, target, loadout, draft]);

  const viewport = () => {
    const rect = rootRef.current?.getBoundingClientRect();
    return { width: rect?.width ?? 1300, height: rect?.height ?? 820 };
  };

  const onPointerDown = useCallback(
    (id: HUDModId) => (e: React.PointerEvent) => {
      const root = rootRef.current;
      const item = hudItem(loadout, id);
      if (!root || !item) return;
      e.preventDefault();
      e.stopPropagation();
      setTarget(id);

      const layer = root.getBoundingClientRect();
      const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
      (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);

      dragRef.current = {
        id,
        mode: e.altKey || e.shiftKey ? 'scale' : 'move',
        pointerId: e.pointerId,
        startClientX: e.clientX,
        startClientY: e.clientY,
        startX: rect.left - layer.left,
        startY: rect.top - layer.top,
        // Unscaled box: the stored dx/dy are measured against the widget's own
        // size, and the rendered rect already carries the current scale.
        size: {
          width: rect.width / (item.scale ?? 1),
          height: rect.height / (item.scale ?? 1),
        },
        startScale: item.scale ?? 1,
      };
    },
    [loadout, setTarget],
  );

  const onPointerMove = useCallback(
    (e: React.PointerEvent) => {
      const drag = dragRef.current;
      if (!drag || drag.pointerId !== e.pointerId) return;
      const vp = viewport();
      const item = hudItem(loadout, drag.id);
      if (!item) return;

      if (drag.mode === 'scale') {
        // One pixel of downward/rightward travel is 1/200 of a scale step.
        const delta = (e.clientX - drag.startClientX + (e.clientY - drag.startClientY)) / 200;
        const scale = clampScale(Number((drag.startScale + delta).toFixed(2)));
        setDraft({
          id: drag.id,
          anchor: item.anchor,
          dx: item.dx,
          dy: item.dy,
          scale,
          x: drag.startX,
          y: drag.startY,
        });
        return;
      }

      const size = { width: drag.size.width * drag.startScale, height: drag.size.height * drag.startScale };
      let x = drag.startX + (e.clientX - drag.startClientX);
      let y = drag.startY + (e.clientY - drag.startClientY);
      if (snap) {
        x = snapTo(x, GRID);
        y = snapTo(y, GRID);
      }
      ({ x, y } = clampToViewport(x, y, size, vp));

      const anchor = anchorForPosition(x, y, size, vp);
      const { dx, dy } = placementFromScreen(anchor, x, y, drag.size, vp);
      setDraft({ id: drag.id, anchor, dx, dy, scale: drag.startScale, x, y });
    },
    [loadout, snap],
  );

  const endDrag = useCallback(
    (e: React.PointerEvent) => {
      const drag = dragRef.current;
      if (!drag || drag.pointerId !== e.pointerId) return;
      dragRef.current = null;
      const element = e.currentTarget as HTMLElement;
      if (element.hasPointerCapture(e.pointerId)) element.releasePointerCapture(e.pointerId);
      if (!draft) return;
      // One setHud per gesture, on drop.
      commitHud(draft.id, draft.anchor, draft.dx, draft.dy, draft.scale);
      setDraft(null);
    },
    [commitHud, draft],
  );

  const exit = useCallback(() => {
    setDraft(null);
    setRoute({ name: 'mods' });
  }, [setRoute]);

  const reset = useCallback(() => {
    for (const id of HUD_MOD_IDS) {
      const fallback = DEFAULT_HUD[id];
      commitHud(id, fallback.anchor, fallback.dx, fallback.dy, 1);
    }
  }, [commitHud]);

  const item = target ? hudItem(loadout, target) : null;
  const readoutX = draft?.x ?? (box?.x ?? 0);
  const readoutY = draft?.y ?? (box?.y ?? 0);
  const readoutScale = draft?.scale ?? item?.scale ?? 1;

  const override = draft
    ? { [draft.id]: { anchor: draft.anchor, dx: draft.dx, dy: draft.dy, scale: draft.scale } }
    : undefined;

  return (
    <div
      className="editor"
      ref={rootRef}
      onPointerMove={onPointerMove}
      onPointerUp={endDrag}
      onPointerCancel={endDrag}
    >
      <div className="editor__dim" />
      {grid && <div className="editor__grid" />}

      <HudLayer
        editor
        override={override}
        renderItem={(id, node) => (
          <div
            style={{ display: 'contents' }}
            onPointerDown={onPointerDown(id)}
            onPointerMove={onPointerMove}
            onPointerUp={endDrag}
          >
            {node}
          </div>
        )}
      />

      {box && target && (
        <>
          <div
            className="editor__selection"
            style={{
              left: (draft?.x ?? box.x) - 8,
              top: (draft?.y ?? box.y) - 8,
              width: box.width + 16,
              height: box.height + 16,
            }}
          />
          {[
            [-12, -12],
            [box.width + 4, -12],
            [-12, box.height + 4],
            [box.width + 4, box.height + 4],
          ].map(([hx, hy], index) => (
            <div
              key={index}
              className="editor__handle"
              style={{ left: (draft?.x ?? box.x) + hx!, top: (draft?.y ?? box.y) + hy! }}
            />
          ))}
          <div
            className="editor__label"
            style={{ left: (draft?.x ?? box.x), top: (draft?.y ?? box.y) - 38 }}
          >
            <span className="editor__label-name">{MOD_REGISTRY[target].label}</span>
            <span className="editor__label-meta">
              x {Math.round(readoutX)}  ·  y {Math.round(readoutY)}  ·  {readoutScale.toFixed(1)}×
            </span>
          </div>
        </>
      )}

      <div className="editor__toolbar">
        <span className="editor__tool">
          <Icon name="move" size={13} />
          HUD layout
        </span>
        <span className="editor__divider" />
        <button
          type="button"
          className={`editor__tool${snap ? ' editor__tool--active' : ''}`}
          onClick={toggleSnap}
        >
          {snap && <Icon name="check" size={13} />}
          Snap
        </button>
        <button
          type="button"
          className={`editor__tool${grid ? ' editor__tool--active' : ''}`}
          onClick={toggleGrid}
        >
          <Icon name="layers" size={13} />
          Grid
        </button>
        <button type="button" className="editor__tool" onClick={reset}>
          <Icon name="rotate-ccw" size={13} />
          Reset
        </button>
        <button type="button" className="editor__tool editor__tool--primary" onClick={exit}>
          Done
        </button>
      </div>

      <div className="editor__hint">{EDITOR_HINT}</div>
    </div>
  );
}
