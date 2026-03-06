import { useState, useEffect } from "react";
import { observer } from "mobx-react-lite";
import { useStore } from "../stores";
import {
  X,
  Share2,
  Copy,
  Check,
  Users,
  Globe,
  Loader2,
  Send,
  CheckCircle2,
  Link,
  UserPlus,
} from "lucide-react";
import type { Instance } from "../stores/InstanceStore";
import type { Friend } from "../stores/SharingStore";

interface Props {
  instance: Instance;
  onClose: () => void;
}

export const ShareInstanceModal = observer(({ instance, onClose }: Props) => {
  const { instances: store, sharing, notifications: notifStore } = useStore();
  const [syncing, setSyncing] = useState(false);
  const [error, setError] = useState("");
  const [shareCode, setShareCode] = useState(instance.shareCode ?? "");
  const [copied, setCopied] = useState(false);
  const [collaborative, setCollaborative] = useState(instance.isCollaborative);
  const [sentTo, setSentTo] = useState<Set<string>>(new Set());
  const [sendingTo, setSendingTo] = useState<string | null>(null);
  const [showCodeSection, setShowCodeSection] = useState(false);

  // Ensure the instance is synced to Supabase on modal open
  useEffect(() => {
    sharing.loadFriends();
    if (!instance.shareCode) {
      syncInstance();
    }
  }, []);

  const syncInstance = async () => {
    setSyncing(true);
    setError("");
    try {
      const code = await sharing.shareInstance(instance);
      if (code) {
        setShareCode(code);
        const data = await sharing.lookupShareCode(code);
        store.update(instance.id, {
          shareCode: code,
          sharedInstanceId: data?.id ?? null,
          syncedAt: new Date().toISOString(),
        });
      } else {
        setError("Failed to sync. Try again.");
      }
    } catch {
      setError("Failed to sync. Try again.");
    }
    setSyncing(false);
  };

  const handleCopy = () => {
    if (!shareCode) return;
    navigator.clipboard.writeText(shareCode);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleToggleCollaborative = async () => {
    const next = !collaborative;
    setCollaborative(next);
    if (instance.sharedInstanceId) {
      await sharing.setCollaborative(instance.sharedInstanceId, next);
      store.update(instance.id, { isCollaborative: next });
    }
  };

  const handleSendToFriend = async (friend: Friend) => {
    const sharedId = instance.sharedInstanceId;
    if (!sharedId) return;

    setSendingTo(friend.profile.id);

    // Also add as collaborator
    await sharing.shareToFriend(sharedId, friend.profile.id);

    // Send notification
    await notifStore.sendShareNotification(friend.profile.id, sharedId);

    setSentTo((prev) => new Set(prev).add(friend.profile.id));
    setSendingTo(null);
  };

  const acceptedFriends = sharing.friends.filter((f) => f.status === "accepted");
  const isReady = !!shareCode && !syncing;

  return (
    <div
      className="fixed inset-0 z-[200] flex items-center justify-center wizard-backdrop"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" />

      <div className="relative w-full max-w-lg mx-4 wizard-modal">
        <div className="glass rounded-3xl overflow-hidden shadow-2xl shadow-black/30">
          {/* Header */}
          <div className="relative px-8 pt-8 pb-5">
            <button
              onClick={onClose}
              className="absolute top-6 right-6 w-8 h-8 rounded-full flex items-center justify-center hover:bg-(--color-surface-tertiary) text-(--color-text-secondary) transition-colors cursor-pointer"
            >
              <X size={16} />
            </button>

            <div className="flex items-center gap-4">
              <div
                className="w-12 h-12 rounded-2xl flex items-center justify-center text-lg font-black"
                style={{
                  backgroundColor: instance.iconColor + "15",
                  color: instance.iconColor,
                }}
              >
                {instance.name[0]?.toUpperCase() ?? "?"}
              </div>
              <div>
                <h2 className="text-xl font-black tracking-tight text-(--color-text-primary)">
                  Share {instance.name}
                </h2>
                <p className="text-xs text-(--color-text-secondary) mt-0.5">
                  Send this modpack to your friends
                </p>
              </div>
            </div>
          </div>

          {/* Content */}
          <div className="px-8 pb-3 max-h-[420px] overflow-y-auto">
            {syncing ? (
              <div className="flex flex-col items-center justify-center py-12">
                <Loader2 size={28} className="text-(--color-accent) animate-spin" />
                <p className="text-sm font-semibold text-(--color-text-primary) mt-4">
                  Preparing share...
                </p>
                <p className="text-xs text-(--color-text-secondary) mt-1">
                  Syncing mods to cloud
                </p>
              </div>
            ) : error ? (
              <div className="flex flex-col items-center justify-center py-12">
                <p className="text-sm text-red-500 font-medium">{error}</p>
                <button
                  onClick={syncInstance}
                  className="mt-3 px-4 py-2 rounded-xl text-xs font-semibold bg-(--color-accent) text-white cursor-pointer"
                >
                  Retry
                </button>
              </div>
            ) : (
              <div className="space-y-5 wizard-step-enter">
                {/* Send to friends */}
                <div className="space-y-3">
                  <h3 className="text-xs font-bold text-(--color-text-secondary) uppercase tracking-wider flex items-center gap-1.5">
                    <Send size={11} />
                    Send to Friends
                  </h3>

                  {acceptedFriends.length === 0 ? (
                    <div className="rounded-2xl bg-(--color-surface-tertiary)/30 border border-(--color-border) p-5 text-center">
                      <UserPlus size={20} className="text-(--color-text-secondary) mx-auto mb-2" />
                      <p className="text-xs text-(--color-text-secondary)">
                        No friends yet. Add friends from the Friends page, or share the code below.
                      </p>
                    </div>
                  ) : (
                    <div className="space-y-2">
                      {acceptedFriends.map((friend) => {
                        const isSent = sentTo.has(friend.profile.id);
                        const isSending = sendingTo === friend.profile.id;
                        return (
                          <div
                            key={friend.id}
                            className="flex items-center gap-3 p-3.5 rounded-2xl bg-(--color-surface-tertiary)/30 border border-(--color-border) transition-all"
                          >
                            <div className="w-9 h-9 rounded-full bg-(--color-accent)/10 flex items-center justify-center text-xs font-bold text-(--color-accent)">
                              {friend.profile.mc_username[0]?.toUpperCase()}
                            </div>
                            <div className="flex-1 min-w-0">
                              <p className="text-sm font-semibold text-(--color-text-primary) truncate">
                                {friend.profile.mc_username}
                              </p>
                            </div>
                            {isSent ? (
                              <div className="flex items-center gap-1.5 text-green-500">
                                <CheckCircle2 size={15} />
                                <span className="text-[11px] font-semibold">Sent</span>
                              </div>
                            ) : (
                              <button
                                onClick={() => handleSendToFriend(friend)}
                                disabled={isSending || !isReady}
                                className="flex items-center gap-1.5 px-4 py-2 rounded-xl text-[11px] font-bold text-white transition-all cursor-pointer disabled:opacity-50"
                                style={{ backgroundColor: instance.iconColor }}
                              >
                                {isSending ? (
                                  <Loader2 size={12} className="animate-spin" />
                                ) : (
                                  <Send size={11} />
                                )}
                                Send
                              </button>
                            )}
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>

                {/* Divider */}
                <div className="flex items-center gap-3">
                  <div className="flex-1 h-px bg-(--color-border)" />
                  <span className="text-[10px] font-semibold text-(--color-text-secondary) uppercase tracking-wider">
                    or
                  </span>
                  <div className="flex-1 h-px bg-(--color-border)" />
                </div>

                {/* Share code */}
                <div className="space-y-3">
                  <button
                    onClick={() => setShowCodeSection(!showCodeSection)}
                    className="flex items-center gap-2 text-xs font-bold text-(--color-text-secondary) uppercase tracking-wider cursor-pointer hover:text-(--color-text-primary) transition-colors"
                  >
                    <Link size={11} />
                    Share via code
                  </button>

                  {(showCodeSection || acceptedFriends.length === 0) && shareCode && (
                    <div className="rounded-2xl bg-(--color-surface-tertiary)/30 border border-(--color-border) p-4 wizard-step-enter">
                      <div className="flex items-center justify-between">
                        <div>
                          <p className="text-[10px] text-(--color-text-secondary) font-medium uppercase tracking-wider mb-1">
                            Share Code
                          </p>
                          <p className="text-2xl font-black tracking-[0.15em] text-(--color-accent) font-mono">
                            {shareCode}
                          </p>
                        </div>
                        <button
                          onClick={handleCopy}
                          className="w-10 h-10 rounded-xl flex items-center justify-center bg-(--color-accent)/10 text-(--color-accent) hover:bg-(--color-accent)/20 transition-colors cursor-pointer"
                        >
                          {copied ? <Check size={16} /> : <Copy size={16} />}
                        </button>
                      </div>
                      {copied && (
                        <p className="text-[11px] text-green-500 font-medium mt-2">
                          Copied to clipboard!
                        </p>
                      )}
                    </div>
                  )}
                </div>

                {/* Collaborative toggle */}
                <div className="rounded-2xl bg-(--color-surface-tertiary)/30 border border-(--color-border) p-4">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <Users size={16} className="text-purple-500 flex-shrink-0" />
                      <div>
                        <p className="text-sm font-semibold text-(--color-text-primary)">
                          Collaborative
                        </p>
                        <p className="text-[11px] text-(--color-text-secondary)">
                          Friends see your mod changes live
                        </p>
                      </div>
                    </div>
                    <button
                      onClick={handleToggleCollaborative}
                      className={`w-11 h-6 rounded-full transition-all cursor-pointer flex-shrink-0 ${
                        collaborative
                          ? "bg-purple-500"
                          : "bg-(--color-surface-tertiary)"
                      }`}
                    >
                      <div
                        className={`w-5 h-5 rounded-full bg-white shadow-sm transition-transform ${
                          collaborative ? "translate-x-[22px]" : "translate-x-0.5"
                        }`}
                      />
                    </button>
                  </div>
                </div>
              </div>
            )}
          </div>

          {/* Footer */}
          <div className="px-8 pb-8 pt-3">
            <button
              onClick={onClose}
              className="w-full py-3 rounded-xl text-sm font-semibold text-(--color-text-secondary) hover:bg-(--color-surface-tertiary) transition-colors cursor-pointer text-center"
            >
              Done
            </button>
          </div>
        </div>
      </div>
    </div>
  );
});
