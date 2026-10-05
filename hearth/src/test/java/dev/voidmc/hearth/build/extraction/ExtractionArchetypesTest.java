package dev.voidmc.hearth.build.extraction;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.BuildModule;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.blueprint.Compiler;
import dev.voidmc.hearth.build.check.BlueprintChecks;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.kit.light.LightRules;
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
 * The extraction family (docs/buildings/extraction.md) beyond the shared invariants: every excavation keeps the
 * invariants — work faces reachable, nothing floating, the light rule of its family — in every culture on every
 * synthetic terrain, at the heights the planner gives out of town and with the params it passes (deep pits, long adits,
 * deep shafts); nothing it draws falls outside its plot; it is worked in many campaigns, each after the opening a bench, a
 * gallery or a lift with cuts in the natural ground and a work face; no stage asks for more goods than a few cart loads;
 * and the woodlot fells and plants.
 */
class ExtractionArchetypesTest {
	static final BlueprintServiceImpl SERVICE;
	static final String[] IDS = {"extract/quarry_pit", "extract/mine", "extract/shaft", "extract/clay_pit", "extract/sand_pit", "extract/plantation"};
	private static final String[] TERRAINS = {"flat", "slope", "steep", "hills", "cliff", "river", "coast", "lake"};
	private static final Direction[] FACINGS = {Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST};

	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		SERVICE = BuildModule.standalone();
	}

	static List<ArchetypeInfo> excavations() {
		List<ArchetypeInfo> out = new ArrayList<>();
		for (String id : IDS) {
			ArchetypeInfo info = SERVICE.archetype(Hearth.id(id));
			assertTrue(info != null, id + " is registered");
			out.add(info);
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

	/** The params the planner passes an excavation (LIVING_WORLD.md §8.5), deep or long by seed. */
	static CompoundTag planned(ArchetypeInfo info, long seed) {
		CompoundTag t = new CompoundTag();
		Random r = new Random(seed);
		t.putInt("site", 3);
		String p = info.id().getPath();
		switch (p) {
			case "extract/quarry_pit" -> {
				t.putString("kind", "stone");
				t.putString("rock", r.nextBoolean() ? "minecraft:andesite" : "minecraft:cobblestone");
				t.putInt("depth", 10 + r.nextInt(15));
			}
			case "extract/mine" -> {
				t.putString("kind", "ore");
				t.putInt("length", 12 + r.nextInt(30));
			}
			case "extract/shaft" -> {
				t.putString("kind", "ore");
				t.putInt("depth", 16 + r.nextInt(50));
			}
			case "extract/clay_pit" -> {
				t.putString("kind", "clay");
				t.putInt("depth", 3 + r.nextInt(2));
			}
			case "extract/sand_pit" -> {
				t.putString("kind", "sand");
				t.putInt("depth", 3 + r.nextInt(2));
			}
			default -> t.putString("kind", "woods");
		}
		t.putString("face", "north");
		return t;
	}

	static Stream<Arguments> cases() {
		List<Arguments> out = new ArrayList<>();
		for (ArchetypeInfo a : excavations()) {
			for (String terrain : TERRAINS) {
				out.add(Arguments.of(a, terrain));
			}
		}
		return out.stream();
	}

	/** Every culture, two seeds, the planner's middle height and the top of the range, plain and with planned params. */
	@ParameterizedTest(name = "{0} on {1}")
	@MethodSource("cases")
	void invariantsHoldOnEveryTerrain(ArchetypeInfo info, String terrain) {
		List<String> problems = new ArrayList<>();
		SyntheticTerrain probe = SyntheticTerrain.named(terrain, 42);
		int mid = (info.minHeight() + info.maxHeight() + 1) / 2;
		for (Culture c : Culture.values()) {
			for (int s = 0; s < 4; s++) {
				long seed = 6151L * (s + 1) + c.ordinal() * 7919L + terrain.hashCode();
				Direction facing = FACINGS[(s + c.ordinal()) % 4];
				CompoundTag params = s % 2 == 0 ? new CompoundTag() : planned(info, seed);
				int height = s < 2 ? mid : info.maxHeight();
				PlotSpec spec = spec(info, c, seed, facing, probe, height, params);
				Draft d = SERVICE.draw(spec, probe);
				BlueprintImpl bp = Compiler.compile(d);
				String where = c.getSerializedName() + " seed " + seed + " " + facing.getSerializedName() + " h" + height + (params.isEmpty() ? "" : " planned");
				for (String p : BlueprintChecks.all(bp, probe)) {
					problems.add(where + ": " + p);
				}
				if (d.clipped() > 0) {
					problems.add(where + ": " + d.clipped() + " placements fell outside the plot " + d.frontage() + "x" + d.depth() + ", e.g. " + d.clippedSample());
				}
			}
		}
		assertTrue(problems.isEmpty(), info.id() + " on " + terrain + ", " + problems.size() + " problems:\n  "
			+ String.join("\n  ", problems.subList(0, Math.min(30, problems.size()))));
	}

	/**
	 * Worked in campaigns: at least three after the opening; each later campaign cuts natural ground (the woodlot: fells) and
	 * has a work face; no stage asks for more than 3,000 goods; the light family is the extraction rule (the woodlot a
	 * farm's).
	 */
	@Test
	void workedInCampaignsWithFacesAndModestBills() {
		List<String> problems = new ArrayList<>();
		for (ArchetypeInfo info : excavations()) {
			boolean woodlot = info.id().getPath().equals("extract/plantation");
			for (String terrain : new String[]{"flat", "hills", "steep"}) {
				SyntheticTerrain probe = SyntheticTerrain.named(terrain, 7);
				for (Culture c : Culture.values()) {
					long seed = 811L + c.ordinal() * 31L + terrain.length();
					PlotSpec spec = spec(info, c, seed, FACINGS[c.ordinal() % 4], probe, info.maxHeight(), planned(info, seed));
					BlueprintImpl bp = SERVICE.compiled(spec, probe);
					String where = info.id().getPath() + " " + terrain + " " + c.getSerializedName();
					// a pit on land falling steeply away from its street finds rock for a bench or two only
					if (bp.campaignCount() < (terrain.equals("steep") ? 2 : 3)) {
						problems.add(where + ": only " + bp.campaignCount() + " campaigns");
					}
					int[] cuts = new int[bp.campaignCount()];
					for (int s = 0; s < bp.stageCount(); s++) {
						int part = bp.campaign(s);
						for (int i = 0; i < bp.stepCount(s); i++) {
							BlockPos p = bp.pos(s, i);
							if (bp.state(s, i).isAir() && (woodlot || p.getY() < probe.groundY(p.getX(), p.getZ()))) {
								cuts[part]++;
							}
						}
						int total = 0;
						for (int g : bp.goods(s)) {
							total += g;
						}
						if (total > 3000) {
							problems.add(where + ": stage " + s + " (" + bp.stageKind(s) + ", part " + part + ") asks for " + total + " goods");
						}
					}
					for (int p = 1; p < cuts.length; p++) {
						if (cuts[p] == 0) {
							problems.add(where + ": part " + p + " cuts nothing");
						}
					}
					long faces = bp.pois().stream().filter(p -> p.type() == PoiType.WORK_FACE).count();
					if (faces < Math.min(3, bp.campaignCount() - 1)) {
						problems.add(where + ": " + faces + " work faces for " + (bp.campaignCount() - 1) + " campaigns");
					}
					LightRules.Family f = LightRules.family(info.id());
					if (f != (woodlot ? LightRules.Family.FARM : LightRules.Family.EXTRACTION)) {
						problems.add(where + ": light family " + f);
					}
					if (info.category() != ArchetypeCategory.EXTRACTION) {
						problems.add(where + ": category " + info.category());
					}
					if (bp.goods(0).length != Goods.VALUES.length) {
						problems.add(where + ": goods array of the wrong length");
					}
				}
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " problems:\n  " + String.join("\n  ", problems.subList(0, Math.min(40, problems.size()))));
	}

	@Test
	void sameSpecSameBlueprint() {
		SyntheticTerrain probe = SyntheticTerrain.named("hills", 7);
		for (ArchetypeInfo info : excavations()) {
			for (Culture c : new Culture[]{Culture.TEMPERATE, Culture.DESERT, Culture.KARST, Culture.NORTHERN}) {
				PlotSpec spec = spec(info, c, 12345L, Direction.EAST, probe, info.maxHeight(), planned(info, 5));
				BlueprintImpl a = Compiler.compile(SERVICE.draw(spec, probe));
				BlueprintImpl b = Compiler.compile(SERVICE.draw(spec, probe));
				assertTrue(BlueprintChecks.same(a, b), info.id() + " in " + c + " is not deterministic");
			}
		}
	}
}
