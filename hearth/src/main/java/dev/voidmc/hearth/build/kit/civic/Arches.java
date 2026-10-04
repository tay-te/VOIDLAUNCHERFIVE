package dev.voidmc.hearth.build.kit.civic;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Arcades: a run of openings between piers along a wall line, each headed by an arch in the tradition's manner. The
 * openings are walkable (two blocks clear at least), the piers carry whatever stands above (a hall, a gallery, a roof).
 *
 * <ul>
 *   <li>{@link Kind#ROUND}: stone piers, round heads of upside-down stairs (two courses on wide bays);</li>
 *   <li>{@link Kind#POINTED}: the same drawn up to a point (desert, mission, gothic);</li>
 *   <li>{@link Kind#BRACED}: oak posts under a running beam, knee braces of stairs either side of every post;</li>
 *   <li>{@link Kind#BRACKETED}: lacquered columns with stacked bracket sets under the beam (karst halls);</li>
 *   <li>{@link Kind#POSTED}: posts and a beam with a fretwork valance of fences (raised verandahs);</li>
 *   <li>{@link Kind#FLAT}: piers and a plain lintel.</li>
 * </ul>
 */
public final class Arches {
	private Arches() {
	}

	public enum Kind {
		ROUND, POINTED, BRACED, BRACKETED, POSTED, FLAT
	}

	/** The arcade a tradition builds. */
	public static Kind kind(Tradition t) {
		return switch (t) {
			case TIMBER, LOG -> Kind.BRACED;
			case STONE, MEDITERRANEAN, ALPINE -> Kind.ROUND;
			case ADOBE -> Kind.POINTED;
			case KARST -> Kind.BRACKETED;
			case STILT, STEPPE -> Kind.POSTED;
		};
	}

	/** Whether the kind is timber (posts and beams) rather than masonry (piers and arches). */
	public static boolean timber(Kind k) {
		return k == Kind.BRACED || k == Kind.BRACKETED || k == Kind.POSTED;
	}

	/**
	 * Indices of piers spread evenly over cells {@code i0..i1} of a wall (both ends included), about {@code pitch} apart
	 * (a pier and pitch - 1 open cells per bay).
	 */
	public static int[] piers(int i0, int i1, int pitch) {
		int n = i1 - i0;
		int bays = Math.max(1, (int) Math.round(n / (double) Math.max(2, pitch)));
		int[] out = new int[bays + 1];
		for (int k = 0; k <= bays; k++) {
			out[k] = i0 + (int) Math.round(k * n / (double) bays);
		}
		return out;
	}

	/** The pier paint for a kind: masonry piers in the wall-accent stone, timber posts in the frame. */
	public static Paint pierPaint(Draft d, Kind kind) {
		return timber(kind) ? d.mat(Role.FRAME).log(Direction.Axis.Y) : d.mat(Role.WALL_ACCENT).full();
	}

	/**
	 * An arcade along wall {@code s}: piers at the given cell indices from standing level {@code y0} to {@code top}
	 * (inclusive), open bays between them from {@code y0} to {@code top - 1} with arch heads, and the course at
	 * {@code top} closed over every bay (a beam for timber kinds, the spandrel wall for masonry). Bays need
	 * {@code top - y0 >= 3}; the higher, the more the arch can curve. Cells drawn are in stage WALLS (masonry) or FRAME
	 * (timber).
	 *
	 * @param pier    what the piers are made of (see {@link #pierPaint})
	 * @param spandrel what fills over the arches (masonry kinds) or the beam course (timber kinds, null for the beam role)
	 */
	public static void arcade(Draft d, Side s, int[] piers, int y0, int top, Kind kind, Paint pier, Paint spandrel) {
		Mat accent = d.mat(Role.WALL_ACCENT);
		Mat beam = d.mat(Role.BEAM);
		Mat frame = d.mat(Role.FRAME);
		Mat fence = d.mat(Role.RAILING);
		boolean wood = timber(kind);
		Direction.Axis axis = s.axis();
		BlockState air = Blocks.AIR.defaultBlockState();
		Paint course = spandrel != null ? spandrel : wood ? beam.log(axis) : d.mat(Role.WALL).full();
		piers = piers.clone();
		java.util.Arrays.sort(piers);
		try (var st = d.stage(wood ? StageKind.FRAME : StageKind.WALLS)) {
			for (int p = 0; p < piers.length; p++) {
				int i = piers[p];
				for (int y = y0; y < top; y++) {
					d.set(s.x(i), y, s.z(i), pier);
				}
				// timber posts stand on pad stones; masonry piers on a base course
				if (top - y0 >= 3) {
					d.set(s.x(i), y0, s.z(i), wood ? d.mat(Role.PLINTH).full() : d.mat(Role.PLINTH).full());
				}
				d.set(s.x(i), top, s.z(i), wood ? course : pier);
				if (p == piers.length - 1) {
					continue;
				}
				int a = i + 1;
				int b = piers[p + 1] - 1;
				int w = b - a + 1;
				if (w <= 0) {
					continue;
				}
				for (int j = a; j <= b; j++) {
					for (int y = y0; y < top; y++) {
						d.set(s.x(j), y, s.z(j), air);
					}
					d.set(s.x(j), top, s.z(j), course);
				}
				int h = top - y0;
				head(d, s, a, b, top, h, kind, accent, beam, frame, fence);
			}
		}
	}

	/** The head of one bay {@code a..b} under the course at {@code top}, {@code h} blocks above the floor. */
	private static void head(Draft d, Side s, int a, int b, int top, int h, Kind kind, Mat accent, Mat beam, Mat frame, Mat fence) {
		int w = b - a + 1;
		Direction left = s.along().getOpposite();
		Direction right = s.along();
		switch (kind) {
			case ROUND, POINTED -> {
				Mat m = accent.has(Form.STAIRS) ? accent : d.mat(Role.WALL);
				Paint solid = d.mat(Role.WALL).full();
				if (w < 3) {
					return;
				}
				boolean pointed = kind == Kind.POINTED;
				int rows = w >= 5 ? 2 : 1;
				if (pointed) {
					rows++;
				}
				// keep two blocks of headroom under the lowest course of the head
				rows = Math.min(rows, h - 2);
				for (int r = 0; r < rows; r++) {
					// row r counts down from just under the course: r = 0 is the highest
					int y = top - 1 - r;
					int inset = rows - 1 - r;
					int la = a + inset;
					int lb = b - inset;
					if (la > lb) {
						continue;
					}
					// solid from the jamb to the shoulder, the shoulder stairs leaning on it
					for (int j = a; j < la; j++) {
						d.set(s.x(j), y, s.z(j), solid);
					}
					for (int j = lb + 1; j <= b; j++) {
						d.set(s.x(j), y, s.z(j), solid);
					}
					if (lb - la >= 2 || pointed && lb - la >= 1 || r == rows - 1 && lb - la >= 1) {
						d.set(s.x(la), y, s.z(la), m.stairsOrSlab(left, Half.TOP));
						d.set(s.x(lb), y, s.z(lb), m.stairsOrSlab(right, Half.TOP));
					} else if (lb == la && r == 0 && !pointed) {
						d.set(s.x(la), y, s.z(la), m.slab(net.minecraft.world.level.block.state.properties.SlabType.TOP));
					}
				}
			}
			case BRACED, BRACKETED -> {
				if (w < 2 || h < 3) {
					return;
				}
				Mat m = kind == Kind.BRACKETED ? frame : beam;
				d.set(s.x(a), top - 1, s.z(a), m.stairsOrSlab(left, Half.TOP));
				d.set(s.x(b), top - 1, s.z(b), m.stairsOrSlab(right, Half.TOP));
				if (kind == Kind.BRACKETED && w >= 4 && h >= 4) {
					// a second, shorter bracket below the first: the stacked bracket set of a timber hall
					d.set(s.x(a), top - 2, s.z(a), m.stairsOrSlab(left, Half.TOP));
					d.set(s.x(b), top - 2, s.z(b), m.stairsOrSlab(right, Half.TOP));
					d.set(s.x(a), top - 1, s.z(a), m.full());
					d.set(s.x(b), top - 1, s.z(b), m.full());
					d.set(s.x(a + 1), top - 1, s.z(a + 1), m.stairsOrSlab(left, Half.TOP));
					d.set(s.x(b - 1), top - 1, s.z(b - 1), m.stairsOrSlab(right, Half.TOP));
				}
			}
			case POSTED -> {
				if (h < 3) {
					return;
				}
				// a valance of fence pickets hanging under the beam, brackets at the posts
				d.set(s.x(a), top - 1, s.z(a), beam.stairsOrSlab(left, Half.TOP));
				d.set(s.x(b), top - 1, s.z(b), beam.stairsOrSlab(right, Half.TOP));
				if (fence.has(Form.FENCE)) {
					for (int j = a + 1; j <= b - 1; j++) {
						d.set(s.x(j), top - 1, s.z(j), fence.fence());
					}
				}
			}
			case FLAT -> {
			}
		}
	}
}
