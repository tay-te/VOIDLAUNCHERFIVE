package dev.voidpvp.client.sensor;

/**
 * Which entities the local player has recently swung at — the whole of {@code hit_color}'s
 * {@code own_hits_only} (§6.6).
 *
 * <p>Plain, like {@link HitTally} and {@link KeyStateTracker}, and for the same reason: the
 * Mixin that feeds it and the Mixin that reads it cannot be unit-tested, and the part that can
 * be wrong is the window.</p>
 *
 * <p><b>Why a window and not a fact.</b> 1.8.9's client is never told who damaged whom. The
 * hurt flash it draws comes from {@code LivingEntity.hurtTime}, which is set by the server's
 * status update and not by the swing — {@code HitTally}'s own comment records the same limit
 * from the other side. So "my hit" is the closest thing an unprivileged client can say: this
 * player swung at this entity recently enough that the flash now on screen is very probably
 * theirs. {@link #WINDOW_MS} is vanilla's own {@code maxHurtTime} of 10 ticks (500 ms, the
 * length of the flash itself) plus a second of room for the round trip that started it, which is
 * generous on purpose — the failure this filter has is recolouring one flash too many, and the
 * failure it must not have is dropping the player's own.</p>
 *
 * <p>A fixed ring rather than a map: this is written on the game thread from
 * {@code MinecraftClient.doAttack} and read on the same thread once per rendered entity per
 * frame, and a structure that allocates on either path is a structure in the frame budget. Thirty-
 * two slots is far more than the entities one player can swing at inside the window, and an
 * overrun drops the oldest, which is the one whose flash has already ended.</p>
 */
public final class OwnHits {

    /** Vanilla's 10-tick hurt animation plus a second of round trip. */
    public static final long WINDOW_MS = 1500L;

    private static final int CAPACITY = 32;

    private final int[] ids = new int[CAPACITY];
    private final long[] at = new long[CAPACITY];
    private int next;

    /** One swing that vanilla resolved onto an entity. */
    public void swungAt(int entityId, long nowMs) {
        for (int i = 0; i < CAPACITY; i++) {
            if (at[i] != 0L && ids[i] == entityId) {
                at[i] = nowMs;
                return;
            }
        }
        ids[next] = entityId;
        at[next] = nowMs;
        next = (next + 1) % CAPACITY;
    }

    /** Whether the flash on this entity is plausibly one this player caused. */
    public boolean isOwn(int entityId, long nowMs) {
        for (int i = 0; i < CAPACITY; i++) {
            if (at[i] != 0L && ids[i] == entityId) {
                return nowMs - at[i] <= WINDOW_MS;
            }
        }
        return false;
    }

    /** Forgets everything — a world change, where entity ids are reissued to other entities. */
    public void clear() {
        for (int i = 0; i < CAPACITY; i++) {
            ids[i] = 0;
            at[i] = 0L;
        }
        next = 0;
    }
}
