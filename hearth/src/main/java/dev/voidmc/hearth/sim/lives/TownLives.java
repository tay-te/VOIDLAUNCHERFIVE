package dev.voidmc.hearth.sim.lives;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.api.JobType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import org.jspecify.annotations.Nullable;

/**
 * A town's part of the lives ({@code hearth:lives/town}, docs/LIVING_WORLD_2.md §4.5): its named characters and where
 * their threads stand, the feuds between its households, the life news its folk talk about, and the counts the harness
 * reads. Residents' own relations and crafts are in their records ({@link Life}). Server thread only.
 */
public final class TownLives {
	static final int NEWS_KEPT = 8;
	static final int MET_KEPT = 16;
	static final int PATH_KEPT = 10;

	public static final Codec<TownLives> CODEC = RecordCodecBuilder.create(i -> i.group(
		Notable.CODEC.listOf().optionalFieldOf("notables", List.of()).forGetter(t -> t.notables),
		Feud.CODEC.listOf().optionalFieldOf("feuds", List.of()).forGetter(t -> t.feuds),
		News.CODEC.listOf().optionalFieldOf("news", List.of()).forGetter(t -> t.news),
		Stats.CODEC.optionalFieldOf("stats", new Stats()).forGetter(t -> t.stats),
		Codec.LONG.optionalFieldOf("last_day", Long.MIN_VALUE).forGetter(t -> t.lastDay),
		Codec.BOOL.optionalFieldOf("adopted", false).forGetter(t -> t.adopted),
		UUIDUtil.CODEC.listOf().optionalFieldOf("orphans", List.of()).forGetter(t -> List.copyOf(t.orphans))
	).apply(i, (notables, feuds, news, stats, lastDay, adopted, orphans) -> {
		TownLives t = new TownLives();
		t.notables.addAll(notables);
		t.feuds.addAll(feuds);
		t.news.addAll(news);
		t.stats = stats;
		t.lastDay = lastDay;
		t.adopted = adopted;
		t.orphans.addAll(orphans);
		return t;
	}));

	public final List<Notable> notables = new ArrayList<>();
	public final List<Feud> feuds = new ArrayList<>();
	/** Newest first. */
	public final List<News> news = new ArrayList<>();
	public Stats stats = new Stats();
	/** The last day the town's lives were stepped. */
	public long lastDay = Long.MIN_VALUE;
	/** Whether the town's standing masters were named (once, the first day the lives see the town). */
	public boolean adopted;
	/** Children whose parents are all gone (the claims find them a home with a grown brother or sister). */
	public final List<UUID> orphans = new ArrayList<>();

	// ---- derived (not saved): rebuilt by the day's pass, or on first use after a load

	/** Jobs settled for apprentices grown, journeymen and masters (ResidentClaims). */
	transient @Nullable Map<UUID, Claim> claims;
	/** The masters, journeymen and apprentices the day's crafts saw (the claims look only at them), or null after a load. */
	transient @Nullable List<UUID> crafters;
	/** Each home's nearest neighbouring home, kept a week (or until the plots change). */
	transient @Nullable Neighbours neighbours;

	record Neighbours(long day, int plots, it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap map) {
	}

	/** Each trade's workplaces, filled as asked, kept until the plots change or a week passes. */
	transient @Nullable Places places;

	record Places(long day, int plots, Map<JobType, List<Workplaces.Place>> map) {
	}

	/** No named character is looked for before this day (the last search found nobody to fit). */
	transient long searchAfter = Long.MIN_VALUE;
	/** The grown folk grouped by work and home, kept a week (the relations' pass). */
	transient Bonds.@Nullable Groups groups;
	/** Homes settled for orphans (with a grown brother or sister). */
	transient @Nullable Map<UUID, Integer> homes;
	/** Residents of feuding households, each with the heads of the households they feud with. */
	transient @Nullable Map<UUID, Set<UUID>> feuders;

	public record Claim(JobType job, int work) {
	}

	public @Nullable Notable notable(UUID resident) {
		for (Notable n : this.notables) {
			if (n.resident.equals(resident)) {
				return n;
			}
		}
		return null;
	}

	void news(String kind, long day, UUID subject, List<String> names) {
		this.news.addFirst(new News(kind, day, subject, names));
		while (this.news.size() > NEWS_KEPT) {
			this.news.removeLast();
		}
	}

	void invalidate() {
		this.claims = null;
		this.homes = null;
		this.feuders = null;
	}

	/** One named character and their thread. */
	public static final class Notable {
		static final Codec<Notable> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("resident").forGetter(n -> n.resident),
			Codec.STRING.fieldOf("role").forGetter(n -> n.role),
			Codec.STRING.fieldOf("thread").forGetter(n -> n.thread),
			Codec.STRING.fieldOf("beat").forGetter(n -> n.beat),
			Codec.LONG.optionalFieldOf("since", 0L).forGetter(n -> n.since),
			UUIDUtil.CODEC.optionalFieldOf("partner").forGetter(n -> Optional.ofNullable(n.partner)),
			UUIDUtil.CODEC.optionalFieldOf("by").forGetter(n -> Optional.ofNullable(n.by)),
			Codec.STRING.optionalFieldOf("chose", "").forGetter(n -> n.chose),
			Codec.STRING.listOf().optionalFieldOf("flags", List.of()).forGetter(n -> List.copyOf(n.flags)),
			UUIDUtil.CODEC.listOf().optionalFieldOf("met", List.of()).forGetter(n -> List.copyOf(n.met)),
			Codec.STRING.listOf().optionalFieldOf("path", List.of()).forGetter(n -> List.copyOf(n.path)),
			Codec.STRING.optionalFieldOf("by_name", "").forGetter(n -> n.byName)
		).apply(i, (resident, role, thread, beat, since, partner, by, chose, flags, met, path, byName) -> {
			Notable n = new Notable(resident, role, thread, beat, since);
			n.partner = partner.orElse(null);
			n.by = by.orElse(null);
			n.chose = chose;
			n.flags.addAll(flags);
			n.met.addAll(met);
			n.path.addAll(path);
			n.byName = byName;
			return n;
		}));

		public final UUID resident;
		public final String role;
		public final String thread;
		/** Where the thread stands. */
		public String beat;
		/** The day the beat began. */
		public long since;
		/** The other person the thread is about (the lover, the apprentice, the creditor), if any. */
		public @Nullable UUID partner;
		/** The player whose choice moved the thread last, if any. */
		public @Nullable UUID by;
		public String byName = "";
		/** The last choice made in the thread (an option id). */
		public String chose = "";
		/** Facts the thread's conditions read: {@code quest:<template>} done, {@code helped}, {@code wronged}... */
		public final Set<String> flags = new HashSet<>();
		/** Players who have talked to them (the People page lists those you have met). */
		public final List<UUID> met = new ArrayList<>();
		/** The beats passed, oldest first. */
		public final List<String> path = new ArrayList<>();

		public Notable(UUID resident, String role, String thread, String beat, long since) {
			this.resident = resident;
			this.role = role;
			this.thread = thread;
			this.beat = beat;
			this.since = since;
		}

		void advance(String to, long day, @Nullable UUID player, String playerName) {
			this.path.add(this.beat);
			while (this.path.size() > PATH_KEPT) {
				this.path.removeFirst();
			}
			this.beat = to;
			this.since = day;
			// a beat that came by itself keeps the name of the player who moved the story last
			if (player != null) {
				this.by = player;
				this.byName = playerName;
			}
		}

		/** Records that a player has met them; returns whether this was the first time. */
		public boolean meet(UUID player) {
			if (this.met.contains(player)) {
				return false;
			}
			this.met.add(player);
			while (this.met.size() > MET_KEPT) {
				this.met.removeFirst();
			}
			return true;
		}
	}

	/** A quarrel between two households, by their heads. */
	public static final class Feud {
		static final Codec<Feud> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("a").forGetter(f -> f.headA),
			UUIDUtil.CODEC.fieldOf("b").forGetter(f -> f.headB),
			Codec.STRING.fieldOf("cause").forGetter(f -> f.cause),
			Codec.LONG.fieldOf("since").forGetter(f -> f.since),
			Codec.INT.optionalFieldOf("heat", 50).forGetter(f -> f.heat)
		).apply(i, (a, b, cause, since, heat) -> {
			Feud f = new Feud(a, b, cause, since);
			f.heat = heat;
			return f;
		}));

		public UUID headA;
		public UUID headB;
		/** {@code love}, {@code theft}, {@code debt}, {@code boundary}, {@code brawl}, {@code raid}, {@code thread}. */
		public final String cause;
		public final long since;
		/** How bitter it is, 1–100: brawls and insults feed it, time cools it. */
		public int heat = 50;

		public Feud(UUID headA, UUID headB, String cause, long since) {
			this.headA = headA;
			this.headB = headB;
			this.cause = cause;
			this.since = since;
		}

		public boolean involves(UUID head) {
			return this.headA.equals(head) || this.headB.equals(head);
		}

		public boolean between(UUID a, UUID b) {
			return this.headA.equals(a) && this.headB.equals(b) || this.headA.equals(b) && this.headB.equals(a);
		}
	}

	/**
	 * Something in a resident's life the town talks about: {@code master} (made master), {@code apprenticed},
	 * {@code sweethearts}, {@code feud}, {@code peace}, {@code widowed}, {@code thread} (a named character's story moved).
	 */
	public record News(String kind, long day, UUID subject, List<String> names) {
		static final Codec<News> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("kind").forGetter(News::kind),
			Codec.LONG.fieldOf("day").forGetter(News::day),
			UUIDUtil.CODEC.fieldOf("subject").forGetter(News::subject),
			Codec.STRING.listOf().optionalFieldOf("names", List.of()).forGetter(News::names)
		).apply(i, News::new));
	}

	/** What the town's lives have come to, counted (the 200-day harness and {@code /hearth lives} read them). */
	public static final class Stats {
		static final Codec<Stats> CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT).xmap(m -> {
			Stats s = new Stats();
			s.counts.putAll(m);
			return s;
		}, s -> Map.copyOf(s.counts));

		final Map<String, Integer> counts = new HashMap<>();

		public int get(String key) {
			return this.counts.getOrDefault(key, 0);
		}

		void add(String key) {
			this.counts.merge(key, 1, Integer::sum);
		}

		public Map<String, Integer> all() {
			return Map.copyOf(this.counts);
		}
	}
}
