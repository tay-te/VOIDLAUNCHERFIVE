package dev.voidmc.hearth.sim.dev;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.BlueprintService;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.api.TerrainProbe;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jspecify.annotations.Nullable;

/**
 * Stand-in buildings so the sim can be run and tested before the build module is merged: plain boxes with a door, a bed
 * and a flat roof; a fenced builders' yard; a wheat field; a well; a storehouse; a graded road. They use the catalogue's
 * archetype ids (ARCHITECTURE.md §6.1) and params, so plots planned with them stay valid when the real generators
 * arrive. Pure functions of spec and terrain; thread-safe.
 */
public final class PlaceholderBlueprints implements BlueprintService {
	public static final Identifier COTTAGE = Hearth.id("house/cottage");
	public static final Identifier HUT = Hearth.id("house/hut");
	public static final Identifier YARD = Hearth.id("work/builders_yard");
	public static final Identifier FIELD = Hearth.id("farm/field");
	public static final Identifier WELL = Hearth.id("landscape/well");
	public static final Identifier STOREHOUSE = Hearth.id("storage/storehouse");
	public static final Identifier ROAD = Hearth.id("infra/road");

	private static final Map<Culture, Palette> PALETTES = new EnumMap<>(Culture.class);
	private final Map<Identifier, ArchetypeInfo> archetypes = new LinkedHashMap<>();
	private final Map<PlotSpec, Blueprint> cache = new ConcurrentHashMap<>();

	private record Palette(Block log, Block planks, Block floor, Block roof, Block wall, Block door, Block path, int wallColor, int roofColor) {
	}

	static {
		Palette oak = new Palette(Blocks.OAK_LOG, Blocks.OAK_PLANKS, Blocks.COBBLESTONE, Blocks.DARK_OAK_PLANKS, Blocks.OAK_PLANKS, Blocks.OAK_DOOR,
			Blocks.DIRT_PATH, 0xB08D57, 0x4A3220);
		Palette spruce = new Palette(Blocks.SPRUCE_LOG, Blocks.SPRUCE_PLANKS, Blocks.COBBLESTONE, Blocks.DARK_OAK_PLANKS, Blocks.SPRUCE_PLANKS,
			Blocks.SPRUCE_DOOR, Blocks.DIRT_PATH, 0x6E5232, 0x3C2A1A);
		for (Culture c : Culture.values()) {
			PALETTES.put(c, oak);
		}
		PALETTES.put(Culture.NORTHERN, spruce);
		PALETTES.put(Culture.TUNDRA, spruce);
		PALETTES.put(Culture.ALPINE, new Palette(Blocks.SPRUCE_LOG, Blocks.SPRUCE_PLANKS, Blocks.STONE_BRICKS, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_PLANKS,
			Blocks.SPRUCE_DOOR, Blocks.DIRT_PATH, 0x7A5A38, 0x5A4028));
		PALETTES.put(Culture.WISTERIA, new Palette(Blocks.CHERRY_LOG, Blocks.CHERRY_PLANKS, Blocks.STONE_BRICKS, Blocks.DYED_TERRACOTTA.pick(DyeColor.PURPLE),
			Blocks.DYED_TERRACOTTA.pick(DyeColor.WHITE), Blocks.CHERRY_DOOR, Blocks.DIRT_PATH, 0xD8C8C0, 0x7A4A6A));
		PALETTES.put(Culture.MOORLAND, new Palette(Blocks.DARK_OAK_LOG, Blocks.COBBLESTONE, Blocks.COBBLESTONE, Blocks.DEEPSLATE_TILES,
			Blocks.COBBLESTONE, Blocks.DARK_OAK_DOOR, Blocks.GRAVEL, 0x7A7A7A, 0x3A3A40));
		PALETTES.put(Culture.MARITIME, new Palette(Blocks.SPRUCE_LOG, Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.DARK_OAK_PLANKS,
			Blocks.STONE_BRICKS, Blocks.SPRUCE_DOOR, Blocks.GRAVEL, 0x8A8A8A, 0x3C2A1A));
		PALETTES.put(Culture.TROPICAL, new Palette(Blocks.JUNGLE_LOG, Blocks.BAMBOO_PLANKS, Blocks.JUNGLE_PLANKS, Blocks.HAY_BLOCK, Blocks.BAMBOO_PLANKS,
			Blocks.JUNGLE_DOOR, Blocks.DIRT_PATH, 0xC8B060, 0xA89040));
		PALETTES.put(Culture.DESERT, new Palette(Blocks.CUT_SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE,
			Blocks.SMOOTH_SANDSTONE, Blocks.JUNGLE_DOOR, Blocks.SANDSTONE, 0xE0D0A0, 0xD8C890));
		PALETTES.put(Culture.MEDITERRANEAN, new Palette(Blocks.STRIPPED_OAK_LOG, Blocks.DYED_TERRACOTTA.pick(DyeColor.WHITE), Blocks.STONE_BRICKS, Blocks.TERRACOTTA,
			Blocks.DYED_TERRACOTTA.pick(DyeColor.WHITE), Blocks.OAK_DOOR, Blocks.GRAVEL, 0xE8E0D8, 0xA0583A));
		PALETTES.put(Culture.STEPPE, new Palette(Blocks.ACACIA_LOG, Blocks.ACACIA_PLANKS, Blocks.PACKED_MUD, Blocks.HAY_BLOCK, Blocks.DYED_TERRACOTTA.pick(DyeColor.ORANGE),
			Blocks.ACACIA_DOOR, Blocks.COARSE_DIRT, 0xA86A3A, 0xA89040));
		PALETTES.put(Culture.KARST, new Palette(Blocks.BAMBOO_BLOCK, Blocks.BAMBOO_PLANKS, Blocks.STONE_BRICKS, Blocks.DYED_TERRACOTTA.pick(DyeColor.GREEN),
			Blocks.BAMBOO_PLANKS, Blocks.BAMBOO_DOOR, Blocks.GRAVEL, 0xC8B060, 0x4A6040));
		PALETTES.put(Culture.BAYOU, new Palette(Blocks.MANGROVE_LOG, Blocks.MANGROVE_PLANKS, Blocks.MUD_BRICKS, Blocks.DARK_OAK_PLANKS,
			Blocks.MANGROVE_PLANKS, Blocks.MANGROVE_DOOR, Blocks.MUD, 0x7A3A30, 0x3C2A1A));
		PALETTES.put(Culture.MESA, new Palette(Blocks.TERRACOTTA, Blocks.DYED_TERRACOTTA.pick(DyeColor.ORANGE), Blocks.TERRACOTTA, Blocks.DYED_TERRACOTTA.pick(DyeColor.RED),
			Blocks.DYED_TERRACOTTA.pick(DyeColor.ORANGE), Blocks.ACACIA_DOOR, Blocks.COARSE_DIRT, 0xA8603A, 0x8E3A2A));
	}

	public PlaceholderBlueprints() {
		info(COTTAGE, ArchetypeCategory.HOUSING, Set.of(), 2, 7, 11, 7, 11, 6, 9, SettlementTier.CAMP, Set.of());
		info(HUT, ArchetypeCategory.HOUSING, Set.of(), 1, 5, 7, 5, 7, 5, 7, SettlementTier.CAMP, Set.of(ArchetypeInfo.SiteFlag.OUTSKIRTS));
		info(YARD, ArchetypeCategory.WORKPLACE, Set.of(JobType.BUILDER), 0, 7, 11, 7, 11, 3, 5, SettlementTier.CAMP, Set.of());
		info(FIELD, ArchetypeCategory.FARM, Set.of(JobType.FARMER), 0, 7, 15, 7, 15, 2, 3, SettlementTier.CAMP,
			Set.of(ArchetypeInfo.SiteFlag.OUTSKIRTS));
		info(WELL, ArchetypeCategory.LANDSCAPE, Set.of(), 0, 5, 5, 5, 5, 4, 5, SettlementTier.CAMP, Set.of(ArchetypeInfo.SiteFlag.CENTRAL));
		info(STOREHOUSE, ArchetypeCategory.STORAGE, Set.of(), 0, 7, 11, 7, 11, 6, 8, SettlementTier.HAMLET, Set.of());
		info(ROAD, ArchetypeCategory.INFRASTRUCTURE, Set.of(), 0, 1, 512, 1, 512, 3, 4, SettlementTier.CAMP, Set.of());
		// phase 3: excavations, for the extraction loop (never proposed by the placeholder planner; tests add them)
		for (ArchetypeInfo x : PlaceholderExcavations.infos()) {
			this.archetypes.put(x.id(), x);
		}
	}

	private void info(Identifier id, ArchetypeCategory category, Set<JobType> jobs, int beds, int minF, int maxF, int minD, int maxD, int minH,
		int maxH, SettlementTier tier, Set<ArchetypeInfo.SiteFlag> flags) {
		this.archetypes.put(id, new ArchetypeInfo(id, category, jobs, beds, minF, maxF, minD, maxD, minH, maxH, tier, Set.of(), flags, List.of(), 0,
			1.0));
	}

	@Override
	public Blueprint blueprint(PlotSpec spec, TerrainProbe terrain) {
		Blueprint bp = this.cache.get(spec);
		if (bp == null) {
			bp = generate(spec, terrain);
			if (this.cache.size() > 4096) {
				this.cache.clear();
			}
			this.cache.put(spec, bp);
		}
		return bp;
	}

	@Override
	public @Nullable ArchetypeInfo archetype(Identifier id) {
		return this.archetypes.get(id);
	}

	@Override
	public Collection<ArchetypeInfo> archetypes() {
		return this.archetypes.values();
	}

	@Override
	public Identifier style(Culture culture) {
		return Hearth.id(culture.getSerializedName());
	}

	static Palette palette(PlotSpec spec) {
		for (Culture c : Culture.values()) {
			if (c.getSerializedName().equals(spec.style().getPath())) {
				return PALETTES.get(c);
			}
		}
		return PALETTES.get(Culture.TEMPERATE);
	}

	static Blueprint generate(PlotSpec spec, TerrainProbe terrain) {
		Identifier a = spec.archetype();
		if (a.equals(COTTAGE) || a.equals(HUT) || a.equals(STOREHOUSE)) {
			return house(spec, terrain, a.equals(STOREHOUSE));
		}
		if (a.equals(YARD)) {
			return yard(spec, terrain);
		}
		if (a.equals(FIELD)) {
			return field(spec, terrain);
		}
		if (a.equals(WELL)) {
			return well(spec, terrain);
		}
		if (a.equals(ROAD)) {
			return road(spec, terrain);
		}
		if (PlaceholderExcavations.handles(a)) {
			return PlaceholderExcavations.generate(spec, terrain);
		}
		throw new IllegalArgumentException("no placeholder for archetype " + a);
	}

	// ---------------------------------------------------------------- shared pieces

	/** Clears the volume above the floor and lays a floor with footings down to the natural ground. */
	private static void groundwork(PlaceholderBlueprint.Builder b, TerrainProbe t, Block floor, int clearHeight) {
		PlotSpec s = b.spec;
		int y0 = s.floorY();
		b.stage(StageKind.CLEAR);
		forFootprint(s, (x, z) -> {
			int g = t.groundY(x, z);
			for (int y = Math.max(y0, Math.min(g, y0 + clearHeight)) - 1; y >= y0; y--) {
				b.set(x, y, z, Blocks.AIR.defaultBlockState());
			}
			for (int y = y0; y < y0 + clearHeight; y++) {
				if (y >= g) {
					b.set(x, y, z, Blocks.AIR.defaultBlockState());
				}
			}
		});
		b.stage(StageKind.FOUNDATION);
		forFootprint(s, (x, z) -> {
			int g = Math.max(t.groundY(x, z), y0 - 12);
			for (int y = g; y < y0; y++) {
				b.set(x, y, z, floor.defaultBlockState());
			}
			if (g >= y0) {
				b.set(x, y0 - 1, z, floor.defaultBlockState());
			}
		});
	}

	private interface Column {
		void at(int x, int z);
	}

	private static void forFootprint(PlotSpec s, Column c) {
		int ox = s.origin().getX();
		int oz = s.origin().getZ();
		for (int dz = 0; dz < s.sizeZ(); dz++) {
			for (int dx = 0; dx < s.sizeX(); dx++) {
				c.at(ox + dx, oz + dz);
			}
		}
	}

	private static BlockState door(Block door, Direction facing, DoubleBlockHalf half) {
		BlockState st = door.defaultBlockState();
		if (st.hasProperty(DoorBlock.FACING)) {
			st = st.setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HALF, half);
		}
		return st;
	}

	// ---------------------------------------------------------------- archetypes

	/** A box on a one-block yard ring: walls with log corners, a door to the street, windows, a flat roof, a bed. */
	private static Blueprint house(PlotSpec s, TerrainProbe t, boolean storehouse) {
		Palette p = palette(s);
		PlaceholderBlueprint.Builder b = new PlaceholderBlueprint.Builder(s);
		int y0 = s.floorY();
		int wallH = 4;
		groundwork(b, t, p.floor, wallH + 2);
		int ox = s.origin().getX() + 1;
		int oz = s.origin().getZ() + 1;
		int ex = s.origin().getX() + s.sizeX() - 2;
		int ez = s.origin().getZ() + s.sizeZ() - 2;
		Direction f = s.facing();
		// the door: middle of the front wall
		int doorX;
		int doorZ;
		switch (f) {
			case NORTH -> {
				doorX = (ox + ex) / 2;
				doorZ = oz;
			}
			case SOUTH -> {
				doorX = (ox + ex) / 2;
				doorZ = ez;
			}
			case WEST -> {
				doorX = ox;
				doorZ = (oz + ez) / 2;
			}
			default -> {
				doorX = ex;
				doorZ = (oz + ez) / 2;
			}
		}
		b.stage(StageKind.FRAME);
		for (int y = y0; y < y0 + wallH; y++) {
			b.set(ox, y, oz, p.log.defaultBlockState());
			b.set(ex, y, oz, p.log.defaultBlockState());
			b.set(ox, y, ez, p.log.defaultBlockState());
			b.set(ex, y, ez, p.log.defaultBlockState());
		}
		b.stage(StageKind.WALLS);
		for (int y = y0; y < y0 + wallH; y++) {
			for (int x = ox; x <= ex; x++) {
				for (int z = oz; z <= ez; z++) {
					boolean edge = x == ox || x == ex || z == oz || z == ez;
					boolean corner = (x == ox || x == ex) && (z == oz || z == ez);
					if (!edge || corner) {
						continue;
					}
					boolean doorway = x == doorX && z == doorZ && y < y0 + 2;
					boolean window = y == y0 + 1 && !doorway && ((x == ox || x == ex) ? (z - oz) % 3 == 2 : (x - ox) % 3 == 2);
					if (!doorway && !window) {
						b.set(x, y, z, p.wall.defaultBlockState());
					}
				}
			}
		}
		b.stage(StageKind.ROOF);
		for (int x = ox; x <= ex; x++) {
			for (int z = oz; z <= ez; z++) {
				b.set(x, y0 + wallH, z, p.roof.defaultBlockState());
			}
		}
		b.stage(StageKind.OPENINGS);
		b.set(doorX, y0, doorZ, door(p.door, f, DoubleBlockHalf.LOWER));
		b.set(doorX, y0 + 1, doorZ, door(p.door, f, DoubleBlockHalf.UPPER));
		for (int x = ox; x <= ex; x++) {
			for (int z = oz; z <= ez; z++) {
				boolean edge = x == ox || x == ex || z == oz || z == ez;
				boolean corner = (x == ox || x == ex) && (z == oz || z == ez);
				if (!edge || corner || (x == doorX && z == doorZ)) {
					continue;
				}
				boolean window = (x == ox || x == ex) ? (z - oz) % 3 == 2 : (x - ox) % 3 == 2;
				if (window) {
					boolean alongX = z == oz || z == ez;
					b.set(x, y0 + 1, z, Blocks.GLASS_PANE.defaultBlockState().setValue(IronBarsBlock.EAST, alongX).setValue(IronBarsBlock.WEST, alongX)
						.setValue(IronBarsBlock.NORTH, !alongX).setValue(IronBarsBlock.SOUTH, !alongX));
				}
			}
		}
		b.stage(StageKind.INTERIOR);
		BlockPos inFront = new BlockPos(doorX, y0, doorZ).relative(f);
		BlockPos inside = new BlockPos(doorX, y0, doorZ).relative(f.getOpposite());
		b.poi(new Poi(PoiType.DOOR, inFront, f.getOpposite(), null));
		b.entrance(inFront);
		// the far corners from the door, inside the walls
		Direction back = f.getOpposite();
		Direction side = f.getClockWise();
		BlockPos backWall = new BlockPos(doorX, y0, doorZ).relative(back, Math.max(1, (f.getAxis() == Direction.Axis.Z ? ez - oz : ex - ox) - 1));
		if (storehouse) {
			for (int i = -1; i <= 1; i++) {
				BlockPos c = backWall.relative(side, i * 2);
				b.set(c.getX(), y0, c.getZ(), Blocks.BARREL.defaultBlockState());
				b.poi(new Poi(PoiType.STORAGE, c.relative(f), back, null));
			}
		} else {
			int beds = (ex - ox >= 6 && ez - oz >= 6) ? 2 : 1;
			for (int i = 0; i < beds; i++) {
				BlockPos head = backWall.relative(side, i == 0 ? -1 : 1);
				BlockPos foot = head.relative(f);
				BlockState bed = Blocks.BED.pick(DyeColor.RED).defaultBlockState().setValue(BedBlock.FACING, back);
				b.set(foot.getX(), y0, foot.getZ(), bed.setValue(BedBlock.PART, BedPart.FOOT));
				b.set(head.getX(), y0, head.getZ(), bed.setValue(BedBlock.PART, BedPart.HEAD));
				b.poi(new Poi(PoiType.BED, head, f, null));
				b.beds(1);
			}
			BlockPos table = inside.relative(side, 1);
			b.set(table.getX(), y0, table.getZ(), Blocks.CRAFTING_TABLE.defaultBlockState());
			b.poi(new Poi(PoiType.TABLE, table, f, null));
		}
		BlockPos torch = inside.relative(side.getOpposite(), 1);
		b.set(torch.getX(), y0, torch.getZ(), Blocks.TORCH.defaultBlockState());
		b.stage(StageKind.EXTERIOR);
		b.set(inFront.getX(), y0 - 1, inFront.getZ(), p.path.defaultBlockState());
		int[][] corners = {{s.origin().getX(), s.origin().getZ()}, {s.origin().getX() + s.sizeX() - 1, s.origin().getZ() + s.sizeZ() - 1}};
		for (int[] c : corners) {
			if (t.groundY(c[0], c[1]) <= y0 && !t.isWater(c[0], c[1])) {
				b.set(c[0], y0, c[1], Blocks.POPPY.defaultBlockState());
			}
		}
		b.colours(p.wallColor, p.roofColor, wallH);
		return b.build();
	}

	/** A fenced yard with workbenches for the builders and a barrel of tools. */
	private static Blueprint yard(PlotSpec s, TerrainProbe t) {
		Palette p = palette(s);
		PlaceholderBlueprint.Builder b = new PlaceholderBlueprint.Builder(s);
		int y0 = s.floorY();
		groundwork(b, t, Blocks.GRAVEL, 3);
		int ox = s.origin().getX();
		int oz = s.origin().getZ();
		int ex = ox + s.sizeX() - 1;
		int ez = oz + s.sizeZ() - 1;
		Direction f = s.facing();
		BlockPos gate = gateOf(s);
		b.stage(StageKind.EXTERIOR);
		Block fence = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(
			BuiltInRegistries.BLOCK.getKey(p.door).getPath().replace("_door", "_fence")));
		if (!(fence instanceof FenceBlock)) {
			fence = Blocks.OAK_FENCE;
		}
		for (int x = ox; x <= ex; x++) {
			for (int z = oz; z <= ez; z++) {
				boolean edge = x == ox || x == ex || z == oz || z == ez;
				if (!edge || (x == gate.getX() && z == gate.getZ())) {
					continue;
				}
				boolean n = isFence(s, gate, x, z - 1);
				boolean so = isFence(s, gate, x, z + 1);
				boolean w = isFence(s, gate, x - 1, z);
				boolean e = isFence(s, gate, x + 1, z);
				b.set(x, y0, z, fence.defaultBlockState().setValue(FenceBlock.NORTH, n).setValue(FenceBlock.SOUTH, so).setValue(FenceBlock.WEST, w)
					.setValue(FenceBlock.EAST, e));
			}
		}
		b.stage(StageKind.INTERIOR);
		Direction side = f.getClockWise();
		BlockPos centre = new BlockPos((ox + ex) / 2, y0, (oz + ez) / 2);
		for (int i = -1; i <= 1; i++) {
			BlockPos bench = centre.relative(side, i * 2).relative(f.getOpposite(), 1);
			b.set(bench.getX(), y0, bench.getZ(), Blocks.CRAFTING_TABLE.defaultBlockState());
			b.poi(new Poi(PoiType.JOB_SITE, bench.relative(f), f.getOpposite(), JobType.BUILDER));
		}
		BlockPos barrel = centre.relative(f.getOpposite(), 2).relative(side, 3);
		b.set(barrel.getX(), y0, barrel.getZ(), Blocks.BARREL.defaultBlockState());
		b.poi(new Poi(PoiType.STORAGE, barrel.relative(f), f.getOpposite(), null));
		BlockPos logs = centre.relative(f, 2).relative(side, -2);
		b.set(logs.getX(), y0, logs.getZ(), p.log.defaultBlockState().trySetValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS,
			side.getAxis()));
		b.set(logs.getX(), y0 + 1, logs.getZ(), p.log.defaultBlockState().trySetValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS,
			side.getAxis()));
		b.entrance(gate);
		b.poi(new Poi(PoiType.DOOR, gate, f.getOpposite(), null));
		b.colours(0x9A8A6A, 0x8A7A5A, 1);
		return b.build();
	}

	private static BlockPos gateOf(PlotSpec s) {
		int ox = s.origin().getX();
		int oz = s.origin().getZ();
		int ex = ox + s.sizeX() - 1;
		int ez = oz + s.sizeZ() - 1;
		return switch (s.facing()) {
			case NORTH -> new BlockPos((ox + ex) / 2, s.floorY(), oz);
			case SOUTH -> new BlockPos((ox + ex) / 2, s.floorY(), ez);
			case WEST -> new BlockPos(ox, s.floorY(), (oz + ez) / 2);
			default -> new BlockPos(ex, s.floorY(), (oz + ez) / 2);
		};
	}

	private static boolean isFence(PlotSpec s, BlockPos gate, int x, int z) {
		int ox = s.origin().getX();
		int oz = s.origin().getZ();
		int ex = ox + s.sizeX() - 1;
		int ez = oz + s.sizeZ() - 1;
		if (x < ox || x > ex || z < oz || z > ez) {
			return false;
		}
		boolean edge = x == ox || x == ex || z == oz || z == ez;
		return edge && !(x == gate.getX() && z == gate.getZ());
	}

	/** Rows of wheat on farmland, watered by a channel down the middle. */
	static Blueprint field(PlotSpec s, TerrainProbe t) {
		PlaceholderBlueprint.Builder b = new PlaceholderBlueprint.Builder(s);
		int y0 = s.floorY();
		int ox = s.origin().getX();
		int oz = s.origin().getZ();
		b.stage(StageKind.CLEAR);
		forFootprint(s, (x, z) -> {
			for (int y = y0; y < y0 + 3; y++) {
				b.set(x, y, z, Blocks.AIR.defaultBlockState());
			}
		});
		b.stage(StageKind.EARTHWORK);
		forFootprint(s, (x, z) -> {
			int g = Math.max(t.groundY(x, z), y0 - 8);
			for (int y = g; y < y0 - 1; y++) {
				b.set(x, y, z, Blocks.DIRT.defaultBlockState());
			}
		});
		boolean alongX = s.sizeX() >= s.sizeZ();
		int mid = alongX ? oz + s.sizeZ() / 2 : ox + s.sizeX() / 2;
		b.stage(StageKind.FOUNDATION);
		forFootprint(s, (x, z) -> {
			boolean channel = (alongX ? z : x) == mid;
			b.set(x, y0 - 1, z, channel ? Blocks.WATER.defaultBlockState() : Blocks.FARMLAND.defaultBlockState()
				.setValue(net.minecraft.world.level.block.FarmlandBlock.MOISTURE, 7));
		});
		b.stage(StageKind.EXTERIOR);
		RandomSource random = RandomSource.create(s.seed());
		forFootprint(s, (x, z) -> {
			if ((alongX ? z : x) != mid) {
				b.set(x, y0, z, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, random.nextInt(8)));
			}
		});
		BlockPos centre = new BlockPos(ox + s.sizeX() / 2, y0, oz + s.sizeZ() / 2);
		b.poi(new Poi(PoiType.JOB_SITE, centre.relative(alongX ? Direction.NORTH : Direction.WEST), alongX ? Direction.SOUTH : Direction.EAST,
			JobType.FARMER));
		b.poi(new Poi(PoiType.JOB_SITE, centre.relative(alongX ? Direction.SOUTH : Direction.EAST), alongX ? Direction.NORTH : Direction.WEST,
			JobType.FARMER));
		b.entrance(gateOf(s));
		b.colours(0xC8B850, 0xC8B850, 0);
		return b.build();
	}

	/** A stone well with a little roof: the heart of a new settlement. */
	private static Blueprint well(PlotSpec s, TerrainProbe t) {
		PlaceholderBlueprint.Builder b = new PlaceholderBlueprint.Builder(s);
		int y0 = s.floorY();
		groundwork(b, t, Blocks.COBBLESTONE, 4);
		int cx = s.origin().getX() + s.sizeX() / 2;
		int cz = s.origin().getZ() + s.sizeZ() / 2;
		b.stage(StageKind.WALLS);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) {
					for (int y = y0 - 4; y < y0; y++) {
						b.set(cx, y, cz, Blocks.WATER.defaultBlockState());
					}
				} else {
					b.set(cx + dx, y0, cz + dz, Blocks.STONE_BRICKS.defaultBlockState());
				}
			}
		}
		b.stage(StageKind.ROOF);
		for (int[] c : new int[][]{{-1, -1}, {1, 1}}) {
			b.set(cx + c[0], y0 + 1, cz + c[1], Blocks.OAK_FENCE.defaultBlockState());
			b.set(cx + c[0], y0 + 2, cz + c[1], Blocks.OAK_FENCE.defaultBlockState());
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				b.set(cx + dx, y0 + 3, cz + dz, Blocks.OAK_SLAB.defaultBlockState());
			}
		}
		b.stage(StageKind.EXTERIOR);
		b.set(cx, y0 + 2, cz, Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
		b.poi(new Poi(PoiType.MEETING_POINT, new BlockPos(cx, y0, cz).relative(s.facing(), 2), s.facing().getOpposite(), null));
		b.entrance(new BlockPos(cx, y0, cz).relative(s.facing(), 2));
		b.colours(0x8A8A8A, 0xA08050, 2);
		return b.build();
	}

	/**
	 * A graded street along a polyline ({@code points}: x,y,z,… at walking level; {@code width}; {@code kind}): cleared
	 * above, filled below, a path surface, and lamp posts along one side.
	 */
	private static Blueprint road(PlotSpec s, TerrainProbe t) {
		Palette p = palette(s);
		PlaceholderBlueprint.Builder b = new PlaceholderBlueprint.Builder(s);
		CompoundTag params = s.params();
		int[] pts = params.getIntArray("points").orElse(new int[0]);
		int width = Math.max(1, params.getIntOr("width", 3));
		List<int[]> cells = rasterise(pts, width);
		b.stage(StageKind.CLEAR);
		for (int[] c : cells) {
			for (int y = c[1]; y < c[1] + 3; y++) {
				b.set(c[0], y, c[2], Blocks.AIR.defaultBlockState());
			}
		}
		b.stage(StageKind.EARTHWORK);
		for (int[] c : cells) {
			int g = Math.max(t.groundY(c[0], c[2]), c[1] - 6);
			for (int y = g; y < c[1] - 1; y++) {
				b.set(c[0], y, c[2], Blocks.DIRT.defaultBlockState());
			}
		}
		b.stage(StageKind.FOUNDATION);
		for (int[] c : cells) {
			boolean wet = t.isWater(c[0], c[2]) && t.waterY(c[0], c[2]) >= c[1];
			b.set(c[0], c[1] - 1, c[2], wet ? p.planks.defaultBlockState() : p.path.defaultBlockState());
		}
		b.stage(StageKind.EXTERIOR);
		for (int i = 0; i + 5 < pts.length; i += 3) {
			int x0 = pts[i];
			int z0 = pts[i + 2];
			int x1 = pts[i + 3];
			int z1 = pts[i + 5];
			int len = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
			for (int d = 8; d < len; d += 16) {
				double f = d / (double) len;
				int x = (int) Math.round(x0 + (x1 - x0) * f);
				int z = (int) Math.round(z0 + (z1 - z0) * f);
				int y = (int) Math.round(pts[i + 1] + (pts[i + 4] - pts[i + 1]) * f);
				// the lamp stands on the road's edge
				int ex = x + (Math.abs(z1 - z0) > Math.abs(x1 - x0) ? width / 2 : 0);
				int ez = z + (Math.abs(z1 - z0) > Math.abs(x1 - x0) ? 0 : width / 2);
				b.set(ex, y, ez, Blocks.OAK_FENCE.defaultBlockState());
				b.set(ex, y + 1, ez, Blocks.OAK_FENCE.defaultBlockState());
				b.set(ex, y + 2, ez, Blocks.LANTERN.defaultBlockState());
			}
		}
		b.colours(0x9A8A6A, 0x9A8A6A, 0);
		return b.build();
	}

	// ---------------------------------------------------------------- the dev catalogue's stand-ins (DevCatalogue)

	/**
	 * A stand-in for any building of the catalogue: the placeholder house grown to the plot (storeys from its height
	 * range, joined by a ladder), with {@code beds} beds spread over the floors, a workstation and job site for each of
	 * {@code jobs} (several for a big workplace), barrels for a store, a counter for a shop.
	 */
	public static Blueprint building(PlotSpec s, TerrainProbe t, ArchetypeInfo info, int beds, java.util.Map<JobType, Integer> jobs, int height) {
		Palette p = palette(s);
		boolean stone = info.category() == ArchetypeCategory.CIVIC || info.category() == ArchetypeCategory.LANDMARK
			|| info.category() == ArchetypeCategory.DEFENSE;
		Block wall = stone ? Blocks.STONE_BRICKS : p.wall;
		Block log = stone ? Blocks.POLISHED_ANDESITE : p.log;
		PlaceholderBlueprint.Builder b = new PlaceholderBlueprint.Builder(s);
		int y0 = s.floorY();
		int storeys = Math.max(1, Math.min(4, Math.min((height - 1) / 4, (s.maxHeight() - 1) / 4)));
		int wallH = 4 * storeys;
		groundwork(b, t, p.floor, wallH + 2);
		// a yard ring round anything roomy; small houses (huts, rowhouses) fill their plot
		boolean ring = s.sizeX() >= 9 && s.sizeZ() >= 9;
		int ox = s.origin().getX() + (ring ? 1 : 0);
		int oz = s.origin().getZ() + (ring ? 1 : 0);
		int ex = s.origin().getX() + s.sizeX() - (ring ? 2 : 1);
		int ez = s.origin().getZ() + s.sizeZ() - (ring ? 2 : 1);
		Direction f = s.facing();
		int doorX = f.getAxis() == Direction.Axis.Z ? (ox + ex) / 2 : f == Direction.WEST ? ox : ex;
		int doorZ = f.getAxis() == Direction.Axis.X ? (oz + ez) / 2 : f == Direction.NORTH ? oz : ez;
		BlockPos door = new BlockPos(doorX, y0, doorZ);
		Direction back = f.getOpposite();
		Direction side = f.getClockWise();
		BlockPos inside = door.relative(back);
		boolean floors = storeys > 1 && ex - ox >= 4 && ez - oz >= 4;
		BlockPos ladder = inside.relative(side, 2);
		b.stage(StageKind.FRAME);
		for (int y = y0; y < y0 + wallH; y++) {
			b.set(ox, y, oz, log.defaultBlockState());
			b.set(ex, y, oz, log.defaultBlockState());
			b.set(ox, y, ez, log.defaultBlockState());
			b.set(ex, y, ez, log.defaultBlockState());
		}
		b.stage(StageKind.WALLS);
		for (int y = y0; y < y0 + wallH; y++) {
			boolean windowRow = (y - y0) % 4 == 1;
			for (int x = ox; x <= ex; x++) {
				for (int z = oz; z <= ez; z++) {
					boolean edge = x == ox || x == ex || z == oz || z == ez;
					boolean corner = (x == ox || x == ex) && (z == oz || z == ez);
					if (!edge || corner) {
						continue;
					}
					boolean doorway = x == doorX && z == doorZ && y < y0 + 2;
					boolean window = windowRow && !doorway && ((x == ox || x == ex) ? (z - oz) % 3 == 2 : (x - ox) % 3 == 2);
					if (!doorway && !window) {
						b.set(x, y, z, wall.defaultBlockState());
					}
				}
			}
		}
		if (floors) {
			for (int k = 1; k < storeys; k++) {
				int fy = y0 + 4 * k - 1;
				for (int x = ox + 1; x < ex; x++) {
					for (int z = oz + 1; z < ez; z++) {
						if (x != ladder.getX() || z != ladder.getZ()) {
							b.set(x, fy, z, p.planks.defaultBlockState());
						}
					}
				}
			}
		}
		b.stage(StageKind.ROOF);
		for (int x = ox; x <= ex; x++) {
			for (int z = oz; z <= ez; z++) {
				b.set(x, y0 + wallH, z, p.roof.defaultBlockState());
			}
		}
		b.stage(StageKind.OPENINGS);
		b.set(doorX, y0, doorZ, door(p.door, f, DoubleBlockHalf.LOWER));
		b.set(doorX, y0 + 1, doorZ, door(p.door, f, DoubleBlockHalf.UPPER));
		for (int y = y0 + 1; y < y0 + wallH; y += 4) {
			for (int x = ox; x <= ex; x++) {
				for (int z = oz; z <= ez; z++) {
					boolean edge = x == ox || x == ex || z == oz || z == ez;
					boolean corner = (x == ox || x == ex) && (z == oz || z == ez);
					if (!edge || corner || (x == doorX && z == doorZ)) {
						continue;
					}
					if ((x == ox || x == ex) ? (z - oz) % 3 == 2 : (x - ox) % 3 == 2) {
						boolean alongX = z == oz || z == ez;
						b.set(x, y, z, Blocks.GLASS_PANE.defaultBlockState().setValue(IronBarsBlock.EAST, alongX).setValue(IronBarsBlock.WEST, alongX)
							.setValue(IronBarsBlock.NORTH, !alongX).setValue(IronBarsBlock.SOUTH, !alongX));
					}
				}
			}
		}
		b.stage(StageKind.INTERIOR);
		BlockPos inFront = door.relative(f);
		b.poi(new Poi(PoiType.DOOR, inFront, back, null));
		b.entrance(inFront);
		if (floors) {
			for (int y = y0; y < y0 + wallH - 1; y++) {
				b.set(ladder.getX(), y, ladder.getZ(), Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, back));
			}
		}
		// the free cells of each floor, from the back wall forward, away from the door and the ladder
		int depth = Math.abs(f.getAxis() == Direction.Axis.Z ? ez - oz : ex - ox) - 1;
		int width = Math.abs(f.getAxis() == Direction.Axis.Z ? ex - ox : ez - oz) - 1;
		BlockPos backWall = door.relative(back, Math.max(1, depth));
		int placedBeds = 0;
		for (int k = 0; k < (floors ? storeys : 1) && placedBeds < beds; k++) {
			int fy = y0 + 4 * k;
			for (int across = -(width / 2) + 1; across <= width / 2 - 1 && placedBeds < beds; across += 2) {
				BlockPos head = backWall.relative(side, across).atY(fy);
				BlockPos foot = head.relative(f);
				if (depth < 3 || foot.equals(inside.atY(fy)) || head.getX() == ladder.getX() && head.getZ() == ladder.getZ()
					|| foot.getX() == ladder.getX() && foot.getZ() == ladder.getZ()) {
					continue;
				}
				BlockState bed = Blocks.BED.pick(DyeColor.values()[(int) Math.floorMod(placedBeds * 5L + s.seed(), 16L)]).defaultBlockState()
					.setValue(BedBlock.FACING, back);
				b.set(foot.getX(), fy, foot.getZ(), bed.setValue(BedBlock.PART, BedPart.FOOT));
				b.set(head.getX(), fy, head.getZ(), bed.setValue(BedBlock.PART, BedPart.HEAD));
				b.poi(new Poi(PoiType.BED, head, f, null));
				b.beds(1);
				placedBeds++;
			}
		}
		if (placedBeds == 0 && beds > 0 && width >= 1 && depth >= 2) {
			// a cramped hut: one bed along the back wall, crosswise
			BlockPos head = backWall.relative(side, Math.max(0, width / 2));
			BlockPos foot = head.relative(side.getOpposite());
			if (!foot.equals(inside) && !head.equals(inside)) {
				BlockState bed = Blocks.BED.pick(DyeColor.BROWN).defaultBlockState().setValue(BedBlock.FACING, side);
				b.set(foot.getX(), y0, foot.getZ(), bed.setValue(BedBlock.PART, BedPart.FOOT));
				b.set(head.getX(), y0, head.getZ(), bed.setValue(BedBlock.PART, BedPart.HEAD));
				b.poi(new Poi(PoiType.BED, head, back, null));
				b.beds(1);
				placedBeds++;
			}
		}
		// workstations along the side walls of the ground floor, the worker standing in front of each
		int slot = 0;
		for (var e : jobs.entrySet()) {
			for (int i = 0; i < e.getValue(); i++, slot++) {
				int along = 1 + slot / 2;
				if (along >= depth) {
					break;
				}
				Direction toWall = slot % 2 == 0 ? side : side.getOpposite();
				BlockPos bench = inside.relative(back, along - 1).relative(toWall, Math.max(1, width / 2));
				BlockPos stand = bench.relative(toWall.getOpposite());
				b.set(bench.getX(), y0, bench.getZ(), workstation(e.getKey()).defaultBlockState());
				b.poi(new Poi(PoiType.JOB_SITE, stand, toWall, e.getKey()));
				if (e.getKey() == JobType.MERCHANT || e.getKey() == JobType.INNKEEPER || e.getKey() == JobType.MINTER) {
					b.poi(new Poi(PoiType.COUNTER, stand, f, e.getKey()));
				}
			}
		}
		if (info.category() == ArchetypeCategory.STORAGE) {
			for (int i = -1; i <= 1; i++) {
				BlockPos c = backWall.relative(side, i * 2);
				b.set(c.getX(), y0, c.getZ(), Blocks.BARREL.defaultBlockState());
				b.poi(new Poi(PoiType.STORAGE, c.relative(f), back, null));
			}
		} else if (placedBeds > 0) {
			BlockPos table = inside.relative(side.getOpposite(), 1);
			b.set(table.getX(), y0, table.getZ(), Blocks.CRAFTING_TABLE.defaultBlockState());
			b.poi(new Poi(PoiType.TABLE, table, f, null));
		}
		BlockPos torch = inside.relative(side.getOpposite(), 2);
		b.set(torch.getX(), y0, torch.getZ(), Blocks.TORCH.defaultBlockState());
		b.stage(StageKind.EXTERIOR);
		b.set(inFront.getX(), y0 - 1, inFront.getZ(), p.path.defaultBlockState());
		b.colours(stone ? 0x8A8A8A : p.wallColor, p.roofColor, wallH);
		return b.build();
	}

	private static Block workstation(JobType job) {
		return switch (job) {
			case SMITH -> Blocks.ANVIL;
			case BAKER -> Blocks.SMOKER;
			case BREWER -> Blocks.BREWING_STAND;
			case MASON -> Blocks.STONECUTTER;
			case TAILOR -> Blocks.LOOM;
			case SCHOLAR -> Blocks.LECTERN;
			case PRIEST -> Blocks.LECTERN;
			case FISHER -> Blocks.BARREL;
			case HUNTER -> Blocks.SMITHING_TABLE;
			case FORESTER, CARPENTER -> Blocks.CRAFTING_TABLE;
			case MERCHANT, INNKEEPER, MINTER -> Blocks.BARREL;
			case GUARD -> Blocks.GRINDSTONE;
			case HERDER -> Blocks.COMPOSTER;
			case FARMER -> Blocks.COMPOSTER;
			case QUARRIER -> Blocks.STONECUTTER;
			default -> Blocks.CRAFTING_TABLE;
		};
	}

	/**
	 * A stand-in square: paved, with lamp posts at the corners and the town's notice board (the quest module's
	 * {@code hearth:notice_board}, by id; an oak sign on a post without it) a little off the middle. What the plaza
	 * {@code keep}s (the well) stands as it is.
	 */
	public static Blueprint plaza(PlotSpec s, TerrainProbe t) {
		Palette p = palette(s);
		PlaceholderBlueprint.Builder b = new PlaceholderBlueprint.Builder(s);
		int y0 = s.floorY();
		int[] keep = s.params().getIntArray("keep").orElse(new int[0]);
		java.util.function.BiPredicate<Integer, Integer> inKeep = (x, z) -> {
			for (int i = 0; i + 3 < keep.length; i += 4) {
				if (x >= keep[i] && x <= keep[i + 2] && z >= keep[i + 1] && z <= keep[i + 3]) {
					return true;
				}
			}
			return false;
		};
		b.stage(StageKind.CLEAR);
		forFootprint(s, (x, z) -> {
			if (!inKeep.test(x, z)) {
				for (int y = y0; y < y0 + 4; y++) {
					b.set(x, y, z, Blocks.AIR.defaultBlockState());
				}
			}
		});
		b.stage(StageKind.FOUNDATION);
		forFootprint(s, (x, z) -> {
			if (!inKeep.test(x, z)) {
				int g = Math.max(t.groundY(x, z), y0 - 6);
				for (int y = g; y < y0 - 1; y++) {
					b.set(x, y, z, p.floor.defaultBlockState());
				}
				b.set(x, y0 - 1, z, ((x + z) & 3) == 0 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
			}
		});
		b.stage(StageKind.EXTERIOR);
		int ox = s.origin().getX();
		int oz = s.origin().getZ();
		int ex = ox + s.sizeX() - 1;
		int ez = oz + s.sizeZ() - 1;
		for (int[] c : new int[][]{{ox, oz}, {ex, oz}, {ox, ez}, {ex, ez}}) {
			if (!inKeep.test(c[0], c[1])) {
				b.set(c[0], y0, c[1], Blocks.OAK_FENCE.defaultBlockState());
				b.set(c[0], y0 + 1, c[1], Blocks.OAK_FENCE.defaultBlockState());
				b.set(c[0], y0 + 2, c[1], Blocks.LANTERN.defaultBlockState());
			}
		}
		// the board: two blocks in from the front edge, facing out to the street, beside the middle
		Direction f = s.facing();
		BlockPos mid = new BlockPos((ox + ex) / 2, y0, (oz + ez) / 2);
		BlockPos boardAt = null;
		for (int k = 0; k < 6 && boardAt == null; k++) {
			BlockPos c = mid.relative(f, Math.max(0, (f.getAxis() == Direction.Axis.Z ? s.sizeZ() : s.sizeX()) / 2 - 2 - k)).relative(f.getClockWise(), 2);
			if (b.inside(c.getX(), c.getZ()) && !inKeep.test(c.getX(), c.getZ()) && !inKeep.test(c.relative(f).getX(), c.relative(f).getZ())) {
				boardAt = c;
			}
		}
		if (boardAt != null) {
			Block board = BuiltInRegistries.BLOCK.getValue(Hearth.id("notice_board"));
			if (board != Blocks.AIR && board.defaultBlockState().hasProperty(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)) {
				b.set(boardAt.getX(), y0, boardAt.getZ(), board.defaultBlockState()
					.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, f));
			} else {
				b.set(boardAt.getX(), y0, boardAt.getZ(), Blocks.OAK_FENCE.defaultBlockState());
				b.set(boardAt.getX(), y0 + 1, boardAt.getZ(), Blocks.OAK_SIGN.defaultBlockState());
			}
			b.poi(new Poi(PoiType.NOTICE_BOARD, boardAt, f, null));
		}
		b.poi(new Poi(PoiType.MEETING_POINT, mid, f, null));
		b.entrance(mid.relative(f, Math.max(1, (f.getAxis() == Direction.Axis.Z ? s.sizeZ() : s.sizeX()) / 2)));
		b.colours(0x8A8A8A, 0x8A8A8A, 0);
		return b.build();
	}

	/**
	 * A stand-in street on exactly the corridor the planner reserved (PLANNING.md §7: the polyline's columns within
	 * {@code (width + 0.5) / 2}): cleared above for walking (trees and boulders out of the way), filled below to the
	 * ground, a surface by kind (planks over water), and lamp posts along streets and lanes.
	 */
	public static Blueprint street(PlotSpec s, TerrainProbe t) {
		Palette p = palette(s);
		PlaceholderBlueprint.Builder b = new PlaceholderBlueprint.Builder(s);
		CompoundTag params = s.params();
		int y0 = s.floorY();
		int[] pts = params.getIntArray("points").orElse(new int[]{s.origin().getX(), y0, s.origin().getZ(), s.origin().getX(), y0, s.origin().getZ()});
		int width = Math.max(1, params.getIntOr("width", 3));
		String kind = params.getStringOr("kind", "lane");
		dev.voidmc.hearth.plan.road.Polyline.Dense d = new dev.voidmc.hearth.plan.road.Polyline(pts).dense();
		List<int[]> cells = new java.util.ArrayList<>();
		dev.voidmc.hearth.plan.road.Polyline.corridor(pts, width, (x, z) -> cells.add(new int[]{x, d.ys()[d.nearest(x, z)], z}));
		b.stage(StageKind.CLEAR);
		for (int[] c : cells) {
			for (int y = c[1]; y < c[1] + 4; y++) {
				b.set(c[0], y, c[2], Blocks.AIR.defaultBlockState());
			}
		}
		b.stage(StageKind.EARTHWORK);
		for (int[] c : cells) {
			int g = Math.max(t.groundY(c[0], c[2]), c[1] - 6);
			for (int y = g; y < c[1] - 1; y++) {
				b.set(c[0], y, c[2], Blocks.DIRT.defaultBlockState());
			}
		}
		b.stage(StageKind.FOUNDATION);
		// earth for most ways (a young camp cannot spare stone for paving); dressed stone only in a dense core's alleys
		Block surface = switch (kind) {
			case "highway" -> Blocks.COARSE_DIRT;
			case "alley" -> Blocks.COBBLESTONE;
			default -> p.path;
		};
		for (int[] c : cells) {
			boolean wet = t.isWater(c[0], c[2]) && t.waterY(c[0], c[2]) >= c[1];
			b.set(c[0], c[1] - 1, c[2], (wet ? p.planks : surface).defaultBlockState());
		}
		if (!kind.equals("path")) {
			b.stage(StageKind.EXTERIOR);
			int[] xs = d.xs();
			int[] zs = d.zs();
			int[] ys = d.ys();
			for (int i = 8; i < xs.length; i += 16) {
				int dx = xs[Math.min(xs.length - 1, i + 1)] - xs[Math.max(0, i - 1)];
				int dz = zs[Math.min(zs.length - 1, i + 1)] - zs[Math.max(0, i - 1)];
				// the lamp at the corridor's edge, to the right of the way
				int ox = Math.abs(dz) >= Math.abs(dx) ? (width / 2 + 1) * Integer.signum(dz == 0 ? 1 : -dz) : 0;
				int oz = Math.abs(dx) > Math.abs(dz) ? (width / 2 + 1) * Integer.signum(dx) : 0;
				int x = xs[i] + ox;
				int z = zs[i] + oz;
				int y = ys[i];
				if (t.isWater(x, z) || Math.abs(t.groundY(x, z) - y) > 1) {
					continue;
				}
				b.set(x, y, z, Blocks.OAK_FENCE.defaultBlockState());
				b.set(x, y + 1, z, Blocks.OAK_FENCE.defaultBlockState());
				b.set(x, y + 2, z, Blocks.LANTERN.defaultBlockState());
			}
		}
		b.colours(0x9A8A6A, 0x9A8A6A, 0);
		return b.build();
	}

	/** The road's cells, each {x, y, z} with y the walking level, deduplicated, in order along the line. */
	static List<int[]> rasterise(int[] pts, int width) {
		Map<Long, int[]> cells = new LinkedHashMap<>();
		int half = width / 2;
		for (int i = 0; i + 5 < pts.length; i += 3) {
			int x0 = pts[i];
			int y0 = pts[i + 1];
			int z0 = pts[i + 2];
			int x1 = pts[i + 3];
			int y1 = pts[i + 4];
			int z1 = pts[i + 5];
			int len = Math.max(1, Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0)));
			boolean alongZ = Math.abs(z1 - z0) > Math.abs(x1 - x0);
			for (int d = 0; d <= len; d++) {
				double f = d / (double) len;
				int x = (int) Math.round(x0 + (x1 - x0) * f);
				int z = (int) Math.round(z0 + (z1 - z0) * f);
				int y = (int) Math.round(y0 + (y1 - y0) * f);
				for (int w = -half; w <= width - 1 - half; w++) {
					int cx = alongZ ? x + w : x;
					int cz = alongZ ? z : z + w;
					cells.putIfAbsent((long) cx << 32 ^ cz & 0xFFFF_FFFFL, new int[]{cx, y, cz});
				}
			}
		}
		return List.copyOf(cells.values());
	}
}
