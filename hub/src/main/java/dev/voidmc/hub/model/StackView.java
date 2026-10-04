package dev.voidmc.hub.model;

/**
 * What the click planners need to know about one stack, without Minecraft types.
 *
 * @param key identity for merging: two stacks merge only if their keys are equal (item and
 *     components, as {@code ItemStack.isSameItemSameComponents} would say)
 * @param sortKey ordering for Sort; stacks with equal sort keys keep a stable order by count
 * @param count how many
 * @param max the most one slot holds
 */
public record StackView(String key, String sortKey, int count, int max) {

	public static final StackView EMPTY = new StackView("", "", 0, 0);

	public boolean isEmpty() {
		return this.count <= 0;
	}

	public StackView withCount(int n) {
		return n <= 0 ? EMPTY : new StackView(this.key, this.sortKey, n, this.max);
	}

	public boolean sameItem(StackView o) {
		return !this.isEmpty() && !o.isEmpty() && this.key.equals(o.key);
	}
}
