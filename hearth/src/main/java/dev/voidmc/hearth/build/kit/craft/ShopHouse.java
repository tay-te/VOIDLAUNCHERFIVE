package dev.voidmc.hearth.build.kit.craft;

import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.homestead.Bohemia;
import dev.voidmc.hearth.build.kit.trade.Floorplan;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.kit.trade.Shop;
import dev.voidmc.hearth.build.kit.trade.Yard;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.Arrays;
import net.minecraft.core.Direction;

/**
 * The shell of a craftsman's shop on the high street, the way the butcher's and the chandler's stand in a row: narrow,
 * two or three storeys, the shop to the street over a stall row, the workroom behind, the household's rooms and stores
 * above under a steep roof whose gable faces the street where the houses stand in a row (eaves to the street, with a
 * dormer, where it stands free and broad), party walls blind, the upper storey oversailing the shop on brackets where
 * the culture frames its upper floors, the front given its depth ({@link Relief}). A back yard where the plot runs
 * deep. Archetypes build it with {@link #build} and then put in the trade: the shop front, the counter and stock, the
 * workroom, the yard, the sign.
 */
public final class ShopHouse {
	public final Draft d;
	public final Hall hall;
	public final int w;
	public final int dp;
	/** The house's front wall row (the stall row before it), its back wall row, and the yard behind (none if back < 1). */
	public final int hz0;
	public final int hz1;
	public final int back;
	public final boolean partyL;
	public final boolean partyR;
	public final boolean flat;
	/** The street entrance column (on the front row). */
	public final int ex;
	/** The shop door's column. */
	public int door;
	public boolean doorLeft;
	/** The switchback wells, storey by storey (null where a storey has none), and the side they stand against. */
	public int[][] wells;
	public Direction wellWall;
	/** The shop window beside the door: first column and width (0 where there is no room). */
	public int win;
	public int winW;
	public Side front;
	/** The back door's column into the yard, or MIN_VALUE (cut by {@link #backDoor()}). */
	public int backDoor = Integer.MIN_VALUE;

	private ShopHouse(Draft d, Hall hall, int hz0, int hz1, int back, int ex, boolean flat) {
		this.d = d;
		this.hall = hall;
		this.w = d.frontage();
		this.dp = d.depth();
		this.hz0 = hz0;
		this.hz1 = hz1;
		this.back = back;
		this.partyL = d.partyLocalLeft();
		this.partyR = d.partyLocalRight();
		this.ex = ex;
		this.flat = flat;
	}

	/**
	 * Builds the shell: levels the plot, raises up to {@code maxStoreys} storeys (fitted under the height with an attic
	 * under a pitched roof), cuts the stairs, plans the door and the shop window, roofs it and gives the front its
	 * relief. {@code yard} is how deep a back yard the trade wants where the plot allows (the house keeps at least
	 * {@code minDepth} rows); {@code loftyChance} how often a steep-roofed culture builds it as one lofty storey under its
	 * roof; {@code jetty} whether its upper floor may oversail the shop.
	 */
	public static ShopHouse build(Draft d, RoofForm[] forms, int maxStoreys, int yard, int minDepth, double loftyChance, boolean jetty) {
		int w = d.frontage();
		int dp = d.depth();
		Habits h = d.habits();
		boolean partyL = d.partyLocalLeft();
		boolean partyR = d.partyLocalRight();
		boolean attached = partyL || partyR;
		RoofForm form = Hall.pick(d, forms);
		if (Craft.steepRoofs(d) && form == RoofForm.HALF_HIP && attached && !d.hasParam("roof")) {
			form = RoofForm.GABLE;
		}
		if (form == RoofForm.HIP && w <= 8 && !d.hasParam("roof")) {
			// a narrow front under a hip reads as a box with a lid: the gable to the street instead
			form = RoofForm.GABLE;
		}
		boolean flat = form == RoofForm.FLAT;
		int fz = 1;
		int back = dp - fz - yard >= minDepth ? yard : 0;
		int hz0 = fz;
		int hz1 = dp - 1 - back;
		int sh = Math.max(4, h.walls().storeyHeight());
		// the Bohemian village shop: one lofty storey under a great steep roof, the boarded gable to the street; the town
		// shop and the other cultures' two or three storeys
		boolean lofty = Craft.steepRoofs(d) && !flat && d.chance(loftyChance) && !d.hasParam("storeys");
		int want = lofty ? 1 : Math.clamp(d.param("storeys", maxStoreys), 1, maxStoreys);
		int[] base = new int[want];
		Arrays.fill(base, sh);
		if (lofty) {
			base[0] = sh + 1;
		}
		Direction.Axis ridge = attached || w <= 7 || d.chance(Math.max(0.6, h.roof().frontGable())) ? Direction.Axis.Z : Direction.Axis.X;
		int[] heights = Hall.budget(d, 0, hz0, w - 1, hz1, 0, base, !flat && !lofty, form, 1, ridge);
		if (heights[0] < 4) {
			// a shop is never lower than a man and a hand: on a low plot the roof flattens (or goes flat) instead
			heights[0] = 4;
		}
		Hall hall = Hall.of(d, 0, hz0, w - 1, hz1, 0, heights, partyL, partyR);
		int storeys = 0;
		for (int k = 0; k < heights.length; k++) {
			if (heights[k] > 2) {
				storeys++;
			}
		}
		Habits.WallKind upper = h.walls().kind(1);
		boolean framedUpper = upper == Habits.WallKind.TIMBER_FRAME || upper == Habits.WallKind.PLANK || upper == Habits.WallKind.LOG;
		if (jetty && storeys >= 2 && framedUpper && !flat && d.chance(0.65)) {
			// the upper floor oversails the shop toward the street on brackets: the stall row is under cover
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
		ShopHouse s = new ShopHouse(d, hall, hz0, hz1, back, ex, flat);
		s.door = door;
		s.doorLeft = doorLeft;
		// the stair wells at the back corner away from the door, storey by storey
		s.wellWall = door <= (in[0] + in[2]) / 2 ? Dirs.RIGHT : Dirs.LEFT;
		s.wells = new int[Math.max(0, hall.storeys() - 1)][];
		for (int k = 0; k + 1 < hall.storeys(); k++) {
			if (k + 1 == hall.storeys() - 1 && !flat && hall.body.height(k + 1) <= 2) {
				break;
			}
			s.wells[k] = hall.switchback(k, s.wellWall);
		}
		s.front = hall.side(0, Dirs.FRONT);
		int[] span = Shop.span(in[0], in[2], door, 3);
		s.win = span != null ? span[0] : door;
		s.winW = span != null ? span[1] : 0;
		// the roof: the gable to the street in a row; a dormer over the front where the eaves face the street
		RoofForm roofForm = form;
		Craft.roof(d, hall, form, ridge, 1, plan -> {
			// a dormer lights the attic: only over an upper storey (its sill clear of the shop's door and window)
			if (plan.axis == Direction.Axis.X && w >= 7 && hall.storeys() >= 2 && roofForm != RoofForm.GAMBREL && roofForm != RoofForm.MANSARD) {
				Side top = hall.side(hall.storeys() - 1, Dirs.FRONT);
				plan.dormer(top, top.column(top.middle()), 1, hall.floorY(hall.storeys() - 1));
			}
		});
		if (flat) {
			hall.crown();
		} else if (hall.roof != null && Bohemia.is(d)) {
			Bohemia.gables(d, hall.roof, hall.ridge, hall.rect(hall.storeys() - 1), hall.plateY());
		}
		if (hall.roof != null && ridge == Direction.Axis.Z) {
			gableWindow(d, hall);
		}
		Relief.wall(d, hall.body, hall.facade, Dirs.FRONT, 0, hall.storeys() - 1, hall.roof, null, Relief.Opts.ALL);
		return s;
	}

	/** A window in the street gable over the plate: two wide in a broad gable, two high where the gable is tall. */
	static void gableWindow(Draft d, Hall hall) {
		Side top = hall.side(hall.storeys() - 1, Dirs.FRONT);
		int mid = top.middle();
		int y = hall.plateY() + 2;
		int x = top.x(mid);
		int z = top.z(mid);
		if (!hall.roof.covers(x, z)) {
			return;
		}
		int under = hall.roof.bottomY(x, z);
		int h = Math.min(2, under - y - 1);
		if (h < 1 || !dev.voidmc.hearth.build.kit.Openings.isWallBlock(d, x, y, z)) {
			return;
		}
		int width = top.length() >= 9 && top.length() % 2 == 0 ? 2 : 1;
		dev.voidmc.hearth.build.kit.Openings.window(d, top, top.column(mid), y, width, h, dev.voidmc.hearth.build.kit.Openings.WindowStyle.DRESSED);
	}

	/** Cuts the back door into the yard (before the ground floor is furnished); answers its column or MIN_VALUE. */
	public int backDoor() {
		if (this.back < 1) {
			return Integer.MIN_VALUE;
		}
		this.backDoor = Craft.backDoor(this.d, this.hall, this.wellWall, this.wells.length > 0 ? this.wells[0] : null);
		return this.backDoor;
	}

	/** The ground floor for hand furnishing: the doors' inside cells and the stair's foot kept, the well blocked. */
	public Floorplan groundFloor() {
		int[] in = this.hall.inner(0);
		Floorplan fp = this.hall.floor(0).blockHoles(this.d);
		fp.keep(this.door, in[1]);
		if (this.backDoor != Integer.MIN_VALUE) {
			fp.keep(this.backDoor, in[3]);
		}
		for (int x = in[0]; x <= in[2]; x++) {
			if (!this.d.isEmpty(x, 0, in[1])) {
				fp.block(x, in[1]);
			}
		}
		if (this.wells.length > 0 && this.wells[0] != null) {
			int[] wl = this.wells[0];
			fp.keep(wl[0], wl[1]);
			for (int x = wl[4]; x <= wl[6]; x++) {
				for (int z = wl[5]; z <= wl[7]; z++) {
					fp.block(x, z);
				}
			}
		}
		return fp;
	}

	/** Storey k (k >= 1) for hand furnishing: the head of the stair below kept, the well up blocked. Null where no stair reaches it. */
	public Floorplan upperFloor(int k) {
		if (this.wells.length < k || this.wells[k - 1] == null) {
			return null;
		}
		Floorplan up = this.hall.floor(k).blockHoles(this.d);
		int[] below = this.wells[k - 1];
		up.keep(below[2], below[3]);
		up.keepArea(below[4], below[5], below[6], below[7]);
		if (k < this.wells.length && this.wells[k] != null) {
			int[] wl = this.wells[k];
			up.keep(wl[0], wl[1]);
			for (int x = wl[4]; x <= wl[6]; x++) {
				for (int z = wl[5]; z <= wl[7]; z++) {
					up.block(x, z);
				}
			}
		}
		return up;
	}

	/** The cell beside the door on the window's side, where a sign's bracket goes. */
	public int signCell() {
		return this.front.index(this.doorLeft ? this.door + 1 : this.door - 1);
	}
}
