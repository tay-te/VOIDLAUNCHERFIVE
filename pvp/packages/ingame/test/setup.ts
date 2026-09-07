/**
 * jsdom shims for APIs the overlay uses that jsdom does not implement.
 * Nothing here is a stub for game behaviour — the fake bridge from
 * `@void/protocol` is what stands in for Java.
 */

/**
 * The view size the in-game overlay actually gets, in CSS pixels.
 *
 * `VoidClient` fits the framebuffer to a 1300 x 820 design canvas, so on a 16:9 client the
 * logical view is 1458 x 820 — measured in game, and the size every geometry number in
 * `overlay.css` and `solveGrid` is quoted at. jsdom's default is 1024 x 768, which is not a
 * size this UI ever runs at; `ModsScreen` reads `innerWidth` / `innerHeight` to shape the grid,
 * so leaving the default in place would have the suite assert a layout nobody sees.
 *
 * jsdom defines these as accessors on the window, so they are replaced rather than assigned.
 */
export const IN_GAME_VIEW = { width: 1458, height: 820 } as const;

if (typeof window !== 'undefined') {
  Object.defineProperty(window, 'innerWidth', {
    configurable: true,
    writable: true,
    value: IN_GAME_VIEW.width,
  });
  Object.defineProperty(window, 'innerHeight', {
    configurable: true,
    writable: true,
    value: IN_GAME_VIEW.height,
  });
}

// Pointer capture: jsdom has no PointerEvent implementation, and the slider and
// the HUD editor both capture the pointer for the duration of a drag.
if (typeof Element !== 'undefined' && !Element.prototype.setPointerCapture) {
  Element.prototype.setPointerCapture = function setPointerCapture() {};
  Element.prototype.releasePointerCapture = function releasePointerCapture() {};
  Element.prototype.hasPointerCapture = function hasPointerCapture() {
    return false;
  };
}
