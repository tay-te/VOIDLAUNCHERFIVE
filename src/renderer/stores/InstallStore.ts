import { makeAutoObservable, runInAction } from "mobx";
import {
  getProject,
  getProjectVersions,
  getProjects,
  getVersion,
  type ModrinthProject,
  type ModVersion,
  type ModVersionDependency,
} from "../api/modrinth";
import type { Instance, InstalledMod } from "./InstanceStore";
import type { SharedInstanceData } from "./SharingStore";
import type { RootStore } from "./index";

type Loader = Instance["loader"];

interface InstallContext {
  version: string;
  loader: Loader;
  installedMods: InstalledMod[];
}

interface CreateInstanceSpec {
  name: string;
  version: string;
  loader: Loader;
  iconColor: string;
}

interface InstallOperation {
  project: ModrinthProject;
  version: ModVersion;
  existingMod: InstalledMod | null;
  kind: "install" | "update";
}

interface InstallPlan {
  target: InstallContext;
  rootVersion: ModVersion;
  operations: InstallOperation[];
  conflicts: ModrinthProject[];
  missing: string[];
  dependencyTree: InstallDependencyNode[];
  createdSpec?: CreateInstanceSpec;
}

export interface InstallDependencyNode {
  projectId: string;
  title: string;
  iconUrl: string | null;
  versionId: string | null;
  versionNumber: string | null;
  dependencyType: "root" | "required" | "optional" | "embedded" | "incompatible";
  status: "install" | "update" | "already-installed" | "missing" | "conflict" | "optional";
  children: InstallDependencyNode[];
}

export interface InstallPreview {
  instance: Instance | null;
  plan: InstallPlan;
}

interface DownloadBinding {
  instanceId: string;
  filename: string;
  basePercent: number;
  spanPercent: number;
}

export interface InstallJob {
  id: string;
  kind:
    | "install-mod"
    | "sync-shared"
    | "switch-version"
    | "remove-mod"
    | "reconcile";
  title: string;
  subtitle: string;
  currentItem: string;
  instanceName: string;
  iconColor: string;
  completedItems: number;
  totalItems: number;
  percent: number;
  status: "running" | "done" | "error";
  error?: string;
}

interface InstallOptions {
  preferredInstanceId?: string | null;
  preferredVersion?: ModVersion | null;
  strictInstance?: boolean;
  strictVersion?: boolean;
  allowCreateInstance?: boolean;
  jobKind?: InstallJob["kind"];
}

const LOADER_ICON_COLOR: Record<Loader, string> = {
  vanilla: "#22c55e",
  fabric: "#dba678",
  forge: "#3b82f6",
};

const LOADER_LABEL: Record<Loader, string> = {
  vanilla: "Vanilla",
  fabric: "Fabric",
  forge: "Forge",
};

const LOADER_PRIORITY: Record<Loader, number> = {
  fabric: 0,
  forge: 1,
  vanilla: 2,
};

const RELEASE_PRIORITY: Record<ModVersion["version_type"], number> = {
  release: 0,
  beta: 1,
  alpha: 2,
};

function compareMinecraftVersions(a: string, b: string) {
  const aParts = a.split(".").map((part) => Number.parseInt(part, 10) || 0);
  const bParts = b.split(".").map((part) => Number.parseInt(part, 10) || 0);

  for (let index = 0; index < Math.max(aParts.length, bParts.length); index += 1) {
    const diff = (aParts[index] ?? 0) - (bParts[index] ?? 0);
    if (diff !== 0) return diff;
  }

  return 0;
}

function choosePrimaryFile(version: ModVersion) {
  return version.files.find((file) => file.primary) ?? version.files[0] ?? null;
}

function selectSupportedLoader(loaders: string[]): Loader | null {
  const normalized = loaders.map((loader) => loader.toLowerCase());
  if (normalized.includes("fabric")) return "fabric";
  if (normalized.includes("forge")) return "forge";
  if (normalized.includes("vanilla")) return "vanilla";
  return null;
}

function isVersionCompatible(version: ModVersion, target: InstallContext) {
  return (
    version.game_versions.includes(target.version) &&
    version.loaders.some((loader) => loader.toLowerCase() === target.loader)
  );
}

function sortVersionsForTarget(versions: ModVersion[], target: InstallContext) {
  return [...versions]
    .filter((version) => isVersionCompatible(version, target))
    .sort((a, b) => {
      const releaseDiff = RELEASE_PRIORITY[a.version_type] - RELEASE_PRIORITY[b.version_type];
      if (releaseDiff !== 0) return releaseDiff;
      return (
        new Date(b.date_published).getTime() - new Date(a.date_published).getTime()
      );
    });
}

function sortVersionsForNewInstance(versions: ModVersion[]) {
  return [...versions]
    .filter((version) => selectSupportedLoader(version.loaders) !== null)
    .sort((a, b) => {
      const releaseDiff = RELEASE_PRIORITY[a.version_type] - RELEASE_PRIORITY[b.version_type];
      if (releaseDiff !== 0) return releaseDiff;

      const loaderDiff =
        LOADER_PRIORITY[selectSupportedLoader(a.loaders) ?? "forge"] -
        LOADER_PRIORITY[selectSupportedLoader(b.loaders) ?? "forge"];
      if (loaderDiff !== 0) return loaderDiff;

      const versionA = [...a.game_versions].sort(compareMinecraftVersions).pop() ?? "0.0.0";
      const versionB = [...b.game_versions].sort(compareMinecraftVersions).pop() ?? "0.0.0";
      const gameVersionDiff = compareMinecraftVersions(versionB, versionA);
      if (gameVersionDiff !== 0) return gameVersionDiff;

      return (
        new Date(b.date_published).getTime() - new Date(a.date_published).getTime()
      );
    });
}

function buildUniqueInstanceName(existingNames: string[], base: string) {
  if (!existingNames.includes(base)) return base;

  let suffix = 2;
  while (existingNames.includes(`${base} ${suffix}`)) {
    suffix += 1;
  }

  return `${base} ${suffix}`;
}

export class InstallStore {
  preferredInstanceId: string | null = null;
  activeJob: InstallJob | null = null;
  toastVisible = false;
  projectBusy = new Set<string>();
  instanceProjectBusy = new Set<string>();
  sharedBusy = new Set<string>();
  sharedProgress = new Map<string, { message: string; percent: number }>();

  private jobCounter = 0;
  private toastDismissTimer: ReturnType<typeof setTimeout> | null = null;
  private downloadBinding: DownloadBinding | null = null;

  constructor(private root: RootStore) {
    makeAutoObservable<this, "root" | "jobCounter" | "toastDismissTimer" | "downloadBinding">(
      this,
      {
        root: false,
        jobCounter: false,
        toastDismissTimer: false,
        downloadBinding: false,
      }
    );

    window.electronAPI.onDownloadProgress((data) => {
      if (
        !this.activeJob ||
        !this.downloadBinding ||
        this.activeJob.status !== "running" ||
        this.downloadBinding.instanceId !== data.instanceId ||
        this.downloadBinding.filename !== data.filename
      ) {
        return;
      }

      runInAction(() => {
        if (!this.activeJob) return;
        this.activeJob.percent = Math.min(
          99,
          Math.round(
            this.downloadBinding!.basePercent +
              (data.percent / 100) * this.downloadBinding!.spanPercent
          )
        );
      });
    });
  }

  setPreferredInstance(id: string | null) {
    this.preferredInstanceId = id;
  }

  clearPreferredInstance() {
    this.preferredInstanceId = null;
  }

  isProjectInstalling(projectId: string) {
    return this.projectBusy.has(projectId);
  }

  isInstanceProjectBusy(instanceId: string, projectId: string) {
    return this.instanceProjectBusy.has(`${instanceId}:${projectId}`);
  }

  isSharedBusy(sharedId: string) {
    return this.sharedBusy.has(sharedId);
  }

  getSharedProgress(sharedId: string) {
    return this.sharedProgress.get(sharedId) ?? null;
  }

  dismissToast() {
    if (this.toastDismissTimer) clearTimeout(this.toastDismissTimer);
    this.toastDismissTimer = null;
    this.toastVisible = false;
    this.activeJob = null;
  }

  async reconcileAllInstances() {
    for (const instance of this.root.instances.instances) {
      await this.reconcileInstance(instance.id);
    }
  }

  async installProject(project: ModrinthProject, options: InstallOptions = {}) {
    const normalizedProject = await this.ensureProjectIdentity(project);
    if (this.projectBusy.has(normalizedProject.id)) return null;

    const preferredInstanceId = options.preferredInstanceId ?? this.preferredInstanceId;
    this.projectBusy.add(normalizedProject.id);

    try {
      const resolution = await this.resolveInstallPlan(normalizedProject, {
        ...options,
        preferredInstanceId,
      });

      let targetInstance = resolution.instance;
      let created = false;

      if (!targetInstance && resolution.plan.createdSpec) {
        targetInstance = this.root.instances.create(resolution.plan.createdSpec);
        if (!targetInstance) {
          throw new Error("Failed to create a compatible instance");
        }
        created = true;
        if (!options.preferredInstanceId) {
          this.preferredInstanceId = targetInstance.id;
        }
      }

      if (!targetInstance) {
        throw new Error("No compatible instance available");
      }

      await this.executeInstallPlan(
        normalizedProject,
        targetInstance,
        resolution.plan,
        created,
        options.jobKind ?? "install-mod"
      );

      return targetInstance;
    } finally {
      runInAction(() => {
        this.projectBusy.delete(normalizedProject.id);
      });
    }
  }

  async previewProjectInstall(
    project: ModrinthProject,
    options: InstallOptions = {}
  ): Promise<InstallPreview> {
    const normalizedProject = await this.ensureProjectIdentity(project);
    const preferredInstanceId = options.preferredInstanceId ?? this.preferredInstanceId;
    const resolution = await this.resolveInstallPlan(normalizedProject, {
      ...options,
      preferredInstanceId,
    });

    return resolution;
  }

  getPreferredInstallInstance() {
    if (!this.preferredInstanceId) return null;
    return (
      this.root.instances.instances.find((instance) => instance.id === this.preferredInstanceId) ??
      null
    );
  }

  getSortedInstallInstances(projectId?: string) {
    return this.getCandidateInstances(projectId ?? "", this.preferredInstanceId, false);
  }

  async switchInstalledModVersion(
    instanceId: string,
    mod: InstalledMod,
    newVersion: ModVersion
  ) {
    const busyKey = `${instanceId}:${mod.projectId}`;
    if (this.instanceProjectBusy.has(busyKey)) return;

    this.instanceProjectBusy.add(busyKey);

    try {
      const project = await getProject(mod.projectId);
      await this.installProject(project, {
        preferredInstanceId: instanceId,
        preferredVersion: newVersion,
        strictInstance: true,
        strictVersion: true,
        allowCreateInstance: false,
        jobKind: "switch-version",
      });
    } finally {
      runInAction(() => {
        this.instanceProjectBusy.delete(busyKey);
      });
    }
  }

  async removeInstalledMod(instanceId: string, mod: InstalledMod) {
    const busyKey = `${instanceId}:${mod.projectId}`;
    if (this.instanceProjectBusy.has(busyKey)) return;

    this.instanceProjectBusy.add(busyKey);

    try {
      const instance = this.root.instances.instances.find((item) => item.id === instanceId);
      if (!instance) throw new Error("Instance not found");

      await this.reconcileInstance(instanceId);
      const freshInstance =
        this.root.instances.instances.find((item) => item.id === instanceId) ?? instance;
      const removalList = await this.buildRemovalList(freshInstance, mod.projectId);

      this.startJob({
        kind: "remove-mod",
        title: `Removing ${mod.title}`,
        subtitle:
          removalList.length > 1
            ? `Cleaning up dependent mods from ${freshInstance.name}`
            : `Removing from ${freshInstance.name}`,
        currentItem: "Preparing removal...",
        instanceName: freshInstance.name,
        iconColor: freshInstance.iconColor,
        completedItems: 0,
        totalItems: Math.max(removalList.length, 1),
        percent: 0,
        status: "running",
      });

      for (let index = 0; index < removalList.length; index += 1) {
        const item = removalList[index];
        const label = item.title || item.projectId;
        this.updateJobProgress(
          `Removing ${label}...`,
          index,
          removalList.length
        );

        const result = await window.electronAPI.removeModFile({
          instanceId,
          filename: item.filename,
        });

        if (!result.success) {
          throw new Error(result.error || `Failed to remove ${label}`);
        }

        this.root.instances.removeMod(instanceId, item.projectId);
        this.updateJobProgress(
          `Removed ${label}`,
          index + 1,
          removalList.length
        );
      }

      await this.reconcileInstance(instanceId);
      await this.syncSharedInstance(instanceId);

      this.finishJob(`Removed ${removalList.length} mod${removalList.length === 1 ? "" : "s"}`);
    } catch (error) {
      this.failJob(error instanceof Error ? error.message : "Removal failed");
      throw error;
    } finally {
      runInAction(() => {
        this.instanceProjectBusy.delete(busyKey);
      });
    }
  }

  async installSharedInstance(
    sharedData: SharedInstanceData,
    options?: { notificationId?: string }
  ) {
    if (this.sharedBusy.has(sharedData.id)) return null;
    this.sharedBusy.add(sharedData.id);
    this.setSharedProgress(sharedData.id, "Preparing download...", 0);

    try {
      const loader = this.ensureSupportedLoader(sharedData.loader);
      let instance = this.root.instances.createFromShared({
        name: sharedData.name,
        version: sharedData.mc_version,
        loader,
        iconColor: sharedData.icon_color,
        sharedInstanceId: sharedData.id,
        shareCode: sharedData.share_code,
        isCollaborative: sharedData.is_collaborative,
        mods: [],
      });

      if (!instance) {
        throw new Error("Failed to create instance");
      }

      if (sharedData.is_collaborative) {
        await this.root.sharing.joinAsCollaborator(sharedData.id);
      }

      const desiredMods = sharedData.mods.map((mod) => ({
        projectId: mod.project_id,
        versionId: mod.version_id,
        title: mod.title,
        iconUrl: mod.icon_url,
      }));

      await this.applySharedManifest(instance, sharedData.name, sharedData.id, desiredMods);

      instance = this.root.instances.instances.find((item) => item.id === instance!.id) ?? instance;
      this.root.instances.update(instance.id, { syncedAt: new Date().toISOString() });

      if (options?.notificationId) {
        await this.root.notifications.markAccepted(options.notificationId);
      }

      return instance;
    } finally {
      runInAction(() => {
        this.sharedBusy.delete(sharedData.id);
        this.sharedProgress.delete(sharedData.id);
      });
    }
  }

  private async resolveInstallPlan(
    project: ModrinthProject,
    options: InstallOptions
  ): Promise<{ instance: Instance | null; plan: InstallPlan }> {
    const strictVersion = options.strictVersion ?? false;
    const allowCreateInstance = options.allowCreateInstance ?? true;
    const allVersions = options.preferredVersion && strictVersion
      ? [options.preferredVersion]
      : await getProjectVersions(project.id);

    const instanceCandidates = this.getCandidateInstances(
      project.id,
      options.preferredInstanceId,
      options.strictInstance ?? false
    );

    for (const instance of instanceCandidates) {
      const plan = await this.buildPlanForExistingInstance(
        project,
        instance,
        allVersions,
        options.preferredVersion ?? null,
        strictVersion
      );

      if (!plan) continue;
      if (plan.conflicts.length > 0 && !(options.strictInstance ?? false)) continue;

      return { instance, plan };
    }

    if (!allowCreateInstance) {
      throw new Error("No compatible instance available for this mod");
    }

    const createdPlan = await this.buildPlanForNewInstance(
      project,
      allVersions,
      options.preferredVersion ?? null,
      strictVersion
    );

    return { instance: null, plan: createdPlan };
  }

  private async buildPlanForExistingInstance(
    project: ModrinthProject,
    instance: Instance,
    allVersions: ModVersion[],
    preferredVersion: ModVersion | null,
    strictVersion: boolean
  ): Promise<InstallPlan | null> {
    await this.reconcileInstance(instance.id);

    const freshInstance =
      this.root.instances.instances.find((item) => item.id === instance.id) ?? instance;
    const target: InstallContext = {
      version: freshInstance.version,
      loader: freshInstance.loader,
      installedMods: [...freshInstance.installedMods],
    };

    const versionCandidates =
      preferredVersion && strictVersion
        ? [preferredVersion]
        : preferredVersion
        ? [preferredVersion, ...sortVersionsForTarget(allVersions, target)]
        : sortVersionsForTarget(allVersions, target);

    for (const candidate of versionCandidates) {
      if (!isVersionCompatible(candidate, target)) continue;

      try {
        const graph = await this.resolveDependencyGraph(project, candidate, target);
        if (graph.missing.length > 0) continue;

        return {
          target,
          rootVersion: candidate,
          operations: this.buildOperations(project, candidate, target, graph.dependencies),
          conflicts: graph.conflicts,
          missing: graph.missing,
          dependencyTree: graph.tree.children,
        };
      } catch {
        if (strictVersion) throw new Error("Failed to resolve selected version");
      }
    }

    return null;
  }

  private async buildPlanForNewInstance(
    project: ModrinthProject,
    allVersions: ModVersion[],
    preferredVersion: ModVersion | null,
    strictVersion: boolean
  ): Promise<InstallPlan> {
    const candidates =
      preferredVersion && strictVersion
        ? [preferredVersion]
        : preferredVersion
        ? [preferredVersion, ...sortVersionsForNewInstance(allVersions)]
        : sortVersionsForNewInstance(allVersions);

    for (const version of candidates) {
      const loader = selectSupportedLoader(version.loaders);
      const gameVersion = [...version.game_versions]
        .sort(compareMinecraftVersions)
        .pop();

      if (!loader || !gameVersion) continue;

      const target: InstallContext = {
        version: gameVersion,
        loader,
        installedMods: [],
      };

      if (!isVersionCompatible(version, target)) continue;

      try {
        const graph = await this.resolveDependencyGraph(project, version, target);
        if (graph.missing.length > 0 || graph.conflicts.length > 0) continue;

        const baseName = `${project.title} (${LOADER_LABEL[loader]})`;

        return {
          target,
          rootVersion: version,
          operations: this.buildOperations(project, version, target, graph.dependencies),
          conflicts: [],
          missing: graph.missing,
          dependencyTree: graph.tree.children,
          createdSpec: {
            name: buildUniqueInstanceName(
              this.root.instances.instances.map((item) => item.name),
              baseName
            ),
            version: gameVersion,
            loader,
            iconColor: LOADER_ICON_COLOR[loader],
          },
        };
      } catch {
        if (strictVersion) break;
      }
    }

    throw new Error("Could not find a launchable version for this mod");
  }

  private async resolveDependencyGraph(
    rootProject: ModrinthProject,
    rootVersion: ModVersion,
    target: InstallContext
  ) {
    const dependencyOrder: Array<{ project: ModrinthProject; version: ModVersion }> = [];
    const operationSeen = new Set<string>();
    const versionListCache = new Map<string, ModVersion[]>();
    const versionCache = new Map<string, ModVersion>();
    const projectCache = new Map<string, ModrinthProject>([[rootProject.id, rootProject]]);
    const conflicts = new Map<string, ModrinthProject>();
    const missing = new Set<string>();
    const installedMap = new Map(
      target.installedMods.map((mod) => [mod.projectId, mod] as const)
    );

    const loadProject = async (projectId: string) => {
      const cached = projectCache.get(projectId);
      if (cached) return cached;

      const [project] = await getProjects([projectId]);
      if (!project) throw new Error(`Missing project ${projectId}`);
      projectCache.set(projectId, project);
      return project;
    };

    const loadProjectVersions = async (projectId: string) => {
      const cached = versionListCache.get(projectId);
      if (cached) return cached;
      const versions = await getProjectVersions(projectId);
      versionListCache.set(projectId, versions);
      return versions;
    };

    const loadVersion = async (versionId: string) => {
      const cached = versionCache.get(versionId);
      if (cached) return cached;
      const version = await getVersion(versionId);
      versionCache.set(versionId, version);
      return version;
    };

    const resolveDependencyVersion = async (
      dependency: ModVersionDependency
    ): Promise<ModVersion | null> => {
      if (dependency.version_id) {
        try {
          const explicit = await loadVersion(dependency.version_id);
          if (isVersionCompatible(explicit, target)) return explicit;
        } catch {
          // fall through to compatibility search
        }
      }

      if (!dependency.project_id) return null;

      const compatible = sortVersionsForTarget(
        await loadProjectVersions(dependency.project_id),
        target
      );

      return compatible[0] ?? null;
    };

    const buildNode = async (
      project: ModrinthProject,
      version: ModVersion,
      dependencyType: InstallDependencyNode["dependencyType"],
      path: Set<string>
    ): Promise<InstallDependencyNode> => {
      const installed = installedMap.get(project.id) ?? null;
      const nextPath = new Set(path);
      nextPath.add(project.id);

      const children: InstallDependencyNode[] = [];

      for (const dependency of version.dependencies ?? []) {
        if (!dependency.project_id) continue;

        const dependencyProject = await loadProject(dependency.project_id);
        const installedDependency = installedMap.get(dependency.project_id) ?? null;

        if (dependency.dependency_type === "incompatible") {
          if (installedDependency) {
            conflicts.set(dependencyProject.id, dependencyProject);
          }

          children.push({
            projectId: dependencyProject.id,
            title: dependencyProject.title,
            iconUrl: dependencyProject.icon_url,
            versionId: installedDependency?.versionId ?? null,
            versionNumber: installedDependency ? "Installed" : null,
            dependencyType: "incompatible",
            status: installedDependency ? "conflict" : "optional",
            children: [],
          });
          continue;
        }

        const dependencyVersion = await resolveDependencyVersion(dependency);
        if (!dependencyVersion) {
          missing.add(dependencyProject.title);
          children.push({
            projectId: dependencyProject.id,
            title: dependencyProject.title,
            iconUrl: dependencyProject.icon_url,
            versionId: null,
            versionNumber: null,
            dependencyType: dependency.dependency_type,
            status: "missing",
            children: [],
          });
          continue;
        }

        const childStatus: InstallDependencyNode["status"] =
          dependency.dependency_type === "optional"
            ? installedDependency?.versionId === dependencyVersion.id
              ? "already-installed"
              : "optional"
            : installedDependency?.versionId === dependencyVersion.id
            ? "already-installed"
            : installedDependency
            ? "update"
            : "install";

        let childChildren: InstallDependencyNode[] = [];
        if (
          !nextPath.has(dependencyProject.id) &&
          dependency.dependency_type !== "incompatible"
        ) {
          const childNode = await buildNode(
            dependencyProject,
            dependencyVersion,
            dependency.dependency_type,
            nextPath
          );
          childChildren = childNode.children;
        }

        children.push({
          projectId: dependencyProject.id,
          title: dependencyProject.title,
          iconUrl: dependencyProject.icon_url,
          versionId: dependencyVersion.id,
          versionNumber: dependencyVersion.version_number,
          dependencyType: dependency.dependency_type,
          status: childStatus,
          children: childChildren,
        });

        if (
          (dependency.dependency_type === "required" ||
            dependency.dependency_type === "embedded") &&
          !operationSeen.has(dependencyProject.id)
        ) {
          operationSeen.add(dependencyProject.id);
          dependencyOrder.push({ project: dependencyProject, version: dependencyVersion });
        }
      }

      return {
        projectId: project.id,
        title: project.title,
        iconUrl: project.icon_url,
        versionId: version.id,
        versionNumber: version.version_number,
        dependencyType,
        status: installed?.versionId === version.id ? "already-installed" : installed ? "update" : "install",
        children,
      };
    };

    const tree = await buildNode(rootProject, rootVersion, "root", new Set<string>());

    return {
      dependencies: dependencyOrder,
      conflicts: [...conflicts.values()],
      missing: [...missing],
      tree,
    };
  }

  private buildOperations(
    rootProject: ModrinthProject,
    rootVersion: ModVersion,
    target: InstallContext,
    dependencies: Array<{ project: ModrinthProject; version: ModVersion }>
  ) {
    const installedMap = new Map(
      target.installedMods.map((mod) => [mod.projectId, mod] as const)
    );
    const items: InstallOperation[] = [];

    for (const dependency of dependencies) {
      const existingMod = installedMap.get(dependency.project.id) ?? null;
      if (existingMod?.versionId === dependency.version.id) continue;

      items.push({
        project: dependency.project,
        version: dependency.version,
        existingMod,
        kind: existingMod ? "update" : "install",
      });
    }

    const existingRoot = installedMap.get(rootProject.id) ?? null;
    if (existingRoot?.versionId !== rootVersion.id) {
      items.push({
        project: rootProject,
        version: rootVersion,
        existingMod: existingRoot,
        kind: existingRoot ? "update" : "install",
      });
    }

    return items;
  }

  private async executeInstallPlan(
    project: ModrinthProject,
    instance: Instance,
    plan: InstallPlan,
    created: boolean,
    kind: InstallJob["kind"]
  ) {
    if (plan.missing.length > 0) {
      throw new Error(`Missing required dependencies: ${plan.missing.join(", ")}`);
    }

    if (plan.conflicts.length > 0) {
      throw new Error(
        `Conflicts with installed mods: ${plan.conflicts
          .map((item) => item.title)
          .join(", ")}`
      );
    }

    const totalItems = Math.max(plan.operations.length, 1);
    const subtitle = created
      ? `Created ${instance.name} automatically`
      : `Installing into ${instance.name}`;

    this.startJob({
      kind,
      title: `${project.title}`,
      subtitle,
      currentItem: created ? "Creating a compatible instance..." : "Preparing install...",
      instanceName: instance.name,
      iconColor: instance.iconColor,
      completedItems: 0,
      totalItems,
      percent: 0,
      status: "running",
    });

    if (plan.operations.length === 0) {
      this.finishJob(`${project.title} is already installed in ${instance.name}`);
      return;
    }

    try {
      for (let index = 0; index < plan.operations.length; index += 1) {
        const operation = plan.operations[index];
        const primaryFile = choosePrimaryFile(operation.version);
        if (!primaryFile) {
          throw new Error(`No downloadable file found for ${operation.project.title}`);
        }

        this.bindDownload(instance.id, primaryFile.filename, index, plan.operations.length);
        this.updateJobProgress(
          `${operation.kind === "update" ? "Updating" : "Installing"} ${operation.project.title}...`,
          index,
          plan.operations.length
        );

        const result = await window.electronAPI.downloadMod({
          instanceId: instance.id,
          url: primaryFile.url,
          filename: primaryFile.filename,
        });

        this.downloadBinding = null;

        if (!result.success) {
          throw new Error(result.error || `Failed to download ${operation.project.title}`);
        }

        if (operation.existingMod) {
          if (operation.existingMod.filename !== primaryFile.filename) {
            await window.electronAPI.removeModFile({
              instanceId: instance.id,
              filename: operation.existingMod.filename,
            });
          }

          this.root.instances.updateMod(instance.id, operation.project.id, {
            versionId: operation.version.id,
            filename: primaryFile.filename,
            title: operation.project.title,
            iconUrl: operation.project.icon_url,
          });
        } else {
          this.root.instances.addMod(instance.id, {
            projectId: operation.project.id,
            versionId: operation.version.id,
            filename: primaryFile.filename,
            title: operation.project.title,
            iconUrl: operation.project.icon_url,
          });
        }

        this.updateJobProgress(
          `${operation.project.title} ready`,
          index + 1,
          plan.operations.length
        );
      }

      await this.reconcileInstance(instance.id);
      await this.syncSharedInstance(instance.id);

      this.finishJob(`${project.title} installed in ${instance.name}`);
    } catch (error) {
      if (created) {
        this.root.instances.remove(instance.id);
      }
      this.failJob(error instanceof Error ? error.message : "Install failed");
      throw error;
    }
  }

  private async applySharedManifest(
    instance: Instance,
    title: string,
    sharedId: string,
    desiredMods: Array<{
      projectId: string;
      versionId: string;
      title: string;
      iconUrl: string | null;
    }>
  ) {
    await this.reconcileInstance(instance.id);

    const current =
      this.root.instances.instances.find((item) => item.id === instance.id) ?? instance;
    const currentMap = new Map(current.installedMods.map((mod) => [mod.projectId, mod] as const));
    const desiredMap = new Map(desiredMods.map((mod) => [mod.projectId, mod] as const));
    const additions = desiredMods.filter((mod) => currentMap.get(mod.projectId)?.versionId !== mod.versionId);
    const removals = current.installedMods.filter((mod) => !desiredMap.has(mod.projectId));
    const totalItems = Math.max(additions.length + removals.length, 1);

    this.startJob({
      kind: "sync-shared",
      title,
      subtitle: `Syncing ${current.name} to this device`,
      currentItem: "Preparing download...",
      instanceName: current.name,
      iconColor: current.iconColor,
      completedItems: 0,
      totalItems,
      percent: 0,
      status: "running",
    });

    let completed = 0;

    try {
      for (const desired of additions) {
        const version = await getVersion(desired.versionId);
        const primaryFile = choosePrimaryFile(version);
        if (!primaryFile) {
          throw new Error(`No downloadable file found for ${desired.title}`);
        }

        this.bindDownload(instance.id, primaryFile.filename, completed, totalItems);
        this.setSharedProgress(
          sharedId,
          `Downloading ${desired.title}...`,
          Math.round((completed / totalItems) * 100)
        );
        this.updateJobProgress(`Downloading ${desired.title}...`, completed, totalItems);

        const result = await window.electronAPI.downloadMod({
          instanceId: instance.id,
          url: primaryFile.url,
          filename: primaryFile.filename,
        });

        this.downloadBinding = null;

        if (!result.success) {
          throw new Error(result.error || `Failed to download ${desired.title}`);
        }

        const existing = currentMap.get(desired.projectId) ?? null;
        if (existing) {
          if (existing.filename !== primaryFile.filename) {
            await window.electronAPI.removeModFile({
              instanceId: instance.id,
              filename: existing.filename,
            });
          }
          this.root.instances.updateMod(instance.id, desired.projectId, {
            versionId: desired.versionId,
            filename: primaryFile.filename,
            title: desired.title,
            iconUrl: desired.iconUrl,
          });
        } else {
          this.root.instances.addMod(instance.id, {
            projectId: desired.projectId,
            versionId: desired.versionId,
            filename: primaryFile.filename,
            title: desired.title,
            iconUrl: desired.iconUrl,
          });
        }

        completed += 1;
        this.setSharedProgress(
          sharedId,
          `Installed ${desired.title}`,
          Math.round((completed / totalItems) * 100)
        );
        this.updateJobProgress(`Installed ${desired.title}`, completed, totalItems);
      }

      for (const removal of removals) {
        this.setSharedProgress(
          sharedId,
          `Removing ${removal.title}...`,
          Math.round((completed / totalItems) * 100)
        );
        this.updateJobProgress(`Removing ${removal.title}...`, completed, totalItems);

        const result = await window.electronAPI.removeModFile({
          instanceId: instance.id,
          filename: removal.filename,
        });

        if (!result.success) {
          throw new Error(result.error || `Failed to remove ${removal.title}`);
        }

        this.root.instances.removeMod(instance.id, removal.projectId);
        completed += 1;
        this.setSharedProgress(
          sharedId,
          `Removed ${removal.title}`,
          Math.round((completed / totalItems) * 100)
        );
        this.updateJobProgress(`Removed ${removal.title}`, completed, totalItems);
      }

      await this.reconcileInstance(instance.id);
      this.finishJob(`${title} is ready on this device`);
    } catch (error) {
      this.failJob(error instanceof Error ? error.message : "Sync failed");
      throw error;
    }
  }

  private async buildRemovalList(instance: Instance, rootProjectId: string) {
    const versions = await Promise.all(
      instance.installedMods.map(async (mod) => {
        try {
          const version = await getVersion(mod.versionId);
          return [mod.projectId, version] as const;
        } catch {
          return [mod.projectId, null] as const;
        }
      })
    );

    const versionMap = new Map(versions);
    const dependents = new Map<string, string[]>();

    for (const mod of instance.installedMods) {
      const version = versionMap.get(mod.projectId);
      if (!version) continue;

      for (const dependency of version.dependencies ?? []) {
        if (
          !dependency.project_id ||
          (dependency.dependency_type !== "required" &&
            dependency.dependency_type !== "embedded")
        ) {
          continue;
        }

        const list = dependents.get(dependency.project_id) ?? [];
        list.push(mod.projectId);
        dependents.set(dependency.project_id, list);
      }
    }

    const visited = new Set<string>();
    const order: string[] = [];

    const visit = (projectId: string) => {
      if (visited.has(projectId)) return;
      visited.add(projectId);
      for (const dependentId of dependents.get(projectId) ?? []) {
        visit(dependentId);
      }
      order.push(projectId);
    };

    visit(rootProjectId);

    return order
      .map((projectId) => instance.installedMods.find((mod) => mod.projectId === projectId))
      .filter((mod): mod is InstalledMod => mod !== undefined);
  }

  private async reconcileInstance(instanceId: string) {
    const result = await window.electronAPI.listInstanceModFiles(instanceId);
    if (!result.success) return;

    const files = new Set(result.files);
    const instance = this.root.instances.instances.find((item) => item.id === instanceId);
    if (!instance) return;

    for (const mod of [...instance.installedMods]) {
      if (!files.has(mod.filename)) {
        this.root.instances.removeMod(instanceId, mod.projectId);
      }
    }
  }

  private async syncSharedInstance(instanceId: string) {
    const instance = this.root.instances.instances.find((item) => item.id === instanceId);
    if (!instance?.sharedInstanceId || !this.root.sharing.profileId) return;

    try {
      await this.root.sharing.syncInstanceMods(instance);
      this.root.instances.update(instanceId, { syncedAt: new Date().toISOString() });
    } catch {
      // keep local install state even if remote sync fails
    }
  }

  private ensureSupportedLoader(loader: string): Loader {
    const normalized = loader.toLowerCase();
    if (normalized === "vanilla" || normalized === "fabric" || normalized === "forge") {
      return normalized;
    }
    throw new Error(`Unsupported loader: ${loader}`);
  }

  private async ensureProjectIdentity(project: ModrinthProject): Promise<ModrinthProject> {
    if (project.id) return project;
    if (project.slug) {
      return getProject(project.slug);
    }
    throw new Error("Project is missing an identifier");
  }

  private getCandidateInstances(
    projectId: string,
    preferredInstanceId: string | null | undefined,
    strictInstance: boolean
  ) {
    const instances = [...this.root.instances.instances];
    if (strictInstance && preferredInstanceId) {
      return instances.filter((instance) => instance.id === preferredInstanceId);
    }

    return instances.sort((a, b) => {
      const aPreferred = a.id === preferredInstanceId ? -1000 : 0;
      const bPreferred = b.id === preferredInstanceId ? -1000 : 0;
      if (aPreferred !== bPreferred) return aPreferred - bPreferred;

      const aInstalled = this.root.instances.hasModInstalled(a.id, projectId) ? 1 : 0;
      const bInstalled = this.root.instances.hasModInstalled(b.id, projectId) ? 1 : 0;
      if (aInstalled !== bInstalled) return aInstalled - bInstalled;

      const aRecent = a.lastPlayed ? new Date(a.lastPlayed).getTime() : 0;
      const bRecent = b.lastPlayed ? new Date(b.lastPlayed).getTime() : 0;
      return bRecent - aRecent;
    });
  }

  private startJob(job: Omit<InstallJob, "id">) {
    if (this.toastDismissTimer) clearTimeout(this.toastDismissTimer);
    this.toastDismissTimer = null;
    this.jobCounter += 1;
    this.activeJob = {
      ...job,
      id: `job-${this.jobCounter}`,
    };
    this.toastVisible = true;
  }

  private updateJobProgress(message: string, completedItems: number, totalItems: number) {
    if (!this.activeJob) return;
    this.activeJob.currentItem = message;
    this.activeJob.completedItems = completedItems;
    this.activeJob.totalItems = totalItems;
    if (!this.downloadBinding) {
      this.activeJob.percent = Math.round((completedItems / Math.max(totalItems, 1)) * 100);
    }
  }

  private setSharedProgress(sharedId: string, message: string, percent: number) {
    this.sharedProgress.set(sharedId, {
      message,
      percent,
    });
  }

  private bindDownload(
    instanceId: string,
    filename: string,
    itemIndex: number,
    totalItems: number
  ) {
    this.downloadBinding = {
      instanceId,
      filename,
      basePercent: (itemIndex / Math.max(totalItems, 1)) * 100,
      spanPercent: 100 / Math.max(totalItems, 1),
    };
  }

  private finishJob(message: string) {
    if (!this.activeJob) return;
    this.downloadBinding = null;
    this.activeJob.currentItem = message;
    this.activeJob.percent = 100;
    this.activeJob.completedItems = this.activeJob.totalItems;
    this.activeJob.status = "done";

    this.toastDismissTimer = setTimeout(() => {
      runInAction(() => {
        this.toastVisible = false;
        this.activeJob = null;
      });
    }, 4500);
  }

  private failJob(error: string) {
    if (!this.activeJob) return;
    this.downloadBinding = null;
    this.activeJob.status = "error";
    this.activeJob.error = error;
    this.activeJob.currentItem = error;

    this.toastDismissTimer = setTimeout(() => {
      runInAction(() => {
        this.toastVisible = false;
        this.activeJob = null;
      });
    }, 7000);
  }
}
