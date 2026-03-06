import { contextBridge, ipcRenderer } from "electron";

// Helper to create a subscribable IPC listener that returns an unsubscribe function
function createListener<T>(channel: string) {
  return (cb: (data: T) => void) => {
    const handler = (_e: Electron.IpcRendererEvent, data: T) => cb(data);
    ipcRenderer.on(channel, handler);
    return () => {
      ipcRenderer.removeListener(channel, handler);
    };
  };
}

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
  onDownloadProgress: createListener<{ instanceId: string; filename: string; percent: number }>("download-progress"),
  onUpdateAvailable: createListener<unknown>("update-available"),
  onUpdateDownloaded: createListener<unknown>("update-downloaded"),
  onUpdateError: createListener<string>("update-error"),
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
  onLaunchProgress: createListener<{
    instanceId: string;
    stage: string;
    message: string;
    percent: number;
  }>("launch-progress"),
  onLaunchSpeed: createListener<{ instanceId: string; speed: number }>("launch-speed"),
  onGameClosed: createListener<{ instanceId: string }>("game-closed"),
  onGameError: createListener<{ instanceId: string; error: string }>("game-error"),
  onGameLog: createListener<{ instanceId: string; line: string }>("game-log"),
});
