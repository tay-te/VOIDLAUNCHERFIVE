package dev.voidmc.hearth.build.archetype.commerce;

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
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.craft.ShopHouse;
import dev.voidmc.hearth.build.kit.craft.Wares;
import dev.voidmc.hearth.build.kit.landscape.Edges;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.trade.Floorplan;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.kit.trade.Props;
import dev.voidmc.hearth.build.kit.trade.Shop;
import dev.voidmc.hearth.build.kit.trade.Signs;
import dev.voidmc.hearth.build.kit.trade.Yard;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/**
 * {@code hearth:commerce/counting_house}: the merchant's counting house on the market square, where the town's trade is
 * reckoned. A tall, narrow house in the row, sober and well kept: a glazed window to the square with the ledgers on its
 * sill, lamps either side of the door, the sign. Inside, the counting room: a counter across the room with the gold
 * scales on it, where the trader weighs coin and takes in bills; the merchant's desk with its lectern of ledgers under
 * a wall of books; at the back, as far from the door as the room allows, the iron-bound strongbox. Above, the
 * merchant's office and the stores of goods held on account: chests and barrels, bales of cloth, sacks.
 *
 * <p>Job sites: MERCHANT at the desk, TRADER at the counter (COUNTER there too). STRONGBOX at the back of the counting
 * room. Storage: the goods chests upstairs (the counter's barrel below where there is no upstairs).
 *
 * <p>Params: {@code party_left}, {@code party_right}, {@code storeys} (1–3), {@code roof}, {@code variant}.
 */
public final class CountingHouse implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("commerce/counting_house"), ArchetypeCategory.COMMERCE,
		Set.of(JobType.MERCHANT, JobType.TRADER), 0, 7, 10, 9, 13, 9, 14, SettlementTier.TOWN, Set.of(),
		Set.of(ArchetypeInfo.SiteFlag.ATTACHABLE), List.of(), 1, 1.0);

	private static final RoofForm[] FORMS = {RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.HIP, RoofForm.GAMBREL, RoofForm.PAGODA, RoofForm.FLAT};

	private static final List<Object[]> COIN = List.of(new Object[]{"minecraft:gold_ingot", 12}, new Object[]{"minecraft:gold_nugget", 48},
		new Object[]{"minecraft:emerald", 16}, new Object[]{"minecraft:paper", 16});

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		ShopHouse s = ShopHouse.build(d, FORMS, 3, 1, 6, 0.2, true);
		Hall hall = s.hall;
		if (!Signs.bracket(d, s.front, s.signCell(), 3, "counting_house", "Counting House")) {
			Side up = hall.side(Math.min(1, hall.storeys() - 1), Dirs.FRONT);
			Signs.bracket(d, up, up.index(s.doorLeft ? s.door + 1 : s.door - 1), hall.floorY(Math.min(1, hall.storeys() - 1)) + 1, "counting_house",
				"Counting House");
		}
		if (s.winW > 0) {
			window(d, s);
		}
		lamps(d, s);
		boolean stored = countingRoom(d, s);
		stored |= upstairs(d, s);
		if (!stored) {
			Props.storeAnywhere(d, s.groundFloor(), List.<Object[]>of(new Object[]{"minecraft:paper", 32}));
		}
		Yard.pave(d, 0, 0, s.w - 1, s.hz0 - 1, 0);
		Yard.connect(d, s.ex, s.door, s.hz0 - 1);
		if (hall.deck != Integer.MIN_VALUE) {
			// the room on the deck only where the terrace's stair can reach it (the outside stair climbs one storey)
			hall.terrace(d.chance(0.5) ? Dirs.LEFT : Dirs.RIGHT, hall.storeys() == 1 && d.chance(0.6));
		}
		Groundwork.clear(d, 0, 0, s.w - 1, s.dp - 1, hall.top + 2);
		// rails round the stairwells and on the terrace where it stands open over the street
		Edges.guard(d, -4, hall.top);
	}

	/** The window to the square: glazed, no stall board (nothing is sold over this sill), ledgers and a candle on the sill inside. */
	private static void window(Draft d, ShopHouse s) {
		Side f = s.front;
		Shop.window(d, f, s.win, s.winW, 0, i -> null, false, false);
		for (int c = s.win; c < s.win + s.winW; c++) {
			int i = f.index(c);
			int x = f.x(i) + Dirs.dx(Dirs.BACK);
			int z = f.z(i) + Dirs.dz(Dirs.BACK);
			if (d.isEmpty(x, 1, z) && d.get(x, 0, z) != null && !d.isEmpty(x, 0, z) && d.isSolid(x, 0, z)) {
				int cc = c;
				d.inStage(StageKind.INTERIOR, () -> d.set(x, 1, z, (cc & 1) == 0 ? Blocks.CHISELED_BOOKSHELF.defaultBlockState()
					.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Dirs.BACK)
					: Wares.candleState(d, x, 1, z, 1, true)));
			}
		}
	}

	/** A lamp either side of the door where the wall allows: the house that keeps the town's money is well lit. */
	private static void lamps(Draft d, ShopHouse s) {
		Side f = s.front;
		for (int c : new int[]{s.door - 1, s.door + 1}) {
			if (!f.contains(c)) {
				continue;
			}
			int i = f.index(c);
			if (f.isCorner(i) && s.hall.body.isParty(c < s.door ? Dirs.LEFT : Dirs.RIGHT)) {
				continue;
			}
			int ox = f.outX(i, 1);
			int oz = f.outZ(i, 1);
			if (d.allowed(ox, 2, oz) && d.isEmpty(ox, 2, oz) && d.isEmpty(ox, 3, oz) && dev.voidmc.hearth.build.kit.Openings.isWallBlock(d, f.x(i), 2,
				f.z(i))) {
				Lighting.doorLamp(d, ox, 2, oz, Dirs.BACK);
			}
		}
	}

	/**
	 * The counting room: the counter across the room behind the window with the scales on it, the trader behind; the
	 * merchant's desk and lectern under shelves of ledgers along the wall away from the door; the strongbox at the back.
	 * Answers whether a store went in.
	 */
	private static boolean countingRoom(Draft d, ShopHouse s) {
		Hall hall = s.hall;
		int[] in = hall.inner(0);
		Floorplan fp = s.groundFloor();
		boolean left = s.doorLeft;
		Direction wall = left ? Dirs.RIGHT : Dirs.LEFT;
		Direction into = wall.getOpposite();
		boolean stored = false;
		// the strongbox: against the back wall, the far corner from the door first
		List<int[]> back = fp.along(Dirs.BACK);
		if (!left) {
			java.util.Collections.reverse(back);
		}
		boolean box = false;
		for (int[] c : back) {
			if (fp.take(c[0], c[1], Dirs.FRONT)) {
				d.inStage(StageKind.INTERIOR, () -> d.set(c[0], 0, c[1], States.facing(Blocks.CHEST, Dirs.FRONT),
					dev.voidmc.hearth.build.draft.BlockEntities.container(COIN)));
				d.poi(PoiType.STRONGBOX, c[0], 0, c[1] - 1, Dirs.BACK);
				box = true;
				break;
			}
		}
		// the counter across the room two in from the front, the trader behind it at the scales
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
		boolean trader = false;
		if (len >= 1) {
			int st = start;
			int n = len;
			Shop.counter(d, st, 0, cz, Dirs.RIGHT, len, Dirs.BACK, i -> null);
			int mid = st + (n - 1) / 2;
			if (fp.take(new int[0][], new int[]{mid, cz + 1})) {
				d.poi(PoiType.JOB_SITE, mid, 0, cz + 1, Dirs.FRONT, JobType.TRADER);
				trader = true;
			}
			// the scales on the counter's end away from the trader's elbow
			int sx = n >= 2 ? st + n - 1 : st;
			if (n < 3 || sx != st + n - 1) {
				Wares.scales(d, sx, 1, cz, Dirs.RIGHT, true);
			} else if (n >= 3) {
				Wares.scales(d, st, 1, cz, Dirs.RIGHT, true);
			}
		}
		// the merchant's desk: a lectern of ledgers against the side wall, shelves of books over and beside it
		boolean desk = false;
		int shelves = 0;
		for (int[] c : fp.along(wall)) {
			if (c[1] <= cz + 1 && in[3] - in[1] >= 4) {
				continue;
			}
			if (!desk && fp.take(c[0], c[1], into)) {
				Props.station(d, Blocks.LECTERN, c[0], 0, c[1], into, JobType.MERCHANT);
				desk = true;
			} else if (shelves < 2 && fp.take(c[0], c[1], null)) {
				d.inStage(StageKind.INTERIOR, () -> {
					d.set(c[0], 0, c[1], Blocks.BOOKSHELF.defaultBlockState());
					if (d.isEmpty(c[0], 1, c[1])) {
						d.set(c[0], 1, c[1], Blocks.BOOKSHELF.defaultBlockState());
					}
				});
				shelves++;
			}
		}
		if (!desk) {
			for (Direction w2 : new Direction[]{Dirs.BACK, into, Dirs.FRONT}) {
				for (int[] c : fp.along(w2)) {
					if (!desk && fp.take(c[0], c[1], w2.getOpposite())) {
						Props.station(d, Blocks.LECTERN, c[0], 0, c[1], w2.getOpposite(), JobType.MERCHANT);
						desk = true;
					}
				}
			}
		}
		if (!trader) {
			// no room for a counter: the trader keeps his book at a table by the window
			for (int[] c : fp.along(Dirs.FRONT)) {
				if (!trader && Math.abs(c[0] - s.door) > 1 && fp.take(c[0], c[1], Dirs.BACK)) {
					Props.station(d, Blocks.CARTOGRAPHY_TABLE, c[0], 0, c[1], Dirs.BACK, JobType.TRADER);
					trader = true;
				}
			}
		}
		if (!box) {
			for (Direction w2 : new Direction[]{wall, into, Dirs.FRONT}) {
				for (int[] c : fp.along(w2)) {
					if (!box && fp.take(c[0], c[1], w2.getOpposite())) {
						Direction face = w2.getOpposite();
						d.inStage(StageKind.INTERIOR, () -> d.set(c[0], 0, c[1], States.facing(Blocks.CHEST, face),
							dev.voidmc.hearth.build.draft.BlockEntities.container(COIN)));
						d.poi(PoiType.STRONGBOX, c[0] + Dirs.dx(face), 0, c[1] + Dirs.dz(face), face.getOpposite());
						box = true;
					}
				}
			}
		}
		if (hall.storeys() < 2 || s.upperFloor(1) == null) {
			stored = Props.storeAnywhere(d, fp, List.of(new Object[]{"minecraft:paper", 32}, new Object[]{"minecraft:book", 6}));
		}
		Rooms.furnish(d, fp.room(), Rooms.Type.HALL, 0, JobType.NONE);
		Lighting.room(d, fp.room());
		return stored;
	}

	/**
	 * Above: the merchant's office on the first floor (a desk, books, a chest of goods on account), the stores over it
	 * (chests, barrels, bales of cloth, sacks). Answers whether a store went in.
	 */
	private static boolean upstairs(Draft d, ShopHouse s) {
		boolean stored = false;
		for (int k = 1; k < s.hall.storeys(); k++) {
			Floorplan up = s.upperFloor(k);
			if (up == null) {
				break;
			}
			int placed = 0;
			for (Direction wall : new Direction[]{Dirs.BACK, Dirs.FRONT, Dirs.LEFT, Dirs.RIGHT}) {
				for (int[] c : up.along(wall)) {
					if (placed >= (k == 1 ? 3 : 4)) {
						continue;
					}
					Direction face = wall.getOpposite();
					if (!up.take(c[0], c[1], face)) {
						continue;
					}
					int slot = placed;
					if (slot == 0) {
						Props.store(d, c[0], up.y, c[1], face, true, List.of(new Object[]{"minecraft:white_wool", 24}, new Object[]{"minecraft:leather", 12},
							new Object[]{"minecraft:honey_bottle", 6}));
						stored = true;
					} else if (k == 1 && slot == 1) {
						d.inStage(StageKind.INTERIOR, () -> {
							d.set(c[0], up.y, c[1], Blocks.BOOKSHELF.defaultBlockState());
							if (d.isEmpty(c[0], up.y + 1, c[1])) {
								d.set(c[0], up.y + 1, c[1], Blocks.BOOKSHELF.defaultBlockState());
							}
						});
					} else if (slot % 2 == 1) {
						Props.store(d, c[0], up.y, c[1], face, false, List.of(new Object[]{"minecraft:wheat", 32}, new Object[]{"minecraft:salmon", 8}));
					} else {
						var bale = Blocks.WOOL.pick(slot % 4 == 0 ? net.minecraft.world.item.DyeColor.BLUE : net.minecraft.world.item.DyeColor.RED)
							.defaultBlockState();
						d.inStage(StageKind.INTERIOR, () -> d.set(c[0], up.y, c[1], bale));
					}
					placed++;
				}
			}
			Rooms.furnish(d, up.room(), k == 1 ? Rooms.Type.LIVING : Rooms.Type.STORAGE, 0, JobType.NONE);
			Lighting.room(d, up.room());
		}
		return stored;
	}
}
