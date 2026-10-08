package dev.voidmc.hearth.economy.price;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.PriceModifier;
import dev.voidmc.hearth.api.Settlement;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The player price modifiers other mods added ({@code EconomyService.addPriceModifier}): their factors multiplied
 * together and kept within {@link PriceModifier#MIN}–{@link PriceModifier#MAX}, applied by {@link MarketPricer} after
 * reputation. None added: a factor of exactly 1.
 */
public final class PriceModifiers {
	private static final List<PriceModifier> MODIFIERS = new CopyOnWriteArrayList<>();

	private PriceModifiers() {
	}

	public static void add(PriceModifier modifier) {
		MODIFIERS.add(modifier);
	}

	public static boolean any() {
		return !MODIFIERS.isEmpty();
	}

	/** The combined factor for one item for this player here (1 with no modifiers). */
	public static double factor(ServerPlayer player, Settlement settlement, ItemStack one, boolean playerBuys) {
		if (MODIFIERS.isEmpty()) {
			return 1.0;
		}
		double f = 1.0;
		for (PriceModifier m : MODIFIERS) {
			try {
				double x = m.factor(player, settlement, one, playerBuys);
				if (Double.isFinite(x) && x > 0) {
					f *= x;
				}
			} catch (RuntimeException e) {
				Hearth.LOG.warn("price modifier {} failed", m.getClass().getName(), e);
			}
		}
		return combine(f);
	}

	/** The product of factors, kept within the bounds. */
	public static double combine(double product) {
		return Math.clamp(product, PriceModifier.MIN, PriceModifier.MAX);
	}

	/** For tests and reloads. */
	public static void clear() {
		MODIFIERS.clear();
	}
}
