import { useState } from "react";
import { observer } from "mobx-react-lite";
import { useStore } from "../stores";
import {
  X,
  Download,
  Package,
  Loader2,
  Check,
  Users,
  Crown,
  Globe,
  Box,
  Zap,
  Hammer,
  ArrowRight,
  CheckCircle2,
  XCircle,
} from "lucide-react";
import type { SharedInstanceData } from "../stores/SharingStore";

const LOADER_META: Record<string, { label: string; color: string; icon: typeof Box }> = {
  vanilla: { label: "Vanilla", color: "#22c55e", icon: Box },
  fabric: { label: "Fabric", color: "#dba678", icon: Zap },
  forge: { label: "Forge", color: "#3b82f6", icon: Hammer },
};

interface Props {
  onClose: () => void;
  onImported: () => void;
}

type Step = "input" | "loading" | "preview" | "installing" | "done" | "error";

export const ImportShareCodeModal = observer(({ onClose, onImported }: Props) => {
  const { sharing, installs } = useStore();
  const [step, setStep] = useState<Step>("input");
  const [code, setCode] = useState("");
  const [sharedData, setSharedData] = useState<SharedInstanceData | null>(null);
  const [error, setError] = useState("");
  const [installProgress, setInstallProgress] = useState(0);
  const [installStatus, setInstallStatus] = useState("");

  const handleLookup = async () => {
    if (!code.trim()) return;
    setStep("loading");
    setError("");

    try {
      const data = await sharing.lookupShareCode(code.trim());
      if (!data) {
        setStep("error");
        setError("Invalid share code. Check the code and try again.");
        return;
      }
      setSharedData(data);
      setStep("preview");
    } catch {
      setStep("error");
      setError("Failed to look up share code. Try again.");
    }
  };

  const handleImport = async () => {
    if (!sharedData) return;
    setStep("installing");
    setInstallProgress(0);
    setInstallStatus("Preparing import...");

    try {
      const progressTimer = window.setInterval(() => {
        const progress = installs.getSharedProgress(sharedData.id);
        if (progress) {
          setInstallStatus(progress.message);
          setInstallProgress(progress.percent);
        }
      }, 80);

      try {
        await installs.installSharedInstance(sharedData);
      } finally {
        window.clearInterval(progressTimer);
      }

      setInstallProgress(100);
      setInstallStatus("Import complete!");
      setStep("done");
    } catch {
      setStep("error");
      setError("Failed to import instance.");
    }
  };

  const loaderMeta = sharedData
    ? LOADER_META[sharedData.loader] ?? LOADER_META.vanilla
    : LOADER_META.vanilla;
  const LoaderIcon = loaderMeta.icon;

  return (
    <div
      className="fixed inset-0 z-[200] flex items-center justify-center wizard-backdrop"
      onClick={(e) => {
        if (e.target === e.currentTarget && step !== "installing") onClose();
      }}
    >
      <div className="absolute inset-0 bg-scrim backdrop-blur-sm" />

      <div className="relative w-full max-w-lg mx-4 wizard-modal">
        <div className="glass rounded-3xl overflow-hidden shadow-2xl shadow-black/30">
          {/* Header */}
          <div className="relative px-8 pt-8 pb-4">
            {step !== "installing" && (
              <button
                onClick={onClose}
                className="absolute top-6 right-6 w-8 h-8 rounded-pill flex items-center justify-center hover:bg-(--color-surface-tertiary) text-(--color-text-secondary) transition-colors cursor-pointer"
              >
                <X size={16} />
              </button>
            )}

            <div className="flex items-center gap-3">
              <div className="w-11 h-11 rounded-panel bg-(--color-accent)/10 flex items-center justify-center text-(--color-accent)">
                <Download size={20} />
              </div>
              <div>
                <h2 className="text-xl font-display tracking-tight text-(--color-text-primary)">
                  Import Instance
                </h2>
                <p className="text-xs text-(--color-text-secondary) mt-0.5">
                  {step === "input" && "Enter a share code to import a modpack"}
                  {step === "loading" && "Looking up share code..."}
                  {step === "preview" && "Review before importing"}
                  {step === "installing" && "Downloading mods..."}
                  {step === "done" && "Import successful!"}
                  {step === "error" && "Something went wrong"}
                </p>
              </div>
            </div>
          </div>

          {/* Content */}
          <div className="px-8 pb-2 min-h-[240px]">
            <div key={step} className="wizard-step-enter">
              {step === "input" && (
                <div className="space-y-5 py-6">
                  <div className="flex flex-col items-center">
                    <p className="text-xs text-(--color-text-secondary) font-body uppercase tracking-wider mb-4">
                      Enter Share Code
                    </p>
                    <input
                      type="text"
                      value={code}
                      onChange={(e) => setCode(e.target.value.toUpperCase())}
                      onKeyDown={(e) => e.key === "Enter" && handleLookup()}
                      placeholder="ABC123"
                      maxLength={8}
                      autoFocus
                      className="text-center text-3xl font-display tracking-[0.25em] text-(--color-text-primary) bg-(--color-surface-tertiary)/50 border border-(--color-border) rounded-panel px-8 py-5 w-full max-w-xs placeholder:text-(--color-text-secondary)/30 focus:outline-none focus:ring-2 focus:ring-(--color-accent)/50 focus:border-transparent transition-all font-mono"
                    />
                  </div>
                  <p className="text-xs text-(--color-text-secondary) text-center">
                    Ask your friend for their instance share code
                  </p>
                </div>
              )}

              {step === "loading" && (
                <div className="flex flex-col items-center justify-center py-16">
                  <Loader2 size={32} className="text-(--color-accent) animate-spin" />
                  <p className="text-sm font-emphasis text-(--color-text-primary) mt-4">
                    Looking up share code...
                  </p>
                </div>
              )}

              {step === "preview" && sharedData && (
                <div className="space-y-4 py-4">
                  {/* Instance preview */}
                  <div
                    className="rounded-panel border p-5 space-y-4"
                    style={{
                      borderColor: sharedData.icon_color + "30",
                      backgroundColor: sharedData.icon_color + "08",
                    }}
                  >
                    <div className="flex items-center gap-4">
                      <div
                        className="w-14 h-14 rounded-panel flex items-center justify-center text-xl font-display"
                        style={{
                          backgroundColor: sharedData.icon_color + "18",
                          color: sharedData.icon_color,
                        }}
                      >
                        {sharedData.name[0]?.toUpperCase() ?? "?"}
                      </div>
                      <div>
                        <h3 className="text-lg font-display text-(--color-text-primary)">
                          {sharedData.name}
                        </h3>
                        <div className="flex items-center gap-2 mt-1">
                          <span className="text-xs font-body text-(--color-text-secondary) bg-(--color-surface-tertiary)/60 px-2.5 py-0.5 rounded-control">
                            {sharedData.mc_version}
                          </span>
                          <span
                            className="text-micro px-2.5 py-0.5 rounded-control font-strong flex items-center gap-1"
                            style={{
                              backgroundColor: loaderMeta.color + "15",
                              color: loaderMeta.color,
                            }}
                          >
                            <LoaderIcon size={10} />
                            {loaderMeta.label}
                          </span>
                        </div>
                      </div>
                    </div>

                    {/* Owner */}
                    <div className="flex items-center gap-2 text-xs text-(--color-text-secondary)">
                      <Crown size={12} className="text-warning" />
                      <span>
                        Created by{" "}
                        <span className="font-emphasis text-(--color-text-primary)">
                          {sharedData.owner.mc_username}
                        </span>
                      </span>
                    </div>

                    {sharedData.is_collaborative && (
                      <div className="flex items-center gap-2 text-xs text-collab">
                        <Users size={12} />
                        <span className="font-emphasis">Collaborative mode enabled</span>
                      </div>
                    )}
                  </div>

                  {/* Mods list */}
                  {sharedData.mods.length > 0 && (
                    <div className="space-y-2">
                      <h4 className="text-xs font-strong text-(--color-text-secondary) uppercase tracking-wider">
                        Mods to install ({sharedData.mods.length})
                      </h4>
                      <div className="max-h-[200px] overflow-y-auto rounded-panel border border-(--color-border) divide-y divide-(--color-border)/50">
                        {sharedData.mods.map((mod) => (
                          <div
                            key={mod.project_id}
                            className="flex items-center gap-3 p-3"
                          >
                            {mod.icon_url ? (
                              <img
                                src={mod.icon_url}
                                className="w-8 h-8 rounded-control object-cover flex-shrink-0"
                              />
                            ) : (
                              <div className="w-8 h-8 rounded-control bg-(--color-surface-tertiary) flex items-center justify-center flex-shrink-0">
                                <Package size={14} className="text-(--color-text-secondary)" />
                              </div>
                            )}
                            <span className="text-xs font-body text-(--color-text-primary) truncate">
                              {mod.title}
                            </span>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              )}

              {step === "installing" && (
                <div className="flex flex-col items-center justify-center py-12">
                  <div className="relative w-20 h-20">
                    <svg className="w-full h-full -rotate-90" viewBox="0 0 80 80">
                      <circle
                        cx="40" cy="40" r="35"
                        fill="none" stroke="var(--color-surface-tertiary)" strokeWidth="4"
                      />
                      <circle
                        cx="40" cy="40" r="35"
                        fill="none" stroke="var(--color-accent)" strokeWidth="4"
                        strokeLinecap="round"
                        strokeDasharray={`${2 * Math.PI * 35}`}
                        strokeDashoffset={`${2 * Math.PI * 35 * (1 - installProgress / 100)}`}
                        className="transition-all duration-300"
                      />
                    </svg>
                    <div className="absolute inset-0 flex items-center justify-center">
                      <span className="text-sm font-strong text-(--color-accent)">
                        {installProgress}%
                      </span>
                    </div>
                  </div>
                  <p className="text-sm font-strong text-(--color-text-primary) mt-5">
                    {installStatus}
                  </p>
                  <p className="text-xs text-(--color-text-secondary) mt-1">
                    Please wait...
                  </p>
                </div>
              )}

              {step === "done" && (
                <div className="flex flex-col items-center justify-center py-12">
                  <CheckCircle2 size={48} className="text-success" />
                  <p className="text-lg font-display text-(--color-text-primary) mt-4">
                    Instance imported!
                  </p>
                  <p className="text-xs text-(--color-text-secondary) mt-1">
                    {sharedData?.name} is ready to play
                  </p>
                </div>
              )}

              {step === "error" && (
                <div className="flex flex-col items-center justify-center py-12">
                  <XCircle size={40} className="text-danger" />
                  <p className="text-sm font-strong text-(--color-text-primary) mt-4">
                    Import failed
                  </p>
                  <p className="text-xs text-(--color-text-secondary) mt-1 text-center max-w-xs">
                    {error}
                  </p>
                </div>
              )}
            </div>
          </div>

          {/* Footer */}
          <div className="px-8 pb-8 pt-3 flex items-center justify-between">
            {step === "input" && (
              <>
                <button
                  onClick={onClose}
                  className="px-5 py-2.5 rounded-card text-sm text-(--color-text-secondary) hover:bg-(--color-surface-tertiary) transition-colors cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  onClick={handleLookup}
                  disabled={!code.trim()}
                  className="flex items-center gap-2 px-6 py-2.5 rounded-card bg-(--color-accent) hover:bg-(--color-accent-hover) text-fg-on-accent text-sm font-emphasis transition-all cursor-pointer shadow-md shadow-(--color-accent)/20 disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  Look Up
                  <ArrowRight size={15} />
                </button>
              </>
            )}
            {step === "preview" && (
              <>
                <button
                  onClick={() => {
                    setStep("input");
                    setSharedData(null);
                  }}
                  className="px-5 py-2.5 rounded-card text-sm text-(--color-text-secondary) hover:bg-(--color-surface-tertiary) transition-colors cursor-pointer"
                >
                  Back
                </button>
                <button
                  onClick={handleImport}
                  className="flex items-center gap-2 px-6 py-2.5 rounded-card bg-(--color-accent) hover:bg-(--color-accent-hover) text-fg-on-accent text-sm font-emphasis transition-all cursor-pointer shadow-md shadow-(--color-accent)/20"
                >
                  <Download size={15} />
                  Import Instance
                </button>
              </>
            )}
            {step === "done" && (
              <>
                <div />
                <button
                  onClick={() => {
                    onImported();
                    onClose();
                  }}
                  className="flex items-center gap-2 px-6 py-2.5 rounded-card bg-(--color-accent) hover:bg-(--color-accent-hover) text-fg-on-accent text-sm font-emphasis transition-all cursor-pointer shadow-md shadow-(--color-accent)/20"
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
                    setStep("input");
                    setError("");
                  }}
                  className="px-5 py-2.5 rounded-card text-sm text-(--color-text-secondary) hover:bg-(--color-surface-tertiary) transition-colors cursor-pointer"
                >
                  Try Again
                </button>
                <button
                  onClick={onClose}
                  className="px-5 py-2.5 rounded-card text-sm text-(--color-text-secondary) hover:bg-(--color-surface-tertiary) transition-colors cursor-pointer"
                >
                  Close
                </button>
              </>
            )}
            {(step === "loading" || step === "installing") && (
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
