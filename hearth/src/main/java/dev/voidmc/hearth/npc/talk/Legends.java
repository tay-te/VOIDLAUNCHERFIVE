package dev.voidmc.hearth.npc.talk;

import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.PlayerSkills;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * Folk who know a legend (docs/requests/mettle-cards.md §10, mettle-speech-stealth.md §12 and §14): a character mod's
 * worn title ({@link PlayerSkills#title}), the Mythics the player holds ({@link PlayerSkills#titles}) and a hero's
 * welcome ({@code hearth:greet/hero}) give a conversation's greeting a line about them now and then. Flavour only:
 * standing, opinion and the per-town titles are untouched. Without a skills mod there is nothing to say and no random
 * number is drawn, so greetings are exactly as before.
 */
public final class Legends {
	/** One greeting in this many (when there is something to say) mentions the legend. */
	static final int ONE_IN = 2;

	private Legends() {
	}

	/** What a skills mod says about the player, for the greeting. */
	public record Fame(String title, List<Identifier> mythics, boolean hero) {
		public static final Fame NONE = new Fame("", List.of(), false);

		public boolean any() {
			return !this.title.isEmpty() || !this.mythics.isEmpty() || this.hero;
		}
	}

	/** Reads the player's fame from the skills mod; {@link Fame#NONE} without one (or if it throws). */
	public static Fame of(ServerPlayer player) {
		PlayerSkills skills = HearthServices.playerSkills();
		if (skills == PlayerSkills.NONE) {
			return Fame.NONE;
		}
		try {
			Component title = skills.title(player);
			List<Identifier> mythics = skills.titles(player);
			boolean hero = PlayerSkills.bonus(skills, player, PlayerSkills.BONUS_GREET_HERO, 0, 1) >= 0.5;
			return new Fame(title == null ? "" : title.getString().trim(), mythics == null ? List.of() : List.copyOf(mythics), hero);
		} catch (RuntimeException e) {
			return Fame.NONE;
		}
	}

	/**
	 * A line about the player's legend, or null: nothing known (no random number drawn), or not this time. Picks among
	 * what is known: the worn title, one of their Mythics, a hero's welcome.
	 */
	public static @Nullable String line(Fame fame, DialogueTemplates.Context ctx, Map<String, String> values, RandomSource random) {
		if (!fame.any() || random.nextInt(ONE_IN) != 0) {
			return null;
		}
		List<String> kinds = new ArrayList<>(3);
		if (!fame.title.isEmpty()) {
			kinds.add("legend.title");
		}
		if (!fame.mythics.isEmpty()) {
			kinds.add("legend.mythic");
		}
		if (fame.hero) {
			kinds.add("legend.hero");
		}
		String kind = kinds.get(random.nextInt(kinds.size()));
		Map<String, String> v = new HashMap<>(values);
		v.put("title", fame.title);
		if (!fame.mythics.isEmpty()) {
			v.put("mythic", mythicName(fame.mythics.get(random.nextInt(fame.mythics.size()))));
		}
		String line = DialogueTemplates.pick(kind, ctx, random);
		return line == null ? null : DialogueTemplates.fill(line, v);
	}

	/** A Mythic's name as folk say it: {@code mettle:mythic/dragon_fall} is "Dragon Fall". */
	public static String mythicName(Identifier id) {
		String path = id.getPath();
		int slash = path.lastIndexOf('/');
		String[] words = (slash >= 0 ? path.substring(slash + 1) : path).split("[_-]+");
		StringBuilder out = new StringBuilder();
		for (String w : words) {
			if (w.isEmpty()) {
				continue;
			}
			if (!out.isEmpty()) {
				out.append(' ');
			}
			out.append(w.substring(0, 1).toUpperCase(Locale.ROOT)).append(w.substring(1));
		}
		return out.toString();
	}
}
