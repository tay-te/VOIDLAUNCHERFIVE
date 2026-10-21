package dev.voidmc.hearth.social.events;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.social.FolkFacts;
import dev.voidmc.hearth.social.SocialModule;
import dev.voidmc.hearth.social.calendar.TownCalendar;
import dev.voidmc.hearth.social.life.Venues;
import dev.voidmc.hearth.social.office.Office;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;

/**
 * Who plays the parts of a scene (docs/LIVING_WORLD_2.md §3.3): the principals and their kin as the event names them,
 * the officials by office, and the rest by trade or age from among the town's residents. A pure choice over the
 * settlement (seeded by the event), so the same scene casts the same folk.
 */
public final class SceneCast {
	/** Every pick a rule's roles may use. */
	public static final Set<String> PICKS = Set.of("principals", "principal", "family", "kin", "household", "priest", "judge", "lord", "reeve",
		"steward", "jailer", "captain", "guards", "builders", "master_builder", "children", "entertainer", "crier", "butcher", "neighbours", "adults",
		"defendant", "witnesses", "competitors", "knights", "champion", "elders", "herald", "parents");

	private SceneCast() {
	}

	/**
	 * The residents for each role, in the rules' order; nobody plays two parts.
	 *
	 * @param near where the scene is (neighbours are those living nearest it)
	 */
	public static Map<String, List<UUID>> cast(Settlement s, Scheduled e, EventRule rule, BlockPos near) {
		Map<String, List<UUID>> out = new java.util.LinkedHashMap<>();
		Set<UUID> taken = new LinkedHashSet<>();
		for (EventRule.RoleSpec role : rule.roles()) {
			List<UUID> pool = pool(s, e, role.pick(), near, taken);
			List<UUID> chosen = new ArrayList<>();
			for (UUID id : pool) {
				if (chosen.size() >= role.count()) {
					break;
				}
				if (taken.add(id)) {
					chosen.add(id);
				}
			}
			out.put(role.name(), chosen);
		}
		return out;
	}

	static List<UUID> pool(Settlement s, Scheduled e, String pick, BlockPos near, Set<UUID> taken) {
		Map<String, UUID> offices = s.data(SocialModule.LEDGER).offices;
		long seed = e.seed() ^ pick.hashCode();
		return switch (pick) {
			case "principals", "principal", "defendant" -> e.principals();
			case "family" -> e.family();
			case "kin" -> {
				List<UUID> kin = new ArrayList<>();
				for (UUID p : e.principals()) {
					Resident r = s.resident(p);
					if (r != null) {
						kin.addAll(FolkFacts.family(s, r));
					}
				}
				kin.addAll(e.family());
				yield kin;
			}
			case "parents" -> {
				List<UUID> parents = new ArrayList<>();
				for (UUID p : e.principals()) {
					Resident r = s.resident(p);
					if (r != null) {
						parents.addAll(FolkFacts.parents(r));
					}
				}
				yield parents;
			}
			case "household" -> by(s, r -> e.plot() >= 0 && r.home == e.plot(), seed);
			case "priest" -> office(s, offices, Office.PRIEST, r -> r.job == JobType.PRIEST || r.job == JobType.MONK, seed);
			case "judge" -> {
				List<UUID> j = office(s, offices, Office.JUDGE, r -> r.job == JobType.JUDGE, seed);
				if (j.isEmpty()) {
					j = office(s, offices, Office.LORD, r -> r.job == JobType.LORD, seed);
				}
				yield j.isEmpty() ? office(s, offices, Office.REEVE, r -> r.job == JobType.REEVE, seed) : j;
			}
			case "lord" -> office(s, offices, Office.LORD, r -> r.job == JobType.LORD, seed);
			case "reeve" -> {
				List<UUID> r0 = office(s, offices, Office.REEVE, r -> r.job == JobType.REEVE, seed);
				yield r0.isEmpty() ? office(s, offices, Office.STEWARD, r -> r.job == JobType.STEWARD, seed) : r0;
			}
			case "steward" -> office(s, offices, Office.STEWARD, r -> r.job == JobType.STEWARD, seed);
			case "jailer" -> office(s, offices, Office.JAILER, r -> r.job == JobType.JAILER || r.job == JobType.GUARD, seed);
			case "captain", "herald" -> office(s, offices, Office.GUARD_CAPTAIN, r -> r.job == JobType.GUARD, seed);
			case "guards" -> by(s, r -> r.job == JobType.GUARD && r.age == AgeStage.ADULT, seed);
			case "champion" -> strongest(s, r -> r.job == JobType.GUARD && r.age == AgeStage.ADULT);
			case "builders" -> by(s, r -> r.age == AgeStage.ADULT && (r.job == JobType.BUILDER || r.job == JobType.CARPENTER || r.job == JobType.MASON), seed);
			case "master_builder" -> office(s, offices, Office.MASTER_BUILDER, r -> r.job == JobType.BUILDER || r.job == JobType.CARPENTER, seed);
			case "children" -> by(s, r -> r.age == AgeStage.CHILD, seed);
			case "elders" -> by(s, r -> r.age == AgeStage.ELDER, seed);
			case "entertainer" -> {
				List<UUID> ent = by(s, r -> r.job == JobType.ENTERTAINER && r.age == AgeStage.ADULT, seed);
				yield ent.isEmpty() ? by(s, r -> r.age == AgeStage.ADULT && (FolkFacts.has(r, "cheerful") || FolkFacts.has(r, "sociable")), seed) : ent;
			}
			case "crier" -> {
				List<UUID> c = by(s, r -> r.job == JobType.COURIER && r.age == AgeStage.ADULT, seed);
				yield c.isEmpty() ? office(s, offices, Office.REEVE, r -> r.job == JobType.REEVE, seed) : c;
			}
			case "butcher" -> {
				List<UUID> b = by(s, r -> r.job == JobType.BUTCHER, seed);
				yield b.isEmpty() ? by(s, r -> r.job == JobType.FARMER || r.job == JobType.HERDER, seed) : b;
			}
			case "competitors" -> {
				List<UUID> c = by(s, r -> r.age == AgeStage.ADULT && (r.job == JobType.HUNTER || r.job == JobType.GUARD || r.job == JobType.FORESTER), seed);
				if (c.size() < 3) {
					c = new ArrayList<>(c);
					c.addAll(by(s, r -> r.age == AgeStage.ADULT, seed));
				}
				yield c;
			}
			case "knights" -> {
				List<UUID> k = by(s, r -> r.age == AgeStage.ADULT && (r.station == Station.GENTRY || r.station == Station.LORD), seed);
				if (k.size() < 2) {
					k = new ArrayList<>(k);
					k.addAll(by(s, r -> r.job == JobType.GUARD && r.age == AgeStage.ADULT, seed));
				}
				yield k;
			}
			case "neighbours" -> {
				List<Resident> rs = new ArrayList<>();
				for (Resident r : s.residents()) {
					if (r.age != AgeStage.CHILD && r.home != Resident.NO_PLOT && !e.involves(r.id)) {
						rs.add(r);
					}
				}
				rs.sort(Comparator.comparingDouble(r -> homeDistance(s, r, near)));
				List<UUID> ids = new ArrayList<>();
				for (Resident r : rs) {
					ids.add(r.id);
				}
				yield ids;
			}
			case "witnesses" -> by(s, r -> r.age == AgeStage.ADULT && !e.involves(r.id) && r.job != JobType.GUARD, seed);
			default -> by(s, r -> r.age == AgeStage.ADULT && !e.involves(r.id), seed); // adults and anyone else
		};
	}

	private static double homeDistance(Settlement s, Resident r, BlockPos near) {
		PlotView p = s.plot(r.home);
		return p == null ? Double.MAX_VALUE : Venues.middle(p.spec()).distSqr(near);
	}

	private static List<UUID> office(Settlement s, Map<String, UUID> offices, Office o, Predicate<Resident> fallback, long seed) {
		UUID holder = offices.get(o.id());
		if (holder != null && s.resident(holder) != null) {
			return List.of(holder);
		}
		return by(s, fallback, seed);
	}

	/** Residents matching, in a seeded order (the same scene casts the same folk). */
	static List<UUID> by(Settlement s, Predicate<Resident> want, long seed) {
		List<Resident> rs = new ArrayList<>();
		for (Resident r : s.residents()) {
			if (want.test(r)) {
				rs.add(r);
			}
		}
		rs.sort(Comparator.comparingLong(r -> TownCalendar.mix(r.id.getLeastSignificantBits() ^ seed)));
		List<UUID> ids = new ArrayList<>(rs.size());
		for (Resident r : rs) {
			ids.add(r.id);
		}
		return ids;
	}

	private static List<UUID> strongest(Settlement s, Predicate<Resident> want) {
		Resident best = null;
		for (Resident r : s.residents()) {
			if (want.test(r) && (best == null || r.bornDay < best.bornDay)) {
				best = r;
			}
		}
		return best == null ? List.of() : List.of(best.id);
	}
}
