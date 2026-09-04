/**
 * Two layers into one Ultralight view (§6.2): the HUD, always mounted and never
 * interactive, and the menu, mounted only while `VoidMenuScreen` is open.
 */

import { useVoidStore } from '@/store/store';
import { HudLayer } from '@/hud/HudLayer';
import { MenuLayer } from '@/menu/MenuLayer';
import { isDebugBridge } from '@/bridge/connect';

export interface AppProps {
  /**
   * Browser harness: render at the authored 1300 × 820 frame size and put the
   * matching Figma export behind the UI. Never true in game.
   */
  debugFrame?: boolean;
  /** File name under `design/screens` to show behind the UI in dev. */
  backdrop?: string;
}

export function App({ debugFrame, backdrop }: AppProps) {
  const menuOpen = useVoidStore((s) => s.menuOpen);
  const editing = useVoidStore((s) => s.route.name === 'hud-editor');

  return (
    <div className={`v-app void-app${debugFrame ? ' void-app--debug' : ''}`}>
      {debugFrame && backdrop && (
        <div
          className="void-debug-backdrop"
          style={{ backgroundImage: `url(/__design/${backdrop})` }}
        />
      )}

      {/* The editor renders its own copy of the HUD, with drag handlers. */}
      {!(menuOpen && editing) && <HudLayer dimmed={menuOpen} />}

      {menuOpen && <MenuLayer />}

      {isDebugBridge() && <span className="void-debug-badge">debug</span>}
    </div>
  );
}
