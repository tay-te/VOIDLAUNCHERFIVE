package dev.voidmc.hearth.economy.household;

import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Settlement;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * The larder chest of a player's house (docs/LIVING_WORLD_2.md §8.2): the house's storage chest, kept stocked with the
 * town's food. The food is bought in the day's books ({@link Servants#daily}) and put in the chest only when its chunk is
 * loaded — at the day's end if it is, else by a servant on their rounds or the next day it is ({@code pending} meanwhile).
 * The chest is read only when loaded; nothing here loads a chunk.
 */
public final class Larder {
	private Larder() {
	}

	/** The house's larder chest (its first storage point that holds a container), or null. */
	public static @Nullable Container chest(ServerLevel level, Settlement s, PlotView plot) {
		for (Poi p : Servants.pois(s, plot, PoiType.STORAGE)) {
			BlockPos pos = p.pos();
			if (!level.isLoaded(pos)) {
				return null;
			}
			Container c = container(level, pos);
			if (c == null) {
				// a storage point names where the keeper stands; the chest is beside it
				for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
					c = container(level, pos.relative(d));
					if (c != null) {
						break;
					}
				}
			}
			if (c != null) {
				return c;
			}
		}
		return null;
	}

	private static @Nullable Container container(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return null;
		}
		BlockEntity be = level.getBlockEntity(pos);
		return be instanceof Container c ? c : null;
	}

	/** Food in a chest, in items. */
	public static int food(Container c) {
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack s = c.getItem(i);
			if (!s.isEmpty() && s.has(DataComponents.FOOD)) {
				n += s.getCount();
			}
		}
		return n;
	}

	/**
	 * Puts what was bought into the chest (as far as it has room) and reads what it holds. Returns the items put in.
	 */
	public static int stock(ServerLevel level, Settlement s, PlotView plot, HouseBook.House h) {
		Container c = chest(level, s, plot);
		if (c == null) {
			return 0;
		}
		int put = 0;
		ItemStack sample = HearthServices.goodsItems().sample(Goods.FOOD, s.culture(), s.id() * 31 + plot.id());
		if (sample.isEmpty() || !sample.has(DataComponents.FOOD)) {
			sample = new ItemStack(net.minecraft.world.item.Items.BREAD);
		}
		while (h.pending > 0) {
			ItemStack stack = sample.copyWithCount(Math.min(h.pending, sample.getMaxStackSize()));
			int placed = insert(c, stack);
			if (placed <= 0) {
				break;
			}
			h.pending -= placed;
			put += placed;
		}
		h.stocked = food(c);
		if (put > 0) {
			c.setChanged();
		}
		return put;
	}

	/** Puts a stack into a container (merging first, then empty slots); the number placed. */
	static int insert(Container c, ItemStack stack) {
		int left = stack.getCount();
		for (int i = 0; i < c.getContainerSize() && left > 0; i++) {
			ItemStack s = c.getItem(i);
			if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, stack) && s.getCount() < s.getMaxStackSize()) {
				int add = Math.min(left, s.getMaxStackSize() - s.getCount());
				s.grow(add);
				left -= add;
			}
		}
		for (int i = 0; i < c.getContainerSize() && left > 0; i++) {
			if (c.getItem(i).isEmpty()) {
				int add = Math.min(left, stack.getMaxStackSize());
				c.setItem(i, stack.copyWithCount(add));
				left -= add;
			}
		}
		return stack.getCount() - left;
	}

	/** Whether the house has a larder chest at all (a storage point). */
	public static boolean has(Settlement s, PlotView plot) {
		List<Poi> l = Servants.pois(s, plot, PoiType.STORAGE);
		return !l.isEmpty();
	}
}
