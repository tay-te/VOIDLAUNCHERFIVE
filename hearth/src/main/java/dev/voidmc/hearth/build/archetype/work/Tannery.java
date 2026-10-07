package dev.voidmc.hearth.build.archetype.work;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.craft.Craft;
import dev.voidmc.hearth.build.kit.craft.Hangs;
import dev.voidmc.hearth.build.kit.craft.Lofts;
import dev.voidmc.hearth.build.kit.craft.Relief;
import dev.voidmc.hearth.build.kit.craft.Vats;
import dev.voidmc.hearth.build.kit.homestead.Bohemia;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.townhome.Proportions;
import dev.voidmc.hearth.build.kit.trade.Floorplan;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.kit.trade.Props;
import dev.voidmc.hearth.build.kit.trade.Signs;
import dev.voidmc.hearth.build.kit.trade.Yard;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * {@code hearth:work/tannery}: the tanner's yard at the downwind edge of town. Behind a fence on the street lies the
 * working yard: tanning pits sunk in a kerbed floor in rows (white lime, brown tan liquor, dark bark ooze, clear water
 * for soaking), a water channel along one side, frames of hides drying behind the fence where the street sees them, a
 * stack of tanbark, a cart with a load of hides. At the back of the yard stands the tannery itself, its long side to the
 * street: the beam house below (soaking vats, the fleshing beam, stores of lime and hides), and over it the drying loft,
 * open between its posts and hung with hides, under a great steep roof crowned with a louvre to let the reek out and a
 * chimney for the boiling-house fire. Where roofs are flat (the Fez manner) the loft is a gallery under a terrace roof
 * where more hides dry on rails, and the pits are round stone vats in a honeycomb.
 *
 * <p>Job sites (TANNER): the soaking vats, the fleshing beam, the pits, the loft. Storage: the chest of leather, the lime
 * barrel. Door: the beam house.
 *
 * <p>Params: {@code roof} (a roof form name), {@code variant}.
 */
public final class Tannery implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("work/tannery"), ArchetypeCategory.WORKPLACE, Set.of(JobType.TANNER), 0,
		9, 13, 11, 15, 11, 16, SettlementTier.VILLAGE, Set.of(), Set.of(ArchetypeInfo.SiteFlag.OUTSKIRTS), List.of(), 0, 1.0);

	private static final RoofForm[] FORMS = {RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.GAMBREL, RoofForm.HIP, RoofForm.PAGODA, RoofForm.FLAT};

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
		if (Craft.steepRoofs(d) && form != RoofForm.GABLE && form != RoofForm.FLAT && !d.hasParam("roof") && d.chance(0.7)) {
			// the tanner's long steep gable over the loft
			form = RoofForm.GABLE;
		}
		boolean flat = form == RoofForm.FLAT;
		boolean south = Craft.southern(d);

		// the tannery across the back of the plot, the yard before it; in the hot wet lands a deeper shed under a broad
		// roof, its loft only where it leaves the shed no taller than half again its narrow side (art review 22)
		Culture c = d.style().culture();
		boolean wet = c == Culture.TROPICAL || c == Culture.BAYOU;
		int bd = wet ? Math.clamp(dp - 4, 6, 9) : Math.clamp(dp - 6, 5, 7);
		int hz1 = dp - 1;
		int hz0 = dp - bd;
		int ex = Yard.entrance(d);
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		int sh = Math.max(4, h.walls().storeyHeight());
		int[] want = wet && 2 * sh + Proportions.rise(d, bd) > Proportions.tallest(Math.min(w, bd)) ? new int[]{sh + 1} : new int[]{sh, 4};
		int[] heights = Hall.budget(d, 0, hz0, w - 1, hz1, 0, want, false, form, 1, Direction.Axis.X);
		boolean loft = heights.length >= 2;
		Hall hall = Hall.of(d, 0, hz0, w - 1, hz1, 0, heights, false, false);
		if (loft && !flat && d.chance(0.7)) {
			// the loft oversails the beam house toward the yard, on brackets
			hall.body.jetty(1, Dirs.FRONT);
		}
		int[] in = hall.inner(0);
		int doorAt = Math.clamp(ex, in[0] + 1, in[2] - 1);
		hall.facade(doorAt, w >= 11 ? 2 : 1, 1, 0.55);
		int door = hall.door != null ? hall.door : doorAt;
		hall.ground(false).shell().openings(true);

		// the stair to the loft along the back wall
		int[] flight = null;
		if (loft) {
			flight = hall.flight(0, Dirs.BACK, door <= (in[0] + in[2]) / 2 ? Dirs.RIGHT : Dirs.LEFT);
		}
		// the drying loft: open between its posts, hung with hides, front and (often) back
		if (loft) {
			Lofts.gallery(d, hall.body, 1, Dirs.FRONT, 3, Hangs.HIDES);
			if (d.chance(0.6)) {
				Lofts.gallery(d, hall.body, 1, Dirs.BACK, 3, Hangs.HIDES);
			}
		}

		// the roof: steep over the loft (Bohemian roofs three to two where the height allows)
		Craft.roof(d, hall, form, Direction.Axis.X, 1, null);
		if (flat) {
			hall.crown();
		} else if (hall.roof != null) {
			if (Bohemia.is(d)) {
				Bohemia.gables(d, hall.roof, Direction.Axis.X, hall.rect(hall.storeys() - 1), hall.plateY());
			}
		}
		// the boiling-house chimney at the gable away from the stair foot
		int chX = flight != null && flight[0] < (in[0] + in[2]) / 2 ? in[2] : in[0];
		int chZ = in[3];
		if (d.chance(Math.max(0.5, h.chimney()))) {
			hall.chimney(chX, chZ, hall.plateY());
		}
		// the frame stands proud of the beam house's walls
		Relief.wall(d, hall.body, hall.facade, Dirs.FRONT, 0, 0, hall.roof, null, Relief.Opts.ALL);

		beamHouse(d, hall, door, flight, chX, chZ);
		if (loft) {
			loftFloor(d, hall, flight);
		}
		yard(d, hall, w, hz0, ex, door, south, loft);
		if (hall.deck != Integer.MIN_VALUE) {
			boolean stair = hall.terrace(d.chance(0.5) ? Dirs.LEFT : Dirs.RIGHT, false);
			if (stair) {
				terraceRacks(d, hall);
			}
		}
		Side front = hall.side(0, Dirs.FRONT);
		Signs.bracket(d, front, front.index(door + (door < (in[0] + in[2]) / 2 ? -1 : hall.doorWidth)), 3, "tannery", "Tanner");
		// a rail wherever the levelled yard ends over a drop
		dev.voidmc.hearth.build.kit.craft.Rails.yard(d);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, hall.top + 2);
	}

	/** Below: the beam house. Soaking vats along the walls, the fleshing beam, lime, hides, a chest of leather. */
	private static void beamHouse(Draft d, Hall hall, int door, int[] flight, int chX, int chZ) {
		int[] in = hall.inner(0);
		Floorplan fp = hall.floor(0).blockHoles(d);
		for (int c = door; c < door + hall.doorWidth; c++) {
			fp.keep(c, in[1]);
		}
		if (flight != null) {
			fp.keep(flight[0], flight[1]);
			for (int x = Math.min(flight[4], flight[6]); x <= Math.max(flight[4], flight[6]); x++) {
				for (int z = Math.min(flight[5], flight[7]); z <= Math.max(flight[5], flight[7]); z++) {
					fp.block(x, z);
				}
			}
		}
		fp.block(chX, chZ);
		// the soaking vats: cauldrons along the side walls (the tanner's own block), each worked from in front
		int vats = 0;
		for (Direction wall : new Direction[]{Dirs.LEFT, Dirs.RIGHT}) {
			for (int[] c : fp.along(wall)) {
				if (vats >= 3 || c[1] == in[1]) {
					continue;
				}
				if (fp.take(c[0], c[1], wall.getOpposite())) {
					Props.station(d, Blocks.CAULDRON, c[0], 0, c[1], wall.getOpposite(), vats == 0 ? JobType.TANNER : null);
					d.inStage(StageKind.INTERIOR, () -> d.set(c[0], 0, c[1], Blocks.WATER_CAULDRON.defaultBlockState()
						.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL_CAULDRON, 3)));
					if (vats == 0) {
						d.poi(dev.voidmc.hearth.api.PoiType.JOB_SITE, c[0] + Dirs.dx(wall.getOpposite()), 0, c[1] + Dirs.dz(wall.getOpposite()),
							wall, JobType.TANNER);
					}
					vats++;
				}
			}
		}
		// the fleshing beam: a stripped log lying in the middle of the floor, worked from its side
		int bx = (in[0] + in[2]) / 2;
		int bz = (in[1] + in[3]) / 2;
		Mat log = d.mat(Role.FRAME);
		for (int[] c : new int[][]{{bx, bz}, {bx + 1, bz}, {bx - 1, bz}, {bx, bz + 1}}) {
			if (fp.take(new int[][]{{c[0], c[1]}}, new int[]{c[0], c[1] - 1})) {
				d.inStage(StageKind.INTERIOR, () -> d.set(c[0], 0, c[1], log.strippedLog(Direction.Axis.X)));
				d.poi(dev.voidmc.hearth.api.PoiType.JOB_SITE, c[0], 0, c[1] - 1, Dirs.BACK, JobType.TANNER);
				break;
			}
		}
		// the leather chest and the lime barrel by the front wall, either side of the door
		List<Object[]> leather = List.of(new Object[]{"minecraft:leather", 24}, new Object[]{"minecraft:rabbit_hide", 12},
			new Object[]{"minecraft:leather_boots", 1});
		boolean chest = false;
		boolean lime = false;
		for (int[] c : fp.along(Dirs.FRONT)) {
			if (Math.abs(c[0] - door) <= 1 || c[0] == door + hall.doorWidth) {
				continue;
			}
			if (!chest && fp.take(c[0], c[1], Dirs.BACK)) {
				Props.store(d, c[0], 0, c[1], Dirs.BACK, true, leather);
				chest = true;
			} else if (!lime && fp.take(c[0], c[1], Dirs.BACK)) {
				Props.store(d, c[0], 0, c[1], Dirs.BACK, false, List.of(new Object[]{"minecraft:bone_meal", 32}, new Object[]{"minecraft:oak_log", 8}));
				lime = true;
			}
		}
		if (!chest) {
			Props.storeAnywhere(d, fp, leather);
		}
		// stacks of hides and sacks of lime against the walls
		int heaps = 0;
		for (Direction wall : new Direction[]{Dirs.BACK, Dirs.LEFT, Dirs.RIGHT}) {
			for (int[] c : fp.along(wall)) {
				if (heaps >= 3) {
					break;
				}
				if (fp.take(c[0], c[1], null)) {
					int k = heaps;
					d.inStage(StageKind.INTERIOR, () -> {
						if (k == 1) {
							d.set(c[0], 0, c[1], Blocks.WOOL.pick(DyeColor.WHITE).defaultBlockState());
						} else {
							d.set(c[0], 0, c[1], Blocks.WOOL.pick(DyeColor.BROWN).defaultBlockState());
							d.set(c[0], 1, c[1], Blocks.CARPET.pick(DyeColor.BROWN).defaultBlockState());
						}
					});
					heaps++;
				}
			}
		}
		Rooms.furnish(d, fp.room(), Rooms.Type.WORKSHOP, 0, JobType.NONE);
		Lighting.room(d, fp.room());
	}

	/** The loft: hides hung on rails down its length, a tanner turning them, light. */
	private static void loftFloor(Draft d, Hall hall, int[] flight) {
		Floorplan up = hall.floor(1).blockHoles(d);
		if (flight != null) {
			up.keep(flight[2], flight[3]);
			up.keepArea(flight[4], flight[5], flight[6], flight[7]);
		}
		int[] in = hall.inner(1);
		int y = hall.floorY(1);
		int top = hall.body.height(1) >= 4 ? y + 2 : y + 1;
		// a rail down the middle of the loft, carried on posts at its ends, hides hung on both faces
		int rz = (in[1] + in[3]) / 2 + 1;
		if (rz < in[3] && top >= y + 2) {
			Hangs.rail(d, in[0], top, rz, Dirs.RIGHT, in[2] - in[0] + 1, Hangs.HIDES, true, true);
		}
		// the loft hand's place by the hides
		for (int[] c : up.along(Dirs.FRONT)) {
			int fz = c[1] + 1;
			if (fz <= in[3] && up.free(c[0], fz) && d.isEmpty(c[0], y, fz) && d.isEmpty(c[0], y + 1, fz)) {
				d.poi(dev.voidmc.hearth.api.PoiType.JOB_SITE, c[0], y, fz, Dirs.FRONT, JobType.TANNER);
				break;
			}
		}
		Lighting.room(d, in[0], in[1], in[2], in[3], y);
	}

	/**
	 * The yard: the fence and gate on the street, the path in to the beam house door; the pits on the roomier side in a
	 * kerbed grid with the water channel along the yard's edge; drying frames of hides on the other side, the first
	 * right behind the fence where the street sees it; tanbark, barrels and a stack of hides by the house.
	 */
	private static void yard(Draft d, Hall hall, int w, int hz0, int ex, int door, boolean south, boolean loft) {
		int yz1 = hz0 - 2;
		Mat kerb = Craft.kerb(d);
		Yard.trodden(d, 0, 0, w - 1, hz0 - 1, 0);
		int dmid = door + (hall.doorWidth - 1) / 2;
		// the path: in from the gate, a jog at the first row if the door is not in line, on to the door
		List<int[]> path = new ArrayList<>();
		path.add(new int[]{ex, 0});
		for (int x = Math.min(ex, dmid); x <= Math.max(ex, dmid); x++) {
			path.add(new int[]{x, 1});
		}
		for (int z = 1; z <= hz0 - 1; z++) {
			path.add(new int[]{dmid, z});
			if (hall.doorWidth > 1) {
				path.add(new int[]{door + 1, z});
			}
		}
		Craft.pave(d, path, 0);
		int lo = Math.min(ex, door);
		int hi = Math.max(ex, door + hall.doorWidth - 1);
		// the fence along the street and down the yard's sides, the gate open on the path
		List<int[]> gaps = new ArrayList<>();
		gaps.add(new int[]{ex, 0});
		Yard.enclose(d, 0, 0, w - 1, yz1, gaps, Dirs.BACK);
		if (dev.voidmc.hearth.build.kit.homestead.Bohemia.is(d)) {
			dev.voidmc.hearth.build.kit.homestead.Bohemia.wattle(d, 0, 0, w - 1, 0);
		}
		// the two sides of the path: frames of hides along the fence where the street sees them, the pits behind, the
		// water channel down the roomier side's outer edge
		int la = 1;
		int lb = lo - 2;
		int ra = hi + 2;
		int rb = w - 2;
		boolean pitsRight = rb - ra >= lb - la;
		for (int[] side : new int[][]{{la, lb}, {ra, rb}}) {
			if (side[1] - side[0] >= 1) {
				Hangs.frame(d, side[0], 0, 1, Dirs.RIGHT, side[1] - side[0] + 1, 3, Hangs.HIDES, false);
			}
		}
		int fa = pitsRight ? la : ra;
		int fb = pitsRight ? lb : rb;
		int lampX = pitsRight ? ex - 1 : ex + 1;
		if (lampX >= 1 && lampX <= w - 2 && d.isEmpty(lampX, 0, 1) && d.isEmpty(lampX, 1, 1)) {
			Lighting.post(d, lampX, 0, 1);
		}
		int za = 3;
		int zb = yz1;
		int chx = pitsRight ? rb : la;
		if (zb - za >= 1 && Math.abs(rb - ra) >= 3) {
			Vats.pit(d, chx, za - 1, chx, zb, 0, Vats.Liquor.WATER, kerb);
		} else {
			chx = Integer.MIN_VALUE;
		}
		// the pits: single pits a kerb apart in rows (lime, tan liquor, bark, water), each under a low rim; round stone
		// vats in a honeycomb where the culture builds so
		Vats.Liquor[] rows = {Vats.Liquor.LIME, Vats.Liquor.TAN, Vats.Liquor.BARK, Vats.Liquor.TAN, Vats.Liquor.WATER};
		int sited = 0;
		for (int[] side : new int[][]{{la, lb}, {ra, rb}}) {
			int a = side[0];
			int b = side[1];
			if (chx == a) {
				a += 2;
			}
			if (chx == b) {
				b -= 2;
			}
			if (b < a) {
				continue;
			}
			boolean right = side[0] == ra;
			if (south && b - a >= 2 && zb - za >= 2) {
				for (int z = za + 1; z <= zb - 1; z += 3) {
					for (int x = a + 1; x <= b - 1; x += 3) {
						Vats.sunkRound(d, x, 0, z, rows[((z - za) / 3 + (x - a) / 3) % rows.length], kerb);
					}
				}
				continue;
			}
			for (int z = za; z <= zb; z += 2) {
				for (int x = right ? a : b; right ? x <= b : x >= a; x += right ? 2 : -2) {
					Vats.Liquor l = rows[((z - za) / 2 + Math.abs(x - (right ? a : b)) / 2) % rows.length];
					Vats.pit(d, x, z, x, z, 0, l, kerb);
					Vats.rim(d, x, z, x, z, 0, kerb);
				}
				// the tanner works the pit beside the path from the path
				int wx = right ? a - 1 : b + 1;
				if (sited < 2 && wx >= 0 && wx < w && Props.site(d, wx, 0, z, right ? Dirs.RIGHT : Dirs.LEFT, JobType.TANNER)) {
					sited++;
				}
			}
		}
		// by the house on the frames' side: tanbark logs under a slab, a stack of hides, barrels of liquor
		int bx = pitsRight ? 1 : w - 2;
		int bz = hz0 - 1;
		Direction along = pitsRight ? Dirs.RIGHT : Dirs.LEFT;
		int bx2 = bx + Dirs.dx(along);
		if (offPath(bx2, lo, hi) && d.isEmpty(bx, 0, bz) && d.isEmpty(bx2, 0, bz)) {
			Garden.woodpile(d, bx, 0, bz, along, 2);
		}
		int sx = pitsRight ? lo - 2 : hi + 2;
		if (sx >= 1 && sx <= w - 2 && offPath(sx, lo, hi) && d.isEmpty(sx, 0, hz0 - 1) && d.isEmpty(sx, 1, hz0 - 1)) {
			d.inStage(StageKind.EXTERIOR, () -> {
				d.set(sx, 0, hz0 - 1, Blocks.WOOL.pick(DyeColor.BROWN).defaultBlockState());
				d.set(sx, 1, hz0 - 1, Blocks.CARPET.pick(DyeColor.WHITE).defaultBlockState());
			});
		}
		int rx = pitsRight ? lo - 3 : hi + 3;
		if (loft && rx >= 1 && rx <= w - 2 && offPath(rx, lo, hi) && d.isEmpty(rx, 0, hz0 - 1)) {
			Garden.barrels(d, rx, 0, hz0 - 1, along.getOpposite(), 1);
		}
	}

	/** Whether column x keeps a cell clear of the path and the door between lo and hi. */
	private static boolean offPath(int x, int lo, int hi) {
		return x < lo - 1 || x > hi + 1;
	}

	/** On a flat roof: rails of hides drying on the terrace. */
	private static void terraceRacks(Draft d, Hall hall) {
		int[] r = hall.rect(hall.storeys() - 1);
		int y = hall.deck;
		int z = (r[1] + r[3]) / 2;
		int x0 = r[0] + 2;
		int x1 = r[2] - 2;
		if (x1 - x0 < 2 || y + 3 >= d.maxHeight()) {
			return;
		}
		Hangs.frame(d, x0, y, z, Dirs.RIGHT, x1 - x0 + 1, 2, Hangs.HIDES, false);
	}

}
