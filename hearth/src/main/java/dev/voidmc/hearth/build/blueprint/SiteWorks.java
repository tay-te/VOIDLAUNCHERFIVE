package dev.voidmc.hearth.build.blueprint;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.kit.lawhouse.Timber;
import dev.voidmc.hearth.build.terrain.Site;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * The works of a building site: what stands on a plot while it is being built and is gone when it is done. A plot the
 * sim is building shows its finished stages, and a stage can wait days for its goods (SIM.md §4); without these a
 * house with its walls up and no roof is a roofless shell with empty window holes, and a town with a dozen of them reads
 * as a burnt-out quarter (art review 2 §3, §8 item 8). With them it reads as a site:
 * <ul>
 *   <li>a hurdle fence round the plot, open where the street door and the paths come in, and a pile of the building's
 *   materials by the door (hewn timbers, a stack of the wall stone), up from the foundation stage, down with the
 *   last;</li>
 *   <li>scaffolding round the rising walls: poles a block out from the wall at the corners and every third block along
 *   it, with putlogs into the wall and a lift of boards every three blocks up, up with the frame (or the walls), down
 *   once the roof is on, before the windows go in; in front of a house built to its frontage line (a town home) a flying
 *   scaffold over the street from the first lift up, the street under it left free;</li>
 *   <li>a temporary board roof over the wall heads while the roof stage waits, taken off as the roof goes up.</li>
 * </ul>
 * The works are steps like any other (the applier writes them, an embodied builder puts them up and takes them down), but
 * they are not drawn: every one stands where the finished building has air, or something of a later stage that
 * replaces it, and each is taken away in a later stage by a step of air (cave air, {@link #TAKEN_DOWN}, where the
 * drawing places nothing, over the street), so the finished
 * blueprint is the drawing (the checks, the audit and the built view read the last step at each position). They cost work but no goods: a
 * builder's poles and boards go from site to site.
 *
 * <p>Only an ordinary building gets them: one with walls and a roof, not built in campaigns (castles, excavations), and
 * not a roofless thing (a field, a yard, a bridge).
 */
final class SiteWorks {
	private SiteWorks() {
	}

	/** One step of the works: a block put up (or, {@code state} air, taken down) in a stage. */
	record Work(int x, int y, int z, BlockState state, StageKind stage) {
	}

	/**
	 * What a work leaves when it is taken down: cave air, which is air in the world in every way, so a reader of steps
	 * can tell a work's place from air the drawing places ({@code BlueprintChecks} counts neither as the building's).
	 */
	static final BlockState TAKEN_DOWN = Blocks.CAVE_AIR.defaultBlockState();

	private static final int MIN_WALL_CELLS = 24;
	private static final int MIN_ROOF_CELLS = 8;
	/** Height of a scaffold lift: boards every third block, a storey's working height. */
	private static final int LIFT = 3;

	/** The works for a finished draft (after shapes are resolved), or none. */
	static List<Work> plan(Draft d) {
		List<Work> out = new ArrayList<>();
		if (d.inCampaigns()) {
			return out;
		}
		EnumSet<StageKind> present = EnumSet.noneOf(StageKind.class);
		// the wall columns: what the walls (and a timber frame) stand in, and how high each rises
		Long2IntOpenHashMap wallTop = new Long2IntOpenHashMap();
		wallTop.defaultReturnValue(Integer.MIN_VALUE);
		int[] counts = new int[2];
		Map<BlockState, Integer> wallStates = new HashMap<>();
		d.forEach((x, y, z, c) -> {
			present.add(c.stage());
			if (c.state().isAir() || y < 1) {
				return;
			}
			if (c.stage() == StageKind.WALLS || c.stage() == StageKind.FRAME) {
				long k = col(x, z);
				wallTop.put(k, Math.max(wallTop.get(k), y));
				counts[0]++;
				if (c.stage() == StageKind.WALLS && c.state().isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
					wallStates.merge(c.state(), 1, Integer::sum);
				}
			} else if (c.stage() == StageKind.ROOF) {
				counts[1]++;
			}
		});
		if (counts[0] < MIN_WALL_CELLS || counts[1] < MIN_ROOF_CELLS || wallTop.isEmpty()) {
			return out;
		}
		int w = d.frontage();
		int dp = d.depth();
		// the eave: the height most wall columns stop at (gables rise over it)
		Map<Integer, Integer> tops = new HashMap<>();
		for (int t : wallTop.values()) {
			tops.merge(t, 1, Integer::sum);
		}
		int eave = tops.entrySet().stream().max(Map.Entry.<Integer, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
			.orElseThrow().getKey();
		// outside: the columns reached from the plot's edge without crossing a wall
		boolean[] outside = outside(d, wallTop, w, dp);

		StageKind poleStage = present.contains(StageKind.FRAME) ? StageKind.FRAME : StageKind.WALLS;
		StageKind deckStage = present.contains(StageKind.FLOORS) ? StageKind.FLOORS : StageKind.WALLS;
		StageKind poleDown = StageKind.EXTERIOR;
		for (StageKind k : new StageKind[]{StageKind.OPENINGS, StageKind.INTERIOR}) {
			if (present.contains(k)) {
				poleDown = k;
				break;
			}
		}
		Timber frame = Timber.frame(d);
		Timber boards = Timber.joinery(d);
		LongOpenHashSet used = new LongOpenHashSet();

		Long2IntOpenHashMap hurdles = hurdles(d, outside, w, dp);
		scaffold(d, out, used, wallTop, outside, w, dp, frame, boards, poleStage, poleDown, hurdles);
		deck(d, out, used, wallTop, outside, eave, w, dp, boards, deckStage);
		fence(d, out, used, hurdles, w, dp, frame);
		BlockState stone = wallStates.entrySet().stream()
			.max(Map.Entry.<BlockState, Integer>comparingByValue().thenComparing(e -> e.getKey().toString())).map(Map.Entry::getKey).orElse(null);
		pile(d, out, used, outside, w, dp, frame, stone);
		// nothing outside the plot or over its height (the street only from 3 up): a work there goes with its removal
		LongOpenHashSet barred = new LongOpenHashSet();
		for (Work k : out) {
			if (!d.allowed(k.x(), k.y(), k.z())) {
				barred.add(BlockPos.asLong(k.x(), k.y(), k.z()));
			}
		}
		if (!barred.isEmpty()) {
			out.removeIf(k -> barred.contains(BlockPos.asLong(k.x(), k.y(), k.z())));
		}
		return out;
	}

	private static long col(int x, int z) {
		return BlockPos.asLong(x, 0, z);
	}

	/** Columns of the plot reached from its edge through columns without wall (a flood over the plan). */
	private static boolean[] outside(Draft d, Long2IntOpenHashMap wallTop, int w, int dp) {
		boolean[] out = new boolean[w * dp];
		java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
		for (int x = 0; x < w; x++) {
			for (int z = 0; z < dp; z++) {
				if ((x == 0 || z == 0 || x == w - 1 || z == dp - 1) && !wallTop.containsKey(col(x, z))) {
					out[x * dp + z] = true;
					q.add(new int[]{x, z});
				}
			}
		}
		while (!q.isEmpty()) {
			int[] c = q.poll();
			for (Direction s : Dirs.HORIZONTAL) {
				int nx = c[0] + Dirs.dx(s);
				int nz = c[1] + Dirs.dz(s);
				if (nx < 0 || nz < 0 || nx >= w || nz >= dp || out[nx * dp + nz] || wallTop.containsKey(col(nx, nz))) {
					continue;
				}
				out[nx * dp + nz] = true;
				q.add(new int[]{nx, nz});
			}
		}
		return out;
	}

	/**
	 * Free in the finished building: air drawn there (the clearing, a room's volume). A work never goes where the drawing
	 * leaves the site alone: not into the hillside (taking it down would leave a hole), and not into open air the drawing
	 * never touched, so the finished blueprint places exactly the cells the drawing does (the checks read "placed" off
	 * the steps). Over the street, in front of the frontage, nothing is drawn: there the works may go from 3 up.
	 */
	private static boolean free(Draft d, int x, int y, int z) {
		Draft.Cell c = d.cell(x, y, z);
		return c == null ? street(d, x, y, z) : c.state().isAir();
	}

	/** Free, or drawn in a stage after {@code stage} (whose block then replaces the work's). */
	private static boolean freeUntil(Draft d, int x, int y, int z, StageKind stage) {
		Draft.Cell c = d.cell(x, y, z);
		return c == null ? street(d, x, y, z) : c.state().isAir() || c.stage().ordinal() > stage.ordinal();
	}

	private static boolean street(Draft d, int x, int y, int z) {
		return z < 0 && y >= 3 && d.allowed(x, y, z);
	}

	private static boolean solid(@Nullable BlockState s) {
		return s != null && !s.isAir() && !s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
	}

	/** The standing level of a column in the finished site: the natural ground, as filled or dug by the draft. */
	private static int stand(Draft d, int x, int z) {
		Site site = d.site();
		int y = site.ground(x, z);
		if (site.isWater(x, z)) {
			return Integer.MIN_VALUE;
		}
		// filled: stand on the fill (earthwork, a foundation's footing; a wall or a stand is not ground)
		for (int i = 0; i < 4; i++) {
			Draft.Cell c = d.cell(x, y, z);
			if (c != null && solid(c.state()) && (c.stage() == StageKind.EARTHWORK || c.stage() == StageKind.FOUNDATION || c.stage() == StageKind.CLEAR)) {
				y++;
			} else {
				break;
			}
		}
		if (solid(d.get(x, y, z))) {
			return Integer.MIN_VALUE;
		}
		// dug: stand in the cut
		for (int i = 0; i < 8; i++) {
			BlockState below = d.get(x, y - 1, z);
			if (below != null && below.isAir()) {
				y--;
			} else {
				break;
			}
		}
		BlockState below = d.get(x, y - 1, z);
		if (below != null && !solid(below)) {
			return Integer.MIN_VALUE;
		}
		return y;
	}

	/** Whether the ground under a standing level is a path, a yard or a garden the finished plot lays (keep off it). */
	private static boolean laid(Draft d, int x, int y, int z) {
		Draft.Cell c = d.cell(x, y - 1, z);
		return c != null && c.stage() == StageKind.EXTERIOR && !c.state().isAir();
	}

	/** The step that takes a work down: air where the drawing places air, else {@link #TAKEN_DOWN}. */
	private static BlockState takenDown(Draft d, int x, int y, int z) {
		return d.cell(x, y, z) == null ? TAKEN_DOWN : Blocks.AIR.defaultBlockState();
	}

	private static void add(Draft d, List<Work> out, LongOpenHashSet used, int x, int y, int z, BlockState s, StageKind up, StageKind down) {
		out.add(new Work(x, y, z, s, up));
		out.add(new Work(x, y, z, takenDown(d, x, y, z), down));
		used.add(BlockPos.asLong(x, y, z));
	}

	private static BooleanProperty side(Direction s) {
		return switch (s) {
			case NORTH -> BlockStateProperties.NORTH;
			case SOUTH -> BlockStateProperties.SOUTH;
			case EAST -> BlockStateProperties.EAST;
			default -> BlockStateProperties.WEST;
		};
	}

	private static @Nullable BlockState fence(Timber t, int x, int y, int z) {
		BlockState s = t.fence().at(x, y, z);
		return s != null && s.getBlock() instanceof FenceBlock ? s : Blocks.SPRUCE_FENCE.defaultBlockState();
	}

	private static BlockState board(Timber t, int x, int y, int z) {
		BlockState s = t.slab(SlabType.BOTTOM).at(x, y, z);
		return s != null && s.getBlock() instanceof SlabBlock ? s : Blocks.SPRUCE_SLAB.defaultBlockState();
	}

	/**
	 * Scaffolding round the walls: on the outside columns next to a wall, a pole at each corner and every third block
	 * along a face, a putlog from the pole into the wall at every lift, and boards along the face at every lift.
	 */
	private static void scaffold(Draft d, List<Work> out, LongOpenHashSet used, Long2IntOpenHashMap wallTop, boolean[] outside, int w, int dp,
		Timber frame, Timber boards, StageKind up, StageKind down, Long2IntOpenHashMap hurdles) {
		for (int x = 0; x < w; x++) {
			// z -1: over the street, in front of a house built to its frontage line (a town home): a flying scaffold from the
			// first lift up, its putlogs carrying it, the street under it free (the plot may overhang it only from 3 up)
			for (int z = -1; z < dp; z++) {
				boolean street = z < 0;
				if (!street && (!outside[x * dp + z] || wallTop.containsKey(col(x, z)))) {
					continue;
				}
				// the wall beside it: faces (sharing a side) and corners (a diagonal only)
				Direction face = null;
				int top = Integer.MIN_VALUE;
				boolean alongX = false;
				boolean alongZ = false;
				for (Direction s : Dirs.HORIZONTAL) {
					int t = wallTop.get(col(x + Dirs.dx(s), z + Dirs.dz(s)));
					if (t != Integer.MIN_VALUE) {
						if (face == null) {
							face = s;
						}
						top = Math.max(top, t);
						alongX |= Dirs.dz(s) != 0;
						alongZ |= Dirs.dx(s) != 0;
					}
				}
				boolean corner = false;
				if (face == null) {
					for (int[] o : new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
						int t = wallTop.get(col(x + o[0], z + o[1]));
						if (t != Integer.MIN_VALUE) {
							corner = true;
							top = Math.max(top, t);
						}
					}
					if (!corner) {
						continue;
					}
				}
				int g = street ? LIFT : stand(d, x, z);
				if (g == Integer.MIN_VALUE || top - g < (street ? 2 : 3)) {
					continue;
				}
				boolean pole = corner || alongX && Math.floorMod(x, LIFT) == 0 || alongZ && Math.floorMod(z, LIFT) == 0 || alongX && alongZ;
				if (pole) {
					// a pole as high as the wall it serves, as far as it stands clear (an eave or a balcony stops it)
					int y = g;
					while (y <= top && y < d.maxHeight() - 1 && free(d, x, y, z) && d.allowed(x, y, z)) {
						y++;
					}
					int ptop = y - 1;
					if (ptop - g < 2) {
						continue;
					}
					for (int py = g; py <= ptop; py++) {
						BlockState f = fence(frame, x, py, z);
						if (face != null && py > g && Math.floorMod(py, LIFT) == 0 && py < top) {
							// a putlog: the pole's arm into the wall it serves
							int wx = x + Dirs.dx(face);
							int wz = z + Dirs.dz(face);
							if (!free(d, wx, py, wz)) {
								f = f.setValue(side(face), true);
							}
						}
						if (py == g && hurdles.get(col(x, z)) == g) {
							// a pole on the fence line is a post of the fence too
							for (Direction s : Dirs.HORIZONTAL) {
								if (hurdles.get(col(x + Dirs.dx(s), z + Dirs.dz(s))) == g) {
									f = f.setValue(side(s), true);
								}
							}
						}
						add(d, out, used, x, py, z, f, up, down);
					}
				} else if (face != null) {
					// boards along the face at every lift, as far as the column stands clear below them
					for (int ly = LIFT; ly < top; ly += LIFT) {
						if (ly < g || !street && ly - g < 2 || ly >= d.maxHeight()) {
							continue;
						}
						boolean clear = true;
						for (int y = g; y <= ly; y++) {
							if (!free(d, x, y, z) || !d.allowed(x, y, z)) {
								clear = false;
								break;
							}
						}
						if (clear) {
							add(d, out, used, x, ly, z, board(boards, x, ly, z), up, down);
						}
					}
				}
			}
		}
	}

	/**
	 * A board roof over the wall heads while the roof waits: bottom slabs one block over the eave across the walls that
	 * stop there and everything inside them, where nothing of the walls or floors stands, lifted as the roof goes on.
	 */
	private static void deck(Draft d, List<Work> out, LongOpenHashSet used, Long2IntOpenHashMap wallTop, boolean[] outside, int eave, int w, int dp,
		Timber boards, StageKind up) {
		// the roofed columns, and the lowest roof block over each: a flat roof's deck lies under its parapets, so the
		// boards go where it will (the commonest lowest roof block) when that is under the eave
		Long2IntOpenHashMap roofLow = new Long2IntOpenHashMap();
		roofLow.defaultReturnValue(Integer.MAX_VALUE);
		d.forEach((x, y, z, c) -> {
			if (c.stage() == StageKind.ROOF && !c.state().isAir() && y >= 1) {
				long k = col(x, z);
				roofLow.put(k, Math.min(roofLow.get(k), y));
			}
		});
		Map<Integer, Integer> lows = new HashMap<>();
		for (var e : roofLow.long2IntEntrySet()) {
			int x = BlockPos.getX(e.getLongKey());
			int z = BlockPos.getZ(e.getLongKey());
			if (x >= 0 && z >= 0 && x < w && z < dp && !outside[x * dp + z] && !wallTop.containsKey(e.getLongKey())) {
				lows.merge(e.getIntValue(), 1, Integer::sum);
			}
		}
		int y = eave + 1;
		int flat = lows.entrySet().stream().max(Map.Entry.<Integer, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
			.map(Map.Entry::getKey).orElse(y);
		y = Math.min(y, flat);
		if (y >= d.maxHeight() || y < 2) {
			return;
		}
		for (int x = 0; x < w; x++) {
			for (int z = 0; z < dp; z++) {
				int t = wallTop.get(col(x, z));
				boolean inside = !outside[x * dp + z] && t == Integer.MIN_VALUE;
				if (!(inside || t != Integer.MIN_VALUE && t < y) || !roofLow.containsKey(col(x, z)) || !freeUntil(d, x, y, z, up)) {
					continue;
				}
				Draft.Cell c = d.cell(x, y, z);
				// a roof block of the finished house takes the board's place; elsewhere the board is lifted with the roof
				boolean roofed = c != null && !c.state().isAir() && c.stage() == StageKind.ROOF;
				out.add(new Work(x, y, z, board(boards, x, y, z), up));
				if (!roofed) {
					out.add(new Work(x, y, z, takenDown(d, x, y, z), StageKind.ROOF));
				}
				used.add(BlockPos.asLong(x, y, z));
			}
		}
	}

	/**
	 * Where the hurdle fence runs: the plot's edge where the ground outside the building is free, open in front of every
	 * door that gives on the street and wherever the finished plot lays a path or a yard. Column to standing level.
	 */
	private static Long2IntOpenHashMap hurdles(Draft d, boolean[] outside, int w, int dp) {
		LongOpenHashSet gaps = new LongOpenHashSet();
		for (int[] e : d.entrances()) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					gaps.add(col(e[0] + dx, e[2] + dz));
					gaps.add(col(e[0] + dx, Math.max(0, e[2]) + dz));
				}
			}
		}
		Long2IntOpenHashMap at = new Long2IntOpenHashMap();
		at.defaultReturnValue(Integer.MIN_VALUE);
		for (int x = 0; x < w; x++) {
			for (int z = 0; z < dp; z++) {
				if (!(x == 0 || z == 0 || x == w - 1 || z == dp - 1) || !outside[x * dp + z] || gaps.contains(col(x, z))) {
					continue;
				}
				int g = stand(d, x, z);
				if (g == Integer.MIN_VALUE || !free(d, x, g, z) || !free(d, x, g + 1, z) || laid(d, x, g, z)) {
					continue;
				}
				at.put(col(x, z), g);
			}
		}
		return at;
	}

	/** The hurdle fence along {@link #hurdles}, its posts joined; a scaffold pole standing on the line is one of them. */
	private static void fence(Draft d, List<Work> out, LongOpenHashSet used, Long2IntOpenHashMap at, int w, int dp, Timber frame) {
		for (int x = 0; x < w; x++) {
			for (int z = 0; z < dp; z++) {
				int g = at.get(col(x, z));
				if (g == Integer.MIN_VALUE || used.contains(BlockPos.asLong(x, g, z))) {
					continue;
				}
				BlockState f = fence(frame, x, g, z);
				int links = 0;
				for (Direction s : Dirs.HORIZONTAL) {
					if (at.get(col(x + Dirs.dx(s), z + Dirs.dz(s))) == g) {
						f = f.setValue(side(s), true);
						links++;
					}
				}
				if (links == 0) {
					// a lone post is no fence
					continue;
				}
				add(d, out, used, x, g, z, f, StageKind.FOUNDATION, StageKind.EXTERIOR);
			}
		}
	}

	/**
	 * The materials by the door: a pair of hewn timbers on top of each other and a stack of the wall stone with a slab
	 * on it, side by side on free ground a little to one side of the main door, off its path.
	 */
	private static void pile(Draft d, List<Work> out, LongOpenHashSet used, boolean[] outside, int w, int dp, Timber frame, @Nullable BlockState stone) {
		int[] main = d.entrances().isEmpty() ? new int[]{w / 2, 0, -1} : d.entrances().get(0);
		int mx = main[0];
		int mz = Math.max(0, main[2]);
		for (int r = 2; r <= 6; r++) {
			for (int dz = 0; dz <= 3; dz++) {
				for (int sgn : new int[]{-1, 1}) {
					int x = mx + sgn * r;
					int z = mz + dz;
					int x2 = x + sgn;
					if (pileAt(d, used, outside, w, dp, x, z, x2, z)) {
						place(d, out, used, frame, stone, x, z, x2, z, Direction.Axis.X);
						return;
					}
				}
			}
		}
	}

	private static boolean pileAt(Draft d, LongOpenHashSet used, boolean[] outside, int w, int dp, int x, int z, int x2, int z2) {
		for (int[] c : new int[][]{{x, z}, {x2, z2}}) {
			if (c[0] < 1 || c[1] < 0 || c[0] >= w - 1 || c[1] >= dp - 1 || !outside[c[0] * dp + c[1]]) {
				return false;
			}
		}
		int g = stand(d, x, z);
		if (g == Integer.MIN_VALUE || stand(d, x2, z2) != g) {
			return false;
		}
		for (int[] c : new int[][]{{x, z}, {x2, z2}}) {
			for (int y = g; y <= g + 2; y++) {
				if (!free(d, c[0], y, c[1]) || used.contains(BlockPos.asLong(c[0], y, c[1]))) {
					return false;
				}
			}
			if (laid(d, c[0], g, c[1])) {
				return false;
			}
			// keep a free cell round it toward the building, so nobody is boxed in
			for (Direction s : Dirs.HORIZONTAL) {
				if (used.contains(BlockPos.asLong(c[0] + Dirs.dx(s), g, c[1] + Dirs.dz(s)))) {
					return false;
				}
			}
		}
		return true;
	}

	private static void place(Draft d, List<Work> out, LongOpenHashSet used, Timber frame, @Nullable BlockState stone, int x, int z, int x2, int z2,
		Direction.Axis axis) {
		int g = stand(d, x, z);
		Direction.Axis lying = axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
		BlockState log = frame.hewn(lying).at(x, g, z);
		if (log != null) {
			add(d, out, used, x, g, z, log, StageKind.FOUNDATION, StageKind.EXTERIOR);
			add(d, out, used, x, g + 1, z, log, StageKind.FOUNDATION, StageKind.EXTERIOR);
		}
		if (stone != null) {
			add(d, out, used, x2, g, z2, stone, StageKind.FOUNDATION, StageKind.EXTERIOR);
			BlockState slab = dev.voidmc.hearth.build.kit.Chimneys.slabOf(stone.getBlock());
			if (slab != null) {
				add(d, out, used, x2, g + 1, z2, slab, StageKind.FOUNDATION, StageKind.EXTERIOR);
			}
		}
	}
}
