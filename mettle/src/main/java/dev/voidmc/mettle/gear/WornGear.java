package dev.voidmc.mettle.gear;

import com.mojang.serialization.Codec;
import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.GearSlot;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.item.ItemStack;

/**
 * The three worn stacks, as stored and synced ({@code mettle:gear/worn}). Pockets live inside the stacks' own
 * {@code mettle:pockets}. A snapshot: always a fresh record of copies, never mutated, so setting it always syncs. Synced to
 * everyone tracking the player (they draw it); copied on death only with keepInventory, which the module does itself.
 */
public record WornGear(List<ItemStack> stacks) {
	public static final WornGear EMPTY = new WornGear(List.of(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));
	public static final Codec<WornGear> CODEC = ItemStack.OPTIONAL_CODEC.listOf().xmap(WornGear::of, WornGear::stacks);

	public static final AttachmentType<WornGear> TYPE = AttachmentRegistry.create(Mettle.id("gear/worn"), b -> b
		.persistent(CODEC)
		.syncWith(ItemStack.OPTIONAL_LIST_STREAM_CODEC.map(WornGear::of, WornGear::stacks), AttachmentSyncPredicate.all()));

	/** Pads or trims to exactly three stacks. */
	public static WornGear of(List<ItemStack> stacks) {
		List<ItemStack> three = new ArrayList<>(3);
		for (int i = 0; i < 3; i++) {
			three.add(i < stacks.size() ? stacks.get(i) : ItemStack.EMPTY);
		}
		return new WornGear(List.copyOf(three));
	}

	public ItemStack get(GearSlot slot) {
		return stacks.get(slot.ordinal());
	}

	public boolean isEmpty() {
		return stacks.stream().allMatch(ItemStack::isEmpty);
	}
}
