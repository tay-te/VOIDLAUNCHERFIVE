import { observer } from "mobx-react-lite";
import {
  ChevronDown,
  Circle,
  Cuboid,
  Home,
  Layers,
  LogOut,
  Package,
  Search,
  Settings,
  Users,
} from "lucide-react";
import { useStore } from "../stores";
import { PlayerAvatar } from "./PlayerAvatar";

interface SidebarProps {
  activePage: string;
  onNavigate: (page: string) => void;
}

const navItems = [
  { id: "home", label: "Home", icon: Home },
  { id: "browse", label: "Browse", icon: Search },
  { id: "instances", label: "Instances", icon: Layers },
  { id: "friends", label: "Friends", icon: Users },
  { id: "settings", label: "Settings", icon: Settings },
];

function getModCount(instance: { installedMods: unknown[]; modCount: number }) {
  return instance.installedMods.length > 0 ? instance.installedMods.length : instance.modCount;
}

export const Sidebar = observer(({ activePage, onNavigate }: SidebarProps) => {
  const { auth, installs, instances } = useStore();
  const recentInstances = [...instances.instances]
    .sort((a, b) => {
      if (a.lastPlayed && b.lastPlayed) return b.lastPlayed.localeCompare(a.lastPlayed);
      if (a.lastPlayed) return -1;
      if (b.lastPlayed) return 1;
      return a.name.localeCompare(b.name);
    })
    .slice(0, 5);

  return (
    <aside className="flex h-full w-[23rem] flex-col bg-(--color-sidebar) text-(--color-text-primary) max-lg:w-[19rem] max-md:h-18 max-md:w-full max-md:flex-row max-md:items-center">
      <div className="drag-region border-b border-(--color-border) px-5 py-4 max-md:flex max-md:h-full max-md:items-center max-md:border-b-0 max-md:px-3 max-md:py-0">
        <div className="mb-5 flex h-3 items-center gap-2 max-md:hidden">
          <span className="h-3 w-3 rounded-pill bg-[#ff5f57]" />
          <span className="h-3 w-3 rounded-pill bg-[#febc2e]" />
          <span className="h-3 w-3 rounded-pill bg-[#28c840]" />
        </div>
        <div className="flex items-center">
          <div className="no-drag flex h-11 w-11 items-center justify-center rounded-control border border-(--color-border) bg-(--color-surface-secondary) text-(--color-accent)">
            <Cuboid size={22} />
          </div>
          <div className="no-drag ml-3 min-w-0">
            <p className="truncate text-xl font-display leading-none">VOID Lab</p>
            <div className="mt-2 flex items-center gap-2 text-xs font-emphasis text-(--color-text-secondary)">
              <span className="h-2 w-2 rounded-pill bg-(--color-success)" />
              <span>Minecraft launcher</span>
            </div>
          </div>
        </div>
      </div>

      <button
        type="button"
        onClick={() => onNavigate("auth")}
        className="no-drag mx-5 mt-5 flex items-center gap-3 rounded-control border border-(--color-border) bg-(--color-surface-secondary) p-3 text-left transition-colors hover:bg-(--color-surface-tertiary) max-md:hidden"
      >
        {auth.isAuthenticated && auth.uuid ? (
          <PlayerAvatar
            uuid={auth.uuid}
            username={auth.username}
            size={36}
            className="rounded-control"
          />
        ) : (
          <div className="flex h-9 w-9 items-center justify-center rounded-control bg-(--color-accent) text-sm font-display text-fg-on-accent">
            V
          </div>
        )}
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-strong">{auth.username}</p>
          <div className="mt-1 flex items-center gap-1.5">
            <span className="h-1.5 w-1.5 rounded-pill bg-(--color-success)" />
            <span className="truncate text-caption font-body text-(--color-text-secondary)">
              Online
            </span>
          </div>
        </div>
        <ChevronDown size={14} className="text-(--color-text-secondary)" />
      </button>

      <div className="mx-5 mt-6 flex items-center justify-between max-md:hidden">
        <p className="text-sm font-display text-(--color-text-secondary)">Workspace</p>
        <div className="flex rounded-control border border-(--color-border) bg-(--color-surface-secondary) p-1 text-(--color-text-secondary)">
          <Circle size={13} />
        </div>
      </div>

      <nav className="mt-3 space-y-1 px-5 max-md:mt-0 max-md:flex max-md:flex-1 max-md:gap-1 max-md:space-y-0 max-md:overflow-x-auto max-md:px-2">
        {navItems.map(({ id, label, icon: Icon }) => {
          const isActive = activePage === id;
          return (
            <button
              key={id}
              type="button"
              onClick={() => {
                if (id === "browse") {
                  installs.clearPreferredInstance();
                }
                onNavigate(id);
              }}
              aria-current={isActive ? "page" : undefined}
              className={`no-drag flex h-11 w-full items-center gap-3 rounded-control px-3 text-sm font-display transition-colors max-md:w-11 max-md:flex-shrink-0 max-md:justify-center max-md:px-0 ${
                isActive
                  ? "bg-(--color-surface-tertiary) text-(--color-text-primary)"
                  : "text-(--color-text-secondary) hover:bg-(--color-surface-secondary) hover:text-(--color-text-primary)"
              }`}
            >
              <Icon
                size={18}
                strokeWidth={isActive ? 2.25 : 1.8}
                className={isActive ? "text-(--color-accent)" : ""}
              />
              <span className="flex-1 text-left max-md:hidden">{label}</span>
            </button>
          );
        })}
      </nav>

      <div className="mx-5 mt-7 min-h-0 flex-1 max-md:hidden">
        <div className="mb-3 flex items-center justify-between">
          <p className="text-sm font-display text-(--color-text-secondary)">Recent Instances</p>
          <button
            type="button"
            onClick={() => onNavigate("instances")}
            className="rounded-md border border-(--color-border) bg-(--color-surface-secondary) px-2 py-1 text-caption font-display text-(--color-text-secondary) transition-colors hover:bg-(--color-surface-tertiary) hover:text-(--color-text-primary)"
          >
            All
          </button>
        </div>
        <div className="space-y-2 overflow-y-auto pr-1">
          {recentInstances.map((instance) => (
            <button
              key={instance.id}
              type="button"
              onClick={() => onNavigate("home")}
              className="flex w-full items-center gap-3 rounded-control border border-(--color-border) bg-(--color-surface-secondary) p-2.5 text-left transition-colors hover:bg-(--color-surface-tertiary)"
            >
              <div
                className="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-control text-sm font-display text-white"
                style={{ backgroundColor: instance.iconColor }}
              >
                {instance.name[0]?.toUpperCase() ?? "?"}
              </div>
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-display text-(--color-text-primary)">
                  {instance.name}
                </p>
                <p className="mt-1 truncate text-xs font-emphasis text-(--color-text-secondary)">
                  {instance.loader} / {instance.version} / {getModCount(instance)} mods
                </p>
              </div>
              <Package size={15} className="text-(--color-text-secondary)" />
            </button>
          ))}
        </div>
      </div>

      <div className="mx-5 mt-auto max-md:hidden">
        <div className="border-t border-(--color-border) py-4">
          <div className="mb-3 flex items-center justify-between px-2 text-caption text-(--color-text-secondary)">
            <span>VOID Launcher</span>
            <span>v2.0.31</span>
          </div>
          <button
            type="button"
            onClick={() => auth.signOut()}
            className="flex h-9 w-full items-center gap-3 rounded-control px-2 text-sm font-emphasis text-(--color-text-secondary) transition-colors hover:bg-(--color-surface-secondary) hover:text-(--color-text-primary)"
          >
            <LogOut size={16} />
            Sign out
          </button>
        </div>
      </div>
    </aside>
  );
});
