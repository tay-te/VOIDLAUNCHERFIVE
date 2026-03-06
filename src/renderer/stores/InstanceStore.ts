import { makeAutoObservable } from "mobx";

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
}

const STORAGE_KEY = "void-instances";

export class InstanceStore {
  instances: Instance[] = [];

  constructor() {
    makeAutoObservable(this);
    this.load();
  }

  private load() {
    try {
      const data = localStorage.getItem(STORAGE_KEY);
      if (data) {
        const parsed = JSON.parse(data) as Instance[];
        this.instances = parsed.map((inst) => ({
          ...inst,
          installedMods: inst.installedMods ?? [],
          modCount: inst.installedMods?.length ?? inst.modCount ?? 0,
        }));
      }
    } catch {
      // ignore corrupt data
    }
  }

  private persist() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(this.instances));
  }

  create(data: Pick<Instance, "name" | "version" | "loader" | "iconColor"> & { memoryMb?: number }) {
    const instance: Instance = {
      ...data,
      id: crypto.randomUUID(),
      memoryMb: data.memoryMb ?? 4096,
      modCount: 0,
      installedMods: [],
      lastPlayed: null,
      dateCreated: new Date().toISOString(),
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
    data: Partial<Pick<Instance, "name" | "version" | "loader" | "iconColor" | "memoryMb">>
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
}
