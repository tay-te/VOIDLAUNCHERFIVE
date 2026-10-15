/**
 * Entry point of the in-game bundle.
 *
 * Loaded from the JAR classpath as `assets/void/ui/index.html`. There is no
 * network here and no devtools; everything the page shows arrives through
 * `window.void`.
 */

import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

// `@void/ui` owns the tokens, the faces and every component style; this bundle
// adds only the two-layer composition on top of them.
import '@void/ui/tokens.css';
import '@void/ui/fonts.css';
import '@void/ui/styles.css';
// The bar's icons, as a generated PNG sprite. Before `overlay.css`, which reads
// `--icon-sheet` off `:root`. See `scripts/build-icons.py` for why they are pixels.
import './styles/icons.css';
import './styles/overlay.css';
import './styles/palette.css';
import './styles/islands.css';
import './styles/toasts.css';
// Cartridge (v2 · Launch refresh, Figma section B): tokens, the generated sprite sheet, the HUD
// and the menu. Last, so they win over the v1 layers they replace.
import './cart/tokens.css';
import './cart/sprites.css';
import './cart/hud.css';
import './cart/menu.css';
import './cart/cosmetics.css';

import { App } from './App';
import { connectBridge } from './bridge/connect';
import { installSpriteIcons } from './icons';
import { installDevHook } from './cart/dev';
import { setGlBlur, setRenderer } from './ui';

const params = new URLSearchParams(typeof location === 'undefined' ? '' : location.search);

/** `?screen=Overlay-Mods.png` puts that Figma export behind the UI in dev. */
const backdrop = params.get('screen') ?? '00-scene.png';

// One attribute selects the whole Ultralight token layer in @void/ui: solid
// panel fills instead of backdrop-filter, no noise, a solid selection border.
setRenderer('ultralight');

// …and `@void/ui`'s icons draw from the PNG sprite rather than from inline SVG, which
// `design/ultralight-notes.md` §7 rates [risky] in this engine — "strokes, `stroke-linejoin`
// and non-scaling strokes are the usual casualties", i.e. every line of every Lucide glyph.
// One call, no call sites touched; see `icons.tsx`. Before the first render, so no icon is ever
// drawn twice.
installSpriteIcons();

// The host sets data-glblur itself when it knows whether its GL blur pass is
// running (ultralight-notes.md §1). index.html ships it on; ?glblur=off
// exercises the denser panel fill in the harness.
setGlBlur(params.get('glblur') !== 'off');

const { debug } = connectBridge();

// The UI tour's screenshot hook (cart/dev.ts). Inert unless the bench harness calls it.
installDevHook();
// Harness only: `?dev=<step>` shows one tour state (e.g. `?debug&dev=search:co`) after mount.
const devStep = debug ? params.get('dev') : null;
if (devStep) setTimeout(() => devStep.split(',').forEach((step) => window.__voidDev?.go(step)), 400);

// Geist / Geist Mono for the browser harness. In game the native font loader serves the TTFs by
// family name, so the woff2 never enter the bundle (and its 400 KB budget).
if (import.meta.env.DEV) void import('./cart/fonts-dev.css').catch(() => undefined);

// The harness renders at the authored frame size, which may exceed the browser
// window; let the page scroll so the whole frame is comparable to the PNG.
if (debug && import.meta.env.DEV) document.body.classList.add('void-debug-body');

const container = document.getElementById('void-root');
if (!container) throw new Error('#void-root is missing from index.html');

createRoot(container).render(
  <StrictMode>
    <App debugFrame={debug && import.meta.env.DEV} backdrop={backdrop} />
  </StrictMode>,
);
