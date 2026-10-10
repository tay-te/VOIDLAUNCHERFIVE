package dev.voidmc.mettle.character.cards;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.core.CardData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The world ledger (docs/CARDS.md §2.1): the single truth for every card that exists, saved with the world
 * ({@code data/mettle/cards.dat}). Ids come from a counter that never goes back, so they never repeat; released cards
 * keep theirs. Every change is one call on the server thread. Players' {@code CardsState} caches are rebuilt from here.
 */
public final class CardLedger extends SavedData {
	public static final Codec<CardLedger> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.LONG.optionalFieldOf("next_id", 1L).forGetter(l -> l.nextId),
		CardData.CODEC.listOf().optionalFieldOf("cards", List.of()).forGetter(l -> List.copyOf(l.cards.values())),
		Codec.unboundedMap(Codec.STRING, CardData.Holder.CODEC).optionalFieldOf("firsts", Map.of()).forGetter(l -> Map.copyOf(l.firsts)),
		Codec.unboundedMap(Codec.STRING, net.minecraft.core.UUIDUtil.CODEC.listOf()).optionalFieldOf("deeds", Map.of()).forGetter(l -> Map.copyOf(l.deeds))
	).apply(i, (next, cards, firsts, deeds) -> {
		CardLedger l = new CardLedger();
		l.nextId = next;
		cards.forEach(l::index);
		l.firsts.putAll(firsts);
		deeds.forEach((k, v) -> l.deeds.put(k, new ArrayList<>(v)));
		return l;
	}));

	public static final SavedDataType<CardLedger> TYPE = new SavedDataType<>(Mettle.id("cards"), CardLedger::new, CODEC, null);

	private long nextId = 1;
	private final Map<Long, CardData> cards = new LinkedHashMap<>();
	private final Map<UUID, List<Long>> byOwner = new HashMap<>();
	/** The Hall of Firsts (§9.1): each world-first or drawn Mythic claimed, with who earned it and when. */
	private final Map<String, CardData.Holder> firsts = new LinkedHashMap<>();
	/** Each deed Mythic: who earned a copy, in order (the n-th copy in this world). */
	private final Map<String, List<UUID>> deeds = new LinkedHashMap<>();

	public CardLedger() {
	}

	public static CardLedger get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
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
		if (this.cards.containsKey(card.id())) {
			throw new IllegalStateException("card id " + card.id() + " already exists");
		}
		index(card);
		setDirty();
	}

	public Optional<CardData> get(long id) {
		return Optional.ofNullable(this.cards.get(id));
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

	public java.util.Optional<CardData.Holder> first(String mythic) {
		return java.util.Optional.ofNullable(this.firsts.get(mythic));
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

	/** Records a deed copy; returns which copy it is (1 = the first in this world). */
	public int deed(String mythic, UUID player) {
		List<UUID> who = this.deeds.computeIfAbsent(mythic, k -> new ArrayList<>());
		who.add(player);
		setDirty();
		return who.size();
	}

	/** Every card ever made, released ones too. */
	public int size() {
		return this.cards.size();
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
}
