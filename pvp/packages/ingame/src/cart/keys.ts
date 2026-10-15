/**
 * Keys as the keyboard map and the conflict check see them.
 *
 * **Vanilla's binds are the player's own** once Java has pushed `binds` (bridge.json,
 * `binds_payload`: `GameOptions.allKeys` with the keys they are on now). Until then — a dev
 * page, an older jar — the table is the 1.8.9 defaults, and the copy says "(vanilla default)".
 * F1 and F3 are hard-coded in vanilla, not bindings, so they are on the board either way.
 *
 * Names are LWJGL 2 key names, the form every stored binding uses (`RSHIFT`, `LMENU`, `F2`).
 */

import { useVoidStore } from '@/store/store';
import type { VanillaBind as VanillaBindRow } from '@/bridge/protocol';
import type { KeybindRow } from '@/menu/keybind-overview';

export interface VanillaBind {
  /** Short label printed on the key. */
  label: string;
  /** The consequence, for the conflict sentence. */
  does: string;
}

/** Short labels and consequences by vanilla translation key — the binding, wherever it is. */
const BY_ID: Readonly<Record<string, VanillaBind>> = {
  'key.attack': { label: 'Attack', does: 'attacks' },
  'key.use': { label: 'Use', does: 'uses your item' },
  'key.forward': { label: 'Forward', does: 'walks forward' },
  'key.left': { label: 'Left', does: 'strafes left' },
  'key.back': { label: 'Back', does: 'walks back' },
  'key.right': { label: 'Right', does: 'strafes right' },
  'key.jump': { label: 'Jump', does: 'jumps' },
  'key.sneak': { label: 'Sneak', does: 'sneaks' },
  'key.sprint': { label: 'Sprint', does: 'sprints' },
  'key.drop': { label: 'Drop', does: 'drops your held item' },
  'key.inventory': { label: 'Inventory', does: 'opens your inventory' },
  'key.chat': { label: 'Chat', does: 'opens chat' },
  'key.playerlist': { label: 'Players', does: 'shows the player list' },
  'key.pickItem': { label: 'Pick block', does: 'picks the block you look at' },
  'key.command': { label: 'Command', does: 'opens the command line' },
  'key.screenshot': { label: 'Screenshot', does: 'takes a screenshot' },
  'key.togglePerspective': { label: 'Perspective', does: 'changes perspective' },
  'key.smoothCamera': { label: 'Cinematic', does: 'toggles the cinematic camera' },
  'key.streamStartStop': { label: 'Stream', does: 'opens the stream screen' },
  'key.streamPauseUnpause': { label: 'Pause stream', does: 'pauses the stream' },
  'key.streamCommercial': { label: 'Commercial', does: 'shows stream commercials' },
  'key.streamToggleMic': { label: 'Mic', does: 'toggles the stream mic' },
  'key.fullscreen': { label: 'Fullscreen', does: 'toggles fullscreen' },
  'key.spectatorOutlines': { label: 'Outlines', does: 'highlights players (spectator)' },
  ...Object.fromEntries(
    Array.from({ length: 9 }, (_, i) => [`key.hotbar.${i + 1}`, { label: `Slot ${i + 1}`, does: `selects hotbar slot ${i + 1}` }]),
  ),
};

/** Vanilla keys that are not bindings: 1.8.9 hard-codes them, so no player can move them. */
const HARD_CODED: Readonly<Record<string, VanillaBind>> = {
  F1: { label: 'Hide HUD', does: 'hides the HUD' },
  F3: { label: 'Debug', does: 'opens the debug screen' },
};

/** The 1.8.9 defaults, keyed by key — the table before (or without) a `binds` push. */
export const VANILLA_BINDS: Readonly<Record<string, VanillaBind>> = {
  W: BY_ID['key.forward']!,
  A: BY_ID['key.left']!,
  S: BY_ID['key.back']!,
  D: BY_ID['key.right']!,
  SPACE: BY_ID['key.jump']!,
  LSHIFT: BY_ID['key.sneak']!,
  LCONTROL: BY_ID['key.sprint']!,
  Q: BY_ID['key.drop']!,
  E: BY_ID['key.inventory']!,
  T: BY_ID['key.chat']!,
  SLASH: BY_ID['key.command']!,
  TAB: BY_ID['key.playerlist']!,
  F1: HARD_CODED.F1!,
  F2: BY_ID['key.screenshot']!,
  F3: HARD_CODED.F3!,
  F5: BY_ID['key.togglePerspective']!,
  F11: BY_ID['key.fullscreen']!,
  ...Object.fromEntries(Array.from({ length: 9 }, (_, i) => [String(i + 1), BY_ID[`key.hotbar.${i + 1}`]!])),
};

const tables = new WeakMap<readonly VanillaBindRow[], Readonly<Record<string, VanillaBind>>>();

/**
 * Vanilla's table keyed by key: the player's binds when Java has sent them, else the defaults.
 * Unbound and mouse binds drop out (the board is a keyboard; a VOID key on a mouse button is
 * rare enough to skip); when two vanilla binds share a key the first in `allKeys` order names it.
 * A binding another mod added has no short label here, so it is shown by the name vanilla
 * Controls gives it.
 */
export function vanillaTable(binds: readonly VanillaBindRow[] | null | undefined): Readonly<Record<string, VanillaBind>> {
  if (!binds) return VANILLA_BINDS;
  const cached = tables.get(binds);
  if (cached) return cached;
  const table: Record<string, VanillaBind> = {};
  for (const row of binds) {
    if (!row.key || row.key === 'NONE' || row.key.startsWith('MOUSE') || table[row.key]) continue;
    table[row.key] = BY_ID[row.id] ?? { label: row.label || row.id, does: `is vanilla's ${row.label || row.id}` };
  }
  for (const [key, bind] of Object.entries(HARD_CODED)) if (!table[key]) table[key] = bind;
  tables.set(binds, table);
  return table;
}

/** The vanilla table the page should warn against, and whether it is the player's own. */
export function useVanillaBinds(): { table: Readonly<Record<string, VanillaBind>>; real: boolean } {
  const binds = useVoidStore((s) => s.vanillaBinds);
  return { table: vanillaTable(binds), real: binds !== null };
}

/** What a candidate key would collide with: the first VOID binding, else vanilla's. */
export interface KeyConflict {
  kind: 'void' | 'vanilla';
  /** The thing already on the key. */
  label: string;
  /** For vanilla: what pressing it does. */
  does?: string;
  /** For vanilla: true when it is the player's own bind, false when it is the 1.8.9 default. */
  real?: boolean;
}

export function conflictFor(
  key: string,
  rows: readonly KeybindRow[],
  selfId: string,
  binds: readonly VanillaBindRow[] | null = null,
): KeyConflict | null {
  if (!key || key === 'NONE') return null;
  const other = rows.find((row) => row.id !== selfId && row.key === key);
  if (other) return { kind: 'void', label: other.label };
  const vanilla = vanillaTable(binds)[key];
  if (vanilla) return { kind: 'vanilla', label: vanilla.label, does: vanilla.does, real: binds !== null };
  return null;
}

/** Keyboard layout for the overview (643:2): [LWJGL name, cap label, width in units]. */
export const KEYBOARD: readonly (readonly [string, string, number])[][] = [
  [
    ['ESCAPE', 'Esc', 1], ['F1', 'F1', 1], ['F2', 'F2', 1], ['F3', 'F3', 1], ['F4', 'F4', 1], ['F5', 'F5', 1],
    ['F6', 'F6', 1], ['F7', 'F7', 1], ['F8', 'F8', 1], ['F9', 'F9', 1], ['F10', 'F10', 1], ['F11', 'F11', 1], ['F12', 'F12', 1],
  ],
  [
    ['GRAVE', '`', 1], ['1', '1', 1], ['2', '2', 1], ['3', '3', 1], ['4', '4', 1], ['5', '5', 1], ['6', '6', 1], ['7', '7', 1],
    ['8', '8', 1], ['9', '9', 1], ['0', '0', 1], ['MINUS', '-', 1], ['EQUALS', '=', 1], ['BACK', 'Back', 2],
  ],
  [
    ['TAB', 'Tab', 1.5], ['Q', 'Q', 1], ['W', 'W', 1], ['E', 'E', 1], ['R', 'R', 1], ['T', 'T', 1], ['Y', 'Y', 1], ['U', 'U', 1],
    ['I', 'I', 1], ['O', 'O', 1], ['P', 'P', 1], ['LBRACKET', '[', 1], ['RBRACKET', ']', 1], ['BACKSLASH', '\\', 1.5],
  ],
  [
    ['CAPITAL', 'Caps', 1.75], ['A', 'A', 1], ['S', 'S', 1], ['D', 'D', 1], ['F', 'F', 1], ['G', 'G', 1], ['H', 'H', 1],
    ['J', 'J', 1], ['K', 'K', 1], ['L', 'L', 1], ['SEMICOLON', ';', 1], ['APOSTROPHE', "'", 1], ['RETURN', 'Enter', 2.25],
  ],
  [
    ['LSHIFT', 'LShift', 2.25], ['Z', 'Z', 1], ['X', 'X', 1], ['C', 'C', 1], ['V', 'V', 1], ['B', 'B', 1], ['N', 'N', 1],
    ['M', 'M', 1], ['COMMA', ',', 1], ['PERIOD', '.', 1], ['SLASH', '/', 1], ['RSHIFT', 'RShift', 2.75],
  ],
  [
    ['LCONTROL', 'LCtrl', 1.25], ['LMETA', 'Win', 1.25], ['LMENU', 'LAlt', 1.25], ['SPACE', 'Space', 6.25],
    ['RMENU', 'RAlt', 1.25], ['APPS', 'Menu', 1.25], ['RMETA', 'Fn', 1.25], ['RCONTROL', 'RCtrl', 1.25],
  ],
];

/** DOM `KeyboardEvent` → LWJGL name, for "press any key to find what it does". */
export function lwjglName(event: KeyboardEvent): string | null {
  const code = event.code;
  if (/^Key[A-Z]$/.test(code)) return code.slice(3);
  if (/^Digit[0-9]$/.test(code)) return code.slice(5);
  if (/^F([1-9]|1[0-2])$/.test(code)) return code;
  const map: Record<string, string> = {
    Space: 'SPACE', ShiftLeft: 'LSHIFT', ShiftRight: 'RSHIFT', ControlLeft: 'LCONTROL', ControlRight: 'RCONTROL',
    AltLeft: 'LMENU', AltRight: 'RMENU', Tab: 'TAB', Enter: 'RETURN', Backspace: 'BACK', Backquote: 'GRAVE',
    Minus: 'MINUS', Equal: 'EQUALS', BracketLeft: 'LBRACKET', BracketRight: 'RBRACKET', Backslash: 'BACKSLASH',
    Semicolon: 'SEMICOLON', Quote: 'APOSTROPHE', Comma: 'COMMA', Period: 'PERIOD', Slash: 'SLASH', CapsLock: 'CAPITAL',
    MetaLeft: 'LMETA', MetaRight: 'RMETA', ContextMenu: 'APPS', Escape: 'ESCAPE',
  };
  if (map[code]) return map[code]!;
  // In game `code` can be empty (the host synthesises events); fall back to the printable key.
  const k = event.key;
  if (k && k.length === 1 && /[a-z0-9]/i.test(k)) return k.toUpperCase();
  return null;
}
