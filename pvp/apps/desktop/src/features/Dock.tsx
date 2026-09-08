/**
 * The dock band — the strip under the content panel (§7) — and its one exception.
 *
 * There are **two** docks, deliberately, because the canonical frames draw two:
 *
 *   `Launcher-Mods.png` / `Launcher-Setup.png` — a full-width band across the bottom of
 *   the window, on the panel's own content edges: loadout pill at x 64, version pill,
 *   the enabled readout with its 24-cell meter, and Launch ending at x 1536.
 *
 *   `Launcher-Play.png` — a floating 852 × 92 card centred on the window at y 800, which
 *   *overlaps* the stage's bottom edge and hangs 44px below it. It carries the same two
 *   selectors and Launch, plus the three most recent loadouts, the friends-online
 *   readout and a settings well, separated by dotted cell rules.
 *
 * `PanelDock` and `PlayDock` below are those two. They share `LaunchControl` and the
 * `Picker`, so the launch state machine and the selector behaviour exist once.
 *
 * Flat, like everything else: no shadow, no blur. The only colour on either is the
 * launch button's `--text-primary` fill and the readiness dot when it is actually good.
 *
 * The one thing the frames have no state for is real: while `prepare` runs, thousands
 * of asset objects are downloading, so the CTA carries a progress fill behind its label
 * (`--v-progress`) rather than a second control.
 */

import { Icon } from '@void/ui';
import { useEffect, type CSSProperties, type ReactElement } from 'react';

import { Menu } from './Menu';
import { onlineCount } from '../local/friends';
import { hypixelReady } from '../local/hypixelReady';
import { MOD_GRID_ORDER, enabledCount } from '../local/registry';
import { formatBytes, stepLabel, useLaunch } from '../stores/launch';
import { useLoadouts } from '../stores/loadouts';
import { useSession } from '../stores/session';
import { useUi, type Screen } from '../stores/ui';

/**
 * The footer hint, per screen. Every frame carries one line of 9px caps under the dock
 * saying what the screen in front of it does; it is the only copy on the band.
 */
const HINTS: Record<Screen, string> = {
  play: 'Enter launches · ⌘K search · ⌘, settings',
  mods: 'Select a mod to open its setup page · changes save to the active loadout',
  cosmetics: '⌘K search · changes save to the active loadout',
  servers: 'Enter joins the selected server · ⌘K search',
  friends: '⌘K search · ⌘, settings',
};

const SETUP_HINT =
  'Drag the module on the screen to place it · changes save to the active loadout';

/** 1.8.9 is the only version this client targets (§15); the rest are shown as coming. */
const VERSIONS = [
  { id: '1.8.9', label: '1.8.9' },
  { id: '1.12.2', label: '1.12.2', disabled: true, hint: 'not yet' },
  { id: '1.21.4', label: '1.21.4', disabled: true, hint: 'not yet' },
];

/** The play mark: three bars stepping down, which is how the frames draw a triangle. */
function PlayMark() {
  return (
    <span className="playmark" aria-hidden="true">
      <i />
      <i />
      <i />
    </span>
  );
}

function Picker({
  caption,
  value,
  open,
  onClick,
  label,
}: {
  caption: string;
  value: string;
  open: boolean;
  onClick: () => void;
  label: string;
}) {
  return (
    <button
      type="button"
      className={`picker${open ? ' is-open' : ''}`}
      aria-label={label}
      aria-expanded={open}
      onClick={onClick}
    >
      <span className="picker__text">
        <span className="eyebrow">{caption}</span>
        <span className="picker__value">{value}</span>
      </span>
      <Icon name="chevron-down" size={12} />
    </button>
  );
}

/** The loadout selector, in whichever dock is asking. */
function LoadoutPicker({ className }: { className?: string }) {
  const { active, library, switchTo } = useLoadouts();
  return (
    <Menu
      className={className}
      items={library.map((l) => ({ id: l.id, label: l.name }))}
      current={active?.name}
      onSelect={(id) => void switchTo(id)}
      trigger={(open, toggle) => (
        <Picker
          caption="Loadout"
          value={active?.name ?? '—'}
          open={open}
          onClick={toggle}
          label="Loadout"
        />
      )}
    />
  );
}

function VersionPicker({ className }: { className?: string }) {
  return (
    <Menu
      className={className}
      items={VERSIONS}
      current="1.8.9"
      onSelect={() => {
        /* 1.8.9 is the only version this client targets (§15). */
      }}
      trigger={(open, toggle) => (
        <Picker
          caption="Version"
          value="1.8.9"
          open={open}
          onClick={toggle}
          label="Minecraft version"
        />
      )}
    />
  );
}

function LaunchControl({ mark = false }: { mark?: boolean }) {
  const phase = useLaunch((s) => s.phase);
  const progress = useLaunch((s) => s.progress);
  const start = useLaunch((s) => s.start);
  const kill = useLaunch((s) => s.kill);
  const active = useLoadouts((s) => s.active);
  const account = useSession((s) => s.account);
  const openSettings = useUi((s) => s.openSettings);

  const canLaunch = Boolean(active) && phase === 'idle';

  /*
    Enter launches, which is what the footer hint on every frame promises, and ⌘↵ does
    too so the chord still works from inside a field. Plain Enter is only honoured when
    nothing focusable has the keyboard — otherwise it would fire while someone is typing
    a server address or confirming a menu row.
  */
  useEffect(() => {
    const onKey = (event: KeyboardEvent): void => {
      if (event.key !== 'Enter') return;
      const chord = event.metaKey || event.ctrlKey;
      const bare = document.activeElement === null || document.activeElement === document.body;
      if (!chord && !bare) return;
      event.preventDefault();
      if (canLaunch && active) void start(active.id);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [canLaunch, active, start]);

  if (phase === 'running') {
    return (
      <button
        type="button"
        className="launch launch--running"
        title="Stop the game"
        onClick={() => void kill()}
      >
        Playing
      </button>
    );
  }

  if (phase === 'preparing' || phase === 'launching') {
    const pct = progress && progress.total > 0 ? (progress.done / progress.total) * 100 : 0;
    return (
      <button
        type="button"
        className="launch launch--working"
        style={{ '--v-progress': `${pct}%` } as CSSProperties}
        disabled
      >
        <span className="launch__label">
          {phase === 'launching' ? 'Launching…' : stepLabel(progress?.step ?? 'manifest')}
        </span>
        {phase === 'preparing' && progress ? (
          <span className="launch__rate tnum">
            {Math.round(pct)}% · {formatBytes(progress.bytes_per_sec)}/s
          </span>
        ) : null}
      </button>
    );
  }

  if (!account) {
    return (
      <button type="button" className="launch" onClick={openSettings}>
        Sign in to launch
      </button>
    );
  }

  return (
    <button
      type="button"
      className="launch"
      disabled={!canLaunch}
      onClick={() => active && void start(active.id)}
    >
      {mark ? <PlayMark /> : null}
      <span className="launch__label">Launch</span>
      <span className="launch__kbd">Enter</span>
    </button>
  );
}

/** The band every panel screen draws: two pills, the readout, Launch on the right. */
function PanelDock(): ReactElement {
  const active = useLoadouts((s) => s.active);
  const on = active ? enabledCount(active) : 0;
  const readiness = active ? hypixelReady(active) : null;

  return (
    <div className="dock">
      <LoadoutPicker />
      <VersionPicker />

      {/* The readout is a label over one cell per mod in the grid (§3). The filled run
          is the enabled count — the only live value on the band. */}
      <p className="dock__readout">
        <span className="dock__line">
          <span className="tnum">
            {on} of {MOD_GRID_ORDER.length} mods enabled
          </span>
          {readiness ? (
            <span className={`dock__ready${readiness.ready ? ' is-ok' : ''}`}>
              <span className={`dot${readiness.ready ? ' is-ok' : ''}`} aria-hidden="true" />
              {readiness.ready ? 'Hypixel-ready' : 'Review mods'}
            </span>
          ) : null}
        </span>
        <span className="dock__cells" aria-hidden="true">
          {MOD_GRID_ORDER.map((id, index) => (
            <span key={id} className={`dock__cell${index < on ? ' is-on' : ''}`} />
          ))}
        </span>
      </p>

      <LaunchControl mark />
    </div>
  );
}

/** A dotted vertical rule — four cells, because §3 says everything textural is a cell. */
function CellRule({ side }: { side: 'left' | 'right' }) {
  return (
    <span className={`playdock__rule playdock__rule--${side}`} aria-hidden="true">
      <i />
      <i />
    </span>
  );
}

/**
 * Play's floating card. Every child is placed at the frame's own coordinate inside the
 * 852 × 92 box (see `.playdock` in `local/app.css`) rather than flowed, because the
 * frame gives each one an absolute position and a chain of gaps would not say so.
 */
function PlayDock(): ReactElement {
  const { active, library, switchTo } = useLoadouts();
  const openSettings = useUi((s) => s.openSettings);
  const online = onlineCount();

  return (
    <div className="playdock">
      <LoadoutPicker className="playdock__loadout" />
      <CellRule side="left" />
      <VersionPicker className="playdock__version" />

      <LaunchControl mark />

      <CellRule side="right" />

      {/* The three most recent loadouts, as cells — one click back to any of them. */}
      <div className="playdock__recent">
        {library.slice(0, 3).map((loadout) => (
          <button
            key={loadout.id}
            type="button"
            className={loadout.id === active?.id ? 'is-on' : undefined}
            title={loadout.name}
            aria-label={`Switch to ${loadout.name}`}
            onClick={() => void switchTo(loadout.id)}
          >
            <span aria-hidden="true" />
          </button>
        ))}
      </div>

      <p className="playdock__online">
        <span className={`dot cell${online > 0 ? ' is-ok' : ''}`} aria-hidden="true" />
        <span className="tnum">{online} online</span>
      </p>

      <button
        type="button"
        className="playdock__gear"
        aria-label="Settings"
        title="Settings"
        onClick={openSettings}
      >
        <Icon name="settings" size={18} />
      </button>
    </div>
  );
}

export function Dock() {
  const screen = useUi((s) => s.screen);
  const modSetup = useUi((s) => s.modSetup);
  const hint = screen === 'mods' && modSetup ? SETUP_HINT : HINTS[screen];

  return (
    <>
      {screen === 'play' ? <PlayDock /> : <PanelDock />}
      <p className="dock__hint">{hint}</p>
    </>
  );
}
