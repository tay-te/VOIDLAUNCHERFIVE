/**
 * Overlay — HUD layout · frame `244:1722`.
 *
 * Direct manipulation over the live game, with no panel: drag a widget to move it, drag one of
 * the selection frame's corner grips to scale it, arrow keys nudge, `Snap` quantises to the grid,
 * the selection frame reads out `x · y · scale` live, and the placement is written back with
 * `void.setHud` **on drop** — one call per gesture, not one per frame (§9; Java mirrors the whole
 * layout to Rust as the `hud` protocol message at the same moment). `setHud` returns the item Java
 * actually stored after its own snapping and clamping, and that is what the widget settles on.
 *
 * ## Three things measured in game, each of which had broken something here
 *
 * **1. `display: contents` is supported, and it is why nothing moved.** The drag handlers used to
 * live on a `<div style={{display:'contents'}}>` wrapped around each widget, and the gesture
 * measured its geometry off `e.currentTarget` — that wrapper. An element with `display: contents`
 * has no principal box, so in game:
 *
 * ```
 * hud-item[keystrokes] rect = 31.0,536.0  130.0x175.0     <- the widget
 * wrapper <div> computedDisplay="contents"
 *                       rect =  0.0,  0.0    0.0x0.0      <- what the editor measured
 *                       clientRects=0  offsetParent=no
 * ```
 *
 * So every gesture started from a widget the editor believed was at the layer origin with no
 * size. Grabbing a chip **teleported** it — measured: keystrokes dragged by (+502, -306) landed at
 * anchor `top` `dx -227`, not `bottom-left dx 533 dy 230` — and because the size was zero the
 * clamp never clamped and `anchorForPosition` picked the anchor from the wrong point. The handlers
 * are on the `.hud-item` box now (`HudLayer`'s `slotProps`), which is the box that has the
 * geometry, and there is no wrapper at all.
 *
 * **2. Pointer events do fire, and capture works.** This was the suspicion, and it is wrong.
 * Measured through the real host path (`View::FireMouseEvent` → WebCore): `pointerdown`,
 * `pointermove` (one per game frame, 25 for 24 posted moves), `pointerup`,
 * `gotpointercapture` / `lostpointercapture` all arrive with `pointerType=mouse`, `pointerId=1`;
 * `setPointerCapture` returns and `hasPointerCapture` is immediately true; and capture **retargets
 * correctly** — a drag begun on a keycap and ended 500 px away kept `target` on the captured
 * element the whole way. So this file may use them, and does.
 *
 * **3. A mouse event carries no modifier state, ever.** `WebView.fireMouseEvent(type, x, y,
 * button)` has no modifier parameter — there is nowhere for one to travel — and every event
 * measured in game reads `altKey=false shiftKey=false ctrlKey=false metaKey=false`. The frame's
 * `⌥ drag to scale` was therefore not merely untested, it was **unimplementable**: `e.altKey` in a
 * pointer handler here is a compile-time constant. Scaling is on the selection frame's four corner
 * grips instead — which `SelectionFrame` has always drawn and `onHandlePointerDown` has always
 * offered, unused. That is the better affordance anyway: it is visible, it is where a person
 * reaches for it, and it does not depend on a key the engine cannot report.
 *
 * ## The gesture, and why it does not repaint the world
 *
 * An idle open menu paints nothing (rendering-invariants §4) and a drag must not be the thing that
 * breaks that. `CellMeter` sets the precedent — report only when the value changes — and this does
 * the same: geometry is measured **once per gesture**, every move is arithmetic against it, and
 * `setDraft` is skipped entirely when the placement it computes is the one already on screen. A
 * slow drag across a snapped grid therefore renders once per 8 px crossed, not once per frame.
 *
 * Input is coalesced to one move per game frame, so a fast drag arrives several pixels apart:
 * everything below interpolates to the pointer's position rather than accumulating deltas, so
 * where the widget lands never depends on how many moves were delivered.
 */

import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import {
  EditorToolbar,
  HintBar,
  SelectionFrame,
  formatSelectionReadout,
  type SelectionHandle,
} from '@/ui';
import { HUD_MOD_IDS, type HUDAnchor, type HUDModId } from '@/bridge/protocol';
import { hudItem, isModOn, useVoidStore } from '@/store/store';
import {
  DEFAULT_HUD,
  GRID,
  anchorForPosition,
  clampScale,
  clampToViewport,
  placementFromScreen,
  screenPosition,
  snapTo,
  type Size,
} from '@/store/hud-geometry';
import { modLabel } from '@/registry';
import { isEscape } from '@/menu/keys';
import { HudLayer, type LivePlacement } from '@/hud/HudLayer';

export { DEFAULT_HUD };

/**
 * The hint bar's copy.
 *
 * It no longer says `⌥ drag to scale`, because a mouse event in this engine carries no modifier
 * state and never can (see the header). A hint that names a gesture the engine cannot deliver is
 * worse than no hint: it sends the player looking for a fault in their own hands.
 */
export const EDITOR_HINT = 'Drag to move   ·   Corners scale   ·   Arrows nudge   ·   Esc to exit';

/**
 * The hint, plus what the editor is *not* showing.
 *
 * A widget whose mod is off is not on the HUD, so there is genuinely nothing here to move — but
 * saying nothing at all is how a player concludes the editor has lost their CPS counter.
 * Rendering-invariants §15: where an absence cannot be gated away, make it visible.
 *
 * One string rather than two nodes, so the hint is one text node and reads as one sentence.
 */
export function editorHint(off: number): string {
  if (off <= 0) return EDITOR_HINT;
  return `${EDITOR_HINT}   ·   ${off} more ${off === 1 ? 'is' : 'are'} switched off`;
}

/** How far a corner grip travels to double a widget, relative to the widget's own size. */
const SCALE_TRAVEL = 1;

/** Scale steps the grips land on: 0.05 free, 0.1 while `Snap` is on. */
const SCALE_STEP = { free: 0.05, snapped: 0.1 };

/** One arrow press, in CSS pixels, when `Snap` is off. Snapped, it moves a whole grid cell. */
const NUDGE = 1;

interface Drag {
  id: HUDModId;
  mode: 'move' | 'scale';
  /** Which grip is being dragged. `move` gestures have none. */
  handle: SelectionHandle | null;
  pointerId: number;
  /** Pointer at grab time, in client coordinates. */
  fromX: number;
  fromY: number;
  /** The widget's rendered top-left in layer coordinates at grab time. */
  originX: number;
  originY: number;
  /**
   * The widget's rendered box at `scale = 1`.
   *
   * Rendered, not intrinsic: it already carries the mod's own `scale` setting, which this editor
   * does not touch. `hud_item.scale` multiplies it, so the box at any draft scale is
   * `unit × scale` and no re-measure is ever needed mid-gesture.
   */
  unit: Size;
  /** `hud_item.scale` at grab time. */
  startScale: number;
  /** The placement at grab time, so a scale gesture can leave `dx`/`dy` alone. */
  anchor: HUDAnchor;
  dx: number;
  dy: number;
}

/** What the editor is showing that the loadout does not yet know about. */
interface Draft extends LivePlacement {
  id: HUDModId;
  /** The rendered box, in layer coordinates — the selection frame is drawn straight off this. */
  x: number;
  y: number;
  width: number;
  height: number;
}

/** Which way a grip pulls on each axis: `se` grows on both, `nw` shrinks on both. */
function gripSigns(handle: SelectionHandle): { x: number; y: number } {
  return {
    x: handle === 'ne' || handle === 'se' ? 1 : -1,
    y: handle === 'sw' || handle === 'se' ? 1 : -1,
  };
}

/** Round to a step without float dust — `0.1 + 0.2` must not print `0.30000000000000004`. */
function toStep(value: number, step: number): number {
  return Math.round(Math.round(value / step) * step * 100) / 100;
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
  const resetHud = useVoidStore((s) => s.resetHud);
  const setRoute = useVoidStore((s) => s.setRoute);

  const rootRef = useRef<HTMLDivElement | null>(null);
  const dragRef = useRef<Drag | null>(null);
  /** Undoes the window listeners of the gesture in flight, if there is one. */
  const releaseRef = useRef<(() => void) | null>(null);
  const [draft, setDraft] = useState<Draft | null>(null);
  /** The selected widget's rendered box, measured from the DOM when nothing is being dragged. */
  const [box, setBox] = useState<Draft | null>(null);

  /** Every HUD mod that is on — the ones the editor draws, and the ones the arrows cycle. */
  const placeable = HUD_MOD_IDS.filter((id) => isModOn(loadout, id));

  /* ---- geometry ---------------------------------------------------------------------- */

  const viewport = useCallback((): Size => {
    const rect = rootRef.current?.getBoundingClientRect();
    return { width: rect?.width ?? 1300, height: rect?.height ?? 820 };
  }, []);

  /** The `.hud-item` box for a mod, in layer coordinates. `null` when it is not on screen. */
  const measure = useCallback((id: HUDModId | null): Draft | null => {
    const root = rootRef.current;
    if (!root || !id) return null;
    const el = root.querySelector<HTMLElement>(`[data-hud-id="${id}"]`);
    if (!el) return null;
    const layer = root.getBoundingClientRect();
    const rect = el.getBoundingClientRect();
    const stored = hudItem(useVoidStore.getState().loadout, id);
    const item = stored ?? { ...DEFAULT_HUD[id], scale: 1 };
    return {
      id,
      anchor: item.anchor,
      dx: item.dx,
      dy: item.dy,
      scale: item.scale ?? 1,
      x: rect.left - layer.left,
      y: rect.top - layer.top,
      width: rect.width,
      height: rect.height,
    };
  }, []);

  // Only when a gesture is *not* running: mid-drag the draft is the truth, computed rather than
  // measured, and re-measuring here would cost a second render for every pointer move.
  useLayoutEffect(() => {
    if (dragRef.current) return;
    setBox(measure(target));
  }, [measure, target, loadout, draft]);

  /* ---- committing -------------------------------------------------------------------- */

  const commit = useCallback(
    (next: Draft | null) => {
      if (!next) return;
      commitHud(next.id, next.anchor, next.dx, next.dy, next.scale);
    },
    [commitHud],
  );

  /**
   * Place a widget from a placement, and hand back the whole draft including the box it will
   * occupy. One function for the pointer, the arrow keys and Reset alike, so all three land in
   * exactly the same place for the same numbers.
   */
  const draftFor = useCallback(
    (id: HUDModId, anchor: HUDAnchor, dx: number, dy: number, scale: number, unit: Size): Draft => {
      const size = { width: unit.width * scale, height: unit.height * scale };
      const { x, y } = screenPosition(anchor, dx, dy, size, viewport());
      return { id, anchor, dx, dy, scale, x, y, width: size.width, height: size.height };
    },
    [viewport],
  );

  /* ---- the gesture ------------------------------------------------------------------- */

  /** The live values the window handlers read. They must not be swapped out mid-gesture. */
  const latest = useRef({ snap, draft, commit, draftFor, viewport });
  useEffect(() => {
    latest.current = { snap, draft, commit, draftFor, viewport };
  });

  const onMove = useCallback((event: PointerEvent) => {
    const drag = dragRef.current;
    if (!drag || drag.pointerId !== event.pointerId) return;
    const { snap: snapping, draftFor: build, viewport: vp } = latest.current;
    const dxPointer = event.clientX - drag.fromX;
    const dyPointer = event.clientY - drag.fromY;
    let next: Draft;

    if (drag.mode === 'scale' && drag.handle) {
      // Pulling a grip away from the widget's centre grows it. Measured against the widget's own
      // size rather than a fixed pixel budget, so a 40px chip and a 175px keypad both take the
      // same *proportional* travel to double — a fixed budget makes small widgets uncontrollable
      // and large ones feel stuck.
      const sign = gripSigns(drag.handle);
      const travel = sign.x * dxPointer + sign.y * dyPointer;
      const reach = Math.max(48, (drag.unit.width + drag.unit.height) / 2) * SCALE_TRAVEL;
      const step = snapping ? SCALE_STEP.snapped : SCALE_STEP.free;
      const scale = clampScale(toStep(drag.startScale * (1 + travel / reach), step));
      next = build(drag.id, drag.anchor, drag.dx, drag.dy, scale, drag.unit);
    } else {
      const size = { width: drag.unit.width * drag.startScale, height: drag.unit.height * drag.startScale };
      let x = drag.originX + dxPointer;
      let y = drag.originY + dyPointer;
      if (snapping) {
        x = snapTo(x, GRID);
        y = snapTo(y, GRID);
      }
      ({ x, y } = clampToViewport(x, y, size, vp()));
      // Re-pick the anchor from where it landed, so a widget dragged to the bottom-right corner
      // stays bottom-right when the window resizes (§8.1).
      const anchor = anchorForPosition(x, y, size, vp());
      const { dx, dy } = placementFromScreen(anchor, x, y, size, vp());
      next = build(drag.id, anchor, dx, dy, drag.startScale, drag.unit);
    }

    // §4's budget: report only when the value changed. Between two grid cells the whole move is a
    // subtraction and three compares, and the page paints nothing.
    const now = latest.current.draft;
    if (
      now &&
      now.id === next.id &&
      now.anchor === next.anchor &&
      now.dx === next.dx &&
      now.dy === next.dy &&
      now.scale === next.scale
    ) {
      return;
    }
    setDraft(next);
  }, []);

  const finish = useCallback(
    (event: PointerEvent, keep: boolean) => {
      const drag = dragRef.current;
      if (!drag || drag.pointerId !== event.pointerId) return;
      // The release carries a position that may never have arrived as a move of its own — the
      // stream is coalesced at the game frame, so a quick flick can end between two of them.
      if (keep) onMove(event);
      const settled = latest.current.draft;
      releaseRef.current?.();
      if (keep) latest.current.commit(settled);
      setDraft(null);
    },
    [onMove],
  );

  const onUp = useCallback((event: PointerEvent) => finish(event, true), [finish]);
  const onCancel = useCallback((event: PointerEvent) => finish(event, false), [finish]);

  /** Start a gesture on `id`. `handle` non-null makes it a scale rather than a move. */
  const begin = useCallback(
    (id: HUDModId, handle: SelectionHandle | null, event: React.PointerEvent) => {
      const root = rootRef.current;
      if (!root) return;
      // Deliberately not `!== 0`: Java maps LWJGL's buttons onto Ultralight's numbering, and only
      // the secondary button needs turning away — the same reasoning as `CellMeter`.
      if (event.button === 2) return;
      const state = useVoidStore.getState();
      const item = hudItem(state.loadout, id) ?? { ...DEFAULT_HUD[id], scale: 1 };
      event.preventDefault();
      event.stopPropagation();
      releaseRef.current?.();
      setTarget(id);

      const el = root.querySelector<HTMLElement>(`[data-hud-id="${id}"]`);
      if (!el) return;
      const layer = root.getBoundingClientRect();
      const rect = el.getBoundingClientRect();
      const scale = item.scale ?? 1;
      // A zero-size box means the widget has not been laid out — jsdom, or a widget that drew
      // nothing. Measuring a gesture against it would put the widget at the layer origin, which
      // is precisely the bug this file exists to have fixed, so refuse to start instead.
      if (rect.width === 0 || rect.height === 0 || scale === 0) return;

      dragRef.current = {
        id,
        mode: handle === null ? 'move' : 'scale',
        handle,
        pointerId: event.pointerId,
        fromX: event.clientX,
        fromY: event.clientY,
        originX: rect.left - layer.left,
        originY: rect.top - layer.top,
        unit: { width: rect.width / scale, height: rect.height / scale },
        startScale: scale,
        anchor: item.anchor,
        dx: item.dx,
        dy: item.dy,
      };

      // Capture keeps the gesture on this widget when the pointer wanders off it — measured
      // working in game, retargeting every move to the captured element. The window listeners
      // below are not redundant with it: they are what ends a gesture whose release lands outside
      // the view entirely, which capture alone does not guarantee.
      try {
        (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
      } catch {
        /* No capture is survivable; the window listeners carry the gesture. */
      }

      window.addEventListener('pointermove', onMove);
      window.addEventListener('pointerup', onUp);
      window.addEventListener('pointercancel', onCancel);
      releaseRef.current = () => {
        window.removeEventListener('pointermove', onMove);
        window.removeEventListener('pointerup', onUp);
        window.removeEventListener('pointercancel', onCancel);
        dragRef.current = null;
        releaseRef.current = null;
      };
    },
    [onCancel, onMove, onUp, setTarget],
  );

  const slotProps = useCallback(
    (id: HUDModId) => ({
      onPointerDown: (event: React.PointerEvent) => begin(id, null, event),
      role: 'button' as const,
      tabIndex: -1,
      'aria-label': `${modLabel(id)} placement`,
    }),
    [begin],
  );

  const onGrip = useCallback(
    (handle: SelectionHandle, event: React.PointerEvent) => {
      const id = useVoidStore.getState().editorTarget;
      if (id) begin(id, handle, event);
    },
    [begin],
  );

  // A route change mid-drag must not leave a listener behind on the move stream.
  useEffect(() => () => releaseRef.current?.(), []);

  /* ---- the keyboard ------------------------------------------------------------------ */

  /**
   * Arrows nudge, Tab cycles, Escape leaves.
   *
   * Nudging is not a convenience here, it is the only way to place a widget *exactly*: input
   * arrives coalesced at the game's frame rate, so a pointer cannot reliably land on a chosen
   * pixel. It is also the one placement gesture that works with the panel scrim over the game and
   * no mouse in hand at all.
   *
   * There is no modifier variant — no Shift for a bigger step — on purpose. Chorded modifiers are
   * not something this host has been shown to deliver, and a shortcut that silently does the
   * unmodified thing is worse than one that does not exist. `Snap` already switches the step
   * between 1 px and a whole grid cell, which is the same choice by a control that is on screen.
   */
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (isEscape(event)) return; // MenuLayer owns Escape; it goes back to the grid.
      const state = useVoidStore.getState();
      const id = state.editorTarget;

      if (event.key === 'Tab') {
        event.preventDefault();
        if (placeable.length === 0) return;
        const at = id ? placeable.indexOf(id) : -1;
        setTarget(placeable[(at + 1 + placeable.length) % placeable.length] ?? placeable[0]!);
        return;
      }

      const step: Record<string, [number, number]> = {
        ArrowLeft: [-1, 0],
        ArrowRight: [1, 0],
        ArrowUp: [0, -1],
        ArrowDown: [0, 1],
      };
      const move = step[event.key];
      if (!move || !id || dragRef.current) return;
      event.preventDefault();

      const current = measure(id);
      if (!current) return;
      const distance = state.editorSnap ? GRID : NUDGE;
      const unit = {
        width: current.width / (current.scale || 1),
        height: current.height / (current.scale || 1),
      };
      const size = { width: current.width, height: current.height };
      const vp = viewport();
      let x = current.x + move[0] * distance;
      let y = current.y + move[1] * distance;
      ({ x, y } = clampToViewport(x, y, size, vp));
      const anchor = anchorForPosition(x, y, size, vp);
      const { dx, dy } = placementFromScreen(anchor, x, y, size, vp);
      // Straight to the loadout: a key press is a whole gesture, so there is nothing to preview.
      commit(draftFor(id, anchor, dx, dy, current.scale, unit));
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [commit, draftFor, measure, placeable, setTarget, viewport]);

  /* ---- the toolbar ------------------------------------------------------------------- */

  const exit = useCallback(() => {
    releaseRef.current?.();
    setDraft(null);
    setRoute({ name: 'mods' });
  }, [setRoute]);

  const reset = useCallback(() => {
    // The whole of it is `store.resetHud` — including standing the snap grid down, without which
    // Reset lands a few pixels off the factory layout rather than on it. See there.
    resetHud();
    setDraft(null);
  }, [resetHud]);

  /* ---- the frame --------------------------------------------------------------------- */

  const selection = draft && target === draft.id ? draft : box;
  const off = HUD_MOD_IDS.length - placeable.length;

  return (
    <div className="editor" ref={rootRef}>
      <div className="editor__dim" />
      {grid && <div className="editor__grid" />}

      <HudLayer editor slotProps={slotProps} override={draft ? { [draft.id]: draft } : undefined} />

      {selection && (
        <SelectionFrame
          name={modLabel(selection.id)}
          readout={formatSelectionReadout(selection.x, selection.y, selection.scale)}
          onHandlePointerDown={onGrip}
          style={{
            // The frame is the widget box plus the design's 8px bleed.
            left: selection.x - 8,
            top: selection.y - 8,
            width: selection.width + 16,
            height: selection.height + 16,
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
        doneKbd={
          // The cap sits on the control Escape drives, exactly as it does in the mods bar — one
          // cap, never two, and never on a control the key does not actually press.
          <span className="okbd" aria-hidden="true">
            esc
          </span>
        }
      />

      <HintBar className="editor__hint">{editorHint(off)}</HintBar>
    </div>
  );
}
