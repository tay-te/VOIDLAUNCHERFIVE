package dev.voidmc.hearth.build.kit;

import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import dev.voidmc.hearth.build.style.Role;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Walls in the style's construction, one storey at a time. Every kind gives the wall depth and structure rather than a
 * flat plane of one block:
 * <ul>
 *   <li><b>timber frame</b>: corner posts and posts flanking every opening, a girt at each floor, a sill beam on upper
 *   storeys, braces in some blank panels, plaster or brick infill, a stone plinth;</li>
 *   <li><b>log</b>: horizontal logs, corners notched with the logs running past on alternate courses, openings framed by
 *   stripped posts;</li>
 *   <li><b>stone</b> and <b>brick</b>: mixed masonry with toothed quoins at the corners;</li>
 *   <li><b>plaster</b> and <b>adobe</b>: render on a plinth with a string course at each floor;</li>
 *   <li><b>bamboo</b> and <b>plank</b>: boards between posts on a stone sill (plank walls use the GABLE role's
 *   boards, so a style can board its upper storeys over plaster ones).</li>
 * </ul>
 * Jettied storeys get brackets under the oversailing wall, and floor beams may show their ends through the wall. Party
 * walls are solid. Openings are cut afterwards by {@link Openings}.
 */
public final class Walls {
	private Walls() {
	}

	/** Draws all walls of storey k in the style's wall kind for that storey (upper storeys may differ). */
	public static void storey(Draft d, Body b, int k, Facade f) {
		storey(d, b, k, f, d.habits().walls().kind(k));
	}

	public static void storey(Draft d, Body b, int k, Facade f, WallKind kind) {
		for (Side s : b.sides(k)) {
			side(d, b, k, s, f, kind);
		}
		if (k > 0) {
			jettyBrackets(d, b, k, f);
		}
	}

	/** The first course of storey k's walls: the plinth at ground level for storey 0, the floor course above. */
	public static int bottom(Body b, int k) {
		return k == 0 ? b.baseY : b.floorY(k) - 1;
	}

	/** Draws one wall of storey k. */
	public static void side(Draft d, Body b, int k, Side s, Facade f, WallKind kind) {
		boolean party = b.isParty(s.out());
		int ya = bottom(b, k);
		int yg = b.floorY(k + 1) - 1;
		int plinth = k == 0 ? Math.max(0, d.habits().walls().plinth()) : 0;
		Direction.Axis axis = s.axis();
		Mat wall = d.mat(Role.WALL);
		Mat frame = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		Mat plinthMat = d.mat(Role.PLINTH);
		Mat accent = d.mat(Role.WALL_ACCENT);
		Mat gable = d.mat(Role.GABLE);
		List<Integer> posts = party ? List.of() : f.posts(s.out());
		for (int i = 0; i < s.length(); i++) {
			int x = s.x(i);
			int z = s.z(i);
			int col = s.column(i);
			boolean corner = s.isCorner(i);
			boolean post = posts.contains(col);
			boolean nextToCorner = i == 1 || i == s.length() - 2;
			for (int y = ya; y <= yg; y++) {
				boolean inPlinth = k == 0 && y < b.baseY + plinth;
				Paint p = switch (kind) {
					case TIMBER_FRAME -> {
						if (inPlinth) {
							yield plinthMat.full();
						}
						if (corner) {
							yield frame.log(Direction.Axis.Y);
						}
						// girts at each floor; a knee wall under the roof (one or two courses) is plastered between its posts
						boolean knee = b.height(k) <= 2 && k == b.storeys() - 1;
						if ((y == yg && !knee) || (k > 0 && y == ya)) {
							yield beam.log(axis);
						}
						if (post) {
							yield frame.log(Direction.Axis.Y);
						}
						yield wall.full();
					}
					case LOG -> {
						if (inPlinth) {
							yield plinthMat.full();
						}
						if (corner) {
							yield frame.log((y & 1) == 0 ? axis : other(axis));
						}
						if (post && !party) {
							yield wall.strippedLog(Direction.Axis.Y);
						}
						yield wall.log(axis);
					}
					case STONE, BRICK -> {
						if (inPlinth && !corner) {
							yield plinthMat.full();
						}
						boolean quoin = corner || (nextToCorner && ((y - b.baseY) & 1) == 0);
						if (quoin) {
							yield accent.full();
						}
						yield wall.full();
					}
					case PLASTER, ADOBE -> {
						if (inPlinth) {
							yield plinthMat.full();
						}
						if (k > 0 && y == ya && !party) {
							yield accent.full();
						}
						yield wall.full();
					}
					case BAMBOO, PLANK -> {
						if (inPlinth) {
							yield plinthMat.full();
						}
						if (corner || post) {
							yield frame.log(Direction.Axis.Y);
						}
						if (y == yg) {
							yield beam.log(axis);
						}
						// plank walls are boarded with the gable material (an upper storey of boards over a plaster one)
						yield kind == WallKind.PLANK ? gable.full() : wall.full();
					}
				};
				d.set(x, y, z, p);
			}
			// log corners run past the wall on alternate courses
			if (kind == WallKind.LOG && corner) {
				int ox = s.x(i) + (i == 0 ? -Dirs.dx(s.along()) : Dirs.dx(s.along()));
				int oz = s.z(i) + (i == 0 ? -Dirs.dz(s.along()) : Dirs.dz(s.along()));
				if (inPlot(d, ox, oz)) {
					for (int y = ya + plinth; y <= yg; y++) {
						if (((y & 1) == 0) && d.isEmpty(ox, y, oz)) {
							d.set(ox, y, oz, frame.log(axis));
						}
					}
				}
			}
		}
		if (kind == WallKind.TIMBER_FRAME && !party) {
			for (int[] br : f.braces(s.out())) {
				brace(d, s, br[0], br[1], k == 0 ? b.baseY + plinth : ya + 1, yg - 1, frame);
			}
		}
	}

	private static Direction.Axis other(Direction.Axis a) {
		return a == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
	}

	/** Whether local (x, z) is inside the plot's footprint. */
	public static boolean inPlot(Draft d, int x, int z) {
		return x >= 0 && z >= 0 && x < d.frontage() && z < d.depth();
	}

	/** A diagonal brace across a panel, rising from the left foot to the right head (or mirrored on the right half). */
	private static void brace(Draft d, Side s, int from, int to, int yLo, int yHi, Mat frame) {
		int w = to - from + 1;
		int h = yHi - yLo + 1;
		if (w < 2 || h < 2) {
			return;
		}
		int iFrom = s.index(from);
		int iTo = s.index(to);
		boolean rising = ((from * 31) & 2) == 0;
		for (int j = 0; j < Math.max(w, h); j++) {
			double t = Math.max(w, h) == 1 ? 0 : (double) j / (Math.max(w, h) - 1);
			int ci = (int) Math.round(iFrom + (iTo - iFrom) * t);
			int y = rising ? (int) Math.round(yLo + (h - 1) * t) : (int) Math.round(yHi - (h - 1) * t);
			d.set(s.x(ci), y, s.z(ci), frame.log(Direction.Axis.Y));
		}
	}

	/**
	 * Under an oversailing wall: an upside-down stair under each post of the storey above (a carved bracket), and the
	 * floor joists' ends under the sill.
	 */
	static void jettyBrackets(Draft d, Body b, int k, Facade f) {
		Mat beam = d.mat(Role.BEAM);
		Mat frame = d.mat(Role.FRAME);
		int y = b.floorY(k) - 2;
		for (Side s : b.sides(k)) {
			int over = b.outset(k, s.out()) - b.outset(k - 1, s.out());
			if (over <= 0) {
				continue;
			}
			for (int i = 0; i < s.length(); i++) {
				int col = s.column(i);
				boolean post = s.isCorner(i) || f.posts(s.out()).contains(col);
				if (!post && (i % 2) != 0) {
					continue;
				}
				int x = s.x(i);
				int z = s.z(i);
				if (d.isEmpty(x, y, z)) {
					Paint p = post ? frame.stairsOrSlab(s.out().getOpposite(), Half.TOP) : beam.stairsOrSlab(s.out().getOpposite(), Half.TOP);
					if (!frame.has(Form.STAIRS)) {
						p = beam.log(s.out().getAxis());
					}
					d.set(x, y, z, p);
				}
			}
		}
	}

	/**
	 * Floor beams showing their ends through a wall at storey k's floor level: a log end just outside the wall at each
	 * post column (only where that cell is inside the plot, or overhangs the street at least three blocks up).
	 */
	public static void beamEnds(Draft d, Body b, int k, Facade f, Direction side) {
		if (k <= 0 || b.isParty(side)) {
			return;
		}
		Side s = b.side(k, side);
		int y = b.floorY(k) - 1;
		Mat beam = d.mat(Role.BEAM);
		for (int i = 1; i < s.length() - 1; i++) {
			if (!f.posts(side).contains(s.column(i))) {
				continue;
			}
			int ox = s.outX(i, 1);
			int oz = s.outZ(i, 1);
			if ((inPlot(d, ox, oz) || oz == -1 && y >= 3) && d.isAbsent(ox, y, oz)) {
				d.set(ox, y, oz, beam.log(side.getAxis()));
			}
		}
	}
}
