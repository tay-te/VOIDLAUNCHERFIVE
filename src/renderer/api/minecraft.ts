export interface MinecraftVersion {
  id: string;
  type: "release" | "snapshot" | "old_beta" | "old_alpha";
  releaseTime: string;
}

interface VersionManifest {
  latest: { release: string; snapshot: string };
  versions: MinecraftVersion[];
}

let cached: VersionManifest | null = null;

export async function getMinecraftVersions(): Promise<VersionManifest> {
  if (cached) return cached;
  const res = await fetch(
    "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json"
  );
  if (!res.ok) {
    throw new Error(`Failed to fetch versions: ${res.status}`);
  }
  const data = await res.json();
  cached = {
    latest: data.latest,
    versions: data.versions.map((v: any) => ({
      id: v.id,
      type: v.type,
      releaseTime: v.releaseTime,
    })),
  };
  return cached;
}
