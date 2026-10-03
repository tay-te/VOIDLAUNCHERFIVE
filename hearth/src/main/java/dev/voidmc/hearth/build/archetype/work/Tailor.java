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
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Porches;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.trade.Floorplan;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.kit.trade.Props;
import dev.voidmc.hearth.build.kit.trade.Shop;
import dev.voidmc.hearth.build.kit.trade.Signs;
import dev.voidmc.hearth.build.kit.trade.Yard;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@code hearth:work/tailor}: the tailor's and dyer's shop, narrow and tall on the high street (in a row with party
 * walls where the street is dense). Its shop window shows bolts of dyed wool under an awning of the house's colours;
 * inside, a counter, shelves of cloth, a loom at the back; up a turning stair, the workroom with its looms under the
 * windows, often a balcony. Behind, where the plot runs deep, the dye yard: vats steaming in cauldrons and lengths of
 * coloured cloth drying on a rail.
 *
 * <p>Job sites (TAILOR): every loom. Storage: the cloth chest. A COUNTER where the tailor serves.
 *
 * <p>Params: {@code party_left}, {@code party_right}, {@code storeys} (1–3), {@code roof}, {@code variant}.
 */
public final class Tailor implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("work/tailor"), ArchetypeCategory.WORKPLACE, Set.of(JobType.TAILOR), 0,
		6, 9, 7, 11, 8, 14, SettlementTier.VILLAGE, Set.of(), Set.of(ArchetypeInfo.SiteFlag.ATTACHABLE), List.of(), 0, 1.0);

	private static final RoofForm[] FORMS = {RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.GAMBREL, RoofForm.MANSARD, RoofForm.HIP, RoofForm.PAGODA,
		RoofForm.FLAT};

	private static final String[] DYES = {"red", "blue", "yellow", "lime", "purple", "orange", "cyan", "magenta", "light_blue", "white", "brown", "green"};

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		Habits h = d.habits();
		boolean partyL = d.partyLocalLeft();
		boolean partyR = d.partyLocalRight();
		boolean attached = partyL || partyR;
		RoofForm form = Hall.pick(d, FORMS);
		boolean flat = form == RoofForm.FLAT;

		int fz = dp >= 10 && !attached ? 2 : 1;
		int back = dp - fz >= 9 ? 3 : 0;
		int hz0 = fz;
		int hz1 = dp - 1 - back;
		int sh = Math.max(4, h.walls().storeyHeight());
		int room = d.maxHeight() - 4;
		int storeys = Math.clamp(d.param("storeys", room >= sh * 3 && d.chance(0.35) ? 3 : room >= sh * 2 ? 2 : 1), 1, 3);
		while (storeys > 1 && storeys * sh + 4 > d.maxHeight()) {
			storeys--;
		}
		int[] heights = new int[storeys + (flat ? 0 : 1)];
		java.util.Arrays.fill(heights, sh);
		if (!flat) {
			heights[storeys] = 2;
		}
		Hall hall = Hall.of(d, 0, hz0, w - 1, hz1, 0, heights, partyL, partyR);
		boolean timber = h.walls().kind() == Habits.WallKind.TIMBER_FRAME || h.walls().upper() != null;
		if (storeys >= 2 && timber && d.chance(Math.max(0.4, h.walls().jetty()))) {
			hall.body.jetty(1, Dirs.FRONT);
		}
		int[] in = hall.inner(0);
		boolean doorLeft = partyL && !partyR ? false : partyR && !partyL || d.chance(0.5);
		int door = doorLeft ? in[0] : in[2];
		hall.facade(door, 1, 1, 0.6);
		door = hall.door != null ? hall.door : door;
		int ex = Yard.entrance(d);
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		hall.ground(false).shell().openings(true);

		// the stair well at the back corner away from the door, before the roof
		Direction wellWall = door <= (in[0] + in[2]) / 2 ? Dirs.RIGHT : Dirs.LEFT;
		int[][] wells = new int[Math.max(0, hall.storeys() - 1)][];
		for (int k = 0; k + 1 < hall.storeys(); k++) {
			if (k + 1 == hall.storeys() - 1 && !flat && hall.body.height(k + 1) <= 2) {
				break;
			}
			wells[k] = hall.switchback(k, wellWall);
		}

		Side front = hall.side(0, Dirs.FRONT);
		int[] span = Shop.span(in[0], in[2], door, 3);
		// where the stair comes down near the front (a shallow house) the way to its foot runs along the window: no sill board
		boolean board = !(wells.length > 0 && wells[0] != null && wells[0][1] <= in[1] + 1);
		int win = span != null ? span[0] : door;
		int winW = span != null ? span[1] : 0;
		String[] palette = palette(d);
		// an awning the length of the shop front in two of the house's dyes
		awning(d, front, palette);
		if (span != null) {
			Shop.window(d, front, win, winW, 0, i -> wool(palette, i), false, board);
		}
		// lengths of the house's dyed cloth hung on the upper front between the windows: the tailor's sign to the street
		for (int k = 1; k < hall.storeys(); k++) {
			if (hall.body.height(k) < 3) {
				continue;
			}
			Side s = hall.side(k, Dirs.FRONT);
			int y = hall.floorY(k) + 2;
			int hung = 0;
			for (int i = 1; i < s.last() && hung < 2; i++) {
				int ox = s.outX(i, 1);
				int oz = s.outZ(i, 1);
				boolean blank = dev.voidmc.hearth.build.kit.Openings.isWallBlock(d, s.x(i), y, s.z(i))
					&& dev.voidmc.hearth.build.kit.Openings.isWallBlock(d, s.x(i), y - 1, s.z(i));
				if (!blank || !d.allowed(ox, y, oz) || !d.isEmpty(ox, y, oz) || !d.isEmpty(ox, y - 1, oz) || (i + k) % 2 == 1 && hung == 0 && i < s.last() - 2) {
					continue;
				}
				String colour = palette[(hung + k) % palette.length];
				var banner = Props.block("minecraft:" + colour + "_wall_banner", Blocks.WALL_BANNER.pick(DyeColor.WHITE));
				int bi = i;
				d.inStage(StageKind.EXTERIOR, () -> d.set(s.outX(bi, 1), y, s.outZ(bi, 1), dev.voidmc.hearth.build.draft.States.facing(banner, Dirs.FRONT)));
				hung++;
				i++;
			}
		}
		// a balcony off the workroom
		if (storeys >= 2 && d.chance(Math.max(0.35, h.balcony()))) {
			Side up = hall.side(1, Dirs.FRONT);
			int c = up.column(up.middle());
			Porches.balcony(d, up, Math.max(up.column(1), c - 1), Math.min(up.column(up.last() - 1), c + 1), hall.floorY(1));
		}

		Direction.Axis ridge = d.chance(Math.max(0.6, h.roof().frontGable())) ? Direction.Axis.Z : Direction.Axis.X;
		if (form == RoofForm.MANSARD || form == RoofForm.HIP || form == RoofForm.PAGODA) {
			ridge = Direction.Axis.Z;
		}
		hall.roof(form, ridge, 1, plan -> {
			if (plan.axis == Direction.Axis.X && w >= 6 && form != RoofForm.GAMBREL && form != RoofForm.MANSARD) {
				Side top = hall.side(hall.storeys() - 1, Dirs.FRONT);
				plan.dormer(top, top.column(top.middle()), 1, hall.floorY(hall.storeys() - 1));
			}
		});
		if (flat) {
			hall.crown();
		}

		interior(d, hall, door, wells, palette);
		if (!Signs.bracket(d, front, front.index(doorLeft ? door + 1 : door - 1), 3, "tailor", "Tailor")) {
			Signs.bracket(d, front, front.index(win + winW / 2), 4, "tailor", "Tailor");
		}
		Yard.pave(d, 0, 0, w - 1, fz - 1, 0);
		if (back > 0) {
			dyeYard(d, hall, w, hz1 + 1, dp - 1, palette, wellWall);
		}
		Yard.connect(d, ex, door, hz0 - 1);
		if (hall.deck != Integer.MIN_VALUE) {
			// a flat roof is a terrace: a stair up the side, a room set back, an awning
			hall.terrace(d.chance(0.5) ? Dirs.LEFT : Dirs.RIGHT, d.chance(0.7));
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, hall.top + 2);
	}

	/** A striped awning of wool stairs over the whole shop front, three blocks up. */
	private static void awning(Draft d, Side front, String[] palette) {
		Block a = Props.block("minecraft:" + palette[0] + "_wool_stairs", Blocks.WOOL.pick(DyeColor.WHITE));
		Block b = Props.block("minecraft:" + palette[1] + "_wool_stairs", Blocks.WOOL.pick(DyeColor.WHITE));
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int i = 0; i <= front.last(); i++) {
				int x = front.outX(i, 1);
				int z = front.outZ(i, 1);
				if (!d.allowed(x, 3, z) || !d.isAbsent(x, 3, z)) {
					continue;
				}
				d.set(x, 3, z, dev.voidmc.hearth.build.draft.States.stairs(i % 2 == 0 ? a : b, Dirs.BACK, net.minecraft.world.level.block.state.properties.Half.BOTTOM));
			}
		}
	}

	/** Three or four dye colours for this shop: the house's cloth first. */
	private static String[] palette(Draft d) {
		String[] out = new String[4];
		for (int i = 0; i < 4; i++) {
			out[i] = DYES[(int) (Noise.unit(d.seed() ^ 0xD7E, i, 0, 0) * DYES.length) % DYES.length];
		}
		return out;
	}

	private static BlockState wool(String[] palette, int i) {
		return Props.block("minecraft:" + palette[i % palette.length] + "_wool", Blocks.WOOL.pick(DyeColor.WHITE)).defaultBlockState();
	}

	/** The shop below (counter, shelves of cloth, a loom), the workroom above (looms, a cutting table). */
	private static void interior(Draft d, Hall hall, int door, int[][] wells, String[] palette) {
		int[] in = hall.inner(0);
		Floorplan fp = hall.floor(0).blockHoles(d);
		fp.keep(door, in[1]);
		for (int x = in[0]; x <= in[2]; x++) {
			if (!d.isEmpty(x, 0, in[1])) {
				fp.block(x, in[1]);
			}
		}
		if (wells.length > 0 && wells[0] != null) {
			int[] wl = wells[0];
			fp.keep(wl[0], wl[1]);
			for (int x = wl[4]; x <= wl[6]; x++) {
				for (int z = wl[5]; z <= wl[7]; z++) {
					fp.block(x, z);
				}
			}
		}
		// the counter across the shop, two in from the window
		int cz = in[1] + 1;
		boolean left = door <= (in[0] + in[2]) / 2;
		int cx0 = left ? door + 2 : in[0];
		int cx1 = left ? in[2] : door - 2;
		int len = 0;
		int start = Integer.MIN_VALUE;
		if (in[3] - in[1] >= 3) {
			for (int x = cx0; x <= cx1 && len < 3; x++) {
				if (fp.free(x, cz) && fp.free(x, cz + 1) && fp.take(new int[][]{{x, cz}}, null)) {
					if (start == Integer.MIN_VALUE) {
						start = x;
					}
					len++;
				} else if (len > 0) {
					break;
				}
			}
		}
		if (len >= 1) {
			int s = start;
			Shop.counter(d, s, 0, cz, Dirs.RIGHT, len, Dirs.BACK, i -> d.mat(dev.voidmc.hearth.build.style.Role.CLOTH).carpet().at(s + i, 1, cz));
			fp.keep(s + (len - 1) / 2, cz + 1);
		}
		// looms at the back (the tailor works there between customers)
		int looms = 0;
		for (int[] c : fp.along(Dirs.BACK)) {
			if (looms >= 1) {
				break;
			}
			if (fp.take(c[0], c[1], Dirs.FRONT)) {
				Props.station(d, Blocks.LOOM, c[0], 0, c[1], Dirs.FRONT, JobType.TAILOR);
				looms++;
			}
		}
		// the cloth chest
		List<Object[]> cloth = List.of(new Object[]{"minecraft:white_wool", 24}, new Object[]{"minecraft:string", 16},
			new Object[]{"minecraft:" + palette[0] + "_dye", 8}, new Object[]{"minecraft:" + palette[1] + "_dye", 8}, new Object[]{"minecraft:leather", 6});
		boolean chest = false;
		for (int[] c : fp.along(left ? Dirs.RIGHT : Dirs.LEFT)) {
			Direction into = left ? Dirs.LEFT : Dirs.RIGHT;
			if (c[1] > in[1] + 1 && fp.take(c[0], c[1], into)) {
				Props.store(d, c[0], 0, c[1], into, true, cloth);
				chest = true;
				break;
			}
		}
		if (!chest) {
			Props.storeAnywhere(d, fp, cloth);
		}
		// where no stair reaches a workroom above, a second loom below
		boolean upstairs = wells.length > 0 && wells[0] != null;
		if (!upstairs) {
			outer:
			for (Direction wall : new Direction[]{Dirs.BACK, Dirs.LEFT, Dirs.RIGHT}) {
				for (int[] c : fp.along(wall)) {
					if (looms < 2 && c[1] > in[1] && fp.take(c[0], c[1], wall.getOpposite())) {
						Props.station(d, Blocks.LOOM, c[0], 0, c[1], wall.getOpposite(), JobType.TAILOR);
						looms++;
						if (looms >= 2) {
							break outer;
						}
					}
				}
			}
		}
		// bolts of cloth on the walls: wool on barrels
		int bolts = 0;
		for (int[] c : fp.along(left ? Dirs.RIGHT : Dirs.LEFT)) {
			Direction into = left ? Dirs.LEFT : Dirs.RIGHT;
			if (bolts < 2 && c[1] > in[1] + 1 && fp.take(c[0], c[1], into)) {
				int k = bolts;
				d.inStage(StageKind.INTERIOR, () -> {
					d.set(c[0], 0, c[1], wool(palette, k + 1));
					d.set(c[0], 1, c[1], wool(palette, k + 2));
				});
				bolts++;
			}
		}
		if (looms == 0) {
			// no room at the back: the loom by the window inside the door
			for (int[] c : fp.along(left ? Dirs.RIGHT : Dirs.LEFT)) {
				Direction into = left ? Dirs.LEFT : Dirs.RIGHT;
				if (fp.take(c[0], c[1], into)) {
					Props.station(d, Blocks.LOOM, c[0], 0, c[1], into, JobType.TAILOR);
					break;
				}
			}
		}
		Rooms.furnish(d, fp.room(), Rooms.Type.HALL, 0, JobType.NONE);
		// the workroom(s) above
		for (int k = 1; k < hall.storeys(); k++) {
			boolean top = k == hall.storeys() - 1;
			if (wells.length < k || wells[k - 1] == null) {
				break;
			}
			Floorplan up = hall.floor(k).blockHoles(d);
			int[] below = wells[k - 1];
			up.keep(below[2], below[3]);
			up.keepArea(below[4], below[5], below[6], below[7]);
			if (k < wells.length && wells[k] != null) {
				int[] wl = wells[k];
				up.keep(wl[0], wl[1]);
				for (int x = wl[4]; x <= wl[6]; x++) {
					for (int z = wl[5]; z <= wl[7]; z++) {
						up.block(x, z);
					}
				}
			}
			int placed = 0;
			for (Direction wall : new Direction[]{Dirs.FRONT, Dirs.BACK}) {
				for (int[] c : up.along(wall)) {
					if (placed >= (top ? 1 : 2)) {
						break;
					}
					Direction into = wall.getOpposite();
					if (up.take(c[0], c[1], into)) {
						Props.station(d, Blocks.LOOM, c[0], up.y, c[1], into, JobType.TAILOR);
						placed++;
					}
				}
			}
			if (up.height >= 2 && up.x1 - up.x0 >= 3 && up.z1 - up.z0 >= 3) {
				int tx = (up.x0 + up.x1) / 2;
				int tz = (up.z0 + up.z1) / 2;
				if (up.take(tx, tz, null)) {
					Furniture.table(d, tx, up.y, tz);
				}
			}
			Rooms.furnish(d, up.room(), top ? Rooms.Type.STORAGE : Rooms.Type.HALL, 0, JobType.NONE);
		}
	}

	/** The dye yard: vats in cauldrons on a stone floor, cloth drying on a rail, a back door. */
	private static void dyeYard(Draft d, Hall hall, int w, int z0, int z1, String[] palette, Direction wellWall) {
		Yard.trodden(d, 0, z0, w - 1, z1, 0);
		Yard.pave(d, 1, z0, w - 2, z0, 0);
		// a back door away from the stair well
		int[] in = hall.inner(0);
		int bx = wellWall == Dirs.LEFT ? in[2] : in[0];
		hall.door(0, Dirs.BACK, bx, 1, false, false);
		int rx0 = 1;
		int rail = z1;
		int len = Math.max(1, w - 2);
		Props.clothRail(d, rx0, 2, rail, Dirs.RIGHT, len, Dirs.FRONT, palette);
		// posts under the rail ends
		var post = Hall.woodFence(d);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int x : new int[]{rx0 - 1, rx0 + len}) {
				if (x >= 0 && x < w) {
					d.set(x, 0, rail, post.fence());
					d.set(x, 1, rail, post.fence());
					d.set(x, 2, rail, d.mat(dev.voidmc.hearth.build.style.Role.BEAM).log(Direction.Axis.X));
				}
			}
		}
		// vats: a cauldron or two by the wall, the dyer working them
		int vx = wellWall == Dirs.LEFT ? 1 : w - 2;
		if (z1 - z0 >= 2 && d.isEmpty(vx, 0, z0 + 1)) {
			Props.trough(d, vx, 0, z0 + 1, wellWall == Dirs.LEFT ? Dirs.RIGHT : Dirs.LEFT, Math.min(2, w - 4));
			Props.site(d, vx, 0, z0, Dirs.BACK, JobType.TAILOR);
		}
	}
}
