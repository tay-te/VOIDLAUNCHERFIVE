package dev.voidpvp.client.state;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Loadout share codes, the Java half of {@code void_loadout::share} — the in-game Loadouts
 * screen exports and imports them without a round trip to the launcher.
 *
 * <pre>
 * VOID1.&lt;base64url, no padding, of raw DEFLATE(json)&gt;.&lt;crc32(json) as 8 lowercase hex&gt;
 * </pre>
 *
 * <p>The JSON carries the name, icon, server, mc, registry version, HUD layout, quick pins and
 * <b>only the settings that differ from the registry defaults</b>. Ids and stats never travel.
 * {@code schema/share-code.fixtures.json} holds codes made by every implementation and
 * {@code ShareCodeTest} decodes all of them, so Rust, Java and the TypeScript fake bridge read
 * each other's codes or a test fails.</p>
 *
 * <p>Decoding is strict about structure (version, checksum, decodable data, a usable name) and
 * lenient about content: a mod or setting this build does not know, or a value outside its
 * schema range, is dropped and counted, so a code made on a newer client still imports. What
 * comes out is materialised by {@link Loadout#fromJson} and is therefore a valid
 * {@code loadout.json} loadout.</p>
 *
 * <p>Stateless and thread-safe.</p>
 */
public final class ShareCode {

    /** The format version this build writes, and the newest it reads. */
    public static final int VERSION = 1;
    /** Longest code accepted. */
    public static final int MAX_CODE_LENGTH = 16 * 1024;
    /** Largest decompressed payload accepted; a guard against a crafted code. */
    static final int MAX_JSON_BYTES = 256 * 1024;

    private ShareCode() {
    }

    /** Why a code was refused; {@link #wire} is {@code bridge.json}'s {@code import_result.error}. */
    public enum Error {
        EMPTY("empty"), FORMAT("format"), VERSION("version"), CHECKSUM("checksum"),
        INVALID("invalid"), FULL("full");

        public final String wire;

        Error(String wire) {
            this.wire = wire;
        }
    }

    /** The outcome of {@link #decode}: a loadout body (no id) and a drop count, or an error. */
    public static final class Decoded {
        /** {@code loadout.json}-shaped, without {@code id}; null when {@link #error} is set. */
        public final JsonObject loadout;
        public final int dropped;
        public final Error error;

        private Decoded(JsonObject loadout, int dropped, Error error) {
            this.loadout = loadout;
            this.dropped = dropped;
            this.error = error;
        }

        static Decoded fail(Error e) {
            return new Decoded(null, 0, e);
        }

        public boolean ok() {
            return error == null;
        }

        /** The decoded name, trimmed; null on failure. */
        public String name() {
            return loadout == null ? null : loadout.get("name").getAsString();
        }
    }

    // -----------------------------------------------------------------
    // Encode
    // -----------------------------------------------------------------

    /** The share code of {@code loadout}. */
    public static String encode(Loadout loadout) {
        byte[] json = payload(loadout).toString().getBytes(StandardCharsets.UTF_8);
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        try {
            deflater.setInput(json);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(json.length / 2 + 16);
            byte[] buf = new byte[1024];
            while (!deflater.finished()) {
                int n = deflater.deflate(buf);
                out.write(buf, 0, n);
            }
            return "VOID" + VERSION + "."
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
                    + "." + hex8(crc(json));
        } finally {
            deflater.end();
        }
    }

    /** The JSON a code carries, before compression. Package-private for tests. */
    static JsonObject payload(Loadout loadout) {
        JsonObject full = loadout.toJson();
        JsonObject o = new JsonObject();
        o.addProperty("name", loadout.name());
        o.addProperty("icon", loadout.icon());
        if (loadout.server() != null) {
            o.addProperty("server", loadout.server());
        }
        o.addProperty("mc", loadout.mc());
        o.addProperty("reg", Integer.valueOf(ModRegistry.REGISTRY_VERSION));
        JsonObject mods = new JsonObject();
        JsonObject all = full.getAsJsonObject("mods");
        for (String modId : ModRegistry.modIds()) {
            if (!all.has(modId)) {
                continue;
            }
            JsonObject defaults = ModRegistry.defaults(modId);
            JsonObject changed = new JsonObject();
            for (Map.Entry<String, JsonElement> e : all.getAsJsonObject(modId).entrySet()) {
                if (!Json.same(defaults.get(e.getKey()), e.getValue())) {
                    changed.add(e.getKey(), e.getValue());
                }
            }
            if (!changed.entrySet().isEmpty()) {
                mods.add(modId, changed);
            }
        }
        o.add("mods", mods);
        o.add("hud", full.getAsJsonArray("hud"));
        if (full.has("quick")) {
            o.add("quick", full.get("quick"));
        }
        return o;
    }

    // -----------------------------------------------------------------
    // Decode
    // -----------------------------------------------------------------

    /** Decodes a pasted code. Never throws. */
    public static Decoded decode(String code) {
        if (code == null) {
            return Decoded.fail(Error.EMPTY);
        }
        String c = code.trim();
        if (c.isEmpty()) {
            return Decoded.fail(Error.EMPTY);
        }
        if (c.length() > MAX_CODE_LENGTH) {
            return Decoded.fail(Error.FORMAT);
        }
        int first = c.indexOf('.');
        int last = c.lastIndexOf('.');
        if (first <= 0 || last <= first) {
            return Decoded.fail(Error.FORMAT);
        }
        String head = c.substring(0, first);
        if (!head.startsWith("VOID") || head.length() == 4) {
            return Decoded.fail(Error.FORMAT);
        }
        int version;
        try {
            version = Integer.parseInt(head.substring(4));
        } catch (NumberFormatException e) {
            return Decoded.fail(Error.FORMAT);
        }
        if (version <= 0) {
            return Decoded.fail(Error.FORMAT);
        }
        if (version > VERSION) {
            return Decoded.fail(Error.VERSION);
        }
        String body = c.substring(first + 1, last);
        String check = c.substring(last + 1);
        if (check.length() != 8 || !check.matches("[0-9a-fA-F]{8}")) {
            return Decoded.fail(Error.FORMAT);
        }
        long expected = Long.parseLong(check, 16);
        byte[] compressed;
        try {
            compressed = Base64.getUrlDecoder().decode(body);
        } catch (IllegalArgumentException e) {
            return Decoded.fail(Error.FORMAT);
        }
        byte[] json = inflate(compressed);
        if (json == null) {
            return Decoded.fail(Error.FORMAT);
        }
        if (crc(json) != expected) {
            return Decoded.fail(Error.CHECKSUM);
        }
        JsonElement parsed;
        try {
            parsed = Json.parse(new String(json, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            return Decoded.fail(Error.FORMAT);
        }
        if (parsed == null || !parsed.isJsonObject()) {
            return Decoded.fail(Error.INVALID);
        }
        return read(parsed.getAsJsonObject());
    }

    private static Decoded read(JsonObject in) {
        int dropped = 0;
        String name = string(in, "name");
        if (name == null) {
            return Decoded.fail(Error.INVALID);
        }
        name = name.trim();
        if (name.codePointCount(0, name.length()) > 48) {
            name = name.substring(0, name.offsetByCodePoints(0, 48)).trim();
        }
        if (name.isEmpty()) {
            return Decoded.fail(Error.INVALID);
        }
        String icon = string(in, "icon");
        if (icon == null || icon.isEmpty() || icon.length() > 32) {
            icon = "sword";
        }
        String server = string(in, "server");
        if (server != null && server.length() > 32) {
            server = null;
        }
        String mc = string(in, "mc");
        if (mc == null || !mc.matches("[0-9]{1,4}\\.[0-9]{1,4}(\\.[0-9]{1,4})?")) {
            mc = "1.8.9";
        }

        JsonObject mods = new JsonObject();
        if (in.has("mods") && in.get("mods").isJsonObject()) {
            for (Map.Entry<String, JsonElement> m : in.getAsJsonObject("mods").entrySet()) {
                String modId = m.getKey();
                if (!ModRegistry.isMod(modId) || !m.getValue().isJsonObject()) {
                    dropped++;
                    continue;
                }
                JsonObject accepted = new JsonObject();
                for (Map.Entry<String, JsonElement> s : m.getValue().getAsJsonObject().entrySet()) {
                    String key = s.getKey();
                    if (ModRegistry.defaultOf(modId, key) == null) {
                        dropped++;
                        continue;
                    }
                    // Exactly valid or dropped — never clamped. Rust's decoder cannot clamp
                    // (its settings types only accept or refuse), and a code must mean the same
                    // loadout to both readers.
                    JsonElement clamped = ModRegistry.clamp(modId, key, s.getValue());
                    if (clamped == null || !Json.same(clamped, s.getValue())) {
                        dropped++;
                        continue;
                    }
                    accepted.add(key, clamped);
                }
                if (!accepted.entrySet().isEmpty()) {
                    mods.add(modId, accepted);
                }
            }
        }

        JsonArray hud = new JsonArray();
        List<String> placed = new ArrayList<String>();
        if (in.has("hud") && in.get("hud").isJsonArray()) {
            for (JsonElement e : in.getAsJsonArray("hud")) {
                JsonObject item = hudItem(e);
                if (item == null || placed.contains(item.get("id").getAsString())) {
                    dropped++;
                    continue;
                }
                placed.add(item.get("id").getAsString());
                hud.add(item);
            }
        }

        JsonObject out = new JsonObject();
        out.addProperty("name", name);
        out.addProperty("icon", icon);
        if (server == null) {
            out.add("server", com.google.gson.JsonNull.INSTANCE);
        } else {
            out.addProperty("server", server);
        }
        out.addProperty("mc", mc);
        out.add("mods", mods);
        out.add("hud", hud);
        if (in.has("quick") && in.get("quick").isJsonArray()) {
            JsonArray given = in.getAsJsonArray("quick");
            List<String> kept = Loadout.normaliseQuick(given);
            dropped += given.size() - kept.size();
            JsonArray q = new JsonArray();
            for (String id : kept) {
                q.add(new JsonPrimitive(id));
            }
            out.add("quick", q);
        }
        return new Decoded(out, dropped, null);
    }

    /** One HUD item if every field is usable, else null. */
    private static JsonObject hudItem(JsonElement e) {
        if (e == null || !e.isJsonObject()) {
            return null;
        }
        JsonObject o = e.getAsJsonObject();
        String id = string(o, "id");
        String anchor = string(o, "anchor");
        if (id == null || !ModRegistry.isHud(id) || anchor == null
                || !HudItem.ANCHORS.contains(anchor)) {
            return null;
        }
        Double dx = number(o, "dx");
        Double dy = number(o, "dy");
        if (dx == null || dy == null || Math.abs(dx.doubleValue()) > 4096
                || Math.abs(dy.doubleValue()) > 4096) {
            return null;
        }
        JsonObject item = new JsonObject();
        item.addProperty("id", id);
        item.addProperty("anchor", anchor);
        item.add("dx", o.get("dx"));
        item.add("dy", o.get("dy"));
        if (o.has("scale") && !o.get("scale").isJsonNull()) {
            Double scale = number(o, "scale");
            if (scale == null || scale.doubleValue() < 0.25 || scale.doubleValue() > 4) {
                return null;
            }
            item.add("scale", o.get("scale"));
        }
        return item;
    }

    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()
                ? e.getAsString() : null;
    }

    private static Double number(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        double d = e.getAsDouble();
        return Double.isNaN(d) || Double.isInfinite(d) ? null : Double.valueOf(d);
    }

    private static byte[] inflate(byte[] data) {
        Inflater inflater = new Inflater(true);
        try {
            inflater.setInput(data);
            ByteArrayOutputStream out = new ByteArrayOutputStream(data.length * 3 + 16);
            byte[] buf = new byte[2048];
            while (!inflater.finished()) {
                int n = inflater.inflate(buf);
                if (n == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) {
                        // A raw stream that ends without its final block is truncated.
                        return null;
                    }
                }
                out.write(buf, 0, n);
                if (out.size() > MAX_JSON_BYTES) {
                    return null;
                }
            }
            return out.toByteArray();
        } catch (DataFormatException e) {
            return null;
        } finally {
            inflater.end();
        }
    }

    private static long crc(byte[] bytes) {
        CRC32 crc = new CRC32();
        crc.update(bytes, 0, bytes.length);
        return crc.getValue();
    }

    private static String hex8(long v) {
        String h = Long.toHexString(v);
        StringBuilder sb = new StringBuilder(8);
        for (int i = h.length(); i < 8; i++) {
            sb.append('0');
        }
        return sb.append(h).toString();
    }
}
