import type {
  ElectronAPI,
  LaunchProgress,
  MicrosoftAuthData,
} from "./types/electron";

const DEV_USER_UUID = "00000000-0000-4000-8000-000000000005";

function noopUnsubscribe() {
  return () => {};
}

function seedDevInstances() {
  const key = `void-instances-${DEV_USER_UUID}`;

  localStorage.setItem(
    key,
    JSON.stringify([
      {
        id: "dev-instance-1",
        name: "Void SMP",
        version: "1.21.4",
        loader: "fabric",
        iconColor: "#38bdf8",
        memoryMb: 6144,
        modCount: 42,
        installedMods: [],
        lastPlayed: new Date(Date.now() - 1000 * 60 * 38).toISOString(),
        dateCreated: new Date(Date.now() - 1000 * 60 * 60 * 24 * 14).toISOString(),
        ownerUuid: DEV_USER_UUID,
        ownerName: "VoidPlayer",
        shareCode: "VOID42",
        sharedInstanceId: "shared-dev-instance-1",
        isCollaborative: true,
        syncedAt: new Date(Date.now() - 1000 * 60 * 18).toISOString(),
      },
      {
        id: "dev-instance-2",
        name: "Create Tech Lab",
        version: "1.20.1",
        loader: "forge",
        iconColor: "#f59e0b",
        memoryMb: 8192,
        modCount: 86,
        installedMods: [],
        lastPlayed: new Date(Date.now() - 1000 * 60 * 60 * 9).toISOString(),
        dateCreated: new Date(Date.now() - 1000 * 60 * 60 * 24 * 38).toISOString(),
        ownerUuid: DEV_USER_UUID,
        ownerName: "VoidPlayer",
        shareCode: null,
        sharedInstanceId: null,
        isCollaborative: false,
        syncedAt: null,
      },
      {
        id: "dev-instance-3",
        name: "Performance Vanilla",
        version: "1.21.5",
        loader: "vanilla",
        iconColor: "#22c55e",
        memoryMb: 4096,
        modCount: 12,
        installedMods: [],
        lastPlayed: null,
        dateCreated: new Date(Date.now() - 1000 * 60 * 60 * 24 * 4).toISOString(),
        ownerUuid: DEV_USER_UUID,
        ownerName: "VoidPlayer",
        shareCode: null,
        sharedInstanceId: null,
        isCollaborative: false,
        syncedAt: null,
      },
    ])
  );
}

function createDevAuth(): MicrosoftAuthData {
  return {
    access_token: "dev-access-token",
    client_token: "dev-client-token",
    uuid: DEV_USER_UUID,
    name: "VoidPlayer",
    refresh_token: "dev-refresh-token",
    user_properties: "{}",
    meta: {
      type: "Xbox",
      access_token_expires_in: 3600,
      demo: false,
    },
    xboxAccount: {
      xuid: "0",
      gamertag: "VoidPlayer",
      ageGroup: "Adult",
    },
    profile: {},
  };
}

export function installDevElectronApi() {
  if (!import.meta.env.DEV || window.electronAPI) return;

  seedDevInstances();

  const launchProgressListeners = new Set<(data: LaunchProgress) => void>();

  window.electronAPI = {
    getSystemTheme: async () => "dark",
    installUpdate: async () => {},
    getPlatform: async () => "darwin",
    openExternal: async () => {},
    getAppVersion: async () => "2.0.31-dev",
    getInstancesPath: async () => "/Users/dev/VOIDLauncher/instances",
    downloadMod: async () => ({ success: true, path: "/tmp/dev-mod.jar" }),
    removeModFile: async () => ({ success: true }),
    listInstanceModFiles: async () => ({ success: true, files: [] }),
    openInstanceFolder: async () => {},
    onDownloadProgress: noopUnsubscribe,
    onUpdateAvailable: noopUnsubscribe,
    onUpdateDownloaded: noopUnsubscribe,
    onUpdateError: noopUnsubscribe,
    checkForUpdates: async () => ({ success: true }),
    onCheckingForUpdate: noopUnsubscribe,
    onUpdateNotAvailable: noopUnsubscribe,
    onUpdateDownloadProgress: noopUnsubscribe,
    microsoftLogin: async () => ({ success: true, data: createDevAuth() }),
    microsoftRefresh: async () => ({ success: true, data: createDevAuth() }),
    microsoftLogout: async () => ({ success: true }),
    microsoftGetStored: async () => ({ success: true, data: createDevAuth() }),
    getSystemInfo: async () => ({
      platform: "darwin",
      arch: "arm64",
      totalMemoryBytes: 17_179_869_184,
      totalMemoryMb: 16384,
    }),
    getJavaStatus: async () => ({
      required: 21,
      installed: true,
      path: "/Library/Java/JavaVirtualMachines/dev/bin/java",
    }),
    launchMinecraft: async (data) => {
      window.setTimeout(() => {
        launchProgressListeners.forEach((listener) =>
          listener({
            instanceId: data.instanceId,
            stage: "launched",
            message: "Dev launch ready",
            percent: 100,
          })
        );
      }, 700);
      return { success: true };
    },
    onLaunchProgress: (cb) => {
      launchProgressListeners.add(cb);
      return () => launchProgressListeners.delete(cb);
    },
    onLaunchSpeed: noopUnsubscribe,
    onGameClosed: noopUnsubscribe,
    onGameError: noopUnsubscribe,
    onGameLog: noopUnsubscribe,
  } satisfies ElectronAPI;
}

installDevElectronApi();
