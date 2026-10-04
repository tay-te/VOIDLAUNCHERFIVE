package dev.voidmc.hearth.quest.service;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Counts the fires burning in a box of loaded chunks, for the bucket brigade. Chunk sections whose palette holds no fire
 * are skipped without reading a block, so a burning house costs a few thousand reads at most and a quiet one almost
 * nothing. Never loads a chunk.
 */
public final class Fires {
	private Fires() {
	}

	/** Fire blocks in the box, or -1 if any of its chunks is not loaded. */
	public static int count(ServerLevel level, BlockPos min, BlockPos max) {
		int minY = Math.max(level.getMinY(), Math.min(min.getY(), max.getY()));
		int maxY = Math.min(level.getMaxY(), Math.max(min.getY(), max.getY()));
		int x0 = Math.min(min.getX(), max.getX());
		int x1 = Math.max(min.getX(), max.getX());
		int z0 = Math.min(min.getZ(), max.getZ());
		int z1 = Math.max(min.getZ(), max.getZ());
		int fires = 0;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int cx = SectionPos.blockToSectionCoord(x0); cx <= SectionPos.blockToSectionCoord(x1); cx++) {
			for (int cz = SectionPos.blockToSectionCoord(z0); cz <= SectionPos.blockToSectionCoord(z1); cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					return -1;
				}
				for (int sy = SectionPos.blockToSectionCoord(minY); sy <= SectionPos.blockToSectionCoord(maxY); sy++) {
					LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sy));
					if (section.hasOnlyAir() || !section.maybeHas(s -> s.is(BlockTags.FIRE))) {
						continue;
					}
					int bx0 = Math.max(x0, cx << 4);
					int bx1 = Math.min(x1, (cx << 4) + 15);
					int bz0 = Math.max(z0, cz << 4);
					int bz1 = Math.min(z1, (cz << 4) + 15);
					int by0 = Math.max(minY, sy << 4);
					int by1 = Math.min(maxY, (sy << 4) + 15);
					for (int x = bx0; x <= bx1; x++) {
						for (int z = bz0; z <= bz1; z++) {
							for (int y = by0; y <= by1; y++) {
								if (section.getBlockState(x & 15, y & 15, z & 15).is(BlockTags.FIRE)) {
									fires++;
								}
							}
						}
					}
				}
			}
		}
		return fires;
	}
}
