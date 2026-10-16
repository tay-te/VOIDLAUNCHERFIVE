package dev.voidmc.hearth.build.kit.noble;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Chimneys;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.civic.Sacred;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * A range: one of the long masonry buildings of a lord's seat (the great hall, the palace, the chapel, the stables, the
 * lodge), standing on its own footings or against a curtain. Masonry walls in the seat's walling with a plinth, dressed
 * quoins, a string course at every floor and the windows of the culture (mullioned, lancet, arched, slot, lattice) in
 * dressed surrounds with sills, timber floors on stone, straight flights between storeys, and a roof in the culture's
 * manner: steep hips and gables with dormers and chimneys, crow-stepped gables on the moor, curved hips in the karst,
 * flat roofs behind a parapet in the south.
 *
 * <p>Build it in order: {@link #footings}, {@link #walls}, {@link #floors}, {@link #flight}s, {@link #door}s, {@link
 * #windows}, {@link #roof}. The archetype partitions and furnishes the rooms itself ({@link #room}).
 */
public final class Range {
	public final int x0;
	public final int z0;
	public final int x1;
	public final int z1;
	/** The ground floor's standing level. */
	public final int level;
	/** Storey heights, floor to floor (4 = rooms three high). */
	public final int[] heights;
	/** The side the main door is on (toward the court). */
	public final Direction court;
	/** Sides with no windows (against a tower, a curtain's walk, another range). */
	public final Set<Direction> blind = EnumSet.noneOf(Direction.class);
	/** Storeys with no floor under them (the upper half of a double-height hall). */
	private final boolean[] open;
	/** Cells kept free of windows and partitions (doors, flights): {x, z, fromY, toY}. */
	private final List<int[]> kept = new ArrayList<>();
	public Roofs.@Nullable Roof roof;
	public int ridge = Integer.MIN_VALUE;
	/** Walls of timber over the plinth (posts and boards), as the north raises its halls on a stone footing. */
	private boolean timber;

	public Range(int x0, int z0, int x1, int z1, int level, int[] heights, Direction court) {
		this.x0 = Math.min(x0, x1);
		this.z0 = Math.min(z0, z1);
		this.x1 = Math.max(x0, x1);
		this.z1 = Math.max(z0, z1);
		this.level = level;
		this.heights = heights;
		this.court = court;
		this.open = new boolean[heights.length];
	}

	/** Builds the walls in timber: a post at every corner and every third cell, boards between, on the stone plinth. */
	/** Whether the walls are framed in logs and boards ({@link #timber()}). */
	public boolean isTimber() {
		return this.timber;
	}

	public Range timber() {
		this.timber = true;
		return this;
	}

	public Range blind(Direction... sides) {
		java.util.Collections.addAll(this.blind, sides);
		return this;
	}

	/** Makes storey k part of the one below (no floor between): a hall two storeys high. */
	public Range openAbove(int k) {
		if (k > 0 && k < this.open.length) {
			this.open[k] = true;
		}
		return this;
	}

	public int storeys() {
		return this.heights.length;
	}

	/** The standing level of storey k. */
	public int floorY(int k) {
		int y = this.level;
		for (int i = 0; i < k; i++) {
			y += this.heights[i];
		}
		return y;
	}

	/** The top course of the walls (the plate the roof sits on). */
	public int plate() {
		return floorY(this.heights.length) - 1;
	}

	/** The ceiling over storey k (the first row of the floor above, or the plate). */
	public int ceiling(int k) {
		int next = k + 1;
		while (next < this.heights.length && this.open[next]) {
			next++;
		}
		return next >= this.heights.length ? plate() : floorY(next) - 1;
	}

	public Side side(Direction out) {
		return Side.of(this.x0, this.z0, this.x1, this.z1, out);
	}

	public boolean wall(int x, int z) {
		return (x == this.x0 || x == this.x1) && z >= this.z0 && z <= this.z1 || (z == this.z0 || z == this.z1) && x >= this.x0 && x <= this.x1;
	}

	/** The room inside the walls on storey k, for furnishing: {x0, z0, x1, z1, floorY, ceilingY}. */
	public int[] room(int k) {
		return new int[]{this.x0 + 1, this.z0 + 1, this.x1 - 1, this.z1 - 1, floorY(k), ceiling(k)};
	}

	private void keep(int x, int z, int y0, int y1) {
		this.kept.add(new int[]{x, z, y0, y1});
	}

	private boolean kept(int x, int z, int y) {
		for (int[] k : this.kept) {
			if (Math.abs(k[0] - x) + Math.abs(k[1] - z) <= 1 && y >= k[2] - 1 && y <= k[3] + 1) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------------------------------- building

	/** Footings under the walls to the ground, the ground floor's floor course over the inside. */
	public Range footings(Draft d, Dress m) {
		return footings(d, m, false);
	}

	/**
	 * Footings: under the walls down to the ground; under the rooms too unless {@code wallsOnly}, where the rooms stand
	 * on made ground (a levelled terrace) and only a course of footing under the floor is laid over it, carried by the
	 * walls where the ground falls away.
	 */
	public Range footings(Draft d, Dress m, boolean wallsOnly) {
		for (int z = this.z0; z <= this.z1; z++) {
			for (int x = this.x0; x <= this.x1; x++) {
				if (!wallsOnly || wall(x, z)) {
					Mason.footing(d, m, x, z, this.level - 2);
				} else {
					try (var s = d.stage(StageKind.FOUNDATION)) {
						d.set(x, this.level - 2, z, m.footing.full());
					}
				}
			}
		}
		return this;
	}

	/** The walls from the floor course to the plate: walling, plinth, quoins, string courses at every floor. */
	public Range walls(Draft d, Dress m, Manner man) {
		int plate = plate();
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = this.z0; z <= this.z1; z++) {
				for (int x = this.x0; x <= this.x1; x++) {
					if (!wall(x, z)) {
						continue;
					}
					boolean corner = (x == this.x0 || x == this.x1) && (z == this.z0 || z == this.z1);
					boolean near = !corner && ((x == this.x0 + 1 || x == this.x1 - 1) && (z == this.z0 || z == this.z1)
						|| (z == this.z0 + 1 || z == this.z1 - 1) && (x == this.x0 || x == this.x1));
					int run = x == this.x0 || x == this.x1 ? z - this.z0 : x - this.x0;
					for (int y = this.level - 1; y <= plate; y++) {
						Paint p;
						if (y <= this.level) {
							p = m.plinth.full();
						} else if (this.timber) {
							boolean post = corner || run % 3 == 0;
							p = post ? Mason.post(m.frame) : y == plate ? Mason.beam(m.beam, x == this.x0 || x == this.x1 ? Direction.Axis.Z : Direction.Axis.X) : m.boards.full();
						} else if (corner || near && ((y - this.level) & 1) == 0) {
							p = m.dress.full();
						} else {
							p = m.wall.full();
						}
						d.set(x, y, z, p);
					}
				}
			}
			// the inside cleared
			for (int z = this.z0 + 1; z < this.z1; z++) {
				for (int x = this.x0 + 1; x < this.x1; x++) {
					for (int y = this.level; y < plate; y++) {
						d.set(x, y, z, Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
		// string courses under each upper floor, all round
		for (int k = 1; k < this.heights.length; k++) {
			if (this.open[k] || this.timber) {
				continue;
			}
			int y = floorY(k) - 1;
			for (Side s : Side.of(this.x0, this.z0, this.x1, this.z1)) {
				for (int i = 0; i < s.length(); i++) {
					Mason.sill(d, m, s.outX(i, 1), y, s.outZ(i, 1), s.out().getOpposite());
				}
			}
		}
		return this;
	}

	/**
	 * Breaks a long blind wall into bays (call after {@link #walls} and the openings): a pier of the dressing up the
	 * wall's face every {@code step} cells, standing a block proud for its lowest {@code proud} courses (a buttress's
	 * foot, its top weathered with a stair), and a small high vent of a slit in every other bay of each storey, as a
	 * stable, a barn or a range's back wall is built. A wall of one material three dozen cells long reads as a blank
	 * slab from the street otherwise.
	 */
	public Range bays(Draft d, Dress m, Direction out, int step, int proud) {
		Side s = side(out);
		int plate = plate();
		// (where the dressing is the walling's own stone, the piers are timber posts let into the wall)
		boolean same = m.dress.main(dev.voidmc.hearth.build.style.Form.FULL) == m.wall.main(dev.voidmc.hearth.build.style.Form.FULL);
		Paint pier = this.timber || same ? Mason.post(m.frame) : m.dress.full();
		for (int i = step; i < s.length() - 1; i += step) {
			int x = s.x(i);
			int z = s.z(i);
			try (var st = d.stage(StageKind.WALLS)) {
				for (int y = this.level + 1; y <= plate; y++) {
					if (d.isSolid(x, y, z)) {
						d.set(x, y, z, pier);
					}
				}
				int ox = s.outX(i, 1);
				int oz = s.outZ(i, 1);
				if (proud > 0 && !this.timber && Mason.inPlot(d, ox, oz)) {
					int g = d.site().ground(ox, oz);
					Mason.footing(d, m, ox, oz, Math.min(g, this.level - 1));
					for (int y = Math.min(g + 1, this.level); y < this.level + proud; y++) {
						if (d.isEmpty(ox, y, oz)) {
							d.set(ox, y, oz, m.dress.full());
						}
					}
					if (d.isEmpty(ox, this.level + proud, oz)) {
						d.set(ox, this.level + proud, oz, m.dress.stairsOrSlab(out.getOpposite(), Half.BOTTOM));
					}
				}
			}
			// a vent high in the bay beside this pier, every other bay, on each storey
			if ((i / step & 1) == 1) {
				for (int k = 0; k < this.heights.length; k++) {
					int vy = ceiling(k) - 1;
					int vi = i - step / 2;
					int vx = s.x(vi);
					int vz = s.z(vi);
					if (vi > 1 && d.isSolid(vx, vy, vz) && d.isSolid(vx, vy - 1, vz) && d.isSolid(vx, vy + 1, vz)) {
						Mason.loop(d, vx, vy, vz, 1);
					}
				}
			}
		}
		return this;
	}

	/** The floors: stone over the ground, timber on every upper storey (but over the flights' wells). */
	public Range floors(Draft d, Dress m) {
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = this.z0 + 1; z < this.z1; z++) {
				for (int x = this.x0 + 1; x < this.x1; x++) {
					d.set(x, this.level - 1, z, m.paving.full());
					for (int k = 1; k < this.heights.length; k++) {
						if (this.open[k] || well(x, z, k)) {
							continue;
						}
						d.set(x, floorY(k) - 1, z, m.floor.full());
					}
				}
			}
		}
		return this;
	}

	/** Flights cut into floors: {x, z, k, dir}: the well over the flight up from storey k. */
	private final List<int[]> wells = new ArrayList<>();

	private boolean well(int x, int z, int k) {
		for (int[] w : this.wells) {
			if (w[2] + 1 != k) {
				continue;
			}
			Direction dir = Dirs.HORIZONTAL[w[3]];
			int len = this.heights[w[2]];
			for (int i = 0; i < len; i++) {
				if (x == w[0] + Dirs.dx(dir) * i && z == w[1] + Dirs.dz(dir) * i) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Plans a straight flight up from storey k to k + 1, its foot at (x, z), climbing {@code dir}; call before
	 * {@link #floors} so the floor above leaves its well, then {@link #flights} to lay the treads.
	 */
	public Range flight(int x, int z, int k, Direction dir) {
		int idx = java.util.Arrays.asList(Dirs.HORIZONTAL).indexOf(dir);
		this.wells.add(new int[]{x, z, k, idx});
		int len = this.heights[k];
		keep(x, z, floorY(k), floorY(k + 1) + 2);
		keep(x + Dirs.dx(dir) * (len - 1), z + Dirs.dz(dir) * (len - 1), floorY(k), floorY(k + 1) + 2);
		keep(x + Dirs.dx(dir) * len, z + Dirs.dz(dir) * len, floorY(k + 1), floorY(k + 1) + 2);
		return this;
	}

	/** Lays the treads of every planned flight, a rail of the furniture's fence over the low end of each well. */
	public Range flights(Draft d, Dress m) {
		BlockState air = Blocks.AIR.defaultBlockState();
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int[] w : this.wells) {
				Direction dir = Dirs.HORIZONTAL[w[3]];
				int k = w[2];
				int len = this.heights[k];
				int f = floorY(k);
				for (int i = 0; i < len; i++) {
					int x = w[0] + Dirs.dx(dir) * i;
					int z = w[1] + Dirs.dz(dir) * i;
					int y = f + i;
					for (int yy = f - 1; yy < y; yy++) {
						d.set(x, yy, z, m.wall.full());
					}
					d.set(x, y, z, m.furniture.stairsOrSlab(dir, Half.BOTTOM));
					for (int yy = y + 1; yy <= y + 3 && yy < floorY(k + 1) + 3; yy++) {
						d.set(x, yy, z, air);
					}
				}
				// the rail: over the well's low end, where it drops more than three from the floor above, a fence
				// at the upper floor's standing level (a full row clear over every climber's head below it)
				int up = floorY(k + 1);
				for (int i = 0; i <= len - 3; i++) {
					int x = w[0] + Dirs.dx(dir) * i;
					int z = w[1] + Dirs.dz(dir) * i;
					if (d.isEmpty(x, up, z) && f + i + 3 < up) {
						d.set(x, up, z, m.furniture.fence());
					}
				}
			}
		}
		return this;
	}

	/**
	 * A door in the wall toward {@code out} at column {@code col} on storey k, {@code width} 1 or 2 leaves, under a
	 * dressed head (an arched head on two leaves); registers the entrance and a DOOR point ({@code main} for the main
	 * entrance). Returns the cell outside it.
	 */
	public int[] door(Draft d, Dress m, Direction out, int col, int k, int width, boolean main) {
		Side s = side(out);
		int i0 = s.index(col);
		int y = floorY(k);
		var doorBlock = d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.DARK_OAK_DOOR);
		for (int c = 0; c < width; c++) {
			int i = i0 + c;
			int x = s.x(i);
			int z = s.z(i);
			DoorHingeSide hinge = width == 1 ? DoorHingeSide.LEFT : c == 0 ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT;
			d.inStage(StageKind.OPENINGS, () -> d.door(x, y, z, doorBlock, out, hinge));
			keep(x, z, y, y + 2);
			// a window drawn here before the door leaves its sill on the step outside: clear the way in
			for (int yy = y; yy <= y + 1; yy++) {
				net.minecraft.world.level.block.state.BlockState there = d.get(s.outX(i, 1), yy, s.outZ(i, 1));
				if (there != null && (there.getBlock() instanceof net.minecraft.world.level.block.StairBlock
					|| there.getBlock() instanceof net.minecraft.world.level.block.SlabBlock)) {
					d.set(s.outX(i, 1), yy, s.outZ(i, 1), Blocks.AIR.defaultBlockState());
				}
			}
		}
		try (var st = d.stage(StageKind.WALLS)) {
			for (int c = -1; c <= width; c++) {
				int i = i0 + c;
				if (i < 0 || i > s.last()) {
					continue;
				}
				d.set(s.x(i), y + 2, s.z(i), m.dress.full());
				if (c == -1 || c == width) {
					d.set(s.x(i), y, s.z(i), m.dress.full());
					d.set(s.x(i), y + 1, s.z(i), m.dress.full());
				}
			}
		}
		int mid = i0 + (width - 1) / 2;
		int ex = s.outX(mid, 1);
		int ez = s.outZ(mid, 1);
		if (main) {
			d.entrance(ex, y, ez);
		}
		d.poi(dev.voidmc.hearth.api.PoiType.DOOR, ex, y, ez, out.getOpposite());
		// the lamp beside it
		int li = i0 - 1 >= 1 ? i0 - 1 : i0 + width;
		int lx = s.outX(li, 1);
		int lz = s.outZ(li, 1);
		if (Mason.ok(d, lx, y + 2, lz) && d.isEmpty(lx, y + 2, lz) && d.isEmpty(lx, y + 1, lz)) {
			dev.voidmc.hearth.build.kit.light.Lighting.doorLamp(d, lx, y + 2, lz, out.getOpposite());
		}
		return new int[]{ex, ez};
	}

	/**
	 * Windows round storey k on every side not blind, every {@code pitch} cells from the middle out, {@code height}
	 * high, in the manner's kind; {@code field} sides (toward {@code fieldSide}, may be null) get only slits on the ground
	 * floor.
	 */
	public Range windows(Draft d, Dress m, Manner man, int k, int height, int pitch, @Nullable Direction fieldSide) {
		int y = floorY(k) + 1;
		if (this.heights[k] <= 3) {
			y = floorY(k);
		}
		for (Side s : Side.of(this.x0, this.z0, this.x1, this.z1)) {
			if (this.blind.contains(s.out())) {
				continue;
			}
			boolean field = s.out() == fieldSide;
			int mid = s.middle();
			List<Integer> cols = new ArrayList<>();
			for (int off = s.length() % 2 == 0 ? 0 : 0; mid - off >= 2 || mid + off <= s.last() - 2; off += pitch) {
				if (mid - off >= 2) {
					cols.add(mid - off);
				}
				if (off > 0 && mid + off <= s.last() - 2) {
					cols.add(mid + off);
				}
			}
			for (int i : cols) {
				int x = s.x(i);
				int z = s.z(i);
				if (kept(x, z, y) || kept(x, z, y + height - 1)) {
					continue;
				}
				if (!Openings.isWallBlock(d, x, y, z) || !Openings.isWallBlock(d, x, y + height - 1, z)) {
					continue;
				}
				if (field && k == 0) {
					Mason.loop(d, x, y, z, 2);
					continue;
				}
				if (this.timber) {
					// a timber wall's light: glass between the posts, no stone about it
					try (var st = d.stage(StageKind.OPENINGS)) {
						for (int yy = y; yy < y + height; yy++) {
							d.set(x, yy, z, m.glass.pane());
						}
					}
					continue;
				}
				window(d, m, man.window(), s, i, y, height);
			}
		}
		return this;
	}

	/**
	 * A skirt of roof round the range at row y, {@code width} cells out from the walls and falling a row a cell: the lower
	 * eaves of a hall roofed in two tiers (the karst's double-eaved halls, the stacked eaves of a tower). Lays only where
	 * nothing stands; its corners lift a half row, as the karst's do.
	 */
	public void skirt(Draft d, Dress m, int y, int width) {
		try (var st = d.stage(StageKind.ROOF)) {
			for (int k = 1; k <= width; k++) {
				int ry = y - (k - 1);
				for (int x = this.x0 - k; x <= this.x1 + k; x++) {
					for (int z = this.z0 - k; z <= this.z1 + k; z++) {
						boolean ringX = x == this.x0 - k || x == this.x1 + k;
						boolean ringZ = z == this.z0 - k || z == this.z1 + k;
						if (!ringX && !ringZ || !Mason.ok(d, x, ry, z) || !d.isAbsent(x, ry, z) && !d.isEmpty(x, ry, z)) {
							continue;
						}
						if (ringX && ringZ) {
							d.set(x, ry, z, m.trim.has(Form.SLAB) ? m.trim.slab(k == width ? SlabType.TOP : SlabType.BOTTOM) : m.roof.full());
							continue;
						}
						Direction out = ringX ? (x < this.x0 ? Dirs.LEFT : Dirs.RIGHT) : (z < this.z0 ? Dirs.FRONT : Dirs.BACK);
						d.set(x, ry, z, m.roof.stairsOrSlab(out.getOpposite(), Half.BOTTOM));
					}
				}
			}
		}
	}

	/** One window at cell i of a side: glass in the manner's kind, dressed jambs and head, a sill. */
	public static void window(Draft d, Dress m, Manner.Window kind, Side s, int i, int y, int height) {
		int x = s.x(i);
		int z = s.z(i);
		Direction out = s.out();
		switch (kind) {
			case LANCET -> {
				Sacred.lancet(d, s, s.column(i), y, 1, height, false);
				return;
			}
			case LATTICE -> {
				try (var st = d.stage(StageKind.OPENINGS)) {
					for (int yy = y; yy < y + height; yy++) {
						d.set(x, yy, z, d.mat(Role.SHUTTER).trapdoor(out.getOpposite(), Half.BOTTOM, true));
					}
				}
			}
			default -> {
				try (var st = d.stage(StageKind.OPENINGS)) {
					for (int yy = y; yy < y + height; yy++) {
						d.set(x, yy, z, m.glass.pane());
					}
				}
			}
		}
		try (var st = d.stage(StageKind.WALLS)) {
			// the head: a lintel of the dressing, an arch's keystone, or the shoulders of a round head
			if (Mason.ok(d, x, y + height, z) && Openings.isWallBlock(d, x, y + height, z)) {
				d.set(x, y + height, z, m.dress.full());
			}
			if (kind == Manner.Window.MULLION || kind == Manner.Window.ARCHED) {
				for (int c : new int[]{-1, 1}) {
					int jx = s.x(i + c);
					int jz = s.z(i + c);
					if (i + c < 1 || i + c > s.last() - 1) {
						continue;
					}
					for (int yy = y; yy < y + height; yy += 2) {
						if (Openings.isWallBlock(d, jx, yy, jz)) {
							d.set(jx, yy, jz, m.dress.full());
						}
					}
				}
			}
		}
		if (kind == Manner.Window.ARCHED && height >= 2) {
			// a round head: the top pane under an upside-down stair in the middle of the lintel course outside
			int ox = s.outX(i, 1);
			int oz = s.outZ(i, 1);
			Mason.corbel(d, m.dress, ox, y + height, oz, out.getOpposite());
		}
		Mason.sill(d, m, s.outX(i, 1), y - 1, s.outZ(i, 1), out.getOpposite());
	}

	/**
	 * The roof: in {@code form} along {@code axis} (flat behind a parapet where the manner builds flat), pitched to the
	 * manner but brought under {@code maxTop}, with {@code dormers} dormer windows on the court side's slope, the gables
	 * crow-stepped where the manner steps them, and chimneys at {@code chimneys} ({x, z} cells on the walls, where the
	 * stacks rise). Returns the highest row used.
	 */
	public int roof(Draft d, Dress m, Manner man, RoofForm form, Direction.Axis axis, int maxTop, int dormers, List<int[]> chimneys) {
		int plate = plate();
		if (man.flat() || form == RoofForm.FLAT) {
			int y = Roofs.flat(d, this.x0, this.z0, this.x1, this.z1, plate, true);
			this.ridge = y + 1;
			for (Side s : Side.of(this.x0, this.z0, this.x1, this.z1)) {
				for (int i = 0; i < s.length(); i += 2) {
					if (man.merlon() == Manner.Merlon.POINTED || man.merlon() == Manner.Merlon.SQUARE) {
						Mason.merlon(d, m, man.merlon(), s.x(i), y + 1, s.z(i), i, s.along(), s.out(), s.isCorner(i));
					}
				}
			}
			return this.ridge;
		}
		double half = (axis == Direction.Axis.X ? this.z1 - this.z0 : this.x1 - this.x0) / 2.0 + 1;
		double pitch = Math.min(man.pitch(), (maxTop - plate - 2) / Math.max(1.0, half));
		pitch = Math.max(0.5, pitch);
		Roofs.Opts o = Roofs.Opts.of(d).withPitch(pitch).withEaves(1, man.crowSteps() ? 0 : 1);
		RoofForm f = man.culture() == dev.voidmc.hearth.api.Culture.KARST && form != RoofForm.GABLE ? RoofForm.PAGODA : form;
		Roofs.Plan plan = Roofs.plan(d, this.x0, this.z0, this.x1, this.z1, plate, f, axis, o, false, false);
		if (dormers > 0 && f != RoofForm.PAGODA) {
			Side cs = side(this.court);
			if (cs.axis() == axis) {
				int n = dormers;
				int len = cs.length();
				for (int j = 0; j < n; j++) {
					int i = (j + 1) * len / (n + 1);
					if (i < 2 || i > len - 3) {
						continue;
					}
					plan.dormer(cs, cs.column(i), 1, plate + 1);
				}
			}
		}
		Roofs.Built b = plan.draw(d);
		this.roof = b.roof();
		this.ridge = this.roof.ridgeY;
		if (man.crowSteps() && f != RoofForm.HIP) {
			crowSteps(d, m, axis);
		}
		for (int[] c : chimneys) {
			int top = Chimneys.stack(d, c[0], c[1], plate + 1, this.roof, this.ridge + 1);
			this.ridge = Math.max(this.ridge, top);
		}
		return this.ridge;
	}

	/** Corbie steps up the gable ends: each pair of columns carried a block over the roof beside it, capped in the dressing. */
	private void crowSteps(Draft d, Dress m, Direction.Axis axis) {
		Roofs.Roof r = this.roof;
		if (r == null) {
			return;
		}
		int plate = plate();
		for (Side s : Side.of(this.x0, this.z0, this.x1, this.z1)) {
			if (s.axis() == axis) {
				continue;
			}
			Direction in = s.out().getOpposite();
			for (int i = 0; i < s.length(); i++) {
				int x = s.x(i);
				int z = s.z(i);
				int pair = Math.min(i, s.last() - i) / 2 * 2;
				int ref = Math.min(i, s.last() - i) == 0 ? 0 : pair;
				int rx = s.x(Math.min(s.last(), Math.max(0, i < s.length() / 2 ? ref : s.last() - ref)));
				int rz = s.z(Math.min(s.last(), Math.max(0, i < s.length() / 2 ? ref : s.last() - ref)));
				int ix = rx + Dirs.dx(in);
				int iz = rz + Dirs.dz(in);
				int sy = r.covers(ix, iz) ? r.surfaceY(ix, iz) : plate;
				int top = Math.max(plate + 1, sy + 1);
				try (var st = d.stage(StageKind.WALLS)) {
					for (int y = plate + 1; y <= top; y++) {
						if (Mason.ok(d, x, y, z)) {
							d.set(x, y, z, m.wall.full());
						}
					}
					if (Mason.ok(d, x, top + 1, z)) {
						d.set(x, top + 1, z, m.dress.has(Form.SLAB) ? m.dress.slab(SlabType.BOTTOM) : m.dress.full());
					}
				}
				// clear the roof's verge outside the gable
				int ox = s.outX(i, 1);
				int oz = s.outZ(i, 1);
				if (r.covers(ox, oz)) {
					for (int y = r.bottomY(ox, oz); y <= r.surfaceY(ox, oz); y++) {
						if (Mason.ok(d, ox, y, oz)) {
							d.remove(ox, y, oz);
						}
					}
				}
			}
		}
	}

	/** A hearth in the wall at (x, z) on storey k facing {@code into} the room, its chimney planned for {@link #roof}. */
	public int[] hearth(Draft d, int x, int z, int k, Direction into) {
		Furniture.hearth(d, x, floorY(k), z, into, true);
		keep(x, z, floorY(k), floorY(k) + 2);
		return new int[]{x, z};
	}

	/** The wall cells the archetype should keep clear of windows (a door from a tower, a partition's end). */
	public Range keepClear(int x, int z, int y0, int y1) {
		keep(x, z, y0, y1);
		return this;
	}

	/** A cross wall inside at {@code x} (along z) or {@code z} (along x) on storey k, with a doorway at {@code door}. */
	public void partition(Draft d, Dress m, int k, boolean alongZ, int at, int door) {
		int f = floorY(k);
		int c = ceiling(k);
		boolean top = c == plate();
		try (var st = d.stage(StageKind.WALLS)) {
			if (alongZ) {
				for (int z = this.z0 + 1; z < this.z1; z++) {
					int h = top ? toRoof(d, at, c, z) : c;
					for (int y = f; y < h; y++) {
						boolean doorway = z == door && y < f + 2;
						d.set(at, y, z, doorway ? Blocks.AIR.defaultBlockState() : m.wall.full().at(at, y, z));
					}
				}
			} else {
				for (int x = this.x0 + 1; x < this.x1; x++) {
					int h = top ? toRoof(d, x, c, at) : c;
					for (int y = f; y < h; y++) {
						boolean doorway = x == door && y < f + 2;
						d.set(x, y, at, doorway ? Blocks.AIR.defaultBlockState() : m.wall.full().at(x, y, at));
					}
				}
			}
		}
	}

	/**
	 * Under an open roof a partition of the top storey runs on up to the roof drawn over it, so no ledge is left on its
	 * head under the rafters; where nothing is over it yet, it stops at the plate.
	 */
	private static int toRoof(Draft d, int x, int c, int z) {
		for (int y = c; y < c + 14; y++) {
			if (!d.isEmpty(x, y, z)) {
				return y;
			}
		}
		return c;
	}

	/** A straight run of the plot's cells along this range's court side, one out (where a lamp or a bench goes). */
	public static int[] outside(Side s, int i, int k) {
		return new int[]{s.outX(i, k), s.outZ(i, k)};
	}

	/** Lanterns hung over the floor of a room every few cells along its middle. */
	public static void hang(Draft d, int[] room, int spacing) {
		int ceiling = room[5];
		int floor = room[4];
		if (ceiling - floor < 3) {
			return;
		}
		int lanternY = ceiling - floor >= 5 ? ceiling - 2 : ceiling - 1;
		boolean alongX = room[2] - room[0] >= room[3] - room[1];
		int mx = (room[0] + room[2]) / 2;
		int mz = (room[1] + room[3]) / 2;
		if (alongX) {
			for (int x = room[0] + spacing / 2; x <= room[2]; x += spacing) {
				if (d.isEmpty(x, lanternY, mz) && d.isSolid(x, ceiling, mz) && d.isEmpty(x, lanternY - 1, mz)) {
					dev.voidmc.hearth.build.kit.light.Lighting.chandelier(d, x, mz, ceiling, lanternY);
				}
			}
		} else {
			for (int z = room[1] + spacing / 2; z <= room[3]; z += spacing) {
				if (d.isEmpty(mx, lanternY, z) && d.isSolid(mx, ceiling, z) && d.isEmpty(mx, lanternY - 1, z)) {
					dev.voidmc.hearth.build.kit.light.Lighting.chandelier(d, mx, z, ceiling, lanternY);
				}
			}
		}
	}

	/** The habits' ridge cap, for callers rasterising their own roofs. */
	public static Habits.Ridge ridge(Draft d) {
		return d.habits().roof().ridge();
	}
}
