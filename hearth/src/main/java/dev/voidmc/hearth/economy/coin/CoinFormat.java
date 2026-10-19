package dev.voidmc.hearth.economy.coin;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Amounts as words: "2 crowns, 3 marks, 4 bits", from the lang file so it reads right in every language. */
public final class CoinFormat {
	private CoinFormat() {
	}

	/** {@code bits} in the fewest coins, largest first; "0 bits" for nothing. Negative amounts get a minus sign. */
	public static MutableComponent format(long bits) {
		if (bits < 0) {
			return Component.translatable("hearth.economy.coin.negative", format(-bits));
		}
		long[] counts = Change.fewest(bits);
		List<Component> parts = new ArrayList<>(3);
		for (Coin coin : Coin.DESCENDING) {
			long n = counts[coin.ordinal()];
			if (n > 0) {
				parts.add(amount(coin, n));
			}
		}
		if (parts.isEmpty()) {
			return amount(Coin.BIT, 0);
		}
		MutableComponent out = parts.getFirst().copy();
		for (int i = 1; i < parts.size(); i++) {
			out = Component.translatable("hearth.economy.coin.join", out, parts.get(i));
		}
		return out;
	}

	/** "3 marks". */
	public static MutableComponent amount(Coin coin, long count) {
		return Component.translatable(coin.amountKey(count), count);
	}

	/**
	 * The first amount {@link #format} wrote somewhere inside {@code text} (a partner mod's "Train Blades (4 marks)" built
	 * with {@code EconomyService.format}), in bits; −1 when it holds none. Reads the component tree, not the words, so it
	 * works in every language.
	 */
	public static long read(@org.jspecify.annotations.Nullable Component text) {
		if (text == null) {
			return -1;
		}
		long v = amountOf(text);
		if (v != Long.MIN_VALUE) {
			return v;
		}
		if (text.getContents() instanceof TranslatableContents t) {
			for (Object arg : t.getArgs()) {
				if (arg instanceof Component c) {
					long a = read(c);
					if (a >= 0) {
						return a;
					}
				}
			}
		}
		for (Component sibling : text.getSiblings()) {
			long a = read(sibling);
			if (a >= 0) {
				return a;
			}
		}
		return -1;
	}

	/** The amount a component made by {@link #format} stands for, or {@link Long#MIN_VALUE} when it is not one. */
	private static long amountOf(Component c) {
		if (!(c.getContents() instanceof TranslatableContents t)) {
			return Long.MIN_VALUE;
		}
		String key = t.getKey();
		Object[] args = t.getArgs();
		if (key.equals("hearth.economy.coin.negative") && args.length == 1 && args[0] instanceof Component inner) {
			long v = amountOf(inner);
			return v == Long.MIN_VALUE ? v : -v;
		}
		if (key.equals("hearth.economy.coin.join") && args.length == 2 && args[0] instanceof Component a && args[1] instanceof Component b) {
			long x = amountOf(a);
			long y = amountOf(b);
			return x == Long.MIN_VALUE || y == Long.MIN_VALUE ? Long.MIN_VALUE : x + y;
		}
		for (Coin coin : Coin.VALUES) {
			String base = "hearth.economy.coin." + coin.name().toLowerCase(java.util.Locale.ROOT);
			if ((key.equals(base + ".one") || key.equals(base + ".many")) && args.length == 1) {
				Object n = args[0];
				long count;
				if (n instanceof Number num) {
					count = num.longValue();
				} else if (n instanceof Component nc) {
					try {
						count = Long.parseLong(nc.getString().strip());
					} catch (NumberFormatException e) {
						return Long.MIN_VALUE;
					}
				} else {
					return Long.MIN_VALUE;
				}
				return count * coin.value;
			}
		}
		return Long.MIN_VALUE;
	}
}
