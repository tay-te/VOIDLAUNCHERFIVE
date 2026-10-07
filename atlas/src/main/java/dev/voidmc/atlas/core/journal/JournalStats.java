package dev.voidmc.atlas.core.journal;

import dev.voidmc.atlas.api.DiscoveryState;
import java.util.List;
import java.util.Map;

/**
 * A snapshot of one world's journal numbers, for the journal screen and the API.
 *
 * @param distance            horizontal blocks travelled, all dimensions together
 * @param distanceByDimension horizontal blocks travelled per dimension
 * @param deepestY            the lowest block Y the player stood at in any dimension; null before the first position
 * @param highestY            the highest block Y in any dimension; null before the first position
 * @param deepestByDimension  the lowest block Y per dimension
 * @param highestByDimension  the highest block Y per dimension
 * @param places              how many places there are, per kind and per state (as last given by the caller)
 * @param firstDay            the first game day seen; -1 if none yet
 * @param lastDay             the last game day seen; -1 if none yet
 * @param dimensions          the dimensions visited, in the order they were first visited
 * @param areasEntered        how many biome areas the player has entered
 * @param events              how many events the journal stands for (a summary counts for everything in it)
 * @param topAreas            the areas explored furthest, by explored share
 */
public record JournalStats(double distance, Map<String, Double> distanceByDimension, Integer deepestY, Integer highestY,
	Map<String, Integer> deepestByDimension, Map<String, Integer> highestByDimension, Map<String, Map<DiscoveryState, Integer>> places,
	long firstDay, long lastDay, List<String> dimensions, int areasEntered, int events, List<AreaStat> topAreas) {

	public JournalStats {
		distanceByDimension = Map.copyOf(distanceByDimension);
		deepestByDimension = Map.copyOf(deepestByDimension);
		highestByDimension = Map.copyOf(highestByDimension);
		places = Map.copyOf(places);
		dimensions = List.copyOf(dimensions);
		topAreas = List.copyOf(topAreas);
	}

	/** Kilometres travelled, all dimensions together. */
	public double kilometres() {
		return this.distance / 1000.0;
	}

	/** How many game days the player has played: the span from the first day seen to the last, both counted. */
	public long daysPlayed() {
		return this.firstDay < 0 ? 0 : this.lastDay - this.firstDay + 1;
	}

	/** How many places of {@code kind} are in {@code state}. */
	public int places(String kind, DiscoveryState state) {
		Map<DiscoveryState, Integer> byState = this.places.get(kind);
		return byState == null ? 0 : byState.getOrDefault(state, 0);
	}

	/** How many places of any kind are in {@code state}. */
	public int places(DiscoveryState state) {
		int n = 0;
		for (Map<DiscoveryState, Integer> byState : this.places.values()) {
			n += byState.getOrDefault(state, 0);
		}
		return n;
	}
}
