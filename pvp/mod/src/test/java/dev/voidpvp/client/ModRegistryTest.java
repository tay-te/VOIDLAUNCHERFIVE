package dev.voidpvp.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.voidpvp.client.state.ModRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ModRegistry} against the real {@code schema/mods.json}.
 *
 * <h2>What this test is for now that the registry is generated</h2>
 *
 * <p>{@code ModRegistry.java}'s table used to be typed in by hand, and most of what follows
 * existed to catch a typo. It is now written by {@code scripts/gen-java-registry.mjs}, and a
 * typo is no longer possible — but <b>staleness is</b>, and staleness is the failure this
 * suite now exists to catch. The generated file is committed (the Gradle build cannot run
 * Node), so there are exactly two ways for it to stop telling the truth: someone changes
 * {@code schema/mods/<id>.json} and does not re-run the generators, or someone edits
 * {@code ModRegistry.java} directly. Both leave a Java table that disagrees with the schema,
 * and both are what these tests fail on.</p>
 *
 * <p>{@code node scripts/gen-java-registry.mjs --check} is the other half of the guard: it
 * proves the committed file is <i>byte-for-byte</i> what the generator produces, which is
 * stronger, and it belongs in CI beside {@code node schema/build.mjs --check}. It cannot run
 * here — there is no Node on the Gradle build path, which is the whole reason the file is
 * committed in the first place — so this suite checks the same thing by <i>meaning</i>: that
 * the table in the JAR says what {@code schema/mods.json} says. That is also the check that
 * still works for someone who has the JAR and not the repo.</p>
 *
 * <p>The rest of the suite is about the hand-written machinery around the table —
 * {@link ModRegistry#clamp}, the {@code Type} dispatch, unknown-key rejection, keybind
 * normalisation. None of that is generated, so none of it is covered by {@code --check}, and
 * it is where the behaviour a player can actually feel lives.</p>
 */
class ModRegistryTest {

    private static JsonObject schema() {
        return Schemas.load("mods.json");
    }

    /** The shipped registry — {@code mods.json} {@code examples[0]}. */
    private static JsonObject registry() {
        return Schemas.examples("mods.json").get(0).getAsJsonObject().getAsJsonObject("mods");
    }

    /** One mod's settings sub-schema properties, in schema order. */
    private static JsonObject settingsOf(JsonObject schema, String id) {
        JsonObject def = schema.getAsJsonObject("definitions").getAsJsonObject(id + "_settings");
        assertNotNull(def, id + "_settings is missing from mods.json");
        return def.getAsJsonObject("properties");
    }

    /**
     * Follow a settings property through its {@code $ref} into {@code mods.json}'s own
     * definitions, local keys winning — the same resolution the generator does, so the test
     * reads the schema the way the generator read it rather than trusting the output.
     */
    private static JsonObject resolve(JsonObject schema, JsonObject node) {
        JsonObject defs = schema.getAsJsonObject("definitions");
        JsonObject n = node;
        for (int hops = 0; hops < 4 && n.has("$ref"); hops++) {
            String ref = n.get("$ref").getAsString().replace("#/definitions/", "");
            JsonObject target = defs.getAsJsonObject(ref);
            assertNotNull(target, "unresolvable $ref " + ref);
            JsonObject merged = new JsonObject();
            for (Map.Entry<String, JsonElement> e : target.entrySet()) {
                merged.add(e.getKey(), e.getValue());
            }
            for (Map.Entry<String, JsonElement> e : n.entrySet()) {
                if (!"$ref".equals(e.getKey())) {
                    merged.add(e.getKey(), e.getValue());
                }
            }
            n = merged;
        }
        return n;
    }

    private static List<String> keys(JsonObject o) {
        List<String> out = new ArrayList<String>();
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            out.add(e.getKey());
        }
        return out;
    }

    // -----------------------------------------------------------------
    // The table against the schema — the staleness guard
    // -----------------------------------------------------------------

    @Test
    @DisplayName("the mod ids are the registry's, in the registry's order")
    void idsMatch() {
        // Order, not just membership: `modIds()` is what `Loadout.materialise` and
        // `LoadoutDiff` iterate, and `mods.json`'s order is a stated decision (HUD mods first,
        // then gameplay, appended never inserted) rather than an accident. The count is read
        // off the schema rather than written here, so wave 2's nine new mods do not need this
        // line edited to keep meaning something.
        assertEquals(keys(registry()), ModRegistry.modIds());
    }

    @Test
    @DisplayName("every mod's kind, category and label match mods.json")
    void classificationMatches() {
        for (Map.Entry<String, JsonElement> e : registry().entrySet()) {
            String id = e.getKey();
            JsonObject row = e.getValue().getAsJsonObject();
            ModRegistry.Kind kind = ModRegistry.kind(id);
            ModRegistry.Category category = ModRegistry.category(id);
            assertNotNull(kind, id + " has no kind");
            assertNotNull(category, id + " has no category");
            assertEquals(row.get("kind").getAsString(), kind.name().toLowerCase(Locale.ROOT),
                    id + " kind");
            assertEquals(row.get("category").getAsString(),
                    category.name().toLowerCase(Locale.ROOT), id + " category");
            // The frames read "FPS display", "CPS counter", "Ping display"; the registry is the
            // one place that copy lives, so the mod carries it rather than the UI overriding it.
            assertEquals(row.get("label").getAsString(), ModRegistry.label(id), id + " label");
            assertTrue(ModRegistry.isMod(id), id + " is not a mod");
            assertEquals("hud".equals(row.get("kind").getAsString()), ModRegistry.isHud(id),
                    id + " isHud");
            assertEquals("gameplay".equals(row.get("kind").getAsString()),
                    ModRegistry.isGameplay(id), id + " isGameplay");
        }
    }

    @Test
    @DisplayName("every mod carries exactly its sub-schema's setting keys, in order")
    void settingKeysMatch() {
        // Both directions, and order too. A *missing* key is the quiet failure the generator
        // exists to remove: `clamp` answers null for an unknown key, so the page draws the
        // control, the player drags it and the value springs back. An *extra* key is the
        // mirror image — Java would happily store something `void-loadout` then refuses to
        // parse, two processes away from here.
        JsonObject schema = schema();
        for (Map.Entry<String, JsonElement> e : registry().entrySet()) {
            String id = e.getKey();
            assertEquals(keys(settingsOf(schema, id)),
                    new ArrayList<String>(ModRegistry.settingKeys(id)),
                    id + " setting keys");
        }
    }

    @Test
    @DisplayName("every factory default matches mods.json, and there are no others")
    void defaultsMatch() {
        for (Map.Entry<String, JsonElement> e : registry().entrySet()) {
            String id = e.getKey();
            JsonObject expected = e.getValue().getAsJsonObject().getAsJsonObject("defaults");
            JsonObject ours = ModRegistry.defaults(id);
            assertEquals(keys(expected), keys(ours), id + " defaults keys");
            for (Map.Entry<String, JsonElement> d : expected.entrySet()) {
                JsonElement mine = ours.get(d.getKey());
                assertNotNull(mine, id + "." + d.getKey() + " is missing from ModRegistry");
                if (d.getValue().getAsJsonPrimitive().isNumber()) {
                    assertEquals(d.getValue().getAsDouble(), mine.getAsDouble(), 1e-9,
                            id + "." + d.getKey());
                } else {
                    assertEquals(d.getValue(), mine, id + "." + d.getKey());
                }
                // `defaults()` builds a fresh object per call; `defaultOf()` reads one key.
                // They are two paths to the same descriptor and must not disagree.
                assertEquals(mine, ModRegistry.defaultOf(id, d.getKey()),
                        id + "." + d.getKey() + ": defaults() and defaultOf() disagree");
            }
        }
    }

    @Test
    @DisplayName("every clamp bound, enum and string pattern comes from the sub-schema")
    void clampDescriptorsMatchTheSubSchemas() {
        // The heart of it. The old suite checked ids, kinds, categories, labels and defaults —
        // but a `maximum` transcribed as 5 instead of 4 passed all of those, and it is the
        // worse bug: nothing looks broken, and a value the schema forbids is clamped straight
        // into a stored loadout that `void-loadout` then refuses to parse.
        //
        // Nothing here reads ModRegistry's private tables. It probes `clamp` from outside with
        // values chosen from the schema, which is the only way to see a descriptor and is also
        // exactly how the bridge sees it.
        JsonObject schema = schema();
        String colorPattern = schema.getAsJsonObject("definitions")
                .getAsJsonObject("hex_color").get("pattern").getAsString();
        String keybindPattern = schema.getAsJsonObject("definitions")
                .getAsJsonObject("keybind").get("pattern").getAsString();

        int checked = 0;
        for (Map.Entry<String, JsonElement> me : registry().entrySet()) {
            String id = me.getKey();
            JsonObject props = settingsOf(schema, id);
            for (Map.Entry<String, JsonElement> pe : props.entrySet()) {
                String key = pe.getKey();
                String where = id + "." + key;
                JsonObject p = resolve(schema, pe.getValue().getAsJsonObject());
                String type = p.has("type") ? p.get("type").getAsString() : null;
                checked++;

                if (p.has("enum")) {
                    // Every member accepted, anything else refused. A missing member is a
                    // control the player can move but not save.
                    for (JsonElement v : p.getAsJsonArray("enum")) {
                        String member = v.getAsString();
                        JsonElement got = ModRegistry.clamp(id, key, new JsonPrimitive(member));
                        assertNotNull(got, where + " rejects its own enum member " + member);
                        assertEquals(member, got.getAsString(), where);
                    }
                    assertNull(ModRegistry.clamp(id, key, new JsonPrimitive("not-a-member")),
                            where + " accepts a value outside its enum");
                } else if ("boolean".equals(type)) {
                    assertNotNull(ModRegistry.clamp(id, key, new JsonPrimitive(Boolean.TRUE)),
                            where + " rejects true");
                    assertNull(ModRegistry.clamp(id, key, new JsonPrimitive("yes")),
                            where + ": a bool is not a string");
                } else if ("integer".equals(type) || "number".equals(type)) {
                    double min = p.get("minimum").getAsDouble();
                    double max = p.get("maximum").getAsDouble();
                    JsonElement hi = ModRegistry.clamp(id, key,
                            new JsonPrimitive(Double.valueOf(max + 1000)));
                    JsonElement lo = ModRegistry.clamp(id, key,
                            new JsonPrimitive(Double.valueOf(min - 1000)));
                    assertNotNull(hi, where + " rejected a number");
                    assertNotNull(lo, where + " rejected a number");
                    assertEquals(max, hi.getAsDouble(), 1e-9, where + " maximum");
                    assertEquals(min, lo.getAsDouble(), 1e-9, where + " minimum");
                    if ("integer".equals(type)) {
                        // An `integer` property must come back whole, or the loadout carries a
                        // 1.5 where the schema promised an integer.
                        JsonElement mid = ModRegistry.clamp(id, key,
                                new JsonPrimitive(Double.valueOf((min + max) / 2 + 0.5)));
                        assertNotNull(mid, where + " rejected a number");
                        assertEquals(-1, mid.getAsString().indexOf('.'),
                                where + " is an integer but clamped to " + mid);
                    }
                } else if ("string".equals(type) && p.has("pattern")) {
                    // COLOR and KEYBIND are both "a string matching a regex"; the generator
                    // tells them apart by the pattern itself, so the test does too rather than
                    // by the property's name — which is `keybind` on one mod and `key` on
                    // another.
                    String pattern = p.get("pattern").getAsString();
                    if (colorPattern.equals(pattern)) {
                        assertNotNull(ModRegistry.clamp(id, key, new JsonPrimitive("#0a1B2c")),
                                where + " rejects a #RRGGBB colour");
                        assertNotNull(ModRegistry.clamp(id, key, new JsonPrimitive("#0A1B2C3D")),
                                where + " rejects a #RRGGBBAA colour");
                        assertNull(ModRegistry.clamp(id, key, new JsonPrimitive("red")),
                                where + " accepts a non-colour");
                    } else if (keybindPattern.equals(pattern)) {
                        JsonElement got = ModRegistry.clamp(id, key, new JsonPrimitive("rshift"));
                        assertNotNull(got, where + " rejects a valid key name");
                        assertEquals("RSHIFT", got.getAsString(),
                                where + ": keybinds are stored upper case (LWJGL 2 names)");
                        assertNull(ModRegistry.clamp(id, key, new JsonPrimitive("NOT_A_KEY")),
                                where + " accepts a key name that does not exist");
                    } else {
                        assertTrue(false, where + ": string pattern is neither hex_color's nor "
                                + "keybind's — ModRegistry.Type has no arm for it");
                    }
                } else {
                    assertTrue(false, where + ": no ModRegistry.Type covers " + p);
                }
            }
        }
        assertTrue(checked > 0, "no settings were checked — mods.json did not load");
    }

    @Test
    @DisplayName("every registry default survives its own clamp untouched")
    void defaultsAreInRange() {
        // Machinery, not transcription: it proves the descriptor a value was generated from and
        // the descriptor it is validated against are the same one. A default outside its own
        // bound would be clamped on the first write and the mod would silently change settings
        // it was never asked to change.
        for (String id : ModRegistry.modIds()) {
            JsonObject defaults = ModRegistry.defaults(id);
            for (Map.Entry<String, JsonElement> e : defaults.entrySet()) {
                JsonElement clamped = ModRegistry.clamp(id, e.getKey(), e.getValue());
                assertNotNull(clamped, id + "." + e.getKey() + " was rejected by its own clamp");
                if (e.getValue().getAsJsonPrimitive().isNumber()) {
                    assertEquals(e.getValue().getAsDouble(), clamped.getAsDouble(), 1e-9,
                            id + "." + e.getKey());
                } else {
                    assertEquals(e.getValue(), clamped, id + "." + e.getKey());
                }
            }
        }
    }

    @Test
    @DisplayName("the hud and gameplay splits match mods.json")
    void splitsMatch() {
        JsonObject definitions = schema().getAsJsonObject("definitions");
        for (JsonElement id : definitions.getAsJsonObject("hud_mod_id").getAsJsonArray("enum")) {
            assertTrue(ModRegistry.isHud(id.getAsString()), id + " should be a HUD mod");
        }
        for (JsonElement id
                : definitions.getAsJsonObject("gameplay_mod_id").getAsJsonArray("enum")) {
            assertTrue(ModRegistry.isGameplay(id.getAsString()),
                    id + " should be a gameplay mod");
        }
        for (JsonElement id : definitions.getAsJsonObject("mod_id").getAsJsonArray("enum")) {
            assertTrue(ModRegistry.isMod(id.getAsString()), id + " should be a known mod");
        }
    }

    @Test
    @DisplayName("the shared HUD chrome block reaches every HUD mod and no gameplay mod")
    void hudChromeIsUniversal() {
        // `background`, `border`, `text_shadow` and `padding` are declared once, in
        // `schema/mods/_shared.json#/hud`, and `schema/build.mjs` merges them into all eight
        // HUD mods. The point of a shared block is that adding the ninth HUD widget cannot
        // forget it, so the invariant is worth stating on this side too: a HUD mod that has
        // lost its chrome draws un-styleable over arbitrary game pixels, which is the exact
        // problem the block was added to solve.
        List<String> chrome = Arrays.asList("background", "border", "text_shadow", "padding");
        for (String id : ModRegistry.modIds()) {
            Set<String> settings = new LinkedHashSet<String>(ModRegistry.settingKeys(id));
            boolean hud = ModRegistry.isHud(id);
            for (String key : chrome) {
                assertEquals(hud, settings.contains(key),
                        id + (hud ? " is missing chrome key " : " should not carry chrome key ")
                                + key);
            }
            // `scale` and `opacity` are the other half of the HUD block; a gameplay mod draws
            // nothing of its own and has neither.
            assertEquals(hud, settings.contains("scale"), id + " scale");
            assertEquals(hud, settings.contains("opacity"), id + " opacity");
            assertTrue(settings.contains("on"), id + " has no `on`");
        }
    }

    @Test
    @DisplayName("category is not a restatement of kind")
    void categoryIsNotKind() {
        // If these ever agreed for all 13, `category` would be dead weight and the panel could
        // filter on `kind`. Crosshair and Zoom are the two that prove they differ. This is an
        // assertion about the *design*, not about the table: it is pinned by name deliberately,
        // so that a schema change collapsing the distinction has to argue with a test rather
        // than propagate silently through a generator.
        assertEquals(ModRegistry.Kind.GAMEPLAY, ModRegistry.kind("crosshair"));
        assertEquals(ModRegistry.Category.VISUAL, ModRegistry.category("crosshair"));
        assertEquals(ModRegistry.Kind.GAMEPLAY, ModRegistry.kind("zoom"));
        assertEquals(ModRegistry.Category.UTILITY, ModRegistry.category("zoom"));
    }

    @Test
    @DisplayName("the watermark is a HUD mod with no colour to set")
    void watermarkHasNoColour() {
        // Kept from the hand-transcribed days, and still worth keeping, because it is the one
        // thing the generated table cannot state: an *absence*. `settingKeysMatch` proves the
        // watermark carries what the schema lists; only a named test proves the schema was
        // right to leave `color` out — the mark is artwork with its own palette, not a tintable
        // readout (`design/quiet-cell-system.md` §1), and `clamp` answering null is what keeps
        // a stray write out of a stored loadout.
        assertEquals(ModRegistry.Kind.HUD, ModRegistry.kind("watermark"));
        assertEquals(ModRegistry.Category.VISUAL, ModRegistry.category("watermark"));
        assertTrue(ModRegistry.isHud("watermark"), "setHud and loadout.hud must accept it");
        assertTrue(!ModRegistry.settingKeys("watermark").contains("color"),
                "the watermark must not gain a colour control");
        assertNull(ModRegistry.clamp("watermark", "color", new JsonPrimitive("#FF0000")));
    }

    // -----------------------------------------------------------------
    // The hand-written machinery — not generated, not covered by --check
    // -----------------------------------------------------------------

    @Test
    @DisplayName("clamp refuses what it cannot store rather than substituting a default")
    void clampRefusesTheUnusable() {
        // `bridge.json#/definitions/setModSetting_returns`: null means nothing was stored and
        // the caller keeps what it had. Returning a default instead would silently overwrite a
        // player's setting with the factory value on any malformed write.
        assertNull(ModRegistry.clamp("not_a_mod", "on", new JsonPrimitive(Boolean.TRUE)),
                "an unknown mod");
        assertNull(ModRegistry.clamp("fps", "not_a_key", new JsonPrimitive(Boolean.TRUE)),
                "an unknown key");
        assertNull(ModRegistry.clamp("fps", "on", null), "a Java null");
        assertNull(ModRegistry.clamp("fps", "on", ModRegistry.nullValue()), "a JSON null");
        assertNull(ModRegistry.clamp("fps", "on", new com.google.gson.JsonObject()),
                "an object where a primitive belongs");
        assertNull(ModRegistry.clamp("fps", "scale", new JsonPrimitive("1.5")),
                "a number as a string");
        assertNull(ModRegistry.clamp("fps", "scale", new JsonPrimitive(Double.valueOf(Double.NaN))),
                "NaN, which would clamp to itself and poison the loadout");
        assertTrue(ModRegistry.nullValue().isJsonNull(), "nullValue is JsonNull");
    }

    @Test
    @DisplayName("an unknown mod id answers null everywhere rather than throwing")
    void unknownModIsAnswerNotAnException() {
        // The bridge passes ids straight through from the page, so every accessor is reachable
        // with a string the registry has never heard of. §6.5 says Java is authoritative and
        // answers; it does not say Java throws into a WebView callback.
        assertTrue(!ModRegistry.isMod("not_a_mod"));
        assertTrue(!ModRegistry.isHud("not_a_mod"));
        assertTrue(!ModRegistry.isGameplay("not_a_mod"));
        assertNull(ModRegistry.kind("not_a_mod"));
        assertNull(ModRegistry.category("not_a_mod"));
        assertNull(ModRegistry.label("not_a_mod"));
        assertNull(ModRegistry.defaultOf("not_a_mod", "on"));
        assertTrue(ModRegistry.settingKeys("not_a_mod").isEmpty());
        assertEquals(0, ModRegistry.defaults("not_a_mod").entrySet().size());
    }

    @Test
    @DisplayName("clamp narrows numbers to the descriptor's bounds and rounds integers")
    void clampNumbers() {
        // Spot checks with the arithmetic written out, as a reader's sanity check on the
        // schema-driven sweep above: if `clampDescriptorsMatchTheSubSchemas` ever passes
        // vacuously — a resolve that quietly returns an empty object, say — these still fail.
        assertEquals(4.0, ModRegistry.clamp("watermark", "scale",
                new JsonPrimitive(Integer.valueOf(99))).getAsDouble(), 1e-9);
        assertEquals(0.25, ModRegistry.clamp("watermark", "scale",
                new JsonPrimitive(Integer.valueOf(0))).getAsDouble(), 1e-9);
        assertEquals(1.0, ModRegistry.clamp("watermark", "opacity",
                new JsonPrimitive(Integer.valueOf(7))).getAsDouble(), 1e-9);
        assertEquals(0.0, ModRegistry.clamp("watermark", "opacity",
                new JsonPrimitive(Integer.valueOf(-1))).getAsDouble(), 1e-9);
        assertEquals(0.9, ModRegistry.defaultOf("watermark", "opacity").getAsDouble(), 1e-9);

        // `corner_radius` is an integer 0..20: a fractional write rounds rather than being
        // refused, because a slider dragged through a scaled UI produces fractions and
        // refusing them would make the control stick.
        assertEquals("9", ModRegistry.clamp("keystrokes", "corner_radius",
                new JsonPrimitive(Double.valueOf(8.7))).getAsString());
        assertEquals("20", ModRegistry.clamp("keystrokes", "corner_radius",
                new JsonPrimitive(Double.valueOf(1000))).getAsString());
    }

    @Test
    @DisplayName("enum values round-trip and a near miss is refused")
    void enumRoundTrip() {
        for (String style : new String[] {"full", "mark", "word"}) {
            assertEquals(style, ModRegistry.clamp("watermark", "style",
                    new JsonPrimitive(style)).getAsString());
        }
        // Near misses, not nonsense: the values a page would plausibly send if it drifted from
        // the schema. Each must be refused so the caller keeps the style the page can draw.
        assertNull(ModRegistry.clamp("watermark", "style", new JsonPrimitive("wordmark")));
        assertNull(ModRegistry.clamp("watermark", "style", new JsonPrimitive("FULL")));
        assertNull(ModRegistry.clamp("crosshair", "style", new JsonPrimitive("tshape")),
                "the member is `t_shape`");
        assertNull(ModRegistry.clamp("watermark", "on", new JsonPrimitive("yes")),
                "a bool is not a string");
    }

    @Test
    @DisplayName("keybinds are stored as upper-case LWJGL 2 names")
    void keybindsAreNormalised() {
        // `openKeybindCapture` returns what `Keyboard.getKeyName` produced, but the page can
        // also write one straight through `setModSetting`, and the schema's pattern is
        // upper-case only. Normalising here means the stored loadout matches the schema
        // whichever path the value took.
        assertEquals("C", ModRegistry.clamp("zoom", "key", new JsonPrimitive("c")).getAsString());
        assertEquals("MOUSE3",
                ModRegistry.clamp("zoom", "key", new JsonPrimitive("mouse3")).getAsString());
        assertEquals("NONE",
                ModRegistry.clamp("keystrokes", "keybind", new JsonPrimitive("none")).getAsString());
        assertNull(ModRegistry.clamp("zoom", "key", new JsonPrimitive("")));
        assertNull(ModRegistry.clamp("zoom", "key", new JsonPrimitive("MOUSE9")));
    }

    @Test
    @DisplayName("defaults() hands out a fresh object every time")
    void defaultsAreNotShared() {
        // `Loadout.materialise` mutates what it gets back. If the registry handed out a shared
        // object, one loadout's settings would leak into every other loadout and into the
        // factory defaults themselves.
        JsonObject first = ModRegistry.defaults("fps");
        first.add("scale", new JsonPrimitive(Double.valueOf(3)));
        assertEquals(1.0, ModRegistry.defaults("fps").get("scale").getAsDouble(), 1e-9);
        assertEquals(1.0, ModRegistry.defaultOf("fps", "scale").getAsDouble(), 1e-9);
    }
}
