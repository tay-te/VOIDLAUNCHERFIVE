package dev.voidmc.hearth.build.noble;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import dev.voidmc.hearth.build.style.StyleRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The nobility's promises beyond the shared invariants (which {@code BlueprintInvariantsTest} also holds them to): the
 * five seats are registered in the {@code NOBLE} category with the jobs and sizes the planner asks for; each carries
 * what its folk use (the throne and the lord's seat, the steward's desk and the strongbox, the guards' posts, the
 * hitching posts and the stable master's trough); they hold the invariants at the smallest and largest plots the
 * planner hands them, in every culture and on rough ground; and no stage asks for more goods than a town can store,
 * however big the castle.
 */
class NobleTest {
	static final BlueprintServiceImpl SERVICE;
	static final String[] IDS = {"keep", "castle", "palace", "hunting_lodge", "stables"};
	private static final String[] TERRAINS = {"flat", "slope", "hills", "river", "coast", "steep"};
	private static final Direction[] FACINGS = {Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST};
	/** The most goods one stage may ask for: a town's stores hold a few thousand of a good. */
	static final int STAGE_GOODS = 4000;

	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		SERVICE = BuildModule.standalone();
	}

	static ArchetypeInfo info(String name) {
		return SERVICE.archetype(Hearth.id("noble/" + name));
	}

	static List<String> registered() {
		List<String> out = new ArrayList<>();
		for (String id : IDS) {
			if (info(id) != null) {
				out.add(id);
			}
		}
		return out;
	}

	static PlotSpec spec(ArchetypeInfo info, Culture c, long seed, int frontage, int depth, Direction facing, TerrainProbe probe, CompoundTag params, int tier) {
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
		return new PlotSpec(info.id(), StyleRegistry.styleId(c), seed, new BlockPos(ox, floor, oz), sizeX, sizeZ, facing, info.maxHeight(), tier, params);
	}

	static PlotSpec spec(ArchetypeInfo info, Culture c, long seed, Direction facing, TerrainProbe probe, CompoundTag params) {
		Random r = new Random(seed * 31 + info.id().hashCode());
		int frontage = info.minFrontage() + r.nextInt(info.maxFrontage() - info.minFrontage() + 1);
		int depth = info.minDepth() + r.nextInt(info.maxDepth() - info.minDepth() + 1);
		return spec(info, c, seed, frontage, depth, facing, probe, params, 2);
	}

	static Stream<Arguments> cases() {
		List<Arguments> out = new ArrayList<>();
		for (String id : registered()) {
			for (String terrain : TERRAINS) {
				out.add(Arguments.of(id, terrain));
			}
		}
		return out.stream();
	}

	static Stream<Arguments> ids() {
		return registered().stream().map(Arguments::of);
	}

	static List<String> problems(BlueprintImpl bp, TerrainProbe probe, String tag) {
		List<String> out = new ArrayList<>();
		for (String p : BlueprintChecks.all(bp, probe)) {
			out.add(tag + ": " + p);
		}
		for (int s = 0; s < bp.stageCount(); s++) {
			int sum = 0;
			for (int g : bp.goods(s)) {
				sum += g;
			}
			if (sum > STAGE_GOODS) {
				out.add(tag + ": stage " + s + " (" + bp.stageKind(s) + ", campaign " + bp.campaign(s) + ") asks for " + sum + " goods");
			}
		}
		return out;
	}

	/** The shared invariants, culture by culture (two seeds, turning facings), on one terrain. */
	@ParameterizedTest(name = "noble/{0} on {1}")
	@MethodSource("cases")
	void invariantsHold(String id, String terrain) {
		ArchetypeInfo info = info(id);
		List<String> problems = new ArrayList<>();
		for (Culture c : Culture.values()) {
			for (int s = 0; s < 2; s++) {
				long seed = 7919L * (s + 1) + c.ordinal() * 104729L;
				Direction facing = FACINGS[(s + c.ordinal()) % 4];
				SyntheticTerrain probe = SyntheticTerrain.named(terrain, 42);
				BlueprintImpl bp = SERVICE.compiled(spec(info, c, seed, facing, probe, new CompoundTag()), probe);
				problems.addAll(problems(bp, probe, c.getSerializedName() + " seed " + seed + " " + facing.getSerializedName()));
			}
		}
		assertTrue(problems.isEmpty(), id + " on " + terrain + ", " + problems.size() + " problems:\n  " + String.join("\n  ", problems));
	}

	/** The smallest and the largest plot the planner may hand each seat, in every culture, on flat ground and on hills. */
	@ParameterizedTest(name = "noble/{0} at its extreme sizes")
	@MethodSource("ids")
	void extremeSizesHold(String id) {
		ArchetypeInfo info = info(id);
		List<String> problems = new ArrayList<>();
		for (String terrain : new String[]{"flat", "hills"}) {
			SyntheticTerrain probe = SyntheticTerrain.named(terrain, 7);
			for (Culture c : Culture.values()) {
				int[][] sizes = {{info.minFrontage(), info.minDepth()}, {info.maxFrontage(), info.maxDepth()}, {info.minFrontage(), info.maxDepth()}};
				for (int k = 0; k < sizes.length; k++) {
					long seed = 555L + k * 31 + c.ordinal() * 7;
					Direction facing = FACINGS[(k + c.ordinal()) % 4];
					CompoundTag params = new CompoundTag();
					params.putString("station", "lord");
					PlotSpec spec = spec(info, c, seed, sizes[k][0], sizes[k][1], facing, probe, params, 3);
					problems.addAll(problems(SERVICE.compiled(spec, probe), probe, terrain + " " + c.getSerializedName() + " " + sizes[k][0] + "x" + sizes[k][1]));
				}
			}
		}
		assertTrue(problems.isEmpty(), id + ", " + problems.size() + " problems:\n  " + String.join("\n  ", problems));
	}

	/** Every style variant of every culture, forced (on a slope, one seed, turning facings). */
	@ParameterizedTest(name = "noble/{0} in every variant")
	@MethodSource("ids")
	void everyVariantHolds(String id) {
		ArchetypeInfo info = info(id);
		List<String> problems = new ArrayList<>();
		SyntheticTerrain probe = SyntheticTerrain.named("slope", 42);
		for (Culture c : Culture.values()) {
			var style = SERVICE.styles().get(StyleRegistry.styleId(c));
			for (int v = 0; v < style.variants().size(); v++) {
				CompoundTag params = new CompoundTag();
				params.putInt("variant", v);
				long seed = 31337L + v * 7L + c.ordinal() * 1009L;
				Direction facing = FACINGS[(v + c.ordinal()) % 4];
				problems.addAll(problems(SERVICE.compiled(spec(info, c, seed, facing, probe, params), probe), probe,
					c.getSerializedName() + " (" + style.variants().get(v).name() + ")"));
			}
		}
		assertTrue(problems.isEmpty(), id + ", " + problems.size() + " problems:\n  " + String.join("\n  ", problems));
	}

	@Test
	void seatsAreRegisteredForThePlanner() {
		for (String id : registered()) {
			ArchetypeInfo info = info(id);
			assertEquals(ArchetypeCategory.NOBLE, info.category(), id);
		}
		ArchetypeInfo keep = info("keep");
		assertTrue(keep.jobs().containsAll(List.of(JobType.LORD, JobType.STEWARD, JobType.GUARD)));
		assertEquals(1, keep.maxPerSettlement());
		ArchetypeInfo castle = info("castle");
		if (castle != null) {
			assertTrue(castle.jobs().containsAll(List.of(JobType.LORD, JobType.STEWARD, JobType.GUARD)));
			assertEquals(1, castle.maxPerSettlement());
			// the castle replaces the keep over the keep's plot and its close: the close is at least the castle's smallest
			assertTrue(castle.minFrontage() >= keep.minFrontage());
		}
		ArchetypeInfo stables = info("stables");
		if (stables != null) {
			assertTrue(stables.jobs().contains(JobType.STABLEMASTER));
		}
	}

	@Test
	void seatsCarryWhatFolkUse() {
		SyntheticTerrain probe = SyntheticTerrain.named("flat", 1);
		for (String id : registered()) {
			ArchetypeInfo info = info(id);
			for (Culture c : Culture.values()) {
				BlueprintImpl bp = SERVICE.compiled(spec(info, c, 99L, Direction.SOUTH, probe, new CompoundTag()), probe);
				for (JobType job : info.jobs()) {
					assertTrue(bp.pois().stream().anyMatch(p -> p.job() == job), id + " in " + c + " has no job site for " + job);
				}
				List<PoiType> want = switch (id) {
					case "keep", "castle", "palace" -> List.of(PoiType.THRONE, PoiType.STRONGBOX, PoiType.BED, PoiType.ALTAR);
					case "stables" -> List.of(PoiType.HITCHING_POST, PoiType.STORAGE);
					case "hunting_lodge" -> List.of(PoiType.HEARTH, PoiType.BED);
					default -> List.of();
				};
				for (PoiType t : want) {
					assertTrue(bp.pois().stream().anyMatch(p -> p.type() == t), id + " in " + c + " has no " + t);
				}
				if (id.equals("keep") || id.equals("castle")) {
					assertTrue(bp.pois().stream().anyMatch(p -> p.type() == PoiType.GUARD_POST), id + " in " + c + " has no guard post");
				}
			}
		}
	}
}
