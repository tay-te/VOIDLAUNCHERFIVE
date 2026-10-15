package dev.voidpvp.client.visual;

/**
 * The {@code fog} mod's arithmetic: what each fog parameter vanilla just computed becomes.
 *
 * <p>Applied as a transform of vanilla's value rather than a replacement, so every vanilla
 * input that feeds a value — render distance, Respiration, water breathing, the nether's thick
 * fog — still moves it. Blindness never reaches here: the Mixin leaves the whole method alone
 * while the camera entity is blind.</p>
 */
public final class FogMath {

    /** Where "off" puts linear fog: far past any render distance. */
    static final float FAR = 1.0e6f;
    /** {@code reduced} distance fog starts this many blocks short of where it ends. */
    static final float REDUCED_BAND = 16f;
    /** {@code reduced} water and lava fog keep this fraction of vanilla's density. */
    static final float REDUCED_DENSITY = 0.25f;

    private FogMath() {
    }

    /** Linear distance fog start. */
    public static float start(int level, float vanillaStart, float vanillaEnd) {
        if (level == VisualConfig.FOG_OFF) {
            return FAR;
        }
        if (level == VisualConfig.FOG_REDUCED) {
            return Math.max(vanillaStart, vanillaEnd - REDUCED_BAND);
        }
        return vanillaStart;
    }

    /** Linear distance fog end. */
    public static float end(int level, float vanillaEnd) {
        return level == VisualConfig.FOG_OFF ? FAR : vanillaEnd;
    }

    /** Exponential water/lava fog density. */
    public static float density(int level, float vanilla) {
        if (level == VisualConfig.FOG_OFF) {
            return 0f;
        }
        return level == VisualConfig.FOG_REDUCED ? vanilla * REDUCED_DENSITY : vanilla;
    }
}
