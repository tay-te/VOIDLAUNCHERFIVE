package dev.voidpvp.client.cosmetic;

/**
 * Per-player render state that has to persist between frames: the cape's cloth, the springs that
 * drive wings and floating items, the trail emitter's carry, and when this player was last drawn.
 * Created on first sight, dropped after {@link CosmeticRenderer#FORGET_NANOS} unseen.
 */
final class PlayerFx {

    final int entityId;
    /** Phase offset in seconds, so two players' halos do not bob in lockstep. */
    final float phase;
    long lastNanos;
    long seenNanos;

    // ---- cape
    Cloth cloth;
    String clothFor;
    boolean clothHi;
    float clothCarry;
    int clothFrame;
    double[] ax = new double[CapeRenderer.HI_COLS + 1];
    double[] ay = new double[CapeRenderer.HI_COLS + 1];
    double[] az = new double[CapeRenderer.HI_COLS + 1];

    // ---- wings: 0 folded .. 1 spread; sweep back 0..1; flap amplitude; flap phase (radians)
    final Spring spread = new Spring(5.5f, 0.15f);
    final Spring sweep = new Spring(5.0f, 0.3f);
    final Spring flapAmp = new Spring(4.0f, 0.03f);
    final Spring tipFold = new Spring(6.0f, 0.4f);
    final Spring lean = new Spring(4.0f, 0f);
    float flapPhase;
    float flapRate = 0.45f;

    // ---- motion estimate, world units per second, smoothed
    double lastX, lastY, lastZ;
    boolean hasLast;
    final Spring speed = new Spring(8f, 0f);
    final Spring vy = new Spring(10f, 0f);

    // ---- secondary motion and fight reactions (see Secondary, Reaction)
    final Secondary sec = new Secondary();
    /** This player's reactions this frame, or null when nothing has happened to them. */
    Reaction react;

    // ---- trails
    float trailCarry;
    double trailLastX, trailLastY, trailLastZ;
    boolean trailHasLast;
    int stairStep;

    // ---- poses, one per slot model, reused
    CosmeticModel.Pose[] poses = new CosmeticModel.Pose[5];

    /** The player's uuid, as the entity has it and as the cosmetic state keys it. */
    final java.util.UUID uuidOf;
    final String uuid;

    PlayerFx(int entityId, java.util.UUID uuid) {
        this.entityId = entityId;
        this.uuidOf = uuid;
        this.uuid = EquippedSet.normalise(String.valueOf(uuid));
        this.phase = Math.abs(this.uuid.hashCode() % 1000) / 97f;
    }

    CosmeticModel.Pose pose(int slot, CosmeticModel m) {
        CosmeticModel.Pose p = poses[slot];
        if (p == null || p.rot.length != m.bones.length * 3) {
            p = new CosmeticModel.Pose(m.bones.length);
            poses[slot] = p;
        }
        p.reset();
        return p;
    }
}
