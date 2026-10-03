package dev.voidmc.hearth.build.civic;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.BuildModule;
import dev.voidmc.hearth.build.archetype.commerce.Inn;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.check.BlueprintChecks;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.style.StyleRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

/**
 * What the civic and commercial family promises beyond the shared invariants: the points of interest each building's
 * folk rely on (counters for merchants, the altar for the priest, the meeting point and notice board of the town hall,
 * guard posts...), in every culture; bells where the brief asks for them; that buildings fit the lowest height the
 * planner may give them; and that every inn name the sign can show has its translations.
 */
class CivicArchetypesTest {
	private static final BlueprintServiceImpl SERVICE;
	private static final Direction[] FACINGS = {Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST};

	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		SERVICE = BuildModule.standalone();
	}

	/** The points each archetype must register in every culture (beyond its jobs, which the invariants test checks). */
	private static final Map<String, Set<PoiType>> REQUIRED = Map.of(
		"commerce/market_hall", EnumSet.of(PoiType.COUNTER, PoiType.MARKET_STALL, PoiType.JOB_SITE, PoiType.MEETING_POINT, PoiType.DOOR),
		"commerce/inn", EnumSet.of(PoiType.COUNTER, PoiType.MARKET_STALL, PoiType.JOB_SITE, PoiType.HEARTH, PoiType.BED, PoiType.SEAT, PoiType.DOOR),
		"commerce/bank", EnumSet.of(PoiType.COUNTER, PoiType.MARKET_STALL, PoiType.JOB_SITE, PoiType.DOOR),
		"civic/chapel", EnumSet.of(PoiType.ALTAR, PoiType.SEAT, PoiType.JOB_SITE, PoiType.DOOR),
		"civic/town_hall", EnumSet.of(PoiType.MEETING_POINT, PoiType.NOTICE_BOARD, PoiType.SEAT, PoiType.DOOR, PoiType.LECTERN),
		"civic/library", EnumSet.of(PoiType.LECTERN, PoiType.JOB_SITE, PoiType.SEAT, PoiType.TABLE, PoiType.STORAGE),
		"civic/school", EnumSet.of(PoiType.LECTERN, PoiType.SEAT, PoiType.DOOR),
		"civic/bathhouse", EnumSet.of(PoiType.SEAT, PoiType.MEETING_POINT, PoiType.DOOR),
		"civic/guardhouse", EnumSet.of(PoiType.GUARD_POST, PoiType.JOB_SITE, PoiType.BED, PoiType.DOOR));

	private static PlotSpec spec(ArchetypeInfo info, Culture c, long seed, Direction facing, TerrainProbe probe, int maxHeight, int tier) {
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
		return new PlotSpec(info.id(), StyleRegistry.styleId(c), seed, new BlockPos(ox, floor, oz), sizeX, sizeZ, facing, maxHeight, tier, new CompoundTag());
	}

	private static List<ArchetypeInfo> family() {
		List<ArchetypeInfo> out = new ArrayList<>();
		for (String id : REQUIRED.keySet()) {
			ArchetypeInfo info = SERVICE.archetype(Hearth.id(id));
			assertTrue(info != null, id + " is not registered");
			out.add(info);
		}
		return out;
	}

	@Test
	void everyBuildingRegistersWhatItsFolkUse() {
		List<String> problems = new ArrayList<>();
		SyntheticTerrain probe = SyntheticTerrain.named("flat", 3);
		for (ArchetypeInfo info : family()) {
			Set<PoiType> want = REQUIRED.get(info.id().getPath());
			for (Culture c : Culture.values()) {
				for (int s = 0; s < 2; s++) {
					long seed = 5150L + s * 977L + c.ordinal() * 31L;
					for (int tier : new int[]{2, 4}) {
						BlueprintImpl bp = SERVICE.compiled(spec(info, c, seed, FACINGS[(s + c.ordinal()) % 4], probe, info.maxHeight(), tier), probe);
						EnumSet<PoiType> have = EnumSet.noneOf(PoiType.class);
						for (Poi p : bp.pois()) {
							have.add(p.type());
						}
						for (PoiType t : want) {
							if (!have.contains(t)) {
								problems.add(info.id().getPath() + " " + c.getSerializedName() + " seed " + seed + " tier " + tier + " lacks " + t);
							}
						}
						for (JobType job : info.jobs()) {
							if (bp.pois().stream().noneMatch(p -> p.type() == PoiType.JOB_SITE && p.job() == job)) {
								problems.add(info.id().getPath() + " " + c.getSerializedName() + " has no job site for " + job);
							}
						}
					}
				}
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " problems:\n  " + String.join("\n  ", problems));
	}

	@Test
	void chapelsAndTownHallsHangABell() {
		List<String> problems = new ArrayList<>();
		SyntheticTerrain probe = SyntheticTerrain.named("flat", 3);
		for (String id : new String[]{"civic/chapel", "civic/town_hall"}) {
			ArchetypeInfo info = SERVICE.archetype(Hearth.id(id));
			for (Culture c : Culture.values()) {
				for (int tier : new int[]{2, 3, 4}) {
					BlueprintImpl bp = SERVICE.compiled(spec(info, c, 777L + tier, Direction.SOUTH, probe, info.maxHeight(), tier), probe);
					if (!contains(bp, s -> s.getBlock() instanceof BellBlock)) {
						problems.add(id + " " + c.getSerializedName() + " tier " + tier + " has no bell");
					}
				}
			}
		}
		assertTrue(problems.isEmpty(), String.join("\n  ", problems));
	}

	@Test
	void townHallPutsUpTheNoticeBoardOrASignForIt() {
		ArchetypeInfo info = SERVICE.archetype(Hearth.id("civic/town_hall"));
		SyntheticTerrain probe = SyntheticTerrain.named("flat", 3);
		for (Culture c : Culture.values()) {
			BlueprintImpl bp = SERVICE.compiled(spec(info, c, 4242L, Direction.SOUTH, probe, info.maxHeight(), 3), probe);
			boolean board = contains(bp, s -> s.getBlock() instanceof StandingSignBlock
				|| net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString().equals("hearth:notice_board"));
			assertTrue(board, "town hall in " + c + " has neither the notice board nor its sign");
		}
	}

	/** The planner gives commerce less height away from the core: every building must still fit and stand. */
	@Test
	void everyBuildingFitsTheLowestHeightItAnnounces() {
		List<String> problems = new ArrayList<>();
		for (String terrain : new String[]{"flat", "slope", "hills"}) {
			SyntheticTerrain probe = SyntheticTerrain.named(terrain, 11);
			for (ArchetypeInfo info : family()) {
				for (Culture c : Culture.values()) {
					long seed = 90210L + c.ordinal();
					PlotSpec spec = spec(info, c, seed, FACINGS[c.ordinal() % 4], probe, info.minHeight(), 2);
					BlueprintImpl bp = SERVICE.compiled(spec, probe);
					for (String p : BlueprintChecks.all(bp, probe)) {
						problems.add(info.id().getPath() + " " + c.getSerializedName() + " " + terrain + " at height " + info.minHeight() + ": " + p);
					}
				}
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " problems:\n  " + String.join("\n  ", problems));
	}

	@Test
	void everyCultureHasATradition() {
		for (Culture c : Culture.values()) {
			assertTrue(Tradition.of(c) != null, c + " has no tradition");
		}
	}

	@Test
	void everyInnNameIsTranslated() throws Exception {
		JsonObject lang;
		// the lang fragments are merged into en_us.json at build time and are not on the test classpath: read the source
		Path file = Path.of("src/main/resources/assets/hearth/lang/en_us/build_civic.json");
		assertTrue(Files.exists(file), "build_civic.json is missing");
		try (var in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			lang = JsonParser.parseReader(in).getAsJsonObject();
		}
		for (String[] name : Inn.NAMES) {
			for (int line = 1; line <= 2; line++) {
				String key = "hearth.build.civic.inn." + name[0] + "." + line;
				assertTrue(lang.has(key), key + " is not in build_civic.json");
			}
		}
		for (ArchetypeInfo info : family()) {
			String key = "hearth.archetype." + info.id().getPath().replace('/', '.');
			assertTrue(lang.has(key), key + " is not in build_civic.json");
		}
	}

	private static boolean contains(BlueprintImpl bp, Predicate<BlockState> test) {
		for (int s = 0; s < bp.stageCount(); s++) {
			for (int i = 0; i < bp.stepCount(s); i++) {
				if (test.test(bp.state(s, i))) {
					return true;
				}
			}
		}
		return false;
	}
}
