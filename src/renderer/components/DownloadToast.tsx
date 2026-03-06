import { observer } from "mobx-react-lite";
import { useStore } from "../stores";
import {
  X,
  Download,
  CheckCircle2,
  AlertTriangle,
  Loader2,
  Package,
} from "lucide-react";

export const DownloadToast = observer(() => {
  const { notifications: store } = useStore();

  if (!store.toastVisible || !store.activeDownload) return null;

  const job = store.activeDownload;
  const progress =
    job.totalMods > 0
      ? Math.round((job.downloadedMods / job.totalMods) * 100)
      : 0;

  return (
    <div className="fixed bottom-6 right-6 z-[300] w-80 download-toast-enter">
      <div className="glass rounded-2xl overflow-hidden shadow-2xl shadow-black/20">
        {/* Header */}
        <div className="flex items-center gap-3 px-4 pt-4 pb-2">
          <div
            className="w-9 h-9 rounded-xl flex items-center justify-center text-sm font-black flex-shrink-0"
            style={{
              backgroundColor: job.iconColor + "15",
              color: job.iconColor,
            }}
          >
            {job.instanceName[0]?.toUpperCase() ?? "?"}
          </div>

          <div className="flex-1 min-w-0">
            <p className="text-xs font-bold text-(--color-text-primary) truncate">
              {job.instanceName}
            </p>
            <p className="text-[10px] text-(--color-text-secondary) truncate mt-0.5">
              {job.status === "done"
                ? "Download complete"
                : job.status === "error"
                ? job.error ?? "Download failed"
                : job.currentMod}
            </p>
          </div>

          {/* Status icon */}
          {job.status === "downloading" && (
            <Loader2
              size={16}
              className="animate-spin flex-shrink-0"
              style={{ color: job.iconColor }}
            />
          )}
          {job.status === "done" && (
            <CheckCircle2
              size={16}
              className="text-green-500 flex-shrink-0"
            />
          )}
          {job.status === "error" && (
            <AlertTriangle
              size={16}
              className="text-red-500 flex-shrink-0"
            />
          )}

          <button
            onClick={() => store.dismissToast()}
            className="w-6 h-6 rounded-full flex items-center justify-center text-(--color-text-secondary) hover:bg-(--color-surface-tertiary) transition-colors cursor-pointer flex-shrink-0"
          >
            <X size={12} />
          </button>
        </div>

        {/* Progress bar */}
        <div className="px-4 pb-4 pt-2">
          <div className="w-full h-1.5 rounded-full bg-(--color-surface-tertiary) overflow-hidden">
            <div
              className="h-full rounded-full transition-all duration-500 ease-out"
              style={{
                width: `${job.status === "done" ? 100 : progress}%`,
                backgroundColor:
                  job.status === "error"
                    ? "#ef4444"
                    : job.status === "done"
                    ? "#22c55e"
                    : job.iconColor,
              }}
            />
          </div>

          <div className="flex items-center justify-between mt-2">
            <span className="text-[10px] text-(--color-text-secondary) flex items-center gap-1">
              <Package size={9} />
              {job.downloadedMods}/{job.totalMods} mods
            </span>
            <span
              className="text-[10px] font-bold"
              style={{
                color:
                  job.status === "error"
                    ? "#ef4444"
                    : job.status === "done"
                    ? "#22c55e"
                    : job.iconColor,
              }}
            >
              {job.status === "done"
                ? "Complete"
                : job.status === "error"
                ? "Failed"
                : `${progress}%`}
            </span>
          </div>
        </div>
      </div>
    </div>
  );
});
