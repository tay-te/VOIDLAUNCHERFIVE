package dev.voidmc.hearth.build.kit.sociallife;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.kit.homestead.Bohemia;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The boundary of a yard that is somewhere in its own right (a churchyard, a court, a green): a wall of the culture's
 * render or rubble two blocks high under a coping of its roofing (the little roof a Bohemian churchyard wall wears), a
 * timber fence on a stone course where the culture builds in wood, stepping with the ground; and the roofed gate into
 * it, the lychgate.
 */
public final class Enclosure {
	private Enclosure() {
	}

	/** How a culture closes a yard. */
	public enum Kind {
		/** Rendered or rubble masonry under a coping of the roofing. */
		WALL,
		/** Timber pales on a stone course. */
		FENCE,
		/** A clipped hedge. */
		HEDGE
	}

	public static Kind kind(Draft d) {
		return switch (Tradition.of(d)) {
			case LOG, STEPPE -> Kind.FENCE;
			case STILT -> d.habits().yard().fence() == Habits.FenceKind.HEDGE ? Kind.HEDGE : Kind.FENCE;
			default -> Kind.WALL;
		};
	}

	/** The masonry of a yard wall: the style's walls where they are masonry, else its foundation stone. */
	static Mat masonry(Draft d) {
		Habits.WallKind k = d.habits().walls().kind();
		boolean mason = k == Habits.WallKind.STONE || k == Habits.WallKind.PLASTER || k == Habits.WallKind.ADOBE || k == Habits.WallKind.BRICK
			|| Bohemia.is(d);
		return mason && Tradition.of(d) != Tradition.KARST ? d.mat(Role.WALL) : d.mat(Role.FOUNDATION);
	}

	/**
	 * A run of boundary over the cells (in order), each standing on its own ground: a footing course, the body
	 * {@code height} high, the coping over it. Cells in water and cells already built on are skipped.
	 */
	public static void run(Draft d, List<int[]> cells, int height) {
		Kind kind = kind(d);
		Mat wall = masonry(d);
		Mat found = d.mat(Role.FOUNDATION);
		Mat roof = d.mat(Role.ROOF);
		Mat fence = d.mat(Role.FENCE).has(Form.FENCE) ? d.mat(Role.FENCE) : d.mat(Role.FRAME);
		Mat hedge = d.mat(Role.HEDGE);
		for (int[] c : cells) {
			int x = c[0];
			int z = c[1];
			if (x < 0 || z < 0 || x >= d.frontage() || z >= d.depth() || d.site().isWater(x, z)) {
				continue;
			}
			int g = Groundwork.surface(d, x, z);
			if (!d.isEmpty(x, g, z)) {
				continue;
			}
			if (g + height + 1 >= d.maxHeight()) {
				continue;
			}
			try (var s = d.stage(StageKind.FOUNDATION)) {
				d.set(x, g - 1, z, found.full());
			}
			switch (kind) {
				case WALL -> {
					try (var s = d.stage(StageKind.WALLS)) {
						for (int y = g; y < g + height; y++) {
							d.set(x, y, z, y == g ? found.full() : wall.full());
						}
					}
					try (var s = d.stage(StageKind.ROOF)) {
						Paint cap = roof.has(Form.SLAB) && !d.habits().roof().thatch() ? roof.slab(SlabType.BOTTOM)
							: d.mat(Role.WALL_ACCENT).has(Form.SLAB) ? d.mat(Role.WALL_ACCENT).slab(SlabType.BOTTOM) : found.slab(SlabType.BOTTOM);
						d.set(x, g + height, z, cap);
					}
				}
				case FENCE -> {
					try (var s = d.stage(StageKind.EXTERIOR)) {
						d.set(x, g, z, found.full());
						for (int y = g + 1; y < g + height; y++) {
							d.set(x, y, z, fence.fence());
						}
					}
				}
				case HEDGE -> {
					try (var s = d.stage(StageKind.EXTERIOR)) {
						for (int y = g; y < g + height; y++) {
							d.set(x, y, z, hedge.leaves());
						}
					}
				}
			}
		}
	}

	/**
	 * The lychgate: a roofed gate centred on column {@code cx} of the front row, three deep (rows 0 to 2) on level ground
	 * at y, its posts at {@code cx - 2} and {@code cx + 2} front and back, beams across them, a steep little gable of the
	 * roofing with its end to the street (boarded in the gable), the coffin benches either side of the way, a lantern hung
	 * under the ridge. Registers the entrance on the street side. Returns false (drawing nothing) where it does not fit.
	 */
	public static boolean lychgate(Draft d, int cx, int y) {
		if (cx - 3 < 0 || cx + 3 >= d.frontage() || d.depth() < 4 || y + 7 >= d.maxHeight()) {
			return false;
		}
		Mat frame = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		Mat found = d.mat(Role.FOUNDATION);
		Mat paving = d.mat(Role.PAVING);
		Mat gable = Bohemia.is(d) ? d.mat(Role.ROOF_TRIM) : d.mat(Role.GABLE);
		Groundwork.level(d, cx - 3, 0, cx + 3, 2, y);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = 0; z <= 2; z++) {
				for (int x = cx - 1; x <= cx + 1; x++) {
					d.set(x, y - 1, z, paving.full());
				}
			}
		}
		try (var s = d.stage(StageKind.FRAME)) {
			for (int x : new int[]{cx - 2, cx + 2}) {
				for (int z : new int[]{0, 2}) {
					d.set(x, y - 1, z, found.full());
					for (int yy = y; yy < y + 3; yy++) {
						d.set(x, yy, z, frame.log(Direction.Axis.Y));
					}
				}
				// the plate along each side, and a rail between the posts at sill height
				for (int z = 0; z <= 2; z++) {
					d.set(x, y + 3, z, beam.log(Direction.Axis.Z));
				}
				d.set(x, y + 1, 1, frame.fence().orElse(beam.log(Direction.Axis.Z)));
			}
			for (int z : new int[]{0, 2}) {
				for (int x = cx - 1; x <= cx + 1; x++) {
					d.set(x, y + 3, z, beam.log(Direction.Axis.X));
				}
			}
		}
		// the roof: a steep gable, its ridge running in from the street
		Roofs.Field f = Roofs.gable(cx - 3, -1, cx + 3, 3, Direction.Axis.Z, Roofs.Profile.straight(1.0));
		Roofs.Roof r = Roofs.raster(d, f, y + 3, cx - 3, -1, cx + 3, 3, d.mat(Role.ROOF), d.mat(Role.ROOF_TRIM), d.habits().roof().ridge());
		// the gables boarded between the tie beam and the roof
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z : new int[]{0, 2}) {
				for (int x = cx - 1; x <= cx + 1; x++) {
					for (int yy = y + 4; yy < r.bottomY(x, z); yy++) {
						d.set(x, yy, z, gable.strippedLog(Direction.Axis.Y).orElse(gable.full()));
					}
				}
			}
		}
		// the coffin benches
		Mat wood = d.mat(Role.FURNITURE);
		for (int x : new int[]{cx - 1, cx + 1}) {
			Direction back = x < cx ? Dirs.LEFT : Dirs.RIGHT;
			d.inStage(StageKind.EXTERIOR, () -> d.set(x, y, 1, wood.stairsOrSlab(back, Half.BOTTOM)));
			d.poi(PoiType.SEAT, x, y, 1, back.getOpposite());
		}
		if (r.covers(cx, 1)) {
			int ceiling = r.surfaceY(cx, 1);
			Lighting.chandelier(d, cx, 1, ceiling, y + 2);
		}
		d.entrance(cx, y, -1);
		d.poi(PoiType.DOOR, cx, y, -1, Dirs.BACK);
		return true;
	}

	/**
	 * A plain gateway where there is no room for a lychgate: two piers of the yard's masonry either side of the way at
	 * {@code cx - 2} and {@code cx + 2} in the front row (the way three wide between them), capped, a lantern on each.
	 * Registers the entrance on the street side.
	 */
	public static void piers(Draft d, int cx, int y) {
		Mat wall = masonry(d);
		Mat found = d.mat(Role.FOUNDATION);
		Mat cap = d.mat(Role.WALL_ACCENT).has(Form.SLAB) ? d.mat(Role.WALL_ACCENT) : found;
		Groundwork.level(d, Math.max(0, cx - 2), 0, Math.min(d.frontage() - 1, cx + 2), 0, y);
		for (int x : new int[]{cx - 2, cx + 2}) {
			if (x < 0 || x >= d.frontage()) {
				continue;
			}
			int top = Math.min(y + 2, d.maxHeight() - 2);
			try (var s = d.stage(StageKind.WALLS)) {
				d.set(x, y - 1, 0, found.full());
				for (int yy = y; yy < top; yy++) {
					d.set(x, yy, 0, yy == y ? found.full() : wall.full());
				}
				d.set(x, top, 0, cap.full());
			}
			if (top + 1 < d.maxHeight()) {
				d.inStage(StageKind.EXTERIOR, () -> d.set(x, top + 1, 0, dev.voidmc.hearth.build.draft.States.lantern(Blocks.LANTERN, false)));
			}
		}
		d.entrance(cx, y, -1);
		d.poi(PoiType.DOOR, cx, y, -1, Dirs.BACK);
	}

	/** Whether a cell is free ground at standing level y (nothing drawn there or over it, the ground at y, no water). */
	public static boolean ground(Draft d, int x, int y, int z) {
		return x >= 0 && z >= 0 && x < d.frontage() && z < d.depth() && d.isEmpty(x, y, z) && d.isEmpty(x, y + 1, z) && !d.site().isWater(x, z)
			&& Groundwork.surface(d, x, z) == y;
	}
}
