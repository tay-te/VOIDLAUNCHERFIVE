package dev.voidmc.hearth.build.station;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.BuildModule;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

/**
 * Shared setup for the homes-by-station tests: one service, single plots at any size and height the catalogue allows,
 * and rows of attached plots laid out as the planner lays a row (each floor meeting the street at its own door, the party
 * walls named as the building's own sides).
 */
final class StationSupport {
	static final BlueprintServiceImpl SERVICE;

	/** The station homes (docs/LIVING_WORLD.md §8.5). */
	static final String[] HOMES = {"house/hovel", "house/shack", "house/croft", "house/merchant_house", "house/reeve_house", "house/rectory",
		"house/scribe_house", "house/hermit_hut"};

	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		SERVICE = BuildModule.standalone();
	}

	private StationSupport() {
	}

	/** The registered station homes (only those built so far). */
	static List<ArchetypeInfo> registered() {
		List<ArchetypeInfo> out = new ArrayList<>();
		// HEARTH_STATION_ONLY=merchant_house,rectory narrows the run while iterating on a few homes
		String only = System.getenv("HEARTH_STATION_ONLY");
		for (String h : HOMES) {
			if (only != null && !only.isBlank() && !java.util.Arrays.asList(only.split(",")).contains(h.substring(h.indexOf('/') + 1))) {
				continue;
			}
			ArchetypeInfo info = SERVICE.archetype(Hearth.id(h));
			if (info != null) {
				out.add(info);
			}
		}
		return out;
	}

	static ArchetypeInfo info(String path) {
		return SERVICE.archetype(Hearth.id(path));
	}

	/** A single plot of a given size and height facing a direction, its floor at the ground just outside its front. */
	static PlotSpec plot(ArchetypeInfo info, Identifier style, long seed, Direction facing, TerrainProbe probe, CompoundTag params, int frontage, int depth,
		int height, int tier) {
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
		return new PlotSpec(info.id(), style, seed, new BlockPos(ox, floor, oz), sizeX, sizeZ, facing, height, tier, params);
	}

	/**
	 * A row of {@code count} plots along x, facing south (the street on +z), each floor at the ground in front of its
	 * middle, party walls between neighbours as the planner sets them.
	 */
	static List<PlotSpec> row(ArchetypeInfo info, Identifier style, long seed, int count, TerrainProbe probe, CompoundTag extra) {
		List<PlotSpec> out = new ArrayList<>();
		int x = -count * 4;
		for (int i = 0; i < count; i++) {
			java.util.Random r = new java.util.Random(seed * 31 + i * 7919L);
			int frontage = info.minFrontage() + r.nextInt(info.maxFrontage() - info.minFrontage() + 1);
			int depth = info.minDepth() + r.nextInt(info.maxDepth() - info.minDepth() + 1);
			int height = info.minHeight() + r.nextInt(info.maxHeight() - info.minHeight() + 1);
			int floor = probe.groundY(x + frontage / 2, 0);
			if (probe.waterY(x + frontage / 2, 0) != TerrainProbe.NO_WATER) {
				floor = Math.max(floor, probe.waterY(x + frontage / 2, 0) + 1);
			}
			CompoundTag p = extra.copy();
			p.putBoolean("party_left", i < count - 1);
			p.putBoolean("party_right", i > 0);
			out.add(new PlotSpec(info.id(), style, seed + i, new BlockPos(x, floor, -depth), frontage, depth, Direction.SOUTH, height, 2, p));
			x += frontage;
		}
		return out;
	}
}
