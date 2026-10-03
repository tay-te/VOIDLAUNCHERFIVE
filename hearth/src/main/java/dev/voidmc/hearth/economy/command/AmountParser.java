package dev.voidmc.hearth.economy.command;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Reads an amount of money: plain bits ("250") or coins ("2c5m3b", "3crowns", "1 crown 4 marks"). */
public final class AmountParser {
	private static final Pattern PART = Pattern.compile("(\\d+)\\s*(crowns?|c|marks?|m|bits?|b)?");

	private AmountParser() {
	}

	/** Bits, or null if the text is not an amount. */
	public static @Nullable Long parse(String text) {
		String s = text.trim().toLowerCase(Locale.ROOT).replace(",", "").replace(" ", "");
		if (s.isEmpty()) {
			return null;
		}
		Matcher m = PART.matcher(s);
		long total = 0;
		int at = 0;
		while (at < s.length()) {
			if (!m.find(at) || m.start() != at) {
				return null;
			}
			long n;
			try {
				n = Long.parseLong(m.group(1));
			} catch (NumberFormatException e) {
				return null;
			}
			String unit = m.group(2);
			long per = unit == null || unit.startsWith("b") ? 1 : unit.startsWith("m") ? 10 : 100;
			try {
				total = Math.addExact(total, Math.multiplyExact(n, per));
			} catch (ArithmeticException e) {
				return null;
			}
			at = m.end();
		}
		return total;
	}
}
