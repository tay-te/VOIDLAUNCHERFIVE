package dev.voidmc.hearth.build.archetype.noble;

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
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.noble.Chambers;
import dev.voidmc.hearth.build.kit.noble.Court;
import dev.voidmc.hearth.build.kit.noble.Dress;
import dev.voidmc.hearth.build.kit.noble.Flag;
import dev.voidmc.hearth.build.kit.noble.Manner;
import dev.voidmc.hearth.build.kit.noble.Mason;
import dev.voidmc.hearth.build.kit.noble.Range;
import dev.voidmc.hearth.build.kit.noble.Turret;
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
 * {@code hearth:noble/stables}: the lord's stables beside his seat, a stable yard as the great houses of Bohemia had
 * them below their castles. Across the back of a walled yard the stable range: horses in stalls along its back wall on
 * the ground floor (a manger of hay and a water trough to each, the aisle in front), its wide doors on the yard; the
 * hay loft over it under a long roof with a louvred lantern on the ridge, and the stable master's chamber at one end of
 * the loft. Along one side of the yard an open cart shed with more stalls; along the other the hitching rail
 * (HITCHING_POSTs), a mounting block and a trough. A low wall round the front with gate piers and the lord's banner.
 *
 * <p>Every culture's materials and roof (curved in the karst, flat behind a parapet in the south) through the
 * {@link Range} and the {@link Manner}.
 *
 * <p>Raised in two campaigns: the stable range; the yard, its wall and sheds.
 */
public final class Stables implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("noble/stables"), ArchetypeCategory.NOBLE, Set.of(JobType.STABLEMASTER), 1,
		17, 25, 15, 21, 12, 20, SettlementTier.TOWN, Set.of(), Set.of(), List.of(), 1, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int maxTop = d.maxHeight() - 1;
		Manner man = Manner.of(d);
		Dress m = Dress.of(d);
		boolean shedLeft = d.param("shed_side", d.chance(0.5) ? "left" : "right").equals("left");
		int y = Court.level(d, 1, 1, w - 2, dp - 2, -1, 1);
		int c = (w - 1) / 2;
		// the stable range across the back: seven deep (stalls of two and an aisle), the loft over it
		int rz0 = dp - 8;
		int rz1 = dp - 1;
		int rx0 = 0;
		int rx1 = w - 1;
		d.body(rx0, rz0, rx1, rz1);
		d.eave(y + 9);
		int[] heights = {5, 4};

		// ---- campaign 0: the range
		try (var ph = d.campaign(0)) {
			Groundwork.level(d, 0, 0, w - 1, dp - 1, y, true);
			Range r = new Range(rx0, rz0, rx1, rz1, y, heights, Dirs.FRONT);
			r.blind(Dirs.BACK);
			// the loft stair along the end wall away from the shed, up toward the back
			int fx = shedLeft ? rx1 - 1 : rx0 + 1;
			r.flight(fx, rz0 + 1, 0, Dirs.BACK);
			r.footings(d, m);
			r.walls(d, m, man);
			r.floors(d, m);
			r.flights(d, m);
			// the wide doors in the middle, a man's door at each end
			r.door(d, m, Dirs.FRONT, c, 0, 2, true);
			int e0 = rx0 + 3;
			int e1 = rx1 - 3;
			r.door(d, m, Dirs.FRONT, shedLeft ? e1 : e0, 0, 1, false);
			r.windows(d, m, man, 0, 1, 3, null);
			r.windows(d, m, man, 1, 1, 4, null);
			int[] hearth = r.hearth(d, shedLeft ? rx0 + 2 : rx1 - 2, rz1, 1, Dirs.FRONT);
			RoofForm form = man.crowSteps() ? RoofForm.GABLE : d.chance(0.5) ? RoofForm.HIP : RoofForm.GABLE;
			r.roof(d, m, man, form, Direction.Axis.X, maxTop - 3, 0, List.of(new int[]{hearth[0], hearth[1]}));
			// the louvred lantern on the ridge over the doors (an open cupola where the roof is flat)
			if (r.roof != null && r.roof.covers(c, (rz0 + rz1) / 2)) {
				int ty = r.roof.surfaceY(c, (rz0 + rz1) / 2) + 1;
				lantern(d, m, man, c, (rz0 + rz1) / 2, ty, maxTop);
			}
			stalls(d, m, r, shedLeft, fx);
			loft(d, m, r, shedLeft, fx, hearth[0]);
		}

		// ---- campaign 1: the yard, its wall, the shed and the rail
		try (var ph = d.campaign(1)) {
			Court.pave(d, m, 1, 1, w - 2, rz0 - 1, y);
			// the yard wall round the front and sides, gate piers at the way in
			int gw = 2;
			for (int x = 0; x < w; x++) {
				if (Math.abs(x - c) > gw) {
					yardWall(d, m, x, 0, y, 2);
				}
			}
			for (int z = 1; z < rz0; z++) {
				yardWall(d, m, 0, z, y, z >= 2 && z <= rz0 - 2 && shedLeft ? 4 : 2);
				yardWall(d, m, w - 1, z, y, z >= 2 && z <= rz0 - 2 && !shedLeft ? 4 : 2);
			}
			for (int s : new int[]{-1, 1}) {
				int px = c + s * (gw + 1);
				pier(d, m, px, 0, y);
			}
			try (var s = d.stage(StageKind.FLOORS)) {
				for (int x = c - gw; x <= c + gw; x++) {
					d.set(x, y - 1, 0, m.paving.full());
				}
			}
			d.entrance(c, y, -1);
			Flag.banner(d, m, c + gw + 1, y + 2, -1, Dirs.FRONT, 1);
			// the open shed along one side wall: stalls, the hitching post before it, tack (STORAGE)
			int sz0 = 2;
			int sz1 = rz0 - 2;
			if (sz1 - sz0 >= 3) {
				int sx0 = shedLeft ? 1 : w - 5;
				int sx1 = sx0 + 3;
				Court.stable(d, m, man, sx0, sz0, sx1, sz1, y, shedLeft ? Dirs.LEFT : Dirs.RIGHT);
				Lighting.room(d, sx0, sz0, sx1, sz1, y);
			}
			// the hitching rail along the other side, a trough and a mounting block
			int rx = shedLeft ? w - 2 : 1;
			Direction in = shedLeft ? Dirs.LEFT : Dirs.RIGHT;
			var fence = d.mat(Role.FENCE);
			int posts = 0;
			for (int z = 3; z <= rz0 - 3; z += 3) {
				if (!d.isEmpty(rx, y, z)) {
					continue;
				}
				try (var s = d.stage(StageKind.EXTERIOR)) {
					d.set(rx, y, z, fence.fence());
				}
				d.poi(PoiType.HITCHING_POST, rx + Dirs.dx(in), y, z, in.getOpposite());
				posts++;
			}
			if (posts == 0) {
				d.poi(PoiType.HITCHING_POST, c + 2, y, rz0 - 2, Dirs.BACK);
			}
			int tz = rz0 - 2;
			if (d.isEmpty(rx, y, tz) && d.isEmpty(rx, y, tz - 1)) {
				try (var s = d.stage(StageKind.EXTERIOR)) {
					d.set(rx, y, tz, Blocks.WATER_CAULDRON.defaultBlockState()
						.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL_CAULDRON, 3));
					d.set(rx, y, tz - 1, m.dress.stairsOrSlab(in.getOpposite(), Half.BOTTOM));
				}
			}
			// lamps on the range's front at the yard's back corners, under the eaves
			for (int lx : new int[]{2, w - 3}) {
				if (d.isEmpty(lx, y + 2, rz0 - 1) && d.isEmpty(lx, y + 1, rz0 - 1)) {
					Lighting.doorLamp(d, lx, y + 2, rz0 - 1, Dirs.BACK);
				}
			}
			Court.lamps(d, 1, 1, w - 2, rz0 - 1, y);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, maxTop);
	}

	/** Horse stalls along the back wall: fence partitions out from the wall, hay and a trough in each, tack by the door. */
	private static void stalls(Draft d, Dress m, Range r, boolean shedLeft, int fx) {
		int[] g = r.room(0);
		int y = g[4];
		int bz = g[3];
		var fence = d.mat(Role.FENCE);
		Rooms.Room room = Chambers.room(g[0], g[1], g[2], g[3], y);
		room.keepArea(fx, g[1], fx, g[1] + 5);
		room.keepArea(g[0], g[1], g[2], g[1]);
		int sx0 = shedLeft ? g[0] : g[0] + 2;
		int sx1 = shedLeft ? g[2] - 2 : g[2];
		boolean master = false;
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int x = sx0; x <= sx1; x++) {
				int k = x - sx0;
				if (k % 3 == 0) {
					// the partition: two of fence out from the back wall
					for (int dz = 0; dz < 2; dz++) {
						if (d.isEmpty(x, y, bz - dz) && Chambers.take(room, x, bz - dz)) {
							d.set(x, y, bz - dz, fence.fence());
							// a lantern on the post at the stall's end, every other stall
							if (dz == 1 && k % 6 == 0 && d.isEmpty(x, y + 1, bz - dz)) {
								d.set(x, y + 1, bz - dz, dev.voidmc.hearth.build.draft.States.lantern(Blocks.LANTERN, false));
							}
						}
					}
				} else if (k % 3 == 1) {
					if (d.isEmpty(x, y, bz) && Chambers.take(room, x, bz)) {
						d.set(x, y, bz, Blocks.HAY_BLOCK.defaultBlockState());
					}
				} else if (!master && d.isEmpty(x, y, bz) && Chambers.take(room, x, bz)) {
					master = true;
					room.keep(x, bz - 1);
					Court.stablemaster(d, x, y, bz, Dirs.FRONT);
				} else if (d.isEmpty(x, y, bz) && Chambers.take(room, x, bz)) {
					d.set(x, y, bz, Blocks.COMPOSTER.defaultBlockState());
				}
			}
		}
		if (!master) {
			Court.stablemaster(d, (g[0] + g[2]) / 2, y, g[1], Dirs.BACK);
		}
		// the tack: a chest and saddles by the man's door
		int cx = shedLeft ? g[0] : g[2];
		for (int z = g[1] + 1; z <= bz - 2; z++) {
			if (d.isEmpty(cx, y, z) && Chambers.take(room, cx, z)) {
				Furniture.chest(d, cx, y, z, shedLeft ? Dirs.RIGHT : Dirs.LEFT, List.of(new Object[]{"minecraft:saddle", 3},
					new Object[]{"minecraft:lead", 4}, new Object[]{"minecraft:hay_block", 8}));
				break;
			}
		}
		// a lantern over the end by the loft stair, where the stalls' light does not reach
		int ex = shedLeft ? g[2] : g[0];
		if (d.isEmpty(ex, y + 2, g[3])) {
			Lighting.sconce(d, ex, y + 2, g[3], shedLeft ? Dirs.RIGHT : Dirs.LEFT);
		}
		Lighting.room(d, g[0], g[1], g[2], g[3], y);
	}

	/** The loft: hay stacked along the back, the stable master's chamber at the stair's far end. */
	private static void loft(Draft d, Dress m, Range r, boolean shedLeft, int fx, int hx) {
		int[] u = r.room(1);
		int y = u[4];
		int land = u[1] + r.heights[0];
		int part = shedLeft ? u[0] + 5 : u[2] - 5;
		r.partition(d, m, 1, true, part, (u[1] + u[3]) / 2);
		Rooms.Room hay = Chambers.room(shedLeft ? part + 1 : u[0], u[1], shedLeft ? u[2] : part - 1, u[3], y,
			new int[]{fx, land}, new int[]{fx, land - 1}, new int[]{shedLeft ? part + 1 : part - 1, (u[1] + u[3]) / 2});
		hay.keepArea(fx, u[1], fx, land);
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int x = hay.x0; x <= hay.x1; x++) {
				if ((x & 3) != 0 && d.isEmpty(x, y, u[3]) && Chambers.take(hay, x, u[3])) {
					d.set(x, y, u[3], Blocks.HAY_BLOCK.defaultBlockState());
					if ((x & 1) == 0 && d.isEmpty(x, y + 1, u[3])) {
						d.set(x, y + 1, u[3], Blocks.HAY_BLOCK.defaultBlockState());
					}
				}
			}
		}
		Chambers.furnish(d, hay, Rooms.Type.STORAGE, 0);
		Rooms.Room chamber = Chambers.room(shedLeft ? u[0] : part + 1, u[1], shedLeft ? part - 1 : u[2], u[3], y,
			new int[]{shedLeft ? part - 1 : part + 1, (u[1] + u[3]) / 2});
		chamber.keep(hx, u[3]);
		Chambers.furnish(d, chamber, Rooms.Type.BEDROOM, 1);
		Lighting.room(d, u[0], u[1], u[2], u[3], y);
	}

	/** A louvred lantern on the ridge: a timber box with open sides under a little cap, a weathervane on the spike. */
	private static void lantern(Draft d, Dress m, Manner man, int x, int z, int y, int maxTop) {
		if (y + 6 > maxTop) {
			return;
		}
		try (var s = d.stage(StageKind.ROOF)) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					d.set(x + dx, y, z + dz, m.boards.full());
					boolean corner = dx != 0 && dz != 0;
					if (corner) {
						d.set(x + dx, y + 1, z + dz, Mason.post(m.frame));
					} else if (dx == 0 && dz == 0) {
						d.set(x + dx, y + 1, z + dz, Blocks.AIR.defaultBlockState());
					} else {
						d.set(x + dx, y + 1, z + dz, m.boards.trapdoor(dx == 0 ? (dz < 0 ? Dirs.BACK : Dirs.FRONT) : (dx < 0 ? Dirs.RIGHT : Dirs.LEFT), Half.BOTTOM, true));
					}
				}
			}
		}
		Turret.cap(d, m, man, x, z, 3, y + 2, maxTop);
	}

	/** A low wall of the walling with a coping, {@code h} courses high, on its footing. */
	private static void yardWall(Draft d, Dress m, int x, int z, int y, int h) {
		if (!d.isEmpty(x, y, z)) {
			return;
		}
		Mason.footing(d, m, x, z, y - 1);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int yy = y; yy < y + h - 1; yy++) {
				d.set(x, yy, z, m.wall.full());
			}
			d.set(x, y + h - 1, z, m.dress.has(dev.voidmc.hearth.build.style.Form.SLAB) ? m.dress.slab(SlabType.BOTTOM) : m.dress.full());
		}
	}

	/** A gate pier: a column of the dressing three high under a cap, a lamp on it. */
	private static void pier(Draft d, Dress m, int x, int z, int y) {
		Mason.footing(d, m, x, z, y - 1);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int yy = y; yy < y + 3; yy++) {
				d.set(x, yy, z, m.dress.full());
			}
		}
		Lighting.lamp(d, x, y + 3, z);
	}
}
