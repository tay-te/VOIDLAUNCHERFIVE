package dev.voidmc.hearth.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** What happens at a settlement's markets, for other mods (skill XP for business, stats). Fired on the server thread. */
public final class MarketEvents {
	/**
	 * A player traded at a market counter (the town's market or a shop, at the posted price or a haggled one), after the
	 * goods and the coin changed hands.
	 */
	public static final Event<Traded> TRADED = EventFactory.createArrayBacked(Traded.class, ls -> (p, s, item, count, bits, buys) -> {
		for (Traded l : ls) {
			l.onTraded(p, s, item, count, bits, buys);
		}
	});

	private MarketEvents() {
	}

	/**
	 * @param item       one of what was traded (a copy; count 1)
	 * @param count      how many items
	 * @param bits       the coin that changed hands for all of them
	 * @param playerBuys whether the player bought (else sold)
	 */
	@FunctionalInterface
	public interface Traded {
		void onTraded(ServerPlayer player, Settlement settlement, ItemStack item, int count, long bits, boolean playerBuys);
	}
}
