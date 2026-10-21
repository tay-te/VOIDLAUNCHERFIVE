package dev.voidmc.mettle.character.cards;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.MettleCodecs;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The world ledger (docs/CARDS.md §2.1): the single truth for every card that exists, saved with the world
 * ({@code data/mettle/cards.dat}, written by {@link LedgerFile}: atomically, with a {@code .dat_old} backup, whenever a
 * player file is saved and right after a trade, a Mythic or a migration). Ids come from a counter that never goes back, so
 * they never repeat; released cards keep theirs. Every change is one call on the server thread. Players'
 * {@code CardsState} caches are rebuilt from here.
 *
 * <p>Never loses data on a save: a card entry this version can't read (a hand edit, a newer version's field) is kept as
 * it was read and written back unchanged, with a loud error in the log. Released cards are archived: only their ids are
 * written ({@code released}), so a big, old world's ledger stays small and an id is still never handed out twice.
 */
public final class CardLedger extends SavedData {
	/** Reads every readable card, keeps the rest raw; never fails on one bad entry. */
	public static final Codec<CardLedger> CODEC = new Codec<>() {
		@Override
		public <O> DataResult<Pair<CardLedger, O>> decode(DynamicOps<O> ops, O input) {
			DataResult<MapLike<O>> m = ops.getMap(input);
			if (m.error().isPresent()) {
				return DataResult.error(() -> "the card ledger is not a map: " + m.error().get().message());
			}
			MapLike<O> map = m.getOrThrow();
			CardLedger l = new CardLedger();
			O next = map.get("next_id");
			if (next != null) {
				l.nextId = Math.max(1, Codec.LONG.parse(ops, next).result().orElse(1L));
			}
			O cards = map.get("cards");
			if (cards != null) {
				ops.getStream(cards).result().orElse(Stream.empty()).forEach(o -> {
					DataResult<CardData> c = CardData.CODEC.parse(ops, o);
					if (c.isSuccess()) {
						CardData card = c.getOrThrow();
						if (card.place().kind() == CardData.PlaceKind.RELEASED) {
							l.archive(card.id());
						} else {
							l.index(card);
						}
					} else {
						Dynamic<O> raw = new Dynamic<>(ops, o);
						long id = raw.get("id").asLong(-1);
						l.unreadable.add(raw);
						if (id >= 0) {
							l.unreadableIds.add(id);
							l.nextId = Math.max(l.nextId, id + 1);
						}
						Mettle.LOGGER.error("Mettle: card {} in the ledger can't be read ({}); it is kept as it is and written back unchanged",
							id, c.error().map(e -> e.message()).orElse("?"));
					}
				});
			}
			O firsts = map.get("firsts");
			if (firsts != null) {
				MettleCodecs.lenientMap(Codec.STRING, CardData.Holder.CODEC, "Hall of Firsts entry").parse(ops, firsts).result().ifPresent(l.firsts::putAll);
			}
			O deeds = map.get("deeds");
			if (deeds != null) {
				MettleCodecs.lenientMap(Codec.STRING, net.minecraft.core.UUIDUtil.CODEC.listOf(), "deed").parse(ops, deeds).result()
					.ifPresent(d -> d.forEach((k, v) -> l.deeds.put(k, new ArrayList<>(v))));
			}
			O returns = map.get("deed_returns");
			if (returns != null) {
				MettleCodecs.lenientList(Returned.CODEC, "returned deed copy").parse(ops, returns).result().ifPresent(l.returns::addAll);
			}
			O released = map.get("released");
			if (released != null) {
				Codec.LONG_STREAM.parse(ops, released).result().ifPresent(s -> s.forEach(l::archive));
			}
			return DataResult.success(Pair.of(l, input));
		}

		@Override
		public <O> DataResult<O> encode(CardLedger l, DynamicOps<O> ops, O prefix) {
			List<O> cards = new ArrayList<>(l.cards.size() + l.unreadable.size());
			LongOpenHashSet released = new LongOpenHashSet(l.archived);
			for (CardData c : l.cards.values()) {
				if (c.place().kind() == CardData.PlaceKind.RELEASED) {
					released.add(c.id());
					continue;
				}
				if (ops == net.minecraft.nbt.NbtOps.INSTANCE) {
					// The fast path for the saved file (the same tag the codec writes).
					@SuppressWarnings("unchecked")
					O tag = (O) CardNbt.write(c);
					cards.add(tag);
					continue;
				}
				DataResult<O> e = CardData.CODEC.encodeStart(ops, c);
				if (e.isError()) {
					// Can't happen with CardData's codec; never drop the card for it.
					return DataResult.error(() -> "card " + c.id() + " can't be written: " + e.error().map(x -> x.message()).orElse("?"));
				}
				cards.add(e.getOrThrow());
			}
			for (Dynamic<?> raw : l.unreadable) {
				cards.add(convert(raw, ops));
			}
			RecordBuilder<O> b = ops.mapBuilder();
			b.add("next_id", ops.createLong(l.nextId));
			b.add("cards", ops.createList(cards.stream()));
			b.add("firsts", Codec.unboundedMap(Codec.STRING, CardData.Holder.CODEC).encodeStart(ops, Map.copyOf(l.firsts)));
			b.add("deeds", Codec.unboundedMap(Codec.STRING, net.minecraft.core.UUIDUtil.CODEC.listOf()).encodeStart(ops, Map.copyOf(l.deeds)));
			b.add("deed_returns", Returned.CODEC.listOf().encodeStart(ops, List.copyOf(l.returns)));
			long[] ids = released.toLongArray();
			java.util.Arrays.sort(ids);
			b.add("released", ops.createLongList(LongStream.of(ids)));
			return b.build(prefix);
		}
	};

	private static <T, O> O convert(Dynamic<T> raw, DynamicOps<O> ops) {
		return raw.getOps().convertTo(ops, raw.getValue());
	}

	private long nextId = 1;
	private final Map<Long, CardData> cards = new LinkedHashMap<>();
	private final Map<UUID, List<Long>> byOwner = new HashMap<>();
	/** The Hall of Firsts (§9.1): each world-first or drawn Mythic claimed, with who earned it and when. */
	private final Map<String, CardData.Holder> firsts = new LinkedHashMap<>();
	/** Each deed Mythic: who earned a copy, in order (the n-th copy in this world). */
	private final Map<String, List<UUID>> deeds = new LinkedHashMap<>();
	/** Deed copies returned when deed Mythics became one of a kind (docs/BUILD_FEEL.md §12): kept for good, as the log. */
	private final List<Returned> returns = new ArrayList<>();
	/** Ids of released cards archived at load (only the id is kept). */
	private final LongOpenHashSet archived = new LongOpenHashSet();
	/** Card entries this version couldn't read, kept as read and written back unchanged. */
	private final List<Dynamic<?>> unreadable = new ArrayList<>();
	private final LongOpenHashSet unreadableIds = new LongOpenHashSet();

	public CardLedger() {
	}

	/** The server's ledger (loaded by {@link LedgerFile} at start). */
	public static CardLedger get(MinecraftServer server) {
		return LedgerFile.get(server);
	}

	private void index(CardData c) {
		CardData old = this.cards.put(c.id(), c);
		if (old != null) {
			List<Long> mine = this.byOwner.get(old.owner());
			if (mine != null) {
				mine.remove(c.id());
			}
		}
		if (c.place().kind() != CardData.PlaceKind.RELEASED) {
			this.byOwner.computeIfAbsent(c.owner(), u -> new ArrayList<>()).add(c.id());
		}
		this.nextId = Math.max(this.nextId, c.id() + 1);
	}

	private void archive(long id) {
		this.archived.add(id);
		this.nextId = Math.max(this.nextId, id + 1);
	}

	/** A fresh id, never given before. */
	public long allocate() {
		setDirty();
		return this.nextId++;
	}

	public long nextId() {
		return this.nextId;
	}

	/** Adds a new card (its id from {@link #allocate}). */
	public void add(CardData card) {
		if (known(card.id())) {
			throw new IllegalStateException("card id " + card.id() + " already exists");
		}
		index(card);
		setDirty();
	}

	/**
	 * Takes back a card a player's own copy still holds but the ledger doesn't know (a ledger restored from its backup or
	 * lost, then started empty): the ledger was older than the player's file. Refused for an id the ledger knows.
	 */
	public boolean adopt(CardData card) {
		if (known(card.id())) {
			return false;
		}
		index(card);
		setDirty();
		return true;
	}

	/** Whether this id was ever given out here: a card, a released card, or an entry that can't be read. */
	public boolean known(long id) {
		return this.cards.containsKey(id) || this.archived.contains(id) || this.unreadableIds.contains(id);
	}

	public boolean released(long id) {
		return this.archived.contains(id) || get(id).map(c -> c.place().kind() == CardData.PlaceKind.RELEASED).orElse(false);
	}

	public Optional<CardData> get(long id) {
		return Optional.ofNullable(this.cards.get(id));
	}

	/** Every card held here (released ones not yet archived included), in id order of creation. */
	public Collection<CardData> all() {
		return Collections.unmodifiableCollection(this.cards.values());
	}

	/** Changes one card; nothing happens for an unknown id. Returns the new card. */
	public Optional<CardData> update(long id, UnaryOperator<CardData> change) {
		CardData old = this.cards.get(id);
		if (old == null) {
			return Optional.empty();
		}
		CardData next = change.apply(old);
		if (next.id() != id) {
			throw new IllegalArgumentException("a card's id never changes");
		}
		index(next);
		setDirty();
		return Optional.of(next);
	}

	/** Every card a player owns that still exists (not released), by id. */
	public List<CardData> owned(UUID owner) {
		List<CardData> out = new ArrayList<>();
		for (long id : this.byOwner.getOrDefault(owner, List.of())) {
			CardData c = this.cards.get(id);
			if (c != null && c.owner().equals(owner) && c.place().kind() != CardData.PlaceKind.RELEASED) {
				out.add(c);
			}
		}
		out.sort(Comparator.comparingLong(CardData::id));
		return out;
	}

	public Optional<CardData.Holder> first(String mythic) {
		return Optional.ofNullable(this.firsts.get(mythic));
	}

	public Map<String, CardData.Holder> firsts() {
		return Map.copyOf(this.firsts);
	}

	public void claim(String mythic, CardData.Holder holder) {
		this.firsts.put(mythic, holder);
		setDirty();
	}

	/** An op's reset: the first (or the draw) can be earned again. */
	public void unclaim(String mythic) {
		if (this.firsts.remove(mythic) != null | this.deeds.remove(mythic) != null) {
			setDirty();
		}
	}

	public List<UUID> deeds(String mythic) {
		return List.copyOf(this.deeds.getOrDefault(mythic, List.of()));
	}

	/**
	 * A deed copy returned by the one-of-a-kind migration (§12): the card, the deed, who held it, when, and whether its
	 * holder has had the 30 shards and the title yet (they get them on their next join).
	 */
	public record Returned(long card, String deed, UUID player, String name, long day, boolean settled) {
		public static final Codec<Returned> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("card").forGetter(Returned::card),
			Codec.STRING.fieldOf("deed").forGetter(Returned::deed),
			net.minecraft.core.UUIDUtil.CODEC.fieldOf("player").forGetter(Returned::player),
			Codec.STRING.optionalFieldOf("name", "").forGetter(Returned::name),
			Codec.LONG.optionalFieldOf("day", 0L).forGetter(Returned::day),
			Codec.BOOL.optionalFieldOf("settled", false).forGetter(Returned::settled)
		).apply(i, Returned::new));
	}

	/** Every deed copy ever returned (the migration's log). */
	public List<Returned> returns() {
		return List.copyOf(this.returns);
	}

	public void addReturn(Returned r) {
		this.returns.add(r);
		setDirty();
	}

	/** A returned copy's holder has had their shards and title. */
	public void settleReturn(long card) {
		for (int i = 0; i < this.returns.size(); i++) {
			Returned r = this.returns.get(i);
			if (r.card() == card && !r.settled()) {
				this.returns.set(i, new Returned(r.card(), r.deed(), r.player(), r.name(), r.day(), true));
				setDirty();
			}
		}
	}

	/**
	 * Records that a player did a deed (once each; the order is who did it first). Since deeds are one of a kind
	 * (§12) the card goes only to the first; the list still says who did the deed after them.
	 */
	public int deed(String mythic, UUID player) {
		List<UUID> who = this.deeds.computeIfAbsent(mythic, k -> new ArrayList<>());
		int at = who.indexOf(player);
		if (at >= 0) {
			return at + 1;
		}
		who.add(player);
		setDirty();
		return who.size();
	}

	/** Cards held here (released ones not yet archived included); archived and unreadable entries are not counted. */
	public int size() {
		return this.cards.size();
	}

	/** Released cards archived to their ids. */
	public int archivedCount() {
		return this.archived.size();
	}

	/** Entries that couldn't be read (kept raw). */
	public int unreadableCount() {
		return this.unreadable.size();
	}

	/**
	 * On start-up: a card left in a trade by a crash goes back to its owner's binder (§11). Returns how many.
	 */
	public int recoverTrades() {
		int n = 0;
		for (CardData c : List.copyOf(this.cards.values())) {
			if (c.place().kind() == CardData.PlaceKind.TRADE) {
				index(c.withPlace(CardData.Place.BINDER));
				n++;
			}
		}
		if (n > 0) {
			setDirty();
		}
		return n;
	}

	@Override
	public String toString() {
		return "CardLedger[" + this.cards.size() + " cards, " + this.archived.size() + " archived, " + this.unreadable.size() + " unreadable, next id "
			+ this.nextId + ", " + this.firsts.size() + " firsts]";
	}
}
