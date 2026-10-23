package dev.voidmc.bestiary.codex;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.bestiary.Bestiary;
import dev.voidmc.bestiary.ai.profile.CreatureProfile;
import dev.voidmc.bestiary.ai.profile.MoveDef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;

/**
 * Codex v0 (BESTIARY.md §10.3): data only, no screen yet (the Hub page comes later, Figma first). Per player and saved
 * with them (kept through death): every creature they have <b>seen</b> (one engaged them), <b>fought</b> (a hit either
 * way), <b>felled</b> (how many), the moves they have learned (each tell that was aimed at them) and whether they have
 * <b>studied</b> it (a Hearth scholar, a bestiary page: {@link #study}). The knowledge level follows: seen, fought,
 * felled 10, studied.
 */
public final class Codex {
	public enum Knowledge {
		NONE, SEEN, FOUGHT, FELLED, STUDIED
	}

	/** One creature's entry: first seen (game tick), fights, kills, moves learned, studied. */
	public record Entry(long seen, int fought, int felled, List<String> moves, boolean studied) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.optionalFieldOf("seen", 0L).forGetter(Entry::seen),
			Codec.INT.optionalFieldOf("fought", 0).forGetter(Entry::fought),
			Codec.INT.optionalFieldOf("felled", 0).forGetter(Entry::felled),
			Codec.STRING.listOf().optionalFieldOf("moves", List.of()).forGetter(Entry::moves),
			Codec.BOOL.optionalFieldOf("studied", false).forGetter(Entry::studied)).apply(i, Entry::new));
		static final Entry EMPTY = new Entry(0, 0, 0, List.of(), false);

		public Knowledge knowledge() {
			if (this.studied) {
				return Knowledge.STUDIED;
			}
			if (this.felled >= 10) {
				return Knowledge.FELLED;
			}
			if (this.fought > 0 || this.felled > 0) {
				return Knowledge.FOUGHT;
			}
			return this.seen > 0 ? Knowledge.SEEN : Knowledge.NONE;
		}
	}

	/** The whole codex: version and entries by creature profile id. */
	public record Book(int version, Map<String, Entry> entries) {
		public static final Codec<Book> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("version", 1).forGetter(Book::version),
			Codec.unboundedMap(Codec.STRING, Entry.CODEC).optionalFieldOf("entries", Map.of()).forGetter(Book::entries)).apply(i, Book::new));
		static final Book EMPTY = new Book(1, Map.of());
	}

	public static final AttachmentType<Book> TYPE = AttachmentRegistry.create(Bestiary.id("codex"), b -> b.persistent(Book.CODEC).copyOnDeath());

	private Codex() {
	}

	public static Book book(ServerPlayer p) {
		Book b = p.getAttached(TYPE);
		return b == null ? Book.EMPTY : b;
	}

	public static Entry entry(ServerPlayer p, String creature) {
		return book(p).entries().getOrDefault(creature, Entry.EMPTY);
	}

	private static void update(ServerPlayer p, CreatureProfile c, java.util.function.UnaryOperator<Entry> f) {
		Book b = book(p);
		String key = c.id().toString();
		Entry before = b.entries().getOrDefault(key, Entry.EMPTY);
		Entry after = f.apply(before);
		if (after.equals(before)) {
			return;
		}
		Map<String, Entry> m = new LinkedHashMap<>(b.entries());
		m.put(key, after);
		p.setAttached(TYPE, new Book(1, Map.copyOf(m)));
	}

	public static void seen(ServerPlayer p, CreatureProfile c) {
		long now = p.level().getGameTime();
		update(p, c, e -> e.seen() > 0 ? e : new Entry(Math.max(1, now), e.fought(), e.felled(), e.moves(), e.studied()));
	}

	public static void fought(ServerPlayer p, CreatureProfile c) {
		long now = p.level().getGameTime();
		update(p, c, e -> new Entry(e.seen() > 0 ? e.seen() : Math.max(1, now), Math.min(Integer.MAX_VALUE - 1, e.fought() + 1), e.felled(), e.moves(),
			e.studied()));
	}

	public static void felled(ServerPlayer p, CreatureProfile c) {
		long now = p.level().getGameTime();
		update(p, c, e -> new Entry(e.seen() > 0 ? e.seen() : Math.max(1, now), Math.max(1, e.fought()), e.felled() + 1, e.moves(), e.studied()));
	}

	/** A tell was aimed at the player: they now know this move (and its colour, which is its clip's). */
	public static void moveSeen(ServerPlayer p, CreatureProfile c, MoveDef m) {
		update(p, c, e -> {
			if (e.moves().contains(m.id())) {
				return e;
			}
			List<String> moves = new ArrayList<>(e.moves());
			moves.add(m.id());
			return new Entry(e.seen() > 0 ? e.seen() : Math.max(1, p.level().getGameTime()), e.fought(), e.felled(), List.copyOf(moves), e.studied());
		});
	}

	/** Everything about a creature (a Hearth scholar, a bestiary page in a ruin, a Mettle perk). */
	public static void study(ServerPlayer p, CreatureProfile c) {
		update(p, c, e -> new Entry(e.seen() > 0 ? e.seen() : Math.max(1, p.level().getGameTime()), e.fought(), e.felled(), e.moves(), true));
	}
}
