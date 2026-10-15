package dev.voidpvp.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.voidpvp.client.state.Json;
import dev.voidpvp.client.state.Loadout;
import dev.voidpvp.client.state.ModRegistry;
import dev.voidpvp.client.state.ShareCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loadout share codes — the Java half of {@code void_loadout::share}. The fixture file is the
 * contract between the three implementations: every code in it, whoever made it, must decode
 * here to what it says.
 */
class ShareCodeTest {

    /** The loadout behind the {@code java} fixture code; the same loadout as Rust's fixture. */
    static Loadout fixtureLoadout() {
        JsonObject o = Json.parseObject("{\"id\":\"fixture\",\"name\":\"Bridge practice\","
                + "\"icon\":\"bed\",\"server\":null,\"mc\":\"1.8.9\","
                + "\"mods\":{\"zoom\":{\"fov_divisor\":6,\"key\":\"V\"},"
                + "\"cps\":{\"on\":true,\"mode\":\"both\"}},"
                + "\"hud\":[{\"id\":\"cps\",\"anchor\":\"bottom-left\",\"dx\":175,\"dy\":-108},"
                + "{\"id\":\"fps\",\"anchor\":\"top-left\",\"dx\":23,\"dy\":23,\"scale\":1.5}],"
                + "\"quick\":[\"toggle_sprint\",\"zoom\",\"cps\"]}");
        return Loadout.fromJson(o);
    }

    @Test
    @DisplayName("every fixture code, from every implementation, decodes to what it says")
    void fixturesDecode() {
        JsonArray codes = Schemas.load("share-code.fixtures.json").getAsJsonArray("codes");
        assertTrue(codes.size() >= 2, "the fixture holds a code from Rust and one from Java");
        for (JsonElement e : codes) {
            JsonObject c = e.getAsJsonObject();
            String madeBy = c.get("made_by").getAsString();
            ShareCode.Decoded d = ShareCode.decode(c.get("code").getAsString());
            assertTrue(d.ok(), madeBy + ": " + d.error);
            assertEquals(0, d.dropped, madeBy + " drops nothing");
            JsonObject expect = c.getAsJsonObject("expect");
            assertEquals(expect.get("name").getAsString(), d.name(), madeBy);
            JsonObject body = d.loadout;
            body.addProperty("id", "fixture");
            Loadout l = Loadout.fromJson(body);
            for (Map.Entry<String, JsonElement> m : expect.getAsJsonObject("mods").entrySet()) {
                for (Map.Entry<String, JsonElement> s : m.getValue().getAsJsonObject().entrySet()) {
                    assertTrue(Json.same(s.getValue(), l.setting(m.getKey(), s.getKey())),
                            madeBy + ": " + m.getKey() + "." + s.getKey() + " is "
                                    + l.setting(m.getKey(), s.getKey()));
                }
            }
            assertEquals(expect.get("hud_items").getAsInt(), l.hud().size(), madeBy);
            if (expect.has("quick")) {
                JsonArray q = expect.getAsJsonArray("quick");
                assertEquals(q.size(), l.quick().size(), madeBy);
                for (int i = 0; i < q.size(); i++) {
                    assertEquals(q.get(i).getAsString(), l.quick().get(i), madeBy);
                }
            }
        }
    }

    @Test
    @DisplayName("the java fixture is what this encoder makes (by checksum, over the JSON)")
    void javaFixtureIsPinned() {
        String ours = ShareCode.encode(fixtureLoadout());
        String fixture = null;
        for (JsonElement e : Schemas.load("share-code.fixtures.json").getAsJsonArray("codes")) {
            if ("java".equals(e.getAsJsonObject().get("made_by").getAsString())) {
                fixture = e.getAsJsonObject().get("code").getAsString();
            }
        }
        assertNotNull(fixture, "share-code.fixtures.json has no java code; add: " + ours);
        assertEquals(fixture.substring(fixture.lastIndexOf('.')), ours.substring(ours.lastIndexOf('.')),
                "the Java payload changed; regenerate the java fixture: " + ours);
    }

    @Test
    @DisplayName("a loadout survives the round trip, and an untouched one carries no settings")
    void roundTrip() {
        Loadout original = fixtureLoadout();
        String code = ShareCode.encode(original);
        assertTrue(code.matches("VOID1\\.[A-Za-z0-9_-]+\\.[0-9a-f]{8}"), code);
        ShareCode.Decoded d = ShareCode.decode("  " + code + "\n");
        assertTrue(d.ok());
        JsonObject body = d.loadout;
        body.addProperty("id", "fixture");
        Loadout back = Loadout.fromJson(body);
        for (String mod : ModRegistry.modIds()) {
            for (String key : ModRegistry.settingKeys(mod)) {
                assertTrue(Json.same(original.setting(mod, key), back.setting(mod, key)),
                        mod + "." + key);
            }
        }
        assertEquals(original.hud().size(), back.hud().size());
        assertEquals(original.quick(), back.quick());

        Loadout blank = Loadout.defaults("x", "Blank");
        JsonObject payload = Json.parseObject(new String(inflate(ShareCode.encode(blank)),
                StandardCharsets.UTF_8));
        assertTrue(payload.getAsJsonObject("mods").entrySet().isEmpty(),
                "only settings that differ from the registry travel: " + payload);
    }

    @Test
    @DisplayName("structure is strict: empty, garbage, a newer version, a damaged checksum")
    void structureIsStrict() {
        assertEquals(ShareCode.Error.EMPTY, ShareCode.decode("   ").error);
        assertEquals(ShareCode.Error.FORMAT, ShareCode.decode("hello").error);
        assertEquals(ShareCode.Error.FORMAT, ShareCode.decode("VOID1.!!!.00000000").error);
        assertEquals(ShareCode.Error.VERSION, ShareCode.decode("VOID9.abc.00000000").error);
        String code = ShareCode.encode(fixtureLoadout());
        String damaged = code.substring(0, code.length() - 1)
                + (code.endsWith("0") ? "1" : "0");
        assertEquals(ShareCode.Error.CHECKSUM, ShareCode.decode(damaged).error);
        assertEquals(ShareCode.Error.INVALID, ShareCode.decode(raw("[1,2,3]")).error);
        assertEquals(ShareCode.Error.INVALID, ShareCode.decode(raw("{\"icon\":\"bed\"}")).error);
    }

    @Test
    @DisplayName("content is lenient: unknown and out-of-range entries are dropped and counted")
    void contentIsLenient() {
        // The same payload as Rust's `content_is_lenient`, and the same count: the two readers
        // must agree on what a code means.
        String json = "{\"name\":\"  From the future  \",\"icon\":\"bed\",\"mc\":\"1.8.9\",\"reg\":999,"
                + "\"mods\":{\"zoom\":{\"fov_divisor\":6,\"warp_drive\":true},\"teleport\":{\"on\":true},"
                + "\"fps\":{\"show_label\":\"maybe\"},\"cps\":{\"window_ms\":99999}},"
                + "\"hud\":[{\"id\":\"fps\",\"anchor\":\"top-left\",\"dx\":4,\"dy\":4},"
                + "{\"id\":\"fps\",\"anchor\":\"top\",\"dx\":0,\"dy\":0},"
                + "{\"id\":\"radar\",\"anchor\":\"top\",\"dx\":0,\"dy\":0}],"
                + "\"quick\":[\"zoom\",\"zoom\",\"teleport\",\"fullbright\"]}";
        ShareCode.Decoded d = ShareCode.decode(raw(json));
        assertTrue(d.ok(), String.valueOf(d.error));
        assertEquals(8, d.dropped);
        assertEquals("From the future", d.name());
        JsonObject body = d.loadout;
        body.addProperty("id", "future");
        Loadout l = Loadout.fromJson(body);
        assertEquals(6.0, l.numberSetting("zoom", "fov_divisor", 0), 1e-9);
        assertEquals(1000.0, l.numberSetting("cps", "window_ms", 0), 1e-9,
                "an out-of-range value is dropped, not clamped");
        assertEquals(1, l.hud().size());
        assertEquals(2, l.quick().size());
        assertFalse(l.quick().contains("teleport"));
    }

    // -----------------------------------------------------------------

    private static String raw(String json) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        Deflater d = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        d.setInput(bytes);
        d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[512];
        while (!d.finished()) {
            out.write(buf, 0, d.deflate(buf));
        }
        d.end();
        CRC32 crc = new CRC32();
        crc.update(bytes, 0, bytes.length);
        return "VOID1." + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
                + "." + String.format("%08x", Long.valueOf(crc.getValue()));
    }

    private static byte[] inflate(String code) {
        String body = code.substring(code.indexOf('.') + 1, code.lastIndexOf('.'));
        java.util.zip.Inflater inf = new java.util.zip.Inflater(true);
        inf.setInput(Base64.getUrlDecoder().decode(body));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[512];
        try {
            while (!inf.finished()) {
                out.write(buf, 0, inf.inflate(buf));
            }
        } catch (java.util.zip.DataFormatException e) {
            throw new AssertionError(e);
        } finally {
            inf.end();
        }
        return out.toByteArray();
    }
}
