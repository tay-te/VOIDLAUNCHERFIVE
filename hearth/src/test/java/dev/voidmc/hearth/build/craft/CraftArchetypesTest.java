package dev.voidmc.hearth.build.craft;

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
 * The craft workshops beyond the shared invariants (and the shared invariants themselves, run here per archetype so a
 * craft change can be checked in seconds): every culture, terrain, seed, facing and style variant keeps the blueprint
 * checks (bounds, nothing floating, reachability, stage order, the light rule); low plots (the planner's outskirts
 * heights) too; nothing of note falls outside the plot; every workshop has its job sites and a store; the shops serve
 * over a counter; the counting house keeps a strongbox; no stage asks for more than a cart train brings.
 */
class CraftArchetypesTest {
	private static final BlueprintServiceImpl SERVICE;
	static final String[] IDS = {"work/tannery", "work/butcher", "work/chandler", "work/dye_works", "work/paper_mill", "work/apiary",
		"commerce/counting_house"};
	private static final String[] TERRAINS = {"flat", "slope", "hills", "river", "coast", "steep"};
	private static final Direction[] FACINGS = {Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST};

	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		SERVICE = BuildModule.standalone();
	}

	private static List<ArchetypeInfo> crafts() {
		List<ArchetypeInfo> out = new ArrayList<>();
		for (String id : IDS) {
			ArchetypeInfo info = SERVICE.archetype(Hearth.id(id));
			if (info != null) {
				out.add(info);
			}
		}
		return out;
	}

	static Stream<Arguments> cases() {
		List<Arguments> out = new ArrayList<>();
		for (ArchetypeInfo a : crafts()) {
			for (String t : TERRAINS) {
				out.add(Arguments.of(a.id().getPath(), t));
			}
		}
		return out.stream();
	}

	/** A spec the way the planner makes one: a size from the range by seed, the floor at the street in front. */
	static PlotSpec spec(ArchetypeInfo info, Culture c, long seed, Direction facing, TerrainProbe probe, int height, CompoundTag params) {
		Random r = new Random(seed * 31 + info.id().hashCode());
		int frontage = info.minFrontage() + r.nextInt(info.maxFrontage() - info.minFrontage() + 1);
		int depth = info.minDepth() + r.nextInt(info.maxDepth() - info.minDepth() + 1);
		return spec(info, c, seed, facing, probe, height, params, frontage, depth);
	}

	static PlotSpec spec(ArchetypeInfo info, Culture c, long seed, Direction facing, TerrainProbe probe, int height, CompoundTag params, int frontage,
		int depth) {
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

	@ParameterizedTest(name = "{0} on {1}")
	@MethodSource("cases")
	void invariantsHoldInEveryCultureAndFacing(String id, String terrain) {
		ArchetypeInfo info = SERVICE.archetype(Hearth.id(id));
		List<String> problems = new ArrayList<>();
		SyntheticTerrain probe = SyntheticTerrain.named(terrain, 42);
		for (Culture c : Culture.values()) {
			for (int s = 0; s < 4; s++) {
				long seed = 7919L * (s + 1) + c.ordinal() * 104729L + terrain.hashCode();
				Direction facing = FACINGS[(s + c.ordinal()) % 4];
				int height = s % 2 == 0 ? info.maxHeight() : info.minHeight() + (info.maxHeight() - info.minHeight()) / 2;
				PlotSpec spec = spec(info, c, seed, facing, probe, height, new CompoundTag());
				Draft d = SERVICE.draw(spec, probe);
				BlueprintImpl bp = dev.voidmc.hearth.build.blueprint.Compiler.compile(d);
				for (String p : BlueprintChecks.all(bp, probe)) {
					problems.add(c.getSerializedName() + " seed " + seed + " " + facing.getSerializedName() + " h" + height + " " + spec.frontage() + "x"
						+ spec.depth() + ": " + p);
				}
				if (d.clipped() > 16) {
					problems.add(c.getSerializedName() + " seed " + seed + ": " + d.clipped() + " blocks fell outside the plot");
				}
			}
		}
		assertTrue(problems.isEmpty(), id + " on " + terrain + ", " + problems.size() + " problems:\n  " + String.join("\n  ",
			problems.subList(0, Math.min(30, problems.size()))));
	}

	static Stream<Arguments> ids() {
		return crafts().stream().map(a -> Arguments.of(a.id().getPath()));
	}

	@ParameterizedTest(name = "{0} in every variant")
	@MethodSource("ids")
	void everyVariantHolds(String id) {
		ArchetypeInfo info = SERVICE.archetype(Hearth.id(id));
		List<String> problems = new ArrayList<>();
		for (String terrain : new String[]{"slope", "flat"}) {
			SyntheticTerrain probe = SyntheticTerrain.named(terrain, 42);
			for (Culture c : Culture.values()) {
				var style = SERVICE.styles().get(StyleRegistry.styleId(c));
				for (int v = 0; v < style.variants().size(); v++) {
					CompoundTag params = new CompoundTag();
					params.putInt("variant", v);
					long seed = 31337L + v * 7L + c.ordinal() * 1009L + terrain.length();
					Direction facing = FACINGS[(v + c.ordinal()) % 4];
					PlotSpec spec = spec(info, c, seed, facing, probe, info.maxHeight(), params);
					for (String p : BlueprintChecks.all(SERVICE.compiled(spec, probe), probe)) {
						problems.add(terrain + " " + c.getSerializedName() + " (" + style.variants().get(v).name() + ") seed " + seed + ": " + p);
					}
				}
			}
		}
		assertTrue(problems.isEmpty(), id + ", " + problems.size() + " problems:\n  " + String.join("\n  ", problems.subList(0, Math.min(30,
			problems.size()))));
	}

	@Test
	void everySizeTheplannerMayAskHolds() {
		List<String> problems = new ArrayList<>();
		SyntheticTerrain probe = SyntheticTerrain.named("hills", 11);
		for (ArchetypeInfo info : crafts()) {
			int k = 0;
			for (int f = info.minFrontage(); f <= info.maxFrontage(); f++) {
				for (int dp = info.minDepth(); dp <= info.maxDepth(); dp++) {
					for (int h : new int[]{info.minHeight(), info.maxHeight()}) {
						Culture c = Culture.values()[k % Culture.values().length];
						long seed = 501L + k * 13L;
						k++;
						PlotSpec spec = spec(info, c, seed, FACINGS[k % 4], probe, h, new CompoundTag(), f, dp);
						for (String p : BlueprintChecks.all(SERVICE.compiled(spec, probe), probe)) {
							problems.add(info.id().getPath() + " " + f + "x" + dp + " h" + h + " " + c.getSerializedName() + ": " + p);
						}
					}
				}
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " problems:\n  " + String.join("\n  ", problems.subList(0, Math.min(30, problems.size()))));
	}

	@Test
	void workshopsHaveTheirJobSitesAndStores() {
		List<String> problems = new ArrayList<>();
		SyntheticTerrain probe = SyntheticTerrain.named("flat", 3);
		for (ArchetypeInfo info : crafts()) {
			for (Culture c : Culture.values()) {
				for (int s = 0; s < 3; s++) {
					int height = s == 2 ? info.minHeight() : info.maxHeight();
					PlotSpec spec = spec(info, c, 900L + s * 37 + c.ordinal(), FACINGS[s], probe, height, new CompoundTag());
					BlueprintImpl bp = SERVICE.compiled(spec, probe);
					long sites = bp.pois().stream().filter(p -> p.type() == PoiType.JOB_SITE).count();
					long stores = bp.pois().stream().filter(p -> p.type() == PoiType.STORAGE).count();
					String where = info.id().getPath() + " " + c.getSerializedName() + " s" + s;
					if (info.category() == ArchetypeCategory.WORKPLACE && sites < 2) {
						problems.add(where + ": " + sites + " job sites (a workshop employs two at least)");
					}
					for (JobType job : info.jobs()) {
						if (bp.pois().stream().noneMatch(p -> p.type() == PoiType.JOB_SITE && p.job() == job)) {
							problems.add(where + ": no job site for " + job);
						}
					}
					if (stores < 1) {
						problems.add(where + ": no store");
					}
					if (bp.pois().stream().noneMatch(p -> p.type() == PoiType.DOOR)) {
						problems.add(where + ": no door");
					}
				}
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " problems:\n  " + String.join("\n  ", problems));
	}

	@Test
	void theCountingHouseKeepsAStrongboxAndServesOverACounter() {
		ArchetypeInfo info = SERVICE.archetype(Hearth.id("commerce/counting_house"));
		if (info == null) {
			return;
		}
		List<String> problems = new ArrayList<>();
		SyntheticTerrain probe = SyntheticTerrain.named("flat", 5);
		for (Culture c : Culture.values()) {
			for (int s = 0; s < 3; s++) {
				PlotSpec spec = spec(info, c, 1300L + s * 41 + c.ordinal(), FACINGS[(s + c.ordinal()) % 4], probe, s == 2 ? info.minHeight()
					: info.maxHeight(), new CompoundTag());
				BlueprintImpl bp = SERVICE.compiled(spec, probe);
				String where = c.getSerializedName() + " s" + s + " " + spec.frontage() + "x" + spec.depth();
				if (bp.pois().stream().noneMatch(p -> p.type() == PoiType.STRONGBOX)) {
					problems.add(where + ": no strongbox");
				}
				if (spec.frontage() >= 8 && bp.pois().stream().noneMatch(p -> p.type() == PoiType.COUNTER)) {
					problems.add(where + ": no counter");
				}
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " problems:\n  " + String.join("\n  ", problems));
	}

	@Test
	void noStageAsksForMoreThanACartTrainBrings() {
		List<String> problems = new ArrayList<>();
		SyntheticTerrain probe = SyntheticTerrain.named("hills", 9);
		for (ArchetypeInfo info : crafts()) {
			for (Culture c : Culture.values()) {
				PlotSpec spec = spec(info, c, 777L + c.ordinal(), Direction.SOUTH, probe, info.maxHeight(), new CompoundTag(), info.maxFrontage(),
					info.maxDepth());
				BlueprintImpl bp = SERVICE.compiled(spec, probe);
				for (int s = 0; s < bp.stageCount(); s++) {
					int sum = 0;
					for (int g : bp.goods(s)) {
						sum += g;
					}
					if (sum > 3000) {
						problems.add(info.id().getPath() + " " + c.getSerializedName() + " " + bp.stageKind(s) + ": " + sum + " goods");
					}
				}
			}
		}
		assertTrue(problems.isEmpty(), String.join("\n", problems));
	}
}
