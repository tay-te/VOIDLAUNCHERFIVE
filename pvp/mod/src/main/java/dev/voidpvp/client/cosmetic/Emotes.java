package dev.voidpvp.client.cosmetic;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Who is emoting right now. Local emotes start here (from {@code window.void.playEmote} or the
 * showcase) and are sent to the launcher, which broadcasts them on the server's realtime channel;
 * remote emotes come back as {@code {"t":"emote","uuid","id","at"}} and start here too.
 *
 * <p>Clips are sampled by wall clock, not by ticks, so an emote is as smooth as the frame rate.
 * {@link Active#weight} is the blend toward the clip: it eases in over the clip's {@code blend}
 * seconds, eases out at the end of a one-shot clip, and eases out from wherever it was when an
 * emote is cancelled (moving, attacking), so nothing ever snaps.</p>
 */
public final class Emotes {

    /** Told when the local player's emote starts or ends — the bridge's {@code emote} event. */
    public interface Listener {
        void onLocalEmote(String idOrNull);
    }

    /** One player's running emote. */
    public static final class Active {
        public final String id;
        public final EmoteClip clip;
        /** {@code System.nanoTime()} at the clip's t = 0. */
        final long startNanos;
        /** Set when cancelled: the time the fade-out began, or 0. */
        volatile long cancelNanos;
        /** Weight at the moment of cancel, so the fade starts from there. */
        volatile float cancelWeight;

        Active(String id, EmoteClip clip, long startNanos) {
            this.id = id;
            this.clip = clip;
            this.startNanos = startNanos;
        }

        public float time(long now) {
            return (now - startNanos) / 1e9f;
        }

        /** Blend weight toward the clip at {@code now}, in [0, 1]. */
        public float weight(long now) {
            float t = time(now);
            float blend = Math.max(0.05f, clip.blend);
            float w = Keyframes.smooth(t / blend);
            if (!clip.loop) {
                w = Math.min(w, Keyframes.smooth((clip.length - t) / blend));
            }
            if (cancelNanos != 0) {
                float out = (now - cancelNanos) / 1e9f;
                w = Math.min(w, cancelWeight * (1 - Keyframes.smooth(out / blend)));
            }
            return Math.max(0, Math.min(1, w));
        }

        /** True once nothing of the clip shows any more. */
        public boolean finished(long now) {
            if (cancelNanos != 0) {
                return (now - cancelNanos) / 1e9f >= Math.max(0.05f, clip.blend);
            }
            return !clip.loop && time(now) >= clip.length;
        }

        public boolean cancelled() {
            return cancelNanos != 0;
        }
    }

    private static final Emotes INSTANCE = new Emotes();

    public static Emotes get() {
        return INSTANCE;
    }

    private final Map<String, Active> active = new ConcurrentHashMap<String, Active>();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<Listener>();
    private volatile String selfUuid = "";
    private volatile String localId;

    Emotes() {
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void setSelfUuid(String uuid) {
        selfUuid = EquippedSet.normalise(uuid);
    }

    /**
     * Starts an emote for the local player and tells the launcher. Refused (false) for an id that
     * is not an emote, is not owned, or has no bundled clip.
     */
    public boolean playLocal(String id) {
        CosmeticCatalog.Item item = CosmeticCatalog.get(id);
        if (item == null || item.kind != CosmeticCatalog.Kind.EMOTE || !CosmeticState.get().owns(id)) {
            return false;
        }
        EmoteClip clip = CosmeticAssets.emote(id);
        if (clip == null) {
            return false;
        }
        active.put(selfUuid, new Active(id, clip, System.nanoTime()));
        localId = id;
        CosmeticState.get().sendEmote(id);
        notifyLocal(id);
        return true;
    }

    /**
     * Starts an emote another VOID player sent. {@code atMs} is the sender's wall clock; an emote
     * that is already older than its own length by the time it arrives is dropped, and a fresh one
     * starts where it should be now (capped, so clock skew cannot skip the whole clip).
     */
    public void startRemote(String uuid, String id, long atMs) {
        String u = EquippedSet.normalise(uuid);
        if (u.isEmpty() || u.equals(selfUuid)) {
            return;
        }
        CosmeticCatalog.Item item = CosmeticCatalog.get(id);
        if (item == null || item.kind != CosmeticCatalog.Kind.EMOTE) {
            return;
        }
        EmoteClip clip = CosmeticAssets.emote(id);
        if (clip == null) {
            return;
        }
        long late = atMs > 0 ? System.currentTimeMillis() - atMs : 0;
        if (late < 0 || late > 1500) {
            late = 0;
        }
        if (!clip.loop && late / 1000f > clip.length) {
            return;
        }
        active.put(u, new Active(id, clip, System.nanoTime() - late * 1_000_000L));
    }

    /** Starts an emote for any player directly, with no network side effects (showcase/dev). */
    public void startDirect(String uuid, String id, float offsetSeconds) {
        EmoteClip clip = CosmeticAssets.emote(id);
        if (clip != null) {
            active.put(EquippedSet.normalise(uuid), new Active(id, clip,
                    System.nanoTime() - (long) (offsetSeconds * 1e9)));
        }
    }

    /** The running emote for a uuid, or null. */
    public Active of(String uuid) {
        return uuid == null ? null : active.get(uuid);
    }

    /** The local player's emote id, or null. */
    public String localEmote() {
        return localId;
    }

    public Active local() {
        return active.get(selfUuid);
    }

    /** Fades a player's emote out from wherever it is. */
    public void cancel(String uuid) {
        Active a = uuid == null ? null : active.get(uuid);
        if (a != null && a.cancelNanos == 0) {
            long now = System.nanoTime();
            a.cancelWeight = a.weight(now);
            a.cancelNanos = now;
        }
    }

    public void cancelLocal() {
        cancel(selfUuid);
    }

    /** Drops finished clips; tells the page when the local one ended. Call once per tick. */
    public void sweep() {
        long now = System.nanoTime();
        for (Iterator<Map.Entry<String, Active>> it = active.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Active> e = it.next();
            if (e.getValue().finished(now)) {
                it.remove();
                if (e.getKey().equals(selfUuid) && localId != null) {
                    localId = null;
                    notifyLocal(null);
                }
            }
        }
        if (localId != null) {
            Active a = active.get(selfUuid);
            if (a == null || a.cancelled()) {
                if (a == null) {
                    localId = null;
                    notifyLocal(null);
                }
            }
        }
    }

    public void clear() {
        active.clear();
        if (localId != null) {
            localId = null;
            notifyLocal(null);
        }
    }

    private void notifyLocal(String id) {
        for (Listener l : listeners) {
            try {
                l.onLocalEmote(id);
            } catch (RuntimeException ignored) {
                // never let the page stop an emote
            }
        }
    }
}
