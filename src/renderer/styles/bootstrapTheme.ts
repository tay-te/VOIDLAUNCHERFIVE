/* ─────────────────────────────────────────────────────────────────────────────
   THEME BOOTSTRAP
   ─────────────────────────────────────────────────────────────────────────────
   ThemeStore resolves the theme asynchronously (it awaits the main process for
   the OS appearance). That leaves a window where <html> carries no data-theme
   and no token resolves. This runs synchronously at import time, before React
   renders, so the correct theme is in place for the very first paint.

   Import it at the top of index.tsx, ahead of the app.
   ────────────────────────────────────────────────────────────────────────────*/

import {
  DEFAULT_THEME_ID,
  SCHEME_DEFAULTS,
  getTheme,
  isKnownTheme,
} from "./themeRegistry";

const STORAGE_KEY = "void-theme";

export function bootstrapTheme() {
  let themeId = DEFAULT_THEME_ID;

  try {
    const saved = localStorage.getItem(STORAGE_KEY);

    if (saved && saved !== "system" && isKnownTheme(saved)) {
      themeId = saved;
    } else {
      // "system", unset, or a theme that no longer exists — follow the OS.
      const prefersDark = window.matchMedia("(prefers-color-scheme: dark)").matches;
      themeId = SCHEME_DEFAULTS[prefersDark ? "dark" : "light"] ?? DEFAULT_THEME_ID;
    }
  } catch {
    // localStorage can throw in restricted contexts; the default is fine.
  }

  const root = document.documentElement;
  root.setAttribute("data-theme", themeId);
  root.setAttribute("data-scheme", getTheme(themeId)?.scheme ?? "dark");
}

bootstrapTheme();
