package dev.voidpvp.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Reads {@code schema/} — the actual contract files, not a copy.
 *
 * <p>CONTRACTS.md says a cross-directory need is expressed by reading a schema,
 * so the tests do exactly that: every example in {@code protocol.json} and
 * {@code bridge.json} is replayed through the code that has to speak them, and
 * a change to a schema that this mod has not caught up with fails the build
 * rather than being discovered in game.</p>
 */
final class Schemas {

    private Schemas() {
    }

    static File dir() {
        String configured = System.getProperty("void.schema.dir");
        File dir = configured != null ? new File(configured) : new File("../schema");
        assertTrue(dir.isDirectory(), "schema directory not found at " + dir.getAbsolutePath()
                + " (set -Dvoid.schema.dir)");
        return dir;
    }

    static JsonObject load(String name) {
        File file = new File(dir(), name);
        try {
            InputStream in = new FileInputStream(file);
            try {
                return new JsonParser()
                        .parse(new InputStreamReader(in, Charset.forName("UTF-8")))
                        .getAsJsonObject();
            } finally {
                in.close();
            }
        } catch (Exception e) {
            fail("could not read " + file + ": " + e);
            return null;
        }
    }

    static JsonArray examples(String name) {
        JsonObject schema = load(name);
        assertTrue(schema.has("examples"), name + " has no top-level examples array");
        return schema.getAsJsonArray("examples");
    }

    /**
     * Asserts that every field of {@code expected} appears, with the same
     * value, in {@code actual}. Extra fields in {@code actual} are fine: the
     * mod materialises schema defaults that an example is free to omit.
     */
    static void assertContains(JsonElement expected, JsonElement actual, String path) {
        if (expected.isJsonObject()) {
            assertTrue(actual != null && actual.isJsonObject(),
                    path + ": expected an object, got " + actual);
            for (Map.Entry<String, JsonElement> e : expected.getAsJsonObject().entrySet()) {
                JsonElement child = actual.getAsJsonObject().get(e.getKey());
                assertTrue(child != null, path + "." + e.getKey() + " is missing");
                assertContains(e.getValue(), child, path + "." + e.getKey());
            }
            return;
        }
        if (expected.isJsonArray()) {
            assertTrue(actual != null && actual.isJsonArray(),
                    path + ": expected an array, got " + actual);
            JsonArray a = expected.getAsJsonArray();
            JsonArray b = actual.getAsJsonArray();
            assertTrue(a.size() == b.size(),
                    path + ": expected " + a.size() + " items, got " + b.size());
            for (int i = 0; i < a.size(); i++) {
                assertContains(a.get(i), b.get(i), path + "[" + i + "]");
            }
            return;
        }
        if (expected.isJsonPrimitive() && expected.getAsJsonPrimitive().isNumber()
                && actual != null && actual.isJsonPrimitive()
                && actual.getAsJsonPrimitive().isNumber()) {
            double d = Math.abs(expected.getAsDouble() - actual.getAsDouble());
            assertTrue(d < 1e-9, path + ": expected " + expected + ", got " + actual);
            return;
        }
        assertTrue(expected.equals(actual), path + ": expected " + expected + ", got " + actual);
    }
}
