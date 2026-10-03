package dev.voidmc.hearth.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Placeholders in quest text: {@code {settlement}}, {@code {giver}}, {@code {count}} and the rest of {@link #KNOWN}. The
 * generator fills them once, when the quest is made, so the client only ever shows finished strings.
 */
public final class QuestText {
	/** Every placeholder the generator can fill. */
	public static final Set<String> KNOWN = Set.of(
		"settlement",   // the settlement giving the quest
		"giver",        // the resident giving it (or the settlement's council)
		"giver_job",    // their trade, lower case ("smith")
		"target",       // a destination settlement, structure, biome or place
		"count",        // how many
		"item",         // the item asked for
		"place",        // what is being built ("cottage", "bridge")
		"goods",        // the kind of goods short ("wood")
		"direction",    // compass direction from the settlement to the target ("north-east")
		"distance",     // rough distance to the target ("about 600 blocks")
		"mob",          // the creature to hunt
		"name",         // a beast's, a pack animal's or a keepsake's name
		"days",         // days allowed
		"recipient",    // who receives a letter or parcel
		"culture",      // the settlement's culture ("alpine")
		"tier"          // the settlement's size ("village")
	);

	private QuestText() {
	}

	/** Replaces each {@code {name}} that has a value; unknown placeholders are left as they are. */
	public static String fill(String text, Map<String, String> vars) {
		if (text.indexOf('{') < 0) {
			return text;
		}
		StringBuilder out = new StringBuilder(text.length() + 32);
		int i = 0;
		while (i < text.length()) {
			char c = text.charAt(i);
			if (c == '{') {
				int end = text.indexOf('}', i + 1);
				if (end > i) {
					String name = text.substring(i + 1, end);
					String value = vars.get(name);
					if (value != null) {
						out.append(value);
						i = end + 1;
						continue;
					}
				}
			}
			out.append(c);
			i++;
		}
		return capitalizeFirst(out.toString());
	}

	/** The placeholder names used in a text. */
	public static List<String> placeholders(String text) {
		List<String> names = new ArrayList<>();
		int i = text.indexOf('{');
		while (i >= 0) {
			int end = text.indexOf('}', i + 1);
			if (end < 0) {
				break;
			}
			String name = text.substring(i + 1, end);
			if (!name.isEmpty() && name.chars().allMatch(ch -> Character.isLetterOrDigit(ch) || ch == '_')) {
				names.add(name);
			}
			i = text.indexOf('{', end + 1);
		}
		return names;
	}

	/** A sentence that starts with a placeholder ("{giver} needs...") still starts with a capital. */
	private static String capitalizeFirst(String s) {
		if (s.isEmpty() || !Character.isLowerCase(s.charAt(0))) {
			return s;
		}
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	/** Compass direction from one point to another, in eight points ("north-east"). */
	public static String direction(int fromX, int fromZ, int toX, int toZ) {
		double dx = toX - fromX;
		double dz = toZ - fromZ;
		if (dx == 0 && dz == 0) {
			return "here";
		}
		// Minecraft: -z is north, +x is east.
		double angle = Math.toDegrees(Math.atan2(dx, -dz));
		int sector = (int) Math.floorMod(Math.round(angle / 45.0), 8);
		return switch (sector) {
			case 0 -> "north";
			case 1 -> "north-east";
			case 2 -> "east";
			case 3 -> "south-east";
			case 4 -> "south";
			case 5 -> "south-west";
			case 6 -> "west";
			default -> "north-west";
		};
	}

	/** A rough distance for speech: "a short walk", "about 400 blocks", "a long way (about 2,300 blocks)". */
	public static String distance(double blocks) {
		if (blocks < 120) {
			return "a short walk";
		}
		long rounded = Math.round(blocks / 100.0) * 100;
		String n = String.format(java.util.Locale.ROOT, "%,d", rounded);
		return blocks > 1500 ? "a long way, about " + n + " blocks" : "about " + n + " blocks";
	}
}
