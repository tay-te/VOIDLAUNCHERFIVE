const BASE_URL = "https://api.modrinth.com/v2";

export interface ModrinthProject {
  slug: string;
  title: string;
  description: string;
  categories: string[];
  client_side: string;
  server_side: string;
  project_type: string;
  downloads: number;
  icon_url: string | null;
  id: string;
  author?: string; // search API only
  team?: string; // detail API only
  date_modified: string;
  latest_version: string;
  versions: string[];
  follows?: number; // search API field name
  followers?: number; // detail API field name
  body?: string;
  gallery?: { url: string; title?: string; description?: string }[];
  license?: { id: string; name: string };
  source_url?: string | null;
  issues_url?: string | null;
  wiki_url?: string | null;
  discord_url?: string | null;
  donation_urls?: { id: string; platform: string; url: string }[];
  date_created?: string;
  game_versions?: string[];
  loaders?: string[];
}

export interface SearchResult {
  hits: ModrinthProject[];
  offset: number;
  limit: number;
  total_hits: number;
}

export interface ModVersionDependency {
  version_id: string | null;
  project_id: string | null;
  file_name: string | null;
  dependency_type: "required" | "optional" | "incompatible" | "embedded";
}

export interface ModVersion {
  id: string;
  project_id: string;
  name: string;
  version_number: string;
  version_type: "release" | "beta" | "alpha";
  game_versions: string[];
  loaders: string[];
  files: {
    url: string;
    filename: string;
    size: number;
    primary: boolean;
  }[];
  dependencies: ModVersionDependency[];
  date_published: string;
  downloads: number;
}

export interface CategoryTag {
  icon: string;
  name: string;
  project_type: string;
  header: string;
}

export interface LoaderTag {
  icon: string;
  name: string;
  supported_project_types: string[];
}

export interface GameVersionTag {
  version: string;
  version_type: "release" | "snapshot" | "old_beta" | "old_alpha";
  date: string;
  major: boolean;
}

// --- Cache layer ---
interface CacheEntry<T> {
  data: T;
  timestamp: number;
}

const cache = new Map<string, CacheEntry<unknown>>();
const CACHE_TTL = 5 * 60 * 1000; // 5 minutes
const SEARCH_CACHE_TTL = 2 * 60 * 1000; // 2 minutes for search results
const TAG_CACHE_TTL = 30 * 60 * 1000; // 30 minutes for tags (rarely change)

function getCached<T>(key: string, ttl: number): T | null {
  const entry = cache.get(key);
  if (entry && Date.now() - entry.timestamp < ttl) {
    return entry.data as T;
  }
  return null;
}

function setCache<T>(key: string, data: T): void {
  cache.set(key, { data, timestamp: Date.now() });
}

// Deduplicate in-flight requests
const inflight = new Map<string, Promise<unknown>>();

async function dedupedFetch<T>(key: string, fetcher: () => Promise<T>): Promise<T> {
  const existing = inflight.get(key);
  if (existing) return existing as Promise<T>;

  const promise = fetcher().finally(() => {
    inflight.delete(key);
  });
  inflight.set(key, promise);
  return promise;
}

const headers = {
  "User-Agent": "VoidLauncher/1.0.0 (contact@voidlauncher.app)",
};

async function apiFetch<T>(url: string): Promise<T> {
  const res = await fetch(url, { headers });
  if (!res.ok) {
    throw new Error(`Modrinth API error: ${res.status} ${res.statusText}`);
  }
  return res.json();
}

export async function searchMods(
  query: string,
  options?: {
    facets?: string[][];
    limit?: number;
    offset?: number;
    index?: "relevance" | "downloads" | "follows" | "newest" | "updated";
  }
): Promise<SearchResult> {
  const params = new URLSearchParams({
    query,
    limit: String(options?.limit ?? 20),
    offset: String(options?.offset ?? 0),
    index: options?.index ?? "relevance",
  });

  if (options?.facets) {
    params.set(
      "facets",
      JSON.stringify(options.facets.map((f) => f.map((v) => `${v}`)))
    );
  }

  const cacheKey = `search:${params.toString()}`;
  const cached = getCached<SearchResult>(cacheKey, SEARCH_CACHE_TTL);
  if (cached) return cached;

  return dedupedFetch(cacheKey, async () => {
    const data = await apiFetch<SearchResult>(`${BASE_URL}/search?${params}`);
    setCache(cacheKey, data);
    // Also cache individual projects from search results as previews
    for (const hit of data.hits) {
      setCache(`preview:${hit.slug}`, hit);
      setCache(`preview:${hit.id}`, hit);
    }
    return data;
  });
}

export async function getProject(idOrSlug: string): Promise<ModrinthProject> {
  const cacheKey = `project-full:${idOrSlug}`;
  const cached = getCached<ModrinthProject>(cacheKey, CACHE_TTL);
  if (cached) return cached;

  return dedupedFetch(cacheKey, async () => {
    const data = await apiFetch<ModrinthProject>(`${BASE_URL}/project/${idOrSlug}`);
    setCache(`project-full:${data.slug}`, data);
    setCache(`project-full:${data.id}`, data);
    return data;
  });
}

export async function getProjectVersions(
  idOrSlug: string,
  options?: {
    loaders?: string[];
    game_versions?: string[];
  }
): Promise<ModVersion[]> {
  const params = new URLSearchParams();
  if (options?.loaders) {
    params.set("loaders", JSON.stringify(options.loaders));
  }
  if (options?.game_versions) {
    params.set("game_versions", JSON.stringify(options.game_versions));
  }

  const cacheKey = `versions:${idOrSlug}:${params.toString()}`;
  const cached = getCached<ModVersion[]>(cacheKey, CACHE_TTL);
  if (cached) return cached;

  return dedupedFetch(cacheKey, async () => {
    const data = await apiFetch<ModVersion[]>(
      `${BASE_URL}/project/${idOrSlug}/version?${params}`
    );
    setCache(cacheKey, data);
    return data;
  });
}

export async function getFeaturedMods(): Promise<SearchResult> {
  return searchMods("", {
    facets: [["project_type:mod"]],
    index: "downloads",
    limit: 8,
  });
}

// --- Filter tags ---

export async function getCategories(): Promise<CategoryTag[]> {
  const cacheKey = "tags:categories";
  const cached = getCached<CategoryTag[]>(cacheKey, TAG_CACHE_TTL);
  if (cached) return cached;

  return dedupedFetch(cacheKey, async () => {
    const data = await apiFetch<CategoryTag[]>(`${BASE_URL}/tag/category`);
    setCache(cacheKey, data);
    return data;
  });
}

export async function getLoaders(): Promise<LoaderTag[]> {
  const cacheKey = "tags:loaders";
  const cached = getCached<LoaderTag[]>(cacheKey, TAG_CACHE_TTL);
  if (cached) return cached;

  return dedupedFetch(cacheKey, async () => {
    const data = await apiFetch<LoaderTag[]>(`${BASE_URL}/tag/loader`);
    setCache(cacheKey, data);
    return data;
  });
}

export async function getGameVersions(): Promise<GameVersionTag[]> {
  const cacheKey = "tags:game_versions";
  const cached = getCached<GameVersionTag[]>(cacheKey, TAG_CACHE_TTL);
  if (cached) return cached;

  return dedupedFetch(cacheKey, async () => {
    const data = await apiFetch<GameVersionTag[]>(`${BASE_URL}/tag/game_version`);
    setCache(cacheKey, data);
    return data;
  });
}

// Batch fetch multiple projects by ID
export async function getProjects(ids: string[]): Promise<ModrinthProject[]> {
  if (ids.length === 0) return [];
  const cacheKey = `projects:${ids.sort().join(",")}`;
  const cached = getCached<ModrinthProject[]>(cacheKey, CACHE_TTL);
  if (cached) return cached;

  return dedupedFetch(cacheKey, async () => {
    const data = await apiFetch<ModrinthProject[]>(
      `${BASE_URL}/projects?ids=${JSON.stringify(ids)}`
    );
    setCache(cacheKey, data);
    for (const p of data) {
      setCache(`project-full:${p.slug}`, p);
      setCache(`project-full:${p.id}`, p);
    }
    return data;
  });
}

// Fetch a single version by ID
export async function getVersion(id: string): Promise<ModVersion> {
  const cacheKey = `version:${id}`;
  const cached = getCached<ModVersion>(cacheKey, CACHE_TTL);
  if (cached) return cached;

  return dedupedFetch(cacheKey, async () => {
    const data = await apiFetch<ModVersion>(`${BASE_URL}/version/${id}`);
    setCache(cacheKey, data);
    return data;
  });
}

// Get a preview from search cache (partial data without body) for instant display
export function getCachedProjectPreview(idOrSlug: string): ModrinthProject | null {
  // Check full cache first, then preview cache
  return (
    getCached<ModrinthProject>(`project-full:${idOrSlug}`, CACHE_TTL) ||
    getCached<ModrinthProject>(`preview:${idOrSlug}`, CACHE_TTL)
  );
}
