import { contextBridge, ipcRenderer } from "electron";

contextBridge.exposeInMainWorld("electronAPI", {
  getSystemTheme: () => ipcRenderer.invoke("get-system-theme"),
  installUpdate: () => ipcRenderer.invoke("install-update"),
  getAppVersion: () => ipcRenderer.invoke("get-app-version"),
  getInstancesPath: () => ipcRenderer.invoke("get-instances-path"),
  downloadMod: (data: { instanceId: string; url: string; filename: string }) =>
    ipcRenderer.invoke("download-mod", data),
  removeModFile: (data: { instanceId: string; filename: string }) =>
    ipcRenderer.invoke("remove-mod-file", data),
  openInstanceFolder: (instanceId: string) =>
    ipcRenderer.invoke("open-instance-folder", instanceId),
  onDownloadProgress: (
    cb: (data: { instanceId: string; filename: string; percent: number }) => void
  ) => ipcRenderer.on("download-progress", (_e, data) => cb(data)),
  onUpdateAvailable: (cb: (info: unknown) => void) =>
    ipcRenderer.on("update-available", (_e, info) => cb(info)),
  onUpdateDownloaded: (cb: (info: unknown) => void) =>
    ipcRenderer.on("update-downloaded", (_e, info) => cb(info)),
  onUpdateError: (cb: (msg: string) => void) =>
    ipcRenderer.on("update-error", (_e, msg) => cb(msg)),
  microsoftLogin: () => ipcRenderer.invoke("microsoft-login"),
  microsoftRefresh: () => ipcRenderer.invoke("microsoft-refresh"),
  microsoftLogout: () => ipcRenderer.invoke("microsoft-logout"),
  microsoftGetStored: () => ipcRenderer.invoke("microsoft-get-stored"),

  // System info
  getSystemInfo: () => ipcRenderer.invoke("get-system-info"),

  // Java management
  getJavaStatus: (data: { mcVersion: string }) =>
    ipcRenderer.invoke("get-java-status", data),

  // Minecraft launching
  launchMinecraft: (data: {
    instanceId: string;
    version: string;
    loader: "vanilla" | "fabric" | "forge";
    memoryMb: number;
  }) => ipcRenderer.invoke("launch-minecraft", data),
  onLaunchProgress: (
    cb: (data: {
      instanceId: string;
      stage: string;
      message: string;
      percent: number;
    }) => void
  ) => ipcRenderer.on("launch-progress", (_e, data) => cb(data)),
  onLaunchSpeed: (
    cb: (data: { instanceId: string; speed: number }) => void
  ) => ipcRenderer.on("launch-speed", (_e, data) => cb(data)),
  onGameClosed: (
    cb: (data: { instanceId: string }) => void
  ) => ipcRenderer.on("game-closed", (_e, data) => cb(data)),
  onGameError: (
    cb: (data: { instanceId: string; error: string }) => void
  ) => ipcRenderer.on("game-error", (_e, data) => cb(data)),
  onGameLog: (
    cb: (data: { instanceId: string; line: string }) => void
  ) => ipcRenderer.on("game-log", (_e, data) => cb(data)),
});
