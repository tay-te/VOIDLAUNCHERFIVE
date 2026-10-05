package dev.voidmc.hearth.build.archetype.civic;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.BlockEntities;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Facade;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.civic.Adobe;
import dev.voidmc.hearth.build.kit.civic.Fam;
import dev.voidmc.hearth.build.kit.civic.Grounds;
import dev.voidmc.hearth.build.kit.civic.Halls;
import dev.voidmc.hearth.build.kit.civic.Portico;
import dev.voidmc.hearth.build.kit.civic.Storeys;
import dev.voidmc.hearth.build.kit.civic.Towers;
import dev.voidmc.hearth.build.kit.civic.Tradition;
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
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:civic/school}: the schoolhouse. One lofty classroom under the culture's roof with tall windows down
 * both sides; rows of desks face the teacher's desk, lectern and blackboard at the far end; pegs and shelves for coats by
 * the door, which has a porch (or, in the south, a colonnade along the front to wait in the shade); a little bell turret
 * on the roof calls the children in. Beside it a fenced schoolyard with a shade tree, a bench, a bell on a post and
 * chalked squares on its path.
 *
 * <p>POIs: SEATs at the desks, the LECTERN, TABLE, STORAGE, DOOR, a MEETING_POINT in the yard.
 *
 * <p>Params: {@code yard_right} (boolean), {@code variant} (int).
 */
public final class School implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("civic/school"), ArchetypeCategory.CIVIC, Set.of(), 0,
		11, 15, 11, 15, 9, 15, SettlementTier.TOWN, Set.of(), Set.of(), List.of(), 1, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int tier = d.tier();
		int maxH = d.maxHeight();
		Habits h = d.habits();
		Tradition t = Tradition.of(d);
		boolean raised = t.raised();
		boolean yardRight = d.param("yard_right", d.chance(0.5));
		int bw = Math.min(w - 1, w >= 13 ? 9 : w - 3);
		int x0 = yardRight ? 0 : w - bw;
		int x1 = x0 + bw - 1;
		int yx0 = yardRight ? x1 + 1 : 0;
		int yx1 = yardRight ? w - 1 : x0 - 1;
		int cx = (x0 + x1) / 2;
		int zF = raised ? 3 : 2;
		int z0 = zF;
		int z1 = dp - 1;
		// a lofty schoolroom where the plot's height allows: tall windows let the light far in
		int base = raised ? Grounds.stiltBase(d, x0, z0, x1, z1) : 0;
		int hh = maxH - base >= 13 ? 6 : 5;
		Body body = new Body(x0, z0, x1, z1, base, new int[]{hh}, false, false);
		d.body(x0, z0, x1, z1);
		int plate = body.plateY();
		d.eave(plate);

		Storeys.ground(d, body, raised);
		Grounds.forecourt(d, 0, w - 1, z0 - 1, false);
		Facade fac = Facade.plan(d, body, cx, 1, h.windows().tall() ? 2 : 1, 1.0);
		Storeys.walls(d, body, fac, 0, 0, null, false);
		// big windows down both sides and at the back, the door with its hood at the front
		Habits.Windows hw = d.habits().windows();
		Openings.WindowStyle style = hw.lattice() ? Openings.WindowStyle.LATTICE : Openings.WindowStyle.DRESSED;
		for (Side s : body.sides(0)) {
			for (Facade.Opening o : fac.openings(s.out())) {
				if (o.kind() == Facade.Kind.WINDOW && s.contains(o.at()) && s.contains(o.at() + o.width() - 1)) {
					Adobe.window(d, s, o.at(), base + 1, o.width(), hh - 3, style);
				}
			}
		}
		Side front = body.side(0, Dirs.FRONT);
		Integer door = fac.openings(Dirs.FRONT).stream().filter(o -> o.kind() == Facade.Kind.DOOR).map(Facade.Opening::at).findFirst().orElse(cx);
		// the way in: a colonnade along the front in the south (a shaded porch to wait in), a porched door elsewhere
		boolean colonnade = (t == Tradition.ADOBE || t == Tradition.MEDITERRANEAN || t == Tradition.STEPPE) && !raised && z0 - 1 >= 1 && bw >= 7;
		if (colonnade) {
			Openings.door(d, front, door, base, 1, new Openings.DoorOpts(true, true, true));
			Portico.build(d, t, x0, x1, z0 - 1, z0 - 1, base, 3, true);
		} else if (!raised && z0 - 1 >= 1) {
			Halls.portal(d, front, door, base, t, tier);
		} else {
			Openings.door(d, front, door, base, 1, new Openings.DoorOpts(true, true, true));
		}

		// --- the roof and its bell turret
		RoofForm form = t == Tradition.ADOBE ? RoofForm.FLAT : t == Tradition.KARST ? RoofForm.PAGODA
			: h.roof().pick(d.random(), RoofForm.GABLE, RoofForm.HIP, RoofForm.HALF_HIP);
		Storeys.Top top = Storeys.roof(d, body, form, Direction.Axis.Z, Math.max(1.0, h.roof().pitch()), 5);
		int skyline = top.ridge();
		if (top.roof() != null) {
			int apex = top.roof().surfaceY(cx, z0);
			if (form == RoofForm.GABLE && apex != Integer.MIN_VALUE) {
				skyline = Math.max(skyline, Towers.bellCote(d, front, front.index(cx), apex, maxH));
			} else {
				skyline = Math.max(skyline, ChapelWestern.turret(d, t, cx, z0 + 1, plate, top.roof().ridgeY, maxH));
			}
		} else {
			skyline = Math.max(skyline, Towers.bellWall(d, front, front.index(cx), top.deck() + 1, 1, maxH));
		}

		// --- the classroom: desks in rows facing the teacher, the blackboard on the back wall
		int ix0 = x0 + 1;
		int ix1 = x1 - 1;
		int iz0 = z0 + 1;
		int iz1 = z1 - 1;
		Mat wood = d.mat(Role.FURNITURE);
		// the blackboard: dark boards framed in wood on the back wall
		Fam board = Fam.of("minecraft:black_concrete|minecraft:coal_block", StageKind.INTERIOR);
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int x = cx - 1; x <= cx + 1; x++) {
				if (Openings.isWallBlock(d, x, base + 1, z1)) {
					d.set(x, base + 1, z1, board.full());
					d.set(x, base + 2, z1, board.full());
				}
			}
		}
		// the teacher's desk and lectern before it
		int tz = iz1 - 1;
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(cx - 1, base, tz, wood.slab(SlabType.TOP));
			d.set(cx, base, tz, wood.slab(SlabType.TOP));
			d.set(cx - 1, base + 1, tz, Blocks.CANDLE.defaultBlockState());
		}
		d.poi(PoiType.TABLE, cx - 1, base, tz - 1, Dirs.BACK);
		if (d.isEmpty(cx + 1, base, tz)) {
			d.inStage(StageKind.INTERIOR, () -> d.set(cx + 1, base, tz, States.facing(Blocks.LECTERN, Dirs.FRONT),
				BlockEntities.lectern("Letters and Numbers", "The Schoolmaster", List.of("A is for apple."))));
			d.poi(PoiType.LECTERN, cx + 1, base, tz - 1, Dirs.BACK);
		}
		// pupils' desks: a seat and a desk top, in pairs either side of an aisle
		for (int z = iz0 + 2; z <= tz - 2; z += 2) {
			for (int x = ix0; x <= ix1; x++) {
				if (x == cx || x == door) {
					continue;
				}
				if (!d.isEmpty(x, base, z) || !d.isEmpty(x, base, z + 1)) {
					continue;
				}
				int fx = x;
				int fz = z;
				try (var s = d.stage(StageKind.INTERIOR)) {
					d.set(fx, base, fz, wood.stairsOrSlab(Dirs.FRONT, Half.BOTTOM));
					d.set(fx, base, fz + 1, wood.has(Form.TRAPDOOR) ? wood.trapdoor(Dirs.BACK, Half.TOP, false) : wood.slab(SlabType.TOP));
				}
				d.poi(PoiType.SEAT, x, base, z, Dirs.BACK);
			}
		}
		// coats and books by the door
		for (int x : new int[]{ix0, ix1}) {
			if (d.isEmpty(x, base, iz0) && Math.abs(x - door) > 1) {
				Furniture.chest(d, x, base, iz0, x == ix0 ? Dirs.RIGHT : Dirs.LEFT, List.of(new Object[]{"minecraft:book", 12}, new Object[]{"minecraft:paper", 16}));
				Furniture.shelf(d, x, base + 1, iz0, x == ix0 ? Dirs.RIGHT : Dirs.LEFT, "study");
				break;
			}
		}
		for (int z = iz0 + 2; z <= iz1 - 1; z += 3) {
			Halls.lanternUnder(d, cx, base, z, 2);
		}

		// --- the schoolyard: fenced, a tree, a bench, the bell on a post, chalk squares
		if (yx1 - yx0 >= 2) {
			int gy = 0;
			Groundwork.level(d, yx0, 1, yx1, dp - 1, gy);
			List<int[]> fence = new ArrayList<>();
			for (int z = 1; z <= dp - 1; z++) {
				fence.add(new int[]{yardRight ? yx1 : yx0, z});
			}
			for (int x = yx0; x <= yx1; x++) {
				fence.add(new int[]{x, dp - 1});
			}
			Garden.fence(d, fence, -1, Dirs.FRONT);
			List<int[]> frontRun = new ArrayList<>();
			for (int x = yx0; x <= yx1; x++) {
				frontRun.add(new int[]{x, 1});
			}
			Garden.fence(d, frontRun, frontRun.size() / 2, Dirs.FRONT);
			int tx = (yx0 + yx1) / 2;
			int tzz = Math.max(3, dp - 4);
			if (d.isEmpty(tx, gy, tzz) && Groundwork.surface(d, tx, tzz) == gy) {
				Garden.tree(d, tx, gy, tzz, 3);
			}
			int bx = yardRight ? yx0 : yx1;
			if (d.isEmpty(bx, gy, 3)) {
				Furniture.chair(d, bx, gy, 3, yardRight ? Dirs.LEFT : Dirs.RIGHT);
			}
			// the bell on a post
			int px = yardRight ? yx0 : yx1;
			int pz = Math.min(dp - 3, 5);
			if (d.isEmpty(px, gy, pz) && Groundwork.surface(d, px, pz) == gy) {
				try (var s = d.stage(StageKind.EXTERIOR)) {
					d.set(px, gy, pz, Halls.post(d));
					d.set(px, gy + 1, pz, Halls.post(d));
					d.set(px, gy + 2, pz, Blocks.BELL.defaultBlockState()
						.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.BELL_ATTACHMENT,
							net.minecraft.world.level.block.state.properties.BellAttachType.FLOOR)
						.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, Dirs.FRONT));
				}
			}
			// chalk squares on the yard's path
			try (var s = d.stage(StageKind.EXTERIOR)) {
				for (int z = 2; z <= Math.min(dp - 2, 7); z++) {
					int x = (yx0 + yx1) / 2 + (z % 3 == 0 ? 1 : 0);
					if (x > yx0 && x < yx1 && d.isEmpty(x, gy, z) && Groundwork.surface(d, x, z) == gy) {
						d.set(x, gy - 1, z, (Noise.unit(d.seed(), x, 0, z) < 0.5 ? Blocks.SMOOTH_STONE : Blocks.CALCITE).defaultBlockState());
					}
				}
			}
			d.poi(PoiType.MEETING_POINT, (yx0 + yx1) / 2, gy, 3, Dirs.FRONT);
		}
		Grounds.approach(d, door, base, z0 - 1);
		if (h.snow()) {
			Garden.snowBanks(d, x0, z0, x1, z1, base, door);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, skyline + 2);
		dev.voidmc.hearth.build.kit.refine.Voids.finish(d);
	}
}
