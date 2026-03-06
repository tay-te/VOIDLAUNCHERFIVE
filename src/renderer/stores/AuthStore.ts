import { makeAutoObservable, runInAction } from "mobx";
import type { MicrosoftAuthData } from "../types/electron";

export class AuthStore {
  mcAccount: MicrosoftAuthData | null = null;
  loading = true;
  error: string | null = null;
  loggingIn = false;

  constructor() {
    makeAutoObservable(this);
    this.init();
  }

  private async init() {
    const result = await window.electronAPI.microsoftGetStored();
    runInAction(() => {
      if (result.success && result.data) {
        this.mcAccount = result.data;
      }
      this.loading = false;
    });
  }

  async signInWithMicrosoft() {
    this.loggingIn = true;
    this.error = null;
    try {
      const result = await window.electronAPI.microsoftLogin();
      runInAction(() => {
        if (result.success && result.data) {
          this.mcAccount = result.data;
        } else {
          this.error = result.error ?? "Login failed";
        }
        this.loggingIn = false;
      });
    } catch {
      runInAction(() => {
        this.error = "Login failed";
        this.loggingIn = false;
      });
    }
  }

  async refreshSession() {
    try {
      const result = await window.electronAPI.microsoftRefresh();
      runInAction(() => {
        if (result.success && result.data) {
          this.mcAccount = result.data;
        } else {
          this.mcAccount = null;
        }
      });
    } catch {
      runInAction(() => {
        this.mcAccount = null;
      });
    }
  }

  async signOut() {
    await window.electronAPI.microsoftLogout();
    runInAction(() => {
      this.mcAccount = null;
    });
  }

  get isAuthenticated() {
    return !!this.mcAccount;
  }

  get username() {
    return this.mcAccount?.name ?? "Player";
  }

  get uuid() {
    return this.mcAccount?.uuid ?? null;
  }
}
