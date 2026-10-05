package dev.voidmc.hearth.build.kit.age;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.style.Role;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A storey added under the old roof: the roof (with everything standing on it: chimney tops, finials, dormers) is lifted
 * whole, and a storey is built under it in the manner of the one below: its walls repeat the top storey's, window for
 * window and beam for beam (a door becomes wall), its floor laid as that storey's was, a ladder up from the old top
 * floor, a bedroom furnished. Chimneys and posts that ran up through the eave run on up through the new storey.
 */
public final class Storey {
	private Storey() {
	}

	/** Lifts the roof and builds a storey under it. Returns whether it was built. */
	public static boolean add(HouseScan h) {
		Draft d = h.d;
		if (h.flat || h.eave < 3) {
			return Why.no("storey", "flat " + h.flat + " eave " + h.eave);
		}
		int sh = Math.clamp(d.habits().walls().storeyHeight(), 3, 5);
		sh = Math.min(sh, h.eave - 1);
		if (sh < 3 || h.top + sh >= d.maxHeight()) {
			return Why.no("storey", "height sh " + sh + " top " + h.top + " max " + d.maxHeight());
		}
		int lastFloor = h.floors.get(h.floors.size() - 1);
		int deck = lastFloor - 1 + sh;
		if (lastFloor > h.eave || deck < h.eave - 1) {
			return Why.no("storey", "floors " + h.floors + " eave " + h.eave + " deck " + deck);
		}
		// the region that rises: everything from the eave up over the body and a margin round it (eaves, outside stacks)
		int m = 2;
		int ax = Math.max(0, h.x0 - m);
		int bx = Math.min(d.frontage() - 1, h.x1 + m);
		int az = Math.max(-1, h.z0 - m);
		int bz = Math.min(d.depth() - 1, h.z1 + m);
		// a roof that runs on past the margin is a wing's or a porch's: lifting part of it would leave the rest in the air
		for (int x = h.x0 - m - 1; x <= h.x1 + m + 1; x++) {
			for (int z = h.z0 - m - 1; z <= h.z1 + m + 1; z++) {
				if (x > h.x0 - m - 1 && x < h.x1 + m + 1 && z > h.z0 - m - 1 && z < h.z1 + m + 1) {
					continue;
				}
				for (int y = h.eave - 2; y <= h.top; y++) {
					Draft.Cell c = d.cell(x, y, z);
					if (c != null && !c.state().isAir() && c.stage() == StageKind.ROOF && !h.added.contains(Draft.key(x, y, z))) {
						return Why.no("storey", "the roof runs on into a wing at " + x + "," + y + "," + z);
					}
				}
			}
		}
		Long2ObjectOpenHashMap<Draft.Cell> lifted = new Long2ObjectOpenHashMap<>();
		for (var e : d.cells().long2ObjectEntrySet()) {
			long k = e.getLongKey();
			int x = Draft.keyX(k);
			int y = Draft.keyY(k);
			int z = Draft.keyZ(k);
			if (y < h.eave - 2 || x < ax || x > bx || z < az || z > bz || h.added.contains(k)) {
				continue;
			}
			boolean inBody = x >= h.x0 && x <= h.x1 && z >= h.z0 && z <= h.z1;
			StageKind st = e.getValue().stage();
			if (!inBody && st == StageKind.EXTERIOR) {
				continue;
			}
			if (y < h.eave) {
				// under the eave only the roof's own overhang (and the brackets under it) outside the walls goes up
				boolean eaveCourse = !inBody && (st == StageKind.ROOF || st == StageKind.FRAME && roofAbove(d, x, y, z));
				if (!eaveCourse) {
					continue;
				}
			}
			lifted.put(k, e.getValue());
		}
		// what ran up through the eave (a chimney, a post) keeps running
		List<long[]> through = new ArrayList<>();
		for (int x = ax; x <= bx; x++) {
			for (int z = az; z <= bz; z++) {
				Draft.Cell below = d.cell(x, h.eave - 1, z);
				Draft.Cell at = d.cell(x, h.eave, z);
				if (below != null && at != null && HouseScan.full(below.state()) && below.state().getBlock() == at.state().getBlock()
					&& !ring(h, x, z)) {
					through.add(new long[]{x, z});
				}
			}
		}
		for (long k : lifted.keySet()) {
			d.remove(Draft.keyX(k), Draft.keyY(k), Draft.keyZ(k));
		}
		for (var e : lifted.long2ObjectEntrySet()) {
			long k = e.getLongKey();
			Draft.Cell c = e.getValue();
			int x = Draft.keyX(k);
			int y = Draft.keyY(k) + sh;
			int z = Draft.keyZ(k);
			final BlockState st = c.state();
			d.inStage(c.stage(), () -> d.set(x, y, z, st, c.tag()));
		}
		for (long[] t : through) {
			int x = (int) t[0];
			int z = (int) t[1];
			Draft.Cell below = d.cell(x, h.eave - 1, z);
			for (int y = h.eave - 2; y < h.eave + sh; y++) {
				if (d.isEmpty(x, y, z)) {
					final int yy = y;
					d.inStage(below.stage(), () -> d.set(x, yy, z, below.state()));
				}
			}
		}
		// the new storey's walls repeat the storey below, row for row
		Mat wall = d.mat(Role.WALL);
		for (int x = h.x0; x <= h.x1; x++) {
			for (int z = h.z0; z <= h.z1; z++) {
				if (!ring(h, x, z)) {
					continue;
				}
				for (int y = h.eave; y < h.eave + sh; y++) {
					Draft.Cell src = d.cell(x, y - sh, z);
					if (src == null || !d.isEmpty(x, y, z) && d.cell(x, y, z).stage() != StageKind.CLEAR) {
						continue;
					}
					BlockState st = src.state();
					Block b = st.getBlock();
					StageKind stage = src.stage();
					if (b instanceof DoorBlock || st.isAir()) {
						// the door of the storey below becomes wall here (the window rows keep their glass)
						try (var sc = d.stage(StageKind.WALLS)) {
							d.set(x, y, z, wall.full());
						}
						continue;
					}
					try (var sc = d.stage(stage)) {
						d.set(x, y, z, st, src.tag());
					}
				}
				// shutters beside the windows go up with them
				for (Direction out : Dirs.HORIZONTAL) {
					int ox = x + Dirs.dx(out);
					int oz = z + Dirs.dz(out);
					if (ox >= h.x0 && ox <= h.x1 && oz >= h.z0 && oz <= h.z1) {
						continue;
					}
					for (int y = h.eave; y < h.eave + sh; y++) {
						Draft.Cell src = d.cell(ox, y - sh, oz);
						if (src != null && src.state().getBlock() instanceof TrapDoorBlock && d.isAbsent(ox, y, oz)
							&& dev.voidmc.hearth.build.kit.townhome.Fronts.allowed(d, ox, y, oz)) {
							final int yy = y;
							d.inStage(src.stage(), () -> d.set(ox, yy, oz, src.state()));
						}
					}
				}
			}
		}
		// the floor of the new storey, laid as the top floor's was (holes and all filled: the way up is a ladder)
		Mat floor = d.mat(Role.FLOOR);
		for (int x = h.x0 + 1; x < h.x1; x++) {
			for (int z = h.z0 + 1; z < h.z1; z++) {
				if (d.isEmpty(x, deck, z)) {
					final int xx = x;
					final int zz = z;
					d.inStage(StageKind.FLOORS, () -> d.set(xx, deck, zz, floor.full()));
				}
			}
		}
		// the way up: a ladder against a wall of the old top floor
		int[] lad = ladderSpot(h, lastFloor, deck);
		if (lad == null) {
			// no way up: the storey is not built (the caller takes down what was begun)
			return Why.no("storey", "no place for a ladder");
		}
		Stairs.ladder(d, lad[0], lad[1], lastFloor, deck, Dirs.HORIZONTAL[lad[2]]);
		// a bedroom for the family that needed the room
		// (where the old top floor stood at the eave, its things went up with the roof: the new room is the one left below)
		int roomY = lastFloor >= h.eave ? lastFloor : deck + 1;
		Rooms.Room room = new Rooms.Room(h.x0 + 1, h.z0 + 1, h.x1 - 1, h.z1 - 1, roomY, 2);
		room.keep(lad[0], lad[1]);
		for (Direction dd : Dirs.HORIZONTAL) {
			room.keep(lad[0] + Dirs.dx(dd), lad[1] + Dirs.dz(dd));
		}
		Rooms.furnish(d, room, Rooms.Type.BEDROOM, (h.x1 - h.x0 - 1) * (h.z1 - h.z0 - 1) >= 24 ? 2 : 1, dev.voidmc.hearth.api.JobType.NONE);
		return true;
	}

	private static boolean roofAbove(Draft d, int x, int y, int z) {
		Draft.Cell c = d.cell(x, y + 1, z);
		return c != null && c.stage() == StageKind.ROOF;
	}

	/** Whether a column is on the body's wall ring. */
	static boolean ring(HouseScan h, int x, int z) {
		boolean in = x >= h.x0 && x <= h.x1 && z >= h.z0 && z <= h.z1;
		return in && (x == h.x0 || x == h.x1 || z == h.z0 || z == h.z1);
	}

	/**
	 * A cell inside a wall where a ladder can run from {@code from} up through {@code deck}: a full wall block behind it,
	 * the column free to the deck, standing room beside its foot and over its top. {x, z, facing index}, or null.
	 */
	static int[] ladderSpot(HouseScan h, int from, int deck) {
		Draft d = h.d;
		for (int x = h.x0 + 1; x < h.x1; x++) {
			for (int z = h.z0 + 1; z < h.z1; z++) {
				for (int f = 0; f < 4; f++) {
					Direction face = Dirs.HORIZONTAL[f];
					int wx = x - Dirs.dx(face);
					int wz = z - Dirs.dz(face);
					if (!ring(h, wx, wz) || !HouseScan.full(stateOr(d, wx, from, wz)) || !HouseScan.full(stateOr(d, wx, deck + 1, wz))) {
						continue;
					}
					boolean free = h.standable(x, from, z);
					for (int y = from; y < deck && free; y++) {
						free = d.isEmpty(x, y, z) && HouseScan.full(stateOr(d, wx, y, wz));
					}
					int sx = x + Dirs.dx(face);
					int sz = z + Dirs.dz(face);
					free = free && h.standable(sx, from, sz) && d.isEmpty(x, deck + 1, z) && d.isEmpty(x, deck + 2, z)
						&& !d.isEmpty(x, from - 1, z);
					if (free) {
						return new int[]{x, z, f};
					}
				}
			}
		}
		return null;
	}

	private static BlockState stateOr(Draft d, int x, int y, int z) {
		BlockState s = d.get(x, y, z);
		return s == null ? Blocks.AIR.defaultBlockState() : s;
	}
}
