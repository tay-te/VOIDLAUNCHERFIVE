package dev.voidmc.hearth.build.kit.light;

import dev.voidmc.hearth.api.PlotSpec;
import java.util.ArrayList;
import java.util.List;

/**
 * The street-light standard of roads (docs/buildings/infra.md): streets five wide carry lamps on their kerb lines every
 * 20 blocks at a camp, 14 in a village or town and 10 in a city, laid on the world grid so a road built in several
 * plots keeps one rhythm; lanes, alleys, paths, tracks and highways carry none (dark on purpose: the roads between
 * towns, and the lanes behind them, are dangerous at night).
 *
 * <p>A road plot does not know where its neighbours' lamps stand, so its rule is about its own: in a village or larger,
 * every walkable cell of the surface within half a spacing of one of its lamps (the reach the rhythm promises each
 * lamp) is never dark. A camp's street is exempt: its light is the brazier at the heart of the camp.
 */
public final class RoadLight {
	private RoadLight() {
	}

	/** Blocks between street lamps at a tier (what {@code kit/infra/Dress} lays). */
	public static int spacing(int tier) {
		return tier >= 3 ? 10 : tier == 0 ? 20 : 14;
	}

	public static List<LightRules.Need> needs(Spaces sp, LightField light, PlotSpec spec) {
		if (spec.tier() < 2) {
			return new ArrayList<>();
		}
		return lampReach(sp, spec.tier(), LightRules.Kind.STREET);
	}

	/**
	 * The open walkable cells of the plot within half the tier's lamp spacing of one of its own lamps (and up to four
	 * blocks under it): what the rhythm of lamps promises to light.
	 */
	public static List<LightRules.Need> lampReach(Spaces sp, int tier, LightRules.Kind kind) {
		List<LightRules.Need> out = new ArrayList<>();
		Grid g = sp.grid();
		List<int[]> lamps = new ArrayList<>();
		for (int y = g.y0; y < g.y0 + g.sy; y++) {
			for (int z = g.z0; z < g.z0 + g.sz; z++) {
				for (int x = g.x0; x < g.x0 + g.sx; x++) {
					if (g.placed(x, y, z) && g.inPlot(x, z) && g.at(x, y, z).getLightEmission() > 0) {
						lamps.add(new int[]{x, y, z});
					}
				}
			}
		}
		if (lamps.isEmpty()) {
			return out;
		}
		int reach = spacing(tier) / 2;
		for (int y = g.y0; y < g.y0 + g.sy; y++) {
			for (int z = g.z0; z < g.z0 + g.sz; z++) {
				for (int x = g.x0; x < g.x0 + g.sx; x++) {
					if (!g.inPlot(x, z) || !sp.walkable(x, y, z) || sp.wet(x, y, z) || !sp.reached(x, y, z) || sp.roofed(x, y, z)) {
						continue;
					}
					for (int[] l : lamps) {
						if (Math.max(Math.abs(l[0] - x), Math.abs(l[2] - z)) <= reach && y <= l[1] && l[1] - y <= 4) {
							out.add(new LightRules.Need(x, y, z, LightRules.NEVER_DARK, kind));
							break;
						}
					}
				}
			}
		}
		return out;
	}
}
