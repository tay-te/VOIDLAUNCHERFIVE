package dev.voidmc.hearth.build.archetype.work;

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
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.homestead.Adobe;
import dev.voidmc.hearth.build.kit.industry.Fam;
import dev.voidmc.hearth.build.kit.industry.Furnace;
import dev.voidmc.hearth.build.kit.industry.Heap;
import dev.voidmc.hearth.build.kit.industry.Pois;
import dev.voidmc.hearth.build.kit.industry.Works;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.kit.trade.Mills;
import dev.voidmc.hearth.build.kit.trade.Props;
import dev.voidmc.hearth.build.kit.trade.Shed;
import dev.voidmc.hearth.build.kit.trade.Signs;
import dev.voidmc.hearth.build.kit.trade.Yard;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * {@code hearth:work/smelter}: the ironworks, where ore and charcoal become iron. In the forest and stone cultures a
 * blast furnace stands at the back of the yard: a battered tower of rubble with dressed quoins, the tapping arch in its
 * face glowing with the hearth deep inside, a gallery round its shaft on posts and brackets where the charger tips ore
 * and charcoal into the throat's fire, and above it a tall hollow stack banded and crowned, its smoke climbing high over
 * the trees (it reads from across the valley and on the LOD horizon). A trestled timber ramp climbs the side of the yard
 * to the gallery; on the far side the bellows house leans against the furnace, its bellows worked by a water wheel
 * where a stream runs behind, by hand where none does. Before the arch the casting floor of sand under an open shed,
 * pigs of iron cooling in their moulds, the anvil where the bloom is shingled; heaps of ore, charcoal and black slag
 * about the yard. In the dry lands and the tropics the smelters build clay shaft furnaces instead: tapering cones of mud
 * with tuyeres round their feet and goatskin bellows, under a shade of poles and fronds or thatch.
 *
 * <p>Job sites (SMELTER): the tapping arch, the anvil, the charging hole on the gallery, the bellows (tower); each
 * furnace's arch and bellows (clay). Storage: a chest of iron in the casting shed, barrels of ore.
 *
 * <p>Params: {@code furnace} ({@code tower} or {@code clay}; default by the culture), {@code wheel} (boolean: a water
 * wheel at the bellows; default where water lies behind the plot), {@code variant}.
 */
public final class Smelter implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("work/smelter"), ArchetypeCategory.WORKPLACE, Set.of(JobType.SMELTER), 0,
		10, 14, 10, 14, 12, 20, SettlementTier.VILLAGE, Set.of(), Set.of(ArchetypeInfo.SiteFlag.OUTSKIRTS), List.of(), 0, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		Pois pois = new Pois();
		int w = d.frontage();
		int dp = d.depth();
		int ex = Yard.entrance(d);
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		Works.Tradition t = Works.tradition(d);
		boolean clay = d.param("furnace", t == Works.Tradition.EARTH || t == Works.Tradition.THATCH ? "clay" : "tower").equals("clay");
		int top;
		if (clay) {
			top = clay(d, pois, w, dp, ex);
		} else {
			top = tower(d, pois, w, dp, ex);
		}
		pois.commit(d, INFO.jobs().toArray(JobType[]::new), new int[]{w / 2, 0, 1}, new int[]{w / 2, 0, 0});
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, top + 1);
	}

	// ------------------------------------------------------------------------------------------------ the blast furnace

	private static int tower(Draft d, Pois pois, int w, int dp, int ex) {
		boolean chargeLeft = d.chance(0.5);
		Direction charge = chargeLeft ? Dirs.LEFT : Dirs.RIGHT;
		Direction away = charge.getOpposite();
		int x0 = chargeLeft ? 3 : w - 8;
		int x1 = x0 + 4;
		int z0 = dp >= 12 ? dp - 6 : dp - 5;
		int z1 = z0 + 4;
		int cz = z0 + 2;
		int gallery = Math.max(4, Math.min(Math.min(z0 + 1, d.maxHeight() - 7), 8));
		Yard.trodden(d, 0, 0, w - 1, dp - 1, 0);
		Mat stone = Works.rubble(d);
		Mat trim = Works.masonry(d);
		Mat flue = d.mat(Role.CHIMNEY);
		if (!flue.has(Form.SLAB) || Works.isWood(flue.mainOr(Form.FULL, Blocks.STONE))) {
			flue = trim;
		}
		Mat deck = d.mat(Role.FLOOR).has(Form.STAIRS) ? d.mat(Role.FLOOR) : d.mat(Role.FURNITURE);
		if (!Works.isWood(deck.mainOr(Form.FULL, Blocks.STONE))) {
			deck = Works.timber(d);
		}
		Mat frame = d.mat(Role.FRAME);
		RoofForm houseForm = Hall.pick(d, RoofForm.GABLE, RoofForm.HIP, RoofForm.HALF_HIP, RoofForm.PAGODA);
		Furnace.Tower tw = Furnace.tower(d, x0, z0, charge, gallery, d.maxHeight() - 1, Fam.of(stone), Fam.of(trim), Fam.of(flue), deck, frame, houseForm);
		pois.siteIf(d, tw.tapX(), 0, tw.tapZ(), Dirs.BACK, JobType.SMELTER);

		// the ramp up the charging side to the gallery, and the bridge across to it
		int rx = chargeLeft ? x0 - 2 : x1 + 2;
		int start = cz - gallery;
		int[] landing = Furnace.ramp(d, rx, start, Dirs.BACK, gallery, charge, deck, frame);
		int bx = chargeLeft ? x0 - 1 : x1 + 1;
		try (var s = d.stage(StageKind.FLOORS)) {
			d.set(bx, gallery - 1, cz, deck.full());
		}
		try (var s = d.stage(StageKind.FRAME)) {
			for (int y = 0; y < gallery - 1; y++) {
				if (d.isEmpty(bx, y, cz) || d.isAbsent(bx, y, cz)) {
					d.set(bx, y, cz, frame.log(Direction.Axis.Y));
				}
			}
		}
		pois.siteIf(d, tw.chargeX(), gallery, tw.chargeZ(), away, JobType.SMELTER);
		// baskets of ore and charcoal waiting on the landing
		int lbx = landing[0];
		int lbz = landing[1] + 1;
		if (Works.in(d, lbx, lbz) && d.isEmpty(lbx, gallery, lbz)) {
			try (var s = d.stage(StageKind.FLOORS)) {
				d.set(lbx, gallery - 1, lbz, deck.full());
			}
			pois.store(d, lbx, gallery, lbz, Dirs.FRONT, false, List.of(new Object[]{"minecraft:raw_iron", 48}, new Object[]{"minecraft:charcoal", 32}));
			try (var s = d.stage(StageKind.FRAME)) {
				for (int y = 0; y < gallery - 1; y++) {
					if (d.isEmpty(lbx, y, lbz) || d.isAbsent(lbx, y, lbz)) {
						d.set(lbx, y, lbz, frame.log(Direction.Axis.Y));
					}
				}
			}
		}

		// the bellows house leaning on the far side, the bellows to the tuyere
		int room = chargeLeft ? w - 1 - x1 : x0;
		int depth = Math.min(3, room);
		Roofs.Roof lean = null;
		if (depth >= 2) {
			int lx0 = away == Dirs.RIGHT ? x1 + 1 : x0 - depth;
			int lx1 = away == Dirs.RIGHT ? x1 + depth : x0 - 1;
			lean = Shed.pent(d, lx0, z0, lx1, z1, away.getOpposite(), 3, 0.5, null);
			int px = away == Dirs.RIGHT ? lx1 : lx0;
			Shed.posts(d, lean, List.of(new int[]{px, z0}, new int[]{px, z1}), 0);
			// the back boarded against the weather
			Mat boards = d.mat(Role.GABLE).has(Form.FULL) && Works.isWood(d.mat(Role.GABLE).mainOr(Form.FULL, Blocks.STONE)) ? d.mat(Role.GABLE) : deck;
			try (var s = d.stage(StageKind.WALLS)) {
				for (int x = Math.min(lx0, lx1); x <= Math.max(lx0, lx1); x++) {
					int under = lean.covers(x, z1) ? lean.bottomY(x, z1) : 3;
					for (int y = 0; y < under; y++) {
						if (d.isEmpty(x, y, z1) || d.isAbsent(x, y, z1)) {
							d.set(x, y, z1, boards.full());
						}
					}
				}
			}
			int fx = away == Dirs.RIGHT ? x1 + 1 : x0 - 1;
			Furnace.bellows(d, fx, 0, cz - 1, away.getOpposite(), Dirs.BACK, d.mat(Role.FURNITURE));
			int sx = fx + Dirs.dx(away) * (depth >= 3 ? 1 : 0);
			int sz = cz + 1;
			if (depth >= 3) {
				pois.siteIf(d, sx + Dirs.dx(away), 0, cz - 1, away.getOpposite(), JobType.SMELTER);
			} else {
				pois.siteIf(d, fx, 0, cz + 1, Dirs.FRONT, JobType.SMELTER);
			}
			boolean water = false;
			for (int x = 0; x < w && !water; x++) {
				for (int z = dp - 2; z <= dp + 2 && !water; z++) {
					water = d.site().isWater(x, z);
				}
			}
			if (d.param("wheel", water) && depth >= 3 && dp - 1 - z1 >= 0) {
				wheel(d, away == Dirs.RIGHT ? x1 + depth : x0 - depth, cz, away, dp);
			}
			if (lean != null && lean.covers(fx + Dirs.dx(away), z0) && lean.bottomY(fx + Dirs.dx(away), z0) >= 3) {
				Props.hangLantern(d, fx + Dirs.dx(away), lean.bottomY(fx + Dirs.dx(away), z0) - 1, z0);
			}
		}

		// the casting floor before the arch: sand, pigs in their moulds, the anvil, under an open shed
		int sz0 = Math.max(1, z0 - 2);
		int sz1 = z0 - 1;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int x = x0; x <= x1; x++) {
				for (int z = sz0; z <= sz1; z++) {
					d.set(x, -1, z, Blocks.SAND.defaultBlockState());
				}
			}
			for (int x = x0; x <= x1; x++) {
				if (x != tw.tapX() && d.isEmpty(x, 0, sz1 - 1) && (x - x0) % 2 == 0) {
					d.set(x, 0, sz1 - 1, Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE.defaultBlockState());
				}
			}
		}
		// the casting shed: a gable on two posts, its ridge running back to the furnace, open to the street under a tie
		// beam so the glow of the arch shows, its gable boarded above the beam
		Roofs.Roof shed = null;
		int shz0 = Math.max(1, z0 - 3);
		if (z0 - 1 - shz0 >= 1) {
			double pitch = Math.max(0.5, Math.min(1.0, Roofs.Opts.of(d).pitch()));
			Roofs.Field f = Roofs.gable(x0 - 1, shz0 - 1, x1 + 1, z0 - 1, Direction.Axis.Z, Roofs.Profile.straight(pitch));
			try (var s = d.stage(StageKind.ROOF)) {
				shed = Roofs.raster(d, f, 3, x0 - 1, Math.max(0, shz0 - 1), x1 + 1, z0 - 1, d.mat(Role.ROOF), d.mat(Role.ROOF_TRIM),
					Roofs.Opts.of(d).ridge());
			}
			Mat beam = d.mat(Role.BEAM);
			Mat gable = d.mat(Role.GABLE);
			try (var s = d.stage(StageKind.FRAME)) {
				for (int x : new int[]{x0, x1}) {
					for (int y = 0; y <= 2; y++) {
						d.set(x, y, shz0, frame.log(Direction.Axis.Y));
					}
				}
				for (int x = x0; x <= x1; x++) {
					d.set(x, 3, shz0, beam.log(Direction.Axis.X));
				}
			}
			try (var s = d.stage(StageKind.WALLS)) {
				for (int x = x0 + 1; x <= x1 - 1; x++) {
					int under = shed.covers(x, shz0) ? shed.bottomY(x, shz0) : 4;
					for (int y = 4; y < under; y++) {
						d.set(x, y, shz0, gable.full());
					}
				}
			}
			int lx = tw.tapX();
			int lz = z0 - 2;
			if (shed.covers(lx, lz) && shed.bottomY(lx, lz) - 1 >= 3) {
				Props.hangLantern(d, lx, shed.bottomY(lx, lz) - 1, lz);
			}
		}
		// the anvil where the bloom is shingled, the quench trough, the chest of iron
		int ax = x0 + (chargeLeft ? 4 : 0);
		int az = sz0;
		if (d.isEmpty(ax, 0, az) && d.isEmpty(ax, 0, az - 1 >= 0 ? az - 1 : az)) {
			pois.anvil(d, ax, 0, az, chargeLeft ? Dirs.LEFT : Dirs.RIGHT, JobType.SMELTER);
		}
		int chx = x0 + (chargeLeft ? 0 : 4);
		if (d.isEmpty(chx, 0, sz1) && chx != tw.tapX()) {
			pois.store(d, chx, 0, sz1 - 1 >= sz0 ? sz1 - 1 : sz1, chargeLeft ? Dirs.RIGHT : Dirs.LEFT, true,
				List.of(new Object[]{"minecraft:iron_ingot", 32}, new Object[]{"minecraft:iron_nugget", 40}, new Object[]{"minecraft:raw_iron", 16}));
		}
		int tx = chargeLeft ? ax - 1 : ax + 1;
		if (Works.in(d, tx, az) && d.isEmpty(tx, 0, az) && tx != tw.tapX()) {
			Props.trough(d, tx, 0, az, Dirs.BACK, 1);
		}

		// the heaps: ore and charcoal by the ramp's foot, slag behind
		int hx = chargeLeft ? 1 : w - 2;
		heaps(d, pois, hx, start, w, dp, chargeLeft, x0, x1, z0, z1, ex);

		// a fence along the street with the gate, the sign
		List<int[]> gaps = new ArrayList<>();
		for (int x = ex - 1; x <= ex + 1; x++) {
			gaps.add(new int[]{x, 0});
		}
		Yard.enclose(d, 0, 0, w - 1, 0, gaps, Dirs.BACK, Dirs.LEFT, Dirs.RIGHT);
		Signs.post(d, ex + 2 <= w - 1 ? ex + 2 : ex - 2, 0, 0, ex + 2 <= w - 1 ? Dirs.LEFT : Dirs.RIGHT, 3, "smelter", "Ironworks");
		Lighting.post(d, ex - 2 >= 0 ? ex - 2 : ex + 3, 0, 1);
		return tw.top();
	}

	/** Heaps of ore and charcoal by the ramp's foot, slag at the back corner on the far side. */
	private static void heaps(Draft d, Pois pois, int hx, int rampStart, int w, int dp, boolean left, int x0, int x1, int z0, int z1, int ex) {
		Block[] ore = {Blocks.RAW_IRON_BLOCK, Blocks.IRON_ORE, Blocks.GRAVEL, Blocks.RAW_IRON_BLOCK, Blocks.STONE};
		Block[] coal = {Blocks.COAL_BLOCK, Blocks.BLACKSTONE, Blocks.COAL_BLOCK};
		Block[] slag = {Blocks.BLACKSTONE, Blocks.BASALT, Blocks.COBBLED_DEEPSLATE, Blocks.BLACKSTONE, Blocks.TUFF};
		// ore and charcoal at the front corners: the ore on the far side from the ramp, the charcoal by the ramp's foot
		// where there is room before it, else beside the ore
		double ox = left ? w - 2.4 : 1.4;
		double oz = 1.8;
		if (Math.abs(ox - ex) > 2.5) {
			Heap.heap(d, ox, oz, 1.9, 1.6, 1.8, 0, ore, null, 51);
			pois.siteIf(d, (int) Math.round(ox + (left ? -2.4 : 2.4)), 0, (int) Math.round(oz), left ? Dirs.RIGHT : Dirs.LEFT, JobType.SMELTER);
		}
		boolean byRamp = rampStart >= 5;
		double cx = byRamp ? (left ? 1.2 : w - 2.2) : ox;
		double cz = byRamp ? 1.6 : oz + 3.2;
		if (Math.abs(cx - ex) > 2.5 && cz + 1.4 < z0 - 2) {
			Heap.heap(d, cx, cz, 1.6, 1.4, 1.6, 0, coal, (x, y, z) -> Blocks.BLACKSTONE_SLAB.defaultBlockState(), 53);
		}
		// slag behind the bellows house
		double sx = left ? w - 1.6 : 0.6;
		double sz = dp - 1.4;
		if (z1 < dp - 1) {
			Heap.heap(d, sx, sz, 1.6, 1.2, 1.2, 0, slag, (x, y, z) -> Blocks.BLACKSTONE_SLAB.defaultBlockState(), 57);
		}
		// barrels of ore by the casting shed
		int bx = left ? x1 + 1 : x0 - 1;
		int bz = Math.max(1, z0 - 4);
		if (Works.in(d, bx, bz) && d.isEmpty(bx, 0, bz) && Math.abs(bx - ex) > 1) {
			pois.store(d, bx, 0, bz, Dirs.FRONT, false, Works.items("minecraft:raw_iron", 64));
		}
	}

	/** A water wheel outside the bellows house, its axle into it, turning in a stone race fed from the water behind. */
	private static void wheel(Draft d, int x, int cz, Direction out, int dp) {
		int wx = x + Dirs.dx(out);
		if (!Works.in(d, wx, cz)) {
			return;
		}
		Mat stone = Works.rubble(d);
		try (var s = d.stage(StageKind.EARTHWORK)) {
			for (int z = cz - 3; z < dp; z++) {
				if (z < 0) {
					continue;
				}
				d.set(wx, -3, z, stone.full());
				d.set(wx, -2, z, Blocks.WATER.defaultBlockState());
				d.set(wx, -1, z, Blocks.WATER.defaultBlockState());
				d.set(wx, 0, z, Blocks.AIR.defaultBlockState());
			}
		}
		Mat rim = d.mat(Role.FRAME);
		Mat spoke = Hall.woodFence(d);
		Mat paddle = d.mat(Role.FLOOR);
		Mills.waterWheel(d, wx, 1, cz, Direction.Axis.X, 2, 1, rim, spoke, paddle, null);
		d.inStage(StageKind.FRAME, () -> d.set(x, 1, cz, rim.log(Direction.Axis.X)));
	}

	// ------------------------------------------------------------------------------------------------ clay furnaces

	private static int clay(Draft d, Pois pois, int w, int dp, int ex) {
		Yard.trodden(d, 0, 0, w - 1, dp - 1, 0);
		int n = w >= 12 ? 2 : 1;
		int fz = dp - 4;
		int h = Math.max(4, Math.min(7, d.maxHeight() - 3));
		int top = h;
		List<Integer> xs = new ArrayList<>();
		if (n == 2) {
			xs.add(w / 2 - 3);
			xs.add(w / 2 + 3);
		} else {
			xs.add(w / 2 + (d.chance(0.5) ? -2 : 2));
		}
		Mat wood = d.mat(Role.FURNITURE);
		for (int i = 0; i < xs.size(); i++) {
			int cx = xs.get(i);
			int[] arch = Furnace.clay(d, cx, fz, h - i, Dirs.FRONT);
			pois.siteIf(d, arch[0], 0, arch[1], Dirs.BACK, JobType.SMELTER);
			// goatskin bellows at the tuyere on the side
			int side = i == 0 ? -1 : 1;
			int bx = cx + side * 3;
			if (Works.in(d, bx, fz) && d.isEmpty(bx, 0, fz) && d.isEmpty(bx, 0, fz + 1)) {
				Furnace.bellows(d, bx, 0, fz, side < 0 ? Dirs.RIGHT : Dirs.LEFT, Dirs.BACK, wood);
				int sx = bx + side;
				if (Works.in(d, sx, fz)) {
					pois.siteIf(d, sx, 0, fz, side < 0 ? Dirs.RIGHT : Dirs.LEFT, JobType.SMELTER);
				}
			}
			top = Math.max(top, h + 1);
		}
		// the shade over the working floor before the furnaces
		int sz0 = Math.max(1, fz - 5);
		int sz1 = fz - 3;
		if (Works.tradition(d) == Works.Tradition.EARTH) {
			Adobe.ramada(d, 1, sz0, w - 2, sz1, 0);
		} else {
			Roofs.Roof r = Shed.open(d, 1, sz0, w - 2, sz1, 0, 3, RoofForm.HIP, Direction.Axis.X, 1);
			if (r != null) {
				top = Math.max(top, r.ridgeY);
			}
		}
		int lx = w / 2;
		Props.hangLantern(d, lx, 2, sz1);
		// a bench with the chest of blooms under the shade
		pois.store(d, 2, 0, sz0 + 1, Dirs.RIGHT, true, List.of(new Object[]{"minecraft:iron_ingot", 16}, new Object[]{"minecraft:raw_iron", 24}));
		pois.anvil(d, w - 3, 0, sz0 + 1, Dirs.LEFT, JobType.SMELTER);
		// heaps: ore, charcoal, slag at the corners
		Block[] ore = {Blocks.RAW_IRON_BLOCK, Blocks.IRON_ORE, Blocks.GRAVEL, Blocks.RAW_IRON_BLOCK, Works.dry(d) ? Blocks.RED_SANDSTONE : Blocks.STONE};
		Block[] coal = {Blocks.COAL_BLOCK, Blocks.BLACKSTONE, Blocks.COAL_BLOCK};
		Block[] slag = {Blocks.BLACKSTONE, Blocks.BASALT, Blocks.COBBLED_DEEPSLATE, Blocks.BLACKSTONE};
		if (Math.abs(1.5 - ex) > 2) {
			Heap.heap(d, 1.5, dp - 1.5, 1.4, 1.4, 1.5, 0, ore, null, 61);
		}
		Heap.heap(d, w - 2.5, dp - 1.5, 1.4, 1.4, 1.3, 0, coal, (x, y, z) -> Blocks.BLACKSTONE_SLAB.defaultBlockState(), 63);
		if (Math.abs(w - 2.0 - ex) > 2) {
			Heap.heap(d, w - 2.0, 1.2, 1.2, 1.0, 1.0, 0, slag, null, 67);
		}
		List<int[]> gaps = new ArrayList<>();
		for (int x = ex - 1; x <= ex + 1; x++) {
			gaps.add(new int[]{x, 0});
		}
		Yard.enclose(d, 0, 0, w - 1, dp - 1, gaps);
		Signs.post(d, ex + 2 <= w - 2 ? ex + 2 : ex - 2, 0, 1, ex + 2 <= w - 2 ? Dirs.LEFT : Dirs.RIGHT, 3, "smelter", "Ironworks");
		return top;
	}
}
