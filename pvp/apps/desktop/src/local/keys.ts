/**
 * Keyboard capture for the launcher's keybind chips.
 *
 * `@void/ui`'s `KeybindChip` owns the `idle → capturing → idle` state and takes the
 * applied key from a promise, because in game that promise is
 * `void.openKeybindCapture(modId)` — Java is authoritative for what key it actually
 * saw. There is no game here, so this resolves the same promise from the browser's own
 * `KeyboardEvent.code`, mapped onto the LWJGL 2 names
 * `schema/mods.json#/definitions/keybind` accepts.
 *
 * TODO(integrate): when the bridge can answer for the launcher too, `captureKey` is the
 * one function to replace; nothing else in the app touches key names.
 */

/** Browser `KeyboardEvent` → the LWJGL 2 names the schema allows. */
export function keyName(event: KeyboardEvent): string {
  const code = event.code;
  if (/^Key[A-Z]$/.test(code)) return code.slice(3);
  if (/^Digit[0-9]$/.test(code)) return code.slice(5);
  if (/^F([1-9]|1[0-2])$/.test(code)) return code;
  if (/^Numpad[0-9]$/.test(code)) return code.replace('Numpad', 'NUMPAD');
  const map: Record<string, string> = {
    Space: 'SPACE',
    Tab: 'TAB',
    Escape: 'ESCAPE',
    Enter: 'RETURN',
    Backspace: 'BACK',
    Delete: 'DELETE',
    Insert: 'INSERT',
    Home: 'HOME',
    End: 'END',
    PageUp: 'PRIOR',
    PageDown: 'NEXT',
    ArrowUp: 'UP',
    ArrowDown: 'DOWN',
    ArrowLeft: 'LEFT',
    ArrowRight: 'RIGHT',
    ShiftLeft: 'LSHIFT',
    ShiftRight: 'RSHIFT',
    ControlLeft: 'LCONTROL',
    ControlRight: 'RCONTROL',
    AltLeft: 'LMENU',
    AltRight: 'RMENU',
    CapsLock: 'CAPITAL',
    BracketLeft: 'LBRACKET',
    BracketRight: 'RBRACKET',
    Semicolon: 'SEMICOLON',
    Quote: 'APOSTROPHE',
    Comma: 'COMMA',
    Period: 'PERIOD',
    Slash: 'SLASH',
    Backslash: 'BACKSLASH',
    Minus: 'MINUS',
    Equal: 'EQUALS',
    Backquote: 'GRAVE',
  };
  return map[code] ?? 'NONE';
}

/** `RSHIFT` → `R-Shift`, the way the Figma prints it. */
export function prettyKey(value: string): string {
  if (!value || value === 'NONE') return 'None';
  const named: Record<string, string> = {
    RSHIFT: 'R-Shift',
    LSHIFT: 'L-Shift',
    RCONTROL: 'R-Ctrl',
    LCONTROL: 'L-Ctrl',
    LMENU: 'L-Alt',
    RMENU: 'R-Alt',
    RETURN: 'Enter',
    PRIOR: 'Page Up',
    NEXT: 'Page Down',
    CAPITAL: 'Caps',
    GRAVE: '`',
  };
  if (named[value]) return named[value];
  if (value.startsWith('MOUSE')) return `Mouse ${value.slice(5)}`;
  if (value.length === 1) return value;
  return value.charAt(0) + value.slice(1).toLowerCase();
}

/**
 * Resolve with the next key the window sees, or `NONE` when the player pressed Escape
 * to clear the binding. Shaped like `void.openKeybindCapture` so `KeybindChip` cannot
 * tell the two apart.
 */
export function captureKey(): Promise<string | null> {
  return new Promise((resolve) => {
    const onKey = (event: KeyboardEvent): void => {
      event.preventDefault();
      event.stopPropagation();
      window.removeEventListener('keydown', onKey, { capture: true });
      resolve(event.key === 'Escape' ? 'NONE' : keyName(event));
    };
    window.addEventListener('keydown', onKey, { capture: true });
  });
}
