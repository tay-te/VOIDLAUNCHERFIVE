package dev.voidmc.hearth.build.archetype.civic;

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
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.civic.Grounds;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.kit.homestead.Bohemia;
import dev.voidmc.hearth.build.kit.landscape.Trees;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.sociallife.Range;
import dev.voidmc.hearth.build.kit.sociallife.Signboard;
import dev.voidmc.hearth.build.kit.sociallife.Wards;
import dev.voidmc.hearth.build.kit.sociallife.Yardwork;
import dev.voidmc.hearth.build.kit.trade.Floorplan;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/**
 * {@code hearth:civic/orphanage}: the foundlings' home, kept by the town or a pious house. A plain range of two storeys.
 * Below, the children's hall, schoolroom and refectory in one: the long tables where they eat and learn their letters,
 * the matron's lectern at the head of the room facing them, the kitchen fire on the end wall, a chest of their few toys
 * and clothes by the door. Up the stair the dormitory, small beds packed side by side down the walls, each reached from
 * its foot. Behind, the yard where they play: a swing on its frame, a shade tree, a little garden they tend.
 *
 * <p>Housing (the planner counts its beds, the orphans' home). POIs: BEDs (8 or more), the LECTERN, TABLE, SEATs, the
 * HEARTH, STORAGE, the DOOR. Params: {@code variant} (int).
 */
public final class Orphanage implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("civic/orphanage"), ArchetypeCategory.CIVIC, Set.of(), 8, 11, 15, 11, 15, 14,
		16, SettlementTier.TOWN, Set.of(), Set.of(), List.of(), 1, 1.0);

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
		Range.Built r = Range.build(d, dp >= 13 ? 8 : 7, t.masonry(), 0.75);
		int base = r.base();
		Floorplan ground = r.ground();
		boolean stairLeft = r.stairLeft();
		int doorX = r.shell().doorCol();

		// --- the hall: the fire at the end away from the stair; the matron's lectern at the stair's end, facing the tables
		int hx = stairLeft ? r.ix1() : r.ix0();
		Direction face = stairLeft ? Dirs.LEFT : Dirs.RIGHT;
		Range.hearth(d, r, ground, hx, face, List.of(new Object[]{"minecraft:bread", 10}, new Object[]{"minecraft:milk_bucket", 1},
			new Object[]{"minecraft:bowl", 10}));
		int lx = stairLeft ? r.ix0() : r.ix1();
		int lz = r.iz0() + 1;
		Direction toTables = stairLeft ? Dirs.RIGHT : Dirs.LEFT;
		if (ground.free(lx, lz) && ground.inside(lx + Dirs.dx(toTables), lz) && ground.take(lx, lz, toTables)) {
			d.inStage(StageKind.INTERIOR, () -> d.set(lx, base, lz, States.facing(Blocks.LECTERN, toTables)));
			d.poi(PoiType.LECTERN, lx + Dirs.dx(toTables), base, lz, toTables.getOpposite());
		}
		// the chest of toys and clothes by the door
		int[] in = r.shell().inside();
		for (int k : new int[]{1, -1, 2, -2}) {
			int bx = in[0] + k;
			if (ground.free(bx, r.iz0()) && ground.take(bx, r.iz0(), Dirs.BACK)) {
				Furniture.chest(d, bx, base, r.iz0(), Dirs.BACK, List.of(new Object[]{"minecraft:white_wool", 4}, new Object[]{"minecraft:stick", 4},
					new Object[]{"minecraft:book", 2}));
				break;
			}
		}
		if (Range.tables(d, ground, r.ix0(), r.ix1(), w >= 13 ? 4 : 3, 2) == 0) {
			Range.tables(d, ground, r.ix0(), r.ix1(), 2, 1);
		}
		Range.wallBench(d, ground);
		ground.blockDrawn(d);
		Rooms.furnish(d, ground.room(), Rooms.Type.HALL, 0, JobType.NONE);
		Lighting.room(d, r.ix0(), r.iz0(), r.ix1(), r.iz1(), base);

		// --- the dormitory: small beds side by side down every wall, each reached from its foot
		Floorplan up = r.upper();
		for (Direction wall : new Direction[]{Dirs.FRONT, Dirs.BACK, Dirs.LEFT, Dirs.RIGHT}) {
			Wards.row(d, up, wall, 1, false, 12, null);
		}
		Lighting.room(d, up.x0, up.z0, up.x1, up.z1, up.y);

		// --- outside: the board, a bench by the door
		Side front = r.body().side(0, Dirs.FRONT);
		int di = front.index(doorX);
		List<String[]> name = Signboard.name(d, "orphanage", Signboard.ORPHANAGES);
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

		// --- the yard behind: a swing, a shade tree, the children's garden
		int yz0 = r.zM() + 1;
		if (dp - 1 - yz0 >= 3 && !raised && Grounds.gentle(d, 0, yz0, w - 1, dp - 1, base)) {
			Groundwork.level(d, 0, yz0, w - 1, dp - 1, base);
			int sz = Math.min(dp - 2, yz0 + 2);
			int sx = stairLeft ? 3 : w - 4;
			Yardwork.swing(d, sx, base, sz, Dirs.RIGHT);
			int tx = stairLeft ? w - 3 : 2;
			int th = Math.min(7, d.maxHeight() - 1 - base);
			if (th >= 4 && dp - 1 - yz0 >= 4) {
				Trees.tree(d, tx, base, Math.min(dp - 2, yz0 + 2), th, Trees.kind(d), tx - 2, yz0, tx + 2, dp - 1);
			}
			int gx0 = w / 2 - 1;
			if (dp - 1 - yz0 >= 4) {
				Garden.kitchenRows(d, gx0, dp - 2, gx0 + 2, dp - 2, base, Dirs.RIGHT);
			}
		}
		if (d.habits().snow()) {
			Garden.snowBanks(d, 0, r.zF(), w - 1, r.zM(), base, doorX);
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, r.shell().skyline() + 3));
	}
}
