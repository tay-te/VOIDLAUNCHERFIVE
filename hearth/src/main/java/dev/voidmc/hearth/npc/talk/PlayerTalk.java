package dev.voidmc.hearth.npc.talk;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.HearthDataKey;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * Folk talk about the player (docs/LIVING_WORLD_2.md §4.1, §4.3): what this folk remembers of them (the standing
 * module's memories, kept in {@code Resident.extra("standing").mem}: {@code p} the player, {@code k} the deed, {@code w} its
 * weight, {@code d} the day) and what the town says of them (the standing module's town news, saved under
 * {@code hearth:standing/town}). Both are read through their saved form, so the npc module needs nothing of the standing
 * module but the documented data.
 *
 * <p>Categories of lines (dialogue templates): {@code about.player.<deed>} (what you did here: "You're the one who put out
 * the fire"), {@code remember.last.<deed>} (their own last memory of you: "Last I saw you, you were running from the
 * guards"), each falling back to {@code .good} and {@code .bad} by the deed's sign; the bark categories
 * {@code lives.overheard.<deed>} are for two folk talking about the player within earshot. {deed} kinds are folded into a
 * few slugs ({@link #slug}).
 */
public final class PlayerTalk {
	private static final Map<Long, TownTalk> TOWN = new ConcurrentHashMap<>();
	private static final long TOWN_TTL = 20 * 30;

	private PlayerTalk() {
	}

	/** A deed folk can speak of: its slug, weight, the day it happened, and whether it was done here. */
	public record Deed(String kind, String slug, int weight, long day) {
	}

	private record TownTalk(long at, List<CompoundTag> news) {
	}

	/** A deed kind folded into the slug the lines are written for; "" for kinds nobody talks about. */
	public static String slug(String kind, int weight) {
		String s = switch (kind) {
			case "repair:fire_brigade" -> "fire";
			case "law:theft", "law:pickpocketing", "law:robbery", "law:lockpicking" -> "theft";
			case "law:murder", "npc:killed" -> "murder";
			case "law:assault", "npc:hurt" -> "assault";
			case "quest:completed", "hearth:quest/completed" -> "quest";
			case "quest:failed", "quest:abandoned", "hearth:quest/failed", "hearth:quest/abandoned" -> "let_down";
			case "defense:monster_slain", "defense:raid_repelled" -> "defender";
			case "npc:gift", "social:wedding_gift" -> "gift";
			case "npc:rescue" -> "rescue";
			case "economy:donation", "economy:funded_project", "repair:funded" -> "donor";
			case "repair:helped", "repair:rebuilt" -> "builder";
			case "law:resisting_arrest", "law:jailbreak" -> "fugitive";
			case "law:pillory", "law:jail_served", "law:fine_paid" -> "punished";
			case "social:bought_a_round" -> "round";
			case "social:attended_mass" -> "pious";
			case "npc:insult", "economy:haggle_soured" -> "rude";
			case "law:trespass", "law:vandalism", "repair:vandalism" -> "trouble";
			case "law:poaching" -> "poacher";
			case "economy:bought_property", "economy:bought_business" -> "neighbour";
			case "economy:trade", "standing:flattered", "standing:amends" -> "";
			default -> kind.startsWith("lives:") ? "kindness" : "";
		};
		if (s.isEmpty() && Math.abs(weight) >= 5 && !kind.equals("economy:trade")) {
			return weight > 0 ? "good" : "bad";
		}
		return s;
	}

	private static @Nullable UUID uuid(@Nullable Tag tag) {
		if (tag instanceof IntArrayTag a && a.getAsIntArray().length == 4) {
			int[] i = a.getAsIntArray();
			return new UUID((long) i[0] << 32 | i[1] & 0xFFFFFFFFL, (long) i[2] << 32 | i[3] & 0xFFFFFFFFL);
		}
		return null;
	}

	/** The strongest of this folk's memories of the player from the last {@code days} days (weighted by recency). */
	public static @Nullable Deed remembered(Resident r, UUID player, long today, int days) {
		if (!(r.extra.get("standing") instanceof CompoundTag st) || !(st.get("mem") instanceof ListTag mem)) {
			return null;
		}
		Deed best = null;
		double bestScore = 0;
		for (Tag t : mem) {
			if (!(t instanceof CompoundTag m) || !player.equals(uuid(m.get("p")))) {
				continue;
			}
			long day = m.getLongOr("d", 0);
			int w = m.getIntOr("w", 0);
			String kind = m.getStringOr("k", "");
			String slug = slug(kind, w);
			if (slug.isEmpty() || today - day > days) {
				continue;
			}
			double score = Math.abs(w) / (1.0 + Math.max(0, today - day) / 4.0);
			if (score > bestScore) {
				bestScore = score;
				best = new Deed(kind, slug, w, day);
			}
		}
		return best;
	}

	/** The latest thing this folk remembers the player doing (for "Back again? Last time you..."). */
	public static @Nullable Deed last(Resident r, UUID player) {
		if (!(r.extra.get("standing") instanceof CompoundTag st) || !(st.get("mem") instanceof ListTag mem)) {
			return null;
		}
		Deed best = null;
		for (Tag t : mem) {
			if (t instanceof CompoundTag m && player.equals(uuid(m.get("p")))) {
				int w = m.getIntOr("w", 0);
				String kind = m.getStringOr("k", "");
				String slug = slug(kind, w);
				long day = m.getLongOr("d", 0);
				if (!slug.isEmpty() && (best == null || day >= best.day())) {
					best = new Deed(kind, slug, w, day);
				}
			}
		}
		return best;
	}

	/** What the town says of the player: its strongest recent news of them done here (cached for half a minute). */
	public static @Nullable Deed townTalk(Settlement s, UUID player, long today, int days, long gameTime) {
		TownTalk tt = TOWN.get(s.id());
		if (tt == null || gameTime - tt.at > TOWN_TTL || gameTime < tt.at) {
			tt = new TownTalk(gameTime, news(s));
			TOWN.put(s.id(), tt);
			if (TOWN.size() > 256) {
				TOWN.clear();
			}
		}
		Deed best = null;
		double bestScore = 0;
		for (CompoundTag n : tt.news) {
			if (!player.equals(uuid(n.get("player"))) || !n.getBooleanOr("local", true)) {
				continue;
			}
			long day = n.getLongOr("day", 0);
			int w = n.getIntOr("weight", 0);
			String kind = n.getStringOr("kind", "");
			String slug = slug(kind, w);
			if (slug.isEmpty() || today - day > days) {
				continue;
			}
			double score = Math.abs(w) / (1.0 + Math.max(0, today - day) / 4.0);
			if (score > bestScore) {
				bestScore = score;
				best = new Deed(kind, slug, w, day);
			}
		}
		return best;
	}

	/** Forgets the cached town talk (a deed just done should be talked of at once). */
	public static void forget(long settlement) {
		TOWN.remove(settlement);
	}

	@SuppressWarnings("unchecked")
	private static List<CompoundTag> news(Settlement s) {
		HearthDataKey<?> key = HearthDataKey.get(Hearth.id("standing/town"));
		List<CompoundTag> out = new ArrayList<>();
		if (key == null) {
			return out;
		}
		try {
			Object ledger = s.data(key);
			Tag t = ((com.mojang.serialization.Codec<Object>) key.codec()).encodeStart(NbtOps.INSTANCE, ledger).result().orElse(null);
			if (t instanceof CompoundTag c && c.get("news") instanceof ListTag list) {
				for (Tag e : list) {
					if (e instanceof CompoundTag n) {
						out.add(n);
					}
				}
			}
		} catch (RuntimeException e) {
			// a ledger that cannot be read: nothing said
		}
		return out;
	}

	/** A line of {@code <prefix>.<slug>} (or the deed's sign) that fits, filled; null if none. */
	public static @Nullable String line(String prefix, Deed deed, DialogueTemplates.Context ctx, Map<String, String> values, RandomSource random) {
		String text = DialogueTemplates.pick(prefix + "." + deed.slug(), ctx, random);
		if (text == null) {
			text = DialogueTemplates.pick(prefix + "." + (deed.weight() >= 0 ? "good" : "bad"), ctx, random);
		}
		return text == null ? null : DialogueTemplates.fill(text, values);
	}

	/**
	 * Something about the player for the greeting, or null: a remembered deed of their own first, else the town's talk
	 * (folk who were not there have heard).
	 */
	public static @Nullable String aboutPlayer(ServerLevel level, Settlement s, Resident r, ServerPlayer player, DialogueTemplates.Context ctx,
		Map<String, String> values, long today, RandomSource random) {
		Deed own = remembered(r, player.getUUID(), today, 20);
		if (own != null && random.nextInt(3) != 0) {
			String l = line("about.player", own, ctx, values, random);
			if (l != null) {
				return l;
			}
		}
		Deed heard = townTalk(s, player.getUUID(), today, 14, level.getGameTime());
		return heard == null ? null : line("about.player", heard, ctx, values, random);
	}
}
