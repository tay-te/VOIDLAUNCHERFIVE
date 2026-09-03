import { lazy, Suspense, useEffect, useMemo, useRef, useState } from "react";
import { observer } from "mobx-react-lite";
import {
  Box,
  Camera,
  CheckCircle2,
  ChevronDown,
  Download,
  Loader2,
  Monitor,
  Play,
  RefreshCcw,
  Square,
  User,
} from "lucide-react";
import { useStore } from "../stores";
import type { Instance } from "../stores/InstanceStore";

const SkinViewer3D = lazy(() =>
  import("./SkinViewer").then((module) => ({ default: module.SkinViewer3D }))
);

interface HomePageProps {
  onOpenMod: (id: string) => void;
  onNavigate: (page: string) => void;
}

function getGreeting() {
  const h = new Date().getHours();
  if (h < 5) return "Late night ready";
  if (h < 12) return "Morning ready";
  if (h < 17) return "Afternoon ready";
  if (h < 21) return "Evening ready";
  return "Late session ready";
}

function getModCount(instance: Pick<Instance, "installedMods" | "modCount">) {
  return instance.installedMods.length > 0 ? instance.installedMods.length : instance.modCount;
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

function formatLastPlayed(value: string | null) {
  if (!value) return "Not launched yet";
  return new Date(value).toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}

function SkinViewerFallback() {
  return (
    <div className="h-44 w-full rounded-control border border-(--color-border) bg-(--color-surface-tertiary) animate-pulse" />
  );
}

function ReadinessRow({
  label,
  value,
}: {
  label: string;
  value: string;
}) {
  return (
    <div className="flex items-center gap-3 border-b border-(--color-border) py-3 last:border-0">
      <CheckCircle2 size={16} className="text-(--color-success)" />
      <span className="text-sm font-emphasis text-(--color-text-primary)">{label}</span>
      <span className="ml-auto text-xs font-body text-(--color-text-secondary)">
        {value}
      </span>
    </div>
  );
}

export const HomePage = observer(({ onNavigate }: HomePageProps) => {
  const { auth, instances } = useStore();
  const [selectedInstanceId, setSelectedInstanceId] = useState<string | null>(null);
  const [showInstancePicker, setShowInstancePicker] = useState(false);
  const pickerRef = useRef<HTMLDivElement>(null);

  const greeting = useMemo(() => getGreeting(), []);
  const sortedInstances = useMemo(
    () => sortInstancesByRecentPlay(instances.instances),
    [
      instances.instances
        .map((instance) => `${instance.id}:${instance.lastPlayed ?? ""}:${getModCount(instance)}`)
        .join("|"),
    ]
  );
  useEffect(() => {
    if (instances.instances.length === 0) {
      setSelectedInstanceId(null);
      setShowInstancePicker(false);
      return;
    }

    if (selectedInstanceId && instances.instances.some((instance) => instance.id === selectedInstanceId)) {
      return;
    }

    setSelectedInstanceId(sortedInstances[0]?.id ?? null);
  }, [instances.instances.length, selectedInstanceId, sortedInstances]);

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
  const selectedModCount = selectedInstance ? getModCount(selectedInstance) : 0;
  const launching = instances.launchingInstanceId === selectedInstanceId;
  const gameRunning = instances.runningInstanceId === selectedInstanceId;
  const launchProgress = launching || gameRunning ? instances.launchProgress : null;
  const launchError = instances.launchError;

  const handleLaunch = () => {
    if (!selectedInstance) return;
    instances.launchGame(selectedInstance.id);
  };

  return (
    <div className="h-full p-7 max-md:min-h-full max-md:p-4">
      <div className="grid h-full min-h-0 gap-6 xl:grid-cols-[minmax(0,1fr)_22rem] max-md:h-auto">
        <section className="flex min-h-[34rem] flex-col overflow-hidden rounded-card border border-(--color-border) bg-(--color-surface-secondary)">
          <div className="flex flex-wrap items-center justify-between gap-4 border-b border-(--color-border) px-5 py-4">
            <div className="min-w-0">
              <p className="text-xs font-strong text-(--color-text-secondary)">
                {selectedInstance?.version ?? "No version"} / {selectedInstance?.loader ?? "loader"}
              </p>
              <h2 className="mt-1 truncate text-2xl font-display leading-none text-(--color-text-primary)">
                {selectedInstance?.name ?? "Select an instance"}
              </h2>
            </div>

            <div className="flex flex-wrap items-center gap-2">
              <div className="overflow-hidden rounded-control border border-(--color-border) bg-(--color-surface)">
                {[
                  ["Iso", Box],
                  ["Profile", Monitor],
                  ["Mods", Download],
                ].map(([label, Icon], index) => {
                  const ViewIcon = Icon as typeof Box;
                  return (
                    <button
                      key={label as string}
                      type="button"
                      className={`h-10 border-r border-(--color-border) px-3 text-xs font-display last:border-r-0 ${
                        index === 0
                          ? "bg-(--color-surface-tertiary) text-(--color-text-primary)"
                          : "text-(--color-text-secondary) hover:bg-(--color-surface-tertiary) hover:text-(--color-text-primary)"
                      }`}
                    >
                      <span className="flex items-center gap-2">
                        <ViewIcon size={15} />
                        {label as string}
                      </span>
                    </button>
                  );
                })}
              </div>
              <button
                type="button"
                className="flex h-10 items-center gap-2 rounded-control border border-(--color-border) bg-(--color-surface) px-3 text-xs font-display text-(--color-text-primary) transition-colors hover:bg-(--color-surface-tertiary)"
              >
                <Camera size={15} />
                Snapshot
              </button>
            </div>
          </div>

          <div className="relative flex min-h-0 flex-1 items-center justify-center overflow-hidden bg-stage">
            <div className="absolute inset-x-0 bottom-0 h-[70%] origin-bottom scale-x-125 bg-[linear-gradient(90deg,rgba(255,255,255,0.08)_1px,transparent_1px),linear-gradient(0deg,rgba(255,255,255,0.08)_1px,transparent_1px)] bg-[size:42px_42px] opacity-50 [transform:perspective(820px)_rotateX(62deg)]" />
            <div className="absolute inset-0 bg-[radial-gradient(circle_at_center,rgba(134,239,138,0.13),transparent_42%),linear-gradient(180deg,transparent_0%,rgba(0,0,0,0.42)_100%)]" />
            <div className="relative z-10 flex w-full max-w-6xl items-center justify-center px-5 py-7">
              <div className="relative aspect-[16/9] w-full overflow-hidden rounded-control border border-(--color-border) bg-(--color-preview-panel)">
                <div className="absolute inset-0 bg-[linear-gradient(90deg,rgba(255,255,255,0.045)_1px,transparent_1px),linear-gradient(0deg,rgba(255,255,255,0.045)_1px,transparent_1px)] bg-[size:36px_36px]" />
                <div className="absolute inset-x-0 bottom-0 h-1/2 bg-[radial-gradient(ellipse_at_center,rgba(134,239,138,0.18),transparent_58%)]" />
                <Suspense fallback={<SkinViewerFallback />}>
                  <SkinViewer3D
                    username={auth.username}
                    className="relative z-10 h-full w-full scale-110"
                  />
                </Suspense>
                <div className="absolute bottom-5 left-5 z-20 flex items-center gap-3 rounded-control border border-(--color-border) bg-stage/70 px-3 py-2 text-xs font-display text-(--color-text-primary) backdrop-blur">
                  <span className="text-(--color-accent)">Iso</span>
                  <span className="text-(--color-text-secondary)">Interactive skin preview</span>
                </div>
              </div>
            </div>
          </div>
        </section>

        <aside className="flex min-h-0 flex-col gap-4 overflow-y-auto">
          <section className="rounded-card border border-(--color-border) bg-(--color-surface-secondary) p-5">
            <div className="flex items-center justify-between gap-3">
              <div>
                <p className="text-caption font-display text-(--color-text-secondary)">
                  Current Instance
                </p>
                <h3 className="mt-1 text-lg font-display text-(--color-text-primary)">
                  Launch Control
                </h3>
              </div>
              <div className="rounded-control border border-(--color-border) bg-(--color-surface) px-3 py-2 text-xs font-display text-(--color-success)">
                {gameRunning ? "Running" : launching ? "Launching" : "Ready"}
              </div>
            </div>

            <p className="mt-4 text-sm font-body leading-relaxed text-(--color-text-secondary)">
              {greeting}, {auth.username}. Choose an instance and launch from the same control surface.
            </p>

            <div ref={pickerRef} className="relative mt-5">
                  {showInstancePicker && (
                    <div
                      id="home-instance-picker"
                      role="listbox"
                      className="absolute left-0 top-full z-50 mt-2 max-h-72 w-full overflow-y-auto rounded-control border border-(--color-glass-border) bg-(--color-glass) p-1.5 shadow-2xl shadow-black/25 backdrop-blur-xl picker-dropdown"
                    >
                      {instances.instances.length === 0 ? (
                        <div className="px-4 py-6 text-center">
                          <p className="text-sm font-strong text-(--color-text-primary)">No instances yet</p>
                          <p className="mt-1 text-xs text-(--color-text-secondary)">Create one to get started</p>
                          <button
                            type="button"
                            onClick={() => {
                              setShowInstancePicker(false);
                              onNavigate("instances");
                            }}
                            className="mt-3 rounded-control bg-(--color-accent) px-4 py-2 text-xs font-emphasis text-fg-on-accent transition-colors hover:bg-(--color-accent-hover) dark:text-fg-on-accent"
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
                            className={`picker-item flex w-full items-center gap-3 rounded-control px-3 py-2.5 text-left transition-colors ${
                              selectedInstanceId === instance.id
                                ? "bg-(--color-accent)/10 text-(--color-accent)"
                                : "text-(--color-text-secondary) hover:bg-(--color-surface-tertiary) hover:text-(--color-text-primary)"
                            }`}
                            style={{ animationDelay: `${index * 40}ms` }}
                          >
                            <div
                              className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-control text-sm font-display text-white"
                              style={{ backgroundColor: instance.iconColor }}
                            >
                              {instance.name[0]?.toUpperCase()}
                            </div>
                            <div className="min-w-0 flex-1">
                              <p className="truncate text-sm font-strong">{instance.name}</p>
                              <p className="text-caption text-(--color-text-secondary)">
                                {instance.version} &middot; {instance.loader} &middot; {getModCount(instance)} mods
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
                    className="flex w-full items-center justify-between gap-4 rounded-control border border-(--color-border) bg-(--color-surface) px-4 py-3 text-left transition-colors hover:bg-(--color-surface-tertiary)"
                  >
                    {selectedInstance ? (
                      <div className="flex min-w-0 items-center gap-3">
                        <div
                          className="flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-control text-sm font-display text-white"
                          style={{ backgroundColor: selectedInstance.iconColor }}
                        >
                          {selectedInstance.name[0]?.toUpperCase()}
                        </div>
                        <div className="min-w-0">
                          <p className="truncate text-sm font-strong text-(--color-text-primary)">
                            {selectedInstance.name}
                          </p>
                          <p className="text-caption text-(--color-text-secondary)">
                            {selectedInstance.version} &middot; {selectedInstance.loader}
                          </p>
                        </div>
                      </div>
                    ) : (
                      <div className="flex items-center gap-3">
                        <div className="flex h-11 w-11 items-center justify-center rounded-control bg-(--color-surface-tertiary)">
                          <User size={16} className="text-(--color-text-secondary)" />
                        </div>
                        <div>
                          <p className="text-sm font-strong text-(--color-text-primary)">No Instance Selected</p>
                          <p className="text-caption text-(--color-text-secondary)">Choose or create an instance</p>
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

            <dl className="mt-5 grid grid-cols-2 gap-3">
              {[
                ["Version", selectedInstance?.version ?? "-"],
                ["Loader", selectedInstance?.loader ?? "-"],
                ["Mods", `${selectedModCount}`],
                ["Last", formatLastPlayed(selectedInstance?.lastPlayed ?? null)],
              ].map(([label, value]) => (
                <div key={label} className="rounded-control border border-(--color-border) bg-(--color-surface) p-3">
                  <dt className="text-micro font-display text-(--color-text-secondary)">{label}</dt>
                  <dd className="mt-1 truncate text-sm font-display capitalize text-(--color-text-primary)">{value}</dd>
                </div>
              ))}
            </dl>

            <div className="mt-5 grid grid-cols-[1fr_auto] gap-2">
              <button
                type="button"
                onClick={handleLaunch}
                disabled={!selectedInstance || instances.isLaunching || instances.isGameRunning}
                className={`flex h-12 items-center justify-center gap-2 rounded-control px-5 text-sm font-display transition-colors disabled:cursor-default disabled:opacity-45 ${
                  gameRunning
                    ? "bg-success text-white hover:bg-success"
                    : "bg-(--color-accent) text-fg-on-accent hover:bg-(--color-accent-hover)"
                }`}
              >
                {launching ? (
                  <>
                    <Loader2 size={16} className="animate-spin" />
                    Launching
                  </>
                ) : gameRunning ? (
                  <>
                    <Square size={14} fill="currentColor" />
                    Running
                  </>
                ) : (
                  <>
                    <Play size={16} fill="currentColor" />
                    Launch
                  </>
                )}
              </button>
              <button
                type="button"
                className="flex h-12 w-12 items-center justify-center rounded-control border border-(--color-border) bg-(--color-surface) text-(--color-text-secondary) transition-colors hover:bg-(--color-surface-tertiary) hover:text-(--color-text-primary)"
                aria-label="Refresh readiness"
              >
                <RefreshCcw size={17} />
              </button>
            </div>
          </section>

          <aside className="rounded-card border border-(--color-border) bg-(--color-surface-secondary) p-6">
            <p className="text-micro font-strong uppercase tracking-[0.2em] text-(--color-text-secondary)">
              Launch Readiness
            </p>

            <div className="mt-5">
              <ReadinessRow label="Game files" value="Up to date" />
              <ReadinessRow label="Mods" value={`${selectedModCount} enabled`} />
              <ReadinessRow label="Java" value="Ready" />
              <ReadinessRow label="Instance" value={selectedInstance ? "Ready" : "Missing"} />
            </div>

            {launching && launchProgress && (
              <div className="mt-5 rounded-control border border-(--color-border) bg-(--color-surface) px-4 py-3 text-xs font-body text-(--color-text-secondary)">
                <div className="flex items-center justify-between gap-3">
                  <span className="truncate">{launchProgress.message}</span>
                  {launchProgress.percent >= 0 && (
                    <span className="flex-shrink-0">{launchProgress.percent}%</span>
                  )}
                </div>
              </div>
            )}

            {launchError && (
              <div className="mt-5 rounded-control border border-danger/20 bg-danger/10 px-4 py-3 text-xs font-body text-danger">
                {launchError}
              </div>
            )}

          </aside>
        </aside>
      </div>
    </div>
  );
});
