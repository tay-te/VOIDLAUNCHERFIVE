package dev.voidmc.hearth.quest.service;

import dev.voidmc.hearth.quest.QuestProgress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/**
 * Items that belong to a quest — a sealed parcel, a keepsake found by the old well, a rubbing taken at a landmark — and
 * the player's inventory as something to hand items over from. Quest items are ordinary vanilla items marked with
 * {@code custom_data.hearth_quest = {id, role}}, so they need no registry entries and survive without the mod.
 */
public final class QuestItems {
	public static final String TAG = "hearth_quest";
	public static final String ROLE_PARCEL = "parcel";
	public static final String ROLE_GRANT = "grant";

	private QuestItems() {
	}

	/** A quest item: the base item renamed, with a lore line and a glint, marked with the quest id and role. */
	public static ItemStack make(Identifier item, String name, String lore, UUID quest, String role) {
		Item base = BuiltInRegistries.ITEM.getValue(item);
		ItemStack stack = new ItemStack(base == Items.AIR ? Items.PAPER : base);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GOLD)));
		if (!lore.isEmpty()) {
			stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(lore).withStyle(ChatFormatting.GRAY))));
		}
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		CompoundTag tag = new CompoundTag();
		tag.putString("id", quest.toString());
		tag.putString("role", role);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> root.put(TAG, tag));
		return stack;
	}

	/** The quest a stack belongs to, if it is a quest item. */
	public static Optional<UUID> questOf(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return Optional.empty();
		}
		CompoundTag root = data.copyTag();
		if (!root.contains(TAG)) {
			return Optional.empty();
		}
		try {
			return root.getCompoundOrEmpty(TAG).getString("id").map(UUID::fromString);
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	private static boolean isFor(ItemStack stack, UUID quest, String role) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return false;
		}
		CompoundTag tag = data.copyTag().getCompoundOrEmpty(TAG);
		return quest.toString().equals(tag.getStringOr("id", "")) && role.equals(tag.getStringOr("role", ""));
	}

	public static int count(ServerPlayer player, UUID quest, String role) {
		int n = 0;
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (!stack.isEmpty() && isFor(stack, quest, role)) {
				n += stack.getCount();
			}
		}
		ItemStack offhand = player.getOffhandItem();
		if (!offhand.isEmpty() && isFor(offhand, quest, role)) {
			n += offhand.getCount();
		}
		return n;
	}

	/** Takes every item of this quest and role; returns how many. */
	public static int takeAll(ServerPlayer player, UUID quest, String role) {
		int n = 0;
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (!stack.isEmpty() && isFor(stack, quest, role)) {
				n += stack.getCount();
				stack.setCount(0);
			}
		}
		ItemStack offhand = player.getOffhandItem();
		if (!offhand.isEmpty() && isFor(offhand, quest, role)) {
			n += offhand.getCount();
			offhand.setCount(0);
		}
		return n;
	}

	/** Gives an item, dropping what does not fit. */
	public static void give(ServerPlayer player, ItemStack stack) {
		player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
	}

	/**
	 * The player's main inventory as a source of items to hand over: matched by item id or {@code #tag}, never taking
	 * quest items (a parcel is not "any paper") or named and enchanted things a player would not mean to give away.
	 */
	public static QuestProgress.ItemSource inventory(ServerPlayer player) {
		return new QuestProgress.ItemSource() {
			@Override
			public int count(String target) {
				int n = 0;
				for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
					if (deliverable(stack, target)) {
						n += stack.getCount();
					}
				}
				return n;
			}

			@Override
			public int take(String target, int max) {
				int taken = 0;
				for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
					if (taken >= max) {
						break;
					}
					if (deliverable(stack, target)) {
						int n = Math.min(max - taken, stack.getCount());
						stack.shrink(n);
						taken += n;
					}
				}
				if (taken > 0) {
					player.getInventory().setChanged();
				}
				return taken;
			}
		};
	}

	static boolean deliverable(ItemStack stack, String target) {
		if (stack.isEmpty() || stack.has(DataComponents.CUSTOM_NAME) || stack.isEnchanted() || questOf(stack).isPresent()) {
			return false;
		}
		if (target.startsWith("#")) {
			Identifier tag = Identifier.tryParse(target.substring(1));
			return tag != null && stack.is(TagKey.create(Registries.ITEM, tag));
		}
		Identifier id = Identifier.tryParse(target);
		return id != null && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id);
	}
}
