package dev.voidmc.hearth.quest.template;

import java.util.Map;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * A count in a template: a number ({@code 12}), a range ({@code [8, 16]}), or a variable the generator binds, optionally
 * scaled ({@code "$missing"}, {@code "$missing*0.5"}).
 */
public record Amount(int min, int max, @Nullable String variable, double factor) {
	public static final Amount ONE = new Amount(1, 1, null, 1.0);

	public static Amount of(int n) {
		return new Amount(n, n, null, 1.0);
	}

	public static Amount range(int min, int max) {
		return new Amount(Math.min(min, max), Math.max(min, max), null, 1.0);
	}

	/** Parses {@code $name} or {@code $name*factor}. */
	public static Amount parseVariable(String text) {
		String s = text.trim();
		if (!s.startsWith("$")) {
			throw new IllegalArgumentException("a variable amount starts with $: " + text);
		}
		double factor = 1.0;
		int star = s.indexOf('*');
		String name = s.substring(1, star < 0 ? s.length() : star).trim();
		if (star >= 0) {
			factor = Double.parseDouble(s.substring(star + 1).trim());
			if (!(factor > 0)) {
				throw new IllegalArgumentException("factor must be positive: " + text);
			}
		}
		if (name.isEmpty() || !name.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '_')) {
			throw new IllegalArgumentException("bad variable name: " + text);
		}
		return new Amount(1, 1, name, factor);
	}

	public boolean isVariable() {
		return this.variable != null;
	}

	/**
	 * The count for one quest: a roll in the range, or the variable's value times the factor (at least 1). A variable
	 * not bound in {@code vars} counts as 1.
	 */
	public int resolve(RandomSource random, Map<String, Integer> vars) {
		if (this.variable != null) {
			Integer v = vars.get(this.variable);
			return Math.max(1, (int) Math.ceil((v == null ? 1 : v) * this.factor));
		}
		return this.min == this.max ? this.min : Mth.nextInt(random, this.min, this.max);
	}
}
