package dev.voidmc.hearth.build.command;

import dev.voidmc.hearth.api.TerrainProbe;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * The land as it stands in a loaded world, for placing a blueprint where a player is looking: the ground is the highest
 * solid block that is not part of a tree or a plant, so a house set down in a wood clears the trees instead of sitting on
 * their crowns. Server thread only; columns are cached for the life of the probe (one command).
 */
final class LiveTerrain implements TerrainProbe {
	private final ServerLevel level;
	/** Packed (ground y, water y) per column. */
	private final Long2LongOpenHashMap columns = new Long2LongOpenHashMap();

	LiveTerrain(ServerLevel level) {
		this.level = level;
	}

	@Override
	public long seed() {
		return this.level.getSeed();
	}

	@Override
	public int minY() {
		return this.level.getMinY();
	}

	@Override
	public int maxY() {
		return this.level.getMaxY();
	}

	@Override
	public int seaLevel() {
		return this.level.getSeaLevel();
	}

	@Override
	public int groundY(int x, int z) {
		return (int) (column(x, z) >> 32);
	}

	@Override
	public int waterY(int x, int z) {
		return (int) column(x, z);
	}

	@Override
	public @Nullable ResourceKey<Biome> biome(int x, int z) {
		return biomeAt(x, z).unwrapKey().orElse(null);
	}

	@Override
	public float temperature(int x, int z) {
		return biomeAt(x, z).value().getBaseTemperature();
	}

	Holder<Biome> biomeAt(int x, int z) {
		return this.level.getBiome(new BlockPos(x, groundY(x, z), z));
	}

	private long column(int x, int z) {
		long key = BlockPos.asLong(x, 0, z);
		if (this.columns.containsKey(key)) {
			return this.columns.get(key);
		}
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int top = this.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
		int min = this.level.getMinY();
		int water = NO_WATER;
		int y = top;
		for (; y > min; y--) {
			BlockState s = this.level.getBlockState(p.set(x, y, z));
			if (s.getFluidState().is(FluidTags.WATER)) {
				if (water == NO_WATER) {
					water = y + 1;
				}
				continue;
			}
			// trees, plants, snow layers and lily pads are not ground
			if (s.isAir() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.is(BlockTags.REPLACEABLE) || s.is(BlockTags.FLOWERS)
				|| s.is(BlockTags.SAPLINGS) || s.getCollisionShape(this.level, p).isEmpty() || s.is(net.minecraft.world.level.block.Blocks.LILY_PAD)) {
				continue;
			}
			break;
		}
		long packed = (long) (y + 1) << 32 | (water & 0xFFFFFFFFL);
		this.columns.put(key, packed);
		return packed;
	}
}
