import { useState, useRef, useEffect } from "react";
import { observer } from "mobx-react-lite";
import { useStore } from "../stores";
import {
  Layers,
  Plus,
  Play,
  Trash2,
  Pencil,
  Check,
  X,
  Box,
  Zap,
  Hammer,
  Clock,
  Calendar,
  Rocket,
  Share2,
  Download,
  Globe,
  Users,
  Loader2,
  Square,
  Cloud,
} from "lucide-react";
import { CreateInstanceWizard } from "./CreateInstanceWizard";
import { InstanceDetailPage } from "./InstanceDetailPage";
import { ShareInstanceModal } from "./ShareInstanceModal";
import { ImportShareCodeModal } from "./ImportShareCodeModal";
import type { Instance } from "../stores/InstanceStore";
import type { SharedInstanceData } from "../stores/SharingStore";

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

function getInstanceModCount(instance: Pick<Instance, "installedMods" | "modCount">) {
  return instance.installedMods.length > 0 ? instance.installedMods.length : instance.modCount;
}

interface InstancesPageProps {
  onNavigate?: (page: string) => void;
}

export const InstancesPage = observer(({ onNavigate }: InstancesPageProps) => {
  const { instances: store, installs } = useStore();
  const [showWizard, setShowWizard] = useState(false);
  const [deleteConfirm, setDeleteConfirm] = useState<string | null>(null);
  const [selectedInstanceId, setSelectedInstanceId] = useState<string | null>(null);
  const [shareInstance, setShareInstance] = useState<Instance | null>(null);
  const [showImport, setShowImport] = useState(false);

  const handleCreate = (data: {
    name: string;
    version: string;
    loader: "vanilla" | "fabric" | "forge";
    iconColor: string;
  }) => {
    store.create(data);
    setShowWizard(false);
  };

  const localCount = store.instances.length;
  const cloudCount = store.cloudInstances.length;
  const totalMods = store.instances.reduce(
    (sum, instance) => sum + getInstanceModCount(instance),
    0
  );
  const recentInstance =
    [...store.instances].sort((a, b) => {
      const aTime = a.lastPlayed ? new Date(a.lastPlayed).getTime() : 0;
      const bTime = b.lastPlayed ? new Date(b.lastPlayed).getTime() : 0;
      return bTime - aTime;
    })[0] ?? null;

  if (selectedInstanceId) {
    return (
      <>
        <InstanceDetailPage
          instanceId={selectedInstanceId}
          onBack={() => setSelectedInstanceId(null)}
          onBrowseMods={() => {
            installs.setPreferredInstance(selectedInstanceId);
            setSelectedInstanceId(null);
            onNavigate?.("browse");
          }}
          onShareInstance={(inst) => setShareInstance(inst)}
        />
        {shareInstance && (
          <ShareInstanceModal
            instance={shareInstance}
            onClose={() => setShareInstance(null)}
          />
        )}
      </>
    );
  }

  return (
    <div className="space-y-5 p-6 lg:p-7">
      <div className="flex items-center justify-end gap-2">
          <button
            onClick={() => setShowImport(true)}
            className="flex items-center gap-2 px-4 py-2.5 rounded-control glass-subtle text-sm font-body text-(--color-text-secondary) hover:text-(--color-text-primary) transition-all cursor-pointer"
          >
            <Download size={15} />
            Import
          </button>
          {(store.instances.length > 0 || store.cloudInstances.length > 0) && (
            <button
              onClick={() => setShowWizard(true)}
              className="flex items-center gap-2 px-5 py-2.5 rounded-control bg-(--color-accent) hover:bg-(--color-accent-hover) text-fg-on-accent text-sm font-emphasis transition-all cursor-pointer shadow-sm shadow-(--color-accent)/12"
            >
              <Plus size={15} />
              New Instance
            </button>
          )}
      </div>

      <section className="grid gap-4 xl:grid-cols-[minmax(0,1.2fr)_minmax(18rem,0.8fr)]">
        <div className="rounded-card border border-(--color-border) bg-(--color-surface-secondary) p-6">
          <div className="flex items-start justify-between gap-6">
            <div>
              <p className="text-caption font-strong uppercase tracking-[0.18em] text-(--color-text-secondary)">
                Workspace
              </p>
              <h2 className="mt-2 text-2xl font-display tracking-tight text-(--color-text-primary)">
                Your instance library
              </h2>
              <p className="mt-2 max-w-xl text-sm leading-relaxed text-(--color-text-secondary)">
                Launch active setups, maintain shared packs, and keep your local installs organized without leaving the launcher.
              </p>
            </div>
            {recentInstance && (
              <div className="min-w-[14rem] rounded-window border border-(--color-border) bg-(--color-surface) px-4 py-4">
                <p className="text-micro font-strong uppercase tracking-[0.16em] text-(--color-text-secondary)">
                  Most Recent
                </p>
                <div className="mt-3 flex items-center gap-3">
                  <div
                    className="flex h-10 w-10 items-center justify-center rounded-control text-sm font-display"
                    style={{
                      backgroundColor: `${recentInstance.iconColor}15`,
                      color: recentInstance.iconColor,
                    }}
                  >
                    {recentInstance.name[0]?.toUpperCase() ?? "?"}
                  </div>
                  <div className="min-w-0">
                    <p className="truncate text-sm font-strong text-(--color-text-primary)">
                      {recentInstance.name}
                    </p>
                    <p className="text-caption text-(--color-text-secondary)">
                      {recentInstance.lastPlayed ? `Played ${timeAgo(recentInstance.lastPlayed)}` : "Not launched yet"}
                    </p>
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>

        <div className="grid gap-4 sm:grid-cols-3 xl:grid-cols-1">
          <SummaryCard label="Local Instances" value={String(localCount)} sub="Installed on this device" icon={Layers} />
          <SummaryCard label="Installed Mods" value={String(totalMods)} sub="Across local instances" icon={Box} />
          <SummaryCard label="Cloud Packs" value={String(cloudCount)} sub="Available to sync" icon={Cloud} />
        </div>
      </section>

      {/* Content */}
      {store.instances.length === 0 && store.cloudInstances.length === 0 && !store.loadingCloud ? (
        <EmptyState
          onCreateClick={() => setShowWizard(true)}
          onImportClick={() => setShowImport(true)}
        />
      ) : (
        <div className="space-y-5">
          {/* Local instances */}
          {store.instances.length > 0 && (
            <section className="space-y-4">
              <div className="flex items-end justify-between gap-4">
                <div>
                  <p className="text-caption font-strong uppercase tracking-[0.18em] text-(--color-text-secondary)">
                    Local Instances
                  </p>
                  <h3 className="mt-1 text-xl font-display tracking-tight text-(--color-text-primary)">
                    Ready to launch
                  </h3>
                </div>
                <p className="text-xs text-(--color-text-secondary)">
                  {store.instances.length} instance{store.instances.length === 1 ? "" : "s"} on this device
                </p>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
              {store.instances.map((inst) => (
                <InstanceCard
                  key={inst.id}
                  instance={inst}
                  isDeleting={deleteConfirm === inst.id}
                  isLaunching={store.launchingInstanceId === inst.id}
                  isRunning={store.runningInstanceId === inst.id}
                  isBusy={store.isLaunching || store.isGameRunning}
                  launchProgress={store.launchingInstanceId === inst.id ? store.launchProgress : null}
                  onClick={() => setSelectedInstanceId(inst.id)}
                  onLaunch={() => store.launchGame(inst.id)}
                  onShare={() => setShareInstance(inst)}
                  onRename={(name) => store.update(inst.id, { name })}
                  onDeleteRequest={() => setDeleteConfirm(inst.id)}
                  onDeleteConfirm={() => {
                    store.remove(inst.id);
                    setDeleteConfirm(null);
                  }}
                  onDeleteCancel={() => setDeleteConfirm(null)}
                />
              ))}
              </div>
            </section>
          )}

          {/* Cloud loading indicator */}
          {store.loadingCloud && (
            <div className="flex items-center justify-center gap-2 py-6 text-xs text-(--color-text-secondary)">
              <Loader2 size={12} className="animate-spin text-(--color-accent)" />
              <span>Checking your account for saved instances...</span>
            </div>
          )}

          {/* Cloud instances — available from Supabase but not on this device */}
          {store.cloudInstances.length > 0 && (
            <section className="space-y-4">
              <div className="flex items-end justify-between gap-4">
                <div>
                  <p className="text-caption font-strong uppercase tracking-[0.18em] text-(--color-text-secondary)">
                    Cloud Library
                  </p>
                  <h3 className="mt-1 text-xl font-display tracking-tight text-(--color-text-primary)">
                    Available from your account
                  </h3>
                </div>
                <p className="text-xs text-(--color-text-secondary)">
                  Sync shared or remote packs onto this device
                </p>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
                {store.cloudInstances.map((cloud) => (
                  <CloudInstanceCard
                    key={cloud.id}
                    data={cloud}
                    syncProgress={installs.getSharedProgress(cloud.id)}
                    onSync={() => {
                      void installs.installSharedInstance(cloud);
                    }}
                  />
                ))}
              </div>
            </section>
          )}
        </div>
      )}

      {/* Modals */}
      {showWizard && (
        <CreateInstanceWizard
          onClose={() => setShowWizard(false)}
          onCreate={handleCreate}
        />
      )}

      {shareInstance && (
        <ShareInstanceModal
          instance={shareInstance}
          onClose={() => setShareInstance(null)}
        />
      )}

      {showImport && (
        <ImportShareCodeModal
          onClose={() => setShowImport(false)}
          onImported={() => {}}
        />
      )}
    </div>
  );
});

function SummaryCard({
  label,
  value,
  sub,
  icon: Icon,
}: {
  label: string;
  value: string;
  sub: string;
  icon: typeof Box;
}) {
  return (
    <div className="rounded-modal border border-(--color-border) bg-(--color-surface-secondary) px-5 py-4">
      <div className="flex items-center gap-2 text-(--color-text-secondary)">
        <Icon size={13} />
        <span className="text-micro font-strong uppercase tracking-[0.16em]">{label}</span>
      </div>
      <p className="mt-3 text-3xl font-display tracking-tight text-(--color-text-primary)">
        {value}
      </p>
      <p className="mt-1 text-xs text-(--color-text-secondary)">{sub}</p>
    </div>
  );
}

/* -- Empty state -- */

function EmptyState({
  onCreateClick,
  onImportClick,
}: {
  onCreateClick: () => void;
  onImportClick: () => void;
}) {
  return (
      <div className="flex flex-col items-center justify-center py-20">
        <div className="relative">
        <div className="w-20 h-20 rounded-modal bg-(--color-accent)/8 flex items-center justify-center mb-5">
          <Rocket
            size={32}
            strokeWidth={1.5}
            className="text-(--color-accent)"
          />
        </div>
        <div className="absolute -top-1 -right-1 w-7 h-7 rounded-md bg-(--color-accent)/12 flex items-center justify-center animate-pulse">
          <Plus size={12} className="text-(--color-accent)" />
        </div>
      </div>
      <h3 className="text-lg font-strong text-(--color-text-primary)">
        No instances yet
      </h3>
      <p className="text-sm text-(--color-text-secondary) mt-1 max-w-xs text-center">
        Create your first instance or import one from a share code.
      </p>
      <div className="flex items-center gap-3 mt-5">
        <button
          onClick={onCreateClick}
          className="flex items-center gap-2 px-5 py-2.5 rounded-control bg-(--color-accent) hover:bg-(--color-accent-hover) text-fg-on-accent text-sm font-emphasis transition-all cursor-pointer shadow-sm shadow-(--color-accent)/12"
        >
          <Plus size={15} />
          Create Instance
        </button>
        <button
          onClick={onImportClick}
          className="flex items-center gap-2 px-5 py-2.5 rounded-control glass-subtle text-sm font-body text-(--color-text-secondary) hover:text-(--color-text-primary) transition-all cursor-pointer"
        >
          <Download size={15} />
          Import Code
        </button>
      </div>
    </div>
  );
}

/* -- Instance card -- */

function InstanceCard({
  instance,
  isDeleting,
  isLaunching,
  isRunning,
  isBusy,
  launchProgress,
  onClick,
  onLaunch,
  onShare,
  onRename,
  onDeleteRequest,
  onDeleteConfirm,
  onDeleteCancel,
}: {
  instance: Instance;
  isDeleting: boolean;
  isLaunching: boolean;
  isRunning: boolean;
  isBusy: boolean;
  launchProgress: import("../types/electron").LaunchProgress | null;
  onClick: () => void;
  onLaunch: () => void;
  onShare: () => void;
  onRename: (name: string) => void;
  onDeleteRequest: () => void;
  onDeleteConfirm: () => void;
  onDeleteCancel: () => void;
}) {
  const loaderMeta = LOADER_META[instance.loader] ?? LOADER_META.vanilla;
  const LoaderIcon = loaderMeta.icon;
  const letter = instance.name[0]?.toUpperCase() ?? "?";
  const [isEditing, setIsEditing] = useState(false);
  const [editName, setEditName] = useState(instance.name);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (isEditing) inputRef.current?.focus();
  }, [isEditing]);

  const handleStartEdit = () => {
    setEditName(instance.name);
    setIsEditing(true);
  };

  const handleSaveEdit = () => {
    const trimmed = editName.trim();
    if (trimmed && trimmed !== instance.name) {
      onRename(trimmed);
    }
    setIsEditing(false);
  };

  const handleCancelEdit = () => {
    setEditName(instance.name);
    setIsEditing(false);
  };

  return (
    <div
      role="button"
      tabIndex={isEditing ? -1 : 0}
      aria-label={`Open ${instance.name}`}
      className="group relative rounded-modal bg-(--color-surface-secondary) border border-(--color-border) overflow-hidden hover:border-line-strong hover:shadow-md transition-all duration-200 cursor-pointer"
      onClick={isEditing ? undefined : onClick}
      onKeyDown={(event) => {
        if (isEditing) return;
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          onClick();
        }
      }}
    >
      <div className="p-5">
        <div className="mb-4 flex items-center justify-between">
          <span className="text-micro font-strong uppercase tracking-[0.16em] text-(--color-text-secondary)">
            Instance
          </span>
          <span className="text-micro font-body text-(--color-text-secondary)">
            {instance.lastPlayed ? `Played ${timeAgo(instance.lastPlayed)}` : "Never launched"}
          </span>
        </div>
        {/* Instance info */}
        <div className="flex items-start gap-3.5">
          <div
            className="w-12 h-12 rounded-control flex items-center justify-center text-base font-strong flex-shrink-0"
            style={{
              backgroundColor: instance.iconColor + "15",
              color: instance.iconColor,
            }}
          >
            {letter}
          </div>
          <div className="flex-1 min-w-0">
            {isEditing ? (
              <div className="flex items-center gap-1.5" onClick={(e) => e.stopPropagation()}>
                <input
                  ref={inputRef}
                  value={editName}
                  onChange={(e) => setEditName(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") handleSaveEdit();
                    if (e.key === "Escape") handleCancelEdit();
                  }}
                  onBlur={handleSaveEdit}
                  className="text-sm font-strong text-(--color-text-primary) bg-(--color-surface-tertiary) border border-(--color-border) rounded-md px-2 py-1 w-full outline-none focus:border-(--color-accent)/40"
                  style={{ minWidth: 0 }}
                />
                <button
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={handleSaveEdit}
                  className="w-6 h-6 rounded-sm flex items-center justify-center text-white flex-shrink-0 cursor-pointer"
                  style={{ backgroundColor: instance.iconColor }}
                >
                  <Check size={11} />
                </button>
                <button
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={handleCancelEdit}
                  className="w-6 h-6 rounded-sm flex items-center justify-center text-(--color-text-secondary) hover:bg-(--color-surface-tertiary) flex-shrink-0 cursor-pointer"
                >
                  <X size={11} />
                </button>
              </div>
            ) : (
              <h3 className="text-sm font-strong text-(--color-text-primary) truncate">
                {instance.name}
              </h3>
            )}
            <div className="flex items-center gap-1.5 mt-1 flex-wrap">
              <span className="text-caption font-body text-(--color-text-secondary) bg-(--color-surface-tertiary) px-2 py-0.5 rounded-sm">
                {instance.version}
              </span>
              <span
                className="text-micro px-2 py-0.5 rounded-sm font-emphasis flex items-center gap-1"
                style={{
                  backgroundColor: loaderMeta.color + "12",
                  color: loaderMeta.color,
                }}
              >
                <LoaderIcon size={9} />
                {loaderMeta.label}
              </span>
              {instance.shareCode && (
                <span className="text-micro px-2 py-0.5 rounded-sm font-emphasis flex items-center gap-1 bg-(--color-accent)/8 text-(--color-accent)">
                  <Globe size={8} />
                  Shared
                </span>
              )}
              {instance.isCollaborative && (
                <span className="text-micro px-2 py-0.5 rounded-sm font-emphasis flex items-center gap-1 bg-collab/8 text-collab">
                  <Users size={8} />
                  Collab
                </span>
              )}
            </div>
          </div>
        </div>

        {/* Meta */}
        <div className="mt-4 grid grid-cols-3 gap-2">
          <div className="rounded-control bg-(--color-surface) px-3 py-2">
            <span className="text-micro font-strong uppercase tracking-[0.14em] text-(--color-text-secondary)">Mods</span>
            <p className="mt-1 text-sm font-strong text-(--color-text-primary)">{getInstanceModCount(instance)}</p>
          </div>
          <div className="rounded-control bg-(--color-surface) px-3 py-2">
            <span className="text-micro font-strong uppercase tracking-[0.14em] text-(--color-text-secondary)">Created</span>
            <p className="mt-1 text-sm font-strong text-(--color-text-primary)">{timeAgo(instance.dateCreated)}</p>
          </div>
          <div className="rounded-control bg-(--color-surface) px-3 py-2">
            <span className="text-micro font-strong uppercase tracking-[0.14em] text-(--color-text-secondary)">Status</span>
            <p className="mt-1 text-sm font-strong text-(--color-text-primary)">{isRunning ? "Running" : isLaunching ? "Launching" : "Idle"}</p>
          </div>
        </div>

        <div className="flex items-center gap-3.5 mt-3 text-caption text-(--color-text-secondary)">
          <span className="flex items-center gap-1">
            <Layers size={10} />
            {getInstanceModCount(instance)} mod{getInstanceModCount(instance) !== 1 ? "s" : ""}
          </span>
          {instance.lastPlayed && (
            <span className="flex items-center gap-1">
              <Clock size={10} />
              {timeAgo(instance.lastPlayed)}
            </span>
          )}
          <span className="flex items-center gap-1">
            <Calendar size={10} />
            {timeAgo(instance.dateCreated)}
          </span>
        </div>

        {/* Launch progress */}
        {isLaunching && launchProgress && (
          <div className="mt-3 space-y-1.5">
            <div className="flex items-center justify-between text-caption">
              <span className="font-body truncate" style={{ color: instance.iconColor }}>
                {launchProgress.message}
              </span>
              {launchProgress.percent >= 0 && (
                <span className="text-(--color-text-secondary) ml-2 flex-shrink-0">
                  {launchProgress.percent}%
                </span>
              )}
            </div>
            {launchProgress.percent >= 0 && (
              <div className="w-full h-1 rounded-pill bg-(--color-surface-tertiary) overflow-hidden">
                <div
                  className="h-full rounded-pill transition-all duration-300"
                  style={{
                    width: `${launchProgress.percent}%`,
                    backgroundColor: instance.iconColor,
                  }}
                />
              </div>
            )}
          </div>
        )}

        {/* Actions */}
        <div
          className="flex items-center gap-2 mt-4"
          onClick={(e) => e.stopPropagation()}
        >
          {isDeleting ? (
            <>
              <span className="text-xs text-danger font-body flex-1">
                Delete this instance?
              </span>
              <button
                onClick={onDeleteCancel}
                className="px-3 py-1.5 rounded-md text-xs text-(--color-text-secondary) hover:bg-(--color-surface-tertiary) transition-colors cursor-pointer"
              >
                Cancel
              </button>
              <button
                onClick={onDeleteConfirm}
                className="px-3 py-1.5 rounded-md bg-danger hover:bg-danger-hover text-white text-xs font-body transition-colors cursor-pointer"
              >
                Delete
              </button>
            </>
          ) : (
            <>
              <button
                onClick={onLaunch}
                disabled={isBusy}
                className="flex-1 flex items-center justify-center gap-2 py-2.5 rounded-control text-white text-xs font-strong transition-all cursor-pointer hover:shadow-md disabled:opacity-50 disabled:cursor-default disabled:hover:shadow-none"
                style={{
                  backgroundColor: isRunning ? "#22c55e" : instance.iconColor,
                  boxShadow: `0 2px 8px ${isRunning ? "#22c55e" : instance.iconColor}18`,
                }}
              >
                {isLaunching ? (
                  <>
                    <Loader2 size={12} className="animate-spin" />
                    Launching...
                  </>
                ) : isRunning ? (
                  <>
                    <Square size={10} fill="currentColor" />
                    Running
                  </>
                ) : (
                  <>
                    <Play size={12} fill="currentColor" />
                    Launch
                  </>
                )}
              </button>
              <button
                onClick={onShare}
                className="w-8 h-8 rounded-control flex items-center justify-center hover:bg-(--color-accent)/10 text-(--color-text-secondary) hover:text-(--color-accent) transition-colors cursor-pointer"
              >
                <Share2 size={12} />
              </button>
              <button
                onClick={handleStartEdit}
                className="w-8 h-8 rounded-control flex items-center justify-center hover:bg-(--color-surface-tertiary) text-(--color-text-secondary) transition-colors cursor-pointer"
              >
                <Pencil size={12} />
              </button>
              <button
                onClick={onDeleteRequest}
                className="w-8 h-8 rounded-control flex items-center justify-center hover:bg-danger/10 text-(--color-text-secondary) hover:text-danger transition-colors cursor-pointer"
              >
                <Trash2 size={12} />
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

/* -- Cloud instance card -- */

function CloudInstanceCard({
  data,
  syncProgress,
  onSync,
}: {
  data: SharedInstanceData;
  syncProgress: { message: string; percent: number } | null;
  onSync: () => void;
}) {
  const loaderMeta = LOADER_META[data.loader] ?? LOADER_META.vanilla;
  const LoaderIcon = loaderMeta.icon;
  const letter = data.name[0]?.toUpperCase() ?? "?";
  const isSyncing = syncProgress !== null;

  return (
    <div className="group relative rounded-panel bg-(--color-surface-secondary) border border-dashed border-(--color-border) overflow-hidden opacity-60 hover:opacity-85 transition-all duration-200">
      <div className="p-5">
        {/* Instance info */}
        <div className="flex items-start gap-3.5">
          <div
            className="w-12 h-12 rounded-card flex items-center justify-center text-base font-strong flex-shrink-0 grayscale"
            style={{
              backgroundColor: data.icon_color + "15",
              color: data.icon_color,
            }}
          >
            {letter}
          </div>
          <div className="flex-1 min-w-0">
            <h3 className="text-sm font-strong text-(--color-text-primary) truncate">
              {data.name}
            </h3>
            <div className="flex items-center gap-1.5 mt-1 flex-wrap">
              <span className="text-caption font-body text-(--color-text-secondary) bg-(--color-surface-tertiary) px-2 py-0.5 rounded-md">
                {data.mc_version}
              </span>
              <span
                className="text-micro px-2 py-0.5 rounded-md font-emphasis flex items-center gap-1"
                style={{
                  backgroundColor: loaderMeta.color + "12",
                  color: loaderMeta.color,
                }}
              >
                <LoaderIcon size={9} />
                {loaderMeta.label}
              </span>
              <span className="text-micro px-2 py-0.5 rounded-md font-emphasis flex items-center gap-1 bg-(--color-text-secondary)/10 text-(--color-text-secondary)">
                <Cloud size={8} />
                Cloud
              </span>
            </div>
          </div>
        </div>

        {/* Meta */}
        <div className="flex items-center gap-3.5 mt-3 text-caption text-(--color-text-secondary)">
          <span className="flex items-center gap-1">
            <Layers size={10} />
            {data.mods.length} mod{data.mods.length !== 1 ? "s" : ""}
          </span>
          <span className="flex items-center gap-1">
            <Globe size={10} />
            {data.owner.mc_username}
          </span>
        </div>

        {/* Sync progress */}
        {isSyncing && (
          <div className="mt-3 space-y-1.5">
            <div className="flex items-center justify-between text-caption">
              <span className="font-body text-(--color-accent) truncate">
                {syncProgress.message}
              </span>
              <span className="text-(--color-text-secondary) ml-2 flex-shrink-0">
                {syncProgress.percent}%
              </span>
            </div>
            <div className="w-full h-1 rounded-pill bg-(--color-surface-tertiary) overflow-hidden">
              <div
                className="h-full rounded-pill transition-all duration-300 bg-(--color-accent)"
                style={{ width: `${syncProgress.percent}%` }}
              />
            </div>
          </div>
        )}

        {/* Action */}
        <div className="flex items-center gap-2 mt-4">
          <button
            onClick={onSync}
            disabled={isSyncing}
            className="flex-1 flex items-center justify-center gap-2 py-2.5 rounded-card text-fg-on-accent text-xs font-strong transition-all cursor-pointer hover:shadow-md disabled:opacity-50 disabled:cursor-default bg-(--color-accent) hover:bg-(--color-accent-hover) shadow-sm shadow-(--color-accent)/20"
          >
            {isSyncing ? (
              <>
                <Loader2 size={12} className="animate-spin" />
                Installing...
              </>
            ) : (
              <>
                <Download size={12} />
                Install to this device
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}
