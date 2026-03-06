import { useState, useEffect, useRef } from "react";
import { observer } from "mobx-react-lite";
import { useStore } from "../stores";
import type { SortIndex } from "../stores/ModStore";
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
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false);
      }
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
        <ChevronDown size={12} className={`transition-transform ${open ? "rotate-180" : ""}`} />
      </button>

      {open && (
        <div className="absolute top-full left-0 mt-1.5 w-52 max-h-60 overflow-y-auto rounded-xl bg-(--color-surface-secondary) border border-(--color-border) shadow-lg z-50 p-1.5">
          {options.map((opt) => (
            <button
              key={opt.value}
              onClick={() => onToggle(opt.value)}
              className={`w-full text-left px-3 py-2 rounded-lg text-xs font-medium transition-all cursor-pointer ${
                selected.includes(opt.value)
                  ? "bg-(--color-accent)/12 text-(--color-accent)"
                  : "text-(--color-text-secondary) hover:bg-(--color-surface-tertiary) hover:text-(--color-text-primary)"
              }`}
            >
              {opt.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

export const BrowsePage = observer(({ onOpenMod }: BrowsePageProps) => {
  const { mods } = useStore();
  const [query, setQuery] = useState(mods.searchQuery);
  const [showFilters, setShowFilters] = useState(false);

  useEffect(() => {
    mods.loadFilters();
    if (mods.mods.length === 0 && !mods.loading) {
      mods.search("", 0);
    }
  }, []);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    mods.search(query, 0);
  };

  const pageNumbers = () => {
    const pages: (number | "...")[] = [];
    const total = mods.totalPages;
    const cur = mods.currentPage;

    if (total <= 7) {
      for (let i = 0; i < total; i++) pages.push(i);
    } else {
      pages.push(0);
      if (cur > 2) pages.push("...");
      for (
        let i = Math.max(1, cur - 1);
        i <= Math.min(total - 2, cur + 1);
        i++
      )
        pages.push(i);
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
    <div className="p-8 space-y-5">
      <div>
        <h1 className="text-3xl font-black tracking-tight text-(--color-text-primary)">
          Explore the <span className="void-text">Void</span>
        </h1>
        <p className="text-sm text-(--color-text-secondary) mt-1">
          Thousands of mods await. Find your next obsession.
        </p>
      </div>

      {/* Search */}
      <form onSubmit={handleSearch} className="flex gap-2">
        <div className="relative flex-1">
          <Search
            size={16}
            className="absolute left-3.5 top-1/2 -translate-y-1/2 text-(--color-text-secondary)"
          />
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search for mods..."
            className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-(--color-surface-secondary) border border-(--color-border) text-sm text-(--color-text-primary) placeholder:text-(--color-text-secondary)/60 focus:outline-none focus:ring-2 focus:ring-(--color-accent)/40 focus:border-transparent transition-all"
          />
        </div>
        <button
          type="submit"
          className="px-5 py-2.5 rounded-xl bg-(--color-accent) hover:bg-(--color-accent-hover) text-white text-sm font-semibold transition-all cursor-pointer shadow-sm shadow-(--color-accent)/20"
        >
          Search
        </button>
        <button
          type="button"
          onClick={() => setShowFilters(!showFilters)}
          className={`w-10 h-10 rounded-xl flex items-center justify-center transition-all cursor-pointer ${
            showFilters || mods.hasActiveFilters
              ? "bg-(--color-accent)/12 text-(--color-accent) ring-1 ring-(--color-accent)/25"
              : "bg-(--color-surface-secondary) border border-(--color-border) text-(--color-text-secondary) hover:text-(--color-text-primary)"
          }`}
        >
          <SlidersHorizontal size={16} />
        </button>
      </form>

      {/* Filters */}
      {showFilters && (
        <div className="rounded-xl bg-(--color-surface-secondary) border border-(--color-border) p-4 space-y-3">
          <div className="flex items-center justify-between">
            <h3 className="text-xs font-bold text-(--color-text-secondary) uppercase tracking-wider">
              Filters
            </h3>
            {mods.hasActiveFilters && (
              <button
                onClick={() => mods.clearFilters()}
                className="flex items-center gap-1 text-[11px] font-semibold text-(--color-accent) hover:underline cursor-pointer"
              >
                <X size={11} />
                Clear all
              </button>
            )}
          </div>

          <div className="flex flex-wrap gap-1.5">
            <ArrowDownWideNarrow size={13} className="text-(--color-text-secondary) mt-2 mr-1" />
            {SORT_OPTIONS.map((opt) => (
              <button
                key={opt.value}
                onClick={() => mods.setSortIndex(opt.value)}
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
            <FilterDropdown
              label="Category"
              options={categoryOptions}
              selected={mods.selectedCategories}
              onToggle={(v) => mods.toggleCategory(v)}
            />
            <FilterDropdown
              label="Loader"
              options={loaderOptions}
              selected={mods.selectedLoaders}
              onToggle={(v) => mods.toggleLoader(v)}
            />
            <FilterDropdown
              label="Game Version"
              options={versionOptions}
              selected={mods.selectedGameVersions}
              onToggle={(v) => mods.toggleGameVersion(v)}
            />
          </div>

          {/* Active filter pills */}
          {mods.hasActiveFilters && (
            <div className="flex flex-wrap gap-1.5 pt-1">
              {mods.selectedCategories.map((cat) => (
                <button
                  key={cat}
                  onClick={() => mods.toggleCategory(cat)}
                  className="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-(--color-accent)/10 text-(--color-accent) text-[11px] font-semibold cursor-pointer hover:bg-(--color-accent)/20 transition-colors"
                >
                  {cat}
                  <X size={10} />
                </button>
              ))}
              {mods.selectedLoaders.map((loader) => (
                <button
                  key={loader}
                  onClick={() => mods.toggleLoader(loader)}
                  className="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-emerald-500/10 text-emerald-500 text-[11px] font-semibold cursor-pointer hover:bg-emerald-500/20 transition-colors"
                >
                  {loader}
                  <X size={10} />
                </button>
              ))}
              {mods.selectedGameVersions.map((ver) => (
                <button
                  key={ver}
                  onClick={() => mods.toggleGameVersion(ver)}
                  className="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-sky-500/10 text-sky-500 text-[11px] font-semibold cursor-pointer hover:bg-sky-500/20 transition-colors"
                >
                  {ver}
                  <X size={10} />
                </button>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Results */}
      {mods.loading ? (
        <div className="grid grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-3">
          {Array.from({ length: 16 }).map((_, i) => (
            <div
              key={i}
              className="h-44 rounded-2xl bg-(--color-surface-secondary) border border-(--color-border) animate-pulse"
            />
          ))}
        </div>
      ) : (
        <>
          {mods.mods.length > 0 && (
            <p className="text-xs font-medium text-(--color-text-secondary) uppercase tracking-wider">
              {mods.totalHits.toLocaleString()} results &middot; Page{" "}
              {mods.currentPage + 1} of {mods.totalPages}
            </p>
          )}

          {/* Card Grid */}
          <div className="grid grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-3">
            {mods.mods.map((mod) => (
              <div
                key={mod.id}
                onClick={() => onOpenMod(mod.slug || mod.id)}
                className="group rounded-2xl bg-(--color-surface-secondary) border border-(--color-border) p-4 hover:border-(--color-accent)/30 hover:shadow-md hover:shadow-(--color-accent)/5 transition-all duration-200 cursor-pointer"
              >
                {/* Icon */}
                {mod.icon_url ? (
                  <img
                    src={mod.icon_url}
                    alt={mod.title}
                    className="w-12 h-12 rounded-xl object-cover"
                  />
                ) : (
                  <div className="w-12 h-12 rounded-xl bg-(--color-surface-tertiary) flex items-center justify-center text-(--color-text-secondary)">
                    <Sparkles size={18} />
                  </div>
                )}

                {/* Title + Author */}
                <div className="mt-3 min-w-0">
                  <h3 className="text-sm font-bold text-(--color-text-primary) truncate">
                    {mod.title}
                  </h3>
                  <p className="text-[11px] text-(--color-text-secondary) mt-0.5 font-medium">
                    {mod.author}
                  </p>
                </div>

                {/* Description */}
                <p className="text-xs text-(--color-text-secondary) line-clamp-2 mt-2 leading-relaxed">
                  {mod.description}
                </p>

                {/* Footer */}
                <div className="mt-3 flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <span className="flex items-center gap-1 text-[11px] font-medium text-(--color-text-secondary)">
                      <Download size={11} />
                      {(mod.downloads ?? 0) >= 1_000_000
                        ? `${((mod.downloads ?? 0) / 1_000_000).toFixed(1)}M`
                        : `${((mod.downloads ?? 0) / 1000).toFixed(0)}k`}
                    </span>
                    <span className="flex items-center gap-1 text-[11px] font-medium text-(--color-text-secondary)">
                      <Heart size={11} />
                      {(mod.follows ?? mod.followers ?? 0).toLocaleString()}
                    </span>
                  </div>
                  <ArrowRight
                    size={13}
                    className="text-(--color-text-secondary) opacity-0 group-hover:opacity-100 transition-opacity"
                  />
                </div>

                {/* Categories */}
                {mod.categories.length > 0 && (
                  <div className="flex flex-wrap gap-1 mt-2">
                    {mod.categories.slice(0, 2).map((cat) => (
                      <span
                        key={cat}
                        className="text-[10px] font-medium px-2 py-0.5 rounded-md bg-(--color-accent)/8 text-(--color-accent)"
                      >
                        {cat}
                      </span>
                    ))}
                  </div>
                )}
              </div>
            ))}
          </div>

          {/* Empty state */}
          {mods.mods.length === 0 && !mods.loading && mods.searchQuery && (
            <div className="flex flex-col items-center justify-center py-20 text-(--color-text-secondary)">
              <Search size={40} className="mb-4 opacity-20" />
              <p className="text-base font-bold text-(--color-text-primary)">No mods found</p>
              <p className="text-sm mt-1">Try different search terms or filters</p>
            </div>
          )}

          {/* Pagination */}
          {mods.totalPages > 1 && (
            <div className="flex items-center justify-center gap-1.5 pt-4">
              <button
                onClick={() => mods.goToPage(mods.currentPage - 1)}
                disabled={mods.currentPage === 0}
                className="w-9 h-9 rounded-xl bg-(--color-surface-secondary) border border-(--color-border) flex items-center justify-center text-(--color-text-secondary) hover:text-(--color-text-primary) transition-colors cursor-pointer disabled:opacity-30 disabled:cursor-default"
              >
                <ChevronLeft size={16} />
              </button>

              {pageNumbers().map((p, i) =>
                p === "..." ? (
                  <span
                    key={`dots-${i}`}
                    className="w-9 h-9 flex items-center justify-center text-xs text-(--color-text-secondary)"
                  >
                    ...
                  </span>
                ) : (
                  <button
                    key={p}
                    onClick={() => mods.goToPage(p)}
                    className={`w-9 h-9 rounded-xl text-xs font-bold transition-all cursor-pointer ${
                      mods.currentPage === p
                        ? "bg-(--color-accent) text-white shadow-sm"
                        : "bg-(--color-surface-secondary) border border-(--color-border) text-(--color-text-secondary) hover:text-(--color-text-primary)"
                    }`}
                  >
                    {p + 1}
                  </button>
                )
              )}

              <button
                onClick={() => mods.goToPage(mods.currentPage + 1)}
                disabled={mods.currentPage >= mods.totalPages - 1}
                className="w-9 h-9 rounded-xl bg-(--color-surface-secondary) border border-(--color-border) flex items-center justify-center text-(--color-text-secondary) hover:text-(--color-text-primary) transition-colors cursor-pointer disabled:opacity-30 disabled:cursor-default"
              >
                <ChevronRight size={16} />
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
});
