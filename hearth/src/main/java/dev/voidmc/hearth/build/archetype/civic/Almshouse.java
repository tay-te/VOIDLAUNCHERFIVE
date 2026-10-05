package dev.voidmc.hearth.build.archetype.civic;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.civic.Grounds;
import dev.voidmc.hearth.build.kit.civic.Sacred;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.kit.homestead.Bohemia;
import dev.voidmc.hearth.build.kit.homestead.Yard;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.sociallife.Range;
import dev.voidmc.hearth.build.kit.sociallife.Signboard;
import dev.voidmc.hearth.build.kit.sociallife.Venue;
import dev.voidmc.hearth.build.kit.sociallife.Wards;
import dev.voidmc.hearth.build.kit.sociallife.Yardwork;
import dev.voidmc.hearth.build.kit.trade.Floorplan;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:civic/almshouse}: the town's charity, where the old and the poor who have no one live out their days. A
 * plain long range of two storeys, the ground storey of stone, a small cross over the door. Below, the common room: the
 * kitchen fire on one end wall, a long table where they eat together, at the other end a little altar with candles where
 * they say their prayers, and by the door the alms box. Up the stair the sleeping floor, a bed for each with a chest at
 * its side,. Behind, the kitchen
 * garden they keep, a well and a bench in the sun.
 *
 * <p>Housing for the POOR (the planner counts its beds). POIs: BEDs (6 or more), STORAGE (the alms box and the chests),
 * TABLE, SEATs, the HEARTH, the ALTAR, the DOOR. Params: {@code variant} (int).
 */
public final class Almshouse implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("civic/almshouse"), ArchetypeCategory.CIVIC, Set.of(), 6, 11, 15, 11, 15, 14,
		16, SettlementTier.TOWN, Set.of(), Set.of(), List.of(), 2, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		Tradition t = Tradition.of(d);
		boolean raised = t.raised();
		Range.Built r = Range.build(d, dp >= 13 ? 8 : 7, true, 0.6);
		int base = r.base();
		Floorplan ground = r.ground();
		boolean stairLeft = r.stairLeft();
		int doorX = r.shell().doorCol();

		// --- the common room: the fire at the end away from the stair, the altar at the stair's end against the front wall
		int hx = stairLeft ? r.ix1() : r.ix0();
		Direction face = stairLeft ? Dirs.LEFT : Dirs.RIGHT;
		Range.hearth(d, r, ground, hx, face, List.of(new Object[]{"minecraft:bread", 8}, new Object[]{"minecraft:potato", 12},
			new Object[]{"minecraft:bowl", 6}));
		int ax = stairLeft ? r.ix0() : r.ix1();
		int az = r.iz0() + 1;
		Direction toPeople = stairLeft ? Dirs.RIGHT : Dirs.LEFT;
		if (ground.free(ax, az) && ground.inside(ax + Dirs.dx(toPeople), az) && ground.take(ax, az, toPeople)) {
			Sacred.altar(d, ax, base, az, toPeople, false, Integer.MIN_VALUE);
			// a crucifix (or the culture's sign) on the wall over it
			Venue.banner(d, ax, base + 2, az, toPeople, "white", List.<String[]>of(new String[]{"minecraft:straight_cross", "black"}), StageKind.INTERIOR);
		}
		// the alms box by the door, inside
		int[] in = r.shell().inside();
		for (int k : new int[]{-1, 1}) {
			int bx = in[0] + k;
			if (ground.free(bx, r.iz0()) && ground.take(bx, r.iz0(), Dirs.BACK)) {
				Furniture.chest(d, bx, base, r.iz0(), Dirs.BACK, List.of(new Object[]{"minecraft:copper_nugget", 9}, new Object[]{"minecraft:bread", 3}));
				break;
			}
		}
		if (Range.tables(d, ground, r.ix0(), r.ix1(), w >= 13 ? 4 : 3, 1) == 0) {
			Range.tables(d, ground, r.ix0(), r.ix1(), 2, 1);
		}
		Range.wallBench(d, ground);
		ground.blockDrawn(d);
		Rooms.furnish(d, ground.room(), Rooms.Type.HALL, 0, JobType.NONE);
		Lighting.room(d, r.ix0(), r.iz0(), r.ix1(), r.iz1(), base);

		// --- the sleeping floor: a bed for each, a chest or a candle stand at its side
		Floorplan up = r.upper();
		List<Object[]> kit = List.of(new Object[]{"minecraft:white_wool", 1}, new Object[]{"minecraft:candle", 1});
		for (Direction wall : new Direction[]{Dirs.FRONT, Dirs.BACK, Dirs.LEFT, Dirs.RIGHT}) {
			Wards.row(d, up, wall, 2, false, 12, kit);
		}
		Lighting.room(d, up.x0, up.z0, up.x1, up.z1, up.y);

		// --- outside: a cross over the door, a bench, the board
		Side front = r.body().side(0, Dirs.FRONT);
		int di = front.index(doorX);
		if (di >= 1 && di <= front.last() - 1) {
			Mat cross = d.mat(Role.FRAME);
			int cy = base + r.heights()[0] + 1;
			int fz = front.outZ(di, 1);
			if (cy + 1 < d.maxHeight() && d.isEmpty(doorX, cy, fz)) {
				try (var s = d.stage(StageKind.EXTERIOR)) {
					d.set(doorX, cy - 1, fz, cross.has(dev.voidmc.hearth.build.style.Form.SLAB) ? cross.slab(SlabType.TOP) : cross.fence());
					d.set(doorX, cy, fz, cross.fence());
				}
			}
		}
		List<String[]> name = Signboard.name(d, "almshouse", Signboard.ALMSHOUSES);
		for (int k : new int[]{2, -2, 3, -3}) {
			int i = di + k;
			if (i >= 1 && i <= front.last() - 1 && Signboard.bracket(d, front.x(i), base + 3, front.z(i), Dirs.FRONT, name)) {
				break;
			}
		}
		Grounds.approach(d, r.shell().outside()[0], base, r.shell().outside()[1]);
		if (!raised) {
			Bohemia.bench(d, doorX, r.zF(), base);
		}

		// --- behind: the kitchen garden they keep, a well, a bench against the back wall
		int yz0 = r.zM() + 1;
		if (dp - 1 - yz0 >= 3 && !raised && Grounds.gentle(d, 0, yz0, w - 1, dp - 1, base)) {
			Groundwork.level(d, 0, yz0, w - 1, dp - 1, base);
			int gx0 = stairLeft ? 1 : 4;
			int gx1 = stairLeft ? w - 5 : w - 2;
			Garden.kitchenRows(d, gx0, yz0 + 1, gx1, dp - 2, base, Dirs.RIGHT);
			Yard.well(d, stairLeft ? w - 3 : 2, base, Math.min(dp - 2, yz0 + 2), Dirs.FRONT);
		}
		if (d.habits().snow()) {
			Garden.snowBanks(d, 0, r.zF(), w - 1, r.zM(), base, doorX);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, r.shell().skyline() + 3));
	}
}
