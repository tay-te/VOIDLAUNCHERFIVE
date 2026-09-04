/**
 * jsdom shims for APIs the overlay uses that jsdom does not implement.
 * Nothing here is a stub for game behaviour — the fake bridge from
 * `@void/protocol` is what stands in for Java.
 */

// Pointer capture: jsdom has no PointerEvent implementation, and the slider and
// the HUD editor both capture the pointer for the duration of a drag.
if (typeof Element !== 'undefined' && !Element.prototype.setPointerCapture) {
  Element.prototype.setPointerCapture = function setPointerCapture() {};
  Element.prototype.releasePointerCapture = function releasePointerCapture() {};
  Element.prototype.hasPointerCapture = function hasPointerCapture() {
    return false;
  };
}
