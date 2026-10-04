package dev.voidmc.hearth.plan.testing;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.ResourceSite;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * The api's in-memory settlement with a territory and resource sites (LIVING_WORLD.md §7.1), as the sim module's survey
 * would give it: for the planner's tests and previews ({@link MiniSim} fills it with {@link MiniSurvey}).
 */
public class SurveyedSettlement extends SimpleSettlement {
	private List<ResourceSite> sites = List.of();
	private int territoryRadius;

	public SurveyedSettlement(long id, String name, Culture culture, BlockPos center, long foundedDay) {
		super(id, name, culture, center, foundedDay);
	}

	public void setSites(List<ResourceSite> sites) {
		this.sites = List.copyOf(sites);
	}

	public void setTerritoryRadius(int radius) {
		this.territoryRadius = radius;
	}

	@Override
	public int territoryRadius() {
		return this.territoryRadius;
	}

	@Override
	public List<ResourceSite> sites() {
		return this.sites;
	}
}
