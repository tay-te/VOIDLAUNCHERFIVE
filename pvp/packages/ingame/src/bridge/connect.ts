/**
 * Boot: pick a bridge, subscribe the nine channels, report focus.
 *
 * §6.5 / bridge.json — Java → JS is push through `void.on(...)`; JS → Java is a
 * synchronous call. There is no fetch, no XHR and no socket in this bundle: the
 * page is loaded off the JAR classpath and the machine may have no internet.
 */

import { createFakeVoid, installVoidShim, type FakeVoid, type VoidBridge } from './protocol';
import { useVoidStore } from '@/store/store';

let bridge: VoidBridge | null = null;
let fake: FakeVoid | null = null;

/** The bridge, once {@link connectBridge} has run. */
export function getVoid(): VoidBridge {
  if (!bridge) throw new Error('window.void is not connected yet — call connectBridge() first');
  return bridge;
}

/** True when the app is running against `createFakeVoid()` rather than the mod. */
export function isDebugBridge(): boolean {
  return fake !== null;
}

/**
 * True while a text input owns the keyboard.
 *
 * `VoidMenuScreen` asks this before it acts on Escape (§6.3): with a search
 * field focused, Escape must reach the page so it can leave the field, and only
 * an unfocused Escape closes the screen. Read straight off the document — no
 * store round-trip, so it can never be one render behind the real focus.
 */
export function hasTextFocus(): boolean {
  if (typeof document === 'undefined') return false;
  const el = document.activeElement as HTMLElement | null;
  if (!el) return false;
  if (el.isContentEditable) return true;
  const tag = el.tagName;
  if (tag === 'TEXTAREA') return true;
  if (tag !== 'INPUT') return false;
  const type = (el as HTMLInputElement).type;
  return type !== 'checkbox' && type !== 'radio' && type !== 'button' && type !== 'submit';
}

/**
 * True when the page will consume the next Escape itself, so the host must not close the menu.
 *
 * **Escape means "up one level".** That is the whole model, and it is decided here because Java
 * has to know the answer before it decides whether to forward the key at all
 * (`VoidMenuScreen.keyPressed` → `UiHost.keepsEscape()`, polled once a frame):
 *
 * ```
 * a focused text field   gives up focus            (MenuLayer)
 * the quick palette      closes, menu stays        (QuickPalette)
 * a mod's page           back to the grid          (MenuLayer)
 * the settings page      back to the grid          (MenuLayer)
 * the HUD editor         back to the grid          (MenuLayer)
 * the grid               closes the menu           (Java)
 * ```
 *
 * A keybind capture is not in the table and must not be: `VoidMenuScreen` consumes Escape for the
 * capture *before* it asks this, so cancelling a capture beats every row above. That ordering is
 * the reason this can be a plain predicate rather than a priority list.
 *
 * Read straight off the document and the store, never from a render — the answer has to be right
 * for the frame the key lands on, and both sources are synchronous.
 */
export function keepsEscape(): boolean {
  if (hasTextFocus()) return true;
  const state = useVoidStore.getState();
  if (state.paletteOpen) return true;
  const route = state.route.name;
  return route === 'mod' || route === 'settings' || route === 'hud-editor';
}

export interface ConnectResult {
  bridge: VoidBridge;
  /** True when the fake bridge is in use, i.e. the DEBUG badge should show. */
  debug: boolean;
  /** Tear down every subscription. Used by tests. */
  dispose(): void;
}

export interface ConnectOptions {
  /** Force the fake bridge, for tests and the harness. */
  forceFake?: boolean;
  /** Start the fake bridge's 20 Hz timer. Off in tests, on in the browser. */
  runFakeClock?: boolean;
}

/**
 * Install `window.void` and wire it to the store.
 *
 * In game the Java host has already installed `window.__void_native`, and
 * `installVoidShim()` builds the bridge on top of it. In a browser — or with
 * `?debug` in the query string — `createFakeVoid()` stands in, playing the part
 * of Java: it owns the loadout library, clamps what it is given, and owns the
 * Right-Shift key, because the mod's `KeyBinding` does in game (§6.3).
 */
export function connectBridge(options: ConnectOptions = {}): ConnectResult {
  const debugRequested =
    options.forceFake === true ||
    (typeof location !== 'undefined' && /(^|[?&])debug($|[=&])/.test(location.search));
  const hasNative = typeof window !== 'undefined' && typeof window.__void_native === 'function';

  if (hasNative && !debugRequested) {
    // `index.html` loads `./void-shim.js` before this bundle, and that file — shipped in
    // the JAR by the mod, source of truth `assets/void/shim/void-shim.js` — has already
    // built `window.void` on top of `window.__void_native`. Use it rather than replacing
    // it, so the object Java pushes into (`window.void.__emit(...)`) is the object this
    // page listens on. `installVoidShim()` is the fallback for a host that installed
    // `__void_native` but no shim, and is the same semantics either way (CONTRACTS.md,
    // "The bridge shim").
    bridge = window.void?.__isVoidBridge === true ? window.void : installVoidShim();
    fake = null;
  } else {
    fake = createFakeVoid({ menuOpen: debugRequested });
    fake.install();
    bridge = fake;
  }

  // The shim's own __hasFocus tracks the menu channel. The Java side needs the
  // narrower question — is a text field eating the keyboard right now — so the
  // app owns this one. Assigning it here keeps `window.void.__hasFocus()`
  // correct for the host no matter which bridge is underneath.
  bridge.__hasFocus = hasTextFocus;
  // …and the wider question Java actually asks, which `__hasFocus` used to answer by proxy. Still
  // an optional member of `VoidBridge`, and deliberately: an older host that has never heard of
  // it falls back to `__hasFocus` and gets the old behaviour, and this page in an older host does
  // the same. It used to be attached through a cast, with a comment saying it should graduate
  // into that interface — it has.
  bridge.__keepsEscape = keepsEscape;

  const store = useVoidStore.getState();

  const offs = [
    // `loadouts` before `loadout`: the library is whole-state and the active loadout
    // wins where they overlap. Java pushes them in that order on `init`, and the fake
    // bridge's `emitInitialState` does the same.
    bridge.on('loadouts', store.applyLoadouts),
    bridge.on('loadout', store.applyLoadout),
    bridge.on('setting', store.applySetting),
    bridge.on('keys', store.applyKeys),
    bridge.on('tick', store.applyTick),
    bridge.on('server', store.applyServer),
    // The eighth channel, and the only immutable one: who is playing. No cast any more —
    // `bridge.json` carries `session` and its payload now, so `@void/protocol`'s event union
    // knows about it and `SessionInfo` is the generated type.
    //
    // **Adding a channel is never only this line.** `void-shim.js`'s `EVENTS` is a closed list:
    // `on` returns a no-op subscription for a name it does not know and `__emit` drops every
    // envelope, silently, with no error anywhere. `session` was pushed by Java and read here for
    // days without arriving, because it was missing from that list. Five files or none —
    // `bridge.json`, `VoidBridge`, the shim, `VOID_EVENTS`, `createFakeVoid`.
    bridge.on('session', store.applySession),
    // The ninth: the client's own settings, which until now lived only in `LiveState` and could
    // be neither read nor written by the page. The Settings page is what asked for it.
    bridge.on('settings', store.applyGlobals),
    bridge.on('menu', store.applyMenu),
  ];

  if (fake) {
    fake.emitInitialState();
    if (options.runFakeClock ?? true) fake.start();
  }

  return {
    bridge,
    debug: fake !== null,
    dispose() {
      for (const off of offs) off();
      fake?.destroy();
      fake = null;
      bridge = null;
    },
  };
}
