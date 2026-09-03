/**
 * The shell: chrome band, recessed canvas, the screen, the dock.
 *
 * The frame geometry is the Figma's — 62 px chrome, a 14 px inset canvas with the
 * hero art behind a scrim, panels floating on the canvas and the dock at the bottom —
 * but expressed as a flex layout rather than absolute pixels, because the window is
 * resizable down to 1100 × 700 and the frames are a single 1300 × 820 size.
 *
 * Everything inside the shell is either a component from `@void/ui` or one of the four
 * launcher-only regions this file arranges — chrome band, canvas, hero, dock.
 *
 * The root would normally carry `v-app`, which is where that package's reset and type
 * ramp live. It does not, for one reason spelled out at the top of `local/app.css`:
 * the `.v-app button` half of that reset outranks the package's own component
 * backgrounds, so `v-app` makes every button in it transparent. `local/app.css`
 * carries the same reset at zero specificity until that is fixed upstream.
 */

import { useEffect } from 'react';

import { BACKDROPS } from '@dev/backdrops';

import { Dock } from './features/Dock';
import { CommandPalette } from './features/CommandPalette';
import { LaunchError, LogDrawer, SessionSummary } from './features/LogDrawer';
import { TopNav } from './features/TopNav';
import { IS_TAURI } from './local/tauri';
import { CosmeticsScreen } from './screens/Cosmetics';
import { FriendsScreen } from './screens/Friends';
import { ModsScreen } from './screens/Mods';
import { PlayScreen } from './screens/Play';
import { ServersScreen } from './screens/Servers';
import { SettingsPanel } from './screens/Settings';
import { useLaunch, wireLaunchEvents } from './stores/launch';
import { useLoadouts, wireLoadoutEvents } from './stores/loadouts';
import { useSession, wireSessionEvents } from './stores/session';
import { useUi } from './stores/ui';

const SCREEN_COMPONENTS = {
  play: PlayScreen,
  mods: ModsScreen,
  cosmetics: CosmeticsScreen,
  servers: ServersScreen,
  friends: FriendsScreen,
} as const;

export function App() {
  const screen = useUi((s) => s.screen);
  const hydrateSession = useSession((s) => s.hydrate);
  const hydrateLoadouts = useLoadouts((s) => s.hydrate);
  const phase = useLaunch((s) => s.phase);

  useEffect(() => {
    void hydrateSession();
    void hydrateLoadouts();
  }, [hydrateSession, hydrateLoadouts]);

  useEffect(() => {
    // One subscription set for the whole app. Every store's events are wired here so
    // that a screen mounting or unmounting can never drop a `game:closed`.
    const pending = Promise.all([wireSessionEvents(), wireLoadoutEvents(), wireLaunchEvents()]);
    return () => {
      void pending.then((unlisteners) => unlisteners.forEach((u) => u()));
    };
  }, []);

  const Screen = SCREEN_COMPONENTS[screen];
  const isPlay = screen === 'play';

  // In the browser preview the canvas shows the design frame itself, cropped to the
  // canvas rectangle; the frames are composites, so their scrim is already baked in and
  // ours would double-darken. A real build gets an empty map — see src/dev/backdrops.ts.
  const backdrop = BACKDROPS[screen];

  return (
    <div className="shell" data-phase={phase}>
      <TopNav />

      <main className="canvas v-noise">
        <div
          className={`canvas__art${backdrop ? ' canvas__art--design' : ''}`}
          style={backdrop ? { backgroundImage: `url(${backdrop})` } : undefined}
          aria-hidden="true"
        />
        {backdrop ? null : <div className="canvas__scrim" aria-hidden="true" />}

        <div className={`canvas__content${isPlay ? ' is-play' : ''}`}>
          <Screen />
        </div>

        <div className="canvas__dock">
          <Dock />
        </div>

        <div className="canvas__banners">
          <LaunchError />
          <SessionSummary />
          {!IS_TAURI ? (
            <div className="banner banner--preview" role="status">
              <span className="banner__text">
                Browser preview — `@tauri-apps/api` is mocked, so pings, launches and sign-in are
                fixtures. Run `pnpm tauri dev` for the real backend.
              </span>
            </div>
          ) : null}
        </div>
      </main>

      <LogDrawer />
      <CommandPalette />
      <SettingsPanel />
    </div>
  );
}
