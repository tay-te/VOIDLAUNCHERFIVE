package dev.voidmc.hearth.quest.template;

import dev.voidmc.hearth.quest.model.ObjectiveKind;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One objective of a template, before binding. Which fields matter depends on the kind (see docs/QUESTS.md); the parser
 * fills defaults and the validator rejects combinations that cannot bind.
 *
 * @param label      journal text with placeholders; empty for the kind's default
 * @param items      deliver: the item choices (ids or {@code #tags}); one is picked per quest. {@code $goods_item} binds the
 *                   stalled goods; {@code $gift} the giver's taste
 * @param entities   kill/slay/escort: the creature choices (ids, {@code #tags}, or {@code monster} for any hostile mob)
 * @param count      how many (items, kills); for supply usually {@code $missing}
 * @param radius     how near counts as there (blocks); kill: the area round the settlement (0: the settlement's bounds)
 * @param minDistance where a place is chosen, from the settlement centre (slay, reach, scout)
 * @param maxDistance as above
 * @param structures explore: structure ids or {@code #tags} (the nearest located one is chosen)
 * @param biomes     biome: any of these biome ids
 * @param names      slay/escort: the creature's name choices; reach: the place's name choices
 * @param night      kill: only kills at night count
 * @param health     slay: health multiplier for the beast
 * @param grant      an item handed to the player when the objective is done (a keepsake found, a rubbing taken)
 * @param recipient  courier/talk: a particular resident of the destination receives it (a letter to a friend)
 * @param clearance  scout: blocks the site must keep from any settlement
 * @param targetName display name override for the target ({@code "the Karst Temple"})
 */
public record ObjectiveSpec(ObjectiveKind kind, String label, List<String> items, List<String> entities, Amount count, int radius,
	int minDistance, int maxDistance, List<String> structures, List<String> biomes, List<String> names, boolean night, double health,
	@Nullable Grant grant, boolean recipient, int clearance, String targetName) {

	public ObjectiveSpec {
		items = List.copyOf(items);
		entities = List.copyOf(entities);
		structures = List.copyOf(structures);
		biomes = List.copyOf(biomes);
		names = List.copyOf(names);
	}

	/**
	 * An item given on arrival.
	 *
	 * @param item     the item id (a paper, a map, an amulet-like vanilla item)
	 * @param name     its custom name, with placeholders
	 * @param lore     a lore line, with placeholders (may be empty)
	 * @param required it must be brought back and is taken at turn-in
	 */
	public record Grant(String item, String name, String lore, boolean required) {
	}
}
