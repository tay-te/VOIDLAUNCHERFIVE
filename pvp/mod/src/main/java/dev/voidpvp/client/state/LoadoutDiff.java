package dev.voidpvp.client.state;

import com.google.gson.JsonElement;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The diff between two loadouts, expressed as a
 * {@code protocol.json#/definitions/state_patch}: a flat map of
 * {@code mods.<mod_id>.<setting>} to the new value.
 *
 * <p>This is what a hot-swap reports to Rust (§8.2): the launcher already has
 * both loadouts, but the patch tells it which live values actually changed and
 * keeps the {@code state} message the single way live state travels.</p>
 */
public final class LoadoutDiff {

    private LoadoutDiff() {
    }

    /** Paths whose effective value differs between {@code from} and {@code to}. */
    public static Map<String, JsonElement> diff(Loadout from, Loadout to) {
        Map<String, JsonElement> patch = new LinkedHashMap<String, JsonElement>();
        if (to == null) {
            return patch;
        }
        for (String modId : ModRegistry.modIds()) {
            for (String key : ModRegistry.settingKeys(modId)) {
                JsonElement after = to.setting(modId, key);
                JsonElement before = from == null ? null : from.setting(modId, key);
                if (!Json.same(before, after)) {
                    patch.put("mods." + modId + "." + key, after);
                }
            }
        }
        return patch;
    }

    /** The one-entry patch for a single setting write. */
    public static Map<String, JsonElement> single(String modId, String key, JsonElement value) {
        Map<String, JsonElement> patch = new LinkedHashMap<String, JsonElement>();
        patch.put("mods." + modId + "." + key, value);
        return patch;
    }
}
