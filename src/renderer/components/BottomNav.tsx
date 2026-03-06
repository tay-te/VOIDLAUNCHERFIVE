import { observer } from "mobx-react-lite";
import { useStore } from "../stores";
import { Home, Search, Layers, Settings, User } from "lucide-react";
import { PlayerAvatar } from "./SkinViewer";

interface SidebarProps {
  activePage: string;
  onNavigate: (page: string) => void;
}

const navItems = [
  { id: "home", label: "Home", icon: Home },
  { id: "browse", label: "Browse", icon: Search },
  { id: "instances", label: "Instances", icon: Layers },
  { id: "settings", label: "Settings", icon: Settings },
];

export const Sidebar = observer(
  ({ activePage, onNavigate }: SidebarProps) => {
    const { auth } = useStore();

    return (
      <nav className="w-16 flex flex-col items-center py-3 gap-1.5">
        {/* Brand */}
        <div className="w-9 h-9 rounded-xl bg-(--color-accent) flex items-center justify-center text-white font-black text-xs mb-4 shadow-sm shadow-(--color-accent)/25 flex-shrink-0">
          V
        </div>

        {navItems.map(({ id, label, icon: Icon }) => {
          const isActive = activePage === id;
          return (
            <button
              key={id}
              onClick={() => onNavigate(id)}
              title={label}
              className={`no-drag relative flex items-center justify-center w-10 h-10 rounded-xl transition-all duration-200 cursor-pointer ${
                isActive
                  ? "bg-(--color-accent)/12 text-(--color-accent)"
                  : "text-(--color-text-secondary) hover:text-(--color-text-primary) hover:bg-(--color-surface-tertiary)"
              }`}
            >
              <Icon size={20} strokeWidth={isActive ? 2.5 : 1.75} />
              {isActive && (
                <div className="absolute -left-[5px] top-1/2 -translate-y-1/2 w-[3px] h-4 rounded-full bg-(--color-accent)" />
              )}
            </button>
          );
        })}

        <div className="flex-1" />

        {/* User */}
        <button
          onClick={() => onNavigate("auth")}
          title="Account"
          className={`no-drag flex items-center justify-center w-10 h-10 rounded-xl transition-all duration-200 cursor-pointer ${
            activePage === "auth"
              ? "bg-(--color-accent)/12 text-(--color-accent)"
              : "text-(--color-text-secondary) hover:text-(--color-text-primary) hover:bg-(--color-surface-tertiary)"
          }`}
        >
          {auth.isAuthenticated && auth.uuid ? (
            <PlayerAvatar
              uuid={auth.uuid}
              username={auth.username}
              size={28}
              className="rounded-full"
            />
          ) : (
            <User size={20} strokeWidth={1.75} />
          )}
        </button>
      </nav>
    );
  }
);
