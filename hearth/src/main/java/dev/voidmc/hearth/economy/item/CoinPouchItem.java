package dev.voidmc.hearth.economy.item;

import dev.voidmc.hearth.economy.EconomyModule;
import dev.voidmc.hearth.economy.coin.Coin;
import dev.voidmc.hearth.economy.coin.CoinFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * A leather pouch for coin. Works like a bundle that takes only coins — click coins onto it to put them in, right-click
 * it to take out a handful of the largest coin — and it picks up coins for you: any coin you walk over goes into the
 * first pouch with room. What it holds counts as carried money everywhere (markets, quests, {@code EconomyService}).
 *
 * <p>Use it in hand to hear it and see what it holds. Its look fills out with what it holds: the server keeps a fullness
 * value in {@code custom_model_data} that the item model definition dispatches on.
 */
public class CoinPouchItem extends Item {
	private static final int BAR_COLOR = ARGB.colorFromFloat(1.0F, 0.86F, 0.66F, 0.24F);
	private static final int FULL_BAR_COLOR = ARGB.colorFromFloat(1.0F, 1.0F, 0.33F, 0.33F);

	public CoinPouchItem(Properties properties) {
		super(properties);
	}

	public static PouchContents contents(ItemStack pouch) {
		return pouch.getOrDefault(EconomyComponents.POUCH_CONTENTS, PouchContents.EMPTY);
	}

	/** Sets what the pouch holds and its look (fullness for the model). */
	public static void setContents(ItemStack pouch, PouchContents contents) {
		pouch.set(EconomyComponents.POUCH_CONTENTS, contents);
		float fullness = Mth.clamp((float) contents.coins() / capacity(), 0F, 1F);
		pouch.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(contents.isEmpty() ? 0F : Math.max(0.01F, fullness)),
			List.of(), List.of(), List.of()));
	}

	public static int capacity() {
		return EconomyModule.config().coins().pouchCapacity();
	}

	/** Puts as many of these coins in as fit; returns how many went in. */
	public static int insert(ItemStack pouch, Coin coin, int count) {
		PouchContents c = contents(pouch);
		int put = Math.max(0, Math.min(count, capacity() - c.coins()));
		if (put > 0) {
			setContents(pouch, c.with(coin, c.count(coin) + put));
		}
		return put;
	}

	/** Takes out up to a stack of the largest coin it holds; empty if it holds none. */
	public static ItemStack removeHandful(ItemStack pouch) {
		PouchContents c = contents(pouch);
		Coin coin = c.largest();
		if (coin == null) {
			return ItemStack.EMPTY;
		}
		int n = Math.min(c.count(coin), EconomyItems.coin(coin).getDefaultMaxStackSize());
		setContents(pouch, c.with(coin, c.count(coin) - n));
		return new ItemStack(EconomyItems.coin(coin), n);
	}

	@Override
	public boolean overrideStackedOnOther(ItemStack self, Slot slot, ClickAction clickAction, Player player) {
		ItemStack other = slot.getItem();
		Coin coin = EconomyItems.coinOf(other);
		if (clickAction == ClickAction.PRIMARY && coin != null) {
			int space = capacity() - contents(self).coins();
			ItemStack taken = space > 0 ? slot.safeTake(other.getCount(), space, player) : ItemStack.EMPTY;
			if (!taken.isEmpty()) {
				insert(self, coin, taken.getCount());
				playInsertSound(player);
			} else {
				playInsertFailSound(player);
			}
			return true;
		}
		if (clickAction == ClickAction.SECONDARY && other.isEmpty()) {
			ItemStack taken = removeHandful(self);
			if (!taken.isEmpty()) {
				ItemStack rest = slot.safeInsert(taken);
				if (!rest.isEmpty()) {
					Coin back = EconomyItems.coinOf(rest);
					if (back != null) {
						insert(self, back, rest.getCount());
					}
				} else {
					playRemoveSound(player);
				}
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction clickAction, Player player,
		SlotAccess carriedItem) {
		Coin coin = EconomyItems.coinOf(other);
		if (clickAction == ClickAction.PRIMARY && coin != null) {
			if (slot.allowModification(player)) {
				int put = insert(self, coin, other.getCount());
				if (put > 0) {
					other.shrink(put);
					playInsertSound(player);
					return true;
				}
			}
			playInsertFailSound(player);
			return true;
		}
		if (clickAction == ClickAction.SECONDARY && other.isEmpty()) {
			if (slot.allowModification(player)) {
				ItemStack taken = removeHandful(self);
				if (!taken.isEmpty()) {
					carriedItem.set(taken);
					playRemoveSound(player);
				}
			}
			return true;
		}
		return false;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		PouchContents c = contents(player.getItemInHand(hand));
		if (!level.isClientSide()) {
			player.sendOverlayMessage(c.isEmpty()
				? Component.translatable("hearth.economy.pouch.empty")
				: Component.translatable("hearth.economy.pouch.holds", CoinFormat.format(c.value())));
			Coin largest = c.largest();
			level.playSound(null, player.blockPosition(), c.isEmpty() ? SoundEvents.BUNDLE_INSERT_FAIL : SoundEvents.BUNDLE_DROP_CONTENTS,
				SoundSource.PLAYERS, 0.6F, largest == null ? 1.0F : largest.pitch);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return !contents(stack).isEmpty();
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Math.min(1 + Mth.floor(12F * contents(stack).coins() / capacity()), 13);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return contents(stack).coins() >= capacity() ? FULL_BAR_COLOR : BAR_COLOR;
	}

	@Override
	public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
		PouchContents c = contents(stack);
		return c.isEmpty() ? Optional.empty() : Optional.of(c);
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		PouchContents c = contents(stack);
		if (c.isEmpty()) {
			builder.accept(Component.translatable("hearth.economy.pouch.empty").withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable("hearth.economy.pouch.hint").withStyle(ChatFormatting.DARK_GRAY));
			return;
		}
		builder.accept(Component.translatable("hearth.economy.pouch.total", CoinFormat.format(c.value())).withStyle(ChatFormatting.GOLD));
		builder.accept(Component.translatable("hearth.economy.pouch.fill", c.coins(), capacity()).withStyle(ChatFormatting.GRAY));
	}

	@Override
	public void onDestroyed(ItemEntity entity) {
		PouchContents c = contents(entity.getItem());
		if (!c.isEmpty()) {
			setContents(entity.getItem(), PouchContents.EMPTY);
			List<ItemStack> spill = new ArrayList<>();
			for (Coin coin : Coin.VALUES) {
				int n = c.count(coin);
				int max = EconomyItems.coin(coin).getDefaultMaxStackSize();
				while (n > 0) {
					int k = Math.min(n, max);
					spill.add(new ItemStack(EconomyItems.coin(coin), k));
					n -= k;
				}
			}
			ItemUtils.onContainerDestroyed(entity, spill.stream());
		}
	}

	private static void playInsertSound(Entity entity) {
		entity.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 1.1F + entity.level().getRandom().nextFloat() * 0.3F);
	}

	private static void playRemoveSound(Entity entity) {
		entity.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 1.1F + entity.level().getRandom().nextFloat() * 0.3F);
	}

	private static void playInsertFailSound(Entity entity) {
		entity.playSound(SoundEvents.BUNDLE_INSERT_FAIL, 1.0F, 1.0F);
	}
}
