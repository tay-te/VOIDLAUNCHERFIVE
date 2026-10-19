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
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.craft.Craft;
import dev.voidmc.hearth.build.kit.craft.Hangs;
import dev.voidmc.hearth.build.kit.craft.Lofts;
import dev.voidmc.hearth.build.kit.craft.Relief;
import dev.voidmc.hearth.build.kit.craft.Vats;
import dev.voidmc.hearth.build.kit.homestead.Bohemia;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.trade.Floorplan;
import dev.voidmc.hearth.build.kit.trade.Hall;
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
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;

/**
 * {@code hearth:work/paper_mill}: the paper mill on the race. A tall mill house with its gable to the street: below, the
 * stamping room, where the great wheel in the race outside turns a camshaft along the wall that lifts and drops a row of
 * hammers into the troughs where rags are beaten to pulp; the vat of white pulp in the middle where the vatman dips his
 * mould, the finishing table by the window. Above, the drying loft: its long walls are ladders of louvres, open to the
 * wind, and inside rails run the loft's length hung with sheets of paper. Bales of rags wait at the door.
 *
 * <p>Job sites (PAPERMAKER): the stampers, the vat, the finishing table, the loft. Storage: the paper chest, the rag
 * barrel.
 *
 * <p>Params: {@code roof} (a roof form name), {@code variant}.
 */
public final class PaperMill implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("work/paper_mill"), ArchetypeCategory.WORKPLACE, Set.of(JobType.PAPERMAKER), 0,
		9, 12, 10, 13, 10, 15, SettlementTier.TOWN, Set.of(), Set.of(ArchetypeInfo.SiteFlag.WATERFRONT), List.of(), 1, 1.0);

	private static final RoofForm[] FORMS = {RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.GAMBREL, RoofForm.PAGODA, RoofForm.FLAT};

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
		int ex = Yard.entrance(d);
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		int sh = Math.max(4, h.walls().storeyHeight());
		int[] heights = Hall.budget(d, hx0, hz0, hx1, hz1, 0, new int[]{sh + 1, Math.max(4, sh)}, false, form, 1, Direction.Axis.Z);
		boolean loft = heights.length >= 2;
		Hall hall = Hall.of(d, hx0, hz0, hx1, hz1, 0, heights, false, false);
		int[] in = hall.inner(0);
		int doorAt = (in[0] + in[2]) / 2 + (wheelLeft ? 1 : -1);
		hall.facade(doorAt, 1, 1, 0.4);
		int door = hall.door != null ? hall.door : doorAt;
		// the mill's lower walls are of stone whatever the culture builds its houses of
		if (!Hall.masonry(d)) {
			hall.kind = null;
		}
		hall.ground(false).shell().openings(true);
		Direction toRace = wheelLeft ? Dirs.LEFT : Dirs.RIGHT;
		Direction dry = toRace.getOpposite();
		// the stair to the loft up the dry wall from the back
		int[] flight = null;
		if (loft) {
			flight = hall.flight(0, dry, Dirs.BACK);
			if (flight == null) {
				flight = hall.flight(0, Dirs.BACK, dry);
			}
		}
		// the loft's long walls are louvres; its gables stay closed
		if (loft) {
			Lofts.louvres(d, hall.body, 1, dry, 3);
			Lofts.louvres(d, hall.body, 1, toRace, 3);
		}
		Craft.roof(d, hall, form, Direction.Axis.Z, 1, null);
		if (flat) {
			hall.crown();
		} else if (hall.roof != null && Bohemia.is(d)) {
			Bohemia.gables(d, hall.roof, Direction.Axis.Z, hall.rect(hall.storeys() - 1), hall.plateY());
		}
		Relief.wall(d, hall.body, hall.facade, Dirs.FRONT, 0, 0, hall.roof, null, Relief.Opts.ALL);
		if (loft && hall.roof != null && !flat) {
			Lofts.ridgeVent(d, hall.roof, (hall.rect(1)[0] + hall.rect(1)[2]) / 2, (hz0 + hz1) / 2, Direction.Axis.Z, 3);
		}

		// the race and the wheel, the axle through the wall to the camshaft
		int r = dp >= 12 && w >= 10 ? 4 : 3;
		int cy = r - 1;
		int cz = Math.clamp((hz0 + hz1) / 2, r + 2, dp - r - 2);
		int z0 = cz - r - 1;
		race(d, kerb, raceA, z0, dp - 1);
		Mills.waterWheel(d, raceA, cy, cz, Direction.Axis.X, r, 2, d.mat(Role.FRAME), Hall.woodFence(d), d.mat(Role.FLOOR), null);
		int wallX = wheelLeft ? hx0 : hx1;
		int inX = wheelLeft ? hx0 + 1 : hx1 - 1;
		int hubX = wheelLeft ? raceA + 2 : raceA - 1;
		Mat axle = d.mat(Role.BEAM);
		d.inStage(StageKind.FRAME, () -> {
			if (d.allowed(hubX, cy, cz)) {
				d.set(hubX, cy, cz, axle.log(Direction.Axis.X));
			}
			d.set(wallX, cy, cz, axle.log(Direction.Axis.X));
		});

		stampingRoom(d, hall, door, flight, inX, cy, cz, toRace);
		if (loft) {
			dryingLoft(d, hall, flight);
		}
		Side front = hall.side(0, Dirs.FRONT);
		int si = front.index(door + (wheelLeft ? 1 : -1));
		if (!Signs.bracket(d, front, si, 3, "paper_mill", "Paper Mill")) {
			Signs.bracket(d, front, front.index(door + (wheelLeft ? -1 : 1)), 3, "paper_mill", "Paper Mill");
		}
		Yard.trodden(d, 0, 0, w - 1, hz0 - 1, 0);
		// bales of rags by the door, waiting for the stampers
		int sx = door + (wheelLeft ? 2 : -2);
		if (sx >= 0 && sx < w && d.isEmpty(sx, 0, 0) && d.isEmpty(sx, 1, 0) && Math.abs(sx - ex) >= 1) {
			d.inStage(StageKind.EXTERIOR, () -> {
				d.set(sx, 0, 0, Blocks.WOOL.pick(DyeColor.LIGHT_GRAY).defaultBlockState());
				d.set(sx, 1, 0, Blocks.WOOL_SLAB.pick(DyeColor.WHITE).defaultBlockState());
			});
		}
		Yard.connect(d, ex, door, hz0 - 1);
		if (hall.deck != Integer.MIN_VALUE) {
			if (hall.terrace(dry, false)) {
				int[] rr = hall.rect(hall.storeys() - 1);
				int x = (rr[0] + rr[2]) / 2;
				if (rr[3] - rr[1] >= 6 && hall.deck + 3 < d.maxHeight()) {
					Hangs.frame(d, x, hall.deck, rr[1] + 2, Dirs.BACK, rr[3] - rr[1] - 3, 2, Hangs.PAPER, false);
				}
			}
		}
		// a rail wherever the levelled yard ends over a drop
		dev.voidmc.hearth.build.kit.craft.Rails.yard(d);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.max(hall.top, 2 * r) + 2);
	}

	/**
	 * The stamping room: the camshaft along the wheel wall at three with the hammers hanging from it over their troughs,
	 * the vat of pulp in the middle where the floor is broad, the finishing table, the paper chest and the rag barrel.
	 */
	private static void stampingRoom(Draft d, Hall hall, int door, int[] flight, int inX, int cy, int cz, Direction toRace) {
		int[] in = hall.inner(0);
		Floorplan fp = hall.floor(0).blockHoles(d);
		if (flight != null) {
			for (int x = Math.min(flight[4], flight[6]); x <= Math.max(flight[4], flight[6]); x++) {
				for (int z = Math.min(flight[5], flight[7]); z <= Math.max(flight[5], flight[7]); z++) {
					fp.block(x, z);
				}
			}
			fp.keep(flight[0], flight[1]);
		}
		fp.keep(door, in[1]);
		Direction into = toRace.getOpposite();
		Mat beam = d.mat(Role.BEAM);
		Mat hammer = d.mat(Role.FRAME);
		// the stampers: troughs along the wheel wall, each with its hammer over it hung from the camshaft
		int stampers = 0;
		int camY = Math.max(3, cy + 1);
		int za = Integer.MAX_VALUE;
		int zb = Integer.MIN_VALUE;
		for (int dz : new int[]{0, -1, 1, -2, 2, 3, -3}) {
			int z = cz + dz;
			if (stampers >= 4 || z <= in[1] || z > in[3]) {
				continue;
			}
			if (fp.take(inX, z, into)) {
				int zz = z;
				Vats.cauldron(d, inX, 0, zz, false);
				d.inStage(StageKind.INTERIOR, () -> d.set(inX, camY - 1, zz, hammer.has(Form.STRIPPED_LOG) ? hammer.strippedLog(Direction.Axis.Y)
					: hammer.log(Direction.Axis.Y)));
				if (stampers < 2) {
					d.poi(PoiType.JOB_SITE, inX + Dirs.dx(into), 0, z + Dirs.dz(into), toRace, JobType.PAPERMAKER);
				}
				za = Math.min(za, z);
				zb = Math.max(zb, z);
				stampers++;
			}
		}
		if (stampers > 0) {
			// the camshaft: from the front wall to the back wall over the stampers, carried by both
			try (var st = d.stage(StageKind.INTERIOR)) {
				for (int z = in[1]; z <= in[3]; z++) {
					if (d.isEmpty(inX, camY, z) || d.isAbsent(inX, camY, z)) {
						d.set(inX, camY, z, beam.log(Direction.Axis.Z));
					}
				}
			}
		}
		// the vat of pulp in the middle where the floor is five wide
		int cx = (in[0] + in[2]) / 2 + Dirs.dx(into);
		if (in[2] - in[0] >= 4 && in[3] - in[1] >= 5) {
			for (int vz = in[1] + 2; vz <= in[3] - 2; vz++) {
				int[][] cells = new int[9][];
				int k = 0;
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						cells[k++] = new int[]{cx + dx, vz + dz};
					}
				}
				int[] stand = {cx, vz - 2};
				if (fp.take(cells, stand)) {
					Vats.tub(d, cx, 0, vz, Vats.Liquor.PULP, d.mat(Role.FURNITURE));
					d.poi(PoiType.JOB_SITE, stand[0], 0, stand[1], Dirs.BACK, JobType.PAPERMAKER);
					break;
				}
			}
		}
		// the finishing table, the paper chest and the rag barrel along the dry wall and the front
		List<Object[]> paper = List.of(new Object[]{"minecraft:paper", 48}, new Object[]{"minecraft:book", 4});
		List<Object[]> rags = List.of(new Object[]{"minecraft:white_wool", 16}, new Object[]{"minecraft:string", 24});
		int placed = 0;
		for (Direction wall : new Direction[]{into, Dirs.FRONT, Dirs.BACK}) {
			for (int[] c : fp.along(wall)) {
				if (placed >= 3 || wall == Dirs.FRONT && Math.abs(c[0] - door) <= 1) {
					continue;
				}
				Direction face = wall.getOpposite();
				if (!fp.take(c[0], c[1], face)) {
					continue;
				}
				switch (placed) {
					case 0 -> Props.station(d, Blocks.CARTOGRAPHY_TABLE, c[0], 0, c[1], face, JobType.PAPERMAKER);
					case 1 -> Props.store(d, c[0], 0, c[1], face, true, paper);
					default -> Props.store(d, c[0], 0, c[1], face, false, rags);
				}
				placed++;
			}
		}
		if (placed < 2) {
			Props.storeAnywhere(d, fp, paper);
		}
		Rooms.furnish(d, fp.room(), Rooms.Type.WORKSHOP, 0, JobType.NONE);
		Lighting.room(d, fp.room());
	}

	/** The drying loft: rails of paper down its length, their ends let into the gables; light; the papermaker turning sheets. */
	private static void dryingLoft(Draft d, Hall hall, int[] flight) {
		Floorplan up = hall.floor(1).blockHoles(d);
		if (flight != null) {
			up.keep(flight[2], flight[3]);
			up.keepArea(flight[4], flight[5], flight[6], flight[7]);
		}
		int[] in = hall.inner(1);
		int y = hall.floorY(1);
		int top = y + 2;
		for (int rx = in[0] + 1; rx < in[2] && in[3] - in[1] >= 3; rx += 2) {
			Hangs.rail(d, rx, top, in[1], Dirs.BACK, in[3] - in[1] + 1, Hangs.PAPER, true, false);
		}
		if (flight != null) {
			for (int[] c : up.along(Dirs.BACK)) {
				int fz = c[1] - 1;
				if (up.open(c[0], fz) && d.isEmpty(c[0], y, fz) && d.isEmpty(c[0], y + 1, fz) && up.take(c[0], c[1], Dirs.FRONT)) {
					Props.store(d, c[0], y, c[1], Dirs.FRONT, false, List.<Object[]>of(new Object[]{"minecraft:paper", 32}));
					d.poi(PoiType.JOB_SITE, c[0], y, fz, Dirs.BACK, JobType.PAPERMAKER);
					break;
				}
			}
		}
		Lighting.room(d, in[0], in[1], in[2], in[3], y);
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
}
