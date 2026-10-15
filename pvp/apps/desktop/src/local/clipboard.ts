/**
 * Clipboard writes, for the one thing the launcher copies: a loadout's share code.
 *
 * `navigator.clipboard` works in both webviews Tauri ships on and in the browser preview,
 * as long as the call follows a click. It can still refuse (an unfocused window, a
 * locked-down webview), so this answers rather than throws and the caller says so.
 */
export async function copyText(text: string): Promise<boolean> {
  try {
    if (typeof navigator === 'undefined' || !navigator.clipboard) return false;
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    return false;
  }
}
