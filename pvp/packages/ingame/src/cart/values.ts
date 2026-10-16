/**
 * A cartridge's live value line — `142 fps`, `11 · 4 cps`, `Blockhit`, `4×`.
 *
 * One selector per mod, returning a string, so a cartridge re-renders only when its own line
 * changes (zustand compares the string by value). HUD mods read the live readings; gameplay mods
 * summarise the setting that defines them.
 *
 * In game the readings are the ones from the moment the menu opened: the store holds HUD
 * publishing while a panel covers it (`applyTick`), which is what keeps an idle open menu at zero
 * repaints. A snapshot is the honest thing to show there — it is what the HUD will say again a
 * tick after the menu closes.
 */

import { cardinalFromYaw, type ModId } from '@/bridge/protocol';
import { modSettings, useVoidStore, type SettingValue, type VoidState } from '@/store/store';
import { armorRow, potionMeta, shortHost } from '@/hud/format';
import { stripMc } from '@/hud/mc-format';
import { formatSetting } from '@/menu/settings-format';

export interface CartValue {
  /** The reading, painted in the category hue. */
  v: string;
  /** A short unit, painted in ink 3 beside it. */
  unit?: string;
}

const word = (value: SettingValue): string => {
  const s = String(value ?? '');
  if (!s) return '';
  const spaced = s.replace(/_/g, ' ');
  return spaced.charAt(0).toUpperCase() + spaced.slice(1);
};

function clock(ms: number): string {
  const total = Math.max(0, Math.round(ms / 1000));
  const m = Math.floor(total / 60);
  const s = total % 60;
  return `${m}:${String(s).padStart(2, '0')}`;
}

type Reader = (state: VoidState, settings: Record<string, SettingValue>) => CartValue | null;

const READERS: Partial<Record<ModId, Reader>> = {
  fps: (s) => ({ v: s.fps > 0 ? String(Math.round(s.fps)) : '—', unit: 'fps' }),
  memory: (s) =>
    s.memory && s.memory.maxMb > 0
      ? { v: String(Math.round((s.memory.usedMb / s.memory.maxMb) * 100)), unit: '%' }
      : { v: '—', unit: 'mem' },
  cps: (s) => ({ v: `${s.cpsLeft} · ${s.cpsRight}`, unit: 'cps' }),
  ping: (s) => (s.server.connected && s.ping >= 0 ? { v: String(s.ping), unit: 'ms' } : { v: 'local' }),
  coordinates: (s) =>
    s.pos ? { v: `${Math.round(s.pos.x)} ${Math.round(s.pos.y)} ${Math.round(s.pos.z)}`.replace(/-/g, '−') } : { v: '—' },
  direction: (s) => (s.pos ? { v: cardinalFromYaw(s.pos.yaw ?? 0) } : { v: '—' }),
  keystrokes: () => ({ v: 'on' }),
  armor_status: (s) => {
    const rows = s.armor
      .filter((slot) => slot.slot !== 'held')
      .map((slot) => armorRow(slot))
      .filter((row): row is NonNullable<typeof row> => row !== null && row.max > 0);
    if (rows.length === 0) return { v: 'no armour' };
    const least = Math.min(...rows.map((r) => r.remaining));
    return { v: String(least), unit: 'min' };
  },
  potion_effects: (s) => {
    const first = s.fx[0];
    if (!first) return { v: 'no effects' };
    return { v: clock(first.duration_ms), unit: potionMeta(first).label.split(' ')[0]?.toLowerCase() };
  },
  saturation: (s) => (s.saturation != null ? { v: s.saturation.toFixed(1), unit: 'sat' } : { v: '—' }),
  momentum: (s) => (s.speed != null ? { v: s.speed.toFixed(2), unit: 'b/s' } : { v: '—' }),
  scoreboard: (s) => (s.scoreboard.visible ? { v: stripMc(s.scoreboard.title) || 'Sidebar' } : { v: 'no sidebar' }),
  combo: (s) => ({ v: String(s.combo), unit: 'hits' }),
  reach: (s) => (s.reach ? { v: s.reach.last.toFixed(2), unit: 'm' } : { v: '—', unit: 'm' }),
  item_counter: (s, o) => {
    const track = String(o.track ?? 'held_stack');
    if (track === 'held_stack') return { v: s.heldCount != null ? String(s.heldCount) : '—', unit: 'held' };
    const n = (s.counts as Record<string, number> | null)?.[track];
    return { v: n != null ? String(n) : '—', unit: word(track).toLowerCase() };
  },
  potion_counter: (s, o) => {
    const counts = (s.counts ?? {}) as Record<string, number>;
    const track = String(o.track ?? 'health_potion');
    const n = counts[track];
    return { v: n != null ? String(n) : '—', unit: track === 'soup' ? 'soup' : 'pots' };
  },
  server_address: (s) => (s.server.connected ? { v: shortHost(s.server.host) } : { v: 'Singleplayer' }),
  stopwatch: () => ({ v: '0:00' }),
  toggle_sprint: () => ({ v: 'Sprint held' }),
  toggle_sneak: (_s, o) => ({ v: word(o.mode) }),
  old_animations: (_s, o) => ({ v: o.block_hit === 'off' ? 'Swing' : 'Blockhit' }),
  old_input: (_s, o) => {
    const n = ['use_while_digging', 'dig_while_using', 'no_miss_delay'].filter((k) => o[k] === true).length;
    return { v: n === 0 ? 'Vanilla' : `${n} of 3`, unit: n === 0 ? undefined : '1.7' };
  },
  hitboxes: (_s, o) => ({ v: formatSetting('line_width', Number(o.line_width ?? 1)), unit: 'px' }),
  hit_color: (_s, o) => ({ v: String(o.color ?? '').slice(0, 7).toUpperCase() }),
  damage_tint: (_s, o) => ({ v: formatSetting('strength', Number(o.strength ?? 0)) }),
  fov: (_s, o) => ({ v: `${o.fov ?? 90}°` }),
  freelook: (_s, o) => ({ v: word(o.mode) }),
  crosshair: (_s, o) => ({ v: `${word(o.style)} · ${o.size ?? 5}px` }),
  fullbright: (_s, o) => ({ v: `γ ${o.gamma ?? 10}` }),
  block_outline: (_s, o) => ({ v: `${o.outline_width ?? 2}px`, unit: String(o.outline_style ?? '') }),
  nametags: (_s, o) => ({ v: o.hide_others ? 'Own only' : o.show_own ? 'Own tag' : 'Others' }),
  time_weather: (_s, o) =>
    o.lock_time ? { v: formatSetting('time_ticks', Number(o.time_ticks ?? 6000)) } : { v: o.clear_weather ? 'Clear' : 'Vanilla' },
  fog: (_s, o) => ({ v: word(o.distance_fog) }),
  clear_glass: (_s, o) => ({ v: o.clear_stained ? 'All glass' : o.clear_panes ? 'Panes' : 'Off' }),
  shiny_pots: (_s, o) => ({ v: `×${o.glint_passes ?? 1}` }),
  overlay: (_s, o) => {
    const n = ['hide_fire', 'hide_own_armor', 'hide_stuck_arrows', 'hide_pumpkin'].filter((k) => o[k] === true).length;
    return { v: `${n} hidden` };
  },
  gui_scale: (_s, o) => ({ v: formatSetting('hotbar_scale', Number(o.hotbar_scale ?? 0)), unit: 'hotbar' }),
  menu_blur: (_s, o) => ({ v: `${o.blur_radius ?? 0}`, unit: 'blur' }),
  watermark: (_s, o) => ({ v: word(o.style) }),
  zoom: (_s, o) => ({ v: formatSetting('fov_divisor', Number(o.fov_divisor ?? 4)) }),
  cosmetics: (_s, o) => ({ v: o.others === false ? 'Only you' : 'Everyone', unit: o.glow_level === 'off' ? undefined : 'glow' }),
  chat: (_s, o) => ({ v: o.stack_repeats ? 'Stack' : 'Plain', unit: o.timestamps ? 'time' : undefined }),
  culling: (_s, o) => ({ v: o.occlude_entities ? 'Entities' : 'Blocks', unit: `${o.always_draw_within ?? 4}b` }),
  particles: (_s, o) => ({ v: String(o.particle_cap ?? 0), unit: 'cap' }),
  frame_limiter: (_s, o) => ({ v: String(o.unfocused_fps ?? 30), unit: 'idle' }),
};

/** The value line for a mod, as one string the selector can compare (`v\u0000unit`). */
export function cartValueKey(state: VoidState, id: ModId): string {
  const read = READERS[id];
  if (!read) return '';
  const value = read(state, modSettings(state.loadout, id));
  if (!value) return '';
  return value.unit ? `${value.v}\u0000${value.unit}` : value.v;
}

export function useCartValue(id: ModId): CartValue | null {
  const key = useVoidStore((s) => cartValueKey(s, id));
  if (!key) return null;
  const [v, unit] = key.split('\u0000');
  return { v: v ?? '', unit };
}
