/**
 * The shell — Figma v1 Ship (`52:2`), and nothing else.
 *
 *   1600 × 980, radius 20, minimum 1280 × 800 (rule 10). A 72px navbar; one content panel
 *   inset 32 at y 80, 1536 × 768, radius 32; the play bar centred over the panel's bottom
 *   edge, identical on every screen (rule 2). The Friends drawer and the Store's item sheet
 *   sit over the panel's right edge.
 *
 * There is no router: a screen is a value in `stores/ui`, and Edit mods' one sub-route —
 * a mod's setup page — is one more value beside it.
 *
 * The root does not carry `v-app`, for the reason spelled out at the top of
 * `local/app.css`: the `.v-app button` half of `@void/ui`'s reset outranks the package's
 * own component backgrounds.
 */

import { useEffect } from 'react';

import chevronRight from './assets/icons/chevron-right.svg';
import { CommandPalette } from './features/CommandPalette';
import { FriendsDrawer } from './features/FriendsDrawer';
import { LaunchError, LogDrawer } from './features/LogDrawer';
import { PlayBar } from './features/PlayBar';
import { TopNav } from './features/TopNav';
import { IS_TAURI } from './local/tauri';
import { ComingSoonScreen } from './screens/ComingSoon';
import { HomeScreen, WHATS_NEW } from './screens/Home';
import { ModSetupScreen } from './screens/ModSetup';
import { ModsScreen } from './screens/Mods';
import { SettingsPanel } from './screens/Settings';
import { StoreScreen } from './screens/Store';
import { useCosmetics } from './stores/cosmetics';
import { useFriends } from './stores/friends';
import { useOnlineStatus, wireOnlineEvents } from './stores/online';
import { useLaunch, wireLaunchEvents } from './stores/launch';
import { useLoadouts, wireLoadoutEvents } from './stores/loadouts';
import { useSession, wireSessionEvents } from './stores/session';
import { isSoon, useUi } from './stores/ui';

/** Edit mods is a page of PvP: the grid, with the way back above it. */
function EditMods() {
  const go = useUi((s) => s.go);
  const modSetup = useUi((s) => s.modSetup);
  if (modSetup) return <ModSetupScreen />;
  return (
    <div className="editmods">
      <header className="editmods__head">
        <button type="button" className="ghostlink ghostlink--back" onClick={() => go('home')}>
          <img src={chevronRight} width={5} height={8} alt="" />
          PvP
        </button>
        <h1 className="display2">Edit mods</h1>
      </header>
      <ModsScreen />
    </div>
  );
}

function ReleaseNotes() {
  const open = useUi((s) => s.notesOpen);
  const setOpen = useUi((s) => s.setNotesOpen);
  useEffect(() => {
    if (!open) return undefined;
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false);
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, setOpen]);
  if (!open) return null;
  return (
    <div className="modalscrim" role="presentation" onMouseDown={() => setOpen(false)}>
      <div className="notes" role="dialog" aria-labelledby="notes-title" onMouseDown={(e) => e.stopPropagation()}>
        <p className="eyebrow2">Release notes</p>
        <h2 id="notes-title" className="notes__title">
          {WHATS_NEW.title}
        </h2>
        <ul className="notes__list">
          {WHATS_NEW.notes.map((n) => (
            <li key={n}>{n}</li>
          ))}
        </ul>
        <button type="button" className="btn2 btn2--secondary" onClick={() => setOpen(false)}>
          Close
        </button>
      </div>
    </div>
  );
}

export function App() {
  const screen = useUi((s) => s.screen);
  const hydrateSession = useSession((s) => s.hydrate);
  const hydrateLoadouts = useLoadouts((s) => s.hydrate);
  const hydrateFriends = useFriends((s) => s.hydrate);
  const hydrateCosmetics = useCosmetics((s) => s.hydrate);
  const hydrateOnline = useOnlineStatus((s) => s.hydrate);
  const phase = useLaunch((s) => s.phase);
  const material = useUi((s) => s.material);

  useEffect(() => {
    void hydrateSession();
    void hydrateLoadouts();
    void hydrateOnline();
    hydrateFriends();
    hydrateCosmetics();
  }, [hydrateSession, hydrateLoadouts, hydrateOnline, hydrateFriends, hydrateCosmetics]);

  useEffect(() => {
    // One subscription set for the whole app. Every store's events are wired here so
    // that a screen mounting or unmounting can never drop a `game:closed`.
    const pending = Promise.all([wireSessionEvents(), wireLoadoutEvents(), wireLaunchEvents(), wireOnlineEvents()]);
    return () => {
      void pending.then((unlisteners) => unlisteners.forEach((u) => u()));
    };
  }, []);

  let content;
  if (screen === 'home') content = <HomeScreen />;
  else if (screen === 'store') content = <StoreScreen />;
  else if (screen === 'mods') content = <EditMods />;
  else if (isSoon(screen)) content = <ComingSoonScreen which={screen} />;

  return (
    <div className="shell ship" data-phase={phase} data-screen={screen} data-material={material}>
      {/* The light the glass is made of: still at rest, tinted by the screen. */}
      <div className="ambient" aria-hidden="true">
        <i className="ambient__a" />
        <i className="ambient__b" />
        <i className="ambient__c" />
      </div>

      <TopNav />

      <main className="panel2" data-screen={screen}>
        <div className="screen-view">{content}</div>
      </main>

      <FriendsDrawer />

      <div className="playbar-dock">
        <PlayBar />
      </div>

      <div className="banners">
        <LaunchError />
        {!IS_TAURI ? (
          <div className="banner banner--preview" role="status">
            <span className="banner__text">
              Browser preview — `@tauri-apps/api` is mocked, so pings, launches, friends and the
              Store are fixtures. Run `pnpm tauri dev` for the real backend.
            </span>
          </div>
        ) : null}
      </div>

      <LogDrawer />
      <CommandPalette />
      <SettingsPanel />
      <ReleaseNotes />
    </div>
  );
}
