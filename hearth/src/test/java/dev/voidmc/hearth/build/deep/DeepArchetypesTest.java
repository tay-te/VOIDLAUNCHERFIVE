package dev.voidmc.hearth.build.deep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.BuildModule;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.check.BlueprintChecks;
import dev.voidmc.hearth.build.kit.deep.DeepSite;
import dev.voidmc.hearth.build.kit.deep.DeepTerrain;
import dev.voidmc.hearth.build.style.StyleRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

/**
 * The deep town (LIVING_WORLD.md §8.2, docs/buildings/deep.md) beyond the shared invariants: every deep archetype keeps the
 * invariants in its realm — on open floor, rubble, at the foot of the realm's wall and on a ledge, with the params the deep
 * planner gives ({@code realm}, {@code character}, {@code ceiling_y}, the realm's {@code local}) — and also where it is
 * built by hand on the surface (the shared test's terrains, seeds and variants, run here for the deep ids alone so they
 * can be iterated on quickly); nothing is built into the vault; the lift reaches from the street to the realm floor at
 * every depth the planner may ask; the hall, market, forge and garden have their POIs; no stage asks for more than a cart
 * train brings.
 */
class DeepArchetypesTest {
	private static final BlueprintServiceImpl SERVICE;
	static final String[] IDS = {"deep/hall", "deep/dwelling", "deep/market", "deep/forge", "deep/glow_garden", "deep/lift"};
	private static final Direction[] FACINGS = {Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST};
	private static final String[] SURFACE = {"flat", "slope", "hills", "river", "coast", "steep"};

	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		SERVICE = BuildModule.standalone();
	}

	static List<ArchetypeInfo> deep() {
		List<ArchetypeInfo> out = new ArrayList<>();
		for (String id : IDS) {
			ArchetypeInfo info = SERVICE.archetype(Hearth.id(id));
			assertTrue(info != null, "the deep archetype " + id + " is registered");
			out.add(info);
		}
		return out;
	}

	/** The shared test's spec: a size from the ranges by seed, the floor at the ground in front. */
	static PlotSpec surfaceSpec(ArchetypeInfo info, Culture c, long seed, Direction facing, TerrainProbe probe, CompoundTag params) {
		Random r = new Random(seed * 31 + info.id().hashCode());
		int frontage = info.minFrontage() + r.nextInt(info.maxFrontage() - info.minFrontage() + 1);
		int depth = info.minDepth() + r.nextInt(info.maxDepth() - info.minDepth() + 1);
		boolean zAxis = facing.getAxis() == Direction.Axis.Z;
		int sizeX = zAxis ? frontage : depth;
		int sizeZ = zAxis ? depth : frontage;
		int ox = -sizeX / 2;
		int oz = -sizeZ / 2;
		int[] f = front(facing, ox, oz, sizeX, sizeZ);
		int floor = probe.groundY(f[0], f[1]);
		if (probe.waterY(f[0], f[1]) != TerrainProbe.NO_WATER) {
			floor = Math.max(floor, probe.waterY(f[0], f[1]) + 1);
		}
		return new PlotSpec(info.id(), StyleRegistry.styleId(c), seed, new BlockPos(ox, floor, oz), sizeX, sizeZ, facing, Math.max(info.maxHeight(), 8), 2,
			params);
	}

	private static int[] front(Direction facing, int ox, int oz, int sizeX, int sizeZ) {
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
		return new int[]{fx, fz};
	}

	/**
	 * A spec as the deep planner gives one: in a realm, the floor where the street meets the front, the vault over the
	 * plot as {@code ceiling_y}, the height up to it; the lift from {@code below} blocks over the floor down to it.
	 */
	static PlotSpec realmSpec(ArchetypeInfo info, Culture c, long seed, Direction facing, DeepTerrain[] out, String kind, int below) {
		Random r = new Random(seed * 31 + info.id().hashCode());
		int frontage = info.minFrontage() + r.nextInt(info.maxFrontage() - info.minFrontage() + 1);
		int depth = info.minDepth() + r.nextInt(info.maxDepth() - info.minDepth() + 1);
		boolean zAxis = facing.getAxis() == Direction.Axis.Z;
		int sizeX = zAxis ? frontage : depth;
		int sizeZ = zAxis ? depth : frontage;
		int ox = -sizeX / 2;
		int oz = -sizeZ / 2;
		DeepTerrain dt = DeepTerrain.named(kind, 42, facing.getOpposite(), depth / 2 - 2);
		out[0] = dt;
		int[] f = front(facing, ox, oz, sizeX, sizeZ);
		int floor = dt.groundY(f[0], f[1]);
		String character = DeepSite.CHARACTERS[(int) Math.floorMod(seed + c.ordinal(), DeepSite.CHARACTERS.length)];
		CompoundTag params = new CompoundTag();
		params.putLong("realm", 7L);
		params.putString("character", character);
		params.put("local", dev.voidmc.hearth.plan.resource.Materials.realm(character));
		int ceiling = dt.ceilingOver(ox, oz, ox + sizeX - 1, oz + sizeZ - 1);
		int height;
		if (info.id().getPath().equals("deep/lift")) {
			params.putInt("bottom_y", floor);
			params.putInt("top_y", floor + below);
			params.putInt("street_y", floor + below);
			params.putInt("deep_street_y", floor);
			params.putString("deep_facing", (seed % 2 == 0 ? facing : facing.getClockWise()).getSerializedName());
			height = below + 10;
		} else {
			params.putInt("ceiling_y", ceiling);
			height = Math.min(Math.max(info.maxHeight(), 8), Math.max(info.minHeight(), ceiling - floor - 1));
		}
		return new PlotSpec(info.id(), StyleRegistry.styleId(c), seed, new BlockPos(ox, floor, oz), sizeX, sizeZ, facing, height, 2, params);
	}

	private static void check(List<String> problems, String label, PlotSpec spec, TerrainProbe probe, Consumer<BlueprintImpl> more) {
		BlueprintImpl bp = SERVICE.compiled(spec, probe);
		for (String p : BlueprintChecks.all(bp, probe)) {
			problems.add(label + ": " + p);
		}
		more.accept(bp);
	}

	private static void report(List<String> problems, int n) {
		assertTrue(problems.isEmpty(), problems.size() + " problems in " + n + " blueprints:\n  " + String.join("\n  ", problems));
	}

	/**
	 * In the realm: every culture, every ground (the lift on the open floor it is planned on), two seeds, turning facings.
	 * Nothing reaches into the vault.
	 */
	@Test
	void invariantsHoldInTheRealm() {
		List<String> problems = new ArrayList<>();
		int n = 0;
		for (ArchetypeInfo info : deep()) {
			for (String kind : DeepTerrain.KINDS) {
				// the planner stands a lift only over open, level floor (DeepTown.liftFits), never against the wall
				if (info.id().getPath().equals("deep/lift") && (kind.equals("wall") || kind.equals("ledge"))) {
					continue;
				}
				for (Culture c : Culture.values()) {
					for (int s = 0; s < 2; s++) {
						long seed = 5003L * (s + 1) + c.ordinal() * 7919L + kind.length();
						Direction facing = FACINGS[(s + c.ordinal() + kind.length()) % 4];
						DeepTerrain[] probe = new DeepTerrain[1];
						PlotSpec spec = realmSpec(info, c, seed, facing, probe, kind, 40 + (int) (seed % 40));
						String label = info.id().getPath() + " " + kind + " " + c.getSerializedName() + " seed " + seed + " " + facing.getSerializedName();
						check(problems, label, spec, probe[0], bp -> {
							if (spec.params().contains("ceiling_y")) {
								int top = spec.params().getIntOr("ceiling_y", 0) - 1;
								if (bp.bounds().maxY() > top) {
									problems.add(label + ": builds up to y " + bp.bounds().maxY() + ", into the vault at " + (top + 1));
								}
							}
						});
						n++;
					}
				}
			}
		}
		report(problems, n);
	}

	/** Built by hand on the surface (no realm): the shared test's terrains and seeds. */
	@Test
	void invariantsHoldOnTheSurface() {
		List<String> problems = new ArrayList<>();
		int n = 0;
		for (ArchetypeInfo info : deep()) {
			for (String terrain : SURFACE) {
				for (Culture c : Culture.values()) {
					for (int s = 0; s < 2; s++) {
						long seed = 7919L * (s + 1) + c.ordinal() * 104729L;
						Direction facing = FACINGS[(s + c.ordinal()) % 4];
						SyntheticTerrain probe = SyntheticTerrain.named(terrain, 42);
						check(problems, info.id().getPath() + " " + terrain + " " + c.getSerializedName() + " seed " + seed + " " + facing.getSerializedName(),
							surfaceSpec(info, c, seed, facing, probe, new CompoundTag()), probe, bp -> {
							});
						n++;
					}
				}
			}
		}
		report(problems, n);
	}

	/** Every style variant of every culture, forced (on a slope, the shared test's seeds). */
	@Test
	void everyVariantHoldsTheInvariants() {
		List<String> problems = new ArrayList<>();
		SyntheticTerrain probe = SyntheticTerrain.named("slope", 42);
		int n = 0;
		for (ArchetypeInfo info : deep()) {
			for (Culture c : Culture.values()) {
				var style = SERVICE.styles().get(StyleRegistry.styleId(c));
				for (int v = 0; v < style.variants().size(); v++) {
					CompoundTag params = new CompoundTag();
					params.putInt("variant", v);
					long seed = 31337L + v * 7L + c.ordinal() * 1009L;
					Direction facing = FACINGS[(v + c.ordinal()) % 4];
					check(problems, info.id().getPath() + " " + c.getSerializedName() + " (" + style.variants().get(v).name() + ") seed " + seed + " "
						+ facing.getSerializedName(), surfaceSpec(info, c, seed, facing, probe, params), probe, bp -> {
						});
					n++;
				}
			}
		}
		report(problems, n);
	}

	/** Every deep building has its entrance and the POIs of its trade, in every culture, wherever it stands. */
	@Test
	void everyBuildingHasItsEntranceAndPois() {
		List<String> problems = new ArrayList<>();
		for (ArchetypeInfo info : deep()) {
			assertEquals(ArchetypeCategory.UNDERGROUND, info.category(), info.id() + " is underground");
			for (Culture c : Culture.values()) {
				for (int k = 0; k < 2; k++) {
					TerrainProbe probe;
					PlotSpec spec;
					if (k == 0) {
						probe = SyntheticTerrain.named("flat", 1);
						spec = surfaceSpec(info, c, 99L, Direction.SOUTH, probe, new CompoundTag());
					} else {
						DeepTerrain[] p = new DeepTerrain[1];
						spec = realmSpec(info, c, 99L + c.ordinal(), FACINGS[c.ordinal() % 4], p, "wall", 60);
						probe = p[0];
					}
					BlueprintImpl bp = SERVICE.compiled(spec, probe);
					String label = info.id().getPath() + " " + c.getSerializedName() + (k == 0 ? " by hand" : " in the realm");
					if (bp.entrances().isEmpty()) {
						problems.add(label + ": no entrance");
					}
					for (var job : info.jobs()) {
						if (bp.pois().stream().noneMatch(p -> p.job() == job)) {
							problems.add(label + ": no job site for " + job);
						}
					}
					if (info.beds() > 0 && bp.beds() < 1) {
						problems.add(label + ": no bed");
					}
					if (bp.beds() != bp.pois().stream().filter(p -> p.type() == PoiType.BED).count()) {
						problems.add(label + ": beds and BED POIs disagree");
					}
					String path = info.id().getPath();
					if ((path.equals("deep/market") || path.equals("deep/hall")) && bp.pois().stream().noneMatch(p -> p.type() == PoiType.STORAGE
						|| p.type() == PoiType.STRONGBOX)) {
						problems.add(label + ": no store");
					}
					if (path.equals("deep/market") && bp.pois().stream().noneMatch(p -> p.type() == PoiType.MARKET_STALL)) {
						problems.add(label + ": no market stall");
					}
					if (path.equals("deep/hall") && bp.pois().stream().noneMatch(p -> p.type() == PoiType.MEETING_POINT)) {
						problems.add(label + ": no meeting point");
					}
				}
			}
		}
		report(problems, 0);
	}

	/** The lift reaches from the street to the realm floor at every depth the planner may ask, in every culture. */
	@Test
	void theLiftReachesTheRealmAtEveryDepth() {
		ArchetypeInfo info = SERVICE.archetype(Hearth.id("deep/lift"));
		List<String> problems = new ArrayList<>();
		int n = 0;
		for (int below : new int[]{14, 23, 37, 64, 101, 160}) {
			for (Culture c : Culture.values()) {
				long seed = 1301L * below + c.ordinal();
				Direction facing = FACINGS[(below + c.ordinal()) % 4];
				DeepTerrain[] probe = new DeepTerrain[1];
				PlotSpec spec = realmSpec(info, c, seed, facing, probe, c.ordinal() % 2 == 0 ? "floor" : "rubble", below);
				check(problems, "lift " + below + " deep " + c.getSerializedName() + " " + facing.getSerializedName(), spec, probe[0], bp -> {
				});
				n++;
			}
		}
		report(problems, n);
	}

	@Test
	void noStageAsksForMoreThanACartTrainBrings() {
		List<String> problems = new ArrayList<>();
		for (ArchetypeInfo info : deep()) {
			for (Culture c : Culture.values()) {
				DeepTerrain[] probe = new DeepTerrain[1];
				PlotSpec spec = realmSpec(info, c, 777L + c.ordinal(), Direction.SOUTH, probe, "wall", 90);
				BlueprintImpl bp = SERVICE.compiled(spec, probe[0]);
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
		report(problems, 0);
	}

	@Test
	void sameSpecSameBlueprint() {
		for (ArchetypeInfo info : deep()) {
			for (Culture c : new Culture[]{Culture.TEMPERATE, Culture.DESERT, Culture.KARST, Culture.NORTHERN}) {
				DeepTerrain[] probe = new DeepTerrain[1];
				PlotSpec spec = realmSpec(info, c, 12345L, Direction.EAST, probe, "ledge", 50);
				BlueprintImpl a = dev.voidmc.hearth.build.blueprint.Compiler.compile(SERVICE.draw(spec, probe[0]));
				BlueprintImpl b = dev.voidmc.hearth.build.blueprint.Compiler.compile(SERVICE.draw(spec, probe[0]));
				assertTrue(BlueprintChecks.same(a, b), info.id() + " in " + c + " is not deterministic");
			}
		}
	}
}
