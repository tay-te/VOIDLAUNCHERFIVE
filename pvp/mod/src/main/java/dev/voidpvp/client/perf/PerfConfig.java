package dev.voidpvp.client.perf;

import dev.voidpvp.client.state.Loadout;

/**
 * The mirrored settings of the three FPS-boost mods — {@code culling}, {@code particles} and
 * {@code frame_limiter} — read every frame by their Mixins.
 *
 * <p>Its own class rather than thirty more fields on {@code LiveState}, for the reason the HUD
 * widgets moved out of {@code widgets.tsx}: one roster wave should not be one more screen of a
 * file every wave edits. {@code LiveState.applyActuatorFields} calls {@link #apply} once and the
 * contract is otherwise identical — written under {@code LiveState}'s monitor on the UI or net
 * thread, read lock-free on the render thread and the culling thread, so every field is
 * {@code volatile}, and everything derivable is derived here rather than per frame (distances are
 * stored squared).</p>
 *
 * <p>The field defaults are the registry's factory defaults, so a frame drawn before the first
 * loadout lands behaves like the shipped mod.</p>
 */
public final class PerfConfig {

    private PerfConfig() {
    }

    // -- culling ----------------------------------------------------------------------------
    public static volatile boolean cullingOn = true;
    public static volatile boolean occludeEntities = true;
    public static volatile boolean occludeBlockEntities = true;
    public static volatile double alwaysDrawWithinSq = 4d * 4d;
    public static volatile boolean nametagsThroughWalls = true;
    public static volatile double blockEntityDistanceSq = 64d * 64d;
    public static volatile double armorStandDistanceSq = 64d * 64d;
    public static volatile double itemFrameDistanceSq = 64d * 64d;
    public static volatile double signTextDistanceSq = 32d * 32d;

    // -- particles --------------------------------------------------------------------------
    public static volatile boolean particlesOn = true;
    public static volatile boolean particleFrustumCull = true;
    public static volatile int particleCap = 4000;
    public static volatile double blockBreakParticles = 1;
    public static volatile double explosionParticles = 1;

    // -- frame_limiter ----------------------------------------------------------------------
    public static volatile boolean frameLimiterOn = true;
    public static volatile int unfocusedFps = 30;
    public static volatile int menuFps = 60;
    public static volatile boolean idleMinimized = true;

    /**
     * A test seam and the benchmark's switch: forces all three mods off regardless of the
     * loadout, so {@code bench/PerfBench} can measure the same scene with and without them in
     * one process. Never set by anything a player can reach.
     */
    public static volatile boolean benchForceOff;

    /** Writes every field from the loadout. Called from {@code LiveState.applyActuatorFields}. */
    public static void apply(Loadout l) {
        cullingOn = l.isOn("culling");
        occludeEntities = l.boolSetting("culling", "occlude_entities", true);
        occludeBlockEntities = l.boolSetting("culling", "occlude_block_entities", true);
        alwaysDrawWithinSq = sq(l.numberSetting("culling", "always_draw_within", 4));
        nametagsThroughWalls = l.boolSetting("culling", "nametags_through_walls", true);
        blockEntityDistanceSq = sq(l.numberSetting("culling", "block_entity_distance", 64));
        armorStandDistanceSq = sq(l.numberSetting("culling", "armor_stand_distance", 64));
        itemFrameDistanceSq = sq(l.numberSetting("culling", "item_frame_distance", 64));
        signTextDistanceSq = sq(l.numberSetting("culling", "sign_text_distance", 32));

        particlesOn = l.isOn("particles");
        particleFrustumCull = l.boolSetting("particles", "frustum_cull", true);
        particleCap = (int) l.numberSetting("particles", "particle_cap", 4000);
        blockBreakParticles = l.numberSetting("particles", "block_break_particles", 1);
        explosionParticles = l.numberSetting("particles", "explosion_particles", 1);

        frameLimiterOn = l.isOn("frame_limiter");
        unfocusedFps = (int) l.numberSetting("frame_limiter", "unfocused_fps", 30);
        menuFps = (int) l.numberSetting("frame_limiter", "menu_fps", 60);
        idleMinimized = l.boolSetting("frame_limiter", "idle_minimized", true);
    }

    /** {@code culling.on}, unless the benchmark has forced the boost off. */
    public static boolean culling() {
        return cullingOn && !benchForceOff;
    }

    /** {@code particles.on}, unless the benchmark has forced the boost off. */
    public static boolean particles() {
        return particlesOn && !benchForceOff;
    }

    /** {@code frame_limiter.on}, unless the benchmark has forced the boost off. */
    public static boolean frameLimiter() {
        return frameLimiterOn && !benchForceOff;
    }

    private static double sq(double v) {
        return v * v;
    }
}
