import { makeAutoObservable, runInAction } from "mobx";
import {
  DEFAULT_THEME_ID,
  SCHEME_DEFAULTS,
  THEMES,
  getTheme,
  isKnownTheme,
  type ColorScheme,
  type ThemeDefinition,
  type ThemeId,
} from "../styles/themeRegistry";

/** A stored preference is either a concrete theme id or "system". */
export type Theme = ThemeId | "system";

const STORAGE_KEY = "void-theme";

export class ThemeStore {
  /** What the user picked — a theme id, or "system" to follow the OS. */
  preference: Theme = "system";
  /** The OS-reported color scheme, used when preference is "system". */
  systemScheme: ColorScheme = "dark";

  private mediaQuery: MediaQueryList | null = null;

  constructor() {
    makeAutoObservable(this);
    this.init();
  }

  private async init() {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved && (saved === "system" || isKnownTheme(saved))) {
      runInAction(() => {
        this.preference = saved;
      });
    }

    let scheme: ColorScheme;
    if (window.electronAPI) {
      scheme = await window.electronAPI.getSystemTheme();
    } else {
      scheme = window.matchMedia("(prefers-color-scheme: dark)").matches
        ? "dark"
        : "light";
    }

    runInAction(() => {
      this.systemScheme = scheme;
    });

    this.watchSystemScheme();
    this.applyTheme();
  }

  /** Keep "System" honest when the OS flips appearance while the app is open. */
  private watchSystemScheme() {
    if (this.mediaQuery) return;
    this.mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");
    this.mediaQuery.addEventListener("change", (event) => {
      runInAction(() => {
        this.systemScheme = event.matches ? "dark" : "light";
      });
      this.applyTheme();
    });
  }

  setTheme(theme: Theme) {
    this.preference = theme;
    localStorage.setItem(STORAGE_KEY, theme);
    this.applyTheme();
  }

  /**
   * Writes both attributes on <html>:
   *   data-theme   the specific theme id, which the CSS files select on
   *   data-scheme  the light/dark family, which `dark:` utilities select on
   * Splitting them means several themes can share a family — three different
   * dark themes all get `dark:` variants without touching a single component.
   */
  private applyTheme() {
    const root = document.documentElement;
    root.setAttribute("data-theme", this.resolvedThemeId);
    root.setAttribute("data-scheme", this.resolvedScheme);
  }

  /** The concrete theme id in effect right now. */
  get resolvedThemeId(): ThemeId {
    if (this.preference === "system") {
      return SCHEME_DEFAULTS[this.systemScheme] ?? DEFAULT_THEME_ID;
    }
    return isKnownTheme(this.preference) ? this.preference : DEFAULT_THEME_ID;
  }

  get resolvedScheme(): ColorScheme {
    return getTheme(this.resolvedThemeId)?.scheme ?? "dark";
  }

  get activeTheme(): ThemeDefinition | undefined {
    return getTheme(this.resolvedThemeId);
  }

  /** Every registered theme, for rendering the picker. */
  get available(): ThemeDefinition[] {
    return THEMES;
  }

  get isDark(): boolean {
    return this.resolvedScheme === "dark";
  }

  // ── Back-compat ──────────────────────────────────────────────────────────
  /** Existing components read `theme.theme`; keep that reading the preference. */
  get theme(): Theme {
    return this.preference;
  }

  /** Previously returned "light" | "dark"; now the resolved theme id, which is
   *  the same value for the two built-in themes. */
  get resolvedTheme(): ThemeId {
    return this.resolvedThemeId;
  }
}
