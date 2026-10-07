package dev.voidmc.hearth.build.archetype.deep;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Roofs;
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
 * The Mediterranean moot hall (art review 28): the <i>palazzo comunale</i>. A block of the culture's whitewash on a
 * course of the realm's stone with dressed quoins, a row of round-arched windows high in its front; before it an open
 * loggia of round arches on columns with a terrace and balustrade over it; a low hipped roof of the culture's tiles on
 * deep eaves; at a front corner a campanile, plain to its belfry of arched openings, a lantern hung in each, a low
 * pyramid cap over a corbelled cornice. Inside, the moot in one tall room under tie beams, a hearth in its side wall.
 */
final class HallLoggia {
	private HallLoggia() {
	}

	static void draw(Draft d, Glow g, int h) {
		int w = d.frontage();
		int dp = d.depth();
		int top = h - 1;
		int bx0 = 1;
		int bx1 = w - 2;
		int zf = 2;
		int zm = zf + 3;
		int zb = dp - 1;
		// the campanile at the front right, five wide on broad plots, four else
		int tw = w >= 19 ? 5 : 4;
		int tx0 = bx1 - tw + 1;
		int c = (bx0 + bx1) / 2;
		int hw = Math.min(8, top - 4);
		int ly = 4;
		d.body(bx0, zm, bx1, zb);
		HallParts.ground(d, zf);
		Groundwork.footings(d, bx0, zf, bx1, zb, -1);
		Mat plinth = d.mat(Role.PLINTH);
		Mat rough = d.mat(Role.FOUNDATION).has(Form.FULL) ? d.mat(Role.FOUNDATION) : plinth;
		Mat dress = Rock.dressing(d);
		Mat paving = d.mat(Role.PAVING);
		Mat floor = d.mat(Role.FLOOR);
		Paint render = DeepManner.home(d, Role.WALL).orElse(d.mat(Role.WALL).full());
		Paint quoin = DeepManner.home(d, Role.WALL_ACCENT).orElse(dress.full());
		// the block
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = zm; z <= zb; z++) {
				for (int x = bx0; x <= bx1; x++) {
					boolean edge = x == bx0 || x == bx1 || z == zm || z == zb;
					if (!edge) {
						continue;
					}
					boolean corner = (x == bx0 || x == bx1) && (z == zm || z == zb);
					for (int y = -1; y <= hw; y++) {
						d.set(x, y, z, y == -1 ? plinth.full() : y == 0 ? rough.full() : corner && (y & 1) == 1 || y == hw ? quoin : render);
					}
				}
			}
		}
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = zf; z < zb; z++) {
				for (int x = bx0; x <= bx1; x++) {
					if (z < zm || x > bx0 && x < bx1 && z > zm) {
						d.set(x, -1, z, z < zm ? paving.full() : floor.full());
					}
				}
			}
		}
		d.carve(bx0 + 1, 0, zm + 1, bx1 - 1, hw - 1, zb - 1);
		loggia(d, bx0, zf, tx0 - 1, zm, ly, c);
		// the roof: a low hip of the culture's tiles on deep eaves over the block
		Mat roofM = d.mat(Role.ROOF);
		Mat trim = d.mat(Role.ROOF_TRIM);
		int dmax = (w - 1) / 2;
		double pitch = Math.min(0.35, Math.max(0.25, (top - hw - 2) / (double) dmax));
		Roofs.Roof r = Roofs.raster(d, Roofs.hip(0, zm - 1, w - 1, zb, Roofs.Profile.straight(pitch)), hw + 1, 0, zm - 1, w - 1, zb, roofM, trim,
			Habits.Ridge.SLAB);
		DeepManner.rehome(d, 0, hw, 0, w - 1, top, dp - 1, roofM, Role.ROOF);
		campanile(d, tx0, zf, bx1, top, g);
		// a cornice of brackets under the eaves along the front
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = bx0; x <= bx1; x++) {
				if (x < tx0 && d.isAbsent(x, hw, zm - 1)) {
					d.set(x, hw, zm - 1, dress.stairsOrSlab(Dirs.BACK, Half.TOP));
				}
			}
		}
		// the windows high in the front over the loggia's terrace, round-arched; tall ones down the sides
		for (int x = bx0 + 2; x + 1 < tx0 - 1; x += 4) {
			Rock.window(d, x, ly + 3, zm, Dirs.FRONT, 2, Math.min(3, hw - ly - 3), true);
		}
		for (int z = zm + 3; z < zb - 1; z += 4) {
			Rock.window(d, bx0, 2, z, Dirs.LEFT, 1, Math.min(4, hw - 3), true);
			Rock.window(d, bx1, 2, z, Dirs.RIGHT, 1, Math.min(4, hw - 3), true);
		}
		// the hall's door from the loggia: double doors under a carved head with the realm's glow
		Block door = d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR);
		Rock.archedDoorHead(d, c, 0, zm, Dirs.FRONT, true, g);
		d.air(c, 0, zm);
		d.air(c, 1, zm);
		d.inStage(StageKind.OPENINGS, () -> d.door(c, 0, zm, door, Dirs.FRONT, DoorHingeSide.LEFT));
		d.entrance(c, 0, zm - 1);
		d.poi(PoiType.DOOR, c, 0, zm - 1, Dirs.BACK);
		int hz = zm + 4;
		int stack = r.covers(bx0, hz) ? r.surfaceY(bx0, hz) + 1 : hw + 2;
		Moot.furnish(d, new Moot.Room(bx0 + 1, zm + 1, bx1 - 1, zb - 1, 0, hw, c, Moot.Fire.WALL, Math.min(top - 1, stack)), g);
		HallParts.apron(d, (bx0 + tx0 - 1) / 2, g, Math.min(4, (tx0 - bx0) / 2 - 1));
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, top);
	}

	/**
	 * The loggia across the front between the block's left corner and the campanile: columns every third cell with round
	 * arches between them, a vaulted ceiling, a terrace over it behind a balustrade, lanterns hung in the bays.
	 */
	private static void loggia(Draft d, int x0, int zf, int x1, int zm, int ly, int c) {
		Mat dress = Rock.dressing(d);
		Paint render = DeepManner.home(d, Role.WALL).orElse(d.mat(Role.WALL).full());
		Paint column = DeepManner.home(d, Role.WALL_ACCENT).orElse(dress.full());
		Mat ceiling = d.mat(Role.ROOF);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = x0; x <= x1; x++) {
				boolean col = (x - x0) % 3 == 0 || x == x1;
				for (int y = -1; y <= ly; y++) {
					if (col || y >= ly - 1) {
						d.set(x, y, zf, y == -1 ? d.mat(Role.PLINTH).full() : col && y < ly - 1 ? column : render);
					}
				}
				if (!col) {
					boolean afterCol = (x - x0) % 3 == 1;
					d.set(x, ly - 2, zf, dress.stairsOrSlab(afterCol ? Dirs.LEFT : Dirs.RIGHT, Half.TOP));
				}
				// the loggia's side wall at the left, an arch through it
				for (int z = zf + 1; z < zm && x == x0; z++) {
					for (int y = -1; y <= ly; y++) {
						d.set(x, y, z, y == -1 ? d.mat(Role.PLINTH).full() : y >= 3 ? render : column);
					}
				}
			}
		}
		d.carve(x0 + 1, 0, zf + 1, x1, ly - 1, zm - 1);
		d.carve(x0 + 1, 0, zf, x1 - 1, ly - 3, zf);
		try (var s = d.stage(StageKind.ROOF)) {
			for (int z = zf; z < zm; z++) {
				for (int x = x0; x <= x1; x++) {
					d.set(x, ly + 1, z, ceiling.full());
					if (z > zf && x > x0) {
						d.set(x, ly, z, ceiling.full());
					}
				}
			}
		}
		// the balustrade round the terrace
		Mat wall = d.mat(Role.WALL);
		try (var s = d.stage(StageKind.ROOF)) {
			for (int x = x0; x <= x1; x++) {
				d.set(x, ly + 2, zf, (x - x0) % 3 == 0 ? column : wall.has(Form.WALL) ? wall.wall() : dress.slab(SlabType.BOTTOM));
			}
			for (int z = zf + 1; z < zm; z++) {
				d.set(x0, ly + 2, z, wall.has(Form.WALL) ? wall.wall() : dress.slab(SlabType.BOTTOM));
			}
		}
		for (int x = x0 + 2; x < x1; x += 3) {
			Lighting.chandelier(d, x, zf + 1, ly, ly - 1);
		}
		Lighting.room(d, x0 + 1, zf, x1, zm - 1, 0);
	}

	/**
	 * The campanile at the front corner: a shaft of the render with dressed quoins, plain to the belfry two from the top
	 * (an arched opening a side with a lantern hung in it), a corbelled cornice, a low pyramid cap of the tiles.
	 */
	private static void campanile(Draft d, int x0, int zf, int x1, int top, Glow g) {
		Mat dress = Rock.dressing(d);
		Paint render = DeepManner.home(d, Role.WALL).orElse(d.mat(Role.WALL).full());
		Paint quoin = DeepManner.home(d, Role.WALL_ACCENT).orElse(dress.full());
		int tw = x1 - x0 + 1;
		int z1 = zf + tw - 1;
		int capH = 2;
		int wallTop = top - capH;
		int belfry = wallTop - 3;
		int mx = (x0 + x1) / 2;
		int mz = (zf + z1) / 2;
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = zf; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					boolean edge = x == x0 || x == x1 || z == zf || z == z1;
					boolean corner = (x == x0 || x == x1) && (z == zf || z == z1);
					for (int y = -1; y <= wallTop; y++) {
						if (!edge && y > 0 && y < belfry - 1) {
							// solid core under the belfry: the bell ropes go down a shaft nobody climbs
							d.set(x, y, z, d.mat(Role.FOUNDATION).has(Form.FULL) ? d.mat(Role.FOUNDATION).full() : render);
							continue;
						}
						if (!edge && y >= belfry - 1 && y < wallTop) {
							continue;
						}
						d.set(x, y, z, y == -1 ? d.mat(Role.PLINTH).full() : corner || y == belfry - 1 || y == wallTop ? quoin : render);
					}
				}
			}
			// the belfry floor
			for (int z = zf + 1; z < z1; z++) {
				for (int x = x0 + 1; x < x1; x++) {
					d.set(x, belfry - 1, z, d.mat(Role.FLOOR).full());
				}
			}
		}
		// the belfry's arches, one a side
		for (Direction face : new Direction[]{Dirs.FRONT, Dirs.LEFT, Dirs.RIGHT, Dirs.BACK}) {
			int fx = face == Dirs.LEFT ? x0 : face == Dirs.RIGHT ? x1 : mx;
			int fz = face == Dirs.FRONT ? zf : face == Dirs.BACK ? z1 : mz;
			try (var s = d.stage(StageKind.OPENINGS)) {
				d.set(fx, belfry, fz, Blocks.AIR.defaultBlockState());
				d.set(fx, belfry + 1, fz, Blocks.AIR.defaultBlockState());
			}
		}
		d.carve(x0 + 1, belfry, zf + 1, x1 - 1, wallTop - 1, z1 - 1);
		Lighting.chandelier(d, mx, mz, wallTop, wallTop - 1);
		// the cornice: corbels round the top
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] p : Draft.ring(x0 - 1, zf - 1, x1 + 1, z1 + 1)) {
				int x = p[0];
				int z = p[1];
				if (x < 0 || x >= d.frontage() || z < 0 || !d.isAbsent(x, wallTop, z)) {
					continue;
				}
				Direction to = x < x0 ? Dirs.RIGHT : x > x1 ? Dirs.LEFT : z < zf ? Dirs.BACK : Dirs.FRONT;
				d.set(x, wallTop, z, dress.stairsOrSlab(to, Half.TOP));
			}
		}
		Mat roofM = d.mat(Role.ROOF);
		int cx0 = Math.max(0, x0 - 1);
		int cx1 = Math.min(d.frontage() - 1, x1 + 1);
		Roofs.Roof cap = Roofs.raster(d, Roofs.hip(cx0, zf - 1, cx1, z1 + 1, Roofs.Profile.straight(0.6)), wallTop + 1, cx0, Math.max(0, zf - 1), cx1, z1 + 1,
			roofM, d.mat(Role.ROOF_TRIM), Habits.Ridge.SLAB);
		DeepManner.rehome(d, cx0, wallTop + 1, 0, cx1, top, z1 + 1, roofM, Role.ROOF);
		if (cap.ridgeY + 1 <= top) {
			try (var s = d.stage(StageKind.ROOF)) {
				d.set(mx, cap.ridgeY + 1, mz, g.lamp.defaultBlockState());
			}
		}
	}
}
