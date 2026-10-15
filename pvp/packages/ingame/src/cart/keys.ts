/**
 * Keys as the keyboard map and the conflict check see them.
 *
 * **Vanilla's binds are the 1.8.9 defaults, not the player's.** Nothing on the bridge carries
 * `GameOptions.keyBindings` (keybind-overview.ts says the same), so a player who rebound Drop
 * away from Q still gets the warning on Q. The table is honest about being defaults — its copy
 * says "(vanilla default)" — and it is the difference between a silent clash and a sentence.
 *
 * Names are LWJGL 2 key names, the form every stored binding uses (`RSHIFT`, `LMENU`, `F2`).
 */

import type { KeybindRow } from '@/menu/keybind-overview';

export interface VanillaBind {
  /** Short label printed on the key. */
  label: string;
  /** The consequence, for the conflict sentence. */
  does: string;
}

export const VANILLA_BINDS: Readonly<Record<string, VanillaBind>> = {
  W: { label: 'Forward', does: 'walks forward' },
  A: { label: 'Left', does: 'strafes left' },
  S: { label: 'Back', does: 'walks back' },
  D: { label: 'Right', does: 'strafes right' },
  SPACE: { label: 'Jump', does: 'jumps' },
  LSHIFT: { label: 'Sneak', does: 'sneaks' },
  LCONTROL: { label: 'Sprint', does: 'sprints' },
  Q: { label: 'Drop', does: 'drops your held item' },
  E: { label: 'Inventory', does: 'opens your inventory' },
  T: { label: 'Chat', does: 'opens chat' },
  SLASH: { label: 'Command', does: 'opens the command line' },
  TAB: { label: 'Players', does: 'shows the player list' },
  F1: { label: 'Hide HUD', does: 'hides the HUD' },
  F2: { label: 'Screenshot', does: 'takes a screenshot' },
  F3: { label: 'Debug', does: 'opens the debug screen' },
  F5: { label: 'Perspective', does: 'changes perspective' },
  F11: { label: 'Fullscreen', does: 'toggles fullscreen' },
  ...Object.fromEntries(
    Array.from({ length: 9 }, (_, i) => [String(i + 1), { label: `Slot ${i + 1}`, does: `selects hotbar slot ${i + 1}` }]),
  ),
};

/** What a candidate key would collide with: the first VOID binding, else vanilla's default. */
export interface KeyConflict {
  kind: 'void' | 'vanilla';
  /** The thing already on the key. */
  label: string;
  /** For vanilla: what pressing it does. */
  does?: string;
}

export function conflictFor(key: string, rows: readonly KeybindRow[], selfId: string): KeyConflict | null {
  if (!key || key === 'NONE') return null;
  const other = rows.find((row) => row.id !== selfId && row.key === key);
  if (other) return { kind: 'void', label: other.label };
  const vanilla = VANILLA_BINDS[key];
  if (vanilla) return { kind: 'vanilla', label: vanilla.label, does: vanilla.does };
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
