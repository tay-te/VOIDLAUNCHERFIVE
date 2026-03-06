import { useState, useEffect, useMemo } from "react";
import { observer } from "mobx-react-lite";
import { useStore } from "../stores";
import {
  X,
  Check,
  ChevronRight,
  ChevronLeft,
  Download,
  Box,
  Zap,
  Hammer,
  Package,
  AlertTriangle,
  Loader2,
  Shield,
  CircleDot,
  CheckCircle2,
  XCircle,
  Info,
  Sparkles,
} from "lucide-react";
import {
  getProjectVersions,
  getProjects,
  getVersion,
  type ModrinthProject,
  type ModVersion,
} from "../api/modrinth";
import type { Instance } from "../stores/InstanceStore";

const LOADER_META: Record<
  string,
  { label: string; color: string; icon: typeof Box }
> = {
  vanilla: { label: "Vanilla", color: "#22c55e", icon: Box },
  fabric: { label: "Fabric", color: "#dba678", icon: Zap },
  forge: { label: "Forge", color: "#3b82f6", icon: Hammer },
};

interface Props {
  mod: ModrinthProject;
  onClose: () => void;
  onInstalled: () => void;
}

interface ResolvedDependency {
  project: ModrinthProject;
  version: ModVersion | null;
  type: "required" | "optional" | "incompatible" | "embedded";
  alreadyInstalled: boolean;
}

type Step = "pick-instance" | "resolving" | "review" | "installing" | "done" | "error";

export const ModInstallModal = observer(({ mod, onClose, onInstalled }: Props) => {
  const { instances: store } = useStore();
  const [step, setStep] = useState<Step>("pick-instance");
  const [selectedInstanceId, setSelectedInstanceId] = useState<string | null>(null);
  const [resolvedVersion, setResolvedVersion] = useState<ModVersion | null>(null);
  const [dependencies, setDependencies] = useState<ResolvedDependency[]>([]);
  const [installOptionalDeps, setInstallOptionalDeps] = useState<Set<string>>(new Set());
  const [installProgress, setInstallProgress] = useState(0);
  const [installStatus, setInstallStatus] = useState("");
  const [error, setError] = useState("");
  const [noVersionFound, setNoVersionFound] = useState(false);

  const selectedInstance = useMemo(
    () => store.instances.find((i) => i.id === selectedInstanceId) ?? null,
    [selectedInstanceId, store.instances]
  );

  const alreadyInstalled = selectedInstance
    ? store.hasModInstalled(selectedInstance.id, mod.id)
    : false;

  // Compatible instances (non-vanilla loaders, or any for vanilla-compatible mods)
  const compatibleInstances = store.instances.filter(
    (i) => i.loader !== "vanilla"
  );

  const handleSelectInstance = async (instance: Instance) => {
    setSelectedInstanceId(instance.id);
    setStep("resolving");
    setNoVersionFound(false);

    try {
      // Fetch all versions for this mod
      const allVersions = await getProjectVersions(mod.id);

      // Find the best version for this instance
      const bestVersion = findBestVersion(allVersions, instance);

      if (!bestVersion) {
        setNoVersionFound(true);
        setStep("error");
        setError(
          `No compatible version found for ${mod.title} on Minecraft ${instance.version} with ${instance.loader}`
        );
        return;
      }

      setResolvedVersion(bestVersion);

      // Resolve dependencies
      const deps = await resolveDependencies(bestVersion, instance);
      setDependencies(deps);

      // Auto-select required dependencies for install
      const optSet = new Set<string>();
      deps
        .filter((d) => d.type === "optional" && !d.alreadyInstalled)
        .forEach((d) => optSet.add(d.project.id));
      setInstallOptionalDeps(optSet);

      setStep("review");
    } catch (err) {
      setStep("error");
      setError(err instanceof Error ? err.message : "Failed to resolve mod version");
    }
  };

  const handleInstall = async () => {
    if (!resolvedVersion || !selectedInstance) return;
    setStep("installing");
    setInstallProgress(0);

    try {
      // Build list of mods to install
      const modsToInstall: { version: ModVersion; project: ModrinthProject }[] = [
        { version: resolvedVersion, project: mod },
      ];

      // Add required deps + selected optional deps
      for (const dep of dependencies) {
        if (dep.alreadyInstalled || !dep.version) continue;
        if (dep.type === "required" || dep.type === "embedded") {
          modsToInstall.push({ version: dep.version, project: dep.project });
        } else if (dep.type === "optional" && installOptionalDeps.has(dep.project.id)) {
          modsToInstall.push({ version: dep.version, project: dep.project });
        }
      }

      const total = modsToInstall.length;

      for (let i = 0; i < modsToInstall.length; i++) {
        const { version, project } = modsToInstall[i];

        // Re-check at install time in case it was installed between resolution and now,
        // or by an earlier dependency in this same batch
        if (store.hasModInstalled(selectedInstance.id, project.id)) {
          continue;
        }

        const primaryFile = version.files.find((f) => f.primary) ?? version.files[0];
        if (!primaryFile) continue;

        setInstallStatus(`Downloading ${project.title}...`);
        setInstallProgress(Math.round(((i) / total) * 100));

        const result = await window.electronAPI.downloadMod({
          instanceId: selectedInstance.id,
          url: primaryFile.url,
          filename: primaryFile.filename,
        });

        if (!result.success) {
          throw new Error(`Failed to download ${project.title}: ${result.error}`);
        }

        // Save to instance
        store.addMod(selectedInstance.id, {
          projectId: project.id,
          versionId: version.id,
          filename: primaryFile.filename,
          title: project.title,
          iconUrl: project.icon_url,
        });
      }

      setInstallProgress(100);
      setInstallStatus("Installation complete!");
      setStep("done");
    } catch (err) {
      setStep("error");
      setError(err instanceof Error ? err.message : "Installation failed");
    }
  };

  const handleDone = () => {
    onInstalled();
    onClose();
  };

  // Listen for download progress from main process
  useEffect(() => {
    const handler = (data: { percent: number }) => {
      // Granular progress within current file
    };
    window.electronAPI.onDownloadProgress(handler);
  }, []);

  return (
    <div
      className="fixed inset-0 z-[200] flex items-center justify-center wizard-backdrop"
      onClick={(e) => {
        if (e.target === e.currentTarget && step !== "installing") onClose();
      }}
    >
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" />

      <div className="relative w-full max-w-xl mx-4 wizard-modal">
        <div className="glass rounded-2xl overflow-hidden shadow-2xl shadow-black/30">
          {/* Header */}
          <div className="relative px-7 pt-7 pb-5">
            {step !== "installing" && (
              <button
                onClick={onClose}
                className="absolute top-5 right-5 w-8 h-8 rounded-full flex items-center justify-center hover:bg-(--color-surface-tertiary) text-(--color-text-secondary) transition-colors cursor-pointer"
              >
                <X size={16} />
              </button>
            )}

            <div className="flex items-center gap-4">
              {mod.icon_url ? (
                <img
                  src={mod.icon_url}
                  alt={mod.title}
                  className="w-12 h-12 rounded-2xl object-cover flex-shrink-0 shadow-md ring-1 ring-black/5"
                />
              ) : (
                <div className="w-12 h-12 rounded-2xl bg-(--color-surface-tertiary) flex items-center justify-center text-(--color-text-secondary) flex-shrink-0">
                  <Package size={20} />
                </div>
              )}
              <div className="min-w-0">
                <h2 className="text-xl font-black tracking-tight text-(--color-text-primary) truncate">
                  Install {mod.title}
                </h2>
                <p className="text-xs text-(--color-text-secondary) mt-0.5">
                  {step === "pick-instance" && "Choose an instance to install to"}
                  {step === "resolving" && "Finding the best version..."}
                  {step === "review" && "Review and confirm installation"}
                  {step === "installing" && "Installing..."}
                  {step === "done" && "Successfully installed!"}
                  {step === "error" && "Something went wrong"}
                </p>
              </div>
            </div>

            {/* Step progress dots */}
            <div className="flex items-center gap-2 mt-5">
              {["pick-instance", "review", "done"].map((s, i) => {
                const stepOrder = ["pick-instance", "resolving", "review", "installing", "done", "error"];
                const currentIdx = stepOrder.indexOf(step);
                const targetIdx = [0, 2, 4][i];
                const isActive = currentIdx >= targetIdx;
                const isCurrent = (i === 0 && currentIdx <= 1) ||
                  (i === 1 && (currentIdx === 2 || currentIdx === 3)) ||
                  (i === 2 && currentIdx >= 4);
                return (
                  <div key={s} className="flex items-center flex-1 last:flex-initial">
                    <div
                      className={`w-2 h-2 rounded-full transition-all duration-300 ${
                        isCurrent
                          ? "w-6 bg-(--color-accent) shadow-md shadow-(--color-accent)/30"
                          : isActive
                          ? "bg-(--color-accent)"
                          : "bg-(--color-surface-tertiary)"
                      }`}
                    />
                    {i < 2 && (
                      <div className="flex-1 mx-2">
                        <div className="h-px bg-(--color-border) relative overflow-hidden">
                          <div
                            className="absolute inset-y-0 left-0 bg-(--color-accent) transition-all duration-500"
                            style={{ width: isActive && !isCurrent ? "100%" : isCurrent && i === 0 ? "100%" : "0%" }}
                          />
                        </div>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>

          {/* Content */}
          <div className="px-7 pb-2 min-h-[300px] max-h-[460px] overflow-y-auto">
            <div key={step} className="wizard-step-enter">
              {step === "pick-instance" && (
                <InstancePicker
                  instances={compatibleInstances}
                  selectedId={selectedInstanceId}
                  modId={mod.id}
                  onSelect={handleSelectInstance}
                />
              )}
              {step === "resolving" && <ResolvingState />}
              {step === "review" && resolvedVersion && selectedInstance && (
                <ReviewStep
                  mod={mod}
                  version={resolvedVersion}
                  instance={selectedInstance}
                  dependencies={dependencies}
                  installOptionalDeps={installOptionalDeps}
                  onToggleOptional={(id) => {
                    const next = new Set(installOptionalDeps);
                    if (next.has(id)) next.delete(id);
                    else next.add(id);
                    setInstallOptionalDeps(next);
                  }}
                  alreadyInstalled={alreadyInstalled}
                />
              )}
              {step === "installing" && (
                <InstallingState progress={installProgress} status={installStatus} />
              )}
              {step === "done" && (
                <DoneState mod={mod} instance={selectedInstance!} />
              )}
              {step === "error" && <ErrorState message={error} />}
            </div>
          </div>

          {/* Footer */}
          <div className="px-7 pb-7 pt-3 flex items-center justify-between">
            {step === "pick-instance" && (
              <>
                <button
                  onClick={onClose}
                  className="px-5 py-2.5 rounded-full text-sm text-(--color-text-secondary) hover:bg-(--color-surface-tertiary) transition-colors cursor-pointer"
                >
                  Cancel
                </button>
                <div />
              </>
            )}
            {step === "review" && (
              <>
                <button
                  onClick={() => {
                    setStep("pick-instance");
                    setResolvedVersion(null);
                    setDependencies([]);
                  }}
                  className="flex items-center gap-2 px-5 py-2.5 rounded-full text-sm text-(--color-text-secondary) hover:bg-(--color-surface-tertiary) transition-colors cursor-pointer"
                >
                  <ChevronLeft size={16} />
                  Back
                </button>
                <button
                  onClick={handleInstall}
                  disabled={alreadyInstalled}
                  className="flex items-center gap-2 px-6 py-2.5 rounded-full bg-(--color-accent) hover:bg-(--color-accent-hover) text-white text-sm font-semibold transition-all cursor-pointer shadow-md shadow-(--color-accent)/20 disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  <Download size={15} />
                  {alreadyInstalled ? "Already Installed" : "Install"}
                </button>
              </>
            )}
            {step === "done" && (
              <>
                <div />
                <button
                  onClick={handleDone}
                  className="flex items-center gap-2 px-6 py-2.5 rounded-full bg-(--color-accent) hover:bg-(--color-accent-hover) text-white text-sm font-semibold transition-all cursor-pointer shadow-md shadow-(--color-accent)/20"
                >
                  <Check size={15} />
                  Done
                </button>
              </>
            )}
            {step === "error" && (
              <>
                <button
                  onClick={() => {
                    setStep("pick-instance");
                    setError("");
                  }}
                  className="flex items-center gap-2 px-5 py-2.5 rounded-full text-sm text-(--color-text-secondary) hover:bg-(--color-surface-tertiary) transition-colors cursor-pointer"
                >
                  <ChevronLeft size={16} />
                  Try Again
                </button>
                <button
                  onClick={onClose}
                  className="px-5 py-2.5 rounded-full text-sm text-(--color-text-secondary) hover:bg-(--color-surface-tertiary) transition-colors cursor-pointer"
                >
                  Close
                </button>
              </>
            )}
            {(step === "resolving" || step === "installing") && (
              <>
                <div />
                <div />
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  );
});

/* ----- Sub-components ----- */

function InstancePicker({
  instances,
  selectedId,
  modId,
  onSelect,
}: {
  instances: Instance[];
  selectedId: string | null;
  modId: string;
  onSelect: (instance: Instance) => void;
}) {
  const { instances: store } = useStore();

  if (instances.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-16 text-(--color-text-secondary)">
        <div className="w-14 h-14 rounded-2xl bg-(--color-accent)/10 flex items-center justify-center mb-4">
          <AlertTriangle size={24} className="text-(--color-accent)" />
        </div>
        <p className="text-sm font-medium text-(--color-text-primary)">
          No compatible instances
        </p>
        <p className="text-xs mt-1 text-center max-w-xs">
          You need at least one instance with Fabric or Forge to install mods. Create an
          instance first.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-2">
      {instances.map((inst) => {
        const meta = LOADER_META[inst.loader] ?? LOADER_META.vanilla;
        const LoaderIcon = meta.icon;
        const letter = inst.name[0]?.toUpperCase() ?? "?";
        const isInstalled = store.hasModInstalled(inst.id, modId);

        return (
          <button
            key={inst.id}
            onClick={() => onSelect(inst)}
            className={`w-full flex items-center gap-4 p-4 rounded-2xl border transition-all cursor-pointer group ${
              selectedId === inst.id
                ? "border-(--color-accent) bg-(--color-accent)/5"
                : "border-(--color-border) hover:border-(--color-text-secondary)/30 hover:bg-(--color-surface-tertiary)/30"
            }`}
          >
            <div
              className="w-11 h-11 rounded-xl flex items-center justify-center text-base font-bold flex-shrink-0 transition-transform group-hover:scale-105"
              style={{
                backgroundColor: inst.iconColor + "18",
                color: inst.iconColor,
              }}
            >
              {letter}
            </div>
            <div className="flex-1 min-w-0 text-left">
              <div className="flex items-center gap-2">
                <span className="text-sm font-semibold text-(--color-text-primary) truncate">
                  {inst.name}
                </span>
                {isInstalled && (
                  <span className="text-[10px] px-2 py-0.5 rounded-full bg-green-500/10 text-green-500 font-semibold flex-shrink-0">
                    Installed
                  </span>
                )}
              </div>
              <div className="flex items-center gap-2 mt-1">
                <span className="text-[11px] text-(--color-text-secondary) bg-(--color-surface-tertiary)/80 px-2 py-0.5 rounded-md">
                  {inst.version}
                </span>
                <span
                  className="text-[10px] px-2 py-0.5 rounded-md font-semibold flex items-center gap-1"
                  style={{
                    backgroundColor: meta.color + "15",
                    color: meta.color,
                  }}
                >
                  <LoaderIcon size={9} />
                  {meta.label}
                </span>
                <span className="text-[11px] text-(--color-text-secondary)">
                  {inst.installedMods.length} mod{inst.installedMods.length !== 1 ? "s" : ""}
                </span>
              </div>
            </div>
            <ChevronRight
              size={16}
              className="text-(--color-text-secondary) group-hover:text-(--color-text-primary) transition-colors flex-shrink-0"
            />
          </button>
        );
      })}
    </div>
  );
}

function ResolvingState() {
  return (
    <div className="flex flex-col items-center justify-center py-20">
      <div className="relative">
        <div className="w-16 h-16 rounded-full bg-(--color-accent)/10 flex items-center justify-center">
          <Loader2 size={28} className="text-(--color-accent) animate-spin" />
        </div>
        <div className="absolute -bottom-1 -right-1 w-7 h-7 rounded-full bg-(--color-surface) border border-(--color-border) flex items-center justify-center">
          <Shield size={13} className="text-(--color-accent)" />
        </div>
      </div>
      <p className="text-base font-bold text-(--color-text-primary) mt-5">
        Resolving compatibility
      </p>
      <p className="text-xs text-(--color-text-secondary) mt-1">
        Finding the best version and checking dependencies...
      </p>
    </div>
  );
}

function ReviewStep({
  mod,
  version,
  instance,
  dependencies,
  installOptionalDeps,
  onToggleOptional,
  alreadyInstalled,
}: {
  mod: ModrinthProject;
  version: ModVersion;
  instance: Instance;
  dependencies: ResolvedDependency[];
  installOptionalDeps: Set<string>;
  onToggleOptional: (id: string) => void;
  alreadyInstalled: boolean;
}) {
  const meta = LOADER_META[instance.loader] ?? LOADER_META.vanilla;
  const primaryFile = version.files.find((f) => f.primary) ?? version.files[0];
  const requiredDeps = dependencies.filter((d) => d.type === "required" || d.type === "embedded");
  const optionalDeps = dependencies.filter((d) => d.type === "optional");
  const incompatibleDeps = dependencies.filter((d) => d.type === "incompatible");

  const formatSize = (bytes: number) => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  };

  return (
    <div className="space-y-4">
      {alreadyInstalled && (
        <div className="flex items-center gap-3 p-3.5 rounded-2xl bg-amber-500/10 border border-amber-500/20">
          <AlertTriangle size={16} className="text-amber-500 flex-shrink-0" />
          <p className="text-xs text-amber-500 font-medium">
            This mod is already installed in {instance.name}
          </p>
        </div>
      )}

      {/* Version info card */}
      <div className="rounded-2xl bg-(--color-surface-tertiary)/30 border border-(--color-border) p-4 space-y-3">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-xs font-medium text-(--color-text-secondary) uppercase tracking-wider">
              Version
            </p>
            <p className="text-sm font-bold text-(--color-text-primary) mt-0.5">
              {version.version_number}
            </p>
          </div>
          <div className="flex items-center gap-2">
            <span
              className={`text-[10px] px-2.5 py-1 rounded-full font-semibold ${
                version.version_type === "release"
                  ? "bg-green-500/10 text-green-500"
                  : version.version_type === "beta"
                  ? "bg-amber-500/10 text-amber-500"
                  : "bg-red-500/10 text-red-500"
              }`}
            >
              {version.version_type}
            </span>
          </div>
        </div>

        <div className="h-px bg-(--color-border)" />

        <div className="grid grid-cols-2 gap-3">
          <div>
            <p className="text-[10px] font-medium text-(--color-text-secondary) uppercase tracking-wider">
              Game Version
            </p>
            <div className="flex flex-wrap gap-1 mt-1">
              {version.game_versions
                .filter((v) => v === instance.version)
                .concat(
                  version.game_versions.filter((v) => v !== instance.version).slice(0, 2)
                )
                .map((v) => (
                  <span
                    key={v}
                    className={`text-[10px] px-2 py-0.5 rounded-full font-medium ${
                      v === instance.version
                        ? "bg-(--color-accent)/10 text-(--color-accent)"
                        : "bg-(--color-surface-tertiary) text-(--color-text-secondary)"
                    }`}
                  >
                    {v}
                    {v === instance.version && " (match)"}
                  </span>
                ))}
            </div>
          </div>
          <div>
            <p className="text-[10px] font-medium text-(--color-text-secondary) uppercase tracking-wider">
              Loader
            </p>
            <div className="flex flex-wrap gap-1 mt-1">
              {version.loaders.map((l) => (
                <span
                  key={l}
                  className={`text-[10px] px-2 py-0.5 rounded-full font-semibold capitalize ${
                    l === instance.loader
                      ? "bg-(--color-accent)/10 text-(--color-accent)"
                      : "bg-(--color-surface-tertiary) text-(--color-text-secondary)"
                  }`}
                >
                  {l}
                  {l === instance.loader && " (match)"}
                </span>
              ))}
            </div>
          </div>
        </div>

        {primaryFile && (
          <>
            <div className="h-px bg-(--color-border)" />
            <div className="flex items-center justify-between">
              <span className="text-xs text-(--color-text-secondary) truncate">
                {primaryFile.filename}
              </span>
              <span className="text-xs font-medium text-(--color-text-secondary) flex-shrink-0 ml-2">
                {formatSize(primaryFile.size)}
              </span>
            </div>
          </>
        )}
      </div>

      {/* Installing to */}
      <div className="flex items-center gap-3 px-4 py-3 rounded-2xl bg-(--color-surface-tertiary)/30 border border-(--color-border)">
        <div
          className="w-8 h-8 rounded-lg flex items-center justify-center text-sm font-bold flex-shrink-0"
          style={{
            backgroundColor: instance.iconColor + "18",
            color: instance.iconColor,
          }}
        >
          {instance.name[0]?.toUpperCase() ?? "?"}
        </div>
        <div className="flex-1 min-w-0">
          <p className="text-xs font-medium text-(--color-text-primary) truncate">
            Installing to {instance.name}
          </p>
          <p className="text-[10px] text-(--color-text-secondary)">
            {instance.version} / {meta.label}
          </p>
        </div>
      </div>

      {/* Dependencies */}
      {requiredDeps.length > 0 && (
        <div className="space-y-2">
          <h4 className="text-xs font-bold text-(--color-text-secondary) uppercase tracking-wider flex items-center gap-1.5">
            <Shield size={11} />
            Required Dependencies ({requiredDeps.length})
          </h4>
          {requiredDeps.map((dep) => (
            <DependencyRow key={dep.project.id} dep={dep} />
          ))}
        </div>
      )}

      {optionalDeps.length > 0 && (
        <div className="space-y-2">
          <h4 className="text-xs font-bold text-(--color-text-secondary) uppercase tracking-wider flex items-center gap-1.5">
            <Info size={11} />
            Optional Dependencies ({optionalDeps.length})
          </h4>
          {optionalDeps.map((dep) => (
            <DependencyRow
              key={dep.project.id}
              dep={dep}
              toggleable
              selected={installOptionalDeps.has(dep.project.id)}
              onToggle={() => onToggleOptional(dep.project.id)}
            />
          ))}
        </div>
      )}

      {incompatibleDeps.length > 0 && (
        <div className="space-y-2">
          <h4 className="text-xs font-bold text-red-500 uppercase tracking-wider flex items-center gap-1.5">
            <AlertTriangle size={11} />
            Incompatible
          </h4>
          {incompatibleDeps.map((dep) => (
            <div
              key={dep.project.id}
              className="flex items-center gap-3 p-3 rounded-xl bg-red-500/5 border border-red-500/15"
            >
              {dep.project.icon_url ? (
                <img
                  src={dep.project.icon_url}
                  className="w-7 h-7 rounded-lg object-cover flex-shrink-0"
                />
              ) : (
                <div className="w-7 h-7 rounded-lg bg-red-500/10 flex items-center justify-center flex-shrink-0">
                  <XCircle size={14} className="text-red-500" />
                </div>
              )}
              <div className="flex-1 min-w-0">
                <p className="text-xs font-medium text-red-500 truncate">
                  {dep.project.title}
                </p>
                <p className="text-[10px] text-red-400/70">
                  This mod is incompatible
                </p>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function DependencyRow({
  dep,
  toggleable = false,
  selected = false,
  onToggle,
}: {
  dep: ResolvedDependency;
  toggleable?: boolean;
  selected?: boolean;
  onToggle?: () => void;
}) {
  return (
    <div
      className={`flex items-center gap-3 p-3 rounded-xl border transition-all ${
        dep.alreadyInstalled
          ? "bg-green-500/5 border-green-500/15"
          : toggleable
          ? "border-(--color-border) cursor-pointer hover:bg-(--color-surface-tertiary)/20"
          : "bg-(--color-surface-tertiary)/20 border-(--color-border)"
      }`}
      onClick={toggleable && !dep.alreadyInstalled ? onToggle : undefined}
    >
      {dep.project.icon_url ? (
        <img
          src={dep.project.icon_url}
          className="w-7 h-7 rounded-lg object-cover flex-shrink-0"
        />
      ) : (
        <div className="w-7 h-7 rounded-lg bg-(--color-surface-tertiary) flex items-center justify-center flex-shrink-0">
          <Package size={13} className="text-(--color-text-secondary)" />
        </div>
      )}
      <div className="flex-1 min-w-0">
        <p className="text-xs font-medium text-(--color-text-primary) truncate">
          {dep.project.title}
        </p>
        <p className="text-[10px] text-(--color-text-secondary)">
          {dep.alreadyInstalled
            ? "Already installed"
            : dep.version
            ? dep.version.version_number
            : "No compatible version found"}
        </p>
      </div>
      {dep.alreadyInstalled ? (
        <CheckCircle2 size={16} className="text-green-500 flex-shrink-0" />
      ) : toggleable ? (
        <div
          className={`w-5 h-5 rounded-md border-2 flex items-center justify-center flex-shrink-0 transition-all ${
            selected
              ? "border-(--color-accent) bg-(--color-accent)"
              : "border-(--color-border)"
          }`}
        >
          {selected && <Check size={11} className="text-white" />}
        </div>
      ) : (
        <CircleDot size={14} className="text-(--color-accent) flex-shrink-0" />
      )}
    </div>
  );
}

function InstallingState({
  progress,
  status,
}: {
  progress: number;
  status: string;
}) {
  return (
    <div className="flex flex-col items-center justify-center py-16">
      <div className="relative w-20 h-20">
        <svg className="w-full h-full -rotate-90" viewBox="0 0 80 80">
          <circle
            cx="40"
            cy="40"
            r="35"
            fill="none"
            stroke="var(--color-surface-tertiary)"
            strokeWidth="4"
          />
          <circle
            cx="40"
            cy="40"
            r="35"
            fill="none"
            stroke="var(--color-accent)"
            strokeWidth="4"
            strokeLinecap="round"
            strokeDasharray={`${2 * Math.PI * 35}`}
            strokeDashoffset={`${2 * Math.PI * 35 * (1 - progress / 100)}`}
            className="transition-all duration-300"
          />
        </svg>
        <div className="absolute inset-0 flex items-center justify-center">
          <span className="text-sm font-bold text-(--color-accent)">{progress}%</span>
        </div>
      </div>
      <p className="text-base font-bold text-(--color-text-primary) mt-5">{status}</p>
      <p className="text-xs text-(--color-text-secondary) mt-1">Please wait...</p>
    </div>
  );
}

function DoneState({
  mod,
  instance,
}: {
  mod: ModrinthProject;
  instance: Instance;
}) {
  return (
    <div className="flex flex-col items-center justify-center py-16">
      <div className="relative">
        <div className="w-20 h-20 rounded-full bg-green-500/10 flex items-center justify-center">
          <CheckCircle2 size={36} className="text-green-500" />
        </div>
        <div className="absolute -bottom-1 -right-1 w-8 h-8 rounded-full bg-(--color-surface) border border-(--color-border) flex items-center justify-center shadow-sm">
          <Sparkles size={14} className="text-(--color-accent)" />
        </div>
      </div>
      <p className="text-lg font-black text-(--color-text-primary) mt-5">
        {mod.title} installed!
      </p>
      <p className="text-xs text-(--color-text-secondary) mt-1">
        Added to {instance.name}
      </p>
    </div>
  );
}

function ErrorState({ message }: { message: string }) {
  return (
    <div className="flex flex-col items-center justify-center py-16">
      <div className="w-16 h-16 rounded-full bg-red-500/10 flex items-center justify-center">
        <XCircle size={28} className="text-red-500" />
      </div>
      <p className="text-base font-bold text-(--color-text-primary) mt-5">
        Installation failed
      </p>
      <p className="text-xs text-(--color-text-secondary) mt-1 text-center max-w-xs">
        {message}
      </p>
    </div>
  );
}

/* ----- Version resolution helpers ----- */

function findBestVersion(
  versions: ModVersion[],
  instance: Instance
): ModVersion | null {
  // Filter to versions matching this instance's game version AND loader
  const compatible = versions.filter(
    (v) =>
      v.game_versions.includes(instance.version) &&
      v.loaders.some((l) => l.toLowerCase() === instance.loader.toLowerCase())
  );

  if (compatible.length === 0) return null;

  // Prefer release > beta > alpha
  const typeOrder = { release: 0, beta: 1, alpha: 2 };
  compatible.sort((a, b) => {
    const typeA = typeOrder[a.version_type] ?? 3;
    const typeB = typeOrder[b.version_type] ?? 3;
    if (typeA !== typeB) return typeA - typeB;
    // Same type: prefer more recent
    return new Date(b.date_published).getTime() - new Date(a.date_published).getTime();
  });

  return compatible[0];
}

async function resolveDependencies(
  version: ModVersion,
  instance: Instance
): Promise<ResolvedDependency[]> {
  if (!version.dependencies || version.dependencies.length === 0) return [];

  // Get unique project IDs from dependencies
  const projectIds = version.dependencies
    .filter((d) => d.project_id)
    .map((d) => d.project_id!);

  if (projectIds.length === 0) return [];

  // Fetch all dependency projects in batch
  const projects = await getProjects(projectIds);
  const projectMap = new Map(projects.map((p) => [p.id, p]));

  const resolved: ResolvedDependency[] = [];

  for (const dep of version.dependencies) {
    if (!dep.project_id) continue;
    const project = projectMap.get(dep.project_id);
    if (!project) continue;

    // Check if already installed
    const alreadyInstalled = instance.installedMods?.some(
      (m) => m.projectId === dep.project_id
    ) ?? false;

    let depVersion: ModVersion | null = null;

    if (!alreadyInstalled && dep.dependency_type !== "incompatible") {
      if (dep.version_id) {
        try {
          depVersion = await getVersion(dep.version_id);
        } catch {
          // Fall back to finding best version
        }
      }

      if (!depVersion) {
        try {
          const depVersions = await getProjectVersions(dep.project_id);
          depVersion = findBestVersion(depVersions, instance);
        } catch {
          // Could not resolve
        }
      }
    }

    resolved.push({
      project,
      version: depVersion,
      type: dep.dependency_type,
      alreadyInstalled,
    });
  }

  return resolved;
}
