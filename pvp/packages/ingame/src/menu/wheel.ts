/**
 * Wheel handling for the overlay.
 *
 * The engine's default action for a wheel event is a **smooth-scroll animation** — WebCore eases
 * the delta out over roughly 50 ms, ticked by Ultralight's scroll timer. In a browser that is
 * what makes a mouse detent pleasant. Here it is wrong twice over.
 *
 * It is wrong for a trackpad, which is the device the overlay is actually used with: macOS has
 * already done the easing, the deltas arriving *are* the animation, and easing them again puts
 * the page permanently behind the fingers.
 *
 * And it is wrong at the rate the deltas arrive. The mod hands one over per rendered frame, and
 * an ease that takes ~50 ms is restarted every ~10 ms and never finishes: measured that way, the
 * page did not move at all until the gesture stopped, and then delivered the lot. Slowing the
 * deltas down to let each ease complete only trades that for a staircase — one step per event,
 * with ~30 ms of stillness in each.
 *
 * So the wheel is applied here instead, straight onto the box's `scrollTop`, and the default is
 * cancelled. Hit-testing and event routing stay the engine's — this is a real DOM `wheel` event
 * on a real target — and only the animation is taken away from it.
 */

import { useEffect } from 'react';

/** Pixels per line, for the unlikely case the engine reports a line-granularity wheel. */
const LINE_PX = 16;

/**
 * The box a wheel over {@code from} should scroll: the nearest ancestor that can actually take
 * the movement being asked for.
 *
 * Two tests, not one. A box has to *be* a scroller (`overflow-y`) and it has to *have room*, and
 * a box already at the end in the direction asked for is skipped so the gesture chains outwards
 * the way a browser's does — without that, a flick that reaches the top of the properties panel
 * would stop dead there instead of passing to whatever is behind it.
 */
function scrollableUnder(from: EventTarget | null, deltaY: number): HTMLElement | null {
  let el = from instanceof Element ? from : null;
  while (el) {
    if (el instanceof HTMLElement) {
      const overflowY = window.getComputedStyle(el).overflowY;
      if (overflowY === 'auto' || overflowY === 'scroll') {
        const room = el.scrollHeight - el.clientHeight;
        const atStart = deltaY < 0 && el.scrollTop <= 0;
        const atEnd = deltaY > 0 && el.scrollTop >= room - 0.5;
        if (room > 0 && !atStart && !atEnd) {
          return el;
        }
      }
    }
    el = el.parentElement;
  }
  return null;
}

/** The wheel delta in CSS pixels, whatever granularity the event claims to be in. */
function pixels(event: WheelEvent, box: HTMLElement): number {
  if (event.deltaMode === 1) return event.deltaY * LINE_PX;
  if (event.deltaMode === 2) return event.deltaY * box.clientHeight;
  return event.deltaY;
}

/**
 * Applies the wheel to the box under the pointer, immediately and exactly.
 *
 * Registered while the menu is mounted and interactive; the HUD has nothing to scroll. Not
 * passive, because cancelling the engine's own smooth scroll is the entire point.
 */
export function useDirectWheel(enabled: boolean): void {
  useEffect(() => {
    if (!enabled) return undefined;
    const onWheel = (event: WheelEvent) => {
      const box = scrollableUnder(event.target, event.deltaY);
      if (!box) return;
      event.preventDefault();
      const room = box.scrollHeight - box.clientHeight;
      const next = box.scrollTop + pixels(event, box);
      box.scrollTop = next < 0 ? 0 : next > room ? room : next;
    };
    window.addEventListener('wheel', onWheel, { passive: false });
    return () => window.removeEventListener('wheel', onWheel);
  }, [enabled]);
}
