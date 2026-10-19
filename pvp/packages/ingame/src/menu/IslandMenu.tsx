/**
 * The Right-Shift menu — Figma `3c0Wgc4ZKFSpm98fWJoARS`, page "★ v1.1 · Iterations":
 *
 *   lobby   `261:50520` "In game · Mods · final header (tabs + search icon)", in the section
 *           "★ In-game · Final shell + original centred mod cards"
 *   match   `229:47020` "Floating · D · In match (quick panel only)"
 *
 * The settled rules (flat, rounded, no gradients; a smaller panel in the middle of the screen;
 * other things as fixed floating panels) and v1 Ship rule 9 — the full menu only in the lobby; in
 * a live match a quick panel only, no dim, closed by the first hit taken (`store.ts`,
 * `MenuContext`).
 *
 * Lobby — five islands over the world:
 *
 *   mark      VOID, top-left                                  (24, 24)    121 × 48
 *   tabs      Mods · Friends · Cosmetics, Tab cycles them     centred at the top, 337 × 50
 *   close     ✕ and the menu key, top-right                   (1468, 24)  108 × 48
 *   panel     the active tab, centred                         987 × 630
 *   strip     where you are, and HUD layout                   centred at the bottom, 302 × 72
 *
 * The Mods panel is the "original cards": the very tiles the grid has always drawn
 * (`ModsScreen`'s `Tile`, live previews and all), five across at 175 × 223, under category tabs
 * with counts and a search button. A tile's body opens the mod's page, its switch toggles it —
 * the tile's own contract, unchanged.
 *
 * Match — the mark, close, and one panel at the top centre: eight numbered quick toggles that
 * `1`–`8` flip without the mouse.
 *
 * Ultralight (`design/ultralight-notes.md`): flexbox and absolute positioning only, every mark is
 * cells or a border, no gradients, no shadows.
 */

import { useEffect, useMemo, useRef, useState, type CSSProperties } from 'react';

import { MOD_REGISTRY, type ModId } from '@/bridge/protocol';
import { MOD_ORDER, modLabel } from '@/registry';
import { isModOn, modsOnCount, useVoidStore } from '@/store/store';
import { Toggle, cx } from '@/ui';
import { Tile, gridRows } from './ModsScreen';
import { keybindLabel } from './settings-format';

type Category = 'hud' | 'pvp' | 'visual' | 'utility';
type Filter = 'all' | Category;
const FILTERS: readonly { id: Filter; label: string }[] = [
  { id: 'all', label: 'All' },
  { id: 'hud', label: 'HUD' },
  { id: 'pvp', label: 'PvP' },
  { id: 'visual', label: 'Visual' },
  { id: 'utility', label: 'Utility' },
];
const HUE: Record<Category, string> = {
  hud: 'var(--hue-hud)',
  pvp: 'var(--hue-pvp)',
  visual: 'var(--hue-visual)',
  utility: 'var(--hue-utility)',
};

export type MenuTab = 'mods' | 'friends' | 'cosmetics';
const TABS: readonly { id: MenuTab; label: string }[] = [
  { id: 'mods', label: 'Mods' },
  { id: 'friends', label: 'Friends' },
  { id: 'cosmetics', label: 'Cosmetics' },
];

/** Five tiles to a row, as the frame lays them out. */
const COLUMNS = 5;

const categoryOf = (id: ModId): Category => (MOD_REGISTRY[id]?.category ?? 'hud') as Category;

/** Every registry mod, in grid order. The `?fake=` padding stays in the old grid. */
const REAL_MODS: readonly ModId[] = MOD_ORDER.filter((id) => Boolean(MOD_REGISTRY[id]));

/**
 * The quick panel's eight (`229:47020`): the mods a fight reaches for. A loadout has no pins in
 * the schema yet, so this is the frame's own list; any the registry lacks are skipped.
 */
export const QUICK_MODS: readonly ModId[] = (
  ['toggle_sprint', 'zoom', 'keystrokes', 'crosshair', 'hit_color', 'fov', 'hitboxes', 'fullbright'] as ModId[]
).filter((id) => Boolean(MOD_REGISTRY[id]));

/** Mods matching a category and a search query, in grid order. */
export function filterMods(filter: Filter, query: string): ModId[] {
  const q = query.trim().toLowerCase();
  return REAL_MODS.filter((id) => {
    if (filter !== 'all' && categoryOf(id) !== filter) return false;
    if (!q) return true;
    return (
      modLabel(id).toLowerCase().includes(q) ||
      MOD_REGISTRY[id].description.toLowerCase().includes(q) ||
      id.includes(q)
    );
  });
}

// ------------------------------------------------------------------- marks

/** The 16-cell VOID ring, 4px cells on a 7 × 7 grid. */
const RING: readonly [number, number][] = [
  [2, 0], [3, 0], [4, 0], [1, 1], [5, 1], [0, 2], [6, 2], [0, 3],
  [6, 3], [0, 4], [6, 4], [1, 5], [5, 5], [2, 6], [3, 6], [4, 6],
];

function RingMark({ size = 28 }: { size?: number }) {
  const step = size / 7;
  return (
    <span className="isl-ring" style={{ width: size, height: size }} aria-hidden="true">
      {RING.map(([x, y]) => (
        <i key={`${x}${y}`} style={{ left: x * step, top: y * step, width: step, height: step }} />
      ))}
    </span>
  );
}

// --------------------------------------------------------------- the chrome

function MarkIsland() {
  return (
    <div className="island island--mark" aria-hidden="true">
      <RingMark size={28} />
      <span className="isl-word">VOID</span>
    </div>
  );
}

function CloseIsland() {
  const closeMenu = useVoidStore((s) => s.closeMenu);
  const menuKey = useVoidStore((s) => s.globals.menuKey);
  return (
    <button type="button" className="island island--close" onClick={closeMenu} aria-label="Close the menu">
      <span className="isl-x" aria-hidden="true" />
      <span className="isl-key">{keybindLabel(menuKey)}</span>
    </button>
  );
}

/** `261:50566`: Mods · Friends · Cosmetics, and the Tab key that walks them. */
function TabsIsland({ tab, onTab }: { tab: MenuTab; onTab: (tab: MenuTab) => void }) {
  return (
    <nav className="island island--tabs" aria-label="Menu">
      {TABS.map((t) => (
        <button
          key={t.id}
          type="button"
          className={cx('isl-tab', tab === t.id && 'is-on')}
          aria-current={tab === t.id ? 'page' : undefined}
          onClick={() => onTab(t.id)}
        >
          {t.label}
        </button>
      ))}
      <span className="isl-tabs__sep" aria-hidden="true" />
      <span className="isl-tabs__hint">
        <span className="isl-key">Tab</span>
      </span>
    </nav>
  );
}

/** `mc.hypixel.net` → `Hypixel`. */
function serverName(host: string): string {
  const parts = host.split(':')[0]!.split('.').filter(Boolean);
  const core = parts.length >= 2 ? parts[parts.length - 2]! : (parts[0] ?? host);
  return core.charAt(0).toUpperCase() + core.slice(1);
}

function LobbyStrip() {
  const server = useVoidStore((s) => s.server);
  const setRoute = useVoidStore((s) => s.setRoute);
  return (
    <div className="island island--strip">
      <div className="isl-where">
        <span className="isl-where__live">
          <i className={server.connected ? 'is-on' : undefined} />
          {server.connected ? 'Lobby' : 'Singleplayer'}
        </span>
        <span className="isl-where__name">{server.connected ? serverName(server.host) : 'Local world'}</span>
      </div>
      <span className="isl-sep" aria-hidden="true" />
      <button type="button" className="isl-pill" onClick={() => setRoute({ name: 'hud-editor' })}>
        <span className="isl-move" aria-hidden="true">
          <i />
          <i />
        </span>
        HUD layout
      </button>
    </div>
  );
}

// --------------------------------------------------------------- the panel

function ModsPanel() {
  const loadout = useVoidStore((s) => s.loadout);
  const selected = useVoidStore((s) => s.selectedMod);
  const selectMod = useVoidStore((s) => s.selectMod);
  const openMod = useVoidStore((s) => s.openMod);
  const toggleMod = useVoidStore((s) => s.toggleMod);
  const [filter, setFilter] = useState<Filter>('all');
  const [searching, setSearching] = useState(false);
  const [query, setQuery] = useState('');
  const field = useRef<HTMLInputElement>(null);
  const scroller = useRef<HTMLDivElement>(null);

  const counts = useMemo(() => {
    const c: Record<Filter, number> = { all: REAL_MODS.length, hud: 0, pvp: 0, visual: 0, utility: 0 };
    for (const id of REAL_MODS) c[categoryOf(id)] += 1;
    return c;
  }, []);
  const visible = useMemo(() => filterMods(filter, searching ? query : ''), [filter, searching, query]);
  const rows = useMemo(() => gridRows(visible, COLUMNS), [visible]);
  const on = loadout ? modsOnCount(loadout) : 0;

  // Keep the highlighted tile in view as the arrows walk the grid.
  useEffect(() => {
    const el = scroller.current?.querySelector<HTMLElement>(`[data-mod-id="${selected}"]`);
    el?.scrollIntoView?.({ block: 'nearest' });
  }, [selected]);

  // ← → ↑ ↓ walk the tiles, Enter opens one, Space toggles it, `/` searches.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const typing = document.activeElement === field.current;
      if (e.key === '/' && !typing) {
        e.preventDefault();
        setSearching(true);
        setTimeout(() => field.current?.focus(), 0);
        return;
      }
      if (typing && !['ArrowDown', 'ArrowUp', 'Enter'].includes(e.key)) return;
      const step = { ArrowRight: 1, ArrowLeft: -1, ArrowDown: COLUMNS, ArrowUp: -COLUMNS }[e.key];
      if (step !== undefined && visible.length > 0) {
        if (typing && (e.key === 'ArrowLeft' || e.key === 'ArrowRight')) return;
        e.preventDefault();
        const at = visible.indexOf(selected as ModId);
        // Nothing highlighted in this view yet: the first press lands on the first tile.
        const next = at < 0 ? 0 : Math.min(visible.length - 1, Math.max(0, at + step));
        selectMod(visible[next]!);
        return;
      }
      if (!visible.includes(selected as ModId)) return;
      if (e.key === 'Enter') {
        e.preventDefault();
        openMod(selected as ModId);
      } else if ((e.key === ' ' || e.key === 'Spacebar') && !typing) {
        e.preventDefault();
        toggleMod(selected as ModId, !isModOn(useVoidStore.getState().loadout, selected as ModId));
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [visible, selected, selectMod, openMod, toggleMod]);

  return (
    <section className="island island--panel isl-mods" aria-label="Mods">
      <header className="isl-mods__head">
        {searching ? (
          <label className="isl-search">
            <span className="isl-search__icon" aria-hidden="true" />
            <input
              ref={field}
              autoFocus
              value={query}
              placeholder="Search mods and settings"
              aria-label="Search mods"
              spellCheck={false}
              onChange={(e) => setQuery(e.target.value)}
            />
            <button
              type="button"
              className="isl-search__done"
              onClick={() => {
                setSearching(false);
                setQuery('');
              }}
            >
              Done
            </button>
          </label>
        ) : (
          <div className="isl-cats" role="tablist" aria-label="Category">
            {FILTERS.map((f) => (
              <button
                key={f.id}
                type="button"
                role="tab"
                aria-selected={filter === f.id}
                className={cx('isl-cats__tab', filter === f.id && 'is-on')}
                onClick={() => setFilter(f.id)}
              >
                {f.label}
                <span className="isl-cats__count">{counts[f.id]}</span>
              </button>
            ))}
          </div>
        )}
        {!searching ? (
          <button
            type="button"
            className="isl-iconbtn"
            aria-label="Search mods"
            onClick={() => {
              setSearching(true);
              setTimeout(() => field.current?.focus(), 0);
            }}
          >
            <span className="isl-search__icon" aria-hidden="true" />
          </button>
        ) : null}
      </header>

      <div className="isl-mods__grid" ref={scroller}>
        {rows.map((row, i) => (
          <div key={i} className="isl-mods__row">
            {row.map((id) => (
              <Tile key={id} id={id} selected={id === selected} />
            ))}
          </div>
        ))}
        {visible.length === 0 ? <p className="isl-empty">No mod matches “{query.trim()}”</p> : null}
      </div>

      <footer className="isl-mods__foot">
        <span>
          {visible.length} of {REAL_MODS.length} shown{'  ·  '}
          {on} enabled{loadout ? ` in ${loadout.name}` : ''}
        </span>
        <span>Changes apply immediately</span>
      </footer>
    </section>
  );
}

/**
 * Friends and Cosmetics. The frame gives them tabs; the bridge gives them no data — nothing on
 * `bridge.json` carries a friend, a presence or an owned cosmetic — so the panel says what it
 * would show and where that lives today, instead of drawing a roster it cannot know.
 */
function PlaceholderPanel({ tab }: { tab: Exclude<MenuTab, 'mods'> }) {
  const copy =
    tab === 'friends'
      ? {
          title: 'Friends',
          line: 'Who is online, who is in a match, and joining them — once the friends service is connected. Until then your list lives in the launcher.',
        }
      : {
          title: 'Cosmetics',
          line: 'Your capes, trails and nameplates, worn in game for other VOID players to see — once cosmetics are connected. Until then, pick them in the launcher’s Store.',
        };
  return (
    <section className="island island--panel isl-soon" aria-label={copy.title}>
      <RingMark size={42} />
      <p className="isl-soon__eyebrow">Not connected yet</p>
      <h2 className="isl-soon__title">{copy.title}</h2>
      <p className="isl-soon__line">{copy.line}</p>
    </section>
  );
}

// ------------------------------------------------------------ in a match

/** `229:47020`: eight numbered toggles, top centre, nothing dimmed. */
function QuickPanel() {
  const loadout = useVoidStore((s) => s.loadout);
  const toggleMod = useVoidStore((s) => s.toggleMod);
  const closeMenu = useVoidStore((s) => s.closeMenu);
  const menuKey = useVoidStore((s) => s.globals.menuKey);

  // 1–8 flip the pins without the mouse — the reason they are numbered.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const n = Number(e.key);
      if (!Number.isInteger(n) || n < 1 || n > QUICK_MODS.length) return;
      e.preventDefault();
      const id = QUICK_MODS[n - 1]!;
      toggleMod(id, !isModOn(useVoidStore.getState().loadout, id));
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [toggleMod]);

  return (
    <div className="isl-quickwrap">
      <section className="island island--quick" aria-label="Quick toggles">
        <header className="isl-quick__head">
          <span className="isl-quick__titles">
            <span className="isl-quick__title">Quick{loadout ? ` · ${loadout.name}` : ''}</span>
            <span className="isl-quick__sub">Closes when you take a hit</span>
          </span>
          <button type="button" className="isl-roundbtn" aria-label="Close" onClick={closeMenu}>
            ×
          </button>
        </header>
        <div className="isl-quick__pins">
          {QUICK_MODS.map((id, i) => {
            const on = isModOn(loadout, id);
            return (
              <div
                key={id}
                className={cx('isl-pin', !on && 'isl-pin--off')}
                style={{ '--hue': HUE[categoryOf(id)] } as CSSProperties}
              >
                <span className="isl-pin__num">{i + 1}</span>
                <span className="isl-pin__name">{modLabel(id)}</span>
                <Toggle size="md" checked={on} onChange={(next) => toggleMod(id, next)} label={`${modLabel(id)} — ${on ? 'on' : 'off'}`} />
              </div>
            );
          })}
        </div>
      </section>
      <p className="isl-quick__hint">
        <span className="isl-key isl-key--sm">1–{QUICK_MODS.length}</span> toggle
        <span className="isl-key isl-key--sm">{keybindLabel(menuKey)}</span> close
      </p>
    </div>
  );
}

// ------------------------------------------------------------------ menu

/** The whole Right-Shift menu for the current context. */
export function IslandMenu() {
  const context = useVoidStore((s) => s.context);
  const [tab, setTab] = useState<MenuTab>('mods');

  // Tab walks the three tabs, Shift+Tab back — the hint on the tabs island says so.
  useEffect(() => {
    if (context !== 'lobby') return undefined;
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== 'Tab') return;
      e.preventDefault();
      setTab((t) => {
        const i = TABS.findIndex((x) => x.id === t);
        return TABS[(i + (e.shiftKey ? TABS.length - 1 : 1)) % TABS.length]!.id;
      });
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [context]);

  if (context === 'match') {
    return (
      <div className="islands islands--match">
        <MarkIsland />
        <CloseIsland />
        <QuickPanel />
      </div>
    );
  }

  return (
    <div className="islands islands--lobby">
      <MarkIsland />
      <TabsIsland tab={tab} onTab={setTab} />
      <CloseIsland />
      {tab === 'mods' ? <ModsPanel /> : <PlaceholderPanel tab={tab} />}
      <LobbyStrip />
    </div>
  );
}
