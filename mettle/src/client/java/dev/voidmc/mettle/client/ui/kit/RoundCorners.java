package dev.voidmc.mettle.client.ui.kit;

import com.mojang.blaze3d.platform.NativeImage;
import dev.voidmc.mettle.Mettle;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Anti-aliased corner masks, made once per radius in screen pixels. 26.3's GUI has no rounded rectangle, and nine-slice
 * sprites only stay crisp at the scale they were drawn for; VOID panels are drawn at native resolution, so the corners
 * are rendered for the exact pixel radius instead. Each texture is a white disc (or ring) of radius r in a 2r × 2r
 * square; the four corners of a shape are its four quadrants, tinted by the blit colour.
 */
final class RoundCorners {
	private static final int SUPERSAMPLE = 4;
	private static final Map<Long, Identifier> CACHE = new HashMap<>();

	private RoundCorners() {
	}

	/** A filled disc. */
	static Identifier disc(int r) {
		return texture(0, r, 0);
	}

	/** A ring of thickness t on the disc's edge (an inside stroke). */
	static Identifier ring(int r, int t) {
		return texture(1, r, t);
	}

	/** Everything outside the disc: lays the surrounding colour over a square image to round it. */
	static Identifier outside(int r) {
		return texture(2, r, 0);
	}

	private static Identifier texture(int kind, int r, int t) {
		long key = ((long) kind << 40) | ((long) r << 20) | t;
		return CACHE.computeIfAbsent(key, k -> {
			int size = 2 * r;
			NativeImage image = new NativeImage(size, size, true);
			for (int y = 0; y < size; y++) {
				for (int x = 0; x < size; x++) {
					float a = switch (kind) {
						case 0 -> coverage(x, y, r);
						case 1 -> coverage(x, y, r) - coverage(x, y, r - t, r);
						default -> 1f - coverage(x, y, r);
					};
					image.setPixel(x, y, (Math.round(Math.clamp(a, 0f, 1f) * 255f) << 24) | 0xFFFFFF);
				}
			}
			Identifier id = Mettle.id("dynamic/corner_" + kind + "_" + r + "_" + t);
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, image));
			return id;
		});
	}

	private static float coverage(int px, int py, int r) {
		return coverage(px, py, r, r);
	}

	/** The share of pixel (px, py) inside a circle of radius {@code radius} centred at (centre, centre). */
	private static float coverage(int px, int py, float radius, int centre) {
		if (radius <= 0) {
			return 0f;
		}
		int inside = 0;
		for (int sy = 0; sy < SUPERSAMPLE; sy++) {
			for (int sx = 0; sx < SUPERSAMPLE; sx++) {
				float dx = px + (sx + 0.5f) / SUPERSAMPLE - centre;
				float dy = py + (sy + 0.5f) / SUPERSAMPLE - centre;
				if (dx * dx + dy * dy <= radius * radius) {
					inside++;
				}
			}
		}
		return inside / (float) (SUPERSAMPLE * SUPERSAMPLE);
	}
}
