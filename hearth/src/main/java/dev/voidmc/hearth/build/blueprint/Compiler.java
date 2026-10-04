package dev.voidmc.hearth.build.blueprint;

import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Silhouette;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.terrain.Site;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.HangingRootsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * Turns a finished {@link Draft} into a {@link BlueprintImpl}: resolves shapes, adds what can be read off the blocks
 * (beds), groups placements by stage, orders each stage the way a person would build it, maps everything to world
 * coordinates and packs it with a chunk index, a materials bill and a silhouette.
 *
 * <p>Build order within a stage:
 * <ol>
 *   <li>bottom-up by the height of what holds a block up (a hanging lantern goes with the ceiling it hangs from, after
 *   it and top-down along its chain);</li>
 *   <li>at one height, structural blocks first, then blocks fixed to a side (wall torches, trapdoors, ladders), then
 *   blocks resting on top (carpets, plants, standing lanterns);</li>
 *   <li>then outward from the main entrance;</li>
 *   <li>ties by position, so indexing is stable.</li>
 * </ol>
 * Clearing goes top-down (trees are felled from the crown); earthwork digs top-down first, then fills bottom-up.
 */
public final class Compiler {
	private Compiler() {
	}

	private record Step(int x, int y, int z, BlockState state, @Nullable CompoundTag tag, StageKind stage, double support, int rank, int dist,
		int sequence) {
	}

	public static BlueprintImpl compile(Draft d) {
		Site site = d.site();
		LocalFrame frame = d.frame();
		// light what the archetype left dark (docs/LIVING_WORLD.md §1), before shapes are final and steps ordered (the pass
		// resolves the archetype's shapes to read light through them; resolving again settles its own fixtures)
		dev.voidmc.hearth.build.kit.light.AutoLighter.light(d);
		ShapeResolver.resolve(d, site);

		int[] main = d.entrances().isEmpty() ? new int[]{d.frontage() / 2, 0, -1} : d.entrances().get(0);
		EnumMap<StageKind, List<Step>> byStage = new EnumMap<>(StageKind.class);
		List<Poi> pois = new ArrayList<>();
		int[] beds = {0};
		d.forEach((x, y, z, c) -> {
			BlockState s = c.state();
			double support = supportY(d, x, y, z, s);
			int rank = rank(s);
			int dx = x - main[0];
			int dz = z - main[2];
			byStage.computeIfAbsent(c.stage(), k -> new ArrayList<>())
				.add(new Step(x, y, z, s, c.tag(), c.stage(), support, rank, dx * dx + dz * dz, c.sequence()));
			if (s.getBlock() instanceof BedBlock && s.getValue(BedBlock.PART) == BedPart.HEAD) {
				beds[0]++;
				pois.add(new Poi(PoiType.BED, frame.toWorld(x, y, z), frame.toWorld(s.getValue(BedBlock.FACING).getOpposite()), null));
			}
		});
		for (Draft.LocalPoi p : d.pois()) {
			pois.add(new Poi(p.type(), frame.toWorld(p.x(), p.y(), p.z()), frame.toWorld(p.facing()), p.job()));
		}
		List<BlockPos> entrances = new ArrayList<>();
		for (int[] e : d.entrances()) {
			entrances.add(frame.toWorld(e[0], e[1], e[2]));
		}

		List<StageKind> kinds = new ArrayList<>();
		List<List<Step>> stages = new ArrayList<>();
		for (StageKind k : StageKind.values()) {
			List<Step> list = byStage.get(k);
			if (list == null || list.isEmpty()) {
				continue;
			}
			list.sort(order(k));
			kinds.add(k);
			stages.add(list);
		}

		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (List<Step> list : stages) {
			for (Step s : list) {
				int wx = frame.worldX(s.x, s.z);
				int wy = frame.worldY(s.y);
				int wz = frame.worldZ(s.x, s.z);
				minX = Math.min(minX, wx);
				minY = Math.min(minY, wy);
				minZ = Math.min(minZ, wz);
				maxX = Math.max(maxX, wx);
				maxY = Math.max(maxY, wy);
				maxZ = Math.max(maxZ, wz);
			}
		}
		if (minX == Integer.MAX_VALUE) {
			BlockPos o = d.spec().origin();
			minX = maxX = o.getX();
			minY = maxY = o.getY();
			minZ = maxZ = o.getZ();
		}
		if (maxX - minX >= BlueprintImpl.MAX_X || maxY - minY >= BlueprintImpl.MAX_Y || maxZ - minZ >= BlueprintImpl.MAX_Z) {
			throw new IllegalStateException("blueprint too large to pack: " + (maxX - minX + 1) + "x" + (maxY - minY + 1) + "x" + (maxZ - minZ + 1));
		}
		BoundingBox bounds = new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);

		int n = stages.size();
		int[][] positions = new int[n][];
		int[][] stateIdx = new int[n][];
		@SuppressWarnings("unchecked")
		Int2ObjectMap<CompoundTag>[] tags = new Int2ObjectMap[n];
		int[] work = new int[n];
		int[][] goods = new int[n][Goods.VALUES.length];
		Map<BlockState, Integer> paletteIndex = new IdentityHashMap<>();
		List<BlockState> palette = new ArrayList<>();
		Long2ObjectOpenHashMap<List<IntArrayList>> chunkLists = new Long2ObjectOpenHashMap<>();
		for (int si = 0; si < n; si++) {
			List<Step> list = stages.get(si);
			positions[si] = new int[list.size()];
			stateIdx[si] = new int[list.size()];
			tags[si] = new Int2ObjectOpenHashMap<>();
			for (int i = 0; i < list.size(); i++) {
				Step s = list.get(i);
				BlockState world = frame.toWorld(s.state);
				int wx = frame.worldX(s.x, s.z);
				int wy = frame.worldY(s.y);
				int wz = frame.worldZ(s.x, s.z);
				positions[si][i] = BlueprintImpl.pack(wx - minX, wy - minY, wz - minZ);
				Integer pi = paletteIndex.get(world);
				if (pi == null) {
					pi = palette.size();
					palette.add(world);
					paletteIndex.put(world, pi);
				}
				stateIdx[si][i] = pi;
				if (s.tag != null) {
					tags[si].put(i, s.tag.copy());
				}
				if (world.isAir()) {
					if (s.y < site.ground(s.x, s.z)) {
						work[si]++;
					}
				} else {
					work[si]++;
					// fill moved about the site costs work, not goods
					if (s.stage != StageKind.EARTHWORK || !MaterialBill.earth(world.getBlock())) {
						MaterialBill.add(world, goods[si]);
					}
				}
				long ck = ChunkPos.pack(wx >> 4, wz >> 4);
				List<IntArrayList> per = chunkLists.get(ck);
				if (per == null) {
					per = new ArrayList<>(n);
					for (int k = 0; k < n; k++) {
						per.add(new IntArrayList());
					}
					chunkLists.put(ck, per);
				}
				per.get(si).add(i);
			}
		}
		Long2ObjectOpenHashMap<int[][]> chunks = new Long2ObjectOpenHashMap<>();
		for (var e : chunkLists.long2ObjectEntrySet()) {
			int[][] arr = new int[n][];
			for (int k = 0; k < n; k++) {
				arr[k] = e.getValue().get(k).toIntArray();
			}
			chunks.put(e.getLongKey(), arr);
		}

		Silhouette silhouette = silhouette(d, frame);
		return new BlueprintImpl(d.spec(), bounds, kinds.toArray(StageKind[]::new), positions, stateIdx, palette.toArray(BlockState[]::new), tags,
			chunks, work, goods, pois, entrances, beds[0], silhouette);
	}

	private static Comparator<Step> order(StageKind kind) {
		Comparator<Step> tail = Comparator.<Step>comparingInt(s -> s.rank).thenComparingInt(s -> s.dist).thenComparingInt(s -> s.x)
			.thenComparingInt(s -> s.z).thenComparingInt(s -> s.y);
		if (kind == StageKind.CLEAR) {
			return Comparator.<Step>comparingInt(s -> -s.y).thenComparing(tail);
		}
		if (kind == StageKind.EARTHWORK) {
			return Comparator.<Step>comparingInt(s -> s.state.isAir() ? 0 : 1)
				.thenComparingInt(s -> s.state.isAir() ? -s.y : s.y)
				.thenComparing(tail);
		}
		return Comparator.<Step>comparingDouble(s -> s.support).thenComparing(tail);
	}

	/** Blocks that hang from what is above them. */
	private static boolean hangs(BlockState s) {
		Block b = s.getBlock();
		if (b instanceof LanternBlock) {
			return s.getValue(LanternBlock.HANGING);
		}
		if (b instanceof ChainBlock) {
			return s.getValue(BlockStateProperties.AXIS) == Direction.Axis.Y;
		}
		return b instanceof CeilingHangingSignBlock || b instanceof HangingRootsBlock || b instanceof VineBlock
			|| b instanceof net.minecraft.world.level.block.WeepingVinesBlock || b instanceof net.minecraft.world.level.block.WeepingVinesPlantBlock
			|| b instanceof net.minecraft.world.level.block.SporeBlossomBlock || b instanceof net.minecraft.world.level.block.HangingMossBlock;
	}

	/** The height used to order a step: its own y, or for hanging blocks just under the ceiling they hang from. */
	private static double supportY(Draft d, int x, int y, int z, BlockState s) {
		if (!hangs(s)) {
			return y;
		}
		int top = y;
		while (true) {
			BlockState above = d.get(x, top + 1, z);
			if (above == null || !hangs(above)) {
				break;
			}
			top++;
		}
		// after the ceiling (top + 1), chain links top-down
		return top + 1 + 0.5 - (top - y) * 0.01;
	}

	private static int rank(BlockState s) {
		Block b = s.getBlock();
		if (hangs(s)) {
			return 3;
		}
		if (b instanceof WallTorchBlock || b instanceof WallSignBlock || b instanceof WallHangingSignBlock || b instanceof WallBannerBlock
			|| b instanceof LadderBlock || b instanceof TrapDoorBlock || b instanceof ButtonBlock || b instanceof LeverBlock) {
			return 1;
		}
		if (b instanceof CarpetBlock || b instanceof BasePressurePlateBlock || b instanceof VegetationBlock || b instanceof TorchBlock
			|| b instanceof LanternBlock || b instanceof CandleBlock || b instanceof FlowerPotBlock || b instanceof SnowLayerBlock) {
			return 2;
		}
		return 0;
	}

	/** Wall and roof colours as seen from far away, from the map colours of the outer walls and the top of each column. */
	private static Silhouette silhouette(Draft d, LocalFrame frame) {
		long[] wall = new long[4];
		long[] roof = new long[4];
		int[] top = {Integer.MIN_VALUE};
		Map<Long, int[]> columns = new java.util.HashMap<>();
		d.forEach((x, y, z, c) -> {
			if (c.state().isAir()) {
				return;
			}
			top[0] = Math.max(top[0], y);
			long ck = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
			int[] col = columns.get(ck);
			if (col == null || y > col[2]) {
				columns.put(ck, new int[]{x, z, y});
			}
		});
		int eave = d.eaveY() != Integer.MIN_VALUE ? d.eaveY() : Math.max(1, top[0] - 3);
		d.forEach((x, y, z, c) -> {
			BlockState s = c.state();
			if (s.isAir() || y < 0 || y > eave) {
				return;
			}
			boolean outer = d.isEmpty(x + 1, y, z) || d.isEmpty(x - 1, y, z) || d.isEmpty(x, y, z + 1) || d.isEmpty(x, y, z - 1);
			if (outer && s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
				accumulate(wall, s);
			}
		});
		for (int[] col : columns.values()) {
			BlockState s = d.get(col[0], col[2], col[1]);
			if (s != null && col[2] >= eave - 1) {
				accumulate(roof, s);
			}
		}
		return new Silhouette(average(wall, 0x9A8670), average(roof, 0x6B4A2E), frame.worldY(eave), frame.worldY(Math.max(top[0], eave)));
	}

	private static void accumulate(long[] acc, BlockState s) {
		int col = s.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).col;
		if (col == 0) {
			return;
		}
		acc[0] += (col >> 16) & 0xFF;
		acc[1] += (col >> 8) & 0xFF;
		acc[2] += col & 0xFF;
		acc[3]++;
	}

	private static int average(long[] acc, int fallback) {
		if (acc[3] == 0) {
			return fallback;
		}
		return (int) (acc[0] / acc[3]) << 16 | (int) (acc[1] / acc[3]) << 8 | (int) (acc[2] / acc[3]);
	}
}
