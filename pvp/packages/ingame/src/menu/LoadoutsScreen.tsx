/**
 * Overlay — Loadouts · frame `244:1130`.
 *
 * "A loadout is which mods are on, their settings and HUD layout." — the
 * definition line is on the frame because the model is the product. Switching is
 * instant: `void.switchLoadout(id)` writes every actuator field and re-renders
 * the HUD in under a frame (§8.2), and the `loadout` event that follows is what
 * updates this screen.
 */

import { isModOn, modsOnCount, useVoidStore } from '@/store/store';
import { MOD_ORDER, MOD_REGISTRY } from '@/registry';
import type { Loadout, ModId } from '@/bridge/protocol';
import { Badge, Button } from '@/ui';
import { Icon, type IconName } from '@/icons/Icon';
import { Panel } from './Panel';
import { playedTime } from '@/hud/format';

export const LOADOUTS_FOOTER =
  'Switching applies instantly   ·   settings and HUD layout are per loadout   ·   L cycles loadouts in game';

/** Chips the card lists before it collapses the rest into `+ N more`. */
const CHIP_BUDGET = 6;

/** Loadout `icon` names resolved against the shared icon set (loadout.json). */
const LOADOUT_ICONS: Record<string, IconName> = {
  sword: 'sword',
  box: 'box',
  bed: 'box',
  heart: 'heart',
  shield: 'shield',
  crosshair: 'crosshair',
};

export function LoadoutCard({ loadout, active }: { loadout: Loadout; active: boolean }) {
  const switchLoadout = useVoidStore((s) => s.switchLoadout);
  const on = MOD_ORDER.filter((id) => isModOn(loadout, id));
  const shown = on.slice(0, CHIP_BUDGET);
  const more = modsOnCount(loadout) - shown.length;
  const fps = loadout.stats?.fps_avg ?? 0;
  const played = loadout.stats?.played_ms ?? 0;

  return (
    <div className={`lcard${active ? ' lcard--active' : ''}`}>
      <div className="lcard__head">
        <span className={`lcard__icon${active ? ' lcard__icon--active' : ''}`}>
          <Icon name={LOADOUT_ICONS[loadout.icon] ?? 'box'} size={22} />
        </span>
        <span className="lcard__names">
          <span className="lcard__title">{loadout.name}</span>
          <span className="lcard__meta">
            {[
              `${modsOnCount(loadout)} mods on`,
              serverLabel(loadout.server),
              loadout.mc,
            ]
              .filter(Boolean)
              .join('   ·   ')}
          </span>
        </span>
        {active && <Badge>Active</Badge>}
      </div>

      <div className="lcard__cap">Includes</div>
      <div className="lcard__chips">
        {shown.map((id: ModId) => (
          <span className="lcard__chip" key={id}>
            <span className="lcard__dot" />
            {MOD_REGISTRY[id].label}
          </span>
        ))}
      </div>
      {more > 0 && <div className="lcard__more">+ {more} more</div>}

      <div className="lcard__spacer" />

      <div className="lcard__stats">
        <span className="lcard__stat">
          <span className="lcard__stat-value">{fps > 0 ? Math.round(fps) : '—'}</span>
          <span className="lcard__stat-unit">fps avg</span>
        </span>
        <span className="lcard__stat">
          <span className="lcard__stat-value">{played > 0 ? playedTime(played) : '—'}</span>
          <span className="lcard__stat-unit">played</span>
        </span>
      </div>

      {active ? (
        <Button variant="raised" icon="check" iconSize={13} full disabled>
          Active
        </Button>
      ) : (
        <Button variant="accent" full onClick={() => switchLoadout(loadout.id)}>
          Switch to {loadout.name}
        </Button>
      )}
    </div>
  );
}

export function LoadoutsScreen() {
  const loadout = useVoidStore((s) => s.loadout);
  const library = useVoidStore((s) => s.library);
  const closeMenu = useVoidStore((s) => s.closeMenu);
  const cards = library.length > 0 ? library : loadout ? [loadout] : [];

  return (
    <Panel
      title="Loadouts"
      subtitle="A loadout is which mods are on, their settings and HUD layout."
      controls={
        <Button
          variant="raised"
          icon="plus"
          iconSize={13}
          title="Create a loadout from the current state"
        >
          New loadout
        </Button>
      }
      footer={LOADOUTS_FOOTER}
      onClose={closeMenu}
    >
      <div className="loadouts">
        {cards.map((entry) => (
          <LoadoutCard key={entry.id} loadout={entry} active={entry.id === loadout?.id} />
        ))}
      </div>
    </Panel>
  );
}

function serverLabel(server: string | null | undefined): string {
  if (!server) return '';
  return server.charAt(0).toUpperCase() + server.slice(1);
}
