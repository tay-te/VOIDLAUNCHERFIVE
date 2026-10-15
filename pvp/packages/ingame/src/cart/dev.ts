/**
 * `window.__voidDev.go(step)` — a screenshot hook for the in-game UI tour.
 *
 * The mod's bench harness (`-Dvoid.bench=ui`, PerfBench) opens the real menu and then asks the
 * page, through `UiHost.devEval`, to show one state: a tab, a search, a mod's bench, the keybind
 * conflict. Every step only drives UI state the player can reach by hand — routes, the search
 * field, the cursor — so it cannot show anything the menu would not. It is inert unless called,
 * and nothing in a player's session calls it.
 *
 * Steps (string in, `ok:<step>` or `error:<why>` out):
 *   mods · mods:hover=<id> · search:<query> · pinned · settings:<id> · capture:<id>
 *   loadouts · keybinds · friends · cosmetics · emotes · hud-editor · toast · close
 */

import { create } from 'zustand';

import { MOD_REGISTRY, type HUDModId, type ModId } from '@/bridge/protocol';
import { useVoidStore } from '@/store/store';
import type { CaptureState } from './Bench';

interface DevState {
  /** The bench's first keybind block opens in this state (the conflict screenshot). */
  captureInitial?: CaptureState;
  /** The Cosmetics tab opens with the emote wheel up. */
  emoteWheel?: boolean;
}

export const useDev = create<DevState>(() => ({}));

export function useDevState(): DevState {
  return useDev();
}

function isMod(id: string): id is ModId {
  return Object.prototype.hasOwnProperty.call(MOD_REGISTRY, id);
}

export function devGo(step: string): string {
  const s = useVoidStore.getState();
  const [name, arg = ''] = step.split(/:(.*)/s);
  useDev.setState({ captureInitial: undefined, emoteWheel: false });
  const tab = (route: 'mods' | 'loadouts' | 'keybinds' | 'friends' | 'cosmetics') => {
    s.setModSearch('');
    s.setModFilter('all');
    s.setRoute({ name: route });
  };
  switch (name) {
    case 'mods': {
      tab('mods');
      const hover = /^hover=(.+)$/.exec(arg)?.[1];
      if (hover && isMod(hover)) s.selectMod(hover);
      return `ok:${step}`;
    }
    case 'search':
      tab('mods');
      s.setModSearch(arg);
      return `ok:${step}`;
    case 'pinned':
      tab('mods');
      s.setQuickPins([]);
      s.setModFilter('pinned');
      return `ok:${step}`;
    case 'settings':
    case 'capture': {
      if (!isMod(arg)) return `error:no mod ${arg}`;
      tab('mods');
      if (name === 'capture') {
        useDev.setState({
          captureInitial: { phase: 'conflict', key: 'Q', conflict: { kind: 'vanilla', label: 'Drop', does: 'drops your held item' } },
        });
      }
      s.openMod(arg);
      return `ok:${step}`;
    }
    case 'loadouts':
    case 'keybinds':
    case 'friends':
    case 'cosmetics':
      tab(name);
      return `ok:${step}`;
    case 'emotes':
      tab('cosmetics');
      useDev.setState({ emoteWheel: true });
      return `ok:${step}`;
    case 'hud-editor':
      s.setEditorTarget((isMod(arg) ? arg : 'keystrokes') as HUDModId);
      s.setRoute({ name: 'hud-editor' });
      return `ok:${step}`;
    case 'toast':
      s.pushToast({ kind: 'loadout', title: arg || 'Sword PvP inserted', detail: 'Loadout switched' });
      return `ok:${step}`;
    case 'close':
      s.closeMenu();
      return `ok:${step}`;
    default:
      return `error:unknown step ${step}`;
  }
}

declare global {
  interface Window {
    __voidDev?: { go(step: string): string };
  }
}

export function installDevHook(): void {
  if (typeof window === 'undefined') return;
  window.__voidDev = { go: devGo };
}
