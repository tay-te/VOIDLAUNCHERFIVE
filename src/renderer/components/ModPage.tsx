import { useEffect, useMemo, useState } from "react";
import { observer } from "mobx-react-lite";
import { useStore } from "../stores";
import { marked } from "marked";
import DOMPurify from "dompurify";
import {
  ArrowLeft,
  Download,
  Heart,
  Calendar,
  Tag,
  ExternalLink,
  FileDown,
  Sparkles,
  Loader2,
} from "lucide-react";
import { ModInstallModal } from "./ModInstallModal";

interface ModPageProps {
  modId: string;
  onBack: () => void;
}

export const ModPage = observer(({ modId, onBack }: ModPageProps) => {
  const { mods } = useStore();
  const mod = mods.selectedMod;
  const versions = mods.selectedModVersions;
  const [showInstallModal, setShowInstallModal] = useState(false);

  useEffect(() => {
    mods.selectMod(modId);
    return () => mods.clearSelectedMod();
  }, [modId]);

  const renderedBody = useMemo(() => {
    if (!mod?.body) return "";
    const raw = marked.parse(mod.body, { async: false }) as string;
    return DOMPurify.sanitize(raw);
  }, [mod?.body]);

  // Full loading state - no cached preview available
  if (mods.detailLoading && !mod) {
    return (
      <div className="p-6 space-y-6">
        <button
          onClick={onBack}
          className="w-10 h-10 rounded-full glass-subtle flex items-center justify-center text-(--color-text-secondary) hover:text-(--color-text-primary) transition-colors cursor-pointer"
        >
          <ArrowLeft size={16} />
        </button>
        <div className="space-y-4">
          <div className="flex items-start gap-5">
            <div className="w-20 h-20 rounded-2xl glass-subtle animate-pulse" />
            <div className="flex-1 space-y-3">
              <div className="h-7 w-64 rounded-full glass-subtle animate-pulse" />
              <div className="h-4 w-96 rounded-full glass-subtle animate-pulse" />
            </div>
          </div>
          <div className="h-96 rounded-2xl glass-subtle animate-pulse" />
        </div>
      </div>
    );
  }

  if (mods.detailError && !mod) {
    return (
      <div className="p-6 space-y-6">
        <button
          onClick={onBack}
          className="w-10 h-10 rounded-full glass-subtle flex items-center justify-center text-(--color-text-secondary) hover:text-(--color-text-primary) transition-colors cursor-pointer"
        >
          <ArrowLeft size={16} />
        </button>
        <div className="flex flex-col items-center justify-center py-24 text-(--color-text-secondary)">
          <p className="text-lg font-bold text-(--color-text-primary)">
            Failed to load mod
          </p>
          <p className="text-sm mt-1">{mods.detailError}</p>
          <button
            onClick={() => mods.selectMod(modId)}
            className="mt-4 px-5 py-2.5 rounded-full bg-(--color-accent) hover:bg-(--color-accent-hover) text-white text-sm font-semibold cursor-pointer"
          >
            Retry
          </button>
        </div>
      </div>
    );
  }

  if (!mod) {
    // Nothing to show yet and not loading - show skeleton as fallback
    return (
      <div className="p-6 space-y-6">
        <button
          onClick={onBack}
          className="w-10 h-10 rounded-full glass-subtle flex items-center justify-center text-(--color-text-secondary) hover:text-(--color-text-primary) transition-colors cursor-pointer"
        >
          <ArrowLeft size={16} />
        </button>
        <div className="space-y-4">
          <div className="flex items-start gap-5">
            <div className="w-20 h-20 rounded-2xl glass-subtle animate-pulse" />
            <div className="flex-1 space-y-3">
              <div className="h-7 w-64 rounded-full glass-subtle animate-pulse" />
              <div className="h-4 w-96 rounded-full glass-subtle animate-pulse" />
            </div>
          </div>
          <div className="h-96 rounded-2xl glass-subtle animate-pulse" />
        </div>
      </div>
    );
  }

  const latestVersions = versions.slice(0, 10);
  const hasBody = !!mod.body;

  return (
    <div className="p-6 space-y-6">
      <button
        onClick={onBack}
        className="w-10 h-10 rounded-full glass-subtle flex items-center justify-center text-(--color-text-secondary) hover:text-(--color-text-primary) transition-colors cursor-pointer"
      >
        <ArrowLeft size={16} />
      </button>

      {/* Header */}
      <div className="rounded-2xl glass-subtle p-6">
        <div className="flex items-start gap-5">
          {mod.icon_url ? (
            <img
              src={mod.icon_url}
              alt={mod.title}
              className="w-20 h-20 rounded-2xl object-cover flex-shrink-0 shadow-lg ring-1 ring-black/5"
            />
          ) : (
            <div className="w-20 h-20 rounded-2xl bg-(--color-surface-tertiary) flex items-center justify-center text-(--color-text-secondary) flex-shrink-0">
              <Sparkles size={28} />
            </div>
          )}
          <div className="flex-1 min-w-0">
            <h1 className="text-4xl font-black tracking-tighter text-(--color-text-primary)">
              {mod.title}
            </h1>
            <p className="text-base text-(--color-text-secondary) mt-1.5 leading-relaxed">
              {mod.description}
            </p>
            <div className="flex items-center gap-3 mt-3 flex-wrap">
              <span className="flex items-center gap-1.5 text-xs font-semibold text-(--color-text-secondary) bg-(--color-surface-tertiary) px-3.5 py-1.5 rounded-full">
                <Download size={13} />
                {(mod.downloads ?? 0).toLocaleString()}
              </span>
              <span className="flex items-center gap-1.5 text-xs font-semibold text-(--color-text-secondary) bg-(--color-surface-tertiary) px-3.5 py-1.5 rounded-full">
                <Heart size={13} />
                {(mod.follows ?? mod.followers ?? 0).toLocaleString()}
              </span>
              {mod.date_created && (
                <span className="flex items-center gap-1.5 text-xs font-semibold text-(--color-text-secondary) bg-(--color-surface-tertiary) px-3.5 py-1.5 rounded-full">
                  <Calendar size={13} />
                  {new Date(mod.date_created).toLocaleDateString()}
                </span>
              )}
            </div>
            <div className="flex items-center gap-2 mt-3 flex-wrap">
              {(mod.categories ?? []).map((cat) => (
                <span
                  key={cat}
                  className="flex items-center gap-1 text-[11px] font-semibold px-3 py-1 rounded-full bg-(--color-accent)/10 text-(--color-accent)"
                >
                  <Tag size={10} />
                  {cat}
                </span>
              ))}
            </div>
            <button
              onClick={() => setShowInstallModal(true)}
              className="mt-4 flex items-center gap-2 px-7 py-3 rounded-full bg-(--color-accent) hover:bg-(--color-accent-hover) text-white text-base font-bold transition-all cursor-pointer shadow-md shadow-(--color-accent)/20 hover:shadow-lg hover:-translate-y-0.5"
            >
              <Download size={15} />
              Install Mod
            </button>
          </div>
        </div>
      </div>

      {/* Gallery */}
      {mod.gallery && mod.gallery.length > 0 && (
        <div className="flex gap-3 overflow-x-auto pb-2 -mx-6 px-6">
          {mod.gallery.map((img, i) => (
            <img
              key={i}
              src={img.url}
              alt={img.title || `Screenshot ${i + 1}`}
              className="h-48 rounded-2xl object-cover flex-shrink-0 shadow-md ring-1 ring-black/5"
            />
          ))}
        </div>
      )}

      <div className="flex gap-6">
        {/* Markdown body */}
        <div className="flex-1 min-w-0">
          <div className="rounded-2xl glass-subtle p-6 overflow-hidden">
            {hasBody ? (
              <div
                className="mod-description prose"
                dangerouslySetInnerHTML={{ __html: renderedBody }}
              />
            ) : mods.detailLoading ? (
              <div className="flex items-center justify-center py-16 gap-3 text-(--color-text-secondary)">
                <Loader2 size={20} className="animate-spin" />
                <span className="text-sm font-medium">Loading description...</span>
              </div>
            ) : (
              <p className="text-sm text-(--color-text-secondary) py-8 text-center">
                No description available
              </p>
            )}
          </div>
        </div>

        {/* Sidebar - Versions & Links */}
        <div className="w-72 flex-shrink-0 space-y-4">
          {/* External Links */}
          {(mod.source_url ||
            mod.issues_url ||
            mod.wiki_url ||
            mod.discord_url) && (
            <div className="rounded-2xl glass-subtle p-5 space-y-3">
              <h3 className="text-sm font-bold text-(--color-text-secondary) uppercase tracking-wider">
                Links
              </h3>
              {[
                { url: mod.source_url, label: "Source Code" },
                { url: mod.issues_url, label: "Issues" },
                { url: mod.wiki_url, label: "Wiki" },
                { url: mod.discord_url, label: "Discord" },
              ]
                .filter((l) => l.url)
                .map(({ url, label }) => (
                  <a
                    key={label}
                    href={url!}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex items-center gap-2 text-xs font-medium text-(--color-accent) hover:underline"
                  >
                    <ExternalLink size={12} />
                    {label}
                  </a>
                ))}
            </div>
          )}

          {/* Versions */}
          <div className="rounded-2xl glass-subtle p-5 space-y-3">
            <h3 className="text-sm font-bold text-(--color-text-secondary) uppercase tracking-wider">
              Recent Versions
            </h3>
            {mods.detailLoading && latestVersions.length === 0 ? (
              <div className="space-y-3">
                {Array.from({ length: 3 }).map((_, i) => (
                  <div key={i} className="h-24 rounded-2xl glass-subtle animate-pulse" />
                ))}
              </div>
            ) : latestVersions.length === 0 ? (
              <p className="text-xs text-(--color-text-secondary)">
                No versions available
              </p>
            ) : (
              latestVersions.map((v) => (
                <div
                  key={v.id}
                  className="p-3.5 rounded-2xl bg-(--color-surface)/60 border border-(--color-border) space-y-2"
                >
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold text-(--color-text-primary) truncate tracking-tight">
                      {v.version_number}
                    </span>
                    <span className="text-[10px] text-(--color-text-secondary)">
                      {new Date(v.date_published).toLocaleDateString()}
                    </span>
                  </div>
                  <div className="flex flex-wrap gap-1">
                    {v.game_versions.slice(0, 3).map((gv) => (
                      <span
                        key={gv}
                        className="text-[10px] px-2 py-0.5 rounded-full bg-(--color-surface-tertiary) text-(--color-text-secondary) font-medium"
                      >
                        {gv}
                      </span>
                    ))}
                    {v.game_versions.length > 3 && (
                      <span className="text-[10px] px-2 py-0.5 rounded-full bg-(--color-surface-tertiary) text-(--color-text-secondary)">
                        +{v.game_versions.length - 3}
                      </span>
                    )}
                  </div>
                  <div className="flex flex-wrap gap-1">
                    {v.loaders.map((l) => (
                      <span
                        key={l}
                        className="text-[10px] px-2 py-0.5 rounded-full bg-(--color-accent)/10 text-(--color-accent) capitalize font-semibold"
                      >
                        {l}
                      </span>
                    ))}
                  </div>
                  {v.files[0] && (
                    <button
                      onClick={() => setShowInstallModal(true)}
                      className="w-full flex items-center justify-center gap-1.5 py-2 rounded-full bg-(--color-accent) hover:bg-(--color-accent-hover) text-white text-[11px] font-semibold transition-all cursor-pointer shadow-sm shadow-(--color-accent)/15"
                    >
                      <FileDown size={12} />
                      Install
                    </button>
                  )}
                </div>
              ))
            )}
          </div>
        </div>
      </div>

      {showInstallModal && mod && (
        <ModInstallModal
          mod={mod}
          onClose={() => setShowInstallModal(false)}
          onInstalled={() => {}}
        />
      )}
    </div>
  );
});
