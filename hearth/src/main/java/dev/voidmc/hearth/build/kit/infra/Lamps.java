package dev.voidmc.hearth.build.kit.infra;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * Street lamps in each culture's manner, one column wide, standing on a walking level {@code y} and never more than
 * four blocks tall:
 *
 * <ul>
 *   <li><b>crook</b> (temperate, alpine, northern, tundra, maritime, steppe, bayou, tropical): a timber post on a stone
 *   or log foot, an arm over the street and a lantern hung from it;</li>
 *   <li><b>pillar</b> (mediterranean, desert, mesa, moorland): a dressed stone foot, a wall-post shaft and a lantern
 *   standing on top;</li>
 *   <li><b>tōrō</b> (karst, wisteria): a stone lantern, base, light box under a little stone roof.</li>
 * </ul>
 *
 * Lamps are drawn in stage EXTERIOR, after the surface they stand on.
 */
public final class Lamps {
	private Lamps() {
	}

	public enum Make {
		CROOK, PILLAR, TORO
	}

	public static Make make(@Nullable Culture c) {
		if (c == null) {
			return Make.CROOK;
		}
		return switch (c) {
			case MEDITERRANEAN, DESERT, MESA, MOORLAND -> Make.PILLAR;
			case KARST, WISTERIA -> Make.TORO;
			default -> Make.CROOK;
		};
	}

	/**
	 * A street lamp at (x, z) standing on walking level y.
	 *
	 * @param arm the world direction a crook's arm reaches (toward the middle of the street), or null for a lantern on top
	 */
	public static void post(Lay lay, int x, int y, int z, @Nullable Direction arm) {
		post(lay, x, y, z, arm, make(lay.draft().style().culture()));
	}

	public static void post(Lay lay, int x, int y, int z, @Nullable Direction arm, Make make) {
		int room = lay.topY() - y + 1;
		if (room < 3) {
			return;
		}
		Block lantern = LineMat.of(lay, Role.LIGHT).mainOr(Form.FULL, Blocks.LANTERN);
		LineMat frame = LineMat.of(lay, Role.FRAME);
		LineMat found = LineMat.of(lay, Role.FOUNDATION);
		LineMat plinth = LineMat.of(lay, Role.PLINTH);
		LineMat accent = LineMat.of(lay, Role.WALL_ACCENT);
		try (var s = lay.draft().stage(StageKind.EXTERIOR)) {
			switch (make) {
				case PILLAR -> {
					LineMat shaft = found.has(Form.WALL) ? found : plinth.has(Form.WALL) ? plinth : accent;
					lay.set(x, y, z, (accent.has(Form.FULL) ? accent : plinth).full());
					int top = Math.min(y + 2, lay.topY() - 1);
					for (int yy = y + 1; yy <= top; yy++) {
						lay.set(x, yy, z, shaft.has(Form.WALL) ? shaft.wall() : shaft.full());
					}
					lay.set(x, top + 1, z, States.lantern(lantern, false));
				}
				case TORO -> {
					LineMat stone = found.has(Form.WALL) ? found : plinth;
					lay.set(x, y, z, stone.has(Form.WALL) ? stone.wall() : stone.full());
					lay.set(x, y + 1, z, (accent.has(Form.SLAB) ? accent : plinth).slab(SlabType.TOP));
					lay.set(x, y + 2, z, States.lantern(lantern, false));
					if (room >= 4) {
						LineMat cap = plinth.has(Form.SLAB) ? plinth : found;
						lay.set(x, y + 3, z, cap.slab(SlabType.BOTTOM));
					}
				}
				default -> {
					// a stone foot (a log one for the timber cultures), the post, the arm and its lantern
					Culture c = lay.draft().style().culture();
					boolean timberFoot = Dress.timberCulture(c) || c == Culture.NORTHERN || c == Culture.TUNDRA;
					if (timberFoot) {
						lay.set(x, y, z, frame.log(Direction.Axis.Y));
					} else {
						lay.set(x, y, z, found.has(Form.WALL) ? found.wall() : found.full());
					}
					LineMat post = frame.has(Form.FENCE) ? frame : LineMat.of(lay, Role.FENCE);
					int top = y + Math.min(3, room - 1);
					for (int yy = y + 1; yy <= top; yy++) {
						lay.set(x, yy, z, post.post());
					}
					int ax = arm == null ? x : x + arm.getStepX();
					int az = arm == null ? z : z + arm.getStepZ();
					if (arm != null && top - y >= 3 && lay.allowed(ax, top, az) && lay.absent(ax, top, az)) {
						lay.set(ax, top, az, post.post());
						lay.set(ax, top - 1, az, States.lantern(lantern, true));
					} else if (top + 1 <= lay.topY()) {
						lay.set(x, top + 1, z, States.lantern(lantern, false));
					} else {
						lay.set(x, top, z, States.lantern(lantern, false));
					}
				}
			}
		}
	}

	/** A short bollard or mooring post: a log stub (timber) or a stone post with a cap. */
	public static void bollard(Lay lay, int x, int y, int z, boolean timber) {
		try (var s = lay.draft().stage(StageKind.EXTERIOR)) {
			if (timber) {
				lay.set(x, y, z, LineMat.of(lay, Role.STILT).log(Direction.Axis.Y));
			} else {
				LineMat stone = LineMat.of(lay, Role.FOUNDATION);
				lay.set(x, y, z, stone.has(Form.WALL) ? stone.wall() : stone.full());
			}
		}
	}

	/** A lantern hung under a beam or deck at (x, ceiling - 1, z). */
	public static void hanging(Lay lay, int x, int ceilingY, int z) {
		Block lantern = LineMat.of(lay, Role.LIGHT).mainOr(Form.FULL, Blocks.LANTERN);
		try (var s = lay.draft().stage(StageKind.EXTERIOR)) {
			lay.set(x, ceilingY - 1, z, States.lantern(lantern, true));
		}
	}

	/** A lantern standing on a parapet or post top at (x, y, z). */
	public static void standing(Lay lay, int x, int y, int z) {
		Block lantern = LineMat.of(lay, Role.LIGHT).mainOr(Form.FULL, Blocks.LANTERN);
		try (var s = lay.draft().stage(StageKind.EXTERIOR)) {
			lay.set(x, y, z, States.lantern(lantern, false));
		}
	}

	/** Stairs used as a little hood over a wall lantern (unused faces stay as they are). */
	static void hood(Lay lay, int x, int y, int z, Direction out) {
		LineMat roof = LineMat.of(lay, Role.ROOF_TRIM);
		lay.set(x, y, z, roof.stairsOrSlab(out.getOpposite(), Half.TOP));
	}
}
