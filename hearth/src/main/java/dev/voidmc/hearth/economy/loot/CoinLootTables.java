package dev.voidmc.hearth.economy.loot;

import dev.voidmc.hearth.economy.EconomyConfig;
import dev.voidmc.hearth.economy.EconomyModule;
import dev.voidmc.hearth.economy.item.EconomyItems;
import java.util.Map;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import org.jspecify.annotations.Nullable;

/**
 * Coin as treasure: old coin turns up in the chests of the world, a little in a village chest, a captain's purse in
 * buried treasure, an ancient hoard of marks and crowns in the deep dark. Added to the built-in loot tables (a datapack
 * that replaces a table keeps its own contents) of vanilla structures and, when installed, Expanse's — its lost chests,
 * its structures' chests and their hidden stashes. Amounts per kind of place are in the config.
 */
public final class CoinLootTables {
	/** Vanilla chest tables and the kind of place each is. */
	private static final Map<String, String> VANILLA = Map.ofEntries(
		Map.entry("chests/buried_treasure", "buried_treasure"),
		Map.entry("chests/shipwreck_treasure", "shipwreck_treasure"),
		Map.entry("chests/shipwreck_supply", "shipwreck"),
		Map.entry("chests/shipwreck_map", "shipwreck"),
		Map.entry("chests/underwater_ruin_small", "ruin"),
		Map.entry("chests/underwater_ruin_big", "ruin"),
		Map.entry("chests/ruined_portal", "ruin"),
		Map.entry("chests/simple_dungeon", "dungeon"),
		Map.entry("chests/abandoned_mineshaft", "mineshaft"),
		Map.entry("chests/ancient_city", "ancient_city"),
		Map.entry("chests/desert_pyramid", "temple"),
		Map.entry("chests/jungle_temple", "temple"),
		Map.entry("chests/stronghold_corridor", "stronghold"),
		Map.entry("chests/stronghold_crossing", "stronghold"),
		Map.entry("chests/stronghold_library", "stronghold"),
		Map.entry("chests/woodland_mansion", "mansion"),
		Map.entry("chests/pillager_outpost", "outpost"),
		Map.entry("chests/igloo_chest", "camp"),
		Map.entry("chests/abandoned_camp_common_chest", "camp"),
		Map.entry("chests/abandoned_camp_secret_chest", "dungeon"));

	private CoinLootTables() {
	}

	public static void init() {
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			if (!source.isBuiltin()) {
				return;
			}
			String category = category(key.identifier());
			if (category == null) {
				return;
			}
			EconomyConfig.CoinLoot loot = EconomyModule.config().loot(category);
			if (loot.chance() <= 0) {
				return;
			}
			if (loot.bitsMax() > 0 || loot.marksMax() > 0) {
				builder.withPool(coinPool(loot));
			}
			if (loot.crownChance() > 0 && loot.crownsMax() > 0) {
				builder.withPool(crownPool(loot));
			}
		});
	}

	/** The kind of place a loot table belongs to, or null for tables that get no coin. */
	public static @Nullable String category(Identifier table) {
		String path = table.getPath();
		if (table.getNamespace().equals("minecraft")) {
			if (path.startsWith("chests/village/")) {
				return "village";
			}
			return VANILLA.get(path);
		}
		if (table.getNamespace().equals("expanse") && path.startsWith("chests/")) {
			if (path.endsWith("_archaeology") || path.endsWith("_map")) {
				return null; // brushed from suspicious blocks, or a map chest: not a place for a purse
			}
			if (path.equals("chests/micro/lost_chest")) {
				return "expanse_lost";
			}
			return path.endsWith("_hidden") ? "expanse_hidden" : "expanse";
		}
		return null;
	}

	/** One roll at {@code chance}: a handful of bits or a few marks. */
	private static LootPool.Builder coinPool(EconomyConfig.CoinLoot l) {
		int miss = Math.round(100 * (1 - l.chance()));
		LootPool.Builder pool = LootPool.lootPool().setRolls(ContextIntProviders.exactly(1));
		if (l.bitsMax() > 0) {
			pool.add(LootItem.lootTableItem(EconomyItems.COPPER_BIT).setWeight(60)
				.apply(SetItemCountFunction.setCount(ContextIntProviders.between(Math.max(1, l.bitsMin()), Math.max(1, l.bitsMax())))));
		}
		if (l.marksMax() > 0) {
			pool.add(LootItem.lootTableItem(EconomyItems.IRON_MARK).setWeight(l.bitsMax() > 0 ? 40 : 100)
				.apply(SetItemCountFunction.setCount(ContextIntProviders.between(Math.max(1, l.marksMin()), Math.max(1, l.marksMax())))));
		}
		if (miss > 0) {
			// The coin entries weigh 100 together; this makes "nothing" come up with probability 1 - chance.
			pool.add(EmptyLootItem.emptyItem().setWeight(miss * 100 / Math.max(1, 100 - miss)));
		}
		return pool;
	}

	/** A separate, rarer roll for gold. */
	private static LootPool.Builder crownPool(EconomyConfig.CoinLoot l) {
		int hit = Math.max(1, Math.round(100 * l.crownChance()));
		LootPool.Builder pool = LootPool.lootPool().setRolls(ContextIntProviders.exactly(1))
			.add(LootItem.lootTableItem(EconomyItems.GOLD_CROWN).setWeight(hit)
				.apply(SetItemCountFunction.setCount(ContextIntProviders.between(1, Math.max(1, l.crownsMax())))));
		if (hit < 100) {
			pool.add(EmptyLootItem.emptyItem().setWeight(100 - hit));
		}
		return pool;
	}
}
