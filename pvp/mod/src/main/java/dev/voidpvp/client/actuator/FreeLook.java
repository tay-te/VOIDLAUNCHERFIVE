package dev.voidpvp.client.actuator;

/**
 * The Freelook actuator's state machine and its camera angles (§6.7) — the half of the mod that
 * can be wrong and can therefore be tested. The Mixin does nothing but ask this class two
 * questions: "are you engaged?" and "what angle?".
 *
 * <p><b>The camera cannot leave the pivot, and that is a property of what this class does not
 * have rather than of a check it performs.</b> There is no position here, no distance, no offset
 * and no way to express one: the only values it produces are a yaw, a pitch, and one of
 * vanilla's own three {@code GameOptions.perspective} values. Everything about where the eye
 * sits is left to {@code GameRenderer.transformCamera}, which computes it from the player's own
 * interpolated position and its own {@code thirdPersonDistance} — neither of which this mod
 * reads or writes. So the eye is always at one of the three places vanilla itself would put it
 * for a player standing exactly where this player is standing. The `$comment` at the top of
 * {@code schema/mods/freelook.json} is the argument for why that is the line; this class is the
 * shape that makes crossing it require new code rather than a new number.</p>
 *
 * <p><b>The turn arithmetic is vanilla's, copied from the bytecode rather than approximated.</b>
 * {@code Entity.increaseTransforms(FF)V} in the named 1.8.9 jar
 * ({@code net.legacyfabric:yarn:1.8.9+build.604}) is, in full:</p>
 *
 * <pre>
 *   float p0 = pitch, y0 = yaw;
 *   yaw   = (float)((double)yaw   + (double)dx * 0.15D);
 *   pitch = (float)((double)pitch - (double)dy * 0.15D);
 *   pitch = MathHelper.clamp(pitch, -90.0F, 90.0F);
 *   prevPitch += pitch - p0;
 *   prevYaw   += yaw   - y0;
 * </pre>
 *
 * <p>{@link #turnYaw} and {@link #turnPitch} are the first three lines, in the same order and at
 * the same precision — the multiply is in {@code double} and the store back is a {@code float}
 * cast, so a freelook sweep and a vanilla sweep of the same mouse travel land on the same angle
 * and the mod cannot feel like a different sensitivity. The last two lines have no counterpart
 * here on purpose: they exist so vanilla's <em>interpolated</em> rotation does not smear over the
 * tick, and this camera has no previous value to smear from — the Mixin answers {@code prevYaw}
 * and {@code yaw} with the same number, which is what "no interpolation" looks like when the
 * value is already a per-frame one.</p>
 */
public final class FreeLook {

    /** Vanilla's pitch limit, from the {@code MathHelper.clamp(pitch, -90.0F, 90.0F)} above. */
    public static final float PITCH_LIMIT = 90f;

    /** Vanilla's mouse-to-degrees scale, from the two {@code ldc2_w 0.15d} in the same method. */
    private static final double TURN_SCALE = 0.15D;

    private boolean engaged;
    private boolean keyWasDown;
    private boolean justReleased;
    private float camYaw;
    private float camPitch;

    /**
     * The perspective the mod forces while it is engaged, as one of vanilla's own three values.
     *
     * <p>{@code GameOptions.perspective} is an {@code int} that
     * {@code GameRenderer.transformCamera} tests three times: {@code perspective <= 0} takes the
     * first-person arm (a {@code translate(0, 0, -0.1F)} and nothing else), {@code > 0} takes the
     * third-person arm, and {@code == 2} flips it to the front. So 0, 1 and 2 are the complete
     * set, and they are exactly what vanilla's own F5 key cycles.</p>
     *
     * <p><b>{@code free} is 0, and that is the whole of what the word is allowed to mean.</b>
     * At 0 the camera is the pivot: vanilla applies no third-person translation at all, so the
     * eye sits where the first-person eye sits and only the <em>angle</em> comes from this class.
     * That is the orbit {@code schema/mods/freelook.json} pins — zero translation, the eye never
     * leaves the pivot. {@code third_back} (1) and {@code third_front} (2) are vanilla's two F5
     * offsets, at vanilla's own distance, block-clamped by vanilla's own eight ray traces. There
     * is no fourth value and no way for this method to produce one: anything it does not
     * recognise falls to 0, which is the arm with no translation in it.</p>
     */
    public static int perspectiveFor(String perspective) {
        if ("third_back".equals(perspective)) {
            return 1;
        }
        if ("third_front".equals(perspective)) {
            return 2;
        }
        return 0;
    }

    /**
     * Advances one frame from the driving key.
     *
     * <p>Sampled once per frame from {@code GameRenderer.render}'s own mouse block — the one
     * place per frame where the game is applying mouse look, with a world loaded, no screen open
     * and the mouse grabbed. That is why {@code canLook} exists: every other path into this class
     * is a <em>release</em> and goes through {@link #forceRelease()}, so the key is sampled by
     * exactly one caller and a toggle cannot be flipped twice by one press.</p>
     *
     * @param enabled     {@code freelook.on}, and the keybind is not {@code NONE}
     * @param holdMode    {@code mode} is {@code hold} rather than {@code toggle}
     * @param keyDown     the bound key is physically down this frame
     * @param canLook     the player exists and the game is applying mouse look
     * @param playerYaw   the player's own yaw, captured on the engaging frame
     * @param playerPitch the player's own pitch, likewise
     * @return whether freelook is engaged after this frame
     */
    public boolean update(boolean enabled, boolean holdMode, boolean keyDown, boolean canLook,
                          float playerYaw, float playerPitch) {
        if (!enabled || !canLook) {
            justReleased = engaged;
            engaged = false;
            keyWasDown = keyDown;
            return false;
        }
        boolean rising = keyDown && !keyWasDown;
        keyWasDown = keyDown;
        boolean want = holdMode ? keyDown : (rising != engaged);
        if (want && !engaged) {
            // The camera starts exactly where the player is already looking, so engaging is
            // invisible until the mouse moves. Nothing else is captured: there is no position
            // to capture.
            camYaw = playerYaw;
            camPitch = playerPitch;
        }
        justReleased = engaged && !want;
        engaged = want;
        return engaged;
    }

    /**
     * Ends freelook from a path that must not look at the key.
     *
     * <p>The game loop's own release — a screen opening, the mod being switched off, a loadout
     * switch, the world going away. It cannot sample the key, because {@link #update} already
     * owns the edge and a second sampler is a toggle that fires twice on one press
     * ({@code toggle_sneak}'s "two owners of one latch", one noun over).</p>
     *
     * @return true if this actually ended an engaged freelook
     */
    public boolean forceRelease() {
        boolean was = engaged;
        engaged = false;
        keyWasDown = false;
        justReleased = was;
        return was;
    }

    /** Applies one frame of mouse travel to the camera, and only to the camera. */
    public void look(float dx, float dy) {
        camYaw = turnYaw(camYaw, dx);
        camPitch = turnPitch(camPitch, dy);
    }

    /** Vanilla's yaw step, at vanilla's precision. See the class comment. */
    public static float turnYaw(float yaw, float dx) {
        return (float) ((double) yaw + (double) dx * TURN_SCALE);
    }

    /** Vanilla's pitch step and vanilla's clamp, in that order. */
    public static float turnPitch(float pitch, float dy) {
        float next = (float) ((double) pitch - (double) dy * TURN_SCALE);
        if (next < -PITCH_LIMIT) {
            return -PITCH_LIMIT;
        }
        return next > PITCH_LIMIT ? PITCH_LIMIT : next;
    }

    public boolean isEngaged() {
        return engaged;
    }

    /** True on the frame freelook ended, so the caller can apply {@code snap_back}. */
    public boolean justReleased() {
        return justReleased;
    }

    public float yaw() {
        return camYaw;
    }

    public float pitch() {
        return camPitch;
    }
}
