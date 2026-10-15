package dev.voidpvp.client.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What one player wears — {@code protocol.json#/definitions/equipped_set}, the contract's
 * per-player JSON:
 *
 * <pre>{ "uuid": "...", "cape": "id|null", "back": null, "head": null, "face": null, "halo": null,
 *   "trail": null, "badge": "void-badge-founder", "emotes": ["emote-wave"], "tints": { "cape": "#9f8bff" } }</pre>
 *
 * <p>Immutable. Parsing is forgiving in the direction the protocol requires (§7): unknown fields
 * are ignored, and an id this jar's {@link CosmeticCatalog} does not know — or one in the wrong
 * slot — reads as "nothing worn", so a launcher that knows a newer catalog degrades to fewer
 * cosmetics rather than to a broken render.</p>
 */
public final class EquippedSet {

    public static final int MAX_EMOTES = 8;

    /** Dashed, lower case; may be empty for the local set before the session is known. */
    public final String uuid;
    private final Map<String, String> slots;
    private final List<String> emotes;
    private final Map<String, Integer> tints;

    private EquippedSet(String uuid, Map<String, String> slots, List<String> emotes,
                        Map<String, Integer> tints) {
        this.uuid = uuid;
        this.slots = Collections.unmodifiableMap(slots);
        this.emotes = Collections.unmodifiableList(emotes);
        this.tints = Collections.unmodifiableMap(tints);
    }

    public static EquippedSet empty(String uuid) {
        return new EquippedSet(normalise(uuid), new LinkedHashMap<String, String>(),
                new ArrayList<String>(), new LinkedHashMap<String, Integer>());
    }

    /** The item in a slot, or null. */
    public String get(String slot) {
        return slots.get(slot);
    }

    public List<String> emotes() {
        return emotes;
    }

    /** The tint for a slot as {@code 0xRRGGBB}, or -1 when none is set. */
    public int tint(String slot) {
        Integer t = tints.get(slot);
        return t == null ? -1 : t.intValue();
    }

    public boolean isEmpty() {
        return slots.isEmpty() && emotes.isEmpty();
    }

    /** A copy with one slot changed; {@code id} null takes the slot off. */
    public EquippedSet with(String slot, String id) {
        Map<String, String> s = new LinkedHashMap<String, String>(slots);
        if (id == null) {
            s.remove(slot);
        } else {
            s.put(slot, id);
        }
        return new EquippedSet(uuid, s, new ArrayList<String>(emotes), new LinkedHashMap<String, Integer>(tints));
    }

    /** A copy with one tint changed; {@code rgb} &lt; 0 clears it. */
    public EquippedSet withTint(String slot, int rgb) {
        Map<String, Integer> t = new LinkedHashMap<String, Integer>(tints);
        if (rgb < 0) {
            t.remove(slot);
        } else {
            t.put(slot, Integer.valueOf(rgb & 0xFFFFFF));
        }
        return new EquippedSet(uuid, new LinkedHashMap<String, String>(slots), new ArrayList<String>(emotes), t);
    }

    public EquippedSet withEmotes(List<String> ids) {
        return new EquippedSet(uuid, new LinkedHashMap<String, String>(slots), cleanEmotes(ids),
                new LinkedHashMap<String, Integer>(tints));
    }

    public EquippedSet withUuid(String id) {
        return new EquippedSet(normalise(id), new LinkedHashMap<String, String>(slots),
                new ArrayList<String>(emotes), new LinkedHashMap<String, Integer>(tints));
    }

    private static List<String> cleanEmotes(List<String> ids) {
        List<String> out = new ArrayList<String>();
        if (ids == null) {
            return out;
        }
        for (String id : ids) {
            CosmeticCatalog.Item item = CosmeticCatalog.get(id);
            if (item != null && item.kind == CosmeticCatalog.Kind.EMOTE && !out.contains(id)
                    && out.size() < MAX_EMOTES) {
                out.add(id);
            }
        }
        return out;
    }

    /** Lower case with dashes; accepts the undashed 32-hex form Mojang uses in profiles. */
    public static String normalise(String uuid) {
        if (uuid == null) {
            return "";
        }
        String u = uuid.trim().toLowerCase(Locale.ROOT);
        if (u.length() == 32 && u.indexOf('-') < 0) {
            u = u.substring(0, 8) + "-" + u.substring(8, 12) + "-" + u.substring(12, 16) + "-"
                    + u.substring(16, 20) + "-" + u.substring(20);
        }
        return u;
    }

    /** Parses {@code #rrggbb} (either case), or -1. */
    public static int parseHex(String hex) {
        if (hex == null || hex.length() != 7 || hex.charAt(0) != '#') {
            return -1;
        }
        try {
            return Integer.parseInt(hex.substring(1), 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06x", Integer.valueOf(rgb & 0xFFFFFF));
    }

    public static EquippedSet fromJson(JsonObject o) {
        String uuid = o.has("uuid") && o.get("uuid").isJsonPrimitive() ? o.get("uuid").getAsString() : "";
        Map<String, String> slots = new LinkedHashMap<String, String>();
        for (String slot : CosmeticCatalog.SLOTS) {
            JsonElement e = o.get(slot);
            if (e != null && e.isJsonPrimitive()) {
                String id = e.getAsString();
                if (CosmeticCatalog.fits(slot, id)) {
                    slots.put(slot, id);
                }
            }
        }
        List<String> emotes = new ArrayList<String>();
        if (o.has("emotes") && o.get("emotes").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("emotes")) {
                if (e.isJsonPrimitive()) {
                    emotes.add(e.getAsString());
                }
            }
        }
        Map<String, Integer> tints = new LinkedHashMap<String, Integer>();
        if (o.has("tints") && o.get("tints").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("tints").entrySet()) {
                if (!CosmeticCatalog.isSlot(e.getKey()) || !e.getValue().isJsonPrimitive()) {
                    continue;
                }
                int rgb = parseHex(e.getValue().getAsString());
                if (rgb >= 0) {
                    tints.put(e.getKey(), Integer.valueOf(rgb));
                }
            }
        }
        return new EquippedSet(normalise(uuid), slots, cleanEmotes(emotes), tints);
    }

    /** The contract shape: every slot present, null when empty. */
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("uuid", uuid);
        for (String slot : CosmeticCatalog.SLOTS) {
            String id = slots.get(slot);
            if (id == null) {
                o.add(slot, JsonNull.INSTANCE);
            } else {
                o.addProperty(slot, id);
            }
        }
        JsonArray e = new JsonArray();
        for (String id : emotes) {
            e.add(new com.google.gson.JsonPrimitive(id));
        }
        o.add("emotes", e);
        if (!tints.isEmpty()) {
            JsonObject t = new JsonObject();
            for (Map.Entry<String, Integer> en : tints.entrySet()) {
                t.addProperty(en.getKey(), hex(en.getValue().intValue()));
            }
            o.add("tints", t);
        }
        return o;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof EquippedSet)) {
            return false;
        }
        EquippedSet s = (EquippedSet) other;
        return uuid.equals(s.uuid) && slots.equals(s.slots) && emotes.equals(s.emotes) && tints.equals(s.tints);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode() * 31 + slots.hashCode();
    }

    @Override
    public String toString() {
        return toJson().toString();
    }
}
