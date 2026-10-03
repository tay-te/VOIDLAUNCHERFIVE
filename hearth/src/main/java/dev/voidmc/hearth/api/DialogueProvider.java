package dev.voidmc.hearth.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Adds choices to the conversation when a player talks to a resident. The NPC module owns the conversation screen and
 * asks every registered provider for options; the economy module adds "Let's trade", the quest module adds the quests
 * on offer and hand-ins, and so on. Everything runs on the server; the client only shows labels and sends back an id.
 */
public interface DialogueProvider {
	/** Adds this provider's options for this player and resident. */
	void collect(ServerPlayer player, Settlement settlement, Resident resident, List<Option> out);

	/**
	 * The player picked one of this provider's options (ids are namespaced, e.g. {@code quest:offer/17}). Returns false
	 * if the option is no longer valid (the conversation refreshes its options).
	 */
	boolean select(ServerPlayer player, Settlement settlement, Resident resident, String optionId);

	/**
	 * @param id    namespaced, unique among the options shown at once
	 * @param order where it sits in the list (lower first): 0–99 greetings and small talk, 100–199 trade, 200–299 quests
	 */
	record Option(String id, Component label, int order) {
	}

	static void register(DialogueProvider provider) {
		Registry.PROVIDERS.add(provider);
	}

	static List<DialogueProvider> providers() {
		return Registry.PROVIDERS;
	}

	final class Registry {
		private static final List<DialogueProvider> PROVIDERS = new CopyOnWriteArrayList<>();

		private Registry() {
		}
	}
}
