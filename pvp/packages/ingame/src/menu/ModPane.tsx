/**
 * ModSettingsPanel — the 278 × 414 pane on the right of the Mods frame
 * (244:784). Header row, live preview, Scale / Opacity sliders, Keybind row,
 * spacer, `Edit position`.
 */

import { modSettings, useVoidStore } from '@/store/store';
import { MOD_REGISTRY, SETTING_RANGES } from '@/registry';
import { HUD_MOD_IDS } from '@/registry';
import type { HUDModId, ModId } from '@/bridge/protocol';
import { Button, KbdChip, Slider, Switch } from '@/ui';
import { formatSetting, keybindLabel, settingLabel } from './settings-format';

/** The small 200 × 128 keycap board, absolutely placed exactly as authored. */
export function KeystrokesPreview() {
  const keys = useVoidStore((s) => s.keys);
  const cap = (label: string, on: boolean, left: number, top: number, wide = false) => (
    <span
      key={label + left}
      className={`keys-preview__cap${wide ? ' keys-preview__cap--wide' : ''}${
        on ? ' keys-preview__cap--on' : ''
      }`}
      style={{ left, top }}
    >
      {label}
    </span>
  );
  return (
    <div className="keys-preview">
      {cap('W', keys.w === 1, 108 - 22, 13)}
      {cap('A', keys.a === 1, 76 - 22, 45)}
      {cap('S', keys.s === 1, 108 - 22, 45)}
      {cap('D', keys.d === 1, 140 - 22, 45)}
      {cap('LMB', keys.lmb === 1, 76 - 22, 77, true)}
      {cap('RMB', keys.rmb === 1, 124 - 22, 77, true)}
    </div>
  );
}

/** Keys with a slider in the pane, in the order the frame lists them. */
const PANE_SLIDERS = ['scale', 'opacity'] as const;

export function ModPane({ id }: { id: ModId }) {
  const entry = MOD_REGISTRY[id];
  const settings = useVoidStore((s) => modSettings(s.loadout, id));
  const setSetting = useVoidStore((s) => s.setSetting);
  const toggleMod = useVoidStore((s) => s.toggleMod);
  const setRoute = useVoidStore((s) => s.setRoute);
  const setEditorTarget = useVoidStore((s) => s.setEditorTarget);
  const captureKeybind = useVoidStore((s) => s.captureKeybind);
  const capturing = useVoidStore((s) => s.capturingKeybind === id);

  const isHud = (HUD_MOD_IDS as readonly string[]).includes(id);
  const keybindKey = 'keybind' in settings ? 'keybind' : 'key' in settings ? 'key' : null;

  return (
    <div className="mod-pane pane">
      <div className="pane__row">
        <span className="mod-pane__title">{entry.label}</span>
        <Switch
          on={settings.on === true}
          size="m"
          label={`${entry.label} enabled`}
          onChange={(next) => toggleMod(id, next)}
        />
      </div>

      {id === 'keystrokes' ? (
        <KeystrokesPreview />
      ) : (
        <div className="keys-preview" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 16, textAlign: 'center' }}>
          <span style={{ fontSize: 'var(--text-md-2)', color: 'var(--text-secondary)' }}>
            {entry.description}
          </span>
        </div>
      )}

      {PANE_SLIDERS.filter((key) => key in settings).map((key) => {
        const range = SETTING_RANGES[key]!;
        const value = Number(settings[key] ?? range.min);
        return (
          <div className="mod-pane__slider" key={key}>
            <div className="mod-pane__slider-head">
              <span className="mod-pane__slider-label">{settingLabel(key)}</span>
              <span className="mod-pane__slider-value">{formatSetting(key, value)}</span>
            </div>
            <Slider
              variant="compact"
              value={value}
              min={range.min}
              max={range.max}
              step={range.step}
              label={settingLabel(key)}
              onChange={(next) => setSetting(id, key, next)}
            />
          </div>
        );
      })}

      {keybindKey && (
        <div className="mod-pane__keybind">
          <span className="mod-pane__keybind-label">{settingLabel(keybindKey)}</span>
          <button type="button" onClick={() => void captureKeybind(id, keybindKey)}>
            <KbdChip variant="keybind">
              {capturing ? 'Press a key…' : keybindLabel(settings[keybindKey] ?? null)}
            </KbdChip>
          </button>
        </div>
      )}

      <div className="pane__spacer" />

      {isHud && (
        <Button
          variant="accent"
          icon="move"
          iconSize={13}
          full
          onClick={() => {
            setEditorTarget(id as HUDModId);
            setRoute({ name: 'hud-editor' });
          }}
        >
          Edit position
        </Button>
      )}
    </div>
  );
}
