import { useState } from "react";
import { observer } from "mobx-react-lite";
import { useStore } from "../stores";
import {
  Layers,
  Plus,
  Play,
  Trash2,
  Pencil,
  Box,
  Zap,
  Hammer,
  Clock,
  Calendar,
  Rocket,
} from "lucide-react";
import { CreateInstanceWizard } from "./CreateInstanceWizard";
import { InstanceDetailPage } from "./InstanceDetailPage";
import type { Instance } from "../stores/InstanceStore";

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

interface InstancesPageProps {
  onNavigate?: (page: string) => void;
}

export const InstancesPage = observer(({ onNavigate }: InstancesPageProps) => {
  const { instances: store } = useStore();
  const [showWizard, setShowWizard] = useState(false);
  const [deleteConfirm, setDeleteConfirm] = useState<string | null>(null);
  const [selectedInstanceId, setSelectedInstanceId] = useState<string | null>(
    null
  );

  const handleCreate = (data: {
    name: string;
    version: string;
    loader: "vanilla" | "fabric" | "forge";
    iconColor: string;
  }) => {
    store.create(data);
    setShowWizard(false);
  };

  if (selectedInstanceId) {
    return (
      <InstanceDetailPage
        instanceId={selectedInstanceId}
        onBack={() => setSelectedInstanceId(null)}
        onBrowseMods={() => {
          setSelectedInstanceId(null);
          onNavigate?.("browse");
        }}
      />
    );
  }

  return (
    <div className="p-8 space-y-5">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-black tracking-tight text-(--color-text-primary)">
            Your <span className="void-text">Worlds</span>
          </h1>
          <p className="text-sm text-(--color-text-secondary) mt-1">
            Craft, configure, and launch your Minecraft instances
          </p>
        </div>
        {store.instances.length > 0 && (
          <button
            onClick={() => setShowWizard(true)}
            className="flex items-center gap-2 px-5 py-2.5 rounded-xl bg-(--color-accent) hover:bg-(--color-accent-hover) text-white text-sm font-semibold transition-all cursor-pointer shadow-sm shadow-(--color-accent)/20"
          >
            <Plus size={15} />
            New Instance
          </button>
        )}
      </div>

      {/* Content */}
      {store.instances.length === 0 ? (
        <EmptyState onCreateClick={() => setShowWizard(true)} />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-3">
          {store.instances.map((inst) => (
            <InstanceCard
              key={inst.id}
              instance={inst}
              isDeleting={deleteConfirm === inst.id}
              onClick={() => setSelectedInstanceId(inst.id)}
              onLaunch={() => store.launch(inst.id)}
              onDeleteRequest={() => setDeleteConfirm(inst.id)}
              onDeleteConfirm={() => {
                store.remove(inst.id);
                setDeleteConfirm(null);
              }}
              onDeleteCancel={() => setDeleteConfirm(null)}
            />
          ))}
        </div>
      )}

      {/* Wizard overlay */}
      {showWizard && (
        <CreateInstanceWizard
          onClose={() => setShowWizard(false)}
          onCreate={handleCreate}
        />
      )}
    </div>
  );
});

/* -- Empty state -- */

function EmptyState({ onCreateClick }: { onCreateClick: () => void }) {
  return (
    <div className="flex flex-col items-center justify-center py-20">
      <div className="relative">
        <div className="w-20 h-20 rounded-2xl bg-(--color-accent)/10 flex items-center justify-center mb-5">
          <Rocket
            size={32}
            strokeWidth={1.5}
            className="text-(--color-accent)"
          />
        </div>
        <div className="absolute -top-1 -right-1 w-7 h-7 rounded-lg bg-(--color-accent)/15 flex items-center justify-center animate-pulse">
          <Plus size={12} className="text-(--color-accent)" />
        </div>
      </div>
      <h3 className="text-lg font-bold text-(--color-text-primary)">
        The void is empty
      </h3>
      <p className="text-sm text-(--color-text-secondary) mt-1 max-w-xs text-center">
        Create your first instance to start building your modded Minecraft
        universe.
      </p>
      <button
        onClick={onCreateClick}
        className="mt-5 flex items-center gap-2 px-5 py-2.5 rounded-xl bg-(--color-accent) hover:bg-(--color-accent-hover) text-white text-sm font-semibold transition-all cursor-pointer shadow-sm shadow-(--color-accent)/20"
      >
        <Plus size={15} />
        Create your first instance
      </button>
    </div>
  );
}

/* -- Instance card -- */

function InstanceCard({
  instance,
  isDeleting,
  onClick,
  onLaunch,
  onDeleteRequest,
  onDeleteConfirm,
  onDeleteCancel,
}: {
  instance: Instance;
  isDeleting: boolean;
  onClick: () => void;
  onLaunch: () => void;
  onDeleteRequest: () => void;
  onDeleteConfirm: () => void;
  onDeleteCancel: () => void;
}) {
  const loaderMeta = LOADER_META[instance.loader] ?? LOADER_META.vanilla;
  const LoaderIcon = loaderMeta.icon;
  const letter = instance.name[0]?.toUpperCase() ?? "?";

  return (
    <div
      className="group relative rounded-2xl bg-(--color-surface-secondary) border border-(--color-border) overflow-hidden hover:border-(--color-accent)/25 hover:shadow-md transition-all duration-200 cursor-pointer"
      onClick={onClick}
    >
      <div className="p-4">
        {/* Instance info */}
        <div className="flex items-start gap-3.5">
          <div
            className="w-12 h-12 rounded-xl flex items-center justify-center text-base font-bold flex-shrink-0"
            style={{
              backgroundColor: instance.iconColor + "15",
              color: instance.iconColor,
            }}
          >
            {letter}
          </div>
          <div className="flex-1 min-w-0">
            <h3 className="text-sm font-bold text-(--color-text-primary) truncate">
              {instance.name}
            </h3>
            <div className="flex items-center gap-1.5 mt-1 flex-wrap">
              <span className="text-[11px] font-medium text-(--color-text-secondary) bg-(--color-surface-tertiary) px-2 py-0.5 rounded-md">
                {instance.version}
              </span>
              <span
                className="text-[10px] px-2 py-0.5 rounded-md font-semibold flex items-center gap-1"
                style={{
                  backgroundColor: loaderMeta.color + "12",
                  color: loaderMeta.color,
                }}
              >
                <LoaderIcon size={9} />
                {loaderMeta.label}
              </span>
            </div>
          </div>
        </div>

        {/* Meta */}
        <div className="flex items-center gap-3.5 mt-3 text-[11px] text-(--color-text-secondary)">
          <span className="flex items-center gap-1">
            <Layers size={10} />
            {instance.installedMods?.length ?? instance.modCount} mod{(instance.installedMods?.length ?? instance.modCount) !== 1 ? "s" : ""}
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

        {/* Actions */}
        <div
          className="flex items-center gap-2 mt-3"
          onClick={(e) => e.stopPropagation()}
        >
          {isDeleting ? (
            <>
              <span className="text-xs text-red-500 font-medium flex-1">
                Delete this instance?
              </span>
              <button
                onClick={onDeleteCancel}
                className="px-3 py-1.5 rounded-lg text-xs text-(--color-text-secondary) hover:bg-(--color-surface-tertiary) transition-colors cursor-pointer"
              >
                Cancel
              </button>
              <button
                onClick={onDeleteConfirm}
                className="px-3 py-1.5 rounded-lg bg-red-500 hover:bg-red-600 text-white text-xs font-medium transition-colors cursor-pointer"
              >
                Delete
              </button>
            </>
          ) : (
            <>
              <button
                onClick={onLaunch}
                className="flex-1 flex items-center justify-center gap-2 py-2.5 rounded-xl text-white text-xs font-bold transition-all cursor-pointer hover:shadow-md"
                style={{
                  backgroundColor: instance.iconColor,
                  boxShadow: `0 2px 8px ${instance.iconColor}25`,
                }}
              >
                <Play size={12} fill="currentColor" />
                Launch
              </button>
              <button className="w-8 h-8 rounded-lg flex items-center justify-center hover:bg-(--color-surface-tertiary) text-(--color-text-secondary) transition-colors cursor-pointer">
                <Pencil size={12} />
              </button>
              <button
                onClick={onDeleteRequest}
                className="w-8 h-8 rounded-lg flex items-center justify-center hover:bg-red-500/10 text-(--color-text-secondary) hover:text-red-500 transition-colors cursor-pointer"
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
