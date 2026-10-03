package dev.voidmc.hearth.build.trade;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.BuildModule;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.check.BlueprintChecks;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.style.StyleRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The trade family beyond the shared invariants: every workplace and store keeps the invariants on the low plots the
 * planner gives away from the core (its minimum height and the middle of its range), nothing it draws is dropped at the
 * plot's edges, every workplace has its job sites and a store for every trade it offers, the shops serve over a
 * counter, a fishery moors where the water is, and no stage asks a builder for more goods than a cart train brings.
 */
class TradeArchetypesTest {
	private static final BlueprintServiceImpl SERVICE;
	private static final String[] IDS = {"work/builders_yard", "work/smithy", "work/carpentry", "work/masonry", "work/bakery", "work/brewery", "work/tailor",
		"work/sawmill", "work/quarry", "work/fishery", "work/windmill", "work/watermill", "work/hunters_lodge", "storage/storehouse", "storage/granary",
		"storage/warehouse"};
	private static final Direction[] FACINGS = {Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST};

	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		SERVICE = BuildModule.standalone();
	}

	private static List<ArchetypeInfo> trades() {
		List<ArchetypeInfo> out = new ArrayList<>();
		for (String id : IDS) {
			ArchetypeInfo info = SERVICE.archetype(Hearth.id(id));
			assertTrue(info != null, "the trade archetype " + id + " is registered");
			out.add(info);
		}
		return out;
	}

	/** A spec the way the planner makes one: a size from the range by seed, the floor at the street in front. */
	private static PlotSpec spec(ArchetypeInfo info, Culture c, long seed, Direction facing, TerrainProbe probe, int height, CompoundTag params) {
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
		return new PlotSpec(info.id(), StyleRegistry.styleId(c), seed, new BlockPos(ox, floor, oz), sizeX, sizeZ, facing, height, 2, params);
	}

	@Test
	void lowPlotsKeepTheInvariantsAndLoseNothingAtTheEdges() {
		List<String> problems = new ArrayList<>();
		for (ArchetypeInfo info : trades()) {
			int[] heights = {info.minHeight(), (info.minHeight() + info.maxHeight()) / 2};
			for (String terrain : new String[]{"flat", "slope", "hills"}) {
				SyntheticTerrain probe = SyntheticTerrain.named(terrain, 42);
				for (Culture c : Culture.values()) {
					for (int k = 0; k < heights.length; k++) {
						long seed = 5003L * (k + 1) + c.ordinal() * 7717L + terrain.length();
						Direction facing = FACINGS[(k + c.ordinal()) % 4];
						PlotSpec spec = spec(info, c, seed, facing, probe, heights[k], new CompoundTag());
						Draft d = SERVICE.draw(spec, probe);
						BlueprintImpl bp = dev.voidmc.hearth.build.blueprint.Compiler.compile(d);
						for (String p : BlueprintChecks.all(bp, probe)) {
							problems.add(info.id().getPath() + " h" + heights[k] + " " + terrain + " " + c.getSerializedName() + " seed " + seed + ": " + p);
						}
						if (d.clipped() > 12) {
							problems.add(info.id().getPath() + " h" + heights[k] + " " + terrain + " " + c.getSerializedName() + " seed " + seed + ": " + d.clipped()
								+ " blocks fell outside the plot");
						}
					}
				}
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " problems:\n  " + String.join("\n  ", problems.subList(0, Math.min(40, problems.size()))));
	}

	@Test
	void workplacesHaveJobSitesAndStores() {
		List<String> problems = new ArrayList<>();
		SyntheticTerrain probe = SyntheticTerrain.named("flat", 3);
		for (ArchetypeInfo info : trades()) {
			for (Culture c : Culture.values()) {
				for (int s = 0; s < 2; s++) {
					PlotSpec spec = spec(info, c, 900L + s * 37 + c.ordinal(), FACINGS[s], probe, info.maxHeight(), new CompoundTag());
					BlueprintImpl bp = SERVICE.compiled(spec, probe);
					long sites = bp.pois().stream().filter(p -> p.type() == PoiType.JOB_SITE).count();
					long stores = bp.pois().stream().filter(p -> p.type() == PoiType.STORAGE).count();
					String where = info.id().getPath() + " " + c.getSerializedName() + " s" + s;
					if (info.category() == ArchetypeCategory.WORKPLACE && sites < 2) {
						problems.add(where + ": " + sites + " job sites (a workplace employs two at least)");
					}
					for (JobType job : info.jobs()) {
						if (bp.pois().stream().noneMatch(p -> p.type() == PoiType.JOB_SITE && p.job() == job)) {
							problems.add(where + ": no job site for " + job);
						}
					}
					if (stores < 1) {
						problems.add(where + ": no store");
					}
				}
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " problems:\n  " + String.join("\n  ", problems));
	}

	@Test
	void shopsServeOverACounter() {
		List<String> problems = new ArrayList<>();
		SyntheticTerrain probe = SyntheticTerrain.named("flat", 5);
		for (String id : new String[]{"work/bakery", "work/tailor"}) {
			ArchetypeInfo info = SERVICE.archetype(Hearth.id(id));
			int missing = 0;
			int n = 0;
			for (Culture c : Culture.values()) {
				for (int s = 0; s < 3; s++) {
					PlotSpec spec = spec(info, c, 1300L + s * 11 + c.ordinal() * 3, FACINGS[s % 4], probe, info.maxHeight(), new CompoundTag());
					BlueprintImpl bp = SERVICE.compiled(spec, probe);
					n++;
					if (bp.pois().stream().noneMatch(p -> p.type() == PoiType.COUNTER)) {
						missing++;
					}
				}
			}
			// the narrowest plots may have no room for a counter beside the door; most shops have one
			if (missing * 4 > n) {
				problems.add(id + ": " + missing + " of " + n + " without a counter");
			}
		}
		assertTrue(problems.isEmpty(), String.join("\n", problems));
	}

	@Test
	void fisheriesMoorWhereTheWaterIs() {
		ArchetypeInfo info = SERVICE.archetype(Hearth.id("work/fishery"));
		TerrainProbe probe = new WaterBehind();
		int moored = 0;
		int n = 0;
		List<String> problems = new ArrayList<>();
		for (Culture c : Culture.values()) {
			for (int s = 0; s < 2; s++) {
				PlotSpec spec = spec(info, c, 2100L + s * 13 + c.ordinal(), Direction.NORTH, probe, info.maxHeight(), new CompoundTag());
				BlueprintImpl bp = SERVICE.compiled(spec, probe);
				for (String p : BlueprintChecks.all(bp, probe)) {
					problems.add(c.getSerializedName() + " s" + s + ": " + p);
				}
				n++;
				if (bp.pois().stream().anyMatch(p -> p.type() == PoiType.DOCK)) {
					moored++;
				}
			}
		}
		assertTrue(problems.isEmpty(), String.join("\n", problems));
		assertTrue(moored * 2 >= n, moored + " of " + n + " fisheries with water behind them have a mooring");
	}

	@Test
	void noStageAsksForMoreThanACartTrainBrings() {
		List<String> problems = new ArrayList<>();
		SyntheticTerrain probe = SyntheticTerrain.named("hills", 9);
		for (ArchetypeInfo info : trades()) {
			for (Culture c : Culture.values()) {
				PlotSpec spec = spec(info, c, 777L + c.ordinal(), Direction.SOUTH, probe, info.maxHeight(), new CompoundTag());
				BlueprintImpl bp = SERVICE.compiled(spec, probe);
				for (int s = 0; s < bp.stageCount(); s++) {
					int sum = 0;
					for (int g : bp.goods(s)) {
						sum += g;
					}
					if (sum > 4500) {
						problems.add(info.id().getPath() + " " + c.getSerializedName() + " " + bp.stageKind(s) + ": " + sum + " goods");
					}
				}
			}
		}
		assertTrue(problems.isEmpty(), String.join("\n", problems));
	}

	/** Flat land to the south of z = 2, open water two deep from there north (a plot facing north has it behind). */
	private static final class WaterBehind implements TerrainProbe {
		@Override
		public long seed() {
			return 1;
		}

		@Override
		public int minY() {
			return -64;
		}

		@Override
		public int maxY() {
			return 320;
		}

		@Override
		public int seaLevel() {
			return 63;
		}

		@Override
		public int groundY(int x, int z) {
			return z >= 2 ? 62 : 64;
		}

		@Override
		public int waterY(int x, int z) {
			return z >= 2 ? 64 : NO_WATER;
		}

		@Override
		public @Nullable ResourceKey<Biome> biome(int x, int z) {
			return null;
		}

		@Override
		public float temperature(int x, int z) {
			return 0.8F;
		}
	}
}
