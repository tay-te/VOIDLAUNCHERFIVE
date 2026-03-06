import { makeAutoObservable, runInAction } from "mobx";
import { supabase } from "../api/supabase";
import type { Profile, SharedInstanceData } from "./SharingStore";

export interface ShareNotification {
  id: string;
  sender: Profile;
  sharedInstance: {
    id: string;
    name: string;
    mc_version: string;
    loader: string;
    icon_color: string;
    share_code: string;
    is_collaborative: boolean;
    modCount: number;
  };
  status: "pending" | "accepted" | "dismissed";
  created_at: string;
}

export interface DownloadJob {
  id: string;
  instanceName: string;
  iconColor: string;
  totalMods: number;
  downloadedMods: number;
  currentMod: string;
  status: "downloading" | "done" | "error";
  error?: string;
}

export class NotificationStore {
  profileId: string | null = null;
  notifications: ShareNotification[] = [];
  loading = false;
  pollTimer: ReturnType<typeof setInterval> | null = null;

  // Download toast state
  activeDownload: DownloadJob | null = null;
  toastVisible = false;
  toastDismissTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    makeAutoObservable(this);
  }

  get unreadCount(): number {
    return this.notifications.filter((n) => n.status === "pending").length;
  }

  setProfileId(id: string) {
    this.profileId = id;
    this.startPolling();
  }

  dispose() {
    if (this.pollTimer) clearInterval(this.pollTimer);
    if (this.toastDismissTimer) clearTimeout(this.toastDismissTimer);
  }

  private startPolling() {
    // Initial load
    this.loadNotifications();
    // Poll every 15 seconds
    if (this.pollTimer) clearInterval(this.pollTimer);
    this.pollTimer = setInterval(() => this.loadNotifications(), 15000);
  }

  async loadNotifications() {
    if (!this.profileId) return;

    try {
      const { data, error } = await supabase
        .from("notifications")
        .select(`
          id, status, type, created_at,
          sender:profiles!sender_id(id, mc_uuid, mc_username, avatar_url),
          shared_instance:shared_instances!shared_instance_id(
            id, name, mc_version, loader, icon_color, share_code, is_collaborative
          )
        `)
        .eq("receiver_id", this.profileId)
        .in("status", ["pending", "accepted"])
        .order("created_at", { ascending: false })
        .limit(50);

      if (error || !data) return;

      runInAction(() => {
        this.notifications = data.map((row: any) => {
          const sender = Array.isArray(row.sender) ? row.sender[0] : row.sender;
          const inst = Array.isArray(row.shared_instance) ? row.shared_instance[0] : row.shared_instance;
          return {
            id: row.id,
            sender: sender as Profile,
            sharedInstance: {
              ...inst,
              modCount: 0, // we'll show this from the notification context
            },
            status: row.status,
            created_at: row.created_at,
          };
        });
      });
    } catch {
      // silent fail
    }
  }

  /** Send a share notification to a friend */
  async sendShareNotification(
    receiverProfileId: string,
    sharedInstanceId: string
  ): Promise<boolean> {
    if (!this.profileId) return false;

    // Check if already sent
    const { data: existing } = await supabase
      .from("notifications")
      .select("id")
      .eq("sender_id", this.profileId)
      .eq("receiver_id", receiverProfileId)
      .eq("shared_instance_id", sharedInstanceId)
      .in("status", ["pending", "accepted"])
      .limit(1);

    if (existing && existing.length > 0) return true; // already sent

    const { error } = await supabase.from("notifications").insert({
      sender_id: this.profileId,
      receiver_id: receiverProfileId,
      shared_instance_id: sharedInstanceId,
      type: "instance_share",
      status: "pending",
    });

    return !error;
  }

  /** Mark a notification as accepted */
  async markAccepted(notificationId: string) {
    await supabase
      .from("notifications")
      .update({ status: "accepted" })
      .eq("id", notificationId);

    runInAction(() => {
      const n = this.notifications.find((n) => n.id === notificationId);
      if (n) n.status = "accepted";
    });
  }

  /** Dismiss a notification */
  async dismiss(notificationId: string) {
    await supabase
      .from("notifications")
      .update({ status: "dismissed" })
      .eq("id", notificationId);

    runInAction(() => {
      this.notifications = this.notifications.filter((n) => n.id !== notificationId);
    });
  }

  /** Start a download job (for the toast) */
  startDownload(job: Omit<DownloadJob, "status">) {
    if (this.toastDismissTimer) clearTimeout(this.toastDismissTimer);
    this.activeDownload = { ...job, status: "downloading" };
    this.toastVisible = true;
  }

  updateDownloadProgress(downloadedMods: number, currentMod: string) {
    if (!this.activeDownload) return;
    this.activeDownload.downloadedMods = downloadedMods;
    this.activeDownload.currentMod = currentMod;
  }

  finishDownload() {
    if (!this.activeDownload) return;
    this.activeDownload.status = "done";
    this.activeDownload.currentMod = "Complete!";
    // Auto-dismiss after 4 seconds
    this.toastDismissTimer = setTimeout(() => {
      runInAction(() => {
        this.toastVisible = false;
        this.activeDownload = null;
      });
    }, 4000);
  }

  failDownload(error: string) {
    if (!this.activeDownload) return;
    this.activeDownload.status = "error";
    this.activeDownload.error = error;
    this.toastDismissTimer = setTimeout(() => {
      runInAction(() => {
        this.toastVisible = false;
        this.activeDownload = null;
      });
    }, 6000);
  }

  dismissToast() {
    if (this.toastDismissTimer) clearTimeout(this.toastDismissTimer);
    this.toastVisible = false;
    this.activeDownload = null;
  }
}
