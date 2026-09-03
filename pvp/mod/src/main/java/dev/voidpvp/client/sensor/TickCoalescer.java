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
 * same 20 times a second. FPS, ping and position always ride along; they
 * change constantly and cost nothing.</p>
 */
public final class TickCoalescer {

    private List<ArmorSlot> lastArmor;
    private List<PotionFx> lastFx;

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
        o.addProperty("fps", Integer.valueOf(clamp(fps, 0, 100000)));
        o.addProperty("ping", Integer.valueOf(clamp(ping, -1, 60000)));

        JsonObject pos = new JsonObject();
        pos.add("x", Json.number(round(x)));
        pos.add("y", Json.number(round(y)));
        pos.add("z", Json.number(round(z)));
        pos.add("yaw", Json.number(round(normaliseYaw(yaw))));
        o.add("pos", pos);

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
