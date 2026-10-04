package dev.voidmc.hearth.sim.dev;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.api.TerrainProbe;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Stand-in excavations (LIVING_WORLD.md §8.5) until the build module's architects land the real ones: blueprints whose
 * steps are cuts, many stages deep, so the sim's extraction loop (labour by the trade, yield from the cuts, the site
 * debited) can be grown end to end and tested. Real excavations will have benches, derricks, rails, props, spoil heaps.
 *
 * <ul>
 *   <li>{@code extract/quarry_pit}, {@code clay_pit}, {@code sand_pit}: one stage per bench, each a layer of the footprint
 *   below the natural ground, stepping in as it goes down (param {@code depth}, default 8), a ladder down one side;</li>
 *   <li>{@code extract/mine}: an adit 3 wide and 3 high into the hill from the plot's front at floor level, one stage per
 *   6 blocks (param {@code length}, default the plot's depth), propped every 4 blocks and lit every 8;</li>
 *   <li>{@code extract/shaft}: a 3 × 3 shaft from the floor down (param {@code depth}, default 16), with a ladder;</li>
 *   <li>{@code extract/plantation}: rows of trees felled (air) and saplings planted, one stage per row.</li>
 * </ul>
 */
public final class PlaceholderExcavations {
	public static final Identifier QUARRY_PIT = Hearth.id("extract/quarry_pit");
	public static final Identifier MINE = Hearth.id("extract/mine");
	public static final Identifier SHAFT = Hearth.id("extract/shaft");
	public static final Identifier CLAY_PIT = Hearth.id("extract/clay_pit");
	public static final Identifier SAND_PIT = Hearth.id("extract/sand_pit");
	public static final Identifier PLANTATION = Hearth.id("extract/plantation");

	private PlaceholderExcavations() {
	}

	public static boolean handles(Identifier id) {
		return id.getNamespace().equals("hearth") && id.getPath().startsWith("extract/");
	}

	/** The excavations' infos (for stand-in catalogues). */
	public static List<ArchetypeInfo> infos() {
		List<ArchetypeInfo> out = new ArrayList<>();
		out.add(info(QUARRY_PIT, JobType.QUARRIER, 9, 21, 9, 21));
		out.add(info(MINE, JobType.MINER, 5, 9, 12, 48));
		out.add(info(SHAFT, JobType.MINER, 5, 7, 5, 7));
		out.add(info(CLAY_PIT, JobType.CLAY_DIGGER, 7, 15, 7, 15));
		out.add(info(SAND_PIT, JobType.CLAY_DIGGER, 7, 15, 7, 15));
		out.add(info(PLANTATION, JobType.FORESTER, 9, 25, 9, 25));
		return out;
	}

	private static ArchetypeInfo info(Identifier id, JobType job, int minF, int maxF, int minD, int maxD) {
		return new ArchetypeInfo(id, ArchetypeCategory.EXTRACTION, Set.of(job), 0, minF, maxF, minD, maxD, 4, 8, SettlementTier.HAMLET, Set.of(),
			Set.of(ArchetypeInfo.SiteFlag.OUTSKIRTS), List.of(), 0, 1.0);
	}

	public static Blueprint generate(PlotSpec s, TerrainProbe t) {
		String path = s.archetype().getPath();
		return switch (path) {
			case "extract/mine" -> mine(s, t);
			case "extract/shaft" -> shaft(s, t);
			case "extract/plantation" -> plantation(s, t);
			case "extract/clay_pit" -> pit(s, t, JobType.CLAY_DIGGER);
			case "extract/sand_pit" -> pit(s, t, JobType.CLAY_DIGGER);
			default -> pit(s, t, JobType.QUARRIER);
		};
	}

	private static final BlockState AIR = Blocks.AIR.defaultBlockState();

	/** Benches down into the ground, each stage a layer, stepping in a block every second layer. */
	private static Blueprint pit(PlotSpec s, TerrainProbe t, JobType job) {
		PlaceholderBlueprint.Builder b = new PlaceholderBlueprint.Builder(s);
		CompoundTag params = s.params();
		int depth = Math.max(2, params.getIntOr("depth", 8));
		int ox = s.origin().getX();
		int oz = s.origin().getZ();
		int ex = ox + s.sizeX() - 1;
		int ez = oz + s.sizeZ() - 1;
		for (int level = 0; level < depth; level++) {
			int inset = level / 2;
			if (ox + inset > ex - inset || oz + inset > ez - inset) {
				break;
			}
			b.stage(StageKind.EARTHWORK);
			for (int x = ox + inset; x <= ex - inset; x++) {
				for (int z = oz + inset; z <= ez - inset; z++) {
					int g = t.groundY(x, z);
					b.set(x, g - 1 - level, z, AIR);
				}
			}
		}
		b.stage(StageKind.EXTERIOR);
		// a ladder down the front wall and a lantern on a post at the rim
		Direction f = s.facing();
		int cx = ox + s.sizeX() / 2;
		int cz = oz + s.sizeZ() / 2;
		BlockPos front = edge(s, f);
		for (int y = t.groundY(front.getX(), front.getZ()) - depth; y < t.groundY(front.getX(), front.getZ()); y++) {
			b.set(front.getX(), y, front.getZ(), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, f.getOpposite()));
		}
		int gy = t.groundY(cx, cz);
		b.set(ox, t.groundY(ox, oz), oz, Blocks.OAK_FENCE.defaultBlockState());
		b.set(ox, t.groundY(ox, oz) + 1, oz, Blocks.LANTERN.defaultBlockState());
		b.poi(new Poi(PoiType.JOB_SITE, front.relative(f), f.getOpposite(), job));
		b.poi(new Poi(PoiType.JOB_SITE, front.relative(f).relative(f.getClockWise()), f.getOpposite(), job));
		b.poi(new Poi(PoiType.WORK_FACE, new BlockPos(cx, gy - 1, cz), f, job));
		b.entrance(front.relative(f));
		b.colours(0x8A8A8A, 0x8A8A8A, 0);
		return b.build();
	}

	/** An adit from the front edge straight into the hill, a stage per six blocks, propped and lit. */
	private static Blueprint mine(PlotSpec s, TerrainProbe t) {
		PlaceholderBlueprint.Builder b = new PlaceholderBlueprint.Builder(s);
		Direction in = s.facing().getOpposite();
		Direction side = in.getClockWise();
		int length = Math.max(6, Math.min(s.depth(), s.params().getIntOr("length", s.depth())));
		BlockPos mouth = edge(s, s.facing());
		int y0 = s.floorY();
		for (int seg = 0; seg * 6 < length; seg++) {
			b.stage(StageKind.EARTHWORK);
			for (int d = seg * 6; d < Math.min(length, seg * 6 + 6); d++) {
				BlockPos c = mouth.relative(in, d);
				for (int w = -1; w <= 1; w++) {
					for (int h = 0; h < 3; h++) {
						BlockPos p = c.relative(side, w).above(h);
						b.set(p.getX(), y0 + h, p.getZ(), AIR);
					}
				}
			}
			b.stage(StageKind.FRAME);
			for (int d = seg * 6; d < Math.min(length, seg * 6 + 6); d++) {
				BlockPos c = mouth.relative(in, d);
				b.set(c.getX(), y0, c.getZ(), Blocks.RAIL.defaultBlockState());
				if (d % 4 == 2) {
					for (int w : new int[] {-1, 1}) {
						BlockPos p = c.relative(side, w);
						b.set(p.getX(), y0, p.getZ(), Blocks.OAK_FENCE.defaultBlockState());
						b.set(p.getX(), y0 + 1, p.getZ(), Blocks.OAK_FENCE.defaultBlockState());
						b.set(p.getX(), y0 + 2, p.getZ(), Blocks.OAK_PLANKS.defaultBlockState());
					}
					b.set(c.getX(), y0 + 2, c.getZ(), Blocks.OAK_PLANKS.defaultBlockState());
					if (d % 8 == 2) {
						BlockPos l = c.relative(side, 1);
						b.set(l.getX(), y0 + 1, l.getZ(), Blocks.LANTERN.defaultBlockState());
					}
				}
			}
		}
		b.poi(new Poi(PoiType.JOB_SITE, mouth.relative(s.facing()), in, JobType.MINER));
		b.poi(new Poi(PoiType.JOB_SITE, mouth.relative(s.facing()).relative(side), in, JobType.MINER));
		b.poi(new Poi(PoiType.WORK_FACE, mouth.relative(in, length - 1).atY(y0), in, JobType.MINER));
		b.entrance(mouth.relative(s.facing()).atY(y0));
		b.colours(0x6A5A40, 0x6A5A40, 0);
		return b.build();
	}

	/** A 3 × 3 shaft from the floor down, a stage per four blocks, with a ladder. */
	private static Blueprint shaft(PlotSpec s, TerrainProbe t) {
		PlaceholderBlueprint.Builder b = new PlaceholderBlueprint.Builder(s);
		int depth = Math.max(4, s.params().getIntOr("depth", 16));
		int cx = s.origin().getX() + s.sizeX() / 2;
		int cz = s.origin().getZ() + s.sizeZ() / 2;
		int top = s.floorY();
		for (int seg = 0; seg * 4 < depth; seg++) {
			b.stage(StageKind.EARTHWORK);
			for (int y = top - 1 - seg * 4; y > top - 1 - Math.min(depth, seg * 4 + 4); y--) {
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						b.set(cx + dx, y, cz + dz, AIR);
					}
				}
			}
		}
		b.stage(StageKind.FRAME);
		Direction f = s.facing();
		BlockPos wall = new BlockPos(cx, top, cz).relative(f.getOpposite(), 1);
		for (int y = top - depth; y < top; y++) {
			b.set(wall.getX(), y, wall.getZ(), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, f));
		}
		b.set(cx + 1, top, cz + 1, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
		b.poi(new Poi(PoiType.JOB_SITE, new BlockPos(cx, top, cz).relative(f, 2), f.getOpposite(), JobType.MINER));
		b.poi(new Poi(PoiType.WORK_FACE, new BlockPos(cx, top - depth, cz), f, JobType.MINER));
		b.entrance(new BlockPos(cx, top, cz).relative(f, 2));
		return b.build();
	}

	/** A woodlot: each stage fells a row of trees (the air where trunks stood) and plants saplings in their place. */
	private static Blueprint plantation(PlotSpec s, TerrainProbe t) {
		PlaceholderBlueprint.Builder b = new PlaceholderBlueprint.Builder(s);
		int ox = s.origin().getX();
		int oz = s.origin().getZ();
		for (int z = oz + 1; z < oz + s.sizeZ() - 1; z += 4) {
			b.stage(StageKind.CLEAR);
			for (int x = ox + 1; x < ox + s.sizeX() - 1; x += 4) {
				int g = t.groundY(x, z);
				for (int y = g; y < g + 6; y++) {
					b.set(x, y, z, AIR);
				}
			}
			b.stage(StageKind.EXTERIOR);
			for (int x = ox + 1; x < ox + s.sizeX() - 1; x += 4) {
				b.set(x, t.groundY(x, z), z, Blocks.OAK_SAPLING.defaultBlockState());
			}
		}
		Direction f = s.facing();
		BlockPos front = edge(s, f);
		b.poi(new Poi(PoiType.JOB_SITE, front.relative(f), f.getOpposite(), JobType.FORESTER));
		b.poi(new Poi(PoiType.WORK_FACE, new BlockPos(ox + s.sizeX() / 2, t.groundY(ox + s.sizeX() / 2, oz + s.sizeZ() / 2), oz + s.sizeZ() / 2), f,
			JobType.FORESTER));
		b.entrance(front.relative(f));
		b.colours(0x3A6A2A, 0x3A6A2A, 0);
		return b.build();
	}

	/** The middle of the footprint's edge on the {@code side} side, inside the footprint. */
	private static BlockPos edge(PlotSpec s, Direction side) {
		int ox = s.origin().getX();
		int oz = s.origin().getZ();
		int cx = ox + s.sizeX() / 2;
		int cz = oz + s.sizeZ() / 2;
		return switch (side) {
			case NORTH -> new BlockPos(cx, s.floorY(), oz);
			case SOUTH -> new BlockPos(cx, s.floorY(), oz + s.sizeZ() - 1);
			case WEST -> new BlockPos(ox, s.floorY(), cz);
			default -> new BlockPos(ox + s.sizeX() - 1, s.floorY(), cz);
		};
	}
}
