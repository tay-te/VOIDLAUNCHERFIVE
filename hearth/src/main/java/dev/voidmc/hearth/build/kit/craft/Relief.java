package dev.voidmc.hearth.build.kit.craft;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Facade;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Walls;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import dev.voidmc.hearth.build.style.Role;
import java.util.List;
import java.util.function.IntFunction;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The depth a skilled builder gives a wall: a second layer laid in the row just outside a finished wall, so the wall
 * reads as built rather than drawn. What goes in that layer follows the construction:
 * <ul>
 *   <li><b>timber frame, plank, bamboo</b>: the frame stands proud of the infill: corner posts and the facade's posts
 *   carried up the face from the plinth to the eaves, girts across at every upper floor, the plaster or boards one block
 *   back between them, so every window sits deep in its bay over its sill;</li>
 *   <li><b>stone, brick</b>: a chamfered base course, a string course of upturned stairs at each floor, hood mouldings
 *   over the windows and dressed corner piers;</li>
 *   <li><b>plaster, adobe</b>: the plinth (sokl) proud and sloped, corner lesenes, a string course, a hood over each
 *   window (the Bohemian {@code šambrána}); adobe puts its vigas out through the wall head;</li>
 *   <li><b>log</b>: a sloped sill course on the plinth and drip boards over the windows.</li>
 * </ul>
 * The layer is laid after the walls, openings and roof: it never displaces a sill, a shutter, a flower box, a lamp, an
 * eave or anything else already drawn, never stands in a doorway, never leaves the plot (the street row only from three
 * up), and stops under the roof. A plinth course standing over low ground gets its footing down to the ground.
 */
public final class Relief {
	private Relief() {
	}

	/** What a wall's relief may include. */
	public record Opts(boolean plinth, boolean posts, boolean girts, boolean hoods, boolean corners) {
		/** Everything the construction has. */
		public static final Opts ALL = new Opts(true, true, true, true, true);
		/** No plinth course (a wall whose foot is a yard people work along, or a front with steps). */
		public static final Opts NO_PLINTH = new Opts(false, true, true, true, true);
		/** Only the frame: posts and girts (an upper storey over a ground floor dressed otherwise). */
		public static final Opts FRAME = new Opts(false, true, true, false, true);
	}

	/**
	 * Lays the relief on wall {@code side} of every storey of {@code body} from {@code fromK} to {@code toK} (inclusive),
	 * using the facade's posts and openings and stopping under {@code roof} (null for none). {@code kinds} gives each
	 * storey's construction (null: the style's).
	 */
	public static void wall(Draft d, Body body, Facade facade, Direction side, int fromK, int toK, Roofs.Roof roof, IntFunction<WallKind> kinds,
		Opts o) {
		if (body.isParty(side)) {
			return;
		}
		for (int k = Math.max(0, fromK); k <= Math.min(body.storeys() - 1, toK); k++) {
			WallKind kind = kinds != null ? kinds.apply(k) : d.habits().walls().kind(k);
			storey(d, body, facade, side, k, roof, kind, o);
		}
	}

	/** {@link #wall} on every storey in the style's construction with everything. */
	public static void wall(Draft d, Body body, Facade facade, Direction side, Roofs.Roof roof) {
		wall(d, body, facade, side, 0, body.storeys() - 1, roof, null, Opts.ALL);
	}

	private static boolean outerOk(Draft d, int x, int y, int z) {
		return y < d.maxHeight() && (Walls.inPlot(d, x, z) || z == -1 && y >= 3 && x >= 0 && x < d.frontage());
	}

	/** Free for the relief: nothing drawn there yet, inside the plot, under the roof. */
	private static boolean free(Draft d, int x, int y, int z, Roofs.Roof roof) {
		if (!outerOk(d, x, y, z) || !d.isAbsent(x, y, z)) {
			return false;
		}
		return roof == null || !roof.covers(x, z) || y < roof.bottomY(x, z);
	}

	private static void storey(Draft d, Body b, Facade f, Direction out, int k, Roofs.Roof roof, WallKind kind, Opts o) {
		Side s = b.side(k, out);
		Direction in = out.getOpposite();
		int ya = Walls.bottom(b, k);
		int yg = b.floorY(k + 1) - 1;
		boolean top = k == b.storeys() - 1;
		int plinth = k == 0 ? Math.max(1, d.habits().walls().plinth()) : 0;
		Mat frame = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		Mat plinthMat = d.mat(Role.PLINTH);
		Mat accent = d.mat(Role.WALL_ACCENT);
		Mat window = d.mat(Role.WINDOW);
		List<Integer> posts = f == null ? List.of() : f.posts(out);
		boolean framed = kind == WallKind.TIMBER_FRAME || kind == WallKind.PLANK || kind == WallKind.BAMBOO;
		boolean masonry = kind == WallKind.STONE || kind == WallKind.BRICK;
		boolean rendered = kind == WallKind.PLASTER || kind == WallKind.ADOBE;
		try (var st = d.stage(StageKind.FRAME)) {
			for (int i = 0; i <= s.last(); i++) {
				int x = s.x(i);
				int z = s.z(i);
				int ox = s.outX(i, 1);
				int oz = s.outZ(i, 1);
				boolean corner = s.isCorner(i);
				boolean post = corner || posts.contains(s.column(i));
				for (int y = ya; y <= yg; y++) {
					if (!free(d, ox, y, oz, roof)) {
						continue;
					}
					boolean wallHere = Openings.isWallBlock(d, x, y, z);
					boolean inPlinth = k == 0 && y < b.baseY + plinth;
					Paint p = null;
					if (inPlinth) {
						if (o.plinth && wallHere && (plinthMat.has(Form.STAIRS) || plinthMat.has(Form.SLAB))) {
							p = plinthMat.has(Form.STAIRS) ? plinthMat.stairs(in, Half.BOTTOM) : plinthMat.slab(SlabType.BOTTOM);
						}
					} else if (framed) {
						if (o.posts && post && wallHere) {
							p = frame.log(Direction.Axis.Y);
						} else if (o.girts && k > 0 && y == ya && wallHere) {
							p = beam.log(s.axis());
						}
					} else if (masonry || rendered) {
						boolean pier = o.corners && corner && wallHere && (!top || y < yg);
						if (pier) {
							p = masonry && ((y - b.baseY) & 1) == 1 ? accent.full() : accent.full();
						} else if (o.girts && k > 0 && y == ya && wallHere && !corner) {
							p = accent.has(Form.STAIRS) ? accent.stairs(in, Half.TOP) : accent.slab(SlabType.TOP);
						}
					}
					if (p != null) {
						d.set(ox, y, oz, p);
						if (inPlinth) {
							footing(d, ox, oz, y);
						}
					}
				}
			}
			// hoods and drip boards over the windows; vigas through an adobe wall head
			if (o.hoods && f != null) {
				for (Facade.Opening op : f.openings(out)) {
					if (op.kind() != Facade.Kind.WINDOW || !s.contains(op.at()) || !s.contains(op.at() + op.width() - 1)) {
						continue;
					}
					int i0 = s.index(op.at());
					int i1 = s.index(op.at() + op.width() - 1);
					int lo = Math.min(i0, i1);
					int hi = Math.max(i0, i1);
					// the window's head: the first row above its glass that is wall again
					int head = Integer.MIN_VALUE;
					for (int y = b.floorY(k) + 1; y <= yg; y++) {
						if (!Openings.isWallBlock(d, s.x(lo), y, s.z(lo))) {
							continue;
						}
						if (y > b.floorY(k) + 1 && !Openings.isWallBlock(d, s.x(lo), y - 1, s.z(lo))) {
							head = y;
							break;
						}
					}
					if (head == Integer.MIN_VALUE || head > yg) {
						continue;
					}
					for (int i = lo - (masonry || rendered ? 1 : 0); i <= hi + (masonry || rendered ? 1 : 0); i++) {
						if (i < 0 || i > s.last()) {
							continue;
						}
						int ox = s.outX(i, 1);
						int oz = s.outZ(i, 1);
						if (!free(d, ox, head, oz, roof)) {
							continue;
						}
						Paint p;
						if (framed) {
							// the girt or a lintel board already crosses there on a framed wall; only a drip board under a girt-less head
							if (k > 0 && head == ya || i < lo || i > hi) {
								continue;
							}
							p = window.has(Form.SLAB) ? window.slab(SlabType.BOTTOM) : window.trapdoor(in, Half.BOTTOM, false);
						} else if (kind == WallKind.LOG) {
							if (i < lo || i > hi) {
								continue;
							}
							p = window.has(Form.SLAB) ? window.slab(SlabType.BOTTOM) : window.trapdoor(in, Half.BOTTOM, false);
						} else {
							boolean end = i < lo || i > hi;
							p = accent.has(Form.STAIRS) ? (end ? accent.stairs(i < lo ? s.along() : s.along().getOpposite(), Half.TOP)
								: accent.stairs(in, Half.TOP)) : accent.slab(SlabType.TOP);
							if (end && accent.has(Form.STAIRS)) {
								// the hood's returns: short ends turned along the wall
								p = accent.slab(SlabType.TOP);
							}
						}
						d.set(ox, head, oz, p);
					}
				}
			}
			if (kind == WallKind.ADOBE && top) {
				int vy = b.plateY() - 1;
				for (int i = 1; i < s.last(); i += 2) {
					int ox = s.outX(i, 1);
					int oz = s.outZ(i, 1);
					if (free(d, ox, vy, oz, roof) && Openings.isWallBlock(d, s.x(i), vy, s.z(i))) {
						d.set(ox, vy, oz, frame.log(out.getAxis()));
					}
				}
			}
		}
	}

	/** Foundation under a plinth stone standing proud over ground lower than the floor. */
	private static void footing(Draft d, int x, int z, int y) {
		if (!Walls.inPlot(d, x, z)) {
			return;
		}
		int g = d.site().ground(x, z);
		Mat found = d.mat(Role.FOUNDATION);
		try (var st = d.stage(StageKind.FOUNDATION)) {
			for (int yy = Math.min(y - 1, g - 1); yy < y; yy++) {
				BlockState below = d.get(x, yy, z);
				if (below == null || below.isAir()) {
					d.set(x, yy, z, found.full());
				}
			}
		}
	}
}
