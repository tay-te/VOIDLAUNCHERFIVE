/**
 * Overlay — HUD layout · frame `244:1722`.
 *
 * Direct manipulation over the live game, with no panel: drag moves a widget,
 * Alt- or Shift-drag scales it, `Snap` quantises to the 8 px grid, the selection
 * frame reads out `x · y · scale` live, and the placement is written back with
 * `void.setHud` **on drop** — one call per gesture, not one per frame (§9; Java
 * mirrors the whole layout to Rust as the `hud` protocol message at the same
 * moment). `setHud` returns the item Java actually stored after its own snapping
 * and clamping, and that is what the widget settles on.
 */

import { useCallback, useLayoutEffect, useRef, useState } from 'react';
import { EditorToolbar, HintBar, SelectionFrame, formatSelectionReadout } from '@/ui';
import { HUD_MOD_IDS, type HUDAnchor, type HUDModId } from '@/bridge/protocol';
import { hudItem, useVoidStore } from '@/store/store';
import {
  GRID,
  anchorForPosition,
  clampScale,
  clampToViewport,
  placementFromScreen,
  snapTo,
} from '@/store/hud-geometry';
import { modLabel } from '@/registry';
import { HudLayer, type LivePlacement } from '@/hud/HudLayer';

/**
 * The frame's copy. `⌥` is what it prints; Shift does the same thing, because a
 * bare Alt-drag is claimed by some window managers.
 */
export const EDITOR_HINT = 'Drag to move   ·   ⌥ drag to scale   ·   Esc to exit';

/** Factory layout, matching the placements drawn on frame 244:1722. */
export const DEFAULT_HUD: Record<HUDModId, { anchor: HUDAnchor; dx: number; dy: number }> = {
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
  /** Unscaled widget box — `dx`/`dy` are measured against this. */
  size: { width: number; height: number };
  startScale: number;
}

interface Draft extends LivePlacement {
  id: HUDModId;
  /** Live screen position, for the readout and the selection box. */
  x: number;
  y: number;
}

export function HudEditorScreen() {
  const loadout = useVoidStore((s) => s.loadout);
  const target = useVoidStore((s) => s.editorTarget);
  const setTarget = useVoidStore((s) => s.setEditorTarget);
  const snap = useVoidStore((s) => s.editorSnap);
  const grid = useVoidStore((s) => s.editorGrid);
  const setSnap = useVoidStore((s) => s.setEditorSnap);
  const setGrid = useVoidStore((s) => s.setEditorGrid);
  const commitHud = useVoidStore((s) => s.commitHud);
  const setRoute = useVoidStore((s) => s.setRoute);

  const rootRef = useRef<HTMLDivElement | null>(null);
  const dragRef = useRef<DragState | null>(null);
  const [draft, setDraft] = useState<Draft | null>(null);
  /** Measured box of the selected widget, in layer coordinates. */
  const [box, setBox] = useState<{ x: number; y: number; width: number; height: number } | null>(
    null,
  );

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
      const element = e.currentTarget as HTMLElement;
      const rect = element.getBoundingClientRect();
      element.setPointerCapture(e.pointerId);

      const scale = item.scale ?? 1;
      dragRef.current = {
        id,
        mode: e.altKey || e.shiftKey ? 'scale' : 'move',
        pointerId: e.pointerId,
        startClientX: e.clientX,
        startClientY: e.clientY,
        startX: rect.left - layer.left,
        startY: rect.top - layer.top,
        size: { width: rect.width / scale, height: rect.height / scale },
        startScale: scale,
      };
    },
    [loadout, setTarget],
  );

  const onPointerMove = useCallback(
    (e: React.PointerEvent) => {
      const drag = dragRef.current;
      if (!drag || drag.pointerId !== e.pointerId) return;
      const item = hudItem(loadout, drag.id);
      if (!item) return;
      const vp = viewport();

      if (drag.mode === 'scale') {
        // 200 px of travel is one whole scale step, in either axis.
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

      const size = {
        width: drag.size.width * drag.startScale,
        height: drag.size.height * drag.startScale,
      };
      let x = drag.startX + (e.clientX - drag.startClientX);
      let y = drag.startY + (e.clientY - drag.startClientY);
      if (snap) {
        x = snapTo(x, GRID);
        y = snapTo(y, GRID);
      }
      ({ x, y } = clampToViewport(x, y, size, vp));

      // Re-pick the anchor from where it landed, so a widget dragged to the
      // bottom-right corner stays bottom-right when the window resizes (§8.1).
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
      if (element.hasPointerCapture?.(e.pointerId)) element.releasePointerCapture(e.pointerId);
      if (!draft) return;
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
    setDraft(null);
  }, [commitHud]);

  const item = target ? hudItem(loadout, target) : null;
  const selectionX = draft?.x ?? box?.x ?? 0;
  const selectionY = draft?.y ?? box?.y ?? 0;
  const readoutScale = draft?.scale ?? item?.scale ?? 1;

  const override = draft
    ? ({ [draft.id]: { anchor: draft.anchor, dx: draft.dx, dy: draft.dy, scale: draft.scale } } as
        Partial<Record<HUDModId, LivePlacement>>)
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
        <SelectionFrame
          name={modLabel(target)}
          readout={formatSelectionReadout(selectionX, selectionY, readoutScale)}
          style={{
            // The frame is the widget box plus the design's 8px bleed.
            left: selectionX - 8,
            top: selectionY - 8,
            width: box.width + 16,
            height: box.height + 16,
          }}
        />
      )}

      <EditorToolbar
        className="editor__toolbar"
        snap={snap}
        onSnapChange={setSnap}
        grid={grid}
        onGridChange={setGrid}
        onReset={reset}
        onDone={exit}
      />

      <HintBar className="editor__hint">{EDITOR_HINT}</HintBar>
    </div>
  );
}
