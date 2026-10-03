package dev.voidmc.hearth.build.check;

import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.blueprint.LocalFrame;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.pathfinder.PathComputationType;

/**
 * Invariants every blueprint must keep, checked on the finished blueprint against the terrain it was made for: it stays
 * in its plot and height, nothing floats, every bed and job site can be walked to from a door, the main door can be
 * walked to from the street, the chunk index agrees with the steps, and stages come in order with structure laid
 * bottom-up. Used by the unit tests over every archetype, style and terrain; cheap enough to run in a dev command.
 */
public final class BlueprintChecks {
	private BlueprintChecks() {
	}

	/** The result of applying a blueprint over its natural terrain. */
	static final class World {
		final Long2ObjectOpenHashMap<BlockState> placed = new Long2ObjectOpenHashMap<>();
		final TerrainProbe probe;

		World(BlueprintImpl bp, TerrainProbe probe) {
			this.probe = probe;
			for (int s = 0; s < bp.stageCount(); s++) {
				for (int i = 0; i < bp.stepCount(s); i++) {
					bp.step(s, i, (x, y, z, state, tag) -> this.placed.put(BlockPos.asLong(x, y, z), state));
				}
			}
		}

		/** The block at a position: what the blueprint placed, else natural ground, water or air. */
		BlockState at(int x, int y, int z) {
			BlockState s = this.placed.get(BlockPos.asLong(x, y, z));
			if (s != null) {
				return s;
			}
			if (y < this.probe.groundY(x, z)) {
				return Blocks.STONE.defaultBlockState();
			}
			int wy = this.probe.waterY(x, z);
			if (wy != TerrainProbe.NO_WATER && y < wy) {
				return Blocks.WATER.defaultBlockState();
			}
			return Blocks.AIR.defaultBlockState();
		}

		boolean natural(int x, int y, int z) {
			return !this.placed.containsKey(BlockPos.asLong(x, y, z)) && y < this.probe.groundY(x, z);
		}
	}

	/** Runs every check; returns the problems found (empty when the blueprint is sound). */
	public static List<String> all(BlueprintImpl bp, TerrainProbe probe) {
		List<String> out = new ArrayList<>();
		World w = new World(bp, probe);
		bounds(bp, out);
		floating(bp, w, out);
		reach(bp, w, out);
		chunks(bp, out);
		order(bp, out);
		return out;
	}

	/** Every step inside the plot and under its height; only overhangs at least 3 above the street may leave it. */
	public static void bounds(BlueprintImpl bp, List<String> out) {
		PlotSpec spec = bp.spec();
		LocalFrame f = new LocalFrame(spec);
		int bad = 0;
		String first = null;
		for (int s = 0; s < bp.stageCount(); s++) {
			for (int i = 0; i < bp.stepCount(s); i++) {
				BlockPos p = bp.pos(s, i);
				int lx = f.localX(p.getX(), p.getZ());
				int lz = f.localZ(p.getX(), p.getZ());
				int ly = p.getY() - spec.floorY();
				boolean inside = lx >= 0 && lx < f.frontage() && lz >= 0 && lz < f.depth();
				boolean overhang = lx >= 0 && lx < f.frontage() && lz == -1 && ly >= 3;
				if (!(inside || overhang) || ly >= spec.maxHeight()) {
					bad++;
					if (first == null) {
						first = bp.state(s, i) + " at local " + lx + "," + ly + "," + lz + " (" + bp.stageKind(s) + ")";
					}
				}
			}
		}
		if (bad > 0) {
			out.add(bad + " steps outside the plot or above max height " + spec.maxHeight() + ", e.g. " + first);
		}
	}

	/** Blocks allowed to hang from what is above them. */
	private static boolean hangs(BlockState s) {
		Block b = s.getBlock();
		return b instanceof LanternBlock && s.getValue(LanternBlock.HANGING) || b instanceof ChainBlock || b instanceof net.minecraft.world.level.block.CeilingHangingSignBlock
			|| b instanceof net.minecraft.world.level.block.VineBlock || b instanceof net.minecraft.world.level.block.HangingRootsBlock;
	}

	/** Offsets of the 18-neighbourhood: blocks sharing a face or an edge (a roof's stair courses touch along edges). */
	private static final int[][] N18 = n18();

	private static int[][] n18() {
		List<int[]> l = new ArrayList<>();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					int nz = (dx != 0 ? 1 : 0) + (dy != 0 ? 1 : 0) + (dz != 0 ? 1 : 0);
					if (nz == 1 || nz == 2) {
						l.add(new int[]{dx, dy, dz});
					}
				}
			}
		}
		return l.toArray(int[][]::new);
	}

	/**
	 * Nothing floats: every placed block connects through placed blocks to the natural ground, counting blocks that
	 * share a face or an edge as joined (stair courses of a roof meet along their edges, and look joined).
	 */
	public static void floating(BlueprintImpl bp, World w, List<String> out) {
		LongOpenHashSet solid = new LongOpenHashSet();
		for (Long2ObjectMap.Entry<BlockState> e : w.placed.long2ObjectEntrySet()) {
			if (!e.getValue().isAir()) {
				solid.add(e.getLongKey());
			}
		}
		LongOpenHashSet seen = new LongOpenHashSet();
		LongArrayFIFOQueue q = new LongArrayFIFOQueue();
		for (long k : solid) {
			int x = BlockPos.getX(k);
			int y = BlockPos.getY(k);
			int z = BlockPos.getZ(k);
			for (Direction d : Direction.values()) {
				if (w.natural(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ())) {
					seen.add(k);
					q.enqueue(k);
					break;
				}
			}
		}
		while (!q.isEmpty()) {
			long k = q.dequeueLong();
			int x = BlockPos.getX(k);
			int y = BlockPos.getY(k);
			int z = BlockPos.getZ(k);
			for (int[] o : N18) {
				long n = BlockPos.asLong(x + o[0], y + o[1], z + o[2]);
				if (solid.contains(n) && seen.add(n)) {
					q.enqueue(n);
				}
			}
		}
		int bad = 0;
		String first = null;
		for (long k : solid) {
			if (!seen.contains(k)) {
				bad++;
				if (first == null) {
					first = w.placed.get(k) + " at " + BlockPos.of(k);
				}
			}
		}
		if (bad > 0) {
			out.add(bad + " blocks float (not connected to the ground), e.g. " + first);
		}
		// what must rest on something does
		int unsupported = 0;
		String uf = null;
		for (long k : solid) {
			BlockState s = w.placed.get(k);
			int x = BlockPos.getX(k);
			int y = BlockPos.getY(k);
			int z = BlockPos.getZ(k);
			boolean needsBelow = s.getBlock() instanceof VegetationBlock || s.getBlock() instanceof CarpetBlock
				|| s.getBlock() instanceof LanternBlock && !s.getValue(LanternBlock.HANGING) || s.getBlock() instanceof SnowLayerBlock;
			boolean needsAbove = hangs(s) && !(s.getBlock() instanceof net.minecraft.world.level.block.VineBlock);
			if (needsBelow && w.at(x, y - 1, z).isAir() || needsAbove && w.at(x, y + 1, z).isAir()) {
				unsupported++;
				if (uf == null) {
					uf = s + " at " + BlockPos.of(k);
				}
			}
		}
		if (unsupported > 0) {
			out.add(unsupported + " blocks lack the support they need (plants, carpets, lanterns), e.g. " + uf);
		}
	}

	// ---------------------------------------------------------------------------------------------- walking

	private static boolean passable(BlockState s) {
		Block b = s.getBlock();
		if (s.isAir() || b instanceof DoorBlock || b instanceof FenceGateBlock || b instanceof CarpetBlock || b instanceof VegetationBlock
			|| b instanceof LadderBlock || b instanceof ScaffoldingBlock || b instanceof LiquidBlock || hangs(s)) {
			return true;
		}
		if (b instanceof TrapDoorBlock) {
			return s.getValue(TrapDoorBlock.OPEN) || s.getValue(TrapDoorBlock.HALF) == Half.BOTTOM;
		}
		if (b instanceof SnowLayerBlock) {
			return s.getValue(SnowLayerBlock.LAYERS) <= 4;
		}
		return s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()
			|| s.isPathfindable(PathComputationType.LAND) && !(b instanceof net.minecraft.world.level.block.FenceBlock)
			&& !(b instanceof net.minecraft.world.level.block.WallBlock) && s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).max(Direction.Axis.Y) <= 0.2;
	}

	private static boolean standable(BlockState s) {
		Block b = s.getBlock();
		if (s.isAir() || b instanceof net.minecraft.world.level.block.FenceBlock || b instanceof net.minecraft.world.level.block.WallBlock
			|| b instanceof FenceGateBlock) {
			return false;
		}
		if (b instanceof LiquidBlock) {
			return true;
		}
		return !s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
	}

	/** Feet at p: the block there and above can be walked through and the block below stood on (or it is a ladder). */
	private static boolean walkable(World w, int x, int y, int z) {
		BlockState feet = w.at(x, y, z);
		BlockState head = w.at(x, y + 1, z);
		if (!passable(feet) || !passable(head)) {
			return false;
		}
		if (feet.getBlock() instanceof LadderBlock || feet.getBlock() instanceof ScaffoldingBlock) {
			return true;
		}
		BlockState below = w.at(x, y - 1, z);
		return standable(below) || below.getBlock() instanceof LadderBlock || below.getBlock() instanceof ScaffoldingBlock;
	}

	/** A stair, a bottom slab or similar low block that can be walked up onto from beside it. */
	private static boolean step(BlockState s) {
		return s.getBlock() instanceof StairBlock && s.getValue(StairBlock.HALF) == Half.BOTTOM
			|| s.getBlock() instanceof SlabBlock && s.getValue(SlabBlock.TYPE) == SlabType.BOTTOM || s.getBlock() instanceof BedBlock;
	}

	/** Every cell walkable from the starts, within a box. */
	static LongOpenHashSet walk(World w, List<BlockPos> starts, int[] box) {
		LongOpenHashSet seen = new LongOpenHashSet();
		LongArrayFIFOQueue q = new LongArrayFIFOQueue();
		for (BlockPos p : starts) {
			// a start may be given at the step level: settle it on what is walkable nearby
			for (int dy = 0; dy >= -2; dy--) {
				if (walkable(w, p.getX(), p.getY() + dy, p.getZ())) {
					long k = BlockPos.asLong(p.getX(), p.getY() + dy, p.getZ());
					if (seen.add(k)) {
						q.enqueue(k);
					}
					break;
				}
			}
			if (walkable(w, p.getX(), p.getY() + 1, p.getZ())) {
				long k = BlockPos.asLong(p.getX(), p.getY() + 1, p.getZ());
				if (seen.add(k)) {
					q.enqueue(k);
				}
			}
		}
		while (!q.isEmpty()) {
			long k = q.dequeueLong();
			int x = BlockPos.getX(k);
			int y = BlockPos.getY(k);
			int z = BlockPos.getZ(k);
			BlockState here = w.at(x, y, z);
			boolean climb = here.getBlock() instanceof LadderBlock || here.getBlock() instanceof ScaffoldingBlock;
			for (Direction d : Direction.Plane.HORIZONTAL) {
				int nx = x + d.getStepX();
				int nz = z + d.getStepZ();
				if (nx < box[0] || nx > box[3] || nz < box[2] || nz > box[5]) {
					continue;
				}
				for (int dy = 1; dy >= -3; dy--) {
					int ny = y + dy;
					if (ny < box[1] || ny > box[4]) {
						continue;
					}
					if (dy == 1 && !passable(w.at(x, y + 2, z))) {
						continue;
					}
					if (walkable(w, nx, ny, nz)) {
						long n = BlockPos.asLong(nx, ny, nz);
						if (seen.add(n)) {
							q.enqueue(n);
						}
						break;
					}
					// stepping onto a stair or slab whose cell is the next feet position's floor
					if (dy == 0 && step(w.at(nx, ny, nz)) && walkable(w, nx, ny + 1, nz) && passable(w.at(x, y + 2, z))) {
						long n = BlockPos.asLong(nx, ny + 1, nz);
						if (seen.add(n)) {
							q.enqueue(n);
						}
						break;
					}
					if (!passable(w.at(nx, ny, nz)) || dy < 0 && !passable(w.at(nx, ny + 1, nz))) {
						if (dy <= 0) {
							break;
						}
					}
				}
			}
			if (climb) {
				for (int dy : new int[]{1, -1}) {
					if (walkable(w, x, y + dy, z) || w.at(x, y + dy, z).getBlock() instanceof LadderBlock) {
						long n = BlockPos.asLong(x, y + dy, z);
						if (seen.add(n)) {
							q.enqueue(n);
						}
					}
				}
			}
		}
		return seen;
	}

	/**
	 * Folk can get about: from the street every entrance (the main one at least) can be walked to, and from the
	 * entrances every bed, job site and store.
	 */
	public static void reach(BlueprintImpl bp, World w, List<String> out) {
		PlotSpec spec = bp.spec();
		LocalFrame f = new LocalFrame(spec);
		var b = bp.bounds();
		int[] box = {b.minX() - 2, b.minY() - 2, b.minZ() - 2, b.maxX() + 2, b.maxY() + 2, b.maxZ() + 2};
		// the street: the row just in front of the plot, at its natural ground
		List<BlockPos> street = new ArrayList<>();
		for (int lx = 0; lx < f.frontage(); lx++) {
			int wx = f.worldX(lx, -1);
			int wz = f.worldZ(lx, -1);
			street.add(new BlockPos(wx, Math.max(w.probe.groundY(wx, wz), spec.floorY()), wz));
		}
		LongOpenHashSet fromStreet = walk(w, street, box);
		if (!bp.entrances().isEmpty()) {
			BlockPos main = bp.entrances().get(0);
			if (!near(fromStreet, main)) {
				out.add("the main entrance " + main + " cannot be walked to from the street (there: " + w.at(main.getX(), main.getY(), main.getZ())
					+ ", below: " + w.at(main.getX(), main.getY() - 1, main.getZ()) + ", above: " + w.at(main.getX(), main.getY() + 1, main.getZ()) + ")");
			}
		}
		LongOpenHashSet inside = walk(w, bp.entrances().isEmpty() ? street : bp.entrances(), box);
		int beds = 0;
		int jobs = 0;
		String first = null;
		for (Poi p : bp.pois()) {
			boolean ok;
			if (p.type() == PoiType.BED) {
				ok = false;
				for (Direction d : Direction.Plane.HORIZONTAL) {
					BlockPos n = p.pos().relative(d);
					ok |= near(inside, n) || near(inside, n.relative(w.at(p.pos().getX(), p.pos().getY(), p.pos().getZ()).getBlock() instanceof BedBlock
						? w.at(p.pos().getX(), p.pos().getY(), p.pos().getZ()).getValue(BedBlock.FACING).getOpposite() : d));
				}
				if (!ok) {
					beds++;
				}
			} else if (p.type() == PoiType.JOB_SITE || p.type() == PoiType.STORAGE) {
				ok = near(inside, p.pos());
				if (!ok) {
					jobs++;
				}
			} else {
				ok = true;
			}
			if (!ok && first == null) {
				BlockPos q = p.pos();
				first = p.type() + " at " + q + " (" + w.at(q.getX(), q.getY(), q.getZ()) + " on " + w.at(q.getX(), q.getY() - 1, q.getZ()) + ")";
			}
		}
		if (beds + jobs > 0) {
			out.add(beds + " beds and " + jobs + " job sites/stores cannot be walked to from a door, e.g. " + first);
		}
	}

	/**
	 * A text map of where folk can walk at one height (world y): 'r' reached from the entrances, '+' walkable but not
	 * reached, '#' solid, '.' air, 'B' a bed, 'P' a job site or store, 'E' an entrance. For debugging generators.
	 */
	public static String reachMap(BlueprintImpl bp, TerrainProbe probe, int y) {
		World w = new World(bp, probe);
		var b = bp.bounds();
		int[] box = {b.minX() - 2, b.minY() - 2, b.minZ() - 2, b.maxX() + 2, b.maxY() + 2, b.maxZ() + 2};
		LongOpenHashSet in = walk(w, bp.entrances(), box);
		StringBuilder sb = new StringBuilder("reach y=" + y + " (x across, z down)\n");
		for (int z = b.minZ() - 1; z <= b.maxZ() + 1; z++) {
			for (int x = b.minX() - 1; x <= b.maxX() + 1; x++) {
				BlockPos p = new BlockPos(x, y, z);
				char c;
				BlockState s = w.at(x, y, z);
				if (in.contains(p.asLong())) {
					c = 'r';
				} else if (walkable(w, x, y, z)) {
					c = '+';
				} else if (s.isAir()) {
					c = '.';
				} else if (s.getBlock() instanceof BedBlock) {
					c = 'B';
				} else {
					c = passable(s) ? ',' : '#';
				}
				for (Poi poi : bp.pois()) {
					if (poi.pos().equals(p) && (poi.type() == PoiType.JOB_SITE || poi.type() == PoiType.STORAGE)) {
						c = c == 'r' ? 'p' : 'P';
					}
				}
				if (bp.entrances().contains(p)) {
					c = 'E';
				}
				sb.append(c);
			}
			sb.append('\n');
		}
		return sb.toString();
	}

	/** Reached at the position, or with feet a block above or below it (stairs, slabs, a step at a door). */
	private static boolean near(LongOpenHashSet set, BlockPos p) {
		return set.contains(p.asLong()) || set.contains(p.above().asLong()) || set.contains(p.below().asLong());
	}

	/** The chunk index lists exactly the steps in each chunk, in build order. */
	public static void chunks(BlueprintImpl bp, List<String> out) {
		int total = 0;
		for (long c : bp.chunks()) {
			int cx = ChunkPos.getX(c);
			int cz = ChunkPos.getZ(c);
			int[] count = {0};
			boolean[] wrong = {false};
			bp.forEachInChunk(cx, cz, 0, bp.stageCount(), (x, y, z, s, t) -> {
				count[0]++;
				if (x >> 4 != cx || z >> 4 != cz) {
					wrong[0] = true;
				}
			});
			if (wrong[0]) {
				out.add("chunk " + cx + "," + cz + " lists steps outside it");
			}
			total += count[0];
		}
		if (total != bp.totalSteps()) {
			out.add("the chunk index holds " + total + " steps, the blueprint " + bp.totalSteps());
		}
	}

	/** Stages in StageKind order; structural blocks laid bottom-up within each building stage. */
	public static void order(BlueprintImpl bp, List<String> out) {
		for (int s = 1; s < bp.stageCount(); s++) {
			if (bp.stageKind(s).ordinal() <= bp.stageKind(s - 1).ordinal()) {
				out.add("stage " + bp.stageKind(s) + " comes after " + bp.stageKind(s - 1));
			}
		}
		for (int s = 0; s < bp.stageCount(); s++) {
			StageKind k = bp.stageKind(s);
			if (k == StageKind.CLEAR || k == StageKind.EARTHWORK) {
				continue;
			}
			int lastY = Integer.MIN_VALUE;
			for (int i = 0; i < bp.stepCount(s); i++) {
				BlockState st = bp.state(s, i);
				if (hangs(st) || st.isAir() || !st.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
					continue;
				}
				int y = bp.pos(s, i).getY();
				if (y < lastY) {
					out.add("stage " + k + " lays a block at y " + y + " after one at y " + lastY + " (step " + i + ")");
					break;
				}
				lastY = y;
			}
		}
	}

	/** Two blueprints have the same steps in the same order with the same states and data. */
	public static boolean same(BlueprintImpl a, BlueprintImpl b) {
		if (a.stageCount() != b.stageCount() || !a.bounds().equals(b.bounds())) {
			return false;
		}
		for (int s = 0; s < a.stageCount(); s++) {
			if (a.stageKind(s) != b.stageKind(s) || a.stepCount(s) != b.stepCount(s)) {
				return false;
			}
			for (int i = 0; i < a.stepCount(s); i++) {
				if (!a.pos(s, i).equals(b.pos(s, i)) || a.state(s, i) != b.state(s, i) || !Objects.equals(a.tag(s, i), b.tag(s, i))) {
					return false;
				}
			}
		}
		return a.pois().equals(b.pois()) && a.entrances().equals(b.entrances()) && a.beds() == b.beds();
	}

	/** Whether a state is fully standable-solid (exposed for tests). */
	static boolean solid(BlockState s) {
		return s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) && !(s.getBlock() instanceof LeavesBlock)
			&& !s.hasProperty(BlockStateProperties.WATERLOGGED);
	}
}
