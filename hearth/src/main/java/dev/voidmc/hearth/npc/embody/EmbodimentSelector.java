package dev.voidmc.hearth.npc.embody;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Decides which of a settlement's residents should be entities, as a pure function so it is tested without a world.
 *
 * <ul>
 *   <li>A resident becomes an entity when the nearest player is within the spawn radius and the place they would be
 *   is in an entity-ticking chunk.</li>
 *   <li>An entity goes back to being a record beyond the despawn radius (hysteresis: a player pacing at the edge does
 *   not make folk flicker in and out) or when its chunk stops ticking entities.</li>
 *   <li>At most {@code cap} per settlement, nearest first; folk who already exist get a head start of
 *   {@code stickiness} blocks so two residents at similar distances do not keep swapping.</li>
 *   <li>New entities are limited to {@code room}, what the server-wide cap leaves, nearest first.</li>
 * </ul>
 */
public final class EmbodimentSelector {
	private EmbodimentSelector() {
	}

	/**
	 * @param distance blocks to the nearest player ({@link Double#POSITIVE_INFINITY} if none in the dimension)
	 * @param embodied whether an entity exists for them now
	 * @param ticking  whether the chunk they are in (or would appear in) ticks entities
	 */
	public record Candidate(UUID id, double distance, boolean embodied, boolean ticking) {
	}

	/** What to change: residents to make into entities (nearest first) and entities to fold back. */
	public record Plan(List<UUID> spawn, List<UUID> fold) {
		public boolean isEmpty() {
			return this.spawn.isEmpty() && this.fold.isEmpty();
		}
	}

	public static Plan plan(List<Candidate> candidates, int cap, int room, double spawnRadius, double despawnRadius, double stickiness) {
		List<Candidate> wanted = new ArrayList<>();
		List<UUID> fold = new ArrayList<>();
		for (Candidate c : candidates) {
			if (c.embodied) {
				if (c.ticking && c.distance <= despawnRadius) {
					wanted.add(c);
				} else {
					fold.add(c.id);
				}
			} else if (c.ticking && c.distance <= spawnRadius) {
				wanted.add(c);
			}
		}
		wanted.sort(Comparator.comparingDouble(c -> c.embodied ? c.distance - stickiness : c.distance));
		List<UUID> spawn = new ArrayList<>();
		int kept = 0;
		for (Candidate c : wanted) {
			if (kept >= cap) {
				if (c.embodied) {
					fold.add(c.id);
				}
				continue;
			}
			if (c.embodied) {
				kept++;
			} else if (spawn.size() < room) {
				spawn.add(c.id);
				kept++;
			}
		}
		return new Plan(spawn, fold);
	}
}
