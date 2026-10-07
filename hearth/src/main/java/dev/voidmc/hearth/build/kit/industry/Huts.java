package dev.voidmc.hearth.build.kit.industry;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.homestead.Round;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.kit.trade.Props;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;
import org.jspecify.annotations.Nullable;

/**
 * The worker's hut that stands by a clamp, a pan or a landing. In the forest cultures the charcoal burner's hut of
 * Central Europe: poles leant together into a steep cone, shingled with bark (or thatched) down to the ground, a little
 * gabled porch over the door. In the thatch cultures a round mud hut under its cone; elsewhere a cell of the culture's
 * walls under its own roof. One room: a bed, a chest, a lamp; the door toward the yard.
 */
public final class Huts {
	private Huts() {
	}

	/** A finished hut: its room, its door, the cell outside it, its highest block. */
	public record Hut(Rooms.Room room, int doorX, int doorZ, int outX, int outZ, int top) {
	}

	/** Which hut a culture builds. */
	public enum Kind {
		CONE, ROUND_MUD, CELL
	}

	public static Kind kind(Draft d) {
		return switch (Works.tradition(d)) {
			case TIMBER, MARSH, EAST -> Kind.CONE;
			case THATCH -> Kind.ROUND_MUD;
			default -> Kind.CELL;
		};
	}

	/** The footprint radius a hut of this kind takes. */
	public static double radius(Kind kind) {
		return kind == Kind.ROUND_MUD ? 2.5 : 2.0;
	}

	/**
	 * The burner's hut on whole centre (cx, cz): poles leant together into a steep A-frame five wide and five deep,
	 * shingled with bark (or thatched) down to the ground, its ridge running back from the door, the gable ends of logs
	 * stood on end with the door in the front one. Inside: the bed down the middle, a barrel under the low eave (a chest
	 * where the eave leaves its lid room), a lantern on a chain from the ridge. Returns the hut, or null without room under the plot's height.
	 */
	public static @Nullable Hut cone(Draft d, int cx, int cz, Direction door, boolean thatch) {
		int limit = d.maxHeight() - 1;
		double pitch = limit >= 4 ? 1.5 : 1.0;
		if (limit < 3) {
			return null;
		}
		// bark shingles: the culture's roofing where it is wooden, else its boards
		Mat cover = d.mat(Role.ROOF);
		if (!Works.isWood(cover.mainOr(Form.FULL, Blocks.STONE)) || !cover.has(Form.STAIRS)) {
			cover = Works.timber(d);
		}
		Mat th = d.mat(Role.THATCH);
		if (thatch && th.has(Form.STAIRS) && th.has(Form.SLAB)) {
			cover = th;
		}
		boolean alongZ = door.getAxis() == Direction.Axis.Z;
		int x0 = cx - 2;
		int x1 = cx + 2;
		int z0 = cz - 2;
		int z1 = cz + 2;
		Mat floor = d.mat(Role.FLOOR);
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int x = x0 + 1; x <= x1 - 1; x++) {
				for (int z = z0 + 1; z <= z1 - 1; z++) {
					d.set(x, -1, z, floor.full());
				}
			}
		}
		// the gable ends: logs stood on end, filled to the roof
		Mat log = Works.logs(d);
		Mat frame = d.mat(Role.FRAME);
		Roofs.Field f = Roofs.gable(x0, z0, x1, z1, alongZ ? Direction.Axis.Z : Direction.Axis.X, Roofs.Profile.straight(pitch));
		Roofs.Roof roof;
		try (var s = d.stage(StageKind.ROOF)) {
			roof = Roofs.raster(d, f, 0, x0, z0, x1, z1, cover, cover, Habits.Ridge.FULL);
		}
		try (var s = d.stage(StageKind.WALLS)) {
			for (int k = -1; k <= 1; k++) {
				for (int end : new int[]{-2, 2}) {
					int x = alongZ ? cx + k : cx + end;
					int z = alongZ ? cz + end : cz + k;
					int under = roof.bottomY(x, z);
					for (int y = 0; y < under; y++) {
						d.set(x, y, z, k == 0 && y >= 2 ? frame.log(Direction.Axis.Y) : log.log(Direction.Axis.Y));
					}
				}
			}
		}
		// inside, open to the roof
		for (int x = x0 + 1; x <= x1 - 1; x++) {
			for (int z = z0 + 1; z <= z1 - 1; z++) {
				int under = roof.bottomY(x, z);
				if (under > 0) {
					d.carve(x, 0, z, x, under - 1, z);
				}
			}
		}
		int px = cx + Dirs.dx(door) * 2;
		int pz = cz + Dirs.dz(door) * 2;
		Block doorBlock = d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR);
		try (var s = d.stage(StageKind.OPENINGS)) {
			d.door(px, 0, pz, doorBlock, door, DoorHingeSide.LEFT);
		}
		int ox = px + Dirs.dx(door);
		int oz = pz + Dirs.dz(door);
		d.poi(PoiType.DOOR, ox, 0, oz, door.getOpposite());
		// inside: the bed down the middle (its foot by the door), a chest under the eave, the lantern from the ridge
		int ix = cx + Dirs.dx(door);
		int iz = cz + Dirs.dz(door);
		Direction side = Dirs.right(door);
		Rooms.Room room = new Rooms.Room(Math.min(cx - 1, cx + 1), Math.min(cz - 1, cz + 1), Math.max(cx - 1, cx + 1), Math.max(cz - 1, cz + 1), 0, 2);
		room.keep(ix, iz);
		Block bed = d.mat(Role.CLOTH).mainOr(Form.BED, Blocks.BED.pick(net.minecraft.world.item.DyeColor.BROWN));
		d.inStage(StageKind.INTERIOR, () -> d.bed(cx, 0, cz, bed, door.getOpposite()));
		int chx = ix + Dirs.dx(side);
		int chz = iz + Dirs.dz(side);
		// under the low eave a chest's lid would strike the poles: a barrel, which opens from the side
		boolean low = roof.covers(chx, chz) && roof.bottomY(chx, chz) <= 1;
		Props.store(d, chx, 0, chz, side.getOpposite(), !low, List.of(new Object[]{"minecraft:bread", 4}, new Object[]{"minecraft:iron_shovel", 1}));
		int ceil = roof.bottomY(ix, iz);
		if (ceil - 1 >= 2) {
			Lighting.chandelier(d, ix, iz, ceil, ceil - 1);
		} else {
			Lighting.candles(d, chx, 1, chz, 2);
		}
		return new Hut(room, px, pz, ox, oz, roof.ridgeY);
	}

	/**
	 * A round mud hut on centre (cx, cz), radius r, under a thatch cone, its door toward {@code door}; a bed, a chest
	 * and a lamp inside. Returns the hut, or null if it did not fit under the plot's height.
	 */
	public static @Nullable Hut mud(Draft d, double cx, double cz, double r, Direction door) {
		Round.Ring ring = Round.ring(cx, cz, r);
		int plate = 2;
		if (plate + 4 > d.maxHeight() - 1) {
			return null;
		}
		Mat found = d.mat(Role.FOUNDATION);
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (int[] c : ring.disc()) {
				int g = d.site().ground(c[0], c[1]);
				for (int y = g - 1; y <= -2; y++) {
					if (d.isAbsent(c[0], y, c[1]) || d.isEmpty(c[0], y, c[1])) {
						d.set(c[0], y, c[1], found.full());
					}
				}
			}
		}
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int[] c : ring.disc()) {
				if (ring.inside(c[0], c[1])) {
					d.set(c[0], -1, c[1], d.mat(Role.PAVING).full());
				}
			}
		}
		Round.walls(d, ring, 0, plate);
		Mat cover = d.mat(Role.THATCH);
		if (!cover.has(Form.STAIRS)) {
			cover = d.mat(Role.ROOF);
		}
		Roofs.Roof roof = Round.cone(d, ring, plate, 1, 1.0, cover, false);
		int[] wc = ring.cellToward(door);
		if (wc == null) {
			return null;
		}
		Block doorBlock = d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR);
		try (var s = d.stage(StageKind.OPENINGS)) {
			d.set(wc[0], 0, wc[1], Blocks.AIR.defaultBlockState());
			d.set(wc[0], 1, wc[1], Blocks.AIR.defaultBlockState());
			d.door(wc[0], 0, wc[1], doorBlock, door, DoorHingeSide.LEFT);
		}
		int ox = wc[0] + Dirs.dx(door);
		int oz = wc[1] + Dirs.dz(door);
		d.poi(PoiType.DOOR, ox, 0, oz, door.getOpposite());
		Rooms.Room room = Round.room(ring, 0, 2);
		room.keep(wc[0] - Dirs.dx(door), wc[1] - Dirs.dz(door));
		Rooms.furnish(d, room, Rooms.Type.BEDROOM, 1, dev.voidmc.hearth.api.JobType.NONE);
		Lighting.room(d, room);
		return new Hut(room, wc[0], wc[1], ox, oz, roof != null ? roof.ridgeY + 2 : plate + 3);
	}

	/**
	 * A one-room cell of the culture's walls over [x0..x1] × [z0..z1] under its own low roof (flat where the culture
	 * builds flat), its door in the front wall at column {@code at}; furnished and lit. Returns the hut.
	 */
	public static Hut cell(Draft d, int x0, int z0, int x1, int z1, int at) {
		RoofForm form = Hall.flatCulture(d) ? RoofForm.FLAT : Hall.pick(d, RoofForm.HIP, RoofForm.GABLE, RoofForm.PAGODA);
		int[] heights = Hall.budget(d, x0, z0, x1, z1, 0, new int[]{4}, false, form, 1);
		if (form == RoofForm.FLAT) {
			heights = new int[]{3};
		}
		Hall h = Hall.of(d, x0, z0, x1, z1, 0, heights, false, false);
		h.facade(at, 1, 1, 0.3).ground(false).shell().openings(true);
		int[] in = h.inner(0);
		int dx = h.door != null ? h.door : at;
		int dz = h.rect(0)[1];
		h.roof(form, (x1 - x0) >= (z1 - z0) ? Direction.Axis.X : Direction.Axis.Z, 1, null);
		Rooms.Room room = new Rooms.Room(in[0], in[1], in[2], in[3], 0, Math.max(2, h.body.height(0) - 1));
		room.keep(dx, dz + 1);
		Rooms.furnish(d, room, Rooms.Type.BEDROOM, 1, dev.voidmc.hearth.api.JobType.NONE);
		Lighting.room(d, room);
		return new Hut(room, dx, dz, dx, dz - 1, h.top);
	}

	/** A stool by a door or a fire: a stump-like log end, or a chair where the culture's furniture makes one. */
	public static void stool(Draft d, int x, int y, int z, Direction back) {
		if (Works.in(d, x, z) && d.isEmpty(x, y, z)) {
			Furniture.chair(d, x, y, z, back);
		}
	}
}
