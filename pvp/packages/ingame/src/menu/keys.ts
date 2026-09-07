/**
 * Key identification that survives the in-game engine.
 *
 * ## `e.key` is not reliable here, and Escape is the case that proves it
 *
 * The host injects key events with a virtual key code and, where there is one, the character
 * that was typed (`VoidMenuScreen.keyPressed` → `UiHost.keyDown` / `keyChar`). WebCore derives
 * `KeyboardEvent.key` from that, and for a key with no character it has nothing to derive from:
 * measured in game, Escape arrives as
 *
 * ```
 * key = "Unidentified"   code = ""   which = 27   keyCode = 27
 * ```
 *
 * so `e.key === 'Escape'` is false on every Escape the page will ever see. That was a live bug
 * with a visible symptom: the `esc` cap was drawn on `‹ Mods`, Java forwarded the key because
 * `keepsEscape()` said the page wanted it, the page's handler dropped it on the `key` test, and
 * Escape on a properties page did **nothing at all** — neither back nor close. An affordance
 * promising a key that does nothing is worse than no affordance.
 *
 * `Enter` and the arrows are not affected and are deliberately left alone: Enter carries a
 * character and the arrows arrive as AppKit private-use characters that WebCore does map, which
 * is why `e.key === 'ArrowDown'` has always worked in the palette and the grid. The rule this
 * file encodes is narrower than "never trust `e.key`" — it is **a key with no character needs a
 * numeric fallback**, and Escape is the only one of those the overlay binds.
 *
 * The numeric code is checked *as well as* the name rather than instead of it, because the same
 * bundle runs in a browser (the `?debug` harness, and jsdom) where `e.key` is correct and
 * `keyCode` is deprecated-but-present. Both are cheap; neither is sufficient alone.
 */

/** Virtual key code for Escape. `KeyNames.java`: `put("ESCAPE", 1, 0x1B)`. */
const VK_ESCAPE = 27;

/** A keyboard event this module can identify, from React or from the DOM. */
type AnyKeyEvent = Pick<KeyboardEvent, 'key'> & {
  keyCode?: number;
  which?: number;
};

/**
 * True for Escape, however this engine chose to describe it.
 *
 * @example
 * ```ts
 * if (!isEscape(e)) return;
 * ```
 */
export function isEscape(e: AnyKeyEvent): boolean {
  return e.key === 'Escape' || e.keyCode === VK_ESCAPE || e.which === VK_ESCAPE;
}
