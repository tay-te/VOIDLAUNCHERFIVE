package dev.voidmc.hearth.build.kit.noble;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Gates. Every gate here runs from the front of the plot toward the back (local z), as a lord's seat faces the road up
 * to it: a passage under a round-shouldered arch at each end, paved, a raised portcullis showing its teeth inside the
 * outer arch, the gate leaves folded back against the passage walls, a lantern in the vault; above it the gate hall at
 * the wall walk's level, reached from the walks; a head over that in the culture's manner. {@link #gatehouse} flanks
 * the gate with two round (or square) towers standing out into the field; {@link #barbican} walls a forecourt in front
 * of it with an outer gate of its own.
 */
public final class Gate {
	private Gate() {
	}

	/** What a gate drew: the passage's columns and the front and back cells outside it. */
	public record Built(int px0, int px1, int z0, int z1, int level, int hall, Tower body) {
		public int mid() {
			return (this.px0 + this.px1) / 2;
		}
	}

	/**
	 * A square gate tower over the box (x0..x1 across, z0..z1 deep) with a passage {@code pw} wide through its middle at
	 * standing level {@code level}, its gate hall at {@code hall} (the walk's level), its head at {@code top}.
	 */
	public static Built tower(Draft d, Dress m, Manner man, int x0, int z0, int x1, int z1, int pw, int level, int hall, int top, int maxTop) {
		int c = (x0 + x1) / 2;
		int px0 = c - pw / 2;
		int px1 = px0 + pw - 1;
		Manner.Cap cap = man.cap() == Manner.Cap.HOARD || man.cap() == Manner.Cap.CONE ? (man.hoardings() > 0.5 ? Manner.Cap.HOARD : Manner.Cap.PYRAMID)
			: man.cap() == Manner.Cap.DOME ? Manner.Cap.CRENEL : man.cap();
		Tower t = Tower.square(x0, z0, x1, z1, level, top, cap);
		t.footings(d, m);
		t.shell(d, m, top - 1, hall);
		passage(d, m, px0, px1, z0, z1, level, hall);
		// the gate hall and the floor over it (the tower's inside beside the passage is solid below the hall)
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = z0 + 1; z < z1; z++) {
				for (int x = x0 + 1; x < x1; x++) {
					if (x >= px0 && x <= px1) {
						continue;
					}
					for (int y = level; y < hall - 1; y++) {
						d.set(x, y, z, m.wall.full());
					}
				}
			}
		}
		// a ladder up from the hall to the head, in the back corner, through a hatch in every floor over the hall: the
		// tower keeps the well open, so the deck (or the top room's floor, under any cap) is cut round its last rung
		// (a curved hip springs from the deck itself and leaves no room over it: there the gate hall rises to the roof)
		int lx = x1 - 1;
		int lz = z1 - 1;
		if (cap == Manner.Cap.PAGODA) {
			t.floors(d, m, top, hall);
		} else {
			t.ladderWell(lx, lz);
			t.floors(d, m, top, hall, hall + 4 < top - 1 ? hall + 4 : hall);
			try (var s = d.stage(StageKind.FLOORS)) {
				for (int y = hall; y < top; y++) {
					d.set(lx, y, lz, States.facing(Blocks.LADDER, Dirs.FRONT));
				}
				d.set(lx, hall - 1, lz, m.floor.full());
			}
		}
		walkDoors(d, m, x0, z0, x1, z1, hall);
		t.loops(d, java.util.Set.of(Dirs.FRONT), hall + 1, top);
		t.window(d, m, Dirs.BACK, hall + 1, 2);
		t.head(d, m, man, maxTop);
		Lighting.room(d, x0 + 1, z0 + 1, x1 - 1, z1 - 1, hall);
		return new Built(px0, px1, z0, z1, level, hall, t);
	}

	/**
	 * The passage through a gate from z0 to z1 (inclusive), columns px0..px1: paved at {@code level}, open to the vault at
	 * {@code hall - 1}, round-shouldered arches at both ends, the portcullis raised inside the outer arch, the gate leaves
	 * folded back, a lantern hung in the vault, the GATE point and a guard post at the mouth.
	 */
	public static void passage(Draft d, Dress m, int px0, int px1, int z0, int z1, int level, int hall) {
		BlockState air = Blocks.AIR.defaultBlockState();
		int vault = hall - 1;
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = px0; x <= px1; x++) {
					for (int y = level; y < vault; y++) {
						d.set(x, y, z, air);
					}
					d.set(x, vault, z, (z == z0 || z == z1) && x == (px0 + px1) / 2 ? m.dress.full() : m.wall.full());
				}
				// the walls of the passage
				for (int x : new int[]{px0 - 1, px1 + 1}) {
					for (int y = level - 1; y <= vault; y++) {
						if (d.isAbsent(x, y, z) || d.isEmpty(x, y, z)) {
							d.set(x, y, z, m.wall.full());
						}
					}
				}
			}
			for (int z : new int[]{z0, z1}) {
				if (m.dress.has(Form.STAIRS) && px1 > px0) {
					d.set(px0, vault - 1, z, m.dress.stairs(Dirs.LEFT, Half.TOP));
					d.set(px1, vault - 1, z, m.dress.stairs(Dirs.RIGHT, Half.TOP));
				}
				for (int x = px0 - 1; x <= px1 + 1; x++) {
					d.set(x, vault, z, m.dress.full());
				}
			}
		}
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = px0; x <= px1; x++) {
					d.set(x, level - 1, z, m.paving.full());
				}
			}
		}
		try (var s = d.stage(StageKind.OPENINGS)) {
			if (z1 - z0 >= 3) {
				for (int x = px0; x <= px1; x++) {
					d.set(x, vault - (px1 > px0 && (x == px0 || x == px1) ? 2 : 1), z0 + 1, Blocks.IRON_BARS.defaultBlockState());
				}
			}
			var door = d.mat(Role.DOOR);
			if (door.has(Form.TRAPDOOR) && z1 - z0 >= 4) {
				for (int y = level; y < Math.min(level + 3, vault - 1); y++) {
					d.set(px0, y, z0 + 2, door.trapdoor(Dirs.RIGHT, Half.BOTTOM, true));
					d.set(px1, y, z0 + 2, door.trapdoor(Dirs.LEFT, Half.BOTTOM, true));
				}
			}
		}
		int cx = (px0 + px1) / 2;
		int cz = (z0 + z1) / 2;
		if (vault - level >= 4) {
			Lighting.chandelier(d, cx, cz, vault, vault - 1);
		}
		d.poi(PoiType.GATE, cx, level, cz, Dirs.FRONT);
	}

	/**
	 * A twin-towered gatehouse: the gate block over the box (x0..x1, z0..z1), the passage {@code pw} wide through its
	 * middle, and two towers of diameter {@code td} centred on its front corners, standing out into the field (round where
	 * the manner builds round towers, else square): their ground floors are guard rooms entered from the passage (the
	 * GUARD's grindstone in one), their stairs climb past the gate hall (which they open into) to their heads.
	 * Returns the gate; the two towers are {@code towers[0]} (left) and {@code towers[1]}.
	 */
	public static Built gatehouse(Draft d, Dress m, Manner man, int x0, int z0, int x1, int z1, int pw, int level, int hall, int top, int maxTop,
		boolean roundTowers, int td, Tower[] towers) {
		Built b = tower(d, m, man, x0, z0, x1, z1, pw, level, hall, top, maxTop);
		int r = td / 2;
		int towerTop = Math.min(top + 2, maxTop - Math.min(9, Tower.headRows(flankCap(man), td, man)));
		towerTop = Math.max(hall + 4, towerTop);
		for (int side : new int[]{-1, 1}) {
			int cx = side < 0 ? x0 : x1;
			int cz = z0;
			Tower t = roundTowers ? Tower.round(cx, cz, td, level, towerTop, flankCap(man))
				: Tower.square(cx - r, cz - r, cx + r, cz + r, level, towerTop, flankCap(man));
			t.footings(d, m);
			t.shell(d, m, towerTop - 1, hall);
			t.stair(d, m, man.timberTops(), side < 0 ? Dirs.LEFT : Dirs.BACK);
			t.floors(d, m, towerTop, hall);
			// the guard room's door from the passage
			Direction toPassage = side < 0 ? Dirs.RIGHT : Dirs.LEFT;
			int[] f = t.face(toPassage);
			if (f[0] >= b.px0() - 1 && f[0] <= b.px1() + 1) {
				t.door(d, m, toPassage, level, 1, true);
			}
			// a doorway at the hall's level out onto the curtain's walk where it runs into the tower's flank, so
			// the walk on each side of the gate is joined through the tower and the gate hall
			t.doorToward(d, m, side < 0 ? Dirs.LEFT : Dirs.RIGHT, hall, false);
			t.loops(d, java.util.Set.of(Dirs.FRONT, side < 0 ? Dirs.LEFT : Dirs.RIGHT), level + 1, towerTop);
			t.window(d, m, side < 0 ? Dirs.LEFT : Dirs.RIGHT, hall + 1, 2);
			t.head(d, m, man, maxTop);
			var room = t.room(level);
			if (side < 0) {
				dev.voidmc.hearth.build.kit.Rooms.furnish(d, room, dev.voidmc.hearth.build.kit.Rooms.Type.WORKSHOP, 0, JobType.GUARD);
			} else {
				dev.voidmc.hearth.build.kit.Rooms.furnish(d, room, dev.voidmc.hearth.build.kit.Rooms.Type.BEDROOM, 2, JobType.NONE);
			}
			t.light(d, towerTop - 1);
			t.watch(d, Dirs.FRONT);
			if (towers != null) {
				towers[side < 0 ? 0 : 1] = t;
			}
		}
		return b;
	}

	private static Manner.Cap flankCap(Manner man) {
		return man.cap() == Manner.Cap.PAGODA ? Manner.Cap.PAGODA : man.cap() == Manner.Cap.HOARD ? Manner.Cap.HOARD : man.cap();
	}

	/**
	 * A guard room in the solid flank of a gate block beside the passage: carved out of {@code (x0..x1, z0..z1)} at
	 * standing level {@code level}, three high, a door from the court at its back, the GUARD's grindstone, a chest of
	 * arrows, a bench and a lamp.
	 */
	public static void guardRoom(Draft d, Dress m, int x0, int z0, int x1, int z1, int level, Direction doorSide) {
		BlockState air = Blocks.AIR.defaultBlockState();
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					for (int y = level; y < level + 3; y++) {
						d.set(x, y, z, air);
					}
				}
			}
		}
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					d.set(x, level - 1, z, m.paving.full());
				}
			}
		}
		int dx = doorSide == Dirs.BACK ? (x0 + x1) / 2 : doorSide == Dirs.LEFT ? x0 - 1 : x1 + 1;
		int dz = doorSide == Dirs.BACK ? z1 + 1 : (z0 + z1) / 2;
		var doorBlock = d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR);
		d.inStage(StageKind.OPENINGS, () -> d.door(dx, level, dz, doorBlock, doorSide, net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT));
		int gx = doorSide == Dirs.LEFT ? x1 : x0;
		int gz = z0;
		Furniture.workstation(d, JobType.GUARD, gx, level, gz, x1 > x0 ? (gx == x0 ? Dirs.RIGHT : Dirs.LEFT) : Dirs.BACK);
		if (z1 > z0 + 1) {
			Furniture.chest(d, gx, level, z0 + 1, gx == x0 ? Dirs.RIGHT : Dirs.LEFT, List.of(new Object[]{"minecraft:arrow", 24}, new Object[]{"minecraft:bread", 4}));
		}
		Lighting.room(d, x0, z0, x1, z1, level);
	}

	/**
	 * A barbican: a walled forecourt in front of a gate, from the front of the plot ({@code zf}) back to the gate's face
	 * at {@code zg} (exclusive), {@code bw} wide centred on {@code cx}; walls {@code thick} thick standing {@code h} over
	 * the court with crenellations, little round turrets (or square ones) at the outer corners, and the outer gate in the
	 * front wall, {@code pw} wide, at standing level {@code level}. Returns the outer gate's middle column.
	 */
	public static int barbican(Draft d, Dress m, Manner man, int cx, int bw, int zf, int zg, int pw, int level, int h, int maxTop, boolean roundTurrets) {
		int x0 = cx - bw / 2;
		int x1 = x0 + bw - 1;
		int walk = level + h;
		Curtain.Opts o = new Curtain.Opts(false, true, false);
		// the side walls run from the front to the gate; the front wall across
		Curtain.run(d, m, man, x0, zf, x0, zg - 1, Dirs.LEFT, 2, walk, o);
		Curtain.run(d, m, man, x1, zf, x1, zg - 1, Dirs.RIGHT, 2, walk, o);
		Curtain.run(d, m, man, x0, zf, x1, zf, Dirs.FRONT, 2, walk, o);
		// the outer gate: an arch through the front wall
		int px0 = cx - pw / 2;
		int px1 = px0 + pw - 1;
		BlockState air = Blocks.AIR.defaultBlockState();
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = zf; z <= zf + 1; z++) {
				for (int x = px0; x <= px1; x++) {
					for (int y = level; y < level + 4 && y < walk; y++) {
						d.set(x, y, z, air);
					}
				}
				if (m.dress.has(Form.STAIRS) && px1 > px0) {
					d.set(px0, level + 3, z, m.dress.stairs(Dirs.LEFT, Half.TOP));
					d.set(px1, level + 3, z, m.dress.stairs(Dirs.RIGHT, Half.TOP));
				}
			}
			// a gable of the dressing over the arch, its merlons stepped up
			for (int x = px0 - 1; x <= px1 + 1; x++) {
				d.set(x, level + 4, zf, m.dress.full());
			}
		}
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = zf; z < zg; z++) {
				for (int x = x0 + 2; x <= x1 - 2; x++) {
					d.set(x, level - 1, z, m.paving.full());
				}
			}
			for (int z = zf; z <= zf + 1; z++) {
				for (int x = px0; x <= px1; x++) {
					d.set(x, level - 1, z, m.paving.full());
				}
			}
		}
		// the court cleared to the sky
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = zf + 2; z < zg; z++) {
				for (int x = x0 + 2; x <= x1 - 2; x++) {
					for (int y = level; y <= level + 5; y++) {
						d.set(x, y, z, air);
					}
				}
			}
		}
		// corner turrets
		int td = 5;
		int tt = Math.min(walk + 3, maxTop - Tower.headRows(man.cap(), td, man));
		for (int side : new int[]{-1, 1}) {
			int tx = side < 0 ? x0 + 1 : x1 - 1;
			int tz = zf + 1;
			// the barbican's turrets stand open behind their battlements (a dome on a garita, a curved roof in the karst)
			Manner.Cap tc = man.cap() == Manner.Cap.DOME || man.cap() == Manner.Cap.PAGODA ? man.cap() : Manner.Cap.CRENEL;
			Tower t = roundTurrets ? Tower.round(tx, tz, td, walk, Math.max(walk + 1, tt), tc)
				: Tower.square(tx - 2, tz - 2, tx + 2, tz + 2, walk, Math.max(walk + 1, tt), tc);
			// solid below the walk: a turret is a bastion carrying a lookout
			try (var s = d.stage(StageKind.WALLS)) {
				for (int[] c : t.cells()) {
					if (!Mason.inPlot(d, c[0], c[1])) {
						continue;
					}
					Mason.footing(d, m, c[0], c[1], Math.min(d.site().ground(c[0], c[1]), walk - 2));
					for (int y = Math.min(d.site().ground(c[0], c[1]), walk - 2); y < walk; y++) {
						d.set(c[0], y, c[1], y <= d.site().ground(c[0], c[1]) ? m.plinth.full() : m.wall.full());
					}
				}
			}
			t.shell(d, m, Math.max(walk, tt - 1));
			t.floors(d, m, Math.max(walk + 1, tt));
			t.head(d, m, man, maxTop);
		}
		// a lamp either side of the outer gate
		Lighting.doorLamp(d, px0 - 1, level + 2, zf - 1, Dirs.BACK);
		Lighting.doorLamp(d, px1 + 1, level + 2, zf - 1, Dirs.BACK);
		d.poi(PoiType.GATE, cx, level, zf, Dirs.FRONT);
		d.poi(PoiType.GUARD_POST, cx, level, zf + 2, Dirs.FRONT);
		return cx;
	}

	/**
	 * Doorways from the gate hall (standing level {@code hall}) onto the wall walks that meet the gate tower's flanks
	 * (x0 and x1): on each flank, the row nearest the back where the walk outside can be stood on (drawn before the gate,
	 * as the curtains are) gets an arched opening two high, so the hall is reached from the walks and the walks run on
	 * through it.
	 */
	static void walkDoors(Draft d, Dress m, int x0, int z0, int x1, int z1, int hall) {
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int side : new int[]{-1, 1}) {
			int x = side < 0 ? x0 : x1;
			int ox = x + side;
			int ix = x - side;
			for (int z = z1 - 1; z > z0; z--) {
				if (!d.isSolid(ox, hall - 1, z) || !d.isEmpty(ox, hall, z) || !d.isEmpty(ox, hall + 1, z) || !d.isSolid(ix, hall - 1, z)) {
					continue;
				}
				try (var s = d.stage(StageKind.WALLS)) {
					d.set(x, hall, z, air);
					d.set(x, hall + 1, z, air);
					d.set(x, hall - 1, z, m.paving.full());
					if (Mason.ok(d, x, hall + 2, z)) {
						d.set(x, hall + 2, z, m.dress.full());
					}
				}
				break;
			}
		}
	}

	/** A slab of the paving, for thresholds. */
	static Paint threshold(Dress m) {
		return m.paving.has(Form.SLAB) ? m.paving.slab(SlabType.BOTTOM) : m.paving.full();
	}
}
