import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

// TODO(integrate): both of these come from `@void/ui` once it has a build:
//   import '@void/ui/tokens.css';
//   import '@void/ui/fonts.css';
import './local/tokens.css';
import './local/app.css';

import { App } from './App';

const root = document.getElementById('root');
if (!root) throw new Error('#root is missing from index.html');

createRoot(root).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
