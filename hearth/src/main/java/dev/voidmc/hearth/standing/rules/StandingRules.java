package dev.voidmc.hearth.standing.rules;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;
import net.minecraft.world.item.DyeColor;
import org.jspecify.annotations.Nullable;

/**
 * The standing module's data, as loaded from data packs ({@link StandingData}): what each kind of deed does, the words
 * folk use when they talk about deeds, and which dress counts as local in each culture. Immutable; a reload swaps the
 * whole set. Built-in defaults stand in for anything a pack does not define, so the module works without its data.
 *
 * @param deeds   deed kind → rule; {@code default} for kinds not listed
 * @param lines   deed kind (or {@code default_good}, {@code default_bad}, {@code title_<name>}) → lines with
 *                {@code {player}} and {@code {town}} placeholders
 * @param attire  culture name → dye colours of its everyday dress
 */
public record StandingRules(Map<String, DeedRule> deeds, Map<String, List<String>> lines, Map<String, Set<DyeColor>> attire) {
	public static final String DEFAULT = "default";

	private static volatile StandingRules current = new StandingRules(Map.of(), Map.of(), Map.of());

	public static StandingRules get() {
		return current;
	}

	public static void set(StandingRules rules) {
		current = rules;
	}

	/** The rule for a deed kind: its own, else the namespace's {@code <ns>:default}, else the global default. */
	public DeedRule rule(String kind) {
		DeedRule r = this.deeds.get(kind);
		if (r != null) {
			return r;
		}
		int colon = kind.indexOf(':');
		if (colon > 0) {
			r = this.deeds.get(kind.substring(0, colon) + ":default");
			if (r != null) {
				return r;
			}
		}
		r = this.deeds.get(DEFAULT);
		return r == null ? DeedRule.DEFAULT : r;
	}

	/**
	 * A line about a deed, filled in: the kind's own lines, else the good or bad default. Null if there is nothing to say.
	 */
	public @Nullable String line(String kind, int weight, String player, String town, RandomGenerator random) {
		List<String> l = this.lines.get(kind);
		if (l == null || l.isEmpty()) {
			l = this.lines.get(weight >= 0 ? "default_good" : "default_bad");
		}
		if (l == null || l.isEmpty()) {
			return null;
		}
		return fill(l.get(random.nextInt(l.size())), player, town);
	}

	/** A line for a key ({@code title_hero}, {@code refuse}…), or null. */
	public @Nullable String keyed(String key, String player, String town, RandomGenerator random) {
		List<String> l = this.lines.get(key);
		if (l == null || l.isEmpty()) {
			return null;
		}
		return fill(l.get(random.nextInt(l.size())), player, town);
	}

	/** The dye colours of a culture's everyday dress (empty if the data says nothing). */
	public Set<DyeColor> dress(String culture) {
		Set<DyeColor> s = this.attire.get(culture);
		return s == null ? Set.of() : s;
	}

	static String fill(String template, String player, String town) {
		return template.replace("{player}", player).replace("{town}", town);
	}

	/** Merges b over a (b wins per key), for packs layered over the built-in data. */
	static <K, V> Map<K, V> merge(Map<K, V> a, Map<K, V> b) {
		Map<K, V> m = new HashMap<>(a);
		m.putAll(b);
		return Map.copyOf(m);
	}
}
