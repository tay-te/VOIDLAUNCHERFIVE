package dev.voidmc.hearth.build.archetype.landscape;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.landscape.Benches;
import dev.voidmc.hearth.build.kit.landscape.Lamps;
import dev.voidmc.hearth.build.kit.landscape.Manner;
import dev.voidmc.hearth.build.kit.landscape.Manner.LampKind;
import dev.voidmc.hearth.build.kit.landscape.Planters;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;

/**
 * {@code hearth:landscape/lamp_post}: a street light standing on a little paved island of its own, in the culture's
 * manner (a timber crook on a stone foot, a double standard in towns, a karst stone lantern, a fire basket on a pillar
 * in the north and on the steppe, a tiki torch, a lantern on a sandstone pillar, a bayou lantern on a chain, an alpine
 * lantern under its little roof). The island is set in a contrasting course round the lamp's foot, with flowers or pots
 * in its corners and, on the bigger plots or by seed, benches back to back against the lamp so folk sit under the light.
 *
 * <p>The planner sets lamps at the corners of the square (3 × 3, facing the middle); the crook reaches toward the front.
 *
 * <p>Params: {@code benches} (boolean, default by seed: likelier on 5 × 5).
 */
public final class LampPost implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("landscape/lamp_post"), ArchetypeCategory.LANDSCAPE, Set.of(), 0,
		3, 5, 3, 5, 4, 7, SettlementTier.VILLAGE, Set.of(), Set.of(ArchetypeInfo.SiteFlag.CENTRAL), List.of(), 0, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int cx = (w - 1) / 2;
		int cz = (dp - 1) / 2;
		d.entrance(cx, 0, -1);
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		// the island: paving, a contrasting course round the foot
		Mat paving = d.mat(Role.PAVING);
		Mat band = Manner.masonry(d);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = 0; z < dp; z++) {
				for (int x = 0; x < w; x++) {
					boolean ring = Math.max(Math.abs(x - cx), Math.abs(z - cz)) == 1 && (w >= 5 || dp >= 5);
					d.set(x, -1, z, x == cx && z == cz || ring ? band.full() : paving.full());
				}
			}
		}
		LampKind kind = Manner.lamp(d);
		if (kind == LampKind.CROOK && (d.tier() >= 3 || d.chance(0.25))) {
			kind = LampKind.DOUBLE;
		}
		boolean benches = d.param("benches", d.chance(w >= 5 && dp >= 5 ? 0.85 : 0.45));
		int top = Lamps.standard(d, cx, 0, cz, d.maxHeight(), kind, kind == LampKind.DOUBLE ? Dirs.FRONT : Dirs.FRONT);
		if (benches && kind != LampKind.STONE_LANTERN) {
			// two seats back to back against the post, looking out along the square's edge
			Benches.bench(d, cx - 1, 0, cz, Dirs.LEFT, Dirs.BACK, 1, false);
			Benches.bench(d, cx + 1, 0, cz, Dirs.RIGHT, Dirs.BACK, 1, false);
		}
		// the corners: flowers set in the paving, or pots, or a clipped bush
		int[][] corners = {{0, 0}, {w - 1, 0}, {0, dp - 1}, {w - 1, dp - 1}};
		for (int[] c : corners) {
			if (!d.isAbsent(c[0], 0, c[1])) {
				continue;
			}
			double u = Noise.unit(d.seed() ^ 0x1A3B, c[0], 0, c[1]);
			if (u < 0.4) {
				Planters.bed(d, c[0], 0, c[1]);
			} else if (u < 0.75) {
				Garden.pot(d, c[0], 0, c[1]);
			} else if (u < 0.9 && top >= 3) {
				Planters.topiary(d, c[0], 0, c[1]);
			}
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, top + 1);
	}
}
