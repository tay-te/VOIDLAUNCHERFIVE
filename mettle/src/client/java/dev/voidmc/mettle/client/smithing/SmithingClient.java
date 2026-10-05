package dev.voidmc.mettle.client.smithing;

import dev.voidmc.mettle.character.skill.smithing.Forged;
import dev.voidmc.mettle.character.skill.smithing.Forged.Mark;
import dev.voidmc.mettle.character.skill.smithing.Forging;
import dev.voidmc.mettle.character.skill.smithing.Honed;
import dev.voidmc.mettle.character.skill.smithing.SmithingComponents;
import java.util.List;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Smithing on the client (called from MettleClient): plain grey tooltip lines saying what a smith put into a piece. */
public final class SmithingClient {
	private SmithingClient() {
	}

	public static void init() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			Forged forged = stack.get(SmithingComponents.FORGED);
			Honed honed = stack.get(SmithingComponents.HONED);
			if (forged != null) {
				lines(stack, forged, lines);
			}
			if (honed != null && honed.left() > 0) {
				lines.add(grey(Component.translatable("mettle.smithing.tooltip.honed." + (honed.weapon() ? "weapon" : "tool"), honed.left())));
			}
		});
	}

	private static void lines(ItemStack stack, Forged forged, List<Component> lines) {
		if (forged.has(Mark.MASTERWORK)) {
			String stat = Forging.isWeapon(stack) ? "damage" : Forging.isArmour(stack) ? "armour" : "dig";
			lines.add(grey(Component.translatable("mettle.smithing.tooltip.masterwork", forged.smith(),
				Component.translatable("mettle.smithing.tooltip.stat." + stat))));
		}
		for (Mark mark : forged.marks()) {
			switch (mark) {
				case MASTERWORK -> {
				}
				case FORGED -> lines.add(grey(Component.translatable("mettle.smithing.tooltip.forged", forged.smith())));
				default -> lines.add(grey(Component.translatable("mettle.smithing.tooltip." + mark.getSerializedName())));
			}
		}
		if (forged.reforge() != Forged.Reforge.NONE) {
			lines.add(grey(Component.translatable("mettle.smithing.tooltip.reforge." + forged.reforge().getSerializedName())));
		}
	}

	private static Component grey(Component text) {
		return text.copy().withStyle(ChatFormatting.GRAY);
	}
}
