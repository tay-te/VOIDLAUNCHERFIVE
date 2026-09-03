/**
 * Entry point of the in-game bundle.
 *
 * Loaded from the JAR classpath as `assets/void/ui/index.html`. There is no
 * network here and no devtools; everything the page shows arrives through
 * `window.void`.
 */

import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import './styles/tokens.css';
import './styles/fonts.css';
import './styles/base.css';
import './styles/controls.css';
import './styles/panel.css';
import './styles/hud.css';
import './styles/mods.css';
import './styles/loadouts.css';
import './styles/party.css';
import './styles/editor.css';
import './styles/palette.css';

import { App } from './App';
import { connectBridge } from './bridge/connect';

const params = new URLSearchParams(typeof location === 'undefined' ? '' : location.search);

/** `?screen=Overlay-Mods.png` puts that Figma export behind the UI in dev. */
const backdrop = params.get('screen') ?? 'Overlay-Mods.png';

const { debug } = connectBridge();

// The host sets data-glblur itself when it knows; default to "on" (index.html),
// and let ?glblur=off exercise the denser panel fill in the harness.
if (params.get('glblur') === 'off') {
  document.documentElement.setAttribute('data-glblur', 'off');
}

const container = document.getElementById('void-root');
if (!container) throw new Error('#void-root is missing from index.html');

createRoot(container).render(
  <StrictMode>
    <App debugFrame={debug && import.meta.env.DEV} backdrop={backdrop} />
  </StrictMode>,
);
