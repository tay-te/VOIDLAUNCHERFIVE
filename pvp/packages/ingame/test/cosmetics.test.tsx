/**
 * The Cosmetics tab — the paper-doll wardrobe, the emote wheel editor and the Crescent wheel —
 * against the real fake bridge, whose cosmetics follow Java's rules (`CosmeticState`): free items
 * are owned, an unowned equip is refused, tints stick only to tintable items, the wheel keeps
 * owned emotes, once each, at most eight.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';

import { connectBridge, getVoid, keepsEscape } from '@/bridge/connect';
import { resetDerivedState, useVoidStore } from '@/store/store';
import { App } from '@/App';
import { COSMETIC_CATALOG, type FakeVoid } from '@/bridge/protocol';
import { priceLabel, useCosmetics } from '@/cart/cosmetics-store';
import { crescent } from '@/cart/CrescentWheel';
import { devGo } from '@/cart/dev';

function set(mutate: () => void) {
  act(() => {
    mutate();
  });
}
const key = (k: string) =>
  set(() => {
    fireEvent.keyDown(window, { key: k });
  });

let dispose: () => void;

beforeEach(() => {
  resetDerivedState();
  useVoidStore.setState({ route: { name: 'mods' }, back: 'mods', paletteOpen: false, context: 'lobby', toasts: [] });
  useCosmetics.setState({ self: null, owned: [], playing: null, wheelOpen: false });
  ({ dispose } = connectBridge({ forceFake: true, runFakeClock: false }));
  set(() => useVoidStore.getState().applyMenu(true));
});

afterEach(() => {
  cleanup();
  dispose();
});

const fake = () => getVoid() as FakeVoid;
const toCosmetics = () => set(() => useVoidStore.getState().setRoute({ name: 'cosmetics' }));

describe('the wardrobe', () => {
  it('reads the cosmetics push: what is worn, and what is owned', () => {
    const { self, owned } = useCosmetics.getState();
    expect(self).not.toBeNull();
    expect(self!.cape).toBe(fake().getCosmetics().self.cape);
    expect(owned).toEqual(fake().getCosmetics().owned);
    // Free items are owned by everyone.
    for (const item of COSMETIC_CATALOG.filter((i) => i.availability === 'free')) expect(owned).toContain(item.id);
  });

  it('draws the doll with every worn item, seven slot cards, and the selected slot’s items', () => {
    toCosmetics();
    const { container } = render(<App />);
    expect(container.querySelectorAll('.c-slot')).toHaveLength(7);
    expect(container.querySelector('.c-doll')).not.toBeNull();
    const cape = useCosmetics.getState().self!.cape;
    expect(container.querySelector('.c-doll__item[data-slot="cape"]')).not.toBeNull();
    expect(container.querySelector(`.c-cos__card[data-cosmetic-id="${cape}"]`)?.classList.contains('is-wearing')).toBe(true);
    // Front and back.
    fireEvent.click(screen.getByRole('radio', { name: 'Front' }));
    expect(container.querySelector('.c-doll--front')).not.toBeNull();
  });

  it('wears and takes off an owned item through equipCosmetic, binding to what Java stored', () => {
    toCosmetics();
    const { container } = render(<App />);
    const owned = useCosmetics.getState().owned;
    const capes = COSMETIC_CATALOG.filter((i) => i.kind === 'cape' && owned.includes(i.id));
    const other = capes.find((i) => i.id !== useCosmetics.getState().self!.cape)!;
    fireEvent.click(container.querySelector(`.c-cos__card[data-cosmetic-id="${other.id}"] .c-cos__hit`)!);
    expect(useCosmetics.getState().self!.cape).toBe(other.id);
    expect(fake().getCosmetics().self.cape).toBe(other.id);
    fireEvent.click(container.querySelector(`.c-cos__card[data-cosmetic-id="${other.id}"] .c-cos__hit`)!);
    expect(useCosmetics.getState().self!.cape).toBeNull();
  });

  it('routes a locked item to the launcher Store with its price in USD, and buys nothing', () => {
    toCosmetics();
    const { container } = render(<App />);
    fireEvent.click(screen.getByRole('tab', { name: /All/ }));
    const owned = useCosmetics.getState().owned;
    const locked = COSMETIC_CATALOG.find((i) => i.kind === 'cape' && !owned.includes(i.id))!;
    const card = container.querySelector(`.c-cos__card[data-cosmetic-id="${locked.id}"]`)!;
    expect(card.textContent).toContain('Get in launcher Store');
    expect(card.textContent).toContain(priceLabel(locked));
    expect(priceLabel(locked)).toMatch(/^\$\d+\.\d\d$/);
    expect(container.textContent).not.toMatch(/\bVC\b/);
    const before = useCosmetics.getState().self!.cape;
    const open = vi.spyOn(getVoid(), 'openStore');
    fireEvent.click(card.querySelector('.c-cos__hit')!);
    expect(open).toHaveBeenCalledWith(locked.id);
    expect(useCosmetics.getState().self!.cape).toBe(before);
    expect(useVoidStore.getState().toasts.at(-1)?.detail).toContain('Opened in the VOID launcher');

    // No launcher link: nothing claims it opened; the notice says where to go.
    open.mockReturnValue(false);
    fireEvent.click(card.querySelector('.c-cos__hit')!);
    expect(useVoidStore.getState().toasts.at(-1)?.detail).toContain('Get it in the VOID launcher');
    open.mockRestore();
  });

  it('tints a tintable item and clears it', () => {
    const tintable = COSMETIC_CATALOG.find((i) => i.tintable && i.kind !== 'emote')!;
    set(() => fake().applyCosmetics({ owned: [...useCosmetics.getState().owned, tintable.id] }));
    set(() => useCosmetics.getState().equip(tintable.kind as never, tintable.id));
    toCosmetics();
    render(<App />);
    fireEvent.click(screen.getByRole('button', { name: new RegExp(`^${tintable.kind === 'head' ? 'Head' : tintable.kind === 'cape' ? 'Cape' : '.*'}`, 'i') }));
    const swatch = screen.getAllByRole('radio', { name: /^#/ })[1]!;
    fireEvent.click(swatch);
    const tints = useCosmetics.getState().self!.tints as Record<string, string>;
    expect(tints[tintable.kind]).toMatch(/^#[0-9a-f]{6}$/);
    fireEvent.click(swatch);
    expect((useCosmetics.getState().self!.tints as Record<string, string> | undefined)?.[tintable.kind]).toBeUndefined();
  });

  it('shows the loading state until the first push', () => {
    set(() => useCosmetics.setState({ self: null }));
    toCosmetics();
    const { container } = render(<App />);
    expect(container.querySelector('.c-cos__loading')?.textContent).toContain('Fetching your wardrobe');
  });
});

describe('the emote wheel editor', () => {
  it('puts an owned emote on the selected slot through setEmoteWheel, and clears it', () => {
    set(() => devGo('cosmetics:emotes'));
    const { container } = render(<App />);
    const owned = useCosmetics.getState().owned;
    const emote = COSMETIC_CATALOG.find((i) => i.kind === 'emote' && owned.includes(i.id) && !useCosmetics.getState().self!.emotes.includes(i.id))!;
    // Slot 3 is empty in the fixture.
    fireEvent.click(screen.getByRole('button', { name: /^Slot 3/ }));
    fireEvent.click(container.querySelector(`.c-cos__card[data-cosmetic-id="${emote.id}"] .c-cos__hit`)!);
    expect(useCosmetics.getState().self!.emotes).toContain(emote.id);
    expect(fake().getCosmetics().self.emotes).toContain(emote.id);
    fireEvent.click(screen.getByRole('button', { name: /^Slot 3/ }));
    fireEvent.click(screen.getByRole('button', { name: 'Clear' }));
    expect(useCosmetics.getState().self!.emotes).not.toContain(emote.id);
  });

  it('writes the cosmetics mod’s switches', () => {
    set(() => devGo('cosmetics:emotes'));
    render(<App />);
    const glow = screen.getByRole('switch', { name: /Glow/ });
    const before = glow.getAttribute('aria-checked') === 'true';
    fireEvent.click(glow);
    expect(screen.getByRole('switch', { name: /Glow/ }).getAttribute('aria-checked')).toBe(String(!before));
  });
});

describe('the Crescent wheel', () => {
  it('lays eight cards on a crescent below the crosshair, left to right', () => {
    const spots = crescent();
    expect(spots).toHaveLength(8);
    for (let i = 1; i < spots.length; i++) expect(spots[i]!.x).toBeGreaterThan(spots[i - 1]!.x);
    expect(Math.min(...spots.map((s) => s.y))).toBeGreaterThan(540);
  });

  it('opens on B, plays with a number key through playEmote, and Escape closes it first', () => {
    toCosmetics();
    const { container } = render(<App />);
    key('b');
    expect(useCosmetics.getState().wheelOpen).toBe(true);
    expect(container.querySelector('.c-wheel')).not.toBeNull();
    expect(keepsEscape()).toBe(true);
    const play = vi.spyOn(getVoid(), 'playEmote');
    key('1');
    expect(play).toHaveBeenCalledWith(useCosmetics.getState().self!.emotes[0]);
    expect(container.querySelector('.c-wheel__playing')?.textContent).toContain('playing');
    set(() => useCosmetics.getState().setWheelOpen(true));
    set(() => {
      fireEvent.keyDown(window, { keyCode: 27 });
    });
    expect(useCosmetics.getState().wheelOpen).toBe(false);
    expect(useVoidStore.getState().route.name).toBe('cosmetics');
  });

  it('lights the emote Java says is playing, and clears it when it ends', () => {
    set(() => fake().startEmote(useCosmetics.getState().self!.emotes[0]! as never));
    expect(useCosmetics.getState().playing).toBe(useCosmetics.getState().self!.emotes[0]);
    set(() => fake().cancelEmote());
    expect(useCosmetics.getState().playing).toBeNull();
  });
});

describe('the world wheel — the emote key held, Java driving', () => {
  it('opens over the HUD with the menu shut, aims by angle, and plays on release', () => {
    set(() => useVoidStore.getState().applyMenu(false));
    const { container } = render(<App />);
    const wheel = useCosmetics.getState().self!.emotes;
    expect(wheel.length).toBeGreaterThan(0);
    set(() => fake().emitWheel({ phase: 'open' }));
    expect(container.querySelector('.c-layer--world .c-wheel--world')).not.toBeNull();
    // A flick down-left points at the first card.
    const play = vi.spyOn(getVoid(), 'playEmote');
    const first = crescent()[0]!;
    set(() => fake().emitWheel({ phase: 'aim', x: (first.x - 960) / 4, y: (first.y - 540) / 4 }));
    expect(container.querySelector('.c-wheel__card.is-pick')?.getAttribute('aria-label')).toMatch(/^1/);
    set(() => fake().emitWheel({ phase: 'release' }));
    expect(play).toHaveBeenCalledWith(wheel[0]);
  });

  it('plays a 1-8 key straight away, and a cancel closes without playing', () => {
    set(() => useVoidStore.getState().applyMenu(false));
    const { container } = render(<App />);
    const play = vi.spyOn(getVoid(), 'playEmote');
    set(() => fake().emitWheel({ phase: 'open' }));
    set(() => fake().emitWheel({ phase: 'key', slot: 1 }));
    expect(play).toHaveBeenCalledWith(useCosmetics.getState().self!.emotes[1]);
    set(() => useCosmetics.getState().setWheelOpen(false));
    set(() => fake().emitWheel({ phase: 'open' }));
    set(() => fake().emitWheel({ phase: 'cancel' }));
    expect(container.querySelector('.c-wheel--world')).toBeNull();
    expect(play).toHaveBeenCalledTimes(1);
  });

  it('lists the emote key in the keybind overview', () => {
    set(() => useVoidStore.getState().setRoute({ name: 'keybinds' }));
    const { container } = render(<App />);
    expect(container.querySelector('[data-key="B"]')?.classList.contains('is-void')).toBe(true);
  });
});
