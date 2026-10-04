package dev.voidmc.hearth.economy.shop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A settlement's shops ({@code settlement.data(EconomyModule.SHOPS)}), and which merchants a player has soured by
 * haggling too hard. A handful of small records per settlement; changed once a sim day and on trades.
 */
public final class ShopBook {
	/** The key for the town's own market (its stockpile and treasury), in {@link #soured}. */
	public static final String TOWN = "town";

	public static final Codec<ShopBook> CODEC = RecordCodecBuilder.create(i -> i.group(
		Shop.CODEC.listOf().optionalFieldOf("shops", List.of()).forGetter(b -> new ArrayList<>(b.shops.values())),
		Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("soured", Map.of()).forGetter(b -> b.soured),
		Codec.LONG.optionalFieldOf("last_sync", Long.MIN_VALUE).forGetter(b -> b.lastSync)
	).apply(i, (shops, soured, lastSync) -> {
		ShopBook b = new ShopBook();
		for (Shop s : shops) {
			b.shops.put(s.key, s);
		}
		b.soured.putAll(soured);
		b.lastSync = lastSync;
		return b;
	}));

	/** By {@link Shop#key}, in the order they opened. */
	public final Map<String, Shop> shops = new LinkedHashMap<>();
	/** {@code <shop key>|<player uuid>} → the last sim day that merchant stays sour with that player. */
	public final Map<String, Long> soured = new HashMap<>();
	/** The sim day the shops were last matched to the settlement's buildings and folk. */
	public long lastSync = Long.MIN_VALUE;

	public @Nullable Shop get(String key) {
		return this.shops.get(key);
	}

	public boolean sour(String shopKey, UUID player, long day) {
		Long until = this.soured.get(shopKey + "|" + player);
		return until != null && day <= until;
	}

	public void setSour(String shopKey, UUID player, long untilDay) {
		this.soured.put(shopKey + "|" + player, untilDay);
	}

	/** Forgets sourness that has passed. */
	public void pruneSour(long day) {
		this.soured.values().removeIf(until -> until < day);
	}
}
