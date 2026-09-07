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

import { App } from './App';
import { connectBridge } from './bridge/connect';
import { installSpriteIcons } from './icons';
import { setGlBlur, setRenderer } from './ui';

const params = new URLSearchParams(typeof location === 'undefined' ? '' : location.search);

/** `?screen=Overlay-Mods.png` puts that Figma export behind the UI in dev. */
const backdrop = params.get('screen') ?? 'Overlay-Mods.png';

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
