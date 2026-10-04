package dev.voidmc.hearth.quest.gen;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.ResourceSite;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.quest.model.SettlementQuests;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What a town is going through, as quest templates ask it in {@code when.requires}: its stalled builds and short
 * workshops, its spent sites, its crime, its damage, its decline, the trades and sites it has. Read once per board refresh
 * from the settlement's record and quest data (no world access), so officials' quests follow the town's real state.
 *
 * <p>Conditions, each optionally negated with a leading {@code !}:
 * <pre>
 *   stalled            a build is stalled for goods          shortage   a workshop is short of an input
 *   depleted           a resource site is spent              crime      crime has been seen here lately
 *   damaged            buildings wait for repair             declining  the town's fortunes are falling
 *   embodied           a player is near                      walled     the town is a village or larger
 *   job:&lt;job&gt;          someone works this trade              site:&lt;kind&gt; a live site of this kind in the territory
 *   plot:&lt;prefix&gt;      a plot whose archetype path starts so  event:&lt;kind&gt; an unresolved happening of this kind
 * </pre>
 */
public record TownState(Set<String> flags, Set<JobType> jobs, Set<ResourceSite.Kind> sites, Set<String> archetypes, Set<String> events) {
	private static final Set<String> FLAGS = Set.of("stalled", "shortage", "depleted", "crime", "damaged", "declining", "embodied", "walled");
	/** Recent crime at or above this counts as a town with crime. */
	public static final int CRIME_THRESHOLD = 2;

	/** The state of a settlement now. {@code damaged} is the number of blocks waiting for repair (the repair service's). */
	public static TownState of(Settlement s, SettlementQuests data, int damaged) {
		Set<String> flags = new HashSet<>();
		for (SettlementQuests.StalledNeed n : data.needs) {
			flags.add(n.contract() ? "shortage" : "stalled");
		}
		Set<JobType> jobs = EnumSet.noneOf(JobType.class);
		for (Resident r : s.residents()) {
			jobs.add(r.job);
		}
		Set<ResourceSite.Kind> sites = EnumSet.noneOf(ResourceSite.Kind.class);
		for (ResourceSite site : s.sites()) {
			if (site.depleted()) {
				flags.add("depleted");
			} else {
				sites.add(site.kind());
			}
		}
		Set<String> archetypes = new HashSet<>();
		for (PlotView p : s.plots()) {
			if (p.state() != PlotState.PLANNED) {
				archetypes.add(p.spec().archetype().getPath());
			}
		}
		Set<String> events = new HashSet<>();
		for (SettlementQuests.Happening h : data.happenings) {
			if (!h.resolved()) {
				events.add(h.kind());
				if (h.kind().equals(SettlementQuests.Happening.DEPLETED)) {
					flags.add("depleted");
				}
			}
		}
		if (data.crime >= CRIME_THRESHOLD) {
			flags.add("crime");
		}
		if (damaged > 0) {
			flags.add("damaged");
		}
		if (data.declining()) {
			flags.add("declining");
		}
		if (s.embodied()) {
			flags.add("embodied");
		}
		if (s.tier().ordinal() >= dev.voidmc.hearth.api.SettlementTier.VILLAGE.ordinal()) {
			flags.add("walled");
		}
		return new TownState(flags, jobs, sites, archetypes, events);
	}

	/** Whether every condition holds. */
	public boolean all(List<String> conditions) {
		for (String c : conditions) {
			if (!test(c)) {
				return false;
			}
		}
		return true;
	}

	public boolean test(String condition) {
		String c = condition.trim().toLowerCase(Locale.ROOT);
		if (c.startsWith("!")) {
			return !test(c.substring(1));
		}
		int colon = c.indexOf(':');
		if (colon < 0) {
			return this.flags.contains(c);
		}
		String arg = c.substring(colon + 1);
		return switch (c.substring(0, colon)) {
			case "job" -> this.jobs.stream().anyMatch(j -> j.getSerializedName().equals(arg));
			case "site" -> this.sites.stream().anyMatch(k -> k.getSerializedName().equals(arg));
			case "plot" -> this.archetypes.stream().anyMatch(a -> a.startsWith(arg));
			case "event" -> this.events.contains(arg);
			default -> false;
		};
	}

	/** Why a condition cannot be understood, or null if it is well formed (for the template validator). */
	public static @org.jspecify.annotations.Nullable String problem(String condition) {
		String c = condition.trim().toLowerCase(Locale.ROOT);
		if (c.startsWith("!")) {
			c = c.substring(1);
		}
		int colon = c.indexOf(':');
		if (colon < 0) {
			return FLAGS.contains(c) ? null : "unknown condition '" + condition + "' (one of " + String.join(", ", FLAGS.stream().sorted().toList())
				+ ", job:, site:, plot:, event:)";
		}
		String arg = c.substring(colon + 1);
		String head = c.substring(0, colon);
		return switch (head) {
			case "job" -> java.util.Arrays.stream(JobType.values()).anyMatch(j -> j.getSerializedName().equals(arg)) ? null : "unknown job '" + arg + "'";
			case "site" -> java.util.Arrays.stream(ResourceSite.Kind.values()).anyMatch(k -> k.getSerializedName().equals(arg)) ? null
				: "unknown site kind '" + arg + "'";
			case "plot" -> arg.isEmpty() ? "plot: needs an archetype path prefix" : null;
			case "event" -> SettlementQuests.Happening.KINDS.contains(arg) ? null : "unknown event '" + arg + "'";
			default -> "unknown condition '" + condition + "'";
		};
	}
}
