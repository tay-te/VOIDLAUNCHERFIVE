package dev.voidmc.hearth.social.events;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.sim.SimServer;
import dev.voidmc.hearth.sim.roads.Band;
import dev.voidmc.hearth.sim.roads.Roads;
import dev.voidmc.hearth.sim.roads.Traffic;
import dev.voidmc.hearth.sim.roads.Way;
import dev.voidmc.hearth.sim.world.SimWorld;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The bands on the roads, as the raids read them (docs/LIVING_WORLD_2.md §3.2: "bands and the bailiff's journey live in
 * §1's traffic data"). Read-only over lane A1's books ({@code sim/roads/Traffic}); a band that raids is hurt or grows
 * through the same books. Soft: with no roads (the roads switched off, a world without them) there are no bands, and no
 * raids.
 */
final class RoadBands {
	private RoadBands() {
	}

	static @Nullable Traffic traffic(HearthWorld w) {
		try {
			SimWorld sw = SimServer.world(w.level());
			Roads.State st = sw == null ? null : Roads.state(sw);
			return st == null ? null : st.traffic();
		} catch (RuntimeException | LinkageError e) {
			return null;
		}
	}

	static List<RaidRules.BandView> of(HearthWorld w) {
		List<RaidRules.BandView> out = new ArrayList<>();
		try {
			SimWorld sw = SimServer.world(w.level());
			Roads.State st = sw == null ? null : Roads.state(sw);
			if (st == null) {
				return out;
			}
			for (Band b : st.traffic().bands()) {
				Way way = st.net().known(b.road());
				if (way == null) {
					continue;
				}
				out.add(new RaidRules.BandView(b.id(), b.strength(), b.morale(), way.a(), way.b(), b.along(), way.length(), b.camp().getX(),
					b.camp().getZ()));
			}
		} catch (RuntimeException | LinkageError e) {
			Hearth.LOG.debug("events: the roads' bands could not be read", e);
		}
		return out;
	}

	/** A band that raided: it grows when it got away with it, loses men when it did not. */
	static void raided(HearthWorld w, long bandId, boolean sacked, int lost) {
		Traffic t = traffic(w);
		if (t == null || bandId == 0) {
			return;
		}
		try {
			if (sacked && lost == 0) {
				t.hurtBand(bandId, -1); // the band grows on its plunder
			} else if (lost > 0) {
				t.hurtBand(bandId, lost);
			}
		} catch (RuntimeException e) {
			Hearth.LOG.debug("events: the band could not be told of its raid", e);
		}
	}

	/** Where a band camps, for the raiders' tracks and the camp quest, or null. */
	static net.minecraft.core.@Nullable BlockPos camp(HearthWorld w, long bandId) {
		Traffic t = traffic(w);
		Band b = t == null ? null : t.band(bandId);
		return b == null ? null : b.camp();
	}

	/** The band's spot on its road, where its party comes from, or null. */
	static net.minecraft.core.@Nullable BlockPos road(HearthWorld w, long bandId) {
		Traffic t = traffic(w);
		Band b = t == null ? null : t.band(bandId);
		if (b == null) {
			return null;
		}
		try {
			SimWorld sw = SimServer.world(w.level());
			Roads.State st = sw == null ? null : Roads.state(sw);
			Way way = st == null ? null : st.net().known(b.road());
			if (way == null) {
				return b.camp();
			}
			return way.view(1).at(b.along());
		} catch (RuntimeException e) {
			return b.camp();
		}
	}
}
