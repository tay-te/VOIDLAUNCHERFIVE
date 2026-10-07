package dev.voidmc.hearth.build.archetype.noble;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.noble.Chambers;
import dev.voidmc.hearth.build.kit.noble.Court;
import dev.voidmc.hearth.build.kit.noble.Dress;
import dev.voidmc.hearth.build.kit.noble.Flag;
import dev.voidmc.hearth.build.kit.noble.Manner;
import dev.voidmc.hearth.build.kit.noble.Mason;
import dev.voidmc.hearth.build.kit.noble.Range;
import dev.voidmc.hearth.build.kit.noble.Tower;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/**
 * {@code hearth:noble/hunting_lodge}: the lord's lodge out by his hunting woods. A hall house of two storeys under a
 * steep roof: the ground storey in stone, the hall with its great hearth, the long table for the hunt's company and
 * the huntsman's bench; over it, in the cultures that frame in timber, a half-timbered storey of bedchambers. At a front
 * corner a slender lookout tower, a ladder up through its floors to the chambers and to its head under the culture's
 * cap, from which the lord watches the chase come out of the trees. Against the front the kennel, a lean-to where the
 * huntsman (HUNTER) keeps the hounds; a woodpile, lamps.
 *
 * <p>Raised in two campaigns: the lodge and its tower; the kennel and the yard.
 */
public final class HuntingLodge implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("noble/hunting_lodge"), ArchetypeCategory.NOBLE, Set.of(JobType.HUNTER), 4,
		15, 21, 14, 20, 20, 30, SettlementTier.TOWN, Set.of(), Set.of(ArchetypeInfo.SiteFlag.OUTSKIRTS), List.of(), 1, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	/** The lodge's form, by the culture (param {@code plan}). */
	enum Plan {
		/** A hall house under a long saddle roof, its upper storey framed, the tower at a front corner (Bohemia, the west). */
		HALL,
		/** A log lodge turning its steep gable to the yard, the tower at a front corner (the north, the mountains). */
		GABLE,
		/** A hipped lodge (a curved hip in the karst, flat in the desert) with its tower in the middle of the front. */
		HIP
	}

	static Plan plan(Draft d, Manner man) {
		String p = d.param("plan", "");
		for (Plan k : Plan.values()) {
			if (k.name().equalsIgnoreCase(p)) {
				return k;
			}
		}
		return switch (man.culture()) {
			case TEMPERATE, WISTERIA, MOORLAND, MARITIME -> Plan.HALL;
			case NORTHERN, TUNDRA, ALPINE -> Plan.GABLE;
			default -> Plan.HIP;
		};
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int maxTop = d.maxHeight() - 1;
		Manner man = Manner.of(d);
		Dress m = Dress.of(d);
		Plan plan = plan(d, man);
		boolean towerLeft = d.param("tower_side", d.chance(0.5) ? "left" : "right").equals("left");
		int y = Court.level(d, 0, 0, w - 1, dp - 1, -1, 1);
		// the lodge across the back a block in from the plot's sides and back, so its eaves stand out round it; the
		// tower and the kennel before it
		int rz0 = 5;
		int rz1 = dp - 2;
		int[] heights = {5, 4};
		Range r = new Range(1, rz0, w - 2, rz1, y, heights, Dirs.FRONT);
		r.blind(Dirs.BACK);
		if (plan == Plan.GABLE && man.culture() != dev.voidmc.hearth.api.Culture.ALPINE) {
			r.timber();
		}
		int c = (w - 1) / 2;
		int tx0 = plan == Plan.HIP ? c - 2 : towerLeft ? 0 : w - 5;
		int tx1 = tx0 + 4;
		int tcx = tx0 + 2;
		int dcol = plan == Plan.HIP ? (towerLeft ? c + 4 : c - 4)
			: towerLeft ? Math.max(tx1 + 2, (w - 1) / 2) : Math.min(tx0 - 2, (w - 1) / 2);
		// the tower's doorway beside its ladder, through the lodge's front wall
		int tdx = towerLeft ? tcx + 1 : tcx - 1;
		d.body(0, rz0, w - 1, rz1);
		d.eave(y + 9);

		try (var ph = d.campaign(0)) {
			Groundwork.level(d, 0, 0, w - 1, dp - 1, y, true);
			r.footings(d, m);
			r.walls(d, m, man);
			if (framed(man) && !r.isTimber()) {
				halfTimber(d, m, r, 1);
			}
			r.floors(d, m);
			r.door(d, m, Dirs.FRONT, dcol, 0, 1, true);
			for (int k = 0; k < 2; k++) {
				r.keepClear(tdx, rz0, r.floorY(k), r.floorY(k) + 2);
			}
			r.windows(d, m, man, 0, 2, 3, Dirs.BACK);
			r.windows(d, m, man, 1, 2, 3, null);
			int hx = towerLeft ? w - 4 : 3;
			int[] hearth = r.hearth(d, hx, rz1, 0, Dirs.FRONT);
			// gables (curved hips in the karst), kept low enough for the tower to stand clear over the ridge
			RoofForm form = plan == Plan.HIP ? RoofForm.HIP : RoofForm.GABLE;
			Direction.Axis axis = plan == Plan.GABLE ? Direction.Axis.Z : Direction.Axis.X;
			int head = Math.min(9, Tower.headRows(man.cap(), 5, man));
			r.roof(d, m, man, form, axis, maxTop - head - 5, plan == Plan.GABLE ? 0 : Math.max(1, (w - 8) / 6), List.of(new int[]{hearth[0], hearth[1]}));
			// the lookout tower: a ladder through its floors, a door into each storey of the lodge, its head over the roof
			int top = Math.min(Math.max(r.plate() + 4, r.ridge + 3), maxTop - head);
			top = Math.max(r.floorY(1) + 4, top);
			Tower t = Tower.square(tx0, 0, tx1, 4, y, top, man.cap() == Manner.Cap.ONION ? Manner.Cap.PYRAMID : man.cap());
			t.footings(d, m);
			t.shell(d, m, top - 1, r.floorY(1));
			t.stair(d, m, man.timberTops(), null);
			t.floors(d, m, top, r.floorY(1));
			for (int k = 0; k < 2; k++) {
				int ly = r.floorY(k);
				t.doorAt(d, m, tdx, 4, Dirs.BACK, ly, 1, false);
				try (var s = d.stage(StageKind.WALLS)) {
					d.set(tdx, ly, rz0, Blocks.AIR.defaultBlockState());
					d.set(tdx, ly + 1, rz0, Blocks.AIR.defaultBlockState());
					d.set(tdx, ly + 2, rz0, m.dress.full());
				}
			}
			t.loops(d, Set.of(Dirs.FRONT, towerLeft ? Dirs.LEFT : Dirs.RIGHT), y + 2, top);
			t.window(d, m, Dirs.FRONT, r.floorY(1) + 1, 2);
			t.flag(towerLeft ? Dirs.RIGHT : Dirs.LEFT);
			t.head(d, m, man, maxTop);
			t.light(d, top - 1);
			t.watch(d, Dirs.FRONT);
			rooms(d, m, r, towerLeft, tdx, dcol, hearth[0]);
		}

		try (var ph = d.campaign(1)) {
			Court.pave(d, m, 0, 0, w - 1, rz0 - 1, y);
			// the kennel against the lodge's front on the far side from the tower
			int kx0 = plan == Plan.HIP ? (towerLeft ? 0 : tx1 + 2) : towerLeft ? Math.max(dcol + 2, w - 6) : 0;
			int kx1 = plan == Plan.HIP ? (towerLeft ? tx0 - 2 : w - 1) : towerLeft ? w - 1 : Math.min(dcol - 2, 5);
			if (plan == Plan.HIP) {
				kx0 = Math.max(kx0, towerLeft ? 0 : Math.max(tx1 + 2, dcol + 2));
				kx1 = Math.min(kx1, towerLeft ? Math.min(tx0 - 2, dcol - 2) : w - 1);
			}
			if (kx1 - kx0 >= 3) {
				int[] door = new int[2];
				Rooms.Room kennel = Court.leanTo(d, m, man, kx0, 1, kx1, rz0 - 1, y, Dirs.BACK, 3, door);
				var fence = d.mat(dev.voidmc.hearth.build.style.Role.FENCE);
				try (var s = d.stage(StageKind.INTERIOR)) {
					for (int x = kennel.x0; x <= kennel.x1; x++) {
						if ((x - kennel.x0) % 2 == 1 && d.isEmpty(x, y, kennel.z1) && Chambers.take(kennel, x, kennel.z1)) {
							d.set(x, y, kennel.z1, fence.fence());
						}
					}
				}
				Rooms.furnish(d, kennel, Rooms.Type.WORKSHOP, 0, JobType.HUNTER);
				Lighting.room(d, kennel.x0, kennel.z0, kennel.x1, kennel.z1, y);
			} else {
				Furniture.workstation(d, JobType.HUNTER, towerLeft ? w - 2 : 1, y, 1, Dirs.BACK);
			}
			if (plan != Plan.HIP) {
				Court.woodpile(d, towerLeft ? tx1 + 1 : tx0 - 1, y, 0, towerLeft ? Dirs.RIGHT : Dirs.LEFT, 1);
			}
			Flag.banner(d, m, dcol, y + 3, rz0 - 1, Dirs.FRONT, 2);
			Court.lamps(d, 0, 0, w - 1, rz0 - 1, y);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, maxTop);
	}

	/** Whether the culture frames the upper storey in timber. */
	static boolean framed(Manner man) {
		return switch (man.culture()) {
			case TEMPERATE, ALPINE, NORTHERN, TUNDRA, WISTERIA, MOORLAND, MARITIME, BAYOU -> true;
			default -> false;
		};
	}

	/**
	 * Half-timbering over storey k: posts at the corners and every few cells, a sill beam along the floor and a plate
	 * along the top, the render between; braces (a stair of the frame) beside the corner posts.
	 */
	static void halfTimber(Draft d, Dress m, Range r, int k) {
		int f = r.floorY(k);
		int c = r.ceiling(k);
		try (var s = d.stage(StageKind.WALLS)) {
			for (Side side : Side.of(r.x0, r.z0, r.x1, r.z1)) {
				Direction.Axis ax = side.axis();
				for (int i = 0; i < side.length(); i++) {
					int x = side.x(i);
					int z = side.z(i);
					boolean post = i == 0 || i == side.last() || i % 2 == 0;
					// braces from the corner posts: a stair of the frame under the plate beside each
					boolean brace = i == 1 || i == side.last() - 1;
					for (int yy = f - 1; yy <= c; yy++) {
						if (!dev.voidmc.hearth.build.kit.Openings.isWallBlock(d, x, yy, z)) {
							continue;
						}
						if (yy == f - 1 || yy == c) {
							d.set(x, yy, z, Mason.beam(m.frame, ax));
						} else if (post) {
							d.set(x, yy, z, Mason.post(m.frame));
						} else if (brace && yy == c - 1 && m.frame.has(dev.voidmc.hearth.build.style.Form.STAIRS)) {
							d.set(x, yy, z, m.frame.stairs(i == 1 ? side.along().getOpposite() : side.along(), net.minecraft.world.level.block.state.properties.Half.TOP));
						}
					}
				}
			}
		}
	}

	private static void rooms(Draft d, Dress m, Range r, boolean towerLeft, int tdx, int dcol, int hx) {
		int[] g = r.room(0);
		Rooms.Room hall = Chambers.room(g[0], g[1], g[2], g[3], g[4], new int[]{dcol, g[1]}, new int[]{tdx, g[1]});
		hall.keep(hx, g[3]);
		hall.keepArea(dcol, g[1], dcol, g[1] + 1);
		// the long table down the middle, benches either side
		int mz = (g[1] + g[3]) / 2;
		int len = Math.max(3, g[2] - g[0] - 6);
		int lx = (g[0] + g[2]) / 2 - len / 2;
		boolean free = true;
		for (int x = lx; x < lx + len; x++) {
			free &= Chambers.take(hall, x, mz);
		}
		if (free) {
			Furniture.longTable(d, lx, g[4], mz, Dirs.RIGHT, len);
			for (int x = lx; x < lx + len; x += 2) {
				for (int dz : new int[]{-1, 1}) {
					if (d.isEmpty(x, g[4], mz + dz) && Chambers.take(hall, x, mz + dz)) {
						Chambers.bench(d, m, x, g[4], mz + dz, dz < 0 ? Dirs.FRONT : Dirs.BACK);
					}
				}
			}
		}
		Chambers.furnish(d, hall, Rooms.Type.LIVING, 0);
		Range.hang(d, g, 4);
		Lighting.room(d, g[0], g[1], g[2], g[3], g[4]);
		// the chambers over it, parted in two
		int[] u = r.room(1);
		int part = (u[0] + u[2]) / 2;
		int door = u[1] + 1;
		r.partition(d, m, 1, true, part, door);
		Rooms.Room a = Chambers.room(u[0], u[1], part - 1, u[3], u[4], new int[]{part - 1, door}, new int[]{tdx, u[1]});
		Rooms.Room b = Chambers.room(part + 1, u[1], u[2], u[3], u[4], new int[]{part + 1, door}, new int[]{tdx, u[1]});
		Chambers.furnish(d, a, Rooms.Type.BEDROOM, 2);
		Chambers.furnish(d, b, Rooms.Type.BEDROOM, 2);
		// the hunt's chambers: the lord's arms and a trophy on the walls, a chest of furs, a rug before the beds
		for (Rooms.Room room : new Rooms.Room[]{a, b}) {
			int[] spot = Chambers.spot(d, room, Dirs.BACK);
			if (spot != null) {
				Furniture.chest(d, spot[0], u[4], spot[1], Dirs.HORIZONTAL[spot[2]].getOpposite(), List.of(new Object[]{"minecraft:leather", 6},
					new Object[]{"minecraft:rabbit_hide", 4}, new Object[]{"minecraft:arrow", 16}));
				Chambers.take(room, spot[0], spot[1]);
			}
		}
		Lighting.room(d, u[0], u[1], u[2], u[3], u[4]);
	}
}
