/**
 * Which screen is up, and whether the palette / log drawer / settings are open.
 *
 * Separate from the data stores so opening the log drawer does not re-render the mod
 * grid, and so the command palette can navigate without importing every screen.
 */

import { create } from 'zustand';

import type { ModId } from '../local/protocol';

/**
 * The navbar's tabs — v1 ships PvP, Quests and Store and nothing else.
 *
 * PvP is home: with modpacks and servers out of v1, a separate Home tab would repeat
 * PvP's hero, loadout map and dock. Friends is a drawer, not a screen, so it is toggled
 * from the navbar rather than listed here.
 */
export const NAV_SCREENS = ['pvp', 'quests', 'store'] as const;

/** Every screen value, including `mods`, which PvP's "Edit mods" opens but no tab names. */
export const SCREENS = [...NAV_SCREENS, 'mods'] as const;
export type Screen = (typeof SCREENS)[number];

export const SCREEN_LABELS: Record<Screen, string> = {
  pvp: 'PvP',
  quests: 'Quests',
  store: 'Store',
  mods: 'Mods',
};

interface UiState {
  screen: Screen;
  paletteOpen: boolean;
  settingsOpen: boolean;
  logOpen: boolean;
  /** The friends drawer, toggled from the navbar's friends button. */
  friendsOpen: boolean;
  /** Which mod the Mods grid has selected. */
  selectedMod: string;
  /**
   * The Mods screen's sub-route: the mod whose setup page is open, or `null` for the
   * grid. Kept here rather than in a router because the app has no router — a screen is
   * a value in this store, and a sub-route is one more value beside it. `go()` clears
   * it, so leaving Mods and coming back lands on the grid.
   */
  modSetup: ModId | null;

  go: (screen: Screen) => void;
  openPalette: () => void;
  closePalette: () => void;
  togglePalette: () => void;
  openSettings: () => void;
  closeSettings: () => void;
  toggleLog: () => void;
  setLogOpen: (open: boolean) => void;
  toggleFriends: () => void;
  setFriendsOpen: (open: boolean) => void;
  selectMod: (id: string) => void;
  /** Open a mod's setup page — navigates to Mods and pushes the sub-route. */
  openModSetup: (id: ModId) => void;
  /** Back to the grid. */
  closeModSetup: () => void;
}

export const useUi = create<UiState>((set, get) => ({
  screen: 'pvp',
  paletteOpen: false,
  settingsOpen: false,
  logOpen: false,
  friendsOpen: false,
  selectedMod: 'keystrokes',
  modSetup: null,

  go: (screen) => set({ screen, paletteOpen: false, modSetup: null }),
  openPalette: () => set({ paletteOpen: true }),
  closePalette: () => set({ paletteOpen: false }),
  togglePalette: () => set({ paletteOpen: !get().paletteOpen }),
  openSettings: () => set({ settingsOpen: true, paletteOpen: false }),
  closeSettings: () => set({ settingsOpen: false }),
  toggleLog: () => set({ logOpen: !get().logOpen }),
  setLogOpen: (logOpen) => set({ logOpen }),
  toggleFriends: () => set({ friendsOpen: !get().friendsOpen }),
  setFriendsOpen: (friendsOpen) => set({ friendsOpen }),
  selectMod: (selectedMod) => set({ selectedMod }),
  openModSetup: (id) =>
    set({ screen: 'mods', selectedMod: id, modSetup: id, paletteOpen: false }),
  closeModSetup: () => set({ modSetup: null }),
}));
