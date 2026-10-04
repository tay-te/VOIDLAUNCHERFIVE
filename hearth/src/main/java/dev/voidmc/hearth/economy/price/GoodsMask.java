package dev.voidmc.hearth.economy.price;

import dev.voidmc.hearth.api.Goods;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** A set of {@link Goods} as the bits of a long (one per ordinal), for what a shop deals in. */
public final class GoodsMask {
	public static final long NONE = 0L;
	public static final long ALL = Goods.VALUES.length >= 64 ? -1L : (1L << Goods.VALUES.length) - 1;

	static {
		if (Goods.VALUES.length > 64) {
			throw new IllegalStateException("GoodsMask holds at most 64 goods");
		}
	}

	private GoodsMask() {
	}

	public static long of(Goods... goods) {
		long m = 0;
		for (Goods g : goods) {
			m |= 1L << g.ordinal();
		}
		return m;
	}

	public static boolean has(long mask, Goods g) {
		return (mask & 1L << g.ordinal()) != 0;
	}

	/** Goods names, {@code "*"} for all; unknown names are skipped. */
	public static long parse(Collection<String> names) {
		long m = 0;
		for (String n : names) {
			if (n.equals("*")) {
				return ALL;
			}
			Goods g = Goods.CODEC.byName(n);
			if (g != null) {
				m |= 1L << g.ordinal();
			}
		}
		return m;
	}

	public static List<Goods> list(long mask) {
		List<Goods> out = new ArrayList<>();
		for (Goods g : Goods.VALUES) {
			if (has(mask, g)) {
				out.add(g);
			}
		}
		return out;
	}
}
