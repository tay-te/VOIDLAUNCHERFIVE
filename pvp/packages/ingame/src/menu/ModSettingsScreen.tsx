/**
 * Overlay — Mod settings · frame `244:834`.
 *
 * `← Mods` back button, title + `HUD · on in N loadouts` subtitle, an
 * `Enabled` switch and a `Keybind` chip in the header, a 908 × 168 LIVE PREVIEW
 * strip, then two 448 × 290 groups — APPEARANCE and BEHAVIOUR — and the
 * `Edit position` / `Reset` actions.
 *
 * In-game changes are instant (the footer says so), which the bridge gives for
 * free: `setModSetting` is synchronous and returns the value Java stored, and
 * that returned value is what the control binds to.
 */

import { hudItem, modSettings, useVoidStore } from '@/store/store';
import { HUD_MOD_IDS, MOD_REGISTRY, SETTING_RANGES } from '@/registry';
import type { HUDAnchor, HUDModId, ModId, ModSettingValue } from '@/bridge/protocol';
import { Button, KbdChip, Slider, Switch } from '@/ui';
import { Icon } from '@/icons/Icon';
import { Panel } from './Panel';
import { KeystrokesWidget } from '@/hud/widgets';
import {
  SETTING_SUBTITLES,
  formatSetting,
  keybindLabel,
  settingLabel,
} from './settings-format';

export const MOD_SETTINGS_FOOTER = 'R-Shift closes   ·   changes apply instantly';

/* The two swatch rows of the Appearance group. Neither `key_color` nor
   `pressed_color` exists in `keystrokes_settings`; the frame draws them, so they
   are written through `setModSetting` with those keys and flagged for
   reconciliation in schema/mods.json. */
const KEY_COLOURS = ['#0a0b0c', '#22262b', '#2a2f35', '#4d87cd', '#2fb8a6'];
const PRESSED_COLOURS = ['#9f8bff', '#4d87cd', '#d9a93a', '#c05b54', '#2fb8a6'];

/** Position chips, in the frame's order, and the anchor each one stores. */
const POSITIONS: Array<{ label: string; anchor: HUDAnchor }> = [
  { label: 'Top left', anchor: 'top-left' },
  { label: 'Top right', anchor: 'top-right' },
  { label: 'Bottom left', anchor: 'bottom-left' },
  { label: 'Bottom right', anchor: 'bottom-right' },
];

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

function GroupRow({
  title,
  sub,
  children,
}: {
  title: string;
  sub?: string;
  children: React.ReactNode;
}) {
  return (
    <div className="sgroup__row">
      <span className="sgroup__labels">
        <span className="sgroup__title">{title}</span>
        {sub && <span className="sgroup__sub">{sub}</span>}
      </span>
      {children}
    </div>
  );
}

export function ModSettingsScreen({ id }: { id: ModId }) {
  const entry = MOD_REGISTRY[id];
  const settings = useVoidStore((s) => modSettings(s.loadout, id));
  const setSetting = useVoidStore((s) => s.setSetting);
  const toggleMod = useVoidStore((s) => s.toggleMod);
  const resetMod = useVoidStore((s) => s.resetMod);
  const setRoute = useVoidStore((s) => s.setRoute);
  const setEditorTarget = useVoidStore((s) => s.setEditorTarget);
  const closeMenu = useVoidStore((s) => s.closeMenu);
  const captureKeybind = useVoidStore((s) => s.captureKeybind);
  const capturing = useVoidStore((s) => s.capturingKeybind === id);
  const placement = useVoidStore((s) => hudItem(s.loadout, id as HUDModId));
  const commitHud = useVoidStore((s) => s.commitHud);
  const onInHowMany = useVoidStore(
    (s) => s.library.filter((l) => (l.mods[id]?.on ?? MOD_REGISTRY[id].defaults.on) === true).length,
  );

  const isHud = (HUD_MOD_IDS as readonly string[]).includes(id);
  const keybindKey = 'keybind' in settings ? 'keybind' : 'key' in settings ? 'key' : null;

  const numericKeys = APPEARANCE_KEYS.filter((key) => key in settings && SETTING_RANGES[key]);
  const behaviourKeys = Object.keys(settings).filter(
    (key) =>
      key !== 'on' &&
      key !== keybindKey &&
      !numericKeys.includes(key) &&
      typeof settings[key] !== 'string',
  );
  const enumKeys = Object.keys(settings).filter(
    (key) =>
      key !== 'on' &&
      key !== keybindKey &&
      typeof settings[key] === 'string' &&
      !String(settings[key]).startsWith('#'),
  );

  const write = (key: string, value: ModSettingValue) => setSetting(id, key, value);

  return (
    <Panel
      title={entry.label}
      leading={
        <button type="button" className="panel__back" onClick={() => setRoute({ name: 'mods' })}>
          <Icon name="arrow-left" size={13} />
          Mods
        </button>
      }
      subtitle={
        <span className="vd-footer-hint">{`${entry.category}   ·   on in ${onInHowMany} loadout${
          onInHowMany === 1 ? '' : 's'
        }`}</span>
      }
      controls={
        <div className="mset__controls">
          <span className="mset__enabled-label">Enabled</span>
          <Switch
            on={settings.on === true}
            size="l"
            label={`${entry.label} enabled`}
            onChange={(next) => toggleMod(id, next)}
          />
          {keybindKey && (
            <button
              type="button"
              className="mset__keybind"
              onClick={() => void captureKeybind(id, keybindKey)}
            >
              <span className="mset__keybind-label">{settingLabel(keybindKey)}</span>
              <KbdChip variant="keybind">
                {capturing ? 'Press a key…' : keybindLabel(settings[keybindKey] ?? null)}
              </KbdChip>
            </button>
          )}
        </div>
      }
      footer={MOD_SETTINGS_FOOTER}
      onClose={closeMenu}
    >
      <div className="mset__preview">
        <span className="mset__preview-caption">Live preview</span>
        <span className="mset__preview-meta">
          {[
            placement ? anchorLabel(placement.anchor) : '—',
            formatSetting('scale', settings.scale ?? 1),
            formatSetting('opacity', settings.opacity ?? 1),
          ].join('   ·   ')}
        </span>
        <div className="mset__preview-stage">
          {id === 'keystrokes' ? (
            <KeystrokesWidget />
          ) : (
            <span style={{ color: 'var(--text-secondary)', fontSize: 'var(--text-md-2)' }}>
              {entry.description}
            </span>
          )}
        </div>
      </div>

      <div className="mset__groups">
        <div className="sgroup">
          <div className="sgroup__cap">Appearance</div>
          {numericKeys.map((key) => {
            const range = SETTING_RANGES[key]!;
            const value = Number(settings[key] ?? range.min);
            return (
              <GroupRow title={settingLabel(key)} key={key}>
                <Slider
                  variant="wide"
                  value={value}
                  min={range.min}
                  max={range.max}
                  step={range.step}
                  label={settingLabel(key)}
                  onChange={(next) => write(key, next)}
                />
                <span className="sgroup__value">{formatSetting(key, value)}</span>
              </GroupRow>
            );
          })}
          {id === 'keystrokes' && (
            <>
              <GroupRow title="Key colour" sub="Background of an unpressed key">
                <div className="sgroup__swatches">
                  {KEY_COLOURS.map((colour) => (
                    <button
                      key={colour}
                      type="button"
                      aria-label={`Key colour ${colour}`}
                      className={`sgroup__swatch${
                        settings.key_color === colour ? ' sgroup__swatch--selected' : ''
                      }`}
                      style={{ background: colour }}
                      onClick={() => write('key_color', colour)}
                    />
                  ))}
                </div>
              </GroupRow>
              <GroupRow title="Pressed colour" sub="Follows the loadout accent by default">
                <div className="sgroup__swatches">
                  {PRESSED_COLOURS.map((colour) => (
                    <button
                      key={colour}
                      type="button"
                      aria-label={`Pressed colour ${colour}`}
                      className={`sgroup__swatch${
                        settings.pressed_color === colour ? ' sgroup__swatch--selected' : ''
                      }`}
                      style={{ background: colour }}
                      onClick={() => write('pressed_color', colour)}
                    />
                  ))}
                </div>
              </GroupRow>
            </>
          )}
        </div>

        <div className="sgroup">
          <div className="sgroup__cap">Behaviour</div>
          {behaviourKeys.map((key) => (
            <GroupRow title={settingLabel(key)} sub={SETTING_SUBTITLES[key]} key={key}>
              <Switch
                on={settings[key] === true}
                size="l"
                label={settingLabel(key)}
                onChange={(next) => write(key, next)}
              />
            </GroupRow>
          ))}
          {enumKeys.map((key) => (
            <GroupRow title={settingLabel(key)} key={key}>
              <div className="sgroup__chips">
                {enumOptions(id, key).map((option) => (
                  <button
                    key={option}
                    type="button"
                    className={`sgroup__chip${
                      settings[key] === option ? ' sgroup__chip--selected' : ''
                    }`}
                    onClick={() => write(key, option)}
                  >
                    {option.charAt(0).toUpperCase() + option.slice(1).replace(/_/g, ' ')}
                  </button>
                ))}
              </div>
            </GroupRow>
          ))}
          {isHud && placement && (
            <GroupRow title="Position">
              <div className="sgroup__chips">
                {POSITIONS.map((p) => (
                  <button
                    key={p.anchor}
                    type="button"
                    className={`sgroup__chip${
                      placement.anchor === p.anchor ? ' sgroup__chip--selected' : ''
                    }`}
                    onClick={() =>
                      commitHud(
                        id as HUDModId,
                        p.anchor,
                        p.anchor.endsWith('right') ? -25 : 25,
                        p.anchor.startsWith('bottom') ? -25 : 25,
                        placement.scale ?? 1,
                      )
                    }
                  >
                    {p.label}
                  </button>
                ))}
              </div>
            </GroupRow>
          )}
        </div>
      </div>

      <div className="mset__actions">
        {isHud && (
          <Button
            variant="accent-sm"
            icon="move"
            iconSize={13}
            onClick={() => {
              setEditorTarget(id as HUDModId);
              setRoute({ name: 'hud-editor' });
            }}
          >
            Edit position
          </Button>
        )}
        <Button variant="raised" icon="rotate-ccw" iconSize={13} onClick={() => resetMod(id)}>
          Reset
        </Button>
      </div>
    </Panel>
  );
}

function anchorLabel(anchor: HUDAnchor): string {
  const words = anchor.split('-');
  const first = words[0]!;
  return [first.charAt(0).toUpperCase() + first.slice(1), ...words.slice(1)].join(' ');
}

/** Enum options for a settings key, transcribed from mods.json. */
function enumOptions(id: ModId, key: string): string[] {
  const table: Record<string, string[]> = {
    'cps.mode': ['left', 'right', 'both'],
    'coordinates.layout': ['stacked', 'inline'],
    'armor_status.orientation': ['horizontal', 'vertical'],
    'toggle_sprint.mode': ['toggle', 'hold'],
    'crosshair.style': ['default', 'cross', 'dot', 'circle', 't_shape', 'none'],
  };
  return table[`${id}.${key}`] ?? [];
}
