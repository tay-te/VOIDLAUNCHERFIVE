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
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.trade.Floorplan;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.kit.trade.Lifts;
import dev.voidmc.hearth.build.kit.trade.Props;
import dev.voidmc.hearth.build.kit.trade.Shed;
import dev.voidmc.hearth.build.kit.trade.Signs;
import dev.voidmc.hearth.build.kit.trade.Yard;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;

/**
 * {@code hearth:storage/warehouse}: a merchant town's warehouse, three or four floors of goods under a tall roof. Its
 * gable faces the street with the loading bay stacked up its middle — the cart doors below, a loading door at every
 * floor above, and the hoist beam out of the gable's peak with a crate on its chain. Crates and barrels stand on the
 * paved apron with a cart drawn up; a cart shed leans against its side on a broad plot. Inside, racks of barrels in two
 * tiers line the walls and crates fill the middle, a turning stair climbs floor by floor, every floor a store.
 *
 * <p>Storage: the chests on every floor. Params: {@code roof}, {@code storeys} (1–4 floors under the attic, default 3;
 * fewer where the plot's height will not take them), {@code variant}.
 */
public final class Warehouse implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("storage/warehouse"), ArchetypeCategory.STORAGE, Set.of(), 0,
		11, 17, 13, 19, 10, 18, SettlementTier.TOWN, Set.of(), Set.of(), List.of(), 0, 1.0);

	private static final RoofForm[] FORMS = {RoofForm.GABLE, RoofForm.GAMBREL, RoofForm.HALF_HIP, RoofForm.MANSARD, RoofForm.PAGODA, RoofForm.HIP,
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
		int fz = 3;
		boolean shed = w >= 14;
		boolean shedLeft = d.chance(0.5);
		int hx0 = shed && shedLeft ? 4 : 0;
		int hx1 = shed && !shedLeft ? w - 5 : w - 1;
		int hz0 = fz;
		int hz1 = dp - 1;
		int sh = Math.max(4, h.walls().storeyHeight());
		int upper = Math.clamp(d.param("storeys", 3) - 1, 0, 3);
		int[] want = new int[1 + upper];
		Arrays.fill(want, sh);
		want[0] = sh + 1;
		// as many floors as the plot's height takes under the roof, the attic for the hoist where it fits
		int[] heights = Hall.budget(d, hx0, hz0, hx1, hz1, 0, want, true, form, 1, flat ? Direction.Axis.X : Direction.Axis.Z);
		Hall hall = Hall.of(d, hx0, hz0, hx1, hz1, 0, heights, false, false);
		int[] in = hall.inner(0);
		int door = (in[0] + in[2]) / 2;
		hall.facade(door, 2, 1, 0.5);
		door = hall.door != null ? hall.door : door;
		int ex = Yard.entrance(d);
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		hall.ground(false).shell().openings(true);
		// the turning stair, floor by floor, in the back corner
		Direction wellWall = d.chance(0.5) ? Dirs.LEFT : Dirs.RIGHT;
		int[][] wells = new int[hall.storeys()][];
		for (int k = 0; k + 1 < hall.storeys(); k++) {
			if (hall.body.height(k + 1) <= 2) {
				break;
			}
			wells[k] = hall.switchback(k, wellWall);
		}
		// the loading doors up the middle of the front, one per floor
		Side front = hall.side(0, Dirs.FRONT);
		for (int k = 1; k < hall.storeys(); k++) {
			if (hall.body.height(k) < 3) {
				continue;
			}
			Side s = hall.side(k, Dirs.FRONT);
			int y = hall.floorY(k);
			int i = s.index(door);
			for (int c = 0; c < 2; c++) {
				int ii = i + c;
				if (ii <= 0 || ii >= s.last()) {
					continue;
				}
				d.inStage(StageKind.OPENINGS, () -> d.door(s.x(ii), y, s.z(ii), d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR), Dirs.FRONT,
					ii == i ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT));
			}
		}
		Direction.Axis ridge = flat ? Direction.Axis.X : Direction.Axis.Z;
		hall.roof(form, ridge, 1, null);
		if (flat) {
			hall.crown();
		}
		// the hoist beam out of the gable's peak (or over the parapet), its chain to the apron
		int top = hall.storeys() - 1;
		Side g = hall.side(top, Dirs.FRONT);
		int hi = g.index(door);
		int beamY = hall.roof != null ? Math.min(hall.roof.bottomY(g.x(hi), g.z(hi)) - 1, d.maxHeight() - 2) : hall.top;
		if (beamY > hall.floorY(top)) {
			Lifts.hoist(d, g, hi, beamY, 2, 3, States.facing(Blocks.BARREL, Direction.UP));
		}

		interior(d, hall, door, wells);
		apron(d, hall, w, fz, door);
		if (shed) {
			cartShed(d, hall, shedLeft ? 0 : hx1 + 1, shedLeft ? hx0 - 1 : w - 1, hz0, hz1, shedLeft);
		}
		if (!Signs.bracket(d, front, front.index(door - 1), 3, "warehouse", "Warehouse")) {
			Signs.bracket(d, front, front.index(door + 2), 3, "warehouse", "Warehouse");
		}
		Yard.connect(d, ex, door, hz0 - 1);
		if (hall.deck != Integer.MIN_VALUE) {
			// a flat roof is a terrace: a stair up the side, a room set back, an awning
			hall.terrace(d.chance(0.5) ? Dirs.LEFT : Dirs.RIGHT, d.chance(0.7));
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, hall.top + 2);
	}

	/** Racks of barrels along the walls, crates in the middle aisle, stores on every floor round the stair. */
	private static void interior(Draft d, Hall hall, int door, int[][] wells) {
		for (int k = 0; k < hall.storeys(); k++) {
			boolean top = k == hall.storeys() - 1;
			if (top && hall.roof != null && hall.body.height(k) <= 2 && k > 0) {
				// the attic over the top floor: open to its rafters, left to the hoist
				break;
			}
			int[] in = hall.inner(k);
			Floorplan fp = hall.floor(k).blockHoles(d);
			int y = fp.y;
			if (k == 0) {
				fp.keep(door, in[1]);
				fp.keep(door + 1, in[1]);
			} else {
				// the loading doors' inside cells
				fp.keep(door, in[1]);
				fp.keep(door + 1, in[1]);
			}
			if (k > 0 && wells[k - 1] != null) {
				int[] below = wells[k - 1];
				fp.keep(below[2], below[3]);
				fp.keepArea(below[4], below[5], below[6], below[7]);
			}
			if (wells[k] != null) {
				int[] wl = wells[k];
				fp.keep(wl[0], wl[1]);
				for (int x = wl[4]; x <= wl[6]; x++) {
					for (int z = wl[5]; z <= wl[7]; z++) {
						fp.block(x, z);
					}
				}
			}
			// barrel racks along the side walls, two tiers
			int racks = 0;
			for (Direction wall : new Direction[]{Dirs.LEFT, Dirs.RIGHT}) {
				for (int[] c : fp.along(wall)) {
					if (c[1] <= in[1] + 1) {
						continue;
					}
					if (fp.take(c[0], c[1], null)) {
						Props.barrelRack(d, c[0], y, c[1], Dirs.BACK, 1, Math.min(2, fp.height - 1), wall.getOpposite());
						racks++;
					}
				}
			}
			// crates down the middle, an aisle each side
			int mx = (in[0] + in[2]) / 2;
			if (in[2] - in[0] >= 6) {
				for (int z = in[1] + 3; z <= in[3] - 2; z++) {
					if (fp.take(mx, z, null)) {
						Props.crates(d, mx, y, z, Dirs.BACK, 1);
					}
				}
			}
			// a chest of the reckoning by the door, on every floor
			for (int[] c : fp.along(Dirs.FRONT)) {
				if (Math.abs(c[0] - door) >= 2 && c[0] != door + 1 && fp.take(c[0], c[1], Dirs.BACK)) {
					Props.store(d, c[0], y, c[1], Dirs.BACK, true, List.of(new Object[]{"minecraft:paper", 16}, new Object[]{"minecraft:wheat", 32},
						new Object[]{"minecraft:white_wool", 16}, new Object[]{"minecraft:iron_ingot", 8}));
					break;
				}
			}
			Rooms.furnish(d, fp.room(), Rooms.Type.STORAGE, 0, JobType.NONE);
			if (k == 0) {
				hall.joists(0, Direction.Axis.X);
			}
		}
	}

	/** The paved apron: crates on pallets, a cart drawn up, lamps either side of the doors. */
	private static void apron(Draft d, Hall hall, int w, int fz, int door) {
		int[] r = hall.rect(0);
		Yard.pave(d, 0, 0, w - 1, fz - 1, 0);
		int z = fz - 1;
		for (int x : new int[]{r[0] + 1, r[2] - 2}) {
			if (Math.abs(x - door) > 2 && d.isEmpty(x, 0, z) && d.isEmpty(x + 1, 0, z)) {
				Props.crates(d, x, 0, z, Dirs.RIGHT, 2);
			}
		}
		int cx = door < w / 2 ? Math.min(w - 2, door + 4) : Math.max(1, door - 3);
		if (fz >= 3 && d.isEmpty(cx, 0, 1) && d.isEmpty(cx - 1, 0, 1) && d.isEmpty(cx + 1, 0, 1) && d.isEmpty(cx, 0, 0)) {
			Props.cart(d, cx, 0, 1, Dirs.FRONT, States.facing(Blocks.BARREL, Direction.UP));
		}
		for (int x : new int[]{door - 2, door + 3}) {
			if (x >= 0 && x < w && d.isEmpty(x, 0, 0)) {
				Props.lampPost(d, x, 0, 0, 2);
			}
		}
	}

	/** A cart shed leaning on the warehouse's side: posts, a lean-to roof, a cart and sacks under it. */
	private static void cartShed(Draft d, Hall hall, int x0, int x1, int z0, int z1, boolean left) {
		Side wall = hall.side(0, left ? Dirs.LEFT : Dirs.RIGHT);
		int[] r = hall.rect(0);
		Yard.trodden(d, x0, z0, x1, z1, 0);
		Roofs.Roof roof = Shed.leanTo(d, wall, r[3], r[1] + 1, x1 - x0, 0, 3, Math.max(3, hall.floorY(1) - 1));
		int cx = (x0 + x1) / 2;
		int cz = z0 + 2;
		if (d.isEmpty(cx, 0, cz) && d.isEmpty(cx, 0, cz - 1) && cx - 1 >= x0 && cx + 1 <= x1) {
			Props.cart(d, cx, 0, cz, Dirs.FRONT, Blocks.HAY_BLOCK.defaultBlockState());
		}
		Props.sacks(d, left ? x0 : x1, 0, z1 - 1, Dirs.FRONT, 2, false);
		if (roof != null) {
			Props.hangLantern(d, cx, roof.bottomY(cx, z1 - 2) - 1, z1 - 2);
		}
	}


}
