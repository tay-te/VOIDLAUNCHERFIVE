package dev.voidmc.hearth.build.archetype.deep;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.kit.deep.DeepManner;
import dev.voidmc.hearth.build.kit.deep.Glow;
import dev.voidmc.hearth.build.kit.deep.Moot;
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

/**
 * The moot hall of the tropics and the bayou (art review 28): a timber hall raised on posts. A grid of posts on stone
 * pads carries a deck three over the floor of the realm; round the hall's front and sides a gallery two wide behind a
 * railing, its posts carrying the eaves; the hall's walls of the culture's boards with shuttered windows; over all a
 * great hipped roof on deep eaves, steep where the vault allows; a broad flight of steps up the front. Inside, the moot on
 * the deck under the tie beams, a brazier before the dais; under the deck the posts, lanterns hung between them.
 */
final class HallStilt {
	private HallStilt() {
	}

	static void draw(Draft d, Glow g, int h) {
		int w = d.frontage();
		int dp = d.depth();
		int top = h - 1;
		int lift = 3;
		int y0 = lift;
		int px0 = 1;
		int px1 = w - 2;
		int pz0 = lift;
		int pz1 = dp - 1;
		int cx0 = px0 + 2;
		int cx1 = px1 - 2;
		int cz0 = pz0 + 2;
		int cz1 = pz1 - 1;
		int c = (cx0 + cx1) / 2;
		// the flight's first column: before the doors, unless the street falls away there
		int sx = HallParts.flightX(d, c - 1, 3, px0 + 1, px1 - 3);
		int plate = y0 + 4;
		int dmax = (w - 1) / 2;
		double pitch = 0.4;
		for (double p : new double[]{1.0, 0.85, 0.75, 0.6, 0.5, 0.4}) {
			if (plate + 1 + (int) Math.ceil(p * dmax) <= top) {
				pitch = p;
				break;
			}
		}
		d.body(cx0, cz0, cx1, cz1);
		HallParts.ground(d, pz0);
		Mat frame = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		Mat plinth = d.mat(Role.PLINTH);
		Mat floor = d.mat(Role.FLOOR);
		Mat rail = d.mat(Role.RAILING).has(Form.FENCE) ? d.mat(Role.RAILING) : d.mat(Role.FURNITURE);
		Paint boards = DeepManner.home(d, Role.WALL).orElse(frame.full());
		Paint deckP = DeepManner.home(d, Role.FLOOR).orElse(floor.full());
		// the posts on their pads, every third cell both ways and at the edges, beams along their heads under the deck
		Groundwork.level(d, px0, pz0, px1, pz1, 0, true);
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (int z = pz0; z <= pz1; z++) {
				for (int x = px0; x <= px1; x++) {
					if (isPost(x, z, px0, pz0, px1, pz1)) {
						d.set(x, -1, z, plinth.full());
					} else {
						d.set(x, -1, z, d.mat(Role.PAVING).full());
					}
				}
			}
		}
		try (var s = d.stage(StageKind.FRAME)) {
			for (int z = pz0; z <= pz1; z++) {
				for (int x = px0; x <= px1; x++) {
					if (isPost(x, z, px0, pz0, px1, pz1)) {
						for (int y = 0; y < y0 - 1; y++) {
							d.set(x, y, z, frame.log(Direction.Axis.Y));
						}
					}
				}
			}
		}
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = pz0; z <= pz1; z++) {
				for (int x = px0; x <= px1; x++) {
					boolean joist = isPost(x, z, px0, pz0, px1, pz1) || x == px0 || x == px1;
					d.set(x, y0 - 1, z, joist && (x == px0 || x == px1 || z == pz0 || z == pz1) ? beam.log(x == px0 || x == px1 ? Direction.Axis.Z
						: Direction.Axis.X) : deckP);
				}
			}
		}
		d.carve(px0, 0, pz0, px1, y0 - 2, pz1);
		// the gallery's railing and its posts up to the eaves' beam
		try (var s = d.stage(StageKind.FRAME)) {
			for (int[] p : Draft.ring(px0, pz0, px1, pz1)) {
				int x = p[0];
				int z = p[1];
				boolean stair = z == pz0 && x >= sx && x <= sx + 2;
				boolean post = isPost(x, z, px0, pz0, px1, pz1) && !stair;
				if (post) {
					for (int y = y0; y < plate; y++) {
						d.set(x, y, z, frame.log(Direction.Axis.Y));
					}
				} else if (!stair) {
					d.set(x, y0, z, rail.fence());
				}
				if (z == pz1) {
					continue;
				}
				d.set(x, plate - 1, z, beam.log(x == px0 || x == px1 ? Direction.Axis.Z : Direction.Axis.X));
			}
		}
		// the hall's walls: boards between posts, a sill and a plate of the beams
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = cz0; z <= cz1; z++) {
				for (int x = cx0; x <= cx1; x++) {
					boolean side = x == cx0 || x == cx1;
					if (!side && z != cz0 && z != cz1) {
						continue;
					}
					boolean post = side && (z == cz0 || z == cz1) || (side ? (z - cz0) % 3 == 0 : (x - cx0) % 3 == 0);
					for (int y = y0; y <= plate; y++) {
						d.set(x, y, z, post ? frame.log(Direction.Axis.Y) : y == plate ? beam.log(side ? Direction.Axis.Z : Direction.Axis.X) : boards);
					}
				}
			}
		}
		d.carve(cx0 + 1, y0, cz0 + 1, cx1 - 1, plate - 1, cz1 - 1);
		// the roof: one great hip over the gallery, the walls filled up to it
		Mat roofM = d.mat(Role.ROOF);
		Mat trim = d.mat(Role.ROOF_TRIM);
		int rz0 = pz0 - 1;
		Roofs.Roof r = Roofs.raster(d, Roofs.hip(0, rz0, w - 1, pz1, Roofs.Profile.straight(pitch)), plate, 0, rz0, w - 1, pz1, roofM, trim,
			Habits.Ridge.SLAB);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = cz0; z <= cz1; z++) {
				for (int x = cx0; x <= cx1; x++) {
					boolean edge = x == cx0 || x == cx1 || z == cz0 || z == cz1;
					if (!edge || !r.covers(x, z)) {
						continue;
					}
					for (int y = plate + 1; y < r.bottomY(x, z); y++) {
						d.set(x, y, z, boards);
					}
				}
			}
		}
		DeepManner.rehome(d, 0, plate, 0, w - 1, top, dp - 1, roofM, Role.ROOF);
		// shuttered windows down the sides and either side of the door
		for (int z = cz0 + 2; z < cz1 - 1; z += 3) {
			shuttered(d, cx0, y0 + 1, z, Dirs.LEFT);
			shuttered(d, cx1, y0 + 1, z, Dirs.RIGHT);
		}
		for (int x = cx0 + 2; x < cx1 - 1; x += 3) {
			if (Math.abs(x - c) >= 2) {
				shuttered(d, x, y0 + 1, cz0, Dirs.FRONT);
			}
		}
		// the steps up the front, three wide
		Stairs.straight(d, sx, 0, 0, Dirs.BACK, lift, 3);
		// double doors
		Block door = d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR);
		for (int x = c; x <= c + 1 && x < cx1; x++) {
			int fx = x;
			d.air(fx, y0, cz0);
			d.air(fx, y0 + 1, cz0);
			DoorHingeSide hinge = x == c ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT;
			d.inStage(StageKind.OPENINGS, () -> d.door(fx, y0, cz0, door, Dirs.FRONT, hinge));
		}
		d.entrance(c, y0, cz0 - 1);
		d.poi(PoiType.DOOR, c, y0, cz0 - 1, Dirs.BACK);
		Moot.furnish(d, new Moot.Room(cx0 + 1, cz0 + 1, cx1 - 1, cz1 - 1, y0, plate, c, Moot.Fire.BRAZIER, 0), g);
		Lighting.room(d, px0 + 1, pz0 + 1, cx0 - 1, pz1 - 1, y0);
		Lighting.room(d, cx1 + 1, pz0 + 1, px1 - 1, pz1 - 1, y0);
		Lighting.room(d, px0 + 1, pz0 + 1, px1 - 1, cz0 - 1, y0);
		// under the deck: lanterns hung between the posts
		for (int z = pz0 + 2; z < pz1; z += 4) {
			for (int x = px0 + 2; x < px1; x += 4) {
				if (d.isAbsent(x, y0 - 2, z) || d.isEmpty(x, y0 - 2, z)) {
					try (var s = d.stage(StageKind.INTERIOR)) {
						d.set(x, y0 - 2, z, States.lantern(Blocks.LANTERN, true));
					}
				}
			}
		}
		HallParts.apron(d, c, g, 3);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, top);
	}

	private static boolean isPost(int x, int z, int x0, int z0, int x1, int z1) {
		boolean px = (x - x0) % 3 == 0 || x == x1;
		boolean pz = (z - z0) % 3 == 0 || z == z1;
		return px && pz;
	}

	/** A window two high with open shutters of the culture's trapdoors either side (or plain where it has none). */
	private static void shuttered(Draft d, int x, int y, int z, Direction out) {
		Mat glass = d.mat(Role.GLASS);
		try (var s = d.stage(StageKind.OPENINGS)) {
			d.set(x, y, z, glass.pane());
			d.set(x, y + 1, z, glass.pane());
		}
		Block shutter = d.mat(Role.SHUTTER).main(Form.TRAPDOOR);
		if (shutter == null) {
			return;
		}
		Direction along = Dirs.right(out.getOpposite());
		try (var s = d.stage(StageKind.OPENINGS)) {
			for (int sgn : new int[]{-1, 1}) {
				int sx = x + Dirs.dx(along) * sgn + Dirs.dx(out);
				int sz = z + Dirs.dz(along) * sgn + Dirs.dz(out);
				if (sz < 0 || !d.isAbsent(sx, y, sz)) {
					continue;
				}
				Direction face = sgn < 0 ? along.getOpposite() : along;
				for (int yy = y; yy <= y + 1; yy++) {
					d.set(sx, yy, sz, States.trapdoor(shutter, face, Half.BOTTOM, true));
				}
			}
		}
	}
}
