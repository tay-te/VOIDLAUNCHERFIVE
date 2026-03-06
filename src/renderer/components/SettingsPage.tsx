import { observer } from "mobx-react-lite";
import { useStore } from "../stores";
import { Sun, Moon, Monitor, Zap } from "lucide-react";
import type { Theme } from "../stores/ThemeStore";
import { useEffect, useState } from "react";

export const SettingsPage = observer(() => {
  const { theme } = useStore();
  const [version, setVersion] = useState("dev");

  useEffect(() => {
    window.electronAPI?.getAppVersion().then(setVersion).catch(() => {});
  }, []);

  const themeOptions: { value: Theme; label: string; icon: typeof Sun }[] = [
    { value: "light", label: "Light", icon: Sun },
    { value: "dark", label: "Dark", icon: Moon },
    { value: "system", label: "System", icon: Monitor },
  ];

  return (
    <div className="p-8 space-y-6">
      <div>
        <h1 className="text-3xl font-black tracking-tight text-(--color-text-primary)">
          <span className="void-text">Settings</span>
        </h1>
        <p className="text-sm text-(--color-text-secondary) mt-1">
          Tune your launcher to perfection
        </p>
      </div>

      {/* Theme */}
      <section className="rounded-2xl bg-(--color-surface-secondary) border border-(--color-border) p-5 space-y-4">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-(--color-accent)/10 flex items-center justify-center">
            <Sun size={15} className="text-(--color-accent)" />
          </div>
          <div>
            <h2 className="text-sm font-bold text-(--color-text-primary)">
              Appearance
            </h2>
            <p className="text-xs text-(--color-text-secondary) mt-0.5">
              Choose how the void looks
            </p>
          </div>
        </div>
        <div className="flex gap-2">
          {themeOptions.map(({ value, label, icon: Icon }) => (
            <button
              key={value}
              onClick={() => theme.setTheme(value)}
              className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold transition-all cursor-pointer ${
                theme.theme === value
                  ? "bg-(--color-accent) text-white shadow-sm"
                  : "bg-(--color-surface-tertiary) text-(--color-text-secondary) hover:text-(--color-text-primary)"
              }`}
            >
              <Icon size={14} />
              {label}
            </button>
          ))}
        </div>
      </section>

      {/* About */}
      <section className="rounded-2xl bg-(--color-surface-secondary) border border-(--color-border) p-5 space-y-4">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-(--color-accent)/10 flex items-center justify-center">
            <Zap size={15} className="text-(--color-accent)" />
          </div>
          <div>
            <h2 className="text-sm font-bold text-(--color-text-primary)">
              About
            </h2>
            <p className="text-xs text-(--color-text-secondary) mt-0.5">
              The engine behind it all
            </p>
          </div>
        </div>
        <div className="p-4 rounded-xl bg-(--color-surface-tertiary) space-y-1.5">
          <p className="text-sm font-bold text-(--color-text-primary)">
            <span className="void-text">VOID</span>{" "}
            <span className="text-(--color-text-secondary) font-normal text-xs">
              v{version}
            </span>
          </p>
          <p className="text-xs text-(--color-text-secondary) leading-relaxed">
            A modern Minecraft modding client. Explore, install, and manage
            mods from the Modrinth ecosystem with style.
          </p>
        </div>
      </section>
    </div>
  );
});
