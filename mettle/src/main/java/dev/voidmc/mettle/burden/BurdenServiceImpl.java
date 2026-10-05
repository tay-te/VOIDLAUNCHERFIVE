package dev.voidmc.mettle.burden;

import dev.voidmc.mettle.api.BandRules;
import dev.voidmc.mettle.api.BurdenService;
import dev.voidmc.mettle.api.LoadBand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemInstance;

/**
 * {@link BurdenService} for both sides. On the server, load and band come from {@link LoadTracker} (recomputed first if
 * anything changed). On the client, the synced {@code mettle:burden/load} for the local player, else worked out from the
 * synced weight table and perks.
 */
final class BurdenServiceImpl implements BurdenService {
	@Override
	public float weight(ItemInstance stack) {
		return Weights.weigh(Weights.table(), stack);
	}

	@Override
	public float weight(Player player, ItemInstance stack) {
		return Weights.weigh(Weights.table(player), player, stack, false, 0);
	}

	@Override
	public float load(Player player) {
		return state(player).load();
	}

	@Override
	public float capacity(Player player) {
		return Loads.capacity(player);
	}

	@Override
	public LoadBand band(Player player) {
		return state(player).band();
	}

	@Override
	public BandRules rules(Player player) {
		return Bands.rules(player);
	}

	static LoadState state(Player player) {
		if (player instanceof ServerPlayer server) {
			return LoadTracker.state(server);
		}
		LoadState synced = LoadState.of(player);
		return synced != null ? synced : Loads.compute(player);
	}
}
