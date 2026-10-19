package dev.voidmc.hearth.build.archetype.work;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.BlockEntities;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.kit.craft.Hatch;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * {@code hearth:work/builders_yard}: the first workplace every settlement raises, where its builders work, and it
 * shows. A fenced yard entered under a timber gate with the builders' sign hanging from its beam; at the back an open
 * work shed under a gable roof, benches and the stone saw beneath it, the master builder's office at one end — two
 * storeys, a turning stair inside (a ladder through a hatch where it will not fit) up to the room where the plans are
 * kept and the yard is watched from the windows. In the yard:
 * logs on bearers, sawn boards in stickered stacks, dressed stone on pallets, scaffolding stacked ready, sawhorses, a
 * loaded cart, a corner of wall going up in its scaffold to try a new stone, and a timber crane whose jib swings a load
 * on a chain over the lane.
 *
 * <p>Job sites (BUILDER): the benches, the stone saw (MASON), the sawhorses, the crane's foot, the trial wall.
 * Storage: the shed's chests and the stacks. A LECTERN with the plans.
 *
 * <p>Params: {@code roof}, {@code variant}.
 */
public final class BuildersYard implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("work/builders_yard"), ArchetypeCategory.WORKPLACE, Set.of(JobType.BUILDER), 0,
		11, 17, 11, 17, 9, 14, SettlementTier.CAMP, Set.of(), Set.of(), List.of(), 3, 1.0);

	private static final RoofForm[] FORMS = {RoofForm.GABLE, RoofForm.HALF_HIP, RoofForm.HIP, RoofForm.PAGODA, RoofForm.SALTBOX, RoofForm.FLAT};

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int ex = Yard.entrance(d);
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		Yard.trodden(d, 1, 1, w - 2, dp - 2, 0);
		RoofForm form = Hall.pick(d, FORMS);

		// the back: the office at one end (two storeys), the open work shed along the rest
		int shedDepth = dp >= 14 ? 5 : 4;
		int sz0 = dp - 1 - shedDepth;
		int sz1 = dp - 1;
		boolean officeLeft = d.chance(0.5);
		int ow = w >= 15 ? 5 : 4;
		int ox0 = officeLeft ? 0 : w - ow;
		int ox1 = ox0 + ow - 1;
		int sx0 = officeLeft ? ox1 + 1 : 0;
		int sx1 = officeLeft ? w - 1 : ox0 - 1;
		Hall office = office(d, ox0, sz0 - 1, ox1, sz1, form, officeLeft);
		shed(d, sx0, sz0, sx1, sz1, form, officeLeft);

		// the gate: two posts and a beam over the lane, the sign hanging from it
		int lane = ex;
		gate(d, w, lane);
		// the yard: timber on one side, stone and boards on the other, the lane up the middle to the shed
		int tSide = officeLeft ? w - 4 : 1;
		int sSide = officeLeft ? 1 : w - 4;
		// the stacks keep two rows clear before the shed and the office door
		int len = Math.max(2, Math.min(4, sz0 - 5));
		Props.logPile(d, tSide, 0, 2, Direction.Axis.Z, len, 3, 3, d.mat(Role.FRAME));
		int px = officeLeft ? tSide - 1 : tSide + 3;
		if (d.isEmpty(px, 0, 3) && d.isEmpty(px, 1, 3)) {
			d.poi(PoiType.STORAGE, px, 0, 3, officeLeft ? Dirs.RIGHT : Dirs.LEFT);
		}
		if (sz0 - 2 >= 4) {
			Props.boardStack(d, sSide, 0, 2, Direction.Axis.Z, 2, 2, d.mat(Role.FLOOR));
			Props.boardStack(d, sSide + 1, 0, 2, Direction.Axis.Z, 2, 3, d.mat(Role.FLOOR));
			if (sz0 - 5 >= 4) {
				Props.stonePallet(d, sSide + (officeLeft ? 0 : 1), 0, 5, d.mat(Role.PLINTH), d.mat(Role.FLOOR));
			}
		}
		// sawhorses with a log across, worked from the lane side
		int hz = Math.min(sz0 - 2, 2 + len + 1);
		if (hz >= 3 && hz < sz0 - 1) {
			int hx = officeLeft ? w - 4 : 1;
			if (d.isEmpty(hx, 0, hz) && d.isEmpty(hx + 1, 0, hz) && d.isEmpty(hx + 2, 0, hz)) {
				Props.sawhorses(d, hx, 0, hz, Direction.Axis.X, true);
				// the joiner's bench at the lane end of the trestles, where the sawn timber is marked and cut to length
				int bx = officeLeft ? hx - 1 : hx + 3;
				if (bx >= 1 && bx <= w - 2 && Math.abs(bx - lane) > 1 && d.isEmpty(bx, 0, hz) && d.isEmpty(bx, 1, hz) && d.isEmpty(bx, 0, hz - 1)
					&& d.isEmpty(bx, 1, hz - 1)) {
					Props.station(d, Blocks.CRAFTING_TABLE, bx, 0, hz, Dirs.FRONT, JobType.BUILDER);
				} else {
					Props.site(d, hx + 1, 0, hz - 1, Dirs.BACK, JobType.BUILDER);
				}
			}
		}
		// the crane before the shed, its jib over the lane, a load of stone on its chain (two rows out, so its mast stands
		// clear of the shed's eave)
		int craneX = officeLeft ? w - 3 : 2;
		int cz = sz0 - 2;
		if (Lifts.crane(d, craneX, cz, 0, 10, craneX > lane ? Dirs.LEFT : Dirs.RIGHT, Math.max(3, Math.min(5, Math.abs(lane - craneX))),
			d.mat(Role.PLINTH).full().at(lane, 2, cz)) != Integer.MIN_VALUE) {
			// the rigger's bench by the crane's foot, its tackle hung over it
			int rx = craneX + (craneX > lane ? 1 : -1);
			if (rx >= 1 && rx <= w - 2 && d.isEmpty(rx, 0, cz) && d.isEmpty(rx, 1, cz) && d.isEmpty(rx, 0, cz - 1) && d.isEmpty(rx, 1, cz - 1)) {
				Props.station(d, Blocks.CRAFTING_TABLE, rx, 0, cz, Dirs.FRONT, JobType.BUILDER);
			} else {
				Props.site(d, rx, 0, cz - 1, Dirs.BACK, JobType.BUILDER);
			}
		}
		// scaffolding stacked ready, a cart loaded with boards by the gate
		int scx = craneX + (craneX > lane ? -2 : 2);
		if (Math.abs(scx - lane) > 1 && d.isEmpty(scx, 0, cz) && d.isEmpty(scx, 0, cz - 1)) {
			scaffoldPile(d, scx, cz);
		}
		int cartX = officeLeft ? lane - 3 : lane + 3;
		if (cartX - 1 >= 1 && cartX + 1 <= w - 2 && d.isEmpty(cartX, 0, 2) && d.isEmpty(cartX - 1, 0, 2) && d.isEmpty(cartX + 1, 0, 2)
			&& d.isEmpty(cartX, 0, 1)) {
			Props.cart(d, cartX, 0, 2, Dirs.FRONT, d.mat(Role.FLOOR).slab(net.minecraft.world.level.block.state.properties.SlabType.BOTTOM).at(cartX, 1, 2));
		}
		// a corner of trial wall going up in its scaffold
		trialWall(d, w, sz0, lane, officeLeft);
		// the lane: a cart track from the gate to the shed
		List<int[]> track = new ArrayList<>();
		for (int z = 0; z < sz0; z++) {
			track.add(new int[]{lane, z});
		}
		dev.voidmc.hearth.build.kit.Garden.path(d, track, 0);
		fence(d, w, dp, lane);
		d.poi(PoiType.GATE, lane, 0, 0, Dirs.BACK);
		// a rail wherever the levelled yard ends over a drop
		dev.voidmc.hearth.build.kit.craft.Rails.yard(d);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.max(office.top, 11) + 1);
		dev.voidmc.hearth.build.kit.refine.Voids.finish(d);
	}

	/**
	 * The master builder's office: two storeys at the back corner, a door to the yard, a turning stair (or a ladder) up
	 * to the plans on a lectern upstairs, the chest of tallies below.
	 */
	private static Hall office(Draft d, int x0, int z0, int x1, int z1, RoofForm form, boolean left) {
		int sh = 4;
		int[] heights = Hall.budget(d, x0, z0, x1, z1, 0, new int[]{sh, sh}, false, form, 1);
		Hall h = Hall.of(d, x0, z0, x1, z1, 0, heights, false, false);
		int[] in = h.inner(0);
		int door = left ? in[2] : in[0];
		h.facade(door, 1, 1, 0.5).ground(false).shell().openings(true);
		door = h.door != null ? h.door : door;
		// a turning stair against the wall away from the door, to the plans upstairs
		int[] well = h.storeys() >= 2 ? h.switchback(0, left ? Dirs.LEFT : Dirs.RIGHT) : null;
		h.roof(form, Direction.Axis.Z, 1, null);
		if (h.roof == null) {
			h.crown();
		}
		// where the turning stair does not fit, a ladder up through a hatch against the back or the side wall
		int[] ladder = h.storeys() >= 2 && well == null ? Hatch.ladder(d, h, 1, null, Dirs.BACK, left ? Dirs.LEFT : Dirs.RIGHT) : null;
		if (h.storeys() >= 2 && well == null && ladder == null) {
			h.openLoft(1);
		}
		dev.voidmc.hearth.build.kit.craft.Rails.well(d, h, 0, well);
		if (well != null || ladder != null) {
			// upstairs: the plans on a lectern, a balcony over the yard
			int[] up = h.inner(1);
			int y = h.floorY(1);
			int lx = left ? up[2] : up[0];
			int lz = up[3];
			if ((well == null || lz > well[3]) && (ladder == null || Math.abs(lx - ladder[0]) + Math.abs(lz - ladder[1]) > 1) && d.isEmpty(lx, y, lz)
				&& d.isEmpty(lx, y, lz - 1) && d.isEmpty(lx, y + 1, lz - 1)) {
				d.inStage(StageKind.INTERIOR, () -> d.set(lx, y, lz, States.facing(Blocks.LECTERN, Dirs.FRONT), BlockEntities.lectern("Plans", "The Master Builder",
					List.of("The settlement's plans: what is to be built next, in what order, with what stone and timber."))));
				d.poi(PoiType.LECTERN, lx, y, lz - 1, Dirs.BACK);
			}
			// (no door out onto the one-deep balcony over the yard: its rail stood in the doorway; the yard is watched from
			// the windows)
			Props.hangLantern(d, (up[0] + up[2]) / 2, y + 1, (up[1] + up[3]) / 2);
		}
		// below: the chest of the settlement's tallies and the plans' copy, kept clear of the stair
		dev.voidmc.hearth.build.kit.trade.Floorplan fp = h.floor(0).blockHoles(d);
		fp.keep(door, in[1]);
		if (well != null) {
			// the well blocked but for its foot: a chest under the flight could not open
			for (int x = well[4]; x <= well[6]; x++) {
				for (int z = well[5]; z <= well[7]; z++) {
					if (x != well[0] || z != well[1]) {
						fp.block(x, z);
					}
				}
			}
			fp.keep(well[0], well[1]);
		}
		if (ladder != null) {
			fp.keep(ladder[0], ladder[1]);
		}
		boolean chest = false;
		for (Direction wall : new Direction[]{Dirs.BACK, left ? Dirs.RIGHT : Dirs.LEFT, Dirs.FRONT}) {
			for (int[] c : fp.along(wall)) {
				if (!chest && fp.take(c[0], c[1], wall.getOpposite())) {
					Props.store(d, c[0], 0, c[1], wall.getOpposite(), true, List.of(new Object[]{"minecraft:paper", 24},
						new Object[]{"minecraft:oak_planks", 32}, new Object[]{"minecraft:stone_bricks", 32}));
					chest = true;
				}
			}
		}
		if (well == null) {
			for (int[] c : fp.along(Dirs.BACK)) {
				if (fp.take(c[0], c[1], Dirs.FRONT)) {
					d.inStage(StageKind.INTERIOR, () -> d.set(c[0], 0, c[1], States.facing(Blocks.LECTERN, Dirs.FRONT),
						BlockEntities.lectern("Plans", "The Master Builder", List.of("The settlement's plans."))));
					d.poi(PoiType.LECTERN, c[0], 0, c[1] - 1, Dirs.BACK);
					break;
				}
			}
		}
		return h;
	}

	/** The work shed: open to the yard on posts, boarded behind, a gable roof; benches, the saw, chests, tools beneath. */
	private static void shed(Draft d, int x0, int z0, int x1, int z1, RoofForm form, boolean officeLeft) {
		Groundwork.footings(d, x0, z0, x1, z1, -1);
		Yard.pave(d, x0 + 1, z0 + 1, x1 - 1, z1 - 1, 0);
		RoofForm f = form == RoofForm.FLAT ? RoofForm.SHED : form == RoofForm.HIP ? RoofForm.HALF_HIP : form;
		// where the sun is strong the builders work under a reed shade before a back wall of their own masonry (the south,
		// the dry lands, the tropics, the karst); elsewhere in the open shed
		boolean shade = dev.voidmc.hearth.build.kit.craft.Craft.southern(d)
			|| dev.voidmc.hearth.build.kit.industry.Works.culture(d) == dev.voidmc.hearth.api.Culture.TROPICAL
			|| dev.voidmc.hearth.build.kit.industry.Works.culture(d) == dev.voidmc.hearth.api.Culture.KARST;
		Roofs.Roof roof = null;
		if (shade) {
			Mat back = d.mat(Role.FOUNDATION);
			try (var s = d.stage(StageKind.WALLS)) {
				for (int x = x0; x <= x1; x++) {
					for (int y = 0; y <= 2; y++) {
						d.set(x, y, z1, back.full());
					}
				}
			}
			dev.voidmc.hearth.build.kit.homestead.Adobe.ramada(d, x0, z0, x1, z1 - 1, 0);
		} else {
			roof = Shed.open(d, x0, z0, x1, z1, 0, 4, f, Direction.Axis.X, 1, Dirs.BACK, officeLeft ? Dirs.RIGHT : Dirs.LEFT);
			d.carve(x0 + 1, 0, z0 + 1, x1 - 1, 3, z1 - 1);
			d.body(x0, z0, x1, z1);
			d.eave(4);
		}
		int row = z1 - 1;
		String[] stations = {"chest", "bench", "saw", "rack", "bench", "chest2", "barrels"};
		int x = officeLeft ? x0 + 1 : x1 - 1;
		int step = officeLeft ? 1 : -1;
		for (String st : stations) {
			if (x <= x0 || x >= x1) {
				break;
			}
			switch (st) {
				case "chest" -> Props.store(d, x, 0, row, Dirs.FRONT, true, List.of(new Object[]{"minecraft:iron_axe", 1}, new Object[]{"minecraft:iron_shovel", 1},
					new Object[]{"minecraft:oak_planks", 32}, new Object[]{"minecraft:cobblestone", 32}));
				case "chest2" -> Props.store(d, x, 0, row, Dirs.FRONT, true, List.of(new Object[]{"minecraft:stone_bricks", 48},
					new Object[]{"minecraft:glass_pane", 16}));
				case "bench" -> Furniture.workstation(d, JobType.BUILDER, x, 0, row, Dirs.FRONT);
				case "saw" -> Furniture.workstation(d, JobType.MASON, x, 0, row, Dirs.FRONT);
				case "rack" -> {
					Props.grindstone(d, x, 0, row, Dirs.FRONT, JobType.BUILDER);
					Props.toolRack(d, x, 2, row, Dirs.FRONT, List.of(new Object[]{"minecraft:iron_axe", 1, 0}, new Object[]{"minecraft:iron_shovel", 1, 1},
						new Object[]{"minecraft:shears", 1, 2}));
				}
				default -> Props.barrelRack(d, x, 0, row, Dirs.RIGHT, 1, 2, Dirs.FRONT);
			}
			x += step;
		}
		if (roof != null) {
			int lx = (x0 + x1) / 2;
			int lz = (z0 + z1) / 2;
			if (roof.covers(lx, lz)) {
				Props.hangLantern(d, lx, roof.bottomY(lx, lz) - 1, lz);
			}
		}
	}

	/** Two posts and a beam over the gate, the sign hanging from it, lanterns on the posts. */
	private static void gate(Draft d, int w, int lane) {
		Mat post = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		int a = lane - 2;
		int b = lane + 2;
		if (a < 0 || b >= w) {
			return;
		}
		try (var s = d.stage(StageKind.FRAME)) {
			for (int x : new int[]{a, b}) {
				for (int y = 0; y <= 3; y++) {
					d.set(x, y, 0, post.log(Direction.Axis.Y));
				}
			}
			for (int x = a; x <= b; x++) {
				d.set(x, 4, 0, beam.log(Direction.Axis.X));
			}
			// a little gabled cap over the beam
			Mat roof = d.mat(Role.ROOF);
			if (5 < d.maxHeight()) {
				d.set(a, 5, 0, roof.stairsOrSlab(Dirs.RIGHT, Half.BOTTOM));
				d.set(b, 5, 0, roof.stairsOrSlab(Dirs.LEFT, Half.BOTTOM));
				for (int x = a + 1; x < b; x++) {
					d.set(x, 5, 0, roof.slab(net.minecraft.world.level.block.state.properties.SlabType.BOTTOM));
				}
			}
		}
		Signs.hang(d, lane, 3, 0, Dirs.FRONT, "builders_yard", "Builders");
		Props.hangLantern(d, a + 1, 3, 0);
		Props.hangLantern(d, b - 1, 3, 0);
	}

	/** A corner of wall in courses of stone with a timber post, scaffolding against it: the builders trying a stone. */
	private static void trialWall(Draft d, int w, int sz0, int lane, boolean officeLeft) {
		int x0 = officeLeft ? 1 : w - 4;
		int z = sz0 - 3;
		if (z < 5 || Math.abs(x0 - lane) <= 1 || Math.abs(x0 + 2 - lane) <= 1) {
			return;
		}
		for (int x = x0; x <= x0 + 2; x++) {
			if (!d.isEmpty(x, 0, z) || !d.isEmpty(x, 0, z - 1)) {
				return;
			}
		}
		Mat stone = d.mat(Role.PLINTH);
		Mat wall = d.mat(Role.WALL);
		Mat post = d.mat(Role.FRAME);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int x = x0; x <= x0 + 2; x++) {
				d.set(x, -1, z, d.mat(Role.FOUNDATION).full());
				int h = x == x0 + 2 ? 1 : 2;
				for (int y = 0; y < h; y++) {
					d.set(x, y, z, y == 0 ? stone.full() : wall.full());
				}
			}
			d.set(x0, 2, z, post.log(Direction.Axis.Y));
			BlockState scaf = Blocks.SCAFFOLDING.defaultBlockState().setValue(ScaffoldingBlock.DISTANCE, 0).setValue(ScaffoldingBlock.BOTTOM, false);
			d.set(x0 + 1, 0, z - 1, scaf);
			d.set(x0 + 1, 1, z - 1, scaf);
		}
		// the banker where the stone for the trial is dressed, beside the corner going up
		int bx = x0 + 2;
		if (d.isEmpty(bx, 0, z - 1) && d.isEmpty(bx, 1, z - 1) && d.isEmpty(bx, 0, z - 2) && d.isEmpty(bx, 1, z - 2)) {
			Props.station(d, Blocks.STONECUTTER, bx, 0, z - 1, Dirs.FRONT, JobType.BUILDER);
		} else {
			Props.site(d, x0 + 2, 0, z - 1, Dirs.BACK, JobType.BUILDER);
		}
	}

	/** Scaffolding stacked ready to go up (two high: a taller stack was a ladder onto the shed's roof). */
	private static void scaffoldPile(Draft d, int x, int z) {
		BlockState scaf = Blocks.SCAFFOLDING.defaultBlockState().setValue(ScaffoldingBlock.DISTANCE, 0).setValue(ScaffoldingBlock.BOTTOM, false);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int k = 0; k < 2; k++) {
				d.set(x, k, z, scaf);
			}
			d.set(x, 0, z - 1, scaf);
		}
	}

	/** The fence (or wall) round the yard, open at the gate. */
	private static void fence(Draft d, int w, int dp, int lane) {
		List<int[]> gaps = new ArrayList<>();
		for (int x = lane - 1; x <= lane + 1; x++) {
			gaps.add(new int[]{x, 0});
		}
		Yard.enclose(d, 0, 0, w - 1, dp - 1, gaps);
	}
}
