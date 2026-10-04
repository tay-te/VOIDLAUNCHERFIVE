package dev.voidmc.hearth.sim.world;

import dev.voidmc.hearth.api.ResourceSite;
import dev.voidmc.hearth.sim.SimConfig;
import dev.voidmc.hearth.sim.apply.StepWriter;
import dev.voidmc.hearth.sim.land.SimSite;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * The trees a town's foresters took abstractly from its natural woods come down in the world (docs/LIVING_WORLD.md §7.3,
 * §7.4): a few random columns of each wood that owes logs are looked at per tick, in loaded chunks only, under a time
 * budget; a natural tree there (logs under leaves that decay) is felled whole, top to bottom, and a sapling of its kind
 * goes in at the stump — woodlots always replant. Coming back after a month, the forest edge has receded.
 *
 * <p>Guardrails (§7.8): never within {@code fell_away_from_players} of a player (they would see trees vanish; embodied
 * foresters fell in view), never on planned or built ground, never next to anything a player built (any block that is
 * not natural ground, plants or trees round the trunk), never at all when extraction is abstract only. Server thread
 * only.
 */
final class Woodcutter {
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
	/** Columns looked at per tick at most, whatever the budget. */
	private static final int MAX_LOOKS = 48;
	private static final int MAX_LOGS = 320;
	private static final int MAX_LEAVES = 900;

	private final SimWorld world;
	private final RandomSource random = RandomSource.create(0x57_0D_C0_77L);
	private int cursor;
	private @Nullable List<Owed> owed;
	private int sinceList;
	long felledTrees;
	long felledLogs;
	long nanos;

	Woodcutter(SimWorld world) {
		this.world = world;
	}

	void tick(long budgetNanos) {
		SimConfig.Extraction x = this.world.rules().resources.extraction();
		if (!x.fellWoods() || x.abstractOnly()) {
			return;
		}
		long start = System.nanoTime();
		// the woods that owe trees, looked up again every few seconds (not every tick: it is a pass over every site)
		if (this.owed == null || ++this.sinceList >= 100) {
			this.owed = owed();
			this.sinceList = 0;
		}
		List<Owed> owed = this.owed;
		if (owed.isEmpty()) {
			return;
		}
		ServerLevel level = this.world.level();
		int looks = 0;
		while (looks < MAX_LOOKS && System.nanoTime() - start < budgetNanos) {
			Owed o = owed.get(this.cursor++ % owed.size());
			looks++;
			var area = o.site.area();
			int px = area.minX() + this.random.nextInt(Math.max(1, area.getXSpan()));
			int pz = area.minZ() + this.random.nextInt(Math.max(1, area.getZSpan()));
			LevelChunk chunk = level.getChunkSource().getChunkNow(px >> 4, pz >> 4);
			if (chunk == null || nearPlayer(level, px, pz, x.fellAway()) || this.world.isPlanned(px, pz)) {
				continue;
			}
			int logs = fell(level, chunk, px, pz, o.site.material());
			if (logs > 0) {
				o.site.felled(logs);
				o.settlement.markDirty();
				this.felledTrees++;
				this.felledLogs += logs;
			}
		}
		this.nanos += System.nanoTime() - start;
	}

	private record Owed(SimSettlement settlement, SimSite site) {
	}

	private List<Owed> owed() {
		List<Owed> out = new ArrayList<>();
		for (SimSettlement s : this.world.settlements()) {
			for (SimSite site : s.land().sites()) {
				if (site.kind() == ResourceSite.Kind.WOODS && site.fellingDebt() >= 4) {
					out.add(new Owed(s, site));
				}
			}
		}
		return out;
	}

	private static boolean nearPlayer(ServerLevel level, int x, int z, int away) {
		long a2 = (long) away * away;
		for (ServerPlayer p : level.players()) {
			double dx = p.getX() - x;
			double dz = p.getZ() - z;
			if (dx * dx + dz * dz < a2) {
				return true;
			}
		}
		return false;
	}

	/** Fells the natural tree at a column, if there is one and nothing a player built stands by it; returns its logs. */
	private int fell(ServerLevel level, LevelChunk chunk, int x, int z, @Nullable Identifier species) {
		int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15);
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, top, z);
		int logY = Integer.MIN_VALUE;
		for (int y = top; y > top - 24; y--) {
			m.setY(y);
			BlockState st = chunk.getBlockState(m);
			if (st.is(BlockTags.LOGS)) {
				logY = y;
				break;
			}
			if (!st.isAir() && !st.is(BlockTags.LEAVES) && !st.canBeReplaced()) {
				return 0;
			}
		}
		if (logY == Integer.MIN_VALUE) {
			return 0;
		}
		// down the trunk to its foot
		int baseY = logY;
		while (baseY > level.getMinY() + 1 && chunk.getBlockState(m.set(x, baseY - 1, z)).is(BlockTags.LOGS)) {
			baseY--;
		}
		BlockPos base = new BlockPos(x, baseY, z);
		BlockState ground = level.getBlockState(base.below());
		if (!ground.is(BlockTags.DIRT)) {
			return 0;
		}
		if (!natural(level, base) || !hasNaturalLeaves(level, base, logY)) {
			return 0;
		}
		// the whole tree: logs joined to the trunk (diagonals too), then the leaves round them that would decay
		List<BlockPos> logs = new ArrayList<>();
		LongOpenHashSet seen = new LongOpenHashSet();
		LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
		queue.enqueue(base.asLong());
		seen.add(base.asLong());
		while (!queue.isEmpty() && logs.size() < MAX_LOGS) {
			BlockPos p = BlockPos.of(queue.dequeueLong());
			if (!level.isLoaded(p) || !level.getBlockState(p).is(BlockTags.LOGS)) {
				continue;
			}
			logs.add(p);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = 0; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						BlockPos q = p.offset(dx, dy, dz);
						if (Math.abs(q.getX() - x) <= 10 && Math.abs(q.getZ() - z) <= 10 && seen.add(q.asLong())) {
							queue.enqueue(q.asLong());
						}
					}
				}
			}
		}
		List<BlockPos> leaves = new ArrayList<>();
		LongOpenHashSet leafSeen = new LongOpenHashSet();
		for (BlockPos p : logs) {
			for (BlockPos q : BlockPos.betweenClosed(p.offset(-3, -1, -3), p.offset(3, 3, 3))) {
				if (leaves.size() >= MAX_LEAVES) {
					break;
				}
				if (!leafSeen.add(q.asLong()) || !level.isLoaded(q)) {
					continue;
				}
				BlockState st = level.getBlockState(q);
				if (st.is(BlockTags.LEAVES) && st.hasProperty(LeavesBlock.PERSISTENT) && !st.getValue(LeavesBlock.PERSISTENT)) {
					leaves.add(q.immutable());
				}
			}
		}
		BlockState air = Blocks.AIR.defaultBlockState();
		// top to bottom, as a forester takes a tree down
		logs.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
		for (BlockPos p : leaves) {
			level.setBlock(p, air, FLAGS);
		}
		for (BlockPos p : logs) {
			level.setBlock(p, air, FLAGS);
		}
		BlockState sapling = sapling(species != null ? species : BuiltInRegistries.BLOCK.getKey(level.getBlockState(base).getBlock()));
		if (sapling != null && sapling.canSurvive(level, base)) {
			level.setBlock(base, sapling, Block.UPDATE_ALL);
		}
		return logs.size();
	}

	/** Nothing but natural ground, plants, water and trees round the trunk (so no one's garden or cabin goes with it). */
	private static boolean natural(ServerLevel level, BlockPos base) {
		for (BlockPos q : BlockPos.betweenClosed(base.offset(-4, -2, -4), base.offset(4, 4, 4))) {
			if (!level.isLoaded(q)) {
				return false;
			}
			BlockState st = level.getBlockState(q);
			boolean wild = st.isAir() || st.is(BlockTags.LOGS) || st.is(BlockTags.LEAVES) || st.is(StepWriter.BUILDABLE_OVER) || st.canBeReplaced()
				|| !st.getFluidState().isEmpty();
			if (!wild) {
				return false;
			}
		}
		return true;
	}

	private static boolean hasNaturalLeaves(ServerLevel level, BlockPos base, int logY) {
		for (BlockPos q : BlockPos.betweenClosed(base.offset(-2, Math.max(1, logY - base.getY() - 2), -2), base.offset(2, logY - base.getY() + 3, 2))) {
			BlockState st = level.getBlockState(q);
			if (st.is(BlockTags.LEAVES) && st.hasProperty(LeavesBlock.PERSISTENT) && !st.getValue(LeavesBlock.PERSISTENT)) {
				return true;
			}
		}
		return false;
	}

	/** The sapling of a log's tree ({@code ns:birch_log} → {@code ns:birch_sapling}, mangrove's propagule), or null. */
	static @Nullable BlockState sapling(Identifier log) {
		String path = log.getPath();
		String name;
		if (path.equals("mangrove_log")) {
			name = "mangrove_propagule";
		} else if (path.endsWith("_log")) {
			name = path.substring(0, path.length() - 4) + "_sapling";
		} else if (path.endsWith("_wood")) {
			name = path.substring(0, path.length() - 5) + "_sapling";
		} else {
			return null;
		}
		return BuiltInRegistries.BLOCK.getOptional(Identifier.fromNamespaceAndPath(log.getNamespace(), name)).map(Block::defaultBlockState)
			.orElse(null);
	}

	String describe() {
		return String.format(Locale.ROOT, "%d trees (%d logs) felled in natural woods, %.2f ms total", this.felledTrees, this.felledLogs, this.nanos / 1e6);
	}
}
