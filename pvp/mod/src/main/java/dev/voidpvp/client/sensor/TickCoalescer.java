package dev.voidpvp.client.sensor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.voidpvp.client.state.Json;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code tick} sensor (§6.6): FPS, ping, position, armor and potion
 * effects coalesced into one push per game tick, i.e. 20 Hz.
 *
 * <p>{@code armor} and {@code fx} are carried only on the ticks where they
 * changed — {@code bridge.json} says a handler treats an absent field as
 * unchanged, and the durability of a set of armor that is not being hit is the
 * same 20 times a second.</p>
 *
 * <p><b>So are FPS, ping and position.</b> This class used to send those three on every tick, on
 * the grounds that they "change constantly and cost nothing". Measured in game, they cost more
 * than everything else combined. Each field that changes re-renders its HUD chip, and Ultralight
 * reports damage as a single <em>bounding rectangle</em> rather than a region — so a chip ticking
 * in one corner drags that rectangle across the whole 6.6 MP surface and unions with whatever the
 * menu is doing. One full-surface repaint measured 49 ms. FPS is the worst of the three because it
 * is the live frame rate: it genuinely changes every tick, so no value-equality check can suppress
 * it, and it alone produced a ~50 ms stall several times a second.</p>
 *
 * <p>FPS is therefore rate-limited to {@link #FPS_INTERVAL_MS} as well as value-checked. A number
 * changing 20 times a second is unreadable anyway, so this costs the reader nothing; ping and
 * position are merely value-checked, since standing still or a stable connection then sends
 * nothing at all.</p>
 */
public final class TickCoalescer {

    /**
     * How often FPS may be republished. Four times a second is faster than anyone reads a number
     * and 5x cheaper than every tick. The HUD's "1% low" is unaffected: it is derived from these
     * same samples, so {@code store.ts} sizes its window in samples, not ticks.
     */
    static final long FPS_INTERVAL_MS = 250L;

    /**
     * How often speed may be republished. The same 250 ms as FPS and for the same reason — it
     * changes every tick while moving — but it is a separate constant because the two are
     * separate decisions, and tying them together would mean tuning one to fix the other.
     */
    static final long SPEED_INTERVAL_MS = 250L;

    /**
     * How often heap may be republished. A second, four times looser than the others: memory is
     * the least urgent number on the HUD and the most restless, so it is the one field where the
     * limit is doing most of the work.
     */
    static final long MEMORY_INTERVAL_MS = 1000L;

    /** The clock the rate limit reads, so a test can drive it without sleeping. */
    public interface Clock {
        long millis();
    }

    private final Clock clock;

    private List<ArmorSlot> lastArmor;
    private List<PotionFx> lastFx;
    private int lastFps = Integer.MIN_VALUE;
    private long lastFpsAt = Long.MIN_VALUE;
    private int lastPing = Integer.MIN_VALUE;
    private boolean posSent;
    private double lastSaturation = Double.NaN;
    private int lastHeldCount = Integer.MIN_VALUE;
    private double lastSpeed = Double.NaN;
    private long lastSpeedAt = Long.MIN_VALUE;
    private int lastMemUsed = Integer.MIN_VALUE;
    private long lastMemAt = Long.MIN_VALUE;
    private int lastHitsDealt = Integer.MIN_VALUE;
    /** The last reach reported, or null before one was. */
    private Double lastReach;
    private int lastHitsTaken = Integer.MIN_VALUE;
    private double lastX;
    private double lastY;
    private double lastZ;
    private double lastYaw;

    public TickCoalescer() {
        this(new Clock() {
            @Override
            public long millis() {
                return System.currentTimeMillis();
            }
        });
    }

    public TickCoalescer(Clock clock) {
        this.clock = clock;
    }

    /**
     * Builds the payload for one tick.
     *
     * <p>Takes a {@link TickInput} rather than a parameter list; that file says why. Every block
     * below decides for itself when its field may be sent, and the rules genuinely differ —
     * which is why this is explicit repetition rather than a generic "emit if changed" helper.
     * A helper would hide exactly the distinction the class header spends four paragraphs on:
     * FPS is rate-limited <em>because no value check can suppress it</em>, position is rounded
     * <em>before</em> comparison so a sub-representable movement sends nothing, and armor and fx
     * are compared by content. Three different answers to "has this changed".</p>
     */
    public JsonObject build(TickInput in) {
        int fps = in.fps;
        int ping = in.ping;
        double x = in.x;
        double y = in.y;
        double z = in.z;
        float yaw = in.yaw;
        List<ArmorSlot> armor = in.armor;
        List<PotionFx> fx = in.fx;
        JsonObject o = new JsonObject();

        int clampedFps = clamp(fps, 0, 100000);
        long now = clock.millis();
        if (clampedFps != lastFps && (lastFpsAt == Long.MIN_VALUE || now - lastFpsAt >= FPS_INTERVAL_MS)) {
            lastFps = clampedFps;
            lastFpsAt = now;
            o.addProperty("fps", Integer.valueOf(clampedFps));
        }

        int clampedPing = clamp(ping, -1, 60000);
        if (clampedPing != lastPing) {
            lastPing = clampedPing;
            o.addProperty("ping", Integer.valueOf(clampedPing));
        }

        // Rounded first, then compared: the wire only ever carries the rounded value, so comparing
        // the raw doubles would send a new position for a movement too small to be representable.
        double rx = round(x);
        double ry = round(y);
        double rz = round(z);
        double ryaw = round(normaliseYaw(yaw));
        if (!posSent || rx != lastX || ry != lastY || rz != lastZ || ryaw != lastYaw) {
            posSent = true;
            lastX = rx;
            lastY = ry;
            lastZ = rz;
            lastYaw = ryaw;
            JsonObject pos = new JsonObject();
            pos.add("x", Json.number(rx));
            pos.add("y", Json.number(ry));
            pos.add("z", Json.number(rz));
            pos.add("yaw", Json.number(ryaw));
            o.add("pos", pos);
        }

        if (armor != null && armorChanged(armor)) {
            lastArmor = new ArrayList<ArmorSlot>(armor);
            JsonArray arr = new JsonArray();
            for (ArmorSlot slot : armor) {
                arr.add(slot.toJson());
            }
            o.add("armor", arr);
        }
        if (fx != null && fxChanged(fx)) {
            lastFx = new ArrayList<PotionFx>(fx);
            JsonArray arr = new JsonArray();
            for (PotionFx e : fx) {
                arr.add(e.toJson());
            }
            o.add("fx", arr);
        }

        // Saturation moves when you eat and when you exert yourself, not on a clock, so a value
        // check is the whole rule. Rounded to 1 dp first for the same reason position is rounded
        // before comparison: the wire carries the rounded figure, so comparing the raw double
        // would republish for a change too small to be representable.
        if (in.saturation != null) {
            double sat = Math.round(in.saturation.doubleValue() * 10.0) / 10.0;
            if (Double.isNaN(lastSaturation) || sat != lastSaturation) {
                lastSaturation = sat;
                o.add("saturation", Json.number(sat));
            }
        }

        // A stack size changes on use. An empty hand omits the field rather than sending 0 —
        // `bridge.json` says so, and the widget draws nothing rather than a zero.
        if (in.heldCount != null && in.heldCount.intValue() != lastHeldCount) {
            lastHeldCount = in.heldCount.intValue();
            o.addProperty("held_count", in.heldCount);
        }

        // Speed changes every tick while moving, so a value check alone would republish 20 Hz
        // and cost the full-surface repaint this class exists to avoid. Rate-limited like FPS,
        // and for the same reason: a number moving 20 times a second is unreadable anyway.
        if (in.speed != null) {
            double speed = Math.round(in.speed.doubleValue() * 100.0) / 100.0;
            if ((Double.isNaN(lastSpeed) || speed != lastSpeed)
                    && (lastSpeedAt == Long.MIN_VALUE || now - lastSpeedAt >= SPEED_INTERVAL_MS)) {
                lastSpeed = speed;
                lastSpeedAt = now;
                o.add("speed", Json.number(speed));
            }
        }

        // Heap moves constantly and is the field most able to undo the coalescing, so its limit
        // is the loosest here by an order of magnitude. `max_mb` is a constant for the process
        // and rides along with `used_mb` rather than being tracked separately — the object is
        // one field on the wire and splitting it would send half a reading.
        if (in.memoryUsedMb != null && in.memoryMaxMb != null) {
            int used = in.memoryUsedMb.intValue();
            if (used != lastMemUsed
                    && (lastMemAt == Long.MIN_VALUE || now - lastMemAt >= MEMORY_INTERVAL_MS)) {
                lastMemUsed = used;
                lastMemAt = now;
                JsonObject mem = new JsonObject();
                mem.addProperty("used_mb", Integer.valueOf(used));
                mem.addProperty("max_mb", in.memoryMaxMb);
                o.add("memory", mem);
            }
        }

        // Hit counters move only on a hit, so a value check is exact and no limit is wanted: a
        // rate limit here would merge two hits into one and the combo would under-count. This is
        // the one new field where dropping an update is a *wrong answer* rather than a stale one.
        if (in.hitsDealt != null && in.hitsTaken != null
                && (in.hitsDealt.intValue() != lastHitsDealt
                    || in.hitsTaken.intValue() != lastHitsTaken)) {
            lastHitsDealt = in.hitsDealt.intValue();
            lastHitsTaken = in.hitsTaken.intValue();
            JsonObject hits = new JsonObject();
            hits.addProperty("dealt", in.hitsDealt);
            hits.addProperty("taken", in.hitsTaken);
            o.add("hits", hits);
        }

        // Reach moves only on a landed attack, so a value check is exact and no rate limit is
        // wanted — the same argument as `hits` above, and the same consequence: dropping an
        // update here is a *wrong answer* rather than a stale one, because two swings at the
        // same distance are indistinguishable from one and the player would see a figure that
        // stopped responding to their own hits.
        //
        // Compared as a boxed value rather than a primitive so that "no reading yet" survives:
        // the field is null until something has been hit, and a `double` sentinel here would be
        // a second spelling of the absence `TickInput.reach` already has a type for.
        if (in.reach != null && !in.reach.equals(lastReach)) {
            lastReach = in.reach;
            o.add("reach", Json.number(in.reach.doubleValue()));
        }
        return o;
    }

    /** Forgets what was last reported, so the next tick carries everything. */
    public void reset() {
        lastSaturation = Double.NaN;
        lastHeldCount = Integer.MIN_VALUE;
        lastSpeed = Double.NaN;
        lastSpeedAt = Long.MIN_VALUE;
        lastMemUsed = Integer.MIN_VALUE;
        lastMemAt = Long.MIN_VALUE;
        lastHitsDealt = Integer.MIN_VALUE;
        lastHitsTaken = Integer.MIN_VALUE;
        lastReach = null;
        lastArmor = null;
        lastFx = null;
        lastFps = Integer.MIN_VALUE;
        lastFpsAt = Long.MIN_VALUE;
        lastPing = Integer.MIN_VALUE;
        posSent = false;
    }


    private boolean armorChanged(List<ArmorSlot> armor) {
        if (lastArmor == null || lastArmor.size() != armor.size()) {
            return true;
        }
        for (int i = 0; i < armor.size(); i++) {
            if (!armor.get(i).equals(lastArmor.get(i))) {
                return true;
            }
        }
        return false;
    }

    private boolean fxChanged(List<PotionFx> fx) {
        if (lastFx == null || lastFx.size() != fx.size()) {
            return true;
        }
        for (int i = 0; i < fx.size(); i++) {
            PotionFx now = fx.get(i);
            PotionFx before = lastFx.get(i);
            if (!now.sameEffect(before)) {
                return true;
            }
            // A re-applied effect keeps its id and amplifier but its remaining
            // time jumps back up; without this the UI would keep counting down
            // from the old value.
            if (now.durationMs > before.durationMs) {
                return true;
            }
        }
        return false;
    }

    /** Yaw normalised to [-180, 180), the range {@code bridge.json} declares. */
    public static double normaliseYaw(double yaw) {
        double y = yaw % 360.0;
        if (y >= 180.0) {
            y -= 360.0;
        }
        if (y < -180.0) {
            y += 360.0;
        }
        if (y == 180.0) {
            y = -180.0;
        }
        return y;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
