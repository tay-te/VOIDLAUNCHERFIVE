package dev.voidmc.hearth.npc.travel;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * A conversation with someone met on the road who is not a resident one can name (docs/LIVING_WORLD_2.md §1.1,
 * "Talking to travellers"): a pedlar, a pilgrim, a messenger, a bandit making his demand. It goes through the ordinary
 * conversation page (the existing screen, until the stranger variant F1 is designed in Figma): a description for a name,
 * their trade, where they come from, a line of speech and the options; the server decides everything.
 */
public interface StrangerTalk {
	/** "a pedlar from Ashbrook": who they look like, since nobody knows their name. */
	String name();

	/** Their trade as a translation key ({@code hearth.npc.job.pedlar}). */
	String title();

	/** Where they come from, or "". */
	String from();

	/** What they say as the conversation opens. */
	Component greeting(ServerPlayer player);

	/** The options now. */
	List<Option> options(ServerPlayer player);

	/**
	 * The stranger card (docs/LIVING_WORLD_2.md §10 F1, docs/OVERLAY.md {@code talk.stranger}, 2026-10-21): what the
	 * richer conversation screen draws in place of a resident's card: {@code kind}, {@code hooded}, a band's size and
	 * demand, and so on. Null for the plain card (a description, their trade, where they come from).
	 */
	default com.google.gson.@Nullable JsonObject card(ServerPlayer player) {
		return null;
	}

	/** The player picked an option: the stranger's answer, or null to end the conversation (the screen closes). */
	@Nullable Component choose(ServerPlayer player, String option);

	/**
	 * An option.
	 *
	 * @param kind  what it is for the richer screens ({@code talk}, {@code trade}, {@code persuade}, {@code intimidate},
	 *              {@code bribe}, {@code fight}, {@code leave})
	 * @param odds  a check's chance 0–100, or -1
	 * @param cost  bits it costs, or -1
	 */
	record Option(String id, Component label, boolean locked, @Nullable Component hint, String kind, int odds, long cost) {
		public static Option talk(String id, Component label) {
			return new Option(id, label, false, null, "talk", -1, -1);
		}
	}
}
