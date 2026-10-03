package dev.voidmc.hearth.build.townhome;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * The town homes beyond the shared invariants: rows of attached houses with party walls (each house sound, its party
 * walls blind), corner houses, stacks of cliff dwellings standing on each other's roofs, doors where walkways meet a
 * tall house, and the places each kind of house must give folk (the shop's counter, the manor's library and gate, the
 * tower's lookout, the court's meeting place).
 */
class TownHomeTest {
	private static final String[] ROWS = {"house/rowhouse", "house/townhouse", "house/shophouse", "house/tenement"};

	static {
		TownHomeSupport.SERVICE.archetypes();
	}

	static Stream<Arguments> rows() {
		List<Arguments> out = new ArrayList<>();
		for (String a : ROWS) {
			for (String terrain : new String[]{"flat", "slope", "steep", "hills"}) {
				out.add(Arguments.of(a, terrain));
			}
		}
		return out.stream();
	}

	/** A row of four in every culture: every house sound, its party walls blind (no glass, no doors in them). */
	@ParameterizedTest(name = "a row of {0} on {1}")
	@MethodSource("rows")
	void rowsAreSoundAndTheirPartyWallsBlind(String archetype, String terrain) {
		ArchetypeInfo info = TownHomeSupport.info(archetype);
		List<String> problems = new ArrayList<>();
		for (Culture c : Culture.values()) {
			SyntheticTerrain probe = SyntheticTerrain.named(terrain, 42);
			for (PlotSpec spec : TownHomeSupport.row(info, StyleRegistry.styleId(c), 1000L + c.ordinal() * 97L, 4, probe, new CompoundTag())) {
				BlueprintImpl bp = TownHomeSupport.SERVICE.compiled(spec, probe);
				for (String p : BlueprintChecks.all(bp, probe)) {
					problems.add(c.getSerializedName() + " " + spec.params() + ": " + p);
					if (problems.size() == 1) {
						problems.add(BlueprintChecks.reachMap(bp, probe, spec.floorY()) + BlueprintChecks.reachMap(bp, probe, spec.floorY() + 5));
					}
				}
				String party = partyOpening(bp, spec);
				if (party != null) {
					problems.add(c.getSerializedName() + " " + spec.params() + ": an opening in a party wall: " + party);
				}
			}
		}
		assertTrue(problems.isEmpty(), archetype + " rows on " + terrain + ", " + problems.size() + " problems:\n  " + String.join("\n  ", problems));
	}

	/** Glass, a door or bars in a party wall's column (local x 0 or frontage-1 on a party side), or null. */
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
					return st + " at local " + lx + "," + ly + "," + f.localZ(p.getX(), p.getZ()) + " (" + bp.stageKind(s) + ", frontage " + f.frontage()
						+ ", depth " + f.depth() + ")";
				}
			}
		}
		return null;
	}

	/** The houses at the ends of a row on a street corner: sound in every culture, with windows on the side street. */
	@Test
	void cornerHousesAreSound() {
		List<String> problems = new ArrayList<>();
		SyntheticTerrain probe = SyntheticTerrain.named("slope", 42);
		for (String archetype : ROWS) {
			ArchetypeInfo info = TownHomeSupport.info(archetype);
			for (Culture c : Culture.values()) {
				CompoundTag params = new CompoundTag();
				params.putBoolean("party_left", true);
				params.putBoolean("corner_right", true);
				PlotSpec spec = TownHomeSupport.plot(info, StyleRegistry.styleId(c), 777L + c.ordinal(), Direction.EAST, probe, params,
					info.maxFrontage(), info.maxDepth(), info.maxHeight());
				for (String p : BlueprintChecks.all(TownHomeSupport.SERVICE.compiled(spec, probe), probe)) {
					problems.add(archetype + " " + c.getSerializedName() + ": " + p);
				}
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " problems:\n  " + String.join("\n  ", problems));
	}

	/**
	 * Cliff dwellings stacked three high as the planner raises them (each on the roof of the one below, set back half
	 * its depth, its floor at the lower one's floor plus its height): every level sound against the roofs below it,
	 * every level's entrance reachable from its street (the roof below), each terrace resting on the deck below.
	 */
	@Test
	void stackedDwellingsStandOnTheRoofsBelow() {
		ArchetypeInfo info = TownHomeSupport.info("house/cliff_dwelling");
		List<String> problems = new ArrayList<>();
		for (String terrain : new String[]{"steep", "cliff", "slope"}) {
			for (Culture c : new Culture[]{Culture.MESA, Culture.KARST, Culture.ALPINE, Culture.DESERT, Culture.TEMPERATE}) {
				for (int depth : new int[]{6, 8, 9}) {
					SyntheticTerrain base = SyntheticTerrain.named(terrain, 42);
					TownHomeSupport.BuiltProbe probe = new TownHomeSupport.BuiltProbe(base);
					int frontage = 8;
					Direction facing = Direction.WEST;
					int x0 = 0;
					int z0 = 0;
					int floor = base.groundY(x0 - 1, z0 + frontage / 2);
					int height = info.maxHeight();
					for (int level = 0; level < 3; level++) {
						CompoundTag p = new CompoundTag();
						if (level > 0) {
							p.putBoolean("stacked", true);
							p.putInt("level", level);
							p.putInt("stacked_on", level - 1);
						}
						PlotSpec spec = new PlotSpec(info.id(), StyleRegistry.styleId(c), 5000L + level + depth * 13L + c.ordinal(), new BlockPos(x0, floor, z0),
							depth, frontage, facing, height, 2, p);
						BlueprintImpl bp = TownHomeSupport.SERVICE.compiled(spec, probe);
						for (String prob : BlueprintChecks.all(bp, probe)) {
							problems.add(terrain + " " + c.getSerializedName() + " depth " + depth + " level " + level + ": " + prob);
						}
						probe.add(bp);
						int set = Math.max(2, depth / 2);
						x0 += set;
						floor += height;
						height = Math.max(info.minHeight(), Math.min(info.maxHeight(), info.minHeight() + 1));
					}
				}
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " problems:\n  " + String.join("\n  ", problems));
	}

	/** Where a walkway meets a tall house (param walkway_y, eight above the floor), the house has a door at that level. */
	@Test
	void walkwaysMeetADoor() {
		List<String> problems = new ArrayList<>();
		SyntheticTerrain probe = SyntheticTerrain.named("flat", 42);
		for (String archetype : new String[]{"house/townhouse", "house/tenement"}) {
			ArchetypeInfo info = TownHomeSupport.info(archetype);
			for (Culture c : Culture.values()) {
				CompoundTag params = new CompoundTag();
				PlotSpec probeSpec = TownHomeSupport.plot(info, StyleRegistry.styleId(c), 31L + c.ordinal(), Direction.SOUTH, probe, params, info.maxFrontage(),
					info.maxDepth(), info.maxHeight());
				params.putInt("walkway_y", probeSpec.floorY() + 8);
				params.putInt("storeys", 4);
				PlotSpec spec = TownHomeSupport.plot(info, StyleRegistry.styleId(c), 31L + c.ordinal(), Direction.SOUTH, probe, params, info.maxFrontage(),
					info.maxDepth(), info.maxHeight());
				BlueprintImpl bp = TownHomeSupport.SERVICE.compiled(spec, probe);
				boolean door = bp.entrances().stream().anyMatch(e -> e.getY() == spec.floorY() + 8);
				if (!door) {
					problems.add(archetype + " " + c.getSerializedName() + ": no entrance at the walkway's level");
				}
				for (String p : BlueprintChecks.all(bp, probe)) {
					problems.add(archetype + " " + c.getSerializedName() + ": " + p);
				}
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " problems:\n  " + String.join("\n  ", problems));
	}

	/** What folk need from each kind of town home, in every culture. */
	@Test
	void everyHouseGivesFolkWhatTheyNeed() {
		SyntheticTerrain probe = SyntheticTerrain.named("flat", 1);
		List<String> problems = new ArrayList<>();
		for (Culture c : Culture.values()) {
			for (long seed : new long[]{11L, 12L}) {
				List<Poi> shop = pois("house/shophouse", c, seed, probe);
				require(problems, c, "shophouse", shop, PoiType.COUNTER, null);
				require(problems, c, "shophouse", shop, PoiType.MARKET_STALL, null);
				require(problems, c, "shophouse", shop, PoiType.JOB_SITE, JobType.MERCHANT);
				List<Poi> manor = pois("house/manor", c, seed, probe);
				require(problems, c, "manor", manor, PoiType.LECTERN, null);
				require(problems, c, "manor", manor, PoiType.GATE, null);
				require(problems, c, "manor", manor, PoiType.HEARTH, null);
				long beds = manor.stream().filter(p -> p.type() == PoiType.BED).count();
				if (beds < 6) {
					problems.add(c.getSerializedName() + " manor has " + beds + " beds");
				}
				require(problems, c, "courtyard house", pois("house/courtyard_house", c, seed, probe), PoiType.MEETING_POINT, null);
				List<Poi> tower = pois("house/tower_house", c, seed, probe);
				if (tower.stream().noneMatch(p -> p.type() == PoiType.VIEWPOINT || p.type() == PoiType.GUARD_POST)) {
					problems.add(c.getSerializedName() + " tower house has no lookout");
				}
				long tenementBeds = pois("house/tenement", c, seed, probe).stream().filter(p -> p.type() == PoiType.BED).count();
				if (tenementBeds < 6) {
					problems.add(c.getSerializedName() + " tenement has " + tenementBeds + " beds");
				}
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " problems:\n  " + String.join("\n  ", problems));
	}

	private static List<Poi> pois(String archetype, Culture c, long seed, SyntheticTerrain probe) {
		ArchetypeInfo info = TownHomeSupport.info(archetype);
		PlotSpec spec = TownHomeSupport.plot(info, StyleRegistry.styleId(c), seed, Direction.SOUTH, probe, new CompoundTag(), info.maxFrontage(),
			info.maxDepth(), info.maxHeight());
		return TownHomeSupport.SERVICE.compiled(spec, probe).pois();
	}

	private static void require(List<String> problems, Culture c, String what, List<Poi> pois, PoiType type, JobType job) {
		if (pois.stream().noneMatch(p -> p.type() == type && (job == null || p.job() == job))) {
			problems.add(c.getSerializedName() + " " + what + " has no " + type + (job == null ? "" : " for " + job));
		}
	}

	/** The catalogue entries the planner reads: ids, sizes, flags and upgrades as the architecture lists them. */
	@Test
	void catalogueEntries() {
		assertEquals(List.of(dev.voidmc.hearth.Hearth.id("house/townhouse"), dev.voidmc.hearth.Hearth.id("house/tenement")),
			TownHomeSupport.info("house/rowhouse").upgradesTo());
		assertEquals(List.of(dev.voidmc.hearth.Hearth.id("house/townhouse"), dev.voidmc.hearth.Hearth.id("house/tenement")),
			TownHomeSupport.info("house/shophouse").upgradesTo());
		assertEquals(List.of(dev.voidmc.hearth.Hearth.id("house/tenement")), TownHomeSupport.info("house/townhouse").upgradesTo());
		assertTrue(TownHomeSupport.info("house/shophouse").jobs().contains(JobType.MERCHANT));
		assertTrue(TownHomeSupport.info("house/cliff_dwelling").flags().contains(ArchetypeInfo.SiteFlag.STACKABLE));
		assertTrue(TownHomeSupport.info("house/cliff_dwelling").flags().contains(ArchetypeInfo.SiteFlag.HILLSIDE));
		assertTrue(TownHomeSupport.info("house/tenement").flags().contains(ArchetypeInfo.SiteFlag.VERTICAL));
		assertEquals(4, TownHomeSupport.info("house/manor").maxPerSettlement());
	}
}
