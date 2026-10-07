package dev.voidmc.hearth.build.kit.landscape;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.light.Spaces;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * Guards on walkable edges, the last thing a builder does before the scaffolding comes down: wherever someone who
 * walked in from the street stands on a floor, a deck, a forecourt or a terrace beside a drop of {@value #DROP} or
 * more, a rail (the house's railing, or a low wall of the masonry on open ground) stands on that edge. The way in is
 * walked as the folk walk (a step up of one, down of three, ladders), over the draft and the natural ground under it.
 *
 * <p>Left open: doorways and the cells before them, the way in from the street, stair treads and steps, ladder heads,
 * the cells folk stand in to use a bed, a chest or a job block (and every POI). A walk one wide (a veranda, a gallery)
 * is not closed by its rail: its deck is carried out a board further and the rail stands on that. A chest or a shelf
 * someone could climb beside a stairwell gets its rail in the well beside it, or a jug on its top where the drop is
 * past the plot.
 */
public final class Edges {
	private Edges() {
	}

	/** The fall a rail guards against: open cells under the next one's feet. */
	public static final int DROP = 3;

	/** Guards every edge walked to in the plot, at any height; returns how many rails it set. */
	public static int guard(Draft d) {
		return guard(d, -24, d.maxHeight());
	}

	/** Guards the whole plot's edges at standing levels {@code y0..y1}; returns how many rails it set. */
	public static int guard(Draft d, int y0, int y1) {
		return guard(d, 0, 0, d.frontage() - 1, d.depth() - 1, y0, y1, null);
	}

	/**
	 * Guards the edges of the cells in {@code [x0..x1] × [z0..z1]} at standing levels {@code y0..y1} with {@code rail}
	 * (default: the railing's fence, or a wall of the masonry where the cell stands on the ground or a paved terrace).
	 * Returns how many rails it set.
	 */
	public static int guard(Draft d, int x0, int z0, int x1, int z1, int y0, int y1, @Nullable Paint rail) {
		LongOpenHashSet reached = walk(d);
		if (glazeLattices(d, reached, x0, z0, x1, z1, y0, y1) > 0) {
			reached = walk(d);
		}
		List<int[]> edges = new ArrayList<>();
		LongOpenHashSet edgeSet = new LongOpenHashSet();
		for (long k : reached) {
			int x = Draft.keyX(k);
			int y = Draft.keyY(k);
			int z = Draft.keyZ(k);
			if (x < x0 || x > x1 || z < z0 || z > z1 || y < y0 || y > y1 || !d.allowed(x, y, z)) {
				continue;
			}
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				int nx = x + dir.getStepX();
				int nz = z + dir.getStepZ();
				if (passable(d, nx, y, nz) && passable(d, nx, y + 1, nz) && drop(d, nx, y, nz) >= DROP) {
					edges.add(new int[]{x, y, z});
					edgeSet.add(Draft.key(x, y, z));
					break;
				}
			}
		}
		LongOpenHashSet ways = new LongOpenHashSet();
		for (int[] e : d.entrances()) {
			// the way in itself (the door before it and the stairs to it keep themselves open)
			for (int dy = -2; dy <= 1; dy++) {
				ways.add(Draft.key(e[0], e[1] + dy, e[2]));
			}
		}
		// where folk stand to use something (a chest's front, a bench, a job site) stays clear
		for (Draft.LocalPoi p : d.pois()) {
			ways.add(Draft.key(p.x(), p.y(), p.z()));
		}
		int n = 0;
		String dbg = System.getProperty("hearth.edges.debug", "");
		boolean trace = !dbg.isEmpty() && java.util.Arrays.stream(dbg.split(",")).anyMatch(d.spec().archetype().getPath()::equals);
		if (trace) {
			System.out.println("EDGES " + d.spec().archetype() + " " + d.spec().style() + " " + d.spec().seed() + " reached " + reached.size() + " edges " + edges.size());
		}
		List<int[]> lastResort = new ArrayList<>();
		for (int[] e : edges) {
			int x = e[0];
			int y = e[1];
			int z = e[2];
			BlockState under = d.get(x, y - 1, z);
			boolean onFurniture = under != null && furniture(under);
			if (trace) {
				System.out.println("  edge " + x + "/" + y + "/" + z + " way=" + ways.contains(Draft.key(x, y, z)) + " open=" + keepsOpen(d, x, y, z) + " under=" + under);
			}
			if (ways.contains(Draft.key(x, y, z))) {
				// the way in (a landing before the door, the head of its flight): rails beside it over the drop, never on it
				n += sideRails(d, ways, x, y, z, rail);
				continue;
			}
			BlockState here = d.get(x, y, z);
			if (here != null && here.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock
				&& here.getValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN) && d.allowed(x, y, z)) {
				Direction f = here.getValue(net.minecraft.world.level.block.TrapDoorBlock.FACING);
				Direction side = f.getClockWise();
				boolean opening = !passable(d, x + side.getStepX(), y, z + side.getStepZ()) && !passable(d, x - side.getStepX(), y, z - side.getStepZ())
					|| !passable(d, x + f.getStepX(), y, z + f.getStepZ()) && !passable(d, x - f.getStepX(), y, z - f.getStepZ());
				int lo = y;
				while (d.get(x, lo - 1, z) != null && d.get(x, lo - 1, z).getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock) {
					lo--;
				}
				int hi = y;
				while (d.get(x, hi + 1, z) != null && d.get(x, hi + 1, z).getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock) {
					hi++;
				}
				BlockState post = d.get(x, lo - 1, z);
				if (post != null && post.getBlock() instanceof net.minecraft.world.level.block.RotatedPillarBlock
					&& post.getValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS) == Direction.Axis.Y) {
					// a lattice band carried round a corner post: the post goes on up through it
					for (int yy = lo; yy <= hi; yy++) {
						int fy = yy;
						d.inStage(StageKind.FRAME, () -> d.set(x, fy, z, post));
					}
				} else if (opening) {
					// a lattice in an opening someone could climb into over the drop: glazed instead
					Mat glass = d.mat(Role.GLASS);
					d.inStage(StageKind.OPENINGS, () -> d.set(x, y, z, glass.has(Form.PANE) ? glass.pane()
						: Paint.of(net.minecraft.world.level.block.Blocks.GLASS_PANE.defaultBlockState())));
				} else {
					// a pair of shutter leaves on a wall's face whose top someone could step onto over the drop: the window
					// goes unshuttered
					for (int dy = -2; dy <= 2; dy++) {
						BlockState leaf = d.get(x, y + dy, z);
						if (leaf != null && leaf.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock
							&& leaf.getValue(net.minecraft.world.level.block.TrapDoorBlock.FACING) == f && d.allowed(x, y + dy, z)) {
							d.remove(x, y + dy, z);
						}
					}
				}
				n++;
				continue;
			}
			if (!onFurniture && !keepsOpen(d, x, y, z)) {
				// on a stair or beside a door: nothing on the cell itself, but a balustrade may still stand in the open well
				// beside it (between the lanes of a switchback, over a stair's open side), or out of doors a rail on a board
				// beside it over the drop
				int railed = wellRail(d, ways, x, y, z, rail);
				n += railed > 0 ? railed : sideRails(d, ways, x, y, z, rail);
				continue;
			}
			// a floor behind the rail: some neighbour stays walkable and is no edge itself
			boolean behind = false;
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				long nk = Draft.key(x + dir.getStepX(), y, z + dir.getStepZ());
				behind |= reached.contains(nk) && !edgeSet.contains(nk);
			}
			if (onFurniture) {
				// someone stood on a chest or a shelf by a stairwell: the rail goes in the well beside it, at the floor's
				// standing level (where a stairwell's rail stands), clear of the treads' head room below; where the drop
				// is past the plot's edge, a jug or a candle stub on the top keeps feet off it (never on a bed)
				int before = n;
				for (Direction dir : Direction.Plane.HORIZONTAL) {
					int nx = x + dir.getStepX();
					int nz = z + dir.getStepZ();
					BlockState t = d.get(nx, y - 1, nz);
					if (d.allowed(nx, y - 1, nz) && (t == null || t.isAir()) && passable(d, nx, y - 1, nz) && passable(d, nx, y, nz)
						&& drop(d, nx, y, nz) >= DROP && drop(d, nx, y - 1, nz) >= 2) {
						Paint p = rail != null ? rail : railing(d);
						d.inStage(StageKind.INTERIOR, () -> d.set(nx, y - 1, nz, p));
						n++;
					}
				}
				if (n == before && d.isEmpty(x, y, z) && d.allowed(x, y, z) && !(under.getBlock() instanceof BedBlock)) {
					d.inStage(StageKind.INTERIOR, () -> d.set(x, y, z, ((x + z) & 1) == 0 ? net.minecraft.world.level.block.Blocks.FLOWER_POT.defaultBlockState()
						: net.minecraft.world.level.block.Blocks.CANDLE.defaultBlockState().setValue(net.minecraft.world.level.block.CandleBlock.LIT, false)));
					n++;
				}
				continue;
			}
			// indoors (a stairwell under the roof): the balustrade stands in the well itself at the floor's level, against
			// the wall beside it, clear of the treads' head room below
			int railed = wellRail(d, ways, x, y, z, rail);
			if (railed > 0) {
				n += railed;
				continue;
			}
			if (behind) {
				lastResort.add(e);
				continue;
			}
			// a walk one wide (a veranda, a gallery): the deck carried out one more board over the drop, the rail on that
			// outer board, so the walk keeps its width
			boolean carried = false;
			if (under != null && d.isSolid(x, y - 1, z)) {
				for (Direction dir : Direction.Plane.HORIZONTAL) {
					int nx = x + dir.getStepX();
					int nz = z + dir.getStepZ();
					if (d.allowed(nx, y - 1, nz) && d.allowed(nx, y, nz) && empty(d, nx, y - 1, nz) && empty(d, nx, y, nz) && empty(d, nx, y + 1, nz)
						&& drop(d, nx, y, nz) >= DROP && !ways.contains(Draft.key(nx, y, nz))) {
						Paint p = rail != null ? rail : railing(d);
						BlockState deck = under;
						d.inStage(StageKind.FLOORS, () -> d.set(nx, y - 1, nz, deck));
						d.inStage(StageKind.EXTERIOR, () -> d.set(nx, y, nz, p));
						n++;
						carried = true;
					}
				}
			}
			if (!carried) {
				lastResort.add(e);
			}
		}
		// a corner post carried up through a lattice band makes the jambs of the lattice beside it: glaze those now
		if (n > 0) {
			n += glazeLattices(d, walk(d), x0, z0, x1, z1, y0, y1);
		}
		// the rest railed on the edge itself, each only where the rail closes nothing but its own cell (every cell walked to
		// before is walked to after): a rail never cuts the way to a room
		if (!lastResort.isEmpty()) {
			int count = inPlot(d, walk(d));
			for (int[] e : lastResort) {
				int x = e[0];
				int y = e[1];
				int z = e[2];
				if (!d.isEmpty(x, y, z)) {
					continue;
				}
				Paint p = rail != null ? rail : railFor(d, x, y, z);
				d.inStage(StageKind.EXTERIOR, () -> d.set(x, y, z, p));
				int now = inPlot(d, walk(d));
				if (trace) {
					System.out.println("  last " + x + "/" + y + "/" + z + " count " + count + " now " + now);
				}
				if (now >= count) {
					count = now;
					n++;
				} else {
					d.remove(x, y, z);
				}
			}
		}
		return n;
	}

	/**
	 * Lattice windows (an open trapdoor in the opening, as the karst and the blossom vale build them) whose sill has a
	 * ledge or an eave outside it that a hand could climb out onto: the lower row is glazed behind its lattice instead.
	 * Returns how many it glazed.
	 */
	private static int glazeLattices(Draft d, LongOpenHashSet reached, int x0, int z0, int x1, int z1, int y0, int y1) {
		int n = 0;
		for (long k : reached) {
			int x = Draft.keyX(k);
			int y = Draft.keyY(k);
			int z = Draft.keyZ(k);
			BlockState s = d.get(x, y, z);
			if (x < x0 || x > x1 || z < z0 || z > z1 || y < y0 || y > y1 || s == null || !(s.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock)
				|| !s.getValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN)) {
				continue;
			}
			Direction f = s.getValue(net.minecraft.world.level.block.TrapDoorBlock.FACING);
			Direction side = f.getClockWise();
			// in a wall: solid either side across the opening, or the next leaf of a wider lattice; or a shutter leaf folded
			// back against its jamb (it faces along the wall, the opening beside it stands open)
			Direction through = f;
			if (!jamb(d, x + side.getStepX(), y, z + side.getStepZ(), f) || !jamb(d, x - side.getStepX(), y, z - side.getStepZ(), f)) {
				if (passable(d, x + f.getStepX(), y, z + f.getStepZ()) || passable(d, x - f.getStepX(), y, z - f.getStepZ())) {
					continue;
				}
				through = side;
			}
			// through it: a cell to stand on beyond (level with the sill or a drop of up to three under it) on an eave, a sill or
			// a porch roof (a stair or a slab), up off the ground, or at the brink of a drop
			boolean out = false;
			for (Direction dir : new Direction[]{through, through.getOpposite()}) {
				int nx = x + dir.getStepX();
				int nz = z + dir.getStepZ();
				// a lattice down to the floor (a raised house's) straight over the drop
				out = out || passable(d, nx, y, nz) && passable(d, nx, y + 1, nz) && drop(d, nx, y, nz) >= DROP;
				for (int dy = 0; dy >= -3 && !out; dy--) {
					int ny = y + dy;
					if (!walkable(d, nx, ny, nz)) {
						continue;
					}
					BlockState under = d.get(nx, ny - 1, nz);
					out = under != null && (under.getBlock() instanceof StairBlock || under.getBlock() instanceof SlabBlock) || ny > d.site().ground(nx, nz) + 1
						|| passable(d, nx + dir.getStepX(), ny, nz + dir.getStepZ()) && drop(d, nx + dir.getStepX(), ny, nz + dir.getStepZ()) >= DROP;
				}
			}
			if (out) {
				Mat glass = d.mat(Role.GLASS);
				d.inStage(StageKind.OPENINGS, () -> d.set(x, y, z, glass.has(Form.PANE) ? glass.pane() : Paint.of(net.minecraft.world.level.block.Blocks.GLASS_PANE.defaultBlockState())));
				n++;
			}
		}
		return n;
	}

	/**
	 * A balustrade in the open well beside walked cell (x, y, z), indoors, against a wall or another rail, where the drop
	 * under it leaves the treads below their head room. Returns how many it set.
	 */
	private static int wellRail(Draft d, LongOpenHashSet ways, int x, int y, int z, @Nullable Paint rail) {
		int n = 0;
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			int nx = x + dir.getStepX();
			int nz = z + dir.getStepZ();
			if (d.allowed(nx, y, nz) && empty(d, nx, y, nz) && empty(d, nx, y + 1, nz) && drop(d, nx, y, nz) >= DROP && roofed(d, nx, y, nz)
				&& braced(d, nx, y, nz, dir.getOpposite()) && !ways.contains(Draft.key(nx, y, nz))) {
				Paint p = rail != null ? rail : railing(d);
				d.inStage(StageKind.INTERIOR, () -> d.set(nx, y, nz, p));
				n++;
			}
		}
		return n;
	}

	/**
	 * Rails beside walked cell (x, y, z), which itself stays open (a way in, a stair tread, a door's cell): on every side
	 * where the next cell falls {@value #DROP} or more, a board under it and a rail on the board, kept only where every
	 * door and POI walked to before is still walked to after. Returns how many it set.
	 */
	private static int sideRails(Draft d, LongOpenHashSet ways, int x, int y, int z, @Nullable Paint rail) {
		int n = 0;
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			int nx = x + dir.getStepX();
			int nz = z + dir.getStepZ();
			if (!d.allowed(nx, y - 1, nz) || !d.allowed(nx, y, nz) || !empty(d, nx, y - 1, nz) || !empty(d, nx, y, nz) || !empty(d, nx, y + 1, nz)
				|| drop(d, nx, y, nz) < DROP || ways.contains(Draft.key(nx, y, nz))) {
				continue;
			}
			int before = inPlot(d, walk(d));
			Mat floor = d.mat(Role.FLOOR);
			Paint board = floor.has(Form.SLAB) ? floor.slab(SlabType.TOP) : floor.full();
			Paint p = rail != null ? rail : railing(d);
			d.inStage(StageKind.FLOORS, () -> d.set(nx, y - 1, nz, board));
			d.inStage(StageKind.EXTERIOR, () -> d.set(nx, y, nz, p));
			if (inPlot(d, walk(d)) < before) {
				d.remove(nx, y, nz);
				d.remove(nx, y - 1, nz);
			} else {
				n++;
			}
		}
		return n;
	}

	/** Beside a lattice: wall, or another lattice leaf facing the same way (a window two wide). */
	private static boolean jamb(Draft d, int x, int y, int z, Direction facing) {
		BlockState s = d.get(x, y, z);
		if (s != null && s.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock && s.getValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN)
			&& s.getValue(net.minecraft.world.level.block.TrapDoorBlock.FACING) == facing) {
			return true;
		}
		return !passable(d, x, y, z);
	}

	/** A block folk do not stand on by choice: furniture, stores, shelves, bales. */
	private static boolean furniture(BlockState s) {
		net.minecraft.world.level.block.Block b = s.getBlock();
		return used(s) || b == net.minecraft.world.level.block.Blocks.BOOKSHELF || b == net.minecraft.world.level.block.Blocks.CHISELED_BOOKSHELF
			|| b == net.minecraft.world.level.block.Blocks.HAY_BLOCK || b instanceof net.minecraft.world.level.block.DecoratedPotBlock
			|| s.is(net.minecraft.tags.BlockTags.WOOL) || net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).getPath().contains("wool");
	}

	private static Paint railing(Draft d) {
		Mat railing = d.mat(Role.RAILING);
		return railing.has(Form.FENCE) ? railing.fence() : d.mat(Role.FENCE).fence();
	}

	/** A cell a rail may take: no door, gate, stair or step under or beside it (a flight's foot or head), no ladder head. */
	private static boolean keepsOpen(Draft d, int x, int y, int z) {
		BlockState below = d.get(x, y - 1, z);
		if (below != null && (below.getBlock() instanceof StairBlock && below.getValue(StairBlock.HALF) == Half.BOTTOM
			|| below.getBlock() instanceof SlabBlock && below.getValue(SlabBlock.TYPE) == SlabType.BOTTOM || below.getBlock() instanceof LadderBlock)) {
			return false;
		}
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			for (int dy = -1; dy <= 0; dy++) {
				BlockState n = d.get(x + dir.getStepX(), y + dy, z + dir.getStepZ());
				if (n == null) {
					continue;
				}
				if (n.getBlock() instanceof DoorBlock || n.getBlock() instanceof FenceGateBlock || n.getBlock() instanceof LadderBlock
					|| n.getBlock() instanceof StairBlock && n.getValue(StairBlock.HALF) == Half.BOTTOM || dy == 0 && used(n)) {
					return false;
				}
			}
		}
		return true;
	}

	/** Something used from the cell beside it: a bed, a container, a job block. */
	private static boolean used(BlockState s) {
		return s.getBlock() instanceof BedBlock || s.getBlock() instanceof net.minecraft.world.level.block.BaseEntityBlock
			|| s.getBlock() instanceof net.minecraft.world.level.block.CraftingTableBlock || s.getBlock() instanceof net.minecraft.world.level.block.AnvilBlock
			|| s.getBlock() instanceof net.minecraft.world.level.block.CauldronBlock || s.getBlock() instanceof net.minecraft.world.level.block.LayeredCauldronBlock
			|| s.getBlock() instanceof net.minecraft.world.level.block.ComposterBlock || s.getBlock() instanceof net.minecraft.world.level.block.GrindstoneBlock
			|| s.getBlock() instanceof net.minecraft.world.level.block.StonecutterBlock || s.getBlock() instanceof net.minecraft.world.level.block.LoomBlock;
	}

	/** The railing's fence indoors and on decks; on the ground or a paved terrace, a wall of the masonry. */
	private static Paint railFor(Draft d, int x, int y, int z) {
		Mat railing = d.mat(Role.RAILING);
		BlockState below = d.get(x, y - 1, z);
		boolean ground = below == null || y <= d.site().ground(x, z) + 1;
		Mat mas = Manner.masonry(d);
		if (ground && mas.has(Form.WALL)) {
			return mas.wall();
		}
		return railing.has(Form.FENCE) ? railing.fence() : mas.has(Form.WALL) ? mas.wall() : d.mat(Role.FENCE).fence();
	}

	// ------------------------------------------------------------------------------ the draft as someone walks it

	/** What stands at a cell: the draft's block, else the natural ground (solid under its standing level), else air. */
	private static boolean passable(Draft d, int x, int y, int z) {
		BlockState s = d.get(x, y, z);
		return s != null ? Spaces.passable(s) : y >= d.site().ground(x, z);
	}

	private static boolean standable(Draft d, int x, int y, int z) {
		BlockState s = d.get(x, y, z);
		return s != null ? Spaces.standable(s) || s.getBlock() instanceof LadderBlock : y < d.site().ground(x, z);
	}

	private static boolean climb(Draft d, int x, int y, int z) {
		BlockState s = d.get(x, y, z);
		return s != null && s.getBlock() instanceof LadderBlock;
	}

	private static boolean step(Draft d, int x, int y, int z) {
		BlockState s = d.get(x, y, z);
		return s != null && (s.getBlock() instanceof StairBlock && s.getValue(StairBlock.HALF) == Half.BOTTOM
			|| s.getBlock() instanceof SlabBlock && s.getValue(SlabBlock.TYPE) == SlabType.BOTTOM || s.getBlock() instanceof BedBlock);
	}

	private static boolean walkable(Draft d, int x, int y, int z) {
		if (!passable(d, x, y, z) || !passable(d, x, y + 1, z)) {
			return false;
		}
		return climb(d, x, y, z) || standable(d, x, y - 1, z);
	}

	/**
	 * How many of the cells walked to matter: where a POI stands (a bed, a job, a seat, a store is used from there) and
	 * the doorways and the cells before them (every room is entered through one). A rail may cut off an alley of bare
	 * ground behind a building; never the way to a room or to what folk use.
	 */
	private static int inPlot(Draft d, LongOpenHashSet reached) {
		LongOpenHashSet keys = new LongOpenHashSet();
		for (Draft.LocalPoi p : d.pois()) {
			keys.add(Draft.key(p.x(), p.y(), p.z()));
		}
		int n = 0;
		for (long k : reached) {
			int x = Draft.keyX(k);
			int y = Draft.keyY(k);
			int z = Draft.keyZ(k);
			if (keys.contains(k) || doorAt(d, x, y, z)) {
				n++;
				continue;
			}
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				if (doorAt(d, x + dir.getStepX(), y, z + dir.getStepZ())) {
					n++;
					break;
				}
			}
		}
		return n;
	}

	private static boolean doorAt(Draft d, int x, int y, int z) {
		BlockState s = d.get(x, y, z);
		return s != null && (s.getBlock() instanceof DoorBlock || s.getBlock() instanceof FenceGateBlock);
	}

	/** Something solid stands over the cell (a floor or a roof): it is indoors. */
	private static boolean roofed(Draft d, int x, int y, int z) {
		for (int yy = y + 2; yy < d.maxHeight() + 2; yy++) {
			BlockState s = d.get(x, yy, z);
			if (s != null && !s.isAir() && !Spaces.passable(s)) {
				return true;
			}
		}
		return false;
	}

	/** A solid face beside the cell other than {@code from}'s (a wall or a post a balustrade's end can stand against). */
	private static boolean braced(Draft d, int x, int y, int z, Direction from) {
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			if (dir == from) {
				continue;
			}
			if (d.isSolid(x + dir.getStepX(), y, z + dir.getStepZ()) || d.get(x + dir.getStepX(), y, z + dir.getStepZ()) != null
				&& d.get(x + dir.getStepX(), y, z + dir.getStepZ()).getBlock() instanceof net.minecraft.world.level.block.FenceBlock) {
				return true;
			}
		}
		return false;
	}

	/** Nothing drawn and no natural ground at a cell (air to build into). */
	private static boolean empty(Draft d, int x, int y, int z) {
		BlockState s = d.get(x, y, z);
		return s != null ? s.isAir() : y >= d.site().ground(x, z);
	}

	/** Open cells under the feet at (x, y, z) before something stops a fall (water stops it at once). */
	private static int drop(Draft d, int x, int y, int z) {
		int n = 0;
		for (int dy = -1; dy >= -12; dy--) {
			BlockState s = d.get(x, y + dy, z);
			if (s != null && !s.getFluidState().isEmpty()) {
				return 0;
			}
			if (!passable(d, x, y + dy, z) || climb(d, x, y + dy, z)) {
				break;
			}
			n++;
		}
		return n;
	}

	/** Every feet cell walked to from the draft's entrances (the plot and two round it), as the audit's walk does. */
	private static LongOpenHashSet walk(Draft d) {
		LongOpenHashSet reached = new LongOpenHashSet();
		LongArrayFIFOQueue q = new LongArrayFIFOQueue();
		int w = d.frontage();
		int dp = d.depth();
		int top = d.maxHeight() + 2;
		for (int[] e : d.entrances()) {
			for (int dy = 0; dy >= -2; dy--) {
				if (walkable(d, e[0], e[1] + dy, e[2])) {
					long k = Draft.key(e[0], e[1] + dy, e[2]);
					if (reached.add(k)) {
						q.enqueue(k);
					}
					break;
				}
			}
		}
		while (!q.isEmpty()) {
			long k = q.dequeueLong();
			int x = Draft.keyX(k);
			int y = Draft.keyY(k);
			int z = Draft.keyZ(k);
			boolean headroom = passable(d, x, y + 2, z);
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				int nx = x + dir.getStepX();
				int nz = z + dir.getStepZ();
				if (nx < -2 || nx > w + 1 || nz < -2 || nz > dp + 1) {
					continue;
				}
				for (int dy = 1; dy >= -3; dy--) {
					int ny = y + dy;
					if (dy == 1 && !headroom) {
						continue;
					}
					if (ny < -24 || ny > top) {
						break;
					}
					if (walkable(d, nx, ny, nz)) {
						long nk = Draft.key(nx, ny, nz);
						if (reached.add(nk)) {
							q.enqueue(nk);
						}
						break;
					}
					if (dy == 0 && step(d, nx, ny, nz) && walkable(d, nx, ny + 1, nz) && headroom) {
						long nk = Draft.key(nx, ny + 1, nz);
						if (reached.add(nk)) {
							q.enqueue(nk);
						}
						break;
					}
					if (!passable(d, nx, ny, nz) || dy < 0 && !passable(d, nx, ny + 1, nz)) {
						if (dy <= 0) {
							break;
						}
					}
				}
			}
			if (climb(d, x, y, z)) {
				for (int dy : new int[]{1, -1}) {
					if (walkable(d, x, y + dy, z) || climb(d, x, y + dy, z)) {
						long nk = Draft.key(x, y + dy, z);
						if (reached.add(nk)) {
							q.enqueue(nk);
						}
					}
				}
			}
		}
		return reached;
	}
}
