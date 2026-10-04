package dev.voidmc.hearth.build.learning;

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
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.check.BlueprintChecks;
import dev.voidmc.hearth.build.style.StyleRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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
 * The clergy and learning family (LIVING_WORLD.md §8.1, §8.5): what each building promises beyond the shared invariants
 * (the points of interest its folk use, in every culture and tier), and the shared invariants themselves run over the
 * family alone at every size the planner may ask for (smallest, largest, the lowest height), so a family change can be
 * checked in seconds rather than with the whole catalogue.
 */
class LearningArchetypesTest {
	private static final BlueprintServiceImpl SERVICE;
	private static final Direction[] FACINGS = {Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST};
	private static final String[] TERRAINS = {"flat", "slope", "hills", "river", "coast", "steep"};

	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		SERVICE = BuildModule.standalone();
	}

	/** The points each archetype must register in every culture (beyond its jobs). */
	private static final Map<String, Set<PoiType>> REQUIRED = new LinkedHashMap<>();

	static {
		REQUIRED.put("civic/shrine", EnumSet.of(PoiType.ALTAR));
		REQUIRED.put("civic/observatory", EnumSet.of(PoiType.JOB_SITE, PoiType.LECTERN, PoiType.VIEWPOINT, PoiType.STORAGE, PoiType.DOOR));
		REQUIRED.put("work/alchemist_tower", EnumSet.of(PoiType.JOB_SITE, PoiType.LECTERN, PoiType.BED, PoiType.STORAGE, PoiType.VIEWPOINT, PoiType.DOOR));
	}

	static List<ArchetypeInfo> family() {
		List<ArchetypeInfo> out = new ArrayList<>();
		for (String id : REQUIRED.keySet()) {
			ArchetypeInfo info = SERVICE.archetype(Hearth.id(id));
			assertTrue(info != null, id + " is not registered");
			out.add(info);
		}
		return out;
	}

	static PlotSpec spec(ArchetypeInfo info, Culture c, long seed, Direction facing, TerrainProbe probe, int maxHeight, int tier, int frontage, int depth,
		CompoundTag params) {
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
		return new PlotSpec(info.id(), StyleRegistry.styleId(c), seed, new BlockPos(ox, floor, oz), sizeX, sizeZ, facing, maxHeight, tier, params);
	}

	static PlotSpec spec(ArchetypeInfo info, Culture c, long seed, Direction facing, TerrainProbe probe, int maxHeight, int tier) {
		Random r = new Random(seed * 31 + info.id().hashCode());
		int frontage = info.minFrontage() + r.nextInt(info.maxFrontage() - info.minFrontage() + 1);
		int depth = info.minDepth() + r.nextInt(info.maxDepth() - info.minDepth() + 1);
		return spec(info, c, seed, facing, probe, maxHeight, tier, frontage, depth, new CompoundTag());
	}

	static Stream<Arguments> cases() {
		List<Arguments> out = new ArrayList<>();
		for (ArchetypeInfo a : family()) {
			for (String terrain : TERRAINS) {
				out.add(Arguments.of(a.id().getPath(), terrain));
			}
		}
		return out.stream();
	}

	/** The shared invariants over the family: every culture, both extreme sizes, two tiers, turning facings. */
	@ParameterizedTest(name = "{0} on {1}")
	@MethodSource("cases")
	void invariantsHoldAtEverySize(String id, String terrain) {
		ArchetypeInfo info = SERVICE.archetype(Hearth.id(id));
		List<String> problems = new ArrayList<>();
		SyntheticTerrain probe = SyntheticTerrain.named(terrain, 42);
		int n = 0;
		for (Culture c : Culture.values()) {
			int k = 0;
			for (int[] size : new int[][]{{info.minFrontage(), info.minDepth()}, {info.maxFrontage(), info.maxDepth()},
				{info.minFrontage(), info.maxDepth()}, {info.maxFrontage(), info.minDepth()}}) {
				long seed = 1009L * (k + 1) + c.ordinal() * 7919L;
				Direction facing = FACINGS[(k + c.ordinal()) % 4];
				int tier = k % 2 == 0 ? 2 : 4;
				PlotSpec spec = spec(info, c, seed, facing, probe, info.maxHeight(), tier, size[0], size[1], new CompoundTag());
				for (String p : BlueprintChecks.all(SERVICE.compiled(spec, probe), probe)) {
					problems.add(c.getSerializedName() + " " + size[0] + "x" + size[1] + " tier " + tier + " seed " + seed + " " + facing.getSerializedName() + ": " + p);
				}
				k++;
				n++;
			}
		}
		assertTrue(problems.isEmpty(), id + " on " + terrain + ", " + problems.size() + " of " + n + " problems:\n  " + String.join("\n  ", problems));
	}

	@Test
	void everyBuildingRegistersWhatItsFolkUse() {
		List<String> problems = new ArrayList<>();
		SyntheticTerrain probe = SyntheticTerrain.named("flat", 3);
		for (ArchetypeInfo info : family()) {
			Set<PoiType> want = REQUIRED.get(info.id().getPath());
			for (Culture c : Culture.values()) {
				for (int s = 0; s < 3; s++) {
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
								problems.add(info.id().getPath() + " " + c.getSerializedName() + " seed " + seed + " has no job site for " + job);
							}
						}
						if (info.beds() > 0 && bp.beds() < 1) {
							problems.add(info.id().getPath() + " " + c.getSerializedName() + " seed " + seed + " has no bed");
						}
					}
				}
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " problems:\n  " + String.join("\n  ", problems));
	}

	/** The planner may give less than the most: every building must still fit and stand at its lowest height. */
	@Test
	void everyBuildingFitsTheLowestHeightItAnnounces() {
		List<String> problems = new ArrayList<>();
		for (String terrain : new String[]{"flat", "slope", "hills"}) {
			SyntheticTerrain probe = SyntheticTerrain.named(terrain, 11);
			for (ArchetypeInfo info : family()) {
				for (Culture c : Culture.values()) {
					long seed = 90210L + c.ordinal();
					PlotSpec spec = spec(info, c, seed, FACINGS[c.ordinal() % 4], probe, info.minHeight(), 2);
					for (String p : BlueprintChecks.all(SERVICE.compiled(spec, probe), probe)) {
						problems.add(info.id().getPath() + " " + c.getSerializedName() + " " + terrain + " at height " + info.minHeight() + ": " + p);
					}
				}
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " problems:\n  " + String.join("\n  ", problems));
	}

	/** Every variant of every culture's style, forced, on rough ground. */
	@Test
	void everyVariantHolds() {
		List<String> problems = new ArrayList<>();
		SyntheticTerrain probe = SyntheticTerrain.named("hills", 5);
		for (ArchetypeInfo info : family()) {
			for (Culture c : Culture.values()) {
				var style = SERVICE.styles().get(StyleRegistry.styleId(c));
				for (int v = 0; v < style.variants().size(); v++) {
					CompoundTag params = new CompoundTag();
					params.putInt("variant", v);
					long seed = 31337L + v * 7L + c.ordinal() * 1009L;
					Random r = new Random(seed);
					int f = info.minFrontage() + r.nextInt(info.maxFrontage() - info.minFrontage() + 1);
					int dp = info.minDepth() + r.nextInt(info.maxDepth() - info.minDepth() + 1);
					PlotSpec spec = spec(info, c, seed, FACINGS[(v + c.ordinal()) % 4], probe, info.maxHeight(), 3, f, dp, params);
					for (String p : BlueprintChecks.all(SERVICE.compiled(spec, probe), probe)) {
						problems.add(info.id().getPath() + " " + c.getSerializedName() + " (" + style.variants().get(v).name() + "): " + p);
					}
				}
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " problems:\n  " + String.join("\n  ", problems));
	}

	@Test
	void everyArchetypeHasItsName() throws Exception {
		Path file = Path.of("src/main/resources/assets/hearth/lang/en_us/build_learning.json");
		JsonObject lang = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
		for (ArchetypeInfo info : family()) {
			String key = "hearth.archetype." + info.id().getPath().replace('/', '.');
			assertTrue(lang.has(key), key + " has no name in build_learning.json");
		}
	}
}
