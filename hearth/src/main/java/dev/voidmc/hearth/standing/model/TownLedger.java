package dev.voidmc.hearth.standing.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.api.StandingService.Title;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import org.jspecify.annotations.Nullable;

/**
 * A settlement's standing book: every player's standing there (the authority once the standing module runs; the sim's
 * {@code Settlement.reputation} reads it) and the news the town has heard — its own folk's talk of deeds done here and
 * gossip arrived from elsewhere — for its folk to talk about. Saved with the settlement under {@code hearth:standing/town}.
 */
public final class TownLedger {
	public static final Codec<TownLedger> CODEC = RecordCodecBuilder.create(i -> i.group(
		Entry.CODEC.listOf().optionalFieldOf("players", List.of()).forGetter(t -> List.copyOf(t.entries.values())),
		News.CODEC.listOf().optionalFieldOf("news", List.of()).forGetter(t -> List.copyOf(t.news))
	).apply(i, (entries, news) -> {
		TownLedger t = new TownLedger();
		for (Entry e : entries) {
			t.entries.put(e.player, e);
		}
		t.news.addAll(news);
		return t;
	}));

	private final Map<UUID, Entry> entries = new LinkedHashMap<>();
	private final List<News> news = new ArrayList<>();

	/** One player's standing in the town. */
	public static final class Entry {
		static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("player").forGetter(e -> e.player),
			Codec.DOUBLE.optionalFieldOf("value", 0.0).forGetter(e -> e.value),
			Title.CODEC.optionalFieldOf("title", Title.STRANGER).forGetter(e -> e.title),
			Codec.LONG.optionalFieldOf("touched", 0L).forGetter(e -> e.touched),
			Codec.INT.optionalFieldOf("deeds", 0).forGetter(e -> e.deeds)
		).apply(i, (player, value, title, touched, deeds) -> {
			Entry e = new Entry(player);
			e.value = value;
			e.title = title;
			e.touched = touched;
			e.deeds = deeds;
			return e;
		}));

		public final UUID player;
		/** −100…100; kept fractional so small shares and slow drift add up. */
		public double value;
		/** The title last announced (STANDING_CHANGED fires when the computed one differs). */
		public Title title = Title.STRANGER;
		/** The sim day of the last change. */
		public long touched;
		/** Deeds counted here (for "known": someone who has done things here is no longer a stranger to talk of). */
		public int deeds;

		public Entry(UUID player) {
			this.player = player;
		}
	}

	/**
	 * Something folk here talk about.
	 *
	 * @param playerName the player's name when it happened (folk say names, not ids)
	 * @param town       where it happened (this town's own name for local deeds)
	 * @param weight     signed strength as heard here
	 * @param local      done here, not heard from elsewhere
	 */
	public record News(UUID player, String playerName, String kind, int weight, String town, long day, boolean local) {
		static final Codec<News> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("player").forGetter(News::player),
			Codec.STRING.optionalFieldOf("name", "").forGetter(News::playerName),
			Codec.STRING.fieldOf("kind").forGetter(News::kind),
			Codec.INT.fieldOf("weight").forGetter(News::weight),
			Codec.STRING.optionalFieldOf("town", "").forGetter(News::town),
			Codec.LONG.fieldOf("day").forGetter(News::day),
			Codec.BOOL.optionalFieldOf("local", true).forGetter(News::local)
		).apply(i, News::new));
	}

	public @Nullable Entry get(UUID player) {
		return this.entries.get(player);
	}

	/** The player's entry, created at 0 if absent. */
	public Entry entry(UUID player) {
		return this.entries.computeIfAbsent(player, Entry::new);
	}

	public boolean has(UUID player) {
		return this.entries.containsKey(player);
	}

	public java.util.Collection<Entry> entries() {
		return this.entries.values();
	}

	/** Newest first. */
	public List<News> news() {
		return List.copyOf(this.news);
	}

	/** Remembers news; past {@code limit} the faintest old item is forgotten. */
	public void addNews(News item, int limit) {
		if (limit <= 0) {
			return;
		}
		this.news.addFirst(item);
		while (this.news.size() > limit) {
			// Forget the weakest of the older half, so a murder outlives a dozen small kindnesses.
			int from = this.news.size() / 2;
			int weakest = from;
			for (int i = from; i < this.news.size(); i++) {
				if (Math.abs(this.news.get(i).weight) < Math.abs(this.news.get(weakest).weight)) {
					weakest = i;
				}
			}
			this.news.remove(weakest);
		}
	}

	/** The news about one player, newest first. */
	public List<News> newsOf(UUID player) {
		List<News> out = new ArrayList<>();
		for (News n : this.news) {
			if (n.player.equals(player)) {
				out.add(n);
			}
		}
		return out;
	}
}
