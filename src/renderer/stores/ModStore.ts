import { makeAutoObservable, runInAction } from "mobx";
import {
  searchMods,
  getFeaturedMods,
  getProject,
  getProjectVersions,
  getCategories,
  getLoaders,
  getGameVersions,
  getCachedProjectPreview,
  type ModrinthProject,
  type ModVersion,
  type CategoryTag,
  type LoaderTag,
  type GameVersionTag,
} from "../api/modrinth";

const PAGE_SIZE = 16;

export type SortIndex = "relevance" | "downloads" | "follows" | "newest" | "updated";

export class ModStore {
  mods: ModrinthProject[] = [];
  featured: ModrinthProject[] = [];
  selectedMod: ModrinthProject | null = null;
  selectedModVersions: ModVersion[] = [];
  loading = false;
  detailLoading = false;
  detailError: string | null = null;
  searchQuery = "";
  totalHits = 0;
  currentPage = 0;

  // Filters
  sortIndex: SortIndex = "relevance";
  selectedCategories: string[] = [];
  selectedLoaders: string[] = [];
  selectedGameVersions: string[] = [];

  // Filter options (fetched from Modrinth tags API)
  availableCategories: CategoryTag[] = [];
  availableLoaders: LoaderTag[] = [];
  availableGameVersions: GameVersionTag[] = [];
  filtersLoaded = false;

  constructor() {
    makeAutoObservable(this);
  }

  get totalPages() {
    return Math.max(1, Math.ceil(this.totalHits / PAGE_SIZE));
  }

  get modCategories(): CategoryTag[] {
    return this.availableCategories.filter((c) => c.project_type === "mod");
  }

  get modLoaders(): LoaderTag[] {
    return this.availableLoaders.filter((l) =>
      l.supported_project_types.includes("mod")
    );
  }

  get majorGameVersions(): GameVersionTag[] {
    return this.availableGameVersions.filter(
      (v) => v.version_type === "release" && v.major
    );
  }

  async loadFilters() {
    if (this.filtersLoaded) return;
    try {
      const [categories, loaders, gameVersions] = await Promise.all([
        getCategories(),
        getLoaders(),
        getGameVersions(),
      ]);
      runInAction(() => {
        this.availableCategories = categories;
        this.availableLoaders = loaders;
        this.availableGameVersions = gameVersions;
        this.filtersLoaded = true;
      });
    } catch {
      // Filters are non-critical, search still works without them
    }
  }

  async loadFeatured() {
    this.loading = true;
    try {
      const result = await getFeaturedMods();
      runInAction(() => {
        this.featured = result.hits;
      });
    } finally {
      runInAction(() => {
        this.loading = false;
      });
    }
  }

  private buildFacets(): string[][] {
    // Modrinth facets: each inner array is OR, outer arrays are AND
    const facets: string[][] = [["project_type:mod"]];
    if (this.selectedCategories.length > 0) {
      // Multiple categories = OR within the group
      facets.push(this.selectedCategories.map((cat) => `categories:${cat}`));
    }
    if (this.selectedLoaders.length > 0) {
      facets.push(this.selectedLoaders.map((loader) => `categories:${loader}`));
    }
    if (this.selectedGameVersions.length > 0) {
      facets.push(this.selectedGameVersions.map((ver) => `versions:${ver}`));
    }
    return facets;
  }

  async search(query: string, page = 0) {
    this.searchQuery = query;
    this.currentPage = page;
    this.loading = true;
    try {
      const result = await searchMods(query, {
        facets: this.buildFacets(),
        limit: PAGE_SIZE,
        offset: page * PAGE_SIZE,
        index: this.sortIndex,
      });
      runInAction(() => {
        this.mods = result.hits;
        this.totalHits = result.total_hits;
      });
    } finally {
      runInAction(() => {
        this.loading = false;
      });
    }
  }

  async goToPage(page: number) {
    await this.search(this.searchQuery, page);
  }

  setSortIndex(index: SortIndex) {
    this.sortIndex = index;
    this.search(this.searchQuery, 0);
  }

  toggleCategory(cat: string) {
    const idx = this.selectedCategories.indexOf(cat);
    if (idx >= 0) {
      this.selectedCategories.splice(idx, 1);
    } else {
      this.selectedCategories.push(cat);
    }
    this.search(this.searchQuery, 0);
  }

  toggleLoader(loader: string) {
    const idx = this.selectedLoaders.indexOf(loader);
    if (idx >= 0) {
      this.selectedLoaders.splice(idx, 1);
    } else {
      this.selectedLoaders.push(loader);
    }
    this.search(this.searchQuery, 0);
  }

  toggleGameVersion(version: string) {
    const idx = this.selectedGameVersions.indexOf(version);
    if (idx >= 0) {
      this.selectedGameVersions.splice(idx, 1);
    } else {
      this.selectedGameVersions.push(version);
    }
    this.search(this.searchQuery, 0);
  }

  clearFilters() {
    this.selectedCategories = [];
    this.selectedLoaders = [];
    this.selectedGameVersions = [];
    this.sortIndex = "relevance";
    this.search(this.searchQuery, 0);
  }

  get hasActiveFilters() {
    return (
      this.selectedCategories.length > 0 ||
      this.selectedLoaders.length > 0 ||
      this.selectedGameVersions.length > 0 ||
      this.sortIndex !== "relevance"
    );
  }

  async selectMod(idOrSlug: string) {
    // Show cached preview instantly if available
    const preview = getCachedProjectPreview(idOrSlug);
    if (preview) {
      this.selectedMod = preview;
    } else {
      this.selectedMod = null;
    }

    this.detailLoading = true;
    this.detailError = null;
    this.selectedModVersions = [];
    try {
      const [project, versions] = await Promise.all([
        getProject(idOrSlug),
        getProjectVersions(idOrSlug),
      ]);
      runInAction(() => {
        this.selectedMod = project;
        this.selectedModVersions = versions;
      });
    } catch (err) {
      runInAction(() => {
        this.detailError =
          err instanceof Error ? err.message : "Failed to load mod";
      });
    } finally {
      runInAction(() => {
        this.detailLoading = false;
      });
    }
  }

  clearSelectedMod() {
    this.selectedMod = null;
    this.selectedModVersions = [];
  }
}
