package dev.voidpvp.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.voidpvp.client.input.KeyNames;
import dev.voidpvp.client.input.VanillaBinds;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** The {@code binds} payload against {@code bridge.json}'s example and its limits. */
class VanillaBindsTest {

    @Test
    @DisplayName("the bridge.json binds example is exactly what the table builds from its columns")
    void rebuildsTheExample() {
        JsonArray payload = null;
        JsonArray examples = Schemas.examples("bridge.json");
        for (int i = 0; i < examples.size(); i++) {
            JsonObject e = examples.get(i).getAsJsonObject();
            if (e.has("e") && "binds".equals(e.get("e").getAsString())) {
                payload = e.getAsJsonArray("payload");
            }
        }
        assertNotNull(payload, "bridge.json has a binds example");
        int n = payload.size();
        String[] ids = new String[n];
        String[] labels = new String[n];
        int[] codes = new int[n];
        for (int i = 0; i < n; i++) {
            JsonObject row = payload.get(i).getAsJsonObject();
            ids[i] = row.get("id").getAsString();
            labels[i] = row.get("label").getAsString();
            codes[i] = KeyNames.codeOf(row.get("key").getAsString());
        }
        assertEquals(payload, VanillaBinds.table(ids, labels, codes));
    }

    @Test
    @DisplayName("mouse buttons, unbound and unknown codes get schema names; empty rows are skipped")
    void namesAndLimits() {
        JsonArray t = VanillaBinds.table(
                new String[] {"key.attack", "key.smoothCamera", "key.weird", null, "key.drop"},
                new String[] {"Attack/Destroy", "Toggle Cinematic Camera", null, "x", "Drop Item"},
                new int[] {-100, 0, 999, 16, 34});
        assertEquals(4, t.size());
        assertEquals("MOUSE0", t.get(0).getAsJsonObject().get("key").getAsString());
        assertEquals("NONE", t.get(1).getAsJsonObject().get("key").getAsString());
        assertEquals("NONE", t.get(2).getAsJsonObject().get("key").getAsString(), "no name, no match");
        assertEquals("key.weird", t.get(2).getAsJsonObject().get("label").getAsString(), "label falls back to id");
        assertEquals("G", t.get(3).getAsJsonObject().get("key").getAsString());

        int many = VanillaBinds.MAX_BINDS + 10;
        String[] ids = new String[many];
        String[] labels = new String[many];
        int[] codes = new int[many];
        for (int i = 0; i < many; i++) {
            ids[i] = "key.extra." + i;
            labels[i] = new String(new char[100]).replace('\0', 'x');
        }
        JsonArray capped = VanillaBinds.table(ids, labels, codes);
        assertEquals(VanillaBinds.MAX_BINDS, capped.size());
        assertEquals(VanillaBinds.MAX_TEXT, capped.get(0).getAsJsonObject().get("label").getAsString().length());
    }
}
