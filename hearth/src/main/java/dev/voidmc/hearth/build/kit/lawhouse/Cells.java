package dev.voidmc.hearth.build.kit.lawhouse;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.landmark.Stone;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;

/**
 * The cells of a jail: a row of them down one side of a corridor, each walled from the next in stone, fronted on the
 * corridor by iron bars with an iron door (prisoners pick its lock, nobody opens it by hand), furnished as the law
 * furnishes them: a bale of straw to sleep on, a bucket, a chain and shackles hanging from the back wall, a slit of
 * barred window high up. Each registers its CELL point inside the bars, facing away from the corridor (the jailer looks
 * in from two blocks out).
 */
public final class Cells {
	private Cells() {
	}

	/**
	 * Cells between {@code z0} and {@code z1} (inclusive) in the band of columns from {@code barX} (the bars, on the
	 * corridor's side) to {@code backX} (inside the outer wall), at standing level y with {@code air} rows to the
	 * ceiling. Cells are {@code cellDepth} rows deep, parted by stone walls. Returns the CELL points' cells {x, z}.
	 */
	public static List<int[]> row(Draft d, int barX, int backX, int z0, int z1, int y, int air, int cellDepth, Stone wall) {
		List<int[]> out = new ArrayList<>();
		Direction away = backX > barX ? Dirs.RIGHT : Dirs.LEFT;
		Direction toCorridor = away.getOpposite();
		int step = Integer.signum(backX - barX);
		int z = z0;
		while (z + cellDepth - 1 <= z1) {
			int c0 = z;
			int c1 = z + cellDepth - 1;
			// the bars across the cell's front, the iron door in the middle of them
			int doorZ = c0 + (cellDepth - 1) / 2;
			try (var st = d.stage(StageKind.WALLS)) {
				for (int cz = c0; cz <= c1; cz++) {
					if (cz == doorZ) {
						continue;
					}
					for (int yy = y; yy < y + air; yy++) {
						d.set(barX, yy, cz, yy < y + Math.min(3, air) ? Blocks.IRON_BARS.defaultBlockState() : wall.full().at(barX, yy, cz));
					}
				}
				for (int yy = y + 2; yy < y + air; yy++) {
					d.set(barX, yy, doorZ, yy == y + 2 ? Blocks.IRON_BARS.defaultBlockState() : wall.full().at(barX, yy, doorZ));
				}
			}
			d.inStage(StageKind.OPENINGS, () -> d.door(barX, y, doorZ, Blocks.IRON_DOOR, toCorridor, DoorHingeSide.LEFT));
			// the wall to the next cell (or the end)
			if (c1 + 1 <= z1) {
				try (var st = d.stage(StageKind.WALLS)) {
					for (int x = barX; x != backX + step; x += step) {
						for (int yy = y; yy < y + air; yy++) {
							d.set(x, yy, c1 + 1, wall.full());
						}
					}
				}
			}
			// inside: straw in the back corner, a bucket, a chain from the back wall (never where the prisoner stands)
			int ix = barX + step;
			int bx = backX;
			try (var st = d.stage(StageKind.INTERIOR)) {
				boolean hay = !(bx == ix && c1 == doorZ);
				if (hay) {
					d.set(bx, y, c1, Blocks.HAY_BLOCK.defaultBlockState());
				}
				if (c0 != c1 && !(bx == ix && c0 == doorZ)) {
					d.set(bx, y, c0, Blocks.CAULDRON.defaultBlockState());
				}
				if (air >= 3) {
					int chainZ = cellDepth >= 3 ? c0 + 1 : c1;
					d.set(bx, y + air - 1, chainZ, States.axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
				}
			}
			out.add(new int[]{ix, doorZ});
			d.poi(PoiType.CELL, ix, y, doorZ, away);
			z = c1 + 2;
		}
		return out;
	}

	/**
	 * Cells side by side across {@code x0..x1} (inclusive), fronted by bars along row {@code barZ} and reaching back to
	 * {@code backZ}, at standing level y with {@code air} rows to the ceiling: as many cells two wide or more as fit, parted
	 * by stone walls, each with its iron door in the bars, straw, a bucket and a chain, a barred slit high in the back
	 * wall. Returns the CELL points' cells {x, z}.
	 */
	public static List<int[]> along(Draft d, int x0, int x1, int barZ, int backZ, int y, int air, Stone wall, Stone dress) {
		List<int[]> out = new ArrayList<>();
		int len = x1 - x0 + 1;
		int n = Math.max(1, (len + 1) / 3);
		int inner = len - (n - 1);
		int step = Integer.signum(backZ - barZ);
		Direction away = step > 0 ? Dirs.BACK : Dirs.FRONT;
		Direction toCorridor = away.getOpposite();
		int x = x0;
		for (int k = 0; k < n; k++) {
			int width = inner / n + (k < inner % n ? 1 : 0);
			int c0 = x;
			int c1 = x + width - 1;
			int doorX = c0 + (width - 1) / 2;
			try (var st = d.stage(StageKind.WALLS)) {
				for (int cx = c0; cx <= c1; cx++) {
					for (int yy = y; yy < y + air; yy++) {
						boolean bar = yy < y + Math.min(3, air) && (cx != doorX || yy == y + 2);
						if (cx == doorX && yy < y + 2) {
							continue;
						}
						d.set(cx, yy, barZ, bar ? Paint.of(Blocks.IRON_BARS.defaultBlockState()) : wall.full());
					}
				}
				// the wall to the next cell
				if (k < n - 1) {
					for (int z = barZ; z != backZ + step; z += step) {
						for (int yy = y; yy < y + air; yy++) {
							d.set(c1 + 1, yy, z, wall.full());
						}
					}
				}
			}
			d.inStage(StageKind.OPENINGS, () -> d.door(doorX, y, barZ, Blocks.IRON_DOOR, toCorridor, DoorHingeSide.LEFT));
			int iz = barZ + step;
			int bz = backZ;
			try (var st = d.stage(StageKind.INTERIOR)) {
				if (!(c1 == doorX && bz == iz)) {
					d.set(c1, y, bz, Blocks.HAY_BLOCK.defaultBlockState());
				}
				if (c0 != c1 && !(c0 == doorX && bz == iz)) {
					d.set(c0, y, bz, Blocks.CAULDRON.defaultBlockState());
				}
				if (air >= 3) {
					d.set(doorX, y + air - 1, bz, States.axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
				}
			}
			out.add(new int[]{doorX, iz});
			d.poi(PoiType.CELL, doorX, y, iz, away);
			x = c1 + 2;
		}
		return out;
	}

	/** A paint helper for a barred slit. */
	static Paint bars() {
		return Paint.of(Blocks.IRON_BARS.defaultBlockState());
	}

	/** Whether a block state is a waterlogged-capable bar (for checks). */
	static boolean barred(net.minecraft.world.level.block.state.BlockState s) {
		return s != null && s.hasProperty(BlockStateProperties.WATERLOGGED) && s.getBlock() == Blocks.IRON_BARS;
	}
}
