package dev.voidmc.hearth.build.lawhouse;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
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
import dev.voidmc.hearth.build.blueprint.LocalFrame;
import dev.voidmc.hearth.build.check.BlueprintChecks;
import dev.voidmc.hearth.build.style.StyleRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

/**
 * What the law's family promises beyond the shared invariants: the places the law module and the officials use (the
 * judge's high seat, the cells, the pillory, the gallows, the guard posts and the gate), in every culture and at the
 * tiers they are built at; the cells really hold (a prisoner cannot walk out without passing a door); the toll gate keeps
 * its road open straight through; every building fits the lowest height it announces and holds the invariants on every
 * terrain, culture, seed and dressing (the same loops as {@code BlueprintInvariantsTest}, kept here so the family can be
 * checked on its own); every name is translated.
 */
class LawHouseArchetypesTest {
	private static final BlueprintServiceImpl SERVICE;
	private static final Direction[] FACINGS = {Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST};
	private static final String[] TERRAINS = {"flat", "slope", "hills", "river", "coast", "steep"};

	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		SERVICE = BuildModule.standalone();
	}

	/** The points each archetype must register in every culture (beyond its jobs). */
	private static final Map<String, Set<PoiType>> REQUIRED = Map.of(
		"law/courthouse", EnumSet.of(PoiType.THRONE, PoiType.SEAT, PoiType.LECTERN, PoiType.STORAGE, PoiType.NOTICE_BOARD, PoiType.DOOR),
		"law/jail", EnumSet.of(PoiType.CELL, PoiType.GUARD_POST, PoiType.STORAGE, PoiType.BED, PoiType.DOOR),
		"law/gallows", EnumSet.of(PoiType.GALLOWS, PoiType.MEETING_POINT),
		"law/pillory", EnumSet.of(PoiType.PILLORY, PoiType.MEETING_POINT),
		"law/watch_house", EnumSet.of(PoiType.GUARD_POST, PoiType.VIEWPOINT, PoiType.BED, PoiType.DOOR),
		"law/toll_gate", EnumSet.of(PoiType.GATE, PoiType.GUARD_POST, PoiType.BED, PoiType.DOOR));

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

	private static List<ArchetypeInfo> family() {
		List<ArchetypeInfo> out = new ArrayList<>();
		for (String id : List.of("law/courthouse", "law/jail", "law/gallows", "law/pillory", "law/watch_house", "law/toll_gate")) {
			ArchetypeInfo info = SERVICE.archetype(Hearth.id(id));
			assertTrue(info != null, id + " is not registered");
			assertTrue(info.category() == ArchetypeCategory.LAW, id + " is not in the LAW category");
			out.add(info);
		}
		return out;
	}

	@Test
	void everyBuildingRegistersWhatTheLawUses() {
		List<String> problems = new ArrayList<>();
		SyntheticTerrain probe = SyntheticTerrain.named("flat", 3);
		for (ArchetypeInfo info : family()) {
			Set<PoiType> want = REQUIRED.get(info.id().getPath());
			for (Culture c : Culture.values()) {
				for (int s = 0; s < 3; s++) {
					long seed = 5150L + s * 977L + c.ordinal() * 31L;
					for (int tier : new int[]{1, 2, 4}) {
						BlueprintImpl bp = SERVICE.compiled(spec(info, c, seed, FACINGS[(s + c.ordinal()) % 4], probe, info.maxHeight(), tier, new CompoundTag()), probe);
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
						if (info.id().getPath().equals("law/jail") && bp.pois().stream().filter(p -> p.type() == PoiType.CELL).count() < 2) {
							problems.add("jail " + c.getSerializedName() + " seed " + seed + " has fewer than two cells");
						}
					}
				}
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " problems:\n  " + String.join("\n  ", problems));
	}

	/**
	 * A cell holds: from its point, every way out more than three blocks (the law module's jailbreak distance) passes a
	 * door (picked, not walked through), and the jailer's place two blocks out from the cell is outside it.
	 */
	@Test
	void cellsHoldTheirPrisoners() {
		ArchetypeInfo info = SERVICE.archetype(Hearth.id("law/jail"));
		List<String> problems = new ArrayList<>();
		for (String terrain : new String[]{"flat", "slope"}) {
			SyntheticTerrain probe = SyntheticTerrain.named(terrain, 5);
			for (Culture c : Culture.values()) {
				for (int s = 0; s < 2; s++) {
					long seed = 808L + s * 13L + c.ordinal() * 7L;
					PlotSpec spec = spec(info, c, seed, FACINGS[(s + c.ordinal()) % 4], probe, info.maxHeight(), 2, new CompoundTag());
					BlueprintImpl bp = SERVICE.compiled(spec, probe);
					Map<Long, BlockState> placed = placed(bp);
					for (Poi p : bp.pois()) {
						if (p.type() != PoiType.CELL) {
							continue;
						}
						int escaped = flood(placed, p.pos(), 4);
						if (escaped > 0) {
							problems.add(terrain + " " + c.getSerializedName() + " seed " + seed + ": the cell at " + p.pos() + " lets its prisoner walk "
								+ "out without a door");
						}
						BlockPos jailer = p.pos().relative(p.facing().getOpposite(), 2);
						BlockState at = placed.getOrDefault(jailer.asLong(), Blocks.AIR.defaultBlockState());
						if (!at.isAir() && !(at.getBlock() instanceof net.minecraft.world.level.block.CarpetBlock)) {
							problems.add(terrain + " " + c.getSerializedName() + " seed " + seed + ": the jailer's place before the cell at " + p.pos()
								+ " is " + at);
						}
					}
				}
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " problems:\n  " + String.join("\n  ", problems));
	}

	/** Cells reached from a cell's point within the bounds of its walls without opening a door, beyond {@code r} blocks. */
	private static int flood(Map<Long, BlockState> placed, BlockPos from, int r) {
		java.util.ArrayDeque<BlockPos> q = new java.util.ArrayDeque<>();
		java.util.Set<Long> seen = new java.util.HashSet<>();
		q.add(from);
		seen.add(from.asLong());
		int far = 0;
		while (!q.isEmpty() && seen.size() < 400) {
			BlockPos p = q.poll();
			if (Math.abs(p.getX() - from.getX()) + Math.abs(p.getZ() - from.getZ()) > r) {
				far++;
				continue;
			}
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				BlockPos n = p.relative(dir);
				if (seen.contains(n.asLong())) {
					continue;
				}
				BlockState feet = placed.getOrDefault(n.asLong(), Blocks.AIR.defaultBlockState());
				BlockState head = placed.getOrDefault(n.above().asLong(), Blocks.AIR.defaultBlockState());
				boolean open = passable(feet) && passable(head);
				if (open) {
					seen.add(n.asLong());
					q.add(n);
				}
			}
		}
		return far;
	}

	private static boolean passable(BlockState s) {
		if (s.getBlock() instanceof DoorBlock) {
			return false;
		}
		return s.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
	}

	private static Map<Long, BlockState> placed(BlueprintImpl bp) {
		Map<Long, BlockState> out = new HashMap<>();
		for (int s = 0; s < bp.stageCount(); s++) {
			for (int i = 0; i < bp.stepCount(s); i++) {
				BlockPos p = bp.pos(s, i);
				out.put(p.asLong(), bp.state(s, i));
			}
		}
		return out;
	}

	/** The toll gate over its road keeps the road open: front to back, two rows high, in the middle of the plot. */
	@Test
	void tollGateKeepsTheRoadOpen() {
		ArchetypeInfo info = SERVICE.archetype(Hearth.id("law/toll_gate"));
		List<String> problems = new ArrayList<>();
		for (String terrain : new String[]{"flat", "slope", "hills"}) {
			SyntheticTerrain probe = SyntheticTerrain.named(terrain, 9);
			for (Culture c : Culture.values()) {
				for (int width : new int[]{3, 5}) {
					CompoundTag params = new CompoundTag();
					params.putBoolean("over_road", true);
					params.putInt("passage_width", width);
					long seed = 1200L + width + c.ordinal();
					int frontage = Math.max(info.minFrontage(), Math.min(info.maxFrontage(), width + 4));
					PlotSpec spec = spec(info, c, seed, FACINGS[c.ordinal() % 4], probe, info.maxHeight(), 2, params, frontage, info.minDepth());
					BlueprintImpl bp = SERVICE.compiled(spec, probe);
					for (String p : BlueprintChecks.all(bp, probe)) {
						problems.add(terrain + " " + c.getSerializedName() + " width " + width + ": " + p);
					}
					LocalFrame f = new LocalFrame(spec);
					Map<Long, BlockState> placed = placed(bp);
					int cx = (spec.frontage() - 1) / 2;
					for (int lz = 0; lz < spec.depth(); lz++) {
						for (int ly = 0; ly <= 1; ly++) {
							int wx = f.worldX(cx, lz);
							int wz = f.worldZ(cx, lz);
							BlockState s = placed.get(BlockPos.asLong(wx, spec.floorY() + ly, wz));
							if (s != null && !passable(s)) {
								problems.add(terrain + " " + c.getSerializedName() + " width " + width + ": the road is blocked at row " + lz + " by " + s);
							}
						}
					}
				}
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " problems:\n  " + String.join("\n  ", problems));
	}

	/** The pillory and the gallows stand in the square at the sizes the square gives them, and hold. */
	@Test
	void squareFurnitureHoldsOnTheSquare() {
		List<String> problems = new ArrayList<>();
		SyntheticTerrain probe = SyntheticTerrain.named("flat", 2);
		for (String id : new String[]{"law/pillory", "law/gallows"}) {
			ArchetypeInfo info = SERVICE.archetype(Hearth.id(id));
			for (Culture c : Culture.values()) {
				for (int size = info.minFrontage(); size <= info.maxFrontage(); size++) {
					CompoundTag params = new CompoundTag();
					params.putBoolean("on_square", true);
					long seed = 77L + size + c.ordinal() * 3L;
					PlotSpec spec = spec(info, c, seed, FACINGS[(size + c.ordinal()) % 4], probe, info.maxHeight(), 1, params, size, size);
					for (String p : BlueprintChecks.all(SERVICE.compiled(spec, probe), probe)) {
						problems.add(id + " " + c.getSerializedName() + " " + size + "x" + size + ": " + p);
					}
				}
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " problems:\n  " + String.join("\n  ", problems));
	}

	/** The planner may give the lowest height a building announces: every one still fits and stands. */
	@Test
	void everyBuildingFitsTheLowestHeightItAnnounces() {
		List<String> problems = new ArrayList<>();
		for (String terrain : new String[]{"flat", "slope", "hills"}) {
			SyntheticTerrain probe = SyntheticTerrain.named(terrain, 11);
			for (ArchetypeInfo info : family()) {
				for (Culture c : Culture.values()) {
					long seed = 90210L + c.ordinal();
					PlotSpec spec = spec(info, c, seed, FACINGS[c.ordinal() % 4], probe, info.minHeight(), 2, new CompoundTag());
					for (String p : BlueprintChecks.all(SERVICE.compiled(spec, probe), probe)) {
						problems.add(info.id().getPath() + " " + c.getSerializedName() + " " + terrain + " at height " + info.minHeight() + ": " + p);
					}
				}
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " problems:\n  " + String.join("\n  ", problems));
	}

	/** The shared invariants, for the family alone: every culture, six terrains, two seeds, turning facings. */
	@Test
	void invariantsHoldOnEveryTerrain() {
		List<String> problems = new ArrayList<>();
		for (ArchetypeInfo info : family()) {
			for (String terrain : TERRAINS) {
				SyntheticTerrain probe = SyntheticTerrain.named(terrain, 42);
				for (Culture c : Culture.values()) {
					for (int s = 0; s < 2; s++) {
						long seed = 7919L * (s + 1) + c.ordinal() * 104729L;
						Direction facing = FACINGS[(s + c.ordinal()) % 4];
						PlotSpec spec = spec(info, c, seed, facing, probe, Math.max(info.maxHeight(), 8), 2, new CompoundTag());
						for (String p : BlueprintChecks.all(SERVICE.compiled(spec, probe), probe)) {
							problems.add(info.id().getPath() + " " + terrain + " " + c.getSerializedName() + " seed " + seed + " " + facing.getSerializedName()
								+ ": " + p);
						}
					}
				}
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " problems:\n  " + String.join("\n  ", problems.subList(0, Math.min(60, problems.size()))));
	}

	/** Every dressing of every culture, forced, on a slope. */
	@Test
	void everyDressingHolds() {
		List<String> problems = new ArrayList<>();
		SyntheticTerrain probe = SyntheticTerrain.named("slope", 42);
		for (ArchetypeInfo info : family()) {
			for (Culture c : Culture.values()) {
				var style = SERVICE.styles().get(StyleRegistry.styleId(c));
				for (int v = 0; v < style.variants().size(); v++) {
					CompoundTag params = new CompoundTag();
					params.putInt("variant", v);
					long seed = 31337L + v * 7L + c.ordinal() * 1009L;
					Direction facing = FACINGS[(v + c.ordinal()) % 4];
					PlotSpec spec = spec(info, c, seed, facing, probe, Math.max(info.maxHeight(), 8), 2, params);
					for (String p : BlueprintChecks.all(SERVICE.compiled(spec, probe), probe)) {
						problems.add(info.id().getPath() + " " + c.getSerializedName() + " (" + style.variants().get(v).name() + ") seed " + seed + ": " + p);
					}
				}
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " problems:\n  " + String.join("\n  ", problems.subList(0, Math.min(60, problems.size()))));
	}

	@Test
	void everyNameIsTranslated() throws Exception {
		Path file = Path.of("src/main/resources/assets/hearth/lang/en_us/build_lawhouse.json");
		assertTrue(Files.exists(file), "build_lawhouse.json is missing");
		JsonObject lang;
		try (var in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			lang = JsonParser.parseReader(in).getAsJsonObject();
		}
		for (ArchetypeInfo info : family()) {
			String key = "hearth.archetype." + info.id().getPath().replace('/', '.');
			assertTrue(lang.has(key), key + " is not in build_lawhouse.json");
		}
		for (String key : new String[]{"hearth.build.law.jail", "hearth.build.law.toll"}) {
			assertTrue(lang.has(key), key + " is not in build_lawhouse.json");
		}
	}
}
