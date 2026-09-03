/**
 * A render smoke test per screen, against the real `createFakeVoid()`.
 *
 * There are no devtools in game, so "it mounted and drew the frame's copy" is
 * the cheapest signal that a screen has not silently broken. Each case asserts
 * the verbatim text the frame carries — footer hints included, because that copy
 * is part of the design contract.
 */

import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react';

/**
 * The frames' footer hints are spaced with runs of three spaces, and
 * testing-library collapses whitespace by default. Assert the copy verbatim:
 * the spacing is part of the design contract.
 */
const verbatim = { collapseWhitespace: false as const, trim: false as const };

/** Store writes from outside a React event have to be flushed like React ones. */
function set(mutate: () => void) {
  act(() => {
    mutate();
  });
}
import { connectBridge } from '@/bridge/connect';
import { resetDerivedState, useVoidStore } from '@/store/store';
import { App } from '@/App';
import { MODS_FOOTER } from '@/menu/ModsScreen';
import { MOD_SETTINGS_FOOTER } from '@/menu/ModSettingsScreen';
import { LOADOUTS_FOOTER } from '@/menu/LoadoutsScreen';
import { PARTY_FOOTER } from '@/menu/PartyScreen';
import { EDITOR_HINT } from '@/menu/HudEditorScreen';

let dispose: () => void;

beforeEach(() => {
  resetDerivedState();
  // The store is a module singleton; reset the UI slice so cases do not leak
  // a filter or a route into each other.
  useVoidStore.setState({
    route: { name: 'mods' },
    selectedMod: 'keystrokes',
    paletteOpen: false,
    modSearch: '',
    modFilter: 'all',
    editorTarget: 'keystrokes',
    editorSnap: true,
    editorGrid: false,
  });
  ({ dispose } = connectBridge({ forceFake: true, runFakeClock: false }));
});

afterEach(() => {
  cleanup();
  dispose();
});

describe('HUD layer', () => {
  it('mounts with the menu closed and draws the loadout’s widgets', () => {
    set(() => useVoidStore.getState().applyMenu(false));
    set(() => useVoidStore.getState().applyTick({ fps: 142, ping: 42 }));
    const { container } = render(<App />);
    expect(container.querySelector('.hud-layer')).not.toBeNull();
    expect(screen.getByText('142')).toBeTruthy();
    expect(screen.getByText('fps')).toBeTruthy();
  });

  it('is not the editor layer, so its widgets stay inert — §6.3', () => {
    const { container } = render(<App />);
    expect(container.querySelector('.hud-layer')).not.toBeNull();
    expect(container.querySelector('.hud-layer--editor')).toBeNull();
  });

  it('draws only the HUD mods the loadout both enables and places', () => {
    const { container } = render(<App />);
    const drawn = [...container.querySelectorAll('[data-hud-id]')].map((el) =>
      el.getAttribute('data-hud-id'),
    );
    const loadout = useVoidStore.getState().loadout!;
    for (const id of drawn) {
      expect(loadout.hud.some((h) => h.id === id)).toBe(true);
    }
  });
});

describe('Mods screen — frame 244:538', () => {
  beforeEach(() => {
    set(() => useVoidStore.getState().applyMenu(true));
  });

  it('renders the title, the tab set and the footer hint verbatim', () => {
    render(<App />);
    expect(screen.getByRole('heading', { name: 'Mods' })).toBeTruthy();
    for (const label of ['All', 'HUD', 'PvP', 'Visual', 'Utility']) {
      expect(screen.getByRole('tab', { name: label })).toBeTruthy();
    }
    expect(screen.getByText(MODS_FOOTER, verbatim)).toBeTruthy();
  });

  it('draws all twelve tiles with their category tags', () => {
    const { container } = render(<App />);
    expect(container.querySelectorAll('[data-mod-id]')).toHaveLength(12);
    expect(screen.getByText('FPS display')).toBeTruthy();
    expect(screen.getByText('Toggle sprint')).toBeTruthy();
  });

  it('filters the grid by tab', () => {
    const { container } = render(<App />);
    set(() => useVoidStore.getState().setModFilter('VISUAL'));
    const ids = [...container.querySelectorAll('[data-mod-id]')].map((el) =>
      el.getAttribute('data-mod-id'),
    );
    expect(ids.sort()).toEqual(['crosshair', 'fullbright']);
  });

  it('filters the grid by search', () => {
    const { container } = render(<App />);
    set(() => useVoidStore.getState().setModSearch('keystro'));
    expect(container.querySelectorAll('[data-mod-id]')).toHaveLength(1);
  });

  it('shows the settings pane for the selected tile', () => {
    const { container } = render(<App />);
    set(() => useVoidStore.getState().selectMod('keystrokes'));
    const pane = container.querySelector('.v-modsettings') as HTMLElement;
    expect(within(pane).getByText('Keystrokes')).toBeTruthy();
    expect(within(pane).getByText('Edit position')).toBeTruthy();
  });
});

describe('Mod settings screen — frame 244:834', () => {
  it('renders the back button, the groups and the footer hint', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setRoute({ name: 'mod-settings', mod: 'keystrokes' }));
    render(<App />);
    expect(screen.getByRole('button', { name: /Mods/ })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Keystrokes' })).toBeTruthy();
    expect(screen.getByText('Enabled')).toBeTruthy();
    expect(screen.getByText('Live preview')).toBeTruthy();
    expect(screen.getByText('Appearance')).toBeTruthy();
    expect(screen.getByText('Behaviour')).toBeTruthy();
    expect(screen.getByText('Edit position')).toBeTruthy();
    expect(screen.getByText('Reset')).toBeTruthy();
    expect(screen.getByText(MOD_SETTINGS_FOOTER, verbatim)).toBeTruthy();
  });

  it('draws the frame’s Appearance rows for Keystrokes', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setRoute({ name: 'mod-settings', mod: 'keystrokes' }));
    render(<App />);
    expect(screen.getByText('Scale')).toBeTruthy();
    expect(screen.getByText('Opacity')).toBeTruthy();
    expect(screen.getByText('Key colour')).toBeTruthy();
    expect(screen.getByText('Background of an unpressed key')).toBeTruthy();
  });

  it('draws the frame’s Behaviour rows', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setRoute({ name: 'mod-settings', mod: 'keystrokes' }));
    render(<App />);
    expect(screen.getByText('Show mouse buttons')).toBeTruthy();
    expect(screen.getByText('LMB and RMB under the arrows')).toBeTruthy();
    expect(screen.getByText('Show CPS')).toBeTruthy();
    expect(screen.getByText('Position')).toBeTruthy();
  });

  it('renders for a gameplay mod too, with no HUD-only controls', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setRoute({ name: 'mod-settings', mod: 'fullbright' }));
    render(<App />);
    expect(screen.getByRole('heading', { name: 'Fullbright' })).toBeTruthy();
    expect(screen.getByText('Gamma')).toBeTruthy();
    expect(screen.queryByText('Edit position')).toBeNull();
  });
});

describe('Loadouts screen — frame 244:1130', () => {
  it('renders the definition line, every card and the footer hint', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setRoute({ name: 'loadouts' }));
    render(<App />);
    expect(screen.getByRole('heading', { name: 'Loadouts' })).toBeTruthy();
    expect(
      screen.getByText('A loadout is which mods are on, their settings and HUD layout.'),
    ).toBeTruthy();
    expect(screen.getByText('New loadout')).toBeTruthy();
    expect(screen.getByText(LOADOUTS_FOOTER, verbatim)).toBeTruthy();
    expect(screen.getAllByText('Includes').length).toBeGreaterThan(0);
  });

  it('marks the active loadout and offers a switch on the others', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setRoute({ name: 'loadouts' }));
    const { container } = render(<App />);
    expect(container.querySelectorAll('.v-loadoutcard--active')).toHaveLength(1);
    expect(screen.getAllByText('Active').length).toBeGreaterThan(0);
    expect(screen.getAllByText(/^Switch to /).length).toBeGreaterThan(0);
  });
});

describe('Party screen — frame 244:1426', () => {
  it('renders the members, the queue pane and the footer hint', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setRoute({ name: 'party' }));
    render(<App />);
    expect(screen.getByRole('heading', { name: 'Party' })).toBeTruthy();
    expect(screen.getByText('In your party')).toBeTruthy();
    expect(screen.getByText('Searge')).toBeTruthy();
    expect(screen.getByText('Leader')).toBeTruthy();
    expect(screen.getByText('Ready')).toBeTruthy();
    expect(screen.getByText('Bedwars 4v4')).toBeTruthy();
    expect(screen.getByText('Queue with party')).toBeTruthy();
    expect(screen.getByText(PARTY_FOOTER, verbatim)).toBeTruthy();
  });
});

describe('HUD layout editor — frame 244:1722', () => {
  it('renders the toolbar, the hint bar and no panel', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setRoute({ name: 'hud-editor' }));
    const { container } = render(<App />);
    expect(screen.getByText('HUD layout')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Snap' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Grid' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Reset' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Done' })).toBeTruthy();
    expect(screen.getByText(EDITOR_HINT, verbatim)).toBeTruthy();
    expect(container.querySelector('.v-panel')).toBeNull();
  });

  it('makes the HUD widgets interactive, unlike the HUD layer proper', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setRoute({ name: 'hud-editor' }));
    const { container } = render(<App />);
    expect(container.querySelector('.hud-layer--editor')).not.toBeNull();
  });

  it('Snap is on by default, as the frame draws it', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setRoute({ name: 'hud-editor' }));
    render(<App />);
    expect(screen.getByRole('button', { name: 'Snap' }).getAttribute('aria-pressed')).toBe('true');
  });
});

describe('Quick palette — frame 244:1900', () => {
  it('renders the input, both captions and the footer hints', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setPaletteOpen(true));
    render(<App />);
    expect(screen.getByRole('dialog', { name: 'Quick palette' })).toBeTruthy();
    expect(screen.getByText('Actions')).toBeTruthy();
    for (const word of ['move', 'run', 'settings', 'close']) {
      expect(screen.getByText(word)).toBeTruthy();
    }
  });

  it('reproduces the frame’s result order for `fullb`', () => {
    set(() => useVoidStore.getState().applyMenu(true));
    set(() => useVoidStore.getState().setPaletteOpen(true));
    const { container } = render(<App />);
    const input = container.querySelector('.v-palette__query input') as HTMLInputElement;
    fireEvent.change(input, { target: { value: 'fullb' } });
    const titles = [...container.querySelectorAll('.v-palette__title')].map((el) => el.textContent);
    expect(titles[0]).toBe('Toggle Fullbright');
    expect(titles[1]).toBe('Fullbright settings');
  });
});
