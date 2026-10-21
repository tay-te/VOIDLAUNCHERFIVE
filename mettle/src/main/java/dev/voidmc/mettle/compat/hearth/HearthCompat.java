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
	private static volatile net.minecraft.server.@org.jspecify.annotations.Nullable MinecraftServer server;

	private HearthCompat() {
	}

	public static void init() {
		TrainerSessions.init();
		HearthServices.setPlayerSkills(new MettlePlayerSkills());
		// What the Hearth actually loaded has of requests 7-14 (an older one lacks them; our api copy is never packaged):
		// bonus keys (12; until then each keyed effect takes its fallback), check events (13), the card broker (7), coin
		// between players (8) and standing bands (9).
		boolean bonus = declares(dev.voidmc.hearth.api.PlayerSkills.class, "bonus");
		boolean checks = present("dev.voidmc.hearth.api.StandingEvents");
		boolean broker = present("dev.voidmc.hearth.api.VirtualGoods") && declares(HearthServices.class, "addVirtualGoods");
		dev.voidmc.mettle.compat.HearthLink.set(true, bonus, checks, broker);
		HearthFees.transfers = declares(EconomyService.class, "transfer") && declares(EconomyService.class, "canReceive");
		if (checks) {
			HearthCheckEvents.init();
		}
		if (broker) {
			CardGoods.init();
		}
		if (field("dev.voidmc.hearth.api.SettlementEvents", "STANDING_CHANGED")) {
			HearthStanding.init();
		}
		dev.voidmc.mettle.character.skill.speech.SpeechHooks.marketAppraiser = HearthAppraiser::appraise;
		ServerLifecycleEvents.SERVER_STARTING.register(s -> server = s);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);
		DialogueProvider.register(TRAINERS);
		HearthXp.init();
		// Cards v2: rebuild fees in coin (to a trainer, or burned at a Card Shrine).
		dev.voidmc.mettle.character.cards.Rebuilds.setFees(new HearthFees());
		// Hearth's economy module registers its service during Hearth's own init, which may run after Mettle's.
		ServerLifecycleEvents.SERVER_STARTING.register(server -> addPrices());
		ServerPlayerEvents.LEAVE.register(player -> {
			TRAINERS.forget(player.getUUID());
			HearthXp.forget(player.getUUID());
			HearthChecks.forget(player.getUUID());
		});
		Mettle.LOGGER.info("VOID Hearth found: skills, prices, trainers and quest XP are on; bonus keys {}; check events {}; card broker {}; coin transfers {}",
			bonus ? "read" : "not read yet (fallbacks on)", checks ? "on" : "absent", broker ? "on" : "absent", HearthFees.transfers ? "on" : "charge and pay");
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

	/** Whether the running Hearth has this class. */
	static boolean present(String type) {
		try {
			Class.forName(type, false, HearthCompat.class.getClassLoader());
			return true;
		} catch (ClassNotFoundException | LinkageError e) {
			return false;
		}
	}

	/** Whether the running Hearth's class has this public field. */
	static boolean field(String type, String name) {
		try {
			Class.forName(type, false, HearthCompat.class.getClassLoader()).getField(name);
			return true;
		} catch (ReflectiveOperationException | LinkageError e) {
			return false;
		}
	}

	/** An online player by id (Hearth's standing events carry ids), or null. */
	static net.minecraft.server.level.@org.jspecify.annotations.Nullable ServerPlayer player(java.util.UUID id) {
		net.minecraft.server.MinecraftServer s = server;
		return s == null ? null : s.getPlayerList().getPlayer(id);
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
