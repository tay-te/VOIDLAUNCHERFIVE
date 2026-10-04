package dev.voidmc.hearth.build.light;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.BuildModule;
import java.util.Random;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

/** Shared setup for the light tests: vanilla registries, one service, specs as the invariant tests make them. */
final class LightSupport {
	static final BlueprintServiceImpl SERVICE;
	static final String[] TERRAINS = {"flat", "slope", "hills", "river", "coast", "steep"};
	static final Direction[] FACINGS = {Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST};

	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		SERVICE = BuildModule.standalone();
	}

	private LightSupport() {
	}

	static void init() {
	}

	static PlotSpec spec(ArchetypeInfo info, Identifier style, long seed, Direction facing, TerrainProbe probe, CompoundTag params) {
		Random r = new Random(seed * 31 + info.id().hashCode());
		int frontage = info.minFrontage() + r.nextInt(info.maxFrontage() - info.minFrontage() + 1);
		int depth = info.minDepth() + r.nextInt(info.maxDepth() - info.minDepth() + 1);
		return spec(info, style, seed, facing, probe, params, frontage, depth, 2);
	}

	static PlotSpec spec(ArchetypeInfo info, Identifier style, long seed, Direction facing, TerrainProbe probe, CompoundTag params, int frontage,
		int depth, int tier) {
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
}
