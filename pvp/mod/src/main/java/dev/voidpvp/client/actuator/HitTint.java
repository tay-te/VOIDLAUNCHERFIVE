package dev.voidpvp.client.actuator;

/**
 * The Hit colour actuator's arithmetic (§6.7): what the four floats of vanilla's hurt-overlay
 * constant colour become.
 *
 * <p><b>{@code intensity: 1} is 0.3, not 1, and that number is vanilla's.</b> Disassembling the
 * named 1.8.9 jar ({@code net.legacyfabric:yarn:1.8.9+build.604}, Loom's
 * {@code minecraft-merged-legacy-intermediary-1-v2} artifact) with {@code javap -p -c},
 * {@code LivingEntityRenderer.method_10252(LivingEntity,float,boolean)Z} — MCP's
 * {@code RendererLivingEntity.setBrightness} — fills a four-float buffer and hands it to
 * {@code GL11.glTexEnv(GL_TEXTURE_ENV, GL_TEXTURE_ENV_COLOR, buffer)}. On the hurt branch,
 * offsets 350-387, those four floats are literally:</p>
 *
 * <pre>
 *   buffer.put(1.0F);   // fconst_1   red
 *   buffer.put(0.0F);   // fconst_0   green
 *   buffer.put(0.0F);   // fconst_0   blue
 *   buffer.put(0.3F);   // ldc 0.3f   alpha  &lt;- the whole of vanilla's hurt-overlay strength
 * </pre>
 *
 * <p>The lightmap unit combines with {@code GLX.interpolate}, source0 {@code constant}, source1
 * {@code previous}, source2 {@code constant} operand {@code SRC_ALPHA} — that is
 * {@code result = constant.rgb * constant.a + previous.rgb * (1 - constant.a)}. So the alpha
 * <em>is</em> the mix, 0.3 is the whole of it, and {@link #alpha} multiplying a 0..1 fraction
 * into 0.3 is the definition {@code schema/mods/hit_color.json} insists on: 1 is exactly what
 * Minecraft already draws and there is nothing above it. A slider that resolved 1 to "fully
 * opaque" would be a mod that makes a landed hit more visible than the game made it, and the
 * `safe` classification would be wrong.</p>
 *
 * <p><b>{@code intensity} is the sole owner of alpha.</b> {@code #/definitions/hex_color} accepts
 * {@code #RRGGBBAA}, so a player can store an alpha byte in {@code color}; {@link #red},
 * {@link #green} and {@link #blue} take a packed value and read only the low three bytes, and
 * {@code LiveState} masks the byte off at the mirror so there is one place it is dropped rather
 * than three places that agree. Multiplying the two instead would give a player who drags
 * {@code intensity} to its top and sees nothing no way to know their colour ended in {@code 00}.
 * </p>
 */
public final class HitTint {

    /**
     * Vanilla's own hurt-overlay alpha, the {@code ldc 0.3f} above. The ceiling of this mod.
     */
    public static final float VANILLA_ALPHA = 0.3f;

    private HitTint() {
    }

    /**
     * The alpha to write, as {@code intensity} of vanilla's own.
     *
     * <p>Clamped to 0..1 first, so the ceiling holds for a loadout that arrived from outside the
     * schema as well as for one the settings page produced. {@code NaN} resolves to 0 — nothing
     * drawn — rather than to a value the texture environment would render as garbage.</p>
     */
    public static float alpha(float intensity) {
        if (Float.isNaN(intensity) || intensity <= 0f) {
            return 0f;
        }
        return (intensity >= 1f ? 1f : intensity) * VANILLA_ALPHA;
    }

    /** Red channel of a packed {@code 0xRRGGBB}, as GL wants it. The alpha byte is not read. */
    public static float red(int rgb) {
        return ((rgb >> 16) & 0xFF) / 255f;
    }

    public static float green(int rgb) {
        return ((rgb >> 8) & 0xFF) / 255f;
    }

    public static float blue(int rgb) {
        return (rgb & 0xFF) / 255f;
    }

    /**
     * Whether this entity's tint is this mod's to recolour.
     *
     * <p>{@code vanillaTinted} is vanilla's own condition — {@code hurtTime > 0 ||
     * deathTime > 0}, offsets 36-55 of the same method — and it is a precondition rather than a
     * setting: the mod recolours a flash the game drew and never draws one the game did not,
     * which is the difference between a recolour and a hit <em>marker</em> and is what
     * {@code schema/mods/hit_color.json} names as moving the mod to `grey`.</p>
     *
     * <p>{@code own_hits_only} sits inside that and is a scope filter and nothing else: the tint
     * on an entity somebody else hit is already on the player's screen at the same alpha for the
     * same ticks, so declining to recolour it hides nothing and reveals nothing.</p>
     */
    public static boolean tints(boolean on, boolean vanillaTinted, boolean ownHitsOnly,
                                boolean isOwnHit) {
        if (!on || !vanillaTinted) {
            return false;
        }
        return !ownHitsOnly || isOwnHit;
    }
}
