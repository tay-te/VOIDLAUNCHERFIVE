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
     * @param fps    {@code Minecraft.currentFps}
     * @param ping   own {@code PlayerListEntry.getLatency}, or -1 when unknown
     * @param armor  worn armor plus, when enabled, the held item; null to omit
     * @param fx     active potion effects; null to omit
     */
    public JsonObject build(int fps, int ping, double x, double y, double z, float yaw,
                            List<ArmorSlot> armor, List<PotionFx> fx) {
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
        return o;
    }

    /** Forgets what was last reported, so the next tick carries everything. */
    public void reset() {
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
