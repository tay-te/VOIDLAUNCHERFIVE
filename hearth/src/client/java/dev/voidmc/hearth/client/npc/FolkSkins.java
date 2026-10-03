package dev.voidmc.hearth.client.npc;

import com.mojang.blaze3d.platform.NativeImage;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.npc.look.FolkLook;
import dev.voidmc.hearth.npc.look.SkinComposer;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Composed folk skins as textures, one per distinct look, made on first sight and released when nobody has drawn them
 * for a while (or when the cache is full, least recently used first). Composing is a fraction of a millisecond, so a
 * town walking into view costs about a frame of work spread over the folk that appear.
 *
 * <p>Render thread only.
 */
public final class FolkSkins {
	private static final int MAX = 384;
	/** Ticks a skin may go undrawn before its texture is released. */
	private static final int UNUSED_TICKS = 20 * 45;

	private static final Map<Long, Entry> CACHE = new LinkedHashMap<>(64, 0.75F, true);
	private static long clock;

	private FolkSkins() {
	}

	private static final class Entry {
		final Identifier id;
		long lastUsed;

		Entry(Identifier id) {
			this.id = id;
		}
	}

	/** The texture for a look, composing and uploading it if needed. */
	public static Identifier get(FolkLook look) {
		long key = look.key();
		Entry e = CACHE.get(key);
		if (e == null) {
			Identifier id = Hearth.id("dynamic/folk/" + String.format(Locale.ROOT, "%016x", key));
			int[] pixels = SkinComposer.compose(look);
			NativeImage image = new NativeImage(64, 64, true);
			for (int y = 0; y < 64; y++) {
				for (int x = 0; x < 64; x++) {
					image.setPixel(x, y, pixels[y * 64 + x]);
				}
			}
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, image));
			e = new Entry(id);
			CACHE.put(key, e);
			if (CACHE.size() > MAX) {
				evict(CACHE.size() - MAX);
			}
		}
		e.lastUsed = clock;
		return e.id;
	}

	/** Once per client tick: release textures nobody has drawn lately. */
	public static void tick() {
		clock++;
		if (clock % 100 != 0) {
			return;
		}
		Iterator<Entry> it = CACHE.values().iterator();
		while (it.hasNext()) {
			Entry e = it.next();
			if (clock - e.lastUsed > UNUSED_TICKS) {
				Minecraft.getInstance().getTextureManager().release(e.id);
				it.remove();
			}
		}
	}

	/** Drops every texture (leaving a world). */
	public static void clear() {
		for (Entry e : CACHE.values()) {
			Minecraft.getInstance().getTextureManager().release(e.id);
		}
		CACHE.clear();
	}

	public static int size() {
		return CACHE.size();
	}

	private static void evict(int n) {
		Iterator<Entry> it = CACHE.values().iterator();
		while (n-- > 0 && it.hasNext()) {
			Entry e = it.next();
			Minecraft.getInstance().getTextureManager().release(e.id);
			it.remove();
		}
	}
}
