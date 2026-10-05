package dev.voidmc.atlas.core;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Readable names and categories for structure ids: vanilla's and Expanse's are named by hand ("Witch hut",
 * "Frostwatch Citadel"), anything else is made readable from its id ("my_mod:old_tower" becomes "Old tower").
 */
public final class StructureNames {
	private static final Map<String, String> NAMES = Map.ofEntries(
		Map.entry("minecraft:village_plains", "Plains village"),
		Map.entry("minecraft:village_desert", "Desert village"),
		Map.entry("minecraft:village_savanna", "Savanna village"),
		Map.entry("minecraft:village_snowy", "Snowy village"),
		Map.entry("minecraft:village_taiga", "Taiga village"),
		Map.entry("minecraft:pillager_outpost", "Pillager outpost"),
		Map.entry("minecraft:mansion", "Woodland mansion"),
		Map.entry("minecraft:jungle_pyramid", "Jungle temple"),
		Map.entry("minecraft:desert_pyramid", "Desert pyramid"),
		Map.entry("minecraft:igloo", "Igloo"),
		Map.entry("minecraft:swamp_hut", "Witch hut"),
		Map.entry("minecraft:monument", "Ocean monument"),
		Map.entry("minecraft:stronghold", "Stronghold"),
		Map.entry("minecraft:ancient_city", "Ancient city"),
		Map.entry("minecraft:trial_chambers", "Trial chambers"),
		Map.entry("minecraft:trail_ruins", "Trail ruins"),
		Map.entry("minecraft:mineshaft", "Mineshaft"),
		Map.entry("minecraft:mineshaft_mesa", "Badlands mineshaft"),
		Map.entry("minecraft:fortress", "Nether fortress"),
		Map.entry("minecraft:bastion_remnant", "Bastion remnant"),
		Map.entry("minecraft:end_city", "End city"),
		Map.entry("minecraft:nether_fossil", "Nether fossil"),
		Map.entry("minecraft:shipwreck", "Shipwreck"),
		Map.entry("minecraft:shipwreck_beached", "Beached shipwreck"),
		Map.entry("minecraft:ocean_ruin_cold", "Ocean ruin"),
		Map.entry("minecraft:ocean_ruin_warm", "Ocean ruin"),
		Map.entry("minecraft:ruined_portal", "Ruined portal"),
		Map.entry("minecraft:ruined_portal_desert", "Ruined portal"),
		Map.entry("minecraft:ruined_portal_jungle", "Ruined portal"),
		Map.entry("minecraft:ruined_portal_swamp", "Ruined portal"),
		Map.entry("minecraft:ruined_portal_mountain", "Ruined portal"),
		Map.entry("minecraft:ruined_portal_ocean", "Ruined portal"),
		Map.entry("minecraft:ruined_portal_nether", "Ruined portal"),
		Map.entry("expanse:karst_pagoda", "Karst temple"),
		Map.entry("expanse:cloud_monastery", "Cloud monastery"),
		Map.entry("expanse:sun_shrine", "Temple of the Pink Sun"),
		Map.entry("expanse:frost_outpost", "Frostwatch Citadel"),
		Map.entry("expanse:palm_harbour", "Palm harbour"),
		Map.entry("expanse:logging_camp", "Logging camp"),
		Map.entry("expanse:wisteria_manor", "Wisteria manor"),
		Map.entry("expanse:caravanserai", "Caravanserai"),
		Map.entry("expanse:stone_circle", "Stone circle"),
		Map.entry("expanse:ruined_watchtower", "Ruined watchtower"),
		Map.entry("expanse:woodland_lodge", "Woodland lodge"),
		Map.entry("expanse:stilt_hamlet", "Stilt hamlet"),
		Map.entry("expanse:lighthouse", "Lighthouse"),
		Map.entry("expanse:lumen_shrine", "Lumen shrine"),
		Map.entry("expanse:nomad_camp", "Nomad camp"),
		Map.entry("expanse:cavern_mine_camp", "Deep mine camp"),
		Map.entry("expanse:deepstone_ruins", "Deepstone ruins"),
		Map.entry("expanse:cavern_landing", "Cavern landing"),
		Map.entry("expanse:village_wisteria", "Wisteria village"),
		Map.entry("expanse:village_redwood", "Redwood village"),
		Map.entry("expanse:village_coast", "Coast village"),
		Map.entry("expanse:village_steppe", "Steppe village"),
		Map.entry("expanse:village_moor", "Moor village"),
		Map.entry("expanse:village_karst", "Karst village"),
		Map.entry("expanse:village_tundra", "Tundra village"),
		Map.entry("expanse:old_well", "Old well"),
		Map.entry("expanse:old_well_limestone", "Old well"),
		Map.entry("expanse:old_well_mud", "Old well"),
		Map.entry("expanse:old_well_sandstone", "Old well"),
		Map.entry("expanse:wayside_shrine_karst", "Wayside shrine"),
		Map.entry("expanse:wayside_shrine_sand", "Wayside shrine"),
		Map.entry("expanse:campsite_bamboo", "Campsite"),
		Map.entry("expanse:campsite_snow", "Campsite"),
		Map.entry("expanse:campsite_steppe", "Campsite"),
		Map.entry("expanse:terrace_farm", "Rice terraces")
	);

	private static final Set<String> LANDMARKS = Set.of("expanse:karst_pagoda", "expanse:cloud_monastery", "expanse:sun_shrine",
		"expanse:frost_outpost", "expanse:palm_harbour", "expanse:logging_camp", "expanse:wisteria_manor", "expanse:caravanserai",
		"minecraft:mansion", "minecraft:monument", "minecraft:stronghold", "minecraft:ancient_city", "minecraft:end_city");
	private static final Set<String> BUILDINGS = Set.of("expanse:stone_circle", "expanse:ruined_watchtower", "expanse:woodland_lodge",
		"expanse:stilt_hamlet", "expanse:lighthouse", "expanse:lumen_shrine", "expanse:nomad_camp", "minecraft:pillager_outpost",
		"minecraft:jungle_pyramid", "minecraft:desert_pyramid", "minecraft:igloo", "minecraft:swamp_hut", "minecraft:trail_ruins");
	private static final Set<String> DUNGEONS = Set.of("minecraft:trial_chambers", "minecraft:mineshaft", "minecraft:mineshaft_mesa",
		"minecraft:fortress", "minecraft:bastion_remnant", "expanse:cavern_mine_camp", "expanse:deepstone_ruins", "expanse:cavern_landing");
	/** Never shown: finding these is the point of a treasure map. */
	private static final Set<String> HIDDEN = Set.of("minecraft:buried_treasure");

	private StructureNames() {
	}

	public static boolean hidden(String id) {
		return HIDDEN.contains(id);
	}

	public static String name(String id) {
		String known = NAMES.get(id);
		if (known != null) {
			return known;
		}
		String path = id.substring(id.indexOf(':') + 1);
		int slash = path.lastIndexOf('/');
		if (slash >= 0) {
			path = path.substring(slash + 1);
		}
		String s = path.replace('_', ' ').strip();
		return s.isEmpty() ? id : Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
	}

	/** LANDMARK, BUILDING, VILLAGE, DUNGEON or SIGHT. {@code village}: whether it is in {@code #minecraft:village}. */
	public static String category(String id, boolean village) {
		if (village || id.contains("village")) {
			return "VILLAGE";
		}
		if (LANDMARKS.contains(id)) {
			return "LANDMARK";
		}
		if (BUILDINGS.contains(id)) {
			return "BUILDING";
		}
		if (DUNGEONS.contains(id) || id.contains("dungeon") || id.contains("ruin")) {
			return "DUNGEON";
		}
		return "SIGHT";
	}
}
