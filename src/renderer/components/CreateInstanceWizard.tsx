import { useState, useEffect, useMemo } from "react";
import {
  X,
  ChevronRight,
  ChevronLeft,
  Check,
  Search,
  Box,
  Layers,
  Hammer,
  Zap,
  Sparkles,
} from "lucide-react";
import {
  getMinecraftVersions,
  type MinecraftVersion,
} from "../api/minecraft";

const ICON_COLORS = [
  { name: "Violet", value: "#8b5cf6" },
  { name: "Blue", value: "#3b82f6" },
  { name: "Cyan", value: "#06b6d4" },
  { name: "Green", value: "#10b981" },
  { name: "Amber", value: "#f59e0b" },
  { name: "Rose", value: "#f43f5e" },
  { name: "Slate", value: "#64748b" },
];

const STEPS = ["Name", "Version", "Loader", "Review"];

type Loader = "vanilla" | "fabric" | "forge";

const LOADERS: {
  id: Loader;
  name: string;
  subtitle: string;
  description: string;
  icon: typeof Box;
  color: string;
}[] = [
  {
    id: "vanilla",
    name: "Vanilla",
    subtitle: "Pure Minecraft",
    description: "Play without any modifications. The classic experience.",
    icon: Box,
    color: "#22c55e",
  },
  {
    id: "fabric",
    name: "Fabric",
    subtitle: "Lightweight & Modern",
    description:
      "A lightweight, modular modding toolchain. Fast updates and great performance.",
    icon: Zap,
    color: "#dba678",
  },
  {
    id: "forge",
    name: "Forge",
    subtitle: "The Classic Choice",
    description:
      "The most established modding platform. Massive mod library and community.",
    icon: Hammer,
    color: "#3b82f6",
  },
];

interface Props {
  onClose: () => void;
  onCreate: (data: {
    name: string;
    version: string;
    loader: Loader;
    iconColor: string;
  }) => void;
}

export function CreateInstanceWizard({ onClose, onCreate }: Props) {
  const [step, setStep] = useState(0);

  // Step 1
  const [name, setName] = useState("");
  const [iconColor, setIconColor] = useState(ICON_COLORS[0].value);

  // Step 2
  const [selectedVersion, setSelectedVersion] = useState("");
  const [versions, setVersions] = useState<MinecraftVersion[]>([]);
  const [versionFilter, setVersionFilter] = useState<"release" | "snapshot">(
    "release"
  );
  const [versionSearch, setVersionSearch] = useState("");
  const [loadingVersions, setLoadingVersions] = useState(true);
  const [versionsError, setVersionsError] = useState<string | null>(null);

  // Step 3
  const [loader, setLoader] = useState<Loader>("fabric");

  const loadVersions = () => {
    setLoadingVersions(true);
    setVersionsError(null);
    getMinecraftVersions()
      .then((data) => {
        setVersions(data.versions);
        setSelectedVersion(data.latest.release);
        setLoadingVersions(false);
      })
      .catch(() => {
        setLoadingVersions(false);
        setVersionsError("Failed to load versions. Check your connection.");
      });
  };

  useEffect(() => {
    loadVersions();
  }, []);

  const filteredVersions = useMemo(() => {
    let filtered = versions.filter((v) => v.type === versionFilter);
    if (versionSearch) {
      filtered = filtered.filter((v) =>
        v.id.toLowerCase().includes(versionSearch.toLowerCase())
      );
    }
    return filtered;
  }, [versions, versionFilter, versionSearch]);

  const canProceed = step === 0
    ? name.trim().length > 0
    : step === 1
    ? selectedVersion.length > 0
    : true;

  const next = () => {
    if (step < 3) {
      setStep(step + 1);
    } else {
      onCreate({
        name: name.trim(),
        version: selectedVersion,
        loader,
        iconColor,
      });
    }
  };

  const back = () => {
    if (step > 0) setStep(step - 1);
  };

  const formatDate = (dateStr: string) =>
    new Date(dateStr).toLocaleDateString(undefined, {
      year: "numeric",
      month: "short",
      day: "numeric",
    });

  return (
    <div
      className="fixed inset-0 z-[100] flex items-center justify-center wizard-backdrop"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" />

      <div className="relative w-full max-w-2xl mx-4 wizard-modal">
        <div className="glass rounded-2xl overflow-hidden shadow-2xl shadow-black/30">
          {/* Header */}
          <div className="relative px-8 pt-8 pb-6">
            <button
              onClick={onClose}
              className="absolute top-6 right-6 w-8 h-8 rounded-full flex items-center justify-center hover:bg-(--color-surface-tertiary) text-(--color-text-secondary) transition-colors cursor-pointer"
            >
              <X size={16} />
            </button>

            <h2 className="text-3xl font-black tracking-tight text-(--color-text-primary)">
              Create Instance
            </h2>

            {/* Step indicator */}
            <div className="flex items-center mt-6">
              {STEPS.map((label, i) => (
                <div
                  key={i}
                  className="flex items-center flex-1 last:flex-initial"
                >
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => i < step && setStep(i)}
                      className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold transition-all duration-300 ${
                        i < step
                          ? "bg-(--color-accent) text-white cursor-pointer"
                          : i === step
                          ? "bg-(--color-accent) text-white shadow-lg shadow-(--color-accent)/30"
                          : "bg-(--color-surface-tertiary) text-(--color-text-secondary)"
                      }`}
                      disabled={i >= step}
                    >
                      {i < step ? <Check size={14} /> : i + 1}
                    </button>
                    <span
                      className={`text-xs font-medium hidden sm:block transition-colors ${
                        i <= step
                          ? "text-(--color-text-primary)"
                          : "text-(--color-text-secondary)"
                      }`}
                    >
                      {label}
                    </span>
                  </div>
                  {i < STEPS.length - 1 && (
                    <div className="flex-1 mx-3">
                      <div className="h-px bg-(--color-border) relative overflow-hidden">
                        <div
                          className="absolute inset-y-0 left-0 bg-(--color-accent) transition-all duration-500"
                          style={{ width: i < step ? "100%" : "0%" }}
                        />
                      </div>
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>

          {/* Content */}
          <div className="px-8 pb-4 min-h-[360px]">
            <div key={step} className="wizard-step-enter">
              {step === 0 && renderNameStep()}
              {step === 1 && renderVersionStep()}
              {step === 2 && renderLoaderStep()}
              {step === 3 && renderReviewStep()}
            </div>
          </div>

          {/* Footer */}
          <div className="px-8 pb-8 flex items-center justify-between">
            <button
              onClick={step === 0 ? onClose : back}
              className="flex items-center gap-2 px-5 py-2.5 rounded-full text-sm text-(--color-text-secondary) hover:bg-(--color-surface-tertiary) transition-colors cursor-pointer"
            >
              {step === 0 ? (
                "Cancel"
              ) : (
                <>
                  <ChevronLeft size={16} />
                  Back
                </>
              )}
            </button>
            <button
              onClick={next}
              disabled={!canProceed}
              className="flex items-center gap-2 px-6 py-2.5 rounded-full bg-(--color-accent) hover:bg-(--color-accent-hover) text-white text-sm font-medium transition-all cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed shadow-lg shadow-(--color-accent)/25"
            >
              {step === 3 ? (
                <>
                  <Sparkles size={16} />
                  Create Instance
                </>
              ) : (
                <>
                  Next
                  <ChevronRight size={16} />
                </>
              )}
            </button>
          </div>
        </div>
      </div>
    </div>
  );

  /* ── Step renderers ────────────────────────────── */

  function renderNameStep() {
    const letter = name.trim() ? name.trim()[0].toUpperCase() : "?";
    return (
      <div className="space-y-8">
        <p className="text-base text-(--color-text-secondary)">
          Give your instance a name and choose its appearance.
        </p>

        <div className="flex flex-col items-center gap-6">
          {/* Icon preview */}
          <div
            className="w-20 h-20 rounded-2xl flex items-center justify-center text-3xl font-bold transition-all duration-300 shadow-lg"
            style={{
              backgroundColor: iconColor + "20",
              color: iconColor,
              boxShadow: `0 8px 32px ${iconColor}25`,
            }}
          >
            {letter}
          </div>

          {/* Name input */}
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && canProceed && next()}
            placeholder="My Awesome Instance..."
            autoFocus
            className="w-full max-w-md text-center px-6 py-4 rounded-2xl bg-(--color-surface-tertiary)/50 border border-(--color-border) text-lg text-(--color-text-primary) placeholder:text-(--color-text-secondary)/50 focus:outline-none focus:ring-2 focus:ring-(--color-accent)/50 focus:border-transparent transition-all"
          />

          {/* Color picker */}
          <div className="space-y-2">
            <p className="text-xs text-(--color-text-secondary) text-center font-medium">
              Accent Color
            </p>
            <div className="flex items-center gap-3">
              {ICON_COLORS.map((c) => (
                <button
                  key={c.value}
                  onClick={() => setIconColor(c.value)}
                  className="relative w-8 h-8 rounded-full transition-all duration-200 cursor-pointer hover:scale-110"
                  style={{ backgroundColor: c.value }}
                  title={c.name}
                >
                  {iconColor === c.value && (
                    <div
                      className="absolute inset-0 rounded-full flex items-center justify-center"
                      style={{
                        boxShadow: `0 0 0 2px var(--color-surface), 0 0 0 4px ${c.value}`,
                      }}
                    >
                      <Check size={14} className="text-white drop-shadow" />
                    </div>
                  )}
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>
    );
  }

  function renderVersionStep() {
    return (
      <div className="space-y-4">
        <p className="text-base text-(--color-text-secondary)">
          Choose which version of Minecraft to play.
        </p>

        {/* Filter tabs + search */}
        <div className="flex items-center gap-3">
          <div className="flex gap-1 bg-(--color-surface-tertiary)/50 rounded-xl p-1">
            {(["release", "snapshot"] as const).map((f) => (
              <button
                key={f}
                onClick={() => setVersionFilter(f)}
                className={`px-4 py-1.5 rounded-lg text-xs font-medium capitalize transition-all cursor-pointer ${
                  versionFilter === f
                    ? "bg-(--color-accent) text-white shadow-sm"
                    : "text-(--color-text-secondary) hover:text-(--color-text-primary)"
                }`}
              >
                {f === "release" ? "Releases" : "Snapshots"}
              </button>
            ))}
          </div>
          <div className="flex-1 relative">
            <Search
              size={14}
              className="absolute left-3 top-1/2 -translate-y-1/2 text-(--color-text-secondary)"
            />
            <input
              type="text"
              value={versionSearch}
              onChange={(e) => setVersionSearch(e.target.value)}
              placeholder="Search versions..."
              className="w-full pl-9 pr-4 py-2 rounded-xl bg-(--color-surface-tertiary)/50 border border-(--color-border) text-xs text-(--color-text-primary) placeholder:text-(--color-text-secondary)/50 focus:outline-none focus:ring-2 focus:ring-(--color-accent)/50 transition-all"
            />
          </div>
        </div>

        {/* Version list */}
        <div className="max-h-[240px] overflow-y-auto rounded-2xl border border-(--color-border) bg-(--color-surface-tertiary)/20">
          {loadingVersions ? (
            <div className="flex items-center justify-center py-12">
              <div className="w-6 h-6 border-2 border-(--color-accent) border-t-transparent rounded-full animate-spin" />
            </div>
          ) : versionsError ? (
            <div className="flex flex-col items-center justify-center py-12 gap-3">
              <p className="text-sm text-(--color-text-secondary)">
                {versionsError}
              </p>
              <button
                onClick={loadVersions}
                className="px-4 py-1.5 rounded-lg text-xs font-medium bg-(--color-accent) text-white hover:bg-(--color-accent-hover) transition-colors cursor-pointer"
              >
                Retry
              </button>
            </div>
          ) : filteredVersions.length === 0 ? (
            <div className="text-center py-12 text-sm text-(--color-text-secondary)">
              No versions found
            </div>
          ) : (
            filteredVersions.map((v) => {
              const isSelected = selectedVersion === v.id;
              return (
                <button
                  key={v.id}
                  onClick={() => setSelectedVersion(v.id)}
                  className={`w-full flex items-center gap-3 px-4 py-3 text-left transition-all cursor-pointer border-b border-(--color-border)/50 last:border-b-0 ${
                    isSelected
                      ? "bg-(--color-accent)/10"
                      : "hover:bg-(--color-surface-tertiary)/50"
                  }`}
                >
                  <div
                    className={`w-5 h-5 rounded-full border-2 flex items-center justify-center flex-shrink-0 transition-all ${
                      isSelected
                        ? "border-(--color-accent) bg-(--color-accent)"
                        : "border-(--color-border)"
                    }`}
                  >
                    {isSelected && (
                      <Check size={12} className="text-white" />
                    )}
                  </div>
                  <div className="flex-1 min-w-0">
                    <span
                      className={`text-sm font-medium ${
                        isSelected
                          ? "text-(--color-accent)"
                          : "text-(--color-text-primary)"
                      }`}
                    >
                      {v.id}
                    </span>
                  </div>
                  <span className="text-[11px] text-(--color-text-secondary) flex-shrink-0">
                    {formatDate(v.releaseTime)}
                  </span>
                </button>
              );
            })
          )}
        </div>
      </div>
    );
  }

  function renderLoaderStep() {
    return (
      <div className="space-y-4">
        <p className="text-base text-(--color-text-secondary)">
          Select a mod loader for your instance.
        </p>

        <div className="grid grid-cols-3 gap-4">
          {LOADERS.map((l) => {
            const isSelected = loader === l.id;
            const Icon = l.icon;
            return (
              <button
                key={l.id}
                onClick={() => setLoader(l.id)}
                className={`relative flex flex-col items-center text-center p-6 rounded-2xl border-2 transition-all duration-300 cursor-pointer group ${
                  isSelected
                    ? "border-transparent shadow-xl"
                    : "border-(--color-border) hover:border-(--color-text-secondary)/30 hover:shadow-lg"
                }`}
                style={
                  isSelected
                    ? {
                        borderColor: l.color,
                        backgroundColor: l.color + "10",
                        boxShadow: `0 8px 32px ${l.color}20`,
                      }
                    : undefined
                }
              >
                {isSelected && (
                  <div
                    className="absolute top-3 right-3 w-5 h-5 rounded-full flex items-center justify-center"
                    style={{ backgroundColor: l.color }}
                  >
                    <Check size={12} className="text-white" />
                  </div>
                )}

                <div
                  className="w-14 h-14 rounded-2xl flex items-center justify-center mb-4 transition-transform duration-300 group-hover:scale-110"
                  style={{
                    backgroundColor: l.color + "15",
                    color: l.color,
                  }}
                >
                  <Icon size={24} />
                </div>

                <h3
                  className="text-base font-bold tracking-tight transition-colors"
                  style={isSelected ? { color: l.color } : undefined}
                >
                  <span className={isSelected ? "" : "text-(--color-text-primary)"}>
                    {l.name}
                  </span>
                </h3>
                <p
                  className="text-[11px] font-medium mt-0.5"
                  style={{ color: l.color }}
                >
                  {l.subtitle}
                </p>
                <p className="text-[11px] text-(--color-text-secondary) mt-2 leading-relaxed">
                  {l.description}
                </p>
              </button>
            );
          })}
        </div>
      </div>
    );
  }

  function renderReviewStep() {
    const letter = name.trim() ? name.trim()[0].toUpperCase() : "?";
    const selectedLoader = LOADERS.find((l) => l.id === loader)!;
    const LoaderIcon = selectedLoader.icon;

    return (
      <div className="space-y-6">
        <p className="text-base text-(--color-text-secondary)">
          Review your settings and create your instance.
        </p>

        <div
          className="rounded-2xl border-2 p-6 space-y-5 transition-all"
          style={{
            borderColor: iconColor + "40",
            backgroundColor: iconColor + "08",
          }}
        >
          {/* Instance header */}
          <div className="flex items-center gap-4">
            <div
              className="w-16 h-16 rounded-2xl flex items-center justify-center text-2xl font-bold shadow-lg"
              style={{
                backgroundColor: iconColor + "20",
                color: iconColor,
                boxShadow: `0 8px 32px ${iconColor}20`,
              }}
            >
              {letter}
            </div>
            <div>
              <h3 className="text-2xl font-black text-(--color-text-primary) tracking-tight">
                {name}
              </h3>
              <p className="text-sm text-(--color-text-secondary) mt-0.5">
                Ready to create
              </p>
            </div>
          </div>

          <div className="h-px bg-(--color-border)" />

          {/* Details */}
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1">
              <p className="text-[11px] font-medium text-(--color-text-secondary) uppercase tracking-wider">
                Minecraft Version
              </p>
              <p className="text-sm font-semibold text-(--color-text-primary)">
                {selectedVersion}
              </p>
            </div>
            <div className="space-y-1">
              <p className="text-[11px] font-medium text-(--color-text-secondary) uppercase tracking-wider">
                Mod Loader
              </p>
              <div className="flex items-center gap-2">
                <div
                  className="w-5 h-5 rounded-md flex items-center justify-center"
                  style={{
                    backgroundColor: selectedLoader.color + "20",
                    color: selectedLoader.color,
                  }}
                >
                  <LoaderIcon size={12} />
                </div>
                <span className="text-sm font-semibold text-(--color-text-primary)">
                  {selectedLoader.name}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    );
  }
}
