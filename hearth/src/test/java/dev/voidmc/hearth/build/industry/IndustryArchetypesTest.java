package dev.voidmc.hearth.build.industry;

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
 * The heavy workshops (docs/buildings/industry.md) beyond the shared invariants: every one keeps the invariants on
 * every terrain at the heights the planner gives at the edge of a town (its minimum and the middle of its range) and
 * loses nothing at the plot's edges; every one has two job sites at least, one for each of its jobs, and a store; the
 * shipyard reaches the water behind it; the caravan yard has its hitching posts; no stage asks for more than a cart
 * train brings.
 */
class IndustryArchetypesTest {
	private static final BlueprintServiceImpl SERVICE;
	static final String[] IDS = {"work/logging_camp", "work/charcoal_kiln", "work/brick_kiln", "work/glassworks", "work/smelter", "work/salt_works",
		"work/shipyard", "commerce/caravan_yard"};
	private static final Direction[] FACINGS = {Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST};
	private static final String[] TERRAINS = {"flat", "slope", "hills", "river", "coast", "steep"};

	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		SERVICE = BuildModule.standalone();
	}

	/** The family's archetypes registered so far (all of them once the family is complete). */
	static List<ArchetypeInfo> family() {
		List<ArchetypeInfo> out = new ArrayList<>();
		for (String id : IDS) {
			ArchetypeInfo info = SERVICE.archetype(Hearth.id(id));
			if (info != null) {
				out.add(info);
			}
		}
		return out;
	}

	/** A spec the way the planner makes one: a size from the range by seed, the floor at the street in front. */
	static PlotSpec spec(ArchetypeInfo info, Culture c, long seed, Direction facing, TerrainProbe probe, int height, CompoundTag params) {
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

	private static String run(List<ArchetypeInfo> infos, String[] terrains, boolean low) {
		List<String> problems = new ArrayList<>();
		for (ArchetypeInfo info : infos) {
			int[] heights = low ? new int[]{info.minHeight(), (info.minHeight() + info.maxHeight()) / 2} : new int[]{info.maxHeight()};
			for (String terrain : terrains) {
				SyntheticTerrain probe = SyntheticTerrain.named(terrain, 42);
				for (Culture c : Culture.values()) {
					for (int k = 0; k < heights.length; k++) {
						long seed = 6007L * (k + 1) + c.ordinal() * 7717L + terrain.length() * 31L;
						Direction facing = FACINGS[(k + c.ordinal() + terrain.length()) % 4];
						PlotSpec spec = spec(info, c, seed, facing, probe, heights[k], new CompoundTag());
						Draft d = SERVICE.draw(spec, probe);
						BlueprintImpl bp = dev.voidmc.hearth.build.blueprint.Compiler.compile(d);
						String where = info.id().getPath() + " h" + heights[k] + " " + terrain + " " + c.getSerializedName() + " seed " + seed + " "
							+ facing.getSerializedName();
						for (String p : BlueprintChecks.all(bp, probe)) {
							problems.add(where + ": " + p);
						}
						if (d.clipped() > 12) {
							problems.add(where + ": " + d.clipped() + " blocks fell outside the plot");
						}
					}
				}
			}
		}
		return problems.isEmpty() ? "" : problems.size() + " problems:\n  " + String.join("\n  ", problems.subList(0, Math.min(60, problems.size())));
	}

	@Test
	void lowPlotsKeepTheInvariantsOnEveryTerrain() {
		String r = run(family(), TERRAINS, true);
		assertTrue(r.isEmpty(), r);
	}

	@Test
	void workplacesHaveJobSitesAndStores() {
		List<String> problems = new ArrayList<>();
		SyntheticTerrain probe = SyntheticTerrain.named("flat", 3);
		for (ArchetypeInfo info : family()) {
			for (Culture c : Culture.values()) {
				for (int s = 0; s < 2; s++) {
					PlotSpec spec = spec(info, c, 900L + s * 37 + c.ordinal(), FACINGS[s], probe, (info.minHeight() + info.maxHeight()) / 2, new CompoundTag());
					BlueprintImpl bp = SERVICE.compiled(spec, probe);
					long sites = bp.pois().stream().filter(p -> p.type() == PoiType.JOB_SITE).count();
					long stores = bp.pois().stream().filter(p -> p.type() == PoiType.STORAGE).count();
					String where = info.id().getPath() + " " + c.getSerializedName() + " s" + s;
					if ((info.category() == ArchetypeCategory.WORKPLACE || info.category() == ArchetypeCategory.COMMERCE) && sites < 2) {
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
					if (info.id().getPath().equals("commerce/caravan_yard") && bp.pois().stream().noneMatch(p -> p.type() == PoiType.HITCHING_POST)) {
						problems.add(where + ": no hitching post");
					}
				}
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " problems:\n  " + String.join("\n  ", problems));
	}

	@Test
	void shipyardsLaunchIntoTheWaterBehind() {
		ArchetypeInfo info = SERVICE.archetype(Hearth.id("work/shipyard"));
		if (info == null) {
			return;
		}
		TerrainProbe probe = new WaterBehind();
		List<String> problems = new ArrayList<>();
		for (Culture c : Culture.values()) {
			for (int s = 0; s < 2; s++) {
				PlotSpec spec = spec(info, c, 2100L + s * 13 + c.ordinal(), Direction.NORTH, probe, (info.minHeight() + info.maxHeight()) / 2, new CompoundTag());
				BlueprintImpl bp = SERVICE.compiled(spec, probe);
				for (String p : BlueprintChecks.all(bp, probe)) {
					problems.add(c.getSerializedName() + " s" + s + ": " + p);
				}
			}
		}
		assertTrue(problems.isEmpty(), String.join("\n", problems));
	}

	@Test
	void noStageAsksForMoreThanACartTrainBrings() {
		List<String> problems = new ArrayList<>();
		SyntheticTerrain probe = SyntheticTerrain.named("hills", 9);
		for (ArchetypeInfo info : family()) {
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
	static final class WaterBehind implements TerrainProbe {
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
			return z >= 2 ? 61 : 64;
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
