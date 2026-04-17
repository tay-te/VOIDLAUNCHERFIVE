import { lazy, Suspense, useEffect, useMemo, useRef, useState } from "react";
import { observer } from "mobx-react-lite";
import { useStore } from "../stores";
import {
  Play,
  ChevronDown,
  User,
  Gamepad2,
  Clock,
  Layers,
  Loader2,
  Square,
  ArrowRight,
} from "lucide-react";

const SkinViewer3D = lazy(() =>
  import("./SkinViewer").then((module) => ({ default: module.SkinViewer3D }))
);

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

function sortInstancesByRecentPlay<T extends { id: string; lastPlayed: string | null }>(
  list: T[]
) {
  return [...list].sort((a, b) => {
    if (a.lastPlayed && b.lastPlayed) return b.lastPlayed.localeCompare(a.lastPlayed);
    if (a.lastPlayed) return -1;
    if (b.lastPlayed) return 1;
    return a.id.localeCompare(b.id);
  });
}

function SkinViewerFallback() {
  return (
    <div className="w-full h-[clamp(16rem,42vh,30rem)] min-h-[16rem] rounded-[1.5rem] bg-(--color-surface-secondary) border border-(--color-border) animate-pulse" />
  );
}

export const HomePage = observer(({ onNavigate }: HomePageProps) => {
  const { auth, instances, installs } = useStore();
  const [selectedInstanceId, setSelectedInstanceId] = useState<string | null>(null);
  const [showInstancePicker, setShowInstancePicker] = useState(false);
  const pickerRef = useRef<HTMLDivElement>(null);

  const greeting = useMemo(() => getGreeting(), []);
  const totalMods = instances.instances.reduce(
    (sum, instance) => sum + (instance.installedMods?.length ?? 0),
    0
  );
  const instanceSelectionKey = instances.instances
    .map((instance) => `${instance.id}:${instance.lastPlayed ?? ""}`)
    .join("|");

  useEffect(() => {
    if (instances.instances.length === 0) {
      setSelectedInstanceId(null);
      setShowInstancePicker(false);
      return;
    }

    if (selectedInstanceId && instances.instances.some((instance) => instance.id === selectedInstanceId)) {
      return;
    }

    const [nextInstance] = sortInstancesByRecentPlay(instances.instances);
    setSelectedInstanceId(nextInstance?.id ?? null);
  }, [instanceSelectionKey, selectedInstanceId]);

  useEffect(() => {
    if (!showInstancePicker) return;

    const handlePointerDown = (event: MouseEvent) => {
      if (!pickerRef.current?.contains(event.target as Node)) {
        setShowInstancePicker(false);
      }
    };

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setShowInstancePicker(false);
      }
    };

    document.addEventListener("mousedown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("mousedown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [showInstancePicker]);

  const selectedInstance = instances.instances.find((instance) => instance.id === selectedInstanceId) ?? null;
  const userName = auth.username;
  const launching = instances.launchingInstanceId === selectedInstanceId;
  const gameRunning = instances.runningInstanceId === selectedInstanceId;
  const launchProgress = launching || gameRunning ? instances.launchProgress : null;
  const launchError = instances.launchError;

  const handleLaunch = () => {
    if (!selectedInstance) return;
    instances.launchGame(selectedInstance.id);
  };

  return (
    <div className="min-h-full px-8 py-8 lg:px-10">
      <div className="mx-auto flex min-h-full max-w-6xl flex-col gap-6">
        <section className="grid gap-6 lg:grid-cols-[minmax(0,1.1fr)_minmax(22rem,0.9fr)]">
          <div className="relative overflow-hidden rounded-[1.5rem] border border-(--color-border) bg-(--color-surface-secondary) px-6 py-6 lg:px-8">
            <div className="absolute inset-0 pointer-events-none">
              <div className="absolute left-1/2 top-1/2 h-[20rem] w-[20rem] -translate-x-1/2 -translate-y-1/2 rounded-full bg-(--color-accent)/5 blur-3xl" />
            </div>
            <div className="relative z-10 flex h-full items-center justify-center">
              <Suspense fallback={<SkinViewerFallback />}>
                <SkinViewer3D
                  username={userName}
                  className="w-full h-[clamp(16rem,42vh,30rem)] min-h-[16rem] max-w-[26rem]"
                />
              </Suspense>
            </div>
          </div>

          <div className="flex flex-col gap-4">
            <div className="rounded-[1.5rem] border border-(--color-border) bg-(--color-surface-secondary) px-6 py-6">
              <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-(--color-text-secondary)">
                {greeting}
              </p>
              <h1 className="mt-2 text-[2.4rem] font-black tracking-tight text-(--color-text-primary)">
                {userName}
              </h1>
              <p className="mt-3 max-w-md text-sm leading-relaxed text-(--color-text-secondary)">
                Return to your latest setup, monitor launch state, and move between your packs without digging through menus.
              </p>
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <div className="rounded-[1.25rem] border border-(--color-border) bg-(--color-surface-secondary) px-5 py-4">
                <div className="mb-2 flex items-center gap-2 text-(--color-text-secondary)">
                  <Layers size={13} />
                  <span className="text-[11px] font-semibold uppercase tracking-wider">Instances</span>
                </div>
                <p className="text-3xl font-black tracking-tight text-(--color-text-primary)">
                  {instances.instances.length}
                </p>
              </div>
              <div className="rounded-[1.25rem] border border-(--color-border) bg-(--color-surface-secondary) px-5 py-4">
                <div className="mb-2 flex items-center gap-2 text-(--color-text-secondary)">
                  <Gamepad2 size={13} />
                  <span className="text-[11px] font-semibold uppercase tracking-wider">Installed Mods</span>
                </div>
                <p className="text-3xl font-black tracking-tight text-(--color-text-primary)">
                  {totalMods}
                </p>
              </div>
            </div>

            <div className="rounded-[1.25rem] border border-(--color-border) bg-(--color-surface-secondary) px-5 py-4">
              <div className="flex items-center justify-between gap-4">
                <div>
                  <p className="text-[11px] font-semibold uppercase tracking-wider text-(--color-text-secondary)">
                    Quick Access
                  </p>
                  <p className="mt-1 text-sm text-(--color-text-secondary)">
                    Need a new pack or mod browse session?
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => onNavigate("instances")}
                    className="rounded-lg border border-(--color-border) bg-(--color-surface) px-4 py-2 text-sm font-semibold text-(--color-text-primary) transition-colors hover:bg-(--color-surface-tertiary)"
                  >
                    Manage Instances
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      installs.clearPreferredInstance();
                      onNavigate("browse");
                    }}
                    className="rounded-lg bg-(--color-accent) px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-(--color-accent-hover)"
                  >
                    Browse Mods
                  </button>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section className="rounded-[1.5rem] border border-(--color-border) bg-(--color-surface-secondary) p-5 lg:p-6">
          <div className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
            <div className="flex-1">
              <div className="flex items-center gap-2 text-(--color-text-secondary)">
                <Clock size={13} />
                <span className="text-[11px] font-semibold uppercase tracking-wider">Launch Ready</span>
              </div>

              <div ref={pickerRef} className="relative mt-3 max-w-xl">
                {showInstancePicker && (
                  <div
                    id="home-instance-picker"
                    role="listbox"
                    className="absolute left-0 top-full z-50 mt-2 max-h-72 w-full overflow-y-auto rounded-[1.25rem] glass border border-(--color-glass-border) p-1.5 shadow-2xl shadow-black/15 picker-dropdown"
                  >
                    {instances.instances.length === 0 ? (
                      <div className="px-4 py-6 text-center">
                        <p className="text-sm font-bold text-(--color-text-primary)">No instances yet</p>
                        <p className="mt-1 text-xs text-(--color-text-secondary)">Create one to get started</p>
                        <button
                          type="button"
                          onClick={() => {
                            setShowInstancePicker(false);
                            onNavigate("instances");
                          }}
                          className="mt-3 rounded-lg bg-(--color-accent) px-4 py-2 text-xs font-semibold text-white transition-colors hover:bg-(--color-accent-hover)"
                        >
                          Create Instance
                        </button>
                      </div>
                    ) : (
                      instances.instances.map((instance, index) => (
                        <button
                          key={instance.id}
                          type="button"
                          role="option"
                          aria-selected={selectedInstanceId === instance.id}
                          onClick={() => {
                            setSelectedInstanceId(instance.id);
                            setShowInstancePicker(false);
                          }}
                          className={`picker-item flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left transition-all ${
                            selectedInstanceId === instance.id
                              ? "bg-(--color-accent)/10 text-(--color-accent)"
                              : "text-(--color-text-secondary) hover:bg-(--color-surface-tertiary) hover:text-(--color-text-primary)"
                          }`}
                          style={{ animationDelay: `${index * 40}ms` }}
                        >
                          <div
                            className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg text-sm font-black text-white"
                            style={{ backgroundColor: instance.iconColor }}
                          >
                            {instance.name[0]?.toUpperCase()}
                          </div>
                          <div className="min-w-0 flex-1">
                            <p className="truncate text-sm font-bold">{instance.name}</p>
                            <p className="text-[11px] text-(--color-text-secondary)">
                              {instance.version} &middot; {instance.loader} &middot; {instance.installedMods?.length ?? 0} mods
                            </p>
                          </div>
                        </button>
                      ))
                    )}
                  </div>
                )}

                <button
                  type="button"
                  aria-expanded={showInstancePicker}
                  aria-haspopup="listbox"
                  aria-controls="home-instance-picker"
                  onClick={() => setShowInstancePicker((open) => !open)}
                  className="selector-btn flex w-full items-center justify-between gap-4 rounded-[1.125rem] border border-(--color-border) bg-(--color-surface) px-4 py-4 text-left hover:bg-(--color-surface-tertiary)"
                >
                  {selectedInstance ? (
                    <div className="flex min-w-0 items-center gap-3">
                      <div
                        className="flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-xl text-sm font-black text-white"
                        style={{ backgroundColor: selectedInstance.iconColor }}
                      >
                        {selectedInstance.name[0]?.toUpperCase()}
                      </div>
                      <div className="min-w-0">
                        <p className="truncate text-sm font-bold text-(--color-text-primary)">
                          {selectedInstance.name}
                        </p>
                        <p className="text-[11px] text-(--color-text-secondary)">
                          {selectedInstance.version} &middot; {selectedInstance.loader}
                        </p>
                      </div>
                    </div>
                  ) : (
                    <div className="flex items-center gap-3">
                      <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-(--color-surface-tertiary)">
                        <User size={16} className="text-(--color-text-secondary)" />
                      </div>
                      <div>
                        <p className="text-sm font-bold text-(--color-text-primary)">No Instance Selected</p>
                        <p className="text-[11px] text-(--color-text-secondary)">Choose or create an instance</p>
                      </div>
                    </div>
                  )}

                  <ChevronDown
                    size={16}
                    className={`flex-shrink-0 text-(--color-text-secondary) transition-transform duration-300 ${
                      showInstancePicker ? "rotate-180" : ""
                    }`}
                  />
                </button>
              </div>

              {selectedInstance?.lastPlayed && (
                <p className="mt-3 text-xs font-medium text-(--color-text-secondary)">
                  Last played{" "}
                  {new Date(selectedInstance.lastPlayed).toLocaleDateString(undefined, {
                    month: "short",
                    day: "numeric",
                    hour: "numeric",
                    minute: "2-digit",
                  })}
                </p>
              )}

              {!selectedInstance && (
                <button
                  type="button"
                  onClick={() => onNavigate("instances")}
                  className="mt-4 inline-flex items-center gap-2 text-sm font-semibold text-(--color-accent) transition-colors hover:text-(--color-accent-hover)"
                >
                  Open instances
                  <ArrowRight size={14} />
                </button>
              )}
            </div>

            <div className="flex w-full max-w-sm flex-col items-stretch gap-3 lg:items-end">
              {launching && launchProgress && (
                <div className="w-full rounded-[1.125rem] border border-(--color-border) bg-(--color-surface) px-4 py-3 text-xs font-medium text-(--color-text-secondary)">
                  <div className="flex items-center justify-between gap-3">
                    <span className="truncate">{launchProgress.message}</span>
                    {launchProgress.percent >= 0 && (
                      <span className="flex-shrink-0">{launchProgress.percent}%</span>
                    )}
                  </div>
                </div>
              )}

              {launchError && (
                <div className="w-full rounded-[1.125rem] border border-red-500/20 bg-red-500/10 px-4 py-3 text-xs font-medium text-red-500">
                  {launchError}
                </div>
              )}

              <button
                type="button"
                onClick={handleLaunch}
                disabled={!selectedInstance || instances.isLaunching || instances.isGameRunning}
                className={`launch-btn flex w-full items-center justify-center gap-3 rounded-[1.125rem] px-7 py-4 text-lg font-black tracking-tight text-white transition-all duration-300 disabled:cursor-default disabled:opacity-40 disabled:hover:translate-y-0 ${
                  gameRunning
                    ? "bg-green-500 hover:bg-green-600"
                    : "bg-(--color-accent) hover:bg-(--color-accent-hover)"
                }`}
              >
                <span className="launch-shimmer" />
                {launching ? (
                  <>
                    <Loader2 size={18} className="relative z-10 animate-spin" />
                    <span className="relative z-10">Launching...</span>
                  </>
                ) : gameRunning ? (
                  <>
                    <Square size={16} fill="white" className="relative z-10" />
                    <span className="relative z-10">Running</span>
                  </>
                ) : (
                  <>
                    <Play size={18} fill="white" className="relative z-10" />
                    <span className="relative z-10">Launch</span>
                  </>
                )}
              </button>
            </div>
          </div>
        </section>
      </div>
    </div>
  );
});
