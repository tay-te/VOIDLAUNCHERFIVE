package dev.voidmc.hearth.economy.broker;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.UUIDUtil;
import org.jspecify.annotations.Nullable;

/**
 * A settlement's broker board (docs/requests/mettle-cards.md §7): goods that live outside inventories (VOID Mettle's
 * cards), listed by players at a price they name. The goods themselves stay with the mod that owns them, held in escrow
 * while listed; the board keeps only who listed what, for how much, and until when. Saved per settlement
 * ({@code hearth:economy/broker}). Plain data; {@link Broker} moves coin and goods.
 */
public final class BrokerBoard {
	/**
	 * One listing.
	 *
	 * @param id      world-unique ({@link #nextId}): the escrow handle the owning mod is given
	 * @param kind    the goods' kind ({@code mettle:card})
	 * @param goods   the owning mod's id for the thing listed
	 * @param seller  who listed it
	 * @param name    the seller's name when they listed it
	 * @param price   what the seller asks, in bits
	 * @param expires the sim day from which the listing has lapsed and goes back
	 */
	public record Listing(long id, String kind, long goods, UUID seller, String name, long price, long listed, long expires) {
		public static final Codec<Listing> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("id").forGetter(Listing::id),
			Codec.STRING.fieldOf("kind").forGetter(Listing::kind),
			Codec.LONG.fieldOf("goods").forGetter(Listing::goods),
			UUIDUtil.STRING_CODEC.fieldOf("seller").forGetter(Listing::seller),
			Codec.STRING.optionalFieldOf("name", "").forGetter(Listing::name),
			Codec.LONG.fieldOf("price").forGetter(Listing::price),
			Codec.LONG.optionalFieldOf("listed", 0L).forGetter(Listing::listed),
			Codec.LONG.fieldOf("expires").forGetter(Listing::expires)
		).apply(i, Listing::new));
	}

	public static final Codec<BrokerBoard> CODEC = RecordCodecBuilder.create(i -> i.group(
		Listing.CODEC.listOf().optionalFieldOf("listings", List.of()).forGetter(b -> b.listings),
		Codec.LONG.optionalFieldOf("seq", 0L).forGetter(b -> b.seq)
	).apply(i, BrokerBoard::new));

	final List<Listing> listings;
	private long seq;

	public BrokerBoard() {
		this(List.of(), 0);
	}

	private BrokerBoard(List<Listing> listings, long seq) {
		this.listings = new ArrayList<>(listings);
		this.seq = seq;
	}

	/** A listing id unique in the world: the settlement's id in the high bits, this board's count in the low 24. */
	public long nextId(long settlementId) {
		this.seq++;
		return settlementId << 24 ^ this.seq & 0xFFFFFF;
	}

	public void add(Listing l) {
		this.listings.add(l);
	}

	public @Nullable Listing get(long id) {
		for (Listing l : this.listings) {
			if (l.id() == id) {
				return l;
			}
		}
		return null;
	}

	public @Nullable Listing remove(long id) {
		for (int k = 0; k < this.listings.size(); k++) {
			if (this.listings.get(k).id() == id) {
				return this.listings.remove(k);
			}
		}
		return null;
	}

	/** Whether these goods are on the board already. */
	public boolean listed(String kind, long goods) {
		return this.listings.stream().anyMatch(l -> l.kind().equals(kind) && l.goods() == goods);
	}

	public List<Listing> all() {
		return List.copyOf(this.listings);
	}

	public int size() {
		return this.listings.size();
	}

	/** A seller's listings, oldest first. */
	public List<Listing> bySeller(UUID seller) {
		return this.listings.stream().filter(l -> l.seller().equals(seller)).toList();
	}

	/** What a buyer may buy: everyone else's live listings, cheapest first. */
	public List<Listing> forBuyer(UUID buyer, long today) {
		return this.listings.stream().filter(l -> !l.seller().equals(buyer) && l.expires() > today)
			.sorted(Comparator.comparingLong(Listing::price).thenComparingLong(Listing::id)).toList();
	}

	/** Takes the listings that have lapsed by {@code today} off the board and returns them (the caller sends the goods back). */
	public List<Listing> expire(long today) {
		List<Listing> out = new ArrayList<>();
		this.listings.removeIf(l -> {
			if (l.expires() <= today) {
				out.add(l);
				return true;
			}
			return false;
		});
		return out;
	}

	/** Listings whose name (as {@code names} words it) contains {@code query}, ignoring case; cheapest first. */
	public List<Listing> search(String query, Function<Listing, String> names, long today) {
		String q = query.trim().toLowerCase(Locale.ROOT);
		return this.listings.stream().filter(l -> l.expires() > today && (q.isEmpty() || names.apply(l).toLowerCase(Locale.ROOT).contains(q)))
			.sorted(Comparator.comparingLong(Listing::price).thenComparingLong(Listing::id)).toList();
	}
}
