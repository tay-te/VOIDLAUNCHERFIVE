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

import {
  MOD_ICONS,
  Palette,
  PaletteFooter,
  PaletteInput,
  PaletteResult,
  PaletteSeam,
  PaletteSection,
  resolveLoadoutIcon,
  type IconName,
} from '@void/ui';
import { useEffect, useMemo, useState } from 'react';

import { MOD_GRID_ORDER, MOD_REGISTRY, isOn } from '../local/registry';
import { useLaunch } from '../stores/launch';
import { useLoadouts } from '../stores/loadouts';
import { useServers } from '../stores/servers';
import { SCREENS, SCREEN_LABELS, useUi, type Screen } from '../stores/ui';

interface Result {
  id: string;
  group: string;
  title: string;
  sub: string;
  icon: IconName;
  keys?: string[];
  run: () => void;
}

/**
 * Palette rows draw from `@void/ui`'s icon set only — the two launcher-only nav marks
 * (`local/glyphs`) exist for the 14px nav band and would need `setIconRenderer` to
 * reach a `PaletteResult`, which is not worth a process-wide swap for two rows.
 */
const SCREEN_ICONS: Record<Screen, IconName> = {
  play: 'play',
  mods: 'layers',
  cosmetics: 'sparkle',
  servers: 'box',
  friends: 'users',
};

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
      // `PaletteInput` carries `autoFocus`, so React focuses it as it mounts.
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
        icon: resolveLoadoutIcon(l.icon ?? 'sword'),
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
        icon: MOD_ICONS[id] ?? 'layers',
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
        icon: MOD_ICONS[id] ?? 'layers',
        run: () => void setMod(id, { on: !on }),
      });
    }

    for (const s of servers) {
      all.push({
        id: `server:${s.host}`,
        group: 'SERVERS',
        title: s.name,
        sub: s.host,
        icon: 'box',
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
        icon: 'play',
        keys: ['⌘', '↵'],
        run: () => active && phase === 'idle' && void start(active.id),
      },
      {
        id: 'action:log',
        group: 'ACTIONS',
        title: 'Toggle game log',
        sub: 'JVM stdout and stderr',
        icon: 'reset',
        run: toggleLog,
      },
      {
        id: 'action:settings',
        group: 'ACTIONS',
        title: 'Open settings',
        sub: 'Account, Java, RAM, hotkeys',
        icon: 'settings',
        run: openSettings,
      },
    );

    const ranked = all
      .map((r) => ({ r, s: Math.max(score(query, r.title), score(query, r.sub) * 0.4) }))
      .filter(({ s }) => s > 0)
      .sort((a, b) => b.s - a.s)
      .slice(0, 12)
      .map(({ r }) => r);

    // The palette is captioned by group, so a group has to be one run of rows: rank
    // first, then pull each group together behind its best-scoring member. Without
    // this the list reads `MODS · ACTIONS · MODS`.
    const order = [...new Set(ranked.map((r) => r.group))];
    return order.flatMap((group) => ranked.filter((r) => r.group === group));
  }, [query, library, active, servers, phase, go, switchTo, selectMod, setMod, selectServer, start, toggleLog, openSettings]);

  if (!open) return null;

  const run = (index: number) => {
    const result = results[index];
    if (!result) return;
    result.run();
    close();
  };

  // Results arrive already ranked, so a group is a run of adjacent rows — which is
  // what `PaletteSection` renders: one caption over one list.
  const groups: { caption: string; rows: { result: Result; index: number }[] }[] = [];
  results.forEach((result, index) => {
    const last = groups[groups.length - 1];
    if (last && last.caption === result.group) last.rows.push({ result, index });
    else groups.push({ caption: result.group, rows: [{ result, index }] });
  });

  return (
    <div className="palette-scrim" onMouseDown={close} role="presentation">
      <Palette aria-label="Command palette" onMouseDown={(e) => e.stopPropagation()}>
        <PaletteInput
          autoFocus
          // The launcher's palette is a focused `<input>`, so the browser already draws
          // a caret at the cursor. The package's decorative one is for a still.
          showCaret={false}
          value={query}
          placeholder="Ask VOID anything"
          aria-label="Ask VOID anything"
          onChange={(next) => {
            setQuery(next);
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

        <PaletteSeam />

        <div className="palette__scroll">
          {results.length === 0 ? (
            <p className="palette__empty">Nothing matches “{query}”.</p>
          ) : (
            groups.map((group) => (
              <PaletteSection key={group.caption} caption={group.caption}>
                {group.rows.map(({ result, index }) => (
                  <PaletteResult
                    key={result.id}
                    title={result.title}
                    sub={result.sub}
                    icon={result.icon}
                    keys={result.keys}
                    selected={index === cursor}
                    onMouseEnter={() => setCursor(index)}
                    onSelect={() => run(index)}
                  />
                ))}
              </PaletteSection>
            ))
          )}
        </div>

        <PaletteSeam />

        <PaletteFooter
          hints={[
            { keys: '\u2191\u2193', word: 'move' },
            { keys: '\u21b5', word: 'run' },
            { keys: 'esc', word: 'close' },
          ]}
          loadout={active?.name ?? '\u2014'}
          loadoutIcon={resolveLoadoutIcon(active?.icon ?? 'sword')}
        />
      </Palette>
    </div>
  );
}
