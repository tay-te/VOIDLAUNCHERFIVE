import { useState, useEffect, useCallback, useMemo } from "react";
import { CheckCircle } from "lucide-react";

interface DownloadProgress {
  bytesPerSecond: number;
  percent: number;
  transferred: number;
  total: number;
}

type OverlayState = "hidden" | "downloading" | "downloaded";

export function UpdateOverlay() {
  const [state, setState] = useState<OverlayState>("hidden");
  const [progress, setProgress] = useState<DownloadProgress>({
    bytesPerSecond: 0,
    percent: 0,
    transferred: 0,
    total: 0,
  });
  const [dismissed, setDismissed] = useState(false);

  useEffect(() => {
    const api = window.electronAPI;
    if (!api) return;

    const unsubs = [
      api.onUpdateAvailable(() => {
        setState("downloading");
        setDismissed(false);
      }),
      api.onUpdateDownloadProgress((data) => {
        setState("downloading");
        setProgress(data);
        setDismissed(false);
      }),
      api.onUpdateDownloaded(() => {
        setState("downloaded");
      }),
      api.onUpdateError(() => {
        setState("hidden");
      }),
    ];

    return () => unsubs.forEach((fn) => fn());
  }, []);

  const handleInstall = useCallback(() => {
    window.electronAPI?.installUpdate();
  }, []);

  const particles = useMemo(
    () =>
      Array.from({ length: 24 }, () => ({
        size: Math.random() * 2.5 + 0.5,
        left: Math.random() * 100,
        bottom: Math.random() * 10,
        opacity: Math.random() * 0.25 + 0.05,
        drift: (Math.random() - 0.5) * 80,
        duration: Math.random() * 12 + 8,
        delay: Math.random() * -18,
      })),
    []
  );

  if (state === "hidden" || dismissed) return null;

  const formatBytes = (bytes: number) => {
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  };

  const formatSpeed = (bps: number) => {
    if (bps < 1024 * 1024) return `${(bps / 1024).toFixed(0)} KB/s`;
    return `${(bps / (1024 * 1024)).toFixed(1)} MB/s`;
  };

  return (
    <div className="fixed inset-0 z-[9999] flex items-center justify-center update-overlay-enter">
      {/* Background */}
      <div className="absolute inset-0 bg-[#08080c]">
        {/* Gradient orbs */}
        <div
          className="absolute w-[500px] h-[500px] rounded-full blur-[150px] opacity-20 login-orb"
          style={
            {
              background: "var(--color-accent)",
              top: "15%",
              left: "25%",
              "--orb-x": "60px",
              "--orb-y": "-40px",
              animationDuration: "12s",
            } as React.CSSProperties
          }
        />
        <div
          className="absolute w-[400px] h-[400px] rounded-full blur-[120px] opacity-15 login-orb"
          style={
            {
              background: "#a855f7",
              bottom: "15%",
              right: "20%",
              "--orb-x": "-40px",
              "--orb-y": "30px",
              animationDuration: "16s",
              animationDelay: "-4s",
            } as React.CSSProperties
          }
        />

        {/* Floating particles */}
        {particles.map((p, i) => (
          <div
            key={i}
            className="absolute rounded-full bg-white login-particle"
            style={
              {
                width: `${p.size}px`,
                height: `${p.size}px`,
                left: `${p.left}%`,
                bottom: `-${p.bottom}%`,
                "--particle-opacity": `${p.opacity}`,
                "--drift": `${p.drift}px`,
                animationDuration: `${p.duration}s`,
                animationDelay: `${p.delay}s`,
              } as React.CSSProperties
            }
          />
        ))}
      </div>

      {/* Content */}
      <div className="relative z-10 flex flex-col items-center gap-8 max-w-sm w-full px-8">
        {/* VOID logo */}
        <div className="flex flex-col items-center gap-3">
          <h1 className="text-5xl font-black tracking-tight void-text login-title-glow">
            VOID
          </h1>
          <p className="text-white/40 text-xs font-medium tracking-[0.2em] uppercase">
            {state === "downloading" ? "Downloading Update" : "Update Ready"}
          </p>
        </div>

        {state === "downloading" ? (
          <div className="w-full space-y-5 update-content-enter">
            {/* Percentage */}
            <p className="text-center text-white/70 text-4xl font-black tabular-nums tracking-tight">
              {Math.round(progress.percent)}
              <span className="text-lg text-white/30 font-semibold">%</span>
            </p>

            {/* Progress bar */}
            <div className="w-full h-1.5 rounded-full bg-white/[0.06] overflow-hidden">
              <div
                className="h-full rounded-full transition-all duration-500 ease-out update-progress-bar"
                style={{ width: `${Math.max(progress.percent, 2)}%` }}
              />
            </div>

            {/* Stats */}
            <div className="flex justify-between text-[11px] text-white/25 font-medium">
              <span>
                {formatBytes(progress.transferred)} / {formatBytes(progress.total)}
              </span>
              <span>{formatSpeed(progress.bytesPerSecond)}</span>
            </div>
          </div>
        ) : (
          <div className="flex flex-col items-center gap-6 update-content-enter">
            <div className="w-12 h-12 rounded-full bg-emerald-500/10 flex items-center justify-center">
              <CheckCircle size={24} className="text-emerald-400" />
            </div>
            <p className="text-white/50 text-sm font-medium">
              A new version is ready to install
            </p>

            <div className="flex gap-3">
              <button
                onClick={() => setDismissed(true)}
                className="px-5 py-2.5 rounded-xl text-xs font-semibold text-white/50 hover:text-white/70 bg-white/[0.06] hover:bg-white/[0.1] transition-all"
              >
                Later
              </button>
              <button
                onClick={handleInstall}
                className="px-6 py-2.5 rounded-xl text-xs font-bold text-white bg-[var(--color-accent)] hover:brightness-110 transition-all shadow-lg shadow-[var(--color-accent)]/30 update-install-btn"
              >
                Install & Restart
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
