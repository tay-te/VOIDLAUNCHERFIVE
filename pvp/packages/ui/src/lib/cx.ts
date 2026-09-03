/**
 * Join class names, dropping anything falsy.
 *
 * Deliberately tiny and dependency-free: this package ships no runtime CSS-in-JS and
 * the in-game bundle has a 400 KB gzipped budget (§10), so a class-name helper is not
 * worth a dependency.
 */
export function cx(...parts: Array<string | false | null | undefined>): string {
  let out = '';
  for (const part of parts) {
    if (!part) continue;
    if (out) out += ' ';
    out += part;
  }
  return out;
}
