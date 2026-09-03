/**
 * The dock of `244:66`: PlayerChip · LoadoutPicker · VersionPicker · LaunchButton ·
 * FriendsOnline · settings gear.
 *
 * Every part is `@void/ui`'s. The two launcher-only additions are the dropdown the
 * pills open (`./Menu`) and the download progress the CTA shows while `prepare` runs —
 * a `--v-progress` width on the button's own `::before`, because the frames have no
 * "downloading 3,000 assets" state and this one is real.
 */

import {
  Divider,
  Dock as DockBar,
  FriendsOnline,
  IconButton,
  LaunchButton,
  LoadoutPicker,
  PlayerChip,
  resolveLoadoutIcon,
  VersionPicker,
} from '@void/ui';
import { useEffect } from 'react';

import { Menu } from './Menu';
import { formatBytes, stepLabel, useLaunch } from '../stores/launch';
import { useLoadouts } from '../stores/loadouts';
import { useSession } from '../stores/session';
import { useUi } from '../stores/ui';

/** The three heads the frame draws. Static until Friends has a backend (§16.2). */
const FRIEND_HEADS = [{ name: 'marrow' }, { name: 'pilot_ash' }, { name: 'nine' }];

/** 1.8.9 is the only version this client targets (§15); the rest are shown as coming. */
const VERSIONS = [
  { id: '1.8.9', label: '1.8.9' },
  { id: '1.12.2', label: '1.12.2', disabled: true, hint: 'not yet' },
  { id: '1.21.4', label: '1.21.4', disabled: true, hint: 'not yet' },
];

function DockLaunchButton() {
  const phase = useLaunch((s) => s.phase);
  const progress = useLaunch((s) => s.progress);
  const start = useLaunch((s) => s.start);
  const kill = useLaunch((s) => s.kill);
  const active = useLoadouts((s) => s.active);
  const account = useSession((s) => s.account);
  const openSettings = useUi((s) => s.openSettings);

  const canLaunch = Boolean(active) && phase === 'idle';

  // ⌘↵ / Ctrl+↵ launches — the kbd chip inside the button is a promise, so honour it.
  useEffect(() => {
    const onKey = (event: KeyboardEvent): void => {
      if (event.key === 'Enter' && (event.metaKey || event.ctrlKey)) {
        event.preventDefault();
        if (canLaunch && active) void start(active.id);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [canLaunch, active, start]);

  if (phase === 'running') {
    return (
      <LaunchButton
        state="running"
        label="Playing"
        title="Stop the game"
        onClick={() => void kill()}
      />
    );
  }

  if (phase === 'preparing' || phase === 'launching') {
    const pct = progress && progress.total > 0 ? (progress.done / progress.total) * 100 : 0;
    return (
      <LaunchButton
        state="launching"
        className="dock__launch"
        style={{ '--v-progress': `${pct}%` } as React.CSSProperties}
        disabled
        label={
          phase === 'launching' ? (
            'Launching…'
          ) : (
            <span className="dock__launch-text">
              {stepLabel(progress?.step ?? 'manifest')}
              {progress ? (
                <span className="dock__launch-rate">
                  {Math.round(pct)}% · {formatBytes(progress.bytes_per_sec)}/s
                </span>
              ) : null}
            </span>
          )
        }
      />
    );
  }

  if (!account) {
    return <LaunchButton label="Sign in to launch" kbd={null} onClick={openSettings} />;
  }

  return (
    <LaunchButton disabled={!canLaunch} onClick={() => active && void start(active.id)} />
  );
}

export function Dock() {
  const account = useSession((s) => s.account);
  const { active, library, switchTo } = useLoadouts();
  const openSettings = useUi((s) => s.openSettings);

  return (
    <DockBar>
      <PlayerChip
        name={account?.name ?? 'Not signed in'}
        avatarSrc={account?.skin_url ?? undefined}
        // The frame prints `Lvl 42`; there is no level, so the second line says which
        // kind of account this is. Kept to one word so the chip keeps the frame's width.
        level={account ? (account.kind === 'offline' ? 'Offline' : 'Microsoft') : 'Signed out'}
      />

      <Divider />

      <Menu
        items={library.map((l) => ({ id: l.id, label: l.name }))}
        current={active?.name}
        onSelect={(id) => void switchTo(id)}
        trigger={(open, toggle) => (
          <LoadoutPicker
            value={active?.name ?? '—'}
            icon={resolveLoadoutIcon(active?.icon ?? 'sword')}
            open={open}
            onClick={toggle}
            aria-label="Loadout"
          />
        )}
      />

      <Menu
        items={VERSIONS}
        current="1.8.9"
        onSelect={() => {
          /* 1.8.9 is the only version this client targets (§15). */
        }}
        trigger={(open, toggle) => (
          <VersionPicker
            value="1.8.9"
            open={open}
            onClick={toggle}
            aria-label="Minecraft version"
          />
        )}
      />

      <DockLaunchButton />

      <Divider />

      <FriendsOnline friends={FRIEND_HEADS} total={3} />

      <IconButton icon="settings" size="dock" label="Settings" onClick={openSettings} />
    </DockBar>
  );
}
