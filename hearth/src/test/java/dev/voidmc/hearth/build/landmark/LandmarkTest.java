package dev.voidmc.hearth.build.landmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.BuildModule;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.check.BlueprintChecks;
import dev.voidmc.hearth.build.style.StyleRegistry;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

/**
 * What the landmark family promises beyond the shared invariants: every landmark is one per settlement at town tier or
 * above and declares the cultures it serves; each carries the points folk use (altars and the priest's lectern in the
 * churches, viewpoints up the towers, a dock on the lighthouse's jetty where water reaches it, bells in the belfries,
 * clock dials on the clock tower); the cathedral stands at the smallest close the planner may hand it and the largest;
 * the skeleton of piers and buttresses is laid before the walls; every stage's goods stay a builder's load; and every
 * landmark keeps inside its height.
 */
class LandmarkTest {
	private static final BlueprintServiceImpl SERVICE;

	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		SERVICE = BuildModule.standalone();
	}

	private static final String[] IDS = {"bell_tower", "clock_tower", "cathedral", "temple", "pagoda", "great_hall", "lighthouse"};

	private static ArchetypeInfo info(String name) {
		return SERVICE.archetype(Hearth.id("landmark/" + name));
	}

	private static PlotSpec spec(ArchetypeInfo info, Culture c, long seed, int frontage, int depth, Direction facing, TerrainProbe probe, CompoundTag params, int tier) {
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

	private static BlueprintImpl build(String name, Culture c, long seed, int frontage, int depth, String terrain, CompoundTag params, int tier) {
		ArchetypeInfo info = info(name);
		SyntheticTerrain probe = SyntheticTerrain.named(terrain, 42);
		return SERVICE.compiled(spec(info, c, seed, frontage, depth, Direction.SOUTH, probe, params, tier), probe);
	}

	@Test
	void everyLandmarkIsRegisteredAsOnePerSettlement() {
		for (String id : IDS) {
			ArchetypeInfo info = info(id);
			assertTrue(info != null, id + " is not registered");
			assertEquals(ArchetypeCategory.LANDMARK, info.category(), id);
			assertEquals(1, info.maxPerSettlement(), id);
			assertTrue(info.minTier().ordinal() >= dev.voidmc.hearth.api.SettlementTier.TOWN.ordinal(), id + " below town tier");
		}
		// the churches give the priest work, the lighthouse a keeper's bed, the lighthouse needs its water
		assertTrue(info("cathedral").jobs().contains(JobType.PRIEST));
		assertTrue(info("temple").jobs().contains(JobType.PRIEST));
		assertTrue(info("lighthouse").flags().contains(ArchetypeInfo.SiteFlag.WATERFRONT));
		assertTrue(info("clock_tower").flags().contains(ArchetypeInfo.SiteFlag.CENTRAL));
		// each culture the planner may ask has at least one landmark of its own, and the famous ones theirs
		assertTrue(info("pagoda").cultures().containsAll(Set.of(Culture.KARST, Culture.WISTERIA)));
		assertTrue(info("great_hall").cultures().containsAll(Set.of(Culture.NORTHERN, Culture.TUNDRA, Culture.MOORLAND)));
		assertTrue(info("temple").cultures().containsAll(Set.of(Culture.DESERT, Culture.MESA, Culture.STEPPE, Culture.TROPICAL, Culture.BAYOU)));
		assertTrue(info("lighthouse").cultures().contains(Culture.MARITIME));
		for (Culture c : Culture.values()) {
			boolean some = false;
			for (String id : IDS) {
				some |= info(id).suits(c);
			}
			assertTrue(some, c + " has no landmark");
		}
	}

	@Test
	void landmarksCarryWhatFolkUse() {
		for (Culture c : Culture.values()) {
			for (String id : IDS) {
				ArchetypeInfo info = info(id);
				BlueprintImpl bp = build(id, c, 4242L + c.ordinal(), info.maxFrontage(), info.maxDepth(), "flat", new CompoundTag(), 3);
				EnumSet<PoiType> types = EnumSet.noneOf(PoiType.class);
				bp.pois().forEach(p -> types.add(p.type()));
				String where = id + " in " + c.getSerializedName();
				assertTrue(types.contains(PoiType.DOOR), where + " has no door");
				switch (id) {
					case "cathedral", "temple" -> {
						assertTrue(types.contains(PoiType.ALTAR), where + " has no altar");
						assertTrue(bp.pois().stream().anyMatch(p -> p.job() == JobType.PRIEST), where + " has no priest's lectern");
						assertTrue(types.contains(PoiType.MEETING_POINT), where + " has no meeting point");
					}
					case "bell_tower", "clock_tower", "lighthouse", "pagoda" -> assertTrue(types.contains(PoiType.VIEWPOINT), where + " has no viewpoint");
					case "great_hall" -> {
						assertTrue(types.contains(PoiType.HEARTH), where + " has no hearth");
						assertTrue(types.contains(PoiType.SEAT), where + " has no seats");
					}
					default -> {
					}
				}
				if (id.equals("lighthouse")) {
					assertTrue(bp.beds() >= 1, where + ": the keeper has no bed");
				}
			}
		}
	}

	@Test
	void belfriesHaveBellsAndClockTowersDials() {
		for (Culture c : new Culture[]{Culture.TEMPERATE, Culture.NORTHERN, Culture.MEDITERRANEAN, Culture.ALPINE, Culture.DESERT}) {
			BlueprintImpl bell = build("bell_tower", c, 99L, 9, 9, "flat", new CompoundTag(), 3);
			assertTrue(count(bell, Blocks.BELL) >= 1, "no bell in the " + c + " bell tower");
			BlueprintImpl clock = build("clock_tower", c, 99L, 9, 9, "flat", new CompoundTag(), 4);
			assertTrue(count(clock, Blocks.BELL) >= 1, "no bell in the " + c + " clock tower");
			// four dials of black hands on white faces
			int white = count(clock, Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.WHITE));
			int black = count(clock, Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.BLACK));
			assertTrue(white >= 4 * 9 && black >= 4 * 2, "the " + c + " clock tower has no dials (" + white + " white, " + black + " black)");
		}
	}

	@Test
	void theLighthouseMoorsBoatsWhereTheWaterReaches() {
		// across a river, the plot's back half in the water
		ArchetypeInfo info = info("lighthouse");
		SyntheticTerrain probe = SyntheticTerrain.named("river", 42);
		int hits = 0;
		for (long seed = 1; seed <= 6; seed++) {
			BlueprintImpl bp = SERVICE.compiled(spec(info, Culture.MARITIME, seed, 13, 21, Direction.WEST, probe, new CompoundTag(), 3), probe);
			if (bp.pois().stream().anyMatch(p -> p.type() == PoiType.DOCK)) {
				hits++;
			}
			assertTrue(BlueprintChecks.all(bp, probe).isEmpty(), "lighthouse on the river: " + BlueprintChecks.all(bp, probe));
		}
		assertTrue(hits >= 1, "no jetty reached the water");
	}

	@Test
	void theCathedralStandsOnTheSmallestAndLargestClose() {
		ArchetypeInfo info = info("cathedral");
		for (Culture c : info.cultures()) {
			for (int[] size : new int[][]{{info.minFrontage(), info.minDepth()}, {info.maxFrontage(), info.maxDepth()}, {24, 33}}) {
				for (String towers : new String[]{"twin", "crossing", "campanile"}) {
					CompoundTag params = new CompoundTag();
					params.putString("towers", towers);
					SyntheticTerrain probe = SyntheticTerrain.named("hills", 7);
					BlueprintImpl bp = SERVICE.compiled(spec(info, c, 77L + size[0], size[0], size[1], Direction.NORTH, probe, params, 4), probe);
					List<String> problems = BlueprintChecks.all(bp, probe);
					assertTrue(problems.isEmpty(), "cathedral " + c + " " + size[0] + "x" + size[1] + " " + towers + ": " + problems);
					assertTrue(bp.pois().stream().filter(p -> p.type() == PoiType.SEAT).count() >= 8, "a cathedral without pews");
				}
			}
		}
	}

	@Test
	void theSkeletonRisesBeforeTheWalls() {
		// a half-built cathedral shows its piers and buttresses: the FRAME stage carries stone, and comes before WALLS
		BlueprintImpl bp = build("cathedral", Culture.TEMPERATE, 5L, 21, 37, "flat", new CompoundTag(), 4);
		int frame = -1;
		int walls = -1;
		for (int s = 0; s < bp.stageCount(); s++) {
			if (bp.stageKind(s) == StageKind.FRAME) {
				frame = s;
			}
			if (bp.stageKind(s) == StageKind.WALLS) {
				walls = s;
			}
		}
		assertTrue(frame >= 0 && walls > frame, "no FRAME stage before WALLS");
		assertTrue(bp.goods(frame)[Goods.STONE.ordinal()] > 100, "the frame stage lays little stone: " + bp.goods(frame)[Goods.STONE.ordinal()]);
		// the roofs and spires come after the walls
		boolean roofAfter = false;
		for (int s = walls + 1; s < bp.stageCount(); s++) {
			roofAfter |= bp.stageKind(s) == StageKind.ROOF;
		}
		assertTrue(roofAfter, "no ROOF stage after the walls");
	}

	/** Prints every landmark's bill per stage at its largest size and keeps each stage's goods a builder's load. */
	@Test
	void stagesStayABuildersLoad() {
		List<String> report = new ArrayList<>();
		int worst = 0;
		String worstAt = "";
		for (String id : IDS) {
			ArchetypeInfo info = info(id);
			for (Culture c : new Culture[]{Culture.TEMPERATE, Culture.KARST, Culture.DESERT, Culture.NORTHERN, Culture.MARITIME}) {
				BlueprintImpl bp = build(id, c, 31L, info.maxFrontage(), info.maxDepth(), "hills", new CompoundTag(), 4);
				StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "%-12s %-10s", id, c.getSerializedName()));
				for (int s = 0; s < bp.stageCount(); s++) {
					int sum = 0;
					for (int g : bp.goods(s)) {
						sum += g;
					}
					line.append(String.format(Locale.ROOT, " %s:%d", bp.stageKind(s).name().toLowerCase(Locale.ROOT), sum));
					if (sum > worst) {
						worst = sum;
						worstAt = id + " " + c + " " + bp.stageKind(s);
					}
				}
				report.add(line.toString());
			}
		}
		report.forEach(System.out::println);
		System.out.println("heaviest stage: " + worst + " at " + worstAt);
		assertTrue(worst <= 9000, "a stage needs " + worst + " goods (" + worstAt + ")");
	}

	@Test
	void landmarksKeepUnderTheirHeight() {
		for (String id : IDS) {
			ArchetypeInfo info = info(id);
			for (Culture c : Culture.values()) {
				BlueprintImpl bp = build(id, c, 13L, info.minFrontage(), info.minDepth(), "slope", new CompoundTag(), 3);
				int top = bp.bounds().maxY() - bp.spec().floorY();
				assertTrue(top < info.maxHeight(), id + " in " + c + " reaches " + top);
				// and it is a landmark: well above an ordinary roof line
				assertTrue(top >= 12, id + " in " + c + " is only " + top + " high");
			}
		}
	}

	private static int count(BlueprintImpl bp, net.minecraft.world.level.block.Block block) {
		int n = 0;
		for (int s = 0; s < bp.stageCount(); s++) {
			for (int i = 0; i < bp.stepCount(s); i++) {
				BlockState st = bp.state(s, i);
				if (st.is(block)) {
					n++;
				}
			}
		}
		return n;
	}

	@SuppressWarnings("unused")
	private static Identifier id(String path) {
		return Hearth.id(path);
	}
}
