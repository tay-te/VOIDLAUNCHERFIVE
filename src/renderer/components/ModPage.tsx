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
  ChevronDown,
  X,
  Search,
  FileText,
  Box,
} from "lucide-react";
import { ModInstallModal } from "./ModInstallModal";
import type { ModVersion } from "../api/modrinth";

// Loader color config
const LOADER_COLORS: Record<string, { color: string; bg: string }> = {
  fabric:   { color: "#dba678", bg: "#dba67818" },
  quilt:    { color: "#c596f9", bg: "#c596f918" },
  forge:    { color: "#3b82f6", bg: "#3b82f618" },
  neoforge: { color: "#e35935", bg: "#e3593518" },
};

const VERSION_TYPE_STYLES: Record<string, { color: string; bg: string; label: string }> = {
  release: { color: "#22c55e", bg: "#22c55e15", label: "Release" },
  beta:    { color: "#f59e0b", bg: "#f59e0b15", label: "Beta" },
  alpha:   { color: "#ef4444", bg: "#ef444415", label: "Alpha" },
};

interface ModPageProps {
  modId: string;
  onBack: () => void;
}

export const ModPage = observer(({ modId, onBack }: ModPageProps) => {
  const { mods } = useStore();
  const mod = mods.selectedMod;
  const versions = mods.selectedModVersions;
  const [showInstallModal, setShowInstallModal] = useState(false);
  const [activeTab, setActiveTab] = useState<"description" | "versions">("description");

  // Version table filters
  const [versionLoaderFilter, setVersionLoaderFilter] = useState<string[]>([]);
  const [versionMcFilter, setVersionMcFilter] = useState<string[]>([]);
  const [versionTypeFilter, setVersionTypeFilter] = useState<string[]>([]);
  const [versionSearch, setVersionSearch] = useState("");

  useEffect(() => {
    mods.selectMod(modId);
    return () => mods.clearSelectedMod();
  }, [modId]);

  const renderedBody = useMemo(() => {
    if (!mod?.body) return "";
    const raw = marked.parse(mod.body, { async: false }) as string;
    return DOMPurify.sanitize(raw);
  }, [mod?.body]);

  // Collect unique loaders / MC versions from all versions
  const allLoaders = useMemo(() => {
    const set = new Set<string>();
    versions.forEach((v) => v.loaders.forEach((l) => set.add(l.toLowerCase())));
    return Array.from(set).sort();
  }, [versions]);

  const allMcVersions = useMemo(() => {
    const set = new Set<string>();
    versions.forEach((v) => v.game_versions.forEach((gv) => set.add(gv)));
    // Sort descending (newest first)
    return Array.from(set).sort((a, b) => {
      const pa = a.split(".").map(Number);
      const pb = b.split(".").map(Number);
      for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
        const diff = (pb[i] ?? 0) - (pa[i] ?? 0);
        if (diff !== 0) return diff;
      }
      return 0;
    });
  }, [versions]);

  // Filtered versions
  const filteredVersions = useMemo(() => {
    return versions.filter((v) => {
      if (versionLoaderFilter.length > 0 && !v.loaders.some((l) => versionLoaderFilter.includes(l.toLowerCase()))) return false;
      if (versionMcFilter.length > 0 && !v.game_versions.some((gv) => versionMcFilter.includes(gv))) return false;
      if (versionTypeFilter.length > 0 && !versionTypeFilter.includes(v.version_type)) return false;
      if (versionSearch && !v.version_number.toLowerCase().includes(versionSearch.toLowerCase()) && !v.name.toLowerCase().includes(versionSearch.toLowerCase())) return false;
      return true;
    });
  }, [versions, versionLoaderFilter, versionMcFilter, versionTypeFilter, versionSearch]);

  const formatSize = (bytes: number) => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  };

  const formatNumber = (n: number) => {
    if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
    if (n >= 1_000) return `${(n / 1000).toFixed(1)}k`;
    return n.toString();
  };

  // Loading skeleton
  if ((mods.detailLoading && !mod) || (!mod && !mods.detailError)) {
    return (
      <div className="h-full overflow-y-auto p-8 space-y-6">
        <button onClick={onBack} className="w-10 h-10 rounded-full glass-subtle flex items-center justify-center text-(--color-text-secondary) hover:text-(--color-text-primary) transition-colors cursor-pointer">
          <ArrowLeft size={16} />
        </button>
        <div className="space-y-4">
          <div className="flex items-start gap-5">
            <div className="w-24 h-24 rounded-3xl glass-subtle animate-pulse" />
            <div className="flex-1 space-y-3">
              <div className="h-8 w-64 rounded-full glass-subtle animate-pulse" />
              <div className="h-4 w-96 rounded-full glass-subtle animate-pulse" />
            </div>
          </div>
          <div className="h-96 rounded-3xl glass-subtle animate-pulse" />
        </div>
      </div>
    );
  }

  if (mods.detailError && !mod) {
    return (
      <div className="h-full overflow-y-auto p-8 space-y-6">
        <button onClick={onBack} className="w-10 h-10 rounded-full glass-subtle flex items-center justify-center text-(--color-text-secondary) hover:text-(--color-text-primary) transition-colors cursor-pointer">
          <ArrowLeft size={16} />
        </button>
        <div className="flex flex-col items-center justify-center py-24 text-(--color-text-secondary)">
          <p className="text-lg font-bold text-(--color-text-primary)">Failed to load mod</p>
          <p className="text-sm mt-1">{mods.detailError}</p>
          <button onClick={() => mods.selectMod(modId)} className="mt-4 px-5 py-2.5 rounded-full bg-(--color-accent) hover:bg-(--color-accent-hover) text-white text-sm font-semibold cursor-pointer">
            Retry
          </button>
        </div>
      </div>
    );
  }

  if (!mod) return null;

  const hasBody = !!mod.body;

  return (
    <div className="h-full overflow-y-auto">
      {/* Hero header */}
      <div className="relative px-8 pt-8 pb-6">
        {/* Background glow from icon */}
        <div className="absolute inset-0 pointer-events-none overflow-hidden">
          <div className="absolute top-0 left-20 w-[400px] h-[250px] rounded-full bg-(--color-accent)/6 blur-3xl" />
        </div>

        <div className="relative z-10">
          <button onClick={onBack} className="w-10 h-10 rounded-full glass-subtle flex items-center justify-center text-(--color-text-secondary) hover:text-(--color-text-primary) transition-colors cursor-pointer mb-6">
            <ArrowLeft size={16} />
          </button>

          <div className="flex items-start gap-6">
            {/* Icon */}
            {mod.icon_url ? (
              <img src={mod.icon_url} alt={mod.title} className="w-24 h-24 rounded-3xl object-cover flex-shrink-0 shadow-lg ring-1 ring-black/5" />
            ) : (
              <div className="w-24 h-24 rounded-3xl bg-(--color-surface-tertiary) flex items-center justify-center text-(--color-text-secondary) flex-shrink-0 shadow-lg">
                <Sparkles size={32} />
              </div>
            )}

            {/* Info */}
            <div className="flex-1 min-w-0">
              <h1 className="text-3xl font-black tracking-tighter text-(--color-text-primary) leading-none">
                {mod.title}
              </h1>
              <p className="text-sm text-(--color-text-secondary) mt-2 leading-relaxed max-w-2xl">
                {mod.description}
              </p>

              {/* Stats row */}
              <div className="flex items-center gap-2 mt-4 flex-wrap">
                <span className="flex items-center gap-1.5 text-xs font-semibold text-(--color-text-secondary) bg-(--color-surface-secondary) border border-(--color-border) px-3 py-1.5 rounded-full">
                  <Download size={12} />
                  {formatNumber(mod.downloads ?? 0)} downloads
                </span>
                <span className="flex items-center gap-1.5 text-xs font-semibold text-(--color-text-secondary) bg-(--color-surface-secondary) border border-(--color-border) px-3 py-1.5 rounded-full">
                  <Heart size={12} />
                  {formatNumber(mod.follows ?? mod.followers ?? 0)} followers
                </span>
                {mod.date_created && (
                  <span className="flex items-center gap-1.5 text-xs font-semibold text-(--color-text-secondary) bg-(--color-surface-secondary) border border-(--color-border) px-3 py-1.5 rounded-full">
                    <Calendar size={12} />
                    {new Date(mod.date_created).toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" })}
                  </span>
                )}
              </div>

              {/* Categories */}
              {(mod.categories ?? []).length > 0 && (
                <div className="flex items-center gap-1.5 mt-3 flex-wrap">
                  {mod.categories.map((cat) => (
                    <span key={cat} className="flex items-center gap-1 text-[11px] font-semibold px-2.5 py-1 rounded-full bg-(--color-accent)/10 text-(--color-accent)">
                      <Tag size={9} />
                      {cat}
                    </span>
                  ))}
                </div>
              )}

              {/* Actions */}
              <div className="flex items-center gap-3 mt-5">
                <button
                  onClick={() => setShowInstallModal(true)}
                  className="launch-btn flex items-center gap-2 px-7 py-3 rounded-2xl bg-(--color-accent) hover:bg-(--color-accent-hover) text-white text-sm font-bold transition-all cursor-pointer hover:-translate-y-0.5"
                >
                  <span className="launch-shimmer" />
                  <Download size={15} className="relative z-10" />
                  <span className="relative z-10">Install Mod</span>
                </button>

                {/* External links */}
                {[
                  { url: mod.source_url, label: "Source" },
                  { url: mod.wiki_url, label: "Wiki" },
                  { url: mod.discord_url, label: "Discord" },
                  { url: mod.issues_url, label: "Issues" },
                ].filter((l) => l.url).map(({ url, label }) => (
                  <a
                    key={label}
                    href={url!}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex items-center gap-1.5 px-4 py-2.5 rounded-2xl bg-(--color-surface-secondary) border border-(--color-border) text-xs font-semibold text-(--color-text-secondary) hover:text-(--color-text-primary) hover:border-(--color-accent)/30 transition-all cursor-pointer"
                  >
                    <ExternalLink size={12} />
                    {label}
                  </a>
                ))}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Gallery */}
      {mod.gallery && mod.gallery.length > 0 && (
        <div className="flex gap-3 overflow-x-auto pb-3 px-8 scrollbar-hide" style={{ scrollbarWidth: "none" }}>
          {mod.gallery.map((img, i) => (
            <img key={i} src={img.url} alt={img.title || `Screenshot ${i + 1}`} className="h-48 rounded-2xl object-cover flex-shrink-0 shadow-md ring-1 ring-black/5" />
          ))}
        </div>
      )}

      {/* Tab bar */}
      <div className="px-8 mt-4 mb-1">
        <div className="flex items-center gap-1 bg-(--color-surface-secondary) rounded-2xl p-1 w-fit border border-(--color-border)">
          <button
            onClick={() => setActiveTab("description")}
            className={`flex items-center gap-2 px-5 py-2.5 rounded-xl text-xs font-bold transition-all cursor-pointer ${
              activeTab === "description"
                ? "bg-(--color-surface) text-(--color-text-primary) shadow-sm"
                : "text-(--color-text-secondary) hover:text-(--color-text-primary)"
            }`}
          >
            <FileText size={13} />
            Description
          </button>
          <button
            onClick={() => setActiveTab("versions")}
            className={`flex items-center gap-2 px-5 py-2.5 rounded-xl text-xs font-bold transition-all cursor-pointer ${
              activeTab === "versions"
                ? "bg-(--color-surface) text-(--color-text-primary) shadow-sm"
                : "text-(--color-text-secondary) hover:text-(--color-text-primary)"
            }`}
          >
            <Box size={13} />
            Versions
            <span className="text-[10px] font-bold bg-(--color-surface-tertiary) px-1.5 py-0.5 rounded-md">
              {versions.length}
            </span>
          </button>
        </div>
      </div>

      {/* Tab content */}
      <div className="px-8 pb-8 mt-4">
        {activeTab === "description" && (
          <div className="rounded-3xl bg-(--color-surface-secondary) border border-(--color-border) p-8 overflow-hidden">
            {hasBody ? (
              <div className="mod-description prose" dangerouslySetInnerHTML={{ __html: renderedBody }} />
            ) : mods.detailLoading ? (
              <div className="flex items-center justify-center py-16 gap-3 text-(--color-text-secondary)">
                <Loader2 size={20} className="animate-spin" />
                <span className="text-sm font-medium">Loading description...</span>
              </div>
            ) : (
              <p className="text-sm text-(--color-text-secondary) py-8 text-center">No description available</p>
            )}
          </div>
        )}

        {activeTab === "versions" && (
          <div className="space-y-4">
            {/* Version filters */}
            <div className="rounded-3xl bg-(--color-surface-secondary) border border-(--color-border) p-5 space-y-4">
              {/* Search */}
              <div className="relative">
                <Search size={14} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-(--color-text-secondary)" />
                <input
                  type="text"
                  value={versionSearch}
                  onChange={(e) => setVersionSearch(e.target.value)}
                  placeholder="Filter versions..."
                  className="w-full pl-9 pr-4 py-2.5 rounded-xl bg-(--color-surface) border border-(--color-border) text-xs text-(--color-text-primary) placeholder:text-(--color-text-secondary)/60 focus:outline-none focus:ring-2 focus:ring-(--color-accent)/30 transition-all"
                />
              </div>

              {/* Filter rows */}
              <div className="space-y-3">
                {/* Loader filter */}
                {allLoaders.length > 0 && (
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="text-[10px] font-bold text-(--color-text-secondary) uppercase tracking-wider w-16 flex-shrink-0">Loader</span>
                    {allLoaders.map((loader) => {
                      const meta = LOADER_COLORS[loader] ?? { color: "var(--color-text-secondary)", bg: "var(--color-surface-tertiary)" };
                      const isActive = versionLoaderFilter.includes(loader);
                      return (
                        <button
                          key={loader}
                          onClick={() => setVersionLoaderFilter((prev) => isActive ? prev.filter((l) => l !== loader) : [...prev, loader])}
                          className="px-3 py-1 rounded-lg text-[11px] font-semibold capitalize transition-all cursor-pointer border"
                          style={{
                            backgroundColor: isActive ? meta.bg : "transparent",
                            color: isActive ? meta.color : "var(--color-text-secondary)",
                            borderColor: isActive ? meta.color + "40" : "var(--color-border)",
                          }}
                        >
                          {loader}
                        </button>
                      );
                    })}
                  </div>
                )}

                {/* MC version filter */}
                {allMcVersions.length > 0 && (
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="text-[10px] font-bold text-(--color-text-secondary) uppercase tracking-wider w-16 flex-shrink-0">Version</span>
                    {allMcVersions.slice(0, 12).map((ver) => {
                      const isActive = versionMcFilter.includes(ver);
                      return (
                        <button
                          key={ver}
                          onClick={() => setVersionMcFilter((prev) => isActive ? prev.filter((v) => v !== ver) : [...prev, ver])}
                          className={`px-3 py-1 rounded-lg text-[11px] font-semibold transition-all cursor-pointer border ${
                            isActive
                              ? "bg-(--color-accent)/12 text-(--color-accent) border-(--color-accent)/30"
                              : "text-(--color-text-secondary) border-(--color-border) hover:text-(--color-text-primary)"
                          }`}
                        >
                          {ver}
                        </button>
                      );
                    })}
                    {allMcVersions.length > 12 && (
                      <span className="text-[10px] text-(--color-text-secondary)">+{allMcVersions.length - 12} more</span>
                    )}
                  </div>
                )}

                {/* Release type filter */}
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="text-[10px] font-bold text-(--color-text-secondary) uppercase tracking-wider w-16 flex-shrink-0">Type</span>
                  {Object.entries(VERSION_TYPE_STYLES).map(([type, style]) => {
                    const isActive = versionTypeFilter.includes(type);
                    return (
                      <button
                        key={type}
                        onClick={() => setVersionTypeFilter((prev) => isActive ? prev.filter((t) => t !== type) : [...prev, type])}
                        className="px-3 py-1 rounded-lg text-[11px] font-semibold transition-all cursor-pointer border"
                        style={{
                          backgroundColor: isActive ? style.bg : "transparent",
                          color: isActive ? style.color : "var(--color-text-secondary)",
                          borderColor: isActive ? style.color + "40" : "var(--color-border)",
                        }}
                      >
                        {style.label}
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* Active filters summary */}
              {(versionLoaderFilter.length > 0 || versionMcFilter.length > 0 || versionTypeFilter.length > 0 || versionSearch) && (
                <div className="flex items-center justify-between pt-2 border-t border-(--color-border)">
                  <span className="text-[11px] font-medium text-(--color-text-secondary)">
                    {filteredVersions.length} of {versions.length} versions
                  </span>
                  <button
                    onClick={() => { setVersionLoaderFilter([]); setVersionMcFilter([]); setVersionTypeFilter([]); setVersionSearch(""); }}
                    className="flex items-center gap-1 text-[11px] font-semibold text-(--color-accent) hover:underline cursor-pointer"
                  >
                    <X size={11} /> Clear filters
                  </button>
                </div>
              )}
            </div>

            {/* Version table */}
            {mods.detailLoading && versions.length === 0 ? (
              <div className="space-y-2">
                {Array.from({ length: 5 }).map((_, i) => (
                  <div key={i} className="h-16 rounded-2xl bg-(--color-surface-secondary) border border-(--color-border) animate-pulse" />
                ))}
              </div>
            ) : filteredVersions.length === 0 ? (
              <div className="flex flex-col items-center py-12 text-(--color-text-secondary)">
                <Search size={32} className="mb-3 opacity-20" />
                <p className="text-sm font-bold text-(--color-text-primary)">No matching versions</p>
                <p className="text-xs mt-1">Try adjusting your filters</p>
              </div>
            ) : (
              <div className="space-y-2">
                {filteredVersions.map((v) => (
                  <VersionRow key={v.id} version={v} onInstall={() => setShowInstallModal(true)} />
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      {showInstallModal && mod && (
        <ModInstallModal mod={mod} onClose={() => setShowInstallModal(false)} onInstalled={() => {}} />
      )}
    </div>
  );
});

// ─── Version row ──────────────────────────────────────────────────────────────

function VersionRow({ version: v, onInstall }: { version: ModVersion; onInstall: () => void }) {
  const [expanded, setExpanded] = useState(false);
  const typeStyle = VERSION_TYPE_STYLES[v.version_type] ?? VERSION_TYPE_STYLES.release;
  const primaryFile = v.files.find((f) => f.primary) ?? v.files[0];

  const formatSize = (bytes: number) => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  };

  return (
    <div className="rounded-2xl bg-(--color-surface-secondary) border border-(--color-border) overflow-hidden transition-all hover:border-(--color-accent)/20">
      {/* Main row */}
      <div
        className="flex items-center gap-4 px-5 py-3.5 cursor-pointer"
        onClick={() => setExpanded(!expanded)}
      >
        {/* Version number + name */}
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2.5">
            <span className="text-sm font-bold text-(--color-text-primary) truncate">
              {v.version_number}
            </span>
            <span
              className="text-[10px] font-bold px-2 py-0.5 rounded-full flex-shrink-0"
              style={{ backgroundColor: typeStyle.bg, color: typeStyle.color }}
            >
              {typeStyle.label}
            </span>
          </div>
          {v.name && v.name !== v.version_number && (
            <p className="text-[11px] text-(--color-text-secondary) mt-0.5 truncate">{v.name}</p>
          )}
        </div>

        {/* Loader tags */}
        <div className="flex items-center gap-1 flex-shrink-0">
          {v.loaders.map((l) => {
            const meta = LOADER_COLORS[l.toLowerCase()] ?? { color: "var(--color-text-secondary)", bg: "var(--color-surface-tertiary)" };
            return (
              <span
                key={l}
                className="text-[10px] font-bold px-2.5 py-0.5 rounded-full capitalize"
                style={{ backgroundColor: meta.bg, color: meta.color }}
              >
                {l}
              </span>
            );
          })}
        </div>

        {/* MC version tags (show first 2) */}
        <div className="flex items-center gap-1 flex-shrink-0">
          {v.game_versions.slice(0, 2).map((gv) => (
            <span key={gv} className="text-[10px] font-semibold px-2 py-0.5 rounded-full bg-(--color-surface-tertiary) text-(--color-text-secondary)">
              {gv}
            </span>
          ))}
          {v.game_versions.length > 2 && (
            <span className="text-[10px] text-(--color-text-secondary)">
              +{v.game_versions.length - 2}
            </span>
          )}
        </div>

        {/* Date */}
        <span className="text-[11px] text-(--color-text-secondary) flex-shrink-0 w-20 text-right">
          {new Date(v.date_published).toLocaleDateString(undefined, { month: "short", day: "numeric", year: "numeric" })}
        </span>

        {/* Size */}
        {primaryFile && (
          <span className="text-[11px] text-(--color-text-secondary) flex-shrink-0 w-16 text-right font-medium">
            {formatSize(primaryFile.size)}
          </span>
        )}

        {/* Install button */}
        <button
          onClick={(e) => { e.stopPropagation(); onInstall(); }}
          className="flex items-center gap-1.5 px-4 py-2 rounded-xl bg-(--color-accent) hover:bg-(--color-accent-hover) text-white text-[11px] font-bold transition-all cursor-pointer shadow-sm shadow-(--color-accent)/15 hover:shadow-md flex-shrink-0"
        >
          <FileDown size={12} />
          Install
        </button>

        {/* Expand chevron */}
        <ChevronDown
          size={14}
          className={`text-(--color-text-secondary) flex-shrink-0 transition-transform duration-200 ${expanded ? "rotate-180" : ""}`}
        />
      </div>

      {/* Expanded details */}
      {expanded && (
        <div className="px-5 pb-4 pt-1 border-t border-(--color-border) space-y-3">
          {/* All game versions */}
          <div>
            <span className="text-[10px] font-bold text-(--color-text-secondary) uppercase tracking-wider">
              Supported Minecraft Versions
            </span>
            <div className="flex flex-wrap gap-1 mt-1.5">
              {v.game_versions.map((gv) => (
                <span key={gv} className="text-[10px] font-semibold px-2 py-0.5 rounded-full bg-(--color-surface-tertiary) text-(--color-text-secondary)">
                  {gv}
                </span>
              ))}
            </div>
          </div>

          {/* All loaders */}
          <div>
            <span className="text-[10px] font-bold text-(--color-text-secondary) uppercase tracking-wider">
              Mod Loaders
            </span>
            <div className="flex flex-wrap gap-1 mt-1.5">
              {v.loaders.map((l) => {
                const meta = LOADER_COLORS[l.toLowerCase()] ?? { color: "var(--color-text-secondary)", bg: "var(--color-surface-tertiary)" };
                return (
                  <span key={l} className="text-[10px] font-bold px-2.5 py-0.5 rounded-full capitalize" style={{ backgroundColor: meta.bg, color: meta.color }}>
                    {l}
                  </span>
                );
              })}
            </div>
          </div>

          {/* Files */}
          <div>
            <span className="text-[10px] font-bold text-(--color-text-secondary) uppercase tracking-wider">
              Files
            </span>
            <div className="space-y-1.5 mt-1.5">
              {v.files.map((f) => (
                <div key={f.filename} className="flex items-center gap-3 px-3 py-2 rounded-xl bg-(--color-surface)/60 border border-(--color-border)">
                  <FileDown size={12} className="text-(--color-text-secondary) flex-shrink-0" />
                  <span className="text-[11px] text-(--color-text-primary) font-medium truncate flex-1">{f.filename}</span>
                  <span className="text-[10px] text-(--color-text-secondary) flex-shrink-0">{formatSize(f.size)}</span>
                  {f.primary && (
                    <span className="text-[9px] font-bold px-2 py-0.5 rounded-full bg-(--color-accent)/10 text-(--color-accent) flex-shrink-0">
                      Primary
                    </span>
                  )}
                </div>
              ))}
            </div>
          </div>

          {/* Dependencies */}
          {v.dependencies.length > 0 && (
            <div>
              <span className="text-[10px] font-bold text-(--color-text-secondary) uppercase tracking-wider">
                Dependencies ({v.dependencies.length})
              </span>
              <div className="flex flex-wrap gap-1 mt-1.5">
                {v.dependencies.map((dep, i) => (
                  <span
                    key={i}
                    className={`text-[10px] font-semibold px-2 py-0.5 rounded-full ${
                      dep.dependency_type === "required" ? "bg-amber-500/10 text-amber-500"
                      : dep.dependency_type === "optional" ? "bg-sky-500/10 text-sky-500"
                      : dep.dependency_type === "incompatible" ? "bg-red-500/10 text-red-500"
                      : "bg-(--color-surface-tertiary) text-(--color-text-secondary)"
                    }`}
                  >
                    {dep.project_id?.slice(0, 8) ?? dep.file_name ?? "unknown"} ({dep.dependency_type})
                  </span>
                ))}
              </div>
            </div>
          )}

          {/* Downloads */}
          <div className="flex items-center gap-2">
            <Download size={11} className="text-(--color-text-secondary)" />
            <span className="text-[11px] text-(--color-text-secondary) font-medium">
              {v.downloads.toLocaleString()} downloads
            </span>
          </div>
        </div>
      )}
    </div>
  );
}
