package dev.voidmc.hearth.build.archetype.deep;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.deep.DeepManner;
import dev.voidmc.hearth.build.kit.deep.Glow;
import dev.voidmc.hearth.build.kit.deep.Moot;
import dev.voidmc.hearth.build.kit.deep.Rock;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The mesa's moot hall (art review 28): a pueblo's great house. Blocks of the culture's earthen render on a course of the
 * realm's stone stepped back in terraces, each a storey over the one before and set back from its front: the great room
 * of the moot on the ground, the record room over it, a lookout over that where the vault leaves room. Flat roofs behind
 * rounded parapets, the <i>vigas</i> (roof beams) showing through the walls under every roof, ladders up the fronts from
 * terrace to terrace, small windows under timber lintels, and on the first terrace's front a mission's bell wall, a
 * stepped gable with a lantern hung in its arch.
 */
final class HallPueblo {
	private HallPueblo() {
	}

	static void draw(Draft d, Glow g, int h) {
		int w = d.frontage();
		int dp = d.depth();
		int top = h - 1;
		int bx0 = 1;
		int bx1 = w - 2;
		int zf = 2;
		int zb = dp - 1;
		int c = w / 2;
		// the storeys: walls five high (the deck the course over them), a parapet over each deck
		int s1 = 5;
		int s2 = s1 + 5;
		int s3 = s2 + 5;
		boolean second = s2 + 1 <= top;
		boolean third = s3 + 1 <= top && zb - (zf + 8) >= 6 && bx1 - bx0 >= 14;
		d.body(bx0, zf, bx1, zb);
		HallParts.ground(d, zf);
		Groundwork.footings(d, bx0, zf, bx1, zb, -1);
		block(d, bx0, zf, bx1, zb, -1, s1, true);
		int z2 = zf + 4;
		int x20 = bx0 + 2;
		int x21 = bx1 - 2;
		int z3 = z2 + 4;
		int x30 = x20 + 3;
		int x31 = x21 - 3;
		if (second) {
			block(d, x20, z2, x21, zb, s1 + 1, s2, false);
		}
		if (third) {
			block(d, x30, z3, x31, zb, s2 + 1, s3, false);
		}
		parapet(d, bx0, zf, bx1, zb, s1 + 1, second ? new int[]{x20, z2, x21} : null);
		if (second) {
			parapet(d, x20, z2, x21, zb, s2 + 1, third ? new int[]{x30, z3, x31} : null);
		}
		if (third) {
			parapet(d, x30, z3, x31, zb, s3 + 1, null);
		}
		// the door under a timber lintel, small windows either side
		Mat beam = d.mat(Role.BEAM);
		Block door = d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR);
		d.air(c, 0, zf);
		d.air(c, 1, zf);
		d.inStage(StageKind.OPENINGS, () -> d.door(c, 0, zf, door, Dirs.FRONT, DoorHingeSide.LEFT));
		try (var s = d.stage(StageKind.FRAME)) {
			for (int x = c - 1; x <= c + 1; x++) {
				d.set(x, 2, zf, beam.log(Direction.Axis.X));
			}
		}
		for (int x = bx0 + 3; x < bx1 - 1; x += 4) {
			if (Math.abs(x - c) >= 3) {
				window(d, x, 2, zf, Dirs.FRONT);
			}
		}
		for (int z = zf + 3; z < zb - 1; z += 4) {
			window(d, bx0, 2, z, Dirs.LEFT);
			window(d, bx1, 2, z, Dirs.RIGHT);
		}
		d.entrance(c, 0, zf - 1);
		d.poi(PoiType.DOOR, c, 0, zf - 1, Dirs.BACK);
		Lighting.doorLamp(d, c - 2, 2, zf - 1, Dirs.BACK);
		// the upper rooms: a door onto the terrace below each, a ladder up to it from the street or the terrace below
		int lx = c + 3 < bx1 - 1 ? c + 3 : c - 3;
		ladder(d, lx, zf - 1, 0, s1 + 1);
		if (second) {
			room(d, x20, z2, x21, zb, s1 + 1, s2, c, g);
			if (third) {
				ladder(d, c + (lx > c ? 2 : -2), z2 - 1, s1 + 1, s2 + 1);
				room(d, x30, z3, x31, zb, s2 + 1, s3, c, g);
				d.poi(PoiType.VIEWPOINT, c, s3 + 1, z3 + 2, Dirs.FRONT);
			} else {
				d.poi(PoiType.VIEWPOINT, c, s2 + 1, z2 + 2, Dirs.FRONT);
			}
		}
		vigas(d, bx0, zf, bx1, zb, s1 - 1);
		if (second) {
			vigas(d, x20, z2, x21, zb, s2 - 1);
		}
		if (third) {
			vigas(d, x30, z3, x31, zb, s3 - 1);
		}
		bellWall(d, c, zf, s1 + 1, top);
		Moot.furnish(d, new Moot.Room(bx0 + 1, zf + 1, bx1 - 1, zb - 1, 0, s1, c, Moot.Fire.WALL, Math.min(top - 1, s1 + 2)), g);
		HallParts.apron(d, c, g, 5);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, top);
	}

	/**
	 * A block of the pueblo: walls of the render round {@code x0..x1 × z0..z1} from {@code y0} up to the deck at
	 * {@code deck} (the realm's stone in its foot course on the ground), the deck of the culture's roofing.
	 */
	private static void block(Draft d, int x0, int z0, int x1, int z1, int y0, int deck, boolean ground) {
		Paint render = DeepManner.home(d, Role.WALL).orElse(d.mat(Role.WALL).full());
		Paint roof = DeepManner.home(d, Role.ROOF).orElse(d.mat(Role.ROOF).full());
		Mat plinth = d.mat(Role.PLINTH);
		Mat rough = d.mat(Role.FOUNDATION).has(Form.FULL) ? d.mat(Role.FOUNDATION) : plinth;
		Mat floor = d.mat(Role.FLOOR);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
					if (!edge) {
						continue;
					}
					for (int y = y0; y < deck; y++) {
						d.set(x, y, z, ground && y == -1 ? plinth.full() : ground && y == 0 ? rough.full() : render);
					}
				}
			}
		}
		if (ground) {
			try (var s = d.stage(StageKind.FLOORS)) {
				for (int z = z0 + 1; z < z1; z++) {
					for (int x = x0 + 1; x < x1; x++) {
						d.set(x, -1, z, floor.full());
					}
				}
			}
		}
		d.carve(x0 + 1, Math.max(0, y0), z0 + 1, x1 - 1, deck - 1, z1 - 1);
		try (var s = d.stage(StageKind.ROOF)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					d.set(x, deck, z, roof);
				}
			}
		}
	}

	/** A rounded parapet (slabs, a block at each corner) round a deck at standing level {@code y}, open where the next block stands. */
	private static void parapet(Draft d, int x0, int z0, int x1, int z1, int y, int[] next) {
		if (y >= d.maxHeight()) {
			return;
		}
		Paint render = DeepManner.home(d, Role.WALL).orElse(d.mat(Role.WALL).full());
		Paint slab = DeepManner.home(d, Role.WALL, Form.SLAB, st -> st).orElse(Rock.dressing(d).slab(SlabType.BOTTOM));
		try (var s = d.stage(StageKind.ROOF)) {
			for (int[] p : Draft.ring(x0, z0, x1, z1)) {
				int x = p[0];
				int z = p[1];
				if (next != null && x >= next[0] && x <= next[2] && z >= next[1]) {
					continue;
				}
				boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
				d.set(x, y, z, corner ? render : slab);
			}
		}
	}

	/** The vigas: the roof's beams showing a block out of the walls' faces under the deck, every other cell. */
	private static void vigas(Draft d, int x0, int z0, int x1, int z1, int y) {
		Mat beam = d.mat(Role.BEAM);
		try (var s = d.stage(StageKind.FRAME)) {
			for (int x = x0 + 1; x < x1; x += 2) {
				if (d.allowed(x, y, z0 - 1) && d.isAbsent(x, y, z0 - 1)) {
					d.set(x, y, z0 - 1, beam.log(Direction.Axis.Z));
				}
			}
			for (int z = z0 + 1; z < z1; z += 2) {
				for (int x : new int[]{x0 - 1, x1 + 1}) {
					if (x >= 0 && x < d.frontage() && d.isAbsent(x, y, z)) {
						d.set(x, y, z, beam.log(Direction.Axis.X));
					}
				}
			}
		}
	}

	/** A small window two high under a timber lintel. */
	private static void window(Draft d, int x, int y, int z, Direction out) {
		Mat glass = d.mat(Role.GLASS);
		Mat beam = d.mat(Role.BEAM);
		try (var s = d.stage(StageKind.OPENINGS)) {
			d.set(x, y, z, glass.pane());
		}
		try (var s = d.stage(StageKind.FRAME)) {
			d.set(x, y + 1, z, beam.log(out.getAxis() == Direction.Axis.Z ? Direction.Axis.X : Direction.Axis.Z));
		}
	}

	/** An upper room: its door onto the terrace in front of it, lit; a chest of the town's tallies against its back wall. */
	private static void room(Draft d, int x0, int z0, int x1, int z1, int y, int deck, int c, Glow g) {
		Block door = d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR);
		d.air(c, y, z0);
		d.air(c, y + 1, z0);
		d.inStage(StageKind.OPENINGS, () -> d.door(c, y, z0, door, Dirs.FRONT, DoorHingeSide.RIGHT));
		Mat beam = d.mat(Role.BEAM);
		try (var s = d.stage(StageKind.FRAME)) {
			d.set(c, y + 2, z0, beam.log(Direction.Axis.X));
		}
		d.entrance(c, y, z0 - 1);
		d.poi(PoiType.DOOR, c, y, z0 - 1, Dirs.BACK);
		Lighting.doorLamp(d, c + 1, y + 2, z0 - 1, Dirs.BACK);
		for (int x = x0 + 3; x < x1 - 1; x += 4) {
			if (Math.abs(x - c) >= 2) {
				window(d, x, y + 1, z0, Dirs.FRONT);
			}
		}
		Lighting.room(d, x0 + 1, z0 + 1, x1 - 1, z1 - 1, y);
	}

	/** A ladder up the face of a wall from the standing level {@code y0} to the terrace at {@code y1}. */
	private static void ladder(Draft d, int x, int z, int y0, int y1) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int y = y0; y < y1; y++) {
				if (d.allowed(x, y, z)) {
					d.set(x, y, z, States.facing(Blocks.LADDER, Dirs.FRONT));
				}
			}
		}
		// the parapet opened where the ladder arrives
		if (d.allowed(x, y1, z + 1)) {
			d.air(x, y1, z + 1);
		}
	}

	/**
	 * The bell wall on the first terrace's front over the door: a stepped gable of the render rising over the parapet,
	 * an arch in it with a lantern hung where a bell would be.
	 */
	private static void bellWall(Draft d, int c, int z, int y, int top) {
		if (y + 3 > top) {
			return;
		}
		Paint render = DeepManner.home(d, Role.WALL).orElse(d.mat(Role.WALL).full());
		Mat dress = Rock.dressing(d);
		try (var s = d.stage(StageKind.ROOF)) {
			for (int x = c - 2; x <= c + 2; x++) {
				int hgt = 3 - Math.abs(x - c) / 2 * 1 - (Math.abs(x - c) == 2 ? 1 : 0);
				for (int yy = y; yy < y + hgt && yy <= top; yy++) {
					d.set(x, yy, z, render);
				}
			}
			if (y + 3 <= top) {
				d.set(c, y + 3, z, dress.slab(SlabType.BOTTOM));
			}
		}
		try (var s = d.stage(StageKind.OPENINGS)) {
			d.set(c, y, z, Blocks.AIR.defaultBlockState());
			d.set(c, y + 1, z, Blocks.AIR.defaultBlockState());
		}
		Lighting.chandelier(d, c, z, y + 2, y + 1);
	}
}
