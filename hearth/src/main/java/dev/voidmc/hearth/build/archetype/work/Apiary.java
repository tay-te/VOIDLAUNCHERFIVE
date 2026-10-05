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
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.craft.Craft;
import dev.voidmc.hearth.build.kit.homestead.Bohemia;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.trade.Floorplan;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.kit.trade.Props;
import dev.voidmc.hearth.build.kit.trade.Yard;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/**
 * {@code hearth:work/apiary}: the beekeeper's garden at the edge of the fields. Behind its fence, rows of flowers and
 * herbs run across the garden with hives on posts standing among them, and a water butt for the bees. Across the back
 * stands the bee house (the Bohemian {@code včelín}, the bee wall of the south): a little hut whose front wall is a
 * row of hives, their entrances to the garden, with the beekeeper's room behind them: the honey shelf, the chest of comb
 * and wax, the bench where the frames are cut, a lantern. Small and low, it reads from far off as a bright patch of
 * flowers with a dark hut behind.
 *
 * <p>Job sites (BEEKEEPER): the hives in the garden, the bench in the bee house. Storage: the honey chest.
 *
 * <p>Params: {@code roof} (a roof form name), {@code variant}.
 */
public final class Apiary implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("work/apiary"), ArchetypeCategory.WORKPLACE, Set.of(JobType.BEEKEEPER), 0,
		7, 11, 7, 11, 9, 12, SettlementTier.HAMLET, Set.of(), Set.of(ArchetypeInfo.SiteFlag.OUTSKIRTS), List.of(), 0, 1.0);

	private static final RoofForm[] FORMS = {RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.HIP, RoofForm.PAGODA, RoofForm.FLAT};

	private static final net.minecraft.world.level.block.Block[] FLOWERS = {Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.POPPY, Blocks.DANDELION,
		Blocks.ALLIUM, Blocks.AZURE_BLUET, Blocks.LILAC, Blocks.PEONY, Blocks.ROSE_BUSH};

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		RoofForm form = Hall.pick(d, FORMS);
		int ex = Math.clamp(Yard.entrance(d), 1, w - 2);
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		Yard.trodden(d, 0, 0, w - 1, dp - 1, 0);
		// the bee house across the back, inside the fence: four deep, the hives in its front wall
		int bx0 = 1;
		int bx1 = w - 2;
		int bz1 = dp - 2;
		int bz0 = bz1 - 3;
		int[] heights = Hall.budget(d, bx0, bz0, bx1, bz1, 0, new int[]{5}, false, form, 1, Direction.Axis.X);
		Hall hall = Hall.of(d, bx0, bz0, bx1, bz1, 0, heights, false, false);
		int[] in = hall.inner(0);
		int doorAt = Math.clamp(ex, in[0], in[2]);
		hall.facade(doorAt, 1, 1, 0.0);
		int door = hall.door != null ? hall.door : doorAt;
		hall.ground(false).shell().openings(false);
		Craft.roof(d, hall, form, Direction.Axis.X, 1, null);
		if (form == RoofForm.FLAT) {
			hall.crown();
		} else if (hall.roof != null && Bohemia.is(d)) {
			Bohemia.gables(d, hall.roof, Direction.Axis.X, hall.rect(0), hall.plateY());
		}
		Side front = hall.side(0, Dirs.FRONT);
		List<Integer> hiveCols = hiveWall(d, front, door);
		beeHouse(d, hall, door, hiveCols);

		// the garden: a path from the gate to the bee house door, rows of flowers either side, hives on posts among them
		Floorplan yp = Craft.yardPlan(d, 1, 1, w - 2, bz0 - 1);
		yp.keep(ex, 1);
		if (bz0 - 1 >= 1) {
			yp.keep(door, bz0 - 1);
		}
		List<int[]> walk = new ArrayList<>();
		for (int z = 1; z < bz0; z++) {
			int x = z < (bz0 + 1) / 2 ? ex : door;
			walk.add(new int[]{x, z});
			yp.keep(x, z);
		}
		for (int x = Math.min(ex, door); x <= Math.max(ex, door); x++) {
			int z = (bz0 + 1) / 2;
			if (z >= 1 && z < bz0) {
				walk.add(new int[]{x, z});
				yp.keep(x, z);
			}
		}
		Garden.path(d, walk, 0);
		// a lamp inside the gate, and one by the bee house door
		// the gate's post carries the lamp
		int lampX = ex + 1 <= w - 2 ? ex + 1 : ex - 1;
		Lighting.post(d, lampX, 0, 0);
		for (int off : new int[]{1, -1}) {
			int x = door + off;
			if (bz0 - 1 >= 1 && x >= 1 && x <= w - 2 && d.isEmpty(x, 2, bz0 - 1) && d.isAbsent(x, 2, bz0) == false) {
				Lighting.doorLamp(d, x, 2, bz0 - 1, Dirs.BACK);
				break;
			}
		}
		// the beekeeper at the bee wall, tending the hives from the garden
		int tended = 0;
		for (int c : hiveCols) {
			if (tended < 2 && bz0 - 1 >= 1 && yp.take(new int[0][], new int[]{c, bz0 - 1})) {
				d.poi(PoiType.JOB_SITE, c, 0, bz0 - 1, Dirs.BACK, JobType.BEEKEEPER);
				tended++;
			}
		}
		// hives on posts, in a row across the garden two in from the bee house, worked from the path side
		int hives = 0;
		int hz = bz0 - 3;
		if (hz >= 2) {
			for (int x = 1; x <= w - 2; x += 2) {
				if (Math.abs(x - ex) <= 0 || Math.abs(x - door) <= 0) {
					continue;
				}
				if (yp.take(x, hz, Dirs.FRONT)) {
					Garden.beehive(d, x, 0, hz, Dirs.FRONT);
					if (hives < 2) {
						d.poi(PoiType.JOB_SITE, x, 0, hz - 1, Dirs.BACK, JobType.BEEKEEPER);
					}
					hives++;
				}
			}
		}
		// the water butt by the path
		for (int z = 2; z < bz0; z++) {
			int x = ex + 1 <= w - 2 ? ex + 1 : ex - 1;
			if (yp.take(x, z, null)) {
				Garden.waterButt(d, x, 0, z);
				break;
			}
		}
		// a small garden: hives wherever one still fits, so the beekeeper has his two places of work
		long sites = d.pois().stream().filter(p -> p.type() == PoiType.JOB_SITE).count();
		for (int z = 1; z < bz0 && sites < 2; z++) {
			for (int x = 1; x <= w - 2 && sites < 2; x++) {
				for (Direction dir : Dirs.HORIZONTAL) {
					if (yp.take(x, z, dir)) {
						Garden.beehive(d, x, 0, z, dir);
						d.poi(PoiType.JOB_SITE, x + Dirs.dx(dir), 0, z + Dirs.dz(dir), dir.getOpposite(), JobType.BEEKEEPER);
						sites++;
						break;
					}
				}
			}
		}
		// flowers wherever is left, in rows by kind, a little thinned
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = 1; z < bz0; z++) {
				for (int x = 1; x <= w - 2; x++) {
					// clumps of a kind two or three wide, the kinds changing across the garden and row to row
					net.minecraft.world.level.block.Block kind = FLOWERS[Math.floorMod((int) (d.seed() >>> 7) + (x + (z & 1)) / 2 * 3 + z * 2,
						FLOWERS.length)];
					if (!yp.free(x, z) || !d.isEmpty(x, 0, z) || d.chance(0.12)) {
						continue;
					}
					boolean tall = kind == Blocks.LILAC || kind == Blocks.PEONY || kind == Blocks.ROSE_BUSH;
					if (tall && !d.isEmpty(x, 1, z)) {
						continue;
					}
					if (yp.take(x, z, null)) {
						d.set(x, -1, z, Blocks.GRASS_BLOCK.defaultBlockState());
						if (tall) {
							d.set(x, 0, z, kind.defaultBlockState());
							d.set(x, 1, z, kind.defaultBlockState().setValue(net.minecraft.world.level.block.DoublePlantBlock.HALF,
								net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
						} else {
							d.set(x, 0, z, kind.defaultBlockState());
						}
					}
				}
			}
		}
		// the fence round it all, the gate at the street; a lamp inside the gate
		List<int[]> line = new ArrayList<>();
		int gateIdx = -1;
		for (int x = 0; x < w; x++) {
			if (x == ex) {
				gateIdx = line.size();
			}
			if (x != lampX) {
				line.add(new int[]{x, 0});
			}
		}
		for (int z = 1; z < dp; z++) {
			line.add(new int[]{w - 1, z});
		}
		for (int x = w - 2; x >= 0; x--) {
			line.add(new int[]{x, dp - 1});
		}
		for (int z = dp - 2; z >= 1; z--) {
			line.add(new int[]{0, z});
		}
		Garden.fence(d, line, gateIdx, Dirs.FRONT);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.max(hall.top, 4) + 2);
	}

	/**
	 * The bee wall: in the bee house's front wall, at standing height, a hive in every other bay between the door and the
	 * corners, its entrance to the garden. Answers the columns that have hives.
	 */
	private static List<Integer> hiveWall(Draft d, Side front, int door) {
		List<Integer> cols = new ArrayList<>();
		for (int i = 1; i < front.last(); i++) {
			int x = front.x(i);
			int z = front.z(i);
			// nothing under a window at the garden's level: the walk along the bee wall stays clear
			int ox = front.outX(i, 1);
			int oz = front.outZ(i, 1);
			var below = d.get(ox, 0, oz);
			if (below != null && !below.isAir() && !d.isSolid(ox, 0, oz) || below != null && below.getBlock() instanceof net.minecraft.world.level.block.StairBlock) {
				d.remove(ox, 0, oz);
			}
			if (Math.abs(x - door) <= 1 || (i & 1) == 0 && front.last() > 4) {
				continue;
			}
			var at = d.get(x, 1, z);
			boolean glazed = at != null && (at.getBlock() instanceof net.minecraft.world.level.block.TransparentBlock
				|| at.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock);
			if (!Openings.isWallBlock(d, x, 1, z) && !glazed) {
				continue;
			}
			try (var s = d.stage(StageKind.OPENINGS)) {
				d.set(x, 1, z, States.facing(Blocks.BEEHIVE, Dirs.FRONT));
			}
			cols.add(x);
		}
		return cols;
	}

	/** The beekeeper's room behind the hives: the bench, the honey chest, the shelf of jars, a lantern. */
	private static void beeHouse(Draft d, Hall hall, int door, List<Integer> hiveCols) {
		int[] in = hall.inner(0);
		Floorplan fp = hall.floor(0).blockHoles(d);
		fp.keep(door, in[1]);
		List<Object[]> honey = List.of(new Object[]{"minecraft:honey_bottle", 12}, new Object[]{"minecraft:honeycomb", 24},
			new Object[]{"minecraft:glass_bottle", 8});
		boolean chest = false;
		boolean bench = false;
		boolean comb = false;
		for (int[] c : fp.along(Dirs.BACK)) {
			if (!chest && fp.take(c[0], c[1], Dirs.FRONT)) {
				Props.store(d, c[0], 0, c[1], Dirs.FRONT, true, honey);
				chest = true;
			} else if (!bench && fp.take(c[0], c[1], Dirs.FRONT)) {
				Props.station(d, Blocks.CRAFTING_TABLE, c[0], 0, c[1], Dirs.FRONT, JobType.BEEKEEPER);
				bench = true;
			} else if (!comb && fp.take(c[0], c[1], null)) {
				comb = true;
				d.inStage(StageKind.INTERIOR, () -> d.set(c[0], 0, c[1], Blocks.HONEYCOMB_BLOCK.defaultBlockState()));
			}
		}
		if (!chest) {
			Props.storeAnywhere(d, fp, honey);
		}
		if (!bench) {
			// the hives' backs are the beekeeper's work: he stands before one inside
			for (int c : hiveCols) {
				if (fp.open(c, in[1]) && d.isEmpty(c, 0, in[1]) && d.isEmpty(c, 1, in[1])) {
					d.poi(PoiType.JOB_SITE, c, 0, in[1], Dirs.FRONT, JobType.BEEKEEPER);
					break;
				}
			}
		}
		Rooms.furnish(d, fp.room(), Rooms.Type.STORAGE, 0, JobType.NONE);
		Lighting.room(d, fp.room());
	}
}
