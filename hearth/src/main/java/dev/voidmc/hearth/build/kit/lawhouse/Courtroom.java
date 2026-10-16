package dev.voidmc.hearth.build.kit.lawhouse;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.BlockEntities;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.civic.Signs;
import dev.voidmc.hearth.build.kit.landmark.Stone;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * A court room, its bench against the back wall and its public at the front (the local frame of the room: the back is
 * {@link Dirs#BACK}). From the back: the judge's dais a block high across the middle, the judge's high seat (THRONE and
 * the JUDGE's job site) between two assessors' chairs, the bench's desk before them with candles, the town's arms
 * hanging behind; the clerk's lectern and the court's chest of records either side of the dais; the dock, a pen of rails
 * open behind, facing the bench; the bar of the court across the room with its gate folded open; benches for the public
 * in rows behind it either side of a carpeted aisle, walks left along the walls. Rows drop out as the room gets shallow
 * (the bar first, then the dock's front rail). Lit by design: a chandelier over the dock, a wheel over the public in a
 * tall room, candles on the bench, the rest to the rule by {@link Lighting#room}.
 *
 * <p>Cells in {@code keep} stay free (inside the doors, a stair's head, a tower's doorway); cells in {@code blocked} are
 * already built (a stairwell, a pier).
 */
public final class Courtroom {
	private Courtroom() {
	}

	/** How the public sits. */
	public enum Seating {
		/** Benches in rows facing the bench either side of a carpeted aisle (the courts of the west). */
		ROWS,
		/** Benches along both side walls facing the middle, as in a hall of assembly (the north's thing, the majlis). */
		SIDES,
		/** Benches along the side walls and a long hearth down the middle of the floor (the lawspeaker's hall). */
		FIRE
	}

	/** Where the room put its main pieces (for the archetype's own dressing). */
	public record Layout(int cx, int daisZ, int dockZ, int barZ, int r) {
	}

	/** A cell key for the keep and blocked sets. */
	public static long key(int x, int z) {
		return BlockPos.asLong(x, 0, z);
	}

	/**
	 * Furnishes the court room over the interior {@code x0..x1 × z0..z1} at standing level y with {@code air} rows of air
	 * over the floor. Returns the layout.
	 */
	public static Layout furnish(Draft d, int x0, int z0, int x1, int z1, int y, int air, Set<Long> keep, Set<Long> blocked) {
		return furnish(d, x0, z0, x1, z1, y, air, keep, blocked, Seating.ROWS);
	}

	/** {@link #furnish} with the public seated in the given manner. */
	public static Layout furnish(Draft d, int x0, int z0, int x1, int z1, int y, int air, Set<Long> keep, Set<Long> blocked, Seating seating) {
		int w = x1 - x0 + 1;
		int depth = z1 - z0 + 1;
		int cx = (x0 + x1) / 2;
		Timber j = Timber.joinery(d);
		Set<Long> taken = new HashSet<>(blocked);
		// --- the dais, two rows deep against the back wall, a block high, stepped at its ends
		int r = Math.clamp((w - 3) / 2, 1, 3);
		int dz1 = z1;
		int dz0 = z1 - 1;
		int stepZ = z1 - 2;
		try (var st = d.stage(StageKind.INTERIOR)) {
			for (int z = dz0; z <= dz1; z++) {
				for (int x = cx - r; x <= cx + r; x++) {
					if (!keep.contains(key(x, z)) && !taken.contains(key(x, z))) {
						d.set(x, y, z, j.planks());
						taken.add(key(x, z));
					}
				}
			}
			for (int x : new int[]{cx - r, cx + r}) {
				if (!keep.contains(key(x, stepZ)) && !taken.contains(key(x, stepZ))) {
					d.set(x, y, stepZ, j.stairs(Dirs.BACK, Half.BOTTOM));
					taken.add(key(x, stepZ));
				}
			}
		}
		int sy = y + 1;
		// the high seat and the assessors either side, the desk before them
		Furniture.chair(d, cx, sy, dz1, Dirs.BACK);
		d.poi(PoiType.THRONE, cx, sy, dz1, Dirs.FRONT);
		d.poi(PoiType.JOB_SITE, cx, sy, dz1, Dirs.FRONT, JobType.JUDGE);
		if (r >= 2) {
			for (int x : new int[]{cx - 2, cx + 2}) {
				Furniture.chair(d, x, sy, dz1, Dirs.BACK);
			}
		}
		try (var st = d.stage(StageKind.INTERIOR)) {
			for (int x = cx - r + 1; x <= cx + r - 1; x++) {
				d.set(x, sy, dz0, j.slab(SlabType.TOP));
			}
		}
		if (r >= 2) {
			Lighting.candles(d, cx - 1, sy + 1, dz0, 3);
			Lighting.candles(d, cx + 1, sy + 1, dz0, 2);
		} else {
			Lighting.candles(d, cx, sy + 1, dz0, 3);
		}
		// the town's arms on the wall behind the bench, high under the ceiling
		Signs.Arms arms = Signs.arms(d);
		int by = y + Math.max(3, air - 1);
		for (int x : r >= 2 ? new int[]{cx - 2, cx, cx + 2} : new int[]{cx}) {
			if (Openings.isWallBlock(d, x, by, dz1 + 1) && d.isEmpty(x, by, dz1) && d.isEmpty(x, by - 1, dz1)) {
				Signs.wallBanner(d, x, by, dz1, Dirs.FRONT, arms.field(), arms.layers());
			}
		}
		// the clerk's lectern on one side of the dais, the chest of the court's records on the other
		int lx = cx - r - 1;
		if (lx >= x0 && free(keep, taken, lx, dz1) && free(keep, taken, lx, dz0)) {
			int lz = dz1;
			d.inStage(StageKind.INTERIOR, () -> d.set(lx, y, lz, States.facing(Blocks.LECTERN, Dirs.FRONT),
				BlockEntities.lectern("Rolls of the Court", "The Clerk", List.of("Pleas and sentences, by the day."))));
			d.poi(PoiType.LECTERN, lx, y, dz0, Dirs.BACK);
			taken.add(key(lx, lz));
		}
		int cxr = cx + r + 1;
		if (cxr <= x1 && free(keep, taken, cxr, dz1) && free(keep, taken, cxr, dz0)) {
			Furniture.chest(d, cxr, y, dz1, Dirs.FRONT, List.of(new Object[]{"minecraft:paper", 32}, new Object[]{"minecraft:writable_book", 3},
				new Object[]{"minecraft:ink_sac", 4}, new Object[]{"minecraft:feather", 2}));
			taken.add(key(cxr, dz1));
		}
		// --- the dock: a pen of rails facing the bench, open behind
		int dockZ = Integer.MIN_VALUE;
		int barZ = Integer.MIN_VALUE;
		Paint rail = Timber.frame(d).fence();
		boolean frontRail = depth >= 8;
		int dz = stepZ - (frontRail ? 2 : 1);
		if (dz - 1 >= z0 + 1) {
			dockZ = dz;
			try (var st = d.stage(StageKind.INTERIOR)) {
				for (int x : new int[]{cx - 1, cx + 1}) {
					if (free(keep, taken, x, dz)) {
						d.set(x, y, dz, rail);
						taken.add(key(x, dz));
					}
				}
				if (frontRail) {
					for (int x = cx - 1; x <= cx + 1; x++) {
						if (free(keep, taken, x, dz + 1)) {
							d.set(x, y, dz + 1, rail);
							taken.add(key(x, dz + 1));
						}
					}
				}
			}
			// the bar of the court two rows behind the dock, its gate folded open
			int bz = dz - 2;
			if (depth >= 10 && bz >= z0 + 2) {
				barZ = bz;
				try (var st = d.stage(StageKind.INTERIOR)) {
					for (int x = x0; x <= x1; x++) {
						if (!free(keep, taken, x, bz) || keep.contains(key(x, bz - 1)) || keep.contains(key(x, bz + 1))) {
							continue;
						}
						if (x == cx) {
							d.set(x, y, bz, j.gate(Dirs.BACK, true));
						} else {
							d.set(x, y, bz, rail);
						}
						taken.add(key(x, bz));
					}
				}
			}
		}
		// --- the public's benches behind the bar: in rows either side of the aisle (walks along the walls), or along the
		// side walls facing the middle (a long hearth down it in the lawspeaker's hall)
		int first = barZ != Integer.MIN_VALUE ? barZ - 1 : dockZ != Integer.MIN_VALUE ? dockZ - 2 : stepZ - 2;
		boolean walls = w >= 9;
		int n = 0;
		if (seating == Seating.ROWS) {
			for (int z = first; z >= z0 + 1; z -= 2) {
				for (int x = x0 + (walls ? 1 : 0); x <= x1 - (walls ? 1 : 0); x++) {
					if (x == cx || !free(keep, taken, x, z) || keep.contains(key(x, z - 1)) || keep.contains(key(x, z + 1))) {
						continue;
					}
					// benches four long at most, a gap between (a way through the rows, and no long ledge to clamber along)
					if (Math.floorMod(Math.abs(x - cx) - 1, 5) == 4) {
						continue;
					}
					if (!d.isEmpty(x, y, z) || !d.isEmpty(x, y + 1, z)) {
						continue;
					}
					try (var st = d.stage(StageKind.INTERIOR)) {
						d.set(x, y, z, j.stairs(Dirs.FRONT, Half.BOTTOM));
					}
					taken.add(key(x, z));
					if ((n++ & 1) == 0) {
						d.poi(PoiType.SEAT, x, y, z, Dirs.BACK);
					}
				}
			}
		} else {
			for (int z = first; z >= z0 + 1; z--) {
				for (int x : new int[]{x0, x1}) {
					if (!free(keep, taken, x, z) || keep.contains(key(x, z - 1)) && keep.contains(key(x, z + 1)) || !d.isEmpty(x, y, z)
						|| !d.isEmpty(x, y + 1, z)) {
						continue;
					}
					// a gap every fourth bench, so one can get to the wall
					if ((first - z) % 4 == 3) {
						continue;
					}
					try (var st = d.stage(StageKind.INTERIOR)) {
						d.set(x, y, z, j.stairs(x == x0 ? Dirs.LEFT : Dirs.RIGHT, Half.BOTTOM));
					}
					taken.add(key(x, z));
					if ((n++ & 1) == 0) {
						d.poi(PoiType.SEAT, x, y, z, x == x0 ? Dirs.RIGHT : Dirs.LEFT);
					}
				}
			}
			if (seating == Seating.FIRE && first - z0 >= 4 && w >= 7) {
				// the long hearth: fires in a trench edged with stone, down the middle from the front to the bar
				Stone edge = Stone.footing(d);
				int fz0 = z0 + 2;
				int fz1 = first - 1;
				try (var st = d.stage(StageKind.INTERIOR)) {
					for (int z = fz0; z <= fz1; z++) {
						if (!free(keep, taken, cx, z)) {
							continue;
						}
						boolean end = z == fz0 || z == fz1;
						d.set(cx, y - 1, z, edge.full());
						d.set(cx, y, z, end || (z - fz0) % 2 == 1 ? edge.slab(SlabType.BOTTOM) : Paint.of(States.lit(Blocks.CAMPFIRE, true)
							.trySetValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SIGNAL_FIRE, false)));
						taken.add(key(cx, z));
					}
				}
				d.poi(PoiType.HEARTH, cx, y, fz0 - 1, Dirs.BACK);
			}
		}
		// the runner up the aisle to the bar, and before the bench
		Mat cloth = d.mat(Role.CLOTH);
		if (cloth.has(Form.CARPET) && seating != Seating.FIRE) {
			int to = dockZ != Integer.MIN_VALUE ? dockZ - 1 : stepZ;
			try (var st = d.stage(StageKind.INTERIOR)) {
				for (int z = z0; z <= to; z++) {
					if (free(keep, taken, cx, z) && d.isEmpty(cx, y, z) && d.isSolid(cx, y - 1, z)) {
						d.set(cx, y, z, cloth.carpet());
					}
				}
			}
		}
		// --- light: a chandelier over the dock, a wheel over the public in a tall room, the rest to the rule
		int ceil = y + air;
		if (dockZ != Integer.MIN_VALUE && air >= 4 && d.isSolid(cx, ceil, dockZ - 1)) {
			Lighting.chandelier(d, cx, dockZ - 1, ceil, Math.max(y + 2, ceil - 2));
		}
		int pubZ = (z0 + first) / 2;
		if (air >= 5 && first - z0 >= 4 && d.isSolid(cx, ceil, pubZ) && w >= 7) {
			boolean clear = true;
			for (int x = cx - 1; x <= cx + 1; x++) {
				for (int z = pubZ - 1; z <= pubZ + 1; z++) {
					clear &= d.isEmpty(x, ceil - 2, z) && d.isEmpty(x, ceil - 3, z);
				}
			}
			if (clear) {
				Lighting.wheel(d, cx, pubZ, ceil, ceil - 2);
			}
		}
		Lighting.room(d, x0, z0, x1, z1, y);
		return new Layout(cx, dz0, dockZ, barZ, r);
	}

	private static boolean free(Set<Long> keep, Set<Long> taken, int x, int z) {
		return !keep.contains(key(x, z)) && !taken.contains(key(x, z));
	}

	/** States helper: a wall banner's facing toward the room. */
	static Direction into(Direction wall) {
		return wall.getOpposite();
	}
}
