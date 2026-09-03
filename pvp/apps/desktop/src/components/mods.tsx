/**
 * The mod tile grid and the mod settings pane.
 *
 * These are the two components the launcher's Mods screen and the in-game Mods panel
 * share (§9: "one React codebase, two bundles"), so they take a loadout and a plain
 * `onChange` and know nothing about stores or `invoke` — the caller decides whether a
 * change goes to `loadouts_update` (launcher, applies next launch) or to
 * `void.setModSetting` (in game, applies instantly).
 *
 * TODO(integrate): `@void/ui` owns these two, for exactly that reason. Its `src/` has a
 * `components/mods.tsx` but no built `dist` yet, so importing it would break
 * `pnpm typecheck` here. The swap is an import change: nothing below reaches outside
 * `./icons`, `./index` and the registry.
 */

import type { Loadout, ModId } from '../local/protocol';
import {
  MOD_REGISTRY,
  type SettingSpec,
  categoryOf,
  effectiveState,
  formatSetting,
  settingsFor,
} from '../local/registry';
import { MOD_ICONS, MoveIcon } from './icons';
import {
  Button,
  ColorSwatches,
  IconWell,
  KeybindChip,
  Segmented,
  Slider,
  Switch,
  Tag,
} from './index';

export function ModTile({
  id,
  loadout,
  selected,
  onSelect,
  onToggle,
}: {
  id: ModId;
  loadout: Pick<Loadout, 'mods'>;
  selected: boolean;
  onSelect: () => void;
  onToggle: (next: boolean) => void;
}) {
  const entry = MOD_REGISTRY[id];
  const Icon = MOD_ICONS[id] ?? MoveIcon;
  const on = effectiveState(loadout, id).on === true;

  return (
    <div
      className={`tile${selected ? ' is-selected' : ''}`}
      // Drag source: the Mods footer promises "drag any tile onto the HUD editor to
      // place it". The editor is in-game, so what travels is the mod id.
      draggable={entry.kind === 'hud'}
      onDragStart={(e) => e.dataTransfer.setData('application/x-void-mod', id)}
    >
      <button type="button" className="tile__hit" onClick={onSelect} aria-label={`Select ${entry.label}`}>
        <IconWell icon={Icon} active={on} />
        <span className="tile__text">
          <span className="tile__name">{entry.label}</span>
          <span className="tile__meta">
            <Tag tone={entry.hypixel_safe === 'grey' ? 'warn' : 'muted'}>{categoryOf(id)}</Tag>
          </span>
        </span>
      </button>
      <div className="tile__switch">
        <Switch size="s" checked={on} onChange={onToggle} label={`${entry.label} on`} />
      </div>
    </div>
  );
}

/**
 * The Keystrokes preview of `252:189` — six keycaps that light up. Rendered for the
 * keystrokes mod only; every other mod gets its registry description instead.
 */
function KeystrokesPreview({ pressed }: { pressed: readonly string[] }) {
  const cap = (key: string, style: React.CSSProperties) => (
    <span key={key} className={`keycap${pressed.includes(key) ? ' is-pressed' : ''}`} style={style}>
      {key}
    </span>
  );
  return (
    <div className="keys-preview" aria-hidden="true">
      {cap('W', { left: 108, top: 13 })}
      {cap('A', { left: 76, top: 45 })}
      {cap('S', { left: 108, top: 45 })}
      {cap('D', { left: 140, top: 45 })}
      {cap('LMB', { left: 76, top: 77, width: 44 })}
      {cap('RMB', { left: 124, top: 77, width: 44 })}
    </div>
  );
}

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
      return <Switch size="l" checked={value === true} onChange={onChange} label={spec.label} />;
    case 'slider':
      return (
        <Slider
          label={spec.label}
          value={Number(value ?? spec.min ?? 0)}
          min={spec.min ?? 0}
          max={spec.max ?? 1}
          step={spec.step ?? 0.01}
          display={formatSetting(spec, value)}
          onChange={onChange}
        />
      );
    case 'select':
      return (
        <Segmented
          label={spec.label}
          value={String(value ?? spec.options?.[0] ?? '')}
          options={spec.options ?? []}
          onChange={onChange}
        />
      );
    case 'keybind':
      return <KeybindChip label={spec.label} value={String(value ?? 'NONE')} onChange={onChange} />;
    case 'color':
      return <ColorSwatches label={spec.label} value={String(value ?? '#FFFFFFFF')} onChange={onChange} />;
  }
}

export function ModSettingsPane({
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
    <div className="mod-pane">
      <header className="mod-pane__head">
        <h2 className="mod-pane__title">{entry.label}</h2>
        <Switch size="m" checked={on} onChange={(next) => onChange({ on: next })} label={`${entry.label} on`} />
      </header>

      {id === 'keystrokes' ? (
        <KeystrokesPreview pressed={['W', 'D', 'LMB']} />
      ) : (
        <p className="mod-pane__desc">{entry.description}</p>
      )}

      <div className={`mod-pane__settings${on ? '' : ' is-disabled'}`}>
        {settingsFor(id).map((spec) =>
          spec.control === 'slider' ? (
            <SettingControl
              key={spec.key}
              spec={spec}
              value={state[spec.key]}
              onChange={(next) => onChange({ [spec.key]: next })}
            />
          ) : (
            <div className="mod-pane__row" key={spec.key}>
              <span className="mod-pane__row-label">{spec.label}</span>
              <SettingControl
                spec={spec}
                value={state[spec.key]}
                onChange={(next) => onChange({ [spec.key]: next })}
              />
            </div>
          ),
        )}
      </div>

      <div className="mod-pane__spacer" />

      {entry.kind === 'hud' ? (
        <Button variant="accent" icon={MoveIcon} full onClick={onEditPosition} disabled={!onEditPosition}>
          Edit position
        </Button>
      ) : (
        <p className="mod-pane__note">
          {entry.hypixel_safe === 'grey'
            ? 'Not Hypixel-safe — turn it off before joining ranked.'
            : 'Applies to the whole loadout.'}
        </p>
      )}
    </div>
  );
}
