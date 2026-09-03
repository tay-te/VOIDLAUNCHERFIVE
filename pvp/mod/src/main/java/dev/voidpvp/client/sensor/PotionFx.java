package dev.voidpvp.client.sensor;

import com.google.gson.JsonObject;

/**
 * One entry of the {@code tick} payload's {@code fx} array. Durations cross
 * the bridge in milliseconds, converted from ticks here, so the UI never needs
 * to know the tick rate.
 */
public final class PotionFx {

    public final int id;
    public final String name;
    public final int amplifier;
    public final int durationMs;
    public final boolean ambient;

    public PotionFx(int id, String name, int amplifier, int durationMs, boolean ambient) {
        this.id = id;
        this.name = name;
        this.amplifier = amplifier;
        this.durationMs = durationMs;
        this.ambient = ambient;
    }

    /** 1.8.9 runs at 20 ticks per second. */
    public static int ticksToMs(int ticks) {
        return Math.max(0, ticks) * 50;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", Integer.valueOf(id));
        if (name != null) {
            o.addProperty("name", name);
        }
        o.addProperty("amplifier", Integer.valueOf(amplifier));
        o.addProperty("duration_ms", Integer.valueOf(durationMs));
        o.addProperty("ambient", Boolean.valueOf(ambient));
        return o;
    }

    /**
     * Effects are compared for change detection without their duration: the
     * remaining time ticks down every tick and would otherwise make the set
     * "changed" forever.
     */
    public boolean sameEffect(PotionFx other) {
        return other != null && id == other.id && amplifier == other.amplifier
                && ambient == other.ambient;
    }
}
