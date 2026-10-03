package dev.voidmc.hearth.build;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.TerrainProbe;
import java.util.Random;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

/** Shared setup for the build module's tests: vanilla registries, one service, specs the way a planner makes them. */
final class BuildTestSupport {
	static final BlueprintServiceImpl SERVICE;

	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		SERVICE = BuildModule.standalone();
	}

	private BuildTestSupport() {
	}

	static void init() {
		// touching the class runs the bootstrap
	}

	/**
	 * A spec for an archetype on a terrain: a size from its range by seed, facing by index, the floor level with the
	 * ground in front of the plot (where the street runs), as the planner sets it.
	 */
	static PlotSpec spec(ArchetypeInfo info, Identifier style, long seed, Direction facing, TerrainProbe probe, CompoundTag params) {
		Random r = new Random(seed * 31 + info.id().hashCode());
		int frontage = info.minFrontage() + r.nextInt(info.maxFrontage() - info.minFrontage() + 1);
		int depth = info.minDepth() + r.nextInt(info.maxDepth() - info.minDepth() + 1);
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
		return new PlotSpec(info.id(), style, seed, new BlockPos(ox, floor, oz), sizeX, sizeZ, facing, Math.max(info.maxHeight(), 8), 2, params);
	}

	static PlotSpec spec(ArchetypeInfo info, Identifier style, long seed, Direction facing, TerrainProbe probe) {
		return spec(info, style, seed, facing, probe, new CompoundTag());
	}
}
