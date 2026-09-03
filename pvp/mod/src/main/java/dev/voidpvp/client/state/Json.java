package dev.voidpvp.client.state;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * The handful of JSON helpers the mod needs. Gson 2.2.4 is what Minecraft
 * 1.8.9 puts on the classpath, so nothing here may use an API added later
 * ({@code JsonParser.parseString}, {@code JsonObject.keySet} and friends).
 */
public final class Json {

    private Json() {
    }

    public static JsonElement parse(String text) {
        return new JsonParser().parse(text);
    }

    public static JsonObject parseObject(String text) {
        JsonElement e = parse(text);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    /**
     * Numbers round-trip as integers when they have no fractional part, so a
     * schema field typed {@code integer} never goes out as {@code 20.0}.
     */
    public static JsonPrimitive number(double v) {
        if (v == Math.rint(v) && !Double.isInfinite(v) && Math.abs(v) < 1e15) {
            return new JsonPrimitive(Long.valueOf((long) v));
        }
        return new JsonPrimitive(Double.valueOf(v));
    }

    public static JsonObject deepCopy(JsonObject o) {
        return o == null ? null : parseObject(o.toString());
    }

    public static JsonArray deepCopy(JsonArray a) {
        return a == null ? null : parse(a.toString()).getAsJsonArray();
    }

    public static String string(JsonObject o, String key, String fallback) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return fallback;
        }
        return o.get(key).getAsString();
    }

    public static boolean bool(JsonObject o, String key, boolean fallback) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return o.get(key).getAsBoolean();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public static double number(JsonObject o, String key, double fallback) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return o.get(key).getAsDouble();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public static int integer(JsonObject o, String key, int fallback) {
        return (int) Math.round(number(o, key, fallback));
    }

    /** {@code true} when both trees are structurally equal. */
    public static boolean same(JsonElement a, JsonElement b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.equals(b);
    }
}
