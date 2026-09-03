/**
 * The canvas backdrop art, **for the browser preview only**.
 *
 * The frames put a rendered Minecraft still behind the recessed canvas. Shipping one is
 * an asset decision the launcher does not own yet (there is no licenced render in the
 * repository, and `design/` is read-only reference material that nothing may import at
 * build time), so the app itself draws a gradient placeholder — see `.canvas__art` in
 * `local/app.css` and the TODO there.
 *
 * For a visual-QA pass that means the biggest region of every frame would differ for a
 * reason that has nothing to do with the components. So in the preview — and *only*
 * there — the canvas shows the design frame itself, cropped to the canvas rectangle by
 * background-position. `vite.config.ts` aliases `@dev/backdrops` to this file when
 * `TAURI_ENV_PLATFORM` is unset and to `backdrops.none.ts` when it is set, which is the
 * same mechanism that swaps in the mocked `@tauri-apps/api`: the alias is the whole
 * thing, so this cannot survive into a shipped bundle.
 *
 * The frames are composites, so the `--scrim-launcher` layer is already baked into
 * them; `App` drops its own scrim whenever a design backdrop is in use.
 */

import cosmetics from '../../../../design/screens/Launcher-Cosmetics.png?url';
import friends from '../../../../design/screens/Launcher-Friends.png?url';
import mods from '../../../../design/screens/Launcher-Mods.png?url';
import play from '../../../../design/screens/Launcher-Play.png?url';
import servers from '../../../../design/screens/Launcher-Servers.png?url';

import type { Screen } from '../stores/ui';

/** The frame each screen sits on, or `{}` in a real build. */
export const BACKDROPS: Partial<Record<Screen, string>> = {
  play,
  mods,
  cosmetics,
  servers,
  friends,
};
