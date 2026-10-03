package dev.voidmc.hearth.build.townhome;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.BuildModule;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

/**
 * Shared setup for the town home tests: one service, rows of attached plots laid out the way the planner lays out a
 * row (each floor meeting the street at its own door, party walls named as the building's own sides), stacks of
 * dwellings raised on each other's roofs, and a probe that sees what earlier blueprints built as ground.
 */
final class TownHomeSupport {
	static final BlueprintServiceImpl SERVICE;

	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		SERVICE = BuildModule.standalone();
	}

	private TownHomeSupport() {
	}

	static ArchetypeInfo info(String path) {
		return SERVICE.archetype(Hearth.id(path));
	}

	/**
	 * A row of {@code count} plots of an archetype along x, facing south (the street on +z), each floor at the ground
	 * in front of its middle, party walls between neighbours as the planner sets them (Housing.row: the house at lower
	 * x has its own left, +x for a south-facing plot, shared).
	 */
	static List<PlotSpec> row(ArchetypeInfo info, Identifier style, long seed, int count, TerrainProbe probe, CompoundTag extra) {
		List<PlotSpec> out = new ArrayList<>();
		int x = -count * 4;
		for (int i = 0; i < count; i++) {
			java.util.Random r = new java.util.Random(seed * 31 + i * 7919L);
			int frontage = info.minFrontage() + r.nextInt(info.maxFrontage() - info.minFrontage() + 1);
			int depth = info.minDepth() + r.nextInt(info.maxDepth() - info.minDepth() + 1);
			int floor = probe.groundY(x + frontage / 2, 0);
			if (probe.waterY(x + frontage / 2, 0) != TerrainProbe.NO_WATER) {
				floor = Math.max(floor, probe.waterY(x + frontage / 2, 0) + 1);
			}
			CompoundTag p = extra.copy();
			p.putBoolean("party_left", i < count - 1);
			p.putBoolean("party_right", i > 0);
			p.putInt("row", count);
			out.add(new PlotSpec(info.id(), style, seed + i, new BlockPos(x, floor, -depth), frontage, depth, Direction.SOUTH, info.maxHeight(), 3, p));
			x += frontage;
		}
		return out;
	}

	/** A single plot of a given size facing a direction, its floor at the ground just outside its front. */
	static PlotSpec plot(ArchetypeInfo info, Identifier style, long seed, Direction facing, TerrainProbe probe, CompoundTag params, int frontage,
		int depth, int height) {
		boolean zAxis = facing.getAxis() == Direction.Axis.Z;
		int sizeX = zAxis ? frontage : depth;
		int sizeZ = zAxis ? depth : frontage;
		int ox = -sizeX / 2;
		int oz = -sizeZ / 2;
		int fx = switch (facing) {
			case EAST -> ox + sizeX;
			case WEST -> ox - 1;
			default -> ox + sizeX / 2;
		};
		int fz = switch (facing) {
			case SOUTH -> oz + sizeZ;
			case NORTH -> oz - 1;
			default -> oz + sizeZ / 2;
		};
		int floor = probe.groundY(fx, fz);
		if (probe.waterY(fx, fz) != TerrainProbe.NO_WATER) {
			floor = Math.max(floor, probe.waterY(fx, fz) + 1);
		}
		return new PlotSpec(info.id(), style, seed, new BlockPos(ox, floor, oz), sizeX, sizeZ, facing, height, 2, params);
	}

	/** A probe that sees the top of earlier blueprints' solid blocks as the ground (a stacked dwelling's roof below). */
	static final class BuiltProbe implements TerrainProbe {
		private final TerrainProbe base;
		private final Long2IntOpenHashMap tops = new Long2IntOpenHashMap();

		BuiltProbe(TerrainProbe base) {
			this.base = base;
			this.tops.defaultReturnValue(Integer.MIN_VALUE);
		}

		BuiltProbe add(BlueprintImpl bp) {
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
			return Math.max(this.base.groundY(x, z), this.tops.get(BlockPos.asLong(x, 0, z)));
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
}
