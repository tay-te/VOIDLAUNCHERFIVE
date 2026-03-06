import { app, BrowserWindow, ipcMain, nativeTheme, shell, session } from "electron";
import { autoUpdater } from "electron-updater";
import path from "node:path";
import fs from "node:fs";
import https from "node:https";
import http from "node:http";
import os from "node:os";
import { execSync } from "node:child_process";
import { Microsoft, Launch } from "minecraft-java-core";

// Handle Squirrel events for Windows installer
import electronSquirrelStartup from "electron-squirrel-startup";
if (electronSquirrelStartup) app.quit();

declare const MAIN_WINDOW_VITE_DEV_SERVER_URL: string;
declare const MAIN_WINDOW_VITE_NAME: string;

let mainWindow: BrowserWindow | null = null;

function getInstancesDir(): string {
  return path.join(app.getPath("userData"), "instances");
}

function getInstanceModsDir(instanceId: string): string {
  return path.join(getInstancesDir(), instanceId, "mods");
}

// Sanitize user-provided path segments to prevent directory traversal
function sanitizePathSegment(segment: string): string {
  // Strip any path separators and parent directory references
  return segment.replace(/[/\\]/g, "").replace(/\.\./g, "");
}

function isPathWithin(parent: string, child: string): boolean {
  const resolvedParent = path.resolve(parent) + path.sep;
  const resolvedChild = path.resolve(child);
  return resolvedChild.startsWith(resolvedParent) || resolvedChild === path.resolve(parent);
}

function ensureDir(dir: string): void {
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
  }
}

// ─── Java version mapping ───────────────────────────────────────────
function getRequiredJavaVersion(mcVersion: string): number {
  const parts = mcVersion.split(".");
  const major = parseInt(parts[0]) || 1;
  const minor = parseInt(parts[1]) || 0;
  const patch = parseInt(parts[2]) || 0;

  // MC 1.20.5+ requires Java 21
  if (major > 1 || (major === 1 && minor > 20) || (major === 1 && minor === 20 && patch >= 5)) {
    return 21;
  }
  // MC 1.17+ requires Java 17
  if (major > 1 || (major === 1 && minor >= 17)) {
    return 17;
  }
  // Older versions use Java 8
  return 8;
}

// ─── Adoptium OS/arch mapping ───────────────────────────────────────
function getAdoptiumOS(): string {
  switch (process.platform) {
    case "darwin": return "mac";
    case "win32": return "windows";
    default: return "linux";
  }
}

function getAdoptiumArch(): string {
  switch (process.arch) {
    case "arm64": return "aarch64";
    case "x64": return "x64";
    default: return "x64";
  }
}

// ─── Java directory management ──────────────────────────────────────
function getJavaBaseDir(): string {
  return path.join(app.getPath("userData"), "java");
}

function getJavaVersionDir(javaVersion: number): string {
  return path.join(getJavaBaseDir(), `java-${javaVersion}-${getAdoptiumArch()}`);
}

function findJavaBinary(javaVersionDir: string): string | null {
  if (!fs.existsSync(javaVersionDir)) return null;

  const entries = fs.readdirSync(javaVersionDir);
  const jdkDir = entries.find((e) => e.startsWith("jdk-") || e.startsWith("jdk"));
  if (!jdkDir) return null;

  const jdkPath = path.join(javaVersionDir, jdkDir);
  const javaBinName = process.platform === "win32" ? "javaw.exe" : "java";

  // macOS JDKs have Contents/Home structure
  const macPath = path.join(jdkPath, "Contents", "Home", "bin", javaBinName);
  if (fs.existsSync(macPath)) return macPath;

  const stdPath = path.join(jdkPath, "bin", javaBinName);
  if (fs.existsSync(stdPath)) return stdPath;

  return null;
}

// ─── Fetch JSON helper ──────────────────────────────────────────────
function fetchJson(url: string): Promise<any> {
  return new Promise((resolve, reject) => {
    const proto = url.startsWith("https") ? https : http;
    proto
      .get(url, { headers: { Accept: "application/json" } }, (res) => {
        if (res.statusCode && res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
          fetchJson(res.headers.location).then(resolve).catch(reject);
          return;
        }
        if (res.statusCode !== 200) {
          reject(new Error(`HTTP ${res.statusCode}`));
          return;
        }
        let data = "";
        res.on("data", (chunk: Buffer) => (data += chunk));
        res.on("end", () => {
          try { resolve(JSON.parse(data)); } catch (e) { reject(e); }
        });
        res.on("error", reject);
      })
      .on("error", reject);
  });
}

// ─── Adoptium Java download & extraction ────────────────────────────
async function ensureJava(
  javaVersion: number,
  progressCb?: (data: { stage: string; percent: number }) => void
): Promise<string> {
  const versionDir = getJavaVersionDir(javaVersion);

  // Check if already installed
  const existing = findJavaBinary(versionDir);
  if (existing) return existing;

  ensureDir(versionDir);

  const adoptOS = getAdoptiumOS();
  const adoptArch = getAdoptiumArch();

  progressCb?.({ stage: "Fetching Java info from Adoptium...", percent: 0 });

  const apiUrl = `https://api.adoptium.net/v3/assets/latest/${javaVersion}/hotspot?architecture=${adoptArch}&image_type=jdk&os=${adoptOS}&vendor=eclipse`;

  const assets = await fetchJson(apiUrl);
  if (!assets || !assets.length) {
    throw new Error(`No Adoptium JDK found for Java ${javaVersion} on ${adoptOS} ${adoptArch}`);
  }

  const pkg = assets[0].binary.package;
  const downloadUrl: string = pkg.link;
  const fileName: string = pkg.name;
  const archivePath = path.join(versionDir, fileName);

  progressCb?.({ stage: "Downloading Java from Adoptium...", percent: 5 });

  await downloadFile(downloadUrl, archivePath, (percent) => {
    progressCb?.({ stage: "Downloading Java...", percent: 5 + Math.floor(percent * 0.75) });
  });

  progressCb?.({ stage: "Extracting Java...", percent: 82 });

  // Extract archive
  if (fileName.endsWith(".tar.gz") || fileName.endsWith(".tgz")) {
    execSync(`tar -xzf "${archivePath}" -C "${versionDir}"`);
  } else if (fileName.endsWith(".zip")) {
    if (process.platform === "win32") {
      execSync(
        `powershell -command "Expand-Archive -Path '${archivePath}' -DestinationPath '${versionDir}' -Force"`
      );
    } else {
      execSync(`unzip -o "${archivePath}" -d "${versionDir}"`);
    }
  }

  // Clean up archive
  try { fs.unlinkSync(archivePath); } catch { /* ignore */ }

  progressCb?.({ stage: "Java ready!", percent: 100 });

  const javaBin = findJavaBinary(versionDir);
  if (!javaBin) throw new Error("Failed to locate java binary after extraction");

  // Ensure executable permission on Unix
  if (process.platform !== "win32") {
    try { fs.chmodSync(javaBin, 0o755); } catch { /* ignore */ }
  }

  return javaBin;
}

// ─── Minecraft root directory ───────────────────────────────────────
function getMinecraftRoot(): string {
  return path.join(app.getPath("userData"), "minecraft");
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

  // Set Content Security Policy
  session.defaultSession.webRequest.onHeadersReceived((details, callback) => {
    callback({
      responseHeaders: {
        ...details.responseHeaders,
        "Content-Security-Policy": [
          MAIN_WINDOW_VITE_DEV_SERVER_URL
            ? "default-src 'self' 'unsafe-inline' 'unsafe-eval'; connect-src 'self' ws: https://api.modrinth.com https://*.supabase.co https://crafatar.com; img-src 'self' data: https://cdn.modrinth.com https://crafatar.com https://*.supabase.co;"
            : "default-src 'self' 'unsafe-inline'; script-src 'self'; connect-src 'self' https://api.modrinth.com https://*.supabase.co https://crafatar.com https://cdn-raw.modrinth.com; img-src 'self' data: https://cdn.modrinth.com https://crafatar.com https://*.supabase.co; font-src 'self' data:;",
        ],
      },
    });
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
  autoUpdater.setFeedURL({
    provider: "github",
    owner: "tay-te",
    repo: "VOIDLAUNCHERFIVE",
  });
  autoUpdater.autoDownload = true;
  autoUpdater.autoInstallOnAppQuit = true;
  autoUpdater.allowDowngrade = false;

  autoUpdater.on("update-available", (info) => {
    mainWindow?.webContents.send("update-available", info);
  });

  autoUpdater.on("update-downloaded", (info) => {
    mainWindow?.webContents.send("update-downloaded", info);
  });

  autoUpdater.on("error", (err) => {
    console.error("Auto-updater error:", err.message);
    mainWindow?.webContents.send("update-error", err.message);
  });

  // Check now, then every 30 minutes
  autoUpdater.checkForUpdates().catch(() => {});
  setInterval(() => autoUpdater.checkForUpdates().catch(() => {}), 30 * 60 * 1000);
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
    const safeInstanceId = sanitizePathSegment(instanceId);
    const safeFilename = sanitizePathSegment(filename);
    if (!safeInstanceId || !safeFilename) {
      return { success: false, error: "Invalid instance ID or filename" };
    }

    // Validate URL is from Modrinth CDN only
    try {
      const parsed = new URL(url);
      if (!parsed.hostname.endsWith("modrinth.com") && !parsed.hostname.endsWith("cdn-raw.modrinth.com")) {
        return { success: false, error: "Downloads only allowed from Modrinth" };
      }
    } catch {
      return { success: false, error: "Invalid download URL" };
    }

    const modsDir = getInstanceModsDir(safeInstanceId);
    ensureDir(modsDir);
    const destPath = path.join(modsDir, safeFilename);

    // Final path containment check
    if (!isPathWithin(modsDir, destPath)) {
      return { success: false, error: "Invalid file path" };
    }

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
    const safeInstanceId = sanitizePathSegment(instanceId);
    const safeFilename = sanitizePathSegment(filename);
    if (!safeInstanceId || !safeFilename) {
      return { success: false, error: "Invalid instance ID or filename" };
    }

    const modsDir = getInstanceModsDir(safeInstanceId);
    const filePath = path.join(modsDir, safeFilename);

    // Path containment check
    if (!isPathWithin(modsDir, filePath)) {
      return { success: false, error: "Invalid file path" };
    }

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
  const safeInstanceId = sanitizePathSegment(instanceId);
  if (!safeInstanceId) return;
  const dir = path.join(getInstancesDir(), safeInstanceId);
  if (!isPathWithin(getInstancesDir(), dir)) return;
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

// ─── System info ────────────────────────────────────────────────────
ipcMain.handle("get-system-info", () => {
  return {
    platform: process.platform,
    arch: process.arch,
    totalMemoryBytes: os.totalmem(),
    totalMemoryMb: Math.floor(os.totalmem() / (1024 * 1024)),
  };
});

// ─── Java status ────────────────────────────────────────────────────
ipcMain.handle("get-java-status", (_event, { mcVersion }: { mcVersion: string }) => {
  const javaVersion = getRequiredJavaVersion(mcVersion);
  const versionDir = getJavaVersionDir(javaVersion);
  const javaBin = findJavaBinary(versionDir);
  return {
    required: javaVersion,
    installed: javaBin !== null,
    path: javaBin,
  };
});

// ─── Launch Minecraft ───────────────────────────────────────────────
ipcMain.handle(
  "launch-minecraft",
  async (
    _event,
    {
      instanceId,
      version,
      loader,
      memoryMb,
    }: {
      instanceId: string;
      version: string;
      loader: "vanilla" | "fabric" | "forge";
      memoryMb: number;
    }
  ) => {
    try {
      // 0. Validate inputs
      if (!/^\d+\.\d+(\.\d+)?$/.test(version)) {
        return { success: false, error: "Invalid Minecraft version format" };
      }
      if (!["vanilla", "fabric", "forge"].includes(loader)) {
        return { success: false, error: "Invalid loader type" };
      }
      const clampedMemory = Math.min(Math.max(512, Math.floor(memoryMb)), 32768);

      // 1. Check auth
      const stored = loadStoredAuth() as any;
      if (!stored) {
        return { success: false, error: "Not logged in. Please sign in with Microsoft first." };
      }

      // 2. Ensure Java is installed
      const javaVersion = getRequiredJavaVersion(version);
      mainWindow?.webContents.send("launch-progress", {
        instanceId,
        stage: "java",
        message: `Checking Java ${javaVersion}...`,
        percent: 0,
      });

      const javaBin = await ensureJava(javaVersion, (progress) => {
        mainWindow?.webContents.send("launch-progress", {
          instanceId,
          stage: "java",
          message: progress.stage,
          percent: progress.percent,
        });
      });

      // 3. Set up paths (sanitize instanceId)
      const safeInstanceId = sanitizePathSegment(instanceId);
      if (!safeInstanceId) {
        return { success: false, error: "Invalid instance ID" };
      }
      const mcRoot = getMinecraftRoot();
      const instanceDir = path.join(getInstancesDir(), safeInstanceId);
      if (!isPathWithin(getInstancesDir(), instanceDir)) {
        return { success: false, error: "Invalid instance path" };
      }
      ensureDir(mcRoot);
      ensureDir(instanceDir);

      // 4. Launch via minecraft-java-core
      mainWindow?.webContents.send("launch-progress", {
        instanceId,
        stage: "assets",
        message: "Preparing game files...",
        percent: 0,
      });

      const launcher = new Launch();

      launcher.on("progress", (progress: number, size: number, element: string) => {
        mainWindow?.webContents.send("launch-progress", {
          instanceId,
          stage: "download",
          message: element,
          percent: size > 0 ? Math.round((progress / size) * 100) : 0,
        });
      });

      launcher.on("speed", (speed: number) => {
        mainWindow?.webContents.send("launch-speed", { instanceId, speed });
      });

      launcher.on("estimated", (time: number) => {
        mainWindow?.webContents.send("launch-estimated", { instanceId, time });
      });

      launcher.on("patch", (patch: any) => {
        mainWindow?.webContents.send("launch-progress", {
          instanceId,
          stage: "patch",
          message: typeof patch === "string" ? patch : "Patching...",
          percent: -1,
        });
      });

      launcher.on("check", (progress: number, size: number, element: string) => {
        mainWindow?.webContents.send("launch-progress", {
          instanceId,
          stage: "check",
          message: `Checking ${element}`,
          percent: size > 0 ? Math.round((progress / size) * 100) : 0,
        });
      });

      launcher.on("extract", (extract: any) => {
        mainWindow?.webContents.send("launch-progress", {
          instanceId,
          stage: "extract",
          message: typeof extract === "string" ? extract : "Extracting...",
          percent: -1,
        });
      });

      launcher.on("data", (data: string) => {
        mainWindow?.webContents.send("game-log", { instanceId, line: data });
      });

      launcher.on("close", () => {
        mainWindow?.webContents.send("game-closed", { instanceId });
      });

      launcher.on("error", (err: any) => {
        mainWindow?.webContents.send("game-error", {
          instanceId,
          error: typeof err === "string" ? err : err?.error || err?.message || "Unknown error",
        });
      });

      const minMemory = Math.max(512, Math.floor(clampedMemory / 2));

      await launcher.Launch({
        url: null,
        authenticator: stored,
        timeout: 10000,
        path: mcRoot,
        version: version,
        instance: null,
        detached: false,
        downloadFileMultiple: 5,
        intelEnabledMac: false,
        ignore_log4j: false,
        loader: {
          type: loader === "vanilla" ? null : loader,
          build: "latest",
          enable: loader !== "vanilla",
        },
        verify: false,
        ignored: [],
        JVM_ARGS: [],
        GAME_ARGS: ["--gameDir", instanceDir],
        java: {
          path: javaBin,
          type: "jdk",
        },
        screen: {},
        memory: {
          min: `${minMemory}M`,
          max: `${clampedMemory}M`,
        },
      });

      mainWindow?.webContents.send("launch-progress", {
        instanceId,
        stage: "launched",
        message: "Game launched!",
        percent: 100,
      });

      return { success: true };
    } catch (err) {
      return {
        success: false,
        error: err instanceof Error ? err.message : "Launch failed",
      };
    }
  }
);

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
