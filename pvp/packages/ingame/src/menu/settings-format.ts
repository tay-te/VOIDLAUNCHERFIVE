/**
 * How a mod setting reads in the UI. The frames print `1.0×`, `85%`, `8 px`.
 */

import type { SettingValue } from '@/store/store';
import { SETTING_RANGES } from '@/registry';

/** Human label for a settings key: `show_mouse` → `Show mouse`. */
export function settingLabel(key: string): string {
  const overrides: Record<string, string> = {
    scale: 'Scale',
    opacity: 'Opacity',
    corner_radius: 'Corner radius',
    show_mouse: 'Show mouse buttons',
    show_cps: 'Show CPS',
    show_spacebar: 'Show space bar',
    show_sneak: 'Show sneak key',
    show_label: 'Show label',
    show_duration: 'Show duration',
    show_amplifier: 'Show amplifier',
    hide_ambient: 'Hide ambient',
    show_durability: 'Show durability',
    show_held_item: 'Show held item',
    // The schema key is `hide_own_armor`, American, while every label this client draws says
    // "armour" — `armor_status`'s own rows, and this setting's own description ("your own
    // armour"). The `_`-to-space fallback would put "Hide own armor" directly beside "Show
    // armour" on the same panel. Fixed here rather than by renaming the schema key: the key is
    // already in the shipped registry defaults, so renaming it now would orphan every loadout
    // carrying it and would need a `REMOVED_SETTINGS` row, which is a migration to pay for a
    // spelling. Worth doing if the key is ever touched for another reason.
    hide_own_armor: 'Hide own armour',
    hide_fire: 'Hide fire overlay',
    hide_stuck_arrows: 'Hide stuck arrows',
    hide_pumpkin: 'Hide pumpkin blur',
    view_bobbing: 'View bobbing',
    lock_sprint: 'Lock while sprinting',
    lock_bow: 'Lock while drawing a bow',
    always_swing: 'Always swing',
    use_while_digging: 'Use item while digging',
    block_hit: 'Block hit',
    show_millis: 'Show hundredths',
    start_key: 'Start / stop key',
    reset_key: 'Reset key',
    show_direction: 'Show direction',
    show_eye_line: 'Show eye line',
    window_ms: 'Window',
    good_ms: 'Good under',
    bad_ms: 'Bad over',
    warn_below: 'Warn under',
    fov_divisor: 'FOV divisor',
    line_width: 'Line width',
    decimals: 'Decimals',
    orientation: 'Orientation',
    layout: 'Layout',
    mode: 'Mode',
    style: 'Style',
    gamma: 'Gamma',
    key: 'Key',
    keybind: 'Keybind',
    size: 'Size',
    thickness: 'Thickness',
    gap: 'Gap',
    outline: 'Outline',
    dynamic: 'Dynamic',
    center_dot: 'Centre dot',
    smooth: 'Smooth',
    cinematic: 'Cinematic',
    color: 'Colour',
    // The depth wave's keys. Spelled out wherever the `_`-to-space fallback would read wrong or
    // flat: `Jitter warn ms` is a variable name, not a label.
    track: 'Counts',
    low_threshold: 'Warn at',
    hide_after_ms: 'Hide after',
    show_average: 'Show average',
    show_jitter: 'Show jitter',
    jitter_warn_ms: 'Jitter warn',
    spike_ms: 'Spike over',
    window: 'Window',
    animate: 'Animate release',
    fade_ms: 'Fade',
    ignore_cancelled: 'Ignore cancelled clicks',
    hidden_effects: 'Hidden effects',
    blink_below_s: 'Blink under',
    state_colors: 'State colours',
    hittable_color: 'Hittable colour',
    damaged_color: 'Damaged colour',
    scroll_adjust: 'Scroll to adjust',
    scale_sensitivity: 'Scale sensitivity',
    timestamps: 'Timestamps',
    stack_repeats: 'Stack repeats',
    click_to_copy: 'Right-click to copy',
    background_opacity: 'Background',
    history_length: 'History',
    show_host: 'Show server',
    show_peak: 'Show peak',
  };
  if (overrides[key]) return overrides[key]!;
  const words = key.replace(/_/g, ' ');
  return words.charAt(0).toUpperCase() + words.slice(1);
}

/** One-line explanation under a settings-row title, where the frames give one. */
export const SETTING_SUBTITLES: Record<string, string> = {
  show_mouse: 'LMB and RMB under the arrows',
  show_cps: 'Clicks per second for both buttons',
  show_spacebar: 'A wide key under the block',
  show_sneak: 'A shift key beside the space bar',
  center_dot: 'A dot on the centre, under the arms',
  dynamic: 'The gap widens while sprinting',
  warn_below: 'Where a durability bar turns amber',
  low_threshold: 'At or below this the figure turns amber · 0 is off',
  hide_after_ms: 'After the last landed hit · 0 keeps it up',
  show_average: 'avg over the window, after the figure',
  show_jitter: 'How much the ping swings, reading to reading',
  jitter_warn_ms: 'Where the jitter aside turns amber · 0 is off',
  spike_ms: 'A jump this far over average marks the chip · 0 is off',
  window: 'Readings the average and jitter cover',
  animate: 'Released keys ease back instead of snapping',
  fade_ms: 'How long a released key takes to rest',
  ignore_cancelled: 'Count swings the game ran, not every press',
  hidden_effects: 'Lit effects are never listed',
  blink_below_s: 'An effect this close to ending blinks · 0 is off',
  state_colors: 'Colour by hurt and in-reach',
  scroll_adjust: 'The wheel changes the zoom while held',
  scale_sensitivity: 'Aim moves the same share of the view zoomed',
  timestamps: 'The arrival time before each line',
  stack_repeats: 'A repeated line becomes one line with a count',
  click_to_copy: 'In open chat, without colour codes',
  background_opacity: 'The band behind the lines · 50% is vanilla',
  history_length: 'Messages kept to scroll back · vanilla keeps 100',
};

/** The value printed to the right of a slider. */
export function formatSetting(key: string, value: SettingValue): string {
  if (typeof value !== 'number') return String(value ?? '—');
  switch (key) {
    case 'scale':
      return `${value.toFixed(1)}×`;
    // Both are 0-1 fractions read as percentages. `warn_below` prints `Never` at 0 rather
    // than `0%`, because a threshold of zero is not a small threshold — it is the setting off,
    // and `0%` reads as "warn me when the bar is empty", which is the opposite.
    case 'warn_below':
      return value <= 0 ? 'Never' : `${Math.round(value * 100)}%`;
    case 'opacity':
    case 'background_opacity':
      return `${Math.round(value * 100)}%`;
    // 0 is the setting off, not a tiny value — the same reasoning as `warn_below`'s `Never`.
    case 'low_threshold':
    case 'jitter_warn_ms':
    case 'spike_ms':
    case 'blink_below_s':
      if (value <= 0) return 'Off';
      return key === 'low_threshold' ? String(value) : key === 'blink_below_s' ? `${value} s` : `${value} ms`;
    case 'hide_after_ms':
      return value <= 0 ? 'Never' : `${(value / 1000).toFixed(value % 1000 === 0 ? 0 : 1)} s`;
    case 'window':
      return `${value} readings`;
    case 'history_length':
      return `${value} lines`;
    case 'fov_divisor':
      return `${value.toFixed(1)}×`;
    case 'tag_scale':
      return `${value.toFixed(2)}×`;
    // A GUI scale step, where 0 is not a size: it hands the choice back to Minecraft.
    case 'hotbar_scale':
    case 'inventory_scale':
      return value <= 0 ? 'Auto' : `${value}`;
    // Ticks are what the game stores and nobody reads; the clock is what a player means. Tick 0
    // is sunrise, which Minecraft's own clock calls 06:00.
    case 'time_ticks': {
      const minutes = Math.round(((value / 1000 + 6) % 24) * 60) % (24 * 60);
      const hh = String(Math.floor(minutes / 60)).padStart(2, '0');
      const mm = String(minutes % 60).padStart(2, '0');
      return `${hh}:${mm}`;
    }
    default: {
      const unit = SETTING_RANGES[key]?.unit;
      // Every `%` setting is a 0-1 fraction, like `opacity` above; printing the raw value with a
      // percent sign would read `0.25%` for a quarter.
      if (unit === '%') return `${Math.round(value * 100)}%`;
      // A word unit takes a space — `30 fps`, `64 blocks` — and a glyph does not (`2×`).
      if (unit && /^[a-z]/i.test(unit)) return `${value} ${unit}`;
      if (unit) return `${value}${unit}`;
      return String(value);
    }
  }
}

/** Keybind chips read `R-Shift`, not `RSHIFT`. */
export function keybindLabel(value: SettingValue): string {
  if (typeof value !== 'string' || value === '' || value === 'NONE') return 'None';
  const named: Record<string, string> = {
    RSHIFT: 'R-Shift',
    LSHIFT: 'L-Shift',
    RCONTROL: 'R-Ctrl',
    LCONTROL: 'L-Ctrl',
    LMENU: 'L-Alt',
    RMENU: 'R-Alt',
    SPACE: 'Space',
    RETURN: 'Enter',
    ESCAPE: 'Esc',
    PRIOR: 'PgUp',
    NEXT: 'PgDn',
    GRAVE: '`',
    CAPITAL: 'Caps',
  };
  if (named[value]) return named[value]!;
  if (/^MOUSE[0-7]$/.test(value)) return `Mouse ${value.slice(5)}`;
  return value.length === 1 ? value : value.charAt(0) + value.slice(1).toLowerCase();
}
