package dev.voidmc.hearth.social.service;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.social.life.Venues;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Who keeps which service, and the building they keep it in. */
public final class ServiceSites {
	static final Set<String> INNS = Set.of("commerce/inn", "commerce/tavern", "commerce/hostel", "commerce/caravanserai");
	static final Set<String> BATHS = Set.of("civic/bathhouse");
	static final Set<String> STABLES = Set.of("noble/stables", "commerce/caravan_yard", "commerce/caravanserai");
	static final Set<String> FARMS = Set.of("farm/field", "farm/orchard", "farm/paddy", "farm/terrace_field");
	static final Set<String> QUARRIES = Set.of("work/quarry", "extract/quarry_pit", "extract/mine", "extract/shaft", "work/masonry");

	private ServiceSites() {
	}

	/** The archetype path of the resident's workplace, or "". */
	public static String workPath(Settlement s, Resident r) {
		PlotView p = workplace(s, r);
		return p == null ? "" : p.spec().archetype().getPath();
	}

	public static @Nullable PlotView workplace(Settlement s, Resident r) {
		return r.work == Resident.NO_PLOT ? null : s.plot(r.work);
	}

	public static boolean innkeeper(Settlement s, Resident r) {
		return r.job == JobType.INNKEEPER || INNS.contains(workPath(s, r));
	}

	public static boolean bathKeeper(Settlement s, Resident r) {
		return r.job == JobType.BATH_KEEPER || BATHS.contains(workPath(s, r));
	}

	/** A stablemaster, or (where allowed) an innkeeper whose inn has a hitching post. */
	public static boolean stablemaster(Settlement s, Resident r, boolean innsHire) {
		if (r.job == JobType.STABLEMASTER || STABLES.contains(workPath(s, r))) {
			return true;
		}
		if (!innsHire || !innkeeper(s, r)) {
			return false;
		}
		PlotView inn = inn(s, r);
		return inn != null && !Venues.inPlot(s.pois(PoiType.HITCHING_POST), inn).isEmpty();
	}

	public static boolean farmhand(Settlement s, Resident r) {
		return r.job == JobType.FARMER || FARMS.contains(workPath(s, r));
	}

	public static boolean quarrier(Settlement s, Resident r) {
		return r.job == JobType.QUARRIER || r.job == JobType.MINER || r.job == JobType.MASON || QUARRIES.contains(workPath(s, r));
	}

	public static boolean builder(Settlement s, Resident r) {
		String w = workPath(s, r);
		return r.job == JobType.BUILDER || w.equals("work/builders_yard") || w.startsWith("storage/");
	}

	/** The inn the resident keeps (their workplace if it is one, else the town's first). */
	public static @Nullable PlotView inn(Settlement s, Resident r) {
		PlotView w = workplace(s, r);
		if (w != null && INNS.contains(w.spec().archetype().getPath())) {
			return w;
		}
		List<PlotView> inns = Venues.built(s, INNS.toArray(String[]::new));
		return inns.isEmpty() ? null : inns.get(0);
	}

	/** POIs of a type inside a plot. */
	public static List<Poi> pois(Settlement s, PlotView plot, PoiType type) {
		return Venues.inPlot(s.pois(type), plot);
	}
}
