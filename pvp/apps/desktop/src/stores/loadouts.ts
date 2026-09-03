/**
 * The loadout library and the global settings.
 *
 * One store for both because the active loadout *is* a setting
 * (`settings.active_loadout`), and splitting them would mean two round trips and a
 * window where the Play screen names a loadout the settings no longer point at.
 */

import { create } from 'zustand';

import type {
  Loadout,
  LoadoutSummary,
  ModId,
  ModState,
  Settings,
  SettingsPatch,
} from '../local/protocol';
import { errorText, invoke, listen } from '../local/tauri';
import { effectiveState } from '../local/registry';

interface LoadoutState {
  library: LoadoutSummary[];
  active: Loadout | null;
  settings: Settings | null;
  error: string | null;
  loading: boolean;

  hydrate: () => Promise<void>;
  switchTo: (id: string) => Promise<void>;
  create: (name: string, icon?: string) => Promise<void>;
  remove: (id: string) => Promise<void>;
  /**
   * Flip or edit one mod on the active loadout.
   *
   * Writes through `loadouts_update` and takes the returned loadout as truth rather
   * than patching locally — the Rust side merges, and a local guess would drift the
   * moment a setting gains a clamp.
   */
  setMod: (id: ModId, next: Partial<ModState>) => Promise<void>;
  saveSettings: (patch: SettingsPatch) => Promise<void>;
  /** Apply a `bridge:state` patch from a running game (§6.1: Java is authoritative). */
  applyStatePatch: (loadoutId: string, patch: Record<string, unknown>) => void;
}

export const useLoadouts = create<LoadoutState>((set, get) => ({
  library: [],
  active: null,
  settings: null,
  error: null,
  loading: true,

  hydrate: async () => {
    try {
      const [library, active, settings] = await Promise.all([
        invoke('loadouts_list'),
        invoke('loadouts_active'),
        invoke('settings_get'),
      ]);
      set({ library, active, settings, loading: false });
    } catch (e) {
      set({ error: errorText(e), loading: false });
    }
  },

  switchTo: async (id) => {
    try {
      const active = await invoke('loadouts_switch', { id });
      const settings = get().settings;
      set({
        active,
        error: null,
        ...(settings ? { settings: { ...settings, active_loadout: active.id } } : {}),
      });
    } catch (e) {
      set({ error: errorText(e) });
    }
  },

  create: async (name, icon) => {
    try {
      const created = await invoke('loadouts_create', icon ? { name, icon } : { name });
      set({ library: await invoke('loadouts_list'), error: null });
      await get().switchTo(created.id);
    } catch (e) {
      set({ error: errorText(e) });
    }
  },

  remove: async (id) => {
    try {
      const library = await invoke('loadouts_delete', { id });
      const active = await invoke('loadouts_active');
      set({ library, active, error: null });
    } catch (e) {
      set({ error: errorText(e) });
    }
  },

  setMod: async (id, next) => {
    const active = get().active;
    if (!active) return;
    // Send the full effective state for this one mod, not a sparse delta: the schema
    // validates a mod's settings as a unit, and a half-written block would fail it.
    const merged = { ...effectiveState(active.mods, id), ...next } as ModState;
    try {
      const updated = await invoke('loadouts_update', {
        id: active.id,
        patch: { mods: { [id]: merged } },
      });
      set({ active: updated, error: null });
    } catch (e) {
      set({ error: errorText(e) });
    }
  },

  saveSettings: async (patch) => {
    try {
      set({ settings: await invoke('settings_set', { patch }), error: null });
    } catch (e) {
      set({ error: errorText(e) });
    }
  },

  applyStatePatch: (loadoutId, patch) => {
    const active = get().active;
    if (!active || active.id !== loadoutId) return;

    const mods = { ...active.mods };
    for (const [path, value] of Object.entries(patch)) {
      // Paths are exactly `mods.<mod_id>.<setting>` (protocol.json state_patch).
      const parts = path.split('.');
      if (parts.length !== 3 || parts[0] !== 'mods') continue;
      const modId = parts[1] as ModId;
      const key = parts[2] as string;
      const current = effectiveState(active.mods, modId);
      mods[modId] = { ...current, [key]: value } as ModState;
    }
    set({ active: { ...active, mods } });
  },
}));

/** `loadout:switched` fires when the tray (or another window) changed it. */
export async function wireLoadoutEvents(): Promise<() => void> {
  const unlistenSwitched = await listen('loadout:switched', (loadout) => {
    const settings = useLoadouts.getState().settings;
    useLoadouts.setState({
      active: loadout,
      ...(settings ? { settings: { ...settings, active_loadout: loadout.id } } : {}),
    });
  });

  const unlistenState = await listen('bridge:state', (msg) => {
    useLoadouts.getState().applyStatePatch(msg.loadout, msg.patch);
  });

  return () => {
    unlistenSwitched();
    unlistenState();
  };
}
