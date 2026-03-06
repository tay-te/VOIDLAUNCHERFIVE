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

export interface ElectronAPI {
  getSystemTheme: () => Promise<"dark" | "light">;
  installUpdate: () => Promise<void>;
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
  ) => void;
  onUpdateAvailable: (cb: (info: unknown) => void) => void;
  onUpdateDownloaded: (cb: (info: unknown) => void) => void;
  onUpdateError: (cb: (msg: string) => void) => void;
  microsoftLogin: () => Promise<MicrosoftAuthResult>;
  microsoftRefresh: () => Promise<MicrosoftAuthResult>;
  microsoftLogout: () => Promise<{ success: boolean }>;
  microsoftGetStored: () => Promise<MicrosoftAuthResult>;
}

declare global {
  interface Window {
    electronAPI: ElectronAPI;
  }
}
