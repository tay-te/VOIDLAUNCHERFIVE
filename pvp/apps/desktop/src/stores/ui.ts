/**
 * Which screen is up, and whether the palette / log drawer / settings are open.
 *
 * Separate from the data stores so opening the log drawer does not re-render the mod
 * grid, and so the command palette can navigate without importing every screen.
 */

import { create } from 'zustand';

export const SCREENS = ['play', 'mods', 'cosmetics', 'servers', 'friends'] as const;
export type Screen = (typeof SCREENS)[number];

export const SCREEN_LABELS: Record<Screen, string> = {
  play: 'Play',
  mods: 'Mods',
  cosmetics: 'Cosmetics',
  servers: 'Servers',
  friends: 'Friends',
};

interface UiState {
  screen: Screen;
  paletteOpen: boolean;
  settingsOpen: boolean;
  logOpen: boolean;
  /** Which mod's settings pane the Mods screen shows. */
  selectedMod: string;

  go: (screen: Screen) => void;
  openPalette: () => void;
  closePalette: () => void;
  togglePalette: () => void;
  openSettings: () => void;
  closeSettings: () => void;
  toggleLog: () => void;
  setLogOpen: (open: boolean) => void;
  selectMod: (id: string) => void;
}

export const useUi = create<UiState>((set, get) => ({
  screen: 'play',
  paletteOpen: false,
  settingsOpen: false,
  logOpen: false,
  selectedMod: 'keystrokes',

  go: (screen) => set({ screen, paletteOpen: false }),
  openPalette: () => set({ paletteOpen: true }),
  closePalette: () => set({ paletteOpen: false }),
  togglePalette: () => set({ paletteOpen: !get().paletteOpen }),
  openSettings: () => set({ settingsOpen: true, paletteOpen: false }),
  closeSettings: () => set({ settingsOpen: false }),
  toggleLog: () => set({ logOpen: !get().logOpen }),
  setLogOpen: (logOpen) => set({ logOpen }),
  selectMod: (selectedMod) => set({ selectedMod }),
}));
