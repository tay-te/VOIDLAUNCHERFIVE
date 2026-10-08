package dev.voidmc.hearth.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

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
	 * What the resident answers after {@link #select} (a speech check's outcome, a trainer's word), shown as their new
	 * line of speech in the conversation; null (the default) keeps the conversation's current line. Asked right after
	 * {@code select} for the same option, so a provider may remember the outcome there and word it here.
	 */
	default @Nullable Component reply(ServerPlayer player, Settlement settlement, Resident resident, String optionId) {
		return null;
	}

	/**
	 * One choice in the conversation.
	 *
	 * @param id      namespaced, unique among the options shown at once
	 * @param order   where it sits in the list (lower first), by band:
	 *                <pre>
	 *                  0–99     greetings and small talk (speech checks: flattery, amends)
	 *                  100–199  trade and services (market, bank, law: fines, fences)
	 *                  200–299  quests (offers, hand-ins, progress)
	 *                  300–399  training (a character mod's trainers: "Train Blades · 40 bits")
	 *                  10 000   goodbye (always last)
	 *                </pre>
	 * @param locked  shown dimmed and cannot be picked (a check the player is not ready for)
	 * @param hint    a short note drawn at the right of the option ("Persuade · 65 %", "Needs Speech 5"), or null
	 * @param tooltip shown while the option is hovered (why it is locked, what it costs), or null
	 */
	record Option(String id, Component label, int order, boolean locked, @Nullable Component hint, @Nullable Component tooltip) {
		/** A plain option: open, no hint, no tooltip. */
		public Option(String id, Component label, int order) {
			this(id, label, order, false, null, null);
		}
	}

	/** The first of the order band reserved for training options (to {@link #TRAINING_LAST}). */
	int TRAINING_FIRST = 300;
	int TRAINING_LAST = 399;

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
