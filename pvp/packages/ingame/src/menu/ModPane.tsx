/**
 * ModSettingsPanel — the 278 × 414 pane on the right of the Mods frame
 * (244:784): header row with the M switch, live preview, Scale / Opacity
 * sliders, the Keybind row, a spacer and `Edit position`.
 *
 * `@void/ui` supplies the shell and the controls; what this file owns is which
 * settings a given mod exposes, which is the registry's business, not the
 * component library's.
 */

import {
  EditPositionButton,
  KeybindChip,
  KeystrokesPreview,
  ModSettingsPanel,
  ModSettingsRow,
  Slider,
} from '@/ui';
import { HUD_MOD_IDS, MOD_REGISTRY, type HUDModId, type ModId } from '@/bridge/protocol';
import { SETTING_RANGES, modLabel } from '@/registry';
import { useModSettings, useVoidStore } from '@/store/store';
import { formatSetting, keybindLabel, settingLabel } from './settings-format';

/** Sliders the pane shows, in the order the frame lists them. */
const PANE_SLIDERS = ['scale', 'opacity'] as const;

export function ModPane({ id }: { id: ModId }) {
  const settings = useModSettings(id);
  const keys = useVoidStore((s) => s.keys);
  const setSetting = useVoidStore((s) => s.setSetting);
  const toggleMod = useVoidStore((s) => s.toggleMod);
  const setRoute = useVoidStore((s) => s.setRoute);
  const setEditorTarget = useVoidStore((s) => s.setEditorTarget);
  const captureKeybind = useVoidStore((s) => s.captureKeybind);

  const isHud = (HUD_MOD_IDS as readonly string[]).includes(id);
  const keybindKey = 'keybind' in settings ? 'keybind' : 'key' in settings ? 'key' : null;

  return (
    <ModSettingsPanel
      title={modLabel(id)}
      on={settings.on === true}
      onToggle={(next) => toggleMod(id, next)}
    >
      {id === 'keystrokes' ? (
        <KeystrokesPreview
          keys={{
            w: keys.w === 1,
            a: keys.a === 1,
            s: keys.s === 1,
            d: keys.d === 1,
            lmb: keys.lmb === 1,
            rmb: keys.rmb === 1,
          }}
        />
      ) : (
        <div className="mod-pane-blurb">{MOD_REGISTRY[id].description}</div>
      )}

      {PANE_SLIDERS.filter((key) => key in settings).map((key) => {
        const range = SETTING_RANGES[key]!;
        const value = Number(settings[key] ?? range.min);
        return (
          <Slider
            key={key}
            variant="compact"
            label={settingLabel(key)}
            readout={formatSetting(key, value)}
            value={value}
            min={range.min}
            max={range.max}
            step={range.step}
            onChange={(next) => setSetting(id, key, next)}
          />
        );
      })}

      {keybindKey && (
        <ModSettingsRow label={settingLabel(keybindKey)}>
          <KeybindChip
            value={keybindLabel(settings[keybindKey] ?? null)}
            onCapture={() => captureKeybind(id)}
            // bridge.json: the capture call does not store the key — the UI does.
            onChange={(key) => setSetting(id, keybindKey, key)}
          />
        </ModSettingsRow>
      )}

      <span className="v-spacer" />

      {isHud && (
        <EditPositionButton
          onClick={() => {
            setEditorTarget(id as HUDModId);
            setRoute({ name: 'hud-editor' });
          }}
        />
      )}
    </ModSettingsPanel>
  );
}
