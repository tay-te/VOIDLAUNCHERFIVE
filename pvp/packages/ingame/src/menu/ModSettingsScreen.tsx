/**
 * Overlay — Mod settings · frame `244:834`.
 *
 * `← Mods` back button, the mod's name, a `HUD · on in N loadouts` subtitle, an
 * `Enabled` switch and a `Keybind` chip in the header, the 908 × 168 LIVE
 * PREVIEW strip, then the APPEARANCE and BEHAVIOUR groups and the
 * `Edit position` / `Reset` actions.
 *
 * In-game changes are instant — the footer says so, and the bridge gives it for
 * free: `setModSetting` is synchronous and returns the value Java stored, which
 * is what the control binds to (bridge.json, `setModSetting_returns`).
 */

import {
  BackButton,
  Button,
  KeybindChip,
  Panel,
  PositionChips,
  SettingsGroup,
  SettingsRow,
  Slider,
  Swatches,
  Toggle,
} from '@/ui';
import {
  HUD_MOD_IDS,
  MOD_REGISTRY,
  type HUDAnchor,
  type HUDModId,
  type ModId,
} from '@/bridge/protocol';
import { MOD_CATEGORY, SETTING_ENUMS, SETTING_RANGES, modLabel } from '@/registry';
import { hudItem, useModSettings, useVoidStore, type SettingValue } from '@/store/store';
import { HudKeystrokes } from '@/hud/widgets';
import { SETTING_SUBTITLES, formatSetting, keybindLabel, settingLabel } from './settings-format';

export const MOD_SETTINGS_FOOTER = 'R-Shift closes   ·   changes apply instantly';

/**
 * The two swatch rows of the Appearance group.
 *
 * `keystrokes.key_color` and `keystrokes.pressed_color` are in `keystrokes_settings`
 * (mods.json), as enums of these swatch names rather than hex values — a name survives
 * a theme change, a hex would freeze the launcher's palette into the in-game bundle.
 * They are deliberately not in `SETTING_ENUMS`: that table drives the generic chip row,
 * and the frame draws these two as swatches.
 */
const KEY_COLOURS = [
  { id: 'shell', color: 'var(--bg-shell)', label: 'Shell' },
  { id: 'raised', color: 'var(--surface-2)', label: 'Raised' },
  { id: 'pill', color: 'var(--surface-3)', label: 'Pill' },
  { id: 'sky', color: 'var(--sky)', label: 'Sky' },
  { id: 'teal', color: 'var(--teal)', label: 'Teal' },
];

const PRESSED_COLOURS = [
  { id: 'accent', color: 'var(--accent)', label: 'Accent' },
  { id: 'sky', color: 'var(--sky)', label: 'Sky' },
  { id: 'warn', color: 'var(--warn)', label: 'Warn' },
  { id: 'fear', color: 'var(--danger)', label: 'Fear' },
  { id: 'teal', color: 'var(--teal)', label: 'Teal' },
];

/** Position chips, in the frame's order, and the anchor each one stores. */
const POSITIONS: Array<{ id: HUDAnchor; label: string }> = [
  { id: 'top-left', label: 'Top left' },
  { id: 'top-right', label: 'Top right' },
  { id: 'bottom-left', label: 'Bottom left' },
  { id: 'bottom-right', label: 'Bottom right' },
];

/** Keys that belong in APPEARANCE rather than BEHAVIOUR, in row order. */
const APPEARANCE_KEYS = [
  'scale',
  'opacity',
  'corner_radius',
  'gamma',
  'size',
  'thickness',
  'gap',
  'line_width',
  'fov_divisor',
  'decimals',
  'window_ms',
  'good_ms',
  'bad_ms',
];

export function ModSettingsScreen({ id }: { id: ModId }) {
  const settings = useModSettings(id);
  const setSetting = useVoidStore((s) => s.setSetting);
  const toggleMod = useVoidStore((s) => s.toggleMod);
  const resetMod = useVoidStore((s) => s.resetMod);
  const setRoute = useVoidStore((s) => s.setRoute);
  const setEditorTarget = useVoidStore((s) => s.setEditorTarget);
  const closeMenu = useVoidStore((s) => s.closeMenu);
  const captureKeybind = useVoidStore((s) => s.captureKeybind);
  const commitHud = useVoidStore((s) => s.commitHud);
  const placement = useVoidStore((s) => hudItem(s.loadout, id as HUDModId));
  const onInHowMany = useVoidStore(
    (s) =>
      s.library.filter((l) => (l.mods[id]?.on ?? MOD_REGISTRY[id].defaults.on) === true).length,
  );

  const isHud = (HUD_MOD_IDS as readonly string[]).includes(id);
  const keybindKey = 'keybind' in settings ? 'keybind' : 'key' in settings ? 'key' : null;

  const numericKeys = APPEARANCE_KEYS.filter((key) => key in settings && SETTING_RANGES[key]);
  const booleanKeys = Object.keys(settings).filter(
    (key) => key !== 'on' && typeof settings[key] === 'boolean',
  );
  const enumKeys = Object.keys(settings).filter(
    (key) => key !== keybindKey && SETTING_ENUMS[`${id}.${key}`] !== undefined,
  );

  const write = (key: string, value: SettingValue) => setSetting(id, key, value);

  return (
    <div className="panel-wrap">
      <Panel
        surface="overlay"
        animate
        className="mset-panel"
        onClose={closeMenu}
        footer={
          <>
            <span className="mset__footer-hint">{MOD_SETTINGS_FOOTER}</span>
            {/* The frame bottom-aligns `Edit position` / `Reset` with the footer
                hint on one band (actions 547 → 578, hint 563), so they belong to
                the footer row, not to the scrolling body above it. */}
            <div className="mset__actions">
              {isHud && (
                <Button
                  variant="accent"
                  icon="move"
                  onClick={() => {
                    setEditorTarget(id as HUDModId);
                    setRoute({ name: 'hud-editor' });
                  }}
                >
                  Edit position
                </Button>
              )}
              <Button variant="raised" icon="reset" onClick={() => resetMod(id)}>
                Reset
              </Button>
            </div>
          </>
        }
        subtitle={`${MOD_CATEGORY[id]}   ·   on in ${onInHowMany} loadout${
          onInHowMany === 1 ? '' : 's'
        }`}
        headerRight={
          <>
            <BackButton label="Mods" onClick={() => setRoute({ name: 'mods' })} />
            <h2 className="v-panel__title">{modLabel(id)}</h2>
            <span className="v-spacer" />
            <div className="mset__controls">
              <span className="mset__enabled">Enabled</span>
              <Toggle
                checked={settings.on === true}
                size="l"
                label={`${modLabel(id)} enabled`}
                onChange={(next) => toggleMod(id, next)}
              />
              {keybindKey && (
                <span className="mset__keybind">
                  <span className="mset__keybind-label">{settingLabel(keybindKey)}</span>
                  <KeybindChip
                    value={keybindLabel(settings[keybindKey] ?? null)}
                    onCapture={() => captureKeybind(id)}
                    onChange={(key) => write(keybindKey, key)}
                  />
                </span>
              )}
            </div>
          </>
        }
      >
        <div className="mset">
          <div className="mset__preview">
            <span className="mset__preview-caption">Live preview</span>
            <span className="mset__preview-meta">
              {[
                placement ? anchorLabel(placement.anchor) : '—',
                formatSetting('scale', Number(settings.scale ?? 1)),
                formatSetting('opacity', Number(settings.opacity ?? 1)),
              ].join('   ·   ')}
            </span>
            <div className="mset__preview-stage">
              {id === 'keystrokes' ? (
                <HudKeystrokes className="v-keystrokes--preview" />
              ) : (
                <span style={{ color: 'var(--text-secondary)', fontSize: 'var(--text-md-2)' }}>
                  {MOD_REGISTRY[id].description}
                </span>
              )}
            </div>
          </div>

          <div className="mset__groups">
            <SettingsGroup caption="Appearance">
              {numericKeys.map((key, index) => {
                const range = SETTING_RANGES[key]!;
                const value = Number(settings[key] ?? range.min);
                return (
                  <SettingsRow
                    key={key}
                    seam={index > 0}
                    title={settingLabel(key)}
                    value={formatSetting(key, value)}
                  >
                    <Slider
                      variant="wide"
                      hideLabels
                      ariaLabel={settingLabel(key)}
                      value={value}
                      min={range.min}
                      max={range.max}
                      step={range.step}
                      onChange={(next) => write(key, next)}
                    />
                  </SettingsRow>
                );
              })}
              {id === 'keystrokes' && (
                <>
                  <SettingsRow
                    seam
                    title="Key colour"
                    sub="Background of an unpressed key"
                  >
                    <Swatches
                      swatches={KEY_COLOURS}
                      value={String(settings.key_color ?? 'shell')}
                      onChange={(next) => write('key_color', next)}
                    />
                  </SettingsRow>
                  <SettingsRow
                    seam
                    title="Pressed colour"
                    sub="Follows the loadout accent by default"
                  >
                    <Swatches
                      swatches={PRESSED_COLOURS}
                      value={String(settings.pressed_color ?? 'accent')}
                      onChange={(next) => write('pressed_color', next)}
                    />
                  </SettingsRow>
                </>
              )}
            </SettingsGroup>

            <SettingsGroup caption="Behaviour">
              {booleanKeys.map((key, index) => (
                <SettingsRow
                  key={key}
                  seam={index > 0}
                  title={settingLabel(key)}
                  sub={SETTING_SUBTITLES[key]}
                >
                  <Toggle
                    checked={settings[key] === true}
                    size="l"
                    label={settingLabel(key)}
                    onChange={(next) => write(key, next)}
                  />
                </SettingsRow>
              ))}
              {enumKeys.map((key) => (
                <SettingsRow key={key} seam title={settingLabel(key)}>
                  <PositionChips
                    options={(SETTING_ENUMS[`${id}.${key}`] ?? []).map((option) => ({
                      id: option,
                      label: option.charAt(0).toUpperCase() + option.slice(1).replace(/_/g, ' '),
                    }))}
                    value={String(settings[key] ?? '')}
                    onChange={(next) => write(key, next)}
                  />
                </SettingsRow>
              ))}
              {isHud && placement && (
                <SettingsRow seam title="Position">
                  <PositionChips
                    options={POSITIONS}
                    value={placement.anchor}
                    onChange={(next) =>
                      commitHud(
                        id as HUDModId,
                        next as HUDAnchor,
                        next.endsWith('right') ? -25 : 25,
                        next.startsWith('bottom') ? -25 : 25,
                        placement.scale ?? 1,
                      )
                    }
                  />
                </SettingsRow>
              )}
            </SettingsGroup>
          </div>

        </div>
      </Panel>
    </div>
  );
}

function anchorLabel(anchor: HUDAnchor): string {
  const words = anchor.split('-');
  const first = words[0]!;
  return [first.charAt(0).toUpperCase() + first.slice(1), ...words.slice(1)].join(' ');
}
