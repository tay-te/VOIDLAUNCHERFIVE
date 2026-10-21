package dev.voidmc.hearth.social.events;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * What players did to an event while it ran (docs/LIVING_WORLD_2.md §3.2, §3.5): testimony, a fine paid, raiders killed,
 * the fire fought, a contest score. The first player to intervene sets it going, others add to it (more defenders). An
 * event nobody touched resolves exactly as it would have abstractly. Not saved: an event in progress at a restart
 * resolves as if nobody had.
 */
public final class Interventions {
	private final Map<String, Double> values = new TreeMap<>();
	private final Set<UUID> players = new LinkedHashSet<>();
	private @Nullable String bestName;

	public static final Interventions NONE = new Interventions();

	public boolean any() {
		return !this.values.isEmpty();
	}

	public double get(String key) {
		return this.values.getOrDefault(key, 0.0);
	}

	public boolean flag(String key) {
		return get(key) != 0;
	}

	/** Adds to a number (testimony, raiders killed, players fighting the fire). */
	public void add(UUID player, String key, double amount) {
		if (this == NONE) {
			throw new IllegalStateException("the empty interventions are shared");
		}
		this.values.merge(key, amount, Double::sum);
		this.players.add(player);
	}

	/** Sets a number to the best seen (a contest's best score), remembering who. */
	public void best(UUID player, String name, String key, double value) {
		if (this == NONE) {
			throw new IllegalStateException("the empty interventions are shared");
		}
		Double old = this.values.get(key);
		if (old == null || value > old) {
			this.values.put(key, value);
			this.bestName = name;
		}
		this.players.add(player);
	}

	public void set(UUID player, String key) {
		add(player, key, 1);
		this.values.put(key, 1.0);
	}

	public Set<UUID> players() {
		return this.players;
	}

	public @Nullable String bestName() {
		return this.bestName;
	}

	@Override
	public String toString() {
		return this.values.toString();
	}
}
