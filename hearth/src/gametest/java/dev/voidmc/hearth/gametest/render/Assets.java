package dev.voidmc.hearth.gametest.render;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import org.jspecify.annotations.Nullable;

/**
 * Reads vanilla (and mod) assets for the offline renderer: from the classpath when the client jar is on it (the
 * gametest runtime classpath has it), otherwise from the client jar in Loom's cache ({@code -Dhearth.mcjar=...}
 * overrides the path). Caches JSON and textures.
 */
public final class Assets {
	private static final String DEFAULT_JAR = System.getProperty("user.home") + "/.gradle/caches/fabric-loom/26.3/minecraft-client.jar";
	private final @Nullable ZipFile jar;
	private final Map<String, Object> json = new ConcurrentHashMap<>();
	private final Map<String, Texture> textures = new ConcurrentHashMap<>();
	private static final Object MISSING = new Object();

	public Assets() {
		ZipFile z = null;
		Path p = Path.of(System.getProperty("hearth.mcjar", DEFAULT_JAR));
		if (Files.exists(p)) {
			try {
				z = new ZipFile(p.toFile());
			} catch (IOException e) {
				z = null;
			}
		}
		this.jar = z;
	}

	/** Bytes of an asset ({@code assets/minecraft/...}), or null. */
	public byte @Nullable [] bytes(String path) {
		try (InputStream in = Assets.class.getResourceAsStream("/" + path)) {
			if (in != null) {
				return in.readAllBytes();
			}
		} catch (IOException ignored) {
			// fall through to the jar
		}
		if (this.jar != null) {
			ZipEntry e = this.jar.getEntry(path);
			if (e != null) {
				try (InputStream in = this.jar.getInputStream(e)) {
					return in.readAllBytes();
				} catch (IOException ignored) {
					return null;
				}
			}
		}
		return null;
	}

	public @Nullable JsonObject json(String path) {
		Object o = this.json.computeIfAbsent(path, p -> {
			byte[] b = bytes(p);
			if (b == null) {
				return MISSING;
			}
			return JsonParser.parseString(new String(b, StandardCharsets.UTF_8)).getAsJsonObject();
		});
		return o == MISSING ? null : (JsonObject) o;
	}

	/** A texture by resource id ({@code minecraft:block/stone}); animated strips give their first frame. */
	public Texture texture(String id) {
		return this.textures.computeIfAbsent(id, this::loadTexture);
	}

	private Texture loadTexture(String id) {
		String ns = id.contains(":") ? id.substring(0, id.indexOf(':')) : "minecraft";
		String p = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
		byte[] b = bytes("assets/" + ns + "/textures/" + p + ".png");
		if (b == null) {
			return Texture.MISSING;
		}
		try {
			BufferedImage img = ImageIO.read(new ByteArrayInputStream(b));
			int w = img.getWidth();
			int h = Math.min(img.getHeight(), w);
			int[] argb = new int[w * h];
			img.getRGB(0, 0, w, h, argb, 0, w);
			return new Texture(w, h, argb, id);
		} catch (IOException e) {
			return Texture.MISSING;
		}
	}

	/** A sub-rectangle of a texture (for block-entity textures laid out as atlases). */
	public Texture crop(String id, int x, int y, int w, int h) {
		return this.textures.computeIfAbsent(id + "#" + x + "," + y + "," + w + "," + h, k -> {
			byte[] b = bytes("assets/" + id.substring(0, id.indexOf(':')) + "/textures/" + id.substring(id.indexOf(':') + 1) + ".png");
			if (b == null) {
				return Texture.MISSING;
			}
			try {
				BufferedImage img = ImageIO.read(new ByteArrayInputStream(b));
				int[] argb = new int[w * h];
				img.getRGB(x, y, w, h, argb, 0, w);
				return new Texture(w, h, argb, k);
			} catch (IOException | RuntimeException e) {
				return Texture.MISSING;
			}
		});
	}

	/** Stacks textures vertically into one (top first), all cropped to the first one's width. */
	public Texture stack(String key, Texture... parts) {
		return this.textures.computeIfAbsent(key, k -> {
			int w = parts[0].w();
			int h = 0;
			for (Texture t : parts) {
				h += t.h();
			}
			int[] argb = new int[w * h];
			int y0 = 0;
			for (Texture t : parts) {
				for (int y = 0; y < t.h(); y++) {
					for (int x = 0; x < w; x++) {
						argb[(y0 + y) * w + x] = t.argb()[y * t.w() + Math.min(x, t.w() - 1)];
					}
				}
				y0 += t.h();
			}
			return new Texture(w, h, argb, k);
		});
	}

	/** A texture image. */
	public record Texture(int w, int h, int[] argb, String id) {
		public static final Texture MISSING = missing();

		private static Texture missing() {
			int[] a = new int[256];
			for (int i = 0; i < 256; i++) {
				a[i] = ((i % 16 < 8) ^ (i / 16 < 8)) ? 0xFFF800F8 : 0xFF000000;
			}
			return new Texture(16, 16, a, "missing");
		}

		/** The colour at texture coordinates in 0..16 units (nearest). */
		public int sample(float u, float v) {
			int x = (int) Math.floor(u / 16f * this.w);
			int y = (int) Math.floor(v / 16f * this.h);
			x = Math.clamp(x, 0, this.w - 1);
			y = Math.clamp(y, 0, this.h - 1);
			return this.argb[y * this.w + x];
		}
	}
}
