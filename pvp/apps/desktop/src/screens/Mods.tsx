/**
 * Mods — `244:110`.
 *
 * The tile grid and the settings pane are the same components the in-game panel uses
 * (`components/mods.tsx`); what differs is where a change goes. Here it goes to
 * `loadouts_update`, which is why the footer says **"Changes apply on next launch"** —
 * in game the same edit is instant, because Java is authoritative for live state
 * (§6.1) and the launcher is not.
 */

import { useMemo, useState } from 'react';

import { FilterTabs, Panel, SearchField } from '../components';
import { ModSettingsPane, ModTile } from '../components/mods';
import type { ModId } from '../local/protocol';
import {
  FILTER_TABS,
  MOD_GRID_ORDER,
  MOD_REGISTRY,
  type FilterTab,
  categoryOf,
  matchesTab,
} from '../local/registry';
import { useLoadouts } from '../stores/loadouts';
import { useUi } from '../stores/ui';

export function ModsScreen() {
  const active = useLoadouts((s) => s.active);
  const setMod = useLoadouts((s) => s.setMod);
  const selectedMod = useUi((s) => s.selectedMod) as ModId;
  const selectMod = useUi((s) => s.selectMod);

  const [tab, setTab] = useState<FilterTab>('All');
  const [query, setQuery] = useState('');

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    return MOD_GRID_ORDER.filter((id) => {
      if (!matchesTab(id, tab)) return false;
      if (!q) return true;
      const entry = MOD_REGISTRY[id];
      return (
        entry.label.toLowerCase().includes(q) ||
        entry.description.toLowerCase().includes(q) ||
        categoryOf(id).toLowerCase().includes(q)
      );
    });
  }, [tab, query]);

  if (!active) return <Panel title="Mods">{null}</Panel>;

  return (
    <Panel
      title="Mods"
      controls={
        <>
          <SearchField value={query} onChange={setQuery} placeholder="Search" width={230} />
          <FilterTabs tabs={FILTER_TABS} value={tab} onChange={setTab} />
        </>
      }
      footer={
        <>
          Changes apply on next launch &nbsp;·&nbsp; drag any tile onto the HUD editor to place it
          &nbsp;·&nbsp; ⌘K search
        </>
      }
    >
      <div className="mods">
        <div className="mods__grid">
          {visible.map((id) => (
            <ModTile
              key={id}
              id={id}
              loadout={active}
              selected={id === selectedMod}
              onSelect={() => selectMod(id)}
              onToggle={(next) => void setMod(id, { on: next })}
            />
          ))}
          {visible.length === 0 ? <p className="mods__empty">No mod matches “{query}”.</p> : null}
        </div>

        <ModSettingsPane
          id={selectedMod}
          loadout={active}
          onChange={(next) => void setMod(selectedMod, next)}
        />
      </div>
    </Panel>
  );
}
