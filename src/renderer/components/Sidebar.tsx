import { observer } from "mobx-react-lite";
import { useStore } from "../stores";
import {
  Home,
  Search,
  Download,
  User,
  Settings,
  Sun,
  Moon,
  Monitor,
} from "lucide-react";
import { PlayerAvatar } from "./PlayerAvatar";

interface SidebarProps {
  activePage: string;
  onNavigate: (page: string) => void;
}

const navItems = [
  { id: "home", label: "Home", icon: Home },
  { id: "browse", label: "Browse Mods", icon: Search },
  { id: "installed", label: "Installed", icon: Download },
  { id: "skin", label: "Skin Viewer", icon: User },
  { id: "settings", label: "Settings", icon: Settings },
];

export const Sidebar = observer(({ activePage, onNavigate }: SidebarProps) => {
  const { theme, auth } = useStore();

  const themeOptions = [
    { value: "light" as const, icon: Sun },
    { value: "dark" as const, icon: Moon },
    { value: "system" as const, icon: Monitor },
  ];

  return (
    <aside className="w-56 h-full flex flex-col border-r border-(--color-border) bg-(--color-sidebar)">
      {/* Logo / Title */}
      <div className="drag-region h-12 flex items-center px-4 font-strong text-lg tracking-tight">
        <span className="no-drag text-(--color-text-primary)">VOID</span>
        <span className="no-drag ml-1 text-(--color-text-secondary) font-light">
          Launcher
        </span>
      </div>

      {/* Navigation */}
      <nav className="flex-1 px-3 py-3 space-y-1">
        {navItems.map(({ id, label, icon: Icon }) => (
          <button
            key={id}
            onClick={() => onNavigate(id)}
            className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-card text-sm transition-all cursor-pointer ${
              activePage === id
                ? "border border-(--color-border) bg-(--color-surface) text-(--color-text-primary) shadow-sm"
                : "border border-transparent text-(--color-text-secondary) hover:bg-(--color-surface-secondary) hover:text-(--color-text-primary)"
            }`}
          >
            <Icon size={18} />
            {label}
          </button>
        ))}
      </nav>

      {/* Theme switcher */}
      <div className="px-3 py-2">
        <div className="flex items-center gap-1 rounded-card border border-(--color-border) bg-(--color-surface-secondary) p-1">
          {themeOptions.map(({ value, icon: Icon }) => (
            <button
              key={value}
              onClick={() => theme.setTheme(value)}
              className={`flex-1 flex justify-center py-1.5 rounded-control text-xs transition-colors cursor-pointer ${
                theme.theme === value
                  ? "bg-(--color-surface) text-(--color-text-primary)"
                  : "text-(--color-text-secondary) hover:text-(--color-text-primary)"
              }`}
            >
              <Icon size={14} />
            </button>
          ))}
        </div>
      </div>

      {/* User info */}
      <div className="px-3 py-3 border-t border-(--color-border)">
        {auth.isAuthenticated ? (
          <div className="flex items-center gap-2">
            <PlayerAvatar
              uuid={auth.uuid}
              username={auth.username}
              size={32}
              className="rounded-pill"
            />
            <div className="flex-1 min-w-0">
              <p className="text-xs font-body truncate text-(--color-text-primary)">
                {auth.username}
              </p>
              <button
                onClick={() => auth.signOut()}
                className="text-micro text-(--color-text-secondary) hover:text-(--color-text-primary) cursor-pointer"
              >
                Sign out
              </button>
            </div>
          </div>
        ) : (
          <button
            onClick={() => onNavigate("auth")}
            className="w-full py-2 rounded-card bg-(--color-accent) hover:bg-(--color-accent-hover) text-fg-on-accent text-sm font-body transition-colors cursor-pointer"
          >
            Sign In
          </button>
        )}
      </div>
    </aside>
  );
});
