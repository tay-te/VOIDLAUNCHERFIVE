package dev.voidmc.hearth.build.kit.craft;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.terrain.Groundwork;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Walls;
import dev.voidmc.hearth.build.kit.homestead.Bohemia;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shared choices of the craft workshops: which cultures build the southern way (round sunk vats, terraces, cloth
 * awnings), the steep roofs of the Bohemian and northern trades, kerbs and paths.
 */
public final class Craft {
	private Craft() {
	}

	/** The cultures of the sun: flat roofs or low ones, round stone vats, work done on terraces and in courtyards. */
	public static boolean southern(Draft d) {
		Culture c = d.style().culture();
		return c == Culture.DESERT || c == Culture.MESA || c == Culture.STEPPE || c == Culture.MEDITERRANEAN || Hall.flatCulture(d);
	}

	/** The cultures that steepen a workshop's roof over a loft: Bohemia, the north and the mountains. */
	public static boolean steepRoofs(Draft d) {
		Culture c = d.style().culture();
		return Bohemia.is(d) || c == Culture.NORTHERN || c == Culture.ALPINE || c == Culture.TUNDRA || c == Culture.MOORLAND || c == Culture.MARITIME;
	}

	/**
	 * Roofs a hall in {@code form} with the ridge along {@code axis}, as steep as the culture likes its workshop roofs
	 * (Bohemian and northern gables two to one, in clean courses, where the height takes it; else the culture's pitch)
	 * while the ridge stays under the plot's height less {@code reserve}; otherwise exactly as {@link Hall#roof}.
	 */
	public static void roof(Draft d, Hall hall, RoofForm form, Direction.Axis axis, int reserve, Consumer<Roofs.Plan> extras) {
		RoofForm base = form == RoofForm.CROSS_GABLE ? RoofForm.GABLE : form;
		if (base == RoofForm.FLAT || base == RoofForm.DOME || base == RoofForm.CONE) {
			hall.roof(form, axis, reserve, extras);
			return;
		}
		int[] tr = hall.body.topRect();
		int plate = hall.plateY();
		int limit = d.maxHeight() - 1 - reserve;
		Roofs.Opts o = Roofs.Opts.of(d);
		List<Double> pitches = new java.util.ArrayList<>();
		// steep only in whole steps: two up for one across, every course a stair on a full block with one verge (the
		// half steps of a 1.5 or 1.25 pitch laid the slope as a staircase of stairs and blocks that read as two roofs)
		Culture c = d.style().culture();
		boolean steep = Bohemia.is(d) || c == Culture.NORTHERN || c == Culture.ALPINE || c == Culture.TUNDRA;
		if (steep && base == RoofForm.GABLE && 2.0 > o.pitch()) {
			pitches.add(2.0);
		}
		for (double p = o.pitch(); p >= 0.5 - 1e-9; p -= 0.25) {
			pitches.add(p);
		}
		Roofs.Opts chosen = null;
		for (double p : pitches) {
			Roofs.Opts t = o.withPitch(base == RoofForm.GAMBREL ? Math.min(p, 1.25) : p);
			if (t.pitch() > 1.0 && (base == RoofForm.GABLE || base == RoofForm.GAMBREL) && d.habits().walls().kind() != Habits.WallKind.LOG) {
				// a steep gable ends flush with its wall: a block of verge on a steep slope is a wall of tiles in front of the
				// gable, and the boarded or rendered triangle should show (but a log wall's corners run a block past the wall,
				// and a flush verge left them standing out beyond the roof like scaffold poles: there the verge stays)
				t = t.withEaves(o.eaves(), 0);
			}
			// the eaves never hang lower than a man's head over the ground before the wall (a steep pitch with deep eaves
			// on a low wall brought the eave down to the garden: the alpine apiary's front was shut off by it)
			int eaveCap = Math.max(0, (int) Math.floor((plate - 1) / Math.max(1.0, t.pitch())));
			if (t.eaves() > eaveCap) {
				t = t.withEaves(eaveCap, t.gableOverhang());
			}
			if (Hall.ridgeY(d, tr[0], tr[1], tr[2], tr[3], plate, base, axis, t, hall.body.partyLeft, hall.body.partyRight) <= limit) {
				chosen = t;
				break;
			}
		}
		if (chosen == null) {
			hall.roof(form, axis, reserve, extras);
			return;
		}
		Roofs.Plan plan = Roofs.plan(d, tr[0], tr[1], tr[2], tr[3], plate, base, axis, chosen, hall.body.partyLeft, hall.body.partyRight);
		int width = tr[2] - tr[0] + 1;
		if (form == RoofForm.CROSS_GABLE && axis == Direction.Axis.X && width >= 7) {
			Side front = hall.body.side(hall.body.storeys() - 1, Dirs.FRONT);
			int gw = width >= 8 ? 5 : 3;
			int centre = hall.door != null ? hall.door : (tr[0] + tr[2]) / 2;
			plan.crossGable(front, Math.clamp(centre - gw / 2, tr[0] + 1, tr[2] - gw), gw, d);
		}
		if (extras != null) {
			extras.accept(plan);
		}
		hall.roof = plan.draw(d).roof();
		hall.form = form;
		hall.ridge = axis;
		hall.top = hall.roof.ridgeY;
		if (Bohemia.is(d) && chosen.pitch() > 1.0) {
			Bohemia.carryRidge(d, hall.roof);
		}
	}

	/** The kerb of pits and channels: the paving where it is stone, else the footings' stone. */
	public static Mat kerb(Draft d) {
		Mat paving = d.mat(Role.PAVING);
		Block b = paving.main(Form.FULL);
		String id = b == null ? "" : BuiltInRegistries.BLOCK.getKey(b).getPath();
		boolean wood = id.contains("planks") || id.contains("bamboo") || id.contains("log") || id.contains("wood");
		if (b != null && paving.has(Form.SLAB) && !wood) {
			return paving;
		}
		return d.mat(Role.FOUNDATION);
	}

	/** Paves the listed cells (x, z) at standing level y (the paving in the row below), over ground and nothing else. */
	public static void pave(Draft d, List<int[]> cells, int y) {
		Mat paving = d.mat(Role.PAVING);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int[] c : cells) {
				if (!Walls.inPlot(d, c[0], c[1]) || !d.isEmpty(c[0], y, c[1])) {
					continue;
				}
				BlockState below = d.get(c[0], y - 1, c[1]);
				if (below != null && !below.isAir() && !Garden.groundLike(d, c[0], y - 1, c[1])) {
					continue;
				}
				d.set(c[0], y - 1, c[1], paving.full());
			}
		}
	}


	/**
	 * A loading door high in a gable wall (a cross gable, a loft's end) at cell i of {@code wall}: the opening two high
	 * from the plate up with a door of the style's wood set in it, and over it a hoist beam reaching two out with a chain
	 * and {@code load} hanging from it over the yard. Only where the gable wall stands two above the plate and the
	 * beam's row and the chain's fall are free. Returns whether it was built.
	 */
	public static boolean hoistDoor(Draft d, Hall hall, Side wall, int i, BlockState load) {
		if (hall.roof == null || i < 1 || i >= wall.last()) {
			return false;
		}
		int x = wall.x(i);
		int z = wall.z(i);
		int y = hall.plateY() + 1;
		int under = hall.roof.covers(x, z) ? hall.roof.bottomY(x, z) : Integer.MIN_VALUE;
		if (under < y + 3 || d.isEmpty(x, y, z) || d.isEmpty(x, y + 1, z)) {
			return false;
		}
		int by = y + 2;
		int len = 2;
		for (int k = 1; k <= len; k++) {
			int ox = wall.outX(i, k);
			int oz = wall.outZ(i, k);
			if (!d.allowed(ox, by, oz) || !d.isEmpty(ox, by, oz) || !d.isEmpty(ox, by - 1, oz)) {
				return false;
			}
		}
		int ex = wall.outX(i, len);
		int ez = wall.outZ(i, len);
		int loadY = Math.max(2, Groundwork.surface(d, ex, ez) + 2);
		for (int yy = loadY; yy < by; yy++) {
			if (!d.isEmpty(ex, yy, ez) || !d.allowed(ex, yy, ez)) {
				return false;
			}
		}
		Block door = d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR);
		d.inStage(StageKind.OPENINGS, () -> d.door(x, y, z, door, wall.out(), DoorHingeSide.LEFT));
		Mat beam = d.mat(Role.BEAM);
		try (var s = d.stage(StageKind.FRAME)) {
			if (Openings.isWallBlock(d, x, by, z) || d.isEmpty(x, by, z)) {
				d.set(x, by, z, beam.log(wall.out().getAxis()));
			}
			for (int k = 1; k <= len; k++) {
				d.set(wall.outX(i, k), by, wall.outZ(i, k), beam.log(wall.out().getAxis()));
			}
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int yy = by - 1; yy > loadY; yy--) {
				d.set(ex, yy, ez, States.axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
			}
			if (load != null) {
				d.set(ex, loadY, ez, load);
			}
		}
		return true;
	}

	/**
	 * A back door into the yard behind a hall: in the back wall of the ground storey, away from the stair well against
	 * {@code wellWall} (whose run {@code well} the door must not open onto), the first column that takes a door and has
	 * the floor inside it free. Returns its column, or MIN_VALUE where none fits.
	 */
	public static int backDoor(Draft d, Hall hall, Direction wellWall, int[] well) {
		int[] in = hall.inner(0);
		boolean fromRight = wellWall == Dirs.LEFT;
		for (int k = 0; k <= in[2] - in[0]; k++) {
			int x = fromRight ? in[2] - k : in[0] + k;
			if (well != null && x >= well[4] && x <= well[6] && in[3] >= well[5] && in[3] <= well[7]) {
				continue;
			}
			if (!d.isEmpty(x, 0, in[3]) || !d.isEmpty(x, 1, in[3])) {
				continue;
			}
			if (hall.door(0, Dirs.BACK, x, 1, false, false)) {
				return x;
			}
		}
		return Integer.MIN_VALUE;
	}

	/**
	 * A walkable plan of a yard at standing level 0 over x0..x1 × z0..z1 for laying out its props: cells blocked where
	 * something already stands that cannot be walked over (a low step, a slab or a carpet can be), or where there is no
	 * footing (a pit, a channel). See {@link dev.voidmc.hearth.build.kit.trade.Floorplan}.
	 */
	public static dev.voidmc.hearth.build.kit.trade.Floorplan yardPlan(Draft d, int x0, int z0, int x1, int z1) {
		var fp = new dev.voidmc.hearth.build.kit.trade.Floorplan(x0, z0, x1, z1, 0, 2);
		for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
			for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
				BlockState st = d.get(x, 0, z);
				BlockState below = d.get(x, -1, z);
				boolean step = st != null && (st.getBlock() instanceof net.minecraft.world.level.block.StairBlock
					&& st.getValue(net.minecraft.world.level.block.StairBlock.HALF) == net.minecraft.world.level.block.state.properties.Half.BOTTOM
					|| st.getBlock() instanceof net.minecraft.world.level.block.SlabBlock
					&& st.getValue(net.minecraft.world.level.block.SlabBlock.TYPE) == net.minecraft.world.level.block.state.properties.SlabType.BOTTOM
					|| st.getBlock() instanceof net.minecraft.world.level.block.CarpetBlock);
				boolean wet = below != null && below.is(Blocks.WATER);
				// no headroom: a sill, a flower box or a step of the house overhead
				BlockState head = d.get(x, 1, z);
				boolean low = head != null && !head.isAir()
					&& !head.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO).isEmpty();
				if (st != null && !st.isAir() && !step || wet || low) {
					fp.block(x, z);
				}
			}
		}
		return fp;
	}
}
