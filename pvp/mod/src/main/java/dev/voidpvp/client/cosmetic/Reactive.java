package dev.voidpvp.client.cosmetic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Reactive cosmetics: the public face of the fight reactions, for the renderer here and for
 * anything else that wants its light to answer the fight (the bloom pass reads
 * {@link #intensity}; a sound or a HUD could listen for {@link Pulse}s).
 *
 * <h2>Intensity</h2>
 * {@link #intensity(String, String)} is a per-player, per-slot multiplier on emissive light:
 * exactly {@code 1} at rest (and always, with {@code cosmetics.reactive} off), below one when the
 * local player is low on health (down to 0.7), above one while a combo builds, a hit lands or a
 * kill flourishes (up to 2.5). Multiply emissive texels and bloom strength by it. It is smooth in
 * time — a curve, never a step — so it can drive a bloom radius directly. Render thread only.
 *
 * <h2>Pulses</h2>
 * {@link #addListener} hears one {@link Pulse} per fight event the moment it is detected (on the
 * client tick): {@link Kind#HURT} (this player was hit), {@link Kind#HIT} (landed a hit;
 * {@code combo} is the count it makes), {@link Kind#KILL} ({@code combo} is the streak). The
 * curves behind {@link #intensity} already include every pulse; a listener is for one-shot
 * effects (a flash, a ring) that want the edge rather than the level.
 *
 * <p>What it may never be: a hit indicator. Every event is derived from what any vanilla client
 * already shows — the red hurt flash ({@code hurtTime}), the swing animation, death — or, for the
 * local player, from the player's own clicks. It changes only how cosmetics look.</p>
 */
public final class Reactive {

    /** What happened. */
    public enum Kind { HURT, HIT, KILL }

    /** One fight event on one player. */
    public static final class Pulse {
        /** Normalised (lower-case, dashed) uuid of the player whose cosmetics react. */
        public final String uuid;
        public final Kind kind;
        /** HIT: the combo this hit makes. KILL: the streak. HURT: 0. */
        public final int count;
        /** 0..1, how big the reaction is (already scaled by the reactive level). */
        public final float strength;
        public final boolean self;

        Pulse(String uuid, Kind kind, int count, float strength, boolean self) {
            this.uuid = uuid;
            this.kind = kind;
            this.count = count;
            this.strength = strength;
            this.self = self;
        }
    }

    /** Hears every {@link Pulse}. Called on the client thread; keep it quick. */
    public interface Listener {
        void onPulse(Pulse pulse);
    }

    private static final Map<String, Reaction> REACTIONS = new HashMap<String, Reaction>();
    private static final List<Listener> LISTENERS = new ArrayList<Listener>();
    private static final long EPOCH = System.nanoTime();
    private static final long FORGET_NANOS = 30_000_000_000L;
    /** Test/showcase override of the level; negative means "read the setting". */
    static float levelOverride = -1f;

    private Reactive() {
    }

    // ------------------------------------------------------------------- the API

    /**
     * The emissive multiplier for {@code slot} ({@code cape}, {@code back}, {@code head},
     * {@code face}, {@code halo}, {@code trail}, {@code badge}) on the player with this uuid.
     * 1 for an unknown player or slot.
     */
    public static float intensity(String uuid, String slot) {
        int s = Reaction.slotIndex(slot);
        Reaction r = uuid == null ? null : REACTIONS.get(uuid);
        if (s < 0 || r == null) {
            return 1f;
        }
        r.update(now(), level());
        return r.intensity(s);
    }

    public static float intensity(java.util.UUID uuid, String slot) {
        return uuid == null ? 1f : intensity(EquippedSet.normalise(uuid.toString()), slot);
    }

    public static void addListener(Listener l) {
        if (l != null && !LISTENERS.contains(l)) {
            LISTENERS.add(l);
        }
    }

    public static void removeListener(Listener l) {
        LISTENERS.remove(l);
    }

    /** The {@code cosmetics.reactive} level: 0 off, {@link Reaction#SUBTLE} subtle, 1 full. */
    public static float level() {
        if (levelOverride >= 0) {
            return levelOverride;
        }
        dev.voidpvp.client.state.LiveState s = dev.voidpvp.client.state.LiveState.get();
        return s == null ? 1f : s.cosmeticsReactive;
    }

    /**
     * Dev only (the showcase's A/B takes): turn secondary motion off, and pin the reactive level
     * ({@code < 0} returns it to the setting).
     */
    public static void devMotion(boolean secondary, float reactiveLevel) {
        Secondary.enabled = secondary;
        levelOverride = reactiveLevel;
    }

    // ---------------------------------------------------------------- internals

    static float now() {
        return (System.nanoTime() - EPOCH) / 1e9f;
    }

    /** This player's reactions, created on first use. */
    static Reaction of(String uuid) {
        Reaction r = REACTIONS.get(uuid);
        if (r == null) {
            r = new Reaction();
            REACTIONS.put(uuid, r);
        }
        r.touchedNanos = System.nanoTime();
        return r;
    }

    /** Without creating one: null for a player nothing has happened to. */
    static Reaction peek(String uuid) {
        return REACTIONS.get(uuid);
    }

    static void hurt(String uuid, float strength, boolean self) {
        float lvl = level();
        if (lvl <= 0) {
            return;
        }
        of(uuid).hurt(now(), strength);
        fire(new Pulse(uuid, Kind.HURT, 0, Math.min(1f, strength) * lvl, self));
    }

    static void landed(String uuid, boolean self) {
        float lvl = level();
        if (lvl <= 0) {
            return;
        }
        int combo = of(uuid).landed(now());
        fire(new Pulse(uuid, Kind.HIT, combo, Math.min(1f, combo / (float) Reaction.COMBO_FULL) * lvl, self));
    }

    static void kill(String uuid, boolean self) {
        float lvl = level();
        if (lvl <= 0) {
            return;
        }
        int streak = of(uuid).kill(now());
        fire(new Pulse(uuid, Kind.KILL, streak, (streak > 1 ? 1f : 0.8f) * lvl, self));
    }

    static void health(String uuid, float fraction) {
        Reaction r = fraction < 0.999f ? of(uuid) : peek(uuid);
        if (r != null) {
            r.health(fraction);
        }
    }

    private static void fire(Pulse p) {
        for (int i = 0; i < LISTENERS.size(); i++) {
            try {
                LISTENERS.get(i).onPulse(p);
            } catch (RuntimeException e) {
                dev.voidpvp.client.VoidLog.warn("cosmetics: a pulse listener threw: " + e);
            }
        }
    }

    /** Drops players nothing has happened to lately. Once a second is plenty. */
    static void sweep() {
        long now = System.nanoTime();
        for (Iterator<Reaction> it = REACTIONS.values().iterator(); it.hasNext(); ) {
            if (now - it.next().touchedNanos > FORGET_NANOS) {
                it.remove();
            }
        }
    }

    static void clear() {
        REACTIONS.clear();
    }
}
