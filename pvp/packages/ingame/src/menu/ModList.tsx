/**
 * The list layout of the Mods screen.
 *
 * `layout` is the other of contract §7's two independent controls, and this is its
 * whole reason to exist: a full-width row that shows what a tile cannot fit — the
 * mod's **description**, its category, its keybind, where it sits on screen and how
 * big it is — all at once, for every mod, without selecting anything.
 *
 * Columns that do not apply to a mod render an em-dash rather than disappearing.
 * A gap where a value would be is information: it says Fullbright has no keybind
 * and Toggle sprint has no place on the HUD. Collapsing the column instead would
 * make every row a different shape and cost the comparison the list is for.
 */

import { MOD_REGISTRY, type HUDModId, type ModId } from '@/bridge/protocol';
import { MOD_CATEGORY, SETTING_RANGES, hueStyle, modLabel } from '@/registry';
import { hudItem, isModOn, useModSettings, useVoidStore } from '@/store/store';
import { Icon, MOD_ICONS, Toggle, cx } from '@/ui';
import { CellMeter } from './CellMeter';
import { anchorShortLabel } from './ModSettingsScreen';
import { formatSetting, keybindLabel } from './settings-format';

/** What a column prints when the mod has nothing to put in it. */
export const EM_DASH = '—';

function Empty(): React.ReactElement {
  return <span className="modrow__empty">{EM_DASH}</span>;
}

function ModRow({ id, selected }: { id: ModId; selected: boolean }): React.ReactElement {
  const settings = useModSettings(id);
  const on = useVoidStore((s) => isModOn(s.loadout, id));
  const placement = useVoidStore((s) => hudItem(s.loadout, id as HUDModId));
  const selectMod = useVoidStore((s) => s.selectMod);
  const toggleMod = useVoidStore((s) => s.toggleMod);

  const keybindKey = 'keybind' in settings ? 'keybind' : 'key' in settings ? 'key' : null;
  const keybind = keybindKey ? keybindLabel(settings[keybindKey] ?? null) : null;
  const scale = SETTING_RANGES.scale!;

  return (
    <div
      className={cx('modrow', selected && 'modrow--selected', !on && 'modrow--off')}
      data-mod-id={id}
      style={hueStyle(id)}
    >
      <button
        type="button"
        className="modrow__select"
        aria-pressed={selected}
        aria-label={modLabel(id)}
        onClick={() => selectMod(id)}
      />
      <span className="modrow__icon">
        <Icon name={MOD_ICONS[id]} size={16} />
      </span>
      <span className="modrow__name">{modLabel(id)}</span>
      <span className="modrow__desc">{MOD_REGISTRY[id].description}</span>
      <span className="modrow__cat">{MOD_CATEGORY[id]}</span>
      <span className="modrow__key">
        {keybind === null ? (
          <Empty />
        ) : keybind === 'None' ? (
          // The mod takes a keybind and has not been given one. That is not the same
          // as having no keybind at all, and the em-dash is reserved for the latter —
          // otherwise the column stops meaning anything.
          <span className="modrow__unbound">None</span>
        ) : (
          <span className="modrow__kbd">{keybind}</span>
        )}
      </span>
      <span className="modrow__pos">
        {placement ? anchorShortLabel(placement.anchor) : <Empty />}
      </span>
      <span className="modrow__scale">
        {'scale' in settings ? (
          <CellMeter
            size="sm"
            cells={8}
            label={`${modLabel(id)} scale`}
            value={Number(settings.scale ?? 1)}
            min={scale.min}
            max={scale.max}
            step={scale.step}
            format={(next) => formatSetting('scale', next)}
          />
        ) : (
          <Empty />
        )}
      </span>
      <span className="modrow__switch">
        <Toggle
          checked={on}
          size="m"
          label={`${modLabel(id)} enabled`}
          onChange={(next) => toggleMod(id, next)}
        />
      </span>
    </div>
  );
}

export interface ModListProps {
  ids: ModId[];
  selected: ModId;
}

export function ModList({ ids, selected }: ModListProps): React.ReactElement {
  return (
    <div className="modlist">
      <div className="modlist__head">
        <span className="modrow__icon" />
        <span className="modrow__name">Name</span>
        <span className="modrow__desc">Description</span>
        <span className="modrow__cat">Category</span>
        <span className="modrow__key">Keybind</span>
        <span className="modrow__pos">Position</span>
        <span className="modrow__scale">Scale</span>
        <span className="modrow__switch">On</span>
      </div>
      <div className="modlist__rows">
        {ids.map((id) => (
          <ModRow key={id} id={id} selected={id === selected} />
        ))}
      </div>
    </div>
  );
}
