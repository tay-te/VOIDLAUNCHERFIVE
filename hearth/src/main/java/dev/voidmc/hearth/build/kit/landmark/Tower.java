package dev.voidmc.hearth.build.kit.landmark;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.style.Form;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * A square masonry tower, raised stage by stage: a core of walls one block thick, clasping corner piers standing a block
 * proud of it (so each face is a recessed panel framed by pilasters), a plinth with a chamfered foot, string courses
 * that run round piers and panels alike, Lombard bands of little arches under them, openings that open up as the tower
 * rises (slits, single lights, paired and triple lights, open belfry arches, louvres), floors, and a spiral stair round a
 * central post. A tower draws nothing by itself: an archetype calls the stages it wants in the order a mason raises
 * them.
 *
 * <p>The core is {@code size} blocks square (odd sizes centre openings and the stair); its interior is
 * {@code size - 2}. A five-block core holds just the spiral; a seven-block one a landing all round it.
 */
public final class Tower {
	/** What fills a stage's openings. */
	public enum Opening {
		NONE, SLIT, SINGLE, BIFORA, TRIFORA, ARCH, LOUVRE
	}

	public final int x0;
	public final int z0;
	public final int x1;
	public final int z1;
	public final int size;
	public final int cx;
	public final int cz;
	public final Stone body;
	public final Stone dress;
	public final Stone base;
	/** Clasping corner piers a block proud of the faces. */
	public final boolean piers;

	public Tower(int x0, int z0, int size, Stone body, Stone dress, Stone base, boolean piers) {
		this.x0 = x0;
		this.z0 = z0;
		this.size = size;
		this.x1 = x0 + size - 1;
		this.z1 = z0 + size - 1;
		this.cx = x0 + (size - 1) / 2;
		this.cz = z0 + (size - 1) / 2;
		this.body = body;
		this.dress = dress;
		this.base = base;
		this.piers = piers;
	}

	/** The four faces of the core (front, right, back, left). */
	public Side[] faces() {
		return Side.of(this.x0, this.z0, this.x1, this.z1);
	}

	public Side face(Direction out) {
		return Side.of(this.x0, this.z0, this.x1, this.z1, out);
	}

	/** The outline including piers: {x0, z0, x1, z1}. */
	public int[] outer() {
		int p = this.piers ? 1 : 0;
		return new int[]{this.x0 - p, this.z0 - p, this.x1 + p, this.z1 + p};
	}

	/** The solid footprint of a wall row (core ring plus piers). */
	public Set<Long> footprint(boolean withPiers) {
		Set<Long> s = new HashSet<>();
		for (int z = this.z0; z <= this.z1; z++) {
			for (int x = this.x0; x <= this.x1; x++) {
				s.add(Shapes.cell(x, z));
			}
		}
		if (withPiers && this.piers) {
			for (int[] p : pierCells()) {
				s.add(Shapes.cell(p[0], p[1]));
			}
		}
		return s;
	}

	/** The cells of the four clasping piers outside the core. */
	public int[][] pierCells() {
		return new int[][]{
			{this.x0 - 1, this.z0 - 1}, {this.x0 - 1, this.z0}, {this.x0, this.z0 - 1},
			{this.x1 + 1, this.z0 - 1}, {this.x1 + 1, this.z0}, {this.x1, this.z0 - 1},
			{this.x0 - 1, this.z1 + 1}, {this.x0 - 1, this.z1}, {this.x0, this.z1 + 1},
			{this.x1 + 1, this.z1 + 1}, {this.x1 + 1, this.z1}, {this.x1, this.z1 + 1}};
	}

	// ------------------------------------------------------------------------------------------------ masonry

	/**
	 * The plinth: rows {@code [y0..y1]} of the core and piers solid in the base stone on its outer ring, the interior
	 * left to the caller, and a chamfer of stairs round the foot at {@code y1 + 1} where the plot allows.
	 */
	public void plinth(Draft d, int y0, int y1, boolean chamfer) {
		plinth(d, y0, y1, chamfer, -100000, -100000);
	}

	/** {@link #plinth}, keeping the chamfer clear of the cell (keepX, keepZ) and its two neighbours along the wall (a door). */
	public void plinth(Draft d, int y0, int y1, boolean chamfer, int keepX, int keepZ) {
		try (var s = d.stage(StageKind.WALLS)) {
			for (int y = y0; y <= y1; y++) {
				for (int[] c : Draft.ring(this.x0, this.z0, this.x1, this.z1)) {
					d.set(c[0], y, c[1], this.base.full());
				}
				if (this.piers) {
					for (int[] p : pierCells()) {
						d.set(p[0], y, p[1], this.base.full());
					}
				}
			}
			if (chamfer && this.base.has(Form.STAIRS)) {
				Set<Long> fp = footprint(true);
				for (int[] c : Shapes.around(fp)) {
					if (!dev.voidmc.hearth.build.kit.Walls.inPlot(d, c[0], c[1]) || !d.isEmpty(c[0], y1 + 1, c[1]) || d.site().ground(c[0], c[1]) > y1 + 1) {
						continue;
					}
					if (Math.abs(c[0] - keepX) + Math.abs(c[1] - keepZ) <= 1) {
						continue;
					}
					Direction in = towardShape(fp, c[0], c[1]);
					if (in != null) {
						d.set(c[0], y1, c[1], this.base.stairs(in, Half.BOTTOM));
						if (d.isAbsent(c[0], y1 - 1, c[1]) && d.site().ground(c[0], c[1]) < y1) {
							for (int y = d.site().ground(c[0], c[1]) - 1; y < y1; y++) {
								d.set(c[0], y, c[1], this.base.full());
							}
						}
					}
				}
			}
		}
	}

	/** The direction from a cell to a neighbouring cell of the shape (null at a diagonal). */
	static Direction towardShape(Set<Long> shape, int x, int z) {
		for (Direction dir : Dirs.HORIZONTAL) {
			if (Shapes.has(shape, x + Dirs.dx(dir), z + Dirs.dz(dir))) {
				return dir;
			}
		}
		return null;
	}

	/**
	 * Walls of rows {@code [y0..y1]}: the core ring in the body stone with toothed quoins in the dressing where there are
	 * no piers, and the piers in the dressing (their faces banded in the body stone every fourth course for texture).
	 */
	public void shaft(Draft d, int y0, int y1) {
		try (var s = d.stage(StageKind.WALLS)) {
			for (int y = y0; y <= y1; y++) {
				for (Side f : faces()) {
					for (int i = 0; i < f.length(); i++) {
						boolean corner = f.isCorner(i);
						boolean tooth = !this.piers && (i == 1 || i == f.length() - 2) && ((y & 1) == 0);
						d.set(f.x(i), y, f.z(i), corner || tooth ? this.dress.full() : this.body.full());
					}
				}
			}
		}
		if (this.piers) {
			try (var s = d.stage(StageKind.FRAME)) {
				for (int y = y0; y <= y1; y++) {
					for (int[] p : pierCells()) {
						d.set(p[0], y, p[1], this.dress.full());
					}
				}
			}
		}
	}

	/** Redraws the core ring of rows {@code [y0..y1]} in another stone (a belfry of ashlar over a rubble shaft). */
	public void ring(Draft d, int y0, int y1, Stone st) {
		try (var s = d.stage(StageKind.WALLS)) {
			for (int y = y0; y <= y1; y++) {
				for (int[] c : Draft.ring(this.x0, this.z0, this.x1, this.z1)) {
					d.set(c[0], y, c[1], st.full());
				}
			}
		}
	}

	/**
	 * A deep blind arch on every face: the middle {@code width} cells of each panel set back a further block for rows
	 * {@code [y0..y1]}, the head shouldered with stairs, a dressed sill. The interior ring is narrowed there (keep it to
	 * stages without landings).
	 */
	public void recess(Draft d, int y0, int y1, int width) {
		try (var s = d.stage(StageKind.WALLS)) {
			for (Side f : faces()) {
				int mid = f.middle();
				int a = mid - width / 2;
				int b = a + width - 1;
				Direction in = f.out().getOpposite();
				for (int i = a; i <= b; i++) {
					int x = f.x(i);
					int z = f.z(i);
					for (int y = y0; y <= y1; y++) {
						d.set(x, y, z, Blocks.AIR.defaultBlockState());
						d.set(x + Dirs.dx(in), y, z + Dirs.dz(in), this.body.full());
					}
					d.set(x, y0 - 1, z, this.dress.full());
					d.set(x, y1 + 1, z, this.dress.full());
				}
				if (width >= 2 && this.dress.has(Form.STAIRS)) {
					d.set(f.x(a), y1, f.z(a), this.dress.stairs(f.along().getOpposite(), Half.TOP));
					d.set(f.x(b), y1, f.z(b), this.dress.stairs(f.along(), Half.TOP));
				}
			}
		}
	}

	/** Air inside the core for rows {@code [y0..y1]} (only where nothing else is drawn). */
	public void hollow(Draft d, int y0, int y1) {
		d.carve(this.x0 + 1, y0, this.z0 + 1, this.x1 - 1, y1, this.z1 - 1);
	}

	/** A floor row at y over the whole interior (a stair drawn later cuts its well through it). */
	public void floor(Draft d, int y, Paint p) {
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = this.z0 + 1; z <= this.z1 - 1; z++) {
				for (int x = this.x0 + 1; x <= this.x1 - 1; x++) {
					d.set(x, y, z, p);
				}
			}
		}
	}

	/** A string course of upside-down stairs round the whole outline (piers and panels) at height y. */
	public void course(Draft d, int y, boolean withPiers) {
		Set<Long> fp = footprint(withPiers);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : Shapes.around(fp)) {
				if (!d.allowed(c[0], y, c[1]) || !d.isEmpty(c[0], y, c[1])) {
					continue;
				}
				Direction in = towardShape(fp, c[0], c[1]);
				if (in != null) {
					d.set(c[0], y, c[1], this.dress.stairsOrSlab(in, Half.TOP));
				}
			}
		}
	}

	/** A Lombard band of little arches across each panel (between the piers) at height y. */
	public void band(Draft d, int y) {
		try (var s = d.stage(StageKind.WALLS)) {
			for (Side f : faces()) {
				int a = this.piers ? 1 : 0;
				Details.corbelTable(d, f, a, f.last() - a, y, this.dress);
			}
		}
	}

	/**
	 * Openings of a stage on the chosen faces, centred on each panel, standing on row y and {@code height} high. The
	 * front face may be skipped (a door below, a clock).
	 *
	 * @param fill glass, air (null) or a lattice
	 */
	public void openings(Draft d, int y, int height, Opening kind, boolean front, Paint fill) {
		if (kind == Opening.NONE) {
			return;
		}
		try (var s = d.stage(StageKind.OPENINGS)) {
			for (Side f : faces()) {
				if (!front && f.out() == Dirs.FRONT) {
					continue;
				}
				opening(d, f, y, height, kind, fill);
			}
		}
	}

	/** One opening of a kind centred on a face. */
	public void opening(Draft d, Side f, int y, int height, Opening kind, Paint fill) {
		int mid = f.middle();
		int panel = this.size - (this.piers ? 2 : 2);
		switch (kind) {
			case SLIT -> Details.slit(d, f, mid, y, height, this.dress, fill);
			case SINGLE -> Details.arch(d, f, mid, 1, y, height, this.dress, fill);
			case BIFORA -> {
				if (panel >= 3) {
					Details.lights(d, f, mid - 1, 2, y, height, this.dress, fill);
				} else {
					Details.arch(d, f, mid, 1, y, height, this.dress, fill);
				}
			}
			case TRIFORA -> {
				if (panel >= 5) {
					Details.lights(d, f, mid - 2, 3, y, height, this.dress, fill);
				} else {
					Details.lights(d, f, mid - 1, 2, y, height, this.dress, fill);
				}
			}
			case ARCH -> {
				int w = panel >= 5 ? 3 : Math.min(3, panel);
				Details.arch(d, f, mid - w / 2, w, y, height, this.dress, null);
			}
			case LOUVRE -> {
				int w = panel >= 5 ? 3 : Math.min(3, panel);
				Details.arch(d, f, mid - w / 2, w, y, height, this.dress, fill);
			}
			default -> {
			}
		}
	}

	/** The cornice: a course of corbels round the outline at y, a solid frieze row on the walls and a deck inside. */
	public void cornice(Draft d, int y) {
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = this.z0 + 1; z <= this.z1 - 1; z++) {
				for (int x = this.x0 + 1; x <= this.x1 - 1; x++) {
					d.set(x, y, z, this.dress.full());
				}
			}
			for (int[] c : Draft.ring(this.x0, this.z0, this.x1, this.z1)) {
				d.set(c[0], y, c[1], this.dress.full());
			}
			if (this.piers) {
				for (int[] p : pierCells()) {
					d.set(p[0], y, p[1], this.dress.full());
				}
			}
		}
		course(d, y, true);
	}

	/** A spiral stair round the central post from standing level y0 up to y1. Returns the ring index of the last step. */
	public int stair(Draft d, int y0, int y1, Stone treads, Paint post, int start) {
		if (y1 <= y0) {
			return start;
		}
		return Shapes.spiral(d, this.cx, this.cz, y0, y1 - y0, start, treads, post);
	}

	/** Bells under a beam across the interior at {@code beamY} (the beam's row): one, three or four by size. */
	public void bells(Draft d, int beamY, Paint beam, Direction.Axis axis) {
		try (var s = d.stage(StageKind.FRAME)) {
			if (axis == Direction.Axis.X) {
				for (int x = this.x0 + 1; x <= this.x1 - 1; x++) {
					d.set(x, beamY, this.cz, beam);
				}
			} else {
				for (int z = this.z0 + 1; z <= this.z1 - 1; z++) {
					d.set(this.cx, beamY, z, beam);
				}
			}
		}
		Direction swing = axis == Direction.Axis.X ? Dirs.FRONT : Dirs.RIGHT;
		Details.bell(d, this.cx, beamY - 1, this.cz, swing);
		if (this.size >= 7) {
			int off = this.size >= 9 ? 2 : 2;
			for (int k : new int[]{-off, off}) {
				int bx = axis == Direction.Axis.X ? this.cx + k : this.cx;
				int bz = axis == Direction.Axis.X ? this.cz : this.cz + k;
				Details.bell(d, bx, beamY - 1, bz, swing);
			}
		}
	}

	/** Whether a cell is in the tower's interior. */
	public boolean inside(int x, int z) {
		return x > this.x0 && x < this.x1 && z > this.z0 && z < this.z1;
	}

	/** An air column (a doorway's height) through the core wall at a face's middle, rows [y, y + h). */
	public void cut(Draft d, Side f, int i, int y, int h) {
		for (int yy = y; yy < y + h; yy++) {
			d.set(f.x(i), yy, f.z(i), Blocks.AIR.defaultBlockState());
		}
	}
}
