package dev.voidmc.hearth.build.kit.age;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.townhome.Fronts;
import dev.voidmc.hearth.build.kit.townhome.Projections;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;

/**
 * What a household hangs on the upper storeys of its house: an oriel (a glazed bay on corbels, latticed in the cultures
 * that screen their windows) and a gallery (a deck along an upper storey on brackets, a door cut out onto it). Built
 * with the townhome projections, on an upper storey of a wall that is not a party wall, over the street or the yard.
 */
public final class Bays {
	private Bays() {
	}

	/** Upper storeys with two clear rows under the eave, lowest first. */
	static List<Integer> upper(HouseScan h) {
		List<Integer> out = new ArrayList<>();
		for (int f : h.floors) {
			if (f >= 3 && f + 2 <= h.eave) {
				out.add(f);
			}
		}
		return out;
	}

	static Direction[] sides(long seed) {
		return (seed & 1) == 0 ? new Direction[]{Dirs.FRONT, Dirs.RIGHT, Dirs.LEFT, Dirs.BACK} : new Direction[]{Dirs.FRONT, Dirs.LEFT, Dirs.BACK, Dirs.RIGHT};
	}

	/** An oriel on an upper storey. */
	public static boolean oriel(HouseScan h, long seed) {
		Draft d = h.d;
		boolean lattice = d.habits().windows().lattice();
		for (int f : upper(h)) {
			for (Direction out : sides(seed)) {
				if (h.party(out)) {
					continue;
				}
				Side s = h.side(out);
				int w = s.last() >= 8 ? 3 : 2;
				for (int i0 : fromMiddle(s, w)) {
					if (wallRun(h, s, i0, i0 + w - 1, f, 2) && dressedOut(h, s, i0 - 1, i0 + w, f - 2, f + 2)) {
						for (int i = i0; i < i0 + w; i++) {
							for (int y = f - 2; y <= f + 2; y++) {
								d.remove(s.outX(i, 1), y, s.outZ(i, 1));
							}
						}
						if (Projections.oriel(d, s, i0, i0 + w - 1, f, 2, lattice)) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	/** A gallery along an upper storey, with a door out onto it. */
	public static boolean gallery(HouseScan h, long seed) {
		Draft d = h.d;
		boolean lattice = d.habits().windows().lattice();
		for (int f : upper(h)) {
			for (Direction out : new Direction[]{Dirs.BACK, Dirs.RIGHT, Dirs.LEFT, Dirs.FRONT}) {
				if (h.party(out)) {
					continue;
				}
				Side s = h.side(out);
				int i0 = 1;
				int i1 = s.last() - 1;
				if (i1 - i0 < 3 || !dressedOut(h, s, i0 - 1, i1 + 1, f - 2, f + 1)) {
					continue;
				}
				// the door out: a plain wall cell with standing room behind
				int door = -1;
				for (int i : fromMiddle(s, 1)) {
					int x = s.x(i);
					int z = s.z(i);
					int ix = x + Dirs.dx(out.getOpposite());
					int iz = z + Dirs.dz(out.getOpposite());
					if (i >= i0 && i <= i1 && h.plainWall(x, f, z) && h.plainWall(x, f + 1, z) && h.standable(ix, f, iz)) {
						door = i;
						break;
					}
				}
				if (door < 0) {
					continue;
				}
				// shutters and window boxes under and along it come off
				for (int i = i0 - 1; i <= i1 + 1; i++) {
					for (int y = f - 2; y <= f + 1; y++) {
						if (!d.isEmpty(s.outX(i, 1), y, s.outZ(i, 1))) {
							d.remove(s.outX(i, 1), y, s.outZ(i, 1));
						}
					}
				}
				Projections.gallery(d, s, i0, i1, f, lattice, !lattice);
				int dx = s.x(door);
				int dz = s.z(door);
				d.inStage(StageKind.OPENINGS, () -> d.door(dx, f, dz, d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR), out, DoorHingeSide.LEFT));
				return true;
			}
		}
		return false;
	}

	/** Cell indexes of a side from the middle outward where a run of {@code w} keeps off the corners. */
	static List<Integer> fromMiddle(Side s, int w) {
		List<Integer> out = new ArrayList<>();
		int mid = (s.last() - w + 1) / 2;
		for (int k = 0; k <= s.last() * 2; k++) {
			int i = k % 2 == 0 ? mid + k / 2 : mid - (k + 1) / 2;
			if (i >= 1 && i + w - 1 <= s.last() - 1 && !out.contains(i)) {
				out.add(i);
			}
		}
		return out;
	}

	/** Wall cells i0..i1 at rows f..f+rows-1 are wall or glass (no doors, nothing open), with room inside. */
	static boolean wallRun(HouseScan h, Side s, int i0, int i1, int f, int rows) {
		Draft d = h.d;
		Direction in = s.out().getOpposite();
		for (int i = i0; i <= i1; i++) {
			int x = s.x(i);
			int z = s.z(i);
			for (int y = f; y < f + rows; y++) {
				Draft.Cell c = d.cell(x, y, z);
				if (c == null || c.state().isAir() || c.state().getBlock() instanceof DoorBlock || c.stage() == StageKind.ROOF) {
					return false;
				}
			}
			if (!d.isEmpty(x + Dirs.dx(in), f, z + Dirs.dz(in)) || !d.isEmpty(x + Dirs.dx(in), f + 1, z + Dirs.dz(in))) {
				return false;
			}
		}
		return true;
	}

	/** In front of cells i0..i1 from row y0 to y1 only air and window dressing (nothing full, no roof), inside the plot. */
	static boolean dressedOut(HouseScan h, Side s, int i0, int i1, int y0, int y1) {
		Draft d = h.d;
		for (int i = i0; i <= i1; i++) {
			int x = s.outX(i, 1);
			int z = s.outZ(i, 1);
			for (int y = y0; y <= y1; y++) {
				if (!Fronts.allowed(d, x, y, z)) {
					return false;
				}
				Draft.Cell c = d.cell(x, y, z);
				if (c != null && !c.state().isAir() && (HouseScan.full(c.state()) || c.stage() == StageKind.ROOF || c.stage() == StageKind.FLOORS
					|| c.stage() == StageKind.FRAME)) {
					return false;
				}
			}
		}
		return true;
	}

	/** Nothing but air in front of cells i0..i1 from row y0 to y1, all inside what the plot allows. */
	static boolean clearOut(HouseScan h, Side s, int i0, int i1, int y0, int y1) {
		Draft d = h.d;
		for (int i = i0; i <= i1; i++) {
			int x = s.outX(i, 1);
			int z = s.outZ(i, 1);
			for (int y = y0; y <= y1; y++) {
				if (!Fronts.allowed(d, x, y, z) || !d.isEmpty(x, y, z)) {
					return false;
				}
			}
		}
		return true;
	}
}
