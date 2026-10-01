package dev.voidmc.expanse.world.terrain;

import dev.voidmc.expanse.registry.ExpanseBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * What grows in the deep caverns, in the manner of vanilla's lush caves: moss on the floors of the halls
 * and along the river ledges, glowcaps in clumps near the water, and glow-berry vines hanging from the
 * roofs, which with the glowcaps are what light the dark. Patches follow a slow noise so the growth
 * comes in drifts, thickest by the rivers.
 */
final class CavernLife {
	private static final BlockState MOSS = Blocks.MOSS_BLOCK.defaultBlockState();
	private static final BlockState MOSS_CARPET = Blocks.MOSS_CARPET.defaultBlockState();

	private CavernLife() {
	}

	static void decorate(WorldGenLevel level, ChunkAccess chunk, CavernModel caverns) {
		ChunkPos pos = chunk.getPos();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int lz = 0; lz < 16; lz++) {
			for (int lx = 0; lx < 16; lx++) {
				int x = pos.getMinBlockX() + lx;
				int z = pos.getMinBlockZ() + lz;
				CavernModel.Info c = caverns.info(x, z);
				boolean ledge = c.river && !c.riverFalls && c.riverDist >= c.riverHalfWidth && c.riverDist < c.riverHalfWidth + 3;
				boolean hall = c.hall && c.hallEdge > 2;
				boolean tunnel = c.tunnel && c.tunnelDist < c.tunnelHalfWidth - 1;
				if (!ledge && !hall && !tunnel) {
					continue;
				}
				// thickest by the water, thinnest along the dry tunnels
				float near = ledge ? 1.0F : hall ? 0.0F : -0.25F;
				int from = ledge ? c.waterTop() - 2 : hall ? Math.round(c.floor) - 4 : Math.round(c.tunnelFloor) - 4;
				int to = ledge ? c.waterTop() + 4 : hall ? Math.round(c.floor) + 8 : Math.round(c.tunnelFloor) + 6;
				long h = PlateMap.mix(0x4C494645L, x, z);
				double drift = PlateMap.unit(h) * 0.35 + 0.65 * (0.5 + 0.5 * Math.sin(x * 0.07 + 1.3 * Math.cos(z * 0.05)) * Math.cos(z * 0.06));
				// the floor: the first air above solid ground, scanning up
				for (int y = from; y < to; y++) {
					BlockState below = level.getBlockState(p.set(x, y - 1, z));
					BlockState here = level.getBlockState(p.set(x, y, z));
					if (!here.isAir() || !below.isSolidRender() || below.is(Blocks.WATER)) {
						continue;
					}
					if (drift + near * 0.3 > 0.55) {
						level.setBlock(p.set(x, y - 1, z), MOSS, 2);
						double roll = PlateMap.unit(h >>> 21);
						if (roll < 0.10 + near * 0.12) {
							BlockState glowcap = ExpanseBlocks.GLOWCAP.defaultBlockState();
							if (glowcap.canSurvive(level, p.set(x, y, z))) {
								level.setBlock(p, glowcap, 2);
							}
						} else if (roll < 0.45) {
							level.setBlock(p.set(x, y, z), MOSS_CARPET, 2);
						}
					}
					break;
				}
				// the roof: glow-berry vines here and there
				if (PlateMap.unit(h >>> 42) < (ledge ? 0.06 : hall ? 0.025 : 0.012)) {
					int top = ledge ? Math.round(c.tunnelRoof) + 6 : hall ? Math.round(c.roof) + 6 : Math.round(c.tunnelFloor + c.tunnelHeight) + 6;
					for (int y = to; y < top; y++) {
						if (level.getBlockState(p.set(x, y, z)).isAir() && level.getBlockState(p.set(x, y + 1, z)).isSolidRender()) {
							int length = 2 + (int) (PlateMap.unit(h >>> 13) * 6);
							for (int k = 0; k < length; k++) {
								BlockPos at = p.set(x, y - k, z).immutable();
								if (!level.getBlockState(at).isAir()) {
									break;
								}
								boolean tip = k == length - 1 || !level.getBlockState(at.below()).isAir();
								BlockState vine = (tip ? Blocks.CAVE_VINES : Blocks.CAVE_VINES_PLANT).defaultBlockState()
									.setValue(CaveVines.BERRIES, PlateMap.unit(h >>> (k + 3)) < 0.4);
								level.setBlock(at, vine, 2);
								if (tip) {
									break;
								}
							}
							break;
						}
					}
				}
			}
		}
	}
}
