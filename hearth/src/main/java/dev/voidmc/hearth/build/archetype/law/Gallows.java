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
import dev.voidmc.hearth.build.kit.lawhouse.Scaffold;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@code hearth:law/gallows}: the gallows at the edge of the square. Stark and plain, the way a town builds the thing it
 * hopes not to use: a scaffold three steps up (a timber deck on posts with knee braces in the timber traditions, a stage
 * of coped stone in the masonry ones), the stair up its side with a rail, a rail round the deck with lanterns on its
 * front posts, and on it the gallows: two uprights and a cross beam on corbels, the ropes hanging from it, the drop a
 * trapdoor in the deck under each rope, the hangman's lever on a post at the back. Nothing more.
 *
 * <p>POIs: GALLOWS where the condemned stands (on the drop, facing the crowd), MEETING_POINT in front where the crowd
 * gathers.
 *
 * <p>Params: {@code ropes} (int, 1 or 2; default by width and seed).
 */
public final class Gallows implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("law/gallows"), ArchetypeCategory.LAW, Set.of(), 0,
		7, 9, 7, 9, 9, 12, SettlementTier.TOWN, Set.of(), Set.of(ArchetypeInfo.SiteFlag.CENTRAL), List.of(), 1, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		Tradition t = Tradition.of(d);
		boolean stone = t.masonry() || t == Tradition.KARST;
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		Paving.lay(d, 0, 0, w - 1, dp - 1, 0, Paving.paint(d, Paving.pattern(d), (w - 1) / 2.0, (dp - 1) / 2.0, 2, false));
		int h = 3;
		// the deck: a column in from each side (the stair takes one), two rows in from the front for the crowd
		boolean stairLeft = d.chance(0.5);
		int x0 = 1;
		int x1 = w - 2;
		int zA = 2;
		int zB = dp - 2;
		if (stone) {
			Scaffold.stone(d, x0, zA, x1, zB, h);
		} else {
			Scaffold.timber(d, x0, zA, x1, zB, h);
		}
		// the stair up the side, its top step beside the gallows' row
		int sx = stairLeft ? 0 : w - 1;
		int zF = zA + (zB - zA + 1) / 2;
		int s0 = zF - (h - 1);
		Scaffold.stair(d, sx, s0, Dirs.BACK, h, stairLeft ? Dirs.LEFT : Dirs.RIGHT, stone);
		// the frame and its ropes
		int a = x0 + 1;
		int b = x1 - 1;
		int span = b - a - 1;
		int ropes = Math.clamp(d.param("ropes", span >= 3 && d.chance(0.6) ? 2 : 1), 1, span >= 3 ? 2 : 1);
		int[] at = ropes == 2 ? new int[]{a + 1, b - 1} : new int[]{(a + b) / 2};
		int tall = Math.min(4, d.maxHeight() - 2 - h);
		int beamY = Scaffold.gallows(d, a, b, zF, h, tall, at);
		// the rail round the deck, open where the stair arrives, the lever at the back by the drop
		Set<Long> gaps = new HashSet<>();
		gaps.add(Scaffold.gap(stairLeft ? x0 : x1, zF));
		int leverX = stairLeft ? b : a;
		gaps.add(Scaffold.gap(leverX, zB));
		Scaffold.rail(d, x0, zA, x1, zB, h, gaps);
		Scaffold.lever(d, leverX, h, zB, Dirs.FRONT);
		// lanterns on the rail's front corner posts
		for (int x : new int[]{x0, x1}) {
			if (d.isEmpty(x, h + 1, zA) && !d.isEmpty(x, h, zA)) {
				Lighting.lamp(d, x, h + 1, zA);
			}
		}
		for (int x : at) {
			d.poi(PoiType.GALLOWS, x, h, zF, Dirs.FRONT);
		}
		// the way up from the square in front, lamps at the front corners of the plot
		d.entrance((w - 1) / 2, 0, -1);
		int top = beamY;
		for (int lx : new int[]{1, w - 2}) {
			if (d.isEmpty(lx, 0, 0) && d.isEmpty(lx, 1, 0) && Groundwork.surface(d, lx, 0) == 0) {
				top = Math.max(top, Lamps.standard(d, lx, 0, 0, 4, Manner.lamp(d), null));
			}
		}
		d.poi(PoiType.MEETING_POINT, (w - 1) / 2, 0, -1, Dirs.BACK);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, top + 1));
	}
}
