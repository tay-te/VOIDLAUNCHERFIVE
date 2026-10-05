package dev.voidmc.mettle.client.burden;

import dev.voidmc.mettle.burden.WeightTable;
import dev.voidmc.mettle.burden.Weights;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * The weight line under an item's name (BURDEN.md §4): "0.25 kg · stack 16.0 kg", or "18.4 kg with contents" for a
 * filled bundle or shulker box. Weights are what the item weighs for this player, so perks show ("40% lighter for you").
 */
final class BurdenTooltip {
	private BurdenTooltip() {
	}

	static void add(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> lines) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || stack.isEmpty()) {
			return;
		}
		WeightTable table = Weights.client();
		float multiplier = Weights.multiplier(mc.player, stack);
		float unit = table.get(stack.getItem()) * multiplier;
		float total = Weights.weigh(table, mc.player, stack, false, 0);
		MutableComponent line;
		if (total - unit * stack.getCount() > 0.005f) {
			line = Component.translatable("mettle.burden.tooltip.contents", Weights.total(total));
		} else if (stack.getCount() > 1) {
			line = Component.translatable("mettle.burden.tooltip.stack", Weights.kg(unit), Weights.total(total));
		} else {
			line = Component.translatable("mettle.burden.tooltip.one", Weights.kg(unit));
		}
		if (multiplier < 0.995f) {
			line = Component.translatable("mettle.burden.tooltip.lighter", line, Math.round((1f - multiplier) * 100f));
		}
		lines.add(Math.min(1, lines.size()), line.withStyle(ChatFormatting.GRAY));
	}
}
