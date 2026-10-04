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
 *                   stalled or short goods; {@code $gift} the giver's taste
 * @param entities   kill/slay/escort/ambush/apprehend: the creature choices (ids, {@code #tags} for kill, or {@code monster}
 *                   for any hostile mob)
 * @param count      how many (items, kills, the size of an ambush, bits to pay); for supply usually {@code $missing}
 * @param radius     how near counts as there (blocks); kill: the area round the settlement (0: the settlement's bounds)
 * @param minDistance where a place is chosen, from the settlement centre (slay, reach, scout, ambush, apprehend, prospect)
 * @param maxDistance as above
 * @param structures explore: structure ids (the nearest located one is chosen)
 * @param biomes     biome: any of these biome ids
 * @param names      slay/escort/apprehend: the creature's name choices; reach: the place's name choices
 * @param night      kill: only kills at night count; ambush/apprehend: raised only after dark
 * @param health     slay/apprehend/ambush: health multiplier for the creatures
 * @param grant      an item handed to the player when the objective is done (a keepsake found, stolen goods recovered)
 * @param recipient  courier/talk: a particular resident of the destination receives it (a letter to a friend)
 * @param clearance  scout: blocks the site must keep from any settlement
 * @param targetName display name override for the target ({@code "the Karst Temple"})
 * @param place      where the objective's place is bound: {@code ring} (a distance band; the default), {@code center},
 *                   {@code event} (the happening's place), {@code route} (partway along the road to the other settlement),
 *                   {@code poi:<type>}, {@code site:<kind>}, {@code plot:<archetype path prefix>}
 * @param along      ambush with place {@code route} or an escort: how far along the road it is sprung (0–1)
 * @param hold       rescue: seconds to stay at the place digging
 * @param resource   prospect: the {@code ResourceSite.Kind} to find ({@code stone}, {@code ore}, {@code clay}...); empty for
 *                   the happening's (a spent site's kind)
 * @param crime      ambush/escort: killing these creatures is this offence ({@code robbery}), reported to the law
 * @param caravan    escort: pack animals in the train (1–6)
 * @param cargo      escort/ambush: the animals carry chests of the town's goods (what robbers take)
 * @param lethal     apprehend: killing the culprit also counts (at a smaller reward)
 */
public record ObjectiveSpec(ObjectiveKind kind, String label, List<String> items, List<String> entities, Amount count, int radius,
	int minDistance, int maxDistance, List<String> structures, List<String> biomes, List<String> names, boolean night, double health,
	@Nullable Grant grant, boolean recipient, int clearance, String targetName, String place, double along, int hold, String resource,
	String crime, int caravan, boolean cargo, boolean lethal) {

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
