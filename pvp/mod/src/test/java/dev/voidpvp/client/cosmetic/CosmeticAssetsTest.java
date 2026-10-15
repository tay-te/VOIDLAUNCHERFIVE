package dev.voidpvp.client.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The catalog against {@code schema/cosmetics.json}, and every bundled asset through the same
 * parsers the game uses. {@code gen-cosmetics.mjs --check} proves the committed files are their
 * sources; this proves the runtime can read every one of them.
 */
class CosmeticAssetsTest {

    private static JsonObject catalogJson() throws Exception {
        String dir = System.getProperty("void.schema.dir", "../schema");
        try (InputStreamReader r = new InputStreamReader(new FileInputStream(new File(dir, "cosmetics.json")),
                StandardCharsets.UTF_8)) {
            return new JsonParser().parse(r).getAsJsonObject();
        }
    }

    @Test
    @DisplayName("the generated Java catalog is schema/cosmetics.json, item for item")
    void catalogMatchesSchema() throws Exception {
        JsonObject doc = catalogJson();
        assertEquals(doc.get("version").getAsInt(), CosmeticCatalog.VERSION);
        JsonArray items = doc.getAsJsonArray("items");
        assertEquals(items.size(), CosmeticCatalog.all().size());
        for (JsonElement e : items) {
            JsonObject o = e.getAsJsonObject();
            CosmeticCatalog.Item item = CosmeticCatalog.get(o.get("id").getAsString());
            assertNotNull(item, o.get("id").getAsString());
            assertEquals(o.get("kind").getAsString(), item.kind.wire());
            assertEquals(o.get("name").getAsString(), item.name);
            assertEquals(o.get("price_cents").getAsInt(), item.priceCents);
            assertEquals(o.has("tintable") && o.get("tintable").getAsBoolean(), item.tintable);
            assertEquals(o.get("availability").getAsString(), item.availability);
        }
    }

    @Test
    @DisplayName("the launch lineup is complete")
    void lineup() {
        assertEquals(6, CosmeticCatalog.ofKind(CosmeticCatalog.Kind.CAPE).size());
        assertEquals(3, CosmeticCatalog.ofKind(CosmeticCatalog.Kind.BACK).size());
        assertEquals(3, CosmeticCatalog.ofKind(CosmeticCatalog.Kind.HEAD).size());
        assertEquals(2, CosmeticCatalog.ofKind(CosmeticCatalog.Kind.HALO).size());
        assertEquals(2, CosmeticCatalog.ofKind(CosmeticCatalog.Kind.FACE).size());
        assertEquals(6, CosmeticCatalog.ofKind(CosmeticCatalog.Kind.EMOTE).size());
        assertEquals(4, CosmeticCatalog.ofKind(CosmeticCatalog.Kind.TRAIL).size());
        assertEquals(4, CosmeticCatalog.ofKind(CosmeticCatalog.Kind.BADGE).size());
        assertNotNull(CosmeticCatalog.get("void-badge-founder"));
        assertTrue(CosmeticCatalog.get("void-badge-player").free());
    }

    @Test
    @DisplayName("every item's bundled assets load through the runtime parsers")
    void everyAssetParses() {
        List<String> failures = new ArrayList<String>();
        for (CosmeticCatalog.Item item : CosmeticCatalog.all()) {
            try {
                switch (item.kind) {
                    case CAPE:
                        CapeStyle c = CosmeticAssets.cape(item.id);
                        assertNotNull(c, item.id + " cape.json");
                        assertTrue(c.frames >= 1);
                        assertTrue(CosmeticAssets.has(item.id, "texture.png"));
                        break;
                    case BACK:
                    case HEAD:
                    case FACE:
                    case HALO: {
                        CosmeticModel m = CosmeticAssets.model(item.id);
                        assertNotNull(m, item.id + " model.json");
                        assertTrue(m.bones.length > 0);
                        int cubes = 0;
                        for (CosmeticModel.Bone b : m.bones) {
                            cubes += b.cubes.length;
                            for (CosmeticModel.Cube cube : b.cubes) {
                                for (float[] uv : cube.uv) {
                                    if (uv == null) {
                                        continue;
                                    }
                                    for (int k = 0; k < 4; k++) {
                                        float lim = k % 2 == 0 ? m.texW : m.texH;
                                        assertTrue(uv[k] >= 0 && uv[k] <= lim, item.id + " uv out of range");
                                    }
                                }
                            }
                        }
                        assertTrue(cubes > 0, item.id + " has no geometry");
                        if (item.kind == CosmeticCatalog.Kind.BACK && "wings".equals(m.procedural)) {
                            for (String bone : new String[] {"wing_r", "wing_r_tip", "wing_l", "wing_l_tip"}) {
                                assertTrue(m.boneIndex(bone) >= 0, item.id + " lacks " + bone);
                            }
                        }
                        String expect = item.kind == CosmeticCatalog.Kind.BACK ? "body"
                                : item.kind == CosmeticCatalog.Kind.HALO ? "halo" : "head";
                        assertEquals(expect, m.attach, item.id + " attaches to the wrong part");
                        break;
                    }
                    case TRAIL:
                        TrailStyle t = CosmeticAssets.trail(item.id);
                        assertNotNull(t, item.id + " trail.json");
                        assertTrue(t.cap > 0 && t.cap <= 64 && t.rate > 0 && t.life > 0);
                        assertTrue(CosmeticAssets.has(item.id, "sprite.png"));
                        break;
                    case BADGE:
                        assertNotNull(CosmeticAssets.badge(item.id), item.id + " badge.json");
                        assertTrue(CosmeticAssets.has(item.id, "icon.png"));
                        break;
                    case EMOTE: {
                        EmoteClip clip = CosmeticAssets.emote(item.id);
                        assertNotNull(clip, item.id + " emote.json");
                        assertTrue(clip.length > 0.5f && clip.length < 10f);
                        assertEquals(item.loop, clip.loop, item.id + " loop flag disagrees with the catalog");
                        break;
                    }
                    default:
                        break;
                }
            } catch (AssertionError e) {
                failures.add(e.getMessage());
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    @Test
    @DisplayName("one-shot emotes end where they began, so the blend out never jumps")
    void emotesReturnToRest() {
        float[] v = new float[3];
        for (CosmeticCatalog.Item item : CosmeticCatalog.ofKind(CosmeticCatalog.Kind.EMOTE)) {
            EmoteClip clip = CosmeticAssets.emote(item.id);
            if (clip.loop) {
                continue;
            }
            for (int part = 0; part < EmoteClip.PARTS; part++) {
                clip.rotation(part, clip.length, v, 0);
                for (float f : v) {
                    assertEquals(0, f, 1e-3, item.id + " part " + part + " does not return to rest");
                }
            }
        }
    }
}
