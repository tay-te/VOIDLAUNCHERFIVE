package dev.voidmc.hearth.build.homestead;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.BuildModule;
import dev.voidmc.hearth.build.archetype.farm.Field;
import dev.voidmc.hearth.build.archetype.farm.Paddy;
import dev.voidmc.hearth.build.archetype.farm.Pasture;
import dev.voidmc.hearth.build.archetype.farm.TerraceField;
import dev.voidmc.hearth.build.archetype.house.Cottage;
import dev.voidmc.hearth.build.archetype.house.Farmhouse;
import dev.voidmc.hearth.build.archetype.house.Longhouse;
import dev.voidmc.hearth.build.archetype.house.RoundHouse;
import dev.voidmc.hearth.build.archetype.house.StiltHouse;
import dev.voidmc.hearth.build.archetype.work.Barn;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.check.BlueprintChecks;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.style.StyleRegistry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.Test;

/**
 * The homesteads and farms beyond the shared invariants: fields that are really watered, paddies that hold their water,
 * pastures with a gate, barns with a way up to the loft, stilt houses clear of the water, longhouses round their fire,
 * split-level cottages that open their lower storey, and every forced layout or option of the family holding the
 * invariants in every culture.
 */
class HomesteadTest {
	private static final BlueprintServiceImpl SERVICE;
	private static final Direction[] FACINGS = {Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST};

	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		SERVICE = BuildModule.standalone();
	}

	/** A drawing and its compiled blueprint for one spec. */
	private record Built(PlotSpec spec, TerrainProbe probe, Draft draft, BlueprintImpl bp) {
		List<String> problems() {
			return BlueprintChecks.all(bp, probe);
		}
	}

	/** A spec the way the planner makes one: a size from the range by seed, the floor at the ground before the front. */
	private static PlotSpec spec(ArchetypeInfo info, Culture c, long seed, Direction facing, TerrainProbe probe, CompoundTag params, int frontage,
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
		return new PlotSpec(info.id(), StyleRegistry.styleId(c), seed, new BlockPos(ox, floor, oz), sizeX, sizeZ, facing, Math.max(info.maxHeight(), 8), 2,
			params);
	}

	private static Built build(ArchetypeInfo info, Culture c, String terrain, long seed, Direction facing, CompoundTag params) {
		Random r = new Random(seed * 31 + info.id().hashCode());
		int frontage = info.minFrontage() + r.nextInt(info.maxFrontage() - info.minFrontage() + 1);
		int depth = info.minDepth() + r.nextInt(info.maxDepth() - info.minDepth() + 1);
		return build(info, c, terrain, seed, facing, params, frontage, depth);
	}

	private static Built build(ArchetypeInfo info, Culture c, String terrain, long seed, Direction facing, CompoundTag params, int frontage, int depth) {
		SyntheticTerrain probe = SyntheticTerrain.named(terrain, 42);
		PlotSpec spec = spec(info, c, seed, facing, probe, params, frontage, depth);
		return new Built(spec, probe, SERVICE.draw(spec, probe), SERVICE.compiled(spec, probe));
	}

	private static CompoundTag params(Consumer<CompoundTag> fill) {
		CompoundTag t = new CompoundTag();
		fill.accept(t);
		return t;
	}

	private static boolean water(BlockState s) {
		return s != null && net.minecraft.world.level.material.Fluids.WATER.isSame(s.getFluidState().getType());
	}

	/** Water at (x, y, z) in the drawing, or the natural water there where the drawing leaves the cell alone. */
	private static boolean wet(Draft d, int x, int y, int z) {
		BlockState s = d.get(x, y, z);
		if (s != null) {
			return water(s);
		}
		if (x < 0 || z < 0 || x >= d.frontage() || z >= d.depth()) {
			return false;
		}
		int wy = d.site().water(x, z);
		return wy != dev.voidmc.hearth.build.terrain.Site.NONE && y < wy && y >= d.site().ground(x, z);
	}

	/** Every forced value of an option, in every culture, on even and rough ground: the invariants hold. */
	private static void everyOption(ArchetypeInfo info, String key, List<String> values, String... terrains) {
		List<String> problems = new ArrayList<>();
		int n = 0;
		for (String v : values) {
			for (String terrain : terrains) {
				for (Culture c : Culture.values()) {
					long seed = 1301L * (v.hashCode() & 0xff) + c.ordinal() * 7717L + terrain.length();
					Direction facing = FACINGS[(c.ordinal() + v.length() + terrain.length()) % 4];
					CompoundTag p = params(t -> {
						if (v.equals("true") || v.equals("false")) {
							t.putBoolean(key, Boolean.parseBoolean(v));
						} else if (v.chars().allMatch(Character::isDigit)) {
							t.putInt(key, Integer.parseInt(v));
						} else {
							t.putString(key, v);
						}
					});
					for (String problem : build(info, c, terrain, seed, facing, p).problems()) {
						problems.add(key + "=" + v + " " + terrain + " " + c.getSerializedName() + " seed " + seed + " " + facing.getSerializedName() + ": " + problem);
					}
					n++;
				}
			}
		}
		assertTrue(problems.isEmpty(), info.id() + ": " + problems.size() + " of " + n + " problems:\n  " + String.join("\n  ", problems));
	}

	@Test
	void fieldFarmlandIsWateredWithinFourBlocks() {
		List<String> problems = new ArrayList<>();
		int farmland = 0;
		for (ArchetypeInfo info : List.of(Field.INFO, TerraceField.INFO)) {
			for (String terrain : new String[]{"flat", "slope", "hills", "steep", "river"}) {
				for (Culture c : Culture.values()) {
					long seed = 811L + c.ordinal() * 31L + terrain.hashCode();
					CompoundTag p = info == TerraceField.INFO ? params(t -> t.putString("crop", "beds")) : new CompoundTag();
					Draft d = build(info, c, terrain, seed, FACINGS[c.ordinal() % 4], p).draft();
					List<int[]> dry = new ArrayList<>();
					d.forEach((x, y, z, cell) -> {
						if (!cell.state().is(Blocks.FARMLAND)) {
							return;
						}
						for (int dz = -4; dz <= 4; dz++) {
							for (int dx = -4; dx <= 4; dx++) {
								if (wet(d, x + dx, y, z + dz) || wet(d, x + dx, y + 1, z + dz)) {
									return;
								}
							}
						}
						dry.add(new int[]{x, y, z});
					});
					int[] all = {0};
					d.forEach((x, y, z, cell) -> all[0] += cell.state().is(Blocks.FARMLAND) ? 1 : 0);
					farmland += all[0];
					if (!dry.isEmpty()) {
						int[] f = dry.getFirst();
						problems.add(info.id() + " " + terrain + " " + c.getSerializedName() + " seed " + seed + ": " + dry.size() + " of " + all[0]
							+ " farmland dry, e.g. at " + f[0] + "," + f[1] + "," + f[2]);
					}
				}
			}
		}
		assertTrue(farmland > 0);
		assertTrue(problems.isEmpty(), problems.size() + " dry farmland:\n  " + String.join("\n  ", problems.subList(0, Math.min(40, problems.size()))));
	}

	/** Water cells of a drawing with an open side at their own level: air there, or natural ground lower than they are. */
	private static List<String> leaks(Draft d) {
		List<String> leaks = new ArrayList<>();
		d.forEach((x, y, z, cell) -> {
			if (!water(cell.state())) {
				return;
			}
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				int nx = x + dir.getStepX();
				int nz = z + dir.getStepZ();
				BlockState n = d.get(nx, y, nz);
				// natural ground holds it (the site is sampled a little way round the plot), or a block that water cannot wash away
				boolean held = n != null ? water(n) || !n.isAir() && !n.canBeReplaced() : d.site().ground(nx, nz) > y;
				if (!held && !wet(d, nx, y, nz)) {
					leaks.add(x + "," + y + "," + z + " toward " + dir.getSerializedName());
				}
			}
		});
		return leaks;
	}

	private static int waterCells(Draft d) {
		int[] n = {0};
		d.forEach((x, y, z, cell) -> n[0] += cell.state().is(Blocks.WATER) ? 1 : 0);
		return n[0];
	}

	@Test
	void paddiesAndFieldsHoldTheirWater() {
		List<String> problems = new ArrayList<>();
		int sheets = 0;
		for (String terrain : new String[]{"flat", "slope", "hills", "steep"}) {
			for (Culture c : Culture.values()) {
				for (ArchetypeInfo info : List.of(Paddy.INFO, Field.INFO, TerraceField.INFO)) {
					if (!info.cultures().isEmpty() && !info.cultures().contains(c)) {
						continue;
					}
					for (int s = 0; s < 2; s++) {
						long seed = 4001L + s * 97L + c.ordinal() * 13L;
						CompoundTag p = info == TerraceField.INFO ? params(t -> t.putString("crop", "beds")) : new CompoundTag();
						Draft d = build(info, c, terrain, seed, FACINGS[(s + c.ordinal()) % 4], p).draft();
						sheets += info == Paddy.INFO ? waterCells(d) : 0;
						List<String> l = leaks(d);
						if (!l.isEmpty()) {
							problems.add(info.id() + " " + terrain + " " + c.getSerializedName() + " seed " + seed + ": water spills at " + l.size() + " cells, e.g. "
								+ l.getFirst());
						}
					}
				}
			}
		}
		assertTrue(sheets > 0, "no paddy water at all");
		assertTrue(problems.isEmpty(), problems.size() + " leaking:\n  " + String.join("\n  ", problems));
	}

	@Test
	void pasturesAreGatedAndWatered() {
		for (Culture c : Culture.values()) {
			for (String terrain : new String[]{"flat", "hills"}) {
				Built b = build(Pasture.INFO, c, terrain, 77L + c.ordinal(), Direction.SOUTH, new CompoundTag());
				int[] gates = {0};
				int[] water = {0};
				b.draft().forEach((x, y, z, cell) -> {
					BlockState s = cell.state();
					if (s.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock) {
						gates[0]++;
					}
					if (water(s) || s.is(Blocks.WATER_CAULDRON)) {
						water[0]++;
					}
				});
				assertTrue(gates[0] >= 1, "pasture in " + c + " on " + terrain + " has no gate");
				assertTrue(water[0] >= 1, "pasture in " + c + " on " + terrain + " has no water for its beasts");
				assertTrue(b.bp().pois().stream().anyMatch(p -> p.job() == JobType.HERDER), "pasture in " + c + " has no herder's place");
			}
		}
	}

	@Test
	void barnsHaveAWayUpToTheLoft() {
		for (Culture c : Culture.values()) {
			for (String terrain : new String[]{"flat", "slope"}) {
				Built b = build(Barn.INFO, c, terrain, 515L + c.ordinal() * 3L, FACINGS[c.ordinal() % 4], new CompoundTag());
				Draft d = b.draft();
				int[] climb = {0};
				int[] hay = {0};
				d.forEach((x, y, z, cell) -> {
					BlockState s = cell.state();
					if (s.is(Blocks.LADDER) || s.getBlock() instanceof net.minecraft.world.level.block.StairBlock && y >= 0 && y <= 3
						&& x > 0 && x < d.frontage() - 1 && z > 1 && z < d.depth() - 1) {
						climb[0]++;
					}
					if (s.is(Blocks.HAY_BLOCK) && y >= 3) {
						hay[0]++;
					}
				});
				var herder = b.bp().pois().stream().filter(p -> p.job() == JobType.HERDER).toList();
				assertTrue(!herder.isEmpty(), "barn in " + c + " has no herder's place");
				if (herder.stream().anyMatch(p -> p.pos().getY() >= b.spec().origin().getY() + 3)) {
					// the herder works up in the loft: there has to be a ladder or a stair up there
					assertTrue(climb[0] > 0, "barn in " + c + " on " + terrain + " has a loft job site but no way up");
				}
				assertTrue(hay[0] > 0, "barn in " + c + " on " + terrain + " stores no hay up high");
			}
		}
	}

	@Test
	void stiltHousesStandClearOfTheWater() {
		for (Culture c : new Culture[]{Culture.BAYOU, Culture.TROPICAL, Culture.MARITIME}) {
			for (int s = 0; s < 3; s++) {
				Built b = build(StiltHouse.INFO, c, "lake", 2024L + s * 13L + c.ordinal(), FACINGS[s], new CompoundTag());
				Draft d = b.draft();
				int highWater = Integer.MIN_VALUE;
				for (int z = 0; z < d.depth(); z++) {
					for (int x = 0; x < d.frontage(); x++) {
						if (d.site().isWater(x, z)) {
							highWater = Math.max(highWater, d.site().water(x, z));
						}
					}
				}
				int[] piles = {0};
				int water = highWater;
				d.forEach((x, y, z, cell) -> {
					BlockState st = cell.state();
					if (y < water && st.hasProperty(BlockStateProperties.AXIS) && st.getValue(BlockStateProperties.AXIS) == Direction.Axis.Y) {
						piles[0]++;
					}
				});
				int origin = b.spec().origin().getY();
				for (BlockPos e : b.bp().entrances()) {
					assertTrue(highWater == Integer.MIN_VALUE || e.getY() - origin >= highWater,
						"stilt house in " + c + " seed " + s + " opens into the water (entrance " + (e.getY() - origin) + ", water " + highWater + ")");
				}
				if (highWater != Integer.MIN_VALUE) {
					assertTrue(piles[0] >= 4, "stilt house in " + c + " seed " + s + " stands in the water without piles");
				}
				assertTrue(b.problems().isEmpty(), "stilt house in " + c + " on the lake: " + b.problems());
			}
		}
	}

	@Test
	void longhousesLiveRoundOneFire() {
		for (Culture c : new Culture[]{Culture.NORTHERN, Culture.TUNDRA, Culture.MOORLAND}) {
			for (int s = 0; s < 4; s++) {
				Built b = build(Longhouse.INFO, c, s % 2 == 0 ? "flat" : "hills", 909L + s * 5L + c.ordinal(), FACINGS[s], new CompoundTag());
				Draft d = b.draft();
				List<int[]> fires = new ArrayList<>();
				d.forEach((x, y, z, cell) -> {
					if (cell.state().is(Blocks.CAMPFIRE) && y >= -1 && y <= 2) {
						fires.add(new int[]{x, z});
					}
				});
				assertTrue(!fires.isEmpty(), "longhouse in " + c + " seed " + s + " has no fire");
				int[] f = fires.getFirst();
				int dp = d.depth();
				assertTrue(f[1] > dp / 5 && f[1] < dp - dp / 5, "longhouse in " + c + " keeps its fire at an end (z " + f[1] + " of " + dp + ")");
				assertTrue(b.bp().pois().stream().anyMatch(p -> p.type() == PoiType.HEARTH), "longhouse in " + c + " has no hearth POI");
				assertTrue(b.bp().beds() >= 2, "longhouse in " + c + " sleeps fewer than two");
			}
		}
	}

	@Test
	void splitLevelCottagesOpenTheirLowerStoreyDownhill() {
		int split = 0;
		List<String> problems = new ArrayList<>();
		for (Culture c : Culture.values()) {
			for (Direction facing : FACINGS) {
				CompoundTag p = params(t -> t.putBoolean("split", true));
				Built b = build(Cottage.INFO, c, "steep", 333L + c.ordinal() * 11L, facing, p, 9, 9);
				Set<Integer> levels = new HashSet<>();
				for (BlockPos e : b.bp().entrances()) {
					levels.add(e.getY());
				}
				if (levels.size() >= 2) {
					split++;
				}
				for (String problem : b.problems()) {
					problems.add(c.getSerializedName() + " " + facing.getSerializedName() + ": " + problem);
				}
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " problems:\n  " + String.join("\n  ", problems));
		assertTrue(split >= 8, "only " + split + " split-level cottages opened a door on two levels");
	}

	/** Every home of the family sleeps someone wherever it stands, and about as many as it promises the planner. */
	@Test
	void everyHomeSleepsItsHousehold() {
		List<String> problems = new ArrayList<>();
		for (ArchetypeInfo info : List.of(Farmhouse.INFO, Longhouse.INFO, RoundHouse.INFO, StiltHouse.INFO, Cottage.INFO,
			dev.voidmc.hearth.build.archetype.house.Hut.INFO)) {
			for (Culture c : Culture.values()) {
				if (!info.cultures().isEmpty() && !info.cultures().contains(c)) {
					continue;
				}
				int sum = 0;
				int n = 0;
				for (String terrain : new String[]{"flat", "slope", "hills", "river", "coast", "steep"}) {
					for (int s = 0; s < 2; s++) {
						long seed = 7919L * (s + 1) + c.ordinal() * 104729L;
						Built b = build(info, c, terrain, seed, FACINGS[(s + c.ordinal()) % 4], new CompoundTag());
						int beds = b.bp().beds();
						if (beds < 1) {
							problems.add(info.id() + " in " + c.getSerializedName() + " on " + terrain + " seed " + seed + " has no bed");
						}
						sum += beds;
						n++;
					}
				}
				if (sum < 0.6 * info.beds() * n) {
					problems.add(info.id() + " in " + c.getSerializedName() + " sleeps " + String.format("%.1f", sum / (double) n) + " on average, promises "
						+ info.beds());
				}
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " problems:\n  " + String.join("\n  ", problems));
	}

	@Test
	void cottagesGrowIntoTownHouses() {
		assertEquals(Set.of(Hearth.id("house/rowhouse"), Hearth.id("house/shophouse"), Hearth.id("house/townhouse")), Set.copyOf(Cottage.INFO.upgradesTo()));
	}

	@Test
	void everyFarmhouseLayoutHoldsTheInvariants() {
		everyOption(Farmhouse.INFO, "layout", List.of("steading", "laithe", "court", "tun", "compound", "raised"), "flat", "slope", "hills");
	}

	@Test
	void everyTerraceCropHoldsTheInvariants() {
		everyOption(TerraceField.INFO, "crop", List.of("vines", "tea", "meadow", "beds"), "slope", "steep", "hills");
	}

	@Test
	void everyLonghouseCoverAndBarnLoftHoldsTheInvariants() {
		everyOption(Longhouse.INFO, "roof", List.of("turf", "shingle", "thatch"), "flat", "slope", "river");
		everyOption(Barn.INFO, "loft", List.of("1", "2", "3"), "flat", "slope", "river");
	}

	/** Round houses are round: the wall at head height mirrors itself across the ring, and a fire burns inside. */
	@Test
	void roundHousesAreRound() {
		List<String> problems = new ArrayList<>();
		for (Culture c : new Culture[]{Culture.STEPPE, Culture.TROPICAL}) {
			for (int size = 9; size <= 13; size++) {
				Built b = build(RoundHouse.INFO, c, "flat", 515L + size * 3L + c.ordinal(), FACINGS[size % 4], new CompoundTag(), size, size);
				Draft d = b.draft();
				if (b.bp().pois().stream().noneMatch(p -> p.type() == PoiType.HEARTH)) {
					problems.add(c + " " + size + ": no hearth");
				}
				int floorY = d.pois().stream().filter(p -> p.type() == PoiType.DOOR).findFirst().map(Draft.LocalPoi::y).orElse(0);
				// the solid cells two above the floor, across the plot: the ring wall (and posts, the veranda's posts)
				Set<Long> wall = new HashSet<>();
				d.forEach((x, y, z, cell) -> {
					if (y == floorY + 2 && !cell.state().isAir()
						&& !cell.state().getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()) {
						wall.add(BlockPos.asLong(x, 0, z));
					}
				});
				if (wall.isEmpty()) {
					problems.add(c + " " + size + ": no wall");
					continue;
				}
				// mirrored across the line through the door (the ring's centre): a woodpile or a pot beside it may break it
				int axis2 = 2 * d.pois().stream().filter(p -> p.type() == PoiType.DOOR).findFirst().map(Draft.LocalPoi::x).orElse(d.frontage() / 2);
				int asym = 0;
				for (long k : wall) {
					if (!wall.contains(BlockPos.asLong(axis2 - BlockPos.getX(k), 0, BlockPos.getZ(k)))) {
						asym++;
					}
				}
				// a window, a shutter or a lantern may break the mirror; a lopsided or squared ring breaks it everywhere
				if (asym > wall.size() / 6) {
					StringBuilder map = new StringBuilder();
					for (int z = -1; z < d.depth(); z++) {
						map.append("\n    ");
						for (int x = 0; x < d.frontage(); x++) {
							map.append(wall.contains(BlockPos.asLong(x, 0, z)) ? '#' : '.');
						}
					}
					problems.add(c + " " + size + ": wall not symmetric (" + asym + " of " + wall.size() + ") at y " + (floorY + 2) + map);
				}
			}
		}
		assertTrue(problems.isEmpty(), String.join("\n  ", problems));
	}

	/** A stilt house on the water moors at a landing behind it (DOCK) and keeps its life on its galleries (seats, views). */
	@Test
	void stiltHousesHaveALandingAndGalleries() {
		for (Culture c : new Culture[]{Culture.BAYOU, Culture.TROPICAL, Culture.MARITIME}) {
			int docks = 0;
			for (int s = 0; s < 3; s++) {
				Built b = build(StiltHouse.INFO, c, "lake", 4040L + s * 17L + c.ordinal(), FACINGS[s], new CompoundTag(), 11, 12);
				assertTrue(b.problems().isEmpty(), "stilt house in " + c + ": " + b.problems());
				if (b.bp().pois().stream().anyMatch(p -> p.type() == PoiType.DOCK)) {
					docks++;
				}
				assertTrue(b.bp().pois().stream().filter(p -> p.type() == PoiType.VIEWPOINT || p.type() == PoiType.SEAT).count() >= 2,
					"stilt house in " + c + " seed " + s + " has no life on its galleries");
				assertTrue(b.bp().pois().stream().anyMatch(p -> p.type() == PoiType.HEARTH), "stilt house in " + c + " has no stove");
			}
			assertTrue(docks >= 2, "stilt houses in " + c + " moor at a landing only " + docks + " of 3 times");
		}
	}

	/** Pastures and barn stalls mark where their beasts are kept (WORK_FACE for the HERDER). */
	@Test
	void pensMarkWhereBeastsAreKept() {
		for (Culture c : new Culture[]{Culture.TEMPERATE, Culture.DESERT, Culture.NORTHERN, Culture.STEPPE}) {
			Built p = build(Pasture.INFO, c, "flat", 707L + c.ordinal(), Direction.SOUTH, new CompoundTag());
			assertTrue(p.bp().pois().stream().anyMatch(x -> x.type() == PoiType.WORK_FACE && x.job() == JobType.HERDER), "pasture in " + c + " has no pen");
			CompoundTag loft = new CompoundTag();
			loft.putInt("loft", 2);
			Built b = build(Barn.INFO, c, "flat", 808L + c.ordinal(), Direction.SOUTH, loft);
			if (b.bp().pois().stream().noneMatch(x -> x.type() == PoiType.WORK_FACE && x.job() == JobType.HERDER)) {
				// a flat-roofed byre keeps its beasts in stalls too
				assertTrue(false, "barn in " + c + " has no stall marked");
			}
		}
	}

	/** Homes dressed for their household: a merchant's strongbox, a smith's anvil; still sound. */
	@Test
	void householdsDressTheirHomes() {
		List<String> problems = new ArrayList<>();
		for (Culture c : new Culture[]{Culture.TEMPERATE, Culture.NORTHERN, Culture.DESERT, Culture.BAYOU}) {
			CompoundTag merchant = params(t -> t.putString("station", "merchant"));
			Built m = build(Cottage.INFO, c, "flat", 909L + c.ordinal(), Direction.SOUTH, merchant, 10, 10);
			if (m.bp().pois().stream().noneMatch(p -> p.type() == PoiType.STRONGBOX)) {
				problems.add(c + ": a merchant's cottage has no strongbox");
			}
			problems.addAll(m.problems().stream().map(x -> c + " merchant: " + x).toList());
			CompoundTag smith = params(t -> {
				CompoundTag h = new CompoundTag();
				h.putString("job", "smith");
				t.put("household", h);
			});
			Built s = build(Cottage.INFO, c, "flat", 919L + c.ordinal(), Direction.SOUTH, smith, 10, 10);
			int[] anvils = {0};
			s.draft().forEach((x, y, z, cell) -> {
				if (cell.state().is(Blocks.CHIPPED_ANVIL)) {
					anvils[0]++;
				}
			});
			if (anvils[0] == 0) {
				problems.add(c + ": a smith's cottage has no anvil");
			}
			problems.addAll(s.problems().stream().map(x -> c + " smith: " + x).toList());
		}
		assertTrue(problems.isEmpty(), String.join("\n  ", problems));
	}

	@Test
	void turfAndShingleCottagesHoldTheInvariants() {
		everyOption(Cottage.INFO, "turf", List.of("true", "false"), "flat", "slope", "steep");
	}

	@Test
	void roundAndStiltHousesHoldTheInvariantsEitherWay() {
		everyOption(RoundHouse.INFO, "stilts", List.of("true", "false"), "flat", "slope", "coast");
		everyOption(RoundHouse.INFO, "veranda", List.of("true", "false"), "flat", "hills", "lake");
		everyOption(StiltHouse.INFO, "side_walk", List.of("true", "false"), "lake", "coast", "river", "flat");
	}
}
