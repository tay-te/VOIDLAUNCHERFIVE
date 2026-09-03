import { observer } from "mobx-react-lite";
import { useStore } from "../stores";
import { Sun, Moon, Monitor, Palette, Zap, RefreshCw, Download, CheckCircle, ScrollText, Tag, Loader2 } from "lucide-react";
import type { Theme } from "../stores/ThemeStore";
import type { ThemeIconKey } from "../styles/themeRegistry";
import { useEffect, useMemo, useRef, useState } from "react";
import { marked } from "marked";
import DOMPurify from "dompurify";

type UpdateStatus = "idle" | "checking" | "up-to-date" | "available" | "downloaded" | "error";

/** Maps the registry's icon keys to Lucide components. */
const THEME_ICONS: Record<ThemeIconKey, typeof Sun> = {
  sun: Sun,
  moon: Moon,
  monitor: Monitor,
  palette: Palette,
};

interface GithubRelease {
  tag_name: string;
  name: string;
  body: string;
  published_at: string;
  html_url: string;
  draft: boolean;
  prerelease: boolean;
}

export const SettingsPage = observer(() => {
  const { theme } = useStore();
  const [version, setVersion] = useState("dev");
  const [updateStatus, setUpdateStatus] = useState<UpdateStatus>("idle");
  const checkingRef = useRef(false);
  const platformRef = useRef("unknown");
  const [platform, setPlatform] = useState("unknown");
  const [releases, setReleases] = useState<GithubRelease[]>([]);
  const [loadingReleases, setLoadingReleases] = useState(true);

  useEffect(() => {
    window.electronAPI?.getAppVersion().then(setVersion).catch(() => {});
    window.electronAPI?.getPlatform().then((value) => {
      platformRef.current = value;
      setPlatform(value);
    }).catch(() => {});
  }, []);

  useEffect(() => {
    fetch("https://api.github.com/repos/tay-te/VOIDLAUNCHERFIVE/releases?per_page=25")
      .then((res) => res.json())
      .then((data: GithubRelease[]) => {
        setReleases(data.filter((r) => !r.draft && !r.prerelease));
        setLoadingReleases(false);
      })
      .catch(() => setLoadingReleases(false));
  }, []);

  useEffect(() => {
    const api = window.electronAPI;
    if (!api) return;

    const unsubs = [
      api.onCheckingForUpdate(() => {
        if (checkingRef.current) setUpdateStatus("checking");
      }),
      api.onUpdateNotAvailable(() => {
        if (checkingRef.current) {
          setUpdateStatus("up-to-date");
          checkingRef.current = false;
        }
      }),
      api.onUpdateAvailable(() => {
        if (checkingRef.current) {
          setUpdateStatus("available");
          if (platformRef.current === "darwin") {
            checkingRef.current = false;
          }
        }
      }),
      api.onUpdateDownloaded(() => {
        if (checkingRef.current) {
          setUpdateStatus("downloaded");
          checkingRef.current = false;
        }
      }),
      api.onUpdateError(() => {
        if (checkingRef.current) {
          setUpdateStatus("error");
          checkingRef.current = false;
        }
      }),
    ];

    return () => unsubs.forEach((fn) => fn());
  }, []);

  const handleCheckForUpdates = async () => {
    checkingRef.current = true;
    setUpdateStatus("checking");
    try {
      const result = await window.electronAPI?.checkForUpdates();
      if (result && !result.success) {
        setUpdateStatus("error");
        checkingRef.current = false;
      }
    } catch {
      setUpdateStatus("error");
      checkingRef.current = false;
    }
  };

  // Rendered from the theme registry, so a newly registered theme shows up in
  // this picker without touching Settings.
  const themeOptions: { value: Theme; label: string; icon: typeof Sun }[] = [
    ...theme.available.map((definition) => ({
      value: definition.id as Theme,
      label: definition.label,
      icon: THEME_ICONS[definition.icon],
    })),
    { value: "system" as Theme, label: "System", icon: Monitor },
  ];
  const isMac = platform === "darwin";

  return (
    <div className="space-y-5 p-6 lg:p-7">
      {/* Theme */}
      <section className="rounded-modal bg-(--color-surface-secondary) border border-(--color-border) p-5 space-y-4">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-control bg-(--color-surface-tertiary) flex items-center justify-center">
            <Sun size={15} className="text-(--color-text-primary)" />
          </div>
          <div>
            <h2 className="text-sm font-strong text-(--color-text-primary)">
              Appearance
            </h2>
            <p className="text-xs text-(--color-text-secondary) mt-0.5">
              Choose how the void looks
            </p>
          </div>
        </div>
        <div className="flex gap-2">
          {themeOptions.map(({ value, label, icon: Icon }) => (
            <button
              key={value}
              onClick={() => theme.setTheme(value)}
              className={`flex items-center gap-2 px-4 py-2 rounded-control text-xs font-emphasis transition-all cursor-pointer ${
                theme.theme === value
                  ? "bg-(--color-surface) text-(--color-text-primary) border border-(--color-border) shadow-sm"
                  : "bg-(--color-surface-tertiary) text-(--color-text-secondary) hover:text-(--color-text-primary)"
              }`}
            >
              <Icon size={14} />
              {label}
            </button>
          ))}
        </div>
      </section>

      {/* Updates */}
      <section className="rounded-modal bg-(--color-surface-secondary) border border-(--color-border) p-5 space-y-4">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-control bg-(--color-surface-tertiary) flex items-center justify-center">
            <Download size={15} className="text-(--color-text-primary)" />
          </div>
          <div>
            <h2 className="text-sm font-strong text-(--color-text-primary)">
              Updates
            </h2>
            <p className="text-xs text-(--color-text-secondary) mt-0.5">
              Keep your launcher fresh
            </p>
          </div>
        </div>
        <div className="flex items-center gap-3">
          <button
            onClick={handleCheckForUpdates}
            disabled={updateStatus === "checking"}
            className={`flex items-center gap-2 px-4 py-2 rounded-control text-xs font-emphasis transition-all cursor-pointer ${
              updateStatus === "checking"
                ? "bg-(--color-surface-tertiary) text-(--color-text-secondary)"
                : "bg-(--color-accent) text-fg-on-accent hover:bg-(--color-accent-hover)"
            }`}
          >
            <RefreshCw
              size={14}
              className={updateStatus === "checking" ? "animate-spin" : ""}
            />
            {updateStatus === "checking" ? "Checking..." : "Check for Updates"}
          </button>
          {updateStatus === "up-to-date" && (
            <span className="text-xs text-success font-body flex items-center gap-1.5 wizard-step-enter">
              <CheckCircle size={13} />
              You're up to date!
            </span>
          )}
          {updateStatus === "available" && (
            <span className="text-xs text-(--color-accent) font-body flex items-center gap-1.5 wizard-step-enter">
              <Download size={13} />
              {isMac ? "Update found. Open the overlay to download." : "Update found! Downloading..."}
            </span>
          )}
          {updateStatus === "downloaded" && (
            <span className="text-xs text-success font-body flex items-center gap-1.5 wizard-step-enter">
              <CheckCircle size={13} />
              Ready to install
            </span>
          )}
          {updateStatus === "error" && (
            <span className="text-xs text-danger font-body wizard-step-enter">
              Failed to check for updates
            </span>
          )}
        </div>
      </section>

      {/* Changelog */}
      <section className="rounded-modal bg-(--color-surface-secondary) border border-(--color-border) p-5 space-y-4">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-control bg-(--color-surface-tertiary) flex items-center justify-center">
            <ScrollText size={15} className="text-(--color-text-primary)" />
          </div>
          <div>
            <h2 className="text-sm font-strong text-(--color-text-primary)">
              What's New
            </h2>
            <p className="text-xs text-(--color-text-secondary) mt-0.5">
              Latest updates and improvements
            </p>
          </div>
        </div>

        {loadingReleases ? (
          <div className="flex items-center justify-center py-8">
            <Loader2 size={16} className="animate-spin text-(--color-accent)" />
          </div>
        ) : releases.length === 0 ? (
          <p className="text-xs text-(--color-text-secondary) text-center py-6">
            No release notes available
          </p>
        ) : (
          <div className="max-h-[400px] overflow-y-auto space-y-3 pr-1 changelog-scroll">
            {releases.map((release) => (
              <ChangelogEntry
                key={release.tag_name}
                release={release}
                isCurrent={`v${version}` === release.tag_name}
              />
            ))}
          </div>
        )}
      </section>

      {/* About */}
      <section className="rounded-modal bg-(--color-surface-secondary) border border-(--color-border) p-5 space-y-4">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-control bg-(--color-surface-tertiary) flex items-center justify-center">
            <Zap size={15} className="text-(--color-text-primary)" />
          </div>
          <div>
            <h2 className="text-sm font-strong text-(--color-text-primary)">
              About
            </h2>
            <p className="text-xs text-(--color-text-secondary) mt-0.5">
              The engine behind it all
            </p>
          </div>
        </div>
        <div className="p-4 rounded-control bg-(--color-surface-tertiary) space-y-1.5">
          <p className="text-sm font-strong text-(--color-text-primary)">
            <span className="void-text">VOID</span>{" "}
            <span className="text-(--color-text-secondary) font-normal text-xs">
              v{version}
            </span>
          </p>
          <p className="text-xs text-(--color-text-secondary) leading-relaxed">
            A modern Minecraft modding client for exploring, installing, and
            managing mods from the Modrinth ecosystem.
          </p>
        </div>
      </section>
    </div>
  );
});

/* -- Changelog entry -- */

function formatDate(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
}

function ChangelogEntry({ release, isCurrent }: { release: GithubRelease; isCurrent: boolean }) {
  const rendered = useMemo(() => {
    if (!release.body) return "";
    const raw = marked.parse(release.body, { async: false }) as string;
    return DOMPurify.sanitize(raw);
  }, [release.body]);

  return (
    <div
      className={`rounded-control border p-4 space-y-2 transition-colors ${
        isCurrent
          ? "border-(--color-accent)/25 bg-(--color-accent)/4"
          : "border-(--color-border) bg-(--color-surface-tertiary)/50"
      }`}
    >
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Tag size={11} className={isCurrent ? "text-(--color-accent)" : "text-(--color-text-secondary)"} />
          <span className={`text-xs font-strong ${isCurrent ? "text-(--color-accent)" : "text-(--color-text-primary)"}`}>
            {release.tag_name}
          </span>
          {isCurrent && (
            <span className="text-micro font-emphasis px-1.5 py-0.5 rounded-md bg-(--color-accent)/15 text-(--color-accent)">
              Current
            </span>
          )}
        </div>
        <span className="text-caption text-(--color-text-secondary)">
          {formatDate(release.published_at)}
        </span>
      </div>
      {rendered && (
        <div
          className="changelog-body text-xs text-(--color-text-secondary) leading-relaxed"
          dangerouslySetInnerHTML={{ __html: rendered }}
        />
      )}
    </div>
  );
}
