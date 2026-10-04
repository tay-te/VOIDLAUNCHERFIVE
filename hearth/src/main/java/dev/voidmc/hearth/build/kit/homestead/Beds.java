package dev.voidmc.hearth.build.kit.homestead;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Rooms;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/**
 * Beds placed first and on purpose, before a room is furnished, so a home sleeps the household it promises: each bed's
 * head against a wall (in a corner where one is free), its foot into the room, a free cell kept beside it to get in,
 * every cell claimed through {@link Rooms.Room} so the room stays walkable. Rooms are then furnished round them.
 */
public final class Beds {
	private Beds() {
	}

	/** Places up to {@code want} beds in the room; returns how many. */
	public static int place(Draft d, Rooms.Room r, int want) {
		int placed = 0;
		List<int[]> cand = candidates(d, r);
		for (int[] c : cand) {
			if (placed >= want) {
				break;
			}
			if (put(d, r, c)) {
				placed++;
			}
		}
		return placed;
	}

	/** Candidates {x, z, into(index into Dirs.HORIZONTAL), side x, side z, score}: heads against walls, corners first. */
	private static List<int[]> candidates(Draft d, Rooms.Room r) {
		List<int[]> out = new ArrayList<>();
		for (int z = r.z0; z <= r.z1; z++) {
			for (int x = r.x0; x <= r.x1; x++) {
				for (int k = 0; k < 4; k++) {
					Direction into = Dirs.HORIZONTAL[k];
					int wx = x - Dirs.dx(into);
					int wz = z - Dirs.dz(into);
					if (!d.isSolid(wx, r.y, wz)) {
						continue;
					}
					int fx = x + Dirs.dx(into);
					int fz = z + Dirs.dz(into);
					if (!r.inside(fx, fz)) {
						continue;
					}
					if (!clear(d, r, x, z) || !clear(d, r, fx, fz)) {
						continue;
					}
					// the side to get in by: whichever side of the bed is inside the room (the inner one preferred)
					Direction side = Dirs.right(into);
					int[] s1 = {x + Dirs.dx(side), z + Dirs.dz(side)};
					int[] s2 = {x - Dirs.dx(side), z - Dirs.dz(side)};
					boolean wall1 = !r.inside(s1[0], s1[1]) || d.isSolid(s1[0], r.y, s1[1]);
					boolean wall2 = !r.inside(s2[0], s2[1]) || d.isSolid(s2[0], r.y, s2[1]);
					int[] s = wall1 ? s2 : wall2 ? s1 : (Noise.unit(d.seed() ^ 0xBED, x, 0, z) < 0.5 ? s1 : s2);
					if (wall1 && wall2 || !r.inside(s[0], s[1]) || !clear(d, r, s[0], s[1])) {
						continue;
					}
					int corner = wall1 || wall2 ? 2 : 0;
					int score = corner * 100 + (int) (Noise.unit(d.seed() ^ 0xBEE, x * 4 + k, 0, z) * 99);
					out.add(new int[]{x, z, k, s[0], s[1], score});
				}
			}
		}
		out.sort((a, b) -> Integer.compare(b[5], a[5]));
		return out;
	}

	private static boolean clear(Draft d, Rooms.Room r, int x, int z) {
		return d.isEmpty(x, r.y, z) && d.isEmpty(x, r.y + 1, z) && d.isSolid(x, r.y - 1, z);
	}

	private static boolean put(Draft d, Rooms.Room r, int[] c) {
		Direction into = Dirs.HORIZONTAL[c[2]];
		int fx = c[0] + Dirs.dx(into);
		int fz = c[1] + Dirs.dz(into);
		if (!clear(d, r, c[0], c[1]) || !clear(d, r, fx, fz) || !clear(d, r, c[3], c[4])) {
			return false;
		}
		r.keep(c[3], c[4]);
		if (!r.claim(c[0], c[1])) {
			return false;
		}
		if (!r.claim(fx, fz)) {
			// the head's cell is spent: a chest stands there instead
			int x = c[0];
			int z = c[1];
			d.inStage(StageKind.INTERIOR, () -> d.set(x, r.y, z, States.facing(Blocks.BARREL, into)));
			return false;
		}
		Furniture.bed(d, c[0], r.y, c[1], into);
		return true;
	}
}
