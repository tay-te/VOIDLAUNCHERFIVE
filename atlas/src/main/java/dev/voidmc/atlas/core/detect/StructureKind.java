package dev.voidmc.atlas.core.detect;

import dev.voidmc.atlas.api.Tier;

/**
 * The structures Atlas can tell from their blocks alone, so they are found on any server with only Atlas installed.
 * Each has the id its places carry (the vanilla structure's id, or its family's for the ones that come in variants:
 * {@code minecraft:village} for every village), how far apart two sightings may be and still be the same one, and its
 * tier.
 */
public enum StructureKind {
	VILLAGE("minecraft:village", "Village", "VILLAGE", Tier.MAJOR, 96),
	RUINED_PORTAL("minecraft:ruined_portal", "Ruined portal", "SIGHT", Tier.MINOR, 24),
	SHIPWRECK("minecraft:shipwreck", "Shipwreck", "SIGHT", Tier.MINOR, 32),
	DESERT_PYRAMID("minecraft:desert_pyramid", "Desert pyramid", "BUILDING", Tier.MAJOR, 32),
	JUNGLE_TEMPLE("minecraft:jungle_pyramid", "Jungle temple", "BUILDING", Tier.MAJOR, 24),
	MONUMENT("minecraft:monument", "Ocean monument", "LANDMARK", Tier.MAJOR, 72),
	MANSION("minecraft:mansion", "Woodland mansion", "LANDMARK", Tier.MAJOR, 96),
	DUNGEON("atlas:dungeon", "Dungeon", "DUNGEON", Tier.MINOR, 16),
	MINESHAFT("minecraft:mineshaft", "Mineshaft", "DUNGEON", Tier.MINOR, 112),
	ANCIENT_CITY("minecraft:ancient_city", "Ancient city", "LANDMARK", Tier.MAJOR, 144),
	TRIAL_CHAMBERS("minecraft:trial_chambers", "Trial chambers", "DUNGEON", Tier.MAJOR, 96),
	GEODE("atlas:geode", "Amethyst geode", "SIGHT", Tier.MINOR, 20),
	TRAIL_RUINS("minecraft:trail_ruins", "Trail ruins", "BUILDING", Tier.MINOR, 40),
	OCEAN_RUIN("minecraft:ocean_ruin", "Ocean ruin", "BUILDING", Tier.MINOR, 32),
	PILLAGER_OUTPOST("minecraft:pillager_outpost", "Pillager outpost", "BUILDING", Tier.MAJOR, 48),
	IGLOO("minecraft:igloo", "Igloo", "BUILDING", Tier.MINOR, 16),
	STRONGHOLD("minecraft:stronghold", "Stronghold", "LANDMARK", Tier.MAJOR, 112),
	FORTRESS("minecraft:fortress", "Nether fortress", "DUNGEON", Tier.MAJOR, 144),
	BASTION("minecraft:bastion_remnant", "Bastion remnant", "DUNGEON", Tier.MAJOR, 72),
	END_CITY("minecraft:end_city", "End city", "LANDMARK", Tier.MAJOR, 80),
	/** Not a structure, but found the same way: an opening in a hillside that leads into the rock. */
	CAVE_MOUTH("atlas:cave_mouth", "Cave mouth", "NATURAL", Tier.MINOR, 48);

	/** The id its places carry. */
	public final String id;
	public final String label;
	public final String category;
	public final Tier tier;
	/** Two sightings closer than this (blocks, horizontally) are the same structure. */
	public final int mergeRadius;

	StructureKind(String id, String label, String category, Tier tier, int mergeRadius) {
		this.id = id;
		this.label = label;
		this.category = category;
		this.tier = tier;
		this.mergeRadius = mergeRadius;
	}

	public static StructureKind byId(String id) {
		for (StructureKind k : values()) {
			if (k.id.equals(id)) {
				return k;
			}
		}
		return null;
	}
}
