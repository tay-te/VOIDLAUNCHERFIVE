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
    hide: 'Hide',
    hide_fire: 'Hide fire overlay',
    hide_stuck_arrows: 'Hide stuck arrows',
    hide_pumpkin: 'Hide pumpkin blur',
    view_bobbing: 'View bobbing',
    lock_sprint: 'Lock while sprinting',
    lock_bow: 'Lock while drawing a bow',
    always_swing: 'Always swing',
    use_while_digging: 'Use item while digging',
    block_hit: 'Block hit',
    show_jitter: 'Show jitter',
    sidebar_scale: 'Sidebar scale',
    offset_x: 'Horizontal offset',
    offset_y: 'Vertical offset',
    show_seconds: 'Show seconds',
    show_figure: 'Show figure',
    window_s: 'Window',
    show_millis: 'Show hundredths',
    start_key: 'Start / stop key',
    reset_key: 'Reset key',
    show_direction: 'Show direction',
    show_eye_line: 'Show eye line',
    window_ms: 'Window',
    good_ms: 'Good under',
    bad_ms: 'Bad over',
    warn_below: 'Warn under',
    warn_above: 'Warn over',
    fov_divisor: 'FOV divisor',
    line_width: 'Line width',
    decimals: 'Decimals',
    orientation: 'Orientation',
    layout: 'Layout',
    mode: 'Mode',
    style: 'Style',
    source: 'Count',
    effect: 'Effect',
    splash_only: 'Splash only',
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
    sensitivity: 'Zoom sensitivity',
    color: 'Colour',
  };
  if (overrides[key]) return overrides[key]!;
  const words = key.replace(/_/g, ' ');
  return words.charAt(0).toUpperCase() + words.slice(1);
}

/**
 * One short line under a settings-row title — what the row does, for every row.
 *
 * ## Why this is total now
 *
 * It used to be seven entries "where the frames give one", which meant seven rows out of a
 * hundred and eighty had an explanation and the rest had a title. That is not restraint, it is a
 * gap with a rationale: `Block hit`, `Dig while using`, `Fov divisor`, `Window ms` and `Style`
 * (six different mods, six different meanings) are titles that say nothing to somebody who did
 * not write the schema, and the schema's own descriptions — the only other text that exists —
 * are two-paragraph arguments aimed at whoever writes the mixin.
 *
 * So the table covers every setting a row can draw, and `test/preview-copy.test.tsx` fails the
 * build when a mod gains a setting without one. Completeness is the point: a settings page where
 * *some* rows explain themselves reads as a page where the unexplained ones are self-evident,
 * which is a claim about the reader.
 *
 * ## The length rule, and why it is a rule
 *
 * **A hint is one clause, no longer than a title's line.** Same test enforces it. The failure
 * mode this table is between is not "too little text" — it is the two-sentence hint, which is
 * the shape the gameplay previews' readings had grown into (`menu/gameplay-previews.tsx` opens
 * with what that cost). A player is on this page mid-session with a game behind it; a paragraph
 * per row is a page nobody finishes reading, and the rows underneath it are the ones that lose.
 *
 * Say what it does, not why it exists. The why is in `schema/mods/<id>.json`, which is where
 * somebody changing the behaviour will look and where a player never will.
 *
 * ## Keyed by mod first
 *
 * `<mod>.<key>` wins over a bare `<key>`, because a bare key is a claim that the setting means
 * the same thing everywhere. `style` is on six mods and means six different things; `color` is
 * on six and inks six different marks. The bare entries are the ones that genuinely are one
 * thing — the shared HUD chrome block, and `decimals`.
 */
export const SETTING_HINTS: Record<string, string> = {
  /* The shared block (`schema/mods/_shared.json#/hud`) — one meaning, every HUD mod. `scale`
     and `size` are absent on purpose: they are spatial, so they live on the preview as a drag
     handle rather than as a row (`ModSettingsScreen`'s `SPATIAL_KEYS`). */
  opacity: 'How solid it is over the game',
  background: 'The ground it sits on over the game',
  border: 'A hairline round the edge',
  padding: 'How much room inside the edge',
  decimals: 'Decimal places on the figure',
  keybind: 'The key that toggles the mod',

  'armor_status.orientation': 'Pieces side by side, or stacked',
  'armor_status.show_durability': 'The number left under each piece',
  'armor_status.show_held_item': 'Your held item as a fifth slot',
  'armor_status.warn_below': 'Where a durability bar turns amber',

  'block_outline.hide': 'Do not draw the outline at all',
  'block_outline.color': 'Ink of the outline, alpha included',
  'block_outline.line_width': 'Stroke width, in GL line units',

  'clock.format': '24-hour, or 12-hour with AM/PM',
  'clock.show_seconds': 'A seconds field after the minutes',

  'combo.reset_ms': 'How long without a hit before it zeroes',
  'combo.show_label': 'The COMBO unit after the figure',

  'coordinates.show_direction': 'The cardinal you face, after Z',
  'coordinates.layout': 'Three lines, or one',
  'coordinates.color': 'Ink for the readings',

  'cps.mode': 'Left, right, or both buttons',
  'cps.show_label': 'The CPS unit after the figures',
  'cps.window_ms': 'How far back the rate is measured',
  'cps.show_peak': 'This session’s best, as an aside',

  'cps_graph.mode': 'Which button the graph plots',
  'cps_graph.window_s': 'How many seconds it covers',
  'cps_graph.show_figure': 'The current rate beside the graph',

  'crosshair.style': 'The shape at the screen centre',
  'crosshair.thickness': 'Stroke width, before GUI scale',
  'crosshair.gap': 'Space between the centre and each arm',
  'crosshair.color': 'Colour of the crosshair',
  'crosshair.outline': 'A dark pixel round it, for contrast',
  'crosshair.dynamic': 'The gap widens while sprinting',
  'crosshair.center_dot': 'A dot on the centre, under the arms',

  'damage_tint.threshold': 'The health the vignette starts at',
  'damage_tint.strength': 'How dark it gets at zero health',
  'damage_tint.camera_shake': 'Vanilla’s hurt-camera roll',

  'direction.style': 'N, North, or the axis',
  'direction.show_degrees': 'The raw yaw angle after the facing',
  'direction.color': 'Ink for the facing',

  'fov.fov': 'The field of view that is held',
  'fov.lock_sprint': 'Stop sprint punching the camera',
  'fov.lock_bow': 'Stop a drawn bow zooming in',

  'fps.color': 'Text colour of the figure',
  'fps.show_label': 'The FPS unit after the number',
  'fps.show_low': 'Your 1% low, as a trailing aside',

  'freelook.keybind': 'The key that engages freelook',
  'freelook.mode': 'Held down, or latched',
  'freelook.perspective': 'Where the camera sits',
  'freelook.snap_back': 'Whether your body follows on release',

  'fullbright.gamma': 'How far the dark end is lifted',

  'hit_color.color': 'Colour of the flash',
  'hit_color.own_hits_only': 'Only your hits, or every flash',
  'hit_color.intensity': 'How strong, against vanilla’s own',

  'hit_trade.style': 'The pair, the ratio, or your hits',
  'hit_trade.show_bar': 'The share of hits that were yours',
  'hit_trade.show_label': 'The TRADE unit after the figure',

  'hitboxes.line_width': 'Stroke width of the box',
  'hitboxes.color': 'Colour of the box',
  'hitboxes.show_eye_line': 'The ray showing where they look',
  'hitboxes.eye_line_color': 'Colour of that ray',
  'hitboxes.max_distance': 'How far away a box is still drawn',

  'item_counter.source': 'The stack in hand, or every copy of it',
  'item_counter.show_label': 'The × before the count',
  'item_counter.low_threshold': 'Where the count turns amber',

  'keystrokes.keybind': 'The key that toggles the overlay',
  'keystrokes.show_mouse': 'LMB and RMB under the arrows',
  'keystrokes.show_spacebar': 'A wide key under the block',
  'keystrokes.show_sneak': 'A shift key beside the space bar',
  'keystrokes.show_cps': 'Clicks per second inside the buttons',
  'keystrokes.corner_radius': 'How round a key tile is',
  'keystrokes.key_color': 'Ground of an unpressed key',
  'keystrokes.pressed_color': 'Fill of a pressed key',

  'memory.style': 'Used, used of max, or a percentage',
  'memory.show_bar': 'A fill bar under the figure',
  'memory.show_label': 'The MB or % after the figure',

  'momentum.unit': 'Blocks per second, or km/h',
  'momentum.show_label': 'The unit after the figure',

  'old_animations.block_hit': '1.7 swings while blocking, 1.8 does not',
  'old_animations.swing_during_delay': 'Your arm moves during the miss delay',

  'old_input.use_while_digging': 'Right click acts during a break',
  'old_input.dig_while_using': 'Keep mining while an item is in use',
  'old_input.no_miss_delay': 'Remove the dead time after a whiff',

  'overlay.hide_fire': 'The flames drawn over your view',
  'overlay.view_bobbing': 'How much the camera and hand move',
  'overlay.hide_own_armor': 'Your own armour on your own model',
  'overlay.hide_stuck_arrows': 'Arrows stuck in you, and on the ground',
  'overlay.hide_pumpkin': 'The blur while a pumpkin is worn',

  'ping.show_label': 'The ms unit after the number',
  'ping.good_ms': 'At or under this, the figure reads good',
  'ping.bad_ms': 'At or over this, the figure reads bad',
  'ping.show_host': 'The server name after the figure',
  'ping.show_jitter': 'How much the ping is moving, as ± ms',

  'potion_effects.show_duration': 'Time left beside each effect',
  'potion_effects.show_amplifier': 'The roman numeral after the name',
  'potion_effects.hide_ambient': 'Leave out beacon auras',

  'potion_counter.effect': 'Which potion is counted',
  'potion_counter.splash_only': 'Only the ones you can throw',
  'potion_counter.show_label': 'The unit after the figure',

  'reach.show_label': 'The blocks unit after the figure',
  'reach.warn_above': 'Where the figure turns amber',

  'saturation.style': 'A number, a bar, or both',
  'saturation.show_label': 'The SAT unit after the figure',

  'scoreboard.hide': 'Do not draw the sidebar at all',
  'scoreboard.sidebar_scale': 'Size, about its own right edge',
  'scoreboard.offset_x': 'Nudge it left or right, in pixels',
  'scoreboard.offset_y': 'Nudge it up or down, in pixels',

  'server_address.style': 'The short host, or the whole thing',

  'stopwatch.show_millis': 'Hundredths after the seconds',
  'stopwatch.format': 'Minutes, or hours as well',
  'stopwatch.start_key': 'Starts the clock, and stops it',
  'stopwatch.reset_key': 'Returns the reading to zero',

  'toggle_sneak.mode': 'Latched, or held like vanilla',
  'toggle_sneak.keybind': 'The key this mod latches',
  'toggle_sprint.keybind': 'The key this mod latches',

  'watermark.style': 'The ring, the word, or both',

  'zoom.key': 'The key you hold to zoom',
  'zoom.fov_divisor': 'How far in it goes',
  'zoom.sensitivity': 'How much mouse you keep while zoomed',
  'zoom.smooth': 'Eases in rather than snapping',
  'zoom.cinematic': 'Damped mouse movement while zoomed',
};

/**
 * The hint for one row, mod-specific first.
 *
 * `undefined` rather than a placeholder when there is nothing: a row with a blank second line is
 * worse than a row with one line, and the gate above means the only ids that can reach here
 * without an entry are the synthetic `?fake=` ones.
 */
export function settingHint(id: string, key: string): string | undefined {
  return SETTING_HINTS[`${id}.${key}`] ?? SETTING_HINTS[key];
}

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
