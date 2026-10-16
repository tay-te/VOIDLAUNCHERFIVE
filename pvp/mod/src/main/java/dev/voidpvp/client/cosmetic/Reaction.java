package dev.voidpvp.client.cosmetic;

/**
 * One player's fight reactions, as curves over time: what their cosmetics are feeling right now.
 * Pure (no Minecraft), so the timing a player sees is the timing a test holds.
 *
 * <p>Events come in from {@link FightWatch} (or a showcase script) and are only timestamps and a
 * combo count; everything drawn is a function of time since them:</p>
 * <ul>
 *   <li>{@link #flinch} — being hit: a fast spike (30 ms up) that falls away in about a quarter of
 *       a second. Capes whip, wings flinch inward, halos wobble.</li>
 *   <li>{@link #flare} — a combo building: a level that eases up a step per landed hit (six hits to
 *       full) and eases back down once the combo lapses ({@link #COMBO_WINDOW} without a hit, or
 *       a hit taken). Wings spread, trails thicken, everything glows a little brighter.</li>
 *   <li>{@link #step} — each landed hit's own short bright tick, bigger the longer the combo.</li>
 *   <li>{@link #flourish} — a kill: a pop that holds briefly and fades over about a second; a
 *       streak (kills inside {@link #STREAK_WINDOW}) plays it at full strength.</li>
 *   <li>{@link #dim} — low health: eases in under a third of a heart bar, to at most 30%.</li>
 * </ul>
 *
 * <p>All of it is scaled by the {@code cosmetics.reactive} level ({@code off} 0, {@code subtle}
 * {@link #SUBTLE}, {@code full} 1), so {@code off} leaves every curve at zero and every intensity
 * at exactly one.</p>
 */
final class Reaction {

    /** Seconds without a landed hit before a combo lapses: the combo HUD's default reset_ms. */
    static final float COMBO_WINDOW = 3.0f;
    /** Hits to a full flare. */
    static final int COMBO_FULL = 6;
    /** Seconds between kills that still count as a streak. */
    static final float STREAK_WINDOW = 20f;
    /** The {@code subtle} level's scale on every reaction. */
    static final float SUBTLE = 0.45f;

    // Slots, as the intensity API names them.
    static final int CAPE = 0, BACK = 1, HEAD = 2, FACE = 3, HALO = 4, TRAIL = 5, BADGE = 6;

    private static final float NEVER = -1e6f;

    float hurtAt = NEVER, hitAt = NEVER, killAt = NEVER, comboAt = NEVER;
    float hurtStrength = 1f, killStrength = 1f;
    int combo;
    int streak;
    /** Bumped per event, so the renderer can play one-shot effects (a cape kick) exactly once. */
    int hurtSerial, hitSerial, killSerial;
    private float healthFrac = 1f;
    private final Spring flareLevel = new Spring(7f, 0f);
    private final Spring dimLevel = new Spring(2.5f, 0f);
    private float updatedAt = NEVER;
    /** Last real time this player was touched, for sweeping. */
    long touchedNanos;

    // ---- outputs, valid after update()
    float flinch, flare, step, flourish, dim;
    /** The level last update ran at. */
    float level = 1f;

    // ------------------------------------------------------------------- events

    void hurt(float now, float strength) {
        hurtAt = now;
        hurtStrength = Math.max(0.3f, Math.min(1.3f, strength));
        combo = 0;
        hurtSerial++;
    }

    /** A hit this player landed; returns the combo it makes. */
    int landed(float now) {
        if (now - comboAt > COMBO_WINDOW) {
            combo = 0;
        }
        combo++;
        comboAt = now;
        hitAt = now;
        hitSerial++;
        return combo;
    }

    /** A kill this player made; returns the streak it makes. */
    int kill(float now) {
        streak = now - killAt <= STREAK_WINDOW ? streak + 1 : 1;
        killAt = now;
        killStrength = streak > 1 ? 1f : 0.8f;
        killSerial++;
        return streak;
    }

    void health(float fraction) {
        healthFrac = Math.max(0f, Math.min(1f, fraction));
    }

    /** The combo as it stands at {@code now}: zero once it has lapsed. */
    int comboAt(float now) {
        return now - comboAt <= COMBO_WINDOW ? combo : 0;
    }

    // ------------------------------------------------------------------- curves

    /**
     * A one-shot envelope: zero before the event, up to one over {@code attack} seconds on a
     * smoothstep, then an exponential fall with time constant {@code decay}.
     */
    static float pulse(float t, float attack, float decay) {
        if (t < 0) {
            return 0f;
        }
        if (t < attack) {
            float u = t / attack;
            return u * u * (3 - 2 * u);
        }
        float d = (t - attack) / decay;
        return d > 12 ? 0f : (float) Math.exp(-d);
    }

    /** The flourish: a pop to one, held a beat, then the exponential tail. */
    static float flourishCurve(float t) {
        if (t < 0) {
            return 0f;
        }
        if (t < 0.08f) {
            float u = t / 0.08f;
            return u * u * (3 - 2 * u);
        }
        if (t < 0.25f) {
            return 1f;
        }
        float d = (t - 0.25f) / 0.45f;
        return d > 12 ? 0f : (float) Math.exp(-d);
    }

    /** How far under the low-health line the player is, as the dim target (0 .. 0.3). */
    static float dimTarget(float healthFrac) {
        float line = 0.35f;
        if (healthFrac >= line) {
            return 0f;
        }
        return 0.3f * (line - healthFrac) / line;
    }

    /** Recomputes the outputs for {@code now} (seconds). Idempotent within a frame. */
    void update(float now, float lvl) {
        float dt = updatedAt == NEVER ? 0f : now - updatedAt;
        if (dt < 0) {
            dt = 0;
        }
        if (dt == 0 && updatedAt != NEVER && lvl == level) {
            return;
        }
        updatedAt = now;
        level = lvl;
        int c = comboAt(now);
        float target = Math.min(1f, c / (float) COMBO_FULL);
        flareLevel.step(target, dt);
        dimLevel.step(dimTarget(healthFrac), dt);
        flinch = lvl * hurtStrength * pulse(now - hurtAt, 0.03f, 0.22f);
        flare = lvl * Math.max(0f, flareLevel.value);
        float combo01 = Math.min(1f, combo / (float) COMBO_FULL);
        step = lvl * pulse(now - hitAt, 0.025f, 0.16f) * (0.55f + 0.45f * combo01);
        flourish = lvl * killStrength * flourishCurve(now - killAt);
        dim = lvl * dimLevel.value;
    }

    /**
     * The emissive/bloom multiplier for one slot: 1 at rest, under 1 when dimmed by low health,
     * over 1 while a combo, a hit or a kill lights it (capped at 2.5). See {@link Reactive}.
     */
    float intensity(int slot) {
        float up;
        switch (slot) {
            case HALO:
                up = 0.6f * flare + 0.7f * step + 1.1f * flourish - 0.35f * flinch;
                break;
            case BACK:
                up = 0.6f * flare + 0.35f * step + 0.9f * flourish - 0.2f * flinch;
                break;
            case CAPE:
                up = 0.35f * flare + 0.25f * step + 0.9f * flourish;
                break;
            case TRAIL:
                up = 0.8f * flare + 0.3f * step + 0.7f * flourish;
                break;
            case BADGE:
                up = 0.2f * flare + 0.6f * flourish;
                break;
            case HEAD:
            case FACE:
            default:
                up = 0.3f * flare + 0.3f * step + 0.6f * flourish;
                break;
        }
        float k = (1f + up) * (1f - dim);
        return Math.max(0f, Math.min(2.5f, k));
    }

    static int slotIndex(String slot) {
        if (slot == null) {
            return -1;
        }
        switch (slot) {
            case "cape": return CAPE;
            case "back": return BACK;
            case "head": return HEAD;
            case "face": return FACE;
            case "halo": return HALO;
            case "trail": return TRAIL;
            case "badge": return BADGE;
            default: return -1;
        }
    }

    // --------------------------------------------------------------- attribution

    /**
     * How plausibly a player who swung {@code swingAge} ticks ago, at ({@code dx}, {@code dy},
     * {@code dz}) blocks from where they stand to the victim, looking along yaw/pitch (degrees,
     * Minecraft's convention), landed the hit the victim just took. Negative means "not them":
     * further than {@link #REACH}, a swing older than {@link #SWING_TICKS}, or facing away.
     * Only facts every client sees — positions, look direction, the swing animation — so this
     * credits remote players with nothing a vanilla client could not have worked out.
     */
    static float attackScore(double dx, double dy, double dz, float yawDeg, float pitchDeg, int swingAge) {
        if (swingAge < 0 || swingAge > SWING_TICKS) {
            return -1f;
        }
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d > REACH || d < 1e-6) {
            return d < 1e-6 ? 0.1f : -1f;
        }
        double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
        // Minecraft's look vector: yaw 0 faces +z, yaw 90 faces -x; pitch down is positive.
        double lx = -Math.sin(yaw) * Math.cos(pitch), lz = Math.cos(yaw) * Math.cos(pitch);
        // Horizontal facing matters most; a jump-crit looks down at the victim's head.
        double hd = Math.sqrt(dx * dx + dz * dz);
        double hl = Math.sqrt(lx * lx + lz * lz);
        // Straight up or down, or standing on top of the victim: the yaw says nothing.
        double facing = hd < 0.3 || hl < 1e-3 ? 1 : (dx * lx + dz * lz) / (hd * hl);
        if (facing < 0.55) {
            return -1f;
        }
        return (float) (facing - 0.08 * d - 0.04 * swingAge);
    }

    static final double REACH = 4.5;
    static final int SWING_TICKS = 8;
}
