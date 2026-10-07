package dev.voidmc.atlas.core.journal;

import java.util.List;
import java.util.Objects;

/**
 * How much of one biome area the player has explored: the map's latest count of its cells, and how that grew.
 *
 * @param id       the area's id (from the map's area index)
 * @param name     its name ("Larkspur Meadow")
 * @param biome    its biome, like {@code minecraft:plains}
 * @param total    how many map cells the area has
 * @param explored how many of them the player has seen
 * @param history  the counts over time, oldest first, at most one point per game day
 */
public record AreaStat(String id, String name, String biome, int total, int explored, List<Point> history) {

	/** One reading of an area's counts. */
	public record Point(long day, long epoch, int explored, int total) {
		public double share() {
			return AreaStat.share(this.explored, this.total);
		}
	}

	public AreaStat {
		Objects.requireNonNull(id, "id");
		name = name == null ? "" : name;
		biome = biome == null ? "" : biome;
		total = Math.max(0, total);
		explored = Math.max(0, Math.min(explored, total));
		history = history == null ? List.of() : List.copyOf(history);
	}

	/** The explored share, 0 to 1. */
	public double share() {
		return share(this.explored, this.total);
	}

	/** The explored share as a whole percentage, 0 to 100 (rounded down, so 100 means all of it). */
	public int percent() {
		return this.total == 0 ? 0 : (int) (100L * this.explored / this.total);
	}

	static double share(int explored, int total) {
		return total <= 0 ? 0 : Math.min(1.0, Math.max(0, explored) / (double) total);
	}
}
