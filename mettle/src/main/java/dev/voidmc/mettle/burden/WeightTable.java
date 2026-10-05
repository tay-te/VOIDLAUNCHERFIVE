package dev.voidmc.mettle.burden;

import dev.voidmc.mettle.burden.WeightDeriver.How;
import it.unimi.dsi.fastutil.objects.Reference2FloatMap;
import it.unimi.dsi.fastutil.objects.Reference2FloatOpenHashMap;
import java.util.Map;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/**
 * Every item's own weight in kg (one item, no contents). Immutable; replaced whole when datapacks reload. The server's
 * copy also knows how each weight was reached; the client's (from the sync payload) only has the numbers.
 */
public final class WeightTable {
	public static final WeightTable EMPTY = new WeightTable(Map.of(), null);

	private final Reference2FloatMap<Item> kg;
	private final @Nullable Map<Item, How> how;

	public WeightTable(Map<Item, Float> kg, @Nullable Map<Item, How> how) {
		this.kg = new Reference2FloatOpenHashMap<>(kg);
		this.kg.defaultReturnValue(Float.NaN);
		this.how = how;
	}

	/** One item's weight; items the table has never seen get the fallback rule. */
	public float get(Item item) {
		float w = this.kg.getFloat(item);
		if (!Float.isNaN(w)) {
			return w;
		}
		return Leaves.technical(item) ? 0f : WeightDeriver.FALLBACK_STACK_KG / Math.max(1, item.getDefaultMaxStackSize());
	}

	public @Nullable How how(Item item) {
		return this.how == null ? null : this.how.get(item);
	}

	public Reference2FloatMap<Item> entries() {
		return this.kg;
	}

	public int size() {
		return this.kg.size();
	}
}
