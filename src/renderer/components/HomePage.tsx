import { useEffect, useMemo, useState } from "react";
import { observer } from "mobx-react-lite";
import { useStore } from "../stores";
import { SkinViewer3D } from "./SkinViewer";
import {
  Play,
  ChevronDown,
  User,
  Gamepad2,
  Clock,
  Layers,
} from "lucide-react";

interface HomePageProps {
  onOpenMod: (id: string) => void;
  onNavigate: (page: string) => void;
}

function getGreeting() {
  const h = new Date().getHours();
  if (h < 5) return "Late night gaming?";
  if (h < 12) return "Good morning,";
  if (h < 17) return "Good afternoon,";
  if (h < 21) return "Good evening,";
  return "Burning the midnight oil,";
}

export const HomePage = observer(({ onOpenMod, onNavigate }: HomePageProps) => {
  const { auth, instances } = useStore();
  const [selectedInstanceId, setSelectedInstanceId] = useState<string | null>(null);
  const [showInstancePicker, setShowInstancePicker] = useState(false);

  const greeting = useMemo(() => getGreeting(), []);

  // Auto-select most recently played or first instance
  useEffect(() => {
    if (instances.instances.length > 0 && !selectedInstanceId) {
      const sorted = [...instances.instances].sort((a, b) => {
        if (a.lastPlayed && b.lastPlayed) return b.lastPlayed.localeCompare(a.lastPlayed);
        if (a.lastPlayed) return -1;
        if (b.lastPlayed) return 1;
        return 0;
      });
      setSelectedInstanceId(sorted[0].id);
    }
  }, [instances.instances.length]);

  const selectedInstance = instances.instances.find((i) => i.id === selectedInstanceId);

  const userName = auth.username;

  const handleLaunch = () => {
    if (selectedInstance) {
      instances.launch(selectedInstance.id);
    }
  };

  return (
    <div className="h-full flex flex-col relative overflow-hidden">
      {/* Main content area - full height split */}
      <div className="flex-1 flex items-center px-10">
        {/* Left side - Skin render */}
        <div className="flex-1 flex items-center justify-center home-slide-up" style={{ animationDelay: "0.1s" }}>
          <div className="relative">
            {/* Glow behind skin */}
            <div className="absolute inset-0 scale-75 rounded-full bg-(--color-accent)/8 blur-3xl pointer-events-none" />
            <SkinViewer3D
              username={userName}
              className="w-[28rem] h-[560px] relative"
            />
          </div>
        </div>

        {/* Right side - Welcome + info */}
        <div className="flex-1 flex flex-col justify-center max-w-md">
          <div className="home-slide-up" style={{ animationDelay: "0.15s" }}>
            <p className="text-sm font-medium text-(--color-text-secondary)">
              {greeting}
            </p>
            <h1 className="mt-1 text-4xl font-black tracking-tight leading-none text-(--color-text-primary)">
              {userName}
            </h1>
          </div>

          {/* Stats row */}
          <div className="flex gap-3 mt-7">
            <div className="home-slide-up rounded-2xl bg-(--color-surface-secondary) border border-(--color-border) px-5 py-4 flex-1" style={{ animationDelay: "0.25s" }}>
              <div className="flex items-center gap-2 text-(--color-text-secondary) mb-1">
                <Layers size={13} />
                <span className="text-[11px] font-semibold uppercase tracking-wider">Instances</span>
              </div>
              <p className="text-2xl font-black text-(--color-text-primary) tracking-tight">
                {instances.instances.length}
              </p>
            </div>
            <div className="home-slide-up rounded-2xl bg-(--color-surface-secondary) border border-(--color-border) px-5 py-4 flex-1" style={{ animationDelay: "0.35s" }}>
              <div className="flex items-center gap-2 text-(--color-text-secondary) mb-1">
                <Gamepad2 size={13} />
                <span className="text-[11px] font-semibold uppercase tracking-wider">Total Mods</span>
              </div>
              <p className="text-2xl font-black text-(--color-text-primary) tracking-tight">
                {instances.instances.reduce((sum, i) => sum + (i.installedMods?.length ?? 0), 0)}
              </p>
            </div>
          </div>

          {/* Recent activity */}
          {selectedInstance?.lastPlayed && (
            <div className="home-slide-up flex items-center gap-2 mt-4 text-(--color-text-secondary)" style={{ animationDelay: "0.45s" }}>
              <Clock size={12} />
              <span className="text-xs font-medium">
                Last played {new Date(selectedInstance.lastPlayed).toLocaleDateString(undefined, {
                  month: "short",
                  day: "numeric",
                  hour: "numeric",
                  minute: "2-digit",
                })}
              </span>
            </div>
          )}
        </div>
      </div>

      {/* Bottom right - Profile selector + Launch */}
      <div className="absolute bottom-6 right-8 flex items-end gap-3 home-slide-up" style={{ animationDelay: "0.3s" }}>
        {/* Instance selector */}
        <div className="relative">
          {showInstancePicker && (
            <div className="absolute bottom-full right-0 mb-2 w-72 max-h-64 overflow-y-auto rounded-2xl glass border border-(--color-glass-border) shadow-2xl shadow-black/15 p-1.5 z-50 picker-dropdown">
              {instances.instances.length === 0 ? (
                <div className="px-4 py-6 text-center picker-item">
                  <p className="text-sm font-bold text-(--color-text-primary)">No instances yet</p>
                  <p className="text-xs text-(--color-text-secondary) mt-1">Create one to get started</p>
                  <button
                    onClick={() => { setShowInstancePicker(false); onNavigate("instances"); }}
                    className="mt-3 px-4 py-2 rounded-xl bg-(--color-accent) text-white text-xs font-semibold cursor-pointer"
                  >
                    Create Instance
                  </button>
                </div>
              ) : (
                instances.instances.map((inst, idx) => (
                  <button
                    key={inst.id}
                    onClick={() => { setSelectedInstanceId(inst.id); setShowInstancePicker(false); }}
                    className={`picker-item w-full flex items-center gap-3 px-3 py-2.5 rounded-xl transition-all cursor-pointer ${
                      selectedInstanceId === inst.id
                        ? "bg-(--color-accent)/10 text-(--color-accent)"
                        : "text-(--color-text-secondary) hover:bg-(--color-surface-tertiary) hover:text-(--color-text-primary)"
                    }`}
                    style={{ animationDelay: `${idx * 50}ms` }}
                  >
                    <div
                      className="w-8 h-8 rounded-lg flex items-center justify-center text-white text-xs font-black flex-shrink-0"
                      style={{ backgroundColor: inst.iconColor }}
                    >
                      {inst.name[0]?.toUpperCase()}
                    </div>
                    <div className="text-left min-w-0 flex-1">
                      <p className="text-sm font-bold truncate">{inst.name}</p>
                      <p className="text-[11px] text-(--color-text-secondary)">
                        {inst.version} &middot; {inst.loader} &middot; {inst.installedMods?.length ?? 0} mods
                      </p>
                    </div>
                  </button>
                ))
              )}
            </div>
          )}

          <button
            onClick={() => setShowInstancePicker(!showInstancePicker)}
            className="selector-btn flex items-center gap-3 px-4 py-3 rounded-2xl bg-(--color-surface-secondary) border border-(--color-border) hover:bg-(--color-surface-tertiary) cursor-pointer"
          >
            {selectedInstance ? (
              <>
                <div
                  className="w-9 h-9 rounded-lg flex items-center justify-center text-white text-sm font-black"
                  style={{ backgroundColor: selectedInstance.iconColor }}
                >
                  {selectedInstance.name[0]?.toUpperCase()}
                </div>
                <div className="text-left">
                  <p className="text-sm font-bold text-(--color-text-primary)">
                    {selectedInstance.name}
                  </p>
                  <p className="text-[11px] text-(--color-text-secondary)">
                    {selectedInstance.version} &middot; {selectedInstance.loader}
                  </p>
                </div>
              </>
            ) : (
              <>
                <div className="w-9 h-9 rounded-lg bg-(--color-surface-tertiary) flex items-center justify-center">
                  <User size={15} className="text-(--color-text-secondary)" />
                </div>
                <div className="text-left">
                  <p className="text-sm font-bold text-(--color-text-primary)">No Instance</p>
                  <p className="text-[11px] text-(--color-text-secondary)">Select or create one</p>
                </div>
              </>
            )}
            <ChevronDown
              size={13}
              className={`text-(--color-text-secondary) ml-2 transition-transform duration-300 ${showInstancePicker ? "rotate-180" : ""}`}
            />
          </button>
        </div>

        {/* Launch button */}
        <button
          onClick={handleLaunch}
          disabled={!selectedInstance}
          className="launch-btn flex items-center gap-3 px-7 py-3.5 rounded-2xl bg-(--color-accent) hover:bg-(--color-accent-hover) text-white font-black text-lg tracking-tight transition-all duration-300 cursor-pointer hover:-translate-y-1 disabled:opacity-40 disabled:cursor-default disabled:hover:translate-y-0"
        >
          <span className="launch-shimmer" />
          <Play size={18} fill="white" className="relative z-10" />
          <span className="relative z-10">Launch</span>
        </button>
      </div>
    </div>
  );
});
