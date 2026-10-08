package dev.voidmc.hearth.build.archetype.law;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.kit.landscape.Lamps;
import dev.voidmc.hearth.build.kit.landscape.Manner;
import dev.voidmc.hearth.build.kit.landscape.Paving;
import dev.voidmc.hearth.build.kit.lawhouse.Justice;
import dev.voidmc.hearth.build.kit.lawhouse.Scaffold;
import dev.voidmc.hearth.build.kit.lawhouse.Timber;
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
		boolean stone = (t.masonry() || t == Tradition.KARST) && !dev.voidmc.hearth.build.kit.civic.Pueblo.is(d);
		if (t == Tradition.TIMBER && w >= 7 && dp >= 7 && d.maxHeight() >= 10) {
			masonry(d);
			return;
		}
		if (dev.voidmc.hearth.build.kit.civic.Pueblo.is(d)) {
			gibbet(d);
			return;
		}
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		Paving.lay(d, 0, 0, w - 1, dp - 1, 0, Paving.paint(d, Paving.pattern(d), (w - 1) / 2.0, (dp - 1) / 2.0, 2, false));
		int h = 3;
		// the deck: a column in from each side (the stair takes one), two rows in from the front for the crowd
		int zA = 2;
		int zB = dp - 2;
		int zMid = zA + (zB - zA + 1) / 2;
		// the stair on the side where the land beyond falls least; where it falls there at all, a column in from the plot's
		// edge, so the stair's outer rail stands on the plot (a rail can't hang over the neighbour's slope)
		int dropL = -d.site().ground(-1, zMid);
		int dropR = -d.site().ground(w, zMid);
		boolean stairLeft = dropL == dropR ? d.chance(0.5) : dropL < dropR;
		int inset = Math.min(dropL, dropR) >= 1 && w >= 7 ? 1 : 0;
		int x0 = stairLeft ? 1 + inset : 1;
		int x1 = stairLeft ? w - 2 : w - 2 - inset;
		if (stone) {
			Scaffold.stone(d, x0, zA, x1, zB, h);
		} else {
			Scaffold.timber(d, x0, zA, x1, zB, h);
		}
		// the stair up the side, its top step beside the gallows' row
		int sx = stairLeft ? inset : w - 1 - inset;
		int zF = zA + (zB - zA + 1) / 2;
		int s0 = zF - (h - 1);
		Scaffold.stair(d, sx, s0, Dirs.BACK, h, stairLeft ? Dirs.LEFT : Dirs.RIGHT, stone);
		// the frame and its ropes
		int a = x0 + 1;
		int b = x1 - 1;
		// on a narrow deck the upright away from the stair stands on the deck's edge, so the frame opens wide enough to read
		if (b - a < 3) {
			if (stairLeft) {
				b = x1;
			} else {
				a = x0;
			}
		}
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
		dev.voidmc.hearth.build.kit.civic.Halls.finish(d);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, top + 1));
	}

	/**
	 * The mesa's gibbet: on the plaza's beaten earth a low platform of planks a step up, a tall post at its back with an
	 * arm braced out over the drop, the chain from the arm's end, a trapdoor under it; a lantern on the post.
	 */
	static void gibbet(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int cx = (w - 1) / 2;
		int cz = dp / 2;
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		Timber t = Timber.frame(d);
		Timber j = Timber.joinery(d);
		try (var st = d.stage(StageKind.FRAME)) {
			for (int z = cz - 1; z <= cz + 1; z++) {
				for (int x = cx - 1; x <= cx + 1; x++) {
					d.set(x, 0, z, j.planks());
				}
			}
			for (int x = cx - 1; x <= cx + 1; x++) {
				d.set(x, 0, cz - 2, j.stairs(Dirs.BACK, net.minecraft.world.level.block.state.properties.Half.BOTTOM));
			}
			for (int y = 1; y <= 5; y++) {
				d.set(cx, y, cz + 1, t.post());
			}
			d.set(cx, 5, cz, t.beam(net.minecraft.core.Direction.Axis.Z));
			d.set(cx, 4, cz + 1, t.post());
			d.set(cx, 4, cz, j.stairs(Dirs.FRONT, net.minecraft.world.level.block.state.properties.Half.TOP));
		}
		try (var st = d.stage(StageKind.INTERIOR)) {
			d.set(cx, 3, cz, Justice.chainState());
		}
		try (var st = d.stage(StageKind.FLOORS)) {
			d.set(cx, 0, cz, j.trapdoor(Dirs.BACK, net.minecraft.world.level.block.state.properties.Half.TOP, false));
		}
		Lighting.lamp(d, cx, 6, cz + 1);
		d.poi(PoiType.GALLOWS, cx, 1, cz, Dirs.FRONT);
		d.entrance(cx, 0, -1);
		d.poi(PoiType.MEETING_POINT, cx, 0, -1, Dirs.BACK);
		dev.voidmc.hearth.build.kit.civic.Halls.finish(d);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, 8));
	}

	/**
	 * The Bohemian gallows (a <i>šibenice</i> on the town's edge): a round platform of rubble two courses high, coped in
	 * the dressing, a flight let into its front; on it two square pillars of dressed stone with capitals carry a hewn beam,
	 * the ropes' chains hang from it over drops let into the deck, a lantern on each capital.
	 */
	static void masonry(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int cx = (w - 1) / 2;
		int cz = Math.min(dp - 4, Math.max(3, dp / 2));
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		Paving.lay(d, 0, 0, w - 1, dp - 1, 0, Paving.paint(d, Paving.pattern(d), cx, cz, 2, false));
		dev.voidmc.hearth.build.kit.landmark.Stone body = dev.voidmc.hearth.build.kit.landmark.Stone.footing(d);
		dev.voidmc.hearth.build.kit.landmark.Stone dress = dev.voidmc.hearth.build.kit.landmark.Stone.dressing(d);
		java.util.Set<Long> deck = dev.voidmc.hearth.build.kit.landmark.Shapes.oct(cx, cz, 3, 1);
		// the platform: rubble to the deck at standing level 2, its rim coped
		try (var st = d.stage(StageKind.FOUNDATION)) {
			for (long k : deck) {
				int x = (int) (k >> 32);
				int z = (int) k;
				for (int y = Math.min(-1, d.site().ground(x, z) - 1); y <= 1; y++) {
					d.set(x, y, z, body.full());
				}
			}
		}
		try (var st = d.stage(StageKind.WALLS)) {
			for (long k : deck) {
				int x = (int) (k >> 32);
				int z = (int) k;
				boolean rim = false;
				for (net.minecraft.core.Direction dir : Dirs.HORIZONTAL) {
					rim |= !deck.contains(dev.voidmc.hearth.build.kit.landmark.Shapes.cell(x + Dirs.dx(dir), z + Dirs.dz(dir)));
				}
				if (rim) {
					d.set(x, 1, z, dress.full());
				}
			}
		}
		// the flight let into the front: two steps across three columns
		int zf = cz - 3;
		try (var st = d.stage(StageKind.EXTERIOR)) {
			for (int x = cx - 1; x <= cx + 1; x++) {
				d.set(x, 0, zf, dress.stairsOrSlab(Dirs.BACK, net.minecraft.world.level.block.state.properties.Half.BOTTOM));
				d.set(x, 1, zf, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
				d.set(x, 1, zf + 1, dress.stairsOrSlab(Dirs.BACK, net.minecraft.world.level.block.state.properties.Half.BOTTOM));
			}
		}
		// the pillars, their capitals, the beam and the ropes
		int pz = cz + 1;
		int top = Math.min(d.maxHeight() - 2, 7);
		Timber t = Timber.frame(d);
		try (var st = d.stage(StageKind.WALLS)) {
			for (int x : new int[]{cx - 2, cx + 2}) {
				for (int y = 2; y < top - 1; y++) {
					d.set(x, y, pz, dress.wallOrFull());
				}
				d.set(x, top - 1, pz, dress.full());
			}
		}
		try (var st = d.stage(StageKind.FRAME)) {
			for (int x = cx - 1; x <= cx + 1; x++) {
				d.set(x, top - 1, pz, t.beam(net.minecraft.core.Direction.Axis.X));
			}
		}
		int[] ropes = {cx - 1, cx + 1};
		try (var st = d.stage(StageKind.INTERIOR)) {
			for (int x : ropes) {
				d.set(x, top - 2, pz, Justice.chainState());
			}
		}
		try (var st = d.stage(StageKind.FLOORS)) {
			for (int x : ropes) {
				d.set(x, 1, pz, Timber.joinery(d).trapdoor(Dirs.BACK, net.minecraft.world.level.block.state.properties.Half.TOP, false));
			}
		}
		for (int x : new int[]{cx - 2, cx + 2}) {
			Lighting.lamp(d, x, top, pz);
		}
		for (int x : ropes) {
			d.poi(PoiType.GALLOWS, x, 2, pz, Dirs.FRONT);
		}
		d.entrance(cx, 0, -1);
		d.poi(PoiType.MEETING_POINT, cx, 0, -1, Dirs.BACK);
		int sky = top + 1;
		int lamps = 0;
		for (int lx : new int[]{0, w - 1}) {
			if (d.isEmpty(lx, 0, 0) && d.isEmpty(lx, 1, 0) && Groundwork.surface(d, lx, 0) == 0) {
				sky = Math.max(sky, Lamps.standard(d, lx, 0, 0, 4, Manner.lamp(d), null));
				lamps++;
			}
		}
		// (the corners off level on a slope: a lamp beside the way up instead, where its foot stands on the ground)
		for (int lx : new int[]{cx - 2, cx + 2, cx - 3, cx + 3}) {
			if (lamps > 0 || lx < 0 || lx >= w) {
				continue;
			}
			int g = Groundwork.surface(d, lx, 0);
			if (Math.abs(g) <= 1 && d.isEmpty(lx, g, 0) && d.isEmpty(lx, g + 1, 0)) {
				sky = Math.max(sky, Lamps.standard(d, lx, g, 0, 4, Manner.lamp(d), null));
				lamps++;
			}
		}
		dev.voidmc.hearth.build.kit.civic.Halls.finish(d);
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, sky + 1));
	}
}
