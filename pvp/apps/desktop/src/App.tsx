/**
 * The shell — §7 of `design/quiet-cell-system.md`, and nothing else.
 *
 *   1600 × 980, radius 20. A navbar (Play · Mods · Cosmetics · Servers · Friends) with
 *   search and the profile chip, then **one** content panel inset 32,80 sized
 *   1536 × 768 radius 18, then a dock band below it holding the loadout selector, the
 *   version selector, the enabled readout and Launch. Every launcher screen uses that
 *   identical shell.
 *
 * So this file arranges exactly three bands and puts the screen in the middle one. The
 * hero art, the scrim and the recessed-canvas inset the earlier pass carried are gone:
 * the system is flat, and the fill steps (shell → ground → card → raised) carry the
 * depth that the gradient and the shadow used to.
 *
 * Mods has one sub-route — a mod's setup page. There is no router; a screen is a value
 * in `stores/ui`, and the sub-route is one more value beside it, so the swap happens
 * here rather than inside `ModsScreen`.
 *
 * The root would normally carry `v-app`, which is where `@void/ui`'s reset and type
 * ramp live. It does not, for the reason spelled out at the top of `local/app.css`:
 * the `.v-app button` half of that reset outranks the package's own component
 * backgrounds. `local/app.css` carries the same reset at zero specificity until that is
 * fixed upstream.
 */

import { useEffect } from 'react';

import { Dock } from './features/Dock';
import { CommandPalette } from './features/CommandPalette';
import { LaunchError, LogDrawer, SessionSummary } from './features/LogDrawer';
import { TopNav } from './features/TopNav';
import { IS_TAURI } from './local/tauri';
import { CosmeticsScreen } from './screens/Cosmetics';
import { FriendsScreen } from './screens/Friends';
import { ModSetupScreen } from './screens/ModSetup';
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
  const modSetup = useUi((s) => s.modSetup);
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

  const Screen = screen === 'mods' && modSetup ? ModSetupScreen : SCREEN_COMPONENTS[screen];

  return (
    <div className="shell" data-phase={phase} data-screen={screen}>
      <TopNav />

      <main className="panel" data-screen={screen}>
        <Screen />
      </main>

      <div className="dockband">
        <Dock />
      </div>

      <div className="banners">
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

      <LogDrawer />
      <CommandPalette />
      <SettingsPanel />
    </div>
  );
}
