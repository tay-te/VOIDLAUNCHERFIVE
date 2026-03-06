import { makeAutoObservable, runInAction } from "mobx";

export interface InstalledMod {
  projectId: string;
  versionId: string;
  filename: string;
  title: string;
  iconUrl: string | null;
}

export interface Instance {
  id: string;
  name: string;
  version: string;
  loader: "vanilla" | "fabric" | "forge";
  iconColor: string;
  memoryMb: number;
  modCount: number;
  installedMods: InstalledMod[];
  lastPlayed: string | null;
  dateCreated: string;
  // User ownership
  ownerUuid: string;
  ownerName: string;
  // Sharing
  shareCode: string | null;
  sharedInstanceId: string | null;
  isCollaborative: boolean;
  syncedAt: string | null;
}

const LEGACY_STORAGE_KEY = "void-instances";

function userStorageKey(mcUuid: string): string {
  return `void-instances-${mcUuid}`;
}

export class InstanceStore {
  instances: Instance[] = [];
  currentUserUuid: string | null = null;
  currentUserName: string | null = null;

  constructor() {
    makeAutoObservable(this);
  }

  setUser(uuid: string, name: string) {
    this.currentUserUuid = uuid;
    this.currentUserName = name;
    this.migrateLegacy(uuid, name);
    this.load();
  }

  clearUser() {
    this.currentUserUuid = null;
    this.currentUserName = null;
    this.instances = [];
  }

  private get storageKey(): string | null {
    return this.currentUserUuid ? userStorageKey(this.currentUserUuid) : null;
  }

  private migrateLegacy(uuid: string, name: string) {
    const key = userStorageKey(uuid);
    if (localStorage.getItem(key)) return; // already migrated
    const legacy = localStorage.getItem(LEGACY_STORAGE_KEY);
    if (!legacy) return;
    try {
      const parsed = JSON.parse(legacy) as Instance[];
      const migrated = parsed.map((inst) => ({
        ...inst,
        ownerUuid: inst.ownerUuid || uuid,
        ownerName: inst.ownerName || name,
        shareCode: inst.shareCode ?? null,
        sharedInstanceId: inst.sharedInstanceId ?? null,
        isCollaborative: inst.isCollaborative ?? false,
        syncedAt: inst.syncedAt ?? null,
        installedMods: inst.installedMods ?? [],
      }));
      localStorage.setItem(key, JSON.stringify(migrated));
      localStorage.removeItem(LEGACY_STORAGE_KEY);
    } catch {
      // ignore corrupt data
    }
  }

  private load() {
    const key = this.storageKey;
    if (!key) return;
    try {
      const data = localStorage.getItem(key);
      if (data) {
        const parsed = JSON.parse(data) as Instance[];
        this.instances = parsed.map((inst) => ({
          ...inst,
          installedMods: inst.installedMods ?? [],
          modCount: inst.installedMods?.length ?? inst.modCount ?? 0,
          ownerUuid: inst.ownerUuid || this.currentUserUuid || "",
          ownerName: inst.ownerName || this.currentUserName || "Player",
          shareCode: inst.shareCode ?? null,
          sharedInstanceId: inst.sharedInstanceId ?? null,
          isCollaborative: inst.isCollaborative ?? false,
          syncedAt: inst.syncedAt ?? null,
        }));
      }
    } catch {
      // ignore corrupt data
    }
  }

  private persist() {
    const key = this.storageKey;
    if (!key) return;
    localStorage.setItem(key, JSON.stringify(this.instances));
  }

  create(data: Pick<Instance, "name" | "version" | "loader" | "iconColor"> & { memoryMb?: number }) {
    if (!this.currentUserUuid || !this.currentUserName) return null;
    const instance: Instance = {
      ...data,
      id: crypto.randomUUID(),
      memoryMb: data.memoryMb ?? 4096,
      modCount: 0,
      installedMods: [],
      lastPlayed: null,
      dateCreated: new Date().toISOString(),
      ownerUuid: this.currentUserUuid,
      ownerName: this.currentUserName,
      shareCode: null,
      sharedInstanceId: null,
      isCollaborative: false,
      syncedAt: null,
    };
    this.instances.unshift(instance);
    this.persist();
    return instance;
  }

  remove(id: string) {
    this.instances = this.instances.filter((i) => i.id !== id);
    this.persist();
  }

  update(
    id: string,
    data: Partial<Pick<Instance, "name" | "version" | "loader" | "iconColor" | "memoryMb" | "shareCode" | "sharedInstanceId" | "isCollaborative" | "syncedAt">>
  ) {
    const inst = this.instances.find((i) => i.id === id);
    if (inst) {
      Object.assign(inst, data);
      this.persist();
    }
  }

  addMod(instanceId: string, mod: InstalledMod) {
    const inst = this.instances.find((i) => i.id === instanceId);
    if (inst) {
      // Don't add duplicates
      if (inst.installedMods.some((m) => m.projectId === mod.projectId)) return;
      inst.installedMods.push(mod);
      inst.modCount = inst.installedMods.length;
      this.persist();
    }
  }

  updateMod(instanceId: string, projectId: string, updates: Partial<InstalledMod>) {
    const inst = this.instances.find((i) => i.id === instanceId);
    if (inst) {
      const mod = inst.installedMods.find((m) => m.projectId === projectId);
      if (mod) {
        Object.assign(mod, updates);
        this.persist();
      }
    }
  }

  removeMod(instanceId: string, projectId: string) {
    const inst = this.instances.find((i) => i.id === instanceId);
    if (inst) {
      inst.installedMods = inst.installedMods.filter((m) => m.projectId !== projectId);
      inst.modCount = inst.installedMods.length;
      this.persist();
    }
  }

  hasModInstalled(instanceId: string, projectId: string): boolean {
    const inst = this.instances.find((i) => i.id === instanceId);
    return inst?.installedMods.some((m) => m.projectId === projectId) ?? false;
  }

  launch(id: string) {
    const inst = this.instances.find((i) => i.id === id);
    if (inst) {
      inst.lastPlayed = new Date().toISOString();
      this.persist();
    }
  }

  /** Create a local instance from shared data (importing a share code) */
  createFromShared(data: {
    name: string;
    version: string;
    loader: "vanilla" | "fabric" | "forge";
    iconColor: string;
    sharedInstanceId: string;
    shareCode: string;
    isCollaborative: boolean;
    mods: InstalledMod[];
  }) {
    if (!this.currentUserUuid || !this.currentUserName) return null;
    const instance: Instance = {
      id: crypto.randomUUID(),
      name: data.name,
      version: data.version,
      loader: data.loader,
      iconColor: data.iconColor,
      memoryMb: 4096,
      modCount: data.mods.length,
      installedMods: data.mods,
      lastPlayed: null,
      dateCreated: new Date().toISOString(),
      ownerUuid: this.currentUserUuid,
      ownerName: this.currentUserName,
      shareCode: data.shareCode,
      sharedInstanceId: data.sharedInstanceId,
      isCollaborative: data.isCollaborative,
      syncedAt: new Date().toISOString(),
    };
    this.instances.unshift(instance);
    this.persist();
    return instance;
  }
}
