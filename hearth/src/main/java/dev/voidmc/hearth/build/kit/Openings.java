package dev.voidmc.hearth.build.kit;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.api.StageKind;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Doors and windows cut into finished walls, with the depth that makes a wall look built: panes sit in the middle of the
 * wall's thickness, sills and lintels stand proud, shutters open against the wall beside the glass, flower boxes hang
 * under it, doors are set back in their frames under a lintel with a lamp beside them.
 */
public final class Openings {
	private Openings() {
	}

	/** How a window is dressed. */
	public enum WindowStyle {
		/** Glass with a sill, maybe shutters and a flower box (the habits decide). */
		DRESSED,
		/** Glass and a sill only. */
		PLAIN,
		/** A round-headed window: shoulders of upside-down stairs at the top. */
		ARCHED,
		/** A wooden lattice instead of glass (karst, wisteria). */
		LATTICE,
		/** A narrow slit with no glass (towers, stores). */
		SLIT
	}

	/** Options for a window; build with {@link #window(Draft, Side, int, int, int, int, WindowStyle)} defaults. */
	public record WindowOpts(boolean shutters, boolean box, boolean sill, boolean lintel) {
	}

	/**
	 * A window at column {@code col} of a wall (see {@link Side#column}), {@code width} × {@code height} from {@code y},
	 * dressed according to the style and the style's habits.
	 */
	public static void window(Draft d, Side s, int col, int y, int width, int height, WindowStyle style) {
		var h = d.habits().windows();
		boolean shutters = style == WindowStyle.DRESSED && !h.lattice() && d.chance(h.shutters());
		boolean box = style == WindowStyle.DRESSED && d.chance(h.boxes());
		window(d, s, col, y, width, height, style, new WindowOpts(shutters, box, style != WindowStyle.SLIT, true));
	}

	public static void window(Draft d, Side s, int col, int y, int width, int height, WindowStyle style, WindowOpts o) {
		Direction out = s.out();
		Direction in = out.getOpposite();
		Mat glass = d.mat(Role.GLASS);
		Mat frame = d.mat(Role.WINDOW);
		Mat shutter = d.mat(Role.SHUTTER);
		WallKind kind = d.habits().walls().kind();
		int i0 = s.index(col);
		for (int c = 0; c < width; c++) {
			int i = i0 + c;
			int x = s.x(i);
			int z = s.z(i);
			for (int yy = y; yy < y + height; yy++) {
				switch (style) {
					case LATTICE -> d.set(x, yy, z, shutter.trapdoor(in, Half.BOTTOM, true));
					case SLIT -> d.set(x, yy, z, Blocks.AIR.defaultBlockState());
					default -> d.set(x, yy, z, glass.pane());
				}
			}
			int ox = s.outX(i, 1);
			int oz = s.outZ(i, 1);
			if (o.sill && Walls.inPlot(d, ox, oz) || o.sill && oz == -1 && y - 1 >= 3) {
				if (o.box) {
					box(d, ox, y - 1, oz, out);
				} else if (d.isAbsent(ox, y - 1, oz)) {
					d.set(ox, y - 1, oz, frame.stairsOrSlab(in, Half.TOP));
				}
			}
		}
		// lintel or arch over the opening, in the wall plane
		int top = y + height;
		if (style == WindowStyle.ARCHED) {
			Mat stone = d.mat(Role.WALL_ACCENT);
			if (width >= 2) {
				// the top row's end cells become stair shoulders: the opening narrows to a round head
				int yy = top - 1;
				d.set(s.x(i0), yy, s.z(i0), stone.stairsOrSlab(s.along().getOpposite(), Half.TOP));
				d.set(s.x(i0 + width - 1), yy, s.z(i0 + width - 1), stone.stairsOrSlab(s.along(), Half.TOP));
			}
			for (int c = -1; c <= width; c++) {
				int i = i0 + c;
				if (i >= 1 && i <= s.last() - 1 && isWallBlock(d, s.x(i), top, s.z(i))) {
					d.set(s.x(i), top, s.z(i), stone.full());
				}
			}
		} else if (o.lintel) {
			for (int c = -1; c <= width; c++) {
				int i = i0 + c;
				if (i < 1 || i > s.last() - 1) {
					continue;
				}
				int x = s.x(i);
				int z = s.z(i);
				if (!isWallBlock(d, x, top, z)) {
					continue;
				}
				switch (kind) {
					case STONE, BRICK, PLASTER, ADOBE -> {
						if (c >= 0 && c < width || kind == WallKind.STONE) {
							d.set(x, top, z, d.mat(Role.WALL_ACCENT).full());
						}
					}
					case LOG, TIMBER_FRAME, BAMBOO, PLANK -> {
						if (c >= 0 && c < width) {
							d.set(x, top, z, d.mat(Role.BEAM).log(s.axis()));
						}
					}
				}
			}
		}
		if (o.shutters && style != WindowStyle.LATTICE && style != WindowStyle.SLIT) {
			for (int side : new int[]{-1, width}) {
				int i = i0 + side;
				int ox = s.outX(i, 1);
				int oz = s.outZ(i, 1);
				if (i < 0 || i > s.last() || !(Walls.inPlot(d, ox, oz) || oz == -1 && y >= 3)) {
					continue;
				}
				for (int yy = y; yy < y + height; yy++) {
					if (d.isAbsent(ox, yy, oz)) {
						d.set(ox, yy, oz, shutter.trapdoor(out, yy == y + height - 1 && height > 1 ? Half.TOP : Half.BOTTOM, true));
					}
				}
			}
		}
	}

	/** Something drawn as wall at a position (not air, glass, a door or nothing). */
	static boolean isWallBlock(Draft d, int x, int y, int z) {
		BlockState s = d.get(x, y, z);
		return s != null && !s.isAir() && s.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO);
	}

	/**
	 * A flower box hanging under a window: a box of flowering leaves (or a potted plant on a ledge) on an upside-down
	 * stair bracket.
	 */
	public static void box(Draft d, int x, int y, int z, Direction out) {
		Direction in = out.getOpposite();
		Mat frame = d.mat(Role.WINDOW);
		if (d.chance(0.6)) {
			Block leaves = d.chance(0.6) ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.AZALEA_LEAVES;
			d.set(x, y, z, Paint.of(States.leaves(leaves)).inStage(StageKind.EXTERIOR));
			if (y - 1 > d.site().ground(x, z) && d.isAbsent(x, y - 1, z)) {
				d.set(x, y - 1, z, frame.stairsOrSlab(in, Half.TOP));
			}
		} else {
			d.set(x, y, z, d.mat(Role.SHUTTER).trapdoor(out, Half.TOP, false));
			Garden.pot(d, x, y + 1, z);
		}
	}

	/** Options for a door. */
	public record DoorOpts(boolean lamp, boolean hood, boolean main) {
		public static final DoorOpts MAIN = new DoorOpts(true, false, true);
		public static final DoorOpts BACK = new DoorOpts(false, false, false);
	}

	/**
	 * A door at column {@code col} of a wall, {@code width} 1 or 2 leaves, standing at y. The leaves are set back in the
	 * wall's thickness, a lintel crosses above, and the outside cell is registered as an entrance (the main one if
	 * {@code o.main}).
	 */
	public static void door(Draft d, Side s, int col, int y, int width, DoorOpts o) {
		Direction out = s.out();
		Mat doorMat = d.mat(Role.DOOR);
		Block door = doorMat.mainOr(Form.DOOR, Blocks.SPRUCE_DOOR);
		int i0 = s.index(col);
		WallKind kind = d.habits().walls().kind();
		for (int c = 0; c < width; c++) {
			int i = i0 + c;
			DoorHingeSide hinge = width == 1 ? DoorHingeSide.LEFT : (c == 0 ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT);
			d.inStage(StageKind.OPENINGS, () -> d.door(s.x(i), y, s.z(i), door, out, hinge));
			int top = y + 2;
			if (isWallBlock(d, s.x(i), top, s.z(i))) {
				d.set(s.x(i), top, s.z(i), switch (kind) {
					case STONE, BRICK, PLASTER, ADOBE -> d.mat(Role.WALL_ACCENT).full();
					default -> d.mat(Role.BEAM).log(s.axis());
				});
			}
		}
		int mid = i0 + (width - 1) / 2;
		int ex = s.outX(mid, 1);
		int ez = s.outZ(mid, 1);
		d.entrance(ex, y, ez);
		d.poi(PoiType.DOOR, ex, y, ez, out.getOpposite());
		if (o.lamp) {
			int li = width == 1 ? i0 + (((col * 7) & 1) == 0 ? 1 : -1) : i0 - 1;
			wallLantern(d, s, li, y + 1);
		}
		if (o.hood) {
			hood(d, s, i0 - 1, i0 + width, y + 2);
		}
	}

	/** A lantern hanging from a little bracket (a closed trapdoor) on the wall at cell i, head height y. */
	public static void wallLantern(Draft d, Side s, int i, int y) {
		if (i < 0 || i > s.last()) {
			return;
		}
		int ox = s.outX(i, 1);
		int oz = s.outZ(i, 1);
		if (!Walls.inPlot(d, ox, oz) && !(oz == -1 && y >= 3)) {
			return;
		}
		if (!d.isAbsent(ox, y, oz) || !d.isAbsent(ox, y + 1, oz)) {
			return;
		}
		d.set(ox, y + 1, oz, d.mat(Role.SHUTTER).trapdoor(s.out(), Half.TOP, false).inStage(StageKind.EXTERIOR));
		d.set(ox, y, oz, Paint.of(States.lantern(d.mat(Role.LIGHT).mainOr(Form.FULL, Blocks.LANTERN), true)).inStage(StageKind.EXTERIOR));
	}

	/** A small hood of slabs over a door from cell i0 to i1 at height y, with stair brackets at its ends. */
	public static void hood(Draft d, Side s, int i0, int i1, int y) {
		Mat roof = d.mat(Role.ROOF);
		for (int i = i0; i <= i1; i++) {
			int ox = s.outX(i, 1);
			int oz = s.outZ(i, 1);
			if (!(Walls.inPlot(d, ox, oz) || oz == -1 && y >= 3) || !d.isAbsent(ox, y, oz)) {
				continue;
			}
			if (i == i0 || i == i1) {
				d.set(ox, y, oz, roof.stairsOrSlab(i == i0 ? s.along() : s.along().getOpposite(), Half.TOP));
			} else {
				d.set(ox, y, oz, roof.slab(SlabType.TOP));
			}
		}
	}
}
