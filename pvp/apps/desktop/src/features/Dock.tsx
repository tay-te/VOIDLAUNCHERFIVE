/**
 * The dock of `244:66`: PlayerChip · LoadoutPicker · VersionPicker · LaunchButton ·
 * FriendsOnline · settings gear.
 *
 * It sits on every screen, not just Play — the Figma shows it under the panel on Mods,
 * Cosmetics, Servers and Friends too.
 */

import { useEffect } from 'react';

import { Avatar, Divider, IconButton, Kbd, Menu } from '../components';
import { GearIcon, PlayIcon } from '../components/icons';
import { LOADOUT_ICONS, SwordIcon } from '../components/icons';
import { formatBytes, stepLabel, useLaunch } from '../stores/launch';
import { useLoadouts } from '../stores/loadouts';
import { useSession } from '../stores/session';
import { useUi } from '../stores/ui';

/** The three overlapped heads + "3 online". Static until Friends has a backend. */
function FriendsOnline({ count = 3 }: { count?: number }) {
  const names = ['marrow', 'pilot_ash', 'nine'];
  return (
    <div className="friends-online">
      <div className="friends-online__heads">
        {names.slice(0, 3).map((n, i) => (
          <span key={n} className="friends-online__head" style={{ left: i * 24 }}>
            <Avatar name={n} size={32} />
          </span>
        ))}
      </div>
      <span className="friends-online__label">{count} online</span>
    </div>
  );
}

function LaunchButton() {
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
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) {
        e.preventDefault();
        if (canLaunch && active) void start(active.id);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [canLaunch, active, start]);

  if (phase === 'running') {
    return (
      <button type="button" className="cta cta--running" onClick={() => void kill()}>
        <span className="cta__pulse" />
        Running · Force quit
      </button>
    );
  }

  if (phase === 'preparing' || phase === 'launching') {
    const pct = progress && progress.total > 0 ? (progress.done / progress.total) * 100 : 0;
    return (
      <button type="button" className="cta cta--busy" disabled>
        <span className="cta__progress" style={{ width: `${pct}%` }} />
        <span className="cta__busy-text">
          {phase === 'launching' ? 'Launching…' : stepLabel(progress?.step ?? 'manifest')}
          {phase === 'preparing' && progress ? (
            <span className="cta__rate">
              {Math.round(pct)}% · {formatBytes(progress.bytes_per_sec)}/s
            </span>
          ) : null}
        </span>
      </button>
    );
  }

  if (!account) {
    return (
      <button type="button" className="cta cta--signin" onClick={openSettings}>
        <PlayIcon size={16} />
        Sign in to launch
      </button>
    );
  }

  return (
    <button
      type="button"
      className="cta"
      disabled={!canLaunch}
      onClick={() => active && void start(active.id)}
    >
      <PlayIcon size={16} />
      <span className="cta__label">Launch</span>
      <Kbd tone="accent">⌘↵</Kbd>
    </button>
  );
}

export function Dock() {
  const account = useSession((s) => s.account);
  const { active, library, switchTo } = useLoadouts();
  const openSettings = useUi((s) => s.openSettings);

  const LoadoutIcon = LOADOUT_ICONS[active?.icon ?? 'sword'] ?? SwordIcon;

  return (
    <div className="dock">
      <div className="dock__identity">
        <Avatar name={account?.name ?? 'Guest'} src={account?.skin_url} size={44} />
        <span className="dock__identity-text">
          <span className="dock__name">{account?.name ?? 'Not signed in'}</span>
          <span className="dock__level">
            {account
              ? account.kind === 'offline'
                ? 'Offline account'
                : 'Microsoft account'
              : 'Sign in to launch'}
          </span>
        </span>
      </div>

      <Divider />

      <Menu
        label="Loadout"
        eyebrow="LOADOUT"
        value={active?.name ?? '—'}
        icon={LoadoutIcon}
        items={library.map((l) => ({ id: l.id, label: l.name }))}
        onSelect={(id) => void switchTo(id)}
      />

      <Menu
        label="Minecraft version"
        eyebrow="VERSION"
        value="1.8.9"
        items={[
          { id: '1.8.9', label: '1.8.9' },
          { id: '1.12.2', label: '1.12.2', disabled: true, hint: 'not yet' },
          { id: '1.21.4', label: '1.21.4', disabled: true, hint: 'not yet' },
        ]}
        onSelect={() => {
          /* 1.8.9 is the only version this client targets (§15). */
        }}
      />

      <LaunchButton />

      <Divider />

      <FriendsOnline />

      <IconButton icon={GearIcon} size={44} glyph={16} label="Settings" onClick={openSettings} />
    </div>
  );
}
