package dev.voidmc.hearth.build.age;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.BuildModule;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import java.util.Random;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.state.BlockState;

/** Shared setup for the age tests: one service, specs the way the planner makes them, blueprints read back by position. */
final class AgeSupport {
	static final BlueprintServiceImpl SERVICE;

	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		SERVICE = BuildModule.standalone();
	}

	private AgeSupport() {
	}

	static final Direction[] FACINGS = {Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST};

	/** A spec for an archetype: a size from its range by seed, the floor at the ground just outside its front. */
	static PlotSpec spec(ArchetypeInfo info, Identifier style, long seed, Direction facing, TerrainProbe probe, CompoundTag params) {
		Random r = new Random(seed * 31 + info.id().hashCode());
		int frontage = info.minFrontage() + r.nextInt(info.maxFrontage() - info.minFrontage() + 1);
		int depth = info.minDepth() + r.nextInt(info.maxDepth() - info.minDepth() + 1);
		return spec(info, style, seed, facing, probe, params, frontage, depth, 2);
	}

	static PlotSpec spec(ArchetypeInfo info, Identifier style, long seed, Direction facing, TerrainProbe probe, CompoundTag params, int frontage, int depth,
		int tier) {
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
		return new PlotSpec(info.id(), style, seed, new BlockPos(ox, floor, oz), sizeX, sizeZ, facing, Math.max(info.maxHeight(), 8), tier, params);
	}

	/** The same spec with one more param. */
	static PlotSpec with(PlotSpec s, String key, double value) {
		CompoundTag p = s.params();
		p.putDouble(key, value);
		return new PlotSpec(s.archetype(), s.style(), s.seed(), s.origin(), s.sizeX(), s.sizeZ(), s.facing(), s.maxHeight(), s.tier(), p);
	}

	static PlotSpec with(PlotSpec s, String key, int value) {
		CompoundTag p = s.params();
		p.putInt(key, value);
		return new PlotSpec(s.archetype(), s.style(), s.seed(), s.origin(), s.sizeX(), s.sizeZ(), s.facing(), s.maxHeight(), s.tier(), p);
	}

	/** Every step of a blueprint by world position (the last step at a position wins, as in the world). */
	static it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<BlockState> blocks(BlueprintImpl bp) {
		it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<BlockState> m = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
		for (int s = 0; s < bp.stageCount(); s++) {
			for (int i = 0; i < bp.stepCount(s); i++) {
				bp.step(s, i, (x, y, z, st, tag) -> m.put(BlockPos.asLong(x, y, z), st));
			}
		}
		return m;
	}
}
