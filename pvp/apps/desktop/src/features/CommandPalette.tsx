/**
 * "Ask VOID anything ⌘K" — the launcher-side command palette (`244:1900` is its
 * in-game twin).
 *
 * §16.4 leaves open whether this is a command palette or an LLM. It is a command
 * palette: everything it can do is something the launcher already does, and a search
 * box that sometimes answers questions and sometimes navigates is worse at both. If an
 * LLM lands later it can be another result group.
 *
 * Sources, in the order they rank: screens, loadouts, mods (jumps to Mods with that
 * mod selected), servers, actions.
 */

import { useEffect, useMemo, useRef, useState } from 'react';

import { Kbd } from '../components';
import {
  GearIcon,
  LayersIcon,
  PlayIcon,
  SearchIcon,
  ServerIcon,
  ShirtIcon,
  SwordIcon,
  TerminalIcon,
  UsersIcon,
} from '../components/icons';
import type { ComponentType } from 'react';
import { MOD_ICONS } from '../components/icons';
import { MOD_GRID_ORDER, MOD_REGISTRY, isOn } from '../local/registry';
import { useLaunch } from '../stores/launch';
import { useLoadouts } from '../stores/loadouts';
import { useServers } from '../stores/servers';
import { SCREENS, SCREEN_LABELS, useUi } from '../stores/ui';

interface Result {
  id: string;
  group: string;
  title: string;
  sub: string;
  icon: ComponentType<{ size?: number }>;
  keys?: string[];
  run: () => void;
}

const SCREEN_ICONS = {
  play: PlayIcon,
  mods: LayersIcon,
  cosmetics: ShirtIcon,
  servers: ServerIcon,
  friends: UsersIcon,
} as const;

/** Subsequence match — "swp" finds "Sword PvP" — with a simple relevance score. */
function score(query: string, text: string): number {
  if (!query) return 1;
  const q = query.toLowerCase();
  const t = text.toLowerCase();
  const exact = t.indexOf(q);
  if (exact === 0) return 1000;
  if (exact > 0) return 500 - exact;
  let qi = 0;
  let hits = 0;
  for (let i = 0; i < t.length && qi < q.length; i += 1) {
    if (t[i] === q[qi]) {
      qi += 1;
      hits += 1;
    }
  }
  return qi === q.length ? hits : 0;
}

export function CommandPalette() {
  const open = useUi((s) => s.paletteOpen);
  const close = useUi((s) => s.closePalette);
  const togglePalette = useUi((s) => s.togglePalette);
  const go = useUi((s) => s.go);
  const openSettings = useUi((s) => s.openSettings);
  const selectMod = useUi((s) => s.selectMod);
  const toggleLog = useUi((s) => s.toggleLog);

  const { library, active, switchTo, setMod } = useLoadouts();
  const servers = useServers((s) => s.servers);
  const selectServer = useServers((s) => s.select);
  const start = useLaunch((s) => s.start);
  const phase = useLaunch((s) => s.phase);

  const [query, setQuery] = useState('');
  const [cursor, setCursor] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);

  // ⌘K / Ctrl+K anywhere.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key.toLowerCase() === 'k' && (e.metaKey || e.ctrlKey)) {
        e.preventDefault();
        togglePalette();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [togglePalette]);

  useEffect(() => {
    if (open) {
      setQuery('');
      setCursor(0);
      // A frame, so the element exists before we reach for it.
      requestAnimationFrame(() => inputRef.current?.focus());
    }
  }, [open]);

  const results = useMemo<Result[]>(() => {
    const all: Result[] = [];

    for (const screen of SCREENS) {
      all.push({
        id: `screen:${screen}`,
        group: 'GO TO',
        title: SCREEN_LABELS[screen],
        sub: 'Screen',
        icon: SCREEN_ICONS[screen],
        run: () => go(screen),
      });
    }

    for (const l of library) {
      all.push({
        id: `loadout:${l.id}`,
        group: 'LOADOUTS',
        title: l.name,
        sub: l.id === active?.id ? 'Active loadout' : 'Switch to this loadout',
        icon: SwordIcon,
        run: () => void switchTo(l.id),
      });
    }

    for (const id of MOD_GRID_ORDER) {
      const entry = MOD_REGISTRY[id];
      const on = active ? isOn(active, id) : false;
      all.push({
        id: `mod:${id}`,
        group: 'MODS',
        title: entry.label,
        sub: on ? 'On · open settings' : 'Off · turn on',
        icon: MOD_ICONS[id] ?? LayersIcon,
        run: () => {
          selectMod(id);
          go('mods');
        },
      });
      all.push({
        id: `mod-toggle:${id}`,
        group: 'MODS',
        title: `${on ? 'Turn off' : 'Turn on'} ${entry.label}`,
        sub: entry.description,
        icon: MOD_ICONS[id] ?? LayersIcon,
        run: () => void setMod(id, { on: !on }),
      });
    }

    for (const s of servers) {
      all.push({
        id: `server:${s.host}`,
        group: 'SERVERS',
        title: s.name,
        sub: s.host,
        icon: ServerIcon,
        run: () => {
          selectServer(s.host);
          go('servers');
        },
      });
    }

    all.push(
      {
        id: 'action:launch',
        group: 'ACTIONS',
        title: phase === 'idle' ? 'Launch Minecraft' : 'Launch in progress',
        sub: active ? `with ${active.name}` : 'no loadout',
        icon: PlayIcon,
        keys: ['⌘', '↵'],
        run: () => active && phase === 'idle' && void start(active.id),
      },
      {
        id: 'action:log',
        group: 'ACTIONS',
        title: 'Toggle game log',
        sub: 'JVM stdout and stderr',
        icon: TerminalIcon,
        run: toggleLog,
      },
      {
        id: 'action:settings',
        group: 'ACTIONS',
        title: 'Open settings',
        sub: 'Account, Java, RAM, hotkeys',
        icon: GearIcon,
        run: openSettings,
      },
    );

    return all
      .map((r) => ({ r, s: Math.max(score(query, r.title), score(query, r.sub) * 0.4) }))
      .filter(({ s }) => s > 0)
      .sort((a, b) => b.s - a.s)
      .slice(0, 12)
      .map(({ r }) => r);
  }, [query, library, active, servers, phase, go, switchTo, selectMod, setMod, selectServer, start, toggleLog, openSettings]);

  if (!open) return null;

  const run = (index: number) => {
    const result = results[index];
    if (!result) return;
    result.run();
    close();
  };

  let lastGroup = '';

  return (
    <div className="palette-scrim" onMouseDown={close} role="presentation">
      <div
        className="palette"
        role="dialog"
        aria-modal="true"
        aria-label="Command palette"
        onMouseDown={(e) => e.stopPropagation()}
      >
        <div className="palette__input">
          <SearchIcon size={18} />
          <input
            ref={inputRef}
            value={query}
            placeholder="Ask VOID anything"
            aria-label="Ask VOID anything"
            onChange={(e) => {
              setQuery(e.target.value);
              setCursor(0);
            }}
            onKeyDown={(e) => {
              if (e.key === 'ArrowDown') {
                e.preventDefault();
                setCursor((c) => Math.min(c + 1, results.length - 1));
              } else if (e.key === 'ArrowUp') {
                e.preventDefault();
                setCursor((c) => Math.max(c - 1, 0));
              } else if (e.key === 'Enter') {
                e.preventDefault();
                run(cursor);
              } else if (e.key === 'Escape') {
                e.preventDefault();
                close();
              }
            }}
          />
          <Kbd tone="palette">esc</Kbd>
        </div>

        <div className="palette__list">
          {results.length === 0 ? (
            <p className="palette__empty">Nothing matches “{query}”.</p>
          ) : (
            results.map((r, i) => {
              const showGroup = r.group !== lastGroup;
              lastGroup = r.group;
              const Icon = r.icon;
              return (
                <div key={r.id}>
                  {showGroup ? <div className="palette__caption">{r.group}</div> : null}
                  <button
                    type="button"
                    className={`palette__row${i === cursor ? ' is-selected' : ''}`}
                    onMouseEnter={() => setCursor(i)}
                    onClick={() => run(i)}
                  >
                    <span className="palette__icon">
                      <Icon size={16} />
                    </span>
                    <span className="palette__text">
                      <span className="palette__title">{r.title}</span>
                      <span className="palette__sub">{r.sub}</span>
                    </span>
                    {r.keys?.map((k) => (
                      <Kbd key={k} tone="palette">
                        {k}
                      </Kbd>
                    ))}
                  </button>
                </div>
              );
            })
          )}
        </div>

        <div className="palette__footer">
          <span>
            <Kbd tone="palette">↑↓</Kbd> navigate
          </span>
          <span>
            <Kbd tone="palette">↵</Kbd> run
          </span>
          <span>
            <Kbd tone="palette">esc</Kbd> close
          </span>
          <span className="palette__footer-spacer" />
          <SwordIcon size={14} />
          <span className="palette__loadout">{active?.name ?? '—'}</span>
        </div>
      </div>
    </div>
  );
}
