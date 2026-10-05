package dev.voidmc.hearth.build.kit.lawhouse;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Walls;
import dev.voidmc.hearth.build.kit.landmark.Stone;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * The windows of the law's masonry buildings: framed in dressed stone (a head of the dressing, jambs every other course
 * where the wall is render, a sill outside), barred where the building keeps people in or keeps its rolls safe, paired
 * under one head with a stone mullion between on the grand storeys of a court.
 */
public final class Fenestration {
	private Fenestration() {
	}

	/**
	 * A window of {@code width} by {@code h} glass from cell i0 of wall s at y: the dressing over its head and as a sill
	 * outside under it; with {@code jambs}, the dressing in its jambs on alternate courses; with {@code bars}, iron bars in
	 * place of glass; with {@code mullion} (width 3), a dressed post parting two lights.
	 */
	public static void framed(Draft d, Side s, int i0, int width, int y, int h, Stone dress, boolean jambs, boolean bars, boolean mullion) {
		framed(d, s, i0, width, y, h, dress, jambs, bars, mullion, false);
	}

	/** {@link #framed}; with {@code sill}, a projecting sill of the dressing outside (else the frame lies flush). */
	public static void framed(Draft d, Side s, int i0, int width, int y, int h, Stone dress, boolean jambs, boolean bars, boolean mullion,
		boolean sill) {
		Paint glass = bars ? Paint.of(Blocks.IRON_BARS.defaultBlockState()) : d.mat(Role.GLASS).pane();
		try (var st = d.stage(StageKind.OPENINGS)) {
			for (int c = 0; c < width; c++) {
				int i = i0 + c;
				boolean post = mullion && width == 3 && c == 1;
				for (int yy = y; yy < y + h; yy++) {
					d.set(s.x(i), yy, s.z(i), post ? dress.wallOrFull() : glass);
				}
				if (Openings.isWallBlock(d, s.x(i), y + h, s.z(i))) {
					d.set(s.x(i), y + h, s.z(i), dress.full());
				}
				if (Openings.isWallBlock(d, s.x(i), y - 1, s.z(i))) {
					d.set(s.x(i), y - 1, s.z(i), dress.full());
				}
				if (sill) {
					int ox = s.outX(i, 1);
					int oz = s.outZ(i, 1);
					if ((Walls.inPlot(d, ox, oz) || oz == -1 && y - 1 >= 3) && d.isEmpty(ox, y - 1, oz)) {
						d.set(ox, y - 1, oz, dress.stairsOrSlab(s.out().getOpposite(), Half.TOP));
					}
				}
			}
			if (jambs) {
				for (int i : new int[]{i0 - 1, i0 + width}) {
					if (i < 1 || i > s.last() - 1) {
						continue;
					}
					for (int yy = y; yy < y + h; yy += 2) {
						if (Openings.isWallBlock(d, s.x(i), yy, s.z(i))) {
							d.set(s.x(i), yy, s.z(i), dress.full());
						}
					}
				}
			}
		}
	}

	/** A small barred opening (a cell's light, a guard room's loophole): bars in the wall, a dressed head and sill. */
	public static void barred(Draft d, Side s, int i, int y, int h, Stone dress) {
		try (var st = d.stage(StageKind.OPENINGS)) {
			for (int yy = y; yy < y + h; yy++) {
				d.set(s.x(i), yy, s.z(i), Blocks.IRON_BARS.defaultBlockState());
			}
			if (Openings.isWallBlock(d, s.x(i), y + h, s.z(i))) {
				d.set(s.x(i), y + h, s.z(i), dress.full());
			}
			if (Openings.isWallBlock(d, s.x(i), y - 1, s.z(i))) {
				d.set(s.x(i), y - 1, s.z(i), dress.full());
			}
		}
	}
}
