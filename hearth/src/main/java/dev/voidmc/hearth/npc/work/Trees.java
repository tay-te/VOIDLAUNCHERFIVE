package dev.voidmc.hearth.npc.work;

import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.ResourceSite;
import dev.voidmc.hearth.api.Settlement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * Finding a tree a forester may fell, and everything that comes down with it (docs/LIVING_WORLD.md §7.4, §7.8).
 *
 * <p>A tree is a connected body of logs standing on natural soil with natural (non-persistent) leaves on it: player-
 * placed leaves are persistent, so a log cabin or a hedge of placed leaves is never taken for a tree. A tree is refused
 * if any block entity or building block touches it (a treehouse, a sign nailed to the trunk, a bee nest), if it is
 * larger than {@code gathering.max_tree_logs} (the redwood giants stay standing), if it stands inside or beside a plot,
 * near a player's deeded plot, outside the settlement's territory, or inside the town where it is not a wood.
 *
 * <p>Reads only loaded chunks, and only a bounded number of blocks per search.
 */
public final class Trees {
	/** Horizontal reach of one tree's crown from its trunk. */
	private static final int CROWN = 7;
	private static final int MAX_LEAVES = 900;

	private Trees() {
	}

	/**
	 * A tree to fell.
	 *
	 * @param base     the lowest trunk log (the stump)
	 * @param stumps   every trunk log at the base level standing on soil (four for a 2×2 trunk), where saplings go
	 * @param logs     every log, highest first: the order they come down
	 * @param leaves   the tree's own natural leaves, highest first
	 * @param extras   vines and cocoa growing on it, removed with it
	 * @param log      the trunk's block, for the sapling and what the forester carries
	 */
	public record Tree(BlockPos base, List<BlockPos> stumps, List<BlockPos> logs, List<BlockPos> leaves, List<BlockPos> extras, Block log) {
	}

	/**
	 * Looks for a tree to fell for a settlement: in a woods site of its territory if the survey found one, else in a
	 * ring just beyond the town's edge. A few sample spots are tried, each a 9×9 patch of columns.
	 */
	public static @Nullable Tree find(ServerLevel level, Settlement s, RandomSource random, int maxLogs, int keepClearOfDeeds, Set<Long> claimed) {
		List<ResourceSite> woods = new ArrayList<>();
		for (ResourceSite site : s.sites()) {
			if (site.kind() == ResourceSite.Kind.WOODS && (site.remaining() > 0 || site.regenPerDay() > 0)) {
				woods.add(site);
			}
		}
		for (int attempt = 0; attempt < 6; attempt++) {
			BlockPos sample = sample(level, s, woods, random);
			if (sample == null || !level.isLoaded(sample)) {
				continue;
			}
			for (int i = 0; i < 81; i++) {
				// spiral-ish: a random walk over the 9x9 patch, so neighbouring foresters do not converge on one tree
				int dx = random.nextInt(9) - 4;
				int dz = random.nextInt(9) - 4;
				BlockPos column = sample.offset(dx, 0, dz);
				if (!level.isLoaded(column)) {
					continue;
				}
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column).below();
				BlockState st = level.getBlockState(top);
				if (!isNaturalLeaves(st) && !st.is(BlockTags.LOGS)) {
					continue;
				}
				BlockPos trunk = findTrunk(level, top);
				if (trunk == null || claimed.contains(trunk.asLong())) {
					continue;
				}
				if (!allowedAt(s, trunk, woods, keepClearOfDeeds)) {
					continue;
				}
				Tree tree = collect(level, trunk, maxLogs);
				if (tree != null && !claimed.contains(tree.base().asLong())) {
					return tree;
				}
			}
		}
		return null;
	}

	private static @Nullable BlockPos sample(ServerLevel level, Settlement s, List<ResourceSite> woods, RandomSource random) {
		if (!woods.isEmpty() && random.nextInt(4) != 0) {
			ResourceSite site = woods.get(random.nextInt(woods.size()));
			BoundingBox a = site.area();
			int x = a.minX() + random.nextInt(Math.max(1, a.getXSpan()));
			int z = a.minZ() + random.nextInt(Math.max(1, a.getZSpan()));
			return new BlockPos(x, site.center().getY(), z);
		}
		BoundingBox b = s.bounds();
		BlockPos c = s.center();
		double angle = random.nextDouble() * Math.PI * 2.0;
		double r = Math.max(b.getXSpan(), b.getZSpan()) / 2.0 + 10 + random.nextInt(30);
		if (s.territoryRadius() > 0) {
			r = Math.min(r, s.territoryRadius() - 4);
		}
		r = Math.min(r, 96);
		return new BlockPos(c.getX() + (int) Math.round(Math.cos(angle) * r), c.getY(), c.getZ() + (int) Math.round(Math.sin(angle) * r));
	}

	/** Whether a forester of this settlement may fell a tree standing here. */
	static boolean allowedAt(Settlement s, BlockPos trunk, List<ResourceSite> woods, int keepClearOfDeeds) {
		int radius = s.territoryRadius();
		if (radius > 0 && horizontalDistSqr(trunk, s.center()) > (double) radius * radius) {
			return false;
		}
		boolean inWoods = false;
		for (ResourceSite w : woods) {
			if (inside(w.area(), trunk, 0)) {
				inWoods = true;
				break;
			}
		}
		// Inside the town only where the survey marked woods: elsewhere the trees between houses are left for shade.
		if (!inWoods && inside(s.bounds(), trunk, 4)) {
			return false;
		}
		for (PlotView p : s.plots()) {
			PlotSpec spec = p.spec();
			int margin = p.owner() != null ? Math.max(keepClearOfDeeds, 2) : 2;
			if (trunk.getX() >= spec.origin().getX() - margin && trunk.getX() < spec.origin().getX() + spec.sizeX() + margin
				&& trunk.getZ() >= spec.origin().getZ() - margin && trunk.getZ() < spec.origin().getZ() + spec.sizeZ() + margin) {
				return false;
			}
		}
		return true;
	}

	private static boolean inside(BoundingBox box, BlockPos p, int margin) {
		return p.getX() >= box.minX() - margin && p.getX() <= box.maxX() + margin && p.getZ() >= box.minZ() - margin && p.getZ() <= box.maxZ() + margin;
	}

	private static double horizontalDistSqr(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}

	/** From a crown top (leaves or log), the trunk log nearest below it that stands on the ground. */
	private static @Nullable BlockPos findTrunk(ServerLevel level, BlockPos top) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dy = 0; dy <= 40; dy++) {
			for (int r = 0; r <= 2; r++) {
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
							continue;
						}
						p.set(top.getX() + dx, top.getY() - dy, top.getZ() + dz);
						if (!level.isLoaded(p)) {
							continue;
						}
						if (level.getBlockState(p).is(BlockTags.LOGS)) {
							// down the trunk to the stump
							BlockPos.MutableBlockPos q = p.mutable();
							for (int i = 0; i < 48 && level.getBlockState(q.below()).is(BlockTags.LOGS); i++) {
								q.move(Direction.DOWN);
							}
							return level.getBlockState(q.below()).is(BlockTags.DIRT) ? q.immutable() : null;
						}
					}
				}
			}
		}
		return null;
	}

	/**
	 * Gathers a whole tree from its stump, or null if it must not be felled (too big, touched by something built, no
	 * natural crown, or reaching into unloaded chunks).
	 */
	public static @Nullable Tree collect(ServerLevel level, BlockPos stump, int maxLogs) {
		BlockState trunkState = level.getBlockState(stump);
		if (!trunkState.is(BlockTags.LOGS)) {
			return null;
		}
		Set<Long> logSet = new HashSet<>();
		List<BlockPos> logs = new ArrayList<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(stump);
		logSet.add(stump.asLong());
		BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
		int naturalLeaves = 0;
		List<BlockPos> extras = new ArrayList<>();
		while (!queue.isEmpty()) {
			BlockPos p = queue.poll();
			logs.add(p);
			if (logs.size() > maxLogs) {
				return null;
			}
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (dx == 0 && dy == 0 && dz == 0) {
							continue;
						}
						n.set(p.getX() + dx, p.getY() + dy, p.getZ() + dz);
						if (!level.isLoaded(n)) {
							return null;
						}
						if (Math.abs(n.getX() - stump.getX()) > CROWN + 4 || Math.abs(n.getZ() - stump.getZ()) > CROWN + 4 || n.getY() < stump.getY()) {
							continue;
						}
						BlockState st = level.getBlockState(n);
						boolean face = Math.abs(dx) + Math.abs(dy) + Math.abs(dz) == 1;
						if (st.is(BlockTags.LOGS)) {
							if (logSet.add(n.asLong())) {
								queue.add(n.immutable());
							}
						} else if (isNaturalLeaves(st)) {
							naturalLeaves++;
						} else if (face && (st.getBlock() instanceof VineBlock || st.getBlock() instanceof CocoaBlock)) {
							extras.add(n.immutable());
						} else if (face && built(st)) {
							// something someone made touches it: a treehouse, a sign, a hive
							return null;
						}
					}
				}
			}
		}
		if (naturalLeaves < 4) {
			// logs with no living crown: a stack of logs, a log fence, a dead stump someone left on purpose
			return null;
		}
		List<BlockPos> stumps = new ArrayList<>();
		for (BlockPos p : logs) {
			if (p.getY() == stump.getY() && level.getBlockState(p.below()).is(BlockTags.DIRT)) {
				stumps.add(p);
			}
		}
		List<BlockPos> leaves = leavesOf(level, logs, logSet);
		logs.sort(Comparator.comparingInt((BlockPos b) -> b.getY()).reversed());
		leaves.sort(Comparator.comparingInt((BlockPos b) -> b.getY()).reversed());
		return new Tree(stump, stumps, logs, leaves, extras, trunkState.getBlock());
	}

	/** The tree's own natural leaves: reached through leaves from its logs, never through a leaf touching another tree. */
	private static List<BlockPos> leavesOf(ServerLevel level, List<BlockPos> logs, Set<Long> logSet) {
		Set<Long> seen = new HashSet<>();
		List<BlockPos> out = new ArrayList<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		ArrayDeque<Integer> depth = new ArrayDeque<>();
		for (BlockPos log : logs) {
			for (Direction d : Direction.values()) {
				BlockPos l = log.relative(d);
				if (seen.add(l.asLong()) && level.isLoaded(l) && isNaturalLeaves(level.getBlockState(l))) {
					queue.add(l);
					depth.add(1);
				}
			}
		}
		while (!queue.isEmpty() && out.size() < MAX_LEAVES) {
			BlockPos p = queue.poll();
			int dep = depth.poll();
			if (touchesForeignLog(level, p, logSet)) {
				continue;
			}
			out.add(p);
			if (dep >= 6) {
				continue;
			}
			for (Direction d : Direction.values()) {
				BlockPos l = p.relative(d);
				if (seen.add(l.asLong()) && level.isLoaded(l) && isNaturalLeaves(level.getBlockState(l))) {
					queue.add(l);
					depth.add(dep + 1);
				}
			}
		}
		return out;
	}

	private static boolean touchesForeignLog(ServerLevel level, BlockPos leaf, Set<Long> logSet) {
		for (Direction d : Direction.values()) {
			BlockPos n = leaf.relative(d);
			if (!logSet.contains(n.asLong()) && level.isLoaded(n) && level.getBlockState(n).is(BlockTags.LOGS)) {
				return true;
			}
		}
		return false;
	}

	public static boolean isNaturalLeaves(BlockState st) {
		return st.is(BlockTags.LEAVES) && st.hasProperty(LeavesBlock.PERSISTENT) && !st.getValue(LeavesBlock.PERSISTENT);
	}

	/** Blocks only people make: planks, glass, torches, doors, anything with a block entity. */
	static boolean built(BlockState st) {
		return st.hasBlockEntity() || st.is(BlockTags.PLANKS) || st.is(BlockTags.WOODEN_STAIRS) || st.is(BlockTags.WOODEN_SLABS)
			|| st.is(BlockTags.FENCES) || st.is(BlockTags.WALLS) || st.is(BlockTags.DOORS) || st.is(BlockTags.TRAPDOORS) || st.is(BlockTags.WOOL)
			|| st.is(BlockTags.BEDS) || st.is(Blocks.GLASS) || st.is(Blocks.GLASS_PANE) || st.is(Blocks.TORCH) || st.is(Blocks.WALL_TORCH)
			|| st.is(Blocks.LANTERN) || st.is(Blocks.LADDER) || st.is(Blocks.SCAFFOLDING) || st.is(Blocks.IRON_CHAIN);
	}

	/** The sapling a felled trunk's species grows from ({@code oak_log} → {@code oak_sapling}, Expanse woods alike). */
	public static Optional<Block> saplingFor(Block log) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(log);
		String path = id.getPath().replace("stripped_", "");
		String base = path.replaceAll("_(log|wood|stem|hyphae)$", "");
		for (String candidate : new String[]{base + "_sapling", base + "_propagule", base + "_fungus"}) {
			Optional<Block> b = BuiltInRegistries.BLOCK.getOptional(Identifier.fromNamespaceAndPath(id.getNamespace(), candidate));
			if (b.isPresent() && b.get() != Blocks.AIR) {
				return b;
			}
		}
		return Optional.empty();
	}
}
