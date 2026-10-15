package dev.voidpvp.client.cosmetic;

import com.google.gson.JsonObject;
import dev.voidpvp.client.VoidLog;
import dev.voidpvp.client.state.Json;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every cosmetic's bundled assets, loaded on first use and kept for the session.
 *
 * <p>Read straight off the classpath ({@code assets/void/cosmetics/<id>/...}) the way the mod's
 * shaders are, not through Minecraft's resource manager: Legacy Fabric without its API does not
 * put mod jars on the resource pack list, and a cosmetic must never depend on a resource reload.
 * Descriptors (JSON) can load on any thread; textures upload to GL and therefore load only on the
 * render thread, through {@link #texture}.</p>
 *
 * <p>A missing or broken asset is logged once and then remembered as missing, so a bad file costs
 * one line in the log rather than a parse per frame.</p>
 */
public final class CosmeticAssets {

    private static final String ROOT = "assets/void/cosmetics/";
    private static final Object MISSING = new Object();
    private static final Map<String, Object> CACHE = new ConcurrentHashMap<String, Object>();

    /** A GL texture plus how its frames animate. */
    public static final class Tex {
        public final int glId;
        public final int width, height;
        public final int frames;
        public final float fps;

        Tex(int glId, int width, int height, int frames, float fps) {
            this.glId = glId;
            this.width = width;
            this.height = height;
            this.frames = frames;
            this.fps = fps;
        }

        /** The frame to show at {@code seconds}. */
        public int frame(double seconds) {
            if (frames <= 1 || fps <= 0) {
                return 0;
            }
            return (int) ((long) Math.floor(seconds * fps) % frames);
        }
    }

    /** Test seam: where descriptors come from. Replaced by tests to read the source tree. */
    public interface Source {
        InputStream open(String path) throws IOException;
    }

    private static volatile Source source = new Source() {
        @Override
        public InputStream open(String path) {
            return CosmeticAssets.class.getClassLoader().getResourceAsStream(path);
        }
    };

    private CosmeticAssets() {
    }

    static void setSource(Source s) {
        source = s;
        CACHE.clear();
    }

    private static byte[] read(String path) {
        try (InputStream in = source.open(ROOT + path)) {
            if (in == null) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    private static JsonObject json(String path) {
        byte[] b = read(path);
        if (b == null) {
            return null;
        }
        return Json.parseObject(new String(b, StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static <T> T cached(String key, Loader<T> loader) {
        Object v = CACHE.get(key);
        if (v == MISSING) {
            return null;
        }
        if (v != null) {
            return (T) v;
        }
        T loaded = null;
        try {
            loaded = loader.load();
        } catch (RuntimeException e) {
            VoidLog.warn("cosmetic asset " + key + " failed to load: " + e);
        }
        CACHE.put(key, loaded == null ? MISSING : loaded);
        return loaded;
    }

    private interface Loader<T> {
        T load();
    }

    public static CosmeticModel model(final String id) {
        return cached(id + "/model", new Loader<CosmeticModel>() {
            @Override
            public CosmeticModel load() {
                JsonObject o = json(id + "/model.json");
                return o == null ? null : CosmeticModel.parse(o);
            }
        });
    }

    public static EmoteClip emote(final String id) {
        return cached(id + "/emote", new Loader<EmoteClip>() {
            @Override
            public EmoteClip load() {
                JsonObject o = json(id + "/emote.json");
                return o == null ? null : EmoteClip.parse(o);
            }
        });
    }

    public static CapeStyle cape(final String id) {
        return cached(id + "/cape", new Loader<CapeStyle>() {
            @Override
            public CapeStyle load() {
                JsonObject o = json(id + "/cape.json");
                return o == null ? null : CapeStyle.parse(o);
            }
        });
    }

    public static TrailStyle trail(final String id) {
        return cached(id + "/trail", new Loader<TrailStyle>() {
            @Override
            public TrailStyle load() {
                JsonObject o = json(id + "/trail.json");
                return o == null ? null : TrailStyle.parse(o);
            }
        });
    }

    public static JsonObject badge(final String id) {
        return cached(id + "/badge", new Loader<JsonObject>() {
            @Override
            public JsonObject load() {
                return json(id + "/badge.json");
            }
        });
    }

    /** True when this id has a file at {@code name} (cheap: cached). */
    public static boolean has(final String id, final String name) {
        Boolean b = cached(id + "/has/" + name, new Loader<Boolean>() {
            @Override
            public Boolean load() {
                return read(id + "/" + name) != null ? Boolean.TRUE : null;
            }
        });
        return b != null;
    }

    /**
     * The GL texture {@code <id>/<name>}, uploading it on first use. Render thread only.
     *
     * @param frames how many frames are stacked vertically in the image
     */
    public static Tex texture(final String id, final String name, final int frames, final float fps) {
        return cached(id + "/tex/" + name, new Loader<Tex>() {
            @Override
            public Tex load() {
                byte[] b = read(id + "/" + name);
                if (b == null) {
                    return null;
                }
                try {
                    BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(b));
                    if (img == null) {
                        return null;
                    }
                    int gl = Gl.upload(img);
                    return new Tex(gl, img.getWidth(), img.getHeight() / Math.max(1, frames),
                            Math.max(1, frames), fps);
                } catch (IOException e) {
                    return null;
                }
            }
        });
    }

    /** Forgets everything (dev reload). GL textures are leaked deliberately: a dev path only. */
    public static void clear() {
        CACHE.clear();
    }
}
