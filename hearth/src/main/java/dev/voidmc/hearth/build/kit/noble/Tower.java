package dev.voidmc.hearth.build.kit.noble;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.landmark.Crowns;
import dev.voidmc.hearth.build.kit.landmark.Shapes;
import dev.voidmc.hearth.build.kit.landmark.Stone;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * A tower of a lord's seat, round or square: footed in the ground (a battered talus where it stands proud of the slope),
 * its shell in the walling with a plinth, dressed quoins on a square plan, string courses at its floors, floors of
 * timber on stone, a spiral stair from the ground floor to the head, arrow loops and windows, doors where the archetype
 * asks (at the foot, onto the wall walks), and a head in the culture's manner ({@link Manner.Cap}): a tall cone or
 * pyramid, a timber hoarding under a steep cap, an open battlemented deck (corbelled out over machicolations where the
 * manner has them), an onion, a curved hip on posts, a dome. The head is what VOID LOD shows on the horizon.
 *
 * <p>Build it in order: {@link #footings}, {@link #shell}, {@link #floors}, {@link #stair}, doors and windows, then
 * {@link #head}. Everything stays under the plot's height: {@link #headRows} says how many rows a head needs, so the
 * caller can choose {@code top} to fit.
 */
public final class Tower {
	public final boolean round;
	/** Centre (round) or the box's middle (square). */
	public final int cx;
	public final int cz;
	/** Diameter of a round tower; width of a square one along x. */
	public final int diam;
	public final int x0;
	public final int z0;
	public final int x1;
	public final int z1;
	public final int thick;
	/** The ground floor's standing level. */
	public final int base;
	/** The head's standing level: the deck of an open top, the floor of the room under a cap. */
	public final int top;
	public final Manner.Cap cap;
	/** Interior floors (standing levels) between base and top, set by {@link #floors}. */
	private final List<Integer> floorLevels = new ArrayList<>();
	private int stairX = Integer.MIN_VALUE;
	private int stairZ;
	private int stairStart;
	/** The spiral's start forced by {@link #arriveToward} (-1: seeded). */
	private int forcedStart = -1;
	private boolean ladder;
	/** The highest row the head used, after {@link #head}. */
	public int highest = Integer.MIN_VALUE;
	/** The way the lord's flag flies from the apex (null: a plain finial). */
	private @Nullable Direction flagFly;
	/** Whether the shell starts with a plinth (not on a turret standing on a bastion). */
	private boolean plinth = true;
	/** Doorways cut (x, z, level) so floors and lights keep them clear. */
	private final List<int[]> doorways = new ArrayList<>();

	private Tower(boolean round, int cx, int cz, int diam, int x0, int z0, int x1, int z1, int thick, int base, int top, Manner.Cap cap) {
		this.round = round;
		this.cx = cx;
		this.cz = cz;
		this.diam = diam;
		this.x0 = x0;
		this.z0 = z0;
		this.x1 = x1;
		this.z1 = z1;
		this.thick = thick;
		this.base = base;
		this.top = top;
		this.cap = cap;
	}

	/** A round tower of odd diameter {@code diam} centred on (cx, cz). */
	public static Tower round(int cx, int cz, int diam, int base, int top, Manner.Cap cap) {
		int r = diam / 2;
		return new Tower(true, cx, cz, diam, cx - r, cz - r, cx + r, cz + r, diam >= 11 ? 2 : 1, base, top, cap);
	}

	/** A square (or oblong) tower over the box. */
	public static Tower square(int x0, int z0, int x1, int z1, int base, int top, Manner.Cap cap) {
		int w = Math.min(x1 - x0, z1 - z0) + 1;
		return new Tower(false, (x0 + x1) / 2, (z0 + z1) / 2, x1 - x0 + 1, x0, z0, x1, z1, w >= 11 ? 2 : 1, base, top, cap);
	}

	// ------------------------------------------------------------------------------------------- geometry

	public boolean in(int x, int z) {
		return this.round ? Round.in(this.cx, this.cz, this.diam, x, z) : x >= this.x0 && x <= this.x1 && z >= this.z0 && z <= this.z1;
	}

	/** Whether a cell is the tower's wall. */
	public boolean wall(int x, int z) {
		if (!in(x, z)) {
			return false;
		}
		if (this.round) {
			int inner = this.diam - 2 * this.thick;
			return Round.edge(this.cx, this.cz, this.diam, x, z) || this.thick > 1 && (inner < 1 || !Round.in(this.cx, this.cz, inner, x, z));
		}
		return x < this.x0 + this.thick || x > this.x1 - this.thick || z < this.z0 + this.thick || z > this.z1 - this.thick;
	}

	public boolean inside(int x, int z) {
		return in(x, z) && !wall(x, z);
	}

	public List<int[]> cells() {
		if (this.round) {
			return Round.disc(this.cx, this.cz, this.diam);
		}
		List<int[]> out = new ArrayList<>();
		for (int z = this.z0; z <= this.z1; z++) {
			for (int x = this.x0; x <= this.x1; x++) {
				out.add(new int[]{x, z});
			}
		}
		return out;
	}

	/** The cells just outside the tower, face to face with its wall. */
	public List<int[]> around() {
		if (this.round) {
			return Round.around(this.cx, this.cz, this.diam);
		}
		List<int[]> out = new ArrayList<>();
		for (int x = this.x0 - 1; x <= this.x1 + 1; x++) {
			for (int z = this.z0 - 1; z <= this.z1 + 1; z++) {
				boolean ring = x == this.x0 - 1 || x == this.x1 + 1 || z == this.z0 - 1 || z == this.z1 + 1;
				if (ring) {
					out.add(new int[]{x, z});
				}
			}
		}
		return out;
	}

	/** The way the tower's face looks at a cell on or outside its wall. */
	public Direction out(int x, int z) {
		if (this.round) {
			return Dirs.nearest(x - this.cx, z - this.cz);
		}
		if (z < this.z0) {
			return Dirs.FRONT;
		}
		if (z > this.z1) {
			return Dirs.BACK;
		}
		if (x < this.x0) {
			return Dirs.LEFT;
		}
		if (x > this.x1) {
			return Dirs.RIGHT;
		}
		double dx = (x - (this.x0 + this.x1) / 2.0) / Math.max(1, this.x1 - this.x0);
		double dz = (z - (this.z0 + this.z1) / 2.0) / Math.max(1, this.z1 - this.z0);
		return Dirs.nearest(dx, dz);
	}

	/** The wall cell in the middle of the face toward {@code dir} (where a door or a window goes). */
	public int[] face(Direction dir) {
		if (this.round) {
			int r = this.diam / 2;
			return new int[]{this.cx + Dirs.dx(dir) * r, this.cz + Dirs.dz(dir) * r};
		}
		return switch (dir) {
			case NORTH -> new int[]{this.cx, this.z1};
			case SOUTH -> new int[]{this.cx, this.z0};
			case EAST -> new int[]{this.x1, this.cz};
			default -> new int[]{this.x0, this.cz};
		};
	}

	private boolean corner(int x, int z) {
		return !this.round && (x == this.x0 || x == this.x1) && (z == this.z0 || z == this.z1);
	}

	/** Rows a head of a kind takes above {@code top} (cap and finial included), for laying out a tower to fit. */
	public static int headRows(Manner.Cap cap, int diam, Manner man) {
		double r = diam / 2.0 + 1;
		return switch (cap) {
			case CONE -> (int) Math.ceil(r * conePitch(man, diam)) + 4;
			case PYRAMID -> (int) Math.ceil(r * pyramidPitch(man, diam)) + 4;
			case HOARD -> (int) Math.ceil((r + 1) * conePitch(man, diam)) + 6;
			case CRENEL -> 3;
			case ONION -> Math.max(12, Crowns.rows(Crowns.Kind.ONION, diam, 30)) + 3;
			case PAGODA -> (int) Math.round(r + 2) + 6;
			case DOME -> (int) Math.ceil(r) + 5;
		};
	}

	static double conePitch(Manner man, int diam) {
		return switch (man.culture()) {
			// Bohemia roofs its towers in tile at the pitch of its houses: a low cone, never a witch's hat
			case TEMPERATE -> diam <= 5 ? 1.5 : diam <= 7 ? 1.25 : 1.1;
			case STEPPE -> 1.0;
			case NORTHERN, TUNDRA -> 1.5;
			case ALPINE, WISTERIA -> diam <= 5 ? 2.5 : diam <= 7 ? 2.0 : 1.7;
			default -> diam <= 5 ? 2.0 : diam <= 7 ? 1.75 : 1.5;
		};
	}

	static double pyramidPitch(Manner man, int diam) {
		return switch (man.culture()) {
			case ALPINE -> 2.5;
			case TEMPERATE -> diam <= 7 ? 1.25 : 1.0;
			case BAYOU, TROPICAL, KARST -> 1.0;
			case NORTHERN, TUNDRA -> 1.5;
			default -> diam <= 7 ? 2.0 : 1.6;
		};
	}

	// ------------------------------------------------------------------------------------------- building

	/** The lord's flag on the apex of the head, flying toward {@code fly} (call before {@link #head}). */
	public Tower flag(Direction fly) {
		this.flagFly = fly;
		return this;
	}

	/** No plinth course at the foot of the shell (a turret standing on a bastion or a wall). */
	public Tower noPlinth() {
		this.plinth = false;
		return this;
	}

	/** Footings under every cell up to the ground floor's floor block, and a battered talus where the tower stands proud. */
	public Tower footings(Draft d, Dress m) {
		for (int[] c : cells()) {
			Mason.footing(d, m, c[0], c[1], this.base - 2);
		}
		for (int[] c : around()) {
			if (Mason.inPlot(d, c[0], c[1]) && d.site().ground(c[0], c[1]) < this.base - 1) {
				Mason.talus(d, m, c[0], c[1], out(c[0], c[1]), this.base - 2);
			}
		}
		return this;
	}

	/**
	 * The shell from the ground floor's floor course to {@code wallTop} (inclusive): the walling, a plinth of two courses,
	 * quoins on the corners of a square tower, and string courses at {@code courses} (standing levels: a drip moulding a
	 * row under each).
	 */
	public Tower shell(Draft d, Dress m, int wallTop, int... courses) {
		// the quoins tooth into the faces only in a tonal dressing (a contrasting one reads as a checkerboard: Walls.tonal)
		boolean toothed = dev.voidmc.hearth.build.kit.Walls.tonal(m.wall.main(Form.FULL), m.dress.main(Form.FULL));
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : cells()) {
				int x = c[0];
				int z = c[1];
				if (!wall(x, z)) {
					continue;
				}
				boolean corner = corner(x, z);
				boolean nearCorner = toothed && !this.round && !corner && (Math.abs(x - this.x0) + Math.abs(z - this.z0) == 1 || Math.abs(x - this.x1) + Math.abs(z - this.z0) == 1
					|| Math.abs(x - this.x0) + Math.abs(z - this.z1) == 1 || Math.abs(x - this.x1) + Math.abs(z - this.z1) == 1);
				for (int y = this.base - 1; y <= wallTop; y++) {
					Paint p;
					if (y <= this.base && this.plinth) {
						p = m.plinth.full();
					} else if (corner || nearCorner && ((y - this.base) & 1) == 0) {
						p = m.dress.full();
					} else {
						p = m.wall.full();
					}
					d.set(x, y, z, p);
				}
			}
		}
		for (int level : courses) {
			course(d, m, level - 1);
		}
		return this;
	}

	/** A drip course of the dressing round the outside at row y. */
	public void course(Draft d, Dress m, int y) {
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : around()) {
				if (!Mason.ok(d, c[0], y, c[1]) || !d.isEmpty(c[0], y, c[1])) {
					continue;
				}
				Direction toWall = out(c[0], c[1]).getOpposite();
				// only where the cell faces the wall square on (no lone corner stairs on a curve)
				int wx = c[0] + Dirs.dx(toWall);
				int wz = c[1] + Dirs.dz(toWall);
				// nor where it would run on from a walk or a deck beside the tower as a ledge round it
				if (!wall(wx, wz) || Mason.ledge(d, c[0], y, c[1], toWall)) {
					continue;
				}
				d.set(c[0], y, c[1], m.dress.stairsOrSlab(toWall, Half.TOP));
			}
		}
	}

	/**
	 * The inside cleared from the ground floor up to {@code ceiling} (exclusive), the ground floor paved, and timber
	 * floors at each standing level in {@code levels} (on a stone course where they meet the wall). The stair's square is
	 * left open; call {@link #stair} first if a stair is wanted.
	 */
	public Tower floors(Draft d, Dress m, int ceiling, int... levels) {
		this.floorLevels.clear();
		for (int l : levels) {
			if (l > this.base && l < ceiling) {
				this.floorLevels.add(l);
			}
		}
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : cells()) {
				if (!inside(c[0], c[1])) {
					continue;
				}
				for (int y = this.base; y < ceiling; y++) {
					if (d.isAbsent(c[0], y, c[1]) || !isStair(d, c[0], y, c[1])) {
						if (!stairCell(c[0], c[1]) || !isStair(d, c[0], y, c[1])) {
							d.set(c[0], y, c[1], Blocks.AIR.defaultBlockState());
						}
					}
				}
			}
		}
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int[] c : cells()) {
				if (!inside(c[0], c[1])) {
					continue;
				}
				if (!stairCell(c[0], c[1]) || !isStair(d, c[0], this.base - 1, c[1])) {
					d.set(c[0], this.base - 1, c[1], m.paving.full());
				}
				for (int l : this.floorLevels) {
					if (this.stairX != Integer.MIN_VALUE && stairNeedsAir(c[0], l - 1, c[1])) {
						continue;
					}
					if (stairCell(c[0], c[1]) && isStair(d, c[0], l - 1, c[1])) {
						continue;
					}
					d.set(c[0], l - 1, c[1], m.floor.full());
				}
			}
		}
		return this;
	}

	private boolean isStair(Draft d, int x, int y, int z) {
		BlockState s = d.get(x, y, z);
		return s != null && (s.getBlock() instanceof net.minecraft.world.level.block.SlabBlock || s.getBlock() instanceof net.minecraft.world.level.block.LadderBlock
			|| x == this.stairX && z == this.stairZ && !s.isAir());
	}

	private boolean stairCell(int x, int z) {
		if (this.stairX == Integer.MIN_VALUE) {
			return false;
		}
		if (this.ladder) {
			return x == this.stairX && z == this.stairZ;
		}
		return Math.abs(x - this.stairX) <= 1 && Math.abs(z - this.stairZ) <= 1;
	}

	private boolean stairNeedsAir(int x, int y, int z) {
		if (this.ladder) {
			return x == this.stairX && z == this.stairZ;
		}
		return Mason.spiralNeedsAir(this.stairX, this.stairZ, this.base, this.top - this.base, this.stairStart, x, y, z);
	}

	/**
	 * The stair from the ground floor to the head: a spiral round a newel in the middle of a round tower (or in a corner of
	 * a square one) where there is room, else a ladder against the wall. {@code timber} for wooden treads.
	 */
	public Tower stair(Draft d, Dress m, boolean timber, @Nullable Direction prefer) {
		int rise = this.top - this.base;
		if (rise <= 0) {
			return this;
		}
		int inner = this.round ? this.diam - 2 * this.thick : Math.min(this.x1 - this.x0, this.z1 - this.z0) + 1 - 2 * this.thick;
		if (inner >= 5) {
			if (this.round) {
				this.stairX = this.cx;
				this.stairZ = this.cz;
			} else {
				Direction p = prefer == null ? Dirs.BACK : prefer;
				int ix0 = this.x0 + this.thick;
				int ix1 = this.x1 - this.thick;
				int iz0 = this.z0 + this.thick;
				int iz1 = this.z1 - this.thick;
				// a cell from the walls in the corner toward the preference, so every tread has the room beside it (a stair
				// against two walls leaves treads with no way off them); in the middle of a five-wide inside
				boolean right = p == Dirs.BACK || p == Dirs.RIGHT;
				boolean back = p == Dirs.BACK || p == Dirs.LEFT;
				int iw = ix1 - ix0 + 1;
				int id = iz1 - iz0 + 1;
				this.stairX = iw >= 6 ? (right ? ix1 - 2 : ix0 + 2) : (ix0 + ix1) / 2;
				this.stairZ = id >= 6 ? (back ? iz1 - 2 : iz0 + 2) : (iz0 + iz1) / 2;
			}
			this.stairStart = this.forcedStart >= 0 ? this.forcedStart : Math.floorMod((int) (d.seed() & 7), 8);
			this.ladder = false;
			Mason.spiral(d, m, this.stairX, this.stairZ, this.base, rise, this.stairStart, timber);
		} else if (inner >= 1) {
			// a ladder against the inside of the back wall
			int lx = this.cx;
			int lz = this.round ? this.cz + (this.diam / 2 - this.thick) : this.z1 - this.thick;
			if (!inside(lx, lz)) {
				lz = this.cz;
			}
			this.stairX = lx;
			this.stairZ = lz;
			this.ladder = true;
			try (var s = d.stage(StageKind.FLOORS)) {
				for (int y = this.base; y < this.top + (this.cap == Manner.Cap.CRENEL || this.cap == Manner.Cap.DOME ? 1 : 0); y++) {
					d.set(lx, y, lz, States.facing(Blocks.LADDER, Dirs.FRONT));
				}
			}
		}
		return this;
	}

	/**
	 * Takes the cell (x, z) inside as the tower's ladder well (call before {@link #floors}): the floors and the head's deck
	 * leave it open as a hatch, so a ladder set in it lands on the deck or the top room's floor.
	 */
	public Tower ladderWell(int x, int z) {
		this.stairX = x;
		this.stairZ = z;
		this.ladder = true;
		return this;
	}

	/**
	 * Turns the spiral (call before {@link #stair}) so it arrives at as many of the standing {@code levels} as can be on
	 * the side toward {@code dir}: in a round tower of seven the landing round the spiral is cut into four by the wall,
	 * and only the quarter the spiral arrives on is reached at each floor, so doorways on a fixed side need this.
	 */
	public Tower arriveToward(Direction dir, int... levels) {
		int[][] ring = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};
		int best = 0;
		int bestScore = -1;
		for (int start = 0; start < 8; start++) {
			int score = 0;
			for (int l : levels) {
				int[] c = ring[Math.floorMod(start + 2 * (l - this.base), 8)];
				if (c[0] * Dirs.dx(dir) + c[1] * Dirs.dz(dir) == 1) {
					score++;
				}
			}
			if (score > bestScore) {
				bestScore = score;
				best = start;
			}
		}
		this.forcedStart = best;
		return this;
	}

	/** The cell (x, z) the stair arrives on at the head, or null without a stair. */
	public int @Nullable [] stairTop() {
		if (this.stairX == Integer.MIN_VALUE) {
			return null;
		}
		if (this.ladder) {
			return new int[]{this.stairX, this.stairZ};
		}
		return Mason.spiralTop(this.stairX, this.stairZ, this.top - this.base, this.stairStart);
	}

	/**
	 * The room on the storey standing at {@code level}, for furnishing: the inside's box, its stair's square and the cells
	 * inside every doorway kept free (and the stair's arrival on this floor).
	 */
	public dev.voidmc.hearth.build.kit.Rooms.Room room(int level) {
		var r = new dev.voidmc.hearth.build.kit.Rooms.Room(this.x0 + this.thick, this.z0 + this.thick, this.x1 - this.thick, this.z1 - this.thick, level, 3);
		if (this.stairX != Integer.MIN_VALUE) {
			if (this.ladder) {
				r.keep(this.stairX, this.stairZ);
				r.keep(this.stairX, this.stairZ - 1);
			} else {
				r.keepArea(this.stairX - 1, this.stairZ - 1, this.stairX + 1, this.stairZ + 1);
				// the cells round the stair's square, so it can be walked onto and off at every floor
				for (int dz = -2; dz <= 2; dz++) {
					for (int dx = -2; dx <= 2; dx++) {
						if ((Math.abs(dx) == 2 || Math.abs(dz) == 2) && inside(this.stairX + dx, this.stairZ + dz)) {
							r.keepArea(this.stairX + dx, this.stairZ + dz, this.stairX + dx, this.stairZ + dz);
						}
					}
				}
				// the ways onto the stair at this floor: beside its first tread on the ground floor, its arrival above
				int[][] ring = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};
				int[] at = level == this.base ? ring[(this.stairStart + 1) % 8] : null;
				int[] cell = at != null ? new int[]{this.stairX + at[0], this.stairZ + at[1]}
					: Mason.spiralTop(this.stairX, this.stairZ, Math.max(0, level - this.base), this.stairStart);
				for (Direction dir : Dirs.HORIZONTAL) {
					int nx = cell[0] + Dirs.dx(dir);
					int nz = cell[1] + Dirs.dz(dir);
					if ((Math.abs(nx - this.stairX) > 1 || Math.abs(nz - this.stairZ) > 1) && inside(nx, nz)) {
						r.keep(nx, nz);
					}
				}
			}
		}
		for (int[] dw : this.doorways) {
			if (dw[2] != level) {
				continue;
			}
			for (Direction dir : Dirs.HORIZONTAL) {
				int ix = dw[0] + Dirs.dx(dir) * (this.thick);
				int iz = dw[1] + Dirs.dz(dir) * (this.thick);
				if (inside(ix, iz)) {
					r.keep(ix, iz);
				}
			}
		}
		return r;
	}

	/** Whether (x, z) lies in the stair's footprint (its 3 × 3, or the ladder's cell). */
	public boolean stairFoot(int x, int z) {
		return stairCell(x, z);
	}

	/**
	 * A doorway through the wall toward {@code dir} at standing level {@code level}, {@code width} wide (1 or 3),
	 * {@code door} for a door leaf in it (else an open arch). Returns the cell outside it.
	 */
	public int[] door(Draft d, Dress m, Direction dir, int level, int width, boolean door) {
		int[] f = face(dir);
		return doorAt(d, m, f[0], f[1], dir, level, width, door);
	}

	/**
	 * A doorway at standing level {@code level} out toward {@code dir} wherever the way beyond is open: the wall cell whose
	 * outside neighbour that way can be stood on (empty two high over something solid) and whose inside neighbour is the
	 * tower's floor, the nearest to the face's middle. Onto a wall walk that runs past the tower off its axis, or a court
	 * in the angle of two curtains. Returns the outside cell, or null when nothing beyond is open.
	 */
	public int @Nullable [] doorToward(Draft d, Dress m, Direction dir, int level, boolean door) {
		int[] best = null;
		int bestScore = Integer.MAX_VALUE;
		int[] f = face(dir);
		for (int[] c : cells()) {
			if (!wall(c[0], c[1])) {
				continue;
			}
			int ox = c[0] + Dirs.dx(dir);
			int oz = c[1] + Dirs.dz(dir);
			if (in(ox, oz)) {
				continue;
			}
			int ix = c[0];
			int iz = c[1];
			for (int t = 0; t < this.thick; t++) {
				ix -= Dirs.dx(dir);
				iz -= Dirs.dz(dir);
			}
			if (!inside(ix, iz) || stairCell(ix, iz)) {
				continue;
			}
			if (!d.isEmpty(ox, level, oz) || !d.isEmpty(ox, level + 1, oz) || !d.isSolid(ox, level - 1, oz)) {
				continue;
			}
			int score = Math.abs(c[0] - f[0]) + Math.abs(c[1] - f[1]);
			if (score < bestScore) {
				bestScore = score;
				best = c;
			}
		}
		if (best == null) {
			return null;
		}
		return doorAt(d, m, best[0], best[1], dir, level, 1, door);
	}

	/** A doorway through the wall at a given wall cell, the passage running {@code dir} outward. */
	public int[] doorAt(Draft d, Dress m, int fx, int fz, Direction dir, int level, int width, boolean door) {
		Direction along = Dirs.right(dir);
		int half = width / 2;
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int k = -half; k <= half; k++) {
			int x = fx + Dirs.dx(along) * k;
			int z = fz + Dirs.dz(along) * k;
			// through the whole thickness, inward
			for (int t = 0; t < this.thick + 1; t++) {
				int px = x - Dirs.dx(dir) * t;
				int pz = z - Dirs.dz(dir) * t;
				if (!wall(px, pz)) {
					continue;
				}
				for (int y = level; y <= level + 1; y++) {
					d.set(px, y, pz, air);
				}
				// the arch's head in the dressing
				if (Mason.ok(d, px, level + 2, pz)) {
					try (var s = d.stage(StageKind.WALLS)) {
						d.set(px, level + 2, pz, m.dress.full());
					}
				}
				try (var s = d.stage(StageKind.FLOORS)) {
					d.set(px, level - 1, pz, m.paving.full());
				}
				// the drip course beside the threshold outside goes: it would be a ledge out of the doorway
				for (Direction side : Dirs.HORIZONTAL) {
					int nx = px + Dirs.dx(side);
					int nz = pz + Dirs.dz(side);
					if (!in(nx, nz) && Mason.course(d.get(nx, level - 1, nz)) && d.isEmpty(nx, level, nz)) {
						d.set(nx, level - 1, nz, air);
					}
				}
			}
			this.doorways.add(new int[]{x, z, level});
		}
		if (door && width == 1) {
			var doorBlock = d.mat(dev.voidmc.hearth.build.style.Role.DOOR).mainOr(Form.DOOR, Blocks.DARK_OAK_DOOR);
			d.inStage(StageKind.OPENINGS, () -> d.door(fx, level, fz, doorBlock, dir, net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT));
		}
		return new int[]{fx + Dirs.dx(dir), fz + Dirs.dz(dir)};
	}

	/** Arrow loops up the faces in {@code dirs}, staggered, from the ground floor to the head, avoiding doorways. */
	public Tower loops(Draft d, Set<Direction> dirs, int from, int to) {
		int k = 0;
		for (int y = from; y < to; y += 3) {
			for (Direction dir : ordered(dirs)) {
				int[] f = face(dir);
				Direction along = Dirs.right(dir);
				int off = this.diam >= 9 && ((k + y) & 1) == 0 ? (this.round ? 0 : 2) : 0;
				int x = f[0] + Dirs.dx(along) * off;
				int z = f[1] + Dirs.dz(along) * off;
				if (blockedByDoor(x, z, y) || !wall(x, z)) {
					continue;
				}
				for (int t = 0; t < this.thick; t++) {
					Mason.loop(d, x - Dirs.dx(dir) * t, y, z - Dirs.dz(dir) * t, 2);
				}
				// a grille in the loop's foot on the outer face: a slit two high in a wall one or two thick is a hole a
				// person could climb into and fall out of
				if (Mason.ok(d, x, y, z)) {
					d.set(x, y, z, Blocks.IRON_BARS.defaultBlockState());
				}
			}
			k++;
		}
		return this;
	}

	private boolean blockedByDoor(int x, int z, int y) {
		for (int[] dw : this.doorways) {
			if (Math.abs(dw[0] - x) + Math.abs(dw[1] - z) <= 1 && y >= dw[2] - 1 && y <= dw[2] + 2) {
				return true;
			}
		}
		return false;
	}

	/** A window of glass, one wide and {@code h} high, in the face toward {@code dir} at standing level {@code y}. */
	public void window(Draft d, Dress m, Direction dir, int y, int h) {
		int[] f = face(dir);
		if (blockedByDoor(f[0], f[1], y) || !wall(f[0], f[1])) {
			return;
		}
		try (var s = d.stage(StageKind.OPENINGS)) {
			for (int t = 0; t < this.thick; t++) {
				int x = f[0] - Dirs.dx(dir) * t;
				int z = f[1] - Dirs.dz(dir) * t;
				for (int k = 0; k < h; k++) {
					d.set(x, y + k, z, t == 0 ? m.glass.pane() : Paint.of(Blocks.AIR.defaultBlockState()));
				}
			}
		}
		int ox = f[0] + Dirs.dx(dir);
		int oz = f[1] + Dirs.dz(dir);
		Mason.sill(d, m, ox, y - 1, oz, dir.getOpposite());
		try (var s = d.stage(StageKind.WALLS)) {
			if (Mason.ok(d, f[0], y + h, f[1])) {
				d.set(f[0], y + h, f[1], m.dress.full());
			}
		}
	}

	// ------------------------------------------------------------------------------------------- lighting

	/**
	 * Lights every storey of the inside: a lantern on a chain from each floor's ceiling, clear of the stair (sconces on
	 * the wall in a small tower), and the rule filled by the kit where that is not enough. Call after floors and furniture.
	 */
	public void light(Draft d, int ceiling) {
		List<Integer> levels = new ArrayList<>();
		levels.add(this.base);
		levels.addAll(this.floorLevels);
		for (int i = 0; i < levels.size(); i++) {
			int l = levels.get(i);
			int c = i + 1 < levels.size() ? levels.get(i + 1) - 1 : ceiling;
			int[] spot = lampSpot(d, l, c);
			if (spot != null && c - l >= 3) {
				Lighting.chandelier(d, spot[0], spot[1], c, Math.max(l + 2, c - 2));
			}
			Lighting.room(d, this.x0, this.z0, this.x1, this.z1, l);
		}
	}

	private int @Nullable [] lampSpot(Draft d, int level, int ceiling) {
		int best = -1;
		int[] out = null;
		for (int[] c : cells()) {
			if (!inside(c[0], c[1]) || stairCell(c[0], c[1])) {
				continue;
			}
			if (!d.isSolid(c[0], ceiling, c[1])) {
				continue;
			}
			boolean clear = true;
			for (int y = level; y < ceiling; y++) {
				if (!d.isEmpty(c[0], y, c[1])) {
					clear = false;
					break;
				}
			}
			if (!clear) {
				continue;
			}
			int score = 100 - Math.abs(c[0] - this.cx) - Math.abs(c[1] - this.cz);
			if (blockedByDoor(c[0], c[1], level)) {
				score -= 50;
			}
			if (score > best) {
				best = score;
				out = c;
			}
		}
		return out;
	}

	// ------------------------------------------------------------------------------------------- the head

	/**
	 * The head over the walls: the deck or top room at {@code top}, and the cap of {@link #cap}. {@code maxTop} is the
	 * highest row allowed (inclusive). Returns the highest row used.
	 */
	public int head(Draft d, Dress m, Manner man, int maxTop) {
		int hi;
		switch (this.cap) {
			case CRENEL -> hi = crenel(d, m, man, false);
			case DOME -> hi = domed(d, m, man, maxTop);
			case HOARD -> hi = hoard(d, m, man, maxTop);
			case ONION, PAGODA -> hi = crowned(d, m, man, maxTop);
			case PYRAMID -> hi = capped(d, m, man, maxTop, false);
			default -> hi = capped(d, m, man, maxTop, true);
		}
		this.highest = hi;
		return hi;
	}

	/** The deck: stone over the inside at top - 1, the stair's opening left. */
	private void deck(Draft d, Dress m, int y, boolean extended) {
		try (var s = d.stage(StageKind.FLOORS)) {
			List<int[]> cs = cells();
			if (extended) {
				cs = new ArrayList<>(cs);
				cs.addAll(around());
			}
			for (int[] c : cs) {
				if (!Mason.ok(d, c[0], y, c[1])) {
					continue;
				}
				if (this.stairX != Integer.MIN_VALUE && stairNeedsAir(c[0], y, c[1])) {
					continue;
				}
				if (stairCell(c[0], c[1]) && isStair(d, c[0], y, c[1])) {
					continue;
				}
				if (wall(c[0], c[1]) || inside(c[0], c[1])) {
					d.set(c[0], y, c[1], inside(c[0], c[1]) ? m.paving.full() : m.wall.full());
				} else {
					d.set(c[0], y, c[1], m.paving.full());
				}
			}
		}
	}

	/** An open battlemented deck (corbelled out over machicolations where the manner has them). */
	private int crenel(Draft d, Dress m, Manner man, boolean forceFlush) {
		int t = this.top;
		boolean mach = man.machicolate() && !forceFlush && corbelRoom(d, t);
		deck(d, m, t - 1, mach);
		List<int[]> parapet = mach ? around() : ringCells();
		if (mach) {
			// corbels under the oversailing parapet, two courses
			for (int[] c : around()) {
				Direction toWall = out(c[0], c[1]).getOpposite();
				if (!wall(c[0] + Dirs.dx(toWall), c[1] + Dirs.dz(toWall))) {
					// a corner cell of a square tower: carried on the deck course
					try (var s = d.stage(StageKind.WALLS)) {
						if (Mason.ok(d, c[0], t - 2, c[1])) {
							d.set(c[0], t - 2, c[1], m.dress.stairsOrSlab(toWall, Half.TOP));
						}
					}
					continue;
				}
				try (var s = d.stage(StageKind.WALLS)) {
					if (Mason.ok(d, c[0], t - 2, c[1])) {
						d.set(c[0], t - 2, c[1], m.dress.stairsOrSlab(toWall, Half.TOP));
					}
				}
			}
		}
		parapetRing(d, m, man, parapet, t, mach);
		return t + 2;
	}

	private boolean corbelRoom(Draft d, int t) {
		for (int[] c : around()) {
			if (!Mason.inPlot(d, c[0], c[1]) && !(c[1] == -1 && t - 2 >= 3)) {
				return false;
			}
		}
		return true;
	}

	private List<int[]> ringCells() {
		List<int[]> out = new ArrayList<>();
		for (int[] c : cells()) {
			if (this.round ? Round.edge(this.cx, this.cz, this.diam, c[0], c[1]) : c[0] == this.x0 || c[0] == this.x1 || c[1] == this.z0 || c[1] == this.z1) {
				out.add(c);
			}
		}
		return out;
	}

	/** A parapet at standing level t round the given ring, merlons at t + 1. */
	private void parapetRing(Draft d, Dress m, Manner man, List<int[]> ring, int t, boolean outer) {
		List<int[]> ordered = order(ring);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int i = 0; i < ordered.size(); i++) {
				int[] c = ordered.get(i);
				if (!Mason.ok(d, c[0], t, c[1])) {
					continue;
				}
				d.set(c[0], t, c[1], man.merlon() == Manner.Merlon.TIMBER ? Mason.post(m.frame) : m.wall.full());
				Direction o = out(c[0], c[1]);
				boolean corner = !this.round && outer ? isOuterCorner(c[0], c[1]) : corner(c[0], c[1]);
				Mason.merlon(d, m, man.merlon(), c[0], t + 1, c[1], i, Dirs.right(o), o, corner);
			}
		}
	}

	private boolean isOuterCorner(int x, int z) {
		return (x == this.x0 - 1 || x == this.x1 + 1) && (z == this.z0 - 1 || z == this.z1 + 1);
	}

	/** Cells of a ring ordered round its centre (so merlons alternate along it). */
	private List<int[]> order(List<int[]> ring) {
		List<int[]> sorted = new ArrayList<>(ring);
		double mx = (this.x0 + this.x1) / 2.0;
		double mz = (this.z0 + this.z1) / 2.0;
		sorted.sort((a, b) -> Double.compare(Math.atan2(a[1] - mz, a[0] - mx), Math.atan2(b[1] - mz, b[0] - mx)));
		return sorted;
	}

	/** A cone (round) or pyramid (square) over the walls carried up two rows over the top room's floor. */
	private int capped(Draft d, Dress m, Manner man, int maxTop, boolean cone) {
		int t = this.top;
		int wallTop = t + 1;
		deck(d, m, t - 1, false);
		// the top room's walls
		raiseWalls(d, m, t, wallTop);
		int eave = wallTop + 1;
		double r = this.diam / 2.0 + 1;
		double pitch = cone ? conePitch(man, this.diam) : pyramidPitch(man, this.diam);
		pitch = Math.max(0.75, Math.min(pitch, (maxTop - 2 - eave) / Math.max(1.0, r)));
		if (cone && this.round) {
			int hi = cone(d, eave, this.diam + 2, pitch, maxTop);
			return finial(d, m, man, hi, maxTop);
		}
		Roofs.Roof roof = roof(d, m, eave, pitch, 1, false);
		fillUnder(d, m, roof, wallTop);
		int hi = roof.ridgeY;
		hi = finial(d, m, man, hi, maxTop);
		return hi;
	}

	/** The walls of the top room carried from {@code from} to {@code to}, with small windows on the four faces. */
	private void raiseWalls(Draft d, Dress m, int from, int to) {
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : cells()) {
				if (!wall(c[0], c[1])) {
					continue;
				}
				for (int y = from; y <= to; y++) {
					if (Mason.ok(d, c[0], y, c[1])) {
						d.set(c[0], y, c[1], corner(c[0], c[1]) ? m.dress.full() : m.wall.full());
					}
				}
			}
			for (int[] c : cells()) {
				if (inside(c[0], c[1])) {
					for (int y = from; y <= to; y++) {
						if (Mason.ok(d, c[0], y, c[1]) && !(stairCell(c[0], c[1]) && isStair(d, c[0], y, c[1]))) {
							d.set(c[0], y, c[1], Blocks.AIR.defaultBlockState());
						}
					}
				}
			}
		}
	}

	/** Rasterises the cap: a cone or a hip over the tower, {@code over} blocks of eaves. */
	private Roofs.Roof roof(Draft d, Dress m, int eave, double pitch, int over, boolean cone) {
		Roofs.Field f;
		int ex0 = this.x0 - over;
		int ez0 = this.z0 - over;
		int ex1 = this.x1 + over;
		int ez1 = this.z1 + over;
		if (cone) {
			f = Roofs.cone(this.cx, this.cz, this.diam / 2.0 + over, pitch);
		} else {
			f = Roofs.hip(ex0, ez0, ex1, ez1, Roofs.Profile.straight(pitch));
		}
		ex0 = Math.max(0, ex0);
		ez0 = Math.max(-1, ez0);
		ex1 = Math.min(d.frontage() - 1, ex1);
		ez1 = Math.min(d.depth() - 1, ez1);
		Roofs.Roof r;
		try (var s = d.stage(StageKind.ROOF)) {
			r = Roofs.raster(d, f, eave, ex0, ez0, ex1, ez1, m.roof, m.trim, Habits.Ridge.FULL);
		}
		Mason.solidify(d, r, m.roof);
		return r;
	}

	/**
	 * A round cone laid as builders lay them: a disc of the roofing at each row, its rim stepped in stairs where the row
	 * above draws in, every disc a cell narrower than the last every {@code pitch} rows (rounded), from a ring
	 * {@code eaveDiam} wide at {@code y0} to a point. Returns the top row.
	 */
	public int cone(Draft d, int y0, int eaveDiam, double pitch, int maxTop) {
		int r0 = eaveDiam / 2;
		List<Set<Long>> rows = new ArrayList<>();
		for (int r = r0; r >= 0; r--) {
			int n = (int) Math.round(pitch * (r0 - r + 1)) - (int) Math.round(pitch * (r0 - r));
			n = Math.max(1, n);
			Set<Long> row = new HashSet<>();
			for (int[] c : Round.disc(this.cx, this.cz, 2 * r + 1)) {
				row.add(Shapes.cell(c[0], c[1]));
			}
			for (int k = 0; k < n; k++) {
				rows.add(row);
			}
		}
		while (!rows.isEmpty() && y0 + rows.size() - 1 > maxTop) {
			// too tall for the plot: drop a held row from the middle
			int i = rows.size() / 2;
			rows.remove(i);
		}
		try (var s = d.stage(StageKind.ROOF)) {
			return Shapes.stack(d, this.cx, this.cz, y0, rows, Stone.of(d, dev.voidmc.hearth.build.style.Role.ROOF).full(),
				Stone.of(d, dev.voidmc.hearth.build.style.Role.ROOF));
		}
	}

	/** The wall carried up to meet the underside of a cap, and the roof space over the inside left clear. */
	private void fillUnder(Draft d, Dress m, Roofs.Roof roof, int wallTop) {
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : cells()) {
				if (!wall(c[0], c[1]) || !roof.covers(c[0], c[1])) {
					continue;
				}
				for (int y = wallTop + 1; y < roof.bottomY(c[0], c[1]); y++) {
					if (Mason.ok(d, c[0], y, c[1])) {
						d.set(c[0], y, c[1], m.wall.full());
					}
				}
			}
		}
	}

	/** A finial over the apex: a post of the frame with a lantern or a banner pole, if there is room. */
	private int finial(Draft d, Dress m, Manner man, int hi, int maxTop) {
		int apex = hi + 1;
		if (apex + 1 > maxTop || !d.isSolid(this.cx, hi, this.cz) && !d.isSolid(this.cx, hi - 1, this.cz)) {
			return hi;
		}
		int y = hi + 1;
		while (y <= maxTop && d.isSolid(this.cx, y, this.cz)) {
			y++;
		}
		if (y + 1 > maxTop) {
			return hi;
		}
		if (this.flagFly != null && y + 3 <= maxTop) {
			return Flag.pole(d, m, this.cx, y, this.cz, 4, this.flagFly, 3, maxTop);
		}
		try (var s = d.stage(StageKind.ROOF)) {
			d.set(this.cx, y, this.cz, m.frame.has(Form.FENCE) ? m.frame.fence() : Paint.of(Blocks.IRON_BARS.defaultBlockState()));
			d.set(this.cx, y + 1, this.cz, dev.voidmc.hearth.build.kit.landmark.Details.rod());
		}
		return y + 1;
	}

	/**
	 * A timber hoarding: beams cantilevered out at top - 2 carry a boarded gallery one block outside the walls (its floor
	 * the top room's, extended), its boards pierced for shooting, under a steep cap reaching over it.
	 */
	private int hoard(Draft d, Dress m, Manner man, int maxTop) {
		int t = this.top;
		if (!corbelRoom(d, t)) {
			return capped(d, m, man, maxTop, this.round);
		}
		// beams under the gallery: logs pointing out every other cell, on corbels
		List<int[]> ring = order(around());
		try (var s = d.stage(StageKind.FRAME)) {
			for (int i = 0; i < ring.size(); i++) {
				int[] c = ring.get(i);
				Direction o = out(c[0], c[1]);
				if (!Mason.ok(d, c[0], t - 2, c[1])) {
					continue;
				}
				if ((i & 1) == 0) {
					d.set(c[0], t - 2, c[1], Mason.beam(m.beam, o.getAxis()));
				}
			}
		}
		// the gallery floor (the top room's floor carried out), its boarded walls, slits
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int[] c : ring) {
				if (Mason.ok(d, c[0], t - 1, c[1])) {
					d.set(c[0], t - 1, c[1], m.boards.has(Form.SLAB) ? m.boards.slab(SlabType.TOP) : m.boards.full());
				}
			}
		}
		deck(d, m, t - 1, false);
		// the masonry stops at the gallery floor; the room under the cap spans the gallery
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : cells()) {
				for (int y = t; y <= t + 1; y++) {
					if (Mason.ok(d, c[0], y, c[1]) && !(stairCell(c[0], c[1]) && isStair(d, c[0], y, c[1]))) {
						d.set(c[0], y, c[1], Blocks.AIR.defaultBlockState());
					}
				}
			}
			for (int i = 0; i < ring.size(); i++) {
				int[] c = ring.get(i);
				for (int y = t; y <= t + 1; y++) {
					if (!Mason.ok(d, c[0], y, c[1])) {
						continue;
					}
					boolean slit = y == t && Math.floorMod(i, 4) == 2;
					boolean post = Math.floorMod(i, 4) == 0;
					if (post) {
						d.set(c[0], y, c[1], Mason.post(m.frame));
					} else if (slit) {
						d.set(c[0], y, c[1], m.boards.has(Form.TRAPDOOR) ? m.boards.trapdoor(out(c[0], c[1]).getOpposite(), Half.TOP, true)
							: Paint.of(Blocks.AIR.defaultBlockState()));
					} else {
						d.set(c[0], y, c[1], m.boards.full());
					}
				}
			}
		}
		int eave = t + 2;
		double r = this.diam / 2.0 + 2;
		double pitch = this.round ? conePitch(man, this.diam) : pyramidPitch(man, this.diam);
		pitch = Math.max(0.75, Math.min(pitch, (maxTop - 2 - eave) / Math.max(1.0, r)));
		if (this.round) {
			int hi = cone(d, eave, this.diam + 4, pitch, maxTop);
			return finial(d, m, man, hi, maxTop);
		}
		Roofs.Roof roof = roof(d, m, eave, pitch, 2, false);
		// the gallery's walls meet the cap
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : ring) {
				if (!roof.covers(c[0], c[1])) {
					continue;
				}
				for (int y = t + 2; y < roof.bottomY(c[0], c[1]); y++) {
					if (Mason.ok(d, c[0], y, c[1])) {
						d.set(c[0], y, c[1], m.boards.full());
					}
				}
			}
		}
		return finial(d, m, man, roof.ridgeY, maxTop);
	}

	/** An onion on a drum (square plan through the crowns kit; round towers turn one on the lathe) or a curved hip on posts. */
	private int crowned(Draft d, Dress m, Manner man, int maxTop) {
		int t = this.top;
		if (this.cap == Manner.Cap.PAGODA && !this.round) {
			deck(d, m, t - 1, false);
			Crowns.Mats mats = Crowns.Mats.of(d, m.dress);
			return Crowns.crown(d, Crowns.Kind.PAGODA, this.x0, this.z0, this.x1, this.z1, t, maxTop, mats);
		}
		if (this.cap == Manner.Cap.ONION && !this.round) {
			// a parapet deck round a drum carrying the onion
			deck(d, m, t - 1, false);
			Crowns.Mats mats = Crowns.Mats.coppered(d, m.dress, 0.6);
			return Crowns.crown(d, Crowns.Kind.ONION, this.x0, this.z0, this.x1, this.z1, t, maxTop, mats);
		}
		return capped(d, m, man, maxTop, true);
	}

	/** A small dome over the deck (the tropics' garitas, the desert's domed towers): a lookout under a dome on posts. */
	private int domed(Draft d, Dress m, Manner man, int maxTop) {
		int t = this.top;
		int hi = crenel(d, m, man, true);
		if (this.diam > 9 || this.diam < 5) {
			return hi;
		}
		// the dome springs from a ring of stone posts standing on the deck inside the parapet
		double r = (this.diam - 3) / 2.0;
		List<Set<Long>> rows = Shapes.domeRows(this.cx, this.cz, r);
		int spring = t + 2;
		if (spring + rows.size() > maxTop) {
			return hi;
		}
		Set<Long> foot = rows.get(0);
		try (var s = d.stage(StageKind.WALLS)) {
			for (long k : foot) {
				int x = (int) (k >> 32);
				int z = (int) k;
				boolean rim = !foot.contains(Shapes.cell(x + 1, z)) || !foot.contains(Shapes.cell(x - 1, z)) || !foot.contains(Shapes.cell(x, z + 1))
					|| !foot.contains(Shapes.cell(x, z - 1));
				boolean pier = rim && (Math.abs(x - this.cx) == Math.abs(z - this.cz) || Math.abs(x - this.cx) + Math.abs(z - this.cz) <= 1);
				if (pier && !stairFoot(x, z)) {
					d.set(x, t, z, m.dress.wallOrFull());
					d.set(x, t + 1, z, m.dress.wallOrFull());
				}
			}
		}
		Stone sheet = Stone.sheet(d);
		try (var s = d.stage(StageKind.ROOF)) {
			int top = Shapes.stack(d, this.cx, this.cz, spring, rows, sheet.full(), sheet);
			return Math.max(hi, top);
		}
	}

	/**
	 * Bartizans: corbelled corner turrets hung on the four corners of a square tower at standing level {@code y} (see
	 * {@link Turret#corner}), where the plot leaves room. Returns the highest row used.
	 */
	public int bartizans(Draft d, Dress m, Manner man, int y, int maxTop) {
		if (this.round) {
			return y;
		}
		int hi = y;
		int[][] corners = {{this.x0, this.z0, -1, -1}, {this.x1, this.z0, 1, -1}, {this.x0, this.z1, -1, 1}, {this.x1, this.z1, 1, 1}};
		for (int[] c : corners) {
			hi = Math.max(hi, Turret.corner(d, m, man, c[0], c[1], c[2], c[3], y, maxTop));
		}
		return hi;
	}

	/** Every POI a tower head offers a guard: a post at each cardinal face of the deck and a viewpoint. */
	public void watch(Draft d, Direction look) {
		int[] f = face(look);
		int x = f[0] - Dirs.dx(look);
		int z = f[1] - Dirs.dz(look);
		if (!inside(x, z) && this.round) {
			x = this.cx + Dirs.dx(look);
			z = this.cz + Dirs.dz(look);
		}
		d.poi(PoiType.GUARD_POST, x, this.top, z, look);
		d.poi(PoiType.VIEWPOINT, x, this.top, z, look);
	}

	/**
	 * The directions of a set in a fixed order (front, right, back, left). A {@code Set.of}'s order changes from one run of
	 * the game to the next, so anything that chooses by walking one must walk it through here, or the blueprint is not the
	 * same on every server.
	 */
	public static List<Direction> ordered(java.util.Collection<Direction> dirs) {
		List<Direction> out = new ArrayList<>(4);
		for (Direction dir : Dirs.HORIZONTAL) {
			if (dirs.contains(dir)) {
				out.add(dir);
			}
		}
		return out;
	}

	/** The set of the four horizontal directions. */
	public static Set<Direction> all() {
		Set<Direction> s = java.util.EnumSet.noneOf(Direction.class);
		java.util.Collections.addAll(s, Dirs.HORIZONTAL);
		return s;
	}
}
