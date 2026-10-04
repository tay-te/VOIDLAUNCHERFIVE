package dev.voidmc.hearth.build.station;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.blueprint.LocalFrame;
import dev.voidmc.hearth.build.check.BlueprintChecks;
import dev.voidmc.hearth.build.style.StyleRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The homes by station (docs/LIVING_WORLD.md §8.1) beyond the shared invariants: every home sound at every size and
 * height the catalogue allows (the planner asks for the low end of the height range out of town), with the station the
 * planner gives it and without; every home sleeps its household; attached homes in rows with blind party walls; and
 * the places each household needs (the merchant's counter and strongbox, the reeve's office and notice board, the
 * priest's and the scribe's lectern, the hermit's shrine, the fire of the poor).
 */
class StationHomesTest {
	private static final String[] TERRAINS = {"flat", "slope", "hills", "river", "coast", "steep"};
	private static final Direction[] FACINGS = {Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST};

	static Stream<Arguments> cases() {
		List<Arguments> out = new ArrayList<>();
		for (ArchetypeInfo a : StationSupport.registered()) {
			for (String terrain : TERRAINS) {
				out.add(Arguments.of(a.id().getPath(), terrain));
			}
		}
		return out.stream();
	}

	/** Every culture, two seeds, the low and the high end of the height range, with and without the planner's station. */
	@ParameterizedTest(name = "{0} on {1}")
	@MethodSource("cases")
	void soundAtEveryHeight(String archetype, String terrain) {
		ArchetypeInfo info = StationSupport.info(archetype);
		List<String> problems = new ArrayList<>();
		for (Culture c : Culture.values()) {
			for (int s = 0; s < 4; s++) {
				long seed = 3301L * (s + 1) + c.ordinal() * 7717L;
				java.util.Random r = new java.util.Random(seed);
				int frontage = info.minFrontage() + r.nextInt(info.maxFrontage() - info.minFrontage() + 1);
				int depth = info.minDepth() + r.nextInt(info.maxDepth() - info.minDepth() + 1);
				int height = (s & 1) == 0 ? info.minHeight() : info.maxHeight();
				CompoundTag params = new CompoundTag();
				if (s >= 2) {
					params.putString("station", station(archetype));
				}
				Direction facing = FACINGS[(s + c.ordinal()) % 4];
				SyntheticTerrain probe = SyntheticTerrain.named(terrain, 42);
				PlotSpec spec = StationSupport.plot(info, StyleRegistry.styleId(c), seed, facing, probe, params, frontage, depth, height, s >= 2 ? 1 : 2);
				BlueprintImpl bp = StationSupport.SERVICE.compiled(spec, probe);
				for (String p : BlueprintChecks.all(bp, probe)) {
					problems.add(c.getSerializedName() + " seed " + seed + " " + facing.getSerializedName() + " " + frontage + "x" + depth + " h" + height + " "
						+ params + ": " + p);
				}
				if (bp.beds() < info.beds()) {
					problems.add(c.getSerializedName() + " seed " + seed + " " + frontage + "x" + depth + " h" + height + ": sleeps " + bp.beds() + " of "
						+ info.beds());
				}
			}
		}
		assertTrue(problems.isEmpty(), archetype + " on " + terrain + ", " + problems.size() + " problems:\n  " + String.join("\n  ", problems));
	}

	/** Every dressing of every culture forced, on a slope, at the low end of the height range. */
	@Test
	void soundInEveryDressing() {
		List<String> problems = new ArrayList<>();
		SyntheticTerrain probe = SyntheticTerrain.named("slope", 42);
		for (ArchetypeInfo info : StationSupport.registered()) {
			for (Culture c : Culture.values()) {
				var style = StationSupport.SERVICE.styles().get(StyleRegistry.styleId(c));
				for (int v = 0; v < style.variants().size(); v++) {
					CompoundTag params = new CompoundTag();
					params.putInt("variant", v);
					long seed = 51337L + v * 7L + c.ordinal() * 1009L;
					java.util.Random r = new java.util.Random(seed);
					int frontage = info.minFrontage() + r.nextInt(info.maxFrontage() - info.minFrontage() + 1);
					int depth = info.minDepth() + r.nextInt(info.maxDepth() - info.minDepth() + 1);
					Direction facing = FACINGS[(v + c.ordinal()) % 4];
					PlotSpec spec = StationSupport.plot(info, StyleRegistry.styleId(c), seed, facing, probe, params, frontage, depth, info.minHeight(), 2);
					BlueprintImpl bp = StationSupport.SERVICE.compiled(spec, probe);
					for (String p : BlueprintChecks.all(bp, probe)) {
						problems.add(info.id().getPath() + " " + c.getSerializedName() + " (" + style.variants().get(v).name() + ") seed " + seed + ": " + p);
					}
					if (bp.beds() < info.beds()) {
						problems.add(info.id().getPath() + " " + c.getSerializedName() + " (" + style.variants().get(v).name() + "): sleeps " + bp.beds());
					}
				}
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " problems:\n  " + String.join("\n  ", problems));
	}

	/** The station the planner gives each home. */
	static String station(String archetype) {
		return switch (archetype) {
			case "house/hovel", "house/shack", "house/hermit_hut" -> "poor";
			case "house/croft" -> "peasant";
			case "house/merchant_house" -> "merchant";
			case "house/reeve_house" -> "official";
			case "house/rectory" -> "clergy";
			case "house/scribe_house" -> "scholar";
			default -> "peasant";
		};
	}

	/** The corners of the catalogue's size and height box, every culture, on flat ground: sound, and sleeping its household. */
	@Test
	void soundAtEverySize() {
		SyntheticTerrain probe = SyntheticTerrain.named("flat", 1);
		List<String> problems = new ArrayList<>();
		for (ArchetypeInfo info : StationSupport.registered()) {
			int[][] sizes = {
				{info.minFrontage(), info.minDepth(), info.minHeight()},
				{info.maxFrontage(), info.minDepth(), info.minHeight()},
				{info.minFrontage(), info.maxDepth(), info.maxHeight()},
				{info.maxFrontage(), info.maxDepth(), info.minHeight()},
				{info.maxFrontage(), info.maxDepth(), info.maxHeight()}};
			for (Culture c : Culture.values()) {
				for (int i = 0; i < sizes.length; i++) {
					int[] z = sizes[i];
					long seed = 900L + i * 31L + c.ordinal();
					PlotSpec spec = StationSupport.plot(info, StyleRegistry.styleId(c), seed, Direction.SOUTH, probe, new CompoundTag(), z[0], z[1], z[2], 2);
					BlueprintImpl bp = StationSupport.SERVICE.compiled(spec, probe);
					String where = info.id().getPath() + " " + c.getSerializedName() + " " + z[0] + "x" + z[1] + " h" + z[2];
					for (String p : BlueprintChecks.all(bp, probe)) {
						problems.add(where + ": " + p);
					}
					if (bp.beds() < info.beds()) {
						problems.add(where + " sleeps " + bp.beds() + " of " + info.beds());
					}
				}
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " problems:\n  " + String.join("\n  ", problems));
	}

	/** Attached homes in a row of four in every culture: every house sound, its party walls blind. */
	@Test
	void rowsAreSoundAndTheirPartyWallsBlind() {
		List<String> problems = new ArrayList<>();
		for (ArchetypeInfo info : StationSupport.registered()) {
			if (!info.flags().contains(ArchetypeInfo.SiteFlag.ATTACHABLE)) {
				continue;
			}
			for (String terrain : new String[]{"flat", "slope", "steep"}) {
				SyntheticTerrain probe = SyntheticTerrain.named(terrain, 42);
				for (Culture c : Culture.values()) {
					for (PlotSpec spec : StationSupport.row(info, StyleRegistry.styleId(c), 2000L + c.ordinal() * 97L, 4, probe, new CompoundTag())) {
						BlueprintImpl bp = StationSupport.SERVICE.compiled(spec, probe);
						for (String p : BlueprintChecks.all(bp, probe)) {
							problems.add(info.id().getPath() + " " + terrain + " " + c.getSerializedName() + " " + spec.params() + ": " + p);
						}
						String party = partyOpening(bp, spec);
						if (party != null) {
							problems.add(info.id().getPath() + " " + terrain + " " + c.getSerializedName() + ": an opening in a party wall: " + party);
						}
					}
				}
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " problems:\n  " + String.join("\n  ", problems));
	}

	/** Glass, a door or bars in a party wall's column, or null. */
	private static String partyOpening(BlueprintImpl bp, PlotSpec spec) {
		LocalFrame f = new LocalFrame(spec);
		boolean leftParty = spec.params().getBooleanOr("party_right", false);
		boolean rightParty = spec.params().getBooleanOr("party_left", false);
		for (int s = 0; s < bp.stageCount(); s++) {
			for (int i = 0; i < bp.stepCount(s); i++) {
				BlockPos p = bp.pos(s, i);
				int lx = f.localX(p.getX(), p.getZ());
				if (!(lx == 0 && leftParty || lx == f.frontage() - 1 && rightParty)) {
					continue;
				}
				BlockState st = bp.state(s, i);
				int ly = p.getY() - spec.floorY();
				if (ly >= 1 && (st.getBlock() instanceof IronBarsBlock || st.getBlock() instanceof TransparentBlock || st.getBlock() instanceof DoorBlock)) {
					return st + " at local " + lx + "," + ly + "," + f.localZ(p.getX(), p.getZ());
				}
			}
		}
		return null;
	}

	/** What each household needs from its home, in every culture. */
	@Test
	void everyHomeGivesItsHouseholdWhatItNeeds() {
		SyntheticTerrain probe = SyntheticTerrain.named("flat", 1);
		List<String> problems = new ArrayList<>();
		for (ArchetypeInfo info : StationSupport.registered()) {
			String a = info.id().getPath();
			for (Culture c : Culture.values()) {
				for (long seed : new long[]{11L, 12L, 13L}) {
					java.util.Random r = new java.util.Random(seed * 7 + c.ordinal());
					int frontage = info.minFrontage() + r.nextInt(info.maxFrontage() - info.minFrontage() + 1);
					int depth = info.minDepth() + r.nextInt(info.maxDepth() - info.minDepth() + 1);
					CompoundTag params = new CompoundTag();
					params.putString("station", station(a));
					PlotSpec spec = StationSupport.plot(info, StyleRegistry.styleId(c), seed, Direction.SOUTH, probe, params, frontage, depth, info.minHeight(), 2);
					List<Poi> pois = StationSupport.SERVICE.compiled(spec, probe).pois();
					String where = a + " " + c.getSerializedName() + " seed " + seed;
					switch (a) {
						case "house/hovel", "house/hermit_hut", "house/croft" -> require(problems, where, pois, PoiType.HEARTH, null);
						case "house/merchant_house" -> {
							require(problems, where, pois, PoiType.JOB_SITE, JobType.MERCHANT);
							require(problems, where, pois, PoiType.STRONGBOX, null);
							require(problems, where, pois, PoiType.COUNTER, null);
						}
						case "house/reeve_house" -> {
							require(problems, where, pois, PoiType.JOB_SITE, JobType.REEVE);
							require(problems, where, pois, PoiType.NOTICE_BOARD, null);
							require(problems, where, pois, PoiType.STRONGBOX, null);
						}
						case "house/rectory", "house/scribe_house" -> require(problems, where, pois, PoiType.LECTERN, null);
						default -> {
						}
					}
					if (a.equals("house/hermit_hut") && pois.stream().noneMatch(p -> p.type() == PoiType.ALTAR)) {
						problems.add(where + ": no shrine (ALTAR)");
					}
				}
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " problems:\n  " + String.join("\n  ", problems));
	}

	private static void require(List<String> problems, String where, List<Poi> pois, PoiType type, JobType job) {
		if (pois.stream().noneMatch(p -> p.type() == type && (job == null || p.job() == job))) {
			problems.add(where + ": no " + type + (job == null ? "" : " for " + job));
		}
	}

	/** Every stage's bill stays a builder's load (a few thousand units of goods at most). */
	@Test
	void billsStayWithinAStagesLoad() {
		SyntheticTerrain probe = SyntheticTerrain.named("slope", 3);
		List<String> problems = new ArrayList<>();
		for (ArchetypeInfo info : StationSupport.registered()) {
			for (Culture c : new Culture[]{Culture.TEMPERATE, Culture.DESERT, Culture.NORTHERN, Culture.KARST, Culture.MEDITERRANEAN}) {
				PlotSpec spec = StationSupport.plot(info, StyleRegistry.styleId(c), 77L, Direction.SOUTH, probe, new CompoundTag(), info.maxFrontage(),
					info.maxDepth(), info.maxHeight(), 3);
				BlueprintImpl bp = StationSupport.SERVICE.compiled(spec, probe);
				for (int s = 0; s < bp.stageCount(); s++) {
					int total = 0;
					for (int g : bp.goods(s)) {
						total += g;
					}
					if (total > 3000) {
						problems.add(info.id().getPath() + " " + c.getSerializedName() + " stage " + bp.stageKind(s) + ": " + total + " units");
					}
				}
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " problems:\n  " + String.join("\n  ", problems));
	}

	/** Unused flag import guard (SiteFlag lives on ArchetypeInfo). */
	static boolean attachable(ArchetypeInfo info) {
		return info.flags().contains(ArchetypeInfo.SiteFlag.ATTACHABLE);
	}
}
