import { useState, useEffect } from "react";
import { observer } from "mobx-react-lite";
import { useStore } from "../stores";
import {
  Users,
  UserPlus,
  Check,
  X,
  Loader2,
  Search,
  UserCheck,
  Clock,
  Trash2,
  Download,
  Share2,
} from "lucide-react";
import type { Friend } from "../stores/SharingStore";

interface Props {
  onImportCode?: () => void;
}

export const FriendsPage = observer(({ onImportCode }: Props) => {
  const { sharing, auth } = useStore();
  const [addUsername, setAddUsername] = useState("");
  const [addLoading, setAddLoading] = useState(false);
  const [addError, setAddError] = useState("");
  const [addSuccess, setAddSuccess] = useState(false);

  useEffect(() => {
    if (auth.uuid) {
      sharing.ensureProfile(auth.uuid, auth.username).then(() => {
        sharing.loadFriends();
      });
    }
  }, [auth.uuid]);

  const handleAddFriend = async () => {
    if (!addUsername.trim()) return;
    setAddLoading(true);
    setAddError("");
    setAddSuccess(false);

    const result = await sharing.addFriend(addUsername.trim());
    if (result.success) {
      setAddSuccess(true);
      setAddUsername("");
      setTimeout(() => setAddSuccess(false), 3000);
    } else {
      setAddError(result.error ?? "Failed to send request");
    }
    setAddLoading(false);
  };

  const acceptedFriends = sharing.friends.filter((f) => f.status === "accepted");
  const pendingIncoming = sharing.friends.filter(
    (f) => f.status === "pending" && !f.isRequester
  );
  const pendingOutgoing = sharing.friends.filter(
    (f) => f.status === "pending" && f.isRequester
  );

  return (
    <div className="p-10 space-y-8 max-w-3xl mx-auto wizard-step-enter">
      {/* Header */}
      <div>
        <h1 className="text-3xl font-black tracking-tight text-(--color-text-primary)">
          <span className="void-text">Friends</span> & Sharing
        </h1>
        <p className="text-sm text-(--color-text-secondary) mt-1.5">
          Add friends and share your modpacks
        </p>
      </div>

      {/* Quick actions */}
      <div className="grid grid-cols-2 gap-4">
        <button
          onClick={onImportCode}
          className="flex items-center gap-3 p-5 rounded-2xl glass-subtle hover:border-(--color-accent)/30 transition-all cursor-pointer group"
        >
          <div className="w-11 h-11 rounded-xl bg-(--color-accent)/10 flex items-center justify-center text-(--color-accent) transition-transform group-hover:scale-110">
            <Download size={20} />
          </div>
          <div className="text-left">
            <p className="text-sm font-bold text-(--color-text-primary)">
              Import Share Code
            </p>
            <p className="text-[11px] text-(--color-text-secondary) mt-0.5">
              Enter a friend's code to get their pack
            </p>
          </div>
        </button>
        <div className="flex items-center gap-3 p-5 rounded-2xl glass-subtle">
          <div className="w-11 h-11 rounded-xl bg-purple-500/10 flex items-center justify-center text-purple-500">
            <Share2 size={20} />
          </div>
          <div className="text-left">
            <p className="text-sm font-bold text-(--color-text-primary)">
              Your Profile
            </p>
            <p className="text-[11px] text-(--color-text-secondary) mt-0.5">
              {auth.username}
            </p>
          </div>
        </div>
      </div>

      {/* Add friend */}
      <section className="space-y-4">
        <h2 className="text-xl font-black text-(--color-text-primary) tracking-tight">
          Add Friend
        </h2>
        <div className="glass-subtle rounded-2xl p-5">
          <div className="flex items-center gap-3">
            <div className="relative flex-1">
              <Search
                size={14}
                className="absolute left-4 top-1/2 -translate-y-1/2 text-(--color-text-secondary)"
              />
              <input
                type="text"
                value={addUsername}
                onChange={(e) => {
                  setAddUsername(e.target.value);
                  setAddError("");
                }}
                onKeyDown={(e) => e.key === "Enter" && handleAddFriend()}
                placeholder="Enter Minecraft username..."
                className="w-full pl-10 pr-4 py-3 rounded-xl bg-(--color-surface-tertiary)/50 border border-(--color-border) text-sm text-(--color-text-primary) placeholder:text-(--color-text-secondary)/50 focus:outline-none focus:ring-2 focus:ring-(--color-accent)/50 transition-all"
              />
            </div>
            <button
              onClick={handleAddFriend}
              disabled={addLoading || !addUsername.trim()}
              className="flex items-center gap-2 px-5 py-3 rounded-xl bg-(--color-accent) hover:bg-(--color-accent-hover) text-white text-sm font-semibold transition-all cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
            >
              {addLoading ? (
                <Loader2 size={15} className="animate-spin" />
              ) : addSuccess ? (
                <Check size={15} />
              ) : (
                <UserPlus size={15} />
              )}
              {addSuccess ? "Sent!" : "Add"}
            </button>
          </div>
          {addError && (
            <p className="text-xs text-red-500 font-medium mt-2 ml-1">{addError}</p>
          )}
          {addSuccess && (
            <p className="text-xs text-green-500 font-medium mt-2 ml-1">
              Friend request sent!
            </p>
          )}
        </div>
      </section>

      {/* Pending requests */}
      {pendingIncoming.length > 0 && (
        <section className="space-y-4">
          <h2 className="text-xl font-black text-(--color-text-primary) tracking-tight flex items-center gap-2">
            Pending Requests
            <span className="text-xs px-2 py-0.5 rounded-full bg-(--color-accent)/10 text-(--color-accent) font-bold">
              {pendingIncoming.length}
            </span>
          </h2>
          <div className="space-y-2">
            {pendingIncoming.map((friend) => (
              <FriendRequestCard
                key={friend.id}
                friend={friend}
                onAccept={() => sharing.acceptFriend(friend.id)}
                onReject={() => sharing.removeFriend(friend.id)}
              />
            ))}
          </div>
        </section>
      )}

      {/* Friends list */}
      <section className="space-y-4">
        <h2 className="text-xl font-black text-(--color-text-primary) tracking-tight">
          Your Friends
          {acceptedFriends.length > 0 && (
            <span className="text-sm font-medium text-(--color-text-secondary) ml-2">
              ({acceptedFriends.length})
            </span>
          )}
        </h2>

        {sharing.loadingFriends ? (
          <div className="flex items-center justify-center py-12">
            <Loader2 size={24} className="text-(--color-accent) animate-spin" />
          </div>
        ) : acceptedFriends.length === 0 && pendingOutgoing.length === 0 ? (
          <div className="glass-subtle rounded-2xl">
            <div className="flex flex-col items-center justify-center py-16 text-(--color-text-secondary)">
              <div className="w-14 h-14 rounded-2xl bg-(--color-accent)/10 flex items-center justify-center mb-4">
                <Users size={24} className="text-(--color-accent)" />
              </div>
              <p className="text-sm font-semibold text-(--color-text-primary)">
                No friends yet
              </p>
              <p className="text-xs mt-1 max-w-xs text-center">
                Add friends by their Minecraft username to share modpacks
              </p>
            </div>
          </div>
        ) : (
          <div className="space-y-2">
            {acceptedFriends.map((friend) => (
              <FriendCard
                key={friend.id}
                friend={friend}
                onRemove={() => sharing.removeFriend(friend.id)}
              />
            ))}
            {pendingOutgoing.map((friend) => (
              <div
                key={friend.id}
                className="flex items-center gap-4 p-4 glass-subtle rounded-2xl opacity-60"
              >
                <div className="w-10 h-10 rounded-full bg-(--color-surface-tertiary) flex items-center justify-center text-sm font-bold text-(--color-text-secondary)">
                  {friend.profile.mc_username[0]?.toUpperCase()}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-semibold text-(--color-text-primary) truncate">
                    {friend.profile.mc_username}
                  </p>
                  <div className="flex items-center gap-1 mt-0.5">
                    <Clock size={10} className="text-amber-500" />
                    <p className="text-[11px] text-amber-500 font-medium">
                      Request pending
                    </p>
                  </div>
                </div>
                <button
                  onClick={() => sharing.removeFriend(friend.id)}
                  className="px-3 py-1.5 rounded-lg text-[11px] text-(--color-text-secondary) hover:text-red-500 hover:bg-red-500/10 transition-colors cursor-pointer"
                >
                  Cancel
                </button>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
});

function FriendCard({
  friend,
  onRemove,
}: {
  friend: Friend;
  onRemove: () => void;
}) {
  const [showRemove, setShowRemove] = useState(false);

  return (
    <div className="flex items-center gap-4 p-4 glass-subtle rounded-2xl group">
      <div className="w-10 h-10 rounded-full bg-(--color-accent)/10 flex items-center justify-center text-sm font-bold text-(--color-accent)">
        {friend.profile.mc_username[0]?.toUpperCase()}
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-sm font-semibold text-(--color-text-primary) truncate">
          {friend.profile.mc_username}
        </p>
        <div className="flex items-center gap-1 mt-0.5">
          <UserCheck size={10} className="text-green-500" />
          <p className="text-[11px] text-green-500 font-medium">Friends</p>
        </div>
      </div>
      {showRemove ? (
        <div className="flex items-center gap-2">
          <span className="text-[11px] text-red-500 font-semibold">Remove?</span>
          <button
            onClick={() => setShowRemove(false)}
            className="px-2 py-1 rounded-lg text-[11px] text-(--color-text-secondary) hover:bg-(--color-surface-tertiary) cursor-pointer"
          >
            No
          </button>
          <button
            onClick={onRemove}
            className="px-2 py-1 rounded-lg bg-red-500 text-white text-[11px] font-semibold hover:bg-red-600 cursor-pointer"
          >
            Yes
          </button>
        </div>
      ) : (
        <button
          onClick={() => setShowRemove(true)}
          className="w-8 h-8 rounded-lg flex items-center justify-center hover:bg-red-500/10 text-(--color-text-secondary) hover:text-red-500 transition-all cursor-pointer opacity-0 group-hover:opacity-100"
        >
          <Trash2 size={13} />
        </button>
      )}
    </div>
  );
}

function FriendRequestCard({
  friend,
  onAccept,
  onReject,
}: {
  friend: Friend;
  onAccept: () => void;
  onReject: () => void;
}) {
  return (
    <div className="flex items-center gap-4 p-4 rounded-2xl border border-(--color-accent)/20 bg-(--color-accent)/5">
      <div className="w-10 h-10 rounded-full bg-(--color-accent)/10 flex items-center justify-center text-sm font-bold text-(--color-accent)">
        {friend.profile.mc_username[0]?.toUpperCase()}
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-sm font-semibold text-(--color-text-primary) truncate">
          {friend.profile.mc_username}
        </p>
        <p className="text-[11px] text-(--color-text-secondary)">
          Wants to be your friend
        </p>
      </div>
      <div className="flex items-center gap-2">
        <button
          onClick={onAccept}
          className="flex items-center gap-1.5 px-4 py-2 rounded-xl bg-(--color-accent) text-white text-xs font-semibold hover:bg-(--color-accent-hover) transition-colors cursor-pointer"
        >
          <Check size={13} />
          Accept
        </button>
        <button
          onClick={onReject}
          className="w-8 h-8 rounded-xl flex items-center justify-center hover:bg-(--color-surface-tertiary) text-(--color-text-secondary) transition-colors cursor-pointer"
        >
          <X size={14} />
        </button>
      </div>
    </div>
  );
}
