package dev.voidmc.hearth.build.kit.deep;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.BlockEntities;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The moot: how the deep hall is furnished inside whatever shell its culture raises round it (a basilica's nave, a stave
 * hall, a timber hall on its terrace, the domed hall behind a court, a pueblo's great room, a palazzo's hall, a hall on
 * stilts). One room facing its doors: a runner from the doors to a dais at the far end, the reeve's high table across
 * the dais with his chair behind it, his lectern beside it and the town chest by his chair, banners on the wall behind;
 * long tables and benches down the room; the fire (a hearth in a side wall with its stack, or a long fire in the middle
 * of the floor in the northern halls); the town's records in chests and shelves along the other wall; lanterns hung from
 * the tie beams and the room lit to the rule.
 *
 * <p>Registers MEETING_POINT, JOB_SITE REEVE and LECTERN, TABLE, SEAT, THRONE, STRONGBOX, STORAGE and HEARTH.
 */
public final class Moot {
	private Moot() {
	}

	/** Where the fire goes. */
	public enum Fire {
		/** A hearth in the left wall, its stack up through the wall column behind it to {@link Room#stackTop}. */
		WALL,
		/** A long fire in a stone-kerbed pit down the middle of the floor (the north's halls). */
		LONG,
		/** A brazier: a fire on a stone block in a corner before the dais (the halls that raise no stack). */
		BRAZIER
	}

	/**
	 * The room: its inside {@code x0..x1 × z0..z1} (walls round it, the doors in the front wall at {@code z0 - 1}), the
	 * level folk stand at, the course the lanterns hang from ({@code ceil}: a beam or ceiling block is drawn there where
	 * a lantern hangs, if nothing is), the door's column, the fire, and for a wall hearth the top of its stack.
	 */
	public record Room(int x0, int z0, int x1, int z1, int y, int ceil, int door, Fire fire, int stackTop) {
		int width() {
			return this.x1 - this.x0 + 1;
		}
	}

	public static void furnish(Draft d, Room r, Glow g) {
		Mat furniture = d.mat(Role.FURNITURE);
		Mat dress = Rock.dressing(d);
		int y = r.y;
		int c = r.door;
		boolean deep = r.z1 - r.z0 >= 9;
		// the dais: the far rows raised a step, stairs across its front
		int dz0 = deep ? Math.max(r.z0 + 5, r.z1 - 3) : r.z1;
		int top = y;
		if (deep) {
			try (var s = d.stage(StageKind.FLOORS)) {
				for (int z = dz0; z <= r.z1; z++) {
					for (int x = r.x0; x <= r.x1; x++) {
						d.set(x, y, z, dress.full());
					}
				}
				for (int x = r.x0; x <= r.x1; x++) {
					d.set(x, y, dz0 - 1, dress.stairsOrSlab(Dirs.BACK, Half.BOTTOM));
				}
			}
			top = y + 1;
		}
		// the high table across the dais, the reeve's chair behind it, his lectern beside, the town chest by the chair
		int tz = deep ? Math.min(r.z1 - 1, dz0 + 1) : r.z1 - 1;
		int th = r.width() >= 11 ? 2 : 1;
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int x = c - th; x <= c + th; x++) {
				d.set(x, top, tz, x == c - th || x == c + th ? furniture.fence() : furniture.slab(SlabType.TOP));
			}
		}
		d.poi(PoiType.TABLE, c, top, tz - 1, Dirs.BACK);
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(c, top, tz + 1, furniture.stairsOrSlab(Dirs.BACK, Half.BOTTOM));
		}
		d.poi(PoiType.SEAT, c, top, tz + 1, Dirs.FRONT);
		d.poi(PoiType.THRONE, c, top, tz + 1, Dirs.FRONT);
		int lx = c + th + 1 <= r.x1 ? c + th + 1 : c - th - 1;
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(lx, top, tz, States.facing(Blocks.LECTERN, Dirs.FRONT));
		}
		d.poi(PoiType.JOB_SITE, lx, top, tz - 1, Dirs.BACK, JobType.REEVE);
		d.poi(PoiType.LECTERN, lx, top, tz - 1, Dirs.BACK);
		int ox = lx > c ? c - th - 1 : c + th + 1;
		if (ox >= r.x0 && ox <= r.x1) {
			try (var s = d.stage(StageKind.INTERIOR)) {
				d.set(ox, top, tz + 1, States.facing(Blocks.CHEST, Dirs.FRONT), BlockEntities.container(List.of(new Object[]{"minecraft:gold_ingot", 6},
					new Object[]{"minecraft:raw_gold", 9}, new Object[]{"minecraft:emerald", 4}, new Object[]{"minecraft:paper", 8})));
			}
			d.poi(PoiType.STRONGBOX, ox, top, tz, Dirs.BACK);
		}
		Lighting.candles(d, c - th, top + 1, tz, 3);
		Lighting.candles(d, c + th, top + 1, tz, 2);
		Block banner = d.mat(Role.CLOTH).main(Form.WALL_BANNER);
		if (banner != null) {
			try (var s = d.stage(StageKind.INTERIOR)) {
				for (int x : new int[]{c - 2, c + 2}) {
					if (x >= r.x0 && x <= r.x1 && d.isSolid(x, top + 3, r.z1 + 1) && d.isAbsent(x, top + 3, r.z1)) {
						d.set(x, top + 3, r.z1, States.facing(banner, Dirs.FRONT));
					}
				}
			}
		}
		// the fire
		int fz = r.z0 + Math.max(2, (dz0 - r.z0) / 2);
		boolean longFire = r.fire == Fire.LONG && dz0 - r.z0 >= 7;
		if (longFire) {
			fire(d, r, c, fz);
		}
		// the runner from the doors to the dais (to the long fire's kerb where one burns)
		Mat cloth = d.mat(Role.CLOTH);
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int z = r.z0; z < dz0 - 1; z++) {
				if (longFire && z >= fz - 2 && z <= fz + 2) {
					continue;
				}
				d.set(c, y, z, cloth.carpet());
			}
		}
		d.poi(PoiType.MEETING_POINT, c, y, longFire ? fz - 2 : (r.z0 + dz0) / 2, Dirs.BACK);
		// long tables and benches down the room either side of the runner
		int[] tables = r.width() >= 9 ? new int[]{c - 2 - (r.width() >= 13 ? 1 : 0), c + 2 + (r.width() >= 13 ? 1 : 0)}
			: new int[]{c + 2 <= r.x1 - 1 ? c + 2 : c - 2};
		for (int tx : tables) {
			int z0 = r.z0 + 2;
			int z1 = dz0 - 3;
			if (z1 - z0 < 2 || tx - 1 < r.x0 && tx + 1 > r.x1) {
				continue;
			}
			Furniture.longTable(d, tx, y, z0, Dirs.BACK, z1 - z0 + 1);
			for (int z = z0; z <= z1; z++) {
				for (int sx : new int[]{tx - 1, tx + 1}) {
					if (sx >= r.x0 && sx <= r.x1 && sx != c && !(longFire && Math.abs(sx - c) <= 1)) {
						try (var s = d.stage(StageKind.INTERIOR)) {
							d.set(sx, y, z, furniture.stairsOrSlab(sx < tx ? Dirs.LEFT : Dirs.RIGHT, Half.BOTTOM));
						}
						if ((z - z0) % 2 == 0) {
							d.poi(PoiType.SEAT, sx, y, z, sx < tx ? Dirs.RIGHT : Dirs.LEFT);
						}
					}
				}
				if ((z - z0) % 3 == 1) {
					Lighting.candles(d, tx, y + 1, z, 2);
				}
			}
		}
		if (r.fire == Fire.WALL) {
			int hz = Math.min(dz0 - 2, r.z0 + 3);
			Furniture.hearth(d, r.x0, y, hz, Dirs.RIGHT, true);
			Mat chimney = d.mat(Role.CHIMNEY);
			try (var s = d.stage(StageKind.WALLS)) {
				for (int yy = y + 1; yy <= r.stackTop; yy++) {
					d.set(r.x0 - 1, yy, hz, chimney.full());
					if (yy >= y + 2 && yy < r.ceil) {
						d.set(r.x0, yy, hz, chimney.full());
					}
				}
			}
			if (d.allowed(r.x0 - 1, r.stackTop + 1, hz)) {
				try (var s = d.stage(StageKind.ROOF)) {
					d.set(r.x0 - 1, r.stackTop + 1, hz, States.lit(Blocks.CAMPFIRE, true).trySetValue(BlockStateProperties.SIGNAL_FIRE, false));
				}
			}
			Lighting.flank(d, r.x0, y + 2, hz, Dirs.LEFT, 2);
		} else if (r.fire == Fire.BRAZIER || r.fire == Fire.LONG && !longFire) {
			// a brazier on a stone block before the dais
			int bx = r.x0;
			int bz = deep ? dz0 - 2 : r.z0 + 1;
			try (var s = d.stage(StageKind.INTERIOR)) {
				d.set(bx, y, bz, dress.full());
				d.set(bx, y + 1, bz, States.lit(Blocks.CAMPFIRE, true).trySetValue(BlockStateProperties.SIGNAL_FIRE, false));
			}
			d.poi(PoiType.HEARTH, bx + 1, y, bz, Dirs.LEFT);
		}
		// the town's records along the right wall
		int rx = r.x1;
		for (int z = r.z0 + 2; z + 1 < dz0 - 1; z += 4) {
			if (!d.isAbsent(rx, y, z) || !d.isAbsent(rx, y, z + 1)) {
				continue;
			}
			Furniture.chest(d, rx, y, z, Dirs.LEFT, List.of(new Object[]{"minecraft:paper", 12}, new Object[]{"minecraft:book", 3},
				new Object[]{"minecraft:gold_nugget", 9}));
			Furniture.bookshelf(d, rx, y, z + 1);
		}
		// lanterns from the beams down the middle, then the rule
		Mat beam = d.mat(Role.BEAM);
		int hub = Math.max(y + 3, r.ceil - 3);
		for (int z = r.z0 + 2; z < dz0 - 1; z += 4) {
			int lz = longFire && Math.abs(z - fz) <= 1 ? z + 2 : z;
			if (lz >= dz0 - 1) {
				continue;
			}
			if (d.isAbsent(c, r.ceil, lz)) {
				// a tie beam out from the middle each way to the first thing it meets (a wall, the roof's underside); laid
				// only where both ends rest on something, never out through a low roof into the open
				int a = c;
				while (a - 1 >= r.x0 - 1 && d.isAbsent(a - 1, r.ceil, lz) && covered(d, a - 1, r.ceil, lz)) {
					a--;
				}
				int b = c;
				while (b + 1 <= r.x1 + 1 && d.isAbsent(b + 1, r.ceil, lz) && covered(d, b + 1, r.ceil, lz)) {
					b++;
				}
				if (d.isSolid(a - 1, r.ceil, lz) && d.isSolid(b + 1, r.ceil, lz)) {
					try (var s = d.stage(StageKind.FRAME)) {
						for (int x = a; x <= b; x++) {
							d.set(x, r.ceil, lz, beam.log(Direction.Axis.X));
						}
					}
				}
			}
			if (!d.isSolid(c, r.ceil, lz)) {
				continue;
			}
			if (r.width() >= 7 && r.ceil - y >= 6) {
				Lighting.wheel(d, c, lz, r.ceil, hub);
			} else {
				Lighting.chandelier(d, c, lz, r.ceil, Math.max(y + 2, r.ceil - 2));
			}
		}
		Lighting.room(d, r.x0, r.z0, r.x1, dz0 - 2, y);
		Lighting.room(d, r.x0, dz0, r.x1, r.z1, top);
	}

	/** Whether something is built over (x, y, z): the cell is under a roof, not out in the open. */
	private static boolean covered(Draft d, int x, int y, int z) {
		for (int k = 1; y + k < d.maxHeight(); k++) {
			if (d.isSolid(x, y + k, z)) {
				return true;
			}
		}
		return false;
	}

	/** The long fire: a pit two long in the middle of the floor, kerbed in stone, its fire burning low in it. */
	private static void fire(Draft d, Room r, int c, int fz) {
		Mat dress = Rock.dressing(d);
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int z = fz - 1; z <= fz + 1; z++) {
				for (int x = c - 1; x <= c + 1; x++) {
					boolean pit = x == c && z != fz + 1;
					if (pit) {
						d.set(x, r.y, z, States.lit(Blocks.CAMPFIRE, true).trySetValue(BlockStateProperties.SIGNAL_FIRE, false));
					} else {
						d.set(x, r.y, z, dress.slab(SlabType.BOTTOM));
					}
				}
			}
		}
		d.poi(PoiType.HEARTH, c, r.y, fz - 2, Dirs.BACK);
	}
}
