package dev.voidmc.hearth.economy.market;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.api.Goods;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import org.jspecify.annotations.Nullable;

/**
 * The economy module's own record of a settlement ({@code settlement.data(EconomyModule.LEDGER)}): how its prices have
 * wandered, how fast it uses each goods, what its builders are waiting on, the money changer's emeralds, and yesterday's
 * books. A few small arrays per settlement; updated once a sim day and on trades.
 */
public final class MarketLedger {
	private static final int N = Goods.VALUES.length;

	public static final Codec<MarketLedger> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.FLOAT.listOf().optionalFieldOf("drift", List.of()).forGetter(l -> floats(l.drift)),
		Codec.FLOAT.listOf().optionalFieldOf("use", List.of()).forGetter(l -> floats(l.use)),
		Codec.INT.listOf().optionalFieldOf("last_stock", List.of()).forGetter(l -> ints(l.lastStock)),
		Codec.INT.listOf().optionalFieldOf("traded", List.of()).forGetter(l -> ints(l.traded)),
		Codec.INT.listOf().optionalFieldOf("need", List.of()).forGetter(l -> ints(l.need)),
		Codec.LONG.listOf().optionalFieldOf("need_day", List.of()).forGetter(l -> longs(l.needDay)),
		Codec.STRING.listOf().optionalFieldOf("need_for", List.of()).forGetter(l -> List.of(l.needFor)),
		Codec.INT.optionalFieldOf("emeralds", -1).forGetter(l -> l.emeralds),
		Codec.LONG.optionalFieldOf("last_day", Long.MIN_VALUE).forGetter(l -> l.lastDay),
		Books.CODEC.optionalFieldOf("books", Books.EMPTY).forGetter(l -> l.books),
		Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG).optionalFieldOf("standing_gain", Map.of()).forGetter(l -> l.standingGain)
	).apply(i, MarketLedger::new));

	/** Log of each goods' price wander (0 = none). */
	public final float[] drift = new float[N];
	/** Smoothed daily use of each goods by the settlement itself (not counting trade with players). */
	public final float[] use = new float[N];
	/** Stock at the last day's step (-1 before the first). */
	public final int[] lastStock = new int[N];
	/** Net units players sold (+) or bought (−) since the last day's step, so they do not count as the town's own use. */
	public final int[] traded = new int[N];
	/** Units the builders are short of (a stalled construction), 0 if none. */
	public final int[] need = new int[N];
	public final long[] needDay = new long[N];
	/** The archetype path of the building waiting, for the merchant to name. */
	public final String[] needFor = new String[N];
	/** Emeralds the money changer holds (-1 until first used: then seeded from the settlement's tier). */
	public int emeralds;
	public long lastDay;
	public Books books;
	/**
	 * Per player, the sim day and reputation already earned that day by trading here ({@code day << 8 | gained}), so
	 * trade raises standing slowly and cannot be farmed.
	 */
	public final Map<UUID, Long> standingGain;

	public MarketLedger() {
		this(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), -1, Long.MIN_VALUE, Books.EMPTY, Map.of());
	}

	private MarketLedger(List<Float> drift, List<Float> use, List<Integer> lastStock, List<Integer> traded, List<Integer> need,
		List<Long> needDay, List<String> needFor, int emeralds, long lastDay, Books books, Map<UUID, Long> standingGain) {
		Arrays.fill(this.lastStock, -1);
		Arrays.fill(this.needFor, "");
		for (int g = 0; g < N; g++) {
			if (g < drift.size()) {
				this.drift[g] = drift.get(g);
			}
			if (g < use.size()) {
				this.use[g] = use.get(g);
			}
			if (g < lastStock.size()) {
				this.lastStock[g] = lastStock.get(g);
			}
			if (g < traded.size()) {
				this.traded[g] = traded.get(g);
			}
			if (g < need.size()) {
				this.need[g] = need.get(g);
			}
			if (g < needDay.size()) {
				this.needDay[g] = needDay.get(g);
			}
			if (g < needFor.size()) {
				this.needFor[g] = needFor.get(g);
			}
		}
		this.emeralds = emeralds;
		this.lastDay = lastDay;
		this.books = books;
		this.standingGain = new HashMap<>(standingGain);
	}

	public float drift(Goods g) {
		return this.drift[g.ordinal()];
	}

	public int need(Goods g) {
		return this.need[g.ordinal()];
	}

	/** The goods the builders are shortest of (by units), or null. */
	public @Nullable Goods mostNeeded() {
		Goods best = null;
		for (Goods g : Goods.VALUES) {
			if (this.need[g.ordinal()] > 0 && (best == null || this.need[g.ordinal()] > this.need[best.ordinal()])) {
				best = g;
			}
		}
		return best;
	}

	/**
	 * Yesterday's books, for the bank screen and the price command.
	 *
	 * @param taxes   taxes and dues collected
	 * @param trade   income from passing trade
	 * @param wages   wages paid
	 * @param upkeep  spent on upkeep
	 * @param minted  coin struck at the mint
	 * @param unpaid  days in a row the treasury could not meet the wages
	 * @param spent   what folk spent on food and comforts
	 * @param dues    market dues and tithes paid to the treasury
	 * @param income  what owned businesses paid their owners
	 * @param hungry  households that could not pay for their food
	 */
	public record Books(long taxes, long trade, long wages, long upkeep, long minted, int unpaid, long spent, long dues, long income, int hungry) {
		public static final Books EMPTY = new Books(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

		public Books(long taxes, long trade, long wages, long upkeep, long minted, int unpaid) {
			this(taxes, trade, wages, upkeep, minted, unpaid, 0, 0, 0, 0);
		}

		public static final Codec<Books> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.optionalFieldOf("taxes", 0L).forGetter(Books::taxes),
			Codec.LONG.optionalFieldOf("trade", 0L).forGetter(Books::trade),
			Codec.LONG.optionalFieldOf("wages", 0L).forGetter(Books::wages),
			Codec.LONG.optionalFieldOf("upkeep", 0L).forGetter(Books::upkeep),
			Codec.LONG.optionalFieldOf("minted", 0L).forGetter(Books::minted),
			Codec.INT.optionalFieldOf("unpaid", 0).forGetter(Books::unpaid),
			Codec.LONG.optionalFieldOf("spent", 0L).forGetter(Books::spent),
			Codec.LONG.optionalFieldOf("dues", 0L).forGetter(Books::dues),
			Codec.LONG.optionalFieldOf("income", 0L).forGetter(Books::income),
			Codec.INT.optionalFieldOf("hungry", 0).forGetter(Books::hungry)
		).apply(i, Books::new));
	}

	private static List<Float> floats(float[] a) {
		List<Float> l = new ArrayList<>(a.length);
		for (float f : a) {
			l.add(f);
		}
		return l;
	}

	private static List<Integer> ints(int[] a) {
		List<Integer> l = new ArrayList<>(a.length);
		for (int v : a) {
			l.add(v);
		}
		return l;
	}

	private static List<Long> longs(long[] a) {
		List<Long> l = new ArrayList<>(a.length);
		for (long v : a) {
			l.add(v);
		}
		return l;
	}
}
