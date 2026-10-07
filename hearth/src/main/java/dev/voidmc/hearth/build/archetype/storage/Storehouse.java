package dev.voidmc.hearth.build.archetype.storage;

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
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Porches;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.craft.Hatch;
import dev.voidmc.hearth.build.kit.trade.Floorplan;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.kit.trade.Lifts;
import dev.voidmc.hearth.build.kit.trade.Props;
import dev.voidmc.hearth.build.kit.trade.Yard;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:storage/storehouse}: where a settlement keeps its goods. Three manners, by culture and seed:
 * <ul>
 *   <li><b>raised store</b> (the northern stabbur, the alpine raccard): a log or plank store standing clear of the damp
 *   on posts over stone footings, its upper storey oversailing on every side under a steep roof, reached by a flight of
 *   steps that stops short of its door;</li>
 *   <li><b>loft store</b>: a stout store with double doors over a loading step, a loft under the roof with its own door
 *   in the gable and a hoist beam over it, crates and a cart before it;</li>
 *   <li><b>flat store</b> (desert, mesa): thick walls with few openings under a flat roof whose parapet rises at the
 *   corners, storage jars by the door.</li>
 * </ul>
 * Stilt cultures raise any of them on stilts with a gallery. Inside, chests and barrels line the walls, sacks and hay
 * stand about, and a stair climbs to the loft where there is room for one.
 *
 * <p>Storage: every chest. Params: {@code roof} (a roof form name), {@code raised} (boolean; only on plots 9+ deep with
 * a pitched roof and no stilts, where the steps, the oversail and an inside stair fit), {@code variant}.
 */
public final class Storehouse implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("storage/storehouse"), ArchetypeCategory.STORAGE, Set.of(), 0,
		7, 11, 7, 11, 8, 13, SettlementTier.CAMP, Set.of(), Set.of(), List.of(), 0, 1.0);

	private static final RoofForm[] FORMS = {RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.HIP, RoofForm.GAMBREL, RoofForm.PAGODA, RoofForm.SALTBOX,
		RoofForm.SHED, RoofForm.FLAT};

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
		boolean logs = h.walls().kind() == Habits.WallKind.LOG || h.walls().kind() == Habits.WallKind.PLANK;
		boolean cold = h.snow() || h.walls().kind() == Habits.WallKind.LOG || h.walls().upper() != null;
		boolean water = d.site().anyWater(1, 2, w - 2, dp - 1);
		boolean stilts = h.plan().stilts() == Habits.Stilts.ALWAYS || water;
		boolean raised = !flat && !stilts && d.param("raised", cold && d.chance(0.75) || logs && d.chance(0.3));
		if (raised && dp < 9) {
			// the steps before it and the oversail behind leave too shallow a store for a stair inside
			raised = false;
		}

		// the body: an apron in front for the doors, a margin at the sides on broad plots
		int front = raised || stilts ? 3 : dp >= 9 ? 2 : 1;
		int sideL = w >= 9 ? 1 : 0;
		int sideR = w >= 10 ? 1 : 0;
		if (raised && w >= 9) {
			sideL = 1;
			sideR = 1;
		}
		int bx0 = sideL;
		int bx1 = w - 1 - sideR;
		int bz0 = front;
		int bz1 = dp - 1;
		int bw = bx1 - bx0 + 1;
		int bd = bz1 - bz0 + 1;
		if (raised) {
			// the oversailing upper storey needs a block of room all round it
			bx0 = Math.max(bx0, 1);
			bx1 = Math.min(bx1, w - 2);
			bz1 = dp - 2;
			bw = bx1 - bx0 + 1;
			bd = bz1 - bz0 + 1;
		}
		int sh = Math.max(4, h.walls().storeyHeight());
		// a raised store needs its posts, two storeys and a steep roof under the plot's height: lower what must give
		int room = d.maxHeight() - 1 - 1 - 5;
		int upper = 3;
		int base = raised ? 2 : 0;
		if (raised && base + sh + upper - 1 > room) {
			upper = 2;
		}
		if (raised && base + sh + upper - 1 > room) {
			base = 1;
		}
		if (raised && base + sh + upper - 1 > room) {
			raised = false;
			base = 0;
		}
		if (stilts) {
			base = 0;
			for (int z = bz0; z <= bz1; z++) {
				for (int x = bx0; x <= bx1; x++) {
					int wy = d.site().water(x, z);
					base = Math.max(base, Math.max(d.site().ground(x, z) + Math.max(1, h.plan().stiltHeight()) + 1,
						wy == dev.voidmc.hearth.build.terrain.Site.NONE ? 0 : wy + 2));
				}
			}
			base = Math.min(base, front);
		}
		// a loft where the roof is steep or the plot tall: a knee storey under the roof
		boolean loft = !flat && (d.maxHeight() >= base + sh + 5) && (h.roof().pitch() >= 0.9 || raised);
		int[] heights = raised ? new int[]{sh, upper} : loft ? new int[]{sh, 2} : new int[]{sh};
		Hall hall = Hall.of(d, bx0, bz0, bx1, bz1, base, heights, false, false);
		if (raised) {
			for (Direction s : Dirs.HORIZONTAL) {
				hall.body.jetty(1, s);
			}
		}
		int doorW = bw >= 7 && !raised ? 2 : 1;
		int doorAt = bx0 + (bw - doorW) / 2;
		hall.facade(doorAt, doorW, 1, raised ? 0.2 : 0.35);
		int door = hall.door != null ? hall.door : doorAt;
		Yard.entrance(d);

		// ground: levelled apron, footings or stilts
		if (!stilts) {
			Groundwork.level(d, 0, 0, w - 1, bz0 - 1, 0, true);
		}
		if (raised) {
			Groundwork.stilts(d, bx0, bz0, bx1, bz1, base - 1, 2);
			dev.voidmc.hearth.build.kit.Floors.boards(d, bx0, bz0, bx1, bz1, base - 1);
			staddles(d, bx0, bz0, bx1, bz1, base);
			hall.stilted = true;
		} else {
			hall.ground(stilts);
		}
		hall.shell().openings(true);
		// the stair to the upper storey goes in before the roof, so its headroom never cuts the roof
		int[] flight = stair(hall, door);
		Direction.Axis ridge = d.chance(Math.max(0.35, h.roof().frontGable())) ? Direction.Axis.Z : Direction.Axis.X;
		if (bw > bd + 2) {
			ridge = Direction.Axis.X;
		}
		final boolean frontGable = ridge == Direction.Axis.Z;
		hall.roof(form, ridge, 1, null);
		if (flat) {
			hall.crown();
		}

		// the way up to the door: steps to a raised store, a loading step, a gallery on stilts
		Side fw = hall.side(0, Dirs.FRONT);
		if (stilts) {
			Porches.porch(d, fw, bx0, bx1, Math.min(2, front - 1), base, 3, door);
			Groundwork.approach(d, door, base, bz0 - Math.min(2, front - 1) - 1);
		} else if (raised) {
			// a flight of steps up to a landing before the door (the stabbur's stair stops short of the store)
			Groundwork.approach(d, door, base, bz0 - 1);
		} else {
			loadingStep(d, hall, door, doorW, w);
		}

		// the loft door in the front gable with a hoist beam over it
		if (loft && frontGable && hall.roof != null) {
			loftDoor(d, hall, door, doorW);
		}

		interior(d, hall, door, doorW, flight);
		yard(d, hall, w, dp, door, doorW, flat, raised || stilts);
		if (h.snow()) {
			Garden.snowBanks(d, bx0, bz0, bx1, bz1, base, door);
		}
		if (hall.deck != Integer.MIN_VALUE) {
			// a flat roof is a terrace: a stair up the side, a room set back, an awning
			hall.terrace(d.chance(0.5) ? Dirs.LEFT : Dirs.RIGHT, d.chance(0.7));
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, hall.top + 2);
		dev.voidmc.hearth.build.kit.refine.Voids.finish(d);
	}

	/** Stone caps on the posts of a raised store, where the timber meets the footing. */
	private static void staddles(Draft d, int x0, int z0, int x1, int z1, int base) {
		Mat stone = d.mat(Role.FOUNDATION);
		for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
			int g = d.site().ground(c[0], c[1]);
			if (base - 1 - g >= 2) {
				d.inStage(StageKind.FOUNDATION, () -> d.set(c[0], g, c[1], stone.full()));
			}
		}
	}

	/** A loading step: a deck of stone or boards before the doors, half a block up, with a bollard of a post. */
	private static void loadingStep(Draft d, Hall hall, int door, int doorW, int w) {
		int[] r = hall.rect(0);
		int z = r[1] - 1;
		if (z < 0) {
			return;
		}
		Mat paving = d.mat(Role.PAVING);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int x = Math.max(0, door - 1); x <= Math.min(w - 1, door + doorW); x++) {
				if (d.isAbsent(x, 0, z)) {
					d.set(x, -1, z, paving.full());
				}
			}
		}
	}

	/** A door in the front gable at loft level, a hoist beam over it with a crate on the chain. */
	private static void loftDoor(Draft d, Hall hall, int door, int doorW) {
		int k = hall.storeys() - 1;
		int y = hall.floorY(k);
		Side gable = hall.side(k, Dirs.FRONT);
		int col = door + (doorW - 1) / 2;
		int i = gable.index(col);
		if (i <= 0 || i >= gable.last()) {
			return;
		}
		if (!Openings.isWallBlock(d, gable.x(i), y, gable.z(i)) || !Openings.isWallBlock(d, gable.x(i), y + 1, gable.z(i))) {
			return;
		}
		int top = hall.roof.bottomY(gable.x(i), gable.z(i));
		if (top - y < 3) {
			return;
		}
		// a half door: a rail at the sill, the shutter above swung open for the hoist
		Hatch.hoistDoor(d, gable.x(i), y, gable.z(i), Dirs.FRONT);
		// the hoist beam over the loft door, its chain and a crate
		int beamY = y + 2;
		if (beamY < top) {
			Lifts.hoist(d, gable, i, beamY, 2, Math.max(3, y - 1), d.mat(Role.FURNITURE).full().at(gable.x(i), y, gable.z(i)));
		}
	}

	/** A stair to the upper storey along a side wall or the back wall, if one fits; null otherwise. */
	private static int[] stair(Hall hall, int door) {
		if (hall.storeys() < 2) {
			return null;
		}
		int[] in = hall.inner(0);
		for (Direction wall : new Direction[]{Dirs.LEFT, Dirs.RIGHT, Dirs.BACK}) {
			Direction from = wall == Dirs.BACK ? (door > (in[0] + in[2]) / 2 ? Dirs.LEFT : Dirs.RIGHT) : Dirs.BACK;
			int len = wall == Dirs.BACK ? in[2] - in[0] + 1 : in[3] - in[1] + 1;
			int across = wall == Dirs.BACK ? in[3] - in[1] + 1 : in[2] - in[0] + 1;
			if (len < hall.body.height(0) + 2 || across < 2) {
				continue;
			}
			// a side-wall flight climbs toward the front, its foot at the back, clear of the door
			if (wall != Dirs.BACK && (wall == Dirs.LEFT ? door <= in[0] : door >= in[2])) {
				continue;
			}
			int[] f = hall.flight(0, wall, from);
			if (f != null) {
				return f;
			}
		}
		return null;
	}

	/** Chests and barrels along the walls, sacks and hay, stores in the loft. */
	private static void interior(Draft d, Hall hall, int door, int doorW, int[] flight) {
		Floorplan fp = hall.floor(0).blockHoles(d);
		int[] in = hall.inner(0);
		for (int c = door; c < door + doorW; c++) {
			fp.keep(c, in[1]);
		}
		int[] ladder = null;
		if (hall.storeys() >= 2 && flight == null) {
			// no flight fits: a ladder up through a hatch against the back or a side wall
			ladder = Hatch.ladder(d, hall, 1, fp, Dirs.BACK, Dirs.LEFT, Dirs.RIGHT);
			if (ladder == null) {
				// not even a ladder: the loft is opened to the rafters over the whole store, nobody's floor
				int[] up = hall.inner(1);
				int fy = hall.floorY(1) - 1;
				for (int x = up[0]; x <= up[2]; x++) {
					for (int z = up[1]; z <= up[3]; z++) {
						d.set(x, fy, z, Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
		if (flight != null) {
			fp.keep(flight[0], flight[1]);
			for (int x = Math.min(flight[4], flight[6]); x <= Math.max(flight[4], flight[6]); x++) {
				for (int z = Math.min(flight[5], flight[7]); z <= Math.max(flight[5], flight[7]); z++) {
					fp.block(x, z);
				}
			}
		}
		// hand-placed: a chest either side of the door's inside, sacks along a wall
		int placed = 0;
		for (int[] c : fp.along(Dirs.BACK)) {
			if (placed >= 2) {
				break;
			}
			if (fp.take(c[0], c[1], Dirs.FRONT)) {
				Props.store(d, c[0], hall.floorY(0), c[1], Dirs.FRONT, true, goods(d, placed));
				placed++;
			}
		}
		Rooms.furnish(d, fp.room(), Rooms.Type.STORAGE, 0, JobType.NONE);
		// the loft: more stores under the roof
		if (hall.storeys() >= 2 && (flight != null || ladder != null)) {
			Floorplan up = hall.floor(hall.storeys() - 1).blockHoles(d);
			if (flight != null) {
				up.keep(flight[2], flight[3]);
				up.keepArea(flight[4], flight[5], flight[6], flight[7]);
			} else {
				// the step off the ladder and the walk to the hoist door
				Hatch.keep(up, ladder);
				up.keep(door, up.z0);
			}
			Rooms.Room room = up.room();
			Rooms.furnish(d, room, Rooms.Type.STORAGE, 0, JobType.NONE);
		}
		hall.joists(0, in[2] - in[0] <= in[3] - in[1] ? Direction.Axis.X : Direction.Axis.Z);
	}

	private static List<Object[]> goods(Draft d, int i) {
		return switch ((int) ((d.seed() + i) & 3)) {
			case 0 -> List.of(new Object[]{"minecraft:wheat", 24}, new Object[]{"minecraft:potato", 16}, new Object[]{"minecraft:carrot", 16});
			case 1 -> List.of(new Object[]{"minecraft:oak_planks", 32}, new Object[]{"minecraft:cobblestone", 32}, new Object[]{"minecraft:coal", 12});
			case 2 -> List.of(new Object[]{"minecraft:bread", 12}, new Object[]{"minecraft:apple", 10}, new Object[]{"minecraft:leather", 6});
			default -> List.of(new Object[]{"minecraft:white_wool", 12}, new Object[]{"minecraft:string", 10}, new Object[]{"minecraft:iron_ingot", 4});
		};
	}

	/** Outside: crates and barrels by the doors, a cart, a lamp, jars in the desert, a woodpile at the side. */
	private static void yard(Draft d, Hall hall, int w, int dp, int door, int doorW, boolean flat, boolean raised) {
		int[] r = hall.rect(0);
		Yard.trodden(d, 0, 0, w - 1, r[1] - 1, 0);
		int z = r[1] - 1;
		if (z < 0) {
			return;
		}
		// crates against the front wall either side of the door
		int lx = door - 2;
		int rx = door + doorW + 1;
		if (!raised) {
			if (lx >= r[0] && d.isAbsent(lx, 0, z)) {
				if (flat) {
					Props.jars(d, lx, 0, z, Dirs.LEFT, Math.min(2, lx - r[0] + 1));
				} else {
					Props.crates(d, lx, 0, z, Dirs.LEFT, Math.min(2, lx - r[0] + 1));
				}
			}
			if (rx <= r[2] && d.isAbsent(rx, 0, z)) {
				Props.sacks(d, rx, 0, z, Dirs.RIGHT, Math.min(2, r[2] - rx + 1), false);
			}
		}
		// a cart standing in the apron where it is deep enough
		if (r[1] >= 3 && !raised) {
			int cx = door > w / 2 ? 2 : w - 3;
			if (cx - 1 >= 0 && cx + 1 < w && d.isAbsent(cx, 0, 1) && d.isAbsent(cx - 1, 0, 1) && d.isAbsent(cx + 1, 0, 1) && d.isAbsent(cx, 0, 0)) {
				Props.cart(d, cx, 0, 1, Dirs.FRONT, Blocks.HAY_BLOCK.defaultBlockState());
			}
		}
		// a woodpile against a side wall
		if (r[0] >= 1 && r[3] - r[1] >= 3 && d.chance(0.6)) {
			Garden.woodpile(d, r[0] - 1, 0, r[3] - 1, Dirs.FRONT, Math.min(3, r[3] - r[1] - 1));
		} else if (r[2] <= w - 2 && r[3] - r[1] >= 3) {
			Props.crates(d, r[2] + 1, 0, r[3] - 1, Dirs.FRONT, 2);
		}
	}
}
