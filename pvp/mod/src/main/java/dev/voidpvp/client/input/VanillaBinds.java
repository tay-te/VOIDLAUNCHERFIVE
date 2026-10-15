package dev.voidpvp.client.input;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * The {@code binds} bridge payload ({@code bridge.json} {@code binds_payload}): vanilla's key
 * bindings as the player has them set, so the page's conflict check warns against the real keys
 * rather than the 1.8.9 defaults.
 *
 * <p>Kept free of Minecraft imports so it is unit-testable: the caller reads
 * {@code GameOptions.allKeys} (translation key, translated label, current code) and hands the
 * three columns here.</p>
 */
public final class VanillaBinds {

    /** {@code binds_payload.maxItems}. 1.8.9 has 33; a mod that adds its own stays under this. */
    public static final int MAX_BINDS = 64;
    /** {@code vanilla_bind.id} and {@code label} {@code maxLength}. */
    public static final int MAX_TEXT = 64;

    private VanillaBinds() {
    }

    /**
     * One row per binding, in the order given. A row without an id is skipped; a code this
     * vocabulary has no name for is sent as {@code NONE}, since the page could not match it to a
     * key anyway.
     */
    public static JsonArray table(String[] ids, String[] labels, int[] codes) {
        JsonArray out = new JsonArray();
        int n = Math.min(ids.length, Math.min(labels.length, codes.length));
        for (int i = 0; i < n && out.size() < MAX_BINDS; i++) {
            String id = ids[i];
            if (id == null || id.isEmpty()) {
                continue;
            }
            JsonObject row = new JsonObject();
            row.addProperty("id", clip(id));
            row.addProperty("label", clip(labels[i] == null ? id : labels[i]));
            row.addProperty("key", KeyNames.nameOf(codes[i]));
            out.add(row);
        }
        return out;
    }

    private static String clip(String s) {
        return s.length() <= MAX_TEXT ? s : s.substring(0, MAX_TEXT);
    }
}
