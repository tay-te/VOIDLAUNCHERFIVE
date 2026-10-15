/**
 * Every key VOID listens for, in one list, with the clashes marked — the Settings page's
 * `Keybinds` group.
 *
 * Built from what decides it rather than from a table: the two client keys from the globals,
 * and every mod setting whose schema type is `keybind` (`KEYBIND_SETTINGS`, generated from
 * `mods.json`), read off the active loadout. A mod added with a key shows up here without this
 * file changing, which is the rule `docs/adding-a-mod.md` holds every per-mod table to.
 *
 * **What counts as a clash.** Two bindings on the same key. Java edges every row of its hotkey
 * table independently (`VoidClient.pollHotkeys`), so two mods on one key both fire on one press —
 * a toggle and a hold, or two toggles, flipping together. Mods that are switched off are still
 * listed and still compared: a toggle key flips its mod *on*, so an "off" mod's key is live.
 * Vanilla's own binds are not known to the page and are not compared; that would need Java to
 * send `GameOptions.keyBindings` over, and nothing on the bridge carries them yet.
 */

import { KEYBIND_SETTINGS, type ModId } from '@/bridge/protocol';
import { MOD_ORDER, modLabel } from '@/registry';
import { isModOn, modSettings, type GlobalsView } from '@/store/store';
import type { Loadout } from '@/bridge/protocol';
import { keybindLabel, settingLabel } from './settings-format';

/** One binding. `mod` is null for the client's own keys. */
export interface KeybindRow {
  id: string;
  label: string;
  /** The LWJGL key name, never `NONE` — unbound keys are not listed. */
  key: string;
  /** Display form of {@link KeybindRow.key}. */
  keyLabel: string;
  mod: ModId | null;
  /** The setting that holds it: `menu_key` / `cycle_loadout_key`, or the mod's own key. */
  setting: string;
  /** False for a mod that is switched off — listed, dimmed, still compared. */
  live: boolean;
  /** Labels of the other bindings on the same key; empty when there is no clash. */
  clashes: string[];
}

/** The mod settings that are keybinds, as `[mod, setting]`, in grid order. */
function keybindSettings(): [ModId, string][] {
  const out: [ModId, string][] = [];
  for (const id of MOD_ORDER) {
    for (const path of KEYBIND_SETTINGS) {
      const [mod, key] = path.split('.') as [string, string];
      if (mod === id) out.push([id, key]);
    }
  }
  return out;
}

/** Every bound key, client keys first, with clashes marked. */
export function keybindOverview(loadout: Loadout | null, globals: GlobalsView): KeybindRow[] {
  const rows: Omit<KeybindRow, 'clashes'>[] = [];
  const push = (row: Omit<KeybindRow, 'clashes' | 'keyLabel'>) => {
    if (!row.key || row.key === 'NONE') return;
    rows.push({ ...row, keyLabel: keybindLabel(row.key) });
  };
  push({ id: 'client:menu', label: 'Open this menu', key: globals.menuKey, mod: null, setting: 'menu_key', live: true });
  push({
    id: 'client:cycle',
    label: 'Cycle loadouts',
    key: globals.cycleLoadoutKey,
    mod: null,
    setting: 'cycle_loadout_key',
    live: true,
  });
  const settings = keybindSettings();
  for (const [id, setting] of settings) {
    const value = modSettings(loadout, id)[setting];
    if (typeof value !== 'string') continue;
    // A mod with one key is named by the mod; one with two (the stopwatch) says which is which.
    const several = settings.filter(([other]) => other === id).length > 1;
    const label = several ? `${modLabel(id)} · ${settingLabel(setting)}` : modLabel(id);
    push({ id: `${id}.${setting}`, label, key: value, mod: id, setting, live: isModOn(loadout, id) });
  }
  return rows.map((row) => ({
    ...row,
    clashes: rows.filter((other) => other !== row && other.key === row.key).map((other) => other.label),
  }));
}
