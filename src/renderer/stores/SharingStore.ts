import { makeAutoObservable, runInAction } from "mobx";
import { supabase } from "../api/supabase";
import type { Instance, InstalledMod } from "./InstanceStore";

export interface Profile {
  id: string;
  mc_uuid: string;
  mc_username: string;
  avatar_url: string | null;
}

export interface Friend {
  id: string;
  profile: Profile;
  status: "pending" | "accepted";
  isRequester: boolean;
}

export interface SharedInstanceData {
  id: string;
  name: string;
  mc_version: string;
  loader: string;
  icon_color: string;
  share_code: string;
  is_collaborative: boolean;
  owner: Profile;
  mods: {
    project_id: string;
    version_id: string;
    title: string;
    icon_url: string | null;
    filename: string;
  }[];
  collaborators: { profile: Profile; role: string }[];
}

function generateShareCode(): string {
  const chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  let code = "";
  for (let i = 0; i < 6; i++) {
    code += chars[Math.floor(Math.random() * chars.length)];
  }
  return code;
}

export class SharingStore {
  profileId: string | null = null;
  friends: Friend[] = [];
  loadingFriends = false;
  friendCode: string | null = null;

  constructor() {
    makeAutoObservable(this);
  }

  /** Ensure a profile exists in Supabase for this MC account */
  async ensureProfile(mcUuid: string, mcUsername: string): Promise<string | null> {
    try {
      // Try to find existing profile
      const { data: existing } = await supabase
        .from("profiles")
        .select("id")
        .eq("mc_uuid", mcUuid)
        .single();

      if (existing) {
        // Update username if changed
        await supabase
          .from("profiles")
          .update({ mc_username: mcUsername, updated_at: new Date().toISOString() })
          .eq("id", existing.id);
        runInAction(() => {
          this.profileId = existing.id;
        });
        return existing.id;
      }

      // Create new profile
      const { data: created, error } = await supabase
        .from("profiles")
        .insert({ mc_uuid: mcUuid, mc_username: mcUsername })
        .select("id")
        .single();

      if (error || !created) return null;

      runInAction(() => {
        this.profileId = created.id;
      });
      return created.id;
    } catch {
      return null;
    }
  }

  /** Share an instance - creates a shared_instance in Supabase */
  async shareInstance(instance: Instance): Promise<string | null> {
    if (!this.profileId) return null;

    try {
      const shareCode = generateShareCode();

      const { data: shared, error } = await supabase
        .from("shared_instances")
        .insert({
          owner_id: this.profileId,
          name: instance.name,
          mc_version: instance.version,
          loader: instance.loader,
          icon_color: instance.iconColor,
          share_code: shareCode,
          is_collaborative: false,
        })
        .select("id, share_code")
        .single();

      if (error || !shared) return null;

      // Sync all installed mods
      if (instance.installedMods.length > 0) {
        const modRows = instance.installedMods.map((m) => ({
          instance_id: shared.id,
          project_id: m.projectId,
          version_id: m.versionId,
          title: m.title,
          icon_url: m.iconUrl,
          filename: m.filename,
          added_by: this.profileId,
        }));

        await supabase.from("shared_instance_mods").insert(modRows);
      }

      return shared.share_code;
    } catch {
      return null;
    }
  }

  /** Update share code to enable/disable collaborative mode */
  async setCollaborative(sharedInstanceId: string, collaborative: boolean) {
    await supabase
      .from("shared_instances")
      .update({ is_collaborative: collaborative, updated_at: new Date().toISOString() })
      .eq("id", sharedInstanceId);
  }

  /** Sync local instance mods to Supabase */
  async syncInstanceMods(instance: Instance) {
    if (!instance.sharedInstanceId || !this.profileId) return;

    // Delete existing mods and re-insert
    await supabase
      .from("shared_instance_mods")
      .delete()
      .eq("instance_id", instance.sharedInstanceId);

    if (instance.installedMods.length > 0) {
      const modRows = instance.installedMods.map((m) => ({
        instance_id: instance.sharedInstanceId!,
        project_id: m.projectId,
        version_id: m.versionId,
        title: m.title,
        icon_url: m.iconUrl,
        filename: m.filename,
        added_by: this.profileId,
      }));

      await supabase.from("shared_instance_mods").insert(modRows);
    }

    await supabase
      .from("shared_instances")
      .update({ updated_at: new Date().toISOString() })
      .eq("id", instance.sharedInstanceId);
  }

  /** Look up a share code and get the instance data */
  async lookupShareCode(code: string): Promise<SharedInstanceData | null> {
    try {
      const { data: shared, error } = await supabase
        .from("shared_instances")
        .select(`
          id, name, mc_version, loader, icon_color, share_code, is_collaborative,
          owner:profiles!owner_id(id, mc_uuid, mc_username, avatar_url)
        `)
        .eq("share_code", code.toUpperCase().trim())
        .single();

      if (error || !shared) return null;

      // Fetch mods
      const { data: mods } = await supabase
        .from("shared_instance_mods")
        .select("project_id, version_id, title, icon_url, filename")
        .eq("instance_id", shared.id);

      // Fetch collaborators
      const { data: collabs } = await supabase
        .from("instance_collaborators")
        .select("role, profile:profiles!profile_id(id, mc_uuid, mc_username, avatar_url)")
        .eq("instance_id", shared.id);

      const ownerData = Array.isArray(shared.owner) ? shared.owner[0] : shared.owner;

      return {
        id: shared.id,
        name: shared.name,
        mc_version: shared.mc_version,
        loader: shared.loader,
        icon_color: shared.icon_color,
        share_code: shared.share_code,
        is_collaborative: shared.is_collaborative,
        owner: ownerData as Profile,
        mods: mods ?? [],
        collaborators: (collabs ?? []).map((c: any) => ({
          profile: Array.isArray(c.profile) ? c.profile[0] : c.profile,
          role: c.role,
        })),
      };
    } catch {
      return null;
    }
  }

  /** Join as collaborator */
  async joinAsCollaborator(sharedInstanceId: string) {
    if (!this.profileId) return;
    await supabase.from("instance_collaborators").upsert({
      instance_id: sharedInstanceId,
      profile_id: this.profileId,
      role: "editor",
    });
  }

  /** Load friends list */
  async loadFriends() {
    if (!this.profileId) return;
    this.loadingFriends = true;

    try {
      // Friends where I'm the requester
      const { data: sent } = await supabase
        .from("friends")
        .select("id, status, receiver:profiles!receiver_id(id, mc_uuid, mc_username, avatar_url)")
        .eq("requester_id", this.profileId);

      // Friends where I'm the receiver
      const { data: received } = await supabase
        .from("friends")
        .select("id, status, requester:profiles!requester_id(id, mc_uuid, mc_username, avatar_url)")
        .eq("receiver_id", this.profileId);

      runInAction(() => {
        const friends: Friend[] = [];

        for (const f of sent ?? []) {
          const profile = Array.isArray(f.receiver) ? f.receiver[0] : f.receiver;
          friends.push({
            id: f.id,
            profile: profile as Profile,
            status: f.status as "pending" | "accepted",
            isRequester: true,
          });
        }

        for (const f of received ?? []) {
          const profile = Array.isArray(f.requester) ? f.requester[0] : f.requester;
          friends.push({
            id: f.id,
            profile: profile as Profile,
            status: f.status as "pending" | "accepted",
            isRequester: false,
          });
        }

        this.friends = friends;
        this.loadingFriends = false;
      });
    } catch {
      runInAction(() => {
        this.loadingFriends = false;
      });
    }
  }

  /** Send friend request by MC username */
  async addFriend(mcUsername: string): Promise<{ success: boolean; error?: string }> {
    if (!this.profileId) return { success: false, error: "Not logged in" };

    // Find the profile by username
    const { data: target } = await supabase
      .from("profiles")
      .select("id")
      .ilike("mc_username", mcUsername.trim())
      .single();

    if (!target) return { success: false, error: "Player not found. They must have opened VOID Launcher at least once." };

    if (target.id === this.profileId) return { success: false, error: "You can't add yourself" };

    // Check if already friends
    const existing = this.friends.find((f) => f.profile.id === target.id);
    if (existing) return { success: false, error: "Already friends or request pending" };

    const { error } = await supabase.from("friends").insert({
      requester_id: this.profileId,
      receiver_id: target.id,
    });

    if (error) return { success: false, error: "Failed to send request" };

    await this.loadFriends();
    return { success: true };
  }

  /** Accept a friend request */
  async acceptFriend(friendId: string) {
    await supabase
      .from("friends")
      .update({ status: "accepted" })
      .eq("id", friendId);
    await this.loadFriends();
  }

  /** Remove a friend */
  async removeFriend(friendId: string) {
    await supabase.from("friends").delete().eq("id", friendId);
    await this.loadFriends();
  }

  /** Share instance directly to a friend (adds them as collaborator) */
  async shareToFriend(sharedInstanceId: string, friendProfileId: string) {
    await supabase.from("instance_collaborators").upsert({
      instance_id: sharedInstanceId,
      profile_id: friendProfileId,
      role: "editor",
    });
  }

  /** Get all shared instances owned by the current user */
  async getMySharedInstances(): Promise<SharedInstanceData[]> {
    if (!this.profileId) return [];

    try {
      const { data: owned } = await supabase
        .from("shared_instances")
        .select(`
          id, name, mc_version, loader, icon_color, share_code, is_collaborative,
          owner:profiles!owner_id(id, mc_uuid, mc_username, avatar_url)
        `)
        .eq("owner_id", this.profileId);

      if (!owned || owned.length === 0) return [];

      const results: SharedInstanceData[] = [];
      for (const shared of owned) {
        const { data: mods } = await supabase
          .from("shared_instance_mods")
          .select("project_id, version_id, title, icon_url, filename")
          .eq("instance_id", shared.id);

        const ownerData = Array.isArray(shared.owner) ? shared.owner[0] : shared.owner;

        results.push({
          id: shared.id,
          name: shared.name,
          mc_version: shared.mc_version,
          loader: shared.loader,
          icon_color: shared.icon_color,
          share_code: shared.share_code,
          is_collaborative: shared.is_collaborative,
          owner: ownerData as Profile,
          mods: mods ?? [],
          collaborators: [],
        });
      }

      return results;
    } catch {
      return [];
    }
  }

  /** Get instances shared with me */
  async getSharedWithMe(): Promise<SharedInstanceData[]> {
    if (!this.profileId) return [];

    try {
      const { data: collabs } = await supabase
        .from("instance_collaborators")
        .select("instance_id")
        .eq("profile_id", this.profileId);

      if (!collabs || collabs.length === 0) return [];

      const instanceIds = collabs.map((c) => c.instance_id);

      const results: SharedInstanceData[] = [];
      for (const id of instanceIds) {
        const { data: shared } = await supabase
          .from("shared_instances")
          .select(`
            id, name, mc_version, loader, icon_color, share_code, is_collaborative,
            owner:profiles!owner_id(id, mc_uuid, mc_username, avatar_url)
          `)
          .eq("id", id)
          .single();

        if (!shared) continue;

        const { data: mods } = await supabase
          .from("shared_instance_mods")
          .select("project_id, version_id, title, icon_url, filename")
          .eq("instance_id", id);

        const ownerData = Array.isArray(shared.owner) ? shared.owner[0] : shared.owner;

        results.push({
          id: shared.id,
          name: shared.name,
          mc_version: shared.mc_version,
          loader: shared.loader,
          icon_color: shared.icon_color,
          share_code: shared.share_code,
          is_collaborative: shared.is_collaborative,
          owner: ownerData as Profile,
          mods: mods ?? [],
          collaborators: [],
        });
      }

      return results;
    } catch {
      return [];
    }
  }
}
