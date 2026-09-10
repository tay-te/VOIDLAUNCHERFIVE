/**
 * The loadout library and the global settings.
 *
 * One store for both because the active loadout *is* a setting
 * (`settings.active_loadout`), and splitting them would mean two round trips and a
 * window where the Play screen names a loadout the settings no longer point at.
 */

import { create } from 'zustand';

import { decodeLoadout, encodeLoadout } from '@void/protocol';

import type {
  HUDItem,
  Loadout,
  LoadoutPatch,
  LoadoutSummary,
  ModId,
  Settings,
  SettingsPatch,
} from '../local/protocol';
import { errorText, invoke, listen } from '../local/tauri';
import { effectiveState } from '../local/registry';

/** What an import did, for the panel that has to say so. */
export type ImportOutcome =
  | { ok: true; name: string; dropped: string[]; foreign: boolean }
  | { ok: false; message: string };

/** Why a code was refused, in the words a player needs. */
const REFUSALS: Record<'empty' | 'prefix' | 'checksum' | 'malformed', string> = {
  empty: 'Paste a share code first.',
  // Named separately from `malformed` because the fix is different and obvious: they pasted
  // something else. Telling them it is corrupt would send them back to the sender for nothing.
  prefix: 'That does not look like a VOID share code.',
  // The realistic corruption, and the one worth naming precisely — a code that lost its tail to
  // a chat client's length limit looks complete to the eye.
  checksum: 'That code is incomplete — copy the whole thing and try again.',
  malformed: 'That code is damaged and cannot be read.',
};

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
  setMod: (id: ModId, next: Record<string, unknown>) => Promise<void>;
  /**
   * Place one HUD item — the mod setup page's drag handle writes through here.
   *
   * The whole layout is sent because `hud` is an ordered array and paint order is part
   * of it; replacing one entry in place keeps that order, and dropping an item is
   * expressed as `null`.
   */
  setHudItem: (id: ModId, item: HUDItem | null) => Promise<void>;
  saveSettings: (patch: SettingsPatch) => Promise<void>;
  /**
   * The active loadout as a share code, or null when there is nothing to share.
   *
   * Synchronous and pure — `encodeLoadout` reads the loadout already in this store, so there is
   * no round trip and nothing to fail. A "copy" button that could reject is a button a player
   * presses twice.
   */
  shareCode: () => string | null;
  /**
   * A share code as a new loadout in the library, switched to on success.
   *
   * Creates and then patches rather than writing a whole loadout in one call, because that is
   * the shape `loadouts_update` already has: Rust merges each mod over the registry defaults and
   * validates it, which is the same reconstruction the codec's delta assumes. Sending a
   * pre-merged loadout would be doing that arithmetic twice, in two languages.
   */
  importCode: (code: string) => Promise<ImportOutcome>;
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
    // Rust merges this over the mod's effective settings and validates the result
    // against that mod's settings sub-schema, so a sparse patch is safe and is what the
    // Mods screen sends — one switch flip, one key.
    try {
      const updated = await invoke('loadouts_update', {
        id: active.id,
        patch: { mods: { [id]: next } },
      });
      set({ active: updated, error: null });
    } catch (e) {
      set({ error: errorText(e) });
    }
  },

  setHudItem: async (id, item) => {
    const active = get().active;
    if (!active) return;
    const rest = active.hud.filter((entry) => entry.id !== id);
    const hud = item ? [...rest, item] : rest;
    try {
      const updated = await invoke('loadouts_update', { id: active.id, patch: { hud } });
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

  shareCode: () => {
    const active = get().active;
    return active === null ? null : encodeLoadout(active);
  },

  importCode: async (code) => {
    const decoded = decodeLoadout(code);
    if (!decoded.ok) return { ok: false, message: REFUSALS[decoded.reason] };
    try {
      // A name that says where it came from, and only when it would otherwise collide. A player
      // importing a friend's "Sword PvP" while holding their own wants both; a player importing
      // something they do not already have wants the name they were given.
      const taken = new Set(get().library.map((entry) => entry.name));
      let name = decoded.loadout.name;
      for (let n = 2; taken.has(name); n += 1) name = `${decoded.loadout.name} (${n})`;

      const created = await invoke('loadouts_create', { name, icon: decoded.loadout.icon });
      const updated = await invoke('loadouts_update', {
        id: created.id,
        patch: {
          server: decoded.loadout.server,
          mods: decoded.loadout.mods as LoadoutPatch['mods'],
          hud: decoded.loadout.hud,
        },
      });
      set({ library: await invoke('loadouts_list'), error: null });
      await get().switchTo(updated.id);
      return { ok: true, name, dropped: decoded.dropped, foreign: decoded.foreign };
    } catch (e) {
      const message = errorText(e);
      set({ error: message });
      return { ok: false, message };
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
      // Layer over the *effective* settings so a patch touching one key does not leave
      // a half-written block behind.
      mods[modId] = { ...effectiveState(active, modId), [key]: value } as never;
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
