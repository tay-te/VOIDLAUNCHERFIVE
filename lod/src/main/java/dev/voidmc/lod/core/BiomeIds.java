package dev.voidmc.lod.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Biomes as small ints, by their registry name ({@code minecraft:plains}): what a sample and a surface carry, so the
 * far view can tell which biome a cell is (VOID Atlas names regions by it). Names, not the session's registry ids, so
 * what is saved outlives the session. -1 is no biome known. Thread-safe.
 */
public final class BiomeIds {
	private static final ConcurrentHashMap<String, Integer> IDS = new ConcurrentHashMap<>();
	private static final List<String> NAMES = new ArrayList<>();

	private BiomeIds() {
	}

	/** The id of biome {@code name}, or -1 for null. */
	public static int id(String name) {
		if (name == null) {
			return -1;
		}
		Integer id = IDS.get(name);
		if (id != null) {
			return id;
		}
		synchronized (NAMES) {
			return IDS.computeIfAbsent(name, n -> {
				NAMES.add(n);
				return NAMES.size() - 1;
			});
		}
	}

	/** The name of biome {@code id}, or null for -1 or an id never handed out. */
	public static String name(int id) {
		synchronized (NAMES) {
			return id >= 0 && id < NAMES.size() ? NAMES.get(id) : null;
		}
	}
}
