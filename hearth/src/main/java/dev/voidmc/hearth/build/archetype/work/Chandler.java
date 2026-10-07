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
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.craft.Craft;
import dev.voidmc.hearth.build.kit.craft.ShopHouse;
import dev.voidmc.hearth.build.kit.craft.Vats;
import dev.voidmc.hearth.build.kit.craft.Wares;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.trade.Floorplan;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.kit.townhome.Proportions;
import dev.voidmc.hearth.build.kit.trade.Props;
import dev.voidmc.hearth.build.kit.trade.Shed;
import dev.voidmc.hearth.build.kit.trade.Shop;
import dev.voidmc.hearth.build.kit.trade.Signs;
import dev.voidmc.hearth.build.kit.trade.Yard;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * {@code hearth:work/chandler}: the chandlery on the market street, the maker of the light every interior needs. A narrow
 * house in the row: its shop window glows with rows of lit candles on the sill board and more set out on the stall board
 * before it, a lantern hangs at either side of the window and the sign by the door. Inside, a counter with a candle tree
 * and the scales, the walls lined with shelves of candles and wax, boards of candles burning; at the back the tallow
 * kettle over its fire under a hood whose flue smokes over the roof, and the dipping rack where the wicks hang. Upstairs
 * the workroom and the wax store; behind, where the plot runs deep, a lean-to where tallow is rendered in the open.
 *
 * <p>Job sites (CHANDLER): the kettle, the dipping rack (and the yard kettle where there is one). COUNTER where the
 * chandler serves. Storage: the wax chest, the tallow barrel.
 *
 * <p>Params: {@code party_left}, {@code party_right}, {@code storeys} (1–3), {@code roof}, {@code variant}.
 */
public final class Chandler implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("work/chandler"), ArchetypeCategory.WORKPLACE, Set.of(JobType.CHANDLER), 0,
		5, 8, 7, 10, 9, 14, SettlementTier.VILLAGE, Set.of(), Set.of(ArchetypeInfo.SiteFlag.ATTACHABLE), List.of(), 0, 1.0);

	private static final RoofForm[] FORMS = {RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.GAMBREL, RoofForm.PAGODA, RoofForm.FLAT};

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		// three storeys in a row; standing alone on a narrow plot, no taller than half again its front (art review 22)
		int sh = Math.max(4, d.habits().walls().storeyHeight());
		int most = Proportions.storeys(d, d.frontage(), 3, sh, 2 + Proportions.rise(d, d.frontage()));
		ShopHouse s = ShopHouse.build(d, FORMS, most, 2, 5, 0.4, true);
		Hall hall = s.hall;
		// the sign by the door before anything else claims the cell
		if (!Signs.bracket(d, s.front, s.signCell(), 3, "chandler", "Chandler")) {
			Side up = hall.side(Math.min(1, hall.storeys() - 1), Dirs.FRONT);
			Signs.bracket(d, up, up.index(s.doorLeft ? s.door + 1 : s.door - 1), hall.floorY(Math.min(1, hall.storeys() - 1)) + 1, "chandler", "Chandler");
		}
		if (s.winW > 0) {
			window(d, s);
		}
		if (s.back >= 2 && s.backDoor() != Integer.MIN_VALUE) {
			yard(d, s);
		}
		interior(d, s);
		upstairs(d, s);
		Yard.pave(d, 0, 0, s.w - 1, s.hz0 - 1, 0);
		Yard.connect(d, s.ex, s.door, s.hz0 - 1);
		if (hall.deck != Integer.MIN_VALUE) {
			hall.terrace(d.chance(0.5) ? Dirs.LEFT : Dirs.RIGHT, d.chance(0.7));
		}
		Groundwork.clear(d, 0, 0, s.w - 1, s.dp - 1, hall.top + 2);
	}

	/**
	 * The shop window: glazed two high, its sill board inside lined with lit candles; outside the stall board with more
	 * candles (left unlit by day: the board is taken in at night), and a lantern at each side of the window.
	 */
	private static void window(Draft d, ShopHouse s) {
		Side f = s.front;
		Shop.window(d, f, s.win, s.winW, 0, i -> null, false, true);
		// candles on the sill board inside, every cell, lit
		for (int c = s.win; c < s.win + s.winW; c++) {
			int i = f.index(c);
			int x = f.x(i) + Dirs.dx(Dirs.BACK);
			int z = f.z(i) + Dirs.dz(Dirs.BACK);
			if (d.isEmpty(x, 1, z) && d.get(x, 0, z) != null && !d.isEmpty(x, 0, z)) {
				Wares.candles(d, x, 1, z, 2 + (c & 1) + (c % 3 == 0 ? 1 : 0), true);
			}
		}
		// the stall board outside, candles on it
		for (int c = s.win; c < s.win + s.winW; c++) {
			int i = f.index(c);
			int ox = f.outX(i, 1);
			int oz = f.outZ(i, 1);
			for (int y = 0; y <= 1; y++) {
				if (d.get(ox, y, oz) != null) {
					d.remove(ox, y, oz);
				}
			}
		}
		List<int[]> board = Wares.stallBoard(d, f, s.win, s.winW, 0);
		for (int[] b : board) {
			if ((b[0] + b[1]) % 2 == 0) {
				Wares.candles(d, b[0], 1, b[1], 3, false);
			} else {
				d.inStage(StageKind.EXTERIOR, () -> d.set(b[0], 1, b[1], Blocks.HONEYCOMB_BLOCK.defaultBlockState()));
			}
		}
		// lanterns flanking the window, the chandler's own light to the street
		for (int c : new int[]{s.win - 1, s.win + s.winW}) {
			if (!f.contains(c) || c == s.door || f.isCorner(f.index(c)) && s.hall.body.isParty(c < s.win ? Dirs.LEFT : Dirs.RIGHT)) {
				continue;
			}
			int i = f.index(c);
			int ox = f.outX(i, 1);
			int oz = f.outZ(i, 1);
			if (d.isEmpty(ox, 2, oz) && d.isEmpty(ox, 3, oz) && d.allowed(ox, 2, oz)) {
				Lighting.doorLamp(d, ox, 2, oz, Dirs.BACK);
			}
		}
	}

	/**
	 * The shop and the chandlery: the counter with a candle tree and scales, shelves of candles, boards of lit candles,
	 * wax cakes, the tallow kettle under its hood with the flue through the roof, the dipping rack.
	 */
	private static void interior(Draft d, ShopHouse s) {
		Hall hall = s.hall;
		int[] in = hall.inner(0);
		Floorplan fp = s.groundFloor();
		boolean left = s.doorLeft;
		// the kettle against the back wall, away from the stair well, its hood and flue over it
		boolean kettle = false;
		List<int[]> back = fp.along(Dirs.BACK);
		if (s.wellWall == Dirs.LEFT) {
			java.util.Collections.reverse(back);
		}
		for (int[] c : back) {
			if (kettle || !fp.take(c[0], c[1], Dirs.FRONT)) {
				continue;
			}
			Vats.kettle(d, c[0], 0, c[1], true);
			d.poi(PoiType.JOB_SITE, c[0], 0, c[1] - 1, Dirs.BACK, JobType.CHANDLER);
			// the hood: the flue rises over the kettle from the head of the room
			int flueFrom = hall.body.height(0) - 1;
			if (flueFrom > 2) {
				hall.chimney(c[0], c[1], flueFrom);
			}
			Mat stone = d.mat(Role.CHIMNEY);
			try (var st = d.stage(StageKind.INTERIOR)) {
				for (Direction side : new Direction[]{Dirs.LEFT, Dirs.RIGHT}) {
					int hx = c[0] + Dirs.dx(side);
					if (hx >= in[0] && hx <= in[2] && d.isEmpty(hx, flueFrom, c[1])) {
						d.set(hx, flueFrom, c[1], stone.stairsOrSlab(side.getOpposite(), Half.TOP));
					}
				}
			}
			kettle = true;
		}
		// the counter across the shop, two in from the window; the chandler behind it
		int cz = in[1] + 1;
		int cx0 = left ? s.door + 2 : in[0];
		int cx1 = left ? in[2] : s.door - 2;
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
			int st = start;
			int n = len;
			Shop.counter(d, st, 0, cz, Dirs.RIGHT, len, Dirs.BACK, i -> i == 0 ? Wares.candleState(d, st, 1, cz, 4, true)
				: i == n - 1 && n >= 3 ? null : Blocks.HONEYCOMB_BLOCK.defaultBlockState());
			fp.keep(st + (len - 1) / 2, cz + 1);
		}
		// the dipping rack along the wall away from the door: a beam with the wicks hanging from it, the dipper before it
		Direction wall = left ? Dirs.RIGHT : Dirs.LEFT;
		Direction into = wall.getOpposite();
		boolean rack = false;
		for (int[] c : fp.along(wall)) {
			if (rack || c[1] <= cz + 1 || c[1] >= in[3]) {
				continue;
			}
			if (fp.free(c[0], c[1] + 1) && fp.take(new int[][]{{c[0], c[1]}, {c[0], c[1] + 1}}, new int[]{c[0] + Dirs.dx(into), c[1]})) {
				Mat beam = d.mat(Role.BEAM);
				try (var st = d.stage(StageKind.INTERIOR)) {
					d.set(c[0], 2, c[1], beam.log(Direction.Axis.Z));
					d.set(c[0], 2, c[1] + 1, beam.log(Direction.Axis.Z));
					d.set(c[0], 1, c[1], States.axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
					d.set(c[0], 1, c[1] + 1, States.axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
					d.set(c[0], 0, c[1], Blocks.POWDER_SNOW_CAULDRON.defaultBlockState()
						.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL_CAULDRON, 2));
					d.set(c[0], 0, c[1] + 1, Blocks.POWDER_SNOW_CAULDRON.defaultBlockState()
						.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL_CAULDRON, 3));
				}
				d.poi(PoiType.JOB_SITE, c[0] + Dirs.dx(into), 0, c[1], wall, JobType.CHANDLER);
				rack = true;
			}
		}
		if (!rack) {
			// no run of wall for the rack: a dipping kettle by a wall, the wicks hung on a hook over it
			outer:
			for (Direction w2 : new Direction[]{wall, Dirs.BACK, into}) {
				for (int[] c : fp.along(w2)) {
					if (c[1] <= in[1] || !fp.take(c[0], c[1], w2.getOpposite())) {
						continue;
					}
					d.inStage(StageKind.INTERIOR, () -> d.set(c[0], 0, c[1], Blocks.POWDER_SNOW_CAULDRON.defaultBlockState()
						.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL_CAULDRON, 3)));
					d.poi(PoiType.JOB_SITE, c[0] + Dirs.dx(w2.getOpposite()), 0, c[1] + Dirs.dz(w2.getOpposite()), w2, JobType.CHANDLER);
					rack = true;
					break outer;
				}
			}
		}
		// shelves of candles and wax up the walls, boards of candles burning, cakes of wax stacked
		int shelves = 0;
		for (Direction w : new Direction[]{wall, Dirs.BACK, into}) {
			for (int[] c : fp.along(w)) {
				if (shelves >= 3 || c[1] <= in[1]) {
					continue;
				}
				Direction face = w.getOpposite();
				int bx = c[0] + Dirs.dx(w);
				int bz = c[1] + Dirs.dz(w);
				if (fp.take(c[0], c[1], face)) {
					int k = shelves;
					if (k == 1) {
						Props.store(d, c[0], 0, c[1], face, true, List.of(new Object[]{"minecraft:honeycomb", 32}, new Object[]{"minecraft:candle", 24},
							new Object[]{"minecraft:string", 16}, new Object[]{"minecraft:white_candle", 8}));
					} else {
						Wares.candleTiers(d, c[0], 0, c[1], face, true);
					}
					if (d.isEmpty(c[0], 2, c[1]) && d.isSolid(bx, 2, bz)) {
						Wares.shelf(d, c[0], 2, c[1], face, Wares.candleStock(d, c[0], 2, c[1]));
					}
					shelves++;
				}
			}
		}
		if (shelves < 2) {
			Props.storeAnywhere(d, fp, List.of(new Object[]{"minecraft:honeycomb", 32}, new Object[]{"minecraft:candle", 24}));
		}
		if (!kettle) {
			// no wall left for the kettle: it stands where it fits, its flue up through the floors
			for (int z = in[3]; z >= in[1] + 2 && !kettle; z--) {
				for (int x = in[0]; x <= in[2] && !kettle; x++) {
					if (fp.take(x, z, null)) {
						Vats.kettle(d, x, 0, z, true);
						for (Direction dir : Dirs.HORIZONTAL) {
							int jx = x + Dirs.dx(dir);
							int jz = z + Dirs.dz(dir);
							if (fp.open(jx, jz) && d.isEmpty(jx, 0, jz) && d.isEmpty(jx, 1, jz)) {
								d.poi(PoiType.JOB_SITE, jx, 0, jz, dir.getOpposite(), JobType.CHANDLER);
								break;
							}
						}
						kettle = true;
					}
				}
			}
		}
		Rooms.furnish(d, fp.room(), Rooms.Type.HALL, 0, JobType.NONE);
		Lighting.room(d, fp.room());
	}

	/** The workroom and the wax store above: a second dipping kettle, barrels of tallow, wax cakes, candles drying on boards. */
	private static void upstairs(Draft d, ShopHouse s) {
		for (int k = 1; k < s.hall.storeys(); k++) {
			Floorplan up = s.upperFloor(k);
			if (up == null) {
				break;
			}
			int placed = 0;
			for (Direction wall : new Direction[]{Dirs.BACK, Dirs.FRONT}) {
				for (int[] c : up.along(wall)) {
					if (placed >= 3 || !up.take(c[0], c[1], wall == Dirs.BACK ? Dirs.FRONT : Dirs.BACK)) {
						continue;
					}
					Direction face = wall.getOpposite();
					if (placed == 0) {
						Props.store(d, c[0], up.y, c[1], face, false, List.of(new Object[]{"minecraft:honeycomb", 24}, new Object[]{"minecraft:string", 12}));
					} else if (placed == 1) {
						Wares.wax(d, c[0], up.y, c[1]);
					} else {
						Wares.candleTiers(d, c[0], up.y, c[1], face, false);
					}
					placed++;
				}
			}
			Rooms.furnish(d, up.room(), Rooms.Type.STORAGE, 0, JobType.NONE);
			Lighting.room(d, up.room());
		}
	}

	/** The rendering yard behind the back door: the tallow kettle under a lean-to on posts, the woodpile, kept walkable. */
	private static void yard(Draft d, ShopHouse s) {
		int w = s.w;
		int z0 = s.hz1 + 1;
		int z1 = s.dp - 1;
		int bx = s.backDoor;
		Yard.trodden(d, 0, z0, w - 1, z1, 0);
		Floorplan yp = Craft.yardPlan(d, 0, z0, w - 1, z1);
		yp.keep(bx, z0);
		// the kettle in the far corner from the back door under a little pent against the back fence
		boolean kLeft = bx > (w - 1) / 2;
		int kx = kLeft ? 1 : w - 2;
		int x0 = kLeft ? 0 : w - 3;
		if (Math.abs(kx - bx) >= 2 && z1 > z0 && yp.take(new int[][]{{kx, z1}, {x0, z1}, {x0 + 2, z1}}, new int[]{kx, z1 - 1})) {
			Vats.kettle(d, kx, 0, z1, true);
			d.poi(PoiType.JOB_SITE, kx, 0, z1 - 1, Dirs.BACK, JobType.CHANDLER);
			Shed.pent(d, x0, z1, x0 + 2, z1, Dirs.BACK, 3, 0.5, null);
			Shed.posts(d, null, List.of(new int[]{x0, z1}, new int[]{x0 + 2, z1}), 0);
		}
		int wx = kLeft ? w - 2 : 1;
		if (Math.abs(wx - bx) >= 1 && yp.take(wx, z1, null)) {
			Garden.woodpile(d, wx, 0, z1, kLeft ? Dirs.LEFT : Dirs.RIGHT, 1);
		}
		int lx = bx + (kLeft ? 1 : -1);
		if (lx >= 0 && lx < w && d.isEmpty(lx, 2, z0) && d.isEmpty(lx, 3, z0)) {
			Lighting.doorLamp(d, lx, 2, z0, Dirs.FRONT);
		}
	}
}
