package dev.voidmc.hearth.build.archetype.civic;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Facade;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Walls;
import dev.voidmc.hearth.build.kit.civic.Arches;
import dev.voidmc.hearth.build.kit.civic.Grounds;
import dev.voidmc.hearth.build.kit.civic.Halls;
import dev.voidmc.hearth.build.kit.civic.Sacred;
import dev.voidmc.hearth.build.kit.civic.Signs;
import dev.voidmc.hearth.build.kit.civic.Towers;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * The adobe mission of the desert and the mesa: a long nave of thick battered walls under a flat roof behind a parapet,
 * vigas through the wall heads, buttresses stepping down its flanks, and a front that rises over the roof into a bell
 * wall pierced for its bells, with an arched portal and a round window over it; in towns a squat bell tower with a little
 * dome beside the front; and before it a walled campo santo with its graves and an arched gate.
 */
final class ChapelMission {
	private ChapelMission() {
	}

	static void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int tier = d.tier();
		int maxH = d.maxHeight();
		Tradition t = Tradition.ADOBE;
		int nw = w >= 12 ? 9 : 7;
		boolean yard = d.param("graveyard", true);
		boolean tower = d.param("tower", tier >= 3) && w - nw >= 4;
		boolean side = yard && !tower && w - nw >= 4;
		boolean right = d.chance(0.5);
		int nx0 = side || tower ? (right ? 1 : w - 1 - nw) : (w - nw) / 2;
		int nx1 = nx0 + nw - 1;
		int cx = (nx0 + nx1) / 2;
		int zF = yard && dp >= 16 && !side ? 4 : 2;
		int z0 = zF;
		int z1 = dp - 2;
		int hn = tier <= 2 ? 6 : 7;
		int base = Grounds.raisedBase(d, nx0, nx1, z0);
		int plate = base + hn - 1;
		d.body(nx0, z0, nx1, z1);
		d.eave(plate);

		Body nave = new Body(nx0, z0, nx1, z1, base, new int[]{hn}, false, false);
		Groundwork.footings(d, nx0, z0, nx1, z1, base - 1);
		Groundwork.apron(d, nx0, z0, nx1, z1, base, 2);
		Grounds.forecourt(d, 0, w - 1, z0 - 1, !(yard && zF >= 4));
		Halls.floor(d, nx0 + 1, z0 + 1, nx1 - 1, z1 - 1, base - 1, true);
		int[] bays = Arches.piers(z0, z1, 4);
		Facade fac = new Facade();
		Walls.storey(d, nave, 0, fac);
		d.carve(nx0 + 1, base, z0 + 1, nx1 - 1, plate - 1, z1 - 1);
		// deep-set arched windows high in the flanks, battered buttresses between them
		for (Direction sd : new Direction[]{Dirs.LEFT, Dirs.RIGHT}) {
			Side s = nave.side(0, sd);
			for (int k = 0; k + 1 < bays.length; k++) {
				int mid = (bays[k] + bays[k + 1]) / 2;
				Sacred.lancet(d, s, mid, base + 2, 1, Math.max(2, hn - 4), false);
			}
			for (int k = 1; k < bays.length - 1; k++) {
				Sacred.buttress(d, s, s.index(bays[k]), base, base + hn - 3);
				Sacred.buttress(d, s, s.index(bays[k]), base, base + 1);
			}
		}
		// the flat roof behind its parapet, vigas showing (the roof kit's adobe habit)
		int deck = Roofs.flat(d, nx0, z0, nx1, z1, plate, true);
		// buttresses on the front corners
		Side front = nave.side(0, Dirs.FRONT);
		Halls.portal(d, front, cx, base, t, tier);
		Sacred.rose(d, front, front.index(cx), base + hn - 2 > base + 4 ? base + hn - 2 : base + 4);
		// the bell wall over the front
		int top = Towers.bellWall(d, front, front.index(cx), deck + 1, nw >= 9 ? 3 : 2, maxH);
		// a squat tower with a dome beside the front in towns
		if (tower) {
			int tx0 = right ? nx1 + 1 : nx0 - 3;
			int tz0 = z0;
			int size = 3;
			if (tx0 >= 0 && tx0 + size - 1 < w) {
				Groundwork.footings(d, tx0, tz0, tx0 + size - 1, tz0 + size - 1, base - 1);
				int yb = deck + 2;
				Towers.shaft(d, tx0, tz0, size, base, yb - 1, t, plate);
				Towers.belfry(d, tx0, tz0, size, yb, 3, t, true);
				top = Math.max(top, Towers.cap(d, tx0, tz0, size, yb + 3, Towers.Cap.DOME, maxH));
			}
		}
		// the steps and the way in
		if (base == 1) {
			Grounds.flight(d, cx - 1, cx + 1, z0 - 1, base, 1);
		}
		Grounds.approach(d, cx, base, z0 - 1);

		// --- inside: pews, an altar before a retablo of carved stone and candles
		Sacred.pews(d, nx0 + 1, nx1 - 1, z0 + 3, z1 - 3, base, cx, Dirs.BACK, nw >= 9);
		Sacred.altar(d, cx, base, z1 - 1, Dirs.FRONT, nw >= 9, cx + (nw >= 9 ? 2 : 1));
		Mat accent = d.mat(Role.ACCENT);
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int x = cx - 1; x <= cx + 1; x++) {
				for (int y = base + 2; y <= base + 3; y++) {
					d.set(x, y, z1, accent.full());
				}
			}
		}
		List<String> colours = Signs.clothColours(d);
		Signs.wallBanner(d, cx - 2 >= nx0 + 1 ? cx - 2 : cx, base + 3, z1 - 1, Dirs.FRONT, colours.get(0), List.<String[]>of(new String[]{"cross",
			colours.size() > 1 ? colours.get(1) : "white"}));
		Signs.wallBanner(d, cx + 2 <= nx1 - 1 ? cx + 2 : cx, base + 3, z1 - 1, Dirs.FRONT, colours.get(0), List.<String[]>of(new String[]{"cross",
			colours.size() > 1 ? colours.get(1) : "white"}));
		for (int k = 1; k < bays.length - 1; k++) {
			Halls.lanternUnder(d, cx, base, bays[k], 2);
		}
		d.inStage(StageKind.INTERIOR, () -> d.set(nx0 + 1, base, z0 + 1, Blocks.WATER_CAULDRON.defaultBlockState()
			.setValue(BlockStateProperties.LEVEL_CAULDRON, 3)));

		// --- the campo santo: before the church, or beside it, walled
		if (yard && zF >= 4) {
			Sacred.churchyard(d, 0, 1, cx - 2, z0 - 2, 0, true, Dirs.FRONT);
			Sacred.churchyard(d, cx + 2, 1, w - 1, z0 - 2, 0, true, Dirs.FRONT);
			java.util.List<int[]> run = new java.util.ArrayList<>();
			for (int x = 0; x < w; x++) {
				run.add(new int[]{x, 0});
			}
			Garden.fence(d, run, cx, Dirs.FRONT);
		} else if (side) {
			int gx0 = right ? nx1 + 2 : 0;
			int gx1 = right ? w - 1 : nx0 - 2;
			Sacred.churchyard(d, gx0, 2, gx1, dp - 2, 0, true, Dirs.FRONT);
			Halls.yardEdge(d, gx0, 1, gx1, dp - 1, 0, right ? Dirs.LEFT : Dirs.RIGHT);
		}
		for (int x : new int[]{Math.max(0, cx - 2), Math.min(w - 1, cx + 2)}) {
			if (Groundwork.surface(d, x, 0) == 0 && d.isEmpty(x, 0, 0) && !d.site().isWater(x, 0)) {
				Garden.pot(d, x, 0, 0);
			}
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.max(top, deck + 2) + 2);
	}

	static Half half() {
		return Half.BOTTOM;
	}
}
