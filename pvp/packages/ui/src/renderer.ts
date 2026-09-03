/**
 * Renderer modes.
 *
 * One React codebase, two bundles (§9). `apps/desktop` bundles these components for a
 * system webview; `packages/ingame` bundles them for Ultralight, which is WebKit-derived
 * and older. The difference is expressed with **one attribute on `<html>`**:
 *
 *     <html data-renderer="webview">     the launcher — real glass, real grain
 *     <html data-renderer="ultralight">  the overlay  — baked fallbacks
 *
 * Nothing in this package branches on the renderer in JavaScript. The attribute selects
 * a token layer in `tokens.css`, and every component reads the same tokens either way,
 * which is what keeps a single stylesheet correct in both places. Never branch on
 * `@supports (backdrop-filter: …)`: Ultralight may claim support and still no-op.
 */

import type { Renderer } from './tokens.js';

export type { Renderer };
export { RENDERERS } from './tokens.js';

/** Where the renderer attribute lives. */
const ATTRIBUTE = 'data-renderer';

/** Where the GL-blur flag lives. */
const GLBLUR_ATTRIBUTE = 'data-glblur';

/**
 * Set `data-renderer` on the document root.
 *
 * Call it once at boot. `apps/desktop` passes `'webview'`; `packages/ingame` passes
 * `'ultralight'`. Safe to call with no DOM (it becomes a no-op), so it can sit at
 * module scope in code that also runs under test.
 *
 * @param renderer Which renderer the page is running in.
 * @param root The element to stamp. Defaults to `document.documentElement`.
 */
export function setRenderer(renderer: Renderer, root?: Element | null): void {
  const element = root ?? globalThis.document?.documentElement;
  element?.setAttribute(ATTRIBUTE, renderer);
}

/**
 * Read the current renderer. Returns `'webview'` when nothing is set, which is the
 * documented default — the authored token values are the launcher's.
 */
export function getRenderer(root?: Element | null): Renderer {
  const element = root ?? globalThis.document?.documentElement;
  return element?.getAttribute(ATTRIBUTE) === 'ultralight' ? 'ultralight' : 'webview';
}

/**
 * Tell the page whether the host's GL blur pass is running.
 *
 * This is the host contract from `design/ultralight-notes.md` §1: the overlay panel sits
 * at 94% alpha when the game behind it is already being blurred by the host, and at 97%
 * when it is not. Only meaningful under `data-renderer="ultralight"`.
 */
export function setGlBlur(on: boolean, root?: Element | null): void {
  const element = root ?? globalThis.document?.documentElement;
  if (!element) return;
  if (on) element.setAttribute(GLBLUR_ATTRIBUTE, 'on');
  else element.removeAttribute(GLBLUR_ATTRIBUTE);
}
