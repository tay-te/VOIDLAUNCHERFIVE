/**
 * Mods — `244:110`.
 *
 * Every part of this screen is `@void/ui`'s: `Panel`, `SearchBar`, `FilterTabs`,
 * `ModGrid`, `ModTile`, `ModSettingsPanel`, `KeystrokesPreview`, `Slider`,
 * `KeybindChip`, `Swatches`, `PositionChips`, `EditPositionButton`. What is local is
 * the *wiring*: which control a given setting gets (`local/registry`) and where a
 * change goes. Here it goes to `loadouts_update`, which is why the footer says
 * **"Changes apply on next launch"** — in game the same edit is instant, because Java
 * is authoritative for live state (§6.1) and the launcher is not.
 */

import {
  EditPositionButton,
  FilterTabs,
  KeybindChip,
  KeystrokesPreview,
  ModGrid,
  ModSettingsPanel,
  ModSettingsRow,
  ModTile,
  MOD_ICONS,
  Panel,
  PositionChips,
  SearchBar,
  Slider,
  Swatches,
  Toggle,
} from '@void/ui';
import { useMemo, useState } from 'react';

import { captureKey, prettyKey } from '../local/keys';
import type { Loadout, ModId } from '../local/protocol';
import {
  FILTER_TABS,
  MOD_GRID_ORDER,
  MOD_REGISTRY,
  type FilterTab,
  type SettingSpec,
  categoryOf,
  effectiveState,
  formatSetting,
  matchesTab,
  settingsFor,
} from '../local/registry';
import { useLoadouts } from '../stores/loadouts';
import { useUi } from '../stores/ui';

/** The frames' pressed keys — `W`, `D` and `LMB` — as a still, not a live capture. */
const PREVIEW_KEYS = { w: true, d: true, lmb: true };

/** The colour row of the mod-settings frame, as ids the schema round-trips. */
const COLOURS = [
  { id: '#FFFFFFFF', color: '#FFFFFF', label: 'White' },
  { id: '#9F8BFFFF', color: '#9F8BFF', label: 'Accent' },
  { id: '#3DD68CFF', color: '#3DD68C', label: 'Green' },
  { id: '#D9A93AFF', color: '#D9A93A', label: 'Amber' },
  { id: '#C05B54FF', color: '#C05B54', label: 'Red' },
  { id: '#4D87CDFF', color: '#4D87CD', label: 'Blue' },
];

function SettingControl({
  spec,
  value,
  onChange,
}: {
  spec: SettingSpec;
  value: unknown;
  onChange: (next: unknown) => void;
}) {
  switch (spec.control) {
    case 'switch':
      return (
        <Toggle
          size="m"
          checked={value === true}
          onChange={onChange}
          label={spec.label}
        />
      );
    case 'select':
      return (
        <PositionChips
          aria-label={spec.label}
          value={String(value ?? spec.options?.[0] ?? '')}
          options={(spec.options ?? []).map((option) => ({
            id: option,
            label: option.replace(/_/g, ' '),
          }))}
          onChange={onChange}
        />
      );
    case 'keybind':
      return (
        <KeybindChip
          aria-label={spec.label}
          value={prettyKey(String(value ?? 'NONE'))}
          onCapture={captureKey}
          onChange={onChange}
        />
      );
    case 'color':
      return (
        <Swatches
          aria-label={spec.label}
          swatches={COLOURS}
          value={String(value ?? '#FFFFFFFF').toUpperCase()}
          onChange={onChange}
        />
      );
    case 'slider':
      return null; // sliders carry their own label row; see ModSettings below
  }
}

/**
 * The 278px pane beside the grid.
 *
 * `ModSettingsPanel` is a shell — the registry decides which rows a mod has, which is
 * the split `@void/ui`'s docs ask for: the package owns the chrome, `@void/protocol`
 * owns what a mod is.
 */
function ModSettings({
  id,
  loadout,
  onChange,
  onEditPosition,
}: {
  id: ModId;
  loadout: Pick<Loadout, 'mods'>;
  onChange: (next: Record<string, unknown>) => void;
  onEditPosition?: () => void;
}) {
  const entry = MOD_REGISTRY[id];
  const state = effectiveState(loadout, id);
  const on = state.on === true;

  return (
    <ModSettingsPanel
      className="mods__pane"
      title={entry.label}
      on={on}
      onToggle={(next) => onChange({ on: next })}
    >
      {id === 'keystrokes' ? (
        <KeystrokesPreview keys={PREVIEW_KEYS} />
      ) : (
        <p className="mods__desc">{entry.description}</p>
      )}

      <div className={`mods__settings${on ? '' : ' is-disabled'}`}>
        {settingsFor(id).map((spec) =>
          spec.control === 'slider' ? (
            <Slider
              key={spec.key}
              label={spec.label}
              readout={formatSetting(spec, state[spec.key])}
              value={Number(state[spec.key] ?? spec.min ?? 0)}
              min={spec.min ?? 0}
              max={spec.max ?? 1}
              step={spec.step ?? 0.01}
              onChange={(next) => onChange({ [spec.key]: next })}
            />
          ) : (
            <ModSettingsRow key={spec.key} label={spec.label}>
              <SettingControl
                spec={spec}
                value={state[spec.key]}
                onChange={(next) => onChange({ [spec.key]: next })}
              />
            </ModSettingsRow>
          ),
        )}
      </div>

      {entry.kind === 'hud' ? (
        <EditPositionButton onClick={onEditPosition} disabled={!onEditPosition} />
      ) : (
        <p className="mods__note">
          {entry.hypixel_safe === 'grey'
            ? 'Not Hypixel-safe — turn it off before joining ranked.'
            : 'Applies to the whole loadout.'}
        </p>
      )}
    </ModSettingsPanel>
  );
}

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
      headerRight={
        <>
          <SearchBar
            variant="panel"
            placeholder="Search"
            value={query}
            onChange={setQuery}
          />
          <FilterTabs
            label="Mod category"
            tabs={FILTER_TABS.map((id) => ({ id, label: id }))}
            value={tab}
            onChange={(id) => setTab(id as FilterTab)}
          />
        </>
      }
      footer={
        <>
          Changes apply on next launch &nbsp;·&nbsp; drag any tile onto the HUD editor to place it
          &nbsp;·&nbsp; ⌘K search
        </>
      }
    >
      <ModGrid>
        {visible.map((id) => {
          const entry = MOD_REGISTRY[id];
          return (
            <ModTile
              key={id}
              name={entry.label}
              category={categoryOf(id)}
              icon={MOD_ICONS[id]}
              on={effectiveState(active, id).on === true}
              selected={id === selectedMod}
              onSelect={() => selectMod(id)}
              onToggle={(next) => void setMod(id, { on: next })}
              // The footer promises "drag any tile onto the HUD editor to place it".
              // The editor is in game, so what travels is the mod id.
              draggable={entry.kind === 'hud'}
              onDragStart={(event) =>
                event.dataTransfer.setData('application/x-void-mod', id)
              }
            />
          );
        })}
        {visible.length === 0 ? <p className="mods__empty">No mod matches “{query}”.</p> : null}
      </ModGrid>

      <ModSettings
        id={selectedMod}
        loadout={active}
        onChange={(next) => void setMod(selectedMod, next)}
      />
    </Panel>
  );
}
