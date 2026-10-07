package dev.voidmc.hearth.build.archetype.work;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.craft.Hatch;
import dev.voidmc.hearth.build.kit.trade.Floorplan;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.kit.trade.Lifts;
import dev.voidmc.hearth.build.kit.trade.Mills;
import dev.voidmc.hearth.build.kit.trade.Props;
import dev.voidmc.hearth.build.kit.trade.Signs;
import dev.voidmc.hearth.build.kit.trade.Yard;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/**
 * {@code hearth:work/watermill}: the corn mill on the race. A stout mill house of two floors, its lower walls of stone,
 * stands beside a stone-lined race that runs in from the water behind; in the race turns the great wheel, its paddles
 * dipping, its axle driven through the wall to the stones inside. Sacks of grain come in at the door and flour goes out;
 * upstairs the meal is stored.
 *
 * <p>Job sites: BAKER at the millstones (the miller), FARMER at the grain bin. Storage: the flour chest and the loft.
 *
 * <p>Params: {@code roof}, {@code variant}.
 */
public final class Watermill implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("work/watermill"), ArchetypeCategory.WORKPLACE,
		Set.of(JobType.FARMER, JobType.BAKER), 0, 9, 12, 10, 13, 9, 14, SettlementTier.VILLAGE, Set.of(), Set.of(ArchetypeInfo.SiteFlag.WATERFRONT),
		List.of(), 2, 1.2);

	private static final RoofForm[] FORMS = {RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.HIP, RoofForm.GAMBREL, RoofForm.PAGODA, RoofForm.SALTBOX,
		RoofForm.FLAT};

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		Habits h = d.habits();
		RoofForm form = Hall.pick(d, FORMS);
		boolean flat = form == RoofForm.FLAT;
		// the race on whichever side the water lies
		int left = 0;
		int right = 0;
		for (int z = dp - 3; z <= dp + 2; z++) {
			for (int x = 0; x < w; x++) {
				if (d.site().isWater(x, z)) {
					if (x < w / 2) {
						left++;
					} else {
						right++;
					}
				}
			}
		}
		boolean wheelLeft = left == right ? d.chance(0.5) : left > right;
		int kerb = wheelLeft ? 0 : w - 1;
		int raceA = wheelLeft ? 1 : w - 3;
		int hx0 = wheelLeft ? 3 : 0;
		int hx1 = wheelLeft ? w - 1 : w - 4;
		int hz0 = 1;
		int hz1 = dp - 1;
		int sh = Math.max(4, h.walls().storeyHeight());
		int[] heights = Hall.budget(d, hx0, hz0, hx1, hz1, 0, new int[]{sh + 1, sh}, true, form, 1);
		int storeys = heights.length - (flat ? 0 : 1);
		Hall hall = Hall.of(d, hx0, hz0, hx1, hz1, 0, heights, false, false);
		int[] in = hall.inner(0);
		int door = (in[0] + in[2]) / 2;
		hall.facade(door, 1, 1, 0.4);
		door = hall.door != null ? hall.door : door;
		int ex = Yard.entrance(d);
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		// the mill's lower walls are of stone whatever the culture builds its houses of
		if (!Hall.masonry(d)) {
			hall.kind = null;
		}
		hall.ground(false).shell().openings(true);
		// a stair to the meal floor
		int[] flight = null;
		if (storeys == 2) {
			flight = hall.flight(0, wheelLeft ? Dirs.RIGHT : Dirs.LEFT, Dirs.BACK);
			if (flight == null) {
				flight = hall.flight(0, Dirs.BACK, wheelLeft ? Dirs.RIGHT : Dirs.LEFT);
			}
		}
		Direction.Axis ridge = d.chance(0.5) ? Direction.Axis.Z : Direction.Axis.X;
		hall.roof(form, ridge, 1, null);
		if (flat) {
			hall.crown();
		}

		// the race and the wheel
		int r = dp >= 12 && w >= 10 ? 4 : 3;
		int cy = r - 1;
		int cz = Math.clamp((hz0 + hz1) / 2, r + 2, dp - r - 2);
		int z0 = cz - r - 1;
		race(d, kerb, raceA, z0, dp - 1);
		Mat rim = d.mat(Role.FRAME);
		Mat spoke = Hall.woodFence(d);
		Mat paddle = d.mat(Role.FLOOR);
		Mills.waterWheel(d, raceA, cy, cz, Direction.Axis.X, r, 2, rim, spoke, paddle, null);
		// the axle through the wall, its hub inside by the stones
		int wallX = wheelLeft ? hx0 : hx1;
		int inX = wheelLeft ? hx0 + 1 : hx1 - 1;
		int hubX = wheelLeft ? raceA + 2 : raceA - 1;
		Mat axle = d.mat(Role.BEAM);
		d.inStage(StageKind.FRAME, () -> {
			if (d.allowed(hubX, cy, cz)) {
				d.set(hubX, cy, cz, axle.log(Direction.Axis.X));
			}
			d.set(wallX, cy, cz, axle.log(Direction.Axis.X));
			d.set(inX, cy, cz, axle.log(Direction.Axis.X));
		});

		boolean upper = interior(d, hall, door, flight, inX, cz, wheelLeft);
		if (upper && hall.roof != null) {
			mealDoor(d, hall, door);
		}
		Side front = hall.side(0, Dirs.FRONT);
		Signs.bracket(d, front, front.index(door + (wheelLeft ? 1 : -1)), 3, "watermill", "Mill");
		Yard.trodden(d, 0, 0, w - 1, hz0 - 1, 0);
		int sx = wheelLeft ? door + 2 : door - 2;
		if (sx >= 0 && sx < w && d.isEmpty(sx, 0, 0)) {
			Props.sacks(d, sx, 0, 0, wheelLeft ? Dirs.RIGHT : Dirs.LEFT, 2, true);
		}
		Yard.connect(d, ex, door, hz0 - 1);
		if (hall.deck != Integer.MIN_VALUE) {
			// a flat roof is a terrace: a stair up the side, a room set back, an awning
			hall.terrace(d.chance(0.5) ? Dirs.LEFT : Dirs.RIGHT, d.chance(0.7));
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, hall.top + 2);
		dev.voidmc.hearth.build.kit.refine.Voids.finish(d);
	}

	/**
	 * The top floor's loading door in the front wall (in the gable where it faces the street) over the street door, a
	 * hoist beam above it with a sack of flour on its chain: what tells the street this is the mill, from the side the
	 * wheel is not on.
	 */
	private static void mealDoor(Draft d, Hall hall, int door) {
		// the meal floor's front wall, over the street door or the nearest blank wall beside it
		// (only the meal floor the stair or ladder reaches, never the attic over it)
		for (int k = Math.min(1, hall.storeys() - 1); k >= 1; k--) {
			Side wall = hall.side(k, Dirs.FRONT);
			int y = hall.floorY(k);
			for (int off : new int[]{0, 1, -1, 2, -2}) {
				int i = wall.index(door) + off;
				if (i <= 0 || i >= wall.last()) {
					continue;
				}
				int x = wall.x(i);
				int z = wall.z(i);
				int top = hall.roof.covers(x, z) ? hall.roof.bottomY(x, z) : d.maxHeight();
				if (top - y < 3 || !dev.voidmc.hearth.build.kit.Openings.isWallBlock(d, x, y, z)
					|| !dev.voidmc.hearth.build.kit.Openings.isWallBlock(d, x, y + 1, z)) {
					continue;
				}
				// a half door: a rail at the sill, the shutter swung open over it for the hoist
				Hatch.hoistDoor(d, x, y, z, Dirs.FRONT);
				if (y + 2 < top) {
					Lifts.hoist(d, wall, i, y + 2, 2, Math.max(3, y - 1), Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState());
				}
				return;
			}
		}
	}

	/** The race: two wide, stone floor and kerb, water two deep, closed at its head by a sluice wall. */
	private static void race(Draft d, int kerb, int a, int z0, int z1) {
		Mat stone = d.mat(Role.FOUNDATION);
		Mat coping = d.mat(Role.PLINTH);
		try (var s = d.stage(StageKind.EARTHWORK)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = a; x <= a + 1; x++) {
					d.set(x, -3, z, stone.full());
					d.set(x, -2, z, Blocks.WATER.defaultBlockState());
					d.set(x, -1, z, Blocks.WATER.defaultBlockState());
					for (int y = 0; y <= 2; y++) {
						d.set(x, y, z, Blocks.AIR.defaultBlockState());
					}
				}
				for (int y = -3; y <= -1; y++) {
					d.set(kerb, y, z, stone.full());
				}
				d.set(kerb, 0, z, coping.has(Form.WALL) ? coping.wall() : coping.full());
			}
			for (int x = a; x <= a + 1; x++) {
				for (int y = -3; y <= -1; y++) {
					d.set(x, y, z0 - 1, stone.full());
				}
			}
		}
	}

	/** The ground floor, and the meal floor when a flight or a ladder reaches it; answers whether one does. */
	private static boolean interior(Draft d, Hall hall, int door, int[] flight, int inX, int cz, boolean wheelLeft) {
		int[] in = hall.inner(0);
		Floorplan fp = hall.floor(0).blockHoles(d);
		fp.keep(door, in[1]);
		if (flight != null) {
			fp.keep(flight[0], flight[1]);
			for (int x = Math.min(flight[4], flight[6]); x <= Math.max(flight[4], flight[6]); x++) {
				for (int z = Math.min(flight[5], flight[7]); z <= Math.max(flight[5], flight[7]); z++) {
					fp.block(x, z);
				}
			}
		}
		Direction into = wheelLeft ? Dirs.RIGHT : Dirs.LEFT;
		// no flight fits the meal floor (or the floor is the attic): a ladder up through a hatch, against the back wall or
		// the side away from the wheel, so the meal floor and its loading door are walked to
		int[] ladder = null;
		if (flight == null && hall.storeys() >= 2) {
			ladder = Hatch.ladder(d, hall, 1, fp, Dirs.BACK, into.getOpposite() == Dirs.LEFT ? Dirs.RIGHT : Dirs.LEFT, Dirs.FRONT);
		}
		// the stones by the axle, the miller between them
		int placed = 0;
		for (int dz : new int[]{0, 1, -1, 2, -2}) {
			int z = cz + dz;
			if (placed >= 2 || z < in[1] + 1 || z > in[3]) {
				continue;
			}
			if (fp.take(inX, z, into)) {
				Props.grindstone(d, inX, 0, z, into, JobType.BAKER);
				placed++;
			}
		}
		// the grain bin and the flour chest by the door
		boolean bin = false;
		for (int[] c : fp.along(Dirs.FRONT)) {
			if (Math.abs(c[0] - door) <= 1) {
				continue;
			}
			if (!bin && fp.take(c[0], c[1], Dirs.BACK)) {
				Props.station(d, Blocks.COMPOSTER, c[0], 0, c[1], Dirs.BACK, JobType.FARMER);
				bin = true;
			} else if (bin && fp.take(c[0], c[1], Dirs.BACK)) {
				Props.store(d, c[0], 0, c[1], Dirs.BACK, true, List.of(new Object[]{"minecraft:wheat", 48}, new Object[]{"minecraft:bread", 12}));
				break;
			}
		}
		if (!bin) {
			for (int[] c : fp.along(Dirs.BACK)) {
				if (fp.take(c[0], c[1], Dirs.FRONT)) {
					Props.station(d, Blocks.COMPOSTER, c[0], 0, c[1], Dirs.FRONT, JobType.FARMER);
					break;
				}
			}
		}
		for (int[] c : fp.along(into.getOpposite() == Dirs.LEFT ? Dirs.RIGHT : Dirs.LEFT)) {
			if (c[1] > in[1] && fp.take(c[0], c[1], null)) {
				Props.sacks(d, c[0], 0, c[1], Dirs.BACK, 1, true);
			}
		}
		Rooms.furnish(d, fp.room(), Rooms.Type.STORAGE, 0, JobType.NONE);
		if ((flight != null || ladder != null) && hall.storeys() >= 2) {
			Floorplan up = hall.floor(1).blockHoles(d);
			if (flight != null) {
				up.keep(flight[2], flight[3]);
				up.keepArea(flight[4], flight[5], flight[6], flight[7]);
			} else {
				Hatch.keep(up, ladder);
				up.keep(door, up.z0);
			}
			for (int[] c : up.along(Dirs.BACK)) {
				if (up.take(c[0], c[1], null)) {
					Props.sacks(d, c[0], up.y, c[1], Dirs.RIGHT, 1, true);
				}
			}
			Rooms.furnish(d, up.room(), Rooms.Type.STORAGE, 0, JobType.NONE);
		}
		return (flight != null || ladder != null) && hall.storeys() >= 2;
	}
}
