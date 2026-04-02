export interface MicrosoftAuthData {
  access_token: string;
  client_token: string;
  uuid: string;
  name: string;
  refresh_token: string;
  user_properties: string;
  meta: {
    type: "Xbox";
    access_token_expires_in: number;
    demo: boolean;
  };
  xboxAccount: {
    xuid: string;
    gamertag: string;
    ageGroup: string;
  };
  profile: {
    skins?: Array<{ id?: string; url?: string; variant?: string; base64?: string }>;
    capes?: Array<{ id?: string; url?: string; alias?: string; base64?: string }>;
  };
}

export interface MicrosoftAuthResult {
  success: boolean;
  data?: MicrosoftAuthData;
  error?: string;
}

export interface SystemInfo {
  platform: string;
  arch: string;
  totalMemoryBytes: number;
  totalMemoryMb: number;
}

export interface JavaStatus {
  required: number;
  installed: boolean;
  path: string | null;
}

export interface LaunchProgress {
  instanceId: string;
  stage: string;
  message: string;
  percent: number;
}

export interface ElectronAPI {
  getSystemTheme: () => Promise<"dark" | "light">;
  installUpdate: () => Promise<void>;
  getPlatform: () => Promise<string>;
  openExternal: (url: string) => Promise<void>;
  getAppVersion: () => Promise<string>;
  getInstancesPath: () => Promise<string>;
  downloadMod: (data: {
    instanceId: string;
    url: string;
    filename: string;
  }) => Promise<{ success: boolean; path?: string; error?: string }>;
  removeModFile: (data: {
    instanceId: string;
    filename: string;
  }) => Promise<{ success: boolean; error?: string }>;
  openInstanceFolder: (instanceId: string) => Promise<void>;
  onDownloadProgress: (
    cb: (data: { instanceId: string; filename: string; percent: number }) => void
  ) => () => void;
  onUpdateAvailable: (cb: (info: { version: string; releaseDate?: string }) => void) => () => void;
  onUpdateDownloaded: (cb: (info: { version: string; releaseDate?: string }) => void) => () => void;
  onUpdateError: (cb: (msg: string) => void) => () => void;
  checkForUpdates: () => Promise<{ success: boolean; error?: string }>;
  onCheckingForUpdate: (cb: (data: unknown) => void) => () => void;
  onUpdateNotAvailable: (cb: (data: unknown) => void) => () => void;
  onUpdateDownloadProgress: (cb: (data: {
    bytesPerSecond: number;
    percent: number;
    transferred: number;
    total: number;
  }) => void) => () => void;
  microsoftLogin: () => Promise<MicrosoftAuthResult>;
  microsoftRefresh: () => Promise<MicrosoftAuthResult>;
  microsoftLogout: () => Promise<{ success: boolean }>;
  microsoftGetStored: () => Promise<MicrosoftAuthResult>;

  // System info
  getSystemInfo: () => Promise<SystemInfo>;

  // Java management
  getJavaStatus: (data: { mcVersion: string }) => Promise<JavaStatus>;

  // Minecraft launching
  launchMinecraft: (data: {
    instanceId: string;
    version: string;
    loader: "vanilla" | "fabric" | "forge";
    memoryMb: number;
  }) => Promise<{ success: boolean; error?: string }>;
  onLaunchProgress: (cb: (data: LaunchProgress) => void) => () => void;
  onLaunchSpeed: (cb: (data: { instanceId: string; speed: number }) => void) => () => void;
  onGameClosed: (cb: (data: { instanceId: string }) => void) => () => void;
  onGameError: (cb: (data: { instanceId: string; error: string }) => void) => () => void;
  onGameLog: (cb: (data: { instanceId: string; line: string }) => void) => () => void;
}

declare global {
  interface Window {
    electronAPI: ElectronAPI;
  }
}
