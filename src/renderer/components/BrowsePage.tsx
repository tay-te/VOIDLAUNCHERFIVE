import { useState, useEffect, useRef } from "react";
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
  Map,
  Wand2,
  Wrench,
  Shuffle,
} from "lucide-react";

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

// Quick-filter cards
const QUICK_FILTERS = [
  { label: "Popular", icon: Flame, color: "#ef4444", gradient: "from-red-500/20 to-orange-500/10", sort: "downloads" as SortIndex, category: null },
  { label: "Trending", icon: TrendingUp, color: "#f59e0b", gradient: "from-amber-500/20 to-yellow-500/10", sort: "follows" as SortIndex, category: null },
  { label: "New Releases", icon: Zap, color: "#22c55e", gradient: "from-emerald-500/20 to-green-500/10", sort: "newest" as SortIndex, category: null },
  { label: "Recently Updated", icon: Clock, color: "#6366f1", gradient: "from-indigo-500/20 to-violet-500/10", sort: "updated" as SortIndex, category: null },
  { label: "Adventure", icon: Map, color: "#3b82f6", gradient: "from-blue-500/20 to-sky-500/10", sort: "downloads" as SortIndex, category: "adventure" },
  { label: "Technology", icon: Wrench, color: "#8b5cf6", gradient: "from-violet-500/20 to-purple-500/10", sort: "downloads" as SortIndex, category: "technology" },
  { label: "Magic", icon: Wand2, color: "#d946ef", gradient: "from-fuchsia-500/20 to-pink-500/10", sort: "downloads" as SortIndex, category: "magic" },
  { label: "Utility", icon: Puzzle, color: "#14b8a6", gradient: "from-teal-500/20 to-cyan-500/10", sort: "downloads" as SortIndex, category: "utility" },
];

// Netflix-style category rows for the home view
const CATEGORY_ROWS = [
  { title: "Popular Right Now", icon: Flame, sort: "downloads" as SortIndex, category: null, offset: 0 },
  { title: "Trending This Week", icon: TrendingUp, sort: "follows" as SortIndex, category: null, offset: 0 },
  { title: "Fresh Off The Press", icon: Zap, sort: "newest" as SortIndex, category: null, offset: 0 },
  { title: "Adventure & Exploration", icon: Map, sort: "downloads" as SortIndex, category: "adventure", offset: 0 },
  { title: "Technology & Automation", icon: Wrench, sort: "downloads" as SortIndex, category: "technology", offset: 0 },
  { title: "Magic & Sorcery", icon: Wand2, sort: "downloads" as SortIndex, category: "magic", offset: 0 },
];

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
        className={`flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-semibold transition-all cursor-pointer ${
          selected.length > 0
            ? "bg-(--color-accent)/12 text-(--color-accent) ring-1 ring-(--color-accent)/25"
            : "bg-(--color-surface-secondary) border border-(--color-border) text-(--color-text-secondary) hover:text-(--color-text-primary)"
        }`}
      >
        {label}
        {selected.length > 0 && (
          <span className="w-4.5 h-4.5 rounded-full bg-(--color-accent) text-white text-[9px] font-bold flex items-center justify-center">
            {selected.length}
          </span>
        )}
        <ChevronDown size={12} className={`transition-transform duration-200 ${open ? "rotate-180" : ""}`} />
      </button>
      {open && (
        <div className="absolute top-full left-0 mt-1.5 w-52 max-h-60 overflow-y-auto rounded-xl bg-(--color-surface-secondary) border border-(--color-border) shadow-lg z-50 p-1.5 picker-dropdown">
          {options.map((opt, idx) => (
            <button
              key={opt.value}
              onClick={() => onToggle(opt.value)}
              className={`picker-item w-full text-left px-3 py-2 rounded-lg text-xs font-medium transition-all cursor-pointer ${
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

function CategoryRow({
  title,
  icon: Icon,
  sort,
  category,
  onOpenMod,
  delay,
}: {
  title: string;
  icon: typeof Flame;
  sort: SortIndex;
  category: string | null;
  onOpenMod: (id: string) => void;
  delay: number;
}) {
  const [mods, setMods] = useState<ModrinthProject[]>([]);
  const [loading, setLoading] = useState(true);
  const scrollRef = useRef<HTMLDivElement>(null);
  const [canScrollLeft, setCanScrollLeft] = useState(false);
  const [canScrollRight, setCanScrollRight] = useState(true);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const facets: string[][] = [["project_type:mod"]];
        if (category) facets.push([`categories:${category}`]);
        const result = await searchMods("", { facets, index: sort, limit: 20, offset: 0 });
        if (!cancelled) setMods(result.hits);
      } catch { /* non-critical */ }
      finally { if (!cancelled) setLoading(false); }
    })();
    return () => { cancelled = true; };
  }, [sort, category]);

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
          <h2 className="text-base font-black text-(--color-text-primary) tracking-tight">{title}</h2>
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
        <div className="flex items-center gap-2.5">
          <Icon size={16} className="text-(--color-text-secondary)" />
          <h2 className="text-base font-black text-(--color-text-primary) tracking-tight">{title}</h2>
        </div>
        <div className="flex items-center gap-1">
          <button
            onClick={() => scroll("left")}
            className={`w-7 h-7 rounded-full flex items-center justify-center transition-all cursor-pointer ${
              canScrollLeft
                ? "bg-(--color-surface-secondary) border border-(--color-border) text-(--color-text-secondary) hover:text-(--color-text-primary)"
                : "opacity-0 pointer-events-none"
            }`}
          >
            <ChevronLeft size={14} />
          </button>
          <button
            onClick={() => scroll("right")}
            className={`w-7 h-7 rounded-full flex items-center justify-center transition-all cursor-pointer ${
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
          <RowCard key={mod.id} mod={mod} idx={idx} onOpenMod={onOpenMod} />
        ))}
      </div>
    </div>
  );
}

// ─── Row card (horizontal scroll card for Netflix rows) ──────────────────────

function RowCard({
  mod,
  idx,
  onOpenMod,
}: {
  mod: ModrinthProject;
  idx: number;
  onOpenMod: (id: string) => void;
}) {
  return (
    <div
      onClick={() => onOpenMod(mod.slug || mod.id)}
      className="browse-mod-card group w-56 flex-shrink-0 rounded-3xl p-5 hover:-translate-y-1.5 hover:shadow-2xl transition-all duration-300 cursor-pointer relative overflow-hidden"
      style={{
        animationDelay: `${idx * 30}ms`,
        background: "var(--color-glass-bg, rgba(255,255,255,0.06))",
        backdropFilter: "blur(24px) saturate(1.4)",
        WebkitBackdropFilter: "blur(24px) saturate(1.4)",
        border: "1px solid var(--color-glass-border, rgba(255,255,255,0.08))",
        boxShadow: "0 4px 30px rgba(0,0,0,0.08), inset 0 1px 0 rgba(255,255,255,0.06)",
      }}
    >
      {/* Hover glow */}
      <div className="absolute inset-0 opacity-0 group-hover:opacity-100 transition-opacity duration-500 pointer-events-none" style={{ background: "radial-gradient(ellipse at 50% 0%, var(--color-accent-raw, 139 92 246) / 0.08, transparent 70%)" }} />

      <div className="relative z-10">
        {mod.icon_url ? (
          <img src={mod.icon_url} alt={mod.title} className="w-16 h-16 rounded-2xl object-cover shadow-lg ring-1 ring-white/10" />
        ) : (
          <div className="w-16 h-16 rounded-2xl flex items-center justify-center text-(--color-text-secondary)" style={{ backgroundColor: "rgba(255,255,255,0.06)" }}>
            <Sparkles size={22} />
          </div>
        )}
        <h3 className="mt-4 text-base font-black text-(--color-text-primary) truncate group-hover:text-(--color-accent) transition-colors tracking-tight">
          {mod.title}
        </h3>
        <p className="text-xs text-(--color-text-secondary) mt-0.5 font-semibold truncate">
          {mod.author}
        </p>
        <p className="text-xs text-(--color-text-secondary) line-clamp-2 mt-2.5 leading-relaxed">
          {mod.description}
        </p>
        <div className="mt-4 flex items-center gap-3">
          <span className="flex items-center gap-1.5 text-[11px] font-semibold text-(--color-text-secondary)">
            <Download size={11} />
            {(mod.downloads ?? 0) >= 1_000_000
              ? `${((mod.downloads ?? 0) / 1_000_000).toFixed(1)}M`
              : `${((mod.downloads ?? 0) / 1000).toFixed(0)}k`}
          </span>
          <span className="flex items-center gap-1.5 text-[11px] font-semibold text-(--color-text-secondary)">
            <Heart size={11} />
            {(mod.follows ?? mod.followers ?? 0).toLocaleString()}
          </span>
        </div>
      </div>
    </div>
  );
}

// ─── Grid card (for search results) ─────────────────────────────────────────

function GridCard({
  mod,
  idx,
  onOpenMod,
}: {
  mod: ModrinthProject;
  idx: number;
  onOpenMod: (id: string) => void;
}) {
  return (
    <div
      onClick={() => onOpenMod(mod.slug || mod.id)}
      className="browse-mod-card group rounded-3xl p-6 hover:-translate-y-1.5 hover:shadow-2xl transition-all duration-300 cursor-pointer relative overflow-hidden"
      style={{
        animationDelay: `${idx * 40}ms`,
        background: "var(--color-glass-bg, rgba(255,255,255,0.06))",
        backdropFilter: "blur(24px) saturate(1.4)",
        WebkitBackdropFilter: "blur(24px) saturate(1.4)",
        border: "1px solid var(--color-glass-border, rgba(255,255,255,0.08))",
        boxShadow: "0 4px 30px rgba(0,0,0,0.08), inset 0 1px 0 rgba(255,255,255,0.06)",
      }}
    >
      {/* Hover glow */}
      <div className="absolute inset-0 opacity-0 group-hover:opacity-100 transition-opacity duration-500 pointer-events-none" style={{ background: "radial-gradient(ellipse at 50% 0%, var(--color-accent-raw, 139 92 246) / 0.08, transparent 70%)" }} />

      <div className="relative z-10">
        <div className="flex items-start gap-5">
          {mod.icon_url ? (
            <img src={mod.icon_url} alt={mod.title} className="w-16 h-16 rounded-2xl object-cover shadow-lg ring-1 ring-white/10 flex-shrink-0" />
          ) : (
            <div className="w-16 h-16 rounded-2xl flex items-center justify-center text-(--color-text-secondary) flex-shrink-0" style={{ backgroundColor: "rgba(255,255,255,0.06)" }}>
              <Sparkles size={22} />
            </div>
          )}
          <div className="min-w-0 flex-1 pt-0.5">
            <h3 className="text-base font-black text-(--color-text-primary) truncate group-hover:text-(--color-accent) transition-colors tracking-tight">
              {mod.title}
            </h3>
            <p className="text-xs text-(--color-text-secondary) mt-0.5 font-semibold">
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
            <span className="flex items-center gap-1.5 text-xs font-semibold text-(--color-text-secondary)">
              <Download size={12} />
              {(mod.downloads ?? 0) >= 1_000_000
                ? `${((mod.downloads ?? 0) / 1_000_000).toFixed(1)}M`
                : `${((mod.downloads ?? 0) / 1000).toFixed(0)}k`}
            </span>
            <span className="flex items-center gap-1.5 text-xs font-semibold text-(--color-text-secondary)">
              <Heart size={12} />
              {(mod.follows ?? mod.followers ?? 0).toLocaleString()}
            </span>
          </div>
          {mod.categories.length > 0 && (
            <div className="flex gap-1.5">
              {mod.categories.slice(0, 2).map((cat) => (
                <span key={cat} className="text-[10px] font-bold px-2.5 py-1 rounded-full text-(--color-accent)" style={{ backgroundColor: "rgba(139, 92, 246, 0.08)" }}>
                  {cat}
                </span>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

// ─── Main BrowsePage ────────────────────────────────────────────────────────

export const BrowsePage = observer(({ onOpenMod }: BrowsePageProps) => {
  const { mods } = useStore();
  const [query, setQuery] = useState(mods.searchQuery);
  const [showFilters, setShowFilters] = useState(false);
  const [activeQuickFilter, setActiveQuickFilter] = useState<string | null>(null);
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    mods.loadFilters();
    if (mods.mods.length === 0 && !mods.loading) {
      mods.search("", 0);
    }
    requestAnimationFrame(() => setMounted(true));
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

  return (
    <div className="h-full overflow-y-auto">
      {/* Hero */}
      <div className="relative px-8 pt-8 pb-4 overflow-hidden">
        <div className="absolute inset-0 pointer-events-none">
          <div
            className="absolute top-0 left-1/4 w-[500px] h-[300px] rounded-full bg-(--color-accent)/6 blur-3xl transition-all duration-1000"
            style={{ opacity: mounted ? 1 : 0, transform: mounted ? "scale(1)" : "scale(0.5)" }}
          />
          <div
            className="absolute top-10 right-1/4 w-[300px] h-[200px] rounded-full bg-(--color-accent)/4 blur-3xl transition-all duration-1000 delay-300"
            style={{ opacity: mounted ? 1 : 0 }}
          />
        </div>

        <div className="relative z-10">
          <div className="home-slide-up" style={{ animationDelay: "0s" }}>
            <h1 className="text-4xl font-black tracking-tight text-(--color-text-primary)">
              Explore the <span className="void-text">Void</span>
            </h1>
            <p className="text-sm text-(--color-text-secondary) mt-1.5 max-w-md">
              Thousands of mods await. Find your next obsession.
            </p>
          </div>

          {/* Search */}
          <form onSubmit={handleSearch} className="flex gap-2 mt-6 home-slide-up" style={{ animationDelay: "0.1s" }}>
            <div className="relative flex-1">
              <Search size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-(--color-text-secondary)" />
              <input
                type="text"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Search for mods..."
                className="w-full pl-11 pr-4 py-3 rounded-2xl bg-(--color-surface-secondary) border border-(--color-border) text-sm text-(--color-text-primary) placeholder:text-(--color-text-secondary)/60 focus:outline-none focus:ring-2 focus:ring-(--color-accent)/40 focus:border-transparent transition-all"
              />
            </div>
            <button type="submit" className="px-6 py-3 rounded-2xl bg-(--color-accent) hover:bg-(--color-accent-hover) text-white text-sm font-bold transition-all cursor-pointer shadow-sm shadow-(--color-accent)/20 hover:shadow-md hover:-translate-y-0.5 active:translate-y-0">
              Search
            </button>
            <button
              type="button"
              onClick={() => setShowFilters(!showFilters)}
              className={`w-11 h-11 rounded-2xl flex items-center justify-center transition-all cursor-pointer ${
                showFilters || mods.hasActiveFilters
                  ? "bg-(--color-accent)/12 text-(--color-accent) ring-1 ring-(--color-accent)/25"
                  : "bg-(--color-surface-secondary) border border-(--color-border) text-(--color-text-secondary) hover:text-(--color-text-primary)"
              }`}
            >
              <SlidersHorizontal size={16} />
            </button>
          </form>

          {/* Quick filter cards */}
          <div className="mt-5 home-slide-up" style={{ animationDelay: "0.2s" }}>
            <div className="grid grid-cols-4 lg:grid-cols-8 gap-2">
              {QUICK_FILTERS.map((filter, idx) => {
                const Icon = filter.icon;
                const isActive = activeQuickFilter === filter.label;
                return (
                  <button
                    key={filter.label}
                    onClick={() => handleQuickFilter(filter)}
                    className={`browse-filter-card group relative flex flex-col items-center gap-2 px-3 py-4 rounded-2xl border transition-all duration-300 cursor-pointer overflow-hidden ${
                      isActive
                        ? "border-transparent shadow-lg scale-[1.02]"
                        : "border-(--color-border) bg-(--color-surface-secondary) hover:border-transparent hover:shadow-md hover:-translate-y-0.5"
                    }`}
                    style={{
                      animationDelay: `${idx * 60}ms`,
                      background: isActive ? `linear-gradient(135deg, ${filter.color}18, ${filter.color}08)` : undefined,
                    }}
                  >
                    <div className={`absolute inset-0 bg-gradient-to-br ${filter.gradient} transition-opacity duration-300 ${isActive ? "opacity-100" : "opacity-0 group-hover:opacity-100"}`} />
                    <div
                      className="relative z-10 w-9 h-9 rounded-xl flex items-center justify-center transition-all duration-300"
                      style={{ backgroundColor: isActive ? `${filter.color}22` : `${filter.color}12`, color: filter.color }}
                    >
                      <Icon size={18} />
                    </div>
                    <span className="relative z-10 text-[11px] font-bold transition-colors duration-300" style={{ color: isActive ? filter.color : undefined }}>
                      {filter.label}
                    </span>
                    {isActive && <div className="absolute bottom-0 left-1/2 -translate-x-1/2 w-6 h-0.5 rounded-full" style={{ backgroundColor: filter.color }} />}
                  </button>
                );
              })}
            </div>
          </div>
        </div>
      </div>

      <div className="px-8 pb-8">
        {/* Filters panel */}
        {showFilters && (
          <div className="rounded-2xl bg-(--color-surface-secondary) border border-(--color-border) p-5 space-y-3 mb-5 picker-dropdown">
            <div className="flex items-center justify-between">
              <h3 className="text-xs font-bold text-(--color-text-secondary) uppercase tracking-wider">Filters</h3>
              {mods.hasActiveFilters && (
                <button onClick={() => { mods.clearFilters(); setActiveQuickFilter(null); }} className="flex items-center gap-1 text-[11px] font-semibold text-(--color-accent) hover:underline cursor-pointer">
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
                  className={`px-3 py-1.5 rounded-lg text-[11px] font-semibold transition-all cursor-pointer ${
                    mods.sortIndex === opt.value
                      ? "bg-(--color-accent) text-white shadow-sm"
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
                  <button key={cat} onClick={() => mods.toggleCategory(cat)} className="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-(--color-accent)/10 text-(--color-accent) text-[11px] font-semibold cursor-pointer hover:bg-(--color-accent)/20 transition-colors">
                    {cat} <X size={10} />
                  </button>
                ))}
                {mods.selectedLoaders.map((loader) => (
                  <button key={loader} onClick={() => mods.toggleLoader(loader)} className="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-emerald-500/10 text-emerald-500 text-[11px] font-semibold cursor-pointer hover:bg-emerald-500/20 transition-colors">
                    {loader} <X size={10} />
                  </button>
                ))}
                {mods.selectedGameVersions.map((ver) => (
                  <button key={ver} onClick={() => mods.toggleGameVersion(ver)} className="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-sky-500/10 text-sky-500 text-[11px] font-semibold cursor-pointer hover:bg-sky-500/20 transition-colors">
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
                delay={0.25 + idx * 0.08}
              />
            ))}
          </div>
        )}

        {/* Search results grid */}
        {hasSearched && (
          <div className="space-y-4">
            {mods.loading ? (
              <div className="grid grid-cols-1 lg:grid-cols-2 xl:grid-cols-3 gap-4">
                {Array.from({ length: 9 }).map((_, i) => (
                  <div key={i} className="h-48 rounded-3xl bg-(--color-surface-secondary) border border-(--color-border) animate-pulse" style={{ animationDelay: `${i * 50}ms` }} />
                ))}
              </div>
            ) : (
              <>
                {mods.mods.length > 0 && (
                  <div className="flex items-center justify-between">
                    <p className="text-xs font-medium text-(--color-text-secondary) uppercase tracking-wider">
                      {mods.totalHits.toLocaleString()} results &middot; Page {mods.currentPage + 1} of {mods.totalPages}
                    </p>
                    {activeQuickFilter && (
                      <button onClick={() => { setActiveQuickFilter(null); mods.clearFilters(); mods.search("", 0); }} className="flex items-center gap-1.5 text-xs font-semibold text-(--color-accent) hover:underline cursor-pointer">
                        <X size={12} /> Clear filter
                      </button>
                    )}
                  </div>
                )}
                <div className="grid grid-cols-1 lg:grid-cols-2 xl:grid-cols-3 gap-4 pt-2">
                  {mods.mods.map((mod, idx) => (
                    <GridCard key={mod.id} mod={mod} idx={idx} onOpenMod={onOpenMod} />
                  ))}
                </div>
                {mods.mods.length === 0 && !mods.loading && (
                  <div className="flex flex-col items-center justify-center py-20 text-(--color-text-secondary)">
                    <Search size={40} className="mb-4 opacity-20" />
                    <p className="text-base font-bold text-(--color-text-primary)">No mods found</p>
                    <p className="text-sm mt-1">Try different search terms or filters</p>
                  </div>
                )}
                {mods.totalPages > 1 && (
                  <div className="flex items-center justify-center gap-1.5 pt-4">
                    <button onClick={() => mods.goToPage(mods.currentPage - 1)} disabled={mods.currentPage === 0} className="w-9 h-9 rounded-xl bg-(--color-surface-secondary) border border-(--color-border) flex items-center justify-center text-(--color-text-secondary) hover:text-(--color-text-primary) transition-colors cursor-pointer disabled:opacity-30 disabled:cursor-default">
                      <ChevronLeft size={16} />
                    </button>
                    {pageNumbers().map((p, i) =>
                      p === "..." ? (
                        <span key={`dots-${i}`} className="w-9 h-9 flex items-center justify-center text-xs text-(--color-text-secondary)">...</span>
                      ) : (
                        <button key={p} onClick={() => mods.goToPage(p)} className={`w-9 h-9 rounded-xl text-xs font-bold transition-all cursor-pointer ${mods.currentPage === p ? "bg-(--color-accent) text-white shadow-sm" : "bg-(--color-surface-secondary) border border-(--color-border) text-(--color-text-secondary) hover:text-(--color-text-primary)"}`}>
                          {p + 1}
                        </button>
                      )
                    )}
                    <button onClick={() => mods.goToPage(mods.currentPage + 1)} disabled={mods.currentPage >= mods.totalPages - 1} className="w-9 h-9 rounded-xl bg-(--color-surface-secondary) border border-(--color-border) flex items-center justify-center text-(--color-text-secondary) hover:text-(--color-text-primary) transition-colors cursor-pointer disabled:opacity-30 disabled:cursor-default">
                      <ChevronRight size={16} />
                    </button>
                  </div>
                )}
              </>
            )}
          </div>
        )}
      </div>
    </div>
  );
});
