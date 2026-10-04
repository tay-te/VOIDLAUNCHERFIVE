package dev.voidmc.hearth.repair;

/** Chunk coordinates packed in a long (x low, z high), as {@code ChunkPos.pack}. */
final class ChunkKeys {
	private ChunkKeys() {
	}

	static long key(int chunkX, int chunkZ) {
		return chunkX & 0xFFFF_FFFFL | (chunkZ & 0xFFFF_FFFFL) << 32;
	}

	static int x(long key) {
		return (int) key;
	}

	static int z(long key) {
		return (int) (key >>> 32);
	}
}
