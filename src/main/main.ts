import { app, BrowserWindow, ipcMain, nativeTheme, shell } from "electron";
import { autoUpdater } from "electron-updater";
import path from "node:path";
import fs from "node:fs";
import https from "node:https";
import http from "node:http";
// eslint-disable-next-line @typescript-eslint/no-var-requires
const { Microsoft } = require("minecraft-java-core");

// Handle Squirrel events for Windows installer
if (require("electron-squirrel-startup")) app.quit();

declare const MAIN_WINDOW_VITE_DEV_SERVER_URL: string;
declare const MAIN_WINDOW_VITE_NAME: string;

let mainWindow: BrowserWindow | null = null;

function getInstancesDir(): string {
  return path.join(app.getPath("userData"), "instances");
}

function getInstanceModsDir(instanceId: string): string {
  return path.join(getInstancesDir(), instanceId, "mods");
}

function ensureDir(dir: string): void {
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
  }
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 800,
    minWidth: 960,
    minHeight: 600,
    titleBarStyle: "hiddenInset",
    frame: process.platform === "darwin",
    backgroundColor: nativeTheme.shouldUseDarkColors ? "#0f0f0f" : "#ffffff",
    webPreferences: {
      preload: path.join(__dirname, "preload.js"),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });

  if (MAIN_WINDOW_VITE_DEV_SERVER_URL) {
    mainWindow.loadURL(MAIN_WINDOW_VITE_DEV_SERVER_URL);
  } else {
    mainWindow.loadFile(
      path.join(__dirname, `../renderer/${MAIN_WINDOW_VITE_NAME}/index.html`)
    );
  }

  mainWindow.on("closed", () => {
    mainWindow = null;
  });
}

// Auto-updater setup
function setupAutoUpdater() {
  autoUpdater.autoDownload = true;
  autoUpdater.autoInstallOnAppQuit = true;

  autoUpdater.on("update-available", (info) => {
    mainWindow?.webContents.send("update-available", info);
  });

  autoUpdater.on("update-downloaded", (info) => {
    mainWindow?.webContents.send("update-downloaded", info);
  });

  autoUpdater.on("error", (err) => {
    mainWindow?.webContents.send("update-error", err.message);
  });

  autoUpdater.checkForUpdates();
  setInterval(() => autoUpdater.checkForUpdates(), 30 * 60 * 1000);
}

// Download a file from URL to a local path
function downloadFile(
  url: string,
  destPath: string,
  onProgress?: (percent: number) => void
): Promise<void> {
  return new Promise((resolve, reject) => {
    const proto = url.startsWith("https") ? https : http;
    const request = proto.get(url, (response) => {
      // Handle redirects
      if (response.statusCode && response.statusCode >= 300 && response.statusCode < 400 && response.headers.location) {
        downloadFile(response.headers.location, destPath, onProgress)
          .then(resolve)
          .catch(reject);
        return;
      }

      if (response.statusCode !== 200) {
        reject(new Error(`Download failed with status ${response.statusCode}`));
        return;
      }

      const totalBytes = parseInt(response.headers["content-length"] || "0", 10);
      let downloadedBytes = 0;

      const fileStream = fs.createWriteStream(destPath);
      response.on("data", (chunk: Buffer) => {
        downloadedBytes += chunk.length;
        if (totalBytes > 0 && onProgress) {
          onProgress(Math.round((downloadedBytes / totalBytes) * 100));
        }
      });
      response.pipe(fileStream);
      fileStream.on("finish", () => {
        fileStream.close();
        resolve();
      });
      fileStream.on("error", (err) => {
        fs.unlink(destPath, () => {});
        reject(err);
      });
    });

    request.on("error", (err) => {
      fs.unlink(destPath, () => {});
      reject(err);
    });
  });
}

// IPC handlers
ipcMain.handle("get-system-theme", () => {
  return nativeTheme.shouldUseDarkColors ? "dark" : "light";
});

ipcMain.handle("install-update", () => {
  autoUpdater.quitAndInstall();
});

ipcMain.handle("get-app-version", () => {
  return app.getVersion();
});

ipcMain.handle("get-instances-path", () => {
  return getInstancesDir();
});

ipcMain.handle(
  "download-mod",
  async (
    _event,
    { instanceId, url, filename }: { instanceId: string; url: string; filename: string }
  ) => {
    const modsDir = getInstanceModsDir(instanceId);
    ensureDir(modsDir);
    const destPath = path.join(modsDir, filename);

    try {
      await downloadFile(url, destPath, (percent) => {
        mainWindow?.webContents.send("download-progress", {
          instanceId,
          filename,
          percent,
        });
      });
      return { success: true, path: destPath };
    } catch (err) {
      return {
        success: false,
        error: err instanceof Error ? err.message : "Download failed",
      };
    }
  }
);

ipcMain.handle(
  "remove-mod-file",
  async (_event, { instanceId, filename }: { instanceId: string; filename: string }) => {
    const filePath = path.join(getInstanceModsDir(instanceId), filename);
    try {
      if (fs.existsSync(filePath)) {
        fs.unlinkSync(filePath);
      }
      return { success: true };
    } catch (err) {
      return {
        success: false,
        error: err instanceof Error ? err.message : "Failed to remove file",
      };
    }
  }
);

ipcMain.handle("open-instance-folder", async (_event, instanceId: string) => {
  const dir = path.join(getInstancesDir(), instanceId);
  ensureDir(dir);
  shell.openPath(dir);
});

// Microsoft auth helpers
const AUTH_FILE = path.join(app.getPath("userData"), "ms-auth.json");

function loadStoredAuth(): unknown | null {
  try {
    if (fs.existsSync(AUTH_FILE)) {
      return JSON.parse(fs.readFileSync(AUTH_FILE, "utf-8"));
    }
  } catch { /* ignore */ }
  return null;
}

function saveAuth(data: unknown): void {
  fs.writeFileSync(AUTH_FILE, JSON.stringify(data, null, 2));
}

function clearAuth(): void {
  try {
    if (fs.existsSync(AUTH_FILE)) fs.unlinkSync(AUTH_FILE);
  } catch { /* ignore */ }
}

const msAuth = new Microsoft("");

ipcMain.handle("microsoft-login", async () => {
  try {
    const result = await msAuth.getAuth("electron");
    if (!result || result === false) {
      return { success: false, error: "Login cancelled" };
    }
    if ("error" in result && result.error) {
      return { success: false, error: String(result.error) };
    }
    saveAuth(result);
    return { success: true, data: result };
  } catch (err) {
    return { success: false, error: err instanceof Error ? err.message : "Login failed" };
  }
});

ipcMain.handle("microsoft-refresh", async () => {
  const stored = loadStoredAuth();
  if (!stored) return { success: false, error: "No stored session" };
  try {
    const result = await msAuth.refresh(stored as any);
    if ("error" in result && result.error) {
      clearAuth();
      return { success: false, error: String(result.error) };
    }
    saveAuth(result);
    return { success: true, data: result };
  } catch (err) {
    clearAuth();
    return { success: false, error: err instanceof Error ? err.message : "Refresh failed" };
  }
});

ipcMain.handle("microsoft-logout", async () => {
  clearAuth();
  return { success: true };
});

ipcMain.handle("microsoft-get-stored", () => {
  const stored = loadStoredAuth();
  if (stored) return { success: true, data: stored };
  return { success: false };
});

app.whenReady().then(() => {
  createWindow();

  if (!MAIN_WINDOW_VITE_DEV_SERVER_URL) {
    setupAutoUpdater();
  }

  app.on("activate", () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") app.quit();
});
