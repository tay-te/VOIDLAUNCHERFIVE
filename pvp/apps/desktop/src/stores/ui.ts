/**
 * Which screen is up, and which overlays are open.
 *
 * Separate from the data stores so opening the friends drawer does not re-render the
 * mod grid, and so the command palette can navigate without importing every screen.
 *
 * The screens are the v1 Ship navbar (Figma `52:2`, rule 1): **PvP** and **Store** are
 * live; **Modpacks**, **Servers** and **Quests** are in the bar, muted with `SOON`, and
 * each opens its coming-soon screen. **Edit mods** is not a tab — it is a page of PvP,
 * reached from the loadout strip on Home, so the PvP tab stays lit while it is up.
 */

import { flushSync } from 'react-dom';
import { create } from 'zustand';

import type { ModId } from '../local/protocol';
import { listen } from '../local/tauri';

export const SCREENS = ['home', 'store', 'modpacks', 'servers', 'quests'] as const;
export type Screen = (typeof SCREENS)[number] | 'mods';

/** The three navbar entries that open a coming-soon screen. */
export const SOON_SCREENS = ['modpacks', 'servers', 'quests'] as const;
export type SoonScreen = (typeof SOON_SCREENS)[number];

export const SCREEN_LABELS: Record<Screen, string> = {
  home: 'PvP',
  store: 'Store',
  modpacks: 'Modpacks',
  servers: 'Servers',
  quests: 'Quests',
  mods: 'Edit mods',
};

/**
 * Exploration switches — the Home layouts under review and the shell's material. Both
 * persist per machine so a layout picked to compare survives a reload.
 */
export const HOME_LAYOUTS = ['classic', 'stage', 'bento', 'lobby'] as const;
export type HomeLayout = (typeof HOME_LAYOUTS)[number];
export type Material = 'glass' | 'flat';

const PREFS_KEY = 'void.ui.prefs.v1';

interface Prefs {
  homeLayout: HomeLayout;
  material: Material;
}

function readPrefs(): Prefs {
  // Flat until the glass shell is signed off in Figma (section 122:274 on the Ship page).
  const fallback: Prefs = { homeLayout: 'classic', material: 'flat' };
  try {
    const raw = localStorage.getItem(PREFS_KEY);
    if (!raw) return fallback;
    const p = JSON.parse(raw) as Partial<Prefs>;
    return {
      homeLayout: (HOME_LAYOUTS as readonly string[]).includes(p.homeLayout ?? '')
        ? (p.homeLayout as HomeLayout)
        : fallback.homeLayout,
      material: p.material === 'glass' ? 'glass' : 'flat',
    };
  } catch {
    return fallback;
  }
}

function writePrefs(p: Prefs): void {
  try {
    localStorage.setItem(PREFS_KEY, JSON.stringify(p));
  } catch {
    /* a private window keeps it for the session */
  }
}

/**
 * Run a state change inside a View Transition so the outgoing screen fades while the
 * incoming one rises (`local/motion.css`). The system webviews are Chromium/WebKit with
 * the API; where it is missing, or the user asks for less motion, the change is instant.
 */
function withTransition(change: () => void): void {
  const doc =
    typeof document === 'undefined'
      ? undefined
      : (document as Document & { startViewTransition?: (cb: () => void) => unknown });
  if (!doc?.startViewTransition || window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) {
    change();
    return;
  }
  doc.startViewTransition(() => flushSync(change));
}

export const isSoon =(screen: Screen): screen is SoonScreen =>
  (SOON_SCREENS as readonly string[]).includes(screen);

/** The navbar tab a screen lights. Edit mods is a page of PvP. */
export const tabFor = (screen: Screen): (typeof SCREENS)[number] =>
  screen === 'mods' ? 'home' : screen;

interface UiState {
  screen: Screen;
  paletteOpen: boolean;
  settingsOpen: boolean;
  logOpen: boolean;
  /** The Friends drawer, opened from the navbar button (rule 1). */
  friendsOpen: boolean;
  /** The Store's item sheet: the id of the item on show, or `null` for the gallery alone. */
  storeItem: string | null;
  /** Which play bar menu is open — Home's "Choose another server" opens it from outside. */
  playMenu: 'server' | 'version' | null;
  /**
   * Home's loadout menu: closed, open on the library, or open with its import field —
   * which is how the palette's "Import loadout code" lands the player in a paste field.
   */
  loadoutMenu: 'list' | 'import' | null;
  /** The release-notes sheet behind Home's "Release notes". */
  notesOpen: boolean;
  /** Which mod the Mods grid has selected. */
  selectedMod: string;
  /**
   * The Mods screen's sub-route: the mod whose setup page is open, or `null` for the
   * grid. `go()` clears it, so leaving Mods and coming back lands on the grid.
   */
  modSetup: ModId | null;
  /** Which Home layout is on screen while the layouts are being compared. */
  homeLayout: HomeLayout;
  /** The shell's material: frosted glass over the ambient light, or the flat cell fills. */
  material: Material;

  go: (screen: Screen) => void;
  setHomeLayout: (layout: HomeLayout) => void;
  setMaterial: (material: Material) => void;
  openPalette: () => void;
  closePalette: () => void;
  togglePalette: () => void;
  openSettings: () => void;
  closeSettings: () => void;
  toggleLog: () => void;
  setLogOpen: (open: boolean) => void;
  toggleFriends: () => void;
  setFriendsOpen: (open: boolean) => void;
  setPlayMenu: (menu: 'server' | 'version' | null) => void;
  setLoadoutMenu: (menu: 'list' | 'import' | null) => void;
  /** Go Home and open the loadout menu on its import field. */
  openLoadoutImport: () => void;
  setNotesOpen: (open: boolean) => void;
  openStoreItem: (id: string) => void;
  closeStoreItem: () => void;
  selectMod: (id: string) => void;
  /** Open a mod's setup page — navigates to Mods and pushes the sub-route. */
  openModSetup: (id: ModId) => void;
  /** Back to the grid. */
  closeModSetup: () => void;
}

export const useUi = create<UiState>((set, get) => ({
  // Rule 1: the app opens on Home (PvP).
  screen: 'home',
  paletteOpen: false,
  settingsOpen: false,
  logOpen: false,
  friendsOpen: false,
  storeItem: null,
  playMenu: null,
  loadoutMenu: null,
  notesOpen: false,
  selectedMod: 'keystrokes',
  modSetup: null,
  ...readPrefs(),

  setHomeLayout: (homeLayout) => {
    set({ homeLayout });
    writePrefs({ homeLayout, material: get().material });
  },
  setMaterial: (material) => {
    set({ material });
    writePrefs({ homeLayout: get().homeLayout, material });
  },
  go: (screen) => {
    if (screen === get().screen && get().modSetup === null) return;
    withTransition(() =>
      set({
        screen,
        paletteOpen: false,
        modSetup: null,
        // The item sheet belongs to the Store; leaving the Store closes it.
        ...(screen !== 'store' ? { storeItem: null } : {}),
      }),
    );
  },
  openPalette: () => set({ paletteOpen: true }),
  closePalette: () => set({ paletteOpen: false }),
  togglePalette: () => set({ paletteOpen: !get().paletteOpen }),
  openSettings: () => set({ settingsOpen: true, paletteOpen: false }),
  closeSettings: () => set({ settingsOpen: false }),
  toggleLog: () => set({ logOpen: !get().logOpen }),
  setLogOpen: (logOpen) => set({ logOpen }),
  // The drawer and the item sheet share the panel's right edge, so one closes the other.
  toggleFriends: () => set({ friendsOpen: !get().friendsOpen, storeItem: null }),
  setFriendsOpen: (friendsOpen) => set({ friendsOpen, ...(friendsOpen ? { storeItem: null } : {}) }),
  setPlayMenu: (playMenu) => set({ playMenu }),
  setLoadoutMenu: (loadoutMenu) => set({ loadoutMenu }),
  openLoadoutImport: () => {
    get().go('home');
    set({ loadoutMenu: 'import', paletteOpen: false });
  },
  setNotesOpen: (notesOpen) => set({ notesOpen }),
  openStoreItem: (storeItem) => set({ screen: 'store', storeItem, friendsOpen: false }),
  closeStoreItem: () => set({ storeItem: null }),
  selectMod: (selectedMod) => set({ selectedMod }),
  openModSetup: (id) =>
    withTransition(() =>
      set({ screen: 'mods', selectedMod: id, modSetup: id, paletteOpen: false }),
    ),
  closeModSetup: () => withTransition(() => set({ modSetup: null })),
}));

/**
 * `store:open` — *Get in launcher Store* pressed on a locked item in game. Rust has already
 * brought the window back from the tray; this lands on that item's Store sheet, with nothing
 * over it. An id the catalog does not know opens the gallery alone.
 */
export async function wireUiEvents(): Promise<() => void> {
  return listen('store:open', ({ item }) => {
    useUi.setState({ settingsOpen: false, paletteOpen: false, modSetup: null });
    useUi.getState().openStoreItem(item);
  });
}
