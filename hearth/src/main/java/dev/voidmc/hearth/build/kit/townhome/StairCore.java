package dev.voidmc.hearth.build.kit.townhome;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.style.Role;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/**
 * A stair that climbs a whole house floor after floor in one well: a switchback (up half a storey in one lane, a
 * landing two wide, back up the other lane) repeated storey over storey, so every floor's flight starts where the one
 * below arrived and the well reads as one stair tower. Folk climb it like any stair; nothing in it is a ladder.
 *
 * <p>The well is two lanes wide ({@link #laneA}, where each flight starts, and {@link #laneB} beside it) and three
 * cells long from {@link #zStart} toward the climbing direction, with a landing row in front of it at
 * {@link #zFoot} (two rows after a storey five high, whose return flight is a step longer). Rooms keep the well and its
 * landing row free with {@link #keep(Rooms.Room)}.
 *
 * <p>A house three blocks wide inside has no room for a two-lane well and a bed beside it: there the stair is
 * {@link #straight straight}, one lane against the side wall climbing the whole storey from {@link #zStart}, flight over
 * flight, entered from and left onto the lane beside it ({@link #laneB}), which stays a passage on every floor.
 */
public final class StairCore {
	public final int laneA;
	public final int laneB;
	/** The first step of every flight. */
	public final int zStart;
	/** The landing row: foot of each flight, arrival of the one below. */
	public final int zFoot;
	/** The way each first flight climbs (BACK or FRONT). */
	public final Direction climb;
	private final Body body;
	private final int fromK;
	private final int toK;
	private final boolean straight;
	private final List<int[]> arrivals = new ArrayList<>();

	/**
	 * @param laneA  x of the lane each flight starts in
	 * @param across the direction from lane A to lane B (LEFT or RIGHT)
	 * @param zStart z of each flight's first step
	 * @param climb  BACK or FRONT
	 * @param fromK  the storey the first flight leaves
	 * @param toK    the storey the last flight arrives on
	 */
	public StairCore(Body body, int laneA, Direction across, int zStart, Direction climb, int fromK, int toK) {
		this(body, laneA, across, zStart, climb, fromK, toK, false);
	}

	private StairCore(Body body, int laneA, Direction across, int zStart, Direction climb, int fromK, int toK, boolean straight) {
		this.straight = straight;
		this.body = body;
		this.laneA = laneA;
		this.laneB = laneA + Dirs.dx(across);
		this.zStart = zStart;
		this.climb = climb;
		this.zFoot = zStart - Dirs.dz(climb);
		this.fromK = fromK;
		this.toK = toK;
	}

	/**
	 * A straight stair in one lane ({@code lane}) climbing from {@code zStart}, flight over flight, with the passage lane
	 * beside it toward {@code across}: each flight is entered from the passage at its first step and left onto it at its
	 * top. Needs as many rows as the tallest storey is high.
	 */
	public static StairCore straight(Body body, int lane, Direction across, int zStart, Direction climb, int fromK, int toK) {
		return new StairCore(body, lane, across, zStart, climb, fromK, toK, true);
	}

	/** The rows a straight stair needs: the tallest rise among its flights. */
	public static int straightRun(Body body, int fromK, int toK) {
		int run = 0;
		for (int k = fromK; k < toK; k++) {
			run = Math.max(run, body.height(k));
		}
		return run;
	}

	public boolean isStraight() {
		return this.straight;
	}

	/** The storeys the stair leaves from and arrives on. */
	public int fromK() {
		return this.fromK;
	}

	public int toK() {
		return this.toK;
	}

	/** Lanes' x range. */
	public int x0() {
		return Math.min(this.laneA, this.laneB);
	}

	public int x1() {
		return Math.max(this.laneA, this.laneB);
	}

	/** The z range of the well with its landing rows (inclusive, either order normalised). */
	public int[] zRange() {
		if (this.straight) {
			int b = this.zStart + (Math.max(1, straightRun(this.body, this.fromK, this.toK)) - 1) * Dirs.dz(this.climb);
			return new int[]{Math.min(this.zStart, b), Math.max(this.zStart, b)};
		}
		int a = this.zFoot - Dirs.dz(this.climb);
		int b = this.zStart + 2 * Dirs.dz(this.climb);
		return new int[]{Math.min(a, b), Math.max(a, b)};
	}

	/** Builds every flight from storey {@code fromK} up to {@code toK}. */
	public StairCore build(Draft d) {
		if (this.straight) {
			return buildStraight(d);
		}
		Direction across = Dirs.of(this.laneB - this.laneA, 0);
		for (int k = this.fromK; k < this.toK; k++) {
			int rise = this.body.height(k);
			int[] top = Stairs.switchback(d, this.laneA, this.body.floorY(k), this.zStart, this.climb, rise, across);
			this.arrivals.add(new int[]{top[0], top[2], k + 1});
		}
		// headroom over the landing rows on every floor, claimed now so no joist or lantern is hung across it later
		for (int k = this.fromK; k <= this.toK && k < this.body.storeys(); k++) {
			int y0 = this.body.floorY(k);
			int y1 = k + 1 < this.body.storeys() ? this.body.floorY(k + 1) - 2 : y0 + 2;
			for (int z : new int[]{this.zFoot, this.zFoot - Dirs.dz(this.climb)}) {
				for (int x = x0(); x <= x1(); x++) {
					for (int y = y0; y <= Math.max(y1, y0 + 2); y++) {
						if (d.isAbsent(x, y, z)) {
							d.air(x, y, z);
						}
					}
				}
			}
		}
		return this;
	}

	private StairCore buildStraight(Draft d) {
		int dz = Dirs.dz(this.climb);
		for (int k = this.fromK; k < this.toK; k++) {
			int rise = this.body.height(k);
			Stairs.straight(d, this.laneA, this.body.floorY(k), this.zStart, this.climb, rise, 1);
			this.arrivals.add(new int[]{this.laneB, this.zStart + (rise - 1) * dz, k + 1});
		}
		// the passage beside the flights kept clear on every floor, so no joist, lantern or chest is put across it
		int[] zr = zRange();
		for (int k = this.fromK; k <= this.toK && k < this.body.storeys(); k++) {
			int y0 = this.body.floorY(k);
			for (int z = zr[0]; z <= zr[1]; z++) {
				for (int y = y0; y <= y0 + 2; y++) {
					if (d.isAbsent(this.laneB, y, z)) {
						d.air(this.laneB, y, z);
					}
				}
			}
		}
		return this;
	}

	/** The cell a flight arrives at on storey k (or null for the first storey). */
	public int[] arrival(int k) {
		for (int[] a : this.arrivals) {
			if (a[2] == k) {
				return a;
			}
		}
		return null;
	}

	/** Keeps the well and its landing row free in a room of storey k, the foot and the arrival reachable. */
	public void keep(Rooms.Room room, int k) {
		int[] zr = zRange();
		room.keepArea(x0(), zr[0], x1(), zr[1]);
		if (this.straight) {
			return;
		}
		if (k >= this.fromK && k < this.toK) {
			room.keep(this.laneA, this.zFoot);
		}
		int[] a = arrival(k);
		if (a != null) {
			room.keep(a[0], a[1]);
		} else if (k == this.toK) {
			room.keep(this.laneB, this.zFoot);
		}
	}

	/**
	 * Carries the stair one flight above the top storey into a stair house on the roof deck (its door opens onto the
	 * terrace): walls and a little roof of its own round the last flight. {@code deckY} is the terrace's standing level.
	 * Returns the cell outside the stair house's door at the deck level, or null if it did not fit.
	 */
	public int[] bulkhead(Draft d, int deckY, int maxY) {
		int topK = this.toK;
		int y0 = this.body.floorY(topK);
		int rise = deckY - y0;
		if (rise < 3 || rise > 6 || deckY + 3 >= maxY) {
			return null;
		}
		if (this.straight) {
			return bulkheadStraight(d, y0, deckY, rise);
		}
		Direction across = Dirs.of(this.laneB - this.laneA, 0);
		Stairs.switchback(d, this.laneA, y0, this.zStart, this.climb, rise, across);
		// the stair house: walls round the well from the deck up three, a slab roof, a door on the landing row
		Mat wall = d.mat(Role.WALL);
		Mat roof = d.mat(Role.ROOF);
		int[] zr = zRange();
		int hx0 = x0() - 1;
		int hx1 = x1() + 1;
		int hz0 = zr[0] - 1 + (this.climb == Dirs.BACK ? 1 : 0);
		int hz1 = zr[1] + 1 - (this.climb == Dirs.BACK ? 0 : 1);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = hz0; z <= hz1; z++) {
				for (int x = hx0; x <= hx1; x++) {
					boolean edge = x == hx0 || x == hx1 || z == hz0 || z == hz1;
					for (int y = deckY; y < deckY + 3; y++) {
						if (edge) {
							if (d.isEmpty(x, y, z)) {
								d.set(x, y, z, wall.full());
							}
						} else if (d.isAbsent(x, y, z)) {
							d.air(x, y, z);
						}
					}
				}
			}
		}
		try (var s = d.stage(StageKind.ROOF)) {
			for (int z = hz0; z <= hz1; z++) {
				for (int x = hx0; x <= hx1; x++) {
					d.set(x, deckY + 3, z, roof.slab(net.minecraft.world.level.block.state.properties.SlabType.BOTTOM));
				}
			}
		}
		// the door: in the wall at the landing row's end facing along the lanes' axis, on lane B's arrival side
		int dz = this.climb == Dirs.BACK ? hz0 : hz1;
		int dx = this.laneB;
		Direction out = this.climb == Dirs.BACK ? Dirs.FRONT : Dirs.BACK;
		d.inStage(StageKind.OPENINGS, () -> d.door(dx, deckY, dz, d.mat(Role.DOOR).mainOr(dev.voidmc.hearth.build.style.Form.DOOR, Blocks.SPRUCE_DOOR), out,
			net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT));
		return new int[]{dx, deckY, dz + Dirs.dz(out)};
	}

	/** The straight stair's last flight up onto the deck inside a stair house over both lanes, the door at the front. */
	private int[] bulkheadStraight(Draft d, int y0, int deckY, int rise) {
		int dz = Dirs.dz(this.climb);
		int zTop = this.zStart + (rise - 1) * dz;
		int[] zr = zRange();
		int lo = Math.min(this.zStart, zTop);
		int hi = Math.max(this.zStart, zTop);
		if (lo < zr[0] || hi > zr[1]) {
			return null;
		}
		Stairs.straight(d, this.laneA, y0, this.zStart, this.climb, rise, 1);
		Mat wall = d.mat(Role.WALL);
		Mat roof = d.mat(Role.ROOF);
		int hx0 = x0() - 1;
		int hx1 = x1() + 1;
		int hz0 = zr[0] - 1;
		int hz1 = zr[1] + 1;
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = hz0; z <= hz1; z++) {
				for (int x = hx0; x <= hx1; x++) {
					boolean edge = x == hx0 || x == hx1 || z == hz0 || z == hz1;
					for (int y = deckY; y < deckY + 3; y++) {
						if (edge) {
							if (d.isEmpty(x, y, z)) {
								d.set(x, y, z, wall.full());
							}
						} else if (d.isAbsent(x, y, z)) {
							d.air(x, y, z);
						}
					}
				}
			}
		}
		try (var s = d.stage(StageKind.ROOF)) {
			for (int z = hz0; z <= hz1; z++) {
				for (int x = hx0; x <= hx1; x++) {
					d.set(x, deckY + 3, z, roof.slab(net.minecraft.world.level.block.state.properties.SlabType.BOTTOM));
				}
			}
		}
		// the door at the passage's end away from the top of the flight
		int doorZ = dz > 0 ? hz0 : hz1;
		Direction out = dz > 0 ? Dirs.FRONT : Dirs.BACK;
		int dx = this.laneB;
		d.inStage(StageKind.OPENINGS, () -> d.door(dx, deckY, doorZ, d.mat(Role.DOOR).mainOr(dev.voidmc.hearth.build.style.Form.DOOR, Blocks.SPRUCE_DOOR), out,
			net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT));
		return new int[]{dx, deckY, doorZ + Dirs.dz(out)};
	}

	/**
	 * Whether a roof stands clear of the flight up to storey k, so building it takes nothing off the roof: a straight
	 * flight clears three blocks over every step and the landing three over its floor, and the arrival needs two to
	 * stand in. A stair that would come up under the slope of a low roof stops a storey short instead.
	 */
	public boolean clears(Roofs.Roof roof, int k) {
		if (roof == null || k <= this.fromK) {
			return true;
		}
		int y0 = this.body.floorY(k - 1);
		int rise = this.body.height(k - 1);
		if (this.straight) {
			int dz = Dirs.dz(this.climb);
			for (int i = 0; i < rise; i++) {
				if (!over(roof, this.laneA, this.zStart + dz * i, y0 + i + 4)) {
					return false;
				}
			}
			return over(roof, this.laneB, this.zStart + dz * (rise - 1), y0 + rise + 2);
		}
		int first = rise / 2;
		int second = rise - first;
		int dz = Dirs.dz(this.climb);
		for (int i = 0; i < first; i++) {
			if (!over(roof, this.laneA, this.zStart + dz * i, y0 + i + 4)) {
				return false;
			}
		}
		int lz = this.zStart + dz * first;
		int ly = y0 + first;
		if (!over(roof, this.laneA, lz, ly + 3) || !over(roof, this.laneB, lz, ly + 3)) {
			return false;
		}
		int sz = lz - dz;
		for (int i = 0; i < second; i++) {
			if (!over(roof, this.laneB, sz - dz * i, ly + i + 4)) {
				return false;
			}
		}
		return over(roof, this.laneB, sz - dz * second, ly + second + 2);
	}

	private static boolean over(Roofs.Roof roof, int x, int z, int y) {
		return !roof.covers(x, z) || roof.bottomY(x, z) >= y;
	}

	/** The same well stopping at another storey. */
	public StairCore to(int k) {
		return new StairCore(this.body, this.laneA, Dirs.of(this.laneB - this.laneA, 0), this.zStart, this.climb, this.fromK, k, this.straight);
	}
}
