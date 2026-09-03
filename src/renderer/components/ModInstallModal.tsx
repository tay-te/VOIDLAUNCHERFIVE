import { useEffect, useMemo, useState } from "react";
import { observer } from "mobx-react-lite";
import {
  AlertTriangle,
  Check,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  CircleDot,
  Download,
  Hammer,
  Info,
  Loader2,
  Package,
  Shield,
  Sparkles,
  X,
  XCircle,
  Zap,
  Box,
} from "lucide-react";
import { useStore } from "../stores";
import type { ModrinthProject, ModVersion } from "../api/modrinth";
import type { Instance } from "../stores/InstanceStore";
import type { InstallDependencyNode, InstallPreview } from "../stores/InstallStore";

const LOADER_META: Record<string, { label: string; color: string; icon: typeof Box }> = {
  vanilla: { label: "Vanilla", color: "#22c55e", icon: Box },
  fabric: { label: "Fabric", color: "#dba678", icon: Zap },
  forge: { label: "Forge", color: "#3b82f6", icon: Hammer },
};

interface Props {
  mod: ModrinthProject;
  preferredVersion?: ModVersion | null;
  onClose: () => void;
  onInstalled: () => void;
}

type Step = "pick-instance" | "resolving" | "review" | "installing" | "done" | "error";

export const ModInstallModal = observer(
  ({ mod, preferredVersion = null, onClose, onInstalled }: Props) => {
    const { installs, instances } = useStore();
    const [step, setStep] = useState<Step>("pick-instance");
    const [selectedInstanceId, setSelectedInstanceId] = useState<string | null>(
      installs.preferredInstanceId
    );
    const [preview, setPreview] = useState<InstallPreview | null>(null);
    const [error, setError] = useState("");

    const selectedInstance = useMemo(
      () =>
        instances.instances.find((instance) => instance.id === selectedInstanceId) ?? null,
      [instances.instances, selectedInstanceId]
    );

    const handleResolve = async (instanceId: string | null) => {
      setSelectedInstanceId(instanceId);
      setError("");
      setPreview(null);
      setStep("resolving");

      try {
        const nextPreview = await installs.previewProjectInstall(mod, {
          preferredInstanceId: instanceId,
          strictInstance: Boolean(instanceId),
          allowCreateInstance: !instanceId,
          preferredVersion,
          strictVersion: Boolean(preferredVersion),
        });

        setPreview(nextPreview);
        setStep("review");
      } catch (nextError) {
        setError(nextError instanceof Error ? nextError.message : "Failed to resolve install plan");
        setStep("error");
      }
    };

    const handleInstall = async () => {
      setStep("installing");
      setError("");

      try {
        await installs.installProject(mod, {
          preferredInstanceId: selectedInstanceId,
          strictInstance: Boolean(selectedInstanceId),
          allowCreateInstance: !selectedInstanceId,
          preferredVersion,
          strictVersion: Boolean(preferredVersion),
        });

        setStep("done");
      } catch (nextError) {
        setError(nextError instanceof Error ? nextError.message : "Installation failed");
        setStep("error");
      }
    };

    const handleDone = () => {
      onInstalled();
      onClose();
    };

    const installStatus = installs.activeJob?.currentItem ?? "Preparing install...";
    const installProgress = installs.activeJob?.percent ?? 0;
    const sortedInstances = installs.getSortedInstallInstances(mod.id);
    const rootAlreadyInstalled =
      preview?.instance && instances.hasModInstalled(preview.instance.id, mod.id);

    useEffect(() => {
      if (step !== "pick-instance") return;
      setSelectedInstanceId(installs.preferredInstanceId);
    }, [installs.preferredInstanceId, step]);

    return (
      <div
        className="fixed inset-0 z-[200] flex items-center justify-center wizard-backdrop"
        onClick={(event) => {
          if (event.target === event.currentTarget && step !== "installing") onClose();
        }}
      >
        <div className="absolute inset-0 bg-scrim backdrop-blur-sm" />

        <div className="relative mx-4 w-full max-w-2xl wizard-modal">
          <div className="glass overflow-hidden rounded-panel shadow-2xl shadow-black/30">
            <div className="relative px-7 pb-5 pt-7">
              {step !== "installing" && (
                <button
                  onClick={onClose}
                  className="absolute right-5 top-5 flex h-8 w-8 items-center justify-center rounded-pill text-(--color-text-secondary) transition-colors hover:bg-(--color-surface-tertiary) cursor-pointer"
                >
                  <X size={16} />
                </button>
              )}

              <div className="flex items-center gap-4">
                {mod.icon_url ? (
                  <img
                    src={mod.icon_url}
                    alt={mod.title}
                    className="h-12 w-12 rounded-panel object-cover shadow-md ring-1 ring-black/5"
                  />
                ) : (
                  <div className="flex h-12 w-12 items-center justify-center rounded-panel bg-(--color-surface-tertiary) text-(--color-text-secondary)">
                    <Package size={20} />
                  </div>
                )}
                <div className="min-w-0">
                  <h2 className="truncate text-xl font-display tracking-tight text-(--color-text-primary)">
                    Install {mod.title}
                  </h2>
                  <p className="mt-0.5 text-xs text-(--color-text-secondary)">
                    {step === "pick-instance" && "Choose a profile or keep it automatic"}
                    {step === "resolving" && "Checking compatibility and building the dependency plan"}
                    {step === "review" && "Review the profile, version, and dependency tree"}
                    {step === "installing" && "Installing with the unified engine"}
                    {step === "done" && "Install finished"}
                    {step === "error" && "Install could not be prepared"}
                  </p>
                </div>
              </div>

              <div className="mt-5 flex items-center gap-2">
                {["pick-instance", "review", "done"].map((key, index) => {
                  const stepOrder = ["pick-instance", "resolving", "review", "installing", "done", "error"];
                  const currentIdx = stepOrder.indexOf(step);
                  const targetIdx = [0, 2, 4][index];
                  const active = currentIdx >= targetIdx;
                  const current =
                    (index === 0 && currentIdx <= 1) ||
                    (index === 1 && (currentIdx === 2 || currentIdx === 3)) ||
                    (index === 2 && currentIdx >= 4);

                  return (
                    <div key={key} className="flex flex-1 items-center last:flex-initial">
                      <div
                        className={`h-2 rounded-pill transition-all duration-300 ${
                          current
                            ? "w-6 bg-(--color-accent) shadow-md shadow-(--color-accent)/30"
                            : active
                            ? "w-2 bg-(--color-accent)"
                            : "w-2 bg-(--color-surface-tertiary)"
                        }`}
                      />
                      {index < 2 && (
                        <div className="mx-2 flex-1">
                          <div className="relative h-px overflow-hidden bg-(--color-border)">
                            <div
                              className="absolute inset-y-0 left-0 bg-(--color-accent) transition-all duration-500"
                              style={{ width: active && !current ? "100%" : current && index === 0 ? "100%" : "0%" }}
                            />
                          </div>
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>

            <div className="min-h-[320px] max-h-[520px] overflow-y-auto px-7 pb-2">
              {step === "pick-instance" && (
                <TargetPickerStep
                  instances={sortedInstances}
                  selectedId={selectedInstanceId}
                  modId={mod.id}
                  onSelect={handleResolve}
                />
              )}
              {step === "resolving" && <ResolvingState />}
              {step === "review" && preview && (
                <ReviewStep
                  instance={selectedInstance}
                  preview={preview}
                  alreadyInstalled={Boolean(rootAlreadyInstalled && preview.plan.operations.length === 0)}
                />
              )}
              {step === "installing" && (
                <InstallingState progress={installProgress} status={installStatus} />
              )}
              {step === "done" && (
                <DoneState
                  mod={mod}
                  instanceName={
                    preview?.instance?.name ?? preview?.plan.createdSpec?.name ?? "new profile"
                  }
                />
              )}
              {step === "error" && <ErrorState message={error} />}
            </div>

            <div className="flex items-center justify-between px-7 pb-7 pt-3">
              {step === "pick-instance" && (
                <>
                  <button
                    onClick={onClose}
                    className="cursor-pointer rounded-pill px-5 py-2.5 text-sm text-(--color-text-secondary) transition-colors hover:bg-(--color-surface-tertiary)"
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
                      setPreview(null);
                      setStep("pick-instance");
                    }}
                    className="flex cursor-pointer items-center gap-2 rounded-pill px-5 py-2.5 text-sm text-(--color-text-secondary) transition-colors hover:bg-(--color-surface-tertiary)"
                  >
                    <ChevronLeft size={16} />
                    Back
                  </button>
                  <button
                    onClick={handleInstall}
                    className="flex cursor-pointer items-center gap-2 rounded-pill bg-(--color-accent) px-6 py-2.5 text-sm font-emphasis text-fg-on-accent shadow-md shadow-(--color-accent)/20 transition-all hover:bg-(--color-accent-hover)"
                  >
                    <Download size={15} />
                    Install
                  </button>
                </>
              )}

              {step === "done" && (
                <>
                  <div />
                  <button
                    onClick={handleDone}
                    className="flex cursor-pointer items-center gap-2 rounded-pill bg-(--color-accent) px-6 py-2.5 text-sm font-emphasis text-fg-on-accent shadow-md shadow-(--color-accent)/20 transition-all hover:bg-(--color-accent-hover)"
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
                      setError("");
                      setStep("pick-instance");
                    }}
                    className="flex cursor-pointer items-center gap-2 rounded-pill px-5 py-2.5 text-sm text-(--color-text-secondary) transition-colors hover:bg-(--color-surface-tertiary)"
                  >
                    <ChevronLeft size={16} />
                    Try Again
                  </button>
                  <button
                    onClick={onClose}
                    className="cursor-pointer rounded-pill px-5 py-2.5 text-sm text-(--color-text-secondary) transition-colors hover:bg-(--color-surface-tertiary)"
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
  }
);

function TargetPickerStep({
  instances,
  selectedId,
  modId,
  onSelect,
}: {
  instances: Instance[];
  selectedId: string | null;
  modId: string;
  onSelect: (instanceId: string | null) => void;
}) {
  const { instances: store } = useStore();

  return (
    <div className="space-y-3">
      <button
        onClick={() => onSelect(null)}
        className={`w-full rounded-panel border p-4 text-left transition-all cursor-pointer ${
          selectedId === null
            ? "border-(--color-accent) bg-(--color-accent)/5"
            : "border-(--color-border) hover:border-(--color-text-secondary)/30 hover:bg-(--color-surface-tertiary)/30"
        }`}
      >
        <div className="flex items-center gap-4">
          <div className="flex h-11 w-11 items-center justify-center rounded-card bg-(--color-accent)/10 text-(--color-accent)">
            <Sparkles size={18} />
          </div>
          <div className="flex-1">
            <p className="text-sm font-emphasis text-(--color-text-primary)">Auto-select</p>
            <p className="mt-1 text-xs text-(--color-text-secondary)">
              The engine chooses the best compatible profile, or creates one automatically.
            </p>
          </div>
          <ChevronRight size={16} className="text-(--color-text-secondary)" />
        </div>
      </button>

      {instances.map((instance) => {
        const meta = LOADER_META[instance.loader] ?? LOADER_META.vanilla;
        const LoaderIcon = meta.icon;
        const installed = store.hasModInstalled(instance.id, modId);

        return (
          <button
            key={instance.id}
            onClick={() => onSelect(instance.id)}
            className={`w-full rounded-panel border p-4 text-left transition-all cursor-pointer ${
              selectedId === instance.id
                ? "border-(--color-accent) bg-(--color-accent)/5"
                : "border-(--color-border) hover:border-(--color-text-secondary)/30 hover:bg-(--color-surface-tertiary)/30"
            }`}
          >
            <div className="flex items-center gap-4">
              <div
                className="flex h-11 w-11 items-center justify-center rounded-card text-base font-strong"
                style={{
                  backgroundColor: `${instance.iconColor}18`,
                  color: instance.iconColor,
                }}
              >
                {instance.name[0]?.toUpperCase() ?? "?"}
              </div>

              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <p className="truncate text-sm font-emphasis text-(--color-text-primary)">
                    {instance.name}
                  </p>
                  {installed && (
                    <span className="rounded-pill bg-success/10 px-2 py-0.5 text-micro font-emphasis text-success">
                      Installed
                    </span>
                  )}
                </div>
                <div className="mt-1 flex items-center gap-2">
                  <span className="rounded-md bg-(--color-surface-tertiary)/80 px-2 py-0.5 text-caption text-(--color-text-secondary)">
                    {instance.version}
                  </span>
                  <span
                    className="flex items-center gap-1 rounded-md px-2 py-0.5 text-micro font-emphasis"
                    style={{ backgroundColor: `${meta.color}15`, color: meta.color }}
                  >
                    <LoaderIcon size={9} />
                    {meta.label}
                  </span>
                </div>
              </div>

              <ChevronRight size={16} className="text-(--color-text-secondary)" />
            </div>
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
        <div className="flex h-16 w-16 items-center justify-center rounded-pill bg-(--color-accent)/10">
          <Loader2 size={28} className="animate-spin text-(--color-accent)" />
        </div>
        <div className="absolute -bottom-1 -right-1 flex h-7 w-7 items-center justify-center rounded-pill border border-(--color-border) bg-(--color-surface)">
          <Shield size={13} className="text-(--color-accent)" />
        </div>
      </div>
      <p className="mt-5 text-base font-strong text-(--color-text-primary)">Resolving install</p>
      <p className="mt-1 text-xs text-(--color-text-secondary)">
        Matching versions, checking the profile, and building the dependency tree...
      </p>
    </div>
  );
}

function ReviewStep({
  instance,
  preview,
  alreadyInstalled,
}: {
  instance: Instance | null;
  preview: InstallPreview;
  alreadyInstalled: boolean;
}) {
  const targetName =
    instance?.name ?? preview.plan.createdSpec?.name ?? preview.instance?.name ?? "Auto";
  const targetLoader = instance?.loader ?? preview.plan.target.loader;
  const targetVersion = instance?.version ?? preview.plan.target.version;

  return (
    <div className="space-y-4">
      {alreadyInstalled && (
        <div className="flex items-center gap-3 rounded-panel border border-warning/20 bg-warning/10 p-3.5">
          <AlertTriangle size={16} className="flex-shrink-0 text-warning" />
          <p className="text-xs font-body text-warning">
            This mod is already installed in {targetName}.
          </p>
        </div>
      )}

      <div className="rounded-panel border border-(--color-border) bg-(--color-surface-tertiary)/30 p-4">
        <div className="grid gap-3 sm:grid-cols-2">
          <div>
            <p className="text-micro font-body uppercase tracking-wider text-(--color-text-secondary)">
              Installing To
            </p>
            <p className="mt-1 text-sm font-strong text-(--color-text-primary)">{targetName}</p>
            <p className="text-caption text-(--color-text-secondary)">
              {targetVersion} · {targetLoader}
            </p>
          </div>
          <div>
            <p className="text-micro font-body uppercase tracking-wider text-(--color-text-secondary)">
              Version
            </p>
            <p className="mt-1 text-sm font-strong text-(--color-text-primary)">
              {preview.plan.rootVersion.version_number}
            </p>
            <p className="text-caption text-(--color-text-secondary)">
              {preview.plan.rootVersion.version_type}
            </p>
          </div>
        </div>
      </div>

      {preview.plan.dependencyTree.length > 0 && (
        <div className="space-y-2">
          <h4 className="flex items-center gap-1.5 text-xs font-strong uppercase tracking-wider text-(--color-text-secondary)">
            <Shield size={11} />
            Dependency Tree
          </h4>
          {preview.plan.dependencyTree.map((node) => (
            <DependencyTreeRow key={node.projectId} node={node} depth={0} />
          ))}
        </div>
      )}

      {preview.plan.dependencyTree.length === 0 && (
        <div className="rounded-panel border border-(--color-border) bg-(--color-surface-tertiary)/20 p-4 text-xs text-(--color-text-secondary)">
          No extra dependencies are required for this install.
        </div>
      )}

      {preview.plan.conflicts.length > 0 && (
        <div className="space-y-2">
          <h4 className="flex items-center gap-1.5 text-xs font-strong uppercase tracking-wider text-danger">
            <XCircle size={11} />
            Conflicts
          </h4>
          {preview.plan.conflicts.map((conflict) => (
            <div
              key={conflict.id}
              className="rounded-card border border-danger/15 bg-danger/5 px-3 py-2.5 text-xs text-danger"
            >
              {conflict.title}
            </div>
          ))}
        </div>
      )}

      {preview.plan.missing.length > 0 && (
        <div className="space-y-2">
          <h4 className="flex items-center gap-1.5 text-xs font-strong uppercase tracking-wider text-danger">
            <AlertTriangle size={11} />
            Missing
          </h4>
          {preview.plan.missing.map((name) => (
            <div
              key={name}
              className="rounded-card border border-danger/15 bg-danger/5 px-3 py-2.5 text-xs text-danger"
            >
              {name}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function DependencyTreeRow({
  node,
  depth,
}: {
  node: InstallDependencyNode;
  depth: number;
}) {
  const statusMeta: Record<
    InstallDependencyNode["status"],
    { label: string; className: string; icon: typeof Package }
  > = {
    install: {
      label: "Install",
      className: "bg-(--color-accent)/10 text-(--color-accent)",
      icon: Package,
    },
    update: {
      label: "Update",
      className: "bg-warning/10 text-warning",
      icon: AlertTriangle,
    },
    "already-installed": {
      label: "Installed",
      className: "bg-success/10 text-success",
      icon: CheckCircle2,
    },
    missing: {
      label: "Missing",
      className: "bg-danger/10 text-danger",
      icon: XCircle,
    },
    conflict: {
      label: "Conflict",
      className: "bg-danger/10 text-danger",
      icon: XCircle,
    },
    optional: {
      label: "Optional",
      className: "bg-info/10 text-info",
      icon: Info,
    },
  };

  const meta = statusMeta[node.status];
  const Icon = meta.icon;
  const dependencyLabel =
    node.dependencyType === "required"
      ? "Required"
      : node.dependencyType === "embedded"
      ? "Embedded"
      : node.dependencyType === "optional"
      ? "Optional"
      : node.dependencyType === "incompatible"
      ? "Incompatible"
      : "Root";

  return (
    <div className="space-y-2">
      <div
        className="flex items-center gap-3 rounded-card border border-(--color-border) bg-(--color-surface-tertiary)/20 px-3 py-2.5"
        style={{ marginLeft: depth * 14 }}
      >
        {node.iconUrl ? (
          <img
            src={node.iconUrl}
            alt={node.title}
            className="h-7 w-7 flex-shrink-0 rounded-control object-cover"
          />
        ) : (
          <div className="flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-control bg-(--color-surface-tertiary)">
            <CircleDot size={14} className="text-(--color-accent)" />
          </div>
        )}

        <div className="min-w-0 flex-1">
          <p className="truncate text-xs font-body text-(--color-text-primary)">{node.title}</p>
          <p className="text-micro text-(--color-text-secondary)">
            {dependencyLabel}
            {node.versionNumber ? ` · ${node.versionNumber}` : ""}
          </p>
        </div>

        <span className={`inline-flex items-center gap-1 rounded-pill px-2 py-1 text-micro font-emphasis ${meta.className}`}>
          <Icon size={10} />
          {meta.label}
        </span>
      </div>

      {node.children.map((child) => (
        <DependencyTreeRow
          key={`${node.projectId}-${child.projectId}-${child.versionId ?? "none"}`}
          node={child}
          depth={depth + 1}
        />
      ))}
    </div>
  );
}

function InstallingState({ progress, status }: { progress: number; status: string }) {
  return (
    <div className="flex flex-col items-center justify-center py-16">
      <div className="relative h-20 w-20">
        <svg className="h-full w-full -rotate-90" viewBox="0 0 80 80">
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
          <span className="text-sm font-strong text-(--color-accent)">{progress}%</span>
        </div>
      </div>
      <p className="mt-5 text-base font-strong text-(--color-text-primary)">{status}</p>
      <p className="mt-1 text-xs text-(--color-text-secondary)">Please wait...</p>
    </div>
  );
}

function DoneState({ mod, instanceName }: { mod: ModrinthProject; instanceName: string }) {
  return (
    <div className="flex flex-col items-center justify-center py-16">
      <div className="relative">
        <div className="flex h-20 w-20 items-center justify-center rounded-pill bg-success/10">
          <CheckCircle2 size={36} className="text-success" />
        </div>
        <div className="absolute -bottom-1 -right-1 flex h-8 w-8 items-center justify-center rounded-pill border border-(--color-border) bg-(--color-surface) shadow-sm">
          <Sparkles size={14} className="text-(--color-accent)" />
        </div>
      </div>
      <p className="mt-5 text-lg font-display text-(--color-text-primary)">{mod.title} installed!</p>
      <p className="mt-1 text-xs text-(--color-text-secondary)">Added to {instanceName}</p>
    </div>
  );
}

function ErrorState({ message }: { message: string }) {
  return (
    <div className="flex flex-col items-center justify-center py-16">
      <div className="flex h-16 w-16 items-center justify-center rounded-pill bg-danger/10">
        <XCircle size={28} className="text-danger" />
      </div>
      <p className="mt-5 text-base font-strong text-(--color-text-primary)">Installation failed</p>
      <p className="mt-1 max-w-xs text-center text-xs text-(--color-text-secondary)">
        {message}
      </p>
    </div>
  );
}
