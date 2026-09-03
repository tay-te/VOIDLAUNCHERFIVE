import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

// The design tokens and the three bundled OFL families, from the shared package.
// `index.html` carries `data-renderer="webview"`, which is what selects the launcher's
// layer of `tokens.css`: the launcher runs in a real system webview, so it keeps the
// blur radii and the noise the in-game bundle has to drop (ultralight-notes.md §1–2).
import '@void/ui/tokens.css';
import '@void/ui/fonts.css';
import './local/app.css';

import { App } from './App';

const root = document.getElementById('root');
if (!root) throw new Error('#root is missing from index.html');

createRoot(root).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
