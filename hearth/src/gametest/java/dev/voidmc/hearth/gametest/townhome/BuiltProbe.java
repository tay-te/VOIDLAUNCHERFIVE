package dev.voidmc.hearth.gametest.townhome;

import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

/**
 * A terrain probe that sees what earlier blueprints built as ground (the top of their solid blocks), so a dwelling
 * stacked on another's roof is drawn and checked against the roof it stands on, as in a live world.
 */
public final class BuiltProbe implements TerrainProbe {
	private final TerrainProbe base;
	private final Long2IntOpenHashMap tops = new Long2IntOpenHashMap();

	public BuiltProbe(TerrainProbe base) {
		this.base = base;
		this.tops.defaultReturnValue(Integer.MIN_VALUE);
	}

	/** Adds a built blueprint: each column's ground rises to the top of its solid blocks. */
	public BuiltProbe add(BlueprintImpl bp) {
		for (int s = 0; s < bp.stageCount(); s++) {
			for (int i = 0; i < bp.stepCount(s); i++) {
				bp.step(s, i, (x, y, z, state, tag) -> {
					if (state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
						long k = BlockPos.asLong(x, 0, z);
						this.tops.put(k, Math.max(this.tops.get(k), y + 1));
					}
				});
			}
		}
		return this;
	}

	@Override
	public int groundY(int x, int z) {
		int t = this.tops.get(BlockPos.asLong(x, 0, z));
		return Math.max(this.base.groundY(x, z), t);
	}

	@Override
	public int waterY(int x, int z) {
		return this.tops.containsKey(BlockPos.asLong(x, 0, z)) ? NO_WATER : this.base.waterY(x, z);
	}

	@Override
	public long seed() {
		return this.base.seed();
	}

	@Override
	public int minY() {
		return this.base.minY();
	}

	@Override
	public int maxY() {
		return this.base.maxY();
	}

	@Override
	public int seaLevel() {
		return this.base.seaLevel();
	}

	@Override
	public @Nullable ResourceKey<Biome> biome(int x, int z) {
		return this.base.biome(x, z);
	}

	@Override
	public float temperature(int x, int z) {
		return this.base.temperature(x, z);
	}
}
