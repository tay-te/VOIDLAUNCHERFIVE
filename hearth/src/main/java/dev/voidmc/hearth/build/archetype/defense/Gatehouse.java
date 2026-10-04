package dev.voidmc.hearth.build.archetype.defense;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.kit.landscape.Lamps;
import dev.voidmc.hearth.build.kit.landscape.Manner;
import dev.voidmc.hearth.build.kit.landscape.Towers;
import dev.voidmc.hearth.build.kit.landscape.Towers.Look;
import dev.voidmc.hearth.build.kit.landscape.Towers.Top;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * {@code hearth:defense/gatehouse}: the town gate over a road where it passes the wall. The road runs through its
 * middle (local z, from the field in front to the town behind) under round-shouldered arches front and back; a raised
 * portcullis shows its teeth inside the front arch, the gate leaves stand folded back against the passage walls, and a
 * lantern hangs in the vault. Two flanking towers rise past the wall top: the left one holds a guard room and the stair
 * up to the gate hall over the passage; the right one a store room with the guards' grindstone, and the stair on up to
 * its lookout. Over the front arch a corbelled, crenellated band; the gate hall has a gable over the arch (or
 * battlements in the south) and the culture's banner; the towers are topped in the culture's manner (see
 * {@link Watchtower}). Where the wall meets it ({@code over_road}), doorways in the towers' flanks let the wall walk in.
 *
 * <p>POIs: GATE in the passage, GUARD_POSTs at the gate's mouth and on the lookout, a VIEWPOINT up there, the GUARD job
 * site (grindstone) and a chest in the store room.
 *
 * <p>Params: {@code over_road} (boolean: stands over a road on the wall line), {@code passage_width} (int, the road's
 * width: 3 or 5), {@code wall_height} (int, 8), {@code roof} (as for the watchtower), {@code street_y}.
 */
public final class Gatehouse implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("defense/gatehouse"), ArchetypeCategory.DEFENSE, Set.of(JobType.GUARD), 0,
		13, 15, 7, 9, 14, 22, SettlementTier.TOWN, Set.of(), Set.of(ArchetypeInfo.SiteFlag.VERTICAL), List.of(), 0, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		Look l = Look.of(d);
		Top top = Watchtower.topFor(d);
		int pw = Math.clamp(d.param("passage_width", w >= 13 ? 5 : 3), 3, Math.max(3, w - 8));
		int c = (w - 1) / 2;
		int px0 = c - pw / 2;
		int px1 = px0 + pw - 1;
		int lx1 = px0 - 1;
		int rx0 = px1 + 1;
		int walk = Math.clamp(d.param("wall_height", 8), 6, 12) - 2;
		int hall = walk;
		// the right tower's platform, reached by the stair; the left tower stands two higher under its roof
		int tt = hall + 6;
		int ttl = Math.min(tt + 2, d.maxHeight() - (top == Top.OPEN ? 3 : 6));
		int cz = (dp - 1) / 2;
		boolean overRoad = d.param("over_road", false);
		Mat body = l.body();
		BlockState air = Blocks.AIR.defaultBlockState();

		// the road outside the gate is the main way in
		d.entrance(c, 0, -1);
		d.poi(PoiType.GATE, c, 0, cz, Dirs.FRONT);
		// the passage levelled and paved with setts; the towers on their footings
		Groundwork.level(d, px0, 0, px1, dp - 1, 0, true);
		pave(d, px0, px1, dp);
		Groundwork.footings(d, 0, 0, lx1, dp - 1, -1);
		Groundwork.footings(d, rx0, 0, w - 1, dp - 1, -1);
		Towers.floor(d, l, 1, 1, lx1 - 1, dp - 2, -1, true);
		Towers.floor(d, l, rx0 + 1, 1, w - 2, dp - 2, -1, true);
		Towers.shell(d, l, 0, 0, lx1, dp - 1, 0, ttl - 2);
		Towers.shell(d, l, rx0, 0, w - 1, dp - 1, 0, tt - 2);
		Towers.string(d, l, 0, 0, lx1, dp - 1, hall - 1);
		Towers.string(d, l, rx0, 0, w - 1, dp - 1, hall - 1);
		Towers.buttresses(d, l, 0, 0, lx1, dp - 1, 1);
		Towers.buttresses(d, l, rx0, 0, w - 1, dp - 1, 1);

		// the vault over the passage, the arches, the hall above
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = 0; z < dp; z++) {
				for (int x = px0; x <= px1; x++) {
					for (int y = 0; y < hall - 1; y++) {
						d.set(x, y, z, air);
					}
					d.set(x, hall - 1, z, (z == 0 || z == dp - 1) && x == c ? l.quoin().full() : body.full());
				}
			}
			for (int z : new int[]{0, dp - 1}) {
				// round shoulders: the arch springs a block below the vault
				if (body.has(Form.STAIRS)) {
					d.set(px0, hall - 2, z, body.stairs(Dirs.LEFT, Half.TOP));
					d.set(px1, hall - 2, z, body.stairs(Dirs.RIGHT, Half.TOP));
				}
			}
			// the gallery's town-side parapet
			for (int x = px0; x <= px1; x++) {
				d.set(x, hall, dp - 1, body.full());
				if (((x - px0) & 1) == 0) {
					d.set(x, hall + 1, dp - 1, body.full());
				}
			}
		}
		// the portcullis, raised, and the gate leaves folded back
		try (var s = d.stage(StageKind.OPENINGS)) {
			for (int x = px0; x <= px1; x++) {
				d.set(x, hall - 2, 1, Blocks.IRON_BARS.defaultBlockState());
			}
			Mat door = d.mat(Role.DOOR);
			if (door.has(Form.TRAPDOOR)) {
				for (int y = 0; y < hall - 2; y++) {
					for (int z = 2; z <= Math.min(3, dp - 3); z++) {
						d.set(px0, y, z, door.trapdoor(Dirs.RIGHT, Half.BOTTOM, true));
						d.set(px1, y, z, door.trapdoor(Dirs.LEFT, Half.BOTTOM, true));
					}
				}
			}
		}
		Furniture.hangingLantern(d, c, hall - 1, cz + 1, 0);
		// the corbelled, crenellated band over the front arch (one row out over the road)
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = lx1; x <= rx0; x++) {
				d.set(x, hall - 1, -1, body.has(Form.STAIRS) ? body.stairs(Dirs.BACK, Half.TOP) : body.full());
				d.set(x, hall, -1, body.full());
				if (((x - lx1) & 1) == 0) {
					d.set(x, hall + 1, -1, body.full());
				}
			}
		}
		Towers.banner(d, lx1 / 2 + (lx1 >= 3 ? 1 : 0), hall + 2, -1, Dirs.FRONT);

		// lanterns either side of the front arch
		for (int x : new int[]{lx1, rx0}) {
			if (d.isAbsent(x, 3, -1)) {
				try (var s = d.stage(StageKind.EXTERIOR)) {
					d.set(x, 4, -1, d.mat(Role.SHUTTER).trapdoor(Dirs.FRONT, Half.TOP, false));
					d.set(x, 3, -1, States.lantern(Lamps.lantern(d), true));
				}
			}
		}

		// the left tower: guard room, the stair up to the hall
		Stairs.switchback(d, 1, 0, 2, Dirs.BACK, hall, Dirs.RIGHT);
		Openings.door(d, Side.of(0, 0, lx1, dp - 1, Dirs.RIGHT), dp - 3, 0, 1, Openings.DoorOpts.BACK);
		// the right tower: store room, the stair on up to the lookout
		Stairs.switchback(d, rx0 + 1, hall, 2, Dirs.BACK, tt - hall, Dirs.RIGHT);
		Openings.door(d, Side.of(rx0, 0, w - 1, dp - 1, Dirs.LEFT), dp - 3, 0, 1, Openings.DoorOpts.BACK);
		Furniture.workstation(d, JobType.GUARD, w - 2, 0, 1, Dirs.BACK);
		Furniture.chest(d, w - 2, 0, dp - 2, Dirs.FRONT, List.of(new Object[]{"minecraft:arrow", 24}, new Object[]{"minecraft:iron_ingot", 3}));
		Watchtower.armsRack(d, rx0 + 1, 1, dp - 2, Dirs.RIGHT);
		Furniture.hangingLantern(d, rx0 + 1, hall - 1, 1, 0);
		// floors at the hall's level in both towers and over the passage
		Towers.floor(d, l, 1, 1, lx1 - 1, dp - 2, hall - 1, false);
		Towers.floor(d, l, rx0 + 1, 1, w - 2, dp - 2, hall - 1, false);
		// openings from the towers into the hall, and the wall walk's doorways in the flanks
		try (var s = d.stage(StageKind.OPENINGS)) {
			for (int x : new int[]{lx1, rx0}) {
				d.set(x, hall, 2, air);
				d.set(x, hall + 1, 2, air);
				d.set(x, hall + 2, 2, air);
			}
			if (overRoad) {
				for (int x : new int[]{0, w - 1}) {
					d.set(x, walk, cz, air);
					d.set(x, walk + 1, cz, air);
				}
			} else {
				Towers.slit(d, 0, hall + 1, cz);
				Towers.slit(d, w - 1, hall + 1, cz);
			}
		}
		// the windlass that raises the portcullis, on the gallery over its slot
		try (var s = d.stage(StageKind.INTERIOR)) {
			Mat beam = d.mat(Role.BEAM);
			for (int x = px0 + 1; x <= px1 - 1; x++) {
				d.set(x, hall, 1, beam.log(Direction.Axis.X).orElse(beam.full()));
			}
			d.set(px0, hall, 1, Lamps.post(d));
			d.set(px1, hall, 1, Lamps.post(d));
			d.set(px0, hall, dp - 2, States.facing(Blocks.BARREL, Direction.UP));
		}
		// the towers' tops: a battlemented platform on the right, reached by the stair; the left, higher, under a
		// steep roof on a corbelled cornice (battlemented too in the south)
		Towers.floor(d, l, rx0, 0, w - 1, dp - 1, tt - 1, !l.timber());
		Towers.battlements(d, l, rx0, 0, w - 1, dp - 1, tt);
		if (top == Top.OPEN) {
			Towers.floor(d, l, 0, 0, lx1, dp - 1, ttl - 1, !l.timber());
			Towers.battlements(d, l, 0, 0, lx1, dp - 1, ttl);
		} else {
			Towers.shell(d, l, 0, 0, lx1, dp - 1, ttl - 2, ttl - 1);
			Towers.corbels(d, l, 0, 0, lx1, dp - 1, ttl - 2);
			Towers.floor(d, l, 1, 1, lx1 - 1, dp - 2, ttl - 1, false);
			// a saddle roof across the tower, its gables to the flanks (a curved hip in the karst)
			boolean pagoda = top == Top.PAGODA;
			Roofs.Opts o = Roofs.Opts.of(d).withEaves(0, 0).withPitch(pagoda ? 1.2 : Math.max(1.2, d.habits().roof().pitch()));
			Roofs.build(d, 0, 0, lx1, dp - 1, ttl - 1, pagoda ? RoofForm.PAGODA : RoofForm.GABLE, Direction.Axis.X, o, false, false);
		}
		Towers.slit(d, lx1 / 2, tt - 1, 0);
		Towers.slit(d, lx1 / 2, tt - 1, dp - 1);
		d.poi(PoiType.GUARD_POST, c, hall, 2, Dirs.FRONT);
		d.poi(PoiType.VIEWPOINT, c - 1, hall, 2, Dirs.FRONT);
		d.poi(PoiType.GUARD_POST, px0, 0, 0, Dirs.FRONT);
		d.poi(PoiType.GUARD_POST, px1, 0, 0, Dirs.FRONT);
		d.poi(PoiType.GUARD_POST, w - 2, tt, 1, Dirs.FRONT);
		d.poi(PoiType.VIEWPOINT, rx0 + 1, tt, 1, Dirs.FRONT);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, d.maxHeight() - 1);
	}

	/** Setts down the passage: the paving with a kerb of the masonry along each side. */
	private static void pave(Draft d, int px0, int px1, int dp) {
		Mat paving = Manner.wood(d.mat(Role.PAVING)) ? Manner.masonry(d) : d.mat(Role.PAVING);
		Mat kerb = Manner.masonry(d);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = 0; z < dp; z++) {
				for (int x = px0; x <= px1; x++) {
					d.set(x, -1, z, x == px0 || x == px1 ? kerb.full() : paving.full());
				}
			}
		}
	}
}
