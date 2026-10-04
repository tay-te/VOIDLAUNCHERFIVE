package dev.voidmc.hearth.plan.society;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Who lives where (LIVING_WORLD.md §8.1): the station a resident's household counts as, the station a home serves, the
 * homes each station asks for, and how a station's home is finished. The planner reads stations from
 * {@code Resident.station} (the npc module keeps it following work and purse) and writes the {@code station} param on
 * every home it plans.
 */
public final class Stations {
	/** Homes each station asks for, best first (archetypes the build module has not registered fall back). */
	private static final List<List<Identifier>> HOMES = List.of(
		// poor: hovels and shacks by the tannery and the fields; rooms in a tenement in a city
		List.of(Archetypes.HOVEL, Archetypes.SHACK, Archetypes.HUT, Archetypes.TENEMENT),
		// peasant: crofts and cottages by the fields, farmhouses out among them, the culture's own house
		List.of(Archetypes.CROFT, Archetypes.COTTAGE, Archetypes.FARMHOUSE, Archetypes.LONGHOUSE, Archetypes.ROUND_HOUSE, Archetypes.HUT),
		// artisan: a house over the workshop on the high street
		List.of(Archetypes.SHOPHOUSE, Archetypes.ROWHOUSE, Archetypes.COTTAGE),
		// merchant: a merchant's house with its counting room by the market, a townhouse, a courtyard house
		List.of(Archetypes.MERCHANT_HOUSE, Archetypes.TOWNHOUSE, Archetypes.COURTYARD_HOUSE, Archetypes.SHOPHOUSE),
		// clergy: the rectory by the chapel
		List.of(Archetypes.RECTORY, Archetypes.COTTAGE),
		// scholar: a scribe's house by the library
		List.of(Archetypes.SCRIBE_HOUSE, Archetypes.TOWNHOUSE, Archetypes.ROWHOUSE, Archetypes.COTTAGE),
		// official: the reeve's house by the hall
		List.of(Archetypes.REEVE_HOUSE, Archetypes.TOWNHOUSE, Archetypes.COTTAGE),
		// gentry: manors on the hill
		List.of(Archetypes.MANOR, Archetypes.COURTYARD_HOUSE, Archetypes.TOWER_HOUSE, Archetypes.TOWNHOUSE),
		// lord: the keep, the castle, the palace (planned by Nobility, not as ordinary homes)
		List.of(Archetypes.KEEP, Archetypes.CASTLE, Archetypes.PALACE, Archetypes.MANOR));

	private Stations() {
	}

	/**
	 * The station a resident's household counts as for housing. Below village tier everyone without a trade works the
	 * land (there is no poor quarter in a hamlet), and a station still at the "no work" default while the resident holds a
	 * job is read as that job's (a record the npc module has not yet updated).
	 */
	public static Station of(Resident r, SettlementTier tier) {
		Station s = r.station == null ? Station.of(r.job) : r.station;
		if (s == Station.POOR && r.job != JobType.NONE && Station.of(r.job) != Station.POOR) {
			s = Station.of(r.job);
		}
		if (s == Station.POOR && tier.ordinal() < SettlementTier.VILLAGE.ordinal()) {
			s = Station.PEASANT;
		}
		if ((s == Station.GENTRY || s == Station.LORD) && tier.ordinal() < SettlementTier.TOWN.ordinal()) {
			// a hamlet's headman lives in a better house, not a manor
			s = Station.MERCHANT;
		}
		return s;
	}

	/** Whether a resident heads a household for housing (adults; children and elders live with them). */
	public static boolean householder(Resident r) {
		return r.age == AgeStage.ADULT;
	}

	/** The homes a station asks for, best first. */
	public static List<Identifier> homes(Station s) {
		return HOMES.get(s.ordinal());
	}

	/**
	 * The station a home serves: its {@code station} param, else what its archetype is built for (plots planned before
	 * stations, or by another planner). Null when the plot is no home.
	 */
	public static @Nullable Station home(PlotSpec spec, @Nullable ArchetypeInfo info) {
		if (info == null || info.beds() <= 0 || !isHome(info)) {
			return null;
		}
		String name = spec.params().getStringOr("station", "");
		if (!name.isEmpty()) {
			for (Station s : Station.values()) {
				if (s.getSerializedName().equals(name)) {
					return s;
				}
			}
		}
		return inferred(spec.archetype());
	}

	/** Homes: houses, the nobility's seats, deep dwellings, and the almshouses and orphanages that house the poor. */
	public static boolean isHome(ArchetypeInfo info) {
		ArchetypeCategory c = info.category();
		if (c == ArchetypeCategory.HOUSING || c == ArchetypeCategory.UNDERGROUND) {
			return true;
		}
		Identifier id = info.id();
		return id.equals(Archetypes.KEEP) || id.equals(Archetypes.CASTLE) || id.equals(Archetypes.PALACE) || id.equals(Archetypes.ALMSHOUSE)
			|| id.equals(Archetypes.ORPHANAGE);
	}

	/** The station an archetype is built for, when nothing else says. */
	public static Station inferred(Identifier id) {
		if (id.equals(Archetypes.HOVEL) || id.equals(Archetypes.SHACK) || id.equals(Archetypes.TENEMENT) || id.equals(Archetypes.ALMSHOUSE)
			|| id.equals(Archetypes.ORPHANAGE) || id.equals(Archetypes.HERMIT_HUT)) {
			return Station.POOR;
		}
		if (id.equals(Archetypes.ROWHOUSE) || id.equals(Archetypes.SHOPHOUSE)) {
			return Station.ARTISAN;
		}
		if (id.equals(Archetypes.TOWNHOUSE) || id.equals(Archetypes.MERCHANT_HOUSE) || id.equals(Archetypes.COURTYARD_HOUSE)
			|| id.equals(Archetypes.TOWER_HOUSE)) {
			return Station.MERCHANT;
		}
		if (id.equals(Archetypes.RECTORY)) {
			return Station.CLERGY;
		}
		if (id.equals(Archetypes.SCRIBE_HOUSE)) {
			return Station.SCHOLAR;
		}
		if (id.equals(Archetypes.REEVE_HOUSE)) {
			return Station.OFFICIAL;
		}
		if (id.equals(Archetypes.MANOR)) {
			return Station.GENTRY;
		}
		if (id.equals(Archetypes.KEEP) || id.equals(Archetypes.CASTLE) || id.equals(Archetypes.PALACE)) {
			return Station.LORD;
		}
		return Station.PEASANT;
	}

	/**
	 * How strongly a station wants an archetype, given that the density band already allows it: the station's own home
	 * most, plain houses some, another station's home hardly at all.
	 */
	public static double affinity(Station s, Identifier id) {
		List<Identifier> own = homes(s);
		int i = own.indexOf(id);
		if (i >= 0) {
			return Math.max(0.7, 3.4 - i * 0.9);
		}
		boolean special = id.equals(Archetypes.HOVEL) || id.equals(Archetypes.SHACK) || id.equals(Archetypes.CROFT) || id.equals(Archetypes.MERCHANT_HOUSE)
			|| id.equals(Archetypes.RECTORY) || id.equals(Archetypes.SCRIBE_HOUSE) || id.equals(Archetypes.REEVE_HOUSE) || id.equals(Archetypes.MANOR)
			|| id.equals(Archetypes.HERMIT_HUT);
		if (special) {
			return 0.02;
		}
		// the culture's own vernacular houses (stilt, cliff, round, long, courtyard) serve every station in their lands
		return switch (s) {
			case POOR, PEASANT -> 0.6;
			case ARTISAN, CLERGY, SCHOLAR, OFFICIAL -> 0.45;
			case MERCHANT, GENTRY, LORD -> 0.3;
		};
	}

	/** Finish offset for a station's home: rough for the poor, fine for gentry and the lord. */
	public static int finish(Station s) {
		return switch (s) {
			case POOR -> -1;
			case PEASANT, ARTISAN, CLERGY, SCHOLAR, OFFICIAL -> 0;
			case MERCHANT, GENTRY -> 1;
			case LORD -> 2;
		};
	}

	/** Where in its archetype's size range a station's home falls: small for the poor, broad for the rich. */
	public static double sizeBias(Station s) {
		return switch (s) {
			case POOR -> -0.35;
			case PEASANT -> -0.1;
			case ARTISAN, CLERGY, SCHOLAR -> 0;
			case OFFICIAL -> 0.1;
			case MERCHANT -> 0.25;
			case GENTRY, LORD -> 0.4;
		};
	}

	/** Storeys a station's home suggests, before the density band raises it. */
	public static int storeys(Station s, int base) {
		return switch (s) {
			case POOR -> 1;
			case PEASANT -> Math.min(base, 2);
			case ARTISAN -> Math.max(2, base);
			case MERCHANT, GENTRY -> Math.max(2, base) + (base >= 3 ? 1 : 0);
			default -> base;
		};
	}
}
