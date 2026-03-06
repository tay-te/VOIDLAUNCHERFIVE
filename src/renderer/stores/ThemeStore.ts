import { makeAutoObservable } from "mobx";

export type Theme = "light" | "dark" | "system";

export class ThemeStore {
  theme: Theme = "system";
  systemTheme: "light" | "dark" = "dark";

  constructor() {
    makeAutoObservable(this);
    this.init();
  }

  private async init() {
    const saved = localStorage.getItem("void-theme") as Theme | null;
    if (saved) this.theme = saved;

    // Get system theme
    if (window.electronAPI) {
      this.systemTheme = await window.electronAPI.getSystemTheme();
    } else {
      this.systemTheme = window.matchMedia("(prefers-color-scheme: dark)")
        .matches
        ? "dark"
        : "light";
    }

    this.applyTheme();
  }

  setTheme(theme: Theme) {
    this.theme = theme;
    localStorage.setItem("void-theme", theme);
    this.applyTheme();
  }

  private applyTheme() {
    const resolved = this.resolvedTheme;
    document.documentElement.classList.toggle("dark", resolved === "dark");
  }

  get resolvedTheme(): "light" | "dark" {
    return this.theme === "system" ? this.systemTheme : this.theme;
  }

  get isDark() {
    return this.resolvedTheme === "dark";
  }
}
