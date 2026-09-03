import { memo, useState, useEffect, useRef } from "react";
import { observer } from "mobx-react-lite";
import { useStore } from "../stores";
import type { SortIndex } from "../stores/ModStore";
import { searchMods, type ModrinthProject } from "../api/modrinth";
import {
  Search,
  Download,
  Heart,
  ChevronLeft,
  ChevronRight,
  ArrowRight,
  Sparkles,
  SlidersHorizontal,
  X,
  ChevronDown,
  ArrowDownWideNarrow,
  Flame,
  TrendingUp,
  Clock,
  Zap,
  Puzzle,
  Map as MapIcon,
  Wand2,
  Wrench,
  Shuffle,
  Calendar,
} from "lucide-react";
import { ModInstallModal } from "./ModInstallModal";

interface BrowsePageProps {
  onOpenMod: (id: string) => void;
}

const SORT_OPTIONS: { value: SortIndex; label: string }[] = [
  { value: "relevance", label: "Relevance" },
  { value: "downloads", label: "Downloads" },
  { value: "follows", label: "Followers" },
  { value: "newest", label: "Newest" },
  { value: "updated", label: "Recently Updated" },
];

// Quick filters
const QUICK_FILTERS = [
  { label: "Popular", icon: Flame, sort: "downloads" as SortIndex, category: null },
  { label: "Trending", icon: TrendingUp, sort: "follows" as SortIndex, category: null },
  { label: "New Releases", icon: Zap, sort: "newest" as SortIndex, category: null },
  { label: "Recently Updated", icon: Clock, sort: "updated" as SortIndex, category: null },
  { label: "Adventure", icon: MapIcon, sort: "downloads" as SortIndex, category: "adventure" },
  { label: "Technology", icon: Wrench, sort: "downloads" as SortIndex, category: "technology" },
  { label: "Magic", icon: Wand2, sort: "downloads" as SortIndex, category: "magic" },
  { label: "Utility", icon: Puzzle, sort: "downloads" as SortIndex, category: "utility" },
];

// Netflix-style category rows for the home view
const CATEGORY_ROWS = [
  { title: "Popular Right Now", icon: Flame, sort: "downloads" as SortIndex, category: null, offset: 0 },
  { title: "Trending This Week", icon: TrendingUp, sort: "follows" as SortIndex, category: null, offset: 0 },
  { title: "Fresh Off The Press", icon: Zap, sort: "newest" as SortIndex, category: null, offset: 0 },
  { title: "Adventure & Exploration", icon: MapIcon, sort: "downloads" as SortIndex, category: "adventure", offset: 0 },
  { title: "Technology & Automation", icon: Wrench, sort: "downloads" as SortIndex, category: "technology", offset: 0 },
  { title: "Magic & Sorcery", icon: Wand2, sort: "downloads" as SortIndex, category: "magic", offset: 0 },
];

const categoryRowCache = new Map<string, ModrinthProject[]>();

function formatCompactNumber(value: number) {
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M`;
  if (value >= 1_000) return `${(value / 1000).toFixed(0)}k`;
  return value.toString();
}

function getPrimaryCategory(mod: ModrinthProject) {
  return mod.categories?.[0] ?? null;
}

function FilterDropdown({
  label,
  options,
  selected,
  onToggle,
}: {
  label: string;
  options: { value: string; label: string }[];
  selected: string[];
  onToggle: (value: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen(!open)}
        className={`flex items-center gap-2 px-3.5 py-2 rounded-card text-xs font-emphasis transition-all cursor-pointer ${
          selected.length > 0
            ? "bg-(--color-accent)/12 text-(--color-accent) ring-1 ring-(--color-accent)/25"
            : "bg-(--color-surface-secondary) border border-(--color-border) text-(--color-text-secondary) hover:text-(--color-text-primary)"
        }`}
      >
        {label}
        {selected.length > 0 && (
          <span className="w-4.5 h-4.5 rounded-pill bg-(--color-accent) text-fg-on-accent text-[9px] font-strong flex items-center justify-center">
            {selected.length}
          </span>
        )}
        <ChevronDown size={12} className={`transition-transform duration-200 ${open ? "rotate-180" : ""}`} />
      </button>
      {open && (
        <div className="absolute top-full left-0 mt-1.5 w-52 max-h-60 overflow-y-auto rounded-card bg-(--color-surface-secondary) border border-(--color-border) shadow-lg z-50 p-1.5 picker-dropdown">
          {options.map((opt, idx) => (
            <button
              key={opt.value}
              onClick={() => onToggle(opt.value)}
              className={`picker-item w-full text-left px-3 py-2 rounded-control text-xs font-body transition-all cursor-pointer ${
                selected.includes(opt.value)
                  ? "bg-(--color-accent)/12 text-(--color-accent)"
                  : "text-(--color-text-secondary) hover:bg-(--color-surface-tertiary) hover:text-(--color-text-primary)"
              }`}
              style={{ animationDelay: `${idx * 30}ms` }}
            >
              {opt.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

// ─── Horizontal scrolling row (Netflix-style) ─────────────────────────────────

const CategoryRow = memo(function CategoryRow({
  title,
  icon: Icon,
  sort,
  category,
  onOpenMod,
  onOpenInstall,
  delay,
}: {
  title: string;
  icon: typeof Flame;
  sort: SortIndex;
  category: string | null;
  onOpenMod: (id: string) => void;
  onOpenInstall: (mod: ModrinthProject) => void;
  delay: number;
}) {
  const cacheKey = `${sort}:${category ?? "all"}`;
  const cachedMods = categoryRowCache.get(cacheKey) ?? [];
  const [mods, setMods] = useState<ModrinthProject[]>(cachedMods);
  const [loading, setLoading] = useState(cachedMods.length === 0);
  const scrollRef = useRef<HTMLDivElement>(null);
  const [canScrollLeft, setCanScrollLeft] = useState(false);
  const [canScrollRight, setCanScrollRight] = useState(true);

  useEffect(() => {
    if (cachedMods.length > 0) {
      setMods(cachedMods);
      setLoading(false);
      return;
    }
    let cancelled = false;
    (async () => {
      try {
        const facets: string[][] = [["project_type:mod"]];
        if (category) facets.push([`categories:${category}`]);
        const result = await searchMods("", { facets, index: sort, limit: 20, offset: 0 });
        if (!cancelled) {
          categoryRowCache.set(cacheKey, result.hits);
          setMods(result.hits);
        }
      } catch { /* non-critical */ }
      finally { if (!cancelled) setLoading(false); }
    })();
    return () => { cancelled = true; };
  }, [cacheKey, cachedMods, sort, category]);

  const updateScrollButtons = () => {
    const el = scrollRef.current;
    if (!el) return;
    setCanScrollLeft(el.scrollLeft > 10);
    setCanScrollRight(el.scrollLeft < el.scrollWidth - el.clientWidth - 10);
  };

  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    el.addEventListener("scroll", updateScrollButtons);
    updateScrollButtons();
    return () => el.removeEventListener("scroll", updateScrollButtons);
  }, [mods]);

  const scroll = (dir: "left" | "right") => {
    const el = scrollRef.current;
    if (!el) return;
    el.scrollBy({ left: dir === "left" ? -400 : 400, behavior: "smooth" });
  };

  if (loading) {
    return (
      <div className="home-slide-up" style={{ animationDelay: `${delay}s` }}>
        <div className="flex items-center gap-2.5 mb-3 px-1">
          <Icon size={16} className="text-(--color-text-secondary)" />
          <h2 className="text-base font-display text-(--color-text-primary) tracking-tight">{title}</h2>
        </div>
        <div className="flex gap-3 overflow-hidden pt-2 -mt-2">
          {Array.from({ length: 8 }).map((_, i) => (
            <div key={i} className="w-56 h-64 rounded-3xl bg-(--color-surface-secondary) border border-(--color-border) animate-pulse flex-shrink-0" />
          ))}
        </div>
      </div>
    );
  }

  if (mods.length === 0) return null;

  return (
    <div className="home-slide-up" style={{ animationDelay: `${delay}s` }}>
      <div className="flex items-center justify-between mb-3 px-1">
        <div>
          <div className="flex items-center gap-2.5">
            <Icon size={16} className="text-(--color-text-secondary)" />
            <h2 className="text-base font-display text-(--color-text-primary) tracking-tight">{title}</h2>
          </div>
          <p className="mt-1 pl-6 text-caption font-body uppercase tracking-[0.16em] text-(--color-text-secondary)">
            {mods.length} picks
          </p>
        </div>
        <div className="flex items-center gap-1">
          <button
            onClick={() => scroll("left")}
            className={`w-7 h-7 rounded-pill flex items-center justify-center transition-all cursor-pointer ${
              canScrollLeft
                ? "bg-(--color-surface-secondary) border border-(--color-border) text-(--color-text-secondary) hover:text-(--color-text-primary)"
                : "opacity-0 pointer-events-none"
            }`}
          >
            <ChevronLeft size={14} />
          </button>
          <button
            onClick={() => scroll("right")}
            className={`w-7 h-7 rounded-pill flex items-center justify-center transition-all cursor-pointer ${
              canScrollRight
                ? "bg-(--color-surface-secondary) border border-(--color-border) text-(--color-text-secondary) hover:text-(--color-text-primary)"
                : "opacity-0 pointer-events-none"
            }`}
          >
            <ChevronRight size={14} />
          </button>
        </div>
      </div>

      <div
        ref={scrollRef}
        className="flex gap-3 overflow-x-auto pt-2 pb-2 -mt-2 scrollbar-hide"
        style={{ scrollbarWidth: "none", msOverflowStyle: "none" }}
      >
        {mods.map((mod, idx) => (
          <RowCard
            key={mod.id}
            mod={mod}
            idx={idx}
            onOpenMod={onOpenMod}
            onOpenInstall={onOpenInstall}
          />
        ))}
      </div>
    </div>
  );
});

// ─── Row card (horizontal scroll card for Netflix rows) ──────────────────────

const RowCard = observer(function RowCard({
  mod,
  idx,
  onOpenMod,
  onOpenInstall,
}: {
  mod: ModrinthProject;
  idx: number;
  onOpenMod: (id: string) => void;
  onOpenInstall: (mod: ModrinthProject) => void;
}) {
  const { installs } = useStore();
  const installing = installs.isProjectInstalling(mod.id);
  const modTarget = mod.slug || mod.id;

  return (
    <article
      role="button"
      tabIndex={0}
      onClick={() => onOpenMod(modTarget)}
      onKeyDown={(event) => {
        if (event.target !== event.currentTarget) return;
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          onOpenMod(modTarget);
        }
      }}
      className="browse-mod-card group relative flex aspect-square w-72 flex-shrink-0 cursor-pointer overflow-hidden rounded-[1.4rem] p-5 text-left transition-all duration-300 hover:-translate-y-1 hover:shadow-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-(--color-accent)/40"
      style={{
        animationDelay: `${idx * 30}ms`,
        background: "var(--color-glass-bg, rgba(255,255,255,0.06))",
        backdropFilter: "blur(18px) saturate(1.2)",
        WebkitBackdropFilter: "blur(18px) saturate(1.2)",
        border: "1px solid var(--color-glass-border, rgba(255,255,255,0.08))",
        boxShadow: "0 4px 24px rgba(0,0,0,0.08), inset 0 1px 0 rgba(255,255,255,0.05)",
      }}
    >
      {/* Hover glow */}
      <div className="absolute inset-0 opacity-0 group-hover:opacity-100 transition-opacity duration-500 pointer-events-none" style={{ background: "radial-gradient(ellipse at 50% 0%, var(--color-accent-raw, 139 92 246) / 0.05, transparent 70%)" }} />

      <div className="relative z-10 flex h-full flex-col">
        {mod.icon_url ? (
          <img src={mod.icon_url} alt={mod.title} className="w-16 h-16 rounded-panel object-cover shadow-lg ring-1 ring-white/10" />
        ) : (
          <div className="w-16 h-16 rounded-panel flex items-center justify-center text-(--color-text-secondary)" style={{ backgroundColor: "rgba(255,255,255,0.06)" }}>
            <Sparkles size={22} />
          </div>
        )}
        <div className="mt-4 min-h-[3.6rem]">
          <h3 className="line-clamp-2 pt-1 text-base leading-tight font-display text-(--color-text-primary) group-hover:text-(--color-accent) transition-colors tracking-tight [overflow-wrap:anywhere]">
            {mod.title}
          </h3>
          <p className="truncate pt-1 text-xs text-(--color-text-secondary) font-emphasis">
            {mod.author}
          </p>
        </div>
        <p className="mt-2.5 min-h-[2.4rem] text-xs text-(--color-text-secondary) line-clamp-2 leading-relaxed">
          {mod.description}
        </p>
        <div className="mt-4 flex items-center gap-3">
          <span className="flex items-center gap-1.5 text-caption font-emphasis text-(--color-text-secondary)">
            <Download size={11} />
            {formatCompactNumber(mod.downloads ?? 0)}
          </span>
          <span className="flex items-center gap-1.5 text-caption font-emphasis text-(--color-text-secondary)">
            <Heart size={11} />
            {(mod.follows ?? mod.followers ?? 0).toLocaleString()}
          </span>
        </div>
        <div className="mt-auto border-t border-line-strong pt-3">
          <button
            type="button"
            onClick={(event) => {
              event.stopPropagation();
              onOpenInstall(mod);
            }}
            disabled={installing}
            className="w-full flex items-center justify-center gap-2 rounded-card bg-(--color-accent) px-3 py-2 text-caption font-strong text-fg-on-accent transition-all cursor-pointer hover:opacity-95 disabled:opacity-60 disabled:cursor-default"
          >
            <Download size={12} />
            {installing ? "Installing..." : "Install"}
          </button>
        </div>
      </div>
    </article>
  );
});

// ─── Grid card (for search results) ─────────────────────────────────────────

const GridCard = observer(function GridCard({
  mod,
  idx,
  onOpenMod,
  onOpenInstall,
}: {
  mod: ModrinthProject;
  idx: number;
  onOpenMod: (id: string) => void;
  onOpenInstall: (mod: ModrinthProject) => void;
}) {
  const { installs } = useStore();
  const installing = installs.isProjectInstalling(mod.id);
  const modTarget = mod.slug || mod.id;

  return (
    <article
      role="button"
      tabIndex={0}
      onClick={() => onOpenMod(modTarget)}
      onKeyDown={(event) => {
        if (event.target !== event.currentTarget) return;
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          onOpenMod(modTarget);
        }
      }}
      className="browse-mod-card group relative flex min-h-[19rem] cursor-pointer overflow-hidden rounded-[1.4rem] p-6 text-left transition-all duration-300 hover:-translate-y-1 hover:shadow-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-(--color-accent)/40"
      style={{
        animationDelay: `${idx * 40}ms`,
        background: "var(--color-glass-bg, rgba(255,255,255,0.06))",
        backdropFilter: "blur(18px) saturate(1.2)",
        WebkitBackdropFilter: "blur(18px) saturate(1.2)",
        border: "1px solid var(--color-glass-border, rgba(255,255,255,0.08))",
        boxShadow: "0 4px 24px rgba(0,0,0,0.08), inset 0 1px 0 rgba(255,255,255,0.05)",
      }}
    >
      {/* Hover glow */}
      <div className="absolute inset-0 opacity-0 group-hover:opacity-100 transition-opacity duration-500 pointer-events-none" style={{ background: "radial-gradient(ellipse at 50% 0%, var(--color-accent-raw, 139 92 246) / 0.05, transparent 70%)" }} />

      <div className="relative z-10 flex h-full flex-col">
        <div className="flex items-start gap-5">
          {mod.icon_url ? (
            <img src={mod.icon_url} alt={mod.title} className="w-16 h-16 rounded-panel object-cover shadow-lg ring-1 ring-white/10 flex-shrink-0" />
          ) : (
            <div className="w-16 h-16 rounded-panel flex items-center justify-center text-(--color-text-secondary) flex-shrink-0" style={{ backgroundColor: "rgba(255,255,255,0.06)" }}>
              <Sparkles size={22} />
            </div>
          )}
          <div className="min-w-0 flex-1 pt-1">
            <h3 className="line-clamp-2 text-base leading-tight font-display text-(--color-text-primary) group-hover:text-(--color-accent) transition-colors tracking-tight [overflow-wrap:anywhere]">
              {mod.title}
            </h3>
            <p className="truncate pt-1 text-xs text-(--color-text-secondary) font-emphasis">
              {mod.author}
            </p>
          </div>
          <ArrowRight
            size={16}
            className="text-(--color-text-secondary) opacity-0 group-hover:opacity-100 group-hover:translate-x-1 transition-all duration-300 flex-shrink-0 mt-2"
          />
        </div>
        <p className="text-sm text-(--color-text-secondary) line-clamp-2 mt-4 leading-relaxed">
          {mod.description}
        </p>
        <div className="mt-4 flex items-center justify-between">
          <div className="flex items-center gap-4">
            <span className="flex items-center gap-1.5 text-xs font-emphasis text-(--color-text-secondary)">
              <Download size={12} />
              {formatCompactNumber(mod.downloads ?? 0)}
            </span>
            <span className="flex items-center gap-1.5 text-xs font-emphasis text-(--color-text-secondary)">
              <Heart size={12} />
              {(mod.follows ?? mod.followers ?? 0).toLocaleString()}
            </span>
          </div>
          <span className="flex items-center gap-1.5 text-caption font-emphasis text-(--color-text-secondary)">
            <Calendar size={12} />
            {new Date(mod.date_modified).toLocaleDateString(undefined, { month: "short", day: "numeric" })}
          </span>
        </div>
        <div className="mt-auto pt-5">
          <button
            type="button"
            onClick={(event) => {
              event.stopPropagation();
              onOpenInstall(mod);
            }}
            disabled={installing}
            className="flex items-center justify-center gap-2 rounded-card bg-(--color-accent) px-4 py-2 text-caption font-strong text-fg-on-accent transition-all cursor-pointer hover:opacity-95 disabled:opacity-60 disabled:cursor-default"
          >
            <Download size={12} />
            {installing ? "Installing..." : "Install"}
          </button>
        </div>
      </div>
    </article>
  );
});

// ─── Main BrowsePage ────────────────────────────────────────────────────────

export const BrowsePage = observer(({ onOpenMod }: BrowsePageProps) => {
  const { mods, installs, instances } = useStore();
  const [query, setQuery] = useState(mods.searchQuery);
  const [showFilters, setShowFilters] = useState(false);
  const [activeQuickFilter, setActiveQuickFilter] = useState<string | null>(null);
  const [installModalMod, setInstallModalMod] = useState<ModrinthProject | null>(null);

  useEffect(() => {
    mods.loadFilters();
    if (mods.mods.length === 0 && !mods.loading) {
      mods.search("", 0);
    }
  }, []);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setActiveQuickFilter(null);
    mods.search(query, 0);
  };

  const handleQuickFilter = (filter: typeof QUICK_FILTERS[0]) => {
    const isActive = activeQuickFilter === filter.label;
    if (isActive) {
      setActiveQuickFilter(null);
      mods.clearFilters();
      mods.search("", 0);
      setQuery("");
      return;
    }
    setActiveQuickFilter(filter.label);
    mods.clearFilters();
    mods.sortIndex = filter.sort;
    if (filter.category) mods.selectedCategories = [filter.category];
    mods.search("", 0);
    setQuery("");
  };

  const hasSearched = mods.searchQuery !== "" || mods.hasActiveFilters || activeQuickFilter !== null;

  const pageNumbers = () => {
    const pages: (number | "...")[] = [];
    const total = mods.totalPages;
    const cur = mods.currentPage;
    if (total <= 7) {
      for (let i = 0; i < total; i++) pages.push(i);
    } else {
      pages.push(0);
      if (cur > 2) pages.push("...");
      for (let i = Math.max(1, cur - 1); i <= Math.min(total - 2, cur + 1); i++) pages.push(i);
      if (cur < total - 3) pages.push("...");
      pages.push(total - 1);
    }
    return pages;
  };

  const categoryOptions = mods.modCategories.map((c) => ({
    value: c.name,
    label: c.name.charAt(0).toUpperCase() + c.name.slice(1),
  }));
  const loaderOptions = mods.modLoaders.map((l) => ({
    value: l.name,
    label: l.name.charAt(0).toUpperCase() + l.name.slice(1),
  }));
  const versionOptions = mods.majorGameVersions.map((v) => ({
    value: v.version,
    label: v.version,
  }));
  const preferredInstance =
    instances.instances.find((instance) => instance.id === installs.preferredInstanceId) ?? null;

  return (
    <div className="h-full overflow-y-auto">
      {/* Hero */}
      <div className="px-6 pt-6 pb-4">
        <div>
          <div className="home-slide-up" style={{ animationDelay: "0s" }}>
            <h1 className="text-[2.5rem] font-display tracking-tight text-(--color-text-primary)">
              Explore the <span className="void-text">Void</span>
            </h1>
            <p className="text-sm text-(--color-text-secondary) mt-1.5 max-w-md">
              Search, compare, and install mods without leaving the launcher.
            </p>
          </div>

          {preferredInstance && (
            <div
              className="mt-4 inline-flex items-center gap-2 rounded-pill border border-(--color-accent)/20 bg-(--color-accent)/8 px-3 py-1.5 text-caption font-emphasis text-(--color-accent) home-slide-up"
              style={{ animationDelay: "0.05s" }}
            >
              Browse opened from {preferredInstance.name}
            </div>
          )}

          {/* Search */}
          <form onSubmit={handleSearch} className="flex gap-2 mt-6 home-slide-up" style={{ animationDelay: "0.1s" }}>
            <div className="relative flex-1">
              <Search size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-(--color-text-secondary)" />
              <input
                type="text"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Search for mods..."
                className="w-full pl-11 pr-4 py-3 rounded-window bg-(--color-surface-secondary) border border-(--color-border) text-sm text-(--color-text-primary) placeholder:text-(--color-text-secondary)/60 focus:outline-none focus:ring-2 focus:ring-(--color-accent)/40 focus:border-transparent transition-all"
              />
            </div>
            <button type="submit" className="px-6 py-3 rounded-window bg-(--color-accent) hover:bg-(--color-accent-hover) text-fg-on-accent text-sm font-strong transition-all cursor-pointer shadow-sm shadow-(--color-accent)/12 hover:shadow-md hover:-translate-y-0.5 active:translate-y-0">
              Search
            </button>
            <button
              type="button"
              onClick={() => setShowFilters(!showFilters)}
              className={`w-11 h-11 rounded-window flex items-center justify-center transition-all cursor-pointer ${
                showFilters || mods.hasActiveFilters
                  ? "bg-(--color-accent)/12 text-(--color-accent) ring-1 ring-(--color-accent)/25"
                  : "bg-(--color-surface-secondary) border border-(--color-border) text-(--color-text-secondary) hover:text-(--color-text-primary)"
              }`}
            >
              <SlidersHorizontal size={16} />
            </button>
          </form>

          {/* Quick filters */}
          <div className="mt-5 home-slide-up" style={{ animationDelay: "0.2s" }}>
            <div className="flex flex-wrap gap-2">
              {QUICK_FILTERS.map((filter) => {
                const Icon = filter.icon;
                const isActive = activeQuickFilter === filter.label;
                return (
                  <button
                    key={filter.label}
                    onClick={() => handleQuickFilter(filter)}
                    className={`group flex h-9 items-center gap-2 rounded-control border px-3 text-xs font-strong transition-colors cursor-pointer ${
                      isActive
                        ? "border-(--color-accent)/35 bg-(--color-accent)/12 text-(--color-accent)"
                        : "border-(--color-border) bg-(--color-surface-secondary) text-(--color-text-secondary) hover:bg-(--color-surface-tertiary) hover:text-(--color-text-primary)"
                    }`}
                  >
                    <Icon size={14} />
                    {filter.label}
                  </button>
                );
              })}
            </div>
          </div>
        </div>
      </div>

      <div className="px-6 pb-8">
        {/* Filters panel */}
        {showFilters && (
          <div className="rounded-modal bg-(--color-surface-secondary) border border-(--color-border) p-5 space-y-3 mb-5 picker-dropdown">
            <div className="flex items-center justify-between">
              <h3 className="text-xs font-strong text-(--color-text-secondary) uppercase tracking-wider">Filters</h3>
              {mods.hasActiveFilters && (
                <button onClick={() => { mods.clearFilters(); setActiveQuickFilter(null); }} className="flex items-center gap-1 text-caption font-emphasis text-(--color-accent) hover:underline cursor-pointer">
                  <X size={11} /> Clear all
                </button>
              )}
            </div>
            <div className="flex flex-wrap gap-1.5">
              <ArrowDownWideNarrow size={13} className="text-(--color-text-secondary) mt-2 mr-1" />
              {SORT_OPTIONS.map((opt) => (
                <button
                  key={opt.value}
                  onClick={() => { mods.setSortIndex(opt.value); setActiveQuickFilter(null); }}
                  className={`px-3 py-1.5 rounded-md text-caption font-emphasis transition-all cursor-pointer ${
                    mods.sortIndex === opt.value
                      ? "bg-(--color-accent) text-fg-on-accent shadow-sm"
                      : "bg-(--color-surface-tertiary) text-(--color-text-secondary) hover:text-(--color-text-primary)"
                  }`}
                >
                  {opt.label}
                </button>
              ))}
            </div>
            <div className="flex flex-wrap gap-2">
              <FilterDropdown label="Category" options={categoryOptions} selected={mods.selectedCategories} onToggle={(v) => mods.toggleCategory(v)} />
              <FilterDropdown label="Loader" options={loaderOptions} selected={mods.selectedLoaders} onToggle={(v) => mods.toggleLoader(v)} />
              <FilterDropdown label="Game Version" options={versionOptions} selected={mods.selectedGameVersions} onToggle={(v) => mods.toggleGameVersion(v)} />
            </div>
            {mods.hasActiveFilters && (
              <div className="flex flex-wrap gap-1.5 pt-1">
                {mods.selectedCategories.map((cat) => (
                  <button key={cat} onClick={() => mods.toggleCategory(cat)} className="flex items-center gap-1 px-2.5 py-1 rounded-md bg-(--color-accent)/10 text-(--color-accent) text-caption font-emphasis cursor-pointer hover:bg-(--color-accent)/20 transition-colors">
                    {cat} <X size={10} />
                  </button>
                ))}
                {mods.selectedLoaders.map((loader) => (
                  <button key={loader} onClick={() => mods.toggleLoader(loader)} className="flex items-center gap-1 px-2.5 py-1 rounded-md bg-success/10 text-success text-caption font-emphasis cursor-pointer hover:bg-success/20 transition-colors">
                    {loader} <X size={10} />
                  </button>
                ))}
                {mods.selectedGameVersions.map((ver) => (
                  <button key={ver} onClick={() => mods.toggleGameVersion(ver)} className="flex items-center gap-1 px-2.5 py-1 rounded-md bg-info/10 text-info text-caption font-emphasis cursor-pointer hover:bg-info/20 transition-colors">
                    {ver} <X size={10} />
                  </button>
                ))}
              </div>
            )}
          </div>
        )}

        {/* Netflix-style home rows — shown when no active search */}
        {!hasSearched && (
          <div className="space-y-7">
            {CATEGORY_ROWS.map((row, idx) => (
              <CategoryRow
                key={row.title}
                title={row.title}
                icon={row.icon}
                sort={row.sort}
                category={row.category}
                onOpenMod={onOpenMod}
                onOpenInstall={(mod) => setInstallModalMod(mod)}
                delay={0.25 + idx * 0.08}
              />
            ))}
          </div>
        )}

        {/* Search results grid */}
        {hasSearched && (
          <div className="space-y-4">
            <div className="rounded-modal border border-(--color-border) bg-(--color-surface-secondary) px-5 py-4">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <p className="text-caption font-strong uppercase tracking-[0.18em] text-(--color-text-secondary)">
                    Search Workspace
                  </p>
                  <h2 className="mt-2 text-xl font-display tracking-tight text-(--color-text-primary)">
                    {mods.searchQuery ? `Results for “${mods.searchQuery}”` : "Filtered mod results"}
                  </h2>
                  <p className="mt-1 text-sm text-(--color-text-secondary)">
                    {mods.totalHits.toLocaleString()} matches across Modrinth
                  </p>
                </div>
                {(mods.searchQuery || activeQuickFilter || mods.hasActiveFilters) && (
                  <button
                    onClick={() => {
                      setActiveQuickFilter(null);
                      setQuery("");
                      mods.clearFilters();
                      mods.search("", 0);
                    }}
                    className="rounded-control border border-(--color-border) bg-(--color-surface) px-3 py-2 text-xs font-emphasis text-(--color-text-primary) transition-colors hover:bg-(--color-surface-tertiary) cursor-pointer"
                  >
                    Reset View
                  </button>
                )}
              </div>
            </div>
            {mods.loading ? (
              <div className="grid grid-cols-1 lg:grid-cols-2 xl:grid-cols-3 gap-4">
                {Array.from({ length: 9 }).map((_, i) => (
                  <div key={i} className="h-48 rounded-[1.4rem] bg-(--color-surface-secondary) border border-(--color-border) animate-pulse" style={{ animationDelay: `${i * 50}ms` }} />
                ))}
              </div>
            ) : (
              <>
                {mods.mods.length > 0 && (
                  <div className="flex items-center justify-between">
                    <p className="text-xs font-body text-(--color-text-secondary) uppercase tracking-wider">
                      {mods.totalHits.toLocaleString()} results &middot; Page {mods.currentPage + 1} of {mods.totalPages}
                    </p>
                    {activeQuickFilter && (
                      <button onClick={() => { setActiveQuickFilter(null); mods.clearFilters(); mods.search("", 0); }} className="flex items-center gap-1.5 text-xs font-emphasis text-(--color-accent) hover:underline cursor-pointer">
                        <X size={12} /> Clear filter
                      </button>
                    )}
                  </div>
                )}
                <div className="grid grid-cols-1 lg:grid-cols-2 xl:grid-cols-3 gap-4 pt-2">
                  {mods.mods.map((mod, idx) => (
                    <GridCard
                      key={mod.id}
                      mod={mod}
                      idx={idx}
                      onOpenMod={onOpenMod}
                      onOpenInstall={(nextMod) => setInstallModalMod(nextMod)}
                    />
                  ))}
                </div>
                {mods.mods.length === 0 && !mods.loading && (
                  <div className="flex flex-col items-center justify-center py-20 text-(--color-text-secondary)">
                    <Search size={40} className="mb-4 opacity-20" />
                    <p className="text-base font-strong text-(--color-text-primary)">No mods found</p>
                    <p className="text-sm mt-1">Try different search terms or filters</p>
                  </div>
                )}
                {mods.totalPages > 1 && (
                  <div className="flex items-center justify-center gap-1.5 pt-4">
                    <button onClick={() => mods.goToPage(mods.currentPage - 1)} disabled={mods.currentPage === 0} className="w-9 h-9 rounded-control bg-(--color-surface-secondary) border border-(--color-border) flex items-center justify-center text-(--color-text-secondary) hover:text-(--color-text-primary) transition-colors cursor-pointer disabled:opacity-30 disabled:cursor-default">
                      <ChevronLeft size={16} />
                    </button>
                    {pageNumbers().map((p, i) =>
                      p === "..." ? (
                        <span key={`dots-${i}`} className="w-9 h-9 flex items-center justify-center text-xs text-(--color-text-secondary)">...</span>
                      ) : (
                        <button key={p} onClick={() => mods.goToPage(p)} className={`w-9 h-9 rounded-control text-xs font-strong transition-all cursor-pointer ${mods.currentPage === p ? "bg-(--color-accent) text-fg-on-accent shadow-sm" : "bg-(--color-surface-secondary) border border-(--color-border) text-(--color-text-secondary) hover:text-(--color-text-primary)"}`}>
                          {p + 1}
                        </button>
                      )
                    )}
                    <button onClick={() => mods.goToPage(mods.currentPage + 1)} disabled={mods.currentPage >= mods.totalPages - 1} className="w-9 h-9 rounded-control bg-(--color-surface-secondary) border border-(--color-border) flex items-center justify-center text-(--color-text-secondary) hover:text-(--color-text-primary) transition-colors cursor-pointer disabled:opacity-30 disabled:cursor-default">
                      <ChevronRight size={16} />
                    </button>
                  </div>
                )}
              </>
            )}
          </div>
        )}
      </div>

      {installModalMod && (
        <ModInstallModal
          mod={installModalMod}
          onClose={() => setInstallModalMod(null)}
          onInstalled={() => {}}
        />
      )}
    </div>
  );
});
