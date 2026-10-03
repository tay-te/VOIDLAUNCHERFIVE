package dev.voidmc.hearth.quest.service;

import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.quest.gen.GameFacts;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * {@link GameFacts} from the running game: the registries, the server's language (Fabric loads every mod's en_us on
 * the server too), and the economy's market prices.
 */
public final class GameFactsImpl implements GameFacts {
	public static final GameFactsImpl INSTANCE = new GameFactsImpl();

	/** Names for structures, which have no lang entries of their own. */
	private static final Map<String, String> STRUCTURES = Map.ofEntries(
		Map.entry("expanse:karst_pagoda", "the Karst Temple"),
		Map.entry("expanse:cloud_monastery", "the Cloud Monastery"),
		Map.entry("expanse:sun_shrine", "the Temple of the Pink Sun"),
		Map.entry("expanse:frost_outpost", "the Frostwatch Citadel"),
		Map.entry("expanse:palm_harbour", "the old Palm Harbour"),
		Map.entry("expanse:logging_camp", "the old logging camp"),
		Map.entry("expanse:wisteria_manor", "the Wisteria Manor"),
		Map.entry("expanse:caravanserai", "the Caravanserai"),
		Map.entry("expanse:stone_circle", "the stone circle"),
		Map.entry("expanse:ruined_watchtower", "the ruined watchtower"),
		Map.entry("expanse:woodland_lodge", "the woodland lodge"),
		Map.entry("expanse:stilt_hamlet", "the stilt hamlet"),
		Map.entry("expanse:lighthouse", "the lighthouse"),
		Map.entry("expanse:lumen_shrine", "the Lumen Shrine"),
		Map.entry("expanse:nomad_camp", "the nomad camp"),
		Map.entry("expanse:mammoth_dig", "the mammoth dig"),
		Map.entry("expanse:fairy_ring", "the fairy ring"),
		Map.entry("expanse:tea_garden", "the tea garden"),
		Map.entry("expanse:treehouse", "the treehouse"),
		Map.entry("expanse:sand_colossus", "the sand colossus"),
		Map.entry("expanse:pilgrim_shrine", "the pilgrim shrine"),
		Map.entry("expanse:shepherd_bothy", "the shepherd's bothy"),
		Map.entry("expanse:miners_camp", "the miners' camp"),
		Map.entry("expanse:summit_cairn", "the summit cairn"),
		Map.entry("expanse:graveyard", "the old graveyard"),
		Map.entry("expanse:windpump", "the windpump"),
		Map.entry("minecraft:desert_pyramid", "the desert pyramid"),
		Map.entry("minecraft:jungle_pyramid", "the jungle temple"),
		Map.entry("minecraft:swamp_hut", "the witch's hut"),
		Map.entry("minecraft:igloo", "the igloo"),
		Map.entry("minecraft:pillager_outpost", "the pillager outpost"),
		Map.entry("minecraft:mansion", "the woodland mansion"),
		Map.entry("minecraft:trail_ruins", "the trail ruins"),
		Map.entry("minecraft:ancient_city", "the ancient city"),
		Map.entry("minecraft:trial_chambers", "the trial chambers"),
		Map.entry("minecraft:shipwreck", "the shipwreck"),
		Map.entry("minecraft:ruined_portal", "the ruined portal"),
		Map.entry("minecraft:ocean_ruin_warm", "the drowned ruins"),
		Map.entry("minecraft:ocean_ruin_cold", "the drowned ruins"));

	private GameFactsImpl() {
	}

	@Override
	public boolean itemExists(Identifier item) {
		return BuiltInRegistries.ITEM.containsKey(item);
	}

	@Override
	public boolean entityExists(Identifier entity) {
		return BuiltInRegistries.ENTITY_TYPE.containsKey(entity);
	}

	@Override
	public int maxStackSize(Identifier item) {
		Item it = BuiltInRegistries.ITEM.getValue(item);
		return it == Items.AIR ? 64 : it.getDefaultMaxStackSize();
	}

	@Override
	public String itemName(Identifier item) {
		Item it = BuiltInRegistries.ITEM.getValue(item);
		return it == Items.AIR ? GameFacts.humanize(item.getPath()) : new ItemStack(it).getHoverName().getString();
	}

	@Override
	public String entityName(Identifier entity) {
		return BuiltInRegistries.ENTITY_TYPE.getOptional(entity).map(t -> t.getDescription().getString())
			.orElseGet(() -> GameFacts.humanize(entity.getPath()));
	}

	@Override
	public String biomeName(Identifier biome) {
		return Language.getInstance().getOrDefault("biome." + biome.getNamespace() + "." + biome.getPath(), GameFacts.humanize(biome.getPath()));
	}

	@Override
	public String structureName(Identifier structure) {
		String name = STRUCTURES.get(structure.toString());
		return name != null ? name : "the " + GameFacts.humanize(structure.getPath()).toLowerCase(java.util.Locale.ROOT);
	}

	@Override
	public String coinText(long bits) {
		EconomyService economy = HearthServices.economy();
		if (economy != null) {
			return economy.format(bits).getString();
		}
		int emeralds = Rewards.emeralds(bits, dev.voidmc.hearth.quest.service.QuestManager.config().bitsPerEmerald());
		return emeralds == 1 ? "1 emerald" : emeralds + " emeralds";
	}

	@Override
	public long marketPrice(Settlement settlement, Identifier item, int count) {
		EconomyService economy = HearthServices.economy();
		Item it = BuiltInRegistries.ITEM.getValue(item);
		if (economy == null || it == Items.AIR) {
			return -1;
		}
		return economy.price(settlement, new ItemStack(it), false) * count;
	}
}
