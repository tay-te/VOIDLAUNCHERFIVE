// Reaching the launcher's screens by name.
//
// The launcher has no router: a screen is a value in the zustand store `useUi`
// (src/stores/ui.ts). Under `vite` dev the page can import that same module instance by
// its URL, so "store" navigation sets the state directly — instant, no view transition,
// which is what you want before frame 0. "click" navigation presses the real control
// (a navbar tab, the friends pill, the search box, the profile menu) and so plays the
// app's own view transition; use it inside a timeline when the transition is the shot.

export const STORE_MODULE = '/src/stores/ui.ts';

/** name -> what it does, for `webcap screens` and the README. */
export const SCREENS = {
  home: 'PvP tab (Home)',
  store: 'Store tab',
  'store-item:<id>': 'Store with an item sheet open',
  mods: 'Edit mods grid (a page of PvP)',
  'modsetup[:<modId>]': "a mod's setup page (default keystrokes)",
  modpacks: 'Coming soon: Modpacks',
  servers: 'Coming soon: Servers',
  quests: 'Coming soon: Quests',
  settings: 'overlay: Settings panel',
  friends: 'overlay: Friends drawer',
  palette: 'overlay: command palette (Ctrl/Cmd+K)',
  notes: 'overlay: release notes',
  log: 'overlay: game log drawer',
  'layout:<classic|stage|bento|lobby>': 'Home layout under review',
  'material:<flat|glass>': 'shell material',
};

/**
 * Page-side function source. `names` is a list such as ["home", "friends"]; overlays
 * stack on whatever screen came before them in the list.
 */
export const NAV_BY_STORE = `async (names) => {
  const { useUi } = await import(${JSON.stringify(STORE_MODULE)});
  const ui = useUi;
  const base = { paletteOpen: false };
  for (const full of names) {
    const [name, arg] = full.split(':');
    switch (name) {
      case 'home': case 'store': case 'modpacks': case 'servers': case 'quests':
        ui.setState({ ...base, screen: name, modSetup: null, ...(name !== 'store' ? { storeItem: null } : {}) });
        break;
      case 'mods':
        ui.setState({ ...base, screen: 'mods', modSetup: null, storeItem: null });
        break;
      case 'modsetup': {
        const id = arg || 'keystrokes';
        ui.setState({ ...base, screen: 'mods', modSetup: id, selectedMod: id, storeItem: null });
        break;
      }
      case 'store-item':
        if (!arg) throw new Error('store-item needs an id: store-item:<id>');
        ui.setState({ ...base, screen: 'store', storeItem: arg, friendsOpen: false });
        break;
      case 'settings': ui.setState({ settingsOpen: true, paletteOpen: false }); break;
      case 'friends': ui.getState().setFriendsOpen(true); break;
      case 'palette': ui.setState({ paletteOpen: true }); break;
      case 'notes': ui.setState({ notesOpen: true }); break;
      case 'log': ui.setState({ logOpen: true }); break;
      case 'layout': ui.getState().setHomeLayout(arg); break;
      case 'material': ui.getState().setMaterial(arg); break;
      default: throw new Error('unknown screen ' + JSON.stringify(full));
    }
  }
  return ui.getState().screen;
}`;

/**
 * "click" navigation: the real control(s) to press, in order. Each step is a selector and
 * an optional text the element must contain.
 */
export function clickPath(name) {
  const [n] = name.split(':');
  const tab = (label) => [{ sel: '.navbar .navtab', text: label }];
  switch (n) {
    case 'home':
      return tab('PvP');
    case 'store':
      return tab('Store');
    case 'modpacks':
      return tab('Modpacks');
    case 'servers':
      return tab('Servers');
    case 'quests':
      return tab('Quests');
    case 'mods':
      return [...tab('PvP'), { sel: '.strip .ghostlink', text: 'Edit mods' }];
    case 'friends':
      return [{ sel: '.navbar .navpill--friends' }];
    case 'palette':
      return [{ sel: '.navbar .navsearch' }];
    case 'settings':
      return [{ sel: '.navbar .navpill--profile' }, { sel: '.popmenu--profile .popmenu__item', text: 'Settings' }];
    default:
      return null;
  }
}
