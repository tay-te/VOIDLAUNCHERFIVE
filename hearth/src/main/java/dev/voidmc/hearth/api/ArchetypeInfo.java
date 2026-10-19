package dev.voidmc.hearth.api;

import java.util.List;
import java.util.Set;
import net.minecraft.resources.Identifier;

/**
 * What the planner needs to know about a kind of building before it asks for one: what need it answers, what land it
 * fits, and who can build it. Footprints are measured relative to the street: frontage along it, depth away from it.
 *
 * @param jobs          the jobs this building provides workplaces for (a bakery: BAKER), empty if none
 * @param beds          typical number of beds (housing capacity) at the minimum size; the blueprint reports the real count
 * @param minTier       the smallest settlement that builds one
 * @param cultures      the cultures that build it; empty means all
 * @param flags         what land and neighbours it suits
 * @param upgradesTo    archetypes a plot of this kind can be rebuilt as when the settlement grows (cottage → townhouse)
 * @param maxPerSettlement 0 for unlimited; 1 for landmarks, town halls, mints
 * @param weight        relative preference among archetypes that answer the same need
 */
public record ArchetypeInfo(Identifier id, ArchetypeCategory category, Set<JobType> jobs, int beds,
	int minFrontage, int maxFrontage, int minDepth, int maxDepth, int minHeight, int maxHeight,
	SettlementTier minTier, Set<Culture> cultures, Set<SiteFlag> flags, List<Identifier> upgradesTo,
	int maxPerSettlement, double weight) {

	public boolean suits(Culture culture) {
		return this.cultures.isEmpty() || this.cultures.contains(culture);
	}

	/** Archetypes whose beds are guest beds, let to travellers: they house nobody (the town builds homes instead). */
	public static final Set<String> GUEST_HOUSES = Set.of("commerce/inn", "commerce/tavern", "commerce/hostel", "commerce/caravanserai");

	/** Whether an archetype's beds are guest beds rather than homes. */
	public static boolean guestHouse(Identifier archetype) {
		return GUEST_HOUSES.contains(archetype.getPath());
	}

	/** The beds of this archetype that house residents: none in a guest house. */
	public int homeBeds() {
		return guestHouse(this.id) ? 0 : this.beds;
	}

	public enum SiteFlag {
		/** Can share side walls with neighbours (rowhouses, shops on a high street): params {@code party_left/right}. */
		ATTACHABLE,
		/** Can step down a slope across its depth (split levels, a lower storey opening downhill). */
		HILLSIDE,
		/** Stands over water on stilts or piles. */
		ON_WATER,
		/** Needs a water edge along its front or back (docks, watermills, fisheries). */
		WATERFRONT,
		/** Tall: three storeys or more, towers, spires. The planner places these where height reads well. */
		VERTICAL,
		/** Can be built on top of another building's roof or a terrace deck (stacked dense districts). */
		STACKABLE,
		/** Belongs at the settlement's heart (well, bell, notice board, market). */
		CENTRAL,
		/** Belongs at the settlement's edge (farms, quarries, kilns, tanneries, walls). */
		OUTSKIRTS
	}
}
