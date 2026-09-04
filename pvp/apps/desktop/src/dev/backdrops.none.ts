/**
 * The shipped half of the `@dev/backdrops` alias: no art at all.
 *
 * `vite.config.ts` resolves `@dev/backdrops` here whenever Tauri is driving the build,
 * so a real launcher bundle contains none of `design/`'s PNGs and falls through to the
 * gradient placeholder in `local/app.css`. See `backdrops.ts` for the other half.
 */

import type { Screen } from '../stores/ui';

/** Empty by construction — the app draws its gradient placeholder instead. */
export const BACKDROPS: Partial<Record<Screen, string>> = {};
