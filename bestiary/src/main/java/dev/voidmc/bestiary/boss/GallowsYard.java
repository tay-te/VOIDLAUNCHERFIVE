package dev.voidmc.bestiary.boss;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The gallows yard (the lair fallback until Expanse's lair anchors exist, CONTRACTS.md §18.2): a trodden earth yard
 * inside a low broken fieldstone wall with a gate on the south side, a mound on the north with the gallows on it (two
 * dark oak posts, a beam, a hanging chain), and three broken pillars to fight around. Grounded, no glow: two lanterns.
 * Built by {@code /bestiary summon gallows_knight yard}; returns the arena it makes.
 */
public final class GallowsYard {
	/** Arena radius inside the wall. */
	public static final int RADIUS = 15;
	public static final int MOUND_RADIUS = 3;
	/** The mound's centre, blocks north of the yard's centre. */
	public static final int MOUND_OFFSET = 8;

	private GallowsYard() {
	}

	/**
	 * The yard's floor: trodden earth in soft patches (coarse dirt and dirt, gravel where the ground is worn, podzol in the
	 * corners) with a worn path from the gate to the gallows; patches, not a checkerboard.
	 */
	static BlockState ground(int dx, int dz, RandomSource r) {
		double n = Math.sin(dx * 0.37 + 1.3) + Math.cos(dz * 0.31 - 0.4) + Math.sin((dx + dz) * 0.19) * 0.8;
		double m = Math.sin(dx * 0.61 - dz * 0.23 + 2.1) + Math.cos(dz * 0.53 + dx * 0.17);
		boolean path = Math.abs(dx + Math.sin(dz * 0.4) * 0.8) <= 1.2 && dz > -MOUND_OFFSET + MOUND_RADIUS && r.nextFloat() < 0.85F;
		if (path) {
			return Blocks.DIRT_PATH.defaultBlockState();
		}
		if (n > 1.95) {
			return Blocks.GRAVEL.defaultBlockState();
		}
		if (n < -1.6) {
			return Blocks.PODZOL.defaultBlockState();
		}
		return m > 0.4 || r.nextFloat() < 0.08F ? Blocks.DIRT.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
	}

	public static Arena place(ServerLevel level, BlockPos centre) {
		RandomSource r = RandomSource.create(centre.asLong());
		int y = centre.getY();
		int cx = centre.getX(), cz = centre.getZ();
		int wall = RADIUS + 1;
		BlockState[] stone = {Blocks.COBBLESTONE.defaultBlockState(), Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState(),
			Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState()};
		// The yard: clear the air above, a floor of trodden earth, footing below so nothing floats.
		for (int dx = -wall - 1; dx <= wall + 1; dx++) {
			for (int dz = -wall - 1; dz <= wall + 1; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > wall + 1.5) {
					continue;
				}
				for (int dy = 0; dy <= 8; dy++) {
					level.setBlock(new BlockPos(cx + dx, y + dy, cz + dz), Blocks.AIR.defaultBlockState(), 2);
				}
				BlockState floor = d <= wall ? ground(dx, dz, r) : Blocks.GRASS_BLOCK.defaultBlockState();
				level.setBlock(new BlockPos(cx + dx, y - 1, cz + dz), floor, 2);
				for (int dy = 2; dy <= 4; dy++) {
					BlockPos below = new BlockPos(cx + dx, y - dy, cz + dz);
					if (level.getBlockState(below).isAir() || !level.getFluidState(below).isEmpty()) {
						level.setBlock(below, Blocks.DIRT.defaultBlockState(), 2);
					}
				}
				// The wall: a ring of fieldstone, one or two high, broken here and there, with a gate to the south.
				if (d >= wall - 0.5 && d < wall + 0.5) {
					boolean gate = dz > 0 && Math.abs(dx) <= 2;
					if (gate || r.nextFloat() < 0.12F) {
						continue;
					}
					int h = r.nextFloat() < 0.45F ? 2 : 1;
					for (int k = 0; k < h; k++) {
						level.setBlock(new BlockPos(cx + dx, y + k, cz + dz), stone[r.nextInt(stone.length)], 2);
					}
				}
			}
		}
		// The mound on the north side, the gallows on it.
		int mx = cx, mz = cz - MOUND_OFFSET;
		for (int dx = -MOUND_RADIUS - 1; dx <= MOUND_RADIUS + 1; dx++) {
			for (int dz = -MOUND_RADIUS - 1; dz <= MOUND_RADIUS + 1; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d <= MOUND_RADIUS + 0.5) {
					level.setBlock(new BlockPos(mx + dx, y, mz + dz), r.nextFloat() < 0.3F ? Blocks.ROOTED_DIRT.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState(),
						2);
				}
				if (d <= MOUND_RADIUS - 0.5) {
					level.setBlock(new BlockPos(mx + dx, y + 1, mz + dz), r.nextFloat() < 0.25F ? Blocks.PODZOL.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState(),
						2);
				}
			}
		}
		int top = y + 2;
		BlockState post = Blocks.DARK_OAK_LOG.defaultBlockState();
		for (int k = 0; k < 5; k++) {
			level.setBlock(new BlockPos(mx - 2, top + k, mz), post, 2);
			level.setBlock(new BlockPos(mx + 2, top + k, mz), post, 2);
		}
		BlockState beam = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
		for (int dx = -3; dx <= 3; dx++) {
			level.setBlock(new BlockPos(mx + dx, top + 5, mz), beam, 2);
		}
		BlockState chain = Blocks.IRON_CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y);
		level.setBlock(new BlockPos(mx, top + 4, mz), chain, 2);
		level.setBlock(new BlockPos(mx, top + 3, mz), chain, 2);
		level.setBlock(new BlockPos(mx + 3, top + 4, mz), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);
		// Gate posts with a lantern.
		for (int sx : new int[] {-3, 3}) {
			level.setBlock(new BlockPos(cx + sx, y, cz + wall), Blocks.STONE_BRICKS.defaultBlockState(), 2);
			level.setBlock(new BlockPos(cx + sx, y + 1, cz + wall), Blocks.STONE_BRICK_WALL.defaultBlockState(), 2);
		}
		level.setBlock(new BlockPos(cx - 3, y + 2, cz + wall), Blocks.LANTERN.defaultBlockState(), 2);
		// Three broken pillars to fight around.
		int[][] pillars = {{-9, 2}, {8, -1}, {2, 9}};
		for (int[] p : pillars) {
			int h = 2 + r.nextInt(3);
			for (int k = 0; k < h; k++) {
				BlockState s = k == h - 1 && r.nextBoolean() ? Blocks.STONE_BRICK_SLAB.defaultBlockState() : stone[2 + r.nextInt(3)];
				level.setBlock(new BlockPos(cx + p[0], y + k, cz + p[1]), s, 2);
			}
		}
		// A few stones and a dead bush at the foot of the gallows.
		level.setBlock(new BlockPos(mx - 1, top, mz + 2), Blocks.DEAD_BUSH.defaultBlockState(), 2);
		level.setBlock(new BlockPos(cx + 5, y, cz - 4), Blocks.MOSSY_COBBLESTONE_SLAB.defaultBlockState(), 2);
		BlockPos home = new BlockPos(cx, y, cz - 3);
		return new Arena(centre, RADIUS, 4, 10, home, 0F, Optional.of(new BlockPos(mx, top, mz)), MOUND_RADIUS);
	}
}
