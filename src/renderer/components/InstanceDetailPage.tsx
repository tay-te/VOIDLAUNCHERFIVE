import { useState } from "react";
import { observer } from "mobx-react-lite";
import { useStore } from "../stores";
import {
  ArrowLeft,
  Play,
  Trash2,
  Pencil,
  Box,
  Zap,
  Hammer,
  Layers,
  Clock,
  Calendar,
  FolderOpen,
  Package,
  Settings,
  Plus,
  AlertTriangle,
  Check,
  X,
  Sparkles,
} from "lucide-react";
import type { Instance, InstalledMod } from "../stores/InstanceStore";

const LOADER_META: Record<
  string,
  { label: string; color: string; icon: typeof Box }
> = {
  vanilla: { label: "Vanilla", color: "#22c55e", icon: Box },
  fabric: { label: "Fabric", color: "#dba678", icon: Zap },
  forge: { label: "Forge", color: "#3b82f6", icon: Hammer },
};

function timeAgo(dateStr: string): string {
  const seconds = Math.floor(
    (Date.now() - new Date(dateStr).getTime()) / 1000
  );
  if (seconds < 60) return "just now";
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `${days}d ago`;
  const months = Math.floor(days / 30);
  return `${months}mo ago`;
}

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString(undefined, {
    year: "numeric",
    month: "long",
    day: "numeric",
  });
}

interface Props {
  instanceId: string;
  onBack: () => void;
  onBrowseMods: () => void;
}

export const InstanceDetailPage = observer(
  ({ instanceId, onBack, onBrowseMods }: Props) => {
    const { instances: store } = useStore();
    const instance = store.instances.find((i) => i.id === instanceId);
    const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
    const [isEditingName, setIsEditingName] = useState(false);
    const [editName, setEditName] = useState("");
    const [removingMod, setRemovingMod] = useState<string | null>(null);

    if (!instance) {
      return (
        <div className="p-8">
          <button
            onClick={onBack}
            className="flex items-center gap-2 text-sm text-(--color-text-secondary) hover:text-(--color-text-primary) transition-colors cursor-pointer"
          >
            <ArrowLeft size={16} />
            Back to Instances
          </button>
          <div className="flex flex-col items-center justify-center py-24 text-(--color-text-secondary)">
            <p className="text-sm">Instance not found</p>
          </div>
        </div>
      );
    }

    const loaderMeta = LOADER_META[instance.loader] ?? LOADER_META.vanilla;
    const LoaderIcon = loaderMeta.icon;
    const letter = instance.name[0]?.toUpperCase() ?? "?";
    const color = instance.iconColor;
    const installedMods = instance.installedMods ?? [];

    const handleDelete = () => {
      store.remove(instance.id);
      onBack();
    };

    const handleStartEdit = () => {
      setEditName(instance.name);
      setIsEditingName(true);
    };

    const handleSaveEdit = () => {
      const trimmed = editName.trim();
      if (trimmed && trimmed !== instance.name) {
        store.update(instance.id, { name: trimmed });
      }
      setIsEditingName(false);
    };

    const handleRemoveMod = async (mod: InstalledMod) => {
      setRemovingMod(mod.projectId);
      try {
        await window.electronAPI.removeModFile({
          instanceId: instance.id,
          filename: mod.filename,
        });
        store.removeMod(instance.id, mod.projectId);
      } catch {
        // File might already be gone, still remove from store
        store.removeMod(instance.id, mod.projectId);
      }
      setRemovingMod(null);
    };

    const handleOpenFolder = () => {
      window.electronAPI.openInstanceFolder(instance.id);
    };

    return (
      <div className="p-8 space-y-6 wizard-step-enter">
        {/* Back button */}
        <button
          onClick={onBack}
          className="flex items-center gap-2 text-sm text-(--color-text-secondary) hover:text-(--color-text-primary) transition-colors cursor-pointer group"
        >
          <ArrowLeft
            size={16}
            className="transition-transform group-hover:-translate-x-0.5"
          />
          Back to Instances
        </button>

        {/* Hero card */}
        <div
          className="rounded-2xl overflow-hidden"
          style={{
            backgroundColor: `${color}0a`,
            border: `1px solid ${color}20`,
          }}
        >
          <div className="p-8">
            <div className="flex items-start gap-6">
              {/* Icon */}
              <div
                className="w-20 h-20 rounded-2xl flex items-center justify-center text-3xl font-bold flex-shrink-0 shadow-lg"
                style={{
                  backgroundColor: color + "20",
                  color: color,
                  boxShadow: `0 8px 32px ${color}20`,
                }}
              >
                {letter}
              </div>

              {/* Info */}
              <div className="flex-1 min-w-0">
                {isEditingName ? (
                  <div className="flex items-center gap-2">
                    <input
                      type="text"
                      value={editName}
                      onChange={(e) => setEditName(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === "Enter") handleSaveEdit();
                        if (e.key === "Escape") setIsEditingName(false);
                      }}
                      autoFocus
                      className="text-3xl font-black tracking-tight text-(--color-text-primary) bg-transparent border-b-2 focus:outline-none px-0 py-1 w-full max-w-md"
                      style={{ borderColor: color }}
                    />
                    <button
                      onClick={handleSaveEdit}
                      className="w-8 h-8 rounded-full flex items-center justify-center text-white cursor-pointer"
                      style={{ backgroundColor: color }}
                    >
                      <Check size={14} />
                    </button>
                    <button
                      onClick={() => setIsEditingName(false)}
                      className="w-8 h-8 rounded-full flex items-center justify-center hover:bg-(--color-surface-tertiary) text-(--color-text-secondary) cursor-pointer"
                    >
                      <X size={14} />
                    </button>
                  </div>
                ) : (
                  <div className="flex items-center gap-3">
                    <h1 className="text-3xl font-black tracking-tight text-(--color-text-primary) truncate">
                      {instance.name}
                    </h1>
                    <button
                      onClick={handleStartEdit}
                      className="w-7 h-7 rounded-full flex items-center justify-center hover:bg-(--color-surface-tertiary) text-(--color-text-secondary) transition-colors cursor-pointer flex-shrink-0 opacity-0 group-hover:opacity-100"
                      style={{ opacity: undefined }}
                    >
                      <Pencil size={12} />
                    </button>
                  </div>
                )}

                <div className="flex items-center gap-2.5 mt-2 flex-wrap">
                  <span className="text-sm font-medium text-(--color-text-secondary) bg-(--color-surface-tertiary)/60 px-3 py-1 rounded-lg">
                    {instance.version}
                  </span>
                  <span
                    className="text-xs px-3 py-1 rounded-lg font-semibold flex items-center gap-1.5"
                    style={{
                      backgroundColor: loaderMeta.color + "15",
                      color: loaderMeta.color,
                    }}
                  >
                    <LoaderIcon size={12} />
                    {loaderMeta.label}
                  </span>
                </div>

                {/* Actions */}
                <div className="flex items-center gap-3 mt-5">
                  <button
                    onClick={() => store.launch(instance.id)}
                    className="flex items-center gap-2.5 px-8 py-3.5 rounded-full text-white text-base font-bold transition-all cursor-pointer hover:shadow-xl hover:-translate-y-0.5"
                    style={{
                      backgroundColor: color,
                      boxShadow: `0 4px 20px ${color}30`,
                    }}
                  >
                    <Play size={15} fill="currentColor" />
                    Launch
                  </button>
                  <button
                    onClick={handleStartEdit}
                    className="flex items-center gap-2 px-5 py-3.5 rounded-full glass-subtle text-base text-(--color-text-secondary) hover:text-(--color-text-primary) transition-colors cursor-pointer"
                  >
                    <Pencil size={14} />
                    Edit
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Stats row */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <StatCard
            icon={Calendar}
            label="Created"
            value={timeAgo(instance.dateCreated)}
            sub={formatDate(instance.dateCreated)}
            color={color}
          />
          <StatCard
            icon={Clock}
            label="Last Played"
            value={instance.lastPlayed ? timeAgo(instance.lastPlayed) : "Never"}
            sub={
              instance.lastPlayed ? formatDate(instance.lastPlayed) : "Not yet"
            }
            color={color}
          />
          <StatCard
            icon={Package}
            label="Mods"
            value={String(installedMods.length)}
            sub={installedMods.length === 1 ? "mod installed" : "mods installed"}
            color={color}
          />
          <StatCard
            icon={LoaderIcon}
            label="Loader"
            value={loaderMeta.label}
            sub={instance.version}
            color={loaderMeta.color}
          />
        </div>

        {/* Installed Mods section */}
        <section className="space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-xl font-black text-(--color-text-primary) tracking-tight">
              Installed Mods
            </h2>
            <button
              onClick={onBrowseMods}
              className="flex items-center gap-1.5 px-4 py-2 rounded-full text-xs font-medium transition-all cursor-pointer text-white hover:opacity-90"
              style={{
                backgroundColor: color,
              }}
            >
              <Plus size={13} />
              Add Mods
            </button>
          </div>

          {installedMods.length === 0 ? (
            <div className="glass-subtle rounded-2xl">
              <div className="flex flex-col items-center justify-center py-16 text-(--color-text-secondary)">
                <div
                  className="w-14 h-14 rounded-2xl flex items-center justify-center mb-4"
                  style={{ backgroundColor: color + "10", color: color }}
                >
                  <Package size={24} strokeWidth={1.5} />
                </div>
                <p className="text-sm font-medium text-(--color-text-primary)">
                  No mods installed
                </p>
                <p className="text-xs mt-1">
                  Browse and install mods to enhance your experience
                </p>
                <button
                  onClick={onBrowseMods}
                  className="mt-4 flex items-center gap-2 px-5 py-2 rounded-full text-xs font-semibold text-white transition-all cursor-pointer hover:opacity-90"
                  style={{
                    backgroundColor: color,
                  }}
                >
                  <Sparkles size={13} />
                  Browse Mods
                </button>
              </div>
            </div>
          ) : (
            <div className="glass-subtle rounded-2xl divide-y divide-(--color-border)/50">
              {installedMods.map((mod) => (
                <div
                  key={mod.projectId}
                  className="flex items-center gap-4 p-4 group"
                >
                  {mod.iconUrl ? (
                    <img
                      src={mod.iconUrl}
                      alt={mod.title}
                      className="w-10 h-10 rounded-xl object-cover flex-shrink-0 shadow-sm ring-1 ring-black/5"
                    />
                  ) : (
                    <div
                      className="w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0"
                      style={{ backgroundColor: color + "10", color: color }}
                    >
                      <Package size={16} />
                    </div>
                  )}
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-semibold text-(--color-text-primary) truncate">
                      {mod.title}
                    </p>
                    <p className="text-[11px] text-(--color-text-secondary) truncate mt-0.5">
                      {mod.filename}
                    </p>
                  </div>
                  {removingMod === mod.projectId ? (
                    <div className="flex items-center gap-2">
                      <span className="text-[11px] text-red-500 font-medium">
                        Remove?
                      </span>
                      <button
                        onClick={() => setRemovingMod(null)}
                        className="px-3 py-1 rounded-full text-[11px] text-(--color-text-secondary) hover:bg-(--color-surface-tertiary) transition-colors cursor-pointer"
                      >
                        No
                      </button>
                      <button
                        onClick={() => handleRemoveMod(mod)}
                        className="px-3 py-1 rounded-full bg-red-500 text-white text-[11px] font-medium hover:bg-red-600 transition-colors cursor-pointer"
                      >
                        Yes
                      </button>
                    </div>
                  ) : (
                    <button
                      onClick={() => setRemovingMod(mod.projectId)}
                      className="w-8 h-8 rounded-full flex items-center justify-center hover:bg-red-500/10 text-(--color-text-secondary) hover:text-red-500 transition-all cursor-pointer opacity-0 group-hover:opacity-100"
                    >
                      <Trash2 size={13} />
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}
        </section>

        {/* Game Settings section */}
        <section className="space-y-4">
          <h2 className="text-xl font-black text-(--color-text-primary) tracking-tight">
            Instance Settings
          </h2>

          <div className="glass-subtle rounded-2xl divide-y divide-(--color-border)/50">
            <SettingRow
              icon={FolderOpen}
              label="Game Directory"
              description="Where this instance's files are stored"
              color={color}
            >
              <button
                onClick={handleOpenFolder}
                className="px-4 py-1.5 rounded-lg text-xs font-medium bg-(--color-surface-tertiary)/80 text-(--color-text-secondary) hover:text-(--color-text-primary) transition-colors cursor-pointer"
              >
                Open Folder
              </button>
            </SettingRow>

            <MemorySlider
              memoryMb={instance.memoryMb ?? 4096}
              color={color}
              onChange={(mb) => store.update(instance.id, { memoryMb: mb })}
            />

            <SettingRow
              icon={Layers}
              label="Game Version"
              description={`Minecraft ${instance.version}`}
              color={color}
            >
              <span className="text-sm font-semibold text-(--color-text-primary)">
                {instance.version}
              </span>
            </SettingRow>
          </div>
        </section>

        {/* Danger zone */}
        <section className="space-y-4">
          <h2 className="text-xl font-black text-red-500 tracking-tight">
            Danger Zone
          </h2>

          <div className="rounded-2xl border border-red-500/20 bg-red-500/5">
            <div className="flex items-center justify-between p-5">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-red-500/10 flex items-center justify-center text-red-500">
                  <AlertTriangle size={18} />
                </div>
                <div>
                  <p className="text-sm font-semibold text-(--color-text-primary)">
                    Delete Instance
                  </p>
                  <p className="text-xs text-(--color-text-secondary) mt-0.5">
                    Permanently remove this instance and all its data
                  </p>
                </div>
              </div>

              {showDeleteConfirm ? (
                <div className="flex items-center gap-2">
                  <button
                    onClick={() => setShowDeleteConfirm(false)}
                    className="px-4 py-2 rounded-full text-xs text-(--color-text-secondary) hover:bg-(--color-surface-tertiary) transition-colors cursor-pointer"
                  >
                    Cancel
                  </button>
                  <button
                    onClick={handleDelete}
                    className="px-4 py-2 rounded-full bg-red-500 hover:bg-red-600 text-white text-xs font-medium transition-colors cursor-pointer"
                  >
                    Yes, Delete
                  </button>
                </div>
              ) : (
                <button
                  onClick={() => setShowDeleteConfirm(true)}
                  className="flex items-center gap-2 px-4 py-2 rounded-full border border-red-500/30 text-red-500 text-xs font-medium hover:bg-red-500 hover:text-white transition-all cursor-pointer"
                >
                  <Trash2 size={13} />
                  Delete
                </button>
              )}
            </div>
          </div>
        </section>
      </div>
    );
  }
);

/* -- Stat card -- */

function StatCard({
  icon: Icon,
  label,
  value,
  sub,
  color,
}: {
  icon: typeof Box;
  label: string;
  value: string;
  sub: string;
  color: string;
}) {
  return (
    <div className="glass-subtle rounded-2xl p-4 space-y-3">
      <div className="flex items-center gap-2">
        <div
          className="w-7 h-7 rounded-lg flex items-center justify-center"
          style={{ backgroundColor: color + "12", color: color }}
        >
          <Icon size={14} />
        </div>
        <span className="text-[11px] font-medium text-(--color-text-secondary) uppercase tracking-wider">
          {label}
        </span>
      </div>
      <div>
        <p className="text-xl font-black text-(--color-text-primary) tracking-tight">
          {value}
        </p>
        <p className="text-[11px] text-(--color-text-secondary) mt-0.5">
          {sub}
        </p>
      </div>
    </div>
  );
}

/* -- Memory slider -- */

const MEMORY_STEPS = [1024, 2048, 3072, 4096, 6144, 8192, 12288, 16384];

function MemorySlider({
  memoryMb,
  color,
  onChange,
}: {
  memoryMb: number;
  color: string;
  onChange: (mb: number) => void;
}) {
  const stepIndex = MEMORY_STEPS.indexOf(memoryMb) !== -1
    ? MEMORY_STEPS.indexOf(memoryMb)
    : MEMORY_STEPS.findIndex((s) => s >= memoryMb);
  const currentIndex = stepIndex === -1 ? 3 : stepIndex;

  const formatMem = (mb: number) =>
    mb >= 1024 ? `${(mb / 1024).toFixed(mb % 1024 === 0 ? 0 : 1)} GB` : `${mb} MB`;

  return (
    <div className="flex items-center justify-between p-4">
      <div className="flex items-center gap-3">
        <div
          className="w-9 h-9 rounded-xl flex items-center justify-center"
          style={{ backgroundColor: color + "10", color: color }}
        >
          <Settings size={16} />
        </div>
        <div>
          <p className="text-sm font-medium text-(--color-text-primary)">
            Memory Allocation
          </p>
          <p className="text-[11px] text-(--color-text-secondary) mt-0.5">
            RAM allocated to this instance
          </p>
        </div>
      </div>

      <div className="flex items-center gap-3">
        <input
          type="range"
          min={0}
          max={MEMORY_STEPS.length - 1}
          step={1}
          value={currentIndex}
          onChange={(e) => onChange(MEMORY_STEPS[Number(e.target.value)])}
          className="w-28 h-1.5 rounded-full appearance-none cursor-pointer memory-slider"
          style={
            {
              "--slider-color": color,
              background: `linear-gradient(to right, ${color} ${(currentIndex / (MEMORY_STEPS.length - 1)) * 100}%, var(--color-surface-tertiary) ${(currentIndex / (MEMORY_STEPS.length - 1)) * 100}%)`,
            } as React.CSSProperties
          }
        />
        <span
          className="text-sm font-bold min-w-[4rem] text-right"
          style={{ color }}
        >
          {formatMem(MEMORY_STEPS[currentIndex])}
        </span>
      </div>
    </div>
  );
}

/* -- Setting row -- */

function SettingRow({
  icon: Icon,
  label,
  description,
  color,
  children,
}: {
  icon: typeof Box;
  label: string;
  description: string;
  color: string;
  children: React.ReactNode;
}) {
  return (
    <div className="flex items-center justify-between p-4">
      <div className="flex items-center gap-3">
        <div
          className="w-9 h-9 rounded-xl flex items-center justify-center"
          style={{ backgroundColor: color + "10", color: color }}
        >
          <Icon size={16} />
        </div>
        <div>
          <p className="text-sm font-medium text-(--color-text-primary)">
            {label}
          </p>
          <p className="text-[11px] text-(--color-text-secondary) mt-0.5">
            {description}
          </p>
        </div>
      </div>
      {children}
    </div>
  );
}
