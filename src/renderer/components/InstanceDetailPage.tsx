import { useState, useEffect, useCallback } from "react";
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
  Share2,
  Users,
  RefreshCw,
  ChevronDown,
  Loader2,
  Globe,
  Copy,
  Square,
} from "lucide-react";
import type { Instance, InstalledMod } from "../stores/InstanceStore";
import {
  getProjectVersions,
  type ModVersion,
} from "../api/modrinth";

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
  onShareInstance?: (instance: Instance) => void;
}

export const InstanceDetailPage = observer(
  ({ instanceId, onBack, onBrowseMods, onShareInstance }: Props) => {
    const { instances: store, sharing, auth } = useStore();
    const instance = store.instances.find((i) => i.id === instanceId);
    const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
    const [isEditingName, setIsEditingName] = useState(false);
    const [editName, setEditName] = useState("");
    const [removingMod, setRemovingMod] = useState<string | null>(null);
    const [copiedCode, setCopiedCode] = useState(false);
    const [syncing, setSyncing] = useState(false);

    // Derive launch state from centralized store
    const launching = store.launchingInstanceId === instanceId;
    const gameRunning = store.runningInstanceId === instanceId;
    const launchProgress = (launching || gameRunning) ? store.launchProgress : null;
    const launchError = store.launchError;

    const handleLaunch = useCallback(() => {
      if (!instance) return;
      store.launchGame(instance.id);
    }, [instance, store]);

    if (!instance) {
      return (
        <div className="p-10">
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
        store.removeMod(instance.id, mod.projectId);
      }
      setRemovingMod(null);
    };

    const handleOpenFolder = () => {
      window.electronAPI.openInstanceFolder(instance.id);
    };

    const handleShare = () => {
      if (onShareInstance) {
        onShareInstance(instance);
      }
    };

    const handleSync = async () => {
      if (!instance.sharedInstanceId) return;
      setSyncing(true);
      await sharing.syncInstanceMods(instance);
      store.update(instance.id, { syncedAt: new Date().toISOString() });
      setSyncing(false);
    };

    return (
      <div className="p-10 space-y-8 wizard-step-enter max-w-5xl mx-auto">
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

        {/* Hero section */}
        <div
          className="relative rounded-3xl overflow-hidden"
          style={{
            background: `linear-gradient(135deg, ${color}12 0%, ${color}06 50%, transparent 100%)`,
            border: `1px solid ${color}15`,
          }}
        >
          {/* Decorative gradient orb */}
          <div
            className="absolute -top-20 -right-20 w-64 h-64 rounded-full blur-3xl opacity-20 pointer-events-none"
            style={{ backgroundColor: color }}
          />

          <div className="relative p-10">
            <div className="flex items-start gap-8">
              {/* Icon */}
              <div
                className="w-24 h-24 rounded-3xl flex items-center justify-center text-4xl font-black flex-shrink-0 shadow-xl"
                style={{
                  backgroundColor: color + "18",
                  color: color,
                  boxShadow: `0 12px 40px ${color}25`,
                }}
              >
                {letter}
              </div>

              {/* Info */}
              <div className="flex-1 min-w-0 pt-1">
                {isEditingName ? (
                  <div className="flex items-center gap-3">
                    <input
                      type="text"
                      value={editName}
                      onChange={(e) => setEditName(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === "Enter") handleSaveEdit();
                        if (e.key === "Escape") setIsEditingName(false);
                      }}
                      autoFocus
                      className="text-4xl font-black tracking-tight text-(--color-text-primary) bg-transparent border-b-2 focus:outline-none px-0 py-1 w-full max-w-lg"
                      style={{ borderColor: color }}
                    />
                    <button
                      onClick={handleSaveEdit}
                      className="w-9 h-9 rounded-full flex items-center justify-center text-white cursor-pointer"
                      style={{ backgroundColor: color }}
                    >
                      <Check size={15} />
                    </button>
                    <button
                      onClick={() => setIsEditingName(false)}
                      className="w-9 h-9 rounded-full flex items-center justify-center hover:bg-(--color-surface-tertiary) text-(--color-text-secondary) cursor-pointer"
                    >
                      <X size={15} />
                    </button>
                  </div>
                ) : (
                  <div className="flex items-center gap-3 group/name">
                    <h1 className="text-4xl font-black tracking-tight text-(--color-text-primary) truncate">
                      {instance.name}
                    </h1>
                    <button
                      onClick={handleStartEdit}
                      className="w-8 h-8 rounded-full flex items-center justify-center hover:bg-(--color-surface-tertiary) text-(--color-text-secondary) transition-all cursor-pointer flex-shrink-0 opacity-0 group-hover/name:opacity-100"
                    >
                      <Pencil size={13} />
                    </button>
                  </div>
                )}

                {/* Owner badge */}
                <p className="text-sm text-(--color-text-secondary) mt-1.5">
                  {instance.ownerName || auth.username}'s instance
                </p>

                <div className="flex items-center gap-3 mt-3 flex-wrap">
                  <span className="text-sm font-semibold text-(--color-text-secondary) bg-(--color-surface-tertiary)/60 px-4 py-1.5 rounded-xl">
                    {instance.version}
                  </span>
                  <span
                    className="text-xs px-4 py-1.5 rounded-xl font-bold flex items-center gap-1.5"
                    style={{
                      backgroundColor: loaderMeta.color + "15",
                      color: loaderMeta.color,
                    }}
                  >
                    <LoaderIcon size={13} />
                    {loaderMeta.label}
                  </span>
                  {instance.shareCode && (
                    <span className="text-xs px-3 py-1.5 rounded-xl font-semibold flex items-center gap-1.5 bg-(--color-accent)/10 text-(--color-accent)">
                      <Globe size={12} />
                      Shared
                    </span>
                  )}
                  {instance.isCollaborative && (
                    <span className="text-xs px-3 py-1.5 rounded-xl font-semibold flex items-center gap-1.5 bg-purple-500/10 text-purple-500">
                      <Users size={12} />
                      Collaborative
                    </span>
                  )}
                </div>

                {/* Launch progress bar */}
                {launching && launchProgress && (
                  <div className="mt-4 space-y-1.5">
                    <div className="flex items-center justify-between text-xs">
                      <span className="font-medium" style={{ color }}>
                        {launchProgress.message}
                      </span>
                      <span className="text-(--color-text-secondary)">
                        {launchProgress.percent}%
                      </span>
                    </div>
                    <div className="w-full h-1.5 rounded-full bg-(--color-surface-tertiary) overflow-hidden">
                      <div
                        className="h-full rounded-full transition-all duration-300"
                        style={{
                          width: `${launchProgress.percent}%`,
                          backgroundColor: color,
                        }}
                      />
                    </div>
                  </div>
                )}

                {launchError && (
                  <div className="mt-4 flex items-center gap-2 text-xs text-red-500 font-medium">
                    <AlertTriangle size={13} />
                    {launchError}
                  </div>
                )}

                {/* Actions */}
                <div className="flex items-center gap-3 mt-6">
                  <button
                    onClick={handleLaunch}
                    disabled={store.isLaunching || store.isGameRunning}
                    className="flex items-center gap-2.5 px-10 py-4 rounded-2xl text-white text-base font-bold transition-all cursor-pointer hover:shadow-xl hover:-translate-y-0.5 active:translate-y-0 disabled:opacity-70 disabled:cursor-default disabled:hover:translate-y-0 disabled:hover:shadow-none"
                    style={{
                      backgroundColor: gameRunning ? "#22c55e" : color,
                      boxShadow: `0 6px 24px ${gameRunning ? "#22c55e" : color}35`,
                    }}
                  >
                    {launching ? (
                      <>
                        <Loader2 size={16} className="animate-spin" />
                        {launchProgress?.message || "Launching..."}
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
                    onClick={handleStartEdit}
                    className="flex items-center gap-2 px-6 py-4 rounded-2xl glass-subtle text-sm text-(--color-text-secondary) hover:text-(--color-text-primary) transition-colors cursor-pointer"
                  >
                    <Pencil size={14} />
                    Edit
                  </button>
                  <button
                    onClick={handleShare}
                    className="flex items-center gap-2 px-6 py-4 rounded-2xl glass-subtle text-sm text-(--color-text-secondary) hover:text-(--color-text-primary) transition-colors cursor-pointer"
                  >
                    <Share2 size={14} />
                    Share
                  </button>
                  {instance.sharedInstanceId && (
                    <button
                      onClick={handleSync}
                      disabled={syncing}
                      className="flex items-center gap-2 px-5 py-4 rounded-2xl glass-subtle text-sm text-(--color-text-secondary) hover:text-(--color-text-primary) transition-colors cursor-pointer disabled:opacity-50"
                    >
                      <RefreshCw size={14} className={syncing ? "animate-spin" : ""} />
                      Sync
                    </button>
                  )}
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Stats row */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-5">
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
        <section className="space-y-5">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-2xl font-black text-(--color-text-primary) tracking-tight">
                Installed Mods
              </h2>
              <p className="text-xs text-(--color-text-secondary) mt-1">
                Manage your mods and swap versions
              </p>
            </div>
            <button
              onClick={onBrowseMods}
              className="flex items-center gap-2 px-5 py-2.5 rounded-xl text-sm font-semibold transition-all cursor-pointer text-white hover:opacity-90 hover:-translate-y-0.5"
              style={{
                backgroundColor: color,
                boxShadow: `0 4px 16px ${color}25`,
              }}
            >
              <Plus size={14} />
              Add Mods
            </button>
          </div>

          {installedMods.length === 0 ? (
            <div className="glass-subtle rounded-3xl">
              <div className="flex flex-col items-center justify-center py-20 text-(--color-text-secondary)">
                <div
                  className="w-16 h-16 rounded-2xl flex items-center justify-center mb-5"
                  style={{ backgroundColor: color + "10", color: color }}
                >
                  <Package size={28} strokeWidth={1.5} />
                </div>
                <p className="text-base font-semibold text-(--color-text-primary)">
                  No mods installed yet
                </p>
                <p className="text-sm mt-1.5 max-w-xs text-center">
                  Browse and install mods to enhance your gameplay experience
                </p>
                <button
                  onClick={onBrowseMods}
                  className="mt-6 flex items-center gap-2 px-6 py-3 rounded-xl text-sm font-bold text-white transition-all cursor-pointer hover:opacity-90"
                  style={{
                    backgroundColor: color,
                  }}
                >
                  <Sparkles size={14} />
                  Browse Mods
                </button>
              </div>
            </div>
          ) : (
            <div className="space-y-3">
              {installedMods.map((mod) => (
                <ModRow
                  key={mod.projectId}
                  mod={mod}
                  instance={instance}
                  color={color}
                  isRemoving={removingMod === mod.projectId}
                  onRemoveRequest={() => setRemovingMod(mod.projectId)}
                  onRemoveCancel={() => setRemovingMod(null)}
                  onRemoveConfirm={() => handleRemoveMod(mod)}
                />
              ))}
            </div>
          )}
        </section>

        {/* Instance Settings section */}
        <section className="space-y-5">
          <h2 className="text-2xl font-black text-(--color-text-primary) tracking-tight">
            Instance Settings
          </h2>

          <div className="glass-subtle rounded-3xl divide-y divide-(--color-border)/50">
            <SettingRow
              icon={FolderOpen}
              label="Game Directory"
              description="Where this instance's files are stored"
              color={color}
            >
              <button
                onClick={handleOpenFolder}
                className="px-5 py-2 rounded-xl text-xs font-semibold bg-(--color-surface-tertiary)/80 text-(--color-text-secondary) hover:text-(--color-text-primary) transition-colors cursor-pointer"
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
              <span className="text-sm font-bold text-(--color-text-primary)">
                {instance.version}
              </span>
            </SettingRow>

            {instance.shareCode && (
              <SettingRow
                icon={Share2}
                label="Share Code"
                description="Share this code with friends to let them import your pack"
                color="var(--color-accent)"
              >
                <div className="flex items-center gap-2">
                  <span className="text-sm font-mono font-bold text-(--color-accent) bg-(--color-accent)/10 px-3 py-1.5 rounded-lg">
                    {instance.shareCode}
                  </span>
                  <button
                    onClick={() => {
                      navigator.clipboard.writeText(instance.shareCode!);
                      setCopiedCode(true);
                      setTimeout(() => setCopiedCode(false), 2000);
                    }}
                    className="px-3 py-1.5 rounded-lg text-xs font-medium text-(--color-text-secondary) hover:text-(--color-accent) transition-colors cursor-pointer"
                  >
                    {copiedCode ? <Check size={14} /> : <Copy size={14} />}
                  </button>
                </div>
              </SettingRow>
            )}
          </div>
        </section>

        {/* Danger zone */}
        <section className="space-y-5">
          <h2 className="text-2xl font-black text-red-500 tracking-tight">
            Danger Zone
          </h2>

          <div className="rounded-3xl border border-red-500/20 bg-red-500/5">
            <div className="flex items-center justify-between p-6">
              <div className="flex items-center gap-4">
                <div className="w-12 h-12 rounded-2xl bg-red-500/10 flex items-center justify-center text-red-500">
                  <AlertTriangle size={20} />
                </div>
                <div>
                  <p className="text-sm font-bold text-(--color-text-primary)">
                    Delete Instance
                  </p>
                  <p className="text-xs text-(--color-text-secondary) mt-0.5">
                    Permanently remove this instance and all its data
                  </p>
                </div>
              </div>

              {showDeleteConfirm ? (
                <div className="flex items-center gap-3">
                  <button
                    onClick={() => setShowDeleteConfirm(false)}
                    className="px-5 py-2.5 rounded-xl text-xs text-(--color-text-secondary) hover:bg-(--color-surface-tertiary) transition-colors cursor-pointer"
                  >
                    Cancel
                  </button>
                  <button
                    onClick={handleDelete}
                    className="px-5 py-2.5 rounded-xl bg-red-500 hover:bg-red-600 text-white text-xs font-semibold transition-colors cursor-pointer"
                  >
                    Yes, Delete
                  </button>
                </div>
              ) : (
                <button
                  onClick={() => setShowDeleteConfirm(true)}
                  className="flex items-center gap-2 px-5 py-2.5 rounded-xl border border-red-500/30 text-red-500 text-xs font-semibold hover:bg-red-500 hover:text-white transition-all cursor-pointer"
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

/* -- Mod row with version selector -- */

function ModRow({
  mod,
  instance,
  color,
  isRemoving,
  onRemoveRequest,
  onRemoveCancel,
  onRemoveConfirm,
}: {
  mod: InstalledMod;
  instance: Instance;
  color: string;
  isRemoving: boolean;
  onRemoveRequest: () => void;
  onRemoveCancel: () => void;
  onRemoveConfirm: () => void;
}) {
  const { instances: store } = useStore();
  const [showVersions, setShowVersions] = useState(false);
  const [versions, setVersions] = useState<ModVersion[]>([]);
  const [loadingVersions, setLoadingVersions] = useState(false);
  const [swapping, setSwapping] = useState(false);

  const loadVersions = async () => {
    if (versions.length > 0) {
      setShowVersions(!showVersions);
      return;
    }
    setShowVersions(true);
    setLoadingVersions(true);
    try {
      const allVersions = await getProjectVersions(mod.projectId);
      // Filter to compatible versions (same loader + MC version)
      const compatible = allVersions.filter(
        (v) =>
          v.game_versions.includes(instance.version) &&
          v.loaders.some(
            (l) => l.toLowerCase() === instance.loader.toLowerCase()
          )
      );
      // Sort: release > beta > alpha, then by date
      const typeOrder: Record<string, number> = { release: 0, beta: 1, alpha: 2 };
      compatible.sort((a, b) => {
        const ta = typeOrder[a.version_type] ?? 3;
        const tb = typeOrder[b.version_type] ?? 3;
        if (ta !== tb) return ta - tb;
        return new Date(b.date_published).getTime() - new Date(a.date_published).getTime();
      });
      setVersions(compatible);
    } catch {
      setVersions([]);
    }
    setLoadingVersions(false);
  };

  const handleSwapVersion = async (newVersion: ModVersion) => {
    setSwapping(true);
    try {
      // Remove old file
      try {
        await window.electronAPI.removeModFile({
          instanceId: instance.id,
          filename: mod.filename,
        });
      } catch {
        // old file might be gone
      }

      // Download new file
      const primaryFile = newVersion.files.find((f) => f.primary) ?? newVersion.files[0];
      if (!primaryFile) {
        setSwapping(false);
        return;
      }

      const result = await window.electronAPI.downloadMod({
        instanceId: instance.id,
        url: primaryFile.url,
        filename: primaryFile.filename,
      });

      if (result.success) {
        store.updateMod(instance.id, mod.projectId, {
          versionId: newVersion.id,
          filename: primaryFile.filename,
        });
      }
    } catch {
      // swap failed
    }
    setSwapping(false);
    setShowVersions(false);
  };

  return (
    <div
      className="glass-subtle rounded-2xl overflow-hidden transition-all"
    >
      <div className="flex items-center gap-4 p-5 group">
        {mod.iconUrl ? (
          <img
            src={mod.iconUrl}
            alt={mod.title}
            className="w-12 h-12 rounded-xl object-cover flex-shrink-0 shadow-sm ring-1 ring-black/5"
          />
        ) : (
          <div
            className="w-12 h-12 rounded-xl flex items-center justify-center flex-shrink-0"
            style={{ backgroundColor: color + "10", color: color }}
          >
            <Package size={18} />
          </div>
        )}

        <div className="flex-1 min-w-0">
          <p className="text-sm font-bold text-(--color-text-primary) truncate">
            {mod.title}
          </p>
          <p className="text-[11px] text-(--color-text-secondary) truncate mt-0.5">
            {mod.filename}
          </p>
        </div>

        {/* Version selector button */}
        <button
          onClick={loadVersions}
          disabled={swapping}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[11px] font-semibold transition-all cursor-pointer hover:bg-(--color-surface-tertiary)"
          style={{
            color: showVersions ? color : "var(--color-text-secondary)",
          }}
        >
          {swapping ? (
            <Loader2 size={12} className="animate-spin" />
          ) : (
            <>
              <RefreshCw size={11} />
              Version
              <ChevronDown
                size={11}
                className={`transition-transform ${showVersions ? "rotate-180" : ""}`}
              />
            </>
          )}
        </button>

        {/* Remove */}
        {isRemoving ? (
          <div className="flex items-center gap-2">
            <span className="text-[11px] text-red-500 font-semibold">
              Remove?
            </span>
            <button
              onClick={onRemoveCancel}
              className="px-3 py-1.5 rounded-lg text-[11px] text-(--color-text-secondary) hover:bg-(--color-surface-tertiary) transition-colors cursor-pointer"
            >
              No
            </button>
            <button
              onClick={onRemoveConfirm}
              className="px-3 py-1.5 rounded-lg bg-red-500 text-white text-[11px] font-semibold hover:bg-red-600 transition-colors cursor-pointer"
            >
              Yes
            </button>
          </div>
        ) : (
          <button
            onClick={onRemoveRequest}
            className="w-9 h-9 rounded-xl flex items-center justify-center hover:bg-red-500/10 text-(--color-text-secondary) hover:text-red-500 transition-all cursor-pointer opacity-0 group-hover:opacity-100"
          >
            <Trash2 size={14} />
          </button>
        )}
      </div>

      {/* Version dropdown */}
      {showVersions && (
        <div className="border-t border-(--color-border)/50 bg-(--color-surface-tertiary)/20 max-h-[280px] overflow-y-auto version-dropdown-enter">
          {loadingVersions ? (
            <div className="flex items-center justify-center py-8">
              <Loader2 size={20} className="animate-spin" style={{ color }} />
            </div>
          ) : versions.length === 0 ? (
            <div className="text-center py-8 text-xs text-(--color-text-secondary)">
              No compatible versions found
            </div>
          ) : (
            versions.map((v) => {
              const isCurrent = v.id === mod.versionId;
              const primaryFile = v.files.find((f) => f.primary) ?? v.files[0];
              return (
                <button
                  key={v.id}
                  onClick={() => !isCurrent && handleSwapVersion(v)}
                  disabled={isCurrent || swapping}
                  className={`w-full flex items-center gap-3 px-5 py-3 text-left transition-all border-b border-(--color-border)/30 last:border-b-0 ${
                    isCurrent
                      ? "bg-(--color-accent)/5"
                      : "hover:bg-(--color-surface-tertiary)/50 cursor-pointer"
                  }`}
                >
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <span
                        className={`text-xs font-semibold ${
                          isCurrent
                            ? "text-(--color-accent)"
                            : "text-(--color-text-primary)"
                        }`}
                      >
                        {v.version_number}
                      </span>
                      <span
                        className={`text-[9px] px-1.5 py-0.5 rounded-full font-bold uppercase ${
                          v.version_type === "release"
                            ? "bg-green-500/10 text-green-500"
                            : v.version_type === "beta"
                            ? "bg-amber-500/10 text-amber-500"
                            : "bg-red-500/10 text-red-500"
                        }`}
                      >
                        {v.version_type}
                      </span>
                      {isCurrent && (
                        <span className="text-[9px] px-1.5 py-0.5 rounded-full bg-(--color-accent)/10 text-(--color-accent) font-bold">
                          CURRENT
                        </span>
                      )}
                    </div>
                    <p className="text-[10px] text-(--color-text-secondary) mt-0.5 truncate">
                      {primaryFile?.filename ?? "No file"} &middot;{" "}
                      {new Date(v.date_published).toLocaleDateString()}
                    </p>
                  </div>
                  {!isCurrent && (
                    <span
                      className="text-[10px] font-semibold px-3 py-1 rounded-lg"
                      style={{
                        backgroundColor: color + "10",
                        color: color,
                      }}
                    >
                      Switch
                    </span>
                  )}
                </button>
              );
            })
          )}
        </div>
      )}
    </div>
  );
}

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
    <div className="glass-subtle rounded-2xl p-5 space-y-3">
      <div className="flex items-center gap-2.5">
        <div
          className="w-8 h-8 rounded-xl flex items-center justify-center"
          style={{ backgroundColor: color + "12", color: color }}
        >
          <Icon size={15} />
        </div>
        <span className="text-[11px] font-semibold text-(--color-text-secondary) uppercase tracking-wider">
          {label}
        </span>
      </div>
      <div>
        <p className="text-2xl font-black text-(--color-text-primary) tracking-tight">
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

const ALL_MEMORY_STEPS = [512, 1024, 2048, 3072, 4096, 6144, 8192, 10240, 12288, 16384, 20480, 24576, 32768, 49152, 65536];

function buildMemorySteps(systemMemoryMb: number): number[] {
  const maxAlloc = Math.floor(systemMemoryMb * 0.75);
  return ALL_MEMORY_STEPS.filter((s) => s <= maxAlloc);
}

function getRecommendedMemory(systemMemoryMb: number): number {
  if (systemMemoryMb >= 32768) return 8192;
  if (systemMemoryMb >= 16384) return 6144;
  if (systemMemoryMb >= 8192) return 4096;
  if (systemMemoryMb >= 4096) return 2048;
  return 1024;
}

const formatMem = (mb: number) =>
  mb >= 1024 ? `${(mb / 1024).toFixed(mb % 1024 === 0 ? 0 : 1)} GB` : `${mb} MB`;

function MemorySlider({
  memoryMb,
  color,
  onChange,
}: {
  memoryMb: number;
  color: string;
  onChange: (mb: number) => void;
}) {
  const [systemMemoryMb, setSystemMemoryMb] = useState(16384);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    window.electronAPI.getSystemInfo().then((info) => {
      setSystemMemoryMb(info.totalMemoryMb);
      setLoaded(true);
    });
  }, []);

  const steps = buildMemorySteps(systemMemoryMb);
  const recommended = getRecommendedMemory(systemMemoryMb);

  useEffect(() => {
    if (loaded && steps.length > 0 && memoryMb > steps[steps.length - 1]) {
      onChange(steps[steps.length - 1]);
    }
  }, [loaded]);

  const stepIndex = steps.indexOf(memoryMb) !== -1
    ? steps.indexOf(memoryMb)
    : steps.findIndex((s) => s >= memoryMb);
  const currentIndex = Math.max(0, stepIndex === -1 ? steps.length - 1 : stepIndex);

  return (
    <div className="p-5 space-y-3">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div
            className="w-10 h-10 rounded-xl flex items-center justify-center"
            style={{ backgroundColor: color + "10", color: color }}
          >
            <Settings size={17} />
          </div>
          <div>
            <p className="text-sm font-semibold text-(--color-text-primary)">
              Memory Allocation
            </p>
            <p className="text-[11px] text-(--color-text-secondary) mt-0.5">
              System: {formatMem(systemMemoryMb)} total · Recommended: {formatMem(recommended)}
            </p>
          </div>
        </div>

        <span
          className="text-sm font-black min-w-[4.5rem] text-right"
          style={{ color }}
        >
          {steps.length > 0 ? formatMem(steps[currentIndex]) : formatMem(memoryMb)}
        </span>
      </div>

      {steps.length > 1 && (
        <div className="pl-[52px]">
          <input
            type="range"
            min={0}
            max={steps.length - 1}
            step={1}
            value={currentIndex}
            onChange={(e) => onChange(steps[Number(e.target.value)])}
            className="w-full h-1.5 rounded-full appearance-none cursor-pointer memory-slider"
            style={
              {
                "--slider-color": color,
                background: `linear-gradient(to right, ${color} ${(currentIndex / (steps.length - 1)) * 100}%, var(--color-surface-tertiary) ${(currentIndex / (steps.length - 1)) * 100}%)`,
              } as React.CSSProperties
            }
          />
          <div className="flex justify-between mt-1.5">
            <span className="text-[10px] text-(--color-text-secondary)">
              {formatMem(steps[0])}
            </span>
            <span className="text-[10px] text-(--color-text-secondary)">
              {formatMem(steps[steps.length - 1])}
            </span>
          </div>
        </div>
      )}
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
    <div className="flex items-center justify-between p-5">
      <div className="flex items-center gap-3">
        <div
          className="w-10 h-10 rounded-xl flex items-center justify-center"
          style={{ backgroundColor: color + "10", color: color }}
        >
          <Icon size={17} />
        </div>
        <div>
          <p className="text-sm font-semibold text-(--color-text-primary)">
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
