package dev.voidmc.hearth.build.archetype.law;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.kit.landscape.Lamps;
import dev.voidmc.hearth.build.kit.landscape.Manner;
import dev.voidmc.hearth.build.kit.landscape.Paving;
import dev.voidmc.hearth.build.kit.lawhouse.Justice;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;

/**
 * {@code hearth:law/pillory}: the town's pillory in its square. A dais of the town's stone, coped in its dressing, one
 * step up on the smallest plots and two on larger ones, with a flight across its front; on it the pillory (two dark
 * posts, the pale neck board, a beam over the head and a little cap carrying a lantern) where the guilty stand facing the
 * crowd, the stocks for two beside it where the plot is wide enough, the bin of rotten vegetables the crowd throws from,
 * and the culture's lamp at a back corner. The ground round it is paved like the square.
 *
 * <p>POIs: PILLORY where the prisoner stands (facing the crowd; the judge watches from three blocks out), MEETING_POINT
 * in front of the dais (where the crowd gathers), DOOR-less: the entrance is the square in front.
 *
 * <p>Params: none beyond the shared ones ({@code on_square}, {@code street_y}, {@code variant}).
 */
public final class Pillory implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("law/pillory"), ArchetypeCategory.LAW, Set.of(), 0,
		5, 7, 5, 7, 6, 8, SettlementTier.VILLAGE, Set.of(), Set.of(ArchetypeInfo.SiteFlag.CENTRAL), List.of(), 1, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int cx = (w - 1) / 2;
		// the crowd comes from the square in front
		d.entrance(cx, 0, -1);
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		Paving.lay(d, 0, 0, w - 1, dp - 1, 0, Paving.paint(d, Paving.pattern(d), cx, (dp - 1) / 2.0, 2, false));
		// the dais: a step up (two on a deeper plot), the flight across the middle of its front
		int h = dp >= 6 && d.maxHeight() >= 8 ? 2 : 1;
		int z0 = h;
		int z1 = dp - 1 - (dp >= 7 ? 1 : 0);
		int x0 = 0;
		int x1 = w - 1;
		boolean wide = w >= 6;
		// with the stocks beside it the pillory stands off the middle
		boolean right = d.chance(0.5);
		int px = wide ? (right ? x1 - 1 : x0 + 1) : cx;
		int pz = z0 + Math.min(1, z1 - z0);
		int s0 = Math.max(x0 + 1, px - 1);
		int s1 = Math.min(x1 - 1, px + 1);
		Justice.dais(d, x0, z0, x1, z1, h, s0, s1);
		// a stone column in the masonry and market-town traditions, a timber frame elsewhere
		Tradition t = Tradition.of(d);
		boolean column = t == Tradition.TIMBER || t == Tradition.ALPINE || t == Tradition.STONE || t == Tradition.MEDITERRANEAN;
		int top = column ? Justice.column(d, px, h, pz, Dirs.FRONT) : Justice.pillory(d, px, h, pz, Dirs.FRONT);
		if (wide) {
			// the stocks along the dais beside it, their bench behind
			int sx = right ? x0 : x1;
			Justice.stocks(d, sx, h, pz, right ? Dirs.RIGHT : Dirs.LEFT, Dirs.BACK, w >= 7 ? 2 : 1);
			Justice.bin(d, right ? x1 : x0, h, z1);
		} else {
			Justice.bin(d, px == x0 + 1 ? x1 : x0, h, z1);
		}
		// a pumpkin or a melon dropped in front of the dais (thrown, missed)
		int fx = px + (d.chance(0.5) ? 2 : -2);
		if (fx >= 0 && fx < w && z0 - h - 1 >= 0 && d.isEmpty(fx, 0, 0) && fx != s0 - 1 && fx != s1 + 1) {
			if (d.chance(0.5)) {
				Justice.produce(d, fx, 0, 0, d.chance(0.5));
			}
		}
		// the lamp at the front corner away from the steps, where the crowd comes from
		int lx = px <= cx ? w - 1 : 0;
		int lz = 0;
		if (d.isEmpty(lx, 0, lz) && Groundwork.surface(d, lx, lz) == 0) {
			top = Math.max(top, Lamps.standard(d, lx, 0, lz, 4, Manner.lamp(d), null));
		}
		d.poi(PoiType.MEETING_POINT, px, 0, -1, Dirs.BACK);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, top + 1));
	}
}
