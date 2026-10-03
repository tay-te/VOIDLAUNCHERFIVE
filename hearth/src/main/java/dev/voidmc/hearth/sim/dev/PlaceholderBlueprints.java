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
	private static Blueprint field(PlotSpec s, TerrainProbe t) {
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
