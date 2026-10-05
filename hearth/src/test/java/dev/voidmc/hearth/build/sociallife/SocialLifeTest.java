package dev.voidmc.hearth.build.sociallife;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * What the social-life family promises beyond the shared invariants: the points of interest the social, economy and law
 * modules rely on (a tavern's bar, stage and dice tables; a cemetery's graves; a festival ground's stage and meeting
 * points; an infirmary's sickbeds...) in every culture and tier; the invariants on every terrain and at the lowest height
 * the planner may give; the params each archetype documents. Runs on the family alone, so an architect can iterate on it
 * without the whole catalogue.
 */
class SocialLifeTest {
	private static final BlueprintServiceImpl SERVICE;
	private static final Direction[] FACINGS = {Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST};
	private static final String[] TERRAINS = {"flat", "slope", "hills", "river", "coast", "steep"};

	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		SERVICE = BuildModule.standalone();
	}

	/** The points each archetype must register in every culture (beyond its jobs). */
	static final Map<String, Set<PoiType>> REQUIRED = new LinkedHashMap<>();

	static {
		REQUIRED.put("commerce/tavern", EnumSet.of(PoiType.COUNTER, PoiType.MARKET_STALL, PoiType.STAGE, PoiType.GAMING_TABLE, PoiType.HEARTH, PoiType.SEAT,
			PoiType.DOOR));
		REQUIRED.put("civic/cemetery", EnumSet.of(PoiType.SEAT, PoiType.DOOR));
		REQUIRED.put("commerce/hostel", EnumSet.of(PoiType.BED, PoiType.COUNTER, PoiType.TABLE, PoiType.SEAT, PoiType.HEARTH, PoiType.DOOR));
		REQUIRED.put("civic/infirmary", EnumSet.of(PoiType.SICKBED, PoiType.STORAGE, PoiType.ALTAR, PoiType.HEARTH, PoiType.DOOR));
		REQUIRED.put("civic/almshouse", EnumSet.of(PoiType.BED, PoiType.STORAGE, PoiType.TABLE, PoiType.SEAT, PoiType.HEARTH, PoiType.ALTAR, PoiType.DOOR));
		REQUIRED.put("civic/orphanage", EnumSet.of(PoiType.BED, PoiType.LECTERN, PoiType.TABLE, PoiType.SEAT, PoiType.HEARTH, PoiType.STORAGE, PoiType.DOOR));
		REQUIRED.put("commerce/thieves_den", EnumSet.of(PoiType.COUNTER, PoiType.STRONGBOX, PoiType.STORAGE, PoiType.HEARTH, PoiType.DOOR));
		REQUIRED.put("civic/playhouse", EnumSet.of(PoiType.STAGE, PoiType.SEAT, PoiType.STORAGE, PoiType.MEETING_POINT, PoiType.DOOR));
		REQUIRED.put("landscape/festival_ground", EnumSet.of(PoiType.STAGE, PoiType.MEETING_POINT, PoiType.TABLE, PoiType.SEAT));
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

	static PlotSpec spec(ArchetypeInfo info, Culture c, long seed, Direction facing, TerrainProbe probe, int maxHeight, int tier, CompoundTag params) {
		Random r = new Random(seed * 31 + info.id().hashCode());
		int frontage = info.minFrontage() + r.nextInt(info.maxFrontage() - info.minFrontage() + 1);
		int depth = info.minDepth() + r.nextInt(info.maxDepth() - info.minDepth() + 1);
		return spec(info, c, seed, facing, probe, maxHeight, tier, params, frontage, depth);
	}

	static PlotSpec spec(ArchetypeInfo info, Culture c, long seed, Direction facing, TerrainProbe probe, int maxHeight, int tier, CompoundTag params,
		int frontage, int depth) {
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

	static Stream<Arguments> cases() {
		List<Arguments> out = new ArrayList<>();
		for (ArchetypeInfo a : family()) {
			for (String t : TERRAINS) {
				out.add(Arguments.of(a.id().getPath(), t));
			}
		}
		return out.stream();
	}

	/** The shared invariants in every culture, three seeds, turning facings, at the top and the bottom of the height range. */
	@ParameterizedTest(name = "{0} on {1}")
	@MethodSource("cases")
	void invariantsHold(String id, String terrain) {
		ArchetypeInfo info = SERVICE.archetype(Hearth.id(id));
		List<String> problems = new ArrayList<>();
		int n = 0;
		SyntheticTerrain probe = SyntheticTerrain.named(terrain, 42);
		for (Culture c : Culture.values()) {
			if (!info.suits(c)) {
				continue;
			}
			for (int s = 0; s < 3; s++) {
				long seed = 6151L * (s + 1) + c.ordinal() * 7919L;
				Direction facing = FACINGS[(s + c.ordinal()) % 4];
				int height = s == 2 ? info.minHeight() : info.maxHeight();
				int tier = 1 + (s + c.ordinal()) % 4;
				PlotSpec spec = spec(info, c, seed, facing, probe, height, tier, new CompoundTag());
				BlueprintImpl bp = SERVICE.compiled(spec, probe);
				for (String p : BlueprintChecks.all(bp, probe)) {
					problems.add(c.getSerializedName() + " seed " + seed + " " + facing.getSerializedName() + " h" + height + " " + spec.frontage() + "x"
						+ spec.depth() + ": " + p);
				}
				n++;
			}
		}
		assertTrue(problems.isEmpty(), id + " on " + terrain + ", " + problems.size() + " of " + n + " problems:\n  " + String.join("\n  ", problems));
	}

	/** Every size the archetype announces, at the corners of its range, on rough ground. */
	@ParameterizedTest(name = "{0} at its extremes")
	@MethodSource("ids")
	void everySizeHolds(String id) {
		ArchetypeInfo info = SERVICE.archetype(Hearth.id(id));
		List<String> problems = new ArrayList<>();
		int k = 0;
		for (String terrain : new String[]{"flat", "hills", "steep"}) {
			SyntheticTerrain probe = SyntheticTerrain.named(terrain, 17);
			for (int f : new int[]{info.minFrontage(), info.maxFrontage()}) {
				for (int dp : new int[]{info.minDepth(), info.maxDepth()}) {
					for (int h : new int[]{info.minHeight(), info.maxHeight()}) {
						Culture c = Culture.values()[k++ % Culture.values().length];
						if (!info.suits(c)) {
							c = info.cultures().iterator().next();
						}
						PlotSpec spec = spec(info, c, 9000L + k, FACINGS[k % 4], probe, h, 2, new CompoundTag(), f, dp);
						for (String p : BlueprintChecks.all(SERVICE.compiled(spec, probe), probe)) {
							problems.add(terrain + " " + c.getSerializedName() + " " + f + "x" + dp + " h" + h + ": " + p);
						}
					}
				}
			}
		}
		assertTrue(problems.isEmpty(), id + ", " + problems.size() + " problems:\n  " + String.join("\n  ", problems));
	}

	static Stream<Arguments> ids() {
		return REQUIRED.keySet().stream().map(Arguments::of);
	}

	@Test
	void everyBuildingRegistersWhatItsFolkUse() {
		List<String> problems = new ArrayList<>();
		SyntheticTerrain probe = SyntheticTerrain.named("flat", 3);
		for (ArchetypeInfo info : family()) {
			Set<PoiType> want = REQUIRED.get(info.id().getPath());
			for (Culture c : Culture.values()) {
				if (!info.suits(c)) {
					continue;
				}
				for (int s = 0; s < 3; s++) {
					long seed = 5150L + s * 977L + c.ordinal() * 31L;
					for (int tier : new int[]{1, 3}) {
						int height = s == 1 ? info.minHeight() : info.maxHeight();
						BlueprintImpl bp = SERVICE.compiled(spec(info, c, seed, FACINGS[(s + c.ordinal()) % 4], probe, height, tier, new CompoundTag()), probe);
						EnumSet<PoiType> have = EnumSet.noneOf(PoiType.class);
						for (Poi p : bp.pois()) {
							have.add(p.type());
						}
						for (PoiType t : want) {
							if (!have.contains(t)) {
								problems.add(info.id().getPath() + " " + c.getSerializedName() + " seed " + seed + " tier " + tier + " h" + height + " lacks " + t);
							}
						}
						for (JobType job : info.jobs()) {
							if (bp.pois().stream().noneMatch(p -> p.type() == PoiType.JOB_SITE && p.job() == job)) {
								problems.add(info.id().getPath() + " " + c.getSerializedName() + " seed " + seed + " has no job site for " + job);
							}
						}
						if (info.beds() > 0 && bp.beds() < info.beds()) {
							problems.add(info.id().getPath() + " " + c.getSerializedName() + " seed " + seed + " has " + bp.beds() + " beds, announces " + info.beds());
						}
					}
				}
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " problems:\n  " + String.join("\n  ", problems));
	}

	@Test
	void sameSpecSameBlueprint() {
		SyntheticTerrain probe = SyntheticTerrain.named("hills", 7);
		for (ArchetypeInfo info : family()) {
			for (Culture c : Culture.values()) {
				if (!info.suits(c)) {
					continue;
				}
				PlotSpec spec = spec(info, c, 12345L, Direction.EAST, probe, info.maxHeight(), 2, new CompoundTag());
				BlueprintImpl a = dev.voidmc.hearth.build.blueprint.Compiler.compile(SERVICE.draw(spec, probe));
				BlueprintImpl b = dev.voidmc.hearth.build.blueprint.Compiler.compile(SERVICE.draw(spec, probe));
				assertTrue(BlueprintChecks.same(a, b), info.id() + " in " + c + " is not deterministic");
			}
		}
	}

	/** Every style variant of every culture, forced (on a slope, one seed, turning facings). */
	@ParameterizedTest(name = "{0} in every variant")
	@MethodSource("ids")
	void everyVariantHolds(String id) {
		ArchetypeInfo info = SERVICE.archetype(Hearth.id(id));
		List<String> problems = new ArrayList<>();
		SyntheticTerrain probe = SyntheticTerrain.named("slope", 42);
		for (Culture c : Culture.values()) {
			if (!info.suits(c)) {
				continue;
			}
			var style = SERVICE.styles().get(StyleRegistry.styleId(c));
			for (int v = 0; v < style.variants().size(); v++) {
				CompoundTag params = new CompoundTag();
				params.putInt("variant", v);
				long seed = 31337L + v * 7L + c.ordinal() * 1009L;
				PlotSpec spec = spec(info, c, seed, FACINGS[(v + c.ordinal()) % 4], probe, info.maxHeight(), 2, params);
				for (String p : BlueprintChecks.all(SERVICE.compiled(spec, probe), probe)) {
					problems.add(c.getSerializedName() + " (" + style.variants().get(v).name() + ") seed " + seed + ": " + p);
				}
			}
		}
		assertTrue(problems.isEmpty(), id + ", " + problems.size() + " problems:\n  " + String.join("\n  ", problems));
	}

	/** Each stage's bill of goods stays within a few thousand units, as the economy's builders can carry. */
	@Test
	void billsStayCarriable() {
		List<String> problems = new ArrayList<>();
		SyntheticTerrain probe = SyntheticTerrain.named("hills", 5);
		for (ArchetypeInfo info : family()) {
			for (Culture c : new Culture[]{Culture.TEMPERATE, Culture.DESERT, Culture.NORTHERN, Culture.KARST, Culture.MEDITERRANEAN, Culture.STEPPE}) {
				if (!info.suits(c)) {
					continue;
				}
				PlotSpec spec = spec(info, c, 777L, Direction.SOUTH, probe, info.maxHeight(), 3, new CompoundTag(), info.maxFrontage(), info.maxDepth());
				BlueprintImpl bp = SERVICE.compiled(spec, probe);
				for (int s = 0; s < bp.stageCount(); s++) {
					int total = 0;
					for (int v : bp.goods(s)) {
						total += v;
					}
					if (total > 4000) {
						problems.add(info.id().getPath() + " " + c.getSerializedName() + " stage " + bp.stageKind(s) + " asks " + total + " units");
					}
				}
			}
		}
		assertTrue(problems.isEmpty(), String.join("\n  ", problems));
	}
}
