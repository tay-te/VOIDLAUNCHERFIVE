package dev.voidmc.atlas.core.names;

/** A hash of a world (its key and seed) that is the same every session: what name providers are given to seed from. */
public final class WorldSeed {
	private static final long GAMMA = 0x9E3779B97F4A7C15L;

	private WorldSeed() {
	}

	/** Mixes a world's key (server address or "sp") and seed (0 when the client does not know it). */
	public static long hash(String worldKey, long seed) {
		String key = worldKey == null ? "" : worldKey;
		long h = 0xCBF29CE484222325L;
		for (int i = 0; i < key.length(); i++) {
			h = (h ^ key.charAt(i)) * 0x100000001B3L;
		}
		h = mix(h ^ key.length());
		return mix(h ^ mix(seed + GAMMA));
	}

	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}
}
