package dev.voidpvp.client.cosmetic;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Turns what the client can see of a fight into {@link Reactive} events, once per client tick.
 * No network, nothing a vanilla client does not already know:
 *
 * <ul>
 *   <li><b>Hurt</b>: the rising edge of a player's {@code hurtTime} — the same edge that starts
 *       vanilla's red flash (and {@code HitTally}'s {@code taken}).</li>
 *   <li><b>Landed</b>: who caused that flash. The local player, if they attacked this entity in
 *       the last {@link #OWN_WINDOW_TICKS} (their own click, via {@link #ownAttack}); otherwise
 *       the remote player who started a swing animation within {@link Reaction#SWING_TICKS},
 *       within reach and facing the victim ({@link Reaction#attackScore}).</li>
 *   <li><b>Kill</b>: a player's death (health to zero or the death animation starting) within
 *       {@link #KILL_WINDOW_TICKS} of a credited hit.</li>
 *   <li><b>Health</b>: the local player's own, for the low-health dim. Other players' health is
 *       never used — a dimming cape on an opponent would be a health bar.</li>
 * </ul>
 *
 * Invisible and spectating players produce no events (they draw nothing to react with).
 */
public final class FightWatch {

    static final int OWN_WINDOW_TICKS = 14;
    static final int KILL_WINDOW_TICKS = 100;

    private static final class Track {
        String uuid;
        java.util.UUID uuidOf;
        int prevHurt;
        boolean wasSwinging;
        int prevSwingTicks;
        int swingTick = -1000;
        boolean dead;
        int attacker = Integer.MIN_VALUE;
        int attackedTick = -1000;
        int seenTick;
        float prevHealth = -1;
    }

    private static final Map<Integer, Track> TRACKS = new HashMap<Integer, Track>();
    private static final Map<Integer, Integer> OWN_ATTACKS = new HashMap<Integer, Integer>();
    private static int tick;

    private FightWatch() {
    }

    /** The local player attacked this entity (their click resolved onto it). Game thread. */
    public static void ownAttack(int entityId) {
        OWN_ATTACKS.put(Integer.valueOf(entityId), Integer.valueOf(tick));
    }

    /** End of the client tick. */
    static void tick(MinecraftClient mc) {
        tick++;
        if (mc.world == null) {
            if (!TRACKS.isEmpty()) {
                clear();
            }
            return;
        }
        // Pass 1: swings, so a swing and the flash it caused in the same tick line up.
        for (Object o : mc.world.playerEntities) {
            if (!(o instanceof PlayerEntity)) {
                continue;
            }
            PlayerEntity p = (PlayerEntity) o;
            Track t = track(p);
            boolean swinging = p.handSwinging;
            if (swinging && (!t.wasSwinging || p.handSwingTicks < t.prevSwingTicks)) {
                t.swingTick = tick;
            }
            t.wasSwinging = swinging;
            t.prevSwingTicks = p.handSwingTicks;
        }
        // Pass 2: hurt edges, who did it, deaths.
        for (Object o : mc.world.playerEntities) {
            if (!(o instanceof PlayerEntity)) {
                continue;
            }
            PlayerEntity p = (PlayerEntity) o;
            Track t = track(p);
            boolean self = p == mc.player;
            boolean hidden = p.isInvisible() || p.isSpectator();
            float health = p.getHealth();
            if (p.hurtTime > t.prevHurt && health > 0) {
                float strength = 1f;
                if (self && t.prevHealth > 0) {
                    // Heavier hits flinch harder: a crit or a sharpness sword reads as one.
                    strength = 0.7f + Math.max(0f, t.prevHealth - health) / 6f;
                }
                if (!hidden) {
                    Reactive.hurt(t.uuid, strength, self);
                }
                PlayerEntity by = attacker(mc, p);
                if (by != null) {
                    Track bt = track(by);
                    t.attacker = by.getEntityId();
                    t.attackedTick = tick;
                    if (!by.isInvisible() && !by.isSpectator()) {
                        Reactive.landed(bt.uuid, by == mc.player);
                    }
                }
            }
            t.prevHurt = p.hurtTime;
            boolean dead = health <= 0 || p.deathTime > 0;
            if (dead && !t.dead && t.attacker != Integer.MIN_VALUE && tick - t.attackedTick <= KILL_WINDOW_TICKS) {
                Track kt = TRACKS.get(Integer.valueOf(t.attacker));
                if (kt != null) {
                    Reactive.kill(kt.uuid, mc.player != null && t.attacker == mc.player.getEntityId());
                }
            }
            t.dead = dead;
            if (self) {
                float max = p.getMaxHealth();
                Reactive.health(t.uuid, max > 0 ? health / max : 1f);
            }
            t.prevHealth = health;
        }
        if (tick % 40 == 0) {
            sweep();
        }
    }

    /** Who most plausibly caused the hurt flash on {@code victim}; null if nobody visible did. */
    private static PlayerEntity attacker(MinecraftClient mc, PlayerEntity victim) {
        Integer own = OWN_ATTACKS.get(Integer.valueOf(victim.getEntityId()));
        if (mc.player != null && victim != mc.player && own != null && tick - own.intValue() <= OWN_WINDOW_TICKS) {
            return mc.player;
        }
        PlayerEntity best = null;
        float bestScore = 0f;
        for (Object o : mc.world.playerEntities) {
            if (!(o instanceof PlayerEntity) || o == victim) {
                continue;
            }
            PlayerEntity q = (PlayerEntity) o;
            if (q == mc.player || q.getHealth() <= 0) {
                // The local player's hits are their own clicks, never a guess from the animation.
                continue;
            }
            Track qt = TRACKS.get(Integer.valueOf(q.getEntityId()));
            if (qt == null) {
                continue;
            }
            float s = Reaction.attackScore(victim.x - q.x, (victim.y + 1) - (q.y + q.getEyeHeight()),
                    victim.z - q.z, q.headYaw, q.pitch, tick - qt.swingTick);
            if (s > bestScore) {
                bestScore = s;
                best = q;
            }
        }
        return best;
    }

    private static Track track(PlayerEntity p) {
        Integer key = Integer.valueOf(p.getEntityId());
        Track t = TRACKS.get(key);
        if (t == null || !p.getUuid().equals(t.uuidOf)) {
            t = new Track();
            t.uuidOf = p.getUuid();
            t.uuid = EquippedSet.normalise(String.valueOf(t.uuidOf));
            t.prevHurt = p.hurtTime;
            t.dead = p.getHealth() <= 0 || p.deathTime > 0;
            TRACKS.put(key, t);
        }
        t.seenTick = tick;
        return t;
    }

    private static void sweep() {
        for (Iterator<Track> it = TRACKS.values().iterator(); it.hasNext(); ) {
            if (tick - it.next().seenTick > 100) {
                it.remove();
            }
        }
        for (Iterator<Integer> it = OWN_ATTACKS.values().iterator(); it.hasNext(); ) {
            if (tick - it.next().intValue() > OWN_WINDOW_TICKS) {
                it.remove();
            }
        }
        Reactive.sweep();
    }

    static void clear() {
        TRACKS.clear();
        OWN_ATTACKS.clear();
        Reactive.clear();
    }
}
