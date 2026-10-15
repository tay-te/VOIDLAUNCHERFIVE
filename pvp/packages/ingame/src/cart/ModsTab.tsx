/**
 * Tab 1 — Mods: cartridges on category shelves (Figma 594:2), the search across mods and every
 * setting (595:2) and the empty Pinned shelf (596:2).
 *
 *   header   Mods  14 / 44 ON      All · HUD · PvP · Visual · Utility · Pinned      [search  /]
 *   shelves  ● HUD  8 OF 12 ON ──────────────────────────────────────── VIEW SHELF ›
 *            [cart][cart][cart][cart][cart][+7 more]
 *            …
 *   footer   4 SHELVES · SCROLL FOR UTILITY             [1-5] sections [Tab] shelf [Space] toggle [E] settings
 *
 * Keyboard (the footer says so): arrows walk the cartridges, Tab / Shift+Tab jump a shelf,
 * Space toggles, E or Enter opens the settings bench, P pins, `/` searches, H opens the HUD
 * layout. Escape is MenuLayer's: it clears a search, then leaves a single shelf, then closes.
 */

import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react';

import { MOD_REGISTRY, type ModId } from '@/bridge/protocol';
import { modLabel } from '@/registry';
import { MAX_QUICK_PINS, isModOn, modSettings, modsOnCount, quickPins, useVoidStore } from '@/store/store';
import { hasTextFocus } from '@/bridge/connect';
import { modProperties } from '@/menu/ModSettingsScreen';
import { formatSetting, keybindLabel } from '@/menu/settings-format';
import { cx } from '@/ui';
import { Cartridge, MoreCartridge, highlight } from './Cartridge';
import { Sprite, hasSprite } from './Sprite';
import { CATEGORIES, CATEGORY_LABEL, Kc, Led, SearchGlyph, catClass, type CartCategory } from './prims';
import { ALL_SHELVED, COLUMNS, SHELF_MODS, SHELVES, shelfPreview } from './shelves';

export type ModsFilter = 'all' | CartCategory | 'pinned';

const FILTERS: readonly ModsFilter[] = ['all', 'hud', 'pvp', 'visual', 'utility', 'pinned'];

function filterLabel(filter: ModsFilter): string {
  if (filter === 'all') return 'All';
  if (filter === 'pinned') return 'Pinned';
  return CATEGORY_LABEL[filter];
}

export function asFilter(value: string): ModsFilter {
  return (FILTERS as readonly string[]).includes(value) ? (value as ModsFilter) : 'all';
}

/* -------------------------------------------------------------------------- */
/* Search                                                                     */
/* -------------------------------------------------------------------------- */

/** Mods matching a query, best match first: name start, word start, anywhere, then description. */
export function searchMods(query: string): ModId[] {
  const q = query.trim().toLowerCase();
  if (!q) return [];
  const scored: [ModId, number][] = [];
  for (const id of ALL_SHELVED) {
    const name = modLabel(id).toLowerCase();
    let score: number | null = null;
    if (name.startsWith(q)) score = 0;
    else if (name.split(/[\s&·-]+/).some((w) => w.startsWith(q))) score = 1;
    else if (name.includes(q)) score = 2;
    else if (id.replace(/_/g, ' ').includes(q)) score = 3;
    // Descriptions only answer a real word: two letters match half the registry's prose.
    else if (q.length >= 3 && MOD_REGISTRY[id].description.toLowerCase().includes(q)) score = 4;
    if (score !== null) scored.push([id, score]);
  }
  return scored.sort((a, b) => a[1] - b[1]).map(([id]) => id);
}

export interface SettingHit {
  id: ModId;
  key: string;
  label: string;
}

/** Settings whose label matches, across every mod — "searches names, shelves and every setting". */
export function searchSettings(query: string, settingsOf: (id: ModId) => Record<string, unknown>): SettingHit[] {
  const q = query.trim().toLowerCase();
  if (q.length < 2) return [];
  const hits: SettingHit[] = [];
  for (const id of ALL_SHELVED) {
    for (const property of modProperties(id, settingsOf(id) as never)) {
      if (property.label.toLowerCase().includes(q)) hits.push({ id, key: property.key, label: property.label });
    }
  }
  return hits.slice(0, 8);
}

/* -------------------------------------------------------------------------- */
/* Layout model — the rows the cursor walks                                   */
/* -------------------------------------------------------------------------- */

/** A slot in a row: a mod, or a shelf's `+N more`. */
export type Slot = ModId | `more:${CartCategory}`;

interface Row {
  shelf: CartCategory | null;
  slots: Slot[];
}

function chunk(ids: readonly ModId[], shelf: CartCategory | null): Row[] {
  const rows: Row[] = [];
  for (let i = 0; i < ids.length; i += COLUMNS) rows.push({ shelf, slots: ids.slice(i, i + COLUMNS) });
  return rows;
}

export function layoutRows(filter: ModsFilter, query: string, pins: readonly ModId[]): Row[] {
  if (query.trim()) return chunk(searchMods(query), null);
  if (filter === 'pinned') return chunk(pins.filter((id) => SHELF_MODS.includes(id)), null);
  if (filter !== 'all') return chunk(SHELVES[filter], filter);
  return CATEGORIES.map((c) => {
    const { shown, more } = shelfPreview(SHELVES[c]);
    return { shelf: c, slots: more > 0 ? [...shown, `more:${c}` as Slot] : [...shown] };
  });
}

/* -------------------------------------------------------------------------- */

function SearchField() {
  const query = useVoidStore((s) => s.modSearch);
  const setQuery = useVoidStore((s) => s.setModSearch);
  const focused = useRef<HTMLInputElement>(null);
  return (
    <label className={cx('c-search', query !== '' && 'is-active')}>
      <SearchGlyph lit={query !== ''} />
      <input
        ref={focused}
        id="c-search-field"
        value={query}
        placeholder="Search mods, settings"
        aria-label="Search mods and settings"
        spellCheck={false}
        autoComplete="off"
        onChange={(event) => setQuery(event.target.value)}
      />
      <Kc>{query !== '' ? 'Esc' : '/'}</Kc>
    </label>
  );
}

function ShelfHead({ category, onView }: { category: CartCategory; onView: () => void }) {
  const ids = SHELVES[category];
  const on = useVoidStore((s) => ids.filter((id) => isModOn(s.loadout, id)).length);
  return (
    <div className={cx('c-shead', catClass(category))}>
      <Led on />
      <span className="c-shead__name">{CATEGORY_LABEL[category]}</span>
      <span className="c-mono">
        {on} of {ids.length} on
      </span>
      <span className="c-shead__rule" />
      <button type="button" className="c-shead__view" onClick={onView}>
        View shelf <span aria-hidden="true">›</span>
      </button>
    </div>
  );
}

function PinnedEmpty({ onBrowse }: { onBrowse: () => void }) {
  return (
    <div className="c-empty">
      <div className="c-empty__slots" aria-hidden="true">
        <span className="c-empty__slot">
          <span className="c-mono">slot 1</span>
        </span>
        <span className="c-empty__slot c-empty__slot--lit">
          {hasSprite('pin') ? <Sprite name="pin" size={64} /> : null}
        </span>
        <span className="c-empty__slot">
          <span className="c-mono">slot 3</span>
        </span>
      </div>
      <div className="c-title">This shelf is empty</div>
      <p className="c-empty__line">
        Pin cartridges you tweak mid-session. Hover one and press <Kc>P</Kc>.
      </p>
      <button type="button" className="c-btn c-btn--s" onClick={onBrowse}>
        Browse all shelves
      </button>
    </div>
  );
}

function SettingRows({ hits, query, onOpen }: { hits: SettingHit[]; query: string; onOpen: (id: ModId) => void }) {
  const loadout = useVoidStore((s) => s.loadout);
  return (
    <div className="c-srows">
      {hits.map((hit) => {
        const value = modSettings(loadout, hit.id)[hit.key];
        const shown =
          typeof value === 'boolean'
            ? value
              ? 'On'
              : 'Off'
            : hit.key.endsWith('key') || hit.key === 'keybind'
              ? keybindLabel(value ?? null)
              : typeof value === 'string' && value.startsWith('#')
                ? value.slice(0, 7).toUpperCase()
                : typeof value === 'string'
                  ? value.charAt(0).toUpperCase() + value.slice(1).replace(/_/g, ' ')
                  : formatSetting(hit.key, value ?? null);
        return (
          <button key={`${hit.id}.${hit.key}`} type="button" className="c-srow" onClick={() => onOpen(hit.id)}>
            <span className="c-srow__bed">{hasSprite(hit.id) ? <Sprite name={hit.id} size={24} /> : null}</span>
            <span className="c-srow__text">
              <span className="c-srow__label">{highlight(hit.label, query)}</span>
              <span className="c-mono c-srow__mod">{modLabel(hit.id)}</span>
            </span>
            <span className="c-srow__value">{shown}</span>
          </button>
        );
      })}
    </div>
  );
}

function FooterKeys({ keys }: { keys: [string, string][] }) {
  return (
    <span className="c-foot__keys">
      {keys.map(([k, what]) => (
        <span key={k} className="c-foot__key">
          <Kc>{k}</Kc>
          {what}
        </span>
      ))}
    </span>
  );
}

/* -------------------------------------------------------------------------- */

export interface ModsTabProps {
  /** The mod whose bench is open, if any. */
  benchId: ModId | null;
}

export function ModsTab({ benchId }: ModsTabProps) {
  const loadout = useVoidStore((s) => s.loadout);
  const query = useVoidStore((s) => s.modSearch);
  const filter = asFilter(useVoidStore((s) => s.modFilter));
  const setFilter = useVoidStore((s) => s.setModFilter);
  const setQuery = useVoidStore((s) => s.setModSearch);
  const cursor = useVoidStore((s) => s.selectedMod);
  const selectMod = useVoidStore((s) => s.selectMod);
  const openMod = useVoidStore((s) => s.openMod);
  const toggleMod = useVoidStore((s) => s.toggleMod);
  const toggleQuickPin = useVoidStore((s) => s.toggleQuickPin);
  const setRoute = useVoidStore((s) => s.setRoute);
  const scroller = useRef<HTMLDivElement>(null);
  // The cursor on a shelf's `+N more` slot. Kept here rather than in `selectedMod`, which only
  // ever names a mod — the palette and the bench read it.
  const [moreAt, setMoreAt] = useState<CartCategory | null>(null);
  const cursorSlot: Slot = moreAt ? `more:${moreAt}` : cursor;

  const pinsKey = useVoidStore((s) => quickPins(s.loadout).join(','));
  const pins = useMemo(() => (pinsKey ? (pinsKey.split(',') as ModId[]) : []), [pinsKey]);
  const searching = query.trim() !== '';
  const rows = useMemo(() => layoutRows(filter, query, pins), [filter, query, pins]);
  const settingHits = useMemo(
    () => (searching ? searchSettings(query, (id) => modSettings(loadout, id)) : []),
    [searching, query, loadout],
  );
  const on = modsOnCount(loadout);
  const total = SHELF_MODS.length;

  const counts: Record<ModsFilter, number> = useMemo(() => {
    if (searching) {
      const hits = searchMods(query);
      return {
        all: hits.length,
        hud: hits.filter((id) => SHELVES.hud.includes(id)).length,
        pvp: hits.filter((id) => SHELVES.pvp.includes(id)).length,
        visual: hits.filter((id) => SHELVES.visual.includes(id)).length,
        utility: hits.filter((id) => SHELVES.utility.includes(id)).length,
        pinned: hits.filter((id) => pins.includes(id)).length,
      };
    }
    return {
      all: total,
      hud: SHELVES.hud.length,
      pvp: SHELVES.pvp.length,
      visual: SHELVES.visual.length,
      utility: SHELVES.utility.length,
      pinned: pins.length,
    };
  }, [searching, query, pins, total]);

  const onCursor = (id: ModId) => {
    setMoreAt(null);
    selectMod(id);
  };

  const viewShelf = (category: CartCategory) => {
    setMoreAt(null);
    setQuery('');
    setFilter(category);
    scroller.current?.scrollTo?.({ top: 0 });
  };

  // Keep the cursor in view as the keys walk it.
  useEffect(() => {
    const el = scroller.current?.querySelector<HTMLElement>(`[data-mod-id="${cursor}"]`);
    el?.scrollIntoView?.({ block: 'nearest' });
  }, [cursor]);

  // The tab's keys. Digits and Escape are the shell's (CartMenu / MenuLayer).
  const latest = useRef({ rows, cursor: cursorSlot, pins });
  latest.current = { rows, cursor: cursorSlot, pins };
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (useVoidStore.getState().paletteOpen) return;
      const typing = hasTextFocus();
      const { rows: grid, cursor: at } = latest.current;
      if (e.key === '/' && !typing) {
        e.preventDefault();
        document.getElementById('c-search-field')?.focus();
        return;
      }
      // While typing, only the keys that leave the field act here.
      if (typing && !['ArrowDown', 'ArrowUp', 'Enter', 'Tab'].includes(e.key)) return;

      const flat: { r: number; c: number; slot: Slot }[] = [];
      grid.forEach((row, r) => row.slots.forEach((slot, c) => flat.push({ r, c, slot })));
      const here = flat.find((cell) => cell.slot === at);

      const go = (slot: Slot | undefined) => {
        if (!slot) return;
        if (slot.startsWith('more:')) {
          setMoreAt(slot.slice(5) as CartCategory);
        } else {
          setMoreAt(null);
          selectMod(slot as ModId);
        }
      };

      if (e.key === 'ArrowRight' || e.key === 'ArrowLeft' || e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        if (typing && (e.key === 'ArrowLeft' || e.key === 'ArrowRight')) return;
        e.preventDefault();
        if (flat.length === 0) return;
        if (!here) {
          go(flat[0]!.slot);
          if (typing) (document.activeElement as HTMLElement | null)?.blur();
          return;
        }
        if (e.key === 'ArrowRight' || e.key === 'ArrowLeft') {
          const i = flat.indexOf(here) + (e.key === 'ArrowRight' ? 1 : -1);
          go(flat[Math.max(0, Math.min(flat.length - 1, i))]!.slot);
          return;
        }
        const r = here.r + (e.key === 'ArrowDown' ? 1 : -1);
        const row = grid[Math.max(0, Math.min(grid.length - 1, r))]!;
        go(row.slots[Math.min(here.c, row.slots.length - 1)]);
        return;
      }

      if (e.key === 'Tab') {
        e.preventDefault();
        if (typing) (document.activeElement as HTMLElement | null)?.blur();
        const state = useVoidStore.getState();
        const current = asFilter(state.modFilter);
        if (current !== 'all' && current !== 'pinned' && !state.modSearch) {
          // A single shelf: Tab walks to the next shelf.
          const i = CATEGORIES.indexOf(current);
          const next = CATEGORIES[(i + (e.shiftKey ? CATEGORIES.length - 1 : 1)) % CATEGORIES.length]!;
          setFilter(next);
          go(SHELVES[next][0]);
          return;
        }
        // All shelves: the cursor jumps to the first cartridge of the next shelf.
        const shelfRows = grid.filter((row) => row.shelf !== null);
        if (shelfRows.length === 0) return;
        const at2 = here ? grid[here.r]!.shelf : null;
        const order = shelfRows.map((row) => row.shelf!);
        const i = at2 ? order.indexOf(at2) : -1;
        const nextShelf = order[(i + (e.shiftKey ? order.length - 1 : 1) + order.length) % order.length]!;
        go(grid.find((row) => row.shelf === nextShelf)?.slots[0]);
        return;
      }

      if (!here || here.slot.startsWith('more:')) {
        if (here && (e.key === 'Enter' || e.key === ' ')) {
          e.preventDefault();
          viewShelf(here.slot.slice(5) as CartCategory);
        }
        return;
      }
      const id = here.slot as ModId;
      // A focused button (a switch just clicked, the bench's controls) answers Space and Enter
      // itself; acting here as well would do it twice.
      const onButton = document.activeElement instanceof HTMLButtonElement;
      if (onButton && (e.key === ' ' || e.key === 'Spacebar' || e.key === 'Enter')) return;
      if (e.key === ' ' || e.key === 'Spacebar') {
        if (typing) return;
        e.preventDefault();
        toggleMod(id, !isModOn(useVoidStore.getState().loadout, id));
      } else if (e.key === 'Enter' || e.key === 'e' || e.key === 'E') {
        if (typing && e.key !== 'Enter') return;
        e.preventDefault();
        openMod(id);
      } else if ((e.key === 'p' || e.key === 'P') && !typing) {
        e.preventDefault();
        const state = useVoidStore.getState();
        const current = quickPins(state.loadout);
        if (!current.includes(id) && current.length >= MAX_QUICK_PINS) {
          state.pushToast({ kind: 'info', title: 'The Pinned shelf is full', detail: `It holds ${MAX_QUICK_PINS} — unpin one first` });
          return;
        }
        toggleQuickPin(id);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectMod, openMod, toggleMod, toggleQuickPin, setFilter]);

  /* ---- body ---- */

  let body: ReactNode;
  const cart = (slot: Slot) =>
    slot.startsWith('more:') ? (
      <MoreCartridge
        key={slot}
        count={SHELVES[slot.slice(5) as CartCategory].length - (COLUMNS - 1)}
        cursor={cursorSlot === slot}
        onCursor={() => setMoreAt(slot.slice(5) as CartCategory)}
        onOpen={() => viewShelf(slot.slice(5) as CartCategory)}
      />
    ) : (
      <Cartridge
        key={slot}
        id={slot as ModId}
        cursor={cursorSlot === slot}
        selected={benchId === slot}
        query={query}
        onCursor={onCursor}
        onOpen={openMod}
      />
    );
  const rowOf = (row: Row, i: number) => (
    <div key={i} className="c-row">
      {row.slots.map(cart)}
      {/* Fillers keep a short last row on the same six-column rhythm. */}
      {Array.from({ length: COLUMNS - row.slots.length }, (_, k) => (
        <span key={`f${k}`} className="c-cart c-cart--filler" aria-hidden="true" />
      ))}
    </div>
  );

  if (searching) {
    const mods = rows.flatMap((r) => r.slots);
    body = (
      <>
        <div className="c-shead">
          <span className="c-shead__name">Mods</span>
          <span className="c-mono">{mods.length} · best match first</span>
          <span className="c-shead__rule" />
        </div>
        {mods.length > 0 ? (
          <div className="c-rows">{rows.map(rowOf)}</div>
        ) : (
          <p className="c-none">No mod matches “{query.trim()}”.</p>
        )}
        {settingHits.length > 0 ? (
          <>
            <div className="c-shead c-shead--gap">
              <span className="c-shead__name">Settings</span>
              <span className="c-mono">{settingHits.length} inside mods</span>
              <span className="c-shead__rule" />
            </div>
            <SettingRows hits={settingHits} query={query} onOpen={openMod} />
          </>
        ) : null}
      </>
    );
  } else if (filter === 'pinned' && rows.length === 0) {
    body = <PinnedEmpty onBrowse={() => setFilter('all')} />;
  } else if (filter === 'all') {
    body = rows.map((row) => (
      <section key={row.shelf!} className="c-shelf" aria-label={`${CATEGORY_LABEL[row.shelf!]} shelf`}>
        <ShelfHead category={row.shelf!} onView={() => viewShelf(row.shelf!)} />
        {rowOf(row, 0)}
      </section>
    ));
  } else {
    body = (
      <section className="c-shelf" aria-label={`${filterLabel(filter)} shelf`}>
        {filter !== 'pinned' ? <ShelfHead category={filter} onView={() => setFilter('all')} /> : null}
        <div className="c-rows">{rows.map(rowOf)}</div>
      </section>
    );
  }

  const footLeft = searching
    ? 'Searches names, shelves and every setting'
    : filter === 'all'
      ? `${CATEGORIES.length} shelves · scroll for more`
      : filter === 'pinned'
        ? 'Pinned cartridges follow you across loadouts'
        : `${SHELVES[filter].length} ${CATEGORY_LABEL[filter]} cartridges · ${
            SHELVES[filter].filter((id) => isModOn(loadout, id)).length
          } on`;

  return (
    <>
      <header className="c-head">
        <div className="c-head__title">
          <span className="c-h1">Mods</span>
          <span className="c-mono">
            {on} / {total} on
          </span>
        </div>
        <div className="c-chips" role="tablist" aria-label="Shelves">
          {FILTERS.map((f) => (
            <button
              key={f}
              type="button"
              role="tab"
              aria-selected={!searching && filter === f}
              className={cx('c-chip', !searching && filter === f && 'is-on', f !== 'all' && f !== 'pinned' && catClass(f))}
              onClick={() => {
                setQuery('');
                setFilter(f);
              }}
            >
              {f !== 'all' && f !== 'pinned' ? <Led on /> : null}
              {filterLabel(f)}
              <i>{counts[f]}</i>
            </button>
          ))}
        </div>
        <SearchField />
      </header>
      <div className="c-scroll" ref={scroller}>
        {body}
      </div>
      <footer className="c-foot">
        <span className="c-mono">{footLeft}</span>
        <FooterKeys
          keys={
            searching
              ? [
                  ['↑↓', 'move'],
                  ['Space', 'toggle'],
                  ['E', 'settings'],
                  ['Esc', 'clear'],
                ]
              : [
                  ['1-5', 'sections'],
                  ['Tab', 'shelf'],
                  ['Space', 'toggle'],
                  ['E', 'settings'],
                ]
          }
        />
      </footer>
    </>
  );
}

export { FooterKeys };
