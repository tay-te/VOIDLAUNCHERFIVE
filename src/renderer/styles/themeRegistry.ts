/* ─────────────────────────────────────────────────────────────────────────────
   THEME REGISTRY
   ─────────────────────────────────────────────────────────────────────────────
   The single source of truth for which themes exist. The Settings UI renders
   from this list, so a new theme appears in the picker automatically.

   To add a theme:
     1. cp styles/themes/dark.css styles/themes/<id>.css   (change the selector
        to [data-theme="<id>"] and edit the values)
     2. @import it in index.css
     3. add one entry below

   Nothing else in the app needs to change.
   ────────────────────────────────────────────────────────────────────────────*/

/** Light/dark family. Drives `dark:` utilities and the native `color-scheme`. */
export type ColorScheme = "light" | "dark";

/** Icon key, mapped to a Lucide component at the render site. Keeping this a
 *  plain string keeps the registry free of React/JSX imports. */
export type ThemeIconKey = "sun" | "moon" | "monitor" | "palette";

export interface ThemeDefinition {
  /** Value written to `data-theme`, and the CSS file's selector. */
  id: string;
  /** Human-readable name shown in Settings. */
  label: string;
  /** Which family this theme belongs to. */
  scheme: ColorScheme;
  icon: ThemeIconKey;
}

export const THEMES: ThemeDefinition[] = [
  { id: "light", label: "Light", scheme: "light", icon: "sun" },
  { id: "dark", label: "Dark", scheme: "dark", icon: "moon" },
];

export type ThemeId = string;

/** Fallback when nothing is stored and the system preference is unavailable. */
export const DEFAULT_THEME_ID: ThemeId = "dark";

/** The theme used for each family when the user picks "System". */
export const SCHEME_DEFAULTS: Record<ColorScheme, ThemeId> = {
  light: "light",
  dark: "dark",
};

export function getTheme(id: ThemeId): ThemeDefinition | undefined {
  return THEMES.find((t) => t.id === id);
}

export function isKnownTheme(id: string): boolean {
  return THEMES.some((t) => t.id === id);
}
