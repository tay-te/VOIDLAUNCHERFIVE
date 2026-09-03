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
  const { installs: store } = useStore();

  if (!store.toastVisible || !store.activeJob) return null;

  const job = store.activeJob;

  return (
    <div className="fixed bottom-6 right-6 z-[300] w-80 download-toast-enter">
      <div className="glass rounded-panel overflow-hidden shadow-2xl shadow-black/20">
        {/* Header */}
        <div className="flex items-center gap-3 px-4 pt-4 pb-2">
          <div
            className="w-9 h-9 rounded-card flex items-center justify-center text-sm font-display flex-shrink-0"
            style={{
              backgroundColor: job.iconColor + "15",
              color: job.iconColor,
            }}
          >
            {(job.instanceName || job.title)[0]?.toUpperCase() ?? "?"}
          </div>

          <div className="flex-1 min-w-0">
            <p className="text-xs font-strong text-(--color-text-primary) truncate">
              {job.title}
            </p>
            <p className="text-micro text-(--color-text-secondary) truncate mt-0.5">
              {job.status === "done"
                ? job.subtitle
                : job.status === "error"
                ? job.error ?? "Install failed"
                : job.currentItem}
            </p>
          </div>

          {/* Status icon */}
          {job.status === "running" && (
            <Loader2
              size={16}
              className="animate-spin flex-shrink-0"
              style={{ color: job.iconColor }}
            />
          )}
          {job.status === "done" && (
            <CheckCircle2
              size={16}
              className="text-success flex-shrink-0"
            />
          )}
          {job.status === "error" && (
            <AlertTriangle
              size={16}
              className="text-danger flex-shrink-0"
            />
          )}

          <button
            onClick={() => store.dismissToast()}
            className="w-6 h-6 rounded-pill flex items-center justify-center text-(--color-text-secondary) hover:bg-(--color-surface-tertiary) transition-colors cursor-pointer flex-shrink-0"
          >
            <X size={12} />
          </button>
        </div>

        {/* Progress bar */}
        <div className="px-4 pb-4 pt-2">
          <div className="w-full h-1.5 rounded-pill bg-(--color-surface-tertiary) overflow-hidden">
            <div
              className="h-full rounded-pill transition-all duration-500 ease-out"
              style={{
                width: `${job.status === "done" ? 100 : job.percent}%`,
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
            <span className="text-micro text-(--color-text-secondary) flex items-center gap-1">
              <Package size={9} />
              {job.completedItems}/{job.totalItems} steps
            </span>
            <span
              className="text-micro font-strong"
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
                : `${job.percent}%`}
            </span>
          </div>
        </div>
      </div>
    </div>
  );
});
