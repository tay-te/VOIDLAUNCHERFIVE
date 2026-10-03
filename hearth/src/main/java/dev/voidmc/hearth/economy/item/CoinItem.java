package dev.voidmc.hearth.economy.item;

import dev.voidmc.hearth.economy.coin.Coin;
import dev.voidmc.hearth.economy.coin.CoinFormat;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A coin. Stacks to 64; its worth is in the tooltip, and a stack shows as a little pile (item model definition). */
public class CoinItem extends Item {
	private final Coin coin;

	public CoinItem(Coin coin, Properties properties) {
		super(properties);
		this.coin = coin;
	}

	public Coin coin() {
		return this.coin;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		if (stack.getCount() > 1) {
			builder.accept(Component.translatable("hearth.economy.coin.worth_stack", CoinFormat.format(this.coin.value),
				CoinFormat.format(this.coin.value * stack.getCount())).withStyle(ChatFormatting.GRAY));
		} else {
			builder.accept(Component.translatable("hearth.economy.coin.worth", CoinFormat.format(this.coin.value)).withStyle(ChatFormatting.GRAY));
		}
	}
}
