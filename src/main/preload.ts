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
});
