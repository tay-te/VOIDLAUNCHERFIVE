package dev.voidmc.hearth.economy.shop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.economy.price.GoodsMask;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import org.jspecify.annotations.Nullable;

/**
 * One shop: a market stall, a shophouse, a workshop's counter, an inn's taproom, a fence's back room. Like a merchant in
 * KCD it has its own stock (in goods units, bought from the town's stores at wholesale) and its own till: it sells only
 * what it holds and cannot buy what it cannot pay for. Its keeper is the resident who works there.
 */
public final class Shop {
	private static final int N = Goods.VALUES.length;

	public static final Codec<Shop> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.fieldOf("key").forGetter(s -> s.key),
		Codec.INT.optionalFieldOf("plot", Resident.NO_PLOT).forGetter(s -> s.plot),
		Codec.STRING.optionalFieldOf("kind", "").forGetter(s -> s.kind),
		UUIDUtil.CODEC.optionalFieldOf("keeper").forGetter(s -> Optional.ofNullable(s.keeper)),
		Codec.LONG.optionalFieldOf("deals", 0L).forGetter(s -> s.deals),
		Codec.BOOL.optionalFieldOf("fence", false).forGetter(s -> s.fence),
		Codec.INT.listOf().optionalFieldOf("stock", List.of()).forGetter(s -> ints(s.stock)),
		Codec.FLOAT.listOf().optionalFieldOf("sales", List.of()).forGetter(s -> floats(s.sales)),
		Codec.INT.listOf().optionalFieldOf("sold_today", List.of()).forGetter(s -> ints(s.soldToday)),
		Codec.LONG.optionalFieldOf("till", 0L).forGetter(s -> s.till),
		Codec.LONG.optionalFieldOf("takings", 0L).forGetter(s -> s.takings),
		Codec.LONG.optionalFieldOf("opened", 0L).forGetter(s -> s.opened)
	).apply(i, (key, plot, kind, keeper, deals, fence, stock, sales, sold, till, takings, opened) -> {
		Shop s = new Shop(key, plot, kind, deals, fence, opened);
		s.keeper = keeper.orElse(null);
		copy(stock, s.stock);
		for (int g = 0; g < Math.min(N, sales.size()); g++) {
			s.sales[g] = sales.get(g);
		}
		copy(sold, s.soldToday);
		s.till = till;
		s.takings = takings;
		return s;
	}));

	/** {@code p<plot id>} for a shop in a building, {@code r<uuid>} for a trader without one (a pedlar, a fence). */
	public final String key;
	/** The workplace plot, or {@link Resident#NO_PLOT}. */
	public final int plot;
	/** The workplace's archetype path ({@code work/bakery}), or {@code job/<job>} for a trader without a building. */
	public final String kind;
	/** Who keeps it now; null while nobody works there (the shop is shut). */
	public @Nullable UUID keeper;
	/** What it deals in ({@link GoodsMask}). */
	public long deals;
	/** A fence: buys stolen goods, cheaply. */
	public boolean fence;
	/** Units of each goods on its shelves. */
	public final int[] stock = new int[N];
	/** Smoothed units of each goods it sells a day (folk and players): how much it keeps in stock. */
	public final float[] sales = new float[N];
	public final int[] soldToday = new int[N];
	/** Coin in its till, in bits. */
	public long till;
	/** Takings since the day began, for the market dues. */
	public long takings;
	public final long opened;

	public Shop(String key, int plot, String kind, long deals, boolean fence, long opened) {
		this.key = key;
		this.plot = plot;
		this.kind = kind;
		this.deals = deals;
		this.fence = fence;
		this.opened = opened;
	}

	public boolean open() {
		return this.keeper != null;
	}

	public boolean deals(Goods g) {
		return GoodsMask.has(this.deals, g);
	}

	/** Whether it deals in everything (a market, a general trader). */
	public boolean general() {
		return (this.deals & GoodsMask.ALL) == GoodsMask.ALL;
	}

	public int stock(Goods g) {
		return this.stock[g.ordinal()];
	}

	/** Records a sale to folk or a player: stock out, coin in. */
	public void sold(Goods g, int units, long bits) {
		int i = g.ordinal();
		int t = Math.min(units, this.stock[i]);
		this.stock[i] -= t;
		this.soldToday[i] += t;
		this.till += bits;
		this.takings += bits;
	}

	/** Units of a goods it would like on its shelves. */
	public int target(Goods g, double stockDays, int minStock, int maxStock) {
		if (!deals(g)) {
			return 0;
		}
		return (int) Math.max(minStock, Math.min(maxStock, Math.ceil(this.sales[g.ordinal()] * stockDays)));
	}

	private static void copy(List<Integer> from, int[] into) {
		for (int g = 0; g < Math.min(into.length, from.size()); g++) {
			into[g] = from.get(g);
		}
	}

	static List<Integer> ints(int[] a) {
		List<Integer> l = new ArrayList<>(a.length);
		for (int v : a) {
			l.add(v);
		}
		return l;
	}

	static List<Float> floats(float[] a) {
		List<Float> l = new ArrayList<>(a.length);
		for (float v : a) {
			l.add(v);
		}
		return l;
	}
}
