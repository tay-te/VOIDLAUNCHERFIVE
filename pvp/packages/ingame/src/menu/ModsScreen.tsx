/**
 * Overlay — Mods · frame `244:538`.
 *
 * Header: title, 230 px search, `All / HUD / PvP / Visual / Utility` tabs, close.
 * Body: the 3 × 4 grid of 200 × 96 tiles + the 278 px settings pane, which shows
 * whichever tile is selected.
 * Footer: `R-Shift closes   ·   drag any tile onto the game to place it   ·   ⌘K search`
 *
 * Keyboard: arrows walk the grid, Enter toggles the highlighted mod, Escape
 * closes — unless the search field has focus, which `MenuLayer` handles.
 */

import { useCallback, useMemo } from 'react';
import { isModOn, useVoidStore } from '@/store/store';
import { FILTER_TABS, MOD_CATEGORY, MOD_ORDER, modLabel } from '@/registry';
import { MOD_REGISTRY, type ModId } from '@/bridge/protocol';
import { FilterTabs, Icon, ModGrid, ModTile, MOD_ICONS, Panel, SearchBar } from '@/ui';
import { ModPane } from './ModPane';

const COLUMNS = 3;

export const MODS_FOOTER =
  'R-Shift closes   ·   drag any tile onto the game to place it   ·   ⌘K search';

/** Tiles matching the current filter tab and search query, in grid order. */
export function visibleMods(filter: string, query: string): ModId[] {
  const q = query.trim().toLowerCase();
  return MOD_ORDER.filter((id) => {
    if (filter !== 'all' && MOD_CATEGORY[id] !== filter) return false;
    if (!q) return true;
    return (
      modLabel(id).toLowerCase().includes(q) ||
      MOD_REGISTRY[id].description.toLowerCase().includes(q) ||
      id.includes(q)
    );
  });
}

function Tile({ id, selected }: { id: ModId; selected: boolean }) {
  const on = useVoidStore((s) => isModOn(s.loadout, id));
  const selectMod = useVoidStore((s) => s.selectMod);
  const toggleMod = useVoidStore((s) => s.toggleMod);
  const setRoute = useVoidStore((s) => s.setRoute);

  return (
    <ModTile
      data-mod-id={id}
      name={modLabel(id)}
      category={MOD_CATEGORY[id]}
      icon={MOD_ICONS[id]}
      on={on}
      selected={selected}
      draggable
      onSelect={() => selectMod(id)}
      onToggle={(next) => {
        selectMod(id);
        toggleMod(id, next);
      }}
      // Double-click opens the mod's full settings screen (frame 244:834); the
      // pane on the right is the single-click view.
      onDoubleClick={() => setRoute({ name: 'mod-settings', mod: id })}
    />
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

  const onKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      const delta =
        e.key === 'ArrowRight'
          ? 1
          : e.key === 'ArrowLeft'
            ? -1
            : e.key === 'ArrowDown'
              ? COLUMNS
              : e.key === 'ArrowUp'
                ? -COLUMNS
                : 0;
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
    <div className="panel-wrap" onKeyDown={onKeyDown}>
      <Panel
        surface="overlay"
        animate
        className="mods-panel"
        title="Mods"
        onClose={closeMenu}
        footer={MODS_FOOTER}
        headerRight={
          <>
            <SearchBar variant="panel" value={search} onChange={setSearch} placeholder="Search" />
            <FilterTabs tabs={FILTER_TABS} value={filter} onChange={setFilter} label="Mod filter" />
            <span className="v-spacer" />
          </>
        }
      >
        <div className="mods-body">
          {ids.length === 0 ? (
            <div className="mods-empty">
              <Icon name="search" size={14} />
              &nbsp;No mod matches “{search}”.
            </div>
          ) : (
            <ModGrid>
              {ids.map((id) => (
                <Tile key={id} id={id} selected={id === selected} />
              ))}
            </ModGrid>
          )}
          <ModPane id={selected} />
        </div>
      </Panel>
    </div>
  );
}
