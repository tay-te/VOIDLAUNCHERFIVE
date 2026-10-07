package dev.voidmc.atlas.api;

import java.util.List;
import java.util.function.Consumer;

/**
 * The player's journal of the world whose map is open: a timeline of firsts (places discovered, areas entered,
 * dimensions visited), depth records and distance travelled, with how much of each area they have explored. Read only;
 * handed to plugins by {@link AtlasPlugin#onAtlasJournal}. Every method answers for the map open now (empty, or null,
 * with none) and is safe from any thread.
 */
public interface AtlasJournal {
	/**
	 * One line of the timeline. {@code kind}: {@code discovery}, {@code area}, {@code dimension}, {@code depth},
	 * {@code distance} or {@code summary} (old lines folded into one a day). {@code day} is the game day it happened
	 * (a summary: its first, with {@code lastDay} its last); {@code placeId} the place or area, if any.
	 */
	record Entry(String kind, String text, long day, long lastDay, long epochMs, String dimension, int x, int y, int z, String placeId) {
	}

	/** Totals: blocks travelled, deepest and highest Y reached (null before any), game days, areas and places. */
	record Stats(double distanceBlocks, Integer deepestY, Integer highestY, long firstDay, long lastDay, int areasEntered, int entries,
		int placesDiscovered, int placesSighted) {
	}

	/** How much of an area the player has explored themselves: cells of 8 x 8 blocks, of those mapped. */
	record AreaProgress(String areaId, String name, int explored, int total) {
		public int percent() {
			return this.total <= 0 ? 0 : (int) Math.min(100, Math.round(100.0 * this.explored / this.total));
		}
	}

	/** The newest lines first, at most {@code limit}. */
	List<Entry> newest(int limit);

	/** The totals, or null with no map open. */
	Stats stats();

	/** The areas explored most (by share), at most {@code n}. */
	List<AreaProgress> topAreas(int n);

	/** Hears each new line as it is written (client thread). */
	void addListener(Consumer<Entry> listener);
}
