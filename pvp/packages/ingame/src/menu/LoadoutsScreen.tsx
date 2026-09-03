/**
 * Overlay — Loadouts · frame `244:1130`.
 *
 * "A loadout is which mods are on, their settings and HUD layout." — the
 * definition line is on the frame because the model *is* the product.
 *
 * Switching is instant: `void.switchLoadout(id)` writes every actuator field and
 * re-renders the HUD in under a frame (§8.2), and the `loadout` event that
 * follows is what updates this screen. There is no optimistic state here.
 */

import { Button, LoadoutCard, Panel } from '@/ui';
import { enabledMods, enabledModCount, type Loadout } from '@/bridge/protocol';
import { modLabel } from '@/registry';
import { useVoidStore } from '@/store/store';
import { playedTime } from '@/hud/format';

export const LOADOUTS_FOOTER =
  'Switching applies instantly   ·   settings and HUD layout are per loadout   ·   L cycles loadouts in game';

/** Chips a card lists before it collapses the rest into `+ N more`. */
const CHIP_BUDGET = 6;

/** The card's mono meta line: `24 mods on   ·   Hypixel   ·   1.8.9`. */
export function loadoutMeta(loadout: Loadout): string {
  return [
    `${enabledModCount(loadout)} mods on`,
    loadout.server ? loadout.server.charAt(0).toUpperCase() + loadout.server.slice(1) : null,
    loadout.mc,
  ]
    .filter(Boolean)
    .join('   ·   ');
}

export function LoadoutsScreen() {
  const active = useVoidStore((s) => s.loadout);
  const library = useVoidStore((s) => s.library);
  const switchLoadout = useVoidStore((s) => s.switchLoadout);
  const closeMenu = useVoidStore((s) => s.closeMenu);

  const cards = library.length > 0 ? library : active ? [active] : [];

  return (
    <div className="panel-wrap">
      <Panel
        surface="overlay"
        animate
        title="Loadouts"
        className="loadouts-panel"
        subtitle="A loadout is which mods are on, their settings and HUD layout."
        onClose={closeMenu}
        footer={LOADOUTS_FOOTER}
        headerRight={
          <>
            <span className="v-spacer" />
            <Button
              variant="raised"
              icon="plus"
              title="Create a loadout from the current state"
            >
              New loadout
            </Button>
          </>
        }
      >
        <div className="loadouts">
          {cards.map((loadout) => {
            const on = enabledMods(loadout);
            const shown = on.slice(0, CHIP_BUDGET);
            const fps = loadout.stats?.fps_avg ?? 0;
            const played = loadout.stats?.played_ms ?? 0;
            return (
              <LoadoutCard
                key={loadout.id}
                name={loadout.name}
                icon={loadout.icon}
                meta={loadoutMeta(loadout)}
                active={loadout.id === active?.id}
                includes={shown.map((id) => ({ label: modLabel(id) }))}
                moreCount={on.length - shown.length}
                stats={[
                  { value: fps > 0 ? Math.round(fps) : '—', unit: 'fps avg' },
                  { value: played > 0 ? playedTime(played) : '—', unit: 'played' },
                ]}
                onSwitch={() => switchLoadout(loadout.id)}
              />
            );
          })}
        </div>
      </Panel>
    </div>
  );
}
