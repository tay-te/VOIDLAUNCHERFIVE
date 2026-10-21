package dev.voidmc.mettle.gear;

import dev.voidmc.mettle.api.GearSlot;
import dev.voidmc.mettle.api.MettleServices;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.jspecify.annotations.Nullable;

/**
 * A player's worn gear as live, mutable stacks: what the menu slots, the quiver and the belt work on. The source of truth
 * stays the {@link WornGear} attachment; this model loads from it whenever the attachment was replaced from outside
 * (world load, a sync from the server, a respawn copy) and writes a fresh snapshot back on every change ({@link #commit}),
 * with the pockets written into each worn stack's {@code mettle:pockets}.
 *
 * <p>Vanilla code mutates stacks in place (a bow splits its arrow off the quiver's stack). Such paths call
 * {@link #markDirty}; the next read, or the end of the server tick, commits.
 */
public final class GearInventory {
	private static final GearSlot[] SLOTS = GearSlot.values();

	private final Player player;
	private final ItemStack[] worn = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
	private final List<NonNullList<ItemStack>> pockets = new ArrayList<>(3);
	private @Nullable WornGear source;
	private boolean loaded;
	private boolean dirty;

	public GearInventory(Player player) {
		this.player = player;
		for (int i = 0; i < 3; i++) {
			pockets.add(NonNullList.withSize(GearRules.MAX_POCKETS_ANY, ItemStack.EMPTY));
		}
	}

	public static GearInventory of(Player player) {
		return ((GearHolder) player).mettle$gear();
	}

	public Player player() {
		return player;
	}

	/** Reloads when the attachment was replaced from outside; commits pending in-place changes. */
	private void sync() {
		WornGear attached = player.getAttached(WornGear.TYPE);
		if (!loaded || attached != source) {
			load(attached);
		} else if (dirty) {
			commit();
		}
	}

	private void load(@Nullable WornGear gear) {
		for (int i = 0; i < 3; i++) {
			worn[i] = gear == null ? ItemStack.EMPTY : gear.stacks().get(i).copy();
			readPockets(i);
		}
		source = gear;
		loaded = true;
		dirty = false;
	}

	private void readPockets(int i) {
		NonNullList<ItemStack> list = pockets.get(i);
		ItemContainerContents contents = worn[i].get(GearComponents.POCKETS);
		if (contents == null) {
			list.replaceAll(s -> ItemStack.EMPTY);
		} else {
			contents.copyInto(list);
		}
	}

	/** Writes the pockets into the worn stacks and the snapshot into the attachment (which syncs it). */
	public void commit() {
		List<ItemStack> copies = new ArrayList<>(3);
		for (int i = 0; i < 3; i++) {
			ItemStack stack = worn[i];
			if (!stack.isEmpty() && stack.has(GearComponents.GEAR)) {
				NonNullList<ItemStack> list = pockets.get(i);
				if (list.stream().allMatch(ItemStack::isEmpty)) {
					stack.remove(GearComponents.POCKETS);
				} else {
					stack.set(GearComponents.POCKETS, ItemContainerContents.fromItems(list));
				}
			}
			copies.add(stack.copy());
		}
		source = new WornGear(List.copyOf(copies));
		loaded = true;
		dirty = false;
		player.setAttached(WornGear.TYPE, source);
		if (player instanceof ServerPlayer serverPlayer) {
			GearModule.changed(serverPlayer);
		}
	}

	public void markDirty() {
		if (!dirty && !player.level().isClientSide()) {
			dirty = true;
			GearModule.DIRTY.add(this);
		}
	}

	/** Commits in-place changes made since the last commit (end of the server tick, before a respawn copy). */
	public void flush() {
		if (dirty && loaded && player.getAttached(WornGear.TYPE) == source) {
			commit();
		}
		dirty = false;
	}

	public ItemStack worn(GearSlot slot) {
		sync();
		return worn[slot.ordinal()];
	}

	public @Nullable GearSpec spec(GearSlot slot) {
		return worn(slot).get(GearComponents.GEAR);
	}

	/** Usable pockets of what is worn there; 0 when nothing (or something that is not that slot's gear) is worn. */
	public int pocketCount(GearSlot slot) {
		GearSpec spec = spec(slot);
		if (spec == null || spec.slot() != slot) {
			return 0;
		}
		// The talent Packrat (docs/BUILD_FEEL.md §13.2): one more belt pocket, up to what the belt row shows.
		int extra = slot == GearSlot.BELT && dev.voidmc.mettle.core.CardStore.get(this.player).trait(PACKRAT) > 0 ? 1 : 0;
		return Math.min(spec.pocketCount() + extra, GearRules.maxPockets(slot));
	}

	private static final net.minecraft.resources.Identifier PACKRAT = net.minecraft.resources.Identifier.fromNamespaceAndPath("mettle", "talent/packrat");

	/**
	 * Pockets beyond what is usable now (Packrat left the row) hand their contents back to the inventory, or drop them,
	 * so nothing sits unseen in a pocket nobody can open. Server side.
	 */
	public void spillHidden(GearSlot slot) {
		sync();
		NonNullList<ItemStack> list = pockets.get(slot.ordinal());
		boolean changed = false;
		for (int i = pocketCount(slot); i < list.size(); i++) {
			ItemStack stack = list.get(i);
			if (!stack.isEmpty()) {
				list.set(i, ItemStack.EMPTY);
				if (!player.getInventory().add(stack) && !stack.isEmpty()) {
					if (player instanceof ServerPlayer sp) {
						sp.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
					}
				}
				changed = true;
			}
		}
		if (changed) {
			commit();
		}
	}

	public ItemStack pocket(GearSlot slot, int index) {
		sync();
		return index < pocketCount(slot) ? pockets.get(slot.ordinal()).get(index) : ItemStack.EMPTY;
	}

	/** Every usable pocket stack, live. */
	public List<ItemStack> pockets(GearSlot slot) {
		sync();
		return pockets.get(slot.ordinal()).subList(0, pocketCount(slot));
	}

	/** Puts gear on (or takes it off with EMPTY): its pockets come with it. */
	public void setWorn(GearSlot slot, ItemStack stack) {
		sync();
		int i = slot.ordinal();
		worn[i] = stack;
		readPockets(i);
		commit();
	}

	public void setPocket(GearSlot slot, int index, ItemStack stack) {
		sync();
		if (index < pocketCount(slot)) {
			pockets.get(slot.ordinal()).set(index, stack);
			commit();
		}
	}

	/** Whether a pocket of what is worn in this slot takes the stack. */
	public boolean fits(GearSlot slot, ItemStack stack) {
		GearSpec spec = spec(slot);
		if (spec == null || stack.isEmpty()) {
			return false;
		}
		boolean hasTag = spec.accepts().isPresent();
		boolean inTag = hasTag && stack.is(spec.accepts().get());
		float kgEach = slot == GearSlot.BELT ? MettleServices.burden().weight(stack) / Math.max(1, stack.getCount()) : 0f;
		return GearRules.fits(slot, stack.has(GearComponents.GEAR), hasTag, inTag, kgEach);
	}

	/**
	 * Moves as much of the stack as fits into this slot's pockets: onto matching stacks first, then into empty pockets.
	 *
	 * @return whether anything moved
	 */
	public boolean insert(GearSlot slot, ItemStack stack) {
		if (stack.isEmpty() || !fits(slot, stack)) {
			return false;
		}
		List<ItemStack> list = pockets(slot);
		int before = stack.getCount();
		for (ItemStack in : list) {
			if (!in.isEmpty() && ItemStack.isSameItemSameComponents(in, stack)) {
				int move = Math.min(stack.getCount(), in.getMaxStackSize() - in.getCount());
				if (move > 0) {
					in.grow(move);
					stack.shrink(move);
				}
			}
			if (stack.isEmpty()) {
				break;
			}
		}
		for (int i = 0; i < list.size() && !stack.isEmpty(); i++) {
			if (list.get(i).isEmpty()) {
				list.set(i, stack.split(Math.min(stack.getCount(), stack.getMaxStackSize())));
			}
		}
		if (stack.getCount() != before) {
			commit();
			return true;
		}
		return false;
	}

	/** The first quiver stack the weapon can shoot, live (the caller may split it); empty when none. */
	public ItemStack quiverProjectile(Predicate<ItemStack> supported) {
		for (ItemStack stack : pockets(GearSlot.QUIVER)) {
			if (!stack.isEmpty() && supported.test(stack)) {
				markDirty();
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	/** Whether this exact stack object is one of the worn quiver's pockets. */
	public boolean inQuiver(ItemStack stack) {
		for (ItemStack in : pockets(GearSlot.QUIVER)) {
			if (in == stack) {
				return true;
			}
		}
		return false;
	}

	/** Takes everything off: the worn stacks (pockets inside) in slot order. */
	public List<ItemStack> takeAll() {
		sync();
		List<ItemStack> out = new ArrayList<>(3);
		for (GearSlot slot : SLOTS) {
			if (!worn[slot.ordinal()].isEmpty()) {
				out.add(worn[slot.ordinal()]);
			}
			worn[slot.ordinal()] = ItemStack.EMPTY;
			readPockets(slot.ordinal());
		}
		commit();
		return out;
	}

	/** Removes worn gear and pocket items matching the filter (Curse of Vanishing at death). */
	public void removeIf(Predicate<ItemStack> filter) {
		sync();
		boolean changed = false;
		for (int i = 0; i < 3; i++) {
			if (!worn[i].isEmpty() && filter.test(worn[i])) {
				worn[i] = ItemStack.EMPTY;
				readPockets(i);
				changed = true;
				continue;
			}
			NonNullList<ItemStack> list = pockets.get(i);
			for (int p = 0; p < list.size(); p++) {
				if (!list.get(p).isEmpty() && filter.test(list.get(p))) {
					list.set(p, ItemStack.EMPTY);
					changed = true;
				}
			}
		}
		if (changed) {
			commit();
		}
	}
}
