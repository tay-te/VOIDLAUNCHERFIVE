package dev.voidpvp.client.sensor;

import java.util.List;

/**
 * One tick's readings, as an object rather than an argument list.
 *
 * <p><b>Why this exists.</b> {@link TickCoalescer#build} took eight positional parameters —
 * {@code (fps, ping, x, y, z, yaw, armor, fx)} — and the five fields Wave 2 needs would have
 * made it thirteen. Thirteen positional arguments, six of them numbers, is a call site where
 * transposing two is a silent wrong reading rather than a compile error: {@code speed} and
 * {@code saturation} are both bare numbers in the same range, and nothing would have caught
 * them swapped.</p>
 *
 * <p>That is the per-mod tax showing up in a new place. `docs/mod-roster.md` §9 is about tables
 * keyed by mod id, but a parameter list grows for exactly the same reason and fails the same
 * way, so it gets the same treatment: adding a sensor reading is now one field here, one
 * coalescing block in {@link TickCoalescer}, and one setter at the call site — each named.</p>
 *
 * <p><b>Mutable and reused.</b> {@code VoidClient} keeps one of these and refills it every tick.
 * A fresh object twenty times a second would be twenty short-lived allocations per second on the
 * render thread, which is the sort of thing this file's neighbour spends paragraphs avoiding.
 * It is never handed outside the sensor package and never retained past the {@code build} call,
 * so the reuse is invisible.</p>
 *
 * <p><b>Every reading is optional.</b> A sensor that cannot read something leaves it unset, and
 * the coalescer omits the field entirely rather than sending a zero — {@code bridge.json} says
 * an absent field means unchanged, and a plausible wrong number is worse than a gap. That is why
 * the boxed types are nullable rather than primitives with a sentinel: {@code -1} means "no
 * server" for {@code ping} because the wire says so, but there is no sensible sentinel for a
 * saturation or a stack size.</p>
 */
public final class TickInput {

    /** {@code Minecraft.currentFps}. */
    public int fps;

    /** Own {@code PlayerListEntry.getLatency}, or -1 when there is no server. */
    public int ping;

    /** Player position. */
    public double x;
    public double y;
    public double z;

    /** Facing, in degrees, unnormalised — {@link TickCoalescer} normalises. */
    public float yaw;

    /** Worn armor plus, when the mod asks for it, the held item. Null to omit. */
    public List<ArmorSlot> armor;

    /** Active potion effects. Null to omit. */
    public List<PotionFx> fx;

    /** Food saturation, 0-20. Null when unreadable. */
    public Double saturation;

    /** Stack size of the held item; null for an empty hand, which is not a count of zero. */
    public Integer heldCount;

    /** Registry name of the held item; null on an empty hand, with {@link #heldCount}. */
    public String heldItem;

    /** Horizontal ground speed, blocks/sec. Null when unreadable. */
    public Double speed;

    /** Heap in use, mebibytes. Null to omit the whole `memory` object. */
    public Integer memoryUsedMb;

    /** Heap ceiling, mebibytes. */
    public Integer memoryMaxMb;

    /** Attacks landed this session, monotonic. Null to omit the whole `hits` object. */
    public Integer hitsDealt;

    /** Times hit this session, monotonic. */
    public Integer hitsTaken;

    /**
     * Distance of the last attack that landed, in blocks. Null until one has.
     *
     * <p>Null and not zero, and the distinction is the mod: a reach of 0 is not a swing at
     * point-blank range, it is a session in which nothing has been hit. The widget draws nothing
     * in that state rather than a figure nobody earned.</p>
     */
    public Double reach;

    /**
     * The main inventory, merged into one entry per distinct thing. Null to omit.
     *
     * <p>An empty tally is not the same as null and both reach the wire as an absence, which is
     * the one place this field is subtle: a player holding nothing has an empty inventory and a
     * sensor that threw has no reading, and neither should make a counter draw a zero it did not
     * measure. {@link dev.voidpvp.client.sensor.TickCoalescer} sends the empty array for the
     * first and nothing for the second.</p>
     */
    public InventoryTally inventory;

    /**
     * Clears every optional reading.
     *
     * <p>Called before each refill so that a reading which became unavailable — the player let go
     * of an item, a sensor threw — actually goes absent, rather than the previous tick's value
     * being resent because nobody overwrote the field. A stale reading that looks live is the
     * failure this whole payload is careful about.</p>
     */
    public void clearOptional() {
        armor = null;
        fx = null;
        saturation = null;
        heldCount = null;
        heldItem = null;
        speed = null;
        memoryUsedMb = null;
        memoryMaxMb = null;
        hitsDealt = null;
        hitsTaken = null;
        reach = null;
        inventory = null;
    }
}
