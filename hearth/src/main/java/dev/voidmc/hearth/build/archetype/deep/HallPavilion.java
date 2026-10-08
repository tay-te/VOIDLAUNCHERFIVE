package dev.voidmc.hearth.build.archetype.deep;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.civic.Eaves;
import dev.voidmc.hearth.build.kit.deep.DeepManner;
import dev.voidmc.hearth.build.kit.deep.Glow;
import dev.voidmc.hearth.build.kit.deep.Moot;
import dev.voidmc.hearth.build.kit.deep.Rock;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The karst's and the glades' moot hall (art review 28): a raised timber hall on a terrace. A platform of the realm's
 * dressed stone two or three high with a balustrade round its edge, a broad flight up its front between lamp posts; on
 * it a hall of the culture's own walls between timber columns, a veranda of columns with brackets round its front and
 * sides under a lower eave of curved tiles with lifted corners, lattice screens in its windows, triple doors; over the
 * hall a hipped roof sweeping up at the corners. Inside, the moot on the terrace's floor, a brazier before the dais.
 */
final class HallPavilion {
	private HallPavilion() {
	}

	static void draw(Draft d, Glow g, int h) {
		int w = d.frontage();
		int dp = d.depth();
		int top = h - 1;
		int lift = h >= 15 ? 3 : 2;
		// the terrace: from row `lift` (the flight below it) to the back; standing level `lift`, its deck the course under
		int tz0 = lift;
		int tx0 = 1;
		int tx1 = w - 2;
		// the veranda's columns on a line one in from the terrace's edge; the hall's walls two in from them
		int vx0 = tx0 + 1;
		int vx1 = tx1 - 1;
		int vz0 = tz0 + 1;
		int cx0 = vx0 + 2;
		int cx1 = vx1 - 2;
		int cz0 = vz0 + 2;
		int cz1 = dp - 2;
		int c = (cx0 + cx1) / 2;
		int y0 = lift;
		int col = 3;
		// the hall's walls must stand over the lower eave's top (three out from them, curved)
		int hw = 6;
		int dmax = (cx1 - cx0 + 2) / 2;
		double pitch = 0.25;
		for (double p : new double[]{1.0, 0.75, 0.5, 0.35, 0.25}) {
			if (y0 + hw + 2 + p * Math.max(0, dmax - 2) + 1 <= top) {
				pitch = p;
				break;
			}
		}
		int plate = y0 + hw - 1;
		d.body(cx0, cz0, cx1, cz1);
		HallParts.ground(d, tz0);
		Mat dress = Rock.dressing(d);
		Mat plinth = d.mat(Role.PLINTH);
		Mat fill = d.mat(Role.FOUNDATION).has(Form.FULL) ? d.mat(Role.FOUNDATION) : d.mat(Role.WALL);
		Mat paving = d.mat(Role.PAVING);
		Mat floor = d.mat(Role.FLOOR);
		Groundwork.footings(d, tx0, tz0, tx1, dp - 1, -1);
		// the terrace: a dressed face and a plinth course, filled inside, paved on top (boards inside the hall)
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (int z = tz0; z < dp; z++) {
				for (int x = tx0; x <= tx1; x++) {
					boolean edge = x == tx0 || x == tx1 || z == tz0 || z == dp - 1;
					for (int y = -1; y < y0 - 1; y++) {
						d.set(x, y, z, edge ? (y == -1 ? plinth.full() : dress.full()) : fill.full());
					}
				}
			}
		}
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = tz0; z < dp; z++) {
				for (int x = tx0; x <= tx1; x++) {
					boolean in = x > cx0 && x < cx1 && z > cz0 && z < cz1;
					d.set(x, y0 - 1, z, in ? floor.full() : paving.full());
				}
			}
		}
		// the flight up the front, five wide (three on narrow plots), and the balustrade round the terrace's edge
		int fw = w >= 19 ? 5 : 3;
		int fx = HallParts.flightX(d, c - fw / 2, fw, tx0 + 1, tx1 - fw);
		Rock.steps(d, fx, 0, 0, Dirs.BACK, lift, fw, -1, dress);
		balustrade(d, tx0, tz0, tx1, dp - 1, y0, fx, fx + fw - 1);
		// the columns: round the veranda (front and sides) and at the hall's corners, brackets at their heads, a beam along
		Mat column = columns(d);
		Mat beam = d.mat(Role.BEAM);
		try (var s = d.stage(StageKind.FRAME)) {
			for (int z = vz0; z <= cz1; z++) {
				for (int x = vx0; x <= vx1; x++) {
					boolean line = x == vx0 || x == vx1 || z == vz0;
					if (!line) {
						continue;
					}
					boolean post = (x == vx0 || x == vx1) ? (z - vz0) % 3 == 0 || z == cz1 : (x - vx0) % 3 == 0 || x == vx1;
					if (z == vz0 && x >= fx && x <= fx + fw - 1 && x != fx && x != fx + fw - 1) {
						post = false;
					}
					if (post) {
						for (int y = y0; y < y0 + col; y++) {
							d.set(x, y, z, column.log(Direction.Axis.Y));
						}
					}
					d.set(x, y0 + col, z, beam.log(x == vx0 || x == vx1 ? Direction.Axis.Z : Direction.Axis.X));
				}
			}
			// brackets: upside-down stairs out from the columns under the beam, along the line
			for (int z = vz0; z <= cz1; z++) {
				for (int x = vx0; x <= vx1; x++) {
					if (!(x == vx0 || x == vx1 || z == vz0) || !d.isAbsent(x, y0 + col - 1, z)) {
						continue;
					}
					boolean nextToPost = isColumn(d, x - 1, y0 + col - 1, z, column) || isColumn(d, x + 1, y0 + col - 1, z, column)
						|| isColumn(d, x, y0 + col - 1, z - 1, column) || isColumn(d, x, y0 + col - 1, z + 1, column);
					if (!nextToPost) {
						continue;
					}
					Direction to = isColumn(d, x - 1, y0 + col - 1, z, column) ? Dirs.LEFT : isColumn(d, x + 1, y0 + col - 1, z, column) ? Dirs.RIGHT
						: isColumn(d, x, y0 + col - 1, z - 1, column) ? Dirs.FRONT : Dirs.BACK;
					d.set(x, y0 + col - 1, z, beam.stairsOrSlab(to, Half.TOP));
				}
			}
		}
		// the hall's walls: the culture's own between columns at the corners and every third, a sill and a plate of timber
		Paint wallP = DeepManner.home(d, Role.WALL).orElse(d.mat(Role.WALL).full());
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = cz0; z <= cz1; z++) {
				for (int x = cx0; x <= cx1; x++) {
					boolean side = x == cx0 || x == cx1;
					if (!side && z != cz0 && z != cz1) {
						continue;
					}
					boolean post = side && (z == cz0 || z == cz1) || (side ? (z - cz0) % 3 == 0 : (x - cx0) % 3 == 0);
					for (int y = y0; y <= plate; y++) {
						Paint p = post ? column.log(Direction.Axis.Y) : y == plate ? beam.log(side ? Direction.Axis.Z : Direction.Axis.X) : wallP;
						d.set(x, y, z, p);
					}
				}
			}
		}
		d.carve(cx0 + 1, y0, cz0 + 1, cx1 - 1, plate - 1, cz1 - 1);
		d.carve(vx0 + 1, y0, vz0 + 1, vx1 - 1, y0 + col - 1, cz1);
		lattices(d, cx0, cz0, cx1, cz1, y0, c);
		// the lower eave round the veranda (curved, corners lifted, lanterns under them), the upper hipped roof
		Eaves.skirt(d, vx0, vz0, vx1, cz1, cx0, cz0, cx1, cz1, y0 + col, 1, 0.5, true, true);
		DeepManner.rehome(d, 0, y0, 0, w - 1, plate, dp - 1, d.mat(Role.ROOF), Role.ROOF);
		Mat roofM = d.mat(Role.ROOF);
		Mat trim = d.mat(Role.ROOF_TRIM);
		int rx0 = cx0 - 1;
		int rx1 = cx1 + 1;
		int rz0 = cz0 - 1;
		int rz1 = Math.min(dp - 1, cz1 + 1);
		Roofs.Field f = Roofs.flared(Roofs.hip(rx0, rz0, rx1, rz1, Roofs.Profile.curved(pitch)), rx0, rz0, rx1, rz1, 1.0, 3);
		Roofs.Roof r = Roofs.raster(d, f, plate + 1, rx0, rz0, rx1, rz1, roofM, trim, Habits.Ridge.FULL);
		Eaves.tips(d, r, rx0, rz0, rx1, rz1);
		DeepManner.rehome(d, 0, plate, 0, w - 1, top, dp - 1, roofM, Role.ROOF);
		// a finial of the realm's glow at each end of the ridge
		if (r.ridgeY != Integer.MIN_VALUE && r.ridgeY + 1 <= top) {
			int cz = (rz0 + rz1) / 2;
			for (int z = rz0; z <= rz1; z++) {
				if (r.covers(c, z) && r.surfaceY(c, z) == r.ridgeY && (z == cz || !r.covers(c, z - 1) || r.surfaceY(c, z - 1) < r.ridgeY
					|| !r.covers(c, z + 1) || r.surfaceY(c, z + 1) < r.ridgeY)) {
					if (z != cz) {
						try (var s = d.stage(StageKind.ROOF)) {
							d.set(c, r.ridgeY + 1, z, g.lamp.defaultBlockState());
						}
					}
				}
			}
		}
		// the triple doors in the front
		Block door = d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR);
		for (int x = c - 1; x <= c + 1; x++) {
			int fx2 = x;
			d.air(fx2, y0, cz0);
			d.air(fx2, y0 + 1, cz0);
			DoorHingeSide hinge = x <= c ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT;
			d.inStage(StageKind.OPENINGS, () -> d.door(fx2, y0, cz0, door, Dirs.FRONT, hinge));
		}
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = c - 1; x <= c + 1; x++) {
				d.set(x, y0 + 2, cz0, beam.log(Direction.Axis.X));
			}
			d.set(c, y0 + 3, cz0, g.accent.defaultBlockState());
		}
		d.entrance(c, y0, cz0 - 1);
		d.poi(PoiType.DOOR, c, y0, cz0 - 1, Dirs.BACK);
		Moot.furnish(d, new Moot.Room(cx0 + 1, cz0 + 1, cx1 - 1, cz1 - 1, y0, plate, c, Moot.Fire.BRAZIER, 0), g);
		Lighting.room(d, vx0 + 1, vz0 + 1, cx0 - 1, cz1, y0);
		Lighting.room(d, cx1 + 1, vz0 + 1, vx1 - 1, cz1, y0);
		Lighting.room(d, vx0 + 1, vz0 + 1, vx1 - 1, cz0 - 1, y0);
		HallParts.apron(d, c, g, fw / 2 + 2);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, top);
	}

	/** The columns: the culture's own frame logs where it has them, else the deep timber. */
	private static Mat columns(Draft d) {
		return d.mat(Role.FRAME);
	}

	private static boolean isColumn(Draft d, int x, int y, int z, Mat column) {
		var s = d.get(x, y, z);
		return s != null && s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS)
			&& s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS) == Direction.Axis.Y;
	}

	/** A balustrade round the terrace's edge: walls of the dressing with posts every few cells, open at the flight. */
	private static void balustrade(Draft d, int x0, int z0, int x1, int z1, int y, int gap0, int gap1) {
		Mat dress = Rock.dressing(d);
		Mat wall = d.mat(Role.WALL);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int[] p : Draft.ring(x0, z0, x1, z1)) {
				int x = p[0];
				int z = p[1];
				if (z == z0 && x >= gap0 && x <= gap1 || z == z1) {
					continue;
				}
				boolean post = (x + z) % 4 == 0 || (x == x0 || x == x1) && (z == z0 || z == z1);
				// posts capped with a slab, a block and a half like the walls between: nobody steps up onto the rail
				boolean cap = d.allowed(x, y + 1, z) && d.isAbsent(x, y + 1, z);
				if (!post && (wall.has(Form.WALL) || dress.has(Form.WALL))) {
					d.set(x, y, z, wall.has(Form.WALL) ? wall.wall() : dress.wall());
				} else {
					d.set(x, y, z, dress.full());
					if (cap) {
						d.set(x, y + 1, z, dress.slab(SlabType.BOTTOM));
					}
				}
			}
		}
	}

	/**
	 * Lattice windows down the hall's sides and either side of the doors: openings of the furniture's fences (the lattice)
	 * two high between the posts, with the house's glass behind where the lattice is open.
	 */
	private static void lattices(Draft d, int x0, int z0, int x1, int z1, int y0, int c) {
		Mat lattice = d.mat(Role.FURNITURE).has(Form.FENCE) ? d.mat(Role.FURNITURE) : d.mat(Role.FRAME);
		try (var s = d.stage(StageKind.OPENINGS)) {
			for (int z = z0 + 1; z < z1; z++) {
				if ((z - z0) % 3 == 0) {
					continue;
				}
				for (int x : new int[]{x0, x1}) {
					for (int y = y0 + 1; y <= y0 + 2; y++) {
						d.set(x, y, z, lattice.fence());
					}
				}
			}
			for (int x = x0 + 1; x < x1; x++) {
				if ((x - x0) % 3 == 0 || Math.abs(x - c) <= 2) {
					continue;
				}
				for (int y = y0 + 1; y <= y0 + 2; y++) {
					d.set(x, y, z0, lattice.fence());
				}
			}
		}
	}
}
