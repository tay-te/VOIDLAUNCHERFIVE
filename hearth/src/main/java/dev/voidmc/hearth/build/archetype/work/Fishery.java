package dev.voidmc.hearth.build.archetype.work;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.trade.Floorplan;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.kit.trade.Props;
import dev.voidmc.hearth.build.kit.trade.Signs;
import dev.voidmc.hearth.build.kit.trade.Yard;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import dev.voidmc.hearth.build.terrain.Site;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/**
 * {@code hearth:work/fishery}: the fish house at the water's edge. A boarded house (in maritime towns a tall, narrow
 * net loft of three floors) stands with its back to the water; behind it a jetty on piles runs out over the water to
 * the mooring, a lantern on its post, barrels of salt fish and a coil of nets. Beside the house hang the nets on their
 * frames and the catch on its drying rack; the house's chimney smokes the herring. On soft or flooded ground the house
 * itself stands on piles, a gallery before its door.
 *
 * <p>Job sites (FISHER): the end of the jetty (where they fish), the gutting bench. A DOCK at the mooring. Storage:
 * the salt barrels and the chest.
 *
 * <p>Params: {@code roof}, {@code storeys} (1–3), {@code variant}.
 */
public final class Fishery implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("work/fishery"), ArchetypeCategory.WORKPLACE, Set.of(JobType.FISHER), 0,
		7, 11, 7, 12, 7, 14, SettlementTier.CAMP, Set.of(), Set.of(ArchetypeInfo.SiteFlag.WATERFRONT), List.of(), 0, 1.0);

	private static final RoofForm[] FORMS = {RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.HIP, RoofForm.SHED, RoofForm.PAGODA, RoofForm.SALTBOX,
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

		// where the water begins, counting from the street
		int shore = dp;
		for (int z = 0; z < dp && shore == dp; z++) {
			int wet = 0;
			for (int x = 0; x < w; x++) {
				if (d.site().isWater(x, z)) {
					wet++;
				}
			}
			if (wet * 2 >= w) {
				shore = z;
			}
		}
		// the house: on the land, its back toward the water; a side strip for the nets
		boolean tall = h.walls().kind() == Habits.WallKind.PLASTER && h.roof().weight(RoofForm.GABLE) >= 3 && h.yard().fence() == Habits.FenceKind.WALL
			&& d.maxHeight() >= 12 && d.chance(0.6);
		int side = w >= 9 ? 3 : 0;
		boolean netsLeft = d.chance(0.5);
		int hx0 = netsLeft ? side : 0;
		int hx1 = netsLeft ? w - 1 : w - 1 - side;
		if (tall) {
			int hw = Math.min(5, hx1 - hx0 + 1);
			hx0 = netsLeft ? w - hw : 0;
			hx1 = hx0 + hw - 1;
		}
		int hz0 = 1;
		int land = Math.max(hz0 + 4, Math.min(shore, dp - 2));
		int hz1 = Math.min(dp - 2, Math.max(hz0 + 4, Math.min(land, hz0 + (tall ? 4 : 6))));
		int sh = Math.max(4, h.walls().storeyHeight());
		int storeys = Math.clamp(d.param("storeys", tall ? 3 : 1), 1, 3);
		while (storeys > 1 && storeys * sh + 4 > d.maxHeight()) {
			storeys--;
		}
		boolean water = d.site().anyWater(hx0, hz0, hx1, hz1);
		int base = 0;
		if (water) {
			for (int z = hz0; z <= hz1; z++) {
				for (int x = hx0; x <= hx1; x++) {
					int wy = d.site().water(x, z);
					base = Math.max(base, wy == Site.NONE ? 0 : wy + 1);
				}
			}
			base = Math.min(base, 2);
		}
		int[] heights = new int[storeys + (flat ? 0 : 1)];
		java.util.Arrays.fill(heights, sh);
		if (!flat) {
			heights[storeys] = 2;
		}
		Hall hall = Hall.of(d, hx0, hz0, hx1, hz1, base, heights, false, false);
		int[] in = hall.inner(0);
		int door = (in[0] + in[2]) / 2;
		hall.facade(door, 1, 1, tall ? 0.6 : 0.4);
		door = hall.door != null ? hall.door : door;
		int ex = Yard.entrance(d);
		Groundwork.level(d, 0, 0, w - 1, Math.min(shore, dp) - 1, 0, false);
		hall.ground(water).shell().openings(true);
		if (base > 0) {
			Groundwork.approach(d, door, base, hz0 - 1);
		}
		// the back door to the jetty
		hall.door(0, Dirs.BACK, in[2] - 1 > door ? in[2] - 1 : in[0] + 1, 1, false, false);
		int backDoor = in[2] - 1 > door ? in[2] - 1 : in[0] + 1;

		Direction.Axis ridge = tall || d.chance(Math.max(0.5, h.roof().frontGable())) ? Direction.Axis.Z : Direction.Axis.X;
		hall.roof(form, ridge, 2, null);
		if (flat) {
			hall.crown();
		}
		// the smoke-house chimney at the back corner
		int cx = backDoor > door ? in[0] : in[2];
		Furniture.hearth(d, cx, base, in[3], Dirs.FRONT, true);
		hall.chimney(cx, in[3], base + 1);

		interior(d, hall, door, backDoor, cx);
		jetty(d, w, hz1 + 1, dp - 1, backDoor, base);
		if (side > 0) {
			nets(d, netsLeft ? 0 : hx1 + 1, netsLeft ? side - 1 : w - 1, hz0, Math.min(hz1, shore - 1));
		}
		Side front = hall.side(0, Dirs.FRONT);
		Signs.bracket(d, front, front.index(door + 1), base + 3, "fishery", "Fish");
		Yard.trodden(d, 0, 0, w - 1, hz0 - 1, 0);
		Yard.connect(d, ex, door, hz0 - 1);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, hall.top + 2);
	}

	private static void interior(Draft d, Hall hall, int door, int backDoor, int hearthX) {
		int[] in = hall.inner(0);
		// the loft above, open over the middle, its edge for nets and barrels
		hall.openLoft(1, hearthX, in[3]);
		Floorplan fp = hall.floor(0);
		fp.keep(door, in[1]);
		fp.keep(backDoor, in[3]);
		fp.block(hearthX, in[3]);
		fp.keep(hearthX, in[3] - 1);
		int y = fp.y;
		// the gutting bench (a barrel and a board) under a window, salt barrels, the chest
		boolean bench = false;
		for (Direction wall : new Direction[]{Dirs.LEFT, Dirs.RIGHT}) {
			for (int[] c : fp.along(wall)) {
				Direction into = wall.getOpposite();
				if (!bench && c[1] > in[1] && fp.take(c[0], c[1], into)) {
					Props.station(d, Blocks.BARREL, c[0], y, c[1], into, JobType.FISHER);
					bench = true;
				}
			}
		}
		for (int[] c : fp.along(Dirs.FRONT)) {
			if (fp.take(c[0], c[1], Dirs.BACK)) {
				Props.store(d, c[0], y, c[1], Dirs.BACK, true, List.of(new Object[]{"minecraft:cod", 16}, new Object[]{"minecraft:salmon", 8},
					new Object[]{"minecraft:string", 12}, new Object[]{"minecraft:fishing_rod", 2}));
				break;
			}
		}
		Rooms.furnish(d, fp.room(), Rooms.Type.STORAGE, 0, JobType.NONE);
	}

	/**
	 * The jetty: a deck on piles from behind the house out over the water to the plot's back edge, a mooring post and
	 * lantern at its end, barrels and a coil of rope.
	 */
	private static void jetty(Draft d, int w, int z0, int z1, int x, int base) {
		if (z1 < z0) {
			return;
		}
		Mat deck = d.mat(Role.FLOOR);
		Mat post = d.mat(Role.STILT);
		Mat fence = Hall.woodFence(d);
		int y = base - 1;
		int x0 = Math.max(0, x - 1);
		int x1 = Math.min(w - 1, x + 1);
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (int z = z0; z <= z1; z++) {
				for (int xx = x0; xx <= x1; xx++) {
					int g = d.site().ground(xx, z);
					boolean wet = d.site().isWater(xx, z);
					if (!wet && g >= y + 1) {
						continue;
					}
					d.set(xx, y, z, deck.full());
					d.set(xx, y + 1, z, Blocks.AIR.defaultBlockState());
					d.set(xx, y + 2, z, Blocks.AIR.defaultBlockState());
					boolean pile = (xx == x0 || xx == x1) && ((z - z0) % 2 == 0 || z == z1);
					if (pile) {
						for (int yy = Math.min(g - 1, y - 1); yy < y; yy++) {
							d.set(xx, yy, z, post.log(Direction.Axis.Y));
						}
					} else if (!wet && g < y) {
						for (int yy = g - 1; yy < y; yy++) {
							d.set(xx, yy, z, post.log(Direction.Axis.Y));
						}
					}
				}
			}
		}
		int ez = z1;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			// a mooring post with a lantern at the end, a rail on one side
			d.set(x1, base, ez, fence.fence());
			d.set(x1, base + 1, ez, States.lantern(d.mat(Role.LIGHT).mainOr(Form.FULL, Blocks.LANTERN), false));
			for (int z = z0 + 1; z < ez; z++) {
				if (d.isEmpty(x0, base, z)) {
					d.set(x0, base, z, fence.fence());
				}
			}
			if (ez - z0 >= 2) {
				d.set(x1, base, z0 + 1, States.facing(Blocks.BARREL, Direction.UP));
			}
		}
		// the mooring and the fishing place only where the jetty reaches the water
		if (d.site().isWater(x, ez) && d.isEmpty(x, base, ez)) {
			d.poi(PoiType.DOCK, x, base, ez, Dirs.BACK);
			d.poi(PoiType.JOB_SITE, x, base, ez, Dirs.BACK, JobType.FISHER);
		}
		if (ez - z0 >= 2 && d.isEmpty(x, base, z0 + 1) && d.get(x, base - 1, z0 + 1) != null) {
			d.poi(PoiType.STORAGE, x, base, z0 + 1, Dirs.RIGHT);
		}
	}

	/** Nets on their frames and fish on the drying rack beside the house. */
	private static void nets(Draft d, int x0, int x1, int z0, int z1) {
		if (z1 - z0 < 2 || d.site().anyWater(x0, z0, x1, z1) || d.site().minGround(x0, z0, x1, z1) < -1) {
			return;
		}
		Yard.trodden(d, x0, z0, x1, z1, 0);
		Mat post = Hall.woodFence(d);
		Mat beam = d.mat(Role.BEAM);
		int x = (x0 + x1) / 2;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			// a net frame: two posts, a rail, the net hung between
			int za = z0 + 1;
			int zb = Math.min(z1, z0 + 3);
			for (int z : new int[]{za, zb}) {
				d.set(x, 0, z, post.fence());
				d.set(x, 1, z, post.fence());
			}
			for (int z = za; z <= zb; z++) {
				d.set(x, 2, z, beam.log(Direction.Axis.Z));
				if (z != za && z != zb) {
					d.set(x, 1, z, Blocks.COBWEB.defaultBlockState());
				}
			}
			// a drying rack of fish: dried kelp on a slatted frame
			if (z1 - zb >= 2) {
				int rz = zb + 2;
				d.set(x, 0, rz, post.fence());
				d.set(x, 1, rz, Blocks.DRIED_KELP_BLOCK.defaultBlockState());
				if (x - 1 >= x0) {
					d.set(x - 1, 0, rz, States.facing(Blocks.BARREL, Direction.UP));
				}
			}
		}
	}
}
