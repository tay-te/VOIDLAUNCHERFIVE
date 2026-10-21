package dev.voidmc.mettle.compat.hearth;

import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.mettle.Mettle;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * Mettle with VOID Hearth (INTEGRATION.md §1): Mettle provides the numbers, Hearth owns how the world reacts. The only way
 * in is {@link #init}, called by {@code compat.CompatModule} when Hearth is loaded; nothing else in Mettle names a Hearth
 * type. Fills Hearth's player-skills slot, adds a price modifier, trains folk's pupils, and turns quests and trade into XP.
 */
public final class HearthCompat {
	private static final Trainers TRAINERS = new Trainers();
	private static boolean pricesAdded;

	private HearthCompat() {
	}

	public static void init() {
		TrainerSessions.init();
		HearthServices.setPlayerSkills(new MettlePlayerSkills());
		// Request 12: does the Hearth actually loaded read bonus keys? Until it does, each keyed effect takes its fallback.
		dev.voidmc.mettle.compat.HearthLink.set(true, declares(dev.voidmc.hearth.api.PlayerSkills.class, "bonus"));
		DialogueProvider.register(TRAINERS);
		HearthXp.init();
		// Cards v2: rebuild fees in coin (to a trainer, or burned at a Card Shrine).
		dev.voidmc.mettle.character.cards.Rebuilds.setFees(new HearthFees());
		// Hearth's economy module registers its service during Hearth's own init, which may run after Mettle's.
		ServerLifecycleEvents.SERVER_STARTING.register(server -> addPrices());
		ServerPlayerEvents.LEAVE.register(player -> {
			TRAINERS.forget(player.getUUID());
			HearthXp.forget(player.getUUID());
		});
		Mettle.LOGGER.info("VOID Hearth found: skills, prices, trainers and quest XP are on; bonus keys {}",
			dev.voidmc.mettle.compat.HearthLink.bonus() ? "read" : "not read yet (fallbacks on)");
	}

	/**
	 * Whether the loaded interface declares a method (our src/hearthApi copy is compile-only and never packaged, so this
	 * reads the running Hearth's own class).
	 */
	static boolean declares(Class<?> type, String method) {
		for (java.lang.reflect.Method m : type.getDeclaredMethods()) {
			if (m.getName().equals(method)) {
				return true;
			}
		}
		return false;
	}

	/** Once per game: the modifier list lives as long as Hearth's classes, across integrated-server restarts. */
	private static void addPrices() {
		EconomyService economy = HearthServices.economy();
		if (economy != null && !pricesAdded) {
			economy.addPriceModifier(new MettlePrices());
			pricesAdded = true;
		}
	}
}
