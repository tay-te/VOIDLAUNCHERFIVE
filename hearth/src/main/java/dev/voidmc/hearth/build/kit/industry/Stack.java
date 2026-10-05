package dev.voidmc.hearth.build.kit.industry;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.style.Form;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * The tall chimney of a works: what tells a smelter, a kiln or a glasshouse from a barn across the valley and on the
 * far horizon. A square shaft of brick or stone on a splayed plinth, banded every few courses, a corbelled crown, and a
 * fire smouldering on straw at its mouth so the smoke climbs high (a signal fire's plume, which is what a furnace
 * chimney looks like). Three wide it is rounded off at the corners (wall posts, so it reads slimmer as it climbs); two
 * wide it is solid. It is cut to the plot's height and kept clear of any roof it passes through.
 */
public final class Stack {
	private Stack() {
	}

	/**
	 * A stack three wide centred on (cx, cz), from {@code baseY} up to its mouth at {@code topY} (clamped under the plot's
	 * height, raised to clear {@code roof} by two where it can), in {@code body} with {@code trim} for its bands and
	 * crown. With {@code plinth}, a splayed footing of stairs round its foot (the ring must be free ground). Returns the
	 * y of the smoking mouth, or MIN_VALUE when there was no room.
	 */
	public static int round(Draft d, int cx, int cz, int baseY, int topY, Fam body, Fam trim, Roofs.@Nullable Roof roof, boolean plinth) {
		int max = d.maxHeight() - 1;
		int t = Math.min(max, topY);
		if (roof != null) {
			int need = Integer.MIN_VALUE;
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (roof.covers(cx + dx, cz + dz)) {
						need = Math.max(need, roof.surfaceY(cx + dx, cz + dz) + 2);
					}
				}
			}
			t = Math.min(max, Math.max(t, need));
		}
		if (t - baseY < 3) {
			return Integer.MIN_VALUE;
		}
		boolean walls = body.has(Form.WALL);
		int plinthTop = baseY + Math.min(3, Math.max(1, (t - baseY) / 4));
		try (var s = d.stage(StageKind.WALLS)) {
			for (int y = baseY; y <= t - 2; y++) {
				boolean band = y == plinthTop || y > plinthTop && (y - plinthTop) % 5 == 0 && y < t - 3;
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						int x = cx + dx;
						int z = cz + dz;
						boolean corner = dx != 0 && dz != 0;
						if (dx == 0 && dz == 0 && y == t - 2) {
							// straw under the mouth's fire: the smoke of a signal fire climbs high
							d.set(x, y, z, Blocks.HAY_BLOCK.defaultBlockState());
							continue;
						}
						if (corner && y > plinthTop && walls) {
							d.set(x, y, z, band ? (trim.has(Form.WALL) ? trim.wall() : trim.full()) : body.wall());
						} else if (band && (corner || y == plinthTop)) {
							d.set(x, y, z, trim.full());
						} else {
							d.set(x, y, z, body.full());
						}
					}
				}
			}
		}
		if (plinth) {
			splay(d, cx - 1, cz - 1, cx + 1, cz + 1, baseY, body);
		}
		try (var s = d.stage(StageKind.ROOF)) {
			// the crown: a corbel course, then a lip; the mouth's fire in the middle
			int y = t - 2;
			for (Direction dir : Dirs.HORIZONTAL) {
				Direction along = Dirs.right(dir);
				for (int k = -1; k <= 1; k++) {
					int x = cx + Dirs.dx(dir) * 2 + Dirs.dx(along) * k;
					int z = cz + Dirs.dz(dir) * 2 + Dirs.dz(along) * k;
					if (!d.allowed(x, y, z) || !d.isEmpty(x, y, z) && !d.isAbsent(x, y, z)) {
						continue;
					}
					d.set(x, y, z, trim.stairsOrSlab(dir.getOpposite(), Half.TOP));
					if (trim.has(Form.SLAB) && d.isEmpty(x, y + 1, z)) {
						d.set(x, y + 1, z, trim.slab(SlabType.BOTTOM));
					}
				}
			}
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dz == 0) {
						d.set(cx, t - 1, cz, fire());
					} else {
						d.set(cx + dx, t - 1, cz + dz, trim.full());
					}
				}
			}
		}
		return t - 1;
	}

	/**
	 * A solid stack two wide over (x0, z0)..(x0 + 1, z0 + 1), from {@code baseY} up to {@code topY} (clamped, cleared
	 * of {@code roof} as {@link #round}), banded and corbelled in {@code trim}, smoking from one of its four cells.
	 * Returns the smoking y, or MIN_VALUE.
	 */
	public static int square(Draft d, int x0, int z0, int baseY, int topY, Fam body, Fam trim, Roofs.@Nullable Roof roof, boolean plinth) {
		int max = d.maxHeight() - 1;
		int t = Math.min(max, topY);
		if (roof != null) {
			int need = Integer.MIN_VALUE;
			for (int dx = 0; dx <= 1; dx++) {
				for (int dz = 0; dz <= 1; dz++) {
					if (roof.covers(x0 + dx, z0 + dz)) {
						need = Math.max(need, roof.surfaceY(x0 + dx, z0 + dz) + 2);
					}
				}
			}
			t = Math.min(max, Math.max(t, need));
		}
		if (t - baseY < 2) {
			return Integer.MIN_VALUE;
		}
		int plinthTop = baseY + Math.min(2, Math.max(1, (t - baseY) / 5));
		try (var s = d.stage(StageKind.WALLS)) {
			for (int y = baseY; y < t; y++) {
				boolean band = y == plinthTop || y > plinthTop && (y - plinthTop) % 5 == 0 && y < t - 2;
				for (int dx = 0; dx <= 1; dx++) {
					for (int dz = 0; dz <= 1; dz++) {
						boolean flue = dx == 0 && dz == 0 && y == t - 1;
						if (flue) {
							d.set(x0 + dx, y, z0 + dz, Blocks.HAY_BLOCK.defaultBlockState());
						} else {
							d.set(x0 + dx, y, z0 + dz, band ? trim.full() : body.full());
						}
					}
				}
			}
		}
		if (plinth) {
			splay(d, x0, z0, x0 + 1, z0 + 1, baseY, body);
		}
		try (var s = d.stage(StageKind.ROOF)) {
			// a corbel course under the mouth where the stack is tall enough to carry one
			if (t - baseY >= 6) {
				for (int[] c : Draft.ring(x0 - 1, z0 - 1, x0 + 2, z0 + 2)) {
					boolean corner = (c[0] == x0 - 1 || c[0] == x0 + 2) && (c[1] == z0 - 1 || c[1] == z0 + 2);
					if (corner || !d.allowed(c[0], t - 1, c[1]) || !d.isAbsent(c[0], t - 1, c[1]) && !d.isEmpty(c[0], t - 1, c[1])) {
						continue;
					}
					Direction in = Dirs.of(c[0] < x0 ? 1 : c[0] > x0 + 1 ? -1 : 0, c[1] < z0 ? 1 : c[1] > z0 + 1 ? -1 : 0);
					if (in != null) {
						d.set(c[0], t - 1, c[1], trim.stairsOrSlab(in, Half.TOP));
					}
				}
			}
			d.set(x0, t, z0, fire());
			for (int[] c : new int[][]{{x0 + 1, z0}, {x0, z0 + 1}, {x0 + 1, z0 + 1}}) {
				d.set(c[0], t, c[1], trim.has(Form.SLAB) ? trim.slab(SlabType.BOTTOM) : body.slab(SlabType.BOTTOM));
			}
		}
		return t;
	}

	/** A splayed footing round a shaft's foot: stairs climbing toward it on every free side cell. */
	public static void splay(Draft d, int x0, int z0, int x1, int z1, int y, Fam m) {
		if (!m.has(Form.STAIRS)) {
			return;
		}
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : Draft.ring(x0 - 1, z0 - 1, x1 + 1, z1 + 1)) {
				boolean corner = (c[0] == x0 - 1 || c[0] == x1 + 1) && (c[1] == z0 - 1 || c[1] == z1 + 1);
				if (corner || !Works.in(d, c[0], c[1]) || !d.isAbsent(c[0], y, c[1]) && !d.isEmpty(c[0], y, c[1])) {
					continue;
				}
				if (!d.isSolid(c[0], y - 1, c[1]) && d.site().ground(c[0], c[1]) != y) {
					continue;
				}
				Direction in = Dirs.of(c[0] < x0 ? 1 : c[0] > x1 ? -1 : 0, c[1] < z0 ? 1 : c[1] > z1 ? -1 : 0);
				if (in != null) {
					d.set(c[0], y, c[1], m.stairs(in, Half.BOTTOM));
				}
			}
		}
	}

	/** A fire smouldering on straw: a lit campfire whose smoke climbs as a signal fire's does. */
	public static BlockState fire() {
		return States.lit(Blocks.CAMPFIRE, true).trySetValue(BlockStateProperties.SIGNAL_FIRE, true);
	}
}
