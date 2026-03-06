import { useState } from "react";
import { observer } from "mobx-react-lite";
import { useStore } from "../stores";
import {
  Bell,
  Download,
  X,
  Package,
  Box,
  Zap,
  Hammer,
  Clock,
  CheckCircle2,
  Loader2,
} from "lucide-react";
import type { ShareNotification } from "../stores/NotificationStore";
import { getVersion } from "../api/modrinth";
import { supabase } from "../api/supabase";

const LOADER_META: Record<string, { label: string; color: string; icon: typeof Box }> = {
  vanilla: { label: "Vanilla", color: "#22c55e", icon: Box },
  fabric: { label: "Fabric", color: "#dba678", icon: Zap },
  forge: { label: "Forge", color: "#3b82f6", icon: Hammer },
};

function timeAgo(dateStr: string): string {
  const seconds = Math.floor((Date.now() - new Date(dateStr).getTime()) / 1000);
  if (seconds < 60) return "just now";
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  return `${days}d ago`;
}

interface Props {
  onNavigateInstances?: () => void;
}

export const NotificationTray = observer(({ onNavigateInstances }: Props) => {
  const { notifications: store, instances: instanceStore, sharing } = useStore();
  const [open, setOpen] = useState(false);
  const [downloadingId, setDownloadingId] = useState<string | null>(null);

  const handleDownload = async (notification: ShareNotification) => {
    const inst = notification.sharedInstance;
    setDownloadingId(notification.id);

    // Mark accepted
    await store.markAccepted(notification.id);

    // Fetch the mods for this shared instance
    const { data: mods } = await supabase
      .from("shared_instance_mods")
      .select("project_id, version_id, title, icon_url, filename")
      .eq("instance_id", inst.id);

    const modList = mods ?? [];

    // Start toast
    store.startDownload({
      id: notification.id,
      instanceName: inst.name,
      iconColor: inst.icon_color,
      totalMods: modList.length,
      downloadedMods: 0,
      currentMod: modList.length > 0 ? `Preparing ${modList[0].title}...` : "Creating instance...",
    });

    // Create local instance
    const localInstance = instanceStore.createFromShared({
      name: inst.name,
      version: inst.mc_version,
      loader: inst.loader as "vanilla" | "fabric" | "forge",
      iconColor: inst.icon_color,
      sharedInstanceId: inst.id,
      shareCode: inst.share_code,
      isCollaborative: inst.is_collaborative,
      mods: [],
    });

    if (!localInstance) {
      store.failDownload("Failed to create instance");
      setDownloadingId(null);
      return;
    }

    // Join as collaborator if collaborative
    if (inst.is_collaborative) {
      await sharing.joinAsCollaborator(inst.id);
    }

    // Download each mod
    try {
      for (let i = 0; i < modList.length; i++) {
        const mod = modList[i];
        store.updateDownloadProgress(i, `Downloading ${mod.title}...`);

        try {
          const version = await getVersion(mod.version_id);
          const primaryFile = version.files.find((f) => f.primary) ?? version.files[0];

          if (primaryFile) {
            const result = await window.electronAPI.downloadMod({
              instanceId: localInstance.id,
              url: primaryFile.url,
              filename: primaryFile.filename,
            });

            if (result.success) {
              instanceStore.addMod(localInstance.id, {
                projectId: mod.project_id,
                versionId: mod.version_id,
                filename: primaryFile.filename,
                title: mod.title,
                iconUrl: mod.icon_url,
              });
            }
          }
        } catch {
          // skip failed mod
        }
      }

      store.updateDownloadProgress(modList.length, "Complete!");
      store.finishDownload();
    } catch {
      store.failDownload("Download failed");
    }

    setDownloadingId(null);
    setOpen(false);
  };

  const pendingNotifications = store.notifications.filter((n) => n.status === "pending");
  const recentAccepted = store.notifications.filter((n) => n.status === "accepted").slice(0, 3);

  return (
    <div className="relative">
      {/* Bell button */}
      <button
        onClick={() => setOpen(!open)}
        title="Notifications"
        className={`no-drag relative flex items-center justify-center w-10 h-10 rounded-xl transition-all duration-200 cursor-pointer ${
          open
            ? "bg-(--color-accent)/12 text-(--color-accent)"
            : "text-(--color-text-secondary) hover:text-(--color-text-primary) hover:bg-(--color-surface-tertiary)"
        }`}
      >
        <Bell size={20} strokeWidth={open ? 2.5 : 1.75} />
        {store.unreadCount > 0 && (
          <div className="absolute -top-0.5 -right-0.5 w-4.5 h-4.5 rounded-full bg-red-500 flex items-center justify-center shadow-sm">
            <span className="text-[9px] font-bold text-white leading-none">
              {store.unreadCount > 9 ? "9+" : store.unreadCount}
            </span>
          </div>
        )}
      </button>

      {/* Tray dropdown */}
      {open && (
        <>
          {/* Backdrop */}
          <div
            className="fixed inset-0 z-[90]"
            onClick={() => setOpen(false)}
          />

          <div className="absolute left-full top-0 ml-2 w-80 z-[100] notification-tray-enter">
            <div className="glass rounded-2xl overflow-hidden shadow-2xl shadow-black/20">
              {/* Header */}
              <div className="px-5 py-4 border-b border-(--color-border)/50">
                <div className="flex items-center justify-between">
                  <h3 className="text-sm font-black text-(--color-text-primary) tracking-tight">
                    Notifications
                  </h3>
                  {store.unreadCount > 0 && (
                    <span className="text-[10px] px-2 py-0.5 rounded-full bg-(--color-accent)/10 text-(--color-accent) font-bold">
                      {store.unreadCount} new
                    </span>
                  )}
                </div>
              </div>

              {/* Content */}
              <div className="max-h-[400px] overflow-y-auto">
                {pendingNotifications.length === 0 && recentAccepted.length === 0 ? (
                  <div className="flex flex-col items-center justify-center py-12 text-(--color-text-secondary)">
                    <Bell size={24} className="mb-3 opacity-30" />
                    <p className="text-xs font-medium">No notifications</p>
                    <p className="text-[11px] mt-0.5 opacity-70">
                      Shares from friends will appear here
                    </p>
                  </div>
                ) : (
                  <div className="divide-y divide-(--color-border)/30">
                    {pendingNotifications.map((notif) => (
                      <NotificationRow
                        key={notif.id}
                        notification={notif}
                        isDownloading={downloadingId === notif.id}
                        onDownload={() => handleDownload(notif)}
                        onDismiss={() => store.dismiss(notif.id)}
                      />
                    ))}
                    {recentAccepted.map((notif) => (
                      <NotificationRow
                        key={notif.id}
                        notification={notif}
                        isDownloading={false}
                        onDownload={() => {}}
                        onDismiss={() => store.dismiss(notif.id)}
                      />
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>
        </>
      )}
    </div>
  );
});

function NotificationRow({
  notification,
  isDownloading,
  onDownload,
  onDismiss,
}: {
  notification: ShareNotification;
  isDownloading: boolean;
  onDownload: () => void;
  onDismiss: () => void;
}) {
  const inst = notification.sharedInstance;
  const loaderMeta = LOADER_META[inst.loader] ?? LOADER_META.vanilla;
  const LoaderIcon = loaderMeta.icon;
  const isPending = notification.status === "pending";

  return (
    <div className={`p-4 transition-all ${isPending ? "bg-(--color-accent)/3" : ""}`}>
      <div className="flex items-start gap-3">
        <div
          className="w-10 h-10 rounded-xl flex items-center justify-center text-sm font-black flex-shrink-0"
          style={{
            backgroundColor: inst.icon_color + "15",
            color: inst.icon_color,
          }}
        >
          {inst.name[0]?.toUpperCase() ?? "?"}
        </div>

        <div className="flex-1 min-w-0">
          <p className="text-xs text-(--color-text-primary)">
            <span className="font-bold">{notification.sender.mc_username}</span>
            {" shared "}
            <span className="font-bold">{inst.name}</span>
          </p>
          <div className="flex items-center gap-2 mt-1.5">
            <span className="text-[10px] font-medium text-(--color-text-secondary) bg-(--color-surface-tertiary)/60 px-1.5 py-0.5 rounded">
              {inst.mc_version}
            </span>
            <span
              className="text-[9px] px-1.5 py-0.5 rounded font-bold flex items-center gap-0.5"
              style={{
                backgroundColor: loaderMeta.color + "12",
                color: loaderMeta.color,
              }}
            >
              <LoaderIcon size={8} />
              {loaderMeta.label}
            </span>
            <span className="text-[10px] text-(--color-text-secondary) flex items-center gap-1">
              <Clock size={9} />
              {timeAgo(notification.created_at)}
            </span>
          </div>

          {/* Actions */}
          {isPending && (
            <div className="flex items-center gap-2 mt-3">
              <button
                onClick={onDownload}
                disabled={isDownloading}
                className="flex items-center gap-1.5 px-4 py-2 rounded-xl text-[11px] font-bold text-white transition-all cursor-pointer disabled:opacity-60"
                style={{ backgroundColor: inst.icon_color }}
              >
                {isDownloading ? (
                  <>
                    <Loader2 size={11} className="animate-spin" />
                    Downloading...
                  </>
                ) : (
                  <>
                    <Download size={11} />
                    Download
                  </>
                )}
              </button>
              <button
                onClick={onDismiss}
                disabled={isDownloading}
                className="px-3 py-2 rounded-xl text-[11px] text-(--color-text-secondary) hover:bg-(--color-surface-tertiary) transition-colors cursor-pointer"
              >
                Dismiss
              </button>
            </div>
          )}

          {!isPending && (
            <div className="flex items-center gap-1.5 mt-2 text-green-500">
              <CheckCircle2 size={12} />
              <span className="text-[11px] font-semibold">Downloaded</span>
            </div>
          )}
        </div>

        {/* Dismiss X for accepted */}
        {!isPending && (
          <button
            onClick={onDismiss}
            className="w-6 h-6 rounded-full flex items-center justify-center text-(--color-text-secondary) hover:bg-(--color-surface-tertiary) transition-colors cursor-pointer flex-shrink-0"
          >
            <X size={12} />
          </button>
        )}
      </div>
    </div>
  );
}
