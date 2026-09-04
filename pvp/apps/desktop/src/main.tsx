import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

// The design tokens, the three bundled OFL families and every component style, from
// the shared package — imported once, here, in the order `@void/ui`'s README gives.
// `index.html` carries `data-renderer="webview"`, which is what selects the launcher's
// layer of `tokens.css`: the launcher runs in a real system webview, so it keeps the
// blur radii and the noise the in-game bundle has to drop (ultralight-notes.md §1–2).
// `local/app.css` comes last and holds only the four regions the package does not own.
import '@void/ui/tokens.css';
import '@void/ui/fonts.css';
import '@void/ui/styles.css';
import './local/app.css';

import { App } from './App';

const root = document.getElementById('root');
if (!root) throw new Error('#root is missing from index.html');

createRoot(root).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
