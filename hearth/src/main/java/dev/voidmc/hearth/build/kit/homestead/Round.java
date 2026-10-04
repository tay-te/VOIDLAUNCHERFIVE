package dev.voidmc.hearth.build.kit.homestead;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Role;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Round buildings: a ring wall on a plinth with posts at the quarters, a painted band under the eaves, a cone roof of
 * thatch, reed or tile overhanging the wall with a finial at its point; the room inside furnished round a fire. Round
 * houses, round huts and round granaries draw with it.
 */
public final class Round {
	private Round() {
	}

	/** A ring: its centre (half-block centres allowed), radius, the cells of its wall and of its floor. */
	public record Ring(double cx, double cz, double r, List<int[]> wall, List<int[]> disc, Set<Long> wallSet, Set<Long> discSet) {
		public boolean isWall(int x, int z) {
			return this.wallSet.contains(key(x, z));
		}

		public boolean inside(int x, int z) {
			return this.discSet.contains(key(x, z)) && !isWall(x, z);
		}

		public int x0() {
			return (int) Math.floor(this.cx - this.r);
		}

		public int x1() {
			return (int) Math.ceil(this.cx + this.r);
		}

		public int z0() {
			return (int) Math.floor(this.cz - this.r);
		}

		public int z1() {
			return (int) Math.ceil(this.cz + this.r);
		}

		/** The wall cell on the given side of the centre, in line with it (the door's place on the front). */
		public int[] cellToward(Direction dir) {
			int bx = (int) Math.round(this.cx);
			int bz = (int) Math.round(this.cz);
			int[] best = null;
			for (int[] c : this.wall) {
				boolean inLine = dir.getAxis() == Direction.Axis.Z ? c[0] == bx : c[1] == bz;
				if (!inLine) {
					continue;
				}
				if (best == null || Dirs.dx(dir) * c[0] + Dirs.dz(dir) * c[1] > Dirs.dx(dir) * best[0] + Dirs.dz(dir) * best[1]) {
					best = c;
				}
			}
			return best;
		}
	}

	static long key(int x, int z) {
		return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
	}

	public static Ring ring(double cx, double cz, double r) {
		List<int[]> wall = Draft2.ellipse(cx, cz, r, false);
		List<int[]> disc = Draft2.ellipse(cx, cz, r, true);
		Set<Long> ws = new HashSet<>();
		Set<Long> ds = new HashSet<>();
		for (int[] c : wall) {
			ws.add(key(c[0], c[1]));
		}
		for (int[] c : disc) {
			ds.add(key(c[0], c[1]));
		}
		return new Ring(cx, cz, r, wall, disc, ws, ds);
	}

	/** Shape helpers on the draft's ellipse rasteriser. */
	static final class Draft2 {
		static List<int[]> ellipse(double cx, double cz, double r, boolean filled) {
			return dev.voidmc.hearth.build.draft.Draft.ellipseCells(cx, cz, r, r, filled);
		}
	}

	/** Whether a wall cell is one of the four (or eight on a big ring) where a post stands. */
	public static boolean post(Ring ring, int[] c) {
		double a = Math.atan2(c[1] + 0.0 - ring.cz, c[0] + 0.0 - ring.cx);
		int n = ring.r >= 3.5 ? 8 : 4;
		double step = 2 * Math.PI / n;
		double off = Math.abs(Math.IEEEremainder(a - step / 2, step));
		return off < 0.28 / Math.max(1, ring.r / 3);
	}

	/** How a rendered ring wall is painted between its plinth and its head band. */
	public enum Pattern {
		/** Plain. */
		NONE,
		/** A row of triangles standing on a painted dado. */
		TRIANGLES,
		/** A stepped zigzag. */
		STEPS,
		/** Lozenges between two lines. */
		LOZENGES
	}

	/** {@link #walls(Draft, Ring, int, int, Pattern)} without paintings. */
	public static void walls(Draft d, Ring ring, int y, int plate) {
		walls(d, ring, y, plate, Pattern.NONE);
	}

	/**
	 * The ring wall from standing level y up to {@code plate}: a plinth course, the culture's wall, a band of the accent
	 * colour on the top course (rendered walls) or a ring beam (timber), posts at the quarters in timber, plank and
	 * bamboo walls (a mud or plaster wall is all of a piece). A rendered wall may be painted between plinth and band:
	 * triangles, a stepped zigzag or lozenges in the accent colour (the steppe's painted houses).
	 */
	public static void walls(Draft d, Ring ring, int y, int plate, Pattern pattern) {
		Mat wall = d.mat(Role.WALL);
		Mat frame = d.mat(Role.FRAME);
		Mat plinth = d.mat(Role.PLINTH);
		Mat accent = d.mat(Role.WALL_ACCENT);
		var kind = d.habits().walls().kind();
		boolean rendered = kind == Habits.WallKind.PLASTER || kind == Habits.WallKind.ADOBE || kind == Habits.WallKind.STONE;
		boolean painted = rendered && pattern != Pattern.NONE;
		// the painting in whichever of the accent and the plinth stands out from the wall (an ochre wall is painted in
		// the dark earth, a pale one in the ochre)
		Mat motif = accent.main(Form.FULL) != wall.main(Form.FULL) ? accent : plinth;
		Mat second = motif == accent ? plinth : accent;
		// a painted band is laid on one even ground (the wall's main colour), so the figures read crisp
		Paint ground = Paint.of(wall.mainOr(Form.FULL, Blocks.TERRACOTTA).defaultBlockState());
		int n = ring.wall.size();
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : ring.wall) {
				boolean post = !rendered && post(ring, c);
				// the cell's place round the ring, in cells
				double a = Math.atan2(c[1] - ring.cz, c[0] - ring.cx);
				int i = Math.floorMod((int) Math.round((a + Math.PI) / (2 * Math.PI) * n), Math.max(1, n));
				for (int yy = y; yy <= plate; yy++) {
					Paint p;
					int row = yy - y - 1;
					if (yy == y && !post) {
						p = plinth.full();
					} else if (post) {
						p = frame.log(Direction.Axis.Y);
					} else if (yy == plate && rendered) {
						p = accent.full();
					} else if (painted && paint(pattern, i, row, plate - y - 1)) {
						p = (figure(pattern, i, plate - y - 1) & 1) == 1 ? second.full() : motif.full();
					} else if (painted && row >= 0 && row < plate - y - 1) {
						p = ground;
					} else if (kind == Habits.WallKind.PLANK || kind == Habits.WallKind.BAMBOO) {
						p = yy == plate ? d.mat(Role.BEAM).log(Direction.Axis.Y) : d.mat(Role.GABLE).full();
					} else if (kind == Habits.WallKind.LOG) {
						p = wall.strippedLog(Direction.Axis.Y).orElse(wall.full());
					} else {
						p = wall.full();
					}
					d.set(c[0], yy, c[1], p);
				}
			}
		}
		for (int[] c : ring.disc) {
			if (!ring.isWall(c[0], c[1])) {
				d.carve(c[0], y, c[1], c[0], plate, c[1]);
			}
		}
	}

	/** Whether the painting covers cell i of the ring at {@code row} of a band {@code rows} high. */
	static boolean paint(Pattern p, int i, int row, int rows) {
		if (row < 0 || row >= rows) {
			return false;
		}
		if (rows >= 3) {
			// bold figures three courses high
			int m = Math.floorMod(i, 6);
			int k = Math.floorMod(i, 4);
			return switch (p) {
				case TRIANGLES -> row == 0 ? m <= 4 : row == 1 ? m >= 1 && m <= 3 : row == 2 && m == 2;
				case STEPS -> row == 0 ? k <= 1 : row == 1 ? k == 1 || k == 2 : row == 2 && k >= 2;
				case LOZENGES -> row == 1 ? m >= 1 && m <= 3 : row <= 2 && m == 2 || row == 0 && m == 5;
				case NONE -> false;
			};
		}
		int m = Math.floorMod(i, 4);
		return switch (p) {
			case TRIANGLES -> row == 0 ? m != 3 : row == 1 && m == 1;
			case STEPS -> row == 0 ? m == 0 || m == 1 : row == 1 && (m == 1 || m == 2);
			case LOZENGES -> row == 0 ? m == 1 || m == 2 : m == 2 || m == 3;
			case NONE -> false;
		};
	}

	/** Which figure of the painting cell i belongs to (to alternate their colours). */
	static int figure(Pattern p, int i, int rows) {
		return Math.floorDiv(i, rows >= 3 && p != Pattern.STEPS ? 6 : 4);
	}

	/**
	 * A cone roof over the ring, overhanging the wall by {@code eaves}, its eave at {@code plate}; the wall filled up to
	 * the roof's underside; a finial at the point (a clay pot where the culture works clay, a post and a ball of thatch
	 * elsewhere). Covered in {@code roof} (the culture's roofing, or its thatch). Returns the roof.
	 */
	public static Roofs.Roof cone(Draft d, Ring ring, int plate, int eaves, double pitchIn, Mat roof) {
		return cone(d, ring, plate, eaves, pitchIn, roof, false);
	}

	/**
	 * As {@link #cone(Draft, Ring, int, int, double, Mat)}; with {@code smokeHole} the point of a cone over an odd ring is
	 * left open round a post that carries a little cap a block above it, so the smoke of the fire below finds its way
	 * out (and the cap reads against the sky).
	 */
	public static Roofs.Roof cone(Draft d, Ring ring, int plate, int eaves, double pitchIn, Mat roof, boolean smokeHole) {
		double rr = ring.r + eaves;
		// a covering laid in whole blocks (bales of thatch) steps a block per ring, so each course rests on the one below
		double pitch = Math.min(1.0, pitchIn);
		// a deep eave flares out gently over a veranda (a curved skirt) so folk walk upright beneath it
		boolean flare = eaves >= 2;
		java.util.function.DoubleUnaryOperator prof = dist -> flare ? 1 + (dist <= 2 ? 0.5 * dist : 1 + pitch * (dist - 2)) : 1 + pitch * dist;
		double cx = ring.cx;
		double cz = ring.cz;
		Roofs.Field f = (x, z) -> {
			double rad = Math.hypot(x - cx, z - cz);
			if (rad > rr + 0.5) {
				return Double.NaN;
			}
			return prof.applyAsDouble(Math.max(0, rr + 0.5 - rad - 0.5));
		};
		int rx0 = Math.max(0, (int) Math.floor(ring.cx - rr - 0.5));
		int rx1 = Math.min(d.frontage() - 1, (int) Math.ceil(ring.cx + rr + 0.5));
		int rz0 = Math.max(-1, (int) Math.floor(ring.cz - rr - 0.5));
		int rz1 = Math.min(d.depth() - 1, (int) Math.ceil(ring.cz + rr + 0.5));
		// the eave course a block over the wall head
		double hWall = prof.applyAsDouble(Math.max(0, eaves));
		int tWall = Math.max(1, (int) Math.round(hWall * 2));
		int y0 = plate + 1 - ((tWall + 1) / 2 - 1);
		Roofs.Roof r;
		try (var s = d.stage(StageKind.ROOF)) {
			r = Roofs.raster(d, f, y0, rx0, rz0, rx1, rz1, roof, roof, Habits.Ridge.FULL);
		}
		// overhang at the street side only where it is three up
		Mat wall = d.mat(Role.WALL);
		var kind = d.habits().walls().kind();
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : ring.wall) {
				int under = r.bottomY(c[0], c[1]);
				for (int yy = plate + 1; yy < under; yy++) {
					d.set(c[0], yy, c[1], kind == Habits.WallKind.PLANK || kind == Habits.WallKind.BAMBOO ? d.mat(Role.GABLE).full() : wall.full());
				}
			}
		}
		// the room open to the cone inside
		for (int[] c : ring.disc) {
			if (ring.isWall(c[0], c[1])) {
				continue;
			}
			int under = r.bottomY(c[0], c[1]);
			if (under - 1 > plate) {
				d.carve(c[0], plate + 1, c[1], c[0], under - 1, c[1]);
			}
		}
		// the finial
		int ax = (int) Math.round(ring.cx);
		int az = (int) Math.round(ring.cz);
		int top = r.surfaceY(ax, az);
		boolean odd = Math.abs(ring.cx - Math.rint(ring.cx)) < 0.01 && Math.abs(ring.cz - Math.rint(ring.cz)) < 0.01;
		if (smokeHole && odd && top != Integer.MIN_VALUE && top + 3 < d.maxHeight()) {
			Mat frame = d.mat(Role.FRAME);
			Mat trim = roof.has(Form.SLAB) ? roof : d.mat(Role.ROOF_TRIM);
			try (var s = d.stage(StageKind.ROOF)) {
				d.set(ax, top, az, frame.fence());
				d.set(ax, top + 1, az, trim.slab(net.minecraft.world.level.block.state.properties.SlabType.BOTTOM).orElse(trim.full()));
			}
			r.ridgeY = Math.max(r.ridgeY, top + 1);
			return r;
		}
		if (top != Integer.MIN_VALUE && top + 2 < d.maxHeight()) {
			boolean clay = d.mat(Role.CHIMNEY).main(Form.FULL) == Blocks.MUD_BRICKS || d.mat(Role.WALL_ACCENT).main(Form.FULL) != null
				&& String.valueOf(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(d.mat(Role.WALL_ACCENT).main(Form.FULL))).contains("terracotta");
			try (var s = d.stage(StageKind.ROOF)) {
				if (clay) {
					d.set(ax, top + 1, az, States.of(Blocks.DECORATED_POT));
				} else {
					d.set(ax, top + 1, az, d.mat(Role.FRAME).fence());
					d.set(ax, top + 2, az, d.mat(Role.FRAME).fence());
				}
			}
		}
		return r;
	}

	/**
	 * A skirt roof round a ring: a lean-to ring from the wall out to radius {@code outer} (over a veranda), its eave a
	 * slab course at {@code eaveY}, rising toward the wall by {@code pitch} a block. The cone over the ring stands above
	 * it, a band of wall between them (the two-tier roof of the tropics). Returns the skirt.
	 */
	public static Roofs.Roof skirt(Draft d, Ring ring, double outer, int eaveY, double pitch, Mat roof) {
		double cx = ring.cx;
		double cz = ring.cz;
		Roofs.Field f = (x, z) -> {
			double rad = Math.hypot(x - cx, z - cz);
			if (rad > outer + 0.5 || ring.inside(x, z) || ring.isWall(x, z)) {
				return Double.NaN;
			}
			return 0.5 + pitch * Math.max(0, outer - rad);
		};
		int rx0 = Math.max(0, (int) Math.floor(cx - outer - 0.5));
		int rx1 = Math.min(d.frontage() - 1, (int) Math.ceil(cx + outer + 0.5));
		int rz0 = Math.max(-1, (int) Math.floor(cz - outer - 0.5));
		int rz1 = Math.min(d.depth() - 1, (int) Math.ceil(cz + outer + 0.5));
		try (var s = d.stage(StageKind.ROOF)) {
			return Roofs.raster(d, f, eaveY, rx0, rz0, rx1, rz1, roof, roof, Habits.Ridge.FULL);
		}
	}

	/** A room over the ring's floor for {@link Rooms#furnish}: the rectangle round it with the cells outside the circle blocked. */
	public static Rooms.Room room(Ring ring, int y, int height) {
		int x0 = ring.x0() + 1;
		int x1 = ring.x1() - 1;
		int z0 = ring.z0() + 1;
		int z1 = ring.z1() - 1;
		Rooms.Room room = new Rooms.Room(x0, z0, x1, z1, y, height);
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				if (!ring.inside(x, z)) {
					room.block(x, z);
				}
			}
		}
		return room;
	}

	/** A fire in the middle of the floor (a campfire in a ring of stone), the hearth registered on its open side. */
	public static int[] fire(Draft d, Ring ring, Rooms.Room room, int y) {
		int ax = (int) Math.round(ring.cx);
		int az = (int) Math.round(ring.cz);
		List<int[]> spots = new ArrayList<>();
		spots.add(new int[]{ax, az});
		for (int[] c : ring.disc) {
			if (ring.inside(c[0], c[1])) {
				spots.add(c);
			}
		}
		spots.sort(java.util.Comparator.comparingDouble(c -> Math.hypot(c[0] - ring.cx, c[1] - ring.cz) + 0.01 * c[1]));
		for (int[] c : spots) {
			if (room.claim(c[0], c[1])) {
				int fx = c[0];
				int fz = c[1];
				d.inStage(StageKind.INTERIOR, () -> d.set(fx, y, fz, States.lit(Blocks.CAMPFIRE, true).trySetValue(BlockStateProperties.SIGNAL_FIRE, false)));
				for (Direction dir : new Direction[]{Dirs.FRONT, Dirs.LEFT, Dirs.RIGHT, Dirs.BACK}) {
					int nx = fx + Dirs.dx(dir);
					int nz = fz + Dirs.dz(dir);
					if (ring.inside(nx, nz) && d.isEmpty(nx, y, nz)) {
						d.poi(PoiType.HEARTH, nx, y, nz, dir.getOpposite());
						break;
					}
				}
				return c;
			}
		}
		return null;
	}

	/** Pots and baskets along the wall where the room can spare the floor: decorated pots and a barrel or two. */
	public static void pots(Draft d, Ring ring, Rooms.Room room, int y, int max) {
		int n = 0;
		for (int[] c : ring.disc) {
			if (n >= max || !ring.inside(c[0], c[1]) || !d.isEmpty(c[0], y, c[1])) {
				continue;
			}
			boolean byWall = false;
			for (Direction dir : Dirs.HORIZONTAL) {
				byWall |= ring.isWall(c[0] + Dirs.dx(dir), c[1] + Dirs.dz(dir));
			}
			if (!byWall || Noise.unit(d.seed() ^ 0x907, c[0], y, c[1]) > 0.35 || !room.claim(c[0], c[1])) {
				continue;
			}
			int fx = c[0];
			int fz = c[1];
			d.inStage(StageKind.INTERIOR, () -> d.set(fx, y, fz, Noise.unit(d.seed(), fx, y, fz) < 0.6 ? States.of(Blocks.DECORATED_POT)
				: States.facing(Blocks.BARREL, Direction.UP)));
			n++;
		}
	}
}
