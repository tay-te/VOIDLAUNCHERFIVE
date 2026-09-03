/**
 * Overlay — Mods · frame `244:538`.
 *
 * Header: title, 230 px search, All / HUD / PvP / Visual / Utility tabs, close.
 * Body: 3 × 4 grid of 200 × 96 tiles + the 278 px settings pane.
 * Footer: `R-Shift closes   ·   drag any tile onto the game to place it   ·   ⌘K search`
 *
 * Keyboard: arrows move the selection through the grid, Enter toggles the
 * highlighted mod, Escape closes (unless the search field has focus).
 */

import { useCallback, useMemo } from 'react';
import { isModOn, useVoidStore } from '@/store/store';
import { FILTER_TABS, MOD_ORDER, MOD_REGISTRY } from '@/registry';
import type { ModId } from '@/bridge/protocol';
import { IconWell, SearchField, Switch, FilterTabs } from '@/ui';
import type { IconName } from '@/icons/Icon';
import { Panel } from './Panel';
import { ModPane } from './ModPane';

const COLUMNS = 3;

export const MODS_FOOTER =
  'R-Shift closes   ·   drag any tile onto the game to place it   ·   ⌘K search';

/** Tiles matching the current filter tab and search query, in grid order. */
export function visibleMods(filter: string, query: string): ModId[] {
  const q = query.trim().toLowerCase();
  return MOD_ORDER.filter((id) => {
    const entry = MOD_REGISTRY[id];
    if (filter !== 'all' && entry.category !== filter) return false;
    if (!q) return true;
    return (
      entry.label.toLowerCase().includes(q) ||
      entry.description.toLowerCase().includes(q) ||
      id.includes(q)
    );
  });
}

function ModTile({ id, selected }: { id: ModId; selected: boolean }) {
  const entry = MOD_REGISTRY[id];
  const on = useVoidStore((s) => isModOn(s.loadout, id));
  const selectMod = useVoidStore((s) => s.selectMod);
  const toggleMod = useVoidStore((s) => s.toggleMod);
  const setRoute = useVoidStore((s) => s.setRoute);

  return (
    <button
      type="button"
      data-mod-id={id}
      aria-pressed={on}
      className={`mod-tile${selected ? ' mod-tile--selected' : ''}`}
      onClick={() => selectMod(id)}
      onDoubleClick={() => setRoute({ name: 'mod-settings', mod: id })}
    >
      <IconWell icon={entry.icon as IconName} size={34} on={on} />
      <span className="mod-tile__body">
        <span className="mod-tile__name">{entry.label}</span>
        <span className="mod-tile__meta">
          <Switch
            on={on}
            size="s"
            label={entry.label}
            onChange={(next) => {
              selectMod(id);
              toggleMod(id, next);
            }}
          />
          <span className="mod-tile__tag">{entry.category}</span>
        </span>
      </span>
    </button>
  );
}

export function ModsScreen() {
  const search = useVoidStore((s) => s.modSearch);
  const setSearch = useVoidStore((s) => s.setModSearch);
  const filter = useVoidStore((s) => s.modFilter);
  const setFilter = useVoidStore((s) => s.setModFilter);
  const selected = useVoidStore((s) => s.selectedMod);
  const selectMod = useVoidStore((s) => s.selectMod);
  const toggleMod = useVoidStore((s) => s.toggleMod);
  const closeMenu = useVoidStore((s) => s.closeMenu);

  const ids = useMemo(() => visibleMods(filter, search), [filter, search]);

  const rows = useMemo(() => {
    const out: ModId[][] = [];
    for (let i = 0; i < ids.length; i += COLUMNS) out.push(ids.slice(i, i + COLUMNS));
    return out;
  }, [ids]);

  /** Arrow keys walk the grid; Enter toggles. Bound on the panel, not globally. */
  const onKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      const delta =
        e.key === 'ArrowRight' ? 1 : e.key === 'ArrowLeft' ? -1 : e.key === 'ArrowDown' ? COLUMNS : e.key === 'ArrowUp' ? -COLUMNS : 0;
      if (delta !== 0) {
        if (ids.length === 0) return;
        e.preventDefault();
        const at = ids.indexOf(selected);
        const next = at < 0 ? 0 : Math.min(ids.length - 1, Math.max(0, at + delta));
        selectMod(ids[next]!);
        return;
      }
      if (e.key === 'Enter' && ids.includes(selected)) {
        e.preventDefault();
        toggleMod(selected, !isModOn(useVoidStore.getState().loadout, selected));
      }
    },
    [ids, selected, selectMod, toggleMod],
  );

  return (
    <div onKeyDown={onKeyDown}>
      <Panel
        title="Mods"
        header={
          <>
            <SearchField value={search} onChange={setSearch} placeholder="Search" width={230} />
            <FilterTabs tabs={FILTER_TABS} value={filter} onChange={setFilter} />
          </>
        }
        footer={MODS_FOOTER}
        onClose={closeMenu}
      >
        <div className="panel__body">
          {ids.length === 0 ? (
            <div className="mods-empty">No mod matches “{search}”.</div>
          ) : (
            <div className="mods-grid">
              {rows.map((row, index) => (
                <div className="mods-grid__row" key={index}>
                  {row.map((id) => (
                    <ModTile key={id} id={id} selected={id === selected} />
                  ))}
                </div>
              ))}
            </div>
          )}
          <ModPane id={selected} />
        </div>
      </Panel>
    </div>
  );
}
