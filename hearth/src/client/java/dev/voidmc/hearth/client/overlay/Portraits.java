package dev.voidmc.hearth.client.overlay;

import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.npc.look.FolkLook;
import dev.voidmc.hearth.npc.look.Portrait;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Residents' 16×16 portraits for hosts (docs/OVERLAY.md {@code portrait}): cut from each folk's own composed skin
 * ({@link Portrait}), encoded once per look as a PNG data URL and kept (least recently used out past {@link #MAX}).
 * Client thread.
 */
public final class Portraits {
	static final int MAX = 256;
	private static final Map<Long, String> CACHE = new LinkedHashMap<>(64, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, String> eldest) {
			return size() > MAX;
		}
	};

	private Portraits() {
	}

	/** A look's portrait as {@code data:image/png;base64,...}. */
	public static String of(FolkLook look) {
		return CACHE.computeIfAbsent(look.key(), k -> Portrait.dataUrl(Portrait.of(look)));
	}

	/** The portrait of the folk with this entity id in the client's level, or null (not a folk, not loaded). */
	public static @Nullable String ofEntity(int entity) {
		FolkLook look = look(entity);
		return look == null ? null : of(look);
	}

	/** A stable key for the portrait (the look's), so a host can cache images by it; empty when there is none. */
	public static String keyOf(int entity) {
		FolkLook look = look(entity);
		return look == null ? "" : String.format(Locale.ROOT, "%016x", look.key());
	}

	private static @Nullable FolkLook look(int entity) {
		Minecraft mc = Minecraft.getInstance();
		if (entity < 0 || mc.level == null) {
			return null;
		}
		Entity e = mc.level.getEntity(entity);
		return e instanceof Folk folk ? folk.look() : null;
	}

	public static void clear() {
		CACHE.clear();
	}

	static int size() {
		return CACHE.size();
	}
}
