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
    show_direction: 'Show direction',
    show_status: 'Show status',
    show_eye_line: 'Show eye line',
    sneak_too: 'Sneak too',
    window_ms: 'Window',
    good_ms: 'Good under',
    bad_ms: 'Bad over',
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
    smooth: 'Smooth',
    cinematic: 'Cinematic',
    color: 'Colour',
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
};

/** The value printed to the right of a slider. */
export function formatSetting(key: string, value: SettingValue): string {
  if (typeof value !== 'number') return String(value ?? '—');
  switch (key) {
    case 'scale':
      return `${value.toFixed(1)}×`;
    case 'opacity':
      return `${Math.round(value * 100)}%`;
    case 'fov_divisor':
      return `${value.toFixed(1)}×`;
    default: {
      const unit = SETTING_RANGES[key]?.unit;
      if (unit === 'px') return `${value} px`;
      if (unit === 'ms') return `${value} ms`;
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
